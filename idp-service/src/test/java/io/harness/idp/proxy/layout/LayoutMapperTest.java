/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.idp.proxy.layout.mappers.LayoutMapper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class LayoutMapperTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String LAYOUT_ID = "layout-1";
  private static final String YAML = "layout: test";
  private static final String DEFAULT_YAML = "default: yaml";
  private static final String DISPLAY_NAME = "Test Layout";
  private static final String ENTITY_KIND = "Component";
  private static final String ENTITY_TYPE = "service";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTOWithAllFields() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setId(LAYOUT_ID);
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);
    layoutRequest.setDefaultYaml(DEFAULT_YAML);
    layoutRequest.setDisplayName(DISPLAY_NAME);
    layoutRequest.setEntityKind(ENTITY_KIND);
    layoutRequest.setEntityType(ENTITY_TYPE);
    layoutRequest.setHarnessManaged(false);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(layoutRequest, ACCOUNT_IDENTIFIER);

    assertNotNull(layoutEntity);
    assertEquals(LAYOUT_ID, layoutEntity.getIdentifier());
    assertEquals(LAYOUT_NAME, layoutEntity.getName());
    assertEquals(LAYOUT_TYPE, layoutEntity.getType());
    assertEquals(YAML, layoutEntity.getYaml());
    assertEquals(DEFAULT_YAML, layoutEntity.getDefaultYaml());
    assertEquals(DISPLAY_NAME, layoutEntity.getDisplayName());
    assertEquals(ENTITY_KIND, layoutEntity.getEntityKind());
    assertEquals(ENTITY_TYPE, layoutEntity.getEntityType());
    assertEquals(ACCOUNT_IDENTIFIER, layoutEntity.getAccountIdentifier());
    assertFalse(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTOWithMinimalFields() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(layoutRequest, ACCOUNT_IDENTIFIER);

    assertNotNull(layoutEntity);
    assertEquals(LAYOUT_NAME, layoutEntity.getName());
    assertEquals(LAYOUT_TYPE, layoutEntity.getType());
    assertEquals(YAML, layoutEntity.getYaml());
    assertEquals(ACCOUNT_IDENTIFIER, layoutEntity.getAccountIdentifier());
    assertTrue(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTODefaultHarnessManagedTrue() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(layoutRequest, ACCOUNT_IDENTIFIER);

    assertTrue(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTOHarnessManagedExplicitlySet() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setHarnessManaged(false);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(layoutRequest, ACCOUNT_IDENTIFIER);

    assertFalse(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testToDTOWithAllFields() {
    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .identifier(LAYOUT_ID)
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .yaml(YAML)
                                    .defaultYaml(DEFAULT_YAML)
                                    .displayName(DISPLAY_NAME)
                                    .entityKind(ENTITY_KIND)
                                    .entityType(ENTITY_TYPE)
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .harnessManaged(true)
                                    .build();

    LayoutRequest layoutRequest = LayoutMapper.toDTO(layoutEntity);

    assertNotNull(layoutRequest);
    assertEquals(LAYOUT_ID, layoutRequest.getId());
    assertEquals(LAYOUT_NAME, layoutRequest.getName());
    assertEquals(LAYOUT_TYPE, layoutRequest.getType());
    assertEquals(YAML, layoutRequest.getYaml());
    assertEquals(DEFAULT_YAML, layoutRequest.getDefaultYaml());
    assertEquals(DISPLAY_NAME, layoutRequest.getDisplayName());
    assertTrue(layoutRequest.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testToDTOWithMinimalFields() {
    LayoutEntity layoutEntity = LayoutEntity.builder()
                                    .name(LAYOUT_NAME)
                                    .type(LAYOUT_TYPE)
                                    .yaml(YAML)
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .harnessManaged(false)
                                    .build();

    LayoutRequest layoutRequest = LayoutMapper.toDTO(layoutEntity);

    assertNotNull(layoutRequest);
    assertEquals(LAYOUT_NAME, layoutRequest.getName());
    assertEquals(LAYOUT_TYPE, layoutRequest.getType());
    assertEquals(YAML, layoutRequest.getYaml());
    assertFalse(layoutRequest.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRoundTripConversion() {
    LayoutRequest originalRequest = new LayoutRequest();
    originalRequest.setId(LAYOUT_ID);
    originalRequest.setName(LAYOUT_NAME);
    originalRequest.setType(LAYOUT_TYPE);
    originalRequest.setYaml(YAML);
    originalRequest.setDefaultYaml(DEFAULT_YAML);
    originalRequest.setDisplayName(DISPLAY_NAME);
    originalRequest.setEntityKind(ENTITY_KIND);
    originalRequest.setEntityType(ENTITY_TYPE);
    originalRequest.setHarnessManaged(true);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(originalRequest, ACCOUNT_IDENTIFIER);
    LayoutRequest convertedRequest = LayoutMapper.toDTO(layoutEntity);

    assertEquals(originalRequest.getId(), convertedRequest.getId());
    assertEquals(originalRequest.getName(), convertedRequest.getName());
    assertEquals(originalRequest.getType(), convertedRequest.getType());
    assertEquals(originalRequest.getYaml(), convertedRequest.getYaml());
    assertEquals(originalRequest.getDefaultYaml(), convertedRequest.getDefaultYaml());
    assertEquals(originalRequest.getDisplayName(), convertedRequest.getDisplayName());
    assertEquals(originalRequest.isHarnessManaged(), convertedRequest.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTOWithNullHarnessManaged() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);

    LayoutEntity layoutEntity = LayoutMapper.fromDTO(layoutRequest, ACCOUNT_IDENTIFIER);

    assertNotNull(layoutEntity);
    assertTrue(layoutEntity.isHarnessManaged());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromDTOWithDifferentAccounts() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);

    LayoutEntity entity1 = LayoutMapper.fromDTO(layoutRequest, "account-1");
    LayoutEntity entity2 = LayoutMapper.fromDTO(layoutRequest, "account-2");

    assertEquals("account-1", entity1.getAccountIdentifier());
    assertEquals("account-2", entity2.getAccountIdentifier());
  }
}
