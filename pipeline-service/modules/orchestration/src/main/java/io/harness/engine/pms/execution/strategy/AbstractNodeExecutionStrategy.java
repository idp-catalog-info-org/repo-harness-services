/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.concurrency.StepConcurrencyHelper;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterGate;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueEntry;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.advise.utils.NodeAdviserUtils;
import io.harness.engine.pms.execution.SdkResponseProcessorFactory;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceModifier;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceModifierFactory;
import io.harness.engine.pms.execution.strategy.node.NodeExecutionStrategy;
import io.harness.event.handlers.SdkResponseProcessor;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.execution.RunNodeRequest;
import io.harness.logging.AutoLogContext;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public abstract class AbstractNodeExecutionStrategy<P extends Node, M extends PmsNodeExecutionMetadata>
    implements NodeExecutionStrategy<P, NodeExecution, M> {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private SdkResponseProcessorFactory sdkResponseProcessorFactory;
  @Inject private NodeAdviseHelper nodeAdviseHelper;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private AmbianceModifierFactory ambianceModifierFactory;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private StepConcurrencyHelper stepConcurrencyService;
  @Inject private StepConcurrencyCounterGate stepConcurrencyCounterGate;
  @Inject private StepConcurrencyQueueService stepConcurrencyQueueService;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject @Named("SdkResponseExecutorService") private ExecutorService sdkResponseExecutorService;
  @Inject @Named("publishAdviserEventForCustomAdvisers") private boolean publishAdviserEventForCustomAdvisers;
  @Override
  public NodeExecution runNode(@NonNull Ambiance ambiance, @NonNull P node, M metadata) {
    return runNode(ambiance, node, metadata, InitiateMode.CREATE_AND_START);
  }

  @Override
  public NodeExecution runNode(@NonNull Ambiance ambiance, @NonNull P node, M metadata, InitiateMode initiateMode) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String parentId = AmbianceUtils.obtainParentRuntimeId(ambiance);
      String notifyId = parentId == null ? null : AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      if (initiateMode == InitiateMode.CREATE) {
        return createNodeExecution(ambiance, node, metadata, notifyId, parentId, null);
      }
      return createAndRunNodeExecution(ambiance, node, metadata, notifyId, parentId, null);
    } catch (Exception ex) {
      log.error("Exception happened while running Node", ex);
      handleError(ambiance, ex);
      return null;
    }
  }

  @Override
  public List<NodeExecution> runNodes(RunNodeBatchRequest runNodeBatchRequest, InitiateMode initiateMode) {
    Ambiance parentAmbiance = runNodeBatchRequest.getParentAmbiance();
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(parentAmbiance)) {
      if (initiateMode == InitiateMode.CREATE) {
        return createNodeExecutions(runNodeBatchRequest);
      }
      throw new InvalidRequestException(
          String.format("Invalid mode %s not supported in run nodes batch request", initiateMode));
    } catch (Exception ex) {
      log.error("Exception happened while running Node", ex);
      handleError(parentAmbiance, ex);
      return null;
    }
  }

  @Override
  // PrevExecution doesn't contain fields for nextId and endTs, if needed handle for projection in NextStepHandler
  public NodeExecution runNextNode(
      @NonNull Ambiance ambiance, @NonNull P node, NodeExecution prevExecution, M metadata) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      return createAndRunNextNodeExecution(
          ambiance, node, metadata, prevExecution.getNotifyId(), prevExecution.getParentId(), prevExecution.getUuid());
    } catch (Exception ex) {
      log.error("Exception happened while running next Node", ex);
      handleError(ambiance, ex);
      return null;
    }
  }

  @VisibleForTesting
  NodeExecution createAndRunNextNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId) {
    NodeExecution savedExecution =
        createNodeExecution(ambiance, node, metadata, notifyId, parentId, previousId, InitiateMode.CREATE_AND_START);
    executorService.submit(
        () -> orchestrationEngine.queueOrStartExecution(nodeExecutionService.getAmbiance(savedExecution)));
    return savedExecution;
  }

  NodeExecution createAndRunNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId) {
    NodeExecution savedExecution =
        createNodeExecution(ambiance, node, metadata, notifyId, parentId, previousId, InitiateMode.CREATE_AND_START);
    orchestrationEngine.queueOrStartExecution(nodeExecutionService.getAmbiance(savedExecution));
    return savedExecution;
  }

  @Override
  public void handleSdkResponseEvent(SdkResponseEventProto event) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(event.getAmbiance(), event.getSdkResponseEventType())) {
      SdkResponseProcessor handler = sdkResponseProcessorFactory.getHandler(event.getSdkResponseEventType());
      handler.handleEvent(event);
      log.info("Event for SdkResponseEvent for event type {} completed successfully", event.getSdkResponseEventType());
    } catch (Exception ex) {
      log.error("Exception happened during Sdk Response Event handling", ex);
      handleError(event.getAmbiance(), ex);
    }
  }

  public void processOrQueueAdvisingEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    // TODO: This should always go to async flow, keeping for rollback capabilities for few releases
    if (!publishAdviserEventForCustomAdvisers || NodeAdviserUtils.hasCustomAdviser(planNode)) {
      nodeAdviseHelper.queueAdvisingEvent(nodeExecution, planNode, fromStatus);
    } else {
      SdkResponseEventProto responseEventProto =
          nodeAdviseHelper.getResponseInCaseOfNoCustomAdviser(nodeExecution, planNode, fromStatus);
      if (responseEventProto != null) {
        handleSdkResponse(responseEventProto);
      }
    }
  }

  private void handleSdkResponse(SdkResponseEventProto sdkResponseEventProto) {
    if (sdkResponseEventProto.getSdkResponseEventType() == SdkResponseEventType.HANDLE_EVENT_ERROR) {
      handleSdkResponseEvent(sdkResponseEventProto);
    } else {
      sdkResponseExecutorService.submit(() -> handleSdkResponseEvent(sdkResponseEventProto));
    }
  }

  public NodeExecution createNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId) {
    return createNodeExecution(ambiance, node, metadata, notifyId, parentId, previousId, InitiateMode.CREATE);
  }

  public NodeExecution createNodeExecution(Ambiance ambiance, P node, M metadata, String notifyId, String parentId,
      String previousId, InitiateMode initiateMode) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    AmbianceModifier ambianceModifier = null;
    if (currentLevel != null && currentLevel.getStepType() != null) {
      ambianceModifier = ambianceModifierFactory.obtainModifier(currentLevel.getStepType().getStepCategory());
    }
    Ambiance modifiedAmbiance = ambiance;
    if (ambianceModifier != null) {
      modifiedAmbiance = ambianceModifier.modify(ambiance, planExecutionService);
    }
    return createNodeExecutionInternal(modifiedAmbiance, node, metadata, notifyId, parentId, previousId, initiateMode);
  }

  public List<NodeExecution> createNodeExecutions(RunNodeBatchRequest runNodeBatchRequest) {
    for (RunNodeRequest request : runNodeBatchRequest.getNodes()) {
      Ambiance ambiance = request.getAmbiance();
      Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
      AmbianceModifier ambianceModifier = null;
      if (currentLevel != null) {
        ambianceModifier = ambianceModifierFactory.obtainModifier(currentLevel.getStepType().getStepCategory());
      }
      if (ambianceModifier != null) {
        Ambiance modifiedAmbiance = ambianceModifier.modify(ambiance, planExecutionService);
        request.setAmbiance(modifiedAmbiance);
      }
    }
    return createNodeExecutionInternal(runNodeBatchRequest);
  }

  public abstract NodeExecution createNodeExecutionInternal(Ambiance ambiance, P node, M metadata, String notifyId,
      String parentId, String previousId, InitiateMode initiateMode);

  public abstract List<NodeExecution> createNodeExecutionInternal(RunNodeBatchRequest runNodeBatchRequest);

  @Override
  public void queueOrStartExecution(Ambiance ambiance) {
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_STEP_CONCURRENCY_ENABLED.name())) {
      NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), Sets.newHashSet(NodeExecutionKeys.mode));
      if (stepConcurrencyService.shouldQueue(nodeExecution.getMode(), ambiance)) {
        nodeExecutionService.updateStatusWithOps(
            AmbianceUtils.obtainCurrentRuntimeId(ambiance), Status.QUEUED_STEP_LIMIT_REACHED, null, null);
        log.warn("[STEP_CONCURRENCY]: Marking as queued");
        return;
      }
      // Additive counter-based gate. Runs only when the counter-gate FF is on for the account.
      // Both gates must pass — per-plan already did above; if the counter gate wants to queue,
      // write Postgres first then flip Mongo (compensating delete on Mongo failure keeps them
      // in sync).
      if (AmbianceUtils.checkIfFeatureFlagEnabled(
              ambiance, FeatureName.PIPE_USE_COUNTER_BASED_STEP_CONCURRENCY_GATE.name())) {
        StepConcurrencyCounterGate.ThrottleDecision decision =
            stepConcurrencyCounterGate.shouldQueueWithReason(nodeExecution.getMode(), ambiance);
        if (decision.isQueue()) {
          queueOnCounterGate(ambiance, decision);
          return;
        }
      }
      if (ExecutionModeUtils.isLeafMode(nodeExecution.getMode())) {
        nodeExecutionService.updateStatusWithOps(AmbianceUtils.obtainCurrentRuntimeId(ambiance),
            Status.STARTING_QUEUED_STEP, null, EnumSet.of(Status.QUEUED, Status.QUEUED_STEP_LIMIT_REACHED));
      }
    }
    startExecution(ambiance);
  }

  /**
   * Given a queuing decision from the counter-based gate, inserts a Postgres queue row first, then
   * flips Mongo status to {@code QUEUED_STEP_LIMIT_REACHED}. Compensates the queue insert on Mongo
   * failure so the queue store never carries a row without a matching QSLR Mongo doc.
   *
   * <p>Callers must ensure {@code decision.isQueue()} is true before invoking this method.
   */
  private void queueOnCounterGate(Ambiance ambiance, StepConcurrencyCounterGate.ThrottleDecision decision) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    StepConcurrencyQueueEntry entry = StepConcurrencyQueueEntry.builder()
                                          .nodeExecutionId(nodeExecutionId)
                                          .planExecutionId(ambiance.getPlanExecutionId())
                                          .accountId(accountId)
                                          .createdAt(Instant.now())
                                          .build();
    // Write ordering: Postgres first, Mongo second. insert() is best-effort (swallows on
    // Postgres blip so orchestration is not blocked) — an orphaned Mongo QSLR row without a
    // matching queue-store row is drainable via tier-1 same-plan dequeue on the next completion.
    stepConcurrencyQueueService.insert(entry);
    try {
      nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.QUEUED_STEP_LIMIT_REACHED, null, null);
    } catch (Exception ex) {
      // Compensating delete: keep the queue store in sync with the failed Mongo flip. Swallow any
      // failure here so the original Mongo exception (the real cause) is always what propagates.
      try {
        stepConcurrencyQueueService.deleteByNodeExecutionId(nodeExecutionId);
      } catch (Exception compensationEx) {
        log.warn("[STEP_CONCURRENCY]: compensating delete failed for nodeExecutionId={} after Mongo flip failure",
            nodeExecutionId, compensationEx);
      }
      throw ex;
    }
    log.warn("[STEP_CONCURRENCY]: counter-gate queued reason={} current={} limit={} account={}", decision.getReason(),
        decision.getCurrentCount(), decision.getLimit(), accountId);
  }

  public void startQueuedExecutionIfAny(Ambiance ambiance) {
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_STEP_CONCURRENCY_ENABLED.name())) {
      NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), Sets.newHashSet(NodeExecutionKeys.mode));
      startQueuedExecutionIfAny(nodeExecution, ambiance);
    }
  }

  public void startQueuedExecutionIfAny(NodeExecution nodeExecution, Ambiance ambiance) {
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_STEP_CONCURRENCY_ENABLED.name())) {
      if (stepConcurrencyService.shouldStartQueuedStep(nodeExecution.getMode(), ambiance)) {
        String planExecutionId = ambiance.getPlanExecutionId();
        Ambiance queuedAmbiance = stepConcurrencyService.findQueuedNode(planExecutionId);
        if (queuedAmbiance != null) {
          executorService.submit(() -> orchestrationEngine.startNodeExecution(queuedAmbiance));
        }
      }
    }
  }
}
