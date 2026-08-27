/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage.kafka;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaConsumer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;

/**
 * Kafka CDC consumer for the {@code pms-harness.planExecutionsSummary} topic.
 *
 * <p>Extends the platform-owned {@link HKafkaConsumer}, so the main poll loop, maintenance
 * gating (via {@link ConsumerMaintenanceListener}), offset commit semantics, metrics, and
 * lifecycle (start / stop as a Dropwizard {@code Managed}) are all inherited.
 *
 * <p>This class runs in {@code UNORDERED + isNoAck=false} mode which uses the {@code runAck}
 * path: all records from a poll batch are submitted to a single-thread executor as individual
 * {@code CompletableFuture} tasks, then the poll-loop thread blocks in {@code allOf.get()}
 * without holding the Kafka consumer monitor. This eliminates the livelock present in
 * {@code ORDERED} mode where the poll-loop held the monitor for the full poll timeout (1000ms)
 * while the executor was trying to acquire it to call {@code resume()}. With a single-thread
 * executor the tasks are processed sequentially in partition offset order, preserving event
 * ordering without any risk of concurrent writes.
 *
 * <p>This class contributes two things on top of the base:
 * <ul>
 *   <li>{@link #getPredefinedProperties(KafkaConsumerConfig)} supplies the Avro value deserializer
 *       and schema registry reader mode.</li>
 *   <li>{@link #sendToDLQ(String, GenericRecord, Map)} is a no-op because no DLQ producer is
 *       wired yet. Handler exceptions are retried and then logged + dropped inside
 *       {@link PipelineExecutionSummaryCdcMessageHandler}.</li>
 * </ul>
 *
 * <p>Actual processing of records is gated at runtime by the global Harness feature flag
 * {@code PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY}. The flag check lives inside the message
 * handler (not the consumer) so that records are always acknowledged even in drain mode,
 * keeping the consumer group current on the broker. See {@link PipelineExecutionSummaryCdcMessageHandler}.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class PipelineExecutionSummaryKafkaConsumer extends HKafkaConsumer<GenericRecord, Void> {
  @Inject
  public PipelineExecutionSummaryKafkaConsumer(PipelineExecutionSummaryCdcMessageHandler messageHandler,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(CdcKafkaConstants.CDC_KAFKA_EXECUTOR_SERVICE) ExecutorService executorService,
      CdcKafkaConfig cdcKafkaConfig) {
    super(buildConsumerConfig(
              cdcKafkaConfig, messageHandler, kafkaBaseConfig, consumerMaintenanceListener, executorService),
        null);
  }

  private static KafkaConsumerConfig<GenericRecord> buildConsumerConfig(CdcKafkaConfig cdcKafkaConfig,
      PipelineExecutionSummaryCdcMessageHandler messageHandler, KafkaBaseConfig kafkaBaseConfig,
      ConsumerMaintenanceListener consumerMaintenanceListener, ExecutorService executorService) {
    CdcKafkaConsumerConfig consumerCfg =
        cdcKafkaConfig.getConsumer(CdcKafkaConfig.PLAN_EXECUTIONS_SUMMARY_CONSUMER)
            .orElseThrow(()
                             -> new IllegalStateException("Missing CDC Kafka consumer config for '"
                                 + CdcKafkaConfig.PLAN_EXECUTIONS_SUMMARY_CONSUMER + "'"));
    return KafkaConsumerConfig.<GenericRecord>builder()
        .tClass(GenericRecord.class)
        .topic(consumerCfg.getTopic())
        .consumerGroupId(CdcKafkaConstants.PLAN_EXECUTIONS_SUMMARY_CONSUMER_GROUP)
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
  protected Properties getPredefinedProperties(KafkaConsumerConfig<GenericRecord> consumerConfig) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
    properties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);
    return properties;
  }

  @Override
  protected void sendToDLQ(String topic, GenericRecord data, Map<String, String> headers) {
    // Intentionally a no-op: no DLQ producer is wired. Handler failures are retried and
    // then logged + dropped inside PipelineExecutionSummaryCdcMessageHandler. This matches
    // the behavior of the prior (deleted) CdcKafkaAvroConsumer.
  }
}
