/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.beans;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.enums.ExportType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ExportsDataTest extends CategoryTest {
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportsData_BuilderAndGetters() {
    List<String> defaultEntityTypes = Arrays.asList("Component", "API");

    ExportsData.ExportDetails exportDetail1 = ExportsData.ExportDetails.builder()
                                                  .type(ExportType.CARD)
                                                  .name("Overview")
                                                  .defaultRoute("/overview")
                                                  .addByDefault("true")
                                                  .build();

    ExportsData.ExportDetails exportDetail2 = ExportsData.ExportDetails.builder()
                                                  .type(ExportType.TAB_CONTENT)
                                                  .name("Details")
                                                  .defaultRoute("/details")
                                                  .addByDefault("false")
                                                  .build();

    List<ExportsData.ExportDetails> exportDetails = Arrays.asList(exportDetail1, exportDetail2);

    ExportsData exportsData =
        ExportsData.builder().defaultEntityTypes(defaultEntityTypes).exportDetails(exportDetails).build();

    assertNotNull(exportsData);
    assertEquals(2, exportsData.getDefaultEntityTypes().size());
    assertEquals("Component", exportsData.getDefaultEntityTypes().get(0));
    assertEquals("API", exportsData.getDefaultEntityTypes().get(1));
    assertEquals(2, exportsData.getExportDetails().size());
    assertEquals(ExportType.CARD, exportsData.getExportDetails().get(0).getType());
    assertEquals("Overview", exportsData.getExportDetails().get(0).getName());
    assertEquals("/overview", exportsData.getExportDetails().get(0).getDefaultRoute());
    assertEquals("true", exportsData.getExportDetails().get(0).getAddByDefault());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportsData_NoArgsConstructor() {
    ExportsData exportsData = new ExportsData();
    assertNotNull(exportsData);
    assertNull(exportsData.getDefaultEntityTypes());
    assertNull(exportsData.getExportDetails());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportsData_AllArgsConstructor() {
    List<String> defaultEntityTypes = Arrays.asList("Component");
    List<ExportsData.ExportDetails> exportDetails =
        Arrays.asList(ExportsData.ExportDetails.builder().type(ExportType.PAGE).name("Page").build());

    ExportsData exportsData = new ExportsData(defaultEntityTypes, exportDetails);

    assertNotNull(exportsData);
    assertEquals(1, exportsData.getDefaultEntityTypes().size());
    assertEquals(1, exportsData.getExportDetails().size());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportDetails_WithLayoutSchemaSpecs() {
    Map<String, Object> layoutSchema = new HashMap<>();
    layoutSchema.put("width", "100%");
    layoutSchema.put("height", "400px");

    ExportsData.ExportDetails exportDetail =
        ExportsData.ExportDetails.builder().type(ExportType.CARD).name("Card").layoutSchemaSpecs(layoutSchema).build();

    assertNotNull(exportDetail);
    assertEquals(ExportType.CARD, exportDetail.getType());
    assertEquals("Card", exportDetail.getName());
    assertNotNull(exportDetail.getLayoutSchemaSpecs());
    assertEquals(layoutSchema, exportDetail.getLayoutSchemaSpecs());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportDetails_AllExportTypes() {
    ExportsData.ExportDetails cardExport =
        ExportsData.ExportDetails.builder().type(ExportType.CARD).name("Card Export").build();

    ExportsData.ExportDetails tabExport =
        ExportsData.ExportDetails.builder().type(ExportType.TAB_CONTENT).name("Tab Export").build();

    ExportsData.ExportDetails pageExport =
        ExportsData.ExportDetails.builder().type(ExportType.PAGE).name("Page Export").build();

    ExportsData.ExportDetails conditionalExport =
        ExportsData.ExportDetails.builder().type(ExportType.CONDITIONAL).name("Conditional Export").build();

    assertEquals(ExportType.CARD, cardExport.getType());
    assertEquals(ExportType.TAB_CONTENT, tabExport.getType());
    assertEquals(ExportType.PAGE, pageExport.getType());
    assertEquals(ExportType.CONDITIONAL, conditionalExport.getType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testExportDetails_Setters() {
    ExportsData.ExportDetails exportDetail = new ExportsData.ExportDetails();
    exportDetail.setType(ExportType.CARD);
    exportDetail.setName("Test Card");
    exportDetail.setDefaultRoute("/test");
    exportDetail.setAddByDefault("true");

    assertEquals(ExportType.CARD, exportDetail.getType());
    assertEquals("Test Card", exportDetail.getName());
    assertEquals("/test", exportDetail.getDefaultRoute());
    assertEquals("true", exportDetail.getAddByDefault());
  }
}
