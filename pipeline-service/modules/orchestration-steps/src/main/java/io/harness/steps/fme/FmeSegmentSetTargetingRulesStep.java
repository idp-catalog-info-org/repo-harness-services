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
import io.harness.beans.Scope;
import io.harness.fme.RuleBasedSegmentExternalDTO;
import io.harness.fme.SegmentRuleExternalDTO;
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
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeSegmentSetTargetingRulesStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_SEGMENT_SET_TARGETING_RULES_STEP...");
    logCallback.saveExecutionLog("Starting FME Segment Set Targeting Rules", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeSegmentSetTargetingRulesParameters p = (FmeSegmentSetTargetingRulesParameters) stepParameters.getSpec();
    String segmentName = getRequiredStringParam(p.getSegmentName(), "segment name");
    String environment = getRequiredStringParam(p.getEnvironment(), "environment");
    List<SegmentTargetRules> rules = extractValue(p.getRules());
    List<String> excludeKeys = extractValue(p.getExcludeKeys());
    List<String> excludeSegments = extractValue(p.getExcludeSegments());
    String comment = extractValue(p.getComment());
    String title = extractValue(p.getTitle());

    logCallback.saveExecutionLog(
        format("Setting targeting rules for segment '%s' in environment '%s'", segmentName, environment),
        LogLevel.INFO);

    List<SegmentRuleExternalDTO> convertedRules = SegmentRuleExternalConverter.convertRules(rules);

    RuleBasedSegmentExternalDTO payload = RuleBasedSegmentExternalDTO.builder()
                                              .name(segmentName)
                                              .environment(environment)
                                              .rules(convertedRules)
                                              .excludedKeys(excludeKeys)
                                              .excludedSegments(excludeSegments)
                                              .comment(comment)
                                              .title(title)
                                              .build();

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(segmentName)
                                   .environment(environment)
                                   .operationName("update segment targeting rules")
                                   .build();

    FmeApiExecutor.execute(fmePipelineClient.updateSegmentRules(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                               scope.getProjectIdentifier(), payload),
        context, NotFoundBehavior.THROW_SEGMENT_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to update segment targeting rules. Error: %s", errorBody));

    logCallback.saveExecutionLog(format("FME Targeting rules updated for segment '%s'", segmentName), LogLevel.INFO,
        CommandExecutionStatus.SUCCESS);

    return buildSuccessResponseNoOutcome(startTime);
  }

  private String getRequiredStringParam(ParameterField<String> param, String name) {
    return Optional.ofNullable(param)
        .map(ParameterField::obtainValue)
        .filter(s -> !Strings.isNullOrEmpty(s))
        .orElseThrow(() -> new FmeInvalidParameterException(format("Missing required parameter: %s", name)));
  }

  private <T> T extractValue(ParameterField<T> field) {
    return ParameterField.isNull(field) ? null : field.getValue();
  }
}
