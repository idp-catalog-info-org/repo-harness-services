/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.REFERENCED_TYPES;
import static io.harness.idp.catalog.utils.Constants.SUB_COMPONENT_OF;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class RelationsProcessor {
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject io.harness.idp.catalog.repositories.KindEntityRepository kindEntityRepository;
  @Inject KindServiceHelper kindServiceHelper;

  public List<CatalogEntity> establishRelations(CatalogEntity catalogEntity) {
    List<CatalogEntity> referencedEntities = new ArrayList<>();
    Map<String, Set<String>> relations = catalogEntity.getRelations();
    String owner = catalogEntity.getOwner();
    if (!isEmpty(owner)) {
      establishRelationsInternal(owner, catalogEntity, "ownedBy", "ownerOf", referencedEntities, "group", "user", null);
    }
    if (relations != null) {
      relations.forEach((k, v) -> {
        if (v.size() > 1000) {
          throw new InvalidRequestException("Number of entities being referenced for a type cannot exceed 1000");
        }

        v.forEach(entityRef -> {
          if (!isEmpty(entityRef)) {
            if (k.equalsIgnoreCase("providesApis")) {
              establishRelationsInternal(entityRef, catalogEntity, "providesApis", "apiProvidedBy", referencedEntities,
                  "component", null, null);
            }
            if (k.equalsIgnoreCase("apiProvidedBy")) {
              establishRelationsInternal(entityRef, catalogEntity, "apiProvidedBy", "providesApis", referencedEntities,
                  "component", null, null);
            }
            if (k.equalsIgnoreCase("consumesApis")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "consumesApis", "apiConsumedBy", referencedEntities, "api", null, null);
            }
            if (k.equalsIgnoreCase("apiConsumedBy")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "apiConsumedBy", "consumesApis", referencedEntities, "api", null, null);
            }
            if (k.equalsIgnoreCase("dependsOn")) {
              establishRelationsInternal(entityRef, catalogEntity, "dependsOn", "dependencyOf", referencedEntities,
                  "component", "resource", null);
            }
            if (k.equalsIgnoreCase("dependencyOf")) {
              establishRelationsInternal(entityRef, catalogEntity, "dependencyOf", "dependsOn", referencedEntities,
                  "component", "resource", null);
            }
            if (k.equalsIgnoreCase("partOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "partOf", "hasPart", referencedEntities, "component", "api", "system");
            }
            if (k.equalsIgnoreCase("hasPart")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "hasPart", "partOf", referencedEntities, "component", "api", "resource");
            }
            if (k.equalsIgnoreCase("memberOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "memberOf", "hasMember", referencedEntities, "group", null, null);
            }
            if (k.equalsIgnoreCase("hasMember")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "hasMember", "memberOf", referencedEntities, "user", null, null);
            }
            if (k.equalsIgnoreCase("childOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "childOf", "parentOf", referencedEntities, "group", null, null);
            }
            if (k.equalsIgnoreCase("parentOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "parentOf", "childOf", referencedEntities, "group", null, null);
            }
            if (k.equalsIgnoreCase("leaderOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "leaderOf", "hasLeader", referencedEntities, "group", null, null);
            }
            if (k.equalsIgnoreCase("hasLeader")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "hasLeader", "leaderOf", referencedEntities, "user", "group", null);
            }
            if (k.equalsIgnoreCase("ownedBy") && !entityRef.equals(owner)) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "ownedBy", "ownerOf", referencedEntities, "group", "user", null);
            }
            if (k.equalsIgnoreCase("ownerOf")) {
              establishRelationsInternal(
                  entityRef, catalogEntity, "ownerOf", "ownedBy", referencedEntities, "group", "user", null);
            }
          }
        });
      });
    }

    return referencedEntities;
  }

  public List<CatalogEntity> updateRelations(CatalogEntity existingCatalogEntity, CatalogEntity catalogEntity) {
    List<CatalogEntity> referencedEntities = new ArrayList<>();

    Set<String> relationsKeys = new HashSet<>();
    if (existingCatalogEntity.getRelations() != null) {
      relationsKeys.addAll(existingCatalogEntity.getRelations().keySet());
    }
    if (catalogEntity.getRelations() != null) {
      relationsKeys.addAll(catalogEntity.getRelations().keySet());
    }

    Map<String, Object> existingSpec =
        existingCatalogEntity.getSpec() != null ? existingCatalogEntity.getSpec() : Collections.emptyMap();
    Map<String, Object> newSpec = catalogEntity.getSpec() != null ? catalogEntity.getSpec() : Collections.emptyMap();
    relationsKeys.removeIf(
        key -> REFERENCED_TYPES.contains(key) && !existingSpec.containsKey(key) && !newSpec.containsKey(key));

    for (String relationKey : relationsKeys) {
      Set<String> existingEntityRelations = existingCatalogEntity.getRelations() != null
          ? existingCatalogEntity.getRelations().getOrDefault(relationKey, Collections.emptySet())
          : Collections.emptySet();
      Set<String> updatedEntityRelations = catalogEntity.getRelations() != null
          ? catalogEntity.getRelations().getOrDefault(relationKey, Collections.emptySet())
          : Collections.emptySet();

      List<String> added = new ArrayList<>(updatedEntityRelations);
      added.removeAll(existingEntityRelations);

      List<String> removed = new ArrayList<>(existingEntityRelations);
      removed.removeAll(updatedEntityRelations);

      if (Math.abs(added.size() - removed.size()) > 1000) {
        throw new InvalidRequestException("Number of entities being referenced for a type cannot exceed 1000");
      }

      if (!isEmpty(added) || !isEmpty(removed)) {
        added.forEach(addedRelation -> {
          if (!isEmpty(addedRelation)) {
            if (relationKey.equalsIgnoreCase("providesApis")) {
              establishRelationsInternal(addedRelation, catalogEntity, "providesApis", "apiProvidedBy",
                  referencedEntities, "component", null, null);
            }
            if (relationKey.equalsIgnoreCase("apiProvidedBy")) {
              establishRelationsInternal(addedRelation, catalogEntity, "apiProvidedBy", "providesApis",
                  referencedEntities, "component", null, null);
            }
            if (relationKey.equalsIgnoreCase("consumesApis")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "consumesApis", "apiConsumedBy", referencedEntities, "api", null, null);
            }
            if (relationKey.equalsIgnoreCase("apiConsumedBy")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "apiConsumedBy", "consumesApis", referencedEntities, "api", null, null);
            }
            if (relationKey.equalsIgnoreCase("dependsOn")) {
              establishRelationsInternal(addedRelation, catalogEntity, "dependsOn", "dependencyOf", referencedEntities,
                  "component", "resource", null);
            }
            if (relationKey.equalsIgnoreCase("dependencyOf")) {
              establishRelationsInternal(addedRelation, catalogEntity, "dependencyOf", "dependsOn", referencedEntities,
                  "component", "resource", null);
            }
            if (relationKey.equalsIgnoreCase("partOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "partOf", "hasPart", referencedEntities, "component", "api", "system");
            }
            if (relationKey.equalsIgnoreCase("hasPart")) {
              establishRelationsInternal(addedRelation, catalogEntity, "hasPart", "partOf", referencedEntities,
                  "component", "api", "resource");
            }
            if (relationKey.equalsIgnoreCase("memberOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "memberOf", "hasMember", referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("hasMember")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "hasMember", "memberOf", referencedEntities, "user", null, null);
            }
            if (relationKey.equalsIgnoreCase("childOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "childOf", "parentOf", referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("parentOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "parentOf", "childOf", referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("leaderOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "leaderOf", "hasLeader", referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("hasLeader")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "hasLeader", "leaderOf", referencedEntities, "user", "group", null);
            }
            if (relationKey.equalsIgnoreCase("ownedBy")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "ownedBy", "ownerOf", referencedEntities, "group", "user", null);
            }
            if (relationKey.equalsIgnoreCase("ownerOf")) {
              establishRelationsInternal(
                  addedRelation, catalogEntity, "ownerOf", "ownedBy", referencedEntities, "group", "user", null);
            }
          }
        });

        removed.forEach(removedRelation -> {
          if (!isEmpty(removedRelation)) {
            if (relationKey.equalsIgnoreCase("providesApis")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "apiProvidedBy",
                  referencedEntities, "component", null, null);
            }
            if (relationKey.equalsIgnoreCase("apiProvidedBy")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "providesApis",
                  referencedEntities, "component", null, null);
            }
            if (relationKey.equalsIgnoreCase("consumesApis")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "apiConsumedBy",
                  referencedEntities, "api", null, null);
            }
            if (relationKey.equalsIgnoreCase("apiConsumedBy")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "consumesApis",
                  referencedEntities, "api", null, null);
            }
            if (relationKey.equalsIgnoreCase("dependsOn")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "dependencyOf",
                  referencedEntities, "component", "resource", null);
            }
            if (relationKey.equalsIgnoreCase("dependencyOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "dependsOn",
                  referencedEntities, "component", "resource", null);
            }
            if (relationKey.equalsIgnoreCase("partOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "hasPart",
                  referencedEntities, "component", "api", "resource");
            }
            if (relationKey.equalsIgnoreCase("hasPart")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "partOf",
                  referencedEntities, "component", "api", "system");
            }
            if (relationKey.equalsIgnoreCase("memberOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "hasMember",
                  referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("hasMember")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "memberOf",
                  referencedEntities, "user", null, null);
            }
            if (relationKey.equalsIgnoreCase("childOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "parentOf",
                  referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("parentOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "childOf",
                  referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("leaderOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "hasLeader",
                  referencedEntities, "group", null, null);
            }
            if (relationKey.equalsIgnoreCase("hasLeader")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "leaderOf",
                  referencedEntities, "user", "group", null);
            }
            if (relationKey.equalsIgnoreCase("ownedBy")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "ownerOf",
                  referencedEntities, "group", "user", null);
            }
            if (relationKey.equalsIgnoreCase("ownerOf")) {
              removeRelationInternal(removedRelation, catalogEntity, existingCatalogEntity, "ownedBy",
                  referencedEntities, "group", "user", null);
            }
          }
        });
      }
    }

    return referencedEntities;
  }

  public List<CatalogEntity> changeScope(CatalogEntity catalogEntity, ScopeInfo destinationScopeInfo) {
    List<CatalogEntity> referencedEntities = new ArrayList<>();
    Map<String, Set<String>> relations = catalogEntity.getRelations();
    String owner = catalogEntity.getOwner();
    if (!isEmpty(owner)) {
      changeRelationsScopeInternal(
          owner, catalogEntity, destinationScopeInfo, "ownerOf", referencedEntities, "group", "user", null);
    }

    if (relations != null) {
      relations.forEach((k, v) -> v.forEach(entityRef -> {
        if (!isEmpty(entityRef)) {
          if (k.equalsIgnoreCase("providesApis")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "apiProvidedBy",
                referencedEntities, "component", null, null);
          }
          if (k.equalsIgnoreCase("apiProvidedBy")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "providesApis",
                referencedEntities, "component", null, null);
          }
          if (k.equalsIgnoreCase("consumesApis")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "apiConsumedBy", referencedEntities, "api", null, null);
          }
          if (k.equalsIgnoreCase("apiConsumedBy")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "consumesApis", referencedEntities, "api", null, null);
          }
          if (k.equalsIgnoreCase("dependsOn")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "dependencyOf",
                referencedEntities, "component", "resource", null);
          }
          if (k.equalsIgnoreCase("dependencyOf")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "dependsOn",
                referencedEntities, "component", "resource", null);
          }
          if (k.equalsIgnoreCase("partOf")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "hasPart", referencedEntities,
                "component", "api", "system");
          }
          if (k.equalsIgnoreCase("hasPart")) {
            changeRelationsScopeInternal(entityRef, catalogEntity, destinationScopeInfo, "partOf", referencedEntities,
                "component", "api", "resource");
          }
          if (k.equalsIgnoreCase("memberOf")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "hasMember", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("hasMember")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "memberOf", referencedEntities, "user", null, null);
          }
          if (k.equalsIgnoreCase("childOf")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "parentOf", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("parentOf")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "childOf", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("leaderOf")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "hasLeader", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("hasLeader")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "leaderOf", referencedEntities, "user", "group", null);
          }
          if (k.equalsIgnoreCase("ownedBy")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "ownerOf", referencedEntities, "group", "user", null);
          }
          if (k.equalsIgnoreCase("ownerOf")) {
            changeRelationsScopeInternal(
                entityRef, catalogEntity, destinationScopeInfo, "ownedBy", referencedEntities, "group", "user", null);
          }
        }
      }));
    }
    return referencedEntities;
  }

  private void changeRelationsScopeInternal(String entityRef, CatalogEntity catalogEntity,
      ScopeInfo destinationScopeInfo, String targetRelation, List<CatalogEntity> referencedEntities,
      String firstAdditionalKindToCheck, String secondAdditionalKindToCheck, String thirdAdditionalKindToCheck) {
    String[] entityRefSplit = entityRef.split(":");
    String kind;
    String parentUniqueId;
    String identifier;
    Optional<CatalogEntity> optionalCatalogEntity = Optional.empty();
    if (entityRefSplit.length == 1) {
      kind = firstAdditionalKindToCheck;
      parentUniqueId = catalogEntity.getParentUniqueId();
      identifier = entityRefSplit[0];
      optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      if (optionalCatalogEntity.isEmpty() && !isEmpty(secondAdditionalKindToCheck)) {
        kind = secondAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && !isEmpty(thirdAdditionalKindToCheck)) {
        kind = thirdAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && isPartOfOrHasPart(targetRelation)) {
        optionalCatalogEntity = lookupByGroupingKinds(parentUniqueId, identifier, catalogEntity.getAccountIdentifier());
      }
    } else if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      parentUniqueId = catalogEntity.getParentUniqueId();
      String scopeIdentifier = entityRefSplit[1];
      int slashIndex = scopeIdentifier.indexOf("/");
      String scope = slashIndex != -1 ? scopeIdentifier.substring(0, slashIndex) : "";
      identifier = slashIndex != -1 ? scopeIdentifier.substring(slashIndex + 1) : scopeIdentifier;

      if (!isEmpty(scope)) {
        String[] scopeSplit = scope.split("\\.");
        ScopeInfo scopeInfo = null;
        if (scopeSplit.length == 3) {
          scopeInfo = getResponse(
              scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], scopeSplit[2]));
        } else if (scopeSplit.length == 2) {
          scopeInfo =
              getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], null));
        } else if (scopeSplit.length == 1) {
          scopeInfo = getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), null, null));
        }
        if (scopeInfo != null) {
          parentUniqueId = scopeInfo.getUniqueId();
        }
      }

      optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    }
    if (optionalCatalogEntity.isEmpty()) {
      return;
    }
    CatalogEntity referencedEntity = optionalCatalogEntity.get();
    Map<String, Object> spec = referencedEntity.getSpec();
    if (isEmpty(spec)) {
      return;
    }

    String targetSpeckKey;
    if (targetRelation.equals("partOf") && spec.containsKey(catalogEntity.getKind())
        && isGroupingKind(catalogEntity.getKind(), catalogEntity.getAccountIdentifier())) {
      targetSpeckKey = catalogEntity.getKind();
    } else {
      targetSpeckKey = targetRelation;
    }

    if (!(targetRelation.equals("hasPart")
            && isGroupingKind(referencedEntity.getKind(), catalogEntity.getAccountIdentifier()))) {
      if (targetRelation.equals("partOf") && spec.containsKey(SUB_COMPONENT_OF)) {
        Object subcompValue = spec.get(SUB_COMPONENT_OF);
        if (subcompValue instanceof String) {
          String catalogEntityRef = CatalogUtils.entityRef(catalogEntity);
          if (((String) subcompValue).equals(catalogEntityRef) || catalogEntityRef.endsWith("/" + subcompValue)) {
            spec.put(SUB_COMPONENT_OF,
                CatalogUtils.entityRef(catalogEntity.getKind(), destinationScopeInfo.getOrgIdentifier(),
                    destinationScopeInfo.getProjectIdentifier(), catalogEntity.getIdentifier()));
            referencedEntity.setSpec(spec);
          }
        }
      }
      List<String> targetRelationList = from(spec, targetSpeckKey, List.class);
      if (!isEmpty(targetRelationList)) {
        targetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(catalogEntity)));
        targetRelationList.add(CatalogUtils.entityRef(catalogEntity.getKind(), destinationScopeInfo.getOrgIdentifier(),
            destinationScopeInfo.getProjectIdentifier(), catalogEntity.getIdentifier()));
        spec.put(targetSpeckKey, targetRelationList);
        referencedEntity.setSpec(spec);
      }
    }
    Map<String, Set<String>> referencedEntityRelations = referencedEntity.getRelations();
    if (isEmpty(referencedEntityRelations)) {
      return;
    }
    Set<String> referencedEntityRelationsTargetRelationList = referencedEntityRelations.get(targetRelation);
    if (isEmpty(referencedEntityRelationsTargetRelationList)) {
      return;
    }
    referencedEntityRelationsTargetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(catalogEntity)));
    referencedEntityRelationsTargetRelationList.add(
        CatalogUtils.entityRef(catalogEntity.getKind(), destinationScopeInfo.getOrgIdentifier(),
            destinationScopeInfo.getProjectIdentifier(), catalogEntity.getIdentifier()));
    referencedEntityRelations.put(targetRelation, referencedEntityRelationsTargetRelationList);
    referencedEntity.setRelations(referencedEntityRelations);
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(catalogEntity.getAccountIdentifier());
    referencedEntity.setYaml(CatalogMapper.presentationYaml(referencedEntity, groupingKinds));
    referencedEntities.add(referencedEntity);
  }

  public List<CatalogEntity> disbandRelations(CatalogEntity catalogEntity) {
    List<CatalogEntity> referencedEntities = new ArrayList<>();
    Map<String, Set<String>> relations = catalogEntity.getRelations();
    String owner = catalogEntity.getOwner();
    if (!isEmpty(owner)) {
      disbandRelationsInternal(owner, catalogEntity, "ownerOf", referencedEntities, "group", "user", null);
    }

    if (relations != null) {
      relations.forEach((k, v) -> v.forEach(entityRef -> {
        if (!isEmpty(entityRef)) {
          if (k.equalsIgnoreCase("providesApis")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "apiProvidedBy", referencedEntities, "component", null, null);
          }
          if (k.equalsIgnoreCase("apiProvidedBy")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "providesApis", referencedEntities, "component", null, null);
          }
          if (k.equalsIgnoreCase("consumesApis")) {
            disbandRelationsInternal(entityRef, catalogEntity, "apiConsumedBy", referencedEntities, "api", null, null);
          }
          if (k.equalsIgnoreCase("apiConsumedBy")) {
            disbandRelationsInternal(entityRef, catalogEntity, "consumesApis", referencedEntities, "api", null, null);
          }
          if (k.equalsIgnoreCase("dependsOn")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "dependencyOf", referencedEntities, "component", "resource", null);
          }
          if (k.equalsIgnoreCase("dependencyOf")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "dependsOn", referencedEntities, "component", "resource", null);
          }
          if (k.equalsIgnoreCase("partOf")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "hasPart", referencedEntities, "component", "api", "system");
          }
          if (k.equalsIgnoreCase("hasPart")) {
            disbandRelationsInternal(
                entityRef, catalogEntity, "partOf", referencedEntities, "component", "api", "resource");
          }
          if (k.equalsIgnoreCase("memberOf")) {
            disbandRelationsInternal(entityRef, catalogEntity, "hasMember", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("hasMember")) {
            disbandRelationsInternal(entityRef, catalogEntity, "memberOf", referencedEntities, "user", null, null);
          }
          if (k.equalsIgnoreCase("childOf")) {
            disbandRelationsInternal(entityRef, catalogEntity, "parentOf", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("parentOf")) {
            disbandRelationsInternal(entityRef, catalogEntity, "childOf", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("leaderOf")) {
            disbandRelationsInternal(entityRef, catalogEntity, "hasLeader", referencedEntities, "group", null, null);
          }
          if (k.equalsIgnoreCase("hasLeader")) {
            disbandRelationsInternal(entityRef, catalogEntity, "leaderOf", referencedEntities, "user", "group", null);
          }
          if (k.equalsIgnoreCase("ownedBy")) {
            disbandRelationsInternal(entityRef, catalogEntity, "ownerOf", referencedEntities, "group", "user", null);
          }
          if (k.equalsIgnoreCase("ownerOf")) {
            disbandRelationsInternal(entityRef, catalogEntity, "ownedBy", referencedEntities, "group", "user", null);
          }
        }
      }));
    }

    return referencedEntities;
  }

  private void establishRelationsInternal(String entityRef, CatalogEntity catalogEntity, String sourceRelation,
      String targetRelation, List<CatalogEntity> referencedEntities, String firstAdditionalKindToCheck,
      String secondAdditionalKindToCheck, String thirdAdditionalKindToCheck) {
    String[] entityRefSplit = entityRef.split(":");
    String kind;
    String parentUniqueId;
    String identifier;
    Optional<CatalogEntity> optionalCatalogEntity = Optional.empty();
    if (entityRefSplit.length == 1) {
      kind = firstAdditionalKindToCheck;
      parentUniqueId = catalogEntity.getParentUniqueId();
      identifier = entityRefSplit[0];
      if (!isEmpty(firstAdditionalKindToCheck)) {
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && !isEmpty(secondAdditionalKindToCheck)) {
        kind = secondAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && !isEmpty(thirdAdditionalKindToCheck)) {
        kind = thirdAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && isPartOfOrHasPart(sourceRelation)) {
        optionalCatalogEntity = lookupByGroupingKinds(parentUniqueId, identifier, catalogEntity.getAccountIdentifier());
      }

    } else if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      parentUniqueId = catalogEntity.getParentUniqueId();
      String scopeIdentifier = entityRefSplit[1];
      int slashIndex = scopeIdentifier.indexOf("/");
      String scope = slashIndex != -1 ? scopeIdentifier.substring(0, slashIndex) : "";
      identifier = slashIndex != -1 ? scopeIdentifier.substring(slashIndex + 1) : scopeIdentifier;

      if (!isEmpty(scope)) {
        String[] scopeSplit = scope.split("\\.");
        ScopeInfo scopeInfo = null;
        if (scopeSplit.length == 3) {
          scopeInfo = getResponse(
              scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], scopeSplit[2]));
        } else if (scopeSplit.length == 2) {
          scopeInfo =
              getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], null));
        } else if (scopeSplit.length == 1) {
          scopeInfo = getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), null, null));
        }
        if (scopeInfo != null) {
          parentUniqueId = scopeInfo.getUniqueId();
        }
      }

      optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    }
    if (optionalCatalogEntity.isEmpty()) {
      return;
    }
    CatalogEntity existingCatalogEntity = optionalCatalogEntity.get();

    Map<String, Object> inlineCatalogEntitySpec =
        !isEmpty(catalogEntity.getSpec()) ? new HashMap<>(catalogEntity.getSpec()) : new HashMap<>();

    String sourceSpecKey;

    if (sourceRelation.equals("partOf") && inlineCatalogEntitySpec.containsKey(existingCatalogEntity.getKind())
        && isGroupingKind(existingCatalogEntity.getKind(), catalogEntity.getAccountIdentifier())) {
      sourceSpecKey = existingCatalogEntity.getKind();
    } else {
      sourceSpecKey = sourceRelation;
    }

    if (!REFERENCED_TYPES.contains(sourceSpecKey) && !sourceSpecKey.equals("ownedBy")
        && !sourceSpecKey.equals("hasMember") && !sourceSpecKey.equals("childOf")
        && !sourceSpecKey.equals("hasLeader")) {
      List<String> inlineCatalogEntitySpecSourceRelationList = from(inlineCatalogEntitySpec, sourceSpecKey, List.class);
      if (!isEmpty(inlineCatalogEntitySpecSourceRelationList)) {
        inlineCatalogEntitySpecSourceRelationList = new ArrayList<>(inlineCatalogEntitySpecSourceRelationList);
        inlineCatalogEntitySpecSourceRelationList.remove(entityRef);
        inlineCatalogEntitySpecSourceRelationList.add(CatalogUtils.entityRef(existingCatalogEntity));
        inlineCatalogEntitySpec.put(sourceSpecKey, inlineCatalogEntitySpecSourceRelationList);
      } else {
        inlineCatalogEntitySpecSourceRelationList = new ArrayList<>();
        inlineCatalogEntitySpecSourceRelationList.add(CatalogUtils.entityRef(existingCatalogEntity));
        inlineCatalogEntitySpec.put(sourceSpecKey, inlineCatalogEntitySpecSourceRelationList);
      }
    }

    Map<String, Set<String>> inlineCatalogEntityRelations =
        !isEmpty(catalogEntity.getRelations()) ? new HashMap<>(catalogEntity.getRelations()) : new HashMap<>();
    Set<String> inlineCatalogEntityRelationsSourceRelationList = inlineCatalogEntityRelations.get(sourceRelation);
    if (!isEmpty(inlineCatalogEntityRelationsSourceRelationList)) {
      inlineCatalogEntityRelationsSourceRelationList = new HashSet<>(inlineCatalogEntityRelationsSourceRelationList);
      inlineCatalogEntityRelationsSourceRelationList.remove(entityRef);
      inlineCatalogEntityRelationsSourceRelationList.add(CatalogUtils.entityRef(existingCatalogEntity));
      inlineCatalogEntityRelations.put(sourceRelation, inlineCatalogEntityRelationsSourceRelationList);
    } else {
      inlineCatalogEntityRelationsSourceRelationList = new HashSet<>();
      inlineCatalogEntityRelationsSourceRelationList.add(CatalogUtils.entityRef(existingCatalogEntity));
      inlineCatalogEntityRelations.put(sourceRelation, inlineCatalogEntityRelationsSourceRelationList);
    }

    catalogEntity.setSpec(inlineCatalogEntitySpec);
    catalogEntity.setRelations(inlineCatalogEntityRelations);
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(catalogEntity.getAccountIdentifier());
    catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity, groupingKinds));

    Map<String, Object> spec = existingCatalogEntity.getSpec();
    if (!isEmpty(spec)) {
      List<String> targetRelationList = from(spec, targetRelation, List.class);
      targetRelationList = isEmpty(targetRelationList) ? new ArrayList<>() : targetRelationList;
      targetRelationList.add(CatalogUtils.entityRef(catalogEntity));
      if (!REFERENCED_TYPES.contains(targetRelation)) {
        spec.put(targetRelation, targetRelationList);
        existingCatalogEntity.setSpec(spec);
      }
      Map<String, Set<String>> referencedEntityRelations = existingCatalogEntity.getRelations();
      if (!isEmpty(referencedEntityRelations)) {
        Set<String> referencedEntityRelationsTargetRelationList = referencedEntityRelations.get(targetRelation);
        referencedEntityRelationsTargetRelationList = isEmpty(referencedEntityRelationsTargetRelationList)
            ? new HashSet<>()
            : referencedEntityRelationsTargetRelationList;
        referencedEntityRelationsTargetRelationList.add(CatalogUtils.entityRef(catalogEntity));
        referencedEntityRelations.put(targetRelation, referencedEntityRelationsTargetRelationList);
      } else {
        referencedEntityRelations = new HashMap<>();
        referencedEntityRelations.put(targetRelation, Set.of(CatalogUtils.entityRef(catalogEntity)));
      }
      existingCatalogEntity.setYaml(CatalogMapper.presentationYaml(existingCatalogEntity, groupingKinds));
      existingCatalogEntity.setRelations(referencedEntityRelations);
      referencedEntities.add(existingCatalogEntity);
    } else {
      spec = new HashMap<>();
      if (!REFERENCED_TYPES.contains(targetRelation)) {
        spec.put(targetRelation, List.of(CatalogUtils.entityRef(catalogEntity)));
        existingCatalogEntity.setSpec(spec);
      }
      Map<String, Set<String>> referencedEntityRelations = existingCatalogEntity.getRelations();
      if (!isEmpty(referencedEntityRelations)) {
        Set<String> referencedEntityRelationsTargetRelationList = referencedEntityRelations.get(targetRelation);
        referencedEntityRelationsTargetRelationList = isEmpty(referencedEntityRelationsTargetRelationList)
            ? new HashSet<>()
            : referencedEntityRelationsTargetRelationList;
        referencedEntityRelationsTargetRelationList.add(CatalogUtils.entityRef(catalogEntity));
        referencedEntityRelations.put(targetRelation, referencedEntityRelationsTargetRelationList);
      } else {
        referencedEntityRelations = new HashMap<>();
        referencedEntityRelations.put(targetRelation, Set.of(CatalogUtils.entityRef(catalogEntity)));
      }
      existingCatalogEntity.setYaml(CatalogMapper.presentationYaml(existingCatalogEntity, groupingKinds));
      existingCatalogEntity.setRelations(referencedEntityRelations);
      referencedEntities.add(existingCatalogEntity);
    }
  }

  private void disbandRelationsInternal(String entityRef, CatalogEntity catalogEntity, String targetRelation,
      List<CatalogEntity> referencedEntities, String firstAdditionalKindToCheck, String secondAdditionalKindToCheck,
      String thirdAdditionalKindToCheck) {
    String[] entityRefSplit = entityRef.split(":");
    String kind;
    String parentUniqueId;
    String identifier;
    Optional<CatalogEntity> optionalCatalogEntity = Optional.empty();
    if (entityRefSplit.length == 1) {
      kind = firstAdditionalKindToCheck;
      parentUniqueId = catalogEntity.getParentUniqueId();
      identifier = entityRefSplit[0];
      optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      if (optionalCatalogEntity.isEmpty() && !isEmpty(secondAdditionalKindToCheck)) {
        kind = secondAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && !isEmpty(thirdAdditionalKindToCheck)) {
        kind = thirdAdditionalKindToCheck;
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      if (optionalCatalogEntity.isEmpty() && isPartOfOrHasPart(targetRelation)) {
        optionalCatalogEntity = lookupByGroupingKinds(parentUniqueId, identifier, catalogEntity.getAccountIdentifier());
      }
    } else if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      parentUniqueId = catalogEntity.getParentUniqueId();
      String scopeIdentifier = entityRefSplit[1];
      int slashIndex = scopeIdentifier.indexOf("/");
      String scope = slashIndex != -1 ? scopeIdentifier.substring(0, slashIndex) : "";
      identifier = slashIndex != -1 ? scopeIdentifier.substring(slashIndex + 1) : scopeIdentifier;

      if (!isEmpty(scope)) {
        String[] scopeSplit = scope.split("\\.");
        ScopeInfo scopeInfo = null;
        try {
          if (scopeSplit.length == 3) {
            scopeInfo = getResponse(
                scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], scopeSplit[2]));
          } else if (scopeSplit.length == 2) {
            scopeInfo =
                getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], null));
          } else if (scopeSplit.length == 1) {
            scopeInfo = getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), null, null));
          }
        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().contains("HTTP Error Status (404 - Resource Not Found)")) {
            log.error("Failed to retrieve scopeInfo for scope [{}], skipping relation removal for entityRef [{}]: {}",
                scope, entityRef, e);
            return;
          }
          throw new InvalidRequestException(e.getMessage());
        }
        if (scopeInfo != null) {
          parentUniqueId = scopeInfo.getUniqueId();
        }
      }

      optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
    }
    if (optionalCatalogEntity.isEmpty()) {
      return;
    }
    CatalogEntity referencedEntity = optionalCatalogEntity.get();
    Map<String, Object> spec = referencedEntity.getSpec();
    if (isEmpty(spec)) {
      return;
    }

    String targetSpeckKey;
    if (targetRelation.equals("partOf") && spec.containsKey(catalogEntity.getKind())
        && isGroupingKind(catalogEntity.getKind(), catalogEntity.getAccountIdentifier())) {
      targetSpeckKey = catalogEntity.getKind();
    } else {
      targetSpeckKey = targetRelation;
    }

    if (!(targetRelation.equals("hasPart")
            && isGroupingKind(referencedEntity.getKind(), catalogEntity.getAccountIdentifier()))) {
      if (targetRelation.equals("partOf") && spec.containsKey(SUB_COMPONENT_OF)) {
        Object subcompValue = spec.get(SUB_COMPONENT_OF);
        if (subcompValue instanceof String) {
          String catalogEntityRef = CatalogUtils.entityRef(catalogEntity);
          if (((String) subcompValue).equals(catalogEntityRef) || catalogEntityRef.endsWith("/" + subcompValue)) {
            spec.remove(SUB_COMPONENT_OF);
            referencedEntity.setSpec(spec);
          }
        }
      }
      List<String> targetRelationList = from(spec, targetSpeckKey, List.class);
      if (!isEmpty(targetRelationList)) {
        targetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(catalogEntity)));
        spec.put(targetSpeckKey, targetRelationList);
        referencedEntity.setSpec(spec);
      }
    }
    Map<String, Set<String>> referencedEntityRelations = referencedEntity.getRelations();
    if (isEmpty(referencedEntityRelations)) {
      return;
    }
    Set<String> referencedEntityRelationsTargetRelationList = referencedEntityRelations.get(targetRelation);
    if (isEmpty(referencedEntityRelationsTargetRelationList)) {
      return;
    }
    referencedEntityRelationsTargetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(catalogEntity)));
    referencedEntityRelations.put(targetRelation, referencedEntityRelationsTargetRelationList);
    referencedEntity.setRelations(referencedEntityRelations);
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(catalogEntity.getAccountIdentifier());
    referencedEntity.setYaml(CatalogMapper.presentationYaml(referencedEntity, groupingKinds));
    referencedEntities.add(referencedEntity);
  }

  private void removeRelationInternal(String entityRef, CatalogEntity catalogEntity,
      CatalogEntity existingCatalogEntity, String targetRelation, List<CatalogEntity> referencedEntities,
      String firstAdditionalKindToCheck, String secondAdditionalKindToCheck, String thirdAdditionalKindToCheck) {
    String[] entityRefSplit = entityRef.split(":");
    Optional<CatalogEntity> optionalCatalogEntity = Optional.empty();
    String kind;
    String parentUniqueId;
    String identifier;
    if (entityRefSplit.length == 1) {
      kind = firstAdditionalKindToCheck;
      parentUniqueId = catalogEntity.getParentUniqueId();
      identifier = entityRefSplit[0];
      Optional<CatalogEntity> optionalReferencedEntity =
          referencedEntities.stream()
              .filter(referencedEntity
                  -> CatalogUtils.entityRef(referencedEntity)
                         .equals(CatalogUtils.entityRef(firstAdditionalKindToCheck, catalogEntity.getOrgIdentifier(),
                             catalogEntity.getProjectIdentifier(), entityRefSplit[0])))
              .findFirst();
      if (optionalReferencedEntity.isPresent()) {
        optionalCatalogEntity = optionalReferencedEntity;
      } else {
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
      kind = secondAdditionalKindToCheck;
      if (optionalCatalogEntity.isEmpty()) {
        optionalReferencedEntity =
            referencedEntities.stream()
                .filter(referencedEntity
                    -> CatalogUtils.entityRef(referencedEntity)
                           .equals(CatalogUtils.entityRef(secondAdditionalKindToCheck, catalogEntity.getOrgIdentifier(),
                               catalogEntity.getProjectIdentifier(), entityRefSplit[0])))
                .findFirst();
        if (optionalReferencedEntity.isPresent()) {
          optionalCatalogEntity = optionalReferencedEntity;
        } else {
          optionalCatalogEntity =
              catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
        }
      }
      kind = thirdAdditionalKindToCheck;
      if (optionalCatalogEntity.isEmpty()) {
        optionalReferencedEntity =
            referencedEntities.stream()
                .filter(referencedEntity
                    -> CatalogUtils.entityRef(referencedEntity)
                           .equals(CatalogUtils.entityRef(thirdAdditionalKindToCheck, catalogEntity.getOrgIdentifier(),
                               catalogEntity.getProjectIdentifier(), entityRefSplit[0])))
                .findFirst();
        if (optionalReferencedEntity.isPresent()) {
          optionalCatalogEntity = optionalReferencedEntity;
        } else {
          optionalCatalogEntity =
              catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
        }
      }
      if (optionalCatalogEntity.isEmpty() && isPartOfOrHasPart(targetRelation)) {
        optionalCatalogEntity = lookupByGroupingKinds(parentUniqueId, identifier, catalogEntity.getAccountIdentifier());
      }

    } else if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      String kindToCheck = kind;
      parentUniqueId = catalogEntity.getParentUniqueId();
      String scopeIdentifier = entityRefSplit[1];
      int slashIndex = scopeIdentifier.indexOf("/");
      String scope = slashIndex != -1 ? scopeIdentifier.substring(0, slashIndex) : "account";
      identifier = slashIndex != -1 ? scopeIdentifier.substring(slashIndex + 1) : scopeIdentifier;

      if (!isEmpty(scope)) {
        String[] scopeSplit = scope.split("\\.");
        ScopeInfo scopeInfo = null;
        try {
          if (scopeSplit.length == 3) {
            scopeInfo = getResponse(
                scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], scopeSplit[2]));
          } else if (scopeSplit.length == 2) {
            scopeInfo =
                getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), scopeSplit[1], null));
          } else if (scopeSplit.length == 1) {
            scopeInfo = getResponse(scopeInfoClient.getScopeInfo(catalogEntity.getAccountIdentifier(), null, null));
          }
        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().contains("HTTP Error Status (404 - Resource Not Found)")) {
            log.error("Failed to retrieve scopeInfo for scope [{}], skipping relation removal for entityRef [{}]: {}",
                scope, entityRef, e);
            return;
          }
          throw new InvalidRequestException(e.getMessage());
        }
        if (scopeInfo != null) {
          parentUniqueId = scopeInfo.getUniqueId();
        }
      }

      Optional<CatalogEntity> optionalReferencedEntity =
          referencedEntities.stream()
              .filter(referencedEntity
                  -> CatalogUtils.entityRef(referencedEntity).equals(kindToCheck + ":" + scope + "/" + identifier))
              .findFirst();
      if (optionalReferencedEntity.isPresent()) {
        optionalCatalogEntity = optionalReferencedEntity;
      } else {
        optionalCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
      }
    }
    if (optionalCatalogEntity.isEmpty()) {
      return;
    }
    CatalogEntity referencedEntity = optionalCatalogEntity.get();
    Map<String, Object> spec = referencedEntity.getSpec();
    if (isEmpty(spec)) {
      return;
    }
    String targetSpeckKey;
    if (targetRelation.equals("partOf") && spec.containsKey(referencedEntity.getKind())
        && isGroupingKind(referencedEntity.getKind(), catalogEntity.getAccountIdentifier())) {
      targetSpeckKey = referencedEntity.getKind();
    } else {
      targetSpeckKey = targetRelation;
    }
    if (targetRelation.equals("partOf") && spec.containsKey(SUB_COMPONENT_OF)) {
      Object subcompValue = spec.get(SUB_COMPONENT_OF);
      if (subcompValue instanceof String) {
        String existingEntityRef = CatalogUtils.entityRef(existingCatalogEntity);
        if (((String) subcompValue).equals(existingEntityRef) || existingEntityRef.endsWith("/" + subcompValue)) {
          spec.remove(SUB_COMPONENT_OF);
          referencedEntity.setSpec(spec);
        }
      }
    }
    List<String> targetRelationList = from(spec, targetSpeckKey, List.class);
    if (!isEmpty(targetRelationList)) {
      targetRelationList = new ArrayList<>(targetRelationList);
      targetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(existingCatalogEntity)));
      spec.put(targetSpeckKey, targetRelationList);
      referencedEntity.setSpec(spec);
    }
    Map<String, Set<String>> referencedEntityRelations = referencedEntity.getRelations();
    if (isEmpty(referencedEntityRelations)) {
      return;
    }
    Set<String> referencedEntityRelationsTargetRelationList = referencedEntityRelations.get(targetRelation);
    if (isEmpty(referencedEntityRelationsTargetRelationList)) {
      return;
    }
    referencedEntityRelationsTargetRelationList.removeIf(d -> d.equals(CatalogUtils.entityRef(existingCatalogEntity)));
    referencedEntityRelations.put(targetRelation, referencedEntityRelationsTargetRelationList);
    referencedEntity.setRelations(referencedEntityRelations);
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(catalogEntity.getAccountIdentifier());
    referencedEntity.setYaml(CatalogMapper.presentationYaml(referencedEntity, groupingKinds));
    referencedEntities.add(referencedEntity);
  }

  private boolean isGroupingKind(String kind, String accountIdentifier) {
    if (isEmpty(kind)) {
      return false;
    }
    if (SYSTEM_KIND.equals(kind)) {
      return true;
    }
    Optional<io.harness.idp.catalog.entities.KindEntity> kindEntity =
        kindEntityRepository.findByAccountIdentifierAndIdentifierWithoutSchema(accountIdentifier, kind);
    return kindEntity.isPresent() && kindEntity.get().isGroupingKind();
  }

  private boolean isPartOfOrHasPart(String relation) {
    return "partOf".equalsIgnoreCase(relation) || "hasPart".equalsIgnoreCase(relation);
  }

  private Optional<CatalogEntity> lookupByGroupingKinds(
      String parentUniqueId, String identifier, String accountIdentifier) {
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(accountIdentifier);
    for (String groupingKind : groupingKinds) {
      Optional<CatalogEntity> optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, groupingKind, identifier);
      if (optionalCatalogEntity.isPresent()) {
        return optionalCatalogEntity;
      }
    }
    return Optional.empty();
  }
}
