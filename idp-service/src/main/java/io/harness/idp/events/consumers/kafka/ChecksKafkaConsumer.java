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
public class ChecksKafkaConsumer extends CdcKafkaAvroConsumer {
  @Inject
  public ChecksKafkaConsumer(ChecksCdcMessageHandler messageHandler,
      ConsumerMaintenanceListener consumerMaintenanceListener, @KafkaModule.General KafkaBaseConfig kafkaBaseConfig,
      CdcKafkaConfig cdcKafkaConfig, FeatureFlagService featureFlagService) {
    this(resolveConsumerConfig(cdcKafkaConfig), kafkaBaseConfig, messageHandler, consumerMaintenanceListener,
        featureFlagService);
  }

  private ChecksKafkaConsumer(CdcKafkaConsumerConfig consumerConfig, KafkaBaseConfig kafkaBaseConfig,
      ChecksCdcMessageHandler messageHandler, ConsumerMaintenanceListener consumerMaintenanceListener,
      FeatureFlagService featureFlagService) {
    super(consumerConfig.getTopic(), CdcKafkaConstants.CHECKS_CONSUMER_GROUP, kafkaBaseConfig, messageHandler,
        consumerMaintenanceListener, consumerConfig.getMaxPollRecords(), featureFlagService,
        FeatureName.IDP_CDC_KAFKA_CHECKS, consumerConfig.isUseJsonValueFormat());
  }

  private static CdcKafkaConsumerConfig resolveConsumerConfig(CdcKafkaConfig cdcKafkaConfig) {
    return cdcKafkaConfig.getConsumer(CdcKafkaConfig.CHECKS_CONSUMER)
        .orElseThrow(()
                         -> new IllegalStateException(
                             "Missing CDC Kafka consumer config for '" + CdcKafkaConfig.CHECKS_CONSUMER + "'"));
  }
}
