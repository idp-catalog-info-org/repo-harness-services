/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.TOKEN_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.KARAN_GARG;
import static io.harness.rule.OwnerRule.SOWMYA;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.event.Event;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenRequest;
import io.harness.ng.core.events.TokenCreateEvent;
import io.harness.ng.core.events.TokenDeleteEvent;
import io.harness.ng.core.events.TokenExpireEvent;
import io.harness.ng.core.events.TokenUpdateEvent;
import io.harness.ng.core.utils.TokenNotificationUtils;
import io.harness.notification.entities.NotificationEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class TokenEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Producer producer;
  private AuditClientService auditClientService;
  private TokenEventHandler TokenEventHandler;
  private TokenNotificationUtils tokenNotificationUtils;

  /**
   * Test data holder for common test identifiers
   */
  private static class TestData {
    final String accountIdentifier;
    final String orgIdentifier;
    final String projectIdentifier;
    final String uniqueId;
    final String identifier;
    final String name;

    TestData() {
      this.accountIdentifier = randomAlphabetic(10);
      this.orgIdentifier = randomAlphabetic(10);
      this.projectIdentifier = randomAlphabetic(10);
      this.uniqueId = randomAlphabetic(10);
      this.identifier = randomAlphabetic(10);
      this.name = randomAlphabetic(10);
    }
  }

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    producer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    tokenNotificationUtils = mock(TokenNotificationUtils.class);
    TokenEventHandler = spy(new TokenEventHandler(producer, auditClientService, tokenNotificationUtils));
  }

  private TokenDTO getTokenDTO(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, String identifier, String uniqueId) {
    return TokenDTO.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .accountIdentifier(accountIdentifier)
        .identifier(identifier)
        .uniqueId(identifier)
        .parentUniqueId(uniqueId)
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    TokenDTO TokenDTO = getTokenDTO(accountIdentifier, projectIdentifier, orgIdentifier, identifier, uniqueId);
    TokenCreateEvent TokenCreateEvent = new TokenCreateEvent(TokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(TokenCreateEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenCreated")
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .resourceScope(TokenCreateEvent.getResourceScope())
                                  .resource(TokenCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(TokenRequest.builder().token(TokenDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, CREATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    TokenDTO oldTokenDTO = getTokenDTO(accountIdentifier, projectIdentifier, orgIdentifier, identifier, uniqueId);
    TokenDTO newTokenDTO = getTokenDTO(accountIdentifier, projectIdentifier, orgIdentifier, identifier, uniqueId);
    TokenUpdateEvent tokenUpdateEvent = new TokenUpdateEvent(oldTokenDTO, newTokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenUpdated")
                                  .eventData(eventData)
                                  .resourceScope(tokenUpdateEvent.getResourceScope())
                                  .resource(tokenUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(TokenRequest.builder().token(oldTokenDTO).build());
    String newYaml = getYamlString(TokenRequest.builder().token(newTokenDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, UPDATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    TokenDTO TokenDTO = getTokenDTO(accountIdentifier, projectIdentifier, orgIdentifier, identifier, uniqueId);

    TokenDeleteEvent TokenDeleteEvent = new TokenDeleteEvent(TokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(TokenDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenDeleted")
                                  .eventData(eventData)
                                  .resourceScope(TokenDeleteEvent.getResourceScope())
                                  .resource(TokenDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(TokenRequest.builder().token(TokenDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, messageArgumentCaptor, auditEntryArgumentCaptor);

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, DELETE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<Message> messageArgumentCaptor,
      ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertMessage(Message message, String accountIdentifier, String action) {
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(TOKEN_ENTITY, metadataMap.get(ENTITY_TYPE));
    assertEquals(action, metadataMap.get(ACTION));
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.TOKEN, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }

  private TokenDTO getServiceAccountTokenDTO(String accountIdentifier, String projectIdentifier, String orgIdentifier,
      String identifier, String uniqueId, String name) {
    return TokenDTO.builder()
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .accountIdentifier(accountIdentifier)
        .identifier(identifier)
        .name(name)
        .uniqueId(uniqueId)
        .parentUniqueId(randomAlphabetic(10))
        .parentIdentifier(randomAlphabetic(10))
        .apiKeyIdentifier(randomAlphabetic(10))
        .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
        .username(randomAlphabetic(10))
        .validFrom(System.currentTimeMillis())
        .validTo(System.currentTimeMillis() + 86400000L)
        .build();
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenTokenCreateEvent_ThenNotificationSentWithUtilsMethod() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getServiceAccountTokenDTO(
        data.accountIdentifier, data.projectIdentifier, data.orgIdentifier, data.identifier, data.uniqueId, data.name);
    TokenCreateEvent tokenCreateEvent = new TokenCreateEvent(tokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenCreated")
                                  .eventData(eventData)
                                  .resourceScope(tokenCreateEvent.getResourceScope())
                                  .resource(tokenCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            eq(tokenDTO), eq(NotificationEvent.TOKEN_CREATED), eq(NotificationEvent.TOKEN_CREATED.name()), isNull());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenTokenRotateEvent_ThenNotificationSentWithUtilsMethod() throws IOException {
    TestData data = new TestData();
    String oldIdentifier = randomAlphabetic(10);
    String rotatedIdentifier = "rotated_" + randomAlphabetic(10);
    TokenDTO oldTokenDTO = getServiceAccountTokenDTO(
        data.accountIdentifier, data.projectIdentifier, data.orgIdentifier, oldIdentifier, data.uniqueId, data.name);
    TokenDTO newTokenDTO = getServiceAccountTokenDTO(data.accountIdentifier, data.projectIdentifier, data.orgIdentifier,
        rotatedIdentifier, data.uniqueId, data.name);

    TokenUpdateEvent tokenUpdateEvent = new TokenUpdateEvent(oldTokenDTO, newTokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenUpdated")
                                  .eventData(eventData)
                                  .resourceScope(tokenUpdateEvent.getResourceScope())
                                  .resource(tokenUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    assertEquals(ApiKeyType.SERVICE_ACCOUNT, newTokenDTO.getApiKeyType());

    // After rotation, the handler replaces the new token's identifier and name with the old token's values
    // so users recognize which token was rotated. Verify with the expected modified state.
    ArgumentCaptor<TokenDTO> tokenCaptor = ArgumentCaptor.forClass(TokenDTO.class);
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(tokenCaptor.capture(), eq(NotificationEvent.TOKEN_ROTATED),
            eq(NotificationEvent.TOKEN_ROTATED.name()), isNull());
    TokenDTO capturedToken = tokenCaptor.getValue();
    assertEquals(oldIdentifier, capturedToken.getIdentifier());
    assertEquals(data.name, capturedToken.getName());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenTokenUpdateEvent_ThenNotificationSentWithUtilsMethod() throws IOException {
    TestData data = new TestData();
    String updatedName = randomAlphabetic(10);
    TokenDTO oldTokenDTO = getServiceAccountTokenDTO(
        data.accountIdentifier, data.projectIdentifier, data.orgIdentifier, data.identifier, data.uniqueId, data.name);
    TokenDTO newTokenDTO = getServiceAccountTokenDTO(data.accountIdentifier, data.projectIdentifier, data.orgIdentifier,
        data.identifier, data.uniqueId, updatedName);
    TokenUpdateEvent tokenUpdateEvent = new TokenUpdateEvent(oldTokenDTO, newTokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenUpdated")
                                  .eventData(eventData)
                                  .resourceScope(tokenUpdateEvent.getResourceScope())
                                  .resource(tokenUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    assertEquals(ApiKeyType.SERVICE_ACCOUNT, newTokenDTO.getApiKeyType());
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            eq(newTokenDTO), eq(NotificationEvent.TOKEN_EDITED), eq(NotificationEvent.TOKEN_EDITED.name()), isNull());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenTokenDeleteEvent_ThenNotificationSentWithUtilsMethod() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getServiceAccountTokenDTO(
        data.accountIdentifier, data.projectIdentifier, data.orgIdentifier, data.identifier, data.uniqueId, data.name);
    TokenDeleteEvent tokenDeleteEvent = new TokenDeleteEvent(tokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenDeleted")
                                  .eventData(eventData)
                                  .resourceScope(tokenDeleteEvent.getResourceScope())
                                  .resource(tokenDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    assertEquals(ApiKeyType.SERVICE_ACCOUNT, tokenDTO.getApiKeyType());
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            eq(tokenDTO), eq(NotificationEvent.TOKEN_DELETED), eq(NotificationEvent.TOKEN_DELETED.name()), isNull());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenTokenExpireEvent_ThenNotificationSentWithUtilsMethod() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getServiceAccountTokenDTO(
        data.accountIdentifier, data.projectIdentifier, data.orgIdentifier, data.identifier, data.uniqueId, data.name);
    TokenExpireEvent tokenExpireEvent = new TokenExpireEvent(tokenDTO);
    String eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(tokenExpireEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("TokenExpired")
                                  .eventData(eventData)
                                  .resourceScope(tokenExpireEvent.getResourceScope())
                                  .resource(tokenExpireEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    assertEquals(ApiKeyType.SERVICE_ACCOUNT, tokenDTO.getApiKeyType());
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            eq(tokenDTO), eq(NotificationEvent.TOKEN_EXPIRED), eq(NotificationEvent.TOKEN_EXPIRED.name()), isNull());
  }

  private TokenDTO getScopedTokenDTO(TestData data, String identifier, TokenMode tokenMode) {
    return TokenDTO.builder()
        .accountIdentifier(data.accountIdentifier)
        .orgIdentifier(data.orgIdentifier)
        .projectIdentifier(data.projectIdentifier)
        .identifier(identifier)
        .name(data.name)
        .uniqueId(data.uniqueId)
        .parentUniqueId(randomAlphabetic(10))
        .parentIdentifier(randomAlphabetic(10))
        .apiKeyIdentifier(randomAlphabetic(10))
        .apiKeyType(ApiKeyType.SCOPED_TOKEN)
        .tokenMode(tokenMode)
        .build();
  }

  private OutboxEvent outboxEvent(String eventType, Event event) throws JsonProcessingException {
    return OutboxEvent.builder()
        .id(randomAlphabetic(10))
        .blocked(false)
        .eventType(eventType)
        .eventData(objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(event))
        .resourceScope(event.getResourceScope())
        .resource(event.getResource())
        .createdAt(Long.parseLong(randomNumeric(5)))
        .build();
  }

  // ---------------------------------------------
  // Ephemeral scoped token: audit publish skipped
  // ---------------------------------------------

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenCreated_thenAuditNotPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenDeleted_thenAuditNotPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenDeleted", new TokenDeleteEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenExpired_thenAuditNotPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenExpired", new TokenExpireEvent(tokenDTO));

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenUpdatedTokenIsEphemeral_thenAuditNotPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO oldTokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.PERSISTENT);
    TokenDTO newTokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenUpdated", new TokenUpdateEvent(oldTokenDTO, newTokenDTO));
    when(producer.send(any())).thenReturn("");

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, never()).publishAudit(any(), any());
  }

  // -------------------------------------------------------
  // Ephemeral skip is a success and preserves side effects
  // -------------------------------------------------------

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenCreated_thenReturnsTrue() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");

    assertTrue(TokenEventHandler.handle(outboxEvent));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenCreated_thenEntityCrudEventStillPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");

    TokenEventHandler.handle(outboxEvent);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(messageCaptor.capture());
    assertMessage(messageCaptor.getValue(), data.accountIdentifier, CREATE_ACTION);
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenEphemeralScopedTokenCreated_thenNotificationStillSent() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");

    TokenEventHandler.handle(outboxEvent);

    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            eq(tokenDTO), eq(NotificationEvent.TOKEN_CREATED), eq(NotificationEvent.TOKEN_CREATED.name()), isNull());
  }

  // -----------------------------------------------
  // Precision: audit is still published otherwise
  // -----------------------------------------------

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenPersistentScopedTokenCreated_thenAuditPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.PERSISTENT);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenScopedTokenModeIsNull_thenAuditPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO tokenDTO = getScopedTokenDTO(data, data.identifier, null);
    OutboxEvent outboxEvent = outboxEvent("TokenCreated", new TokenCreateEvent(tokenDTO));
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void handle_whenUpdatedTokenIsPersistent_thenAuditPublished() throws IOException {
    TestData data = new TestData();
    TokenDTO oldTokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.EPHEMERAL);
    TokenDTO newTokenDTO = getScopedTokenDTO(data, data.identifier, TokenMode.PERSISTENT);
    OutboxEvent outboxEvent = outboxEvent("TokenUpdated", new TokenUpdateEvent(oldTokenDTO, newTokenDTO));
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    TokenEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(any(), any());
  }
}
