/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_RUNNING_EXECUTIONS_INDEX;

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

import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
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
public class PmsElasticSearchCreateRunningExecutionsIndex implements NGMigration {
  @Nullable @Inject private ElasticSearchClient elasticSearchClient;

  @Override
  public void migrate() {
    if (elasticSearchClient != null) {
      try {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                                                    .index(PMS_RUNNING_EXECUTIONS_INDEX)
                                                    .mappings(ElasticSearchUtils.getTypeMappingFromFile(
                                                        "elasticsearch/pms-execution-entity-mappings-v1.json", true))
                                                    .build();
        CreateIndexResponse createIndexResponse = elasticSearchClient.createIndex(createIndexRequest);
        if (!createIndexResponse.acknowledged()) {
          throw new InternalServerErrorException("Unable to create the index pms-running-executions in elasticsearch");
        }
      } catch (Exception ex) {
        throw new InternalServerErrorException(
            "Unable to create the index pms-running-executions in elasticsearch", ex);
      }
    } else {
      throw new ElasticSearchNotAvailableException(
          String.format("[Migration]: Migration %s failed - ELASTICSEARCHDB NOT AVAILABLE", getClass()));
    }
  }
}
