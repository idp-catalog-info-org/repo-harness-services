/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.mapper;

import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.spec.server.idp.v1.model.AggFormula;
import io.harness.spec.server.idp.v1.model.AggregationComputeStatus;
import io.harness.spec.server.idp.v1.model.AggregationEntitySelectionCriteria;
import io.harness.spec.server.idp.v1.model.AggregationRule;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;
import io.harness.spec.server.idp.v1.model.AggregationRuleResponse;
import io.harness.spec.server.idp.v1.model.AggregationScopeLevel;
import io.harness.spec.server.idp.v1.model.AggregationType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AggregationRulesMapper {
  private AggregationRulesMapper() {}

  public static List<AggregationRuleResponse> toResponseList(List<AggregationRule> aggregationRules) {
    List<AggregationRuleResponse> response = new ArrayList<>();
    aggregationRules.forEach(
        aggregationRule -> response.add(new AggregationRuleResponse().aggregationRule(aggregationRule)));
    return response;
  }

  public static AggregationRule toSummaryDTO(AggregationRuleEntity entity) {
    return new AggregationRule()
        .identifier(entity.getIdentifier())
        .name(entity.getName())
        .description(entity.getDescription())
        .fieldForAgg(entity.getFieldForAgg())
        .aggFormula(toAggFormulaDTO(entity.getAggFormula()))
        .scopesToAggregateAt(scopesToStrings(entity.getScopesToAggregateAt()))
        .aggregationType(toAggregationTypeDTO(entity.getAggregationType()))
        .lastComputedStatus(toLastComputedStatusDTO(entity.getStatus()))
        .lastComputedAt(entity.getLastComputedAt())
        .lastErrorMessage(entity.getLastErrorMessage());
  }

  public static AggregationRuleDetails toDetailsDTO(AggregationRuleEntity entity) {
    AggregationRuleDetails dto = new AggregationRuleDetails()
                                     .identifier(entity.getIdentifier())
                                     .name(entity.getName())
                                     .description(entity.getDescription())
                                     .fieldForAgg(entity.getFieldForAgg())
                                     .aggFormula(toAggFormulaDTO(entity.getAggFormula()))
                                     .scopesToAggregateAt(scopesToStrings(entity.getScopesToAggregateAt()))
                                     .aggregationType(toAggregationTypeDTO(entity.getAggregationType()))
                                     .lastComputedStatus(toLastComputedStatusDTO(entity.getStatus()))
                                     .lastComputedAt(entity.getLastComputedAt())
                                     .lastErrorMessage(entity.getLastErrorMessage());

    if (entity.getEntitySelectionCriteria() != null) {
      dto.setEntitySelectionCriteria(toSelectionCriteriaModel(entity.getEntitySelectionCriteria()));
    }
    return dto;
  }

  public static AggregationRuleDetailsResponse toDetailsResponseDTO(AggregationRuleEntity entity) {
    return new AggregationRuleDetailsResponse().aggregationRule(toDetailsDTO(entity));
  }

  public static AggregationRuleEntity fromDTO(String accountIdentifier, AggregationRuleDetails details) {
    if (details == null) {
      return null;
    }
    AggregationRuleEntity.AggregationFormula formula = null;
    if (details.getAggFormula() != null) {
      formula = AggregationRuleEntity.AggregationFormula.valueOf(details.getAggFormula().name());
    }
    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = scopeLevelsToEntityScopes(details.getScopesToAggregateAt());
    AggregationRuleEntity.EntitySelectionCriteria criteria = null;
    if (details.getEntitySelectionCriteria() != null) {
      criteria = fromSelectionCriteriaModel(details.getEntitySelectionCriteria());
    }
    AggregationRuleEntity.AggregationType aggregationType = null;
    if (details.getAggregationType() != null) {
      aggregationType = AggregationRuleEntity.AggregationType.valueOf(details.getAggregationType().name());
    }
    return AggregationRuleEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(details.getIdentifier())
        .name(details.getName())
        .description(details.getDescription())
        .fieldForAgg(details.getFieldForAgg())
        .aggFormula(formula)
        .scopesToAggregateAt(scopesToAggregateAt)
        .aggregationType(aggregationType)
        .entitySelectionCriteria(criteria)
        .build();
  }

  private static List<AggregationScopeLevel> scopesToStrings(Set<AggregationRuleEntity.Scope> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return Collections.emptyList();
    }
    return scopes.stream()
        .filter(Objects::nonNull)
        .map(scope -> AggregationScopeLevel.fromValue(scope.name()))
        .collect(Collectors.toList());
  }

  private static AggregationEntitySelectionCriteria toSelectionCriteriaModel(
      AggregationRuleEntity.EntitySelectionCriteria selectionCriteria) {
    AggregationEntitySelectionCriteria selectionCriteriaDTO = new AggregationEntitySelectionCriteria();
    if (selectionCriteria.getScopes() != null) {
      selectionCriteriaDTO.setScopes(new ArrayList<>(selectionCriteria.getScopes()));
    }
    if (selectionCriteria.getKind() != null) {
      selectionCriteriaDTO.setKind(selectionCriteria.getKind());
    }
    if (selectionCriteria.getType() != null) {
      selectionCriteriaDTO.setType(selectionCriteria.getType());
    }
    if (selectionCriteria.getOwners() != null) {
      selectionCriteriaDTO.setOwners(new ArrayList<>(selectionCriteria.getOwners()));
    }
    if (selectionCriteria.getTags() != null) {
      selectionCriteriaDTO.setTags(new ArrayList<>(selectionCriteria.getTags()));
    }
    if (selectionCriteria.getLifecycles() != null) {
      selectionCriteriaDTO.setLifecycles(new ArrayList<>(selectionCriteria.getLifecycles()));
    }
    return selectionCriteriaDTO;
  }

  private static Set<AggregationRuleEntity.Scope> scopeLevelsToEntityScopes(List<AggregationScopeLevel> levels) {
    if (levels == null || levels.isEmpty()) {
      return Collections.emptySet();
    }
    return levels.stream()
        .filter(Objects::nonNull)
        .map(l -> AggregationRuleEntity.Scope.valueOf(l.name()))
        .collect(Collectors.toSet());
  }

  private static AggregationRuleEntity.EntitySelectionCriteria fromSelectionCriteriaModel(
      AggregationEntitySelectionCriteria aggregationEntitySelectionCriteria) {
    AggregationRuleEntity.EntitySelectionCriteria.EntitySelectionCriteriaBuilder builder =
        AggregationRuleEntity.EntitySelectionCriteria.builder();
    if (aggregationEntitySelectionCriteria.getScopes() != null) {
      builder.scopes(new ArrayList<>(aggregationEntitySelectionCriteria.getScopes()));
    }
    if (aggregationEntitySelectionCriteria.getKind() != null) {
      builder.kind(aggregationEntitySelectionCriteria.getKind());
    }
    if (aggregationEntitySelectionCriteria.getType() != null) {
      builder.type(aggregationEntitySelectionCriteria.getType());
    }
    if (aggregationEntitySelectionCriteria.getOwners() != null) {
      builder.owners(new ArrayList<>(aggregationEntitySelectionCriteria.getOwners()));
    }
    if (aggregationEntitySelectionCriteria.getTags() != null) {
      builder.tags(new ArrayList<>(aggregationEntitySelectionCriteria.getTags()));
    }
    if (aggregationEntitySelectionCriteria.getLifecycles() != null) {
      builder.lifecycles(new ArrayList<>(aggregationEntitySelectionCriteria.getLifecycles()));
    }
    return builder.build();
  }

  private static AggFormula toAggFormulaDTO(AggregationRuleEntity.AggregationFormula aggregationFormula) {
    return aggregationFormula == null ? null : AggFormula.fromValue(aggregationFormula.name());
  }

  private static AggregationComputeStatus toLastComputedStatusDTO(AggregationRuleEntity.ComputedStatus computedStatus) {
    return computedStatus == null ? null : AggregationComputeStatus.fromValue(computedStatus.name());
  }

  private static AggregationType toAggregationTypeDTO(AggregationRuleEntity.AggregationType aggregationType) {
    return aggregationType == null ? null : AggregationType.fromValue(aggregationType.name());
  }
}
