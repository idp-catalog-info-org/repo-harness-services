/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.MUDIT_GUPTA;
import static io.harness.rule.OwnerRule.NEGI;

import static software.wings.beans.TaskType.TASKS_FROM_RUNNER;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.LocalVmDriverType;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.BuildSetupUtils;
import io.harness.ci.execution.execution.BackgroundTaskUtility;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.execution.integrationstage.K8InitializeServiceUtils;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.stepdetails.GenericStepV1DelegateTaskInfo;
import io.harness.ci.stepdetails.InitStepV2DelegateTaskInfo;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponseFromRunner;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.taskrunner.TaskRunnerTaskResponse;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;
import io.harness.runner.request.helpers.RunnerRequestBuilderHelper;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.Silent.class)
public class InitializeTaskStepV2Test {
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Mock private BuildSetupUtils buildSetupUtils;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Mock private BackgroundTaskUtility backgroundTaskUtility;
  @Mock private HPersistence persistence;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Mock private UpdateOperations<CILogKeyMetadata> updateOperations;
  @Mock private Query<CILogKeyMetadata> query;
  @Mock CIExecutionRepository ciExecutionRepository;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private CIStagePlanCreationUtils ciStagePlanCreationUtils;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private VmInitializeUtils vmInitializeUtils;
  @Mock private CILicenseService ciLicenseService;

  @InjectMocks private InitializeTaskStepV2 initializeTaskStepV2;
  private CIExecutionPlanTestHelper ciExecutionPlanTestHelper = new CIExecutionPlanTestHelper();

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_PublishesStepDetails() {
    when(persistence.createUpdateOperations(CILogKeyMetadata.class)).thenReturn(updateOperations);
    when(updateOperations.setOnInsert(anyString(), anyString())).thenReturn(updateOperations);
    when(updateOperations.push(anyString(), any())).thenReturn(updateOperations);
    when(persistence.createQuery(eq(CILogKeyMetadata.class), any(Set.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);

    CIExecutionMetadata mockMetadata = CIExecutionMetadata.builder()
                                           .accountId("accountId")
                                           .stageExecutionId("stageExecutionId")
                                           .status("RUNNING")
                                           .build();
    when(ciExecutionRepository.updateExecutionStatus(anyString(), anyString(), anyString())).thenReturn(mockMetadata);

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorConfig(KubernetesClusterConfigDTO.builder().delegateSelectors(new HashSet<>()).build())
            .build();
    CIK8InitializeTaskParams mockTaskParams = CIK8InitializeTaskParams.builder().k8sConnector(connectorDetails).build();
    when(buildSetupUtils.getBuildSetupTaskParams(any(), any(), anyString(), anyBoolean(), any()))
        .thenReturn(mockTaskParams);

    OptionalSweepingOutput mockOptionalSweepingOutput = mock(OptionalSweepingOutput.class);
    when(mockOptionalSweepingOutput.isFound()).thenReturn(false);
    when(executionSweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(mockOptionalSweepingOutput);
    when(executionSweepingOutputResolver.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(mockOptionalSweepingOutput);

    String mockTaskId = "mockTaskId";
    when(ciDelegateTaskExecutor.queueTask(argThat(map -> map != null && map.containsKey("accountId")), any(), anyList(),
             anyList(), eq(false), eq(false), eq("stageExecutionId"), any(LinkedHashMap.class), anyLong(), eq(true),
             anyList()))
        .thenReturn(mockTaskId);

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("orgIdentifier", "orgId");
    setupAbstractions.put("projectIdentifier", "projectId");

    StepType stageStepType = StepType.newBuilder().setType("CI_STAGE").setStepCategory(StepCategory.STAGE).build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId("stageRuntimeId")
                           .setIdentifier("stageIdentifier")
                           .setStepType(stageStepType)
                           .setGroup("STAGE")
                           .build();

    ExecutionMetadata metadata =
        ExecutionMetadata.newBuilder().putFeatureFlagToValueMap("PIE_SIMPLIFY_LOG_BASE_KEY", false).build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .setStageExecutionId("stageExecutionId")
                            .addLevels(stageLevel)
                            .setMetadata(metadata)
                            .build();

    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .stageElementConfig(ciExecutionPlanTestHelper.getIntegrationStageConfig())
            .executionSource(ciExecutionPlanTestHelper.getCIExecutionArgs().getExecutionSource())
            .executionElementConfig(ciExecutionPlanTestHelper.getExecutionElementConfig())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
            .build();
    initializeStepInfo.setDelegateSelectors(ParameterField.createValueField(Collections.emptyList()));

    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .name("name")
                                                      .spec(initializeStepInfo)
                                                      .build();

    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());

    AsyncExecutableResponse response =
        initializeTaskStepV2.executeAsyncAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());

    assertNotNull("Response should not be null", response);
    assertEquals("Should have exactly one callback ID", 1, response.getCallbackIdsCount());
    assertEquals("Callback ID should match mock task ID", mockTaskId, response.getCallbackIds(0));

    ArgumentCaptor<InitStepV2DelegateTaskInfo> initStepCaptor =
        ArgumentCaptor.forClass(InitStepV2DelegateTaskInfo.class);
    ArgumentCaptor<GenericStepV1DelegateTaskInfo> genericStepCaptor =
        ArgumentCaptor.forClass(GenericStepV1DelegateTaskInfo.class);

    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), initStepCaptor.capture(), eq("initStepV2DelegateTaskInfo"));
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), genericStepCaptor.capture(), eq("genericStepV1DelegateTaskInfo"));

    InitStepV2DelegateTaskInfo initStepInfo = initStepCaptor.getValue();
    GenericStepV1DelegateTaskInfo genericStepInfo = genericStepCaptor.getValue();

    assertEquals(mockTaskId, initStepInfo.getTaskID());
    assertEquals("INITIALIZATION_PHASE", initStepInfo.getTaskName());
    assertEquals(mockTaskId, genericStepInfo.getTaskID());
    assertEquals("INITIALIZATION_PHASE", genericStepInfo.getTaskName());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testIsRouteToRunnerStageVarEnabled() {
    // stage variables is null
    assertFalse(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(null));

    // stage variables is empty
    assertFalse(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(Collections.emptyList()));

    // stage variables does not contain HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER
    NGVariable var1 =
        StringNGVariable.builder().name("OTHER_VARIABLE").value(ParameterField.createValueField("true")).build();
    assertFalse(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(Collections.singletonList(var1)));

    // stage variables contains HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER but value is not true
    NGVariable var2 = StringNGVariable.builder()
                          .name(RunnerRequestBuilderHelper.HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER)
                          .value(ParameterField.createValueField("false"))
                          .build();
    assertFalse(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(Collections.singletonList(var2)));

    // stage variables contains HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER but value is null
    NGVariable var3 =
        StringNGVariable.builder().name(RunnerRequestBuilderHelper.HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER).build();
    assertFalse(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(Collections.singletonList(var3)));

    // stage variables contains HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER and value is true
    NGVariable var4 = StringNGVariable.builder()
                          .name(RunnerRequestBuilderHelper.HARNESS_CI_INTERNAL_ROUTE_TO_RUNNER)
                          .value(ParameterField.createValueField("true"))
                          .build();
    assertTrue(initializeTaskStepV2.isRouteToRunnerStageVarEnabled(Collections.singletonList(var4)));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testShouldRouteStageToRunner() {
    InitializeTaskStepV2 initializeTaskStepV2Spy = spy(initializeTaskStepV2);

    boolean isFreePlan = false;
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("orgIdentifier", "orgId");
    setupAbstractions.put("projectIdentifier", "projectId");
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .setStageExecutionId("stageExecutionId")
                            .build();
    Level ciStage =
        Level.newBuilder()
            .setRuntimeId("stageNodeExecutionId")
            .setStepType(
                StepType.newBuilder().setType("IntegrationStageStepPMS").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level nonCiStage =
        Level.newBuilder()
            .setRuntimeId("stageNodeExecutionId")
            .setStepType(
                StepType.newBuilder().setType("SecurityStageStepPMS").setStepCategory(StepCategory.STAGE).build())
            .build();
    InitializeStepInfo hostedInfra =
        InitializeStepInfo.builder().infrastructure(HostedVmInfraYaml.builder().build()).build();
    InitializeStepInfo localInfra =
        InitializeStepInfo.builder().infrastructure(DockerInfraYaml.builder().build()).build();
    InitializeStepInfo k8sInfra =
        InitializeStepInfo.builder().infrastructure(K8sDirectInfraYaml.builder().build()).build();

    // Infrastructure is HOSTED_VM, CI stage, routeToRunnerStageVar is true, FF is false
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, CI stage, routeToRunnerStageVar is false, FF is true
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, non CI stage, routeToRunnerStageVar is false, FF is false
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, non CI stage, routeToRunnerStageVar is false, FF is true
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, non CI stage, routeToRunnerStageVar is true, FF is false
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is DOCKER, CI stage, routeToRunnerStageVar is true, FF is true
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_LOCAL_BUILDS_USE_RUNNER, "accountId")).thenReturn(true);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), localInfra, isFreePlan));

    // Infrastructure is DOCKER, non CI stage, routeToRunnerStageVar is false, FF is false
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_LOCAL_BUILDS_USE_RUNNER, "accountId")).thenReturn(false);
    assertFalse(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), localInfra, isFreePlan));

    // Infrastructure is DOCKER, non CI stage, routeToRunnerStageVar is true, FF is false
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_LOCAL_BUILDS_USE_RUNNER, "accountId")).thenReturn(false);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), localInfra, isFreePlan));

    // Infrastructure is KUBERNETES_DIRECT, CI stage, routeToRunnerStageVar is true, FF is true
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER, "accountId")).thenReturn(true);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), k8sInfra, isFreePlan));

    // Infrastructure is KUBERNETES_DIRECT, CI stage, routeToRunnerStageVar is true, FF is false
    doReturn(true).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(eq(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER), anyString())).thenReturn(false);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), k8sInfra, isFreePlan));

    // Infrastructure is KUBERNETES_DIRECT, CI stage, routeToRunnerStageVar is false, FF is true
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER, "accountId")).thenReturn(true);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), k8sInfra, isFreePlan));

    // Infrastructure is KUBERNETES_DIRECT, non CI stage, routeToRunnerStageVar is false, FF is false
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER, "accountId")).thenReturn(false);
    assertFalse(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), k8sInfra, isFreePlan));

    // Infrastructure is DOCKER, Security (STO) stage, routeToRunnerStageVar is false, FF is true
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_LOCAL_BUILDS_USE_RUNNER, "accountId")).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_UNIFIED_TASKS_SELECTIVELY_FOR_LOCAL_INFRA, "accountId"))
        .thenReturn(false);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), localInfra, isFreePlan));

    // Infrastructure is KUBERNETES_DIRECT, Security (STO) stage, routeToRunnerStageVar is false, FF is true
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_V0_K8S_BUILDS_USE_RUNNER, "accountId")).thenReturn(true);
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(nonCiStage).build(), k8sInfra, isFreePlan));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFallbackToDliteForRunnerRoutableStage() {
    InitializeTaskStepV2 initializeTaskStepV2Spy = spy(initializeTaskStepV2);

    boolean isFreePlan = false;
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("orgIdentifier", "orgId");
    setupAbstractions.put("projectIdentifier", "projectId");
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .setStageExecutionId("stageExecutionId")
                            .build();
    Level ciStage =
        Level.newBuilder()
            .setRuntimeId("stageNodeExecutionId")
            .setStepType(
                StepType.newBuilder().setType("IntegrationStageStepPMS").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level retriedInitStep =
        Level.newBuilder()
            .setRuntimeId("stageNodeExecutionId")
            .setStepType(
                StepType.newBuilder().setType("IntegrationStageStepPMS").setStepCategory(StepCategory.STAGE).build())
            .setRetryIndex(1)
            .build();
    InitializeStepInfo hostedInfra =
        InitializeStepInfo.builder().infrastructure(HostedVmInfraYaml.builder().build()).build();

    // Infrastructure is HOSTED_VM, stage routable to runner, retry is 0
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, stage routable to runner, retry is 1
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).addLevels(retriedInitStep).build(), hostedInfra, isFreePlan));

    // Infrastructure is HOSTED_VM, stage NOT routable to runner
    doReturn(false).when(initializeTaskStepV2Spy).isRouteToRunnerStageVarEnabled(any());
    assertTrue(initializeTaskStepV2Spy.shouldRouteStageToRunner(
        ambiance.toBuilder().addLevels(ciStage).build(), hostedInfra, isFreePlan));
  }

  // Non-local infra  →  noop
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateRouteToRunnerOtherInfraShouldNoop() {
    when(ciFeatureFlagService.isEnabled(eq(FeatureName.CI_USE_UNIFIED_TASKS_SELECTIVELY_FOR_LOCAL_INFRA), any()))
        .thenReturn(true); // even if FF ON
    SpecParameters nonLocal = mockNonLocalInfra();

    initializeTaskStepV2.updateRouteToRunner(Ambiance.newBuilder().build(), nonLocal, mock(ResponseData.class));

    verifyNoInteractions(executionSweepingOutputService);
  }

  // Local infra but FF disabled  →  noop
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateRouteToRunnerFFDisabledShouldNoop() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(false);
    SpecParameters local = mockLocalInfra();

    initializeTaskStepV2.updateRouteToRunner(Ambiance.newBuilder().build(), local, mock(ResponseData.class));

    verifyNoInteractions(executionSweepingOutputService);
  }

  // Local infra & FF enabled, response from runner  →  noop
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateRouteToRunnerResponseFromRunnerShouldNoop() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(true);
    SpecParameters local = mockLocalInfra();

    SerializedResponseData runnerResp = mock(SerializedResponseData.class);
    when(runnerResp.getTaskType()).thenReturn(TASKS_FROM_RUNNER);

    initializeTaskStepV2.updateRouteToRunner(Ambiance.newBuilder().build(), local, runnerResp);

    verifyNoInteractions(executionSweepingOutputService);
  }

  // Local infra & FF enabled, delegate resp, routeToRunner==true → consumeUpsert invoked with routeToRunner=false */
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateRouteToRunnerDelegateRespRouteReset() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(true);
    SpecParameters local = mockLocalInfra();

    VmStageInfraDetails details = VmStageInfraDetails.builder().routeToRunner(true).build();
    OptionalSweepingOutput osv = OptionalSweepingOutput.builder().found(true).output(details).build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(executionSweepingOutputResolver.resolveOptional(
             eq(ambiance), eq(RefObjectUtils.getOutcomeRefObject(STAGE_INFRA_DETAILS))))
        .thenReturn(osv);

    initializeTaskStepV2.updateRouteToRunner(ambiance, local, mock(ResponseData.class));

    ArgumentCaptor<VmStageInfraDetails> captor = ArgumentCaptor.forClass(VmStageInfraDetails.class);
    verify(executionSweepingOutputResolver)
        .consumeUpsert(eq(ambiance), eq(STAGE_INFRA_DETAILS), captor.capture(), eq(StepOutcomeGroup.STAGE.name()));
    assertThat(captor.getValue().shouldRouteStageToRunner()).isFalse();
  }

  // Local infra & FF enabled, delegate resp, routeToRunner==false consumeUpsert NOT invoked
  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateRouteToRunnerDelegateRespAlreadyReset() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(true);
    SpecParameters local = mockLocalInfra();

    VmStageInfraDetails details = VmStageInfraDetails.builder().routeToRunner(false).build();
    OptionalSweepingOutput osv = OptionalSweepingOutput.builder().found(true).output(details).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any())).thenReturn(osv);

    initializeTaskStepV2.updateRouteToRunner(Ambiance.newBuilder().build(), local, mock(ResponseData.class));

    verify(executionSweepingOutputResolver, never()).consumeUpsert(any(), any(), any(), any());
  }

  private SpecParameters mockLocalInfra() {
    return mockInitInfo(Infrastructure.Type.DOCKER);
  }

  private SpecParameters mockNonLocalInfra() {
    return mockInitInfo(Infrastructure.Type.KUBERNETES_DIRECT);
  }

  private SpecParameters mockInitInfo(Infrastructure.Type type) {
    InitializeStepInfo info = mock(InitializeStepInfo.class, RETURNS_DEEP_STUBS);
    when(info.getInfrastructure().getType()).thenReturn(type);
    return info;
  }

  // --- resolveLocalVmDriverType tests ---

  private Ambiance buildTestAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "testAccountId")
        .putSetupAbstractions("orgIdentifier", "testOrgId")
        .putSetupAbstractions("projectIdentifier", "testProjectId")
        .build();
  }

  private InitializeStepInfo buildDockerInitStepInfo(java.util.List<NGVariable> variables) {
    DockerInfraYaml dockerInfra = mock(DockerInfraYaml.class, RETURNS_DEEP_STUBS);
    when(dockerInfra.getType()).thenReturn(Infrastructure.Type.DOCKER);
    return InitializeStepInfo.builder().infrastructure(dockerInfra).variables(variables).build();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_FFDisabled_ReturnsNone() {
    Ambiance ambiance = buildTestAmbiance();
    InitializeStepInfo initializeStepInfo = buildDockerInitStepInfo(Collections.emptyList());

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(false);

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.NONE, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_NonDockerInfra_ReturnsNone() {
    Ambiance ambiance = buildTestAmbiance();
    K8sDirectInfraYaml k8sInfra = mock(K8sDirectInfraYaml.class, RETURNS_DEEP_STUBS);
    when(k8sInfra.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().infrastructure(k8sInfra).variables(Collections.emptyList()).build();

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(true);

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.NONE, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_StageVarTrue_ReturnsTartVm() {
    Ambiance ambiance = buildTestAmbiance();
    NGVariable localVmsVar = StringNGVariable.builder()
                                 .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                 .value(ParameterField.createValueField("true"))
                                 .build();
    InitializeStepInfo initializeStepInfo = buildDockerInitStepInfo(Collections.singletonList(localVmsVar));

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(true);

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.TART_VM, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_StageVarFalse_ReturnsNone() {
    Ambiance ambiance = buildTestAmbiance();
    NGVariable localVmsVar = StringNGVariable.builder()
                                 .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                 .value(ParameterField.createValueField("false"))
                                 .build();
    InitializeStepInfo initializeStepInfo = buildDockerInitStepInfo(Collections.singletonList(localVmsVar));

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(true);

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.NONE, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_NoStageVar_SettingTrue() throws Exception {
    Ambiance ambiance = buildTestAmbiance();
    InitializeStepInfo initializeStepInfo = buildDockerInitStepInfo(Collections.emptyList());

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(true);

    Call mockCall = mock(Call.class);
    when(settingsClient.getSetting(eq(SettingIdentifiers.CI_ENABLE_LOCAL_VMS), eq("testAccountId"), any(), any()))
        .thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("true").build())));

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.TART_VM, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveLocalVmDriverType_NoStageVar_SettingFalse() throws Exception {
    Ambiance ambiance = buildTestAmbiance();
    InitializeStepInfo initializeStepInfo = buildDockerInitStepInfo(Collections.emptyList());

    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_LOCAL_VMS, "testAccountId")).thenReturn(true);

    Call mockCall = mock(Call.class);
    when(settingsClient.getSetting(eq(SettingIdentifiers.CI_ENABLE_LOCAL_VMS), eq("testAccountId"), any(), any()))
        .thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder().value("false").build())));

    LocalVmDriverType result = initializeTaskStepV2.resolveLocalVmDriverType(ambiance, initializeStepInfo);

    assertEquals(LocalVmDriverType.NONE, result);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarEnabled() {
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarEnabled(null));
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarEnabled(Collections.emptyList()));

    NGVariable trueVar = StringNGVariable.builder()
                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                             .value(ParameterField.createValueField("true"))
                             .build();
    assertTrue(initializeTaskStepV2.isLocalVmsStageVarEnabled(Collections.singletonList(trueVar)));

    NGVariable falseVar = StringNGVariable.builder()
                              .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                              .value(ParameterField.createValueField("false"))
                              .build();
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarEnabled(Collections.singletonList(falseVar)));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarDisabled() {
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarDisabled(null));
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarDisabled(Collections.emptyList()));

    NGVariable falseVar = StringNGVariable.builder()
                              .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                              .value(ParameterField.createValueField("false"))
                              .build();
    assertTrue(initializeTaskStepV2.isLocalVmsStageVarDisabled(Collections.singletonList(falseVar)));

    NGVariable trueVar = StringNGVariable.builder()
                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                             .value(ParameterField.createValueField("true"))
                             .build();
    assertFalse(initializeTaskStepV2.isLocalVmsStageVarDisabled(Collections.singletonList(trueVar)));
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void toK8sTaskExecutionResponseFromRunnerPropagatesFieldsAndStampsK8Type() throws Exception {
    final var input =
        new TaskRunnerTaskResponse(CommandExecutionStatus.FAILURE, "k8s api refused: 403", new HashMap<>());

    final var method = io.harness.ci.states.V1.InitializeTaskStepV2.class.getDeclaredMethod(
        "toK8sTaskExecutionResponseFromRunner", TaskRunnerTaskResponse.class);
    method.setAccessible(true);
    final var result = (K8sTaskExecutionResponseFromRunner) method.invoke(null, input);

    assertThat(result.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(result.getErrorMessage()).isEqualTo("k8s api refused: 403");
    assertThat(result.getType()).isEqualTo(CITaskExecutionResponse.Type.K8);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void toK8sTaskExecutionResponseFromRunnerHandlesNullDelegateMetaInfo() throws Exception {
    final var input = new TaskRunnerTaskResponse(CommandExecutionStatus.SUCCESS, "", new HashMap<>());

    final var method = io.harness.ci.states.V1.InitializeTaskStepV2.class.getDeclaredMethod(
        "toK8sTaskExecutionResponseFromRunner", TaskRunnerTaskResponse.class);
    method.setAccessible(true);
    final var result = (K8sTaskExecutionResponseFromRunner) method.invoke(null, input);

    assertThat(result.getDelegateMetaInfo()).isNull();
    assertThat(result.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = MUDIT_GUPTA)
  @Category(UnitTests.class)
  public void testGetInitEnvVars_K8_MergesPodCommonEnvVars() {
    Map<String, String> containerEnvVars = new HashMap<>();
    containerEnvVars.put("STEP_SPECIFIC", "value");

    Map<String, String> podCommonEnvVars = new HashMap<>();
    podCommonEnvVars.put("DRONE_COMMIT_SHA", "abc123");
    podCommonEnvVars.put("DRONE_COMMIT_BRANCH", "main");
    podCommonEnvVars.put("DRONE_PULL_REQUEST", "42");

    CIK8InitializeTaskParams taskParams = k8TaskParams(containerEnvVars, podCommonEnvVars);
    Map<String, String> result = initializeTaskStepV2.getInitEnvVars(taskParams);

    assertThat(result).containsEntry("STEP_SPECIFIC", "value");
    assertThat(result).containsEntry("DRONE_COMMIT_SHA", "abc123");
    assertThat(result).containsEntry("DRONE_COMMIT_BRANCH", "main");
    assertThat(result).containsEntry("DRONE_PULL_REQUEST", "42");
  }

  @Test
  @Owner(developers = MUDIT_GUPTA)
  @Category(UnitTests.class)
  public void testGetInitEnvVars_K8_PodCommonEnvVarWinsOnKeyCollision() {
    Map<String, String> containerEnvVars = new HashMap<>();
    containerEnvVars.put("SHARED_KEY", "container-value");

    Map<String, String> podCommonEnvVars = new HashMap<>();
    podCommonEnvVars.put("SHARED_KEY", "pod-value");

    CIK8InitializeTaskParams taskParams = k8TaskParams(containerEnvVars, podCommonEnvVars);
    Map<String, String> result = initializeTaskStepV2.getInitEnvVars(taskParams);

    // pod-level metadata is merged after per-container vars, so it wins on collision
    assertThat(result).containsEntry("SHARED_KEY", "pod-value");
  }

  @Test
  @Owner(developers = MUDIT_GUPTA)
  @Category(UnitTests.class)
  public void testGetInitEnvVars_K8_HandlesNullCommonEnvVars() {
    Map<String, String> containerEnvVars = new HashMap<>();
    containerEnvVars.put("STEP_SPECIFIC", "value");

    CIK8InitializeTaskParams taskParams = k8TaskParams(containerEnvVars, null);
    Map<String, String> result = initializeTaskStepV2.getInitEnvVars(taskParams);

    assertThat(result).containsEntry("STEP_SPECIFIC", "value");
  }

  @Test
  @Owner(developers = MUDIT_GUPTA)
  @Category(UnitTests.class)
  public void testGetInitEnvVars_K8_MergesServicePodCommonEnvVars() {
    Map<String, String> containerEnvVars = new HashMap<>();
    containerEnvVars.put("STEP_SPECIFIC", "value");

    Map<String, String> serviceContainerEnvVars = new HashMap<>();
    serviceContainerEnvVars.put("SERVICE_CONTAINER", "svc-value");

    Map<String, String> serviceCommonEnvVars = new HashMap<>();
    serviceCommonEnvVars.put("DRONE_COMMIT_SHA", "abc123");

    CIK8ContainerParams serviceContainer =
        CIK8ContainerParams.builder().name("svc-1").envVars(serviceContainerEnvVars).build();
    CIK8PodParams<CIK8ContainerParams> servicePod =
        CIK8PodParams.<CIK8ContainerParams>builder()
            .name("service-pod")
            .containerParamsList(Collections.singletonList(serviceContainer))
            .commonEnvVars(serviceCommonEnvVars)
            .build();
    CIK8ServicePodParams servicePodParams = CIK8ServicePodParams.builder()
                                                .serviceName("svc")
                                                .selectorMap(Collections.singletonMap("app", "svc"))
                                                .ports(Collections.singletonList(8080))
                                                .cik8PodParams(servicePod)
                                                .build();

    CIK8ContainerParams containerParams =
        CIK8ContainerParams.builder().name("step-1").envVars(containerEnvVars).build();
    CIK8PodParams<CIK8ContainerParams> podParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                       .name("pod-name")
                                                       .containerParamsList(Collections.singletonList(containerParams))
                                                       .build();
    CIK8InitializeTaskParams taskParams = CIK8InitializeTaskParams.builder()
                                              .cik8PodParams(podParams)
                                              .servicePodParams(Collections.singletonList(servicePodParams))
                                              .build();

    Map<String, String> result = initializeTaskStepV2.getInitEnvVars(taskParams);

    assertThat(result).containsEntry("STEP_SPECIFIC", "value");
    assertThat(result).containsEntry("SERVICE_CONTAINER", "svc-value");
    assertThat(result).containsEntry("DRONE_COMMIT_SHA", "abc123");
  }

  private static CIK8InitializeTaskParams k8TaskParams(
      Map<String, String> containerEnvVars, Map<String, String> podCommonEnvVars) {
    CIK8ContainerParams containerParams =
        CIK8ContainerParams.builder().name("step-1").envVars(containerEnvVars).build();
    CIK8PodParams<CIK8ContainerParams> podParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                       .name("pod-name")
                                                       .containerParamsList(Collections.singletonList(containerParams))
                                                       .commonEnvVars(podCommonEnvVars)
                                                       .build();
    return CIK8InitializeTaskParams.builder().cik8PodParams(podParams).build();
  }
}
