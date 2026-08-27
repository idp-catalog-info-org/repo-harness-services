/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.annotations.dev.HarnessTeam.FME;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.fme.AllocationDTO;
import io.harness.fme.RuleConditionDTO;
import io.harness.fme.TargetingRuleDTO;
import io.harness.fme.TargetingRulesDTO;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(FME)
public class FmeTargetingRulesMapper {
  public static List<TargetingRulesDTO> toTargetingRulesDTOs(List<TargetRules> targetRulesList) {
    return targetRulesList.stream().map(FmeTargetingRulesMapper::toTargetingRulesDTO).collect(Collectors.toList());
  }

  private static TargetingRulesDTO toTargetingRulesDTO(TargetRules targetRules) {
    return TargetingRulesDTO.builder()
        .condition(toRuleConditionDTO(targetRules.getCondition()))
        .allocation(toAllocationDTOs(targetRules.getAllocation()))
        .build();
  }

  private static RuleConditionDTO toRuleConditionDTO(ParameterField<RuleCondition> conditionField) {
    if (ParameterField.isNull(conditionField)) {
      return null;
    }
    RuleCondition condition = conditionField.getValue();
    if (condition == null) {
      return null;
    }

    List<TargetingRuleDTO> ruleDtos = null;
    if (!ParameterField.isNull(condition.getRules())) {
      List<Rule> rules = condition.getRules().getValue();
      if (rules != null) {
        ruleDtos = rules.stream().map(FmeTargetingRulesMapper::toTargetingRuleDTO).collect(Collectors.toList());
      }
    }
    return RuleConditionDTO.builder().rules(ruleDtos).build();
  }

  private static TargetingRuleDTO toTargetingRuleDTO(Rule rule) {
    RuleConditionType ruleType = extractValue(rule.getType());
    String attribute = extractValue(rule.getAttribute());
    Object rawValue = extractValue(rule.getValue());
    return TargetingRuleDTO.builder()
        .type(ruleType != null ? ruleType.name() : null)
        .negate(extractValue(rule.getNegate()))
        .featureFlag(extractValue(rule.getFeatureFlag()))
        .attribute(attribute)
        .value(convertValue(ruleType, rawValue, attribute))
        .build();
  }

  private static Object convertValue(RuleConditionType type, Object value, String attribute) {
    if (type == null || value == null) {
      return value;
    }
    try {
      switch (type) {
        case BOOLEAN:
          return FmeRuleValueConverter.asBoolean(value);

        case EQUAL_NUMBER:
        case LESS_THAN_OR_EQUAL_NUMBER:
        case GREATER_THAN_OR_EQUAL_NUMBER:
        case ON_DATE:
        case ON_OR_AFTER_DATE:
        case ON_OR_BEFORE_DATE:
          return FmeRuleValueConverter.asLong(value);

        case BETWEEN_NUMBER:
        case BETWEEN_DATE:
        case BETWEEN_SEMVER:
          return FmeRuleValueConverter.asBetweenMap(value);

        default:
          return value;
      }
    } catch (Exception e) {
      throw new FmeInvalidParameterException(
          format("Unable to parse %s value for attribute '%s': %s", type.name(), attribute, e.getMessage()));
    }
  }

  private static List<AllocationDTO> toAllocationDTOs(ParameterField<List<RuleAllocation>> allocationField) {
    if (ParameterField.isNull(allocationField)) {
      return Collections.emptyList();
    }
    List<RuleAllocation> allocations = allocationField.getValue();
    if (allocations == null) {
      return Collections.emptyList();
    }
    return allocations.stream()
        .map(a
            -> AllocationDTO.builder()
                   .treatment(extractValue(a.getTreatment()))
                   .size(extractValue(a.getSize()))
                   .build())
        .collect(Collectors.toList());
  }

  private static <T> T extractValue(ParameterField<T> field) {
    return ParameterField.isNull(field) ? null : field.getValue();
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void logTargetingRules(NGLogCallback logCallback, List<TargetingRulesDTO> ruleDtos) {
    try {
      String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ruleDtos);
      logCallback.saveExecutionLog(
          format("========== TARGETING RULES BEING SENT ==========\n%s\n==========", json), LogLevel.INFO);
    } catch (Exception e) {
      logCallback.saveExecutionLog(format("Failed to serialize targeting rules: %s", e.getMessage()), LogLevel.WARN);
    }
  }
}
