/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.ff.FeatureFlagService;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerParamsProvider;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.utils.PmsFeatureFlagService;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class InitContainerStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private K8sPodInitUtils k8sPodInitUtils;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ContainerStepImageUtils harnessImageUtils;
  @Mock private ContainerParamsProvider containerParamsProvider;
  @Mock private CiServiceResourceClient ciServiceResourceClient;
  @Mock private ContainerStepInitHelper containerStepInitHelper;
  @InjectMocks @Spy private InitContainerStep initContainerStep;
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_NullBuildSetupTaskParams() {
    // Arrange
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().timeout(ParameterField.createValueField("5m")).build();
    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, null);

    // Assert
    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION.name());
    assertThat(taskData.isAsync()).isTrue();
    assertThat(taskData.getTimeout()).isEqualTo(300000L); // 5m = 300000ms
    assertThat(taskData.getParameters().length).isEqualTo(1);
    assertThat(taskData.getParameters()[0]).isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_NonK8InitializeTaskParams() {
    // Arrange
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().timeout(ParameterField.createValueField("10m")).build();

    CIInitializeTaskParams buildSetupTaskParams = mock(CIInitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.DOCKER);

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION.name());
    assertThat(taskData.getTimeout()).isEqualTo(600000L); // 10m = 600000ms
    assertThat(taskData.getParameters()[0]).isSameAs(buildSetupTaskParams);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsWithoutSpecialVolumes() {
    // Arrange
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().timeout(ParameterField.createValueField("2m")).build();

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);

    CIK8PodParams<CIK8ContainerParams> podParams = mock(CIK8PodParams.class);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(podParams);

    // Create a volume that is neither CONFIG_MAP nor SECRET
    PodVolume regularVolume = mock(PodVolume.class);
    when(regularVolume.getType()).thenReturn(PodVolume.Type.EMPTY_DIR);
    when(podParams.getVolumes()).thenReturn(Collections.singletonList(regularVolume));

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION.name());
    assertThat(taskData.getTimeout()).isEqualTo(120000L); // 2m = 120000ms
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsWithConfigMapVolume() {
    // Arrange
    StepElementParameters stepElementParameters = mock(StepElementParameters.class);
    when(stepElementParameters.getTimeout()).thenReturn(ParameterField.createValueField("3m"));

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);

    CIK8PodParams<CIK8ContainerParams> podParams = mock(CIK8PodParams.class);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(podParams);

    // Create a CONFIG_MAP volume
    PodVolume configMapVolume = mock(PodVolume.class);
    when(configMapVolume.getType()).thenReturn(PodVolume.Type.CONFIG_MAP);
    when(podParams.getVolumes()).thenReturn(Collections.singletonList(configMapVolume));

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION_V2.name());
    assertThat(taskData.getTimeout()).isEqualTo(180000L); // 3m = 180000ms
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsWithSecretVolume() {
    // Arrange
    StepElementParameters stepElementParameters = mock(StepElementParameters.class);
    when(stepElementParameters.getTimeout()).thenReturn(ParameterField.createValueField("1m"));

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);

    CIK8PodParams<CIK8ContainerParams> podParams = mock(CIK8PodParams.class);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(podParams);

    // Create a SECRET volume
    PodVolume secretVolume = mock(PodVolume.class);
    when(secretVolume.getType()).thenReturn(PodVolume.Type.SECRET);
    when(podParams.getVolumes()).thenReturn(Collections.singletonList(secretVolume));

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION_V2.name());
    assertThat(taskData.getTimeout()).isEqualTo(60000L); // 1m = 60000ms
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsWithMixedVolumes() {
    // Arrange
    StepElementParameters stepElementParameters = mock(StepElementParameters.class);
    when(stepElementParameters.getTimeout()).thenReturn(ParameterField.createValueField("4m"));

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);

    CIK8PodParams<CIK8ContainerParams> podParams = mock(CIK8PodParams.class);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(podParams);

    // Create mixed volumes (one regular, one SECRET)
    PodVolume regularVolume = mock(PodVolume.class);
    when(regularVolume.getType()).thenReturn(PodVolume.Type.EMPTY_DIR);

    PodVolume secretVolume = mock(PodVolume.class);
    when(secretVolume.getType()).thenReturn(PodVolume.Type.SECRET);

    when(podParams.getVolumes()).thenReturn(Arrays.asList(regularVolume, secretVolume));

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION_V2.name());
    assertThat(taskData.getTimeout()).isEqualTo(240000L); // 4m = 240000ms
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsNullPodParams() {
    // Arrange
    StepElementParameters stepElementParameters = mock(StepElementParameters.class);
    when(stepElementParameters.getTimeout()).thenReturn(ParameterField.createValueField("30s"));

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(null);

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION.name());
    assertThat(taskData.getTimeout()).isEqualTo(30000L); // 30s = 30000ms
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetTaskData_K8TaskParamsNullVolumes() {
    // Arrange
    StepElementParameters stepElementParameters = mock(StepElementParameters.class);
    when(stepElementParameters.getTimeout()).thenReturn(ParameterField.createValueField("15s"));

    CIK8InitializeTaskParams buildSetupTaskParams = mock(CIK8InitializeTaskParams.class);
    when(buildSetupTaskParams.getType()).thenReturn(CIInitializeTaskParams.Type.GCP_K8);

    CIK8PodParams<CIK8ContainerParams> podParams = mock(CIK8PodParams.class);
    when(buildSetupTaskParams.getCik8PodParams()).thenReturn(podParams);
    when(podParams.getVolumes()).thenReturn(null);

    // Act
    TaskData taskData = initContainerStep.getTaskData(stepElementParameters, buildSetupTaskParams);

    // Assert
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.CONTAINER_INITIALIZATION.name());
    assertThat(taskData.getTimeout()).isEqualTo(15000L); // 15s = 15000ms
  }
}
