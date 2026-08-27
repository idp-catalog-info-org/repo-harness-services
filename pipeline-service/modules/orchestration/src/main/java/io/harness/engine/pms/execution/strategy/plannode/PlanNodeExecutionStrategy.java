/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.beans.FeatureName.PIPE_FIX_STUCK_EXECUTION_AFTER_TRANSITION_FAILURE;
import static io.harness.beans.FeatureName.PIPE_SKIP_EXECUTE_WHEN_CONDITION_ON_RETRY_STEP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INTERNAL_SERVER_ERROR;
import static io.harness.eraro.Level.ERROR;
import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.WaitForExecutionInputHelper;
import io.harness.engine.executioncheck.ExecutionCheck;
import io.harness.engine.executioncheck.PreFacilitationExecutionCheck;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.RunPreFacilitationChecker;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.observers.NodeCreateInfo;
import io.harness.engine.observers.NodeExecutionCreateObserver;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.pms.advise.factory.AdviseHandlerFactory;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceExecutionContextHelper;
import io.harness.engine.pms.execution.strategy.AbstractNodeExecutionStrategy;
import io.harness.engine.pms.execution.strategy.helper.intfc.EndNodeExecutionHelper;
import io.harness.engine.pms.resume.NodeResumeHelper;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.execution.RunNodeRequest;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.common.ExpressionMode;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.ExpressionResolutionMetricsService;
import io.harness.observer.Subject;
import io.harness.opaclient.model.OpaConstants;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.NGWorkflowType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNodeExecutionStrategy extends AbstractNodeExecutionStrategy<PlanNode, NodeExecutionMetadata> {
  @Inject private Injector injector;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private FacilitationHelper facilitationHelper;
  @Inject private ExceptionManager exceptionManager;
  @Inject private EndNodeExecutionHelper endNodeExecutionHelper;
  @Inject private FacilitateEventPublisher facilitateEventPublisher;
  @Inject private NodeStartHelper startHelper;
  @Inject private InterruptService interruptService;
  @Inject private AdviseHandlerFactory adviseHandlerFactory;
  @Inject private NodeResumeHelper resumeHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PmsOutcomeService outcomeService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private PlanExpansionService planExpansionService;
  @Inject WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Inject PlanExecutionService planExecutionService;
  @Inject TransactionHelper transactionHelper;
  @Inject private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PipelineRetentionService pipelineRetentionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private AmbianceExecutionContextHelper ambianceExecutionContextHelper;
  @Inject DagExecutionService dagExecutionService;
  @Inject private ExpressionResolutionMetricsService expressionResolutionMetricsService;
  @Getter private final Subject<NodeExecutionCreateObserver> nodeExecutionCreateObserverSubject = new Subject<>();

  @Override
  public NodeExecution createNodeExecutionInternal(@NotNull Ambiance ambiance, @NotNull PlanNode node,
      NodeExecutionMetadata metadata, String notifyId, String parentId, String previousId, InitiateMode initiateMode) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String name = node.getName();
    String identifier = node.getIdentifier();
    if (metadata != null && metadata.getStrategyMetadata() != null) {
      name = AmbianceUtils.modifyIdentifier(metadata.getStrategyMetadata(), node.getName(), ambiance);
      identifier = AmbianceUtils.modifyIdentifier(metadata.getStrategyMetadata(), node.getIdentifier(), ambiance);
    }

    NodeExecutionBuilder builder =
        NodeExecution.builder()
            .uuid(uuid)
            .executionInputConfigured(!EmptyPredicate.isEmpty(node.getExecutionInputTemplate()))
            .levelCount(ambiance.getLevelsCount())
            .status(Status.QUEUED)
            .notifyId(notifyId)
            .parentId(parentId)
            .previousId(previousId)
            .unitProgresses(new ArrayList<>())
            .module(node.getServiceName())
            .name(name)
            .skipGraphType(node.getSkipGraphType())
            .identifier(identifier)
            .stepType(node.getStepType())
            .nodeId(node.getUuid())
            .stageFqn(node.getStageFqn())
            .group(node.getGroup())
            .skipExpressionChain(node.isSkipExpressionChain())
            .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
            .nodeType(node.getNodeType().name())
            .mode(facilitationHelper.getExecutionMode(node.getFacilitatorObtainments()));
    ambianceExecutionContextHelper.setAmbianceAndExecutionContextValues(ambiance, builder);
    boolean isOptimizationDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION.name());
    // Save NodeExecutionInfo immediately if:
    // 1. Optimization is disabled, OR
    // 2. Node has strategyMetadata (expressions need this data before resolution), OR
    // 3. InitiateMode is CREATE (node won't be started immediately, so defer save is unsafe)
    if (isOptimizationDisabled || initiateMode == InitiateMode.CREATE) {
      pmsGraphStepDetailsService.saveNodeExecutionInfo(uuid, ambiance.getPlanExecutionId(),
          metadata == null ? null : metadata.getStrategyMetadata(), AmbianceUtils.getAccountId(ambiance));
      nodeExecutionCreateObserverSubject.fireInform(NodeExecutionCreateObserver::onNodeCreate,
          NodeCreateInfo.builder()
              .ambiance(ambiance)
              .nodeExecutionId(uuid)
              .planExecutionId(ambiance.getPlanExecutionId())
              .node(node)
              .build());
    }
    return nodeExecutionService.save(builder.build());
  }

  private NodeExecutionBuilder mapToNodeExecutionBuilder(@NotNull Ambiance ambiance, @NotNull PlanNode node,
      NodeExecutionMetadata metadata, String notifyId, String parentId, String previousId) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String name = node.getName();
    String identifier = node.getIdentifier();
    if (metadata != null && metadata.getStrategyMetadata() != null) {
      name = AmbianceUtils.modifyIdentifier(metadata.getStrategyMetadata(), node.getName(), ambiance);
      identifier = AmbianceUtils.modifyIdentifier(metadata.getStrategyMetadata(), node.getIdentifier(), ambiance);
    }

    NodeExecutionBuilder builder =
        NodeExecution.builder()
            .uuid(uuid)
            .executionInputConfigured(!EmptyPredicate.isEmpty(node.getExecutionInputTemplate()))
            .levelCount(ambiance.getLevelsCount())
            .status(Status.QUEUED)
            .notifyId(notifyId)
            .parentId(parentId)
            .previousId(previousId)
            .unitProgresses(new ArrayList<>())
            .module(node.getServiceName())
            .name(name)
            .skipGraphType(node.getSkipGraphType())
            .identifier(identifier)
            .stepType(node.getStepType())
            .nodeId(node.getUuid())
            .stageFqn(node.getStageFqn())
            .group(node.getGroup())
            .skipExpressionChain(node.isSkipExpressionChain())
            .levelRuntimeIdx(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance))
            .nodeType(node.getNodeType().name())
            .mode(facilitationHelper.getExecutionMode(node.getFacilitatorObtainments()));
    ambianceExecutionContextHelper.setAmbianceAndExecutionContextValues(ambiance, builder);
    return builder;
  }

  @Override
  public List<NodeExecution> createNodeExecutionInternal(RunNodeBatchRequest runNodeBatchRequest) {
    List<RunNodeRequest> nodes = runNodeBatchRequest.getNodes();
    List<NodeExecutionBuilder> nodeExecutions =
        nodes.stream()
            .map(o
                -> mapToNodeExecutionBuilder(o.getAmbiance(), (PlanNode) o.getNode(),
                    (NodeExecutionMetadata) o.getMetadata(), o.getNotifyId(), o.getParentId(), o.getPreviousId()))
            .toList();
    List<NodeExecution> builtNodeExecutions = nodeExecutions.stream().map(NodeExecutionBuilder::build).toList();

    pmsGraphStepDetailsService.saveNodeExecutionInfo(
        builtNodeExecutions, nodes.stream().map(RunNodeRequest::getStrategyMetadata).collect(Collectors.toList()));

    for (RunNodeRequest nodeRequest : nodes) {
      nodeExecutionCreateObserverSubject.fireInform(NodeExecutionCreateObserver::onNodeCreate,
          NodeCreateInfo.builder()
              .ambiance(nodeRequest.getAmbiance())
              .nodeExecutionId(nodeRequest.getRuntimeId())
              .planExecutionId(nodeRequest.getAmbiance().getPlanExecutionId())
              .node(nodeRequest.getNode())
              .build());
    }
    return nodeExecutionService.saveAll(builtNodeExecutions);
  }

  @VisibleForTesting
  void resolveParameters(Ambiance ambiance, PlanNode planNode) {
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    String accountId = AmbianceUtils.getAccountId(ambiance);
    log.debug("Starting to Resolve step parameters");
    ExpressionMode expressionMode = planNode.getExpressionMode();
    Object resolvedStepParameters;

    long start = System.currentTimeMillis();
    String status = ExpressionResolutionMetricsService.STATUS_SUCCESS;
    try {
      resolvedStepParameters =
          pmsEngineExpressionService.resolve(ambiance, planNode.getStepParameters(), expressionMode);
    } catch (Exception ex) {
      status = ExpressionResolutionMetricsService.STATUS_FAILURE;
      throw ex;
    } finally {
      recordExpressionResolutionMetrics(accountId, status, Duration.ofMillis(System.currentTimeMillis() - start));
    }
    PmsStepParameters resolvedParameters = PmsStepParameters.parse(
        OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepParameters));
    // Graph step inputs calculate
    PmsStepParameters resolvedStepInputs =
        nodeExecutionService.getResolvedStepInputs(planNode.getExcludedKeysFromStepInputs(), resolvedParameters);

    boolean isOptimizationDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION.name());

    // Get strategyMetadata from ambiance for upsert (when optimization enabled)
    StrategyMetadata strategyMetadata = null;
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (currentLevel != null && currentLevel.hasStrategyMetadata()) {
      strategyMetadata = currentLevel.getStrategyMetadata();
    }

    final StrategyMetadata finalStrategyMetadata = strategyMetadata;
    final boolean optimizationDisabled = isOptimizationDisabled;

    boolean[] wasInserted = {false};
    transactionHelper.performTransaction(() -> {
      // TODO (prashant) : This is a hack right now to serialize in binary as findAndModify is not honoring converter
      // for maps Find a better way to do this
      nodeExecutionService.updateV2(nodeExecutionId, ops -> {
        ops.set(NodeExecutionKeys.resolvedParams, kryoSerializer.asDeflatedBytes(resolvedParameters));
        ops.set(NodeExecutionKeys.excludedKeysFromStepInputs, planNode.getExcludedKeysFromStepInputs());
        ops.inc(NodeExecutionKeys.resolvedParamsVersion);
      });
      // Graph step Inputs update - check if document was inserted
      wasInserted[0] = addResolvedStepInputsAndCheckInsert(
          ambiance.getPlanExecutionId(), nodeExecutionId, resolvedStepInputs, finalStrategyMetadata, accountId);
      planExpansionService.addStepInputs(ambiance, resolvedParameters);
      return resolvedParameters;
    });

    // Fire observer if optimization is enabled and document was just inserted
    if (!optimizationDisabled && wasInserted[0]) {
      nodeExecutionCreateObserverSubject.fireInform(NodeExecutionCreateObserver::onNodeCreate,
          NodeCreateInfo.builder()
              .ambiance(ambiance)
              .nodeExecutionId(nodeExecutionId)
              .planExecutionId(ambiance.getPlanExecutionId())
              .node(planNode)
              .build());
    }
    log.info("Resolved to step parameters");
  }

  @VisibleForTesting
  void addResolvedStepInputs(String planExecutionId, String nodeExecutionId, PmsStepParameters resolvedStepInputs,
      StrategyMetadata strategyMetadata, String accountId, Ambiance ambiance, PlanNode node) {
    pmsGraphStepDetailsService.addStepInputs(
        nodeExecutionId, resolvedStepInputs, planExecutionId, strategyMetadata, accountId);
    log.info("Added Resolved step Inputs");
  }

  @VisibleForTesting
  boolean addResolvedStepInputsAndCheckInsert(String planExecutionId, String nodeExecutionId,
      PmsStepParameters resolvedStepInputs, StrategyMetadata strategyMetadata, String accountId) {
    boolean wasInserted = pmsGraphStepDetailsService.addStepInputsInternal(
        nodeExecutionId, resolvedStepInputs, planExecutionId, strategyMetadata, accountId);
    log.info("Added Resolved step Inputs, wasInserted: {}", wasInserted);
    return wasInserted;
  }

  private void recordExpressionResolutionMetrics(String accountId, String status, Duration duration) {
    try {
      expressionResolutionMetricsService.recordExpressionResolution(accountId, status, duration);
    } catch (Exception ex) {
      log.warn("Failed to record expression resolution metrics", ex);
    }
  }

  @Override
  public void startExecution(Ambiance ambiance) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String nodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), nodeId);
      try {
        resolveParameters(ambiance, planNode);
      } catch (Exception ex) {
        // NOTE: If there is an exception occurred while resolving parameters and when condition evaluates to skipped
        // then we should not throw exception but rather carry on the execution
        PreFacilitationExecutionCheck check = performPreFacilitationChecks(ambiance, planNode);
        if (!check.isProceed()) {
          performUpdateIfRequired(check, nodeExecutionId);
          log.info("Not Proceeding with  Execution. Reason : {}", check.getReason());
          return;
        }
        throw ex;
      }

      PreFacilitationExecutionCheck check = performPreFacilitationChecks(ambiance, planNode);
      if (!check.isProceed()) {
        performUpdateIfRequired(check, nodeExecutionId);
        log.info("Not Proceeding with  Execution. Reason : {}", check.getReason());
        return;
      }
      log.debug("Proceeding with  Execution. Reason : {}", check.getReason());

      // Check if planNode has dependency graph (for now, it will be present only for stages node)
      if ((NGWorkflowType.ORCHESTRATION.equals(AmbianceUtils.getWorkflowType(ambiance))
              || pmsFeatureFlagService.isEnabled(
                  AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))
          && planNode.hasDependencyGraph()) {
        processDependencyGraphExecution(ambiance, planNode);
      }

      if (waitForExecutionInputHelper.waitForExecutionInput(ambiance, nodeExecutionId, planNode)) {
        performUpdateIfRequired(check, nodeExecutionId);
        return;
      }

      if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT)) {
        facilitationHelper.checkAndRunSecondaryFacilitator(ambiance, planNode);
      }

      if (facilitationHelper.customFacilitatorPresent(planNode)) {
        performUpdateIfRequired(check, nodeExecutionId);
        facilitateEventPublisher.publishEvent(ambiance, planNode);
        return;
      }
      FacilitatorResponseProto facilitatorResponseProto =
          facilitationHelper.calculateFacilitatorResponse(ambiance, planNode);
      processFacilitationResponseV2(ambiance, facilitatorResponseProto,
          EmptyPredicate.isEmpty(check.getUpdates()) ? new HashMap<>() : check.getUpdates());
    } catch (Exception exception) {
      log.error(String.format("Exception Occurred in facilitateAndStartStep NodeExecutionId : %s, PlanExecutionId: %s",
                    nodeExecutionId, ambiance.getPlanExecutionId()),
          exception);
      handleError(ambiance, exception);
    }
  }

  private void performUpdateIfRequired(PreFacilitationExecutionCheck check, String nodeExecutionId) {
    if (EmptyPredicate.isNotEmpty(check.getUpdates())) {
      nodeExecutionService.updateV2(nodeExecutionId, ops -> getUpdateOps(check.getUpdates(), ops));
    }
  }

  @Override
  public void processFacilitationResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
      if (isOptimizationWriteDisabled) {
        String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
        nodeExecutionService.updateV2(
            nodeExecutionId, ops -> ops.set(NodeExecutionKeys.mode, facilitatorResponse.getExecutionMode()));

        List<String> interruptNodeExecutionIds = new ArrayList<>(List.of(nodeExecutionId));
        // AbortAll/ExpireAll expires can be at stage level and pipeline level.
        // NodeExecutionId.
        AmbianceUtils.getStageLevelFromAmbiance(ambiance).ifPresent(
            stageLevel -> interruptNodeExecutionIds.add(stageLevel.getRuntimeId()));
        AmbianceUtils.getPipelineLevelFromAmbiance(ambiance).ifPresent(
            pipelineLevel -> interruptNodeExecutionIds.add(pipelineLevel.getRuntimeId()));

        List<String> planExecutionList = new ArrayList<>();
        planExecutionList.add(ambiance.getPlanExecutionId());
        var hasParentPipeline = ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline();
        if (hasParentPipeline) {
          var pipelineStageInfo = ambiance.getMetadata().getPipelineStageInfo();
          planExecutionList.add(pipelineStageInfo.getExecutionId());
        }

        ExecutionCheck check = interruptService.checkInterruptsPreInvocation(
            planExecutionList, nodeExecutionId, interruptNodeExecutionIds, ambiance);

        if (!check.isProceed()) {
          log.info("Not Proceeding with Execution : {}", check.getReason());
          return;
        }
        startHelper.startNode(ambiance, facilitatorResponse);
      } else {
        // Normalize to V2 path so all facilitation writes are merged and executed once in NodeStartHelper
        Map<String, Object> updates = new HashMap<>();
        processFacilitationResponseV2(ambiance, facilitatorResponse, updates);
      }
    } catch (Exception exception) {
      log.error(
          String.format(
              "Exception Occurred while processing facilitation response NodeExecutionId : %s, PlanExecutionId: %s",
              AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId()),
          exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processFacilitationResponseV2(
      Ambiance ambiance, FacilitatorResponseProto facilitatorResponse, Map<String, Object> updates) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      // Do NOT write here. Defer persisting `mode` and any `updates` to NodeStartHelper,
      // which merges them with the status transition in a single atomic write.
      updates.put(NodeExecutionKeys.mode, facilitatorResponse.getExecutionMode());
      boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
      if (isOptimizationWriteDisabled) {
        nodeExecutionService.updateV2(nodeExecutionId, ops -> getUpdateOps(updates, ops));
      }

      List<String> interruptNodeExecutionIds = new ArrayList<>(List.of(nodeExecutionId));
      // AbortAll/ExpireAll expires can be at stage level and pipeline level.
      // NodeExecutionId.
      AmbianceUtils.getStageLevelFromAmbiance(ambiance).ifPresent(
          stageLevel -> interruptNodeExecutionIds.add(stageLevel.getRuntimeId()));
      AmbianceUtils.getPipelineLevelFromAmbiance(ambiance).ifPresent(
          pipelineLevel -> interruptNodeExecutionIds.add(pipelineLevel.getRuntimeId()));

      List<String> planExecutionList = new ArrayList<>();
      planExecutionList.add(ambiance.getPlanExecutionId());
      var hasParentPipeline = ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline();
      if (hasParentPipeline) {
        var pipelineStageInfo = ambiance.getMetadata().getPipelineStageInfo();
        planExecutionList.add(pipelineStageInfo.getExecutionId());
      }

      ExecutionCheck check = interruptService.checkInterruptsPreInvocation(
          planExecutionList, nodeExecutionId, interruptNodeExecutionIds, ambiance);

      if (!check.isProceed()) {
        log.info("Not Proceeding with Execution : {}", check.getReason());
        return;
      }
      // Single-write facilitation: NodeStartHelper.startNode(...) will apply `mode` and all `updates`
      // together with the RUNNING/WAITING status transition in one updateStatusWithOps call.
      if (isOptimizationWriteDisabled) {
        startHelper.startNode(ambiance, facilitatorResponse);
      } else {
        startHelper.startNode(ambiance, facilitatorResponse, updates);
      }
    } catch (Exception exception) {
      log.error(
          String.format(
              "Exception Occurred while processing facilitation response NodeExecutionId : %s, PlanExecutionId: %s",
              AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId()),
          exception);
      handleError(ambiance, exception);
    }
  }

  private void getUpdateOps(Map<String, Object> updates, Update ops) {
    ops.set(NodeExecutionKeys.processingEvent, false);
    for (Map.Entry<String, Object> entry : updates.entrySet()) {
      ops.set(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void processStartEventResponse(Ambiance ambiance, ExecutableResponse executableResponse) {}

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForResume);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (!StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
        log.warn("NodeExecution is no longer in RESUMABLE state Uuid: {} Status {} ", nodeExecution.getUuid(),
            nodeExecution.getStatus());
        return;
      }
      if (nodeExecution.getStatus() != RUNNING) {
        Status previousNodeExecutionStatus = nodeExecution.getStatus();
        log.debug("Marking the nodeExecution with id {} as RUNNING as previous status {}", nodeExecutionId,
            previousNodeExecutionStatus);
        nodeExecution =
            nodeExecutionService.updateStatusWithOps(nodeExecutionId, RUNNING, null, EnumSet.noneOf(Status.class));

        if (nodeExecution == null) {
          throw new InternalServerErrorException(
              String.format("Failed to resume NodeExecution [nodeExecutionId=%s, previousStatus=%s]", nodeExecutionId,
                  previousNodeExecutionStatus));
        }
        // After resuming, pipeline status need to be set. Ex: Pipeline waiting on approval step, pipeline status is
        // waiting, after approval, node execution is marked as running and,  similarly we are marking for pipeline.
        // Earlier pipeline status was marked from step itself.

        // PlanExecution status update check is not even required if previousStatus of nodeExecution was in
        // FLOWING_STATUS
        if (!StatusUtils.flowingStatuses().contains(previousNodeExecutionStatus)) {
          planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
        }
      } else {
        // This will happen if the node is not in any paused or waiting statuses.
        log.debug("NodeExecution with id {} is already in Running status", nodeExecutionId);
      }
      resumeHelper.resume(nodeExecution, response, asyncError);
    } catch (Exception exception) {
      log.error(String.format("Exception Occurred in handling resume with nodeExecutionId %s planExecutionId %s",
                    nodeExecutionId, ambiance.getPlanExecutionId()),
          exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void concludeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance));
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), level.getSetupId());
    if (isEmpty(node.getAdviserObtainments())) {
      boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
      NodeExecution updatedNodeExecution = null;
      if (isOptimizationWriteDisabled) {
        updatedNodeExecution =
            nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus, null, overrideStatusSet);
      } else {
        updatedNodeExecution = nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus,
            ops -> ops.set(NodeExecutionKeys.advisorsProcessed, true), overrideStatusSet);
      }
      if (updatedNodeExecution == null) {
        log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
        return;
      }
      endNodeExecution(ambiance, updatedNodeExecution, null);
      return;
    }
    NodeExecution updatedNodeExecution = nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus,
        ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()), overrideStatusSet);
    if (updatedNodeExecution == null) {
      log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
      return;
    }
    processOrQueueAdvisingEvent(updatedNodeExecution, node, fromStatus);
  }

  @Override
  public void processStepResponse(Ambiance ambiance, StepResponseProto stepResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      handleStepResponseInternal(ambiance, stepResponse);
    } catch (Exception ex) {
      log.error(String.format("Exception Occurred in handleStepResponse NodeExecutionId : %s, PlanExecutionId: %s",
                    AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId()),
          ex);
      handleError(ambiance, ex);
    }
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (adviserResponse.getType() == AdviseType.UNKNOWN) {
        log.warn("Got null advise for node execution with id {}", nodeExecutionId);
        // Fire DAG callbacks for stages in DAG pipelines where no adviser could advise (e.g., ABORTED stages)
        fireDagCallbacksOnUnknownAdvise(ambiance, nodeExecutionId);
        // Mark advisorsProcessed here to avoid relying on fallback in endNodeExecution
        boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
        if (!isOptimizationWriteDisabled) {
          nodeExecutionService.updateV2(nodeExecutionId, ops -> {
            ops.set(NodeExecutionKeys.advisorsProcessed, true);
            ops.set(NodeExecutionKeys.processingEvent, false);
          });
        }
        endNodeExecution(ambiance, null, null);
        return;
      }
      log.info("Starting to handle Adviser Response of type: {}", adviserResponse.getType());
      // Get all fields of NodeExecution as advisors may use any fields of NodeExecution
      NodeExecution updatedNodeExecution = nodeExecutionService.update(nodeExecutionId, ops -> {
        ops.set(NodeExecutionKeys.adviserResponse, adviserResponse);
        ops.set(NodeExecutionKeys.advisorsProcessed, true);
        ops.set(NodeExecutionKeys.processingEvent, false);
      });
      // For Retry Adviser and Intervention Waiting, we don't want to start the queued execution therefore ignoring
      if (adviserResponse.getType() != AdviseType.RETRY && adviserResponse.getType() != AdviseType.INTERVENTION_WAIT) {
        startQueuedExecutionIfAny(updatedNodeExecution, ambiance);
      }
      AdviserResponseHandler adviserResponseHandler = adviseHandlerFactory.obtainHandler(adviserResponse.getType());
      adviserResponseHandler.handleAdvise(updatedNodeExecution, adviserResponse);
    }
  }

  private void endNodeExecutionWithUnexpectedFailure(String nodeExecutionId, Ambiance ambiance) {
    boolean isOptimizationWriteDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION.name());
    nodeExecutionService.updateV2(nodeExecutionId, ops -> {
      ops.set(NodeExecutionKeys.failureInfo,
          FailureData.newBuilder()
              .setLevel(ERROR.name())
              .setCode(INTERNAL_SERVER_ERROR.name())
              .setMessage("Unexpected error. Please contact Harness support.")
              .build());
      if (!isOptimizationWriteDisabled) {
        ops.set(NodeExecutionKeys.advisorsProcessed, true);
      }
    });
    endNodeExecution(ambiance, null, null);
  }

  @Override
  public void endNodeExecution(Ambiance ambiance, NodeExecution nodeExecution, List<StepOutcomeRef> outcomeRefs) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      boolean isOptimizationDisabled = AmbianceUtils.checkIfFeatureFlagEnabled(
          ambiance, FeatureName.PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION.name());
      boolean canUseOptimizedNodeExecution = !isOptimizationDisabled && nodeExecution != null;
      NodeExecution effectiveNodeExecution = canUseOptimizedNodeExecution
          ? nodeExecution
          : nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForExecutionStrategy);
      if (false == effectiveNodeExecution.getAdvisorsProcessed()) {
        // Fallback path detected: advisorsProcessed wasn't set by upstream flows.
        log.warn("advisorsProcessed fallback executed for nodeExecutionId={} planExecutionId={}", nodeExecutionId,
            ambiance.getPlanExecutionId());
        nodeExecutionService.updateV2(nodeExecutionId, ops -> {
          ops.set(NodeExecutionKeys.advisorsProcessed, true);
          ops.set(NodeExecutionKeys.processingEvent, false);
        });
      }
      if (isNotEmpty(effectiveNodeExecution.getNotifyId())) {
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        FailureInfo failureInfo = effectiveNodeExecution.getFailureInfo();
        if (failureInfo != null) {
          FailureInfo.Builder failureInfoBuilder = failureInfo.toBuilder();
          String errorMessage = failureInfo.getErrorMessage();
          if (StatusUtils.brokeStatuses().contains(effectiveNodeExecution.getStatus())) {
            if (EmptyPredicate.isNotEmpty(failureInfoBuilder.getFailureDataList())) {
              failureInfoBuilder.getFailureDataList().forEach(
                  failureData -> endNodeExecutionHelper.decorateFailureData(ambiance, errorMessage, failureData));
            } else {
              FailureData failureData = endNodeExecutionHelper.decorateFailureData(ambiance, errorMessage, null);
              failureInfoBuilder.addFailureData(failureData);
            }
          }
          failureInfo = failureInfoBuilder.build();
        }
        List<StepOutcomeRef> effectiveOutcomeRefs =
            (outcomeRefs != null) ? outcomeRefs : outcomeService.fetchOutcomeRefs(nodeExecutionId);
        StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                  .nodeUuid(level.getSetupId())
                                                  .stepOutcomeRefs(effectiveOutcomeRefs)
                                                  .failureInfo(failureInfo)
                                                  .identifier(level.getIdentifier())
                                                  .nodeExecutionId(level.getRuntimeId())
                                                  .status(effectiveNodeExecution.getStatus())
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
  }

  @VisibleForTesting
  void handleStepResponseInternal(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withStatusAndAdviserObtainment);
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    try {
      String stepOutcomes = RecastOrchestrationUtils.toJson(stepResponse.getStepOutcomesList());
      if (!pipelineSettingsService.isOutcomeResponseWithinLimit(accountIdentifier, stepOutcomes)) {
        log.info(
            "[OUTCOME_RESPONSE_SIZE_EXCEEDED] for nodeExecutionId : {}", nodeExecution.getOriginalNodeExecutionId());
        if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_HARD_IMPOSE_EXECUTION_LIMITS)) {
          throw new LimitExceededException("You have exceeded the response size allowed on the account. Please upgrade "
              + "your plan or contact Harness Support.");
        }
        try {
          pipelineRetentionService.updateMaxOutcomeResponseSize(
              planNode.getAccountIdentifier(), Long.valueOf(stepOutcomes.length()));
        } catch (Exception ex) {
          log.warn(String.format("Can be ignored - Error in overriding the outcome response size limit for account id: "
                           + "{%s}, to size: {%d}:",
                       planNode.getAccountIdentifier(), stepOutcomes.length()),
              ex);
        }
      }
    } catch (Exception ex) {
      log.warn(
          String.format("Can be ignored - Error in evaluating step outcome size for accountId {%s}", accountIdentifier),
          ex);
    }

    // On finishing step, pipeline and stage status need to be set. Ex: Pipeline waiting on approval step, pipeline
    // status is waiting, after approval, node execution is marked as running and finished,  similarly we are marking
    // for pipeline. Earlier pipeline status was marked from step itself.

    // PlanExecution status update check is not even required if previousStatus of nodeExecution was in
    // FLOWING_STATUS
    // Need to update even when there are no advisorObtainments in the node because when steps are in parallel then will
    // not have any advisors but on completion of any parallel step we need to update the status of pipeline and
    // stagenode.
    if (!StatusUtils.flowingStatuses().contains(nodeExecution.getStatus())) {
      planExecutionService.calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
    }

    if (isEmpty(planNode.getAdviserObtainments()) && isEmpty(nodeExecution.getAdviserObtainments())) {
      log.info("No Advisers for the node Ending Execution");
      dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, planNode, stepResponse.getStatus());
      endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(ambiance, stepResponse, planNode);
      return;
    }
    NodeExecution updatedNodeExecution =
        endNodeExecutionHelper.handleStepResponsePreAdviser(ambiance, stepResponse, planNode);
    if (updatedNodeExecution == null) {
      if (pmsFeatureFlagService.isEnabled(accountIdentifier, PIPE_FIX_STUCK_EXECUTION_AFTER_TRANSITION_FAILURE)) {
        log.error("Unrecoverable internal error while updating nodeExecutionId {}, ending execution",
            nodeExecution.getUuid());
        endNodeExecutionWithUnexpectedFailure(nodeExecution.getUuid(), ambiance);
      }
      return;
    }
    processOrQueueAdvisingEvent(updatedNodeExecution, planNode, nodeExecution.getStatus());
  }

  @VisibleForTesting
  PreFacilitationExecutionCheck performPreFacilitationChecks(Ambiance ambiance, PlanNode planNode) {
    // Ignore facilitation checks if node is retried
    if (AmbianceUtils.isRetry(ambiance)
        && pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), PIPE_SKIP_EXECUTE_WHEN_CONDITION_ON_RETRY_STEP)) {
      return PreFacilitationExecutionCheck.builder().proceed(true).reason("Node is retried.").build();
    }
    RunPreFacilitationChecker rChecker = injector.getInstance(RunPreFacilitationChecker.class);
    return rChecker.check(ambiance, planNode);
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    try {
      StepResponseProto.Builder builder = StepResponseProto.newBuilder().setStatus(Status.FAILED);
      List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(exception);
      if (isNotEmpty(responseMessages)) {
        builder.setFailureInfo(EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages));
      }
      handleStepResponseInternal(ambiance, builder.build());
    } catch (Exception ex) {
      // Smile if you see irony in this
      log.error("This is very BAD!!!. Exception Occurred while handling Exception. Erroring out Execution", ex);
    }
  }

  @Override
  public PmsNodeExecutionMetadata createMetadata(StrategyMetadata strategyMetadata) {
    return NodeExecutionMetadata.builder().strategyMetadata(strategyMetadata).build();
  }

  @VisibleForTesting
  void processDependencyGraphExecution(Ambiance ambiance, PlanNode planNode) {
    DependencyGraphProto dependencyGraph = planNode.getDependencyGraph();
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String nodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);

    log.info("Processing dependency graph execution for stagesNodeExecutionId: {}", nodeExecutionId);

    try {
      // Convert proto to map for easier processing
      Map<String, List<String>> dependencyMap = DependencyUtils.convertDependencyGraphToMap(dependencyGraph);

      // 1. Register wait instances for stage dependencies
      dagExecutionService.registerDependencyWaitInstances(dependencyMap, nodeExecutionId, ambiance, nodeId);

      // 2. Calculate leaf nodes (stages that no other stage depends on)
      List<String> leafNodeIds = DependencyUtils.calculateLeafNodes(dependencyMap);
      log.info("Calculated leaf nodes: {} for stagesNodeExecutionId: {}", leafNodeIds, nodeExecutionId);

      // 3. Register wait instances for stages node to wait on leaf nodes
      dagExecutionService.registerLeafNodesWaitInstances(ambiance, leafNodeIds, nodeExecutionId);

      log.info("Successfully processed dependency graph execution for stagesNodeExecutionId: {}", nodeExecutionId);
    } catch (Exception ex) {
      log.error("Error processing dependency graph execution for stagesNodeExecutionId: {}", nodeExecutionId, ex);
      handleError(ambiance, ex);
    }
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
