/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.personaview.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_PERSONA_VIEW;
import static io.harness.idp.personaview.events.PersonaViewDeleteEvent.PERSONA_VIEW_DELETED;
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
public class PersonaViewDeleteEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_VIEW_IDENTIFIER = "custom-view";
  static final String TEST_VIEW_NAME = "Custom View";

  PersonaView personaView;

  @Before
  public void setUp() {
    personaView = new PersonaView();
    personaView.setIdentifier(TEST_VIEW_IDENTIFIER);
    personaView.setName(TEST_VIEW_NAME);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPersonaViewDeleteEvent() {
    PersonaViewDeleteEvent event = new PersonaViewDeleteEvent(personaView, TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(event);
    assertEquals(personaView, event.getOldPersonaView());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEventType() {
    PersonaViewDeleteEvent event = new PersonaViewDeleteEvent(personaView, TEST_ACCOUNT_IDENTIFIER);

    assertEquals(PERSONA_VIEW_DELETED, event.getEventType());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    PersonaViewDeleteEvent event = new PersonaViewDeleteEvent(personaView, TEST_ACCOUNT_IDENTIFIER);

    ResourceScope resourceScope = event.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(AccountScope.class, resourceScope.getClass());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) resourceScope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetResource() {
    PersonaViewDeleteEvent event = new PersonaViewDeleteEvent(personaView, TEST_ACCOUNT_IDENTIFIER);

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
    PersonaViewDeleteEvent event = new PersonaViewDeleteEvent();
    assertNotNull(event);
  }
}
