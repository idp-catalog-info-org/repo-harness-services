/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.tiergroups.service.TierGroupScorecardUsageChecker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.IDP)
public class TierGroupScorecardUsageCheckerImpl implements TierGroupScorecardUsageChecker {
  private final ScorecardRepository scorecardRepository;

  @Inject
  public TierGroupScorecardUsageCheckerImpl(ScorecardRepository scorecardRepository) {
    this.scorecardRepository = scorecardRepository;
  }

  @Override
  public boolean isReferencedByScorecard(String accountIdentifier, String tierGroupIdentifier) {
    return !scorecardRepository.findByAccountIdentifierAndTierGroupIdentifier(accountIdentifier, tierGroupIdentifier)
                .isEmpty();
  }
}
