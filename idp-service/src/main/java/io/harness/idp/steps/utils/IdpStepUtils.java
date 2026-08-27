/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.steps.utils;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.BACKSTAGE_BASE_URL_LOCAL_VALUE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.accountdetails.AccountsDetailsCache;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Map;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
public class IdpStepUtils {
  @Inject(optional = true) BackstageResourceClient backstageResourceClient;
  @Inject(optional = true) @Named("backstageHttpClientConfig") ServiceHttpClientConfig backstageClientConfig;
  @Inject(optional = true) @Named("idpServiceSecret") String idpServiceSecret;
  @Inject(optional = true) AccountsDetailsCache accountsDetailsCache;
  @Inject(optional = true) ServiceTokenGenerator tokenGenerator;
  public static final String X_SOURCE_PRINCIPAL = "X_SOURCE_PRINCIPAL";
  public static final String AUTHORIZATION = "AUTHORIZATION";
  private static final String SPACE = " ";

  public String getUserSpecificToken(String accountId, String email, String uuid) {
    Object response = getGeneralResponse(backstageResourceClient.getUserSpecificToken(accountId, email, uuid));
    JSONObject backstageIdentity = GsonUtils.getJSONObjectFromObject(response, "backstageIdentity");
    return (String) backstageIdentity.get("token");
  }

  public Map<String, String> getTokenWithUserContext(String accountId, String email, String username, String uuid) {
    String authorizationToken = tokenGenerator.getServiceTokenWithDuration(
        idpServiceSecret, Duration.ofHours(1), new ServicePrincipal(IDP_SERVICE.getServiceId()));
    UserPrincipal userPrincipal = new UserPrincipal(uuid, email, username, accountId);
    String sourcePrincipalToken =
        tokenGenerator.getServiceTokenWithDuration(idpServiceSecret, Duration.ofHours(1), userPrincipal);
    return Map.of(AUTHORIZATION, IDP_SERVICE.getServiceId() + SPACE + authorizationToken, X_SOURCE_PRINCIPAL,
        IDP_SERVICE.getServiceId() + SPACE + sourcePrincipalToken);
  }

  public String getBackstageBaseUrl(String accountIdentifier) {
    String backstageBaseUrl = backstageClientConfig.getBaseUrl();

    if (backstageBaseUrl.equals(BACKSTAGE_BASE_URL_LOCAL_VALUE)) {
      String subdomainUrl = accountsDetailsCache.get(accountIdentifier).getSubdomainUrl();
      if (!isEmpty(subdomainUrl)) {
        if (!subdomainUrl.endsWith("/")) {
          subdomainUrl += "/";
        }
        backstageBaseUrl = subdomainUrl;
      }
    }
    return backstageBaseUrl;
  }
}
