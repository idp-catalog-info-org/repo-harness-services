/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.stateutils.buildstate;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_DEBUG;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_FETCH_TAGS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_LFS_ENABLED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_PRE_FETCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_SPARSE_CHECKOUT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_SUBMODULE_STRATEGY;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PERSIST_CREDS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST_TITLE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_OWNER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_WORKSPACE;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.AISHWARYA_LAD;
import static io.harness.rule.OwnerRule.ARCHIT_MALLIK;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.JAMES_RICKS;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.VIVEK_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.sweepingoutputs.Build;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.integrationstage.BuildEnvironmentUtils;
import io.harness.ci.execution.integrationstage.CodebaseUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.CiCodebaseUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.SubmoduleStrategy;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest(BuildEnvironmentUtils.class)
public class CodebaseUtilsTest extends CIExecutionTestBase {
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock private CiCodebaseUtils ciCodebaseUtils;
  @InjectMocks public CodebaseUtils codebaseUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private GitBuildStatusUtility gitBuildStatusUtility;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private HarnessCodeServiceConfig harnessCodeServiceConfig;
  private Ambiance ambiance;

  String accountId;

  @Before
  public void setUp() {
    on(codebaseUtils).set("connectorUtils", connectorUtils);
    on(codebaseUtils).set("executionSweepingOutputResolver", executionSweepingOutputService);
    on(codebaseUtils).set("gitBuildStatusUtility", gitBuildStatusUtility);
    on(codebaseUtils).set("harnessCodeServiceConfig", harnessCodeServiceConfig);
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", "accountId")
                   .putSetupAbstractions("projectIdentifier", "projectId")
                   .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                   .build();
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("url");
    when(featureFlagService.isEnabled(eq(FeatureName.CI_TRIM_ENV_VARIABLES), any())).thenReturn(true);
    accountId = "test-account-id";
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = AISHWARYA_LAD)
  @Category(UnitTests.class)
  public void testTrimLongRuntimeCodebaseEnvVars() {
    int MAX_ENV_VAR_LEN = 8191;
    String commitMessage = "This is a long commit message. ";
    StringBuilder commitMessageBuilder = new StringBuilder();
    while (commitMessageBuilder.length() < 1 * 1024 * 1024) {
      commitMessageBuilder.append(commitMessage);
    }
    String mockCommitMessage = new String(commitMessageBuilder.toString());
    mockCommitMessage = mockCommitMessage.substring(0, MAX_ENV_VAR_LEN);

    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("merged")
                                    .commitSha("commitSha")
                                    .mergeSha("mergeSha")
                                    .commitMessage(commitMessageBuilder.toString())
                                    .build())
                        .build());

    Mockito.mockStatic(BuildEnvironmentUtils.class);
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("https://example.com/build/123");
    when(featureFlagService.isEnabled(eq(FeatureName.CI_TRIM_ENV_VARIABLES), any())).thenReturn(true);
    PowerMockito.when(BuildEnvironmentUtils.trimEnvVar(any())).thenReturn(mockCommitMessage);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);

    String trimmedCommitMessage = runtimeCodebaseVars.get(DRONE_COMMIT_MESSAGE);
    assertThat(trimmedCommitMessage.length()).isEqualTo(1000);
  }

  @Test
  @Owner(developers = AISHWARYA_LAD)
  @Category(UnitTests.class)
  public void testSkipTrimLongRuntimeCodebaseEnvVars() {
    int MAX_ENV_VAR_LEN = 8191;
    String prTitle = "This is a long PR title. ";
    StringBuilder prTitleBuilder = new StringBuilder();
    while (prTitleBuilder.length() < 9 * 1024) {
      prTitleBuilder.append(prTitle);
    }
    String mockPrTitle = new String(prTitleBuilder.toString());
    mockPrTitle = prTitleBuilder.toString();
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("merged")
                                    .commitSha("commitSha")
                                    .mergeSha("mergeSha")
                                    .prTitle(prTitleBuilder.toString())
                                    .build())
                        .build());

    Mockito.mockStatic(BuildEnvironmentUtils.class);
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("https://example.com/build/123");
    when(featureFlagService.isEnabled(eq(FeatureName.CI_TRIM_ENV_VARIABLES), eq(accountId))).thenReturn(false);
    PowerMockito.when(BuildEnvironmentUtils.trimEnvVar(any())).thenReturn(mockPrTitle);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);

    String trimmedCommitMessage = runtimeCodebaseVars.get(DRONE_PULL_REQUEST_TITLE);
    assertThat(trimmedCommitMessage.length()).isGreaterThan(MAX_ENV_VAR_LEN);
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testCommitShaBuildTypeOverridesDroneBuildEventEvenWhenBranchIsSet() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("main")
                                    .commitSha("speculativeMergeSha")
                                    .build(new Build("CommitSha"))
                                    .build())
                        .build());

    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null, null);

    // A non-empty branch alone would set DRONE_BUILD_EVENT to "push" (see CodebaseUtils#getRuntimeCodebaseVars);
    // the CommitSha build type override, evaluated after that, must win so the clone plugin fetches by SHA.
    assertThat(runtimeCodebaseVars.get(DRONE_BUILD_EVENT)).isEqualTo("commitSha");
    assertThat(runtimeCodebaseVars.get(CI_BUILD_EVENT)).isEqualTo("commitSha");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testCommitRefComposesWithCommitShaBuildEvent() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("main")
                                    .commitSha("speculativeMergeSha")
                                    .commitRef("refs/heads/gh-readonly-queue/main/pr-1-abc123")
                                    .build(new Build("CommitSha"))
                                    .build())
                        .build());

    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null, null);

    // The ref-additive and SHA-first halves of the design are independent and must compose: a supplied ref
    // still surfaces as DRONE_COMMIT_REF even while DRONE_BUILD_EVENT drives the clone by SHA.
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("refs/heads/gh-readonly-queue/main/pr-1-abc123");
    assertThat(runtimeCodebaseVars.get(CI_COMMIT_REF)).isEqualTo("refs/heads/gh-readonly-queue/main/pr-1-abc123");
    assertThat(runtimeCodebaseVars.get(DRONE_BUILD_EVENT)).isEqualTo("commitSha");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testHarnessCodeMergeQueueFetchesTheSpeculativeShaUsingPullRequestStrategy() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("main")
                                    .commitSha("speculativeMergeSha")
                                    .mergeQueue(true)
                                    .build(new Build("CommitSha"))
                                    .build())
                        .build());

    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null, null);

    // The clone plugin's commit strategy fetches only +refs/heads/<branch> and then checks out the sha, which
    // cannot work here: the speculative merge commit is unreachable from the target branch. Its pull-request
    // strategy is the only one that fetches DRONE_COMMIT_REF, and Harness Code accepts a bare sha there.
    assertThat(runtimeCodebaseVars.get(DRONE_BUILD_EVENT)).isEqualTo("pull_request");
    // This is only a clone-plugin compatibility switch. Preserve the user-facing build event as CommitSha.
    assertThat(runtimeCodebaseVars.get(CI_BUILD_EVENT)).isEqualTo("commitSha");
    // The sha, not a ref: several speculative commits can be in flight at once and Harness Code advertises a
    // single refs/mergequeue/<branch> for the whole queue, so a ref would clone the wrong entry.
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("speculativeMergeSha");
    assertThat(runtimeCodebaseVars.get(CI_COMMIT_REF)).isEqualTo("speculativeMergeSha");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testMergeQueueShaOverridesAnySuppliedCommitRef() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("main")
                                    .commitSha("speculativeMergeSha")
                                    .commitRef("refs/mergequeue/main")
                                    .mergeQueue(true)
                                    .build(new Build("CommitSha"))
                                    .build())
                        .build());

    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null, null);

    // refs/mergequeue/main tracks the tip of the whole queue rather than the entry being checked, so even when
    // it is present it must not be what the clone fetches.
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("speculativeMergeSha");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("https://github.com/test/repo")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null);
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("git@github.com:test/test-repo.git")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null);
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo.git");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("https://github.com/test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("git@github.com:test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "test-repo");
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(
                AzureRepoConnectorDTO.builder()
                    .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                    .url("https://dev.azure.com/org/project/")
                    .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                    .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("https://dev.azure.com/org/project/_git/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(AzureRepoConnectorDTO.builder()
                                 .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                 .url("git@ssh.dev.azure.com:v3/org/project/")
                                 .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("git@ssh.dev.azure.com:v3/org/project/repo");
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorSkipClone() {
    NGAccess ngAccess = Mockito.mock(NGAccess.class);
    CodeBase codeBase = CodeBase.builder().build();
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(ngAccess, codeBase, true, null);
    assertThat(gitConnector).isNull();
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorNullCodeBase() {
    codebaseUtils.getGitConnector(null, null, false, null);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorCodebase() {
    String connectorRefValue = "myConnectorRef";
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(connectorUtils.getConnectorDetailsWithToken(any(), eq(connectorRefValue), eq(true), any(), any()))
        .thenReturn(connectorDetails);
    CodeBase codeBase = CodeBase.builder().connectorRef(ParameterField.createValueField(connectorRefValue)).build();
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(null, codeBase, false, null);
    assertThat(gitConnector).isEqualTo(connectorDetails);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnector() {
    String connectorRefValue = "myConnectorRef";
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(connectorUtils.getConnectorDetailsWithToken(any(), eq(connectorRefValue), eq(true), any(), any()))
        .thenReturn(connectorDetails);
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(null, connectorRefValue, null, null);
    assertThat(gitConnector).isEqualTo(connectorDetails);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetGitConnectorHarnessCode() {
    String connectorRefValue = null;
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.HARNESS).build();
    when(connectorUtils.fetchAuthToken(any(), any(), any(), any())).thenReturn("auth-token");
    when(connectorUtils.getHarnessConnectorDetails(any(), any(), any(), any(), any())).thenReturn(connectorDetails);
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(null, connectorRefValue, null, "reponame");
    assertThat(gitConnector).isEqualTo(connectorDetails);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesCodeBaseSkipGitClone() {
    final Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(null, null, true);
    assertThat(gitEnvVariables).isEmpty();
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesNullCodebase() {
    codebaseUtils.getGitEnvVariables(null, null, false);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesCodeBase() {
    String repoName = "myRepoName";
    String scmHostName = "github.com";
    String scmUrl = "git@" + scmHostName + ":org";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url(scmUrl)
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();
    CodeBase codeBase = CodeBase.builder().repoName(ParameterField.createValueField(repoName)).build();
    Map<String, String> expectedGitEnvVariables = new HashMap<>();
    expectedGitEnvVariables.put(DRONE_NETRC_MACHINE, scmHostName);
    expectedGitEnvVariables.put(DRONE_REMOTE_URL, scmUrl + "/" + repoName + ".git");
    when(ciCodebaseUtils.getGitEnvVariables(connectorDetails, repoName)).thenReturn(expectedGitEnvVariables);

    final Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(connectorDetails, codeBase, false);
    assertThat(gitEnvVariables.get(DRONE_NETRC_MACHINE)).isEqualTo(scmHostName);
    assertThat(gitEnvVariables.get(DRONE_REMOTE_URL)).isEqualTo(scmUrl + "/" + repoName + ".git");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetGitAdvancedVariables() {
    String repoName = "harness-core";
    HostedVmInfraYaml infra =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .build())
            .build();
    List<String> sparseList = new ArrayList<>();
    sparseList.add("folder1");
    sparseList.add("folder with space");
    CodeBase codeBase = CodeBase.builder()
                            .repoName(ParameterField.createValueField(repoName))
                            .lfs(ParameterField.createValueField(true))
                            .fetchTags(ParameterField.createValueField(false))
                            .debug(ParameterField.createValueField(true))
                            .cloneDirectory(ParameterField.createValueField("directory1"))
                            .submoduleStrategy(ParameterField.createValueField(SubmoduleStrategy.RECURSIVE))
                            .preFetchCommand(ParameterField.createValueField("echo hello\ngit config lfs.url blah"))
                            .sparseCheckout(ParameterField.createValueField(sparseList))
                            .build();

    final Map<String, String> advancedGitVars = codebaseUtils.getGitAdvancedVariables(
        codeBase, false, "https://github.com/harness/harness-core", infra, accountId, false);
    assertThat(advancedGitVars.get(DRONE_NETRC_SPARSE_CHECKOUT)).isEqualTo("folder1\nfolder with space");
    assertThat(advancedGitVars.get(DRONE_NETRC_DEBUG)).isEqualTo("true");
    assertThat(advancedGitVars.get(DRONE_NETRC_FETCH_TAGS)).isEqualTo("false");
    assertThat(advancedGitVars.get(DRONE_NETRC_PRE_FETCH)).isEqualTo("echo hello\ngit config lfs.url blah");
    assertThat(advancedGitVars.get(DRONE_NETRC_SUBMODULE_STRATEGY)).isEqualTo("recursive");
    assertThat(advancedGitVars.get(DRONE_WORKSPACE)).isEqualTo("directory1");
    assertThat(advancedGitVars.get(DRONE_NETRC_LFS_ENABLED)).isEqualTo("true");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testPersistCreds() {
    String repoName = "harness-core";
    HostedVmInfraYaml infra =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .build())
            .build();
    List<String> sparseList = new ArrayList<>();
    sparseList.add("folder1");
    sparseList.add("folder with space");
    CodeBase codeBase = CodeBase.builder()
                            .repoName(ParameterField.createValueField(repoName))
                            .lfs(ParameterField.createValueField(true))
                            .fetchTags(ParameterField.createValueField(false))
                            .debug(ParameterField.createValueField(true))
                            .cloneDirectory(ParameterField.createValueField("directory1"))
                            .submoduleStrategy(ParameterField.createValueField(SubmoduleStrategy.RECURSIVE))
                            .preFetchCommand(ParameterField.createValueField("echo hello\ngit config lfs.url blah"))
                            .sparseCheckout(ParameterField.createValueField(sparseList))
                            .persistCredentials(ParameterField.createValueField(true))
                            .build();
    final Map<String, String> advancedGitVars = codebaseUtils.getGitAdvancedVariables(
        codeBase, false, "https://github.com/harness/harness-core", infra, accountId, false);
    assertThat(advancedGitVars.get(DRONE_PERSIST_CREDS)).isEqualTo("true");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucket() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().sourceBranch("source").build())
                        .build());
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars).containsKey(DRONE_COMMIT_REF);
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/source");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketMergedPR() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("merged")
                                    .commitSha("commitSha")
                                    .mergeSha("mergeSha")
                                    .build())
                        .build());
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/target");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_SHA)).isEqualTo("mergeSha");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketUnMergedPR() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("open")
                                    .commitSha("commitSha")
                                    .prTitle("PR Title")
                                    .commitMessage("Commit Message")
                                    .build())
                        .build());
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("url");
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/source");
    assertThat(runtimeCodebaseVars.get(DRONE_BUILD_LINK)).isEqualTo("url");
    assertThat(runtimeCodebaseVars.get(DRONE_PULL_REQUEST_TITLE)).isEqualTo("PR Title");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_MESSAGE)).isEqualTo("Commit Message");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_SHA)).isEqualTo("commitSha");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketWithoutGitConnector() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().sourceBranch("source").build())
                        .build());
    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null, null);
    assertThat(runtimeCodebaseVars).doesNotContainKey(DRONE_COMMIT_REF);
  }

  @Test
  @Owner(developers = ARCHIT_MALLIK)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsWithFeatureFlagEnabled() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    String gitUserId = "testUser";
    String repoUrl = "https://github.com/testUser/testRepo.git";
    String repoOwner = "expectedOwner";
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().gitUserId(gitUserId).repoUrl(repoUrl).build())
                        .build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_DRONE_REPO_OWNER), any())).thenReturn(true);
    try (MockedStatic<GitClientHelper> gitClientHelperMock = Mockito.mockStatic(GitClientHelper.class)) {
      gitClientHelperMock.when(() -> GitClientHelper.getGitOwner(repoUrl, false)).thenReturn(repoOwner);
      final Map<String, String> runtimeCodebaseVars =
          codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
      assertThat(runtimeCodebaseVars.get(DRONE_REPO_OWNER)).isEqualTo(repoOwner);
    }
  }

  @Test
  @Owner(developers = ARCHIT_MALLIK)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsWithFeatureFlagDisabled() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    String gitUserId = "testUser";
    String repoUrl = "https://github.com/testUser/testRepo.git";
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().gitUserId(gitUserId).repoUrl(repoUrl).build())
                        .build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_DRONE_REPO_OWNER), any())).thenReturn(false);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars.get(DRONE_REPO_OWNER)).isEqualTo(gitUserId);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetDroneRepoVarWithFeatureFlagEnabled() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    String gitUserId = "testUser";
    String repoUrl = "https://github.com/expectedOwner/testRepo.git";
    String expectedRepo = "expectedOwner/testRepo";
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().gitUserId(gitUserId).repoUrl(repoUrl).build())
                        .build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_DRONE_REPO_OWNER), any())).thenReturn(true);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars.get(DRONE_REPO)).isEqualTo(expectedRepo);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetDroneRepoVarWithFeatureFlagDisabled() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    String gitUserId = "testUser";
    String repoUrl = "https://github.com/expectedOwner/testRepo.git";
    String expectedRepo = "testUser/testRepo";
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().gitUserId(gitUserId).repoUrl(repoUrl).build())
                        .build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_DRONE_REPO_OWNER), any())).thenReturn(false);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);
    assertThat(runtimeCodebaseVars.get(DRONE_REPO)).isEqualTo(expectedRepo);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsCICommitMessageReduced() {
    int MAX_ENV_VAR_LEN = 8191;
    String commitMessage = "This is a long commit message. ";
    StringBuilder commitMessageBuilder = new StringBuilder();
    while (commitMessageBuilder.length() < 1 * 1024 * 1024) {
      commitMessageBuilder.append(commitMessage);
    }
    String mockCommitMessage = new String(commitMessageBuilder.toString());
    mockCommitMessage = mockCommitMessage.substring(0, MAX_ENV_VAR_LEN);

    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("merged")
                                    .commitSha("commitSha")
                                    .mergeSha("mergeSha")
                                    .commitMessage(commitMessageBuilder.toString())
                                    .build())
                        .build());

    Mockito.mockStatic(BuildEnvironmentUtils.class);
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("https://example.com/build/123");
    when(featureFlagService.isEnabled(eq(FeatureName.CI_TRIM_ENV_VARIABLES), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_POPULATE_CI_VARIABLE), any())).thenReturn(true);
    PowerMockito.when(BuildEnvironmentUtils.trimEnvVar(any())).thenReturn(mockCommitMessage);
    final Map<String, String> runtimeCodebaseVars =
        codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);

    String droneCommitMessage = runtimeCodebaseVars.get(DRONE_COMMIT_MESSAGE);
    String ciCommitMessage = runtimeCodebaseVars.get(CI_COMMIT_MESSAGE);
    assertThat(droneCommitMessage.length()).isEqualTo(1000);
    assertThat(runtimeCodebaseVars.get("CI")).isEqualTo("true");
    assertThat(ciCommitMessage).isEqualTo(droneCommitMessage);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVars_sweepingOutputNotFound_repoConnector_populatesRepoEnvVars() {
    String repoUrl = "https://github.com/abc/harness-xyz";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().url(repoUrl).connectionType(GitConnectionType.REPO).build())
            .build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    Map<String, String> result = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);

    assertThat(result.get(CI_REPO_LINK)).isEqualTo(repoUrl);
    assertThat(result.get(DRONE_REPO_LINK)).isEqualTo(repoUrl);
    assertThat(result.get(DRONE_REPO_NAME)).isEqualTo("harness-xyz");
    assertThat(result.get(CI_REPO)).isEqualTo("harness-xyz");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVars_sweepingOutputNotFound_accountConnector_doesNotPopulateRepoEnvVars() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("https://github.com/harness")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    Map<String, String> result = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, null);

    assertThat(result).doesNotContainKey(CI_REPO_LINK);
    assertThat(result).doesNotContainKey(DRONE_REPO_LINK);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVars_sweepingOutputNotFound_accountConnector_withRepoName_populatesRepoEnvVars() {
    String baseUrl = "https://github.com/harness";
    String repoName = "harness-core";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(
                GithubConnectorDTO.builder().url(baseUrl).connectionType(GitConnectionType.ACCOUNT).build())
            .build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    Map<String, String> result = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails, repoName);

    assertThat(result.get(CI_REPO_LINK)).isEqualTo(baseUrl + "/" + repoName);
    assertThat(result.get(DRONE_REPO_LINK)).isEqualTo(baseUrl + "/" + repoName);
    assertThat(result.get(DRONE_REPO_NAME)).isEqualTo(repoName);
    assertThat(result.get(CI_REPO)).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetGitConnectorEmptyRefFetchesHarnessConnector() {
    String repoName = "harness-core";
    String gitUrl = "https://code.harness.io";
    String apiUrl = "https://app.harness.io";
    String serviceSecret = "test-secret";
    String authToken = "test-auth-token";
    ConnectorDetails expectedConnectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.HARNESS).build();

    when(harnessCodeServiceConfig.getGitUrl()).thenReturn(gitUrl);
    when(harnessCodeServiceConfig.getApiUrl()).thenReturn(apiUrl);
    when(harnessCodeServiceConfig.getServiceSecret()).thenReturn(serviceSecret);
    when(connectorUtils.fetchAuthToken(any(), any(), eq(repoName), eq(serviceSecret))).thenReturn(authToken);
    when(connectorUtils.getHarnessConnectorDetails(any(), eq(gitUrl), eq(null), eq(authToken), eq(apiUrl)))
        .thenReturn(expectedConnectorDetails);

    ConnectorDetails result = codebaseUtils.getGitConnector(null, (String) null, ambiance, repoName);

    assertThat(result).isEqualTo(expectedConnectorDetails);
    assertThat(result.getConnectorType()).isEqualTo(ConnectorType.HARNESS);
  }
}
