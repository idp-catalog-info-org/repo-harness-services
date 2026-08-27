/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.pipeline.CfExecutionPMSPlanCreator;
import io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator;
import io.harness.cf.pipeline.FeatureFlagStagePlanCreator;
import io.harness.filters.DynamicFilterJsonCreator;
import io.harness.filters.GroupFilterJsonCreator;
import io.harness.filters.InjectPmsFilterJsonCreator;
import io.harness.filters.ParallelFilterJsonCreator;
import io.harness.filters.PipelineFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStageFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStagePlanCreatorV2;
import io.harness.plancreator.approval.v1.ApprovalStageFilterCreator;
import io.harness.plancreator.approval.v1.ApprovalStagePlanCreator;
import io.harness.plancreator.group.GroupPlanCreatorV1;
import io.harness.plancreator.inject.creator.InjectPlanCreator;
import io.harness.plancreator.pipeline.NGPipelinePlanCreator;
import io.harness.plancreator.stages.StagesPlanCreator;
import io.harness.plancreator.stages.dynamic.DynamicStagePlanCreator;
import io.harness.plancreator.stages.dynamic.v1.DynamicStageFilterCreatorV1;
import io.harness.plancreator.stages.dynamic.v1.DynamicStagePlanCreatorV1;
import io.harness.plancreator.steps.barrier.BarrierStepPlanCreator;
import io.harness.plancreator.steps.http.HTTPStepVariableCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepPlanCreator;
import io.harness.plancreator.steps.internal.ChangeAdvisorStepVariableCreator;
import io.harness.plancreator.steps.internal.FlagConfigurationStepPlanCreator;
import io.harness.plancreator.steps.internal.HarnessApprovalStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.PMSStepPlanCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.ShellScriptStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.opa.OPAEvaluationAggregatorStepPlanCreator;
import io.harness.plancreator.steps.opa.OPAEvaluationStepPlanCreator;
import io.harness.plancreator.steps.pluginstep.ContainerStepVariableCreator;
import io.harness.plancreator.steps.resourceconstraint.QueueStepPlanCreator;
import io.harness.plancreator.steps.resourceconstraint.ResourceConstraintStepPlanCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.pipelinerollback.PipelineRollbackStagePlanCreator;
import io.harness.pms.pipelinestage.creator.PipelineStageFilterCreator;
import io.harness.pms.pipelinestage.plancreator.PipelineStagePlanCreator;
import io.harness.pms.pipelinestage.unified.UnifiedPipelineStagePlanCreator;
import io.harness.pms.pipelinestage.v1.plancreator.PipelineStagePlanCreatorV1;
import io.harness.pms.sdk.PmsSdkInitValidator;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.ApprovalStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.DynamicStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.InjectVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.PipelineVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoDecoratorImpl;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.rule.Owner;
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
import io.harness.steps.eventlistener.EventListenerStepFilterJsonCreator;
import io.harness.steps.eventlistener.EventlistenerStepPlanCreator;
import io.harness.steps.eventlistener.EventlistenerStepVariableCreator;
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
import io.harness.steps.upload.FilesUploadStepFilterJsonCreator;
import io.harness.steps.upload.FilesUploadStepPlanCreator;
import io.harness.steps.upload.FilesUploadStepVariableCreator;
import io.harness.steps.upload.unified.UnifiedFilesUploadStepPlanCreator;
import io.harness.steps.wait.WaitStepPlanCreator;
import io.harness.steps.wait.WaitStepVariableCreator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineServiceInternalInfoProviderTest extends CategoryTest {
  public static final int PLAN_CREATOR_NUMBER = 90;
  public static final int FILTER_JSON_CREATOR_NUMBER = 48;
  public static final int VARIABLE_CREATOR_NUMBER = 33;

  @InjectMocks PipelineServiceInternalInfoProvider pipelineServiceInternalInfoProvider;
  @InjectMocks PipelineServiceInfoDecoratorImpl serviceInfoDecorator;
  @Mock InjectorUtils injectorUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorFilterAndVariable() {
    Reflect.on(serviceInfoDecorator).set("pipelineServiceInfoProvider", pipelineServiceInternalInfoProvider);
    PmsSdkInitValidator.validatePlanCreators(serviceInfoDecorator);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPlanCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends PartialPlanCreator>> planCreatorClasses =
        pipelineServiceInternalInfoProvider.getPlanCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(planCreatorClasses).hasSize(PLAN_CREATOR_NUMBER);
    assertThat(planCreatorClasses.contains(CustomApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(NGPipelinePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(StagesPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PMSStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ApprovalStagePlanCreatorV2.class)).isTrue();
    assertThat(planCreatorClasses.contains(ResourceConstraintStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(FeatureFlagStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(CfExecutionPMSPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraUpdateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraCreateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(HarnessApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(BarrierStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(FlagConfigurationStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PolicyStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ChangeAdvisorStepPlanCreator.class)).isTrue();
    assertThat(
        planCreatorClasses.contains(io.harness.plancreator.steps.changeadvisor.v1.ChangeAdvisorStepPlanCreator.class))
        .isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowCreateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowUpdateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowImportSetStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(QueueStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(WaitStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PipelineStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PipelineStagePlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedPipelineStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(GroupPlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(PMSStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(CdSscaOrchestrationStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PipelineRollbackStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(CdSscaEnforcementStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ApprovalStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(io.harness.steps.approval.step.jira.v1.JiraApprovalStepPlanCreator.class))
        .isTrue();
    assertThat(
        planCreatorClasses.contains(io.harness.steps.approval.step.harness.v1.HarnessApprovalStepPlanCreator.class))
        .isTrue();
    assertThat(planCreatorClasses.contains(ResourceConstraintStepPlanCreator.class)).isTrue();
    assertThat(
        planCreatorClasses.contains(io.harness.steps.approval.step.custom.v1.CustomApprovalStepPlanCreator.class))
        .isTrue();
    assertThat(planCreatorClasses.contains(
                   io.harness.steps.approval.step.servicenow.v1.ServiceNowApprovalStepPlanCreator.class))
        .isTrue();
    assertThat(planCreatorClasses.contains(InjectPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(FilesUploadStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(EventlistenerStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedResourceConstraintStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedManualApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedCustomApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedServiceNowApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedJiraApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedQueueStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedPolicyStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(UnifiedFilesUploadStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(DynamicStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(DynamicStagePlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(OPAEvaluationStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(OPAEvaluationAggregatorStepPlanCreator.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFilterJsonCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends FilterJsonCreator>> filterCreatorClasses =
        pipelineServiceInternalInfoProvider.getFilterJsonCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(filterCreatorClasses).hasSize(FILTER_JSON_CREATOR_NUMBER);
    assertThat(filterCreatorClasses.contains(ShellScriptStepFilterJsonCreatorV2.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PipelineFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ParallelFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ApprovalStageFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PmsStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ChangeAdvisorStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(FeatureFlagStageFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PipelineStageFilterCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(GroupFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(HarnessApprovalStepFilterJsonCreatorV2.class)).isTrue();
    assertThat(filterCreatorClasses.contains(CdSscaStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ApprovalStageFilterCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(InjectPmsFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(FilesUploadStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(EventListenerStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(DynamicFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(DynamicStageFilterCreatorV1.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetVariableCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends VariableCreator>> variableCreatorClasses =
        pipelineServiceInternalInfoProvider.getVariableCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(variableCreatorClasses).hasSize(VARIABLE_CREATOR_NUMBER);
    assertThat(variableCreatorClasses.contains(CustomApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PipelineVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(HTTPStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(StepGroupVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ShellScriptStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ApprovalStageVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PolicyStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ChangeAdvisorStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraUpdateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowCreateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowUpdateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowImportSetStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(QueueStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PipelineStageVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PipelineStageOutputsVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ContainerStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(WaitStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(BarrierStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(CdSscaStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(InjectVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(FilesUploadStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(EventlistenerStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(DynamicStageVariableCreator.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStepInfo() {
    List<StepInfo> steps = pipelineServiceInternalInfoProvider.getStepInfo();
    assertThat(steps).isNotEmpty();
    assertThat(steps)
        .hasSize(2)
        .extracting(StepInfo::getName)
        .containsExactly("Flag Configuration", "Harness Approval");
  }
}
