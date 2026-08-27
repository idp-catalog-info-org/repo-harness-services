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
public class BackstageCatalogChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in backstage_catalog for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in backstage_catalog for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.BACKSTAGE_CATALOG).where(Tables.BACKSTAGE_CATALOG.ID.eq(id)).execute();
      log.debug("Successfully deleted data in backstage_catalog for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in backstage_catalog for id {}", id, ex);
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
      log.debug("Successfully updated data in backstage_catalog for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in backstage_catalog for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    Record createRecord = dsl.newRecord(Tables.BACKSTAGE_CATALOG);
    createRecord.set(Tables.BACKSTAGE_CATALOG.ID, id);

    populateFromRoot(node, createRecord);
    populateFromMetadata(node, createRecord);
    populateFromSpec(node, createRecord);

    return createRecord;
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.BACKSTAGE_CATALOG.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("entityUid") != null) {
      createRecord.set(Tables.BACKSTAGE_CATALOG.IDENTIFIER, node.get("entityUid").asText());
    }

    if (node.get("kind") != null) {
      createRecord.set(Tables.BACKSTAGE_CATALOG.KIND, node.get("kind").asText());
    }

    if (node.get("relations") != null) {
      JsonNode nodeRelations = node.get("relations");
      List<JsonNode> relations = StreamSupport.stream(nodeRelations.spliterator(), false).collect(Collectors.toList());
      createRecord.set(Tables.BACKSTAGE_CATALOG.NUMBER_OF_RELATIONS, (short) relations.size());
    }

    if (node.get("createdAt") != null) {
      createRecord.set(Tables.BACKSTAGE_CATALOG.CREATED_AT, node.get("createdAt").asLong());
    }

    if (node.get("lastUpdatedAt") != null) {
      createRecord.set(Tables.BACKSTAGE_CATALOG.LAST_UPDATED_AT, node.get("lastUpdatedAt").asLong());
    }
  }

  @SneakyThrows
  private void populateFromMetadata(JsonNode node, Record createRecord) {
    if (node.get("metadata") != null) {
      JsonNode metadata = node.get("metadata");
      if (metadata.get("name") != null) {
        createRecord.set(Tables.BACKSTAGE_CATALOG.NAME, metadata.get("name").asText());
      }
    }
  }

  @SneakyThrows
  private void populateFromSpec(JsonNode node, Record createRecord) {
    if (node.get("spec") != null) {
      JsonNode spec = node.get("spec");
      if (spec.get("type") != null) {
        createRecord.set(Tables.BACKSTAGE_CATALOG.TYPE, spec.get("type").asText());
      }
      if (spec.get("owner") != null) {
        createRecord.set(Tables.BACKSTAGE_CATALOG.OWNER, spec.get("owner").asText());
      }
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.BACKSTAGE_CATALOG)
        .set(upsertRecord)
        .onConflict(Tables.BACKSTAGE_CATALOG.ID)
        .doUpdate()
        .set(upsertRecord)
        .execute();
  }
}
