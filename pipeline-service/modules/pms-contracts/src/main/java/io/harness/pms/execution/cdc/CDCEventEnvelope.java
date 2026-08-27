/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.cdc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Top-level CDC event envelope matching Kafka Connect MongoDB Source format.
 * Contains metadata about the CDC event and the MongoDB payload.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class CDCEventEnvelope {
  /** Schema version (e.g., "1.0") */
  @JsonProperty("version") String version;

  /** Database name (e.g., "harness-pms") */
  @JsonProperty("db") String db;

  /** Source type identifier (e.g., "MONGODB") */
  @JsonProperty("source_type") String sourceType;

  /** The actual MongoDB CDC payload */
  @JsonProperty("payload") MongoDBCDCEvent payload;
}
