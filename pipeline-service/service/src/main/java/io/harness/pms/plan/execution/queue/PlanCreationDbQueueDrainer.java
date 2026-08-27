/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.queue;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueEntry;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueService;
import io.harness.execution.PriorityType;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.ProcessOutcome;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.ProcessResult;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.RequeueReason;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Drains the Postgres {@code plan_creation_queue} in FIFO order with skip-ahead. This is the
 * Postgres replacement for the hsqs poll/ack/unack loop; it reuses the exact same downstream
 * processing via {@link PlanCreationQueueRequestHelper#processQueuedPlanCreation}.
 *
 * <p><b>Ordering — process first, delete after.</b> For each candidate we run the transport-
 * agnostic processing and only then mutate the queue:
 * <ul>
 *   <li>{@code PROCESSED} / {@code DROP} → delete the row (equivalent to hsqs ack).</li>
 *   <li>{@code REQUEUE} → leave the row in place and move to the next candidate. Leaving the row
 *       preserves its FIFO {@code created_at} position, and skipping to the next candidate is the
 *       skip-ahead property (a project/account at its cap does not block others with headroom).</li>
 * </ul>
 *
 * <p><b>Exactly-once</b> is guaranteed by the idempotent {@code STARTING_PLAN_CREATION} status flip
 * inside {@code processQueuedPlanCreation} — even if two pods walk overlapping batches, or the hsqs
 * poller is running in parallel during the cutover overlap window, only one flip wins and the
 * others get {@code DROP}. <b>Orphan self-heal</b>: a row whose plan is already past
 * {@code QUEUED_PLAN_CREATION} (pod died between flip and delete, or aborted out-of-band) resolves
 * to {@code DROP} on the next walk and the stale row is deleted.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanCreationDbQueueDrainer {
  // Delimiter for the (accountId, parentUniqueId) full-project cache key. '/' can't appear in an NG
  // entity identifier, so it can't be forged across a boundary (e.g. "a"+"b/c" vs "a/b"+"c").
  private static final String KEY_SEPARATOR = "/";

  private final PlanCreationDbQueueService planCreationDbQueueService;
  private final PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  private final int batchSize;

  @Inject
  public PlanCreationDbQueueDrainer(PlanCreationDbQueueService planCreationDbQueueService,
      PlanCreationQueueRequestHelper planCreationQueueRequestHelper,
      @Named("planCreationDbQueueBatchSize") int batchSize) {
    this.planCreationDbQueueService = planCreationDbQueueService;
    this.planCreationQueueRequestHelper = planCreationQueueRequestHelper;
    this.batchSize = batchSize;
  }

  /**
   * Walk one FIFO batch; returns the number of rows cleared from the queue (0 triggers idle sleep).
   * Returning the cleared count (not fetched count) prevents tight-loop thrashing when all entries
   * are REQUEUE (e.g., projects at their per-project cap during PR-3's concurrency gate).
   *
   * <p>Within a single walk we cache scopes already found full so we don't re-run the concurrency
   * gate for every queued entry of a project/account that is at its cap. The cache is per-walk only:
   * counters drop as executions finish and caps can be edited, so a scope found full now is
   * re-evaluated from scratch on the next walk — worst case one extra walk of latency, never a stale
   * skip that starves a freed-up scope, and never an over-admit (we only ever skip, never admit,
   * from the cache). FIFO position is preserved because a skipped row is left in place.
   *
   * <p>The cache is keyed by the actual constraint: {@code fullAccounts} by accountId (the account
   * total blocks every project on that account) and {@code fullProjects} by (accountId,
   * parentUniqueId) — never by project alone, since the legacy High/Low mode's constraint is
   * account+priority, not project (those requeues report {@link RequeueReason#OTHER} and are never
   * cached). The queue is a single global FIFO shared across accounts, so a full account only skips
   * its own entries; other accounts keep draining.
   */
  public int drainOnce() {
    List<PlanCreationDbQueueEntry> batch = planCreationDbQueueService.fetchBatch(batchSize);
    if (EmptyPredicate.isEmpty(batch)) {
      return 0;
    }
    Set<String> fullAccounts = new HashSet<>();
    Set<String> fullProjects = new HashSet<>();
    int cleared = 0;
    for (PlanCreationDbQueueEntry entry : batch) {
      try {
        // Skip-ahead: a scope already found full in THIS walk cannot have gained headroom, so don't
        // re-run the gate for it. Leave the row in place (FIFO position preserved).
        if (fullAccounts.contains(entry.getAccountId())
            || fullProjects.contains(projectKey(entry.getAccountId(), entry.getParentUniqueId()))) {
          continue;
        }
        PriorityType priorityType = parsePriorityType(entry.getPriorityType());
        ProcessOutcome outcome = planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
            entry.getPlanExecutionId(), entry.getAccountId(), entry.getParentUniqueId(), priorityType);
        if (outcome.getResult() != ProcessResult.REQUEUE) {
          // PROCESSED or DROP -> remove from the queue (hsqs ack analogue).
          planCreationDbQueueService.deleteByPlanExecutionId(entry.getPlanExecutionId());
          cleared++;
          continue;
        }
        // REQUEUE -> leave the row; cache the full scope so its remaining entries are skipped.
        cacheFullScope(fullAccounts, fullProjects, entry, outcome);
      } catch (Exception ex) {
        // Never let one bad candidate stall the whole drain; leave its row for the next walk.
        log.error("[PLAN_CREATION_QUEUE] drain failed for planExecutionId={}", entry.getPlanExecutionId(), ex);
      }
    }
    return cleared;
  }

  // Record the blocking scope for the rest of this walk. ACCOUNT_FULL blocks every project on the
  // account; PROJECT_FULL blocks just that (account, project). OTHER (legacy gate / fail-closed blip
  // / lost race) is deliberately NOT cached — its constraint isn't the project scope, so each such
  // entry must be re-evaluated on its own.
  private void cacheFullScope(
      Set<String> fullAccounts, Set<String> fullProjects, PlanCreationDbQueueEntry entry, ProcessOutcome outcome) {
    if (outcome.getRequeueReason() == RequeueReason.ACCOUNT_FULL) {
      fullAccounts.add(entry.getAccountId());
    } else if (outcome.getRequeueReason() == RequeueReason.PROJECT_FULL) {
      // Use the parentUniqueId the gate actually keyed on (the transport may not have carried it).
      fullProjects.add(projectKey(entry.getAccountId(), outcome.getResolvedParentUniqueId()));
    }
  }

  // Project scope is (account, parentUniqueId). A null parentUniqueId can't be a per-project full
  // scope (the gate falls back to account-only), so it never lands here — but key defensively.
  private String projectKey(String accountId, String parentUniqueId) {
    return accountId + KEY_SEPARATOR + parentUniqueId;
  }

  private PriorityType parsePriorityType(String value) {
    if (EmptyPredicate.isEmpty(value)) {
      return PriorityType.NORMAL;
    }
    try {
      return PriorityType.valueOf(value);
    } catch (IllegalArgumentException ex) {
      log.warn("[PLAN_CREATION_QUEUE] unknown priorityType '{}', defaulting to NORMAL", value);
      return PriorityType.NORMAL;
    }
  }
}
