/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class UploadToArtifactoryStepTest extends CategoryTest {
  private UploadToArtifactoryStep uploadToArtifactoryStep;

  @Before
  public void setUp() {
    uploadToArtifactoryStep = new UploadToArtifactoryStep();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testStepType() {
    StepType stepType = UploadToArtifactoryStep.STEP_TYPE;
    assertThat(stepType).isEqualTo(UploadToArtifactoryStepInfo.STEP_TYPE);
    assertThat(stepType.getStepCategory().name()).isEqualTo("STEP");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStorageType() {
    assertThat(uploadToArtifactoryStep.getStorageType()).isEqualTo("ARTIFACTORY");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactReturnsEmptyWhenNull() {
    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(null, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactWithNonFileMetadataType() {
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA).spec(null).build();

    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(artifactMetadata, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactWithNullFileArtifactMetadata() {
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.FILE_ARTIFACT_METADATA).spec(null).build();

    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(artifactMetadata, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactWithEmptyDescriptors() {
    FileArtifactMetadata fileArtifactMetadata = FileArtifactMetadata.builder().build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.FILE_ARTIFACT_METADATA).spec(fileArtifactMetadata).build();

    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(artifactMetadata, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactWithSingleDescriptor() {
    FileArtifactMetadata fileArtifactMetadata =
        FileArtifactMetadata.builder()
            .fileArtifactDescriptor(FileArtifactDescriptor.builder()
                                        .name("file.txt")
                                        .url("https://artifactory.example.com/repo/file.txt")
                                        .filePath("repo/file.txt")
                                        .digest("sha256:abc123")
                                        .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.FILE_ARTIFACT_METADATA).spec(fileArtifactMetadata).build();

    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(artifactMetadata, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).hasSize(1);
    assertThat(result.getPublishedFileArtifacts().get(0).getUrl())
        .isEqualTo("https://artifactory.example.com/repo/file.txt");
    assertThat(result.getPublishedFileArtifacts().get(0).getName()).isEqualTo("file.txt");
    assertThat(result.getPublishedFileArtifacts().get(0).getFilePath()).isEqualTo("repo/file.txt");
    assertThat(result.getPublishedFileArtifacts().get(0).getDigest()).isEqualTo("sha256:abc123");
    assertThat(result.getPublishedFileArtifacts().get(0).getBucketName())
        .as("bucketName should be null for Artifactory")
        .isNull();
    assertThat(result.getPublishedFileArtifacts().get(0).getRegion())
        .as("region should be null for Artifactory")
        .isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactWithMultipleDescriptors() {
    FileArtifactMetadata fileArtifactMetadata =
        FileArtifactMetadata.builder()
            .fileArtifactDescriptor(FileArtifactDescriptor.builder()
                                        .name("file1.txt")
                                        .url("https://artifactory.example.com/repo/file1.txt")
                                        .build())
            .fileArtifactDescriptor(FileArtifactDescriptor.builder()
                                        .name("file2.jar")
                                        .url("https://artifactory.example.com/repo/file2.jar")
                                        .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.FILE_ARTIFACT_METADATA).spec(fileArtifactMetadata).build();

    StepArtifacts result = uploadToArtifactoryStep.handleArtifact(artifactMetadata, null);
    assertThat(result).isNotNull();
    assertThat(result.getPublishedFileArtifacts()).hasSize(2);
    assertThat(result.getPublishedFileArtifacts().get(0).getUrl())
        .isEqualTo("https://artifactory.example.com/repo/file1.txt");
    assertThat(result.getPublishedFileArtifacts().get(1).getUrl())
        .isEqualTo("https://artifactory.example.com/repo/file2.jar");
  }
}
