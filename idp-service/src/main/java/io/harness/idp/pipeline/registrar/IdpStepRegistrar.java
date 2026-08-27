/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.registrar;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.AgentStepInfo;
import io.harness.ci.execution.states.BackgroundStep;
import io.harness.ci.execution.states.CleanupStep;
import io.harness.ci.execution.states.GitCloneStep;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.PluginStep;
import io.harness.ci.execution.states.RunStep;
import io.harness.ci.execution.states.RunTestsStep;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.idp.pipeline.stages.step.IDPStageStepPMS;
import io.harness.idp.pipeline.steps.ActionStep;
import io.harness.idp.pipeline.steps.IdpCookieCutterStep;
import io.harness.idp.pipeline.steps.IdpCreateCatalogStep;
import io.harness.idp.pipeline.steps.IdpCreateOrganisationStep;
import io.harness.idp.pipeline.steps.IdpCreateProjectStep;
import io.harness.idp.pipeline.steps.IdpCreateRepoStep;
import io.harness.idp.pipeline.steps.IdpCreateResourceStep;
import io.harness.idp.pipeline.steps.IdpDirectPushStep;
import io.harness.idp.pipeline.steps.IdpRegisterCatalogStep;
import io.harness.idp.pipeline.steps.IdpUpdateCatalogPropertyStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.steps.executions.executable.IdpSlackNotifyStep;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class IdpStepRegistrar {
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(IDPStageStepPMS.STEP_TYPE, IDPStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(GitCloneStep.STEP_TYPE, GitCloneStep.class);
    engineSteps.put(BackgroundStep.STEP_TYPE, BackgroundStep.class);
    engineSteps.put(RunTestsStep.STEP_TYPE, RunTestsStep.class);
    engineSteps.put(ActionStep.STEP_TYPE, ActionStep.class);
    engineSteps.put(IdpCookieCutterStep.STEP_TYPE, IdpCookieCutterStep.class);
    engineSteps.put(IdpCreateRepoStep.STEP_TYPE, IdpCreateRepoStep.class);
    engineSteps.put(IdpDirectPushStep.STEP_TYPE, IdpDirectPushStep.class);
    engineSteps.put(IdpRegisterCatalogStep.STEP_TYPE, IdpRegisterCatalogStep.class);
    engineSteps.put(IdpCreateCatalogStep.STEP_TYPE, IdpCreateCatalogStep.class);
    engineSteps.put(IdpSlackNotifyStep.STEP_TYPE, IdpSlackNotifyStep.class);
    engineSteps.put(IdpCreateOrganisationStep.STEP_TYPE, IdpCreateOrganisationStep.class);
    engineSteps.put(IdpCreateProjectStep.STEP_TYPE, IdpCreateProjectStep.class);
    engineSteps.put(IdpCreateResourceStep.STEP_TYPE, IdpCreateResourceStep.class);
    engineSteps.put(IdpUpdateCatalogPropertyStep.STEP_TYPE, IdpUpdateCatalogPropertyStep.class);
    engineSteps.put(AgentStepInfo.STEP_TYPE, PluginStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}