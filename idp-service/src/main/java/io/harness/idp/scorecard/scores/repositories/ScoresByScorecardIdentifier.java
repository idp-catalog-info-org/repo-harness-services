/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@OwnedBy(HarnessTeam.IDP)
public class ScoresByScorecardIdentifier {
  private String id;
  private String accountIdentifier;
  private String scorecardIdentifier;
  private String date;
  private List<Integer> scores;
  private long lastComputedTimestamp;
}
