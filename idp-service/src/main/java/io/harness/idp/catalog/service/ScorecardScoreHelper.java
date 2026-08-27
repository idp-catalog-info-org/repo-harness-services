/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifierEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScorecardScoreHelper {
  private final ScoreRepository scoreRepository;

  @Inject
  public ScorecardScoreHelper(ScoreRepository scoreRepository) {
    this.scoreRepository = scoreRepository;
  }

  public Map<String, List<ScoreEntity>> fetchScoresForEntities(String harnessAccount,
      List<CatalogEntity> catalogEntities, List<ScorecardAndChecks> scorecardAndChecks, ScopeTopology topology) {
    if (isEmpty(catalogEntities) || isEmpty(scorecardAndChecks)) {
      return Collections.emptyMap();
    }

    try {
      Set<String> pageKinds = catalogEntities.stream().map(e -> e.getKind().toLowerCase()).collect(Collectors.toSet());

      List<CompiledScorecardFilter> compiledFilters = scorecardAndChecks.stream()
                                                          .filter(sc -> matchesPageKind(sc.getScorecard(), pageKinds))
                                                          .map(sc -> compile(sc.getScorecard(), topology))
                                                          .filter(Objects::nonNull)
                                                          .toList();

      if (compiledFilters.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<String, Set<String>> entityToApplicableScorecards = new HashMap<>();
      for (CatalogEntity entity : catalogEntities) {
        Set<String> applicableScorecardIds = new HashSet<>();
        for (CompiledScorecardFilter filter : compiledFilters) {
          if (matches(filter, entity)) {
            applicableScorecardIds.add(filter.scorecardIdentifier);
          }
        }
        if (!applicableScorecardIds.isEmpty()) {
          entityToApplicableScorecards.put(CatalogUtils.entityRef(entity), applicableScorecardIds);
        }
      }

      if (entityToApplicableScorecards.isEmpty()) {
        return Collections.emptyMap();
      }

      List<String> entityIdentifiers =
          catalogEntities.stream().map(CatalogUtils::entityRefV1).collect(Collectors.toList());

      Map<String, Map<String, ScoreEntity>> dbScores =
          scoreRepository.getAllLatestScoresByScorecardsForEntities(harnessAccount, entityIdentifiers, true)
              .getMappedResults()
              .stream()
              .collect(Collectors.groupingBy(ScoreEntityByScorecardIdentifierEntityIdentifier::getEntityIdentifier,
                  Collectors.toMap(ScoreEntityByScorecardIdentifierEntityIdentifier::getScorecardIdentifier,
                      ScoreEntityByScorecardIdentifierEntityIdentifier::getScoreEntity, (a, b) -> a)));

      Map<String, List<ScoreEntity>> result = new HashMap<>();
      for (CatalogEntity entity : catalogEntities) {
        String entityRef = CatalogUtils.entityRef(entity);
        Set<String> applicableScorecardIds = entityToApplicableScorecards.get(entityRef);
        if (applicableScorecardIds == null) {
          continue;
        }

        String entityDbKey = CatalogUtils.getEntityUUId(entity);
        Map<String, ScoreEntity> scorecardScores = dbScores.getOrDefault(entityDbKey, Collections.emptyMap());

        List<ScoreEntity> scoreList = applicableScorecardIds.stream()
                                          .map(scorecardScores::get)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());

        if (!scoreList.isEmpty()) {
          result.put(entityRef, scoreList);
        }
      }

      return result;
    } catch (Exception e) {
      log.error("Error fetching scores for entities: {}", e.getMessage(), e);
      return Collections.emptyMap();
    }
  }

  private boolean matchesPageKind(ScorecardEntity scorecard, Set<String> pageKinds) {
    if (scorecard.getFilter() == null) {
      return false;
    }
    String scorecardKind = scorecard.getFilter().getKind().toLowerCase();
    if (TEMPLATE_KIND.equals(scorecardKind)) {
      scorecardKind = WORKFLOW_KIND;
    }
    return pageKinds.contains(scorecardKind);
  }

  private CompiledScorecardFilter compile(ScorecardEntity scorecard, ScopeTopology topology) {
    ScorecardFilter f = scorecard.getFilter();
    if (f == null) {
      return null;
    }

    String kind = f.getKind().toLowerCase();
    if (TEMPLATE_KIND.equals(kind)) {
      kind = WORKFLOW_KIND;
    }

    List<String> scopesList = isEmpty(f.getScopes()) ? List.of("account.*") : f.getScopes();
    String scopeString = String.join(",", scopesList);
    Set<String> allowedParentUniqueIds = new HashSet<>(topology.resolveParentUniqueIds(scopeString));

    String type = (f.getType() == null || f.getType().equalsIgnoreCase("all")) ? null : f.getType().toLowerCase();

    Set<String> owners = isEmpty(f.getOwners())
        ? Collections.emptySet()
        : f.getOwners().stream().map(String::toLowerCase).collect(Collectors.toSet());

    Set<String> lifecycle = isEmpty(f.getLifecycle()) ? Collections.emptySet() : new HashSet<>(f.getLifecycle());

    Set<String> tags = isEmpty(f.getTags()) ? Collections.emptySet() : new HashSet<>(f.getTags());

    return new CompiledScorecardFilter(
        scorecard.getIdentifier(), kind, type, owners, lifecycle, tags, allowedParentUniqueIds);
  }

  private boolean matches(CompiledScorecardFilter filter, CatalogEntity entity) {
    if (!filter.kind.equalsIgnoreCase(entity.getKind())) {
      return false;
    }

    if (filter.type != null && entity.getType() != null && !filter.type.equalsIgnoreCase(entity.getType())) {
      return false;
    }

    if (!filter.owners.isEmpty() && entity.getOwner() != null
        && !filter.owners.contains(entity.getOwner().toLowerCase())) {
      return false;
    }

    if (!filter.lifecycle.isEmpty()) {
      Object lifecycleObj = entity.getSpec() != null ? entity.getSpec().get("lifecycle") : null;
      if (lifecycleObj instanceof String lifecycle && !filter.lifecycle.contains(lifecycle)) {
        return false;
      }
    }

    if (!filter.allowedParentUniqueIds.contains(entity.getParentUniqueId())) {
      return false;
    }

    if (!filter.tags.isEmpty()) {
      List<String> entityTags = entity.getTags();
      if (isEmpty(entityTags) || !new HashSet<>(entityTags).containsAll(filter.tags)) {
        return false;
      }
    }

    return true;
  }

  private static class CompiledScorecardFilter {
    final String scorecardIdentifier;
    final String kind;
    final String type;
    final Set<String> owners;
    final Set<String> lifecycle;
    final Set<String> tags;
    final Set<String> allowedParentUniqueIds;

    CompiledScorecardFilter(String scorecardIdentifier, String kind, String type, Set<String> owners,
        Set<String> lifecycle, Set<String> tags, Set<String> allowedParentUniqueIds) {
      this.scorecardIdentifier = scorecardIdentifier;
      this.kind = kind;
      this.type = type;
      this.owners = owners;
      this.lifecycle = lifecycle;
      this.tags = tags;
      this.allowedParentUniqueIds = allowedParentUniqueIds;
    }
  }
}
