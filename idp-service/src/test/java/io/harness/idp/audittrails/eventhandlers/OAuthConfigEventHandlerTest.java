/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.oauth.OAuthConfigCreateEvent.OAUTH_CONFIG_CREATED;
import static io.harness.idp.configmanager.events.oauth.OAuthConfigUpdateEvent.OAUTH_CONFIG_UPDATED;
import static io.harness.rule.OwnerRule.DEVESH;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
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
import io.harness.idp.configmanager.events.oauth.OAuthConfigCreateEvent;
import io.harness.idp.configmanager.events.oauth.OAuthConfigUpdateEvent;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class OAuthConfigEventHandlerTest {
  @Mock private AuditClientService auditClientService;
  @InjectMocks private OAuthConfigEventHandler oAuthConfigEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String AUTH_ID = "github-auth";
  private static final String EVENT_ID = "eventId";
  private static final Long CREATED_AT = 1234567890L;
  private ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleOAuthConfigCreateEvent() throws Exception {
    List<BackstageEnvVariable> envVariables = getBackstageEnvVariables();
    OAuthConfigCreateEvent createEvent = new OAuthConfigCreateEvent(ACCOUNT_ID, AUTH_ID, envVariables);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_CONFIG_CREATED)
                                  .eventData(objectMapper.writeValueAsString(createEvent))
                                  .resourceScope(createEvent.getResourceScope())
                                  .resource(createEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = oAuthConfigEventHandler.handle(outboxEvent);

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
  public void testHandleOAuthConfigUpdateEvent() throws Exception {
    List<BackstageEnvVariable> newEnvVariables = getBackstageEnvVariables();
    List<BackstageEnvVariable> oldEnvVariables = new ArrayList<>();

    BackstageEnvConfigVariable oldClientId = new BackstageEnvConfigVariable();
    oldClientId.setEnvName("AUTH_GITHUB_CLIENT_ID");
    oldClientId.setValue("old-client-id");
    oldClientId.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    oldEnvVariables.add(oldClientId);

    BackstageEnvSecretVariable oldClientSecret = new BackstageEnvSecretVariable();
    oldClientSecret.setEnvName("AUTH_GITHUB_CLIENT_SECRET");
    oldClientSecret.setHarnessSecretIdentifier("old-secret-id");
    oldClientSecret.setType(BackstageEnvVariable.TypeEnum.SECRET);
    oldEnvVariables.add(oldClientSecret);

    OAuthConfigUpdateEvent updateEvent =
        new OAuthConfigUpdateEvent(ACCOUNT_ID, AUTH_ID, newEnvVariables, oldEnvVariables);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_CONFIG_UPDATED)
                                  .eventData(objectMapper.writeValueAsString(updateEvent))
                                  .resourceScope(updateEvent.getResourceScope())
                                  .resource(updateEvent.getResource())
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    when(auditClientService.publishAudit(any(AuditEntry.class), any(GlobalContext.class))).thenReturn(true);

    boolean result = oAuthConfigEventHandler.handle(outboxEvent);

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

    oAuthConfigEventHandler.handle(outboxEvent);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleEventWithIOException() throws Exception {
    Resource resource = Resource.builder().type("OAUTH_CONFIG").identifier(AUTH_ID).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType(OAUTH_CONFIG_CREATED)
                                  .eventData("invalid json")
                                  .resourceScope(new AccountScope(ACCOUNT_ID))
                                  .resource(resource)
                                  .createdAt(CREATED_AT)
                                  .id(EVENT_ID)
                                  .globalContext(new GlobalContext())
                                  .build();

    boolean result = oAuthConfigEventHandler.handle(outboxEvent);

    assertThat(result).isFalse();
  }

  private List<BackstageEnvVariable> getBackstageEnvVariables() {
    List<BackstageEnvVariable> envVariables = new ArrayList<>();

    io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable clientId =
        new io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable();
    clientId.setEnvName("AUTH_GITHUB_CLIENT_ID");
    clientId.setValue("test-client-id");
    clientId.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    envVariables.add(clientId);

    io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable clientSecret =
        new io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable();
    clientSecret.setEnvName("AUTH_GITHUB_CLIENT_SECRET");
    clientSecret.setHarnessSecretIdentifier("test-secret-id");
    clientSecret.setType(BackstageEnvVariable.TypeEnum.SECRET);
    envVariables.add(clientSecret);

    return envVariables;
  }
}
