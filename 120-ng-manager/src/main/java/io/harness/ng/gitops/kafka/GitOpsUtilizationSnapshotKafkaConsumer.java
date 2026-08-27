/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;

/**
 * Kafka CDC consumer for the {@code gitops.harness-gitops.utilization_snapshot} topic.
 *
 * <p>Uses Avro serialization and delegates common consumer logic to {@link AbstractGitopsKafkaConsumer}.
 * Processing is gated at runtime by the global Harness feature flag {@code CDS_GITOPS_ENABLE_KAFKA_CONNECT}.
 *
 * @see GitOpsUtilizationSnapshotCdcMessageHandler
 */
@OwnedBy(GITOPS)
@Singleton
@Slf4j
public class GitOpsUtilizationSnapshotKafkaConsumer extends AbstractGitopsKafkaConsumer<GenericRecord> {
  @Inject
  public GitOpsUtilizationSnapshotKafkaConsumer(GitOpsUtilizationSnapshotCdcMessageHandler messageHandler,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(CdcKafkaConstants.UTILIZATION_SNAPSHOT_EXECUTOR) ExecutorService executorService,
      CdcKafkaConfig cdcKafkaConfig) {
    super(messageHandler, kafkaBaseConfig, consumerMaintenanceListener, executorService, cdcKafkaConfig,
        CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER, CdcKafkaConstants.UTILIZATION_SNAPSHOT_CONSUMER_GROUP,
        GenericRecord.class);
  }

  @Override
  protected Properties getPredefinedProperties(KafkaConsumerConfig<GenericRecord> consumerConfig) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
    properties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);
    return properties;
  }
}