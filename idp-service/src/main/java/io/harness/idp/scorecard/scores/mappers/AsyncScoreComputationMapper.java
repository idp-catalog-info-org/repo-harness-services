/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.mappers;

import static io.harness.idp.common.CommonUtils.getUserFromEmbeddedUser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.User;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class AsyncScoreComputationMapper {
  public ScorecardRecalibrateInfo toDTO(AsyncScoreComputationEntity entity) {
    ScorecardRecalibrateInfo scorecardRecalibrateInfo = new ScorecardRecalibrateInfo();
    scorecardRecalibrateInfo.setStartTime(entity.getStartTime());
    User startedByUser = getUserFromEmbeddedUser(entity.getCreatedBy());
    scorecardRecalibrateInfo.setStartedBy(startedByUser);
    return scorecardRecalibrateInfo;
  }
}
