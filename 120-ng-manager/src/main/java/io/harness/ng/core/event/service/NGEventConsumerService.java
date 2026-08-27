/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.DRIFT_DETECTION_RESPONSE_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_CONFIG_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_FULL_SYNC_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;
import static io.harness.eventsframework.EventsFrameworkConstants.LDAP_GROUP_SYNC;
import static io.harness.eventsframework.EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSE;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSE_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION;
import static io.harness.eventsframework.EventsFrameworkConstants.SCHEDULED_TASK_DEFAULT_RESPONSE_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.UNIFIED_ARTIFACT_POLLING_RESPONSE_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.UNIFIED_INSTANCE_SYNC_RESPONSE_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP_MAX_PROCESSING_TIME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.impl.FullSyncMessageConsumer;
import io.harness.ldap.scheduler.LdapGroupSyncStreamConsumer;
import io.harness.ng.authenticationsettings.SamlAuthorizationStreamConsumer;
import io.harness.ng.core.event.consumer.EntityActivityStreamConsumer;
import io.harness.ng.core.event.consumer.EntityCRUDStreamConsumer;
import io.harness.ng.core.event.consumer.ExecutionRetentionCleanupStreamConsumer;
import io.harness.ng.core.event.consumer.InstanceStatsStreamConsumer;
import io.harness.ng.core.event.consumer.LicenseUsageStreamConsumer;
import io.harness.ng.core.event.consumer.NGAccountSetupConsumer;
import io.harness.ng.core.event.consumer.SetupUsageStreamConsumer;
import io.harness.ng.core.event.consumer.UserMembershipStreamConsumer;
import io.harness.ng.core.event.modulelicense.ModuleLicenseStreamConsumer;
import io.harness.ng.runner.scheduledtask.response.artifactpolling.ArtifactPollingResponseConsumer;
import io.harness.ng.runner.scheduledtask.response.driftdetection.DriftDetectionResponseConsumer;
import io.harness.ng.runner.scheduledtask.response.example.SampleScheduledTaskResponseConsumer;
import io.harness.ng.runner.scheduledtask.response.instancesync.UnifiedInstanceSyncResponseConsumer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class NGEventConsumerService implements Managed {
  @Inject private NGAccountSetupConsumer ngAccountSetupConsumer;
  @Inject private EntityCRUDStreamConsumer entityCRUDStreamConsumer;
  @Inject private UserMembershipStreamConsumer userMembershipStreamConsumer;
  @Inject private ModuleLicenseStreamConsumer moduleLicenseStreamConsumer;
  @Inject private LicenseUsageStreamConsumer licenseUsageStreamConsumer;
  @Inject private SetupUsageStreamConsumer setupUsageStreamConsumer;
  @Inject private EntityActivityStreamConsumer entityActivityStreamConsumer;
  @Inject private SamlAuthorizationStreamConsumer samlAuthorizationStreamConsumer;
  @Inject private LdapGroupSyncStreamConsumer ldapGroupSyncStreamConsumer;
  @Inject private FullSyncMessageConsumer fullSyncMessageConsumer;
  @Inject private SampleScheduledTaskResponseConsumer sampleScheduledTaskResponseConsumer;
  @Inject private UnifiedInstanceSyncResponseConsumer unifiedInstanceSyncResponseConsumer;
  @Inject private ArtifactPollingResponseConsumer artifactPollingResponseConsumer;
  @Inject private DriftDetectionResponseConsumer driftDetectionResponseConsumer;
  private ExecutorService ngAccountSetupConsumerService;

  @Inject private InstanceStatsStreamConsumer instanceStatsStreamConsumer;
  @Inject private ExecutionRetentionCleanupStreamConsumer executionRetentionCleanupStreamConsumer;
  private ExecutorService entityCRUDConsumerService;
  private ExecutorService setupUsageConsumerService;
  private ExecutorService entityActivityConsumerService;
  private ExecutorService licenseUsageConsumerService;
  private ExecutorService userMembershipConsumerService;
  private ExecutorService moduleLicenseConsumerService;
  private ExecutorService samlAuthorizationConsumerService;
  private ExecutorService ldapGroupSyncConsumerService;
  private ExecutorService instanceStatsConsumerService;
  private ExecutorService gitSyncConfigStreamConsumerService;
  private ExecutorService fullSyncStreamConsumerService;
  private ExecutorService scheduledTaskResponseConsumerService;
  private ExecutorService unifiedInstanceSyncResponseConsumerService;
  private ExecutorService artifactPollingResponseConsumerService;
  private ExecutorService driftDetectionResponseConsumerService;
  private ExecutorService executionRetentionCleanupConsumerService;

  @Override
  public void start() {
    ngAccountSetupConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("ng_account_setup_consumer").build());
    entityCRUDConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_CRUD).build());
    setupUsageConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(SETUP_USAGE).build());
    entityActivityConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_ACTIVITY).build());
    licenseUsageConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(LICENSES_USAGE_REDIS_EVENT_CONSUMER).build());
    userMembershipConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(USERMEMBERSHIP).build());
    moduleLicenseConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(MODULE_LICENSE).build());
    samlAuthorizationConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(SAML_AUTHORIZATION_ASSERTION).build());
    ldapGroupSyncConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(LDAP_GROUP_SYNC).build());
    instanceStatsConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INSTANCE_STATS).build());
    gitSyncConfigStreamConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GIT_CONFIG_STREAM).build());
    fullSyncStreamConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GIT_FULL_SYNC_STREAM).build());
    scheduledTaskResponseConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(SCHEDULED_TASK_DEFAULT_RESPONSE_CONSUMER).build());
    unifiedInstanceSyncResponseConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(UNIFIED_INSTANCE_SYNC_RESPONSE_CONSUMER).build());
    artifactPollingResponseConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(UNIFIED_ARTIFACT_POLLING_RESPONSE_CONSUMER).build());
    driftDetectionResponseConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(DRIFT_DETECTION_RESPONSE_CONSUMER).build());
    executionRetentionCleanupConsumerService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(EXECUTION_RETENTION_CLEANUP_EVENT).build());

    entityCRUDConsumerService.execute(entityCRUDStreamConsumer);
    ngAccountSetupConsumerService.execute(ngAccountSetupConsumer);
    setupUsageConsumerService.execute(setupUsageStreamConsumer);
    entityActivityConsumerService.execute(entityActivityStreamConsumer);
    userMembershipConsumerService.execute(userMembershipStreamConsumer);
    moduleLicenseConsumerService.execute(moduleLicenseStreamConsumer);
    licenseUsageConsumerService.execute(licenseUsageStreamConsumer);
    samlAuthorizationConsumerService.execute(samlAuthorizationStreamConsumer);
    ldapGroupSyncConsumerService.execute(ldapGroupSyncStreamConsumer);
    instanceStatsConsumerService.execute(instanceStatsStreamConsumer);
    fullSyncStreamConsumerService.execute(fullSyncMessageConsumer);
    scheduledTaskResponseConsumerService.execute(sampleScheduledTaskResponseConsumer);
    unifiedInstanceSyncResponseConsumerService.execute(unifiedInstanceSyncResponseConsumer);
    artifactPollingResponseConsumerService.execute(artifactPollingResponseConsumer);
    driftDetectionResponseConsumerService.execute(driftDetectionResponseConsumer);
    executionRetentionCleanupConsumerService.execute(executionRetentionCleanupStreamConsumer);
  }

  @Override
  public void stop() throws InterruptedException {
    ngAccountSetupConsumerService.shutdownNow();
    entityCRUDConsumerService.shutdownNow();
    setupUsageConsumerService.shutdownNow();
    entityActivityConsumerService.shutdownNow();
    userMembershipConsumerService.shutdownNow();
    moduleLicenseConsumerService.shutdownNow();
    samlAuthorizationConsumerService.shutdownNow();
    ldapGroupSyncConsumerService.shutdown();
    gitSyncConfigStreamConsumerService.shutdownNow();
    instanceStatsConsumerService.shutdownNow();
    unifiedInstanceSyncResponseConsumerService.shutdownNow();
    artifactPollingResponseConsumerService.shutdownNow();
    driftDetectionResponseConsumerService.shutdownNow();
    executionRetentionCleanupConsumerService.shutdownNow();
    ngAccountSetupConsumerService.awaitTermination(ENTITY_CRUD_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    entityCRUDConsumerService.awaitTermination(ENTITY_CRUD_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    setupUsageConsumerService.awaitTermination(SETUP_USAGE_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    entityActivityConsumerService.awaitTermination(ENTITY_ACTIVITY_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    userMembershipConsumerService.awaitTermination(USERMEMBERSHIP_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    moduleLicenseConsumerService.awaitTermination(MODULE_LICENSE_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    samlAuthorizationConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    ldapGroupSyncConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    instanceStatsConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    gitSyncConfigStreamConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    scheduledTaskResponseConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    unifiedInstanceSyncResponseConsumerService.awaitTermination(
        DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    artifactPollingResponseConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    driftDetectionResponseConsumerService.awaitTermination(DEFAULT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
    executionRetentionCleanupConsumerService.awaitTermination(
        EXECUTION_RETENTION_CLEANUP_EVENT_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
  }
}
