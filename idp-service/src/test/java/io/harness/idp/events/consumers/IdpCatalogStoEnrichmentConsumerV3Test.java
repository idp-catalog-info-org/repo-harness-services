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
import io.harness.idp.catalog.helpers.STOHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
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
public class IdpCatalogStoEnrichmentConsumerV3Test extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "test-account-123";
  static final String TEST_PARENT_UNIQUE_ID = "test-parent-unique-id";
  static final String TEST_ENTITY_REF = "component:default/my-service";
  static final String TEST_ENTITY_KIND = "component";
  static final String TEST_ENTITY_IDENTIFIER = "my-service";

  @Mock ResourceLocker resourceLocker;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock STOHelper stoHelper;
  @Mock IdpCommonService idpCommonService;

  @InjectMocks @Spy IdpCatalogStoEnrichmentConsumerV3 consumer;

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
  public void testProcessMessage_Success_CreateAction() {
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_PARENT_UNIQUE_ID, TEST_ENTITY_KIND, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(stoHelper).populateSTOData(any());

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, times(1)).populateSTOData(mockCatalogEntity);
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
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(stoHelper).populateSTOData(any());

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, times(1)).populateSTOData(mockCatalogEntity);
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
    verify(stoHelper, never()).populateSTOData(any());
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.empty());

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, never()).populateSTOData(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_NonCoreKind_SkipsEnrichment() {
    // Given
    CatalogEntity nonCoreEntity = InlineCatalogEntity.builder()
                                      .accountIdentifier(TEST_ACCOUNT_ID)
                                      .parentUniqueId(TEST_PARENT_UNIQUE_ID)
                                      .kind("CustomKind")
                                      .identifier(TEST_ENTITY_IDENTIFIER)
                                      .build();

    ByteString data = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_ID)
                          .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                          .setEntityRef("CustomKind:default/my-service")
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(nonCoreEntity));

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, never()).populateSTOData(any());
    verify(idpCommonService, never()).idpStoEnabled(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_StoDisabled_SkipsEnrichment() {
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(false);

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, never()).populateSTOData(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessMessage_StoEnrichmentFails_StillReturnsTrue() {
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doThrow(new RuntimeException("STO API failed")).when(stoHelper).populateSTOData(any());

    // When - STO enrichment is best-effort, so failure should not fail message processing
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleStoEnrichment_Success() {
    // Given
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                     .setAccountIdentifier(TEST_ACCOUNT_ID)
                                                     .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                                                     .setEntityRef(TEST_ENTITY_REF)
                                                     .setAction(CREATE_ACTION)
                                                     .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             TEST_PARENT_UNIQUE_ID, TEST_ENTITY_KIND, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(stoHelper).populateSTOData(any());

    // When
    consumer.handleStoEnrichment(event);

    // Then
    verify(stoHelper, times(1)).populateSTOData(mockCatalogEntity);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleStoEnrichment_StoThrows_DoesNotPropagate() {
    // Given
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                     .setAccountIdentifier(TEST_ACCOUNT_ID)
                                                     .setParentUniqueId(TEST_PARENT_UNIQUE_ID)
                                                     .setEntityRef(TEST_ENTITY_REF)
                                                     .setAction(CREATE_ACTION)
                                                     .build();

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doThrow(new RuntimeException("STO failed")).when(stoHelper).populateSTOData(any());

    // When - should not throw, best-effort enrichment
    consumer.handleStoEnrichment(event);

    // Then - no exception propagated
    verify(stoHelper, times(1)).populateSTOData(mockCatalogEntity);
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
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, CREATE_ACTION))
                            .setData(ByteString.copyFromUtf8("invalid protobuf"))
                            .build())
            .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then - returns false due to parsing exception
    assertThat(result).isFalse();
    verify(stoHelper, never()).populateSTOData(any());
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
                          .setAction(CREATE_ACTION)
                          .build()
                          .toByteString();

    Message message = Message.newBuilder()
                          .setId("test-message-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ENTITY_TYPE, "WRONG_TYPE", ACTION, CREATE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();

    // When
    boolean result = consumer.processMessage(message);

    // Then
    assertThat(result).isTrue();
    verify(stoHelper, never()).populateSTOData(any());
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

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(mockCatalogEntity));
    when(idpCommonService.idpStoEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(stoHelper).populateSTOData(any());

    // When - process twice
    boolean result1 = consumer.processMessage(message);
    boolean result2 = consumer.processMessage(message);

    // Then - both succeed, idempotent
    assertThat(result1).isTrue();
    assertThat(result2).isTrue();
    verify(stoHelper, times(2)).populateSTOData(any());
  }
}
