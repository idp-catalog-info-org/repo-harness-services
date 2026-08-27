/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/v2/custom-plugins")
@Tag(name = "CustomPluginsV2", description = "V2 APIs for Custom Plugin CRUD operations")
@OwnedBy(HarnessTeam.IDP)
public interface CustomPluginsV2FileUploadApi {
  @POST
  @Path("/{plugin-id}/file")
  @Consumes("multipart/form-data")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "uploadCustomPluginHtmlFileV2", summary = "Upload an HTML file for a custom plugin",
      security = { @SecurityRequirement(name = "x-api-key") })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "HTML file uploaded successfully")
    , @ApiResponse(responseCode = "400", description = "Unsupported file format"),
        @ApiResponse(responseCode = "404", description = "Plugin not found")
  })
  Response
  uploadHtmlFile(@PathParam("plugin-id") String pluginId, @FormDataParam("file") InputStream fileInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail,
      @HeaderParam("Harness-Account") @Parameter(description = "Account identifier") String harnessAccount);

  @DELETE
  @Path("/{plugin-id}/file")
  @Operation(operationId = "deleteCustomPluginHtmlFileV2", summary = "Delete the HTML file of a custom plugin",
      security = { @SecurityRequirement(name = "x-api-key") })
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "HTML file deleted successfully")
    , @ApiResponse(responseCode = "404", description = "Plugin or file not found")
  })
  Response
  deleteHtmlFile(@PathParam("plugin-id") String pluginId,
      @HeaderParam("Harness-Account") @Parameter(description = "Account identifier") String harnessAccount);

  @GET
  @Path("/{plugin-id}/file")
  @Produces(MediaType.TEXT_HTML)
  @Operation(operationId = "previewCustomPluginFileV2",
      summary = "Preview the HTML file content of a custom plugin (read file content)",
      security = { @SecurityRequirement(name = "x-api-key") })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "HTML file content",
        content = @Content(mediaType = MediaType.TEXT_HTML, schema = @Schema(implementation = String.class)))
    ,
        @ApiResponse(responseCode = "404", description = "Plugin or file not found")
  })
  Response
  previewFile(@PathParam("plugin-id") String pluginId,
      @HeaderParam("Harness-Account") @Parameter(description = "Account identifier") String harnessAccount);
}
