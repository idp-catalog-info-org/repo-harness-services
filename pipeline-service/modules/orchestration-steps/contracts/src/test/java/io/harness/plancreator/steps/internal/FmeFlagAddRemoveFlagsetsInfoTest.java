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
import io.harness.steps.fme.FmeFlagAddRemoveFlagsetsParameters;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagAddRemoveFlagsetsInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeFlagAddRemoveFlagsetsInfo info = new FmeFlagAddRemoveFlagsetsInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeFlagAddRemoveFlagsetsInfo info = new FmeFlagAddRemoveFlagsetsInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFlagAddRemoveFlagsetsInfo info =
        FmeFlagAddRemoveFlagsetsInfo.builder()
            .flagName(ParameterField.createValueField("test-flag"))
            .environment(ParameterField.createValueField("Production"))
            .addFlagsets(ParameterField.createValueField(Arrays.asList("flagset1", "flagset2")))
            .removeFlagsets(ParameterField.createValueField(Arrays.asList("flagset3")))
            .build();

    FmeFlagAddRemoveFlagsetsParameters params = (FmeFlagAddRemoveFlagsetsParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("test-flag");
    assertThat(params.getEnvironment().getValue()).isEqualTo("Production");
    assertThat(params.getAddFlagsets().getValue()).containsExactly("flagset1", "flagset2");
    assertThat(params.getRemoveFlagsets().getValue()).containsExactly("flagset3");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeFlagAddRemoveFlagsetsInfo info = FmeFlagAddRemoveFlagsetsInfo.builder()
                                            .flagName(ParameterField.createValueField("flag-name"))
                                            .environment(ParameterField.createValueField("Staging"))
                                            .build();

    assertThat(info.getFlagName().getValue()).isEqualTo("flag-name");
    assertThat(info.getEnvironment().getValue()).isEqualTo("Staging");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithNullFlagsets() {
    FmeFlagAddRemoveFlagsetsInfo info = FmeFlagAddRemoveFlagsetsInfo.builder()
                                            .flagName(ParameterField.createValueField("test-flag"))
                                            .environment(ParameterField.createValueField("Production"))
                                            .build();

    FmeFlagAddRemoveFlagsetsParameters params = (FmeFlagAddRemoveFlagsetsParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("test-flag");
    assertThat(params.getAddFlagsets()).isNull();
    assertThat(params.getRemoveFlagsets()).isNull();
  }
}
