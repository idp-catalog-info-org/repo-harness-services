/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.entity.accountoverrides.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.accountoverrides.ExpressionCallType;
import io.harness.pms.accountoverrides.LogStreamingLimitsDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AccountOverridesConfigResponse",
    description = "This contains information on the account level overrides to limits.")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class AccountOverridesConfigDTO {
  String accountIdentifier;
  Integer retentionPeriodInMonths;
  Long maxConcurrentExecutions;
  Long maxInputParameterSize;
  Long maxOutcomeResponseSize;
  Integer maxQueuedExecutionLimit;
  Integer maxTriggerCreationLimit;
  DataRetentionSettingsDTO dataRetentionSettings;
  ExportSettingsDTO exportSettings;
  LogStreamingLimitsDTO logStreamingLimits;
  Integer stepOrStageMaxConcurrency;
  Integer maxLeafStepConcurrency;
  Long maxCustomWebhookPayloadSize;
  Map<ExpressionCallType, Integer> maxExpressionCalls;
}
