/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.beans.FeatureName.CDS_RESOLVE_CUSTOM_TRIGGER_EXPRESSION;
import static io.harness.beans.FeatureName.CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION;
import static io.harness.beans.FeatureName.CI_YAML_VERSIONING;
import static io.harness.beans.FeatureName.PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER;
import static io.harness.beans.FeatureName.PIPE_MASK_SECRET_EXPRESSIONS_IN_TRIGGER_PAYLOAD;
import static io.harness.beans.FeatureName.PIPE_RESOLVE_TRIGGER_EXPRESSIONS_IN_RUNTIME_INPUT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.common.ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
import static io.harness.expression.common.ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
import static io.harness.ngtriggers.Constants.COMMIT_SHA_STRING_LENGTH;
import static io.harness.ngtriggers.Constants.EMAIL;
import static io.harness.ngtriggers.Constants.EVENT_CORRELATION_ID;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.MERGE_QUEUE;
import static io.harness.ngtriggers.Constants.PR;
import static io.harness.ngtriggers.Constants.PUSH;
import static io.harness.ngtriggers.Constants.SOURCE_EVENT_ID;
import static io.harness.ngtriggers.Constants.SOURCE_EVENT_LINK;
import static io.harness.ngtriggers.Constants.SYSTEM_EVENT_SOURCE_PIPELINE_KEY;
import static io.harness.ngtriggers.Constants.SYSTEM_EVENT_TYPE_KEY;
import static io.harness.ngtriggers.Constants.TRIGGER_BRANCH;
import static io.harness.ngtriggers.Constants.TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD_BRANCH;
import static io.harness.ngtriggers.Constants.TRIGGER_REF;
import static io.harness.ngtriggers.Constants.TRIGGER_REF_DELIMITER;
import static io.harness.ngtriggers.Constants.UNIQUE_ID;
import static io.harness.pms.contracts.plan.TriggerType.ARTIFACT;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK_CUSTOM;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORTALL;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.common.utils.TriggerOptionalSecretsExpressionMaskingEvaluator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.HarnessArtifactRegistrySpec;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.helpers.ArtifactConfigHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.TriggerIssuer;
import io.harness.pms.contracts.plan.BuildInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.helper.ExecutionHelper;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.product.ci.scm.proto.MergeQueueHook;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.User;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.ProtoUtils;
import io.harness.tracing.TracingUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerExecutionHelper {
  private final PMSPipelineService pmsPipelineService;
  private final PlanExecutionService planExecutionService;
  private final PMSExecutionService pmsExecutionService;
  private final ExecutionHelper executionHelper;
  private final WebhookEventPayloadParser webhookEventPayloadParser;
  private final PipelineServiceClient pipelineServiceClient;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final MetricService metricService;
  private final TriggerOptionalSecretsExpressionMaskingEvaluator triggerOptionalSecretsExpressionMaskingEvaluator =
      new TriggerOptionalSecretsExpressionMaskingEvaluator();
  private static final String WEBHOOK_EVENT_PROCESS_TIME = "webhook_event_process_time";
  private static final String TRIGGER_FAILURE_COUNT = "trigger_failure_count";
  private static final String TRIGGER_ACTIVATION_COUNT = "trigger_activation_count";
  private static final String TRIGGER_EXECUTOR_VALIDATION_EXECUTION_COUNT =
      "trigger_executor_validation_execution_count";
  private final PipelineExecutor pipelineExecutor;
  private final TriggerExecutorResolver triggerExecutorResolver;
  private final YamlExpressionResolveHelper yamlExpressionResolveHelper;

  public PlanExecution resolveRuntimeInputAndSubmitExecutionRequestForArtifactManifestPollingFlow(
      TriggerDetails triggerDetails, TriggerPayload triggerPayload, String runTimeInputYaml, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    String executionTag =
        generateExecutionTagForEvent(triggerDetails, triggerPayload, scopeInfo, isParentIdQueryingEnabled);
    TriggeredBy embeddedUser = generateTriggerdBy(
        executionTag, triggerDetails.getNgTriggerEntity(), triggerPayload, null, scopeInfo, isParentIdQueryingEnabled);

    TriggerType triggerType = findTriggerType(triggerPayload);
    BuildInfo buildInfo = getBuildInfoForArtifacts(triggerDetails, triggerType, triggerPayload);
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder()
                                           .setTriggerType(triggerType)
                                           .setTriggeredBy(embeddedUser)
                                           .setBuildInfo(buildInfo)
                                           .build();

    return createPlanExecution(
        triggerDetails, triggerPayload, null, null, executionTag, triggerInfo, null, runTimeInputYaml);
  }

  public PlanExecution resolveRuntimeInputAndSubmitExecutionRequest(TriggerDetails triggerDetails,
      TriggerPayload triggerPayload, TriggerWebhookEvent triggerWebhookEvent, String payload, List<HeaderConfig> header,
      String runTimeInputYaml, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String executionTagForGitEvent =
        generateExecutionTagForEvent(triggerDetails, triggerPayload, scopeInfo, isParentIdQueryingEnabled);
    TriggeredBy embeddedUser = generateTriggerdBy(executionTagForGitEvent, triggerDetails.getNgTriggerEntity(),
        triggerPayload, triggerWebhookEvent, scopeInfo, isParentIdQueryingEnabled);

    TriggerType triggerType = findTriggerType(triggerPayload);
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(triggerType).setTriggeredBy(embeddedUser).build();
    return createPlanExecution(triggerDetails, triggerPayload, payload, header, executionTagForGitEvent, triggerInfo,
        triggerWebhookEvent, runTimeInputYaml);
  }
  @VisibleForTesting
  BuildInfo getBuildInfoForArtifacts(
      TriggerDetails triggerDetails, TriggerType triggerType, TriggerPayload triggerPayload) {
    BuildInfo.Builder buildInfo = BuildInfo.newBuilder();

    if (triggerType != ARTIFACT) {
      return buildInfo.build();
    }

    NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
    NGTriggerSourceV2 source = ngTriggerConfigV2.getSource();
    NGTriggerSpecV2 spec = source.getSpec();

    if (spec != null && ArtifactTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      String imagePath = ArtifactConfigHelper.fetchImagePath((ArtifactTriggerConfig) spec);
      String build = triggerPayload.getArtifactData().getBuild();

      buildInfo.setBuild(build);
      buildInfo.setImagePath(imagePath != null ? imagePath : "");
    }

    return buildInfo.build();
  }

  public void createPlanExecutionForV1Triggers(
      List<PipelineEntity> pipelineEntities, TriggerWebhookEvent triggerWebhookEvent) {
    setPrincipal(triggerWebhookEvent, null);
    for (PipelineEntity pipelineEntity : pipelineEntities) {
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().build();
      ExecArgs execArgs = buildExecutionArgs(pipelineEntity, null, triggerInfo, Collections.emptyList());
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(pipelineEntity.getAccountId(), pipelineEntity.getParentUniqueId());
      pipelineExecutor.applyOpaOnSaveGate(pipelineEntity, execArgs, scopeInfo);
      startExecution(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), execArgs, scopeInfo);
    }
  }

  public PlanExecution createPlanExecution(TriggerDetails triggerDetails, TriggerPayload triggerPayload, String payload,
      List<HeaderConfig> header, String executionTagForGitEvent, ExecutionTriggerInfo triggerInfo,
      TriggerWebhookEvent triggerWebhookEvent, String runtimeInputYaml) {
    // First we reset git-sync global context to avoid any issues with global context leaking between executions.
    // TODO: Move all calls of `initDefaultScmGitMetaDataAndRequestParams` to the beginning of trigger execution threads
    try (PmsMetricContextGuard metricContext = new PmsMetricContextGuard(
             ImmutableMap.<String, String>builder()
                 .put(PmsEventMonitoringConstants.ACCOUNT_ID, triggerDetails.getNgTriggerEntity().getAccountId())
                 .build())) {
      GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
      metricService.incCounter(TRIGGER_ACTIVATION_COUNT);
      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? scopeResolutionHelper.getScopeInfo(triggerDetails.getNgTriggerEntity().getAccountId(),
                triggerDetails.getNgTriggerEntity().getParentUniqueId())
          : ScopeInfo.builder()
                .accountIdentifier(triggerDetails.getNgTriggerEntity().getAccountId())
                .orgIdentifier(triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                .projectIdentifier(triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                .uniqueId(triggerDetails.getNgTriggerEntity().getParentUniqueId())
                .scopeType(ScopeLevel.PROJECT)
                .build();

      try {
        setPrincipal(triggerWebhookEvent, triggerDetails.getNgTriggerEntity());
        PipelineEntity pipelineEntity =
            getPipelineEntityToExecute(triggerDetails, triggerWebhookEvent, triggerPayload, scopeInfo);
        List<String> stagesToExecute = Collections.emptyList();

        NGTriggerConfigV2 triggerConfigV2 = triggerDetails.getNgTriggerConfigV2();

        if (triggerConfigV2 != null && triggerConfigV2.getStagesToExecute() != null
            && triggerConfigV2.getStagesToExecute().isExpression()) {
          WebhookPayloadData webhookPayloadData =
              WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build();
          TriggerExpressionEvaluator triggerExpressionEvaluator =
              WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
          Object stagesToExecuteResolved = triggerExpressionEvaluator.evaluateExpressionWithExpressionMode(
              triggerConfigV2.getStagesToExecute().getExpressionValue(), THROW_EXCEPTION_IF_UNRESOLVED);
          stagesToExecute = getResolvedStages(triggerConfigV2, stagesToExecuteResolved);
        } else if (triggerConfigV2 != null && triggerConfigV2.getStagesToExecute() != null) {
          stagesToExecute = triggerConfigV2.getStagesToExecute().getValue();
        }

        if (HarnessYamlVersion.V0.equals(triggerDetails.getNgTriggerEntity().getHarnessVersion())
            && HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())
            && isRuntimeInputYamlV0(triggerDetails.getNgTriggerEntity().getAccountId(), runtimeInputYaml)) {
          runtimeInputYaml = getCleanRuntimeInputYaml(pipelineEntity.getYaml(), runtimeInputYaml);
        }
        if (isNotEmpty(runtimeInputYaml)
            && pmsFeatureFlagHelper.isEnabled(
                triggerDetails.getNgTriggerEntity().getAccountId(), PIPE_RESOLVE_TRIGGER_EXPRESSIONS_IN_RUNTIME_INPUT)
            && (runtimeInputYaml.contains("<+" + SetupAbstractionKeys.trigger + ".")
                || runtimeInputYaml.contains("<+" + SetupAbstractionKeys.eventPayload + "."))) {
          String payloadForResolution = payload;
          TriggerPayload triggerPayloadForResolution = triggerPayload;
          if (pmsFeatureFlagHelper.isEnabled(triggerDetails.getNgTriggerEntity().getAccountId(),
                  PIPE_MASK_SECRET_EXPRESSIONS_IN_TRIGGER_PAYLOAD)) {
            if (payload != null) {
              payloadForResolution = maskSecretExpressions(payload);
            }
            if (triggerPayload != null) {
              triggerPayloadForResolution = maskSecretExpressionsInTriggerPayload(triggerPayload);
            }
          }
          try {
            runtimeInputYaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(runtimeInputYaml,
                new TriggerExpressionEvaluator(triggerPayloadForResolution, header, payloadForResolution, null),
                triggerDetails.getNgTriggerEntity().getAccountId());
          } catch (Exception e) {
            log.error("Failed to resolve trigger expressions in runtime input yaml; continuing with original yaml", e);
          }
        }
        ExecArgs execArgs = buildExecutionArgs(
            pipelineEntity, runtimeInputYaml, triggerInfo, stagesToExecute, isParentIdQueryingEnabled, scopeInfo);

        if (triggerConfigV2 != null && triggerConfigV2.getInputSetRefs() != null) {
          List<String> inputSetIdentifiers = getInputSetRefs(triggerConfigV2.getInputSetRefs(), triggerWebhookEvent);
          pipelineExecutor.resolveAndAssignInputSetsToExecution(pipelineEntity.getAccountId(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
              inputSetIdentifiers, execArgs.getPlanExecutionMetadataWithContext(), scopeInfo, true);
          setInputSetBranchName(triggerConfigV2, triggerWebhookEvent, triggerPayload, execArgs);
        }

        PlanExecutionMetadata planExecutionMetadata =
            execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
        if (pmsFeatureFlagHelper.isEnabled(
                triggerDetails.getNgTriggerEntity().getAccountId(), PIPE_MASK_SECRET_EXPRESSIONS_IN_TRIGGER_PAYLOAD)) {
          payload = maskSecretExpressions(payload);
          triggerPayload = maskSecretExpressionsInTriggerPayload(triggerPayload);
        }

        planExecutionMetadata.setTriggerPayload(triggerPayload);
        planExecutionMetadata.setTriggerJsonPayload(payload);
        planExecutionMetadata.setTriggerHeader(header);
        // Since this is a fresh execution and not a retry pipeline, originalExecutionId is null. Therefore,
        // PlanExecutionMetadataWithContext will not have the following fields set.
        // We can safely set these fields without checking if they are already set.
        execArgs.getPlanExecutionMetadataWithContext().setTriggerJsonPayload(payload);
        execArgs.getPlanExecutionMetadataWithContext().setTriggerHeader(header);
        execArgs.getPlanExecutionMetadataWithContext().setTriggerPayload(triggerPayload);
        pipelineExecutor.applyOpaOnSaveGate(pipelineEntity, execArgs, scopeInfo);

        NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
        if (triggerWebhookEvent != null && triggerWebhookEvent.getCreatedAt() != null) {
          metricService.recordMetric(
              WEBHOOK_EVENT_PROCESS_TIME, (double) (System.currentTimeMillis() - triggerWebhookEvent.getCreatedAt()));
        }
        PlanExecution planExecution = startExecution(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
            ngTriggerEntity.getProjectIdentifier(), execArgs, scopeInfo);
        log.info("Plan execution created with planExecutionId {}, accountId {} and triggerId {}",
            planExecution.getAmbiance().getPlanExecutionId(), pipelineEntity.getAccountId(),
            triggerDetails.getNgTriggerEntity().getIdentifier());
        // check if abort prev execution needed.
        requestPipelineExecutionAbortForSameExecTagIfNeeded(
            triggerDetails, planExecution, executionTagForGitEvent, scopeInfo, isParentIdQueryingEnabled);
        // Reset git-sync global context again,
        // thus avoiding any issues with global context leaking in the next trigger run.
        // TODO: Move all calls of `initDefaultScmGitMetaDataAndRequestParams` to the beginning of trigger execution
        GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
        return planExecution;
      } catch (PolicyEvaluationFailureException e) {
        metricService.incCounter(TRIGGER_FAILURE_COUNT);
        GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
        throw e;
      } catch (Exception e) {
        metricService.incCounter(TRIGGER_FAILURE_COUNT);
        GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
        throw new TriggerException("Failed while requesting Pipeline Execution through Trigger: "
                + (e instanceof CriticalExpressionEvaluationException
                            && EmptyPredicate.isNotEmpty(e.getCause().toString())
                        ? e.getCause().toString()
                        : e.getMessage()),
            e, USER);
      }
    }
  }

  private void setInputSetBranchName(NGTriggerConfigV2 triggerConfigV2, TriggerWebhookEvent triggerWebhookEvent,
      TriggerPayload triggerPayload, ExecArgs execArgs) {
    String inputSetBranch = resolveInputSetBranch(triggerConfigV2, triggerWebhookEvent, triggerPayload);
    if (isNotEmpty(inputSetBranch)) {
      execArgs.getPlanExecutionMetadataWithContext().setInputSetBranchName(inputSetBranch);
    }
  }

  private PlanExecution startExecution(
      String accountId, String orgIdentifier, String projectIdentifier, ExecArgs execArgs, ScopeInfo scopeInfo) {
    return executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier, execArgs.getMetadata(),
        execArgs.getPlanExecutionMetadataWithContext(), scopeInfo);
  }

  private ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String runtimeInputYaml,
      ExecutionTriggerInfo triggerInfo, List<String> stagesToExecute) {
    return buildExecutionArgs(pipelineEntity, runtimeInputYaml, triggerInfo, stagesToExecute, false, null);
  }

  private ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String runtimeInputYaml,
      ExecutionTriggerInfo triggerInfo, List<String> stagesToExecute, boolean isParentIdQueryingEnabled,
      ScopeInfo scopeInfo) {
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();

    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(true).build();

    return executionHelper.buildExecutionArgs(pipelineEntity, null, runtimeInputYaml, stagesToExecute,
        Collections.emptyMap(), triggerInfo, null, retryExecutionParameters, false, false,
        planExecutionMetadataWithContext, isParentIdQueryingEnabled, scopeInfo);
  }

  private boolean isRuntimeInputYamlV0(String accountId, String yaml) {
    if (!pmsFeatureFlagHelper.isEnabled(accountId, CI_YAML_VERSIONING)) {
      return true;
    }
    if (yaml == null) {
      return true;
    }
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    return ((ObjectNode) jsonNode).get(YAMLFieldNameConstants.INPUTS) == null
        && ((ObjectNode) jsonNode).get(YAMLFieldNameConstants.INPUTSET) == null;
  }

  public PipelineEntity getPipelineEntityToExecute(TriggerDetails triggerDetails,
      TriggerWebhookEvent triggerWebhookEvent, TriggerPayload triggerPayload, ScopeInfo scopeInfo) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    Optional<PipelineEntity> pipelineEntityToExecute;
    String targetIdentifier = ngTriggerEntity.getTargetIdentifier();
    boolean isParentIdQueryingEnabled = true;
    if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
      // Case for inline pipeline
      pipelineEntityToExecute = pmsPipelineService.getPipeline(ngTriggerEntity.getAccountId(),
          ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), targetIdentifier, false, false,
          false, false, scopeInfo, isParentIdQueryingEnabled);
      if (pipelineEntityToExecute.isEmpty()) {
        throw new TriggerException("Unable to continue trigger execution. Pipeline with identifier: "
                + ngTriggerEntity.getTargetIdentifier() + ", with org: " + scopeInfo.getOrgIdentifier()
                + ", with ProjectId: " + scopeInfo.getProjectIdentifier()
                + ", For Trigger: " + ngTriggerEntity.getIdentifier() + " does not exist.",
            USER);
      }
      if (pipelineEntityToExecute.get().getStoreType() == StoreType.REMOTE) {
        throw new TriggerException("Unable to continue trigger execution. Pipeline with identifier: "
                + ngTriggerEntity.getTargetIdentifier() + ", with org: " + scopeInfo.getOrgIdentifier()
                + ", with ProjectId: " + scopeInfo.getProjectIdentifier() + ", For Trigger: "
                + ngTriggerEntity.getIdentifier() + " has missing or empty pipelineBranchName in trigger's yaml.",
            USER);
      }
      log.info("Triggering execution for inline pipeline with identifier:  {} , in org: {} , ProjectId: {} , "
              + "accountIdentifier: {} , For Trigger: {}",
          ngTriggerEntity.getTargetIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
          ngTriggerEntity.getAccountId(), ngTriggerEntity.getIdentifier());
    } else {
      // Case for remote pipeline
      String branch = null;
      if (isNotEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
        if (TriggerHelper.isBranchExpr(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
          branch = resolveBranchExpression(
              triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), triggerWebhookEvent, triggerPayload);
        } else {
          branch = triggerDetails.getNgTriggerConfigV2().getPipelineBranchName();
        }
      }

      GitAwareContextHelper.updateGitEntityContext(GitEntityInfo.builder().branch(branch).build());
      pipelineEntityToExecute = pmsPipelineService.getPipeline(ngTriggerEntity.getAccountId(),
          ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), targetIdentifier, false, false,
          false, false, scopeInfo, isParentIdQueryingEnabled);
      if (pipelineEntityToExecute.isEmpty()) {
        throw new TriggerException("Unable to continue trigger execution. Pipeline with identifier: "
                + ngTriggerEntity.getTargetIdentifier() + ", with org: " + scopeInfo.getOrgIdentifier()
                + ", with ProjectId: " + scopeInfo.getProjectIdentifier() + ", For Trigger: "
                + ngTriggerEntity.getIdentifier() + " does not exist in branch: " + branch + " configured in trigger.",
            USER);
      }

      // Defensive guard: pipelineBranchName is meaningful only for pipelines whose YAML lives in Git
      if (pipelineEntityToExecute.get().getStoreType() == StoreType.INLINE) {
        log.warn("Trigger '{}' for pipeline '{}' has pipelineBranchName='{}' set in trigger yaml, but the resolved "
                + "pipeline storeType is INLINE. Ignoring pipelineBranchName to avoid polluting git context for "
                + "downstream template / input-set fetches. Please remove pipelineBranchName from the trigger "
                + "configuration.",
            ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(), branch);
        GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
        log.info("Triggering execution for inline pipeline with identifier:  {} , in org: {} , ProjectId: {} , "
                + "accountIdentifier: {} , For Trigger: {}",
            ngTriggerEntity.getTargetIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
            ngTriggerEntity.getAccountId(), ngTriggerEntity.getIdentifier());
      } else {
        if (EmptyPredicate.isEmpty(branch)) {
          branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();
        }
        GitAwareContextHelper.updateGitEntityContext(
            getGitSyncContextWithRepoAndFilePath(pipelineEntityToExecute.get(), branch).getGitBranchInfo());
        log.info("Triggering execution for remote pipeline with identifier:  {} , in org: {} , ProjectId: {} , "
                + "accountIdentifier: {} , For Trigger: {},  in branch {}, repo {} , filePath {}",
            ngTriggerEntity.getTargetIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
            ngTriggerEntity.getAccountId(), ngTriggerEntity.getIdentifier(), branch,
            pipelineEntityToExecute.get().getRepo(), pipelineEntityToExecute.get().getFilePath());
      }
    }
    return pipelineEntityToExecute.get();
  }

  @VisibleForTesting
  TriggeredBy generateTriggerdBy(String executionTagForGitEvent, NGTriggerEntity ngTriggerEntity,
      TriggerPayload triggerPayload, TriggerWebhookEvent triggerWebhookEvent, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    String eventId = triggerWebhookEvent != null ? triggerWebhookEvent.getUuid() : null;
    TriggeredBy.Builder builder = TriggeredBy.newBuilder()
                                      .setIdentifier(ngTriggerEntity.getIdentifier())
                                      .setTriggerIdentifier(ngTriggerEntity.getIdentifier())
                                      .setTriggerName(ngTriggerEntity.getName())
                                      .setUuid("systemUser");

    boolean executorSet = populateTriggeredByFromExecutor(builder, ngTriggerEntity);
    if (!executorSet && triggerWebhookEvent != null && triggerWebhookEvent.getPrincipal() != null) {
      Principal principal = triggerWebhookEvent.getPrincipal();
      if (principal instanceof UserPrincipal) {
        UserPrincipal userPrincipal = (UserPrincipal) principal;
        builder.setIdentifier(userPrincipal.getUsername());
        builder.putExtraInfo(EMAIL, userPrincipal.getEmail());
        builder.setUuid(userPrincipal.getName());
      } else if (principal instanceof ServiceAccountPrincipal) {
        ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
        builder.setIdentifier(serviceAccountPrincipal.getUsername());
        builder.putExtraInfo(EMAIL, serviceAccountPrincipal.getEmail());
        builder.putExtraInfo(UNIQUE_ID, serviceAccountPrincipal.getUniqueId());
      }
    }
    if (isNotBlank(executionTagForGitEvent)) {
      builder.putExtraInfo(PlanExecution.EXEC_TAG_SET_BY_TRIGGER, executionTagForGitEvent);
      builder.putExtraInfo(TRIGGER_REF, generateTriggerRef(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled));

      if (isNotBlank(eventId)) {
        builder.putExtraInfo(EVENT_CORRELATION_ID, eventId);
      }

      if (triggerPayload.hasParsedPayload()) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        User sender = null;
        String sourceEventId = null;
        String sourceEventLink = null;
        if (parsedPayload.hasPush()) {
          sender = parsedPayload.getPush().getSender();
          if (parsedPayload.getPush().hasCommit()) {
            sourceEventId =
                StringUtils.substring(parsedPayload.getPush().getCommit().getSha(), 0, COMMIT_SHA_STRING_LENGTH);
            sourceEventLink = parsedPayload.getPush().getCommit().getLink();
          }
        } else if (parsedPayload.hasPr()) {
          sender = parsedPayload.getPr().getSender();
          if (parsedPayload.getPr().hasPr()) {
            sourceEventId = ((Long) parsedPayload.getPr().getPr().getNumber()).toString();
            sourceEventLink = parsedPayload.getPr().getPr().getLink();
          }
        } else if (parsedPayload.hasRelease()) {
          sender = parsedPayload.getRelease().getSender();
          if (parsedPayload.getRelease().hasRelease()) {
            sourceEventId = parsedPayload.getRelease().getRelease().getTag();
            sourceEventLink = parsedPayload.getRelease().getRelease().getLink();
          }
        } else if (parsedPayload.hasBranch()) {
          sender = parsedPayload.getBranch().getSender();
        } else if (parsedPayload.hasTag()) {
          sender = parsedPayload.getTag().getSender();
        } else if (parsedPayload.hasMergeQueue()) {
          sender = parsedPayload.getMergeQueue().getSender();
          sourceEventId = StringUtils.substring(parsedPayload.getMergeQueue().getSha(), 0, COMMIT_SHA_STRING_LENGTH);
        }
        if (sender != null) {
          if (triggerPayload.getSourceType() == SourceType.HARNESS_REPO
              || triggerPayload.getSourceType() == SourceType.AZURE_REPO) {
            builder.putExtraInfo(GIT_USER, sender.getEmail());
            if (isNotEmpty(sender.getLogin())) {
              builder.setIdentifier(sender.getEmail());
            }
          } else {
            builder.putExtraInfo(GIT_USER, sender.getLogin());
            if (isNotEmpty(sender.getLogin())) {
              builder.setIdentifier(sender.getLogin());
            }
          }
          if (isNotEmpty(sender.getEmail())) {
            builder.putExtraInfo(EMAIL, sender.getEmail());
          }
          if (isNotEmpty(sender.getName())) {
            builder.setUuid(sender.getName());
          }
        }
        if (isNotEmpty(sourceEventId)) {
          builder.putExtraInfo(SOURCE_EVENT_ID, sourceEventId);
        }
        if (isNotEmpty(sourceEventLink)) {
          builder.putExtraInfo(SOURCE_EVENT_LINK, sourceEventLink);
        }
      }
    }
    String systemEventType = triggerPayload.getHeadersOrDefault(SYSTEM_EVENT_TYPE_KEY, null);
    if (isNotBlank(systemEventType)) {
      builder.putExtraInfo(SYSTEM_EVENT_TYPE_KEY, systemEventType);
      builder.putExtraInfo(
          SYSTEM_EVENT_SOURCE_PIPELINE_KEY, triggerPayload.getHeadersOrDefault(SYSTEM_EVENT_SOURCE_PIPELINE_KEY, ""));
    }
    return builder.build();
  }

  private boolean populateTriggeredByFromExecutor(TriggeredBy.Builder builder, NGTriggerEntity entity) {
    if (entity.getExecutorInfo() == null || isEmpty(entity.getExecutorInfo().getIdentifier())) {
      return false;
    }
    try {
      if (entity.getExecutorInfo().getType() != null) {
        builder.putExtraInfo(PmsEventMonitoringConstants.EXECUTOR_TYPE, entity.getExecutorInfo().getType().name());
      }
      Principal executorPrincipal = triggerExecutorResolver.resolveExecutorPrincipal(entity);
      if (executorPrincipal instanceof UserPrincipal) {
        UserPrincipal userPrincipal = (UserPrincipal) executorPrincipal;
        builder.setUuid(userPrincipal.getName());
        builder.setIdentifier(userPrincipal.getUsername());
        if (userPrincipal.getEmail() != null) {
          builder.putExtraInfo(EMAIL, userPrincipal.getEmail());
        }
      } else if (executorPrincipal instanceof ServiceAccountPrincipal) {
        ServiceAccountPrincipal saPrincipal = (ServiceAccountPrincipal) executorPrincipal;
        builder.setUuid(saPrincipal.getName());
        builder.setIdentifier(saPrincipal.getUsername());
        if (saPrincipal.getEmail() != null) {
          builder.putExtraInfo(EMAIL, saPrincipal.getEmail());
        }
        if (saPrincipal.getUniqueId() != null) {
          builder.putExtraInfo(UNIQUE_ID, saPrincipal.getUniqueId());
        }
      }
      return true;
    } catch (Exception e) {
      log.error("Failed to resolve executor for trigger '{}', failing execution.", entity.getIdentifier(), e);
      throw e;
    }
  }

  @VisibleForTesting
  String generateTriggerRef(NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return new StringBuilder(256)
        .append(ngTriggerEntity.getAccountId())
        .append(TRIGGER_REF_DELIMITER)
        .append(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
        .append(TRIGGER_REF_DELIMITER)
        .append(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier())
        .append(TRIGGER_REF_DELIMITER)
        .append(ngTriggerEntity.getIdentifier())
        .toString();
  }

  @VisibleForTesting
  TriggerType findTriggerType(TriggerPayload triggerPayload) {
    TriggerType triggerType = WEBHOOK;
    if (triggerPayload.getType() == Type.SCHEDULED) {
      triggerType = TriggerType.SCHEDULER_CRON;
    } else if (triggerPayload.getType() == Type.ARTIFACT) {
      triggerType = TriggerType.ARTIFACT;
    } else if (triggerPayload.getType() == Type.MANIFEST) {
      triggerType = TriggerType.MANIFEST;
    } else if (triggerPayload.getSourceType() == SourceType.CUSTOM_REPO) {
      triggerType = WEBHOOK_CUSTOM;
    }

    return triggerType;
  }

  /**
   * Generate execution tag to identify pipeline executions caused by similar trigger git events.
   * PR: {accId:orgId:projectId:pipelineIdentifier:PR:RepoUrl:PrNum:SourceBranch:TargetBranch}
   * PUSH: {accId:orgId:projectId:pipelineIdentifier:PUSH:RepoUrl:Ref}
   *
   * @param triggerDetails
   * @param triggerPayload
   * @param scopeInfo
   * @param isParentIdQueryingEnabled
   * @return
   */
  public String generateExecutionTagForEvent(TriggerDetails triggerDetails, TriggerPayload triggerPayload,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String triggerRef;
    StringBuilder builder =
        new StringBuilder(256)
            .append(triggerDetails.getNgTriggerEntity().getAccountId())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                              : triggerDetails.getNgTriggerEntity().getOrgIdentifier())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                              : triggerDetails.getNgTriggerEntity().getProjectIdentifier())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(triggerDetails.getNgTriggerEntity().getTargetIdentifier());

    if (!pmsFeatureFlagHelper.isEnabled(
            triggerDetails.getNgTriggerEntity().getAccountId(), PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER)) {
      builder.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
          .append(triggerDetails.getNgTriggerEntity().getIdentifier());
    }

    triggerRef = builder.toString();

    try {
      if (!triggerPayload.hasParsedPayload()) {
        return triggerRef;
      }

      ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
      StringBuilder executionTag = new StringBuilder(512).append(triggerRef);

      if (parsedPayload.hasPr()) {
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER).append(PR);
        PullRequestHook pullRequestHook = parsedPayload.getPr();
        PullRequest pr = pullRequestHook.getPr();
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pullRequestHook.getRepo().getLink())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getNumber())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getSource())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getTarget());
      } else if (parsedPayload.hasPush()) {
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER).append(PUSH);
        PushHook pushHook = parsedPayload.getPush();
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pushHook.getRepo().getLink())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pushHook.getRef());
      } else if (parsedPayload.hasMergeQueue()) {
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER).append(MERGE_QUEUE);
        MergeQueueHook mergeQueueHook = parsedPayload.getMergeQueue();
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(mergeQueueHook.getRepo().getLink())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(mergeQueueHook.getSha());
        // triggerRef drops the trigger identifier under this flag so PR/PUSH auto-abort can correlate across triggers
        // on the same ref. A merge queue tag must keep it: it is the idempotency/abort key for one speculative commit,
        // and it must be appended here rather than by a wrapping caller, since this method is also what persists the
        // tag on the execution.
        if (pmsFeatureFlagHelper.isEnabled(
                triggerDetails.getNgTriggerEntity().getAccountId(), PIPE_ABORT_ONLY_TRIGGERED_BY_SAME_TRIGGER)) {
          executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
              .append(triggerDetails.getNgTriggerEntity().getIdentifier());
        }
      }
      return executionTag.toString();
    } catch (Exception e) {
      log.error("failed to generate complete Execution Tag for Trigger: " + triggerRef, e);
    }

    return triggerRef;
  }

  @VisibleForTesting
  void requestPipelineExecutionAbortForSameExecTagIfNeeded(TriggerDetails triggerDetails, PlanExecution planExecution,
      String executionTag, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      if (!isAutoAbortSelected(triggerDetails.getNgTriggerConfigV2())) {
        return;
      }

      List<PlanExecution> executionsToAbort =
          planExecutionService.findPrevUnTerminatedPlanExecutionsByExecutionTag(planExecution, executionTag);
      if (isEmpty(executionsToAbort)) {
        return;
      }

      for (PlanExecution execution : executionsToAbort) {
        registerPipelineExecutionAbortInterrupt(
            execution, executionTag, triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      }
    } catch (Exception e) {
      log.error("Failed while requesting abort for pipeline executions using executionTag: " + executionTag, e);
    }
  }

  /**
   * Aborts in-flight executions for a merge queue commit whose checks were canceled.
   * The execution tag deliberately excludes the action, so the tag generated from a
   * checks_canceled event is identical to the one generated from the checks_requested
   * event that started the run.
   */
  public int abortExecutionsForMergeQueueCancel(TriggerDetails triggerDetails, MergeQueueHook mergeQueueHook,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String executionTag =
        getMergeQueueExecutionTag(triggerDetails, mergeQueueHook, scopeInfo, isParentIdQueryingEnabled);

    List<PlanExecution> executionsToAbort =
        planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(executionTag);
    if (isEmpty(executionsToAbort)) {
      log.info("No in-flight executions to abort for merge queue execution tag: {}", executionTag);
      return 0;
    }

    int abortedCount = 0;
    for (PlanExecution execution : executionsToAbort) {
      if (registerPipelineExecutionAbortInterrupt(
              execution, executionTag, triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled)) {
        abortedCount++;
      }
    }
    return abortedCount;
  }

  /**
   * The speculative commit sha makes the execution tag an exact idempotency key for a merge queue checks_requested
   * event: that sha is built once and only once. Webhook delivery is at-least-once and merge queue triggers carry no
   * actions filter or auto-abort, so without this guard a redelivered request would start a second execution that
   * races the first to report the ci check on the same commit.
   */
  public boolean hasUnterminatedExecutionForMergeQueue(TriggerDetails triggerDetails, MergeQueueHook mergeQueueHook,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String executionTag =
        getMergeQueueExecutionTag(triggerDetails, mergeQueueHook, scopeInfo, isParentIdQueryingEnabled);
    if (isEmpty(planExecutionService.findUnterminatedPlanExecutionsByExecutionTag(executionTag))) {
      return false;
    }
    log.info("An execution is already running for merge queue execution tag: {}", executionTag);
    return true;
  }

  public String getMergeQueueExecutionTag(TriggerDetails triggerDetails, MergeQueueHook mergeQueueHook,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    TriggerPayload triggerPayload =
        TriggerPayload.newBuilder()
            .setType(Type.WEBHOOK)
            .setParsedPayload(ParsedPayload.newBuilder().setMergeQueue(mergeQueueHook).build())
            .build();
    // Thin wrapper: the trigger scoping lives in generateExecutionTagForEvent's merge queue branch, so this lookup key
    // is guaranteed to match the tag persisted on the execution.
    return generateExecutionTagForEvent(triggerDetails, triggerPayload, scopeInfo, isParentIdQueryingEnabled);
  }

  @VisibleForTesting
  boolean isAutoAbortSelected(NGTriggerConfigV2 ngTriggerConfigV2) {
    boolean autoAbortPreviousExecutions = false;
    if (WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerConfigV2.getSource().getSpec().getClass())) {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerConfigV2.getSource().getSpec();
      GitAware gitAware = webhookTriggerConfigV2.getSpec().fetchGitAware();
      if (gitAware != null && gitAware.fetchAutoAbortPreviousExecutions()) {
        autoAbortPreviousExecutions = gitAware.fetchAutoAbortPreviousExecutions();
      } else if (webhookTriggerConfigV2.getType().equals(WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY)) {
        autoAbortPreviousExecutions =
            ((HarnessArtifactRegistrySpec) webhookTriggerConfigV2.getSpec()).fetchAutoAbortPreviousExecutions();
      }
    }

    return autoAbortPreviousExecutions;
  }

  public String fetchInputSetYAML(TriggerDetails triggerDetails, TriggerWebhookEvent triggerWebhookEvent,
      List<String> inputSetRefs, TriggerPayload triggerPayload, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    NGTriggerConfigV2 triggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
    String pipelineBranch = triggerConfigV2.getPipelineBranchName();
    String inputYaml = triggerConfigV2.getInputYaml();
    if (isEmpty(inputSetRefs)) {
      return inputYaml;
    }

    String branch = null;
    if (isNotEmpty(pipelineBranch)) {
      if (TriggerHelper.isBranchExpr(pipelineBranch)) {
        branch = resolveBranchExpression(pipelineBranch, triggerWebhookEvent, triggerPayload);
      } else {
        branch = pipelineBranch;
      }
    }
    String inputSetBranch = resolveInputSetBranch(triggerConfigV2, triggerWebhookEvent, triggerPayload);

    MergeInputSetResponseDTOPMS mergeInputSetResponseDTOPMS = NGRestUtils.getResponse(
        pipelineServiceClient.getMergeInputSetFromPipelineTemplate(ngTriggerEntity.getAccountId(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
            ngTriggerEntity.getTargetIdentifier(), branch,
            MergeInputSetRequestDTOPMS.builder()
                .inputSetReferences(inputSetRefs)
                .lastYamlToMerge(inputYaml)
                .getOnlyFileContent(true)
                .inputSetBranchName(inputSetBranch)
                .build()));

    return mergeInputSetResponseDTOPMS.getPipelineYaml();
  }

  public List<String> getInputSetRefs(
      ParameterField<List<String>> inputSetRefs, TriggerWebhookEvent triggerWebhookEvent) {
    if (inputSetRefs.isExpression()) {
      WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build();
      TriggerExpressionEvaluator triggerExpressionEvaluator =
          WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
      Object resolvedInputSet = triggerExpressionEvaluator.evaluateExpressionWithExpressionMode(
          inputSetRefs.getExpressionValue(), THROW_EXCEPTION_IF_UNRESOLVED);
      try {
        return Arrays.asList((String[]) resolvedInputSet);
      } catch (Exception e) {
        throw new InvalidRequestException(
            String.format("Expression %s did not resolve to list", inputSetRefs.getExpressionValue()), e);
      }
    } else {
      return inputSetRefs.getValue();
    }
  }

  /**
   * @return true if the abort interrupt was registered, false if it was swallowed. Callers that report an
   *     abort count to the user must use this rather than the size of the candidate list, since a failure
   *     here is logged and suppressed so that one bad execution cannot stop the rest from being aborted.
   */
  private boolean registerPipelineExecutionAbortInterrupt(PlanExecution execution, String executionTag,
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      log.info(new StringBuilder(128)
                   .append("Requesting Pipeline Execution Abort for planExecutionId")
                   .append(execution.getUuid())
                   .append(", with Tag: ")
                   .append(executionTag)
                   .toString());

      InterruptConfig interruptConfig =
          InterruptConfig.newBuilder()
              .setIssuedBy(IssuedBy.newBuilder()
                               .setTriggerIssuer(TriggerIssuer.newBuilder()
                                                     .setTriggerRef(generateTriggerRef(
                                                         ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled))
                                                     .setAbortPrevConcurrentExecution(true)
                                                     .build())
                               .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                               .build())
              .build();
      pmsExecutionService.registerInterrupt(ABORTALL, execution.getUuid(), null, interruptConfig);
      return true;
    } catch (InvalidRequestException e) {
      log.warn("Exception while requesting Pipeline Execution Abort: " + executionTag, e);
    } catch (Exception e) {
      log.error("Exception while requesting Pipeline Execution Abort: " + executionTag, e);
    }
    return false;
  }

  public String resolveBranchExpression(
      String expression, TriggerWebhookEvent triggerWebhookEvent, TriggerPayload triggerPayload) {
    if (triggerWebhookEvent == null || triggerWebhookEvent.getPayload() == null) {
      throw new InvalidRequestException("Branch can not be expression for non webhook executions");
    }

    TriggerExpressionEvaluator triggerExpressionEvaluator;
    try {
      switch (triggerWebhookEvent.getSourceRepoType()) {
        case "CUSTOM":
          triggerExpressionEvaluator = WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(
              null, triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());

          String branchExpression = TRIGGER_PAYLOAD_BRANCH;
          // For Backward Compatibility Trigger Branch is also handled.
          if (pmsFeatureFlagHelper.isEnabled(triggerWebhookEvent.getAccountId(), CDS_RESOLVE_CUSTOM_TRIGGER_EXPRESSION)
              && EmptyPredicate.isNotEmpty(expression) && !TRIGGER_BRANCH.equals(expression)) {
            branchExpression = expression;
          }
          return (String) triggerExpressionEvaluator.evaluateExpressionWithExpressionMode(
              branchExpression, THROW_EXCEPTION_IF_UNRESOLVED);

        case "HARNESS_ARTIFACT_REGISTRY":
          triggerExpressionEvaluator = WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(
              null, triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());
          return (String) triggerExpressionEvaluator.evaluateExpressionWithExpressionMode(
              expression, THROW_EXCEPTION_IF_UNRESOLVED);

        default:
          WebhookPayloadData webhookPayloadData = webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
          if (pmsFeatureFlagHelper.isEnabled(
                  triggerWebhookEvent.getAccountId(), CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION)
              && triggerPayload != null) {
            triggerExpressionEvaluator =
                WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData, triggerPayload);
          } else {
            if (webhookPayloadData.getParseWebhookResponse() != null
                && webhookPayloadData.getParseWebhookResponse().hasComment()) {
              /* Check whether the original webhook event is of IssueComment type AND trigger's pipelineBranchName is
                 <+trigger.branch> . If this is the case, we log it here, because enabling the Feature Flag
                 CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION will be a change in behavior for this
                 trigger. */
              log.info("IssueComment trigger is being executed with <+trigger.branch> expression.");
            }
            triggerExpressionEvaluator = WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
          }
          return (String) triggerExpressionEvaluator.evaluateExpressionWithExpressionMode(
              expression, THROW_EXCEPTION_IF_UNRESOLVED);
      }
    } catch (CriticalExpressionEvaluationException e) {
      throw new TriggerException(
          String.format("Please ensure the expression %s has the right branch information", expression), USER);
    }
  }

  @VisibleForTesting
  GitSyncBranchContext getGitSyncContextWithRepoAndFilePath(PipelineEntity pipelineEntityToExecute, String branch) {
    return GitSyncBranchContext.builder()
        .gitBranchInfo(GitEntityInfo.builder()
                           .repoName(pipelineEntityToExecute.getRepo())
                           .filePath(pipelineEntityToExecute.getFilePath())
                           .branch(branch)
                           .yamlGitConfigId(pipelineEntityToExecute.getRepo())
                           .connectorRef(pipelineEntityToExecute.getConnectorRef())
                           .build())
        .build();
  }

  @VisibleForTesting
  String getCleanRuntimeInputYaml(String pipelineYaml, String runtimeInputYaml) {
    // Triggers for pipelines with no inputs have "pipeline: {}\n" as their `inputYaml`.
    // To avoid issues with pipeline re-runs, we need to set the inputYaml to "" in this case.
    String templateYaml = InputSetTemplateHelper.createTemplateFromPipeline(pipelineYaml);
    if (templateYaml == null) {
      return "";
    }
    return runtimeInputYaml;
  }

  @VisibleForTesting
  public void setPrincipal(TriggerWebhookEvent triggerWebhookEvent) {
    setPrincipal(triggerWebhookEvent, null);
  }

  @VisibleForTesting
  public void setPrincipal(TriggerWebhookEvent triggerWebhookEvent, NGTriggerEntity triggerEntity) {
    if (triggerEntity != null) {
      boolean executorFeatureEnabled = pmsFeatureFlagHelper.isEnabled(
          triggerEntity.getAccountId(), FeatureName.PIPE_ENFORCE_TRIGGER_EXECUTOR_IDENTITY);
      try {
        triggerExecutorResolver.validateExecutorRequiredForExecution(triggerEntity, executorFeatureEnabled);
      } catch (InvalidRequestException e) {
        recordExecutorExecutionMetric(triggerEntity.getAccountId(), "NONE", "MISSING_EXECUTOR");
        throw e;
      }
    }

    if (triggerEntity != null && triggerEntity.getExecutorInfo() != null
        && isNotEmpty(triggerEntity.getExecutorInfo().getIdentifier())) {
      String executorType = triggerEntity.getExecutorInfo().getType() != null
          ? triggerEntity.getExecutorInfo().getType().name()
          : "UNKNOWN";
      try {
        triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);
        Principal executorPrincipal = triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity);
        triggerExecutorResolver.setExecutorContext(executorPrincipal);
        recordExecutorExecutionMetric(triggerEntity.getAccountId(), executorType, "SUCCESS");
        Span.current().setAttribute("trigger.executor.type", executorType);
        Span.current().setAttribute("trigger.executor.validation.result", "SUCCESS");
        return;
      } catch (Exception e) {
        log.error("Failed to resolve executor principal for trigger '{}'. Execution will fail.",
            triggerEntity.getIdentifier(), e);
        String result = classifyExecutionValidationFailure(e);
        recordExecutorExecutionMetric(triggerEntity.getAccountId(), executorType, result);
        Span.current().setAttribute("trigger.executor.type", executorType);
        Span.current().setAttribute("trigger.executor.validation.result", result);
        throw e;
      }
    }

    if (triggerWebhookEvent != null && triggerWebhookEvent.getPrincipal() != null) {
      SecurityContextBuilder.setContext(triggerWebhookEvent.getPrincipal());
      SourcePrincipalContextBuilder.setSourcePrincipal(triggerWebhookEvent.getPrincipal());
    } else {
      SecurityContextBuilder.setContext(
          new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
      SourcePrincipalContextBuilder.setSourcePrincipal(
          new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    }
  }

  private static String classifyExecutionValidationFailure(Exception e) {
    if (e instanceof NGAccessDeniedException) {
      return "PERMISSION_DENIED";
    }
    if (e instanceof InvalidRequestException) {
      return "ERROR";
    }
    return "INFRA_ERROR";
  }

  private void recordExecutorExecutionMetric(String accountId, String executorType, String result) {
    try (PmsMetricContextGuard ctx =
             new PmsMetricContextGuard(ImmutableMap.<String, String>builder()
                                           .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountId)
                                           .put(PmsEventMonitoringConstants.EXECUTOR_TYPE, executorType)
                                           .put(PmsEventMonitoringConstants.RESULT, result)
                                           .build())) {
      metricService.incCounter(TRIGGER_EXECUTOR_VALIDATION_EXECUTION_COUNT);
    } catch (Exception e) {
      log.warn("Failed to record executor execution metric", e);
    }
  }

  private List<String> getResolvedStages(NGTriggerConfigV2 triggerConfigV2, Object stagesToExecuteResolved) {
    List<String> stagesToExecute;
    try {
      stagesToExecute = Arrays.asList((String[]) stagesToExecuteResolved);
    } catch (Exception e) {
      throw new InvalidRequestException(String.format("Expression %s did not resolve to list",
                                            triggerConfigV2.getStagesToExecute().getExpressionValue()),
          e);
    }
    return stagesToExecute;
  }

  /*
   Only use this method when we need to generate and set traceId for trigger flows where traceId was not set by the
  interceptor.
   */
  public static TracingUtils.TracingContext generateTraceIdAndStartSpan(Tracer tracer, NGTriggerEntity entity) {
    String triggerOperation = String.format("%s:%s:%s:%s", entity.getTargetIdentifier(), entity.getIdentifier(),
        entity.getType().toString(), entity.getUuid());
    return TracingUtils.generateTraceIdAndStartSpan(tracer, triggerOperation);
  }

  private String maskSecretExpressions(String input) {
    return (String) triggerOptionalSecretsExpressionMaskingEvaluator.resolve(
        input, RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  @VisibleForTesting
  TriggerPayload maskSecretExpressionsInTriggerPayload(TriggerPayload triggerPayload) {
    String triggerPayloadJson = null;
    try {
      triggerPayloadJson = StringEscapeUtils.unescapeJava(JsonFormat.printer().print(triggerPayload));
    } catch (InvalidProtocolBufferException e) {
      log.error("Couldn't parse trigger payload protobuf object", e);
      return triggerPayload;
    }
    triggerPayloadJson = maskSecretExpressions(triggerPayloadJson);
    TriggerPayload.Builder triggerPayloadCopy = TriggerPayload.newBuilder();
    try {
      JsonFormat.parser().merge(triggerPayloadJson, triggerPayloadCopy);
    } catch (InvalidProtocolBufferException e) {
      log.error("Couldn't convert json object into trigger payload protobuf object", e);
      return triggerPayload;
    }
    return triggerPayloadCopy.build();
  }

  String resolveInputSetBranch(
      NGTriggerConfigV2 triggerConfigV2, TriggerWebhookEvent triggerWebhookEvent, TriggerPayload triggerPayload) {
    if (triggerConfigV2.getInputSetBranchName() == null) {
      return null;
    }

    if (triggerConfigV2.getInputSetBranchName().isExpression()) {
      return resolveBranchExpression(
          triggerConfigV2.getInputSetBranchName().getExpressionValue(), triggerWebhookEvent, triggerPayload);
    } else {
      return triggerConfigV2.getInputSetBranchName().getValue();
    }
  }
}
