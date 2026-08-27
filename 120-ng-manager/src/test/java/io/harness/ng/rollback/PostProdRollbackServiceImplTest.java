/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.beans.FeatureName.CDS_GITOPS_POST_PROD_ROLLBACK;
import static io.harness.beans.FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER;
import static io.harness.beans.FeatureName.PIPE_ROLLBACK_RETRY_ON_FAILURE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTPAREEK;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.dtos.rollback.BatchRollbackRequestDTO;
import io.harness.dtos.rollback.BatchRollbackResponseDTO;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.dtos.rollback.RollbackRequestDTO;
import io.harness.dtos.rollback.RollbackResponseDTO;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.RollbackStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution;
import io.harness.models.InstanceDetailsDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.execution.Status;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackServiceImplTest extends CategoryTest {
  @Mock private InstanceRepository instanceRepository;
  @Mock private PipelineServiceClient pipelineServiceClient;
  @Mock private PostProdRollbackHelperUtils postProdRollbackHelperUtils;
  @Mock private AccessControlClient accessControlClient;
  @Mock private CDOverviewDashboardService cdOverviewDashboardService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks @Spy private PostProdRollbackServiceImpl postProdRollbackService;
  @Mock private PostProdRollbackServiceImpl mockedPostProdRollbackService;
  String instanceKey = "instanceUuid";
  String infraMappingId = "instanceUuid";
  String accountId = "accountId";
  String planExecutionId = "planExecutionId";
  String orgId = "orgId";
  String projectId = "projectId";
  String parentUniqueId = "parentUniqueId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private void mockScopeInfoForParentUniqueId() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(parentUniqueId)
                              .build();
    doReturn(Map.of(parentUniqueId, Optional.of(scopeInfo)))
        .when(scopeInfoService)
        .getScopeInfo(eq(accountId), anySet());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed() {
    doReturn(Instance.builder()
                 .lastPipelineExecutionId(planExecutionId)
                 .stageStatus(Status.FAILED)
                 .rollbackStatus(RollbackStatus.STARTED)
                 .infrastructureMappingId(infraMappingId)
                 .instanceType(InstanceType.K8S_INSTANCE)
                 .envIdentifier("env_ref")
                 .id(instanceKey)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    doReturn(Instance.builder()
                 .stageStatus(Status.SUCCEEDED)
                 .instanceType(InstanceType.ASG_INSTANCE)
                 .rollbackStatus(RollbackStatus.STARTED)
                 .instanceKey(instanceKey)
                 .envIdentifier("env_ref")
                 .infrastructureMappingId(infraMappingId)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    response = postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    doReturn(Instance.builder()
                 .stageStatus(Status.SUCCEEDED)
                 .instanceType(InstanceType.K8S_INSTANCE)
                 .instanceKey(instanceKey)
                 .rollbackStatus(RollbackStatus.STARTED)
                 .envIdentifier("env_ref")
                 .infrastructureMappingId(infraMappingId)
                 .build())
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    response = postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.NOT_STARTED)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(postProdRollbackSwimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(eq(instance));
    response = postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isTrue();
    assertThat(response.getSwimLaneInfo()).isEqualTo(postProdRollbackSwimLaneInfo);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testTriggerRollback() {
    String newPlanExecutionId = "newPlanExecutionId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    Map<String, Map> responseMap = new HashMap<>();
    Map<String, String> planExecutionMap = new HashMap<>();
    planExecutionMap.put("uuid", newPlanExecutionId);
    responseMap.put("planExecution", planExecutionMap);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(responseMap);

    doThrow(new InvalidRequestException("invalid request"))
        .when(pipelineServiceClient)
        .triggerPostExecutionRollback(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    assertThatThrownBy(
        () -> postProdRollbackService.triggerRollback(accountId, orgId, projectId, instanceKey, infraMappingId))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(null)
        .when(pipelineServiceClient)
        .triggerPostExecutionRollback(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .stageNodeExecutionId(stageNodeExecutionId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    doReturn(null).when(postProdRollbackHelperUtils).getSwimlaneInfo(eq(instance));
    mockScopeInfoForParentUniqueId();
    PostProdRollbackResponseDTO response =
        postProdRollbackService.triggerRollback(accountId, orgId, projectId, instanceKey, infraMappingId);

    verify(pipelineServiceClient, times(1))
        .triggerPostExecutionRollback(planExecutionId, accountId, orgId, projectId, "", stageNodeExecutionId);
    assertThat(response.isRollbackTriggered()).isTrue();
    assertThat(response.getPlanExecutionId()).isEqualTo(newPlanExecutionId);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowedWithAccessControl() {
    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.NOT_STARTED)
                            .infrastructureMappingId(infraMappingId)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .envIdentifier("env")
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(postProdRollbackSwimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(eq(instance));
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();

    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    response = postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isTrue();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testTriggerRollbackV2_Success() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "proj123";
    RollbackRequestDTO rollbackRequestDTO = RollbackRequestDTO.builder()
                                                .serviceIdentifier("service123")
                                                .envIdentifier("env123")
                                                .infraIdentifier("infra123")
                                                .artifact("a123")
                                                .environmentType(EnvironmentType.Production)
                                                .build();

    InstanceDetailsDTO instanceDetails = InstanceDetailsDTO.builder()
                                             .instanceKey("instance123")
                                             .infrastructureMappingId("infraMap123")
                                             .artifactName("a123")
                                             .build();

    InstanceDetailGroupedByPipelineExecution groupedDetails =
        InstanceDetailGroupedByPipelineExecution.builder().instances(List.of(instanceDetails)).build();

    InstanceDetailGroupedByPipelineExecutionList instanceDetailsResponse =
        InstanceDetailGroupedByPipelineExecutionList.builder()
            .instanceDetailGroupedByPipelineExecutionList(List.of(groupedDetails))
            .build();

    PostProdRollbackResponseDTO postProdResponse = PostProdRollbackResponseDTO.builder()
                                                       .instanceKey("instance123")
                                                       .infraMappingId("infraMap123")
                                                       .planExecutionId("execution123")
                                                       .message("Rollback triggered successfully")
                                                       .isRollbackTriggered(true)
                                                       .build();

    doReturn(instanceDetailsResponse)
        .when(cdOverviewDashboardService)
        .getInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier, projectIdentifier, "service123",
            "env123", EnvironmentType.Production, "infra123", null, "a123", null, true);

    doReturn(postProdResponse)
        .when(postProdRollbackService)
        .triggerRollback(accountIdentifier, orgIdentifier, projectIdentifier, "instance123", "infraMap123");

    RollbackResponseDTO result = postProdRollbackService.triggerRollbackV2(
        accountIdentifier, orgIdentifier, projectIdentifier, rollbackRequestDTO);

    assertThat(result).isNotNull();
    assertThat("instance123").isEqualTo(result.getInstanceKey());
    assertThat("infraMap123").isEqualTo(result.getInfraMappingId());
    assertThat("execution123").isEqualTo(result.getPlanExecutionId());
    assertThat("Rollback triggered successfully").isEqualTo(result.getMessage());
    assertThat(true).isEqualTo(result.isRollbackTriggered());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testTriggerRollbackV2_NoInstancesFound() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "proj123";
    RollbackRequestDTO rollbackRequestDTO = RollbackRequestDTO.builder()
                                                .serviceIdentifier("service123")
                                                .envIdentifier("env123")
                                                .infraIdentifier("infra123")
                                                .environmentType(EnvironmentType.Production)
                                                .build();

    InstanceDetailGroupedByPipelineExecution groupedDetails =
        InstanceDetailGroupedByPipelineExecution.builder().instances(List.of()).build();

    InstanceDetailGroupedByPipelineExecutionList instanceDetailsResponse =
        InstanceDetailGroupedByPipelineExecutionList.builder()
            .instanceDetailGroupedByPipelineExecutionList(List.of(groupedDetails))
            .build();

    PostProdRollbackResponseDTO postProdResponse = PostProdRollbackResponseDTO.builder()
                                                       .instanceKey("instance123")
                                                       .infraMappingId("infraMap123")
                                                       .planExecutionId("execution123")
                                                       .message("Rollback triggered successfully")
                                                       .isRollbackTriggered(true)
                                                       .build();

    doReturn(instanceDetailsResponse)
        .when(cdOverviewDashboardService)
        .getInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier, projectIdentifier, "service123",
            "env123", EnvironmentType.Production, "infra123", null, null, null, true);

    doReturn(postProdResponse)
        .when(mockedPostProdRollbackService)
        .triggerRollback(accountIdentifier, orgIdentifier, projectIdentifier, "instance123", "infraMap123");

    assertThatThrownBy(()
                           -> postProdRollbackService.triggerRollbackV2(
                               accountIdentifier, orgIdentifier, projectIdentifier, rollbackRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No active instances found for the given combination of service/infra.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testTriggerRollbackV3_AllTargetsSucceed() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "proj123";

    RollbackRequestDTO target1 = RollbackRequestDTO.builder()
                                     .serviceIdentifier("service1")
                                     .envIdentifier("env1")
                                     .infraIdentifier("infra1")
                                     .environmentType(EnvironmentType.Production)
                                     .build();
    RollbackRequestDTO target2 = RollbackRequestDTO.builder()
                                     .serviceIdentifier("service2")
                                     .envIdentifier("env2")
                                     .infraIdentifier("infra2")
                                     .environmentType(EnvironmentType.Production)
                                     .build();

    BatchRollbackRequestDTO batchRequest = BatchRollbackRequestDTO.builder().targets(List.of(target1, target2)).build();

    RollbackResponseDTO response1 = RollbackResponseDTO.builder()
                                        .isRollbackTriggered(true)
                                        .serviceIdentifier("service1")
                                        .envIdentifier("env1")
                                        .infraIdentifier("infra1")
                                        .environmentType(EnvironmentType.Production.name())
                                        .planExecutionId("exec1")
                                        .build();
    RollbackResponseDTO response2 = RollbackResponseDTO.builder()
                                        .isRollbackTriggered(true)
                                        .serviceIdentifier("service2")
                                        .envIdentifier("env2")
                                        .infraIdentifier("infra2")
                                        .environmentType(EnvironmentType.Production.name())
                                        .planExecutionId("exec2")
                                        .build();

    doReturn(response1)
        .when(postProdRollbackService)
        .triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target1);
    doReturn(response2)
        .when(postProdRollbackService)
        .triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target2);

    BatchRollbackResponseDTO result =
        postProdRollbackService.triggerRollbackV3(accountIdentifier, orgIdentifier, projectIdentifier, batchRequest);

    assertThat(result.getTotalRollbacksTriggered()).isEqualTo(2);
    assertThat(result.getTotalRollbacksFailed()).isEqualTo(0);
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).isRollbackTriggered()).isTrue();
    assertThat(result.getResults().get(1).isRollbackTriggered()).isTrue();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testTriggerRollbackV3_OneTargetFails_OthersContinue() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "proj123";

    RollbackRequestDTO target1 = RollbackRequestDTO.builder()
                                     .serviceIdentifier("service1")
                                     .envIdentifier("env1")
                                     .infraIdentifier("infra1")
                                     .environmentType(EnvironmentType.Production)
                                     .build();
    RollbackRequestDTO target2 = RollbackRequestDTO.builder()
                                     .serviceIdentifier("service2")
                                     .envIdentifier("env2")
                                     .infraIdentifier("infra2")
                                     .environmentType(EnvironmentType.Production)
                                     .build();

    BatchRollbackRequestDTO batchRequest = BatchRollbackRequestDTO.builder().targets(List.of(target1, target2)).build();

    RollbackResponseDTO response1 = RollbackResponseDTO.builder()
                                        .isRollbackTriggered(true)
                                        .serviceIdentifier("service1")
                                        .envIdentifier("env1")
                                        .infraIdentifier("infra1")
                                        .environmentType(EnvironmentType.Production.name())
                                        .planExecutionId("exec1")
                                        .build();

    doReturn(response1)
        .when(postProdRollbackService)
        .triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target1);
    doThrow(new InvalidRequestException("No active instances found for the given combination of service/infra."))
        .when(postProdRollbackService)
        .triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target2);

    BatchRollbackResponseDTO result =
        postProdRollbackService.triggerRollbackV3(accountIdentifier, orgIdentifier, projectIdentifier, batchRequest);

    assertThat(result.getTotalRollbacksTriggered()).isEqualTo(1);
    assertThat(result.getTotalRollbacksFailed()).isEqualTo(1);
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults().get(0).isRollbackTriggered()).isTrue();
    assertThat(result.getResults().get(1).isRollbackTriggered()).isFalse();
    assertThat(result.getResults().get(1).getMessage())
        .isEqualTo("No active instances found for the given combination of service/infra.");
    assertThat(result.getResults().get(1).getServiceIdentifier()).isEqualTo("service2");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testTriggerRollbackV3_NotTriggeredCountsAsFailed() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "proj123";

    RollbackRequestDTO target = RollbackRequestDTO.builder()
                                    .serviceIdentifier("service1")
                                    .envIdentifier("env1")
                                    .infraIdentifier("infra1")
                                    .environmentType(EnvironmentType.Production)
                                    .build();

    BatchRollbackRequestDTO batchRequest = BatchRollbackRequestDTO.builder().targets(List.of(target)).build();

    RollbackResponseDTO notTriggeredResponse = RollbackResponseDTO.builder()
                                                   .isRollbackTriggered(false)
                                                   .message("User does not have the required permission")
                                                   .serviceIdentifier("service1")
                                                   .envIdentifier("env1")
                                                   .infraIdentifier("infra1")
                                                   .environmentType(EnvironmentType.Production.name())
                                                   .build();

    doReturn(notTriggeredResponse)
        .when(postProdRollbackService)
        .triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, target);

    BatchRollbackResponseDTO result =
        postProdRollbackService.triggerRollbackV3(accountIdentifier, orgIdentifier, projectIdentifier, batchRequest);

    assertThat(result.getTotalRollbacksTriggered()).isEqualTo(0);
    assertThat(result.getTotalRollbacksFailed()).isEqualTo(1);
    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).isRollbackTriggered()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed_FFEnabled_StartedStatusBlocksRetry() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.STARTED)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();
    assertThat(response.getMessage())
        .isEqualTo("Can not start the Rollback. Rollback has already been triggered and the previous rollback status "
            + "is: STARTED");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed_FFEnabled_FailureStatusAllowsRetry() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.FAILURE)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    PostProdRollbackSwimLaneInfo swimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(swimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(eq(instance));

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed_FFEnabled_SuccessStatusStillBlocked() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.SUCCESS)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();
    assertThat(response.getMessage())
        .isEqualTo("Can not start the Rollback. Rollback has already been triggered and the previous rollback status "
            + "is: SUCCESS");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed_FFDisabled_StartedStatusBlocksRollback() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.STARTED)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCheckIfRollbackAllowed_FFDisabled_FailureStatusBlocksRollback() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.FAILURE)
                            .envIdentifier("env_ref")
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);
    assertThat(response.isRollbackAllowed()).isFalse();
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_FallbackEnrichesWhenStageMetadataMissing() {
    String stageExecutionId = "stageExecutionId123";
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageStatus(null)
                            .stageNodeExecutionId(null)
                            .rollbackStatus(RollbackStatus.UNAVAILABLE)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .serviceIdentifier("svc_ref")
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(null)
                            .build();
    doReturn(instance).when(instanceRepository).getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, null);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(CDS_GITOPS_POST_PROD_ROLLBACK));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId("svc_ref")
                                        .envId("env_ref")
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    doReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, "svc_ref", "env_ref"), stageMeta))
        .when(cdOverviewDashboardService)
        .getGitOpsStageMetadataForRollback(List.of(planExecutionId));

    PostProdRollbackSwimLaneInfo swimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(swimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(any());

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, null);

    assertThat(response.isRollbackAllowed()).isTrue();
    assertThat(instance.getStageNodeExecutionId()).isEqualTo(stageExecutionId);
    assertThat(instance.getStageStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(instance.getRollbackStatus()).isEqualTo(RollbackStatus.NOT_STARTED);
    verify(instanceRepository, times(1)).save(instance);
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_SkipsWhenStageMetadataAlreadyPresent() {
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageNodeExecutionId("alreadySet")
                            .stageStatus(Status.SUCCEEDED)
                            .rollbackStatus(RollbackStatus.NOT_STARTED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(null)
                            .build();
    doReturn(instance).when(instanceRepository).getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, null);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    PostProdRollbackSwimLaneInfo swimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(swimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(any());

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, null);

    assertThat(response.isRollbackAllowed()).isTrue();
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_SkipsForNonGitOpsInstances() {
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageNodeExecutionId(null)
                            .stageStatus(null)
                            .rollbackStatus(RollbackStatus.UNAVAILABLE)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind("KubernetesDirect")
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(CDS_GITOPS_POST_PROD_ROLLBACK));

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, infraMappingId);

    assertThat(response.isRollbackAllowed()).isFalse();
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_SkipsWhenFFDisabled() {
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageNodeExecutionId(null)
                            .stageStatus(null)
                            .rollbackStatus(RollbackStatus.UNAVAILABLE)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(null)
                            .build();
    doReturn(instance).when(instanceRepository).getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, null);
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(CDS_GITOPS_POST_PROD_ROLLBACK));

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, null);

    assertThat(response.isRollbackAllowed()).isFalse();
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_GracefulOnTimescaleFailure() {
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageNodeExecutionId(null)
                            .stageStatus(null)
                            .rollbackStatus(RollbackStatus.UNAVAILABLE)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(null)
                            .build();
    doReturn(instance).when(instanceRepository).getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, null);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(CDS_GITOPS_POST_PROD_ROLLBACK));
    doThrow(new RuntimeException("Timescale unavailable"))
        .when(cdOverviewDashboardService)
        .getGitOpsStageMetadataForRollback(any());

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, null);

    // Enrichment failed, so stageStatus is still null -> rollback not allowed
    assertThat(response.isRollbackAllowed()).isFalse();
    // But no exception propagated
    assertThat(instance.getStageNodeExecutionId()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testTriggerRollback_PipelineServiceThrows_StatusSetToFailure() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(PIPE_ROLLBACK_RETRY_ON_FAILURE));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .stageNodeExecutionId("stageNodeExecutionId")
                            .lastPipelineExecutionId(planExecutionId)
                            .stageStatus(Status.SUCCEEDED)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .rollbackStatus(RollbackStatus.NOT_STARTED)
                            .infrastructureMappingId(infraMappingId)
                            .build();
    doReturn(instance)
        .when(instanceRepository)
        .getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    doReturn(null).when(postProdRollbackHelperUtils).getSwimlaneInfo(eq(instance));
    mockScopeInfoForParentUniqueId();

    try (MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class)) {
      mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
          .thenThrow(new InvalidRequestException("Policy evaluation failed"));

      // When the pipeline-service call fails, the exception is re-thrown so the caller knows.
      assertThatThrownBy(
          () -> postProdRollbackService.triggerRollback(accountId, orgId, projectId, instanceKey, infraMappingId))
          .isInstanceOf(InvalidRequestException.class);

      // Status is set to FAILURE so the user can retry immediately without needing support intervention.
      assertThat(instance.getRollbackStatus()).isEqualTo(RollbackStatus.FAILURE);
      verify(instanceRepository, times(1)).save(any());
    }
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testEnrichGitOpsInstance_UsesJooqVariantWhenFFEnabled() {
    String stageExecutionId = "stageExecJooq123";
    Instance instance = Instance.builder()
                            .accountIdentifier(accountId)
                            .lastPipelineExecutionId(planExecutionId)
                            .stageStatus(null)
                            .stageNodeExecutionId(null)
                            .rollbackStatus(RollbackStatus.UNAVAILABLE)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .serviceIdentifier("svc_ref")
                            .envIdentifier("env_ref")
                            .instanceKey(instanceKey)
                            .infrastructureMappingId(null)
                            .build();
    doReturn(instance).when(instanceRepository).getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, null);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), eq(CDS_GITOPS_POST_PROD_ROLLBACK));
    doReturn(true)
        .when(cdFeatureFlagHelper)
        .isEnabled(eq(accountId), eq(CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER));
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    GitOpsStageMetadata stageMeta = GitOpsStageMetadata.builder()
                                        .planExecutionId(planExecutionId)
                                        .serviceId("svc_ref")
                                        .envId("env_ref")
                                        .stageExecutionId(stageExecutionId)
                                        .stageStatus("SUCCESS")
                                        .build();
    doReturn(Map.of(GitOpsStageMetadata.buildKey(planExecutionId, "svc_ref", "env_ref"), stageMeta))
        .when(cdOverviewDashboardService)
        .getGitOpsStageMetadataForRollbackViaJooq(List.of(planExecutionId));

    PostProdRollbackSwimLaneInfo swimLaneInfo = K8sPostProdRollbackInfo.builder().build();
    doReturn(swimLaneInfo).when(postProdRollbackHelperUtils).getSwimlaneInfo(any());

    PostProdRollbackCheckDTO response =
        postProdRollbackService.checkIfRollbackAllowed(accountId, orgId, projectId, instanceKey, null);

    assertThat(response.isRollbackAllowed()).isTrue();
    assertThat(instance.getStageNodeExecutionId()).isEqualTo(stageExecutionId);
    verify(cdOverviewDashboardService, times(1)).getGitOpsStageMetadataForRollbackViaJooq(any());
    verify(cdOverviewDashboardService, never()).getGitOpsStageMetadataForRollback(any());
  }
}
