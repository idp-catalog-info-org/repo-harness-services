/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepConcurrencyCounterGateTest extends CategoryTest {
  private static final String ACCOUNT = "acc";
  private static final long CLUSTER_LIMIT = 100;
  private static final int ACCOUNT_LIMIT = 10;

  private StepConcurrencyCounterService counterService;
  private PipelineSettingsService pipelineSettingsService;
  private MetricService metricService;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    counterService = mock(StepConcurrencyCounterService.class);
    pipelineSettingsService = mock(PipelineSettingsService.class);
    metricService = mock(MetricService.class);
    ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT).build();
    when(pipelineSettingsService.getMaxLeafStepConcurrency(anyString())).thenReturn(ACCOUNT_LIMIT);
  }

  private StepConcurrencyCounterGate gate(String mode) {
    return new StepConcurrencyCounterGate(counterService, pipelineSettingsService, metricService, CLUSTER_LIMIT, mode);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nonLeafModeIsAlwaysAllow() {
    StepConcurrencyCounterGate g = gate("enforce");
    when(counterService.getClusterCount()).thenReturn(200L); // above cluster limit
    StepConcurrencyCounterGate.ThrottleDecision d = g.shouldQueueWithReason(ExecutionMode.CHILDREN, ambiance);
    assertThat(d.isQueue()).isFalse();
    verify(counterService, never()).getClusterCount();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void disabledModeShortCircuitsWithoutRedisRead() {
    StepConcurrencyCounterGate g = gate("disabled");
    StepConcurrencyCounterGate.ThrottleDecision d = g.shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isFalse();
    verify(counterService, never()).getClusterCount();
    verify(counterService, never()).getAccountCount(anyString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void enforceMode_clusterAtCap_queuesWithReasonCluster() {
    when(counterService.getClusterCount()).thenReturn(CLUSTER_LIMIT);
    StepConcurrencyCounterGate.ThrottleDecision d = gate("enforce").shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isTrue();
    assertThat(d.getReason()).isEqualTo(StepConcurrencyCounterGate.REASON_CLUSTER);
    assertThat(d.getCurrentCount()).isEqualTo(CLUSTER_LIMIT);
    assertThat(d.getLimit()).isEqualTo(CLUSTER_LIMIT);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void enforceMode_accountAtCap_queuesWithReasonAccount() {
    when(counterService.getClusterCount()).thenReturn(0L);
    when(counterService.getAccountCount(ACCOUNT)).thenReturn((long) ACCOUNT_LIMIT);
    StepConcurrencyCounterGate.ThrottleDecision d = gate("enforce").shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isTrue();
    assertThat(d.getReason()).isEqualTo(StepConcurrencyCounterGate.REASON_ACCOUNT);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void enforceMode_bothUnderCap_allow() {
    when(counterService.getClusterCount()).thenReturn(0L);
    when(counterService.getAccountCount(ACCOUNT)).thenReturn(0L);
    StepConcurrencyCounterGate.ThrottleDecision d = gate("enforce").shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shadowMode_evenIfComputedQueue_alwaysReturnsAllow() {
    when(counterService.getClusterCount()).thenReturn(CLUSTER_LIMIT); // computes to queue
    StepConcurrencyCounterGate.ThrottleDecision d = gate("shadow").shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void failOpenOnRedisException() {
    when(counterService.getClusterCount()).thenThrow(new RuntimeException("redis down"));
    StepConcurrencyCounterGate.ThrottleDecision d = gate("enforce").shouldQueueWithReason(ExecutionMode.TASK, ambiance);
    assertThat(d.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nullGateModeDefaultsToShadow() {
    StepConcurrencyCounterGate g = gate(null);
    assertThat(g.getGateMode()).isEqualTo(StepConcurrencyCounterGate.GateMode.SHADOW);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void hasHeadroomFor_bothUnderCap_true() {
    when(counterService.getClusterCount()).thenReturn(0L);
    when(counterService.getAccountCount(ACCOUNT)).thenReturn(0L);
    assertThat(gate("enforce").hasHeadroomFor(ACCOUNT)).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void hasHeadroomFor_clusterAtCap_false() {
    when(counterService.getClusterCount()).thenReturn(CLUSTER_LIMIT);
    assertThat(gate("enforce").hasHeadroomFor(ACCOUNT)).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void hasHeadroomFor_accountAtCap_false() {
    when(counterService.getClusterCount()).thenReturn(0L);
    when(counterService.getAccountCount(ACCOUNT)).thenReturn((long) ACCOUNT_LIMIT);
    assertThat(gate("enforce").hasHeadroomFor(ACCOUNT)).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void hasHeadroomFor_failClosedOnRedisException() {
    when(counterService.getClusterCount()).thenThrow(new RuntimeException("redis down"));
    assertThat(gate("enforce").hasHeadroomFor(ACCOUNT)).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void hasHeadroomFor_disabledModeAlwaysTrueWithoutRedis() {
    assertThat(gate("disabled").hasHeadroomFor(ACCOUNT)).isTrue();
    verify(counterService, never()).getClusterCount();
  }
}
