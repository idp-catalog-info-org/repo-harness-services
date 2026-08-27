/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.manual.facilitator;

import static io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.execution.OrchestrationFacilitatorType.MANUAL_EXECUTION;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.execution.manual.callback.ManualExecutionCallback;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.AutoRunModeConfig;
import io.harness.pms.contracts.plan.ManualRunModeConfig;
import io.harness.pms.contracts.plan.RunModeConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.facilitator.ManualExecutionFacilitatorParams;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Owner(developers = RISHABH)
@Category(UnitTests.class)
@RunWith(MockitoJUnitRunner.class)
public class ManualExecutionFacilitatorTest extends OrchestrationTestBase {
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Inject private KryoSerializer kryoSerializer;
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;

  private ManualExecutionFacilitator manualExecutionFacilitator;
  private static final String publisherName = "test-publisher";
  private static final String notifyId = "notify-123";

  private Ambiance ambiance;
  private FacilitatorEvent facilitatorEvent;
  private byte[] parameters;
  private byte[] parametersWithoutTimeout;
  private byte[] parametersWithExprTimeout;
  private byte[] parametersWithInvalidTimeout;
  private byte[] autoParameters;
  private StepInputPackage inputPackage;

  @Before
  public void setUp() {
    manualExecutionFacilitator = new ManualExecutionFacilitator();

    // Inject dependencies using Reflect.on() pattern
    Reflect.on(manualExecutionFacilitator).set("waitNotifyEngine", waitNotifyEngine);
    Reflect.on(manualExecutionFacilitator).set("publisherName", publisherName);
    Reflect.on(manualExecutionFacilitator).set("sdkNodeExecutionService", sdkNodeExecutionService);
    Reflect.on(manualExecutionFacilitator).set("kryoSerializer", kryoSerializer);
    Reflect.on(manualExecutionFacilitator).set("pmsEngineExpressionService", pmsEngineExpressionService);

    // Setup test data
    ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId("planExecId")
            .addLevels(
                Level.newBuilder()
                    .setRuntimeId("runtimeId")
                    .setSetupId("setupId")
                    .setStepType(
                        StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build())
                    .build())
            .build();

    FacilitatorObtainment facilitatorObtainment =
        FacilitatorObtainment.newBuilder()
            .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
            .setParameters(ByteString.copyFromUtf8("test-params"))
            .build();
    FacilitatorObtainment facilitatorObtainmentForManualExecution =
        FacilitatorObtainment.newBuilder()
            .setType(FacilitatorType.newBuilder().setType(MANUAL_EXECUTION).build())
            .build();

    facilitatorEvent = FacilitatorEvent.newBuilder()
                           .setNodeExecutionId("runtimeId")
                           .setAmbiance(ambiance)
                           .addFacilitatorObtainments(facilitatorObtainment)
                           .addFacilitatorObtainments(facilitatorObtainmentForManualExecution)
                           .build();

    parameters = kryoSerializer.asBytes(
        ManualExecutionFacilitatorParams.builder()
            .runModeConfig(RunModeConfig.newBuilder()
                               .setManual(ManualRunModeConfig.newBuilder().setType("MANUAL").setTimeout("5m").build())
                               .build())
            .build());
    parametersWithExprTimeout =
        kryoSerializer.asBytes(ManualExecutionFacilitatorParams.builder()
                                   .runModeConfig(RunModeConfig.newBuilder()
                                                      .setManual(ManualRunModeConfig.newBuilder()
                                                                     .setType("MANUAL")
                                                                     .setTimeout("<+stage.variables.timeout>")
                                                                     .build())
                                                      .build())
                                   .build());
    parametersWithInvalidTimeout = kryoSerializer.asBytes(
        ManualExecutionFacilitatorParams.builder()
            .runModeConfig(RunModeConfig.newBuilder()
                               .setManual(ManualRunModeConfig.newBuilder().setType("MANUAL").setTimeout("abc").build())
                               .build())
            .build());
    parametersWithoutTimeout = kryoSerializer.asBytes(
        ManualExecutionFacilitatorParams.builder()
            .runModeConfig(RunModeConfig.newBuilder()
                               .setManual(ManualRunModeConfig.newBuilder().setType("MANUAL").build())
                               .build())
            .build());
    autoParameters = kryoSerializer.asBytes(
        ManualExecutionFacilitatorParams.builder()
            .runModeConfig(
                RunModeConfig.newBuilder().setAuto(AutoRunModeConfig.newBuilder().setType("AUTO").build()).build())
            .build());
    inputPackage = StepInputPackage.builder().build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorSuccess() {
    when(pmsEngineExpressionService.resolve(eq(ambiance), eq("5m"), eq(RETURN_NULL_IF_UNRESOLVED))).thenReturn("5m");
    manualExecutionFacilitator.runCustomFacilitator(notifyId, ambiance, parameters, inputPackage, facilitatorEvent);
    ArgumentCaptor<ManualExecutionCallback> callbackCaptor = ArgumentCaptor.forClass(ManualExecutionCallback.class);
    ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);

    verify(waitNotifyEngine, times(1))
        .waitForAllOn(
            eq(publisherName), callbackCaptor.capture(), any(), listCaptor.capture(), durationCaptor.capture(), any());

    // Verify callback properties
    ManualExecutionCallback capturedCallback = callbackCaptor.getValue();
    assertThat(capturedCallback.getAmbianceBytes()).isEqualTo(ambiance.toByteArray());
    assertThat(capturedCallback.getNodeExecutionId()).isEqualTo("runtimeId");
    assertThat(capturedCallback.getNotifyId()).isEqualTo(notifyId);
    assertThat(capturedCallback.getFacilitatorEventBytes()).isEqualTo(facilitatorEvent.toByteArray());
    assertThat(capturedCallback.getPrimaryFacilitatorObtainmentBytes()).isNull();
    assertThat(listCaptor.getValue()).isNotEmpty();

    // Verify duration is 5 min
    assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));

    // Verify nodeExecutionService.updateStatusWithOps was called
    ArgumentCaptor<ExecutableResponse> executableResponseCaptor = ArgumentCaptor.forClass(ExecutableResponse.class);

    verify(sdkNodeExecutionService, times(1)).addExecutableResponse(eq(ambiance), executableResponseCaptor.capture());

    ExecutableResponse gotExecutableResponse = executableResponseCaptor.getValue();
    assertThat(gotExecutableResponse.hasFacilitator()).isTrue();
    assertThat(gotExecutableResponse.getFacilitator().getType()).isEqualTo(MANUAL_EXECUTION);
    assertThat(gotExecutableResponse.getFacilitator().getStatus()).isEqualTo(INTERVENTION_WAITING);
    assertThat(gotExecutableResponse.getFacilitator().getStartTs()).isGreaterThan(0L);
    assertThat(gotExecutableResponse.getFacilitator().getTimeoutInSeconds()).isEqualTo(300L);
    assertThat(gotExecutableResponse.getFacilitator().getCallbackIds(0)).isEqualTo(listCaptor.getValue().get(0));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorSuccessWithInvalidTimeout() {
    when(pmsEngineExpressionService.resolve(eq(ambiance), eq(""), eq(RETURN_NULL_IF_UNRESOLVED))).thenReturn("");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithoutTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid input for duration of manual stage execution, Duration should be greater than 0");

    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any(), any(), any(), any());
    verify(sdkNodeExecutionService, times(0)).addExecutableResponse(any(), any());
    when(pmsEngineExpressionService.resolve(
             eq(ambiance), eq("<+stage.variables.timeout>"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithExprTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid input for duration of manual stage execution, Duration should be greater than 0");

    when(pmsEngineExpressionService.resolve(
             eq(ambiance), eq("<+stage.variables.timeout>"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("null");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithExprTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid input for duration of manual stage execution, Duration should be greater than 0");

    when(pmsEngineExpressionService.resolve(
             eq(ambiance), eq("<+stage.variables.timeout>"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("abc");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithExprTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Given format not supported for timeout: abc:");

    when(pmsEngineExpressionService.resolve(
             eq(ambiance), eq("<+stage.variables.timeout>"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("0m");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithExprTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid input for duration of manual stage execution, Duration should be greater than 0");

    when(pmsEngineExpressionService.resolve(eq(ambiance), eq("abc"), eq(RETURN_NULL_IF_UNRESOLVED))).thenReturn("abcd");
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               notifyId, ambiance, parametersWithInvalidTimeout, inputPackage, facilitatorEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Given format not supported for timeout: abcd:");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorFalseCases() {
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               null, ambiance, parameters, inputPackage, facilitatorEvent))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Manual Execution Facilitator notifyId cannot be empty");

    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               "notify-123", ambiance, autoParameters, inputPackage, facilitatorEvent))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Currently Manual Execution Facilitator is only supported for Manual Run Mode");

    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               "notify-123", ambiance, "".getBytes(), inputPackage, facilitatorEvent))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Manual Execution Facilitator Params cannot be empty");

    assertThatThrownBy(()
                           -> manualExecutionFacilitator.runCustomFacilitator(
                               "notify-123", ambiance, null, inputPackage, facilitatorEvent))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Manual Execution Facilitator Params cannot be empty");
    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorWithEmptyAmbiance() {
    Ambiance emptyAmbiance = Ambiance.newBuilder().build();
    when(pmsEngineExpressionService.resolve(eq(emptyAmbiance), eq("5m"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("5m");
    manualExecutionFacilitator.runCustomFacilitator(
        notifyId, emptyAmbiance, parameters, inputPackage, facilitatorEvent);

    // Verify waitNotifyEngine was called
    ArgumentCaptor<ManualExecutionCallback> callbackCaptor = ArgumentCaptor.forClass(ManualExecutionCallback.class);
    verify(waitNotifyEngine, times(1))
        .waitForAllOn(eq(publisherName), callbackCaptor.capture(), any(), any(), any(Duration.class), any());

    ManualExecutionCallback capturedCallback = callbackCaptor.getValue();
    assertThat(capturedCallback.getAmbianceBytes()).isEqualTo(emptyAmbiance.toByteArray());
    assertThat(capturedCallback.getNodeExecutionId()).isNull(); // Empty ambiance should result in null runtime ID
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorVerifyCallbackUuidGeneration() {
    when(pmsEngineExpressionService.resolve(
             eq(ambiance), eq("<+stage.variables.timeout>"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("50m");
    manualExecutionFacilitator.runCustomFacilitator(
        notifyId, ambiance, parametersWithExprTimeout, inputPackage, facilitatorEvent);

    // Verify waitNotifyEngine was called with a list containing one UUID
    ArgumentCaptor<List<String>> uuidListCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(waitNotifyEngine, times(1))
        .waitForAllOn(eq(publisherName), any(ManualExecutionCallback.class), any(), uuidListCaptor.capture(),
            durationCaptor.capture(), any());
    assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofMinutes(50));

    List<String> capturedUuidList = uuidListCaptor.getValue();
    assertThat(capturedUuidList).hasSize(1);
    assertThat(capturedUuidList.get(0)).isNotNull();
    assertThat(capturedUuidList.get(0)).isNotEmpty();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFacilitateThrowsUnsupportedOperationException() {
    assertThatThrownBy(()
                           -> manualExecutionFacilitator.facilitate(
                               ambiance, mock(StageElementParameters.class), parameters, inputPackage))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("This is a Custom facilitator, so facilitate isn't supported");
    assertThatThrownBy(() -> manualExecutionFacilitator.facilitate(null, null, null, null))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("This is a Custom facilitator, so facilitate isn't supported");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRunCustomFacilitatorMultipleLevelsInAmbiance() {
    // Create ambiance with multiple levels
    Ambiance multiLevelAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId("planExecId")
            .addLevels(Level.newBuilder().setRuntimeId("level1-runtime").setSetupId("level1-setup").build())
            .addLevels(Level.newBuilder().setRuntimeId("level2-runtime").setSetupId("level2-setup").build())
            .build();
    when(pmsEngineExpressionService.resolve(eq(multiLevelAmbiance), eq("5m"), eq(RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("5m");

    manualExecutionFacilitator.runCustomFacilitator(
        notifyId, multiLevelAmbiance, parameters, inputPackage, facilitatorEvent);
    ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);

    // Verify callback uses the current (last) runtime ID
    ArgumentCaptor<ManualExecutionCallback> callbackCaptor = ArgumentCaptor.forClass(ManualExecutionCallback.class);
    verify(waitNotifyEngine, times(1))
        .waitForAllOn(eq(publisherName), callbackCaptor.capture(), any(), any(), durationCaptor.capture(), any());
    assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));

    ManualExecutionCallback capturedCallback = callbackCaptor.getValue();
    assertThat(capturedCallback.getNodeExecutionId()).isEqualTo("level2-runtime");
  }
}
