/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.planqueue;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

public class PlanCreationDbQueueServiceImplTest extends CategoryTest {
  private DSLContext dsl;
  private MetricService metricService;
  private PlanCreationDbQueueServiceImpl service;

  @Before
  public void setUp() {
    dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
    metricService = mock(MetricService.class);
    service = new PlanCreationDbQueueServiceImpl(dsl, true, metricService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertDispatchesUpsertToJooq() {
    PlanCreationDbQueueEntry entry = PlanCreationDbQueueEntry.builder()
                                         .planExecutionId("plan1")
                                         .accountId("acc1")
                                         .orgId("org1")
                                         .projectId("proj1")
                                         .parentUniqueId("parent1")
                                         .priorityType("HIGH")
                                         .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                                         .build();
    service.insert(entry);
    verify(dsl).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertWhenDisabledIsNoOp() {
    PlanCreationDbQueueServiceImpl disabled = new PlanCreationDbQueueServiceImpl(dsl, false, metricService);
    disabled.insert(PlanCreationDbQueueEntry.builder().planExecutionId("p").build());
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
  public void insertNullPlanExecutionIdIsNoOp() {
    service.insert(PlanCreationDbQueueEntry.builder().accountId("acc").build());
    verify(dsl, never()).insertInto(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void insertSwallowsJooqFailureToProtectHotPath() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.insertInto(any())).thenThrow(new RuntimeException("boom"));
    PlanCreationDbQueueServiceImpl throwing = new PlanCreationDbQueueServiceImpl(throwingDsl, true, metricService);
    throwing.insert(PlanCreationDbQueueEntry.builder().planExecutionId("p").build());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsTrueWhenRowExisted() {
    when(dsl.deleteFrom(any()).where(any(org.jooq.Condition.class)).execute()).thenReturn(1);
    assertThat(service.deleteByPlanExecutionId("plan1")).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsFalseWhenNoRow() {
    when(dsl.deleteFrom(any()).where(any(org.jooq.Condition.class)).execute()).thenReturn(0);
    assertThat(service.deleteByPlanExecutionId("plan1")).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteWhenDisabledReturnsFalse() {
    PlanCreationDbQueueServiceImpl disabled = new PlanCreationDbQueueServiceImpl(dsl, false, metricService);
    assertThat(disabled.deleteByPlanExecutionId("plan1")).isFalse();
    verify(dsl, never()).deleteFrom(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteNullPlanExecutionIdReturnsFalse() {
    assertThat(service.deleteByPlanExecutionId(null)).isFalse();
    verify(dsl, never()).deleteFrom(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void deleteReturnsFalseOnJooqFailure() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.deleteFrom(any())).thenThrow(new RuntimeException("boom"));
    PlanCreationDbQueueServiceImpl throwing = new PlanCreationDbQueueServiceImpl(throwingDsl, true, metricService);
    assertThat(throwing.deleteByPlanExecutionId("plan1")).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void fetchBatchWhenDisabledReturnsEmpty() {
    PlanCreationDbQueueServiceImpl disabled = new PlanCreationDbQueueServiceImpl(dsl, false, metricService);
    assertThat(disabled.fetchBatch(100)).isEmpty();
    verify(dsl, never()).select(any(org.jooq.SelectFieldOrAsterisk[].class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void fetchBatchReturnsEmptyOnJooqFailure() {
    DSLContext throwingDsl = mock(DSLContext.class);
    when(throwingDsl.select(any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
    PlanCreationDbQueueServiceImpl throwing = new PlanCreationDbQueueServiceImpl(throwingDsl, true, metricService);
    assertThat(throwing.fetchBatch(100)).isEmpty();
  }
}
