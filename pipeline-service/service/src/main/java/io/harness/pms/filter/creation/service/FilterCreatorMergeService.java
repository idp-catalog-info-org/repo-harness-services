/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.creation.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitsync.interceptor.GitSyncConstants.DEFAULT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.manage.GlobalContextManager;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ErrorResponseV2;
import io.harness.pms.contracts.plan.FilterCreationBlobRequest;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.FilterCreationResponse;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.filter.creation.FilterCreationBlobResponseUtils;
import io.harness.pms.filter.creation.FilterCreationResponseWrapper;
import io.harness.pms.filter.creation.FilterCreationResponseWrapper.FilterCreationResponseWrapperBuilder;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.ConnectorScopeHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.references.filter.FilterCreationParams;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.setupusage.PipelineSetupUsageHelper;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorServiceHelper;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class FilterCreatorMergeService {
  private final PmsSdkHelper pmsSdkHelper;
  private final PipelineSetupUsageHelper pipelineSetupUsageHelper;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  private final GitSyncSdkService gitSyncSdkService;
  private final PrincipalInfoHelper principalInfoHelper;
  private final TriggeredByHelper triggeredByHelper;

  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoClient scopeInfoClient;
  private final ConnectorScopeHelper connectorScopeHelper;
  public static final int MAX_DEPTH = 10;
  public static final String FILTER_CREATION_TIME_METRIC_NAME = "filter_creation_time";
  public static final String FILTER_CREATION_MODULE_TIME_METRIC_NAME = "filter_creation_module_time";
  private static final String MODULE_METRIC_LABEL = "module";
  private static final String STATUS_METRIC_LABEL = "status";
  private static final String METRIC_STATUS_SUCCESS = "SUCCESS";
  private static final String METRIC_STATUS_FAILURE = "FAILURE";
  private final Executor executor;
  @Inject private MetricService metricService;

  @Inject
  public FilterCreatorMergeService(PmsSdkHelper pmsSdkHelper, PipelineSetupUsageHelper pipelineSetupUsageHelper,
      PmsGitSyncHelper pmsGitSyncHelper, PMSPipelineTemplateHelper pmsPipelineTemplateHelper,
      GitSyncSdkService gitSyncSdkService, PrincipalInfoHelper principalInfoHelper, TriggeredByHelper triggeredByHelper,
      @Named("FilterCreatorMergeExecutorService") Executor executor, PmsFeatureFlagHelper pmsFeatureFlagHelper,
      ScopeInfoClient scopeInfoClient, ConnectorScopeHelper connectorScopeHelper) {
    this.pmsSdkHelper = pmsSdkHelper;
    this.pipelineSetupUsageHelper = pipelineSetupUsageHelper;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.pmsPipelineTemplateHelper = pmsPipelineTemplateHelper;
    this.gitSyncSdkService = gitSyncSdkService;
    this.principalInfoHelper = principalInfoHelper;
    this.triggeredByHelper = triggeredByHelper;
    this.executor = executor;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.scopeInfoClient = scopeInfoClient;
    this.connectorScopeHelper = connectorScopeHelper;
  }

  public FilterCreatorMergeServiceResponse getPipelineInfo(FilterCreationParams filterCreationParams)
      throws IOException {
    long startTs = System.currentTimeMillis();
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put("accountId", pipelineEntity.getAccountId());
    ExecutionStatus metricStatus = ExecutionStatus.FAILED;
    boolean metricPublished = false;
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("[PMS_FilterCreatorMergeService]")) {
      FilterCreationResult result = buildFilterCreationResponse(filterCreationParams);
      metricStatus = ExecutionStatus.SUCCESS;
      pipelineSetupUsageHelper.publishSetupUsageEvent(filterCreationParams, result.response.getReferredEntitiesList());
      return FilterCreatorMergeServiceResponse.builder()
          .filters(result.filters)
          .stageCount(result.response.getStageCount())
          .stageNames(new ArrayList<>(result.response.getStageNamesList()))
          .build();
    } catch (FilterCreatorException ex) {
      if (isNotEmpty(ex.errorModules)) {
        for (String module : ex.errorModules) {
          abstractions.put("module", module);
          publishFilterCreationMetric(startTs, abstractions, metricStatus);
        }
        metricPublished = true;
      }
      throw ex;
    } finally {
      if (!metricPublished) {
        publishFilterCreationMetric(startTs, abstractions, metricStatus);
      }
    }
  }

  public List<EntityDetailProtoDTO> getReferredEntities(FilterCreationParams filterCreationParams) throws IOException {
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("[PMS_GetReferredEntities]")) {
      FilterCreationResult result = buildFilterCreationResponse(filterCreationParams);
      return result.response.getReferredEntitiesList();
    }
  }

  /**
   * Builds the filter creation response by processing pipeline YAML and collecting referred entities.
   * This method handles scope resolution, filter generation, template references, and git connector references.
   *
   * @param filterCreationParams the parameters containing pipeline entity and scope information
   * @return FilterCreationResult containing the response and generated filters
   * @throws IOException if YAML processing fails
   */
  @VisibleForTesting
  FilterCreationResult buildFilterCreationResponse(FilterCreationParams filterCreationParams) throws IOException {
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    String accountId = pipelineEntity.getAccountId();
    ScopeInfo scopeInfo = filterCreationParams.getScopeInfo();
    if (scopeInfo == null && pipelineEntity.getParentUniqueId() != null) {
      scopeInfo = NGRestUtils
                      .getResponse(scopeInfoClient.getScopeInfos(
                          pipelineEntity.getAccountIdentifier(), Set.of(pipelineEntity.getParentUniqueId())))
                      .get(pipelineEntity.getParentUniqueId())
                      .orElseThrow();
    }
    boolean isParentIdQueryingEnabled = true;
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();

    Map<String, PlanCreatorServiceInfo> services = getServices();
    Dependencies dependencies = getDependencies(pipelineEntity.getYaml());
    Map<String, String> filters = new HashMap<>();
    SetupMetadata.Builder setupMetadataBuilder = getSetupMetadataBuilder(
        accountId, orgId, projectId, scopeInfo.getUniqueId(), pipelineEntity.getHarnessVersion());
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    if (gitSyncBranchContext != null) {
      setupMetadataBuilder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    setupMetadataBuilder.setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext());
    if (!gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
      setupMetadataBuilder.setTriggeredInfo(triggeredByHelper.getFromSecurityContext());
    }
    FilterCreationBlobResponse response =
        obtainFiltersRecursively(services, dependencies, filters, setupMetadataBuilder.build(), accountId);
    validateFilterCreationBlobResponse(response);
    if (GitContextHelper.isFullSyncFlow()) {
      // entity is updated by the PL team
      deleteExistingSetupUsages(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
    }
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      List<EntityDetailProtoDTO> templateReferences = pmsPipelineTemplateHelper.getTemplateReferencesForGivenYaml(
          accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());
      response = response.toBuilder().addAllReferredEntities(templateReferences).build();
    }
    // publishSetupUsageEvent()) needs to be changed after entitySetupUsages is updated by the PL team
    Optional<EntityDetailProtoDTO> gitConnectorReference =
        getGitConnectorReference(pipelineEntity, scopeInfo, isParentIdQueryingEnabled);
    if (gitConnectorReference.isPresent()) {
      response = response.toBuilder().addAllReferredEntities(List.of(gitConnectorReference.get())).build();
    }
    return new FilterCreationResult(response, filters);
  }

  /**
   * Container for filter creation response and generated filters.
   * Used to return multiple values from the filter creation processing.
   *
   * @param response the complete filter creation response with referred entities and stage information
   * @param filters  the map of generated filters (mutable, populated during recursive filter processing)
   */
  private record FilterCreationResult(FilterCreationBlobResponse response, Map<String, String> filters) {}

  private void deleteExistingSetupUsages(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
      GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      String orgId = scopeInfo.getOrgIdentifier();
      String projectId = scopeInfo.getProjectIdentifier();
      pipelineSetupUsageHelper.deleteExistingSetupUsages(pipelineEntity.getAccountIdentifier(), orgId, projectId,
          pipelineEntity.getIdentifier(), scopeInfo, isParentIdQueryingEnabled);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
  }

  public SetupMetadata.Builder getSetupMetadataBuilder(
      String accountId, String orgId, String projectId, String harnessVersion) {
    return getSetupMetadataBuilder(accountId, orgId, projectId, null, harnessVersion);
  }

  public SetupMetadata.Builder getSetupMetadataBuilder(
      String accountId, String orgId, String projectId, String parentUniqueId, String harnessVersion) {
    SetupMetadata.Builder setupMetaData = SetupMetadata.newBuilder().setAccountId(accountId);
    if (isNotEmpty(projectId)) {
      setupMetaData.setProjectId(projectId);
    }
    if (isNotEmpty(orgId)) {
      setupMetaData.setOrgId(orgId);
    }
    if (isNotEmpty(parentUniqueId)) {
      setupMetaData.setParentUniqueId(parentUniqueId);
    }
    setupMetaData.setHarnessVersion(EmptyPredicate.isNotEmpty(harnessVersion) ? harnessVersion : HarnessYamlVersion.V0);
    return setupMetaData;
  }

  public Map<String, PlanCreatorServiceInfo> getServices() {
    return pmsSdkHelper.getServicesV2();
  }

  public Dependencies getDependencies(String yaml) throws IOException {
    String processedYaml = YamlUtils.injectUuid(yaml);
    YamlField topRootFieldInYaml = YamlUtils.getTopRootFieldInYaml(processedYaml);

    return Dependencies.newBuilder()
        .setYaml(processedYaml)
        .putDependencies(topRootFieldInYaml.getNode().getUuid(), topRootFieldInYaml.getNode().getYamlPath())
        .build();
  }

  @VisibleForTesting
  public void validateFilterCreationBlobResponse(FilterCreationBlobResponse response) throws IOException {
    if (isNotEmpty(response.getDeps().getDependenciesMap())) {
      throw new InvalidRequestException(PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(response.getDeps()));
    }
  }

  @VisibleForTesting
  public FilterCreationBlobResponse obtainFiltersRecursively(Map<String, PlanCreatorServiceInfo> services,
      Dependencies initialDependencies, Map<String, String> filters, SetupMetadata setupMetadata, String accountId)
      throws IOException {
    try (AutoLogContext autoLogContext = PlanCreatorServiceHelper.autoLogContextFromSetupMetadata(setupMetadata)) {
      Dependencies initialDependenciesWithoutTemplates =
          FilterCreationBlobResponseUtils.removeTemplateDependencies(initialDependencies);
      FilterCreationBlobResponse.Builder finalResponseBuilder =
          FilterCreationBlobResponse.newBuilder().setDeps(initialDependenciesWithoutTemplates);

      if (isEmpty(services) || isEmpty(initialDependenciesWithoutTemplates.getDependenciesMap())) {
        return finalResponseBuilder.build();
      }
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap =
          new HashMap<>();
      YamlField fullYamlField = YamlUtils.readTree(finalResponseBuilder.getDeps().getYaml());
      for (int i = 0; i < MAX_DEPTH && EmptyPredicate.isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap());
           i++) {
        pmsSdkHelper.addDependencyToServiceDependencyMapBasedOnPriority(services,
            finalResponseBuilder.getDeps().getDependenciesMap(), fullYamlField, serviceToDependencyMap,
            setupMetadata.getHarnessVersion(), accountId);
        FilterCreationBlobResponse currIterResponse =
            obtainFiltersPerIterationV1(serviceToDependencyMap, finalResponseBuilder, filters, setupMetadata);

        FilterCreationBlobResponseUtils.mergeResolvedDependencies(finalResponseBuilder, currIterResponse);
        if (isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap())) {
          throw new InvalidRequestException(
              PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(finalResponseBuilder.getDeps()));
        }
        FilterCreationBlobResponseUtils.mergeDependencies(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.updateStageCount(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.mergeReferredEntities(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.mergeStageNames(finalResponseBuilder, currIterResponse);
      }

      return finalResponseBuilder.build();
    }
  }

  @VisibleForTesting
  public FilterCreationBlobResponse obtainFiltersPerIterationV1(
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      FilterCreationBlobResponse.Builder responseBuilder, Map<String, String> filters, SetupMetadata setupMetadata) {
    CompletableFutures<FilterCreationResponseWrapper> completableFutures = new CompletableFutures<>(executor);

    for (Map.Entry<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceEntry :
        serviceToDependencyMap.entrySet()) {
      Map<String, String> dependencyMap = new HashMap<>();
      serviceEntry.getValue().forEach(o -> dependencyMap.put(o.getKey(), o.getValue()));
      Dependencies dep = PmsSdkHelper.createBatchDependency(responseBuilder.getDeps(), dependencyMap);
      if (!pmsSdkHelper.containsSupportedDependencyByYamlPath(
              serviceEntry.getKey(), dep, setupMetadata.getAccountId(), setupMetadata.getHarnessVersion())) {
        continue;
      }

      completableFutures.supplyAsync(() -> {
        String moduleName = serviceEntry.getKey().getKey();
        long moduleStartTs = System.currentTimeMillis();
        String moduleStatus = METRIC_STATUS_SUCCESS;
        FilterCreationResponseWrapperBuilder builder = FilterCreationResponseWrapper.builder().serviceName(moduleName);
        try {
          FilterCreationResponse filterCreationResponse =
              serviceEntry.getKey().getValue().getPlanCreationClient().createFilter(
                  FilterCreationBlobRequest.newBuilder().setDeps(dep).setSetupMetadata(setupMetadata).build());
          if (filterCreationResponse.getResponseCase() == FilterCreationResponse.ResponseCase.ERRORRESPONSE) {
            builder.errorResponse(filterCreationResponse.getErrorResponse());
            moduleStatus = METRIC_STATUS_FAILURE;
          } else if (filterCreationResponse.getResponseCase() == FilterCreationResponse.ResponseCase.ERRORRESPONSEV2) {
            builder.errorResponseV2(filterCreationResponse.getErrorResponseV2());
            moduleStatus = METRIC_STATUS_FAILURE;
          } else {
            builder.response(filterCreationResponse.getBlobResponse());
          }
        } catch (StatusRuntimeException ex) {
          moduleStatus = METRIC_STATUS_FAILURE;
          log.error(String.format("Error connecting with service: [%s]. Is this service Running?", moduleName), ex);
          builder.errorResponse(ErrorResponse.newBuilder()
                                    .addMessages(String.format("Error connecting with service: [%s]", moduleName))
                                    .build());
        } finally {
          recordModuleFilterCreationMetric(moduleName, moduleStatus, System.currentTimeMillis() - moduleStartTs);
        }
        return builder.build();
      });
    }

    List<ErrorResponse> errorResponses;
    List<ErrorResponseV2> errorResponsesV2;
    List<String> errorModules;
    FilterCreationBlobResponse.Builder currentIteration = FilterCreationBlobResponse.newBuilder();
    try {
      List<FilterCreationResponseWrapper> filterCreationResponseWrappers =
          completableFutures.allOf().get(PlanCreatorConstants.SDK_CREATOR_GRPC_TIMEOUT_MINUTES, TimeUnit.MINUTES);
      errorResponses = filterCreationResponseWrappers.stream()
                           .filter(resp -> resp != null && resp.getErrorResponse() != null)
                           .map(FilterCreationResponseWrapper::getErrorResponse)
                           .collect(Collectors.toList());
      errorResponsesV2 = filterCreationResponseWrappers.stream()
                             .filter(resp -> resp != null && resp.getErrorResponseV2() != null)
                             .map(FilterCreationResponseWrapper::getErrorResponseV2)
                             .collect(Collectors.toList());
      errorModules =
          filterCreationResponseWrappers.stream()
              .filter(resp -> resp != null && (resp.getErrorResponse() != null || resp.getErrorResponseV2() != null))
              .map(FilterCreationResponseWrapper::getServiceName)
              .collect(Collectors.toList());
      if (EmptyPredicate.isEmpty(errorResponses) && EmptyPredicate.isEmpty(errorResponsesV2)) {
        filterCreationResponseWrappers.forEach(
            response -> FilterCreationBlobResponseUtils.mergeResponses(currentIteration, response, filters));
      }
    } catch (Exception ex) {
      throw new UnexpectedException("Error fetching filter creation response from service", ex);
    }

    PmsExceptionUtils.checkAndThrowFilterCreatorException(errorResponses, errorResponsesV2, errorModules);
    return currentIteration.build();
  }

  @VisibleForTesting
  public Optional<EntityDetailProtoDTO> getGitConnectorReference(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isGitSimplificationEnabled(pipelineEntity, scopeInfo, isParentIdQueryingEnabled)) {
      String connectorRef = getConnectorRefFromPipelineEntity(pipelineEntity);
      if (GitAwareContextHelper.isNullOrDefault(connectorRef)) {
        return Optional.empty();
      }
      Scope scope = Scope.of(scopeInfo);
      ScopeInfo connectorScopeInfo = connectorScopeHelper.getConnectorScopeInfo(scope, connectorRef);
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, connectorScopeInfo);
      identifierRef.setParentUniqueId(connectorScopeInfo.getUniqueId());
      IdentifierRefProtoDTO connectorReference = IdentifierRefProtoDTOHelper.fromIdentifierRef(identifierRef);
      EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                  .setIdentifierRef(connectorReference)
                                                  .setType(EntityTypeProtoEnum.CONNECTORS)
                                                  .build();
      return Optional.of(connectorDetails);
    }
    return Optional.empty();
  }

  private String getConnectorRefFromPipelineEntity(PipelineEntity pipelineEntity) {
    if (isNotEmpty(pipelineEntity.getConnectorRef())) {
      return pipelineEntity.getConnectorRef();
    }
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (Objects.isNull(gitEntityInfo)) {
      return DEFAULT;
    }
    return gitEntityInfo.getConnectorRef();
  }

  private boolean isGitSimplificationEnabled(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String orgId = scopeInfo.getOrgIdentifier();
    String projectId = scopeInfo.getProjectIdentifier();
    return StoreType.REMOTE.equals(pipelineEntity.getStoreType())
        && gitSyncSdkService.isGitSimplificationEnabled(pipelineEntity.getAccountIdentifier(), orgId, projectId);
  }

  public void deleteSetupReferences(PipelineEntity pipelineEntity, ScopeInfo scopeInfo) {
    pipelineSetupUsageHelper.deleteSetupUsagesForGivenPipeline(
        pipelineEntity, new ArrayList<>(pipelineSetupUsageHelper.entityTypesSupportedByNGCore), scopeInfo);
  }

  private void publishFilterCreationMetric(long startTs, Map<String, String> abstractions, ExecutionStatus status) {
    long endTs = System.currentTimeMillis();
    abstractions.put("status", status.name());
    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(abstractions)) {
      metricService.recordMetric(FILTER_CREATION_TIME_METRIC_NAME, endTs - startTs);
    }
    log.info("[PMS_FILTER] Time taken to complete filter creation: {}ms ", endTs - startTs);
  }

  private void recordModuleFilterCreationMetric(String module, String status, long durationMs) {
    Map<String, String> metricContext = new HashMap<>();
    metricContext.put(MODULE_METRIC_LABEL, module);
    metricContext.put(STATUS_METRIC_LABEL, status);
    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContext)) {
      metricService.recordMetric(FILTER_CREATION_MODULE_TIME_METRIC_NAME, durationMs);
    }
    log.info("[PMS_FILTER] Module [{}] filter creation took {}ms (status={})", module, durationMs, status);
  }
}
