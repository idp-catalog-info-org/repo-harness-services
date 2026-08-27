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
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponse;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class GetEntitiesGroupsDTOTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesGroupsDTOBuilder() {
    EntitiesGroupsResponse response = new EntitiesGroupsResponse();

    GetEntitiesGroupsDTO dto =
        GetEntitiesGroupsDTO.builder().entitiesGroupsResponse(response).totalOwned(5).totalStarred(3).build();

    assertThat(dto).isNotNull();
    assertThat(dto.getEntitiesGroupsResponse()).isEqualTo(response);
    assertThat(dto.getTotalOwned()).isEqualTo(5);
    assertThat(dto.getTotalStarred()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesGroupsDTOSettersAndGetters() {
    GetEntitiesGroupsDTO dto = new GetEntitiesGroupsDTO();
    EntitiesGroupsResponse response = new EntitiesGroupsResponse();

    dto.setEntitiesGroupsResponse(response);
    dto.setTotalOwned(10);
    dto.setTotalStarred(7);

    assertThat(dto.getEntitiesGroupsResponse()).isEqualTo(response);
    assertThat(dto.getTotalOwned()).isEqualTo(10);
    assertThat(dto.getTotalStarred()).isEqualTo(7);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesGroupsDTOWithNullResponse() {
    GetEntitiesGroupsDTO dto =
        GetEntitiesGroupsDTO.builder().entitiesGroupsResponse(null).totalOwned(0).totalStarred(0).build();

    assertThat(dto.getEntitiesGroupsResponse()).isNull();
    assertThat(dto.getTotalOwned()).isEqualTo(0);
    assertThat(dto.getTotalStarred()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntitiesGroupsDTOAllArgsConstructor() {
    EntitiesGroupsResponse response = new EntitiesGroupsResponse();

    GetEntitiesGroupsDTO dto = new GetEntitiesGroupsDTO(response, 15, 8);

    assertThat(dto.getEntitiesGroupsResponse()).isEqualTo(response);
    assertThat(dto.getTotalOwned()).isEqualTo(15);
    assertThat(dto.getTotalStarred()).isEqualTo(8);
  }
}
