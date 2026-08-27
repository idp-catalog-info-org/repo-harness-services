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
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.namespace.service.NamespaceService;
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
public class ProjectMovementConsumerTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-123";
  static final String TEST_PROJECT_ID = "test-project-456";
  static final String TEST_OLD_ORG_ID = "old-org-789";
  static final String TEST_NEW_ORG_ID = "new-org-abc";

  @Mock ResourceLocker resourceLocker;
  @Mock CatalogService catalogService;
  @Mock NamespaceService namespaceService;

  @InjectMocks @Spy ProjectMovementConsumer consumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private ProjectEntityChangeDTO createProjectEntityChangeDTO(String action) {
    return ProjectEntityChangeDTO.newBuilder()
        .setAccountIdentifier(TEST_ACCOUNT_ID)
        .setIdentifier(TEST_PROJECT_ID)
        .setOrgIdentifier(TEST_NEW_ORG_ID)
        .setOldOrgIdentifier(TEST_OLD_ORG_ID)
        .build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSuccessMoveAction() {
    ByteString data = createProjectEntityChangeDTO(MOVE_ACTION).toByteString();

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
    when(namespaceService.getAccountIdpStatus(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(catalogService).projectMovement(any());
    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogService, times(1)).projectMovement(any(ProjectEntityChangeDTO.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSkipsCreateAction() {
    ByteString data = createProjectEntityChangeDTO(CREATE_ACTION).toByteString();

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
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSkipsUpdateAction() {
    ByteString data = createProjectEntityChangeDTO(UPDATE_ACTION).toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageSkipsDeleteAction() {
    ByteString data = createProjectEntityChangeDTO(DELETE_ACTION).toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, DELETE_ACTION))
                            .setData(data)
                            .build())
            .build();

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageProjectMovementFails() {
    ByteString data = createProjectEntityChangeDTO(MOVE_ACTION).toByteString();

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
    when(namespaceService.getAccountIdpStatus(TEST_ACCOUNT_ID)).thenReturn(true);
    doThrow(new RuntimeException("Project movement failed")).when(catalogService).projectMovement(any());

    boolean result = consumer.processMessage(message);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageInvalidProtobuf() {
    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, PROJECT_EVENT_ENTITY, ACTION, MOVE_ACTION))
                            .setData(ByteString.copyFromUtf8("invalid protobuf"))
                            .build())
            .build();

    boolean result = consumer.processMessage(message);

    assertThat(result).isFalse();
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageWrongEntityType() {
    ByteString data = createProjectEntityChangeDTO(MOVE_ACTION).toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ENTITY_TYPE, "WRONG_TYPE", ACTION, MOVE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();
    boolean result = consumer.processMessage(message);
    assertThat(result).isTrue();
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleProjectMovementSuccess() {
    ProjectEntityChangeDTO projectDTO = createProjectEntityChangeDTO(MOVE_ACTION);
    doNothing().when(catalogService).projectMovement(any());

    consumer.handleProjectMovement(projectDTO);

    verify(catalogService, times(1)).projectMovement(projectDTO);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleProjectMovementThrowsPropagatesException() {
    ProjectEntityChangeDTO projectDTO = createProjectEntityChangeDTO(MOVE_ACTION);
    doThrow(new RuntimeException("Movement failed")).when(catalogService).projectMovement(any());
    consumer.handleProjectMovement(projectDTO);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageIdempotencySameMessageTwice() {
    ByteString data = createProjectEntityChangeDTO(MOVE_ACTION).toByteString();

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
    doNothing().when(catalogService).projectMovement(any());

    boolean result1 = consumer.processMessage(message);
    boolean result2 = consumer.processMessage(message);

    assertThat(result1).isTrue();
    assertThat(result2).isTrue();
    verify(catalogService, times(2)).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_NoMessage() {
    Message message = Message.newBuilder().setId("test-message-id").build();
    boolean result = consumer.processMessage(message);
    assertThat(result).isTrue();
    verify(catalogService, never()).projectMovement(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessageMissingMetadata() {
    ByteString data = createProjectEntityChangeDTO(MOVE_ACTION).toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder().setData(data).build())
                          .build();
    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(catalogService, never()).projectMovement(any());
  }
}
