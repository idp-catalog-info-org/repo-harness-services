/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.accountoverrides;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@Builder
@Hidden
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("AccountOverrideUpdateRequest")
@Schema(name = "AccountOverrideUpdateRequest", description = "Contains information for updating account overrides")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class AccountOverridesUpdateRequestDTO {
  @Schema(description = "Max no. of concurrent executions") Long maxConcurrentExecutions;
  @Schema(description = "Max input parameter size") Long maxInputParameterSize;
  @Schema(description = "Max outcome response size") Long maxOutcomeResponseSize;
  @Schema(description = "Max no. of queued executions") Integer maxQueuedExecutionLimit;
  @Schema(description = "Max no. of trigger creations") Integer maxTriggerCreationLimit;
  @Schema(description = "Max no. of concurrently running leaf steps for the account") Integer maxLeafStepConcurrency;
  @Schema(description = "Export settings") ExportSettingsUpdateRequestDTO exportSettings;
  @Schema(description = "Log streaming limits") LogStreamingLimitsDTO logStreamingLimits;
  @Schema(description = "Per-call-type budget of calls allowed during expression resolution")
  Map<ExpressionCallType, Integer> maxExpressionCalls;
}
