/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineInstrumentationUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void test() throws IOException {
    // invalid Yamls
    assertThat(PipelineInstrumentationUtils.getStageTypes(null)).isEmpty();
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    assertThat(PipelineInstrumentationUtils.getStageTypes(pipelineEntity)).isEmpty();
    pipelineEntity.setYaml("abc");
    assertThat(PipelineInstrumentationUtils.getStageTypes(pipelineEntity)).isEmpty();
    pipelineEntity.setYaml("pipeline\n  stage1");
    assertThat(PipelineInstrumentationUtils.getStageTypes(pipelineEntity)).isEmpty();

    // single stage
    String yaml = Resources.toString(this.getClass().getClassLoader().getResource("pipeline.yml"), Charsets.UTF_8);
    pipelineEntity.setYaml(yaml);
    List<String> stageTypes = PipelineInstrumentationUtils.getStageTypes(pipelineEntity);
    assertThat(stageTypes).hasSize(1);
    assertThat(stageTypes.get(0)).isEqualTo("deployment");

    // 2 stages
    yaml = Resources.toString(
        this.getClass().getClassLoader().getResource("pipeline-with-two-stages.yaml"), Charsets.UTF_8);
    pipelineEntity.setYaml(yaml);
    stageTypes = PipelineInstrumentationUtils.getStageTypes(pipelineEntity);
    assertThat(stageTypes).hasSize(2);
    assertThat(stageTypes.get(0)).isEqualTo("deployment");
    assertThat(stageTypes.get(1)).isEqualTo("type2");

    // parallel stages
    yaml = Resources.toString(
        this.getClass().getClassLoader().getResource("pipeline-with-parallel-stages.yaml"), Charsets.UTF_8);
    pipelineEntity.setYaml(yaml);
    stageTypes = PipelineInstrumentationUtils.getStageTypes(pipelineEntity);
    assertThat(stageTypes).hasSize(3);
    assertThat(stageTypes.get(0)).isEqualTo("Approval");
    assertThat(stageTypes.get(1)).isEqualTo("Custom");
    assertThat(stageTypes.get(2)).isEqualTo("Custom1");

    // parallel stages
    yaml = Resources.toString(
        this.getClass().getClassLoader().getResource("pipeline-test-instrumentation.yaml"), Charsets.UTF_8);
    pipelineEntity.setYaml(yaml);
    stageTypes = PipelineInstrumentationUtils.getStageTypes(pipelineEntity);
    assertThat(stageTypes).hasSize(3);
    assertThat(stageTypes.get(0)).isEqualTo("Custom");
    assertThat(stageTypes.get(1)).isEqualTo("Deployment");
    assertThat(stageTypes.get(2)).isEqualTo("Approval");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testPopulateInstrumentationYamlFieldDataForParallelStage() throws IOException {
    String yaml = Resources.toString(
        this.getClass().getClassLoader().getResource("pipeline-with-parallel-stages.yaml"), Charsets.UTF_8);
    YamlField fullField = YamlUtils.readYamlTree(yaml);
    Map<String, Object> map = PipelineInstrumentationUtils.populateInstrumentationYamlFieldData(fullField, "v0");
    assertThat(map.size()).isEqualTo(11);
    assertThat(map.get("has_looping_strategy")).isEqualTo(false);
    assertThat(map.get("looping_strategy_levels")).isEqualTo(Collections.emptyList());
    assertThat(map.get("has_step_group")).isEqualTo(false);
    assertThat(map.get("stages")).isEqualTo(Arrays.asList("Approval", "Custom", "Custom1"));
    assertThat(map.get("has_common_steps")).isEqualTo(true);
    assertThat(map.get("has_failure_strategy")).isEqualTo(false);
    assertThat(map.get("has_barrier")).isEqualTo(false);
    assertThat(map.get("looping_strategy_types")).isEqualTo(Collections.emptyList());
    assertThat(map.get("failure_strategy_levels")).isEqualTo(Collections.emptyList());
    assertThat(map.get("has_steps_insert")).isEqualTo(false);
    assertThat(map.get("has_stages_insert")).isEqualTo(false);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testPopulateInstrumentationYamlFieldDataCoveringEverything() throws IOException {
    String yaml = Resources.toString(
        this.getClass().getClassLoader().getResource("pipeline-test-instrumentation.yaml"), Charsets.UTF_8);
    YamlField fullField = YamlUtils.readYamlTree(yaml);
    Map<String, Object> map = PipelineInstrumentationUtils.populateInstrumentationYamlFieldData(fullField, "v0");
    assertThat(map.size()).isEqualTo(11);
    assertThat(map.get("has_steps_insert")).isEqualTo(true);
    assertThat(map.get("has_stages_insert")).isEqualTo(true);
    assertThat(map.get("has_looping_strategy")).isEqualTo(true);
    assertThat(map.get("looping_strategy_levels")).isEqualTo(Arrays.asList("step", "stage"));
    assertThat(map.get("has_step_group")).isEqualTo(true);
    assertThat(map.get("stages")).isEqualTo(Arrays.asList("Custom", "Deployment", "Approval"));
    assertThat(map.get("has_common_steps")).isEqualTo(true);
    assertThat(map.get("has_failure_strategy")).isEqualTo(true);
    assertThat(map.get("has_barrier")).isEqualTo(false);
    assertThat(map.get("looping_strategy_types")).isEqualTo(Arrays.asList("matrix", "repeat"));
    assertThat(map.get("failure_strategy_levels")).isEqualTo(Arrays.asList("step", "step", "stage", "stage"));
  }
}
