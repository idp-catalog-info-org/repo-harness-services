/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity.LayoutsEntityKeys;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class LayoutHarnessManagedMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding harnessManaged field (default = true) in layouts collection.");

    BasicDBObject basicDBObject = new BasicDBObject();
    BasicDBObject updateOps = new BasicDBObject(LayoutsEntityKeys.harnessManaged, true);
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(LayoutEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Added harnessManaged field successfully for {} records", updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not add harnessManaged field to any record");
    }

    log.info("Migration complete for adding harnessManaged field (default = true) in layouts collection.");
  }
}
