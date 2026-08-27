/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;
import static io.harness.beans.steps.CIStepInfoType.UNIFIED_MANIFESTS_STEP;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.ci.states.V1.cd.helpers.UnifiedServiceStepOpaHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;

public class UnifiedServiceStepTest {
  private static final String accountId = "test-account";
  private static final String orgId = "test-org-id";
  private static final String projectId = "test-project-id";
  private static final String pipelineId = "test-pipeline-id";
  private static final String stageExecutionId = "test-stage-id";
  private static final String planExecutionId = "plan-execution-id";
  private static final String SETUP_ID = "test-id";

  @Mock private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance(boolean isPIESimplifyLogBaseEnabled) {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(SETUP_ID)
                   .setIdentifier("unifiedManifestStep")
                   .setStepType(StepType.newBuilder()
                                    .setType(UNIFIED_MANIFESTS_STEP.getDisplayName())
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
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier(pipelineId)
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, isPIESimplifyLogBaseEnabled)
                         .build())
        .build();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetLogKeyWithPIESimplifyLogBaseEnabled() throws Exception {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Ambiance ambiance = buildAmbiance(true);
    String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    String manifestLogKey =
        Whitebox.invokeMethod(serviceStep, "appendChildStepLevel", logBaseKey, "manifests", ambiance);
    String expectedManifestLogKey =
        "test-account/pipeline/test-pipeline-id/0/-plan-execution-id/unifiedManifestStep/manifests";
    assertThat(manifestLogKey).isEqualTo(expectedManifestLogKey);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetLogKeyWithPIESimplifyLogBaseDisabled() throws Exception {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Ambiance ambiance = buildAmbiance(false);
    String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    String manifestLogKey =
        Whitebox.invokeMethod(serviceStep, "appendChildStepLevel", logBaseKey, "manifests", ambiance);
    String expectedManifestLogKey =
        "accountId:test-account/orgId:test-org-id/projectId:test-project-id/pipelineId:test-pipeline-id/runSequence:0/"
        + "level0:unifiedManifestStep/level1:manifests";
    assertThat(manifestLogKey).isEqualTo(expectedManifestLogKey);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationCalledOnSuccess() {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Whitebox.setInternalState(serviceStep, "unifiedServiceStepOpaHelper", unifiedServiceStepOpaHelper);

    Ambiance ambiance = buildAmbiance(true);

    UnifiedServiceOutcome serviceOutcome =
        UnifiedServiceOutcome.builder().identifier("test-service").name("Test Service").type("Kubernetes").build();

    List<StepResponse.StepOutcome> stepOutcomes =
        Collections.singletonList(StepResponse.StepOutcome.builder().name("service").outcome(serviceOutcome).build());

    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(stepOutcomes).build();

    doNothing()
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
    serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);
    verify(unifiedServiceStepOpaHelper, times(1))
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationThrowsPolicyViolation() {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Whitebox.setInternalState(serviceStep, "unifiedServiceStepOpaHelper", unifiedServiceStepOpaHelper);

    Ambiance ambiance = buildAmbiance(true);

    UnifiedServiceOutcome serviceOutcome =
        UnifiedServiceOutcome.builder().identifier("test-service").name("Test Service").type("Kubernetes").build();

    List<StepResponse.StepOutcome> stepOutcomes =
        Collections.singletonList(StepResponse.StepOutcome.builder().name("service").outcome(serviceOutcome).build());

    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(stepOutcomes).build();

    String policyErrorMessage = "Policy violation: Docker images must be from harness registry";
    doThrow(new PolicyEvaluationFailureException(policyErrorMessage))
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));

    assertThatThrownBy(() -> serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .hasMessage(policyErrorMessage);
    verify(unifiedServiceStepOpaHelper, times(1))
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationNotCalledOnFailedStep() {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Whitebox.setInternalState(serviceStep, "unifiedServiceStepOpaHelper", unifiedServiceStepOpaHelper);

    Ambiance ambiance = buildAmbiance(true);

    List<StepResponse.StepOutcome> stepOutcomes = Collections.emptyList();

    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).stepOutcomes(stepOutcomes).build();
    doNothing()
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
    serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);
    verify(unifiedServiceStepOpaHelper, times(1))
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testOpaEvaluationWithEmptyOutcomes() {
    UnifiedServiceStep serviceStep = new UnifiedServiceStep();
    Whitebox.setInternalState(serviceStep, "unifiedServiceStepOpaHelper", unifiedServiceStepOpaHelper);

    Ambiance ambiance = buildAmbiance(true);

    List<StepResponse.StepOutcome> stepOutcomes = Collections.emptyList();

    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(stepOutcomes).build();

    doNothing()
        .when(unifiedServiceStepOpaHelper)
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
    serviceStep.callOpaForServiceRuntimeContext(ambiance, stepOutcomes, stepResponse);

    verify(unifiedServiceStepOpaHelper, times(1))
        .checkAndCallOpaForServiceRuntimeContext(eq(ambiance), eq(stepOutcomes), any(StepResponse.class));
  }
}
