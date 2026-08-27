/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.EMPTY_DIR_MOUNT_PATH;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.HOST_DIR_MOUNT_PATH;
import static io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper.PVC_DIR_MOUNT_PATH;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SHUBHAM_ANAND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;

import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.delegate.beans.ci.pod.PodTopologySpreadConstraints;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.VolumeMountInfo;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class K8InitializeTaskUtilsTest extends CIExecutionTestBase {
  private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private SecretUtils secretUtils;

  @Before
  public void setUp() {
    k8InitializeTaskUtils = new K8InitializeTaskUtils();

    on(k8InitializeTaskUtils).set("connectorUtils", connectorUtils);
    on(k8InitializeTaskUtils).set("secretUtils", secretUtils);
    on(k8InitializeTaskUtils).set("executionSweepingOutputResolver", executionSweepingOutputResolver);
    on(k8InitializeTaskUtils).set("logServiceUtils", logServiceUtils);
    on(k8InitializeTaskUtils).set("featureFlagService", featureFlagService);
    on(k8InitializeTaskUtils).set("tiServiceUtils", tiServiceUtils);
    on(k8InitializeTaskUtils).set("stoServiceUtils", stoServiceUtils);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void convertDirectK8Volumes() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();

    List<PodVolume> expected = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<PodVolume> actual = k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getVolumeToMountPath() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/tmp/shared");

    expected.put("volume-0", EMPTY_DIR_MOUNT_PATH);
    expected.put("volume-1", HOST_DIR_MOUNT_PATH);
    expected.put("volume-2", PVC_DIR_MOUNT_PATH);

    Map<String, String> actual = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, podVolumes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void getVolumeToMountPathWithoutAddonVolume() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");

    // Ephemeral delegate mode: no addon (lite-engine) container is created, so the addon volume must be omitted
    // while the step volume and shared/user volumes remain.
    Map<String, String> actual = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, podVolumes, false);

    assertThat(actual).doesNotContainKey("addon");
    assertThat(actual).containsEntry("harness", "/harness");
    assertThat(actual).containsEntry("shared-0", "/tmp/shared");
    assertThat(actual).containsEntry("volume-0", EMPTY_DIR_MOUNT_PATH);
    assertThat(actual).containsEntry("volume-1", HOST_DIR_MOUNT_PATH);
    assertThat(actual).containsEntry("volume-2", PVC_DIR_MOUNT_PATH);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void getVolumeToMountPathIncludeAddonVolumeMatchesDefaultOverload() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");

    // The default overload must be equivalent to passing includeAddonVolume=true.
    Map<String, String> withFlag = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, podVolumes, true);
    Map<String, String> defaultOverload = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, podVolumes);

    assertThat(withFlag).containsEntry("addon", "/addon");
    assertThat(withFlag).isEqualTo(defaultOverload);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void volumeV2ShouldOmitAddonVolumeWhenIncludeAddonVolumeFalse() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");

    Map<String, List<VolumeMountInfo>> withAddon =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, null, OSType.Linux, true);
    Map<String, List<VolumeMountInfo>> withoutAddon =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, null, OSType.Linux, false);

    assertThat(withAddon).containsKey("addon");
    assertThat(withoutAddon).doesNotContainKey("addon");
    // The step volume is unaffected by the addon toggle.
    assertThat(withoutAddon).containsKey("harness");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void volumeV2DefaultOverloadIncludesAddonVolume() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");

    Map<String, List<VolumeMountInfo>> defaultOverload =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, null, OSType.Linux);

    assertThat(defaultOverload).containsKey("addon");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getLinuxOS() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();

    OSType expected = OSType.Linux;
    OSType actual = k8InitializeTaskUtils.getOS(k8sDirectInfraYaml);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getWindowsOS() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();
    k8sDirectInfraYaml.getSpec().setOs(ParameterField.createValueField(OSType.Windows));

    OSType expected = OSType.Windows;
    OSType actual = k8InitializeTaskUtils.getOS(k8sDirectInfraYaml);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void getPodSpecOverlay() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8InfrastructureWithVolume();
    List<PodTopologySpreadConstraints> podTopologySpreadConstraints =
        k8InitializeTaskUtils.getTopologySpreadConstraintsList(k8sDirectInfraYaml.getSpec().getPodSpecOverlay());

    assertThat(podTopologySpreadConstraints).isNotNull();
    assertThat(podTopologySpreadConstraints.size()).isEqualTo(2);
    assertThat(podTopologySpreadConstraints.get(0).getMaxSkew()).isEqualTo(3);
    assertThat(podTopologySpreadConstraints.get(0).getMinDomains()).isEqualTo(2);
    assertThat(podTopologySpreadConstraints.get(0).getTopologyKey()).isEqualTo("topology.kubernetes.io/zone");
    assertThat(podTopologySpreadConstraints.get(0).getWhenUnsatisfiable()).isEqualTo("DoNotSchedule");
    assertThat(podTopologySpreadConstraints.get(1).getMaxSkew()).isEqualTo(2);
    assertThat(podTopologySpreadConstraints.get(1).getTopologyKey()).isEqualTo("topology.kubernetes.io/zone");
    assertThat(podTopologySpreadConstraints.get(1).getWhenUnsatisfiable()).isEqualTo("ScheduleAnyway");
    assertThat(podTopologySpreadConstraints.get(1).getNodeAffinityPolicy()).isEqualTo("Honor");
    assertThat(podTopologySpreadConstraints.get(1).getNodeTaintsPolicy()).isEqualTo("Ignore");
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testSecretSanitization() {
    Map<String, String> inputMap = new HashMap<>();
    inputMap.put("test_null", null);
    inputMap.put("test", "not null");
    inputMap.put("test_secret", "<+ngSecretManager..obtain(\"account.sec\", 507718667)");
    Map<String, String> secretMap = k8InitializeTaskUtils.removeEnvVarsWithSecretRef(inputMap);

    // secret map (output) should contain the ones with secret
    assertThat(secretMap).containsKey("test_secret");

    // original map (input) should contain other values
    assertThat(inputMap).containsKey("test_null");
    assertThat(inputMap).containsKey("test");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnEmptyMapWhenCodeBaseIsNull() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, null, OSType.Linux);
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContainKey("netrc-volume");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnEmptyMapWhenPersistCredentialsIsFalse() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(1))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .persistCredentials(ParameterField.createValueField(false))
                            .build();
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, codebase, OSType.Linux);
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContainKey("netrc-volume");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnRootVolumeWhenPersistCredentialsIsTrue() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(1))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .persistCredentials(ParameterField.createValueField(true))
                            .build();
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, codebase, OSType.Linux);
    assertThat(result).isNotEmpty();
    assertThat(result).containsKey("netrc-volume");
    List<VolumeMountInfo> mounts = result.get("netrc-volume");
    assertThat(mounts).hasSize(2);

    assertThat(mounts.get(0).getMountPath()).isEqualTo("/root/.netrc");
    assertThat(mounts.get(0).getSubPath()).isEqualTo(".netrc");

    assertThat(mounts.get(1).getMountPath()).isEqualTo("/home/drone/.netrc");
    assertThat(mounts.get(1).getSubPath()).isEqualTo(".netrc");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnEmptyMapWhenCodeBaseIsNullWin() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, null, OSType.Windows);
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContainKey("netrc-volume");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnEmptyMapWhenPersistCredentialsIsFalseWin() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(1))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .persistCredentials(ParameterField.createValueField(false))
                            .build();
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, codebase, OSType.Windows);
    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContainKey("netrc-volume");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void volumeV2ShouldReturnRootVolumeWhenPersistCredentialsIsTrueWin() {
    List<PodVolume> podVolumes = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<String> sharedPaths = Arrays.asList("/tmp/shared");
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(1))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .persistCredentials(ParameterField.createValueField(true))
                            .build();
    Map<String, List<VolumeMountInfo>> result =
        k8InitializeTaskUtils.getVolumeV2ToMountPath(sharedPaths, podVolumes, codebase, OSType.Windows);
    assertThat(result).isNotEmpty();
    assertThat(result).containsKey("netrc-volume");
    List<VolumeMountInfo> mounts = result.get("netrc-volume");
    assertThat(mounts).hasSize(1);

    assertThat(mounts.get(0).getMountPath()).isEqualTo("/addon/shared");
  }

  @Test
  @Owner(developers = SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void testConvertDirectK8Volumes_EmptyDirWithNullSpec() {
    // Create EmptyDirYaml with null spec
    EmptyDirYaml emptyDirYaml = EmptyDirYaml.builder()
                                    .mountPath(ParameterField.createValueField("/test/path"))
                                    .type(CIVolume.Type.EMPTY_DIR)
                                    .spec(null)
                                    .build();

    // Create infrastructure YAML with the EmptyDir
    List<CIVolume> volumes = Collections.singletonList(emptyDirYaml);
    K8sDirectInfraYamlSpec spec = K8sDirectInfraYamlSpec.builder()
                                      .volumes(ParameterField.createValueField(volumes))
                                      .connectorRef(ParameterField.createValueField("test-connector"))
                                      .namespace(ParameterField.createValueField("test-namespace"))
                                      .build();

    K8sDirectInfraYaml k8sDirectInfraYaml = K8sDirectInfraYaml.builder().spec(spec).build();

    try {
      k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);
      fail("Should have thrown CIStageExecutionException");
    } catch (CIStageExecutionException e) {
      assertThat(e.getMessage()).contains("Invalid volume configuration");
    }
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheEnvironmentVariableForLinux() {
    Map<String, String> envVars = k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Linux);

    assertThat(envVars).isNotEmpty();
    assertThat(envVars).containsEntry("GOCACHE", "/harness/.go/");
    assertThat(envVars).containsEntry("GRADLE_USER_HOME", "/harness/.gradle/");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheEnvironmentVariableForMacOS() {
    Map<String, String> envVars = k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.MacOS);

    assertThat(envVars).isNotEmpty();
    assertThat(envVars).containsEntry("GOCACHE", "/harness/.go/");
    assertThat(envVars).containsEntry("GRADLE_USER_HOME", "/harness/.gradle/");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheEnvironmentVariableForWindows() {
    Map<String, String> envVars = k8InitializeTaskUtils.getCacheEnvironmentVariable(OSType.Windows);

    assertThat(envVars).isNotEmpty();
    assertThat(envVars).containsEntry("GOCACHE", "C:\\harness\\.go\\");
    assertThat(envVars).containsEntry("GRADLE_USER_HOME", "C:\\harness\\.gradle\\");
  }
}
