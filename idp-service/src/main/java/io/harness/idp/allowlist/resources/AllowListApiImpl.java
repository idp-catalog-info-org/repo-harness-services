/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.allowlist.resources;

import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION;
import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.allowlist.services.AllowListService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.AllowListApi;
import io.harness.spec.server.idp.v1.model.AllowListRequest;
import io.harness.spec.server.idp.v1.model.AllowListResponse;
import io.harness.spec.server.idp.v1.model.HostInfo;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@Slf4j
@Timed
@ResponseMetered
public class AllowListApiImpl implements AllowListApi {
  private AllowListService allowListService;
  @Override
  public Response getAllowList(@AccountIdentifier String harnessAccount) {
    try {
      List<HostInfo> hostInfoList = allowListService.getAllowList(harnessAccount);
      AllowListResponse response = new AllowListResponse();
      response.setAllow(hostInfoList);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      String errorMessage =
          String.format("Error occurred while fetching allow list for accountId: [%s]", harnessAccount);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_ADVANCED_CONFIGURATION, permission = IDP_ADVANCED_CONFIGURATION_EDIT)
  public Response saveAllowList(@Valid AllowListRequest body, @AccountIdentifier String harnessAccount) {
    try {
      List<HostInfo> hostInfoList = body.getAllow();
      hostInfoList = allowListService.saveAllowList(hostInfoList, harnessAccount);
      AllowListResponse response = new AllowListResponse();
      response.setAllow(hostInfoList);
      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (Exception e) {
      String errorMessage = String.format("Error occurred while saving allow list for accountId: [%s]", harnessAccount);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
