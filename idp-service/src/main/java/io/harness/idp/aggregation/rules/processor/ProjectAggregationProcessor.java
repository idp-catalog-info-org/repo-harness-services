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
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class ProjectAggregationProcessor extends HierarchyAggregationProcessor {
  public ProjectAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    super(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Override
  public List<AggregationRulesDTO> process() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    List<ScopeInfo> scopeInfos =
        aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndCatalogEntitiesPair.getLeft(), aggregationRuleEntity);
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();
    scopeInfos.forEach(this::processScopeInfo);
    extractMetricAndConstructMetricMap(catalogEntities);
    List<AggregationRulesDTO> projectDTOs = new ArrayList<>();
    orgIdToUniqueIdMap.forEach((key, value) -> projectDTOs.addAll(buildProjectDTOWithAggregations(key)));
    return projectDTOs;
  }

  private List<AggregationRulesDTO> buildProjectDTOWithAggregations(String orgId) {
    return projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
        .stream()
        .map(projectUniqueId -> {
          List<Double> projectMetrics = metricsByParentUniqueId.getOrDefault(projectUniqueId, Collections.emptyList());
          return AggregationRulesDTO.builder()
              .uniqueId(projectUniqueId)
              .aggregationValue(calculator.calculate(projectMetrics))
              .operation(AggregationRulesDTO.UpdateOperation.INGEST)
              .processedScope(AggregationRuleEntity.Scope.PROJECT)
              .build();
        })
        .toList();
  }

  @Override
  public List<AggregationRulesDTO> rename(String oldName) {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    List<ScopeInfo> scopeInfos =
        aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndCatalogEntitiesPair.getLeft(), aggregationRuleEntity);
    scopeInfos.forEach(this::processScopeInfo);
    List<AggregationRulesDTO> projectDTOs = new ArrayList<>();
    orgIdToUniqueIdMap.forEach((key, value) -> projectDTOs.addAll(buildProjectDTOForRename(key, oldName)));
    return projectDTOs;
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    List<ScopeInfo> scopeInfos =
        aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndCatalogEntitiesPair.getLeft(), aggregationRuleEntity);
    scopeInfos.forEach(this::processScopeInfo);
    List<AggregationRulesDTO> projectDTOs = new ArrayList<>();
    orgIdToUniqueIdMap.forEach((key, value) -> projectDTOs.addAll(buildProjectDTOForCleanup(key)));
    return projectDTOs;
  }

  private List<AggregationRulesDTO> buildProjectDTOForRename(String orgId, String oldName) {
    return projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
        .stream()
        .map(projectUniqueId
            -> AggregationRulesDTO.builder()
                   .uniqueId(projectUniqueId)
                   .operation(AggregationRulesDTO.UpdateOperation.RENAME)
                   .oldName(oldName)
                   .processedScope(AggregationRuleEntity.Scope.PROJECT)
                   .build())
        .toList();
  }

  private List<AggregationRulesDTO> buildProjectDTOForCleanup(String orgId) {
    return projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
        .stream()
        .map(projectUniqueId
            -> AggregationRulesDTO.builder()
                   .uniqueId(projectUniqueId)
                   .operation(AggregationRulesDTO.UpdateOperation.DELETE)
                   .processedScope(AggregationRuleEntity.Scope.PROJECT)
                   .build())
        .toList();
  }
}
