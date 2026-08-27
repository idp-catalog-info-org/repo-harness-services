/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.yaml.extended.infrastrucutre.EcsDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImageInformation;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerStepV2DefinitionCreatorTest extends CategoryTest {
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetContainerDefinitionInfo() {
    InitContainerV2StepInfo initContainerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                                          .build())
                                .build())
            .pluginsData(Collections.singletonMap(StepInfo.builder().build(),
                PluginCreationResponseList.newBuilder()
                    .addResponse(PluginCreationResponseWrapper.newBuilder().setShouldSkip(true).build())
                    .build()))
            .build();

    List<ContainerDefinitionInfo> containerDefinitionInfoList =
        ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(initContainerV2StepInfo, "stepGroupIdentifier",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build(), false);

    assertThat(containerDefinitionInfoList).isEmpty();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetContainerDefinitionInfo_ecsDirectInfraDoesNotCastToK8s() {
    InitContainerV2StepInfo initContainerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .infrastructure(ContainerEcsInfra.builder().spec(EcsDirectInfraYamlSpec.builder().build()).build())
            .pluginsData(Collections.singletonMap(StepInfo.builder().build(),
                PluginCreationResponseList.newBuilder()
                    .addResponse(PluginCreationResponseWrapper.newBuilder().setShouldSkip(true).build())
                    .build()))
            .build();

    List<ContainerDefinitionInfo> containerDefinitionInfoList =
        ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(initContainerV2StepInfo, "stepGroupIdentifier",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build(), false);

    assertThat(containerDefinitionInfoList).isEmpty();
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetContainerDefinitionInfoWithRegistryRef() {
    PluginDetails pluginDetails =
        PluginDetails.newBuilder()
            .setResource(PluginContainerResources.newBuilder().setCpu(400).setMemory(500).build())
            .setImageDetails(
                ImageDetails.newBuilder()
                    .setConnectorDetails(ConnectorDetails.newBuilder()
                                             .setConnectorRef("account.harnessImage")
                                             .setRegistryRef("account.myRegistry")
                                             .build())
                    .setImageInformation(ImageInformation.newBuilder()
                                             .setImageName(StringValue.newBuilder().setValue("alpine").build())
                                             .build())
                    .build())
            .setTotalPortUsedDetails(PortDetails.newBuilder().addUsedPorts(20002).build())
            .addPortUsed(20002)
            .build();
    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper wrapper =
        PluginCreationResponseWrapper.newBuilder()
            .setResponse(response)
            .setStepInfo(StepInfoProto.newBuilder().setIdentifier("Run_1").setName("Run_1").setUuid("uuid").build())
            .build();

    InitContainerV2StepInfo initContainerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                                          .build())
                                .build())
            .pluginsData(Collections.singletonMap(StepInfo.builder().stepIdentifier("Run_1").build(),
                PluginCreationResponseList.newBuilder().addResponse(wrapper).build()))
            .build();

    List<ContainerDefinitionInfo> containerDefinitionInfoList =
        ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(initContainerV2StepInfo, "stepGroupIdentifier",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build(), false);

    assertThat(containerDefinitionInfoList).hasSize(1);
    assertThat(containerDefinitionInfoList.get(0).getContainerImageDetails().getRegistryRef())
        .isEqualTo("account.myRegistry");
    assertThat(containerDefinitionInfoList.get(0).getContainerImageDetails().getConnectorIdentifier())
        .isEqualTo("account.harnessImage");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetContainerDefinitionInfoWithEmptyRegistryRef() {
    PluginDetails pluginDetails =
        PluginDetails.newBuilder()
            .setResource(PluginContainerResources.newBuilder().setCpu(400).setMemory(500).build())
            .setImageDetails(
                ImageDetails.newBuilder()
                    .setConnectorDetails(ConnectorDetails.newBuilder().setConnectorRef("account.harnessImage").build())
                    .setImageInformation(ImageInformation.newBuilder()
                                             .setImageName(StringValue.newBuilder().setValue("alpine").build())
                                             .build())
                    .build())
            .setTotalPortUsedDetails(PortDetails.newBuilder().addUsedPorts(20002).build())
            .addPortUsed(20002)
            .build();
    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper wrapper =
        PluginCreationResponseWrapper.newBuilder()
            .setResponse(response)
            .setStepInfo(StepInfoProto.newBuilder().setIdentifier("Run_1").setName("Run_1").setUuid("uuid").build())
            .build();

    InitContainerV2StepInfo initContainerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                                          .build())
                                .build())
            .pluginsData(Collections.singletonMap(StepInfo.builder().stepIdentifier("Run_1").build(),
                PluginCreationResponseList.newBuilder().addResponse(wrapper).build()))
            .build();

    List<ContainerDefinitionInfo> containerDefinitionInfoList =
        ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(initContainerV2StepInfo, "stepGroupIdentifier",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build(), false);

    assertThat(containerDefinitionInfoList).hasSize(1);
    assertThat(containerDefinitionInfoList.get(0).getContainerImageDetails().getRegistryRef()).isNull();
    assertThat(containerDefinitionInfoList.get(0).getContainerImageDetails().getConnectorIdentifier())
        .isEqualTo("account.harnessImage");
  }
}
