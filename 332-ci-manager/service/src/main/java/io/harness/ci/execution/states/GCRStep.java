/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.ProvenanceBuilderData;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.k8s.model.ImageDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.ssca.execution.provenance.ProvenanceStepGenerator;

import com.google.inject.Inject;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.CI)
public class GCRStep extends AbstractImagePushStep {
  public static final StepType STEP_TYPE = GCRStepInfo.STEP_TYPE;
  private static final String GCR_URL_FORMAT = "https://console.cloud.google.com/gcr/images/%s/%s/%s@%s/details";
  private static final String GCR_HOST_REGEX = "^(?:(?<region>[a-zA-Z]+)\\.)?gcr\\.io/?$";
  private static final String GCR_GLOBAL_REGION = "GLOBAL";

  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject ProvenanceGenerator provenanceGenerator;
  @Inject SSCALicenseHelper sscaLicenseHelper;

  @Override
  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    return getStepArtifacts(artifactMetadata, stepParameters, ambiance, Type.VM);
  }

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    return getStepArtifacts(artifactMetadata, stepParameters, ambiance, Type.K8);
  }

  private StepArtifacts getStepArtifacts(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance, Type infraType) {
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

  private void populateArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, StepArtifactsBuilder stepArtifactsBuilder) {
    String identifier = stepParameters.getIdentifier();
    GCRStepInfo ecrStepInfo = (GCRStepInfo) stepParameters.getSpec();
    final String projectID =
        resolveStringParameter("projectID", "BuildAndPushECR", identifier, ecrStepInfo.getProjectID(), true);
    final String host = resolveStringParameter("host", "BuildAndPushECR", identifier, ecrStepInfo.getHost(), true);

    final Pattern pattern = Pattern.compile(GCR_HOST_REGEX, Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(host);
    final String region = (matcher.find() && isNotEmpty(matcher.group("region")))
        ? matcher.group("region").toUpperCase()
        : GCR_GLOBAL_REGION;

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
      if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
        dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
          String digest = desc.getDigest();
          ImageDetails imageDetails = IntegrationStageUtils.getImageInfo(desc.getImageName());
          String image = imageDetails.getName();
          String tag = imageDetails.getTag();
          image = image.replaceFirst("^" + Pattern.quote(dockerArtifactMetadata.getRegistryUrl()) + "/?", "");

          stepArtifactsBuilder.publishedImageArtifact(PublishedImageArtifact.builder()
                                                          .imageName(image)
                                                          .tag(tag)
                                                          .url(format(GCR_URL_FORMAT, projectID, region, image, digest))
                                                          .digest(digest)
                                                          .build());
        });
      }
    }
  }

  private void populateProvenanceInStepOutcome(
      Ambiance ambiance, StepArtifactsBuilder stepArtifactsBuilder, StepBaseParameters stepParameters, Type infraType) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if ((!featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)
            && !sscaLicenseHelper.hasActiveLicense(accountId))
        || !ProvenanceStepGenerator.getAllowedTypesForProvenance().contains(CIStepInfoType.GCR)) {
      return;
    }
    GCRStepInfo gcrStepInfo = (GCRStepInfo) stepParameters.getSpec();
    BuildMetadata buildMetadata = getBuildMetadata(gcrStepInfo, stepParameters.getIdentifier());

    String image;
    if (infraType == Type.K8) {
      image = ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage();
    } else {
      image = ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId);
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

  private BuildMetadata getBuildMetadata(GCRStepInfo gcrStepInfo, String identifier) {
    String repo = resolveStringParameter("imageName", "BuildAndPushGCR", identifier, gcrStepInfo.getImageName(), true);
    String dockerFile =
        resolveStringParameter("dockerfile", "BuildAndPushGCR", identifier, gcrStepInfo.getDockerfile(), false);
    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushGCR", identifier, gcrStepInfo.getBuildArgs(), false);
    String context = resolveStringParameter("context", "BuildAndPushGCR", identifier, gcrStepInfo.getContext(), false);
    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushGCR", identifier, gcrStepInfo.getLabels(), false);

    return BuildMetadata.builder()
        .image(repo)
        .dockerFile(dockerFile)
        .buildArgs(buildArgs)
        .context(context)
        .labels(labels)
        .build();
  }
}
