/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.config.AutoProvisionLicenseConfig;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

// Flex-licensing counterpart to AdminLicenseResource. Lives in 120-ng-manager because the
// edition->module mapping comes from AutoProvisionLicenseConfig (a 120-ng-manager config),
// and pushing that down into 930-ng-license-manager would invert the existing dep direction.
@OwnedBy(PL)
@Path("/admin/licenses/flex")
@Produces({"application/json"})
@Consumes({"application/json"})
@NextGenManagerAuth
@Hidden
@Slf4j
public class FlexLicenseResource {
  private final LicenseService licenseService;
  private final AutoProvisionLicenseConfig autoProvisionLicenseConfig;

  @Inject
  public FlexLicenseResource(LicenseService licenseService, AutoProvisionLicenseConfig autoProvisionLicenseConfig) {
    this.licenseService = licenseService;
    this.autoProvisionLicenseConfig = autoProvisionLicenseConfig;
  }

  @POST
  @Path("{accountIdentifier}")
  @InternalApi
  public Response startFlexLicense(
      @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam("edition") Edition edition, @QueryParam("startTime") Long startTime,
      @DefaultValue("true") @QueryParam("preview") boolean preview) {
    List<ModuleType> modules = autoProvisionLicenseConfig.getModulesForEdition(edition);
    List<ModuleLicenseDTO> result;
    if (startTime == null) {
      result = preview ? licenseService.previewFlexLicense(accountIdentifier, edition, modules)
                       : licenseService.startFlexLicense(accountIdentifier, edition, modules);
    } else {
      result = preview ? licenseService.previewFlexLicense(accountIdentifier, edition, modules, startTime)
                       : licenseService.startFlexLicense(accountIdentifier, edition, modules, startTime);
    }
    Response.Status status = preview ? Response.Status.OK : Response.Status.CREATED;
    return Response.status(status).entity(ResponseDTO.newResponse(result)).build();
  }
}
