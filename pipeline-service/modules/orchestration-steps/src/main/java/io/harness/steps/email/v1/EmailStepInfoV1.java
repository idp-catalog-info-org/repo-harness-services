/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.walktree.visitor.helper.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.EMAIL)
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.email.v1.EmailStepInfoV1")
public class EmailStepInfoV1 implements PMSStepInfo, Visitable {
  ParameterField<String> to;
  ParameterField<String> cc;
  ParameterField<String> subject;
  ParameterField<String> body;
  ParameterField<List<TaskSelectorYaml>> delegates;
  Boolean fireAndForget;

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.EMAIL_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }
}
