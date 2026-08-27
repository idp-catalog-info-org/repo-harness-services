/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.artifacts.ArtifactDetails;
import io.harness.app.beans.entities.artifacts.ArtifactDetailsRequestDTO;
import io.harness.app.beans.entities.artifacts.PublishedArtifactExecutionDetailsResponseDTO;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsService;
import io.harness.exception.InternalServerErrorException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class ArtifactDetailsResourceImplTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private ArtifactDetailsService artifactDetailsService;
  @InjectMocks private ArtifactDetailsResourceImpl artifactDetailsResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactDetailsWhenServiceIsNull() {
    ArtifactDetailsResourceImpl resourceWithNullService = new ArtifactDetailsResourceImpl(null);
    ArtifactDetailsRequestDTO requestDTO = ArtifactDetailsRequestDTO.builder().build();

    assertThatThrownBy(() -> resourceWithNullService.getArtifactDetails(ACCOUNT_IDENTIFIER, requestDTO))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Unable to fetch artifact details as service is unavailable");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArtifactDetailsSuccess() {
    ArtifactDetailsRequestDTO requestDTO = ArtifactDetailsRequestDTO.builder().build();
    ArtifactDetails artifactDetails = ArtifactDetails.builder()
                                          .orgIdentifier("org")
                                          .projectIdentifier("project")
                                          .pipelineIdentifier("pipeline")
                                          .pipelineExecutionId("execId")
                                          .stageExecutionId("stageId")
                                          .stepExecutionId("stepId")
                                          .build();
    when(artifactDetailsService.getArtifactDetailsList(ACCOUNT_IDENTIFIER, requestDTO)).thenReturn(artifactDetails);

    ResponseDTO<PublishedArtifactExecutionDetailsResponseDTO> response =
        artifactDetailsResource.getArtifactDetails(ACCOUNT_IDENTIFIER, requestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getOrgIdentifier()).isEqualTo("org");
    assertThat(response.getData().getProjectIdentifier()).isEqualTo("project");
    assertThat(response.getData().getPipelineIdentifier()).isEqualTo("pipeline");
    assertThat(response.getData().getPipelineExecutionId()).isEqualTo("execId");
    assertThat(response.getData().getStageExecutionId()).isEqualTo("stageId");
    assertThat(response.getData().getStepExecutionId()).isEqualTo("stepId");
  }
}
