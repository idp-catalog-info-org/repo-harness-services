/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.repositories;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardStatsRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ENTITY_IDENTIFIER = "entity";
  private static final String SCORECARD_IDENTIFIER = "scorecard";
  private static final String TIER_NAME = "Gold";
  private static final String TIER_GROUP_IDENTIFIER = "default";
  private static final String TIER_DESCRIPTION = "Top tier";
  private static final String TIER_ICON = "medal";
  private static final String TIER_COLOUR = "#FFD700";

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private ScorecardStatsRepositoryCustomImpl repository;

  private CatalogEntity catalogEntity;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    catalogEntity = mock(CatalogEntity.class);
    when(catalogEntity.getScope()).thenReturn("ACCOUNT");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldCopyTierSnapshot() {
    when(mongoTemplate.findOne(any(Query.class), eq(ScorecardStatsEntity.class))).thenReturn(null);

    ScorecardStatsEntity result = repository.findOneOrConstructStats(scoreEntity(), catalogEntity);

    assertThat(result.getTierName()).isEqualTo(TIER_NAME);
    assertThat(result.getTierGroupIdentifier()).isEqualTo(TIER_GROUP_IDENTIFIER);
    assertThat(result.getTierDescription()).isEqualTo(TIER_DESCRIPTION);
    assertThat(result.getTierIcon()).isEqualTo(TIER_ICON);
    assertThat(result.getTierColour()).isEqualTo(TIER_COLOUR);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void shouldComputeLegacyCountAndPercentage() {
    CountAndPercentage expected = CountAndPercentage.builder().count(5).percentage(0.8).build();
    AggregationResults<CountAndPercentage> aggregationResults = mock(AggregationResults.class);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(expected));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("scorecardStats"), eq(CountAndPercentage.class)))
        .thenReturn(aggregationResults);

    CountAndPercentage result =
        repository.computeScoresPercentageByScorecard(ACCOUNT_IDENTIFIER, SCORECARD_IDENTIFIER, 123L);

    assertThat(result).isEqualTo(expected);
  }

  private ScoreEntity scoreEntity() {
    return ScoreEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .entityIdentifier(ENTITY_IDENTIFIER)
        .scorecardIdentifier(SCORECARD_IDENTIFIER)
        .tierName(TIER_NAME)
        .tierGroupIdentifier(TIER_GROUP_IDENTIFIER)
        .tierDescription(TIER_DESCRIPTION)
        .tierIcon(TIER_ICON)
        .tierColour(TIER_COLOUR)
        .build();
  }
}
