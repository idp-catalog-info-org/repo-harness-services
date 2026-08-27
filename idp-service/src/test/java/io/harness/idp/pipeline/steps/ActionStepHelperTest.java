/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;
import static io.harness.rule.OwnerRule.ARYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ActionStepHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void substituteInputs_replacesPlaceholders() {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("serviceId", "svc-1");
    inputs.put("env", "prod");

    String resolved = ActionStepHelper.substituteInputs("/services/${{input.serviceId}}/env/${{input.env}}", inputs);

    assertThat(resolved).isEqualTo("/services/svc-1/env/prod");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void substituteInputs_missingKeyBecomesEmptyString() {
    String resolved = ActionStepHelper.substituteInputs("/x/${{input.absent}}/y", Collections.emptyMap());
    assertThat(resolved).isEqualTo("/x//y");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void substituteInputs_returnsTemplateUntouchedWhenNoPlaceholders() {
    Map<String, String> inputs = new HashMap<>();
    inputs.put("k", "v");
    assertThat(ActionStepHelper.substituteInputs("static-text", inputs)).isEqualTo("static-text");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void substituteInputs_handlesNullAndEmpty() {
    assertThat(ActionStepHelper.substituteInputs(null, Collections.singletonMap("k", "v"))).isNull();
    assertThat(ActionStepHelper.substituteInputs("", Collections.singletonMap("k", "v"))).isEqualTo("");
    assertThat(ActionStepHelper.substituteInputs("${{input.k}}", null)).isEqualTo("");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void composeUrl_joinsBaseAndPathWithSingleSlash() {
    Map<String, String> inputs = Collections.singletonMap("id", "42");

    assertThat(ActionStepHelper.composeUrl("https://api.example.com", "/incidents/${{input.id}}", null, inputs))
        .isEqualTo("https://api.example.com/incidents/42");
    assertThat(ActionStepHelper.composeUrl("https://api.example.com/", "incidents/${{input.id}}", null, inputs))
        .isEqualTo("https://api.example.com/incidents/42");
    assertThat(ActionStepHelper.composeUrl("https://api.example.com/", "/incidents/${{input.id}}", null, inputs))
        .isEqualTo("https://api.example.com/incidents/42");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void composeUrl_emptyPathReturnsTrimmedBase() {
    assertThat(ActionStepHelper.composeUrl("https://api.example.com/", "", null, Collections.emptyMap()))
        .isEqualTo("https://api.example.com");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void composeUrl_absoluteOverrideWins() {
    String composed = ActionStepHelper.composeUrl("https://api.example.com", "/ignored",
        "https://other.example.com/abs/${{input.x}}", Collections.singletonMap("x", "1"));

    assertThat(composed).isEqualTo("https://other.example.com/abs/1");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void toHttpHeaderConfigList_preservesNullValuesAsEmpty() {
    Map<String, String> input = new HashMap<>();
    input.put("X-First", "v1");
    input.put("X-Second", null);

    java.util.List<io.harness.http.HttpHeaderConfig> out = ActionStepHelper.toHttpHeaderConfigList(input);

    assertThat(out).hasSize(2);
    assertThat(out)
        .extracting(io.harness.http.HttpHeaderConfig::getKey)
        .containsExactlyInAnyOrder("X-First", "X-Second");
    assertThat(out).extracting(io.harness.http.HttpHeaderConfig::getValue).containsExactlyInAnyOrder("v1", "");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void toJsonPointer_acceptsBothPointerAndDottedAndJsonPath() {
    assertThat(ActionStepHelper.toJsonPointer("/foo/bar")).isEqualTo("/foo/bar");
    assertThat(ActionStepHelper.toJsonPointer("foo.bar")).isEqualTo("/foo/bar");
    assertThat(ActionStepHelper.toJsonPointer("$.foo.bar")).isEqualTo("/foo/bar");
    assertThat(ActionStepHelper.toJsonPointer("foo.0.bar")).isEqualTo("/foo/0/bar");
    assertThat(ActionStepHelper.toJsonPointer("")).isEqualTo("");
    assertThat(ActionStepHelper.toJsonPointer(null)).isEqualTo("");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void toJsonPointer_escapesTildeAndSlashPerRfc6901() {
    assertThat(ActionStepHelper.toJsonPointer("a~b.c")).isEqualTo("/a~0b/c");
    assertThat(ActionStepHelper.toJsonPointer("a/b.c")).isEqualTo("/a~1b/c");
    assertThat(ActionStepHelper.toJsonPointer("$.with~tilde.next")).isEqualTo("/with~0tilde/next");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void toNativeValue_preservesTypes() {
    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode tree;
    try {
      tree = om.readTree("{\"s\":\"hello\",\"i\":42,\"l\":9999999999,\"d\":3.14,\"b\":true,\"o\":{\"k\":\"v\"}}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertThat(ActionStepHelper.toNativeValue(tree.get("s"))).isEqualTo("hello");
    assertThat(ActionStepHelper.toNativeValue(tree.get("i"))).isEqualTo(42);
    assertThat(ActionStepHelper.toNativeValue(tree.get("l"))).isEqualTo(9999999999L);
    assertThat(ActionStepHelper.toNativeValue(tree.get("d"))).isEqualTo(3.14);
    assertThat(ActionStepHelper.toNativeValue(tree.get("b"))).isEqualTo(true);
    assertThat(ActionStepHelper.toNativeValue(tree.get("o"))).isEqualTo("{\"k\":\"v\"}");
  }
}
