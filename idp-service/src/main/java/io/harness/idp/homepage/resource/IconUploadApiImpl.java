/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
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
import io.harness.idp.common.IconUploadType;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.model.CardIconResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.io.InputStream;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
@Timed
@ResponseMetered
public class IconUploadApiImpl implements IconUploadApi {
  @Inject HomePageLayoutService homePageLayoutService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response uploadIcon(IconUploadType type, String identifier, String fileType, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, @AccountIdentifier String harnessAccount) {
    String responseIconUrl =
        homePageLayoutService.uploadIcon(type, identifier, fileType, fileInputStream, fileDetail, harnessAccount);
    CardIconResponse cardIconResponse = new CardIconResponse();
    cardIconResponse.setIconUrl(responseIconUrl);
    return Response.status(Response.Status.OK).entity(cardIconResponse).build();
  }
}
