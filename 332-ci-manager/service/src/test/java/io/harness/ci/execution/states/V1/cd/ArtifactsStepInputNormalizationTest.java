/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.rule.OwnerRule.DANIEL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ci.execution.states.helpers.AmiArtifactStepHelper;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactsStepInputNormalizationTest {
  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testNormalizeAmiInputs_whenFiltersArrayAndNullStrings_shouldNormalizeToPluginFormat() {
    Map<String, Object> inputsMap = new HashMap<>();
    inputsMap.put("filters", "[{\"name\":\"ami-image-id\",\"value\":\"ami-123\"}]");
    inputsMap.put("tags", "null");
    inputsMap.put("versionRegex", "null");

    AmiArtifactStepHelper.normalizeInputs(inputsMap);

    assertThat(inputsMap.get("tags")).isEqualTo("");
    assertThat(inputsMap.get("versionRegex")).isEqualTo("");
    assertThat(JsonUtils.asMap((String) inputsMap.get("filters"))).containsEntry("ami-image-id", "ami-123");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testNormalizeAmiInputs_whenTagsArray_shouldConvertToMapFormat() {
    Map<String, Object> inputsMap = new HashMap<>();
    inputsMap.put("tags", "[{\"name\":\"env\",\"value\":\"prod\"},{\"name\":\"env\",\"value\":\"qa\"}]");

    AmiArtifactStepHelper.normalizeInputs(inputsMap);

    Map<String, Object> normalizedTags = JsonUtils.asMap((String) inputsMap.get("tags"));
    assertThat(normalizedTags).containsKey("env");
    assertThat((List<String>) normalizedTags.get("env")).containsExactly("prod", "qa");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testNormalizeAmiInputs_whenFiltersMap_shouldRemainUsable() {
    Map<String, Object> inputsMap = new HashMap<>();
    inputsMap.put("filters", "{\"ami-image-id\":\"ami-999\"}");

    AmiArtifactStepHelper.normalizeInputs(inputsMap);

    assertThat(JsonUtils.asMap((String) inputsMap.get("filters"))).containsEntry("ami-image-id", "ami-999");
  }
}
