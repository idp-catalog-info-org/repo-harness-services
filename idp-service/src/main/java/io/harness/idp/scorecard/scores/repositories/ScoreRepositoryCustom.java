/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.IDP)
public interface ScoreRepositoryCustom {
  AggregationResults<ScoreEntityByScorecardIdentifier> getAllLatestScoresByScorecardsForAnEntity(
      String accountIdentifier, String entityIdentifier, boolean idpV2Enabled);
  AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> getAllLatestScoresByScorecardsForEntities(
      String accountIdentifier, List<String> entityIdentifiers, boolean idpV2Enabled);

  /**
   * Same as {@link #getAllLatestScoresByScorecardsForEntities(String, List, boolean)} but only considers
   * score documents computed at or after {@code computedAfter} (epoch millis). Because each compute run
   * writes a fresh document for every entity, bounding the window keeps the in-memory sort small. A
   * {@code computedAfter <= 0} means no lower bound (equivalent to the unbounded variant).
   */
  AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> getAllLatestScoresByScorecardsForEntities(
      String accountIdentifier, List<String> entityIdentifiers, boolean idpV2Enabled, long computedAfter);
  List<ScoresByScorecardIdentifier> getAllScoresByAccountIdentifierAndScorecardIdentifierPerDay(
      String accountIdentifier, String scorecardIdentifier);

  ScoreEntity getLatestComputedScoreForEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scoreCardIdentifier, boolean idpV2Enabled);

  List<ScoreEntityByEntityIdentifier> getLatestScoresForScorecard(String accountIdentifier, String scorecardIdentifier);

  List<ScoreEntityByEntityIdentifier> getLatestScorePerEntityForScorecard(
      String accountIdentifier, String scorecardIdentifier);

  UpdateResult updateCheckIdentifier(ScoreEntity score, List<CheckStatus> checkStatuses);
  UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid);
  List<String> findUniqueEntityIdentifiers(String accountIdentifier);
}
