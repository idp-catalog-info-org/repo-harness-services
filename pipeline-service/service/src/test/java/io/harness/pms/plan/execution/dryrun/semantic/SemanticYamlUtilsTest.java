/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SemanticYamlUtilsTest extends CategoryTest {
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private List<String> collectStageIds(String yaml, boolean isV1) throws Exception {
    JsonNode root = YAML.readTree(yaml);
    JsonNode stages = SemanticYamlUtils.stagesNode(root, isV1);
    List<String> ids = new ArrayList<>();
    SemanticYamlUtils.forEachStage(stages, isV1, stage -> ids.add(SemanticYamlUtils.stageId(stage, isV1)));
    return ids;
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0StagesWithParallel() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: a\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: b\n"
        + "        - stage:\n"
        + "            identifier: c\n";
    assertThat(collectStageIds(yaml, false)).containsExactly("a", "b", "c");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0StageWrapperWithStageAndParallelVisitsStageBeforeParallel() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: a\n"
        + "      parallel:\n"
        + "        - stage:\n"
        + "            identifier: b\n";
    assertThat(collectStageIds(yaml, false)).containsExactly("a", "b");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1StagesWithParallelObjectShape() throws Exception {
    // Real V1 / UI shape: parallel is an object with nested stages[], not a bare array.
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: a\n"
        + "    - name: parallel_group\n"
        + "      id: parallel_group\n"
        + "      parallel:\n"
        + "        stages:\n"
        + "          - id: b\n"
        + "          - id: c\n";
    assertThat(collectStageIds(yaml, true)).containsExactly("a", "b", "c");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1StagesWithParallelBareArrayStillWorks() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: a\n"
        + "    - parallel:\n"
        + "        - id: b\n"
        + "        - id: c\n";
    assertThat(collectStageIds(yaml, true)).containsExactly("a", "b", "c");
  }
}
