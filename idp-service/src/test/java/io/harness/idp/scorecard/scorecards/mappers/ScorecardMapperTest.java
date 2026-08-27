/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.repositories.CountAndPercentage;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardTierAnalytics;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardMapperTest extends CategoryTest {
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldMapStoredTierAnalytics() {
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .name("Service Maturity")
                                          .identifier("service_maturity")
                                          .checks(Collections.emptyList())
                                          .tierGroupIdentifier("default_tiers")
                                          .componentCount(3)
                                          .tierComponentCounts(List.of(ScorecardEntity.TierComponentCount.builder()
                                                                           .tierName("Silver")
                                                                           .minScore(50)
                                                                           .maxScore(74)
                                                                           .tierColour("#C0C0C0")
                                                                           .componentCount(1)
                                                                           .build(),
                                              ScorecardEntity.TierComponentCount.builder()
                                                  .tierName("Gold")
                                                  .minScore(75)
                                                  .maxScore(100)
                                                  .tierColour("#FFD700")
                                                  .componentCount(2)
                                                  .build()))
                                          .build();

    Scorecard scorecard = ScorecardMapper.toDTO(scorecardEntity, Collections.emptyMap(), "account");

    assertThat(scorecard.getTierGroupIdentifier()).isEqualTo("default_tiers");
    assertThat(scorecard.getComponents()).isEqualTo(3);
    assertThat(scorecard.getPercentage()).isNull();
    assertThat(scorecard.getTierAnalytics()).hasSize(2);
    ScorecardTierAnalytics silver = scorecard.getTierAnalytics().get(0);
    assertThat(silver.getTierName()).isEqualTo("Silver");
    assertThat(silver.getMinScore()).isEqualTo(50);
    assertThat(silver.getMaxScore()).isEqualTo(74);
    assertThat(silver.getTierColour()).isEqualTo("#C0C0C0");
    assertThat(silver.getComponentCount()).isEqualTo(1);
    assertThat(silver.getPercentage()).isEqualTo(33.0);
    ScorecardTierAnalytics gold = scorecard.getTierAnalytics().get(1);
    assertThat(gold.getTierName()).isEqualTo("Gold");
    assertThat(gold.getMinScore()).isEqualTo(75);
    assertThat(gold.getMaxScore()).isEqualTo(100);
    assertThat(gold.getTierColour()).isEqualTo("#FFD700");
    assertThat(gold.getComponentCount()).isEqualTo(2);
    assertThat(gold.getPercentage()).isEqualTo(67.0);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldMapZeroCountsWithoutDividingByZero() {
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .name("Service Maturity")
                                          .identifier("service_maturity")
                                          .checks(Collections.emptyList())
                                          .componentCount(0)
                                          .tierComponentCounts(List.of(ScorecardEntity.TierComponentCount.builder()
                                                                           .tierName("Gold")
                                                                           .minScore(75)
                                                                           .maxScore(100)
                                                                           .tierColour("#FFD700")
                                                                           .componentCount(0)
                                                                           .build()))
                                          .build();

    Scorecard scorecard = ScorecardMapper.toDTO(scorecardEntity, Collections.emptyMap(), "account");

    assertThat(scorecard.getComponents()).isZero();
    assertThat(scorecard.getTierAnalytics()).hasSize(1);
    assertThat(scorecard.getTierAnalytics().get(0).getComponentCount()).isZero();
    assertThat(scorecard.getTierAnalytics().get(0).getPercentage()).isZero();
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldMapLegacyCountsWhenTierAnalyticsDisabled() {
    ScorecardEntity scorecardEntity = ScorecardEntity.builder()
                                          .name("Service Maturity")
                                          .identifier("service_maturity")
                                          .checks(Collections.emptyList())
                                          .tierGroupIdentifier("default_tiers")
                                          .componentCount(3)
                                          .tierComponentCounts(Collections.emptyList())
                                          .build();

    Scorecard scorecard = ScorecardMapper.toDTO(scorecardEntity, Collections.emptyMap(),
        CountAndPercentage.builder().count(4).percentage(0.75).build(), false, "account");

    assertThat(scorecard.getComponents()).isEqualTo(4);
    assertThat(scorecard.getPercentage()).isEqualTo(75.0);
    assertThat(scorecard.getTierAnalytics()).isNull();
    assertThat(scorecard.getTierGroupIdentifier()).isNull();
  }
}
