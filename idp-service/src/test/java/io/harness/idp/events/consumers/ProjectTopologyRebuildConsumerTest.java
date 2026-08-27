/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.MOVE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ProjectTopologyRebuildConsumerTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-123";
  static final String TEST_PROJECT_ID = "test-project-456";
  static final String TEST_ORG_ID = "test-org-789";

  @Mock ResourceLocker resourceLocker;
  @Mock CatalogScopeResolver catalogScopeResolver;
  @Mock CatalogEntityRepository catalogEntityRepository;

  @InjectMocks @Spy ProjectTopologyRebuildConsumer consumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(catalogEntityRepository.existsByAccountIdentifier(TEST_ACCOUNT_ID)).thenReturn(true);
  }

  private ProjectEntityChangeDTO createProjectEntityChangeDTO() {
    return ProjectEntityChangeDTO.newBuilder()
        .setAccountIdentifier(TEST_ACCOUNT_ID)
        .setIdentifier(TEST_PROJECT_ID)
        .setOrgIdentifier(TEST_ORG_ID)
        .build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessCreateAction() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessUpdateAction() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessDeleteAction() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, DELETE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessRestoreAction() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, RESTORE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessMoveAction() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, MOVE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    boolean result = consumer.processMessage(message);
    assertThat(result).isTrue();
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_TopologyRebuildFails_StillReturnsTrue() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    doThrow(new RuntimeException("Topology rebuild failed")).when(catalogScopeResolver).buildScopeTopology(any());
    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_InvalidProtobuf() {
    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, CREATE_ACTION))
                            .setData(ByteString.copyFromUtf8("invalid protobuf"))
                            .build())
            .build();

    boolean result = consumer.processMessage(message);
    assertThat(result).isFalse();
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageWrongEntityType() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ENTITY_TYPE, "WRONG_TYPE", ACTION, CREATE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();
    boolean result = consumer.processMessage(message);
    assertThat(result).isTrue();
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleScopeTopologyRebuildSuccess() {
    ProjectEntityChangeDTO projectDTO = createProjectEntityChangeDTO();
    when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

    consumer.handleScopeTopologyRebuild(projectDTO);
    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleScopeTopologyRebuildThrowsDoesNotPropagate() {
    ProjectEntityChangeDTO projectDTO = createProjectEntityChangeDTO();
    doThrow(new RuntimeException("Rebuild failed")).when(catalogScopeResolver).buildScopeTopology(any());

    consumer.handleScopeTopologyRebuild(projectDTO);

    verify(catalogScopeResolver, times(1)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_NoMessage() {
    Message message = Message.newBuilder().setId("test-message-id").build();
    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_MissingMetadata() {
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder().setData(data).build())
                          .build();

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageAllActionsCoverageCheck() {
    String[] actions = {CREATE_ACTION, UPDATE_ACTION, DELETE_ACTION, RESTORE_ACTION, MOVE_ACTION};

    for (String action : actions) {
      ByteString data = createProjectEntityChangeDTO().toByteString();

      Message message = Message.newBuilder()
                            .setId("test-message-id-" + action)
                            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, action))
                                            .setData(data)
                                            .build())
                            .build();

      when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
      doNothing().when(resourceLocker).releaseLock(any());
      when(catalogScopeResolver.buildScopeTopology(eq(TEST_ACCOUNT_ID))).thenReturn(null);

      boolean result = consumer.processMessage(message);
      assertThat(result).isTrue();
    }
    verify(catalogScopeResolver, times(5)).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testProcessMessage_SkipsWhenNoCatalogEntitiesForAccount() {
    when(catalogEntityRepository.existsByAccountIdentifier(TEST_ACCOUNT_ID)).thenReturn(false);
    ByteString data = createProjectEntityChangeDTO().toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(resourceLocker, never()).acquireLock(any());
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }
}
