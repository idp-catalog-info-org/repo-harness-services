/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import static io.harness.rule.OwnerRule.SHUBHENDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.executions.steps.node.ExecutionNodeType;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.harness.step.HarnessApprovalStep;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationResponse;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationStatus;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CV)
@RunWith(MockitoJUnitRunner.class)
public class ChangeAdvisorV1StepTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrgId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String PIPELINE_ID = "testPipelineId";
  private static final String EXECUTION_ID = "testExecutionId";
  private static final String STEP_IDENTIFIER = "changeAdvisorStep";
  private static final String USER_GROUP = "account.UG_NAME";
  private static final String APPROVAL_INSTANCE_ID = "approvalInstanceId";

  @Mock private ChangeAdvisorEvaluationHelper evaluationHelper;
  @Mock private io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService sweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private ApprovalNotificationHandler approvalNotificationHandler;
  @Mock private ExecutorService executorService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  private ChangeAdvisorV1Step changeAdvisorV1Step;
  private HarnessApprovalStep harnessApprovalStep;
  private Ambiance ambiance;

  @Before
  public void setup() throws Exception {
    harnessApprovalStep = new HarnessApprovalStep();
    injectField(harnessApprovalStep, "approvalInstanceService", approvalInstanceService);
    injectField(harnessApprovalStep, "sweepingOutputService", sweepingOutputService);
    injectField(harnessApprovalStep, "executorService", executorService);
    injectField(harnessApprovalStep, "approvalNotificationHandler", approvalNotificationHandler);
    injectField(harnessApprovalStep, "logStreamingStepClientFactory", logStreamingStepClientFactory);

    changeAdvisorV1Step = new ChangeAdvisorV1Step();
    injectField(changeAdvisorV1Step, "evaluationHelper", evaluationHelper);
    injectField(changeAdvisorV1Step, "sweepingOutputService", sweepingOutputService);
    injectField(changeAdvisorV1Step, "outcomeService", outcomeService);
    injectField(changeAdvisorV1Step, "approvalInstanceService", approvalInstanceService);
    injectField(changeAdvisorV1Step, "approvalNotificationHandler", approvalNotificationHandler);
    injectField(changeAdvisorV1Step, "executorService", executorService);
    injectField(changeAdvisorV1Step, "harnessApprovalStep", harnessApprovalStep);

    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    when(sweepingOutputService.consume(any(), any(), any(), any())).thenReturn("");

    ambiance = buildAmbiance();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbacGateDecisionSavesApprovalAndReturnsCallbackId() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    EvaluationResponse evaluationResponse = buildEvaluationResponse("GATE", 0.42);
    when(evaluationHelper.evaluate(eq(ambiance), any())).thenReturn(evaluationResponse);
    stubApprovalSave();
    when(approvalNotificationHandler.getUserGroups(any())).thenReturn(Collections.singletonList(buildUserGroup()));

    AsyncExecutableResponse response =
        changeAdvisorV1Step.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsList()).containsExactly(APPROVAL_INSTANCE_ID);
    verify(approvalInstanceService).save(any(HarnessApprovalInstance.class));

    InOrder inOrder = inOrder(outcomeService, approvalInstanceService, sweepingOutputService);
    inOrder.verify(outcomeService)
        .consume(eq(ambiance), eq(ChangeAdvisorStep.OUTCOME_NAME), any(ChangeAdvisorOutcome.class), eq(""));
    inOrder.verify(approvalInstanceService).save(any(HarnessApprovalInstance.class));
    inOrder.verify(sweepingOutputService)
        .consume(eq(ambiance), eq(ChangeAdvisorV1Step.CHANGE_ADVISOR_APPROVAL_OUTCOME),
            any(ChangeAdvisorApprovalStepOutcome.class), eq(""));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbacAllowDecisionDoesNotSaveApprovalInstance() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    EvaluationResponse evaluationResponse = buildEvaluationResponse("ALLOW", 0.95);
    when(evaluationHelper.evaluate(eq(ambiance), any())).thenReturn(evaluationResponse);

    AsyncExecutableResponse response =
        changeAdvisorV1Step.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsList()).isEmpty();
    verify(approvalInstanceService, never()).save(any());
    verify(outcomeService)
        .consume(eq(ambiance), eq(ChangeAdvisorStep.OUTCOME_NAME), any(ChangeAdvisorOutcome.class), eq(""));
    verify(sweepingOutputService, never())
        .consume(eq(ambiance), eq(ChangeAdvisorV1Step.CHANGE_ADVISOR_APPROVAL_OUTCOME), any(), any());
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbacGateDecisionSetsHarnessApprovalTypeAfterStepContext() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    when(stepParameters.getType()).thenReturn("ChangeAdvisor");
    EvaluationResponse evaluationResponse = buildEvaluationResponse("GATE", 0.42);
    when(evaluationHelper.evaluate(eq(ambiance), any())).thenReturn(evaluationResponse);
    stubApprovalSave();
    when(approvalNotificationHandler.getUserGroups(any())).thenReturn(Collections.singletonList(buildUserGroup()));

    changeAdvisorV1Step.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<HarnessApprovalInstance> captor = ArgumentCaptor.forClass(HarnessApprovalInstance.class);
    verify(approvalInstanceService).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(ApprovalType.HARNESS_APPROVAL);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbacComingSoonPublishesOutcome() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    ChangeAdvisorComingSoonOutcome comingSoonOutcome =
        ChangeAdvisorComingSoonOutcome.builder().comingSoon(true).contextType("CI").build();
    when(evaluationHelper.evaluate(eq(ambiance), any()))
        .thenReturn(EvaluationResponse.builder()
                        .status(EvaluationStatus.COMING_SOON)
                        .comingSoonOutcome(comingSoonOutcome)
                        .build());

    AsyncExecutableResponse response =
        changeAdvisorV1Step.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsList()).isEmpty();
    verify(outcomeService).consume(ambiance, ChangeAdvisorStep.OUTCOME_NAME, comingSoonOutcome, "");
    verify(approvalInstanceService, never()).save(any());
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalAllowReturnsSucceededWithoutApprovalInstance() {
    StepResponse stepResponse = changeAdvisorV1Step.handleAsyncResponseInternal(
        ambiance, buildStepParameters(buildSpecWithApprovers()), Map.of());

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isEmpty();
    verify(approvalInstanceService, never()).get(any());
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalApprovedReturnsHarnessApprovalOutcome() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    HarnessApprovalResponseData responseData =
        HarnessApprovalResponseData.builder().approvalInstanceId(APPROVAL_INSTANCE_ID).build();
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.APPROVED);
    when(approvalInstanceService.get(APPROVAL_INSTANCE_ID)).thenReturn(approvalInstance);

    StepResponse stepResponse = changeAdvisorV1Step.handleAsyncResponseInternal(
        ambiance, stepParameters, Collections.singletonMap("key", responseData));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(stepOutcome.getName()).isEqualTo("output");
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRejectedReturnsHarnessApprovalOutcome() {
    StepBaseParameters stepParameters = buildStepParameters(buildSpecWithApprovers());
    HarnessApprovalResponseData responseData =
        HarnessApprovalResponseData.builder().approvalInstanceId(APPROVAL_INSTANCE_ID).build();
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.REJECTED);
    when(approvalInstanceService.get(APPROVAL_INSTANCE_ID)).thenReturn(approvalInstance);

    StepResponse stepResponse = changeAdvisorV1Step.handleAsyncResponseInternal(
        ambiance, stepParameters, Collections.singletonMap("key", responseData));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.APPROVAL_REJECTED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(stepOutcome.getName()).isEqualTo("output");
  }

  private EvaluationResponse buildEvaluationResponse(String decision, double score) {
    Advisory advisory = new Advisory();
    advisory.setId("adv-" + decision.toLowerCase());
    advisory.setDecision(decision);
    advisory.setScore(score);
    advisory.setStatus("COMPLETED");

    ChangeAdvisorOutcome outcome = ChangeAdvisorOutcome.builder()
                                       .advisoryId(advisory.getId())
                                       .decision(decision)
                                       .score(score)
                                       .status(advisory.getStatus())
                                       .build();

    return EvaluationResponse.builder()
        .status(EvaluationStatus.ADVISORY_RECEIVED)
        .advisorOutcome(outcome)
        .advisory(advisory)
        .build();
  }

  private void stubApprovalSave() {
    doAnswer(invocation -> {
      HarnessApprovalInstance instance = invocation.getArgument(0, HarnessApprovalInstance.class);
      instance.setId(APPROVAL_INSTANCE_ID);
      return instance;
    })
        .when(approvalInstanceService)
        .save(any(HarnessApprovalInstance.class));
  }

  private StepBaseParameters buildStepParameters(ChangeAdvisorStepSpecParameters spec) {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(spec);
    when(stepBaseParameters.getIdentifier()).thenReturn(STEP_IDENTIFIER);
    when(stepBaseParameters.getName()).thenReturn(STEP_IDENTIFIER);
    when(stepBaseParameters.getType()).thenReturn("ChangeAdvisor");
    return stepBaseParameters;
  }

  private ChangeAdvisorStepSpecParameters buildSpecWithApprovers() {
    return ChangeAdvisorStepSpecParameters.builder()
        .approvers(Approvers.builder()
                       .userGroups(ParameterField.createValueField(Collections.singletonList(USER_GROUP)))
                       .minimumCount(ParameterField.createValueField(1))
                       .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", ORG_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_ID)
        .setPlanExecutionId(EXECUTION_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .addLevels(Level.newBuilder()
                       .setRuntimeId("stage-runtime")
                       .setSetupId("stage-setup")
                       .setIdentifier("stage1")
                       .setStepType(StepType.newBuilder()
                                        .setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP_V1.getName())
                                        .setStepCategory(StepCategory.STAGE)
                                        .build())
                       .build())
        .build();
  }

  private UserGroupDTO buildUserGroup() {
    return UserGroupDTO.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .name("UG NAME")
        .identifier("UG_NAME")
        .build();
  }

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
