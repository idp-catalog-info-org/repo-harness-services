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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class FullHierarchyAggregationProcessor extends HierarchyAggregationProcessor {
  public FullHierarchyAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
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
    return List.of(buildResultWithAggregations());
  }

  @Override
  public List<AggregationRulesDTO> rename(String oldName) {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    List<ScopeInfo> scopeInfos =
        aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndCatalogEntitiesPair.getLeft(), aggregationRuleEntity);
    scopeInfos.forEach(this::processScopeInfo);
    return List.of(buildRenameDTOWithAggregations(oldName));
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    List<ScopeInfo> scopeInfos =
        aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndCatalogEntitiesPair.getLeft(), aggregationRuleEntity);
    scopeInfos.forEach(this::processScopeInfo);
    return List.of(buildCleanupDTOWithAggregations());
  }

  public AggregationRulesDTO buildResultWithAggregations() {
    List<Double> accountMetrics = getAllAccountLevelMetrics();
    Double accountAggregation = calculator.calculate(accountMetrics);

    List<AggregationRulesDTO> orgDTOs =
        orgIdToUniqueIdMap.entrySet().stream().map(this::buildOrgDTOWithAggregations).collect(Collectors.toList());

    return AggregationRulesDTO.builder()
        .uniqueId(aggregationRuleEntity.getAccountIdentifier())
        .aggregationValue(accountAggregation)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
        .children(orgDTOs)
        .build();
  }

  private List<Double> getAllAccountLevelMetrics() {
    List<Double> allMetrics = new ArrayList<>(
        metricsByParentUniqueId.getOrDefault(aggregationRuleEntity.getAccountIdentifier(), Collections.emptyList()));
    orgIdToUniqueIdMap.values().forEach(orgUniqueId -> {
      allMetrics.addAll(metricsByParentUniqueId.getOrDefault(orgUniqueId, Collections.emptyList()));
    });
    projectsByOrgId.values()
        .stream()
        .flatMap(Set::stream)
        .forEach(projectUniqueId
            -> allMetrics.addAll(metricsByParentUniqueId.getOrDefault(projectUniqueId, Collections.emptyList())));
    return allMetrics;
  }

  private AggregationRulesDTO buildOrgDTOWithAggregations(Map.Entry<String, String> orgEntry) {
    String orgId = orgEntry.getKey();
    String orgUniqueId = orgEntry.getValue();

    List<Double> orgLevelMetrics =
        new ArrayList<>(metricsByParentUniqueId.getOrDefault(orgUniqueId, Collections.emptyList()));
    List<AggregationRulesDTO> projectDTOs = projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
                                                .stream()
                                                .map(projectUniqueId -> {
                                                  List<Double> projectMetrics = metricsByParentUniqueId.getOrDefault(
                                                      projectUniqueId, Collections.emptyList());
                                                  orgLevelMetrics.addAll(projectMetrics);

                                                  return AggregationRulesDTO.builder()
                                                      .uniqueId(projectUniqueId)
                                                      .aggregationValue(calculator.calculate(projectMetrics))
                                                      .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                                                      .processedScope(AggregationRuleEntity.Scope.PROJECT)
                                                      .build();
                                                })
                                                .collect(Collectors.toList());

    Double orgAggregation = calculator.calculate(orgLevelMetrics);

    return AggregationRulesDTO.builder()
        .uniqueId(orgUniqueId)
        .aggregationValue(orgAggregation)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
        .children(projectDTOs)
        .build();
  }

  public AggregationRulesDTO buildRenameDTOWithAggregations(String oldName) {
    List<AggregationRulesDTO> orgDTOs = new ArrayList<>();
    for (Map.Entry<String, String> entry : orgIdToUniqueIdMap.entrySet()) {
      orgDTOs.add(buildOrgDTOWithAggregationsForRename(entry, oldName));
    }
    return AggregationRulesDTO.builder()
        .uniqueId(aggregationRuleEntity.getAccountIdentifier())
        .operation(AggregationRulesDTO.UpdateOperation.RENAME)
        .oldName(oldName)
        .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
        .children(orgDTOs)
        .build();
  }

  public AggregationRulesDTO buildCleanupDTOWithAggregations() {
    List<AggregationRulesDTO> orgDTOs = orgIdToUniqueIdMap.entrySet()
                                            .stream()
                                            .map(this::buildOrgDTOWithAggregationsForCleanup)
                                            .collect(Collectors.toList());
    return AggregationRulesDTO.builder()
        .uniqueId(aggregationRuleEntity.getAccountIdentifier())
        .operation(AggregationRulesDTO.UpdateOperation.DELETE)
        .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
        .children(orgDTOs)
        .build();
  }

  private AggregationRulesDTO buildOrgDTOWithAggregationsForRename(Map.Entry<String, String> orgEntry, String oldName) {
    String orgId = orgEntry.getKey();
    String orgUniqueId = orgEntry.getValue();
    List<AggregationRulesDTO> projectDTOs = projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
                                                .stream()
                                                .map(projectUniqueId
                                                    -> AggregationRulesDTO.builder()
                                                           .uniqueId(projectUniqueId)
                                                           .operation(AggregationRulesDTO.UpdateOperation.RENAME)
                                                           .oldName(oldName)
                                                           .processedScope(AggregationRuleEntity.Scope.PROJECT)
                                                           .build())
                                                .collect(Collectors.toList());
    return AggregationRulesDTO.builder()
        .uniqueId(orgUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.RENAME)
        .oldName(oldName)
        .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
        .children(projectDTOs)
        .build();
  }

  private AggregationRulesDTO buildOrgDTOWithAggregationsForCleanup(Map.Entry<String, String> orgEntry) {
    String orgId = orgEntry.getKey();
    String orgUniqueId = orgEntry.getValue();
    List<AggregationRulesDTO> projectDTOs = projectsByOrgId.getOrDefault(orgId, Collections.emptySet())
                                                .stream()
                                                .map(projectUniqueId -> {
                                                  return AggregationRulesDTO.builder()
                                                      .uniqueId(projectUniqueId)
                                                      .operation(AggregationRulesDTO.UpdateOperation.DELETE)
                                                      .processedScope(AggregationRuleEntity.Scope.PROJECT)
                                                      .build();
                                                })
                                                .collect(Collectors.toList());
    return AggregationRulesDTO.builder()
        .uniqueId(orgUniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.DELETE)
        .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
        .children(projectDTOs)
        .build();
  }
}
