/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.ssca.SscaBeansRegistrar.sscaStepPaletteSteps;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.ci.execution.creator.variables.ActionStepVariableCreator;
import io.harness.ci.execution.creator.variables.BackgroundStepVariableCreator;
import io.harness.ci.execution.creator.variables.GitCloneStepVariableCreator;
import io.harness.ci.execution.creator.variables.PluginStepVariableCreator;
import io.harness.ci.execution.creator.variables.RunStepVariableCreator;
import io.harness.ci.execution.creator.variables.STOStageVariableCreator;
import io.harness.ci.execution.creator.variables.SecurityStepVariableCreator;
import io.harness.ci.execution.plan.creator.filter.STOStageFilterJsonCreator;
import io.harness.ci.execution.plan.creator.stage.SecurityStagePMSPlanCreator;
import io.harness.ci.plancreator.ActionStepPlanCreator;
import io.harness.ci.plancreator.AgentStepPlanCreator;
import io.harness.ci.plancreator.BackgroundStepPlanCreator;
import io.harness.ci.plancreator.GitCloneStepPlanCreator;
import io.harness.ci.plancreator.InitializeStepPlanCreator;
import io.harness.ci.plancreator.PluginStepPlanCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.ssca.execution.creator.filter.SscaSTOStepsFilterJsonCreator;
import io.harness.ssca.execution.creator.plan.DeployAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.EnforceAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SlsaVerificationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaAibomOrchestrationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaArtifactSigningStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaArtifactVerificationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaComplianceStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaEnforcementStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaJunitAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaOrchestrationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaPrAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.variable.SscaSTOStepVariableCreator;
import io.harness.sto.STOStepType;
import io.harness.sto.creator.variables.STOCommonStepVariableCreator;
import io.harness.sto.creator.variables.STOStepVariableCreator;
import io.harness.sto.plan.creator.step.STOStepFilterJsonCreatorV2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Singleton
@OwnedBy(HarnessTeam.STO)
public class STOPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  private static final String LITE_ENGINE_TASK = "liteEngineTask";
  @Inject InjectorUtils injectorUtils;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new SecurityStagePMSPlanCreator());

    planCreators.addAll(STOStepType.getPlanCreators());

    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new ActionStepPlanCreator());
    planCreators.add(new BackgroundStepPlanCreator());
    planCreators.add(new SscaOrchestrationStepPlanCreator());
    planCreators.add(new SscaEnforcementStepPlanCreator());
    planCreators.add(new SscaComplianceStepPlanCreator());
    planCreators.add(new SscaArtifactSigningStepPlanCreator());
    planCreators.add(new SscaArtifactVerificationStepPlanCreator());
    planCreators.add(new SlsaVerificationStepPlanCreator());
    planCreators.add(new SscaPrAttestationStepPlanCreator());
    planCreators.add(new SscaJunitAttestationStepPlanCreator());
    planCreators.add(new SscaAibomOrchestrationStepPlanCreator());
    planCreators.add(new EnforceAttestationStepPlanCreator());
    planCreators.add(new DeployAttestationStepPlanCreator());
    planCreators.add(new PluginStepPlanCreator());
    planCreators.add(new InitializeStepPlanCreator());
    planCreators.add(new GitCloneStepPlanCreator());
    planCreators.add(new AgentStepPlanCreator());

    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new STOStageFilterJsonCreator());
    filterJsonCreators.add(new STOStepFilterJsonCreatorV2());
    filterJsonCreators.add(new SscaSTOStepsFilterJsonCreator());

    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new STOStageVariableCreator());
    variableCreators.add(new STOStepVariableCreator());
    variableCreators.add(new STOCommonStepVariableCreator());
    variableCreators.add(new RunStepVariableCreator());
    variableCreators.add(new BackgroundStepVariableCreator());
    variableCreators.add(new SscaSTOStepVariableCreator());
    variableCreators.add(new SecurityStepVariableCreator());
    variableCreators.add(new PluginStepVariableCreator());
    variableCreators.add(new ActionStepVariableCreator());
    variableCreators.add(new GitCloneStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STEP, Set.of(LITE_ENGINE_TASK)));

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo runStepInfo = StepInfo.newBuilder()
                               .setName("Run")
                               .setType(StepSpecTypeConstants.RUN)
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                               .build();
    StepInfo backgroundStepInfo = StepInfo.newBuilder()
                                      .setName("Background")
                                      .setType(StepSpecTypeConstants.BACKGROUND)
                                      .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                      .build();
    StepInfo pluginStepInfo =
        StepInfo.newBuilder()
            .setName("Plugin")
            .setType(StepSpecTypeConstants.PLUGIN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo actionStepInfo = StepInfo.newBuilder()
                                  .setName("GitHub Action plugin")
                                  .setType(StepSpecTypeConstants.ACTION)
                                  .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                  .build();

    StepInfo gitCloneStepInfo =
        StepInfo.newBuilder()
            .setName("Git Clone")
            .setType(StepSpecTypeConstants.GIT_CLONE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(runStepInfo);
    stepInfos.add(backgroundStepInfo);
    stepInfos.add(actionStepInfo);
    stepInfos.add(pluginStepInfo);
    stepInfos.add(gitCloneStepInfo);
    stepInfos.addAll(sscaStepPaletteSteps);

    stepInfos.addAll(STOStepType.getStepInfos());

    StepInfo agentStepInfo =
        StepInfo.newBuilder()
            .setName("Agent")
            .setType(StepSpecTypeConstants.AGENT)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Agents").build())
            .setFeatureFlag(FeatureName.ML_ENABLE_AI_AGENTS.name())
            .build();
    stepInfos.add(agentStepInfo);

    return stepInfos;
  }
}
