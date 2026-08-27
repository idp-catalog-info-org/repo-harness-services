/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;

import static java.lang.String.format;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.mapper.InfraOpenApiMapper;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityVersionAwareFacade;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.spec.server.ng.v1.model.InfrastructureCreateRequest;
import io.harness.spec.server.ng.v1.model.InfrastructureResponse;
import io.harness.spec.server.ng.v1.model.InfrastructureUpdateRequest;
import io.harness.utils.ApiUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class AbstractInfrastructuresApiImpl {
  private final InfrastructureEntityService infrastructureEntityService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final AccessControlClient accessControlClient;

  private final CustomDeploymentYamlHelper customDeploymentYamlHelper;
  private final CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  private final SshEntityHelper sshEntityHelper;
  private final ServiceResourceApiUtils serviceResourceApiUtils;
  private final InfrastructureEntityVersionAwareFacade infraVersionAwareFacade;
  private final ScopeInfoService scopeInfoService;

  public Response createInfrastructureEntity(
      InfrastructureCreateRequest infraRequest, String org, String project, String environment, String harnessAccount) {
    throwExceptionForNoRequestDTO(infraRequest);
    setHarnessVersionAndIdInRequest(infraRequest, null);
    validateProjectLevelInfraScope(org, project);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    environmentValidationHelper.checkThatEnvExists(harnessAccount, org, project, environment);
    // access for updating Environment
    checkForAccessOrThrow(harnessAccount, org, project, environment, ENVIRONMENT_UPDATE_PERMISSION, "create");

    infraVersionAwareFacade.validateSchema(harnessAccount, infraRequest.getYaml(), infraRequest.getHarnessVersion());
    InfrastructureEntity infrastructureEntity =
        InfraOpenApiMapper.toEntity(infraRequest, harnessAccount, org, project, environment);

    if (HarnessYamlVersion.V0.equals(infraRequest.getHarnessVersion())) {
      validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    }

    InfrastructureEntity createdInfrastructure =
        infrastructureEntityService.create(infrastructureEntity).getInfrastructureEntity();

    return Response.status(Response.Status.CREATED)
        .entity(InfraOpenApiMapper.toResponse(createdInfrastructure))
        .build();
  }

  public Response updateInfrastructureEntity(InfrastructureUpdateRequest infraRequest, String org, String project,
      String environment, String infrastructureDefinition, String harnessAccount) {
    throwExceptionForNoRequestDTO(infraRequest);
    setHarnessVersionAndIdInRequest(infraRequest, infrastructureDefinition);
    validateProjectLevelInfraScope(org, project);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    environmentValidationHelper.checkThatEnvExists(harnessAccount, org, project, environment);
    checkForAccessOrThrow(harnessAccount, org, project, environment, ENVIRONMENT_UPDATE_PERMISSION, "update");

    infraVersionAwareFacade.validateSchema(harnessAccount, infraRequest.getYaml(), infraRequest.getHarnessVersion());

    InfrastructureEntity infrastructureEntity =
        InfraOpenApiMapper.toEntity(infraRequest, harnessAccount, org, project, environment);
    if (HarnessYamlVersion.V0.equals(infraRequest.getHarnessVersion())) {
      validateDeploymentTypeSpecificInfrastructureYaml(infrastructureEntity);
    }

    InfrastructureEntity updatedInfraEntity =
        infrastructureEntityService.update(infrastructureEntity).getInfrastructureEntity();
    return Response.ok().entity(InfraOpenApiMapper.toResponse(updatedInfraEntity)).build();
  }

  public Response getInfrastructureEntity(
      String org, String project, String environment, String infrastructureDefinition, String harnessAccount) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    environmentValidationHelper.checkThatEnvExists(harnessAccount, org, project, environment);
    checkForAccessOrThrow(harnessAccount, org, project, environment, ENVIRONMENT_VIEW_PERMISSION, "view");
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
    Optional<InfrastructureEntity> infraEntity = infrastructureEntityService.get(
        harnessAccount, org, project, scopeInfo, environment, infrastructureDefinition, false, false);

    if (infraEntity.isPresent()) {
      InfrastructureEntity infra = infraEntity.get();

      if (isEmpty(infra.getYaml())) {
        InfrastructureConfig infrastructureConfig = InfrastructureEntityConfigMapper.toInfrastructureConfig(infra);
        infra.setYaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig));
      }
    } else {
      throw new NotFoundException(
          format("Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
              infrastructureDefinition, project, org, environment));
    }

    return Response.ok().entity(InfraOpenApiMapper.toResponse(infraEntity.get())).build();
  }

  public Response deleteInfrastructureEntity(String org, String project, String environment,
      String infrastructureDefinition, String harnessAccount, Boolean forceDelete) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    environmentValidationHelper.checkThatEnvExists(harnessAccount, org, project, environment);
    checkForAccessOrThrow(harnessAccount, org, project, environment, ENVIRONMENT_UPDATE_PERMISSION, "delete");
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
    infrastructureEntityService.delete(
        harnessAccount, org, project, scopeInfo, environment, infrastructureDefinition, forceDelete);
    return Response.status(204).build();
  }

  public Response getInfrastructureEntities(String org, String project, String environment, String harnessAccount,
      Integer page, Integer limit, String searchTerm, List<String> infraIds, String sort, Boolean isAccessList,
      List<String> serviceRefs, String templateIdentifier, String templateVersion, String deploymentType,
      String order) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, harnessAccount);
    environmentValidationHelper.checkThatEnvExists(harnessAccount, org, project, environment);
    checkForAccessOrThrow(harnessAccount, org, project, environment, ENVIRONMENT_VIEW_PERMISSION, "list");
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);

    Criteria criteria =
        InfrastructureFilterHelper.createListCriteria(scopeInfo, environment, searchTerm, infraIds, null, null, false);
    Pageable pageRequest;
    pageRequest = preparePageRequest(page, limit, sort, order);
    boolean isGetInfraForCustomDeployment =
        ServiceDefinitionType.CUSTOM_DEPLOYMENT.toString().equals(deploymentType) && !isEmpty(templateIdentifier);
    Page<InfrastructureEntity> infraEntities =
        infrastructureEntityService.list(criteria, pageRequest, isGetInfraForCustomDeployment);

    infraEntities = infrastructureEntityService.getScopedInfrastructures(infraEntities, serviceRefs);

    if (isGetInfraForCustomDeployment) {
      infraEntities = customDeploymentYamlHelper.getFilteredInfraEntities(page, limit,
          Collections.singletonList(serviceResourceApiUtils.mapSort(sort, order)), templateIdentifier, templateVersion,
          infraEntities, harnessAccount, org, project);
    }

    if (Boolean.TRUE.equals(isAccessList)) {
      infraEntities = filterInfraBasedOnAccess(infraEntities, harnessAccount, org, project);
    }

    Page<InfrastructureResponse> infraResponsePage = infraEntities.map(InfraOpenApiMapper::toResponse);
    List<InfrastructureResponse> infraList = infraResponsePage.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, infraResponsePage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(infraList).build();
  }

  private Page<InfrastructureEntity> filterInfraBasedOnAccess(
      Page<InfrastructureEntity> infraEntities, String accountId, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION);

    List<PermissionCheckDTO> permissionCheckDTOS = new ArrayList<>(getPermissionDTOForEnvironments(infraEntities));
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

    List<InfrastructureEntity> permittedInfra =
        infraEntities.stream()
            .filter(infra
                -> permittedEnvMap.get(
                    IdentifierRefHelper
                        .getIdentifierRefFromEntityIdentifiers(infra.getEnvIdentifier(), infra.getAccountIdentifier(),
                            infra.getOrgIdentifier(), infra.getProjectIdentifier())
                        .buildScopedIdentifier()))
            .collect(Collectors.toList());

    return new PageImpl<>(permittedInfra, infraEntities.getPageable(), permittedInfra.size());
  }

  private Set<PermissionCheckDTO> getPermissionDTOForEnvironments(Page<InfrastructureEntity> infraEntities) {
    return infraEntities.stream()
        .map(infra
            -> CDNGRbacUtility.toEnvRuntimePermissionCheckDTO(
                infra.getEnvIdentifier(), infra.getAccountId(), infra.getOrgIdentifier(), infra.getProjectIdentifier()))
        .collect(Collectors.toSet());
  }

  private Pageable preparePageRequest(Integer page, Integer limit, String sort, String order) {
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      String sortQuery = serviceResourceApiUtils.mapSort(sort, order);
      pageRequest = PageUtils.getPageRequest(page, limit, Collections.singletonList(sortQuery));
    }
    return pageRequest;
  }

  private void setHarnessVersionAndIdInRequest(InfrastructureCreateRequest infraRequest, String infraIdentifier) {
    infraRequest.setHarnessVersion(NGYamlHelper.getVersion(infraRequest.getYaml()));
    if (isNotEmpty(infraIdentifier)) {
      infraRequest.setIdentifier(infraIdentifier);
    }
  }

  private void setHarnessVersionAndIdInRequest(InfrastructureUpdateRequest infraRequest, String infraIdentifier) {
    infraRequest.setHarnessVersion(NGYamlHelper.getVersion(infraRequest.getYaml()));
    if (isNotEmpty(infraIdentifier)) {
      infraRequest.setIdentifier(infraIdentifier);
    }
  }

  private void throwExceptionForNoRequestDTO(InfrastructureCreateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier,envIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(InfrastructureUpdateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier. Other optional fields: name, "
          + "orgIdentifier, projectIdentifier,envIdentifier, tags, description");
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

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
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
}
