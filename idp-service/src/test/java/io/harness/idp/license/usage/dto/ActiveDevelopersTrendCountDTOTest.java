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
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersTrendCountDTOTest extends CategoryTest {
  static final String TEST_DATE = "2023-10-26";
  static final long TEST_COUNT = 100L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    ActiveDevelopersTrendCountDTO dto =
        ActiveDevelopersTrendCountDTO.builder().date(TEST_DATE).count(TEST_COUNT).build();

    assertNotNull(dto);
    assertEquals(TEST_DATE, dto.getDate());
    assertEquals(TEST_COUNT, dto.getCount());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    ActiveDevelopersTrendCountDTO dto = ActiveDevelopersTrendCountDTO.builder().build();
    dto.setDate(TEST_DATE);
    dto.setCount(TEST_COUNT);

    assertEquals(TEST_DATE, dto.getDate());
    assertEquals(TEST_COUNT, dto.getCount());
  }
}
