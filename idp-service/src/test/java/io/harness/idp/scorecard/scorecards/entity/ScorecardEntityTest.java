/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.entity;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardEntityTest extends CategoryTest {
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldStoreComponentCountAndTierSnapshot() {
    ScorecardEntity.TierComponentCount bronzeTier = ScorecardEntity.TierComponentCount.builder()
                                                        .tierName("Bronze")
                                                        .minScore(0)
                                                        .maxScore(74)
                                                        .tierColour("#CD7F32")
                                                        .componentCount(1)
                                                        .build();
    ScorecardEntity.TierComponentCount goldTier = ScorecardEntity.TierComponentCount.builder()
                                                      .tierName("Gold")
                                                      .minScore(75)
                                                      .maxScore(100)
                                                      .tierColour("#FFD700")
                                                      .componentCount(2)
                                                      .build();

    ScorecardEntity scorecard = ScorecardEntity.builder()
                                    .componentCount(3)
                                    .tierComponentCounts(List.of(bronzeTier, goldTier))
                                    .scoreCountsComputedAt(1234L)
                                    .build();

    assertThat(scorecard.getComponentCount()).isEqualTo(3);
    assertThat(scorecard.getTierComponentCounts()).containsExactly(bronzeTier, goldTier);
    assertThat(scorecard.getScoreCountsComputedAt()).isEqualTo(1234L);
  }
}
