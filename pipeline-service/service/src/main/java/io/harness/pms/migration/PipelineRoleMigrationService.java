/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.migration;

import io.harness.accesscontrol.migration.AccessControlMigrationFactory;
import io.harness.accesscontrol.migration.job.AccessControlMigrationJob;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineRoleMigrationService implements Managed {
  private final AccessControlMigrationJob migrationJob;
  private static final String SERVICE_NAME = "PIPELINE";
  private static final String EXISTING_PERMISSION = "core_pipeline_edit";
  private static final String ADDED_PERMISSION = "core_pipeline_create";

  @Inject
  public PipelineRoleMigrationService(AccessControlMigrationFactory accessControlMigrationFactory) {
    this.migrationJob = accessControlMigrationFactory.createMigrationJob(SERVICE_NAME, EXISTING_PERMISSION,
        Arrays.asList(ADDED_PERMISSION), SERVICE_NAME, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT_MIGRATION,
        FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT, AuthorizationServiceHeader.PIPELINE_SERVICE);
  }

  @Override
  public void start() throws Exception {
    log.info("[PipelineRoleMigrationService]: Staring the migration job...");
    migrationJob.start();
  }

  @Override
  public void stop() throws Exception {
    log.info("[PipelineRoleMigrationService]: Stopping the migration job...");
    migrationJob.stop();
  }
}
