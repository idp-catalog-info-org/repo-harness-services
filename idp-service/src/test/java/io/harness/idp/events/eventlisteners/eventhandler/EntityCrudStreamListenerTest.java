/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler;

import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.events.eventlisteners.factory.EventMessageHandlerFactory;
import io.harness.idp.events.eventlisteners.messagehandler.ConnectorMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.OrganizationMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.ProjectMessageHandler;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class EntityCrudStreamListenerTest extends CategoryTest {
  @Mock EventMessageHandlerFactory eventMessageHandlerFactory;
  @Mock ResourceLocker resourceLocker;
  @Mock IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @InjectMocks EntityCrudStreamListener entityCrudStreamListener;

  @Mock ConnectorMessageHandler connectorMessageHandler;
  @Mock OrganizationMessageHandler organizationMessageHandler;
  @Mock ProjectMessageHandler projectMessageHandler;

  public static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_CONNECTOR_ID = "test-connector-id";
  private static final String TEST_ORG_ID = "test-org-id";
  private static final String TEST_PROJECT_ID = "test-project-id";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    assertTrue(entityCrudStreamListener.handleMessage(null));

    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(AccountEntityChangeDTO.newBuilder().setAccountId(TEST_ACCOUNT_ID).build().toByteString())
                    .build())
            .build();
    assertTrue(entityCrudStreamListener.handleMessage(message));
    message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(
                        ImmutableMap.of("accountId", TEST_ACCOUNT_ID, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.CONNECTOR_ENTITY))
                    .setData(AccountEntityChangeDTO.newBuilder().setAccountId(TEST_ACCOUNT_ID).build().toByteString())
                    .build())
            .build();
    assertTrue(entityCrudStreamListener.handleMessage(message));

    message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(EntityChangeDTO.newBuilder()
                                 .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_ID))
                                 .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                 .build()
                                 .toByteString())
                    .build())
            .build();
    when(eventMessageHandlerFactory.getEventMessageHandler(any())).thenReturn(connectorMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    assertTrue(entityCrudStreamListener.handleMessage(message));

    message = Message.newBuilder()
                  .setMessage(
                      io.harness.eventsframework.producer.Message.newBuilder()
                          .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                              EventsFrameworkMetadataConstants.ENTITY_TYPE,
                              EventsFrameworkMetadataConstants.ASYNC_CATALOG_IMPORT_ENTITY,
                              EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                          .setData(EntityChangeDTO.newBuilder()
                                       .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                       .build()
                                       .toByteString())
                          .build())
                  .build();
    when(resourceLocker.acquireLock(any(), anyLong())).thenReturn(RedisAcquiredLock.builder().build());
    assertTrue(entityCrudStreamListener.handleMessage(message));

    when(eventMessageHandlerFactory.getEventMessageHandler(any())).thenReturn(null);
    assertTrue(entityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageOrganizationEntity() {
    OrganizationEntityChangeDTO orgDto = OrganizationEntityChangeDTO.newBuilder()
                                             .setAccountIdentifier(TEST_ACCOUNT_ID)
                                             .setIdentifier(TEST_ORG_ID)
                                             .build();
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE,
                                              EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(orgDto.toByteString())
                                          .build())
                          .build();
    when(eventMessageHandlerFactory.getEventMessageHandler(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY))
        .thenReturn(organizationMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    assertTrue(entityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageProjectEntity() {
    ProjectEntityChangeDTO projectDto = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PROJECT_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.CREATE_ACTION))
                    .setData(projectDto.toByteString())
                    .build())
            .build();
    when(eventMessageHandlerFactory.getEventMessageHandler(EventsFrameworkMetadataConstants.PROJECT_ENTITY))
        .thenReturn(projectMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(idpServiceMiscRedisProducer).publishProjectEventToRedis(any(), any());
    assertTrue(entityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleMessageProjectEntityRePublishesToDedicatedStream() {
    ProjectEntityChangeDTO projectDto = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PROJECT_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.CREATE_ACTION))
                    .setData(projectDto.toByteString())
                    .build())
            .build();

    when(eventMessageHandlerFactory.getEventMessageHandler(EventsFrameworkMetadataConstants.PROJECT_ENTITY))
        .thenReturn(projectMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(projectMessageHandler).handleMessage(any(), any(), any());
    doNothing().when(idpServiceMiscRedisProducer).publishProjectEventToRedis(any(), any());

    boolean result = entityCrudStreamListener.handleMessage(message);

    assertTrue(result);
    verify(projectMessageHandler, times(1))
        .handleMessage(eq(message), any(), eq(EventsFrameworkMetadataConstants.CREATE_ACTION));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleMessageProjectEntityRePublishFailsDoesNotFailMainProcessing() {
    ProjectEntityChangeDTO projectDto = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PROJECT_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.UPDATE_ACTION))
                    .setData(projectDto.toByteString())
                    .build())
            .build();

    when(eventMessageHandlerFactory.getEventMessageHandler(EventsFrameworkMetadataConstants.PROJECT_ENTITY))
        .thenReturn(projectMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(projectMessageHandler).handleMessage(any(), any(), any());
    doThrow(new RuntimeException("Re-publish failed"))
        .when(idpServiceMiscRedisProducer)
        .publishProjectEventToRedis(any(), any());
    boolean result = entityCrudStreamListener.handleMessage(message);
    assertTrue(result);
    verify(projectMessageHandler, times(1)).handleMessage(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleMessageNonProjectEntityDoesNotRePublish() {
    OrganizationEntityChangeDTO orgDto = OrganizationEntityChangeDTO.newBuilder()
                                             .setAccountIdentifier(TEST_ACCOUNT_ID)
                                             .setIdentifier(TEST_ORG_ID)
                                             .build();
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE,
                                              EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.CREATE_ACTION))
                                          .setData(orgDto.toByteString())
                                          .build())
                          .build();

    when(eventMessageHandlerFactory.getEventMessageHandler(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY))
        .thenReturn(organizationMessageHandler);
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(organizationMessageHandler).handleMessage(any(), any(), any());

    boolean result = entityCrudStreamListener.handleMessage(message);
    assertTrue(result);
    verify(organizationMessageHandler, times(1)).handleMessage(any(), any(), any());
    verify(idpServiceMiscRedisProducer, never()).publishProjectEventToRedis(any(), any());
  }
}
