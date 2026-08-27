/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ChecksChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    log.info("Check create event received for id {}, value {}", id, value);
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in checks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in checks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      Record record = dsl.fetchOne(dsl.select().from(Tables.CHECKS).where(Tables.CHECKS.ID.eq(id)));
      if (record != null && record.get(Tables.CHECKS.ACCOUNT_IDENTIFIER) != null
          && record.get(Tables.CHECKS.IDENTIFIER) != null && record.get(Tables.CHECKS.CUSTOM) != null) {
        String accountIdentifier = record.get(Tables.CHECKS.ACCOUNT_IDENTIFIER);
        String checkIdentifier = record.get(Tables.CHECKS.IDENTIFIER);
        boolean custom = record.get(Tables.CHECKS.CUSTOM);
        if (custom) {
          deleteScorecardsChecks(accountIdentifier, checkIdentifier);
          deleteCheckStats(accountIdentifier, checkIdentifier);
        } else {
          deleteScorecardsChecks(checkIdentifier);
          deleteCheckStats(checkIdentifier);
        }
      }
      dsl.delete(Tables.CHECKS).where(Tables.CHECKS.ID.eq(id)).execute();
      log.debug("Successfully deleted data in checks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in checks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    log.info("Check update event received for id {}, value {}", id, value);
    Record updateRecord = createRecord(id, value);
    if (updateRecord == null) {
      return true;
    }
    try {
      upsert(updateRecord);
      log.debug("Successfully updated data in checks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in checks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    Record createRecord = dsl.newRecord(Tables.CHECKS);
    createRecord.set(Tables.CHECKS.ID, id);

    populateFromRoot(node, createRecord);

    return createRecord;
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.CHECKS.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("identifier") != null) {
      createRecord.set(Tables.CHECKS.IDENTIFIER, node.get("identifier").asText());
    }

    if (node.get("name") != null) {
      createRecord.set(Tables.CHECKS.NAME, node.get("name").asText());
    }

    if (node.get("isCustom") != null) {
      createRecord.set(Tables.CHECKS.CUSTOM, node.get("isCustom").asBoolean());
    }

    if (node.get("description") != null) {
      createRecord.set(Tables.CHECKS.DESCRIPTION, node.get("description").asText());
    }

    if (node.get("ruleStrategy") != null) {
      createRecord.set(Tables.CHECKS.RULE_STRATEGY, node.get("ruleStrategy").asText());
    }

    if (node.get("expression") != null) {
      createRecord.set(Tables.CHECKS.EXPRESSION, node.get("expression").asText());
    }

    if (node.get("defaultBehaviour") != null) {
      createRecord.set(Tables.CHECKS.DEFAULT_BEHAVIOUR, node.get("defaultBehaviour").asText());
    }

    if (node.get("failMessage") != null) {
      createRecord.set(Tables.CHECKS.FAIL_MESSAGE, node.get("failMessage").asText());
    }

    if (node.get("isDeleted") != null) {
      createRecord.set(Tables.CHECKS.DELETED, node.get("isDeleted").asBoolean());
      if (node.get("isDeleted").asBoolean() && node.get("accountIdentifier") != null
          && node.get("identifier") != null) {
        String accountIdentifier = node.get("accountIdentifier").asText();
        String checkIdentifier = node.get("identifier").asText();
        deleteScorecardsChecks(accountIdentifier, checkIdentifier);
        deleteCheckStats(accountIdentifier, checkIdentifier);
        deleteCheck(accountIdentifier, checkIdentifier);
      }
    }

    if (node.get("createdAt") != null) {
      createRecord.set(Tables.CHECKS.CREATED_AT, node.get("createdAt").asLong());
    }

    if (node.get("createdBy") != null) {
      JsonNode createdByNode = node.get("createdBy");
      if (createdByNode.get("name") != null) {
        createRecord.set(Tables.CHECKS.CREATED_BY, createdByNode.get("name").asText());
      }
    } else {
      createRecord.set(Tables.CHECKS.CREATED_BY, "System");
    }

    if (node.get("lastUpdatedAt") != null) {
      createRecord.set(Tables.CHECKS.LAST_UPDATED_AT, node.get("lastUpdatedAt").asLong());
    }

    if (node.get("lastUpdatedBy") != null) {
      JsonNode lastUpdatedByNode = node.get("lastUpdatedBy");
      if (lastUpdatedByNode.get("name") != null) {
        createRecord.set(Tables.CHECKS.LAST_UPDATED_BY, lastUpdatedByNode.get("name").asText());
      }
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.CHECKS).set(upsertRecord).onConflict(Tables.CHECKS.ID).doUpdate().set(upsertRecord).execute();
  }

  private void deleteCheck(String accountIdentifier, String checkIdentifier) {
    try {
      dsl.delete(Tables.CHECKS)
          .where(Tables.CHECKS.ACCOUNT_IDENTIFIER.eq(accountIdentifier))
          .and(Tables.CHECKS.IDENTIFIER.eq(checkIdentifier))
          .execute();
      log.debug("Successfully deleted data in checks for accountId {} checkId {}", accountIdentifier, checkIdentifier);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in checks for accountId {} checkId {}", accountIdentifier,
          checkIdentifier, e);
    }
  }

  private void deleteScorecardsChecks(String accountIdentifier, String checkIdentifier) {
    try {
      int size = dsl.delete(Tables.SCORECARDS_CHECKS)
                     .where(Tables.SCORECARDS_CHECKS.ACCOUNT_IDENTIFIER.eq(accountIdentifier))
                     .and(Tables.SCORECARDS_CHECKS.CHECK_IDENTIFIER.eq(checkIdentifier))
                     .execute();
      log.debug("Successfully deleted data in scorecards_checks size {} for accountId {} checkId {}", size,
          accountIdentifier, checkIdentifier);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in scorecards_checks for accountId {} checkId {}",
          accountIdentifier, checkIdentifier, e);
    }
  }

  private void deleteCheckStats(String accountIdentifier, String checkIdentifier) {
    try {
      int size = dsl.delete(Tables.CHECK_STATS)
                     .where(Tables.CHECK_STATS.ACCOUNT_IDENTIFIER.eq(accountIdentifier))
                     .and(Tables.CHECK_STATS.CHECK_IDENTIFIER.eq(checkIdentifier))
                     .execute();
      log.debug("Successfully deleted data in check_stats size {} for accountId {} checkId {}", size, accountIdentifier,
          checkIdentifier);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in check_stats for accountId {} checkId {}", accountIdentifier,
          checkIdentifier, e);
    }
  }

  private void deleteScorecardsChecks(String checkIdentifier) {
    try {
      int size = dsl.delete(Tables.SCORECARDS_CHECKS)
                     .where(Tables.SCORECARDS_CHECKS.CHECK_IDENTIFIER.eq(checkIdentifier))
                     .and(Tables.SCORECARDS_CHECKS.CUSTOM.eq(false))
                     .execute();
      log.debug("Successfully deleted data in scorecards_checks size {} for checkId {} custom {}", size,
          checkIdentifier, false);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in scorecards_checks for checkId {} custom {}", checkIdentifier,
          false, e);
    }
  }

  private void deleteCheckStats(String checkIdentifier) {
    try {
      int size = dsl.delete(Tables.CHECK_STATS)
                     .where(Tables.CHECK_STATS.CHECK_IDENTIFIER.eq(checkIdentifier))
                     .and(Tables.CHECK_STATS.CUSTOM.eq(false))
                     .execute();
      log.debug(
          "Successfully deleted data in check_stats size {} for checkId {} custom {}", size, checkIdentifier, false);
    } catch (DataAccessException e) {
      log.error(
          "Caught exception while deleting data in check_stats for checkId {} custom {}", checkIdentifier, false, e);
    }
  }
}
