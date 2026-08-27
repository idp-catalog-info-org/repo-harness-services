/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.helpers;

import static io.harness.beans.FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_BATCH_EVENT_PRODUCER;
import static io.harness.constants.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_PRODUCER;
import static io.harness.pms.events.PmsEventFrameworkConstants.PIE_EVENT_ID;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeBatchEvent;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class InitiateNodeHelper {
  @Inject @Named(INITIATE_NODE_EVENT_PRODUCER) private Producer producer;
  @Inject @Named(INITIATE_NODE_BATCH_EVENT_PRODUCER) private Producer batchProducer;
  @Inject @KafkaModule.General private Optional<HKafkaProtoProducer> hKafkaProtoProducer;
  @Inject private EventsFrameworkConfiguration eventsFrameworkConfiguration;

  public void publishEvent(Ambiance ambiance, String nodeId, String runtimeId) {
    ImmutableMap<String, String> eventMetadata = ImmutableMap.<String, String>builder()
                                                     .put("eventType", "TRIGGER_NODE")
                                                     .put("newNodeId", nodeId)
                                                     .put("newRuntimeId", runtimeId)
                                                     .put(PIE_EVENT_ID, getEventId(ambiance, runtimeId))
                                                     .putAll(AmbianceUtils.logContextMap(ambiance))
                                                     .build();
    InitiateNodeEvent event =
        InitiateNodeEvent.newBuilder().setAmbiance(ambiance).setNodeId(nodeId).setRuntimeId(runtimeId).build();
    if (eventsFrameworkConfiguration.isShouldUseKafka()
        && AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING.name())) {
      if (hKafkaProtoProducer.isPresent()) {
        hKafkaProtoProducer.get().send(EventsFrameworkKafkaTopicResolver.getInitiateNodeTopic(), event, eventMetadata);
        return;
      }
      log.warn("Kafka producer is not present, check the configuration. Fallback to redis.");
    }
    producer.send(Message.newBuilder().putAllMetadata(eventMetadata).setData(event.toByteString()).build());
  }

  public void publishEvent(Ambiance ambiance, InitiateMode initiateMode) {
    ImmutableMap<String, String> eventMetadata =
        ImmutableMap.<String, String>builder()
            .put("eventType", "TRIGGER_NODE")
            .put("newNodeId", Objects.requireNonNull(AmbianceUtils.obtainCurrentSetupId(ambiance)))
            .put("newRuntimeId", Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance)))
            .put(PIE_EVENT_ID, getEventId(ambiance, AmbianceUtils.obtainCurrentRuntimeId(ambiance)))
            .putAll(AmbianceUtils.logContextMap(ambiance))
            .build();
    InitiateNodeEvent event =
        InitiateNodeEvent.newBuilder().setAmbiance(ambiance).setInitiateMode(initiateMode).build();
    if (eventsFrameworkConfiguration.isShouldUseKafka()
        && AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING.name())) {
      if (hKafkaProtoProducer.isPresent()) {
        hKafkaProtoProducer.get().send(EventsFrameworkKafkaTopicResolver.getInitiateNodeTopic(), event, eventMetadata);
        return;
      }
      log.warn("Kafka producer is not present, check the configuration. Fallback to redis.");
    }
    producer.send(Message.newBuilder().putAllMetadata(eventMetadata).setData(event.toByteString()).build());
  }

  public void publishEventBatch(Ambiance ambiance, ChildrenStartRequestBatch batch, InitiateMode mode,
      boolean isCallbackRequired, boolean shouldProccedIfFailed, int maxConcurrency) {
    ImmutableMap<String, String> eventMetadata =
        ImmutableMap.<String, String>builder().put("eventType", "TRIGGER_NODE").build();
    InitiateNodeBatchEvent event = InitiateNodeBatchEvent.newBuilder()
                                       .addAllChildren(batch.getChildren())
                                       .setBatchId(batch.getUuid())
                                       .setAmbiance(ambiance)
                                       .setInitiateMode(mode)
                                       .setShouldProceedIfFailed(shouldProccedIfFailed)
                                       .setShouldRegisterCallback(isCallbackRequired)
                                       .setMaxConcurrency(maxConcurrency)
                                       .build();

    if (eventsFrameworkConfiguration.isShouldUseKafka()
        && AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING.name())) {
      if (hKafkaProtoProducer.isPresent()) {
        hKafkaProtoProducer.get().send(
            EventsFrameworkKafkaTopicResolver.getInitiateNodeBatchTopic(), event, eventMetadata);
        return;
      }
      log.warn("Kafka producer is not present, check the configuration. Fallback to redis.");
    }
    batchProducer.send(Message.newBuilder().putAllMetadata(eventMetadata).setData(event.toByteString()).build());
  }

  private String getEventId(Ambiance ambiance, String runtimeId) {
    return AmbianceUtils.getEventId("NODE_INITIATE", runtimeId);
  }
}
