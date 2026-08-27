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

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersTrendResponseTest extends CategoryTest {
  static final String TEST_DATE_1 = "2023-10-23";
  static final String TEST_DATE_2 = "2023-10-24";
  static final long TEST_COUNT_1 = 50L;
  static final long TEST_COUNT_2 = 75L;
  static final long TEST_PEAK = 75L;
  static final long TEST_TOTAL_UNIQUE_DEVELOPERS = 100L;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    List<ActiveDevelopersTrendCountDTO> trendList = new ArrayList<>();
    trendList.add(ActiveDevelopersTrendCountDTO.builder().date(TEST_DATE_1).count(TEST_COUNT_1).build());
    trendList.add(ActiveDevelopersTrendCountDTO.builder().date(TEST_DATE_2).count(TEST_COUNT_2).build());

    ActiveDevelopersTrendResponse response = ActiveDevelopersTrendResponse.builder()
                                                 .activeDevelopersTrendList(trendList)
                                                 .peak(TEST_PEAK)
                                                 .totalUniqueDevelopers(TEST_TOTAL_UNIQUE_DEVELOPERS)
                                                 .build();

    assertNotNull(response);
    assertNotNull(response.getActiveDevelopersTrendList());
    assertEquals(2, response.getActiveDevelopersTrendList().size());
    assertEquals(TEST_PEAK, response.getPeak());
    assertEquals(TEST_TOTAL_UNIQUE_DEVELOPERS, response.getTotalUniqueDevelopers());
    assertEquals(TEST_DATE_1, response.getActiveDevelopersTrendList().get(0).getDate());
    assertEquals(TEST_COUNT_1, response.getActiveDevelopersTrendList().get(0).getCount());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetters() {
    List<ActiveDevelopersTrendCountDTO> trendList = new ArrayList<>();
    trendList.add(ActiveDevelopersTrendCountDTO.builder().date(TEST_DATE_1).count(TEST_COUNT_1).build());

    ActiveDevelopersTrendResponse response = ActiveDevelopersTrendResponse.builder().build();
    response.setActiveDevelopersTrendList(trendList);
    response.setPeak(TEST_PEAK);
    response.setTotalUniqueDevelopers(TEST_TOTAL_UNIQUE_DEVELOPERS);

    assertNotNull(response.getActiveDevelopersTrendList());
    assertEquals(1, response.getActiveDevelopersTrendList().size());
    assertEquals(TEST_PEAK, response.getPeak());
    assertEquals(TEST_TOTAL_UNIQUE_DEVELOPERS, response.getTotalUniqueDevelopers());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderWithEmptyTrendList() {
    ActiveDevelopersTrendResponse response = ActiveDevelopersTrendResponse.builder()
                                                 .activeDevelopersTrendList(new ArrayList<>())
                                                 .peak(0L)
                                                 .totalUniqueDevelopers(0L)
                                                 .build();

    assertNotNull(response);
    assertNotNull(response.getActiveDevelopersTrendList());
    assertEquals(0, response.getActiveDevelopersTrendList().size());
    assertEquals(0L, response.getPeak());
    assertEquals(0L, response.getTotalUniqueDevelopers());
  }
}
