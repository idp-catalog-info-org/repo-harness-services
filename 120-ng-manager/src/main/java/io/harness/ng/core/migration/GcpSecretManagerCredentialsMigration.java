/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector.GcpSecretManagerConnectorKeys;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.gcpconnector.GcpDelegateDetails;
import io.harness.connector.entities.embedded.gcpconnector.GcpServiceAccountKey;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpCredentialConfig;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@Slf4j
public class GcpSecretManagerCredentialsMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;

  int BATCH_SIZE = 500;
  public static final String GCP_CREDENTIAL_MIGRATION_CONSTANT = "[GcpSecretManagerCredentialsMigration]:";

  @Override
  public void migrate() {
    log.info("{} Starting migration", GCP_CREDENTIAL_MIGRATION_CONSTANT);

    int migratedCounter = 0;
    int totalCounter = 0;
    int batchSizeCounter = 0;

    Query documentQuery = new Query(Criteria.where(ConnectorKeys.type).is(ConnectorType.GCP_SECRET_MANAGER));

    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Connector.class);

    try (Stream<Connector> stream = mongoTemplate.stream(documentQuery.limit(BATCH_SIZE), Connector.class)) {
      Iterator<Connector> iterator = stream.iterator();
      while (iterator.hasNext()) {
        totalCounter++;
        batchSizeCounter++;
        GcpSecretManagerConnector connector = (GcpSecretManagerConnector) iterator.next();
        Update update;
        if (Boolean.TRUE.equals(connector.getAssumeCredentialsOnDelegate())) {
          update = new Update().set(GcpSecretManagerConnectorKeys.credentialConfig,
              GcpCredentialConfig.builder()
                  .credentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                  .credential(GcpDelegateDetails.builder().delegateSelectors(connector.getDelegateSelectors()).build())
                  .build());
        } else {
          update = new Update().set(GcpSecretManagerConnectorKeys.credentialConfig,
              GcpCredentialConfig.builder()
                  .credentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                  .credential(GcpServiceAccountKey.builder().secretKeyRef(connector.getCredentialsRef()).build())
                  .build());
        }
        bulkOperations.updateOne(new Query(Criteria.where(ConnectorKeys.id).is(connector.getId())), update);

        if (batchSizeCounter == BATCH_SIZE) {
          migratedCounter += bulkOperations.execute().getModifiedCount();
          bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Connector.class);
          batchSizeCounter = 0;
        }
      }

      if (batchSizeCounter > 0) {
        migratedCounter += bulkOperations.execute().getModifiedCount();
      }
    } catch (Exception ex) {
      log.error("{} job failed with error", GCP_CREDENTIAL_MIGRATION_CONSTANT, ex);
    }

    log.info("{} Migration completed. Total docs migrated- {}", GCP_CREDENTIAL_MIGRATION_CONSTANT, migratedCounter);
  }
}
