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
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorToIntegrationMigration implements NGMigration {
  @Inject CatalogConnectorRepository catalogConnectorRepository;
  @Inject GitIntegrationServiceImpl gitIntegrationService;

  @Override
  public void migrate() {
    log.info("Starting the migration for catalogConnectors to integrations.");
    Iterable<CatalogConnectorEntity> catalogConnectorEntities = catalogConnectorRepository.findAll();
    log.info("Found {} catalog connectors for migrate to integrations", Iterables.size(catalogConnectorEntities));
    catalogConnectorEntities.forEach(catalogConnectorEntity -> {
      String accountIdentifier = catalogConnectorEntity.getAccountIdentifier();
      String connectorIdentifier = catalogConnectorEntity.getConnectorIdentifier();
      try {
        log.info(
            "Processing {}/{} catalog connector for migrate to integration", accountIdentifier, connectorIdentifier);
        GitIntegrationRequest gitIntegrationRequest = gitIntegrationRequest(connectorIdentifier);
        gitIntegrationService.save(accountIdentifier, gitIntegrationRequest, false, false);
      } catch (Exception ex) {
        log.error("Error in processing {}/{} catalog connector for migrate to integration", accountIdentifier,
            connectorIdentifier);
      }
    });
    log.info("Completed the migration for catalogConnectors to integrations.");
  }

  private GitIntegrationRequest gitIntegrationRequest(String connectorIdentifier) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
    return gitIntegrationRequest;
  }
}
