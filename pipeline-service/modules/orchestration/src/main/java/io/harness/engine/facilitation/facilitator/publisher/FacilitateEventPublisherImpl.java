/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.publisher;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.ModuleType;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class FacilitateEventPublisherImpl implements FacilitateEventPublisher {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsEventSender eventSender;
  @Inject private OrchestrationEngine orchestrationEngine;

  @Override
  public void publishEvent(Ambiance ambiance, PlanNode node) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.forFacilitation);
    FacilitatorEvent event = FacilitatorEvent.newBuilder()
                                 .setNodeExecutionId(nodeExecutionId)
                                 .setAmbiance(ambiance)
                                 .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                                 .setStepType(node.getStepType())
                                 .setNotifyId(generateUuid())
                                 .addAllRefObjects(node.getRefObjects())
                                 .addAllFacilitatorObtainments(node.getFacilitatorObtainments())
                                 .build();

    // markNodesProcessing already retries once on transient Mongo failures. If it is still exhausted, fail the node
    // explicitly instead of publishing the facilitator event, so the node errors out fast rather than hanging until
    // the pipeline timeout. See PIPE-35791.
    try {
      nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecutionId), true);
    } catch (Exception ex) {
      log.error("Failed to mark node {} as processing before publishing facilitator event. Failing the node.",
          nodeExecutionId, ex);
      orchestrationEngine.handleError(ambiance, ex);
      return;
    }
    if (node.isManualExecution()) {
      /*
       This is because we want to run manual execution facilitator in PMS only
       Also We don't want to have deterministic event ID, because we will send the facilitator event again in case of
       manual execution(in case of CI/IACM stages + manual execution)
       */
      eventSender.sendEvent(
          ambiance, event, PmsEventCategory.FACILITATOR_EVENT, ModuleType.PMS.name().toLowerCase(), true, false);
    } else {
      eventSender.sendEvent(ambiance, event, PmsEventCategory.FACILITATOR_EVENT, nodeExecution.getModule(), true, true);
    }
  }
}
