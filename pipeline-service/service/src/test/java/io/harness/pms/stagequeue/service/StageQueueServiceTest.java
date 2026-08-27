/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NEGI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.delegate.FailedRunnerTransaction;
import io.harness.delegate.ListRunnerTransactionsResponse;
import io.harness.delegate.RunnerTransaction;
import io.harness.delegate.RunnerTransactionMetadata;
import io.harness.delegate.RunnerTransactionPriority;
import io.harness.delegate.RunnerTransactionStatus;
import io.harness.delegate.RunnerTransactionStatusFilter;
import io.harness.delegate.RunnerTransactions;
import io.harness.delegate.UpdatePriorityFailureReason;
import io.harness.delegate.UpdateRunnerTransactionsPriorityResponse;
import io.harness.delegate.UpdatedRunnerTransaction;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.stagequeue.beans.StageQueueListResponse;
import io.harness.pms.stagequeue.beans.StageQueuePriority;
import io.harness.pms.stagequeue.beans.StageQueueRow;
import io.harness.pms.stagequeue.beans.StageQueueStatus;
import io.harness.pms.stagequeue.beans.StageSelectorDTO;
import io.harness.pms.stagequeue.beans.UpdatePriorityResponse;
import io.harness.pms.stagequeue.client.RunnerTransactionsServiceClient;
import io.harness.rule.Owner;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class StageQueueServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PLAN_EXEC_ID = "planExecId";
  private static final String STAGE_ID = "stage1";
  private static final String STAGE_RUNTIME_ID_1 = "stageRuntime1";
  private static final String STAGE_RUNTIME_ID_2 = "stageRuntime2";

  @Mock private RunnerTransactionsServiceClient runnerTransactionsClient;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PMSExecutionService pmsExecutionService;

  private StageQueueService service;

  @Before
  public void setUp() {
    service = new StageQueueService(runnerTransactionsClient, nodeExecutionService, pmsExecutionService);
  }

  // ==========================================================================
  // list() — validation
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_limitZero_throwsInvalidRequest() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThatThrownBy(() -> service.list(scope, StageQueueStatus.ALL, 0, 0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("limit");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_limitNegative_throwsInvalidRequest() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThatThrownBy(() -> service.list(scope, StageQueueStatus.ALL, 0, -1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("limit");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_limitExceedsMax_throwsInvalidRequest() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThatThrownBy(() -> service.list(scope, StageQueueStatus.ALL, 0, 101))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("limit");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_pageNegative_throwsInvalidRequest() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThatThrownBy(() -> service.list(scope, StageQueueStatus.ALL, -1, 10))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("page");
  }

  // ==========================================================================
  // list() — proto status filter mapping
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_nullStatusFilter_mapsToStatusFilterAll() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(RunnerTransactionStatusFilter.STATUS_FILTER_ALL), eq(0), eq(10)))
        .thenReturn(emptyResponse(0, 10));

    service.list(scope, null, 0, 10);

    verify(runnerTransactionsClient)
        .list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(RunnerTransactionStatusFilter.STATUS_FILTER_ALL), eq(0),
            eq(10));
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_queuedStatus_mapsToStatusFilterQueued() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(RunnerTransactionStatusFilter.STATUS_FILTER_QUEUED), eq(0), eq(10)))
        .thenReturn(emptyResponse(0, 10));

    service.list(scope, StageQueueStatus.QUEUED, 0, 10);

    verify(runnerTransactionsClient)
        .list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(RunnerTransactionStatusFilter.STATUS_FILTER_QUEUED), eq(0),
            eq(10));
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_runningStatus_mapsToStatusFilterRunning() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(RunnerTransactionStatusFilter.STATUS_FILTER_RUNNING), eq(0), eq(10)))
        .thenReturn(emptyResponse(0, 10));

    service.list(scope, StageQueueStatus.RUNNING, 0, 10);

    verify(runnerTransactionsClient)
        .list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(RunnerTransactionStatusFilter.STATUS_FILTER_RUNNING),
            eq(0), eq(10));
  }

  // ==========================================================================
  // list() — empty upstream
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_emptyUpstream_returnsEmptyResponse_withTotalsAndPaging() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    ListRunnerTransactionsResponse upstream = ListRunnerTransactionsResponse.newBuilder()
                                                  .setTransactions(RunnerTransactions.newBuilder().build())
                                                  .setTotalQueued(7)
                                                  .setTotalRunning(3)
                                                  .setTotalItems(10)
                                                  .setPage(2)
                                                  .setLimit(10)
                                                  .build();
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(), eq(2), eq(10)))
        .thenReturn(upstream);

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 2, 10);

    assertThat(resp.getStages()).isEmpty();
    assertThat(resp.getTotalQueued()).isEqualTo(7);
    assertThat(resp.getTotalRunning()).isEqualTo(3);
    assertThat(resp.getTotalItems()).isEqualTo(10);
    assertThat(resp.getPage()).isEqualTo(2);
    assertThat(resp.getLimit()).isEqualTo(10);
    verify(nodeExecutionService, never()).getAllWithFieldIncluded(any(), any());
    verify(pmsExecutionService, never()).fetchExecutionSummaries(anyString(), anyList(), anyList());
  }

  // ==========================================================================
  // list() — bulk reads
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_bulkReadsNodeExecutionByStageRuntimeIds_andSummariesByPlanExecId() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction queued = queuedTransaction(STAGE_RUNTIME_ID_1, "HIGH", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(queued), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(), eq(0), eq(10)))
        .thenReturn(upstream);
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.getAllWithFieldIncluded(eq(Set.of(STAGE_RUNTIME_ID_1)), any())).thenReturn(List.of(ne));
    when(pmsExecutionService.fetchExecutionSummaries(eq(ACCOUNT_ID), eq(List.of(PLAN_EXEC_ID)), any()))
        .thenReturn(List.of(summary(PLAN_EXEC_ID, "PipelineOne", "pipelineId")));

    service.list(scope, StageQueueStatus.ALL, 0, 10);

    verify(nodeExecutionService).getAllWithFieldIncluded(eq(Set.of(STAGE_RUNTIME_ID_1)), any());
    verify(pmsExecutionService).fetchExecutionSummaries(eq(ACCOUNT_ID), eq(List.of(PLAN_EXEC_ID)), any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_emptyStageRuntimeIds_doesNotInvokeFetchExecutionSummaries() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    // RunnerTransaction with metadata but empty stage_runtime_id (proto3 default).
    RunnerTransaction rt = RunnerTransaction.newBuilder()
                               .setStatus(RunnerTransactionStatus.QUEUED)
                               .setCreatedAt(100L)
                               .setMetadata(RunnerTransactionMetadata.newBuilder().build())
                               .setQueuePosition(1)
                               .build();
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any(), eq(0), eq(10)))
        .thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(eq(Set.of()), any())).thenReturn(List.of());

    service.list(scope, StageQueueStatus.ALL, 0, 10);

    verify(pmsExecutionService, never()).fetchExecutionSummaries(anyString(), anyList(), anyList());
  }

  // ==========================================================================
  // list() — order preservation & null-safe joins
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_preservesUpstreamOrder() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction first = queuedTransaction(STAGE_RUNTIME_ID_1, "HIGH", 100L, 1);
    RunnerTransaction second = queuedTransaction(STAGE_RUNTIME_ID_2, "NORMAL", 200L, 2);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(first, second), 2, 0, 2, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages()).hasSize(2);
    assertThat(resp.getStages().get(0).getCreatedAt()).isEqualTo(100L);
    assertThat(resp.getStages().get(1).getCreatedAt()).isEqualTo(200L);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_missingNodeExecution_leavesStageFieldsNull_butStillEmitsRow() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages()).hasSize(1);
    StageQueueRow row = resp.getStages().get(0);
    assertThat(row.getStageIdentifier()).isNull();
    assertThat(row.getStageName()).isNull();
    assertThat(row.getPipelineIdentifier()).isNull();
    assertThat(row.getStatus()).isEqualTo(StageQueueStatus.QUEUED);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_missingPipelineSummary_leavesPipelineFieldsNull_butStillEmitsRow() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of(ne));
    when(pmsExecutionService.fetchExecutionSummaries(anyString(), anyList(), anyList())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    StageQueueRow row = resp.getStages().get(0);
    assertThat(row.getStageIdentifier()).isEqualTo(STAGE_ID);
    assertThat(row.getStageName()).isEqualTo("Stage 1");
    assertThat(row.getPipelineIdentifier()).isNull();
    assertThat(row.getPipelineName()).isNull();
    assertThat(row.getPipelineExecutionId()).isNull();
  }

  // ==========================================================================
  // list() — queue position stamping
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_queuedRow_stampsQueuePositionFromProto() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 42);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages().get(0).getQueuePosition()).isEqualTo(42);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_queuedRow_withoutQueuePosition_remainsNull() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    // RunnerTransaction with status QUEUED but no queue_position set on the wire.
    RunnerTransaction rt = RunnerTransaction.newBuilder()
                               .setTransactionId("txn-1")
                               .setStatus(RunnerTransactionStatus.QUEUED)
                               .setPriority("NORMAL")
                               .setCreatedAt(100L)
                               .setMetadata(metadataFor(STAGE_RUNTIME_ID_1))
                               .build();
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages().get(0).getQueuePosition()).isNull();
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_runningRow_doesNotStampQueuePosition_evenIfProtoCarriesIt() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = RunnerTransaction.newBuilder()
                               .setTransactionId("txn-1")
                               .setStatus(RunnerTransactionStatus.RUNNING)
                               .setExecutingOnRunnerId("r1")
                               .setExecutingOnRunnerName("runner-1")
                               .setExecutingOnRunnerHostName("host-1")
                               .setCreatedAt(200L)
                               .setMetadata(metadataFor(STAGE_RUNTIME_ID_1))
                               .setQueuePosition(99)
                               .build();
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 0, 1, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages().get(0).getQueuePosition()).isNull();
  }

  // ==========================================================================
  // list() — row construction (queued vs running fields)
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_queuedRow_carriesPriorityAndEligibleDelegates_andQueuedDuration() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = RunnerTransaction.newBuilder()
                               .setTransactionId("txn-1")
                               .setStatus(RunnerTransactionStatus.QUEUED)
                               .setPriority("HIGH")
                               .setCreatedAt(100L)
                               .setMetadata(metadataFor(STAGE_RUNTIME_ID_1))
                               .setQueuePosition(1)
                               .addEligibleToExecuteRunnerNames("r1-name")
                               .addEligibleToExecuteRunnerNames("r2-name")
                               .addEligibleToExecuteRunnerHostNames("r1-host")
                               .addEligibleToExecuteRunnerHostNames("r2-host")
                               .build();
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    StageQueueRow row = resp.getStages().get(0);
    assertThat(row.getPriority()).isEqualTo(StageQueuePriority.HIGH);
    assertThat(row.getEligibleDelegates()).hasSize(2);
    assertThat(row.getEligibleDelegates().get(0).getName()).isEqualTo("r1-name");
    assertThat(row.getEligibleDelegates().get(0).getHostName()).isEqualTo("r1-host");
    assertThat(row.getQueuedDurationMs()).isNotNull();
    assertThat(row.getQueuedDurationMs()).isGreaterThanOrEqualTo(0L);
    assertThat(row.getQueuedDuration()).isNotNull();
    assertThat(row.getExecutingDelegate()).isNull();
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_runningRow_carriesExecutingDelegate_andOmitsPriorityAndQueueFields() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = RunnerTransaction.newBuilder()
                               .setTransactionId("txn-1")
                               .setStatus(RunnerTransactionStatus.RUNNING)
                               .setExecutingOnRunnerId("r1")
                               .setExecutingOnRunnerName("runner-1")
                               .setExecutingOnRunnerHostName("host-1")
                               .setCreatedAt(200L)
                               .setMetadata(metadataFor(STAGE_RUNTIME_ID_1))
                               .build();
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 0, 1, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    StageQueueRow row = resp.getStages().get(0);
    assertThat(row.getStatus()).isEqualTo(StageQueueStatus.RUNNING);
    assertThat(row.getExecutingDelegate()).isNotNull();
    assertThat(row.getExecutingDelegate().getName()).isEqualTo("runner-1");
    assertThat(row.getExecutingDelegate().getHostName()).isEqualTo("host-1");
    assertThat(row.getPriority()).isNull();
    assertThat(row.getEligibleDelegates()).isNull();
    assertThat(row.getQueuedDurationMs()).isNull();
    assertThat(row.getQueuedDuration()).isNull();
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_row_carriesOrgAndProjectFromProtoMetadata() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of());

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages().get(0).getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(resp.getStages().get(0).getProjectIdentifier()).isEqualTo(PROJECT_ID);
  }

  // ==========================================================================
  // list() — trigger info extraction
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_triggeredBy_populatedFromSummary() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of(ne));
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(PLAN_EXEC_ID)
            .name("PipelineOne")
            .pipelineIdentifier("pipelineId")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setIdentifier("alice")
                                                          .putExtraInfo("email", "alice@example.com")
                                                          .build())
                                      .build())
            .build();
    when(pmsExecutionService.fetchExecutionSummaries(anyString(), anyList(), anyList())).thenReturn(List.of(summary));

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    StageQueueRow row = resp.getStages().get(0);
    assertThat(row.getTriggeredBy()).isNotNull();
    assertThat(row.getTriggeredBy().getName()).isEqualTo("alice");
    assertThat(row.getTriggeredBy().getEmail()).isEqualTo("alice@example.com");
    assertThat(row.getTriggerType()).isEqualTo("MANUAL");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void list_triggeredBy_fallsBackToTriggerNameWhenIdentifierEmpty() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    RunnerTransaction rt = queuedTransaction(STAGE_RUNTIME_ID_1, "NORMAL", 100L, 1);
    ListRunnerTransactionsResponse upstream = listResponseWith(List.of(rt), 1, 0, 1, 0, 10);
    when(runnerTransactionsClient.list(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(upstream);
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.getAllWithFieldIncluded(any(), any())).thenReturn(List.of(ne));
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(PLAN_EXEC_ID)
            .pipelineIdentifier("pipelineId")
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setTriggerName("ci-trigger").build())
                                      .build())
            .build();
    when(pmsExecutionService.fetchExecutionSummaries(anyString(), anyList(), anyList())).thenReturn(List.of(summary));

    StageQueueListResponse resp = service.list(scope, StageQueueStatus.ALL, 0, 10);

    assertThat(resp.getStages().get(0).getTriggeredBy().getName()).isEqualTo("ci-trigger");
    assertThat(resp.getStages().get(0).getTriggerType()).isEqualTo("WEBHOOK");
  }

  // ==========================================================================
  // updatePriority() — empty/oversize selectors
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_nullStages_returnsEmptyResponse() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    UpdatePriorityResponse resp = service.updatePriority(scope, null, StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).isEmpty();
    verify(runnerTransactionsClient, never()).updatePriority(anyString(), anyList(), any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_emptyStages_returnsEmptyResponse() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    UpdatePriorityResponse resp = service.updatePriority(scope, List.of(), StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).isEmpty();
    verify(runnerTransactionsClient, never()).updatePriority(anyString(), anyList(), any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_exceedsMaxSelectors_throwsInvalidRequest() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      stages.add(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier("s" + i).build());
    }

    assertThatThrownBy(() -> service.updatePriority(scope, stages, StageQueuePriority.HIGH))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("10");
  }

  // ==========================================================================
  // updatePriority() — pre-classification (NOT_FOUND, OUT_OF_SCOPE, M6 tiebreak)
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_noLiveNodeExecution_classifiedAsNotFound() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    when(nodeExecutionService.findCurrentStageAttempts(eq(Set.of(PLAN_EXEC_ID)), eq(Set.of(STAGE_ID))))
        .thenReturn(List.of());

    UpdatePriorityResponse resp = service.updatePriority(scope, stages, StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.NOT_FOUND);
    verify(runnerTransactionsClient, never()).updatePriority(anyString(), anyList(), any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_nodeExecutionOutsideScope_classifiedAsOutOfScope() {
    // Scope requests org/project A but the NodeExecution carries org/project B.
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    NodeExecution ne = nodeExecutionWithScope(
        STAGE_RUNTIME_ID_1, STAGE_ID, PLAN_EXEC_ID, ACCOUNT_ID, "differentOrg", "differentProject");
    when(nodeExecutionService.findCurrentStageAttempts(eq(Set.of(PLAN_EXEC_ID)), eq(Set.of(STAGE_ID))))
        .thenReturn(List.of(ne));

    UpdatePriorityResponse resp = service.updatePriority(scope, stages, StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.OUT_OF_SCOPE);
    verify(runnerTransactionsClient, never()).updatePriority(anyString(), anyList(), any());
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_M6_tiebreaksOnLargestCreatedAt() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    NodeExecution older = nodeExecutionWithCreatedAt("olderUuid", STAGE_ID, PLAN_EXEC_ID, 100L);
    NodeExecution newer = nodeExecutionWithCreatedAt("newerUuid", STAGE_ID, PLAN_EXEC_ID, 200L);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(older, newer));
    when(runnerTransactionsClient.updatePriority(eq(ACCOUNT_ID), eq(List.of("newerUuid")), any()))
        .thenReturn(UpdateRunnerTransactionsPriorityResponse.newBuilder().build());

    service.updatePriority(scope, stages, StageQueuePriority.HIGH);

    ArgumentCaptor<List<String>> stageRuntimeIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(runnerTransactionsClient).updatePriority(eq(ACCOUNT_ID), stageRuntimeIdsCaptor.capture(), any());
    assertThat(stageRuntimeIdsCaptor.getValue()).containsExactly("newerUuid");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_allPreClassifiedFailed_skipsGrpcCall() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of());

    service.updatePriority(scope, stages, StageQueuePriority.HIGH);

    verify(runnerTransactionsClient, never()).updatePriority(anyString(), anyList(), any());
  }

  // ==========================================================================
  // updatePriority() — proto priority mapping
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_passesProtoPriority_toClient() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(ne));
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any()))
        .thenReturn(UpdateRunnerTransactionsPriorityResponse.newBuilder().build());

    service.updatePriority(scope, stages, StageQueuePriority.LOW);

    verify(runnerTransactionsClient)
        .updatePriority(eq(ACCOUNT_ID), eq(List.of(STAGE_RUNTIME_ID_1)), eq(RunnerTransactionPriority.LOW));
  }

  // ==========================================================================
  // updatePriority() — gRPC failures and result merge
  // ==========================================================================

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_grpcThrows_allDispatchedReportedAsUpstreamRejected() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    List<StageSelectorDTO> stages =
        List.of(StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build());
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(ne));
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any()))
        .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    UpdatePriorityResponse resp = service.updatePriority(scope, stages, StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.UPSTREAM_REJECTED);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_grpcSuccess_mergesUpdatedAndFailedRows() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    StageSelectorDTO sel1 =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build();
    StageSelectorDTO sel2 =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier("stage2").build();
    NodeExecution n1 = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    NodeExecution n2 = nodeExecution(STAGE_RUNTIME_ID_2, "stage2", "Stage 2", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(n1, n2));
    UpdateRunnerTransactionsPriorityResponse grpcResp =
        UpdateRunnerTransactionsPriorityResponse.newBuilder()
            .addUpdated(UpdatedRunnerTransaction.newBuilder()
                            .setStageRuntimeId(STAGE_RUNTIME_ID_1)
                            .setPreviousPriority(RunnerTransactionPriority.NORMAL)
                            .setNewPriority(RunnerTransactionPriority.HIGH)
                            .build())
            .addFailed(FailedRunnerTransaction.newBuilder()
                           .setStageRuntimeId(STAGE_RUNTIME_ID_2)
                           .setReason(UpdatePriorityFailureReason.NOT_QUEUED)
                           .setMessage("already running")
                           .build())
            .build();
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any())).thenReturn(grpcResp);

    UpdatePriorityResponse resp = service.updatePriority(scope, List.of(sel1, sel2), StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).hasSize(1);
    assertThat(resp.getUpdated().get(0).getStageIdentifier()).isEqualTo(STAGE_ID);
    assertThat(resp.getUpdated().get(0).getPreviousPriority()).isEqualTo(StageQueuePriority.NORMAL);
    assertThat(resp.getUpdated().get(0).getNewPriority()).isEqualTo(StageQueuePriority.HIGH);
    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getStageIdentifier()).isEqualTo("stage2");
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.NOT_QUEUED);
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_grpcReturnsUnknownStageRuntimeId_isIgnored() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    StageSelectorDTO sel =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build();
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(ne));
    UpdateRunnerTransactionsPriorityResponse grpcResp =
        UpdateRunnerTransactionsPriorityResponse.newBuilder()
            .addUpdated(UpdatedRunnerTransaction.newBuilder()
                            .setStageRuntimeId("unknownStageRuntimeId")
                            .setPreviousPriority(RunnerTransactionPriority.NORMAL)
                            .setNewPriority(RunnerTransactionPriority.HIGH)
                            .build())
            .build();
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any())).thenReturn(grpcResp);

    UpdatePriorityResponse resp = service.updatePriority(scope, List.of(sel), StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).isEmpty();
    assertThat(resp.getFailed()).isEmpty();
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_grpcFailureReason_internalError_mapsToUpstreamRejected() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    StageSelectorDTO sel =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build();
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(ne));
    UpdateRunnerTransactionsPriorityResponse grpcResp =
        UpdateRunnerTransactionsPriorityResponse.newBuilder()
            .addFailed(FailedRunnerTransaction.newBuilder()
                           .setStageRuntimeId(STAGE_RUNTIME_ID_1)
                           .setReason(UpdatePriorityFailureReason.INTERNAL_ERROR)
                           .setMessage("verify-read mismatch")
                           .build())
            .build();
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any())).thenReturn(grpcResp);

    UpdatePriorityResponse resp = service.updatePriority(scope, List.of(sel), StageQueuePriority.HIGH);

    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.UPSTREAM_REJECTED);
    assertThat(resp.getFailed().get(0).getMessage()).isEqualTo("verify-read mismatch");
  }

  @Test
  @Owner(developers = NEGI)
  @Category(UnitTests.class)
  public void updatePriority_mixedPreClassifiedAndUpstreamResults_combinesAllFailures() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    StageSelectorDTO selFound =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier(STAGE_ID).build();
    StageSelectorDTO selMissing =
        StageSelectorDTO.builder().pipelineExecutionId(PLAN_EXEC_ID).stageIdentifier("missingStage").build();
    NodeExecution ne = nodeExecution(STAGE_RUNTIME_ID_1, STAGE_ID, "Stage 1", PLAN_EXEC_ID);
    when(nodeExecutionService.findCurrentStageAttempts(any(), any())).thenReturn(List.of(ne));
    UpdateRunnerTransactionsPriorityResponse grpcResp =
        UpdateRunnerTransactionsPriorityResponse.newBuilder()
            .addUpdated(UpdatedRunnerTransaction.newBuilder()
                            .setStageRuntimeId(STAGE_RUNTIME_ID_1)
                            .setPreviousPriority(RunnerTransactionPriority.LOW)
                            .setNewPriority(RunnerTransactionPriority.HIGH)
                            .build())
            .build();
    when(runnerTransactionsClient.updatePriority(anyString(), anyList(), any())).thenReturn(grpcResp);

    UpdatePriorityResponse resp = service.updatePriority(scope, List.of(selFound, selMissing), StageQueuePriority.HIGH);

    assertThat(resp.getUpdated()).hasSize(1);
    assertThat(resp.getUpdated().get(0).getStageIdentifier()).isEqualTo(STAGE_ID);
    assertThat(resp.getFailed()).hasSize(1);
    assertThat(resp.getFailed().get(0).getStageIdentifier()).isEqualTo("missingStage");
    assertThat(resp.getFailed().get(0).getReason())
        .isEqualTo(io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason.NOT_FOUND);
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private static ListRunnerTransactionsResponse emptyResponse(int page, int limit) {
    return ListRunnerTransactionsResponse.newBuilder()
        .setTransactions(RunnerTransactions.newBuilder().build())
        .setTotalQueued(0)
        .setTotalRunning(0)
        .setTotalItems(0)
        .setPage(page)
        .setLimit(limit)
        .build();
  }

  private static ListRunnerTransactionsResponse listResponseWith(
      List<RunnerTransaction> rows, int totalQueued, int totalRunning, int totalItems, int page, int limit) {
    RunnerTransactions.Builder rt = RunnerTransactions.newBuilder();
    for (RunnerTransaction r : rows) {
      rt.addTransactions(r);
    }
    return ListRunnerTransactionsResponse.newBuilder()
        .setTransactions(rt.build())
        .setTotalQueued(totalQueued)
        .setTotalRunning(totalRunning)
        .setTotalItems(totalItems)
        .setPage(page)
        .setLimit(limit)
        .build();
  }

  private static RunnerTransactionMetadata metadataFor(String stageRuntimeId) {
    return RunnerTransactionMetadata.newBuilder()
        .setAccountId(ACCOUNT_ID)
        .setOrgId(ORG_ID)
        .setProjectId(PROJECT_ID)
        .setStageRuntimeId(stageRuntimeId)
        .build();
  }

  private static RunnerTransaction queuedTransaction(
      String stageRuntimeId, String priority, long createdAt, int queuePosition) {
    return RunnerTransaction.newBuilder()
        .setTransactionId("txn-" + stageRuntimeId)
        .setStatus(RunnerTransactionStatus.QUEUED)
        .setPriority(priority)
        .setCreatedAt(createdAt)
        .setMetadata(metadataFor(stageRuntimeId))
        .setQueuePosition(queuePosition)
        .build();
  }

  private static NodeExecution nodeExecution(String uuid, String identifier, String name, String planExecutionId) {
    return NodeExecution.builder()
        .uuid(uuid)
        .identifier(identifier)
        .name(name)
        .createdAt(100L)
        .executionContext(
            ExecutionContext.newBuilder()
                .setPlanExecutionId(planExecutionId)
                .putSetupAbstractions("accountId", ACCOUNT_ID)
                .putSetupAbstractions("orgIdentifier", ORG_ID)
                .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                .addLevels(
                    Level.newBuilder()
                        .setIdentifier(identifier)
                        .setStepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
                        .build())
                .build())
        .build();
  }

  private static NodeExecution nodeExecutionWithCreatedAt(
      String uuid, String identifier, String planExecutionId, long createdAt) {
    return NodeExecution.builder()
        .uuid(uuid)
        .identifier(identifier)
        .createdAt(createdAt)
        .executionContext(
            ExecutionContext.newBuilder()
                .setPlanExecutionId(planExecutionId)
                .putSetupAbstractions("accountId", ACCOUNT_ID)
                .putSetupAbstractions("orgIdentifier", ORG_ID)
                .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                .addLevels(
                    Level.newBuilder()
                        .setIdentifier(identifier)
                        .setStepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
                        .build())
                .build())
        .build();
  }

  private static NodeExecution nodeExecutionWithScope(
      String uuid, String identifier, String planExecutionId, String accountId, String orgId, String projectId) {
    return NodeExecution.builder()
        .uuid(uuid)
        .identifier(identifier)
        .createdAt(100L)
        .executionContext(
            ExecutionContext.newBuilder()
                .setPlanExecutionId(planExecutionId)
                .putSetupAbstractions("accountId", accountId)
                .putSetupAbstractions("orgIdentifier", orgId)
                .putSetupAbstractions("projectIdentifier", projectId)
                .addLevels(
                    Level.newBuilder()
                        .setIdentifier(identifier)
                        .setStepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
                        .build())
                .build())
        .build();
  }

  private static PipelineExecutionSummaryEntity summary(String planExecutionId, String name, String pipelineId) {
    return PipelineExecutionSummaryEntity.builder()
        .planExecutionId(planExecutionId)
        .name(name)
        .pipelineIdentifier(pipelineId)
        .build();
  }
}
