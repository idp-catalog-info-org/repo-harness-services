/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_GROUP_RUNTIME_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_GROUP_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT_GROUP;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.beans.cd.api.EnvironmentGroupResource;
import io.harness.beans.cd.api.beans.EnvironmentGroupRequestDTO;
import io.harness.beans.cd.api.beans.EnvironmentGroupResponse;
import io.harness.cd.mappers.EnvironmentGroupEntityMapper;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.environment.utils.EnvironmentGroupEntityRbacHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.CDNGRbacPermissions;
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
public class EnvironmentGroupResourceImpl implements EnvironmentGroupResource {
  private final Validator validator;
  private final AccessControlClient accessControlClient;
  private final EnvironmentGroupService environmentGroupService;
  private final EnvironmentGroupEntityRbacHelper envGroupEntityRbacHelper;

  @Override
  public ResponseDTO<EnvironmentGroupResponse> create(String accountId, EnvironmentGroupRequestDTO requestDTO) {
    validateRequestEntity(requestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.ENVIRONMENT_GROUP, null), CDNGRbacPermissions.ENVIRONMENT_GROUP_CREATE_PERMISSION);
    EnvironmentGroupEntity entity = EnvironmentGroupEntityMapper.toEnvironmentGroupEntity(accountId, requestDTO);
    checkReferredEnvironmentsViewPermission(entity);
    EnvironmentGroupEntity createdEntity = environmentGroupService.create(entity);
    return ResponseDTO.newResponse(EnvironmentGroupEntityMapper.toResponseWrapper(createdEntity));
  }

  @Override
  public ResponseDTO<EnvironmentGroupResponse> get(
      String identifier, String accountId, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT_GROUP, identifier),
        CDNGRbacPermissions.ENVIRONMENT_GROUP_VIEW_PERMISSION);
    Optional<EnvironmentGroupEntity> environmentGroupEntityOptional =
        environmentGroupService.get(accountId, orgIdentifier, projectIdentifier, identifier);
    if (environmentGroupEntityOptional.isPresent()) {
      return ResponseDTO.newResponse(
          EnvironmentGroupEntityMapper.toResponseWrapper(environmentGroupEntityOptional.get()));
    } else {
      throw new NotFoundException(
          String.format("Environment Group with identifier [%s] in project [%s], org [%s] not found", identifier,
              projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public ResponseDTO<EnvironmentGroupResponse> update(String accountId, EnvironmentGroupRequestDTO requestDTO) {
    validateRequestEntity(requestDTO);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier()),
        Resource.of(NGResourceType.ENVIRONMENT_GROUP, requestDTO.getIdentifier()),
        CDNGRbacPermissions.ENVIRONMENT_GROUP_UPDATE_PERMISSION);
    EnvironmentGroupEntity requestedEntity =
        EnvironmentGroupEntityMapper.toEnvironmentGroupEntity(accountId, requestDTO);
    checkReferredEnvironmentsViewPermission(requestedEntity);
    EnvironmentGroupEntity updatedEntity = environmentGroupService.update(requestedEntity);
    return ResponseDTO.newResponse(EnvironmentGroupEntityMapper.toResponseWrapper(updatedEntity));
  }

  @Override
  public ResponseDTO<Boolean> delete(
      String identifier, String accountId, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT_GROUP, identifier),
        CDNGRbacPermissions.ENVIRONMENT_GROUP_DELETE_PERMISSION);
    boolean deleted = environmentGroupService.delete(accountId, orgIdentifier, projectIdentifier, identifier);
    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<PageResponse<EnvironmentGroupResponse>> listEnvironmentGroups(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, String searchTerm, List<String> sort,
      boolean includeChildrenScope, boolean access) {
    validateScopes(accountId, orgIdentifier, projectIdentifier);
    String permissionToCheck = access ? ENVIRONMENT_GROUP_RUNTIME_PERMISSION : ENVIRONMENT_GROUP_VIEW_PERMISSION;
    if (!hasPermissionForEnvironmentGroups(accountId, orgIdentifier, projectIdentifier, permissionToCheck)) {
      throw new NGAccessDeniedException("Unauthorized to list environment groups", USER, emptyList());
    }

    Page<EnvironmentGroupEntity> envGroupEntities = environmentGroupService.list(
        accountId, orgIdentifier, projectIdentifier, searchTerm, includeChildrenScope, permissionToCheck, page, size);

    return ResponseDTO.newResponse(
        getNGPageResponse(envGroupEntities.map(EnvironmentGroupEntityMapper::toResponseWrapper)));
  }

  private boolean hasPermissionForEnvironmentGroups(
      String accountId, String orgIdentifier, String projectIdentifier, String permission) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT_GROUP, null), permission);
  }

  private void checkReferredEnvironmentsViewPermission(EnvironmentGroupEntity environmentGroup) {
    String accountId = environmentGroup.getAccountId();
    String orgId = environmentGroup.getOrgIdentifier();
    String projectId = environmentGroup.getProjectIdentifier();

    List<String> envIdentifiers = environmentGroup.getEnvironments();
    if (isNotEmpty(envIdentifiers)) {
      envIdentifiers.forEach(envId
          -> accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
              Resource.of(NGResourceType.ENVIRONMENT, envId), CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION));
    }
  }

  private void validateRequestEntity(EnvironmentGroupRequestDTO requestDTO) {
    if (requestDTO == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following fields is required: identifier and environments. Other optional "
          + "fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
    Set<ConstraintViolation<EnvironmentGroupRequestDTO>> violations = validator.validate(requestDTO);
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
}
