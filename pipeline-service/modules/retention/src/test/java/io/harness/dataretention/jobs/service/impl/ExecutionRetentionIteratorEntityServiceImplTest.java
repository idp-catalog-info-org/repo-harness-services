/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.jobs.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SAKSHI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.exception.InternalServerErrorException;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ExecutionRetentionIteratorEntityServiceImplTest extends CategoryTest {
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @InjectMocks ExecutionRetentionIteratorEntityServiceImpl executionRetentionIteratorEntityService;

  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(
        executionRetentionIteratorEntityService, "dataRetentionConfig", DataRetentionConfig.builder().build());
    ReflectionTestUtils.setField(executionRetentionIteratorEntityService, "executor", (Executor) Runnable::run);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testSyncRecordsToObjectStore_throwsWhenEntityNotFound() {
    when(pmsExecutionSummaryRepository.fetchByPlanExecutionIdFromSecondary(PLAN_EXECUTION_ID)).thenReturn(null);

    assertThatThrownBy(
        () -> executionRetentionIteratorEntityService.syncRecordsToObjectStore(Sets.newHashSet(PLAN_EXECUTION_ID)))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining(PLAN_EXECUTION_ID)
        .hasStackTraceContaining(
            "[DATA_RETENTION]: PipelineExecutionSummaryEntity not found for planExecutionId: " + PLAN_EXECUTION_ID);
  }
}
