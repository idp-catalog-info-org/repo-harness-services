/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * Container for execution plan and associated metadata.
 * Contains the created Plan and all context required to start a pipeline execution.
 * Used for both regular execution and dry-run validation.
 */
@Value
@Builder
public class ExecutionPlan {
  @Valid @NotNull Plan plan;
  @NotNull Map<String, String> abstractions;
  @NotNull ExecutionMetadata executionMetadata;
  @NotNull PlanExecutionMetadataWithContext planExecutionMetadataWithContext;
}
