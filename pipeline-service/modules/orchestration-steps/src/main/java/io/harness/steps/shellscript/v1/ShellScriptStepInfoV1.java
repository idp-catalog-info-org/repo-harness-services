/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static java.util.Objects.isNull;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filters.WithSecretRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@JsonTypeName(StepSpecTypeConstantsV1.SHELL_SCRIPT)
@RecasterAlias("io.harness.steps.shellscript.v1.ShellScriptStepInfoV1")
public class ShellScriptStepInfoV1
    extends ShellScriptBaseStepInfoV1 implements PMSStepInfo, Visitable, WithDelegateSelector, WithSecretRef {
  NGVariableV1Wrapper output_vars;
  NGVariableV1Wrapper env_vars;

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.SHELL_SCRIPT_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegates();
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegates) {
    setDelegates(delegates);
  }

  @Override
  public Map<String, ParameterField<String>> extractSecretRefs() {
    Map<String, ParameterField<String>> secretRefMap = new HashMap<>();
    if (!isNull(execution_target) && !ParameterField.isBlank(execution_target.getConnector())) {
      secretRefMap.put("execution_target.connector", execution_target.getConnector());
    }

    return secretRefMap;
  }
}
