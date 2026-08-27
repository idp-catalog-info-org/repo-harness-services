/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class LicenseUsageUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchQueryWithFilters_nullModuleType_omitsModuleTypeClause() {
    String query = LicenseUsageUtils.fetchQueryWithFilters("license_usage_daily", null, Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1609459200L, 1612137600L, false);

    assertThat(query).contains("WHERE account_identifier = ?");
    assertThat(query).doesNotContain("AND module_type = ?");
    assertThat(query).contains("module_type");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchQueryWithFilters_withModuleType_includesModuleTypeClause() {
    String query = LicenseUsageUtils.fetchQueryWithFilters("license_usage_daily", "CI", Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1609459200L, 1612137600L, false);

    assertThat(query).contains("AND module_type = ?");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchQueryWithFilters_withModuleTypeAndOrgFilter() {
    String query = LicenseUsageUtils.fetchQueryWithFilters("license_usage_daily", "STO", Arrays.asList("org1", "org2"),
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1609459200L, 1612137600L, false);

    assertThat(query).contains("AND module_type = ?");
    assertThat(query).contains("AND organization_identifier IN (");
  }
}
