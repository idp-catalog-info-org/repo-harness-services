/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.events;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class PermissionsUpdateEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final List<String> TEST_USERGROUP_OLD = List.of("IDP-USER");
  static final List<String> TEST_USERGROUP_NEW = List.of("IDP-ADMIN");
  static final List<String> TEST_PERMISSIONS_OLD = List.of("user_read");
  static final List<String> TEST_PERMISSIONS_NEW = List.of("user_read", "user_update");
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testPermissionsUpdateEventConstructor() {
    BackstagePermissions oldBackstagePermissions = new BackstagePermissions();
    oldBackstagePermissions.setPermissions(TEST_PERMISSIONS_OLD);
    oldBackstagePermissions.setUserGroups(TEST_USERGROUP_OLD);

    BackstagePermissions newBackstagePermissions = new BackstagePermissions();
    newBackstagePermissions.setPermissions(TEST_PERMISSIONS_NEW);
    newBackstagePermissions.setUserGroups(TEST_USERGROUP_NEW);

    PermissionsUpdateEvent event =
        new PermissionsUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newBackstagePermissions, oldBackstagePermissions);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertEquals(newBackstagePermissions, event.getNewBackstagePermissions());
    assertEquals(oldBackstagePermissions, event.getOldBackstagePermissions());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    BackstagePermissions oldBackstagePermissions = new BackstagePermissions();
    oldBackstagePermissions.setPermissions(TEST_PERMISSIONS_OLD);
    oldBackstagePermissions.setUserGroups(TEST_USERGROUP_OLD);

    BackstagePermissions newBackstagePermissions = new BackstagePermissions();
    newBackstagePermissions.setPermissions(TEST_PERMISSIONS_NEW);
    newBackstagePermissions.setUserGroups(TEST_USERGROUP_NEW);

    PermissionsUpdateEvent event =
        new PermissionsUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newBackstagePermissions, oldBackstagePermissions);

    AccountScope resourceScope = (AccountScope) event.getResourceScope();
    assertNotNull(resourceScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, resourceScope.getAccountIdentifier());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetResource() {
    BackstagePermissions oldBackstagePermissions = new BackstagePermissions();
    oldBackstagePermissions.setPermissions(TEST_PERMISSIONS_OLD);
    oldBackstagePermissions.setUserGroups(TEST_USERGROUP_OLD);

    BackstagePermissions newBackstagePermissions = new BackstagePermissions();
    newBackstagePermissions.setPermissions(TEST_PERMISSIONS_NEW);
    newBackstagePermissions.setUserGroups(TEST_USERGROUP_NEW);

    PermissionsUpdateEvent event =
        new PermissionsUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newBackstagePermissions, oldBackstagePermissions);

    Resource resource = event.getResource();
    assertNotNull(resource);
    assertNotNull(resource.getLabels());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetEventType() {
    BackstagePermissions oldBackstagePermissions = new BackstagePermissions();
    oldBackstagePermissions.setPermissions(TEST_PERMISSIONS_OLD);
    oldBackstagePermissions.setUserGroups(TEST_USERGROUP_OLD);

    BackstagePermissions newBackstagePermissions = new BackstagePermissions();
    newBackstagePermissions.setPermissions(TEST_PERMISSIONS_NEW);
    newBackstagePermissions.setUserGroups(TEST_USERGROUP_NEW);

    PermissionsUpdateEvent event =
        new PermissionsUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newBackstagePermissions, oldBackstagePermissions);

    assertEquals("PermissionsUpdated", event.getEventType());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
