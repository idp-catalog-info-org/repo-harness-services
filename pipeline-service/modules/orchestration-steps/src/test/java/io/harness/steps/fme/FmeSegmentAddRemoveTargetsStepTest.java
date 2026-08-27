/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.SegmentKeysDTO;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeSegmentAddRemoveTargetsStepTest extends CategoryTest {
  @InjectMocks FmeSegmentAddRemoveTargetsStep step;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Before
  public void setup() {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .putSetupAbstractions("orgIdentifier", ORG_ID)
            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithBothKeys() throws Exception {
    FmeSegmentAddRemoveTargetsParameters params =
        FmeSegmentAddRemoveTargetsParameters.builder()
            .segmentName(ParameterField.createValueField("premium-users"))
            .environment(ParameterField.createValueField("env-123"))
            .addKeys(ParameterField.createValueField(Arrays.asList("k1", "k2")))
            .removeKeys(ParameterField.createValueField(Arrays.asList("k3")))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Void> updateCall = mock(Call.class);
    Response<Void> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<SegmentKeysDTO> payloadCaptor = ArgumentCaptor.forClass(SegmentKeysDTO.class);
    when(fmePipelineClient.updateSegmentKeys(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    SegmentKeysDTO captured = payloadCaptor.getValue();
    assertThat(captured.getSegmentName()).isEqualTo("premium-users");
    assertThat(captured.getEnvironment()).isEqualTo("env-123");
    assertThat(captured.getOrgId()).isNull();
    assertThat(captured.getAddKeys()).containsExactly("k1", "k2");
    assertThat(captured.getRemoveKeys()).containsExactly("k3");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithOnlyAddKeys() throws Exception {
    FmeSegmentAddRemoveTargetsParameters params =
        FmeSegmentAddRemoveTargetsParameters.builder()
            .segmentName(ParameterField.createValueField("premium-users"))
            .environment(ParameterField.createValueField("env-123"))
            .addKeys(ParameterField.createValueField(Arrays.asList("k1", "k2")))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Void> updateCall = mock(Call.class);
    Response<Void> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<SegmentKeysDTO> payloadCaptor = ArgumentCaptor.forClass(SegmentKeysDTO.class);
    when(fmePipelineClient.updateSegmentKeys(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    SegmentKeysDTO captured = payloadCaptor.getValue();
    assertThat(captured.getAddKeys()).containsExactly("k1", "k2");
    assertThat(captured.getRemoveKeys()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccessWithOnlyRemoveKeys() throws Exception {
    FmeSegmentAddRemoveTargetsParameters params =
        FmeSegmentAddRemoveTargetsParameters.builder()
            .segmentName(ParameterField.createValueField("premium-users"))
            .environment(ParameterField.createValueField("env-123"))
            .removeKeys(ParameterField.createValueField(Arrays.asList("k3", "k4")))
            .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Void> updateCall = mock(Call.class);
    Response<Void> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);

    ArgumentCaptor<SegmentKeysDTO> payloadCaptor = ArgumentCaptor.forClass(SegmentKeysDTO.class);
    when(fmePipelineClient.updateSegmentKeys(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), payloadCaptor.capture()))
        .thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    SegmentKeysDTO captured = payloadCaptor.getValue();
    assertThat(captured.getAddKeys()).isNull();
    assertThat(captured.getRemoveKeys()).containsExactly("k3", "k4");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteMissingSegmentName() {
    FmeSegmentAddRemoveTargetsParameters params = FmeSegmentAddRemoveTargetsParameters.builder()
                                                      .segmentName(ParameterField.createValueField(null))
                                                      .environment(ParameterField.createValueField("env-123"))
                                                      .addKeys(ParameterField.createValueField(Arrays.asList("k1")))
                                                      .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteMissingEnvironment() {
    FmeSegmentAddRemoveTargetsParameters params = FmeSegmentAddRemoveTargetsParameters.builder()
                                                      .segmentName(ParameterField.createValueField("seg"))
                                                      .environment(ParameterField.createValueField(null))
                                                      .addKeys(ParameterField.createValueField(Arrays.asList("k1")))
                                                      .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteNoKeysProvided() {
    FmeSegmentAddRemoveTargetsParameters params = FmeSegmentAddRemoveTargetsParameters.builder()
                                                      .segmentName(ParameterField.createValueField("seg"))
                                                      .environment(ParameterField.createValueField("env"))
                                                      .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteApiFailure() throws Exception {
    FmeSegmentAddRemoveTargetsParameters params = FmeSegmentAddRemoveTargetsParameters.builder()
                                                      .segmentName(ParameterField.createValueField("seg"))
                                                      .environment(ParameterField.createValueField("env"))
                                                      .addKeys(ParameterField.createValueField(Arrays.asList("k1")))
                                                      .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Void> updateCall = mock(Call.class);
    Response<Void> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(false);
    when(updateResponse.code()).thenReturn(500);
    when(updateResponse.errorBody())
        .thenReturn(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"Internal server error\"}"));

    when(fmePipelineClient.updateSegmentKeys(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any())).thenReturn(updateCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeSegmentAddRemoveTargetsStep.STEP_TYPE).isNotNull();
  }
}
