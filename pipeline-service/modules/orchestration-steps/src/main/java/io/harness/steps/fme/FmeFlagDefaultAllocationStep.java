/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.fme.Bucket;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.Treatment;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagDefaultAllocationStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE;
  public static final String STEP_NAME = "FME Flag Default Allocation";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing feature default allocation step..");
    logCallback.saveExecutionLog("Starting Flag Update", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeFlagDefaultAllocationStepParameters allocationStepParameters =
        (FmeFlagDefaultAllocationStepParameters) stepParameters.getSpec();
    String name = allocationStepParameters.getFlagName().obtainValue();
    String environment = allocationStepParameters.getEnvironment().obtainValue();
    List<Allocation> allocations = allocationStepParameters.getAllocation().obtainValue();

    logCallback.saveExecutionLog(
        format("updating Feature flag %s in environment %s with allocation %s", name, environment, allocations),
        LogLevel.INFO);

    validateAllocation(allocations);

    FeatureFlagDefinition existingDefinition = getDefinition(logCallback, scope, name, environment);

    Map<String, Integer> allocationByName =
        allocations.stream()
            .filter(allocation
                -> ParameterField.isNotNull(allocation.getTreatment())
                    && StringUtils.isNotBlank(allocation.getTreatment().getValue()))
            .collect(Collectors.toMap(a
                -> a.getTreatment().getValue(),
                a -> ParameterField.isNull(a.getAmount()) ? 0 : (Integer) a.getAmount().fetchFinalValue()));

    Set<String> existentTreatments = Optional.ofNullable(existingDefinition.getTreatments())
                                         .orElse(List.of())
                                         .stream()
                                         .map(Treatment::getName)
                                         .collect(Collectors.toSet());
    Set<String> newTreatments = allocationByName.keySet();

    for (String newTreatment : newTreatments) {
      if (!existentTreatments.contains(newTreatment)) {
        throw new InvalidRequestException(
            format("Invalid allocations for feature flag  %s. Treatment not found %s. Expected one of %s", name,
                newTreatment, existentTreatments));
      }
    }

    List<Bucket> newBuckets =
        existentTreatments.stream()
            .map(t -> Bucket.builder().treatment(t).size(allocationByName.getOrDefault(t, 0)).build())
            .collect(Collectors.toList());

    patchDefaultRule(logCallback, scope, name, environment, newBuckets);

    logCallback.saveExecutionLog(
        String.format("FME Flag Definition Updated: flag: %s", name), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME, "updated flag");
  }

  private FeatureFlagDefinition getDefinition(NGLogCallback logCallback, Scope scope, String name, String environment) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(name)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    return FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(
            name, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));
  }

  private void patchDefaultRule(
      NGLogCallback logCallback, Scope scope, String name, String environment, List<Bucket> newBuckets) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(name)
                                   .environment(environment)
                                   .operationName("update definition")
                                   .build();

    List<FmePatchOperation> patch = List.of(FmePatchOperation.replace("/defaultRule", newBuckets));

    FmeApiExecutor.executeWrapped(fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment, name, patch),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to update feature flag definition. Error: %s", errorBody));

    logCallback.saveExecutionLog(format("flag %s definition update successful", name), LogLevel.INFO);
  }

  private void validateAllocation(List<Allocation> allocations) {
    if (allocations == null) {
      throw new FmeInvalidParameterException("Allocations must be provided");
    }

    int sum =
        allocations.stream()
            .filter(
                a -> ParameterField.isNotNull(a.getTreatment()) && StringUtils.isNotBlank(a.getTreatment().getValue()))
            .filter(a -> ParameterField.isNotNull(a.getAmount()))
            .mapToInt(a -> (Integer) a.getAmount().fetchFinalValue())
            .sum();
    if (sum != 100) {
      throw new FmeInvalidParameterException("Invalid allocations. Total allocation must equal 100");
    }
  }
}
