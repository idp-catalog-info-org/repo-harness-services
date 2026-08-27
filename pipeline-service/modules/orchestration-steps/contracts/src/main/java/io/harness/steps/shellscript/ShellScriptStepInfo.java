/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.NGExpressionUtils;
import io.harness.filters.WithSecretRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.plancreator.steps.internal.StepParametersUtils;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.walktree.visitor.helper.SimpleVisitorHelper;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.SHELL_SCRIPT)
@SimpleVisitorHelper(helperClass = ShellScriptStepInfoVisitorHelper.class)
@TypeAlias("shellScriptStepInfo")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.cdng.pipeline.stepinfo.ShellScriptStepInfo")
public class ShellScriptStepInfo
    extends ShellScriptBaseStepInfoV0 implements PMSStepInfo, Visitable, WithDelegateSelector, WithSecretRef {
  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;
  List<NGVariable> environmentVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptStepInfo(ShellType shell, ShellScriptSourceWrapper source,
      ParameterField<ExecutionTarget> executionTarget, ParameterField<Boolean> onDelegate,
      List<NGVariable> outputVariables, List<NGVariable> environmentVariables,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String uuid,
      ParameterField<Boolean> includeInfraSelectors, OutputAlias outputAlias) {
    super(uuid, shell, source, executionTarget, onDelegate, delegateSelectors, includeInfraSelectors, outputAlias);
    this.outputVariables = outputVariables;
    this.environmentVariables = environmentVariables;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.SHELL_SCRIPT_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ShellScriptStepParametersV0.infoBuilder()
        .executionTarget(getExecutionTarget())
        .onDelegate(getOnDelegate())
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(outputVariables))
        .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables))
        .secretOutputVariables(NGVariablesUtils.getSetOfSecretVars(outputVariables))
        .shellType(getShell())
        .source(getSource())
        .delegateSelectors(getDelegateSelectors())
        .includeInfraSelectors(getIncludeInfraSelectors())
        .outputAlias(getOutputAlias())
        .build();
  }

  @Override
  public StepParameters getStepParameters(
      PmsAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        StepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<String>> extractSecretRefs() {
    Map<String, ParameterField<String>> secretRefMap = new HashMap<>();
    if (ParameterField.isNotNull(executionTarget)
        && !NGExpressionUtils.matchesInputSetPattern(executionTarget.getExpressionValue())
        && !ParameterField.isBlank(executionTarget.getValue().getConnectorRef())) {
      secretRefMap.put("executionTarget.connectorRef", executionTarget.getValue().getConnectorRef());
    }

    return secretRefMap;
  }
}
