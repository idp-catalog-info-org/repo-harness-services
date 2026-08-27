/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.homepage.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.IconUploadType;
import io.harness.spec.server.idp.v1.model.CardIconResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.InputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/v1/home-page-layout/icon/{type}/{identifier}")
@OwnedBy(HarnessTeam.IDP)
public interface IconUploadApi {
  @POST
  @Consumes({"multipart/form-data"})
  @Produces({"application/json", "application/yaml"})
  @Operation(operationId = "uploadCardIcon", summary = "Upload Card Icon", description = "",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"Card"})
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Example response", content = {
      @Content(mediaType = "application/json", schema = @Schema(implementation = CardIconResponse.class))
    })
  })
  Response
  uploadIcon(@PathParam("type") IconUploadType type, @PathParam("identifier") String identifier,
      @FormDataParam("file_type") String fileType, @FormDataParam("file") InputStream fileInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
