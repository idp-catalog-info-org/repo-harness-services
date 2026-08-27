/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.rule.Owner;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPModuleLicenseUsageTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_EMAIL = "test@example.com";
  static final String TEST_USER_NAME = "Test User";
  static final long TEST_ACCESSED_AT = 1698294600000L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testIDPLicenseUsageUserCaptureDTOBuilder() {
    IDPLicenseUsageUserCaptureDTO dto = IDPLicenseUsageUserCaptureDTO.builder()
                                            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .userIdentifier(TEST_USER_IDENTIFIER)
                                            .email(TEST_EMAIL)
                                            .userName(TEST_USER_NAME)
                                            .accessedAt(TEST_ACCESSED_AT)
                                            .build();

    assertThat(dto).isNotNull();
    assertThat(dto.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(dto.getUserIdentifier()).isEqualTo(TEST_USER_IDENTIFIER);
    assertThat(dto.getEmail()).isEqualTo(TEST_EMAIL);
    assertThat(dto.getUserName()).isEqualTo(TEST_USER_NAME);
    assertThat(dto.getAccessedAt()).isEqualTo(TEST_ACCESSED_AT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testActiveDevelopersTrendCountDTOListOperations() {
    List<ActiveDevelopersTrendCountDTO> trendList =
        List.of(ActiveDevelopersTrendCountDTO.builder().date("2023-10-26").count(10L).build(),
            ActiveDevelopersTrendCountDTO.builder().date("2023-10-27").count(15L).build(),
            ActiveDevelopersTrendCountDTO.builder().date("2023-10-28").count(20L).build());

    assertThat(trendList).hasSize(3);
    assertThat(trendList.get(0).getCount()).isEqualTo(10L);
    assertThat(trendList.get(1).getCount()).isEqualTo(15L);
    assertThat(trendList.get(2).getCount()).isEqualTo(20L);
  }
}
