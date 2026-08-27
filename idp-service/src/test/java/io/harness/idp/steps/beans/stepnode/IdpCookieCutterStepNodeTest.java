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
import io.harness.idp.steps.beans.stepinfo.IdpCookieCutterStepInfo;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpCookieCutterStepNodeTest extends CategoryTest {
  @Mock IdpCookieCutterStepInfo mockStepInfo;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    IdpCookieCutterStepNode stepNode = new IdpCookieCutterStepNode();

    assertNotNull(stepNode);
    assertEquals(CIStepInfoType.COOKIECUTTER.getDisplayName(), stepNode.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetType() {
    IdpCookieCutterStepNode stepNode = new IdpCookieCutterStepNode();

    String type = stepNode.getType();

    assertNotNull(type);
    assertEquals(CIStepInfoType.COOKIECUTTER.getDisplayName(), type);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    IdpCookieCutterStepNode stepNode = new IdpCookieCutterStepNode();

    assertNull(stepNode.getStepSpecType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetAndGetIdpCookieCutterStepInfo() {
    IdpCookieCutterStepNode stepNode = new IdpCookieCutterStepNode();
    IdpCookieCutterStepInfo stepInfo = IdpCookieCutterStepInfo.builder().build();

    stepNode.setIdpCookieCutterStepInfo(stepInfo);

    assertEquals(stepInfo, stepNode.getIdpCookieCutterStepInfo());
    assertEquals(stepInfo, stepNode.getStepSpecType());
  }
}
