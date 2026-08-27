/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts endpoints from a parsed OpenAPI model into the {@code metadata.apis} shape.
 * Keys are {@code "<METHOD> <basePath><openApiPath>"} — a stable contract with the protection gate.
 * Sorted output for idempotent re-runs. Asymmetric basePaths emit one entry per (basePath, path,
 * method). Server template vars use declared defaults; unresolved ones mark the result degraded.
 * Only spec-level {@code servers} are honoured (no path/operation overrides).
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class EndpointExtractor {
  private static final int MAX_ENDPOINTS = 5000;
  private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{([^{}]+)\\}");

  /** {@code degraded} = spec could not be fully extracted (e.g. unresolved server template var). */
  public static final class ExtractionResult {
    private final Map<String, Object> apis;
    private final List<String> warnings;
    private final boolean degraded;

    ExtractionResult(Map<String, Object> apis, List<String> warnings, boolean degraded) {
      this.apis = apis;
      this.warnings = warnings;
      this.degraded = degraded;
    }

    public Map<String, Object> getApis() {
      return apis;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    public boolean isDegraded() {
      return degraded;
    }
  }

  public ExtractionResult extract(OpenAPI spec) {
    if (spec == null) {
      throw new IllegalArgumentException("OpenAPI spec must not be null");
    }

    List<String> warnings = new ArrayList<>();
    boolean[] degradedHolder = new boolean[] {false};

    Map<String, Object> apis = new LinkedHashMap<>();
    apis.put("protocol", "openapi");
    apis.put("version", spec.getOpenapi() == null ? "unknown" : spec.getOpenapi());

    List<Map<String, Object>> serversList = buildServers(spec.getServers());
    apis.put("servers", serversList);

    List<String> resolvedBasePaths = computeResolvedBasePaths(spec.getServers(), warnings, degradedHolder);
    boolean asymmetric = resolvedBasePaths.stream().distinct().count() > 1;

    Map<String, Map<String, Object>> paths = extractPaths(spec, resolvedBasePaths, asymmetric, warnings);

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
      warnings.add(String.format("Spec has %d endpoints; truncated to the first %d. Consider splitting the API into "
              + "multiple entities.",
          totalCount, MAX_ENDPOINTS));
      // Benign — surviving endpoints are fully extracted, does NOT set degraded.
    }

    apis.put("paths", paths);
    apis.put("count", paths.size());
    if (truncated) {
      apis.put("truncated", true);
      apis.put("totalCount", totalCount);
    }

    return new ExtractionResult(apis, warnings, degradedHolder[0]);
  }

  private static List<Map<String, Object>> buildServers(List<Server> servers) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (servers == null || servers.isEmpty()) {
      // OpenAPI 3.x default is "/" — represent explicitly so consumers don't see an empty list.
      Map<String, Object> defaultServer = new LinkedHashMap<>();
      defaultServer.put("url", "/");
      out.add(defaultServer);
      return out;
    }
    for (Server server : servers) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("url", server.getUrl() == null ? "/" : server.getUrl());
      if (server.getDescription() != null && !server.getDescription().isBlank()) {
        map.put("description", server.getDescription());
      }
      out.add(map);
    }
    return out;
  }

  private static List<String> computeResolvedBasePaths(
      List<Server> servers, List<String> warnings, boolean[] degradedHolder) {
    List<String> out = new ArrayList<>();
    if (servers == null || servers.isEmpty()) {
      out.add("");
      return out;
    }
    for (Server server : servers) {
      out.add(resolveBasePath(server, warnings, degradedHolder));
    }
    return out;
  }

  private static String resolveBasePath(Server server, List<String> warnings, boolean[] degradedHolder) {
    String url = server.getUrl();
    if (url == null || url.isBlank()) {
      return "";
    }

    String resolved = substituteServerVariables(url, server.getVariables(), warnings, degradedHolder);

    try {
      URI uri = new URI(resolved);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        return "";
      }
      return stripTrailingSlash(path);
    } catch (URISyntaxException ex) {
      // Relative URL like "/v1" is valid in OpenAPI 3.x.
      if (resolved.startsWith("/")) {
        return stripTrailingSlash(resolved);
      }
      warnings.add("Server URL could not be parsed: " + url);
      return "";
    }
  }

  /**
   * Canonicalises basePath: empty and {@code "/"} both collapse to {@code ""}. Load-bearing for
   * asymmetric-basePath detection — otherwise {@code https://api.com} and {@code .../} look
   * distinct, duplicating each endpoint under a malformed {@code "GET //users"} key.
   */
  static String stripTrailingSlash(String s) {
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

  private static String substituteServerVariables(
      String url, ServerVariables variables, List<String> warnings, boolean[] degradedHolder) {
    Matcher m = TEMPLATE_VARIABLE.matcher(url);
    if (!m.find()) {
      return url;
    }
    m.reset();
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String var = m.group(1);
      String replacement = null;
      if (variables != null) {
        ServerVariable sv = variables.get(var);
        if (sv != null && sv.getDefault() != null && !sv.getDefault().isBlank()) {
          replacement = sv.getDefault();
        }
      }
      if (replacement == null) {
        warnings.add(String.format(
            "Server URL template variable {%s} has no default value; using the literal placeholder.", var));
        degradedHolder[0] = true;
        replacement = "{" + var + "}";
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private Map<String, Map<String, Object>> extractPaths(
      OpenAPI spec, List<String> resolvedBasePaths, boolean asymmetric, List<String> warnings) {
    Map<String, Map<String, Object>> out = new TreeMap<>();

    if (spec.getPaths() == null || spec.getPaths().isEmpty()) {
      return out;
    }

    List<String> basePathsToEmit;
    if (asymmetric) {
      basePathsToEmit = resolvedBasePaths;
      warnings.add("Servers have asymmetric basePaths; emitting one endpoint entry per server "
          + "basePath. Consider registering separate entities per environment.");
    } else if (resolvedBasePaths.isEmpty()) {
      basePathsToEmit = List.of("");
    } else {
      basePathsToEmit = List.of(resolvedBasePaths.get(0));
    }

    for (Map.Entry<String, PathItem> pathEntry : spec.getPaths().entrySet()) {
      String openApiPath = pathEntry.getKey();
      PathItem pathItem = pathEntry.getValue();
      if (pathItem == null || openApiPath == null) {
        continue;
      }
      Map<PathItem.HttpMethod, Operation> ops = pathItem.readOperationsMap();
      if (ops == null || ops.isEmpty()) {
        continue;
      }
      for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : ops.entrySet()) {
        PathItem.HttpMethod method = opEntry.getKey();
        Operation operation = opEntry.getValue();
        if (method == null || operation == null) {
          continue;
        }
        for (String basePath : basePathsToEmit) {
          String key = buildKey(method, basePath, openApiPath);
          if (out.containsKey(key)) {
            warnings.add("Duplicate endpoint key encountered (skipped second occurrence): " + key);
            continue;
          }
          out.put(key, buildEndpoint(operation, method, openApiPath));
        }
      }
    }
    return out;
  }

  private static String buildKey(PathItem.HttpMethod method, String basePath, String openApiPath) {
    String normalisedBase = basePath == null ? "" : basePath;
    String normalisedPath = openApiPath.startsWith("/") ? openApiPath : "/" + openApiPath;
    return method.name() + " " + normalisedBase + normalisedPath;
  }

  private static Map<String, Object> buildEndpoint(
      Operation operation, PathItem.HttpMethod method, String openApiPath) {
    Map<String, Object> endpoint = new LinkedHashMap<>();
    endpoint.put("path", openApiPath);
    endpoint.put("method", method.name());
    if (operation.getSummary() != null && !operation.getSummary().isBlank()) {
      endpoint.put("summary", operation.getSummary());
    }
    if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
      endpoint.put("description", operation.getDescription());
    }
    if (operation.getOperationId() != null && !operation.getOperationId().isBlank()) {
      endpoint.put("operationId", operation.getOperationId());
    }
    if (operation.getTags() != null && !operation.getTags().isEmpty()) {
      endpoint.put("tags", new ArrayList<>(operation.getTags()));
    }
    if (Boolean.TRUE.equals(operation.getDeprecated())) {
      endpoint.put("deprecated", true);
    }
    endpoint.put("enrichments", new LinkedHashMap<String, Object>());
    return endpoint;
  }
}
