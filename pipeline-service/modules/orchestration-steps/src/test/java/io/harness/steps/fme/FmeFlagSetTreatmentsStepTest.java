/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.GONZALO;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmeResponse;
import io.harness.fme.Treatment;
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
import io.harness.utils.PmsFeatureFlagHelper;

import io.split.client.dtos.URN;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeFlagSetTreatmentsStepTest extends CategoryTest {
  @InjectMocks FmeFlagSetTreatmentsStep fmeFlagSetTreatmentsStep;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String FLAG_NAME = "testFlag";
  private static final String ENVIRONMENT = "production";
  private static final String DEFAULT_TREATMENT = "on";
  private static final String BASELINE_TREATMENT = "off";

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
    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);

    // Inject ExceptionManager spy into FmeStepResponseBuilder spy
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testAddNewTreatmentsToExistingDefinition() throws Exception {
    // Setup - add a new treatment to existing definition
    TreatmentConfiguration treatmentConfig1 =
        TreatmentConfiguration.builder()
            .treatment(ParameterField.createValueField("on"))
            .description(ParameterField.createValueField("Updated on description"))
            .build();

    TreatmentConfiguration treatmentConfig2 =
        TreatmentConfiguration.builder()
            .treatment(ParameterField.createValueField("off"))
            .description(ParameterField.createValueField("Updated off description"))
            .build();

    TreatmentConfiguration treatmentConfig3 =
        TreatmentConfiguration.builder()
            .treatment(ParameterField.createValueField("experimental"))
            .description(ParameterField.createValueField("New experimental treatment"))
            .build();

    List<TreatmentConfiguration> treatments = Arrays.asList(treatmentConfig1, treatmentConfig2, treatmentConfig3);

    FmeFlagSetTreatmentsParameters stepParams =
        FmeFlagSetTreatmentsParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .defaultTreatment(ParameterField.createValueField(DEFAULT_TREATMENT))
            .baselineTreatment(ParameterField.createValueField(BASELINE_TREATMENT))
            .treatments(ParameterField.createValueField(treatments))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    // Existing definition with only 2 treatments
    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .defaultTreatment("off")
            .treatments(Arrays.asList(Treatment.builder().name("on").description("Old on description").build(),
                Treatment.builder().name("off").description("Old off description").build()))
            .build();

    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(true);
    when(getResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTreatmentsStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testUpdateDescriptionOnlyPreservingOtherFields() throws Exception {
    // Setup - update description only, preserving configurations and segments
    TreatmentConfiguration treatmentConfig1 =
        TreatmentConfiguration.builder()
            .treatment(ParameterField.createValueField("on"))
            .description(ParameterField.createValueField("New description for on"))
            .build();

    List<TreatmentConfiguration> treatments = Arrays.asList(treatmentConfig1);

    FmeFlagSetTreatmentsParameters stepParams =
        FmeFlagSetTreatmentsParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .defaultTreatment(ParameterField.createValueField(DEFAULT_TREATMENT))
            .baselineTreatment(ParameterField.createValueField(BASELINE_TREATMENT))
            .treatments(ParameterField.createValueField(treatments))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    // Existing definition with treatment that has configurations and segments
    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .defaultTreatment("off")
            .treatments(Arrays.asList(Treatment.builder()
                                          .name("on")
                                          .description("Old description")
                                          .configurations("{\"feature_enabled\": true}")
                                          .keys(Arrays.asList("user1", "user2"))
                                          .segments(Arrays.asList("beta_users"))
                                          .largeSegments(Arrays.asList("premium_users"))
                                          .ruleBasedSegments(Arrays.asList("rule1"))
                                          .build()))
            .build();

    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(true);
    when(getResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTreatmentsStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testRemoveTreatmentsNotInNewList() throws Exception {
    // Setup - only specify 'on' treatment, 'off' should be removed
    TreatmentConfiguration treatmentConfig1 = TreatmentConfiguration.builder()
                                                  .treatment(ParameterField.createValueField("on"))
                                                  .description(ParameterField.createValueField("Keep this treatment"))
                                                  .build();

    List<TreatmentConfiguration> treatments = Arrays.asList(treatmentConfig1);

    FmeFlagSetTreatmentsParameters stepParams =
        FmeFlagSetTreatmentsParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .defaultTreatment(ParameterField.createValueField(DEFAULT_TREATMENT))
            .baselineTreatment(ParameterField.createValueField(BASELINE_TREATMENT))
            .treatments(ParameterField.createValueField(treatments))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    // Existing definition with 3 treatments
    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .defaultTreatment("off")
            .treatments(Arrays.asList(Treatment.builder().name("on").description("Description for on").build(),
                Treatment.builder().name("off").description("Description for off").build(),
                Treatment.builder().name("control").description("Description for control").build()))
            .build();

    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(true);
    when(getResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    Call<FmeResponse<FeatureFlagDefinition>> updateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> updateResponse = mock(Response.class);
    when(updateCall.execute()).thenReturn(updateResponse);
    when(updateResponse.isSuccessful()).thenReturn(true);
    when(updateResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
        .thenReturn(updateCall);

    // Execute
    StepResponse response = fmeFlagSetTreatmentsStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient)
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacFailureOnGet() throws Exception {
    TreatmentConfiguration treatmentConfig = TreatmentConfiguration.builder()
                                                 .treatment(ParameterField.createValueField("on"))
                                                 .description(ParameterField.createValueField("Description"))
                                                 .build();

    FmeFlagSetTreatmentsParameters stepParams =
        FmeFlagSetTreatmentsParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .defaultTreatment(ParameterField.createValueField(DEFAULT_TREATMENT))
            .baselineTreatment(ParameterField.createValueField(BASELINE_TREATMENT))
            .treatments(ParameterField.createValueField(Collections.singletonList(treatmentConfig)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Mock error response (not 404, some other error)
    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(false);
    when(getResponse.code()).thenReturn(500);
    when(getResponse.errorBody())
        .thenReturn(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"Internal server error\"}"));

    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    StepResponse response = fmeFlagSetTreatmentsStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify failure
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(fmeFlagSetTreatmentsStep.STEP_TYPE).isEqualTo(StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS_STEP_TYPE);
  }
}
