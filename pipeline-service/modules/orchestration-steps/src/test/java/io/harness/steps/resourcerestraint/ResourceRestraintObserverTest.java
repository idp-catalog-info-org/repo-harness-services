/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ResourceRestraintObserverTest extends OrchestrationStepsTestBase {
  @Mock ResourceRestraintInstanceService restraintInstanceService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock TransactionHelper transactionHelper;
  @Inject @InjectMocks ResourceRestraintObserver observer;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestOnEnd() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    ResourceRestraintInstance activeRc =
        ResourceRestraintInstance.builder().resourceRestraintId(generateUuid()).state(ACTIVE).build();
    ResourceRestraintInstance blockedRc =
        ResourceRestraintInstance.builder().resourceRestraintId(generateUuid()).state(BLOCKED).build();

    when(restraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(eq(planExecutionId)))
        .thenReturn(ImmutableList.of(activeRc, blockedRc));
    observer.onEnd(ambiance, Status.SUCCEEDED);

    ArgumentCaptor<ResourceRestraintInstance> instanceCaptor = ArgumentCaptor.forClass(ResourceRestraintInstance.class);

    verify(restraintInstanceService, times(1)).findAllActiveAndBlockedByReleaseEntityId(eq(planExecutionId));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testOnEndWithFeatureFlagEnabled() {
    String accountId = "test-account";
    String planExecutionId = "test-plan-execution";
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putSetupAbstractions("accountId", accountId).build();

    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .uuid("test-instance")
                                             .resourceRestraintId("constraint-1")
                                             .resourceUnit("unit-1")
                                             .build();

    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2)).thenReturn(true);
    when(restraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(planExecutionId))
        .thenReturn(ImmutableList.of(instance));
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    observer.onEnd(ambiance, Status.SUCCEEDED);

    verify(restraintInstanceService).findAllActiveAndBlockedByReleaseEntityId(planExecutionId);
    verify(restraintInstanceService).finishInstance(instance.getUuid(), instance.getResourceUnit());
    verify(restraintInstanceService).updateBlockedConstraints(instance);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testOnEndWithFeatureFlagDisabled() {
    String accountId = "test-account";
    String planExecutionId = "test-plan-execution";
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(planExecutionId).putSetupAbstractions("accountId", accountId).build();

    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2)).thenReturn(false);
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    observer.onEnd(ambiance, Status.SUCCEEDED);

    verify(restraintInstanceService).findAllActiveAndBlockedByReleaseEntityId(planExecutionId);
    verify(restraintInstanceService, never()).updateBlockedConstraints(any(ResourceRestraintInstance.class));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testNodeStatusUpdateWithFeatureFlag() {
    String accountId = "test-account";
    String nodeExecutionId = "test-node-execution";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("test-plan-execution")
                            .putSetupAbstractions("accountId", accountId)
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                      .mode(ExecutionMode.SYNC)
                                      .build();

    ResourceRestraintInstance instance =
        ResourceRestraintInstance.builder().resourceRestraintId("constraint-1").resourceUnit("unit-1").build();

    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2)).thenReturn(true);
    when(restraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(nodeExecutionId))
        .thenReturn(ImmutableList.of(instance));
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    observer.onNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());

    verify(restraintInstanceService).findAllActiveAndBlockedByReleaseEntityId(nodeExecutionId);
    verify(restraintInstanceService).finishInstance(instance.getUuid(), instance.getResourceUnit());
    verify(restraintInstanceService).updateBlockedConstraints(instance);
  }
}
