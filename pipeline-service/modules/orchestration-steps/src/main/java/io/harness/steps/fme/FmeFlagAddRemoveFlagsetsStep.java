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
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FlagSetAssociationRef;
import io.harness.fme.FmePatchOperation;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagAddRemoveFlagsetsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE;
  private static final String STEP_NAME = "FME Feature Flag Add/Remove Flagsets";

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_ADD_REMOVE_FLAGSETS_STEP...");
    logCallback.saveExecutionLog("Starting FME Feature Flag Add/Remove Flagsets", LogLevel.INFO);

    Scope scope = getScope(ambiance);

    FmeFlagAddRemoveFlagsetsParameters p = (FmeFlagAddRemoveFlagsetsParameters) stepParameters.getSpec();
    String flagName = p.getFlagName() != null ? p.getFlagName().obtainValue() : null;
    String environment = p.getEnvironment() != null ? p.getEnvironment().obtainValue() : null;

    List<String> addFlagsets =
        Optional.ofNullable(p.getAddFlagsets()).map(ParameterField::obtainValue).orElse(Collections.emptyList());
    List<String> removeFlagsets =
        Optional.ofNullable(p.getRemoveFlagsets()).map(ParameterField::obtainValue).orElse(Collections.emptyList());

    logCallback.saveExecutionLog(
        format("FME Feature Flag Add/Remove Flagsets Inputs -> account: %s, org: %s, project: %s, flagName: %s, "
                + "environment: %s, addFlagsets: %s, removeFlagsets: %s",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), flagName, environment,
            addFlagsets, removeFlagsets),
        LogLevel.INFO);

    assertNotEmpty(flagName, "Missing required parameter: flagName");
    assertNotEmpty(environment, "Missing required parameter: environment");

    addFlagsets = filterBlankEntries(addFlagsets);
    removeFlagsets = filterBlankEntries(removeFlagsets);

    if (addFlagsets.isEmpty() && removeFlagsets.isEmpty()) {
      throw new FmeInvalidParameterException(
          "At least one of 'addFlagsets' or 'removeFlagsets' must be provided with non-empty values");
    }

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    // Step 1: GET current definition to retrieve existing flagSets
    logCallback.saveExecutionLog(
        format("Fetching current definition for flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    FeatureFlagDefinition currentDefinition = FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(flagName, scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND);

    // Step 2: Compute the new flagSets list
    List<FlagSetAssociationRef> currentFlagSets = currentDefinition != null && currentDefinition.getFlagSets() != null
        ? currentDefinition.getFlagSets()
        : Collections.emptyList();

    Set<String> removeFlagsetIds = new LinkedHashSet<>(removeFlagsets);

    // Start with current flagsets, removing those in the remove list
    LinkedHashSet<String> newFlagsetIds = currentFlagSets.stream()
                                              .map(FlagSetAssociationRef::getId)
                                              .filter(id -> !removeFlagsetIds.contains(id))
                                              .collect(Collectors.toCollection(LinkedHashSet::new));

    // Add the new flagsets
    newFlagsetIds.addAll(addFlagsets);

    List<FlagSetAssociationRef> newFlagSets =
        newFlagsetIds.stream().map(FlagSetAssociationRef::of).collect(Collectors.toList());

    logCallback.saveExecutionLog(
        format("Computed new flagSets list: %s (was: %s)", newFlagsetIds,
            currentFlagSets.stream().map(FlagSetAssociationRef::getId).collect(Collectors.toList())),
        LogLevel.INFO);

    // Step 3: PATCH the definition with the new flagSets
    // Use "add" instead of "replace": RFC 6902 "add" creates the field if absent, replaces if present.
    // "replace" would fail when flagSets is null (omitted from JSON due to @JsonInclude(NON_NULL)).
    List<FmePatchOperation> patchOps = List.of(FmePatchOperation.add("/flagSets", newFlagSets));

    logCallback.saveExecutionLog(format("PATCH payload: %s", JsonUtils.asJson(patchOps)), LogLevel.INFO);

    ExecutionContext patchContext = ExecutionContext.builder()
                                        .logCallback(logCallback)
                                        .flagName(flagName)
                                        .environment(environment)
                                        .operationName("add/remove flagsets")
                                        .build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), environment, flagName, patchOps),
        patchContext, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Feature Flag add/remove flagsets request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("FME Feature Flag Add/Remove Flagsets completed: flag: %s, environment: %s", flagName, environment),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(
        startTime, STEP_NAME, format("add/remove flagsets on feature flag %s in %s", flagName, environment));
  }

  private List<String> filterBlankEntries(List<String> items) {
    if (items == null || items.isEmpty()) {
      return Collections.emptyList();
    }
    return items.stream().filter(f -> f != null && !f.trim().isEmpty()).collect(Collectors.toList());
  }
}
