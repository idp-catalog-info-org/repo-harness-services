/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@ApiModel("AzureListInstancesFilter")
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDP)
@Schema(name = "AzureListInstancesFilter", description = "Properties of the Azure Instance list")
public class AzureListInstancesFilterDTO {
  @Schema(description = "subscriptionId") private String subscriptionId;
  @Schema(description = "resourceGroup") private String resourceGroup;
  @Schema(description = "key value tags") private Map<String, String> tags;
  @Schema(description = "true if winRm deployment type. default is false") private Boolean winRm;
  @Schema(description = "Host Connection Type") private String hostConnectionType;
}
