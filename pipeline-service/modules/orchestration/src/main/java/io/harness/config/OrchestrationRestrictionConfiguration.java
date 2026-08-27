/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.accountoverrides.ExpressionCallType;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class OrchestrationRestrictionConfiguration {
  @JsonProperty("maxNestedLevelsCount") int maxNestedLevelsCount;
  @JsonProperty("useRestrictionForFree") boolean useRestrictionForFree;
  @JsonProperty("useRestrictionForTeam") boolean useRestrictionForTeam;
  @JsonProperty("useRestrictionForEnterprise") boolean useRestrictionForEnterprise;
  @JsonProperty("useRestrictionForDevopsEssentials") boolean useRestrictionForDevopsEssentials;
  @JsonProperty("useRestrictionForEssentials") boolean useRestrictionForEssentials;
  @JsonProperty("planExecutionRestriction") PlanExecutionRestrictionConfig planExecutionRestriction;
  @JsonProperty("pipelineCreationRestriction") PlanExecutionRestrictionConfig pipelineCreationRestriction;
  @JsonProperty("maxConcurrencyRestriction") PlanExecutionRestrictionConfig maxConcurrencyRestriction;
  @JsonProperty("maxInputParameterSizeRestriction") PlanExecutionRestrictionConfig maxInputParameterSizeRestriction;
  // Per-node MongoDB (and future datastore) call budget for expression resolution, keyed by call type (PIPE-34261).
  @JsonProperty("maxExpressionCallsRestriction")
  Map<ExpressionCallType, PlanExecutionRestrictionConfig> maxExpressionCallsRestriction;
  @JsonProperty("maxExecutionOutcomeSizeRestriction") PlanExecutionRestrictionConfig maxExecutionOutcomeSizeRestriction;
  @JsonProperty("totalParallelismStopRestriction") PlanExecutionRestrictionConfig totalParallelismStopRestriction;
  @JsonProperty("maxPipelineTimeoutInHours") MaxPipelineTimeoutInHoursConfig maxPipelineTimeoutInHoursConfig;
  @JsonProperty("maxStageTimeoutInHours") MaxStageTimeoutInHoursConfig maxStageTimeoutInHoursConfig;
  @JsonProperty("maxStepTimeoutInHours") MaxStepTimeoutInHours maxStepTimeoutInHours;
  @JsonProperty("maxQueuedExecutionsRestriction") PlanExecutionRestrictionConfig maxQueuedExecutionsRestriction;
  @JsonProperty("triggerCreationRestriction") PlanExecutionRestrictionConfig triggerCreationRestriction;
  @JsonProperty("fileSizeRestriction") PlanExecutionRestrictionConfig fileSizeRestriction;
  @JsonProperty("payloadSizeRestriction") PlanExecutionRestrictionConfig payloadSizeRestriction;
}
