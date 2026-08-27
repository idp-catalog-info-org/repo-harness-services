/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.external.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.proxy.external.beans.ExternalProxyEndpointConfig;
import io.harness.idp.proxy.external.service.ExternalProxyService;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class ExternalProxyApiImpl implements ExternalProxyApi {
  private static final String FORWARDING_MESSAGE = "Forwarding {} request to [{}]";
  private static final String ENDPOINT_NOT_FOUND_MESSAGE = "No proxy endpoint configuration found for path: %s";
  private static final String METHOD_NOT_ALLOWED_MESSAGE = "HTTP method %s is not allowed for endpoint: %s";

  private static final Set<String> BLOCKED_HEADERS =
      new HashSet<>(Arrays.asList("cookie", "set-cookie", "authorization", "x-api-key", "harness-account",
          "harness-token", "x-harness-idp-user-token", "host", "connection", "keep-alive", "proxy-authenticate",
          "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade"));

  private static final Set<String> ALLOWED_HEADERS_FOR_HARNESS_DOMAINS =
      new HashSet<>(Arrays.asList("x-api-key", "cookie", "authorization"));

  private static final Set<String> DEFAULT_ALLOWED_HEADERS =
      new HashSet<>(Arrays.asList("accept", "accept-language", "content-type", "content-language", "cache-control",
          "x-request-id", "x-correlation-id", "user-agent", "if-match", "if-none-match", "if-modified-since"));

  private final ExternalProxyService externalProxyService;

  @Inject
  public ExternalProxyApiImpl(ExternalProxyService externalProxyService) {
    this.externalProxyService = externalProxyService;
  }

  @Override
  public Response getProxy(UriInfo uriInfo, HttpHeaders headers, String endpoint, String harnessAccount) {
    return forwardRequest(uriInfo, headers, endpoint, harnessAccount, "GET", null);
  }

  @Override
  public Response postProxy(UriInfo uriInfo, HttpHeaders headers, String endpoint, String harnessAccount, String body) {
    return forwardRequest(uriInfo, headers, endpoint, harnessAccount, "POST", body);
  }

  @Override
  public Response putProxy(UriInfo uriInfo, HttpHeaders headers, String endpoint, String harnessAccount, String body) {
    return forwardRequest(uriInfo, headers, endpoint, harnessAccount, "PUT", body);
  }

  @Override
  public Response patchProxy(
      UriInfo uriInfo, HttpHeaders headers, String endpoint, String harnessAccount, String body) {
    return forwardRequest(uriInfo, headers, endpoint, harnessAccount, "PATCH", body);
  }

  @Override
  public Response deleteProxy(UriInfo uriInfo, HttpHeaders headers, String endpoint, String harnessAccount) {
    return forwardRequest(uriInfo, headers, endpoint, harnessAccount, "DELETE", null);
  }

  private Response forwardRequest(
      UriInfo uriInfo, HttpHeaders headers, String endpoint, String accountIdentifier, String method, String body) {
    try {
      String[] pathParts = extractEndpointAndPath(endpoint);
      String endpointPath = pathParts[0];
      String remainingPath = pathParts[1];

      Optional<ExternalProxyEndpointConfig> configOpt =
          externalProxyService.getProxyEndpointConfig(accountIdentifier, endpointPath);

      if (configOpt.isEmpty()) {
        log.warn("No proxy configuration found for endpoint: {} in account: {}", endpointPath, accountIdentifier);
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ResponseMessage.builder().message(String.format(ENDPOINT_NOT_FOUND_MESSAGE, endpointPath)).build())
            .build();
      }

      ExternalProxyEndpointConfig config = configOpt.get();
      if (!externalProxyService.isMethodAllowed(config, method)) {
        log.warn("HTTP method {} not allowed for endpoint: {}", method, endpointPath);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ResponseMessage.builder()
                        .message(String.format(METHOD_NOT_ALLOWED_MESSAGE, method, endpointPath))
                        .build())
            .build();
      }

      String targetUrl = buildTargetUrl(config, remainingPath, uriInfo);
      Map<String, List<String>> filteredHeaders = buildFilteredHeaders(headers, config);
      applyConfiguredHeaders(filteredHeaders, config, accountIdentifier);

      String contentType = headers.getMediaType() != null ? headers.getMediaType().toString() : "application/json";
      log.info(FORWARDING_MESSAGE, method, targetUrl);
      return externalProxyService.getResponse(accountIdentifier, config.isEnableSignedUser(), config.getEndpoint(),
          targetUrl, method, body, filteredHeaders, contentType);
    } catch (Exception e) {
      log.error("Error forwarding {} request for endpoint {}: {}", method, endpoint, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Proxy request failed: " + e.getMessage()).build())
          .build();
    }
  }

  private String[] extractEndpointAndPath(String fullPath) {
    if (isEmpty(fullPath)) {
      return new String[] {"", ""};
    }

    if (fullPath.startsWith("/")) {
      fullPath = fullPath.substring(1);
    }

    int firstSlash = fullPath.indexOf('/');
    if (firstSlash == -1) {
      return new String[] {fullPath, ""};
    }

    return new String[] {fullPath.substring(0, firstSlash), fullPath.substring(firstSlash + 1)};
  }

  private String buildTargetUrl(ExternalProxyEndpointConfig config, String remainingPath, UriInfo uriInfo) {
    String target = config.getTarget();
    if (target == null) {
      throw new IllegalArgumentException("Target URL is not configured for endpoint: " + config.getEndpoint());
    }

    if (target.endsWith("/")) {
      target = target.substring(0, target.length() - 1);
    }

    String finalPath = remainingPath;
    if (config.getPathRewrite() != null && !config.getPathRewrite().isEmpty()) {
      finalPath = applyPathRewrite(remainingPath, config.getPathRewrite());
    }

    StringBuilder urlBuilder = new StringBuilder(target);
    if (!isEmpty(finalPath)) {
      if (!finalPath.startsWith("/")) {
        urlBuilder.append("/");
      }
      urlBuilder.append(finalPath);
    }

    String queryString = uriInfo.getRequestUri().getRawQuery();
    if (!isEmpty(queryString)) {
      urlBuilder.append("?").append(queryString);
    }

    return urlBuilder.toString();
  }

  private String applyPathRewrite(String path, Map<String, String> pathRewrite) {
    String result = path;
    for (Map.Entry<String, String> rule : pathRewrite.entrySet()) {
      result = result.replaceAll(rule.getKey(), rule.getValue());
    }
    return result;
  }

  private Map<String, List<String>> buildFilteredHeaders(HttpHeaders headers, ExternalProxyEndpointConfig config) {
    Set<String> allowedHeaders = config.getAllowedHeaders() != null && !config.getAllowedHeaders().isEmpty()
        ? new HashSet<>(config.getAllowedHeaders())
        : DEFAULT_ALLOWED_HEADERS;

    Map<String, List<String>> filteredHeaders = new HashMap<>();
    String target = config.getTarget();
    final boolean isHarnessDomain = isHarnessDomain(target);

    headers.getRequestHeaders().forEach((headerName, values) -> {
      String lowerHeaderName = headerName.toLowerCase();

      if (isHarnessDomain && ALLOWED_HEADERS_FOR_HARNESS_DOMAINS.contains(lowerHeaderName)) {
        filteredHeaders.put(headerName, new ArrayList<>(values));
        return;
      }

      if (BLOCKED_HEADERS.contains(lowerHeaderName)) {
        return;
      }

      if (isHeaderAllowed(lowerHeaderName, allowedHeaders)) {
        filteredHeaders.put(headerName, new ArrayList<>(values));
      }
    });

    return filteredHeaders;
  }

  private boolean isHeaderAllowed(String headerName, Set<String> allowedHeaders) {
    return allowedHeaders.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(headerName));
  }

  private void applyConfiguredHeaders(
      Map<String, List<String>> headers, ExternalProxyEndpointConfig config, String accountIdentifier) {
    if (config.getHeaders() == null || config.getHeaders().isEmpty()) {
      return;
    }

    config.getHeaders().forEach((headerName, headerValue) -> {
      String resolvedValue = externalProxyService.resolveHeaderValue(accountIdentifier, headerValue);
      headers.computeIfAbsent(headerName, k -> new ArrayList<>()).add(resolvedValue);
    });
  }

  private boolean isHarnessDomain(String target) {
    if (target == null) {
      return false;
    }
    try {
      String host = new URL(target).getHost();
      return host.endsWith(".harness.io");
    } catch (MalformedURLException e) {
      log.warn("Failed to parse target URL for domain check: {}", target);
      return false;
    }
  }
}
