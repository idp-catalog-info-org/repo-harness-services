/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.buildSpacePath;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.EntityLinks.LinkTarget;
import io.harness.idp.catalog.events.EntityLinkCreateEvent;
import io.harness.idp.catalog.events.EntityLinkDeleteEvent;
import io.harness.idp.catalog.events.EntityLinkUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.mappers.EntityLinkMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.EntityLinkRepository;
import io.harness.idp.common.YamlUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.EntityLinkExistsResponse;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.FieldMapping;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsRequest;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsResponse;
import io.harness.spec.server.idp.v1.model.ResolvedFieldValue;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DuplicateKeyException;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class EntityLinkServiceImpl implements EntityLinkService {
  private static final String SUPPORTED_KIND = "workflow";
  private final EntityLinkRepository entityLinkRepository;
  private final CatalogEntityRepository catalogEntityRepository;
  private final CatalogServiceHelper catalogServiceHelper;
  private final CatalogScopeResolver scopeResolver;
  private final OutboxService outboxService;
  private final TransactionHelper transactionHelper;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public EntityLinkResponse createLink(String accountIdentifier, EntityLinkRequest request) {
    String entityRef = request.getEntityLink().getEntityRef();
    validateSupportedKind(entityRef);
    validateIntegrationsLimit(request);
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "edit");

    Pair<String, String> orgProject = catalogServiceHelper.getOrgProjectFromEntityRef(entityRef);
    EntityLinks entity = EntityLinkMapper.toEntity(accountIdentifier, request);
    try {
      return transactionHelper.performTransaction(() -> {
        EntityLinks saved = entityLinkRepository.save(entity);
        outboxService.save(new EntityLinkCreateEvent(
            accountIdentifier, orgProject.getLeft(), orgProject.getRight(), entityRef, toAuditJson(saved)));
        return EntityLinkMapper.toDTO(saved);
      });
    } catch (DuplicateKeyException e) {
      throw new InvalidRequestException(
          String.format("Entity link already exists for [%s]. Use update to modify.", entityRef));
    }
  }

  @Override
  public EntityLinkResponse updateLink(String accountIdentifier, String entityRef, EntityLinkRequest request) {
    validateSupportedKind(entityRef);
    validateIntegrationsLimit(request);
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "edit");
    Optional<EntityLinks> existing =
        entityLinkRepository.findByAccountIdentifierAndEntityRef(accountIdentifier, entityRef);

    if (existing.isEmpty()) {
      throw new InvalidRequestException(String.format("No entity link found for [%s].", entityRef));
    }

    Pair<String, String> orgProject = catalogServiceHelper.getOrgProjectFromEntityRef(entityRef);
    String oldJson = toAuditJson(existing.get());
    EntityLinks updatedEntity = EntityLinkMapper.toEntity(accountIdentifier, request);
    EntityLinks.EntityLinksBuilder builder = existing.get()
                                                 .toBuilder()
                                                 .scopes(updatedEntity.getScopes())
                                                 .targets(updatedEntity.getTargets())
                                                 .fieldMappings(updatedEntity.getFieldMappings());
    if (request.getEntityLink().getIntegrations() != null) {
      builder.integrations(updatedEntity.getIntegrations());
    }
    EntityLinks entityToUpdate = builder.build();

    return transactionHelper.performTransaction(() -> {
      EntityLinks saved = entityLinkRepository.save(entityToUpdate);
      outboxService.save(new EntityLinkUpdateEvent(
          accountIdentifier, orgProject.getLeft(), orgProject.getRight(), entityRef, oldJson, toAuditJson(saved)));
      return EntityLinkMapper.toDTO(saved);
    });
  }

  @Override
  public void deleteLink(String accountIdentifier, String entityRef) {
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "edit");
    Pair<String, String> orgProject = catalogServiceHelper.getOrgProjectFromEntityRef(entityRef);
    Optional<EntityLinks> existing =
        entityLinkRepository.findByAccountIdentifierAndEntityRef(accountIdentifier, entityRef);
    transactionHelper.performTransaction(() -> {
      entityLinkRepository.deleteByAccountIdentifierAndEntityRef(accountIdentifier, entityRef);
      existing.ifPresent(e
          -> outboxService.save(new EntityLinkDeleteEvent(
              accountIdentifier, orgProject.getLeft(), orgProject.getRight(), entityRef, toAuditJson(e))));
      return null;
    });
  }

  @Override
  public EntityLinkResponse getLink(String accountIdentifier, String entityRef) {
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "view");
    Optional<EntityLinks> entity =
        entityLinkRepository.findByAccountIdentifierAndEntityRef(accountIdentifier, entityRef);
    return entity.map(EntityLinkMapper::toDTO).orElse(null);
  }

  @Override
  public EntityLinkExistsResponse linkExists(String accountIdentifier, String entityRef) {
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "view");
    Optional<EntityLinks> linkOpt =
        entityLinkRepository.findByAccountIdentifierAndEntityRef(accountIdentifier, entityRef);

    EntityLinkExistsResponse response = new EntityLinkExistsResponse();
    if (linkOpt.isEmpty() || isEmpty(linkOpt.get().getTargets())) {
      response.setLinked(false);
      response.setMatchingEntitiesCount(0);
      return response;
    }

    EntityLinks link = linkOpt.get();
    long matchingEntities = countMatchingCatalogEntities(accountIdentifier, link);
    response.setLinked(matchingEntities > 0);
    response.setMatchingEntitiesCount((int) matchingEntities);
    return response;
  }

  private long countMatchingCatalogEntities(String accountIdentifier, EntityLinks link) {
    Set<String> parentUniqueIds = null;
    if (!isEmpty(link.getScopes())) {
      ScopeTopology topology = scopeResolver.getOrBuildTopology(accountIdentifier);
      String scopeString = String.join(",", link.getScopes());
      parentUniqueIds = new HashSet<>(topology.resolveParentUniqueIds(scopeString));
    }
    List<Pair<String, String>> kindTypePairs =
        link.getTargets().stream().map(t -> Pair.of(t.getEntityKind(), t.getEntityType())).collect(Collectors.toList());
    return catalogEntityRepository.countEntitiesByKindTypeAndScopes(accountIdentifier, kindTypePairs, parentUniqueIds);
  }

  @Override
  public List<String> getLinkedEntities(
      String accountIdentifier, String entityKind, String entityType, String entityRef) {
    List<EntityLinks> links = entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(
        accountIdentifier, entityKind, entityType);

    if (isEmpty(links)) {
      return List.of();
    }

    ScopeTopology topology = scopeResolver.getOrBuildTopology(accountIdentifier);

    Optional<CatalogEntity> entityOpt = lookupEntityByRef(entityRef, topology);
    if (entityOpt.isEmpty()) {
      return List.of();
    }
    CatalogEntity entity = entityOpt.get();
    String entityParentUniqueId = entity.getParentUniqueId();

    List<String> result = new ArrayList<>();
    for (EntityLinks link : links) {
      if (isLinkApplicableForEntity(link, entityKind, entityType, entityParentUniqueId, topology)
          && isIntegrationApplicableForEntity(link, accountIdentifier, entity)) {
        result.add(link.getEntityRef());
      }
    }
    return result;
  }

  @Override
  public ResolveFieldMappingsResponse resolveFieldMappings(
      String accountIdentifier, String scope, String kind, String identifier, ResolveFieldMappingsRequest request) {
    String entityRef = kind + ":" + scope + "/" + identifier;
    catalogServiceHelper.checkCrudRbac(accountIdentifier, entityRef, "view");

    ScopeTopology topology = scopeResolver.getOrBuildTopology(accountIdentifier);
    Optional<CatalogEntity> entityOpt = lookupEntityByRef(entityRef, topology);
    if (entityOpt.isEmpty()) {
      throw new InvalidRequestException(String.format("Entity not found for [%s/%s/%s].", scope, kind, identifier));
    }

    CatalogEntity catalogEntity = entityOpt.get();
    if (isEmpty(catalogEntity.getYaml())) {
      throw new InvalidRequestException(
          String.format("Entity [%s/%s/%s] has no YAML content.", scope, kind, identifier));
    }
    Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(catalogEntity.getYaml());

    ResolveFieldMappingsResponse response = new ResolveFieldMappingsResponse();
    List<ResolvedFieldValue> resolvedValues = new ArrayList<>();

    if (isEmpty(request.getFieldMappings())) {
      response.setResolvedValues(resolvedValues);
      return response;
    }

    for (FieldMapping mapping : request.getFieldMappings()) {
      ResolvedFieldValue resolved = new ResolvedFieldValue();
      resolved.setInput(mapping.getInput());
      resolved.setValue(extractValueFromPath(yamlMap, mapping.getEntityFieldSource()));
      resolvedValues.add(resolved);
    }

    response.setResolvedValues(resolvedValues);
    return response;
  }

  private void validateSupportedKind(String entityRef) {
    if (entityRef == null || !entityRef.toLowerCase().startsWith(SUPPORTED_KIND + ":")) {
      throw new InvalidRequestException(
          String.format("Entity linking is currently only supported for kind [%s].", SUPPORTED_KIND));
    }
  }

  private void validateIntegrationsLimit(EntityLinkRequest request) {
    if (request.getEntityLink().getIntegrations() != null && request.getEntityLink().getIntegrations().size() > 1) {
      throw new InvalidRequestException("Only one integration reference is supported per entity link.");
    }
  }

  private boolean isIntegrationApplicableForEntity(EntityLinks link, String accountIdentifier, CatalogEntity entity) {
    if (isEmpty(link.getIntegrations())) {
      return true;
    }
    Map<String, Object> decorator = entity.getFailSafeDecorator();
    Map<String, Object> processedData = entity.getFailSafeProcessedData(decorator);
    Map<String, Object> metadata = (Map<String, Object>) processedData.get("metadata");
    if (metadata == null) {
      return false;
    }
    Map<String, Object> integrationMap = (Map<String, Object>) metadata.get("integration");
    if (integrationMap == null) {
      return false;
    }
    for (EntityLinks.IntegrationReference ref : link.getIntegrations()) {
      String normalizedSpacePath = ref.getSpacePath().replace(accountIdentifier, "account");
      String integrationId = ref.getIdentifier();
      Map<String, Object> spaceMap = (Map<String, Object>) integrationMap.get(normalizedSpacePath);
      if (spaceMap != null && spaceMap.containsKey(integrationId)) {
        return true;
      }
    }
    return false;
  }

  private boolean isLinkApplicableForEntity(
      EntityLinks link, String entityKind, String entityType, String entityParentUniqueId, ScopeTopology topology) {
    if (link.getTargets() == null) {
      return false;
    }
    boolean targetMatches = link.getTargets().stream().anyMatch(target
        -> entityKind.equalsIgnoreCase(target.getEntityKind()) && entityType.equalsIgnoreCase(target.getEntityType()));
    if (!targetMatches) {
      return false;
    }
    List<String> scopes = link.getScopes();
    if (isEmpty(scopes)) {
      return true;
    }
    String scopeString = String.join(",", scopes);
    Set<String> allowedParentUniqueIds = new HashSet<>(topology.resolveParentUniqueIds(scopeString));
    return allowedParentUniqueIds.contains(entityParentUniqueId);
  }

  private Optional<CatalogEntity> lookupEntityByRef(String entityRef, ScopeTopology topology) {
    Triple<String, String, String> parsed = catalogServiceHelper.getKindScopeIdentifier(entityRef);
    String kind = parsed.getLeft();
    String scopeExpression = parsed.getMiddle();
    String identifier = parsed.getRight();

    if (scopeExpression.contains("*") || scopeExpression.contains(",")) {
      throw new InvalidRequestException(
          String.format("entityRef scope must be fully qualified (e.g. account, account.org, account.org.project); "
                  + "wildcards and comma-lists are not supported. Got: [%s]",
              scopeExpression));
    }

    List<String> parentUniqueIds = topology.resolveParentUniqueIds(scopeExpression);
    if (isEmpty(parentUniqueIds)) {
      return Optional.empty();
    }
    // An entity lives at exactly one scope — stop at the first match.
    for (String parentUniqueId : parentUniqueIds) {
      Optional<CatalogEntity> entity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      if (entity.isPresent()) {
        return entity;
      }
    }
    return Optional.empty();
  }

  private String extractValueFromPath(Map<String, Object> yamlMap, String path) {
    Object value = YamlUtils.getByPath(yamlMap, path);
    if (value == null) {
      return null;
    }
    return value instanceof String ? (String) value : value.toString();
  }

  @Override
  public List<String> getEntityLinksByIntegration(
      String accountIdentifier, String integrationIdentifier, String orgIdentifier, String projectIdentifier) {
    String spacePath = buildSpacePath(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EntityLinks> links = entityLinkRepository.findByAccountIdentifierAndIntegrationIdentifier(
        accountIdentifier, integrationIdentifier, spacePath);
    if (isEmpty(links)) {
      return List.of();
    }
    return links.stream().map(EntityLinks::getEntityRef).collect(Collectors.toList());
  }

  @Override
  public void deleteLinksForIntegration(String accountIdentifier, String integrationIdentifier, String spacePath) {
    long deletedCount = entityLinkRepository.deleteByIntegration(accountIdentifier, integrationIdentifier, spacePath);
    log.info("Deleted {} entity link(s) for integration [{}] spacePath [{}] in account [{}]", deletedCount,
        integrationIdentifier, spacePath, accountIdentifier);
  }

  private String toAuditJson(EntityLinks entity) {
    try {
      Map<String, Object> auditMap = new LinkedHashMap<>();
      auditMap.put("entityRef", entity.getEntityRef());
      auditMap.put("scopes", entity.getScopes());
      auditMap.put("targets", entity.getTargets());
      // TODO: P2 — direct named entity linking
      // auditMap.put("entityIdentifiers", entity.getEntityIdentifiers());
      auditMap.put("fieldMappings", entity.getFieldMappings());
      return objectMapper.writeValueAsString(auditMap);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize entity link for audit", e);
      return "{}";
    }
  }
}
