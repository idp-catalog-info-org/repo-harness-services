/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import static io.harness.hsa.HsaBeansRegistrar.hsaStepPaletteSteps;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.ssca.SscaBeansRegistrar.sscaStepPaletteSteps;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.ci.execution.creator.variables.ActionStepVariableCreator;
import io.harness.ci.execution.creator.variables.AiVerifyStepVariableCreator;
import io.harness.ci.execution.creator.variables.ArtifactoryUploadStepVariableCreator;
import io.harness.ci.execution.creator.variables.BackgroundStepVariableCreator;
import io.harness.ci.execution.creator.variables.BuildAndPushACRStepVariableCreator;
import io.harness.ci.execution.creator.variables.BuildAndPushECRStepVariableCreator;
import io.harness.ci.execution.creator.variables.BuildAndPushGARStepVariableCreator;
import io.harness.ci.execution.creator.variables.BuildAndPushGCRStepVariableCreator;
import io.harness.ci.execution.creator.variables.CIStageVariableCreator;
import io.harness.ci.execution.creator.variables.CIStepVariableCreator;
import io.harness.ci.execution.creator.variables.DockerStepVariableCreator;
import io.harness.ci.execution.creator.variables.GCSUploadStepVariableCreator;
import io.harness.ci.execution.creator.variables.GitCloneStepVariableCreator;
import io.harness.ci.execution.creator.variables.HarUploadStepVariableCreator;
import io.harness.ci.execution.creator.variables.PluginStepVariableCreator;
import io.harness.ci.execution.creator.variables.RestoreCacheAzureStepVariableCreator;
import io.harness.ci.execution.creator.variables.RestoreCacheGCSStepVariableCreator;
import io.harness.ci.execution.creator.variables.RestoreCacheS3StepVariableCreator;
import io.harness.ci.execution.creator.variables.RestoreCacheStepVariableCreator;
import io.harness.ci.execution.creator.variables.RunStepVariableCreator;
import io.harness.ci.execution.creator.variables.RunTestStepV2VariableCreator;
import io.harness.ci.execution.creator.variables.RunTestStepVariableCreator;
import io.harness.ci.execution.creator.variables.S3UploadStepVariableCreator;
import io.harness.ci.execution.creator.variables.STOStageVariableCreator;
import io.harness.ci.execution.creator.variables.SaveCacheAzureStepVariableCreator;
import io.harness.ci.execution.creator.variables.SaveCacheGCSStepVariableCreator;
import io.harness.ci.execution.creator.variables.SaveCacheS3StepVariableCreator;
import io.harness.ci.execution.creator.variables.SaveCacheStepVariableCreator;
import io.harness.ci.execution.creator.variables.SecurityStepVariableCreator;
import io.harness.ci.execution.plan.creator.filter.CIStageFilterJsonCreatorV2;
import io.harness.ci.execution.plan.creator.filter.STOStageFilterJsonCreator;
import io.harness.ci.execution.plan.creator.filter.UnifiedStageFilterCreator;
import io.harness.ci.execution.plan.creator.stage.IntegrationStagePMSPlanCreatorV2;
import io.harness.ci.execution.plan.creator.stage.SecurityStagePMSPlanCreator;
import io.harness.ci.execution.plan.creator.stage.V3.RollbackStepsPMSPlanCreator;
import io.harness.ci.execution.plan.creator.stage.V3.UnifiedStagePMSPlanCreator;
import io.harness.ci.execution.plan.creator.step.CIPMSStepFilterJsonCreator;
import io.harness.ci.execution.plan.creator.step.CIPMSStepPlanCreator;
import io.harness.ci.execution.plan.creator.step.CIStepFilterJsonCreatorV2;
import io.harness.ci.execution.plan.creator.steps.CIStepsPlanCreator;
import io.harness.ci.execution.plancreator.V1.ActionStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.BackgroundStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.BitriseStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.GitClonePlanCreator;
import io.harness.ci.execution.plancreator.V1.PluginStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.RunStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.RunTestsStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.TestStepPlanCreator;
import io.harness.ci.plancreator.ActionStepPlanCreator;
import io.harness.ci.plancreator.AgentStepPlanCreator;
import io.harness.ci.plancreator.AiEvalStepPlanCreator;
import io.harness.ci.plancreator.AiTestAutomationCIStepPlanCreator;
import io.harness.ci.plancreator.AiVerifyStepPlanCreator;
import io.harness.ci.plancreator.ArtifactoryUploadStepPlanCreator;
import io.harness.ci.plancreator.BackgroundStepPlanCreator;
import io.harness.ci.plancreator.BitriseStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushACRStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushECRStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushGARStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushGCRStepPlanCreator;
import io.harness.ci.plancreator.DockerStepPlanCreator;
import io.harness.ci.plancreator.GCSUploadStepPlanCreator;
import io.harness.ci.plancreator.GitCloneStepPlanCreator;
import io.harness.ci.plancreator.HarUploadStepPlanCreator;
import io.harness.ci.plancreator.InitializeStepPlanCreator;
import io.harness.ci.plancreator.PluginStepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheAzureStepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheGCSStepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheS3StepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheStepPlanCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.ci.plancreator.RunTestStepPlanCreator;
import io.harness.ci.plancreator.RunTestStepV2PlanCreator;
import io.harness.ci.plancreator.S3UploadStepPlanCreator;
import io.harness.ci.plancreator.SaveCacheAzureStepPlanCreator;
import io.harness.ci.plancreator.SaveCacheGCSStepPlanCreator;
import io.harness.ci.plancreator.SaveCacheS3StepPlanCreator;
import io.harness.ci.plancreator.SaveCacheStepPlanCreator;
import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.filters.EmptyFilterJsonCreator;
import io.harness.hsa.execution.creator.filter.HsaStepsFilterJsonCreator;
import io.harness.hsa.execution.creator.plan.HsaRepositoryScanStepPlanCreator;
import io.harness.hsa.execution.creator.variable.HsaStepVariableCreator;
import io.harness.plancreator.group.GroupPlanCreatorV1;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.ssca.execution.creator.filter.SscaStepsFilterJsonCreator;
import io.harness.ssca.execution.creator.plan.DeployAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.EnforceAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.ProvenanceStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SlsaVerificationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaAibomOrchestrationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaArtifactSigningStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaArtifactVerificationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaComplianceStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaEnforcementStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaJunitAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaOrchestrationStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaPrAttestationStepPlanCreator;
import io.harness.ssca.execution.creator.variable.SscaStepVariableCreator;
import io.harness.sto.STOStepType;
import io.harness.sto.creator.variables.STOCommonStepVariableCreator;
import io.harness.sto.plan.creator.step.STOStepFilterJsonCreatorV2;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.CI)
public class CIPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  private static final String LITE_ENGINE_TASK = "liteEngineTask";

  @Inject InjectorUtils injectorUtils;
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new IntegrationStagePMSPlanCreatorV2());
    planCreators.add(new SecurityStagePMSPlanCreator());
    planCreators.add(new CIPMSStepPlanCreator());
    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new AiVerifyStepPlanCreator());
    planCreators.add(new AiTestAutomationCIStepPlanCreator());
    planCreators.add(new AgentStepPlanCreator());
    planCreators.add(new AiEvalStepPlanCreator());
    planCreators.add(new BackgroundStepPlanCreator());
    planCreators.add(new RunTestStepPlanCreator());
    planCreators.add(new S3UploadStepPlanCreator());
    planCreators.add(new SaveCacheGCSStepPlanCreator());
    planCreators.add(new GCSUploadStepPlanCreator());
    planCreators.add(new RestoreCacheGCSStepPlanCreator());
    planCreators.add(new RestoreCacheS3StepPlanCreator());
    planCreators.add(new RestoreCacheAzureStepPlanCreator());
    planCreators.add(new RestoreCacheStepPlanCreator());
    planCreators.add(new PluginStepPlanCreator());
    planCreators.add(new DockerStepPlanCreator());
    planCreators.add(new ArtifactoryUploadStepPlanCreator());
    planCreators.add(new HarUploadStepPlanCreator());
    planCreators.add(new BuildAndPushECRStepPlanCreator());
    planCreators.add(new BuildAndPushGARStepPlanCreator());
    planCreators.add(new BuildAndPushACRStepPlanCreator());
    planCreators.add(new BuildAndPushGCRStepPlanCreator());
    planCreators.add(new SaveCacheS3StepPlanCreator());
    planCreators.add(new SaveCacheAzureStepPlanCreator());
    planCreators.add(new SaveCacheStepPlanCreator());
    planCreators.add(new GitCloneStepPlanCreator());
    planCreators.add(new GitClonePlanCreator());
    planCreators.add(new InitializeStepPlanCreator());
    planCreators.add(new ActionStepPlanCreator());
    planCreators.add(new BitriseStepPlanCreator());
    planCreators.add(new HsaRepositoryScanStepPlanCreator());
    planCreators.add(new SscaOrchestrationStepPlanCreator());
    planCreators.add(new SscaEnforcementStepPlanCreator());
    planCreators.add(new ProvenanceStepPlanCreator());
    planCreators.add(new SlsaVerificationStepPlanCreator());
    planCreators.add(new SscaComplianceStepPlanCreator());
    planCreators.add(new SscaArtifactSigningStepPlanCreator());
    planCreators.add(new SscaArtifactVerificationStepPlanCreator());
    planCreators.add(new SscaPrAttestationStepPlanCreator());
    planCreators.add(new SscaJunitAttestationStepPlanCreator());
    planCreators.add(new SscaAibomOrchestrationStepPlanCreator());
    planCreators.add(new EnforceAttestationStepPlanCreator());
    planCreators.add(new DeployAttestationStepPlanCreator());
    planCreators.add(new RunTestStepV2PlanCreator());

    // add V1 plan creators
    planCreators.add(new UnifiedStagePMSPlanCreator());
    planCreators.add(new RollbackStepsPMSPlanCreator());
    planCreators.add(new CIStepsPlanCreator());
    planCreators.add(new RunStepPlanCreatorV1());
    planCreators.add(new RunTestsStepPlanCreatorV1());
    planCreators.add(new PluginStepPlanCreatorV1());
    planCreators.add(new TestStepPlanCreator());
    planCreators.add(new BackgroundStepPlanCreatorV1());
    planCreators.add(new BitriseStepPlanCreatorV1());
    planCreators.add(new ActionStepPlanCreatorV1());
    planCreators.add(new GroupPlanCreatorV1());
    // Add STO Steps plan creators
    planCreators.addAll(STOStepType.getPlanCreators());

    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new CIPMSStepFilterJsonCreator());
    filterJsonCreators.add(new UnifiedStageFilterCreator());
    filterJsonCreators.add(new CIStepFilterJsonCreatorV2());
    filterJsonCreators.add(new CIStageFilterJsonCreatorV2());
    filterJsonCreators.add(new STOStepFilterJsonCreatorV2());
    filterJsonCreators.add(new HsaStepsFilterJsonCreator());
    filterJsonCreators.add(new SscaStepsFilterJsonCreator());
    filterJsonCreators.add(new STOStageFilterJsonCreator());
    filterJsonCreators.add(new EmptyFilterJsonCreator(GROUP, ImmutableSet.of(GROUP)));
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(Set.of(GROUP, YAMLFieldNameConstants.ROLLBACK_STEPS_V1)));

    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new CIStageVariableCreator());
    variableCreators.add(new STOStageVariableCreator());
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new CIStepVariableCreator());
    variableCreators.add(new RunStepVariableCreator());
    variableCreators.add(new AiVerifyStepVariableCreator());
    variableCreators.add(new BackgroundStepVariableCreator());
    variableCreators.add(new RunTestStepVariableCreator());
    variableCreators.add(new S3UploadStepVariableCreator());
    variableCreators.add(new SaveCacheGCSStepVariableCreator());
    variableCreators.add(new GCSUploadStepVariableCreator());
    variableCreators.add(new RestoreCacheGCSStepVariableCreator());
    variableCreators.add(new RestoreCacheS3StepVariableCreator());
    variableCreators.add(new RestoreCacheAzureStepVariableCreator());
    variableCreators.add(new RestoreCacheStepVariableCreator());
    variableCreators.add(new PluginStepVariableCreator());
    variableCreators.add(new DockerStepVariableCreator());
    variableCreators.add(new ArtifactoryUploadStepVariableCreator());
    variableCreators.add(new HarUploadStepVariableCreator());
    variableCreators.add(new BuildAndPushECRStepVariableCreator());
    variableCreators.add(new BuildAndPushACRStepVariableCreator());
    variableCreators.add(new BuildAndPushGCRStepVariableCreator());
    variableCreators.add(new BuildAndPushGARStepVariableCreator());
    variableCreators.add(new SaveCacheS3StepVariableCreator());
    variableCreators.add(new SaveCacheAzureStepVariableCreator());
    variableCreators.add(new SaveCacheStepVariableCreator());
    variableCreators.add(new SecurityStepVariableCreator());
    variableCreators.add(new GitCloneStepVariableCreator());
    variableCreators.add(new ActionStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STEP, Set.of(LITE_ENGINE_TASK)));
    variableCreators.add(new EmptyVariableCreator(GROUP, Set.of(GROUP)));
    variableCreators.add(new EmptyAnyVariableCreator(ImmutableSet.of(GROUP, YAMLFieldNameConstants.ROLLBACK_STEPS_V1)));
    variableCreators.add(new HsaStepVariableCreator());
    variableCreators.add(new SscaStepVariableCreator());
    variableCreators.add(new STOCommonStepVariableCreator());
    variableCreators.add(new RunTestStepV2VariableCreator());

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo runStepInfo =
        StepInfo.newBuilder()
            .setName("Run")
            .setType(StepSpecTypeConstants.RUN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo aiVerifyStepInfo =
        StepInfo.newBuilder()
            .setName("AI Verify")
            .setType(StepSpecTypeConstants.AI_VERIFY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .setFeatureFlag(FeatureName.CDS_AI_VERIFY_DEMO.name())
            .build();

    StepInfo backgroundStepInfo =
        StepInfo.newBuilder()
            .setName("Background")
            .setType(StepSpecTypeConstants.BACKGROUND)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo pluginStepInfo =
        StepInfo.newBuilder()
            .setName("Plugin")
            .setType(StepSpecTypeConstants.PLUGIN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo gitCloneStepInfo =
        StepInfo.newBuilder()
            .setName("Git Clone")
            .setType(StepSpecTypeConstants.GIT_CLONE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo restoreCacheFromGCS = StepInfo.newBuilder()
                                       .setName("Restore Cache From GCS")
                                       .setType(StepSpecTypeConstants.RESTORE_CACHE_GCS)
                                       .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                       .build();

    StepInfo restoreCacheFromS3 = StepInfo.newBuilder()
                                      .setName("Restore Cache From S3")
                                      .setType(StepSpecTypeConstants.RESTORE_CACHE_S3)
                                      .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                      .build();

    StepInfo saveCacheToS3 = StepInfo.newBuilder()
                                 .setName("Save Cache to S3")
                                 .setType(StepSpecTypeConstants.SAVE_CACHE_S3)
                                 .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                 .build();

    StepInfo saveCacheToGCS = StepInfo.newBuilder()
                                  .setName("Save Cache to GCS")
                                  .setType(StepSpecTypeConstants.SAVE_CACHE_GCS)
                                  .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                  .build();

    StepInfo saveCache = StepInfo.newBuilder()
                             .setName("Save Cache")
                             .setType(StepSpecTypeConstants.SAVE_CACHE)
                             .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                             .setFeatureFlag(FeatureName.CI_ENABLE_GENERIC_CACHE_STEPS.name())
                             .build();

    StepInfo restoreCache = StepInfo.newBuilder()
                                .setName("Restore Cache")
                                .setType(StepSpecTypeConstants.RESTORE_CACHE)
                                .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                .setFeatureFlag(FeatureName.CI_ENABLE_GENERIC_CACHE_STEPS.name())
                                .build();

    StepInfo actionStepInfo = StepInfo.newBuilder()
                                  .setName("GitHub Action plugin")
                                  .setType(StepSpecTypeConstants.ACTION)
                                  .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                  .build();

    StepInfo bitriseStepInfo = StepInfo.newBuilder()
                                   .setName("Bitrise plugin")
                                   .setType(StepSpecTypeConstants.BITRISE)
                                   .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                   .build();

    StepInfo ecrPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to ECR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_ECR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo acrPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to ACR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_ACR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo uploadArtifactsToJfrogBuild =
        StepInfo.newBuilder()
            .setName("Upload Artifacts to JFrog Artifactory")
            .setType(StepSpecTypeConstants.ARTIFACTORY_UPLOAD)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo uploadArtifactsToHarBuild =
        StepInfo.newBuilder()
            .setName("Upload Artifacts to HAR")
            .setType(StepSpecTypeConstants.HAR_UPLOAD)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo dockerPushBuild =
        StepInfo.newBuilder()
            .setName("Build and Push an image to Docker Registry")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_DOCKER_REGISTRY)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo garPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to GAR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_GAR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo uploadToGCS = StepInfo.newBuilder()
                               .setName("Upload Artifacts to GCS")
                               .setType(StepSpecTypeConstants.GCS_UPLOAD)
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").build())
                               .build();

    StepInfo uploadToS3 =
        StepInfo.newBuilder()
            .setName("Upload Artifacts to S3")
            .setType(StepSpecTypeConstants.S3_UPLOAD)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Artifacts").build())
            .build();

    StepInfo runTestStepV2Info = StepInfo.newBuilder()
                                     .setName("Test Intelligence")
                                     .setType(StepSpecTypeConstants.RUN_TEST_V2)
                                     .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                     .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(runStepInfo);
    stepInfos.add(aiVerifyStepInfo);
    stepInfos.add(runTestStepV2Info);
    stepInfos.add(backgroundStepInfo);
    stepInfos.add(uploadToGCS);
    stepInfos.add(ecrPushBuilds);
    stepInfos.add(uploadToS3);
    stepInfos.add(garPushBuilds);
    stepInfos.add(acrPushBuilds);
    stepInfos.add(restoreCacheFromGCS);
    stepInfos.add(pluginStepInfo);
    stepInfos.add(restoreCacheFromS3);
    stepInfos.add(dockerPushBuild);
    stepInfos.add(uploadArtifactsToJfrogBuild);
    stepInfos.add(uploadArtifactsToHarBuild);
    stepInfos.add(saveCacheToGCS);
    stepInfos.add(gitCloneStepInfo);
    stepInfos.add(saveCacheToS3);
    stepInfos.add(actionStepInfo);
    stepInfos.add(bitriseStepInfo);
    stepInfos.add(saveCache);
    stepInfos.add(restoreCache);

    StepInfo aiTestAutomationStepInfo =
        StepInfo.newBuilder()
            .setName("AI Test Automation")
            .setType(StepSpecTypeConstants.AI_TEST_AUTOMATION)
            .setFeatureFlag(FeatureName.ATA_EMBEDDED.name())
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("AI Test Automation").addFolderPaths("Build").build())
            .build();
    stepInfos.add(aiTestAutomationStepInfo);

    StepInfo agentStepInfo =
        StepInfo.newBuilder()
            .setName("Agent")
            .setType(StepSpecTypeConstants.AGENT)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Agents").build())
            .setFeatureFlag(FeatureName.ML_ENABLE_AI_AGENTS.name())
            .build();
    stepInfos.add(agentStepInfo);

    StepInfo aiEvalStepInfo = StepInfo.newBuilder()
                                  .setName("AI Evals")
                                  .setType(StepSpecTypeConstants.AI_EVAL)
                                  .setFeatureFlag(FeatureName.AI_ENABLE_EVAL_STEP.name())
                                  .setStepMetaData(StepMetaData.newBuilder()
                                                       .addCategory("AI Evals")
                                                       .addCategory(PLUGIN)
                                                       .addFolderPaths("AI Evals")
                                                       .build())
                                  .build();
    stepInfos.add(aiEvalStepInfo);

    stepInfos.addAll(STOStepType.getStepInfos());

    stepInfos.addAll(hsaStepPaletteSteps);
    stepInfos.addAll(sscaStepPaletteSteps);

    return stepInfos;
  }
}
