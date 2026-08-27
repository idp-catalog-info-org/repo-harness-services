/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency;

import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterGate;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterMutationHook;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueEntry;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class StepConcurrencyHelperTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock private StepConcurrencyQueueService queueService;
  @Mock private StepConcurrencyCounterGate counterGate;
  @Mock private StepConcurrencyCounterMutationHook counterMutationHook;
  @Mock private MetricService metricService;

  private StepConcurrencyHelper stepConcurrencyHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  private Ambiance ambiance;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.openMocks(this);
    stepConcurrencyHelper = new StepConcurrencyHelper();
    FieldUtils.writeField(stepConcurrencyHelper, "nodeExecutionService", nodeExecutionService, true);
    FieldUtils.writeField(stepConcurrencyHelper, "pipelineSettingsService", pipelineSettingsService, true);
    FieldUtils.writeField(stepConcurrencyHelper, "queueService", queueService, true);
    FieldUtils.writeField(stepConcurrencyHelper, "counterGate", counterGate, true);
    FieldUtils.writeField(stepConcurrencyHelper, "counterMutationHook", counterMutationHook, true);
    FieldUtils.writeField(stepConcurrencyHelper, "metricService", metricService, true);
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .build();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testShouldQueue_nonLeafMode_returnsFalse() {
    boolean result = stepConcurrencyHelper.shouldQueue(ExecutionMode.CHILDREN, ambiance);
    assertThat(result).isFalse();
    verify(nodeExecutionService, never()).getCountOfLeafStepsWithGivenStatuses(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testShouldQueue_leafMode_atLimit_queues() {
    when(nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
             eq(PLAN_EXECUTION_ID), eq(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS)))
        .thenReturn(257L);
    when(pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID)).thenReturn(256);

    boolean result = stepConcurrencyHelper.shouldQueue(ExecutionMode.TASK, ambiance);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testShouldQueue_leafMode_belowLimit_doesNotQueue() {
    when(nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
             eq(PLAN_EXECUTION_ID), eq(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS)))
        .thenReturn(100L);
    when(pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID)).thenReturn(256);

    boolean result = stepConcurrencyHelper.shouldQueue(ExecutionMode.TASK, ambiance);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testShouldStartQueuedStep_nonLeafMode_returnsFalse() {
    boolean result = stepConcurrencyHelper.shouldStartQueuedStep(ExecutionMode.CHILD, ambiance);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAvailableSlotsForQueuedSteps_countsStartingQueuedStep() {
    // 255 active + STARTING_QUEUED_STEP nodes already running, limit is 256 → 1 slot available
    when(nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
             eq(PLAN_EXECUTION_ID), eq(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS)))
        .thenReturn(255L);
    when(pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID)).thenReturn(256);

    int slots = stepConcurrencyHelper.getAvailableSlotsForQueuedSteps(ambiance);
    assertThat(slots).isEqualTo(1);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAvailableSlotsForQueuedSteps_atLimit_returnsZero() {
    when(nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
             eq(PLAN_EXECUTION_ID), eq(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS)))
        .thenReturn(256L);
    when(pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID)).thenReturn(256);

    int slots = stepConcurrencyHelper.getAvailableSlotsForQueuedSteps(ambiance);
    assertThat(slots).isEqualTo(0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAvailableSlotsForQueuedSteps_settingsReturnsZero_returnsZero() {
    when(nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
             eq(PLAN_EXECUTION_ID), eq(StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS)))
        .thenReturn(10L);
    when(pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID)).thenReturn(0);

    int slots = stepConcurrencyHelper.getAvailableSlotsForQueuedSteps(ambiance);
    assertThat(slots).isEqualTo(0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier1Hit_claimsSlotAtomically() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid("ne1").ambiance(ambiance).build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isEqualTo(ambiance);
    verify(nodeExecutionService, times(1)).updateUsingQuery(any(), any());
    verify(queueService, never()).fetchBatch(any(Integer.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier1Miss_fallsThroughToTier2AndClaims() {
    // Tier-1 same-plan claim misses (no same-plan queued row) -> falls through to the tier-2
    // cluster-wide Postgres FIFO walk, which finds and claims a candidate.
    NodeExecution claimed = NodeExecution.builder().uuid("ne2").ambiance(ambiance).mode(ExecutionMode.TASK).build();
    StepConcurrencyQueueEntry entry = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId("ne2")
                                          .planExecutionId("otherPlan")
                                          .accountId(ACCOUNT_ID)
                                          .createdAt(Instant.now())
                                          .build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null, claimed);
    when(queueService.fetchBatch(anyInt())).thenReturn(List.of(entry));
    when(counterGate.hasHeadroomFor(ACCOUNT_ID)).thenReturn(true);
    when(queueService.deleteByNodeExecutionId("ne2")).thenReturn(true);
    when(nodeExecutionService.getAmbiance(claimed)).thenReturn(ambiance);

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isEqualTo(ambiance);
    verify(nodeExecutionService, times(2)).updateUsingQuery(any(), any());
    verify(counterMutationHook).onStatusChange(eq(ACCOUNT_ID), any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier2SelfHeal_continuesWalkOnMongoPredicateMiss() {
    // First Postgres candidate's row was orphaned (e.g. pod crashed mid queue-in) — its Mongo
    // QSLR predicate no longer matches. The walk must self-heal by continuing to the next
    // candidate instead of returning null.
    StepConcurrencyQueueEntry orphaned = StepConcurrencyQueueEntry.builder()
                                             .nodeExecutionId("orphan")
                                             .planExecutionId("otherPlan")
                                             .accountId(ACCOUNT_ID)
                                             .createdAt(Instant.now())
                                             .build();
    StepConcurrencyQueueEntry healthy = StepConcurrencyQueueEntry.builder()
                                            .nodeExecutionId("healthy")
                                            .planExecutionId("otherPlan")
                                            .accountId(ACCOUNT_ID)
                                            .createdAt(Instant.now())
                                            .build();
    NodeExecution claimed = NodeExecution.builder().uuid("healthy").ambiance(ambiance).mode(ExecutionMode.TASK).build();
    // call#1 = tier-1 miss, call#2 = tier-2 claim attempt for "orphan" (self-heal miss),
    // call#3 = tier-2 claim attempt for "healthy" (hit).
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null, null, claimed);
    when(queueService.fetchBatch(anyInt())).thenReturn(List.of(orphaned, healthy));
    when(counterGate.hasHeadroomFor(ACCOUNT_ID)).thenReturn(true);
    when(queueService.deleteByNodeExecutionId("orphan")).thenReturn(true);
    when(queueService.deleteByNodeExecutionId("healthy")).thenReturn(true);
    when(nodeExecutionService.getAmbiance(claimed)).thenReturn(ambiance);

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isEqualTo(ambiance);
    verify(queueService).deleteByNodeExecutionId("orphan");
    verify(queueService).deleteByNodeExecutionId("healthy");
    verify(nodeExecutionService, times(3)).updateUsingQuery(any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier2HeadroomGate_skipsCandidateAtCap() {
    // First candidate's account is at cap -> skipped without a Postgres delete attempt. Second
    // candidate has headroom and is claimed.
    StepConcurrencyQueueEntry atCap = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId("atCap")
                                          .planExecutionId("otherPlan")
                                          .accountId("accountAtCap")
                                          .createdAt(Instant.now())
                                          .build();
    StepConcurrencyQueueEntry hasRoom = StepConcurrencyQueueEntry.builder()
                                            .nodeExecutionId("hasRoom")
                                            .planExecutionId("otherPlan")
                                            .accountId(ACCOUNT_ID)
                                            .createdAt(Instant.now())
                                            .build();
    NodeExecution claimed = NodeExecution.builder().uuid("hasRoom").ambiance(ambiance).mode(ExecutionMode.TASK).build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null, claimed);
    when(queueService.fetchBatch(anyInt())).thenReturn(List.of(atCap, hasRoom));
    when(counterGate.hasHeadroomFor("accountAtCap")).thenReturn(false);
    when(counterGate.hasHeadroomFor(ACCOUNT_ID)).thenReturn(true);
    when(queueService.deleteByNodeExecutionId("hasRoom")).thenReturn(true);
    when(nodeExecutionService.getAmbiance(claimed)).thenReturn(ambiance);

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isEqualTo(ambiance);
    verify(queueService, never()).deleteByNodeExecutionId("atCap");
    verify(queueService).deleteByNodeExecutionId("hasRoom");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier2EmptyBatch_returnsNull() {
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null);
    when(queueService.fetchBatch(anyInt())).thenReturn(Collections.emptyList());

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isNull();
    verify(counterGate, never()).hasHeadroomFor(any());
    verify(metricService).recordMetric(eq(StepConcurrencyHelper.METRIC_DEQUEUE_WALK_LENGTH), eq(0d));
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier1Hit_emitsDequeueClaimedMetric() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid("ne1").ambiance(ambiance).build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);

    stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    verify(metricService).incCounter(StepConcurrencyHelper.METRIC_DEQUEUE_TOTAL);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier2Claim_emitsDequeueClaimedAndWalkLengthMetrics() {
    NodeExecution claimed = NodeExecution.builder().uuid("ne2").ambiance(ambiance).mode(ExecutionMode.TASK).build();
    StepConcurrencyQueueEntry entry = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId("ne2")
                                          .planExecutionId("otherPlan")
                                          .accountId(ACCOUNT_ID)
                                          .createdAt(Instant.now())
                                          .build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null, claimed);
    when(queueService.fetchBatch(anyInt())).thenReturn(List.of(entry));
    when(counterGate.hasHeadroomFor(ACCOUNT_ID)).thenReturn(true);
    when(queueService.deleteByNodeExecutionId("ne2")).thenReturn(true);
    when(nodeExecutionService.getAmbiance(claimed)).thenReturn(ambiance);

    stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    // Once for the tier-1 walk_exhausted outcome, once for the tier-2 claimed outcome.
    verify(metricService, times(2)).incCounter(StepConcurrencyHelper.METRIC_DEQUEUE_TOTAL);
    verify(metricService).recordMetric(eq(StepConcurrencyHelper.METRIC_DEQUEUE_WALK_LENGTH), eq(1d));
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testFindQueuedNode_tier2HeadroomGate_emitsAccountCapSkipMetric() {
    StepConcurrencyQueueEntry atCap = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId("atCap")
                                          .planExecutionId("otherPlan")
                                          .accountId("accountAtCap")
                                          .createdAt(Instant.now())
                                          .build();
    when(nodeExecutionService.updateUsingQuery(any(), any())).thenReturn(null);
    when(queueService.fetchBatch(anyInt())).thenReturn(List.of(atCap));
    when(counterGate.hasHeadroomFor("accountAtCap")).thenReturn(false);

    Ambiance result = stepConcurrencyHelper.findQueuedNode(PLAN_EXECUTION_ID);
    assertThat(result).isNull();
    verify(metricService).incCounter(StepConcurrencyHelper.METRIC_DEQUEUE_SKIPS);
    // Every skip in the walk was capacity-bound -> outcome classified as cap_reached, not
    // walk_exhausted.
  }
}
