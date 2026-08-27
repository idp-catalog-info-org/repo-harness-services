/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.mappers;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Tier;
import io.harness.spec.server.idp.v1.model.TierGroupDetails;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupDetailsMapperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void fromDTOTrimsIdentifierAndNameAndSortsTiersByMinScore() {
    TierGroupDetailsRequest request = new TierGroupDetailsRequest().tierGroup(
        new TierGroupDetails()
            .identifier("  compliance_tiers  ")
            .name("  Compliance Tiers  ")
            .tiers(List.of(buildTier("Gold", 75, 100), buildTier("Bronze", 0, 49), buildTier("Silver", 50, 74))));

    TierGroupEntity entity = TierGroupDetailsMapper.fromDTO(request, ACCOUNT_ID);

    assertThat(entity.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(entity.getIdentifier()).isEqualTo("compliance_tiers");
    assertThat(entity.getName()).isEqualTo("Compliance Tiers");
    assertThat(entity.getTiers())
        .extracting(TierGroupEntity.Tier::getName, TierGroupEntity.Tier::getMinScore)
        .containsExactly(tuple("Bronze", 0), tuple("Silver", 50), tuple("Gold", 75));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void toDTOSortsTiersByMinScore() {
    TierGroupEntity entity = TierGroupEntity.builder()
                                 .identifier("compliance_tiers")
                                 .name("Compliance Tiers")
                                 .tiers(List.of(TierGroupEntity.Tier.builder()
                                                    .name("Gold")
                                                    .icon("https://example.com/gold.png")
                                                    .colour("#FFD700")
                                                    .minScore(75)
                                                    .maxScore(100)
                                                    .build(),
                                     TierGroupEntity.Tier.builder()
                                         .name("Bronze")
                                         .icon("https://example.com/bronze.png")
                                         .colour("#CD7F32")
                                         .minScore(0)
                                         .maxScore(49)
                                         .build()))
                                 .build();

    TierGroupDetailsResponse response = TierGroupDetailsMapper.toDTO(entity);

    assertThat(response.getTierGroup().getTiers())
        .extracting(Tier::getName, Tier::getMinScore)
        .containsExactly(tuple("Bronze", 0), tuple("Gold", 75));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void toListItemMapsSummaryFields() {
    TierGroupEntity entity = TierGroupEntity.builder()
                                 .identifier("default_tiers")
                                 .name("Default Tier Group")
                                 .description("Default description")
                                 .tiers(List.of(TierGroupEntity.Tier.builder()
                                                    .name("Bronze")
                                                    .icon("https://example.com/bronze.png")
                                                    .colour("#CD7F32")
                                                    .minScore(0)
                                                    .maxScore(100)
                                                    .build()))
                                 .build();

    assertThat(TierGroupDetailsMapper.toListItem(entity).getIdentifier()).isEqualTo("default_tiers");
    assertThat(TierGroupDetailsMapper.toListItem(entity).getName()).isEqualTo("Default Tier Group");
    assertThat(TierGroupDetailsMapper.toListItem(entity).getDescription()).isEqualTo("Default description");
    assertThat(TierGroupDetailsMapper.toListItem(entity).getTiers()).hasSize(1);
  }

  private Tier buildTier(String name, int minScore, int maxScore) {
    return new Tier()
        .name(name)
        .description(name + " description")
        .icon("https://example.com/" + name.toLowerCase() + ".png")
        .colour("#000000")
        .minScore(minScore)
        .maxScore(maxScore);
  }
}
