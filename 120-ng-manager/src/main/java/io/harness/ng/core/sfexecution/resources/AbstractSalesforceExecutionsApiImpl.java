/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfexecution.resources;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.sfexecution.SalesforceExecutionOrchestrationService;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionEntity;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionEntity.SalesforceExecutionEntityKeys;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionStatus;
import io.harness.ng.core.sfexecution.entity.SalesforceExecutionType;
import io.harness.ng.core.sfexecution.mappers.SalesforceExecutionOpenApiMapper;
import io.harness.ng.core.sfexecution.services.SalesforceExecutionService;
import io.harness.ng.core.utils.NGUtils;
import io.harness.spec.server.ng.v1.model.SalesforceExecution;
import io.harness.spec.server.ng.v1.model.SalesforceExecutionListItem;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
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
public class AbstractSalesforceExecutionsApiImpl {
  private final SalesforceExecutionService service;
  private final ScopeInfoService scopeInfoService;
  private final SalesforceExecutionOrchestrationService orchestrationService;

  public Response getExecution(String org, String project, String identifier, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Optional<SalesforceExecutionEntity> optionalEntity = service.get(scopeInfo, identifier);
    if (optionalEntity.isEmpty()) {
      throw new NotFoundException(format("SalesforceExecution with identifier [%s] not found", identifier));
    }

    SalesforceExecutionEntity entity = orchestrationService.resolveStatusIfInProgress(optionalEntity.get());
    SalesforceExecution response = SalesforceExecutionOpenApiMapper.toResponse(entity, scopeInfo);
    return Response.ok().entity(response).build();
  }

  public Response getExecutions(String org, String project, Integer page, Integer limit, String searchTerm,
      String typeFilter, String changesetId, String account) {
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    Criteria criteria = new Criteria()
                            .and(SalesforceExecutionEntityKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(SalesforceExecutionEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId());

    if (!StringUtils.isBlank(searchTerm)) {
      criteria.andOperator(new Criteria().orOperator(getSearchCriteria(searchTerm)));
    }

    if (!StringUtils.isBlank(typeFilter)) {
      try {
        SalesforceExecutionType executionType = SalesforceExecutionType.valueOf(typeFilter);
        criteria.and(SalesforceExecutionEntityKeys.type).is(executionType);
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException(
            format("Invalid typeFilter value [%s]. Allowed values: DEPLOY, VALIDATE, QUICK_DEPLOY", typeFilter));
      }
    }

    if (!StringUtils.isBlank(changesetId)) {
      criteria.and("metadata.changesetIdentifier").is(changesetId);
    }

    Pageable pageRequest =
        PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, SalesforceExecutionEntityKeys.createdAt));
    Page<SalesforceExecutionEntity> entityPage = service.list(criteria, pageRequest);

    List<SalesforceExecutionListItem> executions =
        entityPage.stream()
            .map(entity
                -> entity.getStatus() == SalesforceExecutionStatus.IN_PROGRESS
                    ? orchestrationService.resolveStatusIfInProgress(entity)
                    : entity)
            .map(entity -> SalesforceExecutionOpenApiMapper.toListItem(entity, scopeInfo))
            .collect(Collectors.toList());

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entityPage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(executions).build();
  }

  private Criteria[] getSearchCriteria(String searchTerm) {
    String escapedSearchTerm = NGUtils.sanitisePCRECharacterData(searchTerm);
    return new Criteria[] {
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceExecutionEntityKeys.name)
            .regex(escapedSearchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        org.springframework.data.mongodb.core.query.Criteria.where(SalesforceExecutionEntityKeys.identifier)
            .regex(escapedSearchTerm, io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)};
  }
}
