/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.provider.resources;

import static io.harness.pms.rbac.CDNGRbacPermissions.PROVIDER_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.PROVIDER_EDIT_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.PROVIDER_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.PROVIDER;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.provider.ProviderService;
import io.harness.provider.dto.CreateProviderRequestDTO;
import io.harness.provider.dto.CreateProviderResponse;
import io.harness.provider.dto.CreateProviderResponseDTO;
import io.harness.provider.dto.DeleteProviderResponse;
import io.harness.provider.dto.DeleteProviderResponseDTO;
import io.harness.provider.dto.GetProviderResponse;
import io.harness.provider.dto.GetProviderResponseDTO;
import io.harness.provider.dto.ListProviderCriteriaDTO;
import io.harness.provider.dto.UpdateProviderCriteriaDTO;
import io.harness.provider.dto.UpdateProviderRequestDTO;
import io.harness.provider.dto.UpdateProviderResponse;
import io.harness.provider.dto.UpdateProviderResponseDTO;
import io.harness.provider.mapper.ProviderMapper;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PROVIDERS})
@NextGenManagerAuth
@Api("/provider")
@Path("/provider")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Provider", description = "This contains APIs related to Provider")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ProviderResource {
  @Inject ProviderService providerService;
  @Inject ProviderMapper providerMapper;

  public static final String PROVIDER_PARAM_MESSAGE = "Provider Identifier";

  @GET
  @Path("{providerIdentifier}")
  @ApiOperation(value = "Gets a Provider by identifier", nickname = "getProvider")
  @NGAccessControlCheck(resourceType = PROVIDER, permission = PROVIDER_VIEW_PERMISSION)
  @Operation(operationId = "getProvider", summary = "Gets a Provider by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Provider")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<GetProviderResponse>
  get(@Parameter(description = PROVIDER_PARAM_MESSAGE) @PathParam(
          "providerIdentifier") @ResourceIdentifier String providerIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    GetProviderResponseDTO providerResponse = providerService.get(accountIdentifier, providerIdentifier);
    return ResponseDTO.newResponse(providerMapper.toGetResponse(providerResponse));
  }

  @POST
  @ApiOperation(value = "Create a Provider", nickname = "createProvider")
  @NGAccessControlCheck(resourceType = PROVIDER, permission = PROVIDER_EDIT_PERMISSION)
  @Operation(operationId = "createProvider", summary = "Create a Provider",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Provider")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<CreateProviderResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true,
          description = "Details of the Provider to be created") CreateProviderRequestDTO createProviderRequestDTO) {
    CreateProviderResponseDTO providerResponse = providerService.create(accountIdentifier, createProviderRequestDTO);
    return ResponseDTO.newResponse(providerMapper.toCreateResponse(providerResponse));
  }

  @DELETE
  @Path("{providerIdentifier}")
  @ApiOperation(value = "Delete a provider by identifier", nickname = "deleteProvider")
  @NGAccessControlCheck(resourceType = PROVIDER, permission = PROVIDER_DELETE_PERMISSION)
  @Operation(operationId = "deleteProvider", summary = "Delete a Provider by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Provider is deleted") })
  @Timed
  @ResponseMetered
  public ResponseDTO<DeleteProviderResponse>
  delete(@Parameter(description = PROVIDER_PARAM_MESSAGE) @PathParam(
             "providerIdentifier") @ResourceIdentifier String providerIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    DeleteProviderResponseDTO providerResponse = providerService.delete(accountIdentifier, providerIdentifier);
    return ResponseDTO.newResponse(providerMapper.toDeleteResponse(providerResponse));
  }

  @PUT
  @Path("{providerIdentifier}")
  @ApiOperation(value = "Update a provider by identifier", nickname = "updateProvider")
  @NGAccessControlCheck(resourceType = PROVIDER, permission = PROVIDER_EDIT_PERMISSION)
  @Operation(operationId = "updateProvider", summary = "Update a Provider by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Provider") })
  @Timed
  @ResponseMetered
  public ResponseDTO<UpdateProviderResponse>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PROVIDER_PARAM_MESSAGE) @PathParam(
          "providerIdentifier") @ResourceIdentifier String providerIdentifier,
      @RequestBody(required = true,
          description = "Details of the Provider to be updated") UpdateProviderRequestDTO updateProviderRequestDTO) {
    UpdateProviderResponseDTO providerResponse = providerService.update(
        UpdateProviderCriteriaDTO.builder().accountIdentifier(accountIdentifier).identifier(providerIdentifier).build(),
        updateProviderRequestDTO);
    return ResponseDTO.newResponse(providerMapper.toUpdateResponse(providerResponse));
  }

  @GET
  @ApiOperation(value = "Gets Provider list", nickname = "getProviderList")
  @NGAccessControlCheck(resourceType = PROVIDER, permission = PROVIDER_VIEW_PERMISSION)
  @Operation(operationId = "getProviderList", summary = "Gets Provider list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<List<GetProviderResponse>>
  listProviders(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    List<GetProviderResponseDTO> providerResponses =
        providerService.list(ListProviderCriteriaDTO.builder().accountIdentifier(accountIdentifier).build());
    return ResponseDTO.newResponse(
        providerResponses.stream().map(providerMapper::toGetResponse).collect(Collectors.toList()));
  }
}