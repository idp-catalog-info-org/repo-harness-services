/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.ScorecardsChecksRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep4;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ScorecardsChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    log.info("Scorecard create event received for id {}, value {}", id, value);
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in scorecards for id {}", id);
      upsertScorecardsChecks(value);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in scorecards for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      Record record = dsl.fetchOne(dsl.select().from(Tables.SCORECARDS).where(Tables.SCORECARDS.ID.eq(id)));
      if (record != null && record.get(Tables.SCORECARDS.ACCOUNT_IDENTIFIER) != null
          && record.get(Tables.SCORECARDS.IDENTIFIER) != null) {
        String accountIdentifier = record.get(Tables.SCORECARDS.ACCOUNT_IDENTIFIER);
        String scorecardIdentifier = record.get(Tables.SCORECARDS.IDENTIFIER);
        deleteScorecardsChecks(accountIdentifier, scorecardIdentifier);
        deleteScorecardStats(accountIdentifier, scorecardIdentifier);
      }
      dsl.delete(Tables.SCORECARDS).where(Tables.SCORECARDS.ID.eq(id)).execute();
      log.debug("Successfully deleted data in scorecards for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in scorecards for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    log.info("Scorecard update event received for id {}, value {}", id, value);
    Record updateRecord = createRecord(id, value);
    if (updateRecord == null) {
      return true;
    }
    try {
      upsert(updateRecord);
      log.debug("Successfully updated data in scorecards for id {}", id);
      upsertScorecardsChecks(value);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in scorecards for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    Record createRecord = dsl.newRecord(Tables.SCORECARDS);
    createRecord.set(Tables.SCORECARDS.ID, id);

    populateFromRoot(node, createRecord);
    populateFromFilter(node, createRecord);
    populateFromChecks(node, createRecord);

    return createRecord;
  }

  @SneakyThrows
  public void upsertScorecardsChecks(String value) {
    JsonNode node = objectMapper.readTree(value);
    if (node.get("checks") != null) {
      JsonNode nodeChecks = node.get("checks");
      List<JsonNode> checks = StreamSupport.stream(nodeChecks.spliterator(), false).toList();
      String accountIdentifier = node.get("accountIdentifier").asText();
      String scorecardIdentifier = node.get("identifier").asText();
      try {
        InsertValuesStep4<ScorecardsChecksRecord, String, String, String, Boolean> bulkInsert =
            dsl.insertInto(Tables.SCORECARDS_CHECKS, Tables.SCORECARDS_CHECKS.ACCOUNT_IDENTIFIER,
                Tables.SCORECARDS_CHECKS.SCORECARD_IDENTIFIER, Tables.SCORECARDS_CHECKS.CHECK_IDENTIFIER,
                Tables.SCORECARDS_CHECKS.CUSTOM);
        for (JsonNode check : checks) {
          bulkInsert.values(accountIdentifier, scorecardIdentifier, check.get("identifier").asText(),
              check.get("isCustom").asBoolean());
        }
        int size = bulkInsert.onConflictOnConstraint(Tables.SCORECARDS_CHECKS.getPrimaryKey()).doNothing().execute();
        log.debug("Successfully inserted scorecards_checks batch size {} for accountId: {} scorecardId: {}", size,
            accountIdentifier, scorecardIdentifier);
      } catch (Exception e) {
        log.error("Exception while bulk insert scorecards_checks for account {} scorecardId: {}", accountIdentifier,
            scorecardIdentifier, e);
      }
    }
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.SCORECARDS.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("identifier") != null) {
      createRecord.set(Tables.SCORECARDS.IDENTIFIER, node.get("identifier").asText());
    }

    if (node.get("name") != null) {
      createRecord.set(Tables.SCORECARDS.NAME, node.get("name").asText());
    }

    if (node.get("description") != null) {
      createRecord.set(Tables.SCORECARDS.DESCRIPTION, node.get("description").asText());
    }

    if (node.get("weightageStrategy") != null) {
      createRecord.set(Tables.SCORECARDS.WEIGHTAGE_STRATEGY, node.get("weightageStrategy").asText());
    }

    if (node.get("published") != null) {
      createRecord.set(Tables.SCORECARDS.PUBLISHED, node.get("published").asBoolean());
    }

    if (node.get("isDeleted") != null) {
      createRecord.set(Tables.SCORECARDS.DELETED, node.get("isDeleted").asBoolean());
    }

    if (node.get("createdAt") != null) {
      createRecord.set(Tables.SCORECARDS.CREATED_AT, node.get("createdAt").asLong());
    }

    if (node.get("createdBy") != null) {
      JsonNode createdByNode = node.get("createdBy");
      if (createdByNode.get("name") != null) {
        createRecord.set(Tables.SCORECARDS.CREATED_BY, createdByNode.get("name").asText());
      }
    }

    if (node.get("lastUpdatedAt") != null) {
      createRecord.set(Tables.SCORECARDS.LAST_UPDATED_AT, node.get("lastUpdatedAt").asLong());
    }

    if (node.get("lastUpdatedBy") != null) {
      JsonNode lastUpdatedByNode = node.get("lastUpdatedBy");
      if (lastUpdatedByNode.get("name") != null) {
        createRecord.set(Tables.SCORECARDS.LAST_UPDATED_BY, lastUpdatedByNode.get("name").asText());
      }
    }
  }

  private void populateFromFilter(JsonNode node, Record createRecord) {
    if (node.get("filter") != null) {
      JsonNode nodeFilter = node.get("filter");
      String filter = nodeFilter.get("kind").asText() + " | " + nodeFilter.get("type").asText();
      List<String> owners = nodeFilter.get("owners") != null
          ? StreamSupport.stream(nodeFilter.get("owners").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList())
          : new ArrayList<>();
      if (isNotEmpty(owners)) {
        filter = filter + " | " + String.join(", ", owners);
      }
      List<String> tags = nodeFilter.get("tags") != null
          ? StreamSupport.stream(nodeFilter.get("tags").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList())
          : new ArrayList<>();
      if (isNotEmpty(tags)) {
        filter = filter + " | " + String.join(", ", tags);
      }
      List<String> lifecycle = nodeFilter.get("lifecycle") != null
          ? StreamSupport.stream(nodeFilter.get("lifecycle").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList())
          : new ArrayList<>();
      if (isNotEmpty(lifecycle)) {
        filter = filter + " | " + String.join(", ", lifecycle);
      }
      List<String> scopes = nodeFilter.get("scopes") != null
          ? StreamSupport.stream(nodeFilter.get("scopes").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList())
          : new ArrayList<>();

      if (isNotEmpty(scopes)) {
        filter = filter + " | " + String.join(", ", scopes);
      }
      createRecord.set(Tables.SCORECARDS.FILTER, filter);
    }
  }

  private void populateFromChecks(JsonNode node, Record createRecord) {
    if (node.get("checks") != null) {
      JsonNode nodeChecks = node.get("checks");
      List<JsonNode> checks = StreamSupport.stream(nodeChecks.spliterator(), false).collect(Collectors.toList());
      createRecord.set(Tables.SCORECARDS.TOTAL_NUMBER_OF_CHECKS, (short) checks.size());
      List<JsonNode> customChecks =
          checks.stream().filter(jsonNode -> jsonNode.get("isCustom").asBoolean()).collect(Collectors.toList());
      createRecord.set(Tables.SCORECARDS.NUMBER_OF_CUSTOM_CHECKS, (short) customChecks.size());
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.SCORECARDS)
        .set(upsertRecord)
        .onConflict(Tables.SCORECARDS.ID)
        .doUpdate()
        .set(upsertRecord)
        .execute();
  }

  private void deleteScorecardsChecks(String accountIdentifier, String scorecardIdentifier) {
    try {
      int size = dsl.delete(Tables.SCORECARDS_CHECKS)
                     .where(Tables.SCORECARDS_CHECKS.ACCOUNT_IDENTIFIER.eq(accountIdentifier))
                     .and(Tables.SCORECARDS_CHECKS.SCORECARD_IDENTIFIER.eq(scorecardIdentifier))
                     .execute();
      log.debug("Successfully deleted data in scorecards_checks size {} for accountId {} scorecardId {}", size,
          accountIdentifier, scorecardIdentifier);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in scorecards_checks for accountId {} scorecardId {}",
          accountIdentifier, scorecardIdentifier, e);
    }
  }

  private void deleteScorecardStats(String accountIdentifier, String scorecardIdentifier) {
    try {
      int size = dsl.delete(Tables.SCORECARD_STATS)
                     .where(Tables.SCORECARD_STATS.ACCOUNT_IDENTIFIER.eq(accountIdentifier))
                     .and(Tables.SCORECARD_STATS.SCORECARD_IDENTIFIER.eq(scorecardIdentifier))
                     .execute();
      log.debug("Successfully deleted data in scorecard_stats size {} for accountId {} scorecardId {}", size,
          accountIdentifier, scorecardIdentifier);
    } catch (DataAccessException e) {
      log.error("Caught exception while deleting data in scorecard_stats for accountId {} scorecardId {}",
          accountIdentifier, scorecardIdentifier, e);
    }
  }
}
