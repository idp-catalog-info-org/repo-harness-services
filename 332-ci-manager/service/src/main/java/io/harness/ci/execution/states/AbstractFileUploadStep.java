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
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for CI file-upload steps (S3, GCS, Artifactory, HAR).
 * Overrides the 3-arg handleArtifact so that the Ambiance is available for
 * MongoDB persistence. Concrete step classes override the 2-arg handleArtifact
 * to build StepArtifacts and implement getStorageType() to identify themselves.
 */
@Slf4j
public abstract class AbstractFileUploadStep extends AbstractStepExecutable {
  @Inject(optional = true) ArtifactDetailsService artifactDetailsService;

  /**
   * Returns the storage-type label recorded in MongoDB (e.g. "GCS", "S3", "ARTIFACTORY", "HAR").
   * CI manager sets this itself — the plugin does not send it.
   */
  protected abstract String getStorageType();

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    StepArtifacts stepArtifacts = handleArtifact(artifactMetadata, stepParameters);
    saveArtifactDetails(stepArtifacts, ambiance);
    return stepArtifacts;
  }

  private void saveArtifactDetails(StepArtifacts stepArtifacts, Ambiance ambiance) {
    if (artifactDetailsService == null) {
      return;
    }
    if (stepArtifacts == null || stepArtifacts.getPublishedFileArtifacts() == null
        || stepArtifacts.getPublishedFileArtifacts().isEmpty()) {
      return;
    }
    try {
      artifactDetailsService.saveFileArtifactDetails(
          stepArtifacts.getPublishedFileArtifacts(), ambiance, getStorageType());
    } catch (Exception e) {
      log.warn("Failed to save file artifact details for step", e);
    }
  }
}
