/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityColumnDetails;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogTableMapperTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromDTO() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();

    List<EntityColumnDetails> columnDetailsList = new ArrayList<>();
    EntityColumnDetails column1 = new EntityColumnDetails();
    column1.setId("col1");
    column1.setType("string");
    column1.setHeaderName("Column 1");
    column1.setSize(100);
    column1.setAccessorKey("accessor1");
    column1.setDescription("First column");
    column1.setVisible(true);
    column1.setHarnessManaged(false);
    column1.setPinned("left");
    Map<String, Object> properties1 = new HashMap<>();
    properties1.put("prop1", "value1");
    column1.setProperties(properties1);
    columnDetailsList.add(column1);

    EntityColumnDetails column2 = new EntityColumnDetails();
    column2.setId("col2");
    column2.setType("number");
    column2.setHeaderName("Column 2");
    column2.setSize(50);
    column2.setAccessorKey("accessor2");
    column2.setDescription("Second column");
    column2.setVisible(false);
    column2.setHarnessManaged(true);
    column2.setPinned("right");
    Map<String, Object> properties2 = new HashMap<>();
    properties2.put("prop2", "value2");
    column2.setProperties(properties2);
    columnDetailsList.add(column2);

    request.setColumnDetails(columnDetailsList);

    CatalogTableEntity entity = CatalogTableMapper.fromDTO(request);

    assertThat(entity).isNotNull();
    assertThat(entity.getType()).isEqualTo("all");
    assertThat(entity.getFilter()).isNotNull();
    assertThat(entity.getFilter().getOwners()).isEmpty();
    assertThat(entity.getFilter().getTags()).isEmpty();
    assertThat(entity.getFilter().getLifecycles()).isEmpty();
    assertThat(entity.getFilter().getScopes()).containsExactly("account.*");

    assertThat(entity.getColumnDetails()).hasSize(2);

    CatalogTableEntity.ColumnDetails col1 = entity.getColumnDetails().get(0);
    assertThat(col1.getId()).isEqualTo("col1");
    assertThat(col1.getType()).isEqualTo("string");
    assertThat(col1.getHeaderName()).isEqualTo("Column 1");
    assertThat(col1.getSize()).isEqualTo(100);
    assertThat(col1.getAccessorKey()).isEqualTo("accessor1");
    assertThat(col1.getDescription()).isEqualTo("First column");
    assertThat(col1.isVisible()).isTrue();
    assertThat(col1.isHarnessManaged()).isFalse();
    assertThat(col1.getPinned()).isEqualTo("left");
    assertThat(col1.getProperties()).containsEntry("prop1", "value1");

    CatalogTableEntity.ColumnDetails col2 = entity.getColumnDetails().get(1);
    assertThat(col2.getId()).isEqualTo("col2");
    assertThat(col2.getType()).isEqualTo("number");
    assertThat(col2.getHeaderName()).isEqualTo("Column 2");
    assertThat(col2.getSize()).isEqualTo(50);
    assertThat(col2.getAccessorKey()).isEqualTo("accessor2");
    assertThat(col2.getDescription()).isEqualTo("Second column");
    assertThat(col2.isVisible()).isFalse();
    assertThat(col2.isHarnessManaged()).isTrue();
    assertThat(col2.getPinned()).isEqualTo("right");
    assertThat(col2.getProperties()).containsEntry("prop2", "value2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromDTOWithNullBooleanValues() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();

    List<EntityColumnDetails> columnDetailsList = new ArrayList<>();
    EntityColumnDetails column = new EntityColumnDetails();
    column.setId("col1");
    column.setType("string");
    column.setHeaderName("Column");
    column.setVisible(null);
    column.setHarnessManaged(null);
    columnDetailsList.add(column);

    request.setColumnDetails(columnDetailsList);

    CatalogTableEntity entity = CatalogTableMapper.fromDTO(request);

    assertThat(entity.getColumnDetails()).hasSize(1);
    assertThat(entity.getColumnDetails().get(0).isVisible()).isFalse();
    assertThat(entity.getColumnDetails().get(0).isHarnessManaged()).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToDTO() {
    List<CatalogTableEntity.ColumnDetails> columnDetailsList = new ArrayList<>();

    CatalogTableEntity.ColumnDetails column1 = CatalogTableEntity.ColumnDetails.builder()
                                                   .id("col1")
                                                   .type("string")
                                                   .headerName("Column 1")
                                                   .size(100)
                                                   .accessorKey("accessor1")
                                                   .description("First column")
                                                   .visible(true)
                                                   .harnessManaged(false)
                                                   .pinned("left")
                                                   .properties(Map.of("prop1", "value1"))
                                                   .build();
    columnDetailsList.add(column1);

    CatalogTableEntity.ColumnDetails column2 = CatalogTableEntity.ColumnDetails.builder()
                                                   .id("col2")
                                                   .type("number")
                                                   .headerName("Column 2")
                                                   .size(50)
                                                   .accessorKey("accessor2")
                                                   .description("Second column")
                                                   .visible(false)
                                                   .harnessManaged(true)
                                                   .pinned("right")
                                                   .properties(Map.of("prop2", "value2"))
                                                   .build();
    columnDetailsList.add(column2);

    CatalogTableEntity entity = CatalogTableEntity.builder()
                                    .identifier("table-id")
                                    .name("Table Name")
                                    .kind("component")
                                    .columnDetails(columnDetailsList)
                                    .build();

    EntityTableResponse response = CatalogTableMapper.toDTO(entity);

    assertThat(response).isNotNull();
    assertThat(response.getIdentifier()).isEqualTo("table-id");
    assertThat(response.getName()).isEqualTo("Table Name");
    assertThat(response.getKind()).isEqualTo("component");
    assertThat(response.getColumnDetails()).hasSize(2);

    EntityColumnDetails col1 = response.getColumnDetails().get(0);
    assertThat(col1.getId()).isEqualTo("col1");
    assertThat(col1.getType()).isEqualTo("string");
    assertThat(col1.getHeaderName()).isEqualTo("Column 1");
    assertThat(col1.getSize()).isEqualTo(100);
    assertThat(col1.getAccessorKey()).isEqualTo("accessor1");
    assertThat(col1.getDescription()).isEqualTo("First column");
    assertThat(col1.isVisible()).isTrue();
    assertThat(col1.isHarnessManaged()).isFalse();
    assertThat(col1.getPinned()).isEqualTo("left");
    assertThat(col1.getProperties()).isNotNull();
    assertThat(((Map<String, Object>) col1.getProperties()).get("prop1")).isEqualTo("value1");

    EntityColumnDetails col2 = response.getColumnDetails().get(1);
    assertThat(col2.getId()).isEqualTo("col2");
    assertThat(col2.getType()).isEqualTo("number");
    assertThat(col2.getHeaderName()).isEqualTo("Column 2");
    assertThat(col2.getSize()).isEqualTo(50);
    assertThat(col2.getAccessorKey()).isEqualTo("accessor2");
    assertThat(col2.getDescription()).isEqualTo("Second column");
    assertThat(col2.isVisible()).isFalse();
    assertThat(col2.isHarnessManaged()).isTrue();
    assertThat(col2.getPinned()).isEqualTo("right");
    assertThat(col2.getProperties()).isNotNull();
    assertThat(((Map<String, Object>) col2.getProperties()).get("prop2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseList() {
    List<CatalogTableEntity> entities = new ArrayList<>();

    CatalogTableEntity entity1 =
        CatalogTableEntity.builder()
            .identifier("table1")
            .name("Table 1")
            .kind("component")
            .columnDetails(List.of(
                CatalogTableEntity.ColumnDetails.builder().id("col1").type("string").headerName("Column").build()))
            .build();
    entities.add(entity1);

    CatalogTableEntity entity2 = CatalogTableEntity.builder()
                                     .identifier("table2")
                                     .name("Table 2")
                                     .kind("api")
                                     .columnDetails(List.of(CatalogTableEntity.ColumnDetails.builder()
                                                                .id("col2")
                                                                .type("number")
                                                                .headerName("Number Column")
                                                                .build()))
                                     .build();
    entities.add(entity2);

    List<EntityTableResponse> responses = CatalogTableMapper.toResponseList(entities);

    assertThat(responses).hasSize(2);

    assertThat(responses.get(0).getIdentifier()).isEqualTo("table1");
    assertThat(responses.get(0).getName()).isEqualTo("Table 1");
    assertThat(responses.get(0).getKind()).isEqualTo("component");
    assertThat(responses.get(0).getColumnDetails()).hasSize(1);
    assertThat(responses.get(0).getColumnDetails().get(0).getId()).isEqualTo("col1");

    assertThat(responses.get(1).getIdentifier()).isEqualTo("table2");
    assertThat(responses.get(1).getName()).isEqualTo("Table 2");
    assertThat(responses.get(1).getKind()).isEqualTo("api");
    assertThat(responses.get(1).getColumnDetails()).hasSize(1);
    assertThat(responses.get(1).getColumnDetails().get(0).getId()).isEqualTo("col2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseListWithEmptyList() {
    List<CatalogTableEntity> entities = new ArrayList<>();

    List<EntityTableResponse> responses = CatalogTableMapper.toResponseList(entities);

    assertThat(responses).isNotNull();
    assertThat(responses).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToDTOWithEmptyColumnDetails() {
    CatalogTableEntity entity = CatalogTableEntity.builder()
                                    .identifier("table-id")
                                    .name("Table Name")
                                    .kind("component")
                                    .columnDetails(new ArrayList<>())
                                    .build();

    EntityTableResponse response = CatalogTableMapper.toDTO(entity);

    assertThat(response).isNotNull();
    assertThat(response.getIdentifier()).isEqualTo("table-id");
    assertThat(response.getColumnDetails()).isEmpty();
  }
}
