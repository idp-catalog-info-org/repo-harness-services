/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate.providers;

import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;
import retrofit2.Call;
import retrofit2.Response;

public class InternalContainerParamsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks private InternalContainerParamsProvider internalContainerParamsProvider;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private CIExecutionConfigService ciExecutionConfigService;

  @Before
  public void setUp() throws Exception {
    when(ciExecutionConfigService.getAddonImage(any())).thenReturn("harness/ci-addon:latest");
    when(ciExecutionConfigService.getLiteEngineImage(any())).thenReturn("harness/ci-lite-engine:latest");
    when(ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue()).thenReturn("delegate-service:8080");
    when(settingsClient.getSetting(any(), eq("account"), isNull(), isNull()))
        .thenAnswer((Answer<Call<ResponseDTO<SettingValueResponseDTO>>>) invocation -> {
          Call<ResponseDTO<SettingValueResponseDTO>> call = Mockito.mock(Call.class);
          SettingValueType settingValueType = SettingValueType.BOOLEAN;
          String value = "true";
          when(call.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(
                  SettingValueResponseDTO.builder().valueType(settingValueType).value(value).build())));
          Call<ResponseDTO<SettingValueResponseDTO>> clonedCall = Mockito.mock(Call.class);
          when(clonedCall.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(
                  SettingValueResponseDTO.builder().valueType(settingValueType).value(value).build())));
          when(call.clone()).thenReturn(clonedCall);
          return call;
        });
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSetupAddonContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        connectorDetails, null, null, "workspace", null, "account", OSType.Linux, null);

    assertThat(containerParams.getName()).isEqualTo(SETUP_ADDON_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.ADD_ON);
    assertThat(containerParams.getArgs()).isEqualTo(Collections.singletonList(UNIX_SETUP_ADDON_ARGS));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLiteEngineContainerParams() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    when(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, "account")).thenReturn(true);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(1).setPipelineIdentifier("pipeline").build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putAllSetupAbstractions(setupAbstractions)
                            .setMetadata(executionMetadata)
                            .build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        new HashMap<>(), k8PodDetails, 500, 200, new HashMap<>(), new HashMap<>(), new HashMap<>(),
        Collections.emptyMap(), Collections.emptyMap(), new HashMap<>(), null, "/step-exec/workspace", null, "test",
        ambiance, null, null, OSType.Linux, false, Collections.emptyMap());

    assertThat(containerParams.getName()).isEqualTo(LITE_ENGINE_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.LITE_ENGINE);
    assertThat(containerParams.getEnvVars().get(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF)).isEqualTo("true");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLiteEngineResourceParamsConservative() throws Exception {
    String accountID = "account";
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(true);

    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(
        internalContainerParamsProvider, "getLiteEngineResourceParams", 500, 600, accountID, false);

    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(LITE_ENGINE_CONTAINER_CPU);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLiteEngineResourceParamsNonConservative() throws Exception {
    String accountID = "account";
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, accountID)).thenReturn(false);

    ContainerResourceParams containerResourceParams = Whitebox.invokeMethod(
        internalContainerParamsProvider, "getLiteEngineResourceParams", 500, 600, accountID, false);

    assertThat(containerResourceParams.getResourceRequestMemoryMiB()).isEqualTo(600 + LITE_ENGINE_CONTAINER_MEM);
    assertThat(containerResourceParams.getResourceRequestMilliCpu()).isEqualTo(500 + LITE_ENGINE_CONTAINER_CPU);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetLiteEngineSecretVars() {
    Map<String, String> logEnvVars = new HashMap<>();
    logEnvVars.put("LOG_KEY", "logValue");
    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put("TI_KEY", "tiValue");

    var secretVars = internalContainerParamsProvider.getLiteEngineSecretVars(
        logEnvVars, tiEnvVars, new HashMap<>(), new HashMap<>(), new HashMap<>(), null);

    assertThat(secretVars).containsKey("LOG_KEY");
    assertThat(secretVars).containsKey("TI_KEY");
  }
}
