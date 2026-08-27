/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.beans.FeatureName.PIPE_ROLLBACK_RETRY_ON_FAILURE;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dtos.rollback.BatchRollbackRequestDTO;
import io.harness.dtos.rollback.BatchRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.dtos.rollback.RollbackRequestDTO;
import io.harness.dtos.rollback.RollbackResponseDTO;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.RollbackStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.utils.TimescaleStatusMapper;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsDTO;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.execution.Status;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PostProdRollbackServiceImpl implements PostProdRollbackService {
  // Set of instanceTypes that supports the PostProdRollback.
  private static final Set<InstanceType> SUPPORTED_INSTANCE_TYPES_FOR_ROLLBACK =
      Set.of(InstanceType.K8S_INSTANCE, InstanceType.TAS_INSTANCE, InstanceType.ECS_INSTANCE, InstanceType.ASG_INSTANCE,
          InstanceType.SPOT_INSTANCE, InstanceType.NATIVE_HELM_INSTANCE, InstanceType.GOOGLE_MIG_INSTANCE);
  private static final Set<RollbackStatus> BASE_ALLOWED_ROLLBACK_START_STATUSES =
      Set.of(RollbackStatus.NOT_STARTED, RollbackStatus.UNAVAILABLE);
  private static final Set<RollbackStatus> RETRY_ALLOWED_ROLLBACK_START_STATUSES =
      Set.of(RollbackStatus.NOT_STARTED, RollbackStatus.UNAVAILABLE, RollbackStatus.FAILURE);
  private static final String ENVIRONMENT_ROLLBACK_PERMISSION = "core_environment_rollback";
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private InstanceRepository instanceRepository;
  @Inject private PostProdRollbackHelperUtils postProdRollbackHelperUtils;
  @Inject private AccessControlClient accessControlClient;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDOverviewDashboardService cdOverviewDashboardService;
  @Inject private ScopeInfoService scopeInfoService;

  @Override
  public PostProdRollbackCheckDTO checkIfRollbackAllowed(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String instanceKey, String infraMappingId) {
    boolean isRollbackAllowed = true;
    String message = null;
    // GitOps instances have null infrastructureMappingId in MongoDB, but the UI may send empty string.
    // Normalize to null so the MongoDB exact-match query finds the document.
    String normalizedInfraMappingId = EmptyPredicate.isEmpty(infraMappingId) ? null : infraMappingId;
    Instance instance =
        instanceRepository.getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, normalizedInfraMappingId);
    if (instance == null) {
      throw new InvalidRequestException(String.format(
          "Could not find the instance for InstanceKey %s and infraMappingId %s", instanceKey, infraMappingId));
    }
    enrichGitOpsInstanceWithStageMetadata(accountIdentifier, instance);

    boolean retryEnabled = cdFeatureFlagHelper.isEnabled(accountIdentifier, PIPE_ROLLBACK_RETRY_ON_FAILURE);
    Set<RollbackStatus> allowedStatuses =
        retryEnabled ? RETRY_ALLOWED_ROLLBACK_START_STATUSES : BASE_ALLOWED_ROLLBACK_START_STATUSES;

    if (instance.getStageStatus() != Status.SUCCEEDED) {
      isRollbackAllowed = false;
      message = String.format(
          "The deployment stage was not successful in latest execution %s", instance.getLastPipelineExecutionId());
    } else if (!SUPPORTED_INSTANCE_TYPES_FOR_ROLLBACK.contains(instance.getInstanceType())) {
      isRollbackAllowed = false;
      message =
          String.format("The given instanceType %s is not supported for rollback.", instance.getInstanceType().name());
    }
    if (instance.getRollbackStatus() == null) {
      isRollbackAllowed = false;
      message = "Unable to determine rollback status for given Instance";
    } else if (!allowedStatuses.contains(instance.getRollbackStatus())) {
      isRollbackAllowed = false;
      message = String.format(
          "Can not start the Rollback. Rollback has already been triggered and the previous rollback status is: %s",
          instance.getRollbackStatus());
    }

    if (!accessControlCheckForEnvRollbackPermission(
            accountIdentifier, orgIdentifier, projectIdentifier, instance.getEnvIdentifier())) {
      isRollbackAllowed = false;
      message = "User does not have the required permission [core_environment_rollback] to rollback the instances";
    }

    PostProdRollbackSwimLaneInfo swimLaneInfo = null;
    if (isRollbackAllowed) {
      swimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    }

    return PostProdRollbackCheckDTO.builder()
        .isRollbackAllowed(isRollbackAllowed)
        .message(message)
        .swimLaneInfo(swimLaneInfo)
        .build();
  }

  @Override
  public PostProdRollbackResponseDTO triggerRollback(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String instanceKey, String infraMappingId) {
    // Normalize infraMappingId: GitOps instances store null in MongoDB, UI may send empty string.
    String normalizedInfraMappingId = EmptyPredicate.isEmpty(infraMappingId) ? null : infraMappingId;
    PostProdRollbackCheckDTO checkDTO =
        checkIfRollbackAllowed(accountIdentifier, orgIdentifier, projectIdentifier, instanceKey, infraMappingId);
    if (!checkDTO.isRollbackAllowed()) {
      return PostProdRollbackResponseDTO.builder()
          .isRollbackTriggered(false)
          .instanceKey(instanceKey)
          .infraMappingId(infraMappingId)
          .message(checkDTO.getMessage())
          .build();
    }
    Instance instance =
        instanceRepository.getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, normalizedInfraMappingId);
    Object response = null;
    try {
      // Pipeline Identifier is being fetch inside the API for RBAC check
      Set<String> parentUniqueIdSet = new HashSet<>();
      parentUniqueIdSet.add(instance.getParentUniqueId());
      Map<String, Optional<ScopeInfo>> scopeInfoMap =
          scopeInfoService.getScopeInfo(instance.getAccountIdentifier(), parentUniqueIdSet);
      Optional<ScopeInfo> optionalScopeInfo = scopeInfoMap.get(instance.getParentUniqueId());
      if (optionalScopeInfo.isPresent()) {
        ScopeInfo scopeInfo = scopeInfoMap.get(instance.getParentUniqueId()).get();
        response = NGRestUtils.getResponse(pipelineServiceClient.triggerPostExecutionRollback(
            instance.getLastPipelineExecutionId(), scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
            scopeInfo.getProjectIdentifier(), "", instance.getStageNodeExecutionId()));
      } else {
        response = NGRestUtils.getResponse(pipelineServiceClient.triggerPostExecutionRollback(
            instance.getLastPipelineExecutionId(), instance.getAccountIdentifier(), instance.getOrgIdentifier(),
            instance.getProjectIdentifier(), "", instance.getStageNodeExecutionId()));
      }
    } catch (Exception ex) {
      log.error(
          String.format("Could not trigger the rollback for instance with InstanceKey %s and infraMappingId %s: %s",
              instanceKey, infraMappingId, ex.getMessage()));
      instance.setRollbackStatus(RollbackStatus.FAILURE);
      instanceRepository.save(instance);
      throw new InvalidRequestException(String.format("Could not trigger the rollback. %s", ex.getMessage()), ex);
    }
    String planExecutionId = (String) (((Map<String, Map>) response).get("planExecution")).get("uuid");
    // since rollback execution is triggered then mark the rollbackStatus as STARTED.
    instance.setRollbackStatus(RollbackStatus.STARTED);
    instanceRepository.save(instance);
    return PostProdRollbackResponseDTO.builder()
        .isRollbackTriggered(true)
        .instanceKey(instanceKey)
        .infraMappingId(infraMappingId)
        .planExecutionId(planExecutionId)
        .build();
  }

  @Override
  public RollbackResponseDTO triggerRollbackV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, RollbackRequestDTO rollbackRequestDTO) {
    InstanceDetailGroupedByPipelineExecutionList instanceDetailsResponse =
        cdOverviewDashboardService.getInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
            projectIdentifier, rollbackRequestDTO.getServiceIdentifier(), rollbackRequestDTO.getEnvIdentifier(),
            rollbackRequestDTO.getEnvironmentType(), rollbackRequestDTO.getInfraIdentifier(), null,
            rollbackRequestDTO.getArtifact(), rollbackRequestDTO.getChartVersion(), true);

    if (EmptyPredicate.isNotEmpty(instanceDetailsResponse.getInstanceDetailGroupedByPipelineExecutionList())
        && EmptyPredicate.isNotEmpty(
            instanceDetailsResponse.getInstanceDetailGroupedByPipelineExecutionList().get(0).getInstances())
        && checkForMultipleInstancesWithDiffArtifact(
            instanceDetailsResponse.getInstanceDetailGroupedByPipelineExecutionList().get(0).getInstances())) {
      InstanceDetailsDTO instance =
          instanceDetailsResponse.getInstanceDetailGroupedByPipelineExecutionList().get(0).getInstances().get(0);
      String instanceKey = instance.getInstanceKey();
      String infrastructureMappingId = instance.getInfrastructureMappingId();
      PostProdRollbackResponseDTO responseDTO =
          triggerRollback(accountIdentifier, orgIdentifier, projectIdentifier, instanceKey, infrastructureMappingId);
      return RollbackResponseDTO.builder()
          .instanceKey(responseDTO.getInstanceKey())
          .infraMappingId(responseDTO.getInfraMappingId())
          .planExecutionId(responseDTO.getPlanExecutionId())
          .message(responseDTO.getMessage())
          .isRollbackTriggered(responseDTO.isRollbackTriggered())
          .serviceIdentifier(rollbackRequestDTO.getServiceIdentifier())
          .envIdentifier(rollbackRequestDTO.getEnvIdentifier())
          .environmentType(rollbackRequestDTO.getEnvironmentType().name())
          .infraIdentifier(rollbackRequestDTO.getInfraIdentifier())
          .build();
    } else {
      throw new InvalidRequestException("No active instances found for the given combination of service/infra.");
    }
  }

  @Override
  public BatchRollbackResponseDTO triggerRollbackV3(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, BatchRollbackRequestDTO batchRollbackRequestDTO) {
    List<RollbackResponseDTO> results = new ArrayList<>();
    int triggered = 0;
    int failed = 0;

    for (RollbackRequestDTO target : batchRollbackRequestDTO.getTargets()) {
      try {
        RollbackResponseDTO result = triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target);
        results.add(result);
        if (result.isRollbackTriggered()) {
          triggered++;
        } else {
          failed++;
        }
      } catch (Exception ex) {
        log.error("Rollback failed for target service={} env={} infra={}: {}", target.getServiceIdentifier(),
            target.getEnvIdentifier(), target.getInfraIdentifier(), ex.getMessage());
        results.add(RollbackResponseDTO.builder()
                        .isRollbackTriggered(false)
                        .message(ex.getMessage())
                        .serviceIdentifier(target.getServiceIdentifier())
                        .envIdentifier(target.getEnvIdentifier())
                        .environmentType(target.getEnvironmentType().name())
                        .infraIdentifier(target.getInfraIdentifier())
                        .build());
        failed++;
      }
    }

    return BatchRollbackResponseDTO.builder()
        .results(results)
        .totalRollbacksTriggered(triggered)
        .totalRollbacksFailed(failed)
        .build();
  }

  private boolean checkForMultipleInstancesWithDiffArtifact(List<InstanceDetailsDTO> instances) {
    String artifactName = instances.get(0).getArtifactName();
    for (InstanceDetailsDTO instance : instances) {
      if (!artifactName.equals(instance.getArtifactName())) {
        throw new InvalidRequestException(
            "Found instances with different Artifact names for the given service-infra configuration. Please "
            + "exclusively provide the artifact field in the API request body to trigger the rollback.");
      }
    }
    return true;
  }

  private boolean accessControlCheckForEnvRollbackPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envRef) {
    IdentifierRef envIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(envRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, envIdentifierRef.getOrgIdentifier(),
                                             envIdentifierRef.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, envIdentifierRef.getIdentifier()), ENVIRONMENT_ROLLBACK_PERMISSION);
  }

  private void enrichGitOpsInstanceWithStageMetadata(String accountIdentifier, Instance instance) {
    if (instance == null || instance.getStageNodeExecutionId() != null) {
      return;
    }
    if (!InfrastructureKind.GITOPS.equals(instance.getInfrastructureKind())) {
      return;
    }
    if (!cdFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)) {
      return;
    }
    try {
      String planExecutionId = instance.getLastPipelineExecutionId();
      if (planExecutionId == null) {
        return;
      }
      Map<String, GitOpsStageMetadata> stageMetadataMap;
      if (cdFeatureFlagHelper.isEnabled(
              accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
        stageMetadataMap =
            cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(List.of(planExecutionId));
      } else {
        stageMetadataMap = cdOverviewDashboardService.getGitOpsStageMetadataForRollback(List.of(planExecutionId));
      }
      GitOpsStageMetadata stageMeta = stageMetadataMap.get(
          GitOpsStageMetadata.buildKey(planExecutionId, instance.getServiceIdentifier(), instance.getEnvIdentifier()));
      if (stageMeta != null) {
        instance.setStageNodeExecutionId(stageMeta.getStageExecutionId());
        instance.setStageStatus(TimescaleStatusMapper.mapStageStatus(stageMeta.getStageStatus()));
        instance.setRollbackStatus(RollbackStatus.NOT_STARTED);
        instanceRepository.save(instance);
        log.info("Enriched GitOps instance {} with stage metadata for post-prod rollback", instance.getInstanceKey());
      }
    } catch (Exception ex) {
      log.warn("Failed to enrich GitOps instance with stage metadata for rollback: {}", ex.getMessage(), ex);
    }
  }
}
