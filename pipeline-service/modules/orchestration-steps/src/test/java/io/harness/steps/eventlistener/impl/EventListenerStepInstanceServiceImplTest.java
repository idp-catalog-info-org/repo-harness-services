/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.repositories.EventListenerStepInstanceRepository;
import io.harness.rule.Owner;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance.EventListenerStepInstanceKeys;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.waiter.WaitNotifyEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class EventListenerStepInstanceServiceImplTest extends CategoryTest {
  @Mock private EventListenerStepInstanceRepository eventListenerStepInstanceRepository;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks EventListenerStepInstanceServiceImpl eventListenerStepInstanceService;

  private static final String PARENT_UNIQUE_ID = "parent_unique_id";

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testSave() {
    EventListenerStepInstance eventListenerStepInstance =
        EventListenerStepInstance.builder().id("_id").ambiance(Ambiance.newBuilder().build()).build();
    when(eventListenerStepInstanceRepository.save(eventListenerStepInstance)).thenReturn(eventListenerStepInstance);
    EventListenerStepInstance instance = eventListenerStepInstanceService.save(eventListenerStepInstance);
    assertThat(instance.getId()).isEqualTo("_id");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testSaveWithAmbiance() {
    EventListenerStepInstance eventListenerStepInstance = EventListenerStepInstance.builder().id("_id").build();
    when(eventListenerStepInstanceRepository.save(eventListenerStepInstance)).thenReturn(eventListenerStepInstance);
    EventListenerStepInstance instance = eventListenerStepInstanceService.save(eventListenerStepInstance);
    assertThat(instance.getId()).isEqualTo("_id");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGet() {
    EventListenerStepInstance eventListenerStepInstance = EventListenerStepInstance.builder().build();
    Optional<EventListenerStepInstance> optional = Optional.of(eventListenerStepInstance);
    when(eventListenerStepInstanceRepository.findById(any())).thenReturn(optional);
    assertThat(eventListenerStepInstanceService.get("hello")).isEqualTo(eventListenerStepInstance);
  }
  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetException() {
    String instance = "Invalid";
    when(eventListenerStepInstanceRepository.findById(instance)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> { eventListenerStepInstanceService.get(""); })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Invalid EventListener step instance id");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testDeleteByNodeExecutionIdsWhenLessThanBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    nodeExecutionIds.add(UUIDGenerator.generateUuid());
    nodeExecutionIds.add(UUIDGenerator.generateUuid());
    when(eventListenerStepInstanceRepository.deleteAllByNodeExecutionIdIn(any())).thenReturn(2L);

    eventListenerStepInstanceService.deleteByNodeExecutionIds(nodeExecutionIds);
    ArgumentCaptor<Set<String>> setArgumentCaptor = ArgumentCaptor.forClass(Set.class);
    verify(eventListenerStepInstanceRepository, times(1)).deleteAllByNodeExecutionIdIn(setArgumentCaptor.capture());
    Set<String> setArgs = setArgumentCaptor.getValue();
    assertThat(setArgs).isEqualTo(nodeExecutionIds);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testDeleteByNodeExecutionIdsWhenEqualToBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      nodeExecutionIds.add(UUIDGenerator.generateUuid());
    }
    // only return value if called with valid query else throw exception
    when(eventListenerStepInstanceRepository.deleteAllByNodeExecutionIdIn(any()))
        .thenAnswer((Answer<Long>) invocation -> {
          Object[] args = invocation.getArguments();
          Set<String> setNodeExecutionId = (Set<String>) args[0];
          if (setNodeExecutionId.equals(nodeExecutionIds)) {
            return 500L;
          }
          throw new Exception();
        });

    eventListenerStepInstanceService.deleteByNodeExecutionIds(nodeExecutionIds);
    verify(eventListenerStepInstanceRepository, times(1)).deleteAllByNodeExecutionIdIn(any());
    verifyNoMoreInteractions(eventListenerStepInstanceRepository);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testDeleteByNodeExecutionIdsWhenMoreThanBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 600; i++) {
      nodeExecutionIds.add(UUIDGenerator.generateUuid());
    }
    // only return value if called with valid query else throw exception
    when(eventListenerStepInstanceRepository.deleteAllByNodeExecutionIdIn(any()))
        .thenAnswer((Answer<Long>) invocation -> {
          Object[] args = invocation.getArguments();
          Set<String> setNodeExecutionId = (Set<String>) args[0];
          if (setNodeExecutionId.size() == 500) {
            return 500L;
          } else if (setNodeExecutionId.size() == 100) {
            return 100L;
          }
          throw new Exception();
        });

    eventListenerStepInstanceService.deleteByNodeExecutionIds(nodeExecutionIds);
    verify(eventListenerStepInstanceRepository, times(2)).deleteAllByNodeExecutionIdIn(any());
    verifyNoMoreInteractions(eventListenerStepInstanceRepository);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testAbortByNodeExecutionId() {
    eventListenerStepInstanceService.abortByNodeExecutionId("hello");
    verify(eventListenerStepInstanceRepository, times(1))
        .updateFirst(eq(new Query(Criteria.where(EventListenerStepInstanceKeys.nodeExecutionId).is("hello"))
                             .addCriteria(Criteria.where(EventListenerStepInstanceKeys.status)
                                              .is(EventListenerStepInstanceStatus.WAITING))),
            eq(new Update().set(EventListenerStepInstanceKeys.status, EventListenerStepInstanceStatus.ABORTED)));
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testExpireByNodeExecutionId() {
    eventListenerStepInstanceService.expireByNodeExecutionId("hello");
    verify(eventListenerStepInstanceRepository, times(1))
        .updateFirst(eq(new Query(Criteria.where(EventListenerStepInstanceKeys.nodeExecutionId).is("hello"))
                             .addCriteria(Criteria.where(EventListenerStepInstanceKeys.status)
                                              .is(EventListenerStepInstanceStatus.WAITING))),
            eq(new Update().set(EventListenerStepInstanceKeys.status, EventListenerStepInstanceStatus.EXPIRED)));
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testFinalizeStatus() {
    EventListenerStepInstance instance = EventListenerStepInstance.builder().build();
    when(eventListenerStepInstanceRepository.updateFirst(any(), any())).thenReturn(instance);
    when(waitNotifyEngine.doneWith(any(), any())).thenReturn("resumeId");
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PlanExecutionId").build();
    instance.setAmbiance(ambiance);
    eventListenerStepInstanceService.finalizeStatus(
        "eventInstanceId", "eventCorrelationId", EventListenerStepInstanceStatus.SUCCEEDED, new ArrayList<>());
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(eventListenerStepInstanceRepository)
        .updateFirst(queryArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertThat(query.getQueryObject()).hasSize(2);
    assertThat(update.getUpdateObject().size()).isEqualTo(1);
  }
}
