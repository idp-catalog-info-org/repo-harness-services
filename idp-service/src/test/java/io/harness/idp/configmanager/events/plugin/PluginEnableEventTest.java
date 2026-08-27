/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.plugin;

import static io.harness.audit.ResourceTypeConstants.IDP_PLUGINS;
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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class PluginEnableEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_PLUGIN_ID = "test-plugin-id";
  private static final String TEST_PLUGIN_NAME = "Test Plugin";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPluginEnableEvent_Construction() {
    PluginEnableEvent event = new PluginEnableEvent(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_PLUGIN_NAME);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertEquals(TEST_PLUGIN_ID, event.getPluginId());
    assertEquals(TEST_PLUGIN_NAME, event.getPluginName());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPluginEnableEvent_EventType() {
    PluginEnableEvent event = new PluginEnableEvent(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_PLUGIN_NAME);

    assertEquals(PluginEnableEvent.PLUGIN_ENABLED, event.getEventType());
    assertEquals("PluginEnabled", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPluginEnableEvent_ResourceScope() {
    PluginEnableEvent event = new PluginEnableEvent(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_PLUGIN_NAME);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPluginEnableEvent_Resource() {
    PluginEnableEvent event = new PluginEnableEvent(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_PLUGIN_NAME);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_PLUGIN_ID, resource.getIdentifier());
    assertEquals(IDP_PLUGINS, resource.getType());
    assertNotNull(resource.getLabels());
    assertEquals(TEST_PLUGIN_NAME + " Plugin", resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPluginEnableEvent_NoArgsConstructor() {
    PluginEnableEvent event = new PluginEnableEvent();

    assertNotNull(event);
  }
}
