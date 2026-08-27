/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.BranchSequenceDTO;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.pms.pipeline.service.BranchSequenceService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class BranchSequenceResourceImplTest extends CategoryTest {
  @Mock private BranchSequenceService branchSequenceService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @InjectMocks private BranchSequenceResourceImpl branchSequenceResource;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String REPO_URL = "https://github.com/harness/harness-core.git";
  private static final String BRANCH = "main";
  private static final String NORMALIZED_REPO_URL = "github.com/harness/harness-core";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testListBranchSequences_FeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    assertThatThrownBy(() -> branchSequenceResource.listBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("CI_ENABLE_BRANCH_SEQUENCE_ID");

    verify(branchSequenceService, never()).getAllForPipeline(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testListBranchSequences_FeatureFlagEnabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(5)
                                          .build();

    doReturn(Arrays.asList(sequence))
        .when(branchSequenceService)
        .getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    ResponseDTO<List<BranchSequenceDTO>> response =
        branchSequenceResource.listBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getSequenceId()).isEqualTo(5);
    assertThat(response.getData().get(0).getBranch()).isEqualTo(BRANCH);
    assertThat(response.getData().get(0).getNormalizedRepoUrl()).isEqualTo(NORMALIZED_REPO_URL);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_FeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    assertThatThrownBy(
        () -> branchSequenceResource.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("CI_ENABLE_BRANCH_SEQUENCE_ID");

    verify(branchSequenceService, never())
        .getBranchSequence(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_FeatureFlagEnabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(Optional.of(10L))
        .when(branchSequenceService)
        .getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    ResponseDTO<BranchSequenceDTO> response =
        branchSequenceResource.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(response.getData().getSequenceId()).isEqualTo(10);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequences_FeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    assertThatThrownBy(() -> branchSequenceResource.deleteBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("CI_ENABLE_BRANCH_SEQUENCE_ID");

    verify(branchSequenceService, never()).deleteAllForPipeline(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequences_FeatureFlagEnabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(3L).when(branchSequenceService).deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    ResponseDTO<Long> response =
        branchSequenceResource.deleteBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(response.getData()).isEqualTo(3L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_FeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    assertThatThrownBy(()
                           -> branchSequenceResource.deleteBranchSequence(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("CI_ENABLE_BRANCH_SEQUENCE_ID");

    verify(branchSequenceService, never())
        .deleteBranchSequence(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_FeatureFlagEnabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(true)
        .when(branchSequenceService)
        .deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    ResponseDTO<Boolean> response =
        branchSequenceResource.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(response.getData()).isTrue();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testSetBranchSequence_FeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    assertThatThrownBy(()
                           -> branchSequenceResource.setBranchSequence(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("CI_ENABLE_BRANCH_SEQUENCE_ID");

    verify(branchSequenceService, never())
        .setBranchSequence(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(100L));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testSetBranchSequence_FeatureFlagEnabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(100)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceService)
        .setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    ResponseDTO<BranchSequenceDTO> response =
        branchSequenceResource.setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    assertThat(response.getData().getSequenceId()).isEqualTo(100);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_NotFound() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(Optional.empty())
        .when(branchSequenceService)
        .getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThatThrownBy(
        () -> branchSequenceResource.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH))
        .isInstanceOf(io.harness.exception.EntityNotFoundException.class)
        .hasMessageContaining("No branch sequence found");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_NotFound() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(false)
        .when(branchSequenceService)
        .deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThatThrownBy(()
                           -> branchSequenceResource.deleteBranchSequence(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH))
        .isInstanceOf(io.harness.exception.EntityNotFoundException.class)
        .hasMessageContaining("No branch sequence found");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testSetBranchSequence_InvalidInput() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(null)
        .when(branchSequenceService)
        .setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    assertThatThrownBy(()
                           -> branchSequenceResource.setBranchSequence(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to set branch sequence");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testListBranchSequences_EmptyList() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(java.util.Collections.emptyList())
        .when(branchSequenceService)
        .getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    ResponseDTO<List<BranchSequenceDTO>> response =
        branchSequenceResource.listBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(response.getData()).isEmpty();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequences_ZeroDeleted() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(0L).when(branchSequenceService).deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    ResponseDTO<Long> response =
        branchSequenceResource.deleteBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(response.getData()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testAccessControlClient_VerifyViewPermissionCalled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(java.util.Collections.emptyList())
        .when(branchSequenceService)
        .getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    branchSequenceResource.listBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    verify(accessControlClient).checkForAccessOrThrow(any(), any(), eq("core_pipeline_view"));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testAccessControlClient_VerifyDeletePermissionCalled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);
    doReturn(5L).when(branchSequenceService).deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    branchSequenceResource.deleteBranchSequences(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    verify(accessControlClient).checkForAccessOrThrow(any(), any(), eq("core_pipeline_delete"));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testAccessControlClient_VerifyEditPermissionCalled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID);

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(100)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceService)
        .setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    branchSequenceResource.setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    verify(accessControlClient).checkForAccessOrThrow(any(), any(), eq("core_pipeline_edit"));
  }
}
