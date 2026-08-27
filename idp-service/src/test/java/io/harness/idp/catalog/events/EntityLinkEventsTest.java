/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.audit.ResourceTypeConstants.IDP_ENTITY_LINK;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class EntityLinkEventsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "myproject";
  private static final String ENTITY_REF = "workflow:account/my-workflow";
  private static final String NEW_JSON = "{\"entityRef\":\"workflow:account/my-workflow\"}";
  private static final String OLD_JSON = "{\"old\":true}";

  // ── EntityLinkCreateEvent ───────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEvent_projectScope_returnsProjectScope() {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, NEW_JSON);

    ResourceScope scope = event.getResourceScope();

    assertThat(scope).isInstanceOf(ProjectScope.class);
    assertThat(((ProjectScope) scope).getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(((ProjectScope) scope).getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(((ProjectScope) scope).getProjectIdentifier()).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEvent_orgScope_returnsOrgScope() {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, null, ENTITY_REF, NEW_JSON);

    ResourceScope scope = event.getResourceScope();

    assertThat(scope).isInstanceOf(OrgScope.class);
    assertThat(((OrgScope) scope).getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(((OrgScope) scope).getOrgIdentifier()).isEqualTo(ORG_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEvent_accountScope_returnsAccountScope() {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, null, null, ENTITY_REF, NEW_JSON);

    ResourceScope scope = event.getResourceScope();

    assertThat(scope).isInstanceOf(AccountScope.class);
    assertThat(((AccountScope) scope).getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEvent_resourceBuiltWithEntityRefAndType() {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, NEW_JSON);

    assertThat(event.getResource().getIdentifier()).isEqualTo(ENTITY_REF);
    assertThat(event.getResource().getType()).isEqualTo(IDP_ENTITY_LINK);
    assertThat(event.getResource().getLabels()).containsValue(ENTITY_REF);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateEvent_eventTypeConstant() {
    EntityLinkCreateEvent event = new EntityLinkCreateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, NEW_JSON);

    assertThat(event.getEventType()).isEqualTo(EntityLinkCreateEvent.ENTITY_LINK_CREATED);
    assertThat(event.getNewEntityLinkJson()).isEqualTo(NEW_JSON);
  }

  // ── EntityLinkUpdateEvent ───────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEvent_projectScope_returnsProjectScope() {
    EntityLinkUpdateEvent event =
        new EntityLinkUpdateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, OLD_JSON, NEW_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(ProjectScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEvent_orgScope_returnsOrgScope() {
    EntityLinkUpdateEvent event = new EntityLinkUpdateEvent(ACCOUNT_ID, ORG_ID, null, ENTITY_REF, OLD_JSON, NEW_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(OrgScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEvent_accountScope_returnsAccountScope() {
    EntityLinkUpdateEvent event = new EntityLinkUpdateEvent(ACCOUNT_ID, null, null, ENTITY_REF, OLD_JSON, NEW_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(AccountScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEvent_hasOldAndNewJson() {
    EntityLinkUpdateEvent event =
        new EntityLinkUpdateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, OLD_JSON, NEW_JSON);

    assertThat(event.getEventType()).isEqualTo(EntityLinkUpdateEvent.ENTITY_LINK_UPDATED);
    assertThat(event.getOldEntityLinkJson()).isEqualTo(OLD_JSON);
    assertThat(event.getNewEntityLinkJson()).isEqualTo(NEW_JSON);
  }

  // ── EntityLinkDeleteEvent ───────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteEvent_projectScope_returnsProjectScope() {
    EntityLinkDeleteEvent event = new EntityLinkDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, OLD_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(ProjectScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteEvent_orgScope_returnsOrgScope() {
    EntityLinkDeleteEvent event = new EntityLinkDeleteEvent(ACCOUNT_ID, ORG_ID, null, ENTITY_REF, OLD_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(OrgScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteEvent_accountScope_returnsAccountScope() {
    EntityLinkDeleteEvent event = new EntityLinkDeleteEvent(ACCOUNT_ID, null, null, ENTITY_REF, OLD_JSON);

    assertThat(event.getResourceScope()).isInstanceOf(AccountScope.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteEvent_hasOldJson_andEventType() {
    EntityLinkDeleteEvent event = new EntityLinkDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENTITY_REF, OLD_JSON);

    assertThat(event.getEventType()).isEqualTo(EntityLinkDeleteEvent.ENTITY_LINK_DELETED);
    assertThat(event.getOldEntityLinkJson()).isEqualTo(OLD_JSON);
    assertThat(event.getResource().getIdentifier()).isEqualTo(ENTITY_REF);
    assertThat(event.getResource().getType()).isEqualTo(IDP_ENTITY_LINK);
  }
}
