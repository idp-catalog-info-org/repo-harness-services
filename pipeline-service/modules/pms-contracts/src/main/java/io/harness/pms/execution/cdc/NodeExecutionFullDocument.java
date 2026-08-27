/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.cdc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.ModuleInfo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Unified full document structure for node execution CDC events.
 * Contains all possible fields from Step, Stage, and Pipeline events.
 * Null fields are excluded from JSON serialization.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeExecutionFullDocument {
  // ========== Common Fields (present in all events) ==========

  /** Event level: step, stage, or pipeline */
  String level;

  /** Account identifier */
  String accountIdentifier;

  /** Organization identifier */
  String orgIdentifier;

  /** Project identifier */
  String projectIdentifier;

  /** Parent unique identifier */
  String parentUniqueId;

  /** Pipeline identifier */
  String pipelineIdentifier;

  /** Pipeline name */
  String pipelineName;

  /** Plan execution ID */
  String planExecutionId;

  /** Execution URL */
  String executionUrl;

  /** Execution status (e.g., Success, Failed, Running) */
  String status;

  /** Event type (e.g., nodeStart, nodeStatusUpdate, nodeEnd) */
  String eventType;

  /** Creation timestamp */
  String createdAt;

  /** Start timestamp */
  String startTs;

  /** Last modified timestamp */
  String lastModifiedAt;

  /** End timestamp */
  String endTs;

  /** Duration in milliseconds */
  String duration;

  /** Failure information (if execution failed) */
  List<FailureDataDocument> failureInfo;

  /** Module-specific information (CD, CI, etc.) */
  ModuleInfo moduleInfo;

  // ========== Stage-Specific Fields ==========

  /** Stage execution ID */
  String stageExecutionId;

  /** Stage identifier */
  String stageIdentifier;

  /** Stage name */
  String stageName;

  /** Stage type */
  String stageType;

  // ========== Step-Specific Fields ==========

  /** Step execution ID */
  String stepExecutionId;

  /** Step identifier */
  String stepIdentifier;

  /** Step name */
  String stepName;

  /** Step type */
  String stepType;

  /** Step inputs (JSON string) */
  String stepInputs;

  /** Whether the step was retried */
  Boolean isRetried;

  /** List of retry IDs */
  List<String> retryIds;

  /** Log URL for step execution */
  String logUrl;

  /** Step outputs (list of JSON strings) */
  List<String> stepOutputs;

  // ========== Pipeline-Specific Fields ==========

  /** Pipeline tags */
  Map<String, String> tags;

  /**
   * Run sequence number for the pipeline execution.
   * Sourced from planExecutionsSummary.runSequence (NOT from the Avro event — Avro schema is untouched).
   * Populated only for pipeline-level CDC events; null for step/stage events.
   */
  Integer runSequence;

  /**
   * Trigger type (e.g. MANUAL, WEBHOOK, SCHEDULER_CRON, ARTIFACT, MANIFEST).
   * Sourced from planExecutionsSummary.executionTriggerInfo.triggerType.
   * Null for step/stage events or when trigger info is unavailable.
   */
  String triggerType;

  /**
   * UUID of the user or trigger entity that initiated the execution.
   * Sourced from planExecutionsSummary.executionTriggerInfo.triggeredBy.uuid.
   * Null for step/stage events or when trigger info is unavailable.
   */
  String triggeredById;

  /**
   * Identifier (username, email, or trigger identifier) of the initiating entity.
   * Sourced from planExecutionsSummary.executionTriggerInfo.triggeredBy.identifier.
   * Null for step/stage events or when trigger info is unavailable.
   */
  String triggeredByIdentifier;

  /**
   * Modules that were actually executed during this pipeline run (e.g. "CD", "CI").
   * Sourced from planExecutionsSummary.executedModules.
   * Null for step/stage events or when no module info is available.
   */
  List<String> executedModules;

  /**
   * Whether the pipeline execution document has been soft-deleted.
   * Sourced from planExecutionsSummary.pipelineDeleted.
   * Null for step/stage events or when the field is not available.
   */
  Boolean deleted;
}
