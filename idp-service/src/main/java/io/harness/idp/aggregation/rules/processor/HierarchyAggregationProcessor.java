/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract class HierarchyAggregationProcessor extends BaseAggregationProcessor {
  protected final Map<String, String> orgIdToUniqueIdMap;
  protected final Map<String, Set<String>> projectsByOrgId;
  protected final Map<String, List<Double>> metricsByParentUniqueId;

  public HierarchyAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    super(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
    orgIdToUniqueIdMap = new HashMap<>();
    projectsByOrgId = new HashMap<>();
    metricsByParentUniqueId = new HashMap<>();
  }

  public void processScopeInfo(ScopeInfo scopeInfo) {
    if (ScopeLevel.ACCOUNT.equals(scopeInfo.getScopeType()))
      return;

    String orgId = scopeInfo.getOrgIdentifier();
    if (ScopeLevel.ORGANIZATION.equals(scopeInfo.getScopeType())) {
      orgIdToUniqueIdMap.put(orgId, scopeInfo.getUniqueId());
    } else {
      projectsByOrgId.computeIfAbsent(orgId, k -> new HashSet<>()).add(scopeInfo.getUniqueId());
    }
  }

  public void extractMetricAndConstructMetricMap(Set<CatalogEntity> entities) {
    Map<String, Double> entityToMetricsMap = extractMetrics(entities);
    for (CatalogEntity entity : entities) {
      String entityIdentifier = CatalogUtils.getEntityUUId(entity);
      Double metric = entityToMetricsMap.get(entityIdentifier);
      if (metric != null) {
        metricsByParentUniqueId.computeIfAbsent(entity.getParentUniqueId(), k -> new ArrayList<>()).add(metric);
      }
    }
  }

  @Override
  public void save(List<AggregationRulesDTO> aggregationRulesDTOs) {
    aggregationRulesDTOs =
        aggregationRulesDTOs.stream()
            .filter(aggregationRulesDTO
                -> aggregationRulesDTO != null
                    && !AggregationRuleEntity.Scope.SYSTEM.equals(aggregationRulesDTO.getProcessedScope())
                    && !AggregationRuleEntity.Scope.TEAM.equals(aggregationRulesDTO.getProcessedScope()))
            .collect(Collectors.toList());
    List<String> uniqueIds = new ArrayList<>();
    aggregationRulesDTOs.forEach(aggregationRulesDTO -> findUniqueIds(aggregationRulesDTO, uniqueIds));
    List<CatalogEntity> existingCatalogEntities = aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(uniqueIds);
    log.info("Found {} hierarchical entities for the aggregation rules to be ingested", existingCatalogEntities.size());
    Map<String, CatalogEntity> catalogsByUniqueId = existingCatalogEntities.stream().collect(Collectors.toMap(
        CatalogEntity::getParentUniqueId, catalogEntity -> catalogEntity, (existing, replacement) -> existing));
    Set<CatalogEntity> modifiedCatalogEntities = new HashSet<>();
    aggregationRulesDTOs.forEach(
        aggregationRulesDTO -> modifyCatalogEntities(aggregationRulesDTO, catalogsByUniqueId, modifiedCatalogEntities));
    aggregationRulesHelper.saveAndAuditChanges(modifiedCatalogEntities, new HashSet<>(existingCatalogEntities));
  }

  private void findUniqueIds(AggregationRulesDTO aggregationRulesDTO, List<String> uniqueIds) {
    if (aggregationRuleEntity.getScopesToAggregateAt().contains(aggregationRulesDTO.getProcessedScope())) {
      uniqueIds.add(aggregationRulesDTO.getUniqueId());
    }
    if (!isEmpty(aggregationRulesDTO.getChildren())) {
      for (AggregationRulesDTO aggregationRulesDTOChild : aggregationRulesDTO.getChildren()) {
        findUniqueIds(aggregationRulesDTOChild, uniqueIds);
      }
    }
  }

  private void modifyCatalogEntities(AggregationRulesDTO aggregationRulesDTO,
      Map<String, CatalogEntity> catalogsByUniqueId, Set<CatalogEntity> modifiedCatalogEntities) {
    if (aggregationRuleEntity.getScopesToAggregateAt().contains(aggregationRulesDTO.getProcessedScope())
        && (!aggregationRulesDTO.getOperation().equals(AggregationRulesDTO.UpdateOperation.INGEST)
            || aggregationRulesDTO.getAggregationValue() != null)) {
      CatalogEntity catalogEntity = catalogsByUniqueId.get(aggregationRulesDTO.getUniqueId());
      if (catalogEntity != null) {
        modifiedCatalogEntities.add(modifyCatalogEntity(catalogEntity, aggregationRulesDTO));
      }
    }
    if (!isEmpty(aggregationRulesDTO.getChildren())) {
      for (AggregationRulesDTO aggregationRulesDTOChild : aggregationRulesDTO.getChildren()) {
        modifyCatalogEntities(aggregationRulesDTOChild, catalogsByUniqueId, modifiedCatalogEntities);
      }
    }
  }
}
