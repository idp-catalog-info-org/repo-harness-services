/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventPoll;

import io.harness.outbox.OutboxPollConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineOutboxPollConfiguration {
  @JsonProperty("executionOutbox") OutboxPollConfiguration executionOutbox;
  @JsonProperty("kafkaOutbox") OutboxPollConfiguration kafkaOutbox;
}