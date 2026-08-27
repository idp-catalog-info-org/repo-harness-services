/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.tiergroups.resources;

import static io.harness.idp.common.RbacConstants.IDP_SCORECARD;
import static io.harness.idp.common.RbacConstants.IDP_SCORECARD_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
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
public class TierIconUploadApiImpl implements TierIconUploadApi {
  private final TierGroupService tierGroupService;
  private final IdpCommonService idpCommonService;

  @Inject
  public TierIconUploadApiImpl(TierGroupService tierGroupService, IdpCommonService idpCommonService) {
    this.tierGroupService = tierGroupService;
    this.idpCommonService = idpCommonService;
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_EDIT)
  public Response uploadTierIcon(String fileType, InputStream fileInputStream, FormDataContentDisposition fileDetail,
      @AccountIdentifier String harnessAccount) {
    if (!idpCommonService.idpScorecardTiersEnabled(harnessAccount)) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(ResponseMessage.builder().message("Scorecard tiers is not enabled for this account").build())
          .build();
    }
    try {
      String responseIconUrl = tierGroupService.uploadTierIcon(fileType, fileInputStream, fileDetail, harnessAccount);
      CardIconResponse cardIconResponse = new CardIconResponse();
      cardIconResponse.setIconUrl(responseIconUrl);
      return Response.status(Response.Status.OK).entity(cardIconResponse).build();
    } catch (InvalidRequestException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not upload tier icon for account {}", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Could not upload tier icon").build())
          .build();
    }
  }
}
