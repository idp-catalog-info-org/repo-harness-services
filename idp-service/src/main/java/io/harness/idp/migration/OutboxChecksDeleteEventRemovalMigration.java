/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.audit.ResourceTypeConstants.IDP_CHECKS;
import static io.harness.audit.ResourceTypeConstants.IDP_SCORECARDS;
import static io.harness.idp.scorecard.checks.events.CheckDeleteEvent.CHECK_DELETED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.outbox.OutboxEvent;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OutboxChecksDeleteEventRemovalMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Override
  public void migrate() {
    log.info("Starting the migration for updating the correct resource type for check delete audit events");

    BasicDBObject basicDBObject = new BasicDBObject().append("_class", "outboxEvents");
    basicDBObject.append("resource.type", IDP_SCORECARDS);
    basicDBObject.append("eventType", CHECK_DELETED);
    BasicDBObject updateOps = new BasicDBObject("resource.type", IDP_CHECKS);
    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(OutboxEvent.class).initializeUnorderedBulkOperation();
    writeOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() > 0) {
      log.info("Updated correct resource type for check delete audit events for records - {}",
          updateOperationResult.getModifiedCount());
    } else {
      log.warn("Could not update correct resource type for check delete audit events record");
    }

    log.info(
        "Migration completed for updating correct resource type for check delete audit events in outbox collection");
  }
}
