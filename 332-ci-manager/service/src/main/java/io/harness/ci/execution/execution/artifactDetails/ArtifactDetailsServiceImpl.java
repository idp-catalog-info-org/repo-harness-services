/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.artifactDetails;

import io.harness.app.beans.entities.artifacts.ArtifactDetails;
import io.harness.app.beans.entities.artifacts.ArtifactDetails.ArtifactDetailsKeys;
import io.harness.app.beans.entities.artifacts.ArtifactDetails.ArtifactDetailsKeysAdditional;
import io.harness.app.beans.entities.artifacts.ArtifactDetailsRequestDTO;
import io.harness.app.beans.entities.artifacts.ArtifactMetadata;
import io.harness.app.beans.entities.artifacts.DockerArtifactMetadata;
import io.harness.app.beans.entities.artifacts.DockerArtifactMetadataRequestDTO;
import io.harness.app.beans.entities.artifacts.FileArtifactMetadata;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.ArtifactDetailsRepository;

import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class ArtifactDetailsServiceImpl implements ArtifactDetailsService {
  @Inject ArtifactDetailsRepository artifactDetailsRepository;

  @Override
  public ArtifactDetails getArtifactDetailsList(
      String accountIdentifier, ArtifactDetailsRequestDTO artifactDetailsRequestDTO) {
    Criteria criteria = Criteria.where(ArtifactDetailsKeys.accountId)
                            .is(accountIdentifier)
                            .and(ArtifactDetailsKeys.type)
                            .is(artifactDetailsRequestDTO.getType().toString());
    switch (artifactDetailsRequestDTO.getType()) {
      case DOCKER:
        DockerArtifactMetadataRequestDTO dockerArtifactMetadataRequestDTO =
            (DockerArtifactMetadataRequestDTO) artifactDetailsRequestDTO.getArtifactMetadataRequestDTO();
        criteria.and(ArtifactDetailsKeysAdditional.imagePath).is(dockerArtifactMetadataRequestDTO.getImagePath());
        if (dockerArtifactMetadataRequestDTO.getDigest() != null
            && !dockerArtifactMetadataRequestDTO.getDigest().isEmpty()) {
          criteria.and(ArtifactDetailsKeysAdditional.digest).is(dockerArtifactMetadataRequestDTO.getDigest());
        } else {
          criteria.and(ArtifactDetailsKeysAdditional.tag).is(dockerArtifactMetadataRequestDTO.getTag());
        }
        break;
      default:
        throw new InvalidRequestException(
            String.format("artifact type: [%s] given in the request in invalid", artifactDetailsRequestDTO.getType()));
    }
    Optional<ArtifactDetails> artifactDetails = artifactDetailsRepository.findOneByCriteria(criteria);

    if (artifactDetails.isEmpty()) {
      throw new EntityNotFoundException("No Execution details exist for the given artifacts details");
    }
    return artifactDetails.get();
  }

  @Override
  public ArtifactDetails saveDockerArtifactDetails(PublishedImageArtifact publishedImageArtifact, Ambiance ambiance) {
    List<ArtifactMetadata> artifactMetadataList = List.of(DockerArtifactMetadata.builder()
                                                              .registryUrl(publishedImageArtifact.getUrl())
                                                              .tag(publishedImageArtifact.getTag())
                                                              .imagePath(publishedImageArtifact.getImageName())
                                                              .digest(publishedImageArtifact.getDigest())
                                                              .build());
    ArtifactDetails artifactDetails = ArtifactDetails.builder()
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
                                          .pipelineExecutionId(AmbianceUtils.getPipelineExecutionIdentifier(ambiance))
                                          .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                          .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                          .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                          .stageExecutionId(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
                                          .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
                                          .artifactMetadataList(artifactMetadataList)
                                          .type(ArtifactType.DOCKER.toString())
                                          .createdAt(System.currentTimeMillis())
                                          .lastUpdatedAt(System.currentTimeMillis())
                                          .build();
    return artifactDetailsRepository.save(artifactDetails);
  }

  @Override
  public ArtifactDetails saveFileArtifactDetails(
      List<PublishedFileArtifact> publishedFileArtifacts, Ambiance ambiance, String storageType) {
    List<ArtifactMetadata> artifactMetadataList = publishedFileArtifacts.stream()
                                                      .map(artifact
                                                          -> FileArtifactMetadata.builder()
                                                                 .fileName(artifact.getName())
                                                                 .url(artifact.getUrl())
                                                                 .digest(artifact.getDigest())
                                                                 .storageType(storageType)
                                                                 .filePath(artifact.getFilePath())
                                                                 .bucketName(artifact.getBucketName())
                                                                 .region(artifact.getRegion())
                                                                 .registry(artifact.getRegistry())
                                                                 .packageName(artifact.getPackageName())
                                                                 .version(artifact.getVersion())
                                                                 .packageType(artifact.getPackageType())
                                                                 .build())
                                                      .collect(Collectors.toList());
    ArtifactDetails artifactDetails = ArtifactDetails.builder()
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
                                          .pipelineExecutionId(AmbianceUtils.getPipelineExecutionIdentifier(ambiance))
                                          .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                          .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                          .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                          .stageExecutionId(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
                                          .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
                                          .artifactMetadataList(artifactMetadataList)
                                          .type(io.harness.app.beans.entities.artifacts.ArtifactType.FILE.toString())
                                          .createdAt(System.currentTimeMillis())
                                          .lastUpdatedAt(System.currentTimeMillis())
                                          .build();
    return artifactDetailsRepository.save(artifactDetails);
  }
}
