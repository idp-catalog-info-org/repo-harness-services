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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeSegmentSetTargetingRulesNodeTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetType() {
    FmeSegmentSetTargetingRulesNode node = new FmeSegmentSetTargetingRulesNode();
    String type = node.getType();
    assertThat(type).isEqualTo(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE.getType());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    FmeSegmentSetTargetingRulesNode node = new FmeSegmentSetTargetingRulesNode();
    FmeSegmentSetTargetingRulesInfo info = FmeSegmentSetTargetingRulesInfo.builder()
                                               .segmentName(ParameterField.createValueField("test-segment"))
                                               .environment(ParameterField.createValueField("env-1"))
                                               .build();
    node.setFmeSegmentSetTargetingRulesInfo(info);

    assertThat(node.getStepSpecType()).isEqualTo(info);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testDefaultStepType() {
    FmeSegmentSetTargetingRulesNode node = new FmeSegmentSetTargetingRulesNode();
    assertThat(node.getType()).isNotNull();
    assertThat(node.getType()).isEqualTo(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES);
  }
}
