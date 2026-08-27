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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@OwnedBy(PIPELINE)
@Value
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("AccountOverridesCreateResponse")
@Schema(name = "AccountOverridesCreateResponse",
    description = "This contains information on the account level overrides create request")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@EqualsAndHashCode(callSuper = true)
public class AccountOverridesCreateResponseDTO extends AbstractAccountOverridesResponseDTO {
  @Schema(description = "Data retention settings") DataRetentionSettingsCreateResponseDTO dataRetentionSettings;
  @Schema(description = "Export settings") ExportSettingsCreateResponseDTO exportSettings;
  @Schema(description = "Log streaming limits") LogStreamingLimitsDTO logStreamingLimits;
}
