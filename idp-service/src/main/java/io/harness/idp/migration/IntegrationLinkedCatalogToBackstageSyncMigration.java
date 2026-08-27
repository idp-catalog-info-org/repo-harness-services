/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class IntegrationLinkedCatalogToBackstageSyncMigration implements NGMigration {
  private static final String INTEGRATION_LINKAGE_PATH = "decorator._processed_data.metadata.integration";
  private static final int BATCH_SIZE = 100;

  @Inject NamespaceService namespaceService;
  @Inject MongoTemplate mongoTemplate;
  @Inject HarnessToIDPHelper harnessToIDPHelper;

  @Override
  public void migrate() {
    log.info("Starting IntegrationLinkedCatalogToBackstageSyncMigration.");
    namespaceService.getAccountIds().forEach(accountIdentifier -> {
      try {
        syncIntegrationLinkedEntitiesForAccount(accountIdentifier);
      } catch (Exception e) {
        log.error("Failed syncing integration-linked catalog entities for account {}", accountIdentifier, e);
      }
    });
    log.info("Completed IntegrationLinkedCatalogToBackstageSyncMigration.");
  }

  private void syncIntegrationLinkedEntitiesForAccount(String accountIdentifier) {
    Query query =
        new Query(Criteria.where("accountIdentifier").is(accountIdentifier).and(INTEGRATION_LINKAGE_PATH).exists(true));
    long matchedCount = mongoTemplate.count(query, CatalogEntity.class);
    SyncCounters counters = new SyncCounters();

    try (Stream<CatalogEntity> stream = mongoTemplate.stream(query, CatalogEntity.class)) {
      Iterator<CatalogEntity> iterator = stream.iterator();
      List<CatalogEntity> batch = new ArrayList<>(BATCH_SIZE);
      while (iterator.hasNext()) {
        batch.add(iterator.next());
        if (batch.size() >= BATCH_SIZE) {
          syncBatch(accountIdentifier, batch, counters);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        syncBatch(accountIdentifier, batch, counters);
      }
    }

    log.info("Integration-linked resync summary for account {}: matched={}, attempted={}, synced={}, failed={}.",
        accountIdentifier, matchedCount, counters.attempted, counters.synced, counters.failed);
  }

  private void syncBatch(String accountIdentifier, List<CatalogEntity> batch, SyncCounters counters) {
    int n = batch.size();
    counters.attempted += n;
    try {
      harnessToIDPHelper.harnessToIdpSync(new ArrayList<>(batch), accountIdentifier, UPDATE_ACTION);
      counters.synced += n;
    } catch (Exception e) {
      log.warn("Batch sync failed for account {} (batchSize={}); retrying per entity.", accountIdentifier, n, e);
      syncBatchWithPerEntityFallback(accountIdentifier, batch, counters);
    }
  }

  private void syncBatchWithPerEntityFallback(
      String accountIdentifier, List<CatalogEntity> batch, SyncCounters counters) {
    for (CatalogEntity entity : batch) {
      try {
        harnessToIDPHelper.harnessToIdpSync(List.of(entity), accountIdentifier, UPDATE_ACTION);
        counters.synced++;
      } catch (Exception e) {
        counters.failed++;
        log.error("Per-entity sync failed for account {} after batch failure", accountIdentifier, e);
      }
    }
  }

  private static final class SyncCounters {
    int attempted;
    int synced;
    int failed;
  }
}
