/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.provider;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.steps.common.Constants.SLACK_NOTIFY;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;
import static io.harness.steps.plugin.ContainerStepConstants.UTILITY;

import io.harness.beans.FeatureName;
import io.harness.ci.plancreator.AgentStepPlanCreator;
import io.harness.ci.plancreator.GitCloneStepPlanCreator;
import io.harness.ci.plancreator.InitializeStepPlanCreator;
import io.harness.ci.plancreator.PluginStepPlanCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.idp.pipeline.stages.filtercreator.IDPStageFilterCreator;
import io.harness.idp.pipeline.stages.plancreator.IDPLiteEngineStepPlanCreator;
import io.harness.idp.pipeline.stages.plancreator.IDPStagePlanCreator;
import io.harness.idp.pipeline.stages.variablecreator.IDPStageVariableCreator;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.StepSpecTypeConstants;
import io.harness.idp.steps.execution.filter.IDPStepFilterJsonCreator;
import io.harness.idp.steps.execution.plan.IdpCookieCutterStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpCreateCatalogStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpCreateOrganisationStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpCreateProjectStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpCreateRepoStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpCreateResourceStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpDirectPushStepPlanCreator;
import io.harness.idp.steps.execution.plan.IdpRegisterCatalogPlanCreator;
import io.harness.idp.steps.execution.plan.IdpUpdateCatalogPropertyStepPlanCreator;
import io.harness.idp.steps.execution.plan.action.ActionStepPlanCreator;
import io.harness.idp.steps.execution.variable.IDPStepVariableCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.steps.executions.plan.IdpSlackNotifyStepPlanCreator;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class IdpPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  @Inject InjectorUtils injectorUtils;
  private static final String LITE_ENGINE_TASK = "liteEngineTask";

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    // Needs to be modified based on steps
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new IDPStagePlanCreator());
    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new GitCloneStepPlanCreator());
    planCreators.add(new PluginStepPlanCreator());
    planCreators.add(new IDPLiteEngineStepPlanCreator());
    planCreators.add(new IdpCookieCutterStepPlanCreator());
    planCreators.add(new IdpCreateRepoStepPlanCreator());
    planCreators.add(new IdpDirectPushStepPlanCreator());
    planCreators.add(new InitializeStepPlanCreator());
    planCreators.add(new IdpRegisterCatalogPlanCreator());
    planCreators.add(new IdpCreateCatalogStepPlanCreator());
    planCreators.add(new IdpSlackNotifyStepPlanCreator());
    planCreators.add(new IdpCreateOrganisationStepPlanCreator());
    planCreators.add(new IdpCreateProjectStepPlanCreator());
    planCreators.add(new IdpCreateResourceStepPlanCreator());
    planCreators.add(new IdpUpdateCatalogPropertyStepPlanCreator());
    planCreators.add(new ActionStepPlanCreator());
    planCreators.add(new AgentStepPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    // Needs to be modified based on steps
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new IDPStageFilterCreator());
    filterJsonCreators.add(new IDPStepFilterJsonCreator());
    injectorUtils.injectMembers(filterJsonCreators);
    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    // Needs to be modified based on steps
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new IDPStageVariableCreator());
    variableCreators.add(new IDPStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STEP, Set.of(LITE_ENGINE_TASK)));

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    // Needs to be modified based on steps
    StepInfo runStepInfo =
        StepInfo.newBuilder()
            .setName("Run")
            .setType(StepSpecTypeConstants.RUN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Miscellaneous").build())
            .build();

    StepInfo pluginStepInfo =
        StepInfo.newBuilder()
            .setName("Plugin")
            .setType(StepSpecTypeConstants.PLUGIN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Miscellaneous").build())
            .build();

    StepInfo gitCloneStepInfo =
        StepInfo.newBuilder()
            .setName("Git Clone")
            .setType(StepSpecTypeConstants.GIT_CLONE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Miscellaneous").build())
            .build();
    StepInfo cookicutterStepInfo =
        StepInfo.newBuilder()
            .setName("Cookiecutter")
            .setType(Constants.COOKIECUTTER)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Code Generators").build())
            .build();

    StepInfo createRepoStepInfo =
        StepInfo.newBuilder()
            .setName("Create Repo")
            .setType(Constants.CREATE_REPO)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Git Repository Setup").build())
            .build();

    StepInfo codePushStepInfo =
        StepInfo.newBuilder()
            .setName("Direct Push")
            .setType(Constants.DIRECT_PUSH)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Git Repository Setup").build())
            .build();

    StepInfo registerCatalogStepInfo =
        StepInfo.newBuilder()
            .setName("Register Catalog")
            .setType(Constants.REGISTER_CATALOG)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Close the loop").build())
            .build();

    StepInfo createCatalogStepInfo =
        StepInfo.newBuilder()
            .setName("Create Catalog")
            .setType(Constants.CREATE_CATALOG)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Git Repository Setup").build())
            .build();

    StepInfo slackNotifyStepInfo = StepInfo.newBuilder()
                                       .setName("Slack Notify")
                                       .setType(SLACK_NOTIFY)
                                       .setStepMetaData(StepMetaData.newBuilder()
                                                            .addCategory(PLUGIN)
                                                            .addCategory(UTILITY)
                                                            .addFolderPaths("Miscellaneous")
                                                            .build())
                                       .build();

    StepInfo createOrganisationStepInfo =
        StepInfo.newBuilder()
            .setName("Create Organization")
            .setType(Constants.CREATE_ORGANISATION)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Harness Entity Setup").build())
            .build();

    StepInfo createProjectStepInfo =
        StepInfo.newBuilder()
            .setName("Create Project")
            .setType(Constants.CREATE_PROJECT)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Harness Entity Setup").build())
            .build();

    StepInfo createResourceStepInfo =
        StepInfo.newBuilder()
            .setName("Create Resource")
            .setType(Constants.CREATE_RESOURCE)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Harness Entity Setup").build())
            .build();

    StepInfo updateCatalogPropertyStepInfo =
        StepInfo.newBuilder()
            .setName("Update Catalog Property")
            .setType(Constants.UPDATE_CATALOG_PROPERTY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Close the loop").build())
            .build();

    StepInfo actionStepInfo =
        StepInfo.newBuilder()
            .setName("IdpAction")
            .setType(io.harness.steps.StepSpecTypeConstants.IDP_ACTION)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Actions").build())
            .setFeatureFlag(FeatureName.IDP_ENABLE_ACTION_STEP.name())
            .build();

    ArrayList<StepInfo> stepInfos = new ArrayList<>();
    stepInfos.add(runStepInfo);
    stepInfos.add(pluginStepInfo);
    stepInfos.add(gitCloneStepInfo);
    stepInfos.add(cookicutterStepInfo);
    stepInfos.add(createRepoStepInfo);
    stepInfos.add(codePushStepInfo);
    stepInfos.add(registerCatalogStepInfo);
    stepInfos.add(createCatalogStepInfo);
    stepInfos.add(slackNotifyStepInfo);
    stepInfos.add(createOrganisationStepInfo);
    stepInfos.add(createProjectStepInfo);
    stepInfos.add(createResourceStepInfo);
    stepInfos.add(updateCatalogPropertyStepInfo);
    stepInfos.add(actionStepInfo);

    StepInfo agentStepInfo =
        StepInfo.newBuilder()
            .setName("Agent")
            .setType(io.harness.beans.steps.StepSpecTypeConstants.AGENT)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Agents").build())
            .setFeatureFlag(FeatureName.ML_ENABLE_AI_AGENTS.name())
            .build();
    stepInfos.add(agentStepInfo);
    return stepInfos;
  }
}