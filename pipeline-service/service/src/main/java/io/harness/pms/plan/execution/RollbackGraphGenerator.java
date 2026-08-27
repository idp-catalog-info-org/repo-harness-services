/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.ScopeInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackGraphGenerator {
  PMSExecutionService executionService;
  RetryExecutionHelper retryExecutionHelper;
  PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;
  ScopeResolutionHelper scopeResolutionHelper;

  public ChildExecutionDetailDTO checkAndBuildRollbackGraph(String accountId, String orgId, String projectId,
      PipelineExecutionSummaryEntity executionSummaryEntity, EntityGitDetails entityGitDetails, String childStageNodeId,
      String stageNodeExecutionId, String stageNodeId) {
    // if rollback mode execution has started, then executionSummaryEntity will have its planExecutionId, and the
    // rollback graph will be always there
    boolean generateRollbackGraph = executionSummaryEntity.getRollbackModeExecutionId() != null;
    if (!generateRollbackGraph) {
      return null;
    }
    boolean isPipelineRollbackStageSelected = isPipelineRollbackStageSelected(executionSummaryEntity, stageNodeId);

    String childExecutionId = executionSummaryEntity.getRollbackModeExecutionId();
    PipelineExecutionSummaryEntity executionSummaryEntityForChild =
        executionService.fetchExecutionSummary(accountId, childExecutionId);

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, executionSummaryEntityForChild.getParentUniqueId());

    ExecutionGraph executionGraphForChild = null;
    if (isPipelineRollbackStageSelected && childStageNodeId != null) {
      executionGraphForChild = ExecutionGraphMapper.toExecutionGraph(
          executionService.getOrchestrationGraph(
              accountId, childStageNodeId, executionSummaryEntityForChild.getPlanExecutionId(), stageNodeExecutionId),
          executionSummaryEntityForChild, scopeInfo);
    }
    return ChildExecutionDetailDTO.builder()
        .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityForChild,
            entityGitDetails, retryExecutionHelper.shouldShowRetryHistory(executionSummaryEntityForChild),
            retryExecutionHelper.isLatestExecution(executionSummaryEntityForChild),
            pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntityForChild), scopeInfo))
        .executionGraph(executionGraphForChild)
        .build();
  }

  boolean isPipelineRollbackStageSelected(PipelineExecutionSummaryEntity executionSummaryEntity, String stageNodeId) {
    return executionSummaryEntity.getLayoutNodeMap().containsKey(stageNodeId)
        && executionSummaryEntity.getLayoutNodeMap()
               .get(stageNodeId)
               .getNodeType()
               .equals(StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE);
  }
}
