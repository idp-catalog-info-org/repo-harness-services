/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.ProvenanceBuilderData;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.GARStepInfo;
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
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.ssca.execution.provenance.ProvenanceStepGenerator;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class GARStep extends AbstractImagePushStep {
  public static final StepType STEP_TYPE = GARStepInfo.STEP_TYPE;
  private static final String GAR_URL_FORMAT =
      "https://console.cloud.google.com/artifacts/docker/%s/%s/%s/%s?project=%s";
  private static final String GAR_HOST_REGEX = "^(?<region>[a-zA-Z]+(?:-[a-zA-Z0-9]+)?)\\-docker\\.pkg\\.dev$";
  private static final String GAR_GLOBAL_REGION = "GLOBAL";
  public static final String REGION = "region";

  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject ProvenanceGenerator provenanceGenerator;
  @Inject SSCALicenseHelper sscaLicenseHelper;

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
      try {
        populateProvenanceInStepOutcome(ambiance, stepArtifactsBuilder, stepParameters, infraType);
      } catch (Exception e) {
        log.error("Error occurred while populating provenance in StepOutcome", e);
      }
    }
    StepArtifacts stepArtifacts = stepArtifactsBuilder.build();
    saveArtifactDetails(stepArtifacts, artifactMetadata, ambiance);
    return stepArtifacts;
  }

  private void populateArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, StepArtifactsBuilder stepArtifactsBuilder) {
    if (artifactMetadata == null) {
      return;
    }

    String identifier = stepParameters.getIdentifier();
    GARStepInfo garStepInfo = (GARStepInfo) stepParameters.getSpec();
    final String projectID =
        resolveStringParameter("projectID", "BuildAndPushGAR", identifier, garStepInfo.getProjectID(), true);
    final String host = resolveStringParameter(REGION, "BuildAndPushGAR", identifier, garStepInfo.getHost(), true);
    ArtifactUtils.populateArtifactForGARStep(host, projectID, artifactMetadata, stepArtifactsBuilder);
  }

  private void populateProvenanceInStepOutcome(Ambiance ambiance, StepArtifactsBuilder stepArtifactsBuilder,
      StepBaseParameters stepParameters, StageInfraDetails.Type infraType) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)
            || sscaLicenseHelper.hasActiveLicense(accountId))) {
      return;
    }
    if (!ProvenanceStepGenerator.getAllowedTypesForProvenance().contains(CIStepInfoType.GAR)) {
      return;
    }
    GARStepInfo garStepInfo = (GARStepInfo) stepParameters.getSpec();
    BuildMetadata buildMetadata = getBuildMetadata(garStepInfo, stepParameters.getIdentifier());

    String image;
    if (infraType == StageInfraDetails.Type.K8) {
      image = ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage();
    } else {
      image = ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId);
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

  private BuildMetadata getBuildMetadata(GARStepInfo garStepInfo, String identifier) {
    String repo = resolveStringParameter("imageName", "BuildAndPushGAR", identifier, garStepInfo.getImageName(), true);
    String dockerFile =
        resolveStringParameter("dockerfile", "BuildAndPushGAR", identifier, garStepInfo.getDockerfile(), false);
    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushGAR", identifier, garStepInfo.getBuildArgs(), false);
    String context = resolveStringParameter("context", "BuildAndPushGAR", identifier, garStepInfo.getContext(), false);
    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushGAR", identifier, garStepInfo.getLabels(), false);

    return BuildMetadata.builder()
        .image(repo)
        .dockerFile(dockerFile)
        .buildArgs(buildArgs)
        .context(context)
        .labels(labels)
        .build();
  }
}
