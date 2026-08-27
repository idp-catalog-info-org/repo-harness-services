/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CI)
public class CISpecStepTest extends CIExecutionTestBase {
  @InjectMocks private CISpecStep ciSpecStep;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");

    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder().setStepType(CISpecStep.STEP_TYPE).build())
                   .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").build())
                   .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(ciSpecStep.getStepParametersClass()).isEqualTo(IntegrationStageStepParametersPMS.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainChild() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNodeId123").build();

    ChildExecutableResponse response =
        ciSpecStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo("childNodeId123");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseSuccess() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNodeId123").build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(
        "childNode", StepResponseNotifyData.builder().nodeUuid("childNodeId123").status(Status.SUCCEEDED).build());

    StepResponse stepResponse = ciSpecStep.handleChildResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseFailure() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNodeId123").build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("childNode",
        StepResponseNotifyData.builder()
            .nodeUuid("childNodeId123")
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage("Child step failed").build())
            .build());

    StepResponse stepResponse = ciSpecStep.handleChildResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isNotNull();
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Child step failed");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithEmptyMap() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNodeId123").build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();

    StepResponse stepResponse = ciSpecStep.handleChildResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}
