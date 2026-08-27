/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_USERGROUP_METADATA_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_NOTIFICATIONS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_ROLE_ASSIGNMENTS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_SCIM_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_SSO_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_USERS_PERMISSION;

import io.harness.accesscontrol.migration.AccessControlMigrationFactory;
import io.harness.accesscontrol.migration.job.AccessControlMigrationJob;
import io.harness.audit.ResourceTypeConstants;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserGroupsManageRoleMigrationJob implements Managed {
  private final AccessControlMigrationJob migrationJob;

  private static final String RESOURCE_TYPE = ResourceTypeConstants.USER_GROUP;
  private static final String EXISTING_PERMISSION = MANAGE_USERGROUP_PERMISSION;
  private static final List<String> NEW_PERMISSIONS =
      Arrays.asList(CREATE_USERGROUP_PERMISSION, EDIT_USERGROUP_METADATA_PERMISSION, DELETE_USERGROUP_PERMISSION,
          MANAGE_USERGROUP_SSO_PERMISSION, MANAGE_USERGROUP_SCIM_PERMISSION, MANAGE_USERGROUP_USERS_PERMISSION,
          MANAGE_USERGROUP_NOTIFICATIONS_PERMISSION, MANAGE_USERGROUP_ROLE_ASSIGNMENTS_PERMISSION);
  private static final String MODULE_NAME = "PLATFORM";
  private static final FeatureName MIGRATION_FF = FeatureName.PL_USER_GROUPS_MANAGE_PERMISSION_SPLIT_MIGRATION;
  private static final FeatureName ENFORCEMENT_FF = FeatureName.PL_USER_GROUPS_MANAGE_PERMISSION_SPLIT_ENFORCE;

  @Inject
  public UserGroupsManageRoleMigrationJob(AccessControlMigrationFactory migrationFactory) {
    this.migrationJob = migrationFactory.createMigrationJob(RESOURCE_TYPE, EXISTING_PERMISSION, NEW_PERMISSIONS,
        MODULE_NAME, MIGRATION_FF, ENFORCEMENT_FF, AuthorizationServiceHeader.NG_MANAGER);
  }

  @Override
  public void start() throws Exception {
    log.info("Starting core_usergroup_manage permission split migration to core_usergroup_manage and {} "
            + "migration service...",
        NEW_PERMISSIONS);
    migrationJob.start();
  }
  @Override
  public void stop() throws Exception {
    log.info("Stopping core_usergroup_manage permission split migration to core_usergroup_manage and {} "
            + "migration service...",
        NEW_PERMISSIONS);
    migrationJob.stop();
  }
}
