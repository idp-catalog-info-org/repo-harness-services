/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import java.util.List;

/**
 * A single, self-contained semantic check run against a resolved dry-run pipeline.
 *
 * <p>Rules operate purely on the {@link SemanticValidationContext} (resolved-YAML DOM, referred
 * entities, batch-fetched connectors) and must not perform their own resolution or fail the dry
 * run. Any field carrying a runtime expression should be skipped rather than flagged.
 */
@OwnedBy(PIPELINE)
public interface SemanticRule {
  List<DryRunPipelineValidationResult> apply(SemanticValidationContext ctx);
}
