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
import io.harness.steps.fme.FmeFlagSetImpressionTrackingParameters;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeFlagSetImpressionTrackingInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeFlagSetImpressionTrackingInfo info = new FmeFlagSetImpressionTrackingInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeFlagSetImpressionTrackingInfo info = new FmeFlagSetImpressionTrackingInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFlagSetImpressionTrackingInfo info = FmeFlagSetImpressionTrackingInfo.builder()
                                                .flagName(ParameterField.createValueField("test-flag"))
                                                .environment(ParameterField.createValueField("production"))
                                                .enabled(ParameterField.createValueField(true))
                                                .build();

    FmeFlagSetImpressionTrackingParameters params = (FmeFlagSetImpressionTrackingParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("test-flag");
    assertThat(params.getEnvironment().getValue()).isEqualTo("production");
    assertThat(params.getEnabled().getValue()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeFlagSetImpressionTrackingInfo info = FmeFlagSetImpressionTrackingInfo.builder()
                                                .flagName(ParameterField.createValueField("my-flag"))
                                                .environment(ParameterField.createValueField("staging"))
                                                .enabled(ParameterField.createValueField(false))
                                                .build();

    assertThat(info.getFlagName().getValue()).isEqualTo("my-flag");
    assertThat(info.getEnvironment().getValue()).isEqualTo("staging");
    assertThat(info.getEnabled().getValue()).isFalse();
  }
}
