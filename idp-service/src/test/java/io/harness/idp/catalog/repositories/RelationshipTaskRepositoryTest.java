/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.RelationshipEventType;
import io.harness.idp.catalog.entities.RelationshipTask;
import io.harness.idp.catalog.entities.TaskStatus;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class RelationshipTaskRepositoryTest extends CategoryTest {
  static final String ENTITY_ID = "entity123";
  static final String ACCOUNT_ID = "acc1";

  @Mock RelationshipTaskRepository relationshipTaskRepository;

  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveAndFindByEntityId() {
    RelationshipTask task = createTask(ENTITY_ID, TaskStatus.PENDING);

    when(relationshipTaskRepository.save(any(RelationshipTask.class))).thenReturn(task);
    when(relationshipTaskRepository.findByEntityId(ENTITY_ID)).thenReturn(Optional.of(task));

    RelationshipTask saved = relationshipTaskRepository.save(task);
    Optional<RelationshipTask> found = relationshipTaskRepository.findByEntityId(ENTITY_ID);

    assertThat(saved).isNotNull();
    assertThat(found).isPresent();
    assertThat(found.get().getEntityId()).isEqualTo(ENTITY_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDuplicateEntityId_ThrowsDuplicateKeyException() {
    RelationshipTask task1 = createTask(ENTITY_ID, TaskStatus.PENDING);

    when(relationshipTaskRepository.save(any(RelationshipTask.class)))
        .thenReturn(task1)
        .thenThrow(new DuplicateKeyException("Duplicate key error"));

    relationshipTaskRepository.save(task1);

    RelationshipTask task2 = createTask(ENTITY_ID, TaskStatus.PENDING);
    assertThatThrownBy(() -> relationshipTaskRepository.save(task2)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindTasksReadyForRetry() {
    long currentTime = System.currentTimeMillis();
    long pastTime = currentTime - 60000;

    RelationshipTask readyTask = createTask("entity_svc1", TaskStatus.FAILED);
    readyTask.setRetryCount(1);
    readyTask.setNextRetryAt(pastTime);

    List<RelationshipTask> expectedTasks = Arrays.asList(readyTask);

    when(relationshipTaskRepository.findTasksReadyForRetry(TaskStatus.FAILED, currentTime, 5))
        .thenReturn(expectedTasks);

    List<RelationshipTask> tasks = relationshipTaskRepository.findTasksReadyForRetry(TaskStatus.FAILED, currentTime, 5);

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).getEntityId()).isEqualTo("entity_svc1");
    assertThat(tasks.get(0).getStatus()).isEqualTo(TaskStatus.FAILED);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndStatus() {
    RelationshipTask deadLetterTask = createTask("entity_failed", TaskStatus.DEAD_LETTER);

    List<RelationshipTask> expectedTasks = Arrays.asList(deadLetterTask);

    when(relationshipTaskRepository.findByAccountIdentifierAndStatus(ACCOUNT_ID, TaskStatus.DEAD_LETTER))
        .thenReturn(expectedTasks);

    List<RelationshipTask> tasks =
        relationshipTaskRepository.findByAccountIdentifierAndStatus(ACCOUNT_ID, TaskStatus.DEAD_LETTER);

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
    assertThat(tasks.get(0).getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCountByStatus() {
    when(relationshipTaskRepository.countByStatus(TaskStatus.PENDING)).thenReturn(10L);
    when(relationshipTaskRepository.countByStatus(TaskStatus.FAILED)).thenReturn(3L);
    when(relationshipTaskRepository.countByStatus(TaskStatus.DEAD_LETTER)).thenReturn(1L);

    long pendingCount = relationshipTaskRepository.countByStatus(TaskStatus.PENDING);
    long failedCount = relationshipTaskRepository.countByStatus(TaskStatus.FAILED);
    long deadLetterCount = relationshipTaskRepository.countByStatus(TaskStatus.DEAD_LETTER);

    assertThat(pendingCount).isEqualTo(10);
    assertThat(failedCount).isEqualTo(3);
    assertThat(deadLetterCount).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCountByAccountIdentifierAndStatus() {
    when(relationshipTaskRepository.countByAccountIdentifierAndStatus(ACCOUNT_ID, TaskStatus.FAILED)).thenReturn(5L);

    long count = relationshipTaskRepository.countByAccountIdentifierAndStatus(ACCOUNT_ID, TaskStatus.FAILED);

    assertThat(count).isEqualTo(5);
    verify(relationshipTaskRepository).countByAccountIdentifierAndStatus(ACCOUNT_ID, TaskStatus.FAILED);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindTasksReadyForRetry_ExcludesMaxRetries() {
    long currentTime = System.currentTimeMillis();

    RelationshipTask exceededRetries = createTask("entity_max", TaskStatus.FAILED);
    exceededRetries.setRetryCount(5);
    exceededRetries.setNextRetryAt(currentTime - 1000);

    when(relationshipTaskRepository.findTasksReadyForRetry(TaskStatus.FAILED, currentTime, 5))
        .thenReturn(Arrays.asList());

    List<RelationshipTask> tasks = relationshipTaskRepository.findTasksReadyForRetry(TaskStatus.FAILED, currentTime, 5);

    assertThat(tasks).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTaskStatusTransition() {
    RelationshipTask task = createTask(ENTITY_ID, TaskStatus.PENDING);

    when(relationshipTaskRepository.save(any(RelationshipTask.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    task.setStatus(TaskStatus.PROCESSING);
    RelationshipTask processing = relationshipTaskRepository.save(task);
    assertThat(processing.getStatus()).isEqualTo(TaskStatus.PROCESSING);

    task.setStatus(TaskStatus.FAILED);
    task.setRetryCount(1);
    task.setErrorMessage("Lock acquisition failed");
    RelationshipTask failed = relationshipTaskRepository.save(task);
    assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
    assertThat(failed.getRetryCount()).isEqualTo(1);
    assertThat(failed.getErrorMessage()).isEqualTo("Lock acquisition failed");

    task.setStatus(TaskStatus.DEAD_LETTER);
    task.setRetryCount(5);
    RelationshipTask deadLetter = relationshipTaskRepository.save(task);
    assertThat(deadLetter.getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
    assertThat(deadLetter.getRetryCount()).isEqualTo(5);
  }

  private RelationshipTask createTask(String entityId, TaskStatus status) {
    return RelationshipTask.builder()
        .entityId(entityId)
        .accountIdentifier(ACCOUNT_ID)
        .eventType(RelationshipEventType.ESTABLISH)
        .status(status)
        .retryCount(0)
        .createdAt(System.currentTimeMillis())
        .lastAttemptAt(0)
        .nextRetryAt(System.currentTimeMillis())
        .eventPayload("{\"test\":\"payload\"}")
        .build();
  }
}
