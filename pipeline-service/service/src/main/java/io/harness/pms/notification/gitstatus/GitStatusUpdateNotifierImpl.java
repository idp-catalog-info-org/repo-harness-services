/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.gitstatus;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.gitops.constants.GitopsConstants;
import io.harness.cdng.gitops.gitstatus.GitOpsGitStatusHelper;
import io.harness.cdng.gitops.outcomes.GitOpsPRStatusInfo;
import io.harness.cdng.gitops.outcomes.GitOpsStatusCheckOutput;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.execution.intfc.GitStatusNotificationParams;
import io.harness.ci.execution.execution.intfc.GitStatusNotificationParams.GitStatusNotificationParamsBuilder;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.NGAccess;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitStatusUpdateNotifierImpl implements GitStatusUpdateNotifier {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PMSExecutionService pmsExecutionService;
  @Inject @Named("harnessCodeClientConfig") private ServiceHttpClientConfig harnessCodeClientConfig;
  @Inject private GitOpsGitStatusHelper gitOpsGitStatusHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public void onGitOpsNodeStatusUpdate(NodeExecution nodeExecution, Ambiance ambiance) {
    if (featureFlagService.isEnabled(
            nodeExecution.getAccountId(), FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED)) {
      return;
    }
    try {
      processGitOpsStatusUpdate(nodeExecution, ambiance);
    } catch (Exception ex) {
      log.error("Failed to send GitOps git status for node execution: {}", nodeExecution.getNodeId(), ex);
    }
  }

  public void onNodeStatusUpdate(NodeExecution nodeExecution, Ambiance ambiance) {
    // Existing CI path
    if (!featureFlagService.isEnabled(nodeExecution.getAccountId(), FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)) {
      return;
    }
    try {
      String planExecutionId = nodeExecution.getPlanExecutionId();
      Optional<PlanExecutionMetadata> planExecutionMetadata =
          planExecutionMetadataService.findByPlanExecutionId(nodeExecution.getAccountId(), planExecutionId);
      if (!planExecutionMetadata.isPresent()) {
        return;
      }
      if (planExecutionMetadata.get().getTriggerPayload() == null
          || planExecutionMetadata.get().getTriggerPayload().getParsedPayload() == null
          || !planExecutionMetadata.get().getTriggerPayload().getParsedPayload().hasPr()) {
        return;
      }

      PlanNode planNode = planService.fetchNode(nodeExecution.getPlanId(), nodeExecution.getNodeId());

      if (null != planNode.getSendGitStatus() && !planNode.getSendGitStatus().getEnabled()) {
        return;
      }

      processGitStatus(nodeExecution, planNode, planExecutionMetadata.get());

    } catch (Exception ex) {
      log.error("Failed to send git status update for node execution: {}, plan execution: {}",
          nodeExecution.getNodeId(), nodeExecution.getPlanExecutionId(), ex);
    }
  }

  private void processGitStatus(
      NodeExecution nodeExecution, PlanNode planNode, PlanExecutionMetadata planExecutionMetadata) {
    StepCategory stepCategory = nodeExecution.getCurrentLevel().getStepType().getStepCategory();

    if (!gitBuildStatusUtility.shouldSendStatus(stepCategory)) {
      return;
    }

    String stageIdentifier = getStageIdentifier(planNode, nodeExecution);
    String pipelineName = getPipelineName(nodeExecution);
    GitStatusNotificationParams gitStatusNotificationParams =
        buildContext(nodeExecution, planExecutionMetadata, stageIdentifier, pipelineName);
    gitBuildStatusUtility.sendStatusToGit(gitStatusNotificationParams);
  }

  @VisibleForTesting
  protected String getPipelineName(NodeExecution nodeExecution) {
    String pipelineIdentifier = nodeExecution.getPipelineIdentifier();
    try {
      PipelineExecutionSummaryEntity summary = pmsExecutionService.getPipelineExecutionSummaryEntity(
          nodeExecution.getAccountId(), nodeExecution.getPlanExecutionId(), false);
      if (summary != null && EmptyPredicate.isNotEmpty(summary.getName())) {
        return summary.getName();
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch pipeline display name for planExecutionId: {}, falling back to identifier",
          nodeExecution.getPlanExecutionId(), ex);
    }
    return pipelineIdentifier;
  }

  @VisibleForTesting
  protected String getStageIdentifier(PlanNode planNode, NodeExecution nodeExecution) {
    String stageIdentifier = planNode.getSendGitStatus().getName();

    if (EmptyPredicate.isEmpty(stageIdentifier)) {
      return nodeExecution.getIdentifier();
    }

    if (NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, stageIdentifier)) {
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      stageIdentifier = (String) pmsEngineExpressionService.resolve(
          ambiance, planNode.getSendGitStatus().getName(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    }
    return EmptyPredicate.isNotEmpty(stageIdentifier) ? stageIdentifier : nodeExecution.getIdentifier();
  }

  @VisibleForTesting
  protected GitStatusNotificationParams buildContext(
      NodeExecution nodeExecution, PlanExecutionMetadata planExecutionMetadata, String stageIdentifier) {
    return buildContext(nodeExecution, planExecutionMetadata, stageIdentifier, nodeExecution.getPipelineIdentifier());
  }

  @VisibleForTesting
  protected GitStatusNotificationParams buildContext(NodeExecution nodeExecution,
      PlanExecutionMetadata planExecutionMetadata, String stageIdentifier, String pipelineName) {
    PullRequestHook prHook = planExecutionMetadata.getTriggerPayload().getParsedPayload().getPr();
    PullRequest pr = prHook.getPr();
    String sha = pr.getSha();
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess,
        planExecutionMetadata.getTriggerPayload().getConnectorRef(), true, harnessCodeClientConfig.getBaseUrl());
    GitStatusNotificationParamsBuilder builder = GitStatusNotificationParams.builder()
                                                     .accountId(nodeExecution.getAccountId())
                                                     .ambiance(ambiance)
                                                     .connectorDetails(connectorDetails)
                                                     .repoName(prHook.getRepo().getName())
                                                     .nodeId(nodeExecution.getNodeId())
                                                     .title(pr.getTitle())
                                                     .pipelineIdentifier(nodeExecution.getPipelineIdentifier())
                                                     .pipelineName(pipelineName)
                                                     .planId(nodeExecution.getPlanId())
                                                     .stageIdentifier(stageIdentifier)
                                                     .status(nodeExecution.getStatus())
                                                     .sha(sha)
                                                     .targetUrl(pr.getTarget())
                                                     .prNumber(String.valueOf(pr.getNumber()))
                                                     .stageExecutionId(nodeExecution.getStageExecutionId())
                                                     .owner(prHook.getRepo().getNamespace())
                                                     .planExecutionId(nodeExecution.getPlanExecutionId());
    if (nodeExecution.getStartTs() != null) {
      builder.startTs(nodeExecution.getStartTs());
    }

    return builder.build();
  }

  private void processGitOpsStatusUpdate(NodeExecution nodeExecution, Ambiance ambiance) {
    if (!StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
      return;
    }

    Node node = planService.fetchNode(nodeExecution.getPlanId(), nodeExecution.getNodeId());
    if (!(node instanceof PlanNode)) {
      return;
    }
    PlanNode planNode = (PlanNode) node;
    if (planNode.getSendGitStatus() == null || !planNode.getSendGitStatus().getEnabled()) {
      return;
    }

    OptionalSweepingOutput statusCheckOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObjectUsingGroup(
            GitopsConstants.GITOPS_STATUS_CHECK_OUTPUT, StepOutcomeGroup.STAGE.name()));

    if (!statusCheckOutput.isFound()) {
      return;
    }

    GitOpsStatusCheckOutput output = (GitOpsStatusCheckOutput) statusCheckOutput.getOutput();

    String statusName = resolveStatusCheckName(planNode, ambiance);
    String executionUrl = gitBuildStatusUtility.getBuildDetailsUrl(ambiance);

    for (GitOpsPRStatusInfo prInfo : output.getPrStatusInfos()) {
      try {
        boolean posted = gitOpsGitStatusHelper.sendFinalStatus(
            ambiance, prInfo, nodeExecution.getStatus(), statusName, executionUrl);
        if (!posted) {
          log.warn("Git status post rejected for PR #{} (sha={})", prInfo.getPrNumber(), prInfo.getSha());
        }
      } catch (Exception e) {
        log.warn("Failed to post final git status for PR #{} (sha={})", prInfo.getPrNumber(), prInfo.getSha(), e);
      }
    }
  }

  /**
   * Resolves the git status check name (the "context" field in the GitHub statuses API) at stage end:
   *   1. If sendGitStatus.name is set → resolve Harness expressions and use the result.
   *   2. If the resolved value is empty (e.g. an unresolvable expression) → fall back to
   * "pipelineIdentifier/stageIdentifier".
   *   3. If sendGitStatus.name is not set then default to "pipelineIdentifier/stageIdentifier".
   */
  @VisibleForTesting
  String resolveStatusCheckName(PlanNode planNode, Ambiance ambiance) {
    String stageIdentifier = AmbianceUtils.getStageIdentifierFromAmbiance(ambiance);
    String pipelineIdentifier = AmbianceUtils.getPipelineIdentifier(ambiance);
    String defaultName = pipelineIdentifier + "/" + stageIdentifier;
    String name = planNode.getSendGitStatus() == null ? null : planNode.getSendGitStatus().getName();

    if (EmptyPredicate.isEmpty(name)) {
      return defaultName;
    }

    if (NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, name)) {
      name = (String) pmsEngineExpressionService.resolve(ambiance, name, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    }
    return EmptyPredicate.isNotEmpty(name) ? name : defaultName;
  }
}
