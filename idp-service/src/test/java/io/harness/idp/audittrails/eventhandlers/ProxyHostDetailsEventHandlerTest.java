/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent.PROXY_HOST_CREATED;
import static io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent.PROXY_HOST_DELETED;
import static io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent.PROXY_HOST_UPDATED;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ProxyHostDetailsEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private ProxyHostDetailsEventHandler proxyHostDetailsEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PLUGIN_ID = "pluginId";
  private static final String HOST = "https://api.example.com";
  private static final String EVENT_ID = "eventId";
  private static final Long CREATED_AT = 1234567890L;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleProxyHostCreateEvent() throws Exception {
    ProxyHostDetail detail = getProxyHostDetail();
    ProxyHostCreateEvent createEvent = new ProxyHostCreateEvent(ACCOUNT_ID, detail);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(PROXY_HOST_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = proxyHostDetailsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.CREATE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNotNull();
    assertThat(capturedEntry.getOldYaml()).isNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleProxyHostUpdateEvent() throws Exception {
    ProxyHostDetail newDetail = getProxyHostDetail();
    ProxyHostDetail oldDetail = getProxyHostDetail();
    oldDetail.setHost("https://old-api.example.com");

    ProxyHostUpdateEvent updateEvent = new ProxyHostUpdateEvent(ACCOUNT_ID, newDetail, oldDetail);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(PROXY_HOST_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = proxyHostDetailsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.UPDATE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNotNull();
    assertThat(capturedEntry.getOldYaml()).isNotNull();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleProxyHostDeleteEvent() throws Exception {
    ProxyHostDetail detail = getProxyHostDetail();
    ProxyHostDeleteEvent deleteEvent = new ProxyHostDeleteEvent(ACCOUNT_ID, detail);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(PROXY_HOST_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = proxyHostDetailsEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.DELETE);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
    assertThat(capturedEntry.getNewYaml()).isNull();
    assertThat(capturedEntry.getOldYaml()).isNotNull();
  }

  @Test(expected = io.harness.exception.InvalidArgumentsException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUnsupportedEventType() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("UNSUPPORTED_EVENT")
                                  .eventData("{}")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    proxyHostDetailsEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(PROXY_HOST_CREATED)
                                  .eventData("invalid json")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = proxyHostDetailsEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private ProxyHostDetail getProxyHostDetail() {
    ProxyHostDetail detail = new ProxyHostDetail();
    detail.setPluginId(PLUGIN_ID);
    detail.setHost(HOST);
    detail.setSelectors(Arrays.asList("delegate1", "delegate2"));
    return detail;
  }
}
