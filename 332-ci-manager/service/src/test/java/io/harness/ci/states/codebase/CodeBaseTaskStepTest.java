/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ABHIJEET_GUPTA;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SIDDHARTHA_ROY;
import static io.harness.rule.OwnerRule.TAPAN;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VIVEK_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.DeleteType;
import io.harness.beans.execution.DeleteWebhookEvent;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CodeBaseTaskStepTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @InjectMocks CodeBaseTaskStep codeBaseTaskStep;
  private Ambiance ambiance;
  private StepInputPackage stepInputPackage;

  @Mock private ScmGitRefManager scmGitRefManager;

  @Mock private CIFeatureFlagService featureFlagService;

  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;

  @Mock private ExceptionManager exceptionManager;

  @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ambiance = Ambiance.newBuilder()
                   .setStageExecutionId("stageExecutionId")
                   .addLevels(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build())
                   .addLevels(Level.newBuilder().setStepType(IntegrationStageStepPMS.STEP_TYPE).build())
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")
                   .build();
    stepInputPackage = StepInputPackage.builder().build();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainTaskForBranchBuilds() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().branch("main").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("http://github.com/octocat/")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();

    ScmGitRefTaskParams taskParams =
        codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, "hello-world");
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getBranch()).isEqualTo("main");
    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(taskParams.getGitRefType()).isEqualTo(GitRefType.LATEST_COMMIT_ID);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainTaskForPRBuilds() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().prNumber("1").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .url("http://github.com/octocat/hello-world")
                                                                 .build())
                                            .build();

    ScmGitRefTaskParams taskParams = codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, null);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getPrNumber()).isEqualTo(1);
    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(taskParams.getGitRefType()).isEqualTo(GitRefType.PULL_REQUEST_WITH_COMMITS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildCommitShaCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .branch("main")
            .repoUrl("http://github.com/octocat/hello-world")
            .getLatestCommitResponse(GetLatestCommitResponse.newBuilder()
                                         .setCommit(Commit.newBuilder()
                                                        .setSha("commitId")
                                                        .setAuthor(Signature.newBuilder()
                                                                       .setLogin("login")
                                                                       .setAvatar("avatar")
                                                                       .setName("name")
                                                                       .setEmail("email")
                                                                       .build())
                                                        .build())
                                         .setCommitId("commitId")
                                         .build()
                                         .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildCommitShaCodebaseSweepingOutput(scmGitRefTaskResponseData, null);
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("login");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("name");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("email");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("avatar");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/heads/main");
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("branch");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldBuildCommitShaCodebaseSweepingOutputFromTag() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .repoUrl("http://github.com/octocat/hello-world")
            .getLatestCommitResponse(GetLatestCommitResponse.newBuilder()
                                         .setCommit(Commit.newBuilder()
                                                        .setSha("commitId")
                                                        .setAuthor(Signature.newBuilder()
                                                                       .setLogin("login")
                                                                       .setAvatar("avatar")
                                                                       .setName("name")
                                                                       .setEmail("email")
                                                                       .build())
                                                        .build())
                                         .setCommitId("commitId")
                                         .build()
                                         .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildCommitShaCodebaseSweepingOutput(scmGitRefTaskResponseData, "tag");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("login");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("name");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("email");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("avatar");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/tags/tag");
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("tag");
    assertThat(codebaseSweepingOutput.getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPRCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .branch("main")
            .repoUrl("http://github.com/octocat/hello-world")
            .findPRResponse(FindPRResponse.newBuilder()
                                .setPr(PullRequest.newBuilder()
                                           .setTarget("main")
                                           .setSource("feature/abc")
                                           .setNumber(1)
                                           .setTitle("Title")
                                           .setSha("commitId")
                                           .setRef("ref")
                                           .setBase(Reference.newBuilder().setSha("commitIdBase").build())
                                           .setAuthor(User.newBuilder()
                                                          .setName("First Last")
                                                          .setEmail("first.last@email.com")
                                                          .setAvatar("http://...")
                                                          .setLogin("firstLast")
                                                          .build())
                                           .setLink("http://github.com/octocat/hello-world/pull/1")
                                           .setClosed(false)
                                           .setMerged(false)
                                           .setMergeSha("mergeSha")
                                           .build())
                                .build()
                                .toByteArray())
            .listCommitsInPRResponse(
                ListCommitsInPRResponse.newBuilder()
                    .addCommits(Commit.newBuilder()
                                    .setSha("commitId")
                                    .setMessage("message")
                                    .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                    .setCommitter(Signature.newBuilder()
                                                      .setDate(Timestamp.newBuilder().setSeconds(123123123).build())
                                                      .build())
                                    .setAuthor(Signature.newBuilder()
                                                   .setName("First Last")
                                                   .setEmail("first.last@email.com")
                                                   .setAvatar("http://...")
                                                   .setLogin("firstLast")
                                                   .build())
                                    .build())
                    .build()
                    .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildPRCodebaseSweepingOutput(scmGitRefTaskResponseData);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("feature/abc");
    assertThat(codebaseSweepingOutput.getPrNumber()).isEqualTo("1");
    assertThat(codebaseSweepingOutput.getPrTitle()).isEqualTo("Title");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBaseCommitSha()).isEqualTo("commitIdBase");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("ref");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getPullRequestLink()).isEqualTo("http://github.com/octocat/hello-world/pull/1");
    assertThat(codebaseSweepingOutput.getCommits().get(0))
        .isEqualTo(CodebaseSweepingOutput.CodeBaseCommit.builder()
                       .link("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                       .id("commitId")
                       .message("message")
                       .timeStamp(123123123)
                       .ownerName("First Last")
                       .ownerEmail("first.last@email.com")
                       .ownerId("firstLast")
                       .build());
    assertThat(codebaseSweepingOutput.getState()).isEqualTo("open");
    assertThat(codebaseSweepingOutput.getMergeSha()).isEqualTo("mergeSha");

    FindPRResponse prResponse = FindPRResponse.newBuilder()
                                    .setPr(PullRequest.newBuilder()
                                               .setTarget("main")
                                               .setSource("feature/abc")
                                               .setNumber(1)
                                               .setTitle("Title")
                                               .setSha("commitId")
                                               .setRef("ref")
                                               .setBase(Reference.newBuilder().setSha("commitIdBase").build())
                                               .setAuthor(User.newBuilder().setAvatar("http://...").build())
                                               .setLink("http://github.com/octocat/hello-world/pull/1")
                                               .setClosed(false)
                                               .setMerged(false)
                                               .setMergeSha("mergeSha")
                                               .build())
                                    .build();

    scmGitRefTaskResponseData.setFindPRResponse(prResponse.toByteArray());
    codebaseSweepingOutput = codeBaseTaskStep.buildPRCodebaseSweepingOutput(scmGitRefTaskResponseData);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildManualCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().branch("main").build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildManualCodebaseSweepingOutput(manualExecutionSource, "url");
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPRWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(PRWebhookEvent.builder()
                              .sourceBranch("feature/abc")
                              .targetBranch("main")
                              .pullRequestId(1L)
                              .title("Title")
                              .pullRequestLink("http://github.com/octocat/hello-world/pull/1")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .mergeSha("mergeSha")
                                                  .build())
                              .commitDetailsList(Arrays.asList(
                                  CommitDetails.builder().message("First commit message").timeStamp(110).build(),
                                  CommitDetails.builder().message("Last commit message").timeStamp(120).build()))
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("feature/abc");
    assertThat(codebaseSweepingOutput.getPrNumber()).isEqualTo("1");
    assertThat(codebaseSweepingOutput.getPrTitle()).isEqualTo("Title");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBaseCommitSha()).isEqualTo("commitIdBase");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getPullRequestLink()).isEqualTo("http://github.com/octocat/hello-world/pull/1");
    assertThat(codebaseSweepingOutput.getMergeSha()).isEqualTo("mergeSha");
    assertThat(codebaseSweepingOutput.getCommitMessage()).isEqualTo("Last commit message");

    PRWebhookEvent prWebhookEvent =
        PRWebhookEvent.builder()
            .sourceBranch("feature/abc")
            .targetBranch("main")
            .pullRequestId(1L)
            .title("Title")
            .pullRequestLink("http://github.com/octocat/hello-world/pull/1")
            .baseAttributes(WebhookBaseAttributes.builder()
                                .after("commitId")
                                .before("commitIdBase")
                                .authorAvatar("http://...")
                                .mergeSha("mergeSha")
                                .build())
            .commitDetailsList(
                Arrays.asList(CommitDetails.builder().message("First commit message").timeStamp(110).build(),
                    CommitDetails.builder()
                        .message("Last commit message")
                        .ownerId("firstLast")
                        .ownerEmail("first.last@email.com")
                        .ownerName("First Last")
                        .timeStamp(120)
                        .build()))
            .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
            .build();
    webhookExecutionSource = WebhookExecutionSource.builder().webhookEvent(prWebhookEvent).build();
    codebaseSweepingOutput = codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPushWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(
                BranchWebhookEvent.builder()
                    .branchName("main")
                    .baseAttributes(WebhookBaseAttributes.builder()
                                        .after("commitId")
                                        .before("commitIdBase")
                                        .authorName("First Last")
                                        .authorEmail("first.last@email.com")
                                        .authorAvatar("http://...")
                                        .authorLogin("firstLast")
                                        .ref("refs/heads/main")
                                        .build())
                    .commitDetailsList(Arrays.asList(CommitDetails.builder().message("Last commit message").build()))
                    .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                    .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getCommitMessage()).isEqualTo("Last commit message");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/heads/main");
    // Regression guard on the shared BRANCH arm every push-triggered CI build flows through: a plain push
    // must never be mistaken for a merge queue event.
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("branch");
    // A true flag here would send every push build down the merge queue clone path.
    assertThat(codebaseSweepingOutput.isMergeQueue()).isFalse();
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void shouldBuildMergeQueueWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(BranchWebhookEvent.builder()
                              .branchName("main")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("speculativeMergeSha")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .action("checks_requested")
                                                  .build())
                              .commitDetailsList(Arrays.asList())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    // The whole point of this fix: the build type flips to CommitSha so the clone plugin fetches the
    // speculative merge commit by SHA instead of the (unreachable) target branch tip.
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("CommitSha");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("speculativeMergeSha");
    // The clone runs long after the webhook payload is out of scope, so the merge queue nature of the build has
    // to be carried on the sweeping output rather than re-derived from the action string down there.
    assertThat(codebaseSweepingOutput.isMergeQueue()).isTrue();
    // Branch fields stay populated so <+codebase.branch> keeps resolving for a merge queue trigger.
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    // No ref is supplied by the provider today (checks_requested carries no `ref`), so commitRef must stay
    // empty rather than pointing at something unrelated - the ref-additive half of the design is a no-op
    // until a provider actually supplies one.
    assertThat(codebaseSweepingOutput.getCommitRef()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void shouldPassThroughCommitRefForMergeQueueWebhookWhenProviderSuppliesOne() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(BranchWebhookEvent.builder()
                              .branchName("main")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("speculativeMergeSha")
                                                  .action("checks_requested")
                                                  .ref("refs/heads/gh-readonly-queue/main/pr-1-abc123")
                                                  .build())
                              .commitDetailsList(Arrays.asList())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("CommitSha");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/heads/gh-readonly-queue/main/pr-1-abc123");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void shouldBuildDeleteWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(DeleteWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .build())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .deleteType(DeleteType.BRANCH_DELETE)
                              .ref("refs/heads/branch")
                              .build())

            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("branch");

    webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(DeleteWebhookEvent.builder()
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .build())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .deleteType(DeleteType.TAG_DELETE)
                              .ref("refs/tags/tag")
                              .build())
            .build();
    codebaseSweepingOutput = codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldBuildReleaseWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(ReleaseWebhookEvent.builder()
                              .releaseTag("1.1")
                              .releaseBody("releaseBody")
                              .releaseLink("LinkToRelease")
                              .title("releaseTitle")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .build())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getReleaseTag()).isEqualTo("1.1");
    assertThat(codebaseSweepingOutput.getReleaseBody()).isEqualTo("releaseBody");
    assertThat(codebaseSweepingOutput.getReleaseLink()).isEqualTo("LinkToRelease");
    assertThat(codebaseSweepingOutput.getReleaseTitle()).isEqualTo("releaseTitle");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldFailPRBuildWhenAPIAccessDisabled() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().prNumber("12").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("http://github.com/octocat/")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(connectorDetails)).thenReturn(false);
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    CodeBaseTaskStepParameters codeBaseTaskStepParameters =
        CodeBaseTaskStepParameters.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .repoName(ParameterField.createValueField("repoName"))
            .executionSource(executionSource)
            .build();
    when(exceptionManager.processException(any())).thenAnswer(invocation -> invocation.getArgument(0));
    StepResponse stepResponse =
        codeBaseTaskStep.executeSync(ambiance, codeBaseTaskStepParameters, stepInputPackage, null);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(ErrorCode.GENERAL_ERROR.name());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getLevel())
        .isEqualTo(io.harness.eraro.Level.ERROR.name());
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isNotBlank();
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypes(0))
        .isEqualTo(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testStepResponseInCaseOfFetchMetadataFailure() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().prNumber("12").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("http://github.com/octocat/")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();
    when(connectorUtils.getConnectorDetailsWithToken(any(), any(), eq(true), any(), any()))
        .thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(connectorDetails)).thenReturn(true);
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(scmGitRefManager.fetchCodebaseMetadata(any(), any(), any(), any(), any(), any()))
        .thenThrow(new CIStageExecutionException("Failed to fetch codebase metadata"));

    CodeBaseTaskStepParameters codeBaseTaskStepParameters =
        CodeBaseTaskStepParameters.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .repoName(ParameterField.createValueField("repoName"))
            .executionSource(executionSource)
            .build();
    StepResponse stepResponse =
        codeBaseTaskStep.executeSync(ambiance, codeBaseTaskStepParameters, stepInputPackage, null);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(ErrorCode.GENERAL_ERROR.name());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getLevel())
        .isEqualTo(io.harness.eraro.Level.ERROR.name());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypes(0))
        .isEqualTo(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void shouldBuildCommitShaCloningCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    FindCommitResponse findCommitResponse =
        FindCommitResponse.newBuilder()
            .setCommit(Commit.newBuilder().setSha("1234").setMessage("message").setLink("link").setAuthor(
                Signature.newBuilder().setAvatar("Avatar").setEmail("email").setName("Name").build()))
            .build();

    String expectedRepoUrl = "https://github.com/org/repo.git";
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = ScmGitRefTaskResponseData.builder()
                                                              .findCommitResponse(findCommitResponse.toByteArray())
                                                              .gitRefType(GitRefType.FIND_COMMIT)
                                                              .repoUrl(expectedRepoUrl)
                                                              .build();

    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildCommitShaCloneCodebaseSweepingOutput(scmGitRefTaskResponseData);
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("1234");
    assertThat(codebaseSweepingOutput.getCommitMessage()).isEqualTo("message");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("email");
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("commitSha");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo(expectedRepoUrl);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("Name");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("Avatar");
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void repoUrlFromResponseForCommitShaClone() throws InvalidProtocolBufferException {
    final String repoUrlFromResponse = "https://gitlab.com/group/repo.git";

    FindCommitResponse findCommitResponse = FindCommitResponse.newBuilder()
                                                .setCommit(Commit.newBuilder()
                                                               .setSha("abcdef0123456789")
                                                               .setMessage("msg")
                                                               .setLink("")
                                                               .setAuthor(Signature.newBuilder()
                                                                              .setAvatar("avatar")
                                                                              .setEmail("author@email.com")
                                                                              .setName("Author Name")
                                                                              .setLogin("login")
                                                                              .build())
                                                               .build())
                                                .build();

    ScmGitRefTaskResponseData resp = ScmGitRefTaskResponseData.builder()
                                         .findCommitResponse(findCommitResponse.toByteArray())
                                         .gitRefType(GitRefType.FIND_COMMIT)
                                         .repoUrl(repoUrlFromResponse)
                                         .build();

    CodebaseSweepingOutput out = codeBaseTaskStep.buildCommitShaCloneCodebaseSweepingOutput(resp);

    assertThat(out.getRepoUrl()).isEqualTo(repoUrlFromResponse);
    assertThat(out.getCommitSha()).isEqualTo("abcdef0123456789");
    assertThat(out.getCommitMessage()).isEqualTo("msg");
    assertThat(out.getGitUser()).isEqualTo("Author Name");
    assertThat(out.getGitUserEmail()).isEqualTo("author@email.com");
    assertThat(out.getGitUserAvatar()).isEqualTo("avatar");
    assertThat(out.getGitUserId()).isEqualTo("login");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetGitRefType_WithBranch() {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().branch("main").build();
    GitRefType result = codeBaseTaskStep.getGitRefType(manualExecutionSource);
    assertThat(result).isEqualTo(GitRefType.LATEST_COMMIT_ID);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetGitRefType_WithPrNumber() {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().prNumber("123").build();
    GitRefType result = codeBaseTaskStep.getGitRefType(manualExecutionSource);
    assertThat(result).isEqualTo(GitRefType.PULL_REQUEST_WITH_COMMITS);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetGitRefType_WithTag() {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().tag("v1.0.0").build();
    GitRefType result = codeBaseTaskStep.getGitRefType(manualExecutionSource);
    assertThat(result).isEqualTo(GitRefType.LATEST_COMMIT_ID);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetGitRefType_WithCommitSha() {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().commitSha("abc123").build();
    GitRefType result = codeBaseTaskStep.getGitRefType(manualExecutionSource);
    assertThat(result).isEqualTo(GitRefType.FIND_COMMIT);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetGitRefType_WithNoFields_ThrowsException() {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().build();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> codeBaseTaskStep.getGitRefType(manualExecutionSource))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Manual codebase git task needs one of PR number, commitSha, branch or tag");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testParseRunnerResponse_LatestCommitId() throws Exception {
    // Create GetLatestCommitResponse-like data as JsonNode
    com.fasterxml.jackson.databind.ObjectMapper testMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.node.ObjectNode commitData = testMapper.createObjectNode();
    com.fasterxml.jackson.databind.node.ObjectNode commit = testMapper.createObjectNode();
    commit.put("sha", "abc123def456");
    commit.put("message", "Test commit message");
    com.fasterxml.jackson.databind.node.ObjectNode author = testMapper.createObjectNode();
    author.put("login", "testuser");
    author.put("name", "Test User");
    author.put("email", "test@example.com");
    commit.set("author", author);
    commitData.set("commit", commit);
    commitData.put("commitId", "abc123def456");

    // Create mock ScmCGIResponse with builder
    io.harness.runnercommons.cgi.model.response.ScmCGIResponse scmCGIResponse =
        io.harness.runnercommons.cgi.model.response.ScmCGIResponse.builder()
            .status(io.harness.logging.CommandExecutionStatus.SUCCESS)
            .data(commitData)
            .build();

    // Mock the objectMapper to return JSON string
    when(objectMapper.writeValueAsString(any())).thenReturn(testMapper.writeValueAsString(commitData));

    ScmGitRefTaskResponseData result =
        codeBaseTaskStep.parseRunnerResponse(scmCGIResponse, GitRefType.LATEST_COMMIT_ID);

    assertThat(result).isNotNull();
    assertThat(result.getGitRefType()).isEqualTo(GitRefType.LATEST_COMMIT_ID);
    assertThat(result.getGetLatestCommitResponse()).isNotNull();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testParseRunnerResponse_PullRequestWithCommits_ParsesPrAndCommits() throws Exception {
    com.fasterxml.jackson.databind.ObjectMapper testMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.node.ObjectNode responseData = testMapper.createObjectNode();
    com.fasterxml.jackson.databind.node.ObjectNode pr = testMapper.createObjectNode();
    pr.put("number", 1);
    pr.put("title", "Title");
    pr.put("source", "feature");
    pr.put("target", "main");
    pr.put("sha", "commitId");
    responseData.set("pr", pr);
    responseData.put("status", 200);

    com.fasterxml.jackson.databind.node.ArrayNode commits = testMapper.createArrayNode();
    com.fasterxml.jackson.databind.node.ObjectNode commit = testMapper.createObjectNode();
    commit.put("sha", "commitId");
    commit.put("message", "commit message");
    com.fasterxml.jackson.databind.node.ObjectNode author = testMapper.createObjectNode();
    author.put("name", "Commit Author");
    author.put("email", "author@email.com");
    author.put("login", "author");
    commit.set("author", author);
    commits.add(commit);
    responseData.set("commits", commits);

    io.harness.runnercommons.cgi.model.response.ScmCGIResponse scmCGIResponse =
        io.harness.runnercommons.cgi.model.response.ScmCGIResponse.builder()
            .status(io.harness.logging.CommandExecutionStatus.SUCCESS)
            .data(responseData)
            .build();

    when(objectMapper.writeValueAsString(any())).thenReturn(testMapper.writeValueAsString(responseData));

    ScmGitRefTaskResponseData result =
        codeBaseTaskStep.parseRunnerResponse(scmCGIResponse, GitRefType.PULL_REQUEST_WITH_COMMITS);

    assertThat(result).isNotNull();
    assertThat(result.getGitRefType()).isEqualTo(GitRefType.PULL_REQUEST_WITH_COMMITS);
    assertThat(result.getFindPRResponse()).isNotNull();
    assertThat(result.getListCommitsInPRResponse()).isNotNull();

    FindPRResponse findPRResponse = FindPRResponse.parseFrom(result.getFindPRResponse());
    assertThat(findPRResponse.getPr().getNumber()).isEqualTo(1);
    assertThat(findPRResponse.getPr().getTitle()).isEqualTo("Title");

    ListCommitsInPRResponse listCommitsInPRResponse =
        ListCommitsInPRResponse.parseFrom(result.getListCommitsInPRResponse());
    assertThat(listCommitsInPRResponse.getCommitsCount()).isEqualTo(1);
    assertThat(listCommitsInPRResponse.getCommits(0).getMessage()).isEqualTo("commit message");
    assertThat(listCommitsInPRResponse.getCommits(0).getAuthor().getEmail()).isEqualTo("author@email.com");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testParseRunnerResponse_NullGitRefType_ThrowsException() {
    io.harness.runnercommons.cgi.model.response.ScmCGIResponse scmCGIResponse =
        io.harness.runnercommons.cgi.model.response.ScmCGIResponse.builder()
            .status(io.harness.logging.CommandExecutionStatus.SUCCESS)
            .build();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> codeBaseTaskStep.parseRunnerResponse(scmCGIResponse, null))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unsupported GitRefType: null");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testObtainTaskParameters_AccountLevelConnector_SetsCompleteRepoUrl() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("https://github.com/org")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();
    ManualExecutionSource executionSource = ManualExecutionSource.builder().branch("main").build();

    ScmGitRefTaskParams taskParams = codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, "myrepo");

    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("https://github.com/org/myrepo");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testObtainTaskParameters_RepoLevelConnector_UsesConnectorUrlDirectly() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("https://github.com/org/repo")
                                                                 .connectionType(GitConnectionType.REPO)
                                                                 .build())
                                            .build();
    ManualExecutionSource executionSource = ManualExecutionSource.builder().branch("main").build();

    ScmGitRefTaskParams taskParams = codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, null);

    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("https://github.com/org/repo");
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testGetScmTaskTimeoutInMillis_FFDisabled_ReturnsDefault() {
    when(featureFlagService.isEnabled(FeatureName.CI_SCM_TASK_TIMEOUT_CONFIGURABLE, "accountId")).thenReturn(false);
    long timeout = codeBaseTaskStep.getScmTaskTimeoutInMillis("accountId");
    assertThat(timeout).isEqualTo(30_000L);
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testGetScmTaskTimeoutInMillis_FFEnabled_ReturnsIncreased() {
    when(featureFlagService.isEnabled(FeatureName.CI_SCM_TASK_TIMEOUT_CONFIGURABLE, "accountId")).thenReturn(true);
    long timeout = codeBaseTaskStep.getScmTaskTimeoutInMillis("accountId");
    assertThat(timeout).isEqualTo(180_000L);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_falseWhenConnectorIsDelegateExecuted() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().executeOnDelegate(Boolean.TRUE).build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", connectorDetails)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_falseWhenFfDisabled() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().executeOnDelegate(Boolean.FALSE).build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(false);

    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", connectorDetails)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_trueWhenPlatformAndFfEnabled() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .executeOnDelegate(Boolean.FALSE)
                                            .connectorConfig(GithubConnectorDTO.builder().proxy(Boolean.TRUE).build())
                                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", connectorDetails)).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_falseWhenProxyNotTrue() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .executeOnDelegate(Boolean.FALSE)
                                            .connectorConfig(GithubConnectorDTO.builder().proxy(Boolean.FALSE).build())
                                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", connectorDetails)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_falseWhenConnectorConfigMissing() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().executeOnDelegate(Boolean.FALSE).build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", connectorDetails)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldForceHarnessCloudSelector_falseWhenConnectorDetailsNull() {
    assertThat(codeBaseTaskStep.shouldForceHarnessCloudSelector("accountId", null)).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void resolveTaskSelectors_returnsHarnessCloudWhenPrivateConnectForcesDelegate() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .executeOnDelegate(Boolean.FALSE)
                                            .connectorConfig(GithubConnectorDTO.builder().proxy(Boolean.TRUE).build())
                                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    java.util.List<io.harness.delegate.TaskSelector> selectors =
        codeBaseTaskStep.resolveTaskSelectors(ambiance, "accountId", connectorDetails);

    assertThat(selectors).hasSize(1);
    assertThat(selectors.get(0).getSelector()).isEqualTo("harness-cloud");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void resolveTaskSelectors_emptyWhenCodebaseSelectorFfOffAndNoPrivateConnect() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().executeOnDelegate(Boolean.TRUE).build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.CI_CODEBASE_SELECTOR, "accountId")).thenReturn(false);

    java.util.List<io.harness.delegate.TaskSelector> selectors =
        codeBaseTaskStep.resolveTaskSelectors(ambiance, "accountId", connectorDetails);

    assertThat(selectors).isEmpty();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void resolveTaskSelectors_privateConnectTakesPrecedenceOverCodebaseSelectorFf() {
    // Both FFs on — private-connect wins because the call must pin to harness-cloud.
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .executeOnDelegate(Boolean.FALSE)
                                            .connectorConfig(GithubConnectorDTO.builder().proxy(Boolean.TRUE).build())
                                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_CODEBASE_SELECTOR, "accountId")).thenReturn(true);

    java.util.List<io.harness.delegate.TaskSelector> selectors =
        codeBaseTaskStep.resolveTaskSelectors(ambiance, "accountId", connectorDetails);

    assertThat(selectors).hasSize(1);
    assertThat(selectors.get(0).getSelector()).isEqualTo("harness-cloud");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void resolveTaskSelectors_bothManagedHelperFlagsUseOneSharedSelector() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .executeOnDelegate(Boolean.FALSE)
                                            .connectorConfig(GithubConnectorDTO.builder().proxy(Boolean.TRUE).build())
                                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY, "accountId")).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_PRIVATE_CONNECT, "accountId")).thenReturn(true);

    java.util.List<io.harness.delegate.TaskSelector> selectors =
        codeBaseTaskStep.resolveTaskSelectors(ambiance, "accountId", connectorDetails);

    assertThat(selectors).hasSize(1);
    assertThat(selectors.get(0).getSelector()).isEqualTo("harness-cloud");
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testExtractDelegateTaskIdFromResponseDataMap() {
    String taskId = "task-abc-DEL";
    assertThat(CodeBaseTaskStep.extractDelegateTaskId(
                   ImmutableMap.of(taskId, StringNotifyResponseData.builder().data("someString").build())))
        .isEqualTo(taskId);
    assertThat(CodeBaseTaskStep.extractDelegateTaskId(ImmutableMap.of())).isNull();
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testLogScmGitRefTaskIdSkipsBlankTaskId() {
    codeBaseTaskStep.logScmGitRefTaskId(null);
    codeBaseTaskStep.logScmGitRefTaskId("");
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testLogScmGitRefTaskIdLogsNonBlankTaskId() {
    codeBaseTaskStep.logScmGitRefTaskId("task-abc-DEL");
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testOnTaskResumeLogsTaskIdFromResponseDataMap() {
    String taskId = "task-abc-DEL";
    CodeBaseTaskStepParameters parameters = CodeBaseTaskStepParameters.builder().build();
    codeBaseTaskStep.onTaskResume(
        ambiance, parameters, ImmutableMap.of(taskId, StringNotifyResponseData.builder().data("someString").build()));
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testHandleFailureInvokesTaskIdLogging() {
    TaskExecutableResponse response = TaskExecutableResponse.newBuilder().setTaskId("task-123").build();
    CodeBaseTaskStepParameters parameters = CodeBaseTaskStepParameters.builder().build();
    codeBaseTaskStep.handleFailure(ambiance, parameters, response, Collections.emptyMap());
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testHandleAbortInvokesTaskIdLogging() {
    TaskExecutableResponse response = TaskExecutableResponse.newBuilder().setTaskId("task-456").build();
    CodeBaseTaskStepParameters parameters = CodeBaseTaskStepParameters.builder().build();
    codeBaseTaskStep.handleAbort(ambiance, parameters, response, false);
  }

  @Test
  @Owner(developers = SIDDHARTHA_ROY)
  @Category(UnitTests.class)
  public void testHandleExpireInvokesTaskIdLogging() {
    TaskExecutableResponse response = TaskExecutableResponse.newBuilder().setTaskId("task-789").build();
    CodeBaseTaskStepParameters parameters = CodeBaseTaskStepParameters.builder().build();
    codeBaseTaskStep.handleExpire(ambiance, parameters, response);
  }
}
