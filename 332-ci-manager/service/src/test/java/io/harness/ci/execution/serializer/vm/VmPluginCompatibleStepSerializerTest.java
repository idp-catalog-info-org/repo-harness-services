/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GARStepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.execution.execution.CIDockerLayerCachingConfigService;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.idp.steps.beans.stepinfo.IdpCookieCutterStepInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmPluginCompatibleStepSerializerTest {
  @Mock private PluginSettingUtils pluginSettingUtils;
  @Mock private CIDockerLayerCachingConfigService dockerLayerCachingConfigService;
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks private VmPluginCompatibleStepSerializer vmPluginStepSerializer;

  @Mock HarnessImageUtils harnessImageUtils;
  @Mock SerializerUtils serializerUtils;

  private static final String TEST_UUID_COOKIECUTTER = "test-cookie-cutter-uuid";
  private static final String TEST_IDENTIFIER_COOKIECUTTER = "test-cookie-cutter-identifier";
  private static final String TEST_NAME_COOKIECUTTER = "test-name-cookiecutter";
  private static final String TEST_PUBLIC_URL = "test-public-url";

  private static final String TEST_IMAGE_NAME = "test-image-name";

  private static final long TEST_TIMEOUT_VALUE = 10;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Maps.of(
            "accountId", "accountId", "projectIdentifier", "projectIdentfier", "orgIdentifier", "orgIdentifier"))
        .build();
  }

  private IdpCookieCutterStepInfo getCookieCutterStepInfo() {
    return IdpCookieCutterStepInfo.builder()
        .uuid(TEST_UUID_COOKIECUTTER)
        .identifier(TEST_IDENTIFIER_COOKIECUTTER)
        .name(TEST_NAME_COOKIECUTTER)
        .publicTemplateUrl(ParameterField.createValueField(TEST_PUBLIC_URL))
        .build();
  }

  private DockerStepInfo getDockerStepInfo() {
    return DockerStepInfo.builder()
        .repo(ParameterField.createValueField("harness"))
        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
        .dockerfile(ParameterField.createValueField("Dockerfile"))
        .context(ParameterField.createValueField("context"))
        .target(ParameterField.createValueField("target"))
        .build();
  }

  private ECRStepInfo getEcrStepInfo() {
    return ECRStepInfo.builder()
        .imageName(ParameterField.createValueField("harness"))
        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
        .dockerfile(ParameterField.createValueField("Dockerfile"))
        .context(ParameterField.createValueField("context"))
        .target(ParameterField.createValueField("target"))
        .build();
  }

  private CIDockerLayerCachingConfig getDlcConfig() {
    return CIDockerLayerCachingConfig.builder()
        .endpoint("endpoint")
        .bucket("bucket")
        .accessKey("access_key")
        .secretKey("secret_key")
        .region("region")
        .build();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcEnabled() {
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;
    CIDockerLayerCachingConfig config = getDlcConfig();

    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any(), any())).thenReturn(config);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, dockerStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList)
        .contains("endpoint")
        .contains("bucket")
        .contains("access_key")
        .contains("secret_key")
        .contains("region");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcDisabled() {
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(false);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, dockerStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerConfigNull() {
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = getDockerStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any(), any())).thenReturn(null);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, dockerStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcEnabledEcr() {
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;
    CIDockerLayerCachingConfig config = getDlcConfig();

    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any(), any())).thenReturn(config);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, ecrStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList)
        .contains("endpoint")
        .contains("bucket")
        .contains("access_key")
        .contains("secret_key")
        .contains("region");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerDlcDisabledEcr() {
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(false);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, ecrStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPluginStepSerializerDockerConfigNullEcr() {
    Ambiance ambiance = getAmbiance();
    ECRStepInfo ecrStepInfo = getEcrStepInfo();
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.DLITE_VM;

    when(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).thenReturn(true);
    when(dockerLayerCachingConfigService.getDockerLayerCachingConfig(any(), any())).thenReturn(null);

    Set<String> secretList = vmPluginStepSerializer.preProcessStep(
        ambiance, ecrStepInfo, stageInfraDetails, "identifier", OSType.Linux.toString());
    assertThat(secretList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSerializeByExcludingConnector() {
    Map<String, String> envVariables = new HashMap<>();
    String testEnvName = "testEnvName";
    String testEnvValue = "testEnvValue";
    envVariables.put(testEnvName, testEnvValue);
    StageInfraDetails stageInfraDetails = () -> StageInfraDetails.Type.K8;

    when(pluginSettingUtils.getPluginCompatibleEnvVariables(
             any(), any(), anyLong(), any(), any(), anyBoolean(), anyBoolean(), eq(OSType.MacOS)))
        .thenReturn(envVariables);
    when(serializerUtils.getStepStatusEnvVars(any())).thenReturn(envVariables);

    try (MockedStatic<TimeoutUtils> timeoutUtils = Mockito.mockStatic(TimeoutUtils.class);
         MockedStatic<CIStepInfoUtils> ciStepInfoUtils = Mockito.mockStatic(CIStepInfoUtils.class);
         MockedStatic<IntegrationStageUtility> integrationStageUtility =
             Mockito.mockStatic(IntegrationStageUtility.class)) {
      timeoutUtils
          .when(() -> TimeoutUtils.getTimeoutInSeconds((ParameterField<Timeout>) Mockito.any(), Mockito.anyLong()))
          .thenReturn(TEST_TIMEOUT_VALUE);

      ciStepInfoUtils.when(() -> CIStepInfoUtils.getPluginCustomStepImage(any(), any(), any(), any()))
          .thenReturn(TEST_IMAGE_NAME);

      integrationStageUtility.when(() -> IntegrationStageUtility.getFullyQualifiedImageName(any(), any()))
          .thenReturn(TEST_IMAGE_NAME);

      when(harnessImageUtils.getHarnessImageConnectorDetailsForVM(any(), any()))
          .thenReturn(ConnectorDetails.builder().build());

      HostedVmInfraYaml infra =
          HostedVmInfraYaml.builder()
              .type(Infrastructure.Type.HOSTED_VM)
              .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                        .platform(ParameterField.createValueField(
                            Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                        .build())
              .build();

      VmPluginStep vmPluginStep = (VmPluginStep) vmPluginStepSerializer.serializeByExcludingConnector(getAmbiance(),
          getCookieCutterStepInfo(), stageInfraDetails, TEST_IDENTIFIER_COOKIECUTTER,
          ParameterField.createValueField(Timeout.builder().build()), TEST_NAME_COOKIECUTTER, infra);

      assertNull(vmPluginStep.getConnector());
      assertEquals(testEnvValue, vmPluginStep.getEnvVariables().get(testEnvName));

      // containerless plugin steps
      ciStepInfoUtils.when(() -> CIStepInfoUtils.canRunVmStepOnHost(any(), any(), any(), any(), any(), any()))
          .thenReturn(true);

      when(pluginSettingUtils.getPluginCompatibleEnvVariables(
               any(), any(), anyLong(), any(), any(), anyBoolean(), anyBoolean(), eq(OSType.Linux)))
          .thenReturn(envVariables);

      when(ciExecutionConfigService.getContainerlessPluginNameForVM(any(), any()))
          .thenReturn(ContainerlessPluginConfig.builder().name("testName").build());

      VmRunStep vmRunStep = (VmRunStep) vmPluginStepSerializer.serializeByExcludingConnector(getAmbiance(),
          getCookieCutterStepInfo(), stageInfraDetails, TEST_IDENTIFIER_COOKIECUTTER,
          ParameterField.createValueField(Timeout.builder().build()), TEST_NAME_COOKIECUTTER, infra);

      assertNull(vmRunStep.getConnector());
      assertEquals(testEnvValue, vmRunStep.getEnvVariables().get(testEnvName));
    }
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateStepConfigForArtifactoryContainerlessLocal() {
    // testing artifactory step in local infra: with runner & containerless enabled
    UploadToArtifactoryStepInfo stepInfo = UploadToArtifactoryStepInfo.builder().build();
    ContainerlessPluginConfig stepConfig =
        ContainerlessPluginConfig.builder().name("jfrog").binarySuffix("tar.gz").disableClone(false).build();
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder()
                                                .type(StageInfraDetails.Type.VM)
                                                .infraInfo(CIInitializeTaskParams.Type.DOCKER)
                                                .routeToRunner(true)
                                                .isContainerLessWithRunner(true)
                                                .build();
    ContainerlessPluginConfig result = vmPluginStepSerializer.updateStepConfigForArtifactoryContainerlessLocal(
        stepInfo, stepConfig, stageInfraDetails);
    assertThat(result.isDisableClone()).isNotEqualTo(stepConfig.isDisableClone());
    assertThat(result.isDisableClone()).isTrue();
    assertThat(result.getName()).isEqualTo(stepConfig.getName());
    assertThat(result.getBinarySuffix()).isEqualTo(stepConfig.getBinarySuffix());

    // testing non artifactory step in local infra: with runner & containerless enabled
    GARStepInfo garStepInfo = GARStepInfo.builder().build();
    ContainerlessPluginConfig garStepConfig =
        ContainerlessPluginConfig.builder().name("gar").binarySuffix("tar.gz").disableClone(false).build();
    result = vmPluginStepSerializer.updateStepConfigForArtifactoryContainerlessLocal(
        garStepInfo, garStepConfig, stageInfraDetails);
    assertThat(result.isDisableClone()).isEqualTo(garStepConfig.isDisableClone());

    // testing artifactory step in local infra: with delegate
    stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.DOCKER).routeToRunner(false).build();
    result = vmPluginStepSerializer.updateStepConfigForArtifactoryContainerlessLocal(
        stepInfo, stepConfig, stageInfraDetails);
    assertThat(result.isDisableClone()).isEqualTo(garStepConfig.isDisableClone());

    // testing artifactory step in local infra: with runner & containerless disabled
    stageInfraDetails = VmStageInfraDetails.builder()
                            .infraInfo(CIInitializeTaskParams.Type.DOCKER)
                            .routeToRunner(true)
                            .isContainerLessWithRunner(false)
                            .build();
    result = vmPluginStepSerializer.updateStepConfigForArtifactoryContainerlessLocal(
        stepInfo, stepConfig, stageInfraDetails);
    assertThat(result.isDisableClone()).isEqualTo(garStepConfig.isDisableClone());

    // testing artifactory step in VM infra
    stageInfraDetails = VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    result = vmPluginStepSerializer.updateStepConfigForArtifactoryContainerlessLocal(
        stepInfo, stepConfig, stageInfraDetails);
    assertThat(result.isDisableClone()).isEqualTo(garStepConfig.isDisableClone());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetContainerizedStepWithRunAsUser() throws Exception {
    // Test that the runAsUser field is properly set in VmPluginStep
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .runAsUser(ParameterField.createValueField(1000))
                                        .build();
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    ConnectorDetails imageConnector = ConnectorDetails.builder().build();
    Map<String, String> envVars = new HashMap<>();
    long timeout = 600;

    // Use reflection to call the private method getContainerizedStep
    Method method = VmPluginCompatibleStepSerializer.class.getDeclaredMethod("getContainerizedStep", Ambiance.class,
        io.harness.beans.steps.stepinfo.PluginCompatibleStep.class, StageInfraDetails.class, ConnectorDetails.class,
        Map.class, long.class, io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.class);
    method.setAccessible(true);

    VmPluginStep vmPluginStep = (VmPluginStep) method.invoke(
        vmPluginStepSerializer, ambiance, dockerStepInfo, stageInfraDetails, imageConnector, envVars, timeout, null);

    // Verify that runAsUser is set correctly
    assertThat(vmPluginStep.getRunAsUser()).isNotNull();
    assertThat(vmPluginStep.getRunAsUser()).isEqualTo("1000");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetContainerizedStepWithNullRunAsUser() throws Exception {
    // Test that the runAsUser field is null when not provided
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder().repo(ParameterField.createValueField("harness")).build();
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    ConnectorDetails imageConnector = ConnectorDetails.builder().build();
    Map<String, String> envVars = new HashMap<>();
    long timeout = 600;

    // Use reflection to call the private method getContainerizedStep
    Method method = VmPluginCompatibleStepSerializer.class.getDeclaredMethod("getContainerizedStep", Ambiance.class,
        io.harness.beans.steps.stepinfo.PluginCompatibleStep.class, StageInfraDetails.class, ConnectorDetails.class,
        Map.class, long.class, io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.class);
    method.setAccessible(true);

    VmPluginStep vmPluginStep = (VmPluginStep) method.invoke(
        vmPluginStepSerializer, ambiance, dockerStepInfo, stageInfraDetails, imageConnector, envVars, timeout, null);

    // Verify that runAsUser is null when not provided
    assertThat(vmPluginStep.getRunAsUser()).isNull();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetContainerizedStepWithStageRunAsUserOverride() throws Exception {
    // Test that stage-level runAsUser is used when step-level is not provided
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder().repo(ParameterField.createValueField("harness")).build();
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().runAsUser(2000).infraInfo(CIInitializeTaskParams.Type.VM).build();
    ConnectorDetails imageConnector = ConnectorDetails.builder().build();
    Map<String, String> envVars = new HashMap<>();
    long timeout = 600;

    try (MockedStatic<SerializerUtils> mockedStatic = Mockito.mockStatic(SerializerUtils.class)) {
      mockedStatic.when(() -> SerializerUtils.resolveRunAsUser(any(), any())).thenReturn("2000");

      // Use reflection to call the private method getContainerizedStep
      Method method = VmPluginCompatibleStepSerializer.class.getDeclaredMethod("getContainerizedStep", Ambiance.class,
          io.harness.beans.steps.stepinfo.PluginCompatibleStep.class, StageInfraDetails.class, ConnectorDetails.class,
          Map.class, long.class, io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.class);
      method.setAccessible(true);

      VmPluginStep vmPluginStep = (VmPluginStep) method.invoke(
          vmPluginStepSerializer, ambiance, dockerStepInfo, stageInfraDetails, imageConnector, envVars, timeout, null);

      // Verify that stage-level runAsUser is used
      assertThat(vmPluginStep.getRunAsUser()).isEqualTo("2000");
    }
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetContainerizedStepStepLevelOverridesStageLevel() throws Exception {
    // Test that step-level runAsUser takes precedence over stage-level
    Ambiance ambiance = getAmbiance();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .runAsUser(ParameterField.createValueField(1000))
                                        .build();
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().runAsUser(2000).infraInfo(CIInitializeTaskParams.Type.VM).build();
    ConnectorDetails imageConnector = ConnectorDetails.builder().build();
    Map<String, String> envVars = new HashMap<>();
    long timeout = 600;

    try (MockedStatic<SerializerUtils> mockedStatic = Mockito.mockStatic(SerializerUtils.class)) {
      mockedStatic.when(() -> SerializerUtils.resolveRunAsUser(any(), any())).thenReturn("1000");

      // Use reflection to call the private method getContainerizedStep
      Method method = VmPluginCompatibleStepSerializer.class.getDeclaredMethod("getContainerizedStep", Ambiance.class,
          io.harness.beans.steps.stepinfo.PluginCompatibleStep.class, StageInfraDetails.class, ConnectorDetails.class,
          Map.class, long.class, io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.class);
      method.setAccessible(true);

      VmPluginStep vmPluginStep = (VmPluginStep) method.invoke(
          vmPluginStepSerializer, ambiance, dockerStepInfo, stageInfraDetails, imageConnector, envVars, timeout, null);

      // Verify that step-level runAsUser takes precedence
      assertThat(vmPluginStep.getRunAsUser()).isEqualTo("1000");
    }
  }
}
