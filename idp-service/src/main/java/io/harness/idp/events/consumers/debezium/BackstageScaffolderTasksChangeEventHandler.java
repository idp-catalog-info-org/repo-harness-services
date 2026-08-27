/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class BackstageScaffolderTasksChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in backstage_scaffolder_tasks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in backstage_scaffolder_tasks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.BACKSTAGE_SCAFFOLDER_TASKS).where(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ID.eq(id)).execute();
      log.debug("Successfully deleted data in backstage_scaffolder_tasks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in backstage_scaffolder_tasks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Record updateRecord = createRecord(id, value);
    if (updateRecord == null) {
      return true;
    }
    try {
      upsert(updateRecord);
      log.debug("Successfully updated data in backstage_scaffolder_tasks for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in backstage_scaffolder_tasks for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    Record createRecord = dsl.newRecord(Tables.BACKSTAGE_SCAFFOLDER_TASKS);
    createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ID, id);

    populateFromRoot(node, createRecord);
    populateFromSpec(node, createRecord);

    return createRecord;
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("identifier") != null) {
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.IDENTIFIER, node.get("identifier").asText());
    }

    if (node.get("status") != null) {
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.STATUS, node.get("status").asText());
    }

    String taskCreatedBy;
    if (node.get("taskCreatedBy") != null) {
      taskCreatedBy = node.get("taskCreatedBy").asText();
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.CREATED_BY, taskCreatedBy);
    }

    long createdAt = 0;
    if (node.get("taskCreatedAt") != null) {
      createdAt = node.get("taskCreatedAt").asLong();
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.CREATED_AT, createdAt);
    }

    long lastHeartbeatAt = 0;
    if (node.get("lastHeartbeatAt") != null) {
      lastHeartbeatAt = node.get("lastHeartbeatAt").asLong();
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.LAST_HEARTBEAT_AT, lastHeartbeatAt);
    }

    createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.TASK_RUN_TIME_MINUTES,
        lastHeartbeatAt > 0 ? (short) ((lastHeartbeatAt - createdAt) / 60000) : 0);
  }

  @SneakyThrows
  private void populateFromSpec(JsonNode node, Record createRecord) {
    if (node.get("spec") != null) {
      JsonNode spec = objectMapper.readTree(node.get("spec").asText());
      populateFromSpecTemplateInfo(spec, createRecord);
      populateFromSpecSteps(spec, createRecord);
    }
  }

  private void populateFromSpecTemplateInfo(JsonNode spec, Record createRecord) {
    if (spec.get("templateInfo") != null) {
      JsonNode templateInfo = spec.get("templateInfo");
      createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ENTITY_REF, templateInfo.get("entityRef").asText());
      if (templateInfo.get("entity") != null && templateInfo.get("entity").get("metadata") != null) {
        JsonNode metadata = templateInfo.get("entity").get("metadata");
        if (metadata.get("name") != null) {
          createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.NAME, metadata.get("name").asText());
        } else {
          createRecord.set(
              Tables.BACKSTAGE_SCAFFOLDER_TASKS.NAME, templateInfo.get("entityRef").asText().split("/")[1]);
        }

        if (metadata.get("tags") != null && metadata.get("tags").isArray()) {
          JsonNode nodeTags = metadata.get("tags");
          String[] tags =
              StreamSupport.stream(nodeTags.spliterator(), false).map(JsonNode::asText).toArray(String[] ::new);
          createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.TAGS, tags);
        }

      } else {
        createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.NAME, templateInfo.get("entityRef").asText().split("/")[1]);
      }
    }
  }

  private void populateFromSpecSteps(JsonNode spec, Record createRecord) {
    if (spec.get("steps") != null) {
      JsonNode specSteps = spec.get("steps");
      if (specSteps.isArray()) {
        List<JsonNode> steps = StreamSupport.stream(specSteps.spliterator(), false).collect(Collectors.toList());
        createRecord.set(Tables.BACKSTAGE_SCAFFOLDER_TASKS.NUMBER_OF_STEPS, (short) steps.size());
      }
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.BACKSTAGE_SCAFFOLDER_TASKS)
        .set(upsertRecord)
        .onConflict(Tables.BACKSTAGE_SCAFFOLDER_TASKS.ID)
        .doUpdate()
        .set(upsertRecord)
        .execute();
  }
}
