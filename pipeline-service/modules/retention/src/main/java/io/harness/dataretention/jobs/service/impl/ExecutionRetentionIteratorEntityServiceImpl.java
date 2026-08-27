/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.jobs.service.impl;

import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.APPROVAL_INSTANCES;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_SUB_GRAPH;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY;
import static io.harness.dataretention.utils.ExecutionRetentionConstants.RECORDS_TO_FETCH_FROM_DB_AND_STORE_IN_OBJECT_STORE;
import static io.harness.dataretention.utils.ExecutionRetentionConstants.RECORDS_WITH_UUID_AS_PLAN_EXECUTION_ID_IN_OBJECT_STORE;
import static io.harness.dataretention.utils.ExecutionRetentionConstants.ZST_DECOMPRESSED_SIZE_METADATA_KEY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.ScopeInfo;
import io.harness.cache.EntityWithAccountId;
import io.harness.cache.SpringMongoStore;
import io.harness.dataretention.beans.RetentionResponseWrapper;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionMetadataUpdateDTO;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.entity.beans.RetentionFileFormat;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.utils.ExecutionRetentionConstants;
import io.harness.dataretention.utils.ExecutionRetentionUtils;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.utils.CompletableFutures;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.repositories.planexecution.PlanExecutionMetadataRepository;
import io.harness.service.PostgreSQLGraphStoreService;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionRetentionIteratorEntityServiceImpl implements ExecutionRetentionIteratorEntityService {
  private static final String ASYNC_ERROR_MESSAGE =
      "[DATA_RETENTION]: Error while syncing execution id: %s to object store";
  private static final String ASYNC_RESPONSE_ERROR_MESSAGE =
      "[DATA_RETENTION]: Error while syncing execution id: %s to object store, error: %s";

  @Inject private ExecutionRetentionMetadataService retentionMetadataService;
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;
  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject @Named("ExecutionRetentionSyncService") Executor executor;
  @Inject DataRetentionConfig dataRetentionConfig;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private PlanExecutionMetadataRepository planExecutionMetadataRepository;
  @Inject private SpringMongoStore mongoStore;
  @Inject private PostgreSQLGraphStoreService postgreSQLGraphStoreService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  private static final String GRAPH_SUB_GRAPH_KEY_FORMAT = "%s/%s";
  private static final String SUB_GRAPH_CANONICAL_KEY_REGEX_PATTERN = "^%s$";
  private static final String SUB_GRAPH_KEY_FORMAT = "%s/.*";

  @Override
  public void syncToObjectStore(String accountIdentifier, String planExecutionId, Long endTs, Status status) {
    if (!dataRetentionConfig.isEnabled()) {
      return;
    }
    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionSummaryRepository.fetchByPlanExecutionIdFromSecondary(planExecutionId);
    if (executionSummary == null && Status.ERRORED.equals(status)) {
      return;
    }

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, executionSummary.getParentUniqueId());
    boolean useScopeInfo = scopeInfo != null;

    retentionMetadataService.upsert(planExecutionId,
        ExecutionRetentionMetadataUpdateDTO.builder()
            .planExecutionId(planExecutionId)
            .accountId(accountIdentifier)
            .endTs(endTs)
            .bucketName(objectStoreClient.getBucketName())
            .retentionFileData(saveExecutionDataToObjectStore(executionSummary))
            .pipelineIdentifier(executionSummary.getPipelineIdentifier())
            .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummary.getOrgIdentifier())
            .projectIdentifier(
                useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummary.getProjectIdentifier())
            .parentUniqueId(executionSummary.getParentUniqueId())
            .build());
  }

  @Override
  public Duration syncRecordsToObjectStore(Set<String> planExecutionIDs) {
    Instant syncStartTs = Instant.now();
    try {
      CompletableFutures<RetentionResponseWrapper> completableFutures = new CompletableFutures<>(executor);
      for (String planExecutionId : planExecutionIDs) {
        completableFutures.supplyAsync(() -> {
          try {
            PipelineExecutionSummaryEntity executionSummaryEntity =
                pmsExecutionSummaryRepository.fetchByPlanExecutionIdFromSecondary(planExecutionId);
            if (executionSummaryEntity == null) {
              throw new EntityNotFoundException(
                  String.format("[DATA_RETENTION]: PipelineExecutionSummaryEntity not found for planExecutionId: %s",
                      planExecutionId));
            }

            ScopeInfo scopeInfo = null;
            try {
              scopeInfo = scopeResolutionHelper.getScopeInfo(
                  executionSummaryEntity.getAccountId(), executionSummaryEntity.getParentUniqueId());
            } catch (Exception ex) {
              log.debug("Error resolving ScopeInfo for parentUniqueId='{}', account='{}' — skipping scope resolution "
                      + "and using entity values",
                  executionSummaryEntity.getParentUniqueId(), executionSummaryEntity.getAccountId(), ex);
              scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(executionSummaryEntity.getAccountId())
                              .orgIdentifier(executionSummaryEntity.getOrgIdentifier())
                              .projectIdentifier(executionSummaryEntity.getProjectIdentifier())
                              .uniqueId(executionSummaryEntity.getParentUniqueId())
                              .build();
            }
            boolean useScopeInfo = scopeInfo != null;

            retentionMetadataService.upsert(executionSummaryEntity.getPlanExecutionId(),
                ExecutionRetentionMetadataUpdateDTO.builder()
                    .planExecutionId(executionSummaryEntity.getPlanExecutionId())
                    .accountId(executionSummaryEntity.getAccountId())
                    .endTs(executionSummaryEntity.getEndTs())
                    .bucketName(objectStoreClient.getBucketName())
                    .retentionFileData(saveExecutionDataToObjectStore(executionSummaryEntity))
                    .pipelineIdentifier(executionSummaryEntity.getPipelineIdentifier())
                    .orgIdentifier(
                        useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummaryEntity.getOrgIdentifier())
                    .projectIdentifier(
                        useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummaryEntity.getProjectIdentifier())
                    .parentUniqueId(useScopeInfo ? scopeInfo.getUniqueId() : executionSummaryEntity.getParentUniqueId())
                    .build());
            return RetentionResponseWrapper.builder().syncComplete(true).build();
          } catch (Exception e) {
            log.error(String.format(ASYNC_ERROR_MESSAGE, planExecutionId), e);
            return RetentionResponseWrapper.builder()
                .syncComplete(false)
                .errorMessage(String.format(ASYNC_RESPONSE_ERROR_MESSAGE, planExecutionId, e.getMessage()))
                .build();
          }
        });
      }
      List<RetentionResponseWrapper> retentionFileResponses =
          completableFutures.allOf().get(dataRetentionConfig.getSyncRecordsTimeoutMinutes(), TimeUnit.MINUTES);
      for (RetentionResponseWrapper response : retentionFileResponses) {
        if (!response.isSyncComplete()) {
          throw new InternalServerErrorException(response.getErrorMessage());
        }
      }
    } catch (Exception e) {
      throw new InternalServerErrorException(
          String.format("[DATA_RETENTION]: Error while syncing executions: %s to object store", planExecutionIDs), e);
    }
    return Duration.between(syncStartTs, Instant.now());
  }

  @Override
  public void syncSummaryEntityToObjectStore(
      PipelineExecutionSummaryEntity executionSummaryEntity, ExecutionRetentionMetadata executionRetentionMetadata) {
    if (!dataRetentionConfig.isEnabled()) {
      return;
    }
    String accountIdentifier = executionSummaryEntity.getAccountId();
    Long endTs = executionSummaryEntity.getEndTs();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();
    RetentionFileData updatedRetentionFileData =
        saveDBRecordToObjectStore(executionSummaryEntity, accountIdentifier, endTs, planExecutionId, EXECUTION_SUMMARY);

    List<RetentionFileData> updatedFileDataList = new ArrayList<>(executionRetentionMetadata.getRetentionFileData());
    updatedFileDataList.removeIf(
        file -> planExecutionId.equals(file.getUuid()) && EXECUTION_SUMMARY.equals(file.getCollection()));
    updatedFileDataList.add(updatedRetentionFileData);

    retentionMetadataService.update(planExecutionId,
        ExecutionRetentionMetadataUpdateDTO.builder()
            .planExecutionId(planExecutionId)
            .accountId(accountIdentifier)
            .endTs(endTs)
            .bucketName(executionRetentionMetadata.getBucketName())
            .retentionFileData(updatedFileDataList)
            .pipelineIdentifier(executionRetentionMetadata.getPipelineIdentifier())
            .orgIdentifier(executionRetentionMetadata.getOrgIdentifier())
            .projectIdentifier(executionRetentionMetadata.getProjectIdentifier())
            .parentUniqueId(executionRetentionMetadata.getParentUniqueId())
            .build());
  }

  private List<RetentionFileData> saveExecutionDataToObjectStore(
      PipelineExecutionSummaryEntity executionSummaryEntity) {
    String accountIdentifier = executionSummaryEntity.getAccountId();
    Long endTs = executionSummaryEntity.getEndTs();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();
    List<RetentionFileData> retentionFileData =
        saveApprovalInstancesToObjectStore(accountIdentifier, endTs, planExecutionId, APPROVAL_INSTANCES);
    /*
     * We are saving execution summary separately as the ExecutionRetentionReconciliationIterator will give us the
     * full summary entity, so to not duplicate the code multiple times we are reusing the same method for the sync
     * iterator
     */
    retentionFileData.add(saveDBRecordToObjectStore(
        executionSummaryEntity, accountIdentifier, endTs, planExecutionId, EXECUTION_SUMMARY));
    retentionFileData.addAll(
        saveExecutionSubGraphsToObjectStore(accountIdentifier, endTs, planExecutionId, EXECUTION_SUB_GRAPH));
    for (ExecutionRetentionObjectStoreCollection collection : RECORDS_TO_FETCH_FROM_DB_AND_STORE_IN_OBJECT_STORE) {
      retentionFileData.add(
          fetchRecordFromDBAndSaveToObjectStore(accountIdentifier, endTs, planExecutionId, collection));
    }
    return retentionFileData;
  }

  private List<RetentionFileData> saveApprovalInstancesToObjectStore(
      String accountId, Long endTs, String planExecutionId, ExecutionRetentionObjectStoreCollection collection) {
    List<ApprovalInstance> approvalInstances =
        approvalInstanceService.getApprovalInstancesByExecutionId(planExecutionId);
    return approvalInstances.stream()
        .map(approvalInstance
            -> saveDBRecordToObjectStore(
                approvalInstance, accountId, endTs, planExecutionId, approvalInstance.getId(), collection))
        .collect(Collectors.toList());
  }

  private List<RetentionFileData> saveExecutionSubGraphsToObjectStore(
      String accountId, Long endTs, String planExecutionId, ExecutionRetentionObjectStoreCollection collection) {
    List<String> subGraphNodeExecutionIDs =
        fetchExecutionSubGraphNodeExecutionIDsFromSecondary(planExecutionId, collection, accountId);
    return subGraphNodeExecutionIDs.stream()
        .map(nodeExecutionID
            -> fetchRecordFromDBAndSaveToObjectStore(accountId, endTs, planExecutionId, nodeExecutionID, collection))
        .collect(Collectors.toList());
  }

  private RetentionFileData fetchRecordFromDBAndSaveToObjectStore(String accountId, Long endTs, String planExecutionId,
      String uuid, ExecutionRetentionObjectStoreCollection collection) {
    Object dbRecord = fetchRecordFromDB(planExecutionId, uuid, collection, accountId);
    return saveDBRecordToObjectStore(dbRecord, accountId, endTs, planExecutionId, uuid, collection);
  }

  // This method is in-case planExecution ID is same as UUID
  private RetentionFileData fetchRecordFromDBAndSaveToObjectStore(
      String accountId, Long endTs, String planExecutionId, ExecutionRetentionObjectStoreCollection collection) {
    if (!RECORDS_WITH_UUID_AS_PLAN_EXECUTION_ID_IN_OBJECT_STORE.contains(collection)) {
      throw new InternalServerErrorException(
          String.format("[DATA_RETENTION]: Collection: %s, does not have uuid same as planExecutionID",
              collection.getCollectionName()));
    }
    return fetchRecordFromDBAndSaveToObjectStore(accountId, endTs, planExecutionId, planExecutionId, collection);
  }

  // This method is in-case planExecution ID is same as UUID
  private RetentionFileData saveDBRecordToObjectStore(Object dbRecord, String accountId, Long endTs,
      String planExecutionId, ExecutionRetentionObjectStoreCollection collection) {
    if (!RECORDS_WITH_UUID_AS_PLAN_EXECUTION_ID_IN_OBJECT_STORE.contains(collection)) {
      throw new InternalServerErrorException(
          String.format("[DATA_RETENTION]: Collection: %s, does not have uuid same as planExecutionID",
              collection.getCollectionName()));
    }
    return saveDBRecordToObjectStore(dbRecord, accountId, endTs, planExecutionId, planExecutionId, collection);
  }

  private RetentionFileData saveDBRecordToObjectStore(Object dbRecord, String accountId, Long endTs,
      String planExecutionId, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    byte[] dbRecordInBytes = ExecutionRetentionUtils.convertDBRecordToBytes(dbRecord, collection);

    // The below object compresses the bytes if the collection's file format is ZSTD
    // Otherwise it's a No-OP operation and returns the passed bytes itself
    byte[] compressedDBRecord = ExecutionRetentionUtils.compressBytesIfRequired(dbRecordInBytes, collection);
    String fileUUID = ExecutionRetentionUtils.buildFileUUIDForCollection(planExecutionId, uuid, collection);
    String filePathForObjectStore =
        ExecutionRetentionUtils.buildFilePathForObjectStore(accountId, endTs, fileUUID, collection);
    StorageObject object = objectStoreClient.uploadObject(filePathForObjectStore, compressedDBRecord);

    return ExecutionRetentionUtils.getRetentionFileData(
        fileUUID, object, collection, getFileMetadata(dbRecord, dbRecordInBytes, collection));
  }

  private Map<String, Object> getFileMetadata(
      Object dbRecord, byte[] dbRecordInBytes, ExecutionRetentionObjectStoreCollection collection) {
    Map<String, Object> metadata = new HashMap<>();
    if (APPROVAL_INSTANCES.equals(collection)) {
      // This subtype is stored because mongodb handles nested types automatically using the _class field
      // But json needs explicit type to parse string to object, so we are storing the subtype here
      // The approval instance doesn't have JsonSubTypes annotation due to which we need to store its subtype
      metadata.put(ExecutionRetentionConstants.APPROVAL_INSTANCE_SUBTYPE_METADATA_KEY,
          ((ApprovalInstance) dbRecord).getType().getDisplayName());
    }
    if (RetentionFileFormat.JSON_ZSTD.equals(collection.getFileFormat())) {
      // ZST needs the decompressed size to decompress bytes back, for which we need to store its original bytes
      metadata.put(ZST_DECOMPRESSED_SIZE_METADATA_KEY, dbRecordInBytes.length);
    }
    return metadata;
  }

  private Object fetchRecordFromDB(
      String planExecutionId, String uuid, ExecutionRetentionObjectStoreCollection collection, String accountId) {
    switch (collection) {
      case EXECUTION_SUMMARY -> {
        PipelineExecutionSummaryEntity executionSummaryEntity =
            pmsExecutionSummaryRepository.fetchByPlanExecutionIdFromSecondary(planExecutionId);
        validateRecordNotNull(collection, executionSummaryEntity);
        return executionSummaryEntity;
      }
      case EXECUTION_GRAPH -> {
        OrchestrationGraph orchestrationGraph = getCachedGraphFromSecondary(planExecutionId, accountId);
        logRecordIfNull(collection, orchestrationGraph, planExecutionId);
        return orchestrationGraph;
      }
      case EXECUTION_SUB_GRAPH -> {
        return getCachedGraphFromSecondary(String.format(GRAPH_SUB_GRAPH_KEY_FORMAT, planExecutionId, uuid), accountId);
      }
      case EXECUTION_METADATA -> {
        PlanExecutionMetadata planExecutionMetadata =
            planExecutionMetadataRepository.fetchByPlanExecutionIdFromSecondary(uuid);
        logRecordIfNull(collection, planExecutionMetadata, planExecutionId);
        return planExecutionMetadata;
      }
      default ->
        throw new InternalServerErrorException(
            String.format("[DATA_RETENTION]: Collection: %s, is not currently supported for retaining in object store",
                          collection.getCollectionName()));
    }
  }

  private static void validateRecordNotNull(ExecutionRetentionObjectStoreCollection collection, Object object) {
    if (object == null) {
      throw new InternalServerErrorException(
          String.format("[DATA_RETENTION]: Record for collection: %s is null", collection.getCollectionName()));
    }
  }

  private static void logRecordIfNull(ExecutionRetentionObjectStoreCollection collection, Object object,
                                      String planExecutionId) {
    if (object == null) {
      log.error("[DATA_RETENTION]: Record for collection: {} for planExecutionId: {} is null",
                collection.getCollectionName(), planExecutionId);
    }
  }

  private OrchestrationGraph getCachedGraphFromSecondary(String key, String accountIdentifier) {
    if (null != accountIdentifier
        && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
      EntityWithAccountId pgResult = postgreSQLGraphStoreService.getWithAccountId(key);
      if (pgResult != null) {
        return (OrchestrationGraph) pgResult.getEntity();
      } else {
        log.error("Failed to retrieve graph from PostgreSQL for planExecutionId: {}", key);
      }
    }

    EntityWithAccountId entity =
        mongoStore.getFromSecondary(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, key, null);
    return entity == null ? null :
        (OrchestrationGraph) entity.getEntity();
    }

    /*
     A separate sub-graph is stored for step group retries.
     */
    private List<String> fetchExecutionSubGraphNodeExecutionIDsFromSecondary(
        String planExecutionId, ExecutionRetentionObjectStoreCollection collection, String accountId) {
      if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
        // Fetch from PostgreSQL using LIKE pattern
        // Pattern: "planExecutionId/%" to match all subgraphs for this execution
        String pattern = planExecutionId + "/%";
        List<String> cacheKeys = postgreSQLGraphStoreService.findCacheKeysByPattern(pattern);

        // Extract nodeExecutionIds from cache keys
        // Cache key format: "planExecutionId/nodeExecutionId"
        List<String> nodeExecutionIds = new ArrayList<>();
        for (String cacheKey : cacheKeys) {
          String[] parts = cacheKey.split("/");
          if (parts.length == 2) {
            nodeExecutionIds.add(parts[1]); // Get the nodeExecutionId part
          }
        }
        if (!nodeExecutionIds.isEmpty()) {
          return nodeExecutionIds;
        }
      }
      String key = String.format(SUB_GRAPH_KEY_FORMAT, planExecutionId);
      List<String> canonicalKeys =
          mongoStore.findCanonicalKeysUsingRegexPatternFromSecondary(OrchestrationGraph.ALGORITHM_ID,
              OrchestrationGraph.STRUCTURE_HASH, key, null, SUB_GRAPH_CANONICAL_KEY_REGEX_PATTERN);
      return ExecutionRetentionUtils.extractNodeExecutionIDsForSubGraph(canonicalKeys);
    }
  }
