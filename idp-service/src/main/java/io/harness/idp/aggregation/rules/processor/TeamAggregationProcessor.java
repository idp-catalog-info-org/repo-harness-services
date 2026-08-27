/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;

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
import io.harness.idp.catalog.graph.utils.EntityRefResolver;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class TeamAggregationProcessor extends BaseAggregationProcessor {
  List<ScopeInfo> scopeInfos;
  Set<String> teamRefs;
  Map<String, List<Double>> metricsByTeamRef;
  Map<String, Set<String>> teamToParentTeamsMap;
  Map<String, CatalogEntity> teamByLookupKey;
  Function<String, String> namespaceResolver;

  public TeamAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    super(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
    scopeInfos = new ArrayList<>();
    teamRefs = new HashSet<>();
    metricsByTeamRef = new HashMap<>();
    teamToParentTeamsMap = new HashMap<>();
    teamByLookupKey = new HashMap<>();
    namespaceResolver = namespace -> null;
  }

  @Override
  public List<AggregationRulesDTO> process() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();

    buildParentMapping();

    Map<String, Double> entityToMetricsMap = extractMetrics(catalogEntities);
    catalogEntities.forEach(entity -> {
      Set<String> allTeamRefs = extractAllTeamRefsWithHierarchy(entity);
      teamRefs.addAll(allTeamRefs);
      String entityIdentifier = CatalogUtils.getEntityUUId(entity);
      Double metric = entityToMetricsMap.get(entityIdentifier);
      if (metric != null) {
        allTeamRefs.forEach(teamRef -> metricsByTeamRef.computeIfAbsent(teamRef, k -> new ArrayList<>()).add(metric));
      }
    });
    List<AggregationRulesDTO> teamDTOs = new ArrayList<>();
    metricsByTeamRef.forEach((teamRef, metrics) -> teamDTOs.add(buildTeamDTOWithAggregations(teamRef, metrics)));
    return teamDTOs;
  }

  @Override
  public void save(List<AggregationRulesDTO> aggregationRulesDTOs) {
    aggregationRulesDTOs =
        aggregationRulesDTOs.stream()
            .filter(aggregationRulesDTO
                -> aggregationRulesDTO != null
                    && AggregationRuleEntity.Scope.TEAM.equals(aggregationRulesDTO.getProcessedScope()))
            .collect(Collectors.toList());
    Set<CatalogEntity> existingCatalogEntities = aggregationRulesHelper.getCatalogEntitiesByRef(
        aggregationRuleEntity.getAccountIdentifier(), scopeInfos, teamRefs);
    Map<String, CatalogEntity> catalogsByEntityRef = existingCatalogEntities.stream().collect(
        Collectors.toMap(CatalogUtils::entityRef, catalogEntity -> catalogEntity, (existing, duplicate) -> existing));
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
    Set<String> allTeamsIncludingParents = collectAllTeamsIncludingParents();

    List<AggregationRulesDTO> teamDTOs = new ArrayList<>();
    allTeamsIncludingParents.forEach(eRef -> teamDTOs.add(buildTeamDTOWithAggregationsForRename(eRef, oldName)));

    return teamDTOs;
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    Set<String> allTeamsIncludingParents = collectAllTeamsIncludingParents();

    List<AggregationRulesDTO> teamDTOs = new ArrayList<>();
    allTeamsIncludingParents.forEach(eRef -> teamDTOs.add(buildTeamDTOWithAggregationsForCleanup(eRef)));

    return teamDTOs;
  }

  private Set<String> collectAllTeamsIncludingParents() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();

    buildParentMapping();

    Set<String> allTeamsIncludingParents = new HashSet<>();
    catalogEntities.forEach(entity -> {
      Set<String> teamEntityRefs = extractAllTeamRefsWithHierarchy(entity);
      allTeamsIncludingParents.addAll(teamEntityRefs);
    });

    return allTeamsIncludingParents;
  }

  private void buildParentMapping() {
    scopeInfos = aggregationRulesHelper.getAllAccountScopeInfos(aggregationRuleEntity.getAccountIdentifier());
    namespaceResolver =
        aggregationRulesHelper.getAccountNamespaceResolver(aggregationRuleEntity.getAccountIdentifier());

    List<CatalogEntity> teamEntities =
        aggregationRulesHelper.getAllTeamEntities(aggregationRuleEntity.getAccountIdentifier(), scopeInfos);
    teamEntities.forEach(teamEntity -> teamByLookupKey.put(lookupKey(teamEntity), teamEntity));

    teamEntities.forEach(teamEntity -> {
      String canonicalRef = CatalogUtils.entityRef(teamEntity);
      Set<String> childOfRefs = teamEntity.getRelationsFor(CHILD_OF);
      if (isEmpty(childOfRefs)) {
        return;
      }
      Set<String> parentRefs = childOfRefs.stream()
                                   .map(this::resolveRelationRefToTeamRef)
                                   .filter(ref -> !isEmpty(ref))
                                   .collect(Collectors.toSet());
      if (!isEmpty(parentRefs)) {
        teamToParentTeamsMap.put(canonicalRef, parentRefs);
      }
    });
  }

  private String lookupKey(CatalogEntity teamEntity) {
    return teamEntity.getParentUniqueId() + "|" + teamEntity.getKind().toLowerCase() + "|" + teamEntity.getIdentifier();
  }

  private String resolveRelationRefToTeamRef(String relationRef) {
    if (isEmpty(relationRef)) {
      return null;
    }
    Optional<EntityRefResolver.ScopedEntityLookup> lookupOpt =
        EntityRefResolver.parseRelationRefToLookup(relationRef, namespaceResolver);
    if (lookupOpt.isEmpty()) {
      return null;
    }
    EntityRefResolver.ScopedEntityLookup lookup = lookupOpt.get();
    CatalogEntity team =
        teamByLookupKey.get(lookup.parentUniqueId + "|" + lookup.kind.toLowerCase() + "|" + lookup.identifier);
    return team != null ? CatalogUtils.entityRef(team) : null;
  }

  private Set<String> extractAllTeamRefsWithHierarchy(CatalogEntity entity) {
    Set<String> allTeams = new HashSet<>();
    Set<String> directTeams = extractOwningTeamRefs(entity);
    String entityRef = CatalogUtils.entityRef(entity);

    directTeams.forEach(teamRef -> {
      allTeams.add(teamRef);
      Set<String> visited = new HashSet<>();
      visited.add(entityRef);
      collectAllParentTeams(teamRef, allTeams, visited);
    });

    return allTeams;
  }

  private AggregationRulesDTO buildTeamDTOWithAggregations(String teamRef, List<Double> teamMetrics) {
    return AggregationRulesDTO.builder()
        .uniqueId(teamRef)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .aggregationValue(calculator.calculate(teamMetrics))
        .processedScope(AggregationRuleEntity.Scope.TEAM)
        .build();
  }

  private Set<String> extractOwningTeamRefs(CatalogEntity entity) {
    Set<String> teams = new HashSet<>();
    try {
      Set<String> ownedByRefs = entity.getRelationsFor(OWNED_BY);
      if (isEmpty(ownedByRefs)) {
        return teams;
      }
      for (String ownedByRef : ownedByRefs) {
        String resolved = resolveRelationRefToTeamRef(ownedByRef);
        if (!isEmpty(resolved)) {
          teams.add(resolved);
        }
      }
    } catch (Exception e) {
      log.error("Error extracting owning teams from entity {}: {}", entity.getIdentifier(), e.getMessage(), e);
    }
    return teams;
  }

  private void collectAllParentTeams(String teamRef, Set<String> allTeams, Set<String> visitedInThisPath) {
    Set<String> parentRefs = teamToParentTeamsMap.getOrDefault(teamRef, Collections.emptySet());

    if (isEmpty(parentRefs)) {
      return;
    }

    for (String parentRef : parentRefs) {
      if (visitedInThisPath.contains(parentRef)) {
        log.warn("Cycle detected: {} -> {} creates a cycle in the team hierarchy.", teamRef, parentRef);
        continue;
      }
      if (allTeams.add(parentRef)) {
        Set<String> newVisited = new HashSet<>(visitedInThisPath);
        newVisited.add(parentRef);
        collectAllParentTeams(parentRef, allTeams, newVisited);
      }
    }
  }

  private AggregationRulesDTO buildTeamDTOWithAggregationsForRename(String parentUniqueId, String oldName) {
    return AggregationRulesDTO.builder()
        .uniqueId(parentUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.RENAME)
        .oldName(oldName)
        .processedScope(AggregationRuleEntity.Scope.TEAM)
        .build();
  }

  private AggregationRulesDTO buildTeamDTOWithAggregationsForCleanup(String parentUniqueId) {
    return AggregationRulesDTO.builder()
        .uniqueId(parentUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.DELETE)
        .processedScope(AggregationRuleEntity.Scope.TEAM)
        .build();
  }
}
