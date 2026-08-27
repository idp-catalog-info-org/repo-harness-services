/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceExecutionContextHelperTest extends CategoryTest {
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks AmbianceExecutionContextHelper ambianceExecutionContextHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSetAmbianceAndExecutionContextValuesBothFFEnabled() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setRuntimeId("id").build())
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .setPlanExecutionId("planExecutionId")
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                            .build();
    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder();
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("ACCOUNT_ID", FeatureName.PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION);
    ambianceExecutionContextHelper.setAmbianceAndExecutionContextValues(ambiance, nodeExecutionBuilder);
    NodeExecution nodeExecution = nodeExecutionBuilder.build();
    assertThat(nodeExecution.getExecutionContext()).isEqualTo(AmbianceUtils.getExecutionContextFromAmbiance(ambiance));
    assertThat(nodeExecution.getAmbiance())
        .isEqualTo(Ambiance.newBuilder()
                       .setPlanExecutionId("planExecutionId")
                       .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                       .build());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSetAmbianceAndExecutionContextValuesOnlyFirstFFEnabled() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setRuntimeId("id").build())
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .setPlanExecutionId("planExecutionId")
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                            .build();
    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder();
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled("ACCOUNT_ID", FeatureName.PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION);
    ambianceExecutionContextHelper.setAmbianceAndExecutionContextValues(ambiance, nodeExecutionBuilder);
    NodeExecution nodeExecution = nodeExecutionBuilder.build();
    assertThat(nodeExecution.getExecutionContext()).isEqualTo(AmbianceUtils.getExecutionContextFromAmbiance(ambiance));
    assertThat(nodeExecution.getAmbiance()).isEqualTo(ambiance);
  }
}