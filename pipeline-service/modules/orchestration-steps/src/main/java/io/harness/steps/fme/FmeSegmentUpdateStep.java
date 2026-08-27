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
import io.harness.fme.FMEPipelineClient;
import io.harness.fme.FmePatchOperation;
import io.harness.fme.SegmentMetadataExternal;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.StringOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineSyncExecutable;
import io.harness.steps.fme.FmeApiExecutor.ExecutionContext;
import io.harness.steps.fme.FmeApiExecutor.NotFoundBehavior;
import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeSegmentUpdateStep extends PipelineSyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_SEGMENT_UPDATE_STEP_TYPE;
  private static final String INFRASTRUCTURE_COMMAND_UNIT = "Execute";

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private FMEPipelineClient fmePipelineClient;
  @Inject private FmeStepResponseBuilder fmeStepResponseBuilder;
  @Inject private FmeOwnerResolver fmeOwnerResolver;

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Executing FME_SEGMENT_UPDATE_STEP...");
    long startTime = System.currentTimeMillis();
    NGLogCallback logCb = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    try {
      logCb.saveExecutionLog("Starting FME Segment Metadata Update", LogLevel.INFO);

      Scope scope = buildScope(ambiance);
      FmeSegmentUpdateParameters parameters = (FmeSegmentUpdateParameters) stepParameters.getSpec();
      String segmentName = getRequiredSegmentName(parameters);

      logExecutionInputs(logCb, scope, segmentName, parameters);

      List<FmePatchOperation> patch = buildPatch(parameters);
      if (patch.isEmpty()) {
        logCb.saveExecutionLog("No segment metadata fields provided; skipping update.", LogLevel.INFO);
        return buildSkippedResponse(segmentName, startTime);
      }

      updateSegmentMetadata(logCb, scope, segmentName, patch);
      logCb.saveExecutionLog(format("FME Segment Metadata Updated: segment: %s", segmentName), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);

      return buildSuccessResponse(segmentName, startTime);
    } catch (Exception e) {
      log.error("Step execution failed: {}", e.getMessage(), e);
      logCb.saveExecutionLog(
          format("Step execution failed: %s", e.getMessage()), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return fmeStepResponseBuilder.getFailedStepResponse(startTime, System.currentTimeMillis(), e);
    }
  }

  private Scope buildScope(Ambiance ambiance) {
    return Scope.builder()
        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .build();
  }

  private String getRequiredSegmentName(FmeSegmentUpdateParameters parameters) {
    return testStringParameter(parameters.getName())
        .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: segment name"));
  }

  private void logExecutionInputs(
      NGLogCallback logCb, Scope scope, String segmentName, FmeSegmentUpdateParameters parameters) {
    logCb.saveExecutionLog(format("Updating segment '%s' in account: %s, org: %s, project: %s", segmentName,
                               scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()),
        LogLevel.INFO);
  }

  private StepResponse buildSuccessResponse(String segmentName, long startTime) {
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name("FME Segment Update")
                .outcome(StringOutcome.builder().message(format("Updated segment metadata: %s", segmentName)).build())
                .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  private StepResponse buildSkippedResponse(String segmentName, long startTime) {
    return StepResponse.builder()
        .status(Status.SKIPPED)
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name("FME Segment Update")
                .outcome(StringOutcome.builder()
                             .message(format("Skipped segment metadata update: %s (no fields provided)", segmentName))
                             .build())
                .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SKIPPED)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  private void updateSegmentMetadata(
      NGLogCallback logCb, Scope scope, String segmentName, List<FmePatchOperation> patch) {
    // Detailed debug logging before API call - visible in pipeline execution logs
    logCb.saveExecutionLog("=== FME Segment Update Debug Info ===", LogLevel.INFO);
    logCb.saveExecutionLog(format("  Segment Name: %s", segmentName), LogLevel.INFO);
    logCb.saveExecutionLog(format("  Account ID (Harness): %s", scope.getAccountIdentifier()), LogLevel.INFO);
    logCb.saveExecutionLog(format("  Organization ID (Harness): %s", scope.getOrgIdentifier()), LogLevel.INFO);
    logCb.saveExecutionLog(format("  Project ID (Harness): %s", scope.getProjectIdentifier()), LogLevel.INFO);
    logCb.saveExecutionLog(format("  Patch Operations: %s", patch), LogLevel.INFO);
    logCb.saveExecutionLog(
        format("  API Endpoint: PATCH "
                + "/internal/api/v3/fme/segment?account_id=%s&organization_id=%s&project_id=%s&segment_name=%s",
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segmentName),
        LogLevel.INFO);
    logCb.saveExecutionLog("=====================================", LogLevel.INFO);

    // Log to server logs as well
    log.info("[FME Segment Update] Preparing PATCH request: accountId={}, orgId={}, projectId={}, segmentName={}, "
            + "patchOperations={}",
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segmentName, patch);

    Call<SegmentMetadataExternal> call = fmePipelineClient.patchSegmentMetadata(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), segmentName, patch);

    ExecutionContext context = ExecutionContext.builder()
                                   .logCallback(logCb)
                                   .flagName(segmentName)
                                   .operationName("update segment metadata")
                                   .build();

    FmeApiExecutor.execute(call, context, NotFoundBehavior.THROW_SEGMENT_NOT_FOUND,
        (segment, env, errorBody) -> format("FME Segment metadata update request failed. Error: %s", errorBody));
  }

  /**
   * Checks if description field has an update.
   * Returns true if field is provided and either:
   * - It's a runtime expression (will be resolved at runtime)
   * - It has a non-empty value (after trimming)
   */
  private boolean hasDescription(ParameterField<String> description) {
    if (description == null || ParameterField.isNull(description)) {
      return false;
    }
    if (description.isExpression()) {
      return true; // Runtime expression is treated as specified
    }
    String value = description.obtainValue();
    return StringUtils.isNotBlank(value);
  }

  /**
   * Checks if owners field has an update.
   * Returns true if field is provided and either:
   * - It's a runtime expression (will be resolved at runtime)
   * - It has a non-empty list
   */
  private boolean hasOwners(ParameterField<List<String>> owners) {
    if (owners == null || ParameterField.isNull(owners)) {
      return false;
    }
    if (owners.isExpression()) {
      return true; // Runtime expression is treated as specified
    }
    List<String> value = owners.obtainValue();
    return value != null && !value.isEmpty();
  }

  /**
   * Checks if tags field has an update.
   * Returns true if field is provided and either:
   * - It's a runtime expression (will be resolved at runtime)
   * - It has a non-empty list
   */
  private boolean hasTags(ParameterField<List<String>> tags) {
    if (tags == null || ParameterField.isNull(tags)) {
      return false;
    }
    if (tags.isExpression()) {
      return true; // Runtime expression is treated as specified
    }
    List<String> value = tags.obtainValue();
    return value != null && !value.isEmpty();
  }

  /**
   * Builds JSON Patch operations from step parameters.
   * Only creates ops for fields with non-empty values or runtime expressions.
   * Empty values are treated as NO CHANGE (no patch op created).
   */
  private List<FmePatchOperation> buildPatch(FmeSegmentUpdateParameters parameters) {
    List<FmePatchOperation> ops = new ArrayList<>();

    if (hasTags(parameters.getTags())) {
      ops.add(createTagsPatchOp(parameters.getTags()));
    }

    if (hasDescription(parameters.getDescription())) {
      ops.add(createDescriptionPatchOp(parameters.getDescription()));
    }

    if (hasOwners(parameters.getOwners())) {
      ops.add(createOwnersPatchOp(parameters.getOwners()));
    }

    return ops;
  }

  private FmePatchOperation createTagsPatchOp(ParameterField<List<String>> tagsField) {
    List<String> tags = resolveListFieldOrEmpty(tagsField, "tags");
    return FmePatchOperation.replace("/tags", getUrns(tags, "Tag"));
  }

  private FmePatchOperation createDescriptionPatchOp(ParameterField<String> descField) {
    String description = resolveStringFieldOrEmpty(descField, "description");
    return FmePatchOperation.replace("/description", description);
  }

  private FmePatchOperation createOwnersPatchOp(ParameterField<List<String>> ownersField) {
    List<String> owners = resolveListFieldOrEmpty(ownersField, "owners");
    owners = fmeOwnerResolver.resolveOwners(owners);
    return FmePatchOperation.replace("/owners", FmeOwnerHelper.parseOwners(owners));
  }

  private List<String> resolveListFieldOrEmpty(ParameterField<List<String>> field, String fieldName) {
    List<String> resolved = resolveFieldValue(field, fieldName);
    return resolved != null ? resolved : Collections.emptyList();
  }

  private String resolveStringFieldOrEmpty(ParameterField<String> field, String fieldName) {
    String resolved = resolveFieldValue(field, fieldName);
    return resolved != null ? resolved : "";
  }

  private <T> T resolveFieldValue(ParameterField<T> field, String fieldName) {
    if (!field.isExpression()) {
      return field.getValue();
    }

    T value = field.obtainValue();
    if (value != null) {
      return value;
    }

    T defaultValue = field.getDefaultValue();
    if (defaultValue != null) {
      return defaultValue;
    }

    throw new FmeInvalidParameterException(
        format("%s is a runtime input/expression but was not resolved and has no default value.", fieldName));
  }

  private FmePatchOperation buildReplacePatch(Object value, String path) {
    return FmePatchOperation.replace(path, value);
  }

  private static List<URN> getUrns(List<String> names, String type) {
    return names.stream().map(name -> createUrn(type, name)).collect(Collectors.toList());
  }

  private static URN createUrn(String type, String nameOrId) {
    URN urn = new URN();
    urn.type = type;
    if (type.equals("User")) {
      urn.id = nameOrId;
    } else {
      urn.name = nameOrId;
    }
    return urn;
  }

  private Optional<String> testStringParameter(ParameterField<String> parameter) {
    return Optional.ofNullable(parameter).map(ParameterField::obtainValue).filter(s -> !Strings.isNullOrEmpty(s));
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }
}
