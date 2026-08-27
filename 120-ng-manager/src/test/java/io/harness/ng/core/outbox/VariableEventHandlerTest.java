/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.YASH;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.events.VariableDeleteEvent;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VariableEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  @Mock private Producer eventProducer;
  @Mock private AuditClientService auditClientService;
  private VariableEventHandler variableEventHandler;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    MockitoAnnotations.initMocks(this);
    variableEventHandler = new VariableEventHandler(auditClientService, eventProducer);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testHandleVariableCreateEvent() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableCreateEvent variableCreateEvent = new VariableCreateEvent(scopeInfo.getAccountIdentifier(), variableDTO);
    String eventData = objectMapper.writeValueAsString(variableCreateEvent);
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
                                  .eventType("VariableCreated")
                                  .globalContext(globalContext)
                                  .eventData(eventData)
                                  .resourceScope(variableCreateEvent.getResourceScope())
                                  .resource(variableCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(VariableRequestDTO.builder().variable(variableDTO).build());

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testVariableDelete_validEventDto_projScope() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableDeleteEvent variableDeleteEvent = new VariableDeleteEvent(scopeInfo.getAccountIdentifier(), variableDTO);
    String eventData = objectMapper.writeValueAsString(variableDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .eventType("VariableDeleted")
                                  .eventData(eventData)
                                  .resourceScope(variableDeleteEvent.getResourceScope())
                                  .resource(variableDeleteEvent.getResource())
                                  .blocked(false)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(globalContext)
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor, messageArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());

    String oldYaml = getYamlString(VariableRequestDTO.builder().variable(variableDTO).build());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "delete");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testVariableDelete_validEventDto_orgScope() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = null;
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableDeleteEvent variableDeleteEvent = new VariableDeleteEvent(scopeInfo.getAccountIdentifier(), variableDTO);
    String eventData = objectMapper.writeValueAsString(variableDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .eventType("VariableDeleted")
                                  .eventData(eventData)
                                  .resourceScope(variableDeleteEvent.getResourceScope())
                                  .resource(variableDeleteEvent.getResource())
                                  .blocked(false)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(globalContext)
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor, messageArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());

    String oldYaml = getYamlString(VariableRequestDTO.builder().variable(variableDTO).build());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "delete");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testVariableDelete_validEventDto_accScope() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = null;
    String projectIdentifier = null;
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableDeleteEvent variableDeleteEvent = new VariableDeleteEvent(scopeInfo.getAccountIdentifier(), variableDTO);
    String eventData = objectMapper.writeValueAsString(variableDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .eventType("VariableDeleted")
                                  .eventData(eventData)
                                  .resourceScope(variableDeleteEvent.getResourceScope())
                                  .resource(variableDeleteEvent.getResource())
                                  .blocked(false)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(globalContext)
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

    verifyMethodInvocation(outboxEvent, auditEntryArgumentCaptor, messageArgumentCaptor);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());

    String oldYaml = getYamlString(VariableRequestDTO.builder().variable(variableDTO).build());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());

    Message message = messageArgumentCaptor.getValue();
    assertMessage(message, accountIdentifier, "delete");
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testVariableDelete_throwsEventFrameworksDownException() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(orgIdentifier, projectIdentifier, identifier);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableDeleteEvent variableDeleteEvent = new VariableDeleteEvent(scopeInfo.getAccountIdentifier(), variableDTO);
    String eventData = objectMapper.writeValueAsString(variableDeleteEvent);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal(
                randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .eventType("VariableDeleted")
                                  .eventData(eventData)
                                  .resourceScope(variableDeleteEvent.getResourceScope())
                                  .resource(variableDeleteEvent.getResource())
                                  .blocked(false)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .globalContext(globalContext)
                                  .build();

    when(eventProducer.send(any())).thenThrow(EventsFrameworkDownException.class);
    boolean result = variableEventHandler.handle(outboxEvent);
    assertFalse(result);
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testBuildEntity_accountScope() {
    OutboxEvent outboxEvent = mock(OutboxEvent.class);
    AccountScope accountScope = mock(AccountScope.class);
    Resource resource = mock(Resource.class);

    when(resource.getIdentifier()).thenReturn("resourceId");
    when(outboxEvent.getResource()).thenReturn(resource);
    when(accountScope.getAccountIdentifier()).thenReturn("accountId");
    when(outboxEvent.getResourceScope()).thenReturn(accountScope);

    EntityChangeDTO entityChangeDTO = variableEventHandler.buildEntityForRedisMessage(outboxEvent);

    assertEquals("resourceId", entityChangeDTO.getIdentifier().getValue());
    assertEquals("accountId", entityChangeDTO.getAccountIdentifier().getValue());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testBuildEntity_orgScope() {
    OutboxEvent outboxEvent = mock(OutboxEvent.class);
    OrgScope orgScope = mock(OrgScope.class);
    Resource resource = mock(Resource.class);

    when(resource.getIdentifier()).thenReturn("resourceId");
    when(outboxEvent.getResource()).thenReturn(resource);
    when(orgScope.getAccountIdentifier()).thenReturn("accountId");
    when(orgScope.getOrgIdentifier()).thenReturn("orgId");
    when(outboxEvent.getResourceScope()).thenReturn(orgScope);

    EntityChangeDTO entityChangeDTO = variableEventHandler.buildEntityForRedisMessage(outboxEvent);

    assertEquals("resourceId", entityChangeDTO.getIdentifier().getValue());
    assertEquals("accountId", entityChangeDTO.getAccountIdentifier().getValue());
    assertEquals("orgId", entityChangeDTO.getOrgIdentifier().getValue());
  }

  @Test
  @Owner(developers = YASH)
  @Category(UnitTests.class)
  public void testBuildEntity_projectScope() {
    OutboxEvent outboxEvent = mock(OutboxEvent.class);
    ProjectScope projectScope = mock(ProjectScope.class);
    Resource resource = mock(Resource.class);

    when(resource.getIdentifier()).thenReturn("resourceId");
    when(outboxEvent.getResource()).thenReturn(resource);
    when(projectScope.getAccountIdentifier()).thenReturn("accountId");
    when(projectScope.getOrgIdentifier()).thenReturn("orgId");
    when(projectScope.getProjectIdentifier()).thenReturn("projectId");
    when(outboxEvent.getResourceScope()).thenReturn(projectScope);

    EntityChangeDTO entityChangeDTO = variableEventHandler.buildEntityForRedisMessage(outboxEvent);

    assertEquals("resourceId", entityChangeDTO.getIdentifier().getValue());
    assertEquals("accountId", entityChangeDTO.getAccountIdentifier().getValue());
    assertEquals("orgId", entityChangeDTO.getOrgIdentifier().getValue());
    assertEquals("projectId", entityChangeDTO.getProjectIdentifier().getValue());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testHandle_notSupportedEventType() {
    String eventType = randomAlphabetic(10);
    OutboxEvent event = OutboxEvent.builder().eventType(eventType).build();
    exceptionRule.expect(InvalidArgumentsException.class);
    exceptionRule.expectMessage(String.format("Not supported event type %s", eventType));
    variableEventHandler.handle(event);
  }

  private VariableDTO getVariableDTO(String orgIdentifier, String projectIdentifier, String identifier) {
    return VariableDTO.builder()
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor) {
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    variableEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void verifyMethodInvocation(OutboxEvent outboxEvent, ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor,
      ArgumentCaptor<Message> messageArgumentCaptor) {
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    when(eventProducer.send(any())).thenReturn("");
    variableEventHandler.handle(outboxEvent);
    verify(eventProducer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.VARIABLE, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
  private void assertMessage(Message message, String accountIdentifier, String action) {
    assertNotNull(message);
    assertEquals(message.getMetadataMap().get("accountId"), accountIdentifier);
    assertEquals(message.getMetadataMap().get("entityType"), "variable");
    assertEquals(message.getMetadataMap().get("action"), action);
  }
}
