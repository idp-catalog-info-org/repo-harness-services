/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.ModuleSpecificPlanCreatorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ModuleSpecificPlanCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddStringParameter_whenKeyExists_shouldCallSetter() {
    Map<String, Object> info = new HashMap<>();
    info.put("serviceRef", "my-service");
    AtomicReference<ParameterField<String>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addStringParameter(info, "serviceRef", captured::set);

    assertThat(captured.get()).as("setter should have been called").isNotNull();
    assertThat(captured.get().getValue()).as("should wrap the string value").isEqualTo("my-service");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddStringParameter_whenKeyMissing_shouldNotCallSetter() {
    Map<String, Object> info = new HashMap<>();
    AtomicReference<ParameterField<String>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addStringParameter(info, "serviceRef", captured::set);

    assertThat(captured.get()).as("setter should not have been called").isNull();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddMapParameter_whenKeyExists_shouldCallSetter() {
    Map<String, Object> info = new HashMap<>();
    Map<String, Object> mapValue = Map.of("key1", "val1", "key2", "val2");
    info.put("variables", mapValue);
    AtomicReference<ParameterField<Map<String, Object>>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addMapParameter(info, "variables", captured::set);

    assertThat(captured.get()).as("setter should have been called").isNotNull();
    assertThat(captured.get().getValue()).as("should wrap the map value").isEqualTo(mapValue);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddMapParameter_whenKeyMissing_shouldNotCallSetter() {
    Map<String, Object> info = new HashMap<>();
    AtomicReference<ParameterField<Map<String, Object>>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addMapParameter(info, "variables", captured::set);

    assertThat(captured.get()).as("setter should not have been called").isNull();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddExpressionParameter_whenKeyExists_shouldCallSetterWithExpression() {
    Map<String, Object> info = new HashMap<>();
    info.put("envRef", "something");
    AtomicReference<ParameterField<String>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addExpressionParameter(
        info, "envRef", "<+pipeline.stages.deploy.envRef>", true, captured::set);

    assertThat(captured.get()).as("setter should have been called").isNotNull();
    assertThat(captured.get().isExpression()).as("should be an expression field").isTrue();
    assertThat(captured.get().getExpressionValue())
        .as("should contain the expression")
        .isEqualTo("<+pipeline.stages.deploy.envRef>");
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddExpressionParameter_whenKeyMissing_shouldNotCallSetter() {
    Map<String, Object> info = new HashMap<>();
    AtomicReference<ParameterField<String>> captured = new AtomicReference<>();

    ModuleSpecificPlanCreatorUtils.addExpressionParameter(
        info, "envRef", "<+pipeline.stages.deploy.envRef>", true, captured::set);

    assertThat(captured.get()).as("setter should not have been called").isNull();
  }
}
