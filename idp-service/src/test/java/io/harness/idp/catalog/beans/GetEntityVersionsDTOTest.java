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
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class GetEntityVersionsDTOTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityVersionsDTOBuilder() {
    EntityVersionResponse version1 = new EntityVersionResponse();
    version1.setVersion("1.0.0");
    version1.setIdentifier("version1");

    EntityVersionResponse version2 = new EntityVersionResponse();
    version2.setVersion("2.0.0");
    version2.setIdentifier("version2");

    List<EntityVersionResponse> versions = Arrays.asList(version1, version2);

    GetEntityVersionsDTO dto =
        GetEntityVersionsDTO.builder().entityVersionResponses(versions).pageNumber(1).totalElements(2).build();

    assertThat(dto.getEntityVersionResponses()).isEqualTo(versions);
    assertThat(dto.getEntityVersionResponses()).hasSize(2);
    assertThat(dto.getPageNumber()).isEqualTo(1);
    assertThat(dto.getTotalElements()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityVersionsDTOSettersAndGetters() {
    GetEntityVersionsDTO dto = new GetEntityVersionsDTO();

    EntityVersionResponse version = new EntityVersionResponse();
    version.setVersion("1.0.0");
    version.setIdentifier("version1");

    List<EntityVersionResponse> versions = Arrays.asList(version);

    dto.setEntityVersionResponses(versions);
    dto.setPageNumber(2);
    dto.setTotalElements(1);

    assertThat(dto.getEntityVersionResponses()).isEqualTo(versions);
    assertThat(dto.getEntityVersionResponses()).hasSize(1);
    assertThat(dto.getPageNumber()).isEqualTo(2);
    assertThat(dto.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityVersionsDTOWithNullVersions() {
    GetEntityVersionsDTO dto =
        GetEntityVersionsDTO.builder().entityVersionResponses(null).pageNumber(0).totalElements(0).build();

    assertThat(dto.getEntityVersionResponses()).isNull();
    assertThat(dto.getPageNumber()).isEqualTo(0);
    assertThat(dto.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityVersionsDTOWithEmptyVersions() {
    GetEntityVersionsDTO dto =
        GetEntityVersionsDTO.builder().entityVersionResponses(Arrays.asList()).pageNumber(0).totalElements(0).build();

    assertThat(dto.getEntityVersionResponses()).isEmpty();
    assertThat(dto.getPageNumber()).isEqualTo(0);
    assertThat(dto.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityVersionsDTOWithMultipleVersions() {
    EntityVersionResponse version1 = new EntityVersionResponse();
    version1.setVersion("1.0.0");
    version1.setIdentifier("version1");

    EntityVersionResponse version2 = new EntityVersionResponse();
    version2.setVersion("2.0.0");
    version2.setIdentifier("version2");

    EntityVersionResponse version3 = new EntityVersionResponse();
    version3.setVersion("3.0.0");
    version3.setIdentifier("version3");

    List<EntityVersionResponse> versions = Arrays.asList(version1, version2, version3);

    GetEntityVersionsDTO dto =
        GetEntityVersionsDTO.builder().entityVersionResponses(versions).pageNumber(1).totalElements(3).build();

    assertThat(dto.getEntityVersionResponses()).hasSize(3);
    assertThat(dto.getEntityVersionResponses()).containsExactlyInAnyOrder(version1, version2, version3);
    assertThat(dto.getPageNumber()).isEqualTo(1);
    assertThat(dto.getTotalElements()).isEqualTo(3);
  }
}
