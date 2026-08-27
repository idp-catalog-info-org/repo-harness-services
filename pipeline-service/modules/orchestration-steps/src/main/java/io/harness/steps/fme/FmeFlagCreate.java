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
import io.harness.fme.Bucket;
import io.harness.fme.FeatureFlag;
import io.harness.fme.FeatureFlagDefinition;
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

import com.google.common.base.Strings;
import io.split.client.dtos.URN;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeFlagCreate extends FmeBaseStep {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FME_FLAG_CREATE_STEP_TYPE;

  @Override
  protected StepResponse executeFmeStep(
      Ambiance ambiance, StepBaseParameters stepParameters, NGLogCallback logCallback, long startTime) {
    log.info("Executing FME_FLAG_CREATE_STEP...");
    logCallback.saveExecutionLog("Starting FME Flag Create", LogLevel.INFO);

    Scope scope = getScope(ambiance);
    FmeFlagCreateParameters p = (FmeFlagCreateParameters) stepParameters.getSpec();

    String flagName = Optional.ofNullable(p.getName()).map(ParameterField::obtainValue).orElse(null);
    String trafficType = Optional.ofNullable(p.getTrafficType()).map(ParameterField::obtainValue).orElse(null);

    if (Strings.isNullOrEmpty(flagName)) {
      throw new FmeInvalidParameterException("Missing required parameter: flag name");
    }
    if (Strings.isNullOrEmpty(trafficType)) {
      throw new FmeInvalidParameterException("Missing required parameter: traffic type");
    }

    String description = Optional.ofNullable(p.getDescription()).map(ParameterField::obtainValue).orElse(null);
    List<String> tags = resolveOptionalStringList(p.getTags());
    List<String> owners = Optional.ofNullable(p.getOwners()).map(ParameterField::obtainValue).orElse(List.of());

    List<TreatmentConfiguration> treatmentConfigs =
        Optional.ofNullable(p.getTreatments()).map(ParameterField::obtainValue).orElse(null);
    String defaultTreatment =
        Optional.ofNullable(p.getDefaultTreatment()).map(ParameterField::obtainValue).orElse(null);
    String baselineTreatment =
        Optional.ofNullable(p.getBaselineTreatment()).map(ParameterField::obtainValue).orElse(null);

    validateTreatmentFields(treatmentConfigs, defaultTreatment, baselineTreatment);

    List<URN> tagUrns = tags.stream().map(t -> createUrn("Tag", t, null)).toList();
    owners = fmeOwnerResolver.resolveOwners(owners);
    List<URN> ownerUrns = FmeOwnerHelper.parseOwners(owners);

    FeatureFlag featureFlag =
        FeatureFlag.builder().name(flagName).description(description).tags(tagUrns).owners(ownerUrns).build();

    if (treatmentConfigs != null) {
      featureFlag.setDefaultRolloutDefinition(
          buildDefaultRolloutDefinition(flagName, treatmentConfigs, defaultTreatment, baselineTreatment));
    }

    logCallback.saveExecutionLog(
        format("Creating feature flag '%s' with trafficType '%s'", flagName, trafficType), LogLevel.INFO);

    ExecutionContext context =
        ExecutionContext.builder().logCallback(logCallback).flagName(flagName).operationName("create").build();

    FmeApiExecutor.executeWrapped(
        fmePipelineClient.createFeatureFlag(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), trafficType, true, featureFlag),
        context, NotFoundBehavior.THROW_FLAG_NOT_FOUND,
        (flag, env, errorBody) -> format("FME Flag creation request failed. Error: %s", errorBody));

    logCallback.saveExecutionLog(
        format("FME Flag Created: %s", flagName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return buildSuccessResponse(startTime, "FME Flag Create", format("created flag %s", flagName));
  }

  private List<String> resolveOptionalStringList(ParameterField<List<String>> field) {
    if (ParameterField.isNull(field) || ParameterField.isBlank(field)) {
      return List.of();
    }
    List<String> value = field.obtainValue();
    return value == null ? List.of() : value;
  }

  private void validateTreatmentFields(
      List<TreatmentConfiguration> treatmentConfigs, String defaultTreatment, String baselineTreatment) {
    boolean hasTreatments = treatmentConfigs != null && !treatmentConfigs.isEmpty();
    boolean hasDefault = !Strings.isNullOrEmpty(defaultTreatment);
    boolean hasBaseline = !Strings.isNullOrEmpty(baselineTreatment);

    int providedCount = (hasTreatments ? 1 : 0) + (hasDefault ? 1 : 0) + (hasBaseline ? 1 : 0);
    if (providedCount != 0 && providedCount != 3) {
      throw new FmeInvalidParameterException("All three fields (treatments, defaultTreatment, baselineTreatment) must "
          + "be provided together, or none at all");
    }
  }

  private FeatureFlagDefinition buildDefaultRolloutDefinition(String flagName,
      List<TreatmentConfiguration> treatmentConfigs, String defaultTreatment, String baselineTreatment) {
    List<Treatment> treatments = new ArrayList<>();
    for (TreatmentConfiguration tc : treatmentConfigs) {
      String name = Optional.ofNullable(tc.getTreatment()).map(ParameterField::obtainValue).orElse(null);
      String desc = Optional.ofNullable(tc.getDescription()).map(ParameterField::obtainValue).orElse(null);
      treatments.add(Treatment.builder().name(name).description(desc).build());
    }

    List<Bucket> defaultRule = new ArrayList<>();
    for (Treatment t : treatments) {
      int size = t.getName().equals(defaultTreatment) ? 100 : 0;
      defaultRule.add(Bucket.builder().treatment(t.getName()).size(size).build());
    }

    return FeatureFlagDefinition.builder()
        .name(flagName)
        .treatments(treatments)
        .defaultTreatment(defaultTreatment)
        .baselineTreatment(baselineTreatment)
        .trafficAllocation(100)
        .defaultRule(defaultRule)
        .build();
  }

  private URN createUrn(String type, String name, String id) {
    URN urn = new URN();
    urn.type = type;
    urn.name = name;
    urn.id = id;
    return urn;
  }
}
