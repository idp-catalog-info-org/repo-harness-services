/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.scores.service.AsyncScoreComputationService;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ScoresApi;
import io.harness.spec.server.idp.v1.model.EntityScores;
import io.harness.spec.server.idp.v1.model.EntityScoresResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfoResponse;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateIdentifiers;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponse;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponseV2;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardScoreResponse;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
public class ScoreApiImpl implements ScoresApi {
  private ScoreService scoreService;
  private AsyncScoreComputationService asyncScoreComputationService;

  @Override
  @Timed
  @ResponseMetered
  public Response getAllScorecardSummary(String entityIdentifier, @AccountIdentifier String harnessAccount) {
    try {
      List<ScorecardSummaryInfo> scorecardSummaryInfoList =
          scoreService.getScoresSummaryForAnEntityV2(harnessAccount, entityIdentifier);
      ScorecardSummaryResponse scorecardSummaryResponse = new ScorecardSummaryResponse();
      scorecardSummaryResponse.setScorecardsSummary(scorecardSummaryInfoList);
      return Response.status(Response.Status.OK).entity(scorecardSummaryResponse).build();
    } catch (Exception e) {
      log.error("Error in getting score summary for scorecards details for account - {} and entity - {},  error = {}",
          harnessAccount, entityIdentifier, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response scorecardRecalibrate(
      @Valid ScorecardRecalibrateRequest scorecardRecalibrateRequest, @AccountIdentifier String harnessAccount) {
    try {
      ScorecardSummaryInfo scorecardSummaryInfo = scoreService.getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
          harnessAccount, scorecardRecalibrateRequest.getIdentifiers().getEntityIdentifier(),
          scorecardRecalibrateRequest.getIdentifiers().getScorecardIdentifier());
      ScorecardRecalibrateResponse scorecardRecalibrateResponse = new ScorecardRecalibrateResponse();
      scorecardRecalibrateResponse.setRecalibratedScores(scorecardSummaryInfo);
      return Response.status(Response.Status.OK).entity(scorecardRecalibrateResponse).build();
    } catch (Exception e) {
      log.error("Error in getting recalibrated score for scorecards details for account - {},  entity - {} and "
              + "scorecard - {}, error = {}",
          harnessAccount, scorecardRecalibrateRequest.getIdentifiers().getEntityIdentifier(),
          scorecardRecalibrateRequest.getIdentifiers().getScorecardIdentifier(), e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getScorecardsGraphsScoreSummary(
      String entityIdentifier, String harnessAccount, @AccountIdentifier String scorecardIdentifier) {
    try {
      List<ScorecardGraphSummaryInfo> scorecardGraphSummaryInfos =
          scoreService.getScoresGraphSummaryForAnEntityAndScorecard(
              harnessAccount, entityIdentifier, scorecardIdentifier);
      ScorecardGraphSummaryInfoResponse scorecardGraphSummaryInfoResponse = new ScorecardGraphSummaryInfoResponse();
      scorecardGraphSummaryInfoResponse.setScorecardGraphSummary(scorecardGraphSummaryInfos);
      return Response.status(Response.Status.OK).entity(scorecardGraphSummaryInfoResponse).build();
    } catch (Exception e) {
      log.error("Error in getting score graph summary for scorecards details for account - {},  entity - {} and "
              + "scorecard - {},  error = {}",
          harnessAccount, entityIdentifier, scorecardIdentifier, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getScorecardsScoresOverview(String entityIdentifier, @AccountIdentifier String harnessAccount) {
    try {
      List<ScorecardScore> scorecardScores =
          scoreService.getScorecardScoreOverviewForAnEntity(harnessAccount, entityIdentifier);
      ScorecardScoreResponse scorecardScoreResponse = new ScorecardScoreResponse();
      scorecardScoreResponse.setScorecardScores(scorecardScores);
      if (!scorecardScores.isEmpty()) {
        double averageScore =
            scorecardScores.stream().filter(Objects::nonNull).mapToInt(ScorecardScore::getScore).average().orElse(0);
        scorecardScoreResponse.setOverallScore((int) Math.round(averageScore));
      }
      return Response.status(Response.Status.OK).entity(scorecardScoreResponse).build();
    } catch (Exception e) {
      log.error("Error in getting scores overview for scorecards details for account - {},  entity - {} ,  error = {}",
          harnessAccount, entityIdentifier, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @Timed
  @ResponseMetered
  public Response getScoresRecalibrateStatus(
      @Valid ScorecardRecalibrateRequest body, @AccountIdentifier String harnessAccount) {
    ScorecardRecalibrateIdentifiers identifiers = body.getIdentifiers();
    String scorecardIdentifier = identifiers.getScorecardIdentifier();
    String entityIdentifier = identifiers.getEntityIdentifier();
    try {
      ScorecardRecalibrateInfo recalibrateInfo =
          asyncScoreComputationService.getRecalibrateInfo(harnessAccount, scorecardIdentifier, entityIdentifier);
      ScorecardRecalibrateResponseV2 responseV2 = new ScorecardRecalibrateResponseV2();
      responseV2.setInfo(recalibrateInfo);
      return Response.status(Response.Status.OK).entity(responseV2).build();
    } catch (Exception e) {
      log.error("Error in getting recalibrate status for account - {}, scorecard - {}, entity - {},  error = {}",
          harnessAccount, scorecardIdentifier, entityIdentifier, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Deprecated(forRemoval = true)
  @Override
  @Timed
  @ResponseMetered
  public Response getAggregatedScores(@Valid ScorecardFilter body, @AccountIdentifier String harnessAccount) {
    try {
      List<EntityScores> entityScores = scoreService.getEntityScores(harnessAccount, body);
      List<EntityScoresResponse> entityScoresResponse = new ArrayList<>();
      entityScores.forEach(entityScore -> entityScoresResponse.add(new EntityScoresResponse().entity(entityScore)));
      return Response.status(Response.Status.OK).entity(entityScoresResponse).build();
    } catch (Exception e) {
      log.error("Error in getting entity scores for account - {},  error = {}", harnessAccount, e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}