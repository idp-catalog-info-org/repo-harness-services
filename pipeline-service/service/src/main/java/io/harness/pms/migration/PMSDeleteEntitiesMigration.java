/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.springdata.PersistenceUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PMSDeleteEntitiesMigration implements Runnable {
  private static final int BATCH_SIZE = 500;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PersistentLocker persistentLocker;

  private static final String DEBUG_LOG = "[PMSDeleteEntitiesMigration]: ";

  private final RetryPolicy<Object> deleteRetryPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed deleting entities; attempt: {}", "[Failed]: Failed deleting entities; attempt: {}");

  private final String PIPELINE_METADATA_V2_COLLECTION = "pipelineMetadataV2";

  private final String PIPELINE_METADATA_COLLECTION = "pipelineMetadata";

  private static final String LOCK_NAME = "PMSDeleteEntitiesMigration";
  // since this migration is mainly for SMP customers, it is enough to have a common key like
  // pmsDeleteStaleRecordsMigrationStatus the migration will not run if the value in cache is SUCCESS
  @Inject @Named("pmsDeleteEntitiesMigrationCache") private Cache<String, String> cache;

  private static final String CACHE_KEY = "pmsDeleteStaleRecordsMigrationStatus";

  private static final String SUCCESS = "SUCCESS";
  private static final String FAILED = "FAILED";
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public void run() {
    log.info(format("%s Migration starting...", DEBUG_LOG));

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_LOG + "Failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_LOG + " Unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
    } catch (Exception ex) {
      log.error(DEBUG_LOG + " Failed to acquire lock", ex);
    }
  }

  void execute() {
    if (cache.containsKey(CACHE_KEY) && cache.get(CACHE_KEY).equals(SUCCESS)) {
      log.info(format("%s Stale PMS records are already deleted, skipping...", DEBUG_LOG));
      return;
    }
    Instant start = Instant.now();

    try {
      // Step 1: Delete in batches from collections pipelineMetadataV2, pipelineMetadata using cursor
      deleteInBatch();

      // Step 2: Deleting documents from pipelinesPMS, inputSetsPMS and triggersNG
      deleteEntities("pipelinesPMS");
      deleteEntities("inputSetsPMS");
      deleteEntities("triggersNG");
      cache.put(CACHE_KEY, SUCCESS);
    } catch (Exception e) {
      log.error(format("%s Migration failed with error", DEBUG_LOG), e);
      cache.put(CACHE_KEY, FAILED);
    }
    log.info(format("%s Migration completed in %s", DEBUG_LOG, Duration.between(start, Instant.now())));
  }

  private void deleteInBatch() {
    long totalPipelinesDeletedInPipelineMetadataV2 = 0;
    long totalPipelinesDeletedInPipelineMetadata = 0;

    Query selectDeletedquery = new Query(Criteria.where("deleted").is(true));
    selectDeletedquery.fields()
        .include("accountId")
        .include("orgIdentifier")
        .include("projectIdentifier")
        .include("identifier");

    List<PipelineEntity> pipelinesBatch = new ArrayList<>(BATCH_SIZE);
    try (Stream<PipelineEntity> stream = mongoTemplate.stream(selectDeletedquery, PipelineEntity.class)) {
      Iterator<PipelineEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        PipelineEntity pipeline = iterator.next();
        pipelinesBatch.add(pipeline);

        // Once we reach the batch size, process the batch and clear it for the next batch
        if (pipelinesBatch.size() == BATCH_SIZE) {
          totalPipelinesDeletedInPipelineMetadataV2 +=
              deleteRelatedDocuments(pipelinesBatch, PIPELINE_METADATA_V2_COLLECTION);
          totalPipelinesDeletedInPipelineMetadata +=
              deleteRelatedDocuments(pipelinesBatch, PIPELINE_METADATA_COLLECTION);
          pipelinesBatch.clear();
        }
      }

      // Process any remaining items in the last batch
      if (!pipelinesBatch.isEmpty()) {
        totalPipelinesDeletedInPipelineMetadataV2 +=
            deleteRelatedDocuments(pipelinesBatch, PIPELINE_METADATA_V2_COLLECTION);
        totalPipelinesDeletedInPipelineMetadata += deleteRelatedDocuments(pipelinesBatch, PIPELINE_METADATA_COLLECTION);
      }
    }

    log.info(String.format("%s Successfully deleted %d entities from %s collection", DEBUG_LOG,
        totalPipelinesDeletedInPipelineMetadataV2, PIPELINE_METADATA_V2_COLLECTION));
    log.info(String.format("%s Successfully deleted %d entities from %s collection", DEBUG_LOG,
        totalPipelinesDeletedInPipelineMetadata, PIPELINE_METADATA_COLLECTION));
  }

  private void deleteEntities(String collectionName) {
    Query query = new Query(Criteria.where("deleted").is(true));
    Failsafe.with(deleteRetryPolicy).run(() -> {
      long count = mongoTemplate.remove(query, collectionName).getDeletedCount();
      log.info(format("%s Successfully deleted %s entities from %s collection", DEBUG_LOG, count, collectionName));
    });
  }

  private long deleteRelatedDocuments(List<PipelineEntity> pipelines, String collectionName) {
    long deletedCount = 0;
    if (isEmpty(pipelines)) {
      return deletedCount;
    }

    BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName);

    for (PipelineEntity pipeline : pipelines) {
      Query relatedQuery =
          new Query(new Criteria().andOperator(Criteria.where("accountIdentifier").is(pipeline.getAccountId()),
              Criteria.where("parentUniqueId").is(pipeline.getParentUniqueId()),
              Criteria.where("identifier").is(pipeline.getIdentifier())));

      bulkOps.remove(relatedQuery);
    }
    deletedCount = Failsafe.with(deleteRetryPolicy).get(() -> bulkOps.execute().getDeletedCount());

    return deletedCount;
  }
}
