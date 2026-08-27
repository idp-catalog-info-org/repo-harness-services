/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.SscaArtifactMetadata;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
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
public class SscaEnforcementStep extends AbstractStepExecutable {
  private static final Logger log = LoggerFactory.getLogger(SscaEnforcementStep.class);
  public static final StepType STEP_TYPE = SscaConstants.SSCA_ENFORCEMENT_STEP_TYPE;

  @Inject SSCAServiceUtils sscaServiceUtils;

  @Override
  protected boolean shouldPublishArtifact(StepStatus stepStatus) {
    return true;
  }

  @Override
  protected boolean shouldPublishArtifactForVm(CommandExecutionStatus commandExecutionStatus) {
    return true;
  }

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);

    EnforcementSummaryResponse enforcementSummary =
        sscaServiceUtils.getEnforcementSummary(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    stepStatus.setArtifactMetadata(
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.SSCA_ARTIFACT_METADATA)
            .spec(SscaArtifactMetadata.builder()
                      .id(enforcementSummary.getArtifact().getId())
                      .name(enforcementSummary.getArtifact().getName())
                      .url(enforcementSummary.getArtifact().getRegistryUrl())
                      .type(Objects.nonNull(enforcementSummary.getArtifact().getType())
                              ? enforcementSummary.getArtifact().getType().toString()
                              : null)
                      .stepExecutionId(stepExecutionId)
                      .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
                      .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
                      .skippedComponentCount(enforcementSummary.getSkippedComponentCount())
                      .imageTag(enforcementSummary.getArtifact().getTag())
                      .digest(enforcementSummary.getArtifact().getDigest())
                      .build())
            .build());
  }

  @Override
  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    EnforcementSummaryResponse enforcementSummary =
        sscaServiceUtils.getEnforcementSummary(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    return StepArtifacts.builder()
        .publishedSbomArtifact(PublishedSbomArtifact.builder()
                                   .id(enforcementSummary.getArtifact().getId())
                                   .url(enforcementSummary.getArtifact().getRegistryUrl())
                                   .imageName(enforcementSummary.getArtifact().getName())
                                   .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
                                   .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
                                   .skippedComponentCount(enforcementSummary.getSkippedComponentCount())
                                   .stepExecutionId(stepExecutionId)
                                   .tag(enforcementSummary.getArtifact().getTag())
                                   .digest(enforcementSummary.getArtifact().getDigest())
                                   .build())
        .build();
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
        stepArtifactsBuilder.publishedSbomArtifact(
            PublishedSbomArtifact.builder()
                .id(sscaArtifactMetadata.getId())
                .url(sscaArtifactMetadata.getUrl())
                .digest(sscaArtifactMetadata.getDigest())
                .imageName(sscaArtifactMetadata.getName())
                .name(sscaArtifactMetadata.getName())
                .type(sscaArtifactMetadata.getType())
                .stepExecutionId(sscaArtifactMetadata.getStepExecutionId())
                .allowListViolationCount(sscaArtifactMetadata.getAllowListViolationCount())
                .denyListViolationCount(sscaArtifactMetadata.getDenyListViolationCount())
                .skippedComponentCount(sscaArtifactMetadata.getSkippedComponentCount())
                .tag(sscaArtifactMetadata.getImageTag())
                .digest(sscaArtifactMetadata.getDigest())
                .build());
      }
    }
    return stepArtifactsBuilder.build();
  }
}
