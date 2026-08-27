/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.beans.FeatureName.CDS_RECONCILE_MULTIPLE_MANIFESTS_AS_PRIMARY_MANIFEST;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.service.entity.NGServiceEntityMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.helpers.ServiceFilterHelper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.services.impl.ServiceRbacHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.spec.server.ng.v1.model.GitEntityFindInfoDTO;
import io.harness.spec.server.ng.v1.model.ManifestsResponseDTO;
import io.harness.spec.server.ng.v1.model.ServiceCreateRequest;
import io.harness.spec.server.ng.v1.model.ServiceResponse;
import io.harness.spec.server.ng.v1.model.ServiceUpdateRequest;
import io.harness.utils.ApiUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import software.wings.beans.ServiceKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor
public abstract class AbstractServicesApiImpl {
  @Inject private final ServiceEntityService serviceEntityService;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final ServiceEntityManagementService serviceEntityManagementService;
  @Inject private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private final ServiceResourceApiUtils serviceResourceApiUtils;
  @Inject private final ServiceEntityYamlSchemaHelper serviceSchemaHelper;
  @Inject private final ServiceRbacHelper serviceRbacHelper;
  @Inject private final ScopeInfoService scopeInfoService;
  @Inject private final NGFeatureFlagHelperService featureFlagHelperService;

  public Response createServiceEntity(ServiceCreateRequest serviceRequest, String org, String project, String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    setYamlVersionInRequest(serviceRequest);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);

    if (HarnessYamlVersion.V0.equals(getHarnessVersion(serviceRequest.getHarnessVersion()))) {
      serviceSchemaHelper.validateSchema(account, serviceRequest.getYaml());
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    ServiceEntity serviceEntity =
        serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account, scopeInfo);
    if (isEmpty(serviceRequest.getYaml())
        && HarnessYamlVersion.V0.equals(getHarnessVersion(serviceRequest.getHarnessVersion()))) {
      serviceSchemaHelper.validateSchema(account, serviceEntity.getYaml(scopeInfo));
    }
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, serviceEntity.getAccountId());
    ServiceGovernanceDataResponse createdServiceMapper = serviceEntityService.create(serviceEntity, scopeInfo);
    ServiceResponse serviceResponse =
        serviceResourceApiUtils.mapToServiceResponse(createdServiceMapper.getService(), scopeInfo);
    return Response.status(Response.Status.CREATED).entity(serviceResponse).build();
  }

  public Response deleteServiceEntity(String org, String project, String service, String account, boolean forceDelete) {
    Optional<ServiceEntity> serviceEntityOptional;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    serviceEntityOptional = serviceEntityService.get(scopeInfo, service, false);
    if (serviceEntityOptional.isEmpty()) {
      throw new NotFoundException(format("Service with identifier [%s] not found", service));
    }
    boolean deleted =
        serviceEntityManagementService.deleteService(account, org, project, service, "ifMatch", forceDelete, scopeInfo);
    if (!deleted) {
      throw new InvalidRequestException(format("Service with identifier [%s] could not be deleted", service));
    }
    return Response.ok()
        .entity(serviceResourceApiUtils.mapToServiceResponse(serviceEntityOptional.get(), scopeInfo))
        .build();
  }

  public Response getServiceEntity(String org, String project, String service, String account) {
    Optional<ServiceEntity> serviceEntityOp;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    serviceEntityOp = serviceEntityService.get(scopeInfo, service, false);
    if (serviceEntityOp.isEmpty()) {
      throw new NotFoundException(ServiceElementMapper.getServiceNotFoundError(org, project, service));
    }

    ServiceEntity serviceEntity = serviceEntityOp.get();
    if (isEmpty(serviceEntity.getYaml(serviceEntity.getHarnessVersion()))) {
      NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity, scopeInfo);
      serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
    }
    return Response.ok().entity(serviceResourceApiUtils.mapToServiceResponse(serviceEntity, scopeInfo)).build();
  }

  public Response getServicesList(String org, String project, Integer page, Integer limit, String searchTerm,
      List<String> services, String sort, Boolean isAccessList, String type, Boolean gitOpsEnabled, String account,
      String order) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to list services");
    ServiceDefinitionType optionalType = ServiceDefinitionType.getServiceDefinitionType(type);
    Criteria criteria;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);
    Map<ScopeLevel, String> uniqueIdsMap = scopeInfoService.getUniqueIdsIncludingParentScopes(scopeInfo);
    criteria = ServiceFilterHelper.createCriteriaForGetList(
        scopeInfo, false, searchTerm, optionalType, gitOpsEnabled, false, null, uniqueIdsMap);
    Pageable pageRequest;
    if (isNotEmpty(services)) {
      criteria.and(ServiceEntityKeys.identifier).in(services);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    } else {
      String sortQuery = serviceResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    if (Boolean.TRUE.equals(isAccessList)) {
      // Services this list response does not contain yaml  as client of this
      // implementation is supposed to only know about service metadata, example
      // use case is to list service dropdown to select at pipeline run
      List<ServiceResponse> filterserviceList = fetchServicesWithRuntimePermission(criteria);
      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, filterserviceList.size(), page, limit);
      return responseBuilderWithLinks.entity(filterserviceList).build();
    } else {
      Page<ServiceEntity> serviceEntities =
          serviceEntityService.listRBACAware(account, org, project, criteria, pageRequest, services);
      Set<String> parentUniqueIds = new HashSet<>();
      serviceEntities.forEach(entity -> parentUniqueIds.add(entity.getParentUniqueId()));
      Map<String, Optional<ScopeInfo>> scopeInfos = scopeInfoService.getScopeInfo(account, parentUniqueIds);

      serviceEntities.forEach(serviceEntity -> {
        if (isEmpty(serviceEntity.getYaml(serviceEntity.getHarnessVersion()))) {
          NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(
              serviceEntity, scopeInfos.get(serviceEntity.getParentUniqueId()).get());
          serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
        }
      });
      Page<ServiceResponse> serviceResponsePage = serviceEntities.map(serviceEntity -> {
        return serviceResourceApiUtils.mapToServiceResponse(
            serviceEntity, scopeInfos.get(serviceEntity.getParentUniqueId()).get());
      });

      List<ServiceResponse> serviceList = serviceResponsePage.getContent();

      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, serviceResponsePage.getTotalElements(), page, limit);

      return responseBuilderWithLinks.entity(serviceList).build();
    }
  }

  private List<ServiceResponse> fetchServicesWithRuntimePermission(Criteria criteria) {
    List<ServiceResponse> serviceList = serviceEntityService.listRunTimePermission(criteria, false)
                                            .stream()
                                            .map(serviceResourceApiUtils::mapToAccessListResponse)
                                            .collect(Collectors.toList());
    List<PermissionCheckDTO> permissionCheckDTOS =
        serviceList.stream()
            .map(serviceResourceApiUtils::serviceResponseToPermissionCheckDTO)
            .collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return filterByPermissionAndId(accessControlList, serviceList);
  }

  public Response updateServiceEntity(
      ServiceUpdateRequest serviceRequest, String org, String project, String service, String account) {
    throwExceptionForNoRequestDTO(serviceRequest);
    setYamlVersionInRequest(serviceRequest);
    if (!service.equals(serviceRequest.getIdentifier())) {
      throw new InvalidRequestException(
          format("Identifier passed in request body: [%s] does not match resource identifier: [%s]",
              serviceRequest.getIdentifier(), service));
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(NGResourceType.SERVICE, serviceRequest.getIdentifier()), SERVICE_UPDATE_PERMISSION);

    if (HarnessYamlVersion.V0.equals(getHarnessVersion(serviceRequest.getHarnessVersion()))) {
      serviceSchemaHelper.validateSchema(account, serviceRequest.getYaml());
    }

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    ServiceEntity requestService =
        serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account, scopeInfo);

    if (isEmpty(serviceRequest.getYaml())
        && HarnessYamlVersion.V0.equals(getHarnessVersion(requestService.getHarnessVersion()))) {
      serviceSchemaHelper.validateSchema(account, requestService.getYaml(scopeInfo));
    }

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, requestService.getAccountId());
    ServiceGovernanceDataResponse updateServiceMapper = serviceEntityService.update(requestService, scopeInfo);
    return Response.ok()
        .entity(serviceResourceApiUtils.mapToServiceResponse(updateServiceMapper.getService(), scopeInfo))
        .build();
  }

  private void throwExceptionForNoRequestDTO(ServiceCreateRequest dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void throwExceptionForNoRequestDTO(ServiceUpdateRequest dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private List<ServiceResponse> filterByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<ServiceResponse> serviceList) {
    List<ServiceResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      ServiceResponse serviceResponse = serviceList.get(i);
      if (accessControlDTO.isPermitted()
          && serviceResponse.getService().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(serviceResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  public Response getPrimaryManifestList(
      String service, String org, String project, GitEntityFindInfoDTO gitEntityFindInfoDTO, String harnessAccount) {
    if (gitEntityFindInfoDTO != null && isNotEmpty(gitEntityFindInfoDTO.getBranch())) {
      GitAwareContextHelper.populateGitDetails(
          GitEntityInfo.builder().branch(gitEntityFindInfoDTO.getBranch()).build());
    }

    Optional<ServiceEntity> serviceEntityOptional;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);

    serviceEntityOptional = serviceEntityService.get(scopeInfo, service, false);

    if (!serviceEntityOptional.isPresent()) {
      throw new NotFoundException(
          format("Service with identifier [%s] in project [%s], org [%s] not found", service, project, org));
    }
    if (isEmpty(serviceEntityOptional.get().getYaml(scopeInfo))) {
      throw new InvalidRequestException(
          format("Service %s is not configured with a Service definition. Service Yaml is empty", service));
    }
    ManifestsResponseDTO response;
    if (featureFlagHelperService.isEnabled(harnessAccount, CDS_RECONCILE_MULTIPLE_MANIFESTS_AS_PRIMARY_MANIFEST)) {
      response = serviceEntityService.getManifestIdentifiersWithInputs(
          serviceEntityOptional.get().getYaml(scopeInfo), service);
    } else {
      response = serviceEntityService.getManifestIdentifiers(serviceEntityOptional.get().getYaml(scopeInfo), service);
    }
    return Response.ok().entity(response).build();
  }

  private String getHarnessVersion(String harnessVersion) {
    return isBlank(harnessVersion) ? HarnessYamlVersion.V0 : harnessVersion;
  }

  private void setYamlVersionInRequest(ServiceCreateRequest serviceRequest) {
    String yamlVersion = HarnessYamlVersion.V0;
    if (isNotBlank(serviceRequest.getYaml())) {
      yamlVersion = NGYamlHelper.getVersion(serviceRequest.getYaml());
    }
    serviceRequest.setHarnessVersion(yamlVersion);
  }

  private void setYamlVersionInRequest(ServiceUpdateRequest serviceRequest) {
    String yamlVersion = HarnessYamlVersion.V0;
    if (isNotBlank(serviceRequest.getYaml())) {
      yamlVersion = NGYamlHelper.getVersion(serviceRequest.getYaml());
    }
    serviceRequest.setHarnessVersion(yamlVersion);
  }
}
