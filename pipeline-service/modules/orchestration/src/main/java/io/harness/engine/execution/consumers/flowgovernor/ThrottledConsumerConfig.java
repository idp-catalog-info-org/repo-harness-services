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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tunables for the {@code ThrottledKafkaConsumer} data-plane subclass.
 *
 * <p>{@code workers} and {@code queueCapacity} are independent knobs — workers hand {@code dispatch}
 * to the shared engine pool, so it is that pool, not the worker count, that bounds in-flight work.
 * Size each on its own terms:
 *
 */
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThrottledConsumerConfig {
  public static final int DEFAULT_WORKERS = 20;
  public static final int DEFAULT_QUEUE_CAPACITY = 200;
  public static final long DEFAULT_OFFER_TIMEOUT_MS = 100L;
  public static final int DEFAULT_HIGH_WATERMARK_PERCENT = 80;
  public static final int DEFAULT_LOW_WATERMARK_PERCENT = 30;
  public static final long DEFAULT_MODE_POLL_INTERVAL_MS = 5000L;
  /** {@code limitRefreshPeriod} handed to Resilience4j; spec §5.1 uses per-second buckets. */
  public static final long DEFAULT_RATE_LIMITER_REFRESH_PERIOD_MS = 1000L;
  /**
   * How long a worker will wait for a rate-limiter permit before checking mode again. Kept
   * short so a mode flip is observed even by threads already blocked in acquire().
   */
  public static final long DEFAULT_PERMIT_ACQUIRE_TIMEOUT_MS = 1000L;

  @JsonProperty("workers") private int workers = DEFAULT_WORKERS;
  @JsonProperty("queueCapacity") private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
  @JsonProperty("offerTimeoutMs") private long offerTimeoutMs = DEFAULT_OFFER_TIMEOUT_MS;
  @JsonProperty("highWatermarkPercent") private int highWatermarkPercent = DEFAULT_HIGH_WATERMARK_PERCENT;
  @JsonProperty("lowWatermarkPercent") private int lowWatermarkPercent = DEFAULT_LOW_WATERMARK_PERCENT;
  @JsonProperty("modePollIntervalMs") private long modePollIntervalMs = DEFAULT_MODE_POLL_INTERVAL_MS;
  @JsonProperty("rateLimiterRefreshPeriodMs")
  private long rateLimiterRefreshPeriodMs = DEFAULT_RATE_LIMITER_REFRESH_PERIOD_MS;
  @JsonProperty("permitAcquireTimeoutMs") private long permitAcquireTimeoutMs = DEFAULT_PERMIT_ACQUIRE_TIMEOUT_MS;

  public static ThrottledConsumerConfig defaults() {
    return new ThrottledConsumerConfig(DEFAULT_WORKERS, DEFAULT_QUEUE_CAPACITY, DEFAULT_OFFER_TIMEOUT_MS,
        DEFAULT_HIGH_WATERMARK_PERCENT, DEFAULT_LOW_WATERMARK_PERCENT, DEFAULT_MODE_POLL_INTERVAL_MS,
        DEFAULT_RATE_LIMITER_REFRESH_PERIOD_MS, DEFAULT_PERMIT_ACQUIRE_TIMEOUT_MS);
  }

  public int highWatermarkThreshold() {
    return (int) Math.ceil(queueCapacity * (highWatermarkPercent / 100.0));
  }

  public int lowWatermarkThreshold() {
    return (int) Math.floor(queueCapacity * (lowWatermarkPercent / 100.0));
  }
}
