/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_CD_INFRA_STEP;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.INFRA_STEP_OUTCOME;
import static io.harness.ci.states.V1.cd.UnifiedCDInfraStep.HARNESS_KUBE_CONFIG_PATH;
import static io.harness.ci.states.V1.cd.UnifiedCDInfraStep.INFRA;
import static io.harness.ci.states.V1.cd.UnifiedCDInfraStep.PLUGIN_HARNESS_KUBE_CONFIG_PATH;
import static io.harness.ci.states.V1.cd.UnifiedCDInfraStep.ROLLBACK_DATA_OUTPUT_KEY;
import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME_YAML;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.rollback.RollbackSweepingOutput;
import io.harness.ci.execution.states.rollback.StepRollbackDataHelper;
import io.harness.ci.states.V1.cd.InfraStepNGOutcomeKeys;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStep;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStepParameters;
import io.harness.ci.states.V1.cd.helpers.UnifiedInfraStepOpaHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepOutputV2;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CIStageOutputHelper;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UnifiedCDInfraStepTest {
  @Mock private UnifiedInfraStepOpaHelper unifiedInfraStepOpaHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private StepRollbackDataHelper stepRollbackDataHelper;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private CIStageOutputHelper ciStageOutputHelper;

  @InjectMocks private UnifiedCDInfraStep unifiedCDInfraStep;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(INFRA_V0_OUTCOME)));
  }

  private Ambiance buildAmbiance() {
    return buildAmbiance(ExecutionMode.NORMAL);
  }

  private Ambiance buildAmbiance(ExecutionMode executionMode) {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId("test-id")
                   .setIdentifier("unifiedInfraStep")
                   .setStepType(StepType.newBuilder()
                                    .setType(UNIFIED_CD_INFRA_STEP.getDisplayName())
                                    .setStepCategory(StepCategory.STEP)
                                    .build())
                   .setRetryIndex(0)
                   .build());
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", "test-account", "orgIdentifier", "test-org", "projectIdentifier",
            "test-project", "pipelineIdentifier", "test-pipeline"))
        .addAllLevels(levels)
        .setPlanExecutionId("plan-execution-id")
        .setStageExecutionId("test-stage-id")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("test-pipeline")
                         .setExecutionMode(executionMode)
                         .build())
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(unifiedCDInfraStep.getStepParametersClass()).isEqualTo(UnifiedCDInfraStepParameters.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateResources_DoesNotThrow() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    unifiedCDInfraStep.validateResources(ambiance, stepParameters);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_EmptyResponseMap_NoInfraOutcome() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_EmptyResponseMap_WithInfraOutcome() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("test-infra")
                                            .name("Test Infra")
                                            .kind("KubernetesDirect")
                                            .infrastructureKey("test-key")
                                            .build();

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build());

    doNothing().when(unifiedInfraStepOpaHelper).checkAndCallOpaForInfrastructureRuntimeContext(any(), any(), any());

    doNothing().when(stepRollbackDataHelper).updateStageRollbackData(any(), any(Status.class), any(), any());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_V0Path_PopulatesReleaseIdNameInfraIdentifier() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("test-infra")
                                            .name("Test Infra")
                                            .releaseId("test-release-id")
                                            .kind("KubernetesDirect")
                                            .build();
    infraStepOutcome.populateMap();

    VariablesSweepingOutput ngInfraOutcome = new VariablesSweepingOutput();
    ngInfraOutcome.put(INFRA_V0_OUTCOME_YAML, "kind: KubernetesDirect");

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build());
    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(INFRA_V0_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(ngInfraOutcome).build());

    doNothing().when(unifiedInfraStepOpaHelper).checkAndCallOpaForInfrastructureRuntimeContext(any(), any(), any());
    doNothing().when(stepRollbackDataHelper).updateStageRollbackData(any(), any(Status.class), any(), any());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    StepResponse.StepOutcome stepOutcome = response.getStepOutcomes().stream().findFirst().orElse(null);
    assertThat(stepOutcome).isNotNull();
    VariablesSweepingOutput outcomeMap = (VariablesSweepingOutput) stepOutcome.getOutcome();
    assertThat(outcomeMap.get(InfraStepNGOutcomeKeys.RELEASE_ID)).isEqualTo("test-release-id");
    assertThat(outcomeMap.get(InfraStepNGOutcomeKeys.NAME)).isEqualTo("Test Infra");
    assertThat(outcomeMap.get(InfraStepNGOutcomeKeys.INFRA_IDENTIFIER)).isEqualTo("test-infra");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_K8Response_Success() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(K8StageInfraDetails.builder().build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_K8Response_PublishesKubeConfigPathAsStageOutput() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .outputV2(List.of(StepOutputV2.builder()
                                                  .key(PLUGIN_HARNESS_KUBE_CONFIG_PATH)
                                                  .value("/harness/.kube/config")
                                                  .build()))
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(K8StageInfraDetails.builder().build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<Map<String, String>> outputsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(ciStageOutputHelper)
        .populateCIStageOutputs(outputsCaptor.capture(), eq("test-account"), eq("test-stage-id"));
    assertThat(outputsCaptor.getValue())
        .containsEntry(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/config")
        .containsEntry(HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/config");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_VmResponse_PublishesKubeConfigPathAsStageOutput() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    VmTaskExecutionResponse vmTaskExecutionResponse =
        VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .outputVars(Map.of(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/tmp/kubeconfig"))
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", vmTaskExecutionResponse);

    when(serializedResponseDataHelper.deserialize(vmTaskExecutionResponse)).thenReturn(vmTaskExecutionResponse);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(VmStageInfraDetails.builder().build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<Map<String, String>> outputsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(ciStageOutputHelper)
        .populateCIStageOutputs(outputsCaptor.capture(), eq("test-account"), eq("test-stage-id"));
    assertThat(outputsCaptor.getValue())
        .containsEntry(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/tmp/kubeconfig")
        .containsEntry(HARNESS_KUBE_CONFIG_PATH, "/tmp/kubeconfig");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_NoKubeConfigInOutput_DoesNotPublishStageOutput() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .outputV2(List.of(StepOutputV2.builder().key("SOME_OTHER_VAR").value("value").build()))
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(K8StageInfraDetails.builder().build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(ciStageOutputHelper, never()).populateCIStageOutputs(any(), any(), any());
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_RollbackMode_RefreshesKubeConfigPathInRollbackData() {
    Ambiance ambiance = buildAmbiance(ExecutionMode.POST_EXECUTION_ROLLBACK);
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .outputV2(List.of(StepOutputV2.builder()
                                                  .key(PLUGIN_HARNESS_KUBE_CONFIG_PATH)
                                                  .value("/harness/.kube/rollback-config")
                                                  .build()))
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(K8StageInfraDetails.builder().build());

    RollbackSweepingOutput rollbackSweepingOutput = new RollbackSweepingOutput();
    Map<String, String> infraOutput = new HashMap<>();
    infraOutput.put(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/stale-config");
    rollbackSweepingOutput.put(INFRA, infraOutput);
    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ROLLBACK_DATA_OUTPUT_KEY))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(rollbackSweepingOutput).build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RollbackSweepingOutput> rollbackCaptor = ArgumentCaptor.forClass(RollbackSweepingOutput.class);
    verify(sweepingOutputService)
        .consumeUpsert(any(Ambiance.class), eq(ROLLBACK_DATA_OUTPUT_KEY), rollbackCaptor.capture(),
            eq(StepCategory.PIPELINE.name()));
    assertThat((Map<String, String>) rollbackCaptor.getValue().get(INFRA))
        .containsEntry(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/rollback-config");

    ArgumentCaptor<Map<String, String>> outputsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(ciStageOutputHelper)
        .populateCIStageOutputs(outputsCaptor.capture(), eq("test-account"), eq("test-stage-id"));
    assertThat(outputsCaptor.getValue())
        .containsEntry(PLUGIN_HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/rollback-config")
        .containsEntry(HARNESS_KUBE_CONFIG_PATH, "/harness/.kube/rollback-config");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_NormalMode_DoesNotReadOrWriteRollbackData() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(K8StageInfraDetails.builder().build());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(sweepingOutputService, never())
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ROLLBACK_DATA_OUTPUT_KEY)));
    verify(sweepingOutputService, never()).consumeUpsert(any(Ambiance.class), any(), any(), any());
    verify(ciStageOutputHelper, never()).populateCIStageOutputs(any(), any(), any());
  }
}
