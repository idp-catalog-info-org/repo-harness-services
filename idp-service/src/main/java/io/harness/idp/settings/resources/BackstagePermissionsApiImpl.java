/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.resources;

import static io.harness.idp.common.RbacConstants.IDP_CATALOG_ACCESS_POLICY;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_ACCESS_POLICY_CREATE;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_ACCESS_POLICY_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_ACCESS_POLICY_VIEW;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.BackstagePermissionsApi;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsRequest;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
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
public class BackstagePermissionsApiImpl implements BackstagePermissionsApi {
  private BackstagePermissionsService backstagePermissionsService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG_ACCESS_POLICY, permission = IDP_CATALOG_ACCESS_POLICY_VIEW)
  public Response getBackstagePermissions(@AccountIdentifier String harnessAccount) {
    Optional<BackstagePermissions> backstagePermissions =
        backstagePermissionsService.findByAccountIdentifier(harnessAccount);
    if (backstagePermissions.isEmpty()) {
      log.warn("Could not fetch permissions for the account {}", harnessAccount);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    BackstagePermissionsResponse backstagePermissionsResponse = new BackstagePermissionsResponse();
    backstagePermissionsResponse.setData(backstagePermissions.get());
    return Response.status(Response.Status.OK).entity(backstagePermissionsResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG_ACCESS_POLICY, permission = IDP_CATALOG_ACCESS_POLICY_CREATE)
  public Response createBackstagePermissions(
      @Valid BackstagePermissionsRequest body, @AccountIdentifier String harnessAccount) {
    BackstagePermissions backstagePermissions;
    try {
      backstagePermissions = backstagePermissionsService.createPermissions(body.getData(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not create permissions", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    BackstagePermissionsResponse backstagePermissionsResponse = new BackstagePermissionsResponse();
    backstagePermissionsResponse.setData(backstagePermissions);
    return Response.status(Response.Status.CREATED).entity(backstagePermissionsResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG_ACCESS_POLICY, permission = IDP_CATALOG_ACCESS_POLICY_EDIT)
  public Response updateBackstagePermissions(
      @Valid BackstagePermissionsRequest body, @AccountIdentifier String harnessAccount) {
    BackstagePermissions backstagePermissions;
    try {
      backstagePermissions = backstagePermissionsService.updatePermissions(body.getData(), harnessAccount);
    } catch (Exception e) {
      log.error("Could not update permissions", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    BackstagePermissionsResponse backstagePermissionsResponse = new BackstagePermissionsResponse();
    backstagePermissionsResponse.setData(backstagePermissions);
    return Response.status(Response.Status.OK).entity(backstagePermissionsResponse).build();
  }
}
