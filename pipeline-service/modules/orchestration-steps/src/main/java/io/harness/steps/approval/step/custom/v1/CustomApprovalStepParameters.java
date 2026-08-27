/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.custom.CustomApprovalSpecParameters;
import io.harness.steps.approval.step.jira.beans.v1.CriteriaSpecWrapper;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.steps.shellscript.v1.HarnessFileStoreSourceV1;
import io.harness.steps.shellscript.v1.ShellScriptBaseSourceV1;
import io.harness.steps.shellscript.v1.ShellScriptInlineSourceV1;
import io.harness.steps.shellscript.v1.ShellScriptSourceWrapperV1;
import io.harness.steps.shellscript.v1.ShellTypeV1;
import io.harness.yaml.core.timeout.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder(builderMethodName = "infoBuilder")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Slf4j
@RecasterAlias("io.harness.steps.approval.step.custom.v1.CustomApprovalStepParameters")
public class CustomApprovalStepParameters implements SpecParameters {
  CriteriaSpecWrapper approval_criteria;
  CriteriaSpecWrapper rejection_criteria;
  ParameterField<Timeout> timeout;
  ParameterField<Timeout> retry;
  ShellTypeV1 shell;
  ShellScriptSourceWrapperV1 source;
  Map<String, Object> output_vars;
  Map<String, Object> env_vars;
  ParameterField<List<TaskSelectorYaml>> delegates;
  Set<String> secret_output_vars;

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }

  public CustomApprovalSpecParameters toCustomApprovalStepParameterV0() {
    return CustomApprovalSpecParameters.builder()
        .approvalCriteria(toCriteria(getApproval_criteria()))
        .rejectionCriteria(toCriteria(getRejection_criteria()))
        .outputVariables(getOutput_vars())
        .secretOutputVariables(getSecret_output_vars())
        .environmentVariables(getEnv_vars())
        .delegateSelectors(getDelegates())
        .retryInterval(getRetry())
        .scriptTimeout(getTimeout())
        .shellType(toShellType(getShell()))
        .source(toShellScriptSourceWrapper(getSource()))
        .build();
  }

  private ShellType toShellType(ShellTypeV1 shellTypeV1) {
    switch (shellTypeV1) {
      case Bash:
        return ShellType.Bash;
      case PowerShell:
        return ShellType.PowerShell;
      default:
        log.error("Shell type {} not supported", shellTypeV1);
        return null;
    }
  }

  private ShellScriptSourceWrapper toShellScriptSourceWrapper(ShellScriptSourceWrapperV1 shellScriptSourceWrapperV1) {
    if (shellScriptSourceWrapperV1 == null) {
      return null;
    }
    return ShellScriptSourceWrapper.builder()
        .spec(toShellScriptBaseSource(shellScriptSourceWrapperV1.getSpec()))
        .type(toShellScriptSourceWrapperType(shellScriptSourceWrapperV1.getType()))
        .build();
  }

  private String toShellScriptSourceWrapperType(String type) {
    if (ShellScriptBaseSourceV1.HARNESS.equals(type)) {
      return ShellScriptBaseSource.HARNESS;
    }
    return ShellScriptBaseSource.INLINE;
  }

  private ShellScriptBaseSource toShellScriptBaseSource(ShellScriptBaseSourceV1 shellScriptBaseSourceV1) {
    if (shellScriptBaseSourceV1 instanceof HarnessFileStoreSourceV1) {
      return HarnessFileStoreSource.builder()
          .file(((HarnessFileStoreSourceV1) shellScriptBaseSourceV1).getFile())
          .build();
    }
    return ShellScriptInlineSource.builder()
        .script(((ShellScriptInlineSourceV1) shellScriptBaseSourceV1).getScript())
        .build();
  }

  private io.harness.steps.approval.step.beans.CriteriaSpecWrapper toCriteria(CriteriaSpecWrapper criteria) {
    if (criteria == null) {
      return null;
    }
    return io.harness.steps.approval.step.beans.CriteriaSpecWrapper.builder()
        .criteriaSpec(JexlCriteriaSpec.builder().expression(criteria.getExpression()).build())
        .build();
  }
}
