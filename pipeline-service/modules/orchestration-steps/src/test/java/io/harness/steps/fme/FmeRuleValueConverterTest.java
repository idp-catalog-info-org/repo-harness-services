/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeRuleValueConverterTest extends CategoryTest {
  // ====================== asStringList ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsStringListWithStringList() {
    List<String> result = FmeRuleValueConverter.asStringList(Arrays.asList("a", "b", "c"));
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsStringListWithMixedList() {
    List<String> result = FmeRuleValueConverter.asStringList(Arrays.asList(1, "two", 3.0));
    assertThat(result).containsExactly("1", "two", "3.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsStringListWithEmptyList() {
    List<String> result = FmeRuleValueConverter.asStringList(Collections.emptyList());
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsStringListWithNonListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asStringList("not-a-list"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected a list");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsStringListWithNullThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asStringList(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  // ====================== asSingleString ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithString() {
    assertThat(FmeRuleValueConverter.asSingleString("hello")).isEqualTo("hello");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithNumber() {
    assertThat(FmeRuleValueConverter.asSingleString(42)).isEqualTo("42");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithSingleElementList() {
    assertThat(FmeRuleValueConverter.asSingleString(Collections.singletonList("val"))).isEqualTo("val");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithMultiElementListTakesFirst() {
    assertThat(FmeRuleValueConverter.asSingleString(Arrays.asList("first", "second"))).isEqualTo("first");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithEmptyListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asSingleString(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-empty list");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsSingleStringWithNullThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asSingleString(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-null");
  }

  // ====================== asBoolean ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithBooleanTrue() {
    assertThat(FmeRuleValueConverter.asBoolean(true)).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithBooleanFalse() {
    assertThat(FmeRuleValueConverter.asBoolean(false)).isFalse();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithStringTrue() {
    assertThat(FmeRuleValueConverter.asBoolean("true")).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithStringFalse() {
    assertThat(FmeRuleValueConverter.asBoolean("false")).isFalse();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithSingleElementList() {
    assertThat(FmeRuleValueConverter.asBoolean(Collections.singletonList("true"))).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithBooleanInList() {
    assertThat(FmeRuleValueConverter.asBoolean(Collections.singletonList(false))).isFalse();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithEmptyListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBoolean(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-empty list");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithIntegerThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBoolean(42))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected a boolean");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBooleanWithNullThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBoolean(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  // ====================== asLong ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithInteger() {
    assertThat(FmeRuleValueConverter.asLong(42)).isEqualTo(42L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithLong() {
    assertThat(FmeRuleValueConverter.asLong(1700000000000L)).isEqualTo(1700000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithDouble() {
    assertThat(FmeRuleValueConverter.asLong(3.14)).isEqualTo(3L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithValidString() {
    assertThat(FmeRuleValueConverter.asLong("123")).isEqualTo(123L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithInvalidStringThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asLong("not-a-number"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid number string");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithSingleElementList() {
    assertThat(FmeRuleValueConverter.asLong(Collections.singletonList("99"))).isEqualTo(99L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithNumberInList() {
    assertThat(FmeRuleValueConverter.asLong(Collections.singletonList(77))).isEqualTo(77L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithEmptyListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asLong(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-empty list");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithBooleanThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asLong(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected a number");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsLongWithNullThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asLong(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  // ====================== asBetweenMap ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithValidMap() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("from", 10);
    input.put("to", 100);

    Map<String, Object> result = FmeRuleValueConverter.asBetweenMap(input);
    assertThat(result.get("from")).isEqualTo(10);
    assertThat(result.get("to")).isEqualTo(100);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithStringValues() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("from", "1.0.0");
    input.put("to", "2.0.0");

    Map<String, Object> result = FmeRuleValueConverter.asBetweenMap(input);
    assertThat(result.get("from")).isEqualTo("1.0.0");
    assertThat(result.get("to")).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapMissingFromThrows() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("to", 100);

    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'from' and 'to'");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapMissingToThrows() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("from", 10);

    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'from' and 'to'");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapNullFromThrows() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("from", null);
    input.put("to", 100);

    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapNullToThrows() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("from", 10);
    input.put("to", null);

    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithTwoElementList() {
    Map<String, Object> result = FmeRuleValueConverter.asBetweenMap(Arrays.asList(10, 100));
    assertThat(result.get("from")).isEqualTo(10);
    assertThat(result.get("to")).isEqualTo(100);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithStringElementList() {
    Map<String, Object> result = FmeRuleValueConverter.asBetweenMap(Arrays.asList("1.0.0", "2.0.0"));
    assertThat(result.get("from")).isEqualTo("1.0.0");
    assertThat(result.get("to")).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithWrongSizeListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(Arrays.asList(1, 2, 3)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly 2 elements");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithSingleElementListThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(Collections.singletonList(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly 2 elements");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithStringThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap("not-a-map"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected a map");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testAsBetweenMapWithNullThrows() {
    assertThatThrownBy(() -> FmeRuleValueConverter.asBetweenMap(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }
}
