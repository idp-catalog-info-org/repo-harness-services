/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeSetTargetingRulesInstructionTest extends CategoryTest {
  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testGetTypeReturnsSetTargetingRules() {
    FmeSetTargetingRulesInstruction instruction = FmeSetTargetingRulesInstruction.builder().build();
    assertThat(instruction.getType()).isEqualTo(FmeInstructionType.SetTargetingRules);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testBuilderWithValue() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("active"))
                    .value(ParameterField.createValueField(true))
                    .build();

    RuleAllocation allocation = RuleAllocation.builder()
                                    .treatment(ParameterField.createValueField("on"))
                                    .size(ParameterField.createValueField(100))
                                    .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.createValueField(Collections.singletonList(allocation)))
                                  .build();

    FmeSetTargetingRulesInstruction instruction =
        FmeSetTargetingRulesInstruction.builder()
            .value(ParameterField.createValueField(Collections.singletonList(targetRules)))
            .build();

    assertThat(instruction.getValue()).isNotNull();
    assertThat(instruction.getValue().getValue()).hasSize(1);
    assertThat(instruction.getValue().getValue().get(0).getCondition().getValue().getRules().getValue()).hasSize(1);
    assertThat(instruction.getValue().getValue().get(0).getAllocation().getValue()).hasSize(1);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testBuilderWithMultipleTargetRules() {
    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(50))
                               .build();

    TargetRules rule1 = TargetRules.builder()
                            .condition(ParameterField.ofNull())
                            .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                            .build();
    TargetRules rule2 = TargetRules.builder()
                            .condition(ParameterField.ofNull())
                            .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                            .build();

    FmeSetTargetingRulesInstruction instruction =
        FmeSetTargetingRulesInstruction.builder()
            .value(ParameterField.createValueField(Arrays.asList(rule1, rule2)))
            .build();

    assertThat(instruction.getValue().getValue()).hasSize(2);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testIsInstanceOfFmeDefinitionInstruction() {
    FmeSetTargetingRulesInstruction instruction = FmeSetTargetingRulesInstruction.builder().build();
    assertThat(instruction).isInstanceOf(FmeDefinitionInstruction.class);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testNullValue() {
    FmeSetTargetingRulesInstruction instruction =
        FmeSetTargetingRulesInstruction.builder().value(ParameterField.ofNull()).build();

    assertThat(instruction.getType()).isEqualTo(FmeInstructionType.SetTargetingRules);
    assertThat(ParameterField.isNull(instruction.getValue())).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testEmptyValueList() {
    FmeSetTargetingRulesInstruction instruction = FmeSetTargetingRulesInstruction.builder()
                                                      .value(ParameterField.createValueField(Collections.emptyList()))
                                                      .build();

    assertThat(instruction.getValue().getValue()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    List<TargetRules> rules = Collections.emptyList();
    FmeSetTargetingRulesInstruction inst1 =
        FmeSetTargetingRulesInstruction.builder().value(ParameterField.createValueField(rules)).build();
    FmeSetTargetingRulesInstruction inst2 =
        FmeSetTargetingRulesInstruction.builder().value(ParameterField.createValueField(rules)).build();

    assertThat(inst1).isEqualTo(inst2);
    assertThat(inst1.hashCode()).isEqualTo(inst2.hashCode());
  }
}
