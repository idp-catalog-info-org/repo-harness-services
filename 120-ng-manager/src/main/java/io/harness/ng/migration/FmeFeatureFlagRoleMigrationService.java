/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.accesscontrol.migration.AccessControlMigrationFactory;
import io.harness.accesscontrol.migration.job.AccessControlMigrationJob;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FmeFeatureFlagRoleMigrationService implements Managed {
  private final AccessControlMigrationJob migrationJob;

  private static final String RESOURCE_TYPE = "FME_FEATURE_FLAG";
  private static final String EXISTING_PERMISSION = "fme_fmefeatureflag_edit";
  private static final List<String> NEW_PERMISSIONS =
      Arrays.asList("fme_fmefeatureflag_create", "fme_fmefeatureflag_editflag", "fme_fmefeatureflag_editdefinition",
          "fme_fmefeatureflag_killswitch", "fme_fmefeatureflag_delete");
  private static final String MODULE_NAME = "FME";
  private static final FeatureName MIGRATION_FF = FeatureName.FME_FF_EDIT_PERMISSION_SPLIT_MIGRATION;
  private static final FeatureName ENFORCEMENT_FF = FeatureName.FME_FF_EDIT_PERMISSION_SPLIT_ENFORCE;

  @Inject
  public FmeFeatureFlagRoleMigrationService(AccessControlMigrationFactory migrationFactory) {
    this.migrationJob = migrationFactory.createMigrationJob(RESOURCE_TYPE, EXISTING_PERMISSION, NEW_PERMISSIONS,
        MODULE_NAME, MIGRATION_FF, ENFORCEMENT_FF, AuthorizationServiceHeader.NG_MANAGER);
  }

  @Override
  public void start() throws Exception {
    log.info("Starting FME feature flag edit permission split migration service...");
    migrationJob.start();
  }

  @Override
  public void stop() throws Exception {
    log.info("Stopping FME feature flag edit permission split migration service...");
    migrationJob.stop();
  }
}
