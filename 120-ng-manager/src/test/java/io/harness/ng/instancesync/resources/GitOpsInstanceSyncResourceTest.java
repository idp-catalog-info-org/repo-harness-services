/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.instancesync.resources;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.GitOpsInstance;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.helper.GitOpsRequestDTOMapper;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesync.GitopsInstanceSyncService;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(GITOPS)
public class GitOpsInstanceSyncResourceTest extends NgManagerTestBase {
  String accountId = "accountId";
  String orgId = "orgId";
  String projId = "projectId";
  String clusterId = "clusterId";
  String agentId = "agentId";
  String serviceId = "serviceId";
  String envId = "envId";
  String buildId = "buildId";
  long millis = System.currentTimeMillis();
  String commonId = "-12345";
  String pipelineId = "pipelineId";
  String podId = "podId";
  String containerId = "containerId";
  String namespace = "namespace";
  String pipelineExecutionId = "pipelineExecutionId";
  String planExecutionId = "planExecutionId";
  String status = "SUCCESS";

  @Mock private CDOverviewDashboardService cdOverviewDashboardService;

  @Mock private GitOpsRequestDTOMapper gitOpsRequestDTOMapper;

  @Mock private GitopsInstanceSyncService gitopsInstanceSyncService;

  @Mock private InstanceService instanceService;

  NGFeatureFlagHelperService ngFeatureFlagHelperService = mock(NGFeatureFlagHelperService.class);

  @InjectMocks GitOpsInstanceSyncResource gitOpsInstanceSyncResource;

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    GitOpsInstance gitOpsInstance =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);
    assertEquals(gitOpsInstance.getAccountIdentifier(), accountId);
    assertEquals(gitOpsInstance.getOrgIdentifier(), orgId);
    assertEquals(gitOpsInstance.getProjectIdentifier(), projId);
    assertEquals(gitOpsInstance.getServiceIdentifier(), serviceId);
    assertEquals(gitOpsInstance.getEnvIdentifier(), envId);
    assertEquals(gitOpsInstance.getClusterIdentifier(), clusterId);
    assertEquals(gitOpsInstance.getAgentIdentifier(), agentId);
    assertEquals(gitOpsInstance.getLastDeployedAt(), millis);
    assertEquals(gitOpsInstance.getPipelineExecutionId(), planExecutionId);
  }

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  public void testPrepareInstanceSyncWithoutPipelineDetails() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(new HashMap<>());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(new ArrayList<>())).thenReturn(new HashMap<>());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    assertThat(gitOpsInstanceSyncResource.prepareInstanceSync(
                   accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_StampsStageMetadataWhenFFEnabled() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));

    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(true);

    String stageExecutionId = "stageExecutionId";
    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId(serviceId)
                                        .envId(envId)
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    when(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Arrays.asList(planExecutionId)))
        .thenReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId), stageMeta));

    GitOpsInstance result =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);

    assertEquals(stageExecutionId, result.getStageNodeExecutionId());
    assertEquals("SUCCESS", result.getStageStatus());
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_InheritsSuccessRollbackStatusFromSibling() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(true);

    String stageExecutionId = "stageExecutionId";
    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId(serviceId)
                                        .envId(envId)
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    when(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Arrays.asList(planExecutionId)))
        .thenReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId), stageMeta));
    // A sibling on the same rolled-back execution already succeeded -> the new pod must inherit SUCCESS.
    when(gitopsInstanceSyncService.getStageNodeIdsWithSuccessfulRollback(
             eq(accountId), eq(Arrays.asList(Pair.of(planExecutionId, stageExecutionId)))))
        .thenReturn(Stream.of(stageExecutionId).collect(Collectors.toSet()));

    GitOpsInstance result =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);

    assertEquals(stageExecutionId, result.getStageNodeExecutionId());
    assertThat(result.isInheritSuccessfulRollbackStatus()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_DoesNotInheritWhenNoSuccessfulSibling() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(true);

    String stageExecutionId = "stageExecutionId";
    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId(serviceId)
                                        .envId(envId)
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    when(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Arrays.asList(planExecutionId)))
        .thenReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId), stageMeta));
    when(gitopsInstanceSyncService.getStageNodeIdsWithSuccessfulRollback(eq(accountId), any()))
        .thenReturn(Collections.emptySet());

    GitOpsInstance result =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);

    // No successful sibling -> inherit flag stays false; the mapper will later default it to NOT_STARTED.
    assertThat(result.isInheritSuccessfulRollbackStatus()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_SkipsStageMetadataWhenFFDisabled() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));

    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(false);

    GitOpsInstance result =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);

    assertThat(result.getStageNodeExecutionId()).isNull();
    assertThat(result.getStageStatus()).isNull();
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollbackViaJooq(any());
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_UsesJooqVariantWhenJooqFFEnabled() {
    when(cdOverviewDashboardService.getLastPipelineViaJooq(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetailsViaJooq(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));

    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(true);
    when(ngFeatureFlagHelperService.isEnabled(
             accountId, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER))
        .thenReturn(true);

    String stageExecutionId = "stageExecJooq";
    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId(serviceId)
                                        .envId(envId)
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    when(cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(Arrays.asList(planExecutionId)))
        .thenReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId), stageMeta));

    GitOpsInstance result =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);

    assertEquals(stageExecutionId, result.getStageNodeExecutionId());
    assertEquals("SUCCESS", result.getStageStatus());
    verify(cdOverviewDashboardService, times(1)).getGitOpsStageMetadataForRollbackViaJooq(any());
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync_StampsCorrectStagePerServiceEnvForMultiStagePipeline() {
    // Regression guard for CDS-121660 gap 8.1: one pipeline (single planExecutionId) that deploys two
    // service+env pairs produces two GitOps stages. Each instance group must receive ITS OWN stage, not the
    // "latest stage" for the whole plan.
    String serviceId2 = "serviceId2";
    String envId2 = "envId2";

    GitOpsInstanceRequest req2 = GitOpsInstanceRequest.builder()
                                     .accountIdentifier(accountId)
                                     .orgIdentifier(orgId)
                                     .projectIdentifier(projId)
                                     .clusterIdentifier(clusterId)
                                     .agentIdentifier(agentId)
                                     .serviceIdentifier(serviceId2)
                                     .envIdentifier(envId2)
                                     .buildId(buildId)
                                     .lastDeployedAt(millis)
                                     .pipelineName(pipelineId)
                                     .pipelineExecutionId(pipelineId + commonId)
                                     .instanceInfo(getInstanceInfo())
                                     .build();
    List<GitOpsInstanceRequest> requests = Arrays.asList(getGitOpsInstanceRequest(), req2);

    GitOpsInstance inst2 = GitOpsInstance.builder()
                               .accountIdentifier(accountId)
                               .orgIdentifier(orgId)
                               .projectIdentifier(projId)
                               .clusterIdentifier(clusterId)
                               .agentIdentifier(agentId)
                               .serviceIdentifier(serviceId2)
                               .envIdentifier(envId2)
                               .buildId(buildId)
                               .lastDeployedAt(millis)
                               .pipelineName(pipelineId)
                               .pipelineExecutionId(pipelineId + commonId)
                               .serviceEnvIdentifier(serviceId2 + "-" + envId2)
                               .instanceInfo(getInstanceInfo())
                               .build();

    // Both service+env groups resolve to the same pipeline execution → same planExecutionId.
    Map<String, String> lastPipeline = new HashMap<>();
    lastPipeline.put(serviceId + "-" + envId, pipelineExecutionId);
    lastPipeline.put(serviceId2 + "-" + envId2, pipelineExecutionId);
    when(cdOverviewDashboardService.getLastPipeline(eq(accountId), eq(orgId), eq(projId), anySet(), anySet()))
        .thenReturn(lastPipeline);
    when(cdOverviewDashboardService.getPipelineExecutionDetails(any())).thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(accountId, orgId, projId, requests))
        .thenReturn(Arrays.asList(getGitOpsInstance(), inst2));

    when(ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK)).thenReturn(true);

    GitOpsStageMetadata stageMeta1 = GitOpsStageMetadata.builder()
                                         .planExecutionId(planExecutionId)
                                         .serviceId(serviceId)
                                         .envId(envId)
                                         .stageExecutionId("stage-1")
                                         .stageStatus("SUCCESS")
                                         .build();
    GitOpsStageMetadata stageMeta2 = GitOpsStageMetadata.builder()
                                         .planExecutionId(planExecutionId)
                                         .serviceId(serviceId2)
                                         .envId(envId2)
                                         .stageExecutionId("stage-2")
                                         .stageStatus("SUCCESS")
                                         .build();
    Map<String, GitOpsStageMetadata> metaMap = new HashMap<>();
    metaMap.put(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId), stageMeta1);
    metaMap.put(GitOpsStageMetadata.buildKey(planExecutionId, serviceId2, envId2), stageMeta2);
    when(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Arrays.asList(planExecutionId)))
        .thenReturn(metaMap);

    List<GitOpsInstance> results = gitOpsInstanceSyncResource.prepareInstanceSync(accountId, orgId, projId, requests);

    Map<String, GitOpsInstance> bySvc =
        results.stream().collect(Collectors.toMap(GitOpsInstance::getServiceIdentifier, i -> i));
    assertEquals("stage-1", bySvc.get(serviceId).getStageNodeExecutionId());
    assertEquals("stage-2", bySvc.get(serviceId2).getStageNodeExecutionId());
  }

  private Map<String, String> getLastPipeline() {
    Map<String, String> pipeline = new HashMap<>();
    pipeline.put(serviceId + "-" + envId, pipelineExecutionId);
    return pipeline;
  }

  private Map<String, ServicePipelineInfo> getPipelineExecutionDetails() {
    Map<String, ServicePipelineInfo> pipeline = new HashMap<>();
    ServicePipelineInfo pipelineInfo = ServicePipelineInfo.builder()
                                           .pipelineExecutionId(pipelineExecutionId)
                                           .planExecutionId(planExecutionId)
                                           .status(status)
                                           .build();
    pipeline.put(pipelineExecutionId, pipelineInfo);
    return pipeline;
  }

  private GitOpsInstanceRequest getGitOpsInstanceRequest() {
    return GitOpsInstanceRequest.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .clusterIdentifier(clusterId)
        .agentIdentifier(agentId)
        .serviceIdentifier(serviceId)
        .envIdentifier(envId)
        .buildId(buildId)
        .lastDeployedAt(millis)
        .pipelineName(pipelineId)
        .pipelineExecutionId(pipelineId + commonId)
        .instanceInfo(getInstanceInfo())
        .build();
  }

  private GitOpsInstance getGitOpsInstance() {
    return GitOpsInstance.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .clusterIdentifier(clusterId)
        .agentIdentifier(agentId)
        .serviceIdentifier(serviceId)
        .envIdentifier(envId)
        .buildId(buildId)
        .lastDeployedAt(millis)
        .pipelineName(pipelineId)
        .pipelineExecutionId(pipelineId + commonId)
        .serviceEnvIdentifier(serviceId + "-" + envId)
        .instanceInfo(getInstanceInfo())
        .build();
  }

  private GitOpsInstanceRequest.K8sBasicInfo getInstanceInfo() {
    return GitOpsInstanceRequest.K8sBasicInfo.builder()
        .agentIdentifier(agentId)
        .clusterIdentifier(clusterId)
        .podId(podId + commonId)
        .podName(podId)
        .namespace(namespace)
        .containerList(Arrays.asList(getK8sContainers()))
        .build();
  }

  private GitOpsInstanceRequest.K8sContainer getK8sContainers() {
    return GitOpsInstanceRequest.K8sContainer.builder()
        .containerId(containerId + commonId)
        .name(containerId)
        .image(null)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_NoMissingOrgs_NoOrgDeletion() {
    // Setup: DB has org1 and org2
    List<List<String>> dbHierarchyList =
        Arrays.asList(Arrays.asList("org1", "proj1", "svc1"), Arrays.asList("org2", "proj1", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests also have org1 and org2 (no missing orgs)
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build(),
        GitOpsInstanceRequest.builder()
            .orgIdentifier("org2")
            .projectIdentifier("proj2")
            .serviceIdentifier("svc1")
            .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify NO org-level deletion was triggered (all orgs are present)
    verify(gitopsInstanceSyncService, never())
        .deleteInstancesByParentEntityList(anyString(), anyString(), anySet(), isNull(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_OneMissingOrg_DeletesOrg() {
    // Setup: DB has org1, org2, org3
    List<List<String>> dbHierarchyList = Arrays.asList(Arrays.asList("org1", "proj1", "svc1"),
        Arrays.asList("org2", "proj1", "svc1"), Arrays.asList("org3", "proj1", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests only have org1 and org2 (missing org3)
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build(),
        GitOpsInstanceRequest.builder()
            .orgIdentifier("org2")
            .projectIdentifier("proj1")
            .serviceIdentifier("svc1")
            .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify org-level deletion was triggered for org3
    ArgumentCaptor<Set<String>> orgCaptor = ArgumentCaptor.forClass(Set.class);
    verify(gitopsInstanceSyncService, times(1))
        .deleteInstancesByParentEntityList(eq(accountId), eq(agentId), orgCaptor.capture(), isNull(), isNull());

    // Verify org3 was deleted
    assertThat(orgCaptor.getValue()).containsExactly("org3");
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_MultipleMissingOrgs_DeletesAll() {
    // Setup: DB has org1, org2, org3, org4
    List<List<String>> dbHierarchyList =
        Arrays.asList(Arrays.asList("org1", "proj1", "svc1"), Arrays.asList("org2", "proj1", "svc1"),
            Arrays.asList("org3", "proj1", "svc1"), Arrays.asList("org4", "proj1", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests only have org1 (missing org2, org3, org4)
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify org-level deletion for missing orgs
    ArgumentCaptor<Set<String>> orgCaptor = ArgumentCaptor.forClass(Set.class);
    verify(gitopsInstanceSyncService, times(1))
        .deleteInstancesByParentEntityList(eq(accountId), eq(agentId), orgCaptor.capture(), isNull(), isNull());

    assertThat(orgCaptor.getValue()).containsExactlyInAnyOrder("org2", "org3", "org4");
  }

  // Test Suite 4: Project-level cleanup tests
  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_NoMissingProjects_NoProjectDeletion() {
    // Setup: DB has org1 with proj1 and proj2
    List<List<String>> dbHierarchyList =
        Arrays.asList(Arrays.asList("org1", "proj1", "svc1"), Arrays.asList("org1", "proj2", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests have both proj1 and proj2 for org1
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build(),
        GitOpsInstanceRequest.builder()
            .orgIdentifier("org1")
            .projectIdentifier("proj2")
            .serviceIdentifier("svc1")
            .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify NO project-level deletion (all projects present)
    verify(gitopsInstanceSyncService, never())
        .deleteInstancesByParentEntityList(anyString(), anyString(), any(), anySet(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_OneMissingProject_DeletesProject() {
    // Setup: DB has org1 with proj1, proj2, proj3
    List<List<String>> dbHierarchyList = Arrays.asList(Arrays.asList("org1", "proj1", "svc1"),
        Arrays.asList("org1", "proj2", "svc1"), Arrays.asList("org1", "proj3", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests only have proj1 and proj2 (missing proj3)
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build(),
        GitOpsInstanceRequest.builder()
            .orgIdentifier("org1")
            .projectIdentifier("proj2")
            .serviceIdentifier("svc1")
            .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify project-level deletion for proj3
    ArgumentCaptor<Set<String>> projectCaptor = ArgumentCaptor.forClass(Set.class);
    verify(gitopsInstanceSyncService, times(1))
        .deleteInstancesByParentEntityList(
            eq(accountId), eq(agentId), eq(Collections.singleton("org1")), projectCaptor.capture(), isNull());

    assertThat(projectCaptor.getValue()).containsExactly("proj3");
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_MultipleMissingProjectsAcrossOrgs() {
    // Setup: org1 has proj1,proj2,proj3 and org2 has proj4,proj5
    List<List<String>> dbHierarchyList = Arrays.asList(Arrays.asList("org1", "proj1", "svc1"),
        Arrays.asList("org1", "proj2", "svc1"), Arrays.asList("org1", "proj3", "svc1"),
        Arrays.asList("org2", "proj4", "svc1"), Arrays.asList("org2", "proj5", "svc1"));
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming: org1 only has proj1 (missing proj2,proj3), org2 only has proj4 (missing proj5)
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org1")
                                                             .projectIdentifier("proj1")
                                                             .serviceIdentifier("svc1")
                                                             .build(),
        GitOpsInstanceRequest.builder()
            .orgIdentifier("org2")
            .projectIdentifier("proj4")
            .serviceIdentifier("svc1")
            .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify project deletions for both orgs
    ArgumentCaptor<Set<String>> orgCaptor = ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<String>> projectCaptor = ArgumentCaptor.forClass(Set.class);
    verify(gitopsInstanceSyncService, times(2))
        .deleteInstancesByParentEntityList(
            eq(accountId), eq(agentId), orgCaptor.capture(), projectCaptor.capture(), isNull());

    // Verify correct deletions (order doesn't matter)
    List<Set<String>> orgValues = orgCaptor.getAllValues();
    List<Set<String>> projectValues = projectCaptor.getAllValues();

    // Check both deletion calls happened correctly
    boolean foundOrg1 = false;
    boolean foundOrg2 = false;
    for (int i = 0; i < 2; i++) {
      if (orgValues.get(i).contains("org1")) {
        foundOrg1 = true;
        assertThat(projectValues.get(i)).containsExactlyInAnyOrder("proj2", "proj3");
      } else if (orgValues.get(i).contains("org2")) {
        foundOrg2 = true;
        assertThat(projectValues.get(i)).containsExactly("proj5");
      }
    }
    assertThat(foundOrg1 && foundOrg2).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testProcessGitOpsInstances_EmptyHierarchy_NoError() {
    // Setup: Empty hierarchy in DB
    List<List<String>> dbHierarchyList = Collections.emptyList();
    when(gitopsInstanceSyncService.getDistinctOrgProjectServiceForAgent(accountId, agentId))
        .thenReturn(dbHierarchyList);

    // Incoming requests have new org2
    List<GitOpsInstanceRequest> requests = Arrays.asList(GitOpsInstanceRequest.builder()
                                                             .orgIdentifier("org2")
                                                             .projectIdentifier("proj4")
                                                             .serviceIdentifier("svc1")
                                                             .build());

    // Mock dependencies
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.emptyList());
    when(
        gitopsInstanceSyncService.processInstanceSync(anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(true);

    // Execute - should handle gracefully without error
    gitOpsInstanceSyncResource.processGitOpsInstances(accountId, orgId, projId, agentId, requests);

    // Verify NO org deletion triggered (empty DB means no orgs to delete)
    verify(gitopsInstanceSyncService, never())
        .deleteInstancesByParentEntityList(anyString(), anyString(), anySet(), any(), any());
  }
}
