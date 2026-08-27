/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.ROHITPAL;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import io.harness.fme.Segment;
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
import io.harness.steps.fme.exception.FmeInternalServerErrorException;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.FME)
@RunWith(MockitoJUnitRunner.class)
public class FmeSegmentCreateTest extends CategoryTest {
  @InjectMocks FmeSegmentCreate fmeSegmentCreate;
  private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private NGLogCallback ngLogCallback;
  @Mock private FMEPipelineClient fmePipelineClient;
  @Mock private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Mock private FmeOwnerResolver fmeOwnerResolver;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SEGMENT_NAME = "test-segment";
  private static final String TRAFFIC_TYPE = "user";
  private static final String DESCRIPTION = "Test segment description";

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

  // ==================== Success Scenarios ====================

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentSuccess() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    verify(fmePipelineClient, times(1))
        .createSegment(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentWithDescription() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment =
        Segment.builder().name(SEGMENT_NAME).description(DESCRIPTION).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result).isNotNull();
    assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentWithTags() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    io.split.client.dtos.URN tag1 = new io.split.client.dtos.URN();
    tag1.type = "Tag";
    tag1.name = "tag1";
    io.split.client.dtos.URN tag2 = new io.split.client.dtos.URN();
    tag2.type = "Tag";
    tag2.name = "tag2";

    Segment segment =
        Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").tags(List.of(tag1, tag2)).build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result).isNotNull();
    assertThat(result.getTags()).hasSize(2);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentWithOwners() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    io.split.client.dtos.URN owner1 = new io.split.client.dtos.URN();
    owner1.type = "User";
    owner1.id = "user1";
    io.split.client.dtos.URN owner2 = new io.split.client.dtos.URN();
    owner2.type = "User";
    owner2.id = "user2";

    Segment segment =
        Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").owners(List.of(owner1, owner2)).build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result).isNotNull();
    assertThat(result.getOwners()).hasSize(2);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentWithAllOptionalFields() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    io.split.client.dtos.URN tag = new io.split.client.dtos.URN();
    tag.type = "Tag";
    tag.name = "tag1";
    io.split.client.dtos.URN owner = new io.split.client.dtos.URN();
    owner.type = "User";
    owner.id = "user1";

    Segment segment = Segment.builder()
                          .name(SEGMENT_NAME)
                          .description(DESCRIPTION)
                          .segmentType("standard_segment")
                          .tags(List.of(tag))
                          .owners(List.of(owner))
                          .build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(SEGMENT_NAME);
    assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
    assertThat(result.getTags()).hasSize(1);
    assertThat(result.getOwners()).hasSize(1);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentStandardType() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(any(), any(), any(), any())).thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result.getSegmentType()).isEqualTo("standard_segment");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentLargeType() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("large_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(any(), any(), any(), any())).thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result.getSegmentType()).isEqualTo("large_segment");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentRuleBasedType() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("rule_based_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.body()).thenReturn(segment);
    when(mockResponse.isSuccessful()).thenReturn(true);

    when(fmePipelineClient.createSegment(any(), any(), any(), any())).thenReturn(mockCall);

    Segment result = fmeSegmentCreate.createSegment(ngLogCallback, scope, segment);

    assertThat(result.getSegmentType()).isEqualTo("rule_based_segment");
  }

  // ==================== Validation Error Scenarios ====================

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingName() {
    FmeSegmentCreateParameters params = FmeSegmentCreateParameters.builder()
                                            .name(ParameterField.createValueField(null))
                                            .trafficType(ParameterField.createValueField(TRAFFIC_TYPE))
                                            .segmentType(ParameterField.createValueField("Standard"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    when(fmeStepResponseBuilder.getFailedStepResponse(Mockito.anyLong(), Mockito.anyLong(), any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());

    StepResponse response =
        fmeSegmentCreate.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingTrafficType() {
    FmeSegmentCreateParameters params = FmeSegmentCreateParameters.builder()
                                            .name(ParameterField.createValueField(SEGMENT_NAME))
                                            .trafficType(ParameterField.createValueField(null))
                                            .segmentType(ParameterField.createValueField("Standard"))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    when(fmeStepResponseBuilder.getFailedStepResponse(Mockito.anyLong(), Mockito.anyLong(), any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());

    StepResponse response =
        fmeSegmentCreate.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testExecuteSyncAfterRbacMissingSegmentType() {
    FmeSegmentCreateParameters params = FmeSegmentCreateParameters.builder()
                                            .name(ParameterField.createValueField(SEGMENT_NAME))
                                            .trafficType(ParameterField.createValueField(TRAFFIC_TYPE))
                                            .segmentType(ParameterField.createValueField(null))
                                            .build();

    StepBaseParameters stepBaseParameters = mock(StepBaseParameters.class);
    when(stepBaseParameters.getSpec()).thenReturn(params);

    when(fmeStepResponseBuilder.getFailedStepResponse(Mockito.anyLong(), Mockito.anyLong(), any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());

    StepResponse response =
        fmeSegmentCreate.executeSyncAfterRbac(ambiance, stepBaseParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  // ==================== API Error Scenarios ====================

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentApiIOException() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new IOException("Network error"));

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    assertThatThrownBy(() -> fmeSegmentCreate.createSegment(ngLogCallback, scope, segment))
        .isInstanceOf(FmeInternalServerErrorException.class)
        .hasMessageContaining("Failed to communicate with FME API");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentApiErrorResponse() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(false);
    when(mockResponse.errorBody()).thenReturn(null);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    assertThatThrownBy(() -> fmeSegmentCreate.createSegment(ngLogCallback, scope, segment))
        .isInstanceOf(FmeInternalServerErrorException.class)
        .hasMessageContaining("FME Segment creation request failed");
  }

  @Test
  @Owner(developers = ROHITPAL)
  @Category(UnitTests.class)
  public void testCreateSegmentApiErrorResponseWithBody() throws Exception {
    Scope scope = ambianceToScope(ambiance);

    Segment segment = Segment.builder().name(SEGMENT_NAME).segmentType("standard_segment").build();

    Call<Segment> mockCall = mock(Call.class);
    Response<Segment> mockResponse = mock(Response.class);
    okhttp3.ResponseBody errorBody =
        okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "Segment already exists");

    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockResponse.isSuccessful()).thenReturn(false);
    when(mockResponse.errorBody()).thenReturn(errorBody);

    when(fmePipelineClient.createSegment(
             scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segment))
        .thenReturn(mockCall);

    assertThatThrownBy(() -> fmeSegmentCreate.createSegment(ngLogCallback, scope, segment))
        .isInstanceOf(FmeInternalServerErrorException.class)
        .hasMessageContaining("Segment already exists");
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
