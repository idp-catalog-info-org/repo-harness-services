/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.mappers;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.entities.PluginConfigEnvVariablesEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ConfigEnvVariablesMapperTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_CONFIG_ID = "test-config-id";
  private static final String TEST_CONFIG_NAME = "Test Config";
  private static final String TEST_ENV_NAME_1 = "TEST_ENV_1";
  private static final String TEST_ENV_NAME_2 = "TEST_ENV_2";
  private static final String TEST_SECRET_ID = "test-secret-id";

  private AppConfig appConfig;

  @Before
  public void setUp() {
    appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigName(TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntitiesForEnvVariables_SingleVariable() {
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.setEnvName(TEST_ENV_NAME_1);
    envVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);

    List<BackstageEnvSecretVariable> envVariables = Collections.singletonList(envVariable);

    List<PluginConfigEnvVariablesEntity> entities =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, envVariables, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(entities);
    assertEquals(1, entities.size());
    PluginConfigEnvVariablesEntity entity = entities.get(0);
    assertEquals(TEST_ENV_NAME_1, entity.getEnvName());
    assertEquals(TEST_CONFIG_NAME, entity.getPluginName());
    assertEquals(TEST_CONFIG_ID, entity.getPluginId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertNotNull(entity.getEnabledDisabledAt());
    assertNotNull(entity.getLastModifiedAt());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntitiesForEnvVariables_MultipleVariables() {
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.setEnvName(TEST_ENV_NAME_1);

    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.setEnvName(TEST_ENV_NAME_2);

    List<BackstageEnvSecretVariable> envVariables = Arrays.asList(envVariable1, envVariable2);

    List<PluginConfigEnvVariablesEntity> entities =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, envVariables, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(entities);
    assertEquals(2, entities.size());
    assertEquals(TEST_ENV_NAME_1, entities.get(0).getEnvName());
    assertEquals(TEST_ENV_NAME_2, entities.get(1).getEnvName());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntitiesForEnvVariables_EmptyList() {
    List<BackstageEnvSecretVariable> envVariables = Collections.emptyList();

    List<PluginConfigEnvVariablesEntity> entities =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, envVariables, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(entities);
    assertTrue(entities.isEmpty());
  }
}
