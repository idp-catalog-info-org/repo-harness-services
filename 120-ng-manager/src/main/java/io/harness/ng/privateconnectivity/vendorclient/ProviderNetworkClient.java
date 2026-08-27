/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.vendorclient;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Map;

/**
 * Provider boundary for remote network operations. The customer configuration and state contract
 * is provider-neutral; the Phase 1 implementation is Tailscale-specific.
 *
 * Organization credentials are deployment-owned configuration. Provider-created child-tailnet
 * credentials are returned once by the provider and must be persisted by the binding owner in its
 * encrypted secret storage.
 */
@OwnedBy(CI)
public interface ProviderNetworkClient {
  /**
   * Create a provider network using the generated recovery name
   * {@code pc-<provider-safe-account-id>-<five-digit-number>}. Account display names and other
   * customer metadata must not be included.
   *
   * @return create result containing the opaque provider network reference and the one-time
   *     child-tailnet OAuth credentials
   */
  NetworkCreateResult createNetwork(String networkName);

  /**
   * Resolve networks by the exact persisted recovery name after a lost create response.
   * Implementations must not perform fuzzy matching.
   */
  List<RecoverableNetwork> findNetworksByName(String networkName);

  /** Return whether the exact provider network still exists in the authoritative provider inventory. */
  boolean networkExists(String providerNetworkRef);

  /**
   * Apply (replace) the full network policy / ACL.
   * Validate first (validatePolicy); do NOT apply if validate fails.
   */
  void applyPolicy(String providerNetworkRef, NetworkPolicy policy);

  /**
   * Validate a candidate policy. HTTP 200 does NOT mean valid — inspect response body.
   *
   * @throws PolicyValidationException if policy is semantically invalid
   */
  void validatePolicy(String providerNetworkRef, NetworkPolicy policy);

  /** Replace the complete provider DNS configuration. */
  void configureDns(String providerNetworkRef, DnsConfig dnsConfig);

  /** Create a Workload Identity Federation credential for build VMs. */
  WifCredentialInfo createWif(String providerNetworkRef, WifConfig wifConfig, String opaqueOperationDescription);

  /**
   * Mint a reusable parent-tagged auth key for customer appliances. A customer may use the key to
   * enroll subnet routers, App Connectors, combined appliances, and their replicas. Persist only
   * the key ID for later revocation; the auth-key value is returned once and must never be stored.
   * NEVER give customers the bootstrap all-scope OAuth admin client.
   */
  JoinCredentialInfo createJoinCredential(
      String providerNetworkRef, List<String> enrollmentTags, String opaqueOperationDescription);

  /** Mint a short-lived, single-use key for the manually deployed Harness helper. */
  JoinCredentialInfo createHelperJoinCredential(
      String providerNetworkRef, List<String> roleTags, String opaqueOperationDescription);

  /**
   * Resolve an outcome-unknown auth-key create by its exact opaque description. If exactly one
   * key exists, revoke it and confirm absence; zero matches is already clean. Multiple matches or
   * an unconfirmed deletion must fail closed. This never returns a raw key.
   */
  void reconcileCredentialCreate(String providerNetworkRef, String opaqueOperationDescription);

  // ---- Release / cleanup hooks (used by ReleaseSanitizer) ----

  /**
   * Revoke all join credentials (auth keys) for a network.
   * Must be called during ReleaseSanitizer before destroy.
   * Failure = stay RELEASING/ERROR; never proceed to destroy.
   */
  void revokeJoinCredentials(String providerNetworkRef, List<String> joinKeyIds);

  /**
   * Delete the WIF credential for a network.
   * Removes trust so no new device can join as a WIF-authorized client.
   */
  void deleteWifCredential(String providerNetworkRef, String wifCredentialId);

  /** Force-delete all devices from a network, including offline customer appliances. */
  void deleteDevices(String providerNetworkRef, List<String> deviceIds);

  List<String> listDeviceIds(String providerNetworkRef);

  /**
   * Destroy the network and confirm that its exact provider reference is absent before returning.
   * Must be idempotent: retries may call it after a prior call succeeded but the Harness DB update
   * failed.
   */
  void deleteNetwork(String providerNetworkRef);

  /**
   * Destroy a network using credentials retained in memory from the create response. This overload
   * is required when the first Mongo persistence attempt fails and the binding is not yet
   * recoverable through the repository.
   */
  void deleteNetwork(String providerNetworkRef, ProviderAdminCredential credential);

  // ---- DTOs ----

  record NetworkCreateResult(String providerNetworkRef, ProviderAdminCredential adminCredential) {
    public NetworkCreateResult {
      if (providerNetworkRef == null || providerNetworkRef.isBlank() || adminCredential == null) {
        throw new IllegalArgumentException("provider network and child OAuth credentials are required");
      }
    }

    @Override
    public String toString() {
      return "NetworkCreateResult[providerNetworkRef=" + providerNetworkRef + ", adminCredential=<redacted>]";
    }
  }

  record ProviderAdminCredential(String clientId, String clientSecret) {
    public ProviderAdminCredential {
      if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
        throw new IllegalArgumentException("provider child OAuth client ID and secret are required");
      }
    }

    @Override
    public String toString() {
      return "ProviderAdminCredential[clientId=" + clientId + ", clientSecret=<redacted>]";
    }
  }

  record RecoverableNetwork(String providerNetworkRef) {}

  record NetworkPolicy(String aclJson) {}

  record DnsConfig(Map<String, List<String>> splitDnsDomains, boolean enabled) {
    public DnsConfig(Map<String, List<String>> splitDnsDomains) {
      this(splitDnsDomains, true);
    }

    public static DnsConfig cleared() {
      return new DnsConfig(Map.of(), false);
    }
  }

  /**
   * WIF trust configuration. subject must match the JWT {@code sub} minted for build VMs
   * (currently bare accountId — keep mint and WIF registration aligned end-to-end).
   */
  record WifConfig(String issuer, String subject, String accountId, String[] tags) {}

  record WifCredentialInfo(String credentialId, String clientId, String audience) {}

  /**
   * Provider join-key result. Customer credentials are reusable while helper credentials are
   * one-off; both are preauthorized and their raw values are returned once. Persist only keyId.
   * expiresAt is epoch milliseconds.
   */
  record JoinCredentialInfo(String keyId, String authKey, long expiresAt, boolean reusable, boolean preauthorized) {
    @Override
    public String toString() {
      return "JoinCredentialInfo[keyId=" + keyId + ", authKey=<redacted>, expiresAt=" + expiresAt
          + ", reusable=" + reusable + ", preauthorized=" + preauthorized + "]";
    }
  }

  class PolicyValidationException extends RuntimeException {
    public PolicyValidationException(String message) {
      super(message);
    }
  }

  class ProviderNetworkException extends RuntimeException {
    public ProviderNetworkException(String message) {
      super(message);
    }

    public ProviderNetworkException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  enum CreateOutcome { DEFINITELY_NOT_CREATED, OUTCOME_UNKNOWN }

  class ProviderCreateException extends ProviderNetworkException {
    private final CreateOutcome outcome;

    public ProviderCreateException(String message, CreateOutcome outcome) {
      super(message);
      this.outcome = outcome;
    }

    public ProviderCreateException(String message, CreateOutcome outcome, Throwable cause) {
      super(message, cause);
      this.outcome = outcome;
    }

    public CreateOutcome getOutcome() {
      return outcome;
    }
  }
}
