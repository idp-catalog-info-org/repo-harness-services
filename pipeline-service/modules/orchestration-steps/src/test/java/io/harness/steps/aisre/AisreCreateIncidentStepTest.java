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
import io.harness.aisre.CreateIncidentRequest;
import io.harness.aisre.IncidentResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
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
import java.util.List;
import java.util.Map;
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
public class AisreCreateIncidentStepTest extends CategoryTest {
  @InjectMocks AisreCreateIncidentStep step;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private AiSrePipelineClient aiSrePipelineClient;
  @Mock private AisreStepResponseBuilder aisreStepResponseBuilder;
  @Mock private AisrePipelineContextFormatter aisrePipelineContextFormatter;
  @Mock private StepsInstrumentationHelper stepsInstrumentationHelper;

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
  public void testExecuteSuccessPublishesOutcome() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-123");
    IncidentResponse.CommsLink web = new IncidentResponse.CommsLink();
    web.setLinkType("WEB");
    web.setUrl("https://app.harness.io/ir/tp/INC-123");
    body.setCommsLinks(List.of(web));

    Call<IncidentResponse> mockCall = mock(Call.class);
    Response<IncidentResponse> mockResponse = Response.success(body);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(aiSrePipelineClient.createIncident(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    Outcome outcome = response.getStepOutcomes().iterator().next().getOutcome();
    assertThat(outcome).isInstanceOf(AisreCreateIncidentOutcome.class);
    AisreCreateIncidentOutcome incidentOutcome = (AisreCreateIncidentOutcome) outcome;
    assertThat(incidentOutcome.getIncidentId()).isEqualTo("INC-123");
    assertThat(incidentOutcome.getIncidentUrl()).isEqualTo("https://app.harness.io/ir/tp/INC-123");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteDefaultsIncidentTypeToInc() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("No type provided"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-9");

    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient, times(1)).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getTemplateShortId()).isEqualTo("INC");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteHonorsProvidedIncidentType() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Custom type"))
                                                   .severity(ParameterField.createValueField("SEV2"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .incidentType(ParameterField.createValueField("SEV"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("SEV-1");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getTemplateShortId()).isEqualTo("SEV");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteHonorsProvidedProjectIdentifier() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Cross-project incident"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .projectIdentifier(ParameterField.createValueField("targetProject"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-42");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(
             eq(ACCOUNT_ID), eq(ORG_ID), eq("targetProject"), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    verify(aiSrePipelineClient, times(1))
        .createIncident(eq(ACCOUNT_ID), eq(ORG_ID), eq("targetProject"), any(CreateIncidentRequest.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteHonorsProvidedOrgIdentifier() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Cross-org incident"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .orgIdentifier(ParameterField.createValueField("targetOrg"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-43");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(
             eq(ACCOUNT_ID), eq("targetOrg"), eq(PROJECT_ID), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    verify(aiSrePipelineClient, times(1))
        .createIncident(eq(ACCOUNT_ID), eq("targetOrg"), eq(PROJECT_ID), any(CreateIncidentRequest.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteHonorsProvidedOrgAndProjectIdentifier() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Cross-scope incident"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .orgIdentifier(ParameterField.createValueField("targetOrg"))
                                                   .projectIdentifier(ParameterField.createValueField("targetProject"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-44");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(
             eq(ACCOUNT_ID), eq("targetOrg"), eq("targetProject"), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    verify(aiSrePipelineClient, times(1))
        .createIncident(eq(ACCOUNT_ID), eq("targetOrg"), eq("targetProject"), any(CreateIncidentRequest.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testMissingTitleFailsTheStep() {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder().build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("title");
    verify(aiSrePipelineClient, times(0)).createIncident(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testMissingSeverityFailsTheStep() {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("severity");
    verify(aiSrePipelineClient, times(0)).createIncident(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecutePassesLabelsInRequest() throws Exception {
    AisreCreateIncidentStepParameters params =
        AisreCreateIncidentStepParameters.builder()
            .title(ParameterField.createValueField("Deploy failed"))
            .severity(ParameterField.createValueField("SEV1"))
            .service(ParameterField.createValueField("checkout"))
            .labels(ParameterField.createValueField(List.of("team", "checkout", "source", "pipeline")))
            .attachPipelineContext(ParameterField.createValueField(false))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-77");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getLabels()).containsExactly("team", "checkout", "source", "pipeline");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteDefaultsPageOnCallToTrue() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-88");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPageOnCall()).isTrue();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteMapsAssignedRespondersToOutcome() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .pageOnCall(ParameterField.createValueField(true))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-99");
    IncidentResponse.OncallUser responder = new IncidentResponse.OncallUser();
    responder.setUserId("user-1");
    responder.setDisplayName("On Call");
    responder.setEmail("oncall@harness.io");
    body.setAssignedResponders(List.of(responder));

    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPageOnCall()).isTrue();

    AisreCreateIncidentOutcome incidentOutcome =
        (AisreCreateIncidentOutcome) response.getStepOutcomes().iterator().next().getOutcome();
    assertThat(incidentOutcome.getAssignedResponders()).hasSize(1);
    assertThat(incidentOutcome.getAssignedResponders().get(0).getUserId()).isEqualTo("user-1");
    assertThat(incidentOutcome.getAssignedResponders().get(0).getEmail()).isEqualTo("oncall@harness.io");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecutePageOnCallFalse() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .pageOnCall(ParameterField.createValueField(false))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-89");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPageOnCall()).isFalse();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteSendsStatusAsTopLevelNotCustomField() throws Exception {
    AisreCreateIncidentStepParameters params =
        AisreCreateIncidentStepParameters.builder()
            .title(ParameterField.createValueField("Deploy failed"))
            .severity(ParameterField.createValueField("SEV1"))
            .status(ParameterField.createValueField("new"))
            .service(ParameterField.createValueField("checkout"))
            .fields(ParameterField.createValueField(Map.of("status", "new", "custom_note", "hello")))
            .attachPipelineContext(ParameterField.createValueField(false))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-57");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("new");
    assertThat(captor.getValue().getCustomFields()).containsEntry("custom_note", "hello");
    assertThat(captor.getValue().getCustomFields()).doesNotContainKey("status");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteOmitsTimelineMessageWhenAttachPipelineContextDisabled() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .description(ParameterField.createValueField("Smoke test failed"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-56");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().getPipelineContextTimelineMessage()).isNull();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteUsesPipelineContextFormatterWhenEnabled() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .description(ParameterField.createValueField("Smoke test failed"))
                                                   .attachPipelineContext(ParameterField.createValueField(true))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);
    when(aisrePipelineContextFormatter.formatPipelineContextBlock(ambiance))
        .thenReturn("Created from Harness pipeline execution.\nExecution URL: https://example");

    IncidentResponse body = new IncidentResponse();
    body.setPrettyId("INC-55");
    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(Response.success(body));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateIncidentRequest> captor = ArgumentCaptor.forClass(CreateIncidentRequest.class);
    verify(aiSrePipelineClient).createIncident(any(), any(), any(), captor.capture());
    verify(aisrePipelineContextFormatter, times(1)).formatPipelineContextBlock(ambiance);
    assertThat(captor.getValue().getSummary()).contains("Smoke test failed");
    assertThat(captor.getValue().getSummary()).contains("Execution URL: https://example");
    assertThat(captor.getValue().getPipelineContextTimelineMessage()).contains("Execution URL: https://example");
    assertThat(captor.getValue().getPageOnCall()).isTrue();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testMissingServiceFailsTheStep() {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("service");
    verify(aiSrePipelineClient, times(0)).createIncident(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testIncidentFailureIsNonFatal() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<IncidentResponse> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new IOException("AI SRE unreachable"));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Incident creation must not fail the pipeline; IGNORE_FAILED surfaces a UI warning.
    assertThat(response.getStatus()).isEqualTo(Status.IGNORE_FAILED);
    assertThat(response.getStepOutcomes()).isEmpty();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testIncidentApiErrorIsNonFatalAndSurfacesMessage() throws Exception {
    AisreCreateIncidentStepParameters params = AisreCreateIncidentStepParameters.builder()
                                                   .title(ParameterField.createValueField("Deploy failed"))
                                                   .severity(ParameterField.createValueField("SEV1"))
                                                   .service(ParameterField.createValueField("checkout"))
                                                   .incidentType(ParameterField.createValueField("INCd"))
                                                   .attachPipelineContext(ParameterField.createValueField(false))
                                                   .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<IncidentResponse> mockCall = mock(Call.class);
    ResponseBody errorBody =
        ResponseBody.create(MediaType.parse("application/json"), "Activity template not found: INCd");
    when(mockCall.execute()).thenReturn(Response.error(404, errorBody));
    when(aiSrePipelineClient.createIncident(any(), any(), any(), any(CreateIncidentRequest.class)))
        .thenReturn(mockCall);

    StepResponse response =
        step.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.IGNORE_FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).contains("Activity template not found: INCd");
    assertThat(response.getStepOutcomes()).isEmpty();
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
                             .name(AisreCreateIncidentStep.OUTPUT)
                             .outcome(AisreCreateIncidentOutcome.builder().incidentId("INC-1").build())
                             .build())
            .build();

    StepExecutionTelemetryEventDTO telemetryEventDTO =
        step.getStepExecutionTelemetryEventDTO(ambiance, stepBaseParameters, stepResponse);

    assertThat(telemetryEventDTO.getStepType()).isEqualTo(AisreCreateIncidentStep.STEP_TYPE.getType());
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
  public void testGetStepExecutionTelemetryEventDTOIncludesSeverity() {
    AisreCreateIncidentStepParameters params =
        AisreCreateIncidentStepParameters.builder().severity(ParameterField.createValueField("SEV1")).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    StepExecutionTelemetryEventDTO telemetryEventDTO = step.getStepExecutionTelemetryEventDTO(
        ambiance, stepBaseParameters, StepResponse.builder().status(Status.SUCCEEDED).build());

    assertThat(telemetryEventDTO.getProperties().get("severity")).isEqualTo("SEV1");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTOIncludesExecutionTime() {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    long start = System.currentTimeMillis();
    StepResponse stepResponse = StepResponse.builder()
                                    .status(Status.SUCCEEDED)
                                    .unitProgressList(List.of(UnitProgress.newBuilder()
                                                                  .setUnitName("Execute")
                                                                  .setStatus(UnitStatus.SUCCESS)
                                                                  .setStartTime(start)
                                                                  .setEndTime(start + 250)
                                                                  .build()))
                                    .build();

    StepExecutionTelemetryEventDTO telemetryEventDTO =
        step.getStepExecutionTelemetryEventDTO(ambiance, stepBaseParameters, stepResponse);

    assertThat(telemetryEventDTO.getProperties().get(AisreBaseStep.TELEMETRY_EXECUTION_TIME_MS)).isEqualTo(250L);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTOOmitsExecutionTimeWhenUnitProgressMissing() {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);

    StepExecutionTelemetryEventDTO telemetryEventDTO = step.getStepExecutionTelemetryEventDTO(
        ambiance, stepBaseParameters, StepResponse.builder().status(Status.FAILED).build());

    assertThat(telemetryEventDTO.getProperties()).doesNotContainKey(AisreBaseStep.TELEMETRY_EXECUTION_TIME_MS);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testPostSyncValidatePublishesTelemetry() {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    step.postSyncValidate(ambiance, stepBaseParameters, stepResponse);

    ArgumentCaptor<StepExecutionTelemetryEventDTO> captor =
        ArgumentCaptor.forClass(StepExecutionTelemetryEventDTO.class);
    verify(stepsInstrumentationHelper, times(1)).publishStepEvent(eq(ambiance), captor.capture());
    assertThat(captor.getValue().getStepType()).isEqualTo(AisreCreateIncidentStep.STEP_TYPE.getType());
  }
}
