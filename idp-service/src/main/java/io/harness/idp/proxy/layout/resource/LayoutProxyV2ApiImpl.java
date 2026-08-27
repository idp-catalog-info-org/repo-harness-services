/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.resource;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.eraro.ResponseMessage;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.LayoutProxyV2Api;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Timed
@ResponseMetered
public class LayoutProxyV2ApiImpl implements LayoutProxyV2Api {
  BackstageResourceClient backstageResourceClient;

  @Override
  public Response getAllLayoutsV2(@AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getAllLayoutsV2(harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getAllLayouts v2 - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }
}
