/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.entity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class EntityDiffUtils {
  public boolean isOnlyNameChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    boolean nameChanged = !isEqual(oldRule.getName(), newRule.getName());
    if (!nameChanged) {
      return false;
    }

    return isEqual(oldRule.getDescription(), newRule.getDescription())
        && isEqual(oldRule.getFieldForAgg(), newRule.getFieldForAgg())
        && isEqual(oldRule.getAggFormula(), newRule.getAggFormula())
        && isEqual(oldRule.getScopesToAggregateAt(), newRule.getScopesToAggregateAt())
        && isEqual(oldRule.getEntitySelectionCriteria(), newRule.getEntitySelectionCriteria())
        && isEqual(oldRule.getAggregationType(), newRule.getAggregationType());
  }

  public boolean isNameChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getName(), newRule.getName());
  }

  public boolean isOnlyScopesToAggregateAtChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    boolean scopesChanged = !isEqual(oldRule.getScopesToAggregateAt(), newRule.getScopesToAggregateAt());
    if (!scopesChanged) {
      return false;
    }

    return isEqual(oldRule.getName(), newRule.getName()) && isEqual(oldRule.getDescription(), newRule.getDescription())
        && isEqual(oldRule.getFieldForAgg(), newRule.getFieldForAgg())
        && isEqual(oldRule.getAggFormula(), newRule.getAggFormula())
        && isEqual(oldRule.getEntitySelectionCriteria(), newRule.getEntitySelectionCriteria())
        && isEqual(oldRule.getAggregationType(), newRule.getAggregationType());
  }

  public boolean isScopesToAggregateAtChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getScopesToAggregateAt(), newRule.getScopesToAggregateAt());
  }

  public boolean isOnlyScopeFilterChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    boolean scopeFilterChanged =
        !isEqual(oldRule.getEntitySelectionCriteria().getScopes(), newRule.getEntitySelectionCriteria().getScopes());
    if (!scopeFilterChanged) {
      return false;
    }

    return isEqual(oldRule.getName(), newRule.getName()) && isEqual(oldRule.getDescription(), newRule.getDescription())
        && isEqual(oldRule.getFieldForAgg(), newRule.getFieldForAgg())
        && isEqual(oldRule.getAggFormula(), newRule.getAggFormula());
  }

  public boolean isScopeFilterChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getEntitySelectionCriteria().getScopes(), newRule.getEntitySelectionCriteria().getScopes());
  }

  public boolean isOnlyDescriptionChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    boolean descriptionChanged = !isEqual(oldRule.getDescription(), newRule.getDescription());
    if (!descriptionChanged) {
      return false;
    }

    return isEqual(oldRule.getName(), newRule.getName()) && isEqual(oldRule.getFieldForAgg(), newRule.getFieldForAgg())
        && isEqual(oldRule.getAggFormula(), newRule.getAggFormula())
        && isEqual(oldRule.getScopesToAggregateAt(), newRule.getScopesToAggregateAt())
        && isEqual(oldRule.getEntitySelectionCriteria(), newRule.getEntitySelectionCriteria())
        && isEqual(oldRule.getAggregationType(), newRule.getAggregationType());
  }

  public boolean isFieldForAggChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getFieldForAgg(), newRule.getFieldForAgg());
  }

  public boolean isAggFormulaChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getAggFormula(), newRule.getAggFormula());
  }

  public boolean isAggregationTypeChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return false;
    }

    return !isEqual(oldRule.getAggregationType(), newRule.getAggregationType());
  }

  public boolean hasOtherFilterChanged(AggregationRuleDetails oldRule, AggregationRuleDetails newRule) {
    if (oldRule == null || newRule == null) {
      return true;
    }

    return !isEqual(oldRule.getEntitySelectionCriteria().getKind(), newRule.getEntitySelectionCriteria().getKind())
        || !isEqual(oldRule.getEntitySelectionCriteria().getType(), newRule.getEntitySelectionCriteria().getType())
        || !isEqual(oldRule.getEntitySelectionCriteria().getTags(), newRule.getEntitySelectionCriteria().getTags())
        || !isEqual(oldRule.getEntitySelectionCriteria().getOwners(), newRule.getEntitySelectionCriteria().getOwners())
        || !isEqual(
            oldRule.getEntitySelectionCriteria().getLifecycles(), newRule.getEntitySelectionCriteria().getLifecycles());
  }

  private boolean isEqual(Object obj1, Object obj2) {
    if (obj1 == null && obj2 == null) {
      return true;
    }
    if (obj1 == null || obj2 == null) {
      return false;
    }
    return obj1.equals(obj2);
  }
}
