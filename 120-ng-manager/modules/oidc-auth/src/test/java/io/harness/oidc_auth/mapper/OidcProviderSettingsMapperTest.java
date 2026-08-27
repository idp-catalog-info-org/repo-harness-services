/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.oidc_auth.entity.ClientIdConfiguration;
import io.harness.oidc_auth.entity.JitConfiguration;
import io.harness.oidc_auth.entity.OidcProviderSettings;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.OidcClientConfigDTO;
import io.harness.spec.server.ng.v1.model.OidcJitConfigDTO;
import io.harness.spec.server.ng.v1.model.OidcProviderDTO;

import software.wings.beans.sso.SSOType;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class OidcProviderSettingsMapperTest extends CategoryTest {
  @Inject private final String ACCOUNT_IDENTIFIER = "test-account";

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProvider_withValidInput() {
    OidcClientConfigDTO clientConfigDTO = new OidcClientConfigDTO()
                                              .identifier("client-id")
                                              .redirectUri("http://redirect.url")
                                              .jwksUri("http://jwks.uri")
                                              .authorizationEndpoint("http://auth.endpoint")
                                              .tokenEndpoint("http://token.endpoint")
                                              .userinfoEndpoint("http://userinfo.endpoint")
                                              .secretRef("secret-ref");

    OidcJitConfigDTO jitConfigDTO =
        new OidcJitConfigDTO().enabled(true).claimKey("claim-key").claimValue("claim-value");

    OidcProviderDTO providerDTO = new OidcProviderDTO()
                                      .identifier("provider-id")
                                      .name("provider-name")
                                      .issuer("http://issuer.url")
                                      .discovery(true)
                                      .pkce(true)
                                      .sendScopeToTokenEndpoint(true)
                                      .uidField("uid-field")
                                      .clientConfig(clientConfigDTO)
                                      .jitConfig(jitConfigDTO);

    OidcProviderSettings oidcProviderSettings = OidcProviderMapper.getOidcProvider(ACCOUNT_IDENTIFIER, providerDTO);

    assertThat(oidcProviderSettings).isNotNull();
    assertThat(oidcProviderSettings.getIdentifier()).isEqualTo("provider-id");
    assertThat(oidcProviderSettings.getName()).isEqualTo("provider-name");
    assertThat(oidcProviderSettings.getIssuer()).isEqualTo("http://issuer.url");
    assertThat(oidcProviderSettings.getResponseType()).isEqualTo("code");
    assertThat(oidcProviderSettings.isDiscovery()).isTrue();
    assertThat(oidcProviderSettings.isPkce()).isTrue();
    assertThat(oidcProviderSettings.isSendScopeToTokenEndpoint()).isTrue();
    assertThat(oidcProviderSettings.getUidField()).isEqualTo("uid-field");
    assertThat(oidcProviderSettings.getType()).isEqualTo(SSOType.OIDC);

    ClientIdConfiguration clientConfig = oidcProviderSettings.getClientIdConfiguration();
    assertThat(clientConfig).isNotNull();
    assertThat(clientConfig.getIdentifier()).isEqualTo("client-id");
    assertThat(clientConfig.getRedirectUrl()).isEqualTo("http://redirect.url");
    assertThat(clientConfig.getJwksUri()).isEqualTo("http://jwks.uri");
    assertThat(clientConfig.getAuthorizationEndpoint()).isEqualTo("http://auth.endpoint");
    assertThat(clientConfig.getTokenEndpoint()).isEqualTo("http://token.endpoint");
    assertThat(clientConfig.getUserInfoEndpoint()).isEqualTo("http://userinfo.endpoint");
    assertThat(clientConfig.getSecretRef()).isEqualTo("secret-ref");

    JitConfiguration jitConfig = oidcProviderSettings.getJitConfiguration();
    assertThat(jitConfig).isNotNull();
    assertThat(jitConfig.isEnabled()).isTrue();
    assertThat(jitConfig.getClaimKey()).isEqualTo("claim-key");
    assertThat(jitConfig.getClaimValue()).isEqualTo("claim-value");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProvider_withNullClientAndJitConfig() {
    OidcProviderDTO providerDTO = new OidcProviderDTO()
                                      .identifier("provider-id")
                                      .name("provider-name")
                                      .issuer("http://issuer.url")
                                      .discovery(false)
                                      .pkce(false)
                                      .sendScopeToTokenEndpoint(false)
                                      .uidField("uid-field")
                                      .clientConfig(null)
                                      .jitConfig(null);

    OidcProviderSettings oidcProviderSettings = OidcProviderMapper.getOidcProvider(ACCOUNT_IDENTIFIER, providerDTO);

    assertThat(oidcProviderSettings).isNotNull();
    assertThat(oidcProviderSettings.getClientIdConfiguration()).isNull();
    assertThat(oidcProviderSettings.getJitConfiguration()).isNull();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProviderDTO_withValidInput() {
    ClientIdConfiguration clientConfig = ClientIdConfiguration.builder()
                                             .identifier("client-id")
                                             .redirectUrl("http://redirect.url")
                                             .jwksUri("http://jwks.uri")
                                             .authorizationEndpoint("http://auth.endpoint")
                                             .tokenEndpoint("http://token.endpoint")
                                             .userInfoEndpoint("http://userinfo.endpoint")
                                             .secretRef("secret-ref")
                                             .build();

    JitConfiguration jitConfig =
        JitConfiguration.builder().enabled(true).claimKey("claim-key").claimValue("claim-value").build();

    OidcProviderSettings provider = OidcProviderSettings.builder()
                                        .identifier("provider-id")
                                        .name("provider-name")
                                        .issuer("http://issuer.url")
                                        .responseType("code")
                                        .discovery(true)
                                        .pkce(true)
                                        .sendScopeToTokenEndpoint(true)
                                        .uidField("uid-field")
                                        .clientIdConfiguration(clientConfig)
                                        .jitConfiguration(jitConfig)
                                        .build();

    OidcProviderDTO providerDTO = OidcProviderMapper.getOidcProviderDTO(provider);

    assertThat(providerDTO).isNotNull();
    assertThat(providerDTO.getIdentifier()).isEqualTo("provider-id");
    assertThat(providerDTO.getName()).isEqualTo("provider-name");
    assertThat(providerDTO.getIssuer()).isEqualTo("http://issuer.url");
    assertThat(providerDTO.isDiscovery()).isTrue();
    assertThat(providerDTO.isPkce()).isTrue();
    assertThat(providerDTO.isSendScopeToTokenEndpoint()).isTrue();
    assertThat(providerDTO.getUidField()).isEqualTo("uid-field");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetOidcProviderDTO_withNullInput() {
    OidcProviderDTO providerDTO = OidcProviderMapper.getOidcProviderDTO(null);

    assertNull(providerDTO);
  }
}
