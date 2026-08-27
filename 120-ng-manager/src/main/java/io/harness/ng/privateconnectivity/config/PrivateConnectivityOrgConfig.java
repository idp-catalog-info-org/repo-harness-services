/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.config;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

/**
 * Ops-owned Tailscale organization configuration for Harness Cloud Private Connectivity.
 *
 * The organization OAuth client creates and recovers per-account tailnets. Its secret must live in
 * the platform/ops config secret store — never in a customer secret manager. Short-lived API
 * access tokens are obtained at runtime and are never configured or persisted.
 */
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CI)
public class PrivateConnectivityOrgConfig {
  public static final String DEFAULT_API_BASE_URL = "https://api.tailscale.com";

  /** OAuth client ID in the creating organization with the provider's documented {@code all} scope. */
  @JsonProperty("orgOAuthClientId") private String orgOAuthClientId;

  /** OAuth client secret paired with {@link #orgOAuthClientId}. It is never logged. */
  @JsonProperty(value = "orgOAuthClientSecret", access = JsonProperty.Access.WRITE_ONLY)
  @ConfigSecret
  @ToString.Exclude
  private String orgOAuthClientSecret;

  /**
   * Exact, stable Tailscale organization ID (for example {@code o123456CNTRL}). Organization API
   * paths use the token-relative {@code -}; the client validates this exact ID in every returned
   * tailnet record. It remains unchanged across OAuth-client rotations and prevents a deployment
   * pointed at another organization from operating on Mongo bindings created under the prior
   * configuration.
   */
  @JsonProperty("organizationIdentity") private String organizationIdentity;

  @JsonIgnore
  public boolean isConfigured() {
    return StringUtils.isNoneBlank(orgOAuthClientId, orgOAuthClientSecret, organizationIdentity);
  }

  public String resolveApiBaseUrl() {
    return DEFAULT_API_BASE_URL;
  }

  @JsonIgnore
  public String configurationFingerprint() {
    if (!isConfigured()) {
      return null;
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
          (DEFAULT_API_BASE_URL + ":" + organizationIdentity.trim()).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required for Private Connectivity configuration", exception);
    }
  }
}
