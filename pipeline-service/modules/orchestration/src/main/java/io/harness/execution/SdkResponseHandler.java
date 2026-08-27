/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseHandler extends PmsBaseEventHandler<SdkResponseEventProto> {
  @Inject private OrchestrationEngine engine;
  @Inject private BlockExecutionMetadataService blockExecutionMetadataService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  // these are events that doesn't create an orchestration change. node execution state is not impacted by these
  private static final List<SdkResponseEventType> ignoreEvents =
      List.of(SdkResponseEventType.UNKNOWN_EVENT_TYPE, SdkResponseEventType.HANDLE_PROGRESS);

  @Override
  protected Map<String, String> extraLogProperties(SdkResponseEventProto event) {
    return ImmutableMap.of("eventType", event.getSdkResponseEventType().name(), "nodeExecutionId",
        SdkResponseEventUtils.getNodeExecutionId(event), "planExecutionId",
        SdkResponseEventUtils.getPlanExecutionId(event));
  }

  @Override
  protected Ambiance extractAmbiance(SdkResponseEventProto event) {
    return event.getAmbiance();
  }

  @Override
  protected String getEventType(SdkResponseEventProto message) {
    return message.getSdkResponseEventType().name();
  }

  @Override
  protected void handleEventWithContext(SdkResponseEventProto event) {
    if (blockExecutionMetadataService.validate(event.getAmbiance())) {
      return;
    }
    var accountId = AmbianceUtils.getAccountId(event.getAmbiance());
    var stuckMonitorV2Enabled = !pmsFeatureFlagHelper.isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);
    if (stuckMonitorV2Enabled) {
      // TODO: we need to mark processing as false before handling the sdk response. The reason is that in
      // sdkEventHandler we often issue new events, having a possibility of race condition
      if (!ignoreEvents.contains(event.getSdkResponseEventType())) {
        var nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance());
        // markNodesProcessing already retries once on transient Mongo failures, but this runs on a no-ack consumer
        // (HKafkaConsumer.runNoAck), so an uncaught exception here silently drops the SDK response event and leaves
        // the node stuck in async-wait until the pipeline timeout. If the retry is still exhausted, fail the node
        // explicitly (via handleError) so it errors out fast instead of hanging - and do NOT handle the response,
        // since the pre-processing has failed. See PIPE-35791.
        try {
          nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecutionId), false);
        } catch (Exception ex) {
          log.error("Failed to mark node {} as processing before handling SDK response event. Failing the node.",
              nodeExecutionId, ex);
          engine.handleError(event.getAmbiance(), ex);
          return;
        }
      }
    }
    // This is the event for new execution
    engine.handleSdkResponseEvent(event);
    // Old flow, processing is marked as false after handling the sdkResponseEvent
    if (!stuckMonitorV2Enabled) {
      if (ignoreEvents.contains(event.getSdkResponseEventType())) {
        return;
      }
      var nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance());
      // Best-effort bookkeeping; the response has already been handled above so failing here must not bubble up to
      // the no-ack consumer. See PIPE-35791.
      try {
        nodeExecutionService.markNodesProcessing(Collections.singletonList(nodeExecutionId), false);
      } catch (Exception ex) {
        log.error("Failed to mark node {} as not processing after handling SDK response event.", nodeExecutionId, ex);
      }
    }
  }
}
