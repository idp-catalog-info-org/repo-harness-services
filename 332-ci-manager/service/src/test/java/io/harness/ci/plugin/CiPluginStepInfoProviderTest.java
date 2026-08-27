/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeStepUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.rule.Owner;
import io.harness.ssca.client.SSCAServiceUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@Slf4j
@OwnedBy(CDP)
public class CiPluginStepInfoProviderTest extends CIExecutionTestBase {
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks @Spy private K8InitializeStepUtils k8InitializeStepUtils;
  @Spy private PluginSettingUtils pluginSettingUtils;
  @InjectMocks private CiPluginStepInfoProvider ciPluginStepInfoProvider;
  @Mock private SSCAServiceUtils sscaServiceUtils;

  @Before
  public void setUp() {}

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPluginInfo() {
    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          runAsUser: 1\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);

    assertThat(pluginInfo.getStepInfo().toString())
        .isEqualTo(""
            + "name: \"Run_1\"\n"
            + "identifier: \"Run_1\"\n"
            + "uuid: \"uuid\"\n");
    assertThat(pluginInfo.getResponse().toString())
        .isEqualTo(""
            + "pluginDetails {\n"
            + "  resource {\n"
            + "    cpu: 400\n"
            + "    memory: 500\n"
            + "  }\n"
            + "  imageDetails {\n"
            + "    connectorDetails {\n"
            + "      connectorRef: \"account.harnessImage\"\n"
            + "    }\n"
            + "    imageInformation {\n"
            + "      imageName {\n"
            + "        value: \"alpine\"\n"
            + "      }\n"
            + "      imagePullPolicy {\n"
            + "        value: \"Always\"\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "  privileged: true\n"
            + "  runAsUser: 1\n"
            + "  totalPortUsedDetails {\n"
            + "    usedPorts: 1234\n"
            + "    usedPorts: 20002\n"
            + "  }\n"
            + "  portUsed: 20002\n"
            + "  isHarnessManaged {\n"
            + "  }\n"
            + "  runAsUserV1 {\n"
            + "    value: 1\n"
            + "  }\n"
            + "}\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPluginInfoPluginCompatibleStep() {
    doReturn(StepImageConfig.builder()
                 .image("path/to/artifact")
                 .entrypoint(List.of("endpoint"))
                 .windowsEntrypoint(List.of("winEndpoint"))
                 .build())
        .when(ciExecutionConfigService)
        .getPluginVersionForK8(any(CIStepInfoType.class), any(), anyString());
    doReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build())
        .when(ciExecutionConfigService)
        .getCiExecutionServiceConfig();
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    String containerRunStepNode = ""
        + "                  __uuid: uuid\n"
        + "                  type: ArtifactoryUpload\n"
        + "                  name: ArtifactoryUpload_1\n"
        + "                  identifier: ArtifactoryUpload_1\n"
        + "                  spec:\n"
        + "                    connectorRef: artifactory\n"
        + "                    target: groupId/artifactId/version\n"
        + "                    sourcePath: path/to/artifact\n"
        + "                    runAsUser: \"1\"\n"
        + "                    resources:\n"
        + "                      limits:\n"
        + "                        memory: 1G\n"
        + "                        cpu: 400m\n"
        + "                  timeout: 10m";

    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);

    assertThat(pluginInfo.getStepInfo().toString())
        .isEqualTo(""
            + "name: \"ArtifactoryUpload_1\"\n"
            + "identifier: \"ArtifactoryUpload_1\"\n"
            + "uuid: \"uuid\"\n");
    assertThat(pluginInfo.getResponse().toString())
        .isEqualTo(""
            + "pluginDetails {\n"
            + "  resource {\n"
            + "    cpu: 400\n"
            + "    memory: 954\n"
            + "  }\n"
            + "  imageDetails {\n"
            + "    connectorDetails {\n"
            + "    }\n"
            + "    imageInformation {\n"
            + "      imageName {\n"
            + "        value: \"path/to/artifact\"\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "  envVariables {\n"
            + "    key: \"PLUGIN_ARTIFACT_FILE\"\n"
            + "    value: \"/addon/tmp/.plugin/artifact\"\n"
            + "  }\n"
            + "  envVariables {\n"
            + "    key: \"PLUGIN_FLAT\"\n"
            + "    value: \"true\"\n"
            + "  }\n"
            + "  envVariables {\n"
            + "    key: \"PLUGIN_SOURCE\"\n"
            + "    value: \"path/to/artifact\"\n"
            + "  }\n"
            + "  envVariables {\n"
            + "    key: \"PLUGIN_TARGET\"\n"
            + "    value: \"groupId/artifactId/version\"\n"
            + "  }\n"
            + "  privileged: true\n"
            + "  runAsUser: 1\n"
            + "  totalPortUsedDetails {\n"
            + "    usedPorts: 1234\n"
            + "    usedPorts: 20002\n"
            + "  }\n"
            + "  portUsed: 20002\n"
            + "  connectors_for_step {\n"
            + "    connectorRef: \"artifactory\"\n"
            + "    connector_secret_env_map {\n"
            + "      key: \"ARTIFACTORY_ENDPOINT\"\n"
            + "      value: \"PLUGIN_URL\"\n"
            + "    }\n"
            + "    connector_secret_env_map {\n"
            + "      key: \"ARTIFACTORY_PASSWORD\"\n"
            + "      value: \"PLUGIN_PASSWORD\"\n"
            + "    }\n"
            + "    connector_secret_env_map {\n"
            + "      key: \"ARTIFACTORY_USERNAME\"\n"
            + "      value: \"PLUGIN_USERNAME\"\n"
            + "    }\n"
            + "  }\n"
            + "  isHarnessManaged {\n"
            + "    value: true\n"
            + "  }\n"
            + "  runAsUserV1 {\n"
            + "    value: 1\n"
            + "  }\n"
            + "}\n");
  }
  @Test(expected = ContainerPluginParseException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoPluginCompatibleStepWhenStepNotCompatible() {
    doReturn(StepImageConfig.builder()
                 .image("path/to/artifact")
                 .entrypoint(List.of("endpoint"))
                 .windowsEntrypoint(List.of("winEndpoint"))
                 .build())
        .when(ciExecutionConfigService)
        .getPluginVersionForK8(any(CIStepInfoType.class), any(), anyString());
    doReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build())
        .when(ciExecutionConfigService)
        .getCiExecutionServiceConfig();
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    String containerRunStepNode = ""
        + "                  __uuid: uuid\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    executionTarget: {}\n"
        + "                    source:\n"
        + "                      spec:\n"
        + "                        script: id\n"
        + "                      type: Inline\n"
        + "                  environmentVariables: []\n"
        + "                  outputVariables: []\n"
        + "                  timeout: 10m";

    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSscaEnvVarsPopulation() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
    Map<String, String> envMapWithSecrets = new HashMap<>();
    envMapWithSecrets.put("SSCA_SERVICE_ENDPOINT", "localhost");
    envMapWithSecrets.put("SSCA_SERVICE_TOKEN", "token");
    when(sscaServiceUtils.getSSCAServiceEnvVariables("accountId")).thenReturn(envMapWithSecrets);
    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          runAsUser: 1\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);

    pluginInfo = ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testRunAsUserV1AndPrivilegedV1Populated() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
    Map<String, String> envMapWithSecrets = new HashMap<>();
    envMapWithSecrets.put("SSCA_SERVICE_ENDPOINT", "localhost");
    envMapWithSecrets.put("SSCA_SERVICE_TOKEN", "token");
    when(sscaServiceUtils.getSSCAServiceEnvVariables("accountId")).thenReturn(envMapWithSecrets);
    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          runAsUser: 1\n"
        + "                          privileged: true\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);

    pluginInfo = ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasRunAsUserV1()).isEqualTo(true);
    assertThat(pluginInfo.getResponse().getPluginDetails().getRunAsUserV1().getValue()).isEqualTo(1);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasPrivilegedV1()).isEqualTo(true);
    assertThat(pluginInfo.getResponse().getPluginDetails().getPrivilegedV1().getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testRunAsUserV1Populated() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
    Map<String, String> envMapWithSecrets = new HashMap<>();
    envMapWithSecrets.put("SSCA_SERVICE_ENDPOINT", "localhost");
    envMapWithSecrets.put("SSCA_SERVICE_TOKEN", "token");
    when(sscaServiceUtils.getSSCAServiceEnvVariables("accountId")).thenReturn(envMapWithSecrets);
    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          runAsUser: 1\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);

    pluginInfo = ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasRunAsUserV1()).isEqualTo(true);
    assertThat(pluginInfo.getResponse().getPluginDetails().getRunAsUserV1().getValue()).isEqualTo(1);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasPrivilegedV1()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPrivilegedV1Populated() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
    Map<String, String> envMapWithSecrets = new HashMap<>();
    envMapWithSecrets.put("SSCA_SERVICE_ENDPOINT", "localhost");
    envMapWithSecrets.put("SSCA_SERVICE_TOKEN", "token");
    when(sscaServiceUtils.getSSCAServiceEnvVariables("accountId")).thenReturn(envMapWithSecrets);
    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          privileged: true\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(containerRunStepNode).setOsType("Linux").build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);

    pluginInfo = ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);
    verify(sscaServiceUtils, times(1)).getSSCAServiceEnvVariables("accountId");
    assertThat(pluginInfo.getResponse().getPluginDetails().getEnvVariablesWithPlainTextSecretMap())
        .isEqualTo(envMapWithSecrets);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasRunAsUserV1()).isEqualTo(false);
    assertThat(pluginInfo.getResponse().getPluginDetails().hasPrivilegedV1()).isEqualTo(true);
    assertThat(pluginInfo.getResponse().getPluginDetails().getPrivilegedV1().getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithHarCdRunStepFlagEnabled() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.HAR_CD_RUN_STEP, "accountId")).thenReturn(true);

    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: echo 'test'\n"
        + "                          registryRef: account.myRegistry\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder()
                                                      .setStepJsonNode(containerRunStepNode)
                                                      .setOsType("Linux")
                                                      .setAccountId("accountId")
                                                      .build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);

    assertThat(pluginInfo.getResponse().getPluginDetails().getImageDetails().getConnectorDetails().getRegistryRef())
        .isEqualTo("account.myRegistry");
    assertThat(pluginInfo.getResponse().getPluginDetails().getImageDetails().getConnectorDetails().getConnectorRef())
        .isEqualTo("account.harnessImage");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithHarCdRunStepFlagDisabled() {
    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);

    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: echo 'test'\n"
        + "                          registryRef: account.myRegistry\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder()
                                                      .setStepJsonNode(containerRunStepNode)
                                                      .setOsType("Linux")
                                                      .setAccountId("accountId")
                                                      .build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);

    assertThat(pluginInfo.getResponse().getPluginDetails().getImageDetails().getConnectorDetails().getRegistryRef())
        .isEmpty();
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithHarCdRunStepFlagEnabledNoRegistryRef() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    when(ciExecutionConfigService.getCiExecutionServiceConfig())
        .thenReturn(CIExecutionServiceConfig.builder().defaultCPULimit(500).build());
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.HAR_CD_RUN_STEP, "accountId")).thenReturn(true);

    String containerRunStepNode = ""
        + "                        __uuid: uuid\n"
        + "                        type: Run\n"
        + "                        name: Run_1\n"
        + "                        identifier: Run_1\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: echo 'test'\n"
        + "                          imagePullPolicy: Always\n"
        + "                          resources:\n"
        + "                            limits:\n"
        + "                              memory: 500Mi\n"
        + "                              cpu: 400m";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder()
                                                      .setStepJsonNode(containerRunStepNode)
                                                      .setOsType("Linux")
                                                      .setAccountId("accountId")
                                                      .build();

    PluginCreationResponseWrapper pluginInfo =
        ciPluginStepInfoProvider.getPluginInfo(pluginCreationRequest, Set.of(1234), ambiance);

    assertThat(pluginInfo.getResponse().getPluginDetails().getImageDetails().getConnectorDetails().getConnectorRef())
        .isEqualTo("account.harnessImage");
    assertThat(pluginInfo.getResponse().getPluginDetails().getImageDetails().getConnectorDetails().getRegistryRef())
        .isEmpty();
  }
}
