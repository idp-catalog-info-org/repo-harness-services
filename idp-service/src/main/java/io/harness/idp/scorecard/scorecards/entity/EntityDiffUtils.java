/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.entity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.DiffUtils;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class EntityDiffUtils {
  public static boolean isScorecardUpdated(ScorecardEntity oldScorecard, ScorecardEntity updatedScorecard) {
    if (updatedScorecard == null && oldScorecard == null) {
      return false;
    }

    if (updatedScorecard == null || oldScorecard == null) {
      return true;
    }
    if (!Objects.equals(updatedScorecard.getName(), oldScorecard.getName())) {
      return true;
    }
    if (!Objects.equals(updatedScorecard.getDescription(), oldScorecard.getDescription())) {
      return true;
    }
    if (!Objects.equals(updatedScorecard.getWeightageStrategy(), oldScorecard.getWeightageStrategy())) {
      return true;
    }
    if (!Objects.equals(updatedScorecard.isPublished(), oldScorecard.isPublished())) {
      return true;
    }
    if (!Objects.equals(updatedScorecard.getTierGroupIdentifier(), oldScorecard.getTierGroupIdentifier())) {
      return true;
    }
    if (isScorecardFilterUpdated(oldScorecard.getFilter(), updatedScorecard.getFilter())) {
      return true;
    }

    Map<String, ScorecardEntity.Check> oldScorecardCheckEntityMap = oldScorecard.getChecks().stream().collect(
        Collectors.toMap(check -> check.getIdentifier() + "_" + check.isCustom(), check -> check));
    Map<String, ScorecardEntity.Check> updatedScorecardCheckEntityMap = updatedScorecard.getChecks().stream().collect(
        Collectors.toMap(check -> check.getIdentifier() + "_" + check.isCustom(), check -> check));

    boolean checksDeletedOrUpdated = oldScorecardCheckEntityMap.keySet().stream().anyMatch(checkId
        -> !(updatedScorecardCheckEntityMap.containsKey(checkId))
            || isScorecardCheckUpdated(
                oldScorecardCheckEntityMap.get(checkId), updatedScorecardCheckEntityMap.get(checkId)));

    boolean checksAdded = updatedScorecardCheckEntityMap.keySet().stream().anyMatch(
        checkId -> !(oldScorecardCheckEntityMap.containsKey(checkId)));

    return checksAdded || checksDeletedOrUpdated;
  }

  private static boolean isScorecardFilterUpdated(ScorecardFilter oldFilter, ScorecardFilter updatedFilter) {
    if (updatedFilter == null && oldFilter == null) {
      return false;
    }
    if (updatedFilter == null || oldFilter == null) {
      return true;
    }
    if (!Objects.equals(updatedFilter.getKind(), oldFilter.getKind())) {
      return true;
    }
    if (!Objects.equals(updatedFilter.getType(), oldFilter.getType())) {
      return true;
    }
    if (DiffUtils.isCollectionUpdated(oldFilter.getLifecycle(), updatedFilter.getLifecycle())) {
      return true;
    }
    if (DiffUtils.isCollectionUpdated(oldFilter.getOwners(), updatedFilter.getOwners())) {
      return true;
    }
    if (DiffUtils.isCollectionUpdated(oldFilter.getTags(), updatedFilter.getTags())) {
      return true;
    }
    return DiffUtils.isCollectionUpdated(oldFilter.getScopes(), updatedFilter.getScopes());
  }

  private static boolean isScorecardCheckUpdated(ScorecardEntity.Check oldCheck, ScorecardEntity.Check updatedCheck) {
    if (updatedCheck == null && oldCheck == null) {
      return false;
    }
    if (updatedCheck == null || oldCheck == null) {
      return true;
    }
    return !Objects.equals(updatedCheck.getWeightage(), oldCheck.getWeightage());
  }
}
