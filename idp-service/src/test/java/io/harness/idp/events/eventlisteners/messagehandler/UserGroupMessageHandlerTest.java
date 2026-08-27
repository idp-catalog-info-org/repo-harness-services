/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.user.service.UserRefreshServiceImpl;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UserGroupMessageHandlerTest extends CategoryTest {
  @Mock IdpCommonService idpCommonService;
  @Mock private UserRefreshServiceImpl userRefreshService;
  @InjectMocks UserGroupMessageHandler userGroupMessageHandler;
  @Mock CatalogService catalogService;
  @Mock NamespaceService namespaceService;

  public static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_CONNECTOR_ID = "test-connector-id";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleMessageUser() {
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_ID))
                                          .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                          .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(entityChangeDTO.toByteString())
                    .build())
            .build();
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT_ID)).thenReturn(false);
    doNothing().when(userRefreshService).processEntityUpdate(any(), any());
    when(namespaceService.getAccountIdpStatus(any())).thenReturn(true);

    userGroupMessageHandler.handleMessage(message, entityChangeDTO, RESTORE_ACTION);
    userGroupMessageHandler.handleMessage(message, entityChangeDTO, UPDATE_ACTION);
    verify(userRefreshService).processEntityUpdate(message, entityChangeDTO);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleMessageUserDeleteAction() {
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_ID))
                                          .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                          .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(entityChangeDTO.toByteString())
                    .build())
            .build();
    when(namespaceService.getAccountIdpStatus(TEST_ACCOUNT_ID)).thenReturn(true);
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT_ID)).thenReturn(false);
    doNothing().when(userRefreshService).processEntityUpdate(any(), any());

    userGroupMessageHandler.handleMessage(message, entityChangeDTO, RESTORE_ACTION);
    userGroupMessageHandler.handleMessage(message, entityChangeDTO, DELETE_ACTION);
    verify(userRefreshService).processEntityUpdate(message, entityChangeDTO);

    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT_ID)).thenReturn(true);
    when(namespaceService.getAccountIdpStatus(any())).thenReturn(true);
    userGroupMessageHandler.handleMessage(message, entityChangeDTO, DELETE_ACTION);
    verify(catalogService, times(2)).handleUserGroupBasedOnAction(TEST_ACCOUNT_ID, TEST_CONNECTOR_ID, DELETE_ACTION);
  }
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleMessageUserUpdateActionCreateAction() {
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_ID))
                                          .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                          .build();
    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(entityChangeDTO.toByteString())
                    .build())
            .build();
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT_ID)).thenReturn(false);
    doNothing().when(userRefreshService).processEntityUpdate(any(), any());
    when(namespaceService.getAccountIdpStatus(any())).thenReturn(true);

    userGroupMessageHandler.handleMessage(message, entityChangeDTO, CREATE_ACTION);
    verify(userRefreshService).processEntityUpdate(message, entityChangeDTO);
  }
}
