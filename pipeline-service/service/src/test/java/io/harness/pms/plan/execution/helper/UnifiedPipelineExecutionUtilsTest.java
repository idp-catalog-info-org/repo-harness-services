/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.yaml.UnifiedPipelineYaml;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class UnifiedPipelineExecutionUtilsTest extends CategoryTest {
  private static final String VALID_PIPELINE_YAML = "pipeline:\n"
      + "  clone:\n"
      + "    disabled: true\n"
      + "  allow-stage-executions: true\n"
      + "  stages:\n"
      + "    - runtime: shell\n"
      + "      steps:\n"
      + "        - run:\n"
      + "            script: |-\n"
      + "              echo CEL: \n"
      + "              echo stage_1\n"
      + "      id: stage_1\n"
      + "      name: stage_1\n"
      + "    - parallel:\n"
      + "        - runtime: shell\n"
      + "          steps:\n"
      + "            - run:\n"
      + "                script: |-\n"
      + "                  echo CEL: \n"
      + "                  echo STAGE_1_PARALLEL\n"
      + "          id: stage_1_parallel\n"
      + "          name: stage_1_parallel\n"
      + "        - runtime: shell\n"
      + "          steps:\n"
      + "            - run:\n"
      + "                script: |-\n"
      + "                  echo CEL: \n"
      + "                  echo STAGE_2_PARALLEL\n"
      + "          id: stage_2_parallel\n"
      + "          name: stage_2_parallel";

  private static final String INVALID_PIPELINE_YAML = "invalid: yaml: content";

  private static final String PIPELINE_WITHOUT_STAGE_EXECUTIONS = "pipeline:\n"
      + "  clone:\n"
      + "    disabled: true\n"
      + "  stages:\n"
      + "    - runtime: shell\n"
      + "      steps:\n"
      + "        - run:\n"
      + "            script: |-\n"
      + "              echo CEL: \n"
      + "              echo stage_1\n"
      + "      id: stage_1\n"
      + "      name: stage_1\n";

  @Test
  @Owner(developers = OwnerRule.RISHIKESH)
  @Category(UnitTests.class)
  public void testGetUnifiedPipeline_validYaml() {
    UnifiedPipelineYaml pipeline = UnifiedPipelineExecutionUtils.getUnifiedPipeline(VALID_PIPELINE_YAML);
    assertThat(pipeline).isNotNull();
    assertThat(pipeline.isAllowStageExecutions()).isTrue();

    assertThatThrownBy(() -> UnifiedPipelineExecutionUtils.getUnifiedPipeline(INVALID_PIPELINE_YAML))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot create unified pipeline entity");
  }

  @Test
  @Owner(developers = OwnerRule.RISHIKESH)
  @Category(UnitTests.class)
  public void testShouldAllowStageExecutions_false() {
    boolean result = UnifiedPipelineExecutionUtils.shouldAllowStageExecutions(PIPELINE_WITHOUT_STAGE_EXECUTIONS);
    assertThat(result).isFalse();

    assertThatThrownBy(() -> UnifiedPipelineExecutionUtils.shouldAllowStageExecutions(INVALID_PIPELINE_YAML))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot create unified pipeline entity");
  }
}
