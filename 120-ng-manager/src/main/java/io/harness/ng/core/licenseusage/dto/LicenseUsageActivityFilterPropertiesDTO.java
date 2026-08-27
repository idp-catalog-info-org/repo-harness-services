/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.licenseusage.utils.LicenseUsageUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("LicenseUsageActivityFilterProperties")
@AllArgsConstructor
@OwnedBy(HarnessTeam.PL)
public class LicenseUsageActivityFilterPropertiesDTO {
  @Schema(description = LicenseUsageUtils.ORGANIZATION_IDENTIFIERS_LIST) List<String> organizationIdentifiers;
  @Schema(description = LicenseUsageUtils.PROJECT_IDENTIFIERS_LIST) List<String> projectIdentifiers;
  @Schema(description = LicenseUsageUtils.PIPELINE_IDENTIFIERS_LIST) List<String> pipelineIdentifiers;
  @Schema(description = LicenseUsageUtils.RESOURCE_CLASSES_LIST) List<String> resourceClasses;
}
