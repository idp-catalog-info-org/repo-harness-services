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
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class AppConfigMapperTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_CONFIG_ID = "test-config-id";
  private static final String TEST_CONFIG_NAME = "Test Config";
  private static final String TEST_CONFIGS = "test-configs-yaml";
  private static final long TEST_CREATED_AT = 1234567890L;
  private static final long TEST_LAST_MODIFIED_AT = 1234567900L;
  private static final long TEST_ENABLED_DISABLED_AT = 1234567910L;

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_AllFieldsMapped() {
    AppConfigEntity entity = AppConfigEntity.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .configId(TEST_CONFIG_ID)
                                 .configName(TEST_CONFIG_NAME)
                                 .configs(TEST_CONFIGS)
                                 .enabled(true)
                                 .enabledDisabledAt(TEST_ENABLED_DISABLED_AT)
                                 .createdAt(TEST_CREATED_AT)
                                 .lastModifiedAt(TEST_LAST_MODIFIED_AT)
                                 .configType(ConfigType.PLUGIN)
                                 .build();

    AppConfig dto = AppConfigMapper.toDTO(entity);

    assertNotNull(dto);
    assertEquals(TEST_CONFIG_ID, dto.getConfigId());
    assertEquals(TEST_CONFIG_NAME, dto.getConfigName());
    assertEquals(TEST_CONFIGS, dto.getConfigs());
    assertTrue(dto.isEnabled());
    assertEquals(TEST_ENABLED_DISABLED_AT, dto.getEnabledDisabledAt().longValue());
    assertEquals(TEST_CREATED_AT, dto.getCreated().longValue());
    assertEquals(TEST_LAST_MODIFIED_AT, dto.getUpdated().longValue());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_WithNullConfigs() {
    AppConfigEntity entity = AppConfigEntity.builder()
                                 .configId(TEST_CONFIG_ID)
                                 .configName(TEST_CONFIG_NAME)
                                 .configs(null)
                                 .enabled(false)
                                 .build();

    AppConfig dto = AppConfigMapper.toDTO(entity);

    assertNotNull(dto);
    assertEquals(TEST_CONFIG_ID, dto.getConfigId());
    assertEquals(TEST_CONFIG_NAME, dto.getConfigName());
    assertNull(dto.getConfigs());
    assertFalse(dto.isEnabled());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO_WithProxyDetails() {
    AppConfigEntity entity = AppConfigEntity.builder().configId(TEST_CONFIG_ID).configName(TEST_CONFIG_NAME).build();

    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setHost("test-host.example.com");
    proxyHostDetail.setProxy(true);
    List<ProxyHostDetail> proxyDetails = Arrays.asList(proxyHostDetail);

    AppConfig dto = AppConfigMapper.toDTO(entity, proxyDetails);

    assertNotNull(dto);
    assertEquals(TEST_CONFIG_ID, dto.getConfigId());
    assertNotNull(dto.getProxy());
    assertEquals(1, dto.getProxy().size());
    assertEquals("test-host.example.com", dto.getProxy().get(0).getHost());
    assertTrue(dto.getProxy().get(0).isProxy());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFromDTO() {
    AppConfig dto = new AppConfig();
    dto.setConfigId(TEST_CONFIG_ID);
    dto.setConfigName(TEST_CONFIG_NAME);
    dto.setConfigs(TEST_CONFIGS);
    dto.setEnabled(true);

    AppConfigEntity entity = AppConfigMapper.fromDTO(dto, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(entity);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CONFIG_ID, entity.getConfigId());
    assertEquals(TEST_CONFIG_NAME, entity.getConfigName());
    assertEquals(TEST_CONFIGS, entity.getConfigs());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFromDTO_WithNullConfigs() {
    AppConfig dto = new AppConfig();
    dto.setConfigId(TEST_CONFIG_ID);
    dto.setConfigName(TEST_CONFIG_NAME);
    dto.setConfigs(null);

    AppConfigEntity entity = AppConfigMapper.fromDTO(dto, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(entity);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CONFIG_ID, entity.getConfigId());
    assertEquals(TEST_CONFIG_NAME, entity.getConfigName());
    assertNull(entity.getConfigs());
  }
}
