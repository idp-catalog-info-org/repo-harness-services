/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notify;

import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import io.swagger.v3.oas.annotations.Hidden;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Path("/notify")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@InternalApi
public interface NotifyResource {
  @POST
  @Path("/{correlationId}")
  @Hidden
  ResponseDTO<Boolean> doneWith(@NotBlank @PathParam("correlationId") @ResourceIdentifier String correlationId);
}
