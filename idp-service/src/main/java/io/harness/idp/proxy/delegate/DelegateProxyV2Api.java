/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

@Path("/v2/idp-proxy/delegate")
@OwnedBy(IDP)
public interface DelegateProxyV2Api {
  @POST
  @Consumes("*/*")
  @Produces("*/*")
  @Path("{actualMethod}")
  Response delegateProxyV2(@PathParam("actualMethod") String actualMethod, @QueryParam("actualUrl") String actualUrl,
      @Context HttpHeaders headers, String actualBody) throws JsonProcessingException;
}
