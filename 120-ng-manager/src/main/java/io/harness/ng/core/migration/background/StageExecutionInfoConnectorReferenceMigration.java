/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class StageExecutionInfoConnectorReferenceMigration implements NGMigration {
  private static final int BATCH_SIZE = 100;
  private final MongoTemplate mongoTemplate;
  private static final String OLD_AWS_CLASS = "io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO";
  private static final String NEW_AWS_CLASS = "io.harness.delegate.beans.connector.AwsConnectorDTO";

  private static final String DEBUG_LOG = "[StageExecutionInfoConnectorReferenceMigration]: ";

  private static final String OLD_GCP_CLASS = "io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO";
  private static final String NEW_GCP_CLASS = "io.harness.delegate.beans.connector.GcpConnectorDTO";

  private static final String STAGE_STATUS = "stageStatus";
  private static final String SUCCEEDED_STATUS = "SUCCEEDED";

  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";

  private static final String STAGE_EXECUTION_INFO_COLLECTION_NAME = "stageExecutionInfo";

  private static final String CONNECTOR_CONFIG_CLASS_PATH = "executionDetails.artifactConfig.connectorConfig._class";

  @Inject
  public StageExecutionInfoConnectorReferenceMigration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    log.info("Starting StageExecutionInfoMigration to update connectorConfig class names for AWS and GCP connectors");

    // Call the unified function to handle both AWS and GCP migrations
    migrateConnectors();

    log.info("StageExecutionInfoMigration: Migration completed");
  }

  private void migrateConnectors() {
    log.info("Migrating AWS and GCP Connector DTOs");

    // Combine criteria for both AWS and GCP connectors
    Criteria awsCriteria =
        Criteria.where(STAGE_STATUS).is(SUCCEEDED_STATUS).and(CONNECTOR_CONFIG_CLASS_PATH).is(OLD_AWS_CLASS);

    Criteria gcpCriteria =
        Criteria.where(STAGE_STATUS).is(SUCCEEDED_STATUS).and(CONNECTOR_CONFIG_CLASS_PATH).is(OLD_GCP_CLASS);

    // Using  $or to handle both conditions in the same query
    Criteria combinedCriteria = new Criteria().orOperator(awsCriteria, gcpCriteria);
    Query combinedQuery = new Query(combinedCriteria);

    // Process both AWS and GCP connector class updates in a single batch-wise operation
    processBatches(combinedQuery);
  }

  private void processBatches(Query query) {
    log.info("Processing batch-wise updates for AWS and GCP class changes");

    int count = 0;
    try {
      while (true) {
        // Fetch a batch of documents
        List<Document> stageExecutionInfos =
            mongoTemplate.find(query.limit(BATCH_SIZE), Document.class, "stageExecutionInfo");

        if (stageExecutionInfos.isEmpty()) {
          log.info("No more documents to process. Total processed: {}", count);
          break;
        }

        for (Document document : stageExecutionInfos) {
          String currentClass = document.get("executionDetails", Document.class)
                                    .get("artifactConfig", Document.class)
                                    .get("connectorConfig", Document.class)
                                    .getString("_class");

          // Determine the new class based on the current class
          String newClassValue = null;
          if (OLD_AWS_CLASS.equals(currentClass)) {
            newClassValue = NEW_AWS_CLASS;
          } else if (OLD_GCP_CLASS.equals(currentClass)) {
            newClassValue = NEW_GCP_CLASS;
          }

          if (newClassValue != null) {
            Object documentId = document.getObjectId("_id"); // Get the _id from the document
            // Create the query using _id
            Query updateQuery = new Query().addCriteria(Criteria.where("_id").is(documentId));
            // Create the update operation
            Update update = new Update().set(CONNECTOR_CONFIG_CLASS_PATH, newClassValue);
            // Perform the update on the document with the retrieved _id
            mongoTemplate.updateFirst(updateQuery, update, STAGE_EXECUTION_INFO_COLLECTION_NAME);
            log.info("Migrated document with _id: {}", documentId);
          }
        }

        count += stageExecutionInfos.size();
        log.info("Processed {} documents so far", count);

        // Optional: Sleep between batches to avoid overloading MongoDB
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
          log.error("Sleep interrupted during batch processing", e);
        }
      }
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Error migrating document for stageExecution Info. Migrated %d documents", e, count);
    }
  }
}
