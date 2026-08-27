/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.io.Serializable;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowGovernorState implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum Mode { NORMAL, HALTED, THROTTLED }

  private Mode mode;

  /**
   * Default per-pod RPS applied when a consumer has no entry in {@link #targetRpsByConsumer}.
   * {@code null} is meaningful and expected in {@link Mode#NORMAL} and {@link Mode#HALTED} — no
   * throttle applies. Callers must null-check before using arithmetically; prefer
   * {@link #resolveRpsFor(String)} which returns {@code null} to signal "no limit".
   */
  @Nullable private Integer targetRps;

  /**
   * Per-consumer overrides keyed by a stable consumer identifier (e.g. {@code "initiateNode"},
   * {@code "initiateNodeBatch"}, {@code "orchestrationEvent"}, {@code "interruptEvent"}). Null or
   * missing entries fall back to {@link #targetRps}.
   */
  @Nullable private Map<String, Integer> targetRpsByConsumer;

  private long version;
  private String updatedBy;
  private long updatedAt;

  public static FlowGovernorState normal() {
    return FlowGovernorState.builder().mode(Mode.NORMAL).version(0L).build();
  }

  /**
   * Resolves the RPS to apply for a given consumer. Prefers the per-consumer override, falls back
   * to the default {@link #targetRps}, and returns {@code null} if neither is set (the caller
   * should treat this as "no limit / not applicable", e.g. in NORMAL mode).
   */
  @Nullable
  public Integer resolveRpsFor(@Nullable String consumerKey) {
    if (targetRpsByConsumer != null && consumerKey != null) {
      Integer override = targetRpsByConsumer.get(consumerKey);
      if (override != null) {
        return override;
      }
    }
    return targetRps;
  }
}
