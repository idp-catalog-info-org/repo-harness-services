/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.harness.idp.catalog.entities.RelationshipEventType;
import io.harness.idp.catalog.entities.RelationshipTask;
import io.harness.idp.catalog.entities.TaskStatus;
import io.harness.idp.catalog.processor.RelationshipEventProcessor;
import io.harness.idp.catalog.repositories.RelationshipTaskRepository;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class RelationshipRetryHandlerTest extends CategoryTest {
  AutoCloseable openMocks;

  @InjectMocks private RelationshipRetryHandler handler;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock RelationshipTaskRepository relationshipTaskRepository;
  @Mock RelationshipEventProcessor relationshipEventProcessor;

  private static final String TEST_ACCOUNT = "test-account";
  private static final String TEST_ENTITY_ID = "entity123";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_NoTasksReadyForRetry() {
    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(Collections.emptyList());

    handler.handle(IteratorEntity.builder().build());

    verify(relationshipEventProcessor, never()).processEvent(any());
    verify(relationshipTaskRepository, never()).delete(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_RetriesAndProcessesDirectly() {
    RelationshipTask task = buildTask(TEST_ENTITY_ID, TEST_ACCOUNT);

    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(List.of(task));

    handler.handle(IteratorEntity.builder().build());

    verify(relationshipEventProcessor).processEvent(any());
    verify(relationshipTaskRepository).delete(task);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_RetriesMultipleTasks() {
    RelationshipTask task1 = buildTask("entity1", TEST_ACCOUNT);
    RelationshipTask task2 = buildTask("entity2", TEST_ACCOUNT);

    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(Arrays.asList(task1, task2));

    handler.handle(IteratorEntity.builder().build());

    verify(relationshipEventProcessor, times(2)).processEvent(any());
    verify(relationshipTaskRepository, times(2)).delete(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_ProcessingFailure_UpdatesTask() {
    RelationshipTask task = buildTask(TEST_ENTITY_ID, TEST_ACCOUNT);

    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(List.of(task));
    doThrow(new RuntimeException("DB error")).when(relationshipEventProcessor).processEvent(any());

    handler.handle(IteratorEntity.builder().build());

    verify(relationshipTaskRepository, never()).delete(any());
    ArgumentCaptor<RelationshipTask> captor = ArgumentCaptor.forClass(RelationshipTask.class);
    verify(relationshipTaskRepository).save(captor.capture());
    RelationshipTask savedTask = captor.getValue();
    assertThat(savedTask.getRetryCount()).isEqualTo(2);
    assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_MaxRetriesExceeded_MovesToDeadLetter() {
    RelationshipTask task = buildTask(TEST_ENTITY_ID, TEST_ACCOUNT);
    task.setRetryCount(4);

    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(List.of(task));
    doThrow(new RuntimeException("DB error")).when(relationshipEventProcessor).processEvent(any());

    handler.handle(IteratorEntity.builder().build());

    ArgumentCaptor<RelationshipTask> captor = ArgumentCaptor.forClass(RelationshipTask.class);
    verify(relationshipTaskRepository).save(captor.capture());
    RelationshipTask savedTask = captor.getValue();
    assertThat(savedTask.getRetryCount()).isEqualTo(5);
    assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandle_SuccessfulProcessing_DeletesTask() {
    RelationshipTask task = buildTask(TEST_ENTITY_ID, TEST_ACCOUNT);

    when(relationshipTaskRepository.findTasksReadyForRetry(eq(TaskStatus.FAILED), anyLong(), anyInt()))
        .thenReturn(List.of(task));

    handler.handle(IteratorEntity.builder().build());

    verify(relationshipEventProcessor).processEvent(any());
    verify(relationshipTaskRepository).delete(task);
    verify(relationshipTaskRepository, never()).save(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(RelationshipRetryHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private RelationshipTask buildTask(String entityId, String accountIdentifier) {
    String eventPayload = String.format(
        "{\"entityId\":\"%s\",\"accountIdentifier\":\"%s\",\"eventType\":\"ESTABLISH\",\"timestamp\":123456}", entityId,
        accountIdentifier);
    return RelationshipTask.builder()
        .entityId(entityId)
        .accountIdentifier(accountIdentifier)
        .eventType(RelationshipEventType.ESTABLISH)
        .status(TaskStatus.FAILED)
        .retryCount(1)
        .eventPayload(eventPayload)
        .build();
  }
}
