/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH_POLICY_FILE_PATH;
import static io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods.DEFAULT_RETENTION_6_MONTHS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.utils.ElasticSearchUtils;
import io.harness.exception.InternalServerErrorException;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.ElasticSearchNotAvailableException;
import io.harness.search.service.PipelineSearchService;

import co.elastic.clients.elasticsearch.ilm.IlmPolicy;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleResponse;
import com.google.inject.Inject;
import java.io.StringReader;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PmsElasticSearchCreateExecutionAlias6Month implements NGMigration {
  @Nullable @Inject private ElasticSearchClient elasticsearchClient;
  @Inject private PipelineSearchService pipelineSearchService;

  @Override
  public void migrate() {
    if (elasticsearchClient != null) {
      try {
        IlmPolicy ilmPolicy = new IlmPolicy.Builder()
                                  .withJson(new StringReader(
                                      ElasticSearchUtils.getJsonFromFile(PMS_EXECUTION_ALIAS_6_MONTH_POLICY_FILE_PATH)))
                                  .build();
        PutLifecycleRequest putLifecycleRequest = new PutLifecycleRequest.Builder()
                                                      .name(DEFAULT_RETENTION_6_MONTHS.getPolicyName())
                                                      .policy(ilmPolicy)
                                                      .build();
        PutLifecycleResponse putLifecycleResponse = elasticsearchClient.putLifecycle(putLifecycleRequest);
        if (!putLifecycleResponse.acknowledged()) {
          throw new InternalServerErrorException(
              String.format("[ELASTIC_SEARCH]: Could not create the ILM policy %s in elasticsearch",
                  DEFAULT_RETENTION_6_MONTHS.getPolicyName()));
        }
        pipelineSearchService.createIndexAlias(null, DEFAULT_RETENTION_6_MONTHS);
      } catch (Exception ex) {
        throw new InternalServerErrorException(
            String.format("[ELASTIC_SEARCH]: Could not create the index alias %s in elasticsearch",
                DEFAULT_RETENTION_6_MONTHS.getIndexName()),
            ex);
      }
    } else {
      throw new ElasticSearchNotAvailableException(
          String.format("[Migration]: Migration %s failed - ELASTICSEARCHDB NOT AVAILABLE", getClass()));
    }
  }
}
