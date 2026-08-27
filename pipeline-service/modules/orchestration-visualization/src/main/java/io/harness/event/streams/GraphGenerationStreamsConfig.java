/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.streams;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.kafka.config.KafkaBaseConfig;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for the CDC-based graph generation pipeline.
 *
 * <p>Wired from config.yml under {@code graphGenerationStreamsConfig}. The
 * {@link io.harness.event.streams.GraphGenerationStreamsModule} derives a
 * {@link io.harness.graph.consumer.GraphCDCConsumerConfig} from this object and binds it for
 * injection into {@link io.harness.graph.consumer.GraphCDCConsumer}.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class GraphGenerationStreamsConfig {
  /** Whether the CDC consumer is enabled. Defaults to false — enable via config or FF. */
  boolean enabled;

  /** Kafka connection settings (bootstrap servers, security, schema registry). */
  KafkaBaseConfig kafkaBaseConfig;

  // CDC input topic — all collections are routed to a single topic by Debezium's RegexRouter.
  // The consumer identifies the source collection from the ns.coll field in each CDC event.
  // Debezium source topic pattern: cdc.pms-harness.{collection}
  // After RegexRouter transform:   cdc.graph-events
  @Builder.Default String cdcTopic = "cdc.graph-events";

  /** Maximum records returned per Kafka poll call. */
  @Builder.Default int maxPollRecords = 1000;

  /**
   * Minimum bytes to fetch from the broker before returning a poll response.
   * Higher values increase batch sizes under load, reducing DB round-trips.
   * Default: 10 MB.
   */
  @Builder.Default int fetchMinBytes = 10000000;

  /**
   * Maximum time (ms) to wait for {@code fetchMinBytes} before returning anyway.
   * Bounds latency when traffic is low. Default: 1 000 ms.
   */
  @Builder.Default int fetchMaxWaitMs = 1000;

  /**
   * Maximum bytes to fetch per partition per poll.
   * Set to 10 MB to handle large CDC batches from high-parallelism executions (1 500+ nodes).
   * Default: 10 MB.
   */
  @Builder.Default int maxPartitionFetchBytes = 10485760;
}
