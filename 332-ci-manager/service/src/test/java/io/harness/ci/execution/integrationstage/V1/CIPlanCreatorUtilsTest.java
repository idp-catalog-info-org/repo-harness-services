/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.VIVEK_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.stepinfo.Strategy;
import io.harness.beans.steps.v1.CloneRef;
import io.harness.beans.steps.v1.CloneType;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.platform.V1.Arch;
import io.harness.beans.yaml.extended.platform.V1.OS;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.DockerRuntime;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.states.codebase.ScmGitRefManager;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidYamlException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.MergeQueueHook;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.SubmoduleStrategy;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.CommitShaBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class CIPlanCreatorUtilsTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ScmGitRefManager scmGitRefManager;

  @InjectMocks private CIPlanCreatorUtils ciPlanCreatorUtils;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_CloneEnabledAtStageLevelWithProperties() {
    // Arrange: Stage level clone with all fields
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("stageConnector"))
            .repo(ParameterField.createValueField("stage-repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .strategy(Strategy.MERGE)
            .depth(ParameterField.createValueField(100))
            .insecure(ParameterField.createValueField(false))
            .lfs(ParameterField.createValueField(true))
            .clonedir(ParameterField.createValueField("/harness"))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("stageConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("stage-repo");
    assertThat(codeBase.getDepth().getValue()).isEqualTo(100);
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetCodebase_MapsGitClonePropertiesFromV1StageClone() {
    List<String> sparsePaths = Arrays.asList("src/main", "src/test");
    ContainerResource resources = ContainerResource.builder().build();
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("connector"))
            .repo(ParameterField.createValueField("repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .clonedir(ParameterField.createValueField("/custom/dir"))
            .lfs(ParameterField.createValueField(true))
            .trace(ParameterField.createValueField(true))
            .tags(ParameterField.createValueField(true))
            .submodules(ParameterField.createValueField(SubmoduleStrategy.TRUE))
            .sparseCheckout(ParameterField.createValueField(sparsePaths))
            .preFetchCommand(ParameterField.createValueField("git fetch origin refs/notes/*:refs/notes/*"))
            .persistCredentials(ParameterField.createValueField(true))
            .resources(resources)
            .user(ParameterField.createValueField(1005))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getCloneDirectory().getValue()).isEqualTo("/custom/dir");
    assertThat(codeBase.getLfs().getValue()).isTrue();
    assertThat(codeBase.getDebug().getValue()).isTrue();
    assertThat(codeBase.getFetchTags().getValue()).isTrue();
    assertThat(codeBase.getSubmoduleStrategy().getValue()).isEqualTo(SubmoduleStrategy.TRUE);
    assertThat(codeBase.getSparseCheckout().getValue()).containsExactly("src/main", "src/test");
    assertThat(codeBase.getPreFetchCommand().getValue()).isEqualTo("git fetch origin refs/notes/*:refs/notes/*");
    assertThat(codeBase.getPersistCredentials().getValue()).isTrue();
    assertThat(codeBase.getResources()).isEqualTo(resources);
    assertThat(codeBase.getRunAsUser().getValue()).isEqualTo(1005);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetCodebase_UserNotSetOnV1StageClone_RunAsUserIsNull() {
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("connector"))
            .repo(ParameterField.createValueField("repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    assertThat(result).isPresent();
    assertThat(result.get().getRunAsUser()).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_CloneEnabledAtPipelineLevelWithProperties() {
    // Arrange: Pipeline level clone with all fields
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("pipelineConnector"))
            .repo(ParameterField.createValueField("pipeline-repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.TAG).name(ParameterField.createValueField("v1.0.0")).build()))
            .strategy(Strategy.SOURCE_BRANCH)
            .depth(ParameterField.createValueField(50))
            .insecure(ParameterField.createValueField(true))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, pipelineClone);

    // Assert
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("pipelineConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("pipeline-repo");
    assertThat(codeBase.getDepth().getValue()).isEqualTo(50);
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.TAG);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_WebhookTrigger_PartialInheritanceFromWebhookPayload() {
    // Arrange: Clone without connector/repo - should inherit from webhook
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        // No connector specified
                                        // No repo specified
                                        .depth(ParameterField.createValueField(25))
                                        .build();

    // Create webhook trigger context with connector and repo info
    TriggerPayload triggerPayload =
        TriggerPayload.newBuilder()
            .setConnectorRef("webhookConnector")
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPr(PullRequestHook.newBuilder()
                               .setRepo(Repository.newBuilder().setNamespace("harness").setName("core").build())
                               .setPr(PullRequest.newBuilder().setNumber(123).build())
                               .build())
                    .build())
            .build();

    PlanCreationContext ctx = createWebhookPlanCreationContext(triggerPayload);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: Should inherit connector and repo from webhook
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("webhookConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("harness/core");
    assertThat(codeBase.getDepth().getValue()).isEqualTo(25);
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.PR);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_RemotePipeline_PartialInheritanceFromGitSync() {
    // Arrange: Clone without connector/repo - should inherit from gitsync
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        // No connector specified
                                        // No repo specified
                                        .depth(ParameterField.createValueField(30))
                                        .build();

    // Setup GitSync context
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().repoName("gitsync-repo").branch("develop").build())
            .build();

    byte[] serializedGitContext = "mock-serialized-context".getBytes();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(gitSyncBranchContext);

    PlanCreationContext ctx =
        createRemotePlanCreationContext("gitsyncConnector", ByteString.copyFrom(serializedGitContext));

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: Should inherit connector and repo from gitsync
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("gitsyncConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("gitsync-repo");
    assertThat(codeBase.getDepth().getValue()).isEqualTo(30);
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_WebhookTrigger_YamlValuesOverrideWebhookPayload() {
    // Arrange: Clone WITH explicit connector/repo and ref specified
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("yamlConnector"))
            .repo(ParameterField.createValueField("yaml-repo"))
            .ref(ParameterField.createValueField(CloneRef.builder()
                                                     .type(CloneType.BRANCH)
                                                     .name(ParameterField.createValueField("feature-branch"))
                                                     .build()))
            .depth(ParameterField.createValueField(100))
            .build();

    // Create webhook trigger context with different connector and repo
    TriggerPayload triggerPayload =
        TriggerPayload.newBuilder()
            .setConnectorRef("webhookConnector")
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPr(PullRequestHook.newBuilder()
                               .setRepo(Repository.newBuilder().setNamespace("webhook").setName("webhook-repo").build())
                               .setPr(PullRequest.newBuilder().setNumber(456).build())
                               .build())
                    .build())
            .build();

    PlanCreationContext ctx = createWebhookPlanCreationContext(triggerPayload);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: A concrete (non-expression) clone.ref in YAML takes precedence over the webhook payload (V0 parity via
    // treatWebhookAsManualExecution). The build is the YAML branch, not the webhook PR #456.
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("yamlConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("yaml-repo");
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
    assertThat(codeBase.getBuild().getValue().getSpec()).isInstanceOf(BranchBuildSpec.class);
    assertThat(((BranchBuildSpec) codeBase.getBuild().getValue().getSpec()).getBranch().getValue())
        .isEqualTo("feature-branch");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testGetCodebase_MergeQueueWebhook_ReturnsCommitShaBuild() {
    // Arrange: clone with no ref specified, so getBuild falls through to the webhook payload switch.
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("connector"))
                                        .repo(ParameterField.createValueField("repo"))
                                        .build();

    MergeQueueHook mergeQueueHook =
        MergeQueueHook.newBuilder()
            .setAction(Action.CHECKS_REQUESTED)
            .setRepo(Repository.newBuilder().setNamespace("acc/org/proj").setName("repo").build())
            .setSender(User.newBuilder().setLogin("user").build())
            .setBranch("main")
            .setSha("speculativeMergeSha")
            .build();
    TriggerPayload triggerPayload =
        TriggerPayload.newBuilder()
            .setParsedPayload(ParsedPayload.newBuilder().setMergeQueue(mergeQueueHook).build())
            .build();

    PlanCreationContext ctx = createWebhookPlanCreationContext(triggerPayload);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: the speculative merge commit is cloned by SHA, not the (unreachable) target branch tip - this
    // is the V1 counterpart of the V0 fix in CodeBaseTaskStep#buildWebhookCodebaseSweepingOutput.
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.COMMIT_SHA);
    assertThat(codeBase.getBuild().getValue().getSpec()).isInstanceOf(CommitShaBuildSpec.class);
    assertThat(((CommitShaBuildSpec) codeBase.getBuild().getValue().getSpec()).getCommitSha().getValue())
        .isEqualTo("speculativeMergeSha");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testGetCodebase_PushWebhook_StillReturnsBranchBuild() {
    // Regression guard, paired explicitly against the merge queue case above: a plain push webhook on the
    // same code path must keep returning BuildType.BRANCH.
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("connector"))
                                        .repo(ParameterField.createValueField("repo"))
                                        .build();

    PushHook pushHook = PushHook.newBuilder()
                            .setRef("refs/heads/main")
                            .setRepo(Repository.newBuilder().setNamespace("acc/org/proj").setName("repo").build())
                            .setSender(User.newBuilder().setLogin("user").build())
                            .build();
    TriggerPayload triggerPayload =
        TriggerPayload.newBuilder().setParsedPayload(ParsedPayload.newBuilder().setPush(pushHook).build()).build();

    PlanCreationContext ctx = createWebhookPlanCreationContext(triggerPayload);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
    assertThat(((BranchBuildSpec) codeBase.getBuild().getValue().getSpec()).getBranch().getValue()).isEqualTo("main");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_RemotePipeline_YamlValuesOverrideGitSync() {
    // Arrange: Clone WITH connector/repo specified - should override gitsync values
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("yamlConnector")) // Explicit override
            .repo(ParameterField.createValueField("yaml-repo")) // Explicit override
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.TAG).name(ParameterField.createValueField("v2.0.0")).build()))
            .depth(ParameterField.createValueField(75))
            .build();

    // Setup GitSync context with different values
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().repoName("gitsync-repo").branch("main").build())
            .build();

    byte[] serializedGitContext = "mock-serialized-context".getBytes();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(gitSyncBranchContext);

    PlanCreationContext ctx = createRemotePlanCreationContext("gitsyncConnector", // Different from YAML
        ByteString.copyFrom(serializedGitContext));

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: YAML values should override gitsync values
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("yamlConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("yaml-repo");
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.TAG);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_RefMissingInYaml_ManualTrigger_FetchesDefaultBranch() {
    // Arrange: Clone without ref - should fetch default branch
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("testConnector"))
                                        .repo(ParameterField.createValueField("test-repo"))
                                        // ref is NOT specified
                                        .depth(ParameterField.createValueField(50))
                                        .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    // Mock connector utils to return a connector
    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    when(connectorUtils.getConnectorDetails(any(), anyString(), any(Boolean.class))).thenReturn(connectorDetails);

    // Mock scmGitRefManager to return default branch
    when(scmGitRefManager.getScmConnector(any(), anyString(), anyString())).thenReturn(null);
    when(scmGitRefManager.getDefaultBranch(any(), anyString())).thenReturn("master");

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: Should use default branch
    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getConnectorRef().getValue()).isEqualTo("testConnector");
    assertThat(codeBase.getRepoName().getValue()).isEqualTo("test-repo");
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.BRANCH);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_NoClonePresent_ReturnsEmpty() {
    // Arrange: No clone defined
    GitCloneStepInfoV1 stageClone = null;

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: Should return empty
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCodebase_CloneDisabledWithEnabledFalse_ReturnsEmpty() {
    // Arrange: Clone with enabled=false
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(false))
                                        .connector(ParameterField.createValueField("testConnector"))
                                        .repo(ParameterField.createValueField("test-repo"))
                                        .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    // Act
    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    // Assert: Should return empty because clone is disabled
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithSize() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .size(ParameterField.createValueField(CIResourceClass.LARGE))
                                       .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(HostedVmInfraYaml.class);
    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    assertThat(hostedVm.getSpec().getRuntime()).isNotNull();
    Runtime runtimeValue = hostedVm.getSpec().getRuntime().getValue();
    assertThat(runtimeValue).isInstanceOf(CloudRuntime.class);
    CloudRuntime cloudRuntime = (CloudRuntime) runtimeValue;
    assertThat(cloudRuntime.getSpec().getSize().getValue()).isEqualTo(CIResourceClass.LARGE);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithPlatformMatrixExpressions() {
    RuntimeV1 runtime = RuntimeV1.builder().cloud(RuntimeV1.CloudRuntimeSpec.builder().build()).build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createExpressionField(true, "<+matrix.os>", null, true))
                                .arch(ParameterField.createExpressionField(true, "<+matrix.arch>", null, true))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(HostedVmInfraYaml.class);
    Platform platform = ((HostedVmInfraYaml) infra).getSpec().getPlatform().getValue();
    assertThat(platform.getOs().isExpression()).isTrue();
    assertThat(platform.getOs().getExpressionValue()).isEqualTo("<+matrix.os>");
    assertThat(platform.getArch().isExpression()).isTrue();
    assertThat(platform.getArch().getExpressionValue()).isEqualTo("<+matrix.arch>");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetInfrastructure_NestedVirtualizationWithPlatformMatrixExpressions() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .nestedVirtualization(ParameterField.createValueField(true))
                                       .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createExpressionField(true, "<+matrix.os>", null, true))
                                .arch(ParameterField.createExpressionField(true, "<+matrix.arch>", null, true))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(HostedVmInfraYaml.class);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithNestedVirtualization() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .size(ParameterField.createValueField(CIResourceClass.LARGE))
                                       .nestedVirtualization(ParameterField.createValueField(true))
                                       .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVm.getSpec().getRuntime().getValue();
    assertThat(cloudRuntime.getSpec().getNestedVirtualization().getValue()).isTrue();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudShorthand() {
    RuntimeV1 runtime = RuntimeV1.builder().cloud(RuntimeV1.CloudRuntimeSpec.builder().build()).build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(HostedVmInfraYaml.class);
    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    assertThat(hostedVm.getSpec().getRuntime()).isNotNull();
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVm.getSpec().getRuntime().getValue();
    assertThat(cloudRuntime.getSpec().getSize()).isNull();
    assertThat(cloudRuntime.getSpec().getNestedVirtualization().getValue()).isFalse();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithBYOI_FullImageSpec() {
    RuntimeV1 runtime =
        RuntimeV1.builder()
            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                       .size(ParameterField.createValueField(CIResourceClass.LARGE))
                       .imageName(ParameterField.createValueField("registry.example.com/custom-image:v1"))
                       .connector(ParameterField.createValueField("dockerConnector"))
                       .build())
            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.MACOS))
                                .arch(ParameterField.createValueField(Arch.ARM_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVm.getSpec().getRuntime().getValue();
    assertThat(cloudRuntime.getSpec().getSize().getValue()).isEqualTo(CIResourceClass.LARGE);
    assertThat(cloudRuntime.getSpec().getImageSpec().getImageName().getValue())
        .isEqualTo("registry.example.com/custom-image:v1");
    // username and password default to "admin" via V0 CloudRuntimeImageSpec getters
    assertThat(cloudRuntime.getSpec().getImageSpec().getUsername().getValue()).isEqualTo("admin");
    assertThat(cloudRuntime.getSpec().getImageSpec().getPassword().getValue()).isEqualTo("admin");
    assertThat(cloudRuntime.getSpec().getImageSpec().getConnectorRef().getValue()).isEqualTo("dockerConnector");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithBYOI_ImageOnly() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .size(ParameterField.createValueField(CIResourceClass.MEDIUM))
                                       .imageName(ParameterField.createValueField("custom-linux:2.0"))
                                       .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVm.getSpec().getRuntime().getValue();
    assertThat(cloudRuntime.getSpec().getImageSpec().getImageName().getValue()).isEqualTo("custom-linux:2.0");
    assertThat(cloudRuntime.getSpec().getImageSpec().getUsername().getValue()).isEqualTo("admin");
    assertThat(cloudRuntime.getSpec().getImageSpec().getPassword().getValue()).isEqualTo("admin");
    assertThat(cloudRuntime.getSpec().getImageSpec().getConnector()).isNull();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithoutBYOI_NoImageSpec() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .size(ParameterField.createValueField(CIResourceClass.SMALL))
                                       .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    HostedVmInfraYaml hostedVm = (HostedVmInfraYaml) infra;
    CloudRuntime cloudRuntime = (CloudRuntime) hostedVm.getSpec().getRuntime().getValue();
    assertThat(cloudRuntime.getSpec().getSize().getValue()).isEqualTo(CIResourceClass.SMALL);
    assertThat(ParameterField.isBlank(cloudRuntime.getSpec().getImageSpec().getImageName())).isTrue();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetInfrastructure_CloudWithNestedVirtualization_NonLinuxAmd_ThrowsException() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .cloud(RuntimeV1.CloudRuntimeSpec.builder()
                                       .size(ParameterField.createValueField(CIResourceClass.LARGE))
                                       .nestedVirtualization(ParameterField.createValueField(true))
                                       .build())
                            .build();
    PlatformV1 macPlatform = PlatformV1.builder()
                                 .os(ParameterField.createValueField(OS.MACOS))
                                 .arch(ParameterField.createValueField(Arch.ARM_64))
                                 .build();

    assertThatThrownBy(() -> ciPlanCreatorUtils.getInfrastructure(runtime, macPlatform, new ParameterField<>()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining(
            "Nested virtualization parameter must be set with VM of Os type Linux and Arch type AMD64");

    PlatformV1 windowsPlatform = PlatformV1.builder()
                                     .os(ParameterField.createValueField(OS.WINDOWS))
                                     .arch(ParameterField.createValueField(Arch.AMD_64))
                                     .build();

    assertThatThrownBy(() -> ciPlanCreatorUtils.getInfrastructure(runtime, windowsPlatform, new ParameterField<>()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining(
            "Nested virtualization parameter must be set with VM of Os type Linux and Arch type AMD64");

    PlatformV1 linuxArmPlatform = PlatformV1.builder()
                                      .os(ParameterField.createValueField(OS.LINUX))
                                      .arch(ParameterField.createValueField(Arch.ARM_64))
                                      .build();

    assertThatThrownBy(() -> ciPlanCreatorUtils.getInfrastructure(runtime, linuxArmPlatform, new ParameterField<>()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining(
            "Nested virtualization parameter must be set with VM of Os type Linux and Arch type AMD64");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetInfrastructure_ShellWithHarnessImageConnector() {
    // Arrange: Shell runtime with override image connector
    String connectorRef = "gargi_connector";
    RuntimeV1 runtime =
        RuntimeV1.builder()
            .shell(
                RuntimeV1.ShellRuntimeSpec.builder().connector(ParameterField.createValueField(connectorRef)).build())
            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    // Act
    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    // Assert
    assertThat(infra).isInstanceOf(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.class);
    io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml dockerInfra =
        (io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml) infra;
    assertThat(dockerInfra.getSpec().getRuntime()).isNotNull();
    assertThat(dockerInfra.getSpec().getRuntime().getValue()).isNotNull();
    assertThat(dockerInfra.getSpec().getRuntime().getValue()).isInstanceOf(DockerRuntime.class);
    DockerRuntime dockerRuntime = (DockerRuntime) dockerInfra.getSpec().getRuntime().getValue();
    assertThat(dockerRuntime.getSpec().getHarnessImageConnectorRef()).isNotNull();
    assertThat(dockerRuntime.getSpec().getHarnessImageConnectorRef().getValue()).isEqualTo(connectorRef);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetInfrastructure_ShellWithoutHarnessImageConnector() {
    // Arrange: Shell runtime without override image connector
    RuntimeV1 runtime = RuntimeV1.builder().shell(RuntimeV1.ShellRuntimeSpec.builder().build()).build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.MACOS))
                                .arch(ParameterField.createValueField(Arch.ARM_64))
                                .build();

    // Act
    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    // Assert
    assertThat(infra).isInstanceOf(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.class);
    io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml dockerInfra =
        (io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml) infra;
    // When no connector is provided, runtime field should be null or empty
    assertThat(dockerInfra.getSpec().getRuntime() == null || ParameterField.isNull(dockerInfra.getSpec().getRuntime()))
        .isTrue();
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetInfrastructure_KubernetesWindows_OsSourcedFromPlatform() {
    // Kubernetes runtime carries no os field; OS is sourced from the stage-level platform.os.
    RuntimeV1 runtime = RuntimeV1.builder()
                            .kubernetes(RuntimeV1.K8RuntimeSpec.builder()
                                            .connector(ParameterField.createValueField("k8sConnector"))
                                            .namespace(ParameterField.createValueField("cie-test"))
                                            .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.WINDOWS))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(K8sDirectInfraYaml.class);
    K8sDirectInfraYaml k8sInfra = (K8sDirectInfraYaml) infra;
    assertThat(k8sInfra.getSpec().getOs().getValue()).isEqualTo(OSType.Windows);
    assertThat(k8sInfra.getSpec().getConnectorRef().getValue()).isEqualTo("k8sConnector");
    assertThat(k8sInfra.getSpec().getNamespace().getValue()).isEqualTo("cie-test");
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testGetInfrastructure_KubernetesLinux_OsSourcedFromPlatform() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .kubernetes(RuntimeV1.K8RuntimeSpec.builder()
                                            .connector(ParameterField.createValueField("k8sConnector"))
                                            .namespace(ParameterField.createValueField("default"))
                                            .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(K8sDirectInfraYaml.class);
    K8sDirectInfraYaml k8sInfra = (K8sDirectInfraYaml) infra;
    assertThat(k8sInfra.getSpec().getOs().getValue()).isEqualTo(OSType.Linux);
  }

  // Regression guard for PIPE-35845 related fix: an expression `pull` field on the kubernetes runtime must survive
  // plan creation as an expression ParameterField<ImagePullPolicy> instead of NPE-ing via obtainValue() on a
  // ParameterField<PullPolicy> that only carries an expressionValue.
  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetInfrastructure_KubernetesImagePullPolicyExpression_PreservedAsExpression() {
    ParameterField<PullPolicy> pullExpr =
        ParameterField.createExpressionField(true, "<+pipeline.variables.pullPolicy>", null, false);
    RuntimeV1 runtime = RuntimeV1.builder()
                            .kubernetes(RuntimeV1.K8RuntimeSpec.builder()
                                            .connector(ParameterField.createValueField("k8sConnector"))
                                            .namespace(ParameterField.createValueField("default"))
                                            .imagePullPolicy(pullExpr)
                                            .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    assertThat(infra).isInstanceOf(K8sDirectInfraYaml.class);
    K8sDirectInfraYaml k8sInfra = (K8sDirectInfraYaml) infra;
    ParameterField<ImagePullPolicy> pull = k8sInfra.getSpec().getImagePullPolicy();
    assertThat(pull).isNotNull();
    assertThat(pull.isExpression()).isTrue();
    assertThat(pull.getExpressionValue()).isEqualTo("<+pipeline.variables.pullPolicy>");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetInfrastructure_KubernetesImagePullPolicyResolvedValue_ConvertedCorrectly() {
    RuntimeV1 runtime = RuntimeV1.builder()
                            .kubernetes(RuntimeV1.K8RuntimeSpec.builder()
                                            .connector(ParameterField.createValueField("k8sConnector"))
                                            .namespace(ParameterField.createValueField("default"))
                                            .imagePullPolicy(ParameterField.createValueField(PullPolicy.ALWAYS))
                                            .build())
                            .build();
    PlatformV1 platformV1 = PlatformV1.builder()
                                .os(ParameterField.createValueField(OS.LINUX))
                                .arch(ParameterField.createValueField(Arch.AMD_64))
                                .build();

    Infrastructure infra = ciPlanCreatorUtils.getInfrastructure(runtime, platformV1, new ParameterField<>());

    K8sDirectInfraYaml k8sInfra = (K8sDirectInfraYaml) infra;
    assertThat(k8sInfra.getSpec().getImagePullPolicy().getValue()).isEqualTo(ImagePullPolicy.ALWAYS);
  }

  // Regression guard: an unresolved <+trigger.*> / <+input> expression on clone.ref.sha must survive plan creation
  // as a raw expression ParameterField<String> so it resolves at execution time (V0 parity).
  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetCodebase_CommitShaExpression_PreservedAsExpressionParameterField() {
    ParameterField<String> shaExpr = ParameterField.createExpressionField(true, "<+trigger.commitSha>", null, true);
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("connector"))
            .repo(ParameterField.createValueField("repo"))
            .ref(ParameterField.createValueField(CloneRef.builder().type(CloneType.COMMIT).sha(shaExpr).build()))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.COMMIT_SHA);
    CommitShaBuildSpec spec = (CommitShaBuildSpec) codeBase.getBuild().getValue().getSpec();
    ParameterField<String> sha = spec.getCommitSha();
    assertThat(sha).isNotNull();
    assertThat(sha.isExpression()).isTrue();
    assertThat(sha.getExpressionValue()).isEqualTo("<+trigger.commitSha>");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetCodebase_PrNumberExpression_PreservedAsStringExpressionParameterField() {
    ParameterField<Integer> prNumberExpr =
        ParameterField.createExpressionField(true, "<+trigger.prNumber>", null, false);
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .enabled(ParameterField.createValueField(true))
            .connector(ParameterField.createValueField("connector"))
            .repo(ParameterField.createValueField("repo"))
            .ref(ParameterField.createValueField(CloneRef.builder().type(CloneType.PR).number(prNumberExpr).build()))
            .build();

    PlanCreationContext ctx = createPlanCreationContext(TriggerType.MANUAL, PipelineStoreType.INLINE);

    Optional<CodeBase> result = ciPlanCreatorUtils.getCodebase(ctx, stageClone);

    assertThat(result).isPresent();
    CodeBase codeBase = result.get();
    assertThat(codeBase.getBuild().getValue().getType()).isEqualTo(BuildType.PR);
    PRBuildSpec spec = (PRBuildSpec) codeBase.getBuild().getValue().getSpec();
    ParameterField<String> number = spec.getNumber();
    assertThat(number).isNotNull();
    assertThat(number.isExpression()).isTrue();
    assertThat(number.getExpressionValue()).isEqualTo("<+trigger.prNumber>");
  }

  private PlanCreationContext createPlanCreationContext(TriggerType triggerType, PipelineStoreType storeType) {
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(triggerType).build();

    PlanExecutionContext executionContext =
        PlanExecutionContext.newBuilder().setTriggerInfo(triggerInfo).setPipelineStoreType(storeType).build();

    PlanCreationContextValue contextValue = PlanCreationContextValue.newBuilder()
                                                .setAccountIdentifier(ACCOUNT_ID)
                                                .setOrgIdentifier(ORG_ID)
                                                .setProjectIdentifier(PROJECT_ID)
                                                .setExecutionContext(executionContext)
                                                .build();

    return PlanCreationContext.builder().globalContext("metadata", contextValue).build();
  }

  private PlanCreationContext createWebhookPlanCreationContext(TriggerPayload triggerPayload) {
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build();

    PlanExecutionContext executionContext = PlanExecutionContext.newBuilder()
                                                .setTriggerInfo(triggerInfo)
                                                .setPipelineStoreType(PipelineStoreType.INLINE)
                                                .build();

    PlanCreationContextValue contextValue = PlanCreationContextValue.newBuilder()
                                                .setAccountIdentifier(ACCOUNT_ID)
                                                .setOrgIdentifier(ORG_ID)
                                                .setProjectIdentifier(PROJECT_ID)
                                                .setExecutionContext(executionContext)
                                                .setTriggerPayload(triggerPayload)
                                                .build();

    return PlanCreationContext.builder().globalContext("metadata", contextValue).build();
  }

  private PlanCreationContext createRemotePlanCreationContext(
      String pipelineConnectorRef, ByteString gitSyncBranchContext) {
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();

    PlanExecutionContext executionContext = PlanExecutionContext.newBuilder()
                                                .setTriggerInfo(triggerInfo)
                                                .setPipelineStoreType(PipelineStoreType.REMOTE)
                                                .setPipelineConnectorRef(pipelineConnectorRef)
                                                .setGitSyncBranchContext(gitSyncBranchContext)
                                                .build();

    PlanCreationContextValue contextValue = PlanCreationContextValue.newBuilder()
                                                .setAccountIdentifier(ACCOUNT_ID)
                                                .setOrgIdentifier(ORG_ID)
                                                .setProjectIdentifier(PROJECT_ID)
                                                .setExecutionContext(executionContext)
                                                .build();

    return PlanCreationContext.builder().globalContext("metadata", contextValue).build();
  }
}
