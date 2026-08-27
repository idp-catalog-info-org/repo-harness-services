/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.AbortInfoHelper;
import io.harness.advisers.pipelinerollback.output.OnFailPipelineRollbackOutput;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionHelper;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.merger.yaml.Utils;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.plan.execution.ExecutionSummaryUpdateUtils;
import io.harness.pms.plan.execution.LayoutNodeGraphConstants;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO.GraphLayoutNodeDTOKeys;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.mappers.PipelineSearchExecutionSummaryDTOMapper;
import io.harness.search.service.PipelineSearchService;
import io.harness.utils.PipelineExecutionSummaryEntityUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.execution.ExecutionModeUtils;

import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsExecutionSummaryServiceImpl implements PmsExecutionSummaryService {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanService planService;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject AbortInfoHelper abortInfoHelper;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private ExecutionSummaryUpdateUtils executionSummaryUpdateUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject PipelineSearchService pipelineSearchService;
  @Inject ExecutionRetentionService executionRetentionService;
  @Inject ScopeResolutionHelper scopeResolutionHelper;
  @Inject private ExecutionRetentionIteratorEntityService retentionIteratorEntityService;

  /**
   * Performs the following:
   * 1. Fetches all stage and strategy nodeExecutions from db with identity nodes
   * 2. Iterate over each node Execution
   *    * If node is of type stage then update status and endTs
   *    * if node is of type strategy then add all its children in layoutNodeMap and update the execution summary
   *
   * @param planExecutionId - the execution id for which the identity stages needs to be updated
   * @param update - the reference to update operation
   * @return true if there is an update otherwise returns false
   */
  public boolean updateIdentityStageOrStrategyNodes(String planExecutionId, Update update) {
    boolean updateApplied = false;

    List<NodeExecution> stageOrStrategyNodeExecutions =
        nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId);

    Optional<PipelineExecutionSummaryEntity> entity =
        pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId);
    if (entity.isEmpty()) {
      log.error(String.format("PlanExecutionSummary not found for plan execution id: %s", planExecutionId));
      return false;
    }
    Map<String, GraphLayoutNodeDTO> graphLayoutNode = entity.get().getLayoutNodeMap();
    // This is done inorder to reduce the load while updating stageInfo. Here we will update only the status.
    for (NodeExecution nodeExecution : stageOrStrategyNodeExecutions) {
      // In the case of Post Prod Rollback, only consider original stage node execution
      if (shouldSkip(planExecutionId, nodeExecution)) {
        continue;
      }

      if (nodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY) {
        // This means it is stage nodes
        updateApplied = handleStageIdentityNodes(nodeExecution, update);
        continue;
      } else if (!AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getLevels())) {
        // There is a chance that strategy might belong to step, this check is used to validate if strategy level is
        // actually at stage
        continue;
      }
      if (graphLayoutNode.get(nodeExecution.getNodeId()) == null) {
        continue;
      }

      // Filter child executions for this strategy
      List<NodeExecution> childrenNodeExecution =
          stageOrStrategyNodeExecutions.stream()
              .filter(o -> o.getParentId().equals(nodeExecution.getUuid()))
              .filter(o -> {
                // In V1 rollback, exclude STRATEGY nodes that are incorrectly parented
                // Only include STAGE nodes as legitimate children of a STRATEGY node
                if (HarnessYamlVersion.isV1(NodeExecutionContextUtils.getHarnessYamlVersion(nodeExecution))
                    && ExecutionModeUtils.isRollbackMode(NodeExecutionContextUtils.getExecutionMode(nodeExecution))
                    && nodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE
                    && o.getStepType().getStepCategory() == StepCategory.STRATEGY) {
                  // This is a strategy node incorrectly claiming to be a child - exclude it
                  log.debug("V1 rollback: excluding strategy node {} from children of strategy node {} - strategies "
                          + "should be siblings",
                      o.getUuid(), nodeExecution.getUuid());
                  return false;
                }
                return true;
              })
              .collect(Collectors.toList());

      // Update Max concurrency in graph (consumed by UI) if the type of strategy is not parallelism
      // For parallelism, the maxConcurrency cannot be defined via yaml, so we are ignoring its addition in graph.
      String nodeType = graphLayoutNode.get(nodeExecution.getNodeId()).getNodeType();
      if (nodeType == null) {
        log.warn("NodeType found null for NodeExecution uuid {}", nodeExecution.getUuid());
      }

      if (!StrategyType.PARALLELISM.name().equals(nodeType)) {
        ConcurrentChildInstance concurrentChildInstance =
            nodeExecutionInfoService.fetchConcurrentChildInstance(nodeExecution.getUuid());
        if (concurrentChildInstance != null && !nodeExecution.getExecutableResponses().isEmpty()) {
          update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId()
                  + ".moduleInfo.maxConcurrency.value",
              nodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency());
          updateApplied = true;
        }
      }

      // We need to update the status and StepParameters for strategy node
      updateStatusAndStepParametersInStrategyNode(nodeExecution, update, childrenNodeExecution);

      String stageSetupId = getStageSetupId(childrenNodeExecution, graphLayoutNode, nodeExecution);
      if (stageSetupId == null) {
        continue;
      }
      // This adds the childNodes as children for the strategy node in top level graph.
      addAndUpdateChildNodesForStrategy(
          planExecutionId, update, graphLayoutNode, nodeExecution, childrenNodeExecution, stageSetupId);
    }

    return updateApplied;
  }

  private boolean shouldSkip(String planExecutionId, NodeExecution nodeExecution) {
    if (ExecutionModeUtils.isPostExecutionRollbackMode(NodeExecutionContextUtils.getExecutionMode(nodeExecution))) {
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
          NodeExecutionContextUtils.getAccountId(nodeExecution), planExecutionId,
          PlanExecutionProjectionConstants.fieldsForPostProdRollback);
      boolean readSwitchEnabled = pmsFeatureFlagService.isEnabled(
          nodeExecution.getAccountId(), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
      PlanExecution planExecution = null;
      if (readSwitchEnabled) {
        Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
            planExecutionId, Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
        if (planExecutionOptional.isPresent()) {
          planExecution = planExecutionOptional.get();
        }
      }
      List<PostExecutionRollbackInfo> postExecutionRollbackInfos =
          PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
              planExecutionMetadata, planExecution);
      boolean isCurrentRollbackStage = postExecutionRollbackInfos.get(0).getOriginalStageExecutionId().equals(
          nodeExecution.getOriginalNodeExecutionId());
      boolean isPostExecutionRollbackStage =
          postExecutionRollbackInfos.get(0).getPostExecutionRollbackStageId().equals(nodeExecution.getNodeId());
      if (!isCurrentRollbackStage && !isPostExecutionRollbackStage) {
        return true;
      }
    }
    return false;
  }

  /**
   *
   * This adds the child stage of strategy to the graph if it is not present. It clones the dummy node that we add
   * during plan creation This also adds the generated nodes as children to the strategy nodes.
   * @param planExecutionId
   * @param update
   * @param graphLayoutNode
   * @param nodeExecution
   * @param childrenNodeExecution
   * @param stageSetupId
   */
  private void addAndUpdateChildNodesForStrategy(String planExecutionId, Update update,
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, NodeExecution nodeExecution,
      List<NodeExecution> childrenNodeExecution, String stageSetupId) {
    if (childrenNodeExecution.isEmpty()) {
      return;
    }
    for (NodeExecution stageNodeExecution : childrenNodeExecution) {
      // If the child already exists in graph then ignore.
      if (!alreadyAddedAsChild(graphLayoutNode, nodeExecution.getNodeId(), stageNodeExecution.getUuid())) {
        GraphLayoutNodeDTO graphLayoutNodeDTO = graphLayoutNode.get(stageSetupId);
        if (graphLayoutNodeDTO == null) {
          // Expected when DAG post-prod rollback layout was pruned to the rollback target only.
          log.warn(String.format("[CLONE_GRAPH_NODE] NodeExecutionId: %s", nodeExecution.getNodeId()));
          graphLayoutNode.forEach(
              (k, v) -> log.warn(String.format("[CLONE_GRAPH_NODE] key %s: value %s", k, v.getNodeIdentifier())));
          log.warn(String.format("[CLONE_GRAPH_NODE] Clone GraphLayoutNode with stageSetupId: %s", stageSetupId));
          continue;
        }
        cloneGraphLayoutNodeDTO(
            graphLayoutNodeDTO, stageNodeExecution.getUuid(), update, stageNodeExecution.getNodeId());
      }
    }

    List<String> childrenExecutionIds =
        childrenNodeExecution.stream().map(NodeExecution::getUuid).collect(Collectors.toList());
    boolean removeDummyNode = isRemoveDummyNode(graphLayoutNode, nodeExecution, stageSetupId);
    addChildrenToStrategyNode(
        update, planExecutionId, nodeExecution, stageSetupId, childrenExecutionIds, removeDummyNode);
  }

  private boolean isRemoveDummyNode(
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, NodeExecution nodeExecution, String stageSetupId) {
    if (pmsFeatureFlagService.isEnabled(
            nodeExecution.getAccountId(), FeatureName.PIPE_ENABLE_REDUNDANT_UPDATES_IN_RETRY_EXECUTIONS)) {
      return true;
    }
    boolean removeDummyNode = true;
    if (graphLayoutNode.get(nodeExecution.getNodeId()) != null
        && graphLayoutNode.get(nodeExecution.getNodeId()).getEdgeLayoutList() != null
        && (!graphLayoutNode.get(nodeExecution.getNodeId())
                 .getEdgeLayoutList()
                 .getCurrentNodeChildren()
                 .contains(stageSetupId))) {
      removeDummyNode = false;
    }
    return removeDummyNode;
  }

  @Override
  public void regenerateStageLayoutGraph(
      String planExecutionId, List<NodeExecution> nodeExecutions, PlanExecution planExecution) {
    Update update = new Update();
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
        AmbianceUtils.getAccountId(planExecution.getAmbiance()), planExecutionId,
        PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    List<PostExecutionRollbackInfo> postExecutionRollbackInfos =
        PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
            planExecutionMetadata, planExecution);
    for (NodeExecution nodeExecution : nodeExecutions) {
      executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, postExecutionRollbackInfos);
    }
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  @Override
  public PipelineExecutionSummaryEntity save(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String accountId = pipelineExecutionSummaryEntity.getAccountId();
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_DISABLE_TTL_INCREASE_FOR_PMS_COLLECTIONS)
        && pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL)) {
      int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountId);
      pipelineExecutionSummaryEntity.setValidUntil(
          PipelineRetentionHelper.getValidUntilAsDate(retentionPeriodInMonths));
    }
    if (pipelineExecutionSummaryEntity.getParentUniqueId() == null) {
      log.error("ParentUniqueId is not getting set via Ambiance in save PipelineExecutionSummaryEntity flow");
      Optional<ScopeInfo> scopeInfo =
          scopeResolutionHelper.getScopeInfoOptional(pipelineExecutionSummaryEntity.getAccountId(),
              pipelineExecutionSummaryEntity.getOrgIdentifier(), pipelineExecutionSummaryEntity.getProjectIdentifier());
      scopeInfo.ifPresent(info -> pipelineExecutionSummaryEntity.setParentUniqueId(info.getUniqueId()));
    }
    PipelineExecutionSummaryEntity summaryEntity = pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    pipelineSearchService.save(summaryEntity);
    return summaryEntity;
  }

  public boolean updateStrategyPlanNode(String planExecutionId, NodeExecution strategyNodeExecution, Update update) {
    // If nodeExecution is of type identity, or it is not stage strategy node then ignore.
    if (strategyNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE
        || strategyNodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY
        || !AmbianceUtils.isCurrentStrategyLevelAtStage(strategyNodeExecution.getLevels())) {
      return false;
    }
    ConcurrentChildInstance concurrentChildInstance =
        nodeExecutionInfoService.fetchConcurrentChildInstance(strategyNodeExecution.getUuid());
    if (concurrentChildInstance != null && !strategyNodeExecution.getExecutableResponses().isEmpty()) {
      Node node = planService.fetchNode(strategyNodeExecution.getPlanId(), strategyNodeExecution.getNodeId());
      // TODO: Revisit this logic seems to violating a few principles
      PmsStepParameters parameters = node.getStepParameters();
      if (parameters.containsKey("strategyType")
          && !parameters.get("strategyType").equals(StrategyType.PARALLELISM.name())) {
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId()
                + ".moduleInfo.maxConcurrency.value",
            strategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency());
      }

      // Extract the node id for the given child
      String childSetupId =
          strategyNodeExecution.getExecutableResponses().get(0).getChildren().getChildren(0).getChildNodeId();
      Optional<PipelineExecutionSummaryEntity> entity =
          pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId);
      if (entity.isEmpty()) {
        return false;
      }
      addChildStagesForStrategy(update, entity.get(), concurrentChildInstance.getChildrenNodeExecutionIds(),
          childSetupId, strategyNodeExecution);
    } else if (StatusUtils.brokeStatuses().contains(strategyNodeExecution.getStatus())) {
      Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntity =
          pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId);
      if (pipelineExecutionSummaryEntity.isPresent()
          && pipelineExecutionSummaryEntity.get().getLayoutNodeMap().containsKey(strategyNodeExecution.getNodeId())) {
        List<String> stageSetupIds = pipelineExecutionSummaryEntity.get()
                                         .getLayoutNodeMap()
                                         .get(strategyNodeExecution.getNodeId())
                                         .getEdgeLayoutList()
                                         .getCurrentNodeChildren();
        if (EmptyPredicate.isNotEmpty(stageSetupIds) && stageSetupIds.size() == 1) {
          String stageSetupId = stageSetupIds.get(0);
          update.set(String.format(LayoutNodeGraphConstants.STATUS, stageSetupId), strategyNodeExecution.getStatus());
          if (strategyNodeExecution.getFailureInfo() != null) {
            update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageSetupId + ".failureInfo",
                ExecutionErrorInfo.builder().message(strategyNodeExecution.getFailureInfo().getErrorMessage()).build());
          }
        }
      }
    }
    updateStatusAndStepParametersInStrategyNode(strategyNodeExecution, update, Collections.emptyList());
    return true;
  }

  @Override
  public PipelineExecutionSummaryEntity update(String planExecutionId, Update update) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    PipelineExecutionSummaryEntity summaryEntity = pmsExecutionSummaryRepository.update(query, update);
    // If summaryEntity is null it means we could not update the PlanExecutionSummary. Throwing an exception so that it
    // can be retried later. This happens when no document is found with the given query.
    if (summaryEntity == null) {
      throw new InvalidRequestException("[PMS_GRAPH] Could not update the PlanExecutionSummary");
    }
    if (pipelineSearchService.shouldSyncToElastic(update)) {
      if (pipelineSearchService.shouldFetchDocumentFromPrimary(update)) {
        // We want to do this because there may be some fields which get parallel updated
        // Like moduleInfo field, so we are fetching the record again from primary db to fix it properly
        summaryEntity = pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId).orElse(summaryEntity);
      }
      pipelineSearchService.update(summaryEntity);
    }
    return summaryEntity;
  }

  @Override
  public void updateResolvedUserInputSetYaml(
      String planExecutionId, String resolvedInputSetYaml, String harnessVersion) {
    if (isEmpty(resolvedInputSetYaml)) {
      return;
    }
    Update update = new Update();
    String simplifiedResolvedInputSetYaml = resolvedInputSetYaml;
    try {
      simplifiedResolvedInputSetYaml = Utils.getYamlWithoutInputs(new YamlConfig(resolvedInputSetYaml, harnessVersion));
    } catch (Exception ex) {
      log.error(
          String.format("Unable to remove validators from given Input Set Yaml for Plan Execution ID %s, please check.",
              planExecutionId),
          ex);
    }
    update.set(PlanExecutionSummaryKeys.resolvedUserInputSetYaml, simplifiedResolvedInputSetYaml);
    update(planExecutionId, update);
  }

  @Override
  public boolean handleNodeExecutionUpdateFromGraphUpdate(
      String planExecutionId, NodeExecution nodeExecution, Update update) {
    // Update strategy node data if it is not an identity node and strategy node
    boolean updateRequired = updateStrategyPlanNode(planExecutionId, nodeExecution, update);
    // Update Status and StepParameters for insert node
    if (nodeExecution.getStepType().getStepCategory() == StepCategory.INSERT) {
      updateExecutionStatusAndTimestamps(nodeExecution, update);
      updateRequired = true;
    }

    if (OrchestrationStepTypes.DYNAMIC_STAGE.equals(nodeExecution.getStepType().getType())) {
      updateRequired = addChildForDynamicStage(nodeExecution, update) || updateRequired;
    }

    // Update identity nodes if only they are in final status.
    if ((OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)
            || nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY)
        && nodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      updateRequired = updateIdentityStageOrStrategyNodes(planExecutionId, update) || updateRequired;
    }
    if (nodeExecution.getStepType().getType().equals(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)) {
      String previousStagePlanNodeId = nodeExecutionService.get(nodeExecution.getPreviousId()).getNodeId();
      executionSummaryUpdateUtils.updateNextIdOfStageBeforePipelineRollback(
          update, nodeExecution.getNodeId(), previousStagePlanNodeId);

      // Update dependency graph for DAG pipelines - link rollback stage to the stage that triggered it
      try {
        PipelineExecutionSummaryEntity summaryEntity =
            pmsExecutionSummaryRepository.getPipelineExecutionSummaryWithProjections(
                Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId),
                Set.of(PlanExecutionSummaryKeys.isDagEnabled));
        if (summaryEntity != null && Boolean.TRUE.equals(summaryEntity.getIsDagEnabled())) {
          Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
          OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
              RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));
          if (optionalSweepingOutput.isFound()) {
            OnFailPipelineRollbackOutput rollbackOutput =
                (OnFailPipelineRollbackOutput) optionalSweepingOutput.getOutput();
            if (rollbackOutput.getLevelsAtFailurePoint() != null) {
              for (Level level : rollbackOutput.getLevelsAtFailurePoint()) {
                if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
                  executionSummaryUpdateUtils.updateDependencyGraphForPipelineRollback(
                      update, nodeExecution.getNodeId(), level.getSetupId());
                  break;
                }
              }
            }
          }
        }
      } catch (Exception ex) {
        log.warn("Failed to update dependency graph for pipeline rollback stage", ex);
      }
    }
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
        NodeExecutionContextUtils.getAccountId(nodeExecution), planExecutionId,
        PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized);
    boolean readSwitchEnabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        nodeExecutionService.getAmbiance(nodeExecution), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution planExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          planExecutionId, Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    List<PostExecutionRollbackInfo> postExecutionRollbackInfos =
        PlanExecutionMigrationHelper.readPostExecutionRollbackInfoWithFallbackOnMetadata(
            planExecutionMetadata, planExecution);
    return executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, postExecutionRollbackInfos)
        || updateRequired;
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      String accountIdentifier, String planExecutionId, Set<String> fields) {
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);
    if (executionSummary == null) {
      Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
      return pmsExecutionSummaryRepository.getPipelineExecutionSummaryWithProjections(criteria, fields);
    }
    return executionSummary;
  }

  @Override
  public Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsAndStatusFromAnalytics(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String parentUniqueId) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionSummaryKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId).include(PlanExecutionSummaryKeys.status);
    return pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(query);
  }

  @Override
  public void deleteAllSummaryForGivenPlanExecutionIds(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    if (retainPipelineExecutionDetailsAfterDelete || isEmpty(planExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      DeleteByQueryResponse deleteByQueryResponse = pipelineSearchService.deleteExecutions(planExecutionIds, accountId);
      if (deleteByQueryResponse == null || deleteByQueryResponse.deleted() == null) {
        log.warn("Deleted: 0 out of {} planExecutionIds from ElasticSearch for account: {}", planExecutionIds.size(),
            accountId);
      } else if (deleteByQueryResponse.deleted() != planExecutionIds.size()) {
        log.warn("Deleted: {} out of {} planExecutionIds from ElasticSearch for account: {}",
            deleteByQueryResponse.deleted(), planExecutionIds.size(), accountId);
      }
      pmsExecutionSummaryRepository.deleteAllByPlanExecutionIdIn(planExecutionIds);
      return true;
    });
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    if (isEmpty(planExecutionId)) {
      return;
    }
    Criteria planExecutionIdCriteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(planExecutionIdCriteria);
    Update ops = new Update();
    ops.set(PlanExecutionSummaryKeys.validUntil, ttlDate);
    pmsExecutionSummaryRepository.multiUpdate(query, ops);
  }

  @Override
  public Update updateStatusOps(PlanExecution planExecution, Update summaryEntityUpdate) {
    ExecutionStatus status = ExecutionStatus.getExecutionStatus(planExecution.getStatus());

    summaryEntityUpdate.set(
        PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, planExecution.getStatus());
    summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, status);
    if ((status == ExecutionStatus.ERRORED || status == ExecutionStatus.EXPIRED)
        && planExecution.getFailureInfo() != null) {
      summaryEntityUpdate.set(PlanExecutionSummaryKeys.executionErrorInfo,
          ExecutionErrorInfo.builder().message(planExecution.getFailureInfo().getErrorMessage()).build());
      summaryEntityUpdate.set(PlanExecutionSummaryKeys.failureInfo,
          FailureInfoDTOConverter.toFailureInfoDTO(planExecution.getFailureInfo()));
    }
    if (status == ExecutionStatus.ABORTED) {
      summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.abortedBy,
          abortInfoHelper.fetchAbortedByInfoFromInterrupts(planExecution.getUuid()));
    }
    if (StatusUtils.isFinalStatus(status.getEngineStatus())) {
      summaryEntityUpdate.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, planExecution.getEndTs());
    }
    return summaryEntityUpdate;
  }

  /**
   * Adds child stages of strategy node to the top level graph
   * @param update
   * @param pipelineExecutionSummaryEntity
   * @param childrenExecutionIds
   * @param childSetupId
   * @param strategyNodeExecution
   * @return
   */
  private boolean addChildStagesForStrategy(Update update,
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, List<String> childrenExecutionIds,
      String childSetupId, NodeExecution strategyNodeExecution) {
    Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTOMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
    GraphLayoutNodeDTO graphLayoutNodeDTO = graphLayoutNodeDTOMap.get(childSetupId);
    if (graphLayoutNodeDTO == null) {
      // Expected when DAG post-prod rollback layout excludes pruned strategy/stage nodes.
      log.warn("Skipping addChildStagesForStrategy: no layoutNodeMap entry for childSetupId={} "
              + "(strategyNodeId={}, strategyNodeExecutionId={}, planExecutionId={})",
          childSetupId, strategyNodeExecution.getNodeId(), strategyNodeExecution.getUuid(),
          pipelineExecutionSummaryEntity.getPlanExecutionId());
      return false;
    }
    for (String childId : childrenExecutionIds) {
      // This ensures that we do not add the graph layoutNode again if already added.
      // Since module field is only added while cloning, this means that the cloning was successful
      if (graphLayoutNodeDTOMap.containsKey(childId) && graphLayoutNodeDTOMap.get(childId).getModule() != null) {
        continue;
      }
      cloneGraphLayoutNodeDTO(graphLayoutNodeDTO, childId, update, graphLayoutNodeDTO.getNodeUuid());
    }
    addChildrenToStrategyNode(update, pipelineExecutionSummaryEntity.getPlanExecutionId(), strategyNodeExecution,
        childSetupId, childrenExecutionIds, true);
    return true;
  }

  /**
   * During strategy, we spawn multiple stages,
   * and we already have a dummy node during plan creation,
   * this method is used to copy fields from dummy node to original node.
   */
  private void cloneGraphLayoutNodeDTO(
      GraphLayoutNodeDTO graphLayoutNodeDTO, String uuid, Update update, String setupId) {
    String baseKey = PlanExecutionSummaryKeys.layoutNodeMap + "." + uuid + ".";
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeType, graphLayoutNodeDTO.getNodeType());
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeGroup, graphLayoutNodeDTO.getNodeGroup());
    update.set(baseKey + GraphLayoutNodeDTOKeys.edgeLayoutList, graphLayoutNodeDTO.getEdgeLayoutList());
    update.set(baseKey + GraphLayoutNodeDTOKeys.skipInfo, graphLayoutNodeDTO.getSkipInfo());
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeUuid, setupId);
    update.set(baseKey + GraphLayoutNodeDTOKeys.module, graphLayoutNodeDTO.getModule());
    update.set(
        baseKey + GraphLayoutNodeDTOKeys.executionInputConfigured, graphLayoutNodeDTO.getExecutionInputConfigured());
  }

  /**
   * This is used to get stage setup id of the child nodes for strategy which we want to add in graph.
   */
  private String getStageSetupId(List<NodeExecution> childrenNodeExecution,
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, NodeExecution nodeExecution) {
    String stageSetupId = null;
    // Get the stageSetupId from ChildNodeExecutions of strategy. Will be null if all the stages were successful in
    // previous execution.
    for (NodeExecution childNodeExecution : childrenNodeExecution) {
      if (childNodeExecution.getNodeType() == NodeType.PLAN_NODE && stageSetupId == null) {
        stageSetupId = childNodeExecution.getNodeId();
      }
    }

    // If null, then that means all our childNodeExecution are of type plan node.
    if (stageSetupId == null) {
      if (EmptyPredicate.isNotEmpty(
              graphLayoutNode.get(nodeExecution.getNodeId()).getEdgeLayoutList().getCurrentNodeChildren())) {
        stageSetupId =
            graphLayoutNode.get(nodeExecution.getNodeId()).getEdgeLayoutList().getCurrentNodeChildren().get(0);
      }
    }

    return stageSetupId;
  }

  /**
   * This method updates the status and endTs for identity nodes. All other fields are updated by our
   * ExecutionSummaryUpdateUtils#addStageUpdateCriteria but status and endTs should be populated once identity nodes are
   * in final status, hence this call is done.
   * @param nodeExecution
   * @param update
   * @return
   */
  private boolean handleStageIdentityNodes(NodeExecution nodeExecution, Update update) {
    String graphNodeId = NodeExecutionContextUtils.obtainCurrentSetupId(nodeExecution);
    if (NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(nodeExecution).isPresent()) {
      graphNodeId = nodeExecution.getUuid();
    }
    update.set(String.format(LayoutNodeGraphConstants.STATUS, graphNodeId),
        ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()));
    update.set(String.format(LayoutNodeGraphConstants.END_TS, graphNodeId), nodeExecution.getEndTs());
    return true;
  }

  /**
   * Checks if the given childNodeExecutionId is already added as child for strategy node.
   * @param graphLayoutNode
   * @param strategyNodeId
   * @param childNodeExecutionId
   * @return
   */
  private boolean alreadyAddedAsChild(
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, String strategyNodeId, String childNodeExecutionId) {
    return graphLayoutNode.get(strategyNodeId)
        .getEdgeLayoutList()
        .getCurrentNodeChildren()
        .contains(childNodeExecutionId);
  }

  /**
   * This removes the dummy node id created during plan creation from strategy children and sets hidden as true for the
   * dummy node.
   * @param planExecutionId
   * @param nodeExecution
   * @param stageSetupId
   */
  @VisibleForTesting
  void pullStageStepIdFromStrategyChildren(String planExecutionId, NodeExecution nodeExecution, String stageSetupId) {
    // This is done because we cannot addToSet and pull in same update. We need to fire two operations.
    Update spotUpdate = new Update();
    spotUpdate.pull(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId()
            + ".edgeLayoutList.currentNodeChildren",
        stageSetupId);
    // set hidden as true for the dummy stage node created for strategy
    spotUpdate.set(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + stageSetupId + "." + GraphLayoutNodeDTOKeys.hidden, true);
    update(planExecutionId, spotUpdate);
  }

  /**
   * Adds children in graphLayoutNodeDTO for strategy node and removes the dummy node
   * @param update
   * @param planExecutionId
   * @param strategyNodeExecution
   * @param childSetupId
   * @param childrenExecutionIds
   */
  private void addChildrenToStrategyNode(Update update, String planExecutionId, NodeExecution strategyNodeExecution,
      String childSetupId, List<String> childrenExecutionIds, boolean removeDummyNode) {
    if (removeDummyNode) {
      // This removes the dummy node
      pullStageStepIdFromStrategyChildren(planExecutionId, strategyNodeExecution, childSetupId);
    }
    // This adds all the new nodes
    update
        .addToSet(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId()
            + ".edgeLayoutList.currentNodeChildren")
        .each(childrenExecutionIds);
  }

  private boolean addChildForDynamicStage(NodeExecution dynamicStageExecution, Update update) {
    // TODO(Brijesh): Optimise this to not send the update if child is already present.
    if (EmptyPredicate.isNotEmpty(dynamicStageExecution.getExecutableResponses())
        && dynamicStageExecution.getExecutableResponses().get(0).hasChild()) {
      String childId = dynamicStageExecution.getExecutableResponses().get(0).getChild().getChildNodeId();
      if (EmptyPredicate.isNotEmpty(childId)) {
        update.addToSet(PlanExecutionSummaryKeys.layoutNodeMap + "." + dynamicStageExecution.getNodeId()
                + ".edgeLayoutList.currentNodeChildren",
            childId);
        return true;
      }
    }
    return false;
  }

  /**
   * This updates the status and the step parameters in the layoutNodeMap for strategy node.
   * StrategyNodeExecution should only be passed as parameter.
   * @param strategyNodeExecution
   * @param update
   */

  private void updateStatusAndStepParametersInStrategyNode(
      NodeExecution strategyNodeExecution, Update update, List<NodeExecution> childrenNodeExecution) {
    update.set(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".moduleInfo.stepParameters",
        strategyNodeExecution.getResolvedStepParameters());
    update.set(String.format(LayoutNodeGraphConstants.NODE_RUN_INFO, strategyNodeExecution.getNodeId()),
        strategyNodeExecution.getNodeRunInfo());
    // Update childrenCount for strategy nodes (wrapper nodes that have children)
    update.set(String.format(LayoutNodeGraphConstants.CHILDREN_COUNT, strategyNodeExecution.getNodeId()),
        strategyNodeExecution.getChildrenCount());

    // For V1 rollback identity strategy nodes, calculate status from children
    // Identity nodes don't execute themselves, so their status should reflect their children's status
    if (HarnessYamlVersion.isV1(NodeExecutionContextUtils.getHarnessYamlVersion(strategyNodeExecution))
        && ExecutionModeUtils.isRollbackMode(NodeExecutionContextUtils.getExecutionMode(strategyNodeExecution))
        && strategyNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE && !childrenNodeExecution.isEmpty()) {
      // Calculate aggregated status from children
      List<Status> childStatuses =
          childrenNodeExecution.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
      Status aggregatedStatus = StatusUtils.calculateStatusForNode(childStatuses, strategyNodeExecution.getNodeId());

      // Update the status in the update operation
      ExecutionStatus executionStatus = ExecutionStatus.getExecutionStatus(aggregatedStatus);
      update.set(String.format(LayoutNodeGraphConstants.STATUS, strategyNodeExecution.getNodeId()), executionStatus);

      log.debug("V1 rollback: calculated status {} for strategy node {} based on {} children", executionStatus,
          strategyNodeExecution.getNodeId(), childrenNodeExecution.size());

      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".startTs",
          strategyNodeExecution.getStartTs());
      if (strategyNodeExecution.getFailureInfo() != null) {
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".failureInfo",
            ExecutionErrorInfo.builder().message(strategyNodeExecution.getFailureInfo().getErrorMessage()).build());
      }
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".endTs",
          strategyNodeExecution.getEndTs());
    } else {
      // Default behavior: use node's own status
      updateExecutionStatusAndTimestamps(strategyNodeExecution, update);
    }
  }

  private void updateExecutionStatusAndTimestamps(NodeExecution nodeExecution, Update update) {
    ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
    update.set(String.format(LayoutNodeGraphConstants.STATUS, nodeExecution.getNodeId()), status);
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".startTs",
        nodeExecution.getStartTs());
    if (nodeExecution.getFailureInfo() != null) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".failureInfo",
          ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
    }
    update.set(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".endTs", nodeExecution.getEndTs());
  }

  public void updatePlanExecutionSummaryStatus(String planExecutionId, PlanExecution planExecution) {
    try {
      Update update = updateStatusOps(planExecution, new Update());
      update(planExecutionId, update);
    } catch (Exception ex) {
      log.warn(String.format("Updating PlanExecutionSummaryEntity status with %s for planExecutionId %s failed",
                   planExecution.getStatus(), planExecutionId),
          ex);
    }
  }

  @Override
  public RetryExecutionInfoDTO fetchLatestRetryExecutionInfoDTO(String accountIdentifier, String rootParentId) {
    if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      return PipelineSearchExecutionSummaryDTOMapper.toRetryExecutionInfoDTO(
          pipelineSearchService.fetchLatestExecutionUsingRootParentId(accountIdentifier, rootParentId));
    } else {
      return PipelineExecutionSummaryEntityUtils.toRetryExecutionInfoDTO(
          pmsExecutionSummaryRepository.fetchLatestExecutionUsingRootParentIdFromSecondary(rootParentId));
    }
  }

  @Override
  public PipelineExecutionSummaryEntity fetchFromSecondaryWithProjections(
      String accountIdentifier, String planExecutionId, Set<String> projections) {
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);
    if (executionSummary != null) {
      return executionSummary;
    }
    return pmsExecutionSummaryRepository.fetchFromSecondaryWithProjections(planExecutionId, projections);
  }

  @Override
  public String fetchRootRetryExecutionId(String accountIdentifier, String planExecutionId) {
    PipelineExecutionSummaryEntity entity =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);
    if (entity != null) {
      return entity.getRetryExecutionMetadata().getRootExecutionId();
    }
    return pmsExecutionSummaryRepository.fetchRootRetryExecutionId(planExecutionId);
  }

  @Override
  public PipelineExecutionSummaryEntity getFromSecondaryWithProjections(String accountId, String orgId,
      String projectId, String planExecutionId, boolean pipelineDeleted, List<String> projections,
      ScopeInfo scopeInfo) {
    PipelineExecutionSummaryEntity executionSummary =
        (PipelineExecutionSummaryEntity) executionRetentionService.readExpiredRecordFromObjectStore(accountId,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
            PipelineExecutionSummaryEntity.class);

    if (executionSummary != null) {
      return executionSummary;
    }
    return pmsExecutionSummaryRepository.getPipelineExecutionSummaryEntityFromSecondaryMongoWithProjections(
        accountId, orgId, projectId, planExecutionId, pipelineDeleted, projections, scopeInfo);
  }

  @Override
  public String getNotesForExecution(String accountIdentifier, String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        fetchFromSecondaryWithProjections(accountIdentifier, planExecutionId, Set.of(PlanExecutionSummaryKeys.notes));
    if (pipelineExecutionSummaryEntity.getNotes() == null) { // if notes is null, fallback to old notes in metadata
      return planExecutionMetadataService.getNotesForExecution(accountIdentifier, planExecutionId);
    } else {
      return pipelineExecutionSummaryEntity.getNotes();
    }
  }

  @Override
  public String updateNotesForExecution(String accountIdentifier, String planExecutionId, String notes) {
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.notes, notes);
    update.set(PlanExecutionSummaryKeys.notesExistForPlanExecutionId, !notes.isEmpty());
    handleRetentionLogic(accountIdentifier, planExecutionId, notes, update);
    return notes;
  }

  @Override
  public void updateStatusFromCDC(String planExecutionId, Status status, Long endTs, FailureInfo failureInfo) {
    ExecutionStatus executionStatus = ExecutionStatus.getExecutionStatus(status);
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.internalStatus, status);
    update.set(PlanExecutionSummaryKeys.status, executionStatus);

    if ((executionStatus == ExecutionStatus.ERRORED || executionStatus == ExecutionStatus.EXPIRED)
        && failureInfo != null) {
      update.set(PlanExecutionSummaryKeys.executionErrorInfo,
          ExecutionErrorInfo.builder().message(failureInfo.getErrorMessage()).build());
      update.set(PlanExecutionSummaryKeys.failureInfo, FailureInfoDTOConverter.toFailureInfoDTO(failureInfo));
    }

    if (executionStatus == ExecutionStatus.ABORTED) {
      update.set(PlanExecutionSummaryKeys.abortedBy, abortInfoHelper.fetchAbortedByInfoFromInterrupts(planExecutionId));
    }

    if (StatusUtils.isFinalStatus(status) && endTs != null) {
      update.set(PlanExecutionSummaryKeys.endTs, endTs);
    }

    try {
      // Include cdcGraphEnabled=true in criteria so the update is a no-op when CDC was not enabled
      // for this execution. Prevents duplicate updates when consumer is running but FF was disabled at start.
      Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId)
                              .is(planExecutionId)
                              .and(PlanExecutionSummaryKeys.cdcGraphEnabled)
                              .is(true);
      Query query = new Query(criteria);
      PipelineExecutionSummaryEntity summaryEntity = pmsExecutionSummaryRepository.update(query, update);
      if (summaryEntity == null) {
        log.debug(
            "[CDC-STATUS] No update applied for planExecutionId {} — entity not found or cdcGraphEnabled is not set",
            planExecutionId);
        return;
      }
      if (pipelineSearchService.shouldSyncToElastic(update)) {
        if (pipelineSearchService.shouldFetchDocumentFromPrimary(update)) {
          summaryEntity = pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId).orElse(summaryEntity);
        }
        pipelineSearchService.update(summaryEntity);
      }
    } catch (Exception ex) {
      log.warn("Updating PipelineExecutionSummaryEntity status via CDC with {} for planExecutionId {} failed", status,
          planExecutionId, ex);
    }
  }

  private void handleRetentionLogic(String accountIdentifier, String planExecutionId, String notes, Update update) {
    // if retention metadata exist, and it is expired. Read from store and sync store
    ExecutionRetentionMetadata retentionMetadataExpired = executionRetentionService.getRetentionMetadataIfExpired(
        accountIdentifier, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);
    if (retentionMetadataExpired != null) {
      RetentionFileData fileData = executionRetentionService.getRetentionFileData(
          retentionMetadataExpired, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);
      PipelineExecutionSummaryEntity executionSummary =
          (PipelineExecutionSummaryEntity) executionRetentionService.readObjectFromStore(fileData,
              ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class);
      if (executionSummary != null) {
        PipelineExecutionSummaryEntity updated =
            executionSummary.toBuilder().notes(notes).notesExistForPlanExecutionId(!notes.isEmpty()).build();
        retentionIteratorEntityService.syncSummaryEntityToObjectStore(updated, retentionMetadataExpired);
        if (pipelineSearchService.shouldSyncToElastic(update)) {
          pipelineSearchService.update(updated);
        }
      }
    } else {
      PipelineExecutionSummaryEntity pipelineExecutionSummaryUpdated = update(planExecutionId, update);
      // if retention metadata exist, and it is not expire. Sync store
      ExecutionRetentionMetadata metadata = executionRetentionService.getRetentionMetadata(
          accountIdentifier, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);
      if (metadata != null) {
        retentionIteratorEntityService.syncSummaryEntityToObjectStore(pipelineExecutionSummaryUpdated, metadata);
      }
    }
  }
}
