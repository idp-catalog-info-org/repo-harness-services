/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CatalogDecoratorProcessedMetadataNullTagsMigration implements NGMigration {
  private static final String DECORATOR_METADATA_TAGS_PATH = "decorator._processed_data.metadata.tags";

  @Inject NamespaceService namespaceService;
  @Inject MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    log.info("Starting migration to remove null decorator._processed_data.metadata.tags.");
    namespaceService.getAccountIds().forEach(accountIdentifier -> {
      try {
        migrateAccount(accountIdentifier);
      } catch (Exception e) {
        log.error("Error while removing null decorator metadata tags for account {}", accountIdentifier, e);
      }
    });
    log.info("Completed migration to remove null decorator._processed_data.metadata.tags.");
  }

  private void migrateAccount(String accountIdentifier) {
    Query query = new Query(Criteria.where("accountIdentifier")
                                .is(accountIdentifier)
                                .and(DECORATOR_METADATA_TAGS_PATH)
                                .exists(true)
                                .type(10));
    Update update = new Update().unset(DECORATOR_METADATA_TAGS_PATH);

    long matchedCount = mongoTemplate.count(query, CatalogEntity.class);
    UpdateResult result = mongoTemplate.updateMulti(query, update, CatalogEntity.class);
    log.info("Matched {} entities and removed null decorator metadata tags for {} entities in account {}.",
        matchedCount, result.getModifiedCount(), accountIdentifier);
  }
}
