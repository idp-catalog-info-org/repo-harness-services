/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.secondary;

import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.engine.observers.PreStepCheckObserver;
import io.harness.execution.NodeExecution;
import io.harness.observer.Subject;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class PreStepCheckFacilitatorTest extends OrchestrationTestBase {
  @Inject private PreStepCheckFacilitator preStepCheckFacilitator;

  private NodeExecution nodeExecution;
  Ambiance ambiance;
  @Mock private Subject<PreStepCheckObserver> preStepCheckObserverSubject;

  @Before
  public void setUp() {
    nodeExecution = NodeExecution.builder()
                        .mode(ExecutionMode.PRE_STEP_CHECK)
                        .resolvedParams(new PmsStepParameters())
                        .identifier("Identifier")
                        .name("name")
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecId").build())
                        .build();

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putFeatureFlagToValueMap(OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT, true)
                                    .build())
                   .build();
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testFacilitatorSuccess() {
    doNothing().when(preStepCheckObserverSubject).fireInform(any(), any(), any());
    preStepCheckFacilitator.facilitateWithMetadata(ambiance, null,
        FacilitatorMetadata.builder()
            .resolvedParams(nodeExecution.getResolvedParams())
            .mode(nodeExecution.getMode())
            .name(nodeExecution.getName())
            .build());
  }
}