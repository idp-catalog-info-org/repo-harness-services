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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import io.harness.utils.PmsFeatureFlagHelper;

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
public class FmeFlagReallocateTrafficStepTest extends CategoryTest {
  @InjectMocks FmeFlagReallocateTrafficStep fmeFlagReallocateTrafficStep;
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

    // Inject ExceptionManager spy into FmeStepResponseBuilder spy
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacSuccess() throws Exception {
    FmeFlagReallocateTrafficParameters stepParams = FmeFlagReallocateTrafficParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlagDefinition>> reallocateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> reallocateResponse = mock(Response.class);
    when(reallocateCall.execute()).thenReturn(reallocateResponse);
    when(reallocateResponse.isSuccessful()).thenReturn(true);
    FeatureFlagDefinition definition = FeatureFlagDefinition.builder().build();
    when(reallocateResponse.body()).thenReturn(FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build());

    when(fmePipelineClient.reallocateFeatureFlag(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(reallocateCall);

    StepResponse response = fmeFlagReallocateTrafficStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);
    verify(fmePipelineClient, times(1)).reallocateFeatureFlag(FLAG_NAME, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacApiFailure() throws Exception {
    FmeFlagReallocateTrafficParameters stepParams = FmeFlagReallocateTrafficParameters.builder()
                                                        .environment(ParameterField.createValueField(ENVIRONMENT))
                                                        .flagName(ParameterField.createValueField(FLAG_NAME))
                                                        .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    Call<FmeResponse<FeatureFlagDefinition>> reallocateCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> reallocateResponse = mock(Response.class);
    ResponseBody errorBody = mock(ResponseBody.class);

    when(reallocateCall.execute()).thenReturn(reallocateResponse);
    when(reallocateResponse.isSuccessful()).thenReturn(false);
    when(reallocateResponse.errorBody()).thenReturn(errorBody);
    when(errorBody.string()).thenReturn("API error message");

    when(fmePipelineClient.reallocateFeatureFlag(
             eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(reallocateCall);

    // Act
    StepResponse stepResponse = fmeFlagReallocateTrafficStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1)).reallocateFeatureFlag(FLAG_NAME, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingEnvironment() {
    FmeFlagReallocateTrafficParameters stepParams =
        FmeFlagReallocateTrafficParameters.builder().flagName(ParameterField.createValueField(FLAG_NAME)).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Act
    StepResponse response = fmeFlagReallocateTrafficStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingFlagName() {
    FmeFlagReallocateTrafficParameters stepParams =
        FmeFlagReallocateTrafficParameters.builder().environment(ParameterField.createValueField(ENVIRONMENT)).build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(stepParams);

    // Act
    StepResponse response = fmeFlagReallocateTrafficStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }
}
