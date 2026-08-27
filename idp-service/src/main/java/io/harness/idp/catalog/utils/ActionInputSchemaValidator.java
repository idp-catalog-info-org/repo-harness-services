/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ActionInputSchemaValidator {
  // ObjectMapper is thread-safe after construction; shared static instance is intentional.
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> VALID_PROPERTY_TYPES =
      Set.of("string", "number", "integer", "boolean", "array", "object");

  public static void validate(Map<String, Object> inputSchema) {
    if (inputSchema == null) {
      return;
    }
    JsonNode root = MAPPER.valueToTree(inputSchema);
    validateRootType(root);
    JsonNode properties = root.path("properties");
    validateProperties(properties);
    validateRequired(root.path("required"), properties);
  }

  private static void validateRootType(JsonNode root) {
    if (!"object".equals(root.path("type").asText(null))) {
      throw new InvalidRequestException("inputSchema root 'type' must be 'object'");
    }
  }

  private static void validateProperties(JsonNode properties) {
    if (properties.isMissingNode() || properties.isNull()) {
      return;
    }
    if (!properties.isObject()) {
      throw new InvalidRequestException("inputSchema 'properties' must be an object");
    }
    for (Map.Entry<String, JsonNode> entry : properties.properties()) {
      validateProperty(entry.getKey(), entry.getValue());
    }
  }

  private static void validateProperty(String name, JsonNode propDef) {
    if (!propDef.isObject()) {
      throw new InvalidRequestException(String.format("inputSchema property '%s' must be an object", name));
    }
    String type = propDef.path("type").asText(null);
    if (type == null || !VALID_PROPERTY_TYPES.contains(type)) {
      throw new InvalidRequestException(String.format(
          "inputSchema property '%s' has invalid type '%s'. Must be one of: %s", name, type, VALID_PROPERTY_TYPES));
    }
    JsonNode binding = propDef.path("binding");
    if (!binding.isMissingNode() && !binding.isNull()) {
      validateBinding(name, binding);
    }
  }

  private static void validateBinding(String propName, JsonNode binding) {
    if (!binding.isObject()) {
      throw new InvalidRequestException(String.format("inputSchema property '%s' binding must be an object", propName));
    }
    if (isEmpty(binding.path("source").asText(null))) {
      throw new InvalidRequestException(
          String.format("inputSchema property '%s' binding must have a non-empty 'source' string", propName));
    }
    if (isEmpty(binding.path("key").asText(null))) {
      throw new InvalidRequestException(
          String.format("inputSchema property '%s' binding must have a non-empty 'key' string", propName));
    }
  }

  private static void validateRequired(JsonNode required, JsonNode properties) {
    if (required.isMissingNode() || required.isNull()) {
      return;
    }
    if (!required.isArray()) {
      throw new InvalidRequestException("inputSchema 'required' must be an array of strings");
    }
    for (JsonNode req : required) {
      if (!req.isTextual()) {
        throw new InvalidRequestException("inputSchema 'required' entries must be strings");
      }
      String key = req.asText();
      if (properties.isMissingNode() || !properties.has(key)) {
        throw new InvalidRequestException(
            String.format("inputSchema 'required' entry '%s' is not defined in 'properties'", key));
      }
    }
  }
}
