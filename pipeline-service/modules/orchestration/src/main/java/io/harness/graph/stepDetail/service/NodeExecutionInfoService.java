/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface NodeExecutionInfoService {
  void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name);

  // TODO: Make this better this should be called from no where else
  void saveNodeExecutionInfo(
      String nodeExecutionId, String planExecutionId, StrategyMetadata metadata, String accountIdentifier);
  void saveNodeExecutionInfo(List<NodeExecution> nodeExecutionBuilders, List<StrategyMetadata> requests);

  void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId);

  void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId,
      StrategyMetadata strategyMetadata, String accountIdentifier);

  boolean addStepInputsInternal(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId,
      StrategyMetadata strategyMetadata, String accountIdentifier);

  PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId);

  PmsStepParameters getStepInputsRecasterPruned(String planExecutionId, String nodeExecutionId);

  PmsStepParameters getStepInputsRecasterPruned(PmsStepParameters pmsStepParameters);

  NodeExecutionsInfo getNodeExecutionsInfo(String nodeExecutionId);
  NodeExecutionsInfo getNodeExecutionsInfoWithProjections(String nodeExecutionId, Set<String> projections);

  Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId);

  Map<String, PmsStepDetails> getStepDetailsFormNodeExecutionInfo(NodeExecutionsInfo nodeExecutionsInfo);

  Stream<NodeExecutionsInfo> getStepDetailsNotUpdatedInGraph(String planExecutionId, Long lastUpdatedAt);

  Stream<NodeExecutionsInfo> getStepDetailsNotUpdatedInGraphFromSecondary(String planExecutionId, Long lastUpdatedAt);

  boolean checkIfUnprocessedNodeExecutionInfo(String planExecutionId, Long lastUpdatedAt);

  void saveNodeExecutionInfoForRetry(String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId);

  void addConcurrentChildInformation(ConcurrentChildInstance concurrentChildInstance, String nodeExecutionId);

  ConcurrentChildInstance incrementCursor(String nodeExecutionId, Status status);

  ConcurrentChildInstance fetchConcurrentChildInstance(String nodeExecutionId);

  /**
   * Delete all nodeExecutionInfo for given nodeExecutionIds
   * Uses - nodeExecutionId_unique_idx index
   * @param nodeExecutionIds
   */
  void deleteNodeExecutionInfoForGivenIds(Set<String> nodeExecutionIds);

  /**
   * Updates TTL for all nodeExecutionInfo for given planExecutionId
   * Uses - nodeExecutionId_unique_idx index
   * @param planExecutionId
   */
  void updateTTLForNodesForGivenPlanExecutionId(String planExecutionId, Date ttlDate);

  Map<String, Object> fetchStrategyObjectMap(String nodeExecutionId);

  Map<String, Object> fetchStrategyObjectMap(List<Level> levelsWithStrategyMetadata);

  Map<String, StrategyMetadata> fetchStrategyMetadata(List<String> nodeExecutionIds);

  StrategyMetadata getStrategyMetadata(NodeExecution nodeExecution);

  StrategyMetadata getStrategyMetadata(Level level);

  Optional<Status> getCurrentStatus(String nodeExecutionId);

  void updateCalculatedStatusForParentNodes(NodeExecution nodeExecution);

  void publishStepDetailsUpdate(String accountId, String planExecutionId, String nodeExecutionId);

  void publishStepDetailsUpdate(String accountId, String planExecutionId, String nodeExecutionId, StepType stepType);

  void clearFirstUnsuccessfulRuntimeIdChain(String nodeExecutionId);
}
