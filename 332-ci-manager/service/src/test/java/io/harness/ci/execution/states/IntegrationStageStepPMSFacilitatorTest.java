/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.SAURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.FacilitatorResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
@Category(UnitTests.class)
public class IntegrationStageStepPMSFacilitatorTest extends CategoryTest {
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private CIBuildEnforcer buildEnforcer;
  @Mock private HsqsClientService hsqsClientService;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private CIExecutionRepository ciExecutionRepository;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private CILicenseService ciLicenseService;
  @Mock private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;

  @InjectMocks private IntegrationStageStepPMSFacilitator facilitator;

  private Ambiance ambiance;
  private StepParameters stepParameters;
  private StageElementParameters stageElementParameters;
  private IntegrationStageStepParametersPMS integrationStageConfig;
  private Infrastructure infrastructure;
  private StepInputPackage inputPackage;
  private byte[] parameters;
  private String accountId;
  private String stageExecutionId;
  private String stageRuntimeId;
  private String topic;
  private QueueServiceClientConfig queueServiceClientConfig;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    // Set up common test data
    accountId = "account123";
    stageExecutionId = "stageExec123";
    stageRuntimeId = "stageRuntime123";
    topic = "ci";

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId(stageRuntimeId)
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    List<Level> levels = new ArrayList<>();
    levels.add(l2);

    Map<String, String> setupAbstractions = new HashMap<>();

    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .setStageExecutionId(stageExecutionId)
                   .addAllLevels(levels)
                   .build();

    // Set up Infrastructure mock
    infrastructure = mock(Infrastructure.class);

    // Set up IntegrationStageStepParametersPMS mock
    integrationStageConfig = mock(IntegrationStageStepParametersPMS.class);
    when(integrationStageConfig.getInfrastructure()).thenReturn(infrastructure);

    // Set up StageElementParameters mock
    stageElementParameters = mock(StageElementParameters.class);
    when(stageElementParameters.getSpecConfig()).thenReturn(integrationStageConfig);

    // Set up stepParameters
    stepParameters = stageElementParameters;

    // Set up inputPackage
    inputPackage = mock(StepInputPackage.class);

    // Set up parameters
    parameters = new byte[0];

    // Set up QueueServiceClientConfig
    queueServiceClientConfig = mock(QueueServiceClientConfig.class);
    when(queueServiceClientConfig.getTopic()).thenReturn(topic);
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueServiceClientConfig);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenQueueingEnabledAndShouldQueue_thenEnqueueRequest() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
      when(enqueueResponse.getItemId()).thenReturn("queueItem123");
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(infrastructure), anyString(), any());

      ArgumentCaptor<EnqueueRequest> enqueueRequestCaptor = ArgumentCaptor.forClass(EnqueueRequest.class);
      verify(hsqsClientService).enqueue(enqueueRequestCaptor.capture());
      EnqueueRequest enqueueRequest = enqueueRequestCaptor.getValue();

      // Verify EnqueueRequest properties
      assert (enqueueRequest.getTopic().equals(topic));
      assert (enqueueRequest.getSubTopic().equals(accountId));
      assert (enqueueRequest.getProducerName().equals(topic));
      assert (enqueueRequest.getPayload() != null);

      // Verify ciExecutionRepository update
      verify(ciExecutionRepository).updateQueueId(accountId, stageExecutionId, "queueItem123", topic, accountId);

      // Verify response
      assert (response.getExecutionMode().equals(ExecutionMode.CHILD));
      assert (response.getStatus().equals(Status.QUEUED_LICENSE_LIMIT_REACHED));
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenQueueingEnabledAndShouldNotQueue_thenNoEnqueue() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(false);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      CIExecutionMetadata ciExecutionMetadata = mock(CIExecutionMetadata.class);
      when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
          .thenReturn(ciExecutionMetadata);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(infrastructure), anyString(), any());
      verify(hsqsClientService, never()).enqueue(any(EnqueueRequest.class));

      // Verify execution status update
      verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());

      // Verify response
      assert (response.getExecutionMode().equals(ExecutionMode.CHILD));
      assert (response.getStatus().equals(Status.RUNNING));
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenQueueingEnabledButNotHostedVM_thenNoEnqueue() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      CIExecutionMetadata ciExecutionMetadata = mock(CIExecutionMetadata.class);
      when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
          .thenReturn(ciExecutionMetadata);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer, never()).shouldQueue(anyString(), any(), anyString(), any());
      verify(hsqsClientService, never()).enqueue(any(EnqueueRequest.class));

      // Verify response
      assert (response.getExecutionMode().equals(ExecutionMode.CHILD));
      assert (response.getStatus().equals(Status.RUNNING));
    }
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenEnqueueFails_thenThrowException() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenThrow(new RuntimeException("Enqueue failed"));

      // When - This should throw a CIStageExecutionException
      facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);
    }
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenCiExecutionMetadataNull_thenThrowException() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(false);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
          .thenReturn(null);

      // When - This should throw a CIStageExecutionException
      facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);
    }
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testFacilitate_whenHostedPlatformNotEnabledForAccount_thenFailWithoutQueueing() {
    // Given - the account is not entitled to the requested hosted platform
    doThrow(new CIStageExecutionException("Mac Arm64 platform is not enabled for accountId " + accountId))
        .when(vmInitializeTaskParamsBuilder)
        .validateHostedPlatform(eq(infrastructure), eq(accountId));

    // When
    assertThatThrownBy(() -> facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Mac Arm64 platform is not enabled");

    // Then - the build is never recorded or queued
    verify(queueExecutionUtils, never()).addExecutionRecord(any(), anyString(), anyString(), any());
    verify(buildEnforcer, never()).shouldQueue(anyString(), any(), anyString(), any());
    verify(queueExecutionUtils, never()).isGlobalQueueEnabled(any(), any());
    verify(hsqsClientService, never()).enqueue(any(EnqueueRequest.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSendFacilitatorResponseWithStatus() {
    // When
    facilitator.sendFacilitatorResponse(ambiance, Status.RUNNING);

    // Then
    ArgumentCaptor<FacilitatorResponseProto> responseCaptor = ArgumentCaptor.forClass(FacilitatorResponseProto.class);
    verify(sdkNodeExecutionService).handleFacilitationResponse(eq(ambiance), eq(""), responseCaptor.capture());

    FacilitatorResponseProto response = responseCaptor.getValue();
    assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
    assertThat(response.getIsSuccessful()).isTrue();
    assertThat(response.getStatus()).isEqualTo(Status.RUNNING);
  }

  // ==================== Tests for CI_GLOBAL_QUEUEING_ENABLED Feature Flag ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingEnabled_thenEnqueueBuild() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - Global queueing is enabled (FF on + HOSTED_VM + shouldQueue=false)
      when(buildEnforcer.shouldQueue(eq(accountId), any(Infrastructure.class), anyString(), any())).thenReturn(false);

      // Mock the infrastructure as HostedVmInfraYaml
      HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
      when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);
      when(hostedVmInfra.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra))).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(hostedVmInfra), any()))
          .thenReturn(86400L);

      // Mock global queue topic and subtopic
      String globalTopic = "global_capacity_queue_ci";
      String subTopic = "Linux-Amd64-free-flex";
      when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

      // Mock HSQS enqueue response
      EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
      when(enqueueResponse.getItemId()).thenReturn("globalQueueItem123");
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(hostedVmInfra), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(hostedVmInfra), anyString(), any());
      verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra));

      // Verify enqueueBuild was called with correct parameters
      ArgumentCaptor<EnqueueRequest> enqueueRequestCaptor = ArgumentCaptor.forClass(EnqueueRequest.class);
      verify(hsqsClientService).enqueue(enqueueRequestCaptor.capture());
      EnqueueRequest enqueueRequest = enqueueRequestCaptor.getValue();

      assertThat(enqueueRequest.getTopic()).isEqualTo(globalTopic);
      assertThat(enqueueRequest.getSubTopic()).isEqualTo(subTopic);
      assertThat(enqueueRequest.getProducerName()).isEqualTo(topic);
      assertThat(enqueueRequest.getPayload()).isNotNull();

      // Verify queue ID was updated
      verify(ciExecutionRepository)
          .updateQueueId(accountId, stageExecutionId, "globalQueueItem123", globalTopic, subTopic);

      // Verify execution status was updated to QUEUED_GLOBAL_INFRA_CAPACITY_REACHED
      verify(ciExecutionRepository)
          .updateExecutionStatus(accountId, stageExecutionId, Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString());

      // Verify metrics were published
      verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), eq(hostedVmInfra));

      // Verify response status
      assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
      assertThat(response.getStatus()).isEqualTo(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED);
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingDisabled_thenUpdateExecutionStatus() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - Global queueing is disabled (FF off)
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(false);
      when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), eq(infrastructure))).thenReturn(false);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      CIExecutionMetadata ciExecutionMetadata = mock(CIExecutionMetadata.class);
      when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
          .thenReturn(ciExecutionMetadata);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(infrastructure), anyString(), any());
      verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), eq(infrastructure));
      verify(hsqsClientService, never()).enqueue(any(EnqueueRequest.class));
      // Verify execution status was updated (not capacity task)
      verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());

      // Verify response
      assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
      assertThat(response.getStatus()).isEqualTo(Status.RUNNING);
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingEnabledButNotHostedVM_thenUpdateExecutionStatus() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - FF is on but infrastructure is not HOSTED_VM
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);
      when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), eq(infrastructure))).thenReturn(false);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      CIExecutionMetadata ciExecutionMetadata = mock(CIExecutionMetadata.class);
      when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
          .thenReturn(ciExecutionMetadata);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer, never()).shouldQueue(anyString(), any(), anyString(), any());

      // Verify execution status was updated (not capacity task)
      verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());

      // Verify response
      assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
      assertThat(response.getStatus()).isEqualTo(Status.RUNNING);
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingEnabledButShouldQueue_thenEnqueueRequest() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - Global queueing would be enabled, but shouldQueue=true takes precedence
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(86400L);

      EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
      when(enqueueResponse.getItemId()).thenReturn("queueItem456");
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(infrastructure), anyString(), any());
      verify(hsqsClientService).enqueue(any(EnqueueRequest.class));

      // Verify response - should queue via license limit, not global queueing
      assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
      assertThat(response.getStatus()).isEqualTo(Status.QUEUED_LICENSE_LIMIT_REACHED);
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingEnabledWithMultipleFFChecks_thenFeatureFlagCheckedOnce() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - Verify isGlobalQueueEnabled is only checked once per facilitate call
      when(buildEnforcer.shouldQueue(eq(accountId), any(Infrastructure.class), anyString(), any())).thenReturn(false);

      HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
      when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);
      when(hostedVmInfra.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra))).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(hostedVmInfra), any()))
          .thenReturn(86400L);

      // Mock global queue topic and subtopic
      String subTopic = "Windows-Amd64-paid-large";
      when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

      // Mock HSQS enqueue response
      EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
      when(enqueueResponse.getItemId()).thenReturn("globalQueueItem789");
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

      // When
      facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then - isGlobalQueueEnabled should only be checked once
      verify(queueExecutionUtils, times(1)).isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra));
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_whenGlobalQueueingEnqueueFails_thenSendFacilitatorResponse() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      // Given - Global queueing is enabled but enqueue fails
      when(buildEnforcer.shouldQueue(eq(accountId), any(Infrastructure.class), anyString(), any())).thenReturn(false);

      HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
      when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);
      when(hostedVmInfra.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra))).thenReturn(true);

      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(hostedVmInfra), any()))
          .thenReturn(86400L);

      // Mock global queue subtopic
      String subTopic = "MacOS-Amd64-free-flex";
      when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

      // Mock HSQS enqueue to throw exception
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenThrow(new RuntimeException("Enqueue failed"));

      // When
      FacilitatorResponse response = facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then - Should send facilitator response to proceed despite failure
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(hostedVmInfra), eq(accountId), eq(stageExecutionId), eq(86400L));
      verify(buildEnforcer).shouldQueue(eq(accountId), eq(hostedVmInfra), anyString(), any());
      verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), eq(hostedVmInfra));
      verify(hsqsClientService).enqueue(any(EnqueueRequest.class));

      // Verify sendFacilitatorResponse was called (via sdkNodeExecutionService)
      verify(sdkNodeExecutionService)
          .handleFacilitationResponse(eq(ambiance), eq(""), any(FacilitatorResponseProto.class));

      // Verify execution proceeds (no exception thrown)
      assertThat(response.getExecutionMode()).isEqualTo(ExecutionMode.CHILD);
      assertThat(response.getStatus()).isEqualTo(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED);
    }
  }

  // ==================== Tests for enqueueBuild method ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testEnqueueBuild_success() {
    // Given
    HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
    when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);

    String globalTopic = "global_capacity_queue_ci";
    String subTopic = "Linux-Amd64-paid-xlarge";
    when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

    EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
    when(enqueueResponse.getItemId()).thenReturn("globalQueueItemABC");
    when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

    // When
    facilitator.enqueueBuild(ambiance, stepParameters);

    // Then
    ArgumentCaptor<EnqueueRequest> enqueueRequestCaptor = ArgumentCaptor.forClass(EnqueueRequest.class);
    verify(hsqsClientService).enqueue(enqueueRequestCaptor.capture());
    EnqueueRequest enqueueRequest = enqueueRequestCaptor.getValue();

    assertThat(enqueueRequest.getTopic()).isEqualTo(globalTopic);
    assertThat(enqueueRequest.getSubTopic()).isEqualTo(subTopic);
    assertThat(enqueueRequest.getProducerName()).isEqualTo(topic);
    assertThat(enqueueRequest.getPayload()).isNotNull();

    // Verify queue ID was updated with correct topic and subtopic
    verify(ciExecutionRepository)
        .updateQueueId(accountId, stageExecutionId, "globalQueueItemABC", globalTopic, subTopic);

    // Verify execution status was updated
    verify(ciExecutionRepository)
        .updateExecutionStatus(accountId, stageExecutionId, Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString());

    // Verify metrics were published
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), eq(hostedVmInfra));

    // Verify sendFacilitatorResponse was NOT called (success case)
    verify(sdkNodeExecutionService, never()).handleFacilitationResponse(any(), any(), any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testEnqueueBuild_whenEnqueueFails_thenSendFacilitatorResponse() {
    // Given
    HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
    when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);

    String subTopic = "Windows-Amd64-free-flex";
    when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

    // Mock enqueue to fail
    when(hsqsClientService.enqueue(any(EnqueueRequest.class)))
        .thenThrow(new RuntimeException("HSQS service unavailable"));

    // When
    facilitator.enqueueBuild(ambiance, stepParameters);

    // Then
    verify(hsqsClientService).enqueue(any(EnqueueRequest.class));

    // Verify queue ID was NOT updated
    verify(ciExecutionRepository, never())
        .updateQueueId(anyString(), anyString(), anyString(), anyString(), anyString());

    // Verify execution status was NOT updated
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());

    // Verify metrics were NOT published
    verify(queueExecutionUtils, never()).publishQueueCountMetrics(any(), any());

    // Verify sendFacilitatorResponse was called to proceed
    verify(sdkNodeExecutionService)
        .handleFacilitationResponse(eq(ambiance), eq(""), any(FacilitatorResponseProto.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testEnqueueBuild_whenItemIdIsBlank_thenDoNotUpdateQueueId() {
    // Given
    HostedVmInfraYaml hostedVmInfra = mock(HostedVmInfraYaml.class);
    when(integrationStageConfig.getInfrastructure()).thenReturn(hostedVmInfra);

    String subTopic = "MacOS-Amd64-paid-large";
    when(queueExecutionUtils.getGlobalQueueSubTopic(eq(ambiance), eq(stepParameters))).thenReturn(subTopic);

    EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
    when(enqueueResponse.getItemId()).thenReturn(""); // Blank item ID
    when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

    // When
    facilitator.enqueueBuild(ambiance, stepParameters);

    // Then
    verify(hsqsClientService).enqueue(any(EnqueueRequest.class));

    // Verify queue ID was NOT updated (blank item ID)
    verify(ciExecutionRepository, never())
        .updateQueueId(anyString(), anyString(), anyString(), anyString(), anyString());

    // Verify execution status WAS updated (despite blank item ID)
    verify(ciExecutionRepository)
        .updateExecutionStatus(accountId, stageExecutionId, Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString());

    // Verify metrics were published
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), eq(hostedVmInfra));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testFacilitate_withNonNullStageTimeout_shouldPassTimeoutSecondsToAddExecutionRecord() {
    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      when(infrastructure.getType()).thenReturn(Infrastructure.Type.HOSTED_VM);
      when(stageElementParameters.getStageTimeout()).thenReturn(ParameterField.createValueField("48h"));
      when(buildEnforcer.shouldQueue(eq(accountId), eq(infrastructure), anyString(), any())).thenReturn(true);

      // Mock IntegrationStageUtils.getStageTimeOut to return 48 hours in seconds (172800L)
      mockedStatic
          .when(()
                    -> IntegrationStageUtils.getStageTimeOut(eq(accountId), any(CIFeatureFlagService.class),
                        any(CILicenseService.class), eq(stageExecutionId), any(), eq(infrastructure), any()))
          .thenReturn(172800L);

      EnqueueResponse enqueueResponse = mock(EnqueueResponse.class);
      when(enqueueResponse.getItemId()).thenReturn("queueItem123");
      when(hsqsClientService.enqueue(any(EnqueueRequest.class))).thenReturn(enqueueResponse);

      // When
      facilitator.facilitate(ambiance, stepParameters, parameters, inputPackage);

      // Then - verify addExecutionRecord was called with timeout in seconds as Long
      ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
      verify(queueExecutionUtils)
          .addExecutionRecord(eq(infrastructure), eq(accountId), eq(stageExecutionId), timeoutCaptor.capture());

      Long capturedTimeoutSeconds = timeoutCaptor.getValue();
      assertThat(capturedTimeoutSeconds).isNotNull();
      // 48 hours = 48 * 60 * 60 = 172800 seconds
      assertThat(capturedTimeoutSeconds).isEqualTo(172800L);
    }
  }
}
