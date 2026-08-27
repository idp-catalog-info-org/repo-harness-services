/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aisre.AiSrePipelineClient;
import io.harness.aisre.AlertResponse;
import io.harness.aisre.CreateAlertRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.telemetry.helpers.StepsInstrumentationHelper;

import java.io.IOException;
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
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CHAOS)
@RunWith(MockitoJUnitRunner.class)
public class AisreCreateAlertStepTest extends CategoryTest {
  @InjectMocks AisreCreateAlertStep step;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private AiSrePipelineClient aiSrePipelineClient;
  @Mock private AisreStepResponseBuilder aisreStepResponseBuilder;
  @Mock private StepsInstrumentationHelper stepsInstrumentationHelper;
  @Mock private AisrePipelineContextFormatter aisrePipelineContextFormatter;

  private Ambiance ambiance;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Before
  public void setup() {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                   .build();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertSuccessPublishesOutcome() throws Exception {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .title(ParameterField.createValueField("Deploy started"))
                                                .status(ParameterField.createValueField("triggered"))
                                                .priority(ParameterField.createValueField("p3_warning"))
                                                .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-7");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(CreateAlertRequest.class)))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    Outcome outcome = response.getStepOutcomes().iterator().next().getOutcome();
    assertThat(outcome).isInstanceOf(AisreCreateAlertOutcome.class);
    assertThat(((AisreCreateAlertOutcome) outcome).getAlertId()).isEqualTo("ALERT-7");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertHonorsProvidedOrgIdentifier() throws Exception {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .title(ParameterField.createValueField("Deploy started"))
                                                .orgIdentifier(ParameterField.createValueField("targetOrg"))
                                                .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-8");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(
        aiSrePipelineClient.createAlert(eq(ACCOUNT_ID), eq("targetOrg"), eq(PROJECT_ID), any(CreateAlertRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    verify(aiSrePipelineClient, times(1))
        .createAlert(eq(ACCOUNT_ID), eq("targetOrg"), eq(PROJECT_ID), any(CreateAlertRequest.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertHonorsProvidedOrgAndProjectIdentifier() throws Exception {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .title(ParameterField.createValueField("Deploy started"))
                                                .orgIdentifier(ParameterField.createValueField("targetOrg"))
                                                .projectIdentifier(ParameterField.createValueField("targetProject"))
                                                .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-9");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(
             eq(ACCOUNT_ID), eq("targetOrg"), eq("targetProject"), any(CreateAlertRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    verify(aiSrePipelineClient, times(1))
        .createAlert(eq(ACCOUNT_ID), eq("targetOrg"), eq("targetProject"), any(CreateAlertRequest.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertPassesStatusInRequest() throws Exception {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .title(ParameterField.createValueField("Deploy resolved"))
                                                .status(ParameterField.createValueField("resolved"))
                                                .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-7");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateAlertRequest> captor = ArgumentCaptor.forClass(CreateAlertRequest.class);
    verify(aiSrePipelineClient, times(1)).createAlert(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("resolved");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertPassesExecutionUrlInRequest() throws Exception {
    AisreCreateAlertStepParameters params =
        AisreCreateAlertStepParameters.builder().title(ParameterField.createValueField("Deploy failed")).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);
    when(aisrePipelineContextFormatter.resolveExecutionUrl(ambiance)).thenReturn("https://app.harness.io/exec/1");

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-7");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateAlertRequest> captor = ArgumentCaptor.forClass(CreateAlertRequest.class);
    verify(aiSrePipelineClient, times(1)).createAlert(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPipelineUrl()).isEqualTo("https://app.harness.io/exec/1");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testCreateAlertOmitsExecutionUrlWhenUnavailable() throws Exception {
    AisreCreateAlertStepParameters params =
        AisreCreateAlertStepParameters.builder().title(ParameterField.createValueField("Deploy failed")).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-7");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // An unresolvable URL must not cost us the alert: the field is simply omitted.
    ArgumentCaptor<CreateAlertRequest> captor = ArgumentCaptor.forClass(CreateAlertRequest.class);
    verify(aiSrePipelineClient, times(1)).createAlert(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPipelineUrl()).isNull();
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testUpdateAlertPassesAlertIdInRequest() throws Exception {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .alertId(ParameterField.createValueField("ALERT-7"))
                                                .status(ParameterField.createValueField("resolved"))
                                                .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    AlertResponse body = new AlertResponse();
    body.setPrettyId("ALERT-7");
    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateAlertRequest> captor = ArgumentCaptor.forClass(CreateAlertRequest.class);
    verify(aiSrePipelineClient, times(1)).createAlert(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getAlertId()).isEqualTo("ALERT-7");
    assertThat(captor.getValue().getStatus()).isEqualTo("resolved");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testAlertFailureIsNonFatal() throws Exception {
    AisreCreateAlertStepParameters params =
        AisreCreateAlertStepParameters.builder().title(ParameterField.createValueField("Alert")).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<AlertResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new IOException("AI SRE unreachable"));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Alerting must not fail the pipeline; IGNORE_FAILED surfaces a UI warning.
    assertThat(response.getStatus()).isEqualTo(Status.IGNORE_FAILED);
    assertThat(response.getStepOutcomes()).isEmpty();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testAlertApiErrorIsNonFatalAndSurfacesMessage() throws Exception {
    AisreCreateAlertStepParameters params =
        AisreCreateAlertStepParameters.builder().title(ParameterField.createValueField("Alert")).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<AlertResponse> mockCall = mock(Call.class);
    ResponseBody errorBody = ResponseBody.create(MediaType.parse("application/json"), "title is required");
    when(mockCall.execute()).thenReturn(Response.error(400, errorBody));
    when(aiSrePipelineClient.createAlert(any(), any(), any(), any(CreateAlertRequest.class))).thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.IGNORE_FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("title is required");
    assertThat(response.getStepOutcomes()).isEmpty();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testMissingTitleFailsTheStep() {
    AisreCreateAlertStepParameters params =
        AisreCreateAlertStepParameters.builder().title(ParameterField.createValueField("")).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("title");
    verify(aiSrePipelineClient, times(0)).createAlert(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTOOnSuccess() {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    StepResponse stepResponse =
        StepResponse.builder()
            .status(Status.SUCCEEDED)
            .stepOutcome(StepResponse.StepOutcome.builder()
                             .name(AisreCreateAlertStep.OUTPUT)
                             .outcome(AisreCreateAlertOutcome.builder().alertId("ALERT-1").build())
                             .build())
            .build();

    StepExecutionTelemetryEventDTO telemetryEventDTO =
        step.getStepExecutionTelemetryEventDTO(ambiance, stepBaseParameters, stepResponse);

    assertThat(telemetryEventDTO.getStepType()).isEqualTo(AisreCreateAlertStep.STEP_TYPE.getType());
    assertThat(telemetryEventDTO.getProperties().get(AisreBaseStep.TELEMETRY_STATUS)).isEqualTo("SUCCEEDED");
    assertThat(telemetryEventDTO.getProperties().get(AisreBaseStep.TELEMETRY_API_SUCCESS)).isEqualTo(true);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTOOnNonFatalFailure() {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();

    StepExecutionTelemetryEventDTO telemetryEventDTO =
        step.getStepExecutionTelemetryEventDTO(ambiance, stepBaseParameters, stepResponse);

    assertThat(telemetryEventDTO.getProperties().get(AisreBaseStep.TELEMETRY_STATUS)).isEqualTo("SUCCEEDED");
    assertThat(telemetryEventDTO.getProperties().get(AisreBaseStep.TELEMETRY_API_SUCCESS)).isEqualTo(false);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTOMarksUpdate() {
    AisreCreateAlertStepParameters params = AisreCreateAlertStepParameters.builder()
                                                .alertId(ParameterField.createValueField("ALERT-7"))
                                                .status(ParameterField.createValueField("resolved"))
                                                .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepExecutionTelemetryEventDTO telemetryEventDTO = step.getStepExecutionTelemetryEventDTO(
        ambiance, stepBaseParameters, StepResponse.builder().status(Status.SUCCEEDED).build());

    assertThat(telemetryEventDTO.getProperties().get("is_update")).isEqualTo(true);
  }
}
