/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.LocalVmDriverType;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.DliteVmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.DockerInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.K8InitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BuildSetupUtilsTest {
  @Mock private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;
  @Mock private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Mock private DliteVmInitializeTaskParamsBuilder dliteVmInitializeTaskParamsBuilder;
  @Mock private DockerInitializeTaskParamsBuilder dockerInitializeTaskParamsBuilder;

  @InjectMocks private BuildSetupUtils buildSetupUtils;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    ambiance = Ambiance.newBuilder().build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParamsForK8() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
            .build();
    CIK8InitializeTaskParams expectedParams = CIK8InitializeTaskParams.builder().build();
    when(
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(eq(initializeStepInfo), eq(ambiance), any(), eq(false)))
        .thenReturn(expectedParams);

    CIInitializeTaskParams result = buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false);

    assertThat(result).isEqualTo(expectedParams);
    verify(k8InitializeTaskParamsBuilder).getK8InitializeTaskParams(initializeStepInfo, ambiance, "log", false);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParamsForVM() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().infrastructure(VmInfraYaml.builder().type(Infrastructure.Type.VM).build()).build();
    CIVmInitializeTaskParams expectedParams = CIVmInitializeTaskParams.builder().build();
    when(vmInitializeTaskParamsBuilder.getDirectVmInitializeTaskParams(eq(initializeStepInfo), eq(ambiance), eq(false)))
        .thenReturn(expectedParams);

    CIInitializeTaskParams result = buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false);

    assertThat(result).isEqualTo(expectedParams);
    verify(vmInitializeTaskParamsBuilder).getDirectVmInitializeTaskParams(initializeStepInfo, ambiance, false);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParamsForDocker() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(DockerInfraYaml.builder().type(Infrastructure.Type.DOCKER).build())
            .build();
    CIVmInitializeTaskParams expectedParams = CIVmInitializeTaskParams.builder().build();
    when(dockerInitializeTaskParamsBuilder.getDockerInitializeTaskParams(
             eq(initializeStepInfo), eq(ambiance), eq(false), eq(LocalVmDriverType.NONE)))
        .thenReturn(expectedParams);

    CIInitializeTaskParams result = buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false);

    assertThat(result).isEqualTo(expectedParams);
    verify(dockerInitializeTaskParamsBuilder)
        .getDockerInitializeTaskParams(initializeStepInfo, ambiance, false, LocalVmDriverType.NONE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParamsForHostedVM() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build())
            .build();
    DliteVmInitializeTaskParams expectedParams = DliteVmInitializeTaskParams.builder().build();
    when(dliteVmInitializeTaskParamsBuilder.getDliteVmInitializeTaskParams(
             eq(initializeStepInfo), eq(ambiance), eq(false)))
        .thenReturn(expectedParams);

    CIInitializeTaskParams result = buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false);

    assertThat(result).isEqualTo(expectedParams);
    verify(dliteVmInitializeTaskParamsBuilder).getDliteVmInitializeTaskParams(initializeStepInfo, ambiance, false);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildSetupTaskParamsOverloadDelegatesToFullMethod() {
    BuildSetupUtils spyBuildSetupUtils = spy(buildSetupUtils);
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
            .build();
    CIK8InitializeTaskParams expectedParams = CIK8InitializeTaskParams.builder().build();
    when(
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(eq(initializeStepInfo), eq(ambiance), any(), eq(false)))
        .thenReturn(expectedParams);

    CIInitializeTaskParams result =
        spyBuildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false);

    assertThat(result).isEqualTo(expectedParams);
    verify(spyBuildSetupUtils)
        .getBuildSetupTaskParams(initializeStepInfo, ambiance, "log", false, LocalVmDriverType.NONE);
  }
}
