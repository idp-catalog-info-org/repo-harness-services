/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.fme.AllocationDTO;
import io.harness.fme.RuleConditionDTO;
import io.harness.fme.TargetingRuleDTO;
import io.harness.fme.TargetingRulesDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.google.common.base.Strings;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagSetTargetingRulesStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_SET_TARGETING_RULES_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Set Targeting Rules", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagSetTargetingRulesParameters p = (FmeFlagSetTargetingRulesParameters) stepParameters.getSpec();

    String flagName = getRequiredStringParam(p.getFlagName(), "flag name");
    String environment = getRequiredStringParam(p.getEnvironment(), "environment");
    List<TargetRules> targetingRules = getRequiredParam(p.getTargetingRules(), "targetingRules");

    logCallback.saveExecutionLog(
        format("Setting targeting rules for flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    List<TargetingRulesDTO> ruleDtos =
        targetingRules.stream().map(this::convertToTargetingRulesDTO).collect(Collectors.toList());

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("update definition rules")
                                   .build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.updateFeatureFlagDefinitionRules(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), flagName, environment, ruleDtos),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag Set Targeting Rules request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("FME Targeting rules updated for flag '%s'", flagName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponseNoOutcome(startTime);
  }

  private TargetingRulesDTO convertToTargetingRulesDTO(TargetRules targetRules) {
    return TargetingRulesDTO.builder()
        .condition(convertToRuleConditionDTO(targetRules.getCondition()))
        .allocation(convertToAllocationDTOList(targetRules.getAllocation()))
        .build();
  }

  private RuleConditionDTO convertToRuleConditionDTO(ParameterField<RuleCondition> conditionField) {
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
        ruleDtos = rules.stream().map(this::convertToTargetingRuleDTO).collect(Collectors.toList());
      }
    }
    return RuleConditionDTO.builder().rules(ruleDtos).build();
  }

  private TargetingRuleDTO convertToTargetingRuleDTO(Rule rule) {
    return TargetingRuleDTO.builder()
        .type(extractValue(rule.getType()) != null ? extractValue(rule.getType()).name() : null)
        .negate(extractValue(rule.getNegate()))
        .featureFlag(extractValue(rule.getFeatureFlag()))
        .attribute(extractValue(rule.getAttribute()))
        .value(extractValue(rule.getValue()))
        .build();
  }

  private List<AllocationDTO> convertToAllocationDTOList(ParameterField<List<RuleAllocation>> allocationField) {
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

  private <T> T extractValue(ParameterField<T> field) {
    return ParameterField.isNull(field) ? null : field.getValue();
  }

  private String getRequiredStringParam(ParameterField<String> param, String name) {
    return Optional.ofNullable(param)
        .map(ParameterField::obtainValue)
        .filter(s -> !Strings.isNullOrEmpty(s))
        .orElseThrow(() -> new FmeInvalidParameterException(format("Missing required parameter: %s", name)));
  }

  private <T> T getRequiredParam(ParameterField<T> param, String name) {
    return Optional.ofNullable(param)
        .map(ParameterField::obtainValue)
        .orElseThrow(() -> new FmeInvalidParameterException(format("Missing required parameter: %s", name)));
  }
}
