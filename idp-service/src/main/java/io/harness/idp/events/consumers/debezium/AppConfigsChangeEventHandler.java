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
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class AppConfigsChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in plugins for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in plugins for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.PLUGINS).where(Tables.PLUGINS.ID.eq(id)).execute();
      log.debug("Successfully deleted data in plugins for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in plugins for id {}", id, ex);
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
      log.debug("Successfully updated data in plugins for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in plugins for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    if (node.get("configType") != null && !node.get("configType").asText().equals("PLUGIN")) {
      return null;
    }

    Record createRecord = dsl.newRecord(Tables.PLUGINS);
    createRecord.set(Tables.PLUGINS.ID, id);

    populateFromRoot(node, createRecord);

    return createRecord;
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.PLUGINS.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("configId") != null) {
      String pluginIdentifier = node.get("configId").asText();
      createRecord.set(Tables.PLUGINS.IDENTIFIER, pluginIdentifier);

      if (node.get("configName") != null) {
        String pluginName = node.get("configName").asText();
        createRecord.set(Tables.PLUGINS.NAME, StringUtils.isNotEmpty(pluginName) ? pluginName : pluginIdentifier);
      } else {
        createRecord.set(Tables.PLUGINS.NAME, pluginIdentifier);
      }
    }

    if (node.get("enabled") != null) {
      createRecord.set(Tables.PLUGINS.ENABLED, node.get("enabled").asBoolean());
    }

    if (node.get("createdAt") != null) {
      createRecord.set(Tables.PLUGINS.CREATED_AT, node.get("createdAt").asLong());
    }

    if (node.get("lastModifiedAt") != null) {
      createRecord.set(Tables.PLUGINS.LAST_UPDATED_AT, node.get("lastModifiedAt").asLong());
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.PLUGINS)
        .set(upsertRecord)
        .onConflict(Tables.PLUGINS.ID)
        .doUpdate()
        .set(upsertRecord)
        .execute();
  }
}
