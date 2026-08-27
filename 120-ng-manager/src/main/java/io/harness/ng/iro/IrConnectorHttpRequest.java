/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

/**
 * Request for an HTTP call whose authentication is resolved on the delegate from a Harness connector. The delegate
 * fetches/decrypts the connector's credentials (including external secret managers) and builds the Authorization
 * header per connector type. This is a separate, additive surface from {@code HttpDelegateTaskRequest} so the existing
 * {@code execute-http-request} contract is unaffected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.IRO)
public class IrConnectorHttpRequest {
  @NotBlank(message = "HTTP method cannot be blank") String method;

  @NotBlank(message = "URL cannot be blank") String url;

  @Builder.Default Map<String, String> headers = new HashMap<>();

  @Builder.Default Map<String, String> queryParams = new HashMap<>();

  JsonNode body;

  List<String> delegateSelectors;

  // Scope used to route/own the delegate task (where the delegate lives). Account is mandatory; org/project optional.
  @NotBlank(message = "delegate account ID cannot be blank") String delegateAccountId;

  String delegateOrgId;

  String delegateProjectId;

  // Connector whose credentials the delegate uses to authenticate the call.
  @NotBlank(message = "connector identifier cannot be blank") String connectorIdentifier;

  // Scope at which the connector lives. Independent of the delegate scope (e.g. an org-scoped connector may be
  // executed by an account-level delegate). When not provided, falls back to the delegate scope for backward
  // compatibility, and the connectorIdentifier prefix (account./org.) still takes precedence during resolution.
  String connectorOrgId;

  String connectorProjectId;

  public String resolveConnectorOrgId() {
    return StringUtils.isNotBlank(connectorOrgId) ? connectorOrgId : delegateOrgId;
  }

  public String resolveConnectorProjectId() {
    return StringUtils.isNotBlank(connectorProjectId) ? connectorProjectId : delegateProjectId;
  }

  public void validate() {
    if (StringUtils.isBlank(method)) {
      throw new IllegalArgumentException("HTTP method is required");
    }
    if (StringUtils.isBlank(url)) {
      throw new IllegalArgumentException("URL is required");
    }
    if (StringUtils.isBlank(delegateAccountId)) {
      throw new IllegalArgumentException("delegateAccountId is required");
    }
    if (StringUtils.isBlank(connectorIdentifier)) {
      throw new IllegalArgumentException("connectorIdentifier is required");
    }
  }
}
