/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage.kafka;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Top-level configuration for CDC (Change Data Capture) Kafka consumers in pipeline-service.
 *
 * <p>The {@code enabled} field is an infrastructure-readiness flag: when true, Kafka CDC
 * infrastructure (Kafka Connect, topics, connectors) is assumed deployed and the Kafka
 * consumer threads are started at boot time (alongside the existing Redis consumer).
 * Whether the Kafka consumer actively <i>processes</i> records (vs. draining) is controlled
 * at runtime by the global Harness Feature Flag {@code PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY}.
 * When {@code enabled=false} (default), only the legacy Redis/Debezium consumer runs.
 */
@OwnedBy(PIPELINE)
@Value
@Builder
@Jacksonized
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class CdcKafkaConfig {
  public static final String PLAN_EXECUTIONS_SUMMARY_CONSUMER = "planExecutionsSummary";

  @JsonProperty boolean enabled;
  @JsonProperty @Builder.Default List<CdcKafkaConsumerConfig> consumers = Collections.emptyList();

  public Optional<CdcKafkaConsumerConfig> getConsumer(String name) {
    if (consumers == null) {
      return Optional.empty();
    }
    return consumers.stream().filter(c -> name.equals(c.getName())).findFirst();
  }

  public static CdcKafkaConfig defaultConfig() {
    return CdcKafkaConfig.builder().build();
  }
}
