/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Shared string constants for the semantic-validation layer. Centralised so the validator and every
 * {@link SemanticRule} agree on the values written into {@code DryRunPipelineValidationResult} and on
 * the runtime-expression marker, instead of each class re-declaring its own copy.
 */
@UtilityClass
@OwnedBy(PIPELINE)
public class SemanticConstants {
  public static final String VALIDATION_TYPE_SEMANTIC = "SEMANTIC";
  public static final String SEVERITY_ERROR = "ERROR";
  public static final String SEVERITY_WARNING = "WARNING";
  public static final String RUNTIME_EXPRESSION_PREFIX = "<+";
}
