/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.gcs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionMetadataUpdateDTO;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.utils.ExecutionRetentionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ParentUniqueIdMigrationForPipelineExecutionSummaryForGCS implements Managed {
  private static final String LOCK_NAME = "GCS_PARENT_UNIQUE_ID_MIGRATION_LOCK";
  private static final String MIGRATION_NAME = "GCS_PARENT_UNIQUE_ID_MIGRATION";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";

  private static final String FAILED_TO_GET_UPDATED_PREFIX = "failedUpdate_";

  @Inject private PersistentLocker persistentLocker;
  @Inject private ExecutionRetentionMetadataService executionRetentionMetadataService;
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  @Inject private ScopeInfoClient scopeInfoClient;
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject private ApprovalInstanceService approvalInstanceService;
  final Map<String, String> scopeEntityUniqueIdMap = new HashMap<>();
  final String LOCAL_MAP_DELIMITER = "|";

  private ScheduledExecutorService executorService;
  ExecutionRetentionObjectStoreCollection executionSummary = ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY;
  ExecutionRetentionObjectStoreCollection approvalInstances =
      ExecutionRetentionObjectStoreCollection.APPROVAL_INSTANCES;
  ExecutionRetentionObjectStoreCollection executionMetadata =
      ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA;

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("gcs-parent-unique-id-migration-job").build());
    executorService.scheduleWithFixedDelay(
        this::run, 20, pipelineServiceConfiguration.getGcsMigrationConfig().getFrequencyInMin(), TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.info(LOCK_NAME + "failed to acquire lock");
        return;
      }
      executeMigration();

    } catch (Exception ex) {
      log.warn("Failed to acquire" + LOCK_NAME, ex);
    }
  }

  private void executeMigration() {
    log.info("Starting {} migration ", MIGRATION_NAME);
    List<ExecutionRetentionMetadata> batchWithMissingParentUniqueIds = getBatchWithMissingParentUniqueIds();

    try {
      for (ExecutionRetentionMetadata executionRetentionMetadata : batchWithMissingParentUniqueIds) {
        try {
          processExecutionRetentionMetadata(executionRetentionMetadata);
        } catch (Exception ex) {
          log.error("Failed to update PipelineExecutionSummary for planExecutionId: {}",
              executionRetentionMetadata.getPlanExecutionId(), ex);
          String parentUniqueId = FAILED_TO_GET_UPDATED_PREFIX + executionRetentionMetadata.getPlanExecutionId();
          executionRetentionMetadataService.upsert(executionRetentionMetadata.getPlanExecutionId(),
              ExecutionRetentionMetadataUpdateDTO.builder().parentUniqueId(parentUniqueId).build());
        }
      }
    } catch (Exception ex) {
      log.error(MIGRATION_NAME
              + (": Failed to update parentUniqueId in GCS for PipelineExecutionSummaryEntity & ApprovalInstance  "
                  + "data."),
          ex);
    }
    log.info("{} migration completed", MIGRATION_NAME);
  }

  private List<ExecutionRetentionMetadata> getBatchWithMissingParentUniqueIds() {
    log.info("Fetching batch of execution retention metadata without parent unique ID");
    return executionRetentionMetadataService.getBatchOfExecutionRetentionMetadataWithoutParentUniqueId(
        pipelineServiceConfiguration.getGcsMigrationConfig().getBatchSize());
  }

  private void processExecutionRetentionMetadata(ExecutionRetentionMetadata executionRetentionMetadata) {
    log.info("Processing execution retention metadata for planExecutionId: {}",
        executionRetentionMetadata.getPlanExecutionId());

    int retentionFileDataListSize = executionRetentionMetadata.getRetentionFileData().size();

    String parentUniqueId = null;

    List<RetentionFileData> retentionFileDataForExecutionSummary =
        fetchRetentionFileData(executionRetentionMetadata, executionSummary);

    List<RetentionFileData> retentionFileDataForApprovalInstance =
        fetchRetentionFileData(executionRetentionMetadata, approvalInstances);

    List<RetentionFileData> retentionFileDataForExecutionMetadata =
        fetchRetentionFileData(executionRetentionMetadata, executionMetadata);

    if (retentionFileDataForExecutionSummary != null) {
      log.debug("Processing retention file data for ExecutionSummaryEntity for planExecutionId: {}",
          executionRetentionMetadata.getPlanExecutionId());
      executionRetentionMetadata.getRetentionFileData().removeAll(retentionFileDataForExecutionSummary);

      Map<String, StorageObject> objectPathToBlobMap = fetchObjectsFromGCS(retentionFileDataForExecutionSummary);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryFromMongo =
          pmsExecutionSummaryRespository.fetchByPlanExecutionIdFromSecondary(
              executionRetentionMetadata.getPlanExecutionId());

      PipelineExecutionSummaryEntity pipelineExecutionSummaryFromGCS = convertBlobToEntityPipelineExecutionSummary(
          objectPathToBlobMap.get(retentionFileDataForExecutionSummary.get(0).getFilePath()),
          retentionFileDataForExecutionSummary.get(0));

      parentUniqueId = getParentUniqueIdForPipelineExecutionSummaryEntity(
          pipelineExecutionSummaryFromGCS, pipelineExecutionSummaryFromMongo);

      log.debug("Derived parentUniqueId: {} for ExecutionSummaryEntity with planExecutionId: {} ", parentUniqueId,
          executionRetentionMetadata.getPlanExecutionId());
      retentionFileDataForExecutionSummary = processRetentionFileDataForPipelineExecutionSummary(
          retentionFileDataForExecutionSummary, objectPathToBlobMap, parentUniqueId);

      executionRetentionMetadata.getRetentionFileData().addAll(retentionFileDataForExecutionSummary);
    }

    if (retentionFileDataForApprovalInstance != null) {
      log.debug("Processing retention file data for ApprovalInstance for planExecutionId: {}",
          executionRetentionMetadata.getPlanExecutionId());

      Map<String, StorageObject> objectPathToBlobMap = fetchObjectsFromGCS(retentionFileDataForApprovalInstance);

      executionRetentionMetadata.getRetentionFileData().removeAll(retentionFileDataForApprovalInstance);
      List<ApprovalInstance> approvalInstancesFromMongo =
          approvalInstanceService.getApprovalInstancesByExecutionId(executionRetentionMetadata.getPlanExecutionId());

      ApprovalInstance approvalInstanceFromGCS = convertBlobToEntityApprovalInstance(
          objectPathToBlobMap.get(retentionFileDataForApprovalInstance.get(0).getFilePath()),
          retentionFileDataForApprovalInstance.get(0));

      if (parentUniqueId == null) {
        log.error("ParentUniqueId has not been set via PipelineExecutionSummaryEntity and ApprovalInstance entry "
                + "exists for planExecutionId: {}",
            executionRetentionMetadata.getPlanExecutionId());
        parentUniqueId =
            getParentUniqueIdForApprovalInstanceEntity(approvalInstanceFromGCS, approvalInstancesFromMongo);
        log.info("Derived parentUniqueId: {} for ApprovalInstance", parentUniqueId);
      }
      retentionFileDataForApprovalInstance = processRetentionFileDataForApprovalInstance(
          retentionFileDataForApprovalInstance, objectPathToBlobMap, parentUniqueId);

      executionRetentionMetadata.getRetentionFileData().addAll(retentionFileDataForApprovalInstance);
    }

    if (retentionFileDataForExecutionMetadata != null) {
      log.debug("Processing retention file data for Execution Metadata for planExecutionId: {}",
          executionRetentionMetadata.getPlanExecutionId());

      Map<String, StorageObject> objectPathToBlobMap = fetchObjectsFromGCS(retentionFileDataForExecutionMetadata);

      executionRetentionMetadata.getRetentionFileData().removeAll(retentionFileDataForExecutionMetadata);

      if (parentUniqueId == null) {
        log.error("ParentUniqueId has not been set via PipelineExecutionSummaryEntity and ApprovalInstance for "
                + "planExecutionId: {} Execution Retention Metadata can not be updated",
            executionRetentionMetadata.getPlanExecutionId());
        return;
      }
      retentionFileDataForExecutionMetadata = processRetentionFileDataForPlanExecutionMetadata(
          retentionFileDataForExecutionMetadata, objectPathToBlobMap, parentUniqueId);

      executionRetentionMetadata.getRetentionFileData().addAll(retentionFileDataForExecutionMetadata);
    }

    if (retentionFileDataListSize == executionRetentionMetadata.getRetentionFileData().size()) {
      updateMetadataInDB(executionRetentionMetadata, parentUniqueId, executionRetentionMetadata.getRetentionFileData());
    } else {
      log.error(
          "Mismatch in retention file data size: Updated list size does not match the original. Plan Execution ID: {}",
          executionRetentionMetadata.getPlanExecutionId());
      throw new InvalidRequestException(
          "Mismatch in retention file data size: Updated list size does not match the original.");
    }
  }

  private List<RetentionFileData> processRetentionFileDataForPipelineExecutionSummary(
      List<RetentionFileData> retentionFileDataForExecutionSummary, Map<String, StorageObject> objectPathToBlobMap,
      String parentUniqueId) {
    List<RetentionFileData> retentionFileDataForExecutionSummaryWithUpdatedBlobSize = new ArrayList<>();

    for (RetentionFileData retentionFileData : retentionFileDataForExecutionSummary) {
      Long updatedBlobSize;

      PipelineExecutionSummaryEntity pipelineExecutionSummary = convertBlobToEntityPipelineExecutionSummary(
          objectPathToBlobMap.get(retentionFileData.getFilePath()), retentionFileData);
      if (pipelineExecutionSummary.getParentUniqueId() == null) {
        pipelineExecutionSummary.setParentUniqueId(parentUniqueId);
        try {
          updatedBlobSize =
              uploadDataToGCS(pipelineExecutionSummary, retentionFileData.getFilePath(), executionSummary).getSize();
        } catch (Exception ex) {
          log.error(MIGRATION_NAME + ": Failed to update parentUniqueId for path {} in PipelineExecutionSummaryEntity",
              retentionFileData.getFilePath(), ex);
          throw ex;
        }

        retentionFileDataForExecutionSummaryWithUpdatedBlobSize.add(
            RetentionFileData.builder()
                .uuid(retentionFileData.getUuid())
                .filePath(retentionFileData.getFilePath())
                .fileSize(updatedBlobSize)
                .collection(retentionFileData.getCollection())
                .collectionName(retentionFileData.getCollectionName())
                .metadata(retentionFileData.getMetadata())
                .fileFormat(retentionFileData.getFileFormat())
                .build());
      } else {
        retentionFileDataForExecutionSummaryWithUpdatedBlobSize.add(retentionFileData);
      }
    }

    return retentionFileDataForExecutionSummaryWithUpdatedBlobSize.isEmpty()
        ? retentionFileDataForExecutionSummary
        : retentionFileDataForExecutionSummaryWithUpdatedBlobSize;
  }

  private List<RetentionFileData> processRetentionFileDataForApprovalInstance(
      List<RetentionFileData> retentionFileDataForApprovalInstance, Map<String, StorageObject> objectPathToBlobMap,
      String parentUniqueId) {
    List<RetentionFileData> retentionFileDataForApprovalInstanceWithUpdatedBlobSize = new ArrayList<>();

    for (RetentionFileData retentionFileData : retentionFileDataForApprovalInstance) {
      Long updatedBlobSize;
      ApprovalInstance approvalInstance = convertBlobToEntityApprovalInstance(
          objectPathToBlobMap.get(retentionFileData.getFilePath()), retentionFileData);
      if (approvalInstance.getParentUniqueId() == null) {
        approvalInstance.setParentUniqueId(parentUniqueId);
        try {
          updatedBlobSize =
              uploadDataToGCS(approvalInstance, retentionFileData.getFilePath(), approvalInstances).getSize();
        } catch (Exception ex) {
          log.error(MIGRATION_NAME + ": Failed to update parentUniqueId for path {} in ApprovalInstance",
              retentionFileData.getFilePath(), ex);
          throw ex;
        }
        retentionFileDataForApprovalInstanceWithUpdatedBlobSize.add(
            RetentionFileData.builder()
                .uuid(retentionFileData.getUuid())
                .filePath(retentionFileData.getFilePath())
                .fileSize(updatedBlobSize)
                .collection(retentionFileData.getCollection())
                .collectionName(retentionFileData.getCollectionName())
                .metadata(retentionFileData.getMetadata())
                .fileFormat(retentionFileData.getFileFormat())
                .build());
      } else {
        retentionFileDataForApprovalInstanceWithUpdatedBlobSize.add(retentionFileData);
      }
    }

    return retentionFileDataForApprovalInstanceWithUpdatedBlobSize.isEmpty()
        ? retentionFileDataForApprovalInstance
        : retentionFileDataForApprovalInstanceWithUpdatedBlobSize;
  }

  private List<RetentionFileData> processRetentionFileDataForPlanExecutionMetadata(
      List<RetentionFileData> retentionFileDataForPlanExecutionMetadata, Map<String, StorageObject> objectPathToBlobMap,
      String parentUniqueId) {
    List<RetentionFileData> retentionFileDataForPlanExecutionMetadataWithUpdatedBlobSize = new ArrayList<>();

    for (RetentionFileData retentionFileData : retentionFileDataForPlanExecutionMetadata) {
      Long updatedBlobSize;
      PlanExecutionMetadata planExecutionMetadata = convertBlobToEntityPlanExecutionMetadata(
          objectPathToBlobMap.get(retentionFileData.getFilePath()), retentionFileData);
      if (planExecutionMetadata.getParentUniqueId() == null) {
        planExecutionMetadata.setParentUniqueId(parentUniqueId);
        try {
          updatedBlobSize =
              uploadDataToGCS(planExecutionMetadata, retentionFileData.getFilePath(), executionMetadata).getSize();
        } catch (Exception ex) {
          log.error(MIGRATION_NAME + ": Failed to update parentUniqueId for path {} in Plan Execution Metadata",
              retentionFileData.getFilePath(), ex);
          throw ex;
        }
        retentionFileDataForPlanExecutionMetadataWithUpdatedBlobSize.add(
            RetentionFileData.builder()
                .uuid(retentionFileData.getUuid())
                .filePath(retentionFileData.getFilePath())
                .fileSize(updatedBlobSize)
                .collection(retentionFileData.getCollection())
                .collectionName(retentionFileData.getCollectionName())
                .metadata(retentionFileData.getMetadata())
                .fileFormat(retentionFileData.getFileFormat())
                .build());
      } else {
        retentionFileDataForPlanExecutionMetadataWithUpdatedBlobSize.add(retentionFileData);
      }
    }

    return retentionFileDataForPlanExecutionMetadataWithUpdatedBlobSize.isEmpty()
        ? retentionFileDataForPlanExecutionMetadata
        : retentionFileDataForPlanExecutionMetadataWithUpdatedBlobSize;
  }

  private Map<String, StorageObject> fetchObjectsFromGCS(List<RetentionFileData> retentionFileData) {
    List<String> objectPaths = retentionFileData.stream().map(RetentionFileData::getFilePath).toList();
    return objectStoreClient.getObjectsByPaths(objectPaths);
  }

  private PipelineExecutionSummaryEntity convertBlobToEntityPipelineExecutionSummary(
      StorageObject object, RetentionFileData fileData) {
    return (PipelineExecutionSummaryEntity) ExecutionRetentionUtils.convertBytesToObject(
        ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY, object.getContent(), fileData.getMetadata(),
        PipelineExecutionSummaryEntity.class);
  }

  private ApprovalInstance convertBlobToEntityApprovalInstance(StorageObject object, RetentionFileData fileData) {
    return (ApprovalInstance) ExecutionRetentionUtils.convertBytesToObject(
        ExecutionRetentionObjectStoreCollection.APPROVAL_INSTANCES, object.getContent(), fileData.getMetadata(),
        ApprovalInstance.class);
  }

  private PlanExecutionMetadata convertBlobToEntityPlanExecutionMetadata(
      StorageObject object, RetentionFileData fileData) {
    return (PlanExecutionMetadata) ExecutionRetentionUtils.convertBytesToObject(
        ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA, object.getContent(), fileData.getMetadata(),
        PlanExecutionMetadata.class);
  }

  private void updateMetadataInDB(ExecutionRetentionMetadata executionRetentionMetadata, String parentUniqueId,
      List<RetentionFileData> retentionFileData) {
    executionRetentionMetadataService.upsert(executionRetentionMetadata.getPlanExecutionId(),
        ExecutionRetentionMetadataUpdateDTO.builder()
            .parentUniqueId(parentUniqueId)
            .retentionFileData(retentionFileData)
            .build());
  }

  private List<RetentionFileData> fetchRetentionFileData(ExecutionRetentionMetadata retentionMetadata,
      ExecutionRetentionObjectStoreCollection executionRetentionObjectStoreCollection) {
    List<RetentionFileData> retentionFileData =
        retentionMetadata.getRetentionFileData()
            .stream()
            .filter(file -> executionRetentionObjectStoreCollection.equals(file.getCollection()))
            .toList();
    if (retentionFileData.isEmpty()) {
      log.error("{}: The requested retention metadata, doesn't exist for {} with planExecutionId {}", MIGRATION_NAME,
          executionRetentionObjectStoreCollection.getCollectionName(), retentionMetadata.getPlanExecutionId());
      return null;
    }
    return retentionFileData;
  }

  private StorageObject uploadDataToGCS(
      Object dbObject, String filePathForObjectStore, ExecutionRetentionObjectStoreCollection collection) {
    byte[] dbRecordInBytes = ExecutionRetentionUtils.convertDBRecordToBytes(dbObject, collection);

    byte[] compressedDBRecord = ExecutionRetentionUtils.compressBytesIfRequired(dbRecordInBytes, collection);

    return objectStoreClient.uploadObject(filePathForObjectStore, compressedDBRecord);
  }

  private String getParentUniqueIdForPipelineExecutionSummaryEntity(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryFromGCS,
      PipelineExecutionSummaryEntity pipelineExecutionSummaryFromMongo) {
    if (pipelineExecutionSummaryFromMongo != null && pipelineExecutionSummaryFromMongo.getParentUniqueId() != null) {
      return pipelineExecutionSummaryFromMongo.getParentUniqueId();
    } else {
      return getParentUniqueIdFromScopeInfoCall(pipelineExecutionSummaryFromGCS.getAccountId(),
          pipelineExecutionSummaryFromGCS.getOrgIdentifier(), pipelineExecutionSummaryFromGCS.getProjectIdentifier());
    }
  }

  private String getParentUniqueIdForApprovalInstanceEntity(
      ApprovalInstance approvalInstanceFromGCS, List<ApprovalInstance> approvalInstanceFromMongo) {
    if (!approvalInstanceFromMongo.isEmpty() && approvalInstanceFromMongo.get(0).getParentUniqueId() != null) {
      return approvalInstanceFromMongo.get(0).getParentUniqueId();
    } else {
      // this job once completed should be removed and hence these fields will not be used.
      return getParentUniqueIdFromScopeInfoCall(approvalInstanceFromGCS.getAccountId(),
          approvalInstanceFromGCS.getOrgIdentifier(), approvalInstanceFromGCS.getProjectIdentifier());
    }
  }

  String getParentUniqueIdFromScopeInfoCall(String account, String org, String proj) {
    String mapKey = getMapKey(account, org, proj);
    ScopeInfo scopeInfo = null;
    try {
      if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
        return scopeEntityUniqueIdMap.get(mapKey);
      }

      scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(account, org, proj));
    } catch (InvalidRequestException exception) {
      // If a valid scope is not found, we will mark the entity as orphan.
    }
    if (scopeInfo == null) {
      // UUIDGenerator ensures there are no issues while creating the compound index.
      return ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
    }
    scopeEntityUniqueIdMap.put(mapKey, scopeInfo.getUniqueId());
    return scopeInfo.getUniqueId();
  }

  private String getMapKey(String account, String org, String proj) {
    if (isNotEmpty(org) && isNotEmpty(proj)) {
      return account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
    } else if (isNotEmpty(org)) {
      return account + LOCAL_MAP_DELIMITER + org;
    } else {
      return account;
    }
  }
}
