/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEvent;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.User;

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
public class IdpCatalogEntitiesSyncCaptureEventConsumerTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_IDENTIFIER = "testIdentifier";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";

  @Mock ResourceLocker resourceLocker;
  @Mock BackstageService backstageService;

  @InjectMocks @Spy IdpCatalogEntitiesSyncCaptureEventConsumer idpCatalogEntitiesSyncCaptureEventConsumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    assertTrue(true);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessage() {
    ByteString data = IdpCatalogEntitiesSyncCaptureEvent.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .setIdentifier(TEST_IDENTIFIER)
                          .setAction(CREATE_ACTION)
                          .setType(BackstageHarnessSyncRequest.TypeEnum.ENTITY.value())
                          .setUserUuid(TEST_USER_IDENTIFIER)
                          .setUserEmail(TEST_USER_EMAIL)
                          .setUserName(TEST_USER_NAME)
                          .setSyncMode("sync")
                          .build()
                          .toByteString();
    Message message =
        Message.newBuilder()
            .setId("test-event-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    when(backstageService.syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY,
             TEST_IDENTIFIER, CREATE_ACTION, "sync",
             new User().uuid(TEST_USER_IDENTIFIER).email(TEST_USER_EMAIL).name(TEST_USER_NAME)))
        .thenReturn(true);

    boolean result = idpCatalogEntitiesSyncCaptureEventConsumer.processMessage(message);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessageValidationFailure() {
    ByteString data = IdpCatalogEntitiesSyncCaptureEvent.newBuilder().build().toByteString();
    Message message = Message.newBuilder()
                          .setId("test-event-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ACTION, CREATE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());

    boolean result = idpCatalogEntitiesSyncCaptureEventConsumer.processMessage(message);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessageError() {
    ByteString data = IdpCatalogEntitiesSyncCaptureEvent.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .setIdentifier(TEST_IDENTIFIER)
                          .setAction(CREATE_ACTION)
                          .setType(IDP_CATALOG_ENTITY)
                          .setUserUuid(TEST_USER_IDENTIFIER)
                          .setUserEmail(TEST_USER_EMAIL)
                          .setUserName(TEST_USER_NAME)
                          .build()
                          .toByteString();
    Message message =
        Message.newBuilder()
            .setId("test-event-id")
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putAllMetadata(Map.of(ENTITY_TYPE, IDP_CATALOG_ENTITY, ACTION, CREATE_ACTION))
                            .setData(data)
                            .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    willAnswer(invocation -> { throw new Exception("Exception Throw"); })
        .given(backstageService)
        .syncByType(TEST_ACCOUNT_IDENTIFIER, BackstageHarnessSyncRequest.TypeEnum.ENTITY, TEST_IDENTIFIER,
            CREATE_ACTION, "sync", new User());

    boolean result = idpCatalogEntitiesSyncCaptureEventConsumer.processMessage(message);
    assertFalse(result);
  }
}
