/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.consumer;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.kafka.config.KafkaBaseConfig;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration for GraphCDCConsumer.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class GraphCDCConsumerConfig {
  private static final String DEFAULT_CONSUMER_GROUP = "graph-cdc-postgres-consumer";
  private static final int DEFAULT_MAX_POLL_INTERVAL_MS = 300000; // 5 minutes

  /**
   * Whether the consumer is enabled.
   */
  @Builder.Default boolean enabled = true;

  /**
   * Kafka base configuration for bootstrap servers and security settings.
   */
  KafkaBaseConfig kafkaBaseConfig;

  /**
   * Consumer group ID.
   */
  @Builder.Default String consumerGroup = DEFAULT_CONSUMER_GROUP;

  /**
   * List of CDC topics to consume from.
   * Default topics: nodeExecutions, planExecutions, nodeExecutionsInfo, outcomeInstances, graphUpdateInfo
   */
  List<String> topics;

  /**
   * Maximum poll interval in milliseconds.
   */
  @Builder.Default int maxPollIntervalMs = DEFAULT_MAX_POLL_INTERVAL_MS;
  @Builder.Default int maxPollRecordCount = 1000;

  /**
   * Minimum bytes to fetch from broker before returning from poll.
   * Default: 1MB - helps with batching in high-throughput scenarios.
   */
  @Builder.Default int fetchMinBytes = 1000000;

  /**
   * Maximum time to wait for fetchMinBytes before returning from poll.
   * Default: 1000ms (1 second).
   */
  @Builder.Default int fetchMaxWaitMs = 1000;

  /**
   * Maximum bytes to fetch per partition in a single poll.
   * Set to 10MB to handle large Avro-compressed batches from high-parallelism executions (1500+ nodes).
   * If a message exceeds this limit, the consumer may silently skip it or the deserializer may fail.
   */
  @Builder.Default int maxPartitionFetchBytes = 10485760;

  /**
   * Create config with specified parameters.
   * Used by GraphGenerationStreamsModule to construct from StreamsConfig.
   */
  public static GraphCDCConsumerConfig create(boolean enabled, KafkaBaseConfig kafkaBaseConfig, String cdcTopic,
      int maxPollRecordCount, int fetchMinBytes, int fetchMaxWaitMs, int maxPartitionFetchBytes) {
    return GraphCDCConsumerConfig.builder()
        .enabled(enabled)
        .maxPollRecordCount(maxPollRecordCount)
        .fetchMinBytes(fetchMinBytes)
        .fetchMaxWaitMs(fetchMaxWaitMs)
        .maxPartitionFetchBytes(maxPartitionFetchBytes)
        .kafkaBaseConfig(kafkaBaseConfig)
        .topics(List.of(cdcTopic))
        .build();
  }
}
