/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup.config;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
public class OrchestrationGraphCacheCleanupConfig {
  @JsonProperty(defaultValue = "false") boolean enabled;
  @JsonProperty(defaultValue = "false") boolean cleanUpEnabled;
  @JsonProperty(defaultValue = "1440") @Builder.Default int cleanUpIntervalMinutes = 1440;
  @JsonProperty(defaultValue = "5000") @Builder.Default int batchSize = 5000;
  @JsonProperty(defaultValue = "55") @Builder.Default int maxJobDurationMinutes = 55;
}
