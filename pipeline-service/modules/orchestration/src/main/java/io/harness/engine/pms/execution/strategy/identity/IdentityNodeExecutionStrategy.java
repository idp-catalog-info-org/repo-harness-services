/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants.IDENTITY_STRATEGY;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.pms.advise.factory.AdviseHandlerFactory;
import io.harness.engine.pms.advise.handlers.IgnoreFailureAdviseHandler;
import io.harness.engine.pms.advise.handlers.InterventionWaitAdviserResponseHandler;
import io.harness.engine.pms.advise.handlers.MarkSuccessAdviseHandler;
import io.harness.engine.pms.advise.handlers.RetryAdviserResponseHandler;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.AbstractNodeExecutionStrategy;
import io.harness.engine.pms.execution.strategy.helper.intfc.EndNodeExecutionHelper;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.IdentityNodeExecutionMetadata;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.logging.AutoLogContext;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.springdata.TransactionHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class IdentityNodeExecutionStrategy
    extends AbstractNodeExecutionStrategy<IdentityPlanNode, IdentityNodeExecutionMetadata> {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private AdviseHandlerFactory adviseHandlerFactory;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private IdentityNodeResumeHelper identityNodeResumeHelper;
  @Inject private TransactionHelper transactionHelper;
  @Inject private IdentityNodeExecutionStrategyHelper identityNodeExecutionStrategyHelper;
  @Inject private PlanService planService;
  @Inject private ExceptionManager exceptionManager;
  @Inject private EndNodeExecutionHelper endNodeExecutionHelper;
  @Inject private DagExecutionService dagExecutionService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;

  private final String SERVICE_NAME_IDENTITY = ModuleType.PMS.name().toLowerCase();

  @Override
  public NodeExecution createNodeExecutionInternal(@NotNull Ambiance ambiance, @NotNull IdentityPlanNode node,
      IdentityNodeExecutionMetadata metadata, String notifyId, String parentId, String previousId,
      InitiateMode initiateMode) {
    return identityNodeExecutionStrategyHelper.createNodeExecution(ambiance, node, notifyId, parentId, previousId);
  }

  @Override
  public List<NodeExecution> createNodeExecutionInternal(RunNodeBatchRequest runNodeBatchRequest) {
    return identityNodeExecutionStrategyHelper.createNodeExecutionInternal(runNodeBatchRequest);
  }

  @Override
  public void startExecution(Ambiance ambiance) {
    String newNodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    NodeExecution newNodeExecution = nodeExecutionService.get(newNodeExecutionId);
    NodeExecution originalExecution = nodeExecutionService.get(newNodeExecution.getOriginalNodeExecutionId());
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      // If Node is skipped and does not have any executable responses then call the adviser response handler straight
      // away
      if (originalExecution.getStatus() == Status.SKIPPED && isEmpty(originalExecution.getExecutableResponses())) {
        NodeExecution skippedExecution = nodeExecutionService.updateStatusWithOps(
            newNodeExecutionId, originalExecution.getStatus(), null, EnumSet.noneOf(Status.class));

        // In Rollback mode, we convert stage nodes to identity nodes.
        // Since we want to run the stages in reverse order, we attach an adviser to every stage and inorder to run that
        // adviser, we need the below if
        if (AmbianceUtils.checkIfFeatureFlagEnabled(
                ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())
            && io.harness.utils.execution.ExecutionModeUtils.isRollbackMode(
                ambiance.getMetadata().getExecutionMode())) {
          IdentityPlanNode idPlanNode = planService.fetchNode(ambiance.getPlanId(), newNodeExecution.getNodeId());
          if (idPlanNode.getUseAdviserObtainments()) {
            processOrQueueAdvisingEvent(newNodeExecution, idPlanNode, newNodeExecution.getStatus());
          } else {
            processAdviserResponse(ambiance, skippedExecution.getAdviserResponse());
          }
        } else if (isDagIdentityContext(ambiance)) {
          // DAG retry: re-obtain advisers (or UNKNOWN → fireDagCallbacksOnUnknownAdvise)
          IdentityPlanNode idPlanNode = planService.fetchNode(ambiance.getPlanId(), newNodeExecution.getNodeId());
          processOrQueueAdvisingEvent(skippedExecution, idPlanNode, skippedExecution.getStatus());
        } else {
          // Sequential retry: reuse copied adviser response (NextStep / NextStage)
          processAdviserResponse(ambiance, skippedExecution.getAdviserResponse());
        }
        return;
      }

      // If this is one of the leaf modes then just clone and copy everything and we should be good
      // This is an optimization/hack to not do any actual work
      if (ExecutionModeUtils.isLeafMode(originalExecution.getMode())) {
        handleLeafNodes(ambiance, newNodeExecution, originalExecution);
        return;
      }

      NodeExecution runningExecution = nodeExecutionService.updateStatusWithOps(
          newNodeExecutionId, Status.RUNNING, null, EnumSet.noneOf(Status.class));

      // If not leaf node then we need to call the identity step
      Ambiance modifyAmbiance = IdentityStep.modifyAmbiance(ambiance);
      NodeStartEvent nodeStartEvent = NodeStartEvent.newBuilder()
                                          .setAmbiance(modifyAmbiance)
                                          .setStepParameters(runningExecution.getResolvedStepParametersBytes())
                                          .setMode(runningExecution.getMode())
                                          .build();
      // hard code of service name to PMS
      eventSender.sendEvent(
          modifyAmbiance, nodeStartEvent, PmsEventCategory.NODE_START, SERVICE_NAME_IDENTITY, true, true);

    } catch (Exception exception) {
      log.error("Exception Occurred in facilitateAndStartStep NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @VisibleForTesting
  void handleLeafNodes(Ambiance ambiance, NodeExecution nodeExecution, NodeExecution originalNodeExecution) {
    transactionHelper.performTransaction(() -> {
      // Copy outcomes
      pmsOutcomeService.cloneForRetryExecution(ambiance, nodeExecution.getOriginalNodeExecutionId());
      // Copy outputs
      pmsSweepingOutputService.cloneForRetryExecution(ambiance, nodeExecution.getOriginalNodeExecutionId());

      // Copying data for retried nodeExecutions when a node has more than one nodeExecutions corresponding to it.
      // This will handle only retry. if there is any new way of running more than one NodeExecution for one planNode
      // then handle that here.
      identityNodeExecutionStrategyHelper.copyNodeExecutionsForRetriedNodes(
          nodeExecution, originalNodeExecution.getRetryIds());

      // Pipeline Stage is a stage-leaf node. Need to set executable response which contains Child ExecutionId. This
      // will be required to show child graph in retried stage.

      // Adding executable response as this will help in fetching logs for retried stages
      return nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), originalNodeExecution.getStatus(),
          update
          -> update.set(NodeExecutionKeys.executableResponses, originalNodeExecution.getExecutableResponses()),
          EnumSet.noneOf(Status.class));
    });

    // Publish StepDetailsUpdate event for IdentityNode to trigger layoutNodeMap update
    // The stepDetails were already copied in copyNodeExecutionsForRetriedNodes via saveNodeExecutionInfoForRetry
    // This event triggers the visualization layer to fetch stepDetails from NodeExecutionsInfo and update layoutNodeMap
    publishStepDetailsUpdateEventForIdentityNode(ambiance, nodeExecution);

    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())
        && io.harness.utils.execution.ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      IdentityPlanNode idPlanNode = planService.fetchNode(ambiance.getPlanId(), nodeExecution.getNodeId());
      // Pipeline Stage is one stage which is coming in as a leaf node. It requies special handler as per below
      if (nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE
          && idPlanNode.getUseAdviserObtainments()) {
        processOrQueueAdvisingEvent(nodeExecution, idPlanNode, originalNodeExecution.getStatus());
      } else {
        processAdviserResponse(ambiance, nodeExecution.getAdviserResponse());
      }
    } else if (isDagIdentityContext(ambiance)) {
      // DAG retry: re-obtain advisers (or UNKNOWN → fireDagCallbacksOnUnknownAdvise)
      IdentityPlanNode idPlanNode = planService.fetchNode(ambiance.getPlanId(), nodeExecution.getNodeId());
      processOrQueueAdvisingEvent(nodeExecution, idPlanNode, originalNodeExecution.getStatus());
    } else {
      // Sequential retry: reuse copied adviser response (NextStep / NextStage)
      processAdviserResponse(ambiance, nodeExecution.getAdviserResponse());
    }
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      if (adviserResponse == null || adviserResponse.getType() == AdviseType.UNKNOWN) {
        fireDagCallbacksOnUnknownAdvise(ambiance, nodeExecutionId);
        endNodeExecution(ambiance, null, null);
        return;
      }
      log.info("Starting to handle Adviser Response of type: {}", adviserResponse.getType());
      // Get all fields of NodeExecution as advisors may use any fields of NodeExecution.
      // As identity nodes can potentially have actual advisors on them, we'll need to update the advisor response
      NodeExecution nodeExecution = nodeExecutionService.update(nodeExecutionId, ops -> {
        ops.set(NodeExecutionKeys.adviserResponse, adviserResponse);
        ops.set(NodeExecutionKeys.advisorsProcessed, true);
      });
      AdviserResponseHandler adviserResponseHandler = adviseHandlerFactory.obtainHandler(adviserResponse.getType());
      if (!isFailureStrategyAdvisor(adviserResponseHandler)) {
        adviserResponseHandler.handleAdvise(nodeExecution, adviserResponse);
      } else {
        endNodeExecution(ambiance, nodeExecution, null);
      }
    }
  }

  private boolean isFailureStrategyAdvisor(AdviserResponseHandler adviserResponseHandler) {
    return adviserResponseHandler instanceof InterventionWaitAdviserResponseHandler
        || adviserResponseHandler instanceof MarkSuccessAdviseHandler
        || adviserResponseHandler instanceof RetryAdviserResponseHandler
        || adviserResponseHandler instanceof IgnoreFailureAdviseHandler;
  }

  @Override
  public void endNodeExecution(Ambiance ambiance, NodeExecution nodeExecution, List<StepOutcomeRef> outcomeRefs) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    boolean isOptimizationDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION.name());
    boolean canUseOptimizedNodeExecution = !isOptimizationDisabled && nodeExecution != null;
    NodeExecution effectiveNodeExecution = canUseOptimizedNodeExecution
        ? nodeExecution
        : nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForExecutionStrategy);
    if (isNotEmpty(effectiveNodeExecution.getNotifyId())) {
      Level level = effectiveNodeExecution.getCurrentLevel();
      StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                .nodeUuid(level.getSetupId())
                                                .failureInfo(effectiveNodeExecution.getFailureInfo())
                                                .identifier(level.getIdentifier())
                                                .status(effectiveNodeExecution.getStatus())
                                                .nodeExecutionId(level.getRuntimeId())
                                                .adviserResponse(effectiveNodeExecution.getAdviserResponse())
                                                .nodeExecutionEndTs(effectiveNodeExecution.getEndTs())
                                                .build();
      startQueuedExecutionIfAny(effectiveNodeExecution, ambiance);
      waitNotifyEngine.doneWith(effectiveNodeExecution.getNotifyId(), responseData);
    } else {
      log.info("Ending Execution");
      startQueuedExecutionIfAny(effectiveNodeExecution, ambiance);
      orchestrationEngine.endNodeExecution(AmbianceUtils.cloneForFinish(ambiance));
    }
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    try {
      StepResponseProto.Builder builder = StepResponseProto.newBuilder().setStatus(Status.FAILED);
      List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(exception);
      if (isNotEmpty(responseMessages)) {
        builder.setFailureInfo(EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages));
      }
      endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(ambiance, builder.build(), null);
    } catch (Exception ex) {
      // Smile if you see irony in this
      log.error("This is very BAD!!!. Exception Occurred while handling Exception. Erroring out Execution", ex);
    }
  }

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForResume);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      identityNodeResumeHelper.resume(nodeExecution, response, asyncError, SERVICE_NAME_IDENTITY);
    } catch (Exception exception) {
      log.error("Exception Occurred in handling resume with nodeExecutionId {} planExecutionId {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processStepResponse(Ambiance ambiance, StepResponseProto stepResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      NodeExecution newNodeExecution = getUpdatedNodeExecution(ambiance, nodeExecutionId, stepResponse);

      /* `newNodeExecution` can be null if the node has already reached a terminal state.
       * One scenario where this can occur, is as follows:
       *
       * If an abort call is made while the identity node creation is still in progress, both threads—one handling
       * the abort and the other handling the node creation—attempt to transition the node to a terminal state
       * simultaneously. This race condition can result in bypassing the check for finalizable statuses. As a
       * consequence, the updated node is returned as null, which leads to a NullPointerException when any further
       * operations are attempted on it.
       */
      if (newNodeExecution == null) {
        return;
      }
      IdentityPlanNode idPlanNode = planService.fetchNode(ambiance.getPlanId(), newNodeExecution.getNodeId());
      if (isDagIdentityContext(ambiance)) {
        // DAG retry: always re-obtain so empty advisers end via UNKNOWN → fireDagCallbacksOnUnknownAdvise
        processOrQueueAdvisingEvent(newNodeExecution, idPlanNode, newNodeExecution.getStatus());
      } else if (Boolean.TRUE.equals(idPlanNode.getUseAdviserObtainments())) {
        // Strategy / rollback Identity nodes that need fresh advisers
        processOrQueueAdvisingEvent(newNodeExecution, idPlanNode, newNodeExecution.getStatus());
      } else {
        // Sequential retry: reuse copied adviser response
        processAdviserResponse(ambiance, newNodeExecution.getAdviserResponse());
      }
    } catch (Exception ex) {
      log.error("Exception Occurred in handleStepResponse NodeExecutionId : {}, PlanExecutionId: {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), ex);
      handleError(ambiance, ex);
    }
  }

  /**
   * True when this execution is a DAG pipeline ({@code ExecutionMetadata.enableDAG}).
   * Used only to select the DAG retry advising path; sequential and rollback paths stay unchanged.
   */
  @VisibleForTesting
  boolean isDagIdentityContext(Ambiance ambiance) {
    return ambiance.getMetadata() != null && ambiance.getMetadata().getEnableDAG();
  }

  private NodeExecution getUpdatedNodeExecution(
      Ambiance ambiance, String nodeExecutionId, StepResponseProto stepResponse) {
    Consumer<Update> updateOps = null;
    // The failure info of identity strategy needs to be updated because even though this node is not retried some
    // stages under this strategy might have been retried and resulted into a new error info
    if (AmbianceUtils.getCurrentStepType(ambiance) != null
        && AmbianceUtils.getCurrentStepType(ambiance).getType().equals(IDENTITY_STRATEGY)) {
      updateOps = ops -> {
        setUnset(ops, NodeExecutionKeys.failureInfo, AmbianceUtils.truncateFailureInfo(stepResponse.getFailureInfo()));
      };
    }
    return nodeExecutionService.updateStatusWithOps(
        nodeExecutionId, stepResponse.getStatus(), updateOps, EnumSet.noneOf(Status.class));
  }

  public PmsNodeExecutionMetadata createMetadata(StrategyMetadata strategyMetadata) {
    return IdentityNodeExecutionMetadata.builder().strategyMetadata(strategyMetadata).build();
  }

  /**
   * Publishes a StepDetailsUpdate event for an IdentityNode to trigger layoutNodeMap update.
   *
   * The stepDetails are already copied in copyNodeExecutionsForRetriedNodes via saveNodeExecutionInfoForRetry.
   * This event triggers the visualization layer to fetch those stepDetails from NodeExecutionsInfo
   * and update the layoutNodeMap so the UI can display the child graph correctly.
   *
   * @param ambiance the execution context
   * @param nodeExecution the IdentityNode execution
   */
  private void publishStepDetailsUpdateEventForIdentityNode(Ambiance ambiance, NodeExecution nodeExecution) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    String nodeExecutionId = nodeExecution.getUuid();
    io.harness.pms.contracts.steps.StepType stepType = nodeExecution.getStepType();

    nodeExecutionInfoService.publishStepDetailsUpdate(accountId, planExecutionId, nodeExecutionId, stepType);
  }

  private void fireDagCallbacksOnUnknownAdvise(Ambiance ambiance, String nodeExecutionId) {
    try {
      if (ambiance.getLevelsCount() < 2) {
        return;
      }
      String parentSetupId = ambiance.getLevels(ambiance.getLevelsCount() - 2).getSetupId();
      Node parentNode = planService.fetchNode(ambiance.getPlanId(), parentSetupId);
      if (parentNode instanceof PlanNode && ((PlanNode) parentNode).hasDependencyGraph()) {
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        Node node = planService.fetchNode(ambiance.getPlanId(), level.getSetupId());
        NodeExecution nodeExecution =
            nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withStatus);
        dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, node, nodeExecution.getStatus());
      }
    } catch (Exception e) {
      log.warn("Error firing DAG callbacks on unknown adviser response for nodeExecutionId: {}", nodeExecutionId, e);
    }
  }
}
