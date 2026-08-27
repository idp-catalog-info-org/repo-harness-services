/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.pms.plan.execution.PipelineExecutionSummaryKeys;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class PipelineExecutionSummaryChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @SneakyThrows
  public Record createRecord(String value, String id) {
    JsonNode node = objectMapper.readTree(value);
    JsonNode moduleInfo = node.get(PipelineExecutionSummaryKeys.moduleInfo);
    if (moduleInfo == null) {
      log.info("[CDC-Kafka-DB] Skipping id={}: moduleInfo is null in document — no TimescaleDB write", id);
      return null;
    }
    Record executionRecord = dsl.newRecord(Tables.PIPELINE_EXECUTION_SUMMARY);
    executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.ID, id);

    if (node.get(PipelineExecutionSummaryKeys.accountId) != null) {
      executionRecord.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.ACCOUNTID, node.get(PipelineExecutionSummaryKeys.accountId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.orgIdentifier) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.ORGIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.orgIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.projectIdentifier) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.PROJECTIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.projectIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.planExecutionId) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.PLANEXECUTIONID,
          node.get(PipelineExecutionSummaryKeys.planExecutionId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.pipelineIdentifier) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.PIPELINEIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.pipelineIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.name) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.NAME, node.get(PipelineExecutionSummaryKeys.name).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.status) != null) {
      executionRecord.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.STATUS, node.get(PipelineExecutionSummaryKeys.status).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.startTs) != null) {
      executionRecord.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS, node.get(PipelineExecutionSummaryKeys.startTs).asLong());
    }
    if (node.get(PipelineExecutionSummaryKeys.endTs) != null
        && !node.get(PipelineExecutionSummaryKeys.endTs).isNull()) {
      executionRecord.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS, node.get(PipelineExecutionSummaryKeys.endTs).asLong());
    }

    if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo) != null) {
      if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggerType)
          != null) {
        executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.TRIGGER_TYPE,
            node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                .get(PipelineExecutionSummaryKeys.triggerType)
                .asText());
      }
      if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggeredBy)
              != null
          && node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                  .get(PipelineExecutionSummaryKeys.triggeredBy)
                  .get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier)
              != null) {
        executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.AUTHOR_ID,
            node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                .get(PipelineExecutionSummaryKeys.triggeredBy)
                .get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier)
                .asText());
      }
    }
    if (node.get(PipelineExecutionSummaryKeys.moduleInfo).get("ci") != null) {
      JsonNode ciObject = node.get(PipelineExecutionSummaryKeys.moduleInfo).get("ci");
      JsonNode ciExecutionInfo = ciObject.get(PipelineExecutionSummaryKeys.ciExecutionInfoDTO);
      if (ciExecutionInfo != null) {
        JsonNode author = ciExecutionInfo.get(PipelineExecutionSummaryKeys.author);
        if (author != null) {
          if (author.get(PipelineExecutionSummaryKeys.commitId) != null) {
            executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.AUTHOR_ID,
                author.get(PipelineExecutionSummaryKeys.commitId).toString());
          }
          if (author.get(PipelineExecutionSummaryKeys.name) != null) {
            executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.AUTHOR_NAME,
                author.get(PipelineExecutionSummaryKeys.name).toString());
          }
          if (author.get(PipelineExecutionSummaryKeys.avatar) != null) {
            executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.AUTHOR_AVATAR,
                author.get(PipelineExecutionSummaryKeys.avatar).toString());
          }
        }
      }
    }
    if (node.get(PipelineExecutionSummaryKeys.parentUniqueId) != null) {
      executionRecord.set(Tables.PIPELINE_EXECUTION_SUMMARY.PARENT_UNIQUE_ID,
          node.get(PipelineExecutionSummaryKeys.parentUniqueId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.uniqueId) != null) {
      executionRecord.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.UNIQUE_ID, node.get(PipelineExecutionSummaryKeys.uniqueId).asText());
    }
    return executionRecord;
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      log.info("[CDC-Kafka-DB] Skipping CREATE for id={}: moduleInfo is null in document, no TimescaleDB write", id);
      return true;
    }
    try {
      int rows = dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY)
                     .set(record)
                     .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY.ID, Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS)
                     .doUpdate()
                     .set(record)
                     .execute();
      log.info("[CDC-Kafka-DB] CREATE upsert for id={}: rowsAffected={}", id, rows);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while inserting data for id={}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    log.info("[CDC-Kafka-DB] DELETE event received for id={}; executing TimescaleDB delete", id);
    try {
      int rows =
          dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY).where(Tables.PIPELINE_EXECUTION_SUMMARY.ID.eq(id)).execute();
      log.info("[CDC-Kafka-DB] DELETE executed for id={}: rowsAffected={}", id, rows);
    } catch (DataAccessException ex) {
      log.error("[CDC-Kafka-DB] Exception while deleting data for id={}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      log.info("[CDC-Kafka-DB] Skipping UPDATE for id={}: moduleInfo is null in document, no TimescaleDB write", id);
      return true;
    }
    try {
      int rows = dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY)
                     .set(record)
                     .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY.ID, Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS)
                     .doUpdate()
                     .set(record)
                     .execute();
      log.info("[CDC-Kafka-DB] UPDATE upsert for id={}: rowsAffected={}", id, rows);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data for id={}", id, ex);
      return false;
    }
    return true;
  }
}
