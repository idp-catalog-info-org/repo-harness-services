/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.SegmentMetadataExternal;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import io.split.client.dtos.URN;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Unit tests for FmeSegmentUpdateStep.
 *
 * Tests the NO-OP semantics:
 * - Field OMITTED => not in patch => server preserves existing (NO CHANGE)
 * - Field PRESENT with non-empty value => in patch => server updates
 * - Field PRESENT but EMPTY => NO CHANGE (skip, no patch op)
 * - Runtime expression => treated as specified (proceeds with update)
 * - Field is unresolved runtime input without default => exception
 */
@Category(UnitTests.class)
public class FmeSegmentUpdateStepTest {
  private FmeSegmentUpdateStep step;
  private Method buildPatchMethod;

  @Before
  public void setUp() throws Exception {
    step = new FmeSegmentUpdateStep();

    FmeOwnerResolver mockResolver = mock(FmeOwnerResolver.class);
    when(mockResolver.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(step, "fmeOwnerResolver", mockResolver);

    // Use reflection to access private buildPatch method for testing
    buildPatchMethod = FmeSegmentUpdateStep.class.getDeclaredMethod("buildPatch", FmeSegmentUpdateParameters.class);
    buildPatchMethod.setAccessible(true);
  }

  /**
   * Helper to invoke buildPatch via reflection
   */
  @SuppressWarnings("unchecked")
  private List<FmePatchOperation> buildPatch(FmeSegmentUpdateParameters params) throws Exception {
    return (List<FmePatchOperation>) buildPatchMethod.invoke(step, params);
  }

  // ==================== A. buildPatch returns empty ====================

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_NoFieldsProvided_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            // All optional fields omitted
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_AllFieldsEmpty_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params =
        FmeSegmentUpdateParameters.builder()
            .name(ParameterField.createValueField("test-segment"))
            .description(ParameterField.createValueField("")) // Empty string
            .tags(ParameterField.createValueField(Collections.emptyList())) // Empty list
            .owners(ParameterField.createValueField(Collections.emptyList())) // Empty list
            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_EmptyDescription_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField(""))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_WhitespaceDescription_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("   "))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_EmptyTags_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .tags(ParameterField.createValueField(Collections.emptyList()))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_EmptyOwners_ReturnsEmpty() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .owners(ParameterField.createValueField(Collections.emptyList()))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_NullValue_ReturnsEmpty() throws Exception {
    ParameterField<List<String>> nullTags = ParameterField.createValueField(null);

    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .tags(nullTags)
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  // ==================== B. buildPatch contains correct patch ops ====================

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_DescriptionWithValue_CreatesReplaceOp() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("my description"))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getOp()).isEqualTo("replace");
    assertThat(patch.get(0).getPath()).isEqualTo("/description");
    assertThat(patch.get(0).getValue()).isEqualTo("my description");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_TagsWithValue_CreatesReplaceOp() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getOp()).isEqualTo("replace");
    assertThat(patch.get(0).getPath()).isEqualTo("/tags");
    @SuppressWarnings("unchecked") List<URN> tagUrns = (List<URN>) patch.get(0).getValue();
    assertThat(tagUrns).hasSize(2);
    assertThat(tagUrns.get(0).type).isEqualTo("Tag");
    assertThat(tagUrns.get(0).name).isEqualTo("tag1");
    assertThat(tagUrns.get(1).type).isEqualTo("Tag");
    assertThat(tagUrns.get(1).name).isEqualTo("tag2");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_OwnersWithValue_CreatesReplaceOp() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .owners(ParameterField.createValueField(Arrays.asList("user1", "user2")))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getOp()).isEqualTo("replace");
    assertThat(patch.get(0).getPath()).isEqualTo("/owners");
    @SuppressWarnings("unchecked") List<URN> ownerUrns = (List<URN>) patch.get(0).getValue();
    assertThat(ownerUrns).hasSize(2);
    assertThat(ownerUrns.get(0).type).isEqualTo("User");
    assertThat(ownerUrns.get(0).id).isEqualTo("user1");
    assertThat(ownerUrns.get(1).type).isEqualTo("User");
    assertThat(ownerUrns.get(1).id).isEqualTo("user2");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_AllFieldsWithValues_CreatesAllOps() throws Exception {
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .tags(ParameterField.createValueField(Arrays.asList("tag1")))
                                            .description(ParameterField.createValueField("desc"))
                                            .owners(ParameterField.createValueField(Arrays.asList("owner1")))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(3);
    assertThat(patch.stream().anyMatch(op -> "/tags".equals(op.getPath()))).isTrue();
    assertThat(patch.stream().anyMatch(op -> "/description".equals(op.getPath()))).isTrue();
    assertThat(patch.stream().anyMatch(op -> "/owners".equals(op.getPath()))).isTrue();
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_MixOmittedAndEmpty_CreatesOnlyNonEmptyOps() throws Exception {
    FmeSegmentUpdateParameters params =
        FmeSegmentUpdateParameters.builder()
            .name(ParameterField.createValueField("test-segment"))
            .tags(ParameterField.createValueField(Collections.emptyList())) // Empty = NO CHANGE
            .description(ParameterField.createValueField("new desc")) // Update description
            // owners omitted - preserve existing
            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    // Only description should create a patch op
    assertThat(patch).hasSize(1);
    assertThat(patch.stream().noneMatch(op -> "/tags".equals(op.getPath()))).isTrue();
    assertThat(patch.stream().anyMatch(op -> "/description".equals(op.getPath()))).isTrue();
    assertThat(patch.stream().noneMatch(op -> "/owners".equals(op.getPath()))).isTrue();
  }

  // ==================== C. buildPatch includes ops when field is expression ====================

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_DescriptionExpression_CreatesOp() throws Exception {
    // Expression with default value
    ParameterField<String> descExpression =
        ParameterField.createFieldWithDefaultValue(true, true, "<+input>", "default desc", null, true);

    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(descExpression)
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getPath()).isEqualTo("/description");
    assertThat(patch.get(0).getValue()).isEqualTo("default desc");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_TagsExpression_CreatesOp() throws Exception {
    // Expression with default value
    ParameterField<List<String>> tagsExpression = ParameterField.createFieldWithDefaultValue(
        true, true, "<+input.tags>", Arrays.asList("default-tag"), null, false);

    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .tags(tagsExpression)
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getPath()).isEqualTo("/tags");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_OwnersExpression_CreatesOp() throws Exception {
    // Expression with default value
    ParameterField<List<String>> ownersExpression = ParameterField.createFieldWithDefaultValue(
        true, true, "<+input.owners>", Arrays.asList("default-user"), null, false);

    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .owners(ownersExpression)
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getPath()).isEqualTo("/owners");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_ExpressionWithoutDefault_ThrowsException() {
    // Expression without default - should throw when trying to resolve
    ParameterField<String> descExpression = ParameterField.createExpressionField(true, "<+input>", null, true);

    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(descExpression)
                                            .build();

    assertThatThrownBy(() -> buildPatch(params))
        .hasCauseInstanceOf(FmeInvalidParameterException.class)
        .hasRootCauseMessage(
            "description is a runtime input/expression but was not resolved and has no default value.");
  }

  // ==================== D. executeSyncAfterRbac tests ====================

  @Test
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac_EmptyPatch_ReturnsSkipped_NoApiCall() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FMEPipelineClient fmeClient = mock(FMEPipelineClient.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Inject mocks using reflection
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmePipelineClient", fmeClient);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver1 = mock(FmeOwnerResolver.class);
    when(resolver1.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver1);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with all fields omitted (will result in empty patch)
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            // All optional fields omitted
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SKIPPED);
    // Verify API client was NOT called
    verify(fmeClient, never()).patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac_AllFieldsEmpty_ReturnsSkipped_NoApiCall() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FMEPipelineClient fmeClient = mock(FMEPipelineClient.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Inject mocks using reflection
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmePipelineClient", fmeClient);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver2 = mock(FmeOwnerResolver.class);
    when(resolver2.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver2);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with all fields empty (will result in empty patch)
    FmeSegmentUpdateParameters params =
        FmeSegmentUpdateParameters.builder()
            .name(ParameterField.createValueField("test-segment"))
            .description(ParameterField.createValueField("")) // Empty string
            .tags(ParameterField.createValueField(Collections.emptyList())) // Empty list
            .owners(ParameterField.createValueField(Collections.emptyList())) // Empty list
            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SKIPPED);
    // Verify API client was NOT called
    verify(fmeClient, never()).patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac_NonEmptyPatch_CallsApi_ReturnsSucceeded() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FMEPipelineClient fmeClient = mock(FMEPipelineClient.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Mock API call
    Call<SegmentMetadataExternal> mockCall = mock(Call.class);
    Response<SegmentMetadataExternal> mockResponse = mock(Response.class);
    SegmentMetadataExternal segmentMetadata =
        SegmentMetadataExternal.builder().name("test-segment").description("new description").build();
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(true);
    when(mockResponse.body()).thenReturn(segmentMetadata);
    when(fmeClient.patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(mockCall);

    // Inject mocks using reflection
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmePipelineClient", fmeClient);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver3 = mock(FmeOwnerResolver.class);
    when(resolver3.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver3);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with non-empty description (will result in non-empty patch)
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("new description"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    // Verify API client WAS called once with expected args
    verify(fmeClient, times(1))
        .patchSegmentMetadata(eq("accountId"), eq("orgId"), eq("projectId"), eq("test-segment"), anyList());
  }

  // ==================== E. Parameter validation tests ====================

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_SingleTag_CreatesOp() throws Exception {
    FmeSegmentUpdateParameters params =
        FmeSegmentUpdateParameters.builder()
            .name(ParameterField.createValueField("test-segment"))
            .tags(ParameterField.createValueField(Collections.singletonList("single-tag")))
            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getPath()).isEqualTo("/tags");
    @SuppressWarnings("unchecked") List<URN> tagUrns = (List<URN>) patch.get(0).getValue();
    assertThat(tagUrns).hasSize(1);
    assertThat(tagUrns.get(0).name).isEqualTo("single-tag");
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_SingleOwner_CreatesOp() throws Exception {
    FmeSegmentUpdateParameters params =
        FmeSegmentUpdateParameters.builder()
            .name(ParameterField.createValueField("test-segment"))
            .owners(ParameterField.createValueField(Collections.singletonList("single-owner")))
            .build();

    List<FmePatchOperation> patch = buildPatch(params);

    assertThat(patch).hasSize(1);
    assertThat(patch.get(0).getPath()).isEqualTo("/owners");
    @SuppressWarnings("unchecked") List<URN> ownerUrns = (List<URN>) patch.get(0).getValue();
    assertThat(ownerUrns).hasSize(1);
    assertThat(ownerUrns.get(0).id).isEqualTo("single-owner");
  }

  @Test
  @Category(UnitTests.class)
  public void testGetRequiredSegmentName_MissingName_ThrowsException() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();
    ExceptionManager exceptionManager = mock(ExceptionManager.class);
    responseBuilder.setExceptionManager(exceptionManager);

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Inject mocks
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver4 = mock(FmeOwnerResolver.class);
    when(resolver4.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver4);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with NULL name
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(null) // Missing name
                                            .description(ParameterField.createValueField("some description"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute and verify it returns FAILED (because exception is caught internally)
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // The step catches exceptions and returns FAILED status
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetRequiredSegmentName_EmptyName_ThrowsException() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();
    ExceptionManager exceptionManager = mock(ExceptionManager.class);
    responseBuilder.setExceptionManager(exceptionManager);

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Inject mocks
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver5 = mock(FmeOwnerResolver.class);
    when(resolver5.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver5);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with EMPTY name
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("")) // Empty name
                                            .description(ParameterField.createValueField("some description"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute and verify it returns FAILED
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac_ApiReturnsError_ReturnsFailed() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FMEPipelineClient fmeClient = mock(FMEPipelineClient.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();
    ExceptionManager exceptionManager = mock(ExceptionManager.class);
    responseBuilder.setExceptionManager(exceptionManager);

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Mock API call to return unsuccessful response
    Call<SegmentMetadataExternal> mockCall = mock(Call.class);
    Response<SegmentMetadataExternal> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(false);
    when(mockResponse.code()).thenReturn(500);
    when(mockResponse.errorBody()).thenReturn(null);
    when(fmeClient.patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(mockCall);

    // Inject mocks
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmePipelineClient", fmeClient);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver6 = mock(FmeOwnerResolver.class);
    when(resolver6.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver6);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters with valid data
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("new description"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify failure
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    verify(fmeClient, times(1)).patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbac_ApiThrowsIOException_ReturnsFailed() throws Exception {
    // Setup mocks
    FmeSegmentUpdateStep stepWithMocks = new FmeSegmentUpdateStep();
    LogStreamingStepClientFactory logFactory = mock(LogStreamingStepClientFactory.class);
    FMEPipelineClient fmeClient = mock(FMEPipelineClient.class);
    FmeStepResponseBuilder responseBuilder = new FmeStepResponseBuilder();
    ExceptionManager exceptionManager = mock(ExceptionManager.class);
    responseBuilder.setExceptionManager(exceptionManager);

    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    // Mock API call to throw IOException
    Call<SegmentMetadataExternal> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new java.io.IOException("Connection refused"));
    when(fmeClient.patchSegmentMetadata(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(mockCall);

    // Inject mocks
    injectField(stepWithMocks, "logStreamingStepClientFactory", logFactory);
    injectField(stepWithMocks, "fmePipelineClient", fmeClient);
    injectField(stepWithMocks, "fmeStepResponseBuilder", responseBuilder);
    FmeOwnerResolver resolver7 = mock(FmeOwnerResolver.class);
    when(resolver7.resolveOwners(any())).thenAnswer(invocation -> invocation.getArgument(0));
    injectField(stepWithMocks, "fmeOwnerResolver", resolver7);

    // Setup ambiance
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgId")
                            .putSetupAbstractions("projectIdentifier", "projectId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    // Setup parameters
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("new description"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    // Execute
    StepResponse response =
        stepWithMocks.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    // Verify failure due to IOException
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Category(UnitTests.class)
  public void testBuildPatch_DescriptionOnlyWhitespaceInnerList_ReturnsEmpty() throws Exception {
    // Test with description that is only tabs/newlines (edge case)
    FmeSegmentUpdateParameters params = FmeSegmentUpdateParameters.builder()
                                            .name(ParameterField.createValueField("test-segment"))
                                            .description(ParameterField.createValueField("\t\n"))
                                            .build();

    List<FmePatchOperation> patch = buildPatch(params);
    assertThat(patch).isEmpty();
  }

  private void injectField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
