/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.resources;

import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.scores.service.ScoreComputerService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateIdentifiers;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponseV2;
import io.harness.spec.server.idp.v1.model.User;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ScoresV2ApiImplTest {
  @Mock private ScoreComputerService scoreComputerService;

  @InjectMocks private ScoresV2ApiImpl scoresV2ApiImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testScorecardRecalibrateV2Success() {
    ScorecardRecalibrateRequest recalibrateRequest = new ScorecardRecalibrateRequest();
    ScorecardRecalibrateIdentifiers identifiers = new ScorecardRecalibrateIdentifiers();
    identifiers.setScorecardIdentifier("test-scorecard-id");
    identifiers.setEntityIdentifier("test-entity-id");
    recalibrateRequest.setIdentifiers(identifiers);
    ScorecardRecalibrateInfo recalibrateInfo = new ScorecardRecalibrateInfo();
    recalibrateInfo.setStartTime(123456789L);
    User mockUser = new User();
    recalibrateInfo.setStartedBy(mockUser);
    when(scoreComputerService.computeScoresAsync(anyString(), anyString(), anyString())).thenReturn(recalibrateInfo);
    Response response = scoresV2ApiImpl.scorecardRecalibrateV2(recalibrateRequest, "test-account-id");
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    ScorecardRecalibrateResponseV2 responseBody = (ScorecardRecalibrateResponseV2) response.getEntity();
    assertEquals(recalibrateInfo.getStartTime(), responseBody.getInfo().getStartTime());
    assertEquals(recalibrateInfo.getStartedBy(), responseBody.getInfo().getStartedBy());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testScorecardRecalibrateV2Failure() {
    ScorecardRecalibrateRequest recalibrateRequest = new ScorecardRecalibrateRequest();
    ScorecardRecalibrateIdentifiers identifiers = new ScorecardRecalibrateIdentifiers();
    identifiers.setScorecardIdentifier("test-scorecard-id");
    identifiers.setEntityIdentifier("test-entity-id");
    recalibrateRequest.setIdentifiers(identifiers);
    when(scoreComputerService.computeScoresAsync(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("Test exception"));
    Response response = scoresV2ApiImpl.scorecardRecalibrateV2(recalibrateRequest, "test-account-id");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    ResponseMessage responseBody = (ResponseMessage) response.getEntity();
    assertEquals("Test exception", responseBody.getMessage());
  }
}
