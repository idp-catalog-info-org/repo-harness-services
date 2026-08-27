/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPNextGenOutboxEventHandlerTest {
  @Mock private Map<String, OutboxEventHandler> outboxEventHandlerMap;
  @Mock private OutboxEventHandler mockEventHandler;
  @InjectMocks private IDPNextGenOutboxEventHandler idpNextGenOutboxEventHandler;

  private static final String RESOURCE_TYPE = "TEST_RESOURCE";
  private static final String EVENT_ID = "eventId";
  private static final Long CREATED_AT = 1234567890L;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithValidHandler() throws Exception {
    Resource resource = Resource.builder().type(RESOURCE_TYPE).identifier("test-id").build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("TEST_EVENT")
                                  .eventData("{}")
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(outboxEventHandlerMap.get(RESOURCE_TYPE)).thenReturn(mockEventHandler);
    when(mockEventHandler.handle(any(OutboxEvent.class))).thenReturn(true);

    boolean result = idpNextGenOutboxEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(outboxEventHandlerMap).get(RESOURCE_TYPE);
    verify(mockEventHandler).handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithNoHandler() throws Exception {
    Resource resource = Resource.builder().type(RESOURCE_TYPE).identifier("test-id").build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("TEST_EVENT")
                                  .eventData("{}")
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(outboxEventHandlerMap.get(RESOURCE_TYPE)).thenReturn(null);

    boolean result = idpNextGenOutboxEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
    verify(outboxEventHandlerMap).get(RESOURCE_TYPE);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithException() throws Exception {
    Resource resource = Resource.builder().type(RESOURCE_TYPE).identifier("test-id").build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("TEST_EVENT")
                                  .eventData("{}")
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(outboxEventHandlerMap.get(RESOURCE_TYPE)).thenThrow(new RuntimeException("Test exception"));

    boolean result = idpNextGenOutboxEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
    verify(outboxEventHandlerMap).get(RESOURCE_TYPE);
  }
}
