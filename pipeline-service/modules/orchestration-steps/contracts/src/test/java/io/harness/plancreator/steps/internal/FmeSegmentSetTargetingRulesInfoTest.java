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
import io.harness.steps.fme.FmeSegmentSetTargetingRulesParameters;
import io.harness.steps.fme.Rule;
import io.harness.steps.fme.RuleCondition;
import io.harness.steps.fme.RuleConditionType;
import io.harness.steps.fme.SegmentTargetRules;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeSegmentSetTargetingRulesInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeSegmentSetTargetingRulesInfo info = new FmeSegmentSetTargetingRulesInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeSegmentSetTargetingRulesInfo info = new FmeSegmentSetTargetingRulesInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    Rule ageRule = Rule.builder()
                       .type(ParameterField.createValueField(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER))
                       .attribute(ParameterField.createValueField("age"))
                       .value(ParameterField.createValueField(25))
                       .build();
    Rule tierRule = Rule.builder()
                        .type(ParameterField.createValueField(RuleConditionType.EQUAL_SET))
                        .attribute(ParameterField.createValueField("tier"))
                        .value(ParameterField.createValueField(Collections.singletonList("premium")))
                        .build();
    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Arrays.asList(ageRule, tierRule)))
                    .build()))
            .build();

    FmeSegmentSetTargetingRulesInfo info =
        FmeSegmentSetTargetingRulesInfo.builder()
            .segmentName(ParameterField.createValueField("high-value-users"))
            .environment(ParameterField.createValueField("env-123"))
            .rules(ParameterField.createValueField(Collections.singletonList(segmentRule)))
            .excludeKeys(ParameterField.createValueField(Arrays.asList("test_user_1")))
            .excludeSegments(ParameterField.createValueField(Arrays.asList("beta-testers")))
            .comment(ParameterField.createValueField("test comment"))
            .title(ParameterField.createValueField("test title"))
            .build();

    FmeSegmentSetTargetingRulesParameters params = (FmeSegmentSetTargetingRulesParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getSegmentName().getValue()).isEqualTo("high-value-users");
    assertThat(params.getEnvironment().getValue()).isEqualTo("env-123");
    assertThat(params.getRules().getValue()).hasSize(1);
    assertThat(params.getRules().getValue().get(0).getCondition().getValue().getRules().getValue()).hasSize(2);
    assertThat(params.getExcludeKeys().getValue()).containsExactly("test_user_1");
    assertThat(params.getExcludeSegments().getValue()).containsExactly("beta-testers");
    assertThat(params.getComment().getValue()).isEqualTo("test comment");
    assertThat(params.getTitle().getValue()).isEqualTo("test title");
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithOptionalFieldsNull() {
    FmeSegmentSetTargetingRulesInfo info = FmeSegmentSetTargetingRulesInfo.builder()
                                               .segmentName(ParameterField.createValueField("test-segment"))
                                               .environment(ParameterField.createValueField("env-1"))
                                               .build();

    FmeSegmentSetTargetingRulesParameters params = (FmeSegmentSetTargetingRulesParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getSegmentName().getValue()).isEqualTo("test-segment");
    assertThat(params.getEnvironment().getValue()).isEqualTo("env-1");
    assertThat(params.getRules()).isNull();
    assertThat(params.getExcludeKeys()).isNull();
    assertThat(params.getExcludeSegments()).isNull();
    assertThat(params.getComment()).isNull();
    assertThat(params.getTitle()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeSegmentSetTargetingRulesInfo info = FmeSegmentSetTargetingRulesInfo.builder()
                                               .segmentName(ParameterField.createValueField("segment-name"))
                                               .environment(ParameterField.createValueField("env-id"))
                                               .build();

    assertThat(info.getSegmentName().getValue()).isEqualTo("segment-name");
    assertThat(info.getEnvironment().getValue()).isEqualTo("env-id");
  }
}
