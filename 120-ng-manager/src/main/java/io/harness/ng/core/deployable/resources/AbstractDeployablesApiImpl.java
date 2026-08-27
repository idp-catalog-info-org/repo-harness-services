/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deployable.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.deployable.DeployableType;
import io.harness.ng.core.deployable.beans.DeployableDataResponse;
import io.harness.ng.core.deployable.entity.DeployableEntity;
import io.harness.ng.core.deployable.entity.DeployableEntity.DeployableEntityKeys;
import io.harness.ng.core.deployable.mappers.DeployableOpenApiMapper;
import io.harness.ng.core.deployable.services.DeployableEntityService;
import io.harness.ng.core.deployable.services.impl.DeployableYamlSchemaHelper;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.spec.server.ng.v1.model.Deployable;
import io.harness.spec.server.ng.v1.model.DeployableCreateRequest;
import io.harness.spec.server.ng.v1.model.DeployableMetadata;
import io.harness.spec.server.ng.v1.model.DeployableUpdateRequest;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.SALESFORCE})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class AbstractDeployablesApiImpl {
  private final DeployableEntityService deployableEntityService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final io.harness.ng.core.services.ScopeInfoService scopeInfoService;
  private final DeployableYamlSchemaHelper deployableYamlSchemaHelper;

  public Response createDeployableEntity(DeployableCreateRequest request, String org, String project, String account) {
    throwExceptionForNoRequestDTO(request);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    if (EmptyPredicate.isNotEmpty(request.getYaml())) {
      deployableYamlSchemaHelper.validateSchema(account, request.getYaml());
    }
    DeployableEntity entity = DeployableOpenApiMapper.toDeployableEntity(account, org, project, request, scopeInfo);

    DeployableDataResponse created = deployableEntityService.create(entity, scopeInfo);
    Deployable deployable = DeployableOpenApiMapper.toResponse(created.getDeployable(), scopeInfo);
    return Response.status(Response.Status.CREATED).entity(deployable).build();
  }

  public Response getDeployableEntity(String org, String project, String deployableIdentifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Optional<DeployableEntity> optionalDeployable = deployableEntityService.get(scopeInfo, deployableIdentifier, false);
    if (optionalDeployable.isEmpty()) {
      throw new NotFoundException(format("Deployable with identifier [%s] not found", deployableIdentifier));
    }

    Deployable deployable = DeployableOpenApiMapper.toResponse(optionalDeployable.get(), scopeInfo);
    return Response.ok().entity(deployable).build();
  }

  public Response updateDeployableEntity(
      DeployableUpdateRequest request, String org, String project, String deployableIdentifier, String account) {
    throwExceptionForNoRequestDTO(request);
    if (isNotEmpty(deployableIdentifier)) {
      request.setIdentifier(deployableIdentifier);
    }

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    DeployableEntity requestedEntity =
        DeployableOpenApiMapper.toDeployableEntity(account, org, project, request, scopeInfo);

    if (EmptyPredicate.isNotEmpty(request.getYaml())) {
      deployableYamlSchemaHelper.validateSchema(account, request.getYaml());
    }

    DeployableDataResponse updated = deployableEntityService.update(requestedEntity, scopeInfo);
    Deployable deployable = DeployableOpenApiMapper.toResponse(updated.getDeployable(), scopeInfo);
    return Response.ok().entity(deployable).build();
  }

  public Response deleteDeployableEntity(String org, String project, String deployableIdentifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    boolean deleted = deployableEntityService.delete(scopeInfo, deployableIdentifier, null, false);
    if (!deleted) {
      throw new InvalidRequestException(
          format("Deployable with identifier [%s] could not be deleted", deployableIdentifier));
    }
    return Response.status(Response.Status.OK).build();
  }

  public Response getDeployableEntities(String org, String project, Integer page, Integer limit, String searchTerm,
      String deployableType, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Criteria criteria = new Criteria()
                            .and(DeployableEntityKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(DeployableEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(DeployableEntityKeys.deleted)
                            .is(false);

    if (!StringUtils.isBlank(searchTerm)) {
      criteria = criteria.andOperator(getSearchCriteria(searchTerm));
    }

    if (!StringUtils.isBlank(deployableType)) {
      validateDeployableType(deployableType);
      criteria.and(DeployableEntityKeys.type).is(deployableType);
    }

    Pageable pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, DeployableEntityKeys.createdAt));
    Page<DeployableEntity> deployablePage = deployableEntityService.list(criteria, pageRequest);

    List<DeployableMetadata> deployables = deployablePage.stream()
                                               .map(entity -> DeployableOpenApiMapper.toMetadata(entity, scopeInfo))
                                               .collect(Collectors.toList());

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, deployablePage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(deployables).build();
  }

  private Criteria getSearchCriteria(String searchTerm) {
    return new Criteria().orOperator(
        org.springframework.data.mongodb.core.query.Criteria.where(DeployableEntityKeys.name)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        org.springframework.data.mongodb.core.query.Criteria.where(DeployableEntityKeys.identifier)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
  }

  private void throwExceptionForNoRequestDTO(DeployableCreateRequest request) {
    if (request == null) {
      throw new InvalidRequestException("No request body sent in the API. Following field is required: identifier, "
          + "type, yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description");
    }
  }

  private void throwExceptionForNoRequestDTO(DeployableUpdateRequest request) {
    if (request == null) {
      throw new InvalidRequestException("No request body sent in the API. Following field is required: identifier, "
          + "type, yaml. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description");
    }
  }

  private void validateDeployableType(String deployableType) {
    try {
      DeployableType.valueOf(deployableType);
    } catch (IllegalArgumentException e) {
      String validValues = Arrays.stream(DeployableType.values()).map(Enum::name).collect(Collectors.joining(", "));
      throw new InvalidRequestException(
          format("Invalid deployable type: %s. Valid values are: %s", deployableType, validValues));
    }
  }
}
