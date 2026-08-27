/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.beans.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO.EnvironmentFilterPropertiesDTOBuilder;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.helpers.EnvironmentResourceHelper;
import io.harness.ng.core.environment.mappers.EnvironmentOpenApiMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.spec.server.ng.v1.model.EnvironmentCreateRequest;
import io.harness.spec.server.ng.v1.model.EnvironmentResponse;
import io.harness.spec.server.ng.v1.model.EnvironmentUpdateRequest;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AbstractEnvironmentsApiImpl {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  private EnvironmentRbacHelper environmentRbacHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final ServiceResourceApiUtils serviceResourceApiUtils;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final ScopeInfoService scopeInfoService;

  public Response createEnvironmentEntity(
      EnvironmentCreateRequest environmentRequest, String org, String project, String harnessAccount) {
    throwExceptionForNoRequestDTO(environmentRequest);
    validateEnvironmentScope(org, project);
    setHarnessYamlVersionAndIdentifierInRequest(environmentRequest, null);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of(ENVIRONMENT, null,
            EnvironmentResourceHelper.getEnvironmentAttributesMap(environmentRequest.getType().toString())),
        ENVIRONMENT_CREATE_PERMISSION);

    if (HarnessYamlVersion.V0.equals(environmentRequest.getHarnessVersion())) {
      environmentEntityYamlSchemaHelper.validateSchema(harnessAccount, environmentRequest.getYaml());
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);

    Environment environmentEntity =
        EnvironmentOpenApiMapper.toEnvironmentEntity(harnessAccount, org, project, environmentRequest);
    if (isEmpty(environmentRequest.getYaml()) && HarnessYamlVersion.V0.equals(environmentRequest.getHarnessVersion())) {
      environmentEntityYamlSchemaHelper.validateSchema(harnessAccount, environmentEntity.getYaml(scopeInfo));
    }

    Environment createdEnvironment = environmentService.create(environmentEntity, scopeInfo).getEnvironment();

    // Not implementing override v2 routing here, because that support was only added
    // in environmentsV2 apis in case user already has api automation for environments
    // considering overrides as part of environment

    return Response.status(Response.Status.CREATED)
        .entity(EnvironmentOpenApiMapper.toResponse(createdEnvironment, scopeInfo))
        .build();
  }

  public Response getEnvironmentEntity(String org, String project, String environment, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Optional<Environment> optionalEnvironment = environmentService.get(scopeInfo, environment, false, false, false);
    if (optionalEnvironment.isPresent()) {
      Environment environmentEntity = optionalEnvironment.get();

      if (!Objects.equals(environmentEntity.getParentUniqueId(), scopeInfo.getUniqueId())) {
        scopeInfo = scopeInfoService.getScopeInfo(account, Set.of(environmentEntity.getParentUniqueId()))
                        .get(environmentEntity.getParentUniqueId())
                        .orElse(null);
      }

      if (EmptyPredicate.isEmpty(environmentEntity.getYaml(environmentEntity.getHarnessVersion()))) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentEntity, scopeInfo);
        environmentEntity.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    } else {
      throw new NotFoundException(
          format("Environment with identifier [%s] in project [%s], org [%s] not found", environment, project, org));
    }

    environmentRbacHelper.checkForAccessOrThrow(
        environmentRbacHelper.getEnvironmentAttributesMap(optionalEnvironment.get().getType().toString()),
        ResourceScope.of(account, org, project), environment, ENVIRONMENT_VIEW_PERMISSION);
    return Response.ok().entity(EnvironmentOpenApiMapper.toResponse(optionalEnvironment.get(), scopeInfo)).build();
  }

  public Response updateEnvironmentEntity(
      EnvironmentUpdateRequest environmentRequest, String org, String project, String environment, String account) {
    throwExceptionForNoRequestDTO(environmentRequest);
    validateEnvironmentScope(org, project);
    setHarnessYamlVersionAndIdentifierInRequest(environmentRequest, environment);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);
    Map<String, String> attributes =
        environmentRbacHelper.getEnvironmentAttributesMap(environmentRequest.getType().toString());
    environmentRbacHelper.checkForAccessOrThrow(attributes, ResourceScope.of(account, org, project),
        environmentRequest.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    if (HarnessYamlVersion.V0.equals(environmentRequest.getHarnessVersion())) {
      environmentEntityYamlSchemaHelper.validateSchema(account, environmentRequest.getYaml());
    }

    Environment requestedEnvironment =
        EnvironmentOpenApiMapper.toEnvironmentEntity(account, org, project, environmentRequest);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    if (isEmpty(environmentRequest.getYaml())
        && HarnessYamlVersion.V0.equals(requestedEnvironment.getHarnessVersion())) {
      environmentEntityYamlSchemaHelper.validateSchema(account, requestedEnvironment.getYaml(scopeInfo));
    }

    Environment updatedEnvironment = environmentService.update(requestedEnvironment, scopeInfo).getEnvironment();

    return Response.ok().entity(EnvironmentOpenApiMapper.toResponse(updatedEnvironment, scopeInfo)).build();
  }

  public Response deleteEnvironmentEntity(
      String org, String project, String environment, String harnessAccount, Boolean forceDelete) {
    Optional<Environment> environmentOp;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);

    environmentOp = environmentService.getMetadata(scopeInfo, environment, false);
    if (environmentOp.isEmpty()) {
      throw new NotFoundException(
          format("Environment with identifier [%s] in project [%s], org [%s] not found", environment, project, org));
    }
    Map<String, String> environmentAttributes = new HashMap<>();
    if (environmentOp.get().getType() != null) {
      environmentAttributes.put("type", environmentOp.get().getType().toString());
    }
    environmentRbacHelper.checkForAccessOrThrow(environmentAttributes, ResourceScope.of(harnessAccount, org, project),
        environment, ENVIRONMENT_DELETE_PERMISSION);
    environmentService.delete(scopeInfo, environment, null, forceDelete);
    return Response.status(204).build();
  }

  public Response getEnvironmentEntities(String org, String project, Integer page, Integer limit, String searchTerm,
      List<String> environments, String sort, Boolean isAccessList, String account, String order) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, null),
        ENVIRONMENT_VIEW_PERMISSION, UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);
    Criteria criteria;
    Pageable pageRequest;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    criteria = environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, null);

    if (isNotEmpty(environments)) {
      criteria.and(EnvironmentKeys.identifier).in(environments);
    }

    pageRequest = preparePageRequest(page, limit, sort, order);

    if (Boolean.TRUE.equals(isAccessList)) {
      List<EnvironmentResponse> envResponseList = environmentService.listAccess(criteria)
                                                      .stream()
                                                      .map(env -> EnvironmentOpenApiMapper.toResponse(env, scopeInfo))
                                                      .toList();
      List<EnvironmentResponse> filterEnvList = filterEnvBasedOnAccessPermission(envResponseList);
      ResponseBuilder responseBuilder = Response.ok();

      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, filterEnvList.size(), page, limit);
      return responseBuilderWithLinks.entity(filterEnvList).build();

    } else {
      Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
      environmentEntities.forEach(environment -> {
        if (EmptyPredicate.isEmpty(environment.getYaml(scopeInfo))) {
          // All env are from specified scope as per createCriteriaForGetList
          NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment, scopeInfo);
          environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
        }
      });

      Page<EnvironmentResponse> envResponsePage =
          environmentEntities.map(entity -> EnvironmentOpenApiMapper.toResponse(entity, scopeInfo));
      List<EnvironmentResponse> environmentList = envResponsePage.getContent();
      ResponseBuilder responseBuilder = Response.ok();
      ResponseBuilder responseBuilderWithLinks =
          ApiUtils.addLinksHeader(responseBuilder, envResponsePage.getTotalElements(), page, limit);

      return responseBuilderWithLinks.entity(environmentList).build();
    }
  }

  private List<EnvironmentResponse> filterEnvBasedOnAccessPermission(List<EnvironmentResponse> envResponseList) {
    List<PermissionCheckDTO> permissionCheckDTOS =
        envResponseList.stream().map(EnvironmentOpenApiMapper::toPermissionCheckDTO).toList();
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return filterByPermissionAndId(accessControlList, envResponseList);
  }

  private List<EnvironmentResponse> filterByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<EnvironmentResponse> environmentList) {
    List<EnvironmentResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      EnvironmentResponse environmentResponse = environmentList.get(i);
      if (accessControlDTO.isPermitted()
          && environmentResponse.getEnvironment().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(environmentResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  private Pageable preparePageRequest(Integer page, Integer limit, String sort, String order) {
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      String sortQuery = serviceResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    return pageRequest;
  }

  private void setHarnessYamlVersionAndIdentifierInRequest(EnvironmentCreateRequest requestBody, String envIdentifier) {
    requestBody.setHarnessVersion(NGYamlHelper.getVersion(requestBody.getYaml()));
    if (isNotEmpty(envIdentifier)) {
      requestBody.setIdentifier(envIdentifier);
    }
  }

  private void setHarnessYamlVersionAndIdentifierInRequest(EnvironmentUpdateRequest requestBody, String envIdentifier) {
    requestBody.setHarnessVersion(NGYamlHelper.getVersion(requestBody.getYaml()));
    if (isNotEmpty(envIdentifier)) {
      requestBody.setIdentifier(envIdentifier);
    }
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

  private void throwExceptionForNoRequestDTO(EnvironmentCreateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void throwExceptionForNoRequestDTO(EnvironmentUpdateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  protected Response searchEnvironmentEntitiesFiltered(String org, String project, Integer page, Integer limit,
      String searchTerm, List<String> environmentsIds, String sort, String order, List<String> environmentNames,
      String description, String filterIdentifier, Boolean includeAllAccessibleAtScope, String repoName,
      List<String> tags, String environmentType, String account) {
    Criteria criteria;
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    // Build filter properties from query parameters
    EnvironmentFilterPropertiesDTO filterProperties =
        buildFilterPropertiesFromParams(environmentsIds, environmentNames, description, tags, environmentType);

    criteria = environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, searchTerm, filterIdentifier,
        filterProperties, Boolean.TRUE.equals(includeAllAccessibleAtScope), repoName);

    Pageable pageRequest = preparePageRequest(page, limit, sort, order);

    // Get RBAC filtered environments (using listEnvironmentsV2 approach)
    Page<Environment> environmentPage = getRBACFilteredEnvironments(account, org, project, criteria, pageRequest);

    // Process environments to ensure YAML is populated
    environmentPage.forEach(environment -> {
      if (environment == null) {
        log.warn("Invalid environment found in the list. Skipping and continuing with other environments.");
        return;
      }
      if (EmptyPredicate.isEmpty(environment.getYaml(scopeInfo))) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment, scopeInfo);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    });

    // Filter out null environments
    List<Environment> filteredContent =
        environmentPage.getContent().stream().filter(Objects::nonNull).collect(Collectors.toList());

    // Get scope info map for multi-scope support
    Set<String> uniqueIds = filteredContent.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(account, uniqueIds);

    // Create filtered page with environment responses
    Page<EnvironmentResponse> envResponsePage =
        new PageImpl<>(filteredContent, environmentPage.getPageable(), environmentPage.getTotalElements()).map(env -> {
          Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.getOrDefault(env.getParentUniqueId(), Optional.empty());
          return EnvironmentOpenApiMapper.toResponse(env, scopeInfoOpt.orElse(null));
        });

    List<EnvironmentResponse> environmentList = envResponsePage.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, envResponsePage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(environmentList).build();
  }

  private EnvironmentFilterPropertiesDTO buildFilterPropertiesFromParams(List<String> environmentIds,
      List<String> environmentNames, String description, List<String> tags, String environmentType) {
    EnvironmentFilterPropertiesDTOBuilder builder = EnvironmentFilterPropertiesDTO.builder();

    if (isNotEmpty(environmentIds)) {
      builder.environmentIdentifiers(environmentIds);
    }

    if (isNotEmpty(environmentNames)) {
      builder.environmentNames(environmentNames);
    }

    if (isNotEmpty(description)) {
      builder.description(description);
    }

    // Convert environmentType string to List<EnvironmentType>
    if (isNotEmpty(environmentType)) {
      try {
        EnvironmentType envType = EnvironmentType.valueOf(environmentType);
        builder.environmentTypes(Collections.singletonList(envType));
      } catch (IllegalArgumentException e) {
        log.warn("Invalid environment type: {}", environmentType, e);
      }
    }

    EnvironmentFilterPropertiesDTO filterProperties = builder.build();

    // Convert tags from List<String> to Map<String, String>
    if (isNotEmpty(tags)) {
      Map<String, String> tagsMap = new HashMap<>();
      for (String tag : tags) {
        if (tag.contains(":")) {
          String[] parts = tag.split(":", 2);
          tagsMap.put(parts[0], parts.length > 1 ? parts[1] : "");
        } else {
          tagsMap.put(tag, "");
        }
      }
      filterProperties.setTags(tagsMap);
    }

    return filterProperties;
  }

  private Page<Environment> getRBACFilteredEnvironments(
      String accountId, String orgId, String projectId, Criteria criteria, Pageable pageRequest) {
    if (!environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
            accountId, orgId, projectId, ENVIRONMENT_VIEW_PERMISSION)) {
      Page<Environment> environments = environmentService.list(criteria, Pageable.unpaged());
      if (environments == null || EmptyPredicate.isEmpty(environments)) {
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
}
