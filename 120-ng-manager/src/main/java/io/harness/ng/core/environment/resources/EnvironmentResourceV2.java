/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.beans.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.environment.beans.EnvironmentMapper.writeDTO;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_UPDATE_ENVIRONMENT_MESSAGE;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateConfigFilesIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateManifestIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.validateNoMoreThanOneHelmOverridePresent;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.allowedvalues.AllowedValuesUsagesInternalDTO;
import io.harness.allowedvalues.AllowedValuesUsagesRequestDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceEntityValidationHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.data.validator.EntityNameValidator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.MoveConfigOperationType;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitx.GitXUtils;
import io.harness.infrastructure.unified.UnifiedEnvConvertorResponse;
import io.harness.infrastructure.unified.UnifiedEnvListConverterResponse;
import io.harness.infrastructure.unified.UnifiedEnvListRequestDTO;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.beans.EntityWithGitInfo;
import io.harness.ng.core.beans.EnvironmentAndServiceOverridesMetadataInput;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentCloneResponse;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentGitMetadataUpdateParams;
import io.harness.ng.core.environment.beans.EnvironmentGitUpdateResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.beans.EnvironmentInputsetYamlAndServiceOverridesMetadataInput;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentMoveConfigOperationDTO;
import io.harness.ng.core.environment.beans.EnvironmentMoveConfigRequestDTO;
import io.harness.ng.core.environment.beans.EnvironmentMoveConfigResponse;
import io.harness.ng.core.environment.beans.EnvironmentRemoteRepoInfo;
import io.harness.ng.core.environment.beans.EnvironmentRemoteRepoListResponse;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.beans.ForceImportEnvironmentYamlOperationDTO;
import io.harness.ng.core.environment.dto.DestinationEnvironmentConfig;
import io.harness.ng.core.environment.dto.EnvironmentBatchResponse;
import io.harness.ng.core.environment.dto.EnvironmentCloneRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentCloneResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentFailureDTO;
import io.harness.ng.core.environment.dto.EnvironmentImportOperationDTO;
import io.harness.ng.core.environment.dto.EnvironmentImportResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.ForceImportEnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.ForceImportEnvironmentResponse;
import io.harness.ng.core.environment.dto.RemoteEnvironmentsDTO;
import io.harness.ng.core.environment.dto.RemoteEnvironmentsResponseDTO;
import io.harness.ng.core.environment.dto.ScopedEnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.ScopedEnvironmentResponseDTO;
import io.harness.ng.core.environment.dto.SourceEnvironmentConfig;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentAuthorizeOperation;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.opa.gitx.EnvironmentOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverrides.resources.ServiceOverridesResource;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverridesMapperV2;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.beans.YamlValidationAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationListAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO.YamlValidationRequestDTOBuilder;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/environmentsV2")
@Path("/environmentsV2")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Environments", description = "This contains APIs related to Environments")
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
@ScopeInfoResolutionApi
@Slf4j
public class EnvironmentResourceV2 {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ServiceOverrideService serviceOverrideService;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final ServiceEntityValidationHelper serviceEntityValidationHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final EnvironmentGroupService environmentGroupService;
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final NGFeatureFlagHelperService featureFlagHelperService;
  private final ScopeAccessHelper scopeAccessHelper;
  private final EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  private EnvironmentRbacHelper environmentRbacHelper;
  private NGSettingsClient settingsClient;
  private ServiceOverridesResource serviceOverridesResource;
  private final ServiceOverridesServiceV2 serviceOverridesServiceV2;
  private EnvironmentCloneHelper environmentCloneHelper;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private ScopeInfoService scopeInfoService;
  private EnvironmentOpaStatusHandler environmentOpaStatusHandler;
  private CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;

  private static final int MAX_LIMIT = 1000;

  public static final String ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "Lists of Environment Identifiers and service identifiers for the entities";

  public static final String ENVIRONMENT_PARAM_MESSAGE = "Environment Identifier for the entity";

  private static final String TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE =
      "You cannot configure multiple Helm Repo Overrides for the service. Overrides provided: [%s]";

  @GET
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Gets a Environment by identifier", nickname = "getEnvironmentV2")
  @Operation(operationId = "getEnvironmentV2", summary = "Gets an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Environment")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<EnvironmentResponse>
  get(@Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Environment is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @Context ScopeInfo scopeInfo) {
    Optional<Environment> optionalEnvironment;
    boolean useScopeInfo = scopeInfo != null;
    if (useScopeInfo) {
      optionalEnvironment = environmentService.get(scopeInfo, environmentIdentifier, deleted,
          GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);

    } else {
      optionalEnvironment = environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier,
          deleted, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    }

    if (optionalEnvironment.isPresent()) {
      Environment environment = optionalEnvironment.get();
      if (isEmpty(environment.getYaml())) {
        if (useScopeInfo && !Objects.equals(scopeInfo.getUniqueId(), environment.getParentUniqueId())) {
          scopeInfo = scopeInfoService.getScopeInfo(accountId, Set.of(environment.getParentUniqueId()))
                          .get(environment.getParentUniqueId())
                          .orElse(null);
        }
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment, scopeInfo);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }

      if (GitXUtils.isRemoteEntity(environment)) {
        try {
          environmentEntityYamlSchemaHelper.validateSchema(
              accountId, useScopeInfo ? environment.getYaml(scopeInfo) : environment.getYaml());
        } catch (InvalidYamlException ex) {
          return ResponseDTO.newResponse(
              EnvironmentResponse.builder()
                  .environment(useScopeInfo ? writeDTO(environment, scopeInfo) : writeDTO(environment))
                  .createdAt(environment.getCreatedAt())
                  .lastModifiedAt(environment.getLastModifiedAt())
                  .entityValidityDetails(
                      EntityValidityDetails.builder()
                          .valid(false)
                          .invalidYaml(useScopeInfo ? environment.getYaml(scopeInfo) : environment.getYaml())
                          .build())
                  .build());
        }
      }
    } else {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    final ScopeInfo finalScopeInfo = scopeInfo;
    environmentRbacHelper.checkForAccessOrThrow(
        getEnvironmentAttributesMap(optionalEnvironment.get().getType().toString()),
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), environmentIdentifier,
        ENVIRONMENT_VIEW_PERMISSION);
    EnvironmentResponse response = useScopeInfo
        ? optionalEnvironment.map(env -> EnvironmentMapper.toResponseWrapper(env, finalScopeInfo)).orElse(null)
        : optionalEnvironment.map(EnvironmentMapper::toResponseWrapper).orElse(null);
    response.setEntityValidityDetails(EntityValidityDetails.builder().valid(true).build());
    cdOpaOnSaveStatusApiHelper
        .resolveGetOpaOnSaveStatus(optionalEnvironment.get(), accountId, finalScopeInfo, environmentOpaStatusHandler)
        .ifPresent(response::setOpaOnSaveStatus);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @ApiOperation(value = "Create an Environment", nickname = "createEnvironmentV2")
  @Operation(operationId = "createEnvironmentV2", summary = "Create an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Environment")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(
          description = "Details of the Environment to be created") @Valid EnvironmentRequestDTO environmentRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, getEnvironmentAttributesMap(environmentRequestDTO.getType().toString())),
        ENVIRONMENT_CREATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());
    Environment environmentEntity = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, scopeInfo);

    boolean useScopeInfo = scopeInfo != null;

    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(
          accountId, useScopeInfo ? environmentEntity.getYaml(scopeInfo) : environmentEntity.getYaml());
    }
    if (useScopeInfo) {
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
          scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), environmentEntity.getAccountId());
    } else {
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(environmentEntity.getOrgIdentifier(),
          environmentEntity.getProjectIdentifier(), environmentEntity.getAccountId());
    }
    EnvironmentGovernanceDataResponse createdEnvironmentMapper =
        environmentService.create(environmentEntity, scopeInfo);

    if (isOverridesV2Enabled(accountId)) {
      updateEnvSpecificOverrideV2(accountId, environmentEntity, scopeInfo);
    }

    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(
        createdEnvironmentMapper.getEnvironment(), createdEnvironmentMapper.getGovernanceMetadata(), scopeInfo));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Environments in batch", nickname = "createEnvironmentsV2")
  @Operation(operationId = "createEnvironmentsV2",
      summary = "Create Environments in batch with partial success support",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the batch creation result with successful and failed environments")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentBatchResponse>
  createEnvironmentsBatch(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environments to be created, maximum 1000 environments can be created.")
      @Size(max = MAX_LIMIT) List<EnvironmentRequestDTO> environmentRequestDTOs) throws IOException {
    if (isEmpty(environmentRequestDTOs)) {
      throw new InvalidRequestException("Environment request list cannot be empty");
    }

    // Convert DTOs to entities with validation
    List<Environment> environmentEntities = new ArrayList<>();
    List<EnvironmentFailureDTO> conversionFailures = new ArrayList<>();

    for (EnvironmentRequestDTO environmentRequestDTO : environmentRequestDTOs) {
      try {
        // Perform bean validation manually to support partial success
        validateEnvironmentRequestDTO(environmentRequestDTO);
        validateEnvironmentScope(environmentRequestDTO);
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                      environmentRequestDTO.getProjectIdentifier()),
            Resource.of(ENVIRONMENT, null, getEnvironmentAttributesMap(environmentRequestDTO.getType().toString())),
            ENVIRONMENT_CREATE_PERMISSION);
        environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());

        ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());

        orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
            scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), accountId);

        Environment environmentEntity =
            EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, scopeInfo);

        if (isEmpty(environmentRequestDTO.getYaml())) {
          environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentEntity.getYaml(scopeInfo));
        }

        environmentEntities.add(environmentEntity);
      } catch (InvalidRequestException | InvalidYamlException ex) {
        log.warn("Failed to convert environment DTO [{}]: {}", environmentRequestDTO.getIdentifier(), ex.getMessage());
        conversionFailures.add(EnvironmentFailureDTO.builder()
                                   .accountId(accountId)
                                   .orgIdentifier(environmentRequestDTO.getOrgIdentifier())
                                   .projectIdentifier(environmentRequestDTO.getProjectIdentifier())
                                   .identifier(environmentRequestDTO.getIdentifier())
                                   .status(Status.FAILURE)
                                   .errorMessage(format("Environment [%s] validation failed: %s",
                                       environmentRequestDTO.getIdentifier(), ex.getMessage()))
                                   .build());
      } catch (Exception ex) {
        log.error("Unexpected error converting environment DTO [{}]", environmentRequestDTO.getIdentifier(), ex);
        conversionFailures.add(
            EnvironmentFailureDTO.builder()
                .accountId(accountId)
                .orgIdentifier(environmentRequestDTO.getOrgIdentifier())
                .projectIdentifier(environmentRequestDTO.getProjectIdentifier())
                .identifier(environmentRequestDTO.getIdentifier())
                .status(Status.FAILURE)
                .errorMessage(format("Environment [%s] processing failed due to unexpected error: %s",
                    environmentRequestDTO.getIdentifier(), ex.getMessage()))
                .build());
      }
    }

    // Call service layer for bulk creation
    EnvironmentBatchResponse batchResponse = null;
    List<EnvironmentResponse> successfulEnvironments = Collections.emptyList();
    List<EnvironmentFailureDTO> serviceLayerFailures = Collections.emptyList();

    if (isNotEmpty(environmentEntities)) {
      batchResponse = environmentService.bulkCreate(accountId, environmentEntities);
      successfulEnvironments = batchResponse.getSuccessful();
      serviceLayerFailures = batchResponse.getFailed();
    }

    // Merge conversion failures with service layer failures
    List<EnvironmentFailureDTO> allFailures = new ArrayList<>(conversionFailures);
    allFailures.addAll(serviceLayerFailures);

    return ResponseDTO.newResponse(EnvironmentBatchResponse.builder()
                                       .successful(successfulEnvironments)
                                       .failed(allFailures)
                                       .totalRequested(environmentRequestDTOs.size())
                                       .totalSuccessful(successfulEnvironments.size())
                                       .totalFailed(allFailures.size())
                                       .build());
  }

  private void updateEnvSpecificOverrideV2(String accountId, Environment environmentEntity, ScopeInfo scopeInfo)
      throws IOException {
    boolean useScopeInfo = scopeInfo != null;
    String envGlobalOverrideIdentifier = generateEnvGlobalOverrideIdentifier(accountId,
        useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentEntity.getOrgIdentifier(),
        useScopeInfo ? scopeInfo.getProjectIdentifier() : environmentEntity.getProjectIdentifier(),
        environmentEntity.getIdentifier());
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentEntity, scopeInfo);

    Optional<ServiceOverrideRequestDTOV2> requestDTOV2 = ServiceOverridesMapperV2.toRequestDTOV2(ngEnvironmentConfig,
        accountId, useScopeInfo ? environmentEntity.getYaml(scopeInfo) : environmentEntity.getYaml());

    if (requestDTOV2.isPresent()) {
      if (scopeInfo == null) {
        scopeInfo = scopeInfoService.getScopeInfo(
            accountId, environmentEntity.getOrgIdentifier(), environmentEntity.getProjectIdentifier());
      }
      Optional<NGServiceOverridesEntity> envGlobalOverridesEntity =
          serviceOverridesServiceV2.getMetadata(scopeInfo, envGlobalOverrideIdentifier);
      // this will create inline overrides
      GitAwareContextHelper.updateGitEntityContextWithInlineStoreType();
      log.warn(format("Using environment v2 apis to manage overrides v2 for accountId: %s", accountId));
      if (envGlobalOverridesEntity.isPresent()) {
        serviceOverridesResource.update(accountId, requestDTOV2.get(), null);
      } else {
        serviceOverridesResource.create(accountId, requestDTOV2.get(), null);
      }
    }
  }

  @DELETE
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Delete en environment by identifier", nickname = "deleteEnvironmentV2")
  @Operation(operationId = "deleteEnvironmentV2", summary = "Delete an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the Environment is deleted")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE)
      @DefaultValue("false") boolean forceDelete, @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    Optional<Environment> environmentOptional = useScopeInfo
        ? environmentService.getMetadata(scopeInfo, environmentIdentifier, false)
        : environmentService.getMetadata(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);

    if (environmentOptional.isEmpty()) {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentOptional.get().getType() != null) {
      environmentAttributes.put("type", environmentOptional.get().getType().toString());
    }
    environmentRbacHelper.checkForAccessOrThrow(environmentAttributes,
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), environmentIdentifier,
        ENVIRONMENT_DELETE_PERMISSION);
    return useScopeInfo ? ResponseDTO.newResponse(environmentService.delete(scopeInfo, environmentIdentifier,
                              isNumeric(ifMatch) ? parseLong(ifMatch) : null, forceDelete))
                        : ResponseDTO.newResponse(environmentService.delete(accountId, orgIdentifier, projectIdentifier,
                              environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, forceDelete));
  }

  @PUT
  @ApiOperation(value = "Update an environment by identifier", nickname = "updateEnvironmentV2")
  @Operation(operationId = "updateEnvironmentV2", summary = "Update an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(
          description = "Details of the Environment to be updated") @Valid EnvironmentRequestDTO environmentRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated",
          hidden = true) @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentRequestDTO.getType() != null) {
      environmentAttributes = getEnvironmentAttributesMap(environmentRequestDTO.getType().toString());
    }
    environmentRbacHelper.checkForAccessOrThrow(environmentAttributes,
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, null);
    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, requestEnvironment.getYaml(scopeInfo));
    }
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    EnvironmentGovernanceDataResponse updatedEnvironmentMapper =
        environmentService.update(requestEnvironment, scopeInfo);

    if (isOverridesV2Enabled(accountId)) {
      updateEnvSpecificOverrideV2(accountId, requestEnvironment, scopeInfo);
    }
    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(
        updatedEnvironmentMapper.getEnvironment(), updatedEnvironmentMapper.getGovernanceMetadata(), scopeInfo));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an environment by identifier", nickname = "upsertEnvironmentV2")
  @Operation(operationId = "upsertEnvironmentV2", summary = "Upsert an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be updated")
      @Valid EnvironmentRequestDTO environmentRequestDTO) throws IOException {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO);
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentRequestDTO.getType() != null) {
      environmentAttributes = getEnvironmentAttributesMap(environmentRequestDTO.getType().toString());
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());

    environmentRbacHelper.checkForAccessOrThrow(environmentAttributes,
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    environmentEntityYamlSchemaHelper.validateSchema(accountId, environmentRequestDTO.getYaml());
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, null);
    if (isEmpty(environmentRequestDTO.getYaml())) {
      environmentEntityYamlSchemaHelper.validateSchema(accountId, requestEnvironment.getYaml(scopeInfo));
    }
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), requestEnvironment.getAccountId());
    EnvironmentGovernanceDataResponse upsertEnvironmentMapper =
        environmentService.upsert(requestEnvironment, UpsertOptions.DEFAULT, scopeInfo);

    if (isOverridesV2Enabled(accountId)) {
      updateEnvSpecificOverrideV2(accountId, requestEnvironment, scopeInfo);
    }
    return ResponseDTO.newResponse(EnvironmentMapper.toResponseWrapper(
        upsertEnvironmentMapper.getEnvironment(), upsertEnvironmentMapper.getGovernanceMetadata(), scopeInfo));
  }

  @GET
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentList")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Specifies sorting criteria of the list. Like sorting based on the last updated entity, "
              + "alphabetical sorting in an ascending or descending order") @QueryParam("sort") List<String> sort,
      @Context ScopeInfo scopeInfo) {
    boolean useRbacImprovement =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_ENV_LISTING_RBAC_IMPROVEMENT);
    if (!useRbacImprovement) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }
    Criteria criteria;
    boolean useScopeInfo = scopeInfo != null;
    if (useScopeInfo) {
      criteria = environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, null);
    } else {
      criteria = environmentFilterHelper.createCriteriaForGetList(
          accountId, orgIdentifier, projectIdentifier, false, searchTerm, null);
    }
    Pageable pageRequest;
    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    if (useRbacImprovement) {
      final Page<Environment> environmentPage =
          getRBACFilteredEnvironments(accountId, orgIdentifier, projectIdentifier, criteria, pageRequest);
      boolean useScopeInfoForYaml = scopeInfo != null;
      environmentPage.forEach(environment -> {
        if (environment == null) {
          return;
        }
        if (isEmpty(useScopeInfoForYaml ? environment.getYaml(scopeInfo) : environment.getYaml())) {
          environment.setYaml(environment.fetchNonEmptyYaml(scopeInfo));
        }
      });
      List<Environment> filteredContent =
          environmentPage.getContent().stream().filter(Objects::nonNull).collect(toList());
      Page<Environment> filteredPage =
          new PageImpl<>(filteredContent, environmentPage.getPageable(), environmentPage.getTotalElements());
      return ResponseDTO.newResponse(getNGPageResponse(filteredPage.map(env
          -> useScopeInfoForYaml ? EnvironmentMapper.toResponseWrapper(env, scopeInfo)
                                 : EnvironmentMapper.toResponseWrapper(env))));
    }
    return getEnvironmentsPageByCriteria(criteria, pageRequest, scopeInfo);
  }

  @POST
  @Path("scope-filtered-list")
  @Hidden
  @ApiOperation(hidden = true, value = "Get Scope Filtered Environment List", nickname = "getScopedEnvironments")
  @InternalApi
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<ScopedEnvironmentResponseDTO>> getScopedEnvironments(
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = "Environment Type") @QueryParam("envType") @ResourceIdentifier String envType,
      @RequestBody(description = "This is the body for filter properties like list of orgIds, projectIds and Scopes.")
      ScopedEnvironmentRequestDTO scopedEnvironmentRequestDTO) {
    Page<Environment> environmentEntities =
        environmentService.list(accountId, envType, scopedEnvironmentRequestDTO, page, size);

    Set<String> uniqueIds =
        environmentEntities.getContent().stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);

    return ResponseDTO.newResponse(getNGPageResponse(environmentEntities.map(env -> {
      ScopeInfo scopeInfo = scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty()).orElse(null);
      return EnvironmentMapper.toScopedResponseWrapper(env, scopeInfo);
    })));
  }

  @GET
  @Path("list/scoped")
  @Hidden
  @ApiOperation(value = "Gets environment list filtered by scoped env refs", nickname = "getEnvironmentListFiltered")
  @Operation(operationId = "getEnvironmentListFiltered", summary = "Gets Environment list filtered by scoped env refs",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Gets Environment list filtered by scoped env refs")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  getEnvironmentsFilteredByRefs(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                    NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Context ScopeInfo scopeInfo) {
    return doGetEnvironmentsFilteredByRefs(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifiers, scopeInfo);
  }

  @POST
  @Path("list/scoped")
  @Hidden
  @ApiOperation(value = "Gets environment list filtered by scoped env refs using POST",
      nickname = "getEnvironmentListFilteredPost")
  @Operation(operationId = "getEnvironmentListFilteredPost",
      summary = "Gets Environment list filtered by scoped env refs using POST",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Gets Environment list filtered by scoped env refs")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  getEnvironmentsFilteredByRefsPost(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                        NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of EnvironmentIds") List<String> envIdentifiers, @Context ScopeInfo scopeInfo) {
    return doGetEnvironmentsFilteredByRefs(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifiers, scopeInfo);
  }

  private ResponseDTO<PageResponse<EnvironmentResponse>> doGetEnvironmentsFilteredByRefs(int page, int size,
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers,
      ScopeInfo scopeInfo) {
    checkAccessForListingAtScope(accountId, orgIdentifier, projectIdentifier, envIdentifiers);
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, envIdentifiers, false, scopeInfo);
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));

    return getEnvironmentsPageByCriteria(criteria, pageRequest, scopeInfo);
  }

  @NotNull
  private ResponseDTO<PageResponse<EnvironmentResponse>> getEnvironmentsPageByCriteria(
      Criteria criteria, Pageable pageRequest, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
    environmentEntities.forEach(environment -> {
      if (isEmpty(useScopeInfo ? environment.getYaml(scopeInfo) : environment.getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment, scopeInfo);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(environmentEntities.map(env
        -> useScopeInfo ? EnvironmentMapper.toResponseWrapper(env, scopeInfo)
                        : EnvironmentMapper.toResponseWrapper(env))));
  }

  @GET
  @Path("/getActiveServiceInstancesForEnvironment")
  @ApiOperation(value = "Get list of instances grouped by service for particular environment",
      nickname = "getActiveServiceInstancesForEnvironment")
  @Hidden
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<InstanceGroupedByServiceList>
  getActiveServiceInstancesForEnvironment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(ENVIRONMENT_IDENTIFIER_KEY) String environmentIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) String serviceIdentifier,
      @QueryParam(NGCommonEntityConstants.BUILD_KEY) String buildId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedByServiceList(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier, buildId));
  }

  @POST
  @Path("/listV2")
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentListV2")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      },
      hidden = true)
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironmentsV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Specifies sorting criteria of the list. Like sorting based on the last updated entity, "
              + "alphabetical sorting in an ascending or descending order") @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Specify true if all accessible environments are to be included. Returns environments "
              + "at account/org/project level.") @QueryParam(NGResourceFilterConstants.INCLUDE_ALL_ACCESSIBLE_AT_SCOPE)
      @DefaultValue("false") boolean includeAllAccessibleAtScope,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam("repoName")
      String repoName, @Context ScopeInfo scopeInfo) {
    Criteria criteria;
    if (scopeInfo != null) {
      criteria = environmentFilterHelper.createCriteriaForGetList(
          scopeInfo, false, searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope, repoName);

    } else {
      criteria = environmentFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
          searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope, repoName);
    }
    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    final Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    final Page<Environment> environmentPage =
        getRBACFilteredEnvironments(accountId, orgIdentifier, projectIdentifier, criteria, pageRequest);

    environmentPage.forEach(environment -> {
      if (environment == null) {
        log.warn("Invalid environment found in the list. Skipping and continuing with other environments.");
        return;
      }
      if (isEmpty(environment.getYaml(scopeInfo))) {
        environment.setYaml(environment.fetchNonEmptyYaml(scopeInfo));
      }
    });

    List<Environment> filteredContent =
        environmentPage.getContent().stream().filter(Objects::nonNull).collect(toList());
    Page<Environment> filteredPage =
        new PageImpl<>(filteredContent, environmentPage.getPageable(), environmentPage.getTotalElements());
    Set<String> uniqueIds = filteredContent.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);

    Page<EnvironmentResponse> responsePage = filteredPage.map(env -> {
      Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty());
      return EnvironmentMapper.toResponseWrapper(env, scopeInfoOpt.orElse(null));
    });

    return ResponseDTO.newResponse(getNGPageResponse(responsePage));
  }

  @POST
  @Path("/listV2/access")
  @ApiOperation(value = "Gets environment access list", nickname = "getEnvironmentAccessListV2")
  @Operation(operationId = "getEnvironmentAccessListV2",
      summary = "Gets Environment Access list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project that are accessible")
      },
      hidden = true)
  @Timed
  @ResponseMetered
  public ResponseDTO<List<EnvironmentResponse>>
  listAccessEnvironmentsV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Specifies sorting criteria of the list. Like sorting based on the last updated entity, "
              + "alphabetical sorting in an ascending or descending order") @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Specify true if all accessible environments are to be included. Returns environments "
              + "at account/org/project level.") @QueryParam(NGResourceFilterConstants.INCLUDE_ALL_ACCESSIBLE_AT_SCOPE)
      @DefaultValue("false") boolean includeAllAccessibleAtScope,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        "Unable to list environments because the user is not having view access for the corresponding scope");
    Criteria criteria;
    if (scopeInfo != null) {
      criteria = environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, filterIdentifier,
          filterProperties, includeAllAccessibleAtScope, StringUtils.EMPTY);

    } else {
      criteria = environmentFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
          searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope, StringUtils.EMPTY);
    }

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    List<Environment> environments =
        environmentService.listAccess(criteria).stream().filter(Objects::nonNull).collect(toList());

    Set<String> uniqueIds = environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);

    List<EnvironmentResponse> environmentList =
        environments.stream()
            .map(env -> {
              Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty());
              return EnvironmentMapper.toResponseWrapper(env, scopeInfoOpt.orElse(null));
            })
            .collect(toList());

    List<PermissionCheckDTO> permissionCheckDTOS =
        environmentList.stream().map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO).collect(toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();

    return ResponseDTO.newResponse(
        environmentRbacHelper.filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList));
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets environment access list", nickname = "getEnvironmentAccessList")
  @Operation(operationId = "getEnvironmentAccessList", summary = "Gets Environment Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Environments that are accessible")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<List<EnvironmentResponse>>
  listAccessEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE) @QueryParam(
                            NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Environment group identifier") @QueryParam(
          "envGroupIdentifier") String envGroupIdentifier,
      @Parameter(description = "Environment type for the entity") @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_TYPE) EnvironmentType type,
      @Parameter(description = "Specifies sorting criteria of the list. Like sorting based on the last updated entity, "
              + "alphabetical sorting in an ascending or descending order") @QueryParam("sort") List<String> sort,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);

    List<EnvironmentResponse> environmentList;

    boolean useScopeInfoForEnvGrp =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY);
    boolean isCrossScopedEnabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_CROSS_SCOPED_ENV_GROUPS);

    if (isEmpty(envIdentifiers) && isNotEmpty(envGroupIdentifier)) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity =
          environmentGroupService.get(scopeInfo, envGroupIdentifier, false);

      // Note: The scope of environment group may not be same as the scope passed in request. So, this needs to be
      // calculated again
      IdentifierRef envGroupIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envGroupIdentifier, accountId, orgIdentifier, projectIdentifier);

      List<String> envIdsFromGroup =
          environmentGroupEntity.map(EnvironmentGroupEntity::getEnvIdentifiers)
              .orElseThrow(()
                               -> new InvalidRequestException(
                                   format("Could not find environment group with identifier: %s", envGroupIdentifier)));

      ScopeInfo scopeInfoOfEnvGrp = scopeInfo;
      if (useScopeInfoForEnvGrp) {
        scopeInfoOfEnvGrp = scopeInfoService.getScopeInfoFromParentUniqueId(
            accountId, environmentGroupEntity.get().getParentUniqueId());
      }

      envIdentifiers.addAll(envIdsFromGroup);
      if (isCrossScopedEnabled) {
        environmentList =
            environmentService.listEnvironmentsForCrossScopedEnvGroup(scopeInfoOfEnvGrp, envIdentifiers, searchTerm);
      } else {
        environmentList = environmentService.listEnvironmentsForSameScopedEnvGroup(
            scopeInfoOfEnvGrp, envIdentifiers, searchTerm, type);
      }

    } else {
      Criteria criteria = environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, type);

      if (isNotEmpty(envIdentifiers)) {
        criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
      }

      List<Environment> environments =
          environmentService.listAccess(criteria).stream().filter(Objects::nonNull).collect(toList());

      Set<String> uniqueIds = environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());

      Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);

      environmentList = environments.stream()
                            .map(env -> {
                              Optional<ScopeInfo> scopeInfoOpt =
                                  scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty());
                              return EnvironmentMapper.toResponseWrapper(env, scopeInfoOpt.orElse(null));
                            })
                            .collect(toList());
    }

    List<PermissionCheckDTO> permissionCheckDTOS =
        environmentList.stream().map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO).collect(toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return ResponseDTO.newResponse(
        environmentRbacHelper.filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList));
  }

  /*
  This API is similar to the listV2 API. But it has a new query field editOnlyRBACPermissions added.
  Also, internally it uses the getRBACFilteredEnvironmentsV2 method while the earlier API uses
  getRBACFilteredEnvironments method.
   */
  @POST
  @Path("v3/list")
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentListV3")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      },
      hidden = true)
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironmentsV3(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Specifies sorting criteria of the list. Like sorting based on the last updated entity, "
              + "alphabetical sorting in an ascending or descending order") @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Specify true if all accessible environments are to be included. Returns environments "
              + "at account/org/project level.") @QueryParam(NGResourceFilterConstants.INCLUDE_ALL_ACCESSIBLE_AT_SCOPE)
      @DefaultValue("false") boolean includeAllAccessibleAtScope,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName,
      @Parameter(description = "Specify true if only environments with edit permission are required. Default value "
              + "false will mean environments with view permission are required.",
          hidden = true) @QueryParam("editOnlyRBACPermission") @DefaultValue("false") boolean editOnlyRBACPermissions,
      @Context ScopeInfo scopeInfo) {
    Criteria criteria;
    if (scopeInfo != null) {
      criteria = environmentFilterHelper.createCriteriaForGetList(
          scopeInfo, false, searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope, repoName);

    } else {
      criteria = environmentFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false,
          searchTerm, filterIdentifier, filterProperties, includeAllAccessibleAtScope, repoName);
    }

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    final Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    String environmentRBACPermission = ENVIRONMENT_VIEW_PERMISSION;
    if (editOnlyRBACPermissions) {
      environmentRBACPermission = ENVIRONMENT_UPDATE_PERMISSION;
    }

    final Page<Environment> environmentPage =
        getRBACFilteredEnvironmentsV2(criteria, pageRequest, environmentRBACPermission);

    environmentPage.forEach(environment -> {
      if (environment == null) {
        log.warn("Invalid environment found in the list. Skipping and continuing with other environments.");
        return;
      }
      if (isEmpty(environment.getYaml(scopeInfo))) {
        environment.setYaml(environment.fetchNonEmptyYaml(scopeInfo));
      }
    });

    List<Environment> filteredContent =
        environmentPage.getContent().stream().filter(Objects::nonNull).collect(toList());

    Set<String> uniqueIds = filteredContent.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountId, uniqueIds);

    Page<Environment> filteredPage =
        new PageImpl<>(filteredContent, environmentPage.getPageable(), environmentPage.getTotalElements());

    return ResponseDTO.newResponse(getNGPageResponse(filteredPage.map(env -> {
      Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty());
      return EnvironmentMapper.toResponseWrapper(env, scopeInfoOpt.orElse(null));
    })));
  }

  @GET
  @Path("/dummy-env-api")
  @ApiOperation(value = "This is dummy api to expose NGEnvironmentConfig", nickname = "dummyNGEnvironmentConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<NGEnvironmentConfig> getNGEnvironmentConfig() {
    return ResponseDTO.newResponse(NGEnvironmentConfig.builder().build());
  }

  @POST
  @Path("/mergeEnvironmentInputs/{environmentIdentifier}")
  @ApiOperation(value = "This api merges old and new environment inputs YAML", nickname = "mergeEnvironmentInputs")
  @Hidden
  @NGAccessControlCheck(resourceType = ENVIRONMENT, permission = ENVIRONMENT_VIEW_PERMISSION)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentInputsMergedResponseDto> mergeEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      String oldEnvironmentInputsYaml, @Context ScopeInfo scopeInfo) {
    if (scopeInfo != null) {
      return ResponseDTO.newResponse(
          environmentService.mergeEnvironmentInputs(scopeInfo, environmentIdentifier, oldEnvironmentInputsYaml));

    } else {
      return ResponseDTO.newResponse(environmentService.mergeEnvironmentInputs(
          accountId, orgIdentifier, projectIdentifier, environmentIdentifier, oldEnvironmentInputsYaml));
    }
  }

  @POST
  @Path("/serviceOverrides")
  @ApiOperation(value = "upsert a Service Override for an Environment", nickname = "upsertServiceOverride")
  @Operation(operationId = "upsertServiceOverride", summary = "upsert a Service Override for an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Upsert ( Create/Update )  a Service Override in an Environment.")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceOverrideResponseDTO>
  upsertServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Service Override to be upserted")
      @Valid ServiceOverrideRequestDTO serviceOverrideRequestDTO) throws IOException {
    throwExceptionForInvalidRequestDTO(serviceOverrideRequestDTO);
    validateServiceOverrideScope(serviceOverrideRequestDTO);

    NGServiceOverridesEntity overridesEntity =
        ServiceOverridesMapper.toServiceOverridesEntity(accountId, serviceOverrideRequestDTO);

    boolean overridesV2Enabled = isOverridesV2Enabled(accountId);
    if (overridesV2Enabled) {
      log.warn(format(
          "Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
          serviceOverrideRequestDTO.getProjectIdentifier(), serviceOverrideRequestDTO.getOrgIdentifier(), accountId));

      ServiceOverridesResponseDTOV2 responseDTOV2 = upsertByOverrideV2Resource(accountId, overridesEntity,
          serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier());
      return ResponseDTO.newResponse(
          ServiceOverridesMapperV2.toResponseDTOV1(responseDTOV2, overridesEntity.getYaml()));
    }

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(serviceOverrideRequestDTO.getOrgIdentifier(),
        serviceOverrideRequestDTO.getProjectIdentifier(), overridesEntity.getAccountId());
    environmentValidationHelper.checkThatEnvExists(overridesEntity.getAccountId(),
        serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier(),
        overridesEntity.getEnvironmentRef());
    serviceEntityValidationHelper.checkThatServiceExists(overridesEntity.getAccountId(),
        serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier(),
        overridesEntity.getServiceRef());
    checkForServiceOverrideUpdateAccess(accountId, serviceOverrideRequestDTO.getOrgIdentifier(),
        serviceOverrideRequestDTO.getProjectIdentifier(), overridesEntity.getEnvironmentRef(),
        overridesEntity.getServiceRef());
    validateServiceOverrides(overridesEntity, serviceOverrideRequestDTO.getOrgIdentifier(),
        serviceOverrideRequestDTO.getProjectIdentifier());

    NGServiceOverridesEntity createdServiceOverride = serviceOverrideService.upsert(overridesEntity,
        serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier());

    return ResponseDTO.newResponse(ServiceOverridesMapper.toResponseWrapper(createdServiceOverride, false,
        serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier()));
  }

  private ServiceOverridesResponseDTOV2 upsertByOverrideV2Resource(String accountId,
      NGServiceOverridesEntity overridesEntity, String orgIdentifier, String projectIdentifier) throws IOException {
    ServiceOverrideRequestDTOV2 requestV2 =
        ServiceOverridesMapperV2.toRequestV2(overridesEntity, orgIdentifier, projectIdentifier);
    // Assumption
    // 1: Type and Yaml field will not be null/empty as from previous migration -
    // AddServiceOverrideV2RelatedFieldsMigration
    // 2: Only one entity either v1 or v2 will exist for given criteria

    Optional<NGServiceOverridesEntity> overrideEntityInDB = serviceOverrideService.getForV1AndV2(accountId,
        orgIdentifier, projectIdentifier, overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef());
    ResponseDTO<ServiceOverridesResponseDTOV2> apiResponseV2 = null;
    if (overrideEntityInDB.isPresent()) {
      apiResponseV2 = serviceOverridesResource.update(accountId, requestV2, null);
    } else {
      apiResponseV2 = serviceOverridesResource.create(accountId, requestV2, null);
    }
    return apiResponseV2.getData();
  }

  @POST
  @Path("/environmentInputYamlAndServiceOverridesMetadata")
  @ApiOperation(value = "This api returns environments runtime input YAML and serviceOverrides Yaml",
      nickname = "getEnvironmentsInputYamlAndServiceOverrides")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<EnvironmentInputSetYamlAndServiceOverridesMetadataDTO>
  getEnvironmentsInputYamlAndServiceOverrides(
      @Parameter(description = ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
      @NotNull EnvironmentInputsetYamlAndServiceOverridesMetadataInput environmentYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    List<String> envIdentifiers = new ArrayList<>();
    if (isNotEmpty(environmentYamlMetadata.getEnvIdentifiers())) {
      envIdentifiers.addAll(environmentYamlMetadata.getEnvIdentifiers());
    }
    if (isNotEmpty(environmentYamlMetadata.getEnvGroupIdentifier())
        && !EngineExpressionEvaluator.hasExpressions(environmentYamlMetadata.getEnvGroupIdentifier())) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity = useScopeInfo
          ? environmentGroupService.get(scopeInfo, environmentYamlMetadata.getEnvGroupIdentifier(), false)
          : environmentGroupService.get(
                accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getEnvGroupIdentifier(), false);
      environmentGroupEntity.ifPresent(groupEntity -> envIdentifiers.addAll(groupEntity.getEnvIdentifiers()));
    }
    boolean isServiceOverrideV2Enabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
    EnvironmentInputSetYamlAndServiceOverridesMetadataDTO environmentInputsetYamlandServiceOverridesMetadataDTO =
        environmentService.getEnvironmentsInputYamlAndServiceOverridesMetadata(accountId, orgIdentifier,
            projectIdentifier, envIdentifiers, environmentYamlMetadata.getServiceIdentifiers(),
            isServiceOverrideV2Enabled);

    return ResponseDTO.newResponse(environmentInputsetYamlandServiceOverridesMetadataDTO);
  }

  @POST
  @Path("/check-allowed-values")
  @ApiOperation(value = "Check for allowed-values in the Environments", nickname = "checkAllowedValuesInEnvs")
  @Operation(operationId = "checkAllowedValuesInEnvs", summary = "Check for allowed-values in the Environments",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Environments using allowed-values")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<AllowedValuesUsagesInternalDTO>
  checkAllowedValues(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody AllowedValuesUsagesRequestDTO request) {
    return ResponseDTO.newResponse(environmentService.checkForAllowedValues(accountId, request));
  }

  @POST
  @Path("v2/env-service-override-metadata")
  @ApiOperation(value = "This api returns environments runtime input YAML and serviceOverrides Yaml",
      nickname = "getEnvironmentsInputYamlAndServiceOverridesV2")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<EnvironmentInputSetYamlAndServiceOverridesMetadataDTO>
  getEnvironmentsInputYamlAndServiceOverridesV2(
      @Parameter(description = ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
      @NotNull EnvironmentAndServiceOverridesMetadataInput environmentYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info for the Base entity")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache") @HeaderParam("Load-From-Cache")
      @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo) {
    // get environment ref-> branch map
    Map<String, String> environmentRefBranchMap = getRefBranchMap(
        accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getEntityWithGitInfoList());
    List<String> envRefs = new ArrayList<>(environmentRefBranchMap.keySet());

    addEnvRefsFromEnvGroup(environmentYamlMetadata, accountId, orgIdentifier, projectIdentifier, envRefs, scopeInfo);

    boolean isServiceOverrideV2Enabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
    Map<String, String> serviceRefBranchMap;
    // get service ref-> branch map
    if (isNotEmpty(environmentYamlMetadata.getServiceWithGitInfoList())) {
      serviceRefBranchMap = getRefBranchMap(
          accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getServiceWithGitInfoList());
    } else {
      serviceRefBranchMap = new HashMap<>();
      for (String serviceId : environmentYamlMetadata.getServiceIdentifiers()) {
        if (isNotEmpty(serviceId)) {
          serviceRefBranchMap.put(
              IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgIdentifier, projectIdentifier, serviceId),
              null);
        }
      }
    }

    EnvironmentInputSetYamlAndServiceOverridesMetadataDTO responseDTO =
        environmentService.getEnvironmentsInputYamlAndServiceOverridesMetadata(accountId, orgIdentifier,
            projectIdentifier, envRefs, environmentRefBranchMap, serviceRefBranchMap, isServiceOverrideV2Enabled,
            GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache));

    return ResponseDTO.newResponse(responseDTO);
  }

  private void addEnvRefsFromEnvGroup(EnvironmentAndServiceOverridesMetadataInput environmentYamlMetadata,
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefs, ScopeInfo scopeInfo) {
    if (isNotEmpty(environmentYamlMetadata.getEnvGroupIdentifier())
        && !EngineExpressionEvaluator.hasExpressions(environmentYamlMetadata.getEnvGroupIdentifier())) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity = scopeInfo != null
          ? environmentGroupService.get(scopeInfo, environmentYamlMetadata.getEnvGroupIdentifier(), false)
          : environmentGroupService.get(
                accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getEnvGroupIdentifier(), false);

      if (environmentGroupEntity.isPresent() && isNotEmpty(environmentGroupEntity.get().getEnvIdentifiers())) {
        envRefs.addAll(environmentGroupEntity.get().getEnvIdentifiers());
      }
    }
  }

  public void validateServiceOverrides(
      NGServiceOverridesEntity serviceOverridesEntity, String orgIdentifier, String projectIdentifier) {
    final NGServiceOverrideConfig serviceOverrideConfig = toNGServiceOverrideConfig(serviceOverridesEntity);
    if (serviceOverrideConfig.getServiceOverrideInfoConfig() != null) {
      final NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          serviceOverrideConfig.getServiceOverrideInfoConfig();

      if (isEmpty(serviceOverrideInfoConfig.getManifests()) && isEmpty(serviceOverrideInfoConfig.getConfigFiles())
          && isEmpty(serviceOverrideInfoConfig.getVariables())
          && serviceOverrideInfoConfig.getApplicationSettings() == null
          && serviceOverrideInfoConfig.getConnectionStrings() == null) {
        final Optional<NGServiceOverridesEntity> optionalNGServiceOverrides =
            serviceOverrideService.get(serviceOverridesEntity.getAccountId(), orgIdentifier, projectIdentifier,
                serviceOverridesEntity.getEnvironmentRef(), serviceOverridesEntity.getServiceRef());
        if (optionalNGServiceOverrides.isEmpty()) {
          throw new InvalidRequestException("No overrides found in request");
        }
      }

      checkDuplicateManifestIdentifiersWithIn(serviceOverrideInfoConfig.getManifests());
      validateNoMoreThanOneHelmOverridePresent(
          serviceOverrideInfoConfig.getManifests(), TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE);
      checkDuplicateConfigFilesIdentifiersWithIn(serviceOverrideInfoConfig.getConfigFiles());

      ServiceOverrideValidatorService.validateAllowedManifestTypesInOverrides(
          serviceOverrideInfoConfig.getManifests(), "service overrides");
    }
  }

  @DELETE
  @Path("/serviceOverrides")
  @ApiOperation(value = "Delete a Service Override entity", nickname = "deleteServiceOverride")
  @Operation(operationId = "deleteServiceOverride", summary = "Delete a ServiceOverride entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Service Override is deleted")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  deleteServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    serviceEntityValidationHelper.checkThatServiceExists(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    // check access for service and env
    checkForServiceOverrideUpdateAccess(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);

    boolean overridesV2Enabled = isOverridesV2Enabled(accountId);

    if (overridesV2Enabled) {
      log.warn(
          format("Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
              projectIdentifier, orgIdentifier, accountId));
    }

    return overridesV2Enabled
        ? serviceOverridesResource.delete(generateServiceOverrideIdentifier(environmentIdentifier, serviceIdentifier),
              accountId, orgIdentifier, projectIdentifier, scopeInfo)
        : ResponseDTO.newResponse(serviceOverrideService.delete(
              accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier));
  }

  @GET
  @Path("/dummy-api-for-exposing-objects")
  @ApiOperation(value = "This is dummy api to expose objects to swagger", nickname = "dummyNGServiceOverrideConfig")
  @Hidden
  @ScopeInfoResolutionExemptedApi
  // do not delete this.
  public ResponseDTO<EnvSwaggerObjectWrapper> exposeSwaggerObjects() {
    return ResponseDTO.newResponse(EnvSwaggerObjectWrapper.builder().build());
  }

  @GET
  @Path("/serviceOverrides")
  @ApiOperation(value = "Gets Service Overrides list ", nickname = "getServiceOverridesList")
  @Operation(operationId = "getServiceOverridesList", summary = "Gets Service Overrides list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Service Overrides for an Environment."
                + "serviceIdentifier, if passed, can be used to get the overrides for that particular Service in the "
                + "Environment")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<ServiceOverrideResponseDTO>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("500") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier @NotNull String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = "Specifies the sorting criteria of the list. Like sorting based on the last updated "
              + "entity, alphabetical sorting in an ascending or descending order") @QueryParam("sort")
      List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    if (isNotEmpty(serviceIdentifier)) {
      serviceEntityValidationHelper.checkThatServiceExists(
          accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION,
        "Unauthorized to view environment");

    Criteria criteria = environmentFilterHelper.createCriteriaForGetServiceOverrides(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    Pageable pageRequest;

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<NGServiceOverridesEntity> serviceOverridesEntities = serviceOverrideService.list(criteria, pageRequest);
    boolean overridesV2Enabled = isOverridesV2Enabled(accountId);
    if (overridesV2Enabled) {
      log.warn(
          format("Using service override v1 api with override v2 enabled in projectId: %s, orgId: %s, accountId: %s",
              projectIdentifier, orgIdentifier, accountId));
    }
    return ResponseDTO.newResponse(getNGPageResponse(serviceOverridesEntities.map(serviceOverridesEntity
        -> ServiceOverridesMapper.toResponseWrapper(
            serviceOverridesEntity, overridesV2Enabled, orgIdentifier, projectIdentifier))));
  }

  @GET
  @Path("/runtimeInputs")
  @ApiOperation(value = "This api returns Environment inputs YAML", nickname = "getEnvironmentInputs")
  @Hidden
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<NGEntityTemplateResponseDTO> getEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    String environmentInputsYaml;
    if (scopeInfo != null) {
      environmentInputsYaml = environmentService.createEnvironmentInputsYaml(scopeInfo, environmentIdentifier, null);

    } else {
      environmentInputsYaml = environmentService.createEnvironmentInputsYaml(
          accountId, orgIdentifier, projectIdentifier, environmentIdentifier, null);
    }

    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(environmentInputsYaml).build());
  }

  @GET
  @Path("/serviceOverrides/runtimeInputs")
  @ApiOperation(value = "This api returns Service Override inputs YAML", nickname = "getServiceOverrideInputs")
  @Hidden
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<NGEntityTemplateResponseDTO> getServiceOverrideInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier) {
    String serviceOverrideInputsYaml = serviceOverrideService.createServiceOverrideInputsYaml(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(serviceOverrideInputsYaml).build());
  }

  @GET
  @Hidden
  @Path("/attributes")
  @ApiOperation(hidden = true, value = "Get Environments Attributes", nickname = "getEnvironmentsAttributes")
  @InternalApi
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<List<Map<String, String>>> getEnvironmentsAttributes(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("envIdentifiers") List<String> envIdentifiers, @Context ScopeInfo scopeInfo) {
    return scopeInfo != null ? ResponseDTO.newResponse(environmentService.getAttributes(scopeInfo, envIdentifiers))
                             : ResponseDTO.newResponse(environmentService.getAttributes(
                                   accountId, orgIdentifier, projectIdentifier, envIdentifiers));
  }

  @GET
  @Path("/remote-environments-metadata")
  @ApiOperation(value = "List remote environments grouped by repository for a given accountId",
      nickname = "getRemoteEnvironmentsMetadata")
  @Operation(operationId = "getRemoteEnvironmentsMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote environments in an account along with "
          + "environment metadata. Optionally filter by repoName.",
      summary = "List remote environments grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the environment file paths in each repo")
      })
  @InternalApi
  @Hidden
  public ResponseDTO<RemoteEnvironmentsResponseDTO>
  getRemoteEnvironmentsMetadata(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote environments only for the given repoName.")
      @QueryParam(NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_ENVIRONMENT_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      EnvironmentRemoteRepoListResponse serviceResponse = environmentService.getRemoteRepoListForAGivenScope(
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<EnvironmentRemoteRepoInfo> serviceRepos =
          serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();
      List<RemoteEnvironmentsDTO> resourceRepos =
          serviceRepos.stream()
              .map(info
                  -> RemoteEnvironmentsDTO.builder()
                         .repoName(info.getRepoName())
                         .repoURL(info.getRepoURL())
                         .count(info.getCount())
                         .filePathsByOwningScope(info.getFilePathsByOwningScope())
                         .connectorRefs(info.getConnectorRefs())
                         .build())
              .collect(toList());
      long totalEnvironments = serviceRepos.stream().mapToLong(EnvironmentRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_ENVIRONMENT_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalEnvironments={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceResponse.getTotalRepos(),
          resourceRepos.size(), totalEnvironments, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemoteEnvironmentsResponseDTO.builder()
                                         .totalEnvironments(totalEnvironments)
                                         .totalRepos(serviceResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error("[REMOTE_ENVIRONMENT_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  @POST
  @Path("/move-config/{environmentIdentifier}")
  @ApiOperation(value = "Move environment YAML from inline to remote", nickname = "moveEnvironmentConfigs")
  @Operation(operationId = "moveEnvironmentConfigs", summary = "Move environment YAML from inline to remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Move environment YAML from inline to remote")
      })
  public ResponseDTO<EnvironmentMoveConfigResponse>
  moveConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @BeanParam EnvironmentMoveConfigRequestDTO environmentRequestDTO, @Context ScopeInfo scopeInfo) {
    // check for environment update permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_UPDATE_PERMISSION);

    EnvironmentMoveConfigOperationDTO moveConfigOperationDTO =
        EnvironmentMoveConfigOperationDTO.builder()
            .repoName(environmentRequestDTO.getRepoName())
            .branch(environmentRequestDTO.getBranch())
            .moveConfigOperationType(
                MoveConfigOperationType.getMoveConfigType(environmentRequestDTO.getMoveConfigOperationType()))
            .connectorRef(environmentRequestDTO.getConnectorRef())
            .isHarnessCodeRepo(environmentRequestDTO.getIsHarnessCodeRepo())
            .baseBranch(environmentRequestDTO.getBaseBranch())
            .commitMessage(environmentRequestDTO.getCommitMsg())
            .isNewBranch(environmentRequestDTO.getIsNewBranch())
            .filePath(environmentRequestDTO.getFilePath())
            .build();

    EnvironmentMoveConfigResponse environmentMoveConfigResponse = environmentService.moveEnvironment(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, moveConfigOperationDTO, scopeInfo);
    return ResponseDTO.newResponse(environmentMoveConfigResponse);
  }

  @PUT
  @Path("/{environmentIdentifier}/update-git-metadata")
  @ApiOperation(value = "Update git-metadata in remote environment Entity", nickname = "updateEnvironmentGitDetails")
  @Operation(operationId = "updateEnvironmentGitDetails",
      description = "Update git-metadata in remote environment and returns the identifier of updated environment",
      summary = "Update git-metadata in remote environment Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns identifier of updated environment")
      })
  public ResponseDTO<EnvironmentGitUpdateResponseDTO>
  updateGitMetadataForEnvironment(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @BeanParam GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_UPDATE_PERMISSION,
        UNAUTHORIZED_TO_UPDATE_ENVIRONMENT_MESSAGE);

    String environmentAfterGitMetadataUpdate =
        environmentService.updateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier,
            EnvironmentGitMetadataUpdateParams.builder()
                .connectorRef(gitMetadataUpdateRequestInfo.getConnectorRef())
                .filePath(gitMetadataUpdateRequestInfo.getFilePath())
                .repoName(gitMetadataUpdateRequestInfo.getRepoName())
                .build(),
            scopeInfo);
    return ResponseDTO.newResponse(
        EnvironmentGitUpdateResponseDTO.builder().identifier(environmentAfterGitMetadataUpdate).build());
  }

  @POST
  @Path("/import")
  @ApiOperation(value = "Get Environment YAML from Git Repository", nickname = "importEnvironment")
  @Operation(operationId = "importEnvironment", summary = "Import and Create Environment from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Import and Create Environment from Git Repository and saves a record for it in Harness")
      })
  public ResponseDTO<EnvironmentImportResponseDTO>
  importEnvironmentFromGit(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO, @Context ScopeInfo scopeInfo) throws IOException {
    validateEnvironmentScope(orgIdentifier, projectIdentifier);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        orgIdentifier, projectIdentifier, accountIdentifier);
    EnvironmentAuthorizeOperation rbac = (env) -> {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null, getEnvironmentAttributesMap(env.getType())), ENVIRONMENT_CREATE_PERMISSION);
    };

    EnvironmentGovernanceDataResponse environmentGovernanceDataResponse =
        environmentService.importEnvironmentFromRemote(accountIdentifier, orgIdentifier, projectIdentifier,
            environmentIdentifier,
            EnvironmentImportOperationDTO.builder()
                .connectorRef(gitImportInfoDTO.getConnectorRef())
                .repoName(gitImportInfoDTO.getRepoName())
                .branch(gitImportInfoDTO.getBranch())
                .filePath(gitImportInfoDTO.getFilePath())
                .isHarnessCodeRepo(gitImportInfoDTO.getIsHarnessCodeRepo())
                .isForceImport(gitImportInfoDTO.getIsForceImport())
                .build(),
            rbac);

    if (isOverridesV2Enabled(accountIdentifier)) {
      updateEnvSpecificOverrideV2(accountIdentifier, environmentGovernanceDataResponse.getEnvironment(), scopeInfo);
    }

    return ResponseDTO.newResponse(
        EnvironmentImportResponseDTO.builder()
            .envIdentifier(environmentGovernanceDataResponse.getEnvironment().getIdentifier())
            .governanceMetadata(environmentGovernanceDataResponse.getGovernanceMetadata())
            .build());
  }

  @POST
  @Path("/force-import")
  @Hidden
  @ApiOperation(value = "Force Import a Environment", nickname = "forceImportEnvironment")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ForceImportEnvironmentResponse> forceImportEnvironment(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      ForceImportEnvironmentRequestDTO requestDTO) {
    Map<String, String> environmentAttributes =
        requestDTO.getType() != null ? getEnvironmentAttributesMap(requestDTO.getType()) : new HashMap<>();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, environmentAttributes), ENVIRONMENT_CREATE_PERMISSION);
    ForceImportEnvironmentYamlOperationDTO operationDTO = ForceImportEnvironmentYamlOperationDTO.builder()
                                                              .branch(requestDTO.getBranch())
                                                              .repoName(requestDTO.getRepoName())
                                                              .connectorRef(requestDTO.getConnectorRef())
                                                              .filePath(requestDTO.getFilePath())
                                                              .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
                                                              .identifier(requestDTO.getIdentifier())
                                                              .orgIdentifier(requestDTO.getOrgIdentifier())
                                                              .projectIdentifier(requestDTO.getProjectIdentifier())
                                                              .type(requestDTO.getType())
                                                              .build();

    ForceImportEnvironmentResponse response =
        environmentService.forceImportEnvironment(accountIdentifier, operationDTO);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("/validate-yaml")
  @Hidden
  @ApiOperation(
      value = "This api return the validation result of Environment yaml", nickname = "validateEnvironmentYaml")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<YamlValidationListAPIResponse>
  validateEnvironmentYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    final YamlValidationRequestDTO yamlValidationRequestDTO = getYamlValidationRequestDTO(yamlValidationRequestBody);
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        environmentService.validateEnvironmentYaml(accountIdentifier, yamlValidationRequestDTO);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
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

  @POST
  @Path("/clone")
  @ApiOperation(value = "Clone an Environment", nickname = "cloneEnvironmentV2")
  @Operation(operationId = "cloneEnvironmentV2", summary = "Clone an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the cloned Environment")
      })
  @Timed
  @Hidden
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentCloneResponseDTO>
  cloneEnvironment(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be created")
      @Valid EnvironmentCloneRequestDTO environmentCloneRequestDTO) {
    SourceEnvironmentConfig sourceEnvConfig = environmentCloneRequestDTO.getSourceConfig();
    DestinationEnvironmentConfig destinationEnvConfig = environmentCloneRequestDTO.getDestinationConfig();
    validateEnvironmentScope(destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier());

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier());

    EnvironmentCloneResponse environmentCloneResponse = environmentCloneHelper.cloneEnvironment(
        accountId, sourceEnvConfig, destinationEnvConfig, environmentCloneRequestDTO.isCloneInfrastructures());

    List<InfrastructureResponse> infrastructureResponseList = new ArrayList<>();
    for (InfrastructureEntity clonedinfrastructureEntity : environmentCloneResponse.getInfrastructureEntities()) {
      infrastructureResponseList.add(InfrastructureMapper.toResponseWrapper(clonedinfrastructureEntity));
    }

    List<String> cloneFailedinfrastructureResponseList = new ArrayList<>();
    for (String cloneFailedInfraIdentifier : environmentCloneResponse.getCloneFailedInfrastructures()) {
      cloneFailedinfrastructureResponseList.add(cloneFailedInfraIdentifier);
    }

    if (environmentCloneResponse.getEnvironment().getParentUniqueId() != scopeInfo.getUniqueId()) {
      scopeInfo = ScopeInfo.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(destinationEnvConfig.getOrgIdentifier())
                      .projectIdentifier(destinationEnvConfig.getProjectIdentifier())
                      .uniqueId(environmentCloneResponse.getEnvironment().getParentUniqueId())
                      .build();
    }

    EnvironmentCloneResponseDTO responseDTO =
        EnvironmentCloneResponseDTO.builder()
            .environment(writeDTO(environmentCloneResponse.getEnvironment(), scopeInfo))
            .createdAt(environmentCloneResponse.getEnvironment().getCreatedAt())
            .lastModifiedAt(environmentCloneResponse.getEnvironment().getLastModifiedAt())
            .infrastructureResponseList(infrastructureResponseList)
            .cloneFailedInfrastructures(cloneFailedinfrastructureResponseList)
            .governanceMetadata(environmentCloneResponse.getGovernanceMetadata())
            .build();

    return ResponseDTO.newResponse(responseDTO);
  }

  private void checkAccessForListingAtScope(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers) {
    if (isEmpty(envIdentifiers)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
      return;
    }

    boolean checkProjectLevelList = false;
    boolean checkOrgLevelList = false;
    boolean checkAccountLevelList = false;

    if (isNotEmpty(envIdentifiers)) {
      for (String envRef : envIdentifiers) {
        if (isNotEmpty(envRef) && !EngineExpressionEvaluator.hasExpressions(envRef)) {
          IdentifierRef envIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);
          if (io.harness.encryption.Scope.PROJECT.equals(envIdentifierRef.getScope())) {
            checkProjectLevelList = true;
          } else if (io.harness.encryption.Scope.ORG.equals(envIdentifierRef.getScope())) {
            checkOrgLevelList = true;
          } else if (io.harness.encryption.Scope.ACCOUNT.equals(envIdentifierRef.getScope())) {
            checkAccountLevelList = true;
          }
        }
      }
    }

    // listing without scoped refs
    if (checkProjectLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }

    if (checkOrgLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, null),
          Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }

    if (checkAccountLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, null, null), Resource.of(ENVIRONMENT, null),
          ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    }
  }

  private void checkForServiceOverrideUpdateAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    final List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                     .resourceIdentifier(environmentRef)
                                     .resourceType(ENVIRONMENT)
                                     .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                     .build());
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(environmentRef, accountId, orgIdentifier, projectIdentifier);
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                     .resourceIdentifier(envIdentifierRef.getIdentifier())
                                     .resourceType(ENVIRONMENT)
                                     .resourceScope(ResourceScope.of(envIdentifierRef.getAccountIdentifier(),
                                         envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier()))
                                     .build());
    }
    String[] serviceRefSplit = StringUtils.split(serviceRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                     .permission(SERVICE_UPDATE_PERMISSION)
                                     .resourceIdentifier(serviceRef)
                                     .resourceType(SERVICE)
                                     .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                     .build());
    } else {
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);
      permissionCheckDTOList.add(
          PermissionCheckDTO.builder()
              .permission(SERVICE_UPDATE_PERMISSION)
              .resourceIdentifier(serviceIdentifierRef.getIdentifier())
              .resourceType(SERVICE)
              .resourceScope(ResourceScope.of(serviceIdentifierRef.getAccountIdentifier(),
                  serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier()))
              .build());
    }

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionCheckDTOList);
    accessCheckResponse.getAccessControlList().forEach(accessControlDTO -> {
      if (!accessControlDTO.isPermitted()) {
        String errorMessage;
        errorMessage = format("Missing permission %s on %s", accessControlDTO.getPermission(),
            accessControlDTO.getResourceType().toLowerCase());
        if (!StringUtils.isEmpty(accessControlDTO.getResourceIdentifier())) {
          errorMessage = errorMessage.concat(format(" with identifier %s", accessControlDTO.getResourceIdentifier()));
        }
        throw new InvalidRequestException(errorMessage, WingsException.USER);
      }
    });
  }

  private void throwExceptionForNoRequestDTO(EnvironmentRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void throwExceptionForInvalidRequestDTO(ServiceOverrideRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for Service overrides");
    }
    if (isEmpty(dto.getServiceIdentifier())) {
      throw new InvalidRequestException("No service identifier for Service Overrides request");
    }

    if (isBlank(dto.getYaml())) {
      throw new InvalidRequestException("No yaml is provided in Service Overrides request");
    }
  }

  private void validateEnvironmentScope(EnvironmentRequestDTO requestDTO) {
    validateEnvironmentScope(requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier());
  }

  private void validateEnvironmentScope(String orgIdentifier, String projectIdentifier) {
    try {
      if (isNotEmpty(projectIdentifier)) {
        Preconditions.checkArgument(isNotEmpty(orgIdentifier),
            "org identifier must be specified when project identifier is specified. Environments can be created at "
                + "Project/Org/Account scope");
      }
    } catch (Exception ex) {
      log.error("failed to validate environment scope", ex);

      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void validateServiceOverrideScope(ServiceOverrideRequestDTO requestDTO) {
    try {
      if (isNotEmpty(requestDTO.getProjectIdentifier())) {
        Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
            "org identifier must be specified when project identifier is specified. Service Overrides can be created "
                + "at Project/Org/Account scope");
      }
    } catch (Exception ex) {
      log.error("failed to validate service override scope", ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentType);
    return environmentAttributes;
  }

  private Map<String, String> getEnvironmentAttributesMap(EnvironmentType type) {
    return type == null ? new HashMap<>() : getEnvironmentAttributesMap(type.toString());
  }

  private Page<Environment> getRBACFilteredEnvironments(
      String accountId, String orgId, String projectId, Criteria criteria, Pageable pageRequest) {
    if (!environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
            accountId, orgId, projectId, ENVIRONMENT_VIEW_PERMISSION)) {
      Page<Environment> environments = environmentService.list(criteria, Pageable.unpaged());
      if (environments == null || isEmpty(environments)) {
        return Page.empty();
      }
      final List<Environment> environmentList =
          environmentRbacHelper.getPermittedEnvironmentsList(environments.getContent());
      if (isEmpty(environmentList)) {
        return Page.empty();
      }
      populateInFilter(criteria, EnvironmentKeys.identifier,
          environmentList.stream()
              .peek(env -> {
                if (env == null) {
                  log.warn("Invalid environment found during permission filtering. Skipping this environment.");
                }
              })
              .filter(Objects::nonNull)
              .map(Environment::getIdentifier)
              .collect(toList()));
    }
    return environmentService.list(criteria, pageRequest);
  }

  /*
  This method getRBACFilteredEnvironmentsV2 is similar to the getRBACFilteredEnvironments method. But instead
  of getPermittedEnvironmentsList, it uses getPermittedEnvironmentsListV2 method internally. Also, it does not
  have an intial check of env. view access at a particular scope because it can contain environment entities
  from different scopes.
   */
  private Page<Environment> getRBACFilteredEnvironmentsV2(
      Criteria criteria, Pageable pageRequest, String environmentRBACPermission) {
    Page<Environment> environments = environmentService.list(criteria, Pageable.unpaged());
    if (environments == null || isEmpty(environments)) {
      return Page.empty();
    }
    final List<Environment> environmentList =
        environmentRbacHelper.getPermittedEnvironmentsListV2(environments.getContent(), environmentRBACPermission);
    if (isEmpty(environmentList)) {
      return Page.empty();
    }
    populateInFilter(criteria, EnvironmentKeys.identifier,
        environmentList.stream()
            .peek(env -> {
              if (env == null) {
                log.warn("Invalid environment found during permission filtering. Skipping this environment.");
              }
            })
            .filter(Objects::nonNull)
            .map(Environment::getIdentifier)
            .collect(toList()));
    return environmentService.list(criteria, pageRequest);
  }

  private boolean isOverridesV2Enabled(String accountId) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0)
        && !featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_OVERRIDES_DISABLE_ENV_API_UPDATES);
  }

  private String generateServiceOverrideIdentifier(String envRef, String serviceRef) {
    return String.join("_", envRef, serviceRef).replace(".", "_");
  }

  private String generateEnvGlobalOverrideIdentifier(
      String accountId, String orgId, String projectId, String envIdentifier) {
    String envQualifiedRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projectId, envIdentifier);
    return envQualifiedRef.replace(".", "_");
  }

  private Map<String, String> getRefBranchMap(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<EntityWithGitInfo> entityWithGitInfo) {
    Map<String, String> resultMap = new HashMap<>();

    if (isEmpty(entityWithGitInfo)) {
      return resultMap;
    }

    for (EntityWithGitInfo input : entityWithGitInfo) {
      String scopedRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
          accountIdentifier, orgIdentifier, projectIdentifier, input.getRef());
      resultMap.put(scopedRef, input.getBranch());
    }

    return resultMap;
  }

  @GET
  @Hidden
  @Path("/to-unified/{environmentId}")
  @ApiOperation(value = "Convert an Environment to Unified Environment", nickname = "toUnifiedEnvironment")
  @Operation(summary = "Convert an Environment to Unified Environment",
      description = "Convert an Environment to Unified Environment")
  public ResponseDTO<UnifiedEnvConvertorResponse>
  convertToUnifiedEnvironment(@Parameter(description = "Environment Identifier") @PathParam(
                                  "environmentId") @ResourceIdentifier String environmentId,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    Optional<Environment> environmentOpt;

    if (scopeInfo != null) {
      environmentOpt = environmentService.get(scopeInfo, environmentId, false);
    } else {
      environmentOpt = environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentId, false);
    }
    if (environmentOpt.isEmpty()) {
      return ResponseDTO.newResponse(null);
    }

    Environment environment = environmentOpt.get();
    UnifiedEnvironmentConverterResponseDTO envResponseDTO = UnifiedEnvironmentConverterResponseDTO.builder()
                                                                .name(environment.getName())
                                                                .identifier(environment.getIdentifier())
                                                                .description(environment.getDescription())
                                                                .tags(convertToMap(environment.getTags()))
                                                                .type(environment.getType())
                                                                .color(environment.getColor())
                                                                .build();
    return ResponseDTO.newResponse(UnifiedEnvConvertorResponse.builder().responseDTO(envResponseDTO).build());
  }

  @POST
  @Hidden
  @Path("/to-unified/list")
  @ApiOperation(hidden = true, value = "Convert environments to unified environments list",
      nickname = "convertToUnifiedEnvironmentsList")
  @Operation(summary = "Convert environments to unified environments list",
      description = "Convert environments to unified environments list with request body")
  public ResponseDTO<UnifiedEnvListConverterResponse>
  convertToUnifiedEnvironmentsList(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @RequestBody UnifiedEnvListRequestDTO requestDTO, @Context ScopeInfo scopeInfo) {
    List<Environment> environments;
    boolean useScopeInfo = scopeInfo != null;

    throwExceptionForInvalidRequestDTO(requestDTO);
    if (requestDTO.isFetchAllEnvs()) {
      // fetch all environments from all scopes
      environments = useScopeInfo
          ? environmentService.listEnvsFromAllScope(scopeInfo)
          : environmentService.listEnvsFromAllScope(accountId, orgIdentifier, projectIdentifier);
    } else {
      // If environment IDs are provided in the request body, fetch only those specific environments
      boolean isCrossScoped = featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_CROSS_SCOPED_ENV_GROUPS);
      if (isCrossScoped) {
        environments = useScopeInfo ? environmentService.fetchesEnvFromListOfRefs(scopeInfo, requestDTO.getEnvRefs())
                                    : environmentService.fetchesEnvFromListOfRefs(
                                          accountId, orgIdentifier, projectIdentifier, requestDTO.getEnvRefs());
      } else {
        environments = useScopeInfo
            ? environmentService.fetchesEnvFromListOfIdentifiers(scopeInfo, requestDTO.getEnvRefs())
            : environmentService.fetchesEnvFromListOfIdentifiers(
                  accountId, orgIdentifier, projectIdentifier, requestDTO.getEnvRefs());
      }
    }

    List<UnifiedEnvironmentConverterResponseDTO> envResponses = new ArrayList<>();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = useScopeInfo
        ? scopeInfoService.getScopeInfo(
              accountId, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()))
        : null;

    for (Environment environment : environments) {
      UnifiedEnvironmentConverterResponseDTO envResponseDTO =
          UnifiedEnvironmentConverterResponseDTO.builder()
              .envRef(
                  environment.fetchRef(useScopeInfo ? scopeInfoMap.get(environment.getParentUniqueId()).get() : null))
              .name(environment.getName())
              .identifier(environment.getIdentifier())
              .description(environment.getDescription())
              .tags(convertToMap(environment.getTags()))
              .type(environment.getType())
              .color(environment.getColor())
              .repo(environment.getRepo())
              .build();
      envResponses.add(envResponseDTO);
    }
    UnifiedEnvListConverterResponse response =
        UnifiedEnvListConverterResponse.builder().environments(envResponses).build();
    return ResponseDTO.newResponse(response);
  }

  private void throwExceptionForInvalidRequestDTO(UnifiedEnvListRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for env list request");
    }
    if (!dto.isFetchAllEnvs() && isEmpty(dto.getEnvRefs())) {
      throw new InvalidRequestException("No env refs list for env list request");
    }
  }

  /**
   * Manual validation for EnvironmentRequestDTO to support partial success in batch operations.
   * This replaces @Valid annotation validation which would fail the entire request.
   * Implements the same validation logic as @EntityIdentifier and @EntityName annotations.
   */
  private void validateEnvironmentRequestDTO(EnvironmentRequestDTO environmentRequestDTO) {
    List<String> validationErrors = new ArrayList<>();

    // Validate identifier - matches @EntityIdentifier(allowBlank=false, allowScoped=false, maxLength=128)
    String identifier = environmentRequestDTO.getIdentifier();
    if (isBlank(identifier)) {
      validationErrors.add("identifier: cannot be empty");
    } else {
      // Check pattern: must start with letter or underscore, contain only alphanumeric, underscore, $
      if (!EntityIdentifierValidator.IDENTIFIER_PATTERN.matcher(identifier).matches()) {
        validationErrors.add("identifier: can be 128 characters long and can only contain alphanumeric, underscore and "
            + "$ characters, and not start with a number or $");
      } else if (EntityIdentifierValidator.NOT_ALLOWED_WORDS.contains(identifier)) {
        validationErrors.add(format("identifier: %s is a keyword, so cannot be used", identifier));
      } else if (identifier.startsWith("_harness_system")) {
        // Log warning for reserved Harness keyword (doesn't fail validation, matches annotation behavior)
        log.warn("Environment identifier [{}] contains harness reserved keyword", identifier);
      }
    }

    // Validate name if present - matches @EntityName (name is optional, validates only if provided)
    String name = environmentRequestDTO.getName();
    if (isNotEmpty(name) && !EntityNameValidator.isValid(name)) {
      validationErrors.add("name: can only contain alphanumeric characters, hyphens, underscores and spaces");
    }

    // Validate type is not null - matches @NotNull annotation on type field
    if (environmentRequestDTO.getType() == null) {
      validationErrors.add("type: must not be null");
    }

    // If there are validation errors, throw exception with all errors combined
    if (!validationErrors.isEmpty()) {
      throw new InvalidRequestException(String.join("; ", validationErrors));
    }
  }
}
