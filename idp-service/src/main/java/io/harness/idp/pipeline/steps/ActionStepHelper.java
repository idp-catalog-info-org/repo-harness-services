/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;

import static io.harness.remote.client.NGRestUtils.getResponse;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.service.ActionService;
import io.harness.logstreaming.NGLogCallback;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Singleton
@Slf4j
public class ActionStepHelper {
  private static final ObjectMapper OBJECT_MAPPER = NG_DEFAULT_OBJECT_MAPPER;
  private static final Pattern INPUT_PLACEHOLDER = Pattern.compile("\\$\\{\\{input\\.([^}]+)}}");
  private static final int DEFAULT_TIMEOUT_MS = 30_000;

  private final ActionService actionService;
  private final ScopeInfoClient scopeInfoClient;
  private final CustomHttpConnectorResolver connectorResolver;

  @Inject
  public ActionStepHelper(
      ActionService actionService, ScopeInfoClient scopeInfoClient, CustomHttpConnectorResolver connectorResolver) {
    this.actionService = actionService;
    this.scopeInfoClient = scopeInfoClient;
    this.connectorResolver = connectorResolver;
  }

  public JsonNode fetchActionDefinition(String accountId, String orgId, String projectId, String actionRef,
      String actionVersion, NGLogCallback logCallback) {
    logCallback.saveExecutionLog(
        String.format("Resolving Action [%s]%s", actionRef, actionVersion == null ? "" : " version " + actionVersion));

    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(actionRef, accountId, orgId, projectId);
    String identifier = identifierRef.getIdentifier();

    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
    if (scopeInfo == null) {
      throw new InvalidRequestException(String.format("Could not resolve scope for actionRef [%s]", actionRef));
    }
    Action action = (actionVersion == null || actionVersion.isEmpty())
        ? actionService.getPublishedAction(scopeInfo, identifier)
        : actionService.getAction(scopeInfo, identifier, actionVersion);
    return OBJECT_MAPPER.valueToTree(action);
  }

  public ActionRequestPlan buildRequestPlan(JsonNode actionDef, Map<String, String> inputs, String accountId,
      String orgId, String projectId, NGLogCallback logCallback) {
    String actionType = actionDef.path("type").asText("");
    if (!"HTTP".equalsIgnoreCase(actionType)) {
      throw new InvalidRequestException(
          String.format("IdpAction step V1 only supports type=HTTP Actions; received type=[%s]", actionType));
    }

    JsonNode httpSpec = actionDef.path("httpConfig");
    if (httpSpec.isMissingNode() || httpSpec.isNull()) {
      throw new InvalidRequestException("Action definition is missing httpConfig");
    }

    String method = httpSpec.path("method").asText("GET").toUpperCase(Locale.ROOT);
    String pathTemplate = httpSpec.path("path").asText("");
    String absoluteUrl = httpSpec.path("url").asText(null);
    String bodyTemplate = httpSpec.path("body").asText(null);
    int timeoutMs = httpSpec.path("timeoutMs").asInt(DEFAULT_TIMEOUT_MS);
    if (timeoutMs <= 0) {
      timeoutMs = DEFAULT_TIMEOUT_MS;
    }

    String connectorRef = actionDef.path("connectorRef").asText(null);
    ResolvedConnector connector;
    if (connectorRef == null || connectorRef.isEmpty()) {
      connector = new ResolvedConnector(null, Collections.emptyMap(), Collections.emptyMap(), new HashSet<>());
    } else {
      connector = resolveConnector(connectorRef, accountId, orgId, projectId, logCallback);
    }

    String fullUrl = composeUrl(connector.getBaseUrl(), pathTemplate, absoluteUrl, inputs);
    String resolvedBody = bodyTemplate == null ? null : substituteInputs(bodyTemplate, inputs);

    Map<String, String> mergedHeaders = new HashMap<>();
    mergedHeaders.putAll(connector.getDefaultHeaders());
    iterStringMap(httpSpec.path("headers"), (k, v) -> mergedHeaders.put(k, substituteInputs(v, inputs)));
    mergedHeaders.putAll(connector.getAuthHeaders());

    return ActionRequestPlan.builder()
        .url(fullUrl)
        .method(method)
        .body(resolvedBody)
        .headers(toHttpHeaderConfigList(mergedHeaders))
        .timeoutMs(timeoutMs)
        .delegateSelectors(connector.getDelegateSelectors())
        .build();
  }

  ResolvedConnector resolveConnector(
      String connectorRef, String accountId, String orgId, String projectId, NGLogCallback logCallback) {
    return connectorResolver.resolve(connectorRef, accountId, orgId, projectId, logCallback);
  }

  public Map<String, Object> extractOutputs(JsonNode actionDef, String responseBody, NGLogCallback logCallback) {
    if (actionDef == null || responseBody == null || responseBody.isEmpty()) {
      return Collections.emptyMap();
    }
    JsonNode mappingNode = actionDef.path("outputMapping");
    if (mappingNode.isMissingNode() || mappingNode.isNull() || !mappingNode.isObject() || mappingNode.size() == 0) {
      return Collections.emptyMap();
    }

    JsonNode parsedBody;
    try {
      parsedBody = OBJECT_MAPPER.readTree(responseBody);
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          String.format("Response body is not valid JSON; cannot extract outputMapping: %s", e.getMessage()));
      return Collections.emptyMap();
    }

    Map<String, Object> outputs = new HashMap<>();
    Iterator<String> keys = mappingNode.fieldNames();
    while (keys.hasNext()) {
      String outputVar = keys.next();
      String rawPath = mappingNode.path(outputVar).asText("");
      JsonNode value = parsedBody.at(toJsonPointer(rawPath));
      if (value.isMissingNode() || value.isNull()) {
        continue;
      }
      outputs.put(outputVar, toNativeValue(value));
    }
    return outputs;
  }

  static Object toNativeValue(JsonNode node) {
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isInt()) {
      return node.intValue();
    }
    if (node.isLong()) {
      return node.longValue();
    }
    if (node.isDouble() || node.isFloat()) {
      return node.doubleValue();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    return node.toString();
  }

  public Set<Integer> expectedStatusCodes(JsonNode actionDef) {
    if (actionDef == null) {
      return Collections.emptySet();
    }
    JsonNode codes = actionDef.path("httpConfig").path("expectedStatusCodes");
    if (codes.isMissingNode() || codes.isNull() || !codes.isArray() || codes.size() == 0) {
      return Collections.emptySet();
    }
    Set<Integer> out = new HashSet<>();
    for (JsonNode c : codes) {
      if (c.isInt() || c.canConvertToInt()) {
        out.add(c.asInt());
      }
    }
    return out;
  }

  public boolean isStatusCodeAccepted(int statusCode, Set<Integer> expected) {
    if (expected == null || expected.isEmpty()) {
      return statusCode >= 200 && statusCode < 300;
    }
    return expected.contains(statusCode);
  }

  public boolean shouldSuppressResponseBody(JsonNode actionDef) {
    if (actionDef == null) {
      return false;
    }
    JsonNode suppress = actionDef.path("httpConfig").path("suppressResponseBody");
    return !suppress.isMissingNode() && suppress.asBoolean(false);
  }

  static String toJsonPointer(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    if (raw.startsWith("/")) {
      return raw;
    }
    String dotted = raw.startsWith("$.") ? raw.substring(2) : raw;
    if (dotted.isEmpty()) {
      return "";
    }
    String[] segments = dotted.split("\\.", -1);
    StringBuilder sb = new StringBuilder(dotted.length() + segments.length);
    for (String segment : segments) {
      sb.append('/').append(escapePointerSegment(segment));
    }
    return sb.toString();
  }

  private static String escapePointerSegment(String segment) {
    if (segment.indexOf('~') < 0 && segment.indexOf('/') < 0) {
      return segment;
    }
    return segment.replace("~", "~0").replace("/", "~1");
  }

  static String composeUrl(
      String baseUrl, String pathTemplate, String absoluteUrlOverride, Map<String, String> inputs) {
    if (absoluteUrlOverride != null && !absoluteUrlOverride.isEmpty()) {
      return substituteInputs(absoluteUrlOverride, inputs);
    }
    if (baseUrl == null || baseUrl.isEmpty()) {
      throw new InvalidRequestException(
          "Action's connector has no baseUrl and the action does not declare an absolute url");
    }
    String resolvedPath = substituteInputs(pathTemplate == null ? "" : pathTemplate, inputs);
    String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    if (resolvedPath.isEmpty()) {
      return trimmedBase;
    }
    return trimmedBase + (resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath);
  }

  static String substituteInputs(String template, Map<String, String> inputs) {
    if (template == null || template.isEmpty()) {
      return template;
    }
    Map<String, String> safeInputs = inputs == null ? Collections.emptyMap() : inputs;
    Matcher m = INPUT_PLACEHOLDER.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1);
      String value = safeInputs.get(key);
      m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  static List<HttpHeaderConfig> toHttpHeaderConfigList(Map<String, String> headers) {
    List<HttpHeaderConfig> out = new ArrayList<>(headers.size());
    for (Map.Entry<String, String> e : headers.entrySet()) {
      out.add(HttpHeaderConfig.builder().key(e.getKey()).value(e.getValue() == null ? "" : e.getValue()).build());
    }
    return out;
  }

  private static void iterStringMap(JsonNode node, java.util.function.BiConsumer<String, String> action) {
    if (node == null || node.isMissingNode() || !node.isObject()) {
      return;
    }
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      String k = it.next();
      action.accept(k, node.path(k).asText(""));
    }
  }

  static final class ResolvedConnector {
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Map<String, String> authHeaders;
    private final Set<String> delegateSelectors;

    ResolvedConnector(String baseUrl, Map<String, String> defaultHeaders, Map<String, String> authHeaders,
        Set<String> delegateSelectors) {
      this.baseUrl = baseUrl;
      this.defaultHeaders = defaultHeaders == null ? Collections.emptyMap() : defaultHeaders;
      this.authHeaders = authHeaders == null ? Collections.emptyMap() : authHeaders;
      this.delegateSelectors = delegateSelectors == null ? new HashSet<>() : delegateSelectors;
    }

    String getBaseUrl() {
      return baseUrl;
    }
    Map<String, String> getDefaultHeaders() {
      return defaultHeaders;
    }
    Map<String, String> getAuthHeaders() {
      return authHeaders;
    }
    Set<String> getDelegateSelectors() {
      return delegateSelectors;
    }
  }
}
