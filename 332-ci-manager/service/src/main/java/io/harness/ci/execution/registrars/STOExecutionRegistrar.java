/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.registrars;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.states.ActionStep;
import io.harness.ci.execution.states.BackgroundStep;
import io.harness.ci.execution.states.CleanupStep;
import io.harness.ci.execution.states.GitCloneStep;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.PluginStep;
import io.harness.ci.execution.states.RunStep;
import io.harness.ci.execution.states.STOSpecStep;
import io.harness.ci.execution.states.SecurityStageStepPMS;
import io.harness.ci.execution.states.SecurityStep;
import io.harness.ci.execution.states.ssca.DeployAttestationStep;
import io.harness.ci.execution.states.ssca.EnforceAttestationStep;
import io.harness.ci.execution.states.ssca.SlsaVerificationStep;
import io.harness.ci.execution.states.ssca.SscaAibomOrchestrationStep;
import io.harness.ci.execution.states.ssca.SscaArtifactSigningStep;
import io.harness.ci.execution.states.ssca.SscaArtifactVerificationStep;
import io.harness.ci.execution.states.ssca.SscaComplianceStep;
import io.harness.ci.execution.states.ssca.SscaEnforcementStep;
import io.harness.ci.execution.states.ssca.SscaJunitAttestationStep;
import io.harness.ci.execution.states.ssca.SscaOrchestrationStep;
import io.harness.ci.execution.states.ssca.SscaPrAttestationStep;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.sto.STOStepType;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.STO)
public class STOExecutionRegistrar {
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(ActionStep.STEP_TYPE, ActionStep.class);
    engineSteps.put(GitCloneStep.STEP_TYPE, GitCloneStep.class);
    engineSteps.put(io.harness.beans.steps.stepinfo.AgentStepInfo.STEP_TYPE, PluginStep.class);
    engineSteps.put(BackgroundStep.STEP_TYPE, BackgroundStep.class);
    engineSteps.put(SscaOrchestrationStep.STEP_TYPE, SscaOrchestrationStep.class);
    engineSteps.put(SscaEnforcementStep.STEP_TYPE, SscaEnforcementStep.class);
    engineSteps.put(SscaComplianceStep.STEP_TYPE, SscaComplianceStep.class);
    engineSteps.put(SscaArtifactSigningStep.STEP_TYPE, SscaArtifactSigningStep.class);
    engineSteps.put(SscaArtifactVerificationStep.STEP_TYPE, SscaArtifactVerificationStep.class);
    engineSteps.put(SlsaVerificationStep.STEP_TYPE, SlsaVerificationStep.class);
    engineSteps.put(SscaPrAttestationStep.STEP_TYPE, SscaPrAttestationStep.class);
    engineSteps.put(SscaJunitAttestationStep.STEP_TYPE, SscaJunitAttestationStep.class);
    engineSteps.put(SscaAibomOrchestrationStep.STEP_TYPE, SscaAibomOrchestrationStep.class);
    engineSteps.put(EnforceAttestationStep.STEP_TYPE, EnforceAttestationStep.class);
    engineSteps.put(DeployAttestationStep.STEP_TYPE, DeployAttestationStep.class);
    engineSteps.putAll(STOStepType.addSTOEngineSteps(SecurityStep.class));
    engineSteps.put(STOSpecStep.STEP_TYPE, STOSpecStep.class);
    engineSteps.put(SecurityStageStepPMS.STEP_TYPE, SecurityStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
