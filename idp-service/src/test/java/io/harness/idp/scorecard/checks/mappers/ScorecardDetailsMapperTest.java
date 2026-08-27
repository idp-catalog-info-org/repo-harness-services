/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardDetailsMapper;
import io.harness.idp.scorecard.scorecards.repositories.CountAndPercentage;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardDetailsMapperTest {
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTO() {
    ScorecardEntity.Check check1 =
        ScorecardEntity.Check.builder().identifier("checkidentifier").isCustom(false).weightage(1).build();
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .identifier("identifier")
                                          .name("name")
                                          .description("description")
                                          .tierGroupIdentifier("default_tiers")
                                          .componentCount(1)
                                          .tierComponentCounts(List.of(ScorecardEntity.TierComponentCount.builder()
                                                                           .tierName("Gold")
                                                                           .minScore(75)
                                                                           .maxScore(100)
                                                                           .tierColour("#00FF00")
                                                                           .componentCount(1)
                                                                           .build()))
                                          .checks(List.of(check1))
                                          .build();
    CheckEntity checkEntity =
        CheckEntity.builder().name("name").identifier("identifier").description("description").build();
    Map<String, CheckEntity> checkEntityMap = Map.of("key", checkEntity);
    String harnessAccount = "harnessAccount";
    ScorecardDetailsResponse response = ScorecardDetailsMapper.toDTO(scorecardEntity, checkEntityMap, harnessAccount);
    assertEquals(1, (long) response.getScorecard().getComponents());
    assertNull(response.getScorecard().getPercentage());
    assertEquals(1, response.getScorecard().getTierAnalytics().size());
    assertEquals("Gold", response.getScorecard().getTierAnalytics().get(0).getTierName());
    assertEquals(75, (int) response.getScorecard().getTierAnalytics().get(0).getMinScore());
    assertEquals(1, (int) response.getScorecard().getTierAnalytics().get(0).getComponentCount());
    assertEquals(100.0, response.getScorecard().getTierAnalytics().get(0).getPercentage());
    assertEquals(Arrays.asList("checkidentifier"), response.getScorecard().getChecksMissing());
    assertEquals("default_tiers", response.getScorecard().getTierGroupIdentifier());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testToDTOLegacyCountsWhenTierAnalyticsDisabled() {
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .identifier("identifier")
                                          .name("name")
                                          .componentCount(1)
                                          .tierComponentCounts(List.of())
                                          .checks(List.of())
                                          .build();

    ScorecardDetailsResponse response = ScorecardDetailsMapper.toDTO(scorecardEntity, Map.of(),
        CountAndPercentage.builder().count(4).percentage(0.75).build(), false, "harnessAccount");

    assertEquals(4, (int) response.getScorecard().getComponents());
    assertEquals(75.0, response.getScorecard().getPercentage());
    assertNull(response.getScorecard().getTierAnalytics());
    assertNull(response.getScorecard().getTierGroupIdentifier());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testToDTOOmitsTierWhenDisabledEvenIfPresentOnEntity() {
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .identifier("identifier")
                                          .name("name")
                                          .tierGroupIdentifier("default_tiers")
                                          .componentCount(1)
                                          .tierComponentCounts(List.of())
                                          .checks(List.of())
                                          .build();

    ScorecardDetailsResponse response =
        ScorecardDetailsMapper.toDTO(scorecardEntity, Map.of(), null, false, "harnessAccount");

    assertNull(response.getScorecard().getTierGroupIdentifier());
    assertNull(response.getScorecard().getTierAnalytics());
    assertNull(response.getScorecard().getComponents());
  }
}