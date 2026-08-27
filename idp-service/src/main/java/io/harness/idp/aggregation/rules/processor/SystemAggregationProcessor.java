/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class SystemAggregationProcessor extends BaseAggregationProcessor {
  List<ScopeInfo> scopeInfos;
  Set<String> entityRefs;
  Map<String, List<Double>> metricsByEntityRef;
  Map<String, Set<String>> systemToParentSystemsMap;

  public SystemAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    super(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
    scopeInfos = new ArrayList<>();
    entityRefs = new HashSet<>();
    metricsByEntityRef = new HashMap<>();
    systemToParentSystemsMap = new HashMap<>();
  }

  @Override
  public List<AggregationRulesDTO> process() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    scopeInfos = scopeInfosAndCatalogEntitiesPair.getLeft();
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();

    buildParentMapping();

    Map<String, Double> entityToMetricsMap = extractMetrics(catalogEntities);
    catalogEntities.forEach(entity -> {
      Set<String> allSystemEntityRefs = extractAllSystemEntityRefsWithHierarchy(entity);
      entityRefs.addAll(allSystemEntityRefs);
      String entityIdentifier = CatalogUtils.getEntityUUId(entity);
      Double metric = entityToMetricsMap.get(entityIdentifier);
      if (metric != null) {
        allSystemEntityRefs.forEach(
            systemRef -> metricsByEntityRef.computeIfAbsent(systemRef, k -> new ArrayList<>()).add(metric));
      }
    });
    List<AggregationRulesDTO> systemDTOs = new ArrayList<>();
    metricsByEntityRef.forEach(
        (systemRef, metrics) -> systemDTOs.add(buildSystemDTOWithAggregations(systemRef, metrics)));
    return systemDTOs;
  }

  @Override
  public void save(List<AggregationRulesDTO> aggregationRulesDTOs) {
    aggregationRulesDTOs =
        aggregationRulesDTOs.stream()
            .filter(aggregationRulesDTO
                -> aggregationRulesDTO != null
                    && AggregationRuleEntity.Scope.SYSTEM.equals(aggregationRulesDTO.getProcessedScope()))
            .collect(Collectors.toList());
    Set<CatalogEntity> existingCatalogEntities = aggregationRulesHelper.getCatalogEntitiesByRef(
        aggregationRuleEntity.getAccountIdentifier(), scopeInfos, entityRefs);
    Map<String, CatalogEntity> catalogsByEntityRef = existingCatalogEntities.stream().collect(
        Collectors.toMap(CatalogUtils::entityRef, catalogEntity -> catalogEntity));
    Set<CatalogEntity> modifiedCatalogEntities = new HashSet<>();
    aggregationRulesDTOs.forEach(aggregationRulesDTO -> {
      if (!aggregationRulesDTO.getOperation().equals(AggregationRulesDTO.UpdateOperation.INGEST)
          || aggregationRulesDTO.getAggregationValue() != null) {
        CatalogEntity catalogEntity = catalogsByEntityRef.get(aggregationRulesDTO.getUniqueId());
        if (catalogEntity != null) {
          modifiedCatalogEntities.add(modifyCatalogEntity(catalogEntity, aggregationRulesDTO));
        }
      }
    });
    aggregationRulesHelper.saveAndAuditChanges(modifiedCatalogEntities, existingCatalogEntities);
  }

  @Override
  public List<AggregationRulesDTO> rename(String oldName) {
    Set<String> allSystemsIncludingParents = collectAllSystemsIncludingParents();

    List<AggregationRulesDTO> systemDTOs = new ArrayList<>();
    allSystemsIncludingParents.forEach(eRef -> systemDTOs.add(buildSystemDTOWithAggregationsForRename(eRef, oldName)));

    return systemDTOs;
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    Set<String> allSystemsIncludingParents = collectAllSystemsIncludingParents();

    List<AggregationRulesDTO> systemDTOs = new ArrayList<>();
    allSystemsIncludingParents.forEach(eRef -> systemDTOs.add(buildSystemDTOWithAggregationsForCleanup(eRef)));

    return systemDTOs;
  }

  private Set<String> collectAllSystemsIncludingParents() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    scopeInfos = scopeInfosAndCatalogEntitiesPair.getLeft();
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();

    buildParentMapping();

    Set<String> allSystemsIncludingParents = new HashSet<>();
    catalogEntities.forEach(entity -> {
      Set<String> systemEntityRefs = extractAllSystemEntityRefsWithHierarchy(entity);
      allSystemsIncludingParents.addAll(systemEntityRefs);
    });

    return allSystemsIncludingParents;
  }

  private AggregationRulesDTO buildSystemDTOWithAggregations(String systemRef, List<Double> systemMetrics) {
    return AggregationRulesDTO.builder()
        .uniqueId(systemRef)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .aggregationValue(calculator.calculate(systemMetrics))
        .processedScope(AggregationRuleEntity.Scope.SYSTEM)
        .build();
  }

  private Set<String> extractAllSystemEntityRefsWithHierarchy(CatalogEntity entity) {
    Set<String> allSystems = new HashSet<>();
    Set<String> directSystems = extractDirectSystemEntityRefs(entity);
    String entityRef = CatalogUtils.entityRef(entity);

    directSystems.forEach(systemRef -> {
      allSystems.add(systemRef);
      Set<String> visited = new HashSet<>();
      visited.add(entityRef);
      collectAllParentSystems(systemRef, allSystems, visited);
    });

    return allSystems;
  }

  private Set<String> extractDirectSystemEntityRefs(CatalogEntity entity) {
    Set<String> systems = new HashSet<>();
    try {
      if (entity.getSpec() != null && entity.getSpec().get("system") != null) {
        Object systemObj = entity.getSpec().get("system");
        if (systemObj instanceof List) {
          @SuppressWarnings("unchecked") List<Object> systemList = (List<Object>) systemObj;
          systems = systemList.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(s -> !s.trim().isEmpty())
                        .collect(Collectors.toSet());
        } else if (systemObj instanceof String systemStr) {
          if (!systemStr.trim().isEmpty()) {
            systems.add(systemStr);
          }
        }
      }
    } catch (Exception e) {
      log.error("Error extracting systems from entity {}: {}", entity.getIdentifier(), e.getMessage());
    }
    return systems;
  }

  private void collectAllParentSystems(String systemRef, Set<String> allSystems, Set<String> visitedInThisPath) {
    Set<String> parentRefs = systemToParentSystemsMap.getOrDefault(systemRef, Collections.emptySet());

    if (parentRefs.isEmpty()) {
      return;
    }

    for (String parentRef : parentRefs) {
      if (visitedInThisPath.contains(parentRef)) {
        log.warn("Cycle detected: {} -> {} creates a cycle in the system hierarchy.", systemRef, parentRef);
        continue;
      }
      if (allSystems.add(parentRef)) {
        Set<String> newVisited = new HashSet<>(visitedInThisPath);
        newVisited.add(parentRef);
        collectAllParentSystems(parentRef, allSystems, newVisited);
      }
    }
  }

  private AggregationRulesDTO buildSystemDTOWithAggregationsForRename(String parentUniqueId, String oldName) {
    return AggregationRulesDTO.builder()
        .uniqueId(parentUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.RENAME)
        .oldName(oldName)
        .processedScope(AggregationRuleEntity.Scope.SYSTEM)
        .build();
  }

  private AggregationRulesDTO buildSystemDTOWithAggregationsForCleanup(String parentUniqueId) {
    return AggregationRulesDTO.builder()
        .uniqueId(parentUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.DELETE)
        .processedScope(AggregationRuleEntity.Scope.SYSTEM)
        .build();
  }

  private void buildParentMapping() {
    List<CatalogEntity> systemEntities =
        aggregationRulesHelper.getAllSystemEntities(aggregationRuleEntity.getAccountIdentifier(), scopeInfos);

    systemEntities.forEach(systemEntity -> {
      String systemRef = CatalogUtils.entityRef(systemEntity);
      Set<String> parentRefs = extractDirectSystemEntityRefs(systemEntity);

      if (!parentRefs.isEmpty()) {
        systemToParentSystemsMap.put(systemRef, parentRefs);
      }
    });
  }
}
