/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.cd.api.ServiceResource;
import io.harness.beans.cd.api.beans.GitCreateRequestDTO;
import io.harness.beans.cd.api.beans.GitFindRequestDTO;
import io.harness.beans.cd.api.beans.GitUpdateRequestDTO;
import io.harness.beans.cd.api.beans.ServiceRequestDTO;
import io.harness.beans.cd.api.beans.ServiceResponse;
import io.harness.cd.mappers.UnifiedGitXUtils;
import io.harness.cd.mappers.UnifiedServiceEntityMapper;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class ServiceResourceImpl implements ServiceResource {
  private final Validator validator;
  private final AccessControlClient accessControlClient;
  private final ServiceEntityService serviceEntityService;

  @Override
  public ResponseDTO<ServiceResponse> create(
      String accountId, ServiceRequestDTO serviceRequestDTO, GitCreateRequestDTO gitDetails) {
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitCreateDetails(gitDetails));
    validateRequestEntity(serviceRequestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    ServiceEntity serviceEntity = UnifiedServiceEntityMapper.toServiceEntity(accountId, serviceRequestDTO);

    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    return ResponseDTO.newResponse(UnifiedServiceEntityMapper.toResponse(createdService));
  }

  @Override
  public ResponseDTO<ServiceResponse> get(String serviceIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, GitFindRequestDTO gitDetails, boolean loadFromFallbackBranch) {
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitFindDetails(gitDetails));
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, serviceIdentifier), SERVICE_VIEW_PERMISSION);
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, loadFromFallbackBranch);
    if (serviceEntity.isEmpty()) {
      throw new NotFoundException(
          UnifiedServiceEntityMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }
    return ResponseDTO.newResponse(UnifiedServiceEntityMapper.toResponse(serviceEntity.get()));
  }

  @Override
  public ResponseDTO<ServiceResponse> update(
      String accountId, ServiceRequestDTO serviceRequestDTO, GitUpdateRequestDTO gitDetails) {
    validateRequestEntity(serviceRequestDTO);
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitUpdateDetails(gitDetails));
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    ServiceEntity serviceEntity = UnifiedServiceEntityMapper.toServiceEntity(accountId, serviceRequestDTO);
    ServiceEntity updatedService = serviceEntityService.update(serviceEntity);
    return ResponseDTO.newResponse(UnifiedServiceEntityMapper.toResponse(updatedService));
  }

  @Override
  public ResponseDTO<Boolean> delete(
      String serviceIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    // Todo: Instances related operation to be implemented
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, serviceIdentifier), SERVICE_DELETE_PERMISSION);
    boolean deleted = serviceEntityService.delete(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<PageResponse<ServiceResponse>> listServices(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, String searchTerm, List<String> sort,
      boolean includeChildrenScope, boolean access) {
    validateScopes(accountId, orgIdentifier, projectIdentifier);
    String permissionToCheck = access ? SERVICE_RUNTIME_PERMISSION : SERVICE_VIEW_PERMISSION;
    if (!hasViewPermissionForServices(accountId, orgIdentifier, projectIdentifier, permissionToCheck)) {
      throw new NGAccessDeniedException("Unauthorized to list services", USER, emptyList());
    }

    Page<ServiceEntity> serviceEntities = serviceEntityService.list(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permissionToCheck, page, size);

    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(UnifiedServiceEntityMapper::toResponse)));
  }

  private void validateRequestEntity(ServiceRequestDTO requestDTO) {
    if (requestDTO == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following fields is required: identifier and yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
    Set<ConstraintViolation<ServiceRequestDTO>> violations = validator.validate(requestDTO);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void validateScopes(String accountId, String orgIdentifier, String projectIdentifier) {
    if (isBlank(accountId)) {
      throw new InvalidRequestException("AccountID is mandatory");
    }

    if (isBlank(orgIdentifier) && isNotBlank(projectIdentifier)) {
      throw new InvalidRequestException("Org Identifier is mandatory if projectIdentifier is given");
    }
  }

  boolean hasViewPermissionForServices(
      String accountId, String orgIdentifier, String projectIdentifier, String permission) {
    return accessControlClient.hasAccess(
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), Resource.of(SERVICE, null), permission);
  }
}
