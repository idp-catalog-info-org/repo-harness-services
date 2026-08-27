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
import io.harness.beans.steps.nodes.RunTestStepV2Node;
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
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RunTestStepV2VariableCreatorTest extends CategoryTest {
  @Inject RunTestStepV2VariableCreator runTestStepV2VariableCreator = new RunTestStepV2VariableCreator();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(runTestStepV2VariableCreator.getSupportedStepTypes())
        .as("supported step types should contain Test")
        .containsExactly("Test");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(runTestStepV2VariableCreator.getFieldClass())
        .as("field class should be RunTestStepV2Node")
        .isEqualTo(RunTestStepV2Node.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNodeV2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("runTestV2CreatorUuidJsonSteps.yaml");
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

    RunTestStepV2Node runTestStepV2Node = YamlUtils.read(stepField.getNode().toString(), RunTestStepV2Node.class);
    VariableCreationResponse variablesForParentNodeV2 = runTestStepV2VariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), runTestStepV2Node);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .as("yaml properties should contain all input variable FQNs")
        .containsExactlyInAnyOrder("pipeline.stages.run_test.spec.execution.steps.ti.spec.resources.limits.memory",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.resources.limits.cpu",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.envVariables.secret",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.envVariables.foo",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.imagePullPolicy",
            "pipeline.stages.run_test.spec.execution.steps.ti.name",
            "pipeline.stages.run_test.spec.execution.steps.ti.description",
            "pipeline.stages.run_test.spec.execution.steps.ti.timeout",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.command",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.privileged",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.connectorRef",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.registryRef",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.reports.spec.paths",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.runAsUser",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.shell",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.image",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.intelligenceMode",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.globs",
            "pipeline.stages.run_test.spec.execution.steps.ti.when");

    YamlExtraProperties stepExtraProps =
        variablesForParentNodeV2.getYamlExtraProperties().get(runTestStepV2Node.getUuid());
    assertThat(stepExtraProps).as("extra properties should not be null for step uuid").isNotNull();

    List<String> fqnExtraPropertiesList =
        stepExtraProps.getPropertiesList().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .as("extra properties should contain step metadata FQNs")
        .containsExactlyInAnyOrder("pipeline.stages.run_test.spec.execution.steps.ti.identifier",
            "pipeline.stages.run_test.spec.execution.steps.ti.type",
            "pipeline.stages.run_test.spec.execution.steps.ti.startTs",
            "pipeline.stages.run_test.spec.execution.steps.ti.endTs",
            "pipeline.stages.run_test.spec.execution.steps.ti.status",
            "pipeline.stages.run_test.spec.execution.steps.ti.nodeExecutionId",
            "pipeline.stages.run_test.spec.execution.steps.ti.log.url");

    List<String> fqnOutputPropertiesList =
        stepExtraProps.getOutputPropertiesList().stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .as("output properties should contain output variable FQNs")
        .containsExactlyInAnyOrder("pipeline.stages.run_test.spec.execution.steps.ti.output.outputVariables.hello");
  }
}
