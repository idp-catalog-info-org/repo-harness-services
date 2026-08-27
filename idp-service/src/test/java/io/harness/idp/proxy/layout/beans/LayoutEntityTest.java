/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.beans;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class LayoutEntityTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_ID = "layout-id";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String IDENTIFIER = "layout-identifier";
  private static final String YAML = "layout: test";
  private static final String DEFAULT_YAML = "default: yaml";
  private static final String DISPLAY_NAME = "Test Layout";
  private static final String DESCRIPTION = "Test Description";
  private static final String ENTITY_KIND = "Component";
  private static final String ENTITY_TYPE = "service";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityCreationWithBuilder() {
    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .id(LAYOUT_ID)
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .identifier(IDENTIFIER)
                                    .yaml(YAML)
                                    .defaultYaml(DEFAULT_YAML)
                                    .displayName(DISPLAY_NAME)
                                    .description(DESCRIPTION)
                                    .entityKind(ENTITY_KIND)
                                    .entityType(ENTITY_TYPE)
                                    .harnessManaged(true)
                                    .build();

    assertNotNull(layoutEntity);
    assertEquals(LAYOUT_ID, layoutEntity.getId());
    assertEquals(ACCOUNT_IDENTIFIER, layoutEntity.getAccountIdentifier());
    assertEquals(LAYOUT_NAME, layoutEntity.getName());
    assertEquals(LAYOUT_TYPE, layoutEntity.getType());
    assertEquals(IDENTIFIER, layoutEntity.getIdentifier());
    assertEquals(YAML, layoutEntity.getYaml());
    assertEquals(DEFAULT_YAML, layoutEntity.getDefaultYaml());
    assertEquals(DISPLAY_NAME, layoutEntity.getDisplayName());
    assertEquals(DESCRIPTION, layoutEntity.getDescription());
    assertEquals(ENTITY_KIND, layoutEntity.getEntityKind());
    assertEquals(ENTITY_TYPE, layoutEntity.getEntityType());
    assertTrue(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntitySettersAndGetters() {
    LayoutEntity layoutEntity = new LayoutEntity();

    layoutEntity.setId(LAYOUT_ID);
    layoutEntity.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    layoutEntity.setName(LAYOUT_NAME);
    layoutEntity.setType(LAYOUT_TYPE);
    layoutEntity.setIdentifier(IDENTIFIER);
    layoutEntity.setYaml(YAML);
    layoutEntity.setDefaultYaml(DEFAULT_YAML);
    layoutEntity.setDisplayName(DISPLAY_NAME);
    layoutEntity.setDescription(DESCRIPTION);
    layoutEntity.setEntityKind(ENTITY_KIND);
    layoutEntity.setEntityType(ENTITY_TYPE);
    layoutEntity.setHarnessManaged(false);

    assertEquals(LAYOUT_ID, layoutEntity.getId());
    assertEquals(ACCOUNT_IDENTIFIER, layoutEntity.getAccountIdentifier());
    assertEquals(LAYOUT_NAME, layoutEntity.getName());
    assertEquals(LAYOUT_TYPE, layoutEntity.getType());
    assertEquals(IDENTIFIER, layoutEntity.getIdentifier());
    assertEquals(YAML, layoutEntity.getYaml());
    assertEquals(DEFAULT_YAML, layoutEntity.getDefaultYaml());
    assertEquals(DISPLAY_NAME, layoutEntity.getDisplayName());
    assertEquals(DESCRIPTION, layoutEntity.getDescription());
    assertEquals(ENTITY_KIND, layoutEntity.getEntityKind());
    assertEquals(ENTITY_TYPE, layoutEntity.getEntityType());
    assertFalse(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityWithAuditFields() {
    EmbeddedUser createdBy = EmbeddedUser.builder().name("creator").email("creator@test.com").build();
    EmbeddedUser updatedBy = EmbeddedUser.builder().name("updater").email("updater@test.com").build();
    long createdAt = System.currentTimeMillis();
    long updatedAt = System.currentTimeMillis() + 1000;

    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .createdBy(createdBy)
                                    .lastUpdatedBy(updatedBy)
                                    .createdAt(createdAt)
                                    .lastUpdatedAt(updatedAt)
                                    .build();

    assertEquals(createdBy, layoutEntity.getCreatedBy());
    assertEquals(updatedBy, layoutEntity.getLastUpdatedBy());
    assertEquals(createdAt, layoutEntity.getCreatedAt());
    assertEquals(updatedAt, layoutEntity.getLastUpdatedAt());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityMinimalFields() {
    LayoutEntity layoutEntity =
        LayoutEntity.builder().accountIdentifier(ACCOUNT_IDENTIFIER).name(LAYOUT_NAME).type(LAYOUT_TYPE).build();

    assertNotNull(layoutEntity);
    assertEquals(ACCOUNT_IDENTIFIER, layoutEntity.getAccountIdentifier());
    assertEquals(LAYOUT_NAME, layoutEntity.getName());
    assertEquals(LAYOUT_TYPE, layoutEntity.getType());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityHarnessManagedFlag() {
    LayoutEntity harnessManagedLayout = LayoutEntity.builder()
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .name(LAYOUT_NAME)
                                            .type(LAYOUT_TYPE)
                                            .harnessManaged(true)
                                            .build();

    LayoutEntity userManagedLayout = LayoutEntity.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .name(LAYOUT_NAME)
                                         .type(LAYOUT_TYPE)
                                         .harnessManaged(false)
                                         .build();

    assertTrue(harnessManagedLayout.isHarnessManaged());
    assertFalse(userManagedLayout.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityWithDifferentTypes() {
    String[] types = {"overview", "service", "api", "system", "domain", "component"};

    for (String type : types) {
      LayoutEntity layoutEntity =
          LayoutEntity.builder().accountIdentifier(ACCOUNT_IDENTIFIER).name(LAYOUT_NAME).type(type).build();

      assertEquals(type, layoutEntity.getType());
    }
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityWithDifferentEntityKinds() {
    String[] kinds = {"Component", "API", "System", "Domain", "Resource", "Template"};

    for (String kind : kinds) {
      LayoutEntity layoutEntity = LayoutEntity.builder()
                                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                                      .name(LAYOUT_NAME)
                                      .type(LAYOUT_TYPE)
                                      .entityKind(kind)
                                      .build();

      assertEquals(kind, layoutEntity.getEntityKind());
    }
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityMongoIndexes() {
    assertNotNull(LayoutEntity.mongoIndexes());
    assertEquals(1, LayoutEntity.mongoIndexes().size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityUpdateFields() {
    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .yaml("old: yaml")
                                    .build();

    assertEquals("old: yaml", layoutEntity.getYaml());

    layoutEntity.setYaml("new: yaml");
    assertEquals("new: yaml", layoutEntity.getYaml());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutEntityWithComplexYaml() {
    String complexYaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: Layout\n"
        + "metadata:\n"
        + "  name: test\n"
        + "spec:\n"
        + "  type: overview\n"
        + "  content:\n"
        + "    - title: Overview\n"
        + "      cards:\n"
        + "        - component: EntityAboutCard\n";

    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .yaml(complexYaml)
                                    .build();

    assertEquals(complexYaml, layoutEntity.getYaml());
  }
}
