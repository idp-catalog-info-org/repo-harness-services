/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.mappers;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginInfoResponse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoMapperTest extends CategoryTest {
  private static final String TEST_IDENTIFIER = "test-plugin";
  private static final String TEST_NAME = "Test Plugin";
  private static final String TEST_CREATOR = "Test Creator";
  private static final String TEST_ICON_URL = "https://example.com/icon.png";
  private static final String TEST_IMAGE_URL = "https://example.com/image.png";
  private static final String TEST_DOCUMENTATION = "https://docs.example.com";
  private static final String TEST_DESCRIPTION = "Test Description";
  private static final String TEST_CATEGORY = "CI/CD";
  private static final String TEST_SOURCE = "Harness";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_DefaultPlugin() {
    DefaultPluginInfoEntity entity = DefaultPluginInfoEntity.builder().core(true).build();
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setName(TEST_NAME);
    entity.setCreator(TEST_CREATOR);
    entity.setIconUrl(TEST_ICON_URL);
    entity.setImageUrl(TEST_IMAGE_URL);
    entity.setDocumentation(TEST_DOCUMENTATION);
    entity.setDescription(TEST_DESCRIPTION);
    entity.setCategory(TEST_CATEGORY);
    entity.setSource(TEST_SOURCE);
    entity.setType(PluginInfo.PluginTypeEnum.DEFAULT);

    PluginInfo dto = PluginInfoMapper.toDTO(entity, true);

    assertNotNull(dto);
    assertEquals(TEST_IDENTIFIER, dto.getId());
    assertEquals(TEST_NAME, dto.getName());
    assertEquals(TEST_CREATOR, dto.getCreatedBy());
    assertEquals(TEST_ICON_URL, dto.getIconUrl());
    assertEquals(TEST_IMAGE_URL, dto.getImageUrl());
    assertEquals(TEST_DOCUMENTATION, dto.getDocumentation());
    assertEquals(TEST_DESCRIPTION, dto.getDescription());
    assertEquals(TEST_CATEGORY, dto.getCategory());
    assertEquals(TEST_SOURCE, dto.getSource());
    assertTrue(dto.isCore());
    assertTrue(dto.isEnabled());
    assertEquals(PluginInfo.PluginTypeEnum.DEFAULT, dto.getPluginType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_CustomPlugin() {
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setName(TEST_NAME);
    entity.setCreator(TEST_CREATOR);
    entity.setDescription(TEST_DESCRIPTION);
    entity.setType(PluginInfo.PluginTypeEnum.CUSTOM);

    PluginInfo dto = PluginInfoMapper.toDTO(entity, false);

    assertNotNull(dto);
    assertEquals(TEST_IDENTIFIER, dto.getId());
    assertEquals(TEST_NAME, dto.getName());
    assertEquals(TEST_CREATOR, dto.getCreatedBy());
    assertEquals(TEST_DESCRIPTION, dto.getDescription());
    assertFalse(dto.isEnabled());
    assertEquals(PluginInfo.PluginTypeEnum.CUSTOM, dto.getPluginType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_WithNullType() {
    PluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setName(TEST_NAME);
    entity.setType(null);

    PluginInfo dto = PluginInfoMapper.toDTO(entity, false);

    assertNotNull(dto);
    assertEquals(PluginInfo.PluginTypeEnum.DEFAULT, dto.getPluginType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToResponseList() {
    PluginInfo plugin1 = new PluginInfo();
    plugin1.setId("plugin-1");
    plugin1.setName("Plugin 1");

    PluginInfo plugin2 = new PluginInfo();
    plugin2.setId("plugin-2");
    plugin2.setName("Plugin 2");

    List<PluginInfo> plugins = Arrays.asList(plugin1, plugin2);

    List<PluginInfoResponse> responses = PluginInfoMapper.toResponseList(plugins);

    assertNotNull(responses);
    assertEquals(2, responses.size());
    assertEquals("plugin-1", responses.get(0).getPlugin().getId());
    assertEquals("plugin-2", responses.get(1).getPlugin().getId());
  }
}
