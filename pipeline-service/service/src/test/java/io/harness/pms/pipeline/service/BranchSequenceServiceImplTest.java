/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.pipeline.branchsequence.BranchSequenceResult;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.repositories.branchsequence.PipelineBranchSequenceRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class BranchSequenceServiceImplTest extends CategoryTest {
  @Mock private PipelineBranchSequenceRepository branchSequenceRepository;
  @Mock private ConnectorResourceClient connectorResourceClient;

  private BranchSequenceServiceImpl branchSequenceService;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String REPO_URL = "https://github.com/harness/harness-core.git";
  private static final String NORMALIZED_REPO_URL = "github.com/harness/harness-core";
  private static final String BRANCH = "main";
  private static final String PARENT_UNIQUE_ID = "parentUniqueId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    HarnessCodeServiceConfig harnessCodeServiceConfig =
        HarnessCodeServiceConfig.builder().gitUrl("https://git.harness.io").build();
    branchSequenceService =
        new BranchSequenceServiceImpl(branchSequenceRepository, connectorResourceClient, harnessCodeServiceConfig);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_Success() {
    PipelineBranchSequence expectedSequence = PipelineBranchSequence.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .pipelineIdentifier(PIPELINE_ID)
                                                  .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                                  .branch(BRANCH)
                                                  .sequenceId(5)
                                                  .build();

    doReturn(expectedSequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, PARENT_UNIQUE_ID);

    long result = branchSequenceService.incrementBranchSequence(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, PARENT_UNIQUE_ID);

    assertThat(result).isEqualTo(5L);
    verify(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_NullRepoUrl() {
    long result =
        branchSequenceService.incrementBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null, BRANCH, null);

    assertThat(result).isEqualTo(0L);
    verify(branchSequenceRepository, never())
        .incrementAndGet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_EmptyBranch() {
    long result =
        branchSequenceService.incrementBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, "", null);

    assertThat(result).isEqualTo(0L);
    verify(branchSequenceRepository, never())
        .incrementAndGet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_RepositoryReturnsNull() {
    doReturn(null)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, null);

    long result = branchSequenceService.incrementBranchSequence(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, null);

    assertThat(result).isEqualTo(0L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_Success() {
    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(10)
                                          .build();

    doReturn(Optional.of(sequence))
        .when(branchSequenceRepository)
        .getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH);

    Optional<Long> result =
        branchSequenceService.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(10L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_NotFound() {
    doReturn(Optional.empty())
        .when(branchSequenceRepository)
        .getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH);

    Optional<Long> result =
        branchSequenceService.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetBranchSequence_InvalidInput() {
    Optional<Long> result =
        branchSequenceService.getBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null, BRANCH);

    assertThat(result).isEmpty();
    verify(branchSequenceRepository, never())
        .getBranchSequence(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteAllForPipeline() {
    doReturn(5L).when(branchSequenceRepository).deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    long result = branchSequenceService.deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(result).isEqualTo(5L);
    verify(branchSequenceRepository).deleteAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetAllForPipeline() {
    List<PipelineBranchSequence> sequences = Arrays.asList(PipelineBranchSequence.builder()
                                                               .accountIdentifier(ACCOUNT_ID)
                                                               .orgIdentifier(ORG_ID)
                                                               .projectIdentifier(PROJECT_ID)
                                                               .pipelineIdentifier(PIPELINE_ID)
                                                               .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                                               .branch("main")
                                                               .sequenceId(5)
                                                               .build(),
        PipelineBranchSequence.builder()
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .normalizedRepoUrl(NORMALIZED_REPO_URL)
            .branch("develop")
            .sequenceId(3)
            .build());

    doReturn(sequences).when(branchSequenceRepository).getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    List<PipelineBranchSequence> result =
        branchSequenceService.getAllForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getBranch()).isEqualTo("main");
    assertThat(result.get(1).getBranch()).isEqualTo("develop");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_Success() {
    doReturn(true)
        .when(branchSequenceRepository)
        .deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH);

    boolean result =
        branchSequenceService.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isTrue();
    verify(branchSequenceRepository)
        .deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_NotFound() {
    doReturn(false)
        .when(branchSequenceRepository)
        .deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH);

    boolean result =
        branchSequenceService.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDeleteBranchSequence_InvalidInput() {
    boolean result =
        branchSequenceService.deleteBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", BRANCH);

    assertThat(result).isFalse();
    verify(branchSequenceRepository, never())
        .deleteBranchSequence(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testSetBranchSequence_Success() {
    PipelineBranchSequence expectedSequence = PipelineBranchSequence.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .pipelineIdentifier(PIPELINE_ID)
                                                  .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                                  .branch(BRANCH)
                                                  .sequenceId(100)
                                                  .build();

    doReturn(expectedSequence)
        .when(branchSequenceRepository)
        .setSequenceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, 100L);

    PipelineBranchSequence result =
        branchSequenceService.setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, BRANCH, 100L);

    assertThat(result).isNotNull();
    assertThat(result.getSequenceId()).isEqualTo(100L);
    verify(branchSequenceRepository)
        .setSequenceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, 100L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testSetBranchSequence_InvalidInput() {
    PipelineBranchSequence result =
        branchSequenceService.setBranchSequence(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null, BRANCH, 100L);

    assertThat(result).isNull();
    verify(branchSequenceRepository, never())
        .setSequenceId(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(100L));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromTriggerPayload_NullPayload() {
    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromTriggerPayload_NoPayload() {
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, triggerPayload, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromTriggerPayload_PushEvent() {
    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PushHook pushHook =
        io.harness.product.ci.scm.proto.PushHook.newBuilder().setRef("refs/heads/main").setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPush(pushHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(1)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, PARENT_UNIQUE_ID);

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, triggerPayload, PARENT_UNIQUE_ID);

    assertThat(result).isNotNull();
    assertThat(result.getBranchSeqId()).isEqualTo(1L);
    assertThat(result.getNormalizedBranch()).isEqualTo(BRANCH);
    assertThat(result.getNormalizedRepoUrl()).isEqualTo(NORMALIZED_REPO_URL);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromTriggerPayload_PREvent() {
    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PullRequest pr =
        io.harness.product.ci.scm.proto.PullRequest.newBuilder().setSource("feature/test").build();
    io.harness.product.ci.scm.proto.PullRequestHook prHook =
        io.harness.product.ci.scm.proto.PullRequestHook.newBuilder().setPr(pr).setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPr(prHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch("feature/test")
                                          .sequenceId(2)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, "feature/test", null);

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, triggerPayload, null);

    assertThat(result).isNotNull();
    assertThat(result.getBranchSeqId()).isEqualTo(2L);
    assertThat(result.getNormalizedBranch()).isEqualTo("feature/test");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromTriggerPayload_TagEvent_ReturnsNull() {
    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PushHook pushHook =
        io.harness.product.ci.scm.proto.PushHook.newBuilder().setRef("refs/tags/v1.0.0").setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPush(pushHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromTriggerPayload(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, triggerPayload, null);

    assertThat(result).isNull();
    verify(branchSequenceRepository, never())
        .incrementAndGet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_Success() {
    String processedYaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: account.github\n"
        + "        repoName: harness-core\n"
        + "        build:\n"
        + "          type: branch\n"
        + "          spec:\n"
        + "            branch: main\n";

    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PushHook pushHook =
        io.harness.product.ci.scm.proto.PushHook.newBuilder().setRef("refs/heads/main").setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPush(pushHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(3)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, null);

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, triggerPayload, null);

    assertThat(result).isNotNull();
    assertThat(result.getBranchSeqId()).isEqualTo(3L);
    assertThat(result.getNormalizedBranch()).isEqualTo(BRANCH);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_EmptyYaml() {
    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_NullYaml() {
    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null, null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_NoCodebase_NoTriggerPayload() {
    String processedYaml = "pipeline:\n"
        + "  name: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_NoCodebase_WithTriggerPayload() {
    // When clone codebase is disabled (no codebase config in YAML) but trigger payload has branch/repo data
    String processedYaml = "pipeline:\n"
        + "  name: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PushHook pushHook =
        io.harness.product.ci.scm.proto.PushHook.newBuilder().setRef("refs/heads/main").setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPush(pushHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch(BRANCH)
                                          .sequenceId(1)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, null);

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, triggerPayload, null);

    assertThat(result).isNotNull();
    assertThat(result.getBranchSeqId()).isEqualTo(1L);
    assertThat(result.getNormalizedBranch()).isEqualTo(BRANCH);
    assertThat(result.getNormalizedRepoUrl()).isEqualTo(NORMALIZED_REPO_URL);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_EmptyYaml_WithTriggerPayload() {
    // When processedYaml is empty but trigger payload has branch/repo data
    io.harness.product.ci.scm.proto.Repository repo =
        io.harness.product.ci.scm.proto.Repository.newBuilder().setLink(REPO_URL).build();
    io.harness.product.ci.scm.proto.PullRequest pr =
        io.harness.product.ci.scm.proto.PullRequest.newBuilder().setSource("feature/test").build();
    io.harness.product.ci.scm.proto.PullRequestHook prHook =
        io.harness.product.ci.scm.proto.PullRequestHook.newBuilder().setPr(pr).setRepo(repo).build();
    ParsedPayload parsedPayload = ParsedPayload.newBuilder().setPr(prHook).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();

    PipelineBranchSequence sequence = PipelineBranchSequence.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .pipelineIdentifier(PIPELINE_ID)
                                          .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                          .branch("feature/test")
                                          .sequenceId(2)
                                          .build();

    doReturn(sequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, "feature/test", null);

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", triggerPayload, null);

    assertThat(result).isNotNull();
    assertThat(result.getBranchSeqId()).isEqualTo(2L);
    assertThat(result.getNormalizedBranch()).isEqualTo("feature/test");
    assertThat(result.getNormalizedRepoUrl()).isEqualTo(NORMALIZED_REPO_URL);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_NoCodebase_TriggerPayloadWithoutParsedPayload() {
    // When clone codebase is disabled and trigger payload exists but without parsed payload
    String processedYaml = "pipeline:\n"
        + "  name: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, triggerPayload, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_ExpressionInBranch() {
    String processedYaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: account.github\n"
        + "        build:\n"
        + "          type: branch\n"
        + "          spec:\n"
        + "            branch: <+trigger.branch>\n";

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceFromProcessedYaml_TagBuild() {
    String processedYaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: account.github\n"
        + "        build:\n"
        + "          type: tag\n"
        + "          spec:\n"
        + "            tag: v1.0.0\n";

    BranchSequenceResult result = branchSequenceService.incrementBranchSequenceFromProcessedYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, processedYaml, null, null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_SshUrl() {
    String sshUrl = "git@github.com:harness/harness-core.git";

    PipelineBranchSequence expectedSequence = PipelineBranchSequence.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .pipelineIdentifier(PIPELINE_ID)
                                                  .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                                  .branch(BRANCH)
                                                  .sequenceId(1)
                                                  .build();

    doReturn(expectedSequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, BRANCH, null);

    long result = branchSequenceService.incrementBranchSequence(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, sshUrl, BRANCH, null);

    assertThat(result).isEqualTo(1L);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIncrementBranchSequence_RefsHeadsBranch() {
    PipelineBranchSequence expectedSequence = PipelineBranchSequence.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_ID)
                                                  .projectIdentifier(PROJECT_ID)
                                                  .pipelineIdentifier(PIPELINE_ID)
                                                  .normalizedRepoUrl(NORMALIZED_REPO_URL)
                                                  .branch("feature/test")
                                                  .sequenceId(1)
                                                  .build();

    doReturn(expectedSequence)
        .when(branchSequenceRepository)
        .incrementAndGet(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, NORMALIZED_REPO_URL, "feature/test", null);

    long result = branchSequenceService.incrementBranchSequence(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, REPO_URL, "refs/heads/feature/test", null);

    assertThat(result).isEqualTo(1L);
  }
}
