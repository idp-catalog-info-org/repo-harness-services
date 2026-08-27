/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.branding.handler;

import static io.harness.rule.OwnerRule.YASH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.branding.entities.Branding;
import io.harness.branding.mapper.BrandingMapper;
import io.harness.branding.outbox.branding.events.BrandingCreateEvent;
import io.harness.branding.outbox.branding.events.BrandingEvent;
import io.harness.branding.outbox.branding.events.BrandingUpdateEvent;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BrandingSettingsDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class BrandingEventHandlerTest extends CategoryTest {
  @Mock private AuditClientService auditClientService;
  @Mock private BrandingMapper brandingMapper;

  @InjectMocks private BrandingEventHandler brandingEventHandler;

  private static final String ACCOUNT_ID = "test-account";
  private ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleBrandingCreateEvent() throws Exception {
    Branding branding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    BrandingCreateEvent createEvent = new BrandingCreateEvent(ACCOUNT_ID, branding);
    BrandingSettingsDTO settingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(true);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingEvent.BRANDING_SETTINGS_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingSettingsDTO(branding)).thenReturn(settingsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = brandingEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(brandingMapper, times(1)).toBrandingSettingsDTO(branding);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleBrandingUpdateEvent() throws Exception {
    Branding oldBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(false).build();

    Branding newBranding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    BrandingUpdateEvent updateEvent = new BrandingUpdateEvent(ACCOUNT_ID, newBranding, oldBranding);
    BrandingSettingsDTO newSettingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(true);
    BrandingSettingsDTO oldSettingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(false);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingEvent.BRANDING_SETTINGS_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingSettingsDTO(newBranding)).thenReturn(newSettingsDTO);
    when(brandingMapper.toBrandingSettingsDTO(oldBranding)).thenReturn(oldSettingsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = brandingEventHandler.handle(outboxEvent);

    assertThat(result).isTrue();
    verify(brandingMapper, times(1)).toBrandingSettingsDTO(newBranding);
    verify(brandingMapper, times(1)).toBrandingSettingsDTO(oldBranding);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleUnsupportedEventType() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("UNSUPPORTED_EVENT")
                                  .eventData("{}")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    assertThatThrownBy(() -> brandingEventHandler.handle(outboxEvent))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Not supported event type UNSUPPORTED_EVENT");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleCreateEventWithInvalidJson() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingEvent.BRANDING_SETTINGS_CREATED)
                                  .eventData("invalid-json")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    boolean result = brandingEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventWithInvalidJson() {
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingEvent.BRANDING_SETTINGS_UPDATED)
                                  .eventData("invalid-json")
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    boolean result = brandingEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testHandleCreateEventAuditFailure() throws Exception {
    Branding branding = Branding.builder().accountIdentifier(ACCOUNT_ID).brandingOnSignInPage(true).build();

    BrandingCreateEvent createEvent = new BrandingCreateEvent(ACCOUNT_ID, branding);
    BrandingSettingsDTO settingsDTO = new BrandingSettingsDTO().brandingOnSignInPage(true);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(BrandingEvent.BRANDING_SETTINGS_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .globalContext(new GlobalContext())
                                  .createdAt(System.currentTimeMillis())
                                  .id("test-id")
                                  .build();

    when(brandingMapper.toBrandingSettingsDTO(branding)).thenReturn(settingsDTO);
    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(false);

    boolean result = brandingEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
    verify(brandingMapper, times(1)).toBrandingSettingsDTO(branding);
    verify(auditClientService, times(1)).publishAudit(any(AuditEntry.class), any(GlobalContext.class));
  }
}