/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.entity.accountoverrides;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.accountoverrides.ExpressionCallType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountOverridesCacheInfo {
  int retentionPeriodInMonths;
  Long maxConcurrentExecutions;
  Long maxInputParameterSize;
  Long maxOutcomeResponseSize;
  Integer maxQueuedExecutionLimit;
  Integer maxTriggerCreationLimit;
  Long maxFileSize;
  Integer stepOrStageMaxConcurrency;
  Integer maxLeafStepConcurrency;
  Integer maxPipelineCreationLimit;
  DataRetentionSettings dataRetentionSettings;
  SearchSettings searchSettings;
  Long maxCustomWebhookPayloadSize;
  Map<ExpressionCallType, Integer> maxExpressionCalls;
}
