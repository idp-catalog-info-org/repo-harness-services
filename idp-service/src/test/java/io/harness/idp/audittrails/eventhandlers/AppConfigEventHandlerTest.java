/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.appconfigs.AppConfigCreateEvent.APP_CONFIG_CREATED;
import static io.harness.idp.configmanager.events.appconfigs.AppConfigUpdateEvent.APP_CONFIG_UPDATED;
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
import io.harness.idp.configmanager.events.appconfigs.AppConfigCreateEvent;
import io.harness.idp.configmanager.events.appconfigs.AppConfigUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AppConfigEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private AppConfigEventHandler appConfigEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String CONFIG_ID = "appConfigId";
  private static final String CONFIG_NAME = "App Config";
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
  public void testHandleAppConfigCreateEvent() throws Exception {
    AppConfig appConfig = getAppConfig();
    AppConfigCreateEvent createEvent = new AppConfigCreateEvent(ACCOUNT_ID, appConfig);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(APP_CONFIG_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = appConfigEventHandler.handle(outboxEvent);

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
  public void testHandleAppConfigUpdateEvent() throws Exception {
    AppConfig newAppConfig = getAppConfig();
    AppConfig oldAppConfig = getAppConfig();
    oldAppConfig.setConfigName("Old App Config");
    String oldConfigs = "{\"oldKey\": \"oldValue\"}";
    oldAppConfig.setConfigs(oldConfigs);

    AppConfigUpdateEvent updateEvent = new AppConfigUpdateEvent(ACCOUNT_ID, newAppConfig, oldAppConfig);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(APP_CONFIG_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = appConfigEventHandler.handle(outboxEvent);

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

    appConfigEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(APP_CONFIG_CREATED)
                                  .eventData("invalid json")
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = appConfigEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private AppConfig getAppConfig() {
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(CONFIG_ID);
    appConfig.setConfigName(CONFIG_NAME);
    String configs = "{\"enabled\": true, \"maxRetries\": 3}";
    appConfig.setConfigs(configs);
    return appConfig;
  }
}
