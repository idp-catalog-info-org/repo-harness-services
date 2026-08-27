
/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntitiesSystemListMigration implements NGMigration {
  BackstageService backstageService;
  NamespaceService namespaceService;

  @Override
  public void migrate() {
    log.info("Starting the migration for changing system as list in backstage catalog.");
    List<NamespaceEntity> namespaceEntities = namespaceService.getActiveAccounts();
    for (NamespaceEntity namespaceEntity : namespaceEntities) {
      backstageService.changeSystemAsList(namespaceEntity.getAccountIdentifier());
    }
    log.info("Migration completed for adding parentUniqueId and unique ID to groups");
  }
}
