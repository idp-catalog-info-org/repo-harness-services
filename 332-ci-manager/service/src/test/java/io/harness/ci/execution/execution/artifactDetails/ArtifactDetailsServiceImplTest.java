/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.artifactDetails;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.artifacts.ArtifactDetails;
import io.harness.app.beans.entities.artifacts.ArtifactDetailsRequestDTO;
import io.harness.app.beans.entities.artifacts.ArtifactMetadata;
import io.harness.app.beans.entities.artifacts.ArtifactType;
import io.harness.app.beans.entities.artifacts.DockerArtifactMetadata;
import io.harness.app.beans.entities.artifacts.DockerArtifactMetadataRequestDTO;
import io.harness.app.beans.entities.artifacts.FileArtifactMetadata;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.exception.EntityNotFoundException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.repositories.ArtifactDetailsRepository;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Criteria;

public class ArtifactDetailsServiceImplTest extends CIExecutionTestBase {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String PIPELINE_ID = "testPipeline";
  private static final String PIPELINE_EXECUTION_ID = "testPipelineExecution";
  private static final String STAGE_EXECUTION_ID = "testStageExecutionId";
  private static final String STAGE_RUNTIME_ID = "testStageRuntime";
  private static final String STEP_RUNTIME_ID = "testStepRuntime";
  private static final String IMAGE_PATH = "library/nginx";
  private static final String TAG = "latest";
  private static final String DIGEST = "sha256:abc123";
  private static final String REGISTRY_URL = "https://index.docker.io/v1/";
  private static final String FILE_NAME = "artifact.zip";
  private static final String FILE_URL = "https://bucket.s3.amazonaws.com/uploads/artifact.zip";
  private static final String FILE_PATH = "uploads/artifact.zip";
  private static final String BUCKET_NAME = "bucket";
  private static final String STORAGE_TYPE = "S3";

  @Mock private ArtifactDetailsRepository artifactDetailsRepository;
  @InjectMocks private ArtifactDetailsServiceImpl artifactDetailsService;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    ambiance = buildAmbiance();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactDetailsList_whenDockerTypeAndDetailsExist_shouldReturnDetails() {
    DockerArtifactMetadataRequestDTO dockerRequest =
        DockerArtifactMetadataRequestDTO.builder().imagePath(IMAGE_PATH).tag(TAG).digest(DIGEST).build();
    ArtifactDetailsRequestDTO requestDTO =
        ArtifactDetailsRequestDTO.builder().type(ArtifactType.DOCKER).artifactMetadataRequestDTO(dockerRequest).build();
    ArtifactDetails expectedDetails =
        ArtifactDetails.builder().accountId(ACCOUNT_ID).type(ArtifactType.DOCKER.toString()).build();
    when(artifactDetailsRepository.findOneByCriteria(any(Criteria.class))).thenReturn(Optional.of(expectedDetails));

    ArtifactDetails result = artifactDetailsService.getArtifactDetailsList(ACCOUNT_ID, requestDTO);

    assertThat(result).as("Should return the artifact details found by repository").isEqualTo(expectedDetails);
    assertThat(result.getAccountId()).as("Account ID should match").isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactDetailsList_whenDockerTypeAndDetailsNotFound_shouldThrowEntityNotFound() {
    DockerArtifactMetadataRequestDTO dockerRequest =
        DockerArtifactMetadataRequestDTO.builder().imagePath(IMAGE_PATH).tag(TAG).digest(DIGEST).build();
    ArtifactDetailsRequestDTO requestDTO =
        ArtifactDetailsRequestDTO.builder().type(ArtifactType.DOCKER).artifactMetadataRequestDTO(dockerRequest).build();
    when(artifactDetailsRepository.findOneByCriteria(any(Criteria.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> artifactDetailsService.getArtifactDetailsList(ACCOUNT_ID, requestDTO))
        .as("Should throw EntityNotFoundException when no details exist")
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("No Execution details exist for the given artifacts details");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSaveDockerArtifactDetails_shouldBuildAndSaveCorrectArtifactDetails() {
    PublishedImageArtifact publishedImageArtifact =
        PublishedImageArtifact.builder().imageName(IMAGE_PATH).tag(TAG).url(REGISTRY_URL).digest(DIGEST).build();
    ArtifactDetails savedDetails = ArtifactDetails.builder().accountId(ACCOUNT_ID).build();
    when(artifactDetailsRepository.save(any(ArtifactDetails.class))).thenReturn(savedDetails);

    ArtifactDetails result = artifactDetailsService.saveDockerArtifactDetails(publishedImageArtifact, ambiance);

    assertThat(result).as("Should return the saved artifact details").isEqualTo(savedDetails);

    ArgumentCaptor<ArtifactDetails> captor = ArgumentCaptor.forClass(ArtifactDetails.class);
    verify(artifactDetailsRepository).save(captor.capture());
    ArtifactDetails captured = captor.getValue();

    assertThat(captured.getAccountId()).as("Account ID should be extracted from ambiance").isEqualTo(ACCOUNT_ID);
    assertThat(captured.getPipelineIdentifier())
        .as("Pipeline identifier should be extracted from ambiance")
        .isEqualTo(PIPELINE_ID);
    assertThat(captured.getPipelineExecutionId())
        .as("Pipeline execution ID should be extracted from ambiance")
        .isEqualTo(PIPELINE_EXECUTION_ID);
    assertThat(captured.getOrgIdentifier()).as("Org identifier should be extracted from ambiance").isEqualTo(ORG_ID);
    assertThat(captured.getProjectIdentifier())
        .as("Project identifier should be extracted from ambiance")
        .isEqualTo(PROJECT_ID);
    assertThat(captured.getType()).as("Type should be DOCKER").isEqualTo("DOCKER");
    assertThat(captured.getArtifactMetadataList()).as("Should have exactly one artifact metadata entry").hasSize(1);

    ArtifactMetadata metadata = captured.getArtifactMetadataList().get(0);
    assertThat(metadata).as("Metadata should be DockerArtifactMetadata").isInstanceOf(DockerArtifactMetadata.class);
    DockerArtifactMetadata dockerMetadata = (DockerArtifactMetadata) metadata;
    assertThat(dockerMetadata.getImagePath()).as("Image path should match published artifact").isEqualTo(IMAGE_PATH);
    assertThat(dockerMetadata.getTag()).as("Tag should match published artifact").isEqualTo(TAG);
    assertThat(dockerMetadata.getRegistryUrl())
        .as("Registry URL should match published artifact")
        .isEqualTo(REGISTRY_URL);
    assertThat(dockerMetadata.getDigest()).as("Digest should match published artifact").isEqualTo(DIGEST);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSaveDockerArtifactDetails_shouldSetStepAndStageExecutionIds() {
    PublishedImageArtifact publishedImageArtifact =
        PublishedImageArtifact.builder().imageName(IMAGE_PATH).tag(TAG).url(REGISTRY_URL).digest(DIGEST).build();
    when(artifactDetailsRepository.save(any(ArtifactDetails.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    artifactDetailsService.saveDockerArtifactDetails(publishedImageArtifact, ambiance);

    ArgumentCaptor<ArtifactDetails> captor = ArgumentCaptor.forClass(ArtifactDetails.class);
    verify(artifactDetailsRepository).save(captor.capture());
    ArtifactDetails captured = captor.getValue();

    assertThat(captured.getStepExecutionId())
        .as("Step execution ID should be the current runtime ID from ambiance")
        .isEqualTo(STEP_RUNTIME_ID);
    assertThat(captured.getStageExecutionId())
        .as("Stage execution ID should be extracted from ambiance")
        .isEqualTo(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSaveFileArtifactDetails_shouldBuildAndSaveCorrectArtifactDetails() {
    PublishedFileArtifact publishedFileArtifact = PublishedFileArtifact.builder()
                                                      .name(FILE_NAME)
                                                      .url(FILE_URL)
                                                      .filePath(FILE_PATH)
                                                      .bucketName(BUCKET_NAME)
                                                      .digest(DIGEST)
                                                      .build();
    when(artifactDetailsRepository.save(any(ArtifactDetails.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ArtifactDetails result =
        artifactDetailsService.saveFileArtifactDetails(List.of(publishedFileArtifact), ambiance, STORAGE_TYPE);

    assertThat(result.getAccountId()).as("Account ID should be extracted from ambiance").isEqualTo(ACCOUNT_ID);
    assertThat(result.getType())
        .as("Type should be FILE, backed by the ArtifactType.FILE enum value")
        .isEqualTo("FILE");
    assertThat(result.getArtifactMetadataList()).as("Should have exactly one artifact metadata entry").hasSize(1);

    ArtifactMetadata metadata = result.getArtifactMetadataList().get(0);
    assertThat(metadata).as("Metadata should be FileArtifactMetadata").isInstanceOf(FileArtifactMetadata.class);
    FileArtifactMetadata fileMetadata = (FileArtifactMetadata) metadata;
    assertThat(fileMetadata.getFileName()).isEqualTo(FILE_NAME);
    assertThat(fileMetadata.getUrl()).isEqualTo(FILE_URL);
    assertThat(fileMetadata.getFilePath()).isEqualTo(FILE_PATH);
    assertThat(fileMetadata.getBucketName()).isEqualTo(BUCKET_NAME);
    assertThat(fileMetadata.getDigest()).isEqualTo(DIGEST);
    assertThat(fileMetadata.getStorageType()).as("Storage type should be set by the caller").isEqualTo(STORAGE_TYPE);
  }

  private Ambiance buildAmbiance() {
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build();
    Level stageLevel =
        Level.newBuilder().setRuntimeId(STAGE_RUNTIME_ID).setSetupId("stageSetup").setGroup("STAGE").build();
    Level stepLevel = Level.newBuilder().setRuntimeId(STEP_RUNTIME_ID).setSetupId("stepSetup").setGroup("STEP").build();
    return Ambiance.newBuilder()
        .setMetadata(metadata)
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", ORG_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_ID)
        .setPlanExecutionId(PIPELINE_EXECUTION_ID)
        .setStageExecutionId(STAGE_EXECUTION_ID)
        .addLevels(stageLevel)
        .addLevels(stepLevel)
        .build();
  }
}
