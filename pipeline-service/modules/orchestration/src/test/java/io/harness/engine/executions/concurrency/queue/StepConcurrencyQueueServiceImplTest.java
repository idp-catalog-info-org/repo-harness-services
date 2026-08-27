/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.queue;

import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import java.time.Instant;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;

public class StepConcurrencyQueueServiceImplTest extends CategoryTest {
  private DSLContext dsl;
  private MetricService metricService;
  private StepConcurrencyQueueServiceImpl service;

  @Before
  public void setUp() {
    dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
    metricService = mock(MetricService.class);
    service = new StepConcurrencyQueueServiceImpl(dsl, true, metricService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertDispatchesUpsertToJooq() {
    StepConcurrencyQueueEntry entry = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId("node1")
                                          .planExecutionId("plan1")
                                          .accountId("acc1")
                                          .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                          .build();
    service.insert(entry);
    // Confirms the fluent chain entered jOOQ.
    verify(dsl).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertWhenDisabledIsNoOp() {
    StepConcurrencyQueueServiceImpl disabled = new StepConcurrencyQueueServiceImpl(dsl, false, metricService);
    disabled.insert(StepConcurrencyQueueEntry.builder().nodeExecutionId("n").build());
    verify(dsl, never()).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertNullEntryIsNoOp() {
    service.insert(null);
    verify(dsl, never()).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertNullNodeExecutionIdIsNoOp() {
    service.insert(StepConcurrencyQueueEntry.builder().accountId("acc").build());
    verify(dsl, never()).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertSwallowsJooqFailureToProtectHotPath() {
    // Non-deep-stubs mock so we can force insertInto(...) itself to throw.
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.insertInto(any())).thenThrow(new RuntimeException("boom"));
    StepConcurrencyQueueServiceImpl throwing = new StepConcurrencyQueueServiceImpl(throwingDsl, true, metricService);
    // Must not throw — orchestration status-transition thread must not be blocked by a Postgres
    // blip. Drift self-heals via the daily rebuild + Mongo _id uniqueness.
    throwing.insert(StepConcurrencyQueueEntry.builder().nodeExecutionId("n").build());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsTrueWhenRowExisted() {
    when(dsl.deleteFrom(any()).where(any(org.jooq.Condition.class)).execute()).thenReturn(1);
    assertThat(service.deleteByNodeExecutionId("node1")).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsFalseWhenNoRow() {
    when(dsl.deleteFrom(any()).where(any(org.jooq.Condition.class)).execute()).thenReturn(0);
    assertThat(service.deleteByNodeExecutionId("node1")).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteWhenDisabledReturnsFalse() {
    StepConcurrencyQueueServiceImpl disabled = new StepConcurrencyQueueServiceImpl(dsl, false, metricService);
    assertThat(disabled.deleteByNodeExecutionId("node1")).isFalse();
    verify(dsl, never()).deleteFrom(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteNullNodeExecutionIdReturnsFalse() {
    assertThat(service.deleteByNodeExecutionId(null)).isFalse();
    verify(dsl, never()).deleteFrom(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsFalseOnJooqFailure() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.deleteFrom(any())).thenThrow(new RuntimeException("boom"));
    StepConcurrencyQueueServiceImpl throwing = new StepConcurrencyQueueServiceImpl(throwingDsl, true, metricService);
    assertThat(throwing.deleteByNodeExecutionId("node1")).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void fetchBatchWhenDisabledReturnsEmpty() {
    StepConcurrencyQueueServiceImpl disabled = new StepConcurrencyQueueServiceImpl(dsl, false, metricService);
    assertThat(disabled.fetchBatch(100)).isEmpty();
    verify(dsl, never()).select(any(org.jooq.SelectFieldOrAsterisk[].class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void fetchBatchReturnsEmptyOnJooqFailure() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.select(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
    StepConcurrencyQueueServiceImpl throwing = new StepConcurrencyQueueServiceImpl(throwingDsl, true, metricService);
    // Failure is swallowed; ops observability comes from the pipeline_step_concurrency_queue_operations_total
    // metric (outcome=error), not from a thrown exception.
    assertThat(throwing.fetchBatch(100)).isEmpty();
    verify(metricService).incCounter(StepConcurrencyQueueServiceImpl.METRIC_QUEUE_OPERATIONS);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void insertEmitsSuccessOperationMetric() {
    StepConcurrencyQueueEntry entry = StepConcurrencyQueueEntry.builder().nodeExecutionId("node1").build();
    service.insert(entry);
    verify(metricService).incCounter(StepConcurrencyQueueServiceImpl.METRIC_QUEUE_OPERATIONS);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void insertFailureEmitsErrorOperationMetric() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.insertInto(any())).thenThrow(new RuntimeException("boom"));
    StepConcurrencyQueueServiceImpl throwing = new StepConcurrencyQueueServiceImpl(throwingDsl, true, metricService);
    throwing.insert(StepConcurrencyQueueEntry.builder().nodeExecutionId("n").build());
    verify(metricService).incCounter(StepConcurrencyQueueServiceImpl.METRIC_QUEUE_OPERATIONS);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void fetchBatchEmitsBatchSizeMetric() {
    service.fetchBatch(100);
    verify(metricService)
        .recordMetric(eq(StepConcurrencyQueueServiceImpl.METRIC_QUEUE_FETCH_BATCH_SIZE),
            org.mockito.ArgumentMatchers.anyDouble());
  }
}
