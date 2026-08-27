/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.config;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@OwnedBy(GITOPS)
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class CdcKafkaConsumerConfig {
  @JsonProperty String name;
  @JsonProperty String topic;
  @JsonProperty @Builder.Default int maxPollRecords = 100;
  /** Controls consumer thread registration at startup. When false, no Kafka connection is made. */
  @JsonProperty boolean kafkaConsumerEnabled;
  /** Controls whether consumed events are written to the DB (true) or silently drained (false). */
  @JsonProperty boolean processingEnabled;
  /** When true, the Redis consumer acks without processing — hands over fully to Kafka. */
  @JsonProperty boolean redisShortCircuit;
}