/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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
import io.harness.cd.mappers.EnvironmentEntityMapper;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ci.v1.model.EnvironmentRequest;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class AbstractEnvironmentsApiImpl {
  private final EnvironmentEntityService environmentEntityService;
  private final AccessControlClient accessControlClient;
  private final Validator validator;

  public Response createEnvironmentEntity(
      EnvironmentRequest body, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    validateRequestEntity(body);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_CREATE_PERMISSION);
    validateEnvironmentScope(orgIdentifier, projectIdentifier);
    EnvironmentEntity environmentEntity =
        EnvironmentEntityMapper.toEnvironmentEntity(harnessAccount, body, orgIdentifier, projectIdentifier);
    EnvironmentEntity createdEnvironment = environmentEntityService.create(environmentEntity);
    return Response.status(Response.Status.CREATED)
        .entity(EnvironmentEntityMapper.toResponse(createdEnvironment))
        .build();
  }

  public Response deleteEnvironmentEntityByIdentifier(
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_DELETE_PERMISSION, "Unable to delete environment");
    boolean deleted =
        environmentEntityService.delete(accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);
    return Response.status(Response.Status.NO_CONTENT).entity(deleted).build();
  }

  public Response getEnvironmentEntityByIdentifier(
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION, "unable to view environment");
    Optional<EnvironmentEntity> environmentEntity =
        environmentEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);
    if (environmentEntity.isEmpty()) {
      throw new NotFoundException(
          EnvironmentEntityMapper.getEnvironmentNotFoundError(orgIdentifier, projectIdentifier, environmentIdentifier));
    }
    return Response.status(Response.Status.OK)
        .entity(EnvironmentEntityMapper.toResponse(environmentEntity.get()))
        .build();
  }

  public Response getEnvironmentEntities(String orgIdentifier, String projectIdentifier, String harnessAccount,
      Integer page, Integer limit, String sort, Boolean isAccessList, String searchTerm, Boolean includeChildrenScope) {
    validateScopes(harnessAccount, orgIdentifier, projectIdentifier);
    String permissionToCheck = isAccessList ? ENVIRONMENT_RUNTIME_PERMISSION : ENVIRONMENT_VIEW_PERMISSION;
    if (!hasRequiredPermissionForEnvironments(harnessAccount, orgIdentifier, projectIdentifier, permissionToCheck)) {
      throw new NGAccessDeniedException("Unauthorized to list environments", USER, emptyList());
    }

    Page<EnvironmentEntity> envEntities = environmentEntityService.list(page, limit, harnessAccount, orgIdentifier,
        projectIdentifier, searchTerm, includeChildrenScope, permissionToCheck);
    return Response.status(Response.Status.OK)
        .entity(getNGPageResponse(envEntities.map(EnvironmentEntityMapper::toResponse)))
        .build();
  }

  public Response updateEnvironmentEntity(
      EnvironmentRequest body, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    validateRequestEntity(body);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, body.getIdentifier()), ENVIRONMENT_UPDATE_PERMISSION);
    EnvironmentEntity environmentEntity =
        EnvironmentEntityMapper.toEnvironmentEntity(accountIdentifier, body, orgIdentifier, projectIdentifier);
    EnvironmentEntity updatedEnvironment = environmentEntityService.update(environmentEntity);
    return Response.status(Response.Status.OK).entity(EnvironmentEntityMapper.toResponse(updatedEnvironment)).build();
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

  private void validateRequestEntity(EnvironmentRequest requestDTO) {
    if (requestDTO == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following fields is required: identifier and yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
    Set<ConstraintViolation<EnvironmentRequest>> violations = validator.validate(requestDTO);
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
