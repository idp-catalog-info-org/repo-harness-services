/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.InitiateNodeBatchRequest;
import io.harness.execution.PmsNodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.NonNull;

@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(HarnessTeam.PIPELINE)
public interface OrchestrationEngine {
  <T extends PmsNodeExecution> T runNode(
      @NonNull Ambiance ambiance, @NonNull Node node, PmsNodeExecutionMetadata metadata);

  <T extends PmsNodeExecution> T initiateNode(
      @NonNull Ambiance ambiance, @NonNull String nodeId, @NonNull String runtimeId, PmsNodeExecutionMetadata metadata);

  <T extends PmsNodeExecution> T initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId,
      @NonNull String runtimeId, PmsNodeExecutionMetadata metadata, StrategyMetadata strategyMetadata,
      InitiateMode initiateMode);

  <T extends PmsNodeExecution> List<T> initiateNodes(
      InitiateNodeBatchRequest initiateNodeBatchRequest, InitiateMode initiateMode);

  <T extends PmsNodeExecution> T runNextNode(
      @NonNull Ambiance ambiance, @NonNull Node node, T previousExecution, PmsNodeExecutionMetadata metadata);
  void startNodeExecution(Ambiance ambiance);

  void queueOrStartExecution(Ambiance ambiance);

  void processFacilitatorResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse);

  void processStepResponse(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse);

  void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError);

  void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse);

  void handleError(Ambiance ambiance, Exception exception);

  void concludeNodeExecution(Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet);

  void endNodeExecution(Ambiance ambiance);

  void handleSdkResponseEvent(SdkResponseEventProto event);
}
