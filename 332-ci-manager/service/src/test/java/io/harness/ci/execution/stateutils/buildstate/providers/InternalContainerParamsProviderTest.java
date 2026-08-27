/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.stateutils.buildstate.providers;

import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INCREASE_LOG_LIMIT;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.ANKUSH_CHATERJEE;
import static io.harness.rule.OwnerRule.EBTASAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.VolumeMountInfo;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;
import retrofit2.Call;
import retrofit2.Response;

public class InternalContainerParamsProviderTest extends CIExecutionTestBase {
  @Inject InternalContainerParamsProvider internalContainerParamsProvider;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock NGSettingsClient settingsClient;
  private static final String WINDOWS_ROOTLESS_CONTAINER_TAG_NAME = "rootless-1.4.0";

  @Before
  public void setUp() throws Exception {
    on(internalContainerParamsProvider).set("settingsClient", settingsClient);
    when(settingsClient.getSetting(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<SettingValueResponseDTO>>>) invocation -> {
          Call<ResponseDTO<SettingValueResponseDTO>> call = Mockito.mock(Call.class);
          SettingValueType settingValueType = SettingValueType.BOOLEAN;
          String value = "true";
          when(call.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(
                  SettingValueResponseDTO.builder().valueType(settingValueType).value(value).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getSetupAddonContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, null, "workspace", null, "account", OSType.Linux, null);

    assertThat(containerParams.getName()).isEqualTo(SETUP_ADDON_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.ADD_ON);
    assertThat(containerParams.getArgs()).isEqualTo(Collections.singletonList(UNIX_SETUP_ADDON_ARGS));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getSetupAddonRootlessContainerParams() {
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(featureFlagService.isEnabled(FeatureName.CI_ADDON_LE_WINDOWS_ROOTLESS, "account")).thenReturn(true);

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, null, "workspace", null, "account", OSType.Windows, null);

    assertThat(containerParams.getImageDetailsWithConnector().getImageDetails().getTag())
        .isEqualTo(WINDOWS_ROOTLESS_CONTAINER_TAG_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.ADD_ON);
    assertThat(containerParams.getArgs()).isEqualTo(Collections.singletonList(WIN_SETUP_ADDON_ARGS));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getLiteEngineContainerParams() {
    int buildID = 1;
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, "account")).thenReturn(true);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(buildID).setPipelineIdentifier("pipeline").build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putAllSetupAbstractions(setupAbstractions)
                            .setMetadata(executionMetadata)
                            .build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();

    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, ConnectorDetails> publishArtifactConnectorDetailsMap = new HashMap<>();
    String logSecret = "secret";
    String logEndpoint = "http://localhost:8079";
    Map<String, String> logEnvVars = new HashMap<>();
    logEnvVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    logEnvVars.put(LOG_SERVICE_TOKEN_VARIABLE, logSecret);

    String tiToken = "token";
    String tiEndpoint = "http://localhost:8078";
    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    tiEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);

    String stoToken = "token";
    String stoEndpoint = "http://localhost:4000";
    Map<String, String> stoEnvVars = new HashMap<>();
    stoEnvVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoEndpoint);
    stoEnvVars.put(STO_SERVICE_TOKEN_VARIABLE, stoToken);
    Map<String, String> volumeToMountPath = new HashMap<>();

    Integer stageCpuRequest = 500;
    Integer stageMemoryRequest = 200;

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        publishArtifactConnectorDetailsMap, k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, tiEnvVars,
        stoEnvVars, Collections.emptyMap(), Collections.emptyMap(), volumeToMountPath, null, "/step-exec/workspace",
        null, "test", ambiance, null, null, OSType.Linux, false, Collections.emptyMap());

    assertThat(containerParams.getName()).isEqualTo(LITE_ENGINE_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.LITE_ENGINE);
    assertThat(containerParams.getEnvVars().containsKey(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF));
    assertThat(containerParams.getEnvVars().containsKey(HARNESS_CI_INCREASE_LOG_LIMIT));
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INCREASE_LOG_LIMIT)).isEqualTo(null);
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF)).isEqualTo("true");
  }
  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void onlLimitLiteEngineResources() throws Exception {
    String accountID = "account";
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(true);
    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(
        internalContainerParamsProvider, "getLiteEngineResourceParams", 500, 600, accountID, false);
    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(LITE_ENGINE_CONTAINER_CPU);
  }
  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void liteEngineResources() throws Exception {
    String accountID = "account";
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(
        internalContainerParamsProvider, "getLiteEngineResourceParams", 500, 600, accountID, false);
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(false);
    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(600 + LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(500 + LITE_ENGINE_CONTAINER_CPU);
  }

  @Test
  @Owner(developers = ANKUSH_CHATERJEE)
  @Category(UnitTests.class)
  public void testIgnoreConservativeLimitsTrue() throws Exception {
    String accountID = "account";
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(true);

    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(internalContainerParamsProvider,
        "getLiteEngineResourceParams", 500, 600, accountID, true); // ignoreConservativeLimits = true

    // Should use the stage-provided values (500, 600) instead of conservative limits
    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(600 + LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(500 + LITE_ENGINE_CONTAINER_MEM);
  }

  @Test
  @Owner(developers = ANKUSH_CHATERJEE)
  @Category(UnitTests.class)
  public void testIgnoreConservativeLimitsFalse() throws Exception {
    String accountID = "account";
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(true);

    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(internalContainerParamsProvider,
        "getLiteEngineResourceParams", 500, 600, accountID, false); // ignoreConservativeLimits = false

    // Should use conservative limits instead of stage-provided values
    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(LITE_ENGINE_CONTAINER_CPU);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void getLiteEngineRootlessContainerParams() {
    int buildID = 1;
    on(internalContainerParamsProvider).set("featureFlagService", featureFlagService);
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, "account")).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CI_ADDON_LE_WINDOWS_ROOTLESS, "account")).thenReturn(true);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(buildID).setPipelineIdentifier("pipeline").build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putAllSetupAbstractions(setupAbstractions)
                            .setMetadata(executionMetadata)
                            .build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();

    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, ConnectorDetails> publishArtifactConnectorDetailsMap = new HashMap<>();
    String logSecret = "secret";
    String logEndpoint = "http://localhost:8079";
    Map<String, String> logEnvVars = new HashMap<>();
    logEnvVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    logEnvVars.put(LOG_SERVICE_TOKEN_VARIABLE, logSecret);

    String tiToken = "token";
    String tiEndpoint = "http://localhost:8078";
    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    tiEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);

    String stoToken = "token";
    String stoEndpoint = "http://localhost:4000";
    Map<String, String> stoEnvVars = new HashMap<>();
    stoEnvVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoEndpoint);
    stoEnvVars.put(STO_SERVICE_TOKEN_VARIABLE, stoToken);
    Map<String, String> volumeToMountPath = new HashMap<>();

    Integer stageCpuRequest = 500;
    Integer stageMemoryRequest = 200;

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        publishArtifactConnectorDetailsMap, k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, tiEnvVars,
        stoEnvVars, Collections.emptyMap(), Collections.emptyMap(), volumeToMountPath, null, "/step-exec/workspace",
        null, "test", ambiance, null, null, OSType.Windows, false, Collections.emptyMap());

    assertThat(containerParams.getName()).isEqualTo(LITE_ENGINE_CONTAINER_NAME);
    assertThat(containerParams.getImageDetailsWithConnector().getImageDetails().getTag())
        .isEqualTo(WINDOWS_ROOTLESS_CONTAINER_TAG_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.LITE_ENGINE);
    assertThat(containerParams.getEnvVars().containsKey(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF));
    assertThat(containerParams.getEnvVars().containsKey(HARNESS_CI_INCREASE_LOG_LIMIT));
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INCREASE_LOG_LIMIT)).isEqualTo(null);
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF)).isEqualTo("true");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testVolumeMountWhenInputIsNull() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, null, "workspace", null, "account", OSType.Linux, null);
    assertThat(containerParams.getVolumeToMountPathV2()).isEmpty();
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testVolumeMountShouldOverrideNetrcVolumeIfPresent() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, List<VolumeMountInfo>> input = new HashMap<>();
    input.put("netrc-volume", Collections.singletonList(VolumeMountInfo.builder().mountPath("/old").build()));

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, input, "workspace", null, "account", OSType.Linux, null);

    Map<String, List<VolumeMountInfo>> volumeMounts = containerParams.getVolumeToMountPathV2();

    assertThat(volumeMounts).containsKey("netrc-volume");
    assertThat(volumeMounts.get("netrc-volume")).hasSize(1);
    assertThat(volumeMounts.get("netrc-volume").get(0).getMountPath()).isEqualTo("/addon/shared");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testVolumeMountShouldNotOverrideIfWindows() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, List<VolumeMountInfo>> input = new HashMap<>();
    input.put("netrc-volume", Collections.singletonList(VolumeMountInfo.builder().mountPath("/old").build()));

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, input, "workspace", null, "account", OSType.Windows, null);

    Map<String, List<VolumeMountInfo>> volumeMounts = containerParams.getVolumeToMountPathV2();

    assertThat(volumeMounts).containsKey("netrc-volume");
    assertThat(volumeMounts.get("netrc-volume")).hasSize(1);
    assertThat(volumeMounts.get("netrc-volume").get(0).getMountPath()).isEqualTo("/old");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testVolumeMountShouldNotOverrideIfNetrcVolumeAbsent() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, List<VolumeMountInfo>> input = new HashMap<>();
    input.put("some-other-volume", Collections.singletonList(VolumeMountInfo.builder().mountPath("/shared").build()));

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, input, "workspace", null, "account", OSType.Linux, null);

    Map<String, List<VolumeMountInfo>> volumeMounts = containerParams.getVolumeToMountPathV2();

    assertThat(volumeMounts).doesNotContainKey("root-volume");
    assertThat(volumeMounts).containsKey("some-other-volume");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void shouldSetVolumeToMountPathV2CorrectlyForLiteEngine() {
    Map<String, List<VolumeMountInfo>> inputV2 =
        Map.of("root-volume", List.of(VolumeMountInfo.builder().mountPath("/shared").build()), "addon-volume",
            List.of(VolumeMountInfo.builder().mountPath("/addon").build()));
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, "account")).thenReturn(true);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(1).setPipelineIdentifier("pipeline").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putAllSetupAbstractions(setupAbstractions)
                            .setMetadata(executionMetadata)
                            .build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();
    CIK8ContainerParams params = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        Map.of(), k8PodDetails, 500, 1024, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), inputV2, "/work",
        null, "logPrefix", ambiance, null, "IfNotPresent", OSType.Linux, false, Collections.emptyMap());

    assertThat(params.getVolumeToMountPathV2()).isEqualTo(inputV2);
  }
}
