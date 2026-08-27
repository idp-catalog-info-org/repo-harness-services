/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage.kafka;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@OwnedBy(PIPELINE)
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class CdcKafkaConsumerConfig {
  @JsonProperty String name;
  @JsonProperty String topic;
  @JsonProperty @Builder.Default int maxPollRecords = 100;
  /**
   * When true, the Redis consumer acks without processing — Kafka consumer is sole writer.
   * Separate from the FF so Redis short-circuit can be enabled independently of Kafka processing.
   */
  @JsonProperty boolean redisShortCircuit;
}
