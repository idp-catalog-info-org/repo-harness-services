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
import io.harness.fme.Bucket;
import io.harness.fme.FeatureFlag;
import io.harness.fme.FeatureFlagDefinition;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.FmeResponse;
import io.harness.fme.TargetingRulesDTO;
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
import io.harness.steps.fme.exception.FmeFeatureFlagDefinitionNotFoundException;
import io.harness.steps.fme.exception.FmeInternalServerErrorException;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagDefinitionInstructionsStep extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE;
  private static final String STEP_NAME = "FME Flag Definition Instructions";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_DEFINITION_INSTRUCTIONS_STEP...");
    logCallback.saveExecutionLog("Starting " + STEP_NAME, LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagDefinitionInstructionsStepParameters parameters =
        (FmeFlagDefinitionInstructionsStepParameters) stepParameters.getSpec();

    String flagName = requiredString(parameters.getFlagName(), "flagName");
    String environment = requiredString(parameters.getEnvironment(), "environment");
    List<FmeDefinitionInstruction> instructions = requiredInstructions(parameters.getInstructions());

    logCallback.saveExecutionLog(format("%s Inputs -> account: %s, org: %s, project: %s, flag: %s, environment: %s",
                                     STEP_NAME, scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                     scope.getProjectIdentifier(), flagName, environment),
        LogLevel.INFO);

    List<FmeDefinitionInstruction> patchInstructions = new ArrayList<>();
    FmeSetTargetingRulesInstruction targetingRulesInstruction = null;
    FmeSetRolloutStatusInstruction rolloutStatusInstruction = null;
    FmeSetFlagKilledInstruction flagKilledInstruction = null;

    for (FmeDefinitionInstruction instruction : instructions) {
      if (instruction instanceof FmeSetTargetingRulesInstruction) {
        if (targetingRulesInstruction != null) {
          throw new FmeInvalidParameterException(
              "Only one SetTargetingRules instruction is allowed per step execution");
        }
        targetingRulesInstruction = (FmeSetTargetingRulesInstruction) instruction;
      } else if (instruction instanceof FmeSetRolloutStatusInstruction) {
        if (rolloutStatusInstruction != null) {
          throw new FmeInvalidParameterException("Only one SetRolloutStatus instruction is allowed per step execution");
        }
        rolloutStatusInstruction = (FmeSetRolloutStatusInstruction) instruction;
      } else if (instruction instanceof FmeSetFlagKilledInstruction) {
        if (flagKilledInstruction != null) {
          throw new FmeInvalidParameterException("Only one SetFlagKilled instruction is allowed per step execution");
        }
        flagKilledInstruction = (FmeSetFlagKilledInstruction) instruction;
      } else {
        patchInstructions.add(instruction);
      }
    }

    FeatureFlagDefinition definitionBefore =
        getOrCreateDefinition(logCallback, scope, flagName, environment, parameters.getDefaultDefinition());
    logDefinition(logCallback, "BEFORE", flagName, environment, definitionBefore);
    if (rolloutStatusInstruction != null) {
      logFlagMetadata(logCallback, "BEFORE", flagName, getFeatureFlag(logCallback, scope, flagName));
    }

    boolean patchApplied = false;
    if (!patchInstructions.isEmpty()) {
      List<FmePatchOperation> patchOps = buildPatchOperations(patchInstructions, definitionBefore, logCallback);

      logCallback.saveExecutionLog(
          format("Calling FME API to apply %d patch operations for flag '%s' in environment '%s'", patchOps.size(),
              flagName, environment),
          LogLevel.INFO);

      logPatchOperations(logCallback, patchOps);
      patchDefinition(logCallback, scope, flagName, environment, patchOps);
      patchApplied = true;
    }

    if (targetingRulesInstruction != null) {
      List<TargetRules> targetRulesList =
          requiredInstructionValue(targetingRulesInstruction.getValue(), "SetTargetingRules");
      try {
        applyTargetingRules(logCallback, scope, flagName, environment, targetRulesList);
      } catch (Exception e) {
        if (patchApplied) {
          logCallback.saveExecutionLog(
              format("WARNING: Patch operations were already applied successfully before SetTargetingRules failed. "
                      + "Flag '%s' in environment '%s' may be in a partially updated state.",
                  flagName, environment),
              LogLevel.WARN);
        }
        throw e;
      }
    }

    if (rolloutStatusInstruction != null) {
      String statusValue = requiredInstructionValue(rolloutStatusInstruction.getValue(), "SetRolloutStatus");
      applySetRolloutStatus(logCallback, scope, flagName, statusValue);
    }

    if (flagKilledInstruction != null) {
      Boolean killed = requiredInstructionValue(flagKilledInstruction.getValue(), "SetFlagKilled");
      applySetFlagKilled(logCallback, scope, flagName, environment, killed);
    }

    FeatureFlagDefinition definitionAfter = getDefinition(logCallback, scope, flagName, environment);
    logDefinition(logCallback, "AFTER", flagName, environment, definitionAfter);
    if (rolloutStatusInstruction != null) {
      logFlagMetadata(logCallback, "AFTER", flagName, getFeatureFlag(logCallback, scope, flagName));
    }

    logCallback.saveExecutionLog(format("%s completed: flag: %s, environment: %s", STEP_NAME, flagName, environment),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return buildSuccessResponse(startTime, STEP_NAME,
        format("applied definition instructions for flag %s in environment %s", flagName, environment));
  }

  private List<FmePatchOperation> buildPatchOperations(
      List<FmeDefinitionInstruction> instructions, FeatureFlagDefinition definition, NGLogCallback logCallback) {
    List<FmePatchOperation> ops = new ArrayList<>();

    List<Treatment> effectiveTreatments = new ArrayList<>(definition.getTreatments());

    List<FmeDefinitionInstruction> orderedInstructions = reorderInstructions(instructions);

    Map<String, Treatment> treatmentMap = toTreatmentMap(effectiveTreatments);
    Map<String, Set<String>> changedFields = new LinkedHashMap<>();

    for (FmeDefinitionInstruction instruction : orderedInstructions) {
      switch (instruction.getType()) {
        case SetDefaultTreatment:
          FmeSetDefaultTreatmentInstruction setDefault = (FmeSetDefaultTreatmentInstruction) instruction;
          String defaultVal = requiredInstructionValue(setDefault.getValue(), "SetDefaultTreatment");
          ops.add(FmePatchOperation.replace("/defaultTreatment", defaultVal));
          logCallback.saveExecutionLog(format("Instruction: SetDefaultTreatment -> '%s'", defaultVal), LogLevel.INFO);
          break;

        case SetBaselineTreatment:
          FmeSetBaselineTreatmentInstruction setBaseline = (FmeSetBaselineTreatmentInstruction) instruction;
          String baselineVal = requiredInstructionValue(setBaseline.getValue(), "SetBaselineTreatment");
          ops.add(FmePatchOperation.replace("/baselineTreatment", baselineVal));
          logCallback.saveExecutionLog(format("Instruction: SetBaselineTreatment -> '%s'", baselineVal), LogLevel.INFO);
          break;

        case SetTrackImpression:
          FmeSetTrackImpressionInstruction setTrack = (FmeSetTrackImpressionInstruction) instruction;
          Boolean trackVal = requiredInstructionValue(setTrack.getValue(), "SetTrackImpression");
          ops.add(FmePatchOperation.replace("/impressionsDisabled", !trackVal));
          logCallback.saveExecutionLog(format("Instruction: SetTrackImpression -> %s", trackVal), LogLevel.INFO);
          break;

        case SetLimitExposure:
          FmeSetLimitExposureInstruction setLimit = (FmeSetLimitExposureInstruction) instruction;
          Integer limitVal = requiredInstructionValue(setLimit.getValue(), "SetLimitExposure");
          ops.add(FmePatchOperation.replace("/trafficAllocation", limitVal));
          logCallback.saveExecutionLog(format("Instruction: SetLimitExposure -> %s", limitVal), LogLevel.INFO);
          break;

        case UpdateIndividualTargets:
          FmeUpdateIndividualTargetsInstruction updateTargets = (FmeUpdateIndividualTargetsInstruction) instruction;
          applyIndividualTargets(requiredInstructionValue(updateTargets.getValue(), "UpdateIndividualTargets"),
              treatmentMap, logCallback, changedFields);
          break;

        case UpdateDynamicConfiguration:
          FmeUpdateDynamicConfigurationInstruction updateConfig =
              (FmeUpdateDynamicConfigurationInstruction) instruction;
          applyDynamicConfiguration(requiredInstructionValue(updateConfig.getValue(), "UpdateDynamicConfiguration"),
              treatmentMap, logCallback, changedFields);
          break;

        case SetDefaultAllocations:
          FmeSetDefaultAllocationsInstruction setAlloc = (FmeSetDefaultAllocationsInstruction) instruction;
          List<Allocation> allocations = requiredInstructionValue(setAlloc.getValue(), "SetDefaultAllocations");
          validateAllocation(allocations);
          List<Bucket> buckets = buildDefaultRuleBuckets(allocations);
          ops.add(FmePatchOperation.replace("/defaultRule", buckets));
          logCallback.saveExecutionLog(
              format("Instruction: SetDefaultAllocations -> %d allocations", allocations.size()), LogLevel.INFO);
          break;

        case SetTreatments:
          FmeSetTreatmentsInstruction setTreatmentsInstr = (FmeSetTreatmentsInstruction) instruction;
          List<TreatmentConfiguration> treatmentConfigs =
              requiredInstructionValue(setTreatmentsInstr.getValue(), "SetTreatments");
          if (treatmentConfigs.isEmpty()) {
            throw new FmeInvalidParameterException("SetTreatments: treatments list cannot be empty");
          }
          List<Treatment> updatedTreatments = buildUpdatedTreatments(treatmentConfigs, logCallback);
          ops.add(FmePatchOperation.replace("/treatments", updatedTreatments));
          effectiveTreatments = updatedTreatments;
          treatmentMap = toTreatmentMap(effectiveTreatments);
          logCallback.saveExecutionLog(
              format("Instruction: SetTreatments -> %d treatments", updatedTreatments.size()), LogLevel.INFO);
          break;

        default:
          throw new FmeInvalidParameterException(format("Unknown instruction type: '%s'", instruction.getType()));
      }
    }

    if (!changedFields.isEmpty()) {
      Map<String, Integer> treatmentIndexMap = buildTreatmentIndexMap(effectiveTreatments);
      for (Map.Entry<String, Set<String>> entry : changedFields.entrySet()) {
        String treatmentName = entry.getKey();
        Set<String> fields = entry.getValue();
        Integer index = treatmentIndexMap.get(treatmentName);
        if (index == null) {
          throw new FmeInvalidParameterException(
              format("Treatment '%s' not found when building deferred patch operations", treatmentName));
        }
        Treatment treatment = treatmentMap.get(treatmentName);
        String basePath = "/treatments/" + index;

        if (fields.contains("keys")) {
          ops.add(FmePatchOperation.add(basePath + "/keys", treatment.getKeys()));
        }
        if (fields.contains("segments")) {
          ops.add(FmePatchOperation.add(basePath + "/segments", treatment.getSegments()));
        }
        if (fields.contains("largeSegments")) {
          ops.add(FmePatchOperation.add(basePath + "/largeSegments", treatment.getLargeSegments()));
        }
        if (fields.contains("ruleBasedSegments")) {
          ops.add(FmePatchOperation.add(basePath + "/ruleBasedSegments", treatment.getRuleBasedSegments()));
        }
        if (fields.contains("configurations")) {
          ops.add(FmePatchOperation.add(basePath + "/configurations", treatment.getConfigurations()));
        }
      }
    }

    return ops;
  }

  private List<FmeDefinitionInstruction> reorderInstructions(List<FmeDefinitionInstruction> instructions) {
    List<FmeDefinitionInstruction> reordered = new ArrayList<>();
    FmeDefinitionInstruction setTreatmentsInstr = null;
    for (FmeDefinitionInstruction instr : instructions) {
      if (instr.getType() == FmeInstructionType.SetTreatments) {
        setTreatmentsInstr = instr;
      } else {
        reordered.add(instr);
      }
    }
    if (setTreatmentsInstr != null) {
      reordered.add(0, setTreatmentsInstr);
    }
    return reordered;
  }

  private void applyIndividualTargets(List<IndividualTargetUpdate> targetUpdates, Map<String, Treatment> treatmentMap,
      NGLogCallback logCallback, Map<String, Set<String>> changedFields) {
    for (IndividualTargetUpdate update : targetUpdates) {
      String treatmentName = update.getTreatment();
      if (Strings.isNullOrEmpty(treatmentName)) {
        throw new FmeInvalidParameterException("UpdateIndividualTargets: treatment name is required");
      }

      Treatment treatment = treatmentMap.getOrDefault(treatmentName, new Treatment());

      List<TargetAction> actions = update.getActions();
      if (actions == null || actions.isEmpty()) {
        throw new FmeInvalidParameterException(
            format("UpdateIndividualTargets: actions required for treatment '%s'", treatmentName));
      }

      validateNoConflictingActions(actions, treatmentName);

      for (TargetAction targetAction : actions) {
        List<String> values = targetAction.getValue() != null ? targetAction.getValue() : List.of();

        switch (targetAction.getAction()) {
          case AddKeys:
            treatment.setKeys(addToList(treatment.getKeys(), values));
            trackChange(changedFields, treatmentName, "keys");
            break;
          case RemoveKeys:
            treatment.setKeys(removeFromList(treatment.getKeys(), values));
            trackChange(changedFields, treatmentName, "keys");
            break;
          case SetKeys:
            treatment.setKeys(new ArrayList<>(values));
            trackChange(changedFields, treatmentName, "keys");
            break;
          case AddSegments:
            Set<String> allExisting = getAllSegments(treatment);
            List<String> trulyNew = values.stream().filter(v -> !allExisting.contains(v)).collect(Collectors.toList());
            treatment.setSegments(addToList(treatment.getSegments(), trulyNew));
            trackChange(changedFields, treatmentName, "segments");
            break;
          case RemoveSegments:
            Set<String> toRemove = new LinkedHashSet<>(values);
            if (hasAny(treatment.getSegments(), toRemove)) {
              treatment.setSegments(removeFromList(treatment.getSegments(), values));
              trackChange(changedFields, treatmentName, "segments");
            }
            if (hasAny(treatment.getLargeSegments(), toRemove)) {
              treatment.setLargeSegments(removeFromList(treatment.getLargeSegments(), values));
              trackChange(changedFields, treatmentName, "largeSegments");
            }
            if (hasAny(treatment.getRuleBasedSegments(), toRemove)) {
              treatment.setRuleBasedSegments(removeFromList(treatment.getRuleBasedSegments(), values));
              trackChange(changedFields, treatmentName, "ruleBasedSegments");
            }
            break;
          case SetSegments:
            treatment.setSegments(new ArrayList<>(values));
            treatment.setLargeSegments(new ArrayList<>());
            treatment.setRuleBasedSegments(new ArrayList<>());
            trackChange(changedFields, treatmentName, "segments");
            trackChange(changedFields, treatmentName, "largeSegments");
            trackChange(changedFields, treatmentName, "ruleBasedSegments");
            break;
          default:
            throw new FmeInvalidParameterException(
                format("UpdateIndividualTargets: unknown action '%s'", targetAction.getAction()));
        }

        logCallback.saveExecutionLog(
            format("Instruction: UpdateIndividualTargets -> treatment '%s', action '%s', %d items", treatmentName,
                targetAction.getAction(), values.size()),
            LogLevel.INFO);
      }
    }
  }

  private void applyDynamicConfiguration(List<DynamicConfigUpdate> configUpdates, Map<String, Treatment> treatmentMap,
      NGLogCallback logCallback, Map<String, Set<String>> changedFields) {
    for (DynamicConfigUpdate update : configUpdates) {
      String treatmentName = update.getTreatment();
      if (Strings.isNullOrEmpty(treatmentName)) {
        throw new FmeInvalidParameterException("UpdateDynamicConfiguration: treatment name is required");
      }

      Treatment treatment = treatmentMap.getOrDefault(treatmentName, new Treatment());

      String configuration = update.getConfiguration();
      if (configuration != null) {
        validateJsonSyntax(treatmentName, configuration);
      }

      treatment.setConfigurations(configuration);
      trackChange(changedFields, treatmentName, "configurations");
      logCallback.saveExecutionLog(format("Instruction: UpdateDynamicConfiguration -> treatment '%s', config %s",
                                       treatmentName, configuration != null ? "set" : "unset"),
          LogLevel.INFO);
    }
  }

  private void applyTargetingRules(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<TargetRules> targetRulesList) {
    logCallback.saveExecutionLog(
        format("Instruction: SetTargetingRules -> %d rule(s) for flag '%s' in environment '%s'", targetRulesList.size(),
            flagName, environment),
        LogLevel.INFO);

    List<TargetingRulesDTO> ruleDtos = FmeTargetingRulesMapper.toTargetingRulesDTOs(targetRulesList);

    FmeTargetingRulesMapper.logTargetingRules(logCallback, ruleDtos);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("set targeting rules")
                                   .build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.updateFeatureFlagDefinitionRules(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), flagName, environment, ruleDtos),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("SetTargetingRules request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("Successfully applied targeting rules for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);
  }

  private void applySetRolloutStatus(NGLogCallback logCallback, Scope scope, String flagName, String status) {
    logCallback.saveExecutionLog(
        format("Applying SetRolloutStatus: setting rollout status to '%s' for flag '%s'", status, flagName),
        LogLevel.INFO);

    URN urn = new URN();
    urn.type = "RolloutStatus";
    urn.name = status;
    List<FmePatchOperation> ops = List.of(FmePatchOperation.replace("/rolloutStatus", urn));

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .operationName("setRolloutStatus")
                                   .build();

    FmeApiExecutor.executeWrapped(fmePipelineClient.updateFeatureFlag(scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), flagName, ops),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("SetRolloutStatus failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("Instruction: SetRolloutStatus -> '%s' applied successfully", status), LogLevel.INFO);
  }

  private void applySetFlagKilled(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, boolean killed) {
    String action = killed ? "kill" : "restore";
    logCallback.saveExecutionLog(
        format("Instruction: SetFlagKilled -> %s flag '%s' in environment '%s'", action, flagName, environment),
        LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName(action)
                                   .build();

    if (killed) {
      FmeApiExecutor.executeWrapped(fmePipelineClient.killFeatureFlag(flagName, scope.getAccountIdentifier(),
                                        scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
          context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
          (flag, env, errorBody) -> format("Error killing feature flag '%s'. Error: %s", flag, errorBody));
    } else {
      FmeApiExecutor.executeWrapped(fmePipelineClient.restoreFeatureFlag(flagName, scope.getAccountIdentifier(),
                                        scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
          context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
          (flag, env, errorBody) -> format("Error restoring feature flag '%s'. Error: %s", flag, errorBody));
    }

    logCallback.saveExecutionLog(
        format("Successfully %s flag '%s' in environment '%s'", killed ? "killed" : "restored", flagName, environment),
        LogLevel.INFO);
  }

  private FeatureFlagDefinition getOrCreateDefinition(NGLogCallback logCallback, Scope scope, String flagName,
      String environment, DefaultDefinitionConfig defaultDefinition) {
    logCallback.saveExecutionLog(
        format("Fetching current definition for flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    FeatureFlagDefinition definition = FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(flagName, scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.RETURN_NULL,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));

    if (definition != null && definition.getTreatments() != null) {
      return definition;
    }

    if (defaultDefinition == null) {
      throw new FmeFeatureFlagDefinitionNotFoundException(flagName, environment);
    }

    logCallback.saveExecutionLog(
        format("Definition not found for flag '%s' in environment '%s', creating from defaultDefinition...", flagName,
            environment),
        LogLevel.INFO);

    List<TreatmentConfiguration> treatmentConfigs =
        requiredInstructionValue(defaultDefinition.getTreatments(), "defaultDefinition.treatments");

    List<Treatment> treatments = new ArrayList<>();
    for (TreatmentConfiguration tc : treatmentConfigs) {
      String name = Optional.ofNullable(tc.getTreatment()).map(ParameterField::obtainValue).orElse(null);
      String desc = Optional.ofNullable(tc.getDescription()).map(ParameterField::obtainValue).orElse(null);
      treatments.add(Treatment.builder().name(name).description(desc).build());
    }

    String defaultTreatment =
        requiredInstructionValue(defaultDefinition.getDefaultTreatment(), "defaultDefinition.defaultTreatment");
    String baselineTreatment =
        requiredInstructionValue(defaultDefinition.getBaselineTreatment(), "defaultDefinition.baselineTreatment");

    logCallback.saveExecutionLog(
        format("Using defaultTreatment='%s', baselineTreatment='%s'", defaultTreatment, baselineTreatment),
        LogLevel.INFO);

    List<Bucket> defaultRule = new ArrayList<>();
    for (Treatment t : treatments) {
      int size = t.getName().equals(defaultTreatment) ? 100 : 0;
      defaultRule.add(Bucket.builder().treatment(t.getName()).size(size).build());
    }

    FeatureFlagDefinition newDefinition = FeatureFlagDefinition.builder()
                                              .name(flagName)
                                              .treatments(treatments)
                                              .defaultTreatment(defaultTreatment)
                                              .baselineTreatment(baselineTreatment)
                                              .trafficAllocation(100)
                                              .defaultRule(defaultRule)
                                              .build();

    ExecutionContext createContext = ExecutionContext.builder()
                                         .logCallback(logCallback)
                                         .flagName(flagName)
                                         .environment(environment)
                                         .operationName("create definition")
                                         .build();

    List<FmeResponse<FeatureFlagDefinition>> createResponses =
        FmeApiExecutor.execute(fmePipelineClient.createFeatureFlagDefinition(scope.getAccountIdentifier(),
                                   scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment, newDefinition),
            createContext, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
            (flag, env,
                errorBody) -> format("Failed to create default definition for flag '%s'. Error: %s", flag, errorBody));

    if (createResponses == null || createResponses.isEmpty()) {
      throw new FmeInternalServerErrorException(format(
          "Create definition API returned empty response for flag '%s' in environment '%s'", flagName, environment));
    }

    if (createResponses.size() > 1) {
      log.warn("Create definition API returned {} items for flag '{}'; using only the first.", createResponses.size(),
          flagName);
    }

    FeatureFlagDefinition created = createResponses.get(0).getEntity();

    logCallback.saveExecutionLog(
        format("Successfully created default definition for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);

    return created;
  }

  private FeatureFlag getFeatureFlag(NGLogCallback logCallback, Scope scope, String flagName) {
    logCallback.saveExecutionLog(format("Fetching metadata for flag '%s'", flagName), LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .operationName("get feature flag metadata")
                                   .build();

    return FmeApiExecutor.executeWrapped(fmePipelineClient.getFeatureFlag(flagName, scope.getAccountIdentifier(),
                                             scope.getOrgIdentifier(), scope.getProjectIdentifier()),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to get feature flag metadata. Error: %s", errorBody));
  }

  private void logFlagMetadata(NGLogCallback logCallback, String label, String flagName, FeatureFlag flag) {
    if (flag == null) {
      return;
    }
    try {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("name", flag.getName());
      metadata.put("rolloutStatus", flag.getRolloutStatus());
      metadata.put("rolloutStatusTimestamp", flag.getRolloutStatusTimestamp());
      String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
      logCallback.saveExecutionLog(
          format("========== FLAG METADATA [%s] flag='%s' ==========\n%s\n==========", label, flagName, json),
          LogLevel.INFO);
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Failed to serialize flag metadata [%s]: %s", label, e.getMessage()), LogLevel.WARN);
    }
  }

  private void validateAllocation(List<Allocation> allocations) {
    if (allocations == null || allocations.isEmpty()) {
      throw new FmeInvalidParameterException("SetDefaultAllocations: allocations must be provided");
    }

    int sum =
        allocations.stream()
            .filter(
                a -> ParameterField.isNotNull(a.getTreatment()) && !Strings.isNullOrEmpty(a.getTreatment().getValue()))
            .filter(a -> ParameterField.isNotNull(a.getAmount()))
            .mapToInt(a -> ((Number) a.getAmount().fetchFinalValue()).intValue())
            .sum();
    if (sum != 100) {
      throw new FmeInvalidParameterException(
          format("SetDefaultAllocations: total allocation must equal 100, got %d", sum));
    }
  }

  private List<Bucket> buildDefaultRuleBuckets(List<Allocation> allocations) {
    return allocations.stream()
        .filter(a -> ParameterField.isNotNull(a.getTreatment()) && !Strings.isNullOrEmpty(a.getTreatment().getValue()))
        .map(a
            -> Bucket.builder()
                   .treatment(a.getTreatment().getValue())
                   .size(
                       ParameterField.isNull(a.getAmount()) ? 0 : ((Number) a.getAmount().fetchFinalValue()).intValue())
                   .build())
        .collect(Collectors.toList());
  }

  private List<Treatment> buildUpdatedTreatments(List<TreatmentConfiguration> configs, NGLogCallback logCallback) {
    Map<String, Treatment> treatmentMap = new LinkedHashMap<>();

    for (TreatmentConfiguration tc : configs) {
      String treatmentName =
          Optional.ofNullable(tc.getTreatment())
              .map(ParameterField::getValue)
              .filter(s -> !Strings.isNullOrEmpty(s))
              .orElseThrow(() -> new FmeInvalidParameterException("SetTreatments: treatment name is required"));
      String description = tc.getDescription() != null ? tc.getDescription().getValue() : null;

      treatmentMap.put(treatmentName, Treatment.builder().name(treatmentName).description(description).build());
    }

    logCallback.saveExecutionLog(
        format("SetTreatments: setting treatments to: %s", new ArrayList<>(treatmentMap.keySet())), LogLevel.INFO);

    return new ArrayList<>(treatmentMap.values());
  }

  private FeatureFlagDefinition getDefinition(
      NGLogCallback logCallback, Scope scope, String flagName, String environment) {
    logCallback.saveExecutionLog(
        format("Fetching current definition for flag '%s' in environment '%s'", flagName, environment), LogLevel.INFO);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("get definition")
                                   .build();

    FeatureFlagDefinition definition = FmeApiExecutor.executeWrapped(
        fmePipelineClient.getFeatureFlagDefinitionInEnvironment(flagName, scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), environment),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("Failed to get feature flag definition. Error: %s", errorBody));

    if (definition == null || definition.getTreatments() == null) {
      throw new FmeFeatureFlagDefinitionNotFoundException(flagName, environment);
    }

    return definition;
  }

  private void patchDefinition(
      NGLogCallback logCallback, Scope scope, String flagName, String environment, List<FmePatchOperation> patchOps) {
    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCallback)
                                   .flagName(flagName)
                                   .environment(environment)
                                   .operationName("apply instructions")
                                   .build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.patchFeatureFlagDefinition(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), environment, flagName, patchOps),
        context, NotFoundBehavior.THROW_DEFINITION_NOT_FOUND,
        (flag, env, errorBody) -> format("%s request failed. Error: %s", STEP_NAME, errorBody));

    logCallback.saveExecutionLog(
        format("Successfully applied instructions for flag '%s' in environment '%s'", flagName, environment),
        LogLevel.INFO);
  }

  private void logPatchOperations(NGLogCallback logCallback, List<FmePatchOperation> patchOps) {
    try {
      String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(patchOps);
      logCallback.saveExecutionLog(
          format("========== PATCH OPERATIONS BEING SENT ==========\n%s\n==========", json), LogLevel.INFO);
    } catch (Exception e) {
      logCallback.saveExecutionLog(format("Failed to serialize patch operations: %s", e.getMessage()), LogLevel.WARN);
    }
  }

  private void logDefinition(
      NGLogCallback logCallback, String label, String flagName, String environment, FeatureFlagDefinition definition) {
    if (definition == null) {
      return;
    }
    try {
      String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
      logCallback.saveExecutionLog(
          format("========== FLAG DEFINITION [%s] flag='%s' env='%s' ==========\n%s\n==========", label, flagName,
              environment, json),
          LogLevel.INFO);
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Failed to serialize definition [%s]: %s", label, e.getMessage()), LogLevel.WARN);
    }
  }

  private Set<String> getAllSegments(Treatment treatment) {
    Set<String> all = new LinkedHashSet<>();
    if (treatment.getSegments() != null) {
      all.addAll(treatment.getSegments());
    }
    if (treatment.getLargeSegments() != null) {
      all.addAll(treatment.getLargeSegments());
    }
    if (treatment.getRuleBasedSegments() != null) {
      all.addAll(treatment.getRuleBasedSegments());
    }
    return all;
  }

  private boolean hasAny(List<String> list, Set<String> items) {
    return list != null && list.stream().anyMatch(items::contains);
  }

  private void validateNoConflictingActions(List<TargetAction> actions, String treatmentName) {
    boolean hasAddKeys = false;
    boolean hasRemoveKeys = false;
    boolean hasSetKeys = false;
    boolean hasAddSegments = false;
    boolean hasRemoveSegments = false;
    boolean hasSetSegments = false;

    for (TargetAction action : actions) {
      switch (action.getAction()) {
        case AddKeys:
          hasAddKeys = true;
          break;
        case RemoveKeys:
          hasRemoveKeys = true;
          break;
        case SetKeys:
          hasSetKeys = true;
          break;
        case AddSegments:
          hasAddSegments = true;
          break;
        case RemoveSegments:
          hasRemoveSegments = true;
          break;
        case SetSegments:
          hasSetSegments = true;
          break;
        default:
          break;
      }
    }

    if (hasSetKeys && (hasAddKeys || hasRemoveKeys)) {
      throw new FmeInvalidParameterException(
          format("UpdateIndividualTargets: treatment '%s' has conflicting key actions. "
                  + "SetKeys cannot be combined with AddKeys or RemoveKeys",
              treatmentName));
    }
    if (hasSetSegments && (hasAddSegments || hasRemoveSegments)) {
      throw new FmeInvalidParameterException(
          format("UpdateIndividualTargets: treatment '%s' has conflicting segment actions. "
                  + "SetSegments cannot be combined with AddSegments or RemoveSegments",
              treatmentName));
    }
  }

  private Map<String, Treatment> toTreatmentMap(List<Treatment> treatments) {
    Map<String, Treatment> map = new LinkedHashMap<>();
    if (treatments != null) {
      treatments.forEach(t -> map.put(t.getName(), t));
    }
    return map;
  }

  private Map<String, Integer> buildTreatmentIndexMap(List<Treatment> treatments) {
    Map<String, Integer> map = new LinkedHashMap<>();
    if (treatments != null) {
      for (int i = 0; i < treatments.size(); i++) {
        map.put(treatments.get(i).getName(), i);
      }
    }
    return map;
  }

  private void trackChange(Map<String, Set<String>> changedFields, String treatmentName, String field) {
    changedFields.computeIfAbsent(treatmentName, k -> new LinkedHashSet<>()).add(field);
  }

  private List<String> addToList(List<String> existing, List<String> toAdd) {
    Set<String> result = new LinkedHashSet<>(existing != null ? existing : List.of());
    result.addAll(toAdd);
    return new ArrayList<>(result);
  }

  private List<String> removeFromList(List<String> existing, List<String> toRemove) {
    if (existing == null) {
      return new ArrayList<>();
    }
    Set<String> removeSet = new LinkedHashSet<>(toRemove);
    return existing.stream().filter(item -> !removeSet.contains(item)).collect(Collectors.toList());
  }

  private void validateJsonSyntax(String treatmentName, String json) {
    try {
      JsonNode node = OBJECT_MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new FmeInvalidParameterException(
            format("Configuration must be a JSON object for treatment '%s', got: %s", treatmentName, json));
      }
    } catch (FmeInvalidParameterException e) {
      throw e;
    } catch (Exception e) {
      throw new FmeInvalidParameterException(
          format("Invalid JSON configuration for treatment '%s': %s", treatmentName, json));
    }
  }

  private String requiredString(ParameterField<String> field, String paramName) {
    return Optional.ofNullable(field)
        .map(ParameterField::obtainValue)
        .filter(s -> !Strings.isNullOrEmpty(s))
        .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: " + paramName));
  }

  private <T> T requiredInstructionValue(ParameterField<T> field, String instructionType) {
    if (field == null || field.getValue() == null) {
      throw new FmeInvalidParameterException(
          format("%s: value must not be null or an unresolved expression", instructionType));
    }
    return field.getValue();
  }

  private List<FmeDefinitionInstruction> requiredInstructions(ParameterField<List<FmeDefinitionInstruction>> field) {
    if (field == null || field.getValue() == null || field.getValue().isEmpty()) {
      throw new FmeInvalidParameterException("Missing required parameter: instructions");
    }
    return field.getValue();
  }
}
