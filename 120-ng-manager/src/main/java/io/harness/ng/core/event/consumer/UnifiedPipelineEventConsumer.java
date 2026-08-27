/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import static io.harness.eventsframework.EventsFrameworkConstants.UNIFIED_DEPLOYMENT_EVENT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaProtoConsumer;
import io.harness.pms.contracts.execution.events.UnifiedDeploymentEvent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for processing unified deployment events from CI-manager.
 * Consumes UnifiedDeploymentEvent messages from the unified_pipeline_event topic.
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class UnifiedPipelineEventConsumer extends HKafkaProtoConsumer<UnifiedDeploymentEvent> {
  @Inject
  public UnifiedPipelineEventConsumer(UnifiedPipelineEventMessageHandler messageHandler,
      @Named("UnifiedPipelineEventExecutorService") ExecutorService executorService,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener) {
    super(KafkaConsumerConfig.<UnifiedDeploymentEvent>builder()
              .tClass(UnifiedDeploymentEvent.class)
              .isNoAck(false)
              .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED)
              .consumerGroupId("cd")
              .topic(UNIFIED_DEPLOYMENT_EVENT)
              .kafkaBaseConfig(kafkaBaseConfig)
              .executorService(executorService)
              .messageHandler(messageHandler)
              .consumerMaintenanceListener(consumerMaintenanceListener)
              .build(),
        null);

    log.info("Initialized UnifiedPipelineEventKafkaConsumer with topic: {} and consumer group: cd",
        UNIFIED_DEPLOYMENT_EVENT);
  }
}
