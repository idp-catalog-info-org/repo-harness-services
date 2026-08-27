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
import java.util.Optional;
import org.jooq.JSONB;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class StrategyTypeExtractorTest extends OrchestrationVisualizationTestBase {
  // ===================== extract (Object) =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_null() {
    assertThat(StrategyTypeExtractor.extract(null)).isEmpty();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_directField() {
    Map<String, Object> params = new HashMap<>();
    params.put("strategyType", "MATRIX");

    Optional<String> result = StrategyTypeExtractor.extract(params);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("MATRIX");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_underRecast() {
    Map<String, Object> recast = new HashMap<>();
    recast.put("strategyType", "PARALLELISM");

    Map<String, Object> params = new HashMap<>();
    params.put("__recast", recast);

    Optional<String> result = StrategyTypeExtractor.extract(params);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("PARALLELISM");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_underStrategyConfig() {
    Map<String, Object> strategyConfig = new HashMap<>();
    strategyConfig.put("strategyType", "FOR_LOOP");

    Map<String, Object> params = new HashMap<>();
    params.put("strategyConfig", strategyConfig);

    Optional<String> result = StrategyTypeExtractor.extract(params);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("FOR_LOOP");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_fromJsonString() {
    String json = "{\"strategyType\":\"MATRIX\"}";

    Optional<String> result = StrategyTypeExtractor.extract(json);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("MATRIX");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_notFound() {
    Map<String, Object> params = new HashMap<>();
    params.put("otherField", "value");

    Optional<String> result = StrategyTypeExtractor.extract(params);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtract_directFieldTakesPriority() {
    Map<String, Object> recast = new HashMap<>();
    recast.put("strategyType", "FROM_RECAST");

    Map<String, Object> params = new HashMap<>();
    params.put("strategyType", "DIRECT");
    params.put("__recast", recast);

    Optional<String> result = StrategyTypeExtractor.extract(params);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("DIRECT");
  }

  // ===================== extractFromJsonb =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractFromJsonb_null() {
    assertThat(StrategyTypeExtractor.extractFromJsonb(null)).isEmpty();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractFromJsonb_valid() {
    JSONB jsonb = JSONB.valueOf("{\"strategyType\":\"MATRIX\"}");
    Optional<String> result = StrategyTypeExtractor.extractFromJsonb(jsonb);
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("MATRIX");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractFromJsonb_empty() {
    JSONB jsonb = JSONB.valueOf("{}");
    assertThat(StrategyTypeExtractor.extractFromJsonb(jsonb)).isEmpty();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractFromJsonb_invalidJson() {
    JSONB jsonb = JSONB.valueOf("not-json");
    assertThat(StrategyTypeExtractor.extractFromJsonb(jsonb)).isEmpty();
  }
}
