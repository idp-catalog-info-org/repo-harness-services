/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.QUEUED_EXECUTION_CONCURRENCY_REACHED;

import io.harness.ModuleType;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.engine.GovernanceService;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterMutationHook;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.engine.pms.execution.strategy.node.NodeExecutionStrategy;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionBuilder;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.PriorityType;
import io.harness.execution.RunNodeBatchRequest;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.governance.GovernanceMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.observer.Subject;
import io.harness.opaclient.model.ActionContext;
import io.harness.opaclient.model.OpaConstants;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.execution.ExecutionModeUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionStrategy
    implements NodeExecutionStrategy<Plan, PlanExecution, PlanExecutionMetadataWithContext> {
  public static final String ENFORCEMENT_CALLBACK_ID = "enforcement-%s";
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private TransactionHelper transactionHelper;
  @Inject private GovernanceService governanceService;
  @Inject private PlanService planService;

  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject private PlanConcurrencyCounterMutationHook planConcurrencyCounterMutationHook;

  @Getter private final Subject<OrchestrationStartObserver> orchestrationStartSubject = new Subject<>();
  @Getter private final Subject<OrchestrationEndObserver> orchestrationEndSubject = new Subject<>();
  @Inject private PlanExpansionService planExpansionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public PlanExecution runNode(
      @NonNull Ambiance ambiance, @NonNull Plan plan, PlanExecutionMetadataWithContext metadataWithContext) {
    return runNode(ambiance, plan, metadataWithContext, InitiateMode.CREATE_AND_START);
  }

  @Override
  public PlanExecution runNode(@NonNull Ambiance ambiance, @NonNull Plan plan,
      PlanExecutionMetadataWithContext metadataWithContext, InitiateMode initiateMode) {
    long startTs = System.currentTimeMillis();
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String accountId = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
      String orgIdentifier = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier);
      String projectIdentifier = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier);
      String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
      String expandedPipelineJson = metadataWithContext.getExpandedPipelineJson();
      if (OrchestrationUtils.checkAsyncPlanCreation(AmbianceUtils.checkIfFeatureFlagEnabled(ambiance,
                                                        FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION.name()),
              AmbianceUtils.checkIfFeatureFlagEnabled(
                  ambiance, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS.name()),
              ambiance.getMetadata().getTriggerInfo(), metadataWithContext.getIsAsyncPlanCreation())) {
        return updateAndStartPlanExecution(
            ambiance, plan, metadataWithContext, accountId, expandedPipelineJson, orgIdentifier, projectIdentifier);
      }
      PlanExecution planExecution;
      PlanExecutionSettingResponse planExecutionSettingResponse = resolveQueueDecision(accountId, parentUniqueId);

      // Check if the number of queued executions are beyond a certain limit, throw an error
      if (planExecutionSettingResponse.isShouldQueue()
          && !pipelineSettingsService.isQueuedExecutionsWithinLimit(accountId)) {
        log.warn("[QUEUED_PIPELINE_LIMIT_EXCEEDED]: Not starting the planExecution with planExecutionId: {} because "
                + "the queue limit is exceeded for the account {}.",
            ambiance.getPlanExecutionId(), accountId);
        throw new LimitExceededException("You have exceeded the number of queued executions allowed on the account. "
            + "Please upgrade your plan or contact harness support.");
      }
      // Build OPA evaluation metadata
      boolean isRerun = AmbianceUtils.isRerunExecution(ambiance);
      ActionContext actionContext =
          ActionContext.builder().rerun(isRerun).executionId(AmbianceUtils.getRunSequence(ambiance)).build();
      GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(expandedPipelineJson,
          accountId, orgIdentifier, projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN,
          ambiance.getPlanExecutionId(), ambiance.getMetadata().getHarnessVersion(), actionContext);

      planExecution = createPlanExecution(ambiance, metadataWithContext, governanceMetadata,
          planExecutionSettingResponse, accountId, orgIdentifier, projectIdentifier);
      if (governanceMetadata.getDeny()) {
        log.info("Not starting the planExecution with planExecutionId: {} because the governance check denied the "
                + "execution.",
            ambiance.getPlanExecutionId());
        return planExecutionService.markPlanExecutionErrored(ambiance.getPlanExecutionId());
      }

      // isNewFlow: for restrictions using the enforcements.
      if (planExecutionSettingResponse.isUseNewFlow() || planExecutionSettingResponse.isShouldQueue()) {
        // Attach a Callback so that if this finishes then next execution starts
        PlanExecutionResumeCallback callback = PlanExecutionResumeCallback.builder()
                                                   .accountIdIdentifier(accountId)
                                                   .orgIdentifier(orgIdentifier)
                                                   .projectIdentifier(projectIdentifier)
                                                   .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
                                                   .build();

        waitNotifyEngine.waitForAllOn(
            publisherName, callback, String.format(ENFORCEMENT_CALLBACK_ID, planExecution.getUuid()));
      }

      if (!planExecutionSettingResponse.isShouldQueue()) {
        // Start the planExecution if it should not be queued.
        startPlanExecution(plan, ambiance);
      } else {
        log.info("Queuing execution with planExecutionId {} as maximum number of allowed concurrent executions for the "
                + "account has been reached",
            planExecution.getUuid());
      }
      return planExecution;
    } finally {
      log.info("[PMS_PlanExecution] Time taken to runNode plan in PlanExecutionStrategy: {} ",
          System.currentTimeMillis() - startTs);
    }
  }

  @Override
  public List<PlanExecution> runNodes(RunNodeBatchRequest nodeBatchRequest, InitiateMode initiateMode) {
    throw new InvalidRequestException("PlanExecutions can not be created in batched mode");
  }

  private PlanExecution updateAndStartPlanExecution(@NonNull Ambiance ambiance, @NonNull Plan plan,
      PlanExecutionMetadataWithContext metadataWithContext, String accountId, String expandedPipelineJson,
      String orgIdentifier, String projectIdentifier) {
    // Build OPA evaluation metadata
    boolean isRerun = AmbianceUtils.isRerunExecution(ambiance);
    ActionContext actionContext =
        ActionContext.builder().rerun(isRerun).executionId(AmbianceUtils.getRunSequence(ambiance)).build();
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(expandedPipelineJson,
        accountId, orgIdentifier, projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN,
        ambiance.getPlanExecutionId(), ambiance.getMetadata().getHarnessVersion(), actionContext);
    PlanExecution planExecution = updatePlanExecution(ambiance, metadataWithContext, governanceMetadata);
    if (governanceMetadata.getDeny()) {
      log.info(
          "Not starting the planExecution with planExecutionId: {} because the governance check denied the execution.",
          ambiance.getPlanExecutionId());
      return planExecutionService.markPlanExecutionErrored(ambiance.getPlanExecutionId());
    }
    startPlanExecution(plan, ambiance);
    return planExecution;
  }

  private PlanExecution updatePlanExecution(Ambiance ambiance,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, GovernanceMetadata governanceMetadata) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();
    Status status = Status.RUNNING;
    PlanExecution updatedPlanExecution = transactionHelper.performTransaction(() -> {
      planExecutionMetadataService.updatePlanExecutionMetadata(ambiance.getPlanExecutionId(),
          ops
          -> ops.set(
              PlanExecutionMetadataKeys.executionInputConfigured, planExecutionMetadata.getExecutionInputConfigured()));
      planExpansionService.create(ambiance.getPlanExecutionId());
      return planExecutionService.updateStatus(ambiance.getPlanExecutionId(), status, ops -> {
        ops.set(PlanExecutionKeys.planId, ambiance.getPlanId());
        ops.set(PlanExecutionKeys.governanceMetadata, governanceMetadata);
        ops.set(PlanExecutionKeys.ambiance, ambiance);
      });
    });
    waitForObserverResponse(ambiance, planExecutionMetadataWithContext, status);
    return updatedPlanExecution;
  }

  /**
   * Legacy partition-based queue decision (Mongo count). Only reached when
   * PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION is disabled. Per-project concurrency logic lives in the
   * async plan creation path instead.
   */
  private PlanExecutionSettingResponse resolveQueueDecision(String accountId, String parentUniqueId) {
    return pipelineSettingsService.shouldQueuePlanExecution(accountId);
  }

  private void waitForObserverResponse(
      Ambiance ambiance, PlanExecutionMetadataWithContext planExecutionMetadataWithContext, Status status) {
    try {
      orchestrationStartSubject.fireInform(OrchestrationStartObserver::onStartWrapper,
          OrchestrationStartInfo.builder()
              .ambiance(ambiance)
              .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
              .startStatus(status)
              .build());
    } catch (Exception e) {
      // Marking the planExecution Errored if OrchestrationStartObservers failed.
      planExecutionService.markPlanExecutionErrored(ambiance.getPlanExecutionId());
      log.error("Not starting the PlanExecution:", e);
      throw e;
    }
  }

  public boolean startPlanExecution(Plan plan, Ambiance ambiance) {
    Node planNode = planService.fetchNode(plan.getUuid(), plan.getStartingNodeId());
    if (planNode == null) {
      throw new InvalidRequestException("Starting node for plan cannot be null");
    }
    Ambiance cloned = AmbianceUtils.cloneForChild(ambiance, PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode));
    executorService.submit(() -> orchestrationEngine.runNode(cloned, planNode, null));
    return true;
  }

  private PlanExecution createPlanExecution(Ambiance ambiance,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, GovernanceMetadata governanceMetadata,
      PlanExecutionSettingResponse planExecutionSettingResponse, String accountIdentifier, String orgIdentifier,
      String projectIdentifier) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();
    // Will start the planExecution with running status if its not being queued.
    Status status = Status.RUNNING;
    if (planExecutionSettingResponse.isShouldQueue()) {
      status = QUEUED_EXECUTION_CONCURRENCY_REACHED;
    }
    PlanExecutionBuilder planExecutionBuilder = PlanExecution.builder()
                                                    .uuid(ambiance.getPlanExecutionId())
                                                    .planId(ambiance.getPlanId())
                                                    .setupAbstractions(ambiance.getSetupAbstractionsMap())
                                                    .status(status)
                                                    .startTs(System.currentTimeMillis())
                                                    .governanceMetadata(governanceMetadata)
                                                    .metadata(ambiance.getMetadata())
                                                    .ambiance(ambiance);

    PriorityType priorityType =
        pipelineSettingsService.getPriorityTypeOfCurrentExecution(accountIdentifier, orgIdentifier, projectIdentifier,
            pmsFeatureFlagService.isEnabled(
                accountIdentifier, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()));
    planExecutionBuilder.priorityType(priorityType);

    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())) {
      planExecutionBuilder.triggerHeader(planExecutionMetadataWithContext.getTriggerHeader())
          .triggerJsonPayload(planExecutionMetadataWithContext.getTriggerJsonPayload())
          .expressionFunctorToken(planExecutionMetadataWithContext.getExpressionFunctorToken())
          .triggerPayload(planExecutionMetadataWithContext.getTriggerPayload())
          .stageExpressionValuesMap(planExecutionMetadataWithContext.getStageExpressionValuesMap())
          .stagesExecutionMetadata(planExecutionMetadataWithContext.getStagesExecutionMetadata())
          .processedYaml(planExecutionMetadataWithContext.getProcessedYaml())
          .postExecutionRollbackInfos(planExecutionMetadataWithContext.getPostExecutionRollbackInfos());
    }

    PlanExecution planExecution = planExecutionBuilder.build();

    PlanExecution createdPlanExecution = transactionHelper.performTransaction(() -> {
      planExecutionMetadataService.save(planExecutionMetadata);
      planExpansionService.create(planExecution.getUuid());
      return planExecutionService.save(planExecution);
    });

    // Post-commit: increment counter for direct-start executions (null → RUNNING/QUEUED transition).
    // Queued executions will get their +1 later when drained via updateStatus(..., RUNNING).
    // Direct-start executions bypass updateStatus, so we must increment here to prevent under-counting.
    // See: PIPE-35674 review comment re: counter drift on direct-start path.
    maybeMutatePlanConcurrencyCounterOnCreate(createdPlanExecution, status);

    waitForObserverResponse(ambiance, planExecutionMetadataWithContext, status);
    return createdPlanExecution;
  }

  /**
   * Increment the per-project and per-account concurrency counters for a newly-created execution.
   * Only applies to direct-start executions (status = RUNNING or other active statuses at creation).
   *
   * <p><b>Why needed:</b> Direct-start executions are created via {@code planExecutionService.save()},
   * which bypasses {@code updateStatus()} and never fires the mutation hook's entry +1. Queued
   * executions get their +1 later when drained via {@code updateStatus(..., RUNNING)}. Without this
   * call, direct-start executions only get the terminal -1, causing the counter to drift negative and
   * the gate to under-throttle.
   *
   * <p><b>Hook contract:</b> {@code onStatusChange(oldStatus=null, newStatus)} means "execution
   * created directly into newStatus". The hook classifies null → RUNNING as an entry (+1).
   */
  private void maybeMutatePlanConcurrencyCounterOnCreate(PlanExecution planExecution, Status status) {
    try {
      if (!planConcurrencyCounterMutationHook.isEnabled()) {
        return;
      }
      Map<String, String> setupAbstractions = planExecution.getSetupAbstractions();
      String accountId = setupAbstractions == null ? null : setupAbstractions.get(SetupAbstractionKeys.accountId);
      if (accountId == null
          || !pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.name())) {
        return;
      }
      // Only mutate if the execution was created in an active status (RUNNING, TASK_WAITING, etc.).
      // Queued executions (QUEUED_*) will get their +1 later when drained via updateStatus.
      if (StatusUtils.activeStatuses().contains(status)) {
        planConcurrencyCounterMutationHook.onStatusChange(setupAbstractions, null, status);
      }
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] failed to mutate counter on create for {}", planExecution.getUuid(), ex);
    }
  }

  @Override
  public void endNodeExecution(Ambiance ambiance, NodeExecution nodeExecution, List<StepOutcomeRef> outcomeRefs) {
    Status status = planExecutionService.calculateStatus(
        ambiance.getPlanExecutionId(), ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode()));
    PlanExecution planExecution = planExecutionService.updateStatus(
        ambiance.getPlanExecutionId(), status, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
    if (planExecution == null) {
      log.error("Cannot transition plan execution to status : {}", status);
      // TODO: Incorporate error handling
      planExecution = planExecutionService.updateStatus(
          ambiance.getPlanExecutionId(), ERRORED, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
    }
    if (planExecution != null) {
      OrchestrationEvent.Builder endEventBuilder = buildEndEvent(ambiance, planExecution.getStatus());
      if (StatusUtils.isFailedStatus(planExecution.getStatus())
          && !pmsFeatureFlagService.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_DISABLE_ADD_FAILURE_INFO_END_EVENT.name())) {
        Optional<NodeExecution> effectiveNodeExecution = nodeExecutionService.getPipelineNodeExecutionWithProjections(
            ambiance.getPlanExecutionId(), Collections.singleton(NodeExecutionKeys.failureInfo));
        if (effectiveNodeExecution.isPresent() && effectiveNodeExecution.get().getFailureInfo() != null) {
          endEventBuilder.setFailureInfo(effectiveNodeExecution.get().getFailureInfo());
        }
      }
      eventEmitter.emitEvent(endEventBuilder.build());
    }
    orchestrationEndSubject.fireInform(OrchestrationEndObserver::onEnd, ambiance, status);
  }

  private OrchestrationEvent.Builder buildEndEvent(Ambiance ambiance, Status status) {
    return OrchestrationEvent.newBuilder()
        .setAmbiance(ambiance)
        .setServiceName(ModuleType.PMS.name().toLowerCase())
        .setEventType(OrchestrationEventType.ORCHESTRATION_END)
        .setStatus(status);
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    // TODO: Add implementation here
  }
}
