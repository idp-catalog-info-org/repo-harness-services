/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.overview.dto.LatestServiceDeploymentResponseDTO;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;

import software.wings.beans.ServiceKeys;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@NextGenManagerAuth
@Api("/services")
@Path("/services")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@ScopeInfoResolutionApi
public class ServiceResource {
  private final ServiceEntityService serviceEntityService;
  private final ServiceEntityManagementService serviceEntityManagementService;
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final ScopeInfoService scopeInfoService;
  private final AccessControlClient accessControlClient;

  private static final int MAX_LIMIT = 1000;

  @GET
  @Path("{service-id}/latest-deployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get latest service deployment details per environment for a particular service.")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<LatestServiceDeploymentResponseDTO> getLatestServiceDeployments(
      @NotNull @PathParam("service-id") String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getLatestServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("{serviceIdentifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets a Service by identifier", nickname = "getService")
  @Deprecated
  public ResponseDTO<ServiceResponseDTO> get(@PathParam("serviceIdentifier") String serviceIdentifier,
      @QueryParam("accountId") String accountId, @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceIdentifier), SERVICE_VIEW_PERMISSION);
    Optional<ServiceEntity> serviceEntity;
    if (scopeInfo != null) {
      serviceEntity = serviceEntityService.get(scopeInfo, serviceIdentifier, deleted);
    } else {
      serviceEntity = serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, deleted);
    }
    if (!serviceEntity.isPresent()) {
      throw new NotFoundException(String.format("Service with identifier [%s] in project [%s], org [%s] not found",
          serviceIdentifier, projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(serviceEntity
                                       .map(entity
                                           -> scopeInfo != null ? ServiceElementMapper.writeDTO(entity, scopeInfo)
                                                                : ServiceElementMapper.writeDTO(entity))
                                       .orElse(null));
  }

  @POST
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create a Service", nickname = "createService")
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<ServiceResponseDTO> create(
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    ServiceGovernanceDataResponse createdServiceMapper;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier());
    createdServiceMapper = serviceEntityService.create(serviceEntity, scopeInfo);
    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(createdServiceMapper.getService(), scopeInfo));
  }

  @POST
  @Path("/batch")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create Services", nickname = "createServices")
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<PageResponse<ServiceResponseDTO>> createServices(@QueryParam("accountId") String accountId,
      @NotNull @Valid @Size(max = MAX_LIMIT) List<ServiceRequestDTO> serviceRequestDTOs) {
    for (ServiceRequestDTO serviceRequestDTO : serviceRequestDTOs) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
          Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    }
    List<ServiceEntity> serviceEntities =
        serviceRequestDTOs.stream()
            .map(serviceRequestDTO -> {
              ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
              return ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
            })
            .collect(Collectors.toList());
    Page<ServiceEntity> createdServices = serviceEntityService.bulkCreate(accountId, serviceEntities);

    Set<String> parentUniqueIds = new HashSet<>();
    serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
    Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(accountId, parentUniqueIds);

    return ResponseDTO.newResponse(getNGPageResponse(createdServices.map(service -> {
      ScopeInfo scopeInfo = scopeInfos.get(service.getParentUniqueId()).get();
      return ServiceElementMapper.writeDTO(service, scopeInfo);
    })));
  }

  @DELETE
  @Path("{serviceIdentifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Delete a service by identifier", nickname = "deleteService")
  @Deprecated
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @PathParam("serviceIdentifier") String serviceIdentifier, @QueryParam("accountId") String accountId,
      @QueryParam("orgIdentifier") String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, serviceIdentifier), SERVICE_DELETE_PERMISSION);
    return ResponseDTO.newResponse(serviceEntityManagementService.deleteService(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, ifMatch, false, scopeInfo));
  }

  @PUT
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Update a service by identifier", nickname = "updateService")
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<ServiceResponseDTO> update(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceGovernanceDataResponse updatedServiceMapper;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());

    updatedServiceMapper = serviceEntityService.update(requestService, scopeInfo);
    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(updatedServiceMapper.getService(), scopeInfo));
  }

  @PUT
  @Path("upsert")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Upsert a service by identifier", nickname = "upsertService")
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<ServiceResponseDTO> upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid ServiceRequestDTO serviceRequestDTO) {
    ServiceResourceApiUtils.validateServiceScope(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier());
    ServiceEntity requestService = ServiceElementMapper.toServiceEntity(accountId, serviceRequestDTO, scopeInfo);
    requestService.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ServiceEntity upsertedService;

    upsertedService = serviceEntityService.upsert(requestService, UpsertOptions.DEFAULT, scopeInfo).getService();

    return ResponseDTO.newResponse(ServiceElementMapper.writeDTO(upsertedService, scopeInfo));
  }

  @GET
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Service list for a project", nickname = "getServiceListForProject")
  @Deprecated
  public ResponseDTO<PageResponse<ServiceResponseDTO>> listServicesForProject(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("100") @Max(MAX_LIMIT) int size, @QueryParam("accountId") String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers, @QueryParam("sort") List<String> sort,
      @Context ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    Criteria criteria;

    if (scopeInfo != null) {
      criteria = CoreCriteriaUtils.createCriteriaForGetList(scopeInfo, false);
    } else {
      criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    }
    Pageable pageRequest;
    if (isNotEmpty(serviceIdentifiers)) {
      criteria.and(ServiceEntityKeys.identifier).in(serviceIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<ServiceResponseDTO> serviceList = serviceEntityService.list(criteria, pageRequest).map(serviceEntity -> {
      return ServiceElementMapper.writeDTO(serviceEntity, scopeInfo);
    });
    return ResponseDTO.newResponse(getNGPageResponse(serviceList));
  }

  @GET
  @Path("helmCmdFlags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get Command flags based on Deployment Type", nickname = "helmCmdFlags")
  @ScopeInfoResolutionExemptedApi
  @Deprecated
  public ResponseDTO<Set<HelmCommandFlagType>> getHelmCommandFlags(
      @QueryParam("serviceSpecType") @NotNull String serviceSpecType, @QueryParam("version") HelmVersion version,
      @QueryParam("storeType") String storeType) {
    Set<HelmCommandFlagType> helmCmdFlags = new HashSet<>();

    for (HelmCommandFlagType flagType : HelmCommandFlagType.values()) {
      if (containsOrNull(flagType.getServiceSpecTypes(), serviceSpecType)
          && containsOrNull(flagType.getSubCommandType().getHelmVersions(), version)
          && containsOrNull(flagType.getStoreTypes(), storeType)) {
        helmCmdFlags.add(flagType);
      }
    }

    return ResponseDTO.newResponse(helmCmdFlags);
  }

  private <T> boolean containsOrNull(Set<T> list, T value) {
    if (Objects.isNull(value)) {
      return true;
    }
    return list.contains(value);
  }
}
