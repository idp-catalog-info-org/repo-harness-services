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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
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

import java.io.IOException;
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
 * Unit tests for FmeSegmentDeleteStep - tests the delete segment functionality.
 * Covers success scenarios, failure handling, edge cases (404, API errors), and parameter validation.
 */
@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeSegmentDeleteStepTest extends CategoryTest {
  @InjectMocks FmeSegmentDeleteStep fmeSegmentDeleteStep;
  private Ambiance ambiance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Spy private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Spy private ExceptionManager exceptionManager;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SEGMENT_NAME = "test-segment";

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

  // ==================== Success Scenarios ====================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacSuccess() throws Exception {
    // Arrange
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField(SEGMENT_NAME)).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Boolean> mockCall = mock(Call.class);
    Response<Boolean> mockResponse = Response.success(true);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME)))
        .thenReturn(mockCall);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(fmePipelineClient, times(1)).deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME));
  }

  // ==================== Failure Scenarios ====================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacFailure() throws Exception {
    // Arrange
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField(SEGMENT_NAME)).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Boolean> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new IOException("Network error"));
    when(fmePipelineClient.deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME)))
        .thenReturn(mockCall);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1)).deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME));
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWith404Response() throws Exception {
    // Arrange - test when API returns 404 (segment not found)
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField(SEGMENT_NAME)).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Boolean> mockCall = mock(Call.class);
    Response<Boolean> mockResponse = Response.error(404, okhttp3.ResponseBody.create(null, "Not found"));
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME)))
        .thenReturn(mockCall);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1)).deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME));
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacWithErrorBody() throws Exception {
    // Arrange - test when API returns error with body
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField(SEGMENT_NAME)).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    Call<Boolean> mockCall = mock(Call.class);
    Response<Boolean> mockResponse = Response.error(400,
        okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "Segment has active definitions"));
    when(mockCall.execute()).thenReturn(mockResponse);
    when(fmePipelineClient.deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME)))
        .thenReturn(mockCall);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmePipelineClient, times(1)).deleteSegment(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SEGMENT_NAME));
  }

  // ==================== Validation Error Scenarios ====================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingName() {
    // Arrange
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField(null)).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacEmptyName() {
    // Arrange
    FmeSegmentDeleteParameters params =
        FmeSegmentDeleteParameters.builder().name(ParameterField.createValueField("")).build();
    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Act
    StepResponse response = fmeSegmentDeleteStep.executeSyncAfterRbac(
        ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Assert - expect Status.FAILED
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo()).isNotNull();
  }

  // ==================== Misc ====================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(FmeSegmentDeleteStep.STEP_TYPE).isNotNull();
  }
}
