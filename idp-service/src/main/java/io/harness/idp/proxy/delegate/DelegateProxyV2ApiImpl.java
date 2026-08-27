/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.idp.common.JacksonUtils.write;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.proxy.delegate.beans.BackstageProxyRequest;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class DelegateProxyV2ApiImpl implements DelegateProxyV2Api {
  private final DelegateProxyApiImpl delegateProxyApi;
  private static final String IDP_S2S_UNDERSCORE = "idp-s2s-underscore";

  @Override
  @POST
  @Path("{actualMethod}")
  @IdpServiceAuthIfHasApiKey
  public Response delegateProxyV2(@PathParam("actualMethod") String actualMethod,
      @QueryParam("actualUrl") String actualUrl, @Context HttpHeaders headers, String actualBody)
      throws JsonProcessingException {
    Map<String, String> backstageProxyRequestHeaders = new HashMap<>();
    headers.getRequestHeaders().forEach((key, values) -> {
      final String updatedKey = key.replace(IDP_S2S_UNDERSCORE, "_");
      if (updatedKey.toLowerCase().startsWith("idp-task-header-")) {
        values.forEach(value
            -> backstageProxyRequestHeaders.put(
                updatedKey.toLowerCase().substring("idp-task-header-".length()), value));
      }
    });

    BackstageProxyRequest backstageProxyRequest = new BackstageProxyRequest();
    backstageProxyRequest.setUrl(actualUrl);
    backstageProxyRequest.setMethod(actualMethod);
    backstageProxyRequest.setHeaders(backstageProxyRequestHeaders);
    backstageProxyRequest.setBody(actualBody);
    return delegateProxyApi.forwardProxy(null, headers, null, write(backstageProxyRequest));
  }
}
