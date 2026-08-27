/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.appconfigs;

import static io.harness.audit.ResourceTypeConstants.IDP_APP_CONFIGS;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class AppConfigUpdateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_CONFIG_ID = "test-config-id";
  private static final String TEST_CONFIG_NAME = "Test Config";
  private static final String OLD_CONFIG_VALUE = "old-config-value";
  private static final String NEW_CONFIG_VALUE = "new-config-value";
  private AppConfig oldAppConfig;
  private AppConfig newAppConfig;

  @Before
  public void setUp() {
    oldAppConfig = new AppConfig();
    oldAppConfig.setConfigId(TEST_CONFIG_ID);
    oldAppConfig.setConfigName(TEST_CONFIG_NAME);
    oldAppConfig.setConfigs(OLD_CONFIG_VALUE);

    newAppConfig = new AppConfig();
    newAppConfig.setConfigId(TEST_CONFIG_ID);
    newAppConfig.setConfigName(TEST_CONFIG_NAME);
    newAppConfig.setConfigs(NEW_CONFIG_VALUE);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAppConfigUpdateEvent_Construction() {
    AppConfigUpdateEvent event = new AppConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newAppConfig, oldAppConfig);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertNotNull(event.getNewAppConfig());
    assertNotNull(event.getOldAppConfig());
    assertEquals(NEW_CONFIG_VALUE, event.getNewAppConfig().getConfigs());
    assertEquals(OLD_CONFIG_VALUE, event.getOldAppConfig().getConfigs());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAppConfigUpdateEvent_EventType() {
    AppConfigUpdateEvent event = new AppConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newAppConfig, oldAppConfig);

    assertEquals(AppConfigUpdateEvent.APP_CONFIG_UPDATED, event.getEventType());
    assertEquals("AppConfigUpdated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAppConfigUpdateEvent_ResourceScope() {
    AppConfigUpdateEvent event = new AppConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newAppConfig, oldAppConfig);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAppConfigUpdateEvent_Resource() {
    AppConfigUpdateEvent event = new AppConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newAppConfig, oldAppConfig);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_CONFIG_ID, resource.getIdentifier());
    assertEquals(IDP_APP_CONFIGS, resource.getType());
    assertNotNull(resource.getLabels());
    assertEquals(TEST_CONFIG_NAME + " Config", resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAppConfigUpdateEvent_NoArgsConstructor() {
    AppConfigUpdateEvent event = new AppConfigUpdateEvent();

    assertNotNull(event);
  }
}
