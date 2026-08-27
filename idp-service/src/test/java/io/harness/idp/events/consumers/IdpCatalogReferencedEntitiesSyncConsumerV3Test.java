/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY_V3;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
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
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEventV3;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class IdpCatalogReferencedEntitiesSyncConsumerV3Test extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-123";
  static final String TEST_PARENT_UNIQUE_ID = "test-parent-unique-id";
  static final String TEST_ENTITY_REF = "Component:default/my-service";
  static final String TEST_ENTITY_KIND = "Component";
  static final String TEST_ENTITY_IDENTIFIER = "my-service";

  @Mock ResourceLocker resourceLocker;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock HarnessToIDPHelper harnessToIDPHelper;

  @InjectMocks @Spy IdpCatalogReferencedEntitiesSyncConsumerV3 consumer;

  CatalogEntity mockCatalogEntity;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    mockCatalogEntity = InlineCatalogEntity.builder()
                            .accountIdentifier(TEST_ACCOUNT_ID)
                            .parentUniqueId(TEST_PARENT_UNIQUE_ID)
                            .kind(TEST_ENTITY_KIND)
                            .identifier(TEST_ENTITY_IDENTIFIER)
                            .build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_Success_UpdateAction() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(UPDATE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_PARENT_UNIQUE_ID, TEST_ENTITY_KIND, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Optional.of(mockCatalogEntity));
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), eq(TEST_ACCOUNT_ID), eq(UPDATE_ACTION));

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(harnessToIDPHelper, times(1)).harnessToIdpSync(List.of(mockCatalogEntity), TEST_ACCOUNT_ID, UPDATE_ACTION);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_SkipsCreateAction() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(CREATE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_SkipsDeleteAction() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(DELETE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, DELETE_ACTION))
                            .setData(data)
                            .build())
            .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_EntityNotFound() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(UPDATE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.empty());

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_SyncFails() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(UPDATE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    doThrow(new RuntimeException("Sync failed")).when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_InvalidProtobuf() {
    // Given
    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, UPDATE_ACTION))
                            .setData(ByteString.copyFromUtf8("invalid protobuf"))
                            .build())
            .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then - returns false due to parsing exception
    assertThat(result).isFalse();
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_WrongEntityType() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(UPDATE_ACTION)
                          .build()
                          .toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ENTITY_TYPE, "WRONG_TYPE", ACTION, UPDATE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleReferencedEntitiesSync_Success() {
    // Given
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                     .setAccountIdentifier(TEST_ACCOUNT_ID)
                                                     .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                                                     .setEntityRef(TEST_ENTITY_REF)
                                                     .setAction(UPDATE_ACTION)
                                                     .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_PARENT_UNIQUE_ID, TEST_ENTITY_KIND, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Optional.of(mockCatalogEntity));
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), eq(TEST_ACCOUNT_ID), eq(UPDATE_ACTION));

    // When
    consumer.handleReferencedEntitiesSync(event);

    // Then
    verify(harnessToIDPHelper, times(1)).harnessToIdpSync(List.of(mockCatalogEntity), TEST_ACCOUNT_ID, UPDATE_ACTION);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleReferencedEntitiesSync_EntityNotFound_DoesNotThrow() {
    // Given
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                     .setAccountIdentifier(TEST_ACCOUNT_ID)
                                                     .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                                                     .setEntityRef(TEST_ENTITY_REF)
                                                     .setAction(UPDATE_ACTION)
                                                     .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.empty());

    // When - should not throw
    consumer.handleReferencedEntitiesSync(event);

    // Then
    verify(harnessToIDPHelper, never()).harnessToIdpSync(any(), any(), any());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleReferencedEntitiesSync_SyncThrows_PropagatesException() {
    // Given
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                     .setAccountIdentifier(TEST_ACCOUNT_ID)
                                                     .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                                                     .setEntityRef(TEST_ENTITY_REF)
                                                     .setAction(UPDATE_ACTION)
                                                     .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    doThrow(new RuntimeException("Sync failed")).when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());

    // When - should throw
    consumer.handleReferencedEntitiesSync(event);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_Idempotency_SameMessageTwice() {
    // Given
    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef(TEST_ENTITY_REF)
                          .setAction(UPDATE_ACTION)
                          .build()
                          .toByteString();

    Message message =
        Message.newBuilder()
            .setId("test-message-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());

    // When - process twice
    boolean result1 = consumer.processMessage(message);
    boolean result2 = consumer.processMessage(message);

    // Then
    assertThat(result1).isTrue();
    assertThat(result2).isTrue();
    verify(harnessToIDPHelper, times(2)).harnessToIdpSync(any(), any(), any());
  }
}
