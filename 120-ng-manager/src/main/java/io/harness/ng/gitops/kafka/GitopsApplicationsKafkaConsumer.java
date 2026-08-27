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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Kafka CDC consumer for the {@code gitops.harness-gitops.applications} topic.
 *
 * <p>Uses JSON (String) serialization instead of Avro because {@code app.objectmeta.labels}
 * map keys contain MongoDB's dot replacement character (~), which is illegal in Avro field names.
 *
 * <p>Delegates common consumer logic to {@link AbstractGitopsKafkaConsumer}. Processing is gated
 * at runtime by the global Harness feature flag {@code CDS_GITOPS_ENABLE_KAFKA_CONNECT}.
 *
 * @see GitopsApplicationsCdcMessageHandler
 */
@OwnedBy(GITOPS)
@Singleton
@Slf4j
public class GitopsApplicationsKafkaConsumer extends AbstractGitopsKafkaConsumer<String> {
  @Inject
  public GitopsApplicationsKafkaConsumer(GitopsApplicationsCdcMessageHandler messageHandler,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      @Named(CdcKafkaConstants.APPLICATIONS_EXECUTOR) ExecutorService executorService, CdcKafkaConfig cdcKafkaConfig) {
    super(messageHandler, kafkaBaseConfig, consumerMaintenanceListener, executorService, cdcKafkaConfig,
        CdcKafkaConfig.APPLICATIONS_CONSUMER, CdcKafkaConstants.APPLICATIONS_CONSUMER_GROUP, String.class);
  }

  @Override
  protected Properties getPredefinedProperties(KafkaConsumerConfig<String> consumerConfig) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    return properties;
  }
}