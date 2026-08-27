/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.kafka;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreams.eventhandlers.ModuleLicensesChangeEventHandler;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.metrics.NextGenMetricsContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GTM)
@Singleton
@Slf4j
public class ModuleLicensesKafkaCdcMessageHandler {
  @VisibleForTesting static final int MAX_RETRIES = 3;
  @VisibleForTesting static final String ERROR_METRIC = "gtm_module_licenses_kafka_consumer_error_count";

  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String SERVICE_NAME = "ng-manager";

  private final ModuleLicensesChangeEventHandler eventHandler;
  private final CdcKafkaConfig cdcKafkaConfig;
  private final MetricService metricService;
  private final long retryBackoffMs;

  @Inject
  public ModuleLicensesKafkaCdcMessageHandler(
      ModuleLicensesChangeEventHandler eventHandler, CdcKafkaConfig cdcKafkaConfig, MetricService metricService) {
    this(eventHandler, cdcKafkaConfig, metricService, 500L);
  }

  @VisibleForTesting
  ModuleLicensesKafkaCdcMessageHandler(ModuleLicensesChangeEventHandler eventHandler, CdcKafkaConfig cdcKafkaConfig,
      MetricService metricService, long retryBackoffMs) {
    this.eventHandler = eventHandler;
    this.cdcKafkaConfig = cdcKafkaConfig;
    this.metricService = metricService;
    this.retryBackoffMs = retryBackoffMs;
  }

  public boolean handleEvent(DebeziumChangeEvent event) {
    if (!isProcessingEnabled()) {
      log.debug("[CDC-Kafka][GTM] Kafka processing disabled (processingEnabled=false) — draining offset");
      return true;
    }
    if (event == null) {
      log.warn("[CDC-Kafka][GTM] Received null DebeziumChangeEvent for moduleLicenses; skipping");
      return true;
    }

    log.info("[CDC-Kafka][GTM] moduleLicenses event received: key={}, optype={}", event.getKey(), event.getOptype());

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        boolean result = eventHandler.handleEvent(event);
        if (result) {
          log.info("[CDC-Kafka][GTM] moduleLicenses event handled successfully: key={}, optype={}", event.getKey(),
              event.getOptype());
          return true;
        }
        // false = handler ran without exception but wrote 0 rows — treat as transient and retry
        if (attempt < MAX_RETRIES) {
          log.warn(
              "[CDC-Kafka][GTM] moduleLicenses handler returned false (attempt {}/{}), retrying: key={}, optype={}",
              attempt, MAX_RETRIES, event.getKey(), event.getOptype());
          sleep(attempt);
        } else {
          log.error(
              "[CDC-Kafka][GTM] moduleLicenses handler returned false after {} attempts — skipping: key={}, optype={}",
              MAX_RETRIES, event.getKey(), event.getOptype());
          incErrorMetric();
          return false;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.warn("[CDC-Kafka][GTM] moduleLicenses retry sleep interrupted: key={}", event.getKey());
        incErrorMetric();
        return false;
      } catch (Exception e) {
        if (attempt < MAX_RETRIES) {
          log.warn("[CDC-Kafka][GTM] moduleLicenses processing failed (attempt {}/{}), retrying: key={}, error={}",
              attempt, MAX_RETRIES, event.getKey(), e.getMessage());
          try {
            sleep(attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[CDC-Kafka][GTM] moduleLicenses retry sleep interrupted: key={}", event.getKey());
            incErrorMetric();
            return false;
          }
        } else {
          log.error("[CDC-Kafka][GTM] moduleLicenses processing failed after {} attempts: key={}, error={}",
              MAX_RETRIES, event.getKey(), e.getMessage(), e);
          incErrorMetric();
          return false;
        }
      }
    }
    return false;
  }

  @VisibleForTesting
  boolean isProcessingEnabled() {
    Optional<CdcKafkaConsumerConfig> consumerCfg =
        cdcKafkaConfig.getConsumer(ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY);
    return consumerCfg.map(CdcKafkaConsumerConfig::isProcessingEnabled).orElse(false);
  }

  private void sleep(int attempt) throws InterruptedException {
    Thread.sleep(retryBackoffMs * attempt);
  }

  private void incErrorMetric() {
    try (NextGenMetricsContext ignored = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
      metricService.incCounter(ERROR_METRIC);
    } catch (Exception e) {
      log.warn("[CDC-Kafka][GTM] Failed to record error metric '{}': {}", ERROR_METRIC, e.getMessage());
    }
  }
}
