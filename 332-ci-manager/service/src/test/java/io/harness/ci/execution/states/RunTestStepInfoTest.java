/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.SAHITHI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefHelper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RunTestStepInfoTest extends CategoryTest {
  String basicStepYaml;

  @Before
  public void setUp() {
    basicStepYaml =
        "{\"type\":\"RunTests\",\"name\":\"RunTests_1\",\"identifier\":\"RunTests_1\",\"spec\":{\"connectorRef\":\"sahithiDockerConnector\",\"image\":\"alpine\",\"language\":\"Java\",\"buildTool\":\"Bazel\",\"args\":\"test\",\"runOnlySelectedTests\":true,\"reports\":{\"type\":\"JUnit\",\"spec\":{\"paths\":[\"harness/reports.xml\"]}},\"outputVariables\":[{\"name\":\"variableWithoutType\"},{\"name\":\"variableWithTypeString\",\"type\":\"String\",\"value\":\"variableWithTypeString\"},{\"name\":\"variableWithTypeSecret\",\"type\":\"Secret\",\"value\":\"variableWithTypeSecret\"}]}}";
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testDeserializationOfRunTestStepOutputVariable() throws IOException {
    AbstractStepNode abstractStepNode = YamlUtils.read(basicStepYaml, AbstractStepNode.class);
    RunTestStepNode runTestStepNode = (RunTestStepNode) abstractStepNode;
    RunTestsStepInfo runStepInfo = (RunTestsStepInfo) runTestStepNode.getStepSpecType();

    NGVariable outputVariableWithoutType = StringNGVariable.builder()
                                               .name("variableWithoutType")
                                               .type(NGVariableType.STRING)
                                               .value(ParameterField.createValueField("variableWithoutType"))
                                               .build();
    NGVariable outputVariableWithTypeString = StringNGVariable.builder()
                                                  .name("variableWithTypeString")
                                                  .type(NGVariableType.STRING)
                                                  .value(ParameterField.createValueField("variableWithTypeString"))
                                                  .build();

    NGVariable outputVariableWithTypeSecret =
        SecretNGVariable.builder()
            .name("variableWithTypeSecret")
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(SecretRefHelper.createSecretRef("variableWithTypeSecret")))
            .build();
    List<NGVariable> ngVariableList = new ArrayList<>();
    ngVariableList.add(outputVariableWithoutType);
    ngVariableList.add(outputVariableWithTypeString);
    ngVariableList.add(outputVariableWithTypeSecret);

    ParameterField<List<NGVariable>> outputVariables =
        ParameterField.<List<NGVariable>>builder().value(ngVariableList).build();
    assertThat(runStepInfo).isNotNull();
    assertEquals(runStepInfo.getOutputVariables(), outputVariables);
  }
}
