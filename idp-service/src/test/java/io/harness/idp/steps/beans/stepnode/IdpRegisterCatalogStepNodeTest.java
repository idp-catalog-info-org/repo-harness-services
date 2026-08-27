/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepnode;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.category.element.UnitTests;
import io.harness.idp.steps.beans.stepinfo.IdpRegisterCatalogStepInfo;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpRegisterCatalogStepNodeTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    IdpRegisterCatalogStepNode stepNode = new IdpRegisterCatalogStepNode();

    assertNotNull(stepNode);
    assertEquals(CIStepInfoType.REGISTER_CATALOG.getDisplayName(), stepNode.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetType() {
    IdpRegisterCatalogStepNode stepNode = new IdpRegisterCatalogStepNode();

    String type = stepNode.getType();

    assertNotNull(type);
    assertEquals(CIStepInfoType.REGISTER_CATALOG.getDisplayName(), type);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    IdpRegisterCatalogStepNode stepNode = new IdpRegisterCatalogStepNode();

    assertNull(stepNode.getStepSpecType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetAndGetIdpRegisterCatalogInfo() {
    IdpRegisterCatalogStepNode stepNode = new IdpRegisterCatalogStepNode();
    IdpRegisterCatalogStepInfo stepInfo = IdpRegisterCatalogStepInfo.builder().build();

    stepNode.setIdpRegisterCatalogInfo(stepInfo);

    assertEquals(stepInfo, stepNode.getIdpRegisterCatalogInfo());
    assertEquals(stepInfo, stepNode.getStepSpecType());
  }
}
