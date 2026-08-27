/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.beans.cd.api.EnvironmentResource;
import io.harness.beans.cd.api.beans.EnvironmentRequestDTO;
import io.harness.beans.cd.api.beans.EnvironmentResponse;
import io.harness.cd.mappers.EnvironmentEntityMapper;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
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
public class EnvironmentResourceImpl implements EnvironmentResource {
  private final Validator validator;
  private final AccessControlClient accessControlClient;
  private final EnvironmentEntityService environmentEntityService;

  @Override
  public ResponseDTO<EnvironmentResponse> create(String accountId, EnvironmentRequestDTO environmentRequestDTO) {
    validateRequestEntity(environmentRequestDTO);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_CREATE_PERMISSION);
    EnvironmentEntity environmentEntity = EnvironmentEntityMapper.toEnvironmentEntity(accountId, environmentRequestDTO);

    EnvironmentEntity createdEnvironment = environmentEntityService.create(environmentEntity);
    return ResponseDTO.newResponse(EnvironmentEntityMapper.toResponse(createdEnvironment));
  }

  @Override
  public ResponseDTO<EnvironmentResponse> get(
      String environmentIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION, "unable to view environment");
    Optional<EnvironmentEntity> environmentEntity =
        environmentEntityService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    if (environmentEntity.isEmpty()) {
      throw new NotFoundException(
          EnvironmentEntityMapper.getEnvironmentNotFoundError(orgIdentifier, projectIdentifier, environmentIdentifier));
    }
    return ResponseDTO.newResponse(EnvironmentEntityMapper.toResponse(environmentEntity.get()));
  }

  @Override
  public ResponseDTO<EnvironmentResponse> update(
      String accountId, EnvironmentRequestDTO environmentRequestDTO, GitEntityUpdateInfoDTO gitEntityInfo) {
    validateRequestEntity(environmentRequestDTO);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, environmentRequestDTO.getIdentifier()), ENVIRONMENT_UPDATE_PERMISSION);
    EnvironmentEntity environmentEntity = EnvironmentEntityMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    EnvironmentEntity updatedEnvironment = environmentEntityService.update(environmentEntity);
    return ResponseDTO.newResponse(EnvironmentEntityMapper.toResponse(updatedEnvironment));
  }

  @Override
  public ResponseDTO<Boolean> delete(
      String environmentIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_DELETE_PERMISSION, "Unable to delete environment");
    boolean deleted =
        environmentEntityService.delete(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<PageResponse<EnvironmentResponse>> listEnvironments(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, String searchTerm, List<String> sort,
      boolean includeChildrenScope, boolean access) {
    validateScopes(accountId, orgIdentifier, projectIdentifier);
    String permissionToCheck = access ? ENVIRONMENT_RUNTIME_PERMISSION : ENVIRONMENT_VIEW_PERMISSION;
    if (!hasRequiredPermissionForEnvironments(accountId, orgIdentifier, projectIdentifier, permissionToCheck)) {
      throw new NGAccessDeniedException("Unauthorized to list environments", USER, emptyList());
    }

    Page<EnvironmentEntity> envEntities = environmentEntityService.list(
        page, size, accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permissionToCheck);

    return ResponseDTO.newResponse(getNGPageResponse(envEntities.map(EnvironmentEntityMapper::toResponse)));
  }

  private void validateRequestEntity(EnvironmentRequestDTO requestDTO) {
    if (requestDTO == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following fields is required: identifier and yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
    Set<ConstraintViolation<EnvironmentRequestDTO>> violations = validator.validate(requestDTO);
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

  boolean hasRequiredPermissionForEnvironments(
      String accountId, String orgIdentifier, String projectIdentifier, String permission) {
    return accessControlClient.hasAccess(
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), Resource.of(ENVIRONMENT, null), permission);
  }
}
