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
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

/*
 * Reset parentIdMigrationCompleted and orphanEntityParentIdMigrationCompleted for UserMembership entity
 * to allow re-running the migration after fixes
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ResetUserMembershipMigrationStatus implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[ResetUserMembershipMigrationStatus]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG
          + ("Starting reset of parentIdMigrationCompleted and orphanEntityParentIdMigrationCompleted fields for "
              + "UserMembership to false"));

      Query<NGManagerUniqueIdParentIdMigrationStatus> userMembershipMigrationStatusQuery =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName, UserMembership.class.getName());

      persistence.update(userMembershipMigrationStatusQuery,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, false)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.orphanEntityParentIdMigrationCompleted, false));

      log.info(DEBUG_LOG
          + ("Successfully reset parentIdMigrationCompleted and orphanEntityParentIdMigrationCompleted fields for "
              + "UserMembership to false"));
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset migration status fields for UserMembership", e);
    }
  }
}
