/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.elastic;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_NOT_EXISTS;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.PL, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdParentIdToEntitiesElasticsearchTask implements Runnable {
  private final ElasticSearchClient elasticsearchClient;
  private final ScopeInfoClient scopeInfoClient;
  private final PersistentLocker persistentLocker;

  private final Duration syncJobMaxRunTime = Duration.ofMinutes(30);
  private static final String PARENT_UNIQUE_ID_KEY = "parentUniqueId";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";
  private static final String PIPELINE_ENTITIES_MIGRATION_LOG =
      "[PipelineAddUniqueIdAndParentUniqueIdToEntitiesElasticTask]:";
  private static final String LOCK_NAME_PREFIX = "ElasticPeriodicMigrationTaskLock";
  private static final int BATCH_SIZE = 500;
  private static final Long SLEEP_MS = Long.valueOf(
      Optional.ofNullable(System.getenv("ELASTIC_MIGRATION_SLEEP_MS")).map(Integer::parseInt).orElse(1000));

  @Inject
  public AddUniqueIdParentIdToEntitiesElasticsearchTask(
      ElasticSearchClient elasticsearchClient, ScopeInfoClient scopeInfoClient, PersistentLocker persistentLocker) {
    this.elasticsearchClient = elasticsearchClient;
    this.scopeInfoClient = scopeInfoClient;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void run() {
    log.info(format("%s starting...", PIPELINE_ENTITIES_MIGRATION_LOG));
    try {
      performParentUniqueIdMigration();
    } catch (IOException e) {
      log.error(format("%s Failed to perform migration for ElasticDB", PIPELINE_ENTITIES_MIGRATION_LOG), e);
    }
  }

  private void performParentUniqueIdMigration() throws IOException {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Elastic DB entity during parentUniqueId migration task",
            PIPELINE_ENTITIES_MIGRATION_LOG));
        return;
      }

      int orphanEntityCounter = 0;
      int migratedCounter = 0;
      int batchSizeCounter = 0;
      int toUpdateCounter = 0;
      int skippedCounter = 0;
      boolean hasMoreResults = true;
      final String LOCAL_MAP_DELIMITER = "|";
      final Map<String, String> scopeEntityUniqueIdMap = new HashMap<>();
      BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
      Instant jobStartTs = Instant.now();
      boolean hasError = false;
      while (hasMoreResults) {
        Query query = ElasticSearchQueryBuilder.buildFieldQuery(MUST_NOT_EXISTS, PARENT_UNIQUE_ID_KEY);
        SearchRequest searchRequest = new SearchRequest.Builder()
                                          .query(query)
                                          .index("pms*") // Should not be using the index pattern in this manor
                                          .source(source
                                              -> source.filter(filter
                                                  -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.accountId,
                                                      PipelineSearchExecutionSummaryDTOKeys.orgIdentifier,
                                                      PipelineSearchExecutionSummaryDTOKeys.projectIdentifier,
                                                      PipelineSearchExecutionSummaryDTOKeys.uuid)))
                                          .size(BATCH_SIZE)
                                          .build();

        SearchResponse<PipelineSearchExecutionSummaryDTO> searchResponse =
            elasticsearchClient.search(searchRequest, PipelineSearchExecutionSummaryDTO.class);
        if (searchResponse == null || searchResponse.hits() == null) {
          log.error(format("%s Failed to fetch search response for ElasticDB", PIPELINE_ENTITIES_MIGRATION_LOG));
          return;
        }

        List<Hit<PipelineSearchExecutionSummaryDTO>> hits = searchResponse.hits().hits();
        if (hits.isEmpty()) {
          hasMoreResults = false; // Stop when no more results
        } else {
          for (Hit<PipelineSearchExecutionSummaryDTO> hit : hits) {
            if (hit.source() != null) {
              String uuid = hit.source().getUuid();
              String accountId = hit.source().getAccountId();
              String orgId = hit.source().getOrgIdentifier();
              String projectId = hit.source().getProjectIdentifier();
              String mapKey = accountId + LOCAL_MAP_DELIMITER + orgId + LOCAL_MAP_DELIMITER + projectId;
              toUpdateCounter++;

              String scopeUniqueId = null;
              if (scopeEntityUniqueIdMap.containsKey(mapKey)) {
                scopeUniqueId = scopeEntityUniqueIdMap.get(mapKey);
              } else {
                if (isNotEmpty(orgId) || isNotEmpty(projectId)) {
                  ScopeInfo scopeInfo = null;
                  try {
                    scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountId, orgId, projectId));
                  } catch (InvalidRequestException exception) {
                    // nothing to handle here, will mark entity as orphan
                  }
                  if (scopeInfo == null) {
                    scopeUniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
                    orphanEntityCounter++;
                  } else {
                    scopeUniqueId = scopeInfo.getUniqueId();
                  }
                } else {
                  scopeUniqueId = accountId;
                }
                scopeEntityUniqueIdMap.put(mapKey, scopeUniqueId);
              }
              batchSizeCounter++;
              PipelineSearchExecutionSummaryDTO recordToUpdate =
                  PipelineSearchExecutionSummaryDTO.builder().parentUniqueId(scopeUniqueId).build();
              bulkRequest.operations(operation
                  -> operation.update(UpdateOperation.of(
                      u -> u.index(hit.index()).id(uuid).routing(hit.routing()).action(a -> a.doc(recordToUpdate)))));

              if (batchSizeCounter == BATCH_SIZE) {
                BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest.build());
                if (bulkResponse.errors()) {
                  hasError = true;
                  log.error(format(
                      "%s job failed to update some entities during parentUniqueId migration for PlanExecutionSummary, error: ",
                      PIPELINE_ENTITIES_MIGRATION_LOG));
                  handleBulkResponse(bulkResponse);
                  break;
                }
                Thread.sleep(SLEEP_MS);
                log.info(format("%s Sleep of %d duration completed.", PIPELINE_ENTITIES_MIGRATION_LOG, SLEEP_MS));
                migratedCounter += bulkResponse.items().size();
                batchSizeCounter = 0;
                bulkRequest = new BulkRequest.Builder();
              }
            }
          }
          if (hasError) {
            log.info(format(
                "%s job failed to update some entities, stopping the migration.", PIPELINE_ENTITIES_MIGRATION_LOG));
            break;
          }
          if (hasJobRunTimeExceededMaxRunTime(jobStartTs)) {
            log.info(format(
                "%s job exceeded max run time of %d", PIPELINE_ENTITIES_MIGRATION_LOG, syncJobMaxRunTime.toMinutes()));
            break;
          }
        }
      }
      if (!hasError && batchSizeCounter > 0) { // for the last remaining batch of entities
        BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest.build());
        if (bulkResponse.errors()) {
          log.error(format(
              "%s job failed to update last remaining entities during parentUniqueId migration for PlanExecutionSummary",
              PIPELINE_ENTITIES_MIGRATION_LOG));
          handleBulkResponse(bulkResponse);
        } else {
          migratedCounter += bulkResponse.items().size();
        }
      }

      if (toUpdateCounter == migratedCounter) {
        log.info(format(
            "%s job on entity PipelineExecutionSummaryEntity for parentUniqueId Succeeded for elastic DB entities. Documents to Update: [%d], Successful: [%d], Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, toUpdateCounter, migratedCounter, orphanEntityCounter, skippedCounter));
      } else {
        log.warn(format(
            "%s job failed on entity PipelineExecutionSummaryEntity for parentUniqueId for elastic DB entities. Documents to Update: [%d], Successful: [%d], Orphan: [%d], Skipped(Failed or Invalid Entities): [%d]",
            PIPELINE_ENTITIES_MIGRATION_LOG, toUpdateCounter, migratedCounter, orphanEntityCounter, skippedCounter));
      }
    } catch (Exception exception) {
      log.error(
          format("%s task failed to iterate over entities during parentUniqueId migration for PlanExecutionSummary",
              PIPELINE_ENTITIES_MIGRATION_LOG),
          exception);
    }
  }

  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs) {
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(this.syncJobMaxRunTime) > 0;
  }

  private void handleBulkResponse(BulkResponse bulkResponse) {
    for (BulkResponseItem item : bulkResponse.items()) {
      if (item.error() != null) {
        String errorMessage = item.error().reason();
        String itemId = item.id();
        log.error(format("Bulk request failed for item ID %s: %s", itemId, errorMessage));
      }
    }
  }
}