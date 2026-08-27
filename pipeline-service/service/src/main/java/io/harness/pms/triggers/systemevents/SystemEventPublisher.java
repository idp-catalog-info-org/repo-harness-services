/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.SYSTEM_EVENT_TRIGGER_STREAM;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_IDENTIFIER_METRICS_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.PipelineSystemEvent;
import io.harness.eventsframework.webhookpayloads.webhookdata.SystemEventEnvelope;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class SystemEventPublisher {
  private final Producer eventProducer;
  private final PmsFeatureFlagService featureFlagService;

  @Inject
  public SystemEventPublisher(
      @Named(SYSTEM_EVENT_TRIGGER_STREAM) Producer eventProducer, PmsFeatureFlagService featureFlagService) {
    this.eventProducer = eventProducer;
    this.featureFlagService = featureFlagService;
  }

  public void publish(Ambiance ambiance, SystemEventType eventType) {
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      if (!featureFlagService.isEnabled(accountId, FeatureName.SYSTEM_EVENTS_TRIGGERS)) {
        return;
      }

      PipelineSystemEvent pipelineSystemEvent =
          PipelineSystemEvent.newBuilder()
              .setAccountId(accountId)
              .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
              .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
              .setSourcePipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
              .setEventType(eventType.eventTypeString())
              .setPlanExecutionId(ambiance.getPlanExecutionId())
              .setTime(System.currentTimeMillis())
              .build();

      SystemEventEnvelope envelope =
          SystemEventEnvelope.newBuilder().setPipelineSystemEvent(pipelineSystemEvent).build();

      String messageId =
          eventProducer.send(Message.newBuilder()
                                 .putAllMetadata(ImmutableMap.of(ACCOUNT_IDENTIFIER_METRICS_KEY, accountId))
                                 .setData(envelope.toByteString())
                                 .build());
      log.info("Published SYSTEM_EVENTS trigger event [{}] planExecutionId={} messageId={}", eventType,
          ambiance.getPlanExecutionId(), messageId);
    } catch (Exception ex) {
      log.warn("Failed to publish SYSTEM_EVENTS trigger event [{}] for planExecutionId={}", eventType,
          ambiance.getPlanExecutionId(), ex);
    }
  }
}
