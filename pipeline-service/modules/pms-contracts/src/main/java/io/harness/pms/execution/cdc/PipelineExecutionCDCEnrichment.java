/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.cdc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Pipeline-specific enrichment data injected into the synthetic CDC {@code fullDocument}.
 *
 * <p>These fields are sourced from {@code planExecutionsSummary} (NOT from the Avro event).
 * The Avro schema is intentionally kept unchanged; all additional CDC fields are injected
 * via this DTO, following the same pattern previously used for {@code runSequence} alone.
 *
 * <p>All fields are nullable. {@code null} means "not available"; derivation fallbacks apply.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class PipelineExecutionCDCEnrichment {
  /**
   * Pipeline run sequence number from planExecutionsSummary.
   * Monotonically increasing counter per pipeline identifier.
   */
  Integer runSequence;

  /**
   * Trigger type string (e.g. {@code MANUAL}, {@code WEBHOOK}, {@code SCHEDULER_CRON})
   * sourced from {@code executionTriggerInfo.triggerType.name()}.
   */
  String triggerType;

  /**
   * UUID of the user / trigger entity that initiated this execution,
   * sourced from {@code executionTriggerInfo.triggeredBy.uuid}.
   */
  String triggeredById;

  /**
   * Identifier (username, email, or trigger identifier) of the initiating entity,
   * sourced from {@code executionTriggerInfo.triggeredBy.identifier}.
   */
  String triggeredByIdentifier;

  /**
   * Modules that were actually executed during this pipeline run (e.g. {@code "CD"}, {@code "CI"}),
   * sourced from {@code PipelineExecutionSummaryEntity.executedModules}.
   */
  List<String> executedModules;

  /**
   * Whether the pipeline execution document has been soft-deleted in planExecutionsSummary,
   * sourced from {@code PipelineExecutionSummaryEntity.pipelineDeleted}.
   */
  Boolean deleted;
}
