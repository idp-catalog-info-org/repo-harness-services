/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.SscaArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ssca.DriftSummary;
import io.harness.delegate.task.stepstatus.artifact.ssca.OssRisksSummary;
import io.harness.delegate.task.stepstatus.artifact.ssca.OssRisksSummary.OssRisksSummaryBuilder;
import io.harness.delegate.task.stepstatus.artifact.ssca.Scorecard;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.spec.server.ssca.v1.model.OrchestrationDriftSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationOssRisksSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationSummaryResponse;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationStep extends AbstractStepExecutable {
  private static final Logger log = LoggerFactory.getLogger(SscaOrchestrationStep.class);
  public static final StepType STEP_TYPE = SscaConstants.SSCA_ORCHESTRATION_STEP_TYPE;
  @Inject private SSCAServiceUtils sscaServiceUtils;

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);

    OrchestrationSummaryResponse stepExecutionResponse =
        sscaServiceUtils.getOrchestrationSummaryResponse(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    SscaArtifactMetadata sscaArtifactMetadata = SscaArtifactMetadata.builder()
                                                    .id(stepExecutionResponse.getArtifact().getId())
                                                    .name(stepExecutionResponse.getArtifact().getName())
                                                    .url(stepExecutionResponse.getArtifact().getRegistryUrl())
                                                    .type(Objects.nonNull(stepExecutionResponse.getArtifact().getType())
                                                            ? stepExecutionResponse.getArtifact().getType().toString()
                                                            : null)
                                                    .sbomName(stepExecutionResponse.getSbom().getName())
                                                    .stepExecutionId(stepExecutionId)
                                                    .imageTag(stepExecutionResponse.getArtifact().getTag())
                                                    .digest(stepExecutionResponse.getArtifact().getDigest())
                                                    .build();

    if (stepExecutionResponse.getScorecardSummary() != null) {
      sscaArtifactMetadata.setScorecard(Scorecard.builder()
                                            .avgScore(stepExecutionResponse.getScorecardSummary().getAvgScore())
                                            .maxScore(stepExecutionResponse.getScorecardSummary().getMaxScore())
                                            .build());
    }

    if (stepExecutionResponse.getDriftSummary() != null) {
      sscaArtifactMetadata.setDrift(getDriftSummary(stepExecutionResponse.getDriftSummary()));
    }

    if (stepExecutionResponse.getOssRisksSummary() != null) {
      sscaArtifactMetadata.setOssRisksSummary(getOssRisksSummary(stepExecutionResponse.getOssRisksSummary()));
    }

    stepStatus.setArtifactMetadata(ArtifactMetadata.builder()
                                       .type(ArtifactMetadataType.SSCA_ARTIFACT_METADATA)
                                       .spec(sscaArtifactMetadata)
                                       .build());
  }

  @Override
  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    OrchestrationSummaryResponse stepExecutionResponse =
        sscaServiceUtils.getOrchestrationSummaryResponse(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    PublishedSbomArtifact publishedSbomArtifact = PublishedSbomArtifact.builder()
                                                      .id(stepExecutionResponse.getArtifact().getId())
                                                      .url(stepExecutionResponse.getArtifact().getRegistryUrl())
                                                      .imageName(stepExecutionResponse.getArtifact().getName())
                                                      .name(stepExecutionResponse.getArtifact().getName())
                                                      .type(stepExecutionResponse.getArtifact().getType().toString())
                                                      .sbomName(stepExecutionResponse.getSbom().getName())
                                                      .stepExecutionId(stepExecutionId)
                                                      .tag(stepExecutionResponse.getArtifact().getTag())
                                                      .digest(stepExecutionResponse.getArtifact().getDigest())
                                                      .build();

    if (stepExecutionResponse.getScorecardSummary() != null) {
      publishedSbomArtifact.setScorecard(Scorecard.builder()
                                             .avgScore(stepExecutionResponse.getScorecardSummary().getAvgScore())
                                             .maxScore(stepExecutionResponse.getScorecardSummary().getMaxScore())
                                             .build());
    }

    if (stepExecutionResponse.getDriftSummary() != null) {
      publishedSbomArtifact.setDrift(getDriftSummary(stepExecutionResponse.getDriftSummary()));
    }

    if (stepExecutionResponse.getOssRisksSummary() != null) {
      publishedSbomArtifact.setOssRisksSummary(getOssRisksSummary(stepExecutionResponse.getOssRisksSummary()));
    }

    return StepArtifacts.builder().publishedSbomArtifact(publishedSbomArtifact).build();
  }

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }
    if (artifactMetadata.getType() == ArtifactMetadataType.SSCA_ARTIFACT_METADATA) {
      SscaArtifactMetadata sscaArtifactMetadata = (SscaArtifactMetadata) artifactMetadata.getSpec();

      if (sscaArtifactMetadata != null) {
        PublishedSbomArtifact publishedSbomArtifact = PublishedSbomArtifact.builder()
                                                          .id(sscaArtifactMetadata.getId())
                                                          .url(sscaArtifactMetadata.getUrl())
                                                          .digest(sscaArtifactMetadata.getDigest())
                                                          .name(sscaArtifactMetadata.getName())
                                                          .type(sscaArtifactMetadata.getType())
                                                          .imageName(sscaArtifactMetadata.getName())
                                                          .sbomName(sscaArtifactMetadata.getSbomName())
                                                          .sbomUrl(sscaArtifactMetadata.getSbomUrl())
                                                          .stepExecutionId(sscaArtifactMetadata.getStepExecutionId())
                                                          .tag(sscaArtifactMetadata.getImageTag())
                                                          .digest(sscaArtifactMetadata.getDigest())
                                                          .build();

        if (sscaArtifactMetadata.getScorecard() != null) {
          publishedSbomArtifact.setScorecard(Scorecard.builder()
                                                 .avgScore(sscaArtifactMetadata.getScorecard().getAvgScore())
                                                 .maxScore(sscaArtifactMetadata.getScorecard().getMaxScore())
                                                 .build());
        }

        if (sscaArtifactMetadata.getDrift() != null) {
          publishedSbomArtifact.setDrift(
              DriftSummary.builder()
                  .base(sscaArtifactMetadata.getDrift().getBase())
                  .driftId(sscaArtifactMetadata.getDrift().getDriftId())
                  .baseTag(sscaArtifactMetadata.getDrift().getBaseTag())
                  .totalDrifts(sscaArtifactMetadata.getDrift().getTotalDrifts())
                  .componentDrifts(sscaArtifactMetadata.getDrift().getComponentDrifts())
                  .licenseDrifts(sscaArtifactMetadata.getDrift().getLicenseDrifts())
                  .componentsAdded(sscaArtifactMetadata.getDrift().getComponentsAdded())
                  .componentsModified(sscaArtifactMetadata.getDrift().getComponentsModified())
                  .componentsDeleted(sscaArtifactMetadata.getDrift().getComponentsDeleted())
                  .licenseAdded(sscaArtifactMetadata.getDrift().getLicenseAdded())
                  .licenseDeleted(sscaArtifactMetadata.getDrift().getLicenseDeleted())
                  .build());
        }

        if (sscaArtifactMetadata.getOssRisksSummary() != null) {
          OssRisksSummaryBuilder builder =
              OssRisksSummary.builder()
                  .totalEolComponentCount(sscaArtifactMetadata.getOssRisksSummary().getTotalEolComponentCount())
                  .definiteEolComponentCount(sscaArtifactMetadata.getOssRisksSummary().getDefiniteEolComponentCount())
                  .derivedEolComponentCount(sscaArtifactMetadata.getOssRisksSummary().getDerivedEolComponentCount())
                  .closeToEolComponentCount(sscaArtifactMetadata.getOssRisksSummary().getCloseToEolComponentCount())
                  .unmaintainedComponentCount(sscaArtifactMetadata.getOssRisksSummary().getUnmaintainedComponentCount())
                  .outdatedComponentCount(sscaArtifactMetadata.getOssRisksSummary().getOutdatedComponentCount());

          if (sscaArtifactMetadata.getOssRisksSummary().getTyposquattedComponentCount() != null) {
            builder.typosquattedComponentCount(
                sscaArtifactMetadata.getOssRisksSummary().getTyposquattedComponentCount());
          }
          if (sscaArtifactMetadata.getOssRisksSummary().getMaliciousComponentCount() != null) {
            builder.maliciousComponentCount(sscaArtifactMetadata.getOssRisksSummary().getMaliciousComponentCount());
          }

          publishedSbomArtifact.setOssRisksSummary(builder.build());
        }

        stepArtifactsBuilder.publishedSbomArtifact(publishedSbomArtifact);
      }
    }
    return stepArtifactsBuilder.build();
  }

  private DriftSummary getDriftSummary(OrchestrationDriftSummary driftSummary) {
    return DriftSummary.builder()
        .base(driftSummary.getBase())
        .driftId(driftSummary.getDriftId())
        .baseTag(driftSummary.getBaseTag())
        .totalDrifts(driftSummary.getTotalDrifts())
        .componentDrifts(driftSummary.getComponentDrifts())
        .licenseDrifts(driftSummary.getLicenseDrifts())
        .componentsAdded(driftSummary.getComponentsAdded())
        .componentsModified(driftSummary.getComponentsModified())
        .componentsDeleted(driftSummary.getComponentsDeleted())
        .licenseAdded(driftSummary.getLicenseAdded())
        .licenseDeleted(driftSummary.getLicenseDeleted())
        .build();
  }

  private OssRisksSummary getOssRisksSummary(OrchestrationOssRisksSummary ossRisksSummary) {
    return OssRisksSummary.builder()
        .totalEolComponentCount(ossRisksSummary.getTotalEolComponentCount())
        .definiteEolComponentCount(ossRisksSummary.getDefiniteEolComponentCount())
        .derivedEolComponentCount(ossRisksSummary.getDerivedEolComponentCount())
        .closeToEolComponentCount(ossRisksSummary.getCloseToEolComponentCount())
        .unmaintainedComponentCount(ossRisksSummary.getUnmaintainedComponentCount())
        .outdatedComponentCount(ossRisksSummary.getOutdatedComponentCount())
        .typosquattedComponentCount(ossRisksSummary.getTyposquattedComponentCount())
        .maliciousComponentCount(ossRisksSummary.getMaliciousComponentCount())
        .build();
  }
}
