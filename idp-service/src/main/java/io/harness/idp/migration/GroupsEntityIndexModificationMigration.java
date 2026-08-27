/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoCommandException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GroupsEntityIndexModificationMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for index modification in groups collection.");

    try {
      mongoPersistence.getCollection(GroupEntity.class).dropIndex("unique_accountIdentifier_identifier");
      log.info("Dropped unique_accountIdentifier_identifier index on groups collection");
    } catch (MongoCommandException ex) {
      if (ex.getErrorCode() == 27) { // IndexNotFound
        log.info("Index unique_accountIdentifier_identifier not found; nothing to drop.");
      } else {
        log.error("Error dropping unique_accountIdentifier_identifier index on groups collection. Error = {}",
            ex.getMessage(), ex);
        throw new RuntimeException("Failed to drop index unique_accountIdentifier_identifier in groups collection", ex);
      }
    } catch (Exception ex) {
      log.error("Unexpected error dropping unique_accountIdentifier_identifier index on groups collection. Error = {}",
          ex.getMessage(), ex);
      throw new RuntimeException("Failed to drop index unique_accountIdentifier_identifier in groups collection", ex);
    }

    try {
      BasicDBObject indexKeys = new BasicDBObject()
                                    .append(GroupEntity.GroupsEntityKeys.parentUniqueId, 1)
                                    .append(GroupEntity.GroupsEntityKeys.identifier, 1);

      BasicDBObject indexOptions =
          new BasicDBObject().append("unique", true).append("name", "unique_parentUniqueId_identifier");

      mongoPersistence.getCollection(GroupEntity.class).createIndex(indexKeys, indexOptions);

      log.info("Created unique compound index on (parentUniqueId, identifier) in groups collection");
    } catch (Exception ex) {
      log.error(
          "Error creating unique compound index on (parentUniqueId, identifier). Error = {}", ex.getMessage(), ex);
    }

    try {
      BasicDBObject indexKeysForUniqueId = new BasicDBObject().append(GroupEntity.GroupsEntityKeys.uniqueId, 1);

      BasicDBObject indexOptionsForUniqueId =
          new BasicDBObject().append("unique", true).append("name", "unique_uniqueId");

      mongoPersistence.getCollection(GroupEntity.class).createIndex(indexKeysForUniqueId, indexOptionsForUniqueId);

      log.info("Created unique compound index on uniqueId in groups collection");
    } catch (Exception ex) {
      log.error("Error creating unique compound index on uniqueId. Error = {}", ex.getMessage(), ex);
    }

    log.info("Migration complete for index modification in groups collection.");
  }
}