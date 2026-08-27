/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.resource;

import static io.harness.NGConstants.REFERRED_BY_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_BY_ENTITY_SCOPE;
import static io.harness.NGConstants.REFERRED_BY_ENTITY_TYPE;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN1;
import static io.harness.NGConstants.REFERRED_ENTITY_FQN2;
import static io.harness.NGConstants.REFERRED_ENTITY_SCOPE;
import static io.harness.NGConstants.REFERRED_ENTITY_TYPE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference.FullyQualifiedEntityIdentifier;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefFullyQualifiedEntityIdentifier;
import io.harness.beans.InfraDefReference;
import io.harness.beans.InputSetReference;
import io.harness.beans.NGTemplateReference;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.beans.TriggerReference;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityReferenceSummaryDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityUsageCountResponseDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api("/entitySetupUsage")
@Path("entitySetupUsage")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(DX)
public class EntitySetupUsageResource {
  EntitySetupUsageService entitySetupUsageService;
  ScopeInfoService scopeInfoService;
  private static final int MAX_LIMIT = 1000;

  @GET
  @ApiOperation(value = "Get Entities referring this resource", nickname = "listReferredByEntities")
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> list(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @BeanParam GitEntityFindInfoDTO gitFindInfoDTO, @Context ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listAllEntityUsage(page, size, scopeInfo,
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(identifier)
            .build(),
        entityType, searchTerm)));
  }

  @GET
  @Path("v2")
  @ApiOperation(value = "Get Entities referring this resource if fqn is given", nickname = "listAllEntityUsageByFqn")
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> listAllEntityUsageV2(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier = parseFQN(referredEntityFQN, entityType);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(fullyQualifiedEntityIdentifier.getAccountIdentifier(),
        fullyQualifiedEntityIdentifier.getOrgIdentifier(), fullyQualifiedEntityIdentifier.getProjectIdentifier());

    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listAllEntityUsage(
        page, size, scopeInfo, fullyQualifiedEntityIdentifier, entityType, searchTerm)));
  }

  @GET
  @Path("internal/listAllEntityUsageV2With2Fqn")
  @ApiOperation(value = "Get Entities referring this resource if fqns are provided",
      nickname = "listAllEntityUsageWithTwoFqns", hidden = true)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>>
  listAllEntityUsageWith2Fqns(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(REFERRED_ENTITY_FQN1) String referredEntityFQN1,
      @NotNull @QueryParam(REFERRED_ENTITY_FQN2) String referredEntityFQN2,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    FullyQualifiedEntityIdentifier firstFullyQualifiedEntityIdentifier = parseFQN(referredEntityFQN1, entityType);
    ScopeInfo firstScopeInfo = scopeInfoService.getScopeInfo(firstFullyQualifiedEntityIdentifier.getAccountIdentifier(),
        firstFullyQualifiedEntityIdentifier.getOrgIdentifier(),
        firstFullyQualifiedEntityIdentifier.getProjectIdentifier());

    FullyQualifiedEntityIdentifier secondFullyQualifiedEntityIdentifier = parseFQN(referredEntityFQN2, entityType);
    ScopeInfo secondScopeInfo =
        scopeInfoService.getScopeInfo(secondFullyQualifiedEntityIdentifier.getAccountIdentifier(),
            secondFullyQualifiedEntityIdentifier.getOrgIdentifier(),
            secondFullyQualifiedEntityIdentifier.getProjectIdentifier());
    return ResponseDTO.newResponse(
        getNGPageResponse(entitySetupUsageService.listAllEntityUsageWithSupportForTwoFqnForASingleEntity(page, size,
            firstScopeInfo, firstFullyQualifiedEntityIdentifier, secondScopeInfo, secondFullyQualifiedEntityIdentifier,
            entityType, searchTerm)));
  }

  @GET
  @Path("internal")
  @ApiOperation(
      value = "Get Entities referring this resource if fqn is given", nickname = "listAllEntityUsage", hidden = true)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>>
  listAllEntityUsage(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier = parseFQN(referredEntityFQN, entityType);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(fullyQualifiedEntityIdentifier.getAccountIdentifier(),
        fullyQualifiedEntityIdentifier.getOrgIdentifier(), fullyQualifiedEntityIdentifier.getProjectIdentifier());

    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listAllEntityUsage(
        page, size, scopeInfo, fullyQualifiedEntityIdentifier, entityType, searchTerm)));
  }

  @GET
  @Path("getOrgEntitiesReferredByProject")
  @ApiOperation(
      value = "Get all Org Scope Entities Referenced by Project", nickname = "getOrgEntitiesReferredByProject")
  @Timed
  @ResponseMetered
  public ResponseDTO<List<EntityReferenceSummaryDTO>>
  getOrgEntitiesReferredByProject(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Context ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(
        entitySetupUsageService.getProjectToOrgReferences(accountIdentifier, scopeInfo.getUniqueId()));
  }

  @GET
  @Path("getEntityReferences")
  @ApiOperation(value = "Get all entity references by scope mapping", nickname = "getEntityReferences")
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> getEntityReferencesRaw(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(REFERRED_BY_ENTITY_SCOPE) String referredByEntityScope,
      @QueryParam(REFERRED_ENTITY_SCOPE) String referredEntityScope,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType, @Context ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(getNGPageResponse(entitySetupUsageService.listEntityReferences(
        page, size, scopeInfo, referredByEntityScope, referredEntityScope, referredEntityType)));
  }

  @POST
  @Path("internal/listAllEntityUsageCountV2WithMultiple2Fqns")
  @ApiOperation(value = "Get count of entities referring multiple resources if fqns are provided",
      nickname = "listAllEntityUsageWithMultipleTwoFqns", hidden = true)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<EntityUsageCountResponseDTO>
  listAllEntityUsageWithMultiple2Fqns(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType, @Body @NotNull List<String> referredEntityFQNPairs) {
    return ResponseDTO.newResponse(entitySetupUsageService.listAllEntityUsageCountV2WithMultiple2Fqns(
        accountIdentifier, entityType, referredEntityFQNPairs));
  }

  /**
   * Use entitySetupUsage/v2/internal/listAllReferredUsages instead
   */
  @GET
  @Path("internal/listAllReferredUsages")
  @ApiOperation(value = "Get Entities referred by this resource", nickname = "listAllReferredUsages", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<List<EntitySetupUsageDTO>> listAllReferredUsages(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam(NGResourceFilterConstants.IS_NEW_GITX_ENABLED) boolean isNewGitXEnabled) {
    // todo: Just fqn is not sufficient here, we should have referredBy entity type also here
    return ResponseDTO.newResponse(entitySetupUsageService.listAllReferredUsages(
        page, size, accountIdentifier, referredByEntityFQN, referredEntityType, searchTerm, isNewGitXEnabled));
  }

  @GET
  @Path("v2/internal/listAllReferredUsages")
  @ApiOperation(value = "Get Entities referred by this resource", nickname = "listAllReferredUsagesV2", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<List<EntitySetupUsageDTO>> listAllReferredUsagesV2(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(MAX_LIMIT) int size,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @QueryParam(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam(NGResourceFilterConstants.IS_NEW_GITX_ENABLED) boolean isNewGitXEnabled) {
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier = parseFQN(referredByEntityFQN, referredByEntityType);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(fullyQualifiedEntityIdentifier.getAccountIdentifier(),
        fullyQualifiedEntityIdentifier.getOrgIdentifier(), fullyQualifiedEntityIdentifier.getProjectIdentifier());

    return ResponseDTO.newResponse(entitySetupUsageService.listAllReferredUsages(page, size, scopeInfo,
        fullyQualifiedEntityIdentifier, referredByEntityType, referredEntityType, searchTerm, isNewGitXEnabled));
  }

  @POST
  @Path("internal/listAllReferredUsagesBatch")
  @ApiOperation(
      value = "Get Entities referred by list of resources", nickname = "listAllReferredUsagesBatch", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<EntityReferencesDTO>
  listAllReferredUsagesBatch(@NotNull @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
                             String accountIdentifier, @Size(max = 50) @Body List<String> referredByEntityFQNList,
      @NotNull @QueryParam(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType,
      @NotNull @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType) {
    // todo @deepak: Will have to add branch and repo, which might be a breaking change
    return ResponseDTO.newResponse(entitySetupUsageService.listAllReferredUsagesBatch(
        accountIdentifier, referredByEntityFQNList, referredByEntityType, referredEntityType));
  }

  @GET
  @Path("/internal/isEntityReferenced")
  @ApiOperation(value = "Returns true if the entity is referenced by other resource", nickname = "isEntityReferenced",
      hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  isEntityReferenced(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType entityType) {
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier = parseFQN(referredEntityFQN, entityType);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(fullyQualifiedEntityIdentifier.getAccountIdentifier(),
        fullyQualifiedEntityIdentifier.getOrgIdentifier(), fullyQualifiedEntityIdentifier.getProjectIdentifier());
    return ResponseDTO.newResponse(
        entitySetupUsageService.isEntityReferenced(scopeInfo, fullyQualifiedEntityIdentifier, entityType));
  }

  // use eveent fmwk
  @POST
  @Path("internal")
  @ApiOperation(value = "Saves the entity reference", nickname = "postEntitySetupUsage", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<EntitySetupUsageDTO> save(EntitySetupUsageDTO entitySetupUsageDTO) {
    return ResponseDTO.newResponse(entitySetupUsageService.save(entitySetupUsageDTO));
  }

  // use event fmwk
  // We no longer support this api, the branching support is also not their for this api
  // for any crud of setup usage use the event framework
  @DELETE
  @Path("internal")
  @ApiOperation(value = "Deletes the entity reference record", nickname = "deleteEntitySetupUsage", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<Boolean> delete(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(REFERRED_ENTITY_FQN) String referredEntityFQN,
      @QueryParam(REFERRED_ENTITY_TYPE) EntityType referredEntityType,
      @QueryParam(REFERRED_BY_ENTITY_FQN) String referredByEntityFQN,
      @QueryParam(REFERRED_BY_ENTITY_TYPE) EntityType referredByEntityType) {
    FullyQualifiedEntityIdentifier fullyQualifiedReferredEntity = parseFQN(referredEntityFQN, referredEntityType);
    ScopeInfo referredEntityScopeInfo =
        scopeInfoService.getScopeInfo(fullyQualifiedReferredEntity.getAccountIdentifier(),
            fullyQualifiedReferredEntity.getOrgIdentifier(), fullyQualifiedReferredEntity.getProjectIdentifier());

    FullyQualifiedEntityIdentifier fullyQualifiedReferredByEntity = parseFQN(referredByEntityFQN, referredByEntityType);
    ScopeInfo referredByEntityScopeInfo =
        scopeInfoService.getScopeInfo(fullyQualifiedReferredByEntity.getAccountIdentifier(),
            fullyQualifiedReferredByEntity.getOrgIdentifier(), fullyQualifiedReferredByEntity.getProjectIdentifier());

    return ResponseDTO.newResponse(entitySetupUsageService.delete(referredEntityScopeInfo, fullyQualifiedReferredEntity,
        referredEntityType, referredByEntityScopeInfo, fullyQualifiedReferredByEntity, referredByEntityType));
  }

  @DELETE
  @Path("cleanupProjectCrossReferences")
  @ApiOperation(value = "Cleanup project cross-references during project movement",
      nickname = "cleanupProjectCrossReferences", hidden = true)
  @InternalApi
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<Long>
  cleanupProjectCrossReferences(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotEmpty @QueryParam("parentUniqueId") String parentUniqueId) {
    return ResponseDTO.newResponse(
        entitySetupUsageService.cleanupProjectCrossReferencesDuringMovement(accountIdentifier, parentUniqueId));
  }

  public static FullyQualifiedEntityIdentifier parseFQN(String fqn, EntityType entityType) {
    if (EntityType.INFRASTRUCTURE.equals(entityType)) {
      return InfraDefReference.parseFQN(fqn);
    }
    return switch (entityType.getEntityReferenceClass().getSimpleName()) {
      case "InputSetReference" -> InputSetReference.parseFQN(fqn);
      case "NGTemplateReference" -> NGTemplateReference.parseFQN(fqn);
      case "TriggerReference" -> TriggerReference.parseFQN(fqn);
      case "InfraDefReference" -> InfraDefReference.parseFQN(fqn);
      default -> IdentifierRef.parseFQN(fqn);
    };
  }
}
