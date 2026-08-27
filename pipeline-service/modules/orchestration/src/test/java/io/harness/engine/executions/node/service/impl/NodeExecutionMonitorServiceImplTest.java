/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import static io.harness.engine.executions.node.service.impl.NodeExecutionMonitorServiceImpl.NODE_EXECUTION;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionMonitorService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.monitoring.ExecutionCountWithModuleAndStepTypeResult;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.rule.Owner;

import com.google.common.cache.LoadingCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMonitorServiceImplTest extends CategoryTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanExecutionService planExecutionService;
  @Mock MetricService metricService;
  @Mock Cache<String, Integer> metricsCache;
  @Mock LoadingCache<String, Set<String>> metricsLoadingCache;
  NodeExecutionMonitorService nodeExecutionMonitorService;

  @Before
  public void beforeTest() {
    MockitoAnnotations.openMocks(this);
    nodeExecutionMonitorService = new NodeExecutionMonitorServiceImpl(
        nodeExecutionService, planExecutionService, metricService, metricsCache, metricsLoadingCache);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testRegisterActiveExecutionMetrics() throws ExecutionException {
    doReturn(true).when(metricsCache).putIfAbsent(any(), any());
    doReturn(Collections.EMPTY_LIST).when(planExecutionService).findAllAccountIdsWithExecutionsFromAnalytics();
    doReturn(new HashSet<>())
        .when(metricsLoadingCache)
        .get(PmsEventMonitoringConstants.MODULE + "_" + PmsEventMonitoringConstants.STEP_TYPE + NODE_EXECUTION);

    List<ExecutionCountWithAccountResult> accountResults = new LinkedList<>();
    accountResults.add(ExecutionCountWithAccountResult.builder().accountId("ABC").count(1).build());
    accountResults.add(ExecutionCountWithAccountResult.builder().accountId("DEF").count(5).build());

    List<ExecutionCountWithModuleAndStepTypeResult> moduleResults = new LinkedList<>();
    moduleResults.add(ExecutionCountWithModuleAndStepTypeResult.builder().module("pms").type("Wait").count(30).build());

    ExecutionStatistics result =
        ExecutionStatistics.builder().accountStats(accountResults).moduleAndStepTypeStats(moduleResults).build();

    doReturn(result).when(nodeExecutionService).aggregateRunningNodeExecutionsCount();
    nodeExecutionMonitorService.registerActiveExecutionMetrics();
    verify(metricService, times(3)).recordMetric(anyString(), anyDouble());
  }
}
