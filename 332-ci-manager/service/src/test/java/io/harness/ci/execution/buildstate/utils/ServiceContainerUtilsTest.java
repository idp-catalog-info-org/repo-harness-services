/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate.utils;

import static io.harness.ci.commonconstants.CIExecutionConstants.ID_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.IMAGE_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.SERVICE_ARG_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.WIN_STEP_COMMAND;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ServiceContainerUtilsTest {
  @Mock private HarnessImageUtils harnessImageUtils;
  @InjectMocks private ServiceContainerUtils serviceContainerUtils;

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCommandForLinux() {
    List<String> command = ServiceContainerUtils.getCommand(OSType.Linux);
    assertThat(command).hasSize(1);
    assertThat(command.get(0)).isEqualTo(UNIX_STEP_COMMAND);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCommandForWindows() {
    List<String> command = ServiceContainerUtils.getCommand(OSType.Windows);
    assertThat(command).hasSize(1);
    assertThat(command.get(0)).isEqualTo(WIN_STEP_COMMAND);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetArguments() {
    List<String> args = ServiceContainerUtils.getArguments("svc1", "nginx:latest", 8080);
    assertThat(args).containsExactly(
        SERVICE_ARG_COMMAND, ID_PREFIX, "svc1", IMAGE_PREFIX, "nginx:latest", PORT_PREFIX, "8080");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetResolvedImagePullPolicyWithNullStageNode() {
    CIServiceInfo service = CIServiceInfo.builder().imagePullPolicy(ParameterField.createValueField(null)).build();
    String result = serviceContainerUtils.getResolvedImagePullPolicy(service, null, Ambiance.newBuilder().build());
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetResolvedImagePullPolicyWithExplicitPolicy() {
    CIServiceInfo service =
        CIServiceInfo.builder().imagePullPolicy(ParameterField.createValueField(ImagePullPolicy.ALWAYS)).build();
    String result = serviceContainerUtils.getResolvedImagePullPolicy(service, null, Ambiance.newBuilder().build());
    assertThat(result).isEqualTo("Always");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetResolvedImagePullPolicyDelegatesToHarnessImageUtils() {
    Infrastructure infra = K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();
    IntegrationStageConfigImpl stageConfig = IntegrationStageConfigImpl.builder().infrastructure(infra).build();
    IntegrationStageNode stageNode = IntegrationStageNode.builder().integrationStageConfig(stageConfig).build();
    CIServiceInfo service = CIServiceInfo.builder().imagePullPolicy(ParameterField.createValueField(null)).build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(harnessImageUtils.getUpdatedImagePullPolicyBasedOnAmbiance(isNull(), eq(infra), eq(ambiance)))
        .thenReturn("IfNotPresent");

    String result = serviceContainerUtils.getResolvedImagePullPolicy(service, stageNode, ambiance);

    assertThat(result)
        .as("Should delegate to harnessImageUtils when stageNode is present and policy is blank")
        .isEqualTo("IfNotPresent");
    verify(harnessImageUtils).getUpdatedImagePullPolicyBasedOnAmbiance(isNull(), eq(infra), eq(ambiance));
  }
}
