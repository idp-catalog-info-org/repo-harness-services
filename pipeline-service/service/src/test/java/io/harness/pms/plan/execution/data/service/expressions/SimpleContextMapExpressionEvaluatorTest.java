/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.expressions;

import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.expression.common.ExpressionMode;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class SimpleContextMapExpressionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testConstructorInitialization() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    assertThat(evaluator).isNotNull();
    assertThat(evaluator.getVariableResolverTracker()).isNotNull(); // inherited from base class
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testInitializeAddsCorrectContextToEvaluator() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("testKey", "testValue");
    contextMap.put("numericKey", 42);

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // The initialize method is called automatically by the base class
    // We can verify it worked by checking if we can resolve expressions

    String expression = "<+testKey>";
    Object result = evaluator.evaluateExpression(expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    assertThat(result).isEqualTo("testValue");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExpressionResolutionWithSimpleValues() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("environment", "production");
    contextMap.put("timeout", 30);
    contextMap.put("enabled", true);

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // Test string value resolution
    assertThat(evaluator.evaluateExpression("<+environment>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("production");

    // Test numeric value resolution
    assertThat(evaluator.evaluateExpression("<+timeout>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo(30);

    // Test boolean value resolution
    assertThat(evaluator.evaluateExpression("<+enabled>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExpressionResolutionWithNestedMap() {
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("host", "localhost");
    innerMap.put("port", 8080);

    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("database", innerMap);
    contextMap.put("simple", "value");

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // Test access to nested values
    assertThat(evaluator.evaluateExpression("<+database.host>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("localhost");

    assertThat(evaluator.evaluateExpression("<+database.port>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo(8080);

    // Test access to simple value alongside nested
    assertThat(evaluator.evaluateExpression("<+simple>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("value");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExpressionResolutionWithEmptyMap() {
    Map<String, Object> contextMap = Collections.emptyMap();

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // Should return null for non-existent keys
    assertThat(evaluator.evaluateExpression("<+nonexistent>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isNull();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testExpressionResolutionWithNullValues() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("nullKey", null);
    contextMap.put("validKey", "validValue");

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // Should return null for null values
    assertThat(evaluator.evaluateExpression("<+nullKey>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isNull();

    // Should return valid value for non-null keys
    assertThat(evaluator.evaluateExpression("<+validKey>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("validValue");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testUnresolvedExpressionHandling() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("existingKey", "existingValue");

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // Test non-existent key returns null
    assertThat(evaluator.evaluateExpression("<+nonexistent>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isNull();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testContextMapIsDirectlyAccessible() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("directKey", "directValue");

    SimpleContextMapExpressionEvaluator evaluator = new SimpleContextMapExpressionEvaluator(contextMap);

    // The context should be directly accessible
    Object result = evaluator.evaluateExpression("<+directKey>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    assertThat(result).isEqualTo("directValue");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testWithDifferentContextMaps() {
    Map<String, Object> contextMap1 = new HashMap<>();
    contextMap1.put("key1", "value1");

    Map<String, Object> contextMap2 = new HashMap<>();
    contextMap2.put("key2", "value2");

    // Test with different context maps
    SimpleContextMapExpressionEvaluator evaluator1 = new SimpleContextMapExpressionEvaluator(contextMap1);
    SimpleContextMapExpressionEvaluator evaluator2 = new SimpleContextMapExpressionEvaluator(contextMap2);

    // Each should resolve their own context
    assertThat(evaluator1.evaluateExpression("<+key1>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("value1");

    assertThat(evaluator2.evaluateExpression("<+key2>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("value2");

    // But they shouldn't cross-resolve
    assertThat(evaluator1.evaluateExpression("<+key2>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isNull();

    assertThat(evaluator2.evaluateExpression("<+key1>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isNull();
  }
}