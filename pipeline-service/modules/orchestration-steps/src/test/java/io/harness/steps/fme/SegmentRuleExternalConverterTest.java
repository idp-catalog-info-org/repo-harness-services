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
import io.harness.fme.MatcherExternalDTO;
import io.harness.fme.SegmentRuleExternalDTO;
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
public class SegmentRuleExternalConverterTest extends CategoryTest {
  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertRulesNull() {
    assertThat(SegmentRuleExternalConverter.convertRules(null)).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertRulesEmpty() {
    assertThat(SegmentRuleExternalConverter.convertRules(Collections.emptyList())).isEmpty();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertNumberMatcher() {
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER, "age", 25);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("GREATER_THAN_OR_EQUAL_NUMBER");
    assertThat(matcher.getAttribute()).isEqualTo("age");
    assertThat(matcher.getNumber()).isEqualTo(25L);
    assertThat(matcher.getString()).isNull();
    assertThat(matcher.getBool()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBooleanMatcher() {
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.BOOLEAN, "premium", true);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BOOLEAN");
    assertThat(matcher.getAttribute()).isEqualTo("premium");
    assertThat(matcher.getBool()).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertDateMatcher() {
    long timestamp = 1700000000000L;
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.ON_OR_AFTER_DATE, "signup_date", timestamp);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("ON_OR_AFTER_DATE");
    assertThat(matcher.getDate()).isEqualTo(timestamp);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertStringListMatcher() {
    List<String> countries = Arrays.asList("US", "UK", "CA");
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.EQUAL_SET, "country", countries);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("EQUAL_SET");
    assertThat(matcher.getStrings()).containsExactly("US", "UK", "CA");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertInListStringMatcher() {
    List<String> values = Arrays.asList("admin", "user");
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.IN_LIST_STRING, "role", values);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("IN_LIST_STRING");
    assertThat(matcher.getStrings()).containsExactly("admin", "user");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertMatchesStringMatcher() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.MATCHES_STRING, "email", Collections.singletonList(".*@harness\\.io"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("MATCHES_STRING");
    assertThat(matcher.getString()).isEqualTo(".*@harness\\.io");
    assertThat(matcher.getStrings()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertSegmentMatcher() {
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.IN_SEGMENT, null, "beta-users");

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("IN_SEGMENT");
    assertThat(matcher.getString()).isEqualTo("beta-users");
    assertThat(matcher.getAttribute()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertSegmentMatcherIgnoresAttribute() {
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.IN_SEGMENT, "age", "beta-users");

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("IN_SEGMENT");
    assertThat(matcher.getString()).isEqualTo("beta-users");
    assertThat(matcher.getAttribute()).isNull();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertSegmentMatcherFromList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.IN_SEGMENT, null, Collections.singletonList("beta-users"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getString()).isEqualTo("beta-users");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenNumberMatcher() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", 10);
    range.put("to", 100);

    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.BETWEEN_NUMBER, "score", range);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BETWEEN_NUMBER");
    assertThat(matcher.getBetween()).isNotNull();
    assertThat(matcher.getBetween().getFrom()).isEqualTo(10L);
    assertThat(matcher.getBetween().getTo()).isEqualTo(100L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenNumberMatcherFromList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.BETWEEN_NUMBER, "score", Arrays.asList("10", "100"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BETWEEN_NUMBER");
    assertThat(matcher.getBetween()).isNotNull();
    assertThat(matcher.getBetween().getFrom()).isEqualTo(10L);
    assertThat(matcher.getBetween().getTo()).isEqualTo(100L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenSemverMatcherFromList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.BETWEEN_SEMVER, "version", Arrays.asList("1.0.0", "2.0.0"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BETWEEN_SEMVER");
    assertThat(matcher.getBetweenString()).isNotNull();
    assertThat(matcher.getBetweenString().getFrom()).isEqualTo("1.0.0");
    assertThat(matcher.getBetweenString().getTo()).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenDateMatcher() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", 1700000000000L);
    range.put("to", 1800000000000L);

    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.BETWEEN_DATE, "signup", range);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BETWEEN_DATE");
    assertThat(matcher.getBetween()).isNotNull();
    assertThat(matcher.getBetween().getFrom()).isEqualTo(1700000000000L);
    assertThat(matcher.getBetween().getTo()).isEqualTo(1800000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertSemverMatcher() {
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.EQUAL_TO_SEMVER, "version", "2.0.0");

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("EQUAL_TO_SEMVER");
    assertThat(matcher.getString()).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenSemverMatcher() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", "1.0.0");
    range.put("to", "2.0.0");

    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.BETWEEN_SEMVER, "version", range);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BETWEEN_SEMVER");
    assertThat(matcher.getBetweenString()).isNotNull();
    assertThat(matcher.getBetweenString().getFrom()).isEqualTo("1.0.0");
    assertThat(matcher.getBetweenString().getTo()).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertInSplitMatcher() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.IN_SPLIT))
                    .featureFlag(ParameterField.createValueField("my_flag"))
                    .value(ParameterField.createValueField(Arrays.asList("on", "off")))
                    .build();

    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                    .build()))
            .build();

    SegmentRuleExternalDTO dto = SegmentRuleExternalConverter.convertRule(segmentRule);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("IN_SPLIT");
    assertThat(matcher.getDepends()).isNotNull();
    assertThat(matcher.getDepends().getSplitName()).isEqualTo("my_flag");
    assertThat(matcher.getDepends().getTreatments()).containsExactly("on", "off");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertWithNegate() {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                    .negate(ParameterField.createValueField(true))
                    .attribute(ParameterField.createValueField("active"))
                    .value(ParameterField.createValueField(true))
                    .build();

    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                    .build()))
            .build();

    SegmentRuleExternalDTO dto = SegmentRuleExternalConverter.convertRule(segmentRule);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getNegate()).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertMultipleMatchersInCondition() {
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

    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder().rules(ParameterField.createValueField(Arrays.asList(rule1, rule2))).build()))
            .build();

    SegmentRuleExternalDTO dto = SegmentRuleExternalConverter.convertRule(segmentRule);

    assertThat(dto.getCondition().getCombiner()).isEqualTo("AND");
    assertThat(dto.getCondition().getMatchers()).hasSize(2);
    assertThat(dto.getCondition().getMatchers().get(0).getType()).isEqualTo("BOOLEAN");
    assertThat(dto.getCondition().getMatchers().get(1).getType()).isEqualTo("GREATER_THAN_OR_EQUAL_NUMBER");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertNullConditionThrows() {
    SegmentTargetRules segmentRule = SegmentTargetRules.builder().condition(ParameterField.ofNull()).build();

    assertThatThrownBy(() -> SegmentRuleExternalConverter.convertRule(segmentRule))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("condition is required");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertInvalidValueTypeThrows() {
    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(
                        Collections.singletonList(Rule.builder()
                                                      .type(ParameterField.createValueField(RuleConditionType.BOOLEAN))
                                                      .attribute(ParameterField.createValueField("flag"))
                                                      .value(ParameterField.createValueField(42))
                                                      .build())))
                    .build()))
            .build();

    assertThatThrownBy(() -> SegmentRuleExternalConverter.convertRule(segmentRule))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("BOOLEAN");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertSemverListMatcher() {
    List<String> versions = Arrays.asList("1.0.0", "2.0.0", "3.0.0");
    SegmentRuleExternalDTO dto = convertSingleRule(RuleConditionType.IN_LIST_SEMVER, "version", versions);

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("IN_LIST_SEMVER");
    assertThat(matcher.getStrings()).containsExactly("1.0.0", "2.0.0", "3.0.0");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertNumberMatcherFromStringList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.GREATER_THAN_OR_EQUAL_NUMBER, "age", Collections.singletonList("24"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("GREATER_THAN_OR_EQUAL_NUMBER");
    assertThat(matcher.getNumber()).isEqualTo(24L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBooleanMatcherFromStringList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.BOOLEAN, "premium", Collections.singletonList("true"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("BOOLEAN");
    assertThat(matcher.getBool()).isTrue();
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertDateMatcherFromStringList() {
    SegmentRuleExternalDTO dto =
        convertSingleRule(RuleConditionType.ON_DATE, "signup_date", Collections.singletonList("1700000000000"));

    MatcherExternalDTO matcher = dto.getCondition().getMatchers().get(0);
    assertThat(matcher.getType()).isEqualTo("ON_DATE");
    assertThat(matcher.getDate()).isEqualTo(1700000000000L);
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertNumberMatcherWithInvalidStringThrows() {
    assertThatThrownBy(() -> convertSingleRule(RuleConditionType.EQUAL_NUMBER, "score", "not-a-number"))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("Expected a valid number string");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenNumberWithNullFromThrows() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", null);
    range.put("to", 100);

    assertThatThrownBy(() -> convertSingleRule(RuleConditionType.BETWEEN_NUMBER, "score", range))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  @Owner(developers = KESHAV)
  @Category(UnitTests.class)
  public void testConvertBetweenNumberWithNullToThrows() {
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("from", 10);
    range.put("to", null);

    assertThatThrownBy(() -> convertSingleRule(RuleConditionType.BETWEEN_NUMBER, "score", range))
        .isInstanceOf(FmeInvalidParameterException.class)
        .hasMessageContaining("must not be null");
  }

  private SegmentRuleExternalDTO convertSingleRule(RuleConditionType type, String attribute, Object value) {
    Rule rule = Rule.builder()
                    .type(ParameterField.createValueField(type))
                    .attribute(attribute != null ? ParameterField.createValueField(attribute) : null)
                    .value(ParameterField.createValueField(value))
                    .build();

    SegmentTargetRules segmentRule =
        SegmentTargetRules.builder()
            .condition(ParameterField.createValueField(
                RuleCondition.builder()
                    .rules(ParameterField.createValueField(Collections.singletonList(rule)))
                    .build()))
            .build();

    return SegmentRuleExternalConverter.convertRule(segmentRule);
  }
}
