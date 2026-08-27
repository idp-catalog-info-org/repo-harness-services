/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.version.resource;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.version.VersionInfoManager;
import io.harness.version.VersionPackage;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("/idp/api/version")
@Path("/idp/api/version")
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class VersionInfoResource {
  private final VersionInfoManager versionInfoManager;

  @Inject
  public VersionInfoResource(VersionInfoManager versionInfoManager) {
    this.versionInfoManager = versionInfoManager;
  }

  @GET
  @ApiOperation(value = "get version for idp service", nickname = "getIDPServiceVersion")
  public RestResponse<VersionPackage> get() {
    return new RestResponse<>(VersionPackage.builder().versionInfo(versionInfoManager.getVersionInfo()).build());
  }
}
