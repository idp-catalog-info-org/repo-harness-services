/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.ScopeInfo;
import io.harness.dto.FailureInfoDTO;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.exception.ExceptionUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ExecutionInfoUpdateEventHandler implements PlanStatusUpdateObserver {
  private final PMSPipelineService pmsPipelineService;
  private final PlanExecutionService planExecutionService;
  private final RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;

  @Inject
  public ExecutionInfoUpdateEventHandler(PMSPipelineService pmsPipelineService,
      PlanExecutionService planExecutionService, RecentExecutionsInfoHelper recentExecutionsInfoHelper,
      ScopeResolutionHelper scopeResolutionHelper, PmsExecutionSummaryService pmsExecutionSummaryService) {
    this.pmsPipelineService = pmsPipelineService;
    this.planExecutionService = planExecutionService;
    this.recentExecutionsInfoHelper = recentExecutionsInfoHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(planExecutionId);
    String parentUniqueId = resolveParentUniqueId(ambiance, accountId, orgId, projectId);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(parentUniqueId)
                              .build();
    FailureInfo failureInfo = StatusUtils.brokeStatuses().contains(planExecution.getStatus())
        ? resolveFailureInfo(accountId, planExecution)
        : null;
    recentExecutionsInfoHelper.onExecutionUpdate(ambiance, planExecution, scopeInfo, true, failureInfo);
    updateExecutionInfoInPipelineEntity(ambiance, planExecution.getStatus(), scopeInfo, true);
  }

  FailureInfo resolveFailureInfo(String accountIdentifier, PlanExecution planExecution) {
    Optional<PlanExecution> planExecutionWithFailureInfo = planExecutionService.getWithFieldsIncludedOptional(
        planExecution.getUuid(), Set.of(PlanExecutionKeys.failureInfo));
    if (planExecutionWithFailureInfo.isPresent() && planExecutionWithFailureInfo.get().getFailureInfo() != null) {
      return planExecutionWithFailureInfo.get().getFailureInfo();
    }
    PipelineExecutionSummaryEntity summary = pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
        accountIdentifier, planExecution.getUuid(), Set.of(PlanExecutionSummaryKeys.failureInfo));
    FailureInfoDTO failureInfoDTO = summary != null ? summary.getFailureInfo() : null;
    return FailureInfoDTOConverter.toFailureInfo(failureInfoDTO);
  }

  String resolveParentUniqueId(Ambiance ambiance, String accountId, String orgId, String projectId) {
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    if (isEmpty(parentUniqueId)) {
      log.info("parentUniqueId not found in ambiance for planExecutionId [{}], resolving via ScopeResolutionHelper",
          ambiance.getPlanExecutionId());
      parentUniqueId = scopeResolutionHelper.getParentUniqueId(accountId, orgId, projectId);
      if (isEmpty(parentUniqueId)) {
        log.warn("Unable to resolve parentUniqueId for planExecutionId [{}]", ambiance.getPlanExecutionId());
      }
    }
    return parentUniqueId;
  }

  void updateExecutionInfoInPipelineEntity(
      Ambiance ambiance, Status planExecutionStatus, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // this security context guard is needed because now pipeline get requires proper permissions to be set in the case
    // when the Pipeline is REMOTE
    try (PmsSecurityContextEventGuard ignore = new PmsSecurityContextEventGuard(ambiance)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
      Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(
          accountId, orgId, projectId, pipelineId, false, true, false, false, scopeInfo, isParentIdQueryingEnabled);
      if (pipelineEntity.isEmpty()) {
        return;
      }
      ExecutionSummaryInfo executionSummaryInfo = pipelineEntity.get().getExecutionSummaryInfo();
      if (executionSummaryInfo != null) {
        executionSummaryInfo.setLastExecutionStatus(ExecutionStatus.getExecutionStatus(planExecutionStatus));
        if (StatusUtils.brokeStatuses().contains(planExecutionStatus)) {
          Map<String, Integer> errors = executionSummaryInfo.getNumOfErrors();
          Date date = new Date();
          SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
          String strDate = formatter.format(date);
          if (errors.containsKey(strDate)) {
            errors.put(strDate, errors.get(strDate) + 1);
          } else {
            errors.put(strDate, 1);
          }
        }
        pmsPipelineService.saveExecutionInfo(scopeInfo, pipelineId, executionSummaryInfo, isParentIdQueryingEnabled);
      } else {
        log.error("ExecutionSummaryInfo is null for executionId - " + ambiance.getPlanExecutionId());
      }
    } catch (Exception e) {
      log.error("Error while updating Plan Status for execution with ID " + ambiance.getPlanExecutionId() + ": "
              + ExceptionUtils.getMessage(e),
          e);
    }
  }
}
