/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.idp.catalog.events.CatalogTableCreateEvent;
import io.harness.idp.catalog.events.CatalogTableUpdateEvent;
import io.harness.idp.catalog.repositories.CatalogTableRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityColumnDetails;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;
import io.harness.springdata.TransactionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogTableServiceImplTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccount";
  private static final String TEST_KIND = "service";
  private static final String DEFAULT_CATALOG_IDENTIFIER = TEST_KIND + "_table";
  private static final String DEFAULT_CATALOG_NAME = TEST_KIND + " table";

  @Mock private CatalogTableRepository catalogTableRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionHelper transactionHelper;

  @InjectMocks private CatalogTableServiceImpl catalogTableService;

  private AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (openMocks != null) {
      openMocks.close();
    }
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateEntityTableWhenNotExists() {
    EntityTableCreateOrUpdateRequest request = createValidRequest();
    when(catalogTableRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_ID, DEFAULT_CATALOG_IDENTIFIER))
        .thenReturn(Optional.empty());
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityTableResponse response = catalogTableService.createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, TEST_KIND);

    assertThat(response).isNotNull();
    assertThat(response.getIdentifier()).isEqualTo(DEFAULT_CATALOG_IDENTIFIER);
    assertThat(response.getName()).isEqualTo(DEFAULT_CATALOG_NAME);
    assertThat(response.getKind()).isEqualTo(TEST_KIND);

    ArgumentCaptor<CatalogTableEntity> entityCaptor = ArgumentCaptor.forClass(CatalogTableEntity.class);
    verify(catalogTableRepository).save(entityCaptor.capture());

    CatalogTableEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.getIdentifier()).isEqualTo(DEFAULT_CATALOG_IDENTIFIER);
    assertThat(savedEntity.getName()).isEqualTo(DEFAULT_CATALOG_NAME);
    assertThat(savedEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(savedEntity.getKind()).isEqualTo(TEST_KIND);

    ArgumentCaptor<CatalogTableCreateEvent> eventCaptor = ArgumentCaptor.forClass(CatalogTableCreateEvent.class);
    verify(outboxService).save(eventCaptor.capture());

    CatalogTableCreateEvent event = eventCaptor.getValue();
    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getEntityTableResponse()).isEqualTo(response);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateEntityTableWhenExists() {
    EntityTableCreateOrUpdateRequest request = createValidRequest();
    CatalogTableEntity existingEntity = new CatalogTableEntity();
    existingEntity.setId("existing-id");
    existingEntity.setIdentifier(DEFAULT_CATALOG_IDENTIFIER);
    existingEntity.setName(DEFAULT_CATALOG_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_ID);
    existingEntity.setKind(TEST_KIND);
    existingEntity.setColumnDetails(Collections.singletonList(
        CatalogTableEntity.ColumnDetails.builder().id("column-1").headerName("Name").build()));

    when(catalogTableRepository.findByAccountIdentifierAndIdentifier(TEST_ACCOUNT_ID, DEFAULT_CATALOG_IDENTIFIER))
        .thenReturn(Optional.of(existingEntity));
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityTableResponse response = catalogTableService.createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, TEST_KIND);

    assertThat(response).isNotNull();
    assertThat(response.getIdentifier()).isEqualTo(DEFAULT_CATALOG_IDENTIFIER);

    ArgumentCaptor<CatalogTableEntity> entityCaptor = ArgumentCaptor.forClass(CatalogTableEntity.class);
    verify(catalogTableRepository).save(entityCaptor.capture());

    CatalogTableEntity savedEntity = entityCaptor.getValue();
    assertThat(savedEntity.getId()).isEqualTo("existing-id");

    ArgumentCaptor<CatalogTableUpdateEvent> eventCaptor = ArgumentCaptor.forClass(CatalogTableUpdateEvent.class);
    verify(outboxService).save(eventCaptor.capture());

    CatalogTableUpdateEvent event = eventCaptor.getValue();
    assertThat(event.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(event.getNewEntityTableResponse()).isEqualTo(response);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateEntityTableRequestWithEmptyColumns() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();
    request.setColumnDetails(Collections.emptyList());

    assertThatThrownBy(() -> catalogTableService.createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, TEST_KIND))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Column details cannot be empty");

    verify(catalogTableRepository, never()).save(any());
    verify(outboxService, never()).save(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testValidateEntityTableRequestWithDuplicateColumnIds() {
    EntityTableCreateOrUpdateRequest request = getEntityTableCreateOrUpdateRequest();

    assertThatThrownBy(() -> catalogTableService.createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, TEST_KIND))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Column id column-id is duplicate");

    verify(catalogTableRepository, never()).save(any());
    verify(outboxService, never()).save(any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityTables() {
    List<CatalogTableEntity> entities = getCatalogTableEntities();

    when(catalogTableRepository.findAllByAccountIdentifierInAndKind(
             List.of(TEST_ACCOUNT_ID, GLOBAL_ACCOUNT_ID), TEST_KIND))
        .thenReturn(entities);

    List<EntityTableResponse> responses = catalogTableService.getEntityTables(TEST_ACCOUNT_ID, TEST_KIND);

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).getIdentifier()).isEqualTo("service_table");
    assertThat(responses.get(1).getIdentifier()).isEqualTo("global_service_table");
  }

  @NotNull
  private static List<CatalogTableEntity> getCatalogTableEntities() {
    CatalogTableEntity entity1 = new CatalogTableEntity();
    entity1.setId("id-1");
    entity1.setIdentifier("service_table");
    entity1.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity1.setKind(TEST_KIND);
    List<CatalogTableEntity.ColumnDetails> columnDetails1 = new ArrayList<>();
    columnDetails1.add(CatalogTableEntity.ColumnDetails.builder().id("column-id").headerName("Column 1").build());
    entity1.setColumnDetails(columnDetails1);

    CatalogTableEntity entity2 = new CatalogTableEntity();
    entity2.setId("id-2");
    entity2.setIdentifier("global_service_table");
    entity2.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    entity2.setKind(TEST_KIND);
    List<CatalogTableEntity.ColumnDetails> columnDetails2 = new ArrayList<>();
    columnDetails2.add(CatalogTableEntity.ColumnDetails.builder().id("column-id").headerName("Column 2").build());
    entity2.setColumnDetails(columnDetails2);

    return List.of(entity1, entity2);
  }

  @NotNull
  private static EntityTableCreateOrUpdateRequest getEntityTableCreateOrUpdateRequest() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();
    List<EntityColumnDetails> columnDetails = new ArrayList<>();

    EntityColumnDetails column1 = new EntityColumnDetails();
    column1.setId("column-id");
    column1.setHeaderName("Column 1");
    columnDetails.add(column1);

    EntityColumnDetails column2 = new EntityColumnDetails();
    column2.setId("column-id");
    column2.setHeaderName("Column 2");
    columnDetails.add(column2);

    request.setColumnDetails(columnDetails);
    return request;
  }

  private EntityTableCreateOrUpdateRequest createValidRequest() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();
    List<EntityColumnDetails> columnDetails = new ArrayList<>();

    EntityColumnDetails column = new EntityColumnDetails();
    column.setId("column-1");
    column.setHeaderName("Name");
    column.setType("string");
    columnDetails.add(column);

    request.setColumnDetails(columnDetails);
    return request;
  }
}
