/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.app.beans.entities.ArtifactDetailsResource;
import io.harness.app.beans.entities.artifacts.ArtifactDetails;
import io.harness.app.beans.entities.artifacts.ArtifactDetailsRequestDTO;
import io.harness.app.beans.entities.artifacts.PublishedArtifactExecutionDetailsResponseDTO;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsService;
import io.harness.exception.InternalServerErrorException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class ArtifactDetailsResourceImpl implements ArtifactDetailsResource {
  @Inject(optional = true) ArtifactDetailsService artifactDetailsService;

  public ResponseDTO<PublishedArtifactExecutionDetailsResponseDTO> getArtifactDetails(
      @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @Valid ArtifactDetailsRequestDTO artifactDetailsRequestDTO) {
    if (isNull(artifactDetailsService)) {
      throw new InternalServerErrorException("Unable to fetch artifact details as service is unavailable");
    }
    ArtifactDetails artifactDetails =
        artifactDetailsService.getArtifactDetailsList(accountIdentifier, artifactDetailsRequestDTO);
    PublishedArtifactExecutionDetailsResponseDTO artifactDetailsResponseDTO =
        PublishedArtifactExecutionDetailsResponseDTO.builder()
            .projectIdentifier(artifactDetails.getProjectIdentifier())
            .orgIdentifier(artifactDetails.getOrgIdentifier())
            .pipelineIdentifier(artifactDetails.getPipelineIdentifier())
            .pipelineExecutionId(artifactDetails.getPipelineExecutionId())
            .stageExecutionId(artifactDetails.getStageExecutionId())
            .stepExecutionId(artifactDetails.getStepExecutionId())
            .build();
    return ResponseDTO.newResponse(artifactDetailsResponseDTO);
  }
}
