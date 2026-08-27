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
import io.harness.spec.server.idp.v1.model.ScorecardScore;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardScoreMapperTest {
  public static final String accountidentifier = "accountidentifier";
  public static final String entityidentifier = "entityidentifier";
  public static final String scorecardidentifier = "scorecardidentifier";
  public static final String scorecardName = "Test Scorecard";
  public static final String scorecardDescription = "This is a test scorecard";
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTO() {
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(accountidentifier)
                                  .entityIdentifier(entityidentifier)
                                  .scorecardIdentifier(scorecardidentifier)
                                  .lastComputedTimestamp(123456789L)
                                  .score(85)
                                  .tierName("Gold")
                                  .tierGroupIdentifier("default_tiers")
                                  .checkStatus(Collections.emptyList())
                                  .build();
    ScorecardScore result = ScorecardScoreMapper.toDTO(scoreEntity, scorecardName, scorecardDescription);
    assertEquals(scorecardName, result.getScorecardName());
    assertEquals(scorecardDescription, result.getDescription());
    assertNotNull(result.getTier());
    assertEquals("Gold", result.getTier().getTierName());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testToDTOOmitsTierWhenDisabled() {
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(accountidentifier)
                                  .entityIdentifier(entityidentifier)
                                  .scorecardIdentifier(scorecardidentifier)
                                  .score(85)
                                  .tierName("Gold")
                                  .tierGroupIdentifier("default_tiers")
                                  .checkStatus(Collections.emptyList())
                                  .build();
    ScorecardScore result = ScorecardScoreMapper.toDTO(scoreEntity, scorecardName, scorecardDescription, false);
    assertNull(result.getTier());
  }
}