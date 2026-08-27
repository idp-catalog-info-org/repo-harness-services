/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.entities;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.beans.ExportsData;
import io.harness.idp.configmanager.enums.ExportType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoEntityTest extends CategoryTest {
  private static final String TEST_IDENTIFIER = "test-plugin";
  private static final String TEST_ACCOUNT_ID = "test-account";
  private static final String TEST_NAME = "Test Plugin";
  private static final String TEST_DESCRIPTION = "Test Description";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetExportTypeCount_WithMatchingTypes() {
    ExportsData.ExportDetails cardExport1 =
        ExportsData.ExportDetails.builder().type(ExportType.CARD).name("Card 1").build();

    ExportsData.ExportDetails cardExport2 =
        ExportsData.ExportDetails.builder().type(ExportType.CARD).name("Card 2").build();

    ExportsData.ExportDetails tabExport =
        ExportsData.ExportDetails.builder().type(ExportType.TAB_CONTENT).name("Tab").build();

    ExportsData exportsData =
        ExportsData.builder().exportDetails(Arrays.asList(cardExport1, cardExport2, tabExport)).build();

    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setExports(exportsData);

    int cardCount = PluginInfoEntity.getExportTypeCount(entity, ExportType.CARD);
    int tabCount = PluginInfoEntity.getExportTypeCount(entity, ExportType.TAB_CONTENT);
    int pageCount = PluginInfoEntity.getExportTypeCount(entity, ExportType.PAGE);

    assertEquals(2, cardCount);
    assertEquals(1, tabCount);
    assertEquals(0, pageCount);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetExportTypeCount_WithEmptyExports() {
    ExportsData exportsData = ExportsData.builder().exportDetails(Collections.emptyList()).build();

    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setExports(exportsData);

    int count = PluginInfoEntity.getExportTypeCount(entity, ExportType.CARD);

    assertEquals(0, count);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_NoChanges() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity1.setName(TEST_NAME);
    entity1.setDescription(TEST_DESCRIPTION);

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity2.setName(TEST_NAME);
    entity2.setDescription(TEST_DESCRIPTION);

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertFalse(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_NameChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setName("Old Name");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setName("New Name");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_DescriptionChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setDescription("Old Description");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setDescription("New Description");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_CreatorChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setCreator("Creator 1");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setCreator("Creator 2");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasFieldChanged_BothNull() {
    boolean changed = PluginInfoEntity.hasFieldChanged(null, null);
    assertFalse(changed);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasFieldChanged_FirstNull() {
    boolean changed = PluginInfoEntity.hasFieldChanged(null, "value");
    assertTrue(changed);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasFieldChanged_SecondNull() {
    boolean changed = PluginInfoEntity.hasFieldChanged("value", null);
    assertTrue(changed);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasFieldChanged_SameValues() {
    boolean changed = PluginInfoEntity.hasFieldChanged("value", "value");
    assertFalse(changed);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasFieldChanged_DifferentValues() {
    boolean changed = PluginInfoEntity.hasFieldChanged("value1", "value2");
    assertTrue(changed);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_IconUrlChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setIconUrl("old-icon.png");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setIconUrl("new-icon.png");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_ConfigChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setConfig("old-config");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setConfig("new-config");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHasChanged_PackageNameChanged() {
    CustomPluginInfoEntity entity1 = CustomPluginInfoEntity.builder().build();
    entity1.setIdentifier(TEST_IDENTIFIER);
    entity1.setPackageName("package-v1");

    CustomPluginInfoEntity entity2 = CustomPluginInfoEntity.builder().build();
    entity2.setIdentifier(TEST_IDENTIFIER);
    entity2.setPackageName("package-v2");

    boolean hasChanged = PluginInfoEntity.hasChanged(entity1, entity2);

    assertTrue(hasChanged);
  }
}
