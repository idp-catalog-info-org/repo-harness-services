/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OrganizationMessageHandlerTest extends CategoryTest {
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private CatalogScopeResolver catalogScopeResolver;

  @InjectMocks OrganizationMessageHandler organizationMessageHandler;

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_ORG_ID = "test-org-id";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(catalogEntityRepository.existsByAccountIdentifier(TEST_ACCOUNT_ID)).thenReturn(true);
  }

  private Message buildMessage(String action) {
    OrganizationEntityChangeDTO orgDto = OrganizationEntityChangeDTO.newBuilder()
                                             .setAccountIdentifier(TEST_ACCOUNT_ID)
                                             .setIdentifier(TEST_ORG_ID)
                                             .build();
    return Message.newBuilder()
        .setMessage(
            io.harness.eventsframework.producer.Message.newBuilder()
                .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                    EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY,
                    EventsFrameworkMetadataConstants.ACTION, action))
                .setData(orgDto.toByteString())
                .build())
        .build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageCreate() throws Exception {
    Message message = buildMessage(CREATE_ACTION);
    organizationMessageHandler.handleMessage(message, null, CREATE_ACTION);
    verify(catalogScopeResolver).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageUpdate() throws Exception {
    Message message = buildMessage(UPDATE_ACTION);
    organizationMessageHandler.handleMessage(message, null, UPDATE_ACTION);
    verify(catalogScopeResolver).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageDelete() throws Exception {
    Message message = buildMessage(DELETE_ACTION);
    organizationMessageHandler.handleMessage(message, null, DELETE_ACTION);
    verify(catalogScopeResolver).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageRestore() throws Exception {
    Message message = buildMessage(RESTORE_ACTION);
    organizationMessageHandler.handleMessage(message, null, RESTORE_ACTION);
    verify(catalogScopeResolver).buildScopeTopology(TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessageSkipsWhenNoCatalogEntitiesForAccount() throws Exception {
    when(catalogEntityRepository.existsByAccountIdentifier(TEST_ACCOUNT_ID)).thenReturn(false);
    Message message = buildMessage(CREATE_ACTION);
    organizationMessageHandler.handleMessage(message, null, CREATE_ACTION);
    verify(catalogScopeResolver, never()).buildScopeTopology(any());
  }
}
