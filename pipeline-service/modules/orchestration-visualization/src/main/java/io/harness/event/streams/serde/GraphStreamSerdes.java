/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.streams.serde;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.OrchestrationGraph;
import io.harness.event.streams.model.ChangeDataEvent;
import io.harness.serializer.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Custom Kafka Serdes for Graph Generation Streams.
 *
 * Provides serialization/deserialization for:
 * - ChangeDataEvent (MongoDB change stream events in JSON via MongoDB Kafka Connector)
 * - OrchestrationGraph (output to graph-updates topic)
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class GraphStreamSerdes {
  // ============== OrchestrationGraph Serde ==============

  public static Serde<OrchestrationGraph> orchestrationGraphSerde() {
    return Serdes.serdeFrom(new OrchestrationGraphSerializer(), new OrchestrationGraphDeserializer());
  }

  public static class OrchestrationGraphSerializer implements Serializer<OrchestrationGraph> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
      // No configuration needed
    }

    @Override
    public byte[] serialize(String topic, OrchestrationGraph data) {
      if (data == null) {
        return null;
      }
      return JsonUtils.asJson(data).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
      // No resources to close
    }
  }

  public static class OrchestrationGraphDeserializer implements Deserializer<OrchestrationGraph> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
      // No configuration needed
    }

    @Override
    public OrchestrationGraph deserialize(String topic, byte[] data) {
      if (data == null || data.length == 0) {
        return null;
      }
      try {
        return JsonUtils.asObject(new String(data, StandardCharsets.UTF_8), OrchestrationGraph.class);
      } catch (Exception e) {
        log.error("Failed to deserialize OrchestrationGraph", e);
        return null;
      }
    }

    @Override
    public void close() {
      // No resources to close
    }
  }

  // ============== ChangeDataEvent JSON Deserializer ==============

  /**
   * Deserializer that reads JSON-encoded MongoDB change stream events
   * from the MongoDB Kafka Connector with SimplifiedJson formatter.
   *
   * No Avro, no Schema Registry — just Jackson JSON deserialization.
   */
  public static class ChangeDataEventDeserializer implements Deserializer<ChangeDataEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
      // No configuration needed
    }

    @Override
    public ChangeDataEvent deserialize(String topic, byte[] data) {
      if (data == null || data.length == 0) {
        return null;
      }
      try {
        return JsonUtils.asObject(new String(data, StandardCharsets.UTF_8), ChangeDataEvent.class);
      } catch (Exception e) {
        log.error("Failed to deserialize ChangeDataEvent from topic {}: {}", topic, e.getMessage(), e);
        return null;
      }
    }

    @Override
    public void close() {
      // No resources to close
    }
  }
}
