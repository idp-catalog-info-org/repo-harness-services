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
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.ng.core.licenseusage.entities.LicenseUsage;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.TypeAlias;

/*
 * Reset parentIdMigrationCompleted for licenseUsage entity
 * due to migration running first and newer entity not handling parentUniqueId field
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ResetLicenseUsageMigrationStatus implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[ResetLicenseUsageMigrationStatus]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting resetting of parentIdMigrationCompleted field for licenseUsage to false");
      Query<NGManagerUniqueIdParentIdMigrationStatus> licenseUsageMigrationStatusQuery =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName,
                  LicenseUsage.class.getAnnotation(TypeAlias.class).value());

      persistence.update(licenseUsageMigrationStatusQuery,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, false));
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset parentIdMigrationCompleted field for licenseUsage", e);
    }
    log.info(DEBUG_LOG + "Successfully reset parentIdMigrationCompleted field for licenseUsage to false");
  }
}
