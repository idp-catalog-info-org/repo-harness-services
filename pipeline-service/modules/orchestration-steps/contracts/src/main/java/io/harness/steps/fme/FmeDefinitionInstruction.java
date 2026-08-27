/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.annotations.dev.HarnessTeam.FME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FmeSetDefaultTreatmentInstruction.class, name = "SetDefaultTreatment")
  , @JsonSubTypes.Type(value = FmeSetBaselineTreatmentInstruction.class, name = "SetBaselineTreatment"),
      @JsonSubTypes.Type(value = FmeSetTrackImpressionInstruction.class, name = "SetTrackImpression"),
      @JsonSubTypes.Type(value = FmeSetLimitExposureInstruction.class, name = "SetLimitExposure"),
      @JsonSubTypes.Type(value = FmeUpdateIndividualTargetsInstruction.class, name = "UpdateIndividualTargets"),
      @JsonSubTypes.Type(value = FmeUpdateDynamicConfigurationInstruction.class, name = "UpdateDynamicConfiguration"),
      @JsonSubTypes.Type(value = FmeSetTargetingRulesInstruction.class, name = "SetTargetingRules"),
      @JsonSubTypes.Type(value = FmeSetDefaultAllocationsInstruction.class, name = "SetDefaultAllocations"),
      @JsonSubTypes.Type(value = FmeSetTreatmentsInstruction.class, name = "SetTreatments"),
      @JsonSubTypes.Type(value = FmeSetRolloutStatusInstruction.class, name = "SetRolloutStatus"),
      @JsonSubTypes.Type(value = FmeSetFlagKilledInstruction.class, name = "SetFlagKilled"),
})
@OwnedBy(FME)
public abstract class FmeDefinitionInstruction {
  public abstract FmeInstructionType getType();
}
