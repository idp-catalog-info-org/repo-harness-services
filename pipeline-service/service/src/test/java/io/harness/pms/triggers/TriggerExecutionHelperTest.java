/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER;
import static io.harness.beans.FeatureName.PIPE_RESOLVE_TRIGGER_EXPRESSIONS_IN_RUNTIME_INPUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.execution.PlanExecution.EXEC_TAG_SET_BY_TRIGGER;
import static io.harness.ngtriggers.Constants.COMMIT_SHA_STRING_LENGTH;
import static io.harness.ngtriggers.Constants.EVENT_CORRELATION_ID;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.SOURCE_EVENT_ID;
import static io.harness.ngtriggers.Constants.SOURCE_EVENT_LINK;
import static io.harness.ngtriggers.Constants.TRIGGER_REF;
import static io.harness.pms.contracts.plan.TriggerType.SCHEDULER_CRON;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORTALL;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.VIVEK_KUMAR;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactoryRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarArtifactEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.HarnessArtifactRegistrySpec;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.BuildInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.helper.ExecutionHelper;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.MergeQueueHook;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Release;
import io.harness.product.ci.scm.proto.ReleaseHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class TriggerExecutionHelperTest extends CategoryTest {
  @Inject @InjectMocks TriggerExecutionHelper triggerExecutionHelper;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projectId = "proj";
  private final String pipelineId = "target";
  private final String uniqueId = "uniqueId";
  private final ScopeInfo scopeInfo = ScopeInfo.builder()
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .uniqueId(uniqueId)
                                          .scopeType(ScopeLevel.PROJECT)
                                          .build();
  private NGTriggerEntity ngTriggerEntity;
  private TriggerWebhookEvent triggerWebhookEvent;
  private PipelineEntity pipelineEntityV1;

  private final ExecutionMetadata metadata = ExecutionMetadata.newBuilder().build();
  private final PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();
  PlanExecutionMetadataWithContext.Builder planExecutionMetadataWithContextBuilder =
      PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(true);

  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock NGSettingsClient settingsClient;
  @Mock ExecutionHelper executionHelper;
  @Mock WebhookEventPayloadParser webhookEventPayloadParser;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock PlanExecutionService planExecutionService;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock MetricService metricService;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock ScopeResolutionHelper scopeResolutionService;
  @Mock TriggerExecutorResolver triggerExecutorResolver;
  @Mock YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Before
  public void setUp() {
    triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("CUSTOM")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(
                "{\"branch\": \"main\", \"input_set_refs\": \"inputSet1,inputSet2\", \"input_yaml\": \"inputsYaml\"}")
            .createdAt(1L)
            .build();
    MockitoAnnotations.initMocks(this);

    when(scopeResolutionService.getScopeInfo(anyString(), anyString())).thenReturn(scopeInfo);

    doAnswer(invocation -> {
      String accountId = invocation.getArgument(0);
      String orgIdentifier = invocation.getArgument(1);
      String projectIdentifier = invocation.getArgument(2);
      String pipelineIdentifier = invocation.getArgument(3);
      List<String> inputSetIdentifiers = invocation.getArgument(4);
      PlanExecutionMetadataWithContext context = invocation.getArgument(5);

      if (inputSetIdentifiers != null && !inputSetIdentifiers.isEmpty()) {
        context.setInputSetIdentifiers(new ArrayList<>(inputSetIdentifiers));
      }
      return null;
    })
        .when(pipelineExecutor)
        .resolveAndAssignInputSetsToExecution(any(String.class), any(String.class), any(String.class),
            any(String.class), any(List.class), any(PlanExecutionMetadataWithContext.class), any(), anyBoolean());

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId("acc")
                          .orgIdentifier("org")
                          .projectIdentifier("proj")
                          .targetIdentifier("target")
                          .identifier("trigger")
                          .name("triggerName")
                          .createdAt(1L)
                          .parentUniqueId("unique-id")
                          .build();

    String simplifiedYaml = readFile("simplified-pipeline.yaml");
    pipelineEntityV1 = PipelineEntity.builder()
                           .accountId(accountId)
                           .orgIdentifier(orgId)
                           .projectIdentifier(projectId)
                           .parentUniqueId(projectId)
                           .identifier(pipelineId)
                           .yaml(simplifiedYaml)
                           .runSequence(394)
                           .harnessVersion(HarnessYamlVersion.V1)
                           .build();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testRequestPipelineExecutionAbortForSameExecTagIfNeeded() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projectId)
                                 .parentUniqueId(projectId)
                                 .identifier("id")
                                 .build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .spec(WebhookTriggerConfigV2.builder()
                                      .spec(GithubSpec.builder()
                                                .spec(GithubPRSpec.builder().autoAbortPreviousExecutions(true).build())
                                                .build())
                                      .build())
                            .build())
                    .build())
            .build();
    List<PlanExecution> executionsToAbort = new ArrayList<>();
    PlanExecution planExecution = PlanExecution.builder().uuid("uuid").build();
    executionsToAbort.add(planExecution);
    String executionTag = "executionTag";
    when(planExecutionService.findPrevUnTerminatedPlanExecutionsByExecutionTag(planExecution, executionTag))
        .thenReturn(executionsToAbort);
    when(pmsExecutionService.registerInterrupt(any(), any(), any(), any()))
        .thenReturn(InterruptDTO.builder().type(ABORTALL).planExecutionId(planExecution.getUuid()).build());
    triggerExecutionHelper.requestPipelineExecutionAbortForSameExecTagIfNeeded(
        triggerDetails, planExecution, executionTag, scopeInfo, false);
    verify(pmsExecutionService, times(1)).registerInterrupt(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetPipelineEntityToExecute() throws Exception {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().repo("repo").filePath("filePath").connectorRef("connectorRef").build();

    NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                                 .accountId("ACCOUNT_ID")
                                                 .orgIdentifier("ORG_IDENTIFIER")
                                                 .projectIdentifier("PROJ_IDENTIFIER")
                                                 .parentUniqueId("PROJ_IDENTIFIER")
                                                 .targetIdentifier("PIPELINE_IDENTIFIER")
                                                 .identifier("IDENTIFIER")
                                                 .name("NAME")
                                                 .targetType(TargetType.PIPELINE)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntityGitSync)
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .inputSetRefs(ParameterField.createValueField(Arrays.asList("inputSet1", "inputSet2")))
                    .pipelineBranchName("pipelineBranchName")
                    .build())
            .build();

    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync, scopeInfo, false))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline("ACCOUNT_ID", "ORG_IDENTIFIER", "PROJ_IDENTIFIER", "PIPELINE_IDENTIFIER", false, false, false,
            false, scopeInfo, true);
    when(pmsGitSyncHelper.serializeGitSyncBranchContext(any())).thenReturn(ByteString.copyFrom(new byte[2]));
    PipelineEntity pipelineEntityToExecute =
        triggerExecutionHelper.getPipelineEntityToExecute(triggerDetails, triggerWebhookEvent, null, scopeInfo);
    assertThat(pipelineEntityToExecute).isEqualToComparingFieldByField(pipelineEntity);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateTriggerRef() {
    assertThat(triggerExecutionHelper.generateTriggerRef(ngTriggerEntity, scopeInfo, false))
        .isEqualTo("acc/org/proj/trigger");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsAutoAbort() {
    GithubPRSpec githubPRSpec = GithubPRSpec.builder().autoAbortPreviousExecutions(true).build();
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder()
            .source(
                NGTriggerSourceV2.builder()
                    .type(NGTriggerType.WEBHOOK)
                    .spec(
                        WebhookTriggerConfigV2.builder()
                            .type(WebhookTriggerType.GITHUB)
                            .spec(GithubSpec.builder().type(GithubTriggerEvent.PULL_REQUEST).spec(githubPRSpec).build())
                            .build())
                    .build())
            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isTrue();

    githubPRSpec.setAutoAbortPreviousExecutions(false);
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();

    ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                            .source(NGTriggerSourceV2.builder()
                                        .type(NGTriggerType.WEBHOOK)
                                        .spec(WebhookTriggerConfigV2.builder()
                                                  .type(WebhookTriggerType.CUSTOM)
                                                  .spec(CustomTriggerSpec.builder().build())
                                                  .build())
                                        .build())
                            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();

    ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                            .source(NGTriggerSourceV2.builder()
                                        .type(NGTriggerType.SCHEDULED)
                                        .spec(ScheduledTriggerConfig.builder()
                                                  .type("Cron")
                                                  .spec(CronTriggerSpec.builder().expression("").build())
                                                  .build())
                                        .build())
                            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();

    ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                            .source(NGTriggerSourceV2.builder()
                                        .type(NGTriggerType.WEBHOOK)
                                        .spec(WebhookTriggerConfigV2.builder()
                                                  .type(WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY)
                                                  .spec(HarnessArtifactRegistrySpec.builder()
                                                            .type(HarTriggerEvent.ARTIFACT)
                                                            .spec(HarArtifactEventSpec.builder().build())
                                                            .build())
                                                  .build())
                                        .build())
                            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateExecutionTagForEvent() {
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());

    String executionTagForEvent =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, payloadBuilder.build(), scopeInfo, false);
    assertThat(executionTagForEvent).isEqualTo("acc:org:proj:target:trigger:PR:https://github.com:1:source:target");

    payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPush(PushHook.newBuilder()
                         .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                         .setRef("ref")
                         .build())
            .build());
    executionTagForEvent =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, payloadBuilder.build(), scopeInfo, false);
    assertThat(executionTagForEvent).isEqualTo("acc:org:proj:target:trigger:PUSH:https://github.com:ref");
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER);
    executionTagForEvent =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, payloadBuilder.build(), scopeInfo, false);
    assertThat(executionTagForEvent).isEqualTo("acc:org:proj:target:PUSH:https://github.com:ref");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testGenerateExecutionTagForMergeQueueEvent() {
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    TriggerPayload requested =
        TriggerPayload.newBuilder()
            .setType(Type.GIT)
            .setParsedPayload(ParsedPayload.newBuilder()
                                  .setMergeQueue(MergeQueueHook.newBuilder()
                                                     .setAction(Action.CHECKS_REQUESTED)
                                                     .setRepo(Repository.newBuilder().setLink("https://code").build())
                                                     .setBranch("main")
                                                     .setSha("abc123")
                                                     .build())
                                  .build())
            .build();

    TriggerPayload canceled = requested.toBuilder()
                                  .setParsedPayload(requested.getParsedPayload()
                                                        .toBuilder()
                                                        .setMergeQueue(requested.getParsedPayload()
                                                                           .getMergeQueue()
                                                                           .toBuilder()
                                                                           .setAction(Action.CHECKS_CANCELED)
                                                                           .build())
                                                        .build())
                                  .build();

    String requestedTag =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, requested, scopeInfo, false);
    String canceledTag =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, canceled, scopeInfo, false);

    assertThat(requestedTag).isEqualTo("acc:org:proj:target:trigger:MERGE_QUEUE:https://code:abc123");
    // the whole abort mechanism depends on these being identical
    assertThat(canceledTag).isEqualTo(requestedTag);
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testGetMergeQueueExecutionTagStaysTriggerScopedWhenAbortOnlySameTriggerEnabled() {
    // generateExecutionTagForEvent drops the trigger identifier under this flag so that PR/PUSH auto-abort can
    // correlate across triggers on the same ref. A merge queue tag must never drop it: it is the idempotency key for
    // checks_requested de-dupe and the correlation key for checks_canceled abort, both scoped to one speculative
    // commit. Without the identifier, two MergeQueue triggers on the same pipeline+repo would collide on the same
    // sha - one trigger's request would be dropped as a false duplicate of the other, and one trigger's cancel would
    // abort the other trigger's execution.
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER);
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_REQUESTED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();

    String executionTag = triggerExecutionHelper.getMergeQueueExecutionTag(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(executionTag).isEqualTo("acc:org:proj:target:MERGE_QUEUE:https://code:abc123:trigger");
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testMergeQueueExecutionTagMatchesTagPersistedBySubmitPath() {
    // The submit path persists its tag via a bare generateExecutionTagForEvent call, not getMergeQueueExecutionTag, so
    // this asserts the two entry points actually agree on a flag-enabled account.
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER);
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_REQUESTED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder()
                                        .setType(Type.WEBHOOK)
                                        .setParsedPayload(ParsedPayload.newBuilder().setMergeQueue(hook).build())
                                        .build();

    String lookupTag = triggerExecutionHelper.getMergeQueueExecutionTag(triggerDetails, hook, scopeInfo, false);
    String persistedTag =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, triggerPayload, scopeInfo, false);

    assertThat(lookupTag).isEqualTo(persistedTag);
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testAbortExecutionsForMergeQueueCancel() {
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_CANCELED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();

    PlanExecution running = PlanExecution.builder().uuid("uuid").build();
    when(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(anyString()))
        .thenReturn(Collections.singletonList(running));
    when(pmsExecutionService.registerInterrupt(any(), any(), any(), any()))
        .thenReturn(InterruptDTO.builder().type(ABORTALL).planExecutionId("uuid").build());

    int aborted = triggerExecutionHelper.abortExecutionsForMergeQueueCancel(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(aborted).isEqualTo(1);
    verify(pmsExecutionService, times(1)).registerInterrupt(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testAbortExecutionsForMergeQueueCancelWithNoInFlightExecutions() {
    // At-least-once delivery: a duplicate or late cancel must not throw when nothing is found.
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_CANCELED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();

    when(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(anyString()))
        .thenReturn(Collections.emptyList());

    int aborted = triggerExecutionHelper.abortExecutionsForMergeQueueCancel(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(aborted).isEqualTo(0);
    verify(pmsExecutionService, never()).registerInterrupt(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testAbortExecutionsForMergeQueueCancelCountsOnlySuccessfulAborts() {
    // registerInterrupt failures are logged and suppressed so one bad execution cannot block the others.
    // The returned count is surfaced in logs as "aborted N execution(s)", so it must reflect the aborts that
    // actually happened rather than the number of candidates found.
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_CANCELED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();

    when(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(anyString()))
        .thenReturn(Arrays.asList(
            PlanExecution.builder().uuid("fails").build(), PlanExecution.builder().uuid("succeeds").build()));
    when(pmsExecutionService.registerInterrupt(any(), eq("fails"), any(), any()))
        .thenThrow(new InvalidRequestException("execution already terminated"));
    when(pmsExecutionService.registerInterrupt(any(), eq("succeeds"), any(), any()))
        .thenReturn(InterruptDTO.builder().type(ABORTALL).planExecutionId("succeeds").build());

    int aborted = triggerExecutionHelper.abortExecutionsForMergeQueueCancel(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(aborted).isEqualTo(1);
    // the failure must not stop the second execution from being attempted
    verify(pmsExecutionService, times(2)).registerInterrupt(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testHasUnterminatedExecutionForMergeQueueWhenExecutionExists() {
    // The speculative commit sha makes the execution tag an exact idempotency key: a checks_requested redelivery
    // for a sha that already has an execution running must be detected so the caller can treat it as a no-op.
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_REQUESTED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();
    when(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(anyString()))
        .thenReturn(Collections.singletonList(PlanExecution.builder().uuid("uuid").build()));

    boolean hasUnterminated = triggerExecutionHelper.hasUnterminatedExecutionForMergeQueue(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(hasUnterminated).isTrue();
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testHasUnterminatedExecutionForMergeQueueWhenNoExecutionExists() {
    MergeQueueHook hook = MergeQueueHook.newBuilder()
                              .setAction(Action.CHECKS_REQUESTED)
                              .setRepo(Repository.newBuilder().setLink("https://code").build())
                              .setSha("abc123")
                              .build();
    when(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(anyString()))
        .thenReturn(Collections.emptyList());

    boolean hasUnterminated = triggerExecutionHelper.hasUnterminatedExecutionForMergeQueue(
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build(), hook, scopeInfo, false);

    assertThat(hasUnterminated).isFalse();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testFetchInputSetYAML() throws Exception {
    NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                                 .accountId("ACCOUNT_ID")
                                                 .orgIdentifier("ORG_IDENTIFIER")
                                                 .projectIdentifier("PROJ_IDENTIFIER")
                                                 .parentUniqueId("PROJ_IDENTIFIER")
                                                 .targetIdentifier("PIPELINE_IDENTIFIER")
                                                 .identifier("IDENTIFIER")
                                                 .name("NAME")
                                                 .targetType(TargetType.PIPELINE)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    List<String> inputSetRefs = Arrays.asList("inputSet1", "inputSet2");
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntityGitSync)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(ParameterField.createValueField(inputSetRefs))
                                   .pipelineBranchName("pipelineBranchName")
                                   .inputYaml("inputsYaml")
                                   .build())
            .build();

    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetResponseDTOPMS = Mockito.mock(Call.class);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync, scopeInfo, false))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());

    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate("ACCOUNT_ID", "ORG_IDENTIFIER", "PROJ_IDENTIFIER",
             "PIPELINE_IDENTIFIER", "pipelineBranchName",
             MergeInputSetRequestDTOPMS.builder()
                 .inputSetReferences(inputSetRefs)
                 .lastYamlToMerge("inputsYaml")
                 .getOnlyFileContent(true)
                 .build()))
        .thenReturn(mergeInputSetResponseDTOPMS);
    when(mergeInputSetResponseDTOPMS.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            MergeInputSetResponseDTOPMS.builder().pipelineYaml("pipelineYaml").isErrorResponse(false).build())));
    assertThat(triggerExecutionHelper.fetchInputSetYAML(
                   triggerDetails, triggerWebhookEvent, inputSetRefs, null, scopeInfo, false))
        .isEqualTo("pipelineYaml");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testFetchInputSetYAMLWithExpression() throws Exception {
    NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                                 .accountId("ACCOUNT_ID")
                                                 .orgIdentifier("ORG_IDENTIFIER")
                                                 .projectIdentifier("PROJ_IDENTIFIER")
                                                 .parentUniqueId("PROJ_IDENTIFIER")
                                                 .targetIdentifier("PIPELINE_IDENTIFIER")
                                                 .identifier("IDENTIFIER")
                                                 .name("NAME")
                                                 .targetType(TargetType.PIPELINE)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    List<String> inputSetRefs = Arrays.asList("inputSet1", "inputSet2");
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntityGitSync)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(ParameterField.createExpressionField(
                                       true, "<+<+trigger.payload.input_set_refs>.split(\",\")>", null, false))
                                   .pipelineBranchName("pipelineBranchName")
                                   .inputYaml("inputsYaml")
                                   .build())
            .build();

    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetResponseDTOPMS = Mockito.mock(Call.class);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync, scopeInfo, false))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());

    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate("ACCOUNT_ID", "ORG_IDENTIFIER", "PROJ_IDENTIFIER",
             "PIPELINE_IDENTIFIER", "pipelineBranchName",
             MergeInputSetRequestDTOPMS.builder()
                 .inputSetReferences(inputSetRefs)
                 .lastYamlToMerge("inputsYaml")
                 .getOnlyFileContent(true)
                 .build()))
        .thenReturn(mergeInputSetResponseDTOPMS);
    when(mergeInputSetResponseDTOPMS.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            MergeInputSetResponseDTOPMS.builder().pipelineYaml("pipelineYaml").isErrorResponse(false).build())));
    assertThat(triggerExecutionHelper.fetchInputSetYAML(
                   triggerDetails, triggerWebhookEvent, inputSetRefs, null, scopeInfo, false))
        .isEqualTo("pipelineYaml");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateTriggeredBy() {
    User user = User.newBuilder().setLogin("login").setEmail("user@email.com").setName("name").build();
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder().uuid("eventId").build();

    TriggeredBy triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPush(
                        PushHook.newBuilder()
                            .setSender(user)
                            .setCommit(Commit.newBuilder().setSha("sourceEventId").setLink("sourceEventLink").build())
                            .build())
                    .build())
            .build(),
        triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "login", "user@email.com", true);

    triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPr(PullRequestHook.newBuilder()
                               .setSender(user)
                               .setPr(PullRequest.newBuilder().setNumber(123).setLink("sourceEventLink").build())
                               .build())
                    .build())
            .build(),
        triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "login", "user@email.com", true);

    triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setRelease(
                        ReleaseHook.newBuilder()
                            .setSender(user)
                            .setRelease(Release.newBuilder().setTag("sourceEventId").setLink("sourceEventLink").build())
                            .build())
                    .build())
            .build(),
        triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "login", "user@email.com", true);

    triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setSourceType(SourceType.HARNESS_REPO)
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPr(PullRequestHook.newBuilder()
                               .setSender(user)
                               .setPr(PullRequest.newBuilder().setNumber(123).setLink("sourceEventLink").build())
                               .build())
                    .build())
            .build(),
        triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "user@email.com", "user@email.com", true);

    Principal servicePrincipal = new ServicePrincipal("svc");
    triggerWebhookEvent.setPrincipal(servicePrincipal);
    triggeredBy = triggerExecutionHelper.generateTriggerdBy(
        null, ngTriggerEntity, TriggerPayload.newBuilder().build(), triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, ngTriggerEntity.getIdentifier(), null, false);

    Principal userPrincipal = new UserPrincipal("user", "mail", "username", "account");
    triggerWebhookEvent.setPrincipal(userPrincipal);
    triggeredBy = triggerExecutionHelper.generateTriggerdBy(
        null, ngTriggerEntity, TriggerPayload.newBuilder().build(), triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "username", "mail", false);

    Principal serviceAccountPrincipal = new ServiceAccountPrincipal("svc", "mail", "username", "account");
    triggerWebhookEvent.setPrincipal(serviceAccountPrincipal);
    triggeredBy = triggerExecutionHelper.generateTriggerdBy(
        null, ngTriggerEntity, TriggerPayload.newBuilder().build(), triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "username", "mail", false);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetSerializedGitSyncContextWithRepoAndFilePath() {
    String repo = "repo";
    String filePath = "filePath";
    String connectorRef = "connectorRef";
    String branch = "branch";
    PipelineEntity pipelineEntityToExecute =
        PipelineEntity.builder().repo(repo).filePath(filePath).connectorRef(connectorRef).build();
    GitSyncBranchContext gitSyncBranchContext =
        triggerExecutionHelper.getGitSyncContextWithRepoAndFilePath(pipelineEntityToExecute, branch);
    when(pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(any(), eq(false)))
        .thenReturn(new PmsGitSyncBranchContextGuard(gitSyncBranchContext, false));
    try (PmsGitSyncBranchContextGuard ignore =
             pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(ByteString.copyFrom(new byte[2]), false)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      assertThat(gitEntityInfo).isNotNull();
      assertThat(gitEntityInfo.getRepoName()).isEqualTo(repo);
      assertThat(gitEntityInfo.getFilePath()).isEqualTo(filePath);
      assertThat(gitEntityInfo.getConnectorRef()).isEqualTo(connectorRef);
      assertThat(gitEntityInfo.getBranch()).isEqualTo(branch);
    }
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testEmptyBranchNameInTrigger() {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntity)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                                        .build();
    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());
    Optional<PipelineEntity> pipelineEntityToExecute =
        Optional.of(PipelineEntity.builder().storeType(StoreType.REMOTE).build());
    doReturn(pipelineEntityToExecute)
        .when(pmsPipelineService)
        .getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false), eq(false),
            eq(false), any(ScopeInfo.class), eq(true));
    assertThatThrownBy(
        ()
            -> triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequestForArtifactManifestPollingFlow(
                triggerDetails, payloadBuilder.build(), null, scopeInfo, false))
        .isInstanceOf(TriggerException.class)
        .hasMessage("Failed while requesting Pipeline Execution through Trigger: Unable to continue trigger execution. "
            + "Pipeline with identifier: target, with org: org, with ProjectId: proj, For Trigger: trigger has "
            + "missing or empty pipelineBranchName in trigger's yaml.");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testBuildInfoForArtifacts() {
    // Test for defined artifact in ArtifactConfigHelper.fetchImagePath(ArtifactTriggerConfig config)
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .source(NGTriggerSourceV2.builder()
                                               .spec(ArtifactTriggerConfig.builder()
                                                         .spec(EcrSpec.builder().imagePath("ecr/image/path").build())
                                                         .build())
                                               .build())
                                   .build())
            .build();
    TriggerPayload payload = TriggerPayload.newBuilder()
                                 .setType(Type.ARTIFACT)
                                 .setArtifactData(ArtifactData.newBuilder().setBuild("1").build())
                                 .build();
    TriggerType type = triggerExecutionHelper.findTriggerType(payload);

    BuildInfo buildinfo = triggerExecutionHelper.getBuildInfoForArtifacts(triggerDetails, type, payload);
    assertNotNull(buildinfo);
    assertThat(buildinfo.getBuild().equals(payload.getArtifactData().getBuild()));
    assertThat(buildinfo.getImagePath().equals("ecr/image/path"));

    // Test for undefined artifact in ArtifactConfigHelper.fetchImagePath(ArtifactTriggerConfig config)
    triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .spec(
                                ArtifactTriggerConfig.builder()
                                    .spec(ArtifactoryRegistrySpec.builder().artifactPath("artifact/image/path").build())
                                    .build())
                            .build())
                    .build())
            .build();

    buildinfo = triggerExecutionHelper.getBuildInfoForArtifacts(triggerDetails, type, payload);
    assertThat(buildinfo.getBuild().equals(payload.getArtifactData().getBuild()));
    assertThat(buildinfo.getImagePath().equals(""));

    payload = TriggerPayload.newBuilder().setType(Type.SCHEDULED).build();
    TriggerType type1 = triggerExecutionHelper.findTriggerType(payload);
    assertThat(type1).isEqualTo(SCHEDULER_CRON);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testBuildInfoForManifest() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .source(NGTriggerSourceV2.builder()
                                               .spec(ManifestTriggerConfig.builder()
                                                         .spec(HelmManifestSpec.builder().chartName("test").build())
                                                         .build())
                                               .build())
                                   .build())
            .build();
    TriggerPayload payload = TriggerPayload.newBuilder()
                                 .setType(Type.MANIFEST)
                                 .setArtifactData(ArtifactData.newBuilder().setBuild("1").build())
                                 .build();
    TriggerType type = triggerExecutionHelper.findTriggerType(payload);

    BuildInfo buildinfo =
        triggerExecutionHelper.getBuildInfoForArtifacts(triggerDetails, TriggerType.ARTIFACT, payload);
    assertNotNull(buildinfo);
    assertThat(buildinfo.getImagePath().equals(""));
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testBuildInfoForWebhook() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().spec(GithubSpec.builder().build()).build())
                                .build())
                    .build())
            .build();
    TriggerPayload payload = TriggerPayload.newBuilder()
                                 .setType(Type.WEBHOOK)
                                 .setArtifactData(ArtifactData.newBuilder().setBuild("1").build())
                                 .build();
    TriggerType type = triggerExecutionHelper.findTriggerType(payload);

    BuildInfo buildinfo = triggerExecutionHelper.getBuildInfoForArtifacts(triggerDetails, type, payload);
    assertNotNull(buildinfo);
    assertThat(buildinfo.getImagePath().equals(""));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResolveRuntimeInputAndSubmitExecutionRequestV1Yaml() {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntity)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                                        .build();
    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());
    doReturn(Optional.of(pipelineEntityV1))
        .when(pmsPipelineService)
        .getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false), eq(false),
            eq(false), any(ScopeInfo.class), eq(true));

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build())
            .metadata(metadata)
            .build();
    TriggerPayload triggerPayload = payloadBuilder.build();
    String executionTagForGitEvent =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, triggerPayload, scopeInfo, false);
    TriggeredBy embeddedUser = triggerExecutionHelper.generateTriggerdBy(executionTagForGitEvent,
        triggerDetails.getNgTriggerEntity(), triggerPayload, triggerWebhookEvent, scopeInfo, false);
    TriggerType triggerType = triggerExecutionHelper.findTriggerType(triggerPayload);
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(triggerType).setTriggeredBy(embeddedUser).build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    PlanExecution expectedPlanExecution = PlanExecution.builder().ambiance(ambiance).build();
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(), any(ScopeInfo.class)))
        .thenReturn(expectedPlanExecution);
    doNothing().when(metricService).recordMetric(any(), anyDouble());
    PlanExecution actualPlanExecution = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(
        triggerDetails, triggerPayload, triggerWebhookEvent, null, null, null, scopeInfo, false);
    assertThat(actualPlanExecution).isEqualToComparingFieldByField(expectedPlanExecution);
  }

  private void assertTriggerBy(TriggeredBy triggeredBy, String identifier, String email, boolean isGitTrigger) {
    Map<String, String> extraInfoMap = triggeredBy.getExtraInfoMap();
    if (isGitTrigger) {
      assertThat(extraInfoMap.containsKey(EXEC_TAG_SET_BY_TRIGGER)).isTrue();
      assertThat(extraInfoMap.containsKey(TRIGGER_REF)).isTrue();
      assertThat(extraInfoMap.get(EXEC_TAG_SET_BY_TRIGGER)).isEqualTo("tag");
      assertThat(extraInfoMap.get(GIT_USER)).isEqualTo(identifier);
      assertThat(extraInfoMap.containsKey(EVENT_CORRELATION_ID)).isTrue();
      assertThat(extraInfoMap.get(EVENT_CORRELATION_ID)).isEqualTo("eventId");
      assertThat(extraInfoMap.get(TRIGGER_REF)).isEqualTo("acc/org/proj/trigger");
      assertThat(extraInfoMap.get(SOURCE_EVENT_ID))
          .isIn(Arrays.asList(
              "123", "sourceEventId", StringUtils.substring("sourceEventId", 0, COMMIT_SHA_STRING_LENGTH)));
      assertThat(extraInfoMap.get(SOURCE_EVENT_LINK)).isEqualTo("sourceEventLink");
    }
    assertThat(triggeredBy.getIdentifier()).isEqualTo(identifier);
    assertThat(triggeredBy.getExtraInfoMap().get("email")).isEqualTo(email);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetCleanRuntimeInputYamlPipelineWithNoInputs() {
    String pipelineYaml = readFile("pipeline.yml");
    String runtimeInputYaml = "pipeline: {}\n";
    String cleanedRuntimeInputYaml = triggerExecutionHelper.getCleanRuntimeInputYaml(pipelineYaml, runtimeInputYaml);
    assertThat(cleanedRuntimeInputYaml).isEqualTo("");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetCleanRuntimeInputYamlPipelineWithInputs() {
    String pipelineYaml = readFile("pipeline-with-variables.yml");
    String triggerYaml = readFile("trigger-with-inputs.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    NGTriggerConfigV2 ngTriggerConfigV2 = elementMapper.toTriggerConfigV2(triggerYaml);
    String runtimeInputYaml = ngTriggerConfigV2.getInputYaml();
    String cleanedRuntimeInputYaml = triggerExecutionHelper.getCleanRuntimeInputYaml(pipelineYaml, runtimeInputYaml);
    assertThat(cleanedRuntimeInputYaml).isEqualTo(runtimeInputYaml);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionForPipelineWithNoInputs() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    String triggerYaml = readFile("trigger-without-inputs.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    TriggerDetails triggerDetails = elementMapper.toTriggerDetails("acc", "default", "test", null, triggerYaml, true);

    // Set parentUniqueId on the trigger entity to match the expected ScopeInfo
    triggerDetails.getNgTriggerEntity().setParentUniqueId("unique-id");

    when(pmsPipelineService.getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                (PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build()))
            .metadata(metadata)
            .build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq("acc"), eq("default"), eq("test"), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().createdAt(1L).build(), triggerDetails.getNgTriggerConfigV2().getInputYaml());
    ArgumentCaptor<String> capturedRuntimeInputYaml = ArgumentCaptor.forClass(String.class);
    verify(executionHelper, times(1))
        .buildExecutionArgs(eq(pipelineEntity), eq(null), capturedRuntimeInputYaml.capture(),
            eq(Collections.emptyList()), eq(Collections.emptyMap()), eq(null), eq(null), eq(retryExecutionParameters),
            eq(false), eq(false), any(PlanExecutionMetadataWithContext.class), eq(true), any(ScopeInfo.class));
    assertThat(capturedRuntimeInputYaml.getValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionWithNullTriggerWebhookEvent() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    String triggerYaml = readFile("trigger-without-inputs.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    TriggerDetails triggerDetails = elementMapper.toTriggerDetails("acc", "default", "test", null, triggerYaml, true);

    // Set parentUniqueId on the trigger entity to match the expected ScopeInfo
    triggerDetails.getNgTriggerEntity().setParentUniqueId("unique-id");

    when(pmsPipelineService.getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build())
            .metadata(metadata)
            .build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq("acc"), eq("default"), eq("test"), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().createdAt(1L).build(), triggerDetails.getNgTriggerConfigV2().getInputYaml());

    Principal expectedPrincipal = new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId());
    assertThat(SecurityContextBuilder.getPrincipal()).isEqualToComparingFieldByField(expectedPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualToComparingFieldByField(expectedPrincipal);
    verify(metricService, times(1)).recordMetric(any(), anyDouble());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().build(), triggerDetails.getNgTriggerConfigV2().getInputYaml());
    verify(metricService, times(1)).recordMetric(any(), anyDouble());

    triggerExecutionHelper.createPlanExecution(
        triggerDetails, null, null, null, null, null, null, triggerDetails.getNgTriggerConfigV2().getInputYaml());
    verify(metricService, times(1)).recordMetric(any(), anyDouble());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionWithStageSelectionAsExpression() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    String triggerYaml = readFile("trigger-with-multi-stages.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    TriggerDetails triggerDetails = elementMapper.toTriggerDetails("acc", "default", "test", null, triggerYaml, true);

    // Set parentUniqueId on the trigger entity to match the expected ScopeInfo
    triggerDetails.getNgTriggerEntity().setParentUniqueId("unique-id");

    when(pmsPipelineService.getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build())
            .metadata(metadata)
            .build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq("acc"), eq("default"), eq("test"), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().createdAt(1L).payload("{\"stages_to_execute\": \"s1\"}").build(),
        triggerDetails.getNgTriggerConfigV2().getInputYaml());

    verify(metricService, times(1)).recordMetric(any(), anyDouble());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testNoGitXContextLeakFromCreatePlanExecution() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    String triggerYaml = readFile("trigger-without-inputs.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    TriggerDetails triggerDetails = elementMapper.toTriggerDetails("acc", "default", "test", null, triggerYaml, true);

    // Set parentUniqueId on the trigger entity to match the expected ScopeInfo
    triggerDetails.getNgTriggerEntity().setParentUniqueId("unique-id");

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("branch").build();
    ScmGitMetaData scmGitMetaData = ScmGitMetaData.builder().filePath("filepath").branchName("branch").build();
    when(pmsPipelineService.getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenAnswer((Answer<Optional<PipelineEntity>>) invocation -> {
          GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
          GitAwareContextHelper.updateScmGitMetaData(scmGitMetaData);
          return Optional.of(pipelineEntity);
        });
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build())
            .metadata(metadata)
            .build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq("acc"), eq("default"), eq("test"), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().createdAt(1L).build(), triggerDetails.getNgTriggerConfigV2().getInputYaml());
    verify(pmsPipelineService, times(1))
        .getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false), eq(false), eq(false),
            any(ScopeInfo.class), eq(true));
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo())
        .isEqualToComparingFieldByField(GitEntityInfo.builder().build());
    assertThat(GitAwareContextHelper.getScmGitMetaData())
        .isEqualToComparingFieldByField(ScmGitMetaData.builder().build());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testNoGitXContextLeakIntoCreatePlanExecution() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .parentUniqueId(uniqueId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    String triggerYaml = readFile("trigger-without-inputs.yml");
    NGTriggerElementMapper elementMapper =
        new NGTriggerElementMapper(null, null, null, null, null, null, settingsClient, null);
    TriggerDetails triggerDetails =
        elementMapper.toTriggerDetails("acc", "default", "test", "unique-id", triggerYaml, true);

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("branch").build();
    ScmGitMetaData scmGitMetaData = ScmGitMetaData.builder().filePath("filepath").branchName("branch").build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    GitAwareContextHelper.updateScmGitMetaData(scmGitMetaData);
    when(scopeResolutionService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    when(pmsPipelineService.getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenAnswer((Answer<Optional<PipelineEntity>>) invocation -> {
          if (isNotEmpty(GitAwareContextHelper.getGitRequestParamsInfo().getBranch())
              || isNotEmpty(GitAwareContextHelper.getBranchInSCMGitMetadata())
              || isNotEmpty(GitAwareContextHelper.getScmGitMetaData().getFilePath())) {
            throw new Exception("Outer GitX Context was leaked into CreatePlanExecution!");
          }
          return Optional.of(pipelineEntity);
        });
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    ExecArgs execArgs =
        ExecArgs.builder()
            .planExecutionMetadataWithContext(
                PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build())
            .metadata(metadata)
            .build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq("acc"), eq("default"), eq("test"), eq(execArgs.getMetadata()),
             eq(execArgs.getPlanExecutionMetadataWithContext()), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());

    triggerExecutionHelper.createPlanExecution(triggerDetails, null, null, null, null, null,
        TriggerWebhookEvent.builder().createdAt(1L).build(), triggerDetails.getNgTriggerConfigV2().getInputYaml());
    verify(pmsPipelineService, times(1))
        .getPipeline(eq("acc"), eq("default"), eq("test"), eq("myPipeline"), eq(false), eq(false), eq(false), eq(false),
            any(ScopeInfo.class), eq(true));
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo())
        .isEqualToComparingFieldByField(GitEntityInfo.builder().build());
    assertThat(GitAwareContextHelper.getScmGitMetaData())
        .isEqualToComparingFieldByField(ScmGitMetaData.builder().build());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testSetPrincipal() {
    // Check service-principal case
    Principal servicePrincipal = new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId());
    TriggerWebhookEvent triggerWebhookEventWithoutPrincipal = TriggerWebhookEvent.builder().principal(null).build();
    triggerExecutionHelper.setPrincipal(triggerWebhookEventWithoutPrincipal);
    assertThat(SecurityContextBuilder.getPrincipal()).isEqualToComparingFieldByField(servicePrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualToComparingFieldByField(servicePrincipal);

    // Check user-principal case
    Principal userPrincipal = new UserPrincipal("user", "mail", "username", "account");
    TriggerWebhookEvent triggerWebhookEventWithPrincipal =
        TriggerWebhookEvent.builder().principal(userPrincipal).build();
    triggerExecutionHelper.setPrincipal(triggerWebhookEventWithPrincipal);
    assertThat(SecurityContextBuilder.getPrincipal()).isEqualToComparingFieldByField(userPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualToComparingFieldByField(userPrincipal);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSetPrincipalWithExecutorIdentity() {
    Principal executorPrincipal = new UserPrincipal("executorId", "executor@mail.com", "executorName", accountId);
    NGTriggerEntity entityWithExecutor = NGTriggerEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier("testTrigger")
                                             .name("Test Trigger")
                                             .type(NGTriggerType.WEBHOOK)
                                             .targetIdentifier(pipelineId)
                                             .targetType(TargetType.PIPELINE)
                                             .yaml("yaml")
                                             .executorInfo(TriggerExecutorDTO.builder()
                                                               .identifier("executorId")
                                                               .name("executorName")
                                                               .email("executor@mail.com")
                                                               .type(TriggerExecutorDTO.ExecutorType.USER)
                                                               .build())
                                             .build();

    when(triggerExecutorResolver.resolveExecutorPrincipal(entityWithExecutor)).thenReturn(executorPrincipal);
    doNothing().when(triggerExecutorResolver).setExecutorContext(executorPrincipal);
    doNothing().when(triggerExecutorResolver).validateExecutorPermissionsForExecution(entityWithExecutor);

    triggerExecutionHelper.setPrincipal(null, entityWithExecutor);

    verify(triggerExecutorResolver, times(1)).validateExecutorPermissionsForExecution(entityWithExecutor);
    verify(triggerExecutorResolver, times(1)).resolveExecutorPrincipal(entityWithExecutor);
    verify(triggerExecutorResolver, times(1)).setExecutorContext(executorPrincipal);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGenerateTriggeredByIncludesExecutorType() {
    Principal executorPrincipal =
        new ServiceAccountPrincipal("executorId", "executor@mail.com", "executorName", accountId);
    NGTriggerEntity entityWithExecutor = NGTriggerEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier("testTrigger")
                                             .name("Test Trigger")
                                             .type(NGTriggerType.WEBHOOK)
                                             .targetIdentifier(pipelineId)
                                             .targetType(TargetType.PIPELINE)
                                             .executorInfo(TriggerExecutorDTO.builder()
                                                               .identifier("executorId")
                                                               .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                                               .build())
                                             .build();
    when(triggerExecutorResolver.resolveExecutorPrincipal(entityWithExecutor)).thenReturn(executorPrincipal);

    TriggeredBy triggeredBy = triggerExecutionHelper.generateTriggerdBy(
        null, entityWithExecutor, TriggerPayload.newBuilder().build(), null, scopeInfo, false);

    assertThat(triggeredBy.getExtraInfoMap().get(PmsEventMonitoringConstants.EXECUTOR_TYPE))
        .isEqualTo(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT.name());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSetPrincipalFailsWhenExecutorLacksPipelinePermissionsAtExecution() {
    NGTriggerEntity entityWithExecutor = NGTriggerEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier("testTrigger")
                                             .name("Test Trigger")
                                             .type(NGTriggerType.WEBHOOK)
                                             .targetIdentifier(pipelineId)
                                             .targetType(TargetType.PIPELINE)
                                             .yaml("yaml")
                                             .executorInfo(TriggerExecutorDTO.builder()
                                                               .identifier("serviceAccountId")
                                                               .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                                               .build())
                                             .build();

    doThrow(new NGAccessDeniedException(
                "'serviceAccountId' does not have permission to run pipeline 'pipeline1'. Grant pipeline execute, "
                    + "edit, create, delete, or abort on that pipeline.",
                null, null))
        .when(triggerExecutorResolver)
        .validateExecutorPermissionsForExecution(entityWithExecutor);

    assertThatThrownBy(() -> triggerExecutionHelper.setPrincipal(null, entityWithExecutor))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");

    verify(triggerExecutorResolver, times(1)).validateExecutorPermissionsForExecution(entityWithExecutor);
    verify(triggerExecutorResolver, never()).resolveExecutorPrincipal(entityWithExecutor);
    verify(triggerExecutorResolver, never()).setExecutorContext(any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSetPrincipalFailsWhenEnforceEnabledAndExecutorMissing() {
    NGTriggerEntity entityWithoutExecutor = NGTriggerEntity.builder()
                                                .accountId(accountId)
                                                .orgIdentifier(orgId)
                                                .projectIdentifier(projectId)
                                                .identifier("testTrigger")
                                                .name("Test Trigger")
                                                .type(NGTriggerType.WEBHOOK)
                                                .targetIdentifier(pipelineId)
                                                .targetType(TargetType.PIPELINE)
                                                .yaml("yaml")
                                                .build();

    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENFORCE_TRIGGER_EXECUTOR_IDENTITY))
        .thenReturn(true);
    doThrow(new InvalidRequestException(
                "Trigger 'testTrigger' requires an executor before it can run. Configure executor identity on the "
                + "trigger."))
        .when(triggerExecutorResolver)
        .validateExecutorRequiredForExecution(entityWithoutExecutor, true);

    assertThatThrownBy(() -> triggerExecutionHelper.setPrincipal(null, entityWithoutExecutor))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("requires an executor");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testSetPrincipalFallsBackToServicePrincipalWhenNoExecutor() {
    Principal servicePrincipal2 = new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId());
    NGTriggerEntity entityWithoutExecutor = NGTriggerEntity.builder()
                                                .accountId(accountId)
                                                .orgIdentifier(orgId)
                                                .projectIdentifier(projectId)
                                                .identifier("testTrigger")
                                                .name("Test Trigger")
                                                .type(NGTriggerType.WEBHOOK)
                                                .targetIdentifier(pipelineId)
                                                .targetType(TargetType.PIPELINE)
                                                .yaml("yaml")
                                                .build();

    triggerExecutionHelper.setPrincipal(null, entityWithoutExecutor);

    assertThat(SecurityContextBuilder.getPrincipal()).isEqualToComparingFieldByField(servicePrincipal2);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualToComparingFieldByField(servicePrincipal2);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testSetPrincipalExecutorTakesPriorityOverWebhookPrincipal() {
    Principal executorPrincipal = new UserPrincipal("executorId", "executor@mail.com", "executorName", accountId);
    Principal webhookPrincipal = new UserPrincipal("webhookUser", "webhook@mail.com", "webhookName", accountId);

    NGTriggerEntity entityWithExecutor = NGTriggerEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier("testTrigger")
                                             .name("Test Trigger")
                                             .type(NGTriggerType.WEBHOOK)
                                             .targetIdentifier(pipelineId)
                                             .targetType(TargetType.PIPELINE)
                                             .yaml("yaml")
                                             .executorInfo(TriggerExecutorDTO.builder()
                                                               .identifier("executorId")
                                                               .name("executorName")
                                                               .email("executor@mail.com")
                                                               .type(TriggerExecutorDTO.ExecutorType.USER)
                                                               .build())
                                             .build();
    TriggerWebhookEvent webhookEvent = TriggerWebhookEvent.builder().principal(webhookPrincipal).build();

    when(triggerExecutorResolver.resolveExecutorPrincipal(entityWithExecutor)).thenReturn(executorPrincipal);
    doNothing().when(triggerExecutorResolver).setExecutorContext(executorPrincipal);

    triggerExecutionHelper.setPrincipal(webhookEvent, entityWithExecutor);

    verify(triggerExecutorResolver, times(1)).resolveExecutorPrincipal(entityWithExecutor);
    verify(triggerExecutorResolver, times(1)).setExecutorContext(executorPrincipal);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForCustomTriggerSuccess() {
    String payload = "{\"branch\":\"branchValue\"}";
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM").payload(payload).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    assertThat(triggerExecutionHelper.resolveBranchExpression("<+trigger.branch>", event, null))
        .isEqualTo("branchValue");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForHarnessArtifactRegistryTriggerSuccess() {
    String payload = "{\"artifact_info\": {\"version\":\"payloadValue\"}}";
    TriggerWebhookEvent event =
        TriggerWebhookEvent.builder().sourceRepoType("HARNESS_ARTIFACT_REGISTRY").payload(payload).build();
    assertThat(triggerExecutionHelper.resolveBranchExpression("<+trigger.payload.artifact_info.version>", event, null))
        .isEqualTo("payloadValue");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForCustomTriggerFailure() {
    String payload = "{}";
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM").payload(payload).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    assertThatThrownBy(() -> triggerExecutionHelper.resolveBranchExpression("<+trigger.branch>", event, null))
        .isInstanceOf(TriggerException.class)
        .hasMessage("Please ensure the expression <+trigger.branch> has the right branch information");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForCustomTriggerWithCustomExpression() {
    String payload = "{\"repository\":{\"name\":\"main\"}}";
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM").payload(payload).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(any(), (FeatureName) any());
    String resolvedBranch =
        triggerExecutionHelper.resolveBranchExpression("<+trigger.payload.repository.name>", event, null);
    assertThat(resolvedBranch).isEqualTo("main");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForCustomTriggerWithEmptyBranchExpression() {
    String payload = "{\"branch\":\"branchValue\"}";
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM").payload(payload).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(any(), (FeatureName) any());
    assertThat(triggerExecutionHelper.resolveBranchExpression("", event, null)).isEqualTo("branchValue");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForCustomTriggerWithNullBranchExpression() {
    String payload = "{\"branch\":\"branchValue\"}";
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().sourceRepoType("CUSTOM").payload(payload).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(any(), (FeatureName) any());
    assertThat(triggerExecutionHelper.resolveBranchExpression(null, event, null)).isEqualTo("branchValue");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveBranchExpressionForWebhookTriggerUsingTriggerPayload() {
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION);
    String payload = "{\"branch\":\"branchValue\"}";
    TriggerWebhookEvent event =
        TriggerWebhookEvent.builder().sourceRepoType("Github").payload(payload).accountId(accountId).build();
    when(webhookEventPayloadParser.parseEvent(event))
        .thenReturn(
            WebhookPayloadData.builder().originalEvent(TriggerWebhookEvent.builder().payload(payload).build()).build());
    TriggerPayload prTriggerPayload =
        TriggerPayload.newBuilder()
            .setType(Type.GIT)
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setPr(
                        PullRequestHook.newBuilder()
                            .setPr(
                                PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                            .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                            .build())
                    .build())
            .build();
    assertThat(triggerExecutionHelper.resolveBranchExpression("<+trigger.branch>", event, prTriggerPayload))
        .isEqualTo("source");
    TriggerPayload pushTriggerPayload =
        TriggerPayload.newBuilder()
            .setType(Type.GIT)
            .setParsedPayload(ParsedPayload.newBuilder()
                                  .setPush(PushHook.newBuilder()
                                               .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                                               .setRef("ref")
                                               .build())
                                  .build())
            .build();
    assertThat(triggerExecutionHelper.resolveBranchExpression("<+trigger.branch>", event, pushTriggerPayload))
        .isEqualTo("ref");
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionForTriggerBasedExecutionsWithSecretExpressionsInPayload() {
    TriggerPayload pushTriggerPayload =
        TriggerPayload.newBuilder()
            .setType(Type.GIT)
            .setParsedPayload(ParsedPayload.newBuilder()
                                  .setPush(PushHook.newBuilder()
                                               .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                                               .setRef("<+secrets.getValue('test'>")
                                               .build())
                                  .build())
            .build();
    pushTriggerPayload = triggerExecutionHelper.maskSecretExpressionsInTriggerPayload(pushTriggerPayload);
    assertThat(pushTriggerPayload.getParsedPayload().getPush().getRef().equals("<invalid.secrets.getValue('test')>"));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGenerateTriggeredByForAzure() {
    User user = User.newBuilder().setLogin("login").setEmail("user@email.com").setName("name").build();
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder().uuid("eventId").build();

    TriggeredBy triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setSourceType(SourceType.AZURE_REPO)
            .setParsedPayload(
                ParsedPayload.newBuilder()
                    .setRelease(
                        ReleaseHook.newBuilder()
                            .setSender(user)
                            .setRelease(Release.newBuilder().setTag("sourceEventId").setLink("sourceEventLink").build())
                            .build())
                    .build())
            .build(),
        triggerWebhookEvent, scopeInfo, false);

    assertTriggerBy(triggeredBy, "user@email.com", "user@email.com", true);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testResolveInputSetBranch_WithLiteralValue() {
    // Given
    ParameterField<String> inputSetBranchName = ParameterField.createValueField("feature-branch");
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder().inputSetBranchName(inputSetBranchName).build();

    TriggerWebhookEvent localTriggerWebhookEvent = TriggerWebhookEvent.builder().build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    // When
    String result =
        triggerExecutionHelper.resolveInputSetBranch(triggerConfig, localTriggerWebhookEvent, triggerPayload);

    // Then
    assertThat(result).isEqualTo("feature-branch");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testResolveInputSetBranch_WithExpression() {
    // Given - create spy and mock expression resolution
    TriggerExecutionHelper spyHelper = spy(triggerExecutionHelper);
    String mockResolvedBranch = "resolved-branch";

    ParameterField<String> inputSetBranchName =
        ParameterField.createExpressionField(true, "<+trigger.sourceBranch>", null, true);
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder().inputSetBranchName(inputSetBranchName).build();

    TriggerWebhookEvent testTriggerWebhookEvent = TriggerWebhookEvent.builder().build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    doReturn(mockResolvedBranch)
        .when(spyHelper)
        .resolveBranchExpression(eq("<+trigger.sourceBranch>"), eq(testTriggerWebhookEvent), eq(triggerPayload));

    // When
    String result = spyHelper.resolveInputSetBranch(triggerConfig, testTriggerWebhookEvent, triggerPayload);

    // Then
    assertThat(result).isEqualTo("resolved-branch");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testResolveInputSetBranch_WithNull() {
    // Given
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder().inputSetBranchName(null).build();

    TriggerWebhookEvent localTriggerWebhookEvent = TriggerWebhookEvent.builder().build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    // When
    String result =
        triggerExecutionHelper.resolveInputSetBranch(triggerConfig, localTriggerWebhookEvent, triggerPayload);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_WithInputSetRefs_SetsInputSetBranchName() {
    // Given: Create trigger with inputSetRefs and inputSetBranchName
    String pipelineYaml = readFile("pipeline.yml");
    ParameterField<String> inputSetBranchName = ParameterField.createValueField("feature-branch");
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .inputSetRefs(ParameterField.createValueField(Arrays.asList("inputSet1")))
                                          .inputSetBranchName(inputSetBranchName)
                                          .inputYaml("pipeline: {}\n")
                                          .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(triggerConfig).build();

    // Setup mocks
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));

    PlanExecutionMetadata testPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(testPlanExecutionMetadata).build();
    ExecArgs execArgs = ExecArgs.builder()
                            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                            .metadata(metadata)
                            .build();

    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), eq(execArgs.getMetadata()),
             eq(execArgs.getPlanExecutionMetadataWithContext()), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);

    // When
    triggerExecutionHelper.createPlanExecution(
        triggerDetails, null, null, null, null, null, null, triggerConfig.getInputYaml());

    // Then: inputSetBranchName should be set
    assertThat(planExecutionMetadataWithContext.getInputSetBranchName()).isEqualTo("feature-branch");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_WithInputSetRefs_NoInputSetBranchNameWhenResolvedBranchIsNull() {
    // Given: Create trigger with inputSetRefs but inputSetBranchName is null
    String pipelineYaml = readFile("pipeline.yml");
    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .inputSetRefs(ParameterField.createValueField(Arrays.asList("inputSet1")))
                                          .inputSetBranchName(null)
                                          .inputYaml("pipeline: {}\n")
                                          .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(triggerConfig).build();

    // Setup mocks
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();
    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), eq(true)))
        .thenReturn(Optional.of(pipelineEntity));

    PlanExecutionMetadata testPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(testPlanExecutionMetadata).build();
    ExecArgs execArgs = ExecArgs.builder()
                            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                            .metadata(metadata)
                            .build();

    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), eq(execArgs.getMetadata()),
             eq(execArgs.getPlanExecutionMetadataWithContext()), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);

    // When
    triggerExecutionHelper.createPlanExecution(
        triggerDetails, null, null, null, null, null, null, triggerConfig.getInputYaml());

    // Then: inputSetBranchName should NOT be set (remains null)
    assertThat(planExecutionMetadataWithContext.getInputSetBranchName()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_BlockedByOpaGitxPolicy_ThrowsPolicyEvaluationFailureException() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .source(NGTriggerSourceV2.builder()
                                                      .type(NGTriggerType.WEBHOOK)
                                                      .spec(WebhookTriggerConfigV2.builder()
                                                                .type(WebhookTriggerType.GITHUB)
                                                                .spec(GithubSpec.builder()
                                                                          .type(GithubTriggerEvent.PUSH)
                                                                          .spec(GithubPRSpec.builder().build())
                                                                          .build())
                                                                .build())
                                                      .build())
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(triggerConfig).build();

    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    PlanExecutionMetadata testPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext testMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(testPlanExecutionMetadata).build();
    ExecArgs execArgs =
        ExecArgs.builder().planExecutionMetadataWithContext(testMetadataWithContext).metadata(metadata).build();

    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);

    OpaOnSaveStatusDTO opaStatus = OpaOnSaveStatusDTO.builder().status(OpaGitxStatus.ERROR).build();
    doThrow(new PolicyEvaluationFailureException("Policy denied", opaStatus))
        .when(pipelineExecutor)
        .applyOpaOnSaveGate(any(PipelineEntity.class), any(ExecArgs.class), any());

    assertThatThrownBy(
        ()
            -> triggerExecutionHelper.createPlanExecution(triggerDetails, TriggerPayload.newBuilder().build(), null,
                null, null, null, TriggerWebhookEvent.builder().createdAt(1L).build(), null))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .satisfies(ex -> {
          PolicyEvaluationFailureException pefe = (PolicyEvaluationFailureException) ex;
          assertThat(pefe.getMessage()).contains("Policy denied");
          assertThat(pefe.getOpaOnSaveStatusDTO()).isNotNull();
          assertThat(pefe.getOpaOnSaveStatusDTO().getStatus()).isEqualTo(OpaGitxStatus.ERROR);
        });
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_OpaGitxKillSwitchEnabled_BypassesEnforcement() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .source(NGTriggerSourceV2.builder()
                                                      .type(NGTriggerType.WEBHOOK)
                                                      .spec(WebhookTriggerConfigV2.builder()
                                                                .type(WebhookTriggerType.GITHUB)
                                                                .spec(GithubSpec.builder()
                                                                          .type(GithubTriggerEvent.PUSH)
                                                                          .spec(GithubPRSpec.builder().build())
                                                                          .build())
                                                                .build())
                                                      .build())
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(triggerConfig).build();

    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    PlanExecutionMetadata testPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext testMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(testPlanExecutionMetadata).build();
    ExecArgs execArgs =
        ExecArgs.builder().planExecutionMetadataWithContext(testMetadataWithContext).metadata(metadata).build();

    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);

    doNothing().when(pipelineExecutor).applyOpaOnSaveGate(any(), any(), any());

    PlanExecution result =
        triggerExecutionHelper.createPlanExecution(triggerDetails, TriggerPayload.newBuilder().build(), null, null,
            null, null, TriggerWebhookEvent.builder().createdAt(1L).build(), null);

    assertNotNull(result);
    verify(pipelineExecutor, times(1)).applyOpaOnSaveGate(any(PipelineEntity.class), any(ExecArgs.class), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_OpaGitxNotBlocked_ProceedsToExecution() {
    String pipelineYaml = readFile("pipeline.yml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    NGTriggerConfigV2 triggerConfig = NGTriggerConfigV2.builder()
                                          .source(NGTriggerSourceV2.builder()
                                                      .type(NGTriggerType.WEBHOOK)
                                                      .spec(WebhookTriggerConfigV2.builder()
                                                                .type(WebhookTriggerType.GITHUB)
                                                                .spec(GithubSpec.builder()
                                                                          .type(GithubTriggerEvent.PUSH)
                                                                          .spec(GithubPRSpec.builder().build())
                                                                          .build())
                                                                .build())
                                                      .build())
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).ngTriggerConfigV2(triggerConfig).build();

    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    PlanExecutionMetadata testPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext testMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(testPlanExecutionMetadata).build();
    ExecArgs execArgs =
        ExecArgs.builder().planExecutionMetadataWithContext(testMetadataWithContext).metadata(metadata).build();

    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);

    doNothing().when(pipelineExecutor).applyOpaOnSaveGate(any(), any(), any());

    PlanExecution result =
        triggerExecutionHelper.createPlanExecution(triggerDetails, TriggerPayload.newBuilder().build(), null, null,
            null, null, TriggerWebhookEvent.builder().createdAt(1L).build(), null);

    assertNotNull(result);
    verify(pipelineExecutor, times(1)).applyOpaOnSaveGate(eq(pipelineEntity), eq(execArgs), any(ScopeInfo.class));
    verify(executionHelper, times(1)).startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(), any());
  }

  private ExecArgs setupCreatePlanExecutionMocks() {
    // V1 pipeline so the V0 runtime input cleanup (which can rewrite the yaml) is skipped deterministically.
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml("pipeline:\n  name: p1\n")
                                        .harnessVersion(HarnessYamlVersion.V1)
                                        .build();
    when(pmsPipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(false), eq(false),
             eq(false), eq(false), any(ScopeInfo.class), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    PlanExecutionMetadataWithContext metadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(PlanExecutionMetadata.builder().build())
            .build();
    ExecArgs execArgs =
        ExecArgs.builder().planExecutionMetadataWithContext(metadataWithContext).metadata(metadata).build();
    when(executionHelper.buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(),
             any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
             any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class)))
        .thenReturn(execArgs);
    when(executionHelper.startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(), any(ScopeInfo.class)))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(any(), any(FeatureName.class))).thenReturn(false);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_RESOLVE_TRIGGER_EXPRESSIONS_IN_RUNTIME_INPUT);
    return execArgs;
  }

  private TriggerDetails buildTriggerDetailsWithInputYaml(String inputYaml) {
    return TriggerDetails.builder()
        .ngTriggerEntity(ngTriggerEntity)
        .ngTriggerConfigV2(NGTriggerConfigV2.builder().inputYaml(inputYaml).build())
        .build();
  }

  private String captureRuntimeInputYamlPassedToBuildExecutionArgs() {
    ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), any(), yamlCaptor.capture(), any(), any(), any(), any(),
            any(RetryExecutionParameters.class), anyBoolean(), anyBoolean(),
            any(PlanExecutionMetadataWithContext.class), anyBoolean(), any(ScopeInfo.class));
    return yamlCaptor.getValue();
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionSkipsTriggerResolutionWhenNoTriggerExpressions() {
    setupCreatePlanExecutionMocks();
    String inputYaml = "pipeline: {}\n";

    triggerExecutionHelper.createPlanExecution(
        buildTriggerDetailsWithInputYaml(inputYaml), null, null, null, null, null, null, inputYaml);

    verify(yamlExpressionResolveHelper, times(0)).resolveExpressionsInYaml(any(), any(), any());
    assertThat(captureRuntimeInputYamlPassedToBuildExecutionArgs()).isEqualTo(inputYaml);
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionResolvesTriggerExpressionsBeforeBuildExecutionArgs() {
    setupCreatePlanExecutionMocks();
    String inputYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+trigger.payload.input>\n";
    String resolvedYaml = "pipeline:\n  variables:\n  - name: var1\n    value: hello-world\n";
    doReturn(resolvedYaml)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(eq(inputYaml), any(EngineExpressionEvaluator.class), eq(accountId));

    triggerExecutionHelper.createPlanExecution(
        buildTriggerDetailsWithInputYaml(inputYaml), null, null, null, null, null, null, inputYaml);

    assertThat(captureRuntimeInputYamlPassedToBuildExecutionArgs()).isEqualTo(resolvedYaml);
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionResolvesAgainstMaskedPayloadWhenMaskingEnabled() {
    setupCreatePlanExecutionMocks();
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.PIPE_MASK_SECRET_EXPRESSIONS_IN_TRIGGER_PAYLOAD);
    String payload = "{\"input\": \"<+secrets.getValue('pw')>\"}";
    String inputYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+trigger.payload.input>\n";
    doAnswer(invocation -> invocation.getArgument(0))
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(any(), any(EngineExpressionEvaluator.class), any());

    triggerExecutionHelper.createPlanExecution(buildTriggerDetailsWithInputYaml(inputYaml),
        TriggerPayload.newBuilder().build(), payload, null, null, null, null, inputYaml);

    ArgumentCaptor<EngineExpressionEvaluator> evaluatorCaptor =
        ArgumentCaptor.forClass(EngineExpressionEvaluator.class);
    verify(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(eq(inputYaml), evaluatorCaptor.capture(), eq(accountId));
    assertThat(evaluatorCaptor.getValue().renderExpression(
                   "<+trigger.payload.input>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<invalid.secrets.getValue('pw')>");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionResolvesAgainstRawPayloadWhenMaskingDisabled() {
    setupCreatePlanExecutionMocks();
    String payload = "{\"input\": \"<+secrets.getValue('pw')>\"}";
    String inputYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+trigger.payload.input>\n";
    doAnswer(invocation -> invocation.getArgument(0))
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(any(), any(EngineExpressionEvaluator.class), any());

    triggerExecutionHelper.createPlanExecution(buildTriggerDetailsWithInputYaml(inputYaml),
        TriggerPayload.newBuilder().build(), payload, null, null, null, null, inputYaml);

    ArgumentCaptor<EngineExpressionEvaluator> evaluatorCaptor =
        ArgumentCaptor.forClass(EngineExpressionEvaluator.class);
    verify(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(eq(inputYaml), evaluatorCaptor.capture(), eq(accountId));
    // The evaluator serves the raw payload value as-is, mirroring what execution-time resolution
    // would serve for an unmasked account today.
    assertThat(evaluatorCaptor.getValue().renderExpression(
                   "<+trigger.payload.input>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+secrets.getValue('pw')>");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionFallsBackToOriginalYamlWhenResolutionFails() {
    setupCreatePlanExecutionMocks();
    String inputYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+trigger.payload.input>\n";
    doThrow(new RuntimeException("resolution failure"))
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(any(), any(EngineExpressionEvaluator.class), any());

    triggerExecutionHelper.createPlanExecution(
        buildTriggerDetailsWithInputYaml(inputYaml), null, null, null, null, null, null, inputYaml);

    assertThat(captureRuntimeInputYamlPassedToBuildExecutionArgs()).isEqualTo(inputYaml);
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionSkipsTriggerResolutionWhenFeatureFlagDisabled() {
    setupCreatePlanExecutionMocks();
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_RESOLVE_TRIGGER_EXPRESSIONS_IN_RUNTIME_INPUT);
    String inputYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+trigger.payload.input>\n";

    triggerExecutionHelper.createPlanExecution(
        buildTriggerDetailsWithInputYaml(inputYaml), null, null, null, null, null, null, inputYaml);

    verify(yamlExpressionResolveHelper, times(0)).resolveExpressionsInYaml(any(), any(), any());
    assertThat(captureRuntimeInputYamlPassedToBuildExecutionArgs()).isEqualTo(inputYaml);
  }
}
