/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageDTOExtendedTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_MODULE = "IDP";
  static final long TEST_TIMESTAMP = 1698294600000L;
  static final String TEST_DISPLAY_NAME = "Last 30 Days";
  static final long TEST_COUNT = 100L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderWithAllFields() {
    UsageDataDTO usageDataDTO = UsageDataDTO.builder().displayName(TEST_DISPLAY_NAME).count(TEST_COUNT).build();

    IDPLicenseUsageDTO dto = IDPLicenseUsageDTO.builder()
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .module(TEST_MODULE)
                                 .timestamp(TEST_TIMESTAMP)
                                 .activeDevelopers(usageDataDTO)
                                 .build();

    assertNotNull(dto);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, dto.getAccountIdentifier());
    assertEquals(TEST_MODULE, dto.getModule());
    assertEquals(TEST_TIMESTAMP, dto.getTimestamp());
    assertNotNull(dto.getActiveDevelopers());
    assertEquals(TEST_DISPLAY_NAME, dto.getActiveDevelopers().getDisplayName());
    assertEquals(TEST_COUNT, dto.getActiveDevelopers().getCount());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    UsageDataDTO usageDataDTO = UsageDataDTO.builder().displayName(TEST_DISPLAY_NAME).count(TEST_COUNT).build();

    IDPLicenseUsageDTO dto = IDPLicenseUsageDTO.builder().build();
    dto.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    dto.setModule(TEST_MODULE);
    dto.setTimestamp(TEST_TIMESTAMP);
    dto.setActiveDevelopers(usageDataDTO);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, dto.getAccountIdentifier());
    assertEquals(TEST_MODULE, dto.getModule());
    assertEquals(TEST_TIMESTAMP, dto.getTimestamp());
    assertNotNull(dto.getActiveDevelopers());
  }
}
