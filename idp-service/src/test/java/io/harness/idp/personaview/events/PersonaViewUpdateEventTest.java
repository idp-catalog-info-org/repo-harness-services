/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.personaview.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_PERSONA_VIEW;
import static io.harness.idp.personaview.events.PersonaViewUpdateEvent.PERSONA_VIEW_UPDATED;
import static io.harness.ng.core.ResourceConstants.LABEL_KEY_RESOURCE_NAME;
import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.PersonaView;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(IDP)
public class PersonaViewUpdateEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_VIEW_IDENTIFIER = "custom-view";
  static final String TEST_VIEW_NAME = "Custom View";

  PersonaView newPersonaView;
  PersonaView oldPersonaView;

  @Before
  public void setUp() {
    newPersonaView = new PersonaView();
    newPersonaView.setIdentifier(TEST_VIEW_IDENTIFIER);
    newPersonaView.setName(TEST_VIEW_NAME);

    oldPersonaView = new PersonaView();
    oldPersonaView.setIdentifier(TEST_VIEW_IDENTIFIER);
    oldPersonaView.setName("Old Custom View");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPersonaViewUpdateEvent() {
    PersonaViewUpdateEvent event = new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(event);
    assertEquals(newPersonaView, event.getNewPersonaView());
    assertEquals(oldPersonaView, event.getOldPersonaView());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEventType() {
    PersonaViewUpdateEvent event = new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, TEST_ACCOUNT_IDENTIFIER);

    assertEquals(PERSONA_VIEW_UPDATED, event.getEventType());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    PersonaViewUpdateEvent event = new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, TEST_ACCOUNT_IDENTIFIER);

    ResourceScope resourceScope = event.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(AccountScope.class, resourceScope.getClass());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) resourceScope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetResource() {
    PersonaViewUpdateEvent event = new PersonaViewUpdateEvent(newPersonaView, oldPersonaView, TEST_ACCOUNT_IDENTIFIER);

    Resource resource = event.getResource();
    assertNotNull(resource);
    assertEquals(IDP_PERSONA_VIEW, resource.getType());
    assertEquals(TEST_ACCOUNT_IDENTIFIER + TEST_VIEW_IDENTIFIER, resource.getIdentifier());
    assertNotNull(resource.getLabels());
    assertEquals(TEST_VIEW_NAME, resource.getLabels().get(LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    PersonaViewUpdateEvent event = new PersonaViewUpdateEvent();
    assertNotNull(event);
  }
}
