/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.CleanupStepInfo;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CleanupStepTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private CleanupStep cleanupStep;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");

    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder().setStepType(CleanupStep.STEP_TYPE).build())
                   .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").build())
                   .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(cleanupStep.getStepParametersClass()).isEqualTo(CleanupStepInfo.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainTaskWithNullInfrastructure() {
    CleanupStepInfo stepInfo = CleanupStepInfo.builder()
                                   .identifier("cleanupStep")
                                   .name("cleanup")
                                   .infrastructure(null)
                                   .podName("pod-1")
                                   .build();

    assertThatThrownBy(() -> cleanupStep.obtainTask(ambiance, stepInfo, StepInputPackage.builder().build()))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Input infrastructure can not be empty");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainTaskWithNullSpec() {
    K8sDirectInfraYaml infrastructure = K8sDirectInfraYaml.builder().spec(null).build();

    CleanupStepInfo stepInfo = CleanupStepInfo.builder()
                                   .identifier("cleanupStep")
                                   .name("cleanup")
                                   .infrastructure(infrastructure)
                                   .podName("pod-1")
                                   .build();

    assertThatThrownBy(() -> cleanupStep.obtainTask(ambiance, stepInfo, StepInputPackage.builder().build()))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Input infrastructure can not be empty");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtainTaskSuccess() {
    K8sDirectInfraYamlSpec spec = K8sDirectInfraYamlSpec.builder()
                                      .connectorRef(ParameterField.createValueField("connectorRef"))
                                      .namespace(ParameterField.createValueField("default"))
                                      .build();
    K8sDirectInfraYaml infrastructure = K8sDirectInfraYaml.builder().spec(spec).build();

    CleanupStepInfo stepInfo = CleanupStepInfo.builder()
                                   .identifier("cleanupStep")
                                   .name("cleanup")
                                   .infrastructure(infrastructure)
                                   .podName("pod-1")
                                   .build();

    PodCleanupDetails podCleanupDetails =
        PodCleanupDetails.builder().cleanUpContainerNames(Arrays.asList("container-1", "container-2")).build();

    KubernetesClusterConfigDTO k8sConfig =
        KubernetesClusterConfigDTO.builder()
            .delegateSelectors(Collections.singleton("delegate"))
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .config(KubernetesDelegateDetailsDTO.builder().build())
                            .build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorConfig(k8sConfig)
                                            .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                                            .identifier("connectorRef")
                                            .build();

    when(executionSweepingOutputResolver.resolve(any(), any())).thenReturn(podCleanupDetails);
    when(connectorUtils.getConnectorDetails(any(), eq("connectorRef"))).thenReturn(connectorDetails);

    TaskRequest taskRequest = cleanupStep.obtainTask(ambiance, stepInfo, StepInputPackage.builder().build());
    assertThat(taskRequest).isNotNull();
  }

  @SneakyThrows
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleTaskResultSuccess() {
    CleanupStepInfo stepInfo =
        CleanupStepInfo.builder().identifier("cleanupStep").name("cleanup").podName("pod-1").build();

    K8sTaskExecutionResponse executionResponse =
        K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    StepResponse stepResponse = cleanupStep.handleTaskResult(ambiance, stepInfo, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailure() {
    CleanupStepInfo stepInfo =
        CleanupStepInfo.builder().identifier("cleanupStep").name("cleanup").podName("pod-1").build();

    K8sTaskExecutionResponse executionResponse = K8sTaskExecutionResponse.builder()
                                                     .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                     .errorMessage("Pod cleanup failed")
                                                     .build();

    StepResponse stepResponse = cleanupStep.handleTaskResult(ambiance, stepInfo, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isNotNull();
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Pod cleanup failed");
  }

  @SneakyThrows
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailureWithNullErrorMessage() {
    CleanupStepInfo stepInfo =
        CleanupStepInfo.builder().identifier("cleanupStep").name("cleanup").podName("pod-1").build();

    K8sTaskExecutionResponse executionResponse =
        K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    StepResponse stepResponse = cleanupStep.handleTaskResult(ambiance, stepInfo, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isNotNull();
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEmpty();
  }
}
