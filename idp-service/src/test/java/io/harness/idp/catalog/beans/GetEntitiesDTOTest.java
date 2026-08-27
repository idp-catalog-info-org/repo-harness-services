/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityResponse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class GetEntitiesDTOTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesDTOBuilder() {
    List<EntityResponse> sampleList = Arrays.asList(new EntityResponse(), new EntityResponse());

    GetEntitiesDTO dto = GetEntitiesDTO.builder()
                             .entityResponses(sampleList)
                             .pageNumber(1)
                             .totalElements(10)
                             .totalOwned(5)
                             .totalStarred(3)
                             .build();

    assertThat(dto.getEntityResponses()).isEqualTo(sampleList);
    assertThat(dto.getPageNumber()).isEqualTo(1);
    assertThat(dto.getTotalElements()).isEqualTo(10);
    assertThat(dto.getTotalOwned()).isEqualTo(5);
    assertThat(dto.getTotalStarred()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesDTOSettersAndGetters() {
    GetEntitiesDTO dto = new GetEntitiesDTO();
    List<EntityResponse> sampleList = Arrays.asList(new EntityResponse(), new EntityResponse(), new EntityResponse());

    dto.setEntityResponses(sampleList);
    dto.setPageNumber(2);
    dto.setTotalElements(15);
    dto.setTotalOwned(8);
    dto.setTotalStarred(4);

    assertThat(dto.getEntityResponses()).isEqualTo(sampleList);
    assertThat(dto.getEntityResponses()).hasSize(3);
    assertThat(dto.getPageNumber()).isEqualTo(2);
    assertThat(dto.getTotalElements()).isEqualTo(15);
    assertThat(dto.getTotalOwned()).isEqualTo(8);
    assertThat(dto.getTotalStarred()).isEqualTo(4);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesDTOWithNullValues() {
    GetEntitiesDTO dto = GetEntitiesDTO.builder()
                             .entityResponses(null)
                             .pageNumber(0)
                             .totalElements(0)
                             .totalOwned(0)
                             .totalStarred(0)
                             .build();

    assertThat(dto.getEntityResponses()).isNull();
    assertThat(dto.getPageNumber()).isEqualTo(0);
    assertThat(dto.getTotalElements()).isEqualTo(0);
    assertThat(dto.getTotalOwned()).isEqualTo(0);
    assertThat(dto.getTotalStarred()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesDTOWithEmptyList() {
    GetEntitiesDTO dto = GetEntitiesDTO.builder()
                             .entityResponses(Arrays.asList())
                             .pageNumber(0)
                             .totalElements(0)
                             .totalOwned(0)
                             .totalStarred(0)
                             .build();

    assertThat(dto.getEntityResponses()).isEmpty();
    assertThat(dto.getPageNumber()).isEqualTo(0);
    assertThat(dto.getTotalElements()).isEqualTo(0);
    assertThat(dto.getTotalOwned()).isEqualTo(0);
    assertThat(dto.getTotalStarred()).isEqualTo(0);
  }
}
