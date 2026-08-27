/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.mappers;

import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardGraphSummaryInfoMapperTest {
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTOwithValidScoreEntity() {
    String scorecardIdentifier = "testScorecard";
    int score = 85;
    long lastComputedTimestamp = System.currentTimeMillis();

    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .scorecardIdentifier(scorecardIdentifier)
                                  .score(score)
                                  .lastComputedTimestamp(lastComputedTimestamp)
                                  .build();
    ScorecardGraphSummaryInfo result = ScorecardGraphSummaryInfoMapper.toDTO(scoreEntity);
    assertEquals(scorecardIdentifier, result.getScorecardIdentifier());
    assertEquals((int) score, (int) result.getScore());
    assertEquals((long) lastComputedTimestamp, (long) result.getTimestamp());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTOWithNullScoreEntity() {
    ScorecardGraphSummaryInfo result = ScorecardGraphSummaryInfoMapper.toDTO(null);
    ScorecardGraphSummaryInfo scorecardGraphSummaryInfo = new ScorecardGraphSummaryInfo();
    assertEquals(scorecardGraphSummaryInfo, result);
  }
}
