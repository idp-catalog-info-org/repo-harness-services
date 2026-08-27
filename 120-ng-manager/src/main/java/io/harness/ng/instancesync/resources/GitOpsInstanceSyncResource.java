/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.instancesync.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.gitops.beans.DeleteInstancesRequest;
import io.harness.cdng.gitops.beans.GitOpsInstance;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.dtos.InstanceDTO;
import io.harness.helper.GitOpsRequestDTOMapper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesync.GitopsInstanceSyncService;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Api("gitOpsInstanceSync")
@Path("instancesync/gitops")
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Produces({"application/json"})
@Consumes({"application/json"})
@Hidden
@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class GitOpsInstanceSyncResource {
  private final InstanceService instanceService;
  private final GitopsInstanceSyncService gitopsInstanceSyncService;
  private final GitOpsRequestDTOMapper gitOpsRequestDTOMapper;
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @POST
  @ApiOperation(value = "Process Gitops instances", nickname = "processGitOpsInstances")
  public ResponseDTO<Boolean> processGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotEmpty @QueryParam(NGCommonEntityConstants.AGENT_KEY) String scopedAgentIdentifier,
      @NotNull @Valid List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    if (isEmpty(gitOpsInstanceRequestList)) {
      deleteInstances(accountId, orgIdentifier, projectIdentifier, scopedAgentIdentifier);
      return ResponseDTO.newResponse(Boolean.TRUE);
    } else {
      return processInstances(accountId, scopedAgentIdentifier, gitOpsInstanceRequestList)
          ? ResponseDTO.newResponse(Boolean.TRUE)
          : ResponseDTO.newResponse(Boolean.FALSE);
    }
  }

  private Boolean processInstances(
      String accountId, String scopedAgentIdentifier, List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    Boolean response = false;

    // Fetch hierarchy from DB for this agent and convert to nested map for O(1) lookups
    // Map structure: org -> project -> Set<service>
    List<List<String>> dbHierarchyList =
        gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, scopedAgentIdentifier);
    Map<String, Map<String, Set<String>>> dbHierarchy = buildHierarchyMap(dbHierarchyList);

    Set<String> dbOrgs = dbHierarchy.keySet();

    Set<String> incomingOrgs =
        gitOpsInstanceRequestList.stream().map(GitOpsInstanceRequest::getOrgIdentifier).collect(Collectors.toSet());

    // Delete instances for missing orgs
    Set<String> missingOrgs = Sets.difference(dbOrgs, incomingOrgs).immutableCopy();
    if (isNotEmpty(missingOrgs)) {
      log.debug("Orgs {} not present in incoming instances for agent {}, deleting all their instances", missingOrgs,
          scopedAgentIdentifier);
      gitopsInstanceSyncService.deleteInstancesByParentEntityList(
          accountId, scopedAgentIdentifier, missingOrgs, null, null);
    }

    // group instances per org, project
    Map<String, Map<String, List<GitOpsInstanceRequest>>> instancesPerOrgAndProject =
        gitOpsInstanceRequestList.stream().collect(Collectors.groupingBy(GitOpsInstanceRequest::getOrgIdentifier,
            Collectors.groupingBy(GitOpsInstanceRequest::getProjectIdentifier)));

    for (Map.Entry<String, Map<String, List<GitOpsInstanceRequest>>> instancesPerOrg :
        instancesPerOrgAndProject.entrySet()) {
      String orgId = instancesPerOrg.getKey();

      Set<String> dbProjects = dbHierarchy.getOrDefault(orgId, Collections.emptyMap()).keySet();

      Set<String> incomingProjects = instancesPerOrg.getValue().keySet();

      Set<String> missingProjects = Sets.difference(dbProjects, incomingProjects).immutableCopy();
      if (isNotEmpty(missingProjects)) {
        log.debug(
            "Projects {} under org {} not present in incoming instances for agent {}, deleting all their instances",
            missingProjects, orgId, scopedAgentIdentifier);
        gitopsInstanceSyncService.deleteInstancesByParentEntityList(
            accountId, scopedAgentIdentifier, Collections.singleton(orgId), missingProjects, null);
      }

      for (Map.Entry<String, List<GitOpsInstanceRequest>> instancesPerProject : instancesPerOrg.getValue().entrySet()) {
        String projectId = instancesPerProject.getKey();
        List<GitOpsInstance> processedInstances =
            prepareInstanceSync(accountId, orgId, projectId, instancesPerProject.getValue());

        if (isEmpty(processedInstances)) {
          continue;
        }
        List<InstanceDTO> instanceDTOs =
            gitOpsRequestDTOMapper.toInstanceDTOList(accountId, orgId, projectId, processedInstances);

        if (isEmpty(instanceDTOs)) {
          continue;
        }
        Boolean successProcessingForProject = gitopsInstanceSyncService.processInstanceSync(
            accountId, orgId, projectId, scopedAgentIdentifier, instanceDTOs, dbHierarchy);
        response = response || successProcessingForProject;
      }
    }
    return response;
  }

  private void deleteInstances(
      String accountId, String orgIdentifier, String projectIdentifier, String prefixedAgentIdentifier) {
    gitopsInstanceSyncService.deleteInstancesForAgent(
        accountId, orgIdentifier, projectIdentifier, prefixedAgentIdentifier);
  }

  @DELETE
  @ApiOperation(value = "Delete instances", nickname = "deleteGitOpsInstances")
  public ResponseDTO<DeleteInstancesRequest> deleteGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Valid List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    final List<InstanceDTO> instanceDTOs =
        gitOpsRequestDTOMapper.toInstanceDTOListForDeletion(accountId, gitOpsInstanceRequestList);
    instanceService.deleteAll(instanceDTOs);
    return ResponseDTO.newResponse(
        DeleteInstancesRequest.builder().deletedCount(gitOpsInstanceRequestList.size()).status(true).build());
  }

  // this method cannot be moved to service because cdOverviewDashboardService is in 120-ng-manager and service is in
  // 126-instance
  List<GitOpsInstance> prepareInstanceSync(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    log.debug("Processing {} Gitops instances for sync to NG", gitOpsInstanceRequestList.size());
    List<GitOpsInstance> instanceDTOs = new ArrayList<>();

    final List<GitOpsInstance> gitOpsInstanceDTOs = gitOpsRequestDTOMapper.toGitOpsInstanceList(
        accountIdentifier, orgIdentifier, projectIdentifier, gitOpsInstanceRequestList);
    final Map<String, List<GitOpsInstance>> gitOpsInstancesGroupedByService =
        gitOpsInstanceDTOs.stream().collect(Collectors.groupingBy(GitOpsInstance::getServiceEnvIdentifier));

    final Set<String> envIdentifiers =
        gitOpsInstanceRequestList.stream().map(GitOpsInstanceRequest::getEnvIdentifier).collect(Collectors.toSet());
    final Set<String> serviceIdentifiers =
        gitOpsInstanceRequestList.stream().map(GitOpsInstanceRequest::getServiceIdentifier).collect(Collectors.toSet());
    // Get pipelines execution details
    Map<String, String> serviceEnvIdToPipelineIdMap;
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      serviceEnvIdToPipelineIdMap = cdOverviewDashboardService.getLastPipelineViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers, envIdentifiers);
    } else {
      serviceEnvIdToPipelineIdMap = cdOverviewDashboardService.getLastPipeline(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers, envIdentifiers);
    }

    List<String> pipelineExecutionIdList = serviceEnvIdToPipelineIdMap.values().stream().collect(Collectors.toList());
    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap;
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      pipelineExecutionDetailsMap =
          cdOverviewDashboardService.getPipelineExecutionDetailsViaJooq(pipelineExecutionIdList);
    } else {
      pipelineExecutionDetailsMap = cdOverviewDashboardService.getPipelineExecutionDetails(pipelineExecutionIdList);
    }

    // Get stage-level metadata for post-prod rollback support.
    // The query returns the latest successful GitOps stage per (planExecutionId, service, env). Each instance
    // group looks up its own stage via GitOpsStageMetadata.buildKey, so a pipeline with multiple GitOps stages
    // (one per service+env, post-CDS-114264) stamps the correct stage on each group.
    Map<String, GitOpsStageMetadata> stageMetadataMap = Collections.emptyMap();
    // Post-prod rollback (R1): a successful rollback reverts the workload and a new (rolled-back-to) pod syncs in
    // afterwards, sharing the same forward (account, lastPipelineExecutionId, stageNodeExecutionId) key. It missed
    // the in-band stamping event, so it would default to NOT_STARTED and wrongly offer re-rollback. Batch-check
    // (once per sync) which stage nodes already have a sibling instance with a SUCCESS rollback and let the new pod
    // inherit that status. Both lookups are gated behind CDS_GITOPS_POST_PROD_ROLLBACK.
    Set<String> stageIdsWithSuccessfulRollback = Collections.emptySet();
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)) {
      List<String> planExecutionIds = pipelineExecutionDetailsMap.values()
                                          .stream()
                                          .filter(Objects::nonNull)
                                          .map(ServicePipelineInfo::getPlanExecutionId)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());
      if (!planExecutionIds.isEmpty()) {
        if (ngFeatureFlagHelperService.isEnabled(
                accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
          stageMetadataMap = cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(planExecutionIds);
        } else {
          stageMetadataMap = cdOverviewDashboardService.getGitOpsStageMetadataForRollback(planExecutionIds);
        }
        List<Pair<String, String>> planStagePairs =
            stageMetadataMap.values()
                .stream()
                .filter(Objects::nonNull)
                .map(meta -> Pair.of(meta.getPlanExecutionId(), meta.getStageExecutionId()))
                .distinct()
                .collect(Collectors.toList());
        stageIdsWithSuccessfulRollback =
            gitopsInstanceSyncService.getStageNodeIdsWithSuccessfulRollback(accountIdentifier, planStagePairs);
      }
    }
    final Map<String, GitOpsStageMetadata> finalStageMetadataMap = stageMetadataMap;
    final Set<String> finalStageIdsWithSuccessfulRollback = stageIdsWithSuccessfulRollback;

    for (Map.Entry<String, List<GitOpsInstance>> instanceGroup : gitOpsInstancesGroupedByService.entrySet()) {
      // Get pipeline Info
      List<GitOpsInstance> instances = instanceGroup.getValue();
      String pipelineServiceEnvId = instanceGroup.getKey();
      if (!serviceEnvIdToPipelineIdMap.containsKey(pipelineServiceEnvId)) {
        log.warn("gitOps instance is not associated to a pipeline with serviceId and environmentId {}",
            pipelineServiceEnvId);
        continue;
      }
      String pipelineId = serviceEnvIdToPipelineIdMap.get(pipelineServiceEnvId);

      if (!pipelineExecutionDetailsMap.containsKey(pipelineId)) {
        log.warn("gitOps instance pipeline: {}, does not have any executions yet", pipelineId);
        continue;
      }
      ServicePipelineInfo pipelineInfo = pipelineExecutionDetailsMap.get(pipelineId);

      // set pipeline details
      if (pipelineInfo != null) {
        instances.forEach(gitInstance -> {
          gitInstance.setPipelineName(pipelineInfo.getName());
          gitInstance.setLastExecutedAt(pipelineInfo.getLastExecutedAt());
          gitInstance.setPipelineExecutionId(pipelineInfo.getPlanExecutionId());
          gitInstance.setLastDeployedById(pipelineInfo.getDeployedById());
          gitInstance.setLastDeployedByName(pipelineInfo.getDeployedByName());
          GitOpsStageMetadata stageMeta = finalStageMetadataMap.get(GitOpsStageMetadata.buildKey(
              pipelineInfo.getPlanExecutionId(), gitInstance.getServiceIdentifier(), gitInstance.getEnvIdentifier()));
          if (stageMeta != null) {
            gitInstance.setStageNodeExecutionId(stageMeta.getStageExecutionId());
            gitInstance.setStageStatus(stageMeta.getStageStatus());
            if (finalStageIdsWithSuccessfulRollback.contains(stageMeta.getStageExecutionId())) {
              gitInstance.setInheritSuccessfulRollbackStatus(true);
              log.info("Inheriting rollbackStatus=SUCCESS for GitOps instance (account={}, planExecutionId={}, "
                      + "stageNodeExecutionId={}, service={}, env={}) "
                      + "because a sibling instance on the same rolled-back execution already has a successful "
                      + "rollback; re-rollback will be suppressed",
                  accountIdentifier, stageMeta.getPlanExecutionId(), stageMeta.getStageExecutionId(),
                  gitInstance.getServiceIdentifier(), gitInstance.getEnvIdentifier());
            }
          }
          instanceDTOs.add(gitInstance);
        });
      } else {
        log.warn("gitOps instance pipeline does not have any execution details for pipeline {}", pipelineId);
      }
    }
    return instanceDTOs;
  }

  private Map<String, Map<String, Set<String>>> buildHierarchyMap(List<List<String>> hierarchyList) {
    Map<String, Map<String, Set<String>>> hierarchyMap = new HashMap<>();

    for (List<String> triplet : hierarchyList) {
      if (triplet == null || triplet.size() < 3) {
        continue;
      }

      String org = triplet.get(0);
      String project = triplet.get(1);
      String service = triplet.get(2);

      // Skip entries with empty org (required)
      if (isEmpty(org)) {
        continue;
      }

      // Get or create org map
      Map<String, Set<String>> projectMap = hierarchyMap.computeIfAbsent(org, k -> new HashMap<>());

      // Skip entries with empty project
      if (isEmpty(project)) {
        continue;
      }

      // Get or create project set
      Set<String> serviceSet = projectMap.computeIfAbsent(project, k -> new HashSet<>());

      // Add service if not empty
      if (isNotEmpty(service)) {
        serviceSet.add(service);
      }
    }

    return hierarchyMap;
  }
}
