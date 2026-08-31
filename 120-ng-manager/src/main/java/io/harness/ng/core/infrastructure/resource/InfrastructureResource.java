/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.NGCommonEntityConstants.INFRA_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.resources.EnvironmentResourceV2.ENVIRONMENT_PARAM_MESSAGE;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.environment.helper.EnvironmentMapper;
import io.harness.cdng.infra.InfrastructureOutcomeProvider;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.MoveConfigOperationType;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitx.GitXUtils;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedGitEntityInfoResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;
import io.harness.infrastructure.unified.UnifiedInfrasConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfrasConvertorResponse;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.beans.InfrastructureYamlMetadataApiInputV2;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.ForceImportInfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.ForceImportInfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.ForceImportInfrastructureYamlOperationDTO;
import io.harness.ng.core.infrastructure.dto.InfraMoveConfigOperationDTO;
import io.harness.ng.core.infrastructure.dto.InfraMoveConfigRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfraMoveConfigResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureGitMetadataUpdateParams;
import io.harness.ng.core.infrastructure.dto.InfrastructureGitUpdateResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureImportOperationDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureImportResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureInputsMergedResponseDto;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadata;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadataApiInput;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadataDTO;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.dto.RemoteInfrastructuresDTO;
import io.harness.ng.core.infrastructure.dto.RemoteInfrastructuresResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.entity.InfrastructureRemoteRepoInfo;
import io.harness.ng.core.infrastructure.entity.InfrastructureRemoteRepoListResponse;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.ng.core.opa.gitx.InfrastructureOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.utils.NgManagerErrorResponseUtils;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.unified.error.NgManagerErrorResponseDTO;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;
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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.validation.Valid;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/infrastructures")
@Path("/infrastructures")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Infrastructures", description = "This contains APIs related to Infrastructure Definitions")
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
@ScopeInfoResolutionApi
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class InfrastructureResource {
  public static final String INFRASTRUCTURE_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "List of Infrastructure Identifiers for the entities";
  @Inject private final InfrastructureEntityService infrastructureEntityService;
  @Inject private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private final EnvironmentValidationHelper environmentValidationHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Inject private final SshEntityHelper sshEntityHelper;
  private InfrastructureYamlSchemaHelper infrastructureYamlSchemaHelper;
  @Inject private ScopeAccessHelper scopeAccessHelper;
  @Inject private InfrastructureHelper infrastructureHelper;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private InfrastructureOutcomeProvider infrastructureOutcomeProvider;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private InfrastructureOpaStatusHandler infrastructureOpaStatusHandler;
  @Inject private CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  @Inject private io.harness.ng.core.infrastructure.services.DiscoveryOrchestrator discoveryOrchestrator;

  public static final String INFRA_PARAM_MESSAGE = "Infrastructure Identifier for the entity";

  @GET
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Gets an Infrastructure by identifier", nickname = "getInfrastructure")
  @Operation(operationId = "getInfrastructure", summary = "Gets an Infrastructure by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "The saved Infrastructure") })
  @Timed
  @ResponseMetered
  public ResponseDTO<InfrastructureResponse>
  get(@Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = "Specify whether Infrastructure is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");

    Optional<InfrastructureEntity> infraEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, scopeInfo, envIdentifier,
            infraIdentifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);

    if (infraEntity.isPresent()) {
      InfrastructureEntity infra = infraEntity.get();

      if (isEmpty(infra.getYaml())) {
        InfrastructureConfig infrastructureConfig = InfrastructureEntityConfigMapper.toInfrastructureConfig(infra);
        infra.setYaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig));
      }

      if (GitXUtils.isRemoteEntity(infra)) {
        try {
          infrastructureYamlSchemaHelper.validateSchema(accountId, infra.getYaml());
        } catch (InvalidYamlException ex) {
          return ResponseDTO.newResponse(
              InfrastructureResponse.builder()
                  .infrastructure(InfrastructureMapper.writeDTO(infra))
                  .createdAt(infra.getCreatedAt())
                  .lastModifiedAt(infra.getLastModifiedAt())
                  .entityValidityDetails(
                      EntityValidityDetails.builder().valid(false).invalidYaml(infra.getYaml()).build())
                  .build());
        }
      }
    } else {
      throw new NotFoundException(
          format("Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
              infraIdentifier, projectIdentifier, orgIdentifier, envIdentifier));
    }

    InfrastructureResponse response = infraEntity.map(InfrastructureMapper::toResponseWrapper).orElse(null);
    response.setEntityValidityDetails(EntityValidityDetails.builder().valid(true).build());
    cdOpaOnSaveStatusApiHelper
        .resolveGetOpaOnSaveStatus(infraEntity.get(), accountId, scopeInfo, infrastructureOpaStatusHandler)
        .ifPresent(response::setOpaOnSaveStatus);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @ApiOperation(value = "Create an Infrastructure in an Environment", nickname = "createInfrastructure")
  @Operation(operationId = "createInfrastructure", summary = "Create an Infrastructure in an Environment",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the created Infrastructure") })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<InfrastructureResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be created",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Infrastructure create payload",
                         value = DocumentationConstants.infrastructureRequestDTO,
                         description = "Sample Infrastructure payload"))
          }) @Valid InfrastructureRequestDTO infrastructureRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    validateProjectLevelInfraScope(infrastructureEntity);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());
    // access for updating Environment
    infrastructureHelper.checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    InfrastructureGovernanceDataResponse createdInfrastructureMapper =
        infrastructureEntityService.create(infrastructureEntity);
    InfrastructureResponse infraResponse = InfrastructureMapper.toResponseWrapper(
        createdInfrastructureMapper.getInfrastructureEntity(), createdInfrastructureMapper.getGovernanceMetadata());
    if (createdInfrastructureMapper.getDiscoveryStatus() != null) {
      infraResponse.getInfrastructure().setDiscoveryStatus(createdInfrastructureMapper.getDiscoveryStatus());
    }
    return ResponseDTO.newResponse(infraResponse);
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Infrastructures", nickname = "createInfrastructures")
  @Operation(operationId = "createInfrastructures", summary = "Create Infrastructures",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the created Infrastructures") },
      hidden = true)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<InfrastructureResponse>>
  createInfrastructures(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Infrastructures to be created")
      @Valid List<InfrastructureRequestDTO> infrastructureRequestDTOS) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTOS);
    infrastructureRequestDTOS.forEach(dto -> infrastructureYamlSchemaHelper.validateSchema(accountId, dto.getYaml()));
    List<InfrastructureEntity> entities = infrastructureRequestDTOS.stream()
                                              .map(dto -> InfrastructureMapper.toInfrastructureEntity(accountId, dto))
                                              .collect(Collectors.toList());
    entities.forEach(entity -> {
      validateProjectLevelInfraScope(entity);
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
          entity.getOrgIdentifier(), entity.getProjectIdentifier(), accountId);
      environmentValidationHelper.checkThatEnvExists(
          accountId, entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getEnvIdentifier());
    });

    checkForAccessBatch(accountId, entities, ENVIRONMENT_UPDATE_PERMISSION);
    Page<InfrastructureEntity> createdInfrastructures = infrastructureEntityService.bulkCreate(accountId, entities);
    return ResponseDTO.newResponse(
        getNGPageResponse(createdInfrastructures.map(InfrastructureMapper::toResponseWrapper)));
  }

  @DELETE
  @Path("{infraIdentifier}")
  @ApiOperation(value = "Delete an infrastructure by identifier", nickname = "deleteInfrastructure")
  @Operation(operationId = "deleteInfrastructure", summary = "Delete an Infrastructure by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Infrastructure is deleted")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> delete(@Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
                                         "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE)
      @DefaultValue("false") boolean forceDelete, @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");

    return ResponseDTO.newResponse(infrastructureEntityService.delete(
        accountId, orgIdentifier, projectIdentifier, scopeInfo, envIdentifier, infraIdentifier, forceDelete));
  }

  @PUT
  @ApiOperation(value = "Update an Infrastructure by identifier", nickname = "updateInfrastructure")
  @Operation(operationId = "updateInfrastructure", summary = "Update an Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Infrastructure") })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<InfrastructureResponse>
  update(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Update", summary = "Sample Infrastructure update payload",
                         value = DocumentationConstants.infrastructureRequestDTO,
                         description = "Sample Infrastructure payload"))
          }) @Valid InfrastructureRequestDTO infrastructureRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated",
          hidden = true) @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateProjectLevelInfraScope(infrastructureEntity);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());

    infrastructureHelper.checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "update");
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    InfrastructureGovernanceDataResponse updatedInfraMapper = infrastructureEntityService.update(infrastructureEntity);
    InfrastructureResponse infraResponse = InfrastructureMapper.toResponseWrapper(
        updatedInfraMapper.getInfrastructureEntity(), updatedInfraMapper.getGovernanceMetadata());
    if (updatedInfraMapper.getDiscoveryStatus() != null) {
      infraResponse.getInfrastructure().setDiscoveryStatus(updatedInfraMapper.getDiscoveryStatus());
    }
    return ResponseDTO.newResponse(infraResponse);
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an Infrastructure by identifier", nickname = "upsertInfrastructure")
  @Operation(operationId = "upsertInfrastructure", summary = "Upsert an Infrastructure by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the upserted Infrastructure") },
      hidden = true)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<InfrastructureResponse>
  upsert(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Infrastructure to be upsert", content = {
        @Content(
            examples = @ExampleObject(name = "Upsert", summary = "Sample Infrastructure upsert payload",
                value = DocumentationConstants.infrastructureRequestDTO, description = "Sample Infrastructure payload"))
      }) @Valid InfrastructureRequestDTO infrastructureRequestDTO) {
    throwExceptionForNoRequestDTO(infrastructureRequestDTO);
    infrastructureYamlSchemaHelper.validateSchema(accountId, infrastructureRequestDTO.getYaml());
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountId, infrastructureRequestDTO);
    validateProjectLevelInfraScope(infrastructureEntity);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier());

    infrastructureHelper.checkForAccessOrThrow(accountId, infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getEnvIdentifier(),
        ENVIRONMENT_UPDATE_PERMISSION, "upsert");
    validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    InfrastructureGovernanceDataResponse upsertedInfraMapper =
        infrastructureEntityService.upsert(infrastructureEntity, UpsertOptions.DEFAULT);
    return ResponseDTO.newResponse(InfrastructureMapper.toResponseWrapper(
        upsertedInfraMapper.getInfrastructureEntity(), upsertedInfraMapper.getGovernanceMetadata()));
  }

  @GET
  @ApiOperation(value = "Gets Infrastructure list ", nickname = "getInfrastructureList")
  @Operation(operationId = "getInfrastructureList", summary = "Gets Infrastructure list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructure for an Environment")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<InfrastructureResponse>>
  listInfrastructures(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String envIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of InfrastructureIds") @QueryParam("infraIdentifiers")
      List<String> infraIdentifiers, @QueryParam("deploymentType") ServiceDefinitionType deploymentType,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(description = NGResourceFilterConstants.SORT_DESCRIPTION) @QueryParam("sort") List<String> sort,
      @Parameter(description = "list of service refs required to fetch infrastructures scoped to these service refs")
      @QueryParam("serviceRefs") List<String> serviceRefs,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam("repoName")
      String repoName, @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "list");

    Criteria criteria = InfrastructureFilterHelper.createListCriteria(
        scopeInfo, envIdentifier, searchTerm, infraIdentifiers, deploymentType, repoName, false);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    boolean isCustomDeployment =
        ServiceDefinitionType.CUSTOM_DEPLOYMENT == deploymentType && !isEmpty(deploymentTemplateIdentifier);
    boolean useMetadata = isCustomDeployment
        && ngFeatureFlagHelperService.isEnabled(
            accountId, FeatureName.CDS_OPTIMIZE_CUSTOM_DEPLOYMENT_LIST_WITH_BRANCH_METADATA);
    Page<InfrastructureEntity> infraEntities;
    if (useMetadata) {
      List<String> versionLabels = customDeploymentYamlHelper.resolveVersionLabels(
          accountId, orgIdentifier, projectIdentifier, deploymentTemplateIdentifier, versionLabel);
      infraEntities = infrastructureEntityService.listWithBranchMetadata(
          criteria, pageRequest, accountId, scopeInfo.getUniqueId(), deploymentTemplateIdentifier, versionLabels);
    } else {
      infraEntities = infrastructureEntityService.list(criteria, pageRequest, isCustomDeployment);
    }
    infraEntities = infrastructureEntityService.getScopedInfrastructures(infraEntities, serviceRefs);
    if (isCustomDeployment) {
      if (useMetadata) {
        List<InfrastructureEntity> remoteEntities = infraEntities.getContent()
                                                        .stream()
                                                        .filter(e -> StoreType.REMOTE.equals(e.getStoreType()))
                                                        .collect(Collectors.toList());
        Page<InfrastructureEntity> inlineOnly =
            new PageImpl<>(infraEntities.getContent()
                               .stream()
                               .filter(e -> !StoreType.REMOTE.equals(e.getStoreType()))
                               .collect(Collectors.toList()));
        Page<InfrastructureEntity> filteredInline = customDeploymentYamlHelper.getFilteredInfraEntities(page, size,
            sort, deploymentTemplateIdentifier, versionLabel, inlineOnly, accountId, orgIdentifier, projectIdentifier);
        List<InfrastructureEntity> merged = new ArrayList<>(remoteEntities);
        merged.addAll(filteredInline.getContent());
        infraEntities = new PageImpl<>(merged, pageRequest, merged.size());
      } else {
        infraEntities = customDeploymentYamlHelper.getFilteredInfraEntities(page, size, sort,
            deploymentTemplateIdentifier, versionLabel, infraEntities, accountId, orgIdentifier, projectIdentifier);
      }
    }
    return ResponseDTO.newResponse(getNGPageResponse(infraEntities.map(InfrastructureMapper::toResponseWrapper)));
  }

  @GET
  @Path("/dummy-infraConfig-api")
  @ApiOperation(value = "This is dummy api to expose infraConfig", nickname = "dummyInfraConfigApi")
  @Hidden
  @ScopeInfoResolutionExemptedApi
  // do not delete this.
  public ResponseDTO<InfrastructureConfig> getInfraConfig() {
    return ResponseDTO.newResponse(InfrastructureConfig.builder().build());
  }

  @GET
  @Path("/runtimeInputs")
  @ApiOperation(value = "This api returns Infrastructure Definition inputs YAML", nickname = "getInfrastructureInputs")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<NGEntityTemplateResponseDTO> getInfrastructureInputs(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = "List of Infrastructure Identifiers") @QueryParam(
          NGCommonEntityConstants.INFRA_IDENTIFIERS) List<String> infraIdentifiers,
      @Parameter(description = "Specify whether Deploy to all infrastructures in the environment") @QueryParam(
          NGCommonEntityConstants.DEPLOY_TO_ALL) @DefaultValue("false") boolean deployToAll,
      @Context ScopeInfo scopeInfo) {
    String infrastructureInputsYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(accountId,
        orgIdentifier, projectIdentifier, scopeInfo, environmentIdentifier, null, infraIdentifiers, deployToAll,
        NoInputMergeInputAction.RETURN_EMPTY);
    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(infrastructureInputsYaml).build());
  }

  private void throwExceptionForNoRequestDTO(InfrastructureRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier,envIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(List<InfrastructureRequestDTO> dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier, envIdentifier, tags, description");
    }
  }

  private void checkForAccessBatch(
      String accountId, List<InfrastructureEntity> infrastructureRequestDTOList, String permission) {
    Map<String, Boolean> accessMap = new HashMap<>();
    for (InfrastructureEntity entity : infrastructureRequestDTOList) {
      StringJoiner joiner = new StringJoiner("|");
      joiner.add(entity.getOrgIdentifier()).add(entity.getProjectIdentifier()).add(entity.getEnvIdentifier());
      String key = joiner.toString();

      accessMap.computeIfAbsent(key,
          k
          -> accessControlClient.hasAccess(
              ResourceScope.of(accountId, entity.getOrgIdentifier(), entity.getProjectIdentifier()),
              Resource.of(NGResourceType.ENVIRONMENT, entity.getEnvIdentifier()), permission));

      if (Boolean.FALSE.equals(accessMap.get(key))) {
        throw new NGAccessDeniedException(
            format("Missing permissions %s on %s", permission, key), WingsException.USER, null);
      }
    }
  }

  @POST
  @Path("/infrastructureYamlMetadata")
  @ApiOperation(value = "This api returns infrastructure YAML and runtime input YAML",
      nickname = "getInfrastructureYamlAndRuntimeInputs")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<InfrastructureYamlMetadataDTO>
  getInfrastructureYamlAndRuntimeInputs(@Parameter(description = INFRASTRUCTURE_YAML_METADATA_INPUT_PARAM_MESSAGE)
                                        @Valid @NotNull InfrastructureYamlMetadataApiInput infrastructureYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Context ScopeInfo scopeInfo) {
    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
    List<InfrastructureYamlMetadata> infrastructureYamlMetadataList =
        infrastructureEntityService.createInfrastructureYamlMetadata(accountId, orgIdentifier, projectIdentifier,
            scopeInfo, environmentIdentifier, infrastructureYamlMetadata.getInfrastructureIdentifiers());
    return ResponseDTO.newResponse(
        InfrastructureYamlMetadataDTO.builder().infrastructureYamlMetadataList(infrastructureYamlMetadataList).build());
  }

  @POST
  @Path("v2/infrastructure-yaml-metadata")
  @ApiOperation(value = "This api returns infrastructure YAML and runtime input YAML",
      nickname = "getInfrastructureYamlAndRuntimeInputsV2")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<InfrastructureYamlMetadataDTO>
  getInfrastructureYamlAndRuntimeInputsV2(
      @Parameter(description = INFRASTRUCTURE_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
      @NotNull InfrastructureYamlMetadataApiInputV2 infrastructureYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info for the Base entity")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache") @HeaderParam("Load-From-Cache")
      @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo) {
    String environmentBranch = infrastructureYamlMetadata.getEnvironmentBranch();
    List<InfrastructureYamlMetadata> infrastructureYamlMetadataList =
        infrastructureEntityService.createInfrastructureYamlMetadata(accountId, orgIdentifier, projectIdentifier,
            scopeInfo, environmentIdentifier, environmentBranch,
            infrastructureYamlMetadata.getInfrastructureIdentifiers(),
            GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache));
    return ResponseDTO.newResponse(
        InfrastructureYamlMetadataDTO.builder().infrastructureYamlMetadataList(infrastructureYamlMetadataList).build());
  }

  @POST
  @Path("/mergeInfrastructureInputs/{infraIdentifier}")
  @ApiOperation(value = "This api merges old and new infrastructure inputs YAML", nickname = "mergeInfraInputs")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<InfrastructureInputsMergedResponseDto> mergeInfrastructureInputs(
      @Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @NotNull
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      String oldInfrastructureInputsYaml, @Context ScopeInfo scopeInfo) {
    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
    return ResponseDTO.newResponse(infrastructureEntityService.mergeInfraStructureInputs(accountId, orgIdentifier,
        projectIdentifier, scopeInfo, envIdentifier, infraIdentifier, oldInfrastructureInputsYaml));
  }

  @GET
  @Path("/list-access")
  @ApiOperation(value = "Gets Infrastructure access list ", nickname = "getInfrastructureAccessList")
  @Operation(operationId = "getInfrastructureAccessList", summary = "Gets Infrastructure access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructure accessible at the current scope")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<List<InfrastructureResponse>>
  listAccessInfrastructures(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
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
      @Parameter(description = "List of InfrastructureIds") @QueryParam("infraIdentifiers")
      List<String> infraIdentifiers, @QueryParam("deploymentType") ServiceDefinitionType deploymentType,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(description = NGResourceFilterConstants.SORT_DESCRIPTION) @QueryParam("sort") List<String> sort,
      @Parameter(description = "list of service refs required to fetch infrastructures scoped to these service refs")
      @QueryParam("serviceRefs") List<String> serviceRefs,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam("repoName")
      String repoName, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        "Unable to list infrastructures because the user is not having view access for the corresponding scope");
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    Criteria criteria = InfrastructureFilterHelper.createListCriteria(
        scopeInfo, null, searchTerm, infraIdentifiers, deploymentType, repoName, true);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    boolean isCustomDeployment =
        ServiceDefinitionType.CUSTOM_DEPLOYMENT == deploymentType && !isEmpty(deploymentTemplateIdentifier);
    Page<InfrastructureEntity> infraEntities =
        infrastructureEntityService.list(criteria, pageRequest, isCustomDeployment);
    infraEntities = infrastructureEntityService.getScopedInfrastructures(infraEntities, serviceRefs);
    if (isCustomDeployment) {
      infraEntities = customDeploymentYamlHelper.getFilteredInfraEntities(page, size, sort,
          deploymentTemplateIdentifier, versionLabel, infraEntities, accountId, orgIdentifier, projectIdentifier);
    }

    List<InfrastructureEntity> finalInfrastructureList = filterInfraBasedOnAccess(infraEntities);
    return ResponseDTO.newResponse(
        finalInfrastructureList.stream().map(InfrastructureMapper::toResponseWrapper).collect(Collectors.toList()));
  }

  /*
  This API is similar to /list-access API. But is has a new query field editOnlyRBACPermissions also added to it.
  Also, internally it uses filterInfraBasedOnRBACPermissions method instead of filterInfraBasedOnAccess.
  It also contains the envIdentifier query parameter
   */
  @GET
  @Path("/v2/list")
  @ApiOperation(value = "Gets Infrastructure list ", nickname = "getInfrastructureListV2")
  @Operation(operationId = "getInfrastructureList", summary = "Gets Infrastructure list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Infrastructure at the current scope")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<List<InfrastructureResponse>>
  listInfrastructuresV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
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
      @Parameter(description = "List of InfrastructureIds") @QueryParam("infraIdentifiers")
      List<String> infraIdentifiers, @QueryParam("deploymentType") ServiceDefinitionType deploymentType,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(description = NGResourceFilterConstants.SORT_DESCRIPTION) @QueryParam("sort") List<String> sort,
      @Parameter(description = "list of service refs required to fetch infrastructures scoped to these service refs")
      @QueryParam("serviceRefs") List<String> serviceRefs,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String envIdentifier,
      @Parameter(
          description = "Specify true if only infrastructures with environment edit permission are required. Default "
              + "value false will mean infrastructures with environment view permission are required.",
          hidden = true) @QueryParam("editOnlyRBACPermission") @DefaultValue("false") boolean editOnlyRBACPermissions,
      @Context ScopeInfo scopeInfo) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    if (isNotEmpty(envIdentifier)) {
      environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    }
    Criteria criteria = InfrastructureFilterHelper.createListCriteria(
        scopeInfo, envIdentifier, searchTerm, infraIdentifiers, deploymentType, repoName, true);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    boolean isCustomDeployment =
        ServiceDefinitionType.CUSTOM_DEPLOYMENT == deploymentType && !isEmpty(deploymentTemplateIdentifier);
    Page<InfrastructureEntity> infraEntities;
    infraEntities = infrastructureEntityService.list(criteria, pageRequest, isCustomDeployment);
    infraEntities = infrastructureEntityService.getScopedInfrastructures(infraEntities, serviceRefs);
    if (isCustomDeployment) {
      infraEntities = customDeploymentYamlHelper.getFilteredInfraEntities(page, size, sort,
          deploymentTemplateIdentifier, versionLabel, infraEntities, accountId, orgIdentifier, projectIdentifier);
    }

    String environmentRBACPermission = ENVIRONMENT_VIEW_PERMISSION;
    if (editOnlyRBACPermissions) {
      environmentRBACPermission = ENVIRONMENT_UPDATE_PERMISSION;
    }

    List<InfrastructureEntity> finalInfrastructureList =
        filterInfraBasedOnRBACPermissions(infraEntities, environmentRBACPermission);
    return ResponseDTO.newResponse(
        finalInfrastructureList.stream().map(InfrastructureMapper::toResponseWrapper).collect(Collectors.toList()));
  }

  private void validateDeploymentTypeSpecificInfrastructureYaml(InfrastructureEntity infrastructureEntity) {
    ServiceDefinitionType deploymentType = infrastructureEntity.getDeploymentType();
    if (deploymentType == ServiceDefinitionType.CUSTOM_DEPLOYMENT
        && infrastructureEntity.getType() == InfrastructureType.CUSTOM_DEPLOYMENT
        && (customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity))) {
      throw new InvalidRequestException(
          "Infrastructure yaml is not valid, template variables and infra variables doesn't match");
    }

    if (deploymentType == ServiceDefinitionType.SSH || deploymentType == ServiceDefinitionType.WINRM) {
      sshEntityHelper.validateInfrastructureYaml(infrastructureEntity);
    }
  }

  private void validateProjectLevelInfraScope(String orgIdentifier, String projectIdentifier) {
    try {
      if (isNotEmpty(projectIdentifier)) {
        Preconditions.checkArgument(isNotEmpty(orgIdentifier),
            "org identifier must be specified when project identifier is specified. Infra can be created at "
                + "Project/Org/Account scope");
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void validateProjectLevelInfraScope(InfrastructureEntity entity) {
    validateProjectLevelInfraScope(entity.getOrgIdentifier(), entity.getProjectIdentifier());
  }

  private List<InfrastructureEntity> filterInfraBasedOnAccess(Page<InfrastructureEntity> infraEntities) {
    List<PermissionCheckDTO> permissionCheckDTOS =
        new ArrayList<>(getPermissionDTOForEnvironments(infraEntities, ENVIRONMENT_VIEW_PERMISSION));
    return getFilteredInfrastructureEntities(infraEntities, permissionCheckDTOS);
  }

  private List<InfrastructureEntity> filterInfraBasedOnRBACPermissions(
      Page<InfrastructureEntity> infraEntities, String environmentRBACPermission) {
    List<PermissionCheckDTO> permissionCheckDTOS =
        new ArrayList<>(getPermissionDTOForEnvironments(infraEntities, environmentRBACPermission));
    return getFilteredInfrastructureEntities(infraEntities, permissionCheckDTOS);
  }

  @NotNull
  private List<InfrastructureEntity> getFilteredInfrastructureEntities(
      Page<InfrastructureEntity> infraEntities, List<PermissionCheckDTO> permissionCheckDTOS) {
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();

    Map<String, Boolean> permittedEnvMap = new HashMap<>();

    accessControlList.forEach(accessControl
        -> permittedEnvMap.put(IdentifierRefHelper
                                   .getIdentifierRefFromEntityIdentifiers(accessControl.getResourceIdentifier(),
                                       accessControl.getResourceScope().getAccountIdentifier(),
                                       accessControl.getResourceScope().getOrgIdentifier(),
                                       accessControl.getResourceScope().getProjectIdentifier())
                                   .buildScopedIdentifier(),
            accessControl.isPermitted()));

    return infraEntities.stream()
        .filter(infra
            -> permittedEnvMap.get(
                IdentifierRefHelper
                    .getIdentifierRefFromEntityIdentifiers(infra.getEnvIdentifier(), infra.getAccountIdentifier(),
                        infra.getOrgIdentifier(), infra.getProjectIdentifier())
                    .buildScopedIdentifier()))
        .collect(Collectors.toList());
  }

  private Set<PermissionCheckDTO> getPermissionDTOForEnvironments(
      Page<InfrastructureEntity> infraEntities, String environmentRBACPermission) {
    return infraEntities.stream()
        .map(infra
            -> CDNGRbacUtility.environmentResponseToPermissionCheckDTO(infra.getEnvIdentifier(), infra.getAccountId(),
                infra.getOrgIdentifier(), infra.getProjectIdentifier(), environmentRBACPermission))
        .collect(Collectors.toSet());
  }

  @GET
  @Path("/remote-infrastructures-metadata")
  @ApiOperation(value = "List remote infrastructures grouped by repository for a given accountId",
      nickname = "getRemoteInfrastructuresMetadata")
  @Operation(operationId = "getRemoteInfrastructuresMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote infrastructures in an account along with "
          + "infrastructure metadata. Optionally filter by repoName.",
      summary = "List remote infrastructures grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the infrastructure file paths in each repo")
      })
  @InternalApi
  @Hidden
  public ResponseDTO<RemoteInfrastructuresResponseDTO>
  getRemoteInfrastructuresMetadata(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote infrastructures only for the given repoName.")
      @QueryParam(NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_INFRA_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      InfrastructureRemoteRepoListResponse serviceResponse =
          infrastructureEntityService.getRemoteRepoListForAGivenScope(
              accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<InfrastructureRemoteRepoInfo> serviceRepos =
          serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();
      List<RemoteInfrastructuresDTO> resourceRepos =
          serviceRepos.stream()
              .map(info
                  -> RemoteInfrastructuresDTO.builder()
                         .repoName(info.getRepoName())
                         .repoURL(info.getRepoURL())
                         .count(info.getCount())
                         .filePathsByOwningScope(info.getFilePathsByOwningScope())
                         .connectorRefs(info.getConnectorRefs())
                         .build())
              .collect(Collectors.toList());
      long totalInfrastructures = serviceRepos.stream().mapToLong(InfrastructureRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_INFRA_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalInfrastructures={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceResponse.getTotalRepos(),
          resourceRepos.size(), totalInfrastructures, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemoteInfrastructuresResponseDTO.builder()
                                         .totalInfrastructures(totalInfrastructures)
                                         .totalRepos(serviceResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error("[REMOTE_INFRA_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  @POST
  @Path("/move-config/{infraIdentifier}")
  @ApiOperation(value = "Move infra YAML from inline to remote", nickname = "moveInfraConfigs")
  @Operation(operationId = "moveInfraConfigs", summary = "Move infra YAML from inline to remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Move infra YAML from inline to remote")
      })
  public ResponseDTO<InfraMoveConfigResponse>
  moveConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentIdentifier,
      @Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          INFRA_IDENTIFIER) @ResourceIdentifier String infraIdentifier,
      @BeanParam InfraMoveConfigRequestDTO infraMoveConfigRequest, @Context ScopeInfo scopeInfo) {
    // check for environment update permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_UPDATE_PERMISSION);

    InfraMoveConfigOperationDTO moveConfigOperationDTO =
        InfraMoveConfigOperationDTO.builder()
            .repoName(infraMoveConfigRequest.getRepoName())
            .branch(infraMoveConfigRequest.getBranch())
            .moveConfigOperationType(
                MoveConfigOperationType.getMoveConfigType(infraMoveConfigRequest.getMoveConfigOperationType()))
            .connectorRef(infraMoveConfigRequest.getConnectorRef())
            .baseBranch(infraMoveConfigRequest.getBaseBranch())
            .commitMessage(infraMoveConfigRequest.getCommitMsg())
            .isNewBranch(infraMoveConfigRequest.getIsNewBranch())
            .filePath(infraMoveConfigRequest.getFilePath())
            .isHarnessCodeRepo(infraMoveConfigRequest.getIsHarnessCodeRepo())
            .build();

    InfraMoveConfigResponse infraMoveResponse = infrastructureEntityService.moveInfrastructure(accountIdentifier,
        orgIdentifier, projectIdentifier, scopeInfo, environmentIdentifier, infraIdentifier, moveConfigOperationDTO);
    return ResponseDTO.newResponse(infraMoveResponse);
  }

  @PUT
  @Path("/{infraIdentifier}/update-git-metadata")
  @ApiOperation(
      value = "Update git-metadata in remote Infrastructure Entity", nickname = "updateInfrastructureGitDetails")
  @Operation(operationId = "updateInfrastructureGitDetails",
      description = "Update git-metadata in remote infrastructure and returns the identifier of updated infrastructure",
      summary = "Update git-metadata in remote infrastructure Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns identifier of updated infrastructure")
      })
  public ResponseDTO<InfrastructureGitUpdateResponseDTO>
  updateGitMetadataForInfrastructure(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentIdentifier,
      @Parameter(description = INFRA_PARAM_MESSAGE) @PathParam(
          INFRA_IDENTIFIER) @ResourceIdentifier String infraIdentifier,
      @BeanParam GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_UPDATE_PERMISSION);

    String infrastructureAfterGitMetadataUpdate = infrastructureEntityService.updateGitMetadata(accountIdentifier,
        orgIdentifier, projectIdentifier, scopeInfo, environmentIdentifier, infraIdentifier,
        InfrastructureGitMetadataUpdateParams.builder()
            .connectorRef(gitMetadataUpdateRequestInfo.getConnectorRef())
            .filePath(gitMetadataUpdateRequestInfo.getFilePath())
            .repoName(gitMetadataUpdateRequestInfo.getRepoName())
            .build());
    return ResponseDTO.newResponse(
        InfrastructureGitUpdateResponseDTO.builder().identifier(infrastructureAfterGitMetadataUpdate).build());
  }

  @POST
  @Path("/import")
  @ApiOperation(value = "Get Infrastructure YAML from Git Repository", nickname = "importInfrastructure")
  @Operation(operationId = "importInfrastructure", summary = "Import and Create Infrastructure from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Import and Create Infrastructure from Git Repository and saves a record for it in Harness")
      })
  public ResponseDTO<InfrastructureImportResponseDTO>
  importInfrastructureFromGit(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = INFRA_PARAM_MESSAGE) @QueryParam(
          INFRA_IDENTIFIER) @ResourceIdentifier String infraIdentifier,
      @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO, @Context ScopeInfo scopeInfo) {
    validateProjectLevelInfraScope(orgIdentifier, projectIdentifier);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        orgIdentifier, projectIdentifier, accountIdentifier);
    environmentValidationHelper.checkThatEnvExists(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);
    // access for updating Environment
    infrastructureHelper.checkForAccessOrThrow(accountIdentifier, orgIdentifier, projectIdentifier,
        environmentIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "create");

    InfrastructureGovernanceDataResponse infrastructureGovernanceDataResponse =
        infrastructureEntityService.importInfrastructureFromRemote(accountIdentifier, orgIdentifier, projectIdentifier,
            null, environmentIdentifier, infraIdentifier,
            InfrastructureImportOperationDTO.builder()
                .connectorRef(gitImportInfoDTO.getConnectorRef())
                .repoName(gitImportInfoDTO.getRepoName())
                .branch(gitImportInfoDTO.getBranch())
                .filePath(gitImportInfoDTO.getFilePath())
                .isForceImport(gitImportInfoDTO.getIsForceImport())
                .isHarnessCodeRepo(gitImportInfoDTO.getIsHarnessCodeRepo())
                .build());

    return ResponseDTO.newResponse(
        InfrastructureImportResponseDTO.builder()
            .identifier(infrastructureGovernanceDataResponse.getInfrastructureEntity().getIdentifier())
            .governanceMetadata(infrastructureGovernanceDataResponse.getGovernanceMetadata())
            .build());
  }

  @POST
  @Path("/validate-yaml")
  @Hidden
  @ApiOperation(
      value = "This api return the validation result of Infrastructure yaml", nickname = "validateInfrastructureYaml")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<YamlValidationListAPIResponse>
  validateInfrastructureYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    YamlValidationRequestDTO yamlValidationRequestDTO = getYamlValidationRequestDTO(yamlValidationRequestBody);
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        infrastructureEntityService.validateInfrastructureYaml(accountIdentifier, yamlValidationRequestDTO);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
  }

  @POST
  @Path("/force-import")
  @Hidden
  @ApiOperation(value = "Force Import an Infrastructure", nickname = "forceImportInfrastructure")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ForceImportInfrastructureResponse> forceImportInfrastructure(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid ForceImportInfrastructureRequestDTO requestDTO) {
    ForceImportInfrastructureYamlOperationDTO operationDTO =
        ForceImportInfrastructureYamlOperationDTO.builder()
            .branch(requestDTO.getBranch())
            .repoName(requestDTO.getRepoName())
            .connectorRef(requestDTO.getConnectorRef())
            .filePath(requestDTO.getFilePath())
            .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
            .identifier(requestDTO.getIdentifier())
            .orgIdentifier(requestDTO.getOrgIdentifier())
            .projectIdentifier(requestDTO.getProjectIdentifier())
            .environmentIdentifier(requestDTO.getEnvironmentIdentifier())
            .build();
    infrastructureHelper.checkForAccessOrThrow(accountIdentifier, operationDTO.getOrgIdentifier(),
        operationDTO.getProjectIdentifier(), operationDTO.getEnvironmentIdentifier(), ENVIRONMENT_UPDATE_PERMISSION,
        "create");
    environmentValidationHelper.checkThatEnvExists(accountIdentifier, operationDTO.getOrgIdentifier(),
        operationDTO.getProjectIdentifier(), operationDTO.getEnvironmentIdentifier());
    ForceImportInfrastructureResponse response =
        infrastructureEntityService.forceImportInfrastructure(accountIdentifier, operationDTO);
    return ResponseDTO.newResponse(response);
  }

  @Hidden
  @POST
  @Path("/to-unified/{infraIdentifier}")
  @ApiOperation(value = "Convert an Ng Service to Unified Infrastructure", nickname = "toUnifiedInfrastructure")
  public ResponseDTO<UnifiedInfraConvertorResponse> convertToUnifiedStageService(
      @Parameter(description = "infraIdentifier") @PathParam(
          "infraIdentifier") @ResourceIdentifier String infraIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @Parameter(description = "Git entity find info") @BeanParam GitEntityFindInfoDTO gitEntityFindInfoDTO,
      @RequestBody(required = true) @Valid UnifiedInfraConverterRequestDTO requestDTO, @Context ScopeInfo scopeInfo)
      throws IOException {
    try {
      infrastructureHelper.checkForAccessOrThrow(
          accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
      Optional<InfrastructureEntity> infrastructureOp = infrastructureEntityService.get(
          accountId, orgIdentifier, projectIdentifier, scopeInfo, envIdentifier, infraIdentifier);
      if (infrastructureOp.isEmpty()) {
        return ResponseDTO.newResponse(null);
      }

      Environment environment =
          environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);
      UnifiedEnvironmentConverterResponseDTO envResponseDTO = getEnvironmentConverterResponseDTO(environment);

      InfrastructureEntity infrastructureEntity = infrastructureOp.get();
      String mergedNgInfrastructureYaml =
          UnifiedInfrastructureConversionUtility.getMergedInfrastructureYaml(requestDTO, infrastructureEntity);
      InfrastructureConfig ngInfrastructureConfig =
          YamlUtils.read(mergedNgInfrastructureYaml, InfrastructureConfig.class);
      String mergedUnifiedInfrastructureYaml =
          UnifiedInfrastructureConversionUtility.toUnifiedInfrastructureYaml(ngInfrastructureConfig);

      // Convert v0 infra YAML expressions from <+...> to ${{...}} for template resolution
      String infraV0Yaml = UnifiedInfrastructureConversionUtility.convertExpressionsV0ToV1(mergedNgInfrastructureYaml);

      ServiceStepOutcome serviceStepOutcome = getServiceStepOutcome(requestDTO);
      EnvironmentOutcome environmentOutcome = getEnvironmentOutcome(requestDTO, environment);

      EntityGitDetails gitDetails = getEntityGitDetails(accountId, infrastructureEntity);

      // Prepare infraV0OutcomeYaml
      String infraV0OutcomeYaml = getInfraV0OutcomeYaml(accountId, orgIdentifier, projectIdentifier,
          infrastructureEntity, ngInfrastructureConfig, serviceStepOutcome, environmentOutcome, gitDetails);

      return ResponseDTO.newResponse(
          UnifiedInfraConvertorResponse.builder()
              .responseDTO(
                  UnifiedInfraConverterResponseDTO.builder()
                      .name(infrastructureEntity.getName())
                      .identifier(infrastructureEntity.getIdentifier())
                      .description(infrastructureEntity.getDescription())
                      .tags(convertToMap(infrastructureEntity.getTags()))
                      .mergedInfrastructureYaml(mergedUnifiedInfrastructureYaml)
                      .environmentResponse(envResponseDTO)
                      .infraV0OutcomeYaml(infraV0OutcomeYaml)
                      .infraV0Yaml(infraV0Yaml)
                      .scopedServiceRefs(ngInfrastructureConfig.getInfrastructureDefinitionConfig().getScopedServices())
                      .build())
              .build());
    } catch (Exception e) {
      String contextMessage = String.format("Failed to convert infrastructure [%s] to unified infrastructure in "
              + "environment [%s], in project [%s], in org [%s]",
          infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier);
      log.error(contextMessage, e);
      NgManagerErrorResponseDTO error = NgManagerErrorResponseUtils.build(e, contextMessage);
      return ResponseDTO.newResponse(UnifiedInfraConvertorResponse.builder().error(error).build());
    }
  }

  @Nullable
  private EntityGitDetails getEntityGitDetails(String accountId, InfrastructureEntity infrastructureEntity) {
    EntityGitDetails gitDetails = null;
    if (ngFeatureFlagHelperService.isDisabled(accountId, FeatureName.PIPE_REVERT_SVC_ENV_INFRA_GIT_DETAILS_OUTPUT)) {
      gitDetails = gitAwareEntityHelper.getEntityGitDetails(infrastructureEntity);
    }
    return gitDetails;
  }

  @Nullable
  private static ServiceStepOutcome getServiceStepOutcome(UnifiedInfraConverterRequestDTO requestDTO) {
    ServiceStepOutcome serviceStepOutcome = null;
    if (isNotEmpty(requestDTO.getServiceStepOutcomeYaml())) {
      try {
        serviceStepOutcome = YamlUtils.read(requestDTO.getServiceStepOutcomeYaml(), ServiceStepOutcome.class);
      } catch (Exception e) {
        log.warn("Failed to deserialize ServiceStepOutcome from YAML", e);
      }
    }
    return serviceStepOutcome;
  }

  @Nullable
  private EnvironmentOutcome getEnvironmentOutcome(
      UnifiedInfraConverterRequestDTO requestDTO, Environment environment) {
    EnvironmentOutcome environmentOutcome = null;
    if (isNotEmpty(requestDTO.getEnvironmentOutcomeYaml())) {
      try {
        environmentOutcome = YamlUtils.read(requestDTO.getEnvironmentOutcomeYaml(), EnvironmentOutcome.class);
      } catch (Exception e) {
        log.warn("Failed to deserialize EnvironmentOutcome from YAML", e);
      }
    }

    // Build EnvironmentOutcome from Environment entity if not provided in request
    if (environmentOutcome == null && environment != null) {
      EntityGitDetails envGitDetails = gitAwareEntityHelper.getEntityGitDetails(environment);
      environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(environment, envGitDetails);
    }

    return environmentOutcome;
  }

  private static UnifiedEnvironmentConverterResponseDTO getEnvironmentConverterResponseDTO(Environment environment) {
    return UnifiedEnvironmentConverterResponseDTO.builder()
        .name(environment.getName())
        .identifier(environment.getIdentifier())
        .description(environment.getDescription())
        .tags(convertToMap(environment.getTags()))
        .type(environment.getType())
        .color(environment.getColor())
        .build();
  }

  private String getInfraV0OutcomeYaml(String accountId, String orgIdentifier, String projectIdentifier,
      InfrastructureEntity infrastructureEntity, InfrastructureConfig ngInfrastructureConfig,
      ServiceStepOutcome serviceStepOutcome, EnvironmentOutcome finalEnvironmentOutcome, EntityGitDetails gitDetails) {
    String infraV0OutcomeYaml = "";
    try {
      Infrastructure infrastructure = ngInfrastructureConfig.getInfrastructureDefinitionConfig().getSpec();

      infrastructure =
          UnifiedInfrastructureConversionUtility.convertInfrastructureReleaseNameFromV0ToV1(infrastructure);

      InfrastructureOutcome infrastructureOutcome = infrastructureOutcomeProvider.getOutcome(null, infrastructure,
          finalEnvironmentOutcome, serviceStepOutcome, accountId, orgIdentifier, projectIdentifier,
          convertToMap(infrastructureEntity.getTags()), infrastructureEntity.getDescription(), gitDetails);
      infraV0OutcomeYaml = YamlUtils.writeYamlString(infrastructureOutcome);
    } catch (Exception e) {
      log.warn("Failed to generate infraV0OutcomeYaml", e);
    }
    return infraV0OutcomeYaml;
  }

  @Hidden
  @POST
  @Path("/to-unified/list")
  @ApiOperation(value = "Convert multiple Infrastructures to Unified format", nickname = "toUnifiedInfrastructureList")
  public ResponseDTO<UnifiedInfrasConvertorResponse> convertToUnifiedInfrastructureList(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @RequestBody(required = true) @Valid UnifiedInfrasConverterRequestDTO requestDTO, @Context ScopeInfo scopeInfo)
      throws IOException {
    try {
      infrastructureHelper.checkForAccessOrThrow(
          accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
      Map<String, String> infraIdToYaml = requestDTO.getInfraIdsToInputYaml();
      if (isEmpty(infraIdToYaml)) {
        return ResponseDTO.newResponse(null);
      }
      boolean isAllInfra = infraIdToYaml.containsKey(YAMLFieldNameConstants.ALL);
      List<InfrastructureEntity> infrastructureEntities = infrastructureEntityService.getAllInfrastructureFromEnvRef(
          accountId, orgIdentifier, projectIdentifier, envIdentifier, scopeInfo);

      List<UnifiedInfraConverterResponseDTO> responseDTOs = new ArrayList<>();
      infrastructureEntities.forEach(infraEntity -> {
        if (isAllInfra || infraIdToYaml.containsKey(infraEntity.getIdentifier())) {
          String mergedYaml = infraIdToYaml.get(infraEntity.getIdentifier());
          UnifiedInfraConverterRequestDTO singleRequestDTO =
              UnifiedInfraConverterRequestDTO.builder().infraInputsYaml(mergedYaml).build();
          try {
            mergedYaml =
                UnifiedInfrastructureConversionUtility.getMergedInfrastructureYaml(singleRequestDTO, infraEntity);
            mergedYaml = UnifiedInfrastructureConversionUtility.toUnifiedInfrastructureYaml(mergedYaml);
          } catch (Exception ex) {
            log.warn("Error while merging input yaml");
          }
          UnifiedInfraConverterResponseDTO responseDTO = UnifiedInfraConverterResponseDTO.builder()
                                                             .name(infraEntity.getName())
                                                             .identifier(infraEntity.getIdentifier())
                                                             .description(infraEntity.getDescription())
                                                             .tags(convertToMap(infraEntity.getTags()))
                                                             .mergedInfrastructureYaml(mergedYaml)
                                                             .build();
          responseDTOs.add(responseDTO);
        }
      });
      return ResponseDTO.newResponse(UnifiedInfrasConvertorResponse.builder().responseDTOs(responseDTOs).build());
    } catch (Exception e) {
      String contextMessage = String.format("Failed to convert infrastructures to unified infrastructure list in "
              + "environment [%s], in project [%s], in org [%s]",
          envIdentifier, projectIdentifier, orgIdentifier);
      log.error(contextMessage, e);
      NgManagerErrorResponseDTO error = NgManagerErrorResponseUtils.build(e, contextMessage);
      return ResponseDTO.newResponse(UnifiedInfrasConvertorResponse.builder().error(error).build());
    }
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

  @Hidden
  @POST
  @Path("/to-unified/infra-git-details")
  @ApiOperation(value = "Get Git details for Infrastructure", nickname = "getInfraGitDetails")
  public ResponseDTO<UnifiedGitEntityInfoResponseDTO> getInfraGitDetails(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_KEY, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_REF_KEY) String environmentRef,
      @Parameter(description = "Environment branch") @QueryParam("environmentBranch") String environmentBranch,
      @Context ScopeInfo scopeInfo) {
    try {
      GitEntityInfo gitEntityInfo = infrastructureEntityService.getGitDetailsForInfrastructure(
          accountId, orgIdentifier, projectIdentifier, scopeInfo, environmentRef, environmentBranch);
      return ResponseDTO.newResponse(UnifiedGitEntityInfoResponseDTO.builder().gitEntityInfo(gitEntityInfo).build());
    } catch (Exception e) {
      String contextMessage = String.format(
          "Failed to fetch git details for infrastructure in environment [%s], in project [%s], in org [%s]",
          environmentRef, projectIdentifier, orgIdentifier);
      log.error(contextMessage, e);
      NgManagerErrorResponseDTO error = NgManagerErrorResponseUtils.build(e, contextMessage);
      return ResponseDTO.newResponse(UnifiedGitEntityInfoResponseDTO.builder().error(error).build());
    }
  }

  @POST
  @Path("/service-discovery/enable")
  @ApiOperation(
      value = "Bulk enable service discovery on infrastructure definitions", nickname = "enableServiceDiscovery")
  @Operation(operationId = "enableServiceDiscovery",
      summary = "Enable service discovery on multiple infrastructure definitions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of infrastructure identifiers that were successfully enabled")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO>
  enableServiceDiscovery(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          ENVIRONMENT_IDENTIFIER_KEY) String envIdentifier,
      @NotNull @Valid io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableRequestDTO request) {
    if (!ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_CLUSTER_INVENTORY)) {
      throw new InvalidRequestException("Service Discovery is not enabled for this account.");
    }
    infrastructureHelper.checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "enable");
    io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO response = discoveryOrchestrator.enableAll(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, request.getInfraIdentifiers());
    return ResponseDTO.newResponse(response);
  }
}
