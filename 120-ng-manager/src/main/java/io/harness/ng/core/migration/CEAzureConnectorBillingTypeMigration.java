/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.connector.entities.embedded.ceazure.CEAzureConfig.CEAzureConfigKeys;
import static io.harness.delegate.beans.connector.ceazure.BillingType.ACTUAL;
import static io.harness.delegate.beans.connector.utils.ConnectorType.CE_AZURE;

import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.delegate.beans.connector.utils.CEFeatures;
import io.harness.migration.beans.NGMigration;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class CEAzureConnectorBillingTypeMigration implements NGMigration {
  private static final int BATCH_SIZE = 100;

  @Inject private ConnectorRepository connectorRepository;

  @Override
  public void migrate() {
    try {
      Criteria criteria = Criteria.where(ConnectorKeys.type)
                              .is(CE_AZURE)
                              .and(CEAzureConfigKeys.featuresEnabled)
                              .in(CEFeatures.BILLING)
                              .and(CEAzureConfigKeys.BILLING_TYPE)
                              .isNull();
      Query query = new Query(criteria);
      query.cursorBatchSize(BATCH_SIZE);
      Update update = new Update();
      update.set(CEAzureConfigKeys.BILLING_TYPE, ACTUAL);
      log.info("[CEAzureConnectorBillingTypeMigration] Query for updating Harness CEAzure connector Billing type: {}",
          query.toString());

      UpdateResult result = connectorRepository.updateMultiple(query, update);

      log.info("[CEAzureConnectorBillingTypeMigration] Successfully updated {} CEAzure connector Billing type",
          result.getModifiedCount());
    } catch (Exception e) {
      log.error("[CEAzureConnectorBillingTypeMigration] Failed to update CEAzure connector Billing type. Error: ", e);
    }
  }
}
