/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class RolesMigrationService implements Managed {
  private Future<?> rolesJobFuture;
  private Future<?> pipelineAbortPermissionMigrationJobFuture;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("role-resource-migration-service-thread").build());
  private final ScheduledExecutorService pipelineAbortPermissionMigrationExecutorService =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setNameFormat("pipelineAbortPermission-migration-service-thread").build());
  private static final String DEBUG_MESSAGE = "RolesMigrationService: ";
  @Inject private InputSetRolesMigration inputSetRolesMigration;
  @Inject private PipelineAbortPermissionMigration pipelineAbortPermissionMigration;

  @Inject @Named("roleMigrationCache") private Cache<String, Boolean> eventsCache;

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    rolesJobFuture = executorService.scheduleWithFixedDelay(inputSetRolesMigration, 0, 1440, TimeUnit.MINUTES);
    pipelineAbortPermissionMigrationJobFuture = pipelineAbortPermissionMigrationExecutorService.scheduleWithFixedDelay(
        pipelineAbortPermissionMigration, 0, 1440, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    rolesJobFuture.cancel(false);
    pipelineAbortPermissionMigrationJobFuture.cancel(false);
    executorService.shutdown();
    pipelineAbortPermissionMigrationExecutorService.shutdown();
  }
}
