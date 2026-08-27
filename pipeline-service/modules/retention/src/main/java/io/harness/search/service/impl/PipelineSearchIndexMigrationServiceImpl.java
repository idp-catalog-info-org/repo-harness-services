/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.repositories.search.PipelineSearchIndexMigrationEntityRepository;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity.PipelineSearchIndexMigrationEntityKeys;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;
import io.harness.search.mappers.PipelineSearchIndexMigrationMapper;
import io.harness.search.service.PipelineSearchIndexMigrationService;
import io.harness.utils.RetentionUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineSearchIndexMigrationServiceImpl implements PipelineSearchIndexMigrationService {
  @Inject private PipelineSearchIndexMigrationEntityRepository migrationEntityRepository;

  @Override
  public PipelineSearchIndexMigrationEntity save(PipelineSearchIndexMigrationEntity indexMigrationEntity) {
    return migrationEntityRepository.save(indexMigrationEntity);
  }

  @Override
  public PipelineSearchIndexMigrationEntity update(String uuid, Update updateOps) {
    return migrationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public PipelineSearchIndexMigration findByAccountIdentifier(String accountIdentifier) {
    try {
      if (isEmpty(accountIdentifier)) {
        throw new InvalidRequestException("Account id cannot be empty");
      }
      return PipelineSearchIndexMigrationMapper.toDTO(
          migrationEntityRepository.findByAccountIdentifier(accountIdentifier));
    } catch (Exception ex) {
      log.error(
          "Exception occurred while fetching search index migration entity for account id: {}", accountIdentifier, ex);
      throw ex;
    }
  }

  @Override
  public PipelineSearchIndexMigration updateRetentionPeriod(
      String accountIdentifier, DataRetentionPeriod dataRetentionPeriod) {
    PipelineSearchIndexMigration indexMigrationDTO = findByAccountIdentifier(accountIdentifier);
    PipelineSearchIndexRetentionPeriods oldRetentionPeriod = DEFAULT_RETENTION_6_MONTHS;
    PipelineSearchIndexRetentionPeriods newRetentionPeriod =
        RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(dataRetentionPeriod);
    if (indexMigrationDTO != null) {
      if (PipelineSearchMigrationStatus.IN_PROGRESS.equals(indexMigrationDTO.getStatus())
          || PipelineSearchMigrationStatus.NOT_STARTED.equals(indexMigrationDTO.getStatus())) {
        throw new InvalidRequestException(
            String.format("Index migration is already under progress for this account: %s, with status: %s",
                accountIdentifier, indexMigrationDTO.getStatus()));
      }
      if (PipelineSearchMigrationStatus.COMPLETE.equals(indexMigrationDTO.getStatus())) {
        oldRetentionPeriod = indexMigrationDTO.getNewIndexRetentionPeriod();
      } else {
        oldRetentionPeriod = indexMigrationDTO.getOldIndexRetentionPeriod();
      }
      if (oldRetentionPeriod == newRetentionPeriod) {
        throw new InvalidRequestException(
            String.format("Currently the account: %s, is already on the requested retention period: %s",
                accountIdentifier, oldRetentionPeriod));
      }
      Update update = new Update();
      update.set(PipelineSearchIndexMigrationEntityKeys.status, PipelineSearchMigrationStatus.NOT_STARTED);
      update.set(PipelineSearchIndexMigrationEntityKeys.oldIndexRetentionPeriod, oldRetentionPeriod);
      update.set(PipelineSearchIndexMigrationEntityKeys.newIndexRetentionPeriod, newRetentionPeriod);
      update.set(PipelineSearchIndexMigrationEntityKeys.nextIteration, System.currentTimeMillis());
      update.unset(PipelineSearchIndexMigrationEntityKeys.elasticTaskID);
      update.unset(PipelineSearchIndexMigrationEntityKeys.elasticBufferSyncTaskID);
      update.unset(PipelineSearchIndexMigrationEntityKeys.migrationStartTime);
      update.unset(PipelineSearchIndexMigrationEntityKeys.migrationEndTime);
      return PipelineSearchIndexMigrationMapper.toDTO(update(indexMigrationDTO.getUuid(), update));
    }
    return PipelineSearchIndexMigrationMapper.toDTO(save(PipelineSearchIndexMigrationEntity.builder()
                                                             .accountIdentifier(accountIdentifier)
                                                             .status(PipelineSearchMigrationStatus.NOT_STARTED)
                                                             .oldIndexRetentionPeriod(oldRetentionPeriod)
                                                             .newIndexRetentionPeriod(newRetentionPeriod)
                                                             .nextIteration(System.currentTimeMillis())
                                                             .build()));
  }
}
