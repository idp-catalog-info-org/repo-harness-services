/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@NextGenManagerAuth
@Api("/environments")
@Path("/environments")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@ScopeInfoResolutionApi
@Deprecated
public class EnvironmentResource {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final EnvironmentRbacHelper environmentRbacHelper;
  private ScopeResolutionHelper scopeResolutionHelper;

  @GET
  @Path("{environmentIdentifier}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets a Environment by identifier", nickname = "getEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> get(@PathParam("environmentIdentifier") String environmentIdentifier,
      @QueryParam("accountId") String accountId, @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    Optional<Environment> envMetadata = useScopeInfo
        ? environmentService.getMetadata(scopeInfo, environmentIdentifier, false)
        : environmentService.getMetadata(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);
    if (envMetadata.isEmpty()) {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    environmentRbacHelper.checkForAccessOrThrow(getEnvironmentAttributes(envMetadata.get().getType()),
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), environmentIdentifier,
        ENVIRONMENT_VIEW_PERMISSION);
    Optional<Environment> environment;
    if (useScopeInfo) {
      environment = environmentService.get(scopeInfo, environmentIdentifier, deleted);

    } else {
      environment = environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, deleted);
    }

    return useScopeInfo
        ? ResponseDTO.newResponse(environment.map(env -> EnvironmentMapper.writeDTO(env, scopeInfo)).orElse(null))
        : ResponseDTO.newResponse(environment.map(EnvironmentMapper::writeDTO).orElse(null));
  }

  @POST
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create an Environment", nickname = "createEnvironment")
  public ResponseDTO<EnvironmentResponseDTO> create(
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    mustBeAtProjectLevel(environmentRequestDTO);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, getEnvironmentAttributes(environmentRequestDTO.getType())),
        ENVIRONMENT_CREATE_PERMISSION);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());
    Environment environmentEntity = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, scopeInfo);
    Environment createdEnvironment = environmentService.create(environmentEntity, scopeInfo).getEnvironment();
    boolean useScopeInfo = scopeInfo != null;

    return useScopeInfo ? ResponseDTO.newResponse(EnvironmentMapper.writeDTO(createdEnvironment, scopeInfo))
                        : ResponseDTO.newResponse(EnvironmentMapper.writeDTO(createdEnvironment));
  }

  @DELETE
  @Timed
  @ResponseMetered
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Delete en environment by identifier", nickname = "deleteEnvironment")
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @PathParam("environmentIdentifier") String environmentIdentifier, @QueryParam("accountId") String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    Optional<Environment> envMetadata = useScopeInfo
        ? environmentService.getMetadata(scopeInfo, environmentIdentifier, false)
        : environmentService.getMetadata(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);
    if (envMetadata.isEmpty()) {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    environmentRbacHelper.checkForAccessOrThrow(getEnvironmentAttributes(envMetadata.get().getType()),
        ResourceScope.of(accountId, orgIdentifier, projectIdentifier), environmentIdentifier,
        ENVIRONMENT_DELETE_PERMISSION);
    return useScopeInfo ? ResponseDTO.newResponse(environmentService.delete(
                              scopeInfo, environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, false))
                        : ResponseDTO.newResponse(environmentService.delete(accountId, orgIdentifier, projectIdentifier,
                              environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null, false));
  }

  @PUT
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Update an environment by identifier", nickname = "updateEnvironment")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentResponseDTO> update(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    mustBeAtProjectLevel(environmentRequestDTO);
    environmentRbacHelper.checkForAccessOrThrow(getEnvironmentAttributes(environmentRequestDTO.getType()),
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, null);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(requestEnvironment.getAccountId(),
        environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());
    Environment updatedEnvironment = environmentService.update(requestEnvironment, scopeInfo).getEnvironment();
    return ResponseDTO.newResponse(EnvironmentMapper.writeDTO(updatedEnvironment, scopeInfo));
  }

  @PUT
  @Path("upsert")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Upsert an environment by identifier", nickname = "upsertEnvironment")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EnvironmentResponseDTO> upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @QueryParam("accountId") String accountId, @NotNull @Valid EnvironmentRequestDTO environmentRequestDTO) {
    mustBeAtProjectLevel(environmentRequestDTO);
    environmentRbacHelper.checkForAccessOrThrow(getEnvironmentAttributes(environmentRequestDTO.getType()),
        ResourceScope.of(
            accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier()),
        environmentRequestDTO.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        accountId, environmentRequestDTO.getOrgIdentifier(), environmentRequestDTO.getProjectIdentifier());
    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO, scopeInfo);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Environment upsertedEnvironment =
        environmentService.upsert(requestEnvironment, UpsertOptions.DEFAULT, scopeInfo).getEnvironment();
    return ResponseDTO.newResponse(EnvironmentMapper.writeDTO(upsertedEnvironment, scopeInfo));
  }

  @GET
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets environment list for a project", nickname = "getEnvironmentListForProject")
  public ResponseDTO<PageResponse<EnvironmentResponseDTO>> listEnvironmentsForProject(
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("100") int size,
      @QueryParam("accountId") String accountId, @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("envIdentifiers") List<String> envIdentifiers, @QueryParam("sort") List<String> sort,
      @Context ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    Criteria criteria = useScopeInfo
        ? CoreCriteriaUtils.createCriteriaForGetList(scopeInfo)
        : CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    Pageable pageRequest;

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    if (!environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
            accountId, orgIdentifier, projectIdentifier, ENVIRONMENT_VIEW_PERMISSION)) {
      Page<Environment> environments = environmentService.list(criteria, Pageable.unpaged());
      if (environments == null || isEmpty(environments)) {
        return ResponseDTO.newResponse(getNGPageResponse(new PageImpl<>(List.of(), pageRequest, 0)));
      }
      List<Environment> permittedEnvironments = environmentRbacHelper.getPermittedEnvironmentsList(
          environments.getContent().stream().filter(Objects::nonNull).collect(toList()));
      if (isEmpty(permittedEnvironments)) {
        return ResponseDTO.newResponse(getNGPageResponse(new PageImpl<>(List.of(), pageRequest, 0)));
      }
      populateInFilter(criteria, EnvironmentKeys.identifier,
          permittedEnvironments.stream().map(Environment::getIdentifier).collect(toList()));
    }

    Page<EnvironmentResponseDTO> environmentList = useScopeInfo
        ? environmentService.list(criteria, pageRequest)
              .map(environment -> EnvironmentMapper.writeDTO(environment, scopeInfo))
        : environmentService.list(criteria, pageRequest).map(EnvironmentMapper::writeDTO);
    return ResponseDTO.newResponse(getNGPageResponse(environmentList));
  }

  private Map<String, String> getEnvironmentAttributes(EnvironmentType type) {
    return type != null ? environmentRbacHelper.getEnvironmentAttributesMap(type.toString()) : new HashMap<>();
  }

  private void mustBeAtProjectLevel(EnvironmentRequestDTO requestDTO) {
    try {
      Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
          "org identifier must be specified. Environments can only be created at Project scope");
      Preconditions.checkArgument(isNotEmpty(requestDTO.getProjectIdentifier()),
          "project identifier must be specified. Environments can only be created at Project scope");
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }
}
