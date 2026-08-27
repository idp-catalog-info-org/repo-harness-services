/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionBackfillService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;

import com.google.inject.Inject;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class NodeExecutionBackfillServiceImpl implements NodeExecutionBackfillService {
  @Inject private NodeExecutionService nodeExecutionService;

  public void replayNodeExecutionEvents(String planExecutionId, String module) {
    log.info("Fetch node executions for: {} and module: {}", planExecutionId, module);
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchAllNodeExecutions(planExecutionId,
             Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.module, NodeExecutionKeys.status,
                 NodeExecutionKeys.endTs))) {
      stream.forEach(nodeExecution -> {
        if (isEmpty(module) || !module.equalsIgnoreCase(nodeExecution.getModule())) {
          return;
        }

        nodeExecutionService.emitEvent(nodeExecution, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE, true);
      });
    }
  }
}
