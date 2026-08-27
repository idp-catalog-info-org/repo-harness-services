/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.resources;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsRequest;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesApiImplTest extends CategoryTest {
  @Mock private AggregationRulesService service;
  @Mock private IdpCommonService idpCommonService;
  private AggregationRulesApiImpl api;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    api = new AggregationRulesApiImpl(service, idpCommonService);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testGetAggregationRuleOk() {
    when(service.getAggregationRule("acc", "id"))
        .thenReturn(
            new AggregationRuleDetailsResponse().aggregationRule(new AggregationRuleDetails().identifier("id")));
    Response r = api.getAggregationRule("id", "acc");
    assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testGetAggregationRuleNotFound() {
    when(service.getAggregationRule("acc", "id")).thenThrow(new NotFoundException("nf"));
    Response r = api.getAggregationRule("id", "acc");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testUpdateAggregationRuleBadRequestOnMismatch() {
    AggregationRuleDetailsRequest req =
        new AggregationRuleDetailsRequest().aggregationRule(new AggregationRuleDetails().identifier("id-2"));
    Response r = api.updateAggregationRule("id-1", req, "acc");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
  }
}
