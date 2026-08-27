/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.util.Lists.newArrayList;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.execution.utils.validation.ValidationUtilsImpl;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ValidationUtilsTest extends CIExecutionTestBase {
  @Inject ValidationUtilsImpl validationUtils;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock CIFeatureFlagServiceImpl ciFeatureFlagService;

  @Before
  public void setUp() {
    initMocks(this);
    on(validationUtils).set("ciFeatureFlagService", ciFeatureFlagService);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void validateWindowsK8Stage() {
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder().os(ParameterField.createValueField(OSType.Windows)).build())
            .build();
    ExecutionElementConfig executionElementConfig = getExecutionWrapperConfig();
    when(k8InitializeTaskUtils.getOS(infrastructure)).thenReturn(OSType.Windows);

    validationUtils.validateStage(executionElementConfig, infrastructure, "accountId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateMacHostedStageWithoutOOTB() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(false);
    ExecutionElementConfig executionElementConfigDocker = getExecutionWrapperConfig();
    validationUtils.validateHostedStage(executionElementConfigDocker, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigAction =
        ExecutionElementConfig.builder()
            .steps(newArrayList(ExecutionWrapperConfig.builder().step(getActionStepElementConfigAsJsonNode()).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigAction, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigArtifactory =
        ExecutionElementConfig.builder()
            .steps(newArrayList(
                ExecutionWrapperConfig.builder().step(getArtifactoryStepElementConfigAsJsonNode()).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigArtifactory, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigRun =
        ExecutionElementConfig.builder()
            .steps(
                newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode(false)).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigRun, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigRun1 =
        ExecutionElementConfig.builder()
            .steps(newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode(true)).build()))
            .build();
    assertThatThrownBy(() -> validationUtils.validateHostedStage(executionElementConfigRun1, OSType.MacOS, "accountId"))
        .isInstanceOf(CIStageExecutionException.class);

    ExecutionElementConfig executionElementConfigAction2 = getExecutionWrapperConfigWithInject();
    validationUtils.validateHostedStage(executionElementConfigAction2, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigAction3 = getExecutionWrapperConfigWithInjectWithOnlyRunStep();
    validationUtils.validateHostedStage(executionElementConfigAction3, OSType.MacOS, "accountId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateMacHostedStageWithOOTB() {
    when(ciFeatureFlagService.isEnabled(any(), any())).thenReturn(true);
    ExecutionElementConfig executionElementConfigDocker = getExecutionWrapperConfig();
    validationUtils.validateHostedStage(executionElementConfigDocker, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigAction =
        ExecutionElementConfig.builder()
            .steps(newArrayList(ExecutionWrapperConfig.builder().step(getActionStepElementConfigAsJsonNode()).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigAction, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigArtifactory =
        ExecutionElementConfig.builder()
            .steps(newArrayList(
                ExecutionWrapperConfig.builder().step(getArtifactoryStepElementConfigAsJsonNode()).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigArtifactory, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigRun =
        ExecutionElementConfig.builder()
            .steps(
                newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode(false)).build()))
            .build();
    validationUtils.validateHostedStage(executionElementConfigRun, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigRun1 =
        ExecutionElementConfig.builder()
            .steps(newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode(true)).build()))
            .build();
    assertThatThrownBy(() -> validationUtils.validateHostedStage(executionElementConfigRun1, OSType.MacOS, "accountId"))
        .isInstanceOf(CIStageExecutionException.class);

    ExecutionElementConfig executionElementConfigAction2 = getExecutionWrapperConfigWithInject();
    validationUtils.validateHostedStage(executionElementConfigAction2, OSType.MacOS, "accountId");

    ExecutionElementConfig executionElementConfigAction3 = getExecutionWrapperConfigWithInjectWithOnlyRunStep();
    validationUtils.validateHostedStage(executionElementConfigAction3, OSType.MacOS, "accountId");
  }

  @Test(expected = InvalidYamlException.class)
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageFailed1() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Windows))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();
    CloudRuntimeSpec spec =
        CloudRuntimeSpec.builder().nestedVirtualization(ParameterField.createValueField(true)).build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  @Test(expected = InvalidYamlException.class)
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageFailed2() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Arm64))
                            .build();
    CloudRuntimeSpec spec =
        CloudRuntimeSpec.builder().nestedVirtualization(ParameterField.createValueField(true)).build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageSuccess1() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();
    CloudRuntimeSpec spec =
        CloudRuntimeSpec.builder().nestedVirtualization(ParameterField.createValueField(true)).build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageSuccess2() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.MacOS))
                            .arch(ParameterField.createValueField(ArchType.Arm64))
                            .build();
    CloudRuntimeSpec spec =
        CloudRuntimeSpec.builder().nestedVirtualization(ParameterField.createValueField(false)).build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageSuccess3() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Windows))
                            .arch(ParameterField.createValueField(ArchType.Arm64))
                            .build();
    CloudRuntimeSpec spec = CloudRuntimeSpec.builder().build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPlatformValidationForHostedStageSuccess4() {
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Arm64))
                            .build();
    CloudRuntimeSpec spec = CloudRuntimeSpec.builder().build();
    CloudRuntime cloudRuntime = CloudRuntime.builder().spec(spec).build();
    ParameterField<Platform> platformParameterField = ParameterField.createValueField(platform);
    IntegrationStageConfigImpl integrationStageConfig =
        IntegrationStageConfigImpl.builder().platform(platformParameterField).runtime(cloudRuntime).build();
    validationUtils.validatePlatformForHostedStage(
        integrationStageConfig, platform.getOs().getValue(), platform.getArch().getValue());
  }

  private ExecutionElementConfig getExecutionWrapperConfig() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getDockerStepElementConfigAsJsonNode()).build());
    return ExecutionElementConfig.builder().steps(steps).build();
  }

  private ExecutionElementConfig getExecutionWrapperConfigWithInject() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().insert(getInjectWithSteps()).build());
    return ExecutionElementConfig.builder().steps(steps).build();
  }

  private ExecutionElementConfig getExecutionWrapperConfigWithInjectWithOnlyRunStep() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().insert(getInjectWithRunStep()).build());
    return ExecutionElementConfig.builder().steps(steps).build();
  }

  private JsonNode getDockerStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "docker");

    stepElementConfig.put("type", "BuildAndPushDockerRegistry");
    stepElementConfig.put("name", "docker");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getInjectWithSteps() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode inject = mapper.createObjectNode();
    inject.put("name", "insert");
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getDockerStepElementConfigAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("step", getArtifactoryStepElementConfigAsJsonNode()));
    inject.put("steps", arrayNode);
    return inject;
  }

  private JsonNode getInjectWithRunStep() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode inject = mapper.createObjectNode();
    inject.put("name", "insert");
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getRunStepElementConfigAsJsonNode(false)));
    inject.put("steps", arrayNode);
    return inject;
  }

  private JsonNode getArtifactoryStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "ArtifactoryUpload");

    stepElementConfig.put("type", "ArtifactoryUpload");
    stepElementConfig.put("name", "ArtifactoryUpload");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("connectorRef", "connector");
    stepSpecType.put("target", "target");
    stepSpecType.put("sourcePath", "sourcePath");
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getActionStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "action");

    stepElementConfig.put("type", "Action");
    stepElementConfig.put("name", "action");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("uses", "uses");
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getRunStepElementConfigAsJsonNode(boolean withContainer) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "run");

    stepElementConfig.put("type", "Run");
    stepElementConfig.put("name", "run");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("shell", "Sh");
    stepSpecType.put("command", "echo run");
    if (withContainer) {
      stepSpecType.put("image", "alpine");
      stepSpecType.put("connectorRef", "connector123");
    }

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }
}
