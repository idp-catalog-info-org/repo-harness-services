/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpCatalogEntitiesAsHarnessEntitiesMigration implements NGMigration {
  @Inject BackstageService backstageService;

  @Override
  public void migrate() {
    log.info("Starting the migration for bringing IDP catalog entities as Harness entities.");
    backstageService.sync();
    log.info("Completed the migration for bringing IDP catalog entities as Harness entities.");
  }
}
