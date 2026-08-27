/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.resources;

import static io.harness.filter.FilterType.OVERRIDE;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideCriteriaHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideV2MigrationService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverridesV2YamlSchemaHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitx.GitXUtils;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.serviceoverride.beans.NGOverrideGovernanceDataResponse;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitMetadataUpdateParams;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateResponseDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideIdentifierParams;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideImportResponseDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideMoveConfigOperationDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideMoveConfigRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideMoveConfigResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesRequest;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesResponse;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesYamlOperationDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverrideRequestDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverridesResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideImportRequestDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverridesMapperV2;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.scope.ScopeHelper;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.UserHelperService;
import io.harness.yaml.validator.beans.YamlValidationAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationListAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO.YamlValidationRequestDTOBuilder;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/overrides")
@Path("/overrides")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Overrides", description = "This contains APIs related to Overrides")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@ScopeInfoResolutionApi
public class OverridesResource {
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject ServiceOverrideV2MigrationService serviceOverrideV2MigrationService;
  @Inject private EnvironmentService environmentService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ServiceOverrideValidatorService overrideValidatorService;

  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private AccountClient accountClient;
  @Inject private FilterService filterService;
  @Inject private UserHelperService userHelperService;
  @Inject private ServiceOverridesV2YamlSchemaHelper serviceOverridesV2YamlSchemaHelper;
  @Inject private ScopeInfoService scopeInfoService;
  private static final int MAX_LIMIT = 1000;

  @GET
  @Path("/{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Overrides by Identifier", nickname = "getOverridesViaIdentifier")
  @Operation(operationId = "getOverridesViaIdentifier", summary = "Gets Overrides by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the Override by the identifier and scope derived from accountId, org "
                + "identifier and project identifier")
      })
  public ResponseDTO<OverridesResponseDTO>
  get(@Parameter(description = NGCommonEntityConstants.OVERRIDES_IDENTIFIER) @PathParam(
          "identifier") @ResourceIdentifier @NotNull String identifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NonNull String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @Parameter(description = "Specifies whether to get only the metadata of entity", hidden = true)
      @QueryParam("getMetadataOnly") @DefaultValue("false") boolean getMetadataOnly, @Context ScopeInfo scopeInfo) {
    Optional<NGServiceOverridesEntity> overridesEntityOptional;
    if (getMetadataOnly) {
      overridesEntityOptional = serviceOverridesServiceV2.getMetadata(scopeInfo, identifier);
    } else {
      overridesEntityOptional = serviceOverridesServiceV2.get(
          scopeInfo, identifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    }
    if (overridesEntityOptional.isEmpty()) {
      throw new NotFoundException(format("Override entity with identifier [%s] in project [%s], org [%s] not found",
          identifier, projectIdentifier, orgIdentifier));
    }
    return checkForAccessAndGenerateResponseDTO(
        accountId, orgIdentifier, projectIdentifier, overridesEntityOptional, scopeInfo);
  }

  @GET
  @Timed
  @Hidden
  @ResponseMetered
  @ApiOperation(value = "Gets Overrides by given environment, service and infrastructure", nickname = "getOverrides")
  @Operation(operationId = "getOverrides", summary = "Gets Overrides by given environment, service and infrastructure",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the Override by given environment, service and infrastructure and scope "
                + "derived from accountId, org identifier and project identifier")
      })
  public ResponseDTO<OverridesResponseDTO>
  getV2(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NonNull String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_REF_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_REF_KEY) @NonNull String environmentRef,
      @Parameter(description = NGCommonEntityConstants.SERVICE_REF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_REF_KEY) String serviceRef,
      @Parameter(description = NGCommonEntityConstants.INFRA_IDENTIFIER) @QueryParam(
          NGCommonEntityConstants.INFRA_IDENTIFIER_KEY) String infraIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @Parameter(description = "Specifies whether to get only the metadata of entity", hidden = true)
      @QueryParam("getMetadataOnly") @DefaultValue("false") boolean getMetadataOnly, @Context ScopeInfo scopeInfo) {
    Optional<NGServiceOverridesEntity> overridesEntityOptional;
    if (getMetadataOnly) {
      overridesEntityOptional =
          serviceOverridesServiceV2.getMetadata(scopeInfo, environmentRef, serviceRef, infraIdentifier);
    } else {
      overridesEntityOptional = serviceOverridesServiceV2.get(scopeInfo, environmentRef, serviceRef, infraIdentifier,
          GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    }
    if (overridesEntityOptional.isEmpty()) {
      throw new NotFoundException(format("Override entity with environment [%s], service [%s], infrastructure [%s] in "
              + "project [%s], org [%s] not found",
          environmentRef, serviceRef, infraIdentifier, projectIdentifier, orgIdentifier));
    }
    return checkForAccessAndGenerateResponseDTO(
        accountId, orgIdentifier, projectIdentifier, overridesEntityOptional, scopeInfo);
  }

  @NotNull
  private ResponseDTO<OverridesResponseDTO> checkForAccessAndGenerateResponseDTO(@NotNull String accountId,
      String orgIdentifier, String projectIdentifier, Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional,
      ScopeInfo scopeInfo) {
    NGServiceOverridesEntity overridesEntity = serviceOverridesEntityOptional.get();

    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        overridesEntity.getEnvironmentRef(), accountId, orgIdentifier, projectIdentifier);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
            envIdentifierRef.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, envIdentifierRef.getIdentifier()), ENVIRONMENT_VIEW_PERMISSION,
        format("Unauthorized to view environment %s referred in Override Entity", envIdentifierRef.getIdentifier()));

    return ResponseDTO.newResponse(
        serviceOverridesEntityOptional
            .map(entity -> ServiceOverridesMapperV2.toOverridesResponseDTO(entity, null, false, scopeInfo))
            .orElse(null));
  }

  @POST
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create an Override Entity", nickname = "createOverride")
  @Operation(operationId = "createOverride", summary = "Create an Override Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Override")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<OverridesResponseDTO>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Override to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Override update request",
                         value = DocumentationConstants.OVERRIDE_REQUEST_DTO, description = "Sample Override Request"))
          }) @Valid OverrideRequestDTO requestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTO.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getServiceRef());
    serviceOverridesV2YamlSchemaHelper.validateSchema(requestDTO.getYaml());
    overrideValidatorService.validateCreateRequestOrThrowV2(requestDTO, accountId);
    NGServiceOverridesEntity overridesEntity = ServiceOverridesMapperV2.toEntity(accountId, requestDTO);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, overridesEntity.getOrgIdentifier(), overridesEntity.getProjectIdentifier());
    NGOverrideGovernanceDataResponse createdOverridesEntityMapper =
        serviceOverridesServiceV2.create(scopeInfo, overridesEntity);
    return ResponseDTO.newResponse(
        ServiceOverridesMapperV2.toOverridesResponseDTO(createdOverridesEntityMapper.getOverridesEntity(),
            createdOverridesEntityMapper.getGovernanceMetadata(), true, scopeInfo));
  }

  @PUT
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Update an Override Entity", nickname = "updateOverride")
  @Operation(operationId = "updateOverride", summary = "Update an Override Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Override")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<OverridesResponseDTO>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Override to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Update", summary = "Sample Override update request",
                         value = DocumentationConstants.OVERRIDE_REQUEST_DTO, description = "Sample Override Request"))
          }) @Valid OverrideRequestDTO requestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated",
          hidden = true) @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) throws IOException {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTO.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getServiceRef());
    serviceOverridesV2YamlSchemaHelper.validateSchema(requestDTO.getYaml());
    overrideValidatorService.validateRequestOrThrowV2(requestDTO, accountId);
    NGServiceOverridesEntity overridesEntity = ServiceOverridesMapperV2.toEntity(accountId, requestDTO);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, overridesEntity.getOrgIdentifier(), overridesEntity.getProjectIdentifier());
    NGOverrideGovernanceDataResponse updatedOverridesEntityMapper =
        serviceOverridesServiceV2.update(scopeInfo, overridesEntity);
    return ResponseDTO.newResponse(
        ServiceOverridesMapperV2.toOverridesResponseDTO(updatedOverridesEntityMapper.getOverridesEntity(),
            updatedOverridesEntityMapper.getGovernanceMetadata(), false, scopeInfo));
  }

  @DELETE
  @Path("/{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Delete an Override entity", nickname = "deleteOverride")
  @Operation(operationId = "deleteOverride", summary = "Delete a Override entity",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Override is deleted") })
  public ResponseDTO<Boolean> delete(@Parameter(description = NGCommonEntityConstants.OVERRIDES_IDENTIFIER) @PathParam(
                                         "identifier") @ResourceIdentifier @NotNull String identifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    Optional<NGServiceOverridesEntity> ngServiceOverridesEntityOptional =
        serviceOverridesServiceV2.getMetadata(scopeInfo, identifier);
    if (ngServiceOverridesEntityOptional.isEmpty()) {
      throw new InvalidRequestException(
          format("Override with identifier [%s], project [%s], organization [%s] does not exist", identifier,
              projectIdentifier, orgIdentifier));
    }
    NGServiceOverridesEntity ngServiceOverridesEntity = ngServiceOverridesEntityOptional.get();

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, ngServiceOverridesEntity.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, orgIdentifier, projectIdentifier, ngServiceOverridesEntity.getServiceRef());
    overrideValidatorService.validateDeleteRequestOrThrow(ngServiceOverridesEntity, orgIdentifier, projectIdentifier);

    boolean deleted =
        serviceOverridesServiceV2.delete(scopeInfo, ngServiceOverridesEntity.getIdentifier(), ngServiceOverridesEntity);
    return ResponseDTO.newResponse(deleted);
  }

  @POST
  @Path("/list")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Override List", nickname = "getOverrideList")
  @Operation(operationId = "getOverrideList", summary = "Gets Overrides List",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Overrides") })
  public ResponseDTO<PageResponse<OverridesResponseDTO>>
  listOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("500") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is override type which is based on override source") @QueryParam(
          "type") ServiceOverridesType type,
      @RequestBody(description = "This is the body for the filter properties for listing overrides.")
      OverrideFilterPropertiesDTO filterProperties,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier, @Context ScopeInfo scopeInfo) {
    OverrideFilterPropertiesDTO finalFilterProperties = filterProperties != null
        ? filterProperties
        : fetchFilterPropertiesFromFilterIdentifier(filterIdentifier, accountId, orgIdentifier, projectIdentifier);
    Criteria criteria;
    if (scopeInfo != null) {
      criteria =
          ServiceOverrideCriteriaHelper.createCriteriaForGetList(scopeInfo, type, searchTerm, finalFilterProperties);
    } else {
      criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(
          accountId, orgIdentifier, projectIdentifier, type, searchTerm, finalFilterProperties);
    }
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));
    Page<NGServiceOverridesEntity> OverridesEntities = serviceOverridesServiceV2.list(criteria, pageRequest);

    if (scopeInfo != null) {
      return ResponseDTO.newResponse(getNGPageResponse(OverridesEntities.map(
          entity -> ServiceOverridesMapperV2.toOverridesResponseDTO(entity, null, false, scopeInfo))));
    } else {
      return ResponseDTO.newResponse(getNGPageResponse(
          OverridesEntities.map(entity -> ServiceOverridesMapperV2.toOverridesResponseDTO(entity, null, false))));
    }
  }

  @POST
  @Path("/move-config")
  @ApiOperation(
      value = "Move Override YAML from inline to remote or remote to inline", nickname = "OverrideMoveConfigs")
  @Operation(operationId = "OverrideMoveConfigs",
      summary = "Move Override YAML from inline to remote or remote to inline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Override YAML from Harness DB and creates a remote entity or Fetches Override YAML "
                + "from remote repository and creates a inline entity")
      })
  public ResponseDTO<ServiceOverrideMoveConfigResponseDTO>
  moveConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NonNull String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @BeanParam ServiceOverrideMoveConfigRequestDTO serviceOverrideMoveConfigRequestDTO,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, serviceOverrideMoveConfigRequestDTO.getEnvironmentRef()),
        ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, orgIdentifier, projectIdentifier, serviceOverrideMoveConfigRequestDTO.getServiceRef());
    ServiceOverrideMoveConfigResponseDTO moveConfigResponse;
    ServiceOverrideMoveConfigOperationDTO moveConfigDTO =
        ServiceOverrideMoveConfigOperationDTO.builder()
            .repoName(serviceOverrideMoveConfigRequestDTO.getRepoName())
            .branch(serviceOverrideMoveConfigRequestDTO.getBranch())
            .moveConfigOperationType(serviceOverrideMoveConfigRequestDTO.getMoveConfigOperationType())
            .environmentRef(serviceOverrideMoveConfigRequestDTO.getEnvironmentRef())
            .serviceRef(serviceOverrideMoveConfigRequestDTO.getServiceRef())
            .infraIdentifier(serviceOverrideMoveConfigRequestDTO.getInfraIdentifier())
            .serviceOverridesType(serviceOverrideMoveConfigRequestDTO.getServiceOverridesType())
            .identifier(serviceOverrideMoveConfigRequestDTO.getIdentifier())
            .connectorRef(serviceOverrideMoveConfigRequestDTO.getConnectorRef())
            .isHarnessCodeRepo(serviceOverrideMoveConfigRequestDTO.getIsHarnessCodeRepo())
            .baseBranch(serviceOverrideMoveConfigRequestDTO.getBaseBranch())
            .commitMessage(serviceOverrideMoveConfigRequestDTO.getCommitMsg())
            .isNewBranch(serviceOverrideMoveConfigRequestDTO.getIsNewBranch())
            .filePath(serviceOverrideMoveConfigRequestDTO.getFilePath())
            .build();
    if (scopeInfo != null) {
      moveConfigResponse = serviceOverridesServiceV2.moveConfig(scopeInfo, moveConfigDTO);
    } else {
      moveConfigResponse =
          serviceOverridesServiceV2.moveConfig(accountId, orgIdentifier, projectIdentifier, moveConfigDTO);
    }
    return ResponseDTO.newResponse(moveConfigResponse);
  }

  @PUT
  @Path("/update-git-metadata")
  @ApiOperation(value = "Update git-metadata in remote Override Entity", nickname = "updateOverrideGitDetails")
  @Operation(operationId = "updateOverrideGitDetails",
      description = "Update git-metadata in remote Override and returns the identifier of updated Override",
      summary = "Update git-metadata in remote Override Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns identifier and associated environmentRef, serviceRef and infraIdentifier of updated Override")
      })
  public ResponseDTO<ServiceOverrideGitUpdateResponseDTO>
  updateGitMetadataForServiceOverride(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @BeanParam ServiceOverrideGitUpdateRequestDTO serviceOverrideGitUpdateRequest, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, serviceOverrideGitUpdateRequest.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceOverrideGitUpdateRequest.getServiceRef());
    ServiceOverrideGitMetadataUpdateParams gitMetadataUpdateParams =
        ServiceOverrideGitMetadataUpdateParams.builder()
            .connectorRef(serviceOverrideGitUpdateRequest.getGitMetadataUpdateRequestInfo().getConnectorRef())
            .repoName(serviceOverrideGitUpdateRequest.getGitMetadataUpdateRequestInfo().getRepoName())
            .filePath(serviceOverrideGitUpdateRequest.getGitMetadataUpdateRequestInfo().getFilePath())
            .build();
    ServiceOverrideIdentifierParams serviceOverrideIdentifierParams =
        ServiceOverrideIdentifierParams.builder()
            .environmentRef(serviceOverrideGitUpdateRequest.getEnvironmentRef())
            .serviceRef(serviceOverrideGitUpdateRequest.getServiceRef())
            .infraIdentifier(serviceOverrideGitUpdateRequest.getInfraIdentifier())
            .identifier(serviceOverrideGitUpdateRequest.getIdentifier())
            .type(serviceOverrideGitUpdateRequest.getServiceOverridesType())
            .build();
    ServiceOverrideGitUpdateResponseDTO serviceOverrideAfterGitMetadataUpdate;
    if (scopeInfo != null) {
      serviceOverrideAfterGitMetadataUpdate = serviceOverridesServiceV2.updateGitMetadata(
          scopeInfo, gitMetadataUpdateParams, serviceOverrideIdentifierParams);
    } else {
      serviceOverrideAfterGitMetadataUpdate = serviceOverridesServiceV2.updateGitMetadata(accountIdentifier,
          orgIdentifier, projectIdentifier, gitMetadataUpdateParams, serviceOverrideIdentifierParams);
    }
    return ResponseDTO.newResponse(serviceOverrideAfterGitMetadataUpdate);
  }

  @POST
  @Path("/import")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "import Overrides from remote", nickname = "importOverrides")
  @Operation(operationId = "importOverrides", summary = "import Overrides from remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "imports the Override from GIT and saves a record for it in Harness.")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceOverrideImportResponseDTO>
  importOverrideFromGit(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountId,
      @RequestBody(required = true, description = "Details of the Override to be imported",
          content =
          {
            @Content(examples = @ExampleObject(name = "Import", summary = "Override import request",
                         value = DocumentationConstants.SERVICE_OVERRIDE_IMPORT_REQUEST_DTO,
                         description = "Override import Request"))
          }) @Valid ServiceOverrideImportRequestDTO requestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTO.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getServiceRef());
    NGOverrideGovernanceDataResponse ngOverrideGovernanceDataResponse =
        serviceOverridesServiceV2.importOverrideFromRemote(accountId,
            ServiceOverridesMapperV2.getOverrideRequestDTOV2FromImportRequest(requestDTO),
            ServiceOverridesMapperV2.getOverridesOperationsDTOFromGitImportInfoDTO(gitImportInfoDTO), true);
    NGServiceOverridesEntity ngServiceOverridesEntity = ngOverrideGovernanceDataResponse.getOverridesEntity();
    return ResponseDTO.newResponse(ServiceOverrideImportResponseDTO.builder()
                                       .identifier(ngServiceOverridesEntity.getIdentifier())
                                       .environmentRef(ngServiceOverridesEntity.getEnvironmentRef())
                                       .serviceRef(ngServiceOverridesEntity.getServiceRef())
                                       .infraIdentifier(ngServiceOverridesEntity.getInfraIdentifier())
                                       .type(ngServiceOverridesEntity.getType())
                                       .governanceMetadata(ngOverrideGovernanceDataResponse.getGovernanceMetadata())
                                       .build());
  }

  private OverrideFilterPropertiesDTO fetchFilterPropertiesFromFilterIdentifier(
      String filterIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    if (isNull(filterIdentifier)) {
      return null;
    }
    FilterDTO overrideFilterDTO =
        filterService.get(accountId, orgIdentifier, projectIdentifier, filterIdentifier, OVERRIDE);
    if (overrideFilterDTO == null) {
      throw new InvalidRequestException(format("Could not find a override filter with the identifier %s, in %s",
          filterIdentifier, ScopeHelper.getScopeMessageForLogs(accountId, orgIdentifier, projectIdentifier)));
    }

    if (!(overrideFilterDTO.getFilterProperties() instanceof OverrideFilterPropertiesDTO)) {
      throw new InvalidRequestException(format("Filter with the identifier %s, in %s is not an override filter",
          filterIdentifier, ScopeHelper.getScopeMessageForLogs(accountId, orgIdentifier, projectIdentifier)));
    }
    return (OverrideFilterPropertiesDTO) overrideFilterDTO.getFilterProperties();
  }

  @POST
  @Path("/validate-yaml")
  @Hidden
  @ApiOperation(value = "This api return the validation result of Override yaml", nickname = "validateOverridesYaml")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<YamlValidationListAPIResponse> validateOverrideYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    final YamlValidationRequestDTO yamlValidationRequestDTO = getYamlValidationRequestDTO(yamlValidationRequestBody);
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        serviceOverridesServiceV2.validateOverrideYaml(accountIdentifier, yamlValidationRequestDTO);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
  }

  @POST
  @Hidden
  @Path("/force-import")
  @ApiOperation(value = "Import YAML from git and create override bypassing errors", nickname = "forceImportOverrides")
  @Operation(operationId = "forceImportOverrides", summary = "Force import and create override from git repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Force import and create override from git repository")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ForceImportOverridesResponse>
  forceImportOverride(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      ForceImportOverridesRequest requestDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTO.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(accountIdentifier, requestDTO.getOrgIdentifier(),
        requestDTO.getProjectIdentifier(), requestDTO.getServiceRef());
    ForceImportOverridesResponse response = serviceOverridesServiceV2.forceImportOverrides(accountIdentifier,
        ForceImportOverridesYamlOperationDTO.builder()
            .branch(requestDTO.getBranch())
            .repoName(requestDTO.getRepoName())
            .connectorRef(requestDTO.getConnectorRef())
            .filePath(requestDTO.getFilePath())
            .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
            .identifier(requestDTO.getIdentifier())
            .orgIdentifier(requestDTO.getOrgIdentifier())
            .projectIdentifier(requestDTO.getProjectIdentifier())
            .environmentRef(requestDTO.getEnvironmentRef())
            .serviceRef(requestDTO.getServiceRef())
            .infraIdentifier(requestDTO.getInfraIdentifier())
            .type(requestDTO.getServiceOverridesType())
            .build(),
        true);
    return ResponseDTO.newResponse(response);
  }

  private YamlValidationRequestDTO getYamlValidationRequestDTO(YamlValidationRequestBody yamlValidationRequestBody) {
    YamlValidationRequestDTOBuilder yamlValidationRequestDTOBuilder =
        YamlValidationRequestDTO.builder().yaml(yamlValidationRequestBody.getYaml());
    if (yamlValidationRequestBody.getGitYamlValidationRequestParams() != null) {
      yamlValidationRequestDTOBuilder.branch(yamlValidationRequestBody.getGitYamlValidationRequestParams().getBranch());
      yamlValidationRequestDTOBuilder.filePath(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getFilePath());
      yamlValidationRequestDTOBuilder.repoName(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getRepoName());
      yamlValidationRequestDTOBuilder.isDefaultBranch(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getIsDefaultBranch());
      yamlValidationRequestDTOBuilder.commitId(
          yamlValidationRequestBody.getGitYamlValidationRequestParams().getCommitId());
    }
    return yamlValidationRequestDTOBuilder.build();
  }

  private void checkServiceUpdatePermissionIfScoped(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceRef) {
    if (serviceRef == null || serviceRef.isEmpty()) {
      return;
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceRef), SERVICE_UPDATE_PERMISSION);
  }
}
