/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfcomparisonpair.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.sfcomparisonpair.entity.SalesforceComparisonPairEntity;
import io.harness.ng.core.sfcomparisonpair.entity.SalesforceComparisonPairEntity.SalesforceComparisonPairEntityKeys;
import io.harness.ng.core.sfcomparisonpair.mappers.SalesforceComparisonPairOpenApiMapper;
import io.harness.ng.core.sfcomparisonpair.services.SalesforceComparisonPairService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPair;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairMetadata;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairUpdateRequest;
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
public class AbstractSalesforceComparisonPairsApiImpl {
  private final SalesforceComparisonPairService service;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final io.harness.ng.core.services.ScopeInfoService scopeInfoService;

  public Response createComparisonPair(
      SalesforceComparisonPairCreateRequest request, String org, String project, String account) {
    throwExceptionForNoRequestDTO(request);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    SalesforceComparisonPairEntity entity =
        SalesforceComparisonPairOpenApiMapper.toEntity(account, org, project, request, scopeInfo);

    SalesforceComparisonPairEntity created = service.create(entity, scopeInfo);
    SalesforceComparisonPair response = SalesforceComparisonPairOpenApiMapper.toResponse(created, scopeInfo);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  public Response getComparisonPair(String org, String project, String identifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Optional<SalesforceComparisonPairEntity> optionalEntity = service.get(scopeInfo, identifier);
    if (optionalEntity.isEmpty()) {
      throw new NotFoundException(format("SalesforceComparisonPair with identifier [%s] not found", identifier));
    }

    SalesforceComparisonPair response =
        SalesforceComparisonPairOpenApiMapper.toResponse(optionalEntity.get(), scopeInfo);
    return Response.ok().entity(response).build();
  }

  public Response updateComparisonPair(
      SalesforceComparisonPairUpdateRequest request, String org, String project, String identifier, String account) {
    throwExceptionForNoRequestDTO(request);
    if (isNotEmpty(identifier)) {
      request.setIdentifier(identifier);
    }

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account);

    SalesforceComparisonPairEntity requestedEntity =
        SalesforceComparisonPairOpenApiMapper.toEntity(account, org, project, request, scopeInfo);

    SalesforceComparisonPairEntity updated = service.update(requestedEntity, scopeInfo);
    SalesforceComparisonPair response = SalesforceComparisonPairOpenApiMapper.toResponse(updated, scopeInfo);
    return Response.ok().entity(response).build();
  }

  public Response deleteComparisonPair(String org, String project, String identifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    boolean deleted = service.delete(scopeInfo, identifier);
    if (!deleted) {
      throw new InvalidRequestException(
          format("SalesforceComparisonPair with identifier [%s] could not be deleted", identifier));
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  public Response getComparisonPairs(String org, String project, Integer page, Integer limit, String searchTerm,
      String sourceRefFilter, String metadataTypeFilter, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Criteria criteria = new Criteria()
                            .and(SalesforceComparisonPairEntityKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(SalesforceComparisonPairEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId());

    List<Criteria> andOperatorCriteriaList = new ArrayList<>();

    if (!StringUtils.isBlank(searchTerm)) {
      andOperatorCriteriaList.add(new Criteria().orOperator(getSearchCriteria(searchTerm)));
    }

    if (!StringUtils.isBlank(sourceRefFilter)) {
      andOperatorCriteriaList.add(new Criteria().orOperator(
          Criteria.where(SalesforceComparisonPairEntityKeys.sourceRef1)
              .regex(sourceRefFilter, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(SalesforceComparisonPairEntityKeys.sourceRef2)
              .regex(sourceRefFilter, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)));
    }

    if (!andOperatorCriteriaList.isEmpty()) {
      criteria.andOperator(andOperatorCriteriaList.toArray(new Criteria[0]));
    }

    if (!StringUtils.isBlank(metadataTypeFilter)) {
      criteria.and(SalesforceComparisonPairEntityKeys.metadataTypes).in(metadataTypeFilter);
    }

    Pageable pageRequest =
        PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, SalesforceComparisonPairEntityKeys.createdAt));
    Page<SalesforceComparisonPairEntity> entityPage = service.list(criteria, pageRequest);

    List<SalesforceComparisonPairMetadata> comparisonPairs =
        entityPage.stream()
            .map(entity -> SalesforceComparisonPairOpenApiMapper.toMetadata(entity, scopeInfo))
            .collect(Collectors.toList());

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entityPage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(comparisonPairs).build();
  }

  private Criteria[] getSearchCriteria(String searchTerm) {
    return new Criteria[] {
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceComparisonPairEntityKeys.name)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceComparisonPairEntityKeys.identifier)
            .regex(searchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)};
  }

  private void throwExceptionForNoRequestDTO(SalesforceComparisonPairCreateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, name, sourceRef1, sourceRef2. "
          + "Either metadataTypes or metadataFilter is required. Other optional fields: description, tags");
    }
  }

  private void throwExceptionForNoRequestDTO(SalesforceComparisonPairUpdateRequest request) {
    if (request == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, name, sourceRef1, sourceRef2. "
          + "Either metadataTypes or metadataFilter is required. Other optional fields: description, tags, version");
    }
  }
}
