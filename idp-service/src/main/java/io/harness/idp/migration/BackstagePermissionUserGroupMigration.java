/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstagePermissionUserGroupMigration implements NGMigration {
  @Inject BackstagePermissionsService backstagePermissionsService;

  @Override
  public void migrate() {
    log.info("Migration started for Backstage Permission userGroups field.....");

    Iterable<BackstagePermissionsEntity> backstagePermissionsEntityList =
        backstagePermissionsService.getAllBackstagePermissions();

    for (BackstagePermissionsEntity backstagePermissionsEntity : backstagePermissionsEntityList) {
      if (backstagePermissionsEntity.getUserGroups() == null) {
        log.info("Migration for Backstage Permission userGroups field started for account - {}",
            backstagePermissionsEntity.getAccountIdentifier());
        backstagePermissionsEntity.setUserGroups(List.of(backstagePermissionsEntity.getUserGroup()));
        backstagePermissionsService.savePermission(backstagePermissionsEntity);

        log.info("Migration for Backstage Permission userGroups field completed for account - {}",
            backstagePermissionsEntity.getAccountIdentifier());
      }
    }

    log.info("Migration completed for Backstage Permission userGroups field.....");
  }
}
