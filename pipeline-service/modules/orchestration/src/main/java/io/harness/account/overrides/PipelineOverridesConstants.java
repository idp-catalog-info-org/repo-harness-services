/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.overrides;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
public class PipelineOverridesConstants {
  public static final Long DEFAULT_MAX_OUTCOME_RESPONSE_SIZE_IN_BYTES = 1000000L; // 1MB

  public static final int DEFAULT_MAX_CONCURRENCY_ENTERPRISE = 100;

  public static final long DEFAULT_MAX_INPUT_PARAMETER_SIZE_IN_BYTES = 1000000L; // 1MB

  public static final long DEFAULT_NO_LIMIT = -1;

  public static final int DEFAULT_MAX_QUEUED_EXECUTIONS = 2000;

  public static final long DEFAULT_MAX_PIPELINE_CREATION_LIMIT = 10000;

  public static final int DEFAULT_MAX_TRIGGER_CREATION_LIMIT = 10000;

  public static final long DEFAULT_MAX_FILE_SIZE_LIMIT = 1000000L;

  public static final long DEFAULT_MAX_PAYLOAD_SIZE_LIMIT = 10000000L;

  public static final int DEFAULT_STEP_OR_STAGE_MAX_CONCURRENCY = 1000000;

  public static final int DEFAULT_MAX_EXPRESSION_CALLS = 200;
}