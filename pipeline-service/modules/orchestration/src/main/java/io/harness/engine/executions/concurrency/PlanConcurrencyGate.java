/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-project pipeline execution concurrency gate. Decides whether a new execution must be queued,
 * using the Redis counters ({@link PlanConcurrencyCounterService}) and the caps from
 * {@link PipelineSettingsService}.
 *
 * <p>Only used in {@code PER_PROJECT} mode (the caller checks). Modes:
 * <ul>
 *   <li>{@code enforce} — decision drives queue-in.</li>
 *   <li>{@code shadow} — decision computed, logged and metered; caller always gets "allow".</li>
 *   <li>{@code disabled} — returns "allow" without a Redis read.</li>
 * </ul>
 *
 * <p>Checks the project cap first, then the account total (the account total is authoritative and
 * wins when both are hit). Fails open on the gate path, fails closed on the drain-headroom path.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyGate {
  public static final String REASON_PROJECT = "project";
  public static final String REASON_ACCOUNT = "account";
  public static final String REASON_NONE = "none";
  public static final String DECISION_QUEUE = "queue";
  public static final String DECISION_ALLOW = "allow";
  public static final String METRIC_GATE_DECISION = "pipeline_plan_concurrency_gate_decision_total";

  public enum GateMode { ENFORCE, SHADOW, DISABLED }

  private final PlanConcurrencyCounterService counterService;
  private final PipelineSettingsService pipelineSettingsService;
  private final MetricService metricService;
  private final GateMode gateMode;
  private final boolean counterMutationEnabled;

  @Inject
  public PlanConcurrencyGate(PlanConcurrencyCounterService counterService,
      PipelineSettingsService pipelineSettingsService, MetricService metricService,
      @Named("planConcurrencyGateMode") String gateModeInjected,
      @Named("planConcurrencyCounterMutationEnabled") boolean counterMutationEnabled) {
    this.counterService = counterService;
    this.pipelineSettingsService = pipelineSettingsService;
    this.metricService = metricService;
    this.gateMode = parseGateMode(gateModeInjected);
    this.counterMutationEnabled = counterMutationEnabled;
    // Log this misconfiguration once here, not per admission (both fields are final).
    if (this.gateMode == GateMode.ENFORCE && !counterMutationEnabled) {
      log.warn("[PLAN_CONCURRENCY] ENFORCE mode with counter mutation disabled; gate admits without "
          + "reserving until planConcurrencyCounterMutationEnabled is true");
    }
  }

  private static GateMode parseGateMode(String value) {
    if (value == null || value.isBlank()) {
      return GateMode.SHADOW;
    }
    try {
      return GateMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("[PLAN_CONCURRENCY] unknown planConcurrencyGateMode='{}'; defaulting to SHADOW", value);
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
   * Gate decision for a new execution: project cap first, then the account total. {@code SHADOW}
   * computes and logs but returns "allow"; {@code DISABLED} returns "allow" without a Redis read.
   * Always emits the decision metric.
   */
  public ThrottleDecision shouldQueue(String accountId, String parentUniqueId) {
    if (gateMode == GateMode.DISABLED) {
      return ThrottleDecision.allow();
    }
    try {
      // Cap and counter are both keyed by the stable parentUniqueId, so it survives project moves.
      if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
        int projectCap = pipelineSettingsService.getEffectiveProjectConcurrency(accountId, parentUniqueId);
        if (projectCap > 0) {
          long projectCount = counterService.getProjectCount(accountId, parentUniqueId);
          if (projectCount >= projectCap) {
            return finalizeDecision(new ThrottleDecision(true, REASON_PROJECT, projectCount, projectCap), accountId);
          }
        }
      }
      long accountTotal = pipelineSettingsService.getMaxConcurrency(accountId);
      if (accountTotal != Long.MAX_VALUE && accountTotal > 0) {
        long accountCount = counterService.getAccountCount(accountId);
        if (accountCount >= accountTotal) {
          return finalizeDecision(new ThrottleDecision(true, REASON_ACCOUNT, accountCount, accountTotal), accountId);
        }
      }
      emitDecisionMetric(accountId, DECISION_ALLOW, REASON_NONE);
      return ThrottleDecision.allow();
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] gate failing open for account={} parentUniqueId={}", accountId, parentUniqueId, ex);
      return ThrottleDecision.allow();
    }
  }

  private ThrottleDecision finalizeDecision(ThrottleDecision computed, String accountId) {
    String decisionLabel = gateMode == GateMode.SHADOW ? DECISION_ALLOW : DECISION_QUEUE;
    emitDecisionMetric(accountId, decisionLabel, computed.getReason());
    if (gateMode == GateMode.SHADOW) {
      log.info("[PLAN_CONCURRENCY_SHADOW] would queue reason={} current={} limit={} account={}", computed.getReason(),
          computed.getCurrentCount(), computed.getLimit(), accountId);
      return ThrottleDecision.allow();
    }
    log.info("[PLAN_CONCURRENCY] queue reason={} current={} limit={} account={}", computed.getReason(),
        computed.getCurrentCount(), computed.getLimit(), accountId);
    return computed;
  }

  private void emitDecisionMetric(String accountId, String decision, String reason) {
    try (PmsMetricContextGuard guard =
             new PmsMetricContextGuard(ImmutableMap.of("accountId", accountId == null ? "" : accountId, "decision",
                 decision, "reason", reason, "mode", gateMode.name().toLowerCase()))) {
      metricService.incCounter(METRIC_GATE_DECISION);
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY] gate decision metric emission failed", ex);
    }
  }

  /**
   * Which cap (if any) is blocking one more execution. Lets the drain walker cache a "full" scope
   * for the rest of a single batch so it does not re-run the gate for every queued entry of a
   * project/account that is already at its limit.
   *
   * <ul>
   *   <li>{@code HAS_HEADROOM} — admit.</li>
   *   <li>{@code PROJECT_FULL} — the per-project cap is hit (account may still have room).</li>
   *   <li>{@code ACCOUNT_FULL} — the account total is hit (blocks every project on the account).</li>
   *   <li>{@code INDETERMINATE} — ENFORCE fail-closed on a Redis error: requeue this candidate, but
   *       the reason is unknown so callers must NOT cache it (the blip may be transient).</li>
   * </ul>
   */
  public enum HeadroomDecision { HAS_HEADROOM, PROJECT_FULL, ACCOUNT_FULL, INDETERMINATE }

  /**
   * True if both counters have room for one more execution. Used by the drain walker and queue
   * helpers before admitting a candidate. SHADOW always returns true (after logging); ENFORCE
   * returns the real check and fails closed on a Redis error (delays drain, never breaches a cap).
   */
  public boolean hasHeadroomFor(String accountId, String parentUniqueId) {
    return evaluateHeadroom(accountId, parentUniqueId) == HeadroomDecision.HAS_HEADROOM;
  }

  /**
   * Like {@link #hasHeadroomFor} but reports which cap is blocking so the drainer can skip the rest
   * of a full scope in the same walk. Same mode semantics: {@code DISABLED}/{@code SHADOW} always
   * return {@code HAS_HEADROOM} (SHADOW logs the real decision first); {@code ENFORCE} returns the
   * real decision and {@code INDETERMINATE} on a Redis error (fail-closed — requeue, do not cache).
   */
  public HeadroomDecision evaluateHeadroom(String accountId, String parentUniqueId) {
    if (gateMode == GateMode.DISABLED) {
      return HeadroomDecision.HAS_HEADROOM;
    }

    if (gateMode == GateMode.SHADOW) {
      // Compute for observability but always allow.
      try {
        HeadroomDecision decision = computeHeadroomDecision(accountId, parentUniqueId);
        log.info("[PLAN_CONCURRENCY] hasHeadroomFor shadow decision: decision={} account={}", decision, accountId);
        return HeadroomDecision.HAS_HEADROOM;
      } catch (Exception ex) {
        log.warn("[PLAN_CONCURRENCY] hasHeadroomFor shadow exception for account={} parentUniqueId={}", accountId,
            parentUniqueId, ex);
        return HeadroomDecision.HAS_HEADROOM; // fail-open
      }
    }

    try {
      return computeHeadroomDecision(accountId, parentUniqueId);
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] hasHeadroomFor failing closed for account={} parentUniqueId={}", accountId,
          parentUniqueId, ex);
      return HeadroomDecision.INDETERMINATE; // fail-closed: requeue but do not cache the scope
    }
  }

  private HeadroomDecision computeHeadroomDecision(String accountId, String parentUniqueId) {
    if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
      int projectCap = pipelineSettingsService.getEffectiveProjectConcurrency(accountId, parentUniqueId);
      if (projectCap > 0 && counterService.getProjectCount(accountId, parentUniqueId) >= projectCap) {
        return HeadroomDecision.PROJECT_FULL;
      }
    }
    long accountTotal = pipelineSettingsService.getMaxConcurrency(accountId);
    if (accountTotal != Long.MAX_VALUE && accountTotal > 0
        && counterService.getAccountCount(accountId) >= accountTotal) {
      return HeadroomDecision.ACCOUNT_FULL;
    }
    return HeadroomDecision.HAS_HEADROOM;
  }

  /** Outcome of an atomic reserve attempt. */
  public enum ReserveOutcome {
    /** ENFORCE: slot reserved. The reserve owns the {@code +1}, so the status-flip must not re-count. */
    RESERVED,
    /** ENFORCE: at cap — caller must requeue; nothing mutated. */
    DENIED,
    /** SHADOW/DISABLED: nothing reserved — the hook applies the {@code +1} on the flip as today. */
    NOT_RESERVED
  }

  /**
   * Atomically reserve one slot for the project + account, closing the check-then-act race that lets
   * drainers on different pods overshoot a cap (PIPE-35674).
   *
   * <ul>
   *   <li>{@code ENFORCE} — atomic conditional-increment. {@link ReserveOutcome#RESERVED} means the
   *       reserve owns the {@code +1} (caller suppresses the hook on the flip, releases if it fails);
   *       {@link ReserveOutcome#DENIED} means at cap.
   *   <li>{@code SHADOW}/{@code DISABLED} — reserves nothing, returns {@link ReserveOutcome#NOT_RESERVED}.
   * </ul>
   *
   * <p>Fails closed in {@code ENFORCE} (returns {@code DENIED}) so a Redis blip requeues; fails open
   * in {@code SHADOW}.
   */
  public ReserveOutcome tryReserveSlot(String accountId, String parentUniqueId) {
    if (gateMode == GateMode.DISABLED) {
      return ReserveOutcome.NOT_RESERVED;
    }
    // ENFORCE needs the hook on, else the reserved +1 never decrements and leaks. Degrade to
    // NOT_RESERVED; logged once in the constructor.
    if (gateMode == GateMode.ENFORCE && !counterMutationEnabled) {
      return ReserveOutcome.NOT_RESERVED;
    }
    if (gateMode == GateMode.SHADOW) {
      try {
        boolean wouldHaveHeadroom = computeHeadroomDecision(accountId, parentUniqueId) == HeadroomDecision.HAS_HEADROOM;
        // debug, not info: SHADOW is the default mode and the decision metric already covers it.
        log.debug("[PLAN_CONCURRENCY_SHADOW] tryReserveSlot would {} account={} parentUniqueId={}",
            wouldHaveHeadroom ? "reserve" : "deny", accountId, parentUniqueId);
      } catch (Exception ex) {
        log.warn("[PLAN_CONCURRENCY] tryReserveSlot shadow exception for account={} parentUniqueId={}", accountId,
            parentUniqueId, ex);
      }
      return ReserveOutcome.NOT_RESERVED;
    }

    // ENFORCE: resolve the caps and hand them to the atomic reserve. A non-positive project cap or
    // unlimited/absent account total is passed as -1 ("no cap") so that leg increments without
    // rejecting.
    try {
      long projectCap = -1L;
      if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
        int effectiveProjectCap = pipelineSettingsService.getEffectiveProjectConcurrency(accountId, parentUniqueId);
        if (effectiveProjectCap > 0) {
          projectCap = effectiveProjectCap;
        }
      }
      long accountTotal = pipelineSettingsService.getMaxConcurrency(accountId);
      long accountCap = (accountTotal != Long.MAX_VALUE && accountTotal > 0) ? accountTotal : -1L;

      boolean reserved = counterService.tryReserveSlot(accountId, parentUniqueId, projectCap, accountCap);
      return reserved ? ReserveOutcome.RESERVED : ReserveOutcome.DENIED;
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] tryReserveSlot failing closed for account={} parentUniqueId={}", accountId,
          parentUniqueId, ex);
      return ReserveOutcome.DENIED;
    }
  }

  /**
   * Release a reserved slot when admission failed after the reserve (flip lost or threw). Decrements
   * both legs; counters clamp at zero so over-release can't go negative. Each leg has its own
   * try/catch so one leg failing still frees the other.
   */
  public void releaseReservedSlot(String accountId, String parentUniqueId) {
    if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
      try {
        counterService.incrementProject(accountId, parentUniqueId, -1);
      } catch (Exception ex) {
        log.warn("[PLAN_CONCURRENCY] releaseReservedSlot project leg failed for account={} parentUniqueId={}",
            accountId, parentUniqueId, ex);
      }
    }
    try {
      counterService.incrementAccount(accountId, -1);
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] releaseReservedSlot account leg failed for account={}", accountId, ex);
    }
  }
}
