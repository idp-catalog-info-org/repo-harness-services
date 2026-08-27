/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.ZANINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.FailureType;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ExecutionInfoUpdateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;

  private ExecutionInfoUpdateEventHandler executionInfoUpdateEventHandler;

  @Before
  public void setUp() {
    executionInfoUpdateEventHandler = new ExecutionInfoUpdateEventHandler(pmsPipelineService, planExecutionService,
        recentExecutionsInfoHelper, scopeResolutionHelper, pmsExecutionSummaryService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdate() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.FAILED).build());

    ArgumentCaptor<ExecutionSummaryInfo> captor = ArgumentCaptor.forClass(ExecutionSummaryInfo.class);
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), captor.capture(), anyBoolean());

    executionInfoUpdateEventHandler.onPlanStatusUpdate(ambiance);

    ExecutionSummaryInfo value = captor.getValue();
    assertThat(value.getLastExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(value.getNumOfErrors()).isNotEmpty();

    assertThat(value.getNumOfErrors().get(getFormattedDate())).isEqualTo(1);

    ArgumentCaptor<Boolean> flagCaptor = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<FailureInfo> failureInfoCaptor = ArgumentCaptor.forClass(FailureInfo.class);
    verify(recentExecutionsInfoHelper)
        .onExecutionUpdate(any(), any(), any(ScopeInfo.class), flagCaptor.capture(), failureInfoCaptor.capture());

    assertThat(flagCaptor.getValue()).isTrue();
    assertThat(failureInfoCaptor.getValue()).isNull();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void shouldResolveParentUniqueIdFromAmbianceWhenPresent() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .putSetupAbstractions(SetupAbstractionKeys.parentUniqueId, "unique-id")
                            .build();

    String result = executionInfoUpdateEventHandler.resolveParentUniqueId(ambiance, "accId", "orgId", "projId");

    assertThat(result).isEqualTo("unique-id");
    verify(scopeResolutionHelper, never()).getParentUniqueId(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void shouldFallbackToScopeResolutionHelperWhenParentUniqueIdMissing() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();

    when(scopeResolutionHelper.getParentUniqueId("accId", "orgId", "projId")).thenReturn("resolved-unique-id");

    String result = executionInfoUpdateEventHandler.resolveParentUniqueId(ambiance, "accId", "orgId", "projId");

    assertThat(result).isEqualTo("resolved-unique-id");
    verify(scopeResolutionHelper).getParentUniqueId("accId", "orgId", "projId");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void shouldReturnNullWhenBothAmbianceAndFallbackHaveNoParentUniqueId() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();

    when(scopeResolutionHelper.getParentUniqueId("accId", "orgId", "projId")).thenReturn(null);

    String result = executionInfoUpdateEventHandler.resolveParentUniqueId(ambiance, "accId", "orgId", "projId");

    assertThat(result).isNull();
    verify(scopeResolutionHelper).getParentUniqueId("accId", "orgId", "projId");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdateWithMissingParentUniqueId() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .uuid(generateUuid())
                                        .executionSummaryInfo(ExecutionSummaryInfo.builder()
                                                                  .lastExecutionStatus(ExecutionStatus.RUNNING)
                                                                  .numOfErrors(new HashMap<>())
                                                                  .build())
                                        .build();

    when(scopeResolutionHelper.getParentUniqueId("accId", "orgId", "projId")).thenReturn("resolved-unique-id");
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.ABORTED).build());

    ArgumentCaptor<ExecutionSummaryInfo> captor = ArgumentCaptor.forClass(ExecutionSummaryInfo.class);
    doNothing().when(pmsPipelineService).saveExecutionInfo(any(), anyString(), captor.capture(), anyBoolean());

    executionInfoUpdateEventHandler.onPlanStatusUpdate(ambiance);

    verify(scopeResolutionHelper).getParentUniqueId("accId", "orgId", "projId");

    ExecutionSummaryInfo value = captor.getValue();
    assertThat(value.getLastExecutionStatus()).isEqualTo(ExecutionStatus.ABORTED);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void shouldResolveFailureInfoFromSummaryWhenPlanExecutionHasNoFailureInfo() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();
    when(planExecutionService.getWithFieldsIncludedOptional(eq(planExecutionId), anySet()))
        .thenReturn(Optional.of(PlanExecution.builder().uuid(planExecutionId).build()));
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
             eq("accId"), eq(planExecutionId), anySet()))
        .thenReturn(PipelineExecutionSummaryEntity.builder()
                        .failureInfo(FailureInfoDTO.builder()
                                         .message("pipeline failed")
                                         .failureTypeList(EnumSet.of(FailureType.APPLICATION_ERROR))
                                         .build())
                        .build());

    FailureInfo result = executionInfoUpdateEventHandler.resolveFailureInfo("accId", planExecution);

    assertThat(result.getErrorMessage()).isEqualTo("pipeline failed");
    assertThat(result.getFailureTypesList())
        .containsExactly(io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE);
  }

  private String getFormattedDate() {
    Date date = new Date();
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
    return formatter.format(date);
  }
}
