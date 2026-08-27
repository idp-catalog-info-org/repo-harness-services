/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.steps.fme.FmeFlagsetCreateParameters;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagsetCreateInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeFlagsetCreateInfo info = new FmeFlagsetCreateInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeFlagsetCreateInfo info = new FmeFlagsetCreateInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFlagsetCreateInfo info = FmeFlagsetCreateInfo.builder()
                                    .name(ParameterField.createValueField("test-flagset"))
                                    .description(ParameterField.createValueField("test description"))
                                    .build();

    FmeFlagsetCreateParameters params = (FmeFlagsetCreateParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getName().getValue()).isEqualTo("test-flagset");
    assertThat(params.getDescription().getValue()).isEqualTo("test description");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeFlagsetCreateInfo info = FmeFlagsetCreateInfo.builder()
                                    .name(ParameterField.createValueField("flagset-name"))
                                    .description(ParameterField.createValueField("my description"))
                                    .build();

    assertThat(info.getName().getValue()).isEqualTo("flagset-name");
    assertThat(info.getDescription().getValue()).isEqualTo("my description");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithNullDescription() {
    FmeFlagsetCreateInfo info =
        FmeFlagsetCreateInfo.builder().name(ParameterField.createValueField("test-flagset")).build();

    FmeFlagsetCreateParameters params = (FmeFlagsetCreateParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getName().getValue()).isEqualTo("test-flagset");
    assertThat(params.getDescription()).isNull();
  }
}
