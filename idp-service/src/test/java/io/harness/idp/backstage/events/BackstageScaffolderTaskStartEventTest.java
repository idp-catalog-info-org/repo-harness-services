/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.events;

import static io.harness.audit.ResourceTypeConstants.IDP_BACKSTAGE_SCAFFOLDER_TASK;
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
public class BackstageScaffolderTaskStartEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-123";
  private static final String TEST_TASK_ID = "task-id-456";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstructor() {
    BackstageScaffolderTaskStartEvent event = new BackstageScaffolderTaskStartEvent(TEST_ACCOUNT_ID, TEST_TASK_ID);

    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getTaskId()).isEqualTo(TEST_TASK_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNoArgsConstructor() {
    BackstageScaffolderTaskStartEvent event = new BackstageScaffolderTaskStartEvent();
    assertThat(event).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEventType() {
    BackstageScaffolderTaskStartEvent event = new BackstageScaffolderTaskStartEvent(TEST_ACCOUNT_ID, TEST_TASK_ID);

    assertThat(event.getEventType()).isEqualTo("BackstageScaffolderTaskStarted");
    assertThat(event.getEventType()).isEqualTo(BackstageScaffolderTaskStartEvent.BACKSTAGE_SCAFFOLDER_TASK_START);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    BackstageScaffolderTaskStartEvent event = new BackstageScaffolderTaskStartEvent(TEST_ACCOUNT_ID, TEST_TASK_ID);

    ResourceScope resourceScope = event.getResourceScope();
    assertThat(resourceScope).isInstanceOf(AccountScope.class);
    AccountScope accountScope = (AccountScope) resourceScope;
    assertThat(accountScope.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetResource() {
    BackstageScaffolderTaskStartEvent event = new BackstageScaffolderTaskStartEvent(TEST_ACCOUNT_ID, TEST_TASK_ID);

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(TEST_TASK_ID);
    assertThat(resource.getType()).isEqualTo(IDP_BACKSTAGE_SCAFFOLDER_TASK);
    assertThat(resource.getLabels()).containsEntry(ResourceConstants.LABEL_KEY_RESOURCE_NAME, TEST_TASK_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMultipleInstances() {
    BackstageScaffolderTaskStartEvent event1 = new BackstageScaffolderTaskStartEvent("account1", "task1");
    BackstageScaffolderTaskStartEvent event2 = new BackstageScaffolderTaskStartEvent("account2", "task2");

    assertThat(event1.getAccountIdentifier()).isEqualTo("account1");
    assertThat(event1.getTaskId()).isEqualTo("task1");
    assertThat(event2.getAccountIdentifier()).isEqualTo("account2");
    assertThat(event2.getTaskId()).isEqualTo("task2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstant() {
    assertThat(BackstageScaffolderTaskStartEvent.BACKSTAGE_SCAFFOLDER_TASK_START)
        .isEqualTo("BackstageScaffolderTaskStarted");
  }
}
