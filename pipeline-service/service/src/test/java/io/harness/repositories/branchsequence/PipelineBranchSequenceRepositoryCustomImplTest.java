/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.ABHAY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.rule.Owner;

import com.mongodb.MongoSocketOpenException;
import com.mongodb.ServerAddress;
import com.mongodb.client.result.DeleteResult;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CI)
public class PipelineBranchSequenceRepositoryCustomImplTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;

  private PipelineBranchSequenceRepositoryCustomImpl repository;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String REPO_URL = "github.com/harness/harness-core";
  private static final String BRANCH = "main";
  private static final String PARENT_UNIQUE_ID = "parentUniqueId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    repository = new PipelineBranchSequenceRepositoryCustomImpl(mongoTemplate);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_Success() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(1)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, PARENT_UNIQUE_ID);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(1L);
    verify(mongoTemplate, times(1))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_DuplicateKeyException_RetriesAndSucceeds() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(2)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new DuplicateKeyException("E11000 duplicate key error"))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, PARENT_UNIQUE_ID);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(2L);
    verify(mongoTemplate, times(2))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_DuplicateKeyException_AllRetriesExhausted() {
    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

    assertThatThrownBy(()
                           -> repository.incrementAndGet(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, PARENT_UNIQUE_ID))
        .isInstanceOf(DuplicateKeyException.class);

    verify(mongoTemplate, times(3))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_WithoutParentUniqueId() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(1)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, null);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testSetSequenceId_Success() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(100)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.setSequenceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(100L);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testSetSequenceId_DuplicateKeyException_RetriesAndSucceeds() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(50)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new DuplicateKeyException("E11000 duplicate key error"))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.setSequenceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 50L);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(50L);
    verify(mongoTemplate, times(2))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetBranchSequence_Found() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(5)
                                          .build();

    when(mongoTemplate.findOne(any(Query.class), eq(PipelineBranchSequence.class))).thenReturn(expected);

    Optional<PipelineBranchSequence> result =
        repository.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isPresent();
    assertThat(result.get().getSequenceId()).isEqualTo(5L);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetBranchSequence_NotFound() {
    when(mongoTemplate.findOne(any(Query.class), eq(PipelineBranchSequence.class))).thenReturn(null);

    Optional<PipelineBranchSequence> result =
        repository.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testDeleteAllForPipeline() {
    when(mongoTemplate.remove(any(Query.class), eq(PipelineBranchSequence.class)))
        .thenReturn(DeleteResult.acknowledged(3));

    long result = repository.deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(result).isEqualTo(3L);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetAllForPipeline() {
    List<PipelineBranchSequence> expected = Arrays.asList(PipelineBranchSequence.builder()
                                                              .accountIdentifier(ACCOUNT_ID)
                                                              .orgIdentifier(ORG_ID)
                                                              .projectIdentifier(PROJECT_ID)
                                                              .pipelineIdentifier(PIPELINE_ID)
                                                              .normalizedRepoUrl(REPO_URL)
                                                              .branch("main")
                                                              .sequenceId(5)
                                                              .build(),
        PipelineBranchSequence.builder()
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .normalizedRepoUrl(REPO_URL)
            .branch("develop")
            .sequenceId(3)
            .build());

    when(mongoTemplate.find(any(Query.class), eq(PipelineBranchSequence.class))).thenReturn(expected);

    List<PipelineBranchSequence> result = repository.getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(result).hasSize(2);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_Success() {
    when(mongoTemplate.remove(any(Query.class), eq(PipelineBranchSequence.class)))
        .thenReturn(DeleteResult.acknowledged(1));

    boolean result = repository.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_NotFound() {
    when(mongoTemplate.remove(any(Query.class), eq(PipelineBranchSequence.class)))
        .thenReturn(DeleteResult.acknowledged(0));

    boolean result = repository.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_SocketException_RetriesAndSucceeds() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(3)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new MongoSocketOpenException("Connection refused", new ServerAddress()))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, null);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(3L);
    verify(mongoTemplate, times(2))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_OptimisticLockingException_RetriesAndSucceeds() {
    PipelineBranchSequence expected = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(4)
                                          .build();

    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new OptimisticLockingFailureException("Version mismatch"))
        .thenReturn(expected);

    PipelineBranchSequence result =
        repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, null);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(4L);
    verify(mongoTemplate, times(2))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testIncrementAndGet_NonRetryableException_FailsImmediately() {
    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class)))
        .thenThrow(new IllegalStateException("Unexpected error"));

    PipelineBranchSequence result = null;
    try {
      result =
          repository.incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, PARENT_UNIQUE_ID);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
    }

    verify(mongoTemplate, times(1))
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PipelineBranchSequence.class));
  }
}
