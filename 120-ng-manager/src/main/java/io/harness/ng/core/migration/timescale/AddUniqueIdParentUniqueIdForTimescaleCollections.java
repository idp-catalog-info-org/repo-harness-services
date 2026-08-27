/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus;
import io.harness.ng.core.entities.migration.NgManagerTsdbUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NgManagerTsdbUniqueIdParentIdMigrationStatus.NgManagerTsdbUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class AddUniqueIdParentUniqueIdForTimescaleCollections implements Runnable {
  private final TimeScaleDBService timeScaleDBService;
  private final MongoTemplate mongoTemplate;
  private final HPersistence persistence;
  private final PersistentLocker persistentLocker;

  private static final String FETCH_EXECUTIONS_BATCH =
      "SELECT id, account_id, org_identifier, project_identifier FROM %s WHERE parent_unique_id IS NULL "
      + "AND id > ? ORDER BY id ASC LIMIT ?";

  private static final String FETCH_EXECUTIONS_BATCH_FOR_PROJECTS =
      "SELECT id, account_identifier, org_identifier FROM projects WHERE parent_unique_id IS NULL "
      + "AND id > ? ORDER BY id ASC LIMIT ?";

  private static final String COUNT_RECORDS_WITHOUT_PARENT_UNIQUE_ID =
      "SELECT COUNT(*) FROM %s WHERE parent_unique_id IS NULL";

  private static final String FETCH_ORGANIZATION_FOR_ACCOUNT_AND_ORG_IDENTIFIER =
      "SELECT unique_id FROM organizations WHERE account_identifier = ? AND "
      + "identifier = ?";

  private static final String FETCH_PROJECT_FOR_ACCOUNT_AND_ORG_IDENTIFIER_AND_PROJECT_IDENTIFIER =
      "SELECT unique_id FROM projects WHERE account_identifier "
      + "= ? AND org_identifier = ? AND identifier = ?";

  private static final String UPDATE_PARENT_UNIQUE_ID_PROJECT =
      "UPDATE %s SET parent_unique_id = ? WHERE account_id = ? AND org_identifier = ? AND project_identifier = ?";

  private static final String UPDATE_PARENT_UNIQUE_ID_ACCOUNT_ORG_ONLY =
      "UPDATE %s SET parent_unique_id = ? WHERE account_id = ? AND org_identifier = ? AND project_identifier IS NULL";

  private static final String UPDATE_PARENT_UNIQUE_ID_ACCOUNT_ONLY =
      "UPDATE %s SET parent_unique_id = ? WHERE account_id = ? AND org_identifier IS NULL AND project_identifier IS "
      + "NULL";
  private static final String UPDATE_PARENT_UNIQUE_ID_FOR_PROJECTS =
      "UPDATE projects SET parent_unique_id = ? WHERE account_identifier = ? AND org_identifier = ? ";

  private static final String debugLine = "ParentUniqueIdTimescaleMigrationLog: ";

  private static final List<String> tablesList =
      List.of("projects", "infrastructures", "services", "environments", "pipelines");

  private final Map<String, String> uniqueIdMap;
  private static final String LOCAL_MAP_DELIMITER = "|";
  private static final String ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX = "orphan_";
  private static final int DEFAULT_BATCH_SIZE = 1000;
  private static final int MAX_RETRY_COUNT = 5;
  private static final String LOCK_NAME_PREFIX = "NGEntitiesTsdbPeriodicMigrationTaskLock";

  @Inject
  public AddUniqueIdParentUniqueIdForTimescaleCollections(TimeScaleDBService timeScaleDBService,
      MongoTemplate mongoTemplate, HPersistence persistence, PersistentLocker persistentLocker) {
    this.timeScaleDBService = timeScaleDBService;
    this.mongoTemplate = mongoTemplate;
    this.persistence = persistence;
    this.persistentLocker = persistentLocker;
    this.uniqueIdMap = new HashMap<>();
  }

  @Override
  public void run() {
    log.info(format("%s starting...", debugLine));
    for (String tableName : tablesList) {
      NgManagerTsdbUniqueIdParentIdMigrationStatus foundEntity = getMigrationStatus(tableName);
      if (foundEntity.getMigrationCompleted()) {
        log.info(debugLine + "Migration already completed for Table: " + tableName);
      } else {
        try {
          runParentIdMigration(tableName, foundEntity);
        } catch (Exception ex) {
          log.error(debugLine + "Exception while running migration for class: " + tableName, ex);
        }
      }
    }
  }

  private NgManagerTsdbUniqueIdParentIdMigrationStatus getMigrationStatus(String tableName) {
    NgManagerTsdbUniqueIdParentIdMigrationStatus foundEntity =
        persistence.createQuery(NgManagerTsdbUniqueIdParentIdMigrationStatus.class)
            .field(NgManagerTsdbUniqueIdParentIdMigrationStatusKeys.entityClassName)
            .equal(tableName)
            .get();

    if (foundEntity == null) {
      foundEntity = NgManagerTsdbUniqueIdParentIdMigrationStatus.builder()
                        .entityClassName(tableName)
                        .migrationCompleted(false)
                        .build();
    }
    return foundEntity;
  }

  private void runParentIdMigration(String tableName, NgManagerTsdbUniqueIdParentIdMigrationStatus foundEntity) {
    if (tableName.equals("projects")) {
      // first make sure that the cdc for organizations is done so that the org collection will be upto date with mongo
      // data
      Query<NGManagerCDCEntitiesMigrationStatus> orgQuery =
          persistence.createQuery(NGManagerCDCEntitiesMigrationStatus.class)
              .filter(NGManagerCDCEntitiesMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName,
                  Organization.class.getSimpleName());

      NGManagerCDCEntitiesMigrationStatus orgMigrationStatus = orgQuery.get();
      if (!(orgMigrationStatus != null && orgMigrationStatus.getCdcMigrationCompleted())) {
        // skip migration for projects
        log.info(
            debugLine + "Skipping projects timescale migration. Waiting for organizations cdc migration to complete");
        return;
      }
    }
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (!timeScaleDBService.isValid()) {
        log.info(debugLine + "TIMESCALEDBSERVICE NOT AVAILABLE. Skipping migration.");
        return;
      }

      try (Connection connection = timeScaleDBService.getDBConnection()) {
        log.info(debugLine + "Starting migration for {} to add parentUniqueId", tableName);

        String idForLastSeenRecord = "000000000000000000000000";
        String idForLastSeenRecord_inBatch = "";
        int retry = 0;
        int totalRecordsUpdated = 0;
        int totalRecordsToUpdate = getTotalRecordsToUpdate(connection, tableName);

        while (retry < MAX_RETRY_COUNT) {
          List<Map<String, String>> batchRecords =
              fetchBatchRecords(connection, tableName, idForLastSeenRecord, DEFAULT_BATCH_SIZE);

          if (batchRecords.isEmpty()) {
            break;
          }

          try {
            idForLastSeenRecord_inBatch = updateParentUniqueIdColumn(connection, tableName, batchRecords);
            totalRecordsUpdated += batchRecords.size();
            idForLastSeenRecord = idForLastSeenRecord_inBatch;
          } catch (Exception e) {
            log.error(debugLine + "Exception while updating parentUniqueId for batch for table {}", tableName, e);
            retry++;
            continue;
          }

          Thread.sleep(1000); // Sleep for 1000ms to avoid overwhelming the database
        }
        log.info(debugLine + "Total records to update for collection {} : {}", tableName, totalRecordsToUpdate);
        if (totalRecordsToUpdate == totalRecordsUpdated) {
          log.info(debugLine + "Migration completed.");
          foundEntity.setMigrationCompleted(true);
          mongoTemplate.save(foundEntity);
        } else {
          log.info(debugLine + "Migration not completed for table {}. Total records to pending to update: {}",
              tableName, totalRecordsToUpdate - totalRecordsUpdated);
        }

      } catch (Exception e) {
        log.error(debugLine + "Exception during migration for table {}", tableName, e);
      }
    }
  }

  private int getTotalRecordsToUpdate(Connection connection, String tableName) {
    String sql = String.format(COUNT_RECORDS_WITHOUT_PARENT_UNIQUE_ID, tableName);
    try (PreparedStatement fetchStatement = connection.prepareStatement(sql)) {
      ResultSet resultSet = fetchStatement.executeQuery();
      if (resultSet.next()) {
        return resultSet.getInt(1); // first column = COUNT(*)
      }
    } catch (Exception e) {
      log.error(debugLine + "Failed to fetch total records to update for table {}", tableName, e);
    }
    return 0;
  }

  private List<Map<String, String>> fetchBatchRecords(
      Connection connection, String tableName, String idForLastSeenRecord, int batchSize) {
    List<Map<String, String>> batchRecords = new ArrayList<>();
    // A Set to track unique combinations of (account_id, org_identifier, project_identifier)
    Set<String> seenKeys = new HashSet<>();
    if (Objects.equals(tableName, "projects")) {
      try (PreparedStatement fetchStatement = connection.prepareStatement(FETCH_EXECUTIONS_BATCH_FOR_PROJECTS)) {
        fetchStatement.setString(1, idForLastSeenRecord);
        fetchStatement.setInt(2, batchSize);
        ResultSet resultSet = fetchStatement.executeQuery();

        while (resultSet.next()) {
          Map<String, String> record = new HashMap<>();
          String accountId = Optional.ofNullable(resultSet.getString("account_identifier")).orElse("");
          String orgId = Optional.ofNullable(resultSet.getString("org_identifier")).orElse("");
          record.put("id", resultSet.getString("id"));
          record.put("account_id", accountId);
          record.put("org_identifier", orgId);

          String uniqueKey = accountId + "|" + orgId;
          if (seenKeys.contains(uniqueKey)) {
            continue;
          }
          seenKeys.add(uniqueKey);
          batchRecords.add(record);
        }
      } catch (Exception e) {
        log.error(debugLine + "Failed to fetch execution records from TimescaleDB", e);
      }
    } else {
      String sql = String.format(FETCH_EXECUTIONS_BATCH, tableName);
      try (PreparedStatement fetchStatement = connection.prepareStatement(sql)) {
        fetchStatement.setString(1, idForLastSeenRecord);
        fetchStatement.setInt(2, batchSize);
        ResultSet resultSet = fetchStatement.executeQuery();

        while (resultSet.next()) {
          Map<String, String> record = new HashMap<>();
          String accountId = Optional.ofNullable(resultSet.getString("account_id")).orElse("");
          String orgId = Optional.ofNullable(resultSet.getString("org_identifier")).orElse("");
          String projectId = Optional.ofNullable(resultSet.getString("project_identifier")).orElse("");
          record.put("id", resultSet.getString("id"));
          record.put("account_id", accountId);
          record.put("org_identifier", orgId);
          record.put("project_identifier", projectId);

          String uniqueKey = accountId + "|" + orgId + "|" + projectId;
          if (seenKeys.contains(uniqueKey)) {
            continue;
          }
          seenKeys.add(uniqueKey);
          batchRecords.add(record);
        }
      } catch (Exception e) {
        log.error(debugLine + "Failed to fetch execution records from TimescaleDB", e);
      }
    }
    return batchRecords;
  }

  private String updateParentUniqueIdColumn(
      Connection connection, String tableName, List<Map<String, String>> batchRecords) throws SQLException {
    if (Objects.equals(tableName, "projects")) {
      try (PreparedStatement updateStatement = connection.prepareStatement(UPDATE_PARENT_UNIQUE_ID_FOR_PROJECTS)) {
        for (Map<String, String> record : batchRecords) {
          String account = record.get("account_id");
          String org = record.get("org_identifier");
          String parentUniqueId = fetchParentUniqueId(connection, account, org, null);
          updateStatement.setString(1, parentUniqueId);
          updateStatement.setString(2, account);
          updateStatement.setString(3, org);
          updateStatement.addBatch();
        }
        updateStatement.executeBatch();
      } catch (SQLException e) {
        log.error("Error updating parentUniqueId in TimescaleDB", e);
        throw e;
      }
    } else {
      String sqlWithProject = String.format(UPDATE_PARENT_UNIQUE_ID_PROJECT, tableName);
      String sqlWithOrgOnly = String.format(UPDATE_PARENT_UNIQUE_ID_ACCOUNT_ORG_ONLY, tableName);
      String sqlAccountOnly = String.format(UPDATE_PARENT_UNIQUE_ID_ACCOUNT_ONLY, tableName);

      try (PreparedStatement stmtWithProject = connection.prepareStatement(sqlWithProject);
           PreparedStatement stmtWithOrgOnly = connection.prepareStatement(sqlWithOrgOnly);
           PreparedStatement stmtAccountOnly = connection.prepareStatement(sqlAccountOnly)) {
        for (Map<String, String> record : batchRecords) {
          String account = record.get("account_id");
          String org = record.get("org_identifier");
          String project = record.get("project_identifier");
          String parentUniqueId = fetchParentUniqueId(connection, account, org, project);

          if (isNotEmpty(project)) {
            // Case 1: has project_identifier
            stmtWithProject.setString(1, parentUniqueId);
            stmtWithProject.setString(2, account);
            stmtWithProject.setString(3, org);
            stmtWithProject.setString(4, project);
            stmtWithProject.addBatch();
          } else if (isNotEmpty(org)) {
            // Case 2: org_identifier only
            stmtWithOrgOnly.setString(1, parentUniqueId);
            stmtWithOrgOnly.setString(2, account);
            stmtWithOrgOnly.setString(3, org);
            stmtWithOrgOnly.addBatch();
          } else {
            // Case 3: account_id only
            stmtAccountOnly.setString(1, parentUniqueId);
            stmtAccountOnly.setString(2, account);
            stmtAccountOnly.addBatch();
          }
        }

        // Execute each batch once
        stmtWithProject.executeBatch();
        stmtWithOrgOnly.executeBatch();
        stmtAccountOnly.executeBatch();
      } catch (SQLException e) {
        log.error("Error updating parentUniqueId in TimescaleDB", e);
        throw e;
      }
    }

    if (batchRecords.isEmpty()) {
      return "000000000000000000000000";
    }
    return batchRecords.get(batchRecords.size() - 1).get("id");
  }

  private String fetchParentUniqueId(Connection connection, String account, String org, String proj) {
    if (isEmpty(account) || (isEmpty(org) && isNotEmpty(proj))) {
      // wrong entry or corrupted entry
      return ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
    }

    String mapKey = null;
    if (isNotEmpty(org) && isNotEmpty(proj)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org + LOCAL_MAP_DELIMITER + proj;
    } else if (isNotEmpty(org)) {
      mapKey = account + LOCAL_MAP_DELIMITER + org;
    } else {
      return account;
    }

    String uniqueId = null;
    if (uniqueIdMap.containsKey(mapKey)) {
      uniqueId = uniqueIdMap.get(mapKey);
    } else {
      if (isNotEmpty(proj)) {
        try (PreparedStatement fetchStatement =
                 connection.prepareStatement(FETCH_PROJECT_FOR_ACCOUNT_AND_ORG_IDENTIFIER_AND_PROJECT_IDENTIFIER)) {
          fetchStatement.setString(1, account);
          fetchStatement.setString(2, org);
          fetchStatement.setString(3, proj);
          try (ResultSet resultSet = fetchStatement.executeQuery()) {
            if (resultSet.next()) {
              // row exists
              uniqueId = resultSet.getString("unique_id");
            } else {
              // no row found → generate UUID
              uniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
            }
          }
        } catch (SQLException e) {
          log.error(debugLine + "Error fetching project from PROJECT table", e);
        }

      } else {
        try (PreparedStatement fetchStatement =
                 connection.prepareStatement(FETCH_ORGANIZATION_FOR_ACCOUNT_AND_ORG_IDENTIFIER)) {
          fetchStatement.setString(1, account);
          fetchStatement.setString(2, org);
          try (ResultSet resultSet = fetchStatement.executeQuery()) {
            if (resultSet.next()) {
              // row exists
              uniqueId = resultSet.getString("unique_id");
            } else {
              // no row found → generate UUID
              uniqueId = ORPHAN_ENTITY_PARENT_UNIQUE_ID_PREFIX + UUIDGenerator.generateUuid();
            }
          }
        } catch (SQLException e) {
          log.error(debugLine + "Error fetching organisation from ORGANIZATION table", e);
        }
      }
      uniqueIdMap.put(mapKey, uniqueId);
    }
    return uniqueId;
  }
}