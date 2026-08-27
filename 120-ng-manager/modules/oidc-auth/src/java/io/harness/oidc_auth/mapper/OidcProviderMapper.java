/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.mapper;

import io.harness.oidc_auth.entity.AuthorizationConfig;
import io.harness.oidc_auth.entity.ClientIdConfiguration;
import io.harness.oidc_auth.entity.JitConfiguration;
import io.harness.oidc_auth.entity.OidcProviderSettings;
import io.harness.spec.server.ng.v1.model.OidcAuthorizationConfigDTO;
import io.harness.spec.server.ng.v1.model.OidcClientConfigDTO;
import io.harness.spec.server.ng.v1.model.OidcJitConfigDTO;
import io.harness.spec.server.ng.v1.model.OidcProviderDTO;

import software.wings.beans.sso.SSOType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OidcProviderMapper {
  public static OidcProviderSettings getOidcProvider(String accountIdentifier, OidcProviderDTO oidcProviderDTO) {
    return OidcProviderSettings.builder()
        .identifier(oidcProviderDTO.getIdentifier())
        .name(oidcProviderDTO.getName())
        .accountIdentifier(accountIdentifier)
        .issuer(oidcProviderDTO.getIssuer())
        .responseType(OidcProviderDTO.ResponseTypeEnum.CODE.value())
        .discovery(Boolean.TRUE.equals(oidcProviderDTO.isDiscovery()))
        .pkce(Boolean.TRUE.equals(oidcProviderDTO.isPkce()))
        .scopes(getOidcScopes(oidcProviderDTO))
        .sendScopeToTokenEndpoint(Boolean.TRUE.equals(oidcProviderDTO.isSendScopeToTokenEndpoint()))
        .uidField(oidcProviderDTO.getUidField())
        .ssoType(SSOType.OIDC)
        .clientIdConfiguration(oidcProviderDTO.getClientConfig() == null
                ? null
                : ClientIdConfiguration.builder()
                      .identifier(oidcProviderDTO.getClientConfig().getIdentifier())
                      .redirectUrl(oidcProviderDTO.getClientConfig().getRedirectUri())
                      .jwksUri(oidcProviderDTO.getClientConfig().getJwksUri())
                      .authorizationEndpoint(oidcProviderDTO.getClientConfig().getAuthorizationEndpoint())
                      .tokenEndpoint(oidcProviderDTO.getClientConfig().getTokenEndpoint())
                      .userInfoEndpoint(oidcProviderDTO.getClientConfig().getUserinfoEndpoint())
                      .secretRef(oidcProviderDTO.getClientConfig().getSecretRef())
                      .build())
        .jitConfiguration(oidcProviderDTO.getJitConfig() == null
                ? null
                : JitConfiguration.builder()
                      .enabled(Boolean.TRUE.equals(oidcProviderDTO.getJitConfig().isEnabled()))
                      .claimKey(oidcProviderDTO.getJitConfig().getClaimKey())
                      .claimValue(oidcProviderDTO.getJitConfig().getClaimValue())
                      .build())
        .authorizationConfig(oidcProviderDTO.getAuthorizationConfig() == null
                ? null
                : AuthorizationConfig.builder()
                      .enabled(Boolean.TRUE.equals(oidcProviderDTO.getAuthorizationConfig().isAuthorizationEnabled()))
                      .groupClaim(oidcProviderDTO.getAuthorizationConfig().getGroupClaim())
                      .build())
        .build();
  }

  private static List<String> getOidcScopes(OidcProviderDTO oidcProviderDTO) {
    Set<String> scopes = new HashSet<>(Arrays.asList("openid", "profile", "email"));

    for (String scope : oidcProviderDTO.getScope()) {
      scopes.add(scope);
    }

    return scopes.stream().toList();
  }

  public static OidcProviderDTO getOidcProviderDTO(OidcProviderSettings savedOidcProviderSettings) {
    if (savedOidcProviderSettings == null) {
      return null;
    }

    return new OidcProviderDTO()
        .identifier(savedOidcProviderSettings.getIdentifier())
        .name(savedOidcProviderSettings.getName())
        .issuer(savedOidcProviderSettings.getIssuer())
        .responseType(OidcProviderDTO.ResponseTypeEnum.fromValue(savedOidcProviderSettings.getResponseType()))
        .discovery(savedOidcProviderSettings.isDiscovery())
        .pkce(savedOidcProviderSettings.isPkce())
        .sendScopeToTokenEndpoint(savedOidcProviderSettings.isSendScopeToTokenEndpoint())
        .uidField(savedOidcProviderSettings.getUidField())
        .scope(savedOidcProviderSettings.getScopes())
        .clientConfig(savedOidcProviderSettings.getClientIdConfiguration() != null
                ? new OidcClientConfigDTO()
                      .identifier(savedOidcProviderSettings.getClientIdConfiguration().getIdentifier())
                      .redirectUri(savedOidcProviderSettings.getClientIdConfiguration().getRedirectUrl())
                      .jwksUri(savedOidcProviderSettings.getClientIdConfiguration().getJwksUri())
                      .authorizationEndpoint(
                          savedOidcProviderSettings.getClientIdConfiguration().getAuthorizationEndpoint())
                      .tokenEndpoint(savedOidcProviderSettings.getClientIdConfiguration().getTokenEndpoint())
                      .userinfoEndpoint(savedOidcProviderSettings.getClientIdConfiguration().getUserInfoEndpoint())
                      .secretRef(savedOidcProviderSettings.getClientIdConfiguration().getSecretRef())
                : null)
        .jitConfig(savedOidcProviderSettings.getJitConfiguration() != null
                ? new OidcJitConfigDTO()
                      .enabled(savedOidcProviderSettings.getJitConfiguration().isEnabled())
                      .claimKey(savedOidcProviderSettings.getJitConfiguration().getClaimKey())
                      .claimValue(savedOidcProviderSettings.getJitConfiguration().getClaimValue())
                : null)
        .authorizationConfig(savedOidcProviderSettings.getAuthorizationConfig() != null
                ? new OidcAuthorizationConfigDTO()
                      .authorizationEnabled(savedOidcProviderSettings.getAuthorizationConfig().isEnabled())
                      .groupClaim(savedOidcProviderSettings.getAuthorizationConfig().getGroupClaim())
                : null)
        .created(savedOidcProviderSettings.getCreatedAt())
        .updated(savedOidcProviderSettings.getLastModifiedDate());
  }
}
