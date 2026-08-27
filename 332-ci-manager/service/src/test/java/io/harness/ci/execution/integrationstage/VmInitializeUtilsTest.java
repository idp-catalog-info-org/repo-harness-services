/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static com.mongodb.assertions.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeImageSpec;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.DockerRuntime;
import io.harness.beans.yaml.extended.runtime.DockerRuntime.DockerRuntimeSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.vm.VmInitializeUtilsImpl;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class VmInitializeUtilsTest extends CIExecutionTestBase {
  @InjectMocks private VmInitializeUtilsImpl vmInitializeUtils;
  @Mock CIFeatureFlagService featureFlagService;
  private final String accountId = "accountId";

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void validateStageConfig() {
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfig();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId, false, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void validateStageConfigWithInject() throws Exception {
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    IntegrationStageConfig integrationStageConfig =
        VmInitializeTaskHelper.getIntegrationStageConfigWithStepGroupAndInject();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId, true, null);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateStageConfigWithStepGroup() throws Exception {
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfigWithStepGroup();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId, false, null);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testLinuxOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithLinuxPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());

    assertThat(os).isEqualTo(OSType.Linux);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInvalidOSArch() {
    ParameterField os = ParameterField.ofNull();
    os.setValue("invalidValue");
    ParameterField arch = ParameterField.ofNull();
    arch.setValue("invalidValue");
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(Platform.builder().os(os).arch(arch).build()))
                      .build())
            .build();
    assertThatThrownBy(() -> VmInitializeUtils.getOS(hostedVmInfraYaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Os type invalidValue is invalid, valid values are : [Linux, MacOS, Windows]");
    assertThatThrownBy(() -> VmInitializeUtils.getArchType(hostedVmInfraYaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Arch type invalidValue is invalid, valid values are : [Amd64, Arm64]");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testMacOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithMacPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());
    assertThat(os).isEqualTo(OSType.MacOS);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testWorkDirDockerInfraTypeMacOsWithFFEnabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_MOUNT_PATH_ENABLED_MAC, accountId)).thenReturn(true);
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS, accountId, infrastructure)).isEqualTo("/private/tmp/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testWorkDirDockerInfraTypeMacOsWithFFDisabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_MOUNT_PATH_ENABLED_MAC, accountId)).thenReturn(false);
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS, accountId, infrastructure)).isEqualTo("/tmp/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testWorkDirDockerInfraTypeLinuxOs() {
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    assertThat(vmInitializeUtils.getWorkDir(OSType.Linux, accountId, infrastructure)).isEqualTo("/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testWorkDirNonDockerInfraType1() {
    Infrastructure infrastructure = HostedVmInfraYaml.builder().build();
    assertThat(vmInitializeUtils.getWorkDir(OSType.Linux, accountId, infrastructure)).isEqualTo("/harness");
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS, accountId, infrastructure)).isEqualTo("/tmp/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testWorkDirNonDockerInfraType2() {
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    assertThat(vmInitializeUtils.getWorkDir(OSType.Linux, accountId, infrastructure)).isEqualTo("/harness");
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS, accountId, infrastructure)).isEqualTo("/tmp/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathDockerInfraMacOsWithFFEnabled() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/private/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    when(featureFlagService.isEnabled(FeatureName.CI_MOUNT_PATH_ENABLED_MAC, accountId)).thenReturn(true);
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathDockerInfraMacOsWithFFDisabled() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    when(featureFlagService.isEnabled(FeatureName.CI_MOUNT_PATH_ENABLED_MAC, accountId)).thenReturn(false);
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathDockerInfraLinuxOs() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    Infrastructure infrastructure = DockerInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.Linux, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathNonDockerInfraMacOs1() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    Infrastructure infrastructure = HostedVmInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathNonDockerInfraMacOs2() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathNonDockerInfraLinuxOs1() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");
    Infrastructure infrastructure = HostedVmInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.Linux, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathNonDockerInfraLinuxOs2() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");

    Infrastructure infrastructure = VmInfraYaml.builder().build();
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.Linux, accountId, infrastructure);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testDebugModeValidation() {
    Infrastructure hostedInfrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.MacOS);
    Infrastructure vmInfrastructure = VmInitializeTaskHelper.getVMInfra(OSType.MacOS);

    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setIsDebug(true).build()).build();

    try {
      vmInitializeUtils.validateDebug(hostedInfrastructure, ambiance);
    } catch (Exception e) {
      fail("Debug should not be supported with mac");
    }

    try {
      vmInitializeUtils.validateDebug(vmInfrastructure, ambiance);
    } catch (Exception e) {
      fail("Debug should be supported with mac");
    }

    hostedInfrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.Linux);
    vmInfrastructure = VmInitializeTaskHelper.getVMInfra(OSType.Linux);

    try {
      boolean result = vmInitializeUtils.validateDebug(hostedInfrastructure, ambiance);
      assertThat(result).isEqualTo(true);
    } catch (Exception e) {
      fail("Debug should be supported with Linux");
    }

    try {
      boolean result = vmInitializeUtils.validateDebug(vmInfrastructure, ambiance);
      assertThat(result).isEqualTo(true);
    } catch (Exception e) {
      fail("Debug should be supported with Linux");
    }
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageName_withRealObjects_returnsNull() {
    Infrastructure infrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.Linux);
    String result = VmInitializeUtils.getImageName(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageName_withRealObjects_returnsImageName() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder()
                              .spec(CloudRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .imageName(ParameterField.createValueField("test-image-name"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();
    String result = VmInitializeUtils.getImageName(infrastructure);
    assertThat(result).isEqualTo("test-image-name");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageName_withNonHostedVmInfra_returnsNull() {
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    String result = VmInitializeUtils.getImageName(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetConnector_withRealObjects_returnsNull() {
    Infrastructure infrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.Linux);
    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetConnector_withRealObjects_returnsConnector() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder()
                              .spec(CloudRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .connectorRef(ParameterField.createValueField("test-connector"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isEqualTo("test-connector");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetConnector_withNonHostedVmInfra_returnsNull() {
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetConnector_withNullImageSpec_returnsNull() {
    // Create infrastructure with runtime but null imageSpec
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder().spec(CloudRuntimeSpec.builder().build()).build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetConnector_withNullConnectorValue_returnsNull() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(
                        Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                    .runtime(ParameterField.createValueField(
                        CloudRuntime.builder()
                            .spec(CloudRuntimeSpec.builder()
                                      .imageSpec(
                                          CloudRuntimeImageSpec.builder().connectorRef(ParameterField.ofNull()).build())
                                      .build())
                            .build()))
                    .build())
            .build();

    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageSize_withRealObjects_returnsNull() {
    Infrastructure infrastructure = VmInitializeTaskHelper.getHostedInfra(OSType.Linux);
    String result = VmInitializeUtils.getResourceClass(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageSize_withRealObjects_returnsSize() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder()
                              .spec(CloudRuntimeSpec.builder()
                                        .size(ParameterField.createValueField(CIResourceClass.LARGE))
                                        .build())
                              .build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getResourceClass(infrastructure);
    assertThat(result).isEqualTo("LARGE");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageSize_withNonHostedVmInfra_returnsNull() {
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    String result = VmInitializeUtils.getResourceClass(infrastructure);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageSpec_withDockerRuntime_returnsImageSpec() {
    DockerInfraYaml infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .runtime(ParameterField.createValueField(
                          DockerRuntime.builder()
                              .spec(DockerRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .imageName(ParameterField.createValueField("my-docker-image"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();

    Optional<CloudRuntimeImageSpec> result = VmInitializeUtils.getImageSpec(infrastructure);
    assertThat(result).isPresent();
    assertThat(result.get().getImageName().getValue()).isEqualTo("my-docker-image");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageSpec_withDockerRuntime_noImageSpec_returnsEmptyImageSpec() {
    DockerInfraYaml infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .runtime(ParameterField.createValueField(
                          DockerRuntime.builder().spec(DockerRuntimeSpec.builder().build()).build()))
                      .build())
            .build();

    Optional<CloudRuntimeImageSpec> result = VmInitializeUtils.getImageSpec(infrastructure);
    assertThat(result).isPresent();
    // imageSpec getter returns a default empty instance when null
    assertThat(result.get().getImageName()).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageSpec_withDockerInfra_noRuntime_returnsEmpty() {
    DockerInfraYaml infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .build())
            .build();

    Optional<CloudRuntimeImageSpec> result = VmInitializeUtils.getImageSpec(infrastructure);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageSpec_withCloudRuntime_stillWorks() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder()
                              .spec(CloudRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .imageName(ParameterField.createValueField("cloud-image"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();

    Optional<CloudRuntimeImageSpec> result = VmInitializeUtils.getImageSpec(infrastructure);
    assertThat(result).isPresent();
    assertThat(result.get().getImageName().getValue()).isEqualTo("cloud-image");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageSpec_withVmInfra_returnsEmpty() {
    Infrastructure infrastructure = VmInfraYaml.builder().build();
    Optional<CloudRuntimeImageSpec> result = VmInitializeUtils.getImageSpec(infrastructure);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetImageName_withDockerRuntime_returnsImageName() {
    DockerInfraYaml infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .runtime(ParameterField.createValueField(
                          DockerRuntime.builder()
                              .spec(DockerRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .imageName(ParameterField.createValueField("docker-img:v1"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getImageName(infrastructure);
    assertThat(result).isEqualTo("docker-img:v1");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetConnector_withDockerRuntime_returnsConnector() {
    DockerInfraYaml infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .runtime(ParameterField.createValueField(
                          DockerRuntime.builder()
                              .spec(DockerRuntimeSpec.builder()
                                        .imageSpec(CloudRuntimeImageSpec.builder()
                                                       .connectorRef(ParameterField.createValueField("docker-conn"))
                                                       .build())
                                        .build())
                              .build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getConnector(infrastructure);
    assertThat(result).isEqualTo("docker-conn");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetImageSize_withNullSizeValue_returnsNull() {
    HostedVmInfraYaml infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .runtime(ParameterField.createValueField(
                          CloudRuntime.builder()
                              .spec(CloudRuntimeSpec.builder().size(ParameterField.ofNull()).build())
                              .build()))
                      .build())
            .build();

    String result = VmInitializeUtils.getResourceClass(infrastructure);
    assertThat(result).isEqualTo("MEDIUM");
  }
}