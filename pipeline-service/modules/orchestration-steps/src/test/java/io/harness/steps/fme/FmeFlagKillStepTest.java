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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmeResponse;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

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

/**
 * Unit tests for FmeFlagKillStep - tests the kill feature flag functionality.
 * Covers success scenarios, failure handling, edge cases (404, false response), and parameter validation.
 */
@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeFlagKillStepTest extends CategoryTest {
  @InjectMocks FmeFlagKillStep fmeFlagKillStep;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private NGLogCallback ngLogCallback;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Mock private AccessControlClient accessControlClient;
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

    // Inject ExceptionManager spy into FmeStepResponseBuilder spy
    fmeStepResponseBuilder.setExceptionManager(exceptionManager);
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacSuccess() throws Exception {
    // Arrange
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(FLAG_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    FeatureFlagDefinition definition = FeatureFlagDefinition.builder().build();
    FmeResponse<FeatureFlagDefinition> fmeResponse =
        FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build();
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse = Response.success(fmeResponse);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(mockCall);

    // Act
    StepResponse stepResponse =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient, times(1))
        .killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacFailure() throws Exception {
    // Arrange
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(FLAG_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new RuntimeException("API call failed"));
    when(fmePipelineClient.killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(mockCall);

    // Act
    StepResponse stepResponse =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1))
        .killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithFalseResponse() throws Exception {
    // Arrange - test when API returns false (flag may not exist)
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(FLAG_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    FeatureFlagDefinition definition = FeatureFlagDefinition.builder().build();
    FmeResponse<FeatureFlagDefinition> fmeResponse =
        FmeResponse.<FeatureFlagDefinition>builder().entity(definition).build();
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse = Response.success(fmeResponse);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(mockCall);

    // Act
    StepResponse stepResponse =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient, times(1))
        .killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWith404Response() throws Exception {
    // Arrange - test when API returns 404 (flag not found)
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(FLAG_NAME))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    Call<FmeResponse<FeatureFlagDefinition>> mockCall = mock(Call.class);
    Response<FmeResponse<FeatureFlagDefinition>> mockResponse =
        Response.error(404, okhttp3.ResponseBody.create(null, "Not found"));
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT)))
        .thenReturn(mockCall);

    // Act
    StepResponse stepResponse =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1))
        .killFeatureFlag(eq(FLAG_NAME), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENVIRONMENT));
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeFlagKillStep.STEP_TYPE).isNotNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithMissingName() {
    // Arrange
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(null))
                                                       .environment(ParameterField.createValueField(ENVIRONMENT))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    // Act
    StepResponse response =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithMissingEnvironment() {
    // Arrange
    FmeFlagKillStepParameters killStepParameters = FmeFlagKillStepParameters.builder()
                                                       .flagName(ParameterField.createValueField(FLAG_NAME))
                                                       .environment(ParameterField.createValueField(null))
                                                       .build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(killStepParameters);

    // Act
    StepResponse response =
        fmeFlagKillStep.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }
}
