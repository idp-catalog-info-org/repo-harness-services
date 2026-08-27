/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scores.mappers.ScoreTierMapper;
import io.harness.spec.server.idp.v1.model.ScorecardStats;
import io.harness.spec.server.idp.v1.model.ScorecardStatsResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardStatsMapper {
  public ScorecardStatsResponse toDTO(List<ScorecardStatsEntity> scorecardStatsEntities, String name) {
    return toDTO(scorecardStatsEntities, name, true);
  }

  public ScorecardStatsResponse toDTO(
      List<ScorecardStatsEntity> scorecardStatsEntities, String name, boolean includeTier) {
    ScorecardStatsResponse response = new ScorecardStatsResponse();
    response.setName(name);
    long lastComputedAt = 0;
    List<ScorecardStats> scorecardStats = new ArrayList<>();
    for (ScorecardStatsEntity scorecardStatsEntity : scorecardStatsEntities) {
      ScorecardStats stats = new ScorecardStats();
      StatsMetadata metadata = scorecardStatsEntity.getMetadata();
      stats.setName(metadata.getName());
      stats.setNamespace(metadata.getNamespace());
      stats.setOwner(metadata.getOwner());
      stats.setSystem(metadata.getSystem());
      stats.setKind(metadata.getKind());
      stats.setType(metadata.getType());
      if (lastComputedAt == 0) {
        lastComputedAt = scorecardStatsEntity.getLastUpdatedAt();
      }
      stats.setScore(scorecardStatsEntity.getScore());
      if (includeTier) {
        ScoreTierMapper.fromScorecardStatsEntity(scorecardStatsEntity).ifPresent(stats::setTier);
      }
      scorecardStats.add(stats);
    }
    response.setTimestamp(lastComputedAt);
    response.setStats(scorecardStats);
    return response;
  }
}
