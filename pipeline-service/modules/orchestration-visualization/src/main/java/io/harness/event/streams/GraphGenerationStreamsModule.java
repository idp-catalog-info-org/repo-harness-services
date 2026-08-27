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
import io.harness.graph.consumer.GraphCDCConsumer;
import io.harness.graph.consumer.GraphCDCConsumerConfig;
import io.harness.graph.service.GraphCDCService;
import io.harness.graph.service.impl.GraphCDCServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Guice module that wires the CDC-based graph generation pipeline.
 *
 * <p>Binds:
 * <ul>
 *   <li>{@link GraphCDCConsumer} — Kafka consumer that writes CDC events to PostgreSQL</li>
 *   <li>{@link GraphCDCService} → {@link GraphCDCServiceImpl}</li>
 * </ul>
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link GraphGenerationStreamsConfig} — the config object passed at construction time</li>
 *   <li>{@link GraphCDCConsumerConfig} — derived from the streams config (topics, fetch settings)</li>
 * </ul>
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GraphGenerationStreamsModule extends AbstractModule {
  private final GraphGenerationStreamsConfig config;

  public GraphGenerationStreamsModule(GraphGenerationStreamsConfig config) {
    this.config = config;
  }

  /**
   * Create a module with default configuration.
   */
  public static GraphGenerationStreamsModule withDefaults() {
    return new GraphGenerationStreamsModule(GraphGenerationStreamsConfig.builder().build());
  }

  /**
   * Create a module with custom configuration.
   */
  public static GraphGenerationStreamsModule withConfig(GraphGenerationStreamsConfig config) {
    return new GraphGenerationStreamsModule(config);
  }

  @Override
  protected void configure() {
    log.info("[MODULE] Configuring Graph Generation Streams Module");

    // Bind singleton components
    bind(GraphCDCConsumer.class).in(Singleton.class);
    bind(GraphCDCService.class).to(GraphCDCServiceImpl.class).in(Singleton.class);

    log.info("[MODULE] Graph Generation Streams Module configured");
  }

  @Provides
  @Singleton
  GraphGenerationStreamsConfig provideConfig() {
    log.info("[MODULE] Providing GraphGenerationStreamsConfig: {}", config);
    return config;
  }

  @Provides
  @Singleton
  GraphCDCConsumerConfig provideGraphCDCConsumerConfig() {
    // Derive CDC consumer config from streams config - enabled by default
    GraphCDCConsumerConfig cdcConsumerConfig = GraphCDCConsumerConfig.create(config.isEnabled(),
        config.getKafkaBaseConfig(), config.getCdcTopic(), config.getMaxPollRecords(), config.getFetchMinBytes(),
        config.getFetchMaxWaitMs(), config.getMaxPartitionFetchBytes());
    log.info("[MODULE] Providing GraphCDCConsumerConfig: enabled={}, bootstrapServers={}, topics={}",
        cdcConsumerConfig.isEnabled(), cdcConsumerConfig.getKafkaBaseConfig().getBootstrapServers(),
        cdcConsumerConfig.getTopics());
    return cdcConsumerConfig;
  }
}
