/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfchangeset.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfchangeset.entity.SalesforceChangesetEntity;
import io.harness.ng.core.sfchangeset.entity.SalesforceChangesetEntity.SalesforceChangesetEntityKeys;
import io.harness.ng.core.sfchangeset.mappers.SalesforceChangesetOpenApiMapper;
import io.harness.ng.core.sfchangeset.services.SalesforceChangesetService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.spec.server.ng.v1.model.SalesforceChangeset;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetMetadata;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetUpdateRequest;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
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
public class AbstractSalesforceChangesetsApiImpl {
  private final SalesforceChangesetService service;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ScopeInfoService scopeInfoService;

  public Response createChangeset(
      SalesforceChangesetCreateRequest request, String org, String project, String account) {
    throwExceptionForNoRequestDTO(request);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    SalesforceChangesetEntity entity =
        SalesforceChangesetOpenApiMapper.toEntity(account, org, project, request, scopeInfo);

    SalesforceChangesetEntity created = service.create(entity, scopeInfo);
    SalesforceChangeset response = SalesforceChangesetOpenApiMapper.toResponse(created, scopeInfo);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  public Response getChangeset(String org, String project, String identifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Optional<SalesforceChangesetEntity> optionalEntity = service.get(scopeInfo, identifier);
    if (optionalEntity.isEmpty()) {
      throw new NotFoundException(format("SalesforceChangeset with identifier [%s] not found", identifier));
    }

    SalesforceChangeset response = SalesforceChangesetOpenApiMapper.toResponse(optionalEntity.get(), scopeInfo);
    return Response.ok().entity(response).build();
  }

  public Response updateChangeset(
      SalesforceChangesetUpdateRequest request, String org, String project, String identifier, String account) {
    throwExceptionForNoRequestDTO(request);

    // Validate that path parameter matches body identifier if provided
    if (isNotEmpty(request.getIdentifier()) && !request.getIdentifier().equals(identifier)) {
      throw new InvalidRequestException(format(
          "Identifier in request body [%s] does not match path parameter [%s]", request.getIdentifier(), identifier));
    }

    // Set identifier from path parameter (authoritative source)
    request.setIdentifier(identifier);

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    SalesforceChangesetEntity requestedEntity =
        SalesforceChangesetOpenApiMapper.toEntity(account, org, project, request, scopeInfo);

    SalesforceChangesetEntity updated = service.update(requestedEntity, scopeInfo);
    SalesforceChangeset response = SalesforceChangesetOpenApiMapper.toResponse(updated, scopeInfo);
    return Response.ok().entity(response).build();
  }

  public Response deleteChangeset(String org, String project, String identifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    boolean deleted = service.delete(scopeInfo, identifier);
    if (!deleted) {
      throw new InvalidRequestException(
          format("SalesforceChangeset with identifier [%s] could not be deleted", identifier));
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  public Response getChangesets(String org, String project, Integer page, Integer limit, String searchTerm,
      String sourceFilter, String metadataTypeFilter, String comparisonPairRefFilter, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Criteria criteria = new Criteria()
                            .and(SalesforceChangesetEntityKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(SalesforceChangesetEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId());

    List<Criteria> andOperatorCriteriaList = new ArrayList<>();

    if (!StringUtils.isBlank(searchTerm)) {
      andOperatorCriteriaList.add(new Criteria().orOperator(getSearchCriteria(searchTerm)));
    }

    if (!StringUtils.isBlank(sourceFilter)) {
      andOperatorCriteriaList.add(new Criteria().orOperator(
          Criteria.where(SalesforceChangesetEntityKeys.source1)
              .regex(sourceFilter, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(SalesforceChangesetEntityKeys.source2)
              .regex(sourceFilter, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)));
    }

    if (!andOperatorCriteriaList.isEmpty()) {
      criteria.andOperator(andOperatorCriteriaList.toArray(new Criteria[0]));
    }

    if (!StringUtils.isBlank(metadataTypeFilter)) {
      criteria.and(SalesforceChangesetEntityKeys.metadataTypes).in(metadataTypeFilter);
    }

    if (!StringUtils.isBlank(comparisonPairRefFilter)) {
      criteria.and(SalesforceChangesetEntityKeys.comparisonPairRef).is(comparisonPairRefFilter);
    }

    Pageable pageRequest =
        PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, SalesforceChangesetEntityKeys.createdAt));
    Page<SalesforceChangesetEntity> entityPage = service.list(criteria, pageRequest);

    List<SalesforceChangesetMetadata> changesets =
        entityPage.stream()
            .map(entity -> SalesforceChangesetOpenApiMapper.toMetadata(entity, scopeInfo))
            .collect(Collectors.toList());

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entityPage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(changesets).build();
  }

  private Criteria[] getSearchCriteria(String searchTerm) {
    return new Criteria[] {
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceChangesetEntityKeys.name)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceChangesetEntityKeys.identifier)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)};
  }

  private void throwExceptionForNoRequestDTO(SalesforceChangesetCreateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, name, source1, source2. "
          + "Either metadataTypes or metadataFilter is required. Other optional fields: description, tags, "
          + "diffOutput, packageXml, destructiveChangesXml, dependencies, reverseDependencies");
    }
  }

  private void throwExceptionForNoRequestDTO(SalesforceChangesetUpdateRequest request) {
    if (request == null) {
      throw new InvalidRequestException("No request body sent in the API. Following field is required: identifier. "
          + "Optional fields: name, description, tags, metadataTypes, metadataFilter, diffOutput, packageXml, "
          + "destructiveChangesXml, dependencies, reverseDependencies, version");
    }
  }
}
