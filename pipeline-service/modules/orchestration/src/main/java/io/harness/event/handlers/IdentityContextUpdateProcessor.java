/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.IdentityExecutionContext;
import io.harness.pms.contracts.execution.IdentityContextUpdateResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.SdkResponseEventUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Save-back leg of the identity enrich flow: persists the enriched {@link IdentityExecutionContext} the
 * step emitted (via {@link IdentityContextUpdateResponse}) back onto the node execution's stored ambiance.
 */
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class IdentityContextUpdateProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    String nodeExecutionId = SdkResponseEventUtils.getNodeExecutionId(event);
    IdentityContextUpdateResponse response =
        event.getAddExecutableResponseRequest().getExecutableResponse().getIdentityContextUpdate();
    IdentityExecutionContext updatedContext = response.getUpdatedContext();

    // Persist the WHOLE executionContext (converter-safe): a dot-path set of the raw proto has no registered
    // converter and would write malformed BSON, so rebuild the parent and set it as one field.
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        nodeExecutionId, Sets.newHashSet(NodeExecutionKeys.executionContext));
    if (nodeExecution == null) {
      log.warn("NodeExecution [{}] not found in DB; skipping identity context save-back", nodeExecutionId);
      return;
    }
    ExecutionContext existing = nodeExecution.getExecutionContext();
    if (existing == null) {
      log.warn("NodeExecution [{}] exists but has no executionContext; skipping identity context save-back",
          nodeExecutionId);
      return;
    }
    ExecutionContext merged = existing.toBuilder().setIdentityExecutionContext(updatedContext).build();
    nodeExecutionService.updateV2(nodeExecutionId, ops -> ops.set(NodeExecutionKeys.executionContext, merged));
    log.info("Updated identityExecutionContext for nodeExecutionId {}", nodeExecutionId);
  }
}
