/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.enums;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ExportTypeTest extends CategoryTest {
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportType_AllValues() {
    ExportType[] values = ExportType.values();

    assertNotNull(values);
    assertEquals(4, values.length);
    assertEquals(ExportType.CARD, values[0]);
    assertEquals(ExportType.TAB_CONTENT, values[1]);
    assertEquals(ExportType.PAGE, values[2]);
    assertEquals(ExportType.CONDITIONAL, values[3]);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportType_ValueOf() {
    assertEquals(ExportType.CARD, ExportType.valueOf("CARD"));
    assertEquals(ExportType.TAB_CONTENT, ExportType.valueOf("TAB_CONTENT"));
    assertEquals(ExportType.PAGE, ExportType.valueOf("PAGE"));
    assertEquals(ExportType.CONDITIONAL, ExportType.valueOf("CONDITIONAL"));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportType_ToString() {
    assertEquals("CARD", ExportType.CARD.toString());
    assertEquals("TAB_CONTENT", ExportType.TAB_CONTENT.toString());
    assertEquals("PAGE", ExportType.PAGE.toString());
    assertEquals("CONDITIONAL", ExportType.CONDITIONAL.toString());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportType_Name() {
    assertEquals("CARD", ExportType.CARD.name());
    assertEquals("TAB_CONTENT", ExportType.TAB_CONTENT.name());
    assertEquals("PAGE", ExportType.PAGE.name());
    assertEquals("CONDITIONAL", ExportType.CONDITIONAL.name());
  }
}
