/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PopulateQueryableEntityRefInCatalog implements NGMigration {
  NamespaceRepository namespaceRepository;
  CatalogEntityRepository catalogEntityRepository;
  CatalogServiceHelper catalogServiceHelper;

  @Override
  public void migrate() {
    log.info("Starting the migration for populating queryableEntityRef in catalog.");

    Iterable<NamespaceEntity> idpAccounts = namespaceRepository.findAll();
    idpAccounts.forEach(idpAccount -> {
      try {
        List<CatalogEntity> catalogEntitiesForAccount =
            catalogEntityRepository.findAllByAccountIdentifier(idpAccount.getAccountIdentifier());
        catalogEntitiesForAccount.forEach(catalogEntityForAccount -> {
          catalogEntityForAccount.setQueryableEntityRef(
              catalogServiceHelper.queryableEntityRef(catalogEntityForAccount));
          catalogEntityRepository.save(catalogEntityForAccount);
        });
      } catch (Exception ex) {
        log.error("Error in migration for populating queryableEntityRef in catalog for account = {} Exception = {}",
            idpAccount.getAccountIdentifier(), ex.getMessage(), ex);
      }
    });

    log.info("Completed the migration for populating queryableEntityRef in catalog.");
  }
}
