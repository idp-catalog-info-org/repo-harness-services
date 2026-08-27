/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.events;

import static io.harness.rule.OwnerRule.NISARG;

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
public class LayoutCreateEventTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String DISPLAY_NAME = "Test Layout";

  private LayoutRequest layoutRequest;
  private LayoutCreateEvent layoutCreateEvent;

  @Before
  public void setUp() {
    layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setDisplayName(DISPLAY_NAME);
    layoutRequest.setYaml("layout: test");

    layoutCreateEvent = new LayoutCreateEvent(layoutRequest, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutCreateEventCreation() {
    assertNotNull(layoutCreateEvent);
    assertEquals(layoutRequest, layoutCreateEvent.getNewLayout());
    assertEquals(ACCOUNT_IDENTIFIER, layoutCreateEvent.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetEventType() {
    String eventType = layoutCreateEvent.getEventType();
    assertEquals(LayoutCreateEvent.LAYOUT_CREATED, eventType);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    ResourceScope resourceScope = layoutCreateEvent.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(true, resourceScope instanceof AccountScope);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertEquals(ACCOUNT_IDENTIFIER, accountScope.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource resource = layoutCreateEvent.getResource();
    assertNotNull(resource);
    assertEquals(ACCOUNT_IDENTIFIER + LAYOUT_NAME + LAYOUT_TYPE, resource.getIdentifier());
    assertNotNull(resource.getLabels());
    assertEquals(DISPLAY_NAME + " Layout", resource.getLabels().get("resourceName"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    LayoutCreateEvent event = new LayoutCreateEvent();
    assertNotNull(event);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutCreateEventWithDifferentLayouts() {
    LayoutRequest customLayout = new LayoutRequest();
    customLayout.setName("custom-layout");
    customLayout.setType("service");
    customLayout.setDisplayName("Custom Layout");

    LayoutCreateEvent customEvent = new LayoutCreateEvent(customLayout, "custom-account");

    assertEquals(customLayout, customEvent.getNewLayout());
    assertEquals("custom-account", customEvent.getAccountIdentifier());
    assertEquals(LayoutCreateEvent.LAYOUT_CREATED, customEvent.getEventType());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResourceWithNullDisplayName() {
    LayoutRequest requestWithNullDisplay = new LayoutRequest();
    requestWithNullDisplay.setName(LAYOUT_NAME);
    requestWithNullDisplay.setType(LAYOUT_TYPE);
    requestWithNullDisplay.setDisplayName(null);

    LayoutCreateEvent event = new LayoutCreateEvent(requestWithNullDisplay, ACCOUNT_IDENTIFIER);
    Resource resource = event.getResource();

    assertNotNull(resource);
  }
}
