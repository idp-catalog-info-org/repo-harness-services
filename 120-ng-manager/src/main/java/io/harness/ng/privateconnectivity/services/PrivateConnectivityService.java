/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.services;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityAdminResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityHelperCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityInternalDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupResponseDTO;

/**
 * Private connectivity service contract.
 *
 * Customer API verbs stay stable; bind/release is implemented by CreateOnceNetworkProvisioner.
 */
@OwnedBy(CI)
public interface PrivateConnectivityService {
  /**
   * Ensure this account has a provisioned private network and initial reusable credential.
   */
  PrivateConnectivitySetupResponseDTO setup(String accountIdentifier, PrivateConnectivitySetupRequestDTO request);

  /** Public state; no credentials or provider IDs. Allowed when FF is off. */
  PrivateConnectivityResponseDTO get(String accountIdentifier);

  /** Harness-operator sanitized binding, provider, helper, and recovery state. */
  PrivateConnectivityAdminResponseDTO getAdmin(String accountIdentifier);

  /**
   * Replace routes/domains/DNS on the bound network. Mutations are serialized by the account lock;
   * this does not rotate credentials.
   */
  PrivateConnectivityResponseDTO updateConfig(String accountIdentifier, PrivateConnectivitySetupRequestDTO request);

  /** Reconciler/admin recovery for a durable desired-state update. */
  boolean reconcileConfigIfStuck(String accountIdentifier);

  /** Admin recovery entry point for the operation recorded on the account. */
  boolean resume(String accountIdentifier);

  /**
   * Mint a new reusable, preauthorized 90-day customer-appliance credential. Every call creates an
   * independent credential and retains all earlier key IDs for release-time revocation.
   */
  PrivateConnectivityCredentialDTO getCredential(String accountIdentifier);

  /** Harness-operator only: mint a tracked one-time Tailscale credential for the manually deployed helper. */
  PrivateConnectivityHelperCredentialDTO getHelperCredential(String accountIdentifier);

  /**
   * Harness-operator only: release this account's private connectivity asynchronously.
   * Returns {@code true} only when async sanitizer work was scheduled (config present and
   * prepared); {@code false} when there is nothing to release.
   * Always runs ReleaseSanitizer when scheduled.
   * Releases {@code PROVISIONED} networks (intentional for admin DELETE).
   */
  boolean release(String accountIdentifier);

  /**
   * Reconciler-only recovery: release only if the account is still stuck in a transient /
   * partial state under the account lock — {@code PROVISIONING} (stale), {@code RELEASING},
   * {@code ERROR} with a live providerNetworkRef, or an outcome-unknown provider create with its
   * exact generated recovery name still present.
   * Never tears down a live {@code PROVISIONED} network (avoids TOCTOU after a hung setup completes).
   */
  boolean releaseIfStuck(String accountIdentifier);

  /** CI-internal inject-eligible state. Never returns provider credentials or provider IDs. */
  PrivateConnectivityInternalDTO getInternal(String accountIdentifier);
}
