/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.ng.v1.ConfluenceUtilsApi;

import clients.iromanager.remote.connectors.confluence.ConfluenceOAuthResponse;
import clients.iromanager.remote.connectors.confluence.ConfluenceRetroFitClient;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ConfluenceUtilsApiImpl implements ConfluenceUtilsApi {
  private final ConfluenceRetroFitClient retroFitClient;
  @Inject public final NextGenConfiguration configuration;

  @Override
  @PublicApi
  public Response confluenceOauthTokenGenerate(String authorizationCode) {
    try {
      ConfluenceOAuthResponse confluenceOAuthResponse = retroFitClient.performOAuthAccessTokenRequest(
          configuration.getConfluenceConfig().getClientId(), configuration.getConfluenceConfig().getClientSecret(),
          authorizationCode, configuration.getConfluenceConfig().getCallbackUrl());

      return Response.ok().entity(confluenceOAuthResponse).build();
    } catch (Exception e) {
      log.error("Error generating Confluence OAuth token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to generate Confluence OAuth token: ".concat(e.getMessage()))
          .build();
    }
  }
}
