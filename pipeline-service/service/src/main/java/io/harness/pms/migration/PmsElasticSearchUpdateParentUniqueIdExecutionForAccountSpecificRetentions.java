/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.exception.InternalServerErrorException;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.ElasticSearchNotAvailableException;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.service.PipelineSearchService;
import io.harness.utils.RetentionUtils;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PmsElasticSearchUpdateParentUniqueIdExecutionForAccountSpecificRetentions implements NGMigration {
  @Nullable @Inject private ElasticSearchClient elasticsearchClient;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject private PipelineRetentionService pipelineRetentionService;
  public final String FIELD_NAME = "parentUniqueId";

  @Override
  public void migrate() {
    String accountIdentifier = null;
    String retentionPeriodIndexName = null;
    if (elasticsearchClient != null) {
      try (Stream<DataRetentionEntity> stream = pipelineRetentionService.getAllWithSearchSettingsFromSecondary()) {
        Iterator<DataRetentionEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          DataRetentionEntity dataRetentionEntity = iterator.next();
          accountIdentifier = dataRetentionEntity.getAccountIdentifier();
          if (dataRetentionEntity.getDataRetentionSettings().getDataRetentionPeriod() != null) {
            PipelineSearchIndexRetentionPeriods pipelineSearchIndexRetentionPeriods =
                RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(
                    dataRetentionEntity.getDataRetentionSettings().getDataRetentionPeriod());
            retentionPeriodIndexName = pipelineSearchIndexRetentionPeriods.name();
            pipelineSearchService.updateIndexAlias(accountIdentifier, FIELD_NAME, pipelineSearchIndexRetentionPeriods);
          }
        }
      } catch (Exception ex) {
        throw new InternalServerErrorException(
            String.format(
                "[ELASTIC_SEARCH]: Could not update the index alias for account: %s specific retention %s in elasticsearch",
                accountIdentifier, retentionPeriodIndexName),
            ex);
      }
    } else {
      throw new ElasticSearchNotAvailableException(
          String.format("[Migration]: Migration %s failed - ELASTICSEARCHDB NOT AVAILABLE", getClass()));
    }
  }
}
