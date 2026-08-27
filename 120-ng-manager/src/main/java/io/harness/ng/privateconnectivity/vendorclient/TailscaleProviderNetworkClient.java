/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.vendorclient;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityHelpers;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

/**
 * Production Tailscale ProviderNetworkClient.
 *
 * Documented APIs only:
 *  - Create automation tailnet: POST /api/v2/organizations/-/tailnets (multi-tailnet alpha)
 *  - Destroy: DELETE /api/v2/tailnet/{id} with a child-tailnet-scoped access token
 *  - OAuth exchange: POST /api/v2/oauth/token
 *  - ACL validate/apply: POST /api/v2/tailnet/{t}/acl/validate, POST /api/v2/tailnet/{t}/acl
 *  - Complete DNS configuration: POST /api/v2/tailnet/{t}/dns/configuration
 *  - Auth keys + WIF federated identities: POST /api/v2/tailnet/{t}/keys and
 *    DELETE /api/v2/tailnet/{t}/keys/{keyId}
 *  - Devices: GET /api/v2/tailnet/{t}/devices and DELETE /api/v2/device/{id}
 *
 * Organization inventory operations use short-lived access tokens derived from the deployment-owned
 * organization OAuth client. Tailnet-scoped operations use short-lived tokens derived from the
 * child-tailnet OAuth client returned by the create response and persisted encrypted with the
 * binding.
 */
@Slf4j
@OwnedBy(CI)
public class TailscaleProviderNetworkClient implements ProviderNetworkClient {
  // Docker cannot express suffix-specific resolvers. PC-enabled Linux and Windows containers use
  // Quad100 for private suffixes, so configure stable public defaults for unmatched names. The
  // defaults must be active (not fallback-only): Linux hosts with native split-DNS support do not
  // expose fallback-only resolvers through Quad100 to Docker containers.
  private static final List<String> HOSTED_PUBLIC_DNS_RESOLVERS = List.of("8.8.8.8", "8.8.4.4");
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
  // Transient-error retry policy for the vendor API (429/5xx). Bounded so provision/release never
  // hang on a persistent Tailscale outage.
  private static final int MAX_HTTP_ATTEMPTS = 2;
  private static final long BASE_BACKOFF_MS = 500L;
  private static final long MAX_BACKOFF_MS = 5000L;
  private static final long CUSTOMER_AUTH_KEY_EXPIRY_SECONDS = Duration.ofDays(90).toSeconds();
  private static final long HELPER_AUTH_KEY_EXPIRY_SECONDS = Duration.ofHours(1).toSeconds();
  private static final long ACCESS_TOKEN_EXPIRY_SKEW_MILLIS = Duration.ofSeconds(30).toMillis();
  private static final int MAX_KEY_DESCRIPTION_LENGTH = 50;
  private static final String TOKEN_RELATIVE_IDENTIFIER = "-";
  // Documented maximum page size for the organization tailnet inventory.
  private static final int TAILNET_INVENTORY_PAGE_SIZE = 100;
  // Bounds the cursor walk so a provider paging fault cannot loop indefinitely. Exhausting this
  // budget is reported as a failure rather than a short inventory.
  private static final int MAX_TAILNET_INVENTORY_PAGES = 100;

  private final PrivateConnectivityOrgConfig orgConfig;
  private final PrivateConnectivityConfigRepository configRepository;
  private final PrivateConnectivityChildCredentialService childCredentialService;
  private final OkHttpClient httpClient;
  /** Short-lived access tokens keyed by OAuth client ID (never persisted). */
  private final ConcurrentHashMap<String, CachedAccessToken> accessTokenCache = new ConcurrentHashMap<>();

  public TailscaleProviderNetworkClient(PrivateConnectivityOrgConfig orgConfig,
      PrivateConnectivityConfigRepository configRepository,
      PrivateConnectivityChildCredentialService childCredentialService) {
    this(orgConfig, configRepository, childCredentialService,
        new OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT)
            .readTimeout(HTTP_TIMEOUT)
            .writeTimeout(HTTP_TIMEOUT)
            // Retries are classified explicitly below. Automatic client retries could replay a
            // non-idempotent create after an ambiguous connection loss.
            .retryOnConnectionFailure(false)
            .build());
  }

  TailscaleProviderNetworkClient(PrivateConnectivityOrgConfig orgConfig,
      PrivateConnectivityConfigRepository configRepository,
      PrivateConnectivityChildCredentialService childCredentialService, OkHttpClient httpClient) {
    this.orgConfig = orgConfig;
    this.configRepository = configRepository;
    this.childCredentialService = childCredentialService;
    this.httpClient = httpClient;
  }

  @Override
  public NetworkCreateResult createNetwork(String networkName) {
    requireOrgCredentials();
    try {
      // Verify that the deployment OAuth client resolves to the configured organization before the
      // non-idempotent create. The create response contains a one-time child secret, so discovering
      // an organization mismatch only after the POST could leave an undeletable tailnet.
      if (listNetworks().size() == 0) {
        throw new ProviderNetworkException(
            "Tailscale organization inventory is empty; organization identity cannot be confirmed");
      }
    } catch (ProviderNetworkException exception) {
      // No create request has been sent, so this is unambiguously safe to retry after correcting the
      // provider configuration or transient inventory failure.
      throw new ProviderCreateException("Tailscale organization preflight failed before network creation",
          CreateOutcome.DEFINITELY_NOT_CREATED, exception);
    }
    ObjectNode body = MAPPER.createObjectNode();
    body.put("displayName", networkName);
    JsonNode response = executeNonIdempotentCreateJson(
        "POST", organizationTailnetsUrl(), orgBearer(exchangeOrganizationAccessToken()), body.toString());
    assertCreateResponseOrganization(response);
    String id = text(response, "id");
    if (StringUtils.isBlank(id)) {
      throw unknownCreateResponse("Tailscale create network response missing id");
    }
    JsonNode oauthClient = response.path("oauthClient");
    String oauthClientId = text(oauthClient, "id");
    String oauthClientSecret = text(oauthClient, "secret");
    if (StringUtils.isAnyBlank(oauthClientId, oauthClientSecret)) {
      throw unknownCreateResponse("Tailscale create network response missing child OAuth client id/secret");
    }
    String dnsName = text(response, "dnsName");
    log.info("Private Connectivity provider network created networkName={} networkRef={} dnsName={}", networkName, id,
        dnsName);
    return new NetworkCreateResult(id, new ProviderAdminCredential(oauthClientId, oauthClientSecret));
  }

  @Override
  public List<RecoverableNetwork> findNetworksByName(String networkName) {
    requireOrgCredentials();
    if (StringUtils.isBlank(networkName)) {
      throw new ProviderNetworkException("Provider network name is required for exact recovery lookup");
    }
    configRepository.findByProviderNetworkName(networkName).ifPresent(this::assertProviderConfigurationMatches);
    JsonNode tailnets = listNetworks();
    List<RecoverableNetwork> matches = new ArrayList<>();
    for (JsonNode tailnet : tailnets) {
      String displayName = text(tailnet, "displayName");
      if (!networkName.equals(displayName)) {
        continue;
      }
      String id = text(tailnet, "id");
      if (StringUtils.isBlank(id)) {
        throw new ProviderNetworkException("Tailscale exact network recovery match is missing id");
      }
      matches.add(new RecoverableNetwork(id));
    }
    return List.copyOf(matches);
  }

  @Override
  public boolean networkExists(String providerNetworkRef) {
    if (StringUtils.isBlank(providerNetworkRef)) {
      return false;
    }
    configRepository.findByProviderNetworkRef(providerNetworkRef).ifPresent(this::assertProviderConfigurationMatches);
    for (JsonNode tailnet : listNetworks()) {
      String id = text(tailnet, "id");
      if (providerNetworkRef.equals(id)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Documented: GET /organizations/{org}/tailnets is cursor-paginated and returns at most 100
   * tailnets per page. Exact-name recovery and absence confirmation both read this inventory, so a
   * single page must never be treated as the whole organization: that would hide a recoverable
   * tailnet, report a live tailnet as deleted, or skip a required delete and orphan it. The walk
   * therefore continues until the provider stops returning a cursor, and any paging fault fails the
   * call instead of yielding a short inventory. The token is exchanged once so it cannot expire
   * between pages.
   */
  private JsonNode listNetworks() {
    requireOrgCredentials();
    String authorization = orgBearer(exchangeOrganizationAccessToken());
    ArrayNode inventory = MAPPER.createArrayNode();
    Set<String> seenTailnetIds = new LinkedHashSet<>();
    String cursor = null;
    Integer reportedTotal = null;
    for (int page = 0; page < MAX_TAILNET_INVENTORY_PAGES; page++) {
      JsonNode response = executeJson("GET", organizationTailnetsPageUrl(cursor), authorization, null);
      if (!response.isObject() || !response.has("tailnets") || !response.get("tailnets").isArray()) {
        throw new ProviderNetworkException("Tailscale list networks response missing tailnets array");
      }
      JsonNode tailnets = response.get("tailnets");
      for (JsonNode tailnet : tailnets) {
        assertInventoryOrganization(tailnet);
        String id = text(tailnet, "id");
        if (StringUtils.isBlank(id)) {
          throw new ProviderNetworkException(
              "Tailscale network inventory contains a tailnet without an id; refusing absence confirmation");
        }
        if (StringUtils.isBlank(text(tailnet, "displayName"))) {
          throw new ProviderNetworkException(
              "Tailscale network inventory contains a tailnet without a displayName; refusing exact-name recovery");
        }
        if (!seenTailnetIds.add(id)) {
          throw new ProviderNetworkException(
              "Tailscale network inventory contains a duplicate tailnet id; refusing absence confirmation");
        }
        inventory.add(tailnet);
      }
      JsonNode totalCount = response.get("totalCount");
      if (totalCount != null && !totalCount.isNull()) {
        if (!totalCount.isIntegralNumber() || !totalCount.canConvertToInt() || totalCount.asInt() < 0) {
          throw new ProviderNetworkException(
              "Tailscale network inventory contains an invalid totalCount; refusing absence confirmation");
        }
        int pageReportedTotal = totalCount.asInt();
        if (reportedTotal != null && reportedTotal.intValue() != pageReportedTotal) {
          throw new ProviderNetworkException(
              "Tailscale network inventory total changed during pagination; refusing absence confirmation");
        }
        reportedTotal = pageReportedTotal;
      }
      JsonNode cursorNode = response.get("cursor");
      if (cursorNode != null && !cursorNode.isNull() && !cursorNode.isTextual()) {
        throw new ProviderNetworkException(
            "Tailscale network inventory contains an invalid cursor; refusing absence confirmation");
      }
      String nextCursor = text(response, "cursor");
      if (StringUtils.isBlank(nextCursor)) {
        if (reportedTotal != null && reportedTotal.intValue() != inventory.size()) {
          throw new ProviderNetworkException("Tailscale network inventory count is inconsistent; refusing absence "
              + "confirmation (collected=" + inventory.size() + ", reported=" + reportedTotal + ")");
        }
        log.debug("Private Connectivity provider network inventory completed pages={} networkCount={}", page + 1,
            inventory.size());
        return inventory;
      }
      if (nextCursor.equals(cursor)) {
        throw new ProviderNetworkException(
            "Tailscale network inventory repeated a pagination cursor; refusing absence confirmation");
      }
      cursor = nextCursor;
    }
    throw new ProviderNetworkException("Tailscale network inventory exceeded " + MAX_TAILNET_INVENTORY_PAGES
        + " pages; refusing absence confirmation");
  }

  @Override
  public void applyPolicy(String providerNetworkRef, NetworkPolicy policy) {
    String acl = policy == null ? null : policy.aclJson();
    if (StringUtils.isBlank(acl)) {
      throw new ProviderNetworkException("ACL policy JSON is required");
    }
    executeRaw("POST", tailnetUrl(providerNetworkRef, "/acl"), tailnetBearer(providerNetworkRef), acl, false);
    log.debug("Private Connectivity provider policy applied networkRef={}", providerNetworkRef);
  }

  @Override
  public void validatePolicy(String providerNetworkRef, NetworkPolicy policy) {
    String acl = policy == null ? null : policy.aclJson();
    if (StringUtils.isBlank(acl)) {
      throw new PolicyValidationException("ACL policy JSON is required");
    }
    JsonNode response =
        executeJson("POST", tailnetUrl(providerNetworkRef, "/acl/validate"), tailnetBearer(providerNetworkRef), acl);
    // Documented: HTTP 200 does not imply validity — inspect body for problems/errors.
    if (hasValidationProblems(response)) {
      throw new PolicyValidationException("Tailscale ACL validation failed");
    }
    log.debug("Private Connectivity provider policy validated networkRef={}", providerNetworkRef);
  }

  @Override
  public void configureDns(String providerNetworkRef, DnsConfig dnsConfig) {
    Map<String, List<String>> split =
        dnsConfig == null || dnsConfig.splitDnsDomains() == null ? Collections.emptyMap() : dnsConfig.splitDnsDomains();
    boolean enabled = dnsConfig != null && dnsConfig.enabled();

    // Replace the complete DNS configuration in one request so reconciliation cannot leave a
    // partially updated combination of global, split-DNS, and MagicDNS settings.
    ObjectNode body = MAPPER.createObjectNode();
    ArrayNode globalNameservers = body.putArray("nameservers");
    if (enabled) {
      HOSTED_PUBLIC_DNS_RESOLVERS.forEach(resolver -> addDnsResolver(globalNameservers, resolver));
    }
    ObjectNode splitBody = body.putObject("splitDNS");
    if (enabled) {
      for (Map.Entry<String, List<String>> entry : split.entrySet()) {
        if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
          continue;
        }
        ArrayNode nameservers = splitBody.putArray(entry.getKey());
        entry.getValue()
            .stream()
            .filter(StringUtils::isNotBlank)
            .forEach(resolver -> addDnsResolver(nameservers, resolver));
      }
    }
    body.putArray("searchPaths");
    ObjectNode preferences = body.putObject("preferences");
    preferences.put("overrideLocalDNS", enabled);
    preferences.put("magicDNS", enabled);

    executeJson("POST", tailnetUrl(providerNetworkRef, "/dns/configuration"), tailnetBearer(providerNetworkRef),
        body.toString());

    log.debug("Private Connectivity provider DNS configured networkRef={} enabled={} splitDnsEntryCount={} "
            + "globalResolverCount={}",
        providerNetworkRef, enabled, splitBody.size(), globalNameservers.size());
  }

  private static void addDnsResolver(ArrayNode destination, String address) {
    if (StringUtils.isBlank(address)) {
      return;
    }
    ObjectNode resolver = destination.addObject();
    resolver.put("address", address.trim());
    resolver.put("useWithExitNode", false);
  }

  @Override
  public WifCredentialInfo createWif(
      String providerNetworkRef, WifConfig wifConfig, String opaqueOperationDescription) {
    if (StringUtils.isBlank(providerNetworkRef) || wifConfig == null
        || StringUtils.isAnyBlank(wifConfig.issuer(), wifConfig.subject(), wifConfig.accountId())
        || wifConfig.tags() == null || wifConfig.tags().length != 1
        || !PrivateConnectivityHelpers.CI_RUNNER_TAG.equals(wifConfig.tags()[0])) {
      throw new ProviderNetworkException(
          "provider network, WIF issuer, subject, account claim and the fixed CI runner tag are required");
    }
    validateKeyDescription(opaqueOperationDescription);
    ObjectNode body = MAPPER.createObjectNode();
    body.put("keyType", "federated");
    body.put("description", opaqueOperationDescription);
    ArrayNode scopes = body.putArray("scopes");
    scopes.add("auth_keys");
    ArrayNode tags = body.putArray("tags");
    for (String tag : wifConfig.tags()) {
      tags.add(tag);
    }
    body.put("issuer", wifConfig.issuer());
    body.put("subject", wifConfig.subject());
    ObjectNode claims = body.putObject("customClaimRules");
    claims.put("account_id", wifConfig.accountId());

    JsonNode response = executeNonIdempotentCreateJson(
        "POST", tailnetUrl(providerNetworkRef, "/keys"), tailnetBearer(providerNetworkRef), body.toString());
    // Documented federated-key response: `id` is the WIF client-id used by `tailscale up --client-id`.
    String credentialId = text(response, "id");
    String audience = text(response, "audience");
    if (StringUtils.isAnyBlank(credentialId, audience)) {
      throw unknownCreateResponse("Tailscale WIF create response missing required id/audience fields");
    }
    log.debug("Private Connectivity provider workload identity created networkRef={} credentialId={}",
        providerNetworkRef, credentialId);
    return new WifCredentialInfo(credentialId, credentialId, audience);
  }

  @Override
  public JoinCredentialInfo createJoinCredential(
      String providerNetworkRef, List<String> enrollmentTags, String opaqueOperationDescription) {
    return createJoinCredential(
        providerNetworkRef, enrollmentTags, opaqueOperationDescription, true, CUSTOMER_AUTH_KEY_EXPIRY_SECONDS);
  }

  @Override
  public JoinCredentialInfo createHelperJoinCredential(
      String providerNetworkRef, List<String> tags, String opaqueOperationDescription) {
    return createJoinCredential(
        providerNetworkRef, tags, opaqueOperationDescription, false, HELPER_AUTH_KEY_EXPIRY_SECONDS);
  }

  private JoinCredentialInfo createJoinCredential(String providerNetworkRef, List<String> tags,
      String opaqueOperationDescription, boolean reusable, long expirySeconds) {
    if (StringUtils.isBlank(providerNetworkRef) || tags == null || tags.isEmpty()
        || tags.stream().anyMatch(StringUtils::isBlank)) {
      throw new ProviderNetworkException(
          "provider network, tags and opaque operation description are required for join credential");
    }
    validateKeyDescription(opaqueOperationDescription);
    long requestedAt = System.currentTimeMillis();
    ObjectNode body = MAPPER.createObjectNode();
    body.put("expirySeconds", expirySeconds);
    body.put("description", opaqueOperationDescription);
    ObjectNode capabilities = body.putObject("capabilities");
    ObjectNode devices = capabilities.putObject("devices");
    ObjectNode create = devices.putObject("create");
    create.put("reusable", reusable);
    create.put("ephemeral", false);
    create.put("preauthorized", true);
    ArrayNode tagsNode = create.putArray("tags");
    tags.forEach(tagsNode::add);

    // Never retry an outcome-unknown create in this request. The caller persisted the opaque
    // description before this POST; a later reconciliation resolves and removes the exact
    // unrecoverable key before another credential is created.
    JsonNode response = executeNonIdempotentCreateJson(
        "POST", tailnetUrl(providerNetworkRef, "/keys"), tailnetBearer(providerNetworkRef), body.toString());
    String keyId = text(response, "id");
    String authKey = text(response, "key");
    if (StringUtils.isAnyBlank(keyId, authKey)) {
      throw unknownCreateResponse("Tailscale auth key create response missing id/key");
    }
    long fallbackExpiresAt = Math.addExact(requestedAt, Math.multiplyExact(expirySeconds, 1000L));
    long expiresAt = parseExpiresAtEpochMs(response, fallbackExpiresAt);
    log.debug("Private Connectivity provider join credential created networkRef={} tags={} keyId={}",
        providerNetworkRef, tags, keyId);
    return new JoinCredentialInfo(keyId, authKey, expiresAt, reusable, true);
  }

  /**
   * A create response can be lost after Tailscale persisted the credential. Because auth-key secret
   * values are revealed only in the create response and key list responses are intentionally sparse, such a
   * credential cannot be safely adopted. Resolve the exact opaque description, delete one match and
   * confirm absence before retrying. Multiple matches are never guessed at.
   */
  private void removeUnrecoverableCredentialMatch(String providerNetworkRef, String opaqueOperationDescription) {
    List<String> matches = findCredentialIdsByOpaqueDescription(providerNetworkRef, opaqueOperationDescription);
    if (matches.size() > 1) {
      throw new ProviderNetworkException(
          "Multiple credentials match the persisted operation identity; operator intervention is required");
    }
    if (matches.size() == 1) {
      executeRaw("DELETE", tailnetUrl(providerNetworkRef, "/keys/" + matches.get(0)), tailnetBearer(providerNetworkRef),
          null, true);
      if (!findCredentialIdsByOpaqueDescription(providerNetworkRef, opaqueOperationDescription).isEmpty()) {
        throw new ProviderNetworkException("Credential deletion could not be confirmed; refusing to retry create");
      }
    }
  }

  private static void validateKeyDescription(String description) {
    if (StringUtils.isBlank(description) || description.length() > MAX_KEY_DESCRIPTION_LENGTH) {
      throw new ProviderNetworkException(
          "opaque key operation description must be between 1 and " + MAX_KEY_DESCRIPTION_LENGTH + " characters");
    }
  }

  @Override
  public void reconcileCredentialCreate(String providerNetworkRef, String opaqueOperationDescription) {
    if (StringUtils.isAnyBlank(providerNetworkRef, opaqueOperationDescription)) {
      throw new ProviderNetworkException(
          "providerNetworkRef and opaque operation description are required for credential reconciliation");
    }
    removeUnrecoverableCredentialMatch(providerNetworkRef, opaqueOperationDescription);
  }

  /**
   * Tailscale's list response is deliberately sparse. Resolve every returned identifier through the
   * documented key-detail endpoint and compare the opaque description exactly.
   */
  private List<String> findCredentialIdsByOpaqueDescription(
      String providerNetworkRef, String opaqueOperationDescription) {
    JsonNode response =
        executeJson("GET", tailnetUrl(providerNetworkRef, "/keys?all=true"), tailnetBearer(providerNetworkRef), null);
    if (!response.isObject() || !response.has("keys") || !response.get("keys").isArray()) {
      throw new ProviderNetworkException("Tailscale list credentials response missing keys array");
    }
    JsonNode keys = response.get("keys");
    List<String> matches = new ArrayList<>();
    for (JsonNode key : keys) {
      String keyId = text(key, "id");
      if (StringUtils.isBlank(keyId)) {
        throw new ProviderNetworkException("Tailscale list credentials response contains a key without an id");
      }
      JsonNode detail =
          executeJson("GET", tailnetUrl(providerNetworkRef, "/keys/" + keyId), tailnetBearer(providerNetworkRef), null);
      if (!detail.isObject() || !keyId.equals(text(detail, "id"))) {
        throw new ProviderNetworkException(
            "Tailscale credential detail response is incomplete; refusing create reconciliation");
      }
      String keyType = text(detail, "keyType");
      if (StringUtils.isBlank(keyType)) {
        throw new ProviderNetworkException(
            "Tailscale credential detail response is missing keyType; refusing create reconciliation");
      }
      if (!"auth".equals(keyType) && !"federated".equals(keyType)) {
        continue;
      }
      String description = text(detail, "description");
      if (StringUtils.isBlank(description)) {
        throw new ProviderNetworkException(
            "Tailscale credential detail response is missing description; refusing create reconciliation");
      }
      if (opaqueOperationDescription.equals(description)) {
        matches.add(keyId);
      }
    }
    return List.copyOf(matches);
  }

  @Override
  public void revokeJoinCredentials(String providerNetworkRef, List<String> joinKeyIds) {
    if (joinKeyIds == null || joinKeyIds.isEmpty()) {
      return;
    }
    Set<String> expectedAbsent = new LinkedHashSet<>();
    for (String keyId : joinKeyIds) {
      if (StringUtils.isBlank(keyId)) {
        throw new ProviderNetworkException("A persisted join credential id is blank; refusing to advance release");
      }
      expectedAbsent.add(keyId);
      executeRaw(
          "DELETE", tailnetUrl(providerNetworkRef, "/keys/" + keyId), tailnetBearer(providerNetworkRef), null, true);
    }
    confirmCredentialIdsAbsent(providerNetworkRef, expectedAbsent);
    log.debug("Private Connectivity provider join credentials revoked networkRef={} count={}", providerNetworkRef,
        joinKeyIds.size());
  }

  @Override
  public void deleteWifCredential(String providerNetworkRef, String wifCredentialId) {
    if (StringUtils.isBlank(wifCredentialId)) {
      return;
    }
    executeRaw("DELETE", tailnetUrl(providerNetworkRef, "/keys/" + wifCredentialId), tailnetBearer(providerNetworkRef),
        null, true);
    confirmCredentialIdsAbsent(providerNetworkRef, Set.of(wifCredentialId));
    log.debug("Private Connectivity provider workload identity deleted networkRef={} credentialId={}",
        providerNetworkRef, wifCredentialId);
  }

  @Override
  public void deleteDevices(String providerNetworkRef, List<String> deviceIds) {
    if (deviceIds == null || deviceIds.isEmpty()) {
      return;
    }
    for (String deviceId : deviceIds) {
      if (StringUtils.isBlank(deviceId)) {
        continue;
      }
      executeRaw("DELETE", orgConfig.resolveApiBaseUrl() + "/api/v2/device/" + deviceId,
          tailnetBearer(providerNetworkRef), null, true);
    }
    if (!listDeviceIds(providerNetworkRef).isEmpty()) {
      throw new ProviderNetworkException(
          "Tailscale device deletion has not reached a confirmed empty inventory; refusing to advance release");
    }
    log.debug(
        "Private Connectivity provider devices deleted networkRef={} count={}", providerNetworkRef, deviceIds.size());
  }

  private void confirmCredentialIdsAbsent(String providerNetworkRef, Set<String> expectedAbsent) {
    Set<String> remaining = new LinkedHashSet<>(listCredentialIds(providerNetworkRef));
    remaining.retainAll(expectedAbsent);
    if (!remaining.isEmpty()) {
      throw new ProviderNetworkException(
          "Tailscale credential deletion has not been confirmed; refusing to advance release");
    }
  }

  private List<String> listCredentialIds(String providerNetworkRef) {
    JsonNode response =
        executeJson("GET", tailnetUrl(providerNetworkRef, "/keys?all=true"), tailnetBearer(providerNetworkRef), null);
    if (response == null || !response.isObject() || !response.has("keys") || !response.get("keys").isArray()) {
      throw new ProviderNetworkException(
          "Tailscale credential inventory response is incomplete; refusing to advance release");
    }
    JsonNode keys = response.get("keys");
    Set<String> ids = new LinkedHashSet<>();
    for (JsonNode key : keys) {
      String id = text(key, "id");
      if (StringUtils.isBlank(id)) {
        throw new ProviderNetworkException(
            "Tailscale credential inventory contains a key without an id; refusing to advance release");
      }
      ids.add(id);
    }
    return List.copyOf(ids);
  }

  @Override
  public List<String> listDeviceIds(String providerNetworkRef) {
    JsonNode response = executeJson(
        "GET", tailnetUrl(providerNetworkRef, "/devices?fields=all"), tailnetBearer(providerNetworkRef), null);
    if (response == null || !response.has("devices") || !response.get("devices").isArray()) {
      throw new ProviderNetworkException(
          "Tailscale device inventory response is incomplete; refusing to advance release");
    }
    Set<String> ids = new LinkedHashSet<>();
    for (JsonNode device : response.get("devices")) {
      String id = firstNonBlank(text(device, "id"), text(device, "nodeId"));
      if (StringUtils.isBlank(id)) {
        throw new ProviderNetworkException(
            "Tailscale device inventory contains a device without an id; refusing to advance release");
      }
      ids.add(id);
    }
    return List.copyOf(ids);
  }

  @Override
  public void deleteNetwork(String providerNetworkRef) {
    requireOrgCredentials();
    if (!networkExists(providerNetworkRef)) {
      return;
    }
    PrivateConnectivityConfig config =
        configRepository.findByProviderNetworkRef(providerNetworkRef)
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 "No private connectivity binding for provider network " + providerNetworkRef));
    assertProviderConfigurationMatches(config);
    PrivateConnectivityChildCredentialService.ChildCredential stored =
        childCredentialService.load(config.getAccountIdentifier(), config.getProviderTailnetOAuthSecretRef());
    if (!stored.clientId().equals(config.getProviderTailnetOAuthClientId())) {
      throw new ProviderNetworkException("Stored provider child OAuth identity does not match the durable binding");
    }
    deleteNetwork(providerNetworkRef, new ProviderAdminCredential(stored.clientId(), stored.clientSecret()));
  }

  @Override
  public void deleteNetwork(String providerNetworkRef, ProviderAdminCredential credential) {
    requireOrgCredentials();
    if (StringUtils.isBlank(providerNetworkRef) || credential == null
        || StringUtils.isAnyBlank(credential.clientId(), credential.clientSecret())) {
      throw new InvalidRequestException(
          "provider network and child-tailnet OAuth credentials are required for deletion");
    }
    if (!networkExists(providerNetworkRef)) {
      clearChildAccessToken(credential.clientId());
      return;
    }
    clearChildAccessToken(credential.clientId());
    // The child-tailnet client is already scoped to this tailnet. Do not use the organization
    // client here: Tailscale returns 404 for deletion authorized by the parent client.
    executeRaw("DELETE", explicitTailnetUrl(providerNetworkRef),
        orgBearer(exchangeAccessToken(credential.clientId(), credential.clientSecret())), null, true);
    if (networkExists(providerNetworkRef)) {
      throw new ProviderNetworkException(
          "Tailscale network deletion has not yet been confirmed for ref=" + providerNetworkRef);
    }
    clearChildAccessToken(credential.clientId());
    log.debug("Private Connectivity provider network deleted and absence confirmed networkRef={}", providerNetworkRef);
  }

  private void clearChildAccessToken(String childClientId) {
    if (StringUtils.isNotBlank(childClientId)) {
      accessTokenCache.remove(childClientId);
    }
  }

  private String tailnetBearer(String providerNetworkRef) {
    if (StringUtils.isBlank(providerNetworkRef)) {
      throw new InvalidRequestException("providerNetworkRef is required for a tailnet-scoped access token");
    }
    PrivateConnectivityConfig config =
        configRepository.findByProviderNetworkRef(providerNetworkRef)
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 "No private connectivity binding for provider network " + providerNetworkRef));
    assertProviderConfigurationMatches(config);
    return orgBearer(exchangeChildTailnetAccessToken(config));
  }

  private String exchangeChildTailnetAccessToken(PrivateConnectivityConfig config) {
    if (StringUtils.isAnyBlank(config.getProviderTailnetOAuthClientId(), config.getProviderTailnetOAuthSecretRef())) {
      throw new ProviderNetworkException(
          "Private connectivity binding is missing the provider-created child-tailnet OAuth credentials");
    }
    PrivateConnectivityChildCredentialService.ChildCredential stored =
        childCredentialService.load(config.getAccountIdentifier(), config.getProviderTailnetOAuthSecretRef());
    if (!stored.clientId().equals(config.getProviderTailnetOAuthClientId())) {
      throw new ProviderNetworkException("Stored provider child OAuth identity does not match the durable binding");
    }
    return exchangeAccessToken(stored.clientId(), stored.clientSecret());
  }

  private void assertProviderConfigurationMatches(PrivateConnectivityConfig config) {
    String current = orgConfig.configurationFingerprint();
    if (config == null || StringUtils.isBlank(current)
        || !current.equals(config.getProviderConfigurationFingerprint())) {
      throw new ProviderNetworkException(
          "Tailscale organization configuration does not match the durable network binding");
    }
  }

  private String exchangeOrganizationAccessToken() {
    return exchangeAccessToken(orgConfig.getOrgOAuthClientId(), orgConfig.getOrgOAuthClientSecret());
  }

  private String exchangeAccessToken(String clientId, String clientSecret) {
    CachedAccessToken cached = accessTokenCache.get(clientId);
    long now = System.currentTimeMillis();
    if (cached != null && cached.expiresAtEpochMs() > now + ACCESS_TOKEN_EXPIRY_SKEW_MILLIS) {
      return cached.token();
    }
    FormBody.Builder form = new FormBody.Builder()
                                .add("grant_type", "client_credentials")
                                .add("client_id", clientId)
                                .add("client_secret", clientSecret);
    Request request = new Request.Builder()
                          .url(orgConfig.resolveApiBaseUrl() + "/api/v2/oauth/token")
                          .post(form.build())
                          .header("Accept", "application/json")
                          .build();
    ProviderNetworkException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_HTTP_ATTEMPTS; attempt++) {
      try (Response response = httpClient.newCall(request).execute()) {
        String body = response.body() == null ? "" : response.body().string();
        int statusCode = response.code();
        if (!response.isSuccessful()) {
          lastFailure = new ProviderNetworkException("Tailscale OAuth token exchange failed status=" + statusCode);
          boolean retryable = statusCode == 429 || statusCode >= 500 && statusCode <= 599;
          if (!retryable || attempt == MAX_HTTP_ATTEMPTS) {
            throw lastFailure;
          }
          sleepBeforeRetry(retryBackoffMs(attempt, retryAfterMs(response.header("Retry-After"))));
          continue;
        }

        final JsonNode json;
        try {
          json = MAPPER.readTree(body);
        } catch (IOException exception) {
          // OAuth responses can contain access tokens. Do not retain a parser cause that may embed
          // a source excerpt in diagnostics.
          throw new ProviderNetworkException("Tailscale OAuth token exchange returned malformed JSON");
        }
        String token = text(json, "access_token");
        if (StringUtils.isBlank(token)) {
          throw new ProviderNetworkException("Tailscale OAuth token exchange missing access_token");
        }
        JsonNode expiresInNode = json.get("expires_in");
        if (expiresInNode == null || !expiresInNode.isIntegralNumber() || !expiresInNode.canConvertToLong()
            || expiresInNode.asLong() <= 0L) {
          throw new ProviderNetworkException("Tailscale OAuth token exchange returned invalid expires_in");
        }
        long expiresIn = expiresInNode.asLong();
        final long expiresAtEpochMs;
        try {
          expiresAtEpochMs = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(expiresIn, 1000L));
        } catch (ArithmeticException exception) {
          throw new ProviderNetworkException("Tailscale OAuth token exchange returned invalid expires_in", exception);
        }
        // Cache the provider's actual expiry. Reads apply a 30-second safety skew above.
        accessTokenCache.put(clientId, new CachedAccessToken(token, expiresAtEpochMs));
        return token;
      } catch (IOException exception) {
        lastFailure = new ProviderNetworkException("Tailscale OAuth token exchange failed", exception);
        if (attempt == MAX_HTTP_ATTEMPTS) {
          throw lastFailure;
        }
        sleepBeforeRetry(retryBackoffMs(attempt, -1L));
      }
    }
    throw lastFailure != null ? lastFailure : new ProviderNetworkException("Tailscale OAuth token exchange failed");
  }

  private void requireOrgCredentials() {
    if (orgConfig == null || !orgConfig.isConfigured()) {
      throw new ProviderNetworkException(
          "PrivateConnectivityOrgConfig organization OAuth credentials and exact organization identity are required "
          + "with the official HTTPS API origin for Tailscale network operations");
    }
  }

  private String organizationTailnetsUrl() {
    return HttpUrl.get(orgConfig.resolveApiBaseUrl())
        .newBuilder()
        .addPathSegment("api")
        .addPathSegment("v2")
        .addPathSegment("organizations")
        .addPathSegment(TOKEN_RELATIVE_IDENTIFIER)
        .addPathSegment("tailnets")
        .build()
        .toString();
  }

  private String organizationTailnetsPageUrl(String cursor) {
    HttpUrl.Builder builder = HttpUrl.get(organizationTailnetsUrl())
                                  .newBuilder()
                                  .addQueryParameter("limit", String.valueOf(TAILNET_INVENTORY_PAGE_SIZE));
    if (StringUtils.isNotBlank(cursor)) {
      builder.addQueryParameter("cursor", cursor);
    }
    return builder.build().toString();
  }

  private String tailnetUrl(String providerNetworkRef, String suffix) {
    if (StringUtils.isBlank(providerNetworkRef)) {
      throw new InvalidRequestException("providerNetworkRef is required for a tailnet-scoped operation");
    }
    // The bearer token comes from the OAuth client created for this tailnet. Using '-' resolves the
    // resource relative to that child-scoped token.
    return orgConfig.resolveApiBaseUrl() + "/api/v2/tailnet/" + TOKEN_RELATIVE_IDENTIFIER + suffix;
  }

  private String explicitTailnetUrl(String providerNetworkRef) {
    if (StringUtils.isBlank(providerNetworkRef)) {
      throw new InvalidRequestException("providerNetworkRef is required for tailnet deletion");
    }
    return orgConfig.resolveApiBaseUrl() + "/api/v2/tailnet/" + providerNetworkRef;
  }

  private static String orgBearer(String token) {
    return "Bearer " + token;
  }

  private void assertCreateResponseOrganization(JsonNode response) {
    if (!expectedOrganizationId().equals(text(response, "orgId"))) {
      // The non-idempotent create may already have succeeded. Preserve the durable generated
      // recovery name so exact-name recovery can discover and remove it safely.
      throw unknownCreateResponse("Tailscale create network response has missing or unexpected orgId");
    }
  }

  private void assertInventoryOrganization(JsonNode tailnet) {
    if (!expectedOrganizationId().equals(text(tailnet, "orgId"))) {
      throw new ProviderNetworkException(
          "Tailscale network inventory has missing or unexpected orgId; refusing absence confirmation");
    }
  }

  private String expectedOrganizationId() {
    return orgConfig.getOrganizationIdentity().trim();
  }

  private JsonNode executeJson(String method, String url, String authorization, String jsonBody) {
    String body = executeRaw(method, url, authorization, jsonBody, false);
    if (StringUtils.isBlank(body)) {
      return MAPPER.createObjectNode();
    }
    try {
      return MAPPER.readTree(body);
    } catch (IOException e) {
      // Provider responses can contain credentials. Do not retain a parser cause that may embed a
      // source excerpt in diagnostics.
      throw new ProviderNetworkException("Unable to parse Tailscale response from " + sanitizeUrlForLog(url));
    }
  }

  private JsonNode executeNonIdempotentCreateJson(String method, String url, String authorization, String jsonBody) {
    String body = executeRaw(method, url, authorization, jsonBody, false, true);
    if (StringUtils.isBlank(body)) {
      return MAPPER.createObjectNode();
    }
    try {
      return MAPPER.readTree(body);
    } catch (IOException exception) {
      throw new ProviderCreateException(
          "Unable to parse Tailscale create response from " + sanitizeUrlForLog(url), CreateOutcome.OUTCOME_UNKNOWN);
    }
  }

  private String executeRaw(
      String method, String url, String authorization, String jsonBody, boolean treatNotFoundAsSuccess) {
    return executeRaw(method, url, authorization, jsonBody, treatNotFoundAsSuccess, false);
  }

  private String executeRaw(String method, String url, String authorization, String jsonBody,
      boolean treatNotFoundAsSuccess, boolean nonIdempotentCreate) {
    Request request = buildRequest(method, url, authorization, jsonBody);
    // Retry rate-limits and 5xx only for idempotent verbs. A create response can be lost after the
    // provider commits the resource, so non-idempotent POSTs are never retried here.
    boolean idempotent = "GET".equals(method) || "DELETE".equals(method);
    ProviderNetworkException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_HTTP_ATTEMPTS; attempt++) {
      try (Response response = httpClient.newCall(request).execute()) {
        String body = response.body() == null ? "" : response.body().string();
        int code = response.code();
        if (treatNotFoundAsSuccess && code == 404) {
          return body;
        }
        if (response.isSuccessful()) {
          return body;
        }
        // Never attach response bodies — /keys and /oauth/token can echo secret material.
        String safeUrl = sanitizeUrlForLog(url);
        boolean retryable = !nonIdempotentCreate && ((code == 429) || (idempotent && code >= 500 && code <= 599));
        if (nonIdempotentCreate) {
          CreateOutcome outcome =
              isDefiniteCreateRejection(code) ? CreateOutcome.DEFINITELY_NOT_CREATED : CreateOutcome.OUTCOME_UNKNOWN;
          throw new ProviderCreateException(
              "Tailscale API " + method + " " + safeUrl + " failed status=" + code, outcome);
        }
        lastFailure =
            new ProviderNetworkException("Tailscale API " + method + " " + safeUrl + " failed status=" + code);
        if (!retryable || attempt == MAX_HTTP_ATTEMPTS) {
          log.debug("Private Connectivity provider API failed method={} url={} status={} attempt={}", method, safeUrl,
              code, attempt);
          throw lastFailure;
        }
        long backoffMs = retryBackoffMs(attempt, retryAfterMs(response.header("Retry-After")));
        log.debug("Private Connectivity provider API retrying method={} url={} status={} attempt={}/{} backoffMs={}",
            method, safeUrl, code, attempt, MAX_HTTP_ATTEMPTS, backoffMs);
        sleepBeforeRetry(backoffMs);
      } catch (IOException e) {
        if (nonIdempotentCreate) {
          throw new ProviderCreateException(
              "Tailscale API " + method + " " + sanitizeUrlForLog(url) + " failed", CreateOutcome.OUTCOME_UNKNOWN, e);
        }
        lastFailure =
            new ProviderNetworkException("Tailscale API " + method + " " + sanitizeUrlForLog(url) + " failed", e);
        if (!idempotent || attempt == MAX_HTTP_ATTEMPTS) {
          throw lastFailure;
        }
        long backoffMs = retryBackoffMs(attempt, -1L);
        log.debug("Private Connectivity provider API transport retry method={} url={} attempt={}/{} backoffMs={}",
            method, sanitizeUrlForLog(url), attempt, MAX_HTTP_ATTEMPTS, backoffMs);
        sleepBeforeRetry(backoffMs);
      }
    }
    // Loop always returns or throws above; this satisfies the compiler for the final fallthrough.
    throw lastFailure != null
        ? lastFailure
        : new ProviderNetworkException("Tailscale API " + method + " " + sanitizeUrlForLog(url) + " failed");
  }

  private static ProviderCreateException unknownCreateResponse(String message) {
    return new ProviderCreateException(message, CreateOutcome.OUTCOME_UNKNOWN);
  }

  private static boolean isDefiniteCreateRejection(int statusCode) {
    return switch (statusCode) {
      case 400, 401, 403, 404, 405, 406, 411, 413, 414, 415, 422 -> true;
      default -> false;
    };
  }

  private static Request buildRequest(String method, String url, String authorization, String jsonBody) {
    Request.Builder builder =
        new Request.Builder().url(url).header("Authorization", authorization).header("Accept", "application/json");
    switch (method) {
      case "GET" -> builder.get();
      case "DELETE" -> builder.delete();
      case "POST" -> {
        RequestBody requestBody = jsonBody == null
            ? RequestBody.create(new byte[0], JSON)
            :
        RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8), JSON);
        builder.post(requestBody);
    }
    default -> throw new ProviderNetworkException("Unsupported HTTP method " + method);
  }
  return builder.build();
}

/** Exponential backoff with jitter, capped, honoring Retry-After when the server provides it. */
private static long retryBackoffMs(int attempt, long retryAfterMs) {
  if (retryAfterMs >= 0) {
    return Math.min(retryAfterMs, MAX_BACKOFF_MS);
  }
  long base = Math.min(BASE_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
  long jitter = ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS + 1);
  return Math.min(base + jitter, MAX_BACKOFF_MS);
}

/** Parses the Retry-After header (delta-seconds form only); returns -1 when absent/invalid. */
private static long retryAfterMs(String retryAfterHeader) {
  if (StringUtils.isBlank(retryAfterHeader)) {
    return -1L;
  }
  try {
    long seconds = Long.parseLong(retryAfterHeader.trim());
    if (seconds < 0) {
      return -1L;
    }
    // Clamp before converting to ms: the backoff is capped at MAX_BACKOFF_MS anyway, so a huge or
    // overflowing Retry-After is equivalent to the cap and must not throw ArithmeticException.
    if (seconds >= MAX_BACKOFF_MS / 1000L) {
      return MAX_BACKOFF_MS;
    }
    return seconds * 1000L;
  } catch (NumberFormatException e) {
    return -1L;
  }
}

private static void sleepBeforeRetry(long backoffMs) {
  try {
    Thread.sleep(backoffMs);
  } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new ProviderNetworkException("Interrupted while backing off before Tailscale API retry", e);
  }
}

private static boolean hasValidationProblems(JsonNode response) {
  // Tailscale documents an empty response body (normalized to {}) or {} as success. Any other
  // successful response contains validation errors/warnings or is malformed and must fail closed.
  return response == null || !response.isObject() || response.size() != 0;
}

private static String text(JsonNode node, String field) {
  if (node == null || node.isMissingNode() || node.isNull()) {
    return null;
  }
  JsonNode value = node.path(field);
  return value.isTextual() ? value.asText() : null;
}

private static String firstNonBlank(String... values) {
  if (values == null) {
    return null;
  }
  for (String value : values) {
    if (StringUtils.isNotBlank(value)) {
      return value;
    }
  }
  return null;
}

private static long parseExpiresAtEpochMs(JsonNode response, long fallbackExpiresAt) {
  JsonNode value = response.path("expires");
  if (value.isNumber()) {
    long timestamp = value.asLong();
    return timestamp < 100_000_000_000L ? Math.multiplyExact(timestamp, 1000L) : timestamp;
  }
  if (value.isTextual() && StringUtils.isNotBlank(value.asText())) {
    try {
      return Instant.parse(value.asText()).toEpochMilli();
    } catch (DateTimeParseException ignored) {
      // The request duration remains the authoritative fallback when the optional provider
      // expiration timestamp is absent or malformed.
    }
  }
  return fallbackExpiresAt;
}

/** Path-only URL for exceptions/logs — strips query strings that might carry tokens. */
private static String sanitizeUrlForLog(String url) {
  if (url == null) {
    return "";
  }
  int query = url.indexOf('?');
  return query < 0 ? url : url.substring(0, query);
}

private record CachedAccessToken(String token, long expiresAtEpochMs) {
  @Override
  public String toString() {
    return "CachedAccessToken[token=<redacted>, expiresAtEpochMs=" + expiresAtEpochMs + "]";
  }
}
}
