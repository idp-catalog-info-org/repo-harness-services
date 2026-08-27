/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionsCacheTest extends CategoryTest {
  NodeExecutionsCache nodeExecutionsCache;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PLAN_EXECUTION_ID").build();
    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testFindAllTerminalChildrenStatusOnlyWithOptimizationFeatureFlagEnabled() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("test").status(Status.ABORTED).build()))
        .when(nodeExecutionService)
        .findAllChildrenWithStatusInAndWithoutOldRetriesV2(
            "PLAN_EXECUTION_ID", "PARENT_ID", StatusUtils.finalStatuses(), false);

    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class)) {
      mockAmbianceUtils
          .when(()
                    -> AmbianceUtils.checkIfFeatureFlagEnabled(
                        nodeExecutionsCache.getAmbiance(), "PIPE_FIND_ALL_TERMINAL_CHILDREN_OPTIMIZATION"))
          .thenReturn(true);

      List<Status> allChildren = nodeExecutionsCache.findAllTerminalChildrenStatusOnly("PARENT_ID", false, false);
      assertThat(allChildren.size()).isEqualTo(1);
      assertThat(allChildren.get(0)).isEqualTo(Status.ABORTED);
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testFindAllTerminalChildrenStatusOnlyWithOptimizationFeatureFlagEnabledAndAdvisorProcessed() {
    doReturn(
        Arrays.asList(NodeExecution.builder().identifier("test").status(Status.FAILED).advisorsProcessed(false).build(),
            NodeExecution.builder().identifier("test2").status(Status.SUCCEEDED).advisorsProcessed(true).build()))
        .when(nodeExecutionService)
        .findAllChildrenWithStatusInAndWithoutOldRetriesV2(
            "PLAN_EXECUTION_ID", "PARENT_ID", StatusUtils.finalStatuses(), false);

    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class)) {
      mockAmbianceUtils
          .when(()
                    -> AmbianceUtils.checkIfFeatureFlagEnabled(
                        nodeExecutionsCache.getAmbiance(), "PIPE_FIND_ALL_TERMINAL_CHILDREN_OPTIMIZATION"))
          .thenReturn(true);

      List<Status> allChildren = nodeExecutionsCache.findAllTerminalChildrenStatusOnly("PARENT_ID", false, true);
      assertThat(allChildren.size()).isEqualTo(1);
      assertThat(allChildren.get(0)).isEqualTo(Status.SUCCEEDED);
    }
  }
}
