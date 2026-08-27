/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class YamlUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testYamlObject() {
    assertThat(YamlUtils.yamlObject()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testWriteObjectAsYaml() {
    Map<String, Object> data = new HashMap<>();
    data.put("name", "test");
    data.put("value", 123);

    String yaml = YamlUtils.writeObjectAsYaml(data);
    assertThat(yaml).isNotNull();
    assertThat(yaml).contains("name");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadYaml() {
    String yamlString = "name: test\nvalue: 123";

    Map<String, Object> result = YamlUtils.read(yamlString, Map.class);
    assertThat(result).isNotNull();
    assertThat(result.get("name")).isEqualTo("test");
    assertThat(result.get("value")).isEqualTo(123);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadWithInvalidYaml() {
    String invalidYaml = "invalid: yaml: content: [";

    assertThatThrownBy(() -> YamlUtils.read(invalidYaml, Map.class))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Error reading the content");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testLoadYamlStringAsMap() {
    String yamlString = "key1: value1\nkey2: value2";

    Map<String, Object> result = YamlUtils.loadYamlStringAsMap(yamlString);
    assertThat(result).isNotNull();
    assertThat(result.get("key1")).isEqualTo("value1");
    assertThat(result.get("key2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeWithSimpleValues() {
    Map<String, Object> base = new LinkedHashMap<>();
    base.put("key1", "value1");
    base.put("key2", "value2");

    Map<String, Object> additional = new LinkedHashMap<>();
    additional.put("key3", "value3");

    Map<String, Object> merged = YamlUtils.merge(base, additional);

    assertThat(merged).hasSize(3);
    assertThat(merged.get("key1")).isEqualTo("value1");
    assertThat(merged.get("key2")).isEqualTo("value2");
    assertThat(merged.get("key3")).isEqualTo("value3");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeWithNestedMaps() {
    Map<String, Object> baseNested = new LinkedHashMap<>();
    baseNested.put("nestedKey", "nestedValue");

    Map<String, Object> base = new LinkedHashMap<>();
    base.put("key1", "value1");
    base.put("nested", baseNested);

    Map<String, Object> additionalNested = new LinkedHashMap<>();
    additionalNested.put("nestedKey2", "nestedValue2");

    Map<String, Object> additional = new LinkedHashMap<>();
    additional.put("nested", additionalNested);

    Map<String, Object> merged = YamlUtils.merge(base, additional);

    assertThat(merged).hasSize(2);
    Map<String, Object> mergedNested = (Map<String, Object>) merged.get("nested");
    assertThat(mergedNested).hasSize(2);
    assertThat(mergedNested.get("nestedKey")).isEqualTo("nestedValue");
    assertThat(mergedNested.get("nestedKey2")).isEqualTo("nestedValue2");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeWithLists() {
    Map<String, Object> base = new LinkedHashMap<>();
    base.put("list", new ArrayList<>(Arrays.asList("item1", "item2")));

    Map<String, Object> additional = new LinkedHashMap<>();
    additional.put("list", new ArrayList<>(Arrays.asList("item3", "item2")));

    Map<String, Object> merged = YamlUtils.merge(base, additional);

    List<Object> mergedList = (List<Object>) merged.get("list");
    assertThat(mergedList).hasSize(3);
    assertThat(mergedList).contains("item1", "item2", "item3");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeDecoratorWithMaps() {
    Map<String, Object> yamlMap = new LinkedHashMap<>();
    yamlMap.put("key1", "value1");

    Map<String, Object> decorator = new LinkedHashMap<>();
    decorator.put("key2", "value2");

    Map<String, Object> result = YamlUtils.mergeDecorator(yamlMap, decorator);

    assertThat(result).hasSize(2);
    assertThat(result.get("key1")).isEqualTo("value1");
    assertThat(result.get("key2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeDecoratorWithNestedMaps() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("nestedKey", "nestedValue");

    Map<String, Object> yamlMap = new LinkedHashMap<>();
    yamlMap.put("nested", nested);

    Map<String, Object> decoratorNested = new LinkedHashMap<>();
    decoratorNested.put("nestedKey2", "nestedValue2");

    Map<String, Object> decorator = new LinkedHashMap<>();
    decorator.put("nested", decoratorNested);

    Map<String, Object> result = YamlUtils.mergeDecorator(yamlMap, decorator);

    Map<String, Object> resultNested = (Map<String, Object>) result.get("nested");
    assertThat(resultNested).hasSize(2);
    assertThat(resultNested.get("nestedKey")).isEqualTo("nestedValue");
    assertThat(resultNested.get("nestedKey2")).isEqualTo("nestedValue2");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeDecoratorWithLists() {
    Map<String, Object> yamlMap = new LinkedHashMap<>();
    yamlMap.put("list", new ArrayList<>(Arrays.asList("item1", "item2")));

    Map<String, Object> decorator = new LinkedHashMap<>();
    decorator.put("list", new ArrayList<>(Arrays.asList("item3")));

    Map<String, Object> result = YamlUtils.mergeDecorator(yamlMap, decorator);

    List<Object> resultList = (List<Object>) result.get("list");
    assertThat(resultList).hasSize(3);
    assertThat(resultList).contains("item1", "item2", "item3");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeDecoratorWithNullBaseMap() {
    // Entity-creation case: the base decorator is null. Merge must not NPE and should return the decorator content.
    Map<String, Object> decorator = new LinkedHashMap<>();
    decorator.put("key1", "value1");

    Map<String, Object> result = YamlUtils.mergeDecorator(null, decorator);

    assertThat(result).hasSize(1);
    assertThat(result.get("key1")).isEqualTo("value1");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMergeDecoratorWithNullDecorator() {
    Map<String, Object> yamlMap = new LinkedHashMap<>();
    yamlMap.put("key1", "value1");

    Map<String, Object> result = YamlUtils.mergeDecorator(yamlMap, null);

    assertThat(result).hasSize(1);
    assertThat(result.get("key1")).isEqualTo("value1");
  }
}
