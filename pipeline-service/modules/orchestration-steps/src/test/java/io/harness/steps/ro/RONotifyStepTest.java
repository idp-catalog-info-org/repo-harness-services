/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import static io.harness.rule.OwnerRule.SHASHANK_JAIN;
import static io.harness.rule.OwnerRule.ZANINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class RONotifyStepTest extends CategoryTest {
  @InjectMocks private RONotifyStep roNotifyStep;

  @Mock private ReleaseManagementClient releaseManagementClient;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ArtifactsResolver artifactsResolver;
  @Mock private io.harness.expression.EngineExpressionService engineExpressionService;

  private Ambiance ambiance;

  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrgId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String PIPELINE_ID = "testPipelineId";
  private static final String EXECUTION_ID = "testExecutionId";
  private static final String EVENT_TYPE = "PIPELINE_STEP_WEBHOOK";
  private static final String CUSTOM_EVENT = "CUSTOM_EVENT_FROM_USER";

  @Before
  public void setup() throws Exception {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", ORG_ID)
            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
            .setPlanExecutionId(EXECUTION_ID)
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
            .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder().setRuntimeId("default-runtime-id").build())
            .build();

    java.lang.reflect.Field eventTypeField = RONotifyStep.class.getDeclaredField("eventType");
    eventTypeField.setAccessible(true);
    eventTypeField.set(roNotifyStep, EVENT_TYPE);

    // Default mock: resolver returns empty artifacts for existing tests
    when(artifactsResolver.resolve(any(), any()))
        .thenReturn(new ArtifactsResolver.ResolvedArtifacts(
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList()));
    when(artifactsResolver.getMaxItemsPerType()).thenReturn(ArtifactsResolver.DEFAULT_MAX_ITEMS_PER_TYPE);
  }

  private Call<ResponseDTO<Void>> mockSuccessCall() throws Exception {
    Call<ResponseDTO<Void>> call = mock(Call.class);
    Call<ResponseDTO<Void>> clonedCall = mock(Call.class);
    Response<ResponseDTO<Void>> response = Response.success(ResponseDTO.newResponse(null));
    when(call.clone()).thenReturn(clonedCall);
    when(clonedCall.execute()).thenReturn(response);
    return call;
  }

  private Call<ResponseDTO<Void>> mockFailureCall(Exception exception) throws Exception {
    Call<ResponseDTO<Void>> call = mock(Call.class);
    Call<ResponseDTO<Void>> clonedCall = mock(Call.class);
    when(call.clone()).thenReturn(clonedCall);
    when(clonedCall.execute()).thenThrow(exception);
    return call;
  }

  private StepBaseParameters paramsWithEmptyMetadata() {
    RONotifyStepParameters spec =
        RONotifyStepParameters.builder()
            .metadata(RONotifyMetadata.builder().values(java.util.Collections.emptyList()).build())
            .build();
    StepBaseParameters wrap = mock(StepBaseParameters.class);
    doReturn(spec).when(wrap).getSpec();
    return wrap;
  }

  private void setEventType(String value) throws Exception {
    java.lang.reflect.Field f = RONotifyStep.class.getDeclaredField("eventType");
    f.setAccessible(true);
    f.set(roNotifyStep, value);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExecuteSyncSuccess() throws Exception {
    RONotifyStepParameters params =
        RONotifyStepParameters.builder()
            .metadata(RONotifyMetadata.builder()
                          .values(Arrays.asList(RONotifyKeyValuePair.builder().key("version").value("1.0.0").build()))
                          .build())
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    doReturn(params).when(stepBaseParameters).getSpec();

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse stepResponse =
        roNotifyStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RONotifyRequestBody> bodyCaptor = ArgumentCaptor.forClass(RONotifyRequestBody.class);
    verify(releaseManagementClient).notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), bodyCaptor.capture());

    RONotifyRequestBody capturedBody = bodyCaptor.getValue();
    assertThat(capturedBody.getEventType()).isEqualTo(EVENT_TYPE);
    assertThat(capturedBody.getPipeline().getOrgId()).isEqualTo(ORG_ID);
    assertThat(capturedBody.getPipeline().getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(capturedBody.getPipeline().getIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(capturedBody.getPipeline().getExecutionId()).isEqualTo(EXECUTION_ID);
    assertThat(capturedBody.getMetadata()).containsEntry("version", "1.0.0");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExecuteSyncFailureOnException() throws Exception {
    RONotifyStepParameters params = RONotifyStepParameters.builder().build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    doReturn(params).when(stepBaseParameters).getSpec();

    Call<ResponseDTO<Void>> call = mockFailureCall(new java.io.IOException("connection refused"));
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse stepResponse =
        roNotifyStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithNullMetadata() throws Exception {
    RONotifyStepParameters params = RONotifyStepParameters.builder().metadata(null).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    doReturn(params).when(stepBaseParameters).getSpec();

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse stepResponse =
        roNotifyStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RONotifyRequestBody> bodyCaptor = ArgumentCaptor.forClass(RONotifyRequestBody.class);
    verify(releaseManagementClient).notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), bodyCaptor.capture());
    assertThat(bodyCaptor.getValue().getMetadata()).isEmpty();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithMultipleMetadataEntries() throws Exception {
    RONotifyStepParameters params =
        RONotifyStepParameters.builder()
            .metadata(RONotifyMetadata.builder()
                          .values(Arrays.asList(RONotifyKeyValuePair.builder().key("env").value("prod").build(),
                              RONotifyKeyValuePair.builder().key("region").value("us-east-1").build()))
                          .build())
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    doReturn(params).when(stepBaseParameters).getSpec();

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse stepResponse =
        roNotifyStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RONotifyRequestBody> bodyCaptor = ArgumentCaptor.forClass(RONotifyRequestBody.class);
    verify(releaseManagementClient).notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), bodyCaptor.capture());
    assertThat(bodyCaptor.getValue().getMetadata()).hasSize(2);
    assertThat(bodyCaptor.getValue().getMetadata()).containsEntry("env", "prod");
    assertThat(bodyCaptor.getValue().getMetadata()).containsEntry("region", "us-east-1");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void executeSyncAttachesResolvedArtifactsAndPreservesEventType() throws Exception {
    setEventType(CUSTOM_EVENT);

    java.util.List<java.util.Map<String, Object>> images =
        java.util.Collections.singletonList(java.util.Collections.singletonMap("imageName", "gcr.io/acme/api"));
    when(artifactsResolver.resolve(any(), any()))
        .thenReturn(new ArtifactsResolver.ResolvedArtifacts(
            images, java.util.Collections.emptyList(), java.util.Collections.emptyList()));

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse resp = roNotifyStep.executeSyncAfterRbac(
        ambiance, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);

    assertThat(resp.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RONotifyRequestBody> captor = ArgumentCaptor.forClass(RONotifyRequestBody.class);
    verify(releaseManagementClient).notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), captor.capture());
    RONotifyRequestBody sent = captor.getValue();
    // The step preserves the customer-configured eventType (RM additively
    // dispatches the artifact-tracker handler when artifacts are present).
    assertThat(sent.getEventType()).isEqualTo(CUSTOM_EVENT);
    assertThat(sent.getMetadata()).doesNotContainKey("originalEventType");
    assertThat(sent.getArtifacts()).isNotNull();
    assertThat(sent.getArtifacts().getImages()).hasSize(1);
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void executeSyncPassThroughWhenResolverEmpty() throws Exception {
    setEventType(CUSTOM_EVENT);

    when(artifactsResolver.resolve(any(), any()))
        .thenReturn(new ArtifactsResolver.ResolvedArtifacts(
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList()));

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), any())).thenReturn(call);

    StepResponse resp = roNotifyStep.executeSyncAfterRbac(
        ambiance, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);

    assertThat(resp.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<RONotifyRequestBody> captor = ArgumentCaptor.forClass(RONotifyRequestBody.class);
    verify(releaseManagementClient).notifyReleaseOrchestration(eq(ACCOUNT_ID), any(), captor.capture());
    RONotifyRequestBody sent = captor.getValue();
    assertThat(sent.getEventType()).isEqualTo(CUSTOM_EVENT);
    assertThat(sent.getMetadata()).doesNotContainKey("originalEventType");
    assertThat(sent.getArtifacts()).isNull();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void executeSyncTwoCoResidentStepsProduceDistinctIdempotencyKeys() throws Exception {
    when(artifactsResolver.resolve(any(), any()))
        .thenReturn(new ArtifactsResolver.ResolvedArtifacts(
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList()));

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), keyCaptor.capture(), any()))
        .thenReturn(call);

    // Same plan execution id, two different setupIds — simulating two
    // distinct RO Notify steps in the same plan.
    Ambiance step1 = ambiance.toBuilder()
                         .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder().setSetupId("setup-1").build())
                         .build();
    Ambiance step2 = ambiance.toBuilder()
                         .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder().setSetupId("setup-2").build())
                         .build();

    roNotifyStep.executeSyncAfterRbac(step1, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);
    roNotifyStep.executeSyncAfterRbac(step2, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);

    java.util.List<String> keys = keyCaptor.getAllValues();
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    assertThat(keys.get(0)).startsWith(EXECUTION_ID + ":");
    assertThat(keys.get(1)).startsWith(EXECUTION_ID + ":");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void executeSyncRetryProducesSameIdempotencyKey() throws Exception {
    // Engine retry of the same plan node: setupId is stable, runtimeId is
    // freshly allocated. The idempotency key MUST stay the same so RM
    // dedupes the retry instead of double-ingesting the artifact.
    when(artifactsResolver.resolve(any(), any()))
        .thenReturn(new ArtifactsResolver.ResolvedArtifacts(
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList()));

    Call<ResponseDTO<Void>> call = mockSuccessCall();
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    when(releaseManagementClient.notifyReleaseOrchestration(eq(ACCOUNT_ID), keyCaptor.capture(), any()))
        .thenReturn(call);

    String stableSetupId = "setup-shared";
    Ambiance attempt1 = ambiance.toBuilder()
                            .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                           .setSetupId(stableSetupId)
                                           .setRuntimeId("runtime-attempt-1")
                                           .build())
                            .build();
    Ambiance attempt2 = ambiance.toBuilder()
                            .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                           .setSetupId(stableSetupId)
                                           .setRuntimeId("runtime-attempt-2")
                                           .build())
                            .build();

    roNotifyStep.executeSyncAfterRbac(attempt1, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);
    roNotifyStep.executeSyncAfterRbac(attempt2, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);

    java.util.List<String> keys = keyCaptor.getAllValues();
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void executeSyncFailsTheStepWhenResolverReportsInfraFailure() throws Exception {
    // ArtifactsResolver throws OutcomeException when its bulk Mongo lookups
    // fail. The step MUST fail rather than ship a notify-without-artifacts,
    // because a successful notify would burn the idempotency key and lock
    // the customer out of recovery.
    when(artifactsResolver.resolve(any(), any()))
        .thenThrow(new io.harness.engine.pms.data.OutcomeException("mongo timeout", new RuntimeException("boom")));

    StepResponse resp = roNotifyStep.executeSyncAfterRbac(
        ambiance, paramsWithEmptyMetadata(), StepInputPackage.builder().build(), null);

    assertThat(resp.getStatus()).isEqualTo(Status.FAILED);
    // No HTTP call should have been issued — the failure happened before the POST.
    verify(releaseManagementClient, org.mockito.Mockito.never()).notifyReleaseOrchestration(any(), any(), any());
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void renderExpressionOrBlank_returnsEmptyForNullEngineResult() {
    when(engineExpressionService.renderExpression(any(), eq("<+codebase.commitSha>"), eq(true))).thenReturn(null);
    assertThat(roNotifyStep.renderExpressionOrBlank(ambiance, "<+codebase.commitSha>")).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void renderExpressionOrBlank_returnsEmptyForLiteralNullString() {
    when(engineExpressionService.renderExpression(any(), eq("<+codebase.branch>"), eq(true))).thenReturn("null");
    assertThat(roNotifyStep.renderExpressionOrBlank(ambiance, "<+codebase.branch>")).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void renderExpressionOrBlank_returnsEmptyWhenEngineEchoesTheExpression() {
    // Engine returns the input expression verbatim when no functor matches.
    when(engineExpressionService.renderExpression(any(), eq("<+codebase.repoUrl>"), eq(true)))
        .thenReturn("<+codebase.repoUrl>");
    assertThat(roNotifyStep.renderExpressionOrBlank(ambiance, "<+codebase.repoUrl>")).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void renderExpressionOrBlank_passesThroughLegitimateValueContainingPlusBracket() {
    // A real rendered value that happens to contain "<+" must NOT be silenced.
    // The previous startsWith("<+") heuristic would have dropped this — the
    // exact-match check passes it through.
    String value = "fix: handle <+something+> tokens";
    when(engineExpressionService.renderExpression(any(), eq("<+codebase.commitSha>"), eq(true))).thenReturn(value);
    assertThat(roNotifyStep.renderExpressionOrBlank(ambiance, "<+codebase.commitSha>")).isEqualTo(value);
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void renderExpressionOrBlank_swallowsExceptionsAsEmpty() {
    when(engineExpressionService.renderExpression(any(), eq("<+codebase.branch>"), eq(true)))
        .thenThrow(new RuntimeException("engine boom"));
    assertThat(roNotifyStep.renderExpressionOrBlank(ambiance, "<+codebase.branch>")).isEmpty();
  }
}
