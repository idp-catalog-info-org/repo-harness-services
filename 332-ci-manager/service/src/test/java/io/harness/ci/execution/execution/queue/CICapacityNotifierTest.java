/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.queue;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.queue.CICapacityNotifier;
import io.harness.ci.execution.queue.CICapacityPollerUtils;
import io.harness.ci.execution.queue.ProcessMessageResponse;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.beans.ci.vm.CapacityReservation;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;

import com.google.protobuf.ByteString;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CICapacityNotifierTest extends CIExecutionTestBase {
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private CIExecutionRepository ciExecutionRepository;
  @Mock private IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private CICapacityPollerUtils executionPollerUtils;

  private CICapacityNotifier notifier;
  private String accountId;
  private String stageExecutionId;
  private Ambiance ambiance;
  private StepParameters stepParameters;
  private DequeueResponse dequeueResponse;
  private byte[] ambianceBytes;
  private byte[] stepParametersBytes;
  private byte[] dequeueResponseBytes;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    accountId = "testAccount123";
    stageExecutionId = "stageExec456";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setStageExecutionId(stageExecutionId).build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .type(Infrastructure.Type.HOSTED_VM)
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    dequeueResponse = DequeueResponse.builder().itemId("item123").payload("payload").build();

    // Convert to bytes
    ambianceBytes = ambiance.toByteArray();
    String stepParamsJson = RecastOrchestrationUtils.toJson(stepParameters);
    stepParametersBytes = ByteString.copyFromUtf8(stepParamsJson).toByteArray();
    String dequeueResponseJson = RecastOrchestrationUtils.toJson(dequeueResponse);
    dequeueResponseBytes = ByteString.copyFromUtf8(dequeueResponseJson).toByteArray();

    // Create notifier with injected mocks
    notifier = CICapacityNotifier.builder()
                   .waitId("waitId123")
                   .ambianceBytes(ambianceBytes)
                   .stepParametersBytes(stepParametersBytes)
                   .dequeueResponseBytes(dequeueResponseBytes)
                   .build();

    // Inject dependencies using reflection
    setPrivateField(notifier, "ciExecutionServiceConfig", ciExecutionServiceConfig);
    setPrivateField(notifier, "serializedResponseDataHelper", serializedResponseDataHelper);
    setPrivateField(notifier, "ciExecutionRepository", ciExecutionRepository);
    setPrivateField(notifier, "stepPMSFacilitator", stepPMSFacilitator);
    setPrivateField(notifier, "queueExecutionUtils", queueExecutionUtils);
    setPrivateField(notifier, "executionPollerUtils", executionPollerUtils);
  }

  private void setPrivateField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }

  // ==================== Tests for notify - Success Cases ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenCapacityReservedSuccessfully_thenProceedWithExecution() {
    // Given
    VmTaskExecutionResponse vmResponse =
        VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .capacityReservation(CapacityReservation.builder().poolID("pool123").build())
            .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);
    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(vmResponse);
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(any(Ambiance.class), any(Infrastructure.class));
    verify(queueExecutionUtils)
        .publishGlobalQueueTimeMetrics(any(Ambiance.class), any(Infrastructure.class), eq("queue123"));
    verify(executionPollerUtils).processResults(any(ProcessMessageResponse.class), any(DequeueResponse.class));
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
    verify(ciExecutionRepository, never()).updateCapacityTaskInProgress(anyString(), anyString(), eq(false));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenCapacityReservedWithNullQueueId_thenDoNotPublishQueueTimeMetrics() {
    // Given
    VmTaskExecutionResponse vmResponse =
        VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .capacityReservation(CapacityReservation.builder().poolID("pool123").build())
            .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);
    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId(null).build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    notifier.notify(responseMap);

    // Then
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(any(Ambiance.class), any(Infrastructure.class));
    verify(queueExecutionUtils, never()).publishGlobalQueueTimeMetrics(any(), any(), anyString());
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenCapacityTaskFails_thenProceedWithExecution() {
    // Given
    VmTaskExecutionResponse vmResponse =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);
    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue456").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(vmResponse);
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(any(Ambiance.class), any(Infrastructure.class));
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
    verify(ciExecutionRepository, never()).updateCapacityTaskInProgress(anyString(), anyString(), eq(false));
  }

  // ==================== Tests for notify - Retry Cases ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenCapacityNotAvailable_thenQueueForRetry() {
    // Given
    VmTaskExecutionResponse vmResponse = VmTaskExecutionResponse.builder()
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .capacityReservation(null) // No capacity reserved
                                             .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(vmResponse);
    verify(executionPollerUtils).processResults(any(ProcessMessageResponse.class), any(DequeueResponse.class));
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciExecutionRepository).updateCapacityTaskInProgress(accountId, stageExecutionId, false);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenCapacityReservationHasEmptyPoolId_thenQueueForRetry() {
    // Given
    VmTaskExecutionResponse vmResponse = VmTaskExecutionResponse.builder()
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .capacityReservation(CapacityReservation.builder()
                                                                      .poolID("") // Empty pool ID
                                                                      .build())
                                             .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(vmResponse);
    verify(executionPollerUtils).processResults(any(ProcessMessageResponse.class), any(DequeueResponse.class));
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciExecutionRepository).updateCapacityTaskInProgress(accountId, stageExecutionId, false);
  }

  // ==================== Tests for notify - Error Cases ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenNoResponseReceived_thenDoNothing() {
    // Given
    Map<String, Supplier<ResponseData>> emptyResponseMap = new HashMap<>();

    // When
    notifier.notify(emptyResponseMap);

    // Then
    verify(serializedResponseDataHelper, never()).deserialize(any());
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenUnexpectedResponseType_thenDoNothing() {
    // Given
    ResponseData unexpectedResponse = new ResponseData() {};
    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> unexpectedResponse);

    when(serializedResponseDataHelper.deserialize(unexpectedResponse)).thenReturn(unexpectedResponse);

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(unexpectedResponse);
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciExecutionRepository, never()).updateCapacityTaskInProgress(anyString(), anyString(), eq(false));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenExceptionDuringProcessing_thenSendFacilitatorResponse() {
    // Given
    VmTaskExecutionResponse vmResponse =
        VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .capacityReservation(CapacityReservation.builder().poolID("pool123").build())
            .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenThrow(new RuntimeException("Database error"));

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper).deserialize(vmResponse);
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
    verify(ciExecutionRepository, never()).updateCapacityTaskInProgress(anyString(), anyString(), eq(false));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenBothUpdateAndFacilitatorFail_thenHandleGracefully() {
    // Given
    VmTaskExecutionResponse vmResponse =
        VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .capacityReservation(CapacityReservation.builder().poolID("pool123").build())
            .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> vmResponse);

    when(serializedResponseDataHelper.deserialize(vmResponse)).thenReturn(vmResponse);
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenThrow(new RuntimeException("Database error"));
    doThrow(new RuntimeException("Facilitator error"))
        .when(stepPMSFacilitator)
        .sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));

    // When
    notifier.notify(responseMap);

    // Then - Should not throw exception
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenInvalidAmbianceBytes_thenDoNothing() {
    // Given
    notifier = CICapacityNotifier.builder()
                   .waitId("waitId123")
                   .ambianceBytes(new byte[] {1, 2, 3}) // Invalid bytes
                   .stepParametersBytes(stepParametersBytes)
                   .dequeueResponseBytes(dequeueResponseBytes)
                   .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> new ResponseData() {});

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper, never()).deserialize(any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenInvalidStepParametersBytes_thenDoNothing() {
    // Given
    notifier = CICapacityNotifier.builder()
                   .waitId("waitId123")
                   .ambianceBytes(ambianceBytes)
                   .stepParametersBytes(new byte[] {1, 2, 3}) // Invalid bytes
                   .dequeueResponseBytes(dequeueResponseBytes)
                   .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> new ResponseData() {});

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper, never()).deserialize(any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotify_whenInvalidDequeueResponseBytes_thenDoNothing() {
    // Given
    notifier = CICapacityNotifier.builder()
                   .waitId("waitId123")
                   .ambianceBytes(ambianceBytes)
                   .stepParametersBytes(stepParametersBytes)
                   .dequeueResponseBytes(new byte[] {1, 2, 3}) // Invalid bytes
                   .build();

    Map<String, Supplier<ResponseData>> responseMap = new HashMap<>();
    responseMap.put("taskId123", () -> new ResponseData() {});

    // When
    notifier.notify(responseMap);

    // Then
    verify(serializedResponseDataHelper, never()).deserialize(any());
  }
}
