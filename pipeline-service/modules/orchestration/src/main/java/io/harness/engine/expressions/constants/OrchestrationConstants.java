/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.constants;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public final class OrchestrationConstants {
  private OrchestrationConstants() {}

  public static final String STAGE_SUCCESS = "OnStageSuccess";
  public static final String STAGE_FAILURE = "OnStageFailure";
  public static final String PIPELINE_SUCCESS = "OnPipelineSuccess";
  public static final String PIPELINE_FAILURE = "OnPipelineFailure";
  public static final String ALWAYS = "Always";
  public static final String CURRENT_STATUS = "currentStatus";
  public static final String EXECUTION_URL = "executionUrl";
  public static final String LIVE_STATUS = "liveStatus";
  public static final String ROLLBACK_MODE_EXECUTION = "OnRollbackModeExecution";

  // DAG dependency status aliases — mirrors OnPipelineSuccess/OnPipelineFailure.
  // The "All"/"Any" prefix makes the aggregation explicit in the alias name.
  // Must match WhenConditionConstants.ON_ALL_DEPENDANTS_SUCCESS / ON_ANY_DEPENDANT_FAILURE.
  public static final String ALL_DEPENDANTS_SUCCESS = "OnAllDependantsSuccess";
  public static final String ANY_DEPENDANT_FAILURE = "OnAnyDependantFailure";

  // NodeExecutionMap resolver keys for DAG dependency status. Because these live on NodeExecutionMap,
  // they resolve per-node — so <+stage.allDependantsSucceeded> works today for DAG stages, and once
  // DAG support reaches steps / step-groups, <+step.allDependantsSucceeded> will work without any
  // additional wiring, since NodeExecutionMap is built for any level.
  public static final String ALL_DEPENDANTS_SUCCEEDED = "allDependantsSucceeded";
  public static final String ANY_DEPENDANT_FAILED = "anyDependantFailed";
}
