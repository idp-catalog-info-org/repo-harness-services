/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts endpoints from a Swagger 2.0 spec parsed as a raw JSON tree. Produces the same
 * {@link EndpointExtractor.ExtractionResult} shape as the OpenAPI 3.x extractor so the
 * downstream pipeline (ApiEndpointProcessor) is version-agnostic.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class Swagger2EndpointExtractor {
  private static final int MAX_ENDPOINTS = 5000;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch");

  public EndpointExtractor.ExtractionResult extract(String content) {
    if (content == null || content.isBlank()) {
      throw new OpenApiParseException("Spec content is empty.");
    }

    JsonNode root;
    try {
      root = parseContent(content);
    } catch (Exception ex) {
      throw new OpenApiParseException("Failed to parse Swagger 2.0 content: " + ex.getMessage(), ex);
    }

    if (root == null || !root.isObject()) {
      throw new OpenApiParseException("Content is not a valid Swagger 2.0 specification.");
    }

    List<String> warnings = new ArrayList<>();
    boolean degraded = false;

    String version = textValue(root, "swagger");
    String basePath = textValue(root, "basePath");
    if (basePath == null || basePath.isEmpty()) {
      basePath = "";
    } else {
      basePath = stripTrailingSlash(basePath);
    }

    Map<String, Object> apis = new LinkedHashMap<>();
    apis.put("protocol", "swagger");
    apis.put("version", version != null ? version : "2.0");

    List<Map<String, Object>> serversList = buildServers(root);
    apis.put("servers", serversList);

    Map<String, Map<String, Object>> paths = extractPaths(root, basePath, warnings);

    int totalCount = paths.size();
    boolean truncated = false;
    if (totalCount > MAX_ENDPOINTS) {
      truncated = true;
      Map<String, Map<String, Object>> kept = new TreeMap<>();
      int i = 0;
      for (Map.Entry<String, Map<String, Object>> e : paths.entrySet()) {
        if (i++ >= MAX_ENDPOINTS) {
          break;
        }
        kept.put(e.getKey(), e.getValue());
      }
      paths = kept;
      warnings.add(String.format(
          "Spec has %d endpoints; truncated to the first %d. Consider splitting the API into multiple entities.",
          totalCount, MAX_ENDPOINTS));
    }

    apis.put("paths", paths);
    apis.put("count", paths.size());
    if (truncated) {
      apis.put("truncated", true);
      apis.put("totalCount", totalCount);
    }

    return new EndpointExtractor.ExtractionResult(apis, warnings, degraded);
  }

  private static JsonNode parseContent(String content) throws Exception {
    String trimmed = content.trim();
    if (trimmed.startsWith("{")) {
      return JSON_MAPPER.readTree(trimmed);
    }
    return YAML_MAPPER.readTree(trimmed);
  }

  private static List<Map<String, Object>> buildServers(JsonNode root) {
    List<Map<String, Object>> out = new ArrayList<>();
    String host = textValue(root, "host");
    String basePath = textValue(root, "basePath");
    JsonNode schemes = root.get("schemes");

    if (host == null || host.isEmpty()) {
      Map<String, Object> defaultServer = new LinkedHashMap<>();
      defaultServer.put("url", basePath != null && !basePath.isEmpty() ? basePath : "/");
      out.add(defaultServer);
      return out;
    }

    String scheme = "https";
    if (schemes != null && schemes.isArray() && schemes.size() > 0) {
      String first = schemes.get(0).asText();
      if (first != null && !first.isEmpty()) {
        scheme = first;
      }
    }

    String url = scheme + "://" + host + (basePath != null ? basePath : "");
    Map<String, Object> server = new LinkedHashMap<>();
    server.put("url", url);
    out.add(server);
    return out;
  }

  private static Map<String, Map<String, Object>> extractPaths(JsonNode root, String basePath, List<String> warnings) {
    Map<String, Map<String, Object>> out = new TreeMap<>();
    JsonNode pathsNode = root.get("paths");
    if (pathsNode == null || !pathsNode.isObject()) {
      return out;
    }

    Iterator<Map.Entry<String, JsonNode>> pathIter = pathsNode.fields();
    while (pathIter.hasNext()) {
      Map.Entry<String, JsonNode> pathEntry = pathIter.next();
      String path = pathEntry.getKey();
      JsonNode pathItem = pathEntry.getValue();
      if (pathItem == null || !pathItem.isObject()) {
        continue;
      }

      for (String method : HTTP_METHODS) {
        JsonNode operation = pathItem.get(method);
        if (operation == null || !operation.isObject()) {
          continue;
        }

        String key = method.toUpperCase() + " " + basePath + (path.startsWith("/") ? path : "/" + path);
        if (out.containsKey(key)) {
          warnings.add("Duplicate endpoint key encountered (skipped second occurrence): " + key);
          continue;
        }
        out.put(key, buildEndpoint(operation, method, path));
      }
    }
    return out;
  }

  private static Map<String, Object> buildEndpoint(JsonNode operation, String method, String path) {
    Map<String, Object> endpoint = new LinkedHashMap<>();
    endpoint.put("path", path);
    endpoint.put("method", method.toUpperCase());

    String summary = textValue(operation, "summary");
    if (summary != null && !summary.isBlank()) {
      endpoint.put("summary", summary);
    }

    String description = textValue(operation, "description");
    if (description != null && !description.isBlank()) {
      endpoint.put("description", description);
    }

    String operationId = textValue(operation, "operationId");
    if (operationId != null && !operationId.isBlank()) {
      endpoint.put("operationId", operationId);
    }

    JsonNode tags = operation.get("tags");
    if (tags != null && tags.isArray() && tags.size() > 0) {
      List<String> tagList = new ArrayList<>();
      for (JsonNode tag : tags) {
        if (tag.isTextual()) {
          tagList.add(tag.asText());
        }
      }
      if (!tagList.isEmpty()) {
        endpoint.put("tags", tagList);
      }
    }

    JsonNode deprecated = operation.get("deprecated");
    if (deprecated != null && deprecated.asBoolean(false)) {
      endpoint.put("deprecated", true);
    }

    endpoint.put("enrichments", new LinkedHashMap<String, Object>());
    return endpoint;
  }

  private static String stripTrailingSlash(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    if (s.equals("/")) {
      return "";
    }
    if (s.endsWith("/")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  private static String textValue(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || !child.isTextual()) {
      return null;
    }
    return child.asText();
  }
}
