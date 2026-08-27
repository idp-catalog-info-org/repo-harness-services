/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.plan.execution;

import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.category.element.UnitTests;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PmsExecutionSummaryDtoUpdateHelperTest extends CategoryTest {
  private static final String accountId = "accountId";

  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Inject @InjectMocks PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetQueuedReason() {
    doReturn(PlanExecutionSettingResponse.builder().shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(accountId);
    String planExecutionId = "planExecutionId";
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION);
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .planExecutionId(planExecutionId)
                                                                .status(ExecutionStatus.QUEUED_PLAN_CREATION)
                                                                .build();
    QueuedType queuedType = pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity);
    assertThat(queuedType).isNull();
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION);
    queuedType = pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity);
    assertThat(queuedType).isEqualTo(QueuedType.MAX_CONCURRENCY_REACHED);
    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(accountId)
                                                                 .planExecutionId(planExecutionId)
                                                                 .status(ExecutionStatus.RUNNING)
                                                                 .build();
    queuedType = pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity1);
    assertThat(queuedType).isNull();
  }
}
