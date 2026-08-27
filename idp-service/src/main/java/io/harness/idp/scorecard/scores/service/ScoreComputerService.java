/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import io.harness.beans.ScopeInfo;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public interface ScoreComputerService {
  void computeScores(String accountIdentifier, List<String> scorecardIdentifiers, List<String> entityIdentifiers);

  Set<BackstageCatalogEntity> getAllEntities(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters);

  Set<CatalogEntity> getAllEntitiesForIDPCatalogs(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters);

  ScorecardRecalibrateInfo computeScoresAsync(
      String accountIdentifier, String scorecardIdentifier, String entityIdentifier);

  Set<? extends BackstageCatalogEntity> getBackstageEntitiesForScorecardsAndEntityIdentifiers(
      String accountIdentifier, List<ScorecardAndChecks> scorecardsAndChecks, List<String> entityIdentifiers);

  Set<? extends CatalogEntity> getCatalogEntitiesForScorecardsAndEntityIdentifiers(
      String accountIdentifier, List<ScorecardAndChecks> scorecardsAndChecks, List<String> entityIdentifiers);

  boolean isFilterMatchingWithAnEntity(ScorecardFilter filter, BackstageCatalogEntity entity);
  Pair<Boolean, Map<String, Set<ScopeInfo>>> isFilterMatchingWithCatalogEntity(
      ScorecardFilter filter, CatalogEntity entity, Map<String, Set<ScopeInfo>> scopeInfosForScopes);
}
