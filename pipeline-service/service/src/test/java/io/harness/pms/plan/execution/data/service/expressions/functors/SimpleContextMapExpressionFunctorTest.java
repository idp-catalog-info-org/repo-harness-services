/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.expressions.functors;

import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class SimpleContextMapExpressionFunctorTest extends CategoryTest {
  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBindWithEmptyMap() {
    Map<String, Object> contextMap = Collections.emptyMap();
    SimpleContextMapExpressionFunctor functor = new SimpleContextMapExpressionFunctor(contextMap);

    Object result = functor.bind();

    assertThat(result).isEqualTo(contextMap);
    assertThat(result).isSameAs(contextMap);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBindWithSimpleValues() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");
    contextMap.put("key2", 123);
    contextMap.put("key3", true);

    SimpleContextMapExpressionFunctor functor = new SimpleContextMapExpressionFunctor(contextMap);

    Object result = functor.bind();

    assertThat(result).isEqualTo(contextMap);
    assertThat(result).isSameAs(contextMap);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBindWithNestedMap() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("nested1", "nestedValue1");
    nestedMap.put("nested2", 456);

    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("simpleKey", "simpleValue");
    contextMap.put("complexKey", nestedMap);

    SimpleContextMapExpressionFunctor functor = new SimpleContextMapExpressionFunctor(contextMap);

    Object result = functor.bind();

    assertThat(result).isEqualTo(contextMap);
    assertThat(result).isSameAs(contextMap);

    // Verify nested structure is preserved
    @SuppressWarnings("unchecked") Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("simpleKey")).isEqualTo("simpleValue");
    assertThat(resultMap.get("complexKey")).isEqualTo(nestedMap);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBindWithNullValues() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("nullKey", null);
    contextMap.put("validKey", "validValue");

    SimpleContextMapExpressionFunctor functor = new SimpleContextMapExpressionFunctor(contextMap);

    Object result = functor.bind();

    assertThat(result).isEqualTo(contextMap);
    assertThat(result).isSameAs(contextMap);

    @SuppressWarnings("unchecked") Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("nullKey")).isNull();
    assertThat(resultMap.get("validKey")).isEqualTo("validValue");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testConstructorWithNullMap() {
    SimpleContextMapExpressionFunctor functor = new SimpleContextMapExpressionFunctor(null);

    Object result = functor.bind();

    assertThat(result).isNull();
  }
}
