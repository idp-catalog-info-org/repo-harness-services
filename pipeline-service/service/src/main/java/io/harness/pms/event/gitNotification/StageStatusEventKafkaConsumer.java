/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.gitNotification;

import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PMS_SDK_DEDUPLICATOR;

import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.common.DuplicatedConsumerRecordProcessor;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaProtoConsumer;
import io.harness.pms.contracts.stage.StageStatusEvent;
import io.harness.pms.sdk.execution.events.PmsSdkKafkaTopicResolver;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class StageStatusEventKafkaConsumer extends HKafkaProtoConsumer<StageStatusEvent> {
  @Inject
  public StageStatusEventKafkaConsumer(StageStatusEventHandler messageHandler,
      @Named(SDK_SERVICE_NAME) String serviceName, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(PMS_SDK_DEDUPLICATOR) DuplicatedConsumerRecordProcessor duplicatedConsumerRecordProcessor) {
    super(KafkaConsumerConfig.<StageStatusEvent>builder()
              .tClass(StageStatusEvent.class)
              .topic(EventsFrameworkKafkaTopicResolver.getPipelineStageStatusTopic())
              .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED)
              .isNoAck(true)
              .kafkaBaseConfig(kafkaBaseConfig)
              .messageHandler(messageHandler)
              .consumerGroupId(PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(serviceName))
              .executorService(executorService)
              .consumerRecordFilters(List.of(duplicatedConsumerRecordProcessor))
              .consumerMaintenanceListener(consumerMaintenanceListener)
              .build(),
        null);
  }
}
