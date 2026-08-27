/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.repositories.CountAndPercentage;
import io.harness.spec.server.idp.v1.model.Check;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardResponse;
import io.harness.spec.server.idp.v1.model.ScorecardTierAnalytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardMapper {
  public Scorecard toDTO(
      ScorecardEntity scorecardEntity, Map<String, CheckEntity> checkEntityMap, String harnessAccount) {
    return toDTO(scorecardEntity, checkEntityMap, null, true, harnessAccount);
  }

  public Scorecard toDTO(ScorecardEntity scorecardEntity, Map<String, CheckEntity> checkEntityMap,
      CountAndPercentage countAndPercentage, boolean tierAnalyticsEnabled, String harnessAccount) {
    Scorecard scorecard = new Scorecard();
    scorecard.setName(scorecardEntity.getName());
    scorecard.setIdentifier(scorecardEntity.getIdentifier());
    scorecard.setDescription(scorecardEntity.getDescription());
    List<Check> checks = new ArrayList<>();
    List<String> checksMissing = new ArrayList<>();
    scorecardEntity.getChecks().forEach(scorecardCheck -> {
      String accountIdentifier = scorecardCheck.isCustom() ? harnessAccount : GLOBAL_ACCOUNT_ID;
      CheckEntity checkEntity = checkEntityMap.get(accountIdentifier + DOT_SEPARATOR + scorecardCheck.getIdentifier());
      if (checkEntity != null && !checkEntity.isDeleted()) {
        Check check = new Check();
        check.setName(checkEntity.getName());
        check.setIdentifier(checkEntity.getIdentifier());
        check.setDescription(checkEntity.getDescription());
        check.setExpression(checkEntity.getExpression());
        checks.add(check);
      } else {
        checksMissing.add(scorecardCheck.getIdentifier());
      }
    });
    scorecard.setChecks(checks);
    scorecard.setChecksMissing(checksMissing);
    scorecard.setPublished(scorecardEntity.isPublished());
    scorecard.setPercentage(null);

    if (tierAnalyticsEnabled) {
      scorecard.setTierGroupIdentifier(scorecardEntity.getTierGroupIdentifier());
      if (scorecardEntity.getComponentCount() != null) {
        scorecard.setComponents(scorecardEntity.getComponentCount());
        scorecard.setTierAnalytics(toTierAnalytics(scorecardEntity));
      }
    } else {
      scorecard.setTierAnalytics(null);
      if (countAndPercentage != null) {
        scorecard.setComponents(countAndPercentage.getCount());
        scorecard.setPercentage((double) Math.round(countAndPercentage.getPercentage() * 100.0));
      }
    }
    return scorecard;
  }

  public List<ScorecardTierAnalytics> toTierAnalytics(ScorecardEntity scorecardEntity) {
    if (scorecardEntity.getComponentCount() == null || scorecardEntity.getTierComponentCounts() == null) {
      return List.of();
    }
    int componentCount = scorecardEntity.getComponentCount();
    return scorecardEntity.getTierComponentCounts()
        .stream()
        .sorted(Comparator.comparingInt(ScorecardEntity.TierComponentCount::getMinScore))
        .map(tierComponentCount -> {
          ScorecardTierAnalytics tierAnalytics = new ScorecardTierAnalytics();
          tierAnalytics.setTierName(tierComponentCount.getTierName());
          tierAnalytics.setMinScore(tierComponentCount.getMinScore());
          tierAnalytics.setMaxScore(tierComponentCount.getMaxScore());
          tierAnalytics.setTierColour(tierComponentCount.getTierColour());
          tierAnalytics.setComponentCount(tierComponentCount.getComponentCount());
          tierAnalytics.setPercentage(componentCount == 0
                  ? 0.0
                  : (double) Math.round(tierComponentCount.getComponentCount() * 100.0 / componentCount));
          return tierAnalytics;
        })
        .toList();
  }

  public List<ScorecardResponse> toResponseList(List<Scorecard> scorecards) {
    List<ScorecardResponse> response = new ArrayList<>();
    scorecards.forEach(scorecard -> response.add(new ScorecardResponse().scorecard(scorecard)));
    return response;
  }
}
