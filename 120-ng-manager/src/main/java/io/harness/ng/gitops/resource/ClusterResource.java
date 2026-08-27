/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.resource;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.String.format;
import static org.jooq.tools.StringUtils.defaultIfEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.beans.ClusterBatchRequest;
import io.harness.cdng.gitops.beans.ClusterBatchResponse;
import io.harness.cdng.gitops.beans.ClusterBulkOperationResult;
import io.harness.cdng.gitops.beans.ClusterFromGitops;
import io.harness.cdng.gitops.beans.ClusterLinkRequest;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.beans.ClusterUnlinkRequest;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.mappers.ClusterEntityMapper;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@NextGenManagerAuth
@Api("/gitops/clusters")
@Path("/gitops/clusters")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Clusters", description = "This contains APIs related to Gitops Clusters")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@Slf4j
public class ClusterResource {
  @Inject private ClusterService clusterService;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private EnvironmentValidationHelper environmentValidationHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ScopeInfoService scopeInfoService;

  private static final String CLUSTER_PARAM_MESSAGE = "Cluster Identifier for the entity";
  private static final int UNLIMITED_PAGE_SIZE = 10000;
  private static final int DEFAULT_PAGE_SIZE = 1000;

  @GET
  @Path("{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets a Cluster by identifier", nickname = "getCluster")
  @Operation(operationId = "getCluster", summary = "Get a Cluster linked to an environment by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "The saved Cluster") })
  public ResponseDTO<ClusterResponse>
  get(@Parameter(description = CLUSTER_PARAM_MESSAGE) @PathParam("identifier") @ResourceIdentifier String clusterRef,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @NotEmpty String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.AGENT_KEY) @QueryParam(
          NGCommonEntityConstants.AGENT_KEY) String agentIdentifier,
      @Parameter(description = "Specify whether cluster is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, ENVIRONMENT_VIEW_PERMISSION, "view");

    Optional<Cluster> entity = clusterService.get(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, agentIdentifier, clusterRef);
    if (entity.isEmpty()) {
      throw new NotFoundException(format("Cluster with clusterRef [%s] in project [%s], org [%s] not found", clusterRef,
          projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(entity.map(ClusterEntityMapper::writeDTO).orElse(null));
  }

  @POST
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Link a gitops cluster to an environment", nickname = "linkCluster")
  @Operation(operationId = "linkCluster", summary = "Link a Cluster",
      description = "Link a GitOps cluster to an environment by identifier",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the linked Cluster") })
  public ResponseDTO<ClusterResponse>
  link(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the createCluster to be linked") @Valid ClusterRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(accountId, request.getOrgIdentifier(), request.getProjectIdentifier());

    // TODO: add validation that cluster scope can not be higher than env scope
    Cluster entity = ClusterEntityMapper.toEntity(accountId, request, scopeInfo);

    Cluster created = clusterService.create(entity);
    return ResponseDTO.newResponse(ClusterEntityMapper.writeDTO(created));
  }

  @POST
  @Path("/batch")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Link gitops clusters to an environment", nickname = "linkClusters")
  @Operation(operationId = "linkClusters", summary = "Link Clusters",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the linked Clusters") })
  public ResponseDTO<ClusterBatchResponse>
  linkBatch(@Parameter(description = "Account Identifier of the environment.") @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Request body for linking clusters to an environment. "
              + "Either provide a list of clusters in the clusters field "
              + "OR use linkAllClusters=true with searchTerm to link all matching clusters.")
      @Valid ClusterLinkRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    if (!request.isLinkAllClusters() && isNotEmpty(request.getClusters())) {
      validateClustersExist(accountId, request);
    }

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    // Convert to original DTO to maintain backward compatibility with existing business logic
    ClusterBatchRequest batchRequest = toClusterBatchRequest(request);

    List<Cluster> entities = new ArrayList<>();
    if (!batchRequest.isLinkAllClusters()) {
      // TODO: add validation that cluster scope can not be higher than env scope
      entities = ClusterEntityMapper.toEntities(accountId, batchRequest);
    } else {
      // link all according search term from account level
      PageResponse<ClusterFromGitops> accountLevelClusters =
          fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, "", "", batchRequest.getSearchTerm());
      entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
          batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), accountLevelClusters.getContent()));

      // link all according to search term from org level
      if (isNotEmpty(batchRequest.getOrgIdentifier())) {
        PageResponse<ClusterFromGitops> orgLevelClusters = fetchClustersFromGitopsService(
            0, UNLIMITED_PAGE_SIZE, accountId, batchRequest.getOrgIdentifier(), "", batchRequest.getSearchTerm());
        entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
            batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), orgLevelClusters.getContent()));
      }

      // link all according to search term from project level
      if (isNotEmpty(batchRequest.getOrgIdentifier()) && isNotEmpty(batchRequest.getProjectIdentifier())) {
        PageResponse<ClusterFromGitops> projectLevelClusters =
            fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, batchRequest.getOrgIdentifier(),
                batchRequest.getProjectIdentifier(), batchRequest.getSearchTerm());
        entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
            batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), projectLevelClusters.getContent()));
      }
    }
    long linked = 0;
    if (isNotEmpty(entities)) {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
          accountId, batchRequest.getOrgIdentifier(), batchRequest.getProjectIdentifier());
      entities = entities.stream()
                     .peek(cluster -> {
                       cluster.setUniqueId(UUIDGenerator.generateUuid());
                       cluster.setParentUniqueId(scopeInfo.getUniqueId());
                     })
                     .collect(Collectors.toList());
      linked = clusterService.bulkCreate(entities);
    }
    return ResponseDTO.newResponse(ClusterBatchResponse.builder().linked(linked).build());
  }

  @POST
  @Path("/batch/internal")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Link gitops clusters to an environment with detailed response",
      nickname = "linkClustersInternal", hidden = true)
  @Operation(operationId = "linkClustersInternal", summary = "Link Clusters with Detailed Response (Internal)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns detailed success and failure information for each cluster")
      },
      hidden = true)
  public ResponseDTO<ClusterBatchResponse>
  linkBatchInternal(@Parameter(description = "Account Identifier of the environment.") @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Request body for linking clusters to an environment. "
              + "Either provide a list of clusters in the clusters field "
              + "OR use linkAllClusters=true with searchTerm to link all matching clusters.")
      @Valid ClusterLinkRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "create");

    ClusterBatchRequest batchRequest = toClusterBatchRequest(request);

    // Pre-validation failures that will be added to failed array
    List<ClusterBatchResponse.ClusterOperationResult> validationFailures = new ArrayList<>();
    List<Cluster> entities = new ArrayList<>();
    // Store fetched clusters for name enrichment (avoid duplicate GitOps calls)
    Map<String, ClusterFromGitops> fetchedClustersForEnrichment = new HashMap<>();

    if (!batchRequest.isLinkAllClusters()) {
      entities =
          validateAndFilterClusters(accountId, request, batchRequest, validationFailures, fetchedClustersForEnrichment);
    } else {
      PageResponse<ClusterFromGitops> accountLevelClusters =
          fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, "", "", batchRequest.getSearchTerm());
      entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
          batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), accountLevelClusters.getContent()));
      // Store for name enrichment to avoid duplicate calls
      accountLevelClusters.getContent().forEach(c
          -> fetchedClustersForEnrichment.put(
              c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.ACCOUNT, c));
      if (isNotEmpty(batchRequest.getOrgIdentifier())) {
        PageResponse<ClusterFromGitops> orgLevelClusters = fetchClustersFromGitopsService(
            0, UNLIMITED_PAGE_SIZE, accountId, batchRequest.getOrgIdentifier(), "", batchRequest.getSearchTerm());
        entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
            batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), orgLevelClusters.getContent()));
        // Store for name enrichment to avoid duplicate calls
        orgLevelClusters.getContent().forEach(c
            -> fetchedClustersForEnrichment.put(
                c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.ORGANIZATION, c));
      }
      if (isNotEmpty(batchRequest.getOrgIdentifier()) && isNotEmpty(batchRequest.getProjectIdentifier())) {
        PageResponse<ClusterFromGitops> projectLevelClusters =
            fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, batchRequest.getOrgIdentifier(),
                batchRequest.getProjectIdentifier(), batchRequest.getSearchTerm());
        entities.addAll(ClusterEntityMapper.toEntities(accountId, batchRequest.getOrgIdentifier(),
            batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), projectLevelClusters.getContent()));
        // Store for name enrichment to avoid duplicate calls
        projectLevelClusters.getContent().forEach(c
            -> fetchedClustersForEnrichment.put(
                c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.PROJECT, c));
      }
    }
    ClusterBulkOperationResult result = ClusterBulkOperationResult.builder()
                                            .successfulClusters(new ArrayList<>())
                                            .failedClusters(new ArrayList<>())
                                            .build();

    if (isNotEmpty(entities)) {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
          accountId, batchRequest.getOrgIdentifier(), batchRequest.getProjectIdentifier());
      entities = entities.stream()
                     .peek(cluster -> {
                       cluster.setUniqueId(UUIDGenerator.generateUuid());
                       cluster.setParentUniqueId(scopeInfo.getUniqueId());
                     })
                     .collect(Collectors.toList());
      result = clusterService.bulkCreateInternal(entities);
    }

    List<ClusterBatchResponse.ClusterOperationResult> successResults = result.getSuccessfulClusters()
                                                                           .stream()
                                                                           .map(ClusterEntityMapper::toOperationResult)
                                                                           .collect(Collectors.toList());
    List<ClusterBatchResponse.ClusterOperationResult> failedResults =
        result.getFailedClusters().stream().map(ClusterEntityMapper::toOperationResult).collect(Collectors.toList());

    failedResults.addAll(validationFailures);

    //  Enrich cluster names from GitOps service (reuse fetched clusters to avoid duplicate calls)
    enrichClusterNamesFromGitops(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), successResults,
        failedResults, fetchedClustersForEnrichment);
    ClusterBatchResponse response = ClusterBatchResponse.builder()
                                        .linked(successResults.size())
                                        .success(successResults)
                                        .failed(failedResults)
                                        .build();

    return ResponseDTO.newResponse(response);
  }

  @POST
  @Path("/batchunlink")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Unlink gitops clusters to an environment", nickname = "unlinkClustersInBatch")
  @Operation(operationId = "unlinkClustersInBatch", summary = "Unlink Clusters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if all the Clusters are deleted")
      })
  public ResponseDTO<ClusterBatchResponse>
  unlinkBatch(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Request body for unlinking clusters from an environment. "
              + "Either provide a list of specific clusters OR use unlinkAllClusters=true to unlink all.")
      @Valid ClusterUnlinkRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "delete");

    ClusterBatchRequest batchRequest = toClusterBatchRequest(request);

    long unlinked;
    if (batchRequest.isUnlinkAllClusters()) {
      unlinked = clusterService.deleteAllFromEnvAndReturnCount(
          accountId, batchRequest.getOrgIdentifier(), batchRequest.getProjectIdentifier(), batchRequest.getEnvRef());
    } else {
      List<Cluster> entities = ClusterEntityMapper.toEntities(accountId, batchRequest);
      unlinked = isNotEmpty(entities) ? clusterService.bulkDelete(entities, accountId, batchRequest.getOrgIdentifier(),
                                            batchRequest.getProjectIdentifier(), batchRequest.getEnvRef())
                                      : 0;
    }
    return ResponseDTO.newResponse(ClusterBatchResponse.builder().unlinked(unlinked).build());
  }

  @POST
  @Path("/batchunlink/internal")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Unlink gitops clusters from an environment with detailed response",
      nickname = "unlinkClustersInBatchInternal", hidden = true)
  @Operation(operationId = "unlinkClustersInBatchInternal",
      summary = "Unlink Clusters with Detailed Response (Internal)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns detailed success and failure information for each cluster")
      },
      hidden = true)
  public ResponseDTO<ClusterBatchResponse>
  unlinkBatchInternal(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Request body for unlinking clusters from an environment. "
              + "Either provide a list of specific clusters OR use unlinkAllClusters=true to unlink all.")
      @Valid ClusterUnlinkRequest request) {
    throwExceptionForNoRequestDTO(request);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        request.getOrgIdentifier(), request.getProjectIdentifier(), accountId);
    environmentValidationHelper.checkThatEnvExists(
        accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef());

    checkForAccessOrThrow(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), request.getEnvRef(),
        ENVIRONMENT_UPDATE_PERMISSION, "delete");

    ClusterBatchRequest batchRequest = toClusterBatchRequest(request);

    // Fetch all clusters from the environment first, then use bulkDeleteInternal for detailed response
    List<Cluster> entities;
    if (batchRequest.isUnlinkAllClusters()) {
      Page<Cluster> allClusters =
          clusterService.list(0, UNLIMITED_PAGE_SIZE, accountId, batchRequest.getOrgIdentifier(),
              batchRequest.getProjectIdentifier(), batchRequest.getEnvRef(), null, null, null, null);
      entities = allClusters.getContent();
    } else {
      entities = ClusterEntityMapper.toEntities(accountId, batchRequest);
    }

    ClusterBulkOperationResult result = isNotEmpty(entities)
        ? clusterService.bulkDeleteInternal(entities, accountId, batchRequest.getOrgIdentifier(),
              batchRequest.getProjectIdentifier(), batchRequest.getEnvRef())
        : ClusterBulkOperationResult.builder()
              .successfulClusters(new ArrayList<>())
              .failedClusters(new ArrayList<>())
              .build();

    List<ClusterBatchResponse.ClusterOperationResult> successResults = result.getSuccessfulClusters()
                                                                           .stream()
                                                                           .map(ClusterEntityMapper::toOperationResult)
                                                                           .collect(Collectors.toList());
    List<ClusterBatchResponse.ClusterOperationResult> failedResults =
        result.getFailedClusters().stream().map(ClusterEntityMapper::toOperationResult).collect(Collectors.toList());

    // Enrich cluster names from GitOps service
    enrichClusterNamesFromGitops(accountId, request.getOrgIdentifier(), request.getProjectIdentifier(), successResults,
        failedResults, new HashMap<>());
    ClusterBatchResponse response = ClusterBatchResponse.builder()
                                        .unlinked(successResults.size())
                                        .success(successResults)
                                        .failed(failedResults)
                                        .build();

    return ResponseDTO.newResponse(response);
  }

  @DELETE
  @Path("{identifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Delete a Cluster by identifier", nickname = "deleteCluster")
  @Operation(operationId = "deleteCluster", summary = "Unlink a cluster by identifier",
      description = "Unlink a cluster from an environment by identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Cluster is deleted") })
  public ResponseDTO<Boolean> delete(
      @Parameter(description = CLUSTER_PARAM_MESSAGE) @PathParam("identifier") @ResourceIdentifier String clusterRef,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @NotEmpty String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.AGENT_KEY) @QueryParam(
          NGCommonEntityConstants.AGENT_KEY) String agentIdentifier,
      @Parameter(description = "Scope for the gitops cluster") @QueryParam("scope") ScopeLevel scope) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, ENVIRONMENT_UPDATE_PERMISSION, "delete");
    return ResponseDTO.newResponse(clusterService.delete(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, agentIdentifier, clusterRef, scope));
  }

  @GET
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets cluster list ", nickname = "getClusterList")
  @Operation(operationId = "getClusterList", summary = "Gets cluster list",
      description = "Gets a list of GitOps clusters linked to an environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of cluster for a Project")
      })
  public ResponseDTO<PageResponse<ClusterResponse>>
  list(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "Environment Identifier of the clusters", required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String envIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of cluster identifiers") @QueryParam("identifiers") List<String> identifiers,
      @Parameter(description = "Specifies the sorting criteria of the list. "
              + "Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending "
              + "order") @QueryParam("sort") List<String> sort,
      @Parameter(description = "Scope of linked clusters to be returned, ACCOUNT/ORGANIZATION/PROJECT. Returns all by "
              + "default if this is not specified") @QueryParam(NGResourceFilterConstants.SCOPE) ScopeLevel scope) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    checkForAccessOrThrow(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, ENVIRONMENT_VIEW_PERMISSION, "list");

    // NG Clusters
    Page<Cluster> entities = getClustersForEnvId(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, identifiers, sort, scope);

    // Initialize cluster collections
    PageResponse<ClusterFromGitops> accountLevelClusters = PageResponse.getEmptyPageResponse(null);
    PageResponse<ClusterFromGitops> orgLevelClusters = PageResponse.getEmptyPageResponse(null);
    PageResponse<ClusterFromGitops> projectLevelClusters = PageResponse.getEmptyPageResponse(null);

    // Account level clusters
    if (scope == null || scope == ScopeLevel.ACCOUNT) {
      accountLevelClusters = fetchClustersFromGitopsService(0, DEFAULT_PAGE_SIZE, accountId, "", "", searchTerm);
    }

    // Org level clusters
    if (scope == null || scope == ScopeLevel.ORGANIZATION) {
      orgLevelClusters = fetchClustersFromGitopsService(0, DEFAULT_PAGE_SIZE, accountId, orgIdentifier, "", searchTerm);
    }

    // Project level clusters
    if (scope == null || scope == ScopeLevel.PROJECT) {
      projectLevelClusters =
          fetchClustersFromGitopsService(0, DEFAULT_PAGE_SIZE, accountId, orgIdentifier, projectIdentifier, searchTerm);
    }

    Map<String, ClusterFromGitops> allClusters =
        Stream.of(accountLevelClusters.getContent(), orgLevelClusters.getContent(), projectLevelClusters.getContent())
            .flatMap(List::stream)
            .collect(Collectors.toMap(e
                -> ClusterEntityMapper.generateClusterIdentifierKey(
                    getScope(e.getScopeLevel()) + "." + e.getIdentifier(), e.getAgentIdentifier(), accountId,
                    orgIdentifier, projectIdentifier),
                Function.identity(), (c1, c2) -> c1));
    return ResponseDTO.newResponse(getNGPageResponse(entities.map(e -> ClusterEntityMapper.writeDTO(e, allClusters))));
  }

  private String getScope(ScopeLevel scopeLevel) {
    // For Organization scoped clusters, the prefix used is "org"
    if (ScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return ClusterEntityMapper.ORG;
    }
    // For Account and Project scoped clusters, the prefix used is the scope itself
    return scopeLevel.toString().toLowerCase();
  }

  @GET
  @Path("/listFromGitops")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets cluster list from Gitops Service ", nickname = "getClusterListFromSource")
  @Hidden
  public ResponseDTO<PageResponse<ClusterFromGitops>> listFromGitopsService(
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "If true, returns cluster list based on the context(acc/org/project) passed in request. "
              + "Else will aggregate from account and project levels.") @QueryParam("scoped") @DefaultValue("false")
      boolean scoped) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);

    if (scoped) {
      // Instead of aggregating from all the levels, we will send the clusters as per the level/context passed.
      return ResponseDTO.newResponse(
          fetchClustersFromGitopsService(page, size, accountId, orgIdentifier, projectIdentifier, searchTerm));
    }
    // Account level clusters
    PageResponse<ClusterFromGitops> accountLevelClusters =
        fetchClustersFromGitopsService(page, size, accountId, "", "", searchTerm);
    // check number of project level clusters
    PageResponse<ClusterFromGitops> projectLevelClusterSample =
        fetchClustersFromGitopsService(0, 1, accountId, orgIdentifier, projectIdentifier, searchTerm);

    final long totalItems = accountLevelClusters.getTotalItems() + projectLevelClusterSample.getTotalItems();
    final long totalCombinedPages = totalItems % size == 0 ? totalItems / size : totalItems / size + 1;

    if (accountLevelClusters.getContent().size() == size) {
      return ResponseDTO.newResponse(
          accountLevelClusters.but().totalPages(totalCombinedPages).totalItems(totalItems).build());
    }

    int leftOverSpace = size - accountLevelClusters.getContent().size();
    int newPageIndex = (int) (leftOverSpace == size ? page - accountLevelClusters.getTotalPages()
                                                    : 1 + page - accountLevelClusters.getTotalPages());
    // Project level clusters
    PageResponse<ClusterFromGitops> projectLevelClusters = fetchClustersFromGitopsService(
        newPageIndex, leftOverSpace, accountId, orgIdentifier, projectIdentifier, searchTerm);

    PageResponse<ClusterFromGitops> result = PageResponse.getEmptyPageResponse(null);
    result.setEmpty(accountLevelClusters.isEmpty() && projectLevelClusters.isEmpty());
    result.setPageIndex(page);
    result.setTotalPages(totalCombinedPages);
    result.setContent(Stream.of(accountLevelClusters.getContent(), projectLevelClusters.getContent())
                          .flatMap(List::stream)
                          .collect(Collectors.toList()));
    result.setPageSize(size);
    result.setPageItemCount(result.getContent().size());
    result.setTotalItems(totalItems);

    return ResponseDTO.newResponse(result);
  }

  @VisibleForTesting
  PageResponse<ClusterFromGitops> fetchClustersFromGitopsService(
      int page, int size, String accountId, String orgIdentifier, String projectIdentifier, String searchTerm) {
    final PageResponse<ClusterFromGitops> clusters;
    final ClusterQuery query = ClusterQuery.builder()
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .pageSize(size)
                                   .pageIndex(page)
                                   .searchTerm(searchTerm)
                                   .build();

    final Response<PageResponse<io.harness.gitops.models.Cluster>> clusterResponse;
    try {
      clusterResponse = gitopsResourceClient.listClusters(query).execute();
      if (!clusterResponse.isSuccessful()) {
        handleFailureResponse(clusterResponse);
      }
      if (clusterResponse.body() == null) {
        handleFailureResponse(clusterResponse);
      }
      ScopeLevel scopeLevel = ScopeLevel.of(accountId, orgIdentifier, projectIdentifier);
      if (clusterResponse.body().isEmpty()) {
        clusterResponse.body().setContent(new ArrayList<>());
      }
      clusters = clusterResponse.body().map(c -> ClusterEntityMapper.writeDTO(scopeLevel, c));
    } catch (IOException io) {
      throw new InvalidRequestException("failed to fetch cluster list from gitops", io);
    }
    return clusters;
  }

  private void handleFailureResponse(Response<?> response) {
    String errorBody = null;
    try {
      errorBody = response.errorBody().string();
    } catch (Exception e) {
      log.error("Could not read error body {}", response.errorBody(), e);
    }
    if (isEmpty(errorBody) && response.body() == null) {
      errorBody = "No clusters found in gitops";
    }
    throw new InvalidRequestException(
        String.format("Failed to list clusters from gitops service. %s", defaultIfEmpty(errorBody, "")));
  }

  private void throwExceptionForNoRequestDTO(Object dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: "
          + "name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s gitops cluster(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }

  private Page<Cluster> getClustersForEnvId(int page, int size, String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String searchTerm, Collection<String> identifiers,
      List<String> sort, ScopeLevel scope) {
    String[] strings = envIdentifier.split("\\.");
    if (strings.length == 2) {
      projectIdentifier = null;
      envIdentifier = strings[1];
      if (strings[0].equals("account")) {
        orgIdentifier = null;
      }
    } else if (strings.length != 1) {
      throw new InvalidRequestException("Environment identifier cannot contain dots.");
    }
    return clusterService.list(
        page, size, accountId, orgIdentifier, projectIdentifier, envIdentifier, searchTerm, identifiers, sort, scope);
  }

  // Conversion method to maintain backward compatibility with existing business logic
  private ClusterBatchRequest toClusterBatchRequest(ClusterLinkRequest request) {
    return ClusterBatchRequest.builder()
        .orgIdentifier(request.getOrgIdentifier())
        .projectIdentifier(request.getProjectIdentifier())
        .envRef(request.getEnvRef())
        .clusters(request.getClusters())
        .linkAllClusters(request.isLinkAllClusters())
        .searchTerm(request.getSearchTerm())
        .build();
  }

  // Conversion method to maintain backward compatibility with existing business logic
  private ClusterBatchRequest toClusterBatchRequest(ClusterUnlinkRequest request) {
    return ClusterBatchRequest.builder()
        .orgIdentifier(request.getOrgIdentifier())
        .projectIdentifier(request.getProjectIdentifier())
        .envRef(request.getEnvRef())
        .clusters(request.getClusters())
        .unlinkAllClusters(request.isUnlinkAllClusters())
        .build();
  }

  /**
   * Validates and filters clusters for linking.
   * Performs two validations:
   * 1. Cluster existence in GitOps (if skipClusterValidation=false)
   * 2. Cluster scope hierarchy (always)
   *
   * Returns list of valid cluster entities to link.
   * Adds validation failures to the validationFailures list.
   * Populates fetchedClustersMap for later name enrichment to avoid duplicate GitOps calls.
   */
  private List<Cluster> validateAndFilterClusters(String accountId, ClusterLinkRequest request,
      ClusterBatchRequest batchRequest, List<ClusterBatchResponse.ClusterOperationResult> validationFailures,
      Map<String, ClusterFromGitops> fetchedClustersMap) {
    if (request.getClusters() == null || request.getClusters().isEmpty()) {
      return new ArrayList<>();
    }

    // Determine environment scope for hierarchy validation
    ScopeLevel envScope = ScopeLevel.of(accountId, request.getOrgIdentifier(), request.getProjectIdentifier());

    Map<String, ClusterFromGitops> existingClustersMap = null;
    if (!request.isSkipClusterValidation()) {
      existingClustersMap = fetchExistingClustersMap(accountId, request);
      // Store for later name enrichment to avoid duplicate calls
      fetchedClustersMap.putAll(existingClustersMap);
    }

    List<Cluster> validEntities = new ArrayList<>();

    for (ClusterBatchRequest.ClusterBasicDTO clusterDTO : request.getClusters()) {
      if (!isClusterScopeValidForEnv(envScope, clusterDTO.getScope())) {
        validationFailures.add(
            ClusterBatchResponse.ClusterOperationResult.builder()
                .clusterRef(getScopedClusterRef(clusterDTO.getScope(), clusterDTO.getIdentifier()))
                .agentIdentifier(clusterDTO.getAgentIdentifier())
                .name(null) // Will be enriched later if possible
                .failureReason(format("Cluster '%s' with scope '%s' cannot be linked to environment with scope '%s'. "
                        + "Cluster scope must be equal to or wider than environment scope.",
                    clusterDTO.getIdentifier(), clusterDTO.getScope(), envScope))
                .errorCode("INVALID_SCOPE_HIERARCHY")
                .build());
        continue; // Skip this cluster
      }

      // Validation 2: Check existence in GitOps (only if not skipped)
      if (existingClustersMap != null) {
        String clusterKey =
            clusterDTO.getIdentifier() + ":" + clusterDTO.getAgentIdentifier() + ":" + clusterDTO.getScope();
        ClusterFromGitops gitopsCluster = existingClustersMap.get(clusterKey);

        if (gitopsCluster == null) {
          validationFailures.add(
              ClusterBatchResponse.ClusterOperationResult.builder()
                  .clusterRef(getScopedClusterRef(clusterDTO.getScope(), clusterDTO.getIdentifier()))
                  .agentIdentifier(clusterDTO.getAgentIdentifier())
                  .name(null)
                  .failureReason(format("Cluster '%s' (agent: %s) does not exist in GitOps service at scope '%s'",
                      clusterDTO.getIdentifier(), clusterDTO.getAgentIdentifier(), clusterDTO.getScope()))
                  .errorCode("CLUSTER_NOT_FOUND_IN_GITOPS")
                  .build());
          continue; // Skip this cluster
        }
      }

      // Cluster passed all validations - create entity
      Cluster entity = Cluster.builder()
                           .accountId(accountId)
                           .orgIdentifier(batchRequest.getOrgIdentifier())
                           .projectIdentifier(batchRequest.getProjectIdentifier())
                           .agentIdentifier(clusterDTO.getAgentIdentifier())
                           .envRef(batchRequest.getEnvRef())
                           .clusterRef(getScopedClusterRef(clusterDTO.getScope(), clusterDTO.getIdentifier()))
                           // Note: uniqueId and parentUniqueId will be set later in the main flow
                           .build();

      validEntities.add(entity);
    }

    return validEntities;
  }

  /**
   * Fetches existing clusters from GitOps service and builds a map for lookup.
   * Key format: "identifier:agentIdentifier:scope" for simple lookup
   */
  private Map<String, ClusterFromGitops> fetchExistingClustersMap(String accountId, ClusterLinkRequest request) {
    Map<String, ClusterFromGitops> clusterMap = new HashMap<>();

    // Determine which scopes to fetch
    Set<ScopeLevel> requiredScopes =
        request.getClusters().stream().map(ClusterBatchRequest.ClusterBasicDTO::getScope).collect(Collectors.toSet());

    // Fetch from required scopes and store with simple key format
    if (requiredScopes.contains(ScopeLevel.ACCOUNT)) {
      List<ClusterFromGitops> accountClusters = fetchAllClustersFromScope(accountId, "", "");
      accountClusters.forEach(
          c -> clusterMap.put(c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.ACCOUNT, c));
    }

    if (requiredScopes.contains(ScopeLevel.ORGANIZATION) && isNotEmpty(request.getOrgIdentifier())) {
      List<ClusterFromGitops> orgClusters = fetchAllClustersFromScope(accountId, request.getOrgIdentifier(), "");
      orgClusters.forEach(
          c -> clusterMap.put(c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.ORGANIZATION, c));
    }

    if (requiredScopes.contains(ScopeLevel.PROJECT) && isNotEmpty(request.getOrgIdentifier())
        && isNotEmpty(request.getProjectIdentifier())) {
      List<ClusterFromGitops> projectClusters =
          fetchAllClustersFromScope(accountId, request.getOrgIdentifier(), request.getProjectIdentifier());
      projectClusters.forEach(
          c -> clusterMap.put(c.getIdentifier() + ":" + c.getAgentIdentifier() + ":" + ScopeLevel.PROJECT, c));
    }

    return clusterMap;
  }

  /**
   * Returns scoped cluster reference based on scope level.
   */
  private String getScopedClusterRef(ScopeLevel scope, String identifier) {
    if (scope == ScopeLevel.ACCOUNT) {
      return "account." + identifier;
    } else if (scope == ScopeLevel.ORGANIZATION) {
      return "org." + identifier;
    } else {
      return identifier; // PROJECT scope - no prefix
    }
  }

  private void validateClustersExist(String accountId, ClusterLinkRequest request) {
    if (request.getClusters() == null || request.getClusters().isEmpty()) {
      return;
    }

    // Determine which scopes are actually needed for validation
    Set<ScopeLevel> requiredScopes =
        request.getClusters().stream().map(ClusterBatchRequest.ClusterBasicDTO::getScope).collect(Collectors.toSet());

    // Conditionally fetch clusters only from required scopes
    Map<ScopeLevel, List<ClusterFromGitops>> clustersByScope = new HashMap<>();

    if (requiredScopes.contains(ScopeLevel.ACCOUNT)) {
      clustersByScope.put(ScopeLevel.ACCOUNT, fetchAllClustersFromScope(accountId, "", ""));
    }
    if (requiredScopes.contains(ScopeLevel.ORGANIZATION)) {
      clustersByScope.put(
          ScopeLevel.ORGANIZATION, fetchAllClustersFromScope(accountId, request.getOrgIdentifier(), ""));
    }
    if (requiredScopes.contains(ScopeLevel.PROJECT)) {
      clustersByScope.put(ScopeLevel.PROJECT,
          fetchAllClustersFromScope(accountId, request.getOrgIdentifier(), request.getProjectIdentifier()));
    }

    Map<ScopeLevel, Set<String>> clusterKeysByScope = new HashMap<>();
    for (Map.Entry<ScopeLevel, List<ClusterFromGitops>> entry : clustersByScope.entrySet()) {
      Set<String> keys = entry.getValue()
                             .stream()
                             .map(cluster -> cluster.getIdentifier() + ":" + cluster.getAgentIdentifier())
                             .collect(Collectors.toSet());
      clusterKeysByScope.put(entry.getKey(), keys);
    }

    List<String> invalidClusters = new ArrayList<>();

    // Validate each cluster in its requested scope
    for (ClusterBatchRequest.ClusterBasicDTO cluster : request.getClusters()) {
      String clusterKey = cluster.getIdentifier() + ":" + cluster.getAgentIdentifier();

      Set<String> relevantKeys = clusterKeysByScope.get(cluster.getScope());
      if (relevantKeys == null) {
        throw new InvalidRequestException("Invalid scope: " + cluster.getScope());
      }

      boolean exists = relevantKeys.contains(clusterKey);
      if (!exists) {
        invalidClusters.add(format("identifier='%s', agentIdentifier='%s', scope='%s'", cluster.getIdentifier(),
            cluster.getAgentIdentifier(), cluster.getScope()));
      }
    }

    if (!invalidClusters.isEmpty()) {
      throw new InvalidRequestException(
          format("The following clusters do not exist : [%s]", String.join(", ", invalidClusters)));
    }
  }

  private List<ClusterFromGitops> fetchAllClustersFromScope(String accountId, String orgId, String projectId) {
    List<ClusterFromGitops> allClusters = new ArrayList<>();
    int pageSize = DEFAULT_PAGE_SIZE;
    int currentPage = 0;

    while (true) {
      PageResponse<ClusterFromGitops> pageResponse =
          fetchClustersFromGitopsService(currentPage, pageSize, accountId, orgId, projectId, "");

      allClusters.addAll(pageResponse.getContent());

      if (pageResponse.getContent().size() < pageSize) {
        break;
      }

      currentPage++;
    }

    return allClusters;
  }

  /**
   * Enriches cluster operation results with cluster names from GitOps service.
   *
   * If preFetchedClusters is provided and not empty, it will be used first to avoid duplicate GitOps calls.
   * Otherwise, clusters will be fetched from GitOps service.
   *
   * NOTE: This method is fail-safe. If GitOps service is unavailable, it logs the error
   * and returns without enrichment rather than failing the entire operation.
   *
   * @param preFetchedClusters Map of clusters already fetched (key: "identifier:agentIdentifier:scope")
   */
  private void enrichClusterNamesFromGitops(String accountId, String orgIdentifier, String projectIdentifier,
      List<ClusterBatchResponse.ClusterOperationResult> successResults,
      List<ClusterBatchResponse.ClusterOperationResult> failedResults,
      Map<String, ClusterFromGitops> preFetchedClusters) {
    try {
      Map<String, ClusterFromGitops> clusterDetailsMap;

      // Use pre-fetched clusters if available, otherwise fetch from GitOps
      if (preFetchedClusters != null && !preFetchedClusters.isEmpty()) {
        // Convert simple key format to ClusterEntityMapper key format for enrichment lookup
        clusterDetailsMap = new HashMap<>();
        for (Map.Entry<String, ClusterFromGitops> entry : preFetchedClusters.entrySet()) {
          ClusterFromGitops cluster = entry.getValue();
          String clusterRef = getScopedClusterRef(cluster.getScopeLevel(), cluster.getIdentifier());
          String enrichmentKey = ClusterEntityMapper.generateClusterIdentifierKey(
              clusterRef, cluster.getAgentIdentifier(), accountId, orgIdentifier, projectIdentifier);
          clusterDetailsMap.put(enrichmentKey, cluster);
        }
      } else {
        PageResponse<ClusterFromGitops> accountLevelClusters =
            fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, "", "", "");

        PageResponse<ClusterFromGitops> orgLevelClusters = PageResponse.getEmptyPageResponse(null);
        if (isNotEmpty(orgIdentifier)) {
          orgLevelClusters = fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, orgIdentifier, "", "");
        }

        PageResponse<ClusterFromGitops> projectLevelClusters = PageResponse.getEmptyPageResponse(null);
        if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
          projectLevelClusters =
              fetchClustersFromGitopsService(0, UNLIMITED_PAGE_SIZE, accountId, orgIdentifier, projectIdentifier, "");
        }

        // Build a map using ClusterEntityMapper key format for enrichment
        clusterDetailsMap =
            Stream
                .of(accountLevelClusters.getContent(), orgLevelClusters.getContent(), projectLevelClusters.getContent())
                .flatMap(List::stream)
                .collect(Collectors.toMap(cluster -> {
                  String clusterRef = getScopedClusterRef(cluster.getScopeLevel(), cluster.getIdentifier());
                  return ClusterEntityMapper.generateClusterIdentifierKey(
                      clusterRef, cluster.getAgentIdentifier(), accountId, orgIdentifier, projectIdentifier);
                }, Function.identity(), (c1, c2) -> c1));
      }

      for (int i = 0; i < successResults.size(); i++) {
        ClusterBatchResponse.ClusterOperationResult result = successResults.get(i);
        String clusterKey = ClusterEntityMapper.generateClusterIdentifierKey(
            result.getClusterRef(), result.getAgentIdentifier(), accountId, orgIdentifier, projectIdentifier);

        ClusterFromGitops gitopsCluster = clusterDetailsMap.get(clusterKey);
        if (gitopsCluster != null && gitopsCluster.getName() != null) {
          // Create new instance with enriched name (since ClusterOperationResult is immutable @Value)
          successResults.set(i,
              ClusterBatchResponse.ClusterOperationResult.builder()
                  .clusterRef(result.getClusterRef())
                  .agentIdentifier(result.getAgentIdentifier())
                  .name(gitopsCluster.getName())
                  .failureReason(result.getFailureReason())
                  .errorCode(result.getErrorCode())
                  .build());
        }
      }

      for (int i = 0; i < failedResults.size(); i++) {
        ClusterBatchResponse.ClusterOperationResult result = failedResults.get(i);
        String clusterKey = ClusterEntityMapper.generateClusterIdentifierKey(
            result.getClusterRef(), result.getAgentIdentifier(), accountId, orgIdentifier, projectIdentifier);

        ClusterFromGitops gitopsCluster = clusterDetailsMap.get(clusterKey);
        if (gitopsCluster != null && gitopsCluster.getName() != null) {
          failedResults.set(i,
              ClusterBatchResponse.ClusterOperationResult.builder()
                  .clusterRef(result.getClusterRef())
                  .agentIdentifier(result.getAgentIdentifier())
                  .name(gitopsCluster.getName())
                  .failureReason(result.getFailureReason())
                  .errorCode(result.getErrorCode())
                  .build());
        }
      }
    } catch (Exception e) {
      // Fail-safe: If GitOps service is unavailable, log and continue without enrichment
      // This ensures the batch operation result is still returned even if name enrichment fails
      log.warn("Failed to enrich cluster names from GitOps service for account [{}], org [{}], project [{}]. "
              + "Returning results without cluster names. Error: {}",
          accountId, orgIdentifier, projectIdentifier, e.getMessage(), e);
    }
  }

  /**
   * Checks if a cluster scope is valid for an environment scope.
   * Cluster scope must be equal to or wider than environment scope.
   *
   * Rules:
   * - PROJECT env can link: PROJECT, ORGANIZATION, or ACCOUNT clusters
   * - ORGANIZATION env can link: ORGANIZATION or ACCOUNT clusters
   * - ACCOUNT env can link: ACCOUNT clusters only
   *
   * @param envScope Environment scope level
   * @param clusterScope Cluster scope level
   * @return true if cluster scope is valid for the environment, false otherwise
   */
  private boolean isClusterScopeValidForEnv(ScopeLevel envScope, ScopeLevel clusterScope) {
    switch (envScope) {
      case PROJECT:
        return true;
      case ORGANIZATION:
        return clusterScope == ScopeLevel.ORGANIZATION || clusterScope == ScopeLevel.ACCOUNT;
      case ACCOUNT:
        return clusterScope == ScopeLevel.ACCOUNT;
      default:
        return false;
    }
  }
}
