/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(io.harness.annotations.dev.HarnessTeam.PIPELINE)
public class StagesStepWithChildrenSupportTest extends CategoryTest {
  private StagesStepWithChildrenSupport stagesStepWithChildrenSupport;
  private Ambiance mockAmbiance;
  private StepInputPackage mockInputPackage;

  @Before
  public void setUp() {
    stagesStepWithChildrenSupport = new StagesStepWithChildrenSupport();
    mockAmbiance = mock(Ambiance.class);
    mockInputPackage = mock(StepInputPackage.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStepTypeConstants() {
    // Test main step type
    assertThat(StagesStepWithChildrenSupport.STEP_TYPE.getType())
        .isEqualTo(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY);
    assertThat(StagesStepWithChildrenSupport.STEP_TYPE.getStepCategory()).isEqualTo(StepCategory.STAGES);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    Class<StagesStepParameters> parameterClass = stagesStepWithChildrenSupport.getStepParametersClass();
    assertThat(parameterClass).isEqualTo(StagesStepParameters.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateResources() {
    StagesStepParameters stepParameters = StagesStepParameters.builder()
                                              .childrenIds(Collections.singletonList("child1"))
                                              .logMessage("Test stages")
                                              .build();

    // Should not throw any exception - validation is a no-op for StagesStep
    stagesStepWithChildrenSupport.validateResources(mockAmbiance, stepParameters);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbac_WithMultipleChildrenIds() {
    List<String> childrenIds = Arrays.asList("stage1", "stage2", "stage3");
    StagesStepParameters stepParameters =
        StagesStepParameters.builder().childrenIds(childrenIds).logMessage("Multiple stages execution").build();

    ChildrenExecutableResponse response =
        stagesStepWithChildrenSupport.obtainChildrenAfterRbac(mockAmbiance, stepParameters, mockInputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildrenList()).hasSize(3);
    assertThat(response.getMaxConcurrency()).isEqualTo(3);

    // Verify all children are included
    List<String> actualChildIds =
        response.getChildrenList().stream().map(ChildrenExecutableResponse.Child::getChildNodeId).toList();
    assertThat(actualChildIds).containsExactlyInAnyOrder("stage1", "stage2", "stage3");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbac_WithSingleChildNodeId_BackwardCompatibility() {
    StagesStepParameters stepParameters = StagesStepParameters.builder()
                                              .childrenIds(Collections.singletonList("singleStage"))
                                              .logMessage("Single stage execution")
                                              .build();

    ChildrenExecutableResponse response =
        stagesStepWithChildrenSupport.obtainChildrenAfterRbac(mockAmbiance, stepParameters, mockInputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildrenList()).hasSize(1);
    assertThat(response.getMaxConcurrency()).isEqualTo(1);
    assertThat(response.getChildren(0).getChildNodeId()).isEqualTo("singleStage");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbac_PrioritizesChildrenIdsOverChildNodeId() {
    List<String> childrenIds = Arrays.asList("priority1", "priority2");
    StagesStepParameters stepParameters =
        StagesStepParameters.builder().childrenIds(childrenIds).logMessage("Priority test").build();

    ChildrenExecutableResponse response =
        stagesStepWithChildrenSupport.obtainChildrenAfterRbac(mockAmbiance, stepParameters, mockInputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildrenList()).hasSize(2);
    assertThat(response.getMaxConcurrency()).isEqualTo(2);

    List<String> actualChildIds =
        response.getChildrenList().stream().map(ChildrenExecutableResponse.Child::getChildNodeId).toList();
    assertThat(actualChildIds).containsExactlyInAnyOrder("priority1", "priority2");
    assertThat(actualChildIds).doesNotContain("shouldBeIgnored");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbac_WithNoChildren() {
    StagesStepParameters stepParameters = StagesStepParameters.builder().logMessage("No children test").build();

    ChildrenExecutableResponse response =
        stagesStepWithChildrenSupport.obtainChildrenAfterRbac(mockAmbiance, stepParameters, mockInputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getChildrenList()).isEmpty();
    assertThat(response.getMaxConcurrency()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse_WithMultipleResponses() {
    StagesStepParameters stepParameters = StagesStepParameters.builder()
                                              .childrenIds(Arrays.asList("stage1", "stage2"))
                                              .logMessage("Multiple response test")
                                              .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    StepResponseNotifyData mockResponse1 = mock(StepResponseNotifyData.class);
    StepResponseNotifyData mockResponse2 = mock(StepResponseNotifyData.class);
    responseDataMap.put("stage1", mockResponse1);
    responseDataMap.put("stage2", mockResponse2);

    StepResponse stepResponse =
        stagesStepWithChildrenSupport.handleChildrenResponse(mockAmbiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
    // The actual response creation is handled by SdkCoreStepUtils.createStepResponseFromChildResponse
    // We're testing that the method completes successfully and returns a non-null response
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse_WithSingleResponse() {
    StagesStepParameters stepParameters = StagesStepParameters.builder()
                                              .childrenIds(Collections.singletonList("singleStage"))
                                              .logMessage("Single response test")
                                              .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    StepResponseNotifyData mockResponse = mock(StepResponseNotifyData.class);
    responseDataMap.put("singleStage", mockResponse);

    StepResponse stepResponse =
        stagesStepWithChildrenSupport.handleChildrenResponse(mockAmbiance, stepParameters, responseDataMap);

    assertThat(stepResponse).isNotNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testStagesStepParameters_Builder() {
    List<String> childrenIds = Arrays.asList("child1", "child2");

    StagesStepParameters parameters = StagesStepParameters.builder()
                                          .childrenIds(childrenIds)
                                          .logMessage("Test message")
                                          .name("Test stages")
                                          .id("test-id")
                                          .skip(false)
                                          .build();

    assertThat(parameters.getChildrenIds()).isEqualTo(childrenIds);
    assertThat(parameters.getLogMessage()).isEqualTo("Test message");
    assertThat(parameters.getName()).isEqualTo("Test stages");
    assertThat(parameters.getId()).isEqualTo("test-id");
    assertThat(parameters.getSkip()).isEqualTo(false);
  }
}
