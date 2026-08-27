/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterService;
import io.harness.engine.executions.concurrency.rebuild.PlanConcurrencyCounterRebuildJob;
import io.harness.engine.executions.concurrency.rebuild.StepConcurrencyCounterRebuildJob;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.PlanConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.StepConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.service.helper.ForceAbortPlanExecutionsHelper;
import io.harness.pms.plan.execution.service.ExecutionSummaryBackfillService;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineAdminResourceServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";

  @Mock private BlockExecutionMetadataService blockExecutionMetadataService;
  @Mock private PipelineRetentionService pipelineRetentionService;
  @Mock private ExecutionSummaryBackfillService executionReplayService;
  @Mock private ForceAbortPlanExecutionsHelper forceAbortPlanExecutionsHelper;
  @Mock private StepConcurrencyCounterRebuildJob stepConcurrencyCounterRebuildJob;
  @Mock private StepConcurrencyCounterService stepConcurrencyCounterService;
  @Mock private PlanConcurrencyCounterRebuildJob planConcurrencyCounterRebuildJob;
  @Mock private PlanConcurrencyCounterService planConcurrencyCounterService;

  @InjectMocks private PipelineAdminResourceServiceImpl pipelineAdminResourceService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputeStepConcurrencyCountersDelegatesToRebuildJob() {
    pipelineAdminResourceService.recomputeStepConcurrencyCounters();

    verify(stepConcurrencyCounterRebuildJob).rebuild();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterReadsClusterCount() {
    when(stepConcurrencyCounterService.getClusterCount()).thenReturn(5L);

    StepConcurrencyCounterResponseDTO response =
        pipelineAdminResourceService.getStepConcurrencyCounter("cluster", null);

    assertThat(response.getScope()).isEqualTo("cluster");
    assertThat(response.getValue()).isEqualTo(5L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterReadsAccountCount() {
    when(stepConcurrencyCounterService.getAccountCount(ACCOUNT_ID)).thenReturn(7L);

    StepConcurrencyCounterResponseDTO response =
        pipelineAdminResourceService.getStepConcurrencyCounter("account", ACCOUNT_ID);

    assertThat(response.getScope()).isEqualTo("account");
    assertThat(response.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getValue()).isEqualTo(7L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterReadsAllAccountCountsWhenAccountIdOmitted() {
    Map<String, Long> allCounts = Map.of(ACCOUNT_ID, 7L, "otherAccount", 2L);
    when(stepConcurrencyCounterService.getAllAccountCounts()).thenReturn(allCounts);

    StepConcurrencyCounterResponseDTO response =
        pipelineAdminResourceService.getStepConcurrencyCounter("account", null);

    assertThat(response.getScope()).isEqualTo("account");
    assertThat(response.getAccountCounts()).isEqualTo(allCounts);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterThrowsForInvalidScope() {
    assertThatThrownBy(() -> pipelineAdminResourceService.getStepConcurrencyCounter("bogus", null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputePlanConcurrencyCountersDelegatesToRebuildJob() {
    pipelineAdminResourceService.recomputePlanConcurrencyCounters();

    verify(planConcurrencyCounterRebuildJob).rebuild();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getPlanConcurrencyCountersReturnsAccountAndProjectCounts() {
    when(planConcurrencyCounterService.getAccountCount(ACCOUNT_ID)).thenReturn(7L);
    Map<String, Long> projectCounts = Map.of("projA", 3L, "projB", 1L);
    when(planConcurrencyCounterService.getProjectCountsForAccount(ACCOUNT_ID)).thenReturn(projectCounts);

    PlanConcurrencyCounterResponseDTO response = pipelineAdminResourceService.getPlanConcurrencyCounters(ACCOUNT_ID);

    assertThat(response.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getAccountCount()).isEqualTo(7L);
    assertThat(response.getProjectCounts()).containsOnly(Map.entry("projA", 3L), Map.entry("projB", 1L));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getPlanConcurrencyCountersThrowsForEmptyAccount() {
    assertThatThrownBy(() -> pipelineAdminResourceService.getPlanConcurrencyCounters(""))
        .isInstanceOf(InvalidRequestException.class);
  }
}
