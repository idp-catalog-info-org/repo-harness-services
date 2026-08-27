/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.AiVerifyStepNode;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class AiVerifyStepVariableCreatorTest extends CategoryTest {
  AiVerifyStepVariableCreator aiVerifyStepVariableCreator = new AiVerifyStepVariableCreator();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = aiVerifyStepVariableCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).as("should support AiVerify step type").containsExactly("AiVerify");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(aiVerifyStepVariableCreator.getFieldClass())
        .as("should return AiVerifyStepNode class")
        .isEqualTo(AiVerifyStepNode.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJsonAiVerifyStep.json");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");

    AiVerifyStepNode aiVerifyStepNode = YamlUtils.read(stepField.getNode().toString(), AiVerifyStepNode.class);
    VariableCreationResponse variablesForParentNodeV2 = aiVerifyStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), aiVerifyStepNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .as("should contain expected input variable FQN properties")
        .containsExactlyInAnyOrder("pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.name",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.connectorRef",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.command",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.shell",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.envVariables",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.description",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.timeout",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.image",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.privileged",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.runAsUser",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.reports",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.imagePullPolicy",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.when",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.spec.registryRef");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepExtraProperties() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJsonAiVerifyStep.json");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");

    AiVerifyStepNode aiVerifyStepNode = YamlUtils.read(stepField.getNode().toString(), AiVerifyStepNode.class);
    VariableCreationResponse variablesForParentNodeV2 = aiVerifyStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), aiVerifyStepNode);

    YamlExtraProperties extraProperties =
        variablesForParentNodeV2.getYamlExtraProperties().get(aiVerifyStepNode.getUuid());
    assertThat(extraProperties).as("extra properties should not be null for step uuid").isNotNull();

    List<String> fqnExtraPropertiesList =
        extraProperties.getPropertiesList().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .as("should contain identifier, type, startTs, endTs, status, nodeExecutionId, and log.url")
        .containsExactlyInAnyOrder("pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.identifier",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.type",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.startTs",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.endTs",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.status",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.nodeExecutionId",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.log.url");

    List<String> fqnOutputPropertiesList =
        extraProperties.getOutputPropertiesList().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .as("should contain output variable FQN properties")
        .containsExactlyInAnyOrder(
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.output.outputVariables.outputVar1",
            "pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.output.outputVariables.outputVar2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNode_whenNoOutputVariables() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJsonAiVerifyStep.json");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    String modifiedJson = pipelineJson.replace("\"outputVariables\": [\n"
            + "                        {\n"
            + "                          \"name\": \"outputVar1\",\n"
            + "                          \"__uuid\": \"QiIY1YwlTXmysO_mQ-ANbA\"\n"
            + "                        },\n"
            + "                        {\n"
            + "                          \"name\": \"outputVar2\",\n"
            + "                          \"__uuid\": \"Y5aDBRH4Qk6GUhAVsKLLkw\"\n"
            + "                        }\n"
            + "                      ],",
        "\"outputVariables\": [],");
    YamlField fullYamlField = YamlUtils.readTree(modifiedJson);

    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");

    AiVerifyStepNode aiVerifyStepNode = YamlUtils.read(stepField.getNode().toString(), AiVerifyStepNode.class);
    VariableCreationResponse variablesForParentNodeV2 = aiVerifyStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), aiVerifyStepNode);

    YamlExtraProperties extraProperties =
        variablesForParentNodeV2.getYamlExtraProperties().get(aiVerifyStepNode.getUuid());
    assertThat(extraProperties).as("extra properties should exist even without output variables").isNotNull();
    List<String> fqnOutputPropertiesList =
        extraProperties.getOutputPropertiesList().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .as("output properties should only contain base outputVariables path when no output variables defined")
        .containsExactly("pipeline.stages.aiVerifyStage.spec.execution.steps.aiVerifyStep.output.outputVariables");
  }
}
