/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.UploadToHarStepInfo;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;

@OwnedBy(HarnessTeam.HAR)
public class UploadToHarStep extends AbstractFileUploadStep {
  public static final StepType STEP_TYPE = UploadToHarStepInfo.STEP_TYPE;

  @Override
  protected String getStorageType() {
    return "HAR";
  }

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }
    if (artifactMetadata.getType() == ArtifactMetadataType.FILE_ARTIFACT_METADATA) {
      FileArtifactMetadata fileArtifactMetadata = (FileArtifactMetadata) artifactMetadata.getSpec();
      if (fileArtifactMetadata != null && isNotEmpty(fileArtifactMetadata.getFileArtifactDescriptors())) {
        fileArtifactMetadata.getFileArtifactDescriptors().forEach(desc
            -> stepArtifactsBuilder.publishedFileArtifact(PublishedFileArtifact.builder()
                                                              .name(desc.getName())
                                                              .url(desc.getUrl())
                                                              .filePath(desc.getFilePath())
                                                              // bucketName and region are null for HAR
                                                              .digest(desc.getDigest())
                                                              .registry(desc.getRegistry())
                                                              .packageName(desc.getPackageName())
                                                              .version(desc.getVersion())
                                                              .packageType(desc.getPackageType())
                                                              .build()));
      }
    }
    return stepArtifactsBuilder.build();
  }
}
