/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfcomparisonpair.resources;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SalesforceMetadataTypesApiImplTest extends CategoryTest {
  private SalesforceMetadataTypesApiImpl api;

  @Before
  public void setUp() {
    api = new SalesforceMetadataTypesApiImpl();
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetSalesforceMetadataTypes() {
    Response response = api.getSalesforceMetadataTypes();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(List.class);

    List<String> metadataTypes = (List<String>) response.getEntity();
    assertThat(metadataTypes).isNotEmpty();
    assertThat(metadataTypes).contains("ApexClass", "ApexTrigger", "CustomObject", "Flow");
  }
}
