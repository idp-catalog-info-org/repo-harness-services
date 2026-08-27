/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.dispatch;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class EventEnvelopePublisher {
  private final Optional<HKafkaProtoProducer> kafkaProducer;
  private final PmsFeatureFlagService pmsFeatureFlagService;

  @Inject
  public EventEnvelopePublisher(
      @KafkaModule.General Optional<HKafkaProtoProducer> kafkaProducer, PmsFeatureFlagService pmsFeatureFlagService) {
    this.kafkaProducer = kafkaProducer;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
  }

  public void publishPipelineEvent(Ambiance ambiance, Status status) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.UDP_EVENT_DISPATCH_ENABLED)) {
        return;
      }
      if (kafkaProducer.isEmpty()) {
        log.debug("Kafka proto producer not available, skipping event dispatch");
        return;
      }
      var envelope = PipelineEventEnvelopeBuilder.build(ambiance, status);
      kafkaProducer.get().send(
          EventsFrameworkKafkaTopicResolver.getEventDispatchTopic(), envelope, Collections.emptyMap(), accountId);
      log.info("Published {} event for execution {} account {}", envelope.getType(), ambiance.getPlanExecutionId(),
          accountId);
    } catch (Exception ex) {
      log.warn("Failed to publish event dispatch envelope for execution {}: {}", ambiance.getPlanExecutionId(),
          ex.getMessage(), ex);
    }
  }
}
