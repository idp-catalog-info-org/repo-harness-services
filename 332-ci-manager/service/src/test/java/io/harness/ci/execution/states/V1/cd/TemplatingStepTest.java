/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.rule.OwnerRule.LOKESH_BIHANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ServiceHookMetadata;
import io.harness.cd.beans.outcomes.ServiceHooksSweepingOutput;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.states.V1.cd.ResponseHandlerUtils;
import io.harness.ci.states.V1.cd.ServiceHookTaskHelper;
import io.harness.ci.states.V1.cd.TemplatingStep;
import io.harness.ci.states.V1.cd.TemplatingStepParameters;
import io.harness.ci.states.V1.cd.TemplatingStepPassThroughData;
import io.harness.ci.states.V1.cd.TemplatingStepPassThroughData.ChainLink;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.DeployTemplateFetchHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TemplatingStepTest {
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Mock private RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private DeployTemplateFetchHelper deployTemplateFetchHelper;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ResponseHandlerUtils responseHandlerUtils;
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private ServiceHookTaskHelper serviceHookTaskHelper;

  @InjectMocks private TemplatingStep templatingStep;

  private static final String ACCOUNT_ID = "testAccount";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "org", "projectIdentifier", "proj"))
        .build();
  }

  private TemplatingStepParameters buildStepParameters() {
    return TemplatingStepParameters.builder().id("templating-step").name("Templating Step").build();
  }

  private ServiceHookMetadata buildHookMetadata(String stepId) {
    return ServiceHookMetadata.builder()
        .stepId(stepId)
        .hookYaml("hookYaml-" + stepId)
        .logKey("logKey-" + stepId)
        .build();
  }

  private ServiceHooksSweepingOutput buildHooksOutput(List<String> stepIds) {
    LinkedHashMap<String, ServiceHookMetadata> map = new LinkedHashMap<>();
    for (String id : stepIds) {
      map.put(id, buildHookMetadata(id));
    }
    return ServiceHooksSweepingOutput.builder().hookMetadataMap(map).envVars(null).build();
  }

  private TemplatingStepPassThroughData extractPtd(AsyncChainExecutableResponse response) {
    return RecastOrchestrationUtils.fromBytes(
        response.getPassThroughData().toByteArray(), TemplatingStepPassThroughData.class);
  }

  // -----------------------------------------------------------------------
  // Test 1: nothing to do → chainEnd=true immediately; finalize returns SKIPPED
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void startChainLink_nothingToDo_returnsChainEndImmediately() {
    Ambiance ambiance = buildAmbiance();

    when(serviceHookTaskHelper.isServiceHooksEnabled(ambiance)).thenReturn(false);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    // willRunTemplating: no manifest type found → returns empty → false
    when(cdStepsExpressionResolver.renderValue(any(), any(), eq(true))).thenReturn("");

    AsyncChainExecutableResponse response =
        templatingStep.startChainLinkAfterRbac(ambiance, buildStepParameters(), null);

    assertThat(response.getChainEnd()).isTrue();
    assertThat(response.getCallbackIdsList()).isEmpty();
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void finalizeExecution_withNullPtd_returnsSkipped() throws Exception {
    Ambiance ambiance = buildAmbiance();

    StepResponse response =
        templatingStep.finalizeExecutionWithSecurityContext(ambiance, buildStepParameters(), null, () -> null);

    assertThat(response.getStatus()).isEqualTo(Status.SKIPPED);
  }

  // -----------------------------------------------------------------------
  // Test 2: full chain — 2 pre-hooks → templating → 2 post-hooks, submitted in order, each exactly once
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void fullChain_twoPreHooksThenTemplatingThenTwoPostHooks_submittedInOrder() throws Exception {
    Ambiance ambiance = buildAmbiance();
    TemplatingStepParameters stepParams = buildStepParameters();

    ServiceHooksSweepingOutput preHooksOutput = buildHooksOutput(List.of("preHook1", "preHook2"));
    ServiceHooksSweepingOutput postHooksOutput = buildHooksOutput(List.of("postHook1", "postHook2"));
    String templateYaml = "template: yaml";

    when(serviceHookTaskHelper.isServiceHooksEnabled(ambiance)).thenReturn(true);
    when(serviceStepSweepingOutputHelper.fetchPreTemplateHooksSweepingOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(preHooksOutput).build());
    when(serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(postHooksOutput).build());
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(cdStepsExpressionResolver.renderValue(
             any(), eq(TemplatingStep.SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP), eq(true)))
        .thenReturn("k8s");
    when(cdStepsExpressionResolver.renderValue(
             any(), eq(TemplatingStep.SERVICE_OUTPUT_FILES_TO_TEMPLATIZED_EXP), eq(true)))
        .thenReturn("overrides.yaml");
    when(deployTemplateFetchHelper.getTemplatingTemplateYamlContent(eq("k8s"), any())).thenReturn(templateYaml);
    when(commonAbstractStepUtils.getStageInfra(ambiance))
        .thenReturn(VmStageInfraDetails.builder().type(VmStageInfraDetails.Type.VM).build());
    when(serviceHookTaskHelper.submitHookTask(any(), any(), any(), any(), any()))
        .thenReturn("cb-preHook1", "cb-preHook2", "cb-postHook1", "cb-postHook2");
    when(runnerSubmitTaskUtils.submitTaskByTemplate(any(), any(), any(), any(), any(), any()))
        .thenReturn("cb-templating");

    // Step 1: startChainLinkAfterRbac — submits preHook1
    AsyncChainExecutableResponse r1 = templatingStep.startChainLinkAfterRbac(ambiance, stepParams, null);
    assertThat(r1.getChainEnd()).isFalse();
    assertThat(r1.getCallbackIdsList()).containsExactly("cb-preHook1");
    TemplatingStepPassThroughData ptd1 = extractPtd(r1);
    assertThat(ptd1.getPendingPreHooks()).hasSize(1);
    assertThat(ptd1.getPendingPostHooks()).hasSize(2);

    // Step 2: preHook1 done → submits preHook2
    AsyncChainExecutableResponse r2 = templatingStep.executeNextLinkWithSecurityContext(
        ambiance, stepParams, null, ptd1, () -> Collections.emptyMap());
    assertThat(r2.getChainEnd()).isFalse();
    assertThat(r2.getCallbackIdsList()).containsExactly("cb-preHook2");
    TemplatingStepPassThroughData ptd2 = extractPtd(r2);
    assertThat(ptd2.getPendingPreHooks()).isEmpty();

    // Step 3: preHook2 done → submits templating
    AsyncChainExecutableResponse r3 = templatingStep.executeNextLinkWithSecurityContext(
        ambiance, stepParams, null, ptd2, () -> Collections.emptyMap());
    assertThat(r3.getChainEnd()).isFalse();
    assertThat(r3.getCallbackIdsList()).containsExactly("cb-templating");
    TemplatingStepPassThroughData ptd3 = extractPtd(r3);
    assertThat(ptd3.getCompletedLink()).isEqualTo(ChainLink.PRE_HOOKS);
    assertThat(ptd3.getPendingPostHooks()).hasSize(2);

    // Step 4: templating done → submits postHook1
    Map<String, ResponseData> templatingResponse = buildVmSuccessResponse("cb-templating");
    AsyncChainExecutableResponse r4 =
        templatingStep.executeNextLinkWithSecurityContext(ambiance, stepParams, null, ptd3, () -> templatingResponse);
    assertThat(r4.getChainEnd()).isFalse();
    assertThat(r4.getCallbackIdsList()).containsExactly("cb-postHook1");
    TemplatingStepPassThroughData ptd4 = extractPtd(r4);
    assertThat(ptd4.getCompletedLink()).isEqualTo(ChainLink.TEMPLATING);
    assertThat(ptd4.getPendingPostHooks()).hasSize(1);

    // Step 5: postHook1 done → submits postHook2
    AsyncChainExecutableResponse r5 = templatingStep.executeNextLinkWithSecurityContext(
        ambiance, stepParams, null, ptd4, () -> Collections.emptyMap());
    assertThat(r5.getChainEnd()).isFalse();
    assertThat(r5.getCallbackIdsList()).containsExactly("cb-postHook2");
    TemplatingStepPassThroughData ptd5 = extractPtd(r5);
    assertThat(ptd5.getPendingPostHooks()).isEmpty();

    // Step 6: postHook2 done → chainEnd
    AsyncChainExecutableResponse r6 = templatingStep.executeNextLinkWithSecurityContext(
        ambiance, stepParams, null, ptd5, () -> Collections.emptyMap());
    assertThat(r6.getChainEnd()).isTrue();

    // Finalize
    StepResponse finalResponse =
        templatingStep.finalizeExecutionWithSecurityContext(ambiance, stepParams, extractPtd(r6), () -> null);
    assertThat(finalResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    // Verify ordering: preHook1, preHook2, postHook1, postHook2 — each submitted exactly once
    InOrder order = inOrder(serviceHookTaskHelper);
    order.verify(serviceHookTaskHelper).submitHookTask(eq(ambiance), hookWithStepId("preHook1"), any(), any(), any());
    order.verify(serviceHookTaskHelper).submitHookTask(eq(ambiance), hookWithStepId("preHook2"), any(), any(), any());
    order.verify(serviceHookTaskHelper).submitHookTask(eq(ambiance), hookWithStepId("postHook1"), any(), any(), any());
    order.verify(serviceHookTaskHelper).submitHookTask(eq(ambiance), hookWithStepId("postHook2"), any(), any(), any());
    verify(serviceHookTaskHelper, times(4)).submitHookTask(any(), any(), any(), any(), any());
  }

  // -----------------------------------------------------------------------
  // Test 3: mid-queue post-hook failure surfaces as InvalidRequestException
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void postHookFailure_vmFailureResponse_throwsInvalidRequestException() {
    Ambiance ambiance = buildAmbiance();
    TemplatingStepParameters stepParams = buildStepParameters();

    TemplatingStepPassThroughData ptd =
        TemplatingStepPassThroughData.builder()
            .completedLink(ChainLink.TEMPLATING)
            .pendingPostHooks(List.of(buildHookMetadata("postHook2"), buildHookMetadata("postHook3")))
            .build();

    VmTaskExecutionResponse failureResponse = VmTaskExecutionResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .errorMessage("hook script exited with code 1")
                                                  .build();
    Map<String, ResponseData> responseDataMap = Map.of("cb-postHook1", failureResponse);

    when(serializedResponseDataHelper.deserialize(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(
        () -> templatingStep.executeNextLinkWithSecurityContext(ambiance, stepParams, null, ptd, () -> responseDataMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("post-template");
  }

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void postHookFailure_k8sNonSuccessStatus_throwsInvalidRequestException() {
    Ambiance ambiance = buildAmbiance();
    TemplatingStepParameters stepParams = buildStepParameters();

    TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                            .completedLink(ChainLink.TEMPLATING)
                                            .pendingPostHooks(List.of(buildHookMetadata("postHook2")))
                                            .build();

    StepStatusTaskResponseData failedStatus =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.FAILURE).build())
            .build();
    Map<String, ResponseData> responseDataMap = Map.of("cb-postHook1", failedStatus);

    when(serializedResponseDataHelper.deserialize(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(
        () -> templatingStep.executeNextLinkWithSecurityContext(ambiance, stepParams, null, ptd, () -> responseDataMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("post-template");
  }

  // -----------------------------------------------------------------------
  // Test 4: pre-hook ErrorNotifyResponseData propagates immediately
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void preHookFailure_errorNotifyResponse_throwsInvalidRequestException() {
    Ambiance ambiance = buildAmbiance();
    TemplatingStepParameters stepParams = buildStepParameters();

    TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                            .completedLink(null)
                                            .pendingPreHooks(List.of(buildHookMetadata("preHook2")))
                                            .pendingPostHooks(new ArrayList<>())
                                            .build();

    ErrorNotifyResponseData errorData = ErrorNotifyResponseData.builder().errorMessage("infra error").build();
    Map<String, ResponseData> responseDataMap = Map.of("cb-preHook1", errorData);

    when(serializedResponseDataHelper.deserialize(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(
        () -> templatingStep.executeNextLinkWithSecurityContext(ambiance, stepParams, null, ptd, () -> responseDataMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("pre-template");
  }

  // -----------------------------------------------------------------------
  // Test 5: no pre-hooks, templating skipped, 2 post-hooks → first submitted immediately
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void startChainLink_noPreHooks_templatingSkipped_firstPostHookSubmittedImmediately() {
    Ambiance ambiance = buildAmbiance();

    ServiceHooksSweepingOutput postHooksOutput = buildHooksOutput(List.of("postHook1", "postHook2"));

    when(serviceHookTaskHelper.isServiceHooksEnabled(ambiance)).thenReturn(true);
    when(serviceStepSweepingOutputHelper.fetchPreTemplateHooksSweepingOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchPostTemplateHooksSweepingOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(postHooksOutput).build());
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    // willRunTemplating → false (empty overrides)
    when(cdStepsExpressionResolver.renderValue(
             any(), eq(TemplatingStep.SERVICE_OUTPUT_MANIFESTS_PRIMARY_TYPE_EXP), eq(true)))
        .thenReturn("k8s");
    when(cdStepsExpressionResolver.renderValue(
             any(), eq(TemplatingStep.SERVICE_OUTPUT_FILES_TO_TEMPLATIZED_EXP), eq(true)))
        .thenReturn("");
    when(serviceHookTaskHelper.submitHookTask(any(), any(), any(), any(), any())).thenReturn("cb-postHook1");

    AsyncChainExecutableResponse response =
        templatingStep.startChainLinkAfterRbac(ambiance, buildStepParameters(), null);

    assertThat(response.getChainEnd()).isFalse();
    assertThat(response.getCallbackIdsList()).containsExactly("cb-postHook1");

    TemplatingStepPassThroughData ptd = extractPtd(response);
    assertThat(ptd.getCompletedLink()).isEqualTo(ChainLink.TEMPLATING);
    assertThat(ptd.isTemplatingSkipped()).isTrue();
    assertThat(ptd.getPendingPostHooks()).hasSize(1);

    verify(serviceHookTaskHelper, times(1))
        .submitHookTask(eq(ambiance), hookWithStepId("postHook1"), any(), any(), any());
    verify(runnerSubmitTaskUtils, never()).submitTaskByTemplate(any(), any(), any(), any(), any(), any());
  }

  // -----------------------------------------------------------------------
  // Test 6: finalizeExecution with output vars produces correct outcome
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void finalizeExecution_withOutputVars_returnsSucceededWithOutcome() throws Exception {
    Ambiance ambiance = buildAmbiance();

    TemplatingStepPassThroughData ptd = TemplatingStepPassThroughData.builder()
                                            .completedLink(ChainLink.POST_HOOKS)
                                            .outputVars(Map.of("myKey", "myValue"))
                                            .build();

    StepResponse response =
        templatingStep.finalizeExecutionWithSecurityContext(ambiance, buildStepParameters(), ptd, () -> null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome outcome = response.getStepOutcomes().iterator().next();
    assertThat(outcome.getName()).isEqualTo("output");
    assertThat(((Map<?, ?>) outcome.getOutcome()).get("myKey")).isEqualTo("myValue");
  }

  // -----------------------------------------------------------------------
  // Test 7: willRunTemplating returns false for HELM service type → chainEnd immediately
  // -----------------------------------------------------------------------

  @Test
  @Owner(developers = LOKESH_BIHANI)
  @Category(UnitTests.class)
  public void startChainLink_helmServiceType_templatingSkipped_noHooks_chainEndImmediately() {
    Ambiance ambiance = buildAmbiance();

    UnifiedServiceOutcome helmOutcome = UnifiedServiceOutcome.builder().type("helm").build();

    when(serviceHookTaskHelper.isServiceHooksEnabled(ambiance)).thenReturn(false);
    when(serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(helmOutcome).build());

    AsyncChainExecutableResponse response =
        templatingStep.startChainLinkAfterRbac(ambiance, buildStepParameters(), null);

    assertThat(response.getChainEnd()).isTrue();
    assertThat(response.getCallbackIdsList()).isEmpty();
  }

  // -----------------------------------------------------------------------
  // Helper methods
  // -----------------------------------------------------------------------

  private Map<String, ResponseData> buildVmSuccessResponse(String callbackId) {
    VmTaskExecutionResponse successResponse = VmTaskExecutionResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .outputVars(new HashMap<>())
                                                  .build();
    Map<String, ResponseData> map = new HashMap<>();
    map.put(callbackId, successResponse);
    return map;
  }

  private ServiceHookMetadata hookWithStepId(String stepId) {
    return org.mockito.ArgumentMatchers.argThat(hook -> hook != null && stepId.equals(hook.getStepId()));
  }
}
