/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.manual.callback;

import static io.harness.beans.steps.StepSpecTypeConstants.INTEGRATIONSTAGESTEPPMS_FACILITATOR;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.engine.pms.execution.manual.beans.ManualExecutionAction.MARK_AS_FAIL;
import static io.harness.engine.pms.execution.manual.beans.ManualExecutionAction.MARK_AS_RESUME;
import static io.harness.engine.pms.execution.manual.callback.ManualExecutionCallback.STEP_DETAILS_NAME;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.steps.StepSpecTypeConstants.RESOURCE_RESTRAINT_FACILITATOR_TYPE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.facilitator.CoreFacilitator;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionDetailsInfo;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionResponseData;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.rule.Owner;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ManualExecutionCallbackTest extends CategoryTest {
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Mock private FacilitationHelper facilitationHelper;
  @Mock private CoreFacilitator coreFacilitator;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEventSender eventSender;

  private static final String NODE_EXECUTION_ID = generateUuid();
  private static final String NOTIFY_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build();
  private static final FacilitatorObtainment facilitatorObtainment =
      FacilitatorObtainment.newBuilder()
          .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
          .setParameters(ByteString.copyFromUtf8("test-params"))
          .build();
  private static final FacilitatorObtainment facilitatorObtainmentForManualExecution =
      FacilitatorObtainment.newBuilder()
          .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.MANUAL_EXECUTION).build())
          .build();
  private static final ManualExecutionCallback callback =
      ManualExecutionCallback.builder()
          .ambianceBytes(ambiance.toByteArray())
          .notifyId(NOTIFY_ID)
          .nodeExecutionId(NODE_EXECUTION_ID)
          .primaryFacilitatorObtainmentBytes(facilitatorObtainment.toByteArray())
          .startTs(100L)
          .build();
  private static final ManualExecutionResponseData successResponseData =
      ManualExecutionResponseData.builder().action(MARK_AS_RESUME).build();
  private static final Map<String, ResponseData> response = Map.of("key", successResponseData);
  private static final FacilitatorResponseProto facilitatorResponse =
      FacilitatorResponseProto.newBuilder().setIsSuccessful(true).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    injectMocksInCallback(callback);
  }

  private void injectMocksInCallback(ManualExecutionCallback callback) {
    Reflect.on(callback).set("sdkNodeExecutionService", sdkNodeExecutionService);
    Reflect.on(callback).set("sdkGraphVisualizationDataService", sdkGraphVisualizationDataService);
    Reflect.on(callback).set("facilitationHelper", facilitationHelper);
    Reflect.on(callback).set("sdkGraphVisualizationDataService", sdkGraphVisualizationDataService);
    Reflect.on(callback).set("nodeExecutionService", nodeExecutionService);
    Reflect.on(callback).set("eventSender", eventSender);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyWithMarkAsFailAction() {
    ManualExecutionResponseData responseData = ManualExecutionResponseData.builder().action(MARK_AS_FAIL).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("key", responseData);

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notify(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo("User marked the manual execution to fail");

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyWithMarkAsSuccessAction() {
    when(facilitationHelper.getFacilitatorFromType(
             FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build()))
        .thenReturn(coreFacilitator);
    when(coreFacilitator.facilitate(any(Ambiance.class), any(byte[].class))).thenReturn(facilitatorResponse);

    callback.notify(response);
    verify(facilitationHelper)
        .getFacilitatorFromType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build());
    verify(coreFacilitator).facilitate(any(Ambiance.class), any(byte[].class));

    FacilitatorResponseProto expectedResponse = facilitatorResponse.toBuilder().setStatus(Status.RUNNING).build();
    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), eq(expectedResponse));

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyWithEmptyResponse() {
    Map<String, ResponseData> response = new HashMap<>();

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notify(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo("Response can not be empty to resolve callback");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyWithNonManualExecutionResponseData() {
    ResponseData responseData = new ResponseData() {};

    Map<String, ResponseData> response = new HashMap<>();
    response.put("key", responseData);

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notify(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData())
        .isEqualTo("Response data is not of type ManualExecutionResponseData");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyWithInvalidAmbianceBytes() {
    ManualExecutionCallback callback = ManualExecutionCallback.builder()
                                           .ambianceBytes("invalid-bytes".getBytes())
                                           .notifyId(NOTIFY_ID)
                                           .nodeExecutionId(NODE_EXECUTION_ID)
                                           .primaryFacilitatorObtainmentBytes("invalid-bytes".getBytes())
                                           .build();

    Reflect.on(callback).set("sdkNodeExecutionService", sdkNodeExecutionService);
    Reflect.on(callback).set("facilitationHelper", facilitationHelper);

    callback.notify(response);

    // Should not call sdkNodeExecutionService due to InvalidProtocolBufferException
    verify(sdkNodeExecutionService, never()).handleFacilitationResponse(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyTimeout() {
    Map<String, ResponseData> response = new HashMap<>();

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notifyTimeout(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo("Timed out while waiting for user input");

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyErrorWithDefaultMessage() {
    Map<String, ResponseData> response = new HashMap<>();

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notifyError(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo("Failed to process Manual Execution callback");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyErrorWithErrorResponseData() {
    String errorMessage = "Error from ErrorResponseData";
    ErrorResponseData errorResponseData = ErrorNotifyResponseData.builder().errorMessage(errorMessage).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("key", errorResponseData);

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notifyError(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo(errorMessage);

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyErrorWithInvalidAmbianceBytes() {
    ManualExecutionCallback callback = ManualExecutionCallback.builder()
                                           .ambianceBytes("invalid-bytes".getBytes())
                                           .notifyId(NOTIFY_ID)
                                           .nodeExecutionId(NODE_EXECUTION_ID)
                                           .build();

    Reflect.on(callback).set("sdkNodeExecutionService", sdkNodeExecutionService);
    Reflect.on(callback).set("sdkGraphVisualizationDataService", sdkGraphVisualizationDataService);

    Map<String, ResponseData> response = new HashMap<>();

    callback.notifyError(response);

    // Should not call sdkNodeExecutionService due to InvalidProtocolBufferException
    verify(sdkNodeExecutionService, never()).handleFacilitationResponse(any(), any(), any());
    verify(sdkGraphVisualizationDataService, never()).publishStepDetailInformation(any(), any(), any());

    callback.notifyTimeout(response);
    verify(sdkNodeExecutionService, never()).handleFacilitationResponse(any(), any(), any());
    verify(sdkGraphVisualizationDataService, never()).publishStepDetailInformation(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNotifyErrorWithEmptyResponse() {
    Map<String, ResponseData> response = new HashMap<>();

    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);

    callback.notifyError(response);

    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), responseCaptor.capture());

    FacilitatorResponseProto capturedResponse = responseCaptor.getValue();
    assertThat(capturedResponse.getIsSuccessful()).isFalse();
    assertThat(capturedResponse.getPassThroughData()).isEqualTo("Failed to process Manual Execution callback");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleSuccessResponse_SendEventForCustomFacilitator() {
    FacilitatorObtainment obtainment =
        FacilitatorObtainment.newBuilder()
            .setType(FacilitatorType.newBuilder().setType(INTEGRATIONSTAGESTEPPMS_FACILITATOR).build())
            .build();
    FacilitatorObtainment obtainment2 =
        FacilitatorObtainment.newBuilder()
            .setType(FacilitatorType.newBuilder().setType(RESOURCE_RESTRAINT_FACILITATOR_TYPE).build())
            .build();
    FacilitatorEvent event = FacilitatorEvent.newBuilder()
                                 .setNodeExecutionId(NODE_EXECUTION_ID)
                                 .setAmbiance(ambiance)
                                 .addFacilitatorObtainments(obtainment)
                                 .addFacilitatorObtainments(obtainment2)
                                 .build();
    FacilitatorEvent eventWithManualExecution = FacilitatorEvent.newBuilder()
                                                    .setNodeExecutionId(NODE_EXECUTION_ID)
                                                    .setAmbiance(ambiance)
                                                    .addFacilitatorObtainments(obtainment)
                                                    .addFacilitatorObtainments(obtainment2)
                                                    .addFacilitatorObtainments(facilitatorObtainmentForManualExecution)
                                                    .build();
    ManualExecutionCallback callbackWithFacilitatorEvent =
        ManualExecutionCallback.builder()
            .ambianceBytes(ambiance.toByteArray())
            .notifyId(NOTIFY_ID)
            .nodeExecutionId(NODE_EXECUTION_ID)
            .facilitatorEventBytes(eventWithManualExecution.toByteArray())
            .startTs(100L)
            .build();
    injectMocksInCallback(callbackWithFacilitatorEvent);

    NodeExecution ne = Mockito.mock(NodeExecution.class);
    when(ne.getModule()).thenReturn("ci");
    when(nodeExecutionService.getWithFieldsIncluded(eq(NODE_EXECUTION_ID), eq(NodeProjectionUtils.forFacilitation)))
        .thenReturn(ne);

    callbackWithFacilitatorEvent.notify(response);
    verify(facilitationHelper, never()).getFacilitatorFromType(any());
    verify(coreFacilitator, never()).facilitate(any(), any());
    verify(sdkNodeExecutionService, never()).handleFacilitationResponse(any(), any(), any());
    verify(nodeExecutionService, times(1))
        .getWithFieldsIncluded(eq(NODE_EXECUTION_ID), eq(NodeProjectionUtils.forFacilitation));
    verify(eventSender, times(1))
        .sendEvent(eq(ambiance), eq(event), eq(PmsEventCategory.FACILITATOR_EVENT), eq("ci"), eq(true), eq(true));
    verify(facilitationHelper, never()).getFacilitatorFromType(any());

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleSuccessResponse_PrimaryFacilitator() {
    FacilitatorEvent event = FacilitatorEvent.newBuilder()
                                 .setNodeExecutionId(NODE_EXECUTION_ID)
                                 .setAmbiance(ambiance)
                                 .addFacilitatorObtainments(facilitatorObtainment)
                                 .addFacilitatorObtainments(facilitatorObtainmentForManualExecution)
                                 .build();
    ManualExecutionCallback callbackWithFacilitatorEvent = ManualExecutionCallback.builder()
                                                               .ambianceBytes(ambiance.toByteArray())
                                                               .notifyId(NOTIFY_ID)
                                                               .nodeExecutionId(NODE_EXECUTION_ID)
                                                               .facilitatorEventBytes(event.toByteArray())
                                                               .startTs(100L)
                                                               .build();
    injectMocksInCallback(callbackWithFacilitatorEvent);

    when(coreFacilitator.facilitate(any(Ambiance.class), any(byte[].class))).thenReturn(facilitatorResponse);
    when(facilitationHelper.getFacilitatorFromType(
             FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build()))
        .thenReturn(coreFacilitator);
    callbackWithFacilitatorEvent.notify(response);
    verify(facilitationHelper)
        .getFacilitatorFromType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build());
    verify(coreFacilitator).facilitate(any(Ambiance.class), any(byte[].class));

    FacilitatorResponseProto expectedResponse = facilitatorResponse.toBuilder().setStatus(Status.RUNNING).build();
    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(any(Ambiance.class), eq(NOTIFY_ID), eq(expectedResponse));
    verify(nodeExecutionService, times(0)).getWithFieldsIncluded(any(), any());

    ArgumentCaptor<ManualExecutionDetailsInfo> detailsInfoCaptor =
        ArgumentCaptor.forClass(ManualExecutionDetailsInfo.class);
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(eq(ambiance), detailsInfoCaptor.capture(), eq(STEP_DETAILS_NAME));
    ManualExecutionDetailsInfo manualExecutionDetailsInfo = detailsInfoCaptor.getValue();
    assertThat(manualExecutionDetailsInfo.getStartTs()).isEqualTo(100L);
    assertThat(manualExecutionDetailsInfo.getEndTs()).isGreaterThan(0L);
  }
}
