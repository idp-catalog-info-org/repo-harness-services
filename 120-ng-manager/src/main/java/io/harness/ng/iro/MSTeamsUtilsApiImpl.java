/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.ng.v1.MsTeamsUtilsApi;

import clients.iromanager.remote.connectors.msteams.MsTeamsOAuthResponse;
import clients.iromanager.remote.connectors.msteams.MsTeamsRetroFitClient;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class MSTeamsUtilsApiImpl implements MsTeamsUtilsApi {
  private final MsTeamsRetroFitClient msTeamsRetroFitClient;
  @Inject public final NextGenConfiguration configuration;

  @Override
  @PublicApi
  public Response msTeamsOauthTokenGenerate(String authorizationCode) {
    try {
      MsTeamsOAuthResponse msTeamsOAuthResponse =
          msTeamsRetroFitClient.performOAuthAccessTokenRequest(configuration.getMsTeamsConfig().getScope(),
              configuration.getMsTeamsConfig().getClientId(), configuration.getMsTeamsConfig().getClientSecret(),
              authorizationCode, configuration.getMsTeamsConfig().getCallbackUrl());

      return Response.ok().entity(msTeamsOAuthResponse).build();
    } catch (Exception e) {
      log.error("Error generating MsTeams OAuth token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to generate MsTeams OAuth token: " + e.getMessage())
          .build();
    }
  }
}
