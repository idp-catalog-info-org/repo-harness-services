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
 * Root MongoDB CDC event structure matching Kafka Connect MongoDB Source format.
 * This represents the complete change stream event from MongoDB.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class MongoDBCDCEvent {
  /** MongoDB change stream resume token */
  @JsonProperty("_id") ResumeToken id;

  /** MongoDB operation type: insert, update, replace, delete */
  String operationType;

  /** MongoDB cluster time as BSON timestamp */
  ClusterTime clusterTime;

  /** Wall clock time when the event occurred */
  WallTime wallTime;

  /** MongoDB namespace (database and collection) */
  Namespace ns;

  /** MongoDB document key (_id field) */
  DocumentKey documentKey;

  /** Complete document with all node execution fields */
  NodeExecutionFullDocument fullDocument;
}
