/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves {@code spec.definition} into raw spec content the parser can consume. Handles four
 * shapes: plain URL string (fetched), inline string content, inline YAML object, and
 * {@code $yaml/$json/$text} placeholder whose resolved content is read from the decorator
 * (placed there by {@code PlaceholderProcessor} — we don't re-fetch).
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class SpecSourceResolver {
  private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
  private static final String YAML_PLACEHOLDER = "$yaml";
  private static final String JSON_PLACEHOLDER = "$json";
  private static final String TEXT_PLACEHOLDER = "$text";
  private static final String SPEC_KEY = "spec";
  private static final String DEFINITION_KEY = "definition";

  private final SpecFetcher specFetcher;

  @Inject
  public SpecSourceResolver(SpecFetcher specFetcher) {
    this.specFetcher = specFetcher;
  }

  public String resolve(CatalogEntity entity) {
    Objects.requireNonNull(entity, "entity must not be null");

    Map<String, Object> spec = entity.getSpec();
    if (spec == null || !spec.containsKey(DEFINITION_KEY)) {
      throw new SpecResolutionException("API entity has no spec.definition. Add an OpenAPI spec source.");
    }

    Object definition = spec.get(DEFINITION_KEY);

    if (definition instanceof String) {
      return resolveString((String) definition);
    }
    if (definition instanceof Map) {
      return resolveMap((Map<String, Object>) definition, entity);
    }
    throw new SpecResolutionException(
        "spec.definition must be either a string (URL or inline YAML/JSON) or an object (inline "
        + "YAML or a $yaml/$json/$text placeholder). Got: "
        + (definition == null ? "null" : definition.getClass().getSimpleName()));
  }

  private String resolveString(String definition) {
    String trimmed = definition.trim();
    if (trimmed.isEmpty()) {
      throw new SpecResolutionException("spec.definition is empty.");
    }
    if (ABSOLUTE_URL.matcher(trimmed).matches()) {
      return specFetcher.fetch(trimmed);
    }
    return trimmed;
  }

  private String resolveMap(Map<String, Object> definition, CatalogEntity entity) {
    String placeholderKey = findPlaceholderKey(definition);
    if (placeholderKey == null) {
      return writeObjectAsYaml(definition); // inline YAML object
    }

    String resolved = readResolvedFromDecorator(entity, placeholderKey);
    if (resolved == null || resolved.isBlank()) {
      throw new SpecResolutionException("Resolved content for spec.definition " + placeholderKey
          + " is not available on the "
          + "entity. If the reference is Git-hosted, re-saving the entity should populate it and "
          + "retry the extraction; otherwise use a plain URL string in spec.definition instead.");
    }
    return resolved.trim();
  }

  private static String findPlaceholderKey(Map<String, Object> definition) {
    if (definition.containsKey(YAML_PLACEHOLDER)) {
      return YAML_PLACEHOLDER;
    }
    if (definition.containsKey(JSON_PLACEHOLDER)) {
      return JSON_PLACEHOLDER;
    }
    if (definition.containsKey(TEXT_PLACEHOLDER)) {
      return TEXT_PLACEHOLDER;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static String readResolvedFromDecorator(CatalogEntity entity, String placeholderKey) {
    Map<String, Object> decorator = entity.getDecorator();
    if (decorator == null) {
      return null;
    }
    Object spec = decorator.get(SPEC_KEY);
    if (!(spec instanceof Map)) {
      return null;
    }
    Object definition = ((Map<String, Object>) spec).get(DEFINITION_KEY);
    if (!(definition instanceof Map)) {
      return null;
    }
    Object value = ((Map<String, Object>) definition).get(placeholderKey);
    if (value instanceof String) {
      return (String) value;
    }
    // Defensive: if the resolved content was stored as an object, serialise it to YAML.
    return value == null ? null : writeObjectAsYaml(value);
  }
}
