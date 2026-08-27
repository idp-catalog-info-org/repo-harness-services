/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.eraro.ErrorCode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.FacilitatorResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class FacilitateResponseRequestProcessorTest extends CategoryTest {
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @InjectMocks private FacilitateResponseRequestProcessor facilitateResponseRequestHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAdviseEvent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    // Facilitation success.
    FacilitatorResponseRequest request = FacilitatorResponseRequest.newBuilder()
                                             .setFacilitatorResponse(FacilitatorResponseProto.newBuilder()
                                                                         .setIsSuccessful(true)
                                                                         .setExecutionMode(ExecutionMode.TASK)
                                                                         .build())
                                             .build();
    SdkResponseEventProto sdkResponseEventInternal =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();
    facilitateResponseRequestHandler.handleEvent(sdkResponseEventInternal);
    // facilitation response is successful. So engine.processFacilitatorResponse will be invoked.
    verify(orchestrationEngine, times(1))
        .processFacilitatorResponse(sdkResponseEventInternal.getAmbiance(), request.getFacilitatorResponse());

    // Facilitation failed.
    request = FacilitatorResponseRequest.newBuilder()
                  .setFacilitatorResponse(FacilitatorResponseProto.newBuilder()
                                              .setIsSuccessful(false)
                                              .setPassThroughData("Error during the facilitation")
                                              .setExecutionMode(ExecutionMode.TASK)
                                              .build())
                  .build();
    sdkResponseEventInternal =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();
    facilitateResponseRequestHandler.handleEvent(sdkResponseEventInternal);
    ArgumentCaptor<StepResponseProto> argumentCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    // facilitation response is not successful. So engine.processStepResponse will be invoked with status=FAILED.
    verify(orchestrationEngine, times(1))
        .processStepResponse(eq(sdkResponseEventInternal.getAmbiance()), argumentCaptor.capture());
    StepResponseProto stepResponseProto = argumentCaptor.getValue();

    assertThat(stepResponseProto.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .setMessage(request.getFacilitatorResponse().getPassThroughData())
                                           .setCode(ErrorCode.GENERAL_ERROR.name())
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                           .build())
                       .setErrorMessage(request.getFacilitatorResponse().getPassThroughData())
                       .build());
    assertThat(stepResponseProto.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testHandleEvent_shouldProcessStatusUpdateIfStatusNotNullOrNoop() {
    String nodeExecutionId = "nodeExecutionId";
    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId(nodeExecutionId)
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    List<Level> levels = new ArrayList<>();
    levels.add(l2);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    FacilitatorResponseProto response =
        FacilitatorResponseProto.newBuilder().setStatus(Status.QUEUED_LICENSE_LIMIT_REACHED).build();

    FacilitatorResponseRequest request =
        FacilitatorResponseRequest.newBuilder().setFacilitatorResponse(response).build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();

    facilitateResponseRequestHandler.handleEvent(event);

    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(nodeExecutionId, Status.QUEUED_LICENSE_LIMIT_REACHED, null, EnumSet.noneOf(Status.class));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEvent_isSuccessfulWithRunningStatus() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("plan-exec-id").build();

    FacilitatorResponseProto response = FacilitatorResponseProto.newBuilder()
                                            .setIsSuccessful(true)
                                            .setStatus(Status.RUNNING)
                                            .setExecutionMode(ExecutionMode.CHILD)
                                            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setAmbiance(ambiance)
            .setFacilitatorResponseRequest(
                FacilitatorResponseRequest.newBuilder().setFacilitatorResponse(response).build())
            .build();

    facilitateResponseRequestHandler.handleEvent(event);

    verify(orchestrationEngine).processFacilitatorResponse(ambiance, response);
    verify(planExecutionService).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testHandleEvent_isSuccessfulWithNoOpStatus() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("plan-exec-id").build();

    FacilitatorResponseProto response = FacilitatorResponseProto.newBuilder()
                                            .setIsSuccessful(true)
                                            .setStatus(Status.NO_OP)
                                            .setExecutionMode(ExecutionMode.CHILD)
                                            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setAmbiance(ambiance)
            .setFacilitatorResponseRequest(
                FacilitatorResponseRequest.newBuilder().setFacilitatorResponse(response).build())
            .build();

    facilitateResponseRequestHandler.handleEvent(event);

    verify(orchestrationEngine).processFacilitatorResponse(ambiance, response);
    verify(planExecutionService, times(0)).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testHandleEvent_shouldNotUpdateStatusForNoOp() {
    String nodeExecutionId = "nodeExecutionId";
    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId(nodeExecutionId)
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    List<Level> levels = new ArrayList<>();
    levels.add(l2);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    FacilitatorResponseProto response =
        FacilitatorResponseProto.newBuilder().setStatus(Status.NO_OP).setIsSuccessful(true).build();

    FacilitatorResponseRequest request =
        FacilitatorResponseRequest.newBuilder().setFacilitatorResponse(response).build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();

    facilitateResponseRequestHandler.handleEvent(event);

    verify(nodeExecutionService, times(0))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.NO_OP), eq(null), eq(EnumSet.noneOf(Status.class)));
    verify(orchestrationEngine).processFacilitatorResponse(ambiance, response);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testHandleEvent_shouldNotEarlyReturnForRunningStatus() {
    String nodeExecutionId = "nodeExecutionId";
    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId(nodeExecutionId)
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    List<Level> levels = new ArrayList<>();
    levels.add(l2);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    FacilitatorResponseProto response =
        FacilitatorResponseProto.newBuilder().setStatus(Status.RUNNING).setIsSuccessful(true).build();

    FacilitatorResponseRequest request =
        FacilitatorResponseRequest.newBuilder().setFacilitatorResponse(response).build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();

    facilitateResponseRequestHandler.handleEvent(event);

    verify(orchestrationEngine).processFacilitatorResponse(ambiance, response);
    verify(planExecutionService).calculateAndUpdateRunningStatusForStageAndPlanUnderLock(ambiance);
  }
}
