/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration.ootb;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionBuiltinConfig;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.idp.catalog.repositories.ActionRepository;
import io.harness.migration.beans.NGMigration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

/**
 * Base class for OOTB Action seed migrations. Each concrete subclass declares the resource path
 * of a single seed JSON under {@code seeds/ootb-actions/}. The migration framework handles
 * sequencing, locking, and version-tracking via {@link IdpOotbMigrationSchema}, so this class
 * only needs to load the JSON, parse it into an {@link Action}, and insert it under the global
 * scope if it does not already exist.
 */
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public abstract class AbstractOotbActionSeedMigration implements NGMigration {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SEED_FOLDER = "seeds/ootb-actions/";

  @Inject private ActionRepository actionRepository;

  protected abstract String getSeedFileName();

  @Override
  public void migrate() {
    String filename = getSeedFileName();
    String resourcePath = SEED_FOLDER + filename;
    log.info("[OOTB-Seed] Applying {}", resourcePath);

    String json;
    try {
      json = Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UnexpectedException("Failed to load OOTB seed resource " + resourcePath, ex);
    }

    Action action;
    try {
      action = parseAction(json, filename);
    } catch (IOException ex) {
      throw new UnexpectedException("Failed to parse OOTB seed JSON " + resourcePath, ex);
    }

    Optional<Action> existing = actionRepository.findByParentUniqueIdAndIdentifierAndVersion(
        action.getParentUniqueId(), action.getIdentifier(), action.getVersion());
    if (existing.isPresent()) {
      log.info("[OOTB-Seed] Action [{}] version [{}] already present; nothing to seed", action.getIdentifier(),
          action.getVersion());
      return;
    }

    try {
      actionRepository.save(action);
      log.info("[OOTB-Seed] Seeded action [{}] version [{}]", action.getIdentifier(), action.getVersion());
    } catch (DuplicateKeyException dupe) {
      // Another writer raced us under the global migration lock should be impossible, but the
      // unique index on (parentUniqueId, identifier, version) is the source of truth either way.
      log.info("[OOTB-Seed] Action [{}] version [{}] inserted concurrently; treating as applied",
          action.getIdentifier(), action.getVersion());
    }
  }

  private Action parseAction(String json, String filename) throws IOException {
    JsonNode node = MAPPER.readTree(json);
    String identifier = requireText(node, "identifier", filename);
    String name = requireText(node, "name", filename);
    String description = node.hasNonNull("description") ? node.get("description").asText() : null;
    String version = requireText(node, "version", filename);
    String handler = requireText(node, "handler", filename);
    String category = node.hasNonNull("category") ? node.get("category").asText() : null;

    JsonNode schemaNode = node.get("inputSchema");
    Map<String, Object> inputSchema = null;
    if (schemaNode != null && !schemaNode.isNull()) {
      inputSchema = MAPPER.convertValue(schemaNode, new TypeReference<Map<String, Object>>() {});
    }

    return Action.builder()
        .identifier(identifier)
        .name(name)
        .description(description)
        .version(version)
        .status(ActionStatus.PUBLISHED)
        .type(ActionType.BUILTIN)
        .builtinConfig(ActionBuiltinConfig.builder().handler(handler).build())
        .inputSchema(inputSchema)
        .category(category)
        .accountIdentifier(Action.GLOBAL_ACCOUNT_IDENTIFIER)
        .parentUniqueId(Action.GLOBAL_PARENT_UNIQUE_ID)
        .build();
  }

  private static String requireText(JsonNode node, String field, String filename) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isEmpty()) {
      throw new IllegalStateException(String.format("Seed file %s is missing required field '%s'", filename, field));
    }
    return value.asText();
  }
}
