/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersTrendCountDTOV2Test extends CategoryTest {
  static final String TEST_DATE = "2023-10-26";
  static final long TEST_COUNT = 100L;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    Set<String> uniqueUserIds = new HashSet<>();
    uniqueUserIds.add("user1");
    uniqueUserIds.add("user2");
    uniqueUserIds.add("user3");

    ActiveDevelopersTrendCountDTOV2 dto = ActiveDevelopersTrendCountDTOV2.builder()
                                              .date(TEST_DATE)
                                              .count(TEST_COUNT)
                                              .uniqueUserIdentifiers(uniqueUserIds)
                                              .build();

    assertNotNull(dto);
    assertEquals(TEST_DATE, dto.getDate());
    assertEquals(TEST_COUNT, dto.getCount());
    assertNotNull(dto.getUniqueUserIdentifiers());
    assertEquals(3, dto.getUniqueUserIdentifiers().size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetters() {
    Set<String> uniqueUserIds = new HashSet<>();
    uniqueUserIds.add("user1");
    uniqueUserIds.add("user2");

    ActiveDevelopersTrendCountDTOV2 dto = ActiveDevelopersTrendCountDTOV2.builder().build();
    dto.setDate(TEST_DATE);
    dto.setCount(TEST_COUNT);
    dto.setUniqueUserIdentifiers(uniqueUserIds);

    assertEquals(TEST_DATE, dto.getDate());
    assertEquals(TEST_COUNT, dto.getCount());
    assertNotNull(dto.getUniqueUserIdentifiers());
    assertEquals(2, dto.getUniqueUserIdentifiers().size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithEmptyUniqueUsers() {
    ActiveDevelopersTrendCountDTOV2 dto = ActiveDevelopersTrendCountDTOV2.builder()
                                              .date(TEST_DATE)
                                              .count(0L)
                                              .uniqueUserIdentifiers(new HashSet<>())
                                              .build();

    assertNotNull(dto);
    assertEquals(TEST_DATE, dto.getDate());
    assertEquals(0L, dto.getCount());
    assertNotNull(dto.getUniqueUserIdentifiers());
    assertEquals(0, dto.getUniqueUserIdentifiers().size());
  }
}
