/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionCleanupResult;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dataretention.utils.ExecutionRetentionUtils;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.StorageObject;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionRetentionServiceImpl implements ExecutionRetentionService {
  @Inject private ExecutionRetentionMetadataService retentionMetadataService;
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;
  @Inject DataRetentionConfig dataRetentionConfig;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  private static final int DELETE_BATCH_SIZE = 500;

  @Override
  public Object readExpiredRecordFromObjectStore(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection, Class<?> classType) {
    if (isDataRetentionDisabled(accountIdentifier)) {
      return null;
    }
    ExecutionRetentionMetadata retentionMetadata = getRetentionMetadata(accountIdentifier, uuid, collection);
    if (retentionMetadata == null || !hasMongoTTLExpired(retentionMetadata.getEndTs(), collection)) {
      return null;
    }
    RetentionFileData fileData = getRetentionFileData(retentionMetadata, uuid, collection);
    if (fileData == null) {
      return null;
    }
    return readObjectFromStore(fileData, collection, classType);
  }

  @Override
  public Map<String, Object> readExpiredRecordsFromObjectStore(String accountIdentifier, List<String> uuids,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType) {
    if (isDataRetentionDisabled(accountIdentifier) || isEmpty(uuids)) {
      return Collections.emptyMap();
    }

    // Fetching ExecutionRetentionMetadata
    List<RetentionFileData> retentionFileDataList = getRetentionFileDataListForExpiredRecords(uuids, collection);
    return getUuidsToObjectsMap(retentionFileDataList, collection, classType);
  }

  private List<RetentionFileData> getRetentionFileDataListForExpiredRecords(
      List<String> uuids, ExecutionRetentionObjectStoreCollection collection) {
    List<ExecutionRetentionMetadata> retentionMetadataList = retentionMetadataService.getAll(uuids, collection);
    if (isEmpty(retentionMetadataList)) {
      return null;
    }

    // populating map for fileDataUuid to filedData while filtering on collection after which we will filter for
    // required uuids while returning
    Map<String, RetentionFileData> uuidToRetentionFileDataMap = new HashMap<>();
    for (ExecutionRetentionMetadata retentionMetadata : retentionMetadataList) {
      if (hasMongoTTLExpired(retentionMetadata.getEndTs(), collection)) {
        retentionMetadata.getRetentionFileData()
            .stream()
            .filter(fileData -> collection.equals(fileData.getCollection()))
            .forEach(fileData -> uuidToRetentionFileDataMap.putIfAbsent(fileData.getUuid(), fileData));
      }
    }
    return uuids.stream().filter(uuidToRetentionFileDataMap::containsKey).map(uuidToRetentionFileDataMap::get).toList();
  }

  private Map<String, Object> getUuidsToObjectsMap(List<RetentionFileData> retentionFileDataList,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType) {
    if (isEmpty(retentionFileDataList)) {
      return Collections.emptyMap();
    }

    // Fetching objects from object store
    List<String> objectPaths = retentionFileDataList.stream().map(RetentionFileData::getFilePath).toList();
    Map<String, StorageObject> objectPathToObjectMap = objectStoreClient.getObjectsByPaths(objectPaths);

    Map<String, Object> uuidsToObjectsMap = new HashMap<>();
    retentionFileDataList.forEach(retentionFileData -> {
      if (objectPathToObjectMap.containsKey(retentionFileData.getFilePath())) {
        StorageObject object = objectPathToObjectMap.get(retentionFileData.getFilePath());
        if (object != null) {
          uuidsToObjectsMap.putIfAbsent(retentionFileData.getUuid(),
              ExecutionRetentionUtils.convertBytesToObject(
                  collection, object.getContent(), retentionFileData.getMetadata(), classType));
        } else {
          // Here object null means, we could not find the value object corresponding to this uuid so putting it's
          // values as null
          log.warn(String.format(
              "[DATA_RETENTION]: The requested record for uuid: %s, doesn't exist in object store for collection: %s",
              retentionFileData.getUuid(), collection.getName()));
          uuidsToObjectsMap.putIfAbsent(retentionFileData.getUuid(), null);
        }
      }
    });
    return uuidsToObjectsMap;
  }

  @Override
  public Map<String, Object> readRecordsFromObjectStore(List<ExecutionRetentionMetadata> retentionMetadataList,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType) {
    if (!dataRetentionConfig.isEnabled() || isEmpty(retentionMetadataList)) {
      return Collections.emptyMap();
    }

    // Fetching ExecutionRetentionMetadata
    List<RetentionFileData> retentionFileDataList = getRetentionFileDataList(retentionMetadataList, collection);
    return getUuidsToObjectsMap(retentionFileDataList, collection, classType);
  }

  private List<RetentionFileData> getRetentionFileDataList(
      List<ExecutionRetentionMetadata> retentionMetadataList, ExecutionRetentionObjectStoreCollection collection) {
    Map<String, RetentionFileData> uuidToRetentionFileDataMap = new HashMap<>();
    for (ExecutionRetentionMetadata retentionMetadata : retentionMetadataList) {
      retentionMetadata.getRetentionFileData()
          .stream()
          .filter(fileData -> collection.equals(fileData.getCollection()))
          .forEach(fileData -> uuidToRetentionFileDataMap.putIfAbsent(fileData.getUuid(), fileData));
    }
    if (isNotEmpty(uuidToRetentionFileDataMap)) {
      return uuidToRetentionFileDataMap.values().stream().toList();
    }
    return null;
  }

  private Map<String, List<String>> updateMetadataUuidsToObjectPaths(
      ExecutionRetentionMetadata executionRetentionMetadata) {
    Map<String, List<String>> metadataUuidsToObjectPaths = new HashMap<>();
    List<String> objectPaths =
        metadataUuidsToObjectPaths.computeIfAbsent(executionRetentionMetadata.getUuid(), v -> new ArrayList<>());
    for (RetentionFileData retentionFileData : executionRetentionMetadata.getRetentionFileData()) {
      objectPaths.add(retentionFileData.getFilePath());
    }
    return metadataUuidsToObjectPaths;
  }

  private int cleanUpObjectStoreAndMetadataInternal(
      Map<String, List<String>> metadataUuidsToObjectPathsMap, String accountIdentifier) {
    List<String> objectPaths = new ArrayList<>();
    Map<String, String> objectPathToMetadataUuidMap = new HashMap<>();

    // Populating object paths to be deleted and map of the objectPath to metadataUuid map as well
    for (Map.Entry<String, List<String>> entry : metadataUuidsToObjectPathsMap.entrySet()) {
      for (String objectPath : entry.getValue()) {
        objectPaths.add(objectPath);
        objectPathToMetadataUuidMap.put(objectPath, entry.getKey());
      }
    }

    // Deleting objects from the object store corresponding to objectPaths
    Map<String, Boolean> objectPathToDeletionResultMap = objectStoreClient.deleteObjectsByPaths(objectPaths);

    // It maintains the map of metadata uuids to object paths  which did not get deleted
    Map<String, List<String>> nonDeletableUuidsToObjectPathsMap = new HashMap<>();

    for (Map.Entry<String, Boolean> entry : objectPathToDeletionResultMap.entrySet()) {
      // checking if objectPath did not get deleted then metadata should not be deleted and corresponding
      // retentionFileData list should be updated only with object paths which are not deleted, populating the same
      // information in nonDeletableUuidsToObjectPathsMap
      if (Boolean.FALSE.equals(entry.getValue())) {
        String failedDeletionObjectPath = entry.getKey();
        String failedDeletionObjectPathMetadataUuid = objectPathToMetadataUuidMap.get(entry.getKey());
        nonDeletableUuidsToObjectPathsMap.computeIfAbsent(failedDeletionObjectPathMetadataUuid, v -> new ArrayList<>())
            .add(failedDeletionObjectPath);
      }
    }

    for (Map.Entry<String, List<String>> entry : nonDeletableUuidsToObjectPathsMap.entrySet()) {
      // updating the retention file data list with only object paths which were not deleted from object store
      retentionMetadataService.updateRetentionFileDataList(entry.getKey(), entry.getValue());
    }

    Set<String> toBeDeletedMetadataUuids = new HashSet<>();
    for (String metadataUuid : metadataUuidsToObjectPathsMap.keySet()) {
      // Filtering out the metadata uuids which should not be deleted, as there are some object paths corresponding to
      // it did not get deleted
      if (!nonDeletableUuidsToObjectPathsMap.containsKey(metadataUuid)) {
        toBeDeletedMetadataUuids.add(metadataUuid);
      }
    }
    retentionMetadataService.deleteAllExecutionRetentionMetadataByUuids(toBeDeletedMetadataUuids);
    if (accountIdentifier != null) {
      log.info("Cleaned up {} ExecutionRetentionMetadata for account: {}", toBeDeletedMetadataUuids.size(),
          accountIdentifier);
    } else {
      log.info("Cleaned up {} ExecutionRetentionMetadata", toBeDeletedMetadataUuids.size());
    }
    return toBeDeletedMetadataUuids.size();
  }

  private int cleanUpObjectStoreAndMetadata(Instant cleanupJobStartTs, Duration syncJobMaxRunTime,
      Iterator<ExecutionRetentionMetadata> iterator, String accountIdentifier) {
    Map<String, List<String>> metadataUuidsToObjectPathsMap = new HashMap<>();
    int totalCleanedUpMetadataCount = 0;
    while (iterator.hasNext()) {
      ExecutionRetentionMetadata executionRetentionMetadata = iterator.next();
      metadataUuidsToObjectPathsMap.putAll(updateMetadataUuidsToObjectPaths(executionRetentionMetadata));
      if (metadataUuidsToObjectPathsMap.size() == DELETE_BATCH_SIZE) {
        totalCleanedUpMetadataCount +=
            cleanUpObjectStoreAndMetadataInternal(metadataUuidsToObjectPathsMap, accountIdentifier);
        metadataUuidsToObjectPathsMap.clear();
      }
      if (isCleanupJobTimeMaxLimitExceeded(cleanupJobStartTs, syncJobMaxRunTime) || getMaintenanceFlag()) {
        log.warn("Interrupting the flow as system is in maintenance or clean up job has exceeded max runtime");
        break;
      }
    }
    if (!metadataUuidsToObjectPathsMap.isEmpty()) {
      totalCleanedUpMetadataCount +=
          cleanUpObjectStoreAndMetadataInternal(metadataUuidsToObjectPathsMap, accountIdentifier);
    }
    return totalCleanedUpMetadataCount;
  }

  @Override
  public void deleteAllPlanExecutionsData(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete) {
    if (retainPipelineExecutionDetailsAfterDelete || !dataRetentionConfig.isEnabled() || isEmpty(planExecutionIds)) {
      return;
    }
    try (Stream<ExecutionRetentionMetadata> stream =
             retentionMetadataService.getExecutionRetentionMetadataForExecutions(planExecutionIds)) {
      Iterator<ExecutionRetentionMetadata> iterator = stream.iterator();
      cleanUpObjectStoreAndMetadata(null, null, iterator, null);
    } catch (Exception ex) {
      log.error("[PIPELINE_DELETE]: Failed while deleting plan executions from object store", ex);
      throw new InternalServerErrorException(
          "[PIPELINE_DELETE]: Failed while deleting plan executions from object store", ex);
    }
  }

  @Override
  public int deleteExpiredTTLExecutions(
      String accountIdentifier, int retentionPeriodInMonths, Instant cleanupJobStartTs, Duration syncJobMaxRunTime) {
    int totalCleanedUpMetadataCount = 0;
    try (Stream<ExecutionRetentionMetadata> stream =
             retentionMetadataService.getExecutionRetentionMetadataWithExpiredTtl(
                 accountIdentifier, retentionPeriodInMonths)) {
      Iterator<ExecutionRetentionMetadata> iterator = stream.iterator();
      totalCleanedUpMetadataCount +=
          cleanUpObjectStoreAndMetadata(cleanupJobStartTs, syncJobMaxRunTime, iterator, accountIdentifier);
    } catch (Exception ex) {
      log.error(String.format("Failed while cleaning up object store for accountID: %s, with retention period: %d",
                    accountIdentifier, retentionPeriodInMonths),
          ex);
    }
    return totalCleanedUpMetadataCount;
  }

  @Override
  public ExecutionRetentionCleanupResult deleteExpiredTTLExecutionsWithResult(
      String accountIdentifier, int retentionPeriodInMonths, Instant cleanupJobStartTs, Duration syncJobMaxRunTime) {
    List<String> cleanedPlanExecutionIds = new ArrayList<>();
    int totalCleanedUpMetadataCount = 0;
    try (Stream<ExecutionRetentionMetadata> stream =
             retentionMetadataService.getExecutionRetentionMetadataWithExpiredTtl(
                 accountIdentifier, retentionPeriodInMonths)) {
      Iterator<ExecutionRetentionMetadata> iterator = stream.iterator();
      Map<String, List<String>> metadataUuidsToObjectPathsMap = new HashMap<>();
      Map<String, String> metadataUuidToPlanExecutionIdMap = new HashMap<>();

      while (iterator.hasNext()) {
        ExecutionRetentionMetadata executionRetentionMetadata = iterator.next();
        metadataUuidsToObjectPathsMap.putAll(updateMetadataUuidsToObjectPaths(executionRetentionMetadata));
        metadataUuidToPlanExecutionIdMap.put(
            executionRetentionMetadata.getUuid(), executionRetentionMetadata.getPlanExecutionId());

        if (metadataUuidsToObjectPathsMap.size() == DELETE_BATCH_SIZE) {
          Set<String> deletedUuids =
              cleanUpObjectStoreAndMetadataInternalWithResult(metadataUuidsToObjectPathsMap, accountIdentifier);
          totalCleanedUpMetadataCount += deletedUuids.size();

          // Collect planExecutionIds for deleted metadata
          for (String uuid : deletedUuids) {
            String planExecutionId = metadataUuidToPlanExecutionIdMap.get(uuid);
            if (planExecutionId != null) {
              cleanedPlanExecutionIds.add(planExecutionId);
            }
          }

          metadataUuidsToObjectPathsMap.clear();
          metadataUuidToPlanExecutionIdMap.clear();
        }
        if (isCleanupJobTimeMaxLimitExceeded(cleanupJobStartTs, syncJobMaxRunTime) || getMaintenanceFlag()) {
          log.warn("Interrupting the flow as system is in maintenance or clean up job has exceeded max runtime");
          break;
        }
      }

      if (!metadataUuidsToObjectPathsMap.isEmpty()) {
        Set<String> deletedUuids =
            cleanUpObjectStoreAndMetadataInternalWithResult(metadataUuidsToObjectPathsMap, accountIdentifier);
        totalCleanedUpMetadataCount += deletedUuids.size();

        // Collect planExecutionIds for deleted metadata
        for (String uuid : deletedUuids) {
          String planExecutionId = metadataUuidToPlanExecutionIdMap.get(uuid);
          if (planExecutionId != null) {
            cleanedPlanExecutionIds.add(planExecutionId);
          }
        }
      }
    } catch (Exception ex) {
      log.error(String.format("Failed while cleaning up object store for accountID: %s, with retention period: %d",
                    accountIdentifier, retentionPeriodInMonths),
          ex);
    }
    return ExecutionRetentionCleanupResult.builder()
        .cleanedCount(totalCleanedUpMetadataCount)
        .cleanedPlanExecutionIds(cleanedPlanExecutionIds)
        .build();
  }

  private Set<String> cleanUpObjectStoreAndMetadataInternalWithResult(
      Map<String, List<String>> metadataUuidsToObjectPathsMap, String accountIdentifier) {
    List<String> objectPaths = new ArrayList<>();
    Map<String, String> objectPathToMetadataUuidMap = new HashMap<>();

    // Populating object paths to be deleted and map of the objectPath to metadataUuid map as well
    for (Map.Entry<String, List<String>> entry : metadataUuidsToObjectPathsMap.entrySet()) {
      for (String objectPath : entry.getValue()) {
        objectPaths.add(objectPath);
        objectPathToMetadataUuidMap.put(objectPath, entry.getKey());
      }
    }

    // Deleting objects from the object store corresponding to objectPaths
    Map<String, Boolean> objectPathToDeletionResultMap = objectStoreClient.deleteObjectsByPaths(objectPaths);

    // It maintains the map of metadata uuids to object paths which did not get deleted
    Map<String, List<String>> nonDeletableUuidsToObjectPathsMap = new HashMap<>();

    for (Map.Entry<String, Boolean> entry : objectPathToDeletionResultMap.entrySet()) {
      if (Boolean.FALSE.equals(entry.getValue())) {
        String failedDeletionObjectPath = entry.getKey();
        String failedDeletionObjectPathMetadataUuid = objectPathToMetadataUuidMap.get(entry.getKey());
        nonDeletableUuidsToObjectPathsMap.computeIfAbsent(failedDeletionObjectPathMetadataUuid, v -> new ArrayList<>())
            .add(failedDeletionObjectPath);
      }
    }

    for (Map.Entry<String, List<String>> entry : nonDeletableUuidsToObjectPathsMap.entrySet()) {
      retentionMetadataService.updateRetentionFileDataList(entry.getKey(), entry.getValue());
    }

    Set<String> toBeDeletedMetadataUuids = new HashSet<>();
    for (String metadataUuid : metadataUuidsToObjectPathsMap.keySet()) {
      if (!nonDeletableUuidsToObjectPathsMap.containsKey(metadataUuid)) {
        toBeDeletedMetadataUuids.add(metadataUuid);
      }
    }
    retentionMetadataService.deleteAllExecutionRetentionMetadataByUuids(toBeDeletedMetadataUuids);
    if (accountIdentifier != null) {
      log.info("Cleaned up {} ExecutionRetentionMetadata for account: {}", toBeDeletedMetadataUuids.size(),
          accountIdentifier);
    } else {
      log.info("Cleaned up {} ExecutionRetentionMetadata", toBeDeletedMetadataUuids.size());
    }
    return toBeDeletedMetadataUuids;
  }

  private boolean hasMongoTTLExpired(Long endTs, ExecutionRetentionObjectStoreCollection collection) {
    Instant epochInstant = Instant.ofEpochMilli(endTs);
    Instant now = Instant.now();
    Instant limit = epochInstant.plus(Duration.of(getMongoTTL(collection), ChronoUnit.DAYS));
    return now.isAfter(limit);
  }

  private int getMongoTTL(ExecutionRetentionObjectStoreCollection collection) {
    switch (collection) {
      case EXECUTION_GRAPH, EXECUTION_SUB_GRAPH -> {
        return dataRetentionConfig.getMongoTTLDays().getExecutionGraph();
      }
      case EXECUTION_METADATA -> {
        return dataRetentionConfig.getMongoTTLDays().getExecutionMetadata();
      }
      default -> {
        return dataRetentionConfig.getMongoTTLDays().getDefaultTTL();
      }
    }
  }

  @Override
  public int getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection collection) {
    switch (collection) {
      case EXECUTION_GRAPH -> {
        return dataRetentionConfig.getMongoTTLDays().getExecutionGraph();
      }
      case EXECUTION_METADATA -> {
        return dataRetentionConfig.getMongoTTLDays().getExecutionMetadata();
      }
      default -> {
        throw new InvalidRequestException(String.format("[DATA_RETENTION]: Overriding ttl for collection: %s is not supported", collection));
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return dataRetentionConfig.isEnabled();
  }

  @Override
  public ExecutionRetentionMetadata getRetentionMetadata(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    if (isDataRetentionDisabled(accountIdentifier)) {
      return null;
    }
    return retentionMetadataService.get(uuid, collection);
  }

  @Override
  public ExecutionRetentionMetadata getRetentionMetadataIfExpired(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    if (isDataRetentionDisabled(accountIdentifier)) {
      return null;
    }
    ExecutionRetentionMetadata metadata = getRetentionMetadata(accountIdentifier, uuid, collection);
    if (metadata == null || !hasMongoTTLExpired(metadata.getEndTs(), collection)) {
      return null;
    }
    return metadata;
  }

  @Override
  public RetentionFileData getRetentionFileData(ExecutionRetentionMetadata retentionMetadata, String uuid,
      ExecutionRetentionObjectStoreCollection collection) {
    if (retentionMetadata == null) {
      return null;
    }
    return retentionMetadataService.filterRetentionFileData(retentionMetadata, uuid, collection);
  }

  @Override
  public Object readObjectFromStore(RetentionFileData fileData, ExecutionRetentionObjectStoreCollection collection,
      Class<?> classType) {
    if (fileData == null || objectStoreClient == null) {
      return null;
    }
    StorageObject object = objectStoreClient.getObject(fileData.getFilePath());
    if (object == null) {
      log.error(String.format(
          "[DATA_RETENTION]: The requested record for uuid: %s, doesn't exist in object store for collection: %s",
          fileData.getUuid(), collection.getCollectionName()));
      return null;
    }
    return ExecutionRetentionUtils.convertBytesToObject(
        collection, object.getContent(), fileData.getMetadata(), classType);
  }

  private boolean isCleanupJobTimeMaxLimitExceeded(Instant jobStartTs, Duration syncJobMaxRunTime) {
    if (jobStartTs == null || syncJobMaxRunTime == null) {
      return false;
    }
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(syncJobMaxRunTime) > 0;
  }

  private boolean isDataRetentionDisabled(String accountIdentifier) {
    return !dataRetentionConfig.isEnabled() || !pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_ENABLE_DATA_RETENTION);
  }
}
