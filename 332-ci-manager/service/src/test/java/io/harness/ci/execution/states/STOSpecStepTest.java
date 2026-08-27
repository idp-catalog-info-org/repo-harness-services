/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class STOSpecStepTest extends CategoryTest {
  private STOSpecStep stoSpecStep;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    stoSpecStep = new STOSpecStep();
    ambiance = Ambiance.newBuilder().build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testStepType() {
    StepType stepType = STOSpecStep.STEP_TYPE;
    assertThat(stepType.getType()).isEqualTo("STOSPECPMS");
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(stoSpecStep.getStepParametersClass()).isEqualTo(IntegrationStageStepParametersPMS.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainChild() {
    String childNodeId = "childNode123";
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID(childNodeId).build();
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    ChildExecutableResponse response = stoSpecStep.obtainChild(ambiance, stepParameters, inputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isEqualTo(childNodeId);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithSuccess() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNode123").build();
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();

    StepResponse stepResponse = stoSpecStep.handleChildResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleChildResponseWithFailure() {
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.builder().childNodeID("childNode123").build();
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.FAILED).build())
            .build();

    StepResponse stepResponse = stoSpecStep.handleChildResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }
}
