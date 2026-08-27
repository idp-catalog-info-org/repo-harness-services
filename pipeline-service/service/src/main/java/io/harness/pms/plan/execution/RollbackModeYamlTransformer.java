/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.plancreator.pipelinerollback.PipelineRollbackStageHelper.PIPELINE_ROLLBACK_STAGE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
public class RollbackModeYamlTransformer {
  NodeExecutionService nodeExecutionService;
  private PmsFeatureFlagService featureFlagService;
  private DynamicExecutionService dynamicExecutionService;

  String transformProcessedYaml(String accountId, String processedYaml, ExecutionMode executionMode,
      String originalPlanExecutionId, List<String> stageNodeExecutionIds, String harnessVersion) {
    return transformProcessedYaml(
        accountId, processedYaml, executionMode, originalPlanExecutionId, stageNodeExecutionIds, harnessVersion, false);
  }

  String transformProcessedYaml(String accountId, String processedYaml, ExecutionMode executionMode,
      String originalPlanExecutionId, List<String> stageNodeExecutionIds, String harnessVersion, boolean enableDAG) {
    switch (executionMode) {
      case PIPELINE_ROLLBACK:
        return transformProcessedYamlForPipelineRollbackMode(processedYaml, originalPlanExecutionId, harnessVersion);
      case POST_EXECUTION_ROLLBACK:
        // Forward harnessVersion so that V1 (Unified) pipelines use the V1-aware filter (id, parallel.stages, group).
        // V0 pipelines continue to flow through the same legacy code path as before.
        return transformProcessedYamlForPostExecutionRollbackMode(
            accountId, processedYaml, originalPlanExecutionId, stageNodeExecutionIds, harnessVersion, enableDAG);
      default:
        throw new InvalidRequestException(String.format("Unsupported Execution Mode %s in RollbackModeExecutionHelper "
                + "while transforming plan for execution with id %s",
            executionMode.name(), originalPlanExecutionId));
    }
  }

  /**
   * This is to reverse the stages in the processed yaml, and remove stages that were not run in the original execution
   * Original->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   *  - stage:
   *       identifier: s2
   *  - stage:
   *       identifier: s3
   * Lets say s3 was not run.
   * Transformed->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s2
   *   - stage:
   *       identifier: s1
   *
   * For DAG pipelines (enableDAG=true), the dependency graph is reversed:
   * Original: S3 depends_on [S1, S2] (S1, S2 execute first, then S3)
   * Rollback: S1 depends_on [S3], S2 depends_on [S3] (S3 rolls back first, then S1, S2)
   */
  String transformProcessedYamlForPipelineRollbackMode(
      String processedYaml, String originalPlanExecutionId, String harnessVersion) {
    List<String> executedStages = nodeExecutionService.getStageDetailFromPlanExecutionIdV2(originalPlanExecutionId)
                                      .stream()
                                      .filter(info -> !info.getName().equals(PIPELINE_ROLLBACK_STAGE_NAME))
                                      .map(info -> info.getIdentifier())
                                      .collect(Collectors.toList());

    // DAG pipelines need special handling - reverse the dependency graph instead of just reversing stage order
    if (isDagPipeline(processedYaml)) {
      return filterProcessedYamlForDagRollback(processedYaml, executedStages);
    }

    // Non-DAG pipeline - use standard reverse order transformation
    return filterProcessedYamlWithRequiredStageIdentifiers(
        originalPlanExecutionId, processedYaml, executedStages, harnessVersion);
  }

  /**
   * This is to reverse the stages in the processed yaml
   * Original->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   *  - stage:
   *       identifier: s2
   * Transformed->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s2
   *   - stage:
   *       identifier: s1
   *
   * If stageNodeExecutionIds contains one element, and it corresponds to the stage s1, then we will get->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   *
   * Backward-compat overload — defaults to V0 semantics. Existing V0 callers and tests continue to use this.
   * V1 (Unified) flow is routed through the 5-arg overload below from {@link #transformProcessedYaml}.
   */
  String transformProcessedYamlForPostExecutionRollbackMode(
      String accountId, String processedYaml, String originalPlanExecutionId, List<String> stageNodeExecutionIds) {
    return transformProcessedYamlForPostExecutionRollbackMode(
        accountId, processedYaml, originalPlanExecutionId, stageNodeExecutionIds, HarnessYamlVersion.V0, false);
  }

  /**
   * Version-aware overload introduced for V1 (Unified) Post Prod Rollback support.
   * Behaviour for V0 is identical to the legacy path; V1 takes the version-aware filter that understands
   * stage `id`, `parallel: { stages: [...] }` and `group: { stages: [...] }`.
   */
  String transformProcessedYamlForPostExecutionRollbackMode(String accountId, String processedYaml,
      String originalPlanExecutionId, List<String> stageNodeExecutionIds, String harnessVersion) {
    return transformProcessedYamlForPostExecutionRollbackMode(
        accountId, processedYaml, originalPlanExecutionId, stageNodeExecutionIds, harnessVersion, false);
  }

  String transformProcessedYamlForPostExecutionRollbackMode(String accountId, String processedYaml,
      String originalPlanExecutionId, List<String> stageNodeExecutionIds, String harnessVersion, boolean enableDAG) {
    List<String> executedStages = new ArrayList<>();
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchStageExecutionsWithProjection(originalPlanExecutionId,
            Sets.newHashSet(NodeExecutionKeys.identifier, NodeExecutionKeys.status, NodeExecutionKeys.stepType));
    nodeExecutions.forEach(nodeExecution -> {
      if (null != nodeExecution.getUuid() && stageNodeExecutionIds.contains(nodeExecution.getUuid())
          && !StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
        throw new InvalidRequestException(
            String.format("Stage plan execution [%s] is still in Progress. Wait for Node Execution [%s] to complete.",
                originalPlanExecutionId, nodeExecution.getIdentifier()));
      }
      if (StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
        executedStages.add(nodeExecution.getIdentifier());
      } else if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY
          && nodeExecution.getStatus() == Status.RUNNING) {
        executedStages.add(nodeExecution.getIdentifier());
      }
    });
    if (isDagPostExecutionRollbackEnabled(accountId, enableDAG, processedYaml)) {
      return filterProcessedYamlForDagRollback(processedYaml, executedStages);
    }
    return filterProcessedYaml(accountId, processedYaml, executedStages, harnessVersion);
  }

  /**
   * Post-prod DAG YAML transform requires both pipeline {@code enableDAG} and
   * {@link FeatureName#PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION}, plus {@code depends_on} in the processed YAML.
   */
  boolean isDagPostExecutionRollbackEnabled(String accountId, boolean enableDAG, String processedYaml) {
    return enableDAG && featureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)
        && isDagPipeline(processedYaml);
  }

  /**
   * Backward-compat overload — defaults to V0. Used by tests and any legacy caller.
   */
  String filterProcessedYaml(String accountId, String processedYaml, List<String> executedStageIds) {
    return filterProcessedYaml(accountId, processedYaml, executedStageIds, HarnessYamlVersion.V0);
  }

  /**
   * Version-aware filter for POST_EXECUTION_ROLLBACK. For V0 this preserves the existing dispatcher;
   * for V1 it routes through V1-aware helpers (id-based stage detection, V1 parallel.stages, V1 group).
   */
  String filterProcessedYaml(
      String accountId, String processedYaml, List<String> executedStageIds, String harnessVersion) {
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(processedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ObjectNode pipelineInnerNode = (ObjectNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE);
    ArrayNode stagesList = (ArrayNode) pipelineInnerNode.get(YAMLFieldNameConstants.STAGES);
    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    int numStages = stagesList.size();
    for (int i = numStages - 1; i >= 0; i--) {
      filterProcessedYamlInternal(executedStageIds, stagesList, i, reversedStages, true, harnessVersion);
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }

  /**
   * Backward-compat overload — defaults to V0.
   */
  private void filterProcessedYamlInternal(List<String> executedStageIds, ArrayNode stagesList, int i,
      ArrayNode reversedStages, boolean reverseStagesOrderInPPRollback) {
    filterProcessedYamlInternal(
        executedStageIds, stagesList, i, reversedStages, reverseStagesOrderInPPRollback, HarnessYamlVersion.V0);
  }

  /**
   * Dispatcher for one entry of pipeline.stages in POST_EXECUTION_ROLLBACK. V1 (Unified) adds two extra cases:
   *  - V1 stage-level group ({@code group: { stages: [...] }})
   *  - V1 stage-level parallel ({@code parallel: { stages: [...] }}) — handled inside the parallel helper.
   */
  private void filterProcessedYamlInternal(List<String> executedStageIds, ArrayNode stagesList, int i,
      ArrayNode reversedStages, boolean reverseStagesOrderInPPRollback, String harnessVersion) {
    JsonNode currentNode = stagesList.get(i);
    if (null != currentNode && currentNode.get(YAMLFieldNameConstants.INSERT) != null) {
      // Pre-existing INSERT (V0 template-injected stages). Forward harnessVersion so V1-style children inside
      // INSERT (group, V1 stages) are also handled correctly when V1 + INSERT is used.
      handleInjectStages(currentNode, executedStageIds, reversedStages, harnessVersion, reverseStagesOrderInPPRollback);
    } else if (HarnessYamlVersion.isV1(harnessVersion) && currentNode.get(YAMLFieldNameConstants.GROUP) != null) {
      // V1 stage-level group. Prune-style: keep the group wrapper with only the executed children inside.
      handleGroupStagesForPostExecutionRollback(
          currentNode, executedStageIds, reversedStages, harnessVersion, reverseStagesOrderInPPRollback);
    } else if (currentNode.get(YAMLFieldNameConstants.PARALLEL) == null) {
      // Serial stage — handleSerialStage already supports both V0 (`stage.identifier`) and V1 (`id`).
      handleSerialStage(currentNode, executedStageIds, reversedStages);
    } else {
      // V0 parallel array OR V1 `parallel: { stages: [...] }`. The version-aware overload picks the right shape.
      handleParallelStagesForPostExecutionRollback(
          currentNode, executedStageIds, reversedStages, reverseStagesOrderInPPRollback, harnessVersion);
    }
  }

  String filterProcessedYamlWithRequiredStageIdentifiers(
      String planExecutionId, String processedYaml, List<String> requiredStageIds, String harnessVersion) {
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(processedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ObjectNode pipelineInnerNode = (ObjectNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE);
    ArrayNode stagesList = (ArrayNode) pipelineInnerNode.get(YAMLFieldNameConstants.STAGES);
    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    int numStages = stagesList.size();
    for (int i = numStages - 1; i >= 0; i--) {
      JsonNode currentNode = stagesList.get(i);
      if (null != currentNode && currentNode.get(YAMLFieldNameConstants.INSERT) != null) {
        // reverseStagesOrderInPPRollback is passed as true in this function, because in pipeline rollback, we always
        // reverse the order while filtering processed stages.
        handleInjectStages(currentNode, requiredStageIds, reversedStages, harnessVersion, true);
      } else if (currentNode.has(YAMLFieldNameConstants.STAGE)
          && currentNode.get(YAMLFieldNameConstants.STAGE).has(YAMLFieldNameConstants.TYPE)
          && StepSpecTypeConstants.DYNAMIC_STAGE.equals(
              currentNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.TYPE).asText())) {
        handleDynamicStages(planExecutionId, currentNode, requiredStageIds, reversedStages, harnessVersion, true);
      } else if (HarnessYamlVersion.isV1(harnessVersion) && currentNode.has(YAMLFieldNameConstants.TYPE)
          && YAMLFieldNameConstants.DYNAMIC_STAGE_V1.equals(currentNode.get(YAMLFieldNameConstants.TYPE).asText())) {
        handleDynamicStagesV1(planExecutionId, currentNode, requiredStageIds, reversedStages, harnessVersion, true);
      } else if (HarnessYamlVersion.isV1(harnessVersion) && currentNode.get(YAMLFieldNameConstants.GROUP) != null) {
        handleGroupStages(currentNode, requiredStageIds, reversedStages, harnessVersion, true);
      } else if (currentNode.get(YAMLFieldNameConstants.PARALLEL) == null) {
        handleSerialStage(currentNode, requiredStageIds, reversedStages);
      } else {
        handleParallelStages(currentNode, requiredStageIds, reversedStages, harnessVersion, true);
      }
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }

  void handleSerialStage(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages) {
    String stageId;
    if (currentNode.has(YAMLFieldNameConstants.STAGE)
        && currentNode.get(YAMLFieldNameConstants.STAGE).has(YAMLFieldNameConstants.IDENTIFIER)) {
      stageId = currentNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
    } else {
      stageId = currentNode.get(YAMLFieldNameConstants.ID).asText();
    }
    if (executedStages.contains(stageId)) {
      reversedStages.add(currentNode);
    }
  }

  /**
   * If child is Insert, then handleInjectStagesInsideParallel will reverse the stages inside Insert
   * and it will return the stageIds of all children of Insert, if any stageId is executed
   * then we will add the parallel node to reversedStages arrayNode and we will break the loop
   * as the whole parallel node is already added and no need to iterate over its remaining children.
   */
  List<String> handleParallelStages(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages,
      String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    ArrayNode parallelStages = null;
    if (HarnessYamlVersion.isV1(harnessVersion)) {
      ObjectNode parallelNode = (ObjectNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
      parallelStages = (ArrayNode) parallelNode.get(YAMLFieldNameConstants.STAGES);
    } else {
      parallelStages = (ArrayNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
    }
    int numParallelStages = parallelStages.size();
    List<String> stageIdsInsideParallel = new ArrayList<>();
    for (int i = 0; i < numParallelStages; i++) {
      JsonNode currParallelStage = parallelStages.get(i);
      String stageId;
      if (HarnessYamlVersion.isV1(harnessVersion)) {
        if (currParallelStage.get(YAMLFieldNameConstants.GROUP) != null) {
          handleGroupStages(
              currParallelStage, executedStages, reversedStages, harnessVersion, reverseStagesOrderInPPRollback);
        } else {
          stageId = currParallelStage.get(YAMLFieldNameConstants.ID).asText();
          if (currentStageIsExecuted(currentNode, executedStages, reversedStages, stageId)) {
            break;
          }
        }
      } else if (currParallelStage.get(YAMLFieldNameConstants.INSERT) != null) {
        List<String> stageInsideInsert = handleInjectStagesInsideParallel(
            currParallelStage, executedStages, harnessVersion, reverseStagesOrderInPPRollback);
        if (!stageInsideInsert.isEmpty()) {
          stageIdsInsideParallel.addAll(stageInsideInsert);
        }
        boolean executedStagesContainsAnyInsertStage = executedStages.stream().anyMatch(stageInsideInsert::contains);
        if (executedStagesContainsAnyInsertStage) {
          reversedStages.add(currentNode);
          break;
        }
      } else {
        stageId = currParallelStage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
        stageIdsInsideParallel.add(stageId);
        if (currentStageIsExecuted(currentNode, executedStages, reversedStages, stageId)) {
          break;
        }
      }
    }
    return stageIdsInsideParallel;
  }

  private static boolean currentStageIsExecuted(
      JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages, String stageId) {
    if (executedStages.contains(stageId)) {
      // adding currentNode because we need to add the parallel block fully
      reversedStages.add(currentNode);
      return true;
    }
    return false;
  }

  void handleDynamicStages(String planExecutionId, JsonNode currentNode, List<String> executedStages,
      ArrayNode reversedStages, String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    Optional<DynamicExecutionInstanceResponseDTO> dynamicExecutionInstanceResponseDTO =
        dynamicExecutionService.getByPlanExecutionIdAndIdentifier(planExecutionId,
            currentNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText());
    if (dynamicExecutionInstanceResponseDTO.isEmpty()) {
      // If instance was not found means the dynamic stage was not executed.
      return;
    }

    String yaml = dynamicExecutionInstanceResponseDTO.get().getProcessedYaml();
    YamlField yamlField = YamlUtils.readYamlTree(yaml);
    JsonNode stagesNode = yamlField.getNode()
                              .getField(YAMLFieldNameConstants.PIPELINE)
                              .getNode()
                              .getField(YAMLFieldNameConstants.STAGES)
                              .getNode()
                              .getCurrJsonNode();

    ArrayNode stagesArrayNode = (ArrayNode) stagesNode;
    ArrayNode reversedWrappedStages = stagesArrayNode.deepCopy().removeAll();
    int stagesCount = stagesArrayNode.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = stagesCount - 1; i >= 0; i--) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, stagesArrayNode, i, reversedWrappedStages);
      }
    } else {
      for (int i = 0; i <= stagesCount - 1; i++) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, stagesArrayNode, i, reversedWrappedStages);
      }
    }
    ((ObjectNode) currentNode.get(YAMLFieldNameConstants.STAGE))
        .set(YAMLFieldNameConstants.STAGES, reversedWrappedStages);
    reversedStages.add(currentNode);
  }

  void handleDynamicStagesV1(String planExecutionId, JsonNode currentNode, List<String> executedStages,
      ArrayNode reversedStages, String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    String stageId = currentNode.get(YAMLFieldNameConstants.ID).asText();
    Optional<DynamicExecutionInstanceResponseDTO> dynamicExecutionInstanceResponseDTO =
        dynamicExecutionService.getByPlanExecutionIdAndIdentifier(planExecutionId, stageId);
    if (dynamicExecutionInstanceResponseDTO.isEmpty()) {
      log.warn(
          "Dropping dynamic stage '{}' from rollback YAML: no DynamicExecutionInstance found for planExecutionId={}",
          stageId, planExecutionId);
      return;
    }

    String yaml = dynamicExecutionInstanceResponseDTO.get().getProcessedYaml();
    YamlField yamlField = YamlUtils.readYamlTree(yaml);
    JsonNode stagesNode = yamlField.getNode()
                              .getField(YAMLFieldNameConstants.PIPELINE)
                              .getNode()
                              .getField(YAMLFieldNameConstants.STAGES)
                              .getNode()
                              .getCurrJsonNode();

    ArrayNode stagesArrayNode = (ArrayNode) stagesNode;
    ArrayNode reversedWrappedStages = stagesArrayNode.deepCopy().removeAll();
    int stagesCount = stagesArrayNode.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = stagesCount - 1; i >= 0; i--) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, stagesArrayNode, i, reversedWrappedStages);
      }
    } else {
      for (int i = 0; i <= stagesCount - 1; i++) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, stagesArrayNode, i, reversedWrappedStages);
      }
    }
    ((ObjectNode) currentNode).set(YAMLFieldNameConstants.STAGES, reversedWrappedStages);
    reversedStages.add(currentNode);
  }

  void handleInjectStages(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages,
      String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    JsonNode injectNode = currentNode.get(YAMLFieldNameConstants.INSERT);
    JsonNode stagesNode = injectNode.get(YAMLFieldNameConstants.STAGES);
    if (stagesNode == null || !stagesNode.isArray()) {
      return;
    }
    ArrayNode injectStages = (ArrayNode) stagesNode;
    ArrayNode reversedInjectStages = injectStages.deepCopy().removeAll();
    int numInjectStages = injectStages.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = numInjectStages - 1; i >= 0; i--) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, injectStages, i, reversedInjectStages);
      }
    } else {
      for (int i = 0; i <= numInjectStages - 1; i++) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, injectStages, i, reversedInjectStages);
      }
    }
    ((ObjectNode) injectNode).set(YAMLFieldNameConstants.STAGES, reversedInjectStages);
    reversedStages.add(currentNode);
  }

  void handleGroupStages(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages,
      String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    JsonNode groupNode = currentNode.get(YAMLFieldNameConstants.GROUP);
    JsonNode stagesNode = groupNode.get(YAMLFieldNameConstants.STAGES);
    if (stagesNode == null || !stagesNode.isArray()) {
      return;
    }
    ArrayNode groupStages = (ArrayNode) stagesNode;
    ArrayNode reversedGroupStages = groupStages.deepCopy().removeAll();
    int numGroupStages = groupStages.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = numGroupStages - 1; i >= 0; i--) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, groupStages, i, reversedGroupStages);
      }
    } else {
      for (int i = 0; i <= numGroupStages - 1; i++) {
        handleWrappedStagesInternal(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, groupStages, i, reversedGroupStages);
      }
    }
    ((ObjectNode) groupNode).set(YAMLFieldNameConstants.STAGES, reversedGroupStages);
    reversedStages.add(currentNode);
  }

  private void handleWrappedStagesInternal(List<String> executedStages, String harnessVersion,
      boolean reverseStagesOrderInPPRollback, ArrayNode injectStages, int i, ArrayNode reversedInjectStages) {
    JsonNode currInjectStage = injectStages.get(i);
    String stageId;
    if (currInjectStage.has(YAMLFieldNameConstants.PARALLEL)) {
      handleParallelStages(
          currInjectStage, executedStages, reversedInjectStages, harnessVersion, reverseStagesOrderInPPRollback);
    } else if (HarnessYamlVersion.isV1(harnessVersion) && currInjectStage.get(YAMLFieldNameConstants.GROUP) != null) {
      handleGroupStages(
          currInjectStage, executedStages, reversedInjectStages, harnessVersion, reverseStagesOrderInPPRollback);
    } else {
      if (currInjectStage.has(YAMLFieldNameConstants.STAGE)
          && currInjectStage.get(YAMLFieldNameConstants.STAGE).has(YAMLFieldNameConstants.IDENTIFIER)) {
        stageId = currInjectStage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
      } else {
        stageId = currInjectStage.get(YAMLFieldNameConstants.ID).asText();
      }
      if (executedStages.contains(stageId)) {
        reversedInjectStages.add(currInjectStage);
      }
    }
  }

  List<String> handleInjectStagesInsideParallel(
      JsonNode currentNode, List<String> executedStages, boolean reverseStagesOrderInPPRollback) {
    return handleInjectStagesInsideParallel(
        currentNode, executedStages, HarnessYamlVersion.V0, reverseStagesOrderInPPRollback);
  }

  /**
   *
   * @param currentNode - It is the Insert Node
   * @param executedStages
   * @param harnessVersion
   * @param reverseStagesOrderInPPRollback
   * @return - List of stageIds (nested stages as well) of all children inside Insert
   */
  List<String> handleInjectStagesInsideParallel(JsonNode currentNode, List<String> executedStages,
      String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    List<String> stageIdsInsideInsert = new ArrayList<>();
    JsonNode injectNode = currentNode.get(YAMLFieldNameConstants.INSERT);
    JsonNode stagesNode = injectNode.get(YAMLFieldNameConstants.STAGES);
    if (stagesNode == null || !stagesNode.isArray()) {
      return new ArrayList<>();
    }
    ArrayNode injectStages = (ArrayNode) stagesNode;
    ArrayNode reversedInjectStages = injectStages.deepCopy().removeAll();
    int numInjectStages = injectStages.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = numInjectStages - 1; i >= 0; i--) {
        JsonNode currInjectStage = injectStages.get(i);
        handleInsertStagesInsideParallelInternal(executedStages, harnessVersion, reverseStagesOrderInPPRollback,
            currInjectStage, reversedInjectStages, stageIdsInsideInsert);
      }
    } else {
      for (int i = 0; i <= numInjectStages - 1; i++) {
        JsonNode currInjectStage = injectStages.get(i);
        handleInsertStagesInsideParallelInternal(executedStages, harnessVersion, reverseStagesOrderInPPRollback,
            currInjectStage, reversedInjectStages, stageIdsInsideInsert);
      }
    }
    ((ObjectNode) injectNode).set(YAMLFieldNameConstants.STAGES, reversedInjectStages);
    return stageIdsInsideInsert;
  }

  /**
   * Handling for each child of Insert, if child is stage and it is executedStages,
   * then add that into reversedInjectStages arrayNode
   * If child is parallel then it is handled in handleParallelStages.
   */
  private void handleInsertStagesInsideParallelInternal(List<String> executedStages, String harnessVersion,
      boolean reverseStagesOrderInPPRollback, JsonNode currInjectStage, ArrayNode reversedInjectStages,
      List<String> stageIdsInsideInsert) {
    if (currInjectStage.has(YAMLFieldNameConstants.PARALLEL)) {
      List<String> stageIdsInsideParallel = handleParallelStages(
          currInjectStage, executedStages, reversedInjectStages, harnessVersion, reverseStagesOrderInPPRollback);
      if (!stageIdsInsideParallel.isEmpty()) {
        stageIdsInsideInsert.addAll(stageIdsInsideParallel);
      }
    } else {
      String stageId =
          currInjectStage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
      stageIdsInsideInsert.add(stageId);
      if (executedStages.contains(stageId)) {
        reversedInjectStages.add(currInjectStage);
      }
    }
  }

  /**
   * We iterate over each child of parallel, if the child is stage and it is executed then we add it to
   * reversedStages else we skip it, If the child is Insert then handleInjectStagesInsideParallel will modify
   * the ( insert -> stages ) to contain only the executed stages and it will return all the children of insert
   * if any stageId is in executedStages then we add the current node in the reversedStages.
   *
   * Backward-compat overload — defaults to V0 semantics (parallel as ArrayNode of `stage:` wrappers).
   * Existing tests and any legacy callers continue to use this; matches behaviour prior to V1 PPR support.
   */
  void handleParallelStagesForPostExecutionRollback(JsonNode currentNode, List<String> executedStages,
      ArrayNode reversedStages, boolean reverseStagesOrderInPPRollback) {
    handleParallelStagesForPostExecutionRollback(
        currentNode, executedStages, reversedStages, reverseStagesOrderInPPRollback, HarnessYamlVersion.V0);
  }

  /**
   * Version-aware POST_EXECUTION_ROLLBACK parallel handler.
   * Prune semantics (unlike PIPELINE_ROLLBACK's whole-block keep): keeps only executed children inside the parallel
   * wrapper. Reads V0 shape (`parallel: [...]`) and V1 shape (`parallel: { stages: [...] }`).
   */
  void handleParallelStagesForPostExecutionRollback(JsonNode currentNode, List<String> executedStages,
      ArrayNode reversedStages, boolean reverseStagesOrderInPPRollback, String harnessVersion) {
    boolean isV1 = HarnessYamlVersion.isV1(harnessVersion);
    ArrayNode parallelStages;
    if (isV1) {
      ObjectNode parallelNode = (ObjectNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
      JsonNode stagesNode = parallelNode == null ? null : parallelNode.get(YAMLFieldNameConstants.STAGES);
      if (stagesNode == null || !stagesNode.isArray()) {
        return;
      }
      parallelStages = (ArrayNode) stagesNode;
    } else {
      parallelStages = (ArrayNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
    }
    ArrayNode parallelExecutedStages = parallelStages.deepCopy().removeAll();
    int numParallelStages = parallelStages.size();
    for (int i = 0; i < numParallelStages; i++) {
      JsonNode currParallelStage = parallelStages.get(i);
      if (!isV1 && currParallelStage.has(YAMLFieldNameConstants.INSERT)) {
        // V0-only: insert (template-injected stages) inside parallel.
        List<String> stageIdsInsideParallel =
            handleInjectStagesInsideParallel(currParallelStage, executedStages, reverseStagesOrderInPPRollback);
        boolean executedStagesContainsAnyInsertStage =
            executedStages.stream().anyMatch(stageIdsInsideParallel::contains);
        if (executedStagesContainsAnyInsertStage) {
          reversedStages.add(currentNode);
          break;
        }
      } else if (isV1 && currParallelStage.get(YAMLFieldNameConstants.GROUP) != null) {
        // V1 group inside V1 parallel — recurse with prune semantics into a temp accumulator,
        // then keep the original group node only if at least one of its children was executed.
        ArrayNode tempAccumulator = parallelStages.deepCopy().removeAll();
        handleGroupStagesForPostExecutionRollback(
            currParallelStage, executedStages, tempAccumulator, harnessVersion, reverseStagesOrderInPPRollback);
        if (tempAccumulator.size() > 0) {
          parallelExecutedStages.add(tempAccumulator.get(0));
        }
      } else {
        // Plain stage inside parallel. V0 uses `stage.identifier`; V1 uses `id`.
        String stageId = extractStageIdentifier(currParallelStage);
        if (stageId != null && executedStages.contains(stageId)) {
          parallelExecutedStages.add(currParallelStage);
        }
      }
    }
    if (!parallelExecutedStages.isEmpty()) {
      ObjectNode newParallelNodeNode = (ObjectNode) currentNode;
      if (isV1) {
        // Preserve V1 shape: parallel: { stages: [...] }.
        ObjectNode parallelInner = (ObjectNode) newParallelNodeNode.get(YAMLFieldNameConstants.PARALLEL);
        parallelInner.set(YAMLFieldNameConstants.STAGES, parallelExecutedStages);
      } else {
        newParallelNodeNode.set(YAMLFieldNameConstants.PARALLEL, parallelExecutedStages);
      }
      reversedStages.add(newParallelNodeNode);
    }
  }

  /**
   * V1 stage-level group handler for POST_EXECUTION_ROLLBACK. Prune semantics: keep the group wrapper only when
   * at least one stage inside was executed, and keep only those executed stages within the wrapper.
   * Mirrors the prune-not-merge behaviour of {@link #handleParallelStagesForPostExecutionRollback}, distinct from
   * {@link #handleGroupStages} which is used by PIPELINE_ROLLBACK.
   */
  void handleGroupStagesForPostExecutionRollback(JsonNode currentNode, List<String> executedStages,
      ArrayNode reversedStages, String harnessVersion, boolean reverseStagesOrderInPPRollback) {
    JsonNode groupNode = currentNode.get(YAMLFieldNameConstants.GROUP);
    JsonNode stagesNode = groupNode == null ? null : groupNode.get(YAMLFieldNameConstants.STAGES);
    if (stagesNode == null || !stagesNode.isArray()) {
      return;
    }
    ArrayNode groupStages = (ArrayNode) stagesNode;
    ArrayNode prunedGroupStages = groupStages.deepCopy().removeAll();
    int numGroupStages = groupStages.size();
    if (reverseStagesOrderInPPRollback) {
      for (int i = numGroupStages - 1; i >= 0; i--) {
        addExecutedChildToGroup(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, groupStages.get(i), prunedGroupStages);
      }
    } else {
      for (int i = 0; i < numGroupStages; i++) {
        addExecutedChildToGroup(
            executedStages, harnessVersion, reverseStagesOrderInPPRollback, groupStages.get(i), prunedGroupStages);
      }
    }
    if (prunedGroupStages.size() > 0) {
      ((ObjectNode) groupNode).set(YAMLFieldNameConstants.STAGES, prunedGroupStages);
      reversedStages.add(currentNode);
    }
  }

  /**
   * Helper for {@link #handleGroupStagesForPostExecutionRollback}: keeps a child of a group only if it
   * (or any nested executed stage) was executed in the original run. Supports nested parallel/group within
   * the group as well, by reusing the version-aware POST_EXECUTION_ROLLBACK helpers.
   */
  private void addExecutedChildToGroup(List<String> executedStages, String harnessVersion,
      boolean reverseStagesOrderInPPRollback, JsonNode currChild, ArrayNode prunedChildren) {
    if (currChild.has(YAMLFieldNameConstants.PARALLEL)) {
      ArrayNode tempAccumulator = prunedChildren.deepCopy().removeAll();
      handleParallelStagesForPostExecutionRollback(
          currChild, executedStages, tempAccumulator, reverseStagesOrderInPPRollback, harnessVersion);
      if (tempAccumulator.size() > 0) {
        prunedChildren.add(tempAccumulator.get(0));
      }
    } else if (HarnessYamlVersion.isV1(harnessVersion) && currChild.get(YAMLFieldNameConstants.GROUP) != null) {
      ArrayNode tempAccumulator = prunedChildren.deepCopy().removeAll();
      handleGroupStagesForPostExecutionRollback(
          currChild, executedStages, tempAccumulator, harnessVersion, reverseStagesOrderInPPRollback);
      if (tempAccumulator.size() > 0) {
        prunedChildren.add(tempAccumulator.get(0));
      }
    } else {
      String stageId = extractStageIdentifier(currChild);
      if (stageId != null && executedStages.contains(stageId)) {
        prunedChildren.add(currChild);
      }
    }
  }

  /**
   * Returns the stage identifier of a YAML node that is a serial stage entry — supports V0 (`stage.identifier`)
   * and V1 (`id`). Returns {@code null} if the node is some other shape (e.g. parallel/group/insert wrapper).
   */
  private static String extractStageIdentifier(JsonNode currNode) {
    if (currNode.has(YAMLFieldNameConstants.STAGE)
        && currNode.get(YAMLFieldNameConstants.STAGE).has(YAMLFieldNameConstants.IDENTIFIER)) {
      return currNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
    }
    if (currNode.has(YAMLFieldNameConstants.ID)) {
      return currNode.get(YAMLFieldNameConstants.ID).asText();
    }
    return null;
  }

  // ==================== DAG Pipeline Rollback Support ====================

  /**
   * Transforms DAG pipeline stages for rollback by reversing the dependency graph.
   *
   * For DAG pipelines, rollback order is determined by reversing dependencies:
   * - Original: S3 depends_on [S1, S2] means S1, S2 execute first, then S3
   * - Rollback: S1 depends_on [S3], S2 depends_on [S3] means S3 rolls back first, then S1, S2
   *
   * The DAG engine determines execution order from the depends_on graph,
   * so the order of stages in the YAML array doesn't matter.
   */
  ArrayNode transformDagStagesForRollback(ArrayNode stagesList, List<String> executedStages) {
    // Step 1: Build identifier to stage node mapping and extract original dependency graph
    Set<String> executedStagesSet = new HashSet<>(executedStages);
    Map<String, JsonNode> identifierToStageNode = new HashMap<>();
    Map<String, List<String>> originalDependencyGraph = new HashMap<>();

    for (int i = 0; i < stagesList.size(); i++) {
      JsonNode currentNode = stagesList.get(i);
      if (currentNode.has(YAMLFieldNameConstants.STAGE)) {
        JsonNode stageNode = currentNode.get(YAMLFieldNameConstants.STAGE);
        String identifier = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();

        // Only include executed stages in rollback
        if (executedStagesSet.contains(identifier)) {
          identifierToStageNode.put(identifier, currentNode);

          // Extract depends_on relationships
          List<String> dependencies = new ArrayList<>();
          if (stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)) {
            JsonNode dependsOnNode = stageNode.get(YAMLFieldNameConstants.DEPENDS_ON);
            if (dependsOnNode.isArray()) {
              for (JsonNode dep : dependsOnNode) {
                String depIdentifier = dep.asText();
                // Only include dependencies that were also executed
                if (executedStagesSet.contains(depIdentifier)) {
                  dependencies.add(depIdentifier);
                }
              }
            }
          }
          originalDependencyGraph.put(identifier, dependencies);
        }
      }
    }

    // Step 2: Compute reverse dependency graph for rollback
    // Original: A depends_on [B, C] means B, C must complete before A starts
    // Reverse: B depends_on [A], C depends_on [A] means A must rollback before B, C rollback
    Map<String, List<String>> reverseDependencyGraph =
        DependencyUtils.computeReverseDependencyGraph(originalDependencyGraph);

    // Step 3: Build the transformed stages array with reversed dependencies
    // Order in the array doesn't matter — the DAG engine uses depends_on to determine execution order
    ArrayNode transformedStages = stagesList.deepCopy().removeAll();

    for (Map.Entry<String, JsonNode> entry : identifierToStageNode.entrySet()) {
      String stageIdentifier = entry.getKey();
      // Deep copy to avoid modifying original
      ObjectNode stageWrapper = (ObjectNode) entry.getValue().deepCopy();
      ObjectNode stageNode = (ObjectNode) stageWrapper.get(YAMLFieldNameConstants.STAGE);

      // Update depends_on with reversed dependencies
      List<String> newDependencies = reverseDependencyGraph.get(stageIdentifier);
      if (newDependencies != null && !newDependencies.isEmpty()) {
        ArrayNode dependsOnArray = stageNode.putArray(YAMLFieldNameConstants.DEPENDS_ON);
        for (String dep : newDependencies) {
          dependsOnArray.add(dep);
        }
      } else {
        // Remove depends_on if no dependencies in reverse graph (was a leaf node, now a root)
        stageNode.remove(YAMLFieldNameConstants.DEPENDS_ON);
      }

      transformedStages.add(stageWrapper);
    }

    return transformedStages;
  }

  boolean isDagPipeline(String processedYaml) {
    try {
      JsonNode pipelineNode = YamlUtils.readTree(processedYaml).getNode().getCurrJsonNode();
      JsonNode pipelineInnerNode = pipelineNode.get(YAMLFieldNameConstants.PIPELINE);
      if (pipelineInnerNode == null) {
        return false;
      }
      JsonNode stagesList = pipelineInnerNode.get(YAMLFieldNameConstants.STAGES);
      if (stagesList == null || !stagesList.isArray()) {
        return false;
      }
      for (JsonNode stageWrapper : stagesList) {
        JsonNode stageNode = stageWrapper.get(YAMLFieldNameConstants.STAGE);
        if (stageNode != null && stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)) {
          return true;
        }
      }
    } catch (IOException e) {
      log.warn("Error checking if pipeline is DAG", e);
    }
    return false;
  }

  String filterProcessedYamlForDagRollback(String processedYaml, List<String> executedStages) {
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(processedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML for DAG rollback");
    }

    ObjectNode pipelineInnerNode = (ObjectNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE);
    ArrayNode stagesList = (ArrayNode) pipelineInnerNode.get(YAMLFieldNameConstants.STAGES);

    // Transform DAG stages with reversed dependencies
    ArrayNode transformedStages = transformDagStagesForRollback(stagesList, executedStages);

    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, transformedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }
}
