/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.ExecutionRetentionMetadata.ExecutionRetentionMetadataKeys;
import io.harness.dataretention.entity.beans.ExecutionRetentionMetadataUpdateDTO;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.entity.beans.RetentionFileData.RetentionFileDataKeys;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.dataretention.ExecutionRetentionMetadataRepository;
import io.harness.utils.ReconciliationOrgScopeCriteriaHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionRetentionMetadataServiceImpl implements ExecutionRetentionMetadataService {
  @Inject private ExecutionRetentionMetadataRepository retentionMetadataRepository;
  @Inject private ReconciliationOrgScopeCriteriaHelper reconciliationOrgScopeCriteriaHelper;
  private static final int MAX_BATCH_SIZE = 1000;

  @Override
  public ExecutionRetentionMetadata save(ExecutionRetentionMetadata executionRetentionMetadata) {
    return retentionMetadataRepository.save(executionRetentionMetadata);
  }

  @Override
  public ExecutionRetentionMetadata upsert(String planExecutionId, ExecutionRetentionMetadataUpdateDTO updateDTO) {
    return retentionMetadataRepository.upsert(planExecutionId, getUpdateMapFromUpdateDTO(updateDTO));
  }

  @Override
  public ExecutionRetentionMetadata update(String uuid, ExecutionRetentionMetadataUpdateDTO updateDTO) {
    return retentionMetadataRepository.update(uuid, getUpdateMapFromUpdateDTO(updateDTO));
  }

  @Override
  public ExecutionRetentionMetadata get(String uuid, ExecutionRetentionObjectStoreCollection collection) {
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.retentionFileUUID)
                            .is(uuid)
                            .and(ExecutionRetentionMetadataKeys.retentionFileCollection)
                            .is(collection);
    return retentionMetadataRepository.fetchFromSecondary(criteria);
  }

  @Override
  public List<ExecutionRetentionMetadata> getAll(
      List<String> uuids, ExecutionRetentionObjectStoreCollection collection) {
    validateBatchSize(uuids);
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.retentionFileUUID)
                            .in(uuids)
                            .and(ExecutionRetentionMetadataKeys.retentionFileCollection)
                            .is(collection);
    return retentionMetadataRepository.fetchAllFromSecondary(criteria);
  }

  private Criteria buildCriteria(String accountId, Long startTime, Long endTime, String orgIdentifier) {
    if (startTime == null) {
      throw new InvalidRequestException("ExecutionRetentionMetadata start time filter cannot be null");
    }
    if (accountId == null) {
      throw new InvalidRequestException("ExecutionRetentionMetadata account id filter cannot be null");
    }
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.accountId).is(accountId);
    if (endTime == null) {
      criteria = criteria.and(ExecutionRetentionMetadataKeys.endTs).gte(startTime);
    } else {
      criteria = criteria.and(ExecutionRetentionMetadataKeys.endTs).gte(startTime).lte(endTime);
    }
    reconciliationOrgScopeCriteriaHelper.applyOrgScopeFilter(criteria, accountId, orgIdentifier,
        ExecutionRetentionMetadataKeys.orgIdentifier, ExecutionRetentionMetadataKeys.parentUniqueId);
    return criteria;
  }

  @Override
  public Stream<ExecutionRetentionMetadata> fetchExecutionMetadataBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime) {
    return fetchExecutionMetadataBetweenEndTsFromSecondary(accountId, startTime, endTime, null);
  }

  @Override
  public Stream<ExecutionRetentionMetadata> fetchExecutionMetadataBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, String orgIdentifier) {
    Criteria criteria = buildCriteria(accountId, startTime, endTime, orgIdentifier);
    Query query = query(criteria);
    query.with(Sort.by(Sort.Direction.ASC, ExecutionRetentionMetadataKeys.endTs));
    return retentionMetadataRepository.streamFromSecondary(query);
  }

  @Override
  public RetentionFileData filterRetentionFileData(
      ExecutionRetentionMetadata retentionMetadata, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    if (isNull(retentionMetadata.getRetentionFileData())) {
      log.error(String.format(
          "[DATA_RETENTION]: The requested retention metadata for uuid: %s, doesn't contain any file", uuid));
      throw new InvalidRequestException(String.format(
          "[DATA_RETENTION]: The requested retention metadata for uuid: %s, doesn't contain any file", uuid));
    }
    Optional<RetentionFileData> retentionFileData =
        retentionMetadata.getRetentionFileData()
            .stream()
            .filter(file -> uuid.equals(file.getUuid()) & collection.equals(file.getCollection()))
            .findFirst();
    if (retentionFileData.isEmpty()) {
      log.error(String.format(
          "[DATA_RETENTION]: The requested retention metadata for uuid: %s, doesn't exist for collection: %s", uuid,
          collection.getCollectionName()));
      throw new InvalidRequestException(String.format(
          "[DATA_RETENTION]: The requested retention metadata for uuid: %s, doesn't exist for collection: %s", uuid,
          collection.getCollectionName()));
    }
    return retentionFileData.get();
  }

  @Override
  public Stream<ExecutionRetentionMetadata> getExecutionRetentionMetadataWithExpiredTtl(
      String accountIdentifier, int retentionPeriodInMonths) {
    long ttl = DateTime.now().minusMonths(retentionPeriodInMonths).getMillis();
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.accountId)
                            .is(accountIdentifier)
                            .and(ExecutionRetentionMetadataKeys.endTs)
                            .lt(ttl);
    return retentionMetadataRepository.streamFromSecondary(criteria);
  }

  @Override
  public Stream<ExecutionRetentionMetadata> getExecutionRetentionMetadataForExecutions(Set<String> planExecutionIds) {
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.planExecutionId).in(planExecutionIds);
    return retentionMetadataRepository.streamFromSecondary(criteria);
  }

  @Override
  public List<String> getAllUniqueAccountIdsWithoutRetentionSetting(Set<String> accountIdentifiers) {
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.accountId).nin(accountIdentifiers);
    return retentionMetadataRepository.getAllUniqueAccountIdsFromSecondary(criteria);
  }

  @Override
  public DeleteResult deleteAllExecutionRetentionMetadataByUuids(Set<String> executionRetentionMetadataUuids) {
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.uuid).in(executionRetentionMetadataUuids);
    return retentionMetadataRepository.delete(criteria);
  }

  @Override
  public ExecutionRetentionMetadata updateRetentionFileDataList(
      String executionRetentionMetadataUuid, List<String> filesPathList) {
    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.uuid).is(executionRetentionMetadataUuid);
    Update update = new Update().pull(ExecutionRetentionMetadataKeys.retentionFileData,
        query(Criteria.where(RetentionFileDataKeys.filePath).nin(filesPathList)));
    return retentionMetadataRepository.update(criteria, update);
  }

  @Override
  public List<ExecutionRetentionMetadata> getBatchOfExecutionRetentionMetadataWithoutParentUniqueId(int batchSize) {
    Criteria criteria = new Criteria().where(ExecutionRetentionMetadataKeys.parentUniqueId).is(null);

    return retentionMetadataRepository.fetchAllFromSecondary(query(criteria).limit(batchSize));
  }

  @Override
  public List<ExecutionRetentionMetadata> fetchAllFromSecondary(Criteria criteria, Set<String> fieldsToInclude) {
    Query query = query(criteria);
    if (isNotEmpty(fieldsToInclude)) {
      for (String field : fieldsToInclude) {
        if (EmptyPredicate.isNotEmpty(field)) {
          query.fields().include(field);
        }
      }
    }
    return retentionMetadataRepository.fetchAllFromSecondary(query);
  }

  private Update getUpdateMapFromUpdateDTO(ExecutionRetentionMetadataUpdateDTO updateDTO) {
    Update updateOps = new Update();
    if (updateDTO.getAccountId() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.accountId, updateDTO.getAccountId());
    }
    if (updateDTO.getBucketName() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.bucketName, updateDTO.getBucketName());
    }
    if (updateDTO.getPlanExecutionId() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.planExecutionId, updateDTO.getPlanExecutionId());
    }
    if (updateDTO.getEndTs() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.endTs, updateDTO.getEndTs());
    }
    if (updateDTO.getRetentionFileData() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.retentionFileData, updateDTO.getRetentionFileData());
    }
    if (updateDTO.getParentUniqueId() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.parentUniqueId, updateDTO.getParentUniqueId());
    }
    if (updateDTO.getPipelineIdentifier() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.pipelineIdentifier, updateDTO.getPipelineIdentifier());
    }
    if (updateDTO.getOrgIdentifier() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.orgIdentifier, updateDTO.getOrgIdentifier());
    }
    if (updateDTO.getProjectIdentifier() != null) {
      updateOps.set(ExecutionRetentionMetadataKeys.projectIdentifier, updateDTO.getProjectIdentifier());
    }

    return updateOps;
  }

  private void validateBatchSize(List<String> uuids) {
    if (isEmpty(uuids) || (uuids.size() > MAX_BATCH_SIZE)) {
      throw new InvalidRequestException(
          String.format("[DATA_RETENTION]: batch size should not be empty or greater than %d", MAX_BATCH_SIZE));
    }
  }
}
