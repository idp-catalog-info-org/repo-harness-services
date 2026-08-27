/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

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
public class ConfigTypeTest extends CategoryTest {
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testConfigType_AllValues() {
    ConfigType[] values = ConfigType.values();

    assertNotNull(values);
    assertEquals(4, values.length);
    assertEquals(ConfigType.PLUGIN, values[0]);
    assertEquals(ConfigType.INTEGRATION, values[1]);
    assertEquals(ConfigType.AUTH, values[2]);
    assertEquals(ConfigType.BACKEND, values[3]);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testConfigType_ValueOf() {
    assertEquals(ConfigType.PLUGIN, ConfigType.valueOf("PLUGIN"));
    assertEquals(ConfigType.INTEGRATION, ConfigType.valueOf("INTEGRATION"));
    assertEquals(ConfigType.AUTH, ConfigType.valueOf("AUTH"));
    assertEquals(ConfigType.BACKEND, ConfigType.valueOf("BACKEND"));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testConfigType_ToString() {
    assertEquals("PLUGIN", ConfigType.PLUGIN.toString());
    assertEquals("INTEGRATION", ConfigType.INTEGRATION.toString());
    assertEquals("AUTH", ConfigType.AUTH.toString());
    assertEquals("BACKEND", ConfigType.BACKEND.toString());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testConfigType_Name() {
    assertEquals("PLUGIN", ConfigType.PLUGIN.name());
    assertEquals("INTEGRATION", ConfigType.INTEGRATION.name());
    assertEquals("AUTH", ConfigType.AUTH.name());
    assertEquals("BACKEND", ConfigType.BACKEND.name());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testConfigType_Ordinal() {
    assertEquals(0, ConfigType.PLUGIN.ordinal());
    assertEquals(1, ConfigType.INTEGRATION.ordinal());
    assertEquals(2, ConfigType.AUTH.ordinal());
    assertEquals(3, ConfigType.BACKEND.ordinal());
  }
}
