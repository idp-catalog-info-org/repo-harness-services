/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.mapper;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AggregationRule;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesMapperTest extends CategoryTest {
  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testToDetailsAndBackWithNulls() {
    AggregationRuleEntity entity = AggregationRuleEntity.builder()
                                       .identifier("id1")
                                       .name("name1")
                                       .description(null)
                                       .fieldForAgg("f1")
                                       .aggFormula(null)
                                       .scopesToAggregateAt(null)
                                       .entitySelectionCriteria(null)
                                       .build();

    AggregationRuleDetailsResponse resp = AggregationRulesMapper.toDetailsResponseDTO(entity);
    assertNotNull(resp);
    assertNotNull(resp.getAggregationRule());
    assertEquals("id1", resp.getAggregationRule().getIdentifier());
    AggregationRuleDetails details = resp.getAggregationRule();
    AggregationRuleEntity back = AggregationRulesMapper.fromDTO("acc", details);
    assertNotNull(back);
    assertEquals("acc", back.getAccountIdentifier());
    assertEquals("id1", back.getIdentifier());
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testToResponseList() {
    AggregationRule rule = new AggregationRule().identifier("x").name("y");
    assertEquals(1, AggregationRulesMapper.toResponseList(java.util.List.of(rule)).size());
  }
}
