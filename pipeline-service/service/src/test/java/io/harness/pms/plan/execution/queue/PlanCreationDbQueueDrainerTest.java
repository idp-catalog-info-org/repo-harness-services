/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.queue;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueEntry;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueService;
import io.harness.execution.PriorityType;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.ProcessOutcome;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.ProcessResult;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper.RequeueReason;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanCreationDbQueueDrainerTest extends CategoryTest {
  @Mock private PlanCreationDbQueueService planCreationDbQueueService;
  @Mock private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  private PlanCreationDbQueueDrainer drainer;

  private static final String ACCOUNT = "acc";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    drainer = new PlanCreationDbQueueDrainer(planCreationDbQueueService, planCreationQueueRequestHelper, 100);
  }

  private PlanCreationDbQueueEntry entry(String id, PriorityType priority) {
    return entry(id, ACCOUNT, "puid", priority);
  }

  private PlanCreationDbQueueEntry entry(String id, String accountId, String parentUniqueId, PriorityType priority) {
    return PlanCreationDbQueueEntry.builder()
        .planExecutionId(id)
        .accountId(accountId)
        .orgId("org")
        .projectId("proj")
        .parentUniqueId(parentUniqueId)
        .priorityType(priority == null ? null : priority.name())
        .createdAt(Instant.now())
        .build();
  }

  private static ProcessOutcome processed() {
    return ProcessOutcome.of(ProcessResult.PROCESSED);
  }

  private static ProcessOutcome dropped() {
    return ProcessOutcome.of(ProcessResult.DROP);
  }

  private static ProcessOutcome requeue(RequeueReason reason, String resolvedParentUniqueId) {
    return ProcessOutcome.requeue(reason, resolvedParentUniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void emptyQueueReturnsZeroAndDoesNothing() {
    when(planCreationDbQueueService.fetchBatch(100)).thenReturn(List.of());
    assertThat(drainer.drainOnce()).isEqualTo(0);
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void processedCandidateIsDeletedFromQueue() {
    when(planCreationDbQueueService.fetchBatch(100)).thenReturn(List.of(entry("p1", PriorityType.NORMAL)));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("p1", ACCOUNT, "puid", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1);
    verify(planCreationDbQueueService).deleteByPlanExecutionId("p1");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void droppedCandidateIsDeletedFromQueue() {
    when(planCreationDbQueueService.fetchBatch(100)).thenReturn(List.of(entry("p1", PriorityType.NORMAL)));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("p1", ACCOUNT, "puid", PriorityType.NORMAL))
        .thenReturn(dropped());

    drainer.drainOnce();
    // DROP (aborted / expired / failed / lost idempotency race) still removes the stale row -> self-heal.
    verify(planCreationDbQueueService).deleteByPlanExecutionId("p1");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void requeuedCandidateIsLeftInPlaceAndSkipAheadContinues() {
    // p1 requeued for a non-cacheable reason (OTHER) -> left in place; p2 (different project) has
    // headroom (PROCESSED -> claimed).
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(
            entry("p1", ACCOUNT, "puidA", PriorityType.NORMAL), entry("p2", ACCOUNT, "puidB", PriorityType.NORMAL)));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p1", ACCOUNT, "puidA", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.OTHER, "puidA"));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p2", ACCOUNT, "puidB", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1); // Only p2 was cleared, p1 was REQUEUE
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId("p1");
    verify(planCreationDbQueueService).deleteByPlanExecutionId("p2");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void oneFailingCandidateDoesNotStallTheRestOfTheBatch() {
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(entry("p1", PriorityType.NORMAL), entry("p2", PriorityType.NORMAL)));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("p1", ACCOUNT, "puid", PriorityType.NORMAL))
        .thenThrow(new RuntimeException("boom"));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("p2", ACCOUNT, "puid", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1); // Only p2 was cleared, p1 threw exception
    // p1's row is left for the next walk; p2 still processed.
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId("p1");
    verify(planCreationDbQueueService).deleteByPlanExecutionId("p2");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nullPriorityTypeStringDefaultsToNormal() {
    when(planCreationDbQueueService.fetchBatch(100)).thenReturn(List.of(entry("p1", null)));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             eq("p1"), eq(ACCOUNT), eq("puid"), eq(PriorityType.NORMAL)))
        .thenReturn(processed());

    drainer.drainOnce();
    verify(planCreationQueueRequestHelper, times(1))
        .processQueuedPlanCreationWithOutcome("p1", ACCOUNT, "puid", PriorityType.NORMAL);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void allRequeueReturnsZeroToTriggerIdleSleep() {
    // All projects at their per-project cap -> all REQUEUE. Should return 0 to engage idle sleep
    // in the poller, preventing tight-loop thrashing when no rows can be cleared.
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(entry("p1", ACCOUNT, "puidA", PriorityType.NORMAL),
            entry("p2", ACCOUNT, "puidB", PriorityType.NORMAL), entry("p3", ACCOUNT, "puidC", PriorityType.NORMAL)));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p1", ACCOUNT, "puidA", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.PROJECT_FULL, "puidA"));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p2", ACCOUNT, "puidB", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.PROJECT_FULL, "puidB"));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p3", ACCOUNT, "puidC", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.PROJECT_FULL, "puidC"));

    assertThat(drainer.drainOnce()).isEqualTo(0); // Zero cleared -> idle sleep engages
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void projectFullSkipsRemainingEntriesOfSameProjectInSameWalk() {
    // p1 and p3 belong to full project puidA; p2 belongs to puidB with headroom. Once p1 reports
    // PROJECT_FULL, p3 must be skipped WITHOUT calling the gate again, while p2 still processes.
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(entry("p1", ACCOUNT, "puidA", PriorityType.NORMAL),
            entry("p2", ACCOUNT, "puidB", PriorityType.NORMAL), entry("p3", ACCOUNT, "puidA", PriorityType.NORMAL)));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p1", ACCOUNT, "puidA", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.PROJECT_FULL, "puidA"));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p2", ACCOUNT, "puidB", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1); // only p2 cleared
    verify(planCreationQueueRequestHelper)
        .processQueuedPlanCreationWithOutcome("p2", ACCOUNT, "puidB", PriorityType.NORMAL);
    // p3 was skipped from the cache -> the gate was never invoked for it.
    verify(planCreationQueueRequestHelper, never())
        .processQueuedPlanCreationWithOutcome("p3", ACCOUNT, "puidA", PriorityType.NORMAL);
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId("p3");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void accountFullSkipsRemainingEntriesOfSameAccountButNotOtherAccounts() {
    // The queue is a single global FIFO across accounts. Account acc1 is full; account acc2 is not.
    // Once acc1's first entry reports ACCOUNT_FULL, acc1's other entries are skipped, but acc2 drains.
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(entry("a1", "acc1", "puidA", PriorityType.NORMAL),
            entry("a2", "acc1", "puidB", PriorityType.NORMAL), entry("b1", "acc2", "puidC", PriorityType.NORMAL)));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("a1", "acc1", "puidA", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.ACCOUNT_FULL, "puidA"));
    when(
        planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome("b1", "acc2", "puidC", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1); // only acc2's b1 cleared
    // acc1's second entry is skipped via the full-account cache -> gate never called for it.
    verify(planCreationQueueRequestHelper, never())
        .processQueuedPlanCreationWithOutcome("a2", "acc1", "puidB", PriorityType.NORMAL);
    verify(planCreationQueueRequestHelper)
        .processQueuedPlanCreationWithOutcome("b1", "acc2", "puidC", PriorityType.NORMAL);
    verify(planCreationDbQueueService).deleteByPlanExecutionId("b1");
    verify(planCreationDbQueueService, never()).deleteByPlanExecutionId("a2");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void otherReasonIsNotCachedSoSameProjectIsReEvaluated() {
    // A non-cacheable requeue (legacy gate / fail-closed blip / lost race) must NOT poison the
    // cache: a later entry of the same project is still evaluated (and here it succeeds).
    when(planCreationDbQueueService.fetchBatch(100))
        .thenReturn(List.of(
            entry("p1", ACCOUNT, "puidA", PriorityType.NORMAL), entry("p2", ACCOUNT, "puidA", PriorityType.NORMAL)));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p1", ACCOUNT, "puidA", PriorityType.NORMAL))
        .thenReturn(requeue(RequeueReason.OTHER, "puidA"));
    when(planCreationQueueRequestHelper.processQueuedPlanCreationWithOutcome(
             "p2", ACCOUNT, "puidA", PriorityType.NORMAL))
        .thenReturn(processed());

    assertThat(drainer.drainOnce()).isEqualTo(1);
    // p2 WAS evaluated despite p1's OTHER requeue on the same project.
    verify(planCreationQueueRequestHelper)
        .processQueuedPlanCreationWithOutcome("p2", ACCOUNT, "puidA", PriorityType.NORMAL);
    verify(planCreationDbQueueService).deleteByPlanExecutionId("p2");
  }
}
