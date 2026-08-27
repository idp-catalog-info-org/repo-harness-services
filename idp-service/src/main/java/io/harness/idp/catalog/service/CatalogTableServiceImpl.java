/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.idp.catalog.events.CatalogTableCreateEvent;
import io.harness.idp.catalog.events.CatalogTableUpdateEvent;
import io.harness.idp.catalog.mapper.CatalogTableMapper;
import io.harness.idp.catalog.repositories.CatalogTableRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogTableServiceImpl implements CatalogTableService {
  @Inject CatalogTableRepository catalogTableRepository;
  @Inject OutboxService outboxService;
  @Inject TransactionHelper transactionHelper;

  private static final String DEFAULT_CATALOG_IDENTIFIER = "%s_table";
  private static final String DEFAULT_CATALOG_NAME = "%s table";

  @Override
  public EntityTableResponse createOrUpdateEntityTable(
      EntityTableCreateOrUpdateRequest request, String accountIdentifier, String kind) {
    validateEntityTableRequest(request);
    String identifier = String.format(DEFAULT_CATALOG_IDENTIFIER, kind);
    String name = String.format(DEFAULT_CATALOG_NAME, kind);
    Optional<CatalogTableEntity> optionalCatalogTableEntity =
        catalogTableRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    CatalogTableEntity catalogTableEntity = CatalogTableMapper.fromDTO(request);
    optionalCatalogTableEntity.ifPresent(tableEntity -> catalogTableEntity.setId(tableEntity.getId()));
    catalogTableEntity.setAccountIdentifier(accountIdentifier);
    catalogTableEntity.setIdentifier(identifier);
    catalogTableEntity.setName(name);
    catalogTableEntity.setKind(kind);
    EntityTableResponse newEntityTableResponse = CatalogTableMapper.toDTO(catalogTableEntity);
    transactionHelper.performTransaction(() -> {
      catalogTableRepository.save(catalogTableEntity);
      if (optionalCatalogTableEntity.isPresent()) {
        sendOutboxEvent(
            accountIdentifier, newEntityTableResponse, CatalogTableMapper.toDTO(optionalCatalogTableEntity.get()));
      } else {
        sendOutboxEvent(accountIdentifier, newEntityTableResponse, null);
      }
      return null;
    });
    return newEntityTableResponse;
  }

  @Override
  public List<EntityTableResponse> getEntityTables(String accountIdentifier, String kind) {
    List<CatalogTableEntity> catalogTableEntities =
        catalogTableRepository.findAllByAccountIdentifierInAndKind(List.of(accountIdentifier, GLOBAL_ACCOUNT_ID), kind);
    if (isEmpty(catalogTableEntities)) {
      Optional<CatalogTableEntity> optionalCatalogTableEntity =
          catalogTableRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, "__Harness_default_Table__");
      optionalCatalogTableEntity.ifPresent(catalogTableEntity -> catalogTableEntities.add(catalogTableEntity));
    }
    return CatalogTableMapper.toResponseList(catalogTableEntities);
  }

  private void validateEntityTableRequest(EntityTableCreateOrUpdateRequest request) {
    if (request.getColumnDetails() == null || request.getColumnDetails().isEmpty()) {
      throw new InvalidRequestException("Column details cannot be empty");
    }

    Set<String> columnIds = new HashSet<>();
    request.getColumnDetails().forEach(columnDetail -> {
      if (columnIds.contains(columnDetail.getId())) {
        throw new InvalidRequestException("Column id " + columnDetail.getId() + " is duplicate");
      }
      columnIds.add(columnDetail.getId());
    });
  }

  private void sendOutboxEvent(String accountIdentifier, EntityTableResponse newEntityTableResponse,
      EntityTableResponse oldEntityTableResponse) {
    if (oldEntityTableResponse == null) {
      outboxService.save(new CatalogTableCreateEvent(accountIdentifier, newEntityTableResponse));
    } else {
      outboxService.save(
          new CatalogTableUpdateEvent(accountIdentifier, newEntityTableResponse, oldEntityTableResponse));
    }
  }
}
