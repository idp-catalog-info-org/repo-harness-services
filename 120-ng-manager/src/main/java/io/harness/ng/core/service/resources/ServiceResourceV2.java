/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
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
import io.harness.beans.ScopeLevel;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.bean.yaml.ArtifactSourceConfig;
import io.harness.cdng.artifact.utils.ArtifactsProcessedResponse;
import io.harness.cdng.deploymentmetadata.DeploymentMetadataServiceHelper;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.KustomizeCommandFlagType;
import io.harness.cdng.service.beans.ServiceDefinitionCategory;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceV2YamlMetadata;
import io.harness.cdng.service.beans.ServicesV2YamlMetadataDTO;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.data.validator.EntityNameValidator;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.MoveConfigOperationType;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitx.GitXUtils;
import io.harness.imageplugins.PluginInfoResponseDto;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.artifact.ArtifactSourceYamlRequestDTO;
import io.harness.ng.core.artifacts.resources.artifactsource.ArtifactSourceTemplateHelper;
import io.harness.ng.core.beans.DocumentationConstants;
import io.harness.ng.core.beans.EntityWithGitInfo;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInputV2;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.RepoListResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.opa.gitx.ServiceOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.service.ServiceGitMetadataUpdateParams;
import io.harness.ng.core.service.ServiceGitUpdateResponseDTO;
import io.harness.ng.core.service.dto.DestinationServiceConfig;
import io.harness.ng.core.service.dto.ForceImportServiceRequestDTO;
import io.harness.ng.core.service.dto.ForceImportServiceResponse;
import io.harness.ng.core.service.dto.RemoteServicesDTO;
import io.harness.ng.core.service.dto.RemoteServicesResponseDTO;
import io.harness.ng.core.service.dto.ServiceCloneRequestDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.SourceServiceConfig;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ForceImportServiceYamlOperationDTO;
import io.harness.ng.core.service.entity.NGServiceEntityMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.entity.ServiceImportResponseDTO;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.entity.ServiceMoveConfigOperationDTO;
import io.harness.ng.core.service.entity.ServiceMoveConfigRequestDTO;
import io.harness.ng.core.service.entity.ServiceMoveConfigResponse;
import io.harness.ng.core.service.entity.ServiceRemoteRepoInfo;
import io.harness.ng.core.service.entity.ServiceRemoteRepoListResponse;
import io.harness.ng.core.service.helpers.ServiceFilterHelper;
import io.harness.ng.core.service.mappers.GitImportToServiceImportOperationMapper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.services.impl.ServiceRbacHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.service.NGEntityFetchRequest;
import io.harness.unified.service.NGServiceEntityMetadata;
import io.harness.unified.service.NgServicePropertiesResponse;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;
import io.harness.unified.service.UnifiedServiceConverterResponse;
import io.harness.unified.service.UnifiedServiceTypeResponse;
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

import software.wings.beans.ServiceKeys;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableSet;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.Max;
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
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@NextGenManagerAuth
@Api("/servicesV2")
@Path("/servicesV2")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Services", description = "This contains APIs related to Services")
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
public class ServiceResourceV2 {
  private final ServiceEntityService serviceEntityService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final AccessControlClient accessControlClient;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject ArtifactSourceTemplateHelper artifactSourceTemplateHelper;
  private ServiceEntityYamlSchemaHelper serviceSchemaHelper;
  private ScopeAccessHelper scopeAccessHelper;
  @Inject private DeploymentMetadataServiceHelper deploymentMetadataServiceHelper;
  private ServiceRbacHelper serviceRbacHelper;
  @Inject ServiceCloneHelper serviceCloneHelper;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private ServiceHelper serviceHelper;
  @Inject private ServiceOpaStatusHandler serviceOpaStatusHandler;
  @Inject private CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  private NgToUnifiedServiceHelper ngToUnifiedServiceHelper;

  public static final String SERVICE_PARAM_MESSAGE = "Service Identifier for the entity";
  public static final String SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "List of Service Identifiers for the entities, maximum size of list is 1000.";
  private static final int MAX_LIMIT = 1000;
  private static final String UNAUTHORIZED_TO_VIEW_SERVICES = "Unauthorized to view services";
  private static final Set<String> allowedServiceSpecs =
      ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES);

  @GET
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Gets a Service by identifier", nickname = "getServiceV2")
  @NGAccessControlCheck(resourceType = SERVICE, permission = SERVICE_VIEW_PERMISSION)
  @Operation(operationId = "getServiceV2", summary = "Gets a Service by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Service")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<ServiceResponse>
  get(@Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Service is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "Specify true for fetching resolved service yaml", hidden = true) @QueryParam(
          "fetchResolvedYaml") @DefaultValue("false") boolean fetchResolvedYaml,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @Context ScopeInfo scopeInfo) {
    Optional<ServiceEntity> serviceEntity;
    serviceEntity = serviceEntityService.get(scopeInfo, serviceIdentifier, deleted,
        GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);
    if (serviceEntity.isPresent()) {
      ServiceEntity service = serviceEntity.get();
      if (isEmpty(service.getYaml(scopeInfo))) {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(service, scopeInfo);
        service.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }

      if (GitXUtils.isRemoteEntity(service)) {
        try {
          serviceSchemaHelper.validateSchema(accountId, service.getYaml(scopeInfo));
        } catch (InvalidYamlException ex) {
          return ResponseDTO.newResponse(
              ServiceResponse.builder()
                  .service(ServiceElementMapper.writeDTO(service, scopeInfo))
                  .createdAt(service.getCreatedAt())
                  .lastModifiedAt(service.getLastModifiedAt())
                  .entityValidityDetails(
                      EntityValidityDetails.builder().valid(false).invalidYaml(service.getYaml(scopeInfo)).build())
                  .build());
        }
      }
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }

    if (fetchResolvedYaml) {
      String yaml;
      yaml = serviceEntityService.resolveArtifactSourceTemplateRefs(scopeInfo, serviceEntity.get().getYaml(scopeInfo));
      serviceEntity.get().setYaml(yaml);
    }
    ServiceResponse response;
    ScopeInfo finalScopeInfo = scopeInfo;
    response = serviceEntity.map(entity -> ServiceElementMapper.toResponseWrapper(entity, finalScopeInfo)).orElse(null);
    response.setEntityValidityDetails(EntityValidityDetails.builder().valid(true).build());
    cdOpaOnSaveStatusApiHelper
        .resolveGetOpaOnSaveStatus(serviceEntity.get(), accountId, scopeInfo, serviceOpaStatusHandler)
        .ifPresent(response::setOpaOnSaveStatus);
    return ResponseDTO.newResponse(response);
  }

  @POST
  @ApiOperation(value = "Create a Service", nickname = "createServiceV2")
  @Operation(operationId = "createServiceV2", summary = "Create a Service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Service")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be created",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service create payload",
                         value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
          }) @Valid ServiceRequestDTO serviceRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created",
          hidden = true) @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO, scopeInfo);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, serviceEntity.getYaml(scopeInfo));
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(), serviceEntity.getAccountId());
    ServiceGovernanceDataResponse createdServiceMapper;

    createdServiceMapper = serviceEntityService.create(serviceEntity, scopeInfo);

    return ResponseDTO.newResponse(ServiceElementMapper.toResponseWrapper(
        createdServiceMapper.getService(), createdServiceMapper.getGovernanceMetadata(), scopeInfo));
  }

  @POST
  @Path("/batch")
  @ApiOperation(value = "Create Services", nickname = "createServicesV2")
  @Operation(operationId = "createServicesV2", summary = "Create Services",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Services")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<ServiceResponse>>
  createServices(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Services to be created, maximum 1000 services can be created.") @Valid
      @Size(max = MAX_LIMIT) List<ServiceRequestDTO> serviceRequestDTOs) {
    throwExceptionForNoRequestDTO(serviceRequestDTOs);
    for (ServiceRequestDTO serviceRequestDTO : serviceRequestDTOs) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
          Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
      orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
          serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(), accountId);
    }
    serviceRequestDTOs.forEach(
        serviceRequestDTO -> serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml()));
    List<ServiceEntity> serviceEntities = serviceRequestDTOs.stream()
                                              .map(serviceRequestDTO
                                                  -> ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO,
                                                      ScopeInfo.builder()
                                                          .accountIdentifier(accountId)
                                                          .orgIdentifier(serviceRequestDTO.getOrgIdentifier())
                                                          .projectIdentifier(serviceRequestDTO.getProjectIdentifier())
                                                          .uniqueId("DummyValNotUsed")
                                                          .build()))
                                              .collect(toList());

    for (int i = 0; i < serviceRequestDTOs.size(); i++) {
      if (isEmpty(serviceRequestDTOs.get(i).getYaml())) {
        serviceSchemaHelper.validateSchema(accountId,
            serviceEntities.get(i).getYaml(ScopeInfo.builder()
                                               .accountIdentifier(serviceEntities.get(i).getAccountIdentifier())
                                               .orgIdentifier(serviceEntities.get(i).getOrgIdentifier())
                                               .projectIdentifier(serviceEntities.get(i).getProjectIdentifier())
                                               .uniqueId("DummyValue")
                                               .build()));
      }
    }

    Page<ServiceEntity> createdServices = serviceEntityService.bulkCreate(accountId, serviceEntities);

    Set<String> parentUniqueIds = new HashSet<>();
    createdServices.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));

    Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);

    return ResponseDTO.newResponse(getNGPageResponse(createdServices.map(serviceEntity -> {
      Optional<ScopeInfo> scopeInfo = scopeInfos.get(serviceEntity.getParentUniqueId());
      return ServiceElementMapper.toResponseWrapper(serviceEntity, scopeInfo.get());
    })));
  }

  @POST
  @Path("/batch/partial")
  @ApiOperation(value = "Create Services with partial success support", nickname = "createServicesPartialBatch")
  @Operation(operationId = "createServicesPartialBatch",
      summary = "Create Services with partial success support - returns both successful and failed services",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the batch creation result with success and failure details")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<io.harness.ng.core.service.dto.ServiceBatchResponseDTO>
  createServicesPartialBatch(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                             @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Services to be created, maximum 1000 services can be created.") @Size(
          max = MAX_LIMIT) List<ServiceRequestDTO> serviceRequestDTOs) {
    if (isEmpty(serviceRequestDTOs)) {
      throw new InvalidRequestException("Service request list cannot be empty");
    }

    List<ServiceEntity> serviceEntities = new ArrayList<>();
    List<io.harness.ng.core.service.dto.ServiceFailureResponse> conversionFailures = new ArrayList<>();

    for (ServiceRequestDTO serviceRequestDTO : serviceRequestDTOs) {
      try {
        // Perform bean validation manually to support partial success
        validateServiceRequestDTO(serviceRequestDTO);

        accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
            Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
        orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
            serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(), accountId);

        ServiceEntity serviceEntity = convertDTOToEntity(accountId, serviceRequestDTO);
        serviceEntities.add(serviceEntity);
      } catch (Exception ex) {
        log.warn("Failed to process service DTO [{}]: {}", serviceRequestDTO.getIdentifier(), ex.getMessage());
        conversionFailures.add(
            io.harness.ng.core.service.dto.ServiceFailureResponse.builder()
                .accountId(accountId)
                .orgIdentifier(serviceRequestDTO.getOrgIdentifier())
                .projectIdentifier(serviceRequestDTO.getProjectIdentifier())
                .identifier(serviceRequestDTO.getIdentifier())
                .errorMessage(format("Service [%s] failed: %s", serviceRequestDTO.getIdentifier(), ex.getMessage()))
                .correlationId(io.harness.ng.core.CorrelationContext.getCorrelationId())
                .build());
      }
    }

    io.harness.ng.core.service.dto.ServiceBatchResponseDTO batchResponse;
    if (isNotEmpty(serviceEntities)) {
      batchResponse = serviceEntityService.bulkCreatePartial(accountId, serviceEntities);
    } else {
      batchResponse = io.harness.ng.core.service.dto.ServiceBatchResponseDTO.builder()
                          .successfulServices(Collections.emptyList())
                          .failedServices(Collections.emptyList())
                          .totalSuccess(0)
                          .totalFailed(0)
                          .build();
    }

    List<io.harness.ng.core.service.dto.ServiceFailureResponse> allFailures = new ArrayList<>(conversionFailures);
    allFailures.addAll(batchResponse.getFailedServices());

    return ResponseDTO.newResponse(io.harness.ng.core.service.dto.ServiceBatchResponseDTO.builder()
                                       .successfulServices(batchResponse.getSuccessfulServices())
                                       .failedServices(allFailures)
                                       .totalRequested(serviceRequestDTOs.size())
                                       .totalSuccess(batchResponse.getTotalSuccess())
                                       .totalFailed(allFailures.size())
                                       .build());
  }

  @POST
  @Path("/check-allowed-values")
  @ApiOperation(value = "Check for allowed-values in the Services", nickname = "checkAllowedValuesInServices")
  @Operation(operationId = "checkAllowedValuesInServices", summary = "Check for allowed-values in the Services",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of services using allowed-values")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<AllowedValuesUsagesInternalDTO>
  checkAllowedValues(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody AllowedValuesUsagesRequestDTO request) {
    return ResponseDTO.newResponse(serviceEntityService.checkForAllowedValues(accountId, request));
  }

  @DELETE
  @Path("{serviceIdentifier}")
  @ApiOperation(value = "Delete a service by identifier", nickname = "deleteServiceV2")
  @NGAccessControlCheck(resourceType = SERVICE, permission = "core_service_delete")
  @Operation(operationId = "deleteServiceV2", summary = "Delete a Service by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Service is deleted") })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE)
      @DefaultValue("false") boolean forceDelete, @Context ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(serviceEntityManagementService.deleteService(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, ifMatch, forceDelete, scopeInfo));
  }

  @PUT
  @ApiOperation(value = "Update a service by identifier", nickname = "updateServiceV2")
  @Operation(operationId = "updateServiceV2", summary = "Update a Service by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Service") })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be updated",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service update payload",
                         value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
          }) @Valid ServiceRequestDTO serviceRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated")
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) {
    throwExceptionForNoRequestDTO(serviceRequestDTO);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO, scopeInfo);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, requestService.getYaml(scopeInfo));
    }
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceGovernanceDataResponse updatedServiceMapper;
    updatedServiceMapper = serviceEntityService.update(requestService, scopeInfo);

    ServiceResponse serviceResponse;
    serviceResponse = ServiceElementMapper.toResponseWrapper(
        updatedServiceMapper.getService(), updatedServiceMapper.getGovernanceMetadata(), scopeInfo);
    return ResponseDTO.newResponse(serviceResponse);
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert a service by identifier", nickname = "upsertServiceV2")
  @Operation(operationId = "upsertServiceV2", summary = "Upsert a Service by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the updated Service") })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true, description = "Details of the Service to be upserted", content = {
        @Content(examples = @ExampleObject(name = "Create", summary = "Sample Service upsert payload",
                     value = DocumentationConstants.serviceRequestDTO, description = "Sample Service payload"))
      }) @Valid ServiceRequestDTO serviceRequestDTO) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());

    throwExceptionForNoRequestDTO(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO, scopeInfo);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, requestService.getYaml(scopeInfo));
    }
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(), requestService.getAccountId());
    ServiceGovernanceDataResponse upsertServiceMapper;
    upsertServiceMapper = serviceEntityService.upsert(requestService, UpsertOptions.DEFAULT, scopeInfo);
    ServiceResponse serviceResponse;
    serviceResponse = ServiceElementMapper.toResponseWrapper(
        upsertServiceMapper.getService(), upsertServiceMapper.getGovernanceMetadata(), scopeInfo);

    return ResponseDTO.newResponse(serviceResponse);
  }

  @GET
  @ApiOperation(value = "Gets Service list", nickname = "getServiceList")
  @Operation(operationId = "getServiceList", summary = "Gets Service list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of Services for a Project")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<ServiceResponse>>
  listServices(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @Parameter(description = "Specifies the sorting criteria of the list. Like sorting based on the last updated "
              + "entity, alphabetical sorting in an ascending or descending order") @QueryParam("sort")
      List<String> sort,
      @QueryParam("type") ServiceDefinitionType type,
      @Parameter(description = "Filter services by the family of service types they belong to, for example AiService "
              + "for AI agent services. Can be combined with type to narrow down to a single type.")
      @QueryParam("category") ServiceDefinitionCategory category,
      @QueryParam("gitOpsEnabled") Boolean gitOpsEnabled,
      @Parameter(description = "Filter services by tags. Each entry is of the form key:value, or key for a key-only "
              + "match. A service matches if it has any of the supplied tags.") @QueryParam("tags") List<String> tags,
      @Parameter(description = "Filter services by where the entity is stored. INLINE returns Harness-stored services, "
              + "REMOTE returns Git-stored services.") @QueryParam("storeType") StoreType storeType,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") @DefaultValue("false") boolean includeAllServicesAccessibleAtScope,
      @Parameter(description = "Specify true if services' version info need to be included", hidden = true) @QueryParam(
          "includeVersionInfo") @DefaultValue("false") boolean includeVersionInfo,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName,
      @Parameter(description = "Specify true if only services with edit permission are required. Default value false "
              + "will mean services with view permission are required.",
          hidden = true) @QueryParam("editOnlyRBACPermission") @DefaultValue("false") boolean editOnlyRBACPermissions,
      @Context ScopeInfo scopeInfo) {
    Boolean effectiveGitOpsEnabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES) ? null : gitOpsEnabled;
    // When a storeType filter is supplied we match the deployment type strictly inside applyServiceViewFilters,
    // so the type is not threaded into the base criteria (which would otherwise also pull in all remote services).
    ServiceDefinitionType typeForBaseCriteria = storeType == null ? type : null;
    Criteria criteria;
    Map<ScopeLevel, String> uniqueIdsMap = scopeInfoService.getUniqueIdsIncludingParentScopes(scopeInfo);
    criteria = ServiceFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, typeForBaseCriteria,
        effectiveGitOpsEnabled, includeAllServicesAccessibleAtScope, repoName, uniqueIdsMap);
    ServiceFilterHelper.applyServiceViewFilters(criteria, tags, storeType, type, category);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    String serviceRBACPermission = SERVICE_VIEW_PERMISSION;
    if (editOnlyRBACPermissions) {
      serviceRBACPermission = SERVICE_UPDATE_PERMISSION;
    }

    // Adding queryParam "serviceIdentifiers" directly for $in criteria against "identifier" key,
    // Only in case when user has view/edit permission to all services
    // In else case, we have to first apply filter for queryParam "serviceIdentifiers",
    // Then apply filter for specific services view/edit access then pass aggregated result
    // for $in criteria against "identifier" key
    boolean isCustomDeployment =
        ServiceDefinitionType.CUSTOM_DEPLOYMENT == type && !isEmpty(deploymentTemplateIdentifier);
    Page<ServiceEntity> serviceEntities;
    if (hasRequiredPermissionForAllServices(accountId, orgIdentifier, projectIdentifier, serviceRBACPermission)) {
      if (isNotEmpty(serviceIdentifiers)) {
        criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
      }
      serviceEntities = serviceEntityService.list(criteria, pageRequest, isCustomDeployment);
    } else {
      serviceEntities = serviceEntityService.getRBACFilteredServices(
          criteria, pageRequest, serviceIdentifiers, serviceRBACPermission, isCustomDeployment);
    }

    if (isCustomDeployment) {
      serviceEntities = customDeploymentYamlHelper.getFilteredServiceEntities(page, size, sort,
          deploymentTemplateIdentifier, versionLabel, serviceEntities, accountId, orgIdentifier, projectIdentifier);
    }
    Set<String> parentUniqueIds = new HashSet<>();
    serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
    Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);

    serviceEntities.forEach(serviceEntity -> {
      if (isEmpty(serviceEntity.getYaml(scopeInfos.get(serviceEntity.getParentUniqueId()).get()))) {
        serviceEntity.setYaml(serviceEntity.fetchNonEmptyYaml(scopeInfos.get(serviceEntity.getParentUniqueId()).get()));
      }
    });

    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(entity -> {
      ScopeInfo entityScopeInfo = scopeInfos.get(entity.getParentUniqueId()).get();
      return ServiceElementMapper.toResponseWrapper(entity, includeVersionInfo, entityScopeInfo);
    })));
  }

  @GET
  @Hidden
  @Path("/list/all-services")
  @ApiOperation(value = "Get all services list", nickname = "getAllServicesList")
  @Operation(operationId = "getAllServicesList",
      summary = "Get all services list across organizations and projects within account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of all Services across organizations and projects within account")
      },
      hidden = true)
  @InternalApi
  @NGAccessControlCheck(resourceType = SERVICE, permission = SERVICE_VIEW_PERMISSION)
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<ServiceResponse>>
  getAllServicesList(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGResourceFilterConstants.SEARCH_TERM) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(1000) int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SORT)
      List<String> sort, @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    Criteria criteria;
    if (useScopeInfo) {
      criteria = new Criteria();
      criteria.and(ServiceEntityKeys.accountId).is(accountIdentifier);
      if (orgIdentifier != null) {
        if (projectIdentifier != null) {
          criteria.and(ServiceEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());
        } else {
          Set<String> parentUniqueIdsInScope = scopeInfoService.getUniqueIdsIncludingChildScope(scopeInfo);
          criteria.and(ServiceEntityKeys.parentUniqueId).in(parentUniqueIdsInScope);
        }
      }

      ServiceFilterHelper.addDeleteAndSearchTermCriteriaForListingAllServices(criteria, searchTerm, false);
    } else {
      criteria = ServiceFilterHelper.createCriteriaForListingAllServices(
          accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, false);
    }
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt))
        : PageUtils.getPageRequest(page, size, sort);
    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);

    if (useScopeInfo) {
      Set<String> parentUniqueIds = new HashSet<>();
      serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
      Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountIdentifier, parentUniqueIds);
      return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(entity -> {
        ScopeInfo entityScopeInfo = scopeInfos.get(entity.getParentUniqueId()).get();
        return ServiceElementMapper.toResponseWrapper(entity, entityScopeInfo);
      })));
    } else {
      return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(ServiceElementMapper::toResponseWrapper)));
    }
  }

  @GET
  @Path("/list/scoped")
  @Hidden
  @ApiOperation(value = "Gets Service list filtered by service refs", nickname = "getServiceListFiltered")
  @Operation(operationId = "getServiceList", summary = "Gets Service list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Services filtered by scoped service refs")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<ServiceResponse>>
  getServicesFilteredByRefs(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @Context ScopeInfo scopeInfo) {
    return doGetServicesFilteredByRefs(
        page, size, accountId, orgIdentifier, projectIdentifier, serviceIdentifiers, scopeInfo);
  }

  @POST
  @Path("/list/scoped")
  @Hidden
  @ApiOperation(
      value = "Gets Service list filtered by service refs using POST", nickname = "getServiceListFilteredPost")
  @Operation(operationId = "getServiceListPost", summary = "Gets Service list using POST",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Services filtered by scoped service refs")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<ServiceResponse>>
  getServicesFilteredByRefsPost(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                                    NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "List of ServicesIds") List<String> serviceIdentifiers, @Context ScopeInfo scopeInfo) {
    return doGetServicesFilteredByRefs(
        page, size, accountId, orgIdentifier, projectIdentifier, serviceIdentifiers, scopeInfo);
  }

  private ResponseDTO<PageResponse<ServiceResponse>> doGetServicesFilteredByRefs(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers, ScopeInfo scopeInfo) {
    checkAccessForListingAtScope(accountId, orgIdentifier, projectIdentifier, serviceIdentifiers);
    Criteria criteria;
    Map<ScopeLevel, String> uniqueIdsMap = scopeInfoService.getUniqueIdsIncludingParentScopes(scopeInfo);
    criteria = ServiceFilterHelper.createCriteriaForGetList(
        scopeInfo, serviceIdentifiers, false, null, null, null, false, null, uniqueIdsMap);
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));

    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(
        accountId, serviceEntities.stream().map(ServiceEntity::getParentUniqueId).collect(Collectors.toSet()));

    serviceEntities.forEach(serviceEntity -> {
      if (isEmpty(serviceEntity.getYaml(scopeInfoMap.get(serviceEntity.getParentUniqueId()).get()))) {
        ScopeInfo scopeInfoServiceAccount = scopeInfoMap.get(serviceEntity.getParentUniqueId()).get();
        NGServiceConfig ngServiceConfig =
            NGServiceEntityMapper.toNGServiceConfig(serviceEntity, scopeInfoServiceAccount);
        serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
      }
    });

    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(entity -> {
      ScopeInfo entityScopeInfo = scopeInfoMap.get(entity.getParentUniqueId()).get();
      return ServiceElementMapper.toResponseWrapper(entity, false, entityScopeInfo);
    })));
  }

  private void checkAccessForListingAtScope(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    if (isEmpty(serviceIdentifiers)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    boolean checkProjectLevelList = false;
    boolean checkOrgLevelList = false;
    boolean checkAccountLevelList = false;

    if (isNotEmpty(serviceIdentifiers)) {
      for (String serviceRef : serviceIdentifiers) {
        if (isNotEmpty(serviceRef) && !EngineExpressionEvaluator.hasExpressions(serviceRef)) {
          IdentifierRef serviceIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);
          if (io.harness.encryption.Scope.PROJECT.equals(serviceIdentifierRef.getScope())) {
            checkProjectLevelList = true;
          } else if (io.harness.encryption.Scope.ORG.equals(serviceIdentifierRef.getScope())) {
            checkOrgLevelList = true;
          } else if (io.harness.encryption.Scope.ACCOUNT.equals(serviceIdentifierRef.getScope())) {
            checkAccountLevelList = true;
          }
        }
      }
    }

    // listing without scoped refs
    if (checkProjectLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    if (checkOrgLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, null),
          Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }

    if (checkAccountLevelList) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, null, null), Resource.of(SERVICE, null),
          SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    }
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets Service Access list ", nickname = "getServiceAccessList")
  @Operation(operationId = "getServiceAccessList", summary = "Gets Service Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Services for a Project that are accessible")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<List<ServiceResponse>>
  listAccessServices(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of ServicesIds") @QueryParam("serviceIdentifiers") @Size(
          max = MAX_LIMIT) List<String> serviceIdentifiers,
      @Parameter(description = "Specifies the sorting criteria of the list. Like sorting based on the last updated "
              + "entity, alphabetical sorting in an ascending or descending order") @QueryParam("sort")
      List<String> sort,
      @QueryParam("type") ServiceDefinitionType type, @QueryParam("gitOpsEnabled") Boolean gitOpsEnabled,
      @Parameter(description = "The Identifier of deployment template if infrastructure is of type custom deployment")
      @QueryParam("deploymentTemplateIdentifier") String deploymentTemplateIdentifier,
      @Parameter(
          description = "The version label of deployment template if infrastructure is of type custom deployment")
      @QueryParam("versionLabel") String versionLabel,
      @QueryParam("deploymentMetadataYaml") String deploymentMetaDataYaml,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") @DefaultValue("false") boolean includeAllServicesAccessibleAtScope,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountId, orgIdentifier, projectIdentifier))),
        "Unauthorized to list services");
    Boolean effectiveGitOpsEnabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES) ? null : gitOpsEnabled;
    Criteria criteria;
    Map<ScopeLevel, String> uniqueIdsMap = scopeInfoService.getUniqueIdsIncludingParentScopes(scopeInfo);
    criteria = ServiceFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, type, effectiveGitOpsEnabled,
        includeAllServicesAccessibleAtScope, null, uniqueIdsMap);
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }

    List<ServiceResponse> serviceList;
    if (type == ServiceDefinitionType.CUSTOM_DEPLOYMENT && !isEmpty(deploymentTemplateIdentifier)) {
      List<String> versionLabels = customDeploymentYamlHelper.resolveVersionLabels(
          accountId, orgIdentifier, projectIdentifier, deploymentTemplateIdentifier, versionLabel);

      boolean useMetadata = featureFlagHelperService.isEnabled(
          accountId, FeatureName.CDS_OPTIMIZE_CUSTOM_DEPLOYMENT_LIST_WITH_BRANCH_METADATA);
      List<ServiceEntity> serviceEntities;
      if (useMetadata) {
        serviceEntities = serviceEntityService.listRunTimePermissionWithBranchMetadata(
            criteria, sort, accountId, scopeInfo.getUniqueId(), deploymentTemplateIdentifier, versionLabels);
      } else {
        serviceEntities = serviceEntityService.listRunTimePermission(criteria, sort, true);
      }
      Set<String> parentUniqueIds = new HashSet<>();
      serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
      Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);
      Stream<ServiceEntity> serviceStream = serviceEntities.stream();
      if (!useMetadata) {
        // old path: YAML was freshly fetched from Git for REMOTE; filter all entities
        serviceStream = serviceStream.filter(serviceEntity
            -> customDeploymentYamlHelper.isServiceUsingDeploymentTemplate(
                deploymentTemplateIdentifier, versionLabels, serviceEntity));
      } else {
        // new path: REMOTE entities are already matched by metadata; only INLINE need YAML filtering
        serviceStream = serviceStream.filter(serviceEntity
            -> StoreType.REMOTE.equals(serviceEntity.getStoreType())
                || customDeploymentYamlHelper.isServiceUsingDeploymentTemplate(
                    deploymentTemplateIdentifier, versionLabels, serviceEntity));
      }
      serviceList = serviceStream
                        .map(entity -> {
                          ScopeInfo entityScopeInfo = scopeInfos.get(entity.getParentUniqueId()).get();
                          return ServiceElementMapper.toAccessListResponseWrapper(entity, entityScopeInfo);
                        })
                        .collect(toList());
    } else if (ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS.equals(type)) {
      List<ServiceEntity> serviceEntities = serviceEntityService.listRunTimePermission(criteria, sort, false);

      Set<String> parentUniqueIds = new HashSet<>();
      serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
      Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);

      serviceEntities = deploymentMetadataServiceHelper.filterOnDeploymentMetadata(
          serviceEntities, type, deploymentMetaDataYaml, scopeInfos);

      serviceList = serviceEntities.stream()
                        .map(entity -> {
                          ScopeInfo entityScopeInfo = scopeInfos.get(entity.getParentUniqueId()).get();
                          return ServiceElementMapper.toAccessListResponseWrapper(entity, entityScopeInfo);
                        })
                        .collect(toList());
    } else {
      List<ServiceEntity> serviceEntities = serviceEntityService.listRunTimePermission(criteria, sort, false);
      Set<String> parentUniqueIds = new HashSet<>();
      serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
      Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);
      serviceList = serviceEntities.stream()
                        .map(entity -> {
                          ScopeInfo entityScopeInfo = scopeInfos.get(entity.getParentUniqueId()).get();
                          return ServiceElementMapper.toAccessListResponseWrapper(entity, entityScopeInfo);
                        })
                        .collect(toList());
    }
    Map<String, List<String>> envRefInfraRefsMapping = new HashMap<>();
    serviceList = filterByScopedInfrastructures(
        accountId, orgIdentifier, projectIdentifier, scopeInfo, serviceList, envRefInfraRefsMapping);

    List<PermissionCheckDTO> permissionCheckDTOS =
        serviceList.stream().map(CDNGRbacUtility::serviceResponseToPermissionCheckDTO).collect(toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return ResponseDTO.newResponse(serviceHelper.filterByPermissionAndId(accessControlList, serviceList));
  }

  @GET
  @Path("/dummy-serviceConfig-api")
  @ApiOperation(value = "This is dummy api to expose NGServiceConfig", nickname = "dummyNGServiceConfigApi")
  @ScopeInfoResolutionExemptedApi
  @Hidden
  // do not delete this.
  public ResponseDTO<NGServiceConfig> getNGServiceConfig() {
    return ResponseDTO.newResponse(NGServiceConfig.builder().build());
  }

  @GET
  @Path("/dummy-artifactSummary-api")
  @ApiOperation(value = "This is dummy api to expose ArtifactSummary", nickname = "dummyArtifactSummaryApi")
  @ScopeInfoResolutionExemptedApi
  @Hidden
  // do not delete this.
  public ResponseDTO<ArtifactSummary> getArtifactSummaries() {
    return ResponseDTO.newResponse(new ArtifactSummary() {
      @Override
      public String getType() {
        return null;
      }

      @Override
      public String getDisplayName() {
        return null;
      }
    });
  }

  @GET
  @Path("/runtimeInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api returns runtime input YAML", nickname = "getRuntimeInputsServiceEntity")
  @Hidden
  @NGAccessControlCheck(resourceType = SERVICE, permission = SERVICE_VIEW_PERMISSION)
  @Timed
  @ResponseMetered
  public ResponseDTO<NGEntityTemplateResponseDTO> getServiceRuntimeInputs(
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    Optional<ServiceEntity> serviceEntity;
    serviceEntity = serviceEntityService.get(scopeInfo, serviceIdentifier, false);

    if (serviceEntity.isPresent()) {
      if (isEmpty(serviceEntity.get().getYaml(scopeInfo))) {
        throw new InvalidRequestException("Service is not configured with a Service definition. Service Yaml is empty");
      }
      String serviceInputYaml = serviceEntityService.createServiceInputsYaml(
          accountId, serviceEntity.get().getYaml(scopeInfo), serviceEntity.get().getIdentifier());
      return ResponseDTO.newResponse(
          NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(serviceInputYaml).build());
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
  }

  @POST
  @Path("/servicesYamlMetadata")
  @ApiOperation(
      value = "This api returns service YAML and runtime input YAML", nickname = "getServicesYamlAndRuntimeInputs")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<ServicesV2YamlMetadataDTO>
  getServicesYamlAndRuntimeInputs(@Parameter(description = SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
                                  @NotNull ServicesYamlMetadataApiInput servicesYamlMetadataApiInput,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    List<String> serviceRefs = getServiceRefsWithViewPermission(
        accountId, orgIdentifier, projectIdentifier, servicesYamlMetadataApiInput.getServiceIdentifiers());
    List<ServiceV2YamlMetadata> serviceV2YamlMetadataList;
    serviceV2YamlMetadataList =
        serviceEntityService.getServicesYamlMetadata(scopeInfo, serviceRefs, new HashMap<>(), false);

    return ResponseDTO.newResponse(
        ServicesV2YamlMetadataDTO.builder().serviceV2YamlMetadataList(serviceV2YamlMetadataList).build());
  }

  @POST
  @Path("/v2/services-yaml-metadata")
  @ApiOperation(
      value = "This api returns service YAML and runtime input YAML", nickname = "getServicesYamlAndRuntimeInputsV2")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<ServicesV2YamlMetadataDTO>
  getServicesYamlAndRuntimeInputsV2(@Parameter(description = SERVICE_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
                                    @NotNull ServicesYamlMetadataApiInputV2 servicesYamlMetadataApiInput,
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
    // get service ref-> branch map
    Map<String, String> serviceRefBranchMap = getServiceBranchMap(
        accountId, orgIdentifier, projectIdentifier, servicesYamlMetadataApiInput.getServiceWithGitInfoList());

    // scoped service refs
    List<String> serviceRefs = getServiceRefsWithViewPermission(
        accountId, orgIdentifier, projectIdentifier, new ArrayList<>(serviceRefBranchMap.keySet()));

    List<ServiceV2YamlMetadata> servicesYamlMetadata;
    servicesYamlMetadata = serviceEntityService.getServicesYamlMetadata(
        scopeInfo, serviceRefs, serviceRefBranchMap, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache));

    return ResponseDTO.newResponse(
        ServicesV2YamlMetadataDTO.builder().serviceV2YamlMetadataList(servicesYamlMetadata).build());
  }

  private Map<String, String> getServiceBranchMap(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<EntityWithGitInfo> entityWithGitInfo) {
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
  @Path("/artifactSourceInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api returns artifact source identifiers and their runtime inputs YAML",
      nickname = "getArtifactSourceInputs")
  @Hidden
  @NGAccessControlCheck(resourceType = SERVICE, permission = SERVICE_VIEW_PERMISSION)
  @Timed
  @ResponseMetered
  public ResponseDTO<ArtifactSourcesResponseDTO>
  getArtifactSourceInputs(@Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
                              "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true)
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo) {
    Optional<ServiceEntity> serviceEntity;
    serviceEntity = serviceEntityService.get(
        scopeInfo, serviceIdentifier, false, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), false);

    if (serviceEntity.isPresent()) {
      if (isEmpty(serviceEntity.get().getYaml(scopeInfo))) {
        throw new InvalidRequestException(
            format("Service %s is not configured with a Service definition. Service Yaml is empty", serviceIdentifier));
      }
      return ResponseDTO.newResponse(
          serviceEntityService.getArtifactSourceInputs(serviceEntity.get().getYaml(scopeInfo), serviceIdentifier));
    } else {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
  }

  @GET
  @Path("/dummy-artifactSourceConfig-api")
  @ApiOperation(value = "This is dummy api to expose ArtifactSourceConfig", nickname = "dummyArtifactSourceConfigApi")
  @ScopeInfoResolutionExemptedApi
  @Hidden
  // do not delete this.
  public ResponseDTO<ArtifactSourceConfig> getArtifactSourceConfig() {
    return ResponseDTO.newResponse(ArtifactSourceConfig.builder().build());
  }

  @POST
  @Path("/artifact-source-references")
  @ApiOperation(
      value = "Gets Artifact Source Template entity references", nickname = "getArtifactSourceTemplateEntityReferences")
  @Operation(operationId = "getArtifactSourceTemplateEntityReferences",
      summary = "Gets Artifact Source Template entity references",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns all entity references in the artifact source template.")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<List<EntityDetailProtoDTO>>
  getEntityReferences(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Artifact Source Yaml Request DTO containing entityYaml")
      @NotNull ArtifactSourceYamlRequestDTO artifactSourceYamlRequestDTO, @Context ScopeInfo scopeInfo) {
    List<EntityDetailProtoDTO> entityReferences = artifactSourceTemplateHelper.getReferencesFromYaml(
        accountId, orgId, projectId, artifactSourceYamlRequestDTO.getEntityYaml());
    return ResponseDTO.newResponse(entityReferences);
  }

  @POST
  @Path("/mergeServiceInputs/{serviceIdentifier}")
  @ApiOperation(value = "This api merges old and new service inputs YAML", nickname = "mergeServiceInputs")
  @Hidden
  @NGAccessControlCheck(resourceType = SERVICE, permission = SERVICE_VIEW_PERMISSION)
  @Timed
  @ResponseMetered
  public ResponseDTO<ServiceInputsMergedResponseDto> mergeServiceInputs(
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      String oldServiceInputsYaml, @Context ScopeInfo scopeInfo) {
    ServiceInputsMergedResponseDto serviceInputsMergedResponseDto;
    serviceInputsMergedResponseDto =
        serviceEntityService.mergeServiceInputs(scopeInfo, serviceIdentifier, oldServiceInputsYaml);
    return ResponseDTO.newResponse(serviceInputsMergedResponseDto);
  }

  @GET
  @Path("/k8s/command-flags")
  @ApiOperation(value = "Get Command flags for K8s", nickname = "k8sCmdFlags")
  @Operation(operationId = "k8sCmdFlags", summary = "Retrieving the list of Kubernetes Command Options",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Kubernetes Command Options")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<Set<K8sCommandFlagType>>
  getK8sCommandFlags(
      @QueryParam("serviceSpecType") @NotNull String serviceSpecType, @QueryParam("stepType") String stepType) {
    Set<K8sCommandFlagType> k8sCmdFlags = new HashSet<>();
    for (K8sCommandFlagType k8sCommandFlagType : K8sCommandFlagType.values()) {
      if (k8sCommandFlagType.getServiceSpecTypes().contains(serviceSpecType)
          && (isEmpty(stepType) || k8sCommandFlagType.getStepTypes().isEmpty()
              || k8sCommandFlagType.getStepTypes().contains(stepType))) {
        k8sCmdFlags.add(k8sCommandFlagType);
      }
    }
    return ResponseDTO.newResponse(k8sCmdFlags);
  }

  @GET
  @Path("/hooks/actions")
  @ApiOperation(value = "Get Available Service Hook Actions", nickname = "hookActions")
  @Operation(operationId = "hookActions", summary = "Retrieving the list of actions available for service hooks",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of actions available for service hooks")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<Set<ServiceHookAction>>
  getServiceHookActions(@QueryParam("serviceSpecType") @NotNull String serviceSpecType) {
    if (allowedServiceSpecs.contains(serviceSpecType)) {
      return ResponseDTO.newResponse(Set.of(ServiceHookAction.values()));
    }
    throw new InvalidRequestException(
        format("Service with type: [%s] does not support service hooks", serviceSpecType));
  }

  @GET
  @Path("validate-template-inputs")
  @ApiOperation(value = "This validates inputs for templates like artifact sources for service yaml",
      nickname = "validateTemplateInputs")
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<ValidateTemplateInputsResponseDTO>
  validateTemplateInputs(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String serviceIdentifier,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "This contains details of Git Entity like Git Branch info", hidden = true)
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @Context ScopeInfo scopeInfo) {
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO;
    validateTemplateInputsResponseDTO =
        serviceEntityService.validateTemplateInputs(scopeInfo, serviceIdentifier, loadFromCache);
    return ResponseDTO.newResponse(validateTemplateInputsResponseDTO);
  }

  @Hidden
  @POST
  @Path("/to-unified/{serviceIdentifier}")
  @ApiOperation(value = "Convert an Ng Service to Unified Service", nickname = "toUnifiedService")
  public ResponseDTO<UnifiedServiceConverterResponse> convertToUnifiedStageService(
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @RequestBody(required = true) @Valid UnifiedServiceConverterRequestDTO requestDTO, @Context ScopeInfo scopeInfo)
      throws IOException {
    try {
      String mergedNgServiceYaml = ngToUnifiedServiceHelper.getMergedServiceYamlOrFromRequest(
          requestDTO, serviceIdentifier, accountId, orgIdentifier, projectIdentifier, scopeInfo, true);

      if (isEmpty(mergedNgServiceYaml)) {
        return ResponseDTO.newResponse(null);
      }

      // updating primary ref in yaml
      ArtifactsProcessedResponse artifactsProcessedResponse =
          ngToUnifiedServiceHelper.processArtifactsInYaml(null, mergedNgServiceYaml, Boolean.TRUE);
      mergedNgServiceYaml = artifactsProcessedResponse.getServiceYaml();

      // defaulting an unresolved primaryManifestRef to the only helm chart, when there is exactly one
      mergedNgServiceYaml = ngToUnifiedServiceHelper.processManifestsInYaml(mergedNgServiceYaml);

      // resolving artifact source template refs in the service yaml
      mergedNgServiceYaml = resolveArtifactSourceTemplateRefs(
          accountId, orgIdentifier, projectIdentifier, scopeInfo, mergedNgServiceYaml);

      // Fail fast with a clear, field-scoped message when a manifest boolean flag (optionalValuesYaml) holds a
      // non-boolean value, instead of letting it surface later as an opaque "Couldn't convert object to Yaml" error
      // while the manifest outcomes are serialized.
      ngToUnifiedServiceHelper.validateServiceParameters(mergedNgServiceYaml);

      // Build outcomes and extract overrides
      NgToUnifiedServiceHelper.NgOutcomesWithOverrides outcomesWithOverrides =
          ngToUnifiedServiceHelper.buildNgOutcomes(mergedNgServiceYaml, requestDTO, accountId, orgIdentifier,
              projectIdentifier, serviceIdentifier, true, scopeInfo);

      // NEW FRAMEWORK: Try template path first, falls back to POJO path automatically
      return ngToUnifiedServiceHelper.buildUnifiedServiceResponseWithTemplate(mergedNgServiceYaml,
          outcomesWithOverrides.getNgOutcomes(), outcomesWithOverrides.getMergedOverrideV2Configs());
    } catch (Exception e) {
      return ngToUnifiedServiceHelper.buildUnifiedServiceErrorResponse(
          e, serviceIdentifier, orgIdentifier, projectIdentifier);
    }
  }

  @Hidden
  @POST
  @Path("/to-unified-service-type/{serviceIdentifier}")
  @ApiOperation(value = "Resolve the unified swimlane type of an Ng Service", nickname = "toUnifiedServiceType")
  @Operation(operationId = "toUnifiedServiceType",
      summary = "Resolves only the unified swimlane type (e.g. kubernetes, helm) of a service",
      description =
          "Reads the static, immutable service definition type straight from the service's DB metadata (no YAML "
          + "parsing, no outcome building, no Git fetch), so it works for inline and remote services alike and even "
          + "when the service still has unresolved runtime inputs.")
  public ResponseDTO<UnifiedServiceTypeResponse>
  convertToUnifiedStageServiceType(@Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
                                       "serviceIdentifier") @ResourceIdentifier String serviceIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo) {
    try {
      // The unified swimlane type is a static, immutable field persisted on the service's single (branch-agnostic) DB
      // metadata record. Reading it directly from Mongo needs neither the service's runtime inputs nor a Git fetch, so
      // this resolves the type for inline and remote services alike, even when the service still carries unresolved
      // runtime inputs (a full unified conversion would fail in that case).
      Optional<ServiceEntity> serviceEntityOpt = serviceEntityService.getMetadata(scopeInfo, serviceIdentifier, false);

      if (serviceEntityOpt.isEmpty()) {
        return ResponseDTO.newResponse(null);
      }

      ServiceType serviceType = ngToUnifiedServiceHelper.resolveUnifiedServiceType(serviceEntityOpt.get().getType());
      return ResponseDTO.newResponse(UnifiedServiceTypeResponse.builder()
                                         .serviceType(serviceType == null ? null : serviceType.getDisplayName())
                                         .build());
    } catch (Exception e) {
      return ngToUnifiedServiceHelper.buildUnifiedServiceTypeErrorResponse(
          e, serviceIdentifier, orgIdentifier, projectIdentifier);
    }
  }

  private String resolveArtifactSourceTemplateRefs(String accountId, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, String mergedNgServiceYaml) {
    mergedNgServiceYaml = serviceEntityService.resolveArtifactSourceTemplateRefs(scopeInfo, mergedNgServiceYaml);
    return mergedNgServiceYaml;
  }

  @Hidden
  @POST
  @Path("/fetch-processed-ng-entity")
  @ApiOperation(value = "Process NG Entity and return merged YAMLs", nickname = "processNgEntity")
  @Operation(operationId = "processNgEntity", summary = "Process NG Entity and return merged YAMLs",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns NG Service Properties with merged YAMLs and outcomes")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<NgServicePropertiesResponse>
  fetchProcessedNGEntities(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @RequestBody(required = true, description = "NG Entity Fetch Request containing service and override details")
      @Valid NGEntityFetchRequest requestDTO, @Context ScopeInfo scopeInfo) throws IOException {
    String serviceRef = null;
    try {
      serviceRef = ngToUnifiedServiceHelper.validateAndGetServiceIdentifier(requestDTO);
      ServiceEntity serviceEntity = ngToUnifiedServiceHelper.fetchServiceEntity(
          accountId, orgIdentifier, projectIdentifier, serviceRef, true, scopeInfo);

      String mergedNgServiceYaml =
          ngToUnifiedServiceHelper.getMergedNgServiceYaml(requestDTO, serviceEntity, true, scopeInfo);
      NGServiceConfig ngServiceConfig = YamlUtils.read(mergedNgServiceYaml, NGServiceConfig.class);
      ServiceStepOutcome serviceStepOutcome =
          ngToUnifiedServiceHelper.buildServiceStepOutcome(serviceEntity, ngServiceConfig, accountId, true, scopeInfo);

      Map<String, String> overrideAndEnvironmentMap = ngToUnifiedServiceHelper.getMergedOverrideYaml(
          requestDTO, accountId, orgIdentifier, projectIdentifier, serviceRef, true, scopeInfo);
      NGServiceEntityMetadata ngServiceEntityMetadata =
          ngToUnifiedServiceHelper.buildServiceEntityMetadata(serviceEntity);

      return ngToUnifiedServiceHelper.buildNgServicePropertiesResponse(
          mergedNgServiceYaml, overrideAndEnvironmentMap, ngServiceEntityMetadata, serviceStepOutcome);
    } catch (Exception e) {
      return ngToUnifiedServiceHelper.buildNgServicePropertiesErrorResponse(
          e, serviceRef, orgIdentifier, projectIdentifier);
    }
  }

  private List<ServiceResponse> filterByScopedInfrastructures(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, List<ServiceResponse> serviceResponses,
      Map<String, List<String>> envRefInfraRefsMapping) {
    if (CollectionUtils.isEmpty(serviceResponses)) {
      return serviceResponses;
    }
    List<String> currentServiceRefs = serviceResponses.stream()
                                          .map(serviceResponse -> serviceResponse.getService().getIdentifier())
                                          .collect(toList());
    List<String> allowedServiceRefs = infrastructureEntityService.filterServicesByScopedInfrastructures(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, currentServiceRefs, envRefInfraRefsMapping);
    return serviceResponses.stream()
        .filter(serviceResponse -> allowedServiceRefs.contains(serviceResponse.getService().getIdentifier()))
        .collect(toList());
  }

  private void throwExceptionForNoRequestDTO(List<ServiceRequestDTO> dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(ServiceRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  @GET
  @Path("kustomize/command-flags")
  @ApiOperation(value = "Get Command flags for kustomize", nickname = "kustomizeCmdFlags")
  @Operation(operationId = "kustomizeCmdFlags", summary = "Retrieving the list of Kustomize Command Flags",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Kustomize Command Flags")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<Set<KustomizeCommandFlagType>>
  getKustomizeCommandFlags() {
    return ResponseDTO.newResponse(new HashSet<>(Arrays.asList(KustomizeCommandFlagType.values())));
  }

  boolean hasRequiredPermissionForAllServices(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceRBACPermission) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, null), serviceRBACPermission);
  }

  /**
   * Returns the subset of the requested service refs the principal is allowed to view.
   *
   * <p>A scope level check ({@code resourceIdentifier == null}) only passes for principals holding the permission on
   * every service in the scope, so it cannot be used as the gate for a request that names specific services. When the
   * principal does not hold the scope level permission, each ref is evaluated individually and the non permitted ones
   * are dropped. Each check is pinned to the scope encoded in the ref itself, so account and org level services are not
   * evaluated against the scope of the request.
   */
  List<String> getServiceRefsWithViewPermission(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceRefs) {
    if (isEmpty(serviceRefs)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
          Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION, UNAUTHORIZED_TO_VIEW_SERVICES);
      return serviceRefs;
    }

    if (hasRequiredPermissionForAllServices(accountId, orgIdentifier, projectIdentifier, SERVICE_VIEW_PERMISSION)) {
      return serviceRefs;
    }
    List<PermissionCheckDTO> permissionChecks =
        serviceRefs.stream()
            .map(serviceRef
                -> IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier))
            .map(identifierRef
                -> PermissionCheckDTO.builder()
                       .permission(SERVICE_VIEW_PERMISSION)
                       .resourceType(SERVICE)
                       .resourceIdentifier(identifierRef.getIdentifier())
                       .resourceScope(ResourceScope.of(identifierRef.getAccountIdentifier(),
                           identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()))
                       .build())
            .collect(toList());

    // throws only when none of the requested services are viewable
    Set<String> permittedRefKeys =
        accessControlClient.checkForAccessOrThrow(permissionChecks, UNAUTHORIZED_TO_VIEW_SERVICES)
            .getAccessControlList()
            .stream()
            .filter(AccessControlDTO::isPermitted)
            .map(ServiceResourceV2::getScopedRefKey)
            .collect(Collectors.toSet());

    return serviceRefs.stream()
        .filter(serviceRef
            -> permittedRefKeys.contains(getScopedRefKey(
                IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier))))
        .collect(toList());
  }

  private static String getScopedRefKey(AccessControlDTO accessControlDTO) {
    ResourceScope resourceScope = accessControlDTO.getResourceScope();
    if (resourceScope == null) {
      return getScopedRefKey(null, null, null, accessControlDTO.getResourceIdentifier());
    }
    return getScopedRefKey(resourceScope.getAccountIdentifier(), resourceScope.getOrgIdentifier(),
        resourceScope.getProjectIdentifier(), accessControlDTO.getResourceIdentifier());
  }

  private static String getScopedRefKey(IdentifierRef identifierRef) {
    return getScopedRefKey(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  private static String getScopedRefKey(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return String.join("/", blankToEmpty(accountIdentifier), blankToEmpty(orgIdentifier),
        blankToEmpty(projectIdentifier), blankToEmpty(identifier));
  }

  private static String blankToEmpty(String value) {
    return isBlank(value) ? "" : value;
  }

  @GET
  @Path("/list-repo")
  @Hidden
  @ApiOperation(value = "Gets all repo list", nickname = "getRepositoryList")
  @Operation(operationId = "getRepositoryList", summary = "Gets the list of all repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all Services")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<RepoListResponseDTO>
  listRepos(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify true if all accessible Services are to be included") @QueryParam(
          "includeAllServicesAccessibleAtScope") boolean includeAllServicesAccessibleAtScope,
      @Context ScopeInfo scopeInfo) {
    if (featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_ENTITY_CRUD_RBAC)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION);
    }
    RepoListResponseDTO repoListResponseDTO;
    repoListResponseDTO = serviceEntityService.getListOfRepos(scopeInfo, includeAllServicesAccessibleAtScope);
    return ResponseDTO.newResponse(repoListResponseDTO);
  }

  @GET
  @Path("/remote-services-metadata")
  @ApiOperation(value = "List remote services grouped by repository for a given accountId",
      nickname = "getRemoteServicesMetadata")
  @Operation(operationId = "getRemoteServicesMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote services in an account along with "
          + "service metadata. Optionally filter by repoName.",
      summary = "List remote services grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the service file paths in each repo")
      })
  @InternalApi
  @Hidden
  public ResponseDTO<RemoteServicesResponseDTO>
  getRemoteServicesMetadata(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote services only for the given repoName.") @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo) {
    long startMs = System.currentTimeMillis();
    log.info("[REMOTE_SERVICE_METADATA] start account={} org={} project={} repoNameFilter={} page={} size={}",
        accountIdentifier, orgIdentifier, projectIdentifier, repoName, page, size);
    try {
      ServiceRemoteRepoListResponse serviceResponse = serviceEntityService.getRemoteRepoListForAGivenScope(
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, scopeInfo, page, size);
      List<ServiceRemoteRepoInfo> serviceRepos =
          serviceResponse.getRepositories() == null ? Collections.emptyList() : serviceResponse.getRepositories();
      List<RemoteServicesDTO> resourceRepos = serviceRepos.stream()
                                                  .map(info
                                                      -> RemoteServicesDTO.builder()
                                                             .repoName(info.getRepoName())
                                                             .repoURL(info.getRepoURL())
                                                             .count(info.getCount())
                                                             .filePathsByOwningScope(info.getFilePathsByOwningScope())
                                                             .connectorRefs(info.getConnectorRefs())
                                                             .build())
                                                  .collect(toList());
      long totalServices = serviceRepos.stream().mapToLong(ServiceRemoteRepoInfo::getCount).sum();
      log.info("[REMOTE_SERVICE_METADATA] done account={} org={} project={} repoNameFilter={} totalRepos={} "
              + "pageRepos={} totalServices={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, serviceResponse.getTotalRepos(),
          resourceRepos.size(), totalServices, System.currentTimeMillis() - startMs);
      return ResponseDTO.newResponse(RemoteServicesResponseDTO.builder()
                                         .totalServices(totalServices)
                                         .totalRepos(serviceResponse.getTotalRepos())
                                         .repositories(resourceRepos)
                                         .build());
    } catch (Exception e) {
      log.error("[REMOTE_SERVICE_METADATA] failure account={} org={} project={} repoNameFilter={} latencyMs={}",
          accountIdentifier, orgIdentifier, projectIdentifier, repoName, System.currentTimeMillis() - startMs, e);
      throw e;
    }
  }

  @POST
  @Path("/move-config/{serviceIdentifier}")
  @ApiOperation(value = "Move Service YAML from inline to remote", nickname = "moveServiceConfigs")
  @Operation(operationId = "moveServiceConfigs", summary = "Move Service YAML from inline to remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Fetches Service YAML from Harness DB and creates a remote entity")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<ServiceMoveConfigResponse>
  moveConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @BeanParam ServiceMoveConfigRequestDTO serviceRequestDTO, @Context ScopeInfo scopeInfo) {
    // check for service update permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceIdentifier), SERVICE_UPDATE_PERMISSION);

    ServiceMoveConfigOperationDTO moveConfigOperationDTO =
        ServiceMoveConfigOperationDTO.builder()
            .repoName(serviceRequestDTO.getRepoName())
            .branch(serviceRequestDTO.getBranch())
            .moveConfigOperationType(
                MoveConfigOperationType.getMoveConfigType(serviceRequestDTO.getMoveConfigOperationType()))
            .connectorRef(serviceRequestDTO.getConnectorRef())
            .isHarnessCodeRepo(serviceRequestDTO.getIsHarnessCodeRepo())
            .baseBranch(serviceRequestDTO.getBaseBranch())
            .commitMessage(serviceRequestDTO.getCommitMsg())
            .isNewBranch(serviceRequestDTO.getIsNewBranch())
            .filePath(serviceRequestDTO.getFilePath())
            .build();

    ServiceMoveConfigResponse serviceMoveConfigResponse;
    serviceMoveConfigResponse =
        serviceEntityService.moveServiceStoreTypeConfig(scopeInfo, serviceIdentifier, moveConfigOperationDTO);
    return ResponseDTO.newResponse(serviceMoveConfigResponse);
  }

  @PUT
  @Path("/{serviceIdentifier}/update-git-metadata")
  @ApiOperation(value = "Update git-metadata in remote service Entity", nickname = "updateServiceGitDetails")
  @Operation(operationId = "updateServiceGitDetails",
      description = "Update git-metadata in remote service and returns the identifier of updated service",
      summary = "Update git-metadata in remote service Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns identifier of updated Service")
      })
  public ResponseDTO<ServiceGitUpdateResponseDTO>
  updateGitMetadataForService(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = SERVICE_PARAM_MESSAGE) @PathParam(
          SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @BeanParam GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceIdentifier), SERVICE_UPDATE_PERMISSION);

    String serviceAfterGitMetadataUpdate;
    serviceAfterGitMetadataUpdate = serviceEntityService.updateGitMetadata(scopeInfo, serviceIdentifier,
        ServiceGitMetadataUpdateParams.builder()
            .connectorRef(gitMetadataUpdateRequestInfo.getConnectorRef())
            .filePath(gitMetadataUpdateRequestInfo.getFilePath())
            .repoName(gitMetadataUpdateRequestInfo.getRepoName())
            .build());
    return ResponseDTO.newResponse(
        ServiceGitUpdateResponseDTO.builder().identifier(serviceAfterGitMetadataUpdate).build());
  }

  @POST
  @Path("/import")
  @ApiOperation(value = "Get Service YAML from Git Repository", nickname = "importService")
  @Operation(operationId = "importService", summary = "Get Service YAML from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Service YAML from Git Repository and saves a record for it in Harness")
      })
  public ResponseDTO<ServiceImportResponseDTO>
  importServiceFromGit(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                           description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(SERVICE_IDENTIFIER_KEY) @Parameter(
          description = SERVICE_PARAM_MESSAGE) @ResourceIdentifier String serviceIdentifier,
      @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        orgIdentifier, projectIdentifier, accountIdentifier);
    ServiceGovernanceDataResponse serviceGovernanceDataResponse;
    serviceGovernanceDataResponse = serviceEntityService.importServiceFromRemote(scopeInfo, serviceIdentifier,
        GitImportToServiceImportOperationMapper.serviceImportOperationDTO(gitImportInfoDTO));
    return ResponseDTO.newResponse(ServiceImportResponseDTO.builder()
                                       .identifier(serviceGovernanceDataResponse.getService().getIdentifier())
                                       .governanceMetadata(serviceGovernanceDataResponse.getGovernanceMetadata())
                                       .build());
  }

  @POST
  @Path("/validate-yaml")
  @Hidden
  @ApiOperation(value = "This api return the validation result of service yaml", nickname = "validateServiceYaml")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<YamlValidationListAPIResponse> validateServiceYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody) {
    YamlValidationRequestDTO yamlValidationRequestDTO = getYamlValidationRequestDTO(yamlValidationRequestBody);
    List<YamlValidationResponseDTO> yamlValidationResponseDTOS =
        serviceEntityService.validateServiceYaml(accountIdentifier, yamlValidationRequestDTO);
    List<YamlValidationAPIResponse> yamlValidationAPIResponses =
        yamlValidationResponseDTOS.stream()
            .map(YamlValidationAPIResponse::toYamlValidationAPIResponse)
            .collect(toList());
    return ResponseDTO.newResponse(
        YamlValidationListAPIResponse.builder().yamlValidationAPIResponseList(yamlValidationAPIResponses).build());
  }

  @POST
  @Path("/clone")
  @ApiOperation(value = "Clone a Service", nickname = "cloneServiceV2")
  @Operation(operationId = "cloneServiceV2", summary = "Clone a Service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the cloned Service")
      })
  @Timed
  @Hidden
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ServiceResponse>
  cloneService(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @RequestBody(required = true,
          description = "Details of the Service to be cloned") @Valid ServiceCloneRequestDTO serviceCloneRequestDTO) {
    SourceServiceConfig sourceServiceConfig = serviceCloneRequestDTO.getSourceConfig();
    DestinationServiceConfig destinationServiceConfig = serviceCloneRequestDTO.getDestinationConfig();
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, sourceServiceConfig.getOrgIdentifier(), sourceServiceConfig.getProjectIdentifier());

    ServiceGovernanceDataResponse clonedServiceMapper =
        serviceCloneHelper.cloneService(accountId, sourceServiceConfig, destinationServiceConfig, scopeInfo);

    if (clonedServiceMapper.getService().getParentUniqueId() != scopeInfo.getUniqueId()) {
      scopeInfo = ScopeInfo.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(destinationServiceConfig.getOrgIdentifier())
                      .projectIdentifier(destinationServiceConfig.getProjectIdentifier())
                      .uniqueId(clonedServiceMapper.getService().getParentUniqueId())
                      .build();
    }

    return ResponseDTO.newResponse(ServiceElementMapper.toResponseWrapper(
        clonedServiceMapper.getService(), clonedServiceMapper.getGovernanceMetadata(), scopeInfo));
  }

  @POST
  @Path("/force-import")
  @Hidden
  @ApiOperation(value = "Force Import a Service", nickname = "forceImportServiceV2")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<ForceImportServiceResponse> forceImportService(
      @NotNull @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      ForceImportServiceRequestDTO requestDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    ForceImportServiceYamlOperationDTO operationDTO = ForceImportServiceYamlOperationDTO.builder()
                                                          .branch(requestDTO.getBranch())
                                                          .repoName(requestDTO.getRepoName())
                                                          .connectorRef(requestDTO.getConnectorRef())
                                                          .filePath(requestDTO.getFilePath())
                                                          .isHarnessCodeRepo(requestDTO.getIsHarnessCodeRepo())
                                                          .identifier(requestDTO.getIdentifier())
                                                          .orgIdentifier(requestDTO.getOrgIdentifier())
                                                          .projectIdentifier(requestDTO.getProjectIdentifier())
                                                          .build();

    ForceImportServiceResponse response = serviceEntityService.forceImportService(accountIdentifier, operationDTO);
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

  @GET
  @Path("/plugin-info")
  @Timed
  @ApiOperation(value = "Gets PluginInfo list", nickname = "getPluginInfo")
  @Operation(operationId = "getPluginInfo", summary = "Get Plugin Info at Service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Runtime and Serverless Version")
      })
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PluginInfoResponseDto>
  getPluginInfoList() {
    return ResponseDTO.newResponse(serviceEntityService.getPluginInfoList());
  }

  private ServiceEntity convertDTOToEntity(String accountId, ServiceRequestDTO serviceRequestDTO) throws Exception {
    serviceSchemaHelper.validateSchema(accountId, serviceRequestDTO.getYaml());

    ServiceEntity serviceEntity;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());
    serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO, scopeInfo);
    if (isEmpty(serviceRequestDTO.getYaml())) {
      serviceSchemaHelper.validateSchema(accountId, serviceEntity.getYaml(scopeInfo));
    }
    return serviceEntity;
  }

  /**
   * Manual validation for ServiceRequestDTO to support partial success in batch operations.
   * This replaces @Valid annotation validation which would fail the entire request.
   * Implements the same validation logic as @EntityIdentifier and @EntityName annotations.
   */
  private void validateServiceRequestDTO(ServiceRequestDTO serviceRequestDTO) {
    List<String> validationErrors = new ArrayList<>();

    // Validate identifier - matches @EntityIdentifier(allowBlank=false, allowScoped=false, maxLength=128)
    String identifier = serviceRequestDTO.getIdentifier();
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
        log.warn("Service identifier [{}] contains harness reserved keyword", identifier);
      }
    }

    // Validate name if present - matches @EntityName (name is optional, validates only if provided)
    String name = serviceRequestDTO.getName();
    if (isNotEmpty(name) && !EntityNameValidator.isValid(name)) {
      validationErrors.add("name: can only contain alphanumeric characters, hyphens, underscores and spaces");
    }

    // If there are validation errors, throw exception with all errors combined
    if (!validationErrors.isEmpty()) {
      throw new InvalidRequestException(String.join("; ", validationErrors));
    }
  }
}
