/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaConsumer;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base for GitOps CDC Kafka consumers. Eliminates duplication across
 * utilization_snapshot, applications, and appsync consumers.
 *
 * <p>Subclasses supply:
 * <ul>
 *   <li>Value type (GenericRecord for Avro, String for JSON)</li>
 *   <li>Message handler implementation</li>
 *   <li>Consumer config lookup key</li>
 *   <li>Consumer group ID</li>
 *   <li>Deserializer properties via {@link #getPredefinedProperties(KafkaConsumerConfig)}</li>
 * </ul>
 *
 * @param <T> Value type: {@code GenericRecord} for Avro, {@code String} for JSON
 */
@OwnedBy(GITOPS)
@Slf4j
public abstract class AbstractGitopsKafkaConsumer<T> extends HKafkaConsumer<T, Void> {
  protected AbstractGitopsKafkaConsumer(MessageHandler<T> messageHandler, KafkaBaseConfig kafkaBaseConfig,
      ConsumerMaintenanceListener consumerMaintenanceListener, ExecutorService executorService,
      CdcKafkaConfig cdcKafkaConfig, String consumerConfigKey, String consumerGroupId, Class<T> valueClass) {
    super(buildConsumerConfig(cdcKafkaConfig, messageHandler, kafkaBaseConfig, consumerMaintenanceListener,
              executorService, consumerConfigKey, consumerGroupId, valueClass),
        null);
  }

  private static <T> KafkaConsumerConfig<T> buildConsumerConfig(CdcKafkaConfig cdcKafkaConfig,
      MessageHandler<T> messageHandler, KafkaBaseConfig kafkaBaseConfig,
      ConsumerMaintenanceListener consumerMaintenanceListener, ExecutorService executorService,
      String consumerConfigKey, String consumerGroupId, Class<T> valueClass) {
    CdcKafkaConsumerConfig consumerCfg =
        cdcKafkaConfig.getConsumer(consumerConfigKey)
            .orElseThrow(
                () -> new IllegalStateException("Missing CDC Kafka consumer config for '" + consumerConfigKey + "'"));

    return KafkaConsumerConfig.<T>builder()
        .tClass(valueClass)
        .topic(consumerCfg.getTopic())
        .consumerGroupId(consumerGroupId)
        .kafkaBaseConfig(kafkaBaseConfig)
        .messageHandler(messageHandler)
        .executorService(executorService)
        .consumerMaintenanceListener(consumerMaintenanceListener)
        .consumerMode(KafkaConsumerConfig.ConsumerMode.UNORDERED)
        .isNoAck(false)
        .maxPollRecords(consumerCfg.getMaxPollRecords())
        .build();
  }

  @Override
  protected void sendToDLQ(String topic, T data, Map<String, String> headers) {
    // Intentionally a no-op: no DLQ producer is wired. Handler failures are retried and
    // then logged + dropped inside the message handler. This matches the behavior of the
    // prior Redis consumer.
  }
}