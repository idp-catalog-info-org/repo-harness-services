/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_ARCHIVE_TYPE_TAR;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_HARNESS_BACKEND;
import static io.harness.common.NGExpressionUtils.EMPTY;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DLITE_VM;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DOCKER;
import static io.harness.delegate.task.citasks.cik8handler.params.CIConstants.PLUGIN_BINARIES_GCS_PATH;
import static io.harness.delegate.task.citasks.cik8handler.params.CIConstants.SEMI_COLON;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CacheServiceConfig;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.ci.cacheserviceclient.CacheServiceUtils;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class SerializerUtilTest extends CategoryTest {
  @Mock private CIFeatureFlagService featureFlagService;

  @Mock private ConnectorUtils connectorUtils;
  @InjectMocks private SerializerUtils serializerUtils;
  @Mock private CacheServiceUtils cacheServiceUtils;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testHarnessBackendForSaveCacheIntelligence() {
    when(featureFlagService.isEnabled(FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL, "accountId")).thenReturn(true);
    when(cacheServiceUtils.getCacheServiceConfig()).thenReturn(CacheServiceConfig.builder().build());
    Map<String, String> envMap = new HashMap<>();
    serializerUtils.setHostedCacheEnvironments(ambiance, envMap, null);
    assertThat(envMap.containsKey("PLUGIN_CACHE_SERVICE_BEARER_TOKEN")).isEqualTo(true);
    assertThat(envMap.get("PLUGIN_BACKEND")).isEqualTo(CACHE_HARNESS_BACKEND);
    assertThat(envMap.containsKey("PLUGIN_CACHE_SERVICE_BASE_URL")).isEqualTo(true);
    assertThat(envMap.get("PLUGIN_ARCHIVE_FORMAT")).isEqualTo(CACHE_ARCHIVE_TYPE_TAR);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testHarnessBackendForRestoreCacheIntelligence() {
    when(featureFlagService.isEnabled(FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL, "accountId")).thenReturn(true);
    when(cacheServiceUtils.getCacheServiceConfig()).thenReturn(CacheServiceConfig.builder().build());
    Map<String, String> envMap = new HashMap<>();
    serializerUtils.setHostedCacheEnvironments(ambiance, envMap, null);
    assertThat(envMap.containsKey("PLUGIN_CACHE_SERVICE_BEARER_TOKEN")).isEqualTo(true);
    assertThat(envMap.get("PLUGIN_BACKEND")).isEqualTo(CACHE_HARNESS_BACKEND);
    assertThat(envMap.containsKey("PLUGIN_CACHE_SERVICE_BASE_URL")).isEqualTo(true);
    assertThat(envMap.get("PLUGIN_ARCHIVE_FORMAT")).isEqualTo(CACHE_ARCHIVE_TYPE_TAR);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetImagePullPolicyForDockerInfra() {
    ParameterField<ImagePullPolicy> pullPolicyParameterField = ParameterField.createValueField(ImagePullPolicy.ALWAYS);
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().infraInfo(DOCKER).build();
    String pullPolicy =
        SerializerUtils.getImagePullPolicy(ambiance, pullPolicyParameterField, stageInfraDetails, featureFlagService);
    assertThat(pullPolicy).isEqualTo("Always");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetImagePullPolicyForDLiteInfraWithFFDisabled() {
    ParameterField<ImagePullPolicy> pullPolicyParameterField = ParameterField.createValueField(ImagePullPolicy.ALWAYS);
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().infraInfo(DLITE_VM).build();
    String pullPolicy =
        SerializerUtils.getImagePullPolicy(ambiance, pullPolicyParameterField, stageInfraDetails, featureFlagService);
    assertThat(pullPolicy).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetImagePullPolicyForDLiteInfraWithFFEnabled() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_IMAGE_PULL_POLICY_VM, "accountId")).thenReturn(true);
    ParameterField<ImagePullPolicy> pullPolicyParameterField = ParameterField.createValueField(ImagePullPolicy.ALWAYS);
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().infraInfo(DLITE_VM).build();
    String pullPolicy =
        SerializerUtils.getImagePullPolicy(ambiance, pullPolicyParameterField, stageInfraDetails, featureFlagService);
    assertThat(pullPolicy).isEqualTo("Always");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testImageFQNForPluginOrBackgroundStep() {
    String imageName = "testRepo:latest";
    String connectorIdentifier = "docker-connector";
    DockerConnectorDTO dockerConnectorDTO =
        DockerConnectorDTO.builder().dockerRegistryUrl("https://docker.private.registry").build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder().connectorType(ConnectorType.DOCKER).connectorConfig(dockerConnectorDTO).build();
    when(connectorUtils.getConnectorDetails(any(), eq("docker-connector"))).thenReturn(connectorDetails);

    // when ff is disabled
    String fqn = serializerUtils.checkAndGetFullyQualifiedName(connectorIdentifier, imageName, ambiance);
    assertThat(fqn).isEqualTo("testRepo:latest");

    when(featureFlagService.isEnabled(eq(FeatureName.CI_REMOVE_FQN_DEPENDENCY), any())).thenReturn(true);

    // when registry is private
    fqn = serializerUtils.checkAndGetFullyQualifiedName(connectorIdentifier, imageName, ambiance);
    assertThat(fqn).isEqualTo("docker.private.registry/testRepo:latest");

    // when the registry is docker hub
    dockerConnectorDTO.setDockerRegistryUrl("https://index.docker.io/v2/");
    fqn = serializerUtils.checkAndGetFullyQualifiedName(connectorIdentifier, imageName, ambiance);
    assertThat(fqn).isEqualTo("testRepo:latest");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetHostedStepEntrypoint() {
    StageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    String pluginName = "github.com/drone-plugins/drone-s3@refs/tags/v1.5.3";
    ContainerlessPluginConfig config = ContainerlessPluginConfig.builder()
                                           .name("github.com/drone-plugins/drone-s3@refs/tags/v1.5.3")
                                           .disableClone(true)
                                           .binarySuffix("drone-s3-{{ os }}-{{ arch }}.zst")
                                           .build();

    List<String> result2 = serializerUtils.getHostedStepEntrypoint(config, stageInfraDetails);
    assertThat(result2).contains("plugin", "-kind", "harness", "-name", pluginName, "-disable-clone", "-sources");
    // Verify that sources contains both primary and fallback URLs
    String expectedSource = result2.get(result2.size() - 1);
    assertThat(expectedSource)
        .contains("https://github.com/drone-plugins/drone-s3/releases/download/{{ release }}/drone-s3-{{ os }}-{{ arch "
            + "}}.zst");
    assertThat(expectedSource)
        .contains("https://app.harness.io/storage/harness-download/harness-ti/drone-s3/{{ release }}/drone-s3-{{ os "
            + "}}-{{ arch }}.zst");

    // Test with disabled clone but no binary suffix
    config = ContainerlessPluginConfig.builder().name(pluginName).disableClone(true).build();
    List<String> result3 = serializerUtils.getHostedStepEntrypoint(config, stageInfraDetails);
    assertThat(result3).containsExactly("plugin", "-kind", "harness", "-name", pluginName, "-disable-clone");

    // Test with binary suffix enabled clone
    config =
        ContainerlessPluginConfig.builder().name(pluginName).binarySuffix("drone-s3-{{ os }}-{{ arch }}.zst").build();
    List<String> result4 = serializerUtils.getHostedStepEntrypoint(config, stageInfraDetails);

    assertThat(result4).contains("plugin", "-kind", "harness", "-name", pluginName, "-sources");
    // Verify that sources contains both primary and fallback URLs
    expectedSource = result4.get(result4.size() - 1);
    assertThat(expectedSource)
        .contains("https://github.com/drone-plugins/drone-s3/releases/download/{{ release }}/drone-s3-{{ os }}-{{ arch "
            + "}}.zst");
    assertThat(expectedSource)
        .contains("https://app.harness.io/storage/harness-download/harness-ti/drone-s3/{{ release }}/drone-s3-{{ os "
            + "}}-{{ arch }}.zst");
    stageInfraDetails = VmStageInfraDetails.builder().build();
    List<String> result5 = serializerUtils.getHostedStepEntrypoint(config, stageInfraDetails);
    assertThat(result5).containsExactly("plugin", "-kind", "harness", "-name", pluginName);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGenerateBinarySources() {
    String pluginName = "github.com/drone-plugins/drone-s3@refs/tags/v1.5.3";
    String binarySuffix = "drone-s3-{{ os }}-{{ arch }}.zst";
    String expectedGithubSource =
        "https://github.com/drone-plugins/drone-s3/releases/download/{{ release }}/drone-s3-{{ os }}-{{ arch }}.zst";
    String expectedHarnessSource = "https://app.harness.io/storage/harness-download/harness-ti/drone-s3/{{ release "
        + "}}/drone-s3-{{ os }}-{{ arch }}.zst";
    // Test with valid input - app.harness.io should be primary, GitHub should be fallback
    String result1 = serializerUtils.generateBinarySources(pluginName, binarySuffix);
    assertThat(result1).isEqualTo(expectedHarnessSource + SEMI_COLON + expectedGithubSource);

    // Test with empty name
    String result2 = serializerUtils.generateBinarySources("", binarySuffix);
    assertThat(result2).isEqualTo(EMPTY);

    // Test with invalid name format (no @)
    String result3 = serializerUtils.generateBinarySources("org/repo", binarySuffix);
    assertThat(result3).isEqualTo(EMPTY);

    // Test with null name
    String result4 = serializerUtils.generateBinarySources(null, binarySuffix);
    assertThat(result4).isEqualTo(EMPTY);
  }
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGenerateBinarySourcesCacheProxy() {
    String pluginName = "org/wings-software/cache-proxy@github";
    String binarySuffix = "cache-proxy-linux-amd64.zst";
    String expectedFallback =
        String.format(PLUGIN_BINARIES_GCS_PATH + "%s/{{ release }}/%s", "cache-proxy", binarySuffix);

    String result = serializerUtils.generateBinarySources(pluginName, binarySuffix);
    assertThat(result).isEqualTo(expectedFallback);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetHostedStepEntrypointForRunnerContainerless() {
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().isContainerLessWithRunner(true).build();
    String pluginName = "github.com/drone-plugins/drone-gcs@refs/tags/v1.5.3";
    ContainerlessPluginConfig config = ContainerlessPluginConfig.builder()
                                           .name("github.com/drone-plugins/drone-gcs@refs/tags/v1.5.3")
                                           .disableClone(true)
                                           .binarySuffix("drone-s3-{{ os }}-{{ arch }}.zst")
                                           .build();
    List<String> result1 = serializerUtils.getHostedStepEntrypoint(config, stageInfraDetails);
    assertThat(result1).contains("plugin", "-kind", "harness", "-name", "-disable-clone", "-sources");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetRepoNameAndVersion() {
    // valid plugin identifier
    String plugin = "github.com/drone-plugins/drone-s3@refs/tags/v1.5.3";
    assertThat(SerializerUtils.getRepoName(plugin)).isEqualTo("drone-s3");
    assertThat(SerializerUtils.getVersion(plugin)).isEqualTo("v1.5.3");

    // missing '@' should return EMPTY
    String invalid1 = "github.com/drone-plugins/drone-s3";
    assertThat(SerializerUtils.getRepoName(invalid1)).isEqualTo(EMPTY);
    assertThat(SerializerUtils.getVersion(invalid1)).isEqualTo(EMPTY);

    // missing tag part after '@' should return EMPTY
    String invalid2 = "github.com/drone-plugins/drone-s3@";
    assertThat(SerializerUtils.getRepoName(invalid2)).isEqualTo(EMPTY);
    assertThat(SerializerUtils.getVersion(invalid2)).isEqualTo(EMPTY);
  }
}
