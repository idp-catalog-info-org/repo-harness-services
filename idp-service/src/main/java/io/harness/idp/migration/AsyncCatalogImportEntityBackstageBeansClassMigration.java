/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AsyncCatalogImportEntityBackstageBeansClassMigration implements NGMigration {
  private static final String ELEM_KIND = "elem.kind";

  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for updating _class field for catalogDomains, catalogSystems, catalogComponents "
        + "entities in asyncCatalogImport collection.");

    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(AsyncCatalogImportEntity.class).initializeUnorderedBulkOperation();

    BasicDBObject basicDBObject = new BasicDBObject("catalogDomains.entities.kind", "Domain");
    writeOperation.find(basicDBObject)
        .arrayFilters(List.of(new BasicDBObject(ELEM_KIND, "Domain")))
        .update(new BasicDBObject("$set",
            new BasicDBObject("catalogDomains.entities.$[elem]._class",
                "io.harness.idp.backstage.entities.BackstageCatalogDomainEntity")));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated _class field in catalogDomains entities[*] successfully for {} records",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update _class field in catalogDomains entities[*] for any record");
    }

    writeOperation = mongoPersistence.getCollection(AsyncCatalogImportEntity.class).initializeUnorderedBulkOperation();
    basicDBObject = new BasicDBObject("catalogSystems.entities.kind", "System");
    writeOperation.find(basicDBObject)
        .arrayFilters(List.of(new BasicDBObject(ELEM_KIND, "System")))
        .update(new BasicDBObject("$set",
            new BasicDBObject("catalogSystems.entities.$[elem]._class",
                "io.harness.idp.backstage.entities.BackstageCatalogSystemEntity")));
    updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated _class field in catalogSystems entities[*] successfully for {} records",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update _class field in catalogSystems entities[*] for any record");
    }

    writeOperation = mongoPersistence.getCollection(AsyncCatalogImportEntity.class).initializeUnorderedBulkOperation();
    basicDBObject = new BasicDBObject("catalogComponents.entities.kind", "Component");
    writeOperation.find(basicDBObject)
        .arrayFilters(List.of(new BasicDBObject(ELEM_KIND, "Component")))
        .update(new BasicDBObject("$set",
            new BasicDBObject("catalogComponents.entities.$[elem]._class",
                "io.harness.idp.backstage.entities.BackstageCatalogComponentEntity")));
    updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated _class field in catalogComponents entities[*] successfully for {} records",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update _class field in catalogComponents entities[*] for any record");
    }

    log.info("Completed migration for updating _class field for catalogDomains, catalogSystems, catalogComponents "
        + "entities in asyncCatalogImport collection.");
  }
}
