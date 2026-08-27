/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;

import io.harness.beans.FeatureName;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.ProvenanceBuilderData;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.ACRStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.serializer.ArtifactUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.execution.provenance.ProvenanceStepGenerator;

import com.google.inject.Inject;
import java.util.Map;

public class ACRStep extends AbstractImagePushStep {
  public static final StepType STEP_TYPE = ACRStepInfo.STEP_TYPE;

  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject ProvenanceGenerator provenanceGenerator;

  @Override
  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    return getStepArtifacts(artifactMetadata, stepParameters, ambiance, StageInfraDetails.Type.VM);
  }

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    return getStepArtifacts(artifactMetadata, stepParameters, ambiance, StageInfraDetails.Type.K8);
  }

  private StepArtifacts getStepArtifacts(ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters,
      Ambiance ambiance, StageInfraDetails.Type infraType) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    populateArtifact(artifactMetadata, stepParameters, stepArtifactsBuilder);
    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      populateProvenanceInStepOutcome(ambiance, stepArtifactsBuilder, stepParameters, infraType);
    }
    StepArtifacts stepArtifacts = stepArtifactsBuilder.build();
    saveArtifactDetails(stepArtifacts, artifactMetadata, ambiance);
    return stepArtifacts;
  }

  private void populateProvenanceInStepOutcome(Ambiance ambiance, StepArtifactsBuilder stepArtifactsBuilder,
      StepBaseParameters stepParameters, StageInfraDetails.Type infraType) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)
        || !ProvenanceStepGenerator.getAllowedTypesForProvenance().contains(CIStepInfoType.ACR)) {
      return;
    }
    ACRStepInfo acrStepInfo = (ACRStepInfo) stepParameters.getSpec();
    BuildMetadata buildMetadata = getBuildMetadata(acrStepInfo, stepParameters.getIdentifier());

    String image;
    if (infraType == StageInfraDetails.Type.K8) {
      image = ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage();
    } else {
      image = ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId);
    }

    ProvenanceBuilderData provenanceBuilder =
        ProvenanceBuilderData.builder()
            .accountId(accountId)
            .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .pipelineExecutionId(AmbianceUtils.getPipelineExecutionIdentifier(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .startTime(ambiance.getStartTs())
            .pluginInfo(image)
            .buildMetadata(buildMetadata)
            .build();

    ProvenancePredicate predicate = provenanceGenerator.buildProvenancePredicate(provenanceBuilder, ambiance);
    stepArtifactsBuilder.provenanceArtifact(
        ProvenanceArtifact.builder().predicateType(SscaConstants.PREDICATE_TYPE).predicate(predicate).build());
  }

  private void populateArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, StepArtifactsBuilder stepArtifactsBuilder) {
    String identifier = stepParameters.getIdentifier();
    ACRStepInfo acrStepInfo = (ACRStepInfo) stepParameters.getSpec();
    String repository =
        resolveStringParameter("repository", "BuildAndPushACR", identifier, acrStepInfo.getRepository(), true);
    String subscriptionId =
        resolveStringParameter("subscriptionId", "BuildAndPushACR", identifier, acrStepInfo.getSubscriptionId(), false);
    ArtifactUtils.populateArtifactForACRStep(repository, subscriptionId, artifactMetadata, stepArtifactsBuilder);
  }

  private BuildMetadata getBuildMetadata(ACRStepInfo acrStepInfo, String identifier) {
    String repo =
        resolveStringParameter("repository", "BuildAndPushACR", identifier, acrStepInfo.getRepository(), true);
    String dockerFile =
        resolveStringParameter("dockerfile", "BuildAndPushACR", identifier, acrStepInfo.getDockerfile(), false);
    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushACR", identifier, acrStepInfo.getBuildArgs(), false);
    String context = resolveStringParameter("context", "BuildAndPushACR", identifier, acrStepInfo.getContext(), false);
    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushACR", identifier, acrStepInfo.getLabels(), false);

    return BuildMetadata.builder()
        .image(repo)
        .dockerFile(dockerFile)
        .buildArgs(buildArgs)
        .context(context)
        .labels(labels)
        .build();
  }
}
