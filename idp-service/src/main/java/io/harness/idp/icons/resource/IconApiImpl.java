/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.icons.resource;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.icons.service.IconService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.IconsApi;
import io.harness.spec.server.idp.v1.model.IconsResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class IconApiImpl implements IconsApi {
  @Inject IconService iconService;

  @Override
  public Response getIcons(@AccountIdentifier String harnessAccount) {
    IconsResponse iconResponse = new IconsResponse();
    iconResponse.setIcons(iconService.getAllIcons(harnessAccount));
    return Response.status(Response.Status.OK).entity(iconResponse).build();
  }
}
