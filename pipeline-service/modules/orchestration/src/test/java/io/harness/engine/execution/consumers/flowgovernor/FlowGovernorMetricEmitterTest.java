/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.monitoring.EventMonitoringService;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FlowGovernorMetricEmitterTest extends CategoryTest {
  private static final String TOPIC = "initiate_node_event_topic";

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void recordDispatched_normal_bumpsInvokedCounter() {
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);

    emitter.recordDispatched(FlowGovernorState.Mode.NORMAL);
    emitter.recordDispatched(FlowGovernorState.Mode.THROTTLED);
    emitter.recordDispatched(FlowGovernorState.Mode.HALTED);

    verify(monitoring, times(3)).incCounter(FlowGovernorMetrics.INVOKED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void recordPauseOrResume_bumpsCounter() {
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);

    emitter.recordPauseOrResume();

    verify(monitoring, times(1)).incCounter(FlowGovernorMetrics.PAUSE_RESUME);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void emitGauges_normalMode_emitsOnlyQueueDepth() {
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);

    emitter.emitGauges(FlowGovernorState.Mode.NORMAL, 42, 1000);

    verify(monitoring, times(1)).sendMetric(FlowGovernorMetrics.QUEUE_DEPTH, 42L);
    // RPS gauges only make sense under THROTTLED — skip elsewhere to keep cardinality down.
    verify(monitoring, never()).sendMetric(eq(FlowGovernorMetrics.RPS_ACTUAL), anyLong());
    verify(monitoring, never()).sendMetric(eq(FlowGovernorMetrics.RPS_EXPECTED), anyLong());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void emitGauges_throttled_emitsAllThreeGauges() {
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);

    emitter.emitGauges(FlowGovernorState.Mode.THROTTLED, 7, 20);

    verify(monitoring, times(1)).sendMetric(FlowGovernorMetrics.QUEUE_DEPTH, 7L);
    verify(monitoring, times(1)).sendMetric(FlowGovernorMetrics.RPS_EXPECTED, 20L);
    verify(monitoring, atLeastOnce()).sendMetric(eq(FlowGovernorMetrics.RPS_ACTUAL), anyLong());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void emitGauges_haltedMode_emitsOnlyQueueDepth() {
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);

    emitter.emitGauges(FlowGovernorState.Mode.HALTED, 5, 1000);

    verify(monitoring, times(1)).sendMetric(FlowGovernorMetrics.QUEUE_DEPTH, 5L);
    verify(monitoring, never()).sendMetric(eq(FlowGovernorMetrics.RPS_EXPECTED), anyLong());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void nullMonitoringService_isSilentNoOp() {
    // SDK-only path: monitoring not bound. Emitter must not throw.
    FlowGovernorMetricEmitter emitter = new FlowGovernorMetricEmitter(null, TOPIC);

    emitter.recordDispatched(FlowGovernorState.Mode.NORMAL);
    emitter.recordPauseOrResume();
    emitter.emitGauges(FlowGovernorState.Mode.THROTTLED, 3, 10);
  }
}
