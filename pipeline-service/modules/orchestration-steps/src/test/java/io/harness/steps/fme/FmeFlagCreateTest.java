/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.CAMERON;
import static io.harness.rule.OwnerRule.TIRTH;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlag;
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
import io.harness.steps.fme.exception.FmeInvalidParameterException;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Arrays;
import java.util.Collections;
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
public class FmeFlagCreateTest extends CategoryTest {
  @InjectMocks FmeFlagCreate fmeFlagCreateStep;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Mock private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Mock private FmeOwnerResolver fmeOwnerResolver;

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
    Mockito.when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    Mockito.when(fmeOwnerResolver.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacSuccess() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";
    String description = "Test flag description";

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .description(ParameterField.createValueField(description))
                                             .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                             .owners(ParameterField.createValueField(Arrays.asList("owner1")))
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient, times(1))
        .createFeatureFlag(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class));
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteWithCustomTreatments() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";

    List<TreatmentConfiguration> treatments =
        Arrays.asList(TreatmentConfiguration.builder()
                          .treatment(ParameterField.createValueField("on"))
                          .description(ParameterField.createValueField("On treatment"))
                          .build(),
            TreatmentConfiguration.builder()
                .treatment(ParameterField.createValueField("off"))
                .description(ParameterField.createValueField("Off treatment"))
                .build());

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .treatments(ParameterField.createValueField(treatments))
                                             .defaultTreatment(ParameterField.createValueField("on"))
                                             .baselineTreatment(ParameterField.createValueField("off"))
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
    verify(fmePipelineClient)
        .createFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), captor.capture());

    FeatureFlag capturedFlag = captor.getValue();
    assertThat(capturedFlag.getDefaultRolloutDefinition()).isNotNull();
    assertThat(capturedFlag.getDefaultRolloutDefinition().getTreatments()).hasSize(2);
    assertThat(capturedFlag.getDefaultRolloutDefinition().getDefaultTreatment()).isEqualTo("on");
    assertThat(capturedFlag.getDefaultRolloutDefinition().getBaselineTreatment()).isEqualTo("off");
    assertThat(capturedFlag.getDefaultRolloutDefinition().getDefaultRule()).hasSize(2);
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteWithNoTreatmentsDoesNotSetDefinition() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
    verify(fmePipelineClient)
        .createFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), captor.capture());

    assertThat(captor.getValue().getDefaultRolloutDefinition()).isNull();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testExecuteWithPartialTreatmentsThrowsValidationError() {
    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField("testFlag"))
                                             .trafficType(ParameterField.createValueField("user"))
                                             .defaultTreatment(ParameterField.createValueField("on"))
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    StepResponse failedResponse = StepResponse.builder().status(Status.FAILED).build();
    when(fmeStepResponseBuilder.getFailedStepResponse(any(Long.class), any(Long.class), any(Exception.class)))
        .thenReturn(failedResponse);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
    verify(fmeStepResponseBuilder).getFailedStepResponse(any(Long.class), any(Long.class), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).isInstanceOf(FmeInvalidParameterException.class);
    assertThat(exceptionCaptor.getValue().getMessage()).contains("All three fields");
  }

  @Test
  @Owner(developers = TIRTH)
  @Category(UnitTests.class)
  public void testExecuteWithEmptyTagsSucceeds() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .tags(ParameterField.createValueField(Collections.emptyList()))
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
    verify(fmePipelineClient)
        .createFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), captor.capture());
    assertThat(captor.getValue().getTags()).isEmpty();
  }

  @Test
  @Owner(developers = TIRTH)
  @Category(UnitTests.class)
  public void testExecuteWithNullTagsSucceeds() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .tags(ParameterField.ofNull())
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
    verify(fmePipelineClient)
        .createFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), captor.capture());
    assertThat(captor.getValue().getTags()).isEmpty();
  }

  @Test
  @Owner(developers = TIRTH)
  @Category(UnitTests.class)
  public void testExecuteWithBlankStringTagsSucceeds() throws Exception {
    String trafficType = "user";
    String flagName = "testFlag";

    // Simulates the actual runtime-input bug: a blank string ("") resolved for an optional
    // tags: <+input> field, stored in the List<String>-typed ParameterField with typeString=true.
    // This is the exact branch ParameterField.isBlank() guards against (isTypeString() &&
    // StringUtils.isBlank(value)), which is not exercised by the empty-list or ofNull() cases above.
    @SuppressWarnings("unchecked")
    ParameterField<List<String>> blankTags =
        (ParameterField<List<String>>) (ParameterField<?>) ParameterField.createValueFieldWithInputSetValidator(
            "", null, true);

    FmeFlagCreateParameters stepParams = FmeFlagCreateParameters.builder()
                                             .name(ParameterField.createValueField(flagName))
                                             .trafficType(ParameterField.createValueField(trafficType))
                                             .tags(blankTags)
                                             .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlag>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlag>> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body())
        .thenReturn(FmeResponse.<FeatureFlag>builder().entity(FeatureFlag.builder().name(flagName).build()).build());

    when(fmePipelineClient.createFeatureFlag(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), any(FeatureFlag.class)))
        .thenReturn(mockCall);

    StepResponse response =
        fmeFlagCreateStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
    verify(fmePipelineClient)
        .createFeatureFlag(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(trafficType), eq(true), captor.capture());
    assertThat(captor.getValue().getTags()).isEmpty();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeFlagCreate.STEP_TYPE).isNotNull();
  }
}
