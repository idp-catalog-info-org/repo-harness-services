/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.GetEntitiesGroupsDTO;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.spec.server.idp.v1.model.CatalogSyncRequest;
import io.harness.spec.server.idp.v1.model.EntitiesConvertRequestBody;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.EntityConvertResponse;
import io.harness.spec.server.idp.v1.model.EntityConvertV2Response;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.EntityKindsResponse;
import io.harness.spec.server.idp.v1.model.EntityMoveRequest;
import io.harness.spec.server.idp.v1.model.EntityRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentBluePrintInfoResponse;
import io.harness.spec.server.idp.v1.model.GitMetadataUpdateRequest;

import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogService {
  void backgroundMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier);
  boolean syncInSynchronousMode(String accountIdentifier, String entityUid, String action);

  void handleUserBasedOnAction(String accountIdentifier, UserMembershipDTO userMembershipDTO, String action);
  void handleUserGroupBasedOnAction(String accountIdentifier, String userGroupIdentifier, String action);
  void migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier);

  List<EntityKindsResponse> getEntitiesKinds(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<EntityFiltersResponse> getEntitiesFilters(String accountIdentifier, String scopes, String kind, String filter);

  List<EntityFiltersResponse> getEntitiesFiltersByRefs(
      String accountIdentifier, String entityRefs, String kind, String filter);

  EntityConvertResponse convertEntity(
      String harnessAccount, String option, EntityRequest body, String entityRef, boolean loadFromFallbackBranch);

  EntityResponse createEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      Boolean forceConvert, Boolean dryRun, EntityCreateRequest body);

  Pair<EntityResponse, EntityVersionResponse> createEntity(String harnessAccount, String orgIdentifier,
      String projectIdentifier, Boolean forceConvert, Boolean dryRun, EntityCreateRequest body,
      EntityVersionCreateRequest versionCreateRequest, boolean versionedEntity);

  EntityResponse importEntity(String harnessAccount, String orgIdentifier, String projectIdentifier);

  void moveEntity(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef, EntityMoveRequest body);

  default EntityResponse getEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, boolean resolvePlaceholders, boolean loadFromFallbackBranch, boolean loadFromCache) {
    return getEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, resolvePlaceholders,
        loadFromFallbackBranch, loadFromCache, true);
  }

  EntityResponse getEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      boolean resolvePlaceholders, boolean loadFromFallbackBranch, boolean loadFromCache, boolean shouldValidateRBAC);

  List<EntityValidateResponse> validateYaml(String harnessAccount, EntityValidateRequest entityValidateRequest);

  default EntityResponse updateEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, EntityUpdateRequest body) {
    return updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, body, true, true, true, false);
  }

  Pair<EntityResponse, EntityVersionResponse> updateEntity(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String entityRef, EntityUpdateRequest body, boolean shouldValidateRBAC,
      boolean shouldUpdateOnGit, boolean shouldCheckExistingSourceValidation,
      EntityVersionUpdateRequest entityVersionUpdateRequest, boolean versionedEntity, String version,
      boolean metadataEnrichmentByUser);

  EntityResponse updateEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      EntityUpdateRequest body, boolean shouldValidateRBAC, boolean shouldUpdateOnGit,
      boolean shouldCheckExistingSourceValidation, boolean metadataEnrichmentByUser);

  void updateSourceCodeInEntityOnConnectorUpdate(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef);
  void removeSourceCodeReferencesOnConnectorDeletion(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef);

  void updateGitMetadata(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      GitMetadataUpdateRequest body);

  void deleteEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      Boolean deleteHierarchyKindEntity);

  void deleteEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      String version, boolean versionedEntity, Boolean deleteHierarchyKindEntity);

  GetEntitiesDTO getEntities(String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe, Boolean favorites, String kind,
      String type, String owner, String lifecycle, String tags, String filter, boolean includeScorecardsData,
      boolean entityRefAndCriteria);
  GetEntitiesDTO getEntities(String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe, Boolean favorites, String kind,
      String type, String owner, String lifecycle, String tags, String filter, boolean includeScorecardsData);

  GetEntitiesDTO getEntitiesV2(String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe, Boolean favorites, String kind,
      String type, String owner, String lifecycle, String tags, String filter, boolean includeScorecardsData,
      boolean entityRefAndCriteria, Boolean skipFavorites);

  void migrateEntities(String harnessAccount, EntitiesMigrateRequest body);

  void syncCatalogEntities(String harnessAccount, String option, CatalogSyncRequest body);

  CatalogEntity changeScope(CatalogEntity catalogEntity, ScopeInfo destinationScope);

  String getJsonSchema(String kind);

  GetEntitiesGroupsDTO getEntitiesGroups(String harnessAccount, String searchOnEntities, String searchOnGroups,
      String scopes, String kind, Boolean ownedByMe, Boolean favorites, String type, String owner, String lifecycle,
      String tags);
  void recreateCatalogsWithAccountAsNamespaceForIDPV2(String accountIdentifier);

  CatalogEntity getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String kind, String identifier);

  List<EnvironmentBluePrintInfoResponse> getEnvironmentBlueprintInfo(
      String accountIdentifier, List<String> bluePrintIdentifiers);

  GetEntitiesDTO getEnvironmentsByBlueprintIdentifier(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String blueprintIdentifier, Integer page, Integer limit, String sort,
      String searchTerm);

  GetEntitiesDTO getEntityAssociations(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String kind, String identifier, String relations, Integer page, Integer limit, String sort, String searchTerm,
      Boolean ownedByMe, Boolean favorites, String associationKind, String type, String owner, String lifecycle,
      String tags, String filter);

  void projectMovement(ProjectEntityChangeDTO projectEntityChangeDTO);

  Response getWorkflowExecutionHistory(String accountIdentifier, List<String> entityRefs, boolean executedByMe,
      List<String> status, Long startTime, Long endTime, String searchTerm, String sort, int page, int size);

  Pair<String, String> getEntityContent(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, String path);

  List<EntityConvertV2Response> convertEntityV2(
      String harnessAccount, String option, @Valid List<EntitiesConvertRequestBody> entitiesConvertRequestBodyList);
}
