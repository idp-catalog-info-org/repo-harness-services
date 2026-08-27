/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.IACM_PREPARE_EXECUTION_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.IACM_PREPARE_EXECUTION_NODE_NAME;
import static io.harness.ci.states.V1.iacm.UnifiedIACMPrepareParameters.UnifiedIACMPrepareParametersBuilder;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.StageChildrenEntitiesType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.V1.iacm.UnifiedIACMPrepareParameters;
import io.harness.ci.states.V1.iacm.UnifiedIACMPrepareStep;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IACM)
@UtilityClass
@Slf4j
public class IACMPlanCreatorUtils {
  public static LinkedHashMap<String, PlanCreationResponse> addIACMNode(KryoSerializer kryoSerializer,
      String nextNodeID, String workspaceNodeId, Map<String, Object> iacmNodesInfo, boolean isStepInsideRollback,
      PlanCreationContext ctx) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    UnifiedIACMPrepareParameters unifiedIACMPrepareExecutionStepParameters = buildStepParams(iacmNodesInfo, ctx);
    final PlanNode node =
        PlanNode.builder()
            .uuid(workspaceNodeId)
            .stepType(UnifiedIACMPrepareStep.STEP_TYPE)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .name(IACM_PREPARE_EXECUTION_NODE_NAME)
            .identifier(IACM_PREPARE_EXECUTION_NODE_ID)
            .stepParameters(unifiedIACMPrepareExecutionStepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeID).build())))
                    .build())
            .skipExpressionChain(true)
            .whenCondition(RunInfoUtilsV1.getStepWhenCondition(null, isStepInsideRollback))
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return planCreationResponseMap;
  }

  private static UnifiedIACMPrepareParameters buildStepParams(
      Map<String, Object> iacmNodesInfo, PlanCreationContext ctx) {
    ParameterField<String> workspaceId =
        (ParameterField<String>) iacmNodesInfo.get(YAMLFieldNameConstants.IACM_WORKSPACE);
    ParameterField<String> moduleId =
        (ParameterField<String>) iacmNodesInfo.get(YAMLFieldNameConstants.IACM_TOFU_MODULE);
    ParameterField<String> remoteExecutionId =
        (ParameterField<String>) iacmNodesInfo.get(YAMLFieldNameConstants.IACM_REMOTE_EXECUTION);
    ParameterField<List<String>> playbooks =
        (ParameterField<List<String>>) iacmNodesInfo.get(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    ParameterField<List<String>> inventories =
        (ParameterField<List<String>>) iacmNodesInfo.get(YAMLFieldNameConstants.IACM_INVENTORIES);

    UnifiedIACMPrepareParametersBuilder stepParamBuilder = UnifiedIACMPrepareParameters.builder()
                                                               .workspaceId(workspaceId)
                                                               .moduleTestId(moduleId)
                                                               .remoteExecutionId(remoteExecutionId)
                                                               .playbooks(playbooks)
                                                               .inventories(inventories);

    ExecutionTriggerInfo triggerInfo = ctx.getTriggerInfo();
    if (triggerInfo.getTriggerType() == TriggerType.WEBHOOK
        || (triggerInfo.getIsRerun() && triggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK)) {
      addWebhookInfo(stepParamBuilder, ctx.getTriggerPayload(), iacmNodesInfo);
    }
    return stepParamBuilder.build();
  }

  static Map<String, Object> getIacmNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    Map<String, Object> entitiesInfoMap = new HashMap<>();
    if (ParameterField.isNotNull(stageNode.getWorkspace())) {
      entitiesInfoMap.put(YAMLFieldNameConstants.IACM_WORKSPACE, stageNode.getWorkspace());
    }
    if (ParameterField.isNotNull(stageNode.getRemoteExecution())) {
      entitiesInfoMap.put(YAMLFieldNameConstants.IACM_REMOTE_EXECUTION, stageNode.getRemoteExecution());
    }
    if (ParameterField.isNotNull(stageNode.getTofuModule())) {
      entitiesInfoMap.put(YAMLFieldNameConstants.IACM_TOFU_MODULE, stageNode.getTofuModule());
    }
    if (ParameterField.isNotNull(stageNode.getPlaybooks())) {
      entitiesInfoMap.put(YAMLFieldNameConstants.IACM_PLAYBOOKS, stageNode.getPlaybooks());
    }
    if (ParameterField.isNotNull(stageNode.getInventories())) {
      entitiesInfoMap.put(YAMLFieldNameConstants.IACM_INVENTORIES, stageNode.getInventories());
    }
    return entitiesInfoMap;
  }

  static void getIacmStageChildrenEntitiesInfo(
      Map<String, Object> modulesImplicitNodesInfo, ListValue.Builder stageChildren) {
    if (modulesImplicitNodesInfo.containsKey(TemplateType.IACM.getName())) {
      Map<String, Object> iacmModuleInfo =
          (Map<String, Object>) modulesImplicitNodesInfo.get(TemplateType.IACM.getName());
      if (iacmModuleInfo.containsKey(YAMLFieldNameConstants.IACM_WORKSPACE)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.WORKSPACE.getDisplayName()).build());
      }
      if (iacmModuleInfo.containsKey(YAMLFieldNameConstants.IACM_REMOTE_EXECUTION)) {
        stageChildren.addValues(HarnessValue.newBuilder()
                                    .setStringValue(StageChildrenEntitiesType.REMOTE_EXECUTION.getDisplayName())
                                    .build());
      }
      if (iacmModuleInfo.containsKey(YAMLFieldNameConstants.IACM_TOFU_MODULE)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.MODULE.getDisplayName()).build());
      }
      if (iacmModuleInfo.containsKey(YAMLFieldNameConstants.IACM_PLAYBOOKS)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.PLAYBOOKS.getDisplayName()).build());
      }
      if (iacmModuleInfo.containsKey(YAMLFieldNameConstants.IACM_INVENTORIES)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.INVENTORIES.getDisplayName()).build());
      }
    }
  }

  private static void addWebhookInfo(UnifiedIACMPrepareParametersBuilder stepParamBuilder,
      TriggerPayload triggerPayload, Map<String, Object> iacmNodesInfo) {
    ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
    String eventType;
    String link;
    Repository repo;
    switch (parsedPayload.getPayloadCase()) {
      case PR
          -> {
        PullRequest pr = parsedPayload.getPr().getPr(); link = pr.getLink(); eventType = pr.getMerged() ? "merged":
        "pull_request";
        repo = parsedPayload.getPr().getRepo();
    }
      case PUSH -> {
        eventType = "push";
        link = parsedPayload.getPush().getCommit().getLink();
        repo = parsedPayload.getPush().getRepo();
      }
      case RELEASE -> {
        eventType = "release";
        link = parsedPayload.getRelease().getRelease().getLink();
        repo = parsedPayload.getRelease().getRepo();
      }
      case BRANCH -> {
        eventType = "branch";
        link ="";
        repo = parsedPayload.getBranch().getRepo();
      }
      case TAG -> {
        eventType = "tag";
        link = "";
        repo = parsedPayload.getTag().getRepo();
      }
      default -> throw new IllegalStateException("Unexpected value: " + parsedPayload.getPayloadCase());
    }

    String fullRepoName = isEmpty(repo.getNamespace()) ? repo.getName() :
        format("%s/%s", repo.getNamespace(), repo.getName());

        stepParamBuilder.webhookLink(link)
            .webhookEventType(eventType)
            .webhookRepo(fullRepoName)
            .webhookConnector(triggerPayload.getConnectorRef());

        Map<String, String> iacmWebhookInfoMap = new HashMap<>();
        iacmWebhookInfoMap.put("type", eventType);
        iacmWebhookInfoMap.put("connector", triggerPayload.getConnectorRef());
        iacmWebhookInfoMap.put("repo", fullRepoName);
        iacmWebhookInfoMap.put("link", link);

        iacmNodesInfo.put(YAMLFieldNameConstants.IACM_WEBHOOK_INFO, iacmWebhookInfoMap);
  }
}
