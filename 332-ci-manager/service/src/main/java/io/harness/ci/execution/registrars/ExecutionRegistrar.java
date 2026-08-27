/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.registrars;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.AgentStepInfo;
import io.harness.beans.steps.stepinfo.AiEvalStepInfo;
import io.harness.beans.steps.stepinfo.GARStepInfo;
import io.harness.ci.execution.aitestautomation.AiTestAutomationCIStep;
import io.harness.ci.execution.states.ACRStep;
import io.harness.ci.execution.states.ActionStep;
import io.harness.ci.execution.states.AiVerifyStep;
import io.harness.ci.execution.states.BackgroundStep;
import io.harness.ci.execution.states.BitriseStep;
import io.harness.ci.execution.states.CISpecStep;
import io.harness.ci.execution.states.CleanupStep;
import io.harness.ci.execution.states.DockerStep;
import io.harness.ci.execution.states.ECRStep;
import io.harness.ci.execution.states.GARStep;
import io.harness.ci.execution.states.GCRStep;
import io.harness.ci.execution.states.GitCloneStep;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.execution.states.PluginStep;
import io.harness.ci.execution.states.RestoreCacheAzureStep;
import io.harness.ci.execution.states.RestoreCacheGCSStep;
import io.harness.ci.execution.states.RestoreCacheS3Step;
import io.harness.ci.execution.states.RestoreCacheStep;
import io.harness.ci.execution.states.RunStep;
import io.harness.ci.execution.states.RunTestStepV2;
import io.harness.ci.execution.states.RunTestsStep;
import io.harness.ci.execution.states.STOSpecStep;
import io.harness.ci.execution.states.SaveCacheAzureStep;
import io.harness.ci.execution.states.SaveCacheGCSStep;
import io.harness.ci.execution.states.SaveCacheS3Step;
import io.harness.ci.execution.states.SaveCacheStep;
import io.harness.ci.execution.states.SecurityStageStepPMS;
import io.harness.ci.execution.states.SecurityStep;
import io.harness.ci.execution.states.UploadToArtifactoryStep;
import io.harness.ci.execution.states.UploadToGCSStep;
import io.harness.ci.execution.states.UploadToHarStep;
import io.harness.ci.execution.states.UploadToS3Step;
import io.harness.ci.execution.states.ssca.DeployAttestationStep;
import io.harness.ci.execution.states.ssca.EnforceAttestationStep;
import io.harness.ci.execution.states.ssca.ProvenanceStep;
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
import io.harness.ci.states.V1.cd.ArtifactsStep;
import io.harness.ci.states.V1.cd.ConfigFilesStep;
import io.harness.ci.states.V1.cd.ManifestsStep;
import io.harness.ci.states.V1.cd.RenderingStep;
import io.harness.ci.states.V1.cd.ServiceHooksStep;
import io.harness.ci.states.V1.cd.TemplatingStep;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStep;
import io.harness.ci.states.V1.cd.UnifiedMultiDeploymentSpawnerStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.ci.states.V1.iacm.UnifiedIACMPrepareStep;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.steps.group.GroupStepV1;
import io.harness.steps.rollback.CombinedRollbackStep;
import io.harness.steps.rollback.InfrastructureDefinitionStep;
import io.harness.steps.rollback.InfrastructureProvisionerStep;
import io.harness.steps.rollback.RollbackOptionalChildChainStep;
import io.harness.steps.rollback.StepGroupRollbackChainStep;
import io.harness.sto.STOStepType;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CI)
public class ExecutionRegistrar {
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    //    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStep.class);
    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.put(AgentStepInfo.STEP_TYPE, PluginStep.class);
    engineSteps.put(AiEvalStepInfo.STEP_TYPE, PluginStep.class);
    engineSteps.put(AiVerifyStep.STEP_TYPE, AiVerifyStep.class);
    engineSteps.put(AiTestAutomationCIStep.STEP_TYPE, AiTestAutomationCIStep.class);
    engineSteps.put(RunTestStepV2.STEP_TYPE, RunTestStepV2.class);
    engineSteps.put(BackgroundStep.STEP_TYPE, BackgroundStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(GitCloneStep.STEP_TYPE, GitCloneStep.class);
    engineSteps.putAll(STOStepType.addSTOEngineSteps(SecurityStep.class));
    engineSteps.put(ECRStep.STEP_TYPE, ECRStep.class);
    engineSteps.put(GCRStep.STEP_TYPE, GCRStep.class);
    engineSteps.put(GARStepInfo.STEP_TYPE, GARStep.class);
    engineSteps.put(ACRStep.STEP_TYPE, ACRStep.class);
    engineSteps.put(DockerStep.STEP_TYPE, DockerStep.class);
    engineSteps.put(UploadToS3Step.STEP_TYPE, UploadToS3Step.class);
    engineSteps.put(SaveCacheS3Step.STEP_TYPE, SaveCacheS3Step.class);
    engineSteps.put(RestoreCacheS3Step.STEP_TYPE, RestoreCacheS3Step.class);
    engineSteps.put(SaveCacheAzureStep.STEP_TYPE, SaveCacheAzureStep.class);
    engineSteps.put(RestoreCacheAzureStep.STEP_TYPE, RestoreCacheAzureStep.class);
    engineSteps.put(UploadToGCSStep.STEP_TYPE, UploadToGCSStep.class);
    engineSteps.put(SaveCacheGCSStep.STEP_TYPE, SaveCacheGCSStep.class);
    engineSteps.put(RestoreCacheGCSStep.STEP_TYPE, RestoreCacheGCSStep.class);
    engineSteps.put(SaveCacheStep.STEP_TYPE, SaveCacheStep.class);
    engineSteps.put(RestoreCacheStep.STEP_TYPE, RestoreCacheStep.class);
    engineSteps.put(UploadToArtifactoryStep.STEP_TYPE, UploadToArtifactoryStep.class);
    engineSteps.put(UploadToHarStep.STEP_TYPE, UploadToHarStep.class);
    engineSteps.put(RunTestsStep.STEP_TYPE, RunTestsStep.class);
    engineSteps.put(IntegrationStageStepPMS.STEP_TYPE, IntegrationStageStepPMS.class);
    engineSteps.put(STOSpecStep.STEP_TYPE, STOSpecStep.class);
    engineSteps.put(SecurityStageStepPMS.STEP_TYPE, SecurityStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.put(ActionStep.STEP_TYPE, ActionStep.class);
    engineSteps.put(BitriseStep.STEP_TYPE, BitriseStep.class);
    engineSteps.put(CISpecStep.STEP_TYPE, CISpecStep.class);
    engineSteps.put(SscaOrchestrationStep.STEP_TYPE, SscaOrchestrationStep.class);
    engineSteps.put(SscaEnforcementStep.STEP_TYPE, SscaEnforcementStep.class);
    engineSteps.put(ProvenanceStep.STEP_TYPE, ProvenanceStep.class);
    engineSteps.put(SlsaVerificationStep.STEP_TYPE, SlsaVerificationStep.class);
    engineSteps.put(SscaComplianceStep.STEP_TYPE, SscaComplianceStep.class);
    engineSteps.put(SscaArtifactSigningStep.STEP_TYPE, SscaArtifactSigningStep.class);
    engineSteps.put(SscaArtifactVerificationStep.STEP_TYPE, SscaArtifactVerificationStep.class);
    engineSteps.put(SscaPrAttestationStep.STEP_TYPE, SscaPrAttestationStep.class);
    engineSteps.put(SscaJunitAttestationStep.STEP_TYPE, SscaJunitAttestationStep.class);
    engineSteps.put(SscaAibomOrchestrationStep.STEP_TYPE, SscaAibomOrchestrationStep.class);
    engineSteps.put(EnforceAttestationStep.STEP_TYPE, EnforceAttestationStep.class);
    engineSteps.put(DeployAttestationStep.STEP_TYPE, DeployAttestationStep.class);
    engineSteps.put(GroupStepV1.STEP_TYPE, GroupStepV1.class);
    engineSteps.put(CombinedRollbackStep.STEP_TYPE, CombinedRollbackStep.class);
    engineSteps.put(RollbackOptionalChildChainStep.STEP_TYPE, RollbackOptionalChildChainStep.class);
    engineSteps.put(StepGroupRollbackChainStep.STEP_TYPE, StepGroupRollbackChainStep.class);
    engineSteps.put(InfrastructureProvisionerStep.STEP_TYPE, InfrastructureProvisionerStep.class);
    engineSteps.put(InfrastructureDefinitionStep.STEP_TYPE, InfrastructureDefinitionStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    engineSteps.put(UnifiedServiceStep.STEP_TYPE, UnifiedServiceStep.class);
    engineSteps.put(UnifiedMultiDeploymentSpawnerStep.STEP_TYPE, UnifiedMultiDeploymentSpawnerStep.class);
    engineSteps.put(ManifestsStep.STEP_TYPE, ManifestsStep.class);
    engineSteps.put(UnifiedCDInfraStep.STEP_TYPE, UnifiedCDInfraStep.class);
    engineSteps.put(ArtifactsStep.STEP_TYPE, ArtifactsStep.class);
    engineSteps.put(RenderingStep.STEP_TYPE, RenderingStep.class);
    engineSteps.put(TemplatingStep.STEP_TYPE, TemplatingStep.class);
    engineSteps.put(ConfigFilesStep.STEP_TYPE, ConfigFilesStep.class);
    engineSteps.put(ServiceHooksStep.STEP_TYPE, ServiceHooksStep.class);
    engineSteps.put(UnifiedIACMPrepareStep.STEP_TYPE, UnifiedIACMPrepareStep.class);
    return engineSteps;
  }
}
