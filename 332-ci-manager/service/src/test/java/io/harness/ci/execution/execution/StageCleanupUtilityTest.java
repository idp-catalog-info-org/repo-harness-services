/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.rule.OwnerRule.ABHIJEET_GUPTA;
import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SAURABH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.dto.CITaskDetails;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.CIFeatureFlagTarget;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.LiteEngineConnectionCapability;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.expression.ExpressionEvaluator;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.rule.Owner;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.service.ScheduleResponse;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;

public class StageCleanupUtilityTest extends CIExecutionTestBase {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;

  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private RunnerRequestBuilder runnerRequestBuilder;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private CITaskDetailsRepository ciTaskDetailsRepository;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private HPersistence persistence;
  @Mock private Query<CIResourceCleanup> mockCleanupQuery;
  @Mock private UpdateOperations<CIResourceCleanup> mockCleanupUpdateOps;

  @InjectMocks private StageCleanupUtility stageCleanupUtility;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").build();
  private static final CIVmInitializeTaskParams.Type vmInfraInfo = CIVmInitializeTaskParams.Type.VM;

  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEventForRunning() throws IOException {
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance,
        OptionalSweepingOutput.builder()
            .found(true)
            .output(K8StageInfraDetails.builder()
                        .podName("podName")
                        .containerNames(new ArrayList<>())
                        .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                        .build())
            .build());
    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testBuildAndfetchAwsCleanUpParameters() throws IOException {
    String poolId = "test";
    String stageRuntimeId = "test";
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance,
        OptionalSweepingOutput.builder()
            .found(true)
            .output(VmStageInfraDetails.builder().poolId(poolId).infraInfo(vmInfraInfo).build())
            .build());

    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
    assertEquals(params.getLeft().getType(), CICleanupTaskParams.Type.VM);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEventForRunningLiteEngineCapability() throws IOException {
    String ip = "1.2.3.4";
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(outcomeService.resolve(any(), any())).thenReturn(LiteEnginePodDetailsOutcome.builder().ipAddress(ip).build());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance,
        OptionalSweepingOutput.builder()
            .found(true)
            .output(K8StageInfraDetails.builder()
                        .podName("podName")
                        .containerNames(new ArrayList<>())
                        .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                        .build())
            .build());

    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
    assertThat(((CIK8CleanupTaskParams) params.getLeft()).getLiteEngineIP()).isEqualTo(ip);
    assertThat(((CIK8CleanupTaskParams) params.getLeft()).getLiteEnginePort()).isEqualTo(LITE_ENGINE_PORT);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParameters() throws Exception {
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder()
            .podName("PodName")
            .containerNames(new ArrayList<>())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithUntrimmedNamespace())
            .build();

    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());
    CIK8CleanupTaskParams cik8CleanupTaskParams =
        Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance);

    assertThat(cik8CleanupTaskParams).isNotNull();
    assertThat(cik8CleanupTaskParams.getPodNameList().equals(Arrays.asList("PodName"))).isEqualTo(true);
    assertThat(cik8CleanupTaskParams.getNamespace().equals("testNamespace")).isEqualTo(true);
    assertThat(cik8CleanupTaskParams.isUseDefaultGracePeriod()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParametersWithoutLiteEngineIp() throws Exception {
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder()
            .podName("PodName")
            .containerNames(new ArrayList<>())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithUntrimmedNamespace())
            .build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_ENABLE_CAPABILITY_CHECK_INIT_CLEANUP), any())).thenReturn(true);
    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());
    CIK8CleanupTaskParams cik8CleanupTaskParams =
        Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance);

    assertThat(cik8CleanupTaskParams).isNotNull();
    assertThat(cik8CleanupTaskParams.getPodNameList().equals(Arrays.asList("PodName"))).isEqualTo(true);
    List<ExecutionCapability> capabilityCheckList =
        cik8CleanupTaskParams.fetchRequiredExecutionCapabilities(new ExpressionEvaluator());
    assertThat(capabilityCheckList.size()).isEqualTo(1);
    assertThat(capabilityCheckList.get(0)).isInstanceOf(LiteEngineConnectionCapability.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParametersDefaultGracePeriod() throws Exception {
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder()
            .podName("PodName")
            .containerNames(new ArrayList<>())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithUntrimmedNamespace())
            .build();
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());
    CIK8CleanupTaskParams cik8CleanupTaskParams =
        Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance);

    assertThat(cik8CleanupTaskParams).isNotNull();
    assertThat(cik8CleanupTaskParams.getPodNameList().equals(Arrays.asList("PodName"))).isEqualTo(true);
    assertThat(cik8CleanupTaskParams.getNamespace().equals("testNamespace")).isEqualTo(true);
    assertThat(cik8CleanupTaskParams.isUseDefaultGracePeriod()).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequestWithV1Version() throws InterruptedException {
    String stageIdentifier = "testStage";
    StageInfraDetails stageInfraDetails = mock(StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(StageInfraDetails.Type.VM);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(stageInfraDetails).build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyStageCleanupUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), VmStageInfraDetails.builder().build()))
        .when(spyStageCleanupUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    spyStageCleanupUtility.submitCleanupRequest(
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build(),
        stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submit(any());
    verify(runnerRequestBuilder, times(1)).buildCleanupRequest(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequestWithRouteToRunner() throws InterruptedException {
    String stageIdentifier = "testStage";
    StageInfraDetails stageInfraDetails = mock(StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(StageInfraDetails.Type.VM);
    when(stageInfraDetails.shouldRouteStageToRunner()).thenReturn(true);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(stageInfraDetails).build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");
    StageCleanupUtility spyStageCleanupUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), VmStageInfraDetails.builder().build()))
        .when(spyStageCleanupUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    spyStageCleanupUtility.submitCleanupRequest(ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submit(any());
    verify(runnerRequestBuilder, times(1)).buildCleanupRequest(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildAndfetchCleanUpParameters_whenStageInfraNotFound_shouldFallbackToPodCleanupDetails() {
    PodCleanupDetails podCleanupDetails = PodCleanupDetails.builder()
                                              .podName("fallbackPod")
                                              .cleanUpContainerNames(Arrays.asList("container1"))
                                              .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                                              .build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(podCleanupDetails).build());
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());

    Pair<CICleanupTaskParams, StageInfraDetails> result = stageCleanupUtility.buildAndfetchCleanUpParameters(
        ambiance, OptionalSweepingOutput.builder().found(false).build());

    assertThat(result.getLeft()).as("Should return K8 cleanup params from PodCleanupDetails fallback").isNotNull();
    assertThat(result.getRight().getType())
        .as("Infra type should be K8 when falling back to PodCleanupDetails")
        .isEqualTo(StageInfraDetails.Type.K8);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildAndfetchCleanUpParameters_whenNoCleanupDetailsFound_shouldThrow() {
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThatThrownBy(()
                           -> stageCleanupUtility.buildAndfetchCleanUpParameters(
                               ambiance, OptionalSweepingOutput.builder().found(false).build()))
        .as("Should throw when both stage infra and pod cleanup details are missing")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unable to do cleanup");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildAndfetchCleanUpParameters_whenDliteVmType_shouldReturnDliteParams() {
    String stageRuntimeId = "dliteRuntime";
    String poolId = "dlitePool";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId(poolId).build();

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Pair<CICleanupTaskParams, StageInfraDetails> result = stageCleanupUtility.buildAndfetchCleanUpParameters(
        ambiance, OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());

    assertThat(result.getLeft().getType())
        .as("Should return DLITE_VM cleanup params for DliteVmStageInfraDetails")
        .isEqualTo(CICleanupTaskParams.Type.DLITE_VM);
    assertThat(result.getRight()).as("Should return the original DliteVm infra details").isEqualTo(dliteInfra);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildAndfetchCleanUpParameters_whenUnknownType_shouldThrow() {
    StageInfraDetails unknownInfra = mock(StageInfraDetails.class);
    when(unknownInfra.getType()).thenReturn(StageInfraDetails.Type.ECS);

    assertThatThrownBy(()
                           -> stageCleanupUtility.buildAndfetchCleanUpParameters(
                               ambiance, OptionalSweepingOutput.builder().found(true).output(unknownInfra).build()))
        .as("Should throw for unknown infra type")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unknown infra type");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParameters_whenInfrastructureIsNull_shouldThrow() throws Exception {
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder().podName("PodName").containerNames(new ArrayList<>()).infrastructure(null).build();

    assertThatThrownBy(
        () -> Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance))
        .as("Should throw when infrastructure is null")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Input infrastructure can not be empty");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParameters_whenLiteEngineIpIsAvailable_shouldSetIp() throws Exception {
    String ip = "10.0.0.1";
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder()
            .podName("PodName")
            .containerNames(new ArrayList<>())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithUntrimmedNamespace())
            .build();

    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(LiteEnginePodDetailsOutcome.builder().ipAddress(ip).build())
                        .build());

    CIK8CleanupTaskParams result =
        Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance);

    assertThat(result.getLiteEngineIP()).as("Should set lite engine IP from outcome").isEqualTo(ip);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildK8CleanupParameters_whenSupportBundleEnabled_shouldSetGracePeriod() throws Exception {
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder()
            .podName("PodName")
            .containerNames(new ArrayList<>())
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithUntrimmedNamespace())
            .build();

    when(featureFlagService.isEnabled(eq(FeatureName.CI_SUPPORT_BUNDLE_COLLECTION), any())).thenReturn(true);
    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());

    CIK8CleanupTaskParams result =
        Whitebox.invokeMethod(stageCleanupUtility, "buildK8CleanupParameters", k8StageInfraDetails, ambiance);

    assertThat(result.getGracePeriodSeconds())
        .as("Should set gracePeriodSeconds to 90 when support bundle FF is enabled")
        .isEqualTo(90L);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildVmCleanupParameters_whenStageDetailsNotFound_shouldThrow() throws Exception {
    VmStageInfraDetails vmInfraDetails =
        VmStageInfraDetails.builder().poolId("pool1").infraInfo(CIVmInitializeTaskParams.Type.VM).build();
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThatThrownBy(
        () -> Whitebox.invokeMethod(stageCleanupUtility, "buildVmCleanupParameters", ambiance, vmInfraDetails))
        .as("Should throw when stage details are not found")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unable to fetch stage details");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildHostedVmCleanupParameters_shouldReturnDliteVmParams() throws Exception {
    String stageRuntimeId = "hosted-runtime-1";
    String poolId = "hosted-pool-1";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId(poolId).build();

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    DliteVmCleanupTaskParams result =
        Whitebox.invokeMethod(stageCleanupUtility, "buildHostedVmCleanupParameters", ambiance, dliteInfra);

    assertThat(result.getStageRuntimeId())
        .as("Should set stage runtime ID from stage details")
        .isEqualTo(stageRuntimeId);
    assertThat(result.getPoolId()).as("Should set pool ID from infra details").isEqualTo(poolId);
    assertThat(result.getLogKey()).as("Should set log key for cleanup").isNotNull();
    assertThat(result.getContext()).as("Should set context with account details").isNotNull();
    assertThat(result.getContext().getAccountID()).as("Should set account ID in context").isEqualTo("accountId");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testBuildHostedVmCleanupParameters_whenStageDetailsNotFound_shouldThrow() throws Exception {
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThatThrownBy(
        () -> Whitebox.invokeMethod(stageCleanupUtility, "buildHostedVmCleanupParameters", ambiance, dliteInfra))
        .as("Should throw when stage details are not found for hosted VM")
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unable to fetch stage details");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenNonRunnerPath_shouldUseSubmitAsyncTaskV2() throws InterruptedException {
    String stageIdentifier = "testStage";
    StageInfraDetails stageInfraDetails = mock(StageInfraDetails.class);
    when(stageInfraDetails.getType()).thenReturn(StageInfraDetails.Type.K8);
    when(stageInfraDetails.shouldRouteStageToRunner()).thenReturn(false);
    when(stageInfraDetails.isInitWithUnifiedAPI()).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(stageInfraDetails).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("asyncTaskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), K8StageInfraDetails.builder().build()))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance nonV1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("0").build()).build();

    spyUtility.submitCleanupRequest(nonV1Ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(any(), any());
    verify(delegateGrpcClientWrapper, times(0)).submit(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenVmWithTransactionId_shouldUseScheduledTaskAPI() throws InterruptedException {
    String stageIdentifier = "testStage";
    VmStageInfraDetails vmInfra = VmStageInfraDetails.builder()
                                      .poolId("pool1")
                                      .infraInfo(CIVmInitializeTaskParams.Type.VM)
                                      .transactionId("txn-123")
                                      .build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(vmInfra).build());
    ScheduleResponse scheduleResponse = new ScheduleResponse("scheduledTaskId", "scheduledId", "txn-123");
    when(delegateGrpcClientWrapper.submitScheduleTask(any())).thenReturn(scheduleResponse);
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), vmInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance v1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();

    spyUtility.submitCleanupRequest(v1Ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submitScheduleTask(any());
    verify(runnerRequestBuilder, times(1)).buildCleanupRequestV1(any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenStageInfraNotFound_shouldStillProceed() throws InterruptedException {
    String stageIdentifier = "testStage";
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), K8StageInfraDetails.builder().build()))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    spyUtility.submitCleanupRequest(ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenDliteVmInfra_shouldProceedWithRunner() throws InterruptedException {
    String stageIdentifier = "testStage";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("del-1").build())
                        .build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(DliteVmCleanupTaskParams.builder().build(), dliteInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance v1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();

    assertThatCode(() -> spyUtility.submitCleanupRequest(v1Ambiance, stageIdentifier))
        .as("Should not throw when submitting cleanup request for DLITE_VM infra")
        .doesNotThrowAnyException();

    verify(delegateGrpcClientWrapper, times(1)).submit(any());
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), eq("taskId"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDelegateCleanupTaskRequest_whenDliteVmDistributed_shouldSetTaskTypeV2() throws Exception {
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").distributed(true).build();
    DliteVmCleanupTaskParams dliteParams = DliteVmCleanupTaskParams.builder().poolId("pool1").build();
    Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams = Pair.of(dliteParams, dliteInfra);
    when(connectorUtils.fetchDelegateSelector(any(), any())).thenReturn(new ArrayList<>());

    DelegateTaskRequest result = Whitebox.invokeMethod(
        stageCleanupUtility, "getDelegateCleanupTaskRequest", ambiance, "accountId", cleanupParams);

    assertThat(result.getTaskType())
        .as("Task type should be DLITE_CI_VM_CLEANUP_TASK_V2 for distributed DLITE_VM")
        .contains("DLITE_CI_VM_CLEANUP_TASK_V2");
    assertThat(result.isExecuteOnHarnessHostedDelegates())
        .as("Should execute on harness hosted delegates for DLITE_VM")
        .isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDelegateCleanupTaskRequest_whenDliteVmNonDistributed_shouldFetchDelegateId() throws Exception {
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").distributed(false).build();
    DliteVmCleanupTaskParams dliteParams = DliteVmCleanupTaskParams.builder().poolId("pool1").build();
    Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams = Pair.of(dliteParams, dliteInfra);
    when(connectorUtils.fetchDelegateSelector(any(), any())).thenReturn(new ArrayList<>());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("delegate-123").build())
                        .build());

    DelegateTaskRequest result = Whitebox.invokeMethod(
        stageCleanupUtility, "getDelegateCleanupTaskRequest", ambiance, "accountId", cleanupParams);

    assertThat(result.isExecuteOnHarnessHostedDelegates())
        .as("Should execute on harness hosted delegates for DLITE_VM")
        .isTrue();
    assertThat(result.getEligibleToExecuteDelegateIds())
        .as("Should include delegate ID from VmDetailsOutcome")
        .contains("delegate-123");
    verify(ciTaskDetailsRepository, times(1)).deleteFirstByStageExecutionId(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDelegateCleanupTaskRequest_whenVmDockerType_shouldAddDelegateId() throws Exception {
    VmStageInfraDetails vmInfra =
        VmStageInfraDetails.builder().poolId("pool1").infraInfo(CIVmInitializeTaskParams.Type.VM).build();
    CIVmCleanupTaskParams vmParams = CIVmCleanupTaskParams.builder()
                                         .poolId("pool1")
                                         .stageRuntimeId("runtime1")
                                         .infraInfo(CIInitializeTaskParams.Type.DOCKER)
                                         .build();
    Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams = Pair.of(vmParams, vmInfra);
    when(connectorUtils.fetchDelegateSelector(any(), any())).thenReturn(new ArrayList<>());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("docker-delegate-1").build())
                        .build());

    DelegateTaskRequest result = Whitebox.invokeMethod(
        stageCleanupUtility, "getDelegateCleanupTaskRequest", ambiance, "accountId", cleanupParams);

    assertThat(result.getEligibleToExecuteDelegateIds())
        .as("Should include docker delegate ID for VM with Docker infra")
        .contains("docker-delegate-1");
    assertThat(result.getTaskType()).as("Task type should remain CI_CLEANUP for VM type").isEqualTo("CI_CLEANUP");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchDelegateId_whenOutcomeHasDelegateId_shouldReturnIt() throws Exception {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("del-abc").build())
                        .build());

    String result = Whitebox.invokeMethod(stageCleanupUtility, "fetchDelegateId", ambiance);

    assertThat(result).as("Should return delegate ID from VmDetailsOutcome").isEqualTo("del-abc");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchDelegateId_whenOutcomeIsNull_andDbHasDelegateId_shouldReturnFromDb() throws Exception {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(VmDetailsOutcome.builder().build()).build());
    CITaskDetails taskDetails = CITaskDetails.builder().delegateId("db-delegate-1").build();
    when(ciTaskDetailsRepository.findFirstByStageExecutionId(any())).thenReturn(Optional.of(taskDetails));

    String result = Whitebox.invokeMethod(stageCleanupUtility, "fetchDelegateId", ambiance);

    assertThat(result)
        .as("Should return delegate ID from database when outcome has no delegate ID")
        .isEqualTo("db-delegate-1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchDelegateId_whenNoDelegateIdAnywhere_shouldReturnNull() throws Exception {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(VmDetailsOutcome.builder().build()).build());
    CITaskDetails taskDetails = CITaskDetails.builder().build();
    when(ciTaskDetailsRepository.findFirstByStageExecutionId(any())).thenReturn(Optional.of(taskDetails));

    String result = Whitebox.invokeMethod(stageCleanupUtility, "fetchDelegateId", ambiance);

    assertThat(result).as("Should return null when no delegate ID is available anywhere").isNull();
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenSkipFFOnDliteVm_shouldShortCircuit() throws InterruptedException {
    String stageIdentifier = "testStage";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());
    when(
        featureFlagService.isEnabledForTarget(eq(FeatureName.CI_SKIP_CLOUD_VM_CLEANUP), any(CIFeatureFlagTarget.class)))
        .thenReturn(true);
    when(persistence.createQuery(eq(CIResourceCleanup.class), any())).thenReturn(mockCleanupQuery);
    when(mockCleanupQuery.filter(anyString(), any())).thenReturn(mockCleanupQuery);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(mockCleanupQuery.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.notEqual(any())).thenReturn(mockCleanupQuery);
    when(persistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockCleanupUpdateOps);
    when(mockCleanupUpdateOps.set(anyString(), any())).thenReturn(mockCleanupUpdateOps);

    StageCleanupUtility.CleanupSubmitResult result =
        stageCleanupUtility.submitCleanupRequest(ambiance, stageIdentifier);

    assertThat(result.isSubmitted()).isFalse();
    assertThat(result.getInfraType()).isEqualTo("HostedVm");
    verify(delegateGrpcClientWrapper, never()).submit(any());
    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(), any());
    verify(delegateGrpcClientWrapper, never()).submitScheduleTask(any());
    verify(waitNotifyEngine, never()).waitForAllOn(any(), any(), any());
    verify(mockCleanupUpdateOps, times(1)).set(eq(CIResourceCleanupResponseKeys.deferredByFF), eq(true));
    verify(mockCleanupUpdateOps, times(1)).set(eq(CIResourceCleanupResponseKeys.validUntil), any(java.util.Date.class));
    // processAfter must also be pushed out, otherwise the reaper would pick the row up
    // ~30 min later and dispatch cleanup, defeating the FF-driven delay.
    verify(mockCleanupUpdateOps, times(1)).set(eq(CIResourceCleanupResponseKeys.processAfter), anyLong());
    // Idempotency guard: defer query must filter on deferredByFF != true so a
    // duplicate stage-end event doesn't reset the 2-day clock.
    verify(mockCleanupQuery, times(1)).field(eq(CIResourceCleanupResponseKeys.deferredByFF));
    verify(fieldEnd, times(1)).notEqual(eq(true));
    verify(persistence, times(1)).update(any(Query.class), any(UpdateOperations.class));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenAlreadyDeferredAndFFOnDliteVm_shouldForceCleanup()
      throws InterruptedException {
    // Second pickup by the reaper at T₀+2d: the row's deferredByFF=true is
    // passed through, FF is still on, but cleanup must dispatch anyway so the
    // VM doesn't outlive the 2-day budget.
    String stageIdentifier = "testStage";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());
    when(
        featureFlagService.isEnabledForTarget(eq(FeatureName.CI_SKIP_CLOUD_VM_CLEANUP), any(CIFeatureFlagTarget.class)))
        .thenReturn(true);
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("del-1").build())
                        .build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(DliteVmCleanupTaskParams.builder().build(), dliteInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance v1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();

    spyUtility.submitCleanupRequest(v1Ambiance, stageIdentifier, true);

    verify(delegateGrpcClientWrapper, times(1)).submit(any());
    // Crucially, no second deferral write — we are forcing cleanup, not re-deferring.
    verify(persistence, never()).update(any(Query.class), any(UpdateOperations.class));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenSkipFFOnK8_shouldStillCleanup() throws InterruptedException {
    String stageIdentifier = "testStage";
    StageInfraDetails k8Infra = mock(StageInfraDetails.class);
    when(k8Infra.getType()).thenReturn(StageInfraDetails.Type.K8);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(k8Infra).build());
    when(
        featureFlagService.isEnabledForTarget(eq(FeatureName.CI_SKIP_CLOUD_VM_CLEANUP), any(CIFeatureFlagTarget.class)))
        .thenReturn(true);
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), K8StageInfraDetails.builder().build()))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    spyUtility.submitCleanupRequest(ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(any(), any());
    verify(persistence, never()).update(any(Query.class), any(UpdateOperations.class));
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenSkipFFDisabledOnDliteVm_shouldProceed() throws InterruptedException {
    String stageIdentifier = "testStage";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());
    when(
        featureFlagService.isEnabledForTarget(eq(FeatureName.CI_SKIP_CLOUD_VM_CLEANUP), any(CIFeatureFlagTarget.class)))
        .thenReturn(false);
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("del-1").build())
                        .build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any())).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(DliteVmCleanupTaskParams.builder().build(), dliteInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance v1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();

    spyUtility.submitCleanupRequest(v1Ambiance, stageIdentifier);

    verify(delegateGrpcClientWrapper, times(1)).submit(any());
    verify(persistence, never()).update(any(Query.class), any(UpdateOperations.class));
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenVmInfra_shouldSetMemoryMetricsLogKey() throws InterruptedException {
    String stageIdentifier = "testStage";
    VmStageInfraDetails vmInfra =
        VmStageInfraDetails.builder().poolId("pool1").infraInfo(CIVmInitializeTaskParams.Type.VM).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(vmInfra).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any(), any(String[].class))).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), vmInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    StageCleanupUtility.CleanupSubmitResult result = spyUtility.submitCleanupRequest(ambiance, stageIdentifier);

    assertThat(result.isSubmitted()).isTrue();
    assertThat(result.getInfraType()).isEqualTo("VM");
    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    verify(waitNotifyEngine).waitForAllOn(any(), callbackCaptor.capture(), any(String[].class));
    NotifyCallback captured = callbackCaptor.getValue();
    String memoryMetricsLogKey = Whitebox.getInternalState(captured, "memoryMetricsLogKey");
    assertThat(memoryMetricsLogKey).as("VM infra should set memoryMetricsLogKey").isNotNull();
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenDliteVmInfra_shouldSetMemoryMetricsLogKey() throws InterruptedException {
    String stageIdentifier = "testStage";
    DliteVmStageInfraDetails dliteInfra = DliteVmStageInfraDetails.builder().poolId("pool1").build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dliteInfra).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().delegateId("del-1").build())
                        .build());
    when(delegateGrpcClientWrapper.submit(any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any(), any(String[].class))).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(DliteVmCleanupTaskParams.builder().build(), dliteInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    Ambiance v1Ambiance =
        ambiance.toBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();

    StageCleanupUtility.CleanupSubmitResult dliteResult = spyUtility.submitCleanupRequest(v1Ambiance, stageIdentifier);

    assertThat(dliteResult.isSubmitted()).isTrue();
    assertThat(dliteResult.getInfraType()).isEqualTo("HostedVm");
    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    verify(waitNotifyEngine).waitForAllOn(any(), callbackCaptor.capture(), any(String[].class));
    NotifyCallback captured = callbackCaptor.getValue();
    String memoryMetricsLogKey = Whitebox.getInternalState(captured, "memoryMetricsLogKey");
    assertThat(memoryMetricsLogKey).as("DLITE_VM infra should set memoryMetricsLogKey").isNotNull();
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenK8Infra_shouldNotSetMemoryMetricsLogKey() throws InterruptedException {
    String stageIdentifier = "testStage";
    K8StageInfraDetails k8Infra = K8StageInfraDetails.builder()
                                      .podName("pod1")
                                      .containerNames(new ArrayList<>())
                                      .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                                      .build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(k8Infra).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");

    when(waitNotifyEngine.waitForAllOn(any(), any(), any(String[].class))).thenReturn("");
    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIK8CleanupTaskParams.builder().build(), k8Infra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    StageCleanupUtility.CleanupSubmitResult k8Result = spyUtility.submitCleanupRequest(ambiance, stageIdentifier);

    assertThat(k8Result.isSubmitted()).isTrue();
    assertThat(k8Result.getInfraType()).isEqualTo("KubernetesDirect");
    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    verify(waitNotifyEngine).waitForAllOn(any(), callbackCaptor.capture(), any(String[].class));
    NotifyCallback captured = callbackCaptor.getValue();
    String memoryMetricsLogKey = Whitebox.getInternalState(captured, "memoryMetricsLogKey");
    assertThat(memoryMetricsLogKey).as("K8 infra should NOT set memoryMetricsLogKey").isNull();
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testSubmitCleanupRequest_whenLocalDocker_shouldLabelDockerNotVm() throws InterruptedException {
    String stageIdentifier = "testStage";
    VmStageInfraDetails dockerInfra =
        VmStageInfraDetails.builder().poolId("pool1").infraInfo(CIInitializeTaskParams.Type.DOCKER).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(dockerInfra).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(waitNotifyEngine.waitForAllOn(any(), any(), any(String[].class))).thenReturn("");

    StageCleanupUtility spyUtility = spy(stageCleanupUtility);
    doReturn(Pair.of(CIVmCleanupTaskParams.builder().build(), dockerInfra))
        .when(spyUtility)
        .buildAndfetchCleanUpParameters(any(), any());

    StageCleanupUtility.CleanupSubmitResult result = spyUtility.submitCleanupRequest(ambiance, stageIdentifier);

    assertThat(result.isSubmitted()).isTrue();
    assertThat(result.getInfraType()).isEqualTo("DOCKER");
  }
}
