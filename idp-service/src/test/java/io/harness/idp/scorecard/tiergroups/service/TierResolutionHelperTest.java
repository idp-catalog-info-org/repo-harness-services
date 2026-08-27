/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.DEFAULT_TIER_GROUP_IDENTIFIER;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.MAX_TIERS;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.TIER_GROUP_DESCRIPTION_MAX_LENGTH;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierIcon;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class TierResolutionHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersAcceptsContiguousRanges() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49), buildTier("Silver", 50, 74), buildTier("Gold", 75, 100));

    TierResolutionHelper.validateTiers(tiers);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersAcceptsMissingDescription() {
    Tier tierWithoutDescription = buildTier("Bronze", 0, 49).description(null);
    List<Tier> tiers = List.of(tierWithoutDescription, buildTier("Silver", 50, 100));

    TierResolutionHelper.validateTiers(tiers);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsGaps() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 40), buildTier("Gold", 75, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("contiguous");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsFewerThanTwoTiers() {
    List<Tier> tiers = List.of(buildTier("Only", 0, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("At least 2 tiers");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsDuplicateNames() {
    Tier duplicateNameTier = buildTier("Bronze", 0, 49);
    List<Tier> tiers = List.of(duplicateNameTier, buildTier("Bronze", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Duplicate tier name");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersAllowsDifferentCasingForDistinctNames() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49), buildTier("bronze", 50, 100));

    TierResolutionHelper.validateTiers(tiers);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersTrimsNamesBeforeUniquenessCheck() {
    Tier trimmedNameTier = new Tier()
                               .name(" Bronze ")
                               .description("Bronze description")
                               .icon("https://example.com/bronze.png")
                               .colour("#000000")
                               .minScore(0)
                               .maxScore(49);
    List<Tier> tiers = List.of(trimmedNameTier, buildTier("Silver", 50, 74), buildTier("Gold", 75, 100));

    TierResolutionHelper.validateTiers(tiers);

    assertThat(tiers.get(0).getName()).isEqualTo("Bronze");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersForDefaultTierGroupAcceptsBuiltInIcon() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49).icon("BRONZE"), buildTier("Silver", 50, 100).icon("gold"));

    TierResolutionHelper.validateTiers(tiers, DEFAULT_TIER_GROUP_IDENTIFIER);

    assertThat(tiers.get(0).getIcon()).isEqualTo("BRONZE");
    assertThat(tiers.get(1).getIcon()).isEqualTo("GOLD");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersForDefaultTierGroupAcceptsUploadedIconUrl() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49), buildTier("Silver", 50, 100));

    TierResolutionHelper.validateTiers(tiers, DEFAULT_TIER_GROUP_IDENTIFIER);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersForCustomTierGroupRejectsBuiltInIcon() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49).icon("BRONZE"), buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers, "custom_tiers"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("valid HTTPS URL");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveTierIconForDisplayMapsBuiltInIconToUrl() {
    assertThat(TierResolutionHelper.resolveTierIconForDisplay("BRONZE"))
        .isEqualTo(DefaultTierIcon.BRONZE.getDisplayUrl());
    assertThat(TierResolutionHelper.resolveTierIconForDisplay("https://example.com/custom.png"))
        .isEqualTo("https://example.com/custom.png");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsInvalidIconUrl() {
    Tier invalidIconTier = buildTier("Bronze", 0, 49);
    invalidIconTier.setIcon("ftp://example.com/icon.png");
    List<Tier> tiers = List.of(invalidIconTier, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("valid HTTPS URL");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsHttpIconUrl() {
    Tier httpIconTier = buildTier("Bronze", 0, 49);
    httpIconTier.setIcon("http://example.com/icon.png");
    List<Tier> tiers = List.of(httpIconTier, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("valid HTTPS URL");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveTierReturnsMatchingTier() {
    TierGroupEntity tierGroup = TierGroupEntity.builder()
                                    .identifier("compliance")
                                    .tiers(List.of(TierGroupEntity.Tier.builder()
                                                       .name("Gold")
                                                       .description("Gold tier")
                                                       .icon("https://example.com/gold.png")
                                                       .colour("#FFD700")
                                                       .minScore(75)
                                                       .maxScore(100)
                                                       .build(),
                                        TierGroupEntity.Tier.builder()
                                            .name("Silver")
                                            .description("Silver tier")
                                            .icon("https://example.com/silver.png")
                                            .colour("#C0C0C0")
                                            .minScore(50)
                                            .maxScore(74)
                                            .build()))
                                    .build();

    Optional<TierGroupEntity.Tier> resolved = TierResolutionHelper.resolveTier(tierGroup, 80);

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getName()).isEqualTo("Gold");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersAcceptsMaxTierCount() {
    List<Tier> tiers = buildContiguousTiers(MAX_TIERS);

    TierResolutionHelper.validateTiers(tiers);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsMoreThanMaxTierCount() {
    List<Tier> tiers = buildContiguousTiers(MAX_TIERS + 1);

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("more than " + MAX_TIERS);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsOverlappingRanges() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 50), buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("contiguous");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsRangesNotStartingAtZero() {
    List<Tier> tiers = List.of(buildTier("Bronze", 1, 50), buildTier("Silver", 51, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("start at 0");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsRangesNotEndingAtHundred() {
    List<Tier> tiers = List.of(buildTier("Bronze", 0, 49), buildTier("Silver", 50, 99));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("end at 100");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsEmptyIcon() {
    Tier tierWithEmptyIcon = buildTier("Bronze", 0, 49);
    tierWithEmptyIcon.setIcon("  ");
    List<Tier> tiers = List.of(tierWithEmptyIcon, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("icon cannot be empty");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsMalformedIconUrl() {
    Tier tierWithMalformedIcon = buildTier("Bronze", 0, 49);
    tierWithMalformedIcon.setIcon("not-a-url");
    List<Tier> tiers = List.of(tierWithMalformedIcon, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("valid HTTPS URL");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsEmptyTierName() {
    Tier tierWithEmptyName = buildTier("Bronze", 0, 49);
    tierWithEmptyName.setName("  ");
    List<Tier> tiers = List.of(tierWithEmptyName, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Tier name cannot be empty");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsEmptyColour() {
    Tier tierWithEmptyColour = buildTier("Bronze", 0, 49);
    tierWithEmptyColour.setColour("  ");
    List<Tier> tiers = List.of(tierWithEmptyColour, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("colour cannot be empty");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsNullMinScore() {
    Tier tierWithNullMin = buildTier("Bronze", 0, 49);
    tierWithNullMin.setMinScore(null);
    List<Tier> tiers = List.of(tierWithNullMin, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("min_score and max_score");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsNullMaxScore() {
    Tier tierWithNullMax = buildTier("Bronze", 0, 49);
    tierWithNullMax.setMaxScore(null);
    List<Tier> tiers = List.of(tierWithNullMax, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("min_score and max_score");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsMinScoreGreaterThanMaxScore() {
    List<Tier> tiers = List.of(buildTier("Bronze", 60, 40), buildTier("Silver", 41, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("min_score cannot be greater than max_score");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsScoreBelowZero() {
    Tier tierWithNegativeMin = buildTier("Bronze", -1, 49);
    List<Tier> tiers = List.of(tierWithNegativeMin, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("min_score must be between");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsScoreAboveHundred() {
    Tier tierWithHighMax = buildTier("Bronze", 0, 101);
    List<Tier> tiers = List.of(tierWithHighMax, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("max_score must be between");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateDescriptionLengthRejectsOverMaxLength() {
    String longDescription = "x".repeat(TIER_GROUP_DESCRIPTION_MAX_LENGTH + 1);

    assertThatThrownBy(() -> TierResolutionHelper.validateDescriptionLength(longDescription, "Tier group description"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.valueOf(TIER_GROUP_DESCRIPTION_MAX_LENGTH));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void validateTiersRejectsTierDescriptionOverMaxLength() {
    Tier tierWithLongDescription = buildTier("Bronze", 0, 49);
    tierWithLongDescription.setDescription("x".repeat(TIER_GROUP_DESCRIPTION_MAX_LENGTH + 1));
    List<Tier> tiers = List.of(tierWithLongDescription, buildTier("Silver", 50, 100));

    assertThatThrownBy(() -> TierResolutionHelper.validateTiers(tiers))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.valueOf(TIER_GROUP_DESCRIPTION_MAX_LENGTH));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveTierReturnsCorrectTierAtBoundaries() {
    TierGroupEntity tierGroup = buildDefaultTierGroupEntity();

    assertThat(TierResolutionHelper.resolveTier(tierGroup, 0).map(TierGroupEntity.Tier::getName)).contains("Bronze");
    assertThat(TierResolutionHelper.resolveTier(tierGroup, 49).map(TierGroupEntity.Tier::getName)).contains("Bronze");
    assertThat(TierResolutionHelper.resolveTier(tierGroup, 50).map(TierGroupEntity.Tier::getName)).contains("Silver");
    assertThat(TierResolutionHelper.resolveTier(tierGroup, 74).map(TierGroupEntity.Tier::getName)).contains("Silver");
    assertThat(TierResolutionHelper.resolveTier(tierGroup, 75).map(TierGroupEntity.Tier::getName)).contains("Gold");
    assertThat(TierResolutionHelper.resolveTier(tierGroup, 100).map(TierGroupEntity.Tier::getName)).contains("Gold");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void resolveTierReturnsEmptyForNullTierGroup() {
    assertThat(TierResolutionHelper.resolveTier(null, 50)).isEmpty();
  }

  private List<Tier> buildContiguousTiers(int count) {
    List<Tier> tiers = new ArrayList<>();
    int rangeSize = 100 / count;
    int minScore = 0;
    for (int i = 0; i < count; i++) {
      int maxScore = i == count - 1 ? 100 : minScore + rangeSize - 1;
      tiers.add(buildTier("Tier" + i, minScore, maxScore));
      minScore = maxScore + 1;
    }
    return tiers;
  }

  private TierGroupEntity buildDefaultTierGroupEntity() {
    return TierGroupEntity.builder()
        .identifier("default_tiers")
        .tiers(List.of(TierGroupEntity.Tier.builder().name("Bronze").minScore(0).maxScore(49).build(),
            TierGroupEntity.Tier.builder().name("Silver").minScore(50).maxScore(74).build(),
            TierGroupEntity.Tier.builder().name("Gold").minScore(75).maxScore(100).build()))
        .build();
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
