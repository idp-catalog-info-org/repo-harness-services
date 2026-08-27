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
import io.harness.steps.fme.FmeSegmentAddRemoveTargetsParameters;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeSegmentAddRemoveTargetsInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeSegmentAddRemoveTargetsInfo info = new FmeSegmentAddRemoveTargetsInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeSegmentAddRemoveTargetsInfo info = new FmeSegmentAddRemoveTargetsInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeSegmentAddRemoveTargetsInfo info =
        FmeSegmentAddRemoveTargetsInfo.builder()
            .segmentName(ParameterField.createValueField("premium-users"))
            .environment(ParameterField.createValueField("env-123"))
            .addKeys(ParameterField.createValueField(Arrays.asList("user123", "user456")))
            .removeKeys(ParameterField.createValueField(Arrays.asList("user789")))
            .build();

    FmeSegmentAddRemoveTargetsParameters params = (FmeSegmentAddRemoveTargetsParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getSegmentName().getValue()).isEqualTo("premium-users");
    assertThat(params.getEnvironment().getValue()).isEqualTo("env-123");
    assertThat(params.getAddKeys().getValue()).containsExactly("user123", "user456");
    assertThat(params.getRemoveKeys().getValue()).containsExactly("user789");
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeSegmentAddRemoveTargetsInfo info = FmeSegmentAddRemoveTargetsInfo.builder()
                                              .segmentName(ParameterField.createValueField("test-segment"))
                                              .environment(ParameterField.createValueField("env-1"))
                                              .addKeys(ParameterField.createValueField(Arrays.asList("key1")))
                                              .removeKeys(ParameterField.createValueField(Arrays.asList("key2")))
                                              .build();

    assertThat(info.getSegmentName().getValue()).isEqualTo("test-segment");
    assertThat(info.getEnvironment().getValue()).isEqualTo("env-1");
    assertThat(info.getAddKeys().getValue()).containsExactly("key1");
    assertThat(info.getRemoveKeys().getValue()).containsExactly("key2");
  }
}
