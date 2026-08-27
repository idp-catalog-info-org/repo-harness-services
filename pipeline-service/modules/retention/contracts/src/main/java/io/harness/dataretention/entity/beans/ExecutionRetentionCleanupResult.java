/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dataretention.entity.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * DTO to hold the result of execution retention cleanup operation.
 * Contains both the count of cleaned up records and the list of planExecutionIds that were cleaned up.
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class ExecutionRetentionCleanupResult {
  int cleanedCount;
  List<String> cleanedPlanExecutionIds;
}
