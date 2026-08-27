/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.resources;

import static io.harness.rule.OwnerRule.DHRUVX;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesApiImplFeatureFlagTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";

  @Mock private AggregationRulesService service;
  @Mock private IdpCommonService idpCommonService;
  private AggregationRulesApiImpl api;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    api = new AggregationRulesApiImpl(service, idpCommonService);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAggregationRule_HarnessScopeEnabled_Succeeds() {
    doNothing().when(idpCommonService).harnessScopeCheck(ACCOUNT_ID);
    when(service.getAggregationRule(ACCOUNT_ID, "rule-1"))
        .thenReturn(
            new AggregationRuleDetailsResponse().aggregationRule(new AggregationRuleDetails().identifier("rule-1")));

    Response response = api.getAggregationRule("rule-1", ACCOUNT_ID);

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAggregationRule_HarnessScopeDisabled_Throws() {
    doThrow(new InvalidRequestException("Account not enabled for Harness Scope"))
        .when(idpCommonService)
        .harnessScopeCheck(ACCOUNT_ID);

    assertThatThrownBy(() -> api.getAggregationRule("rule-1", ACCOUNT_ID)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAggregationRules_HarnessScopeEnabled_PassesCheck() {
    doNothing().when(idpCommonService).harnessScopeCheck(ACCOUNT_ID);

    try {
      api.getAggregationRules(ACCOUNT_ID, 0, 10, null, null);
    } catch (Exception e) {
      // Downstream NPEs are expected from unmocked service dependencies
    }

    verify(idpCommonService).harnessScopeCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAggregationRules_HarnessScopeDisabled_Throws() {
    doThrow(new InvalidRequestException("Account not enabled for Harness Scope"))
        .when(idpCommonService)
        .harnessScopeCheck(ACCOUNT_ID);

    assertThatThrownBy(() -> api.getAggregationRules(ACCOUNT_ID, 0, 10, null, null))
        .isInstanceOf(InvalidRequestException.class);
  }
}
