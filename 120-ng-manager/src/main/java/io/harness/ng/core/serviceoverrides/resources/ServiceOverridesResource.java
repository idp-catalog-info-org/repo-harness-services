/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.filter.FilterType.OVERRIDE;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

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
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideCriteriaHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideV2MigrationService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverridesV2YamlSchemaHelper;
import io.harness.configuration.DeployMode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.AccessDeniedException;
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
import io.harness.ng.core.opa.gitx.ServiceOverrideOpaStatusHandler;
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
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRemoteRepoInfo;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRemoteRepoListResponse;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesRequest;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesResponse;
import io.harness.ng.core.serviceoverridev2.beans.ForceImportOverridesYamlOperationDTO;
import io.harness.ng.core.serviceoverridev2.beans.RemoteEnvFetchFailureResponsesListDTO;
import io.harness.ng.core.serviceoverridev2.beans.RemoteEnvGlobalOverridesMigrationDetails;
import io.harness.ng.core.serviceoverridev2.beans.RemoteEnvWithOverridesV1FetchFailureResponsesDetails;
import io.harness.ng.core.serviceoverridev2.beans.RemoteEnvironmentGlobalOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.RemoteServiceOverridesDTO;
import io.harness.ng.core.serviceoverridev2.beans.RemoteServiceOverridesResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideBatchMigrationDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideImportRequestDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverridesMapperV2;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.CGRestUtils;
import io.harness.scope.ScopeHelper;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
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
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/serviceOverrides")
@Path("/serviceOverrides")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "ServiceOverrides", description = "This contains APIs related to Service Overrides V2")
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
public class ServiceOverridesResource {
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject ServiceOverrideV2MigrationService serviceOverrideV2MigrationService;
  @Inject private EnvironmentService environmentService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ServiceOverrideValidatorService overrideValidatorService;

  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private AccountClient accountClient;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private FilterService filterService;
  @Inject private UserHelperService userHelperService;
  @Inject private ServiceOverridesV2YamlSchemaHelper serviceOverridesV2YamlSchemaHelper;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private ServiceOverrideOpaStatusHandler serviceOverrideOpaStatusHandler;
  @Inject private CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  private static final int MAX_LIMIT = 1000;

  @GET
  @Path("/{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Service Overrides by Identifier", nickname = "getServiceOverridesV2")
  @Operation(operationId = "getServiceOverrides", summary = "Gets Service Overrides by Identifier",
      description = "Retrieves a Service Override by its identifier. "
          + "The identifier can be user-provided during creation, or if not provided, "
          + "it is auto-generated based on the override type: "
          + "ENV_GLOBAL_OVERRIDE = environmentRef, "
          + "ENV_SERVICE_OVERRIDE = environmentRef_serviceRef, "
          + "INFRA_GLOBAL_OVERRIDE = environmentRef_infraIdentifier, "
          + "INFRA_SERVICE_OVERRIDE = environmentRef_serviceRef_infraIdentifier. "
          + "Dots in refs are replaced with underscores.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the Service Override by the identifier and scope derived from accountId, "
                + "org identifier and project identifier")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  get(@Parameter(description = NGCommonEntityConstants.SERVICE_OVERRIDES_IDENTIFIER) @PathParam(
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
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional;
    if (getMetadataOnly) {
      serviceOverridesEntityOptional = serviceOverridesServiceV2.getMetadata(scopeInfo, identifier);
    } else {
      serviceOverridesEntityOptional = serviceOverridesServiceV2.get(
          scopeInfo, identifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    }
    if (serviceOverridesEntityOptional.isEmpty()) {
      throw new NotFoundException(
          format("ServiceOverride entity with identifier [%s] in project [%s], org [%s] not found", identifier,
              projectIdentifier, orgIdentifier));
    }
    return checkForAccessAndGenerateResponseDTO(
        accountId, orgIdentifier, projectIdentifier, serviceOverridesEntityOptional);
  }

  @GET
  @Timed
  @Hidden
  @ResponseMetered
  @ApiOperation(value = "Gets Service Overrides by given environment, service and infrastructure",
      nickname = "getServiceOverrides")
  @Operation(operationId = "getServiceOverridesV2",
      summary = "Gets Service Overrides by given environment, service and infrastructure",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the Service Override by given environment, service and infrastructure and "
                + "scope derived from accountId, org identifier and project identifier")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
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
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional;
    if (getMetadataOnly) {
      serviceOverridesEntityOptional =
          serviceOverridesServiceV2.getMetadata(scopeInfo, environmentRef, serviceRef, infraIdentifier);
    } else {
      serviceOverridesEntityOptional = serviceOverridesServiceV2.get(scopeInfo, environmentRef, serviceRef,
          infraIdentifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    }
    if (serviceOverridesEntityOptional.isEmpty()) {
      throw new NotFoundException(format("ServiceOverride entity with environment [%s], service [%s], infrastructure "
              + "[%s] in project [%s], org [%s] not found",
          environmentRef, serviceRef, infraIdentifier, projectIdentifier, orgIdentifier));
    }
    return checkForAccessAndGenerateResponseDTO(
        accountId, orgIdentifier, projectIdentifier, serviceOverridesEntityOptional);
  }

  @NotNull
  private ResponseDTO<ServiceOverridesResponseDTOV2> checkForAccessAndGenerateResponseDTO(@NotNull String accountId,
      String orgIdentifier, String projectIdentifier,
      Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional) {
    NGServiceOverridesEntity serviceOverridesEntity = serviceOverridesEntityOptional.get();

    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        serviceOverridesEntity.getEnvironmentRef(), accountId, orgIdentifier, projectIdentifier);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
            envIdentifierRef.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, envIdentifierRef.getIdentifier()), ENVIRONMENT_VIEW_PERMISSION,
        format(
            "Unauthorized to view environment %s referred in serviceOverrideEntity", envIdentifierRef.getIdentifier()));

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
    ServiceOverridesResponseDTOV2 response =
        ServiceOverridesMapperV2.toResponseDTO(serviceOverridesEntity, null, false, scopeInfo);
    cdOpaOnSaveStatusApiHelper
        .resolveGetOpaOnSaveStatus(serviceOverridesEntity, accountId, scopeInfo, serviceOverrideOpaStatusHandler)
        .ifPresent(response::setOpaOnSaveStatus);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create an ServiceOverride Entity", nickname = "createServiceOverrideV2")
  @Operation(operationId = "createServiceOverride", summary = "Create an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created ServiceOverride")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service Override to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service Override update request",
                         value = DocumentationConstants.SERVICE_OVERRIDE_V2_REQUEST_DTO,
                         description = "Sample Service Override Request"))
          }) @Valid ServiceOverrideRequestDTOV2 requestDTOV2,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTOV2.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(), requestDTOV2.getServiceRef());
    modifySpecAndYamlInRequest(requestDTOV2, accountId);
    String yamlInternal = requestDTOV2.getYamlInternal();
    // if request is coming from v1 automation, yamlInternal is only to be used for sending back to v1 api response
    // In this case it cant be used for creating spec as v1 yaml and v2 yaml (created from spec) are different
    if (isNotEmpty(yamlInternal) && requestDTOV2.getSpec() == null && !requestDTOV2.isV1Api()) {
      log.info(format("Using new terraform resource to manage overrides v2 for accountId: %s", accountId));
      try {
        ServiceOverridesSpec spec = YamlUtils.read(yamlInternal, ServiceOverridesSpec.class);
        requestDTOV2.setSpec(spec);
        modifySpecAndYamlInRequest(requestDTOV2, accountId);
      } catch (Exception ex) {
        log.error("Failed to create the service override entity through harness terraform provider", ex);
        throw new InvalidRequestException(format("Creation of the service override entity through harness terraform "
                + "provider failed due to following error: [%s]",
            ex.getMessage()));
      }
    }
    overrideValidatorService.validateCreateRequestOrThrow(requestDTOV2, accountId);
    NGServiceOverridesEntity serviceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceOverride.getOrgIdentifier(), serviceOverride.getProjectIdentifier());
    NGOverrideGovernanceDataResponse createdServiceOverrideMapper =
        serviceOverridesServiceV2.create(scopeInfo, serviceOverride);
    return ResponseDTO.newResponse(
        ServiceOverridesMapperV2.toResponseDTO(createdServiceOverrideMapper.getOverridesEntity(),
            createdServiceOverrideMapper.getGovernanceMetadata(), true, scopeInfo));
  }

  @PUT
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Update an ServiceOverride Entity", nickname = "updateServiceOverrideV2")
  @Operation(operationId = "updateServiceOverride", summary = "Update an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated ServiceOverride")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service Override to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Update", summary = "Sample Service Override update request",
                         value = DocumentationConstants.SERVICE_OVERRIDE_V2_REQUEST_DTO,
                         description = "Sample Service Override Request"))
          }) @Valid ServiceOverrideRequestDTOV2 requestDTOV2,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated",
          hidden = true) @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) throws IOException {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTOV2.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(), requestDTOV2.getServiceRef());
    modifySpecAndYamlInRequest(requestDTOV2, accountId);
    String yamlInternal = requestDTOV2.getYamlInternal();
    if (isNotEmpty(yamlInternal) && requestDTOV2.getSpec() == null && !requestDTOV2.isV1Api()) {
      log.info(format("Using new terraform resource to manage overrides v2 for accountId: %s", accountId));
      try {
        ServiceOverridesSpec spec = YamlUtils.read(yamlInternal, ServiceOverridesSpec.class);
        requestDTOV2.setSpec(spec);
        modifySpecAndYamlInRequest(requestDTOV2, accountId);
      } catch (Exception ex) {
        log.error("Failed to update the service override entity through harness terraform provider", ex);
        throw new InvalidRequestException(format("Updating the service override entity through harness terraform "
                + "provider failed due to following error: [%s]",
            ex.getMessage()));
      }
    }
    overrideValidatorService.validateRequestOrThrow(requestDTOV2, accountId);
    NGServiceOverridesEntity requestedServiceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, requestedServiceOverride.getOrgIdentifier(), requestedServiceOverride.getProjectIdentifier());
    NGOverrideGovernanceDataResponse updatedServiceOverrideMapper =
        serviceOverridesServiceV2.update(scopeInfo, requestedServiceOverride);
    return ResponseDTO.newResponse(
        ServiceOverridesMapperV2.toResponseDTO(updatedServiceOverrideMapper.getOverridesEntity(),
            updatedServiceOverrideMapper.getGovernanceMetadata(), false, scopeInfo));
  }

  @POST
  @Path("/upsert")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Upsert an ServiceOverride Entity", nickname = "upsertServiceOverrideV2")
  @Operation(operationId = "upsertServiceOverrideV2", summary = "Upsert an ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created/updated ServiceOverride")
      })
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  upsert(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the ServiceOverride to be updated")
      @Valid ServiceOverrideRequestDTOV2 requestDTOV2) throws IOException {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTOV2.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(), requestDTOV2.getServiceRef());
    modifySpecAndYamlInRequest(requestDTOV2, accountId);
    overrideValidatorService.validateRequestOrThrow(requestDTOV2, accountId);
    NGServiceOverridesEntity requestedServiceOverride = ServiceOverridesMapperV2.toEntity(accountId, requestDTOV2);
    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier());
    Pair<NGOverrideGovernanceDataResponse, Boolean> upsertResult =
        serviceOverridesServiceV2.upsert(requestedServiceOverride, scopeInfo);
    return ResponseDTO.newResponse(ServiceOverridesMapperV2.toResponseDTO(upsertResult.getLeft().getOverridesEntity(),
        upsertResult.getLeft().getGovernanceMetadata(), upsertResult.getRight(), scopeInfo));
  }

  @DELETE
  @Path("/{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Delete a Service Override entity", nickname = "deleteServiceOverrideV2")
  @Operation(operationId = "deleteServiceOverride", summary = "Delete a ServiceOverride entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Service Override is deleted")
      })
  public ResponseDTO<Boolean> delete(@Parameter(description = NGCommonEntityConstants.SERVICE_OVERRIDES_IDENTIFIER)
                                     @PathParam("identifier") @ResourceIdentifier @NotNull String identifier,
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
      throw new InvalidRequestException(format("Service Override [%s], Project[%s], Organization [%s] does not exist",
          identifier, projectIdentifier, orgIdentifier));
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

  @GET
  @Path("/list")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Service Override List", nickname = "getServiceOverrideListV2")
  @Operation(operationId = "getServiceOverrideListV2", summary = "Gets Service Override List",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  public ResponseDTO<PageResponse<ServiceOverridesResponseDTOV2>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("500") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is service override type which is based on override source") @QueryParam("type")
      ServiceOverridesType type, @Context ScopeInfo scopeInfo) {
    Criteria criteria = ServiceOverrideCriteriaHelper.createCriteriaForGetList(scopeInfo, type, null, null);
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));
    Page<NGServiceOverridesEntity> serviceOverridesEntities =
        getRBACFilteredOverrides(criteria, pageRequest, true, scopeInfo);

    return ResponseDTO.newResponse(getNGPageResponse(serviceOverridesEntities.map(
        entity -> ServiceOverridesMapperV2.toResponseDTO(entity, null, false, scopeInfo))));
  }

  @POST
  @Path("/v2/list")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Service Override List", nickname = "getServiceOverrideListV3")
  @Operation(operationId = "getServiceOverrideListV3", summary = "Gets Service Override List",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  public ResponseDTO<PageResponse<ServiceOverridesResponseDTOV2>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("500") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is service override type which is based on override source") @QueryParam(
          "type") ServiceOverridesType type,
      @RequestBody(description = "This is the body for the filter properties for listing overrides.")
      OverrideFilterPropertiesDTO filterProperties,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier, @Context ScopeInfo scopeInfo) {
    OverrideFilterPropertiesDTO finalFilterProperties = filterProperties != null
        ? filterProperties
        : fetchFilterPropertiesFromFilterIdentifier(filterIdentifier, accountId, orgIdentifier, projectIdentifier);
    Criteria criteria =
        ServiceOverrideCriteriaHelper.createCriteriaForGetList(scopeInfo, type, searchTerm, finalFilterProperties);
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));
    Page<NGServiceOverridesEntity> serviceOverridesEntities =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_ENTITY_CRUD_RBAC)
        ? getRBACFilteredOverridesV2(criteria, pageRequest, true, scopeInfo)
        : serviceOverridesServiceV2.list(criteria, pageRequest);

    return ResponseDTO.newResponse(getNGPageResponse(serviceOverridesEntities.map(
        entity -> ServiceOverridesMapperV2.toResponseDTO(entity, null, false, scopeInfo))));
  }

  @POST
  @Path("/v3/list")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Service Override List based on RBAC permissions", nickname = "getServiceOverrideListWithRBAC")
  @Operation(operationId = "getServiceOverrideListWithRBAC",
      summary = "Gets Service Override List based on RBAC permissions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of services overrides for a Project")
      })
  public ResponseDTO<PageResponse<ServiceOverridesResponseDTOV2>>
  listServiceOverridesV3(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                             NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("500") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is service override type which is based on override source") @QueryParam(
          "type") ServiceOverridesType type,
      @RequestBody(description = "This is the body for the filter properties for listing overrides.")
      OverrideFilterPropertiesDTO filterProperties,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier, @Context ScopeInfo scopeInfo) {
    OverrideFilterPropertiesDTO finalFilterProperties = filterProperties != null
        ? filterProperties
        : fetchFilterPropertiesFromFilterIdentifier(filterIdentifier, accountId, orgIdentifier, projectIdentifier);
    Criteria criteria =
        ServiceOverrideCriteriaHelper.createCriteriaForGetList(scopeInfo, type, searchTerm, finalFilterProperties);
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.lastModifiedAt));

    Page<NGServiceOverridesEntity> serviceOverridesEntities =
        getRBACFilteredOverridesV2(criteria, pageRequest, true, scopeInfo);

    return ResponseDTO.newResponse(getNGPageResponse(serviceOverridesEntities.map(
        entity -> ServiceOverridesMapperV2.toResponseDTO(entity, null, false, scopeInfo))));
  }

  @POST
  @Hidden
  @Path("/migrate")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Migrate ServiceOverride to V2", nickname = "migrateServiceOverride")
  @Operation(operationId = "migrateServiceOverride", summary = "Migrate ServiceOverride to V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  migrateServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to migrate overrides.", USER);
    }
    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, true, false);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @POST
  @Hidden
  @Path("/batch-migrate-and-enable")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Migrate ServiceOverride to V2", nickname = "migrateAndEnableServiceOverrideV2")
  @Operation(operationId = "migrateServiceOverride", summary = "Migrate ServiceOverride to V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Details")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<List<ServiceOverrideMigrationResponseDTO>>
  migrateAndEnableServiceOverrideV2(
      @RequestBody(required = true, description = "Details of accounts to be migrated to override v2") @NonNull
      @Valid ServiceOverrideBatchMigrationDTO batchMigrationDTO) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to migrate overrides.", USER);
    }

    List<ServiceOverrideMigrationResponseDTO> migrationResponseDTOS = new ArrayList<>();

    for (String accountId : batchMigrationDTO.getAccountIds()) {
      try {
        ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
            serviceOverrideV2MigrationService.migrateToV2(accountId, null, null, true, false);
        migrationResponseDTOS.add(serviceOverrideMigrationResponseDTO);
      } catch (Exception e) {
        log.error(String.format("Could not migrate override v2 accountId: %s", accountId), e);
      }
    }

    return ResponseDTO.newResponse(migrationResponseDTOS);
  }

  @POST
  @Hidden
  @Path("/migrateScope")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Migrate ServiceOverride to V2 at one scope", nickname = "migrateServiceOverrideScoped")
  @Operation(operationId = "migrateServiceOverrideScoped", summary = "Migrate ServiceOverride to V2 at one scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  migrateServiceOverrideAtScope(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to migrate overrides.", USER);
    }

    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, false, false);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  private Boolean hasOverridesMigrationAccess() {
    // for on-prem environment, all customers have the access to perform overrides migration for their account
    String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if (DeployMode.isOnPrem(deployMode)) {
      return true;
    }

    // for SAAS, the user running the migration API should be a member of harness support user group
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    return CGRestUtils.getResponse(accountClient.isHarnessSupportUserId(userId));
  }

  @POST
  @Path("/revertMigration")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Revert ServiceOverride V2 Migration", nickname = "revertMigrationServiceOverride")
  @Operation(operationId = "revertMigrationServiceOverride", summary = "Revert ServiceOverride V2 Migration",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Revert Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  revertMigrationServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to revert overrides migration.", USER);
    }

    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, true, true);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @POST
  @Path("/revertMigrationScope")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Revert ServiceOverride V2 Migration at one scope", nickname = "revertMigrationServiceOverrideScoped")
  @Operation(operationId = "revertMigrationServiceOverrideScoped",
      summary = "Revert ServiceOverride V2 Migration at one scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Override Migration Revert Details")
      })
  public ResponseDTO<ServiceOverrideMigrationResponseDTO>
  revertMigrationServiceOverrideAtScope(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                        @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to revert overrides migration.", USER);
    }

    ServiceOverrideMigrationResponseDTO serviceOverrideMigrationResponseDTO =
        serviceOverrideV2MigrationService.migrateToV2(accountId, orgIdentifier, projectIdentifier, false, true);
    return ResponseDTO.newResponse(serviceOverrideMigrationResponseDTO);
  }

  @GET
  @Path("/get-with-yaml/{identifier}")
  @Hidden
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Service Overrides by Identifier including the yaml of spec also in response",
      nickname = "getWithYamlServiceOverridesV2")
  @Operation(operationId = "getWithYamlServiceOverridesV2",
      summary = "Gets Service Overrides by Identifier including the yaml of spec also in response",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the Service Overrides by the identifier and scope derived from accountId, "
                + "org identifier and project identifier")
      })
  public ResponseDTO<ServiceOverridesResponseDTOV2>
  getWithUpdatedYamlInternal(@Parameter(description = NGCommonEntityConstants.SERVICE_OVERRIDES_IDENTIFIER) @PathParam(
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
      @Context ScopeInfo scopeInfo) {
    log.info(format("Using new terraform resource to manage overrides v2 for accountId: %s", accountId));
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional = serviceOverridesServiceV2.get(
        scopeInfo, identifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    if (serviceOverridesEntityOptional.isEmpty()) {
      throw new NotFoundException(
          format("ServiceOverrides entity with identifier [%s] in project [%s], org [%s] not found", identifier,
              projectIdentifier, orgIdentifier));
    }
    NGServiceOverridesEntity serviceOverridesEntity = serviceOverridesEntityOptional.get();

    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        serviceOverridesEntity.getEnvironmentRef(), accountId, orgIdentifier, projectIdentifier);

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
            envIdentifierRef.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, envIdentifierRef.getIdentifier()), ENVIRONMENT_VIEW_PERMISSION,
        format(
            "Unauthorized to view environment %s referred in serviceOverrideEntity", envIdentifierRef.getIdentifier()));

    ServiceOverridesSpec spec = serviceOverridesEntity.getSpec();
    if (spec != null) {
      try {
        String yamlInternalFromSpec = YamlUtils.writeYamlString(spec);
        serviceOverridesEntity.setYamlInternal(yamlInternalFromSpec);
      } catch (Exception ex) {
        throw new InvalidRequestException(
            format("Generation of yaml from service override entity's spec failed due to following error: [%s]",
                ex.getMessage()));
      }
    }

    return ResponseDTO.newResponse(
        serviceOverridesEntityOptional
            .map(entity -> ServiceOverridesMapperV2.toResponseDTO(entity, null, false, scopeInfo))
            .orElse(null));
  }

  @POST
  @Path("/move-config")
  @ApiOperation(value = "Move ServiceOverride YAML from inline to remote or remote to inline",
      nickname = "serviceOverrideMoveConfigs")
  @Operation(operationId = "serviceOverrideMoveConfigs",
      summary = "Move ServiceOverride YAML from inline to remote or remote to inline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches ServiceOverride YAML from Harness DB and creates a remote entity or Fetches "
                + "ServiceOverride YAML from remote repository and creates a inline entity")
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
    ServiceOverrideMoveConfigOperationDTO moveConfigOperationDTO =
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
    ServiceOverrideMoveConfigResponseDTO moveConfigResponse =
        serviceOverridesServiceV2.moveConfig(scopeInfo, moveConfigOperationDTO);
    return ResponseDTO.newResponse(moveConfigResponse);
  }

  @PUT
  @Path("/update-git-metadata")
  @ApiOperation(
      value = "Update git-metadata in remote ServiceOverride Entity", nickname = "updateServiceOverrideGitDetails")
  @Operation(operationId = "updateServiceOverrideGitDetails",
      description =
          "Update git-metadata in remote ServiceOverride and returns the identifier of updated ServiceOverride",
      summary = "Update git-metadata in remote ServiceOverride Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns identifier and associated environmentRef, serviceRef and infraIdentifier of updated "
                + "ServiceOverride")
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
    ServiceOverrideGitUpdateResponseDTO serviceOverrideAfterGitMetadataUpdate =
        serviceOverridesServiceV2.updateGitMetadata(
            scopeInfo, gitMetadataUpdateParams, serviceOverrideIdentifierParams);
    return ResponseDTO.newResponse(serviceOverrideAfterGitMetadataUpdate);
  }

  @POST
  @Path("/import")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "import Service Overrides from remote", nickname = "importServiceOverrides")
  @Operation(operationId = "importServiceOverrides", summary = "import Service Overrides from remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "imports the Service Override from GIT and saves a record for it in Harness.")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceOverrideImportResponseDTO>
  importOverrideFromGit(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountId,
      @RequestBody(required = true, description = "Details of the Service Override to be imported",
          content =
          {
            @Content(examples = @ExampleObject(name = "Import", summary = "Service Override import request",
                         value = DocumentationConstants.SERVICE_OVERRIDE_IMPORT_REQUEST_DTO,
                         description = "Service Override import Request"))
          }) @Valid ServiceOverrideImportRequestDTO requestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, requestDTO.getEnvironmentRef()), ENVIRONMENT_UPDATE_PERMISSION);
    checkServiceUpdatePermissionIfScoped(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getServiceRef());
    NGOverrideGovernanceDataResponse overrideResponseMapper = serviceOverridesServiceV2.importOverrideFromRemote(
        accountId, ServiceOverridesMapperV2.getOverrideRequestDTOV2FromImportRequest(requestDTO),
        ServiceOverridesMapperV2.getOverridesOperationsDTOFromGitImportInfoDTO(gitImportInfoDTO), false);
    NGServiceOverridesEntity ngServiceOverridesEntity = overrideResponseMapper.getOverridesEntity();
    return ResponseDTO.newResponse(ServiceOverrideImportResponseDTO.builder()
                                       .identifier(ngServiceOverridesEntity.getIdentifier())
                                       .environmentRef(ngServiceOverridesEntity.getEnvironmentRef())
                                       .serviceRef(ngServiceOverridesEntity.getServiceRef())
                                       .infraIdentifier(ngServiceOverridesEntity.getInfraIdentifier())
                                       .type(ngServiceOverridesEntity.getType())
                                       .governanceMetadata(overrideResponseMapper.getGovernanceMetadata())
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
      throw new InvalidRequestException(String.format("Could not find a override filter with the identifier %s, in %s",
          filterIdentifier, ScopeHelper.getScopeMessageForLogs(accountId, orgIdentifier, projectIdentifier)));
    }

    if (!(overrideFilterDTO.getFilterProperties() instanceof OverrideFilterPropertiesDTO)) {
      throw new InvalidRequestException(String.format("Filter with the identifier %s, in %s is not an override filter",
          filterIdentifier, ScopeHelper.getScopeMessageForLogs(accountId, orgIdentifier, projectIdentifier)));
    }
    return (OverrideFilterPropertiesDTO) overrideFilterDTO.getFilterProperties();
  }

  @GET
  @Hidden
  @Path("/get-remote-environments-in-non-default-branch")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get remote environments details", nickname = "getRemoteEnvironmentsDetails")
  @Operation(operationId = "getRemoteEnvironmentsDetails", summary = "Get remote environments details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Get remote environments details and add the failing ones to response.")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<RemoteEnvFetchFailureResponsesListDTO>
  getRemoteEnvironmentsDetails(@RequestBody(
      required = true, description = "Details of accounts to be considered for fetching remote environment details")
      @NonNull @Valid ServiceOverrideBatchMigrationDTO accountsListDTO) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to migrate overrides.", USER);
    }
    List<RemoteEnvWithOverridesV1FetchFailureResponsesDetails> fetchFailureResponsesDetailsList = new ArrayList<>();

    for (String accountId : accountsListDTO.getAccountIds()) {
      try {
        RemoteEnvWithOverridesV1FetchFailureResponsesDetails response =
            serviceOverrideV2MigrationService.getListOfRemoteEnvsFetchFailureResponses(accountId);
        fetchFailureResponsesDetailsList.add(response);
      } catch (Exception e) {
        throw new InvalidRequestException(
            format(
                "An error occurred while fetching non-default branch remote environments for accountId: %s", accountId),
            e);
      }
    }

    RemoteEnvFetchFailureResponsesListDTO remoteEnvFetchFailureResponsesListDTO =
        RemoteEnvFetchFailureResponsesListDTO.builder()
            .remoteEnvFetchFailureResponsesDetails(fetchFailureResponsesDetailsList)
            .build();
    return ResponseDTO.newResponse(remoteEnvFetchFailureResponsesListDTO);
  }

  @POST
  @Hidden
  @Path("/migrate-remote-environment-overrides")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Migrate remote environment overrides", nickname = "migrateRemoteEnvironmentOverrides")
  @Operation(operationId = "migrateRemoteEnvironmentOverrides", summary = "Migrate remote environment overrides",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Migrate remote environments to overrides V2")
      })
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<RemoteEnvironmentGlobalOverrideMigrationResponseDTO>
  migrateRemoteEnvironmentOverrides(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    if (Boolean.FALSE.equals(hasOverridesMigrationAccess())) {
      throw new AccessDeniedException("User doesn't have permission to migrate overrides.", USER);
    }
    RemoteEnvGlobalOverridesMigrationDetails migrationDetails;
    try {
      migrationDetails = serviceOverrideV2MigrationService.migrateRemoteEnvironmentsToOverridesV2(accountId);
    } catch (Exception e) {
      log.error(
          String.format("An error occurred while migrating remote env. global overrides for accountId: %s", accountId),
          e);
      throw new InvalidRequestException(
          format("An error occurred while migrating remote env. global overrides for accountId: %s.", accountId), e);
    }

    RemoteEnvironmentGlobalOverrideMigrationResponseDTO responseDTO =
        ServiceOverridesMapperV2.toEnvGlobalOverrideMigrationResponseDTO(accountId, migrationDetails);
    return ResponseDTO.newResponse(responseDTO);
  }

  @GET
  @Path("/remote-service-overrides-metadata")
  @ApiOperation(value = "List remote service overrides grouped by repository for a given accountId",
      nickname = "getRemoteServiceOverridesMetadata")
  @Operation(operationId = "getRemoteServiceOverridesMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote service overrides in an account along with "
          + "service override metadata. Optionally filter by repoName.",
      summary = "List remote service overrides grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the service override file paths in each repo")
      })
  @InternalApi
  @Hidden
  public ResponseDTO<RemoteServiceOverridesResponseDTO>
  getRemoteServiceOverridesMetadata(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote service overrides only for the given repoName.")
      @QueryParam(NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_SERVICE_OVERRIDE_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      ServiceOverrideRemoteRepoListResponse serviceOverrideResponse =
          serviceOverridesServiceV2.getRemoteRepoListForAGivenScope(
              accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<ServiceOverrideRemoteRepoInfo> serviceOverrideRepos = serviceOverrideResponse.getRepositories() == null
          ? Collections.emptyList()
          : serviceOverrideResponse.getRepositories();
      List<RemoteServiceOverridesDTO> resourceRepos =
          serviceOverrideRepos.stream()
              .map(info
                  -> RemoteServiceOverridesDTO.builder()
                         .repoName(info.getRepoName())
                         .repoURL(info.getRepoURL())
                         .count(info.getCount())
                         .filePathsByOwningScope(info.getFilePathsByOwningScope())
                         .connectorRefs(info.getConnectorRefs())
                         .build())
              .collect(Collectors.toList());
      long totalServiceOverrides =
          serviceOverrideRepos.stream().mapToLong(ServiceOverrideRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_SERVICE_OVERRIDE_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalServiceOverrides={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceOverrideResponse.getTotalRepos(),
          resourceRepos.size(), totalServiceOverrides, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemoteServiceOverridesResponseDTO.builder()
                                         .totalServiceOverrides(totalServiceOverrides)
                                         .totalRepos(serviceOverrideResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error(
          "[REMOTE_SERVICE_OVERRIDE_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  private Page<NGServiceOverridesEntity> getRBACFilteredOverrides(
      Criteria criteria, Pageable pageRequest, boolean useScopeInfo, ScopeInfo scopeInfo) {
    Page<NGServiceOverridesEntity> overridesEntities = serviceOverridesServiceV2.list(criteria, Pageable.unpaged());
    if (overridesEntities == null || EmptyPredicate.isEmpty(overridesEntities)) {
      return Page.empty();
    }
    final List<NGServiceOverridesEntity> overridesEntitiesList =
        overrideValidatorService.getPermittedOverridesList(overridesEntities.getContent(), useScopeInfo, scopeInfo);
    if (isEmpty(overridesEntitiesList)) {
      return Page.empty();
    }
    populateInFilter(criteria, NGServiceOverridesEntityKeys.identifier,
        overridesEntitiesList.stream().map(NGServiceOverridesEntity::getIdentifier).collect(toList()));

    return serviceOverridesServiceV2.list(criteria, pageRequest);
  }

  private Page<NGServiceOverridesEntity> getRBACFilteredOverridesV2(
      Criteria criteria, Pageable pageRequest, boolean useScopeInfo, ScopeInfo scopeInfo) {
    Page<NGServiceOverridesEntity> overridesEntities = serviceOverridesServiceV2.list(criteria, Pageable.unpaged());
    if (overridesEntities == null || EmptyPredicate.isEmpty(overridesEntities)) {
      return Page.empty();
    }
    final List<NGServiceOverridesEntity> overridesEntitiesList =
        overrideValidatorService.getPermittedOverridesList(overridesEntities.getContent(), useScopeInfo, scopeInfo);
    if (isEmpty(overridesEntitiesList)) {
      return Page.empty();
    }
    List<String> permittedIds =
        overridesEntitiesList.stream().map(NGServiceOverridesEntity::getIdentifier).collect(toList());
    Criteria pagedCriteria =
        new Criteria().andOperator(criteria, Criteria.where(NGServiceOverridesEntityKeys.identifier).in(permittedIds));

    return serviceOverridesServiceV2.list(pagedCriteria, pageRequest);
  }

  @POST
  @Path("/validate-yaml")
  @Hidden
  @ApiOperation(value = "This api return the validation result of Override yaml", nickname = "validateOverrideYaml")
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
  @ApiOperation(value = "Import YAML from git and create override bypassing errors", nickname = "forceImportOverride")
  @Operation(operationId = "forceImportOverride", summary = "Force import and create override from git repository",
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
        false);
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

  private void modifySpecAndYamlInRequest(ServiceOverrideRequestDTOV2 requestDTOV2, String accountId) {
    try {
      if (requestDTOV2.getSpec() == null && isNotEmpty(requestDTOV2.getYaml())) {
        log.info(format("The spec field provided in overrides v2 CRUD request is null. AccountId: %s", accountId));
        ServiceOverridesSpec spec = ServiceOverridesMapperV2.getOverridesV2SpecFromYamlV2(requestDTOV2.getYaml());
        requestDTOV2.setSpec(spec);
      }

    } catch (Exception ex) {
      log.error(
          format(
              "An error occurred while converting yaml to spec for override v2 entity of type [%s] with envRef: [%s]",
              requestDTOV2.getType().toString(), requestDTOV2.getEnvironmentRef()),
          ex);
    }
    if (isEmpty(requestDTOV2.getYaml()) && requestDTOV2.getSpec() != null) {
      log.info(format("The yaml field provided in overrides v2 CRUD request is null. AccountId: %s", accountId));
      requestDTOV2.setYaml(ServiceOverridesMapperV2.getYamlV2FromOverridesV2Spec(requestDTOV2.getSpec()));
    }
  }

  private void checkServiceUpdatePermissionIfScoped(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceRef) {
    if (isEmpty(serviceRef)) {
      return;
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceRef), SERVICE_UPDATE_PERMISSION);
  }
}
