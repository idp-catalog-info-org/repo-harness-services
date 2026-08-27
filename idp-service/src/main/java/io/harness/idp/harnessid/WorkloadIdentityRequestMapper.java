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

import com.harness.harnessid.proto.workload.v1.IdpExternalProxyContext;
import com.harness.harnessid.proto.workload.v1.WorkloadContext;
import com.harness.harnessid.proto.workload.v1.WorkloadRegistrationRequest;
import com.harness.harnessid.proto.workload.v1.WorkloadType;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Maps IDP external proxy requests into HarnessID {@link WorkloadRegistrationRequest} proto.
 * Used for generating signed user tokens when IDP proxies requests to external systems.
 *
 * <p>Mirrors the pattern used by {@code io.harness.steps.workloadidentity.WorkloadIdentityRequestMapper}
 * (878-ng-common-utilities) for step workload identities:
 * <ul>
 *   <li>{@code iss} is fixed server-side by HarnessID ({@code <baseUrl>/workload/identity}) and is
 *       never settable from the client - there is no issuer field on the proto.</li>
 *   <li>{@code sub} is a literal value set via {@link WorkloadRegistrationRequest.Builder#setSubTemplate},
 *       not resolved/templated server-side (see WorkloadRegistrationRequestMapperTest for step identities).</li>
 *   <li>{@code email}/{@code account_id} are not injected automatically - they must be added explicitly
 *       via {@code custom_claims} to appear as top-level JWT claims.</li>
 *   <li>{@code aud} is not part of registration; it is supplied separately to
 *       {@code HarnessIdClientService#generateIdToken(workloadToken, audience, tokenMode)}.</li>
 * </ul>
 */
@OwnedBy(IDP)
@UtilityClass
public class WorkloadIdentityRequestMapper {
  private static final String CLAIM_ACCOUNT_ID = "account_id";

  /**
   * Builds a workload registration request for IDP external proxy with signed user context.
   *
   * @param accountId Account identifier
   * @param userPrincipal User making the request
   * @param endpoint Proxy endpoint name
   * @param targetHost Target external system host (used as audience at token-generation time)
   * @param customClaims Additional custom claims to include in the token
   * @return WorkloadRegistrationRequest for HarnessID
   */
  public static WorkloadRegistrationRequest buildExternalProxyRequest(String accountId, UserPrincipal userPrincipal,
      String endpoint, String targetHost, Map<String, String> customClaims) {
    String email = emptyIfNull(userPrincipal.getEmail());

    // Minimal context with only triggered_by - all other claims come from top-level fields or custom claims
    IdpExternalProxyContext proxyContext = IdpExternalProxyContext.newBuilder().setTriggeredBy(email).build();

    // sub_template: template string with placeholders that HarnessID resolves from claims
    String subject = "account/{account_id}:triggeredby/{triggered_by}";

    Map<String, String> claims = new HashMap<>();
    if (customClaims != null) {
      claims.putAll(customClaims);
    }

    return WorkloadRegistrationRequest.newBuilder()
        .setWorkloadType(WorkloadType.WORKLOAD_TYPE_IDP_EXTERNAL_PROXY)
        .setAccountId(emptyIfNull(accountId))
        .setSubTemplate(subject)
        .setWorkloadContext(WorkloadContext.newBuilder().setIdpExternalProxy(proxyContext).build())
        .putAllCustomClaims(claims)
        .build();
  }

  private static String emptyIfNull(String value) {
    return StringUtils.defaultString(value);
  }
}
