/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.harnessid;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.dto.UserPrincipal;

import java.util.Map;

/**
 * Service for generating HarnessID signed user tokens for IDP external proxy requests.
 * Implements the two-step flow: workload registration → OIDC token generation.
 */
@OwnedBy(IDP)
public interface HarnessIdTokenService {
  /**
   * Generates a signed OIDC ID token for the given user and proxy endpoint.
   * This token can be verified by external systems to authenticate the IDP user.
   *
   * Flow:
   * 1. Register workload with HarnessID (gRPC) → get workload token
   * 2. Exchange workload token for OIDC ID token (REST)
   *
   * @param accountId Account identifier
   * @param userPrincipal User making the request
   * @param endpoint Proxy endpoint name
   * @param targetHost Target external system host
   * @param audience Token audience (typically the target host or a standard value)
   * @param customClaims Additional custom claims to include
   * @return Signed OIDC ID token (JWT)
   * @throws io.harness.exception.InvalidRequestException if token generation fails
   */
  String generateSignedUserToken(String accountId, UserPrincipal userPrincipal, String endpoint, String targetHost,
      String audience, Map<String, String> customClaims);

  /**
   * Check if HarnessID integration is enabled and configured.
   *
   * @return true if HarnessID client is configured and ready
   */
  boolean isEnabled();
}
