/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_CD_INFRA_STEP;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.INFRA_STEP_OUTCOME;
import static io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.rollback.StepRollbackDataHelper;
import io.harness.ci.states.V1.cd.helpers.UnifiedInfraStepOpaHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UnifiedCDInfraStepTest {
  private static final String accountId = "test-account";
  private static final String orgId = "test-org-id";
  private static final String projectId = "test-project-id";
  private static final String pipelineId = "test-pipeline-id";
  private static final String stageExecutionId = "test-stage-id";
  private static final String planExecutionId = "plan-execution-id";
  private static final String SETUP_ID = "test-id";

  @Mock private UnifiedInfraStepOpaHelper unifiedInfraStepOpaHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private StepRollbackDataHelper stepRollbackDataHelper;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;

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
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(SETUP_ID)
                   .setIdentifier("unifiedInfraStep")
                   .setStepType(StepType.newBuilder()
                                    .setType(UNIFIED_CD_INFRA_STEP.getDisplayName())
                                    .setStepCategory(StepCategory.STEP)
                                    .build())
                   .setRetryIndex(0)
                   .build());
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", accountId, "orgIdentifier", orgId, "projectIdentifier", projectId,
            "pipelineIdentifier", pipelineId))
        .addAllLevels(levels)
        .setPlanExecutionId(planExecutionId)
        .setStageExecutionId(stageExecutionId)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build())
        .build();
  }
  private InfraStepOutcome buildInfraStepOutcome() {
    return InfraStepOutcome.builder()
        .identifier("test-infra")
        .name("Test Infrastructure")
        .kind("KubernetesDirect")
        .description("Test infrastructure for OPA evaluation")
        .infrastructureKey("test-infra-key")
        .build();
  }
  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCallOpaForInfraRuntimeContext_Success() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);

    doNothing()
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));

    unifiedCDInfraStep.callOpaForInfraRuntimeContext(ambiance, infraStepOutcome, responseBuilder);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCallOpaForInfraRuntimeContext_PolicyViolation() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);

    String policyErrorMessage = "Policy violation: Infrastructure must use approved namespaces";
    doThrow(new PolicyEvaluationFailureException(policyErrorMessage))
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));

    assertThatThrownBy(
        () -> unifiedCDInfraStep.callOpaForInfraRuntimeContext(ambiance, infraStepOutcome, responseBuilder))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .hasMessageContaining(policyErrorMessage);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCallOpaForInfraRuntimeContext_CatchAndRethrowException() {
    Ambiance ambiance = buildAmbiance();
    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);

    String policyErrorMessage = "Policy evaluation failed for infrastructure step";
    PolicyEvaluationFailureException policyException = new PolicyEvaluationFailureException(policyErrorMessage);

    doThrow(policyException)
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));

    assertThatThrownBy(
        () -> unifiedCDInfraStep.callOpaForInfraRuntimeContext(ambiance, infraStepOutcome, responseBuilder))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .isSameAs(policyException);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(eq(ambiance), eq(infraStepOutcome), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_WithEmptyResponseMap_CallsOpa() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build());

    doNothing()
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));

    doNothing()
        .when(stepRollbackDataHelper)
        .updateStageRollbackData(any(), any(Status.class), any(Ambiance.class), any());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_WithK8Response_CallsOpa() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task-id", stepStatusTaskResponseData);

    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();

    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);

    K8StageInfraDetails k8StageInfraDetails = K8StageInfraDetails.builder().build();
    when(commonAbstractStepUtils.getStageInfra(any(Ambiance.class))).thenReturn(k8StageInfraDetails);

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build());

    doNothing()
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));

    doNothing()
        .when(stepRollbackDataHelper)
        .updateStageRollbackData(any(), any(Status.class), any(Ambiance.class), any());

    StepResponse response = unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_OpaThrowsException_PropagatesException() {
    Ambiance ambiance = buildAmbiance();
    UnifiedCDInfraStepParameters stepParameters = UnifiedCDInfraStepParameters.builder().build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    InfraStepOutcome infraStepOutcome = buildInfraStepOutcome();

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build());

    String policyErrorMessage = "Policy violation detected";
    doThrow(new PolicyEvaluationFailureException(policyErrorMessage))
        .when(unifiedInfraStepOpaHelper)
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));

    assertThatThrownBy(() -> unifiedCDInfraStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .hasMessageContaining(policyErrorMessage);

    verify(unifiedInfraStepOpaHelper, times(1))
        .checkAndCallOpaForInfrastructureRuntimeContext(
            any(Ambiance.class), any(InfraStepOutcome.class), any(StepResponse.class));
  }
}