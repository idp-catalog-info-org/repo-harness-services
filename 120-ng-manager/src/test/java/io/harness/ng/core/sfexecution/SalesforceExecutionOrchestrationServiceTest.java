/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.BaseUrls;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.sfexecution.entity.SalesforceEvaluateDiffExecutionMetadata;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionEntity;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionStatus;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionType;
import io.harness.ng.core.sfexecution.services.SalesforceExecutionService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.SalesforceExecuteRequest;
import io.harness.spec.server.ng.v1.model.SalesforceExecution;
import io.harness.spec.server.pipeline.v1.model.ExecutionDetails;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteResponseBody;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDC)
public class SalesforceExecutionOrchestrationServiceTest extends CategoryTest {
  @Mock private SalesforceExecutionService salesforceExecutionService;
  @Mock private PipelineServiceClient pipelineServiceClient;
  @Mock private NextGenConfiguration nextGenConfiguration;

  @InjectMocks private SalesforceExecutionOrchestrationService service;

  private AutoCloseable mocks;

  private static final String ACCOUNT = "account123";
  private static final String ORG = "org123";
  private static final String PROJECT = "proj123";
  private static final String PIPELINE_ID = "myPipeline";
  private static final String PLAN_EXECUTION_ID = "planExec456";

  private SalesforceExecutionEntity inProgressEntity;
  private ScopeInfo scopeInfo;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    inProgressEntity = SalesforceExecutionEntity.builder()
                           .identifier("exec1")
                           .accountIdentifier(ACCOUNT)
                           .orgIdentifier(ORG)
                           .projectIdentifier(PROJECT)
                           .pipelineId(PIPELINE_ID)
                           .pipelineExecutionId(PLAN_EXECUTION_ID)
                           .type(SalesforceExecutionType.DEPLOY)
                           .status(SalesforceExecutionStatus.IN_PROGRESS)
                           .build();
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT)
                    .orgIdentifier(ORG)
                    .projectIdentifier(PROJECT)
                    .uniqueId("uniqueId")
                    .build();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_AlreadyDone() {
    inProgressEntity.setStatus(SalesforceExecutionStatus.SUCCEEDED);

    SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

    assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.SUCCEEDED);
    verify(pipelineServiceClient, never()).getExecutionDetailV2(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_ResolvesSucceeded() {
    Map<String, Object> summary = new HashMap<>();
    summary.put("status", "Success");
    Map<String, Object> response = new HashMap<>();
    response.put("pipelineExecutionSummary", summary);

    Call<ResponseDTO<Object>> call = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2(PLAN_EXECUTION_ID, ACCOUNT, ORG, PROJECT)).thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getResponse(call)).thenReturn(response);

      SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

      assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.SUCCEEDED);
      verify(salesforceExecutionService).updateStatus(ACCOUNT, PLAN_EXECUTION_ID, SalesforceExecutionStatus.SUCCEEDED);
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_ResolvesAborted() {
    Map<String, Object> summary = new HashMap<>();
    summary.put("status", "Aborted");
    Map<String, Object> response = new HashMap<>();
    response.put("pipelineExecutionSummary", summary);

    Call<ResponseDTO<Object>> call = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2(PLAN_EXECUTION_ID, ACCOUNT, ORG, PROJECT)).thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getResponse(call)).thenReturn(response);

      SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

      assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.ABORTED);
      verify(salesforceExecutionService).updateStatus(ACCOUNT, PLAN_EXECUTION_ID, SalesforceExecutionStatus.ABORTED);
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_StillInProgress() {
    Map<String, Object> summary = new HashMap<>();
    summary.put("status", "Running");
    Map<String, Object> response = new HashMap<>();
    response.put("pipelineExecutionSummary", summary);

    Call<ResponseDTO<Object>> call = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2(PLAN_EXECUTION_ID, ACCOUNT, ORG, PROJECT)).thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getResponse(call)).thenReturn(response);

      SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

      assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.IN_PROGRESS);
      verify(salesforceExecutionService, never()).updateStatus(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_ExceptionSuppressed() {
    Call<ResponseDTO<Object>> call = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2(PLAN_EXECUTION_ID, ACCOUNT, ORG, PROJECT)).thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getResponse(call)).thenThrow(new RuntimeException("timeout"));

      SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

      assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.IN_PROGRESS);
      verify(salesforceExecutionService, never()).updateStatus(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testResolveStatusIfInProgress_MalformedResponse_SuppressedAndStaysInProgress() {
    Call<ResponseDTO<Object>> call = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2(PLAN_EXECUTION_ID, ACCOUNT, ORG, PROJECT)).thenReturn(call);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      // Response is not a Map — extractStatusFromPipelineResponse throws IllegalStateException
      ngRestUtils.when(() -> NGRestUtils.getResponse(call)).thenReturn("not-a-map");

      SalesforceExecutionEntity result = service.resolveStatusIfInProgress(inProgressEntity);

      assertThat(result.getStatus()).isEqualTo(SalesforceExecutionStatus.IN_PROGRESS);
      verify(salesforceExecutionService, never()).updateStatus(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testExecute_DeployType() {
    SalesforceExecuteRequest request = new SalesforceExecuteRequest()
                                           .pipelineId(PIPELINE_ID)
                                           .type(SalesforceExecuteRequest.TypeEnum.DEPLOY)
                                           .changesetId("changeset-abc")
                                           .inputsYaml("inputs: {}");

    PipelineExecuteResponseBody pipelineResponse = new PipelineExecuteResponseBody();
    ExecutionDetails executionDetails = new ExecutionDetails();
    executionDetails.setExecutionId(PLAN_EXECUTION_ID);
    pipelineResponse.setExecutionDetails(executionDetails);

    SalesforceExecutionEntity savedEntity = SalesforceExecutionEntity.builder().identifier("saved-id").build();

    Call<PipelineExecuteResponseBody> executeCall = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.executePipeline(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(executeCall);
    when(salesforceExecutionService.create(any(SalesforceExecutionEntity.class), any(ScopeInfo.class)))
        .thenReturn(savedEntity);
    when(nextGenConfiguration.getBaseUrls()).thenReturn(null);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(executeCall)).thenReturn(pipelineResponse);

      SalesforceExecution result = service.execute(ACCOUNT, ORG, PROJECT, request, scopeInfo);

      assertThat(result).isNotNull();
      verify(salesforceExecutionService).create(any(SalesforceExecutionEntity.class), any(ScopeInfo.class));
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testExecute_EvaluateDiffType_SetsMetadata() {
    SalesforceExecuteRequest request = new SalesforceExecuteRequest()
                                           .pipelineId(PIPELINE_ID)
                                           .type(SalesforceExecuteRequest.TypeEnum.EVALUATE_DIFF)
                                           .changesetId("changeset-abc")
                                           .inputsYaml("inputs: {}");

    PipelineExecuteResponseBody pipelineResponse = new PipelineExecuteResponseBody();
    ExecutionDetails executionDetails = new ExecutionDetails();
    executionDetails.setExecutionId(PLAN_EXECUTION_ID);
    pipelineResponse.setExecutionDetails(executionDetails);

    when(nextGenConfiguration.getBaseUrls()).thenReturn(null);

    Call<PipelineExecuteResponseBody> executeCall = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.executePipeline(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(executeCall);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(executeCall)).thenReturn(pipelineResponse);

      org.mockito.ArgumentCaptor<SalesforceExecutionEntity> entityCaptor =
          org.mockito.ArgumentCaptor.forClass(SalesforceExecutionEntity.class);
      when(salesforceExecutionService.create(entityCaptor.capture(), any())).thenAnswer(inv -> inv.getArgument(0));

      service.execute(ACCOUNT, ORG, PROJECT, request, scopeInfo);

      SalesforceExecutionEntity captured = entityCaptor.getValue();
      assertThat(captured.getType()).isEqualTo(SalesforceExecutionType.EVALUATE_DIFF);
      assertThat(captured.getMetadata()).isInstanceOf(SalesforceEvaluateDiffExecutionMetadata.class);
      assertThat(captured.getMetadata().getChangesetIdentifier()).isEqualTo("changeset-abc");
    }
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testExecute_NullChangesetId_ThrowsException() {
    SalesforceExecuteRequest request = new SalesforceExecuteRequest()
                                           .pipelineId(PIPELINE_ID)
                                           .type(SalesforceExecuteRequest.TypeEnum.DEPLOY)
                                           .inputsYaml("inputs: {}");
    assertThatThrownBy(() -> service.execute(ACCOUNT, ORG, PROJECT, request, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("changesetId is required");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testExecute_EmptyChangesetId_ThrowsException() {
    SalesforceExecuteRequest request = new SalesforceExecuteRequest()
                                           .pipelineId(PIPELINE_ID)
                                           .type(SalesforceExecuteRequest.TypeEnum.DEPLOY)
                                           .changesetId("")
                                           .inputsYaml("inputs: {}");
    assertThatThrownBy(() -> service.execute(ACCOUNT, ORG, PROJECT, request, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("changesetId is required");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testBuildExecutionUrl_WithTrailingSlash() {
    BaseUrls baseUrls = BaseUrls.builder().nextGenUiUrl("https://app.harness.io/").build();
    when(nextGenConfiguration.getBaseUrls()).thenReturn(baseUrls);

    SalesforceExecuteRequest request = new SalesforceExecuteRequest()
                                           .pipelineId(PIPELINE_ID)
                                           .type(SalesforceExecuteRequest.TypeEnum.DEPLOY)
                                           .changesetId("changeset-abc")
                                           .inputsYaml("inputs: {}");

    PipelineExecuteResponseBody pipelineResponse = new PipelineExecuteResponseBody();
    ExecutionDetails executionDetails = new ExecutionDetails();
    executionDetails.setExecutionId(PLAN_EXECUTION_ID);
    pipelineResponse.setExecutionDetails(executionDetails);

    Call<PipelineExecuteResponseBody> executeCall = org.mockito.Mockito.mock(Call.class);
    when(pipelineServiceClient.executePipeline(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(executeCall);
    when(salesforceExecutionService.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(executeCall)).thenReturn(pipelineResponse);
      // Just verify no exception is thrown and entity is created (URL is logged, not returned)
      service.execute(ACCOUNT, ORG, PROJECT, request, scopeInfo);
      verify(salesforceExecutionService).create(any(), any());
    }
  }
}
