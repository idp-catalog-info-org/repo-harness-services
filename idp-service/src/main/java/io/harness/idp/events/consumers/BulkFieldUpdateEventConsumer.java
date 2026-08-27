/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.idp.catalog.entities.BulkFieldUpdateOperation;
import io.harness.idp.catalog.entities.OperationStatus;
import io.harness.idp.catalog.events.BulkFieldUpdateEvent;
import io.harness.idp.catalog.repositories.BulkFieldUpdateOperationRepository;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BulkFieldUpdateEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "BulkFieldUpdateEventConsumer";
  private static final String BULK_FIELD_UPDATE_LOCK_PREFIX = "bulk_field_update_";
  private static final int MAX_RETRY = 3;

  private final BulkEntityFieldUpdateService bulkEntityFieldUpdateService;
  private final BulkFieldUpdateOperationRepository operationRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  public BulkFieldUpdateEventConsumer(@Named(IDP_BULK_FIELD_UPDATE_EVENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker,
      BulkEntityFieldUpdateService bulkEntityFieldUpdateService,
      BulkFieldUpdateOperationRepository operationRepository) {
    super(redisConsumer, queueController, resourceLocker);
    this.bulkEntityFieldUpdateService = bulkEntityFieldUpdateService;
    this.operationRepository = operationRepository;
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message != null && message.hasMessage()) {
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        boolean isValid =
            entityTypeAndActionValidation(CONSUMER_NAME, message, IDP_BULK_FIELD_UPDATE_EVENT, List.of(CREATE_ACTION));
        if (isValid) {
          ByteString data = message.getMessage().getData();
          BulkFieldUpdateEvent event = objectMapper.readValue(data.toStringUtf8(), BulkFieldUpdateEvent.class);
          String lockName = BULK_FIELD_UPDATE_LOCK_PREFIX + event.getId();
          return lockAndProcessData(lockName, entityType, data);
        }
      } catch (Exception ex) {
        log.error("Error in processing message with id: {} in {} consumer. Error = {}", message.getId(), CONSUMER_NAME,
            ex.getMessage(), ex);
        return false;
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    BulkFieldUpdateEvent event = objectMapper.readValue(data.toStringUtf8(), BulkFieldUpdateEvent.class);
    String id = event.getId();
    String accountIdentifier = event.getAccountIdentifier();

    log.info("Processing bulk field update operation: operationId={}, account={}", id, accountIdentifier);

    Optional<BulkFieldUpdateOperation> opOpt = operationRepository.findByIdAndAccountIdentifier(id, accountIdentifier);
    if (opOpt.isEmpty()) {
      log.error("Bulk field update operation not found: operationId={}", id);
      return;
    }

    BulkFieldUpdateOperation operation = opOpt.get();
    if (operation.getStatus() == OperationStatus.SUCCESS || operation.getStatus() == OperationStatus.PARTIAL_SUCCESS
        || operation.getStatus() == OperationStatus.DEAD_LETTER) {
      log.info("Bulk field update operation already in terminal state: operationId={}, status={}", id,
          operation.getStatus());
      return;
    }

    operation.setStatus(OperationStatus.PROCESSING);
    operation.setLastUpdatedAt(System.currentTimeMillis());
    operationRepository.save(operation);

    try {
      bulkEntityFieldUpdateService.execute(id);
      log.info("Successfully processed bulk field update operation: operationId={}", id);
    } catch (Exception e) {
      log.error("Failed to process bulk field update operation: operationId={}, error={}", id, e.getMessage(), e);
      int retryCount = operation.getRetryCount();
      if (retryCount < MAX_RETRY) {
        operation.setRetryCount(retryCount + 1);
        operation.setStatus(OperationStatus.QUEUED);
        operation.setErrorMessage(e.getMessage());
        operation.setLastUpdatedAt(System.currentTimeMillis());
        operationRepository.save(operation);
        log.info("Bulk field update operation will be retried: operationId={}, retryCount={}", id, retryCount + 1);
        throw e;
      } else {
        operation.setStatus(OperationStatus.DEAD_LETTER);
        operation.setErrorMessage(
            String.format("Max retries (%d) exceeded. Last error: %s", MAX_RETRY, e.getMessage()));
        operation.setLastUpdatedAt(System.currentTimeMillis());
        operationRepository.save(operation);
        log.error("Bulk field update operation moved to DEAD_LETTER after max retries: operationId={}", id);
      }
    }
  }
}
