/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class DefaultSettingsUtilsTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseIntSettingReturnsNullForNull() {
    assertThat(DefaultSettingsUtils.parseIntSetting(null)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseIntSettingReturnsNullForEmpty() {
    assertThat(DefaultSettingsUtils.parseIntSetting("")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseIntSettingReturnsValueForValidInt() {
    assertThat(DefaultSettingsUtils.parseIntSetting("1000")).isEqualTo(1000);
    assertThat(DefaultSettingsUtils.parseIntSetting(" 42 ")).isEqualTo(42);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseIntSettingReturnsNullForInvalidInt() {
    assertThat(DefaultSettingsUtils.parseIntSetting("abc")).isNull();
    assertThat(DefaultSettingsUtils.parseIntSetting("12.5")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseBoolSettingReturnsNullForNull() {
    assertThat(DefaultSettingsUtils.parseBoolSetting(null)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseBoolSettingReturnsNullForEmpty() {
    assertThat(DefaultSettingsUtils.parseBoolSetting("")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseBoolSettingReturnsParsedValue() {
    assertThat(DefaultSettingsUtils.parseBoolSetting("true")).isTrue();
    assertThat(DefaultSettingsUtils.parseBoolSetting(" true ")).isTrue();
    assertThat(DefaultSettingsUtils.parseBoolSetting("false")).isFalse();
    assertThat(DefaultSettingsUtils.parseBoolSetting("invalid")).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseCommaSeparatedReturnsNullForNull() {
    assertThat(DefaultSettingsUtils.parseCommaSeparated(null)).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseCommaSeparatedReturnsNullForEmpty() {
    assertThat(DefaultSettingsUtils.parseCommaSeparated("")).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseCommaSeparatedReturnsSingleValue() {
    List<String> result = DefaultSettingsUtils.parseCommaSeparated("NET_ADMIN");
    assertThat(result).containsExactly("NET_ADMIN");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseCommaSeparatedReturnsMultipleValues() {
    List<String> result = DefaultSettingsUtils.parseCommaSeparated("NET_ADMIN, SYS_TIME , CHOWN");
    assertThat(result).containsExactly("NET_ADMIN", "SYS_TIME", "CHOWN");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testParseCommaSeparatedFiltersEmptyEntries() {
    List<String> result = DefaultSettingsUtils.parseCommaSeparated("NET_ADMIN,,, CHOWN");
    assertThat(result).containsExactly("NET_ADMIN", "CHOWN");
  }
}
