/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.SUCCESS_RESPONSE;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.GitXUtils;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.GetEntitiesGroupsDTO;
import io.harness.idp.catalog.beans.GetEntityVersionsDTO;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncFailedException;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncInProgressException;
import io.harness.idp.catalog.service.ApiDefinitionResolver;
import io.harness.idp.catalog.service.ApiEndpointSyncService;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateService;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.service.CatalogTableService;
import io.harness.idp.catalog.service.CatalogVersionService;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.idp.v1.EntitiesApi;
import io.harness.spec.server.idp.v1.model.ApiEndpointSyncResponse;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateRequest;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateOperationResponse;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateSubmitResponse;
import io.harness.spec.server.idp.v1.model.CatalogSyncRequest;
import io.harness.spec.server.idp.v1.model.DefaultSaveResponse;
import io.harness.spec.server.idp.v1.model.EntitiesByRefsRequest;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.EntityConvertResponse;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityFilterQueryRequest;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.EntityKindsResponse;
import io.harness.spec.server.idp.v1.model.EntityMoveRequest;
import io.harness.spec.server.idp.v1.model.EntityRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentBluePrintInfoResponse;
import io.harness.spec.server.idp.v1.model.EnvironmentBlueprintInfoRequest;
import io.harness.spec.server.idp.v1.model.GitImportDetails;
import io.harness.spec.server.idp.v1.model.GitMetadataUpdateRequest;
import io.harness.spec.server.idp.v1.model.WorkflowExecutionHistoryRequest;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Timed
@ResponseMetered
public class EntitiesApiImpl implements EntitiesApi {
  // V1 default: SUPPORTED_KINDS minus the three special kinds excluded by V1's permittedEntityRefs filter
  private static final String V1_DEFAULT_KIND_FILTER =
      Constants.SUPPORTED_KINDS.stream()
          .filter(k
              -> !k.equals("workflow") && !k.equals("environment") && !k.equals("environmentblueprint")
                  && !k.equals("group"))
          .collect(Collectors.joining(","));

  private CatalogService catalogService;
  private IdpCommonService idpCommonService;
  private IDPGitXHelper idpGitXHelper;
  private CatalogVersionService catalogVersionService;
  private CatalogTableService catalogTableService;
  private ApiEndpointSyncService apiEndpointSyncService;
  private BulkEntityFieldUpdateService bulkEntityFieldUpdateService;
  private ApiDefinitionResolver apiDefinitionResolver;
  private CatalogServiceHelper catalogServiceHelper;

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response convertEntity(@Valid EntityRequest body, String option, @AccountIdentifier String harnessAccount,
      String entityRef, String branchName, String connectorRef, String repoName) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branchName).connectorRef(connectorRef).repoName(repoName).build());
    EntityConvertResponse entityConvertResponse =
        catalogService.convertEntity(harnessAccount, option, body, entityRef, false);
    return Response.ok().entity(entityConvertResponse).build();
  }

  @Override
  public Response createEntity(@Valid EntityCreateRequest body, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, Boolean convert,
      Boolean dryRun) {
    idpCommonService.idpV2Check(harnessAccount);
    GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitCreateDetails(body.getGitDetails()));
    EntityResponse entityResponse =
        catalogService.createEntity(harnessAccount, orgIdentifier, projectIdentifier, convert, dryRun, body);
    return Response.status(Response.Status.CREATED).entity(entityResponse).build();
  }

  @Override
  public Response importEntity(@Valid GitImportDetails body, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    idpCommonService.idpV2Check(harnessAccount);
    GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitImportDetails(body));
    EntityResponse entityResponse = catalogService.importEntity(harnessAccount, orgIdentifier, projectIdentifier);
    return Response.status(Response.Status.CREATED).entity(entityResponse).build();
  }

  @Override
  public Response migrateEntities(@Valid EntitiesMigrateRequest body, @AccountIdentifier String harnessAccount) {
    idpCommonService.idpV2Check(harnessAccount);
    catalogService.migrateEntities(harnessAccount, body);
    return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
  }

  @Override
  public Response moveEntity(@Valid EntityMoveRequest body, String scope, String kind, String identifier,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    idpCommonService.idpV2Check(harnessAccount);
    catalogService.moveEntity(
        harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier, body);
    return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
  }

  @Override
  public Response deleteEntity(String scope, String kind, String identifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @AccountIdentifier String harnessAccount,
      Boolean deleteHierarchyKindEntity) {
    idpCommonService.idpV2Check(harnessAccount);
    catalogService.deleteEntity(harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier,
        deleteHierarchyKindEntity);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response deleteEntityVersion(String scope, String kind, String identifier, String version,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    catalogService.deleteEntity(
        harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier, version, true, false);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getEntities(@AccountIdentifier String harnessAccount, Integer page, @Max(100L) Integer limit,
      String sort, String searchTerm, Boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe,
      Boolean favorites, String kind, String type, String owner, String lifecycle, String tags, String filter) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    boolean useOptimizedPath =
        isEmpty(entityRefs) && idpCommonService.idpEntityListOptimizedPathEnabled(harnessAccount);
    GetEntitiesDTO getEntitiesDTO = useOptimizedPath
        ? catalogService.getEntitiesV2(harnessAccount, page, pageLimit, sort, searchTerm, resolvePlaceholders, scopes,
              entityRefs, ownedByMe, favorites, isEmpty(kind) ? V1_DEFAULT_KIND_FILTER : kind, type, owner, lifecycle,
              tags, filter, true, false, null)
        : catalogService.getEntities(harnessAccount, page, pageLimit, sort, searchTerm, resolvePlaceholders, scopes,
              entityRefs, ownedByMe, favorites, kind, type, owner, lifecycle, tags, filter, true);
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
    responseBuilderWithLinks.header("Total-Owned", getEntitiesDTO.getTotalOwned());
    responseBuilderWithLinks.header("Total-Starred", getEntitiesDTO.getTotalStarred());
    return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
  }

  @Override
  public Response getEntitiesByRefs(@Valid EntitiesByRefsRequest body, @AccountIdentifier String harnessAccount,
      Integer page, @Max(100L) Integer limit, String sort, String searchTerm, String scopes, Boolean ownedByMe,
      Boolean favorites, String kind, String type, String owner, String lifecycle, String tags, String filter,
      Boolean resolvePlaceholders) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    GetEntitiesDTO getEntitiesDTO = catalogService.getEntities(harnessAccount, page, pageLimit, sort, searchTerm,
        resolvePlaceholders, scopes, String.join(",", body.getEntityRefs()), ownedByMe, favorites, kind, type, owner,
        lifecycle, tags, filter, true, true);
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
    responseBuilderWithLinks.header("Total-Owned", getEntitiesDTO.getTotalOwned());
    responseBuilderWithLinks.header("Total-Starred", getEntitiesDTO.getTotalStarred());
    return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
  }

  @Override
  public Response getEntitiesFilters(@NotNull String accountIdentifier, String kind, String scopes, String filter,
      @AccountIdentifier String harnessAccount) {
    List<EntityFiltersResponse> entityKindsResponses =
        catalogService.getEntitiesFilters(accountIdentifier, scopes, kind, filter);
    return Response.ok().entity(entityKindsResponses).build();
  }

  @Override
  public Response getEntitiesFiltersByQuery(@NotNull String accountIdentifier, @Valid EntityFilterQueryRequest body,
      @AccountIdentifier String harnessAccount, String kind, String scopes, String filter) {
    List<EntityFiltersResponse> entityKindsResponses = catalogService.getEntitiesFiltersByRefs(
        accountIdentifier, String.join(",", body.getEntityRefs()), kind, filter);
    return Response.ok().entity(entityKindsResponses).build();
  }

  @Override
  public Response getEntitiesGroups(@AccountIdentifier String harnessAccount, String searchOnEntities,
      String searchOnGroups, String scopes, String kind, Boolean ownedByMe, Boolean favorites, String type,
      String owner, String lifecycle, String tags) {
    GetEntitiesGroupsDTO getEntitiesGroupsDTO = catalogService.getEntitiesGroups(harnessAccount, searchOnEntities,
        searchOnGroups, scopes, kind, ownedByMe, favorites, type, owner, lifecycle, tags);
    Response.ResponseBuilder responseBuilder = Response.ok();
    responseBuilder.header("Total-Owned", getEntitiesGroupsDTO.getTotalOwned());
    responseBuilder.header("Total-Starred", getEntitiesGroupsDTO.getTotalStarred());
    return responseBuilder.entity(getEntitiesGroupsDTO.getEntitiesGroupsResponse()).build();
  }

  @Override
  public Response getEntitiesKinds(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @AccountIdentifier String harnessAccount) {
    List<EntityKindsResponse> entityKindsResponses =
        catalogService.getEntitiesKinds(accountIdentifier, orgIdentifier, projectIdentifier);
    return Response.ok().entity(entityKindsResponses).build();
  }

  @Override
  public Response getEntity(String scope, String kind, String identifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @AccountIdentifier String harnessAccount, String branchName,
      String connectorRef, String repoName, String loadFromCache, Boolean loadFromFallbackBranch,
      Boolean resolvePlaceholders) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder().branch(branchName).connectorRef(connectorRef).repoName(repoName).build());
    EntityResponse entityResponse = catalogService.getEntity(harnessAccount, orgIdentifier, projectIdentifier,
        kind + ":" + scope + "/" + identifier, Boolean.TRUE.equals(resolvePlaceholders),
        Boolean.TRUE.equals(loadFromFallbackBranch), GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache));
    return Response.ok().entity(entityResponse).build();
  }

  @Override
  public Response updateEntity(@Valid EntityUpdateRequest body, String scope, String kind, String identifier,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    idpCommonService.idpV2Check(harnessAccount);
    GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitUpdateDetails(body.getGitDetails()));
    EntityResponse entityResponse = catalogService.updateEntity(
        harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier, body);
    return Response.status(Response.Status.OK).entity(entityResponse).build();
  }

  @Override
  public Response updateGitMetadata(@Valid GitMetadataUpdateRequest body, String scope, String kind, String identifier,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    idpCommonService.idpV2Check(harnessAccount);
    catalogService.updateGitMetadata(
        harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier, body);
    return Response.status(Response.Status.OK).entity(new DefaultSaveResponse().status(SUCCESS_RESPONSE)).build();
  }

  @Override
  public Response validateYaml(@Valid EntityValidateRequest body, @AccountIdentifier String harnessAccount) {
    idpCommonService.idpV2Check(harnessAccount);
    List<EntityValidateResponse> entityValidateResponses = catalogService.validateYaml(harnessAccount, body);
    return Response.status(Response.Status.OK).entity(entityValidateResponses).build();
  }

  @Override
  @PublicApi
  public Response getJsonSchema(String kind) {
    String jsonSchema = catalogService.getJsonSchema(kind);
    return Response.status(Response.Status.OK).entity(jsonSchema).build();
  }

  @Override
  public Response getWorkflowExecutionHistory(@Valid WorkflowExecutionHistoryRequest body,
      @AccountIdentifier String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      Boolean myExecutions) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    return catalogService.getWorkflowExecutionHistory(harnessAccount, body.getEntityRefs(), myExecutions,
        body.getStatus(), body.getStart(), body.getEnd(), searchTerm, sort, pageIndex, pageLimit);
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response syncCatalogEntities(
      String option, @Valid CatalogSyncRequest body, @AccountIdentifier String harnessAccount) {
    catalogService.syncCatalogEntities(harnessAccount, option, body);
    return Response.status(Response.Status.OK).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response createOrUpdateEntityTable(
      @Valid EntityTableCreateOrUpdateRequest body, @AccountIdentifier String harnessAccount, String kind) {
    idpCommonService.idpV2Check(harnessAccount);
    EntityTableResponse response = catalogTableService.createOrUpdateEntityTable(body, harnessAccount, kind);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getEntityTables(@AccountIdentifier String harnessAccount, String kind) {
    idpCommonService.idpV2Check(harnessAccount);
    List<EntityTableResponse> responses = catalogTableService.getEntityTables(harnessAccount, kind);
    return Response.status(Response.Status.OK).entity(responses).build();
  }

  @Override
  public Response createEntityVersion(@Valid EntityVersionCreateRequest body, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    EntityVersionResponse response =
        catalogService.createEntity(harnessAccount, orgIdentifier, projectIdentifier, false, false, null, body, true)
            .getRight();
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Override
  public Response getEntityVersion(String scope, String kind, String identifier, String version,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    EntityVersionResponse response = catalogVersionService.getEntityVersion(
        harnessAccount, orgIdentifier, projectIdentifier, scope, kind, identifier, version);

    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getEntityVersions(String scope, String kind, String identifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @AccountIdentifier String harnessAccount, Integer page,
      @Max(100L) Integer limit, String searchTerm, Boolean deprecated) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;

    GetEntityVersionsDTO getEntityVersionsDTO = catalogVersionService.getEntityVersions(harnessAccount, orgIdentifier,
        projectIdentifier, scope, kind, identifier, page, pageLimit, searchTerm, deprecated);

    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, getEntityVersionsDTO.getTotalElements(), getEntityVersionsDTO.getPageNumber(), pageLimit);

    return responseBuilderWithLinks.entity(getEntityVersionsDTO.getEntityVersionResponses()).build();
  }

  @Override
  public Response updateEntityVersion(@Valid EntityVersionUpdateRequest body, String scope, String kind,
      String identifier, String version, @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    EntityVersionResponse response =
        catalogService
            .updateEntity(harnessAccount, orgIdentifier, projectIdentifier, kind + ":" + scope + "/" + identifier, null,
                true, false, false, body, true, version, false)
            .getRight();
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getEnvironmentBlueprintInfo(
      @Valid EnvironmentBlueprintInfoRequest body, @AccountIdentifier String harnessAccount) {
    List<EnvironmentBluePrintInfoResponse> environmentBluePrintInfoResponses =
        catalogService.getEnvironmentBlueprintInfo(harnessAccount, body.getEnvironmentBlueprintIdentifiers());
    return Response.status(Response.Status.OK).entity(environmentBluePrintInfoResponses).build();
  }

  @Override
  public Response getBlueprintEnvironments(String identifier, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, Integer page,
      @Max(100L) Integer limit, String sort, String searchTerm) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    GetEntitiesDTO getEntitiesDTO = catalogService.getEnvironmentsByBlueprintIdentifier(
        harnessAccount, orgIdentifier, projectIdentifier, identifier, page, pageLimit, sort, searchTerm);
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
    responseBuilderWithLinks.header("Total-Owned", getEntitiesDTO.getTotalOwned());
    responseBuilderWithLinks.header("Total-Starred", getEntitiesDTO.getTotalStarred());
    return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
  }

  @Override
  public Response getEntityAssociations(String kind, String identifier, @AccountIdentifier String harnessAccount,
      String relations, String orgIdentifier, String projectIdentifier, Integer page, @Max(100L) Integer limit,
      String sort, String searchTerm, Boolean ownedByMe, Boolean favorites, String associationKind, String type,
      String owner, String lifecycle, String tags, String filter) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    GetEntitiesDTO getEntitiesDTO = catalogService.getEntityAssociations(harnessAccount, orgIdentifier,
        projectIdentifier, kind, identifier, relations, page, pageLimit, sort, searchTerm, ownedByMe, favorites,
        associationKind, type, owner, lifecycle, tags, filter);
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
    responseBuilderWithLinks.header("Total-Owned", getEntitiesDTO.getTotalOwned());

    responseBuilderWithLinks.header("Total-Starred", getEntitiesDTO.getTotalStarred());
    return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
  }

  @Override
  public Response getEntityContent(String scope, String kind, String identifier, String path, String orgIdentifier,
      String projectIdentifier, @AccountIdentifier String harnessAccount) {
    if (isEmpty(path)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("path query parameter is required").build();
    }

    Pair<String, String> content = catalogService.getEntityContent(
        harnessAccount, orgIdentifier, projectIdentifier, scope, kind, identifier, path);
    return Response.ok(content.getLeft()).header("Content-Type", content.getRight()).build();
  }

  @Override
  public Response submitBulkUpdateEntityField(
      @Valid BulkEntityFieldUpdateRequest body, @AccountIdentifier String harnessAccount) {
    log.info("Bulk field update submit request received for account: {}, properties: {}", harnessAccount,
        body.getProperties() != null ? body.getProperties().size() : 0);
    BulkFieldUpdateSubmitResponse response = bulkEntityFieldUpdateService.submit(body, harnessAccount);
    log.info("Bulk field update submitted for account: {}, operationId: {}, status: {}, matched: {}, permitted: {}",
        harnessAccount, response.getOperationId(), response.getStatus(), response.getMatched(),
        response.getPermitted());
    return Response.status(202).entity(response).build();
  }

  @Override
  public Response getBulkUpdateEntityFieldOperation(String operationId, @AccountIdentifier String harnessAccount) {
    log.info("Get bulk field update operation: account: {}, operationId: {}", harnessAccount, operationId);
    BulkFieldUpdateOperationResponse response = bulkEntityFieldUpdateService.getOperation(harnessAccount, operationId);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response syncApiEndpoints(String scope, String kind, String identifier, String orgIdentifier,
      String projectIdentifier, @AccountIdentifier String harnessAccount) {
    idpCommonService.idpV2Check(harnessAccount);
    try {
      ApiEndpointSyncResponse response =
          apiEndpointSyncService.sync(harnessAccount, orgIdentifier, projectIdentifier, kind, identifier);
      return Response.ok().entity(response).build();
    } catch (ApiEndpointSyncInProgressException e) {
      return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
    } catch (ApiEndpointSyncFailedException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
  }

  @Override
  public Response resolveEntityApiDefinition(String scope, String kind, String identifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @AccountIdentifier String harnessAccount, String branchName, String loadFromCache,
      Boolean loadFromFallbackBranch) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
    EntityResponse resolved = apiDefinitionResolver.resolve(catalogServiceHelper.getCatalogEntityWithRbac(
        harnessAccount, kind, scope, identifier, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache),
        loadFromFallbackBranch == null || loadFromFallbackBranch));
    return Response.ok().entity(resolved).build();
  }
}
