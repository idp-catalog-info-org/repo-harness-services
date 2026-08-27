/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.exception.WingsException.USER;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUidFromEntityRef;
import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.ccp.utils.CatalogCustomPropertiesUtils.insertMap;
import static io.harness.idp.ccp.utils.CatalogCustomPropertiesUtils.removeProperties;
import static io.harness.idp.common.CommonUtils.buildMap;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.CommonUtils.tokenizeProperty;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.Constants.RESPONSE_STATUS;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.idp.common.JacksonUtils.readValueForObject;
import static io.harness.idp.common.JacksonUtils.readValueForSingleEntity;
import static io.harness.idp.common.JacksonUtils.write;
import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.security.SecurityContextBuilder.EMAIL;
import static io.harness.security.SecurityContextBuilder.UNIQUE_ID;
import static io.harness.security.SecurityContextBuilder.USERNAME;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.CatalogMetadataRequest;
import io.harness.clients.CustomPropertiesDeleteRequest;
import io.harness.clients.CustomPropertiesRequest;
import io.harness.context.GlobalContextData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.backstage.utils.BackstageUtils;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.ccp.cache.SchemaCache;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDisableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyEnableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent;
import io.harness.idp.ccp.mappers.CatalogCustomPropertiesMapper;
import io.harness.idp.ccp.repositories.CatalogCustomPropertiesRepository;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.RbacUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.manage.GlobalContextManager;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.PrincipalContextData;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.CustomPropertiesBase;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyResponse;
import io.harness.spec.server.idp.v1.model.EntityRefs;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityValue;
import io.harness.spec.server.idp.v1.model.PropertyValue;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesServiceImpl implements CatalogCustomPropertiesService {
  public static final String CUSTOM_PROPERTIES_METADATA_KEY = "customPropertiesEnabled";
  public static final Set<String> UNMODIFIABLE_FIELDS = Set.of(
      "identifier", "orgIdentifier", "projectIdentifier", "kind", "metadata", "metadata.name", "metadata.namespace");
  private static final ObjectMapper objectMapper = new ObjectMapper();
  @Inject CatalogCustomPropertiesRepository ccpRepository;
  @Inject BackstageService backstageService;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject SchemaCache schemaCache;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject OutboxService outboxService;
  @Inject NamespaceService namespaceService;
  @Inject @Named("NON_PRIVILEGED") AccessControlClient accessControlClient;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject CatalogService catalogService;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject IdpCommonService idpCommonService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject IDPToHarnessHelper idpToHarnessHelper;
  @Inject KindServiceHelper kindServiceHelper;
  @Inject ResourceLocker resourceLocker;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  // Lost-update protection: any CCP write to a kind=api entity acquires the same per-entity lock
  // the processor uses (ApiEndpointProcessor.lockNameFor). See #acquireApiEntityLocks.
  private static final long CCP_LOCK_TIMEOUT_MINS = 5;
  private static final long CCP_LOCK_WAIT_SECONDS = 10;
  private static final String CATALOG_API_SUFFIX = "%s/idp/api/catalog/entities/"
      + "by-query?filter=kind=component,kind=api,kind=resource,kind=system,kind=domain&fullTextFilterTerm=%s&"
      + "fullTextFilterFields=metadata.name&fields=kind,metadata.namespace,metadata.name&limit=20";

  @Override
  public CustomPropertyByFieldResponse resolveEntitiesAndUpsertCustomProperties(
      CustomPropertyFilterRequest request, String accountIdentifier, boolean dryRun) {
    checkForUnmodifiableFields(request.getProperty());
    List<String> skipEntityRefs = new ArrayList<>();
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (!isEmpty(request.getSkipEntityRefs())) {
      request.getSkipEntityRefs().forEach(entityRef
          -> skipEntityRefs.add(isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(entityRef)
                                               : BackstageUtils.getFullyQualifiedEntityRef(entityRef)));
    }
    Set entities = new HashSet();
    List<String> entityRefs = new ArrayList<>();
    List<CatalogEntity> processedEntities = new ArrayList<>();
    if (isIdpV2Enabled) {
      String kind =
          request.getFilter().getKind().equalsIgnoreCase("template") ? "workflow" : request.getFilter().getKind();
      kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      String type = request.getFilter().getType();
      String owner = String.join(",", request.getFilter().getOwners());
      String tag = String.join(",", request.getFilter().getTags());
      String lifecycle = String.join(",", request.getFilter().getLifecycle());
      String scopes = catalogServiceHelper.getAllScopes();

      if (!isEmpty(request.getFilter().getScopes())) {
        scopes = String.join(",", request.getFilter().getScopes());
      }

      Page<CatalogEntity> catalogEntitiesPaged;
      int page = 0;
      do {
        catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
            catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, null).getLeft(),
            page, -1, null, null, null, null, kind, type, owner, lifecycle, tag, null, null);
        if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
          entities.addAll(catalogEntitiesPaged.getContent());
        }
        page++;
      } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

      if (!isEmpty(entities)) {
        Map<String, String> entityRefToOwner = new HashMap<>();
        entities.forEach(entity
            -> entityRefToOwner.put(
                CatalogUtils.entityRef((CatalogEntity) entity), ((CatalogEntity) entity).getOwner()));
        Set<String> allowedEntityRefs = catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
            accountIdentifier, entityRefToOwner, "edit");
        if (isEmpty(allowedEntityRefs)) {
          throw new NGAccessDeniedException(
              String.format("Missing %s edit Permission", kind), USER, Collections.emptyList());
        }
        allowedEntityRefs.removeIf(skipEntityRefs::contains);
        entityRefs = allowedEntityRefs.stream().toList();
      }
    } else {
      checkRBAC(accountIdentifier, "edit");
      entities = fetchEntities(accountIdentifier, request.getFilter(), null, skipEntityRefs);
      entityRefs = getEntityRefs(entities);
    }
    List<CatalogCustomPropertyEntity> entitiesToAdd = new ArrayList<>();
    List<CatalogCustomPropertyEntity> entitiesToUpdate = new ArrayList<>();
    Map<String, CatalogCustomPropertyEntity> entitiesInDBMap =
        getEntitiesByPropertyFromDb(entityRefs, accountIdentifier, request.getProperty());

    for (Object entity : entities) {
      processedEntities.add(processEntity(entity, request, entitiesInDBMap, entitiesToAdd, entitiesToUpdate));
    }

    log.info("Field {} added in {} and updated in {} entities.", request.getProperty(), entitiesToAdd.size(),
        entitiesToUpdate.size());

    saveInDBAndAuditChangesIfRequired(entitiesInDBMap, entitiesToAdd, entitiesToUpdate, accountIdentifier, dryRun, true,
        isIdpV2Enabled, processedEntities);
    return CatalogCustomPropertiesMapper.toResponse(request.getProperty(), entitiesToAdd, entitiesToUpdate);
  }

  @Override
  public CustomPropertyByFieldDeleteResponse deleteCustomProperties(
      CustomPropertyFilterDeleteRequest request, String accountIdentifier, boolean dryRun) {
    checkApiEndpointDataProtection(request.getProperty());
    List<String> skipEntityRefs = new ArrayList<>();
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (!isEmpty(request.getSkipEntityRefs())) {
      request.getSkipEntityRefs().forEach(entityRef
          -> skipEntityRefs.add(isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(entityRef)
                                               : BackstageUtils.getFullyQualifiedEntityRef(entityRef)));
    }
    Set entities = new HashSet();
    List<String> entityRefs = new ArrayList<>();
    if (isIdpV2Enabled) {
      String kind =
          request.getFilter().getKind().equalsIgnoreCase("template") ? "workflow" : request.getFilter().getKind();
      kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      String type = request.getFilter().getType();
      String owner = String.join(",", request.getFilter().getOwners());
      String tag = String.join(",", request.getFilter().getTags());
      String lifecycle = String.join(",", request.getFilter().getLifecycle());
      String scopes = catalogServiceHelper.getAllScopes();
      if (!isEmpty(request.getFilter().getScopes())) {
        scopes = String.join(",", request.getFilter().getScopes());
      }

      Page<CatalogEntity> catalogEntitiesPaged;
      int page = 0;
      do {
        catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
            catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, null).getLeft(),
            page, -1, null, null, null, null, kind, type, owner, lifecycle, tag, null, null);
        if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
          entities.addAll(catalogEntitiesPaged.getContent());
        }
        page++;
      } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

      if (!isEmpty(entities)) {
        Map<String, String> entityRefToOwner = new HashMap<>();
        entities.forEach(entity
            -> entityRefToOwner.put(
                CatalogUtils.entityRef((CatalogEntity) entity), ((CatalogEntity) entity).getOwner()));
        Set<String> allowedEntityRefs = catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
            accountIdentifier, entityRefToOwner, "edit");
        if (isEmpty(allowedEntityRefs)) {
          throw new NGAccessDeniedException(
              String.format("Missing %s edit Permission", kind), USER, Collections.emptyList());
        }
        allowedEntityRefs.removeIf(skipEntityRefs::contains);
        entityRefs = allowedEntityRefs.stream().toList();
      }
    } else {
      checkRBAC(accountIdentifier, "delete");
      entities = fetchEntities(accountIdentifier, request.getFilter(), null, skipEntityRefs);
      entityRefs = getEntityRefs(entities);
    }
    List<String> entityRefsToDelete = deleteInDBAndAuditChangesIfRequired(
        accountIdentifier, entityRefs, request.getProperty(), dryRun, isIdpV2Enabled);
    return CatalogCustomPropertiesMapper.toDeleteResponse(request.getProperty(), entityRefsToDelete);
  }

  @Override
  public CustomPropertyByEntityGetResponse getCustomPropertiesForEntity(String accountIdentifier, String entityRef) {
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    entityRef = isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(entityRef)
                               : BackstageUtils.getFullyQualifiedEntityRef(entityRef);
    if (isIdpV2Enabled) {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = kindScopeIdentifier.getLeft();
      kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      String scope = kindScopeIdentifier.getMiddle();
      String identifier = kindScopeIdentifier.getRight();
      Pair<String, String> orgProjectIdentifier = catalogServiceHelper.getOrgProjectFromScope(scope);
      CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(
          accountIdentifier, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight(), kind, identifier);
      String owner =
          catalogEntity == null ? null : catalogServiceHelper.resolveOwner(accountIdentifier, catalogEntity.getOwner());
      catalogServiceHelper.checkRbacWithOwnerFallback(accountIdentifier, entityRef, owner, "view");
    }
    List<CatalogCustomPropertyEntity> entitiesInDB = ccpRepository.findByAccountIdentifierAndEntityRef(
        accountIdentifier, entityRef.replace("workflow:", "template:"));
    return CatalogCustomPropertiesMapper.toEntityGetResponse(entitiesInDB);
  }

  @Override
  public CustomPropertyResponse resolveCustomPropertiesForEntity(
      CustomPropertyByEntityRequest request, String accountIdentifier, boolean dryRun) {
    validateCustomPropertiesByEntityRequest(request);
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    String entityRef = isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(request.getEntityRef())
                                      : BackstageUtils.getFullyQualifiedEntityRef((request.getEntityRef()));
    Object entity;
    if (isIdpV2Enabled) {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = kindScopeIdentifier.getLeft();
      kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      String scope = kindScopeIdentifier.getMiddle();
      String identifier = kindScopeIdentifier.getRight();
      Pair<String, String> orgProjectIdentifier = catalogServiceHelper.getOrgProjectFromScope(scope);
      ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(
          accountIdentifier, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight()));
      CatalogEntity catalogEntity = findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);
      catalogServiceHelper.checkRbacWithOwnerFallback(accountIdentifier, entityRef,
          catalogServiceHelper.resolveOwner(accountIdentifier, catalogEntity.getOwner()), "edit");
      entity = catalogEntity;
    } else {
      checkRBAC(accountIdentifier, "edit");
      String entityUid = getEntityUidFromEntityRef(entityRef);
      entity = backstageService.findByAccountIdentifierAndEntityRef(accountIdentifier, entityUid);
    }
    List<CatalogCustomPropertyEntity> entitiesToAdd = new ArrayList<>();
    List<CatalogCustomPropertyEntity> entitiesToUpdate = new ArrayList<>();
    Map<String, PropertyValue> propertyValueMap = null;
    List<String> properties = new ArrayList<>();
    if (request.getProperty() != null) {
      properties.add(request.getProperty());
    } else {
      propertyValueMap = new LinkedHashMap<>();
      for (PropertyValue propertyValue : request.getProperties()) {
        propertyValueMap.put(propertyValue.getProperty(), propertyValue);
        properties.add(propertyValue.getProperty());
      }
    }
    Map<String, CatalogCustomPropertyEntity> entitiesInDBMap =
        new HashMap<>(getPropertiesByEntityFromDb(accountIdentifier, entityRef, properties));
    CatalogEntity processedEntity =
        processPropertiesByEntity(entity, request, propertyValueMap, entitiesInDBMap, entitiesToAdd, entitiesToUpdate);
    log.info(
        "Entity {} resolved with {} added and {} updated.", entityRef, entitiesToAdd.size(), entitiesToUpdate.size());

    saveInDBAndAuditChangesIfRequired(entitiesInDBMap, entitiesToAdd, entitiesToUpdate, accountIdentifier, dryRun,
        false, isIdpV2Enabled, Collections.singletonList(processedEntity));
    return CatalogCustomPropertiesMapper.toEntitySaveResponse(
        entityRef, entitiesToAdd.size() + entitiesToUpdate.size());
  }

  @Override
  public CustomPropertyResponse deleteCustomPropertiesForEntity(
      CustomPropertyByEntityDeleteRequest request, String accountIdentifier, boolean dryRun) {
    if (request.getProperty() != null) {
      checkApiEndpointDataProtection(request.getProperty());
    }
    if (request.getProperties() != null) {
      for (String prop : request.getProperties()) {
        checkApiEndpointDataProtection(prop);
      }
    }
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    String entityRef = isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(request.getEntityRef())
                                      : BackstageUtils.getFullyQualifiedEntityRef(request.getEntityRef());
    CatalogEntity catalogEntity = null;
    if (isIdpV2Enabled) {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = kindScopeIdentifier.getLeft();
      kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
      String scope = kindScopeIdentifier.getMiddle();
      String identifier = kindScopeIdentifier.getRight();
      Pair<String, String> orgProjectIdentifier = catalogServiceHelper.getOrgProjectFromScope(scope);
      ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(
          accountIdentifier, orgProjectIdentifier.getLeft(), orgProjectIdentifier.getRight()));
      try {
        catalogEntity = findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);
      } catch (InvalidRequestException e) {
        log.error(e.getMessage());
      }
      String owner =
          catalogEntity == null ? null : catalogServiceHelper.resolveOwner(accountIdentifier, catalogEntity.getOwner());
      catalogServiceHelper.checkRbacWithOwnerFallback(accountIdentifier, entityRef, owner, "edit");
    } else {
      checkRBAC(accountIdentifier, "delete");
    }
    List<String> properties = new ArrayList<>();
    if (request.getProperty() != null) {
      properties.add(request.getProperty());
    } else {
      properties.addAll(request.getProperties());
    }
    Map<String, CatalogCustomPropertyEntity> entitiesInDBMap =
        new HashMap<>(getPropertiesByEntityFromDb(accountIdentifier, entityRef, properties));
    List<String> propertiesToDelete = new ArrayList<>(entitiesInDBMap.keySet());
    if (!dryRun && !isEmpty(propertiesToDelete)) {
      CatalogEntity catalogEntityForLocking = catalogEntity;
      // Acquire the processor lock before the transaction for API entities; the helper returns
      // empty for non-API or unloadable entities.
      List<AcquiredLock<?>> apiEntityLocks = (isIdpV2Enabled && catalogEntityForLocking != null)
          ? acquireApiEntityLocks(List.of(catalogEntityForLocking))
          : Collections.emptyList();
      // Re-resolve the entity inside the lock+transaction to close the TOCTOU window; the pre-lock
      // load only decides whether to lock. The holder carries the saved state out to the
      // post-transaction sendCatalogEventsToRedis call.
      CatalogEntity[] savedCatalogEntityRef = new CatalogEntity[] {null};
      boolean isTransactionSuccessful;
      try {
        isTransactionSuccessful =
            Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
              ccpRepository.deleteMulti(
                  accountIdentifier, entityRef.replace("workflow:", "template:"), propertiesToDelete);
              if (isIdpV2Enabled) {
                // Re-fetch under the lock so the mutation runs on fresh state; skip the catalog
                // save if it was deleted meanwhile (the CCP records are still removed).
                CatalogEntity freshCatalogEntity =
                    catalogEntityForLocking == null ? null : reloadFreshEntity(catalogEntityForLocking);
                if (freshCatalogEntity != null) {
                  Map<String, Object> decorator = freshCatalogEntity.getFailSafeDecorator();
                  Map<String, Object> processedData = freshCatalogEntity.getFailSafeProcessedData(decorator);
                  removeProperties(processedData, propertiesToDelete);
                  Map<String, Object> metadata = (Map<String, Object>) processedData.get(METADATA);
                  if (isEmpty(metadata)) {
                    processedData.remove(METADATA);
                  }
                  decorator.put(PROCESSED_DATA, processedData);
                  freshCatalogEntity.setDecorator(decorator);
                  freshCatalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(freshCatalogEntity));
                  catalogEntityRepository.save(freshCatalogEntity);
                  catalogServiceHelper.publishAsyncComputationEvent(
                      accountIdentifier, null, CatalogUtils.getEntityUUId(freshCatalogEntity));
                  savedCatalogEntityRef[0] = freshCatalogEntity;
                }
              } else {
                deleteCustomPropertiesFromIdpApp(accountIdentifier,
                    CustomPropertiesDeleteRequest.builder()
                        .propertiesMap(Map.of(entityRef, propertiesToDelete))
                        .build());
              }
              entitiesInDBMap.forEach(
                  (property,
                      entity) -> outboxService.save(new CatalogCustomPropertyDeleteEvent(accountIdentifier, entity)));
              return true;
            }));
      } finally {
        releaseLocks(apiEntityLocks);
      }

      if (isTransactionSuccessful && isIdpV2Enabled && savedCatalogEntityRef[0] != null) {
        idpToHarnessHelper.sendCatalogEventsToRedis(List.of(savedCatalogEntityRef[0]), UPDATE_ACTION);
      }
    }
    return CatalogCustomPropertiesMapper.toEntityDeleteResponse(entityRef, propertiesToDelete);
  }

  @Override
  public CustomPropertyByFieldGetResponse getCustomPropertiesForCustomProperty(
      String accountIdentifier, String property) {
    List<CatalogCustomPropertyEntity> entitiesInDB =
        ccpRepository.findByAccountIdentifierAndField(accountIdentifier, property);
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (isIdpV2Enabled) {
      Set<String> entityRefsInDB =
          entitiesInDB.stream().map(CatalogCustomPropertyEntity::getEntityRef).collect(Collectors.toSet());
      Set<String> allowedEntityRefs = catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
          accountIdentifier, getEntityRefToOwnerMap(accountIdentifier, entityRefsInDB), "view");
      if (allowedEntityRefs == null) {
        throw new NGAccessDeniedException(
            "Missing Catalog / Workflow / Group View Permission", USER, Collections.emptyList());
      }
      Set<String> modifiedAllowedEntityRefs = new HashSet<>(allowedEntityRefs);
      entitiesInDB.removeIf(entity -> !modifiedAllowedEntityRefs.contains(entity.getEntityRef()));
    }
    return CatalogCustomPropertiesMapper.toFieldGetResponse(entitiesInDB);
  }

  @Override
  public CustomPropertyResponse resolveEntitiesForCustomProperty(
      CustomPropertyByFieldRequest request, String accountIdentifier, boolean dryRun) {
    validateCustomPropertiesByEntityRequest(request);
    List<CatalogCustomPropertyEntity> entitiesToAdd = new ArrayList<>();
    List<CatalogCustomPropertyEntity> entitiesToUpdate = new ArrayList<>();

    Map<String, EntityValue> entityValueMap = null;
    List<String> entityRefs = new ArrayList<>();
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (request.getEntityRef() != null) {
      entityRefs.add(isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(request.getEntityRef())
                                    : BackstageUtils.getFullyQualifiedEntityRef(request.getEntityRef()));
    } else {
      entityValueMap = request.getEntityRefs().stream().collect(Collectors.toMap(entityValue
          -> isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(entityValue.getEntityRef())
                            : BackstageUtils.getFullyQualifiedEntityRef(entityValue.getEntityRef()),
          Function.identity()));
      entityRefs = entityValueMap.keySet().stream().toList();
    }
    Set entities;
    if (isIdpV2Enabled) {
      kindServiceHelper.validateKindsIfExistInEntityRefs(accountIdentifier, entityRefs);
      Set<CatalogEntity> fetchedEntities = new HashSet<>(
          catalogEntityRepository
              .getEntities(accountIdentifier,
                  catalogServiceHelper
                      .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, null, String.join(",", entityRefs))
                      .getLeft(),
                  null, -1, null, null, null, String.join(",", entityRefs), null, null, null, null, null, null, null)
              .getContent());
      Map<String, String> entityRefToOwner = new HashMap<>();
      fetchedEntities.forEach(entity -> entityRefToOwner.put(CatalogUtils.entityRef(entity), entity.getOwner()));
      Set<String> allowedEntityRefs =
          catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(accountIdentifier, entityRefToOwner, "edit");
      if (isEmpty(allowedEntityRefs)) {
        throw new NGAccessDeniedException(
            "Missing Catalog / Workflow / Group Edit Permission", USER, Collections.emptyList());
      }
      entityRefs = allowedEntityRefs.stream().distinct().collect(Collectors.toList());
      entities = fetchedEntities.stream()
                     .filter(entity -> allowedEntityRefs.contains(CatalogUtils.entityRef(entity)))
                     .collect(Collectors.toSet());
    } else {
      checkRBAC(accountIdentifier, "edit");
      entities = fetchEntities(accountIdentifier, null, entityRefs, null);
    }

    Map<String, CatalogCustomPropertyEntity> entitiesInDBMap =
        new HashMap<>(getEntitiesByPropertyFromDb(entityRefs, accountIdentifier, request.getProperty()));
    List<CatalogEntity> processedEntities = new ArrayList<>();
    for (Object entity : entities) {
      processedEntities.add(
          processEntityByProperty(entity, request, entityValueMap, entitiesInDBMap, entitiesToAdd, entitiesToUpdate));
    }
    log.info("Property {} added in {} and updated in {} entities.", request.getProperty(), entitiesToAdd.size(),
        entitiesToUpdate.size());

    saveInDBAndAuditChangesIfRequired(entitiesInDBMap, entitiesToAdd, entitiesToUpdate, accountIdentifier, dryRun, true,
        isIdpV2Enabled, processedEntities);
    return CatalogCustomPropertiesMapper.toPropertySaveResponse(
        request.getProperty(), entitiesToAdd.size() + entitiesToUpdate.size());
  }

  @Override
  public CustomPropertyResponse deleteEntitiesForCustomProperty(
      CustomPropertyByFieldDeleteRequest request, String accountIdentifier, boolean dryRun) {
    checkApiEndpointDataProtection(request.getProperty());
    List<String> entityRefs = new ArrayList<>();
    boolean isIdpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (request.getEntityRef() != null) {
      entityRefs.add(isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(request.getEntityRef())
                                    : BackstageUtils.getFullyQualifiedEntityRef(request.getEntityRef()));
    } else {
      request.getEntityRefs().forEach(entityRef
          -> entityRefs.add(isIdpV2Enabled ? CatalogUtils.getFullyQualifiedEntityRef(entityRef)
                                           : BackstageUtils.getFullyQualifiedEntityRef(entityRef)));
    }
    if (isIdpV2Enabled) {
      kindServiceHelper.validateKindsIfExistInEntityRefs(accountIdentifier, entityRefs);
      Set<String> allowedEntityRefs = catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(
          accountIdentifier, getEntityRefToOwnerMap(accountIdentifier, new HashSet<>(entityRefs)), "edit");
      if (isEmpty(allowedEntityRefs)) {
        throw new NGAccessDeniedException(
            "Missing Catalog and Workflow Edit Permission", USER, Collections.emptyList());
      }
      Set<String> modifiedAllowedEntityRefs = new HashSet<>(allowedEntityRefs);
      entityRefs.retainAll(modifiedAllowedEntityRefs);
    } else {
      checkRBAC(accountIdentifier, "delete");
    }
    List<String> entityRefsToDelete = deleteInDBAndAuditChangesIfRequired(
        accountIdentifier, entityRefs, request.getProperty(), dryRun, isIdpV2Enabled);
    return CatalogCustomPropertiesMapper.toPropertyDeleteResponse(request.getProperty(), entityRefsToDelete);
  }

  @Override
  public void toggleCustomProperties(String harnessAccount, Boolean enabled) {
    Optional<NamespaceEntity> namespaceEntityOptional = namespaceService.getEntityForAccountIdentifier(harnessAccount);
    namespaceEntityOptional.ifPresentOrElse(
        namespaceEntity
        -> {
          NamespaceEntity.Metadata metadata = namespaceEntity.getMetadata();
          boolean oldEnabled = metadata.isCatalogCustomPropertiesEnabled();
          metadata.setCatalogCustomPropertiesEnabled(enabled);
          namespaceService.save(namespaceEntity);
          if (oldEnabled != enabled) {
            if (enabled) {
              outboxService.save(new CatalogCustomPropertyEnableEvent(harnessAccount));
            } else {
              outboxService.save(new CatalogCustomPropertyDisableEvent(harnessAccount));
            }
            updateCustomPropertiesMetadata(harnessAccount, CUSTOM_PROPERTIES_METADATA_KEY, String.valueOf(enabled));
          }
        },
        () -> { throw new InvalidRequestException(String.format("Account %s is invalid", harnessAccount)); });
  }

  @Override
  public EntityRefs fetchEntityRefs(String accountIdentifier, String searchTerm) {
    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    EntityRefs entityRefs = new EntityRefs();
    List<String> entityRefIds = new ArrayList<>();
    if (idpV2Enabled) {
      List<EntityResponse> entities =
          catalogService
              .getEntities(accountIdentifier, 0, 20, null, searchTerm, false, catalogServiceHelper.getAllScopes(), null,
                  null, null, null, null, null, null, null, null, false)
              .getEntityResponses();
      entities.forEach(entity -> entityRefIds.add(entity.getEntityRef()));
    } else {
      String url = String.format(CATALOG_API_SUFFIX, accountIdentifier, searchTerm);
      Object response = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.toString(), Map.class);
      List<Map<String, Object>> items = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "items");

      for (Map<String, Object> item : items) {
        CommonUtils.normalizeSystemField(item);
      }

      List<BackstageCatalogEntity> entities = convert(objectMapper, items, BackstageCatalogEntity.class);
      entities.forEach(entity -> entityRefIds.add(CatalogUtils.getEntityRef(entity)));
    }
    entityRefs.setEntityRefs(entityRefIds);
    return entityRefs;
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityRefs) {
    List<String> entityRefs = ccpRepository.findUniqueEntityRefs(accountIdentifier);
    log.info("Totally {} unique records present in CatalogCustomProperties collection for account {}",
        entityRefs.size(), accountIdentifier);

    for (String entityRef : entityRefs) {
      int colonIndex = entityRef.indexOf(':');
      int slashIndex = entityRef.indexOf('/');

      if (colonIndex == -1 || slashIndex == -1) {
        continue;
      }
      String kind = entityRef.substring(0, colonIndex).toLowerCase();
      String namespace = entityRef.substring(colonIndex + 1, slashIndex).toLowerCase();
      String name = entityRef.substring(slashIndex + 1).toLowerCase();
      if (!namespace.equals("account") && !namespace.contains(".")) {
        String modifiedEntityRef = kind + ":account/" + name;
        String modifiedEntityRefForConflict = kind + ":account/" + namespace + "_" + name;
        if (conflictedEntityRefs.contains(modifiedEntityRefForConflict)) {
          modifiedEntityRef = modifiedEntityRefForConflict;
        }
        try {
          UpdateResult updateResult = ccpRepository.updateEntityRef(accountIdentifier, entityRef, modifiedEntityRef);
          log.info("Totally {} records modified in CatalogCustomProperties collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityRef);
        } catch (DuplicateKeyException e) {
          log.error(
              "Duplicate key exception occurred while modifying entityRef {} for CatalogCustomProperties collection",
              entityRef, e);
        }
      }
    }
  }

  @Override
  public void modifyScopeForEntityRef(String accountIdentifier, String existingEntityRef, String modifiedEntityRef) {
    try {
      UpdateResult updateResult =
          ccpRepository.updateEntityRef(accountIdentifier, existingEntityRef, modifiedEntityRef);
      log.info("Totally {} records modified in CatalogCustomProperties collection for IDP 2.0 MigrationAPI Operation "
              + "for account {}, identifier {}",
          updateResult.getModifiedCount(), accountIdentifier, existingEntityRef);
    } catch (Exception e) {
      log.error("Error occurred while modifying CatalogCustomProperties collection for IDP 2.0 MigrationAPI Operation "
              + "for account {}, identifier {}",
          accountIdentifier, existingEntityRef, e);
    }
  }

  /**
   * Re-reads each API entity under the lock and re-applies CCP mutations to the fresh decorator.
   * Closes the lost-update window: upstream built its version from a read taken before the lock.
   * Non-API entities pass through unchanged.
   */
  @SuppressWarnings("unchecked")
  List<CatalogEntity> rebuildProcessedEntitiesFromFreshState(List<CatalogEntity> processedEntities,
      List<CatalogCustomPropertyEntity> entitiesToAdd, List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    if (isEmpty(processedEntities)) {
      return processedEntities;
    }
    List<CatalogCustomPropertyEntity> allCcpRecords = new ArrayList<>();
    if (entitiesToAdd != null) {
      allCcpRecords.addAll(entitiesToAdd);
    }
    if (entitiesToUpdate != null) {
      allCcpRecords.addAll(entitiesToUpdate);
    }

    List<CatalogEntity> rebuilt = new ArrayList<>(processedEntities.size());
    for (CatalogEntity stale : processedEntities) {
      if (stale == null) {
        continue;
      }
      if (!Constants.API_KIND.equalsIgnoreCase(stale.getKind())) {
        rebuilt.add(stale);
        continue;
      }
      CatalogEntity fresh = reloadFreshEntity(stale);
      if (fresh == null) {
        log.warn("CCP fresh re-read returned null for entity {} (kind={}); it may have been deleted "
                + "concurrently. Skipping save; catalog_custom_properties records still persist.",
            stale.getIdentifier(), stale.getKind());
        continue;
      }

      String freshEntityRef = CatalogUtils.entityRef(fresh);
      Map<String, Object> decorator = fresh.getFailSafeDecorator();
      Map<String, Object> processedData = fresh.getFailSafeProcessedData(decorator);
      for (CatalogCustomPropertyEntity ccpRecord : allCcpRecords) {
        if (!freshEntityRef.equals(ccpRecord.getEntityRef())) {
          continue;
        }
        Object value = readValueForSingleEntity(ccpRecord.getValue(), Object.class);
        Map<String, Object> propertyMap = buildMap(ccpRecord.getField(), value);
        // mode can be null on older records; coerce to REPLACE.
        CustomPropertiesBase.ModeEnum mode =
            ccpRecord.getMode() == null ? CustomPropertiesBase.ModeEnum.REPLACE : ccpRecord.getMode();
        insertMap(processedData, propertyMap, mode);
      }
      decorator.put(PROCESSED_DATA, processedData);
      fresh.setDecorator(decorator);
      rebuilt.add(fresh);
    }
    return rebuilt;
  }

  /** Loads the entity fresh from MongoDB; returns {@code null} if it was deleted meanwhile. */
  private CatalogEntity reloadFreshEntity(CatalogEntity stale) {
    try {
      return catalogEntityRepository
          .findByParentUniqueIdAndKindAndIdentifier(stale.getParentUniqueId(), stale.getKind(), stale.getIdentifier())
          .orElse(null);
    } catch (Exception ex) {
      log.warn("Failed to re-read entity {} for fresh state under lock: {}", stale.getIdentifier(), ex.getMessage());
      return null;
    }
  }

  /** Acquires processor per-entity locks, sorted by id to avoid deadlock across concurrent CCP requests. */
  List<AcquiredLock<?>> acquireApiEntityLocks(List<CatalogEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<CatalogEntity> apiEntitiesSorted =
        entities.stream()
            .filter(e -> e != null && Constants.API_KIND.equalsIgnoreCase(e.getKind()))
            .sorted(Comparator.comparing(e -> Optional.ofNullable(e.getId()).orElse("")))
            .collect(Collectors.toList());
    if (apiEntitiesSorted.isEmpty()) {
      return Collections.emptyList();
    }
    List<AcquiredLock<?>> acquired = new ArrayList<>(apiEntitiesSorted.size());
    for (CatalogEntity entity : apiEntitiesSorted) {
      String lockName = ApiEndpointProcessor.lockNameFor(entity);
      AcquiredLock<?> lock = resourceLocker.acquireLock(lockName, CCP_LOCK_TIMEOUT_MINS, CCP_LOCK_WAIT_SECONDS);
      if (lock == null) {
        log.warn("CCP could not acquire processor lock '{}' for entity {} after {}s; aborting to avoid lost-update.",
            lockName, entity.getIdentifier(), CCP_LOCK_WAIT_SECONDS);
        releaseLocks(acquired);
        throw new InvalidRequestException(
            String.format("Could not acquire lock for API entity '%s'. The endpoint extraction processor may be "
                    + "running on this entity. Please retry in a few seconds.",
                entity.getIdentifier()));
      }
      acquired.add(lock);
    }
    return acquired;
  }

  void releaseLocks(List<AcquiredLock<?>> locks) {
    if (locks == null) {
      return;
    }
    for (AcquiredLock<?> lock : locks) {
      try {
        resourceLocker.releaseLock(lock);
      } catch (Exception ex) {
        log.warn("Failed to release CCP-acquired API entity lock; will expire after {} min. Error: {}",
            CCP_LOCK_TIMEOUT_MINS, ex.getMessage());
      }
    }
  }

  private Map<String, String> getEntityRefToOwnerMap(String accountIdentifier, Set<String> entityRefs) {
    Map<String, String> entityRefToOwner = new HashMap<>();
    if (isEmpty(entityRefs)) {
      return entityRefToOwner;
    }
    entityRefs.forEach(entityRef -> entityRefToOwner.put(entityRef, null));
    List<CatalogEntity> catalogEntities =
        catalogEntityRepository
            .getEntities(accountIdentifier,
                catalogServiceHelper
                    .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, null, String.join(",", entityRefs))
                    .getLeft(),
                null, -1, null, null, null, String.join(",", entityRefs), null, null, null, null, null, null, null)
            .getContent();
    catalogEntities.forEach(
        catalogEntity -> entityRefToOwner.put(CatalogUtils.entityRef(catalogEntity), catalogEntity.getOwner()));
    return entityRefToOwner;
  }

  private void checkRBAC(String accountIdentifier, String permission) {
    // Skip RBAC for pure service-to-service calls (no user context)
    if (RbacUtils.isPureServiceToServiceCall()) {
      return;
    }

    accessControlClient.checkForAccessOrThrow(
        RbacUtils.fromSecurityPrincipalType(SecurityContextBuilder.getPrincipal().getType()),
        ResourceScope.of(accountIdentifier, null, null), Resource.of(IDP_ADVANCED_CONFIGURATION, null),
        "idp_advancedconfiguration_" + permission,
        String.format("Missing Advanced Configuration %s Permission", permission));
  }

  private void validateCustomPropertiesByEntityRequest(CustomPropertyByEntityRequest request) {
    if (isEmpty(request.getEntityRef())) {
      throw new InvalidRequestException("entity_ref field is required and cannot be empty");
    }
    if (request.getProperty() == null && isEmpty(request.getProperties())) {
      throw new InvalidRequestException("Request should contain either property or properties");
    }
    if (request.getProperty() != null && !isEmpty(request.getProperties())) {
      throw new InvalidRequestException("Request should contain either property or properties, not both");
    }
    if (request.getProperty() != null) {
      checkForUnmodifiableFields(request.getProperty());
      if (request.getValue() == null) {
        throw new InvalidRequestException("value field is required");
      }
    }
    for (PropertyValue propertyValue : request.getProperties()) {
      checkForUnmodifiableFields(propertyValue.getProperty());
      if (propertyValue.getValue() == null) {
        throw new InvalidRequestException(
            String.format("value field is required for property %s", propertyValue.getProperty()));
      }
    }
  }

  private void checkForUnmodifiableFields(String property) {
    if (property.isEmpty()) {
      throw new InvalidRequestException("property field cannot be empty");
    }
    if (UNMODIFIABLE_FIELDS.contains(property.toLowerCase())) {
      throw new InvalidRequestException(String.format("Not allowed to modify %s property", property));
    }
    String[] dotSplit = property.split("\\.");
    if (dotSplit[0].equalsIgnoreCase("spec")) {
      throw new InvalidRequestException(String.format("Not allowed to modify spec properties - %s", property));
    }
    checkApiEndpointDataProtection(property);
  }

  // Blocks external writes/deletes to the system-managed metadata.apis subtree; only
  // metadata.apis.paths.<endpointKey>.enrichments.* is customer-writable. Not FF-gated, so no
  // garbage is seeded into accounts where extraction is off.
  static void checkApiEndpointDataProtection(String property) {
    List<String> tokens = tokenizeProperty(property);
    if (tokens.size() < 2) {
      return;
    }
    if (!"metadata".equalsIgnoreCase(tokens.get(0)) || !"apis".equalsIgnoreCase(tokens.get(1))) {
      return;
    }
    if (isEnrichmentsPath(tokens)) {
      return;
    }
    throw new InvalidRequestException(
        String.format("Not allowed to modify property '%s' — metadata.apis is system-managed by the API "
                + "endpoint extraction feature. The only customer-writable path inside this subtree is "
                + "metadata.apis.paths.\"<METHOD path>\".enrichments.* (e.g., "
                + "metadata.apis.paths.\"POST /v1/payments\".enrichments.riskScore).",
            property));
  }

  /** Matches {@code [metadata, apis, paths, <non-empty endpointKey>, enrichments, ...]}. */
  private static boolean isEnrichmentsPath(List<String> tokens) {
    if (tokens.size() < 5) {
      return false;
    }
    if (!"paths".equalsIgnoreCase(tokens.get(2))) {
      return false;
    }
    if (tokens.get(3) == null || tokens.get(3).isEmpty()) {
      return false;
    }
    return "enrichments".equalsIgnoreCase(tokens.get(4));
  }

  private void validateCustomPropertiesByEntityRequest(CustomPropertyByFieldRequest request) {
    if (isEmpty(request.getProperty())) {
      throw new InvalidRequestException("property field is required and cannot be empty");
    }
    checkForUnmodifiableFields(request.getProperty());
    if (request.getEntityRef() == null && isEmpty(request.getEntityRefs())) {
      throw new InvalidRequestException("Request should contain either entity_ref or entity_refs");
    }
    if (request.getEntityRef() != null && !isEmpty(request.getEntityRefs())) {
      throw new InvalidRequestException("Request should contain either entity_ref or entity_refs, not both");
    }
    if (request.getEntityRef() != null && request.getValue() == null) {
      throw new InvalidRequestException("value field is required");
    }
    for (EntityValue entityValue : request.getEntityRefs()) {
      if (entityValue.getValue() == null && request.getValue() == null) {
        throw new InvalidRequestException(String.format(
            "value field is required as %s entity_ref doesn't have corresponding value", entityValue.getEntityRef()));
      }
    }
  }

  private List<String> getEntityRefs(Set<Object> entities) {
    return entities.stream().map(CatalogUtils::getEntityRef).collect(Collectors.toList());
  }

  private Set<BackstageCatalogEntity> fetchEntities(
      String accountIdentifier, ScorecardFilter filter, List<String> entityRefs, List<String> skipEntityRefs) {
    Set<BackstageCatalogEntity> entities = new HashSet<>();
    if (filter != null) {
      List<String> skipEntityUids = new ArrayList<>();
      if (!isEmpty(skipEntityRefs)) {
        skipEntityRefs.forEach(entityUid -> skipEntityUids.add(getEntityUidFromEntityRef(entityUid)));
      }
      entities.addAll(backstageService.queryEntities(filter, accountIdentifier, skipEntityUids));
    }
    if (!isEmpty(entityRefs)) {
      List<String> entityUids = new ArrayList<>();
      entityRefs.forEach(entityUid -> entityUids.add(getEntityUidFromEntityRef(entityUid)));
      entities.addAll(
          backstageService.findAllByAccountIdentifierAndEntityRefs(accountIdentifier, new ArrayList<>(entityUids)));
    }
    return entities;
  }

  private CatalogEntity processEntity(Object entity, CustomPropertyFilterRequest request,
      Map<String, CatalogCustomPropertyEntity> entitiesInDBMap, List<CatalogCustomPropertyEntity> entitiesToAdd,
      List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    Object value = request.getValue();
    String entityRef = CatalogUtils.getEntityRefFromUid(entity);
    CustomPropertiesBase.ModeEnum mode = request.getMode() == null
        ? CustomPropertiesBase.ModeEnum.REPLACE
        : CustomPropertiesBase.ModeEnum.fromValue(request.getMode().toString());
    CatalogEntity processedEntity = validate(entity, request.getProperty(), mode, value);
    entitiesToBeAddedOrUpdated(entitiesInDBMap, entitiesToAdd, entitiesToUpdate,
        constructEntity(entity, request.getProperty(), value, mode), entityRef);
    return processedEntity;
  }

  private CatalogEntity processEntityByProperty(Object entity, CustomPropertyByFieldRequest request,
      Map<String, EntityValue> entityValueMap, Map<String, CatalogCustomPropertyEntity> entitiesInDBMap,
      List<CatalogCustomPropertyEntity> entitiesToAdd, List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    Object value = request.getValue();
    String entityRef = CatalogUtils.getEntityRefFromUid(entity);
    if (entityValueMap != null && entityValueMap.containsKey(entityRef)) {
      Object overrideValue = entityValueMap.get(entityRef).getValue();
      if (overrideValue != null) {
        value = overrideValue;
      }
    }
    CustomPropertiesBase.ModeEnum mode =
        request.getMode() == null ? CustomPropertiesBase.ModeEnum.REPLACE : request.getMode();
    CatalogEntity processedEntity = validate(entity, request.getProperty(), mode, value);
    entitiesToBeAddedOrUpdated(entitiesInDBMap, entitiesToAdd, entitiesToUpdate,
        constructEntity(entity, request.getProperty(), value, mode), entityRef);
    return processedEntity;
  }

  private CatalogEntity processPropertiesByEntity(Object entity, CustomPropertyByEntityRequest request,
      Map<String, PropertyValue> propertyValueMap, Map<String, CatalogCustomPropertyEntity> entitiesInDBMap,
      List<CatalogCustomPropertyEntity> entitiesToAdd, List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    if (propertyValueMap != null) {
      return validateAndConstructEntities(entity, propertyValueMap, entitiesInDBMap, entitiesToAdd, entitiesToUpdate);
    } else {
      CustomPropertiesBase.ModeEnum mode =
          request.getMode() == null ? CustomPropertiesBase.ModeEnum.REPLACE : request.getMode();
      CatalogEntity processedEntity = validate(entity, request.getProperty(), mode, request.getValue());
      entitiesToBeAddedOrUpdated(entitiesInDBMap, entitiesToAdd, entitiesToUpdate,
          constructEntity(entity, request.getProperty(), request.getValue(), mode), request.getProperty());
      return processedEntity;
    }
  }

  private CatalogEntity validate(Object entity, String field, CustomPropertiesBase.ModeEnum mode, Object value) {
    String yaml = entity instanceof CatalogEntity ? ((CatalogEntity) entity).getDecoratedYaml()
                                                  : YamlUtils.writeObjectAsYaml(entity);
    Map<String, Object> entityMap = YamlUtils.loadYamlStringAsMap(yaml);
    return addPropertyAndValidateSchema(entity, entityMap, field, value, mode);
  }

  private CatalogEntity validateAndConstructEntities(Object entity, Map<String, PropertyValue> propertyValueMap,
      Map<String, CatalogCustomPropertyEntity> entitiesInDBMap, List<CatalogCustomPropertyEntity> entitiesToAdd,
      List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    String yaml = entity instanceof CatalogEntity ? ((CatalogEntity) entity).getDecoratedYaml()
                                                  : YamlUtils.writeObjectAsYaml(entity);
    CatalogEntity catalogEntity = null;
    Map<String, Object> entityMap = YamlUtils.loadYamlStringAsMap(yaml);
    for (Map.Entry<String, PropertyValue> entry : propertyValueMap.entrySet()) {
      String field = entry.getValue().getProperty();
      Object value = entry.getValue().getValue();
      CustomPropertiesBase.ModeEnum mode = entry.getValue().getMode() == null
          ? CustomPropertiesBase.ModeEnum.REPLACE
          : CustomPropertiesBase.ModeEnum.fromValue(entry.getValue().getMode().toString());
      catalogEntity = addPropertyAndValidateSchema(entity, entityMap, field, value, mode);
      entitiesToBeAddedOrUpdated(
          entitiesInDBMap, entitiesToAdd, entitiesToUpdate, constructEntity(entity, field, value, mode), field);
    }
    return catalogEntity;
  }

  private CatalogEntity addPropertyAndValidateSchema(
      Object entity, Map<String, Object> entityMap, String field, Object value, CustomPropertiesBase.ModeEnum mode) {
    Map<String, Object> contructMap = buildMap(field, value);
    insertMap(entityMap, contructMap, mode);
    String yaml = YamlUtils.writeObjectAsYaml(entityMap);
    if (entity instanceof CatalogEntity) {
      KindEntity kindEntity = kindServiceHelper.kindEntity(
          ((CatalogEntity) entity).getAccountIdentifier(), ((CatalogEntity) entity).getKind());
      catalogServiceHelper.validateAgainstJsonSchema(((CatalogEntity) entity).getKind(), yaml, kindEntity.getSchema());
      Map<String, Object> decorator = ((CatalogEntity) entity).getFailSafeDecorator();
      Map<String, Object> processedData = ((CatalogEntity) entity).getFailSafeProcessedData(decorator);
      insertMap(processedData, contructMap, mode);
      decorator.put(PROCESSED_DATA, processedData);
      ((CatalogEntity) entity).setDecorator(decorator);
      return (CatalogEntity) entity;
    } else {
      String schema = schemaCache.get(((BackstageCatalogEntity) entity).getKind());
      boolean isValid;
      try {
        isValid = ConfigManagerUtils.isValidSchema(yaml, schema).isEmpty();
      } catch (Exception e) {
        throw new UnexpectedException(
            String.format("Validation cannot be performed. Failed to load schema with error : %s", e.getMessage()), e);
      }

      if (!isValid) {
        throw new InvalidRequestException(
            String.format("Invalid field [%s] or value [%s] provided for entity [%s] for account [%s]", field, value,
                ((BackstageCatalogEntity) entity).getEntityUid(),
                ((BackstageCatalogEntity) entity).getAccountIdentifier()));
      }
    }
    return null;
  }

  private CatalogCustomPropertyEntity constructEntity(
      Object entity, String field, Object value, CustomPropertiesBase.ModeEnum mode) {
    CatalogCustomPropertyEntity ccpEntity;
    ccpEntity = CatalogCustomPropertyEntity.builder()
                    .accountIdentifier(entity instanceof CatalogEntity
                            ? ((CatalogEntity) entity).getAccountIdentifier()
                            : ((BackstageCatalogEntity) entity).getAccountIdentifier())
                    .entityRef(CatalogUtils.getEntityRefFromUid(entity))
                    .field(field)
                    .value(write(value))
                    .mode(mode)
                    .build();
    return ccpEntity;
  }

  private void entitiesToBeAddedOrUpdated(Map<String, CatalogCustomPropertyEntity> entitiesInDBMap,
      List<CatalogCustomPropertyEntity> entitiesToAdd, List<CatalogCustomPropertyEntity> entitiesToUpdate,
      CatalogCustomPropertyEntity ccpEntity, String key) {
    if (entitiesInDBMap.containsKey(key)) {
      CatalogCustomPropertyEntity entityFromDB = entitiesInDBMap.get(key);
      ccpEntity.setId(entityFromDB.getId());
      appendWithExistingValues(ccpEntity, entityFromDB);
      entitiesToUpdate.add(ccpEntity);
    } else {
      entitiesToAdd.add(ccpEntity);
    }
  }

  private void appendWithExistingValues(
      CatalogCustomPropertyEntity ccpEntity, CatalogCustomPropertyEntity entityFromDB) {
    if (CustomPropertiesBase.ModeEnum.APPEND.equals(ccpEntity.getMode())) {
      Object valuesInDB = readValueForSingleEntity(entityFromDB.getValue(), Object.class);
      Object values = readValueForSingleEntity(ccpEntity.getValue(), Object.class);
      if (valuesInDB instanceof List && values instanceof List) {
        ((List) valuesInDB).addAll((List) values);
      } else if (valuesInDB instanceof Map && values instanceof Map) {
        ((Map) valuesInDB).putAll((Map) values);
      } else {
        log.warn("Skipping append operation for existing values");
        return;
      }
      ccpEntity.setValue(write(valuesInDB));
    }
  }

  private void saveInDBAndAuditChangesIfRequired(Map<String, CatalogCustomPropertyEntity> entitiesInDBMap,
      List<CatalogCustomPropertyEntity> entitiesToAdd, List<CatalogCustomPropertyEntity> entitiesToUpdate,
      String accountIdentifier, boolean dryRun, boolean byEntityRef, boolean isIdpV2Enabled,
      List<CatalogEntity> processedEntities) {
    if (dryRun) {
      return;
    }
    List<AcquiredLock<?>> apiEntityLocks =
        isIdpV2Enabled ? acquireApiEntityLocks(processedEntities) : Collections.emptyList();
    try {
      // Re-fetch and re-apply mutations under the lock, before the transaction so the read isn't
      // bound to its snapshot. Non-API entities pass through unchanged.
      List<CatalogEntity> freshProcessedEntities = isIdpV2Enabled
          ? rebuildProcessedEntitiesFromFreshState(processedEntities, entitiesToAdd, entitiesToUpdate)
          : processedEntities;

      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        List<CatalogCustomPropertyEntity> allEntities = new ArrayList<>();
        allEntities.addAll(entitiesToAdd);
        allEntities.addAll(entitiesToUpdate);
        ccpRepository.saveAll(entitiesToAdd);
        ccpRepository.saveAll(entitiesToUpdate);
        if (isIdpV2Enabled) {
          freshProcessedEntities.forEach(catalogEntity
              -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
          catalogEntityRepository.saveAll(freshProcessedEntities);
          idpToHarnessHelper.sendCatalogEventsToRedis(freshProcessedEntities, UPDATE_ACTION);
          for (CatalogEntity entity : freshProcessedEntities) {
            catalogServiceHelper.publishAsyncComputationEvent(
                accountIdentifier, null, CatalogUtils.getEntityUUId(entity));
          }
        } else {
          sendCustomPropertiesToIdpApp(accountIdentifier, allEntities);
        }
        GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
        setUserContext(accountIdentifier);
        entitiesToAdd.forEach(
            entity -> outboxService.save(new CatalogCustomPropertyCreateEvent(accountIdentifier, entity)));
        entitiesToUpdate.forEach(newEntity -> {
          if (entitiesInDBMap.containsKey(byEntityRef ? newEntity.getEntityRef() : newEntity.getField())) {
            CatalogCustomPropertyEntity oldEntity =
                entitiesInDBMap.get(byEntityRef ? newEntity.getEntityRef() : newEntity.getField());
            if (!oldEntity.getValue().equals(newEntity.getValue())) {
              outboxService.save(new CatalogCustomPropertyUpdateEvent(accountIdentifier, oldEntity, newEntity));
            }
          }
        });
        GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
        return true;
      }));
    } finally {
      releaseLocks(apiEntityLocks);
    }
  }

  private List<String> deleteInDBAndAuditChangesIfRequired(
      String accountIdentifier, List<String> entityRefs, String property, boolean dryRun, boolean isIdpV2Enabled) {
    Map<String, CatalogCustomPropertyEntity> entitiesInDBMap =
        getEntitiesByPropertyFromDb(entityRefs, accountIdentifier, property);
    List<String> entityRefsToDelete = new ArrayList<>(entitiesInDBMap.keySet());
    if (dryRun || isEmpty(entityRefsToDelete)) {
      return entityRefsToDelete;
    }
    // Pre-load entities outside the transaction to acquire processor locks before entering it.
    // The query is re-issued inside; under the lock only this path mutates the API decorators, so
    // the snapshot won't drift due to the processor.
    List<CatalogEntity> apiEntitiesForLocking = Collections.emptyList();
    if (isIdpV2Enabled) {
      List<CatalogEntity> preLoad =
          catalogEntityRepository
              .getEntities(accountIdentifier,
                  catalogServiceHelper
                      .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, null, String.join(",", entityRefs))
                      .getLeft(),
                  null, -1, null, null, null, String.join(",", entityRefs), null, null, null, null, null, null, null)
              .getContent();
      apiEntitiesForLocking = preLoad == null ? Collections.emptyList() : preLoad;
    }
    List<AcquiredLock<?>> apiEntityLocks = acquireApiEntityLocks(apiEntitiesForLocking);
    try {
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        List<String> modifiedEntityRefsToDelete =
            entityRefsToDelete.stream()
                .map(entityRefToDelete -> entityRefToDelete.replace("workflow:", "template:"))
                .toList();
        ccpRepository.deleteMulti(accountIdentifier, modifiedEntityRefsToDelete, property);
        if (isIdpV2Enabled) {
          List<CatalogEntity> catalogEntities = catalogEntityRepository
                                                    .getEntities(accountIdentifier,
                                                        catalogServiceHelper
                                                            .getScopeInfosBasedOnScopesAndEntityRefs(
                                                                accountIdentifier, null, String.join(",", entityRefs))
                                                            .getLeft(),
                                                        null, -1, null, null, null, String.join(",", entityRefs), null,
                                                        null, null, null, null, null, null)
                                                    .getContent();
          List<CatalogEntity> finalCatalogEntities = new ArrayList<>();
          for (CatalogEntity catalogEntity : catalogEntities) {
            Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
            Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);
            removeProperties(processedData, Collections.singletonList(property));
            decorator.put(PROCESSED_DATA, processedData);
            catalogEntity.setDecorator(decorator);
            finalCatalogEntities.add(catalogEntity);
          }
          finalCatalogEntities.forEach(catalogEntity
              -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
          catalogEntityRepository.saveAll(finalCatalogEntities);
          for (CatalogEntity entity : finalCatalogEntities) {
            catalogServiceHelper.publishAsyncComputationEvent(
                accountIdentifier, null, CatalogUtils.getEntityUUId(entity));
          }
          idpToHarnessHelper.sendCatalogEventsToRedis(finalCatalogEntities, UPDATE_ACTION);
        } else {
          Map<String, List<String>> fieldEntityRefs = new HashMap<>();
          fieldEntityRefs.put(property, entityRefsToDelete);
          deleteCustomPropertiesFromIdpApp(
              accountIdentifier, CustomPropertiesDeleteRequest.builder().entityRefsMap(fieldEntityRefs).build());
        }
        entitiesInDBMap.forEach(
            (entityRef, entity) -> outboxService.save(new CatalogCustomPropertyDeleteEvent(accountIdentifier, entity)));
        return true;
      }));
    } finally {
      releaseLocks(apiEntityLocks);
    }
    return entityRefsToDelete;
  }

  @SuppressWarnings("unchecked")
  private void sendCustomPropertiesToIdpApp(String accountIdentifier, List<CatalogCustomPropertyEntity> entities) {
    try {
      Object response = getGeneralResponse(backstageResourceClient.createOrUpdateCustomProperties(
          accountIdentifier, buildCustomPropertiesRequest(entities)));
      Map<String, Integer> responseMap = (Map<String, Integer>) readValueForObject(response, Map.class);
      if (!responseMap.get(RESPONSE_STATUS).equals(200)) {
        log.error("IDP App API call to add/update custom properties failed status {} and error {}",
            responseMap.get(RESPONSE_STATUS), responseMap.get("error"));
      }
    } catch (Exception e) {
      log.error("Sending custom properties to IDP App failed with error : {}", e.getMessage(), e);
    }
  }

  private void sendCatalogCustomPropertyEventsToRedis(String accountIdentifier, List<String> yamls) {
    User user = new User();
    user.setName(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(USERNAME));
    user.email(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(EMAIL));
    user.setUuid(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID));
    for (String yaml : yamls) {
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(yaml);
      String orgIdentifier = from(entityYamlMap, "orgIdentifier", String.class);
      String projectIdentifier = from(entityYamlMap, "projectIdentifier", String.class);
      String identifier = from(entityYamlMap, "identifier", String.class);
      String kind = from(entityYamlMap, "kind", String.class);
      idpServiceMiscRedisProducer.publishIDPCatalogCustomPropertyEventsToRedis(accountIdentifier, orgIdentifier,
          projectIdentifier, CatalogUtils.entityRef(kind, orgIdentifier, projectIdentifier, identifier), yaml, "update",
          user);
    }
  }

  @SuppressWarnings("unchecked")
  private void deleteCustomPropertiesFromIdpApp(String accountIdentifier, CustomPropertiesDeleteRequest request) {
    try {
      Object response = getGeneralResponse(backstageResourceClient.deleteCustomProperties(accountIdentifier, request));
      Map<String, Integer> responseMap = (Map<String, Integer>) readValueForObject(response, Map.class);
      if (!responseMap.get(RESPONSE_STATUS).equals(200)) {
        log.error("IDP App API call to delete custom properties failed status {} and error {}",
            responseMap.get(RESPONSE_STATUS), responseMap.get("error"));
      }
    } catch (Exception e) {
      log.error("Deleting custom properties from IDP App failed with error : {}", e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private void updateCustomPropertiesMetadata(String accountIdentifier, String key, String value) {
    try {
      Object response = getGeneralResponse(
          backstageResourceClient.updateCatalogMetadata(accountIdentifier, buildCatalogMetadataRequest(key, value)));
      Map<String, Integer> responseMap = (Map<String, Integer>) readValueForObject(response, Map.class);
      if (!responseMap.get(RESPONSE_STATUS).equals(200)) {
        log.error("IDP App API call to add/update catalog metadata failed status {} and error {}",
            responseMap.get(RESPONSE_STATUS), responseMap.get("error"));
      }
    } catch (Exception e) {
      log.error("Update catalog metadata in IDP App failed with error : {}", e.getMessage(), e);
    }
  }

  private CatalogMetadataRequest buildCatalogMetadataRequest(String key, String value) {
    return CatalogMetadataRequest.builder().key(key).value(value).build();
  }

  private CustomPropertiesRequest buildCustomPropertiesRequest(List<CatalogCustomPropertyEntity> entities) {
    List<CustomPropertiesRequest.CustomProperty> properties = new ArrayList<>();
    entities.forEach(entity
        -> properties.add(CustomPropertiesRequest.CustomProperty.builder()
                              .entityRef(entity.getEntityRef())
                              .field(entity.getField())
                              .value(entity.getValue())
                              .mode(entity.getMode())
                              .build()));
    return CustomPropertiesRequest.builder().customProperties(properties).build();
  }

  private Map<String, CatalogCustomPropertyEntity> getEntitiesByPropertyFromDb(
      List<String> entityRefs, String accountIdentifier, String key) {
    List<CatalogCustomPropertyEntity> entitiesInDB;
    if (!isEmpty(entityRefs)) {
      List<String> modifiedEntityRefs =
          entityRefs.stream().map(entityRef -> entityRef.replace("workflow:", "template:")).toList();
      entitiesInDB =
          ccpRepository.findByAccountIdentifierAndEntityRefInAndField(accountIdentifier, modifiedEntityRefs, key);
    } else {
      entitiesInDB = ccpRepository.findByAccountIdentifierAndField(accountIdentifier, key);
    }
    return entitiesInDB.stream().collect(
        Collectors.toMap(CatalogCustomPropertyEntity::getEntityRef, Function.identity()));
  }

  private Map<String, CatalogCustomPropertyEntity> getPropertiesByEntityFromDb(
      String accountIdentifier, String entityRef, List<String> properties) {
    List<CatalogCustomPropertyEntity> propertiesInDB;
    if (!isEmpty(properties)) {
      propertiesInDB = ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
          accountIdentifier, entityRef.replace("workflow:", "template:"), properties);
    } else {
      propertiesInDB = ccpRepository.findByAccountIdentifierAndEntityRef(
          accountIdentifier, entityRef.replace("workflow:", "template:"));
    }
    return propertiesInDB.stream().collect(
        Collectors.toMap(CatalogCustomPropertyEntity::getField, Function.identity()));
  }

  private CatalogEntity findByParentUniqueIdAndKindAndIdentifier(
      String parentUniqueId, String kind, String identifier) {
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    if (optionalCatalogEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("No record found for kind %s, identifier %s", kind, identifier));
    }
    return optionalCatalogEntity.get();
  }

  private void setUserContext(String accountIdentifier) {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && StringUtils.isNotBlank(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID))) {
      String uuid = SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID);
      String username = SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(USERNAME);
      String email = SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(EMAIL);
      GlobalContextManager.upsertGlobalContextRecord(
          PrincipalContextData.builder()
              .principal(new UserPrincipal(uuid, email, username, accountIdentifier))
              .build());
    }
  }
}
