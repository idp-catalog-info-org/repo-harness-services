/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.CustomPluginInfoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.InputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/v1/plugins-info/{plugin-id}/file")
@OwnedBy(HarnessTeam.IDP)
public interface PluginFileUploadApi {
  @POST
  @Consumes({"multipart/form-data"})
  @Produces({"application/json", "application/yaml"})
  @Operation(operationId = "uploadCustomPluginFile", summary = "Upload Custom Plugin File", description = "",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"PluginInfo"})
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Example response", content = {
      @Content(mediaType = "application/json", schema = @Schema(implementation = CustomPluginInfoResponse.class))
    })
  })
  Response
  uploadCustomPluginFile(@PathParam("plugin-id") String pluginId, @FormDataParam("file_type") String fileType,
      @FormDataParam("file") InputStream fileInputStream, @FormDataParam("file") FormDataContentDisposition fileDetail,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @DELETE
  @Operation(operationId = "deletePluginFile", summary = "Delete plugin file", description = "",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"PluginInfo"})
  @ApiResponses({ @ApiResponse(responseCode = "204", description = "No Content") })
  Response
  deletePluginFile(@PathParam("plugin-id") String pluginId,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      @QueryParam("file-type") @Parameter(description = "Type of the file") String fileType,
      @QueryParam("file-url") @Parameter(description = "URL of the uploaded file") String fileUrl);
}
