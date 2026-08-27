/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.connector.validator.SecretEntityCRUDEventHandler;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.event.handler.SecretEntityEventHandler;
import io.harness.ng.core.event.listener.SecretEntityCRUDStreamListener;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;

@OwnedBy(PL)
public class SecretEntityCRUDStreamListenerTest extends CategoryTest {
  private SecretCrudService secretCrudService;
  private SecretEntityCRUDEventHandler secretEntityCRUDEventHandler;
  @Inject @InjectMocks private SecretEntityCRUDStreamListener secretEntityCRUDStreamListener;
  private SecretEntityEventHandler secretEntityEventHandler;
  @Before
  public void setup() {
    secretCrudService = mock(SecretCrudService.class);
    secretEntityCRUDEventHandler = mock(SecretEntityCRUDEventHandler.class);
    secretEntityCRUDStreamListener = spy(
        new SecretEntityCRUDStreamListener(secretCrudService, secretEntityCRUDEventHandler, secretEntityEventHandler));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testAccountDeleteEvent() {
    String accountId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountId,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getAccountPayload(accountId))
                                          .build())
                          .build();
    final ArgumentCaptor<ScopeInfo> scopeInfoArgumentCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    when(secretEntityCRUDEventHandler.deleteAssociatedSecrets(any(ScopeInfo.class))).thenReturn(true);
    boolean result = secretEntityCRUDStreamListener.handleMessage(message);
    verify(secretEntityCRUDEventHandler, times(1)).deleteAssociatedSecrets(scopeInfoArgumentCaptor.capture());
    assertEquals(scopeInfoArgumentCaptor.getValue().getAccountIdentifier(), accountId);
    assertEquals(scopeInfoArgumentCaptor.getValue().getUniqueId(), accountId);
    assertEquals(scopeInfoArgumentCaptor.getValue().getScopeType(), ScopeLevel.ACCOUNT);
    assertTrue(result);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testAccountChangeEvent_withoutAnyActionSetInMetadata() {
    String accountId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountId,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY))
                                          .setData(getAccountPayload(accountId))
                                          .build())
                          .build();
    boolean result = secretEntityCRUDStreamListener.handleMessage(message);
    verify(secretEntityCRUDEventHandler, times(0)).deleteAssociatedSecrets(any());
    assertTrue(result);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testOrganizationDeleteEvent() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ORGANIZATION_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getOrganizationPayload(accountIdentifier, identifier, uniqueId))
                                          .build())
                          .build();
    final ArgumentCaptor<ScopeInfo> scopeInfoArgumentCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    when(secretEntityCRUDEventHandler.deleteAssociatedSecrets(any(ScopeInfo.class))).thenReturn(true);
    secretEntityCRUDStreamListener.handleMessage(message);
    verify(secretEntityCRUDEventHandler, times(1)).deleteAssociatedSecrets(scopeInfoArgumentCaptor.capture());
    assertEquals(scopeInfoArgumentCaptor.getValue().getAccountIdentifier(), accountIdentifier);
    assertEquals(scopeInfoArgumentCaptor.getValue().getOrgIdentifier(), identifier);
    assertEquals(scopeInfoArgumentCaptor.getValue().getUniqueId(), uniqueId);
    assertEquals(scopeInfoArgumentCaptor.getValue().getScopeType(), ScopeLevel.ORGANIZATION);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testProjectDeleteEvent() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier, uniqueId))
                    .build())
            .build();
    final ArgumentCaptor<ScopeInfo> scopeInfoArgumentCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    when(secretEntityCRUDEventHandler.deleteAssociatedSecrets(any(ScopeInfo.class))).thenReturn(true);
    secretEntityCRUDStreamListener.handleMessage(message);
    verify(secretEntityCRUDEventHandler, times(1)).deleteAssociatedSecrets(scopeInfoArgumentCaptor.capture());
    assertEquals(scopeInfoArgumentCaptor.getValue().getAccountIdentifier(), accountIdentifier);
    assertEquals(scopeInfoArgumentCaptor.getValue().getOrgIdentifier(), orgIdentifier);
    assertEquals(scopeInfoArgumentCaptor.getValue().getProjectIdentifier(), identifier);
    assertEquals(scopeInfoArgumentCaptor.getValue().getUniqueId(), uniqueId);
    assertEquals(scopeInfoArgumentCaptor.getValue().getScopeType(), ScopeLevel.PROJECT);
  }

  private ByteString getProjectPayload(
      String accountIdentifier, String orgIdentifier, String identifier, String uniqueId) {
    return ProjectEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setOrgIdentifier(orgIdentifier)
        .setAccountIdentifier(accountIdentifier)
        .setUniqueId(uniqueId)
        .build()
        .toByteString();
  }

  private ByteString getOrganizationPayload(String accountIdentifier, String identifier, String uniqueId) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .setUniqueId(uniqueId)
        .build()
        .toByteString();
  }

  private ByteString getAccountPayload(String identifier) {
    return AccountEntityChangeDTO.newBuilder().setAccountId(identifier).build().toByteString();
  }
}
