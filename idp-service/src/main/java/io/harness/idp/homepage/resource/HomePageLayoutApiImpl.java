/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.resource;

import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.HomePageLayoutApi;
import io.harness.spec.server.idp.v1.model.DeleteHomePageLayoutIconRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutRequest;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.HomePageLayoutYamlResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class HomePageLayoutApiImpl implements HomePageLayoutApi {
  @Inject HomePageLayoutService homePageLayoutService;

  @Override
  public Response getHomePageLayoutInfo(@AccountIdentifier String harnessAccount) {
    HomePageLayoutResponse homePageLayoutResponse = homePageLayoutService.getHomePageLayout(harnessAccount);
    return Response.status(Response.Status.OK).entity(homePageLayoutResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response saveHomePageLayoutInfo(HomePageLayoutRequest body, @AccountIdentifier String harnessAccount) {
    HomePageLayoutResponse homePageLayoutResponse = homePageLayoutService.saveHomePageLayout(body, harnessAccount);
    return Response.status(Response.Status.OK).entity(homePageLayoutResponse).build();
  }

  @Override
  public Response getYamlForHomePageLayout(@AccountIdentifier String harnessAccount) {
    HomePageLayoutYamlResponse homePageLayoutYamlResponse = homePageLayoutService.getHomePageLayoutYaml(harnessAccount);
    return Response.status(Response.Status.OK).entity(homePageLayoutYamlResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteCustomLinkCardQuickLinks(
      String cardIdentifier, String quickLinkIdentifier, @AccountIdentifier String harnessAccount) {
    homePageLayoutService.deleteCustomCardQuickLinksIcon(harnessAccount, cardIdentifier, quickLinkIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteHeadersQuickLinksIcon(String quickLinkIdentifier, @AccountIdentifier String harnessAccount) {
    homePageLayoutService.deleteHeaderQuickLinksIcon(harnessAccount, quickLinkIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteHomePageLayoutCardsIcon(String cardIdentifier, @AccountIdentifier String harnessAccount) {
    homePageLayoutService.deleteCardIcon(harnessAccount, cardIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteHomePageLayoutIcon(
      @Valid DeleteHomePageLayoutIconRequest body, @AccountIdentifier String harnessAccount) {
    homePageLayoutService.deleteHomePageLayoutIcon(harnessAccount, body.getIconUrl());
    return Response.status(Response.Status.NO_CONTENT).build();
  }
}
