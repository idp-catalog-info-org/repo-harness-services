/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import io.harness.fme.FmePatchOperation;
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
public class FmeFlagDefaultAllocationStepTest extends CategoryTest {
  @InjectMocks FmeFlagDefaultAllocationStep fmeFlagDefaultAllocationStep;
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
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeFlagDefaultAllocationStep.STEP_TYPE)
        .isEqualTo(StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testUsesPatchInsteadOfPut() throws Exception {
    Allocation alloc1 = Allocation.builder()
                            .treatment(ParameterField.createValueField("on"))
                            .amount(ParameterField.createValueField(70))
                            .build();
    Allocation alloc2 = Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(30))
                            .build();

    FmeFlagDefaultAllocationStepParameters stepParams =
        FmeFlagDefaultAllocationStepParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .treatments(Arrays.asList(Treatment.builder().name("on").build(), Treatment.builder().name("off").build()))
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

    StepResponse response = fmeFlagDefaultAllocationStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    // Must use PATCH, never PUT
    verify(fmePipelineClient, times(1))
        .patchFeatureFlagDefinition(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList());
    verify(fmePipelineClient, never())
        .updateFeatureFlagDefinition(anyString(), anyString(), anyString(), anyString(), anyString(), any());

    List<FmePatchOperation> capturedPatch = patchCaptor.getValue();
    assertThat(capturedPatch).hasSize(1);
    assertThat(capturedPatch.get(0).getOp()).isEqualTo("replace");
    assertThat(capturedPatch.get(0).getPath()).isEqualTo("/defaultRule");
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testDefaultAllocationSuccess() throws Exception {
    Allocation alloc1 = Allocation.builder()
                            .treatment(ParameterField.createValueField("on"))
                            .amount(ParameterField.createValueField(60))
                            .build();
    Allocation alloc2 = Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(40))
                            .build();

    FmeFlagDefaultAllocationStepParameters stepParams =
        FmeFlagDefaultAllocationStepParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    URN envUrn = new URN();
    envUrn.name = ENVIRONMENT;
    envUrn.type = "Environment";

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .environment(envUrn)
            .treatments(Arrays.asList(Treatment.builder().name("on").build(), Treatment.builder().name("off").build()))
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
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(patchCall);

    StepResponse response = fmeFlagDefaultAllocationStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testDefaultAllocationInvalidTreatment() throws Exception {
    Allocation alloc1 = Allocation.builder()
                            .treatment(ParameterField.createValueField("on"))
                            .amount(ParameterField.createValueField(60))
                            .build();
    Allocation alloc2 = Allocation.builder()
                            .treatment(ParameterField.createValueField("nonexistent"))
                            .amount(ParameterField.createValueField(40))
                            .build();

    FmeFlagDefaultAllocationStepParameters stepParams =
        FmeFlagDefaultAllocationStepParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .treatments(Arrays.asList(Treatment.builder().name("on").build(), Treatment.builder().name("off").build()))
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

    StepResponse response = fmeFlagDefaultAllocationStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testDefaultAllocationSumNot100() throws Exception {
    Allocation alloc1 = Allocation.builder()
                            .treatment(ParameterField.createValueField("on"))
                            .amount(ParameterField.createValueField(60))
                            .build();
    Allocation alloc2 = Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(20))
                            .build();

    FmeFlagDefaultAllocationStepParameters stepParams =
        FmeFlagDefaultAllocationStepParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    StepResponse response = fmeFlagDefaultAllocationStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, never()).getFeatureFlagDefinitionInEnvironment(any(), any(), any(), any(), any());
    verify(fmePipelineClient, never()).patchFeatureFlagDefinition(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testDefaultAllocationApiFailure() throws Exception {
    Allocation alloc1 = Allocation.builder()
                            .treatment(ParameterField.createValueField("on"))
                            .amount(ParameterField.createValueField(50))
                            .build();
    Allocation alloc2 = Allocation.builder()
                            .treatment(ParameterField.createValueField("off"))
                            .amount(ParameterField.createValueField(50))
                            .build();

    FmeFlagDefaultAllocationStepParameters stepParams =
        FmeFlagDefaultAllocationStepParameters.builder()
            .flagName(ParameterField.createValueField(FLAG_NAME))
            .environment(ParameterField.createValueField(ENVIRONMENT))
            .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    FeatureFlagDefinition existingDefinition =
        FeatureFlagDefinition.builder()
            .name(FLAG_NAME)
            .treatments(Arrays.asList(Treatment.builder().name("on").build(), Treatment.builder().name("off").build()))
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
    when(patchResponse.isSuccessful()).thenReturn(false);
    when(patchResponse.errorBody())
        .thenReturn(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"server error\"}"));
    when(fmePipelineClient.patchFeatureFlagDefinition(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT), eq(FLAG_NAME), anyList()))
        .thenReturn(patchCall);

    StepResponse response = fmeFlagDefaultAllocationStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }
}
