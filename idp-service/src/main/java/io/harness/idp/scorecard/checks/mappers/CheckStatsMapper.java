/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStats;
import io.harness.spec.server.idp.v1.model.CheckStatsResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CheckStatsMapper {
  public CheckStatsResponse toDTO(List<CheckStatsEntity> checkStatsEntities, String name) {
    CheckStatsResponse response = new CheckStatsResponse();
    response.setName(name);
    long lastComputedAt = 0;
    List<CheckStats> checkStats = new ArrayList<>();
    for (CheckStatsEntity checkStatsEntity : checkStatsEntities) {
      CheckStats stats = new CheckStats();
      StatsMetadata metadata = checkStatsEntity.getMetadata();
      stats.setName(metadata.getName());
      stats.setNamespace(metadata.getNamespace());
      stats.setOwner(metadata.getOwner());
      stats.setSystem(metadata.getSystem());
      stats.setKind(metadata.getKind());
      stats.setType(metadata.getType());
      lastComputedAt = Long.max(lastComputedAt, checkStatsEntity.getLastUpdatedAt());
      stats.setStatus(checkStatsEntity.getStatus());
      checkStats.add(stats);
    }
    response.setTimestamp(lastComputedAt);
    response.setStats(checkStats);
    return response;
  }

  public List<CheckGraph> toDTO(List<CheckStatusEntity> checkStatusEntities) {
    List<CheckGraph> checkGraphs = new ArrayList<>();
    for (CheckStatusEntity checkStatusEntity : checkStatusEntities) {
      CheckGraph checkGraph = new CheckGraph();
      checkGraph.setCount(checkStatusEntity.getPassCount());
      checkGraph.setTimestamp(checkStatusEntity.getTimestamp());
      checkGraphs.add(checkGraph);
    }
    return checkGraphs;
  }
}
