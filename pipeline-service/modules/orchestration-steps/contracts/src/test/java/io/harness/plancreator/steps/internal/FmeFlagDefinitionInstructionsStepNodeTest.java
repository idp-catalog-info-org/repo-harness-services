/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeSetDefaultTreatmentInstruction;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagDefinitionInstructionsStepNodeTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetType() {
    FmeFlagDefinitionInstructionsStepNode node = new FmeFlagDefinitionInstructionsStepNode();
    String type = node.getType();
    assertThat(type).isEqualTo(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE.getType());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    FmeFlagDefinitionInstructionsStepNode node = new FmeFlagDefinitionInstructionsStepNode();
    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    info.setFlagName(ParameterField.createValueField("test-flag"));
    info.setEnvironment(ParameterField.createValueField("production"));
    info.setInstructions(ParameterField.createValueField(
        List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build())));
    node.setFmeFlagDefinitionInstructionsStepInfo(info);

    assertThat(node.getStepSpecType()).isEqualTo(info);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testDefaultStepType() {
    FmeFlagDefinitionInstructionsStepNode node = new FmeFlagDefinitionInstructionsStepNode();
    assertThat(node.getType()).isNotNull();
    assertThat(node.getType()).isEqualTo(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS);
  }
}
