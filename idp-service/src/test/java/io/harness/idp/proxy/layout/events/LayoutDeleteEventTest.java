/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.events;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class LayoutDeleteEventTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String DISPLAY_NAME = "Test Layout";

  private LayoutRequest layoutRequest;
  private LayoutDeleteEvent layoutDeleteEvent;

  @Before
  public void setUp() {
    layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setDisplayName(DISPLAY_NAME);
    layoutRequest.setYaml("layout: test");

    layoutDeleteEvent = new LayoutDeleteEvent(layoutRequest, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLayoutDeleteEventCreation() {
    assertNotNull(layoutDeleteEvent);
    assertEquals(layoutRequest, layoutDeleteEvent.getOldLayout());
    assertEquals(ACCOUNT_IDENTIFIER, layoutDeleteEvent.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEventType() {
    String eventType = layoutDeleteEvent.getEventType();
    assertEquals(LayoutDeleteEvent.LAYOUT_DELETED, eventType);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    ResourceScope resourceScope = layoutDeleteEvent.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(true, resourceScope instanceof AccountScope);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertEquals(ACCOUNT_IDENTIFIER, accountScope.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource resource = layoutDeleteEvent.getResource();
    assertNotNull(resource);
    assertEquals(ACCOUNT_IDENTIFIER + LAYOUT_NAME + LAYOUT_TYPE, resource.getIdentifier());
    assertNotNull(resource.getLabels());
    assertEquals(DISPLAY_NAME + " Layout", resource.getLabels().get("resourceName"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    LayoutDeleteEvent event = new LayoutDeleteEvent();
    assertNotNull(event);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLayoutDeleteEventWithDifferentLayouts() {
    LayoutRequest customLayout = new LayoutRequest();
    customLayout.setName("custom-layout");
    customLayout.setType("service");
    customLayout.setDisplayName("Custom Layout");
    customLayout.setYaml("custom: yaml");

    LayoutDeleteEvent customEvent = new LayoutDeleteEvent(customLayout, "custom-account");

    assertEquals(customLayout, customEvent.getOldLayout());
    assertEquals("custom-account", customEvent.getAccountIdentifier());
    assertEquals(LayoutDeleteEvent.LAYOUT_DELETED, customEvent.getEventType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResourceWithNullDisplayName() {
    LayoutRequest requestWithNullDisplay = new LayoutRequest();
    requestWithNullDisplay.setName(LAYOUT_NAME);
    requestWithNullDisplay.setType(LAYOUT_TYPE);
    requestWithNullDisplay.setDisplayName(null);

    LayoutDeleteEvent event = new LayoutDeleteEvent(requestWithNullDisplay, ACCOUNT_IDENTIFIER);
    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(ACCOUNT_IDENTIFIER + LAYOUT_NAME + LAYOUT_TYPE, resource.getIdentifier());
  }
}
