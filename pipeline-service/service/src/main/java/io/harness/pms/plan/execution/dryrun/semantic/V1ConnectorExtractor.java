/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Extracts connector references that appear directly in resolved V1 pipeline YAML, synthesizing the
 * CONNECTORS {@link EntityDetailProtoDTO}s that V1 filter creation never emits. The output has the
 * same shape V0 filter creation produces, so the validator's existing connector fetch and Rule 1
 * consume it unchanged.
 *
 * <p>Slots (all appear directly in the pipeline YAML; template inputs/bodies are out of scope):
 * fields named {@code connector} (container / clone / runtime.* / options.repository) and
 * {@code registryRef}; the k8s/vm {@code harness-image-connector} field; and
 * {@code options.registry.credentials[].name} (its sibling {@code match} is an image glob, skipped).
 * Runtime expressions ({@code <+...>}) and blanks are skipped.
 */
@UtilityClass
@OwnedBy(PIPELINE)
public class V1ConnectorExtractor {
  private static final String FIELD_CONNECTOR = "connector";
  private static final String FIELD_REGISTRY_REF = "registryRef";
  private static final String FIELD_HARNESS_IMAGE_CONNECTOR = "harness-image-connector";
  private static final String FIELD_OPTIONS = "options";
  private static final String FIELD_REGISTRY = "registry";
  private static final String FIELD_CREDENTIALS = "credentials";
  private static final String FIELD_NAME = "name";

  public List<EntityDetailProtoDTO> extractReferredConnectors(
      JsonNode root, String account, String org, String project) {
    Set<String> rawRefs = new LinkedHashSet<>();
    collect(root, rawRefs);

    List<EntityDetailProtoDTO> entities = new ArrayList<>();
    Set<String> seenScoped = new LinkedHashSet<>();
    for (String rawRef : rawRefs) {
      IdentifierRef ref;
      try {
        ref = IdentifierRefHelper.getIdentifierRef(rawRef, account, org, project);
      } catch (InvalidIdentifierRefException ex) {
        continue;
      }
      if (ref == null) {
        continue;
      }
      String scoped = ref.buildScopedIdentifier();
      if (scoped == null || scoped.isBlank() || !seenScoped.add(scoped)) {
        continue;
      }
      entities.add(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(ref))
                       .setType(EntityTypeProtoEnum.CONNECTORS)
                       .build());
    }
    return entities;
  }

  private void collect(JsonNode node, Set<String> rawRefs) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      addIfUsable(node.get(FIELD_CONNECTOR), rawRefs);
      addIfUsable(node.get(FIELD_REGISTRY_REF), rawRefs);
      addIfUsable(node.get(FIELD_HARNESS_IMAGE_CONNECTOR), rawRefs);
      collectRegistryCredentials(node, rawRefs);
      node.fields().forEachRemaining(entry -> collect(entry.getValue(), rawRefs));
    } else if (node.isArray()) {
      node.forEach(child -> collect(child, rawRefs));
    }
  }

  /** {@code options.registry.credentials[].name} holds the connector id; {@code match} is a glob. */
  private void collectRegistryCredentials(JsonNode node, Set<String> rawRefs) {
    JsonNode credentials = at(node, FIELD_OPTIONS, FIELD_REGISTRY, FIELD_CREDENTIALS);
    if (credentials != null && credentials.isArray()) {
      for (JsonNode credential : credentials) {
        addIfUsable(credential.get(FIELD_NAME), rawRefs);
      }
    }
  }

  private void addIfUsable(JsonNode value, Set<String> rawRefs) {
    if (value == null || value.isNull()) {
      return;
    }
    String text = value.asText();
    if (text == null || text.isBlank()) {
      return;
    }
    text = text.trim();
    if (text.startsWith(SemanticConstants.RUNTIME_EXPRESSION_PREFIX)) {
      return;
    }
    rawRefs.add(text);
  }

  private JsonNode at(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      if (current == null) {
        return null;
      }
      current = current.get(segment);
    }
    return current;
  }
}
