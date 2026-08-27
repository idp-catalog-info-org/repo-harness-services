/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.ng.v1.SlackUtilsApi;

import clients.iromanager.remote.connectors.slack.SlackOAuthResponse;
import clients.iromanager.remote.connectors.slack.SlackRetroFitClient;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class SlackUtilsApiImpl implements SlackUtilsApi {
  private final SlackRetroFitClient slackRetroFitClient;
  @Inject public final NextGenConfiguration configuration;

  @Override
  @PublicApi
  public Response slackOauthTokenGenerate(String authorizationCode) {
    try {
      SlackOAuthResponse slackOAuthResponse = slackRetroFitClient.performOAuthAccessTokenRequest(
          configuration.getSlackConfig().getClientId(), configuration.getSlackConfig().getClientSecret(),
          authorizationCode, configuration.getSlackConfig().getCallbackUrl());

      return Response.ok().entity(slackOAuthResponse).build();
    } catch (Exception e) {
      log.error("Error generating Slack OAuth token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to generate Slack OAuth token: " + e.getMessage())
          .build();
    }
  }
}
