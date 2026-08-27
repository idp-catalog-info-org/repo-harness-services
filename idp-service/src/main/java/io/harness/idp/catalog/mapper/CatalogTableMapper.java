/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.spec.server.idp.v1.model.EntityColumnDetails;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogTableMapper {
  @SuppressWarnings("unchecked")
  public CatalogTableEntity fromDTO(EntityTableCreateOrUpdateRequest request) {
    CatalogTableEntity.Filter filter = CatalogTableEntity.Filter.builder()
                                           .owners(Collections.emptyList())
                                           .tags(Collections.emptyList())
                                           .lifecycles(Collections.emptyList())
                                           .scopes(List.of("account.*"))
                                           .build();
    List<CatalogTableEntity.ColumnDetails> columnDetailsList = new ArrayList<>();
    for (EntityColumnDetails columnDetail : request.getColumnDetails()) {
      CatalogTableEntity.ColumnDetails columnDetails =
          CatalogTableEntity.ColumnDetails.builder()
              .id(columnDetail.getId())
              .type(columnDetail.getType())
              .headerName(columnDetail.getHeaderName())
              .size(columnDetail.getSize())
              .accessorKey(columnDetail.getAccessorKey())
              .description(columnDetail.getDescription())
              .visible(Boolean.TRUE.equals(columnDetail.isVisible()))
              .harnessManaged(Boolean.TRUE.equals(columnDetail.isHarnessManaged()))
              .pinned(columnDetail.getPinned())
              .properties((Map<String, Object>) columnDetail.getProperties())
              .build();
      columnDetailsList.add(columnDetails);
    }

    return CatalogTableEntity.builder().type("all").filter(filter).columnDetails(columnDetailsList).build();
  }

  public EntityTableResponse toDTO(CatalogTableEntity catalogTableEntity) {
    EntityTableResponse entityTableResponse = new EntityTableResponse();
    entityTableResponse.setIdentifier(catalogTableEntity.getIdentifier());
    entityTableResponse.setName(catalogTableEntity.getName());
    entityTableResponse.setKind(catalogTableEntity.getKind());
    List<EntityColumnDetails> entityColumnDetailsList = new ArrayList<>();
    catalogTableEntity.getColumnDetails().forEach(columnDetail -> {
      EntityColumnDetails columnDetails = new EntityColumnDetails();
      columnDetails.setId(columnDetail.getId());
      columnDetails.setType(columnDetail.getType());
      columnDetails.setHeaderName(columnDetail.getHeaderName());
      columnDetails.setSize(columnDetail.getSize());
      columnDetails.setAccessorKey(columnDetail.getAccessorKey());
      columnDetails.setDescription(columnDetail.getDescription());
      columnDetails.setVisible(columnDetail.isVisible());
      columnDetails.setHarnessManaged(columnDetail.isHarnessManaged());
      columnDetails.setPinned(columnDetail.getPinned());
      columnDetails.setProperties(columnDetail.getProperties());
      entityColumnDetailsList.add(columnDetails);
    });
    entityTableResponse.setColumnDetails(entityColumnDetailsList);
    return entityTableResponse;
  }

  public List<EntityTableResponse> toResponseList(List<CatalogTableEntity> catalogTableEntities) {
    List<EntityTableResponse> entityTableResponses = new ArrayList<>();
    catalogTableEntities.forEach(catalogTableEntity -> entityTableResponses.add(toDTO(catalogTableEntity)));
    return entityTableResponses;
  }
}
