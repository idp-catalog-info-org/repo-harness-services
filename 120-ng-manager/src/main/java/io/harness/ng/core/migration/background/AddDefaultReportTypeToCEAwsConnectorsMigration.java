/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig.CEAwsConfigKeys;
import io.harness.migration.beans.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
public class AddDefaultReportTypeToCEAwsConnectorsMigration implements NGMigration {
  private static final String LOG_PREFIX = "[AddDefaultReportTypeToCEAwsConnectorsMigration]: ";
  private static final String REPORT_TYPE_FIELD = CEAwsConfigKeys.curAttributes + ".reportType";
  private static final String DEFAULT_REPORT_TYPE = "CUR_1_0";

  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info(LOG_PREFIX + "Starting migration to set default reportType=" + DEFAULT_REPORT_TYPE
          + " on CE AWS Billing connectors");

      DBCollection collection = hPersistence.getCollection(CEAwsConfig.class);

      // Find all CE_AWS connectors with BILLING feature enabled but no reportType set
      BasicDBObject filter = new BasicDBObject();
      filter.put(ConnectorKeys.type, "CE_AWS");
      filter.put(CEAwsConfigKeys.featuresEnabled, new BasicDBObject("$in", Collections.singletonList("BILLING")));
      filter.put(REPORT_TYPE_FIELD, new BasicDBObject("$exists", false));

      long count = collection.count(filter);
      log.info(LOG_PREFIX + "Found {} CE AWS connectors without reportType", count);

      if (count == 0) {
        log.info(LOG_PREFIX + "No connectors to migrate, skipping");
        return;
      }

      BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
      bulkWriteOperation.find(filter).update(
          new BasicDBObject("$set", new BasicDBObject(REPORT_TYPE_FIELD, DEFAULT_REPORT_TYPE)));
      bulkWriteOperation.execute();

      log.info(LOG_PREFIX + "Successfully set reportType=" + DEFAULT_REPORT_TYPE + " on {} CE AWS connectors", count);
    } catch (Exception e) {
      log.error(LOG_PREFIX + "Migration failed", e);
    }
  }
}
