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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.executions.steps.node.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
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
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CV)
@RunWith(MockitoJUnitRunner.class)
public class ChangeAdvisorStepTest extends CategoryTest {
  @InjectMocks private ChangeAdvisorEvaluationHelper evaluationHelper;

  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private ChangeAdvisorServiceClient changeAdvisorServiceClient;

  private ChangeAdvisorStep changeAdvisorStep;

  private Ambiance ambiance;

  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrgId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String PIPELINE_ID = "testPipelineId";
  private static final String EXECUTION_ID = "testExecutionId";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Before
  public void setup() throws Exception {
    changeAdvisorStep = new ChangeAdvisorStep();
    Field evaluationHelperField = ChangeAdvisorStep.class.getDeclaredField("evaluationHelper");
    evaluationHelperField.setAccessible(true);
    evaluationHelperField.set(changeAdvisorStep, evaluationHelper);
    ambiance = buildAmbianceWithStageType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName());
  }

  private Ambiance buildAmbianceWithStageType(String stageStepType) {
    return buildAmbianceWithStageType(stageStepType, null);
  }

  private Ambiance buildAmbianceWithStageType(String stageStepType, String harnessVersion) {
    ExecutionMetadata.Builder metadataBuilder = ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID);
    if (harnessVersion != null) {
      metadataBuilder.setHarnessVersion(harnessVersion);
    }
    Ambiance.Builder builder = Ambiance.newBuilder()
                                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                   .setPlanExecutionId(EXECUTION_ID)
                                   .setMetadata(metadataBuilder.build());
    if (stageStepType != null) {
      builder.addLevels(
          Level.newBuilder()
              .setRuntimeId("stage-runtime")
              .setSetupId("stage-setup")
              .setIdentifier("stage1")
              .setStepType(StepType.newBuilder().setType(stageStepType).setStepCategory(StepCategory.STAGE).build())
              .build());
    }
    return builder.build();
  }

  private StepBaseParameters buildStepParameters(ChangeAdvisorStepSpecParameters spec) {
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(spec);
    return stepBaseParameters;
  }

  @SuppressWarnings("unchecked")
  private Call<Advisory> mockCallReturning(Response<Advisory> response) throws IOException {
    Call<Advisory> call = mock(Call.class);
    when(call.execute()).thenReturn(response);
    return call;
  }

  @SuppressWarnings("unchecked")
  private Call<Advisory> mockCallThrowing(Exception exception) throws IOException {
    Call<Advisory> call = mock(Call.class);
    when(call.execute()).thenThrow(exception);
    return call;
  }

  private ChangeAdvisorComingSoonOutcome extractComingSoonOutcome(StepResponse stepResponse) {
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(stepOutcome.getName()).isEqualTo(ChangeAdvisorStep.OUTCOME_NAME);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(ChangeAdvisorComingSoonOutcome.class);
    return (ChangeAdvisorComingSoonOutcome) stepOutcome.getOutcome();
  }

  private ChangeAdvisorOutcome extractAdvisorOutcome(StepResponse stepResponse) {
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(stepOutcome.getName()).isEqualTo(ChangeAdvisorStep.OUTCOME_NAME);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(ChangeAdvisorOutcome.class);
    return (ChangeAdvisorOutcome) stepOutcome.getOutcome();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testFfDisabledSkipsClientCallAndReturnsSucceeded() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(false);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(
        ambiance, buildStepParameters(null), StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    assertThat(stepResponse.getStepOutcomes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testFfLookupThrowsTreatedAsDisabledAndReturnsSucceeded() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED))
        .thenThrow(new RuntimeException("FF backend down"));

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(
        ambiance, buildStepParameters(null), StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    assertThat(stepResponse.getStepOutcomes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testDeploymentStageStepV1CallsAdvisoryService() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance v1Ambiance = buildAmbianceWithStageType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP_V1.getName());

    Advisory advisory = new Advisory();
    advisory.setId("adv-v1");
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(v1Ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient).createAdvisory(any(CreateAdvisoryRequest.class));
    ChangeAdvisorOutcome outcome = extractAdvisorOutcome(stepResponse);
    assertThat(outcome.getAdvisoryId()).isEqualTo("adv-v1");
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testUiRenderPropagatesFromAdvisoryEvidence() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance v1Ambiance = buildAmbianceWithStageType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP_V1.getName());

    Advisory advisory = new Advisory();
    advisory.setId("adv-uirender");
    advisory.setEvidence(Map.of("signals", List.of(Map.of("source", "UI_RENDER", "data", Map.of("hello", "world")))));

    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(v1Ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    ChangeAdvisorOutcome outcome = extractAdvisorOutcome(stepResponse);
    assertThat(outcome.getUiRender()).isNotNull();
    assertThat(OBJECT_MAPPER.readTree(outcome.getUiRender()))
        .isEqualTo(OBJECT_MAPPER.readTree("{\"hello\":\"world\"}"));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testFfEnabledSuccessfulAdvisoryReturnsSucceeded() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Advisory advisory = new Advisory();
    advisory.setId("adv-123");
    advisory.setStatus("PENDING");
    advisory.setDecision("ALLOW");
    advisory.setScore(0.85);
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient).createAdvisory(any(CreateAdvisoryRequest.class));
    ChangeAdvisorOutcome outcome = extractAdvisorOutcome(stepResponse);
    assertThat(outcome.getAdvisoryId()).isEqualTo("adv-123");
    assertThat(outcome.getStatus()).isEqualTo("PENDING");
    assertThat(outcome.getDecision()).isEqualTo("ALLOW");
    assertThat(outcome.getScore()).isEqualTo(0.85);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testCdStageSuccessfulAdvisoryOutcomeFieldsMapExactlyFromResponse() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Advisory advisory = new Advisory();
    advisory.setId("adv-outcome-mapping-test");
    advisory.setStatus("COMPLETED");
    advisory.setDecision("BLOCK");
    advisory.setScore(0.42);
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    ChangeAdvisorOutcome outcome = extractAdvisorOutcome(stepResponse);
    assertThat(outcome.getAdvisoryId()).isEqualTo(advisory.getId());
    assertThat(outcome.getStatus()).isEqualTo(advisory.getStatus());
    assertThat(outcome.getDecision()).isEqualTo(advisory.getDecision());
    assertThat(outcome.getScore()).isEqualTo(advisory.getScore());
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testFfEnabledClientThrowsIsGracefullyDegradedToSucceeded() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Call<Advisory> call = mockCallThrowing(new IOException("connection refused"));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient).createAdvisory(any(CreateAdvisoryRequest.class));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testFfEnabledNon2xxResponseReturnsSucceeded() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Response<Advisory> errorResponse =
        Response.error(503, ResponseBody.create(MediaType.parse("application/json"), "{\"error\":\"unavailable\"}"));
    Call<Advisory> call = mockCallReturning(errorResponse);
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient).createAdvisory(any(CreateAdvisoryRequest.class));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testRequestBuildingMapsAmbianceAndSpecParams() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Advisory advisory = new Advisory();
    advisory.setId("adv-xyz");
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    ChangeAdvisorStepSpecParameters spec = ChangeAdvisorStepSpecParameters.builder()
                                               .mode(ParameterField.createValueField("ADVISORY"))
                                               .timeoutMinutes(ParameterField.createValueField(5))
                                               .presets(ParameterField.createValueField(Arrays.asList("safe", "fast")))
                                               .env(ParameterField.createValueField("prod-1"))
                                               .build();

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(
        ambiance, buildStepParameters(spec), StepInputPackage.builder().build(), null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<CreateAdvisoryRequest> captor = ArgumentCaptor.forClass(CreateAdvisoryRequest.class);
    verify(changeAdvisorServiceClient).createAdvisory(captor.capture());
    CreateAdvisoryRequest req = captor.getValue();

    assertThat(req.getPipelineEngine()).isEqualTo("NG");
    assertThat(req.getStageType()).isEqualTo("Deployment");
    assertThat(req.getPipelineContext()).isNotNull();
    assertThat(req.getPipelineContext().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(req.getPipelineContext().getOrgId()).isEqualTo(ORG_ID);
    assertThat(req.getPipelineContext().getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(req.getPipelineContext().getPipelineId()).isEqualTo(PIPELINE_ID);

    assertThat(req.getExecution()).isNotNull();
    assertThat(req.getExecution().getPipelineExecutionId()).isEqualTo(EXECUTION_ID);
    assertThat(req.getExecution().getPlanExecutionId()).isEqualTo(EXECUTION_ID);

    assertThat(req.getOptions()).isNotNull();
    assertThat(req.getOptions().getDryRun()).isTrue();
    assertThat(req.getOptions().getTimeoutSeconds()).isEqualTo(300);

    assertThat(req.getPresets()).containsExactly("safe", "fast");
    assertThat(req.getEnvironment()).isNotNull();
    assertThat(req.getEnvironment().getId()).isEqualTo("prod-1");
    assertThat(req.getEnvironment().getName()).isEqualTo("prod-1");
    assertThat(req.getPipelineContext().getMetadata()).isNotNull();
    assertThat(req.getPipelineContext().getMetadata().get("harnessYamlVersion")).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testRequestBuildingSendsHarnessYamlVersionMetadataForV1Pipeline() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance v1Ambiance =
        buildAmbianceWithStageType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName(), HarnessYamlVersion.V1);

    Advisory advisory = new Advisory();
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    changeAdvisorStep.executeSyncAfterRbac(v1Ambiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    ArgumentCaptor<CreateAdvisoryRequest> captor = ArgumentCaptor.forClass(CreateAdvisoryRequest.class);
    verify(changeAdvisorServiceClient).createAdvisory(captor.capture());
    CreateAdvisoryRequest req = captor.getValue();

    assertThat(req.getPipelineContext()).isNotNull();
    assertThat(req.getPipelineContext().getMetadata()).isNotNull();
    assertThat(req.getPipelineContext().getMetadata().get("harnessYamlVersion")).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testRequestBuildingDoesNotSendUnresolvedExpressionsAsRawPlaceholders() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);

    Advisory advisory = new Advisory();
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    ChangeAdvisorStepSpecParameters spec =
        ChangeAdvisorStepSpecParameters.builder()
            .mode(ParameterField.createExpressionField(true, "<+input>", null, true))
            .env(ParameterField.createExpressionField(true, "<+pipeline.variables.env>", null, true))
            .timeoutMinutes(ParameterField.createExpressionField(true, "<+input>", null, false))
            .presets(ParameterField.createExpressionField(true, "<+input>", null, false))
            .build();

    changeAdvisorStep.executeSyncAfterRbac(
        ambiance, buildStepParameters(spec), StepInputPackage.builder().build(), null);

    ArgumentCaptor<CreateAdvisoryRequest> captor = ArgumentCaptor.forClass(CreateAdvisoryRequest.class);
    verify(changeAdvisorServiceClient).createAdvisory(captor.capture());
    CreateAdvisoryRequest req = captor.getValue();

    assertThat(req.getEnvironment()).isNull();
    assertThat(req.getPresets()).isNull();
    assertThat(req.getOptions()).isNotNull();
    assertThat(req.getOptions().getDryRun()).isNull();
    assertThat(req.getOptions().getTimeoutSeconds()).isNull();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testV1IntegrationStageStepPmsCallsAdvisoryService() throws Exception {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance v1CiAmbiance = buildAmbianceWithStageType("IntegrationStageStepPMS", HarnessYamlVersion.V1);

    Advisory advisory = new Advisory();
    advisory.setId("adv-v1-ci");
    Call<Advisory> call = mockCallReturning(Response.success(advisory));
    when(changeAdvisorServiceClient.createAdvisory(any())).thenReturn(call);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(v1CiAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient).createAdvisory(any(CreateAdvisoryRequest.class));
    ChangeAdvisorOutcome outcome = extractAdvisorOutcome(stepResponse);
    assertThat(outcome.getAdvisoryId()).isEqualTo("adv-v1-ci");
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testV0IntegrationStageStepPmsShortCircuitsWithComingSoonOutcome() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance v0CiAmbiance = buildAmbianceWithStageType("IntegrationStageStepPMS", HarnessYamlVersion.V0);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(v0CiAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.isComingSoon()).isTrue();
    assertThat(outcome.getContextType()).isEqualTo("ci");
    assertThat(outcome.getTitle()).isNotBlank();
    assertThat(outcome.getMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testCiStageShortCircuitsWithComingSoonOutcome() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance ciAmbiance = buildAmbianceWithStageType("IntegrationStageStepPMS");

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(ciAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.isComingSoon()).isTrue();
    assertThat(outcome.getContextType()).isEqualTo("ci");
    assertThat(outcome.getTitle()).isNotBlank();
    assertThat(outcome.getMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testIacmStageShortCircuitsWithComingSoonOutcome() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance iacmAmbiance = buildAmbianceWithStageType("IACMStage");

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(iacmAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.getContextType()).isEqualTo("iacm");
    assertThat(outcome.getTitle()).isNotBlank();
    assertThat(outcome.getMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testYamlDeploymentStageTypeShortCircuitsWithComingSoonOutcome() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance yamlTypeAmbiance = buildAmbianceWithStageType("Deployment");

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(yamlTypeAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.getContextType()).isEqualTo("custom");
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testUnrecognizedStageShortCircuitsWithComingSoonOutcome() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance customAmbiance = buildAmbianceWithStageType("CustomStage");

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(customAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.getContextType()).isNotIn("ci", "iacm");
    assertThat(outcome.getContextType()).isIn("approval", "custom", "unknown");
    assertThat(outcome.getTitle()).isNotBlank();
    assertThat(outcome.getMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testNullStageTypeShortCircuitsAsUnknown() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.FF_CHANGEADVISOR_ENABLED)).thenReturn(true);
    Ambiance nullStageAmbiance = buildAmbianceWithStageType(null);

    StepResponse stepResponse = changeAdvisorStep.executeSyncAfterRbac(nullStageAmbiance,
        buildStepParameters(ChangeAdvisorStepSpecParameters.builder().build()), StepInputPackage.builder().build(),
        null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(changeAdvisorServiceClient, never()).createAdvisory(any());
    ChangeAdvisorComingSoonOutcome outcome = extractComingSoonOutcome(stepResponse);
    assertThat(outcome.getContextType()).isEqualTo("unknown");
  }
}
