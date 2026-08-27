/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.harnessid;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.harnessid.client.HarnessIdClientService;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationRequest;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Implementation of HarnessIdTokenService for IDP external proxy signed user tokens.
 * Orchestrates the two-step HarnessID flow: workload registration → OIDC token generation.
 */
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(IDP)
@Slf4j
@Singleton
public class HarnessIdTokenServiceImpl implements HarnessIdTokenService {
  private static final String DEFAULT_AUDIENCE = "harness-idp";
  private static final String TOKEN_MODE_STANDARD = "STANDARD";
  private static final String USER_TOKEN_HEADER = "X-Harness-IDP-User-Token";

  private final HarnessIdClientService harnessIdClientService;

  @Inject
  public HarnessIdTokenServiceImpl(HarnessIdClientService harnessIdClientService) {
    this.harnessIdClientService = harnessIdClientService;
  }

  @Override
  public String generateSignedUserToken(String accountId, UserPrincipal userPrincipal, String endpoint,
      String targetHost, String audience, Map<String, String> customClaims) {
    if (!isEnabled()) {
      log.warn("HarnessID is not enabled. Skipping signed user token generation for endpoint: {}", endpoint);
      return null;
    }

    if (userPrincipal == null) {
      throw new InvalidRequestException("User principal is required for signed user token generation");
    }

    try {
      log.debug("Generating signed user token for user: {}, endpoint: {}, target: {}", userPrincipal.getEmail(),
          endpoint, targetHost);

      WorkloadRegistrationRequest registrationRequest = WorkloadIdentityRequestMapper.buildExternalProxyRequest(
          accountId, userPrincipal, endpoint, targetHost, customClaims);

      WorkloadRegistrationResponse registrationResponse = harnessIdClientService.register(registrationRequest);

      if (registrationResponse == null || StringUtils.isBlank(registrationResponse.getWorkloadToken())) {
        throw new InvalidRequestException(
            "HarnessID workload registration failed: empty workload token for endpoint: " + endpoint);
      }

      String workloadToken = registrationResponse.getWorkloadToken();
      log.debug("Workload token obtained for endpoint: {}, exchanging for OIDC token", endpoint);

      String effectiveAudience = StringUtils.isNotBlank(audience) ? audience : DEFAULT_AUDIENCE;
      String idToken = harnessIdClientService.generateIdToken(workloadToken, effectiveAudience, TOKEN_MODE_STANDARD);

      if (StringUtils.isBlank(idToken)) {
        throw new InvalidRequestException("HarnessID returned empty OIDC token for endpoint: " + endpoint);
      }

      log.info(
          "Successfully generated signed user token for user: {}, endpoint: {}", userPrincipal.getEmail(), endpoint);
      return idToken;

    } catch (Exception ex) {
      log.error("Failed to generate signed user token for user: {}, endpoint: {}, target: {}", userPrincipal.getEmail(),
          endpoint, targetHost, ex);
      throw new InvalidRequestException(
          "Failed to generate signed user token for endpoint: " + endpoint + ". Error: " + ex.getMessage(), ex);
    }
  }

  @Override
  public boolean isEnabled() {
    return harnessIdClientService.isEnabled();
  }
}
