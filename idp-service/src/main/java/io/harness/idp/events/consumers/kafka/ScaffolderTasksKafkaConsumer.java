/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.kafka;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.idp.config.CdcKafkaConsumerConfig;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(IDP)
@Singleton
public class ScaffolderTasksKafkaConsumer extends CdcKafkaAvroConsumer {
  @Inject
  public ScaffolderTasksKafkaConsumer(ScaffolderTasksCdcMessageHandler messageHandler,
      ConsumerMaintenanceListener consumerMaintenanceListener, @KafkaModule.General KafkaBaseConfig kafkaBaseConfig,
      CdcKafkaConfig cdcKafkaConfig, FeatureFlagService featureFlagService) {
    this(resolveConsumerConfig(cdcKafkaConfig), kafkaBaseConfig, messageHandler, consumerMaintenanceListener,
        featureFlagService);
  }

  private ScaffolderTasksKafkaConsumer(CdcKafkaConsumerConfig consumerConfig, KafkaBaseConfig kafkaBaseConfig,
      ScaffolderTasksCdcMessageHandler messageHandler, ConsumerMaintenanceListener consumerMaintenanceListener,
      FeatureFlagService featureFlagService) {
    super(consumerConfig.getTopic(), CdcKafkaConstants.SCAFFOLDER_TASKS_CONSUMER_GROUP, kafkaBaseConfig, messageHandler,
        consumerMaintenanceListener, consumerConfig.getMaxPollRecords(), featureFlagService,
        FeatureName.IDP_CDC_KAFKA_SCAFFOLDER_TASKS, consumerConfig.isUseJsonValueFormat());
  }

  private static CdcKafkaConsumerConfig resolveConsumerConfig(CdcKafkaConfig cdcKafkaConfig) {
    return cdcKafkaConfig.getConsumer(CdcKafkaConfig.SCAFFOLDER_TASKS_CONSUMER)
        .orElseThrow(()
                         -> new IllegalStateException("Missing CDC Kafka consumer config for '"
                             + CdcKafkaConfig.SCAFFOLDER_TASKS_CONSUMER + "'"));
  }
}
