/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.kafkaconsumer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;
import io.harness.pms.redisConsumer.PipelineExecutionSummaryCDChangeEventHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Message handler for the PIPELINE CDC Kafka consumer that processes
 * {@code pmsMongo.pms-harness.planExecutionsSummary} events.
 *
 * <p>Processing is gated by {@code cdcKafka.consumers[planExecutionsSummaryCD].processingEnabled}.
 * When disabled, events are silently drained (offset committed) without being written to TimescaleDB.
 * This enables safe phased cutover: register consumer → verify → enable processing → short-circuit Redis.
 *
 * <p>Retry logic: up to {@value MAX_RETRIES} attempts with exponential back-off before giving up.
 * On final failure the offset is still committed to avoid blocking the partition.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class PipelineExecutionSummaryCDKafkaCdcMessageHandler {
  @VisibleForTesting static final int MAX_RETRIES = 3;

  private final PipelineExecutionSummaryCDChangeEventHandler eventHandler;
  private final CdcKafkaConfig cdcKafkaConfig;
  private final long retryBackoffMs;

  @Inject
  public PipelineExecutionSummaryCDKafkaCdcMessageHandler(
      PipelineExecutionSummaryCDChangeEventHandler eventHandler, CdcKafkaConfig cdcKafkaConfig) {
    this(eventHandler, cdcKafkaConfig, 500L);
  }

  @VisibleForTesting
  PipelineExecutionSummaryCDKafkaCdcMessageHandler(
      PipelineExecutionSummaryCDChangeEventHandler eventHandler, CdcKafkaConfig cdcKafkaConfig, long retryBackoffMs) {
    this.eventHandler = eventHandler;
    this.cdcKafkaConfig = cdcKafkaConfig;
    this.retryBackoffMs = retryBackoffMs;
  }

  public boolean handleEvent(DebeziumChangeEvent event) {
    if (!isProcessingEnabled()) {
      log.debug("[CDC-Kafka][PIPE] Kafka processing disabled (processingEnabled=false) — draining offset");
      return true;
    }
    if (event == null) {
      log.warn("[CDC-Kafka][PIPE] Received null DebeziumChangeEvent for planExecutionsSummary; skipping");
      return true;
    }

    log.debug(
        "[CDC-Kafka][PIPE] planExecutionsSummary event received: key={}, optype={}", event.getKey(), event.getOptype());

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        boolean result = eventHandler.handleEvent(event);
        if (result) {
          return true;
        }
        // false = handler ran without exception but wrote 0 rows — treat as transient and retry
        if (attempt < MAX_RETRIES) {
          log.warn("[CDC-Kafka][PIPE] planExecutionsSummary handler returned false (attempt {}/{}), retrying: key={}, "
                  + "optype={}",
              attempt, MAX_RETRIES, event.getKey(), event.getOptype());
          sleep(attempt);
        } else {
          log.error("[CDC-Kafka][PIPE] planExecutionsSummary handler returned false after {} attempts — skipping: "
                  + "key={}, optype={}",
              MAX_RETRIES, event.getKey(), event.getOptype());
          return false;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.warn("[CDC-Kafka][PIPE] planExecutionsSummary retry sleep interrupted: key={}", event.getKey());
        return false;
      } catch (Exception e) {
        if (attempt < MAX_RETRIES) {
          log.warn(
              "[CDC-Kafka][PIPE] planExecutionsSummary processing failed (attempt {}/{}), retrying: key={}, error={}",
              attempt, MAX_RETRIES, event.getKey(), e.getMessage());
          try {
            sleep(attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[CDC-Kafka][PIPE] planExecutionsSummary retry sleep interrupted: key={}", event.getKey());
            return false;
          }
        } else {
          log.error("[CDC-Kafka][PIPE] planExecutionsSummary processing failed after {} attempts: key={}, error={}",
              MAX_RETRIES, event.getKey(), e.getMessage(), e);
          return false;
        }
      }
    }
    return false;
  }

  @VisibleForTesting
  boolean isProcessingEnabled() {
    Optional<CdcKafkaConsumerConfig> consumerCfg =
        cdcKafkaConfig.getConsumer(PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_CONFIG_KEY);
    return consumerCfg.map(CdcKafkaConsumerConfig::isProcessingEnabled).orElse(false);
  }

  private void sleep(int attempt) throws InterruptedException {
    Thread.sleep(retryBackoffMs * attempt);
  }
}
