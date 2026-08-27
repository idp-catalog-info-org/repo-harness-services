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

import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class AccountAggregationProcessor extends HierarchyAggregationProcessor {
  public AccountAggregationProcessor(AggregationRulesHelper aggregationRulesHelper,
      AggregationRuleEntity aggregationRuleEntity, ScoreRepository scoreRepository) {
    super(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Override
  public List<AggregationRulesDTO> process() {
    String accountIdentifier = aggregationRuleEntity.getAccountIdentifier();
    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair =
        aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity);
    Set<CatalogEntity> catalogEntities = scopeInfosAndCatalogEntitiesPair.getRight();
    extractMetricAndConstructMetricMap(catalogEntities);
    List<Double> accountMetrics = metricsByParentUniqueId.values().stream().flatMap(List::stream).toList();
    Double accountAggregation = calculator.calculate(accountMetrics);
    return List.of(AggregationRulesDTO.builder()
                       .uniqueId(accountIdentifier)
                       .aggregationValue(accountAggregation)
                       .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                       .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                       .build());
  }

  @Override
  public List<AggregationRulesDTO> rename(String oldName) {
    String accountIdentifier = aggregationRuleEntity.getAccountIdentifier();
    return List.of(AggregationRulesDTO.builder()
                       .uniqueId(accountIdentifier)
                       .operation(AggregationRulesDTO.UpdateOperation.RENAME)
                       .oldName(oldName)
                       .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                       .build());
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    String accountIdentifier = aggregationRuleEntity.getAccountIdentifier();
    return List.of(AggregationRulesDTO.builder()
                       .uniqueId(accountIdentifier)
                       .operation(AggregationRulesDTO.UpdateOperation.DELETE)
                       .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                       .build());
  }
}
