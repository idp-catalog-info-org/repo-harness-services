/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.FailedRunnerTransaction;
import io.harness.delegate.ListRunnerTransactionsResponse;
import io.harness.delegate.RunnerTransaction;
import io.harness.delegate.RunnerTransactionPriority;
import io.harness.delegate.RunnerTransactionStatus;
import io.harness.delegate.RunnerTransactionStatusFilter;
import io.harness.delegate.UpdateRunnerTransactionsPriorityResponse;
import io.harness.delegate.UpdatedRunnerTransaction;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.stagequeue.beans.DelegateRefDTO;
import io.harness.pms.stagequeue.beans.StageQueueListResponse;
import io.harness.pms.stagequeue.beans.StageQueuePriority;
import io.harness.pms.stagequeue.beans.StageQueueRow;
import io.harness.pms.stagequeue.beans.StageQueueRow.StageQueueRowBuilder;
import io.harness.pms.stagequeue.beans.StageQueueStatus;
import io.harness.pms.stagequeue.beans.StageSelectorDTO;
import io.harness.pms.stagequeue.beans.UpdatePriorityFailure;
import io.harness.pms.stagequeue.beans.UpdatePriorityFailureReason;
import io.harness.pms.stagequeue.beans.UpdatePriorityResponse;
import io.harness.pms.stagequeue.beans.UpdatePrioritySuccess;
import io.harness.pms.stagequeue.client.RunnerTransactionsServiceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Backs the customer-facing {@code /v2/stages/queue} APIs. Stateless: every call hits the DMS
 * gRPC service and pipeline-service's own {@code NodeExecution} / {@code PipelineExecutionSummary}
 * collections directly, with no caching layer.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class StageQueueService {
  private static final int MAX_LIMIT = 100;
  private static final int MAX_SELECTORS_PER_UPDATE = 10;

  private static final Set<String> NODE_EXECUTION_FORWARD_FIELDS =
      Set.of(NodeExecutionKeys.uuid, NodeExecutionKeys.identifier, NodeExecutionKeys.name,
          NodeExecutionKeys.executionContext, NodeExecutionKeys.ambiance, NodeExecutionKeys.createdAt);
  private static final List<String> SUMMARY_PROJECTIONS =
      List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.name,
          PlanExecutionSummaryKeys.pipelineIdentifier, PlanExecutionSummaryKeys.executionTriggerInfo);

  private final RunnerTransactionsServiceClient runnerTransactionsClient;
  private final NodeExecutionService nodeExecutionService;
  private final PMSExecutionService pmsExecutionService;

  @Inject
  public StageQueueService(RunnerTransactionsServiceClient runnerTransactionsClient,
      NodeExecutionService nodeExecutionService, PMSExecutionService pmsExecutionService) {
    this.runnerTransactionsClient = runnerTransactionsClient;
    this.nodeExecutionService = nodeExecutionService;
    this.pmsExecutionService = pmsExecutionService;
  }

  // ===========================================================================
  // GET /v2/stages/queue
  // ===========================================================================

  public StageQueueListResponse list(Scope scope, StageQueueStatus status, int page, int limit) {
    if (limit <= 0 || limit > MAX_LIMIT) {
      throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT + " (inclusive); got " + limit);
    }
    if (page < 0) {
      throw new InvalidRequestException("page must be >= 0; got " + page);
    }

    // DMS performs scope + status filtering, sorting (priority-time), and pagination server-side
    // and returns only the page slice. Pipeline-service joins those rows with NodeExecution +
    // PipelineExecutionSummaryEntity to add stage/pipeline display fields.
    ListRunnerTransactionsResponse upstream = runnerTransactionsClient.list(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), toProtoStatusFilter(status), page, limit);
    List<RunnerTransaction> pageTransactions = upstream.getTransactions().getTransactionsList();
    if (pageTransactions.isEmpty()) {
      return StageQueueListResponse.builder()
          .stages(Collections.emptyList())
          .totalQueued(upstream.getTotalQueued())
          .totalRunning(upstream.getTotalRunning())
          .page(upstream.getLimit() == 0 ? page : upstream.getPage())
          .limit(upstream.getLimit() == 0 ? limit : upstream.getLimit())
          .totalItems(upstream.getTotalItems())
          .build();
    }

    // Bulk read NodeExecution by uuid IN [stage_runtime_ids] for the page slice only.
    Set<String> stageRuntimeIds = pageTransactions.stream()
                                      .map(rt -> rt.getMetadata().getStageRuntimeId())
                                      .filter(EmptyPredicate::isNotEmpty)
                                      .collect(Collectors.toSet());
    Map<String, NodeExecution> nodeExecutionByUuid =
        nodeExecutionService.getAllWithFieldIncluded(stageRuntimeIds, NODE_EXECUTION_FORWARD_FIELDS)
            .stream()
            .collect(Collectors.toMap(NodeExecution::getUuid, ne -> ne, (a, b) -> a));

    // Bulk read PipelineExecutionSummaryEntity by planExecutionId for the page slice only.
    // Uses fetchExecutionSummaries (object-store fallback when Mongo TTL is expired and data retention FF is on).
    List<String> planExecutionIds = nodeExecutionByUuid.values()
                                        .stream()
                                        .map(StageQueueService::getPlanExecutionId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .collect(Collectors.toList());
    Map<String, PipelineExecutionSummaryEntity> summariesByPlan = planExecutionIds.isEmpty()
        ? Map.of()
        : pmsExecutionService
              .fetchExecutionSummaries(scope.getAccountIdentifier(), planExecutionIds, SUMMARY_PROJECTIONS)
              .stream()
              .collect(Collectors.toMap(PipelineExecutionSummaryEntity::getPlanExecutionId, e -> e, (a, b) -> a));

    // Build rows in the order DMS returned (already sorted + paginated upstream). DMS is the
    // source of truth: every transaction yields a row, and QUEUED rows already carry their
    // 1-based absolute queue_position computed against the global QUEUED bucket. PMS-side fields
    // (stage / pipeline / trigger) are populated where the joins resolve and left null otherwise —
    // keeps pagination stable (no follow-up page request to backfill dropped rows).
    List<StageQueueRow> rows = new ArrayList<>(pageTransactions.size());
    for (RunnerTransaction rt : pageTransactions) {
      String stageRuntimeId = rt.getMetadata().getStageRuntimeId();
      NodeExecution ne = nodeExecutionByUuid.get(stageRuntimeId);
      PipelineExecutionSummaryEntity summary = ne == null ? null : summariesByPlan.get(getPlanExecutionId(ne));
      StageQueueRow row = buildRow(rt, ne, summary);
      if (row.getStatus() == StageQueueStatus.QUEUED && rt.hasQueuePosition()) {
        row = row.toBuilder().queuePosition(rt.getQueuePosition()).build();
      }
      rows.add(row);
    }

    return StageQueueListResponse.builder()
        .stages(rows)
        .totalQueued(upstream.getTotalQueued())
        .totalRunning(upstream.getTotalRunning())
        .page(upstream.getLimit() == 0 ? page : upstream.getPage())
        .limit(upstream.getLimit() == 0 ? limit : upstream.getLimit())
        .totalItems(upstream.getTotalItems())
        .build();
  }

  private static RunnerTransactionStatusFilter toProtoStatusFilter(StageQueueStatus status) {
    if (status == null) {
      return RunnerTransactionStatusFilter.STATUS_FILTER_ALL;
    }
    switch (status) {
      case QUEUED:
        return RunnerTransactionStatusFilter.STATUS_FILTER_QUEUED;
      case RUNNING:
        return RunnerTransactionStatusFilter.STATUS_FILTER_RUNNING;
      case ALL:
      default:
        return RunnerTransactionStatusFilter.STATUS_FILTER_ALL;
    }
  }

  // ===========================================================================
  // PUT /v2/stages/queue/priority
  // ===========================================================================

  public UpdatePriorityResponse updatePriority(
      Scope scope, List<StageSelectorDTO> stages, StageQueuePriority priority) {
    if (stages == null || stages.isEmpty()) {
      return UpdatePriorityResponse.builder().updated(List.of()).failed(List.of()).build();
    }
    if (stages.size() > MAX_SELECTORS_PER_UPDATE) {
      throw new InvalidRequestException(
          "stages exceeds the per-request limit of " + MAX_SELECTORS_PER_UPDATE + " entries");
    }

    List<UpdatePrioritySuccess> updated = new ArrayList<>();
    List<UpdatePriorityFailure> failed = new ArrayList<>();

    // Step 1: pipelineExecutionId == planExecutionId (M1). Bulk-resolve (planExecId, stageIdentifier) →
    // current NodeExecution via the planExecutionId_stepCategory_identifier_idx, sorted createdAt DESC
    // to break the M6 race deterministically (PMS retry insert + oldRetry flip are non-atomic).
    Set<String> planIds = stages.stream().map(StageSelectorDTO::getPipelineExecutionId).collect(Collectors.toSet());
    Set<String> stageIdents = stages.stream().map(StageSelectorDTO::getStageIdentifier).collect(Collectors.toSet());
    List<NodeExecution> nodes = nodeExecutionService.findCurrentStageAttempts(planIds, stageIdents);

    // For every (planExecutionId, stageIdentifier) keep the largest-createdAt match (M6 tiebreak).
    Map<String, NodeExecution> currentAttemptByKey = new HashMap<>();
    for (NodeExecution ne : nodes) {
      String key = stageKey(getPlanExecutionId(ne), getIdentifier(ne));
      NodeExecution existing = currentAttemptByKey.get(key);
      if (existing == null || ne.getCreatedAt() > existing.getCreatedAt()) {
        currentAttemptByKey.put(key, ne);
      }
    }

    // Bucket each selector. Both PMS-only failure modes — NOT_FOUND (no live NodeExecution, M4)
    // and OUT_OF_SCOPE (NodeExecution's stored scope outside the request scope) — short-circuit
    // here. We never call DMS for these, since DMS doesn't know PMS's scope or retention state.
    Map<String, StageSelectorDTO> dispatchedByStageRuntimeId = new HashMap<>();
    List<String> stageRuntimeIdsToUpdate = new ArrayList<>();
    for (StageSelectorDTO sel : stages) {
      String key = stageKey(sel.getPipelineExecutionId(), sel.getStageIdentifier());
      NodeExecution ne = currentAttemptByKey.get(key);
      if (ne == null) {
        failed.add(notFound(sel, "no live stage execution found"));
        continue;
      }
      if (!nodeExecutionMatchesScope(ne, scope)) {
        failed.add(failure(sel, UpdatePriorityFailureReason.OUT_OF_SCOPE, "stage scope outside the request scope"));
        continue;
      }
      stageRuntimeIdsToUpdate.add(ne.getUuid());
      dispatchedByStageRuntimeId.put(ne.getUuid(), sel);
    }

    if (stageRuntimeIdsToUpdate.isEmpty()) {
      return UpdatePriorityResponse.builder().updated(updated).failed(failed).build();
    }

    // Step 2: single gRPC UpdatePriority. DMS runs the read-update-verify (3-query) flow and
    // returns per-stage results: updated[] for rows whose verify-read shows the new priority,
    // failed[] for rows that did not transition (NOT_FOUND / NOT_QUEUED / INTERNAL_ERROR).
    UpdateRunnerTransactionsPriorityResponse resp;
    try {
      resp = runnerTransactionsClient.updatePriority(
          scope.getAccountIdentifier(), stageRuntimeIdsToUpdate, toProtoPriority(priority));
    } catch (StatusRuntimeException ex) {
      log.warn("UpdatePriority gRPC call failed for account {}: {}", scope.getAccountIdentifier(), ex.getStatus(), ex);
      // Whole-call failure: every dispatched selector is reported as upstream-rejected.
      for (StageSelectorDTO sel : dispatchedByStageRuntimeId.values()) {
        failed.add(failure(sel, UpdatePriorityFailureReason.UPSTREAM_REJECTED, "DMS rejected the priority update"));
      }
      return UpdatePriorityResponse.builder().updated(List.of()).failed(failed).build();
    }

    // Step 3: per-stage merge. Failed rows map proto reasons back to the public DTO enum.
    // Clients refetch GET /v2/stages/queue if they need refreshed queue positions; computing
    // them here would be stale by arrival anyway (dispatcher / concurrent update can move the
    // queue between the verify-read and the response reaching the client).
    for (UpdatedRunnerTransaction u : resp.getUpdatedList()) {
      StageSelectorDTO sel = dispatchedByStageRuntimeId.get(u.getStageRuntimeId());
      if (sel == null) {
        continue; // defensive: DMS returned a stage_runtime_id we did not send
      }
      updated.add(UpdatePrioritySuccess.builder()
                      .pipelineExecutionId(sel.getPipelineExecutionId())
                      .stageIdentifier(sel.getStageIdentifier())
                      .previousPriority(fromProtoPriority(u.getPreviousPriority()))
                      .newPriority(fromProtoPriority(u.getNewPriority()))
                      .build());
    }
    for (FailedRunnerTransaction f : resp.getFailedList()) {
      StageSelectorDTO sel = dispatchedByStageRuntimeId.get(f.getStageRuntimeId());
      if (sel == null) {
        continue;
      }
      failed.add(failure(sel, fromProtoFailureReason(f.getReason()), f.getMessage()));
    }
    return UpdatePriorityResponse.builder().updated(updated).failed(failed).build();
  }

  private static UpdatePriorityFailureReason fromProtoFailureReason(io.harness.delegate.UpdatePriorityFailureReason r) {
    if (r == null) {
      return UpdatePriorityFailureReason.UPSTREAM_REJECTED;
    }
    switch (r) {
      case NOT_FOUND:
        return UpdatePriorityFailureReason.NOT_FOUND;
      case NOT_QUEUED:
        return UpdatePriorityFailureReason.NOT_QUEUED;
      case INTERNAL_ERROR:
      case UPDATE_PRIORITY_FAILURE_REASON_UNSPECIFIED:
      default:
        return UpdatePriorityFailureReason.UPSTREAM_REJECTED;
    }
  }

  private static StageQueuePriority fromProtoPriority(RunnerTransactionPriority p) {
    if (p == null) {
      return null;
    }
    switch (p) {
      case HIGH:
        return StageQueuePriority.HIGH;
      case NORMAL:
        return StageQueuePriority.NORMAL;
      case LOW:
        return StageQueuePriority.LOW;
      default:
        return null;
    }
  }

  // ===========================================================================
  // Helpers — row construction
  // ===========================================================================

  private StageQueueRow buildRow(RunnerTransaction rt, NodeExecution ne, PipelineExecutionSummaryEntity summary) {
    StageQueueStatus rowStatus = toUiStatus(rt.getStatus());
    // RunnerTransaction is the source of truth for the row. PMS-side joins fill display fields
    // when present; leave them null otherwise (NodeExecution / PipelineExecutionSummary may be
    // missing under retention reaping or a write race with the runner transaction).
    StageQueueRowBuilder b =
        StageQueueRow.builder()
            .pipelineIdentifier(summary == null ? null : summary.getPipelineIdentifier())
            .pipelineName(summary == null ? null : summary.getName())
            .pipelineExecutionId(summary == null ? null : summary.getPlanExecutionId())
            .planExecutionId(summary == null ? null : summary.getPlanExecutionId())
            .stageIdentifier(ne == null ? null : getIdentifier(ne))
            .stageName(ne == null ? null : ne.getName())
            .status(rowStatus)
            .orgIdentifier(rt.getMetadata().getOrgId())
            .projectIdentifier(rt.getMetadata().getProjectId())
            .triggeredBy(summary == null ? null : extractTriggeredBy(summary.getExecutionTriggerInfo()))
            .triggerType(summary == null ? null : extractTriggerType(summary.getExecutionTriggerInfo()))
            .createdAt(rt.getCreatedAt());

    if (rowStatus == StageQueueStatus.QUEUED) {
      b.priority(toPriorityEnum(rt.getPriority()));
      b.eligibleDelegates(buildEligibleDelegates(rt));
      long now = Instant.now().toEpochMilli();
      long queuedMs = Math.max(0, now - rt.getCreatedAt());
      b.queuedDurationMs(queuedMs);
      b.queuedDuration(humanDuration(queuedMs));
    } else if (rowStatus == StageQueueStatus.RUNNING) {
      b.executingDelegate(DelegateRefDTO.builder()
                              .name(rt.getExecutingOnRunnerName())
                              .hostName(rt.getExecutingOnRunnerHostName())
                              .build());
    }
    return b.build();
  }

  private static List<DelegateRefDTO> buildEligibleDelegates(RunnerTransaction rt) {
    List<String> names = rt.getEligibleToExecuteRunnerNamesList();
    List<String> hosts = rt.getEligibleToExecuteRunnerHostNamesList();
    int n = Math.max(names.size(), hosts.size());
    if (n == 0) {
      return List.of();
    }
    List<DelegateRefDTO> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(DelegateRefDTO.builder()
                  .name(i < names.size() ? names.get(i) : null)
                  .hostName(i < hosts.size() ? hosts.get(i) : null)
                  .build());
    }
    return out;
  }

  private static io.harness.pms.stagequeue.beans.TriggeredByDTO extractTriggeredBy(ExecutionTriggerInfo info) {
    if (info == null || !info.hasTriggeredBy()) {
      return null;
    }
    TriggeredBy tb = info.getTriggeredBy();
    String name = isNotEmpty(tb.getIdentifier()) ? tb.getIdentifier() : tb.getTriggerName();
    String email = tb.getExtraInfoOrDefault("email", null);
    if (isEmpty(name) && isEmpty(email)) {
      return null;
    }
    return io.harness.pms.stagequeue.beans.TriggeredByDTO.builder().name(name).email(email).build();
  }

  private static String extractTriggerType(ExecutionTriggerInfo info) {
    return info == null || info.getTriggerType() == null ? null : info.getTriggerType().name();
  }

  // ===========================================================================
  // Helpers — sorting / filtering / mapping
  // ===========================================================================

  private static StageQueuePriority toPriorityEnum(String legacy) {
    if (legacy == null) {
      return null;
    }
    switch (legacy) {
      case "HIGH":
        return StageQueuePriority.HIGH;
      case "NORMAL":
        return StageQueuePriority.NORMAL;
      case "LOW":
        return StageQueuePriority.LOW;
      default:
        return null;
    }
  }

  private static RunnerTransactionPriority toProtoPriority(StageQueuePriority p) {
    switch (p) {
      case HIGH:
        return RunnerTransactionPriority.HIGH;
      case NORMAL:
        return RunnerTransactionPriority.NORMAL;
      case LOW:
        return RunnerTransactionPriority.LOW;
      default:
        return RunnerTransactionPriority.RUNNER_TRANSACTION_PRIORITY_UNSPECIFIED;
    }
  }

  private static StageQueueStatus toUiStatus(RunnerTransactionStatus s) {
    if (s == RunnerTransactionStatus.QUEUED) {
      return StageQueueStatus.QUEUED;
    }
    if (s == RunnerTransactionStatus.RUNNING) {
      return StageQueueStatus.RUNNING;
    }
    return null;
  }

  // ===========================================================================
  // Helpers — scope filtering on the priority-update path
  // ===========================================================================

  /**
   * Scope filter for the priority-update path, applied directly off the resolved
   * {@link NodeExecution}. We do not consult DMS for this — pipeline-service is the origin of
   * the scope value (CI submission copies it from the ambiance into
   * {@code RunnerTransactionMetadata}), so the {@code NodeExecution} is the authoritative read
   * and lets us classify {@code OUT_OF_SCOPE} before any gRPC call.
   */
  private static boolean nodeExecutionMatchesScope(NodeExecution ne, Scope scope) {
    if (!Objects.equals(safe(scope.getAccountIdentifier()), safe(NodeExecutionContextUtils.getAccountId(ne)))) {
      return false;
    }
    if (isNotEmpty(scope.getOrgIdentifier())
        && !scope.getOrgIdentifier().equals(NodeExecutionContextUtils.getOrgIdentifier(ne))) {
      return false;
    }
    if (isNotEmpty(scope.getProjectIdentifier())
        && !scope.getProjectIdentifier().equals(NodeExecutionContextUtils.getProjectIdentifier(ne))) {
      return false;
    }
    return true;
  }

  // ===========================================================================
  // Helpers — NodeExecution field extraction (uses NodeExecutionContextUtils-equivalents)
  // ===========================================================================

  private static String getPlanExecutionId(NodeExecution ne) {
    // NodeExecutionContextUtils prefers executionContext (memory: ambiance.setupAbstractions
    // is deprecated/empty) and falls back to ambiance.getPlanExecutionId() when null.
    return NodeExecutionContextUtils.getPlanExecutionId(ne);
  }

  private static String getIdentifier(NodeExecution ne) {
    if (isNotEmpty(ne.getIdentifier())) {
      return ne.getIdentifier();
    }
    return NodeExecutionContextUtils.obtainStepIdentifier(ne);
  }

  // ===========================================================================
  // Helpers — small utilities
  // ===========================================================================

  private static String stageKey(String planExecutionId, String stageIdentifier) {
    return planExecutionId + ":" + stageIdentifier;
  }

  private static UpdatePriorityFailure failure(StageSelectorDTO sel, UpdatePriorityFailureReason reason, String msg) {
    return UpdatePriorityFailure.builder()
        .pipelineExecutionId(sel.getPipelineExecutionId())
        .stageIdentifier(sel.getStageIdentifier())
        .reason(reason)
        .message(msg)
        .build();
  }

  private static UpdatePriorityFailure notFound(StageSelectorDTO sel, String msg) {
    return failure(sel, UpdatePriorityFailureReason.NOT_FOUND, msg);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String humanDuration(long ms) {
    Duration d = Duration.ofMillis(ms);
    long h = d.toHours();
    long m = d.toMinutesPart();
    long s = d.toSecondsPart();
    if (h > 0) {
      return h + "h " + m + "m";
    }
    if (m > 0) {
      return m + "m " + s + "s";
    }
    return s + "s";
  }
}
