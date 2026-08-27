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
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class PluginsProxyInfoMapperTest extends CategoryTest {
  private static final String TEST_ID = "test-proxy-id";
  private static final String TEST_PLUGIN_ID = "test-plugin-id";
  private static final String TEST_HOST = "test-host.example.com";
  private static final String TEST_DELEGATE_SELECTOR = "test-selector";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDto_AllFieldsMapped() {
    List<String> delegateSelectors = Collections.singletonList(TEST_DELEGATE_SELECTOR);

    PluginsProxyInfoEntity entity = PluginsProxyInfoEntity.builder()
                                        .id(TEST_ID)
                                        .pluginId(TEST_PLUGIN_ID)
                                        .host(TEST_HOST)
                                        .proxy(true)
                                        .delegateSelectors(delegateSelectors)
                                        .build();

    ProxyHostDetail dto = PluginsProxyInfoMapper.toDto(entity);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getIdentifier());
    assertEquals(TEST_PLUGIN_ID, dto.getPluginId());
    assertEquals(TEST_HOST, dto.getHost());
    assertTrue(dto.isProxy());
    assertNotNull(dto.getSelectors());
    assertEquals(1, dto.getSelectors().size());
    assertEquals(TEST_DELEGATE_SELECTOR, dto.getSelectors().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDto_WithMultipleSelectors() {
    List<String> delegateSelectors = Arrays.asList("selector-1", "selector-2", "selector-3");

    PluginsProxyInfoEntity entity = PluginsProxyInfoEntity.builder()
                                        .id(TEST_ID)
                                        .pluginId(TEST_PLUGIN_ID)
                                        .host(TEST_HOST)
                                        .proxy(false)
                                        .delegateSelectors(delegateSelectors)
                                        .build();

    ProxyHostDetail dto = PluginsProxyInfoMapper.toDto(entity);

    assertNotNull(dto);
    assertFalse(dto.isProxy());
    assertEquals(3, dto.getSelectors().size());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDto_WithNullSelectors() {
    PluginsProxyInfoEntity entity =
        PluginsProxyInfoEntity.builder().id(TEST_ID).pluginId(TEST_PLUGIN_ID).host(TEST_HOST).proxy(true).build();

    ProxyHostDetail dto = PluginsProxyInfoMapper.toDto(entity);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getIdentifier());
    assertEquals(TEST_HOST, dto.getHost());
  }
}
