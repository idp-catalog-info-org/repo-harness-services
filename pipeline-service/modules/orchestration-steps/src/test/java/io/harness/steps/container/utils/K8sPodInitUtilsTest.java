/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.ConfigMapVolumeYaml;
import io.harness.beans.yaml.extended.volumes.ConfigMapVolumeYaml.ConfigMapVolumeYamlSpec;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.SecretVolumeYaml;
import io.harness.beans.yaml.extended.volumes.SecretVolumeYaml.SecretVolumeYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.commonconstants.ContainerExecutionConstants;
import io.harness.delegate.beans.ci.pod.ConfigMapVolume;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.pod.SecretVolume;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.container.beans.ServiceEnvironmentVars;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class K8sPodInitUtilsTest extends CategoryTest {
  @Mock private PipelineRbacHelper pipelineRbacHelper;

  @Mock private PmsFeatureFlagHelper featureFlagHelper;

  @Mock private NGSettingsClient settingsClient;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private STOServiceUtils stoServiceUtils;
  @Mock private ContainerExecutionConfig containerExecutionConfig;
  @Mock private LogStreamingServiceConfiguration logStreamingServiceConfiguration;

  @InjectMocks private K8sPodInitUtils k8sPodInitUtils;
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
  @Owner(developers = OwnerRule.SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCheckSecretAccess_withNonEmptyEntityDetails() throws IOException {
    Ambiance ambiance = mock(Ambiance.class);
    String accountIdentifier = "accountId";
    String projectIdentifier = "projectId";
    String orgIdentifier = "orgId";

    List<SecretVariableDetails> secretVariableDetails = List.of(
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .secret(SecretRefData.builder().scope(Scope.ACCOUNT).identifier("secret1").build())
                                   .build())
            .build(),
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .secret(SecretRefData.builder().scope(Scope.PROJECT).identifier("secret2").build())
                                   .build())
            .build(),
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .secret(SecretRefData.builder().scope(Scope.ORG).identifier("secret3").build())
                                   .build())
            .build());

    ArgumentCaptor<List<EntityDetail>> captor = ArgumentCaptor.forClass(List.class);

    k8sPodInitUtils.checkSecretAccess(
        ambiance, secretVariableDetails, accountIdentifier, projectIdentifier, orgIdentifier);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(Ambiance.class), captor.capture(), eq(false));

    List<EntityDetail> capturedEntityDetails = captor.getValue();
    assertThat(capturedEntityDetails.size()).isEqualTo(3);
    assertThat(capturedEntityDetails.get(0).getEntityRef().getScope()).isEqualTo(Scope.ACCOUNT);
    assertThat(capturedEntityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("secret1");
    assertThat(capturedEntityDetails.get(1).getEntityRef().getScope()).isEqualTo(Scope.PROJECT);
    assertThat(capturedEntityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("secret2");
    assertThat(capturedEntityDetails.get(2).getEntityRef().getScope()).isEqualTo(Scope.ORG);
    assertThat(capturedEntityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("secret3");
  }

  @Test
  @Owner(developers = OwnerRule.PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPath_withConfigMapVolume() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

    List<String> sharedPaths = Arrays.asList("/path1");
    List<PodVolume> volumes = new ArrayList<>();
    ConfigMapVolume configMapVolume = mock(ConfigMapVolume.class);
    when(configMapVolume.getType()).thenReturn(PodVolume.Type.CONFIG_MAP);
    when(configMapVolume.getName()).thenReturn("configMapVolume");
    when(configMapVolume.getMountPath()).thenReturn("/mnt/configMap");

    volumes.add(configMapVolume);

    Map<String, String> result = k8sPodInitUtils.getVolumeToMountPath(sharedPaths, volumes, true);

    assertThat(result.size()).isEqualTo(4);
    assertThat(result.get("configMapVolume")).isEqualTo("/mnt/configMap");
  }

  @Test
  @Owner(developers = OwnerRule.PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testConvertDirectK8Volumes_ConfigMap() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

    ContainerK8sInfra k8sDirectInfraYaml = mock(ContainerK8sInfra.class);

    ContainerInfraYamlSpec containerInfraYamlSpec = mock(ContainerInfraYamlSpec.class);
    // Mocking the CIVolume list to simulate the input with a ConfigMap volume
    List<CIVolume> volumes = new ArrayList<>();
    ConfigMapVolumeYamlSpec configMapVolumeYamlSpec = mock(ConfigMapVolumeYamlSpec.class);
    ConfigMapVolumeYaml configMapVolumeYaml = mock(ConfigMapVolumeYaml.class);
    when(configMapVolumeYaml.getSpec()).thenReturn(configMapVolumeYamlSpec);
    when(configMapVolumeYamlSpec.getName()).thenReturn(ParameterField.createValueField("configMapVolume"));
    when(configMapVolumeYamlSpec.getOptional()).thenReturn(ParameterField.createValueField(Boolean.TRUE));
    when(configMapVolumeYaml.getMountPath()).thenReturn(ParameterField.createValueField("/mnt/configMap"));
    when(configMapVolumeYaml.getType()).thenReturn(CIVolume.Type.CONFIG_MAP);

    volumes.add(configMapVolumeYaml);

    // Mocking k8sDirectInfraYaml to return the mocked volumes
    when(k8sDirectInfraYaml.getSpec()).thenReturn(containerInfraYamlSpec);
    when(containerInfraYamlSpec.getVolumes()).thenReturn(ParameterField.createValueField(volumes));
    // Call the method under test
    List<PodVolume> podVolumes = k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml, ambiance);

    // Assertions to verify the correct conversion
    assertThat(podVolumes.size()).isEqualTo(1); // We should have 1 volume (the ConfigMap)
    PodVolume podVolume = podVolumes.get(0);

    // Validate that the volume is of type ConfigMap and has the correct name and mount path
    assertThat(podVolume).isInstanceOf(ConfigMapVolume.class);
    assertThat(((ConfigMapVolume) podVolume).getName()).isEqualTo("volume-0");
    assertThat(((ConfigMapVolume) podVolume).getConfigMapName()).isEqualTo("configMapVolume");
    assertThat(((ConfigMapVolume) podVolume).getMountPath()).isEqualTo("/mnt/configMap");
    assertThat(((ConfigMapVolume) podVolume).getOptional()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testConvertDirectK8Volumes_Secret() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

    ContainerK8sInfra k8sDirectInfraYaml = mock(ContainerK8sInfra.class);

    ContainerInfraYamlSpec containerInfraYamlSpec = mock(ContainerInfraYamlSpec.class);
    // Mocking the CIVolume list to simulate the input with a ConfigMap volume
    List<CIVolume> volumes = new ArrayList<>();
    SecretVolumeYamlSpec secretVolumeYamlSpec = mock(SecretVolumeYamlSpec.class);
    SecretVolumeYaml secretVolumeYaml = mock(SecretVolumeYaml.class);
    when(secretVolumeYaml.getSpec()).thenReturn(secretVolumeYamlSpec);
    when(secretVolumeYamlSpec.getName()).thenReturn(ParameterField.createValueField("secretVolume"));
    when(secretVolumeYamlSpec.getOptional()).thenReturn(ParameterField.createValueField(Boolean.TRUE));
    when(secretVolumeYaml.getMountPath()).thenReturn(ParameterField.createValueField("/mnt/secret"));
    when(secretVolumeYaml.getType()).thenReturn(CIVolume.Type.SECRET);

    volumes.add(secretVolumeYaml);

    // Mocking k8sDirectInfraYaml to return the mocked volumes
    when(k8sDirectInfraYaml.getSpec()).thenReturn(containerInfraYamlSpec);
    when(containerInfraYamlSpec.getVolumes()).thenReturn(ParameterField.createValueField(volumes));
    // Call the method under test
    List<PodVolume> podVolumes = k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml, ambiance);

    // Assertions to verify the correct conversion
    assertThat(podVolumes.size()).isEqualTo(1); // We should have 1 volume (the ConfigMap)
    PodVolume podVolume = podVolumes.get(0);

    // Validate that the volume is of type ConfigMap and has the correct name and mount path
    assertThat(podVolume).isInstanceOf(SecretVolume.class);
    assertThat(((SecretVolume) podVolume).getName()).isEqualTo("volume-0");
    assertThat(((SecretVolume) podVolume).getSecretName()).isEqualTo("secretVolume");
    assertThat(((SecretVolume) podVolume).getMountPath()).isEqualTo("/mnt/secret");
    assertThat(((SecretVolume) podVolume).getOptional()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.ARCHIT)
  @Category(UnitTests.class)
  public void testShouldSkipImagePullSecret() throws IOException {
    // Given
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "test-account")
                            .putSetupAbstractions("orgIdentifier", "test-org")
                            .putSetupAbstractions("projectIdentifier", "test-project")
                            .build();

    // Test Case 1: When setting is true (should NOT skip image pull secret)
    SettingValueResponseDTO trueSettingValue = mock(SettingValueResponseDTO.class);
    when(trueSettingValue.getValue()).thenReturn("true");

    ResponseDTO<SettingValueResponseDTO> trueResponseData = ResponseDTO.newResponse(trueSettingValue);
    Call<ResponseDTO<SettingValueResponseDTO>> trueResponseDTO = mock(Call.class);
    when(trueResponseDTO.execute()).thenReturn(Response.success(trueResponseData));

    when(settingsClient.getSetting(eq(SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED), eq("test-account"),
             eq("test-org"), eq("test-project")))
        .thenReturn(trueResponseDTO);

    mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any())).thenReturn(trueSettingValue);

    // When
    boolean resultForTrue = k8sPodInitUtils.shouldSkipImagePullSecret(ambiance);

    // Then
    assertThat(resultForTrue).isFalse(); // Should NOT skip when setting is true

    // Test Case 2: When setting is false (should skip image pull secret)
    SettingValueResponseDTO falseSettingValue = mock(SettingValueResponseDTO.class);
    when(falseSettingValue.getValue()).thenReturn("false");

    ResponseDTO<SettingValueResponseDTO> falseResponseData = ResponseDTO.newResponse(falseSettingValue);
    Call<ResponseDTO<SettingValueResponseDTO>> falseResponseDTO = mock(Call.class);
    when(falseResponseDTO.execute()).thenReturn(Response.success(falseResponseData));

    when(settingsClient.getSetting(eq(SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED), eq("test-account"),
             eq("test-org"), eq("test-project")))
        .thenReturn(falseResponseDTO);

    when(NGRestUtils.getResponse(any())).thenReturn(falseSettingValue);

    // When
    boolean resultForFalse = k8sPodInitUtils.shouldSkipImagePullSecret(ambiance);

    // Then
    assertThat(resultForFalse).isTrue(); // Should skip when setting is false

    // Test Case 3: When setting is invalid (should default to skipping)
    SettingValueResponseDTO invalidSettingValue = mock(SettingValueResponseDTO.class);
    when(invalidSettingValue.getValue()).thenReturn("invalid-value");

    ResponseDTO<SettingValueResponseDTO> invalidResponseData = ResponseDTO.newResponse(invalidSettingValue);
    Call<ResponseDTO<SettingValueResponseDTO>> invalidResponseDTO = mock(Call.class);
    when(invalidResponseDTO.execute()).thenReturn(Response.success(invalidResponseData));

    when(settingsClient.getSetting(eq(SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED), eq("test-account"),
             eq("test-org"), eq("test-project")))
        .thenReturn(invalidResponseDTO);

    when(NGRestUtils.getResponse(any())).thenReturn(invalidSettingValue);

    // When
    boolean resultForInvalid = k8sPodInitUtils.shouldSkipImagePullSecret(ambiance);

    // Then
    assertThat(resultForInvalid).isTrue(); // Should default to skipping for invalid values
  }

  @Test
  @Owner(developers = OwnerRule.ARCHIT)
  @Category(UnitTests.class)
  public void testShouldSkipImagePullSecret_ErrorCases() throws IOException {
    // Given
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "test-account")
                            .putSetupAbstractions("orgIdentifier", "test-org")
                            .putSetupAbstractions("projectIdentifier", "test-project")
                            .build();

    // Test Case 1: When exception occurs
    Call<ResponseDTO<SettingValueResponseDTO>> exceptionResponseDTO = mock(Call.class);
    when(exceptionResponseDTO.execute()).thenThrow(new RuntimeException("Test exception"));

    when(settingsClient.getSetting(eq(SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED), eq("test-account"),
             eq("test-org"), eq("test-project")))
        .thenReturn(exceptionResponseDTO);

    mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("Test exception"));

    // When
    boolean resultForException = k8sPodInitUtils.shouldSkipImagePullSecret(ambiance);

    // Then
    assertThat(resultForException).isFalse(); // Should default to not skipping when exception occurs

    // Test Case 2: When settings client is null
    // Create a new instance with null settingsClient
    K8sPodInitUtils k8sPodInitUtilsWithNullClient = new K8sPodInitUtils();

    // When
    boolean resultForNullClient = k8sPodInitUtilsWithNullClient.shouldSkipImagePullSecret(ambiance);

    // Then
    assertThat(resultForNullClient).isFalse(); // Should default to not skipping when settings client is null
  }

  @Test
  @Owner(developers = OwnerRule.PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void shouldAddCiNewVersionGodotenvWhenFeatureFlagEnabled() {
    String ACCOUNT_ID = "account";
    when(featureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS)).thenReturn(true);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "org", "projectIdentifier", "proj"))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe").setRunSequence(1).build())
            .setPlanExecutionId("exec-1")
            .build();

    Map<String, String> envVars = k8sPodInitUtils.getCommonStepEnvVariables(
        InitContainerV2StepInfo.builder().build(), "/harness", "prefix", ambiance);

    assertThat(envVars).containsEntry(ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV, "true");
  }

  @Test
  @Owner(developers = OwnerRule.PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void shouldNotAddCiNewVersionGodotenvWhenFeatureFlagDisabled() {
    String ACCOUNT_ID = "account";
    when(featureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS)).thenReturn(false);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "org", "projectIdentifier", "proj"))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe").setRunSequence(1).build())
            .setPlanExecutionId("exec-1")
            .build();

    Map<String, String> envVars = k8sPodInitUtils.getCommonStepEnvVariables(
        InitContainerV2StepInfo.builder().build(), "/harness", "prefix", ambiance);

    assertThat(envVars).doesNotContainKey(ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void testGetServiceEnvironmentVars() throws IOException {
    ContainerDetailsSweepingOutput k8PodDetails = ContainerDetailsSweepingOutput.builder().build();
    String accountId = "account123";
    String logServiceBaseUrl = "log-service.harness.io";
    String logServiceToken = "log-service-token";
    String stoServiceBaseUrl = "sto-service.harness.io";
    String stoServiceToken = "sto-service-token";

    STOServiceConfig stoServiceConfig = STOServiceConfig.builder().baseUrl(stoServiceBaseUrl).build();

    when(containerExecutionConfig.getLogStreamingContainerStepBaseUrl()).thenReturn(logServiceBaseUrl);
    when(logStreamingServiceConfiguration.getServiceToken()).thenReturn("service-token");
    when(logStreamingStepClientFactory.retrieveLogStreamingAccountToken(accountId)).thenReturn(logServiceToken);

    when(stoServiceUtils.getStoServiceConfig()).thenReturn(stoServiceConfig);
    when(stoServiceUtils.getSTOServiceToken(accountId, List.of("sto-plugin"))).thenReturn(stoServiceToken);

    ServiceEnvironmentVars result = k8sPodInitUtils.getServiceEnvironmentVars(k8PodDetails, accountId);

    assertThat(result).isNotNull();

    Map<String, String> logEnvVars = result.getLogEnvVars();
    assertThat(logEnvVars).isNotNull();
    assertThat(logEnvVars).hasSize(2);
    assertThat(logEnvVars.get(ContainerExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE)).isEqualTo(logServiceToken);
    assertThat(logEnvVars.get(ContainerExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE)).isEqualTo(logServiceBaseUrl);

    Map<String, String> stoEnvVars = result.getStoEnvVars();
    assertThat(stoEnvVars).isNotNull();
    assertThat(stoEnvVars).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void shouldUseStageIdentifierWhenFeatureFlagEnabled() {
    String ACCOUNT_ID = "account";
    String STAGE_IDENTIFIER = "myStage";
    String STAGE_EXECUTION_ID = "stage-exec-123";

    when(featureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_CONTAINER_STEP_USE_STAGE_IDENTIFIER)).thenReturn(true);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "org", "projectIdentifier", "proj"))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe").setRunSequence(1).build())
            .setPlanExecutionId("exec-1")
            .setStageExecutionId(STAGE_EXECUTION_ID)
            .addLevels(Level.newBuilder()
                           .setRuntimeId(STAGE_EXECUTION_ID)
                           .setSetupId("stage-setup-id")
                           .setIdentifier(STAGE_IDENTIFIER)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .build();

    Map<String, String> envVars = k8sPodInitUtils.getCommonStepEnvVariables(
        InitContainerV2StepInfo.builder().build(), "/harness", "prefix", ambiance);

    // When feature flag is enabled, it should use stage identifier, not stage execution ID
    assertThat(envVars).containsEntry(ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE, STAGE_IDENTIFIER);
    assertThat(envVars.get(ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE)).isNotEqualTo(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void shouldUseStageExecutionIdWhenFeatureFlagDisabled() {
    String ACCOUNT_ID = "account";
    String STAGE_IDENTIFIER = "myStage";
    String STAGE_EXECUTION_ID = "stage-exec-123";

    when(featureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_CONTAINER_STEP_USE_STAGE_IDENTIFIER))
        .thenReturn(false);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(
                Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "org", "projectIdentifier", "proj"))
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe").setRunSequence(1).build())
            .setPlanExecutionId("exec-1")
            .setStageExecutionId(STAGE_EXECUTION_ID)
            .addLevels(Level.newBuilder()
                           .setRuntimeId(STAGE_EXECUTION_ID)
                           .setSetupId("stage-setup-id")
                           .setIdentifier(STAGE_IDENTIFIER)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .build();

    Map<String, String> envVars = k8sPodInitUtils.getCommonStepEnvVariables(
        InitContainerV2StepInfo.builder().build(), "/harness", "prefix", ambiance);

    // When feature flag is disabled, it should use stage execution ID, not stage identifier
    assertThat(envVars).containsEntry(ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE, STAGE_EXECUTION_ID);
    assertThat(envVars.get(ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE)).isNotEqualTo(STAGE_IDENTIFIER);
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_ANAND)
  @Category(UnitTests.class)
  public void testConvertDirectK8Volumes_EmptyDirWithNullSpec() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

    // Create EmptyDirYaml with null spec
    EmptyDirYaml emptyDirYaml = EmptyDirYaml.builder()
                                    .mountPath(ParameterField.createValueField("/test/path"))
                                    .type(CIVolume.Type.EMPTY_DIR)
                                    .spec(null)
                                    .build();

    // Create container infra with the volume
    List<CIVolume> volumes = Collections.singletonList(emptyDirYaml);
    ContainerInfraYamlSpec containerInfraYamlSpec = mock(ContainerInfraYamlSpec.class);
    when(containerInfraYamlSpec.getVolumes()).thenReturn(ParameterField.createValueField(volumes));

    ContainerK8sInfra k8sDirectInfraYaml = mock(ContainerK8sInfra.class);
    when(k8sDirectInfraYaml.getSpec()).thenReturn(containerInfraYamlSpec);

    try {
      k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml, ambiance);
      fail("Should have thrown ContainerStepExecutionException");
    } catch (ContainerStepExecutionException e) {
      assertThat(e.getMessage()).contains("Invalid volume configuration");
    }
  }
}
