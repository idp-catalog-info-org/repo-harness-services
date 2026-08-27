/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.bulkReconciliation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.BULK_RECONCILIATION_EVENT;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.bulkReconciliation.BulkReconciliationEventData;
import io.harness.bulkReconciliation.ReferenceEntityType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.template.refresh.ReferenceEntityDetails;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.events.base.MessageLogContext;
import io.harness.pms.events.base.PmsRedisConsumer;
import io.harness.pms.pipeline.PipelineResource;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.queue.QueueController;
import io.harness.scope.ScopeHelper;
import io.harness.security.PrincipalProtoMapper;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class PipelineBulkReconciliationStreamConsumer implements PmsRedisConsumer {
  private final Consumer eventConsumer;
  private final QueueController queueController;
  private final Executor executorService;
  private final PipelineServiceConfiguration PipelineServiceConfiguration;
  private final Duration sleepMs;
  private static final Integer SLEEP_TIME_FOR_MAINTENANCE = 10;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final int WAIT_TIME_IN_SECONDS = 30;
  private final PipelineRefreshService pipelineRefreshService;
  private final TemplateResourceClient templateResourceClient;
  private final PipelineResource pipelineResource;
  private final SCMGitSyncHelper scmGitSyncHelper;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Inject
  public PipelineBulkReconciliationStreamConsumer(@Named(BULK_RECONCILIATION_EVENT) Consumer eventConsumer,
      QueueController queueController, @Named("BulkReconciliationExecutorService") Executor executor,
      PipelineServiceConfiguration PipelineServiceConfiguration, PipelineRefreshService pipelineRefreshService,
      TemplateResourceClient templateResourceClient, AccessControlClient accessControlClient,
      PipelineResource pipelineResource, SCMGitSyncHelper scmGitSyncHelper,
      PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper, PmsFeatureFlagService pmsFeatureFlagService,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.eventConsumer = eventConsumer;
    this.queueController = queueController;
    this.executorService = executor;
    this.PipelineServiceConfiguration = PipelineServiceConfiguration;
    this.pipelineRefreshService = pipelineRefreshService;
    this.templateResourceClient = templateResourceClient;
    this.pipelineSplitPermissionsHelper = pipelineSplitPermissionsHelper;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.pipelineResource = pipelineResource;
    this.scmGitSyncHelper = scmGitSyncHelper;
    Integer sleepMs = this.PipelineServiceConfiguration.getBulkReconciliationConsumerSleepIntervalMs();
    this.sleepMs = Duration.ofMillis(sleepMs);
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public void run() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    String threadName = this.getClass().getSimpleName() + "-handler-" + generateUuid();
    log.debug("Setting thread name to {}", threadName);
    Thread.currentThread().setName(threadName);

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(SLEEP_TIME_FOR_MAINTENANCE));
        }
        if (queueController.isNotPrimary()) {
          log.debug(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }

        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for Bulk Reconciliation stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages = eventConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    if (EmptyPredicate.isEmpty(messages)) {
      return;
    }

    PipelineBulkReconciliationStreamConsumer.ReadResult readResult = mapPipelineIdToMessages(messages);

    try {
      for (ReferenceEntityDetails referenceEntityDetails : readResult.referenceEntityEventDetails) {
        CompletableFutures<Void> completableFutures = new CompletableFutures<>(executorService);
        completableFutures.supplyAsync(() -> {
          Runnable runnable = PipelineReconciliationHandler.builder()
                                  .accountIdentifier(referenceEntityDetails.getAccountIdentifier())
                                  .orgIdentifier(referenceEntityDetails.getOrgIdentifier())
                                  .projectIdentifier(referenceEntityDetails.getProjectIdentifier())
                                  .parentUniqueId(referenceEntityDetails.getParentUniqueId())
                                  .pipelineIdentifier(referenceEntityDetails.getIdentifier())
                                  .bulkReconciliationUUID(referenceEntityDetails.getBulkReconciliationUUID())
                                  .pipelineRefreshService(pipelineRefreshService)
                                  .templateResourceClient(templateResourceClient)
                                  .pipelineResource(pipelineResource)
                                  .principal(referenceEntityDetails.getPrincipal())
                                  .referenceEntityDetails(referenceEntityDetails)
                                  .scmGitSyncHelper(scmGitSyncHelper)
                                  .pmsFeatureFlagService(pmsFeatureFlagService)
                                  .pipelineSplitPermissionsHelper(pipelineSplitPermissionsHelper)
                                  .scopeResolutionHelper(scopeResolutionHelper)
                                  .build();
          runnable.run();
          return null;
        });
        try {
          completableFutures.allOf().get(5, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }
      }
    } finally {
      // Ack all the messages.
      try {
        if (EmptyPredicate.isNotEmpty(readResult.tobeAcked)) {
          eventConsumer.acknowledge(readResult.tobeAcked);
        }
      } catch (Exception ex) {
        log.error("error while acknowledging messages", ex);
      }

      if (messages.size() < eventConsumer.getBatchSize()) {
        // Adding thread sleep when the events read are less than the batch-size. This way when the load is high,
        // consumer will query the events quickly. And in case of low load, thread will sleep for some time.
        log.debug("Sleeping the thread for {}", sleepMs);
        if (!sleepMs.isNegative() && !sleepMs.isZero()) {
          sleep(sleepMs);
        }
      }
    }
  }

  @VisibleForTesting
  PipelineBulkReconciliationStreamConsumer.ReadResult mapPipelineIdToMessages(List<Message> messages) {
    Set<String> toBeAcked = new HashSet<>();
    Set<ReferenceEntityDetails> referenceEntityDetailsSet = new HashSet<>();
    for (Message message : messages) {
      try (AutoLogContext ignore = new MessageLogContext(message)) {
        BulkReconciliationEventData event = buildEventFromMessage(message);
        if (event == null) {
          toBeAcked.add(message.getId());
          continue;
        }
        if (event.getReferenceEntityType().name().equals(ReferenceEntityType.PIPELINE.name())) {
          referenceEntityDetailsSet.add(
              ReferenceEntityDetails.builder()
                  .accountIdentifier(event.getAccountIdentifier())
                  .identifier(event.getIdentifier())
                  .orgIdentifier(event.getOrgIdentifier())
                  .projectIdentifier(event.getProjectIdentifier())
                  .parentUniqueId(event.getParentUniqueId())
                  .referenceEntityType(ReferenceEntityType.valueOf(event.getReferenceEntityType().name()))
                  .bulkReconciliationUUID(event.getBulkReconciliationUUID())
                  .messageId(message.getId())
                  .principal(PrincipalProtoMapper.toPrincipalDTO(event.getAccountIdentifier(), event.getPrincipal()))
                  .scope(ScopeHelper.getScope(
                      event.getAccountIdentifier(), event.getOrgIdentifier(), event.getProjectIdentifier()))
                  .type(StoreType.valueOf(event.getStoreType().name()))
                  .branch(event.getBranch())
                  .commitMessage(event.getCommitMessage())
                  .repo(event.getRepo())
                  .checkForReconciliation(event.getCheckForReconciliation())
                  .build());
          toBeAcked.add(message.getId());
        }
      }
    }
    return new PipelineBulkReconciliationStreamConsumer.ReadResult(
        referenceEntityDetailsSet, toBeAcked.toArray(String[] ::new));
  }

  private BulkReconciliationEventData buildEventFromMessage(Message message) {
    try {
      return BulkReconciliationEventData.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Could not map message to BulkReconciliationEventData", e);
      return null;
    }
  }

  @Override
  public void shutDown() {}

  @AllArgsConstructor
  static class ReadResult {
    Set<ReferenceEntityDetails> referenceEntityEventDetails;
    String[] tobeAcked;
  }
}
