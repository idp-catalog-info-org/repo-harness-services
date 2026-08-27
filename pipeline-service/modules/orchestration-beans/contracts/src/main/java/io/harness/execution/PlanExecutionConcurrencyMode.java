/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Account-level pipeline execution concurrency mode. Mutually exclusive:
 * <ul>
 *   <li>{@code PARTITIONS} — the existing High/Low priority-partition model (default; regression-safe
 *       for existing PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY customers).</li>
 *   <li>{@code PER_PROJECT} — per-project caps (default + per-project overrides) backed by Redis
 *       counters, gated by PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.</li>
 * </ul>
 * Parsed from the {@code pipeline_execution_concurrency_mode} account setting
 * ({@code Partitions}/{@code PerProject}); unknown/absent values resolve to {@link #PARTITIONS}.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public enum PlanExecutionConcurrencyMode {
  PARTITIONS,
  PER_PROJECT;

  public static PlanExecutionConcurrencyMode fromSettingValue(String value) {
    if (value == null) {
      return PARTITIONS;
    }
    if ("PerProject".equalsIgnoreCase(value) || "PER_PROJECT".equalsIgnoreCase(value)) {
      return PER_PROJECT;
    }
    return PARTITIONS;
  }
}
