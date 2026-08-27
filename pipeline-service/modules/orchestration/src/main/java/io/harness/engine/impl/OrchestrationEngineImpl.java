/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.execution.strategy.factory.NodeExecutionStrategyFactory;
import io.harness.engine.pms.execution.strategy.node.NodeExecutionStrategy;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.InitiateNodeBatchRequest;
import io.harness.execution.InitiateNodeRequest;
import io.harness.execution.PmsNodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.execution.RunNodeRequest;
import io.harness.execution.RunNodeRequest.RunNodeRequestBuilder;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.IdentityDeclaredLevel;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.workloadidentity.NodeIdentityCascadeHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Please do not use this class outside of orchestration module. All the interactions with engine must be done via
 * {@link OrchestrationService}. This is for the internal workings of the engine
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OrchestrationEngineImpl implements OrchestrationEngine {
  @Inject private PlanService planService;
  @Inject private NodeExecutionStrategyFactory strategyFactory;

  @Override
  public <T extends PmsNodeExecution> T runNode(
      @NonNull Ambiance ambiance, @NonNull Node node, PmsNodeExecutionMetadata metadata) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNode(ambiance, node, metadata);
  }

  @Override
  public <T extends PmsNodeExecution> T initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId,
      @NonNull String runtimeId, PmsNodeExecutionMetadata metadata) {
    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(ambiance, PmsLevelUtils.buildLevelFromNode(runtimeId, node));
    clonedAmbiance = NodeIdentityCascadeHelper.mergeIdentities(clonedAmbiance, node, identityLevelFor(node));
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNode(clonedAmbiance, node, metadata);
  }

  @Override
  public <T extends PmsNodeExecution> T initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId,
      @NonNull String runtimeId, PmsNodeExecutionMetadata metadata, StrategyMetadata strategyMetadata,
      InitiateMode initiateMode) {
    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(ambiance,
        PmsLevelUtils.buildLevelFromNode(runtimeId, node, strategyMetadata,
            AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.PIPE_REMOVE_STRATEGY_METADATA_POPULATION.name())));
    clonedAmbiance = NodeIdentityCascadeHelper.mergeIdentities(clonedAmbiance, node, identityLevelFor(node));
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    if (metadata == null) {
      metadata = strategy.createMetadata(strategyMetadata);
    }
    return (T) strategy.runNode(clonedAmbiance, node, metadata, initiateMode);
  }

  @Override
  public <T extends PmsNodeExecution> List<T> initiateNodes(
      InitiateNodeBatchRequest initiateNodeBatchRequest, InitiateMode initiateMode) {
    Ambiance parentAmbiance = initiateNodeBatchRequest.getParentAmbiance();
    List<String> nodeIds = initiateNodeBatchRequest.getNodes().stream().map(InitiateNodeRequest::getSetupId).toList();
    Map<String, Node> nodes = planService.fetchAllNodes(parentAmbiance.getPlanId(), new HashSet<>(nodeIds))
                                  .stream()
                                  .collect(Collectors.toMap(Node::getUuid, obj -> obj));

    Map<NodeExecutionStrategy, List<RunNodeRequest>> strategyToRequestsMap = new HashMap<>();
    String parentId = AmbianceUtils.obtainCurrentRuntimeId(parentAmbiance);

    boolean shouldRemoveStrategyMetadataPopulation = AmbianceUtils.checkIfFeatureFlagEnabled(
        parentAmbiance, FeatureName.PIPE_REMOVE_STRATEGY_METADATA_POPULATION.name());
    // Map to List of RunNodeRequest
    for (InitiateNodeRequest request : initiateNodeBatchRequest.getNodes()) {
      Node node = nodes.get(request.getSetupId());
      Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(parentAmbiance,
          PmsLevelUtils.buildLevelFromNode(
              request.getRuntimeId(), node, request.getStrategyMetadata(), shouldRemoveStrategyMetadataPopulation));
      clonedAmbiance = NodeIdentityCascadeHelper.mergeIdentities(clonedAmbiance, node, identityLevelFor(node));
      RunNodeRequestBuilder builder = RunNodeRequest.builder()
                                          .strategyMetadata(request.getStrategyMetadata())
                                          .runtimeId(request.getRuntimeId())
                                          .setupId(request.getSetupId())
                                          .node(node)
                                          .ambiance(clonedAmbiance)
                                          .parentId(parentId)
                                          .notifyId(parentId == null ? null : request.getRuntimeId());

      NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
      if (request.getMetadata() == null) {
        builder.metadata(strategy.createMetadata(request.getStrategyMetadata()));
      }
      strategyToRequestsMap.computeIfAbsent(strategy, k -> new ArrayList<>()).add(builder.build());
    }

    List<T> initiateNodesResult = new ArrayList<>();
    strategyToRequestsMap.forEach((strategy, requests) -> {
      initiateNodesResult.addAll(strategy.runNodes(
          RunNodeBatchRequest.builder().parentAmbiance(parentAmbiance).nodes(requests).build(), initiateMode));
    });
    return initiateNodesResult;
  }

  @Override
  public <T extends PmsNodeExecution> T runNextNode(
      @NonNull Ambiance ambiance, @NonNull Node node, T previousExecution, PmsNodeExecutionMetadata metadata) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNextNode(ambiance, node, previousExecution, metadata);
  }

  @Override
  public void startNodeExecution(Ambiance ambiance) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.startExecution(ambiance);
  }

  @Override
  public void queueOrStartExecution(Ambiance ambiance) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.queueOrStartExecution(ambiance);
  }

  @Override
  public void processFacilitatorResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processFacilitationResponse(ambiance, facilitatorResponse);
  }

  @Override
  public void processStepResponse(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processStepResponse(ambiance, stepResponse);
  }

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.resumeNodeExecution(ambiance, response, asyncError);
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processAdviserResponse(ambiance, adviserResponse);
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.handleError(ambiance, exception);
  }

  @Override
  public void concludeNodeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.concludeExecution(ambiance, toStatus, fromStatus, overrideStatusSet);
  }

  @Override
  public void endNodeExecution(Ambiance ambiance) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.endNodeExecution(ambiance, null, null);
  }

  @Override
  public void handleSdkResponseEvent(SdkResponseEventProto event) {
    NodeExecutionStrategy strategy =
        strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(event.getAmbiance()));
    strategy.handleSdkResponseEvent(event);
  }

  private static IdentityDeclaredLevel identityLevelFor(Node node) {
    return switch (node.getStepCategory()) {
          case PIPELINE -> IdentityDeclaredLevel.IDENTITY_LEVEL_PIPELINE;
          case STAGE -> IdentityDeclaredLevel.IDENTITY_LEVEL_STAGE;
          default -> IdentityDeclaredLevel.IDENTITY_LEVEL_STEP;
      };
  }
}
