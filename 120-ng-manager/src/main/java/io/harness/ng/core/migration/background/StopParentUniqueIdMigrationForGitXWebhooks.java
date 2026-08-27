/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UniqueIdAware;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class StopParentUniqueIdMigrationForGitXWebhooks implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[StopParentUniqueIdMigrationForGitXWebhooks]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Stopping the ParentUniqueId migration for GitXWebhook");
      Query<NGManagerUniqueIdParentIdMigrationStatus> gitXwebhookRerunMigrationQuery =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName,
                  getTypeAliasValueOrNameForClass(GitXWebhook.class));

      persistence.update(gitXwebhookRerunMigrationQuery,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, true));
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to stop the ParentUniqueId migration for GitXWebhook", e);
    }
    log.info(DEBUG_LOG + ("Successfully stopped the ParentUniqueId migration for GitXWebhook"));
  }

  private String getTypeAliasValueOrNameForClass(Class<? extends UniqueIdAware> clazz) {
    if (clazz.isAnnotationPresent(TypeAlias.class)) {
      TypeAlias annotation = clazz.getAnnotation(TypeAlias.class);
      return annotation.value();
    }
    return clazz.getName();
  }
}
