/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NamedUser {
  private String name;
  private UserSpec user;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UserSpec {
    // Bearer token -> SERVICE_ACCOUNT
    private String token;

    // Basic auth -> USER_PASSWORD
    private String username;
    private String password;

    // Client key/cert -> CLIENT_KEY_CERT (inline *-data or file-path variants)
    @JsonProperty("client-certificate-data") private String clientCertificateData;
    @JsonProperty("client-key-data") private String clientKeyData;
    @JsonProperty("client-certificate") private String clientCertificate;
    @JsonProperty("client-key") private String clientKey;

    // auth-provider block -> oidc / gcp / azure
    @JsonProperty("auth-provider") private AuthProvider authProvider;

    // exec plugin -> unsupported (gke-gcloud-auth-plugin, aws-iam-authenticator, kubelogin)
    private ExecConfig exec;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AuthProvider {
    private String name;
    private Map<String, String> config;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ExecConfig {
    private String command;
  }
}
