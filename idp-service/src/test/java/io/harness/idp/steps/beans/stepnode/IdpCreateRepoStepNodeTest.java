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
import io.harness.idp.steps.beans.stepinfo.IdpCreateRepoStepInfo;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpCreateRepoStepNodeTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    IdpCreateRepoStepNode stepNode = new IdpCreateRepoStepNode();

    assertNotNull(stepNode);
    assertEquals(CIStepInfoType.CREATE_REPO.getDisplayName(), stepNode.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetType() {
    IdpCreateRepoStepNode stepNode = new IdpCreateRepoStepNode();

    String type = stepNode.getType();

    assertNotNull(type);
    assertEquals(CIStepInfoType.CREATE_REPO.getDisplayName(), type);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    IdpCreateRepoStepNode stepNode = new IdpCreateRepoStepNode();

    assertNull(stepNode.getStepSpecType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetAndGetIdpCreateRepoStepInfo() {
    IdpCreateRepoStepNode stepNode = new IdpCreateRepoStepNode();
    IdpCreateRepoStepInfo stepInfo = IdpCreateRepoStepInfo.builder().build();

    stepNode.setIdpCreateRepoStepInfo(stepInfo);

    assertEquals(stepInfo, stepNode.getIdpCreateRepoStepInfo());
    assertEquals(stepInfo, stepNode.getStepSpecType());
  }
}
