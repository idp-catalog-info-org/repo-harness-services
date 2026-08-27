/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.stage.StageStatusEvent;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StageStatusEventProducer {
  @Inject @KafkaModule.General private Optional<HKafkaProtoProducer> hKafkaProtoProducer;
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject private PlanService planService;

  public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean sendStatusToGitEnabled =
        featureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT);
    boolean gitOpsStatusEnabled =
        !featureFlagService.isEnabled(accountId, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED);
    if (hKafkaProtoProducer.isEmpty() || (!sendStatusToGitEnabled && !gitOpsStatusEnabled)) {
      return;
    }
    Node node = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    if (!(node instanceof PlanNode)) {
      return;
    }
    PlanNode planNode = (PlanNode) node;
    /*
     * Currently the implementation is to send the stage status event only if the sendGitStatus is enabled.
     * This is to ensure that we do not send unnecessary events when the feature is not enabled.
     * If in the future we want to send stage status events regardless of the sendGitStatus setting,
     * we can remove this check.
     */
    if (null == planNode.getSendGitStatus() || !planNode.getSendGitStatus().getEnabled()) {
      return;
    }

    StageStatusEvent stageStatusEvent = StageStatusEvent.newBuilder()
                                            .setAccountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                            .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                            .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                            .setPipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
                                            .setNodeExecutionId(nodeOutboxInfo.getNodeExecution().getUuid())
                                            .setPlanExecutionId(ambiance.getPlanExecutionId())
                                            .setStatus(nodeOutboxInfo.getStatus().name())
                                            .build();

    hKafkaProtoProducer.get().send(EventsFrameworkKafkaTopicResolver.getPipelineStageStatusTopic(), stageStatusEvent,
        Collections.emptyMap(), stageStatusEvent.getStageExecutionId());
  }
}
