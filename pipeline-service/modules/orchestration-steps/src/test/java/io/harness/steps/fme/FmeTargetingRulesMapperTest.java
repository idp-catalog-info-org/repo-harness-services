/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.rule.OwnerRule.KESHAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.fme.TargetingRulesDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeTargetingRulesMapperTest extends CategoryTest {
  // ====================== Full mapping tests ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapSingleRuleWithConditionAndAllocation() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.EQUAL_SET))
                    .negate(ParameterField.createValueField(false))
                    .attribute(ParameterField.createValueField("country"))
                    .value(ParameterField.createValueField(Arrays.asList("US", "UK")))
                    .build();

    RuleAllocation alloc1 = RuleAllocation.builder()
                                .treatment(ParameterField.createValueField("on"))
                                .size(ParameterField.createValueField(80))
                                .build();
    RuleAllocation alloc2 = RuleAllocation.builder()
                                .treatment(ParameterField.createValueField("off"))
                                .size(ParameterField.createValueField(20))
                                .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.createValueField(Arrays.asList(alloc1, alloc2)))
                                  .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));

    assertThat(result).hasSize(1);
    TargetingRulesDTO dto = result.get(0);

    assertThat(dto.getCondition()).isNotNull();
    assertThat(dto.getCondition().getRules()).hasSize(1);
    assertThat(dto.getCondition().getRules().get(0).getType()).isEqualTo("EQUAL_SET");
    assertThat(dto.getCondition().getRules().get(0).getAttribute()).isEqualTo("country");
    assertThat(dto.getCondition().getRules().get(0).getNegate()).isFalse();
    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(Arrays.asList("US", "UK"));

    assertThat(dto.getAllocation()).hasSize(2);
    assertThat(dto.getAllocation().get(0).getTreatment()).isEqualTo("on");
    assertThat(dto.getAllocation().get(0).getSize()).isEqualTo(80);
    assertThat(dto.getAllocation().get(1).getTreatment()).isEqualTo("off");
    assertThat(dto.getAllocation().get(1).getSize()).isEqualTo(20);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapMultipleTargetRules() {
    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(100))
                               .build();

    TargetRules rule1 = TargetRules.builder()
                            .condition(ParameterField.ofNull())
                            .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                            .build();
    TargetRules rule2 = TargetRules.builder()
                            .condition(ParameterField.ofNull())
                            .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                            .build();

    List<TargetingRulesDTO> result = FmeTargetingRulesMapper.toTargetingRulesDTOs(Arrays.asList(rule1, rule2));
    assertThat(result).hasSize(2);
  }

  // ====================== Null / empty condition handling ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapWithNullCondition() {
    RuleAllocation alloc = RuleAllocation.builder()
                               .treatment(ParameterField.createValueField("on"))
                               .size(ParameterField.createValueField(50))
                               .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.ofNull())
                                  .allocation(ParameterField.createValueField(Collections.singletonList(alloc)))
                                  .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCondition()).isNull();
    assertThat(result.get(0).getAllocation()).hasSize(1);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapWithNullAllocation() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("active"))
                    .value(ParameterField.createValueField(true))
                    .build();

    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.ofNull())
                                  .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCondition()).isNotNull();
    assertThat(result.get(0).getAllocation()).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapWithNullRulesInsideCondition() {
    TargetRules targetRules =
        TargetRules.builder()
            .condition(ParameterField.createValueField(RuleCondition.builder().rules(ParameterField.ofNull()).build()))
            .allocation(ParameterField.createValueField(Collections.emptyList()))
            .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCondition()).isNotNull();
    assertThat(result.get(0).getCondition().getRules()).isNull();
  }

  // ====================== Type conversion tests ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBooleanRuleConvertsStringToBoolean() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("premium"))
                    .value(ParameterField.createValueField("true"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBooleanRuleConvertsNativeBoolean() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("premium"))
                    .value(ParameterField.createValueField(false))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(false);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapEqualNumberConvertsStringToLong() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.EQUAL_NUMBER))
                    .attribute(ParameterField.createValueField("age"))
                    .value(ParameterField.createValueField("25"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(25L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapGreaterThanOrEqualNumberConverts() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER))
                    .attribute(ParameterField.createValueField("score"))
                    .value(ParameterField.createValueField(100))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(100L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapLessThanOrEqualNumberConverts() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.LESS_THAN_OR_EQUAL_NUMBER))
                    .attribute(ParameterField.createValueField("count"))
                    .value(ParameterField.createValueField("50"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(50L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapOnDateConvertsToLong() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.ON_DATE))
                    .attribute(ParameterField.createValueField("signup"))
                    .value(ParameterField.createValueField(1700000000000L))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(1700000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapOnOrAfterDateConverts() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.ON_OR_AFTER_DATE))
                    .attribute(ParameterField.createValueField("created"))
                    .value(ParameterField.createValueField("1700000000000"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(1700000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapOnOrBeforeDateConverts() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.ON_OR_BEFORE_DATE))
                    .attribute(ParameterField.createValueField("expiry"))
                    .value(ParameterField.createValueField(1800000000000L))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(1800000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBetweenNumberConvertsToMap() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", 10);
    range.put("to", 100);

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BETWEEN_NUMBER))
                    .attribute(ParameterField.createValueField("score"))
                    .value(ParameterField.createValueField(range))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) dto.getCondition().getRules().get(0).getValue();
    assertThat(resultMap.get("from")).isEqualTo(10);
    assertThat(resultMap.get("to")).isEqualTo(100);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBetweenDateConvertsToMap() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", 1700000000000L);
    range.put("to", 1800000000000L);

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BETWEEN_DATE))
                    .attribute(ParameterField.createValueField("period"))
                    .value(ParameterField.createValueField(range))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) dto.getCondition().getRules().get(0).getValue();
    assertThat(resultMap.get("from")).isEqualTo(1700000000000L);
    assertThat(resultMap.get("to")).isEqualTo(1800000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBetweenSemverConvertsToMap() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", "1.0.0");
    range.put("to", "2.0.0");

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BETWEEN_SEMVER))
                    .attribute(ParameterField.createValueField("version"))
                    .value(ParameterField.createValueField(range))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) dto.getCondition().getRules().get(0).getValue();
    assertThat(resultMap.get("from")).isEqualTo("1.0.0");
    assertThat(resultMap.get("to")).isEqualTo("2.0.0");
  }

  // ====================== Passthrough types (no conversion) ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapInSegmentPassesValueThrough() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.IN_SEGMENT))
                    .value(ParameterField.createValueField("beta-users"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getType()).isEqualTo("IN_SEGMENT");
    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo("beta-users");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapEqualSetPassesListThrough() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.EQUAL_SET))
                    .attribute(ParameterField.createValueField("country"))
                    .value(ParameterField.createValueField(Arrays.asList("US", "UK")))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(Arrays.asList("US", "UK"));
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapMatchesStringPassesThrough() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.MATCHES_STRING))
                    .attribute(ParameterField.createValueField("email"))
                    .value(ParameterField.createValueField(".*@harness.io"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo(".*@harness.io");
  }

  // ====================== Feature flag / IN_SPLIT ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapInSplitWithFeatureFlag() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.IN_SPLIT))
                    .negate(ParameterField.createValueField(true))
                    .featureFlag(ParameterField.createValueField("parent_flag"))
                    .value(ParameterField.createValueField(Arrays.asList("on", "off")))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getType()).isEqualTo("IN_SPLIT");
    assertThat(dto.getCondition().getRules().get(0).getFeatureFlag()).isEqualTo("parent_flag");
    assertThat(dto.getCondition().getRules().get(0).getNegate()).isTrue();
  }

  // ====================== Null value handling ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapWithNullValueReturnsNull() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("flag"))
                    .value(ParameterField.ofNull())
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getValue()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapWithNullTypeReturnsValueAsIs() {
    Rule rule = Rule.builder()
                    .type(ParameterField.ofNull())
                    .attribute(ParameterField.createValueField("attr"))
                    .value(ParameterField.createValueField("raw-value"))
                    .build();

    TargetingRulesDTO dto = mapSingleRule(rule);

    assertThat(dto.getCondition().getRules().get(0).getType()).isNull();
    assertThat(dto.getCondition().getRules().get(0).getValue()).isEqualTo("raw-value");
  }

  // ====================== Error cases ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBooleanWithInvalidTypeThrows() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .attribute(ParameterField.createValueField("flag"))
                    .value(ParameterField.createValueField(42))
                    .build();

    assertThatThrownBy(() -> mapSingleRule(rule))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("BOOLEAN")
        .hasMessageContaining("flag");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapEqualNumberWithInvalidStringThrows() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.EQUAL_NUMBER))
                    .attribute(ParameterField.createValueField("score"))
                    .value(ParameterField.createValueField("not-a-number"))
                    .build();

    assertThatThrownBy(() -> mapSingleRule(rule))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("EQUAL_NUMBER")
        .hasMessageContaining("score");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapBetweenNumberWithInvalidMapThrows() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", null);
    range.put("to", 100);

    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BETWEEN_NUMBER))
                    .attribute(ParameterField.createValueField("score"))
                    .value(ParameterField.createValueField(range))
                    .build();

    assertThatThrownBy(() -> mapSingleRule(rule))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("BETWEEN_NUMBER");
  }

  // ====================== Multiple conditions in one rule ======================

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testMapMultipleConditionsInOneRule() {
    Rule rule1 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                     .attribute(ParameterField.createValueField("premium"))
                     .value(ParameterField.createValueField(true))
                     .build();
    Rule rule2 = Rule.builder()
                     .type(ParameterField.createValueField(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER))
                     .attribute(ParameterField.createValueField("age"))
                     .value(ParameterField.createValueField(18))
                     .build();

    TargetRules targetRules =
        TargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder().rules(ParameterField.createValueField(Arrays.asList(rule1, rule2))).build()))
            .allocation(ParameterField.createValueField(Collections.emptyList()))
            .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));

    assertThat(result.get(0).getCondition().getRules()).hasSize(2);
    assertThat(result.get(0).getCondition().getRules().get(0).getValue()).isEqualTo(true);
    assertThat(result.get(0).getCondition().getRules().get(1).getValue()).isEqualTo(18L);
  }

  // ====================== Helper ======================

  private TargetingRulesDTO mapSingleRule(Rule rule) {
    TargetRules targetRules = TargetRules.builder()
                                  .condition(ParameterField.createValueField(
                                      RuleCondition.builder()
                                          .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                                          .build()))
                                  .allocation(ParameterField.createValueField(Collections.emptyList()))
                                  .build();

    List<TargetingRulesDTO> result =
        FmeTargetingRulesMapper.toTargetingRulesDTOs(Collections.singletonList(targetRules));
    return result.get(0);
  }
}
