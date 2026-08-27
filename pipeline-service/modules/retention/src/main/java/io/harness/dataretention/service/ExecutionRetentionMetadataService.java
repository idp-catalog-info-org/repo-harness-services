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
import io.harness.dataretention.entity.beans.ExecutionRetentionMetadataUpdateDTO;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Criteria;

/*
 * This service is used to do MongoDB operations for the ExecutionRetentionMetadata
 * Like save/update records in DB, it's also used to fetch the metadata from DB by filtering by collection name and uuid
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecutionRetentionMetadataService {
  /**
   * Saves the execution retention metadata in harness pms db
   * @param executionRetentionMetadata metadata to save
   * @return ExecutionRetentionMetadata saved metadata
   */
  ExecutionRetentionMetadata save(ExecutionRetentionMetadata executionRetentionMetadata);

  /**
   * Upsert the execution retention metadata in harness pms db, creates record if not found
   * @param planExecutionId the execution id for which metadata is to be updated
   * @param updateDTO the updates to be performed
   * @return ExecutionRetentionMetadata updated metadata
   */
  ExecutionRetentionMetadata upsert(String planExecutionId, ExecutionRetentionMetadataUpdateDTO updateDTO);

  /**
   * Updates the execution retention metadata in harness pms db
   * @param uuid the uuid for which metadata is to be updated
   * @param updateDTO the updates to be performed
   * @return ExecutionRetentionMetadata updated metadata
   */
  ExecutionRetentionMetadata update(String uuid, ExecutionRetentionMetadataUpdateDTO updateDTO);

  /**
   * Finds the execution retention metadata in harness pms db
   * Uses - retentionFile_uuid_collectionName_idx index
   * @param uuid retentionFile UUID
   * @param collection collection of the file to filter by
   * @return ExecutionRetentionMetadata fetched metadata
   */
  ExecutionRetentionMetadata get(String uuid, ExecutionRetentionObjectStoreCollection collection);

  /**
   * Finds the execution retention metadata in harness pms db
   * Uses - retentionFile_uuid_collectionName_idx index
   * @param uuids retentionFile UUIDs
   * @param collection collection of the file to filter by
   * @return List of fetched  ExecutionRetentionMetadata
   */
  List<ExecutionRetentionMetadata> getAll(List<String> uuids, ExecutionRetentionObjectStoreCollection collection);

  /**
   * Stream ExecutionRetentionMetadata entities from secondary database based on criteria
   * @return A stream of ExecutionRetentionMetadata entities
   */
  Stream<ExecutionRetentionMetadata> fetchExecutionMetadataBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime);

  /**
   * Same as {@link #fetchExecutionMetadataBetweenEndTsFromSecondary(String, Long, Long)} with optional org scope.
   * When orgIdentifier is null, no org filter is applied.
   */
  Stream<ExecutionRetentionMetadata> fetchExecutionMetadataBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, String orgIdentifier);

  /**
   * Filters the given retention metadata by uuid and collection and returns its file data
   * @return RetentionFileData file data of the given uuid/collection
   */
  RetentionFileData filterRetentionFileData(
      ExecutionRetentionMetadata retentionMetadata, String uuid, ExecutionRetentionObjectStoreCollection collection);

  /**
   * Fetches all ExecutionRetentionMetadata for an accountIdentifier with expired TTL, It checks for less than the given
   * time ( lt not lte )
   * @param accountIdentifier
   * @param retentionPeriodInMonths
   * @return
   */
  Stream<ExecutionRetentionMetadata> getExecutionRetentionMetadataWithExpiredTtl(
      String accountIdentifier, int retentionPeriodInMonths);

  Stream<ExecutionRetentionMetadata> getExecutionRetentionMetadataForExecutions(Set<String> planExecutionIds);

  /**
   * Fetches all accountIdentifiers which don't have data retention setting enabled
   * @param accountIdsWithRetentionSetting
   * @return
   */
  List<String> getAllUniqueAccountIdsWithoutRetentionSetting(Set<String> accountIdsWithRetentionSetting);

  /**
   * remove all the ExecutionRetentionMetadata for given uuids
   * @param executionRetentionMetadataUuids
   * @return
   */
  DeleteResult deleteAllExecutionRetentionMetadataByUuids(Set<String> executionRetentionMetadataUuids);

  /**
   * update retentionFileData list of ExecutionRetentionMetadata
   * @param executionRetentionMetadataUuid
   * @param filesPathList
   * @return
   */
  ExecutionRetentionMetadata updateRetentionFileDataList(
      String executionRetentionMetadataUuid, List<String> filesPathList);

  List<ExecutionRetentionMetadata> getBatchOfExecutionRetentionMetadataWithoutParentUniqueId(int batchSize);

  List<ExecutionRetentionMetadata> fetchAllFromSecondary(Criteria criteria, Set<String> fieldsToInclude);
}
