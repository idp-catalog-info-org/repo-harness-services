/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.mappers;

import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardSummaryInfoMapperTest {
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTOWithNullScoreEntity() {
    String scoreCardName = "Test Scorecard";
    String scoreCardDescription = "Description of Test Scorecard";
    String scoreCardIdentifier = "scorecard-123";
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo();
    ScorecardSummaryInfo result = ScorecardSummaryInfoMapper.toDTO(
        null, scoreCardName, scoreCardDescription, scoreCardIdentifier, recalibrateInfo);
    assertEquals(0, (long) result.getScore());
    assertNull(result.getTimestamp());
    assertEquals(scoreCardIdentifier, result.getScorecardIdentifier());
    assertEquals(scoreCardName, result.getScorecardName());
    assertEquals(scoreCardDescription, result.getDescription());
    assertEquals(recalibrateInfo, result.getRecalibrateInfo());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTOWithScoreEntity() {
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .score(85)
                                  .lastComputedTimestamp(1632873600000L)
                                  .tierName("Gold")
                                  .tierGroupIdentifier("default_tiers")
                                  .checkStatus(List.of(new CheckStatus().status(CheckStatus.StatusEnum.valueOf("PASS")),
                                      new CheckStatus().status(CheckStatus.StatusEnum.valueOf("FAIL"))))
                                  .build();
    String scoreCardName = "Test Scorecard";
    String scoreCardDescription = "Description of Test Scorecard";
    String scoreCardIdentifier = "scorecard-123";
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo();
    ScorecardSummaryInfo result = ScorecardSummaryInfoMapper.toDTO(
        scoreEntity, scoreCardName, scoreCardDescription, scoreCardIdentifier, recalibrateInfo);
    assertEquals(85, (int) result.getScore());
    assertEquals(1632873600000L, (long) result.getTimestamp());
    assertEquals(scoreCardIdentifier, result.getScorecardIdentifier());
    assertEquals(scoreCardName, result.getScorecardName());
    assertEquals(scoreCardDescription, result.getDescription());
    assertEquals(recalibrateInfo, result.getRecalibrateInfo());
    assertEquals(scoreEntity.getCheckStatus(), result.getChecksStatuses());
    assertNotNull(result.getTier());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testToDTOOmitsTierWhenDisabled() {
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .score(85)
                                  .lastComputedTimestamp(1632873600000L)
                                  .tierName("Gold")
                                  .tierGroupIdentifier("default_tiers")
                                  .checkStatus(List.of())
                                  .build();
    ScorecardSummaryInfo result =
        ScorecardSummaryInfoMapper.toDTO(scoreEntity, "Test Scorecard", "Description", "scorecard-123", null, false);
    assertNull(result.getTier());
  }
}
