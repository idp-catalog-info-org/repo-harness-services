/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.group.GroupPlanCreatorV1HelperUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;
import io.harness.steps.rollback.RollbackNode;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;
import io.harness.steps.rollback.RollbackStepsStep;
import io.harness.steps.rollback.StepGroupRollbackChainStep;
import io.harness.utils.execution.ExecutionModeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class StepGroupRollbackPlanCreatorUtils {
  /**
   * <ul>
   *   <li>Inner list with size == 1 → sequential (single step group rollback)</li>
   *   <li>Inner list with size  > 1 → parallel  (multiple step groups from same parallel: section)</li>
   * </ul>
   *  If a parallel section has only one qualifying step group, it's treated as sequential (size == 1).
   */
  public static List<List<RollbackNode>> collectAllStepGroupRollbackGroups(YamlField executionStepsField) {
    if (executionStepsField == null || executionStepsField.getNode() == null) {
      return Collections.emptyList();
    }
    List<YamlNode> stepsArray = executionStepsField.getNode().asArray();
    if (isEmpty(stepsArray)) {
      return Collections.emptyList();
    }

    List<List<RollbackNode>> sgRollbackGroups = new ArrayList<>();
    // ancestorHasStrategy=false: top-level traversal starts with no strategy ancestor
    collectStepGroupRollbackGroups(
        stepsArray, sgRollbackGroups, YAMLFieldNameConstants.STAGE + "." + YAMLFieldNameConstants.STEPS, false);

    if (!isEmpty(sgRollbackGroups)) {
      // Reverse — last SG's rollback runs first during backtracking
      Collections.reverse(sgRollbackGroups);
    }
    return sgRollbackGroups;
  }

  /**
   * Recursive traversal of the steps array, grouping entries by parallel context.
   * <ul>
   *   <li>Groups with rollback → added as singleton list (sequential)</li>
   *   <li>Groups without rollback → recurse into nested steps</li>
   *   <li>Parallel sections → collect all qualifying entries into a single multi-element list (parallel)</li>
   * </ul>
   *
   * @param ancestorHasStrategy true if any ancestor group in the recursion has a looping strategy.
   *                            Set on RollbackNode entries as insideStrategyAncestor so the runtime
   *                            chain step uses ancestor climbing for expression resolution.
   */
  private static void collectStepGroupRollbackGroups(List<YamlNode> stepsArray,
      List<List<RollbackNode>> sgRollbackGroups, String currentStepsExpression, boolean ancestorHasStrategy) {
    for (YamlNode stepNode : stepsArray) {
      YamlField groupField = new YamlField(stepNode).getNode().getField(YAMLFieldNameConstants.GROUP);
      if (groupField != null && groupField.getNode() != null) {
        String groupId = stepNode.getId();
        if (isEmpty(groupId)) {
          continue;
        }

        String groupExpression = currentStepsExpression + "." + groupId;
        boolean hasStrategy = stepNode.getField(YAMLFieldNameConstants.STRATEGY) != null;

        YamlField sgRollbackField = groupField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);
        boolean hasRollback = sgRollbackField != null && sgRollbackField.getNode() != null
            && !sgRollbackField.getNode().asArray().isEmpty();

        if (hasRollback) {
          // If the group has a looping strategy, the entry point is the strategy wrapper UUID;
          // otherwise, the rollback group UUID directly.
          String sgRollbackNodeId = sgRollbackField.getNode().getUuid()
              + (hasStrategy ? NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX
                             : NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX);
          // Sequential — singleton list (size == 1)
          sgRollbackGroups.add(Collections.singletonList(RollbackNode.builder()
                                                             .nodeId(sgRollbackNodeId)
                                                             .dependentNodeIdentifier(groupExpression)
                                                             .insideStrategyAncestor(ancestorHasStrategy)
                                                             .build()));
        } else if (hasStrategy && GroupPlanCreatorV1HelperUtils.hasDescendantRollbacks(groupField.getNode())) {
          // Group has strategy but no rollback, AND has descendants with rollback.
          // A synthetic strategy wrapper wraps descendant rollbacks with this group's strategy.
          String syntheticStrategyId =
              groupField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX;
          sgRollbackGroups.add(Collections.singletonList(RollbackNode.builder()
                                                             .nodeId(syntheticStrategyId)
                                                             .dependentNodeIdentifier(groupExpression)
                                                             .insideStrategyAncestor(ancestorHasStrategy)
                                                             .build()));
        } else {
          // Group without rollback (and no applicable strategy) — recurse to find deeper descendants.
          YamlField nestedStepsField = groupField.getNode().getField(YAMLFieldNameConstants.STEPS);
          if (nestedStepsField != null && nestedStepsField.getNode() != null) {
            List<YamlNode> nestedStepsArray = nestedStepsField.getNode().asArray();
            if (!isEmpty(nestedStepsArray)) {
              collectStepGroupRollbackGroups(nestedStepsArray, sgRollbackGroups,
                  groupExpression + "." + YAMLFieldNameConstants.STEPS, hasStrategy || ancestorHasStrategy);
            }
          }
        }
        continue;
      }

      YamlField parallelField = new YamlField(stepNode).getNode().getField(YAMLFieldNameConstants.PARALLEL);
      if (parallelField != null && parallelField.getNode() != null) {
        YamlField parallelStepsField = parallelField.getNode().getField(YAMLFieldNameConstants.STEPS);
        List<YamlNode> parallelChildren =
            parallelStepsField != null ? parallelStepsField.getNode().asArray() : parallelField.getNode().asArray();
        if (!isEmpty(parallelChildren)) {
          List<RollbackNode> parallelNodes = new ArrayList<>();
          collectParallelGroupRollbackNodes(
              parallelChildren, parallelNodes, currentStepsExpression, ancestorHasStrategy);
          if (!parallelNodes.isEmpty()) {
            // size > 1 → parallel group; size == 1 → treated as sequential by consumers
            sgRollbackGroups.add(parallelNodes);
          }
        }
      }
    }
  }

  /**
   * Collects RollbackNodes from within a parallel section into a flat list.
   * All entries will execute in parallel during rollback.
   * Handles nested groups (without rollback → recurse into children).
   */
  private static void collectParallelGroupRollbackNodes(List<YamlNode> stepsArray, List<RollbackNode> parallelNodes,
      String currentStepsExpression, boolean ancestorHasStrategy) {
    for (YamlNode stepNode : stepsArray) {
      YamlField groupField = new YamlField(stepNode).getNode().getField(YAMLFieldNameConstants.GROUP);
      if (groupField != null && groupField.getNode() != null) {
        String groupId = stepNode.getId();
        if (isEmpty(groupId)) {
          continue;
        }

        String groupExpression = currentStepsExpression + "." + groupId;
        boolean hasStrategy = stepNode.getField(YAMLFieldNameConstants.STRATEGY) != null;

        YamlField sgRollbackField = groupField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);
        boolean hasRollback = sgRollbackField != null && sgRollbackField.getNode() != null
            && !sgRollbackField.getNode().asArray().isEmpty();

        if (hasRollback) {
          // If the group has a looping strategy, the entry point is the strategy wrapper UUID;
          // otherwise, the rollback group UUID directly.
          String sgRollbackNodeId = sgRollbackField.getNode().getUuid()
              + (hasStrategy ? NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX
                             : NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX);
          parallelNodes.add(RollbackNode.builder()
                                .nodeId(sgRollbackNodeId)
                                .dependentNodeIdentifier(groupExpression)
                                .insideStrategyAncestor(ancestorHasStrategy)
                                .build());
        } else if (hasStrategy && GroupPlanCreatorV1HelperUtils.hasDescendantRollbacks(groupField.getNode())) {
          // Synthetic — strategy wrapper handles descendant rollbacks per-iteration
          String syntheticStrategyId =
              groupField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.STRATEGY_ROLLBACK_NODE_ID_SUFFIX;
          parallelNodes.add(RollbackNode.builder()
                                .nodeId(syntheticStrategyId)
                                .dependentNodeIdentifier(groupExpression)
                                .insideStrategyAncestor(ancestorHasStrategy)
                                .build());
        } else {
          // Group without rollback (and no applicable strategy) — recurse to find deeper descendants.
          YamlField nestedStepsField = groupField.getNode().getField(YAMLFieldNameConstants.STEPS);
          if (nestedStepsField != null && nestedStepsField.getNode() != null) {
            List<YamlNode> nestedStepsArray = nestedStepsField.getNode().asArray();
            if (!isEmpty(nestedStepsArray)) {
              collectParallelGroupRollbackNodes(nestedStepsArray, parallelNodes,
                  groupExpression + "." + YAMLFieldNameConstants.STEPS, hasStrategy || ancestorHasStrategy);
            }
          }
        }
      }
    }
  }

  public PlanNode createOuterPerGroupWrapperPlanNode(String uuid, RollbackNode node) {
    return PlanNode.builder()
        .uuid(uuid)
        .name(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_NAME)
        .identifier(NGCommonUtilPlanCreationConstants.STEP_GROUP_ROLLBACK_IDENTIFIER + "_expr_" + node.getNodeId())
        .stepType(StepGroupRollbackChainStep.STEP_TYPE)
        .stepParameters(RollbackOptionalChildChainStepParameters.builder().childNode(node).build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .skipExpressionChain(true)
        .build();
  }

  public PlanNode createParallelNodeForOuterStepGroupRollback(String uuid, List<String> childIds) {
    return PlanNode.builder()
        .uuid(uuid)
        .name(YAMLFieldNameConstants.PARALLEL)
        .identifier(YAMLFieldNameConstants.PARALLEL + uuid)
        .stepType(NGForkStep.STEP_TYPE)
        .stepParameters(ForkStepParameters.builder().parallelNodeIds(childIds).build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(true)
        .build();
  }

  public static PlanNode createRollbackStepsWrapperNode(String uuid, String initNodeUuid) {
    return PlanNode.builder()
        .uuid(uuid)
        .name(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)
        .identifier(YAMLFieldNameConstants.ROLLBACK_STEPS)
        .stepType(RollbackStepsStep.STEP_TYPE)
        .stepParameters(
            NGSectionStepParameters.builder().childNodeId(initNodeUuid).logMessage("Execution Rollback").build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  public static void hideInitForStageRollback(
      ExecutionMode executionMode, PlanCreationResponse targetResponse, PlanCreationResponse initResponse) {
    boolean isPipelineRollback = ExecutionModeUtils.isRollbackMode(executionMode);
    if (!isPipelineRollback && initResponse.getPlanNode() != null) {
      PlanNode initNode = initResponse.getPlanNode();
      PlanNode hiddenInit = initNode.toBuilder().skipGraphType(SkipType.SKIP_NODE).build();
      targetResponse.addNode(hiddenInit);
    }
  }
}
