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
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.gitintegration.service.GitIntegrationServiceImpl;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AzureCatalogConnectorSchemaMigration implements NGMigration {
  @Inject CatalogConnectorRepository catalogConnectorRepository;
  @Inject GitIntegrationServiceImpl gitIntegrationService;

  @Override
  public void migrate() {
    log.info("Starting the migration for azure catalog connectors schema.");
    Iterable<CatalogConnectorEntity> catalogConnectorEntities = catalogConnectorRepository.findAll();
    catalogConnectorEntities.forEach(catalogConnectorEntity -> {
      if (catalogConnectorEntity.getConnectorProviderType().equals("AzureRepo")) {
        String accountIdentifier = catalogConnectorEntity.getAccountIdentifier();
        String connectorIdentifier = catalogConnectorEntity.getConnectorIdentifier();
        log.info("Processing {}/{} catalog connector for AzureRepo schema migration", accountIdentifier,
            connectorIdentifier);
        try {
          gitIntegrationService.saveConnectorDetails(accountIdentifier, connectorDetails(connectorIdentifier));
        } catch (Exception ex) {
          log.error("Error in processing {}/{} catalog connector AzureRepo schema migration", accountIdentifier,
              connectorIdentifier);
        }
      }
    });
    log.info("Completed the migration for azure catalog connectors schema.");
  }

  private ConnectorDetails connectorDetails(String connectorIdentifier) {
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier(connectorIdentifier);
    connectorDetails.setType(ConnectorDetails.TypeEnum.AZUREREPO);
    return connectorDetails;
  }
}
