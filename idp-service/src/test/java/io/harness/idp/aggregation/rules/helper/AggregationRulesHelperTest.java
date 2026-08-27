/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.helper;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.rule.Owner;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesHelperTest extends CategoryTest {
  AggregationRulesHelper helper;
  CatalogServiceHelper catalogServiceHelper;

  @Before
  public void setup() throws IllegalAccessException {
    helper = new AggregationRulesHelper();
    catalogServiceHelper = Mockito.mock(CatalogServiceHelper.class);
    FieldUtils.writeField(helper, "catalogServiceHelper", catalogServiceHelper, true);
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = HARJAS)
  public void testGetDefaultScopeSelector() {
    Mockito.when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    assertEquals(1, helper.getDefaultScopeSelector().size());
    assertEquals("account.*", helper.getDefaultScopeSelector().get(0));
  }
}
