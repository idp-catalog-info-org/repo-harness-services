/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.app.beans.entities.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.beans.cd.api.InfrastructureResource;
import io.harness.beans.cd.api.beans.GitCreateRequestDTO;
import io.harness.beans.cd.api.beans.GitFindRequestDTO;
import io.harness.beans.cd.api.beans.GitUpdateRequestDTO;
import io.harness.beans.cd.api.beans.InfrastructureRequestDTO;
import io.harness.beans.cd.api.beans.InfrastructureResponse;
import io.harness.cd.mappers.InfrastructureEntityMapper;
import io.harness.cd.mappers.UnifiedGitXUtils;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.ci.environment.utils.InfrastructureMongoOperationsSpringHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class InfrastructureResourceImpl implements InfrastructureResource {
  private final Validator validator;
  private final AccessControlClient accessControlClient;
  private final EnvironmentEntityService environmentEntityService;
  private final InfrastructureEntityService infrastructureEntityService;

  @Override
  public ResponseDTO<InfrastructureResponse> create(
      String accountId, InfrastructureRequestDTO requestDTO, GitCreateRequestDTO gitDetails) {
    validateRequestEntity(requestDTO);
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitCreateDetails(gitDetails));
    checkIfEnvironmentExistOrThrow(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getEnvIdentifier());
    checkForAccessOrThrow(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(),
        requestDTO.getEnvIdentifier(), ENVIRONMENT_UPDATE_PERMISSION, "create");
    InfrastructureEntity infrastructureEntity =
        InfrastructureEntityMapper.toInfrastructureEntity(accountId, requestDTO);
    InfrastructureEntity createdInfra = infrastructureEntityService.create(infrastructureEntity);
    return ResponseDTO.newResponse(InfrastructureEntityMapper.toResponse(createdInfra));
  }

  @Override
  public ResponseDTO<InfrastructureResponse> get(String infraIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, GitFindRequestDTO gitDetails) {
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitFindDetails(gitDetails));
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
    Optional<InfrastructureEntity> infrastructureEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    if (infrastructureEntity.isEmpty()) {
      throw new NotFoundException(InfrastructureEntityMapper.getInfraNotFoundError(
          orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier));
    }
    return ResponseDTO.newResponse(InfrastructureEntityMapper.toResponse(infrastructureEntity.get()));
  }

  @Override
  public ResponseDTO<InfrastructureResponse> update(
      String accountId, InfrastructureRequestDTO requestDTO, GitUpdateRequestDTO gitDetails) {
    validateRequestEntity(requestDTO);
    GitAwareContextHelper.populateGitDetails(UnifiedGitXUtils.populateGitUpdateDetails(gitDetails));
    checkIfEnvironmentExistOrThrow(
        accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getEnvIdentifier());
    checkForAccessOrThrow(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(),
        requestDTO.getEnvIdentifier(), ENVIRONMENT_UPDATE_PERMISSION, "update");
    InfrastructureEntity infrastructureEntity =
        InfrastructureEntityMapper.toInfrastructureEntity(accountId, requestDTO);
    InfrastructureEntity updatedInfra = infrastructureEntityService.update(infrastructureEntity);
    return ResponseDTO.newResponse(InfrastructureEntityMapper.toResponse(updatedInfra));
  }

  @Override
  public ResponseDTO<Boolean> delete(
      String infraIdentifier, String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    checkIfEnvironmentExistOrThrow(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");
    boolean deleted =
        infrastructureEntityService.delete(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<PageResponse<InfrastructureResponse>> listInfrastructures(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, String envIdentifier, String searchTerm, List<String> sort,
      boolean includeChildrenScope, boolean access) {
    if (isNotBlank(envIdentifier)) {
      if (access) {
        checkForAccessOrThrow(
            accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_RUNTIME_PERMISSION, "access");
      } else {
        checkForAccessOrThrow(
            accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");
      }
      checkIfEnvironmentExistOrThrow(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    }

    Criteria criteria = InfrastructureMongoOperationsSpringHelper.getInfrastructureListCriteria(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, includeChildrenScope);
    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<InfrastructureEntity> infraListResponse = infrastructureEntityService.list(criteria, pageRequest);
    return ResponseDTO.newResponse(getNGPageResponse(infraListResponse.map(InfrastructureEntityMapper::toResponse)));
  }

  private void validateRequestEntity(InfrastructureRequestDTO requestDTO) {
    if (requestDTO == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following fields is required: identifier and yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
    Set<ConstraintViolation<InfrastructureRequestDTO>> violations = validator.validate(requestDTO);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });

    if (!violations.isEmpty()) {
      throw new ValidationException(builder.toString());
    }
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }

  private void checkIfEnvironmentExistOrThrow(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    Optional<EnvironmentEntity> environmentEntity =
        environmentEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    if (environmentEntity.isEmpty()) {
      throw new NotFoundException(String.format("Environment with identifier [%s] not found in project [%s], org [%s]",
          envIdentifier, projectIdentifier, orgIdentifier));
    }
  }
}
