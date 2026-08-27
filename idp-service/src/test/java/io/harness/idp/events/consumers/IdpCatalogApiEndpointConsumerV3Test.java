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
import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
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
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.util.Collections;
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
public class IdpCatalogApiEndpointConsumerV3Test extends CategoryTest {
  static final String ACCOUNT_ID = "acc-123";
  static final String PARENT_UNIQUE_ID = "parent-abc";
  static final String IDENTIFIER = "payments-api";
  static final String ENTITY_REF = "api:account/payments-api";

  @Mock ResourceLocker resourceLocker;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock ApiEndpointProcessor apiEndpointProcessor;
  @Mock IdpCommonService idpCommonService;

  @InjectMocks @Spy IdpCatalogApiEndpointConsumerV3 consumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_create_apiKind_ffOn_processes() throws Exception {
    Message message = buildMessage(CREATE_ACTION, ENTITY_REF);
    CatalogEntity entity =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_ID).kind("api").identifier(IDENTIFIER).build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.of(entity));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_ID)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(entity)).thenReturn(emptySuccess());

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor).processEntity(entity);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_update_apiKind_ffOn_processes() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, ENTITY_REF);
    CatalogEntity entity =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_ID).kind("api").identifier(IDENTIFIER).build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.of(entity));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_ID)).thenReturn(true);
    when(apiEndpointProcessor.processEntity(entity)).thenReturn(emptySuccess());

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor).processEntity(entity);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_ffOff_skipsProcessing() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, ENTITY_REF);
    CatalogEntity entity =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_ID).kind("api").identifier(IDENTIFIER).build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.of(entity));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_ID)).thenReturn(false);

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_ffCheckThrows_acksWithoutProcessing() throws Exception {
    // FF service failure must skip-and-ack, not redeliver, to avoid a redelivery storm.
    Message message = buildMessage(UPDATE_ACTION, ENTITY_REF);
    CatalogEntity entity =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_ID).kind("api").identifier(IDENTIFIER).build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.of(entity));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_ID))
        .thenThrow(new RuntimeException("FF service down"));

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_nonApiKind_skipsProcessing() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, "component:account/svc-1");
    CatalogEntity nonApi = InlineCatalogEntity.builder().kind("component").identifier("svc-1").build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "component", "svc-1"))
        .thenReturn(Optional.of(nonApi));

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_deleteAction_skipped() throws Exception {
    Message message = buildMessage(DELETE_ACTION, ENTITY_REF);

    assertThat(consumer.processMessage(message)).isTrue();
    verify(catalogEntityRepository, never()).findByParentUniqueIdAndKindAndIdentifier(any(), any(), any());
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_entityNotFound_acked() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, ENTITY_REF);

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.empty());

    assertThat(consumer.processMessage(message)).isTrue();
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_malformedEntityRef_acked() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, "not-a-valid-ref");

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());

    assertThat(consumer.processMessage(message)).isTrue();
    verify(catalogEntityRepository, never()).findByParentUniqueIdAndKindAndIdentifier(any(), any(), any());
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_processorThrows_returnsFalse() throws Exception {
    Message message = buildMessage(UPDATE_ACTION, ENTITY_REF);
    CatalogEntity entity =
        InlineCatalogEntity.builder().accountIdentifier(ACCOUNT_ID).kind("api").identifier(IDENTIFIER).build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(PARENT_UNIQUE_ID, "api", IDENTIFIER))
        .thenReturn(Optional.of(entity));
    when(idpCommonService.idpApiEndpointExtractionEnabled(ACCOUNT_ID)).thenReturn(true);
    willAnswer(invocation -> { throw new RuntimeException("boom"); }).given(apiEndpointProcessor).processEntity(entity);

    assertThat(consumer.processMessage(message)).isFalse();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testProcessMessage_wrongEntityType_skipped() throws Exception {
    ByteString data = eventData(UPDATE_ACTION, ENTITY_REF);
    Message message =
        Message.newBuilder()
            .setId("test-event-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, "some_other_stream", ACTION, UPDATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    assertThat(consumer.processMessage(message)).isTrue();
    verify(catalogEntityRepository, never()).findByParentUniqueIdAndKindAndIdentifier(any(), any(), any());
    verify(apiEndpointProcessor, never()).processEntity(any());
  }

  private static ProcessingOutcome emptySuccess() {
    return ProcessingOutcome.success(false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  private static ByteString eventData(String action, String entityRef) {
    return IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
        .setAccountIdentifier(ACCOUNT_ID)
        .setParentUniqueId(PARENT_UNIQUE_ID)
        .setEntityRef(entityRef)
        .setAction(action)
        .build()
        .toByteString();
  }

  private Message buildMessage(String action, String entityRef) {
    return Message.newBuilder()
        .setId("test-event-id-" + action)
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY_V3, ACTION, action))
                        .setData(eventData(action, entityRef))
                        .build())
        .build();
  }
}
