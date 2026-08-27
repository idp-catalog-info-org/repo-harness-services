/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORTALL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.eventsframework.EntityChangeLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.SystemIssuer;
import io.harness.pms.event.entitycrud.response.ExecutionDetailsDeleteResponseWrapper;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.pipelinedelete.service.PipelineDeleteProcessorIteratorEntityService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.search.helper.PipelineSearchHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.remote.client.NGRestUtils;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.service.PipelineSearchService;
import io.harness.serializer.ProtoUtils;
import io.harness.service.GraphGenerationService;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.waiter.WaitNotifyEngine;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PipelineEntityCRUDStreamListener implements MessageListener {
  private final NGTriggerService ngTriggerService;
  private final PipelineMetadataService pipelineMetadataService;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final BarrierService barrierService;
  private final PreflightService preflightService;
  private final PmsSweepingOutputService pmsSweepingOutputService;
  private final PmsOutcomeService pmsOutcomeService;
  private final InterruptService interruptService;
  private final InputFileService inputFileService;
  private final GraphGenerationService graphGenerationService;
  private final NodeExecutionService nodeExecutionService;
  private final NGTriggerEventsService ngTriggerEventsService;
  private final PlanExecutionService planExecutionService;
  private final PlanExpansionService planExpansionService;
  private final NGSettingsClient ngSettingsClient;
  private final ExecutorService pipelineExecutorService;
  // Max batch size of planExecutionIds to delete related metadata, so that delete records are in limited range
  private final Integer MAX_DELETION_BATCH_PROCESSING;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PMSExecutionService pmsExecutionService;
  private final WaitNotifyEngine waitNotifyEngine;
  private final PipelineDeleteProcessorIteratorEntityService deleteProcessorIteratorEntityService;
  private final ExecutionRetentionService executionRetentionService;
  private final PipelineSearchService pipelineSearchService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PipelineExecutionGitMetadataService pipelineExecutionGitMetadataService;
  private final MetricService metricService;

  private final int MAX_RETRIES = 10;
  public static final String ABORT_AND_DELETE_PIPELINES = "abort_and_delete_pipelines";
  private static final String PIPELINE_DELETE_ENTITIES_PROCESSED_COUNT = "pipeline_delete_entities_processed_count";
  private static final String PIPELINE_DELETE_ENTITIES_PROCESSING_DURATION =
      "pipeline_delete_entities_processing_duration";
  Set<String> expectedDeletedEntityTypes =
      new HashSet<>(Arrays.asList("BARRIERS", "SWEEPING_OUTPUTS", "OUTCOMES", "INTERRUPTS", "GRAPHS", "NODE_EXECUTIONS",
          "RETENTION_METADATA", "PLAN_EXECUTION_METADATA", "PLAN_EXECUTION_SUMMARY", "PLAN_EXPANSIONS", "INPUT_FILES"));

  @Inject
  public PipelineEntityCRUDStreamListener(NGTriggerService ngTriggerService,
      PipelineMetadataService pipelineMetadataService, PmsExecutionSummaryService pmsExecutionSummaryService,
      BarrierService barrierService, PreflightService preflightService,
      PmsSweepingOutputService pmsSweepingOutputService, PmsOutcomeService pmsOutcomeService,
      InterruptService interruptService, InputFileService inputFileService,
      GraphGenerationService graphGenerationService, NodeExecutionService nodeExecutionService,
      NGTriggerEventsService ngTriggerEventsService, PlanExecutionService planExecutionService,
      PlanExpansionService planExpansionService, NGSettingsClient ngSettingsClient,
      @Named("PipelineExecutorService") ExecutorService pipelineExecutorService,
      @Named("pipelineExecutionDetailsDeleteMaxBatchSize") Integer max_deletion_batch_processing,
      PmsFeatureFlagService pmsFeatureFlagService, PMSExecutionService pmsExecutionService,
      WaitNotifyEngine waitNotifyEngine,
      PipelineDeleteProcessorIteratorEntityService deleteProcessorIteratorEntityService,
      ExecutionRetentionService executionRetentionService, PipelineSearchService pipelineSearchService,
      ScopeResolutionHelper scopeResolutionHelper,
      PipelineExecutionGitMetadataService pipelineExecutionGitMetadataService, MetricService metricService) {
    this.ngTriggerService = ngTriggerService;
    this.pipelineMetadataService = pipelineMetadataService;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
    this.barrierService = barrierService;
    this.preflightService = preflightService;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.pmsOutcomeService = pmsOutcomeService;
    this.interruptService = interruptService;
    this.inputFileService = inputFileService;
    this.graphGenerationService = graphGenerationService;
    this.nodeExecutionService = nodeExecutionService;
    this.planExecutionService = planExecutionService;
    this.ngTriggerEventsService = ngTriggerEventsService;
    this.planExpansionService = planExpansionService;
    this.ngSettingsClient = ngSettingsClient;
    this.pipelineExecutorService = pipelineExecutorService;
    this.MAX_DELETION_BATCH_PROCESSING = max_deletion_batch_processing;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.pmsExecutionService = pmsExecutionService;
    this.waitNotifyEngine = waitNotifyEngine;
    this.deleteProcessorIteratorEntityService = deleteProcessorIteratorEntityService;
    this.executionRetentionService = executionRetentionService;
    this.pipelineSearchService = pipelineSearchService;
    this.scopeResolutionHelper = scopeResolutionHelper;
    this.pipelineExecutionGitMetadataService = pipelineExecutionGitMetadataService;
    this.metricService = metricService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && PIPELINE_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        EntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processPipelineEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processPipelineEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        if (checkIfAnyRequiredFieldIsNotEmpty(entityChangeDTO)) {
          try (EntityChangeLogContext logContext = new EntityChangeLogContext(entityChangeDTO)) {
            return handleDeleteEvent(entityChangeDTO);
          }
        } else {
          return true;
        }
      default:
    }
    return true;
  }

  /**
   * Delete the entities in background which can be slow processing and will not impact other operations for the
   * customers.
   * Mainly for deleting executions, metadata, background entities etc.
   * @param entityChangeDTO
   * @return
   */
  private boolean handleDeleteEvent(EntityChangeDTO entityChangeDTO) {
    String accountId = entityChangeDTO.getAccountIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String pipelineIdentifier = entityChangeDTO.getIdentifier().getValue();
    String parentUniqueId = entityChangeDTO.getScopeInfo().getUniqueId().getValue();

    boolean retainPipelineExecutionDetailsAfterDelete = false;
    try {
      retainPipelineExecutionDetailsAfterDelete = Boolean.parseBoolean(
          NGRestUtils
              .getResponse(ngSettingsClient.getSetting(
                  NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName(), accountId, null, null))
              .getValue());
    } catch (Exception ex) {
      log.warn(String.format("Could not fetch setting: %s",
                   NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName()),
          ex);
    }

    deleteProcessorIteratorEntityService.save(
        PipelineDeleteProcessorIteratorEntity.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgIdentifier)
            .pipelineIdentifier(pipelineIdentifier)
            .projectIdentifier(projectIdentifier)
            .parentUniqueId(parentUniqueId)
            .retainPipelineExecutionDetailsAfterDelete(retainPipelineExecutionDetailsAfterDelete)
            .nextIteration(0L)
            .build());
    return true;
  }

  public boolean processDeleteEvent(Instant jobStartTs, Duration syncJobMaxRunTime, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      boolean retainPipelineExecutionDetailsAfterDelete, String parentUniqueId) {
    deletePipelineMetadataDetails(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        retainPipelineExecutionDetailsAfterDelete, parentUniqueId);
    log.info(String.format("Processed deleting metadata for "
            + "given pipeline %s in accountId [%s] and orgIdentifier [%s] and projectIdentifier [%s]",
        pipelineIdentifier, accountId, orgIdentifier, projectIdentifier));
    return deletePipelineExecutionsDetails(jobStartTs, syncJobMaxRunTime, accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, retainPipelineExecutionDetailsAfterDelete, parentUniqueId);
  }

  // Delete all pipeline metadata details related to given pipeline identifier.
  private void deletePipelineMetadataDetails(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean retainPipelineExecutionDetailsAfterDelete, String parentUniqueId) {
    // Delete all triggers, ignore any error
    ngTriggerService.deleteAllForPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
    // Delete trigger event history
    ngTriggerEventsService.deleteAllForPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
    // Delete the pipeline metadata to delete run-sequence, etc.
    pipelineMetadataService.deletePipelineMetadata(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
    // Delete git metadata for the pipeline
    pipelineExecutionGitMetadataService.deletePipelineGitMetadata(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, retainPipelineExecutionDetailsAfterDelete, parentUniqueId);
    // Deletes all related preflight data
    preflightService.deleteAllPreflightEntityForGivenPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
  }

  private void checkAndDeletePipelineExecutionsDetailsOnFullBatch(
      String accountId, Set<String> toBeDeletedPlanExecutions, boolean retainPipelineExecutionDetailsAfterDelete) {
    // If max deletion batch is reached, delete all its related entities
    // We don't want to delete all executions for a pipeline together as total delete could be very high
    if (toBeDeletedPlanExecutions.size() >= MAX_DELETION_BATCH_PROCESSING) {
      deletePipelineExecutionsDetailsInternal(
          toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete, accountId);
      toBeDeletedPlanExecutions.clear();
    }
  }

  // Delete all execution related details using all planExecution for given pipelineIdentifier.
  private boolean deletePipelineExecutionsDetails(Instant jobStartTs, Duration syncJobMaxRunTime, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      boolean retainPipelineExecutionDetailsAfterDelete, String parentUniqueId) {
    long startTime = System.currentTimeMillis();
    int totalEntitiesProcessed = 0;
    Set<String> toBeDeletedPlanExecutions = new HashSet<>();
    Set<String> toBeAbortedAndDeletedPlanExecutions = new HashSet<>();
    int toBeDeletedExecutionCount = 0;
    int toBeAbortedAndDeletedExecutionCount = 0;

    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      Query query = PipelineSearchHelper.formQueryWithScopeAndPipelineIdentifier(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
      try (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> elasticSearchStream =
               pipelineSearchService.fetchPipelineSearchReadExecutionSummaryDTO(accountId, query,
                   Set.of(PipelineSearchExecutionSummaryDTOKeys.status,
                       PipelineSearchExecutionSummaryDTOKeys.planExecutionId))) {
        Iterator<PipelineSearchReadExecutionSummaryDTO> iterator = elasticSearchStream.iterator();
        while (iterator.hasNext()) {
          PipelineSearchReadExecutionSummaryDTO pipelineSearchReadExecutionSummaryDTO = iterator.next();
          String planExecutionId = pipelineSearchReadExecutionSummaryDTO.getPlanExecutionId();
          totalEntitiesProcessed++;
          if (!StatusUtils.isFinalStatus(
                  ExecutionStatus.valueOf(pipelineSearchReadExecutionSummaryDTO.getStatus()).getEngineStatus())) {
            toBeAbortedAndDeletedPlanExecutions.add(planExecutionId);
            toBeAbortedAndDeletedExecutionCount++;
          } else {
            toBeDeletedPlanExecutions.add(planExecutionId);
            toBeDeletedExecutionCount++;
          }
          checkAndDeletePipelineExecutionsDetailsOnFullBatch(
              accountId, toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete);
          if (shouldPauseDeleteProcess(
                  jobStartTs, syncJobMaxRunTime, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier)) {
            return false;
          }
        }
      }
    } else {
      try (Stream<PipelineExecutionSummaryEntity> stream =
               pmsExecutionSummaryService.fetchPlanExecutionIdsAndStatusFromAnalytics(
                   accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId)) {
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = iterator.next();
          String planExecutionId = pipelineExecutionSummaryEntity.getPlanExecutionId();
          if (!StatusUtils.isFinalStatus(pipelineExecutionSummaryEntity.getStatus().getEngineStatus())) {
            toBeAbortedAndDeletedPlanExecutions.add(planExecutionId);
            toBeAbortedAndDeletedExecutionCount++;
          } else {
            toBeDeletedPlanExecutions.add(planExecutionId);
            toBeDeletedExecutionCount++;
          }
          checkAndDeletePipelineExecutionsDetailsOnFullBatch(
              accountId, toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete);
          totalEntitiesProcessed++;
          if (shouldPauseDeleteProcess(
                  jobStartTs, syncJobMaxRunTime, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier)) {
            return false;
          }
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(toBeAbortedAndDeletedPlanExecutions)) {
      InterruptConfig interruptConfig = getInterruptConfig();
      String taskId = generateUuid();
      for (String planExecutionId : toBeAbortedAndDeletedPlanExecutions) {
        try {
          pmsExecutionService.registerInterrupt(ABORTALL, planExecutionId, null, interruptConfig);
        } catch (Exception e) {
          log.error(String.format("Unable to abort planExecutionId [%s] for pipeline [%s] in account [%s] and "
                            + "orgIdentifier [%s] and projectIdentifier [%s]",
                        planExecutionId, pipelineIdentifier, accountId, orgIdentifier, projectIdentifier),
              e);
        }
      }

      AbortAllPlanExecutionsCallback abortAndDeleteCallback =
          AbortAllPlanExecutionsCallback.builder()
              .planExecutionsToDelete(toBeAbortedAndDeletedPlanExecutions)
              .retainPipelineExecutionDetailsAfterDelete(retainPipelineExecutionDetailsAfterDelete)
              .accountId(accountId)
              .build();
      waitNotifyEngine.waitForAllOn(ABORT_AND_DELETE_PIPELINES, abortAndDeleteCallback, null,
          Collections.singletonList(taskId), Duration.ofMinutes(30), null);
      log.info(String.format("Processing Abort and deleting execution details for "
              + "given pipeline %s having %s running executions in accountId [%s] and orgIdentifier [%s] and "
              + "projectIdentifier [%s]",
          pipelineIdentifier, toBeAbortedAndDeletedExecutionCount, accountId, orgIdentifier, projectIdentifier));
    }

    if (EmptyPredicate.isNotEmpty(toBeDeletedPlanExecutions)) {
      deletePipelineExecutionsDetailsInternal(
          toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete, accountId);
    }
    log.info(String.format("Processed deleting execution details for "
            + "given pipeline %s having %s executions in accountId [%s] and orgIdentifier [%s] and projectIdentifier "
            + "[%s]",
        pipelineIdentifier, toBeDeletedExecutionCount, accountId, orgIdentifier, projectIdentifier));

    // Record metrics for entity processing
    long duration = System.currentTimeMillis() - startTime;
    metricService.recordMetric(PIPELINE_DELETE_ENTITIES_PROCESSED_COUNT, totalEntitiesProcessed);
    metricService.recordDuration(PIPELINE_DELETE_ENTITIES_PROCESSING_DURATION, Duration.ofMillis(duration));

    return true;
  }

  private boolean shouldPauseDeleteProcess(Instant jobStartTs, Duration syncJobMaxRunTime, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    if (hasJobRunTimeExceededMaxRunTime(jobStartTs, syncJobMaxRunTime)) {
      log.warn(String.format("[PIPELINE_DELETE]: Delete job run time exceeded max run time, will try again after 30 "
              + "mins for account: %s, org: %s, project: %s, pipeline: %s",
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier));
      return true;
    }
    if (getMaintenanceFlag()) {
      log.warn("[PIPELINE_DELETE]: Service is going in maintenance mode so shutting down the iterator");
      return true;
    }
    return false;
  }

  private InterruptConfig getInterruptConfig() {
    return InterruptConfig.newBuilder()
        .setIssuedBy(IssuedBy.newBuilder()
                         .setSystemIssuer(SystemIssuer.newBuilder().build())
                         .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                         .build())
        .build();
  }

  @VisibleForTesting
  // Internal method which deletes all execution metadata for given planExecutions
  void deletePipelineExecutionsDetailsInternal(
      Set<String> planExecutionsToDelete, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    CompletableFutures<ExecutionDetailsDeleteResponseWrapper> completableFutures =
        new CompletableFutures<>(pipelineExecutorService);

    completableFutures.supplyAsync(() -> { // Deletes the barrierInstances
      barrierService.deleteAllForGivenPlanExecutionId(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("BARRIERS").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete sweepingOutput
      pmsSweepingOutputService.deleteAllSweepingOutputInstances(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("SWEEPING_OUTPUTS").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete outcome instances
      pmsOutcomeService.deleteAllOutcomesInstances(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("OUTCOMES").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete all interrupts
      interruptService.deleteAllInterrupts(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("INTERRUPTS").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete all graph metadata
      graphGenerationService.deleteAllGraphMetadataForGivenExecutionIds(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete, accountId);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("GRAPHS").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete nodeExecutions and its metadata
      nodeExecutionService.deleteAllNodeExecutionAndMetadata(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("NODE_EXECUTIONS").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete retention data from object store
      executionRetentionService.deleteAllPlanExecutionsData(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("RETENTION_METADATA").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete all planExecutions and its metadata
      planExecutionService.deleteAllPlanExecutionAndMetadata(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete, accountId);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("PLAN_EXECUTION_METADATA").build();
    });

    completableFutures.supplyAsync(() -> {
      // Delete all planExecution summary from elastic and mongo
      pmsExecutionSummaryService.deleteAllSummaryForGivenPlanExecutionIds(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete, accountId);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("PLAN_EXECUTION_SUMMARY").build();
    });

    completableFutures.supplyAsync(() -> {
      planExpansionService.deleteAllExpansions(planExecutionsToDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("PLAN_EXPANSIONS").build();
    });

    completableFutures.supplyAsync(() -> {
      inputFileService.deleteFilesForAllExecutions(planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete);
      return ExecutionDetailsDeleteResponseWrapper.builder().deletedEntityType("INPUT_FILES").build();
    });

    try {
      // waiting for all futures to get complete
      List<ExecutionDetailsDeleteResponseWrapper> responses = completableFutures.allOf().get(1, TimeUnit.HOURS);

      if (responses.size() != expectedDeletedEntityTypes.size()) {
        // Collect the entity types that were successfully deleted
        Set<String> deletedEntityTypes = responses.stream()
                                             .map(ExecutionDetailsDeleteResponseWrapper::getDeletedEntityType)
                                             .collect(Collectors.toSet());

        // Find entity types that weren't successfully deleted
        Set<String> missingEntityTypes = new HashSet<>(expectedDeletedEntityTypes);
        missingEntityTypes.removeAll(deletedEntityTypes);

        // Log errors for entity types that weren't deleted
        if (!missingEntityTypes.isEmpty()) {
          log.error("Failed to delete the following entity types for accountID: {}: {}", accountId,
              String.join(", ", missingEntityTypes));
        }
      }
    } catch (Exception e) {
      log.error("Error in processing delete event for pipeline", e);
    }
  }

  public void deleteAbortedPipelineExecutions(
      Set<String> planExecutionsToDelete, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    deletePipelineExecutionsDetailsInternal(
        planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete, accountId);
  }
  private boolean checkIfAnyRequiredFieldIsNotEmpty(EntityChangeDTO entityChangeDTO) {
    String accountId = entityChangeDTO.getAccountIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String pipelineIdentifier = entityChangeDTO.getIdentifier().getValue();
    if (EmptyPredicate.isEmpty(accountId) || EmptyPredicate.isEmpty(orgIdentifier)
        || EmptyPredicate.isEmpty(projectIdentifier) || EmptyPredicate.isEmpty(pipelineIdentifier)) {
      log.warn("Either of required fields for Pipeline Delete event is empty - " + entityChangeDTO);
      return false;
    }
    return true;
  }

  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs, Duration syncJobMaxRunTime) {
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(syncJobMaxRunTime) > 0;
  }
}
