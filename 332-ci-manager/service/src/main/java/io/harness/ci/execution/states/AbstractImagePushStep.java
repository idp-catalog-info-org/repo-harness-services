/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for CI image-push steps (Docker, GCR, ECR, ACR, GAR).
 * Provides shared ArtifactDetails persistence logic.
 */
@Slf4j
public abstract class AbstractImagePushStep extends AbstractStepExecutable {
  @Inject(optional = true) ArtifactDetailsService artifactDetailsService;

  protected void saveArtifactDetails(
      StepArtifacts stepArtifacts, ArtifactMetadata artifactMetadata, Ambiance ambiance) {
    if (artifactDetailsService == null || artifactMetadata.getType() != ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      return;
    }
    stepArtifacts.getPublishedImageArtifacts().forEach(artifact -> {
      try {
        artifactDetailsService.saveDockerArtifactDetails(artifact, ambiance);
      } catch (Exception e) {
        log.warn("Failed to save artifact details for image {}", artifact.getImageName(), e);
      }
    });
  }
}
