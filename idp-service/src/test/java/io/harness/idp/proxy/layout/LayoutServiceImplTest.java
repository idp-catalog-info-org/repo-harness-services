/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.idp.proxy.layout.events.LayoutCreateEvent;
import io.harness.idp.proxy.layout.events.LayoutUpdateEvent;
import io.harness.idp.proxy.layout.repositories.LayoutRepository;
import io.harness.idp.proxy.layout.service.LayoutServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import java.lang.reflect.Constructor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class LayoutServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String LAYOUT_NAME = "test-layout";
  private static final String LAYOUT_TYPE = "overview";
  private static final String LAYOUT_ID = "layout-1";
  private static final String YAML = "layout: test";
  private static final String UPDATED_YAML = "layout: updated";

  AutoCloseable openMocks;

  @Mock LayoutRepository layoutRepository;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock TransactionTemplate transactionTemplate;
  @Mock OutboxService outboxService;

  LayoutServiceImpl layoutService;

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
    Constructor<LayoutServiceImpl> constructor = LayoutServiceImpl.class.getDeclaredConstructor(
        LayoutRepository.class, BackstageResourceClient.class, TransactionTemplate.class, OutboxService.class);
    constructor.setAccessible(true);
    layoutService =
        constructor.newInstance(layoutRepository, backstageResourceClient, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveNewLayout() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);
    layoutRequest.setId(LAYOUT_ID);

    LayoutEntity savedLayoutEntity = LayoutEntity.builder()
                                         .name(LAYOUT_NAME)
                                         .type(LAYOUT_TYPE)
                                         .yaml(YAML)
                                         .identifier(LAYOUT_ID)
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .createdBy(EmbeddedUser.builder().name("test-user").build())
                                         .lastUpdatedBy(EmbeddedUser.builder().name("test-user").build())
                                         .build();

    when(layoutRepository.findByAccountIdentifierAndNameAndType(
             eq(ACCOUNT_IDENTIFIER), eq(LAYOUT_NAME), eq(LAYOUT_TYPE)))
        .thenReturn(null);
    when(layoutRepository.save(any(LayoutEntity.class))).thenReturn(savedLayoutEntity);
    when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
      TransactionCallback callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    layoutService.saveOrUpdateLayouts(layoutRequest, ACCOUNT_IDENTIFIER);

    verify(layoutRepository).save(any(LayoutEntity.class));
    verify(outboxService).save(any(LayoutCreateEvent.class));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateExistingLayout() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(UPDATED_YAML);
    layoutRequest.setId(LAYOUT_ID);

    LayoutEntity existingLayoutEntity = LayoutEntity.builder()
                                            .id("existing-id")
                                            .name(LAYOUT_NAME)
                                            .type(LAYOUT_TYPE)
                                            .yaml(YAML)
                                            .identifier(LAYOUT_ID)
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .createdAt(1000L)
                                            .createdBy(EmbeddedUser.builder().name("creator").build())
                                            .build();

    LayoutEntity updatedLayoutEntity = LayoutEntity.builder()
                                           .id("existing-id")
                                           .name(LAYOUT_NAME)
                                           .type(LAYOUT_TYPE)
                                           .yaml(UPDATED_YAML)
                                           .identifier(LAYOUT_ID)
                                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                                           .createdAt(1000L)
                                           .createdBy(EmbeddedUser.builder().name("creator").build())
                                           .lastUpdatedBy(EmbeddedUser.builder().name("updater").build())
                                           .build();

    when(layoutRepository.findByAccountIdentifierAndNameAndType(
             eq(ACCOUNT_IDENTIFIER), eq(LAYOUT_NAME), eq(LAYOUT_TYPE)))
        .thenReturn(existingLayoutEntity);
    when(layoutRepository.save(any(LayoutEntity.class))).thenReturn(updatedLayoutEntity);
    when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
      TransactionCallback callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    layoutService.saveOrUpdateLayouts(layoutRequest, ACCOUNT_IDENTIFIER);

    ArgumentCaptor<LayoutEntity> layoutCaptor = ArgumentCaptor.forClass(LayoutEntity.class);
    verify(layoutRepository).save(layoutCaptor.capture());

    LayoutEntity savedLayout = layoutCaptor.getValue();
    assertEquals("existing-id", savedLayout.getId());
    assertEquals(1000L, savedLayout.getCreatedAt());
    assertEquals("creator", savedLayout.getCreatedBy().getName());

    verify(outboxService).save(any(LayoutUpdateEvent.class));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testUpdateLayoutWithSameYamlDoesNotTriggerEvent() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);
    layoutRequest.setId(LAYOUT_ID);

    LayoutEntity existingLayoutEntity = LayoutEntity.builder()
                                            .id("existing-id")
                                            .name(LAYOUT_NAME)
                                            .type(LAYOUT_TYPE)
                                            .yaml(YAML)
                                            .identifier(LAYOUT_ID)
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .createdAt(1000L)
                                            .createdBy(EmbeddedUser.builder().name("creator").build())
                                            .build();

    LayoutEntity updatedLayoutEntity = LayoutEntity.builder()
                                           .id("existing-id")
                                           .name(LAYOUT_NAME)
                                           .type(LAYOUT_TYPE)
                                           .yaml(YAML)
                                           .identifier(LAYOUT_ID)
                                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                                           .createdAt(1000L)
                                           .createdBy(EmbeddedUser.builder().name("creator").build())
                                           .lastUpdatedBy(EmbeddedUser.builder().name("updater").build())
                                           .build();

    when(layoutRepository.findByAccountIdentifierAndNameAndType(
             eq(ACCOUNT_IDENTIFIER), eq(LAYOUT_NAME), eq(LAYOUT_TYPE)))
        .thenReturn(existingLayoutEntity);
    when(layoutRepository.save(any(LayoutEntity.class))).thenReturn(updatedLayoutEntity);
    when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
      TransactionCallback callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    layoutService.saveOrUpdateLayouts(layoutRequest, ACCOUNT_IDENTIFIER);

    verify(layoutRepository).save(any(LayoutEntity.class));
    verify(outboxService, times(0)).save(any(LayoutUpdateEvent.class));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSaveLayoutWithCompleteInformation() {
    LayoutRequest layoutRequest = new LayoutRequest();
    layoutRequest.setName(LAYOUT_NAME);
    layoutRequest.setType(LAYOUT_TYPE);
    layoutRequest.setYaml(YAML);
    layoutRequest.setId(LAYOUT_ID);
    layoutRequest.setDisplayName("Test Display Name");
    layoutRequest.setEntityKind("Component");
    layoutRequest.setEntityType("service");
    layoutRequest.setDefaultYaml("default: yaml");
    layoutRequest.setHarnessManaged(true);

    LayoutEntity savedLayoutEntity = LayoutEntity.builder()
                                         .name(LAYOUT_NAME)
                                         .type(LAYOUT_TYPE)
                                         .yaml(YAML)
                                         .identifier(LAYOUT_ID)
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .displayName("Test Display Name")
                                         .entityKind("Component")
                                         .entityType("service")
                                         .defaultYaml("default: yaml")
                                         .harnessManaged(true)
                                         .createdBy(EmbeddedUser.builder().name("test-user").build())
                                         .lastUpdatedBy(EmbeddedUser.builder().name("test-user").build())
                                         .build();

    when(layoutRepository.findByAccountIdentifierAndNameAndType(anyString(), anyString(), anyString()))
        .thenReturn(null);
    when(layoutRepository.save(any(LayoutEntity.class))).thenReturn(savedLayoutEntity);
    when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
      TransactionCallback callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    layoutService.saveOrUpdateLayouts(layoutRequest, ACCOUNT_IDENTIFIER);

    verify(layoutRepository).save(any(LayoutEntity.class));
    verify(outboxService).save(any(LayoutCreateEvent.class));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
