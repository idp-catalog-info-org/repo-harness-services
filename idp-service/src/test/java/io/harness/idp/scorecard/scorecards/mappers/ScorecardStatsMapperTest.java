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
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardStatsResponse;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardStatsMapperTest extends CategoryTest {
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void mapsTier() {
    ScorecardStatsEntity entity = ScorecardStatsEntity.builder()
                                      .score(85)
                                      .tierName("Gold")
                                      .tierGroupIdentifier("default_tiers")
                                      .metadata(StatsMetadata.builder().name("service").build())
                                      .build();

    ScorecardStatsResponse response = ScorecardStatsMapper.toDTO(List.of(entity), "Scorecard");

    assertThat(response.getStats().get(0).getTier()).isNotNull();
    assertThat(response.getStats().get(0).getTier().getTierName()).isEqualTo("Gold");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void omitsTierWhenDisabled() {
    ScorecardStatsEntity entity = ScorecardStatsEntity.builder()
                                      .score(85)
                                      .tierName("Gold")
                                      .tierGroupIdentifier("default_tiers")
                                      .metadata(StatsMetadata.builder().name("service").build())
                                      .build();

    ScorecardStatsResponse response = ScorecardStatsMapper.toDTO(List.of(entity), "Scorecard", false);

    assertThat(response.getStats().get(0).getTier()).isNull();
  }
}
