/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardStatsResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public interface ScorecardService {
  List<Scorecard> getAllScorecardsAndChecksDetails(String accountIdentifier);
  List<ScorecardAndChecks> getAllScorecardAndChecks(String accountIdentifier, List<String> scorecardIdentifiers);
  void saveScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier);
  void updateScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier);
  ScorecardDetailsResponse getScorecardDetails(String accountIdentifier, String identifier);
  List<ScorecardFilter> getScorecardFilters(String accountIdentifier, List<String> scorecardIdentifiers);
  void deleteScorecard(String accountIdentifier, String identifier);
  List<EntityFiltersResponse> getAllEntityFacets(String accountIdentifier, String kind);
  ScorecardStatsResponse getScorecardStats(String accountIdentifier, String identifier);
  List<String> getScorecardIdentifiers(String accountIdentifier, String checkIdentifier, Boolean custom);
  void migrateEntityIdentifier(Map<String, String> entityIdentifiersMap, String accountIdentifier);
  void modifyEntityIdentifier(String accountIdentifier);
  void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids);
  void modifyScopeForEntityIdentifier(
      String accountIdentifier, String existingEntityIdentifier, String modifiedEntityIdentifier);
  void addScopeToScorecardsForAccount(String accountIdentifier);
  Map<String, CheckEntity> getIdentifierCheckEntityMapping(String accountIdentifier, Set<String> checkIds);

  String ensureScorecardTierGroupIdentifier(String accountIdentifier, ScorecardEntity scorecard);
}
