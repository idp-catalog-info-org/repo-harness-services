/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.node.service.NodeExecutionBackfillService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@Slf4j
public class ExecutionSummaryBackfillService {
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private NodeExecutionBackfillService nodeExecutionBackfillService;
  @Inject @Named("ExecutionSummaryBackfillExecutorService") private ExecutorService executorService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  public void replayNodeExecutions(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String module, long startTs, long endTs) {
    Criteria criteria = new Criteria();

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    setScopeCriteriaWithParentUniqueId(criteria, scopeInfo);
    criteria.and(PlanExecutionSummaryKeys.startTs).gte(startTs);
    criteria.and(PlanExecutionSummaryKeys.endTs).lte(endTs);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);

    try (var stream = pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(query)) {
      stream.forEach(executionSummary -> {
        final String planExecutionId = executionSummary.getPlanExecutionId();
        executorService.submit(() -> {
          try {
            nodeExecutionBackfillService.replayNodeExecutionEvents(planExecutionId, module);
          } catch (Exception e) {
            log.warn("Failed to replay orchestration events for plan execution id: {}", planExecutionId);
          }
        });
      });
    }
  }

  private void setScopeCriteriaWithParentUniqueId(Criteria criteria, ScopeInfo scopeInfo) {
    criteria.and(PlanExecutionSummaryKeys.accountId)
        .is(scopeInfo.getAccountIdentifier())
        .and(PlanExecutionSummaryKeys.parentUniqueId)
        .is(scopeInfo.getUniqueId());
  }
}
