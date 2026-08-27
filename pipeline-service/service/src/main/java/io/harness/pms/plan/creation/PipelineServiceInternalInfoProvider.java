/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator.FEATURE_FLAG_SUPPORTED_TYPE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.FILES_UPLOAD_V1;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.steps.StepSpecTypeConstants.AISRE_CREATE_ALERT;
import static io.harness.steps.StepSpecTypeConstants.AISRE_CREATE_INCIDENT;
import static io.harness.steps.StepSpecTypeConstants.FLAG_CONFIGURATION;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_ARCHIVE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_CREATE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_DELETE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_KILL_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_REALLOCATE_TRAFFIC_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_RESTORE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_SET_TARGETS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_FLAG_UPDATE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_SEGMENT_CREATE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_SEGMENT_DELETE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.FME_SEGMENT_UPDATE_STEP_TYPE;
import static io.harness.steps.StepSpecTypeConstants.HARNESS_APPROVAL;
import static io.harness.steps.StepSpecTypeConstants.OPA_EVALUATION;
import static io.harness.steps.StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR;
import static io.harness.steps.StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE;
import static io.harness.steps.StepSpecTypeConstants.RESOURCE_CONSTRAINT;
import static io.harness.steps.StepSpecTypeConstants.RO_NOTIFY_STEP_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cf.pipeline.CfExecutionPMSPlanCreator;
import io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator;
import io.harness.cf.pipeline.FeatureFlagStagePlanCreator;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.filters.DynamicFilterJsonCreator;
import io.harness.filters.EmptyFilterJsonCreator;
import io.harness.filters.GroupFilterJsonCreator;
import io.harness.filters.InjectPmsFilterJsonCreator;
import io.harness.filters.ParallelFilterJsonCreator;
import io.harness.filters.PipelineFilterJsonCreator;
import io.harness.plancreator.EmptyVariableCreatorV1;
import io.harness.plancreator.approval.ApprovalStageFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStagePlanCreatorV2;
import io.harness.plancreator.approval.v1.ApprovalStageFilterCreator;
import io.harness.plancreator.approval.v1.ApprovalStagePlanCreator;
import io.harness.plancreator.group.GroupPlanCreatorV1;
import io.harness.plancreator.inject.creator.InjectPlanCreator;
import io.harness.plancreator.pipeline.NGPipelinePlanCreator;
import io.harness.plancreator.pipeline.PipelinePlanCreatorV1;
import io.harness.plancreator.stages.StagesPlanCreator;
import io.harness.plancreator.stages.dynamic.DynamicStagePlanCreator;
import io.harness.plancreator.stages.dynamic.v1.DynamicStageFilterCreatorV1;
import io.harness.plancreator.stages.dynamic.v1.DynamicStagePlanCreatorV1;
import io.harness.plancreator.stages.v1.StagesPlanCreatorV1;
import io.harness.plancreator.steps.StepGroupPMSPlanCreatorV2;
import io.harness.plancreator.steps.barrier.BarrierStepPlanCreator;
import io.harness.plancreator.steps.barrier.unified.UnifiedBarrierStepPlanCreator;
import io.harness.plancreator.steps.email.EmailStepPlanCreator;
import io.harness.plancreator.steps.email.EmailStepVariableCreator;
import io.harness.plancreator.steps.http.HTTPStepVariableCreator;
import io.harness.plancreator.steps.http.v1.HttpStepPlanCreator;
import io.harness.plancreator.steps.internal.AisreCreateAlertStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.AisreCreateAlertStepPlanCreator;
import io.harness.plancreator.steps.internal.AisreCreateIncidentStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.AisreCreateIncidentStepPlanCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepPlanCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepVariableCreator;
import io.harness.plancreator.steps.internal.FlagConfigurationStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveTargetsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagAddRemoveTargetsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagArchiveStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagArchiveStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagCreateFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagCreatePlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagDefaultAllocationStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagDefaultAllocationStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagDefinitionInstructionsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagDefinitionInstructionsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagDeleteStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagDeleteStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagKillStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagKillStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagLimitExposureStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagLimitExposureStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagPatchDefinitionStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagPatchDefinitionStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagPatchDefinitionStepVariableCreator;
import io.harness.plancreator.steps.internal.FmeFlagReallocateTrafficStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagReallocateTrafficStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagRestoreStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagRestoreStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetDynamicConfigurationsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetDynamicConfigurationsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetDynamicConfigurationsStepVariableCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetImpressionTrackingStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetImpressionTrackingStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetingRulesStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetingRulesStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetsFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTargetsPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTreatmentsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagSetTreatmentsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeFlagUpdateFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeFlagUpdatePlanCreator;
import io.harness.plancreator.steps.internal.FmeSegmentAddRemoveTargetsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeSegmentAddRemoveTargetsStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeSegmentCreateFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeSegmentCreatePlanCreator;
import io.harness.plancreator.steps.internal.FmeSegmentDeleteStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeSegmentDeleteStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeSegmentSetTargetingRulesStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeSegmentSetTargetingRulesStepPlanCreator;
import io.harness.plancreator.steps.internal.FmeSegmentUpdateStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.FmeSegmentUpdateStepPlanCreator;
import io.harness.plancreator.steps.internal.HarnessApprovalStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.PMSStepPlanCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.RONotifyStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.RONotifyStepPlanCreator;
import io.harness.plancreator.steps.internal.ShellScriptStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.v1.PmsStepFilterJsonCreatorV3;
import io.harness.plancreator.steps.opa.OPAEvaluationAggregatorStepPlanCreator;
import io.harness.plancreator.steps.opa.OPAEvaluationStepPlanCreator;
import io.harness.plancreator.steps.pluginstep.ContainerStepPlanCreator;
import io.harness.plancreator.steps.pluginstep.ContainerStepVariableCreator;
import io.harness.plancreator.steps.resourceconstraint.QueueStepPlanCreator;
import io.harness.plancreator.steps.resourceconstraint.ResourceConstraintStepPlanCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.pipelinerollback.PipelineRollbackStagePlanCreator;
import io.harness.pms.pipelinestage.creator.PipelineStageFilterCreator;
import io.harness.pms.pipelinestage.plancreator.PipelineStagePlanCreator;
import io.harness.pms.pipelinestage.unified.UnifiedPipelineStagePlanCreator;
import io.harness.pms.pipelinestage.v1.plancreator.PipelineStagePlanCreatorV1;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.ApprovalStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.DynamicStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.InjectVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.PipelineVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.ssca.cd.execution.filtercreator.CdSscaStepFilterJsonCreator;
import io.harness.ssca.cd.execution.variablecreator.CdSscaStepVariableCreator;
import io.harness.ssca.plancreator.CdSscaEnforcementStepPlanCreator;
import io.harness.ssca.plancreator.CdSscaOrchestrationStepPlanCreator;
import io.harness.steps.approval.ApprovalStepVariableCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepPlanCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepVariableCreator;
import io.harness.steps.approval.step.custom.unified.UnifiedCustomApprovalStepPlanCreator;
import io.harness.steps.approval.step.harness.HarnessApprovalStepPlanCreator;
import io.harness.steps.approval.step.harness.unified.UnifiedManualApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepVariableCreator;
import io.harness.steps.approval.step.jira.unified.UnifiedJiraApprovalStepPlanCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepPlanCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepVariableCreator;
import io.harness.steps.approval.step.servicenow.unified.UnifiedServiceNowApprovalStepPlanCreator;
import io.harness.steps.barriers.BarrierStepVariableCreator;
import io.harness.steps.cf.FlagConfigurationStep;
import io.harness.steps.eventlistener.EventListenerStepFilterJsonCreator;
import io.harness.steps.eventlistener.EventlistenerStepPlanCreator;
import io.harness.steps.eventlistener.EventlistenerStepVariableCreator;
import io.harness.steps.fme.FmeFlagsetPlanCreatorRegistrar;
import io.harness.steps.jira.JiraStepVariableCreator;
import io.harness.steps.jira.JiraUpdateStepVariableCreator;
import io.harness.steps.jira.create.JiraCreateStepPlanCreator;
import io.harness.steps.jira.update.JiraUpdateStepPlanCreator;
import io.harness.steps.pipelinestage.PipelineStageOutputsVariableCreator;
import io.harness.steps.pipelinestage.PipelineStageVariableCreator;
import io.harness.steps.policy.step.PolicyStepPlanCreator;
import io.harness.steps.policy.unified.UnifiedPolicyStepPlanCreator;
import io.harness.steps.policy.variables.PolicyStepVariableCreator;
import io.harness.steps.resourcerestraint.QueueStepVariableCreator;
import io.harness.steps.resourcerestraint.unified.UnifiedQueueStepPlanCreator;
import io.harness.steps.resourcerestraint.unified.UnifiedResourceConstraintStepPlanCreator;
import io.harness.steps.servicenow.ServiceNowCreateStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowImportSetStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowUpdateStepVariableCreator;
import io.harness.steps.servicenow.create.ServiceNowCreateStepPlanCreator;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStepPlanCreator;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepPlanCreator;
import io.harness.steps.shellscript.ShellScriptStepVariableCreator;
import io.harness.steps.shellscript.v1.ShellScriptStepPlanCreator;
import io.harness.steps.upload.FilesUploadStepFilterJsonCreator;
import io.harness.steps.upload.FilesUploadStepPlanCreator;
import io.harness.steps.upload.FilesUploadStepVariableCreator;
import io.harness.steps.upload.unified.UnifiedFilesUploadStepPlanCreator;
import io.harness.steps.wait.WaitStepPlanCreator;
import io.harness.steps.wait.WaitStepVariableCreator;
import io.harness.steps.wait.unified.UnifiedWaitStepPlanCreator;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineServiceInternalInfoProvider implements PipelineServiceInfoProvider {
  @Inject InjectorUtils injectorUtils;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(new NGPipelinePlanCreator());
    planCreators.add(new PipelinePlanCreatorV1());
    planCreators.add(new StagesPlanCreator());
    planCreators.add(new StagesPlanCreatorV1());
    planCreators.add(new PMSStepPlanCreator());
    planCreators.add(new io.harness.plancreator.steps.http.HttpStepPlanCreator());
    planCreators.add(new HttpStepPlanCreator());
    planCreators.add(new io.harness.plancreator.steps.email.v1.EmailStepPlanCreator());
    planCreators.add(new io.harness.plancreator.steps.changeadvisor.v1.ChangeAdvisorStepPlanCreator());
    planCreators.add(new UnifiedQueueStepPlanCreator());
    planCreators.add(new EmailStepPlanCreator());
    planCreators.add(new JiraCreateStepPlanCreator());
    planCreators.add(new JiraUpdateStepPlanCreator());
    planCreators.add(new io.harness.steps.shellscript.ShellScriptStepPlanCreator());
    planCreators.add(new ShellScriptStepPlanCreator());
    planCreators.add(new ApprovalStagePlanCreatorV2());
    planCreators.add(new ResourceConstraintStepPlanCreator());
    planCreators.add(new UnifiedResourceConstraintStepPlanCreator());
    planCreators.add(new QueueStepPlanCreator());
    planCreators.add(new FeatureFlagStagePlanCreator());
    planCreators.add(new CfExecutionPMSPlanCreator());
    planCreators.add(new ServiceNowApprovalStepPlanCreator());
    planCreators.add(new io.harness.steps.approval.step.servicenow.v1.ServiceNowApprovalStepPlanCreator());
    planCreators.add(new JiraApprovalStepPlanCreator());
    planCreators.add(new io.harness.steps.approval.step.jira.v1.JiraApprovalStepPlanCreator());
    planCreators.add(new HarnessApprovalStepPlanCreator());
    planCreators.add(new UnifiedManualApprovalStepPlanCreator());
    planCreators.add(new UnifiedCustomApprovalStepPlanCreator());
    planCreators.add(new UnifiedServiceNowApprovalStepPlanCreator());
    planCreators.add(new UnifiedJiraApprovalStepPlanCreator());
    planCreators.add(new io.harness.steps.approval.step.harness.v1.HarnessApprovalStepPlanCreator());
    planCreators.add(new BarrierStepPlanCreator());
    planCreators.add(new UnifiedBarrierStepPlanCreator());
    planCreators.add(new FlagConfigurationStepPlanCreator());
    planCreators.add(new PolicyStepPlanCreator());
    planCreators.add(new ChangeAdvisorStepPlanCreator());
    planCreators.add(new UnifiedPolicyStepPlanCreator());
    planCreators.add(new ServiceNowCreateStepPlanCreator());
    planCreators.add(new ServiceNowUpdateStepPlanCreator());
    planCreators.add(new ServiceNowImportSetStepPlanCreator());
    planCreators.add(new CustomApprovalStepPlanCreator());
    planCreators.add(new io.harness.steps.approval.step.custom.v1.CustomApprovalStepPlanCreator());
    planCreators.add(new WaitStepPlanCreator());
    planCreators.add(new FmeFlagCreatePlanCreator());
    planCreators.add(new FmeSegmentCreatePlanCreator());
    planCreators.add(new FmeFlagUpdatePlanCreator());
    planCreators.add(new FmeFlagDefaultAllocationStepPlanCreator());
    planCreators.add(new FmeFlagSetTargetsPlanCreator());
    planCreators.add(new FmeFlagAddRemoveTargetsStepPlanCreator());
    planCreators.add(new AisreCreateIncidentStepPlanCreator());
    planCreators.add(new AisreCreateAlertStepPlanCreator());
    planCreators.add(new FmeFlagArchiveStepPlanCreator());
    planCreators.add(new FmeFlagDeleteStepPlanCreator());
    planCreators.add(new FmeFlagLimitExposureStepPlanCreator());
    planCreators.add(new FmeFlagPatchDefinitionStepPlanCreator());
    planCreators.add(new FmeFlagDefinitionInstructionsStepPlanCreator());
    planCreators.add(new FmeFlagReallocateTrafficStepPlanCreator());
    planCreators.add(new FmeFlagSetDynamicConfigurationsStepPlanCreator());
    planCreators.add(new FmeFlagSetTargetingRulesStepPlanCreator());
    planCreators.add(new FmeFlagSetTreatmentsStepPlanCreator());
    planCreators.add(new FmeSegmentUpdateStepPlanCreator());
    planCreators.add(new FmeSegmentDeleteStepPlanCreator());
    planCreators.add(new FmeSegmentAddRemoveTargetsStepPlanCreator());
    planCreators.add(new FmeSegmentSetTargetingRulesStepPlanCreator());
    // FME Flagset steps — registered via dedicated helper to reduce merge conflicts
    planCreators.addAll(FmeFlagsetPlanCreatorRegistrar.getPlanCreators());
    planCreators.add(new FmeFlagKillStepPlanCreator());
    planCreators.add(new FmeFlagRestoreStepPlanCreator());
    planCreators.add(new FmeFlagSetImpressionTrackingStepPlanCreator());
    planCreators.add(new RONotifyStepPlanCreator());
    planCreators.add(new UnifiedWaitStepPlanCreator());
    planCreators.add(new UnifiedFilesUploadStepPlanCreator());
    planCreators.add(new DynamicStagePlanCreator());
    planCreators.add(new DynamicStagePlanCreatorV1());
    planCreators.add(new PipelineStagePlanCreator());
    planCreators.add(new PipelineRollbackStagePlanCreator());
    planCreators.add(new PipelineStagePlanCreatorV1());
    planCreators.add(new UnifiedPipelineStagePlanCreator());
    planCreators.add(new ContainerStepPlanCreator());
    planCreators.add(new GroupPlanCreatorV1());
    planCreators.add(new CdSscaOrchestrationStepPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreatorV2());
    planCreators.add(new CdSscaEnforcementStepPlanCreator());
    planCreators.add(new ApprovalStagePlanCreator());
    planCreators.add(new InjectPlanCreator());
    planCreators.add(new FilesUploadStepPlanCreator());
    planCreators.add(new EventlistenerStepPlanCreator());
    // OPA Evaluation step - internal step, auto-injected only, not visible in UI
    // DO NOT add to getStepInfo() - this ensures isPartOfStepPallete=false and step is hidden from manual selection
    planCreators.add(new OPAEvaluationStepPlanCreator());
    planCreators.add(new OPAEvaluationAggregatorStepPlanCreator());

    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new PipelineFilterJsonCreator());
    filterJsonCreators.add(new ParallelFilterJsonCreator());
    filterJsonCreators.add(new ApprovalStageFilterJsonCreator());
    filterJsonCreators.add(new PmsStepFilterJsonCreator());
    filterJsonCreators.add(new PmsStepFilterJsonCreatorV2());
    filterJsonCreators.add(new PmsStepFilterJsonCreatorV3());
    filterJsonCreators.add(new ShellScriptStepFilterJsonCreatorV2());
    filterJsonCreators.add(new ChangeAdvisorStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagCreateFilterJsonCreator());
    filterJsonCreators.add(new FmeSegmentCreateFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagDefaultAllocationStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagSetTargetsFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagAddRemoveTargetsStepFilterJsonCreator());
    filterJsonCreators.add(new AisreCreateIncidentStepFilterJsonCreator());
    filterJsonCreators.add(new AisreCreateAlertStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagArchiveStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagDeleteStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagLimitExposureStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagPatchDefinitionStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagDefinitionInstructionsStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagReallocateTrafficStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagSetDynamicConfigurationsStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagSetTargetingRulesStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagSetTreatmentsStepFilterJsonCreator());
    filterJsonCreators.add(new FmeSegmentUpdateStepFilterJsonCreator());
    filterJsonCreators.add(new FmeSegmentDeleteStepFilterJsonCreator());
    filterJsonCreators.add(new FmeSegmentAddRemoveTargetsStepFilterJsonCreator());
    filterJsonCreators.add(new FmeSegmentSetTargetingRulesStepFilterJsonCreator());
    // FME Flagset steps — registered via dedicated helper to reduce merge conflicts
    filterJsonCreators.addAll(FmeFlagsetPlanCreatorRegistrar.getFilterJsonCreators());
    filterJsonCreators.add(new FmeFlagKillStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagRestoreStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagSetImpressionTrackingStepFilterJsonCreator());
    filterJsonCreators.add(new RONotifyStepFilterJsonCreator());
    filterJsonCreators.add(new FmeFlagUpdateFilterJsonCreator());
    filterJsonCreators.add(new FeatureFlagStageFilterJsonCreator());
    filterJsonCreators.add(new PipelineStageFilterCreator());
    filterJsonCreators.add(new InjectPmsFilterJsonCreator());
    filterJsonCreators.add(new GroupFilterJsonCreator());
    filterJsonCreators.add(new EmptyFilterJsonCreator(
        STEP, ImmutableSet.of(FLAG_CONFIGURATION, FILES_UPLOAD_V1, OPA_EVALUATION, OPA_EVALUATION_AGGREGATOR)));
    filterJsonCreators.add(
        new EmptyFilterJsonCreator(YAMLFieldNameConstants.JOBS, Collections.singleton(PlanCreatorUtils.ANY_TYPE)));
    filterJsonCreators.add(new EmptyFilterJsonCreator(
        STAGE, ImmutableSet.of(PIPELINE_ROLLBACK_STAGE, YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN)));
    filterJsonCreators.add(new EmptyFilterJsonCreator(GROUP, ImmutableSet.of(GROUP)));
    filterJsonCreators.add(new HarnessApprovalStepFilterJsonCreatorV2());
    filterJsonCreators.add(new CdSscaStepFilterJsonCreator());
    filterJsonCreators.add(new ApprovalStageFilterCreator());
    filterJsonCreators.add(new FilesUploadStepFilterJsonCreator());
    filterJsonCreators.add(new EventListenerStepFilterJsonCreator());
    filterJsonCreators.add(new DynamicFilterJsonCreator());
    filterJsonCreators.add(new DynamicStageFilterCreatorV1());

    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new PipelineVariableCreator());
    variableCreators.add(new HTTPStepVariableCreator());
    variableCreators.add(new EmailStepVariableCreator());
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new ShellScriptStepVariableCreator());
    variableCreators.add(new JiraStepVariableCreator());
    variableCreators.add(new ApprovalStepVariableCreator());
    variableCreators.add(new ApprovalStageVariableCreator());
    variableCreators.add(new PolicyStepVariableCreator());
    variableCreators.add(new ChangeAdvisorStepVariableCreator());
    variableCreators.add(new ServiceNowApprovalStepVariableCreator());
    variableCreators.add(new JiraUpdateStepVariableCreator());
    variableCreators.add(new JiraApprovalStepVariableCreator());
    variableCreators.add(new ServiceNowCreateStepVariableCreator());
    variableCreators.add(new ServiceNowUpdateStepVariableCreator());
    variableCreators.add(new ServiceNowImportSetStepVariableCreator());
    variableCreators.add(new QueueStepVariableCreator());
    variableCreators.add(new CustomApprovalStepVariableCreator());
    variableCreators.add(new InjectVariableCreator());
    variableCreators.add(new PipelineStageVariableCreator());
    variableCreators.add(new PipelineStageOutputsVariableCreator());
    variableCreators.add(new WaitStepVariableCreator());
    variableCreators.add(new FmeFlagSetDynamicConfigurationsStepVariableCreator());
    variableCreators.add(new FmeFlagPatchDefinitionStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STAGE,
        ImmutableSet.of(FEATURE_FLAG_SUPPORTED_TYPE, PIPELINE_ROLLBACK_STAGE, YAMLFieldNameConstants.APPROVAL_V1,
            YAMLFieldNameConstants.UNIFIED_PIPELINE_CHAIN, YAMLFieldNameConstants.DYNAMIC_STAGE_V1)));
    variableCreators.add(
        new EmptyVariableCreator(YAMLFieldNameConstants.JOBS, ImmutableSet.of(PlanCreatorUtils.ANY_TYPE)));
    // Build STEP empty variable set: FME steps + other internal step types
    Set<String> emptyVarStepTypes =
        new HashSet<>(ImmutableSet.of(FLAG_CONFIGURATION, FME_FLAG_CREATE_STEP_TYPE.getType(),
            FME_FLAG_UPDATE_STEP_TYPE.getType(), FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE.getType(),
            FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE.getType(), FME_FLAG_ARCHIVE_STEP_TYPE.getType(),
            FME_FLAG_DELETE_STEP_TYPE.getType(), FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE.getType(),
            FME_FLAG_PATCH_DEFINITION_STEP_TYPE.getType(), FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE.getType(),
            FME_FLAG_REALLOCATE_TRAFFIC_STEP_TYPE.getType(), FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE.getType(),
            FME_FLAG_SET_TARGETING_RULES_STEP_TYPE.getType(), FME_FLAG_SET_TARGETS_STEP_TYPE.getType(),
            FME_FLAG_SET_TREATMENTS_STEP_TYPE.getType(), FME_FLAG_KILL_STEP_TYPE.getType(),
            FME_FLAG_RESTORE_STEP_TYPE.getType(), FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE.getType(),
            FME_SEGMENT_CREATE_STEP_TYPE.getType(), FME_SEGMENT_UPDATE_STEP_TYPE.getType(),
            FME_SEGMENT_DELETE_STEP_TYPE.getType(), FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE.getType(),
            FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE.getType(), RESOURCE_CONSTRAINT, FILES_UPLOAD_V1, OPA_EVALUATION,
            OPA_EVALUATION_AGGREGATOR, AISRE_CREATE_ALERT, AISRE_CREATE_INCIDENT));
    emptyVarStepTypes.add(RO_NOTIFY_STEP_TYPE.getType());
    // FME Flagset step types — registered via helper to reduce merge conflicts
    emptyVarStepTypes.addAll(FmeFlagsetPlanCreatorRegistrar.getStepTypeStrings());
    variableCreators.add(new EmptyVariableCreator(STEP, emptyVarStepTypes));
    variableCreators.add(new EmptyVariableCreator(GROUP, ImmutableSet.of(GROUP)));
    variableCreators.add(new EmptyAnyVariableCreator(ImmutableSet.of(GROUP)));
    variableCreators.add(new ContainerStepVariableCreator());
    variableCreators.add(new BarrierStepVariableCreator());
    variableCreators.add(new CdSscaStepVariableCreator());
    variableCreators.add(new EmptyVariableCreatorV1());
    variableCreators.add(new FilesUploadStepVariableCreator());
    variableCreators.add(new EventlistenerStepVariableCreator());
    variableCreators.add(new DynamicStageVariableCreator());

    injectorUtils.injectMembers(variableCreators);
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo k8sRolling = StepInfo.newBuilder()
                              .setName(FlagConfigurationStep.STEP_NAME)
                              .setType(FLAG_CONFIGURATION)
                              .setStepMetaData(StepMetaData.newBuilder()
                                                   .addCategory(FlagConfigurationStep.STEP_CATEGORY)
                                                   .addFolderPaths("Feature Flags")
                                                   .build())
                              .build();

    StepInfo harnessApprovalStepInfo =
        StepInfo.newBuilder()
            .setName("Harness Approval")
            .setType(HARNESS_APPROVAL)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Approval").addFolderPaths("Approval").build())
            .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_HARNESS_UI.name())
            .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(k8sRolling);
    stepInfos.add(harnessApprovalStepInfo);
    return stepInfos;
  }
}
