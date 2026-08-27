/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.yaml.YamlNode;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class EnvironmentRefreshHelperTest extends CategoryTest {
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock NGSettingsClient settingsClient;
  @Mock AccountClient accountClient;
  @Mock ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private Call<RestResponse<Boolean>> restRequest;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock ScopeInfoService scopeInfoService;

  @InjectMocks private EnvironmentRefreshHelper refreshHelper;

  private final EntityRefreshContext refreshContext = getRefreshContext();
  private AutoCloseable mocks;
  private ScopeInfo scopeInfo;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(settingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    doReturn(restRequest).when(accountClient).isFeatureFlagEnabled(anyString(), anyString());
    RestResponse<Boolean> mockResponse = new RestResponse<>(true);
    doReturn(Response.success(mockResponse)).when(restRequest).execute();

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(refreshContext.getAccountId())
                    .orgIdentifier(refreshContext.getOrgId())
                    .projectIdentifier(refreshContext.getProjectId())
                    .uniqueId("uniqueId")
                    .build();
    doReturn(scopeInfo)
        .when(scopeInfoService)
        .getScopeInfo(refreshContext.getAccountId(), refreshContext.getOrgId(), refreshContext.getProjectId());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_0() throws IOException {
    mockEnvWithNoRuntimeInputs("env_without_inputs");

    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(buildEnvYamlNode("env_without_inputs"), refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_1() throws IOException {
    mockEnvWithRuntimeInputs("env_with_inputs");

    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    YamlNode env_with_inputs = buildEnvYamlNode("env_with_inputs");
    refreshHelper.validateEnvironmentInputs(env_with_inputs, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isFalse();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(env_with_inputs, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_with_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\",\n"
            + "  \"environmentInputs\" : {\n"
            + "    \"identifier\" : \"env_with_inputs\",\n"
            + "    \"type\" : \"PreProduction\",\n"
            + "    \"variables\" : [ {\n"
            + "      \"name\" : \"numvar\",\n"
            + "      \"type\" : \"Number\",\n"
            + "      \"value\" : \"<+input>\"\n"
            + "    } ]\n"
            + "  }\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_withTemplate_0() throws IOException {
    mockEnvWithNoRuntimeInputs("env_without_inputs");

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeSvcEnvRuntime());

    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(buildEnvYamlNode("env_without_inputs"), refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_withTemplate_1() throws IOException {
    String envId = "env_with_serviceoverride_inputs";
    String serviceId = "serviceId";

    mockEnvWithNoRuntimeInputs(envId);
    mockEnvWithServiceOverrideInputs(serviceId, envId);

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNode(envId);
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(entityNode, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isFalse();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(entityNode, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_with_serviceoverride_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\",\n"
            + "  \"serviceOverrideInputs\" : {\n"
            + "    \"variables\" : [ {\n"
            + "      \"name\" : \"aa\",\n"
            + "      \"type\" : \"String\",\n"
            + "      \"value\" : \"<+input>\"\n"
            + "    } ]\n"
            + "  }\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void validateNoServiceOverrideInputs_withTemplate_1() throws IOException {
    String envId = "env_with_serviceoverride_inputs";
    String serviceId = "serviceId";

    mockEnvWithNoRuntimeInputs(envId);
    mockEnvWithServiceOverrideInputs(serviceId, envId);

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeMultiSvcEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNodeWithRuntimeInputs(envId);

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(entityNode, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_with_serviceoverride_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\"\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void validateNoServiceOverrideInputs_withTemplate_2() throws IOException {
    String envId = "env_with_serviceoverride_inputs";
    String serviceId = "serviceId";

    mockEnvWithNoRuntimeInputs(envId);
    mockEnvWithServiceOverrideInputs(serviceId, envId);

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeMultiSvcEnvRuntime2());

    YamlNode entityNode = buildEnvYamlNodeWithRuntimeInputs(envId);

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(entityNode, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_with_serviceoverride_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\"\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_whenExpression() throws IOException {
    String envId = "<+pipeline.variables.environmentRef>";
    String serviceId = "<+pipeline.variables.serviceRef>";

    doThrow(new RuntimeException("invalid identifier"))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), eq(envId), eq(false));

    doThrow(new RuntimeException("invalid identifier"))
        .when(serviceOverrideService)
        .createServiceOverrideInputsYaml(anyString(), anyString(), anyString(), eq(envId), eq(serviceId));

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNode(envId);
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(entityNode, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(entityNode, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"<+pipeline.variables.environmentRef>\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\"\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_whenOnlySvcExpression() throws IOException {
    String envId = "org.env";
    String serviceId = "<+pipeline.variables.serviceRef>";

    doReturn("serviceOverrideInputs:\n"
        + "              variables:\n"
        + "                - name: aa\n"
        + "                  type: String\n"
        + "                  value: <+input>")
        .when(serviceOverrideService)
        .createServiceOverrideInputsYaml(anyString(), anyString(), anyString(), eq(envId), eq(serviceId));

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNode(envId);
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(entityNode, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_whenOnlyEnvExpression() throws IOException {
    String envId = "<+pipeline.variables.environmentRef>";
    String serviceId = "org.svc";

    doReturn("serviceOverrideInputs:\n"
        + "              variables:\n"
        + "                - name: aa\n"
        + "                  type: String\n"
        + "                  value: <+input>")
        .when(serviceOverrideService)
        .createServiceOverrideInputsYaml(anyString(), anyString(), anyString(), eq(envId), eq(serviceId));

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNode(envId);
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    refreshHelper.validateEnvironmentInputs(entityNode, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void validateInfraDefinitionsIdentifierAsExpression() throws IOException {
    mockEnvWithNoRuntimeInputs("env_without_inputs");

    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    YamlNode env_with_inputs = buildEnvYamlNodeWithInfraDefAsExpression("env_without_inputs");
    refreshHelper.validateEnvironmentInputs(env_with_inputs, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(env_with_inputs, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_without_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : [ {\n"
            + "    \"identifier\" : \"<+env.name>\"\n"
            + "  } ]\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_withTemplate_withOverrideV2() throws IOException {
    String envId = "env_with_serviceoverride_inputs";
    String serviceId = "serviceId";

    mockEnvMetadataWithNoRuntimeInputs(envId);
    mockEnvWithServiceOverrideInputsV2(serviceId, envId);

    refreshContext.setResolvedTemplatesYamlNode(buildStageTemplateNodeEnvRuntime(serviceId));

    YamlNode entityNode = buildEnvYamlNode(envId);
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    when(overrideV2ValidationHelper.isOverridesV2Enabled(anyString())).thenReturn(true);
    refreshHelper.validateEnvironmentInputs(entityNode, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isFalse();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(entityNode, refreshContext);

    // added env global overrides to environment inputs
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"env_with_serviceoverride_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"infrastructureDefinitions\" : \"<+input>\",\n"
            + "  \"serviceOverrideInputs\" : {\n"
            + "    \"variables\" : [ {\n"
            + "      \"name\" : \"aa\",\n"
            + "      \"type\" : \"String\",\n"
            + "      \"value\" : \"<+input>\"\n"
            + "    } ]\n"
            + "  }\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_WithGitopsClustersAndDeployToAll() throws IOException {
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    YamlNode env_with_inputs = buildGitopsEnvYamlNodeWithRuntimeInputs("gitops_env_with_inputs", true);
    refreshHelper.validateEnvironmentInputs(env_with_inputs, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(env_with_inputs, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"gitops_env_with_inputs\",\n"
            + "  \"deployToAll\" : true\n"
            + "}");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void validateEnvironmentInputs_WithGitopsClustersAndWithoutDeployToAll() throws IOException {
    InputsValidationResponse validationResponse = InputsValidationResponse.builder().isValid(true).build();
    YamlNode env_with_inputs = buildGitopsEnvYamlNodeWithRuntimeInputs("gitops_env_with_inputs", false);
    refreshHelper.validateEnvironmentInputs(env_with_inputs, refreshContext, validationResponse);

    assertThat(validationResponse.isValid()).isTrue();

    JsonNode jsonNode = refreshHelper.refreshEnvironmentInputs(env_with_inputs, refreshContext);
    assertThat(jsonNode.toPrettyString())
        .isEqualTo("{\n"
            + "  \"environmentRef\" : \"gitops_env_with_inputs\",\n"
            + "  \"deployToAll\" : false,\n"
            + "  \"gitOpsClusters\" : \"<+input>\"\n"
            + "}");
  }

  private YamlNode buildEnvYamlNode(String identifier) throws IOException {
    String yaml = "environmentRef: " + identifier + "\n"
        + "deployToAll: false\n"
        + "infrastructureDefinitions: <+input>";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildEnvYamlNodeWithRuntimeInputs(String identifier) throws IOException {
    String yaml = "environmentRef: " + identifier + "\n"
        + "deployToAll: false\n"
        + "environmentInputs: <+input>\n"
        + "serviceOverrideInputs: <+input>\n"
        + "infrastructureDefinitions: <+input>";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildGitopsEnvYamlNodeWithRuntimeInputs(String identifier, boolean deployToAll) throws IOException {
    String yaml = "environmentRef: " + identifier + "\n"
        + "deployToAll: " + deployToAll + "\n"
        + "gitOpsClusters: <+input>";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildEnvYamlNodeWithInfraDefAsExpression(String identifier) throws IOException {
    String yaml = "environmentRef: " + identifier + "\n"
        + "deployToAll: false\n"
        + "infrastructureDefinitions:\n"
        + "- identifier: <+env.name>";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private void mockEnvWithNoRuntimeInputs(String identifier) {
    Environment environment = EnvironmentMapper.toNGEnvironmentEntity("accountId",
        NGEnvironmentConfig.builder()
            .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                         .identifier(identifier)
                                         .type(EnvironmentType.PreProduction)
                                         .tags(Map.of("k", ""))
                                         .build())
            .build(),
        null);

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), eq(identifier), eq(false));
    doReturn(null).when(environmentService).createEnvironmentInputsYaml(eq(scopeInfo), eq(identifier), any());
  }

  private void mockEnvMetadataWithNoRuntimeInputs(String identifier) {
    Environment environment = EnvironmentMapper.toNGEnvironmentEntity("accountId",
        NGEnvironmentConfig.builder()
            .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                         .identifier(identifier)
                                         .type(EnvironmentType.PreProduction)
                                         .tags(Map.of("k", ""))
                                         .build())
            .build(),
        null);

    doReturn(Optional.of(environment)).when(environmentService).getMetadata(eq(scopeInfo), eq(identifier), eq(false));
    doReturn(null).when(environmentService).createEnvironmentInputsYaml(eq(scopeInfo), eq(identifier), any());
  }

  private void mockEnvWithRuntimeInputs(String identifier) {
    Environment environment = EnvironmentMapper.toNGEnvironmentEntity("accountId",
        NGEnvironmentConfig.builder()
            .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                         .identifier(identifier)
                                         .type(EnvironmentType.PreProduction)
                                         .tags(Map.of("k", ""))
                                         .build())
            .build(),
        null);

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), eq(identifier), eq(false));
    doReturn("environmentInputs:\n"
        + "    identifier: " + identifier + " \n"
        + "    type: PreProduction\n"
        + "    variables:\n"
        + "      - name: numvar\n"
        + "        type: Number\n"
        + "        value: <+input>")
        .when(environmentService)
        .createEnvironmentInputsYaml(eq(scopeInfo), eq(identifier), any());
  }

  private void mockEnvWithServiceOverrideInputs(String serviceId, String envId) {
    doReturn("serviceOverrideInputs:\n"
        + "              variables:\n"
        + "                - name: aa\n"
        + "                  type: String\n"
        + "                  value: <+input>")
        .when(serviceOverrideService)
        .createServiceOverrideInputsYaml(anyString(), anyString(), anyString(), eq(envId), eq(serviceId));
  }

  private void mockEnvWithServiceOverrideInputsV2(String serviceId, String envId) {
    doReturn("serviceOverrideInputs:\n"
        + "              variables:\n"
        + "                - name: aa\n"
        + "                  type: String\n"
        + "                  value: <+input>")
        .when(serviceOverridesServiceV2)
        .createServiceOverrideInputsYaml(anyString(), anyString(), anyString(), eq(envId), eq(serviceId));
  }

  private EntityRefreshContext getRefreshContext() {
    return EntityRefreshContext.builder().accountId("accountId").orgId("orgId").projectId("projectId").build();
  }

  private YamlNode buildStageTemplateNodeSvcEnvRuntime() throws IOException {
    String yaml = "template:\n"
        + "  name: stage_template\n"
        + "  type: Stage\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  spec:\n"
        + "    type: Deployment\n"
        + "    spec:\n"
        + "      deploymentType: NativeHelm\n"
        + "      service:\n"
        + "        serviceRef: <+input>\n"
        + "        serviceInputs: <+input>\n"
        + "      environment:\n"
        + "        environmentRef: <+input>\n"
        + "        deployToAll: false\n"
        + "        environmentInputs: <+input>\n"
        + "        serviceOverrideInputs: <+input>\n"
        + "        infrastructureDefinitions: <+input>\n"
        + "  identifier: wellsfargo\n"
        + "  versionLabel: v1\n";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildStageTemplateNodeEnvRuntime(String serviceRef) throws IOException {
    String yaml = "template:\n"
        + "  name: stage_template\n"
        + "  type: Stage\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  spec:\n"
        + "    type: Deployment\n"
        + "    spec:\n"
        + "      deploymentType: NativeHelm\n"
        + "      service:\n"
        + "        serviceRef: " + serviceRef + "\n"
        + "        serviceInputs: <+input>\n"
        + "      environment:\n"
        + "        environmentRef: <+input>\n"
        + "        deployToAll: false\n"
        + "        environmentInputs: <+input>\n"
        + "        serviceOverrideInputs: <+input>\n"
        + "        infrastructureDefinitions: <+input>\n"
        + "  identifier: stage_template\n"
        + "  versionLabel: v1\n";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildStageTemplateNodeMultiSvcEnvRuntime(String serviceRef) throws IOException {
    String yaml = "template:\n"
        + "  name: stage_template\n"
        + "  type: Stage\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  spec:\n"
        + "    type: Deployment\n"
        + "    spec:\n"
        + "      deploymentType: NativeHelm\n"
        + "      services:\n"
        + "        serviceRef: " + serviceRef + "\n"
        + "        serviceInputs: <+input>\n"
        + "      environment:\n"
        + "        environmentRef: <+input>\n"
        + "        deployToAll: false\n"
        + "        environmentInputs: <+input>\n"
        + "        serviceOverrideInputs: <+input>\n"
        + "        infrastructureDefinitions: <+input>\n"
        + "  identifier: stage_template\n"
        + "  versionLabel: v1\n";
    return YamlNode.fromYamlPath(yaml, "");
  }

  private YamlNode buildStageTemplateNodeMultiSvcEnvRuntime2() throws IOException {
    String yaml = "template:\n"
        + "  name: stage_template\n"
        + "  type: Stage\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  spec:\n"
        + "    type: Deployment\n"
        + "    spec:\n"
        + "      deploymentType: NativeHelm\n"
        + "      service:\n"
        + "        useFromStage:\n"
        + "          stage: s1\n"
        + "        serviceRef: <+input>\n"
        + "        serviceInputs: <+input>\n"
        + "      environment:\n"
        + "        environmentRef: <+input>\n"
        + "        deployToAll: false\n"
        + "        environmentInputs: <+input>\n"
        + "        serviceOverrideInputs: <+input>\n"
        + "        infrastructureDefinitions: <+input>\n"
        + "  identifier: stage_template\n"
        + "  versionLabel: v1\n";
    return YamlNode.fromYamlPath(yaml, "");
  }
}
