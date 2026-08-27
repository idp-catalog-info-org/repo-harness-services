/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.fme.BetweenAttributeExternalDTO;
import io.harness.fme.BetweenStringAttributeExternalDTO;
import io.harness.fme.ConditionExternalDTO;
import io.harness.fme.DependsExternalDTO;
import io.harness.fme.MatcherExternalDTO;
import io.harness.fme.MatcherExternalDTO.MatcherExternalDTOBuilder;
import io.harness.fme.SegmentRuleExternalDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Converts Pipeline YAML DTOs (with polymorphic {@code ParameterField<Object> value})
 * to External DTOs (with typed fields like number, string, bool, etc.).
 *
 * This is the harness-core equivalent of {@code RuleExternalMapper} in the Main repo,
 * adapted to work with harness-core's {@link Rule} class instead of Main's typed MatcherValue wrappers.
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
public class SegmentRuleExternalConverter {
  private static final String COMBINER_AND = "AND";

  public static List<SegmentRuleExternalDTO> convertRules(List<SegmentTargetRules> rules) {
    if (rules == null || rules.isEmpty()) {
      return List.of();
    }

    List<SegmentRuleExternalDTO> result = new ArrayList<>();
    for (SegmentTargetRules rule : rules) {
      result.add(convertRule(rule));
    }
    return result;
  }

  public static SegmentRuleExternalDTO convertRule(SegmentTargetRules segmentTargetRules) {
    ConditionExternalDTO condition = convertCondition(segmentTargetRules.getCondition());
    return SegmentRuleExternalDTO.builder().condition(condition).build();
  }

  private static ConditionExternalDTO convertCondition(ParameterField<RuleCondition> conditionField) {
    if (ParameterField.isNull(conditionField) || conditionField.getValue() == null) {
      throw new FmeInvalidParameterException("Rule condition is required");
    }

    RuleCondition condition = conditionField.getValue();
    if (ParameterField.isNull(condition.getRules()) || condition.getRules().getValue() == null) {
      throw new FmeInvalidParameterException("Rule condition must contain at least one rule");
    }

    List<Rule> rules = condition.getRules().getValue();
    List<MatcherExternalDTO> matchers = new ArrayList<>();
    for (Rule rule : rules) {
      matchers.add(convertMatcher(rule));
    }

    return ConditionExternalDTO.builder().combiner(COMBINER_AND).matchers(matchers).build();
  }

  private static MatcherExternalDTO convertMatcher(Rule rule) {
    RuleConditionType type = extractValue(rule.getType());
    if (type == null) {
      throw new FmeInvalidParameterException("Rule type is required");
    }

    MatcherExternalDTOBuilder builder =
        MatcherExternalDTO.builder().type(type.name()).negate(extractValue(rule.getNegate()));

    String attribute = extractValue(rule.getAttribute());
    if (attribute != null && type != RuleConditionType.IN_SEGMENT && type != RuleConditionType.IN_SPLIT) {
      builder.attribute(attribute);
    }

    Object value = extractValue(rule.getValue());
    String featureFlag = extractValue(rule.getFeatureFlag());

    switch (type) {
      case IN_SPLIT:
        handleInSplitMatcher(builder, value, featureFlag, attribute);
        break;

      case IN_SEGMENT:
        handleSegmentMatcher(builder, value);
        break;

      case BOOLEAN:
        handleBooleanMatcher(builder, value, attribute);
        break;

      case EQUAL_NUMBER:
      case LESS_THAN_OR_EQUAL_NUMBER:
      case GREATER_THAN_OR_EQUAL_NUMBER:
        handleNumberMatcher(builder, value, type, attribute);
        break;

      case BETWEEN_NUMBER:
        handleBetweenNumberMatcher(builder, value, attribute);
        break;

      case ON_DATE:
      case ON_OR_AFTER_DATE:
      case ON_OR_BEFORE_DATE:
        handleDateMatcher(builder, value, type, attribute);
        break;

      case BETWEEN_DATE:
        handleBetweenDateMatcher(builder, value, attribute);
        break;

      case IN_LIST_STRING:
      case STARTS_WITH_STRING:
      case ENDS_WITH_STRING:
      case CONTAINS_STRING:
        handleStringListMatcher(builder, value, type, attribute);
        break;

      case MATCHES_STRING:
        handleSingleStringMatcher(builder, value, type, attribute);
        break;

      case EQUAL_SET:
      case ANY_OF_SET:
      case ALL_OF_SET:
      case PART_OF_SET:
        handleSetMatcher(builder, value, type, attribute);
        break;

      case EQUAL_TO_SEMVER:
      case GREATER_THAN_OR_EQUAL_TO_SEMVER:
      case LESS_THAN_OR_EQUAL_TO_SEMVER:
        handleSemverMatcher(builder, value, type, attribute);
        break;

      case BETWEEN_SEMVER:
        handleBetweenSemverMatcher(builder, value, attribute);
        break;

      case IN_LIST_SEMVER:
        handleSemverListMatcher(builder, value, type, attribute);
        break;

      default:
        throw new FmeInvalidParameterException(format("Unsupported matcher type: %s", type));
    }

    return builder.build();
  }

  private static void handleInSplitMatcher(
      MatcherExternalDTOBuilder builder, Object value, String featureFlag, String attribute) {
    handleWithContext(() -> {
      List<String> treatments = FmeRuleValueConverter.asStringList(value);
      builder.depends(DependsExternalDTO.builder().splitName(featureFlag).treatments(treatments).build());
    }, "IN_SPLIT", attribute);
  }

  private static void handleSegmentMatcher(MatcherExternalDTOBuilder builder, Object value) {
    handleWithContext(() -> { builder.string(FmeRuleValueConverter.asSingleString(value)); }, "IN_SEGMENT", null);
  }

  private static void handleBooleanMatcher(MatcherExternalDTOBuilder builder, Object value, String attribute) {
    handleWithContext(() -> { builder.bool(FmeRuleValueConverter.asBoolean(value)); }, "BOOLEAN", attribute);
  }

  private static void handleNumberMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.number(FmeRuleValueConverter.asLong(value)); }, type.name(), attribute);
  }

  private static void handleBetweenNumberMatcher(MatcherExternalDTOBuilder builder, Object value, String attribute) {
    handleWithContext(() -> {
      Map<String, Object> range = FmeRuleValueConverter.asBetweenMap(value);
      builder.between(BetweenAttributeExternalDTO.builder()
                          .from(FmeRuleValueConverter.asLong(range.get("from")))
                          .to(FmeRuleValueConverter.asLong(range.get("to")))
                          .build());
    }, "BETWEEN_NUMBER", attribute);
  }

  private static void handleDateMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.date(FmeRuleValueConverter.asLong(value)); }, type.name(), attribute);
  }

  private static void handleBetweenDateMatcher(MatcherExternalDTOBuilder builder, Object value, String attribute) {
    handleWithContext(() -> {
      Map<String, Object> range = FmeRuleValueConverter.asBetweenMap(value);
      builder.between(BetweenAttributeExternalDTO.builder()
                          .from(FmeRuleValueConverter.asLong(range.get("from")))
                          .to(FmeRuleValueConverter.asLong(range.get("to")))
                          .build());
    }, "BETWEEN_DATE", attribute);
  }

  private static void handleStringListMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.strings(FmeRuleValueConverter.asStringList(value)); }, type.name(), attribute);
  }

  private static void handleSingleStringMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.string(FmeRuleValueConverter.asSingleString(value)); }, type.name(), attribute);
  }

  private static void handleSetMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.strings(FmeRuleValueConverter.asStringList(value)); }, type.name(), attribute);
  }

  private static void handleSemverMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.string(FmeRuleValueConverter.asSingleString(value)); }, type.name(), attribute);
  }

  private static void handleBetweenSemverMatcher(MatcherExternalDTOBuilder builder, Object value, String attribute) {
    handleWithContext(() -> {
      Map<String, Object> range = FmeRuleValueConverter.asBetweenMap(value);
      builder.betweenString(BetweenStringAttributeExternalDTO.builder()
                                .from(String.valueOf(range.get("from")))
                                .to(String.valueOf(range.get("to")))
                                .build());
    }, "BETWEEN_SEMVER", attribute);
  }

  private static void handleSemverListMatcher(
      MatcherExternalDTOBuilder builder, Object value, RuleConditionType type, String attribute) {
    handleWithContext(() -> { builder.strings(FmeRuleValueConverter.asStringList(value)); }, type.name(), attribute);
  }

  private static <T> T extractValue(ParameterField<T> field) {
    return ParameterField.isNull(field) ? null : field.getValue();
  }

  private static void handleWithContext(Runnable operation, String matcherType, String attribute) {
    try {
      operation.run();
    } catch (Exception e) {
      if (attribute == null) {
        throw new FmeInvalidParameterException(format("Unable to parse %s condition: %s", matcherType, e.getMessage()));
      }
      throw new FmeInvalidParameterException(
          format("Unable to parse %s condition for attribute '%s': %s", matcherType, attribute, e.getMessage()));
    }
  }
}
