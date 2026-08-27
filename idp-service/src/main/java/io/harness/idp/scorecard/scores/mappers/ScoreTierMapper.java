/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.ScoreTier;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScoreTierMapper {
  public Optional<ScoreTier> fromScoreEntity(ScoreEntity scoreEntity) {
    if (scoreEntity == null || StringUtils.isBlank(scoreEntity.getTierName())) {
      return Optional.empty();
    }
    return Optional.of(buildScoreTier(scoreEntity.getTierName(), scoreEntity.getTierGroupIdentifier(),
        scoreEntity.getTierDescription(), scoreEntity.getTierIcon(), scoreEntity.getTierColour()));
  }

  public Optional<ScoreTier> fromScorecardStatsEntity(ScorecardStatsEntity scorecardStatsEntity) {
    if (scorecardStatsEntity == null || StringUtils.isBlank(scorecardStatsEntity.getTierName())) {
      return Optional.empty();
    }
    return Optional.of(buildScoreTier(scorecardStatsEntity.getTierName(), scorecardStatsEntity.getTierGroupIdentifier(),
        scorecardStatsEntity.getTierDescription(), scorecardStatsEntity.getTierIcon(),
        scorecardStatsEntity.getTierColour()));
  }

  private ScoreTier buildScoreTier(
      String tierName, String tierGroupIdentifier, String tierDescription, String tierIcon, String tierColour) {
    return new ScoreTier()
        .tierName(tierName)
        .tierGroupIdentifier(tierGroupIdentifier)
        .tierDescription(tierDescription)
        .tierIcon(tierIcon)
        .tierColour(tierColour);
  }
}
