/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupScorecardUsageCheckerImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String TIER_GROUP_ID = "compliance_tiers";

  @Mock private ScorecardRepository scorecardRepository;

  private TierGroupScorecardUsageCheckerImpl usageChecker;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    usageChecker = new TierGroupScorecardUsageCheckerImpl(scorecardRepository);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void isReferencedByScorecardReturnsTrueWhenScorecardsExist() {
    when(scorecardRepository.findByAccountIdentifierAndTierGroupIdentifier(ACCOUNT_ID, TIER_GROUP_ID))
        .thenReturn(List.of(ScorecardEntity.builder().identifier("scorecard1").build()));

    assertThat(usageChecker.isReferencedByScorecard(ACCOUNT_ID, TIER_GROUP_ID)).isTrue();
    verify(scorecardRepository).findByAccountIdentifierAndTierGroupIdentifier(ACCOUNT_ID, TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void isReferencedByScorecardReturnsFalseWhenNoScorecardsExist() {
    when(scorecardRepository.findByAccountIdentifierAndTierGroupIdentifier(ACCOUNT_ID, TIER_GROUP_ID))
        .thenReturn(Collections.emptyList());

    assertThat(usageChecker.isReferencedByScorecard(ACCOUNT_ID, TIER_GROUP_ID)).isFalse();
  }
}
