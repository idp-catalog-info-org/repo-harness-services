/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runtime toggle + NORMAL-mode capacity settings for the pipeline-execution-events flow governor.
 * When {@code enabled=false} (the default), the throttled orchestration consumers behave
 * bit-identically to the vanilla consumers and never touch the shared state cache.
 *
 * <p>Split of responsibilities: NORMAL-mode RPS is a capacity setting that changes on deploy and
 * lives here in yaml. THROTTLED-mode RPS is an incident-response dial that changes at runtime and
 * lives in Redis (see {@link FlowGovernorState}). Both share the same override → default fallback
 * shape so consumer code can treat them interchangeably.
 *
 * <p>{@code @NoArgsConstructor} is retained because Jackson uses it when deserializing the
 * {@code flowGovernorConfig:} block from {@code config.yml}; setters come from {@code @Data}.
 */
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowGovernorConfig {
  /** High default so NORMAL mode is effectively a no-op until operators tune it to 5× p99. */
  public static final int DEFAULT_NORMAL_RPS = 10_000;

  @JsonProperty("enabled") private boolean enabled;

  /** Per-pod RPS ceiling applied when a consumer has no entry in {@link #normalRpsByConsumer}. */
  @JsonProperty("normalRps") private int normalRps = DEFAULT_NORMAL_RPS;

  /**
   * Per-consumer overrides keyed by a {@link FlowGovernorConsumerKeys} constant. Missing entries
   * fall back to {@link #normalRps}.
   */
  @JsonProperty("normalRpsByConsumer") @Nullable private Map<String, Integer> normalRpsByConsumer;

  /** Data-plane tunables for the {@code ThrottledKafkaConsumer} subclass. */
  @JsonProperty("throttledConsumerConfig")
  private ThrottledConsumerConfig throttledConsumerConfig = ThrottledConsumerConfig.defaults();

  public static FlowGovernorConfig disabled() {
    return new FlowGovernorConfig(false, DEFAULT_NORMAL_RPS, null, ThrottledConsumerConfig.defaults());
  }

  /**
   * Resolves the NORMAL-mode RPS for a given consumer: per-consumer override if present, otherwise
   * the default {@link #normalRps}. Never returns null — NORMAL always has a numeric ceiling.
   */
  public int resolveNormalRpsFor(@Nullable String consumerKey) {
    if (normalRpsByConsumer != null && consumerKey != null) {
      Integer override = normalRpsByConsumer.get(consumerKey);
      if (override != null) {
        return override;
      }
    }
    return normalRps;
  }
}
