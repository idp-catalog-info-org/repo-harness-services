/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity.Metadata.NamespaceMetadataKeys;
import io.harness.idp.namespace.beans.entity.NamespaceEntity.NamespaceKeys;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScaffolderTasksCreatedByMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info(
        "Starting the migration for populating createdBy field in backstageScaffolderTasks collection by resetting "
        + "scaffolderTasksSyncFrom field in backstageNamespace collection");

    BasicDBObject basicDBObject = new BasicDBObject();
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(NamespaceEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject)
        .update(new BasicDBObject("$set",
            new BasicDBObject(NamespaceKeys.metadata + "." + NamespaceMetadataKeys.scaffolderTasksSyncFrom, 0L)));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated scaffolderTasksSyncFrom field successfully from {} records",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update scaffolderTasksSyncFrom field in any record");
    }

    log.info("Migration complete for populating createdBy field in backstageScaffolderTasks collection by resetting "
        + "scaffolderTasksSyncFrom field in backstageNamespace collection");
  }
}
