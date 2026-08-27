/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class MongoTypeConverterTest extends OrchestrationVisualizationTestBase {
  // ===================== extractLongFromExtendedJson =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractLongFromExtendedJson_numberLongString() {
    Map<String, Object> map = new HashMap<>();
    map.put("$numberLong", "1234567890123");
    assertThat(MongoTypeConverter.extractLongFromExtendedJson(map)).isEqualTo(1234567890123L);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractLongFromExtendedJson_numberLongNumber() {
    Map<String, Object> map = new HashMap<>();
    map.put("$numberLong", 987654321L);
    assertThat(MongoTypeConverter.extractLongFromExtendedJson(map)).isEqualTo(987654321L);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractLongFromExtendedJson_invalidNumberLong() {
    Map<String, Object> map = new HashMap<>();
    map.put("$numberLong", "not-a-number");
    assertThat(MongoTypeConverter.extractLongFromExtendedJson(map)).isNull();
  }
}
