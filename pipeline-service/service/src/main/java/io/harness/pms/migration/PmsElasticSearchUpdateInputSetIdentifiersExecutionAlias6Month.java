/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.exception.InternalServerErrorException;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.ElasticSearchNotAvailableException;
import io.harness.search.service.PipelineSearchService;

import com.google.inject.Inject;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PmsElasticSearchUpdateInputSetIdentifiersExecutionAlias6Month implements NGMigration {
  @Nullable @Inject private ElasticSearchClient elasticsearchClient;
  @Inject private PipelineSearchService pipelineSearchService;
  public final String FIELD_NAME = "inputSetIdentifiers";

  @Override
  public void migrate() {
    if (elasticsearchClient != null) {
      try {
        pipelineSearchService.updateIndexAlias(null, FIELD_NAME, DEFAULT_RETENTION_6_MONTHS);
      } catch (Exception ex) {
        throw new InternalServerErrorException(
            String.format("[ELASTIC_SEARCH]: Could not update the index alias %s in elasticsearch",
                DEFAULT_RETENTION_6_MONTHS.getIndexName()),
            ex);
      }
    } else {
      throw new ElasticSearchNotAvailableException(
          String.format("[Migration]: Migration %s failed - ELASTICSEARCHDB NOT AVAILABLE", getClass()));
    }
  }
}
