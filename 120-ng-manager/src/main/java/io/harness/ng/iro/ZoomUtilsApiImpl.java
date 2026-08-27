/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.ng.v1.ZoomUtilsApi;

import clients.iromanager.remote.connectors.zoom.ZoomRefreshOAuthToken;
import clients.iromanager.remote.connectors.zoom.ZoomRetrofitClient;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ZoomUtilsApiImpl implements ZoomUtilsApi {
  @Inject public final NextGenConfiguration configuration;

  private final ZoomRetrofitClient zoomRetrofitClient;
  @Override
  @PublicApi
  public Response zoomOauthTokenGenerate(String authorizationCode) {
    try {
      String basicAuth = zoomRetrofitClient.generateBasicAuthHeader(
          configuration.getZoomConfig().getClientId(), configuration.getZoomConfig().getClientSecret().toCharArray());
      ZoomRefreshOAuthToken zoomRefreshOAuthToken = zoomRetrofitClient.performGetOauthToken(
          basicAuth, authorizationCode, configuration.getZoomConfig().getCallbackUrl());

      return Response.ok().entity(zoomRefreshOAuthToken).build();
    } catch (Exception e) {
      log.error("Error generating Zoom OAuth token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to generate Zoom OAuth token: " + e.getMessage())
          .build();
    }
  }
}