/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.helper.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.steps.io.StepResponseProto;

import lombok.NonNull;

@OwnedBy(HarnessTeam.PIPELINE)
public interface EndNodeExecutionHelper {
  void endNodeExecutionWithNoAdvisers(
      @NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse, PlanNode planNode);

  NodeExecution handleStepResponsePreAdviser(Ambiance ambiance, StepResponseProto stepResponse, PlanNode planNode);

  FailureData decorateFailureData(Ambiance ambiance, String errorMessage, FailureData failureData);
}
