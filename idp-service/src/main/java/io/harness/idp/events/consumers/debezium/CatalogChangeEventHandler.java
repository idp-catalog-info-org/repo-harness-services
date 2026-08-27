/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
public class CatalogChangeEventHandler extends DebeziumAbstractRedisEventHandler {
  @Inject private DSLContext dsl;

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record insertRecord = createRecord(id, value);
    if (insertRecord == null) {
      return true;
    }
    try {
      upsert(insertRecord);
      log.debug("Successfully inserted data in CATALOG for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while inserting data in CATALOG for id {}", id, ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.CATALOG).where(Tables.CATALOG.ID.eq(id)).execute();
      log.debug("Successfully deleted data in CATALOG for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught exception while deleting data in CATALOG for id {}", id, ex);
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
      log.debug("Successfully updated data in CATALOG for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data in CATALOG for id {}", id, ex);
      return false;
    }
    return true;
  }

  @SneakyThrows
  public Record createRecord(String id, String value) {
    JsonNode node = objectMapper.readTree(value);

    Record createRecord = dsl.newRecord(Tables.CATALOG);
    createRecord.set(Tables.CATALOG.ID, id);

    populateFromRoot(node, createRecord);

    return createRecord;
  }

  private void populateFromRoot(JsonNode node, Record createRecord) {
    if (node.get("accountIdentifier") != null) {
      createRecord.set(Tables.CATALOG.ACCOUNT_IDENTIFIER, node.get("accountIdentifier").asText());
    }

    if (node.get("orgIdentifier") != null) {
      createRecord.set(Tables.CATALOG.ORG_IDENTIFIER, node.get("orgIdentifier").asText());
    }

    if (node.get("projectIdentifier") != null) {
      createRecord.set(Tables.CATALOG.PROJECT_IDENTIFIER, node.get("projectIdentifier").asText());
    }

    if (node.get("identifier") != null) {
      createRecord.set(Tables.CATALOG.IDENTIFIER, node.get("identifier").asText());
    }

    if (node.get("kind") != null) {
      createRecord.set(Tables.CATALOG.KIND, node.get("kind").asText());
    }

    // create and set entity ref in `kind:{scope}/{identifier}` format
    if (node.get("kind") != null && node.get("identifier") != null) {
      StringBuilder scopeBuilder = new StringBuilder();
      scopeBuilder.append("account");
      if (node.get("orgIdentifier") != null) {
        scopeBuilder.append(".").append(node.get("orgIdentifier").asText());
      }
      if (node.get("projectIdentifier") != null) {
        scopeBuilder.append(".").append(node.get("projectIdentifier").asText());
      }

      String scope = scopeBuilder.toString();
      String entityRef = node.get("kind").asText() + ":" + scope + "/" + node.get("identifier").asText();
      createRecord.set(Tables.CATALOG.ENTITY_REF, entityRef);
    }

    if (node.get("name") != null) {
      createRecord.set(Tables.CATALOG.NAME, node.get("name").asText());
    }

    if (node.get("relations") != null) {
      JsonNode nodeRelations = node.get("relations");
      List<JsonNode> relations = StreamSupport.stream(nodeRelations.spliterator(), false).collect(Collectors.toList());
      createRecord.set(Tables.CATALOG.NUMBER_OF_RELATIONS, (short) relations.size());
    }

    if (node.get("tags") != null) {
      JsonNode nodeTags = node.get("tags");
      String[] tags = StreamSupport.stream(nodeTags.spliterator(), false).map(JsonNode::asText).toArray(String[] ::new);
      createRecord.set(Tables.CATALOG.TAGS, tags);
    }

    if (node.get("type") != null) {
      createRecord.set(Tables.CATALOG.TYPE, node.get("type").asText());
    }
    if (node.get("owner") != null) {
      createRecord.set(Tables.CATALOG.OWNER, node.get("owner").asText());
    }

    if (node.get("createdAt") != null) {
      createRecord.set(Tables.CATALOG.CREATED_AT, node.get("createdAt").asLong());
    }

    if (node.get("lastUpdatedAt") != null) {
      createRecord.set(Tables.CATALOG.LAST_UPDATED_AT, node.get("lastUpdatedAt").asLong());
    }
  }

  private void upsert(Record upsertRecord) {
    dsl.insertInto(Tables.CATALOG)
        .set(upsertRecord)
        .onConflict(Tables.CATALOG.ID)
        .doUpdate()
        .set(upsertRecord)
        .execute();
  }
}
