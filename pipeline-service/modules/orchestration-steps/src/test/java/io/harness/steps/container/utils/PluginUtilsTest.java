/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PluginUtilsTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;

  @InjectMocks private PluginUtils pluginUtils;

  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetContainerStepDelegateSelectors() {
    ConnectorDetails connectorDetails = getConnectorDetailsWithDelegateSelector(List.of("connector-del-selector1"));

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("step-del-selector");
    taskSelectorYaml.setOrigin("step");
    ContainerStepInfo containerStepSpec = getContainerStepInfo(List.of(taskSelectorYaml));

    doReturn(connectorDetails).when(connectorUtils).getConnectorDetails(any(), any());

    List<TaskSelector> containerStepDelegateSelectors =
        pluginUtils.getContainerStepDelegateSelectors(testAmbiance(), containerStepSpec);

    assertThat(containerStepDelegateSelectors).hasSize(1);
    assertThat(containerStepDelegateSelectors.get(0).getSelector()).isEqualTo("step-del-selector");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetContainerStepDelegateSelectorsWithStepDelSelector() {
    ConnectorDetails connectorDetails = getConnectorDetailsWithDelegateSelector(List.of());

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("step-del-selector");
    taskSelectorYaml.setOrigin("step");
    ContainerStepInfo containerStepSpec = getContainerStepInfo(List.of(taskSelectorYaml));

    doReturn(connectorDetails).when(connectorUtils).getConnectorDetails(any(), any());

    List<TaskSelector> containerStepDelegateSelectors =
        pluginUtils.getContainerStepDelegateSelectors(testAmbiance(), containerStepSpec);

    assertThat(containerStepDelegateSelectors).hasSize(1);
    assertThat(containerStepDelegateSelectors.get(0).getSelector()).isEqualTo("step-del-selector");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetContainerStepDelegateSelectorsWithConnectorDelSelector() {
    ConnectorDetails connectorDetails = getConnectorDetailsWithDelegateSelector(List.of("connector-del-selector1"));

    ContainerStepInfo containerStepSpec = getContainerStepInfo(List.of());

    doReturn(connectorDetails).when(connectorUtils).getConnectorDetails(any(), any());

    List<TaskSelector> containerStepDelegateSelectors =
        pluginUtils.getContainerStepDelegateSelectors(testAmbiance(), containerStepSpec);

    assertThat(containerStepDelegateSelectors).hasSize(1);
    assertThat(containerStepDelegateSelectors.get(0).getSelector()).isEqualTo("connector-del-selector1");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetContainerStepDelegateSelectorsEmpty() {
    ConnectorDetails connectorDetails = getConnectorDetailsWithDelegateSelector(List.of());

    ContainerStepInfo containerStepSpec = getContainerStepInfo(List.of());

    doReturn(connectorDetails).when(connectorUtils).getConnectorDetails(any(), any());

    List<TaskSelector> containerStepDelegateSelectors =
        pluginUtils.getContainerStepDelegateSelectors(testAmbiance(), containerStepSpec);

    assertThat(containerStepDelegateSelectors).isEmpty();
  }

  @NotNull
  private ContainerStepInfo getContainerStepInfo(List<TaskSelectorYaml> delegateSelectors) {
    ContainerStepInfo containerStepSpec = ContainerStepInfo.infoBuilder().build();
    containerStepSpec.setDelegateSelectors(ParameterField.createValueField(delegateSelectors));
    containerStepSpec.setInfrastructure(
        ContainerK8sInfra.builder()
            .spec(ContainerInfraYamlSpec.builder()
                      .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                      .connectorRef(ParameterField.<String>builder().value("connector").build())
                      .build())
            .build());
    return containerStepSpec;
  }

  private ConnectorDetails getConnectorDetailsWithDelegateSelector(List<String> delSelectors) {
    return ConnectorDetails.builder()
        .connectorConfig(KubernetesClusterConfigDTO.builder().delegateSelectors(new HashSet<>(delSelectors)).build())
        .build();
  }

  private Ambiance testAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(1).setPipelineIdentifier("pipeline").build();
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(setupAbstractions)
        .setMetadata(executionMetadata)
        .build();
  }
}
