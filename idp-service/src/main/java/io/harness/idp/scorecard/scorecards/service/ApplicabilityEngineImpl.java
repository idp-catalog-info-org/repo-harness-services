/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.IDP)
public class ApplicabilityEngineImpl implements ApplicabilityEngine {
  private static final String TEMPLATE_KIND = "template";
  private static final String WORKFLOW_KIND = "workflow";

  @Override
  public boolean isApplicable(ScorecardFilter filter, CatalogEntity entity, Map<String, Set<ScopeInfo>> scopeInfos) {
    if (filter == null || entity == null) {
      return false;
    }

    String kind = filter.getKind().toLowerCase();
    if (TEMPLATE_KIND.equals(kind)) {
      kind = WORKFLOW_KIND;
    }

    if (!kind.equalsIgnoreCase(entity.getKind())) {
      return false;
    }

    String entityType = entity.getType();
    String filterType = filter.getType();
    if (filterType != null && !filterType.equalsIgnoreCase("all") && entityType != null
        && !filterType.equalsIgnoreCase(entityType)) {
      return false;
    }

    String owner = entity.getOwner();
    if (!isEmpty(filter.getOwners())) {
      if (owner == null || filter.getOwners().stream().noneMatch(filterOwner -> filterOwner.equalsIgnoreCase(owner))) {
        return false;
      }
    }

    String lifecycle = entity.getSpec() != null ? (String) entity.getSpec().get("lifecycle") : null;
    if (!isEmpty(filter.getLifecycle()) && lifecycle != null && !filter.getLifecycle().contains(lifecycle)) {
      return false;
    }

    if (!isScopeMatching(filter, entity, scopeInfos)) {
      return false;
    }

    if (!isEmpty(filter.getTags())) {
      List<String> entityTags = entity.getTags();
      if (isEmpty(entityTags) || !new HashSet<>(entityTags).containsAll(filter.getTags())) {
        return false;
      }
    }

    return true;
  }

  @Override
  public Set<String> getApplicableScorecardIds(String accountIdentifier, CatalogEntity entity,
      List<ScorecardEntity> scorecards, Map<String, Set<ScopeInfo>> scopeInfos) {
    return scorecards.stream()
        .filter(scorecard -> isApplicable(scorecard.getFilter(), entity, scopeInfos))
        .map(ScorecardEntity::getIdentifier)
        .collect(Collectors.toSet());
  }

  private boolean isScopeMatching(
      ScorecardFilter filter, CatalogEntity entity, Map<String, Set<ScopeInfo>> scopeInfos) {
    List<ScopeInfo> allScopeInfos = scopeInfos.values().stream().flatMap(Set::stream).collect(Collectors.toList());

    List<String> filterScopes = filter.getScopes();
    if (!isEmpty(filterScopes)) {
      Set<String> filterScopeParts = filterScopes.stream()
                                         .flatMap(s -> Arrays.stream(s.split(",")))
                                         .map(String::trim)
                                         .filter(s -> !s.isEmpty())
                                         .collect(Collectors.toSet());

      allScopeInfos =
          allScopeInfos.stream().filter(si -> matchesScopePattern(si, filterScopeParts)).collect(Collectors.toList());
    }

    return allScopeInfos.stream().anyMatch(scopeInfo
        -> Objects.equals(scopeInfo.getAccountIdentifier(), entity.getAccountIdentifier())
            && Objects.equals(scopeInfo.getOrgIdentifier(), entity.getOrgIdentifier())
            && Objects.equals(scopeInfo.getProjectIdentifier(), entity.getProjectIdentifier())
            && Objects.equals(scopeInfo.getUniqueId(), entity.getParentUniqueId())
            && scopeInfo.getScopeType() == ScopeLevel.valueOf(entity.getScope()));
  }

  private boolean matchesScopePattern(ScopeInfo scopeInfo, Set<String> patterns) {
    for (String pattern : patterns) {
      if (pattern.equalsIgnoreCase("account.*")) {
        return true;
      }
      if (pattern.equalsIgnoreCase("account")) {
        if (scopeInfo.getOrgIdentifier() == null && scopeInfo.getProjectIdentifier() == null) {
          return true;
        }
      }
      if (pattern.equalsIgnoreCase("account.org")) {
        if (scopeInfo.getOrgIdentifier() != null && scopeInfo.getProjectIdentifier() == null) {
          return true;
        }
      }
      if (pattern.equalsIgnoreCase("account.org.project")) {
        if (scopeInfo.getProjectIdentifier() != null) {
          return true;
        }
      }
      String[] parts = pattern.split("\\.");
      if (parts.length == 3 && parts[2].equals("*")) {
        String orgId = parts[1];
        if (orgId.equalsIgnoreCase(scopeInfo.getOrgIdentifier())) {
          return true;
        }
      }
      if (parts.length == 2 && !parts[1].equals("*")) {
        String orgId = parts[1];
        if (orgId.equalsIgnoreCase(scopeInfo.getOrgIdentifier()) && scopeInfo.getProjectIdentifier() == null) {
          return true;
        }
      }
      if (parts.length == 3 && !parts[2].equals("*")) {
        String orgId = parts[1];
        String projId = parts[2];
        if (orgId.equalsIgnoreCase(scopeInfo.getOrgIdentifier())
            && projId.equalsIgnoreCase(scopeInfo.getProjectIdentifier())) {
          return true;
        }
      }
    }
    return false;
  }
}
