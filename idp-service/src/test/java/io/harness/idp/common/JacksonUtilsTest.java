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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class JacksonUtilsTest extends CategoryTest {
  static class TestEntity {
    private String name;
    private int value;

    public TestEntity() {}
    public TestEntity(String name, int value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }
    public int getValue() {
      return value;
    }
    public void setValue(int value) {
      this.value = value;
    }
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValue() {
    String json = "[{\"name\":\"test1\",\"value\":100},{\"name\":\"test2\",\"value\":200}]";
    List<TestEntity> entities = JacksonUtils.readValue(json, TestEntity.class);

    assertThat(entities).isNotNull();
    assertThat(entities).hasSize(2);
    assertThat(entities.get(0).getName()).isEqualTo("test1");
    assertThat(entities.get(0).getValue()).isEqualTo(100);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValueWithInvalidJson() {
    String invalidJson = "invalid json";
    assertThatThrownBy(() -> JacksonUtils.readValue(invalidJson, TestEntity.class))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Error in readValue");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValueForSingleEntity() {
    String json = "{\"name\":\"test\",\"value\":123}";
    TestEntity entity = JacksonUtils.readValueForSingleEntity(json, TestEntity.class);

    assertThat(entity).isNotNull();
    assertThat(entity.getName()).isEqualTo("test");
    assertThat(entity.getValue()).isEqualTo(123);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValueForSingleEntityWithInvalidJson() {
    String invalidJson = "invalid json";
    assertThatThrownBy(() -> JacksonUtils.readValueForSingleEntity(invalidJson, TestEntity.class))
        .isInstanceOf(UnexpectedException.class);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValueForObject() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("value", 123);

    TestEntity entity = JacksonUtils.readValueForObject(map, TestEntity.class);

    assertThat(entity).isNotNull();
    assertThat(entity.getName()).isEqualTo("test");
    assertThat(entity.getValue()).isEqualTo(123);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testReadValueForObjectWithNull() {
    TestEntity entity = JacksonUtils.readValueForObject(null, TestEntity.class);
    assertThat(entity).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testConvert() {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("name", "test1");
    map1.put("value", 100);

    Map<String, Object> map2 = new HashMap<>();
    map2.put("name", "test2");
    map2.put("value", 200);

    List<Map<String, Object>> entities = List.of(map1, map2);
    List<TestEntity> converted = JacksonUtils.convert(entities, TestEntity.class);

    assertThat(converted).isNotNull();
    assertThat(converted).hasSize(2);
    assertThat(converted.get(0).getName()).isEqualTo("test1");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testConvertWithObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("value", 123);

    List<Map<String, Object>> entities = List.of(map);
    List<TestEntity> converted = JacksonUtils.convert(mapper, entities, TestEntity.class);

    assertThat(converted).isNotNull();
    assertThat(converted).hasSize(1);
    assertThat(converted.get(0).getName()).isEqualTo("test");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testWrite() {
    TestEntity entity = new TestEntity("test", 123);
    String json = JacksonUtils.write(entity);

    assertThat(json).isNotNull();
    assertThat(json).contains("test");
    assertThat(json).contains("123");
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testConvertEntityToMap() {
    TestEntity entity = new TestEntity("test", 123);
    Map<String, Object> map = JacksonUtils.convert(entity);

    assertThat(map).isNotNull();
    assertThat(map.get("name")).isEqualTo("test");
    assertThat(map.get("value")).isEqualTo(123);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testConvertMapToObject() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("value", 123);

    Object result = JacksonUtils.convert(map);
    assertThat(result).isNotNull();
  }
}
