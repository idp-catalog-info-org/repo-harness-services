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
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.DefaultDefinitionConfig;
import io.harness.steps.fme.FmeDefinitionInstruction;
import io.harness.steps.fme.FmeFlagDefinitionInstructionsStepParameters;
import io.harness.steps.fme.FmeSetDefaultTreatmentInstruction;
import io.harness.steps.fme.TreatmentConfiguration;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagDefinitionInstructionsStepInfoTest extends CategoryTest {
  private static final List<FmeDefinitionInstruction> SAMPLE_INSTRUCTIONS =
      List.of(FmeSetDefaultTreatmentInstruction.builder().value(ParameterField.createValueField("on")).build());

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    info.setFlagName(ParameterField.createValueField("test-flag"));
    info.setEnvironment(ParameterField.createValueField("production"));
    info.setInstructions(ParameterField.createValueField(SAMPLE_INSTRUCTIONS));

    FmeFlagDefinitionInstructionsStepParameters params =
        (FmeFlagDefinitionInstructionsStepParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("test-flag");
    assertThat(params.getEnvironment().getValue()).isEqualTo("production");
    assertThat(params.getInstructions().getValue()).isEqualTo(SAMPLE_INSTRUCTIONS);
    assertThat(params.getDefaultDefinition()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithDefaultDefinition() {
    DefaultDefinitionConfig defaultDef = DefaultDefinitionConfig.builder()
                                             .treatments(ParameterField.createValueField(List.of(
                                                 TreatmentConfiguration.builder()
                                                     .treatment(ParameterField.createValueField("beta"))
                                                     .description(ParameterField.createValueField("Beta variant"))
                                                     .build(),
                                                 TreatmentConfiguration.builder()
                                                     .treatment(ParameterField.createValueField("gamma"))
                                                     .description(ParameterField.createValueField("Gamma variant"))
                                                     .build())))
                                             .defaultTreatment(ParameterField.createValueField("beta"))
                                             .baselineTreatment(ParameterField.createValueField("gamma"))
                                             .build();

    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    info.setFlagName(ParameterField.createValueField("test-flag"));
    info.setEnvironment(ParameterField.createValueField("production"));
    info.setDefaultDefinition(defaultDef);
    info.setInstructions(ParameterField.createValueField(SAMPLE_INSTRUCTIONS));

    FmeFlagDefinitionInstructionsStepParameters params =
        (FmeFlagDefinitionInstructionsStepParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getDefaultDefinition()).isNotNull();
    assertThat(params.getDefaultDefinition().getDefaultTreatment().getValue()).isEqualTo("beta");
    assertThat(params.getDefaultDefinition().getBaselineTreatment().getValue()).isEqualTo("gamma");
    assertThat(params.getDefaultDefinition().getTreatments().getValue()).hasSize(2);
    assertThat(params.getDefaultDefinition().getTreatments().getValue().get(0).getTreatment().getValue())
        .isEqualTo("beta");
    assertThat(params.getDefaultDefinition().getTreatments().getValue().get(1).getTreatment().getValue())
        .isEqualTo("gamma");
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    FmeFlagDefinitionInstructionsStepInfo info = new FmeFlagDefinitionInstructionsStepInfo();
    info.setFlagName(ParameterField.createValueField("my-flag"));
    info.setEnvironment(ParameterField.createValueField("staging"));
    info.setInstructions(ParameterField.createValueField(SAMPLE_INSTRUCTIONS));

    assertThat(info.getFlagName().getValue()).isEqualTo("my-flag");
    assertThat(info.getEnvironment().getValue()).isEqualTo("staging");
    assertThat(info.getInstructions().getValue()).isEqualTo(SAMPLE_INSTRUCTIONS);
  }
}
