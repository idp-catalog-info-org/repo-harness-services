/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.provision.resource;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ProvisionApi;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class ProvisionApiImpl implements ProvisionApi {
  private IdpCommonService idpCommonService;
  private ProvisionService provisionService;

  public Response provisionIdp(String accountIdentifier) {
    try {
      idpCommonService.checkUserAuthorization();
      NamespaceInfo namespaceInfo = provisionService.provision(accountIdentifier);
      return Response.status(Response.Status.CREATED).entity(namespaceInfo).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
