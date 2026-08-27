/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntityVersionsDTO;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.idp.catalog.events.CatalogEntityVersionCreateEvent;
import io.harness.idp.catalog.events.CatalogEntityVersionDeleteEvent;
import io.harness.idp.catalog.events.CatalogEntityVersionUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.mapper.CatalogVersionMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.CatalogEntityVersionRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.outbox.api.OutboxService;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogVersionServiceImpl implements CatalogVersionService {
  CatalogServiceHelper catalogServiceHelper;
  CatalogEntityVersionRepository catalogEntityVersionRepository;
  ScopeInfoClient scopeInfoClient;
  IdpCommonService idpCommonService;
  OutboxService outboxService;
  TransactionHelper transactionHelper;
  CatalogEntityRepository catalogEntityRepository;

  @Inject
  public CatalogVersionServiceImpl(CatalogEntityVersionRepository catalogEntityVersionRepository,
      CatalogServiceHelper catalogServiceHelper, IdpCommonService idpCommonService, ScopeInfoClient scopeInfoClient,
      OutboxService outboxService, TransactionHelper transactionHelper,
      CatalogEntityRepository catalogEntityRepository) {
    this.catalogEntityRepository = catalogEntityRepository;
    this.catalogEntityVersionRepository = catalogEntityVersionRepository;
    this.scopeInfoClient = scopeInfoClient;
    this.catalogServiceHelper = catalogServiceHelper;
    this.idpCommonService = idpCommonService;
    this.outboxService = outboxService;
    this.transactionHelper = transactionHelper;
  }

  @Override
  public EntityVersionResponse createEntityVersion(CatalogEntity catalogEntity, String entityYaml, String version,
      String description, Boolean deprecated, Boolean stable, String orgName, String projectName) {
    String identifier = catalogEntity.getIdentifier();

    try {
      CatalogEntityVersion catalogEntityVersion = CatalogVersionMapper.yamlToEntity(
          catalogEntity.getId(), entityYaml, version, description, deprecated, stable);

      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(catalogEntity.getAccountIdentifier())
                                .orgIdentifier(catalogEntity.getOrgIdentifier())
                                .projectIdentifier(catalogEntity.getProjectIdentifier())
                                .scopeType(ScopeLevel.of(catalogEntity.getAccountIdentifier(),
                                    catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()))
                                .uniqueId(catalogEntity.getParentUniqueId())
                                .build();

      CatalogEntityVersion res = transactionHelper.performTransaction(() -> {
        outboxService.save(new CatalogEntityVersionCreateEvent(scopeInfo, catalogEntityVersion.getYaml(),
            catalogEntity.getKind(), catalogEntity.getIdentifier(), catalogEntityVersion.getVersion(),
            catalogEntityVersion.isStable(), catalogEntityVersion.isDeprecated()));

        return catalogEntityVersionRepository.createCatalogEntityVersionAndSyncStable(catalogEntityVersion);
      });

      return CatalogVersionMapper.entityVersionToResponse(res, catalogEntity.getOrgIdentifier(), orgName,
          catalogEntity.getProjectIdentifier(), projectName, catalogEntity.getScope(), catalogEntity.getKind(),
          identifier);
    } catch (DuplicateKeyException e) {
      assert identifier != null;
      String errorMessage =
          String.format("Entity with identifier [%s] and version [%s] already exists for the same kind",
              identifier.toLowerCase(), version);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    } catch (Exception ex) {
      log.error("Error in create entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public EntityVersionResponse updateEntityVersion(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String version, EntityVersionUpdateRequest body, CatalogEntity existingCatalogEntity) {
    try {
      CatalogEntityVersionScopeInfo info = getCatalogEntityVersionScopeInfo(existingCatalogEntity);

      CatalogEntityVersion catalogEntityVersion =
          catalogEntityVersionRepository.findByEntityIdAndVersion(info.parentId(), version);
      if (catalogEntityVersion == null) {
        String errorMessage = String.format("Entity with kind '%s' and identifier '%s' does not have a version '%s'.",
            existingCatalogEntity.getKind(), existingCatalogEntity.getIdentifier(), version);
        log.error(errorMessage);
        throw new InvalidRequestException(errorMessage);
      }

      CatalogEntityVersion res = transactionHelper.performTransaction(() -> {
        // Capture old values for audit event
        String oldYaml = catalogEntityVersion.getYaml();
        Boolean oldStable = catalogEntityVersion.isStable();
        Boolean oldDeprecated = catalogEntityVersion.isDeprecated();

        if (body.getDescription() != null) {
          catalogEntityVersion.setDescription(body.getDescription());
        }

        if (body.getYaml() != null) {
          if (catalogEntityVersion.isDeprecated()) {
            throw new InvalidRequestException("A deprecated version cannot be edited.");
          }

          catalogEntityVersion.setYaml(body.getYaml());
        }

        if (body.isStable() != null) {
          if (body.isStable() && !catalogEntityVersion.isStable()) {
            if (catalogEntityVersion.isDeprecated()) {
              throw new InvalidRequestException("A deprecated version cannot be marked as stable.");
            }

            catalogEntityVersion.setStable(true);
          } else if (!body.isStable() && catalogEntityVersion.isStable()) {
            throw new InvalidRequestException("A stable version must be replaced by another stable version.");
          }
        }

        if (body.isDeprecated() != null) {
          if (body.isDeprecated() && catalogEntityVersion.isStable()) {
            throw new InvalidRequestException("A stable version cannot be deprecated.");
          }

          if (body.isDeprecated() && !catalogEntityVersion.isDeprecated()) {
            catalogEntityVersion.setDeprecatedAt(System.currentTimeMillis());
          } else if (!body.isDeprecated() && catalogEntityVersion.isDeprecated()) {
            catalogEntityVersion.setDeprecatedAt(0);
          }
          catalogEntityVersion.setDeprecated(body.isDeprecated());
        }

        outboxService.save(new CatalogEntityVersionUpdateEvent(info.scopeInfo(), catalogEntityVersion.getYaml(),
            oldYaml, info.kind(), info.identifier(), version, oldStable, catalogEntityVersion.isStable(), oldDeprecated,
            catalogEntityVersion.isDeprecated()));

        catalogEntityVersionRepository.updateCatalogEntityVersionAndSyncStable(catalogEntityVersion);

        return catalogEntityVersion;
      });

      return CatalogVersionMapper.entityVersionToResponse(res, info.orgIdentifier(), info.orgName(),
          info.projectIdentifier(), info.projectName(), info.scope(), info.kind(), info.identifier());
    } catch (Exception e) {
      log.error("Error in update entity version. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public EntityVersionResponse getEntityVersion(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, String version) {
    try {
      if (isEmpty(version)) {
        throw new InvalidRequestException("Version must not be null or empty");
      }
      String validatedKind = catalogServiceHelper.validateAndSanitizeKind(kind);
      catalogServiceHelper.validateIdentifier(identifier);

      CatalogEntityVersionScopeInfo info = getCatalogEntityVersionScopeInfo(
          harnessAccount, orgIdentifier, projectIdentifier, scope, validatedKind, identifier);

      catalogServiceHelper.checkCrudRbac(harnessAccount, info.orgIdentifier(), info.projectIdentifier(), info.kind(),
          CatalogUtils.entityRef(info.kind(), info.orgIdentifier(), info.projectIdentifier(), info.identifier()),
          "view");

      CatalogEntityVersion catalogEntityVersion =
          catalogEntityVersionRepository.findByEntityIdAndVersion(info.parentId(), version);

      if (catalogEntityVersion == null) {
        String errorMessage = String.format("Entity with kind '%s' and identifier '%s' does not have a version '%s'.",
            validatedKind, identifier, version);
        log.error(errorMessage);
        throw new InvalidRequestException(errorMessage);
      }

      return CatalogVersionMapper.entityVersionToResponse(catalogEntityVersion, info.orgIdentifier(), info.orgName(),
          info.projectIdentifier(), info.projectName(), info.scope(), info.kind(), info.identifier());
    } catch (Exception e) {
      log.error("Error in get entity version. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public GetEntityVersionsDTO getEntityVersions(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, Integer page, Integer limit, String versionSearchTerm,
      Boolean deprecated) {
    try {
      String validatedKind = catalogServiceHelper.validateAndSanitizeKind(kind);

      CatalogEntityVersionScopeInfo info = getCatalogEntityVersionScopeInfo(
          harnessAccount, orgIdentifier, projectIdentifier, scope, validatedKind, identifier);

      catalogServiceHelper.checkCrudRbac(harnessAccount, info.orgIdentifier(), info.projectIdentifier(), info.kind(),
          CatalogUtils.entityRef(info.kind(), info.orgIdentifier(), info.projectIdentifier(), info.identifier()),
          "view");

      Page<CatalogEntityVersion> pagedCatalogEntityVersions =
          catalogEntityVersionRepository.findByEntityId(info.parentId(), page, limit, versionSearchTerm, deprecated);

      List<CatalogEntityVersion> catalogEntitiesPagedContent = pagedCatalogEntityVersions.getContent();
      List<EntityVersionResponse> entityVersionResponses = new ArrayList<>();

      for (CatalogEntityVersion catalogEntityVersion : catalogEntitiesPagedContent) {
        EntityVersionResponse entityVersionResponse =
            CatalogVersionMapper.entityVersionToResponse(catalogEntityVersion, info.orgIdentifier(), info.orgName(),
                info.projectIdentifier(), info.projectName(), info.scope(), info.kind(), info.identifier());
        entityVersionResponses.add(entityVersionResponse);
      }

      return GetEntityVersionsDTO.builder()
          .entityVersionResponses(entityVersionResponses)
          .pageNumber(pagedCatalogEntityVersions.getNumber())
          .totalElements(pagedCatalogEntityVersions.getTotalElements())
          .build();
    } catch (Exception e) {
      log.error("Error in get entity versions. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public void deleteEntityVersion(String harnessAccount, String orgIdentifier, String projectIdentifier,
      CatalogEntity existingCatalogEntity, String version) {
    try {
      if (isEmpty(version)) {
        throw new InvalidRequestException("Version must not be null or empty");
      }
      // Fetch version entity before deletion to capture audit data
      CatalogEntityVersion catalogEntityVersion =
          catalogEntityVersionRepository.findByEntityIdAndVersion(existingCatalogEntity.getId(), version);

      if (catalogEntityVersion == null) {
        String errorMessage = String.format("Entity with kind '%s' and identifier '%s' does not have a version '%s'.",
            existingCatalogEntity.getKind(), existingCatalogEntity.getIdentifier(), version);
        log.error(errorMessage);
        throw new InvalidRequestException(errorMessage);
      }
      // Build ScopeInfo for audit event
      ScopeInfo scopeInfo =
          ScopeInfo.builder()
              .accountIdentifier(existingCatalogEntity.getAccountIdentifier())
              .orgIdentifier(existingCatalogEntity.getOrgIdentifier())
              .projectIdentifier(existingCatalogEntity.getProjectIdentifier())
              .scopeType(ScopeLevel.of(existingCatalogEntity.getAccountIdentifier(),
                  existingCatalogEntity.getOrgIdentifier(), existingCatalogEntity.getProjectIdentifier()))
              .uniqueId(existingCatalogEntity.getParentUniqueId())
              .build();
      transactionHelper.performTransaction(() -> {
        // Save audit event before deletion
        outboxService.save(new CatalogEntityVersionDeleteEvent(scopeInfo, catalogEntityVersion.getYaml(),
            existingCatalogEntity.getKind(), existingCatalogEntity.getIdentifier(), version,
            catalogEntityVersion.isStable(), catalogEntityVersion.isDeprecated()));

        // Perform deletion
        catalogEntityVersionRepository.deleteByEntityIdAndVersion(existingCatalogEntity.getId(), version);

        return null;
      });
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error in delete entity versions. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  private record CatalogEntityVersionScopeInfo(String orgIdentifier, String orgName, String projectIdentifier,
      String projectName, String identifier, String kind, ScopeInfo scopeInfo, String parentId, String scope) {}

  private CatalogEntityVersionScopeInfo getCatalogEntityVersionScopeInfo(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String scope, String kind, String identifier) {
    String orgIdentifierFromScope, projectIdentifierFromScope;
    String[] scopeSplit = scope.split("\\.");
    orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
    projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
    if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
        || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
      throw new InvalidRequestException(
          "Organization / Project from request query params doesn't match with the details provided for scope");
    }
    orgIdentifier = orgIdentifierFromScope;
    projectIdentifier = projectIdentifierFromScope;

    String orgName = idpCommonService.getOrgName(harnessAccount, orgIdentifier);
    String projectName = idpCommonService.getProjectName(harnessAccount, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(scopeInfo.getUniqueId(), kind, identifier);

    if (catalogEntity == null) {
      String errorMessage =
          String.format("Entity with kind '%s' and identifier '%s' does not exist.", kind, identifier.toLowerCase());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return new CatalogEntityVersionScopeInfo(orgIdentifier, orgName, projectIdentifier, projectName, identifier, kind,
        scopeInfo, catalogEntity.getId(), catalogEntity.getScope());
  }

  private CatalogEntityVersionScopeInfo getCatalogEntityVersionScopeInfo(CatalogEntity catalogEntity) {
    String accountIdentifier = catalogEntity.getAccountIdentifier();
    String orgIdentifier = catalogEntity.getOrgIdentifier();
    String projectIdentifier = catalogEntity.getProjectIdentifier();

    String orgName = idpCommonService.getOrgName(accountIdentifier, orgIdentifier);
    String projectName = idpCommonService.getProjectName(accountIdentifier, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier))
                              .uniqueId(catalogEntity.getParentUniqueId())
                              .build();

    return new CatalogEntityVersionScopeInfo(orgIdentifier, orgName, projectIdentifier, projectName,
        catalogEntity.getIdentifier(), catalogEntity.getKind(), scopeInfo, catalogEntity.getId(),
        catalogEntity.getScope());
  }
}
