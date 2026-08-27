/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kafka.common.ConsumerMaintenanceListener;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guice module for unified pipeline event Kafka consumer bindings.
 */
@OwnedBy(HarnessTeam.PL)
public class UnifiedPipelineEventConsumerModule extends AbstractModule {
  private static UnifiedPipelineEventConsumerModule instance;

  public static UnifiedPipelineEventConsumerModule getInstance() {
    if (instance == null) {
      instance = new UnifiedPipelineEventConsumerModule();
    }
    return instance;
  }

  private UnifiedPipelineEventConsumerModule() {}

  @Override
  protected void configure() {
    bind(UnifiedPipelineEventMessageHandler.class).in(Singleton.class);
    bind(UnifiedPipelineEventConsumer.class).in(Singleton.class);
    bind(ConsumerMaintenanceListener.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  @Named("UnifiedPipelineEventExecutorService")
  ExecutorService unifiedPipelineEventExecutorService() {
    return Executors.newFixedThreadPool(
        5, new ThreadFactoryBuilder().setNameFormat("unified-pipeline-event-handler-%d").build());
  }
}
