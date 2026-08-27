/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.execution.ExecutionModeUtils.isRollbackMode;

import static java.lang.String.format;

import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterMutationHook;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueEntry;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueService;
import io.harness.engine.executions.plan.service.PlanCreationQueueRequestService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.DynamicOrchestrationStartInfo;
import io.harness.engine.observers.beans.OrchestrationQueueInfo;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.PlanCreatorException;
import io.harness.exception.WingsException;
import io.harness.execution.ExecutionPlan;
import io.harness.execution.PlanCreationQueueRequest;
import io.harness.execution.PlanCreationRequest;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionBuilder;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.PriorityType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse.HsqsProcessMessageResponseBuilder;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.AccountIdContext;
import io.harness.observer.Subject;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.opaclient.model.OpaConstants;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.branchsequence.BranchSequenceResult;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.BranchSequenceService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.PlanExecutionUtils;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.SetupAbstractionUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.runnercommons.cgi.utils.UnifiedConditionChecker;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PlanCreationQueueRequestHelper {
  /**
   * Result of processing a queued plan creation request, transport-agnostic.
   */
  public enum ProcessResult {
    /** Successfully processed; delete from queue. */
    PROCESSED,
    /** Should be requeued for later retry; leave in queue. */
    REQUEUE,
    /** Should be dropped (already processed, aborted, expired); delete from queue. */
    DROP
  }

  /**
   * Why a candidate was requeued, so the Postgres drainer can skip the rest of a full scope in the
   * same batch. Only meaningful when {@link ProcessResult#REQUEUE}; {@link #NONE} otherwise.
   *
   * <ul>
   *   <li>{@code PROJECT_FULL} — per-project cap hit; skip other entries of this (account, project).</li>
   *   <li>{@code ACCOUNT_FULL} — account total hit; skip every entry of this account.</li>
   *   <li>{@code OTHER} — legacy High/Low gate, a fail-closed Redis blip, or a lost reserve race:
   *       the scope is NOT safe to cache, so each such entry is re-evaluated on its own.</li>
   * </ul>
   */
  public enum RequeueReason { PROJECT_FULL, ACCOUNT_FULL, OTHER, NONE }

  /**
   * Result of processing one queued plan creation plus the info the drainer needs to cache a full
   * scope: the requeue reason and the resolved parentUniqueId (the transport may not have carried
   * it). Cache-only metadata — the hsqs path ignores it.
   */
  @Value
  public static class ProcessOutcome {
    ProcessResult result;
    RequeueReason requeueReason;
    String resolvedParentUniqueId;

    public static ProcessOutcome of(ProcessResult result) {
      return new ProcessOutcome(result, RequeueReason.NONE, null);
    }

    public static ProcessOutcome requeue(RequeueReason reason, String resolvedParentUniqueId) {
      return new ProcessOutcome(ProcessResult.REQUEUE, reason, resolvedParentUniqueId);
    }
  }

  @Inject PipelineMetadataService pipelineMetadataService;
  @Inject BranchSequenceService branchSequenceService;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PlanCreatorMergeService planCreatorMergeService;
  @Inject private NodeTypeLookupService nodeTypeLookupService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private MetricService metricService;
  @Inject private RetryExecutionHelper retryExecutionHelper;
  @Inject private RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanCreationQueueRequestService planCreationQueueRequestService;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject private PipelineGovernanceService pipelineGovernanceService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject @Named("queueServiceClientConfig") QueueServiceClientConfig queueServiceClientConfig;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private PlanCreationDbQueueService planCreationDbQueueService;
  @Inject @Named("useDbQueueForPlanCreation") private boolean useDbQueueForPlanCreation;
  @Inject private PlanConcurrencyGate planConcurrencyGate;

  @Inject private PlanService planService;
  @Inject private PipelineIdentityService pipelineIdentityService;
  private static final String planCreationTopic = "_plan_creation";
  private static final String highPriorityPlanCreationSubTopic = "_high_priority";
  private static final String lowPriorityPlanCreationSubTopic = "_low_priority";

  public static final String PLAN_CREATION_TIME_METRIC_NAME = "plan_creation_time";
  private static final String CI_STAGE = "CI";
  @Getter private final Subject<OrchestrationStartObserver> orchestrationStartSubject = new Subject<>();

  public PlanExecution savePlanExecutionAndQueuePlanExecutionRequest(PlanCreationRequest planCreationRequest) {
    String accountId = planCreationRequest.getAccountId();
    String orgIdentifier = planCreationRequest.getOrgIdentifier();
    String projectIdentifier = planCreationRequest.getProjectIdentifier();
    ExecutionMetadata executionMetadata = planCreationRequest.getExecutionMetadata();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        planCreationRequest.getPlanExecutionMetadataWithContext();
    ScopeInfo scopeInfo = planCreationRequest.getScopeInfo();
    Map<String, String> setupAbstractions = setupAbstractions(accountId, orgIdentifier, projectIdentifier, scopeInfo);

    // check hard limit for no. of queued executions
    checkQueuedExecutionsHardLimit(accountId, executionMetadata);

    // increment run sequence
    ExecutionMetadata.Builder executionMetadataBuilder = executionMetadata.toBuilder().setRunSequence(
        pipelineMetadataService.incrementRunSequence(accountId, orgIdentifier, projectIdentifier,
            executionMetadata.getPipelineIdentifier(), scopeInfo, planCreationRequest.isParentIdQueryingEnabled()));

    // increment branch sequence for trigger-based builds (CI-19987)
    incrementBranchSequenceIfApplicable(executionMetadataBuilder, accountId, orgIdentifier, projectIdentifier,
        executionMetadata.getPipelineIdentifier(), planExecutionMetadataWithContext, scopeInfo);

    ExecutionMetadata finalExecutionMetadata = executionMetadataBuilder.build();

    // create and save plan execution, planExecutionMetadata ,planExecutionSummary, planCreationQueueRequest and
    // dummyOrchestrationGraph
    PlanExecution createdPlanExecution = createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, finalExecutionMetadata, scopeInfo);

    // queue plan creation request
    queuePlanCreationRequest(createdPlanExecution);
    return createdPlanExecution;
  }

  private void checkQueuedExecutionsHardLimit(String accountId, ExecutionMetadata executionMetadata) {
    if (!pipelineSettingsService.isQueuedExecutionsWithinLimit(accountId)) {
      log.warn("[QUEUED_PIPELINE_LIMIT_EXCEEDED]: Not starting the planExecution with planExecutionId: {} because the "
              + "queue limit is exceeded for the account {}.",
          executionMetadata.getExecutionUuid(), accountId);
      throw new LimitExceededException("You have exceeded the number of queued executions allowed on the account. "
              + "Please upgrade your plan or contact harness support.",
          ErrorCode.TOO_MANY_REQUESTS);
    }
  }

  private void queuePlanCreationRequest(PlanExecution createdPlanExecution) {
    ExecutionMetadata executionMetadata = createdPlanExecution.getMetadata();
    Map<String, String> setupAbstractions = createdPlanExecution.getSetupAbstractions();
    String accountId = SetupAbstractionUtils.getAccountId(setupAbstractions);
    String orgId = SetupAbstractionUtils.getOrgIdentifier(setupAbstractions);
    String projectId = SetupAbstractionUtils.getProjectIdentifier(setupAbstractions);
    String parentUniqueId = setupAbstractions.get(SetupAbstractionKeys.parentUniqueId);
    PriorityType priorityType = createdPlanExecution.getPriorityType();

    // Transport toggle (PIPE-35674): Postgres FIFO queue vs legacy hsqs. Defaults to hsqs so
    // existing PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION customers are unaffected until deliberately
    // flipped. The Mongo PlanExecution (QUEUED_PLAN_CREATION) + PlanCreationQueueRequest doc are
    // saved identically by the caller regardless of transport; this only chooses the FIFO index.
    if (useDbQueueForPlanCreation) {
      queuePlanCreationRequestToDb(executionMetadata, accountId, orgId, projectId, parentUniqueId, priorityType);
      return;
    }
    queuePlanCreationRequestToHsqs(executionMetadata, accountId, priorityType);
  }

  private void queuePlanCreationRequestToHsqs(
      ExecutionMetadata executionMetadata, String accountId, PriorityType priorityType) {
    String topic = queueServiceClientConfig.getTopic() + planCreationTopic;
    String subTopic = getSubTopicBasedOnPriority(accountId, priorityType);
    String payload = RecastOrchestrationUtils.toJson(PlanCreationQueuePayload.builder()
                                                         .planExecutionId(executionMetadata.getExecutionUuid())
                                                         .accountId(accountId)
                                                         .priorityType(priorityType)
                                                         .build());
    EnqueueRequest enqueueRequest =
        EnqueueRequest.builder().topic(topic).subTopic(subTopic).producerName(topic).payload(payload).build();
    try {
      hsqsClientService.enqueue(enqueueRequest);
    } catch (Exception e) {
      log.info("failed to queue plan creation request", e);
      throw e;
    }
  }

  // Postgres FIFO enqueue. On failure we rethrow — matching the hsqs path's fail-loud behaviour so
  // the caller surfaces the error and the execution is not silently stranded in QUEUED_PLAN_CREATION.
  private void queuePlanCreationRequestToDb(ExecutionMetadata executionMetadata, String accountId, String orgId,
      String projectId, String parentUniqueId, PriorityType priorityType) {
    try {
      planCreationDbQueueService.insert(PlanCreationDbQueueEntry.builder()
                                            .planExecutionId(executionMetadata.getExecutionUuid())
                                            .accountId(accountId)
                                            .orgId(orgId)
                                            .projectId(projectId)
                                            .parentUniqueId(parentUniqueId)
                                            .priorityType(priorityType == null ? null : priorityType.name())
                                            .createdAt(java.time.Instant.now())
                                            .build());
    } catch (Exception e) {
      log.error("Failed to enqueue plan creation request to Postgres for planExecutionId: {}",
          executionMetadata.getExecutionUuid(), e);
      throw e;
    }
  }

  private String getSubTopicBasedOnPriority(String accountId, PriorityType priorityType) {
    String subTopic = accountId;
    // subtopic = accountId_high_priority / accountId_low_priority
    if (!priorityType.equals(PriorityType.NORMAL)) {
      subTopic +=
          priorityType.equals(PriorityType.HIGH) ? highPriorityPlanCreationSubTopic : lowPriorityPlanCreationSubTopic;
    }
    return subTopic;
  }

  private PlanCreationQueueRequest createPlanCreationQueueRequest(PlanCreationRequest planCreationRequest,
      Map<String, String> setupAbstractionsMap, ExecutionMetadata executionMetadata, ScopeInfo scopeInfo,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    String parentUniqueId = getParentUniqueId(setupAbstractionsMap, scopeInfo);

    return PlanCreationQueueRequest.builder()
        .accountId(SetupAbstractionUtils.getAccountId(setupAbstractionsMap))
        .orgId(SetupAbstractionUtils.getOrgIdentifier(setupAbstractionsMap))
        .projectId(SetupAbstractionUtils.getProjectIdentifier(setupAbstractionsMap))
        .planExecutionId(executionMetadata.getExecutionUuid())
        .isDebug(planCreationRequest.isDebug())
        .isParentIdQueryingEnabled(planCreationRequest.isParentIdQueryingEnabled())
        .scopeInfo(scopeInfo)
        .isRetry(planExecutionMetadataWithContext.isRetry())
        .previousExecutionId(planExecutionMetadataWithContext.getPreviousExecutionId())
        .retryStagesIdentifier(planExecutionMetadataWithContext.getRetryStagesIdentifier())
        .identifierOfSkipStages(planExecutionMetadataWithContext.getIdentifierOfSkipStages())
        .runAllStages(planExecutionMetadataWithContext.isRunAllStages())
        .pipelineYamlWithTemplateRef(planExecutionMetadataWithContext.getPipelineYamlWithTemplateRef())
        .isDynamicExecution(planExecutionMetadataWithContext.getIsDynamicExecution())
        .branch(GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata())
        .createdAt(System.currentTimeMillis())
        .parentUniqueId(parentUniqueId)
        .build();
  }

  @VisibleForTesting
  protected String getParentUniqueId(Map<String, String> setupAbstractionsMap, ScopeInfo scopeInfo) {
    String parentUniqueId = null;
    if (scopeInfo != null && scopeInfo.getUniqueId() != null) {
      parentUniqueId = scopeInfo.getUniqueId();
    }
    Optional<ScopeInfo> scopeInfoOptional =
        scopeResolutionHelper.getScopeInfoOptional(SetupAbstractionUtils.getAccountId(setupAbstractionsMap),
            SetupAbstractionUtils.getOrgIdentifier(setupAbstractionsMap),
            SetupAbstractionUtils.getProjectIdentifier(setupAbstractionsMap));
    if (scopeInfoOptional.isPresent()) {
      parentUniqueId = scopeInfoOptional.get().getUniqueId();
    }
    return parentUniqueId;
  }

  public PlanExecution createPlanExecution(PlanCreationRequest planCreationRequest,
      Map<String, String> setupAbstractionsMap, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ExecutionMetadata executionMetadata, ScopeInfo scopeInfo) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();
    // Register one workload token per pipeline-level identity (behind PIPE_PIPELINE_IDENTITY + HarnessID.isEnabled()).
    // Returns null when the feature is off or no identities are declared, in which case nothing is seeded on the root
    // ambiance and behavior is unchanged. A single identity's registration failure never fails plan creation (design
    // 4.8) - it is handled inside the service by keeping an empty token and emitting metrics.
    if (planExecutionMetadataWithContext.getIdentityExecutionContext() == null) {
      planExecutionMetadataWithContext.setIdentityExecutionContext(
          pipelineIdentityService.buildPipelineIdentityContext(planCreationRequest.getAccountId(),
              planCreationRequest.getOrgIdentifier(), planCreationRequest.getProjectIdentifier(),
              executionMetadata.getPipelineIdentifier(), planExecutionMetadata.getProcessedYaml()));
    }
    // Will start the planExecution with queued status
    Status status = Status.QUEUED_PLAN_CREATION; // new status
    PlanExecutionBuilder planExecutionBuilder =
        PlanExecution.builder()
            .uuid(executionMetadata.getExecutionUuid())
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(executionMetadata.getExecutionUuid())
                          .putAllSetupAbstractions(setupAbstractionsMap)
                          .setMetadata(executionMetadata)
                          .build())
            .setupAbstractions(setupAbstractionsMap)
            .status(status)
            .startTs(System.currentTimeMillis())
            .metadata(executionMetadata)
            .priorityType(pipelineSettingsService.getPriorityTypeOfCurrentExecution(planCreationRequest.getAccountId(),
                planCreationRequest.getOrgIdentifier(), planCreationRequest.getProjectIdentifier(),
                pmsFeatureFlagHelper.isEnabled(
                    planCreationRequest.getAccountId(), FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY)));

    PlanExecution planExecution = planExecutionBuilder.build();
    // create planCreationQueueRequest
    PlanCreationQueueRequest planCreationQueueRequest = createPlanCreationQueueRequest(
        planCreationRequest, setupAbstractionsMap, executionMetadata, scopeInfo, planExecutionMetadataWithContext);
    PlanExecution createdPlanExecution = transactionHelper.performTransaction(() -> {
      planExecutionMetadataService.save(planExecutionMetadata);
      // save planCreationQueueRequest
      planCreationQueueRequestService.save(planCreationQueueRequest);
      return planExecutionService.save(planExecution);
    });
    try {
      orchestrationStartSubject.fireInform(OrchestrationStartObserver::onQueue,
          OrchestrationQueueInfo.builder()
              .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
              .planExecution(createdPlanExecution)
              .scopeInfo(scopeInfo)
              .build());
    } catch (Exception e) {
      markPlanExecutionFailed(e, executionMetadata.getExecutionUuid());
      log.error("Not starting the PlanExecution:", e);
      throw e;
    }
    return createdPlanExecution;
  }

  /**
   * Process a queued plan creation request in a transport-agnostic way.
   * <p>
   * Called by both the hsqs consumer (via {@link #processMessage}) and the Postgres drainer.
   *
   * @param planExecutionId the plan execution ID
   * @param accountId the account ID
   * @param parentUniqueId the parent unique ID (project scope stable identifier), nullable
   * @param priorityType the priority type
   * @return PROCESSED if successfully handled (delete from queue), REQUEUE if over concurrency limit
   *     (retry later), DROP if already aborted/processed/expired (delete from queue)
   */
  @VisibleForTesting
  public ProcessResult processQueuedPlanCreation(
      String planExecutionId, String accountId, String parentUniqueId, PriorityType priorityType) {
    // Thin wrapper: the hsqs path and existing tests only care about the ProcessResult; the Postgres
    // drainer calls processQueuedPlanCreationWithOutcome to also learn the requeue reason.
    return processQueuedPlanCreationWithOutcome(planExecutionId, accountId, parentUniqueId, priorityType).getResult();
  }

  /**
   * Same as {@link #processQueuedPlanCreation} but also returns why a candidate was requeued and the
   * resolved parentUniqueId, so the Postgres drainer can cache a full scope for the rest of a batch.
   */
  public ProcessOutcome processQueuedPlanCreationWithOutcome(
      String planExecutionId, String accountId, String parentUniqueId, PriorityType priorityType) {
    // Kept in scope for the outer catch so a reserved slot is released if the flip throws.
    // flipSucceeded gates that release: once the flip succeeds the execution is active and its
    // terminal -1 frees the slot, so releasing here too would double-count.
    AdmissionDecision admission = null;
    boolean flipSucceeded = false;
    try {
      AccountIdContext.setAccountId(accountId);

      // Check if execution already aborted/completed — no need to process
      if (checkIfExecutionAlreadyAborted(planExecutionId)) {
        return ProcessOutcome.of(ProcessResult.DROP);
      }

      // In per-project ENFORCE this reserves the slot; otherwise it's a plain headroom check.
      admission = admitOrRequeue(planExecutionId, accountId, parentUniqueId, priorityType);
      if (admission.isRequeue()) {
        return ProcessOutcome.requeue(admission.getRequeueReason(), admission.getResolvedParentUniqueId());
      }

      // Flip to STARTING_PLAN_CREATION (prevents duplicate processing). If the slot was reserved, the
      // reserve owns the +1, so the flip suppresses the hook's increment to avoid double-counting.
      PlanExecution updatedPlanExecution = flipToStartingPlanCreation(planExecutionId, admission);
      if (updatedPlanExecution == null) {
        log.warn("Unable to start plan creation for planExecutionId : {} due to planExecution status update failure",
            planExecutionId);
        // CAS lost (another consumer advanced this execution). Release the reserved slot so it doesn't leak.
        admission.releaseIfReserved(planConcurrencyGate, accountId, parentUniqueId);
        return ProcessOutcome.of(ProcessResult.DROP);
      }
      flipSucceeded = true;

      try (PmsGitSyncBranchContextGuard ignore =
               pmsGitSyncHelper.createGitSyncBranchContextGuard(updatedPlanExecution.getAmbiance(), true);
           PmsSecurityContextEventGuard pmsSecurityContextEventGuard =
               new PmsSecurityContextEventGuard(updatedPlanExecution.getAmbiance());
           AutoLogContext ignore2 = AmbianceUtils.autoLogContext(updatedPlanExecution.getAmbiance())) {
        PlanCreationQueueRequest planCreationQueueRequest = planCreationQueueRequestService.get(planExecutionId);
        if (checkAndMarkPlanExecutionExpired(planCreationQueueRequest, planExecutionId)) {
          return ProcessOutcome.of(ProcessResult.DROP);
        }
        PlanExecutionMetadata planExecutionMetadata =
            planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId).get();
        PlanExecutionMetadataWithContext planExecutionMetadataWithContext = buildPlanExecutionMetadataWithContext(
            planCreationQueueRequest, planExecutionMetadata, updatedPlanExecution.getMetadata());
        executePlanCreationRequest(createPlanCreationRequest(
            accountId, planCreationQueueRequest, updatedPlanExecution, planExecutionMetadataWithContext));
      } catch (Exception e) {
        log.error("Exception while doing planCreation for planExecutionId : {}", planExecutionId, e);
        markPlanExecutionFailed(e, planExecutionId);
      }
      planCreationQueueRequestService.updateTTL(planExecutionId);
      return ProcessOutcome.of(ProcessResult.PROCESSED);
    } catch (Exception e) {
      log.error("Exception while processing plan creation request for planExecutionId : {}", planExecutionId, e);
      // A throw before the flip leaves the execution in QUEUED_PLAN_CREATION, so the hook never sees
      // an exit transition and the reserved +1 would leak. Release it here (skipped after a
      // successful flip, else its terminal -1 double-counts).
      if (!flipSucceeded && admission != null) {
        admission.releaseIfReserved(planConcurrencyGate, accountId, parentUniqueId);
      }
      if (planExecutionId != null) {
        markPlanExecutionFailed(e, planExecutionId);
      }
      return ProcessOutcome.of(ProcessResult.DROP);
    } finally {
      AccountIdContext.clearAccountId();
    }
  }

  public HsqsProcessMessageResponse processMessage(DequeueResponse message) {
    HsqsProcessMessageResponseBuilder hsqsProcessMessageResponseBuilder = HsqsProcessMessageResponse.builder();
    String planExecutionId = null;
    String subtopic = null;
    try {
      PlanCreationQueuePayload planCreationQueuePayload =
          RecastOrchestrationUtils.fromJson(message.getPayload(), PlanCreationQueuePayload.class);
      String accountId = planCreationQueuePayload.getAccountId();
      hsqsProcessMessageResponseBuilder.accountId(accountId);
      planExecutionId = planCreationQueuePayload.getPlanExecutionId();
      PriorityType priorityType = planCreationQueuePayload.getPriorityType();
      subtopic = getSubTopicBasedOnPriority(accountId, priorityType);

      // Delegate to transport-agnostic processing
      ProcessResult result = processQueuedPlanCreation(planExecutionId, accountId, null, priorityType);

      // Map ProcessResult to hsqs response: REQUEUE → success=false (requeue), PROCESSED/DROP → success=true (ack)
      boolean success = (result != ProcessResult.REQUEUE);
      return hsqsProcessMessageResponseBuilder.success(success).subtopic(subtopic).build();
    } catch (Exception e) {
      log.error("Exception while processing plan creation request for planExecutionId : {}", planExecutionId, e);
      // Outer exception (e.g., payload deserialization) — ack to avoid infinite retry
      return hsqsProcessMessageResponseBuilder.success(true).subtopic(subtopic).build();
    }
  }

  private boolean checkAndMarkPlanExecutionExpired(
      PlanCreationQueueRequest planCreationQueueRequest, String planExecutionId) {
    if (planCreationQueueRequest == null) {
      log.error("Unable to find planCreationQueueRequest for planExecutionId: {}. Execution might have been queued for "
              + "more than 30 days",
          planExecutionId);
      planExecutionService.updateStatus(planExecutionId, Status.EXPIRED,
          ops
          -> ops.set(PlanExecutionKeys.failureInfo,
              FailureInfo.newBuilder()
                  .setErrorMessage("Execution might have been queued for more than 30 days")
                  .addFailureData(
                      FailureData.newBuilder()
                          .addFailureTypeInfos(
                              FailureTypeInfo.newBuilder().setFailureType(FailureType.UNKNOWN_FAILURE).build())
                          .setCode(String.valueOf(ErrorCode.PLAN_CREATION_ERROR))
                          .setLevel(Level.ERROR.name())
                          .setMessage("Execution might have been queued for more than 30 days")
                          .build())
                  .build()));
      return true;
    }
    return false;
  }

  // If execution is already aborted we should not process this request and mark it is as done
  private boolean checkIfExecutionAlreadyAborted(String planExecutionId) {
    PlanExecution currPlanExecution =
        planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status));
    if (StatusUtils.isFinalStatus(currPlanExecution.getStatus())) {
      log.info("PlanExecution with planExecutionId: {} is already in final state: {}. No need to process again",
          planExecutionId, currPlanExecution.getStatus());
      return true;
    }
    return false;
  }

  /**
   * Outcome of the admission decision for one queued plan creation: whether to requeue, and whether a
   * slot was reserved (so the status-flip suppresses the hook's +1 and releases if the flip fails).
   */
  @VisibleForTesting
  static final class AdmissionDecision {
    private final boolean requeue;
    private final boolean slotReserved;
    private final String parentUniqueId;
    // On requeue, why (so the drainer can cache the full scope for the rest of the batch). NONE
    // unless requeue. The resolvedParentUniqueId is the scope key the gate actually used.
    private final RequeueReason requeueReason;
    private final String resolvedParentUniqueId;

    private AdmissionDecision(boolean requeue, boolean slotReserved, String parentUniqueId, RequeueReason requeueReason,
        String resolvedParentUniqueId) {
      this.requeue = requeue;
      this.slotReserved = slotReserved;
      this.parentUniqueId = parentUniqueId;
      this.requeueReason = requeueReason;
      this.resolvedParentUniqueId = resolvedParentUniqueId;
    }

    static AdmissionDecision requeue(RequeueReason requeueReason, String resolvedParentUniqueId) {
      return new AdmissionDecision(true, false, null, requeueReason, resolvedParentUniqueId);
    }

    /** Admitted without an atomic reserve (SHADOW/DISABLED/legacy gate) — hook applies the +1. */
    static AdmissionDecision admitWithoutReserve() {
      return new AdmissionDecision(false, false, null, RequeueReason.NONE, null);
    }

    /** Admitted with an atomic reserve owning the +1 (per-project ENFORCE). */
    static AdmissionDecision admitWithReserve(String parentUniqueId) {
      return new AdmissionDecision(false, true, parentUniqueId, RequeueReason.NONE, parentUniqueId);
    }

    boolean isRequeue() {
      return requeue;
    }

    boolean isSlotReserved() {
      return slotReserved;
    }

    RequeueReason getRequeueReason() {
      return requeueReason;
    }

    String getResolvedParentUniqueId() {
      return resolvedParentUniqueId;
    }

    void releaseIfReserved(PlanConcurrencyGate gate, String accountId, String resolvedParentUniqueId) {
      if (slotReserved) {
        gate.releaseReservedSlot(accountId, parentUniqueId != null ? parentUniqueId : resolvedParentUniqueId);
      }
    }
  }

  // Decide whether to admit this queued execution or requeue it. In per-project mode
  // (PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES + mode=PER_PROJECT) the ENFORCE gate atomically reserves
  // the slot so drainers on different pods can't each admit past the cap (PIPE-35674). Otherwise falls
  // back to the existing High/Low + account gate.
  @VisibleForTesting
  AdmissionDecision admitOrRequeue(
      String planExecutionId, String accountId, String parentUniqueId, PriorityType priorityType) {
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES)
        && pipelineSettingsService.getConcurrencyMode(accountId) == PlanExecutionConcurrencyMode.PER_PROJECT) {
      // The gate keys everything by parentUniqueId. Resolve it from the execution when the transport
      // didn't carry it (the hsqs payload has no scope).
      String resolvedParentUniqueId = parentUniqueId;
      if (resolvedParentUniqueId == null) {
        PlanExecution planExecution =
            planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.setupAbstractions));
        Map<String, String> setupAbstractions = planExecution == null ? null : planExecution.getSetupAbstractions();
        if (setupAbstractions != null) {
          resolvedParentUniqueId = setupAbstractions.get(SetupAbstractionKeys.parentUniqueId);
        }
      }
      // Atomic admission: RESERVED -> admit and own the +1; DENIED -> requeue (also the fail-closed
      // path on a Redis blip, so we never breach the cap); NOT_RESERVED (shadow/disabled) -> admit
      // and let the hook apply the +1 on the flip exactly as today.
      PlanConcurrencyGate.ReserveOutcome outcome =
          planConcurrencyGate.tryReserveSlot(accountId, resolvedParentUniqueId);
      switch (outcome) {
        case RESERVED:
          return AdmissionDecision.admitWithReserve(resolvedParentUniqueId);
        case DENIED:
          // Classify which cap denied so the drainer can skip the rest of this full scope in the
          // same walk. One extra read-only gate call, paid once per scope per walk (the drainer's
          // cache short-circuits the remaining entries of that scope before they reach here).
          return AdmissionDecision.requeue(
              classifyRequeueReason(accountId, resolvedParentUniqueId), resolvedParentUniqueId);
        case NOT_RESERVED:
        default:
          return AdmissionDecision.admitWithoutReserve();
      }
    }
    PlanExecutionSettingResponse planExecutionSettingResponse;
    if (!priorityType.equals(PriorityType.NORMAL)) {
      planExecutionSettingResponse = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);
    } else {
      planExecutionSettingResponse = pipelineSettingsService.shouldQueuePlanExecution(accountId);
    }
    // Legacy High/Low gate: the constraint is account+priority, NOT the project — never cacheable by
    // project, so the reason is OTHER (each entry re-evaluated on its own).
    return planExecutionSettingResponse.isShouldQueue() ? AdmissionDecision.requeue(RequeueReason.OTHER, parentUniqueId)
                                                        : AdmissionDecision.admitWithoutReserve();
  }

  // Map a per-project ENFORCE denial to the blocking cap. INDETERMINATE (fail-closed Redis blip) and
  // an unexpected HAS_HEADROOM (a slot freed between the reserve attempt and this read) are both
  // reported as OTHER so the drainer does not cache a scope it cannot trust.
  private RequeueReason classifyRequeueReason(String accountId, String resolvedParentUniqueId) {
    switch (planConcurrencyGate.evaluateHeadroom(accountId, resolvedParentUniqueId)) {
      case PROJECT_FULL:
        return RequeueReason.PROJECT_FULL;
      case ACCOUNT_FULL:
        return RequeueReason.ACCOUNT_FULL;
      default:
        return RequeueReason.OTHER;
    }
  }

  // Flip the execution to STARTING_PLAN_CREATION. When the slot was atomically reserved, suppress
  // the mutation hook's admission +1 on this transition (the reserve already applied it); otherwise
  // let the hook count as before.
  private PlanExecution flipToStartingPlanCreation(String planExecutionId, AdmissionDecision admission) {
    if (admission.isSlotReserved()) {
      return PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(
          () -> planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION));
    }
    return planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION);
  }

  public PlanCreationRequest createPlanCreationRequest(String accountId,
      PlanCreationQueueRequest planCreationQueueRequest, PlanExecution updatedPlanExecution,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    return PlanCreationRequest.builder()
        .accountId(accountId)
        .orgIdentifier(planCreationQueueRequest.getOrgId())
        .projectIdentifier(planCreationQueueRequest.getProjectId())
        .executionMetadata(updatedPlanExecution.getMetadata())
        .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
        .scopeInfo(planCreationQueueRequest.getScopeInfo())
        .isParentIdQueryingEnabled(planCreationQueueRequest.isParentIdQueryingEnabled())
        .isDebug(planCreationQueueRequest.isDebug())
        .runSequenceIncrementNeeded(false)
        .branchSequenceIncrementNeeded(false)
        .build();
  }

  private PlanExecutionMetadataWithContext buildPlanExecutionMetadataWithContext(
      PlanCreationQueueRequest planCreationQueueRequest, PlanExecutionMetadata planExecutionMetadata,
      ExecutionMetadata executionMetadata) {
    return PlanExecutionMetadataWithContext.builder()
        .isRetry(planCreationQueueRequest.isRetry())
        .identifierOfSkipStages(planCreationQueueRequest.getIdentifierOfSkipStages())
        .previousExecutionId(planCreationQueueRequest.getPreviousExecutionId())
        .retryStagesIdentifier(planCreationQueueRequest.getRetryStagesIdentifier())
        .runAllStages(planCreationQueueRequest.isRunAllStages())
        .expandedPipelineJson(getExpandedJson(planCreationQueueRequest, executionMetadata))
        .planExecutionMetadata(planExecutionMetadata)
        .triggerHeader(planExecutionMetadata.getTriggerHeader())
        .triggerJsonPayload(planExecutionMetadata.getTriggerJsonPayload())
        .expressionFunctorToken(planExecutionMetadata.getExpressionFunctorToken())
        .stagesExecutionMetadata(planExecutionMetadata.getStagesExecutionMetadata())
        .triggerPayload(planExecutionMetadata.getTriggerPayload())
        .stageExpressionValuesMap(planExecutionMetadata.getStageExpressionValuesMap())
        .processedYaml(planExecutionMetadata.getProcessedYaml())
        .postExecutionRollbackInfos(planExecutionMetadata.getPostExecutionRollbackInfos())
        .isDynamicExecution(planCreationQueueRequest.isDynamicExecution())
        .isAsyncPlanCreation(true)
        .build();
  }

  private String getExpandedJson(
      PlanCreationQueueRequest planCreationQueueRequest, ExecutionMetadata executionMetadata) {
    boolean isParentIdQueryingEnabled = planCreationQueueRequest.isParentIdQueryingEnabled();
    ScopeInfo scopeInfo = planCreationQueueRequest.getScopeInfo();
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(planCreationQueueRequest.getAccountId(),
        planCreationQueueRequest.getOrgId(), planCreationQueueRequest.getProjectId(),
        executionMetadata.getPipelineIdentifier(), false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntity.isEmpty() || isEmpty(planCreationQueueRequest.getPipelineYamlWithTemplateRef())) {
      return null;
    }
    String expandedJson;
    if (planCreationQueueRequest.isParentIdQueryingEnabled()) {
      expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity.get(),
          planCreationQueueRequest.getScopeInfo(), planCreationQueueRequest.getPipelineYamlWithTemplateRef(),
          planCreationQueueRequest.getBranch(), OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
    } else {
      expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity.get(),
          planCreationQueueRequest.getPipelineYamlWithTemplateRef(), planCreationQueueRequest.getBranch(),
          OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
    }
    return expandedJson;
  }

  @VisibleForTesting
  void markPlanExecutionFailed(Exception e, String planExecutionId) {
    PolicyEvaluationFailureException policyFailure = ExceptionUtils.cause(PolicyEvaluationFailureException.class, e);
    String errorMessage;
    FailureType failureType;
    ErrorCode errorCode;
    OpaOnSaveStatusDTO opaOnSaveStatus;
    GovernanceMetadata governanceMetadata;
    if (policyFailure != null) {
      if (isNotEmpty(policyFailure.getMessage())) {
        errorMessage = policyFailure.getMessage();
      } else {
        errorMessage = e.getMessage();
      }
      failureType = FailureType.POLICY_EVALUATION_FAILURE;
      errorCode = ErrorCode.POLICY_EVALUATION_FAILURE;
      opaOnSaveStatus = policyFailure.getOpaOnSaveStatusDTO();
      if (opaOnSaveStatus != null && opaOnSaveStatus.getGovernanceMetadata() != null) {
        governanceMetadata = opaOnSaveStatus.getGovernanceMetadata();
      } else {
        governanceMetadata = policyFailure.getGovernanceMetadata();
      }
    } else {
      errorMessage = e.getMessage();
      failureType = FailureType.UNKNOWN_FAILURE;
      errorCode = ErrorCode.PLAN_CREATION_ERROR;
      opaOnSaveStatus = null;
      governanceMetadata = null;
    }

    planExecutionService.updateStatus(planExecutionId, Status.ERRORED, ops -> {
      ops.set(PlanExecutionKeys.failureInfo,
          FailureInfo.newBuilder()
              .setErrorMessage(errorMessage)
              .addFailureData(FailureData.newBuilder()
                                  .addFailureTypeInfos(FailureTypeInfo.newBuilder().setFailureType(failureType).build())
                                  .setCode(String.valueOf(errorCode))
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(errorMessage)
                                  .build())
              .build());
      if (governanceMetadata != null) {
        ops.set(PlanExecutionKeys.governanceMetadata, governanceMetadata);
      }
    });
    updateExecutionSummaryWithOpaFields(planExecutionId, governanceMetadata, opaOnSaveStatus);
  }

  private void updateExecutionSummaryWithOpaFields(
      String planExecutionId, GovernanceMetadata governanceMetadata, OpaOnSaveStatusDTO opaOnSaveStatus) {
    if (governanceMetadata == null && opaOnSaveStatus == null) {
      return;
    }
    Update summaryUpdate = new Update();
    if (governanceMetadata != null) {
      summaryUpdate.set(PlanExecutionSummaryKeys.governanceMetadata, governanceMetadata);
    }
    if (opaOnSaveStatus != null) {
      summaryUpdate.set(PlanExecutionSummaryKeys.opaOnSaveStatus, opaOnSaveStatus);
    }
    try {
      pmsExecutionSummaryService.update(planExecutionId, summaryUpdate);
    } catch (Exception ex) {
      log.warn("Failed to propagate OPA governance fields to execution summary for planExecutionId: {}",
          planExecutionId, ex);
    }
  }

  public PlanExecution executePlanCreationRequest(PlanCreationRequest planCreationRequest) {
    ExecutionPlan executionPlan = preparePlanAndMetadata(planCreationRequest);
    try {
      return orchestrationService.startExecution(executionPlan.getPlan(), executionPlan.getAbstractions(),
          executionPlan.getExecutionMetadata(), executionPlan.getPlanExecutionMetadataWithContext());
    } catch (WingsException e) {
      log.warn("Unable to start the execution: [{}]", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.warn("Add transaction for increment and startExecution as execution failed after plan creation");
      throw new InternalServerErrorException(e.getMessage());
    }
  }

  public ExecutionPlan createPlanForDryRun(PlanCreationRequest planCreationRequest) {
    return preparePlanAndMetadata(planCreationRequest);
  }

  /**
   * Common method to prepare plan and metadata for both regular and dry-run executions.
   * Extracts request parameters, creates plan, transforms it, and builds final execution metadata.
   */
  private ExecutionPlan preparePlanAndMetadata(PlanCreationRequest planCreationRequest) {
    String accountId = planCreationRequest.getAccountId();
    String orgIdentifier = planCreationRequest.getOrgIdentifier();
    String projectIdentifier = planCreationRequest.getProjectIdentifier();
    ScopeInfo scopeInfo = planCreationRequest.getScopeInfo();
    ExecutionMetadata executionMetadata = planCreationRequest.getExecutionMetadata();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        planCreationRequest.getPlanExecutionMetadataWithContext();
    boolean isParentIdQueryingEnabled = planCreationRequest.isParentIdQueryingEnabled();
    boolean isDebug = planCreationRequest.isDebug();
    boolean runSequenceIncrementNeeded = planCreationRequest.isRunSequenceIncrementNeeded();
    long startTs = System.currentTimeMillis();
    Map<String, String> abstractions = setupAbstractions(accountId, orgIdentifier, projectIdentifier, scopeInfo);

    // Seed pipeline identities on the sync path too (async path seeds in createPlanExecution). Null guard
    // prevents the async consumer (which also routes here) from double-minting.
    if (planExecutionMetadataWithContext.getIdentityExecutionContext() == null) {
      planExecutionMetadataWithContext.setIdentityExecutionContext(
          pipelineIdentityService.buildPipelineIdentityContext(accountId, orgIdentifier, projectIdentifier,
              executionMetadata.getPipelineIdentifier(), planExecutionMetadataWithContext.getProcessedYaml()));
    }

    // We are doing a pipeline level check for unified flow, to minimise the affected scope
    // This is a temporary solution, once unified flow is enabled at account level we can remove this.
    boolean shouldUseUnifiedFlow = YamlUtils.extractShouldUseUnifiedFlow(
        planExecutionMetadataWithContext.getProcessedYaml(), UnifiedConditionChecker.CD_ROUTE_TO_UNIFIED);
    if (shouldUseUnifiedFlow) {
      abstractions.put(UnifiedConditionChecker.CD_ROUTE_TO_UNIFIED, String.valueOf(true));
    }
    try (AutoLogContext ignore = PlanCreatorUtils.autoLogContext(
             executionMetadata, accountId, orgIdentifier, projectIdentifier, scopeInfo)) {
      Plan plan = createPlan(accountId, orgIdentifier, projectIdentifier, executionMetadata,
          planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled, startTs, abstractions);

      List<Node> planNodesList = plan.getPlanNodes();
      // Fetches the modules based on the steps in the pipeline using the stepType in the planeNodes
      List<String> modules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodesList);
      // Setting all the modules based on the steps in the pipeline
      if (!modules.isEmpty()) {
        plan = plan.withStepModules(modules);
      }

      ExecutionMode executionMode = executionMetadata.getExecutionMode();
      List<String> rollbackStageIds = Collections.emptyList();

      if (planExecutionMetadataWithContext.getStagesExecutionMetadata() != null) {
        rollbackStageIds = planExecutionMetadataWithContext.getStagesExecutionMetadata().getStageIdentifiers();
      }
      plan = transformPlan(plan, planExecutionMetadataWithContext.isRetry(),
          planExecutionMetadataWithContext.getIdentifierOfSkipStages(),
          planExecutionMetadataWithContext.getPreviousExecutionId(),
          planExecutionMetadataWithContext.getRetryStagesIdentifier(), executionMode, rollbackStageIds,
          planExecutionMetadataWithContext.isRunAllStages(), accountId);

      if (isDebug && validateDebugRunForCI(plan)) {
        throw new InvalidRequestException(
            format("Debug executions are not allowed for pipeline [%s]", executionMetadata.getPipelineIdentifier()));
      }
      ExecutionMetadata.Builder executionMetadataBuilder = executionMetadata.toBuilder();

      // increment run sequence if needed
      if (runSequenceIncrementNeeded) {
        executionMetadataBuilder.setRunSequence(pipelineMetadataService.incrementRunSequence(accountId, orgIdentifier,
            projectIdentifier, executionMetadata.getPipelineIdentifier(), scopeInfo, isParentIdQueryingEnabled));
      }

      // increment branch sequence for CI builds (CI-19987)
      if (planCreationRequest.isBranchSequenceIncrementNeeded()) {
        incrementBranchSequenceIfApplicable(executionMetadataBuilder, accountId, orgIdentifier, projectIdentifier,
            executionMetadata.getPipelineIdentifier(), planExecutionMetadataWithContext, scopeInfo);
      }

      ExecutionMetadata finalExecutionMetadata = executionMetadataBuilder.build();
      return ExecutionPlan.builder()
          .plan(plan)
          .abstractions(abstractions)
          .executionMetadata(finalExecutionMetadata)
          .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
          .build();
    }
  }

  private Plan createPlan(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, long startTs, Map<String, String> abstractions) {
    Plan plan;
    PlanCreationBlobResponse resp;
    try {
      resp = planCreatorMergeService.createPipelinePlanVersion(accountId, orgIdentifier, projectIdentifier,
          executionMetadata.getProcessedYamlVersion(), executionMetadata, planExecutionMetadataWithContext, scopeInfo,
          isParentIdQueryingEnabled);
      plan = PlanExecutionUtils.extractPlan(resp, accountId);
      publishPlanCreationMetric(startTs, abstractions, io.harness.beans.ExecutionStatus.SUCCESS);
    } catch (IOException e) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
      publishPlanCreationMetric(startTs, abstractions, io.harness.beans.ExecutionStatus.FAILED);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
    } catch (PlanCreatorException ex) {
      if (isNotEmpty(ex.errorModules)) {
        for (String module : ex.errorModules) {
          abstractions.put(
              "module", module); // add module as metric parameter for recording failed plan creations so that we can
          // easily recognise of some particular module is facing some issue
          publishPlanCreationMetric(startTs, abstractions, io.harness.beans.ExecutionStatus.FAILED);
        }
      } else {
        publishPlanCreationMetric(startTs, abstractions, io.harness.beans.ExecutionStatus.FAILED);
      }
      throw ex;
    } catch (Exception ex) {
      publishPlanCreationMetric(startTs, abstractions, io.harness.beans.ExecutionStatus.FAILED);
      throw ex;
    }
    return plan;
  }

  public Plan createAndAppendToExistingPlan(Ambiance ambiance, String processedYaml, YamlField rootField) {
    try {
      String fqnPrefixToReplace = "pipeline.";
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.findByPlanExecutionId(accountId, ambiance.getPlanExecutionId()).orElseThrow();
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
          PlanExecutionMetadataWithContext.builder()
              .processedYaml(processedYaml)
              .planExecutionMetadata(planExecutionMetadata)
              .build();

      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .uniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
                                .build();

      PlanCreationBlobResponse resp = planCreatorMergeService.createPlanVersioned(accountId, orgId, projectId,
          rootField, ambiance.getMetadata().getProcessedYamlVersion(), ambiance.getMetadata(),
          planExecutionMetadataWithContext, scopeInfo, true,
          Collections.singletonMap(PlanCreatorConstants.CHILD_OF_DYNAMIC_STAGE, Boolean.TRUE.toString()));

      Plan plan = PlanExecutionUtils.extractPlan(ambiance.getPlanId(), resp, AmbianceUtils.getAccountId(ambiance));
      String currentNodeFqn = AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList()) + ".";
      List<Node> planNodes = plan.getPlanNodes()
                                 .stream()
                                 .map(planNode -> {
                                   if (planNode.getStageFqn().startsWith(fqnPrefixToReplace)) {
                                     return ((PlanNode) planNode)
                                         .toBuilder()
                                         .stageFqn(planNode.getStageFqn().replace(fqnPrefixToReplace, currentNodeFqn))
                                         .build();
                                   }
                                   return planNode;
                                 })
                                 .collect(Collectors.toList());
      plan = plan.withPlanNodes(planNodes);
      orchestrationStartSubject.fireInform(OrchestrationStartObserver::onDynamicStart,
          DynamicOrchestrationStartInfo.builder().ambiance(ambiance).plan(plan).build());
      return planService.save(plan);
    } catch (IOException ex) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    } catch (Exception ex) {
      throw new InvalidRequestException("Error while creating the plan for the dynamically generated yaml", ex);
    }
  }

  @VisibleForTesting
  protected Map<String, String> setupAbstractions(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, accountId);
    abstractions.put(SetupAbstractionKeys.projectIdentifier, projectIdentifier);

    if (isNotEmpty(orgIdentifier)) {
      abstractions.put(SetupAbstractionKeys.orgIdentifier, orgIdentifier);
    }
    if (scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      abstractions.put(SetupAbstractionKeys.parentUniqueId, scopeInfo.getUniqueId());
      if (isNotEmpty(scopeInfo.getOrgIdentifier())) {
        abstractions.put(SetupAbstractionKeys.orgIdentifier, scopeInfo.getOrgIdentifier());
      }
    } else {
      Optional<ScopeInfo> optionalScopeInfo =
          scopeResolutionHelper.getScopeInfoOptional(accountId, orgIdentifier, projectIdentifier);
      if (optionalScopeInfo.isPresent() && isNotEmpty(optionalScopeInfo.get().getUniqueId())) {
        abstractions.put(SetupAbstractionKeys.parentUniqueId, optionalScopeInfo.get().getUniqueId());
        if (isNotEmpty(optionalScopeInfo.get().getOrgIdentifier())) {
          abstractions.put(SetupAbstractionKeys.orgIdentifier, optionalScopeInfo.get().getOrgIdentifier());
        }
      }
    }
    return abstractions;
  }

  private void publishPlanCreationMetric(
      long startTs, Map<String, String> abstractions, io.harness.beans.ExecutionStatus status) {
    long endTs = System.currentTimeMillis();
    abstractions.put("status", status.name());
    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(abstractions)) {
      metricService.recordMetric(PLAN_CREATION_TIME_METRIC_NAME, endTs - startTs);
    }
    log.info("[PMS_PLAN] Time taken to complete plan: {}ms ", endTs - startTs);
  }

  Plan transformPlan(Plan plan, boolean isRetry, List<String> identifierOfSkipStages, String previousExecutionId,
      List<String> retryStagesIdentifier, ExecutionMode executionMode, List<String> rollbackStageIds,
      boolean runAllStages, String accountId) {
    if (isRetry) {
      return retryExecutionHelper.transformPlan(
          plan, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier, runAllStages);
    }
    if (isRollbackMode(executionMode)) {
      return rollbackModeExecutionHelper.transformPlanForRollbackMode(plan, previousExecutionId,
          plan.getPreservedNodesInRollbackMode(), executionMode, rollbackStageIds, accountId);
    }
    return plan;
  }

  private boolean validateDebugRunForCI(Plan plan) {
    if (plan == null || plan.getGraphLayoutInfo() == null) {
      return false;
    }

    Set<String> ciStageTypes = plan.getGraphLayoutInfo()
                                   .getLayoutNodesMap()
                                   .values()
                                   .stream()
                                   .map(GraphLayoutNode::getNodeType)
                                   .filter(CI_STAGE::equals)
                                   .collect(Collectors.toSet());
    return ciStageTypes.isEmpty();
  }

  /**
   * Increments the branch sequence counter for builds.
   *
   * <p>Uses BranchSequenceService to extract branch and repo URL and increment the per-branch
   * counter. Sets branchSeqId, codebaseBranch, and normalizedRepoUrl on ExecutionMetadata.
   *
   * <p>Supports two modes:
   * <ol>
   *   <li>Webhook triggers: Branch/repo extracted from trigger payload (push, PR webhooks)</li>
   *   <li>Manual execution with branch: Branch extracted from processed YAML codebase config</li>
   * </ol>
   *
   * @param executionMetadataBuilder the builder to set branch sequence fields on
   * @param accountId the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param planExecutionMetadataWithContext the plan execution metadata containing trigger payload and processed YAML
   * @param scopeInfo the scope info containing parentUniqueId for UniqueIdAware pattern
   */
  private void incrementBranchSequenceIfApplicable(ExecutionMetadata.Builder executionMetadataBuilder, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, ScopeInfo scopeInfo) {
    try {
      // Check if the feature flag is enabled
      if (!pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID)) {
        return;
      }

      log.info("[BranchSeqId] Starting branch sequence check for pipeline={}", pipelineIdentifier);

      if (planExecutionMetadataWithContext == null) {
        log.info("[BranchSeqId] No plan execution metadata available, skipping branch sequence increment");
        return;
      }

      // Get parentUniqueId for UniqueIdAware pattern
      String parentUniqueId = scopeInfo != null ? scopeInfo.getUniqueId() : null;

      BranchSequenceResult result = null;

      // First, try to get branch/repo from trigger payload (webhook triggers)
      TriggerPayload triggerPayload = null;
      if (planExecutionMetadataWithContext.getPlanExecutionMetadata() != null) {
        triggerPayload = planExecutionMetadataWithContext.getPlanExecutionMetadata().getTriggerPayload();
      }

      log.info("[BranchSeqId] TriggerPayload present={}, hasParsedPayload={}", triggerPayload != null,
          triggerPayload != null && triggerPayload.hasParsedPayload());

      if (triggerPayload != null && triggerPayload.hasParsedPayload()) {
        result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerPayload, parentUniqueId);
        log.info("[BranchSeqId] Result from trigger payload: {}", result != null ? "success" : "null");
      }

      // If no result from trigger payload, try to extract from YAML (manual execution)
      if (result == null) {
        // Try multiple YAML sources - processedYaml, pipelineYaml, or yaml from metadata
        String yamlToUse = planExecutionMetadataWithContext.getProcessedYaml();
        log.info("[BranchSeqId] processedYaml present={}", isNotEmpty(yamlToUse));

        if (isEmpty(yamlToUse)) {
          yamlToUse = planExecutionMetadataWithContext.getPipelineYaml();
          log.info("[BranchSeqId] pipelineYaml present={}", isNotEmpty(yamlToUse));
        }
        if (isEmpty(yamlToUse) && planExecutionMetadataWithContext.getPlanExecutionMetadata() != null) {
          yamlToUse = planExecutionMetadataWithContext.getPlanExecutionMetadata().getYaml();
          log.info("[BranchSeqId] metadata.yaml present={}", isNotEmpty(yamlToUse));
        }
        if (isNotEmpty(yamlToUse)) {
          log.info("[BranchSeqId] Using YAML (first 200 chars): {}",
              yamlToUse.length() > 200 ? yamlToUse.substring(0, 200) + "..." : yamlToUse);
          result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(accountId, orgIdentifier,
              projectIdentifier, pipelineIdentifier, yamlToUse, triggerPayload, parentUniqueId);
          log.info("[BranchSeqId] Result from YAML: {}", result != null ? "success" : "null");
        } else {
          log.info("[BranchSeqId] No YAML available from any source");
        }
      }

      if (result != null) {
        // Set branch sequence fields on execution metadata
        executionMetadataBuilder.setBranchSeqId(result.getBranchSeqId());
        executionMetadataBuilder.setCodebaseBranch(result.getNormalizedBranch());
        executionMetadataBuilder.setNormalizedRepoUrl(result.getNormalizedRepoUrl());

        log.info("[BranchSeqId] SUCCESS: Set branchSeqId={} for pipeline={}, branch={}, repo={}",
            result.getBranchSeqId(), pipelineIdentifier, result.getNormalizedBranch(), result.getNormalizedRepoUrl());
      } else {
        log.info(
            "[BranchSeqId] Could not determine branch sequence for pipeline={} (no trigger payload or codebase config)",
            pipelineIdentifier);
      }

    } catch (Exception e) {
      // Don't fail the execution if branch sequence increment fails
      log.warn("[BranchSeqId] Failed to increment branch sequence for pipeline={}: {}", pipelineIdentifier,
          e.getMessage(), e);
    }
  }
}