/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution.consumers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PMS_SDK_DEDUPLICATOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConfig;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConsumerKeys;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateCache;
import io.harness.engine.execution.consumers.flowgovernor.ThrottledKafkaConsumer;
import io.harness.execution.InitiateNodeHandler;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.common.DuplicatedConsumerRecordProcessor;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.sdk.execution.events.PmsSdkKafkaTopicResolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;

@OwnedBy(PIPELINE)
@Singleton
public class InitiateNodeEventKafkaConsumer extends ThrottledKafkaConsumer<InitiateNodeEvent> {
  @Inject
  public InitiateNodeEventKafkaConsumer(InitiateNodeHandler messageHandler, @Named(SDK_SERVICE_NAME) String serviceName,
      @Named("EngineExecutorService") ExecutorService executorService,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(PMS_SDK_DEDUPLICATOR) DuplicatedConsumerRecordProcessor duplicatedConsumerRecordProcessor,
      FlowGovernorConfig governorConfig, @Nullable FlowGovernorStateCache stateCache) {
    super(KafkaConsumerConfig.<InitiateNodeEvent>builder()
              .tClass(InitiateNodeEvent.class)
              .isNoAck(true)
              .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED)
              .consumerGroupId(PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(serviceName))
              .topic(PmsSdkKafkaTopicResolver.getInitiateNodeTopic())
              .kafkaBaseConfig(kafkaBaseConfig)
              .executorService(executorService)
              .consumerRecordFilters(List.of(duplicatedConsumerRecordProcessor))
              .messageHandler(messageHandler)
              .consumerMaintenanceListener(consumerMaintenanceListener)
              .build(),
        null, FlowGovernorConsumerKeys.INITIATE_NODE, governorConfig, stateCache);
  }
}
