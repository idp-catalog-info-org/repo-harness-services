/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.events;

import static io.harness.audit.ResourceTypeConstants.IDP_BACKSTAGE_CATALOG_ENTITY;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

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
public class BackstageCatalogEntityEventsTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-123";
  private static final String TEST_ENTITY_UID = "default/Component/my-service";
  private static final String TEST_YAML =
      "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: my-service";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityCreateEvent_Constructor() {
    BackstageCatalogEntityCreateEvent event =
        new BackstageCatalogEntityCreateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getNewEntityUid()).isEqualTo(TEST_ENTITY_UID);
    assertThat(event.getNewYaml()).isEqualTo(TEST_YAML);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityCreateEvent_NoArgsConstructor() {
    BackstageCatalogEntityCreateEvent event = new BackstageCatalogEntityCreateEvent();
    assertThat(event).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityCreateEvent_GetEventType() {
    BackstageCatalogEntityCreateEvent event =
        new BackstageCatalogEntityCreateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    assertThat(event.getEventType()).isEqualTo("BackstageCatalogEntityCreated");
    assertThat(event.getEventType()).isEqualTo(BackstageCatalogEntityCreateEvent.BACKSTAGE_CATALOG_ENTITY_CREATED);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityCreateEvent_GetResourceScope() {
    BackstageCatalogEntityCreateEvent event =
        new BackstageCatalogEntityCreateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    ResourceScope resourceScope = event.getResourceScope();
    assertThat(resourceScope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityCreateEvent_GetResource() {
    BackstageCatalogEntityCreateEvent event =
        new BackstageCatalogEntityCreateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(TEST_ENTITY_UID);
    assertThat(resource.getType()).isEqualTo(IDP_BACKSTAGE_CATALOG_ENTITY);
    assertThat(resource.getLabels()).containsEntry(ResourceConstants.LABEL_KEY_RESOURCE_NAME, TEST_ENTITY_UID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityDeleteEvent_Constructor() {
    BackstageCatalogEntityDeleteEvent event =
        new BackstageCatalogEntityDeleteEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getOldEntityUid()).isEqualTo(TEST_ENTITY_UID);
    assertThat(event.getOldYaml()).isEqualTo(TEST_YAML);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityDeleteEvent_NoArgsConstructor() {
    BackstageCatalogEntityDeleteEvent event = new BackstageCatalogEntityDeleteEvent();
    assertThat(event).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityDeleteEvent_GetEventType() {
    BackstageCatalogEntityDeleteEvent event =
        new BackstageCatalogEntityDeleteEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    assertThat(event.getEventType()).isEqualTo("BackstageCatalogEntityDeleted");
    assertThat(event.getEventType()).isEqualTo(BackstageCatalogEntityDeleteEvent.BACKSTAGE_CATALOG_ENTITY_DELETED);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityDeleteEvent_GetResourceScope() {
    BackstageCatalogEntityDeleteEvent event =
        new BackstageCatalogEntityDeleteEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    ResourceScope resourceScope = event.getResourceScope();
    assertThat(resourceScope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityDeleteEvent_GetResource() {
    BackstageCatalogEntityDeleteEvent event =
        new BackstageCatalogEntityDeleteEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, TEST_YAML);

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(TEST_ENTITY_UID);
    assertThat(resource.getType()).isEqualTo(IDP_BACKSTAGE_CATALOG_ENTITY);
    assertThat(resource.getLabels()).containsEntry(ResourceConstants.LABEL_KEY_RESOURCE_NAME, TEST_ENTITY_UID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityUpdateEvent_Constructor() {
    String oldYaml = "old yaml content";
    String newYaml = "new yaml content";
    BackstageCatalogEntityUpdateEvent event =
        new BackstageCatalogEntityUpdateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, oldYaml, newYaml);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getNewEntityUid()).isEqualTo(TEST_ENTITY_UID);
    assertThat(event.getOldYaml()).isEqualTo(oldYaml);
    assertThat(event.getNewYaml()).isEqualTo(newYaml);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityUpdateEvent_NoArgsConstructor() {
    BackstageCatalogEntityUpdateEvent event = new BackstageCatalogEntityUpdateEvent();
    assertThat(event).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityUpdateEvent_GetEventType() {
    BackstageCatalogEntityUpdateEvent event =
        new BackstageCatalogEntityUpdateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, "old", "new");

    assertThat(event.getEventType()).isEqualTo("BackstageCatalogEntityUpdated");
    assertThat(event.getEventType()).isEqualTo(BackstageCatalogEntityUpdateEvent.BACKSTAGE_CATALOG_ENTITY_UPDATED);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityUpdateEvent_GetResourceScope() {
    BackstageCatalogEntityUpdateEvent event =
        new BackstageCatalogEntityUpdateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, "old", "new");

    ResourceScope resourceScope = event.getResourceScope();
    assertThat(resourceScope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageCatalogEntityUpdateEvent_GetResource() {
    BackstageCatalogEntityUpdateEvent event =
        new BackstageCatalogEntityUpdateEvent(TEST_ACCOUNT_ID, TEST_ENTITY_UID, "old", "new");

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(TEST_ENTITY_UID);
    assertThat(resource.getType()).isEqualTo(IDP_BACKSTAGE_CATALOG_ENTITY);
    assertThat(resource.getLabels()).containsEntry(ResourceConstants.LABEL_KEY_RESOURCE_NAME, TEST_ENTITY_UID);
  }
}
