/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionModeUtils;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Additive counter-based step-concurrency gate. Reads Redis counters (cluster + per-account) via
 * {@link StepConcurrencyCounterService} and decides whether a leaf step must be queued.
 *
 * <p>Runs on top of the existing per-plan gate ({@code StepConcurrencyHelper.shouldQueue}) — both
 * gates must pass for a step to start. The counter gate is gated by the FF
 * {@code PIPE_USE_COUNTER_BASED_STEP_CONCURRENCY_GATE}; the caller consults the FF before invoking
 * this class.
 *
 * <p>Gate mode (config-driven, default {@code shadow}):
 * <ul>
 *   <li>{@code enforce} — decision drives queue-in.</li>
 *   <li>{@code shadow} — decision is computed + logged + metric emitted; caller always gets "allow".</li>
 *   <li>{@code disabled} — short-circuit to "allow" without a Redis read.</li>
 * </ul>
 *
 * <p>Fail-open on Redis error: over-throttling freezes customers, under-throttling is bounded and
 * recoverable via the daily rebuild.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class StepConcurrencyCounterGate {
  public static final String REASON_CLUSTER = "cluster";
  public static final String REASON_ACCOUNT = "account";
  public static final String REASON_NONE = "none";
  public static final String DECISION_QUEUE = "queue";
  public static final String DECISION_ALLOW = "allow";
  public static final String METRIC_GATE_DECISION = "pipeline_step_concurrency_gate_decision_total";

  public enum GateMode { ENFORCE, SHADOW, DISABLED }

  private final StepConcurrencyCounterService counterService;
  private final PipelineSettingsService pipelineSettingsService;
  private final MetricService metricService;
  private final long clusterLimit;
  private final GateMode gateMode;

  @Inject
  public StepConcurrencyCounterGate(StepConcurrencyCounterService counterService,
      PipelineSettingsService pipelineSettingsService, MetricService metricService,
      @Named("pipelineExecutionClusterStepConcurrencyLimit") Long clusterLimitInjected,
      @Named("stepConcurrencyGateMode") String gateModeInjected) {
    this.counterService = counterService;
    this.pipelineSettingsService = pipelineSettingsService;
    this.metricService = metricService;
    this.clusterLimit = clusterLimitInjected == null ? Long.MAX_VALUE : clusterLimitInjected;
    this.gateMode = parseGateMode(gateModeInjected);
  }

  private static GateMode parseGateMode(String value) {
    if (value == null || value.isBlank()) {
      return GateMode.SHADOW;
    }
    try {
      return GateMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("[STEP_CONCURRENCY] unknown stepConcurrencyGateMode='{}'; defaulting to SHADOW", value);
      return GateMode.SHADOW;
    }
  }

  /** Decision returned by the gate. */
  @Value
  public static class ThrottleDecision {
    boolean queue;
    String reason;
    long currentCount;
    long limit;

    public static ThrottleDecision allow() {
      return new ThrottleDecision(false, null, -1, -1);
    }
  }

  public GateMode getGateMode() {
    return gateMode;
  }

  /**
   * Gate decision. Cluster check first (cheaper), then account. Callers on the queue-in path
   * consult this after (and only if) their FF check + leaf-mode check pass.
   *
   * <p>In {@link GateMode#SHADOW} we compute the decision and log it but return "allow"; in
   * {@link GateMode#DISABLED} we short-circuit without a Redis read.
   */
  public ThrottleDecision shouldQueueWithReason(ExecutionMode mode, Ambiance ambiance) {
    if (!ExecutionModeUtils.isLeafMode(mode)) {
      return ThrottleDecision.allow();
    }
    if (gateMode == GateMode.DISABLED) {
      return ThrottleDecision.allow();
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      if (clusterLimit != Long.MAX_VALUE && clusterLimit > 0) {
        long clusterCount = counterService.getClusterCount();
        if (clusterCount >= clusterLimit) {
          return finalizeDecision(new ThrottleDecision(true, REASON_CLUSTER, clusterCount, clusterLimit), accountId);
        }
      }
      int accountLimit = pipelineSettingsService.getMaxLeafStepConcurrency(accountId);
      if (accountLimit > 0) {
        long accountCount = counterService.getAccountCount(accountId);
        if (accountCount >= accountLimit) {
          return finalizeDecision(new ThrottleDecision(true, REASON_ACCOUNT, accountCount, accountLimit), accountId);
        }
      }
      // Under both caps -> allow. Emit an "allow" sample so shadow-mode dashboards see the
      // baseline denominator and can compute queue rate.
      emitDecisionMetric(accountId, DECISION_ALLOW, REASON_NONE);
      return ThrottleDecision.allow();
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] gate failing open for account={} (cluster limit={})", accountId, clusterLimit, ex);
      return ThrottleDecision.allow();
    }
  }

  private ThrottleDecision finalizeDecision(ThrottleDecision computed, String accountId) {
    // Metric decision reflects what actually happens to the caller: in SHADOW mode we report
    // "allow" (that's what the caller sees) plus the would-be reason, so shadow observability
    // can still surface the counter that would have tripped.
    String decisionLabel = gateMode == GateMode.SHADOW ? DECISION_ALLOW : DECISION_QUEUE;
    emitDecisionMetric(accountId, decisionLabel, computed.getReason());

    if (gateMode == GateMode.SHADOW) {
      log.info("[STEP_CONCURRENCY_SHADOW] would queue reason={} current={} limit={} account={}", computed.getReason(),
          computed.getCurrentCount(), computed.getLimit(), accountId);
      return ThrottleDecision.allow();
    }
    log.info("[STEP_CONCURRENCY] queue reason={} current={} limit={} account={}", computed.getReason(),
        computed.getCurrentCount(), computed.getLimit(), accountId);
    return computed;
  }

  private void emitDecisionMetric(String accountId, String decision, String reason) {
    try (PmsMetricContextGuard guard =
             new PmsMetricContextGuard(ImmutableMap.of("accountId", accountId == null ? "" : accountId, "decision",
                 decision, "reason", reason, "mode", gateMode.name().toLowerCase()))) {
      metricService.incCounter(METRIC_GATE_DECISION);
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY] gate decision metric emission failed", ex);
    }
  }

  /**
   * Returns true if the current cluster + account counters have headroom for one more leaf step.
   * Used by the tier-2 dequeue walker before consuming a candidate from the queue store.
   *
   * <p>Fail-closed on Redis error: returning {@code false} delays drain but doesn't breach the cap.
   */
  public boolean hasHeadroomFor(String accountId) {
    if (gateMode == GateMode.DISABLED) {
      return true;
    }
    try {
      if (clusterLimit != Long.MAX_VALUE && clusterLimit > 0 && counterService.getClusterCount() >= clusterLimit) {
        return false;
      }
      int accountLimit = pipelineSettingsService.getMaxLeafStepConcurrency(accountId);
      if (accountLimit > 0 && counterService.getAccountCount(accountId) >= accountLimit) {
        return false;
      }
      return true;
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] hasHeadroomFor failing closed for account={}", accountId, ex);
      return false;
    }
  }
}
