/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GsonUtilsTest extends CategoryTest {
  static class TestObject {
    private String name;
    private int value;

    public TestObject() {}
    public TestObject(String name, int value) {
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
  public void testConvertJsonStringToObject() {
    String json = "{\"name\":\"test\",\"value\":123}";
    TestObject result = GsonUtils.convertJsonStringToObject(json, TestObject.class);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("test");
    assertThat(result.getValue()).isEqualTo(123);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testConvertJsonStringToObjectWithNull() {
    TestObject result = GsonUtils.convertJsonStringToObject(null, TestObject.class);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetJSONObjectFromObject() {
    TestObject testObject = new TestObject("test", 123);

    // Create a map wrapper instead since Gson serialization requires proper structure
    java.util.Map<String, Object> wrapper = new java.util.HashMap<>();
    wrapper.put("data", testObject);

    JSONObject result = GsonUtils.getJSONObjectFromObject(wrapper, "data");

    assertThat(result).isNotNull();
    assertThat(result.getString("name")).isEqualTo("test");
    assertThat(result.getInt("value")).isEqualTo(123);
  }
}
