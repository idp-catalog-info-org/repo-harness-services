/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent.CATALOG_CUSTOM_PROPERTY_CREATED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent.CATALOG_CUSTOM_PROPERTY_DELETED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyDisableEvent.CATALOG_CUSTOM_PROPERTY_DISABLED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyEnableEvent.CATALOG_CUSTOM_PROPERTY_ENABLED;
import static io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent.CATALOG_CUSTOM_PROPERTY_UPDATED;
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
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDisableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyEnableEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private CatalogCustomPropertiesEventHandler catalogCustomPropertiesEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ENTITY_REF = "component:default/test";
  private static final String FIELD = "customField";
  private static final String VALUE = "customValue";
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
  public void testHandleCatalogCustomPropertyCreateEvent() throws Exception {
    CatalogCustomPropertyEntity entity = getCatalogCustomPropertyEntity();
    CatalogCustomPropertyCreateEvent createEvent = new CatalogCustomPropertyCreateEvent(ACCOUNT_ID, entity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

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
  public void testHandleCatalogCustomPropertyUpdateEvent() throws Exception {
    CatalogCustomPropertyEntity newEntity = getCatalogCustomPropertyEntity();
    CatalogCustomPropertyEntity oldEntity = getCatalogCustomPropertyEntity();
    oldEntity.setValue("oldValue");

    CatalogCustomPropertyUpdateEvent updateEvent =
        new CatalogCustomPropertyUpdateEvent(ACCOUNT_ID, newEntity, oldEntity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

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
  public void testHandleCatalogCustomPropertyDeleteEvent() throws Exception {
    CatalogCustomPropertyEntity entity = getCatalogCustomPropertyEntity();
    CatalogCustomPropertyDeleteEvent deleteEvent = new CatalogCustomPropertyDeleteEvent(ACCOUNT_ID, entity);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_DELETED)
                                  .eventData(objectMapper.writeValueAsString(deleteEvent))
                                  .resourceScope(deleteEvent.getResourceScope())
                                  .resource(deleteEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

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

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCatalogCustomPropertyEnableEvent() throws Exception {
    CatalogCustomPropertyEnableEvent enableEvent = new CatalogCustomPropertyEnableEvent(ACCOUNT_ID);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_ENABLED)
                                  .eventData(objectMapper.writeValueAsString(enableEvent))
                                  .resourceScope(enableEvent.getResourceScope())
                                  .resource(enableEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.ENABLED);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleCatalogCustomPropertyDisableEvent() throws Exception {
    CatalogCustomPropertyDisableEvent disableEvent = new CatalogCustomPropertyDisableEvent(ACCOUNT_ID);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_DISABLED)
                                  .eventData(objectMapper.writeValueAsString(disableEvent))
                                  .resourceScope(disableEvent.getResourceScope())
                                  .resource(disableEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    ArgumentCaptor<AuditEntry> auditEntryCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClientService).publishAudit(auditEntryCaptor.capture(), any(GlobalContext.class));

    AuditEntry capturedEntry = auditEntryCaptor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo(Action.DISABLED);
    assertThat(capturedEntry.getInsertId()).isEqualTo(EVENT_ID);
    assertThat(capturedEntry.getTimestamp()).isEqualTo(CREATED_AT);
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

    catalogCustomPropertiesEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(CATALOG_CUSTOM_PROPERTY_CREATED)
                                  .eventData("invalid json")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = catalogCustomPropertiesEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private CatalogCustomPropertyEntity getCatalogCustomPropertyEntity() {
    return CatalogCustomPropertyEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .entityRef(ENTITY_REF)
        .field(FIELD)
        .value(VALUE)
        .build();
  }
}
