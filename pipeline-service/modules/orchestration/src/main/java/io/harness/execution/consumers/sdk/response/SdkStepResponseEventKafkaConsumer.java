/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution.consumers.sdk.response;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PMS_SDK_DEDUPLICATOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConfig;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConsumerKeys;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateCache;
import io.harness.engine.execution.consumers.flowgovernor.ThrottledKafkaConsumer;
import io.harness.execution.SdkResponseHandler;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.common.DuplicatedConsumerRecordProcessor;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.sdk.execution.events.PmsSdkKafkaTopicResolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class SdkStepResponseEventKafkaConsumer extends ThrottledKafkaConsumer<SdkResponseEventProto> {
  @Inject
  public SdkStepResponseEventKafkaConsumer(SdkResponseHandler messageHandler,
      @Named(SDK_SERVICE_NAME) String serviceName, @Named("SdkResponseExecutorService") ExecutorService executorService,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(PMS_SDK_DEDUPLICATOR) DuplicatedConsumerRecordProcessor duplicatedConsumerRecordProcessor,
      FlowGovernorConfig governorConfig, @Nullable FlowGovernorStateCache stateCache) {
    super(KafkaConsumerConfig.<SdkResponseEventProto>builder()
              .tClass(SdkResponseEventProto.class)
              .topic(PmsSdkKafkaTopicResolver.getPipelineSdkStepResponseTopic())
              .consumerGroupId(PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(serviceName))
              .kafkaBaseConfig(kafkaBaseConfig)
              .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED)
              .isNoAck(true)
              .executorService(executorService)
              .messageHandler(messageHandler)
              .consumerMaintenanceListener(consumerMaintenanceListener)
              .consumerRecordFilters(List.of(duplicatedConsumerRecordProcessor))
              .build(),
        null, FlowGovernorConsumerKeys.SDK_STEP_RESPONSE, governorConfig, stateCache);
    log.info("Initialized SdkStepResponseEventKafkaConsumer with topic: {} and consumer group: {}",
        PmsSdkKafkaTopicResolver.getPipelineSdkStepResponseTopic(),
        PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(serviceName));
  }
}
