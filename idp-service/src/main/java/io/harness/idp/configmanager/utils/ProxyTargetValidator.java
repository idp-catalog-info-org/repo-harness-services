/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.SsrfDestinationValidator;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import okhttp3.HttpUrl;

/**
 * Save-time SSRF policy for Backstage backend-proxy targets (IDP-10919).
 *
 * <p>Backstage's proxy-backend fetches {@code proxy.endpoints.*.target} from inside the account's pod and relays the
 * raw response to the caller, so an account-settable target pointing at cloud metadata leaks the pod's credentials.
 *
 * <p>Hostname policy only, no DNS resolution. Resolving here would falsely reject delegate-routed customer-internal
 * hostnames that do not resolve from Harness, and DNS rebinding defeats a resolve-then-store check anyway. The
 * resolution-time guarantee lives in the Backstage pod's egress interceptor.
 */
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ProxyTargetValidator {
  private static final String PROXY = "proxy";
  private static final String ENDPOINTS = "endpoints";
  private static final String TARGET = "target";
  private static final String HEADERS = "headers";
  private static final String SCHEME_SEPARATOR = "://";
  private static final String ENV_VARIABLE_PREFIX = "${";

  private static final Set<String> BLOCKED_HOSTS =
      Set.of("metadata", "metadata.google.internal", "metadata.goog", "metadata.azure.com");

  private static final List<String> BLOCKED_HOST_SUFFIXES =
      List.of(".internal", ".local", ".localdomain", ".cluster.local", ".svc");

  private static final Set<String> BLOCKED_HEADERS =
      Set.of("metadata-flavor", "metadata", "x-aws-ec2-metadata-token", "x-google-metadata-request");

  // Matches dotted-quad (127.0.0.1) as well as the decimal (2852039166) and partial (127.1) forms Java's
  // InetAddress#getByName also accepts as IPv4 literals; see isBlockedLiteralAddress for why this only has to be a
  // candidate filter, not a strict validator.
  private static final Pattern IPV4_LITERAL_CANDIDATE = Pattern.compile("^\\d+(\\.\\d+){0,3}$");

  private static final String BLOCKED_TARGET_MESSAGE =
      "Proxy endpoint [%s] has target [%s] which is not allowed. Cloud metadata and internal-only destinations "
      + "cannot be used as backend proxy targets. Use a publicly resolvable HTTP(S) target, or reach internal hosts "
      + "through a delegate.";

  private static final String BLOCKED_SCHEME_MESSAGE =
      "Proxy endpoint [%s] has target [%s] which is not allowed. Backend proxy targets must use http or https.";

  private static final String BLOCKED_HEADER_MESSAGE =
      "Proxy endpoint [%s] sets header [%s] which is not allowed. Cloud metadata headers cannot be configured on "
      + "backend proxy endpoints.";

  /**
   * Rejects any {@code proxy.endpoints} entry in the given plugin config YAML that targets cloud metadata or an
   * internal-only destination, or that injects a cloud metadata header.
   *
   * @throws InvalidRequestException naming the offending endpoint and target
   */
  public void validateProxyTargets(String config) {
    JsonNode endpoints = proxyEndpoints(config);
    if (endpoints == null) {
      return;
    }
    Iterator<Map.Entry<String, JsonNode>> endpointsIterator = endpoints.fields();
    while (endpointsIterator.hasNext()) {
      Map.Entry<String, JsonNode> endpoint = endpointsIterator.next();
      validateEndpoint(endpoint.getKey(), endpoint.getValue());
    }
  }

  private JsonNode proxyEndpoints(String config) {
    if (isEmpty(config)) {
      return null;
    }
    JsonNode proxy = ConfigManagerUtils.asJsonNode(config).get(PROXY);
    if (proxy == null || !proxy.isObject()) {
      return null;
    }
    JsonNode endpoints = proxy.get(ENDPOINTS);
    return endpoints != null && endpoints.isObject() ? endpoints : null;
  }

  private void validateEndpoint(String endpointName, JsonNode endpoint) {
    if (endpoint == null || !endpoint.isObject()) {
      return;
    }
    JsonNode target = endpoint.get(TARGET);
    if (target != null && target.isTextual()) {
      validateTarget(endpointName, target.asText());
    }
    validateHeaders(endpointName, endpoint.get(HEADERS));
  }

  private void validateTarget(String endpointName, String target) {
    // Targets carrying ${VAR} are substituted inside the Backstage pod, so the final destination is not knowable
    // here. The pod's egress interceptor is the enforcement point for those.
    if (isEmpty(target) || target.contains(ENV_VARIABLE_PREFIX)) {
      return;
    }

    String scheme = scheme(target);
    if (scheme != null && !"http".equals(scheme) && !"https".equals(scheme)) {
      throw new InvalidRequestException(format(BLOCKED_SCHEME_MESSAGE, endpointName, target));
    }

    HttpUrl parsed = HttpUrl.parse(target);
    if (parsed == null) {
      // Every plugin schema this has been checked against already rejects a target OkHttp can't parse, and
      // Backstage's own `new URL(target)` would reject the same inputs it relays for delegate-routed targets. This
      // runs for any plugin config with a proxy: block though, not only harness-proxy; if a plugin schema is ever
      // added without an equivalent target pattern, this branch is fail-open for that plugin's unparseable targets
      // until the connect-time guard in the Backstage pod catches them.
      return;
    }

    if (isBlockedHost(parsed.host())) {
      throw new InvalidRequestException(format(BLOCKED_TARGET_MESSAGE, endpointName, target));
    }
  }

  private void validateHeaders(String endpointName, JsonNode headers) {
    if (headers == null || !headers.isObject()) {
      return;
    }
    Iterator<String> headerNames = headers.fieldNames();
    while (headerNames.hasNext()) {
      String headerName = headerNames.next();
      if (BLOCKED_HEADERS.contains(headerName.toLowerCase().trim())) {
        throw new InvalidRequestException(format(BLOCKED_HEADER_MESSAGE, endpointName, headerName));
      }
    }
  }

  private boolean isBlockedHost(String host) {
    String normalized = host.toLowerCase();
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (BLOCKED_HOSTS.contains(normalized)) {
      return true;
    }
    if (BLOCKED_HOST_SUFFIXES.stream().anyMatch(normalized::endsWith)) {
      return true;
    }
    return isBlockedLiteralAddress(normalized);
  }

  /**
   * Only literal addresses are inspected. Names are deliberately not resolved here, see the class comment, so
   * {@link InetAddress#getByName} is only ever called on inputs {@link #IPV4_LITERAL_CANDIDATE} or the IPv6 colon
   * check already narrows to digits/dots/colons, which {@code InetAddress#getByName} parses locally without a DNS
   * lookup, valid or not (confirmed: it also rejects malformed digit groups like "300.1.2.3" without one). The
   * candidate check only has to avoid sending real hostnames into a DNS lookup, not fully validate the address, so
   * it deliberately also matches decimal (2852039166) and partial (127.1) forms alongside dotted-quad, which
   * getByName resolves to the same address a browser or curl would.
   */
  private boolean isBlockedLiteralAddress(String host) {
    if (host.indexOf(':') < 0 && !IPV4_LITERAL_CANDIDATE.matcher(host).matches()) {
      return false;
    }
    try {
      return SsrfDestinationValidator.isBlockedAddress(InetAddress.getByName(host));
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private String scheme(String target) {
    int separator = target.indexOf(SCHEME_SEPARATOR);
    return separator > 0 ? target.substring(0, separator).toLowerCase() : null;
  }
}
