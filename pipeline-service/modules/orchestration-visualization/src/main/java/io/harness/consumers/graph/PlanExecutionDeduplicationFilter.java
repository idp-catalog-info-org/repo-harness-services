/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.kafka.common.ConsumerRecordFilter;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;

import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Filter that deduplicates OrchestrationLogEvents by planExecutionId within a batch.
 * This ensures that multiple events for the same plan execution within a single batch
 * are processed only once, similar to the logic in GraphUpdateRedisConsumer.mapPlanExecutionToMessages.
 *
 * Only the first record for each planExecutionId in a batch is kept.
 * Invalid records (null or unparseable) are kept for error handling downstream.
 */
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
public class PlanExecutionDeduplicationFilter implements ConsumerRecordFilter {
  public PlanExecutionDeduplicationFilter() {
    // No-arg constructor for Guice injection
  }

  @Override
  public <T> List<ConsumerRecord<String, T>> filter(List<ConsumerRecord<String, T>> records) {
    if (EmptyPredicate.isEmpty(records)) {
      return records;
    }

    // Map to track first occurrence of each planExecutionId
    Map<String, ConsumerRecord<String, T>> seenPlanExecutions = new LinkedHashMap<>();
    List<ConsumerRecord<String, T>> filteredRecords = new ArrayList<>();
    for (ConsumerRecord<String, T> record : records) {
      OrchestrationLogEvent event = extractEvent(record);

      if (event == null || EmptyPredicate.isEmpty(event.getPlanExecutionId())) {
        // Keep invalid records for error handling/logging downstream
        filteredRecords.add(record);
        continue;
      }

      String planExecutionId = event.getPlanExecutionId();

      // Keep only the first record for each planExecutionId
      if (!seenPlanExecutions.containsKey(planExecutionId)) {
        seenPlanExecutions.put(planExecutionId, record);
        filteredRecords.add(record);
      }
    }

    return filteredRecords;
  }

  /**
   * Extract OrchestrationLogEvent from a Kafka ConsumerRecord.
   */
  @SuppressWarnings("unchecked")
  private <T> OrchestrationLogEvent extractEvent(ConsumerRecord<String, T> record) {
    try {
      if (record.value() == null) {
        return null;
      }

      if (record.value() instanceof OrchestrationLogEvent) {
        return (OrchestrationLogEvent) record.value();
      }

      if (record.value() instanceof byte[]) {
        return OrchestrationLogEvent.parseFrom((byte[]) record.value());
      }

      log.warn("Unexpected record value type: {}", record.value().getClass().getName());
      return null;
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse OrchestrationLogEvent from record: topic={}, partition={}, offset={}", record.topic(),
          record.partition(), record.offset(), e);
      return null;
    } catch (Exception e) {
      log.error("Unexpected error extracting event from record: topic={}, partition={}, offset={}", record.topic(),
          record.partition(), record.offset(), e);
      return null;
    }
  }
}
