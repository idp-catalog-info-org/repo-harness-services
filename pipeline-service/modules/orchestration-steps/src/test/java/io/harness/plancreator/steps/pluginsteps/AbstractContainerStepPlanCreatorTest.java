/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginsteps;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.pluginstep.ContainerStepPlanCreator;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.plugin.ContainerStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AbstractContainerStepPlanCreatorTest extends CategoryTest {
  @InjectMocks ContainerStepPlanCreator containerStepPlanCreator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() throws IOException {
    YamlField stepGroupYamlField = getContainerStepGroupYamlField("pipeline-container-step-matrix.yaml");
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepGroupYamlField).build();
    ContainerStepNode config = new ContainerStepNode();
    config.setName("Container_1");
    config.setIdentifier("Container_1");
    List<String> childrenNodeIds = List.of("child");
    PlanNode planNode = containerStepPlanCreator.createPlanForParentNode(ctx, config, childrenNodeIds);
    assertThat(planNode.getName()).isEqualTo("Container_1<+strategy.identifierPostFix>");
    assertThat(planNode.getIdentifier()).isEqualTo("Container_1<+strategy.identifierPostFix>");
  }

  private YamlField getContainerStepGroupYamlField(String pipelineYamlName) throws IOException {
    final URL pipelineYamlFile = this.getClass().getClassLoader().getResource(pipelineYamlName);
    assertThat(pipelineYamlFile).isNotNull();
    String pipelineYaml = Resources.toString(pipelineYamlFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();

    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stagesNodes = stagesYamlField.getNode().asArray();
    YamlField approvalStageField = stagesNodes.get(0).getField("stage");
    YamlField stageSpec = Objects.requireNonNull(approvalStageField).getNode().getField("spec");
    YamlField executionField = Objects.requireNonNull(stageSpec).getNode().getField("execution");

    return executionField.getNode().getField("steps").getNode().asArray().get(0).getField("step");
  }
}
