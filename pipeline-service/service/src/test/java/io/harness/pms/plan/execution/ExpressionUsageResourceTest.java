/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity.ExecutionExpressionUsagesEntityKeys;
import io.harness.engine.expressions.usages.dto.ExecutionContextResponse;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExpressionUsageResourceTest extends CategoryTest {
  @InjectMocks ExpressionUsageResource expressionUsageResource;
  @Mock AccessControlClient accessControlClient;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private PlanExecutionService planExecutionService;

  @Mock ExecutionExpressionUsageService executionExpressionUsageService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PLAN_EXECUTION_ID = "planId";
  private final String NODE_EXECUTION_ID = "nodeExecutionId";

  PipelineEntity entity;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .build();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                 .planExecutionId(PLAN_EXECUTION_ID)
                                 .name(PLAN_EXECUTION_ID)
                                 .runSequence(0)
                                 .entityGitDetails(entityGitDetails)
                                 .build();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateNotesForPlanExecutionNegativeCase() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .orgIdentifier(ORG_IDENTIFIER)
                                                                        .projectIdentifier(PROJ_IDENTIFIER)
                                                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                        .pipelineDeleted(false)
                                                                        .planExecutionId(PLAN_EXECUTION_ID)
                                                                        .build();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(
            ACCOUNT_ID, PLAN_EXECUTION_ID, Set.of(PlanExecutionSummaryKeys.pipelineIdentifier));
    List<ExecutionExpressionUsagesEntity> resolvedExpressionsResponse =
        Arrays.asList(ExecutionExpressionUsagesEntity.builder()
                          .expression("expression1")
                          .expressionValue("value1")
                          .isError(false)
                          .build(),
            ExecutionExpressionUsagesEntity.builder().expression("expression2").isError(true).build());
    when(executionExpressionUsageService.getExpressionsWithProjection(PLAN_EXECUTION_ID, NODE_EXECUTION_ID,
             Sets.newHashSet(ExecutionExpressionUsagesEntityKeys.expression,
                 ExecutionExpressionUsagesEntityKeys.expressionValue, ExecutionExpressionUsagesEntityKeys.isError)))
        .thenReturn(resolvedExpressionsResponse);
    ExecutionContextResponse result =
        expressionUsageResource
            .getExecutionContext(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, NODE_EXECUTION_ID)
            .getData();
    assertEquals(1, result.getResolvedExpressionDTOS().size());
    assertEquals("expression1", result.getResolvedExpressionDTOS().get(0).getExpression());

    assertEquals(1, result.getFailedExpressions().size());
    assertEquals("expression2", result.getFailedExpressions().get(0));
  }
}
