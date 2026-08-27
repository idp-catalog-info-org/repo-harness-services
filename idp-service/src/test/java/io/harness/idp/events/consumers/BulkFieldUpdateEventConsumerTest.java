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
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.idp.catalog.entities.BulkFieldUpdateOperation;
import io.harness.idp.catalog.entities.OperationStatus;
import io.harness.idp.catalog.events.BulkFieldUpdateEvent;
import io.harness.idp.catalog.repositories.BulkFieldUpdateOperationRepository;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BulkFieldUpdateEventConsumerTest extends CategoryTest {
  private static final String OPERATION_ID = "op123";
  private static final String ACCOUNT_ID = "acc1";

  @Mock private Consumer redisConsumer;
  @Mock private QueueController queueController;
  @Mock private ResourceLocker resourceLocker;
  @Mock private BulkEntityFieldUpdateService bulkEntityFieldUpdateService;
  @Mock private BulkFieldUpdateOperationRepository operationRepository;

  private BulkFieldUpdateEventConsumer consumer;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    consumer = new BulkFieldUpdateEventConsumer(
        redisConsumer, queueController, resourceLocker, bulkEntityFieldUpdateService, operationRepository);

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalHappyPath() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.QUEUED)
                                             .retryCount(0)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    verify(bulkEntityFieldUpdateService).execute(OPERATION_ID);
    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository).save(captor.capture());
    BulkFieldUpdateOperation saved = captor.getValue();
    assert saved.getStatus() == OperationStatus.PROCESSING;
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalTerminalStateSuccess() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.SUCCESS)
                                             .retryCount(0)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    verify(bulkEntityFieldUpdateService, never()).execute(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalTerminalStatePartialSuccess() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.PARTIAL_SUCCESS)
                                             .retryCount(0)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    verify(bulkEntityFieldUpdateService, never()).execute(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalTerminalStateDeadLetter() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.DEAD_LETTER)
                                             .retryCount(3)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    verify(bulkEntityFieldUpdateService, never()).execute(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalOperationNotFound() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    verify(bulkEntityFieldUpdateService, never()).execute(any());
  }

  @Test(expected = Exception.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalExecuteThrowsAndRetryCountLessThan3Rethrows() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.QUEUED)
                                             .retryCount(1)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    doThrow(new RuntimeException("Execution failed")).when(bulkEntityFieldUpdateService).execute(OPERATION_ID);

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository, times(2)).save(captor.capture());
    BulkFieldUpdateOperation retryOp = captor.getAllValues().get(1);
    assert retryOp.getStatus() == OperationStatus.QUEUED;
    assert retryOp.getRetryCount() == 2;
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessInternalExecuteThrowsAndRetryCountEquals3DoesNotRethrow() throws Exception {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.QUEUED)
                                             .retryCount(3)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    doThrow(new RuntimeException("Execution failed")).when(bulkEntityFieldUpdateService).execute(OPERATION_ID);

    consumer.processInternal(IDP_BULK_FIELD_UPDATE_EVENT, data);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository, times(2)).save(captor.capture());
    BulkFieldUpdateOperation deadLetterOp = captor.getAllValues().get(1);
    assert deadLetterOp.getStatus() == OperationStatus.DEAD_LETTER;
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageHappyPath() {
    BulkFieldUpdateEvent event = BulkFieldUpdateEvent.builder().id(OPERATION_ID).accountIdentifier(ACCOUNT_ID).build();
    ByteString data;
    try {
      data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(event));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Message message = Message.newBuilder()
                          .setId("msgId123")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ENTITY_TYPE, IDP_BULK_FIELD_UPDATE_EVENT, "action",
                                              CREATE_ACTION, "accountId", ACCOUNT_ID))
                                          .setData(data)
                                          .build())
                          .build();

    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(OPERATION_ID)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.QUEUED)
                                             .retryCount(0)
                                             .permittedEntityRefs(Collections.emptyList())
                                             .properties(Collections.emptyList())
                                             .build();
    when(operationRepository.findByIdAndAccountIdentifier(OPERATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    boolean result = consumer.processMessage(message);

    assert result;
  }
}
