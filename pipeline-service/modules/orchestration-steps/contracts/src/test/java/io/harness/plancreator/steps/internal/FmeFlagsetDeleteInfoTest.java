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
import io.harness.steps.fme.FmeFlagsetDeleteParameters;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagsetDeleteInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeFlagsetDeleteInfo info = new FmeFlagsetDeleteInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_FLAGSET_DELETE_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeFlagsetDeleteInfo info = new FmeFlagsetDeleteInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFlagsetDeleteInfo info =
        FmeFlagsetDeleteInfo.builder().name(ParameterField.createValueField("test-flagset")).build();

    FmeFlagsetDeleteParameters params = (FmeFlagsetDeleteParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getName().getValue()).isEqualTo("test-flagset");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeFlagsetDeleteInfo info =
        FmeFlagsetDeleteInfo.builder().name(ParameterField.createValueField("flagset-name")).build();

    assertThat(info.getName().getValue()).isEqualTo("flagset-name");
  }
}
