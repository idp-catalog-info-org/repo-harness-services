/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.StepSpecTypeConstants.INIT_CONTAINER_V2_STEP_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.execution.strategy.identity.IdentityStep;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyInternalStep;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyStep;
import io.harness.plancreator.steps.pluginstep.InitContainerV2Step;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.execution.CdSscaEnforcementStep;
import io.harness.ssca.execution.CdSscaOrchestrationStep;
import io.harness.steps.StagesStep;
import io.harness.steps.StagesStepWithChildrenSupport;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.aisre.AisreCreateAlertStep;
import io.harness.steps.aisre.AisreCreateIncidentStep;
import io.harness.steps.approval.stage.ApprovalStageStep;
import io.harness.steps.approval.step.custom.CustomApprovalStep;
import io.harness.steps.approval.step.harness.step.HarnessApprovalStep;
import io.harness.steps.approval.step.jira.step.JiraApprovalStep;
import io.harness.steps.approval.step.servicenow.step.ServiceNowApprovalStep;
import io.harness.steps.barriers.BarrierStep;
import io.harness.steps.cf.FeatureFlagStageStep;
import io.harness.steps.cf.FlagConfigurationStep;
import io.harness.steps.changeadvisor.ChangeAdvisorStep;
import io.harness.steps.changeadvisor.ChangeAdvisorV1Step;
import io.harness.steps.common.pipeline.PipelineSetupStep;
import io.harness.steps.container.InitContainerStep;
import io.harness.steps.container.execution.RunContainerStep;
import io.harness.steps.email.EmailStep;
import io.harness.steps.eventlistener.EventlistenerStep;
import io.harness.steps.fme.FmeFlagAddRemoveTargetsStep;
import io.harness.steps.fme.FmeFlagArchiveStep;
import io.harness.steps.fme.FmeFlagCreate;
import io.harness.steps.fme.FmeFlagDefaultAllocationStep;
import io.harness.steps.fme.FmeFlagDefinitionInstructionsStep;
import io.harness.steps.fme.FmeFlagDeleteStep;
import io.harness.steps.fme.FmeFlagKillStep;
import io.harness.steps.fme.FmeFlagLimitExposureStep;
import io.harness.steps.fme.FmeFlagPatchDefinitionStep;
import io.harness.steps.fme.FmeFlagReallocateTrafficStep;
import io.harness.steps.fme.FmeFlagRestoreStep;
import io.harness.steps.fme.FmeFlagSetDynamicConfigurationsStep;
import io.harness.steps.fme.FmeFlagSetImpressionTrackingStep;
import io.harness.steps.fme.FmeFlagSetTargetingRulesStep;
import io.harness.steps.fme.FmeFlagSetTargets;
import io.harness.steps.fme.FmeFlagSetTreatmentsStep;
import io.harness.steps.fme.FmeFlagUpdate;
import io.harness.steps.fme.FmeFlagsetStepRegistrar;
import io.harness.steps.fme.FmeSegmentAddRemoveTargetsStep;
import io.harness.steps.fme.FmeSegmentCreate;
import io.harness.steps.fme.FmeSegmentDeleteStep;
import io.harness.steps.fme.FmeSegmentSetTargetingRulesStep;
import io.harness.steps.fme.FmeSegmentUpdateStep;
import io.harness.steps.group.GroupStepV1;
import io.harness.steps.http.v1.step.HttpStep;
import io.harness.steps.jira.create.JiraCreateStep;
import io.harness.steps.jira.update.JiraUpdateStep;
import io.harness.steps.opa.step.OPAEvaluationAggregatorStep;
import io.harness.steps.opa.step.OPAEvaluationStep;
import io.harness.steps.policy.step.PolicyStep;
import io.harness.steps.resourcerestraint.QueueStep;
import io.harness.steps.resourcerestraint.ResourceRestraintStep;
import io.harness.steps.ro.RONotifyStep;
import io.harness.steps.servicenow.create.ServiceNowCreateStep;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStep;
import io.harness.steps.servicenow.update.ServiceNowUpdateStep;
import io.harness.steps.shellscript.v1.step.ShellScriptStep;
import io.harness.steps.upload.FilesUploadStep;
import io.harness.steps.wait.WaitStep;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(PIPELINE)
@UtilityClass
public class OrchestrationStepsModuleStepRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(BarrierStep.STEP_TYPE, BarrierStep.class);
    engineSteps.put(ResourceRestraintStep.STEP_TYPE, ResourceRestraintStep.class);
    engineSteps.put(QueueStep.STEP_TYPE, QueueStep.class);
    engineSteps.put(PipelineSetupStep.STEP_TYPE, PipelineSetupStep.class);

    engineSteps.put(ApprovalStageStep.STEP_TYPE, ApprovalStageStep.class);
    engineSteps.put(io.harness.steps.approval.stage.v1.ApprovalStageStep.STEP_TYPE,
        io.harness.steps.approval.stage.v1.ApprovalStageStep.class);
    engineSteps.put(HarnessApprovalStep.STEP_TYPE, HarnessApprovalStep.class);
    engineSteps.put(io.harness.steps.approval.step.harness.v1.step.HarnessApprovalStep.STEP_TYPE,
        io.harness.steps.approval.step.harness.v1.step.HarnessApprovalStep.class);
    engineSteps.put(CustomApprovalStep.STEP_TYPE, CustomApprovalStep.class);
    engineSteps.put(io.harness.steps.approval.step.custom.v1.step.CustomApprovalStep.STEP_TYPE,
        io.harness.steps.approval.step.custom.v1.step.CustomApprovalStep.class);
    engineSteps.put(JiraApprovalStep.STEP_TYPE, JiraApprovalStep.class);
    engineSteps.put(io.harness.steps.approval.step.jira.v1.JiraApprovalStep.STEP_TYPE,
        io.harness.steps.approval.step.jira.v1.JiraApprovalStep.class);
    engineSteps.put(JiraCreateStep.STEP_TYPE, JiraCreateStep.class);
    engineSteps.put(JiraUpdateStep.STEP_TYPE, JiraUpdateStep.class);

    engineSteps.put(io.harness.steps.http.HttpStep.STEP_TYPE, io.harness.steps.http.HttpStep.class);
    engineSteps.put(HttpStep.STEP_TYPE, HttpStep.class);
    engineSteps.put(EmailStep.STEP_TYPE, EmailStep.class);
    engineSteps.put(io.harness.steps.email.v1.step.EmailStep.STEP_TYPE, io.harness.steps.email.v1.step.EmailStep.class);
    engineSteps.put(RONotifyStep.STEP_TYPE, RONotifyStep.class);
    engineSteps.put(
        io.harness.steps.shellscript.ShellScriptStep.STEP_TYPE, io.harness.steps.shellscript.ShellScriptStep.class);
    engineSteps.put(ShellScriptStep.STEP_TYPE, ShellScriptStep.class);
    engineSteps.put(ServiceNowApprovalStep.STEP_TYPE, ServiceNowApprovalStep.class);
    engineSteps.put(io.harness.steps.approval.step.servicenow.v1.step.ServiceNowApprovalStep.STEP_TYPE,
        io.harness.steps.approval.step.servicenow.v1.step.ServiceNowApprovalStep.class);
    engineSteps.put(ServiceNowCreateStep.STEP_TYPE, ServiceNowCreateStep.class);
    engineSteps.put(ServiceNowUpdateStep.STEP_TYPE, ServiceNowUpdateStep.class);
    engineSteps.put(ServiceNowImportSetStep.STEP_TYPE, ServiceNowImportSetStep.class);
    engineSteps.put(StagesStep.STEP_TYPE, StagesStep.class);
    engineSteps.put(StagesStep.DEPRECATED_STEP_TYPE, StagesStep.class);
    // Feature Flag
    engineSteps.put(FlagConfigurationStep.STEP_TYPE, FlagConfigurationStep.class);
    engineSteps.put(FeatureFlagStageStep.STEP_TYPE, FeatureFlagStageStep.class);

    // FME FF
    engineSteps.put(AisreCreateIncidentStep.STEP_TYPE, AisreCreateIncidentStep.class);
    engineSteps.put(AisreCreateAlertStep.STEP_TYPE, AisreCreateAlertStep.class);
    engineSteps.put(FmeFlagCreate.STEP_TYPE, FmeFlagCreate.class);
    engineSteps.put(FmeSegmentCreate.STEP_TYPE, FmeSegmentCreate.class);
    engineSteps.put(FmeFlagDefaultAllocationStep.STEP_TYPE, FmeFlagDefaultAllocationStep.class);
    engineSteps.put(FmeFlagSetTargets.STEP_TYPE, FmeFlagSetTargets.class);
    engineSteps.put(FmeFlagAddRemoveTargetsStep.STEP_TYPE, FmeFlagAddRemoveTargetsStep.class);
    engineSteps.put(FmeFlagKillStep.STEP_TYPE, FmeFlagKillStep.class);
    engineSteps.put(FmeFlagRestoreStep.STEP_TYPE, FmeFlagRestoreStep.class);
    engineSteps.put(FmeFlagUpdate.STEP_TYPE, FmeFlagUpdate.class);
    engineSteps.put(FmeFlagArchiveStep.STEP_TYPE, FmeFlagArchiveStep.class);
    engineSteps.put(FmeFlagDeleteStep.STEP_TYPE, FmeFlagDeleteStep.class);
    engineSteps.put(FmeFlagLimitExposureStep.STEP_TYPE, FmeFlagLimitExposureStep.class);
    engineSteps.put(FmeFlagPatchDefinitionStep.STEP_TYPE, FmeFlagPatchDefinitionStep.class);
    engineSteps.put(FmeFlagDefinitionInstructionsStep.STEP_TYPE, FmeFlagDefinitionInstructionsStep.class);
    engineSteps.put(FmeFlagReallocateTrafficStep.STEP_TYPE, FmeFlagReallocateTrafficStep.class);
    engineSteps.put(FmeFlagSetDynamicConfigurationsStep.STEP_TYPE, FmeFlagSetDynamicConfigurationsStep.class);
    engineSteps.put(FmeFlagSetTargetingRulesStep.STEP_TYPE, FmeFlagSetTargetingRulesStep.class);
    engineSteps.put(FmeFlagSetTreatmentsStep.STEP_TYPE, FmeFlagSetTreatmentsStep.class);
    engineSteps.put(FmeSegmentUpdateStep.STEP_TYPE, FmeSegmentUpdateStep.class);
    engineSteps.put(FmeSegmentDeleteStep.STEP_TYPE, FmeSegmentDeleteStep.class);
    engineSteps.put(FmeSegmentAddRemoveTargetsStep.STEP_TYPE, FmeSegmentAddRemoveTargetsStep.class);
    engineSteps.put(FmeSegmentSetTargetingRulesStep.STEP_TYPE, FmeSegmentSetTargetingRulesStep.class);
    engineSteps.put(FmeFlagSetImpressionTrackingStep.STEP_TYPE, FmeFlagSetImpressionTrackingStep.class);

    // FME Flagset steps — registered via dedicated helper to reduce merge conflicts
    engineSteps.putAll(FmeFlagsetStepRegistrar.getEngineSteps());

    engineSteps.put(PolicyStep.STEP_TYPE, PolicyStep.class);
    engineSteps.put(ChangeAdvisorStep.STEP_TYPE, ChangeAdvisorStep.class);
    engineSteps.put(StepSpecTypeConstantsV1.CHANGE_ADVISOR_STEP_TYPE, ChangeAdvisorV1Step.class);
    // IdentityStep
    engineSteps.put(IdentityStep.STEP_TYPE, IdentityStep.class);
    engineSteps.put(IdentityStrategyStep.STEP_TYPE, IdentityStrategyStep.class);
    engineSteps.put(IdentityStrategyInternalStep.STEP_TYPE, IdentityStrategyInternalStep.class);

    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    engineSteps.put(WaitStep.STEP_TYPE, WaitStep.class);
    engineSteps.put(GroupStepV1.STEP_TYPE, GroupStepV1.class);
    engineSteps.put(GroupStepV1.GROUP_STAGE_TYPE, GroupStepV1.class);
    engineSteps.put(InitContainerStep.STEP_TYPE, InitContainerStep.class);
    engineSteps.put(RunContainerStep.STEP_TYPE, RunContainerStep.class);
    engineSteps.put(SscaConstants.CD_SSCA_ORCHESTRATION_STEP_TYPE, CdSscaOrchestrationStep.class);
    engineSteps.put(INIT_CONTAINER_V2_STEP_TYPE, InitContainerV2Step.class);
    engineSteps.put(CdSscaEnforcementStep.STEP_TYPE, CdSscaEnforcementStep.class);
    engineSteps.put(FilesUploadStep.STEP_TYPE, FilesUploadStep.class);
    engineSteps.put(EventlistenerStep.STEP_TYPE, EventlistenerStep.class);

    // OPA Evaluation Step - Internal step for auto-injected OPA policy evaluations
    engineSteps.put(OPAEvaluationStep.STEP_TYPE, OPAEvaluationStep.class);
    engineSteps.put(OPAEvaluationAggregatorStep.STEP_TYPE, OPAEvaluationAggregatorStep.class);

    engineSteps.put(StagesStepWithChildrenSupport.STEP_TYPE, StagesStepWithChildrenSupport.class);

    return engineSteps;
  }
}
