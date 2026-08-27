/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.config;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@OwnedBy(IDP)
@Value
@Builder
@Jacksonized
public class CdcKafkaConsumerConfig {
  @JsonProperty String name;
  @JsonProperty boolean enabled;
  @JsonProperty String topic;
  @JsonProperty @Builder.Default int maxPollRecords = 100;
  @JsonProperty boolean useJsonValueFormat;
  /**
   * When true, the Redis consumer acks without processing — Kafka consumer is sole writer.
   * Separate from the FF so Redis short-circuit can be enabled independently of Kafka processing.
   */
  @JsonProperty boolean redisShortCircuit;
}
