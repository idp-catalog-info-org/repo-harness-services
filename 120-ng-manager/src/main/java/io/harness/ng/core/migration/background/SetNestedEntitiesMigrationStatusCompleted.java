/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.ng.core.migration.AddUniqueIdParentIdToEntitiesTask;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration class that sets the migration status to true
 * for entities returned by getEntitiesWithNestedParentUniqueId().
 */
@OwnedBy(PL)
@Slf4j
public class SetNestedEntitiesMigrationStatusCompleted implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[SetNestedEntitiesMigrationStatusCompleted]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting to set nested entities migration status to completed (true)");

      // Get the list of entity classes from getEntitiesWithNestedParentUniqueId()
      Map<Object, List<Map<String, String>>> entitiesMap =
          AddUniqueIdParentIdToEntitiesTask.getEntitiesWithNestedParentuniqueId();

      // Extract class names from the map keys
      Set<String> entityClassNames =
          entitiesMap.keySet().stream().map(clazz -> ((Class<?>) clazz).getName()).collect(Collectors.toSet());
      // user membership is differently
      entityClassNames.add(UserMembership.class.getName());

      log.info(DEBUG_LOG + "Updating migration status for entity classes: " + String.join(", ", entityClassNames));

      // Query only for entities that match our class names
      Query<NGManagerUniqueIdParentIdMigrationStatus> query =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class)
              .field(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName)
              .in(new ArrayList<>(entityClassNames));

      // Update only those specific entities
      persistence.update(query,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.uniqueIdMigrationCompleted, true)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, true)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.orphanEntityParentIdMigrationCompleted, true));

      log.info(DEBUG_LOG + "Successfully set migration status to completed (true) for " + entityClassNames.size()
          + " nested entity classes");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to set nested entities migration status to completed", e);
      throw new RuntimeException("Failed to set nested entities migration status to completed", e);
    }
  }
}
