/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data.sweepingoutput;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.RawSweepingOutputConsumeUpsert;
import io.harness.engine.pms.data.Resolver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;

import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsSweepingOutputService extends Resolver {
  RawOptionalSweepingOutput resolveOptional(Ambiance ambiance, RefObject refObject);

  List<RawOptionalSweepingOutput> findOutputsUsingNodeId(Ambiance ambiance, String name, List<String> nodeIds);
  List<RawOptionalSweepingOutput> findOutputsWithGivenNameAndStageExecution(Ambiance ambiance, String name);

  List<RawOptionalSweepingOutput> findOutputsUsingExecutionIds(Ambiance ambiance, String name, List<String> nodeIds);

  List<ExecutionSweepingOutputInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId);

  List<String> fetchNameOfOutcomesInPlanExecutionId(String planExecutionId);

  List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionUuid);

  /**
   * Delete all sweeping output instances for given planExecutionIds
   * Uses - unique_levelRuntimeIdUniqueIdx2
   * @param planExecutionIds
   */
  void deleteAllSweepingOutputInstances(Set<String> planExecutionIds);

  /**
   * Updates all sweeping output instances for given planExecutionId
   * Uses - unique_levelRuntimeIdUniqueIdx2
   * @param planExecutionId
   */
  void updateTTL(String planExecutionId, Date ttlDate);

  /**
   * Note: Use this method with caution
   * Overriding Behaviour: If an existing output is present at same scope and name, then override with `value` specified
   *
   * If such an output is not present, behaviour is same as consume
   * @return the uuid of the instance created/modified and isUpsert boolean
   */
  RawSweepingOutputConsumeUpsert consumeUpsert(
      @NotNull Ambiance ambiance, @NotNull String name, String value, String groupName);
}
