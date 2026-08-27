/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata.Builder;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.SubCategory;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.plan.utils.PlanResourceUtility;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  private NodeExecutionService nodeExecutionService;
  private PlanExecutionService planExecutionService;
  private PlanService planService;
  private PrincipalInfoHelper principalInfoHelper;
  private RollbackModeYamlTransformer rollbackModeYamlTransformer;
  private PmsFeatureFlagService featureFlagService;
  private NodeExecutionInfoService nodeExecutionInfoService;

  public ExecutionMetadata transformExecutionMetadata(ExecutionMetadata executionMetadata, String planExecutionID,
      ExecutionTriggerInfo triggerInfo, ExecutionMode executionMode, PipelineStageInfo parentStageInfo,
      List<String> stageNodeExecutionIds) {
    String originalPlanExecutionId = executionMetadata.getExecutionUuid();
    Builder newMetadata = executionMetadata.toBuilder()
                              .setExecutionUuid(planExecutionID)
                              .setTriggerInfo(triggerInfo)
                              .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
                              .setExecutionMode(executionMode)
                              .setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionId);
    if (parentStageInfo != null) {
      newMetadata = newMetadata.setPipelineStageInfo(parentStageInfo);
    }

    return newMetadata.build();
  }

  public PlanExecutionMetadata transformPlanExecutionMetadata(PlanExecutionMetadata planExecutionMetadata,
      String planExecutionID, ExecutionMode executionMode, List<String> stageNodeExecutionIds, String updatedNotes,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    String originalPlanExecutionId = planExecutionMetadata.getPlanExecutionId();
    boolean readSwitchEnabled = featureFlagService.isEnabled(
        planExecutionMetadata.getAccountIdentifier(), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional =
          planExecutionService.getWithFieldsIncludedOptional(originalPlanExecutionId,
              Set.of(PlanExecutionKeys.triggerHeader, PlanExecutionKeys.triggerJsonPayload,
                  PlanExecutionKeys.triggerPayload, PlanExecutionKeys.expressionFunctorToken,
                  PlanExecutionKeys.stageExpressionValuesMap, PlanExecutionKeys.processedYaml));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }

    boolean enableDAG = resolveEnableDAGFromOriginalExecution(originalPlanExecutionId, planExecution);

    String getProcessedYaml =
        PlanExecutionMigrationHelper.readProcessedYamlWithFallBackOnMetadata(planExecutionMetadata, planExecution);
    String processedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        planExecutionMetadata.getAccountIdentifier(), getProcessedYaml, executionMode, originalPlanExecutionId,
        stageNodeExecutionIds, planExecutionMetadata.getHarnessVersion(), enableDAG);
    planExecutionMetadataWithContext.setProcessedYaml(processedYaml);
    PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
        planExecutionMetadata.toBuilder()
            .planExecutionId(planExecutionID)
            .processedYaml(processedYaml)
            .notes(updatedNotes) // these are updated notes given for a pipelineRollback.
            .uuid(null); // this uuid is the mongo uuid. It is being set as null so that when this Plan Execution
                         // Metadata is saved later on in the execution, a new object is stored rather than
                         // replacing the Metadata for the original execution

    if (planExecution != null) {
      populatePlanExecutionMetadataWithContextFromPlanExecution(planExecutionMetadataWithContext, planExecution);
    }

    if (EmptyPredicate.isEmpty(stageNodeExecutionIds)) {
      return planExecutionMetadataBuilder.build();
    }

    List<NodeExecution> rollbackStageNodeExecutions = nodeExecutionService.getAllWithFieldIncluded(
        new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.fieldsForRollbackTransformer);

    List<String> rollbackStageFQNs = new LinkedList<>();
    // Adding postExecutionRollbackInfo
    rollbackStageNodeExecutions.forEach(rollbackStageNodeExecution -> {
      PostExecutionRollbackInfo postExecutionRollbackInfo = createPostExecutionRollbackInfo(rollbackStageNodeExecution);
      planExecutionMetadataBuilder.postExecutionRollbackInfo(postExecutionRollbackInfo);
      planExecutionMetadataWithContext.getPostExecutionRollbackInfos().add(postExecutionRollbackInfo);
      rollbackStageFQNs.add(rollbackStageNodeExecution.getStageFqn());
    });

    StagesExecutionMetadata stagesExecutionMetadata =
        StagesExecutionMetadata.builder()
            .fullPipelineYaml(planExecutionMetadata.getYaml())
            .stageIdentifiers(rollbackStageFQNs)
            .stageIdentifierToNameMap(StagesExecutionHelper.getStageIdentifierToNameMap(
                planExecutionMetadata.getYaml(), rollbackStageFQNs, planExecutionMetadata.getHarnessVersion()))
            .build();
    planExecutionMetadataBuilder.stagesExecutionMetadata(stagesExecutionMetadata);
    planExecutionMetadataWithContext.setStagesExecutionMetadata(stagesExecutionMetadata);
    return planExecutionMetadataBuilder.build();
  }

  private boolean resolveEnableDAGFromOriginalExecution(String originalPlanExecutionId, PlanExecution planExecution) {
    if (planExecution != null && planExecution.getMetadata() != null) {
      return planExecution.getMetadata().getEnableDAG();
    }
    Optional<PlanExecution> planExecutionOptional =
        planExecutionService.getWithFieldsIncludedOptional(originalPlanExecutionId, Set.of(PlanExecutionKeys.metadata));
    if (planExecutionOptional.isPresent() && planExecutionOptional.get().getMetadata() != null) {
      return planExecutionOptional.get().getMetadata().getEnableDAG();
    }
    return false;
  }

  private static void populatePlanExecutionMetadataWithContextFromPlanExecution(
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, PlanExecution planExecution) {
    // Populate PlanExecutionMetadataWithContext with original planExecution
    planExecutionMetadataWithContext.setTriggerHeader(planExecution.getTriggerHeader());
    planExecutionMetadataWithContext.setTriggerJsonPayload(planExecution.getTriggerJsonPayload());
    planExecutionMetadataWithContext.setExpressionFunctorToken(planExecution.getExpressionFunctorToken());
    planExecutionMetadataWithContext.setTriggerPayload(planExecution.getTriggerPayload());
    planExecutionMetadataWithContext.setStageExpressionValuesMap(planExecution.getStageExpressionValuesMap());
    planExecutionMetadataWithContext.setProcessedYaml(planExecution.getProcessedYaml());
  }

  private PostExecutionRollbackInfo createPostExecutionRollbackInfo(NodeExecution rollbackStageNodeExecution) {
    PostExecutionRollbackInfo.Builder builder = PostExecutionRollbackInfo.newBuilder();
    String stageId;
    // This stageId will also be the startingNodeId in the execution graph. So if its under the
    // strategy(Multi-deployment) then it must be set to strategy setupId so that graph is shown correctly.
    if (NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(rollbackStageNodeExecution).isPresent()) {
      // If the nodeExecutions is under the strategy, then set the stageId to strategy setupId.
      stageId = NodeExecutionContextUtils.getStrategySetupId(rollbackStageNodeExecution);
      builder.setRollbackStageStrategyMetadata(
          nodeExecutionInfoService.getStrategyMetadata(rollbackStageNodeExecution));
    } else {
      // If not under strategy then stage setupId will be the stageId.
      stageId = NodeExecutionContextUtils.obtainCurrentSetupId(rollbackStageNodeExecution);
    }
    builder.setPostExecutionRollbackStageId(stageId);
    String stageExecutionId = NodeExecutionContextUtils.obtainCurrentRuntimeId(rollbackStageNodeExecution);
    builder.setOriginalStageExecutionId(stageExecutionId);
    return builder.build();
  }

  /**
   * Step1: Initialise a map from planNodeIDs to Plan Nodes
   * Step2: fetch all node executions of previous execution that are the descendants of any stage
   * Step3: create identity plan nodes for all node executions that are the descendants of any stage, and add them to
   * the map
   * Step4: Go through `createdPlan`. If any Plan node has AdvisorObtainments for POST_EXECUTION_ROLLBACK Mode, add them
   * to the corresponding Identity Plan Node in the initialised map
   * Step5: From `createdPlan`, pick out all nodes that are not a descendants of some stage, and add them to the
   * initialised map.
   * Step6: For all IDs in `nodeIDsToPreserve`, remove the Identity Plan Nodes in the map, and put the
   * Plan nodes from `createdPlan`
   */
  public Plan transformPlanForRollbackMode(Plan createdPlan, String previousExecutionId, List<String> nodeIDsToPreserve,
      ExecutionMode executionMode, List<String> rollbackStageIds, String accountId) {
    boolean flagVal =
        featureFlagService.isEnabled(accountId, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK);

    // steps 1, 2, and 3
    Map<String, Node> planNodeIDToUpdatedPlanNodes =
        buildIdentityNodes(previousExecutionId, createdPlan.getPlanNodes(), flagVal);

    // step 4
    addAdvisorsToIdentityNodes(createdPlan, planNodeIDToUpdatedPlanNodes, executionMode, rollbackStageIds);

    if (flagVal) {
      // steps 5 and 6
      addPreservedPlanNodesV2(
          createdPlan, nodeIDsToPreserve, planNodeIDToUpdatedPlanNodes, executionMode, rollbackStageIds);
    } else {
      addPreservedPlanNodes(createdPlan, nodeIDsToPreserve, planNodeIDToUpdatedPlanNodes);
    }

    Plan transformedPlan = Plan.builder()
                               .uuid(createdPlan.getUuid())
                               .planNodes(planNodeIDToUpdatedPlanNodes.values())
                               .startingNodeId(createdPlan.getStartingNodeId())
                               .setupAbstractions(createdPlan.getSetupAbstractions())
                               .graphLayoutInfo(createdPlan.getGraphLayoutInfo())
                               .validUntil(createdPlan.getValidUntil())
                               .valid(createdPlan.isValid())
                               .errorResponse(createdPlan.getErrorResponse())
                               .build();

    if (executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK) {
      boolean enableDAG = resolveEnableDAGFromOriginalExecution(previousExecutionId, null);
      Map<String, List<String>> dependencyGraph = getDependencyGraphFromPlan(createdPlan);
      if (isDagPostExecutionRollbackActive(
              accountId, enableDAG, isDagEnabledPlan(createdPlan), dependencyGraph, featureFlagService)) {
        return adjustDagPlanForPostExecutionRollback(transformedPlan, createdPlan, nodeIDsToPreserve, rollbackStageIds);
      }
    }
    return transformedPlan;
  }

  /**
   * Runtime DAG post-prod rollback gate — aligned with YAML transform ({@code enableDAG} + FF + DAG plan shape).
   */
  public static boolean isDagPostExecutionRollbackActive(String accountId, boolean enableDAG,
      boolean isDagEnabledOnPlan, Map<String, List<String>> dependencyGraph, PmsFeatureFlagService featureFlagService) {
    return enableDAG && featureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)
        && isDagEnabledOnPlan && dependencyGraph != null && !dependencyGraph.isEmpty();
  }

  private boolean isDagEnabledPlan(Plan plan) {
    return plan.getGraphLayoutInfo() != null && plan.getGraphLayoutInfo().getIsDagEnabled();
  }

  private Plan adjustDagPlanForPostExecutionRollback(
      Plan transformedPlan, Plan originalPlan, List<String> nodeIDsToPreserve, List<String> rollbackStageIds) {
    Map<String, List<String>> fullDependencyGraph = getDependencyGraphFromPlan(transformedPlan);
    if (EmptyPredicate.isEmpty(fullDependencyGraph)) {
      return transformedPlan;
    }

    List<String> rollbackTargetStageIds =
        getRollbackTargetStageNodeIds(transformedPlan, rollbackStageIds, fullDependencyGraph.keySet());
    if (EmptyPredicate.isEmpty(rollbackTargetStageIds)) {
      return transformedPlan;
    }

    // Layout graph: focused rollback run — only the rollback target (or its stage-level strategy wrapper for
    // multi-deploy), not upstream DAG context. Matches sequential post-prod rollback UX.
    List<String> layoutFocusNodeIds =
        resolvePostExecutionRollbackLayoutFocusNodeIds(transformedPlan, rollbackTargetStageIds, fullDependencyGraph);
    Set<String> layoutGraphNodes = new LinkedHashSet<>(layoutFocusNodeIds);
    Map<String, List<String>> layoutDependencyGraph =
        DependencyUtils.pruneDependencyGraph(fullDependencyGraph, layoutGraphNodes);
    List<String> layoutRootStageIds = sortStageIdsByStageFqn(
        transformedPlan, DependencyUtils.findRootNodesInDependencyGraphMap(layoutDependencyGraph));

    // Execution graph: only rollback target(s) run — upstream identity stages stay layout-only, matching
    // sequential post-prod rollback where custom stages are not scheduled (no IdentityStep replay).
    Set<String> executionGraphNodes = new HashSet<>(rollbackTargetStageIds);
    Map<String, List<String>> executionDependencyGraph =
        DependencyUtils.pruneDependencyGraph(fullDependencyGraph, executionGraphNodes);
    List<String> executionRootStageIds = sortStageIdsByStageFqn(
        transformedPlan, DependencyUtils.findRootNodesInDependencyGraphMap(executionDependencyGraph));
    if (EmptyPredicate.isEmpty(executionRootStageIds)) {
      return transformedPlan;
    }

    DependencyGraphProto executionDependencyGraphProto =
        DependencyUtils.buildDependencyGraphProto(executionDependencyGraph);
    DependencyGraphProto layoutDependencyGraphProto = DependencyUtils.buildDependencyGraphProto(layoutDependencyGraph);

    List<Node> updatedNodes = new ArrayList<>();
    for (Node node : transformedPlan.getPlanNodes()) {
      if (node instanceof PlanNode planNode && planNode.hasDependencyGraph()
          && OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY.equals(planNode.getStepType().getType())) {
        Map<String, Object> paramsMap =
            planNode.getStepParameters() != null ? new HashMap<>(planNode.getStepParameters()) : new HashMap<>();
        paramsMap.put("childrenIds", executionRootStageIds);
        updatedNodes.add(planNode.toBuilder()
                             .stepParameters(new PmsStepParameters(paramsMap))
                             .dependencyGraph(executionDependencyGraphProto)
                             .build());
      } else {
        updatedNodes.add(node);
      }
    }

    GraphLayoutInfo graphLayoutInfo = originalPlan.getGraphLayoutInfo();
    Map<String, GraphLayoutNode> prunedLayoutNodesMap =
        DependencyUtils.pruneLayoutNodeMapForSubgraph(graphLayoutInfo.getLayoutNodesMap(), layoutFocusNodeIds, false);
    GraphLayoutInfo updatedGraphLayoutInfo = graphLayoutInfo.toBuilder()
                                                 .clearLayoutNodes()
                                                 .putAllLayoutNodes(prunedLayoutNodesMap)
                                                 .setDependencyGraph(layoutDependencyGraphProto)
                                                 .clearStartingNodeIds()
                                                 .addAllStartingNodeIds(layoutRootStageIds)
                                                 .setStartingNodeId(layoutRootStageIds.get(0))
                                                 .build();

    return Plan.builder()
        .uuid(transformedPlan.getUuid())
        .planNodes(updatedNodes)
        .startingNodeId(transformedPlan.getStartingNodeId())
        .setupAbstractions(transformedPlan.getSetupAbstractions())
        .graphLayoutInfo(updatedGraphLayoutInfo)
        .validUntil(transformedPlan.getValidUntil())
        .valid(transformedPlan.isValid())
        .errorResponse(transformedPlan.getErrorResponse())
        .build();
  }

  private Map<String, List<String>> getDependencyGraphFromPlan(Plan plan) {
    Optional<PlanNode> stagesPlanNode =
        plan.getPlanNodes()
            .stream()
            .filter(PlanNode.class ::isInstance)
            .map(PlanNode.class ::cast)
            .filter(PlanNode::hasDependencyGraph)
            .filter(node -> OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY.equals(node.getStepType().getType()))
            .findFirst();
    if (stagesPlanNode.isPresent()) {
      return DependencyUtils.convertDependencyGraphToMap(stagesPlanNode.get().getDependencyGraph());
    }
    if (plan.getGraphLayoutInfo() != null && plan.getGraphLayoutInfo().hasDependencyGraph()) {
      return DependencyUtils.convertDependencyGraphToMap(plan.getGraphLayoutInfo().getDependencyGraph());
    }
    return Collections.emptyMap();
  }

  /**
   * Layout focus node(s) for DAG post-prod rollback. When the rollback target sits under stage-level multi-deploy,
   * the strategy wrapper node is used so the UI graph matches {@code PostExecutionRollbackInfo}.
   */
  private List<String> resolvePostExecutionRollbackLayoutFocusNodeIds(
      Plan plan, List<String> rollbackTargetStageIds, Map<String, List<String>> reversedDependencyGraph) {
    if (EmptyPredicate.isEmpty(rollbackTargetStageIds)) {
      return Collections.emptyList();
    }
    Map<String, List<String>> forwardDependencyGraph =
        DependencyUtils.computeReverseDependencyGraph(reversedDependencyGraph);
    Map<String, Node> nodesById =
        plan.getPlanNodes().stream().collect(Collectors.toMap(Node::getUuid, node -> node, (left, right) -> left));
    return rollbackTargetStageIds.stream()
        .map(targetId -> findStageLevelStrategyParent(forwardDependencyGraph, nodesById, targetId).orElse(targetId))
        .distinct()
        .collect(Collectors.toList());
  }

  private Optional<String> findStageLevelStrategyParent(
      Map<String, List<String>> forwardDependencyGraph, Map<String, Node> nodesById, String targetStageId) {
    for (Map.Entry<String, List<String>> entry : forwardDependencyGraph.entrySet()) {
      if (!entry.getValue().contains(targetStageId)) {
        continue;
      }
      Node parentNode = nodesById.get(entry.getKey());
      if (parentNode != null && parentNode.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        return Optional.of(entry.getKey());
      }
    }
    return Optional.empty();
  }

  /**
   * Resolves rollback target stage node UUIDs from stage FQNs. Only nodes in the stage-level dependency graph are
   * included — step-level strategy nodes share the stage FQN but must not become STAGES {@code childrenIds}.
   */
  private List<String> getRollbackTargetStageNodeIds(
      Plan plan, List<String> rollbackStageIds, Set<String> dependencyGraphNodeIds) {
    if (EmptyPredicate.isEmpty(rollbackStageIds)) {
      return Collections.emptyList();
    }
    // Stages are converted to IdentityPlanNode in buildIdentityNodes — match on Node, not PlanNode only.
    return plan.getPlanNodes()
        .stream()
        .filter(node -> node.getStepType().getStepCategory() == StepCategory.STAGE)
        .filter(node -> dependencyGraphNodeIds.contains(node.getUuid()))
        .filter(node -> rollbackStageIds.contains(node.getStageFqn()))
        .sorted(Comparator.comparing(Node::getStageFqn, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(Node::getUuid)
        .collect(Collectors.toList());
  }

  private List<String> sortStageIdsByStageFqn(Plan plan, List<String> stageIds) {
    if (EmptyPredicate.isEmpty(stageIds)) {
      return stageIds;
    }
    Map<String, String> uuidToStageFqn =
        plan.getPlanNodes().stream().collect(Collectors.toMap(Node::getUuid, Node::getStageFqn, (left, right) -> left));
    return stageIds.stream()
        .sorted(Comparator.comparing(id -> uuidToStageFqn.getOrDefault(id, id)))
        .collect(Collectors.toList());
  }

  Map<String, Node> buildIdentityNodes(
      String previousExecutionId, List<Node> createdPlanNodes, boolean usePipelineRollbackV2) {
    Map<String, Node> planNodeIDToUpdatedNodes = new HashMap<>();
    Map<String, Node> createdNodesMap = new HashMap<>();
    createdPlanNodes.forEach(o -> createdNodesMap.put(o.getUuid(), o));

    try (Stream<NodeExecution> nodeExecutionStream = getNodeExecutionsWithProjections(
             previousExecutionId, createdPlanNodes, NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation)) {
      Iterator<NodeExecution> nodeExecutions = nodeExecutionStream.iterator();
      while (nodeExecutions.hasNext()) {
        NodeExecution nodeExecution = nodeExecutions.next();
        String planNodeIdFromNodeExec = nodeExecution.getNodeId();
        if (!usePipelineRollbackV2 && nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE) {
          continue;
        }
        if (planNodeIDToUpdatedNodes.containsKey(nodeExecution.getNodeId())) {
          // this means that the current plan node ID was already added, hence this plan node has multiple node
          // executions mapped to it. Hence, the identity node created for the plan node needs to be updated to contain
          // the IDs of all the node executions mapped to it
          IdentityPlanNode previouslyAddedNode =
              (IdentityPlanNode) planNodeIDToUpdatedNodes.get(planNodeIdFromNodeExec);
          previouslyAddedNode.convertToListOfOGNodeExecIds(nodeExecution.getUuid());
          planNodeIDToUpdatedNodes.put(planNodeIdFromNodeExec, previouslyAddedNode);
        } else {
          Node node = planService.fetchNode(nodeExecution.getPlanId(), nodeExecution.getNodeId());
          if (nodeExecution.getStepType().getType().equals(OrchestrationStepTypes.DYNAMIC_STAGE)
              || nodeExecution.getStepType().getType().equals(OrchestrationStepTypes.DYNAMIC_STAGE_V1)) {
            planNodeIDToUpdatedNodes.put(planNodeIdFromNodeExec, createdNodesMap.get(planNodeIdFromNodeExec));
            continue;
          }

          IdentityPlanNode identityPlanNode;
          if (usePipelineRollbackV2) {
            if (nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE) {
              identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNode(node.getUuid(), node,
                  nodeExecution.getIdentifier(), nodeExecution.getName(), node.getStepType(), nodeExecution.getUuid());
            } else {
              identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNodeWithSkipAsTrue(node.getUuid(), node,
                  nodeExecution.getIdentifier(), nodeExecution.getName(), node.getStepType(), nodeExecution.getUuid());
            }
          } else {
            identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNodeWithSkipAsTrue(node.getUuid(), node,
                nodeExecution.getIdentifier(), nodeExecution.getName(), node.getStepType(), nodeExecution.getUuid());
          }
          planNodeIDToUpdatedNodes.put(planNodeIdFromNodeExec, identityPlanNode);
        }
      }
    }
    return planNodeIDToUpdatedNodes;
  }

  Stream<NodeExecution> getNodeExecutionsWithProjections(
      String previousExecutionId, List<Node> createdPlanNodes, Set<String> projections) {
    List<String> stageFQNs = createdPlanNodes.stream()
                                 .filter(n -> n.getStepCategory() == StepCategory.STAGE)
                                 .map(Node::getStageFqn)
                                 .collect(Collectors.toList());
    return nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(previousExecutionId, stageFQNs, projections);
  }

  void addAdvisorsToIdentityNodes(Plan createdPlan, Map<String, Node> planNodeIDToUpdatedPlanNodes,
      ExecutionMode executionMode, List<String> stageFQNsToRollback) {
    for (Node planNode : createdPlan.getPlanNodes()) {
      if (EmptyPredicate.isEmpty(planNode.getAdvisorObtainmentsForExecutionMode())) {
        continue;
      }
      if (executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK) {
        // use the advisorObtainmentsForRollback only stage/strategy nodes and all children nodes of node being
        // rolledback. So that for all stages, the advisor will start the new nextStage(reverse order) and for all
        // nodes of rollback stage, the advisors will start the rollback steps.
        if (EmptyPredicate.isEmpty(stageFQNsToRollback)
            || !(stageFQNsToRollback.contains(planNode.getStageFqn())
                || planNode.getGroup().equals(StepCategory.STAGE.name())
                || planNode.getGroup().equals(StepCategory.STRATEGY.name()))) {
          continue;
        }
      }
      List<AdviserObtainment> adviserObtainments = planNode.getAdvisorObtainmentsForExecutionMode().get(executionMode);
      if (planNode.getAdvisorObtainmentsForExecutionMode().containsKey(executionMode)) {
        Node updatedNode = planNodeIDToUpdatedPlanNodes.get(planNode.getUuid());
        if (updatedNode instanceof IdentityPlanNode) {
          planNodeIDToUpdatedPlanNodes.put(planNode.getUuid(),
              ((IdentityPlanNode) updatedNode)
                  .withAdviserObtainments(adviserObtainments)
                  .withUseAdviserObtainments(true));
        }
      }
    }
  }

  /**
   * It checks whether node should be preserved if it's ID is present in nodeIDsToPreserve list with
   * additional check of POST_EXECUTION_ROLLBACK executionMode for which it should have same stageFQN
   * as stage being rolled back
   * @param node
   * @param nodeIDsToPreserve
   * @param executionMode
   * @param rollbackStageIds
   * @return
   */
  private boolean shouldPreserveNode(
      Node node, List<String> nodeIDsToPreserve, ExecutionMode executionMode, List<String> rollbackStageIds) {
    boolean isNodeExistInPreserveMap = nodeIDsToPreserve.contains(node.getUuid());
    if (executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK) {
      return isNodeExistInPreserveMap && rollbackStageIds.contains(node.getStageFqn());
    }
    return isNodeExistInPreserveMap;
  }

  /**
   * It replaces the IdentityPlanNodes with PlanNodes in the planNodeIDToUpdatedPlanNodes map for all IDs present in
   * the nodeIDsToPreserve list. As nodeIDsToPreserve list contains all node IDs for which we want to run the
   * corresponding nodes rather than copying them from the original execution, as we do for IdentityPlanNodes.
   * @param createdPlan
   * @param nodeIDsToPreserve
   * @param planNodeIDToUpdatedPlanNodes
   * @param executionMode
   * @param rollbackIds
   */
  void addPreservedPlanNodesV2(Plan createdPlan, List<String> nodeIDsToPreserve,
      Map<String, Node> planNodeIDToUpdatedPlanNodes, ExecutionMode executionMode, List<String> rollbackIds) {
    for (Node node : createdPlan.getPlanNodes()) {
      if (shouldPreserveNode(node, nodeIDsToPreserve, executionMode, rollbackIds)
          || isStageOrAncestorOfSomeStageV2(node)) {
        PlanNode planNode = ((PlanNode) node).withPreserveInRollbackMode(true);
        planNodeIDToUpdatedPlanNodes.put(node.getUuid(), planNode);
      }
    }
  }

  boolean isStageOrAncestorOfSomeStageV2(Node planNode) {
    StepCategory stepCategory = planNode.getStepCategory();
    // This ensures that we keep only parallel at stage level or parallel in insert stages to be in planNode (Basically
    // all the parallel/stages nodes who are ancestor of stage) Todo(Sahil): Come up with a better way to handle this.
    boolean isAncestorOfStage = isAncestorOfStage(planNode);
    if (planNode.getStepCategory() == StepCategory.FORK
        && (planNode.getStageFqn().equals("pipeline.stages") || planNode.getStageFqn().equals("stages"))) {
      return true;
    }
    return Objects.equals(StepCategory.STAGES, stepCategory) || isAncestorOfStage;
  }

  private static boolean isAncestorOfStage(Node planNode) {
    return planNode.getStepType().getSubCategory() == SubCategory.STAGE_LEVEL
        || StepCategory.STAGE == planNode.getStepCategory()
        && (StepSpecTypeConstants.DYNAMIC_STAGE.equals(planNode.getStepType().getType())
            || OrchestrationStepTypes.DYNAMIC_STAGE_V1.equals(planNode.getStepType().getType()));
  }

  void addPreservedPlanNodes(
      Plan createdPlan, List<String> nodeIDsToPreserve, Map<String, Node> planNodeIDToUpdatedPlanNodes) {
    for (Node node : createdPlan.getPlanNodes()) {
      if (nodeIDsToPreserve.contains(node.getUuid()) || isStageOrAncestorOfSomeStage(node)) {
        PlanNode planNode = ((PlanNode) node).withPreserveInRollbackMode(true);
        planNodeIDToUpdatedPlanNodes.put(node.getUuid(), planNode);
      }
    }
  }

  boolean isStageOrAncestorOfSomeStage(Node planNode) {
    StepCategory stepCategory = planNode.getStepCategory();
    if (Arrays.asList(StepCategory.PIPELINE, StepCategory.STAGES, StepCategory.STAGE).contains(stepCategory)) {
      return true;
    }
    // todo: once fork and strategy are divided in sub categories of step and stage, add that check as well
    // parallel nodes and strategy nodes need to be plan nodes so that we don't take the advisor response from the
    // previous execution. Previous execution's advisor response would be setting next step as something we dont want in
    // rollback mode. We want the new advisors set in the Plan Node to be used
    // This will be removed post the change in PR#53874 is available in all sdks
    return Arrays.asList(StepCategory.FORK, StepCategory.STRATEGY).contains(stepCategory);
  }

  public void checkAndThrowExceptionIfExecutionOlderThanOneMonthForPostProdRollback(
      Long createdAt, ExecutionMode executionMode) {
    if (executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK) {
      boolean inTimeLimit = PlanResourceUtility.validateInTimeLimitForRetry(createdAt);
      if (!inTimeLimit) {
        throw new InvalidRequestException("This instance cannot be rolled back as the execution where this instance "
            + "was deployed is already 30 or more days old");
      }
    }
  }

  public void checkIfPostExecutionRollbackAllowed(List<String> stageNodeExecutionIds) {
    List<Status> stageNodeExecutionsWithBrokeStatuses =
        nodeExecutionService
            .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.withStatus)
            .stream()
            .map(NodeExecution::getStatus)
            .filter(status -> StatusUtils.brokeAndAbortedStatuses().contains(status))
            .toList();
    if (!stageNodeExecutionsWithBrokeStatuses.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Could not start the Post Execution Rollback because the stages %s are not SUCCEEDED but %s",
              stageNodeExecutionIds, stageNodeExecutionsWithBrokeStatuses));
    }
  }
}
