/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.FmeResponse;
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
import io.harness.steps.StepSpecTypeConstants;

import java.util.List;
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
public class FmeFlagSetImpressionTrackingStepTest extends CategoryTest {
  @InjectMocks FmeFlagSetImpressionTrackingStep step;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String FLAG_NAME = "testFlag";
  private static final String ENVIRONMENT = "production";

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
  public void testGetStepType() {
    assertThat(FmeFlagSetImpressionTrackingStep.STEP_TYPE)
        .isEqualTo(StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSuccess() throws Exception {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    FeatureFlagDefinition definition = FeatureFlagDefinition.builder().build();
    FmeResponse<FeatureFlagDefinition> fmeResponse =
        FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build();
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse = Response.success(fmeResponse);
    when(mockCall.execute()).thenReturn(mockResponse);

    ArgumentCaptor<List<FmePatchOperation>> patchCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), patchCaptor.capture()))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());

    List<FmePatchOperation> capturedPatch = patchCaptor.getValue();
    assertThat(capturedPatch).hasSize(1);
    assertThat(capturedPatch.get(0).getOp()).isEqualTo("replace");
    assertThat(capturedPatch.get(0).getPath()).isEqualTo("/impressionsDisabled");
    assertThat(capturedPatch.get(0).getValue()).isEqualTo(false);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteWithDisabledFlag() throws Exception {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(false))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    FeatureFlagDefinition definition = FeatureFlagDefinition.builder().build();
    FmeResponse<FeatureFlagDefinition> fmeResponse =
        FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build();
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse = Response.success(fmeResponse);
    when(mockCall.execute()).thenReturn(mockResponse);

    ArgumentCaptor<List<FmePatchOperation>> patchCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), patchCaptor.capture()))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<FmePatchOperation> capturedPatch = patchCaptor.getValue();
    assertThat(capturedPatch).hasSize(1);
    assertThat(capturedPatch.get(0).getPath()).isEqualTo("/impressionsDisabled");
    assertThat(capturedPatch.get(0).getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteApiFailure() throws Exception {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new RuntimeException("API call failed"));
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecute404Response() throws Exception {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse =
        Response.error(404, ResponseBody.create(MediaType.parse("application/json"), "Not found"));
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteWithMissingFlagName() {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(null))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteWithMissingEnvironment() {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(null))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteWithMissingEnabled() {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(null))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteApiErrorResponse() throws Exception {
    FmeFlagSetImpressionTrackingParameters params = FmeFlagSetImpressionTrackingParameters.builder()
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .enabled(ParameterField.createValueField(true))
                                                        .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse =
        Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"server error\"}"));
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }
}
