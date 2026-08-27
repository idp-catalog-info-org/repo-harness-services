/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.utils;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetLiteEngineStep() {
    Set<String> liteEngineSteps = IDPCreatorUtils.getLiteEngineStep();

    assertNotNull(liteEngineSteps);
    assertFalse(liteEngineSteps.isEmpty());
    assertTrue(liteEngineSteps.contains("liteEngineTask"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetLiteEngineStepSize() {
    Set<String> liteEngineSteps = IDPCreatorUtils.getLiteEngineStep();

    assertEquals(1, liteEngineSteps.size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetLiteEngineStepReturnsConsistentResult() {
    Set<String> firstCall = IDPCreatorUtils.getLiteEngineStep();
    Set<String> secondCall = IDPCreatorUtils.getLiteEngineStep();

    assertEquals(firstCall, secondCall);
  }
}
