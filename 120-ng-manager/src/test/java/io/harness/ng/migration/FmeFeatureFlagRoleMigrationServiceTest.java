/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.rule.OwnerRule.KESHAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.migration.AccessControlMigrationFactory;
import io.harness.accesscontrol.migration.job.AccessControlMigrationJob;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FmeFeatureFlagRoleMigrationServiceTest extends CategoryTest {
  @Mock private AccessControlMigrationFactory migrationFactory;
  @Mock private AccessControlMigrationJob migrationJob;

  private FmeFeatureFlagRoleMigrationService service;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    org.mockito.Mockito
        .when(migrationFactory.createMigrationJob("FME_FEATURE_FLAG", "fme_fmefeatureflag_edit",
            java.util.Arrays.asList("fme_fmefeatureflag_create", "fme_fmefeatureflag_editflag",
                "fme_fmefeatureflag_editdefinition", "fme_fmefeatureflag_killswitch", "fme_fmefeatureflag_delete"),
            "FME", FeatureName.FME_FF_EDIT_PERMISSION_SPLIT_MIGRATION, FeatureName.FME_FF_EDIT_PERMISSION_SPLIT_ENFORCE,
            io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER))
        .thenReturn(migrationJob);

    service = new FmeFeatureFlagRoleMigrationService(migrationFactory);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testFactoryReceivesExactResourceType() {
    ArgumentCaptor<String> resourceTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(migrationFactory)
        .createMigrationJob(resourceTypeCaptor.capture(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThat(resourceTypeCaptor.getValue()).isEqualTo("FME_FEATURE_FLAG");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testFactoryReceivesExactExistingPermission() {
    ArgumentCaptor<String> permissionCaptor = ArgumentCaptor.forClass(String.class);
    verify(migrationFactory)
        .createMigrationJob(org.mockito.ArgumentMatchers.anyString(), permissionCaptor.capture(),
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThat(permissionCaptor.getValue()).isEqualTo("fme_fmefeatureflag_edit");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testFactoryReceivesExactFiveNewPermissions() {
    ArgumentCaptor<java.util.List<String>> permissionsCaptor = ArgumentCaptor.forClass(java.util.List.class);
    verify(migrationFactory)
        .createMigrationJob(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            permissionsCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

    java.util.List<String> newPermissions = permissionsCaptor.getValue();
    assertThat(newPermissions)
        .hasSize(5)
        .contains("fme_fmefeatureflag_create", "fme_fmefeatureflag_editflag", "fme_fmefeatureflag_editdefinition",
            "fme_fmefeatureflag_killswitch", "fme_fmefeatureflag_delete");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testArchiveUnarchiveExcludedFromNewPermissions() {
    ArgumentCaptor<java.util.List<String>> permissionsCaptor = ArgumentCaptor.forClass(java.util.List.class);
    verify(migrationFactory)
        .createMigrationJob(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            permissionsCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

    java.util.List<String> newPermissions = permissionsCaptor.getValue();
    assertThat(newPermissions).doesNotContain("fme_fmefeatureflag_archive", "fme_fmefeatureflag_unarchive");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testStartDelegates() throws Exception {
    service.start();
    verify(migrationJob).start();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testStopDelegates() throws Exception {
    service.stop();
    verify(migrationJob).stop();
  }
}
