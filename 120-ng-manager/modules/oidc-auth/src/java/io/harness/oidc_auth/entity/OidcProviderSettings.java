/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.entity;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.sso.entity.SSOSettings;

import software.wings.beans.sso.SSOType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonCreator;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "OidcProviderKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
@Persistent
@TypeAlias("OidcProviderSettings")
public class OidcProviderSettings extends SSOSettings {
  List<String> scopes;
  @NotEmpty String responseType;
  @NotEmpty String issuer;
  boolean discovery;
  @NotEmpty String uidField;
  boolean sendScopeToTokenEndpoint;
  boolean pkce;
  ClientIdConfiguration clientIdConfiguration;
  JitConfiguration jitConfiguration;
  AuthorizationConfig authorizationConfig;

  @JsonCreator
  @Builder
  public OidcProviderSettings(String identifier, String name, String accountIdentifier, SSOType ssoType, String url,
      List<String> scopes, String responseType, String issuer, boolean discovery, String uidField,
      boolean sendScopeToTokenEndpoint, boolean pkce, ClientIdConfiguration clientIdConfiguration,
      JitConfiguration jitConfiguration, AuthorizationConfig authorizationConfig) {
    super(ssoType, name, identifier, url, accountIdentifier);
    this.setScopes(scopes);
    this.responseType = responseType;
    this.issuer = issuer;
    this.discovery = discovery;
    this.uidField = uidField;
    this.sendScopeToTokenEndpoint = sendScopeToTokenEndpoint;
    this.pkce = pkce;
    this.clientIdConfiguration = clientIdConfiguration;
    this.jitConfiguration = jitConfiguration;
    this.authorizationConfig = authorizationConfig;
  }

  public void setScopes(List<String> scopes) {
    if (isEmpty(scopes)) {
      this.scopes = Arrays.asList("openid", "profile", "email");
    } else {
      this.scopes = scopes;
    }
  }

  @Override
  public SSOType getType() {
    return SSOType.OIDC;
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    return Collections.emptyList();
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return null;
  }

  @Override
  public String getUuid() {
    return getId();
  }
}
