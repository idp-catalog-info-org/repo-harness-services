/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.CAMERON;
import static io.harness.rule.OwnerRule.GONZALO;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.FmeResponse;
import io.harness.fme.Treatment;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagHelper;

import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeFlagAddRemoveTargetsStepTest extends CategoryTest {
  @InjectMocks FmeFlagAddRemoveTargetsStep fmeFlagAddRemoveTargetsStep;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private NGLogCallback ngLogCallback;
  @Mock private FMEPipelineClient fmePipelineClient;

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
    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeFlagAddRemoveTargetsStep.STEP_TYPE)
        .isEqualTo(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testProcessTargetsWithExistingDefinition() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    TreatmentTarget treatmentTarget = TreatmentTarget.builder()
                                          .treatment(ParameterField.createValueField("on"))
                                          .addKeys(ParameterField.createValueField(Arrays.asList("user1", "user2")))
                                          .removeKeys(ParameterField.createValueField(Arrays.asList("user3")))
                                          .addSegments(ParameterField.createValueField(Arrays.asList("segment1")))
                                          .removeSegments(ParameterField.createValueField(Arrays.asList("segment2")))
                                          .build();

    List<TreatmentTarget> treatments = Arrays.asList(treatmentTarget);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .treatments(Arrays.asList(Treatment.builder()
                                          .name("on")
                                          .keys(new ArrayList<>(Arrays.asList("user3", "user4")))
                                          .segments(new ArrayList<>(Arrays.asList("segment2", "segment3")))
                                          .build(),
                Treatment.builder().name("off").description("OFF treatment").build()))
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

    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
    when(patchCall.execute()).thenReturn(patchResponse);
    when(patchResponse.isSuccessful()).thenReturn(true);
    when(patchResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    ArgumentCaptor<List<FmePatchOperation>> patchCaptor = ArgumentCaptor.forClass(List.class);
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), patchCaptor.capture()))
        .thenReturn(patchCall);

    fmeFlagAddRemoveTargetsStep.processTargets(ngLogCallback, scope, FLAG_NAME, ENVIRONMENT, treatments);

    verify(fmePipelineClient, times(1))
        .getFeatureFlagDefinitionInEnvironment(FLAG_NAME, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT);
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
    verify(fmePipelineClient, never()).updateFeatureFlagDefinition(any(), any(), any(), any(), any(), any());

    List<Treatment> patchedTreatments = (List<Treatment>) patchCaptor.getValue().get(0).getValue();
    assertThat(patchedTreatments).extracting(Treatment::getName).containsExactly("on", "off");
    assertThat(patchedTreatments.get(1).getDescription()).isEqualTo("OFF treatment");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testProcessTargetsWithNoExistingDefinition() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    TreatmentTarget treatmentTarget = TreatmentTarget.builder()
                                          .treatment(ParameterField.createValueField("on"))
                                          .addKeys(ParameterField.createValueField(Arrays.asList("user1", "user2")))
                                          .build();

    List<TreatmentTarget> treatments = Arrays.asList(treatmentTarget);

    Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
    when(getCall.execute()).thenReturn(getResponse);
    when(getResponse.isSuccessful()).thenReturn(false);
    when(getResponse.code()).thenReturn(404);

    when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(getCall);

    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
    when(patchCall.execute()).thenReturn(patchResponse);
    when(patchResponse.isSuccessful()).thenReturn(true);
    when(patchResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(null).build());

    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
        .thenReturn(patchCall);

    fmeFlagAddRemoveTargetsStep.processTargets(ngLogCallback, scope, FLAG_NAME, ENVIRONMENT, treatments);

    verify(fmePipelineClient, times(1))
        .getFeatureFlagDefinitionInEnvironment(FLAG_NAME, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT);
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
    verify(fmePipelineClient, never()).updateFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testProcessTargetsPreservesConfigurationAndDescription() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    TreatmentTarget treatmentTarget = TreatmentTarget.builder()
                                          .treatment(ParameterField.createValueField("on"))
                                          .addKeys(ParameterField.createValueField(Arrays.asList("user1", "user2")))
                                          .removeSegments(ParameterField.createValueField(Arrays.asList("segment1")))
                                          .build();

    List<TreatmentTarget> treatments = Arrays.asList(treatmentTarget);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    // Create existing treatment with configuration and description
    String originalConfiguration = "{\"color\": \"blue\", \"size\": \"large\"}";
    String originalDescription = "This is the ON treatment with important business logic";

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .treatments(Arrays.asList(Treatment.builder()
                                          .name("on")
                                          .keys(new ArrayList<>(Arrays.asList("user3")))
                                          .segments(new ArrayList<>(Arrays.asList("segment1", "segment2")))
                                          .configurations(originalConfiguration)
                                          .description(originalDescription)
                                          .largeSegments(Arrays.asList("largeSegment1"))
                                          .ruleBasedSegments(Arrays.asList("ruleSegment1"))
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

    Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
    when(patchCall.execute()).thenReturn(patchResponse);
    when(patchResponse.isSuccessful()).thenReturn(true);
    when(patchResponse.body())
        .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
        .thenReturn(patchCall);

    fmeFlagAddRemoveTargetsStep.processTargets(ngLogCallback, scope, FLAG_NAME, ENVIRONMENT, treatments);

    verify(fmePipelineClient, times(1))
        .getFeatureFlagDefinitionInEnvironment(FLAG_NAME, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT);
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
    verify(fmePipelineClient, never()).updateFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacSuccess() {
    TreatmentTarget treatmentTarget = TreatmentTarget.builder()
                                          .treatment(ParameterField.createValueField("on"))
                                          .addKeys(ParameterField.createValueField(Arrays.asList("user1")))
                                          .build();

    FmeFlagAddRemoveTargetsStepParameters stepParams =
        FmeFlagAddRemoveTargetsStepParameters.builder()
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .treatments(ParameterField.createValueField(Arrays.asList(treatmentTarget)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder().name(FLAG_NAME).environment(envUrn).treatments(new ArrayList<>()).build();

    try {
      Call<FmeResponse<FeatureFlagDefinition>> getCall = mock(Call.class);
      Response<FmeResponse<FeatureFlagDefinition>> getResponse = mock(Response.class);
      when(getCall.execute()).thenReturn(getResponse);
      when(getResponse.isSuccessful()).thenReturn(true);
      when(getResponse.body())
          .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

      when(fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
               eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
          .thenReturn(getCall);

      Call<FmeResponse<FeatureFlagDefinition>> patchCall = mock(Call.class);
      Response<FmeResponse<FeatureFlagDefinition>> patchResponse = mock(Response.class);
      when(patchCall.execute()).thenReturn(patchResponse);
      when(patchResponse.isSuccessful()).thenReturn(true);
      when(patchResponse.body())
          .thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(existingDefinition).build());

      when(fmePipelineClient.patchFeatureFlagDefinition(
               eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), any()))
          .thenReturn(patchCall);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    StepResponse response = fmeFlagAddRemoveTargetsStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  private Scope ambianceToScope(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String parentUniqueIdentifier = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    return Scope.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectIdentifier)
        .parentUniqueId(parentUniqueIdentifier)
        .build();
  }
}
