/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.monitoring.EventMonitoringService;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Emits the five flow-governor metrics defined in
 * {@code pipeline_execution_events_flow_governor_metrics.yaml}. Kept separate from
 * {@link ThrottledKafkaConsumer} so the ingestion + worker loops stay focused on control flow;
 * label building and metric-context wiring live here.
 *
 * <p>All emission calls short-circuit if {@link EventMonitoringService} is null (SDK-only path,
 * or when the governor is disabled and this emitter isn't constructed at all). Callers still
 * null-check the emitter itself — see {@link ThrottledKafkaConsumer}.
 *
 * <p><b>Actual RPS accounting</b>: workers atomically bump a per-second counter on each dispatch.
 * The mode poller reads and resets the counter every {@code modePollIntervalMs} tick, dividing by
 * the elapsed seconds since the last read to get a rate estimate.
 */
@OwnedBy(PIPELINE)
@Slf4j
final class FlowGovernorMetricEmitter {
  private final EventMonitoringService monitoringService;
  private final String topic;

  private final AtomicLong dispatchCounter = new AtomicLong();
  /** Nanotime of the last {@link #emitGauges} call, used to derive the actual-RPS window length. */
  private final AtomicLong lastGaugeEmitNanos = new AtomicLong(System.nanoTime());

  FlowGovernorMetricEmitter(EventMonitoringService monitoringService, String topic) {
    this.monitoringService = monitoringService;
    this.topic = topic;
  }

  /**
   * Increment the invoked counter for one record. Labelled by the mode observed at dequeue time —
   * NORMAL/THROTTLED for dispatched records, HALTED for drops. Also feeds the actual-RPS window.
   */
  void recordDispatched(FlowGovernorState.Mode mode) {
    if (mode != FlowGovernorState.Mode.HALTED) {
      dispatchCounter.incrementAndGet();
    }
    incWithLabels(FlowGovernorMetrics.INVOKED,
        ImmutableMap.of(FlowGovernorMetrics.LABEL_MODE, mode.name(), FlowGovernorMetrics.LABEL_TOPIC, topic));
  }

  /** Increment the pause/resume counter. Same labels on both actions per spec (topic only). */
  void recordPauseOrResume() {
    incWithLabels(FlowGovernorMetrics.PAUSE_RESUME, ImmutableMap.of(FlowGovernorMetrics.LABEL_TOPIC, topic));
  }

  /**
   * Emit the three gauges — {@code queue_depth} unconditionally, and the RPS gauges only when
   * THROTTLED. {@code expectedRps} is the rate limiter's current {@code limitForPeriod}.
   */
  void emitGauges(FlowGovernorState.Mode mode, int queueDepth, int expectedRps) {
    sendWithLabels(
        FlowGovernorMetrics.QUEUE_DEPTH, (long) queueDepth, ImmutableMap.of(FlowGovernorMetrics.LABEL_TOPIC, topic));

    if (mode != FlowGovernorState.Mode.THROTTLED) {
      // Drain the dispatch counter even when not throttled so a subsequent THROTTLED window
      // starts fresh instead of inheriting whatever piled up during NORMAL.
      dispatchCounter.set(0);
      lastGaugeEmitNanos.set(System.nanoTime());
      return;
    }

    long nowNanos = System.nanoTime();
    long lastNanos = lastGaugeEmitNanos.getAndSet(nowNanos);
    long dispatched = dispatchCounter.getAndSet(0);
    double elapsedSeconds = Math.max(1e-6, (nowNanos - lastNanos) / 1_000_000_000.0);
    long actualRps = Math.round(dispatched / elapsedSeconds);

    ImmutableMap<String, String> labels = ImmutableMap.of(FlowGovernorMetrics.LABEL_TOPIC, topic);
    sendWithLabels(FlowGovernorMetrics.RPS_ACTUAL, actualRps, labels);
    sendWithLabels(FlowGovernorMetrics.RPS_EXPECTED, (long) expectedRps, labels);
  }

  private void incWithLabels(String metric, @Nullable ImmutableMap<String, String> labels) {
    if (monitoringService == null) {
      return;
    }
    try (PmsMetricContextGuard ignored = new PmsMetricContextGuard(labels)) {
      monitoringService.incCounter(metric);
    } catch (Exception ex) {
      log.warn("Flow governor metric emit failed for [{}] on topic [{}].", metric, topic, ex);
    }
  }

  private void sendWithLabels(String metric, long value, @Nullable ImmutableMap<String, String> labels) {
    if (monitoringService == null) {
      return;
    }
    try (PmsMetricContextGuard ignored = new PmsMetricContextGuard(labels)) {
      monitoringService.sendMetric(metric, value);
    } catch (Exception ex) {
      log.warn("Flow governor metric emit failed for [{}] on topic [{}].", metric, topic, ex);
    }
  }
}
