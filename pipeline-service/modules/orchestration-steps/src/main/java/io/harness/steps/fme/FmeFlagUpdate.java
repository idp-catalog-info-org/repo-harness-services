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
import io.harness.fme.FmePatchOperation;
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
import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagUpdate extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_UPDATE_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_UPDATE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Update", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagUpdateParameters p = (FmeFlagUpdateParameters) stepParameters.getSpec();

    String flagName = getStringParam(p.getName())
                          .orElseThrow(() -> new FmeInvalidParameterException("Missing required parameter: flag name"));

    logCallback.saveExecutionLog(format("Updating feature flag '%s'", flagName), LogLevel.INFO);

    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(flagName).operationName("update").build();

    FmeApiExecutor.executeWrapped(fmePipelineClient.updateFeatureFlag(scope.getAccountIdentifier(),
                                      scope.getOrgIdentifier(), scope.getProjectIdentifier(), flagName, buildPatch(p)),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag update request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("FME Flag Updated: %s", flagName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(startTime, "FME Flag Update", format("updated flag %s", flagName));
  }

  private List<FmePatchOperation> buildPatch(FmeFlagUpdateParameters p) {
    List<FmePatchOperation> ops = new ArrayList<>();

    List<String> tags = getListParam(p.getTags());
    if (!tags.isEmpty()) {
      ops.add(FmePatchOperation.replace("/tags", toUrns(tags, "Tag")));
    }

    getStringParam(p.getRolloutStatus()).ifPresent(status -> {
      URN urn = new URN();
      urn.type = "RolloutStatus";
      urn.name = status;
      ops.add(FmePatchOperation.replace("/rolloutStatus", urn));
    });

    getStringParam(p.getDescription()).ifPresent(desc -> ops.add(FmePatchOperation.replace("/description", desc)));

    List<String> owners = getListParam(p.getOwners());
    if (!owners.isEmpty()) {
      owners = fmeOwnerResolver.resolveOwners(owners);
      ops.add(FmePatchOperation.replace("/owners", FmeOwnerHelper.parseOwners(owners)));
    }

    return ops;
  }

  private List<URN> toUrns(List<String> names, String type) {
    return names.stream()
        .map(name -> {
          URN u = new URN();
          u.type = type;
          u.name = name;
          return u;
        })
        .toList();
  }

  private Optional<String> getStringParam(ParameterField<String> param) {
    return Optional.ofNullable(param).map(ParameterField::obtainValue).filter(s -> !Strings.isNullOrEmpty(s));
  }

  private <T> List<T> getListParam(ParameterField<List<T>> param) {
    return Optional.ofNullable(param).map(ParameterField::obtainValue).orElse(List.of());
  }
}
