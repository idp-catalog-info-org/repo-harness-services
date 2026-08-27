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
public class LayoutUpdateEventTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String DISPLAY_NAME = "Test Layout";

  private LayoutRequest oldLayoutRequest;
  private LayoutRequest newLayoutRequest;
  private LayoutUpdateEvent layoutUpdateEvent;

  @Before
  public void setUp() {
    oldLayoutRequest = new LayoutRequest();
    oldLayoutRequest.setName(LAYOUT_NAME);
    oldLayoutRequest.setType(LAYOUT_TYPE);
    oldLayoutRequest.setDisplayName(DISPLAY_NAME);
    oldLayoutRequest.setYaml("layout: old");

    newLayoutRequest = new LayoutRequest();
    newLayoutRequest.setName(LAYOUT_NAME);
    newLayoutRequest.setType(LAYOUT_TYPE);
    newLayoutRequest.setDisplayName(DISPLAY_NAME);
    newLayoutRequest.setYaml("layout: new");

    layoutUpdateEvent = new LayoutUpdateEvent(newLayoutRequest, oldLayoutRequest, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutUpdateEventCreation() {
    assertNotNull(layoutUpdateEvent);
    assertEquals(newLayoutRequest, layoutUpdateEvent.getNewLayout());
    assertEquals(oldLayoutRequest, layoutUpdateEvent.getOldLayout());
    assertEquals(ACCOUNT_IDENTIFIER, layoutUpdateEvent.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetEventType() {
    String eventType = layoutUpdateEvent.getEventType();
    assertEquals(LayoutUpdateEvent.LAYOUT_UPDATED, eventType);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    ResourceScope resourceScope = layoutUpdateEvent.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(true, resourceScope instanceof AccountScope);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertEquals(ACCOUNT_IDENTIFIER, accountScope.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource resource = layoutUpdateEvent.getResource();
    assertNotNull(resource);
    assertEquals(ACCOUNT_IDENTIFIER + LAYOUT_NAME + LAYOUT_TYPE, resource.getIdentifier());
    assertNotNull(resource.getLabels());
    assertEquals(DISPLAY_NAME + " Layout", resource.getLabels().get("resourceName"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    LayoutUpdateEvent event = new LayoutUpdateEvent();
    assertNotNull(event);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLayoutUpdateEventWithDifferentLayouts() {
    LayoutRequest oldCustomLayout = new LayoutRequest();
    oldCustomLayout.setName("custom-layout");
    oldCustomLayout.setType("service");
    oldCustomLayout.setDisplayName("Old Custom Layout");
    oldCustomLayout.setYaml("old: yaml");

    LayoutRequest newCustomLayout = new LayoutRequest();
    newCustomLayout.setName("custom-layout");
    newCustomLayout.setType("service");
    newCustomLayout.setDisplayName("New Custom Layout");
    newCustomLayout.setYaml("new: yaml");

    LayoutUpdateEvent customEvent = new LayoutUpdateEvent(newCustomLayout, oldCustomLayout, "custom-account");

    assertEquals(newCustomLayout, customEvent.getNewLayout());
    assertEquals(oldCustomLayout, customEvent.getOldLayout());
    assertEquals("custom-account", customEvent.getAccountIdentifier());
    assertEquals(LayoutUpdateEvent.LAYOUT_UPDATED, customEvent.getEventType());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testResourceUsesNewLayoutInformation() {
    LayoutRequest oldLayout = new LayoutRequest();
    oldLayout.setName("old-name");
    oldLayout.setType("old-type");
    oldLayout.setDisplayName("Old Display");

    LayoutRequest newLayout = new LayoutRequest();
    newLayout.setName("new-name");
    newLayout.setType("new-type");
    newLayout.setDisplayName("New Display");

    LayoutUpdateEvent event = new LayoutUpdateEvent(newLayout, oldLayout, ACCOUNT_IDENTIFIER);
    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(ACCOUNT_IDENTIFIER + "new-name"
            + "new-type",
        resource.getIdentifier());
    assertEquals("New Display Layout", resource.getLabels().get("resourceName"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBothOldAndNewLayoutsAreAccessible() {
    assertNotNull(layoutUpdateEvent.getOldLayout());
    assertNotNull(layoutUpdateEvent.getNewLayout());
    assertEquals("layout: old", layoutUpdateEvent.getOldLayout().getYaml());
    assertEquals("layout: new", layoutUpdateEvent.getNewLayout().getYaml());
  }
}
