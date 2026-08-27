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
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesProcessorFactory {
  @Inject AggregationRulesHelper aggregationRulesHelper;
  @Inject ScoreRepository scoreRepository;

  public AggregationProcessor createProcessor(AggregationRuleEntity aggregationRuleEntity) {
    Set<AggregationRuleEntity.Scope> scopes = aggregationRuleEntity.getScopesToAggregateAt();
    Set<AggregationRuleEntity.Scope> hierarchicalScopes =
        scopes.stream()
            .filter(scope
                -> scope == AggregationRuleEntity.Scope.ACCOUNT || scope == AggregationRuleEntity.Scope.ORGANIZATION
                    || scope == AggregationRuleEntity.Scope.PROJECT)
            .collect(Collectors.toSet());

    boolean hasSystem = scopes.contains(AggregationRuleEntity.Scope.SYSTEM);
    boolean hasTeam = scopes.contains(AggregationRuleEntity.Scope.TEAM);

    List<AggregationProcessor> processors = new ArrayList<>();
    AggregationProcessor hierarchicalProcessor = getHierarchicalProcessor(hierarchicalScopes, aggregationRuleEntity);
    if (hierarchicalProcessor != null) {
      processors.add(hierarchicalProcessor);
    }
    if (hasSystem) {
      processors.add(new SystemAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository));
    }
    if (hasTeam) {
      processors.add(new TeamAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository));
    }

    if (processors.isEmpty()) {
      throw new IllegalArgumentException("No valid aggregation scopes provided: " + scopes);
    }
    if (processors.size() == 1) {
      return processors.get(0);
    }
    return new CompositeAggregationProcessor(processors);
  }

  private AggregationProcessor getHierarchicalProcessor(
      Set<AggregationRuleEntity.Scope> scopes, AggregationRuleEntity aggregationRuleEntity) {
    if (scopes.isEmpty()) {
      return null;
    }

    if ((scopes.containsAll(Set.of(AggregationRuleEntity.Scope.ACCOUNT, AggregationRuleEntity.Scope.ORGANIZATION,
             AggregationRuleEntity.Scope.PROJECT))
            || (scopes.size() > 1))) {
      return new FullHierarchyAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
    }

    AggregationRuleEntity.Scope singleScope = scopes.iterator().next();
    return switch (singleScope) {
      case ACCOUNT -> new AccountAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
      case ORGANIZATION -> new OrgAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
      case PROJECT -> new ProjectAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
      default -> throw new IllegalArgumentException("Invalid hierarchical scope combination: " + scopes
          + ". Use either single scopes (ACCOUNT, ORG, or PROJECT) or all three together.");
    };

  }
}
