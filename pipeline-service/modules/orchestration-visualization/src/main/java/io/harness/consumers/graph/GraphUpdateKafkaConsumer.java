/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaProtoConsumer;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.sdk.execution.events.PmsSdkKafkaTopicResolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for processing OrchestrationLogEvents.
 * Consumes events from Kafka topic and triggers graph updates.
 *
 * This follows the same pattern as InitiateNodeEventKafkaConsumer for consistency.
 *
 * It uses two levels of deduplication:
 * 1. DuplicatedConsumerRecordProcessor: Deduplicates by Kafka offset (infrastructure level - handles redeliveries)
 * 2. PlanExecutionDeduplicationFilter: Deduplicates by planExecutionId within batch (business logic)
 *    This replicates the behavior of GraphUpdateRedisConsumer.mapPlanExecutionToMessages.
 */
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
public class GraphUpdateKafkaConsumer extends HKafkaProtoConsumer<OrchestrationLogEvent> {
  @Inject
  public GraphUpdateKafkaConsumer(GraphUpdateKafkaMessageHandler messageHandler,
      @Named(SDK_SERVICE_NAME) String serviceName,
      @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      PlanExecutionDeduplicationFilter planExecutionDeduplicationFilter) {
    super(KafkaConsumerConfig.<OrchestrationLogEvent>builder()
              .tClass(OrchestrationLogEvent.class)
              .isNoAck(false) // We want acknowledgment for processed messages
              .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED) // Parallel processing
              .consumerGroupId(PmsSdkKafkaTopicResolver.getGraphUpdateKafkaConsumerGroupId(serviceName))
              .topic(EventsFrameworkKafkaTopicResolver.getOrchestrationLogTopic())
              .kafkaBaseConfig(kafkaBaseConfig)
              .executorService(executorService)
              .consumerRecordFilters(List.of(planExecutionDeduplicationFilter))
              .messageHandler(messageHandler)
              .consumerMaintenanceListener(consumerMaintenanceListener)
              .build(),
        null);

    log.info("Initialized GraphUpdateKafkaConsumer with topic: {} and consumer group: {} with dual deduplication",
        EventsFrameworkKafkaTopicResolver.getOrchestrationLogTopic(),
        PmsSdkKafkaTopicResolver.getGraphUpdateKafkaConsumerGroupId(serviceName));
  }
}
