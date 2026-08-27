/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionCleanupResult;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * This service is used to do fetch an object from object store, which internally fetches the metadata record
 * And then if found fetches the record from object store and converts the json file to a java object based on
 * the collection passed to it
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecutionRetentionService {
  /**
   * Fetches records from object store, for which it first fetches the retention metadata
   * Uses the object store path present in the same to fetch it from object store
   * Uses - retentionFile_uuid_collectionName_idx index
   * @param accountIdentifier accountID to check if FF is enabled
   * @param uuid UUID of the record
   * @param collection collection to fetch
   * @param classType type of class in which to cast the record
   * @return Object the java object
   */
  Object readExpiredRecordFromObjectStore(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection, Class<?> classType);

  /**
   * Returns ExecutionRetentionMetadata for a given uuid and collection. Returns null if not found.
   */
  ExecutionRetentionMetadata getRetentionMetadata(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection);

  /**
   * Returns ExecutionRetentionMetadata only if data retention is enabled, the FF is on for the account,
   * and the record's Mongo TTL has expired for the given collection. Otherwise returns null.
   */
  ExecutionRetentionMetadata getRetentionMetadataIfExpired(
      String accountIdentifier, String uuid, ExecutionRetentionObjectStoreCollection collection);

  /**
   * From a metadata record, returns the RetentionFileData corresponding to the specific uuid and collection.
   * Returns null if none is found.
   */
  io.harness.dataretention.entity.beans.RetentionFileData getRetentionFileData(
      ExecutionRetentionMetadata retentionMetadata, String uuid, ExecutionRetentionObjectStoreCollection collection);

  /**
   * Reads a single object from the object store using the provided file data and converts it to the target class.
   * Returns null if the object is missing in the object store.
   */
  Object readObjectFromStore(io.harness.dataretention.entity.beans.RetentionFileData fileData,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType);

  /**
   * Fetches records from object store corresponding to provided uuids and returns the map of uuids to objects
   * @param accountIdentifier
   * @param uuids
   * @param collection
   * @return
   */
  Map<String, Object> readExpiredRecordsFromObjectStore(String accountIdentifier, List<String> uuids,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType);

  Map<String, Object> readRecordsFromObjectStore(List<ExecutionRetentionMetadata> retentionMetadataList,
      ExecutionRetentionObjectStoreCollection collection, Class<?> classType);

  void deleteAllPlanExecutionsData(Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete);

  int deleteExpiredTTLExecutions(
      String accountIdentifier, int retentionPeriodInMonths, Instant cleanupJobStartTs, Duration syncJobMaxRunTime);

  /**
   * Deletes expired TTL executions and returns detailed result including cleaned planExecutionIds.
   * This is used by the cleanup job to publish events for downstream services.
   *
   * @param accountIdentifier account identifier
   * @param retentionPeriodInMonths retention period in months
   * @param cleanupJobStartTs timestamp when cleanup job started
   * @param syncJobMaxRunTime maximum runtime for the sync job
   * @return ExecutionRetentionCleanupResult containing count and list of cleaned planExecutionIds
   */
  ExecutionRetentionCleanupResult deleteExpiredTTLExecutionsWithResult(
      String accountIdentifier, int retentionPeriodInMonths, Instant cleanupJobStartTs, Duration syncJobMaxRunTime);

  int getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection collection);

  boolean isEnabled();
}
