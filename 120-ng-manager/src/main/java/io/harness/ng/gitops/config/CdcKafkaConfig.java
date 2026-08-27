/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.config;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Top-level configuration for CDC (Change Data Capture) Kafka consumers in ng-manager.
 *
 * <p>The {@code enabled} field is an infrastructure-readiness flag: when true, Kafka CDC
 * infrastructure (Kafka Connect, topics, connectors) is assumed deployed and the Kafka
 * consumer threads are started at boot time (alongside the existing Redis consumer).
 * Whether the Kafka consumer actively <i>processes</i> records (vs. draining) is controlled
 * at runtime by the global Harness Feature Flag {@code CDS_GITOPS_UTILIZATION_USE_KAFKA}.
 * When {@code enabled=false} (default), only the legacy Redis/Debezium consumer runs.
 */
@OwnedBy(GITOPS)
@Value
@Builder
@Jacksonized
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class CdcKafkaConfig {
  public static final String UTILIZATION_SNAPSHOT_CONSUMER = "utilizationSnapshot";
  public static final String APPLICATIONS_CONSUMER = "applications";

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