/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.commonconstants.CIExecutionConstants.BUILD_CACHE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.execution.serializer.vm.constants.VmStepSerializerConstants.HARNESS_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_KUMAR;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.integrationstage.ci.CIStepGroupUtils;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtility;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.execution.utils.ci.HarnessImageUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsOidcSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureOidcSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureSecretType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SSCAModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.SubCategory;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.slsa.beans.SlsaConfig;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.action.IgnoreFailureActionConfig;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

public class CIStepGroupUtilsTest extends CIExecutionTestBase {
  @Inject CIStepGroupUtils ciStepGroupUtils;
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock CIFeatureFlagService featureFlagService;
  @Inject SSCALicenseHelper sscaLicenseHelper;
  @Mock NgLicenseHttpClient ngLicenseHttpClient;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private HarnessImageUtils harnessImageUtils;

  @Mock private ExecutionSource executionSource;

  @Mock private ManualExecutionSource manualExecutionSource;

  private final String HARNESS_CACHE_S3_BUCKET = "HARNESS_CACHE_S3_BUCKET";
  private final String HARNESS_CACHE_S3_REGION = "HARNESS_CACHE_S3_REGION";
  private final String HARNESS_CACHE_S3_ENDPOINT = "HARNESS_CACHE_S3_ENDPOINT";
  private final String HARNESS_SELF_HOSTED = "HARNESS_SELF_HOSTED";
  private final String HARNESS_CACHE_S3_ACCESS_KEY = "HARNESS_CACHE_S3_ACCESS_KEY";
  private final String HARNESS_CACHE_S3_SECRET_KEY = "HARNESS_CACHE_S3_SECRET_KEY";
  private final String HARNESS_AWS_OIDC_TOKEN_ID = "HARNESS_AWS_OIDC_TOKEN_ID";
  private final String HARNESS_ASSUME_ROLE = "HARNESS_ASSUME_ROLE";
  private final String HARNESS_CACHE_AWS_CONNECTOR = "HARNESS_CACHE_AWS_CONNECTOR";
  private final String HARNESS_USER_ROLE_ARN = "HARNESS_USER_ROLE_ARN";
  private final String HARNESS_USER_ROLE_EXTERNAL_ID = "HARNESS_USER_ROLE_EXTERNAL_ID";
  private final String HARNESS_CACHE_AWS_INHERIT_FROM_DELEGATE = "HARNESS_CACHE_AWS_INHERIT_FROM_DELEGATE";
  private final String HARNESS_GCP_JSON_KEY = "HARNESS_GCP_JSON_KEY";
  private final String HARNESS_CACHE_GCP_CONNECTOR = "HARNESS_CACHE_GCP_CONNECTOR";
  private final String HARNESS_GCP_OIDC_TOKEN_ID = "HARNESS_GCP_OIDC_TOKEN_ID";
  private final String HARNESS_PROJECT_NUMBER = "HARNESS_PROJECT_NUMBER";
  private final String HARNESS_POOL_ID = "HARNESS_POOL_ID";
  private final String HARNESS_PROVIDER_ID = "HARNESS_PROVIDER_ID";
  private final String HARNESS_SERVICE_ACCOUNT_EMAIL = "HARNESS_SERVICE_ACCOUNT_EMAIL";
  private final String HARNESS_AZURE_CLIENT_ID = "HARNESS_AZURE_CLIENT_ID";
  private final String HARNESS_AZURE_TENANT_ID = "HARNESS_AZURE_TENANT_ID";
  private final String HARNESS_AZURE_CLIENT_SECRET = "HARNESS_AZURE_CLIENT_SECRET";
  private final String HARNESS_AZURE_OIDC_TOKEN_ID = "HARNESS_AZURE_OIDC_TOKEN_ID";
  private final String HARNESS_CACHE_AZURE_CONNECTOR = "HARNESS_CACHE_AZURE_CONNECTOR";

  private final String accountID = "accountID";

  private final ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setup() {
    on(ciStepGroupUtils).set("featureFlagService", featureFlagService);
    on(sscaLicenseHelper).set("ngLicenseHttpClient", ngLicenseHttpClient);
    on(ciStepGroupUtils).set("connectorUtils", connectorUtils);
    on(ciStepGroupUtils).set("ciExecutionConfigService", ciExecutionConfigService);
    on(ciStepGroupUtils).set("harnessImageUtils", harnessImageUtils);
    when(ngLicenseHttpClient.getAccountLicensesDTO(any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          Call<ResponseDTO<AccountLicenseDTO>> call = mock(Call.class);
          Map<ModuleType, List<ModuleLicenseDTO>> testLicenses = new HashMap<>();
          ModuleLicenseDTO sscaModuleLicneseDTO = SSCAModuleLicenseDTO.builder()
                                                      .moduleType(ModuleType.SSCA)
                                                      .status(LicenseStatus.ACTIVE)
                                                      .startTime(1594684800000L) // 14 July 2020 00:00:00
                                                      .build();
          testLicenses.put(ModuleType.SSCA, List.of(sscaModuleLicneseDTO));
          when(call.execute())
              .thenReturn(Response.success(
                  ResponseDTO.newResponse(AccountLicenseDTO.builder().allModuleLicenses(testLicenses).build())));
          when(call.clone()).thenReturn(null);
          return call;
        });
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void createExecutionWrapperWithLiteEngineSteps() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setInfrastructure(
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .build()))
                    .build())
            .build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);
    leWrapperConfig.getStep().has("failureStrategies");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void initTimeWhenInfraIsVmHostedAndQueueIsDisabled() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setInfrastructure(
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .build()))
                    .build())
            .build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);
    JsonNode timeout = leWrapperConfig.getStep().get("timeout");
    assertEquals("10h", timeout.asText());
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void initTimeWhenInfraIsVmHostedAndQueueIsEnabled() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setInfrastructure(
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraYaml.HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .build()))
                    .build())
            .build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);

    JsonNode timeout = leWrapperConfig.getStep().get("timeout");
    assertEquals("10h", timeout.asText());
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void initTimeWhenInfraIsKubernetesDirect() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();

    Infrastructure k8Infra = K8sDirectInfraYaml.builder()
                                 .spec(K8sDirectInfraYamlSpec.builder().build())
                                 .type(Infrastructure.Type.KUBERNETES_DIRECT)
                                 .build();
    integrationStageNode.getIntegrationStageConfig().setInfrastructure(k8Infra);
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any()))
        .thenReturn(StepImageConfig.builder().image("gitCloneImage").build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);

    JsonNode timeout = leWrapperConfig.getStep().get("timeout");
    assertEquals("10m", timeout.asText());
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void initTimeWhenInfraIsVmPoolYaml() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    VmInfraYaml awsVmInfraYaml = VmInfraYaml.builder()
                                     .spec(VmPoolYaml.builder()
                                               .spec(VmPoolYamlSpec.builder()
                                                         .identifier("poolId")
                                                         .poolName(ParameterField.createValueField(null))
                                                         .build())
                                               .build())
                                     .build();

    integrationStageNode.getIntegrationStageConfig().setInfrastructure(awsVmInfraYaml);
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty();
    ExecutionWrapperConfig leWrapperConfig = executionWrapperConfigs.get(0);

    JsonNode timeout = leWrapperConfig.getStep().get("timeout");
    assertEquals("15m", timeout.asText());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testProvenanceGeneration() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setSlsa_provenance(getSlsaConfig());
    integrationStageNode.getIntegrationStageConfig().getExecution().getSteps().add(
        ExecutionWrapperConfig.builder().step(getDockerBuildAndPushJsonNode()).build());
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any()))
        .thenReturn(StepImageConfig.builder().image("gitCloneImage").build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty().hasSize(6);
    ExecutionWrapperConfig stepGroupConfig = executionWrapperConfigs.get(5);
    assertThat(stepGroupConfig.getStepGroup()).isNotNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testProvenanceGenerationInParallelNode() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setSlsa_provenance(getSlsaConfig());
    ArrayNode parallelNodes = (ArrayNode) ciExecutionPlanTestHelper.getRunAndPluginStepsInParallelAsJsonNode();
    parallelNodes.add(new ObjectMapper().createObjectNode().set("step", getDockerBuildAndPushJsonNode()));
    integrationStageNode.getIntegrationStageConfig().getExecution().getSteps().add(
        ExecutionWrapperConfig.builder().parallel(parallelNodes).build());
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any()))
        .thenReturn(StepImageConfig.builder().image("gitCloneImage").build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, false);
    assertThat(executionWrapperConfigs).isNotEmpty().hasSize(6);
    //    ExecutionWrapperConfig stepGroupConfig = executionWrapperConfigs.get(5);
    //    assertThat(stepGroupConfig.getStepGroup()).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProvenanceGenerationNodeWithInject() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setSlsa_provenance(getSlsaConfig());
    ObjectNode injectSteps = (ObjectNode) ciExecutionPlanTestHelper.getRunAndPluginStepsInInjectAsJsonNode();
    integrationStageNode.getIntegrationStageConfig().getExecution().getSteps().add(
        ExecutionWrapperConfig.builder().insert(injectSteps).build());
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any()))
        .thenReturn(StepImageConfig.builder().image("gitCloneImage").build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, true);
    assertThat(executionWrapperConfigs).isNotEmpty().hasSize(6);
    ExecutionWrapperConfig stepGroupConfig = executionWrapperConfigs.get(5);
    assertThat(stepGroupConfig.getInsert().get("steps").get(2).get("stepGroup").get("steps").size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProvenanceGenerationNodeInParallelWithInject() {
    IntegrationStageNode integrationStageNode = ciExecutionPlanTestHelper.getIntegrationStageNode();
    integrationStageNode.getIntegrationStageConfig().setSlsa_provenance(getSlsaConfig());
    ArrayNode parallelNodes = (ArrayNode) ciExecutionPlanTestHelper.getRunAndPluginStepsAndInjectInParallelAsJsonNode();
    integrationStageNode.getIntegrationStageConfig().getExecution().getSteps().add(
        ExecutionWrapperConfig.builder().parallel(parallelNodes).build());
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any()))
        .thenReturn(StepImageConfig.builder().image("gitCloneImage").build());
    List<ExecutionWrapperConfig> executionWrapperConfigs = ciStepGroupUtils.createExecutionWrapperWithInitializeStep(
        integrationStageNode, ciExecutionPlanTestHelper.getCIExecutionArgs(), ciExecutionPlanTestHelper.getCICodebase(),
        integrationStageNode.getIntegrationStageConfig().getInfrastructure(), accountID, null, null, true);
    assertThat(executionWrapperConfigs).isNotEmpty().hasSize(6);
    ExecutionWrapperConfig stepGroupConfig = executionWrapperConfigs.get(5);
    assertThat(
        stepGroupConfig.getParallel().get(2).get("insert").get("steps").get(2).get("stepGroup").get("steps").size())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheS3Step() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "us-east-1");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "https://s3.amazonaws.com");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "connector-ref");

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("cache-key"));
    when(caching.getEnabled()).thenReturn(ParameterField.createValueField(true));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(false));

    // Act
    ExecutionWrapperConfig result = ciStepGroupUtils.getSelfHostedRestoreCacheS3Step(caching, settingsMap, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("false", result.getStep().get("spec").get("failIfKeyNotFound").asText());
    assertEquals("test-bucket", result.getStep().get("spec").get("bucket").asText());
    assertEquals("us-east-1", result.getStep().get("spec").get("region").asText());
    assertEquals("connector-ref", result.getStep().get("spec").get("connectorRef").asText());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheS3StepK8PassesResourcesAndRunAsUser() {
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "us-east-1");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "https://s3.amazonaws.com");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "connector-ref");

    ContainerResource resources =
        ContainerResource.builder()
            .limits(ContainerResource.Limits.builder().memory(ParameterField.createValueField("128Mi")).build())
            .build();

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/cache/path")));
    when(caching.getResources()).thenReturn(resources);
    when(caching.getRunAsUser()).thenReturn(ParameterField.createValueField(1000));

    ExecutionWrapperConfig result = ciStepGroupUtils.getSelfHostedRestoreCacheS3Step(caching, settingsMap, null, true);

    assertNotNull(result);
    JsonNode spec = result.getStep().get("spec");
    assertThat(spec.has("resources")).isTrue();
    assertThat(resolveRunAsUserFromSpec(spec)).isEqualTo(1000);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheS3StepNonK8OmitsResourcesAndRunAsUser() {
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "us-east-1");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "https://s3.amazonaws.com");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "connector-ref");

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/cache/path")));

    ExecutionWrapperConfig result = ciStepGroupUtils.getSelfHostedRestoreCacheS3Step(caching, settingsMap, null, false);

    assertNotNull(result);
    JsonNode spec = result.getStep().get("spec");
    // Non-K8 passes null for resources/runAsUser; Jackson omits null fields from JSON (no key), not JSON null.
    assertThat(spec.has("resources")).isFalse();
    assertThat(spec.has("runAsUser")).isFalse();
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedSaveCacheS3StepK8PassesResourcesAndRunAsUser() {
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "us-east-1");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "https://s3.amazonaws.com");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "connector-ref");

    ContainerResource resources =
        ContainerResource.builder()
            .limits(ContainerResource.Limits.builder().cpu(ParameterField.createValueField("0.5")).build())
            .build();

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));
    when(caching.getResources()).thenReturn(resources);
    when(caching.getRunAsUser()).thenReturn(ParameterField.createValueField(2000));

    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedSaveCacheS3Step(caching, settingsMap, "acct", null, true);

    JsonNode spec = result.getStep().get("spec");
    assertThat(spec.has("resources")).isTrue();
    assertThat(resolveRunAsUserFromSpec(spec)).isEqualTo(2000);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedSaveCacheS3Step() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "us-east-1");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "https://s3.amazonaws.com");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "connector-ref");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedSaveCacheS3Step(caching, settingsMap, accountId, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("test-bucket", result.getStep().get("spec").get("bucket").asText());
    assertEquals("us-east-1", result.getStep().get("spec").get("region").asText());
    assertEquals("connector-ref", result.getStep().get("spec").get("connectorRef").asText());
    assertEquals("true", result.getStep().get("spec").get("override").asText());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheGCSStep() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "gcs-test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "gcs-connector-ref");

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("gcs-cache-key"));
    when(caching.getEnabled()).thenReturn(ParameterField.createValueField(true));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/gcs/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(false));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedRestoreCacheGCSStep(caching, settingsMap, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("gcs-cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/gcs/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("false", result.getStep().get("spec").get("failIfKeyNotFound").asText());
    assertEquals("gcs-test-bucket", result.getStep().get("spec").get("bucket").asText());
    assertEquals("gcs-connector-ref", result.getStep().get("spec").get("connectorRef").asText());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedSaveCacheGCSStep() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "gcs-test-bucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "gcs-connector-ref");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("gcs-cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/gcs/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedSaveCacheGCSStep(caching, settingsMap, accountId, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("gcs-cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/gcs/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("gcs-test-bucket", result.getStep().get("spec").get("bucket").asText());
    assertEquals("gcs-connector-ref", result.getStep().get("spec").get("connectorRef").asText());
    assertEquals("true", result.getStep().get("spec").get("override").asText());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheAzureStep() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "azure-test-container");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azure-test-account");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "azure-connector-ref");

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("azure-cache-key"));
    when(caching.getEnabled()).thenReturn(ParameterField.createValueField(true));
    when(caching.getPaths())
        .thenReturn(ParameterField.createValueField(Collections.singletonList("/azure/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(false));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedRestoreCacheAzureStep(caching, settingsMap, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("azure-cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/azure/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("false", result.getStep().get("spec").get("failIfKeyNotFound").asText());
    assertEquals("azure-test-container", result.getStep().get("spec").get("containerName").asText());
    assertEquals("azure-test-account", result.getStep().get("spec").get("storageAccount").asText());
    assertEquals("azure-connector-ref", result.getStep().get("spec").get("connectorRef").asText());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedSaveCacheAzureStep() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "azure-test-container");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azure-test-account");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "azure-connector-ref");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("azure-cache-key"));
    when(caching.getPaths())
        .thenReturn(ParameterField.createValueField(Collections.singletonList("/azure/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedSaveCacheAzureStep(caching, settingsMap, accountId, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("tar", result.getStep().get("spec").get("archiveFormat").asText());
    assertEquals("azure-cache-key", result.getStep().get("spec").get("key").asText());
    assertEquals("/azure/cache/path", result.getStep().get("spec").get("sourcePaths").get(0).asText());
    assertEquals("azure-test-container", result.getStep().get("spec").get("containerName").asText());
    assertEquals("azure-test-account", result.getStep().get("spec").get("storageAccount").asText());
    assertEquals("azure-connector-ref", result.getStep().get("spec").get("connectorRef").asText());
    assertEquals("true", result.getStep().get("spec").get("override").asText());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedRestoreCacheAzureStepWithConditionalExecution() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "azure-container");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azure-account");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "azure-connector");

    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/path")));

    ParameterField<String> expressionValue =
        ParameterField.<String>builder().value("<+pipeline.variables.enableCache>").build();

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedRestoreCacheAzureStep(caching, settingsMap, expressionValue, false);

    // Assert
    assertNotNull(result);
    assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("<+pipeline.variables.enableCache>", result.getStep().get("when").get("condition").asText());
    assertEquals("Success", result.getStep().get("when").get("stageStatus").asText());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSelfHostedSaveCacheAzureStepWithOverrideFalse() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "azure-container");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azure-account");
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "azure-connector");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("azure-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/azure/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(false));

    // Act
    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSelfHostedSaveCacheAzureStep(caching, settingsMap, accountId, null, false);

    // Assert
    assertNotNull(result);
    assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
    assertEquals("false", result.getStep().get("spec").get("override").asText());
    assertEquals(
        "Ignore", result.getStep().get("failureStrategies").get(0).get("onFailure").get("action").get("type").asText());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSaveCacheStepHosted() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, "accessKeyRef");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, "secretKeyRef");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("aws-cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/aws/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    Infrastructure infrastructure = mock(Infrastructure.class);
    // Mocking the static method
    try (MockedStatic<CIStepInfoUtils> mocked = mockStatic(CIStepInfoUtils.class)) {
      mocked.when(() -> CIStepInfoUtils.useS3ForCacheIntel(featureFlagService, accountId, infrastructure))
          .thenReturn(true);

      // Act
      ExecutionWrapperConfig result =
          ciStepGroupUtils.getSaveCacheStep(caching, settingsMap, accountId, infrastructure, true, null);

      // Assert
      assertNotNull(result);
      assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
      assertEquals("aws-cache-key", result.getStep().get("spec").get("envVariables").get("PLUGIN_CACHE_KEY").asText());
      assertEquals("/aws/cache/path", result.getStep().get("spec").get("envVariables").get("PLUGIN_MOUNT").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_EXIT_CODE").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_REBUILD").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_OVERRIDE").asText());
      assertEquals("false", result.getStep().get("spec").get("envVariables").get("PLUGIN_DEBUG").asText());
      assertEquals("private", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACL").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_AUTO_CACHE").asText());
      assertEquals(
          "test-account-id", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCOUNT_ID").asText());
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSaveCacheStepSelfHosted() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, "accessKeyRef");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, "secretKeyRef");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("aws-cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/aws/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    Infrastructure infrastructure = mock(Infrastructure.class);
    // Mocking the static method
    try (MockedStatic<CIStepInfoUtils> mocked = mockStatic(CIStepInfoUtils.class)) {
      mocked.when(() -> CIStepInfoUtils.useS3ForCacheIntel(featureFlagService, accountId, infrastructure))
          .thenReturn(true);

      // Act
      ExecutionWrapperConfig result =
          ciStepGroupUtils.getSaveCacheStep(caching, settingsMap, accountId, infrastructure, false, null);

      // Assert
      assertNotNull(result);
      assertEquals("save-cache-harness", result.getStep().get("identifier").asText());
      assertEquals("aws-cache-key", result.getStep().get("spec").get("envVariables").get("PLUGIN_CACHE_KEY").asText());
      assertEquals("/aws/cache/path", result.getStep().get("spec").get("envVariables").get("PLUGIN_MOUNT").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_EXIT_CODE").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_REBUILD").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_OVERRIDE").asText());
      assertEquals("false", result.getStep().get("spec").get("envVariables").get("PLUGIN_DEBUG").asText());
      assertNull(result.getStep().get("spec").get("envVariables").get("PLUGIN_ACL"));
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_AUTO_CACHE").asText());
      assertEquals("intel", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCOUNT_ID").asText());
      assertEquals("<+secrets.getValue(\"accessKeyRef\")>",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCESS_KEY").asText());
      assertEquals("<+secrets.getValue(\"secretKeyRef\")>",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_SECRET_KEY").asText());
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetRestoreCacheStepHosted() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, "accessKeyRef");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, "secretKeyRef");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("aws-cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/aws/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    Infrastructure infrastructure = mock(Infrastructure.class);
    // Mocking the static method
    try (MockedStatic<CIStepInfoUtils> mocked = mockStatic(CIStepInfoUtils.class)) {
      mocked.when(() -> CIStepInfoUtils.useS3ForCacheIntel(featureFlagService, accountId, infrastructure))
          .thenReturn(true);

      // Act
      ExecutionWrapperConfig result =
          ciStepGroupUtils.getRestoreCacheStep(caching, settingsMap, accountId, infrastructure, true, null);

      // Assert
      assertNotNull(result);
      assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
      assertEquals("aws-cache-key", result.getStep().get("spec").get("envVariables").get("PLUGIN_CACHE_KEY").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_EXIT_CODE").asText());
      assertEquals("false",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT").asText());
      assertEquals("private", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACL").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_AUTO_CACHE").asText());
      assertEquals(
          "test-account-id", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCOUNT_ID").asText());
    }
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetRestoreCacheStepSelfHosted() {
    // Arrange
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, "accessKeyRef");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, "secretKeyRef");

    String accountId = "test-account-id";
    Caching caching = mock(Caching.class);
    when(caching.getKey()).thenReturn(ParameterField.createValueField("aws-cache-key"));
    when(caching.getPaths()).thenReturn(ParameterField.createValueField(Collections.singletonList("/aws/cache/path")));
    when(caching.getOverride()).thenReturn(ParameterField.createValueField(true));

    Infrastructure infrastructure = mock(Infrastructure.class);
    // Mocking the static method
    try (MockedStatic<CIStepInfoUtils> mocked = mockStatic(CIStepInfoUtils.class)) {
      mocked.when(() -> CIStepInfoUtils.useS3ForCacheIntel(featureFlagService, accountId, infrastructure))
          .thenReturn(true);

      // Act
      ExecutionWrapperConfig result =
          ciStepGroupUtils.getRestoreCacheStep(caching, settingsMap, accountId, infrastructure, false, null);

      // Assert
      assertNotNull(result);
      assertEquals("restore-cache-harness", result.getStep().get("identifier").asText());
      assertEquals("aws-cache-key", result.getStep().get("spec").get("envVariables").get("PLUGIN_CACHE_KEY").asText());
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_EXIT_CODE").asText());
      assertEquals("false",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT").asText());
      assertNull(result.getStep().get("spec").get("envVariables").get("PLUGIN_ACL"));
      assertEquals("true", result.getStep().get("spec").get("envVariables").get("PLUGIN_AUTO_CACHE").asText());
      assertEquals("intel", result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCOUNT_ID").asText());
      assertEquals("<+secrets.getValue(\"accessKeyRef\")>",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_ACCESS_KEY").asText());
      assertEquals("<+secrets.getValue(\"secretKeyRef\")>",
          result.getStep().get("spec").get("envVariables").get("PLUGIN_SECRET_KEY").asText());
    }
  }

  private static JsonNode getDockerBuildAndPushJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "docker");

    stepElementConfig.put("type", "BuildAndPushDockerRegistry");
    stepElementConfig.put("name", "docker");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", "docker");
    stepSpecType.put("name", "docker");
    stepSpecType.put("connectorRef", "connector");
    stepSpecType.put("repo", "repo");

    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add("tag1");

    stepSpecType.set("tags", arrayNode);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private SlsaConfig getSlsaConfig() {
    return SlsaConfig.builder().enabled(ParameterField.createValueField(true)).build();
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsForAWSManualProject() {
    // Arrange
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    String accessKey = "accessKey";
    String secretKeyRef = "secretKeyRef";

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .config(AwsManualConfigSpecDTO.builder()
                        .accessKey(accessKey)
                        .secretKeyRef(SecretRefData.builder().identifier(secretKeyRef).scope(Scope.PROJECT).build())
                        .build())
            .build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
    assertThat(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY).getValue()).isEqualTo("accessKey");
    assertThat(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY).getValue())
        .isEqualTo("<+secrets.getValue(\"secretKeyRef\")>");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsAWSAccountLevel() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    String accessKey = "accessKey";
    String secretKeyRef = "secretKeyRef";

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .config(AwsManualConfigSpecDTO.builder()
                        .accessKey(accessKey)
                        .secretKeyRef(SecretRefData.builder().identifier(secretKeyRef).scope(Scope.ACCOUNT).build())
                        .build())
            .build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
    assertThat(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY).getValue()).isEqualTo("accessKey");
    assertThat(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY).getValue())
        .isEqualTo("<+secrets.getValue(\"account.secretKeyRef\")>");
  }
  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsUnSupportedAWSConnector() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsAWSIRSAWithCrossAccount() {
    // IRSA connectors carry no credential spec (getConfig() is null), but a configured cross-account role must still
    // be emitted as HARNESS_USER_ROLE_ARN so cache-proxy can perform the secondary (two-hop) AssumeRole. This is the
    // fix for the build-intelligence path dropping cross-account access for config-less credential types.
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.IRSA)
            .crossAccountAccess(CrossAccountAccessDTO.builder()
                                    .crossAccountRoleArn("arn:aws:iam::123456789012:role/cross-account-role")
                                    .externalId("externalIdValue")
                                    .build())
            .build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNotNull(envVarMap.get(HARNESS_USER_ROLE_ARN));
    assertThat(envVarMap.get(HARNESS_USER_ROLE_ARN).getValue())
        .isEqualTo("arn:aws:iam::123456789012:role/cross-account-role");
    assertThat(envVarMap.get(HARNESS_USER_ROLE_EXTERNAL_ID).getValue()).isEqualTo("externalIdValue");
    // IRSA base identity is resolved from the pod environment on the cache-proxy side.
    assertThat(envVarMap.get(HARNESS_CACHE_AWS_INHERIT_FROM_DELEGATE).getValue()).isEqualTo("true");
    assertThat(envVarMap.get(HARNESS_CACHE_AWS_CONNECTOR).getValue()).isEqualTo("true");
    // No static keys for IRSA.
    assertNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsAWSInheritFromDelegateWithCrossAccount() {
    // INHERIT_FROM_DELEGATE connectors also carry no credential spec (getConfig() is null), and the cross-account
    // block must emit HARNESS_USER_ROLE_ARN for them too. This test ensures the restructured branch handles both
    // config-less types (IRSA and INHERIT_FROM_DELEGATE) identically.
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .crossAccountAccess(CrossAccountAccessDTO.builder()
                                    .crossAccountRoleArn("arn:aws:iam::987654321098:role/delegate-cross-account")
                                    .externalId("inherit-external-id")
                                    .build())
            .build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNotNull(envVarMap.get(HARNESS_USER_ROLE_ARN));
    assertThat(envVarMap.get(HARNESS_USER_ROLE_ARN).getValue())
        .isEqualTo("arn:aws:iam::987654321098:role/delegate-cross-account");
    assertThat(envVarMap.get(HARNESS_USER_ROLE_EXTERNAL_ID).getValue()).isEqualTo("inherit-external-id");
    assertThat(envVarMap.get(HARNESS_CACHE_AWS_INHERIT_FROM_DELEGATE).getValue()).isEqualTo("true");
    assertThat(envVarMap.get(HARNESS_CACHE_AWS_CONNECTOR).getValue()).isEqualTo("true");
    assertNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsAWSOrgLevel() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    String accessKey = "accessKey";
    String secretKeyRef = "secretKeyRef";

    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .config(AwsManualConfigSpecDTO.builder()
                        .accessKey(accessKey)
                        .secretKeyRef(SecretRefData.builder().identifier(secretKeyRef).scope(Scope.ORG).build())
                        .build())
            .build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY));
    assertNotNull(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY));
    assertThat(envVarMap.get(HARNESS_CACHE_S3_ACCESS_KEY).getValue()).isEqualTo("accessKey");
    assertThat(envVarMap.get(HARNESS_CACHE_S3_SECRET_KEY).getValue())
        .isEqualTo("<+secrets.getValue(\"org.secretKeyRef\")>");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsAWSOIDC() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    String iamRoleArn = "iamrole";
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.OIDC_AUTHENTICATION)
                                            .config(AwsOidcSpecDTO.builder().iamRoleArn(iamRoleArn).build())
                                            .build();

    Map<EnvVariableEnum, String> envToSecretsMap = new HashMap<>();
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "oidcTokenIdValue");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_ASSUME_ROLE, "assumeRoleValue");

    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .envToSecretsMap(envToSecretsMap)
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertThat(envVarMap.get(HARNESS_AWS_OIDC_TOKEN_ID).getValue()).isEqualTo("oidcTokenIdValue");
    assertThat(envVarMap.get(HARNESS_ASSUME_ROLE).getValue()).isEqualTo("assumeRoleValue");
    assertThat(envVarMap.get(HARNESS_CACHE_AWS_CONNECTOR).getValue()).isEqualTo("true");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsGCSManual() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    ConnectorConfigDTO connectorConfigDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder()
                    .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                    .config(GcpManualDetailsDTO.builder()
                                .secretKeyRef(
                                    SecretRefData.builder().identifier("secretKeyRef").scope(Scope.ACCOUNT).build())
                                .build())
                    .build())
            .build();

    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GCP)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertThat(envVarMap.get(HARNESS_GCP_JSON_KEY).getValue())
        .isEqualTo("<+secrets.getValue(\"account.secretKeyRef\")>");

    assertThat(envVarMap.get(HARNESS_CACHE_GCP_CONNECTOR).getValue()).isEqualTo("true");
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSelfHostedAuthCredentialsGCSOIDC() {
    String accountId = "testAccountId";
    String orgId = "testOrgId";
    String projectId = "testProjectId";
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_CONNECTOR, "testConnector");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    ConnectorConfigDTO connectorConfigDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.OIDC_AUTHENTICATION)
                            .config(GcpOidcDetailsDTO.builder().build())
                            .build())
            .build();

    Map<EnvVariableEnum, String> envToSecretsMap = new HashMap<>();
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "oidcTokenId");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_PROJECT_NUMBER, "projectNumber");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_POOL_ID, "poolId");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_PROVIDER_ID, "providerId");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_SERVICE_ACCOUNT_EMAIL, "serviceAccountEmail");

    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GCP)
                                            .envToSecretsMap(envToSecretsMap)
                                            .connectorConfig(connectorConfigDTO)
                                            .identifier("testConnector")
                                            .orgIdentifier(orgId)
                                            .projectIdentifier(projectId)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq("testConnector"))).thenReturn(connectorDetails);

    Map<String, ParameterField<String>> envVarMap = new HashMap<>();

    ciStepGroupUtils.getSelfHostedAuthCredentials(accountId, orgId, projectId, settingsMap, envVarMap, false);

    assertThat(envVarMap.get(HARNESS_GCP_OIDC_TOKEN_ID).getValue()).isEqualTo("oidcTokenId");
    assertThat(envVarMap.get(HARNESS_PROJECT_NUMBER).getValue()).isEqualTo("projectNumber");
    assertThat(envVarMap.get(HARNESS_POOL_ID).getValue()).isEqualTo("poolId");
    assertThat(envVarMap.get(HARNESS_PROVIDER_ID).getValue()).isEqualTo("providerId");
    assertThat(envVarMap.get(HARNESS_SERVICE_ACCOUNT_EMAIL).getValue()).isEqualTo("serviceAccountEmail");

    assertThat(envVarMap.get(HARNESS_CACHE_GCP_CONNECTOR).getValue()).isEqualTo("true");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesManualSecretKey() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder()
            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
            .config(
                AzureManualDetailsDTO.builder()
                    .clientId("clientId")
                    .tenantId("tenantId")
                    .authDTO(
                        AzureAuthDTO.builder()
                            .azureSecretType(AzureSecretType.SECRET_KEY)
                            .credentials(
                                AzureClientSecretKeyDTO.builder()
                                    .secretKey(
                                        SecretRefData.builder().identifier("secretKeyRef").scope(Scope.ACCOUNT).build())
                                    .build())
                            .build())
                    .build())
            .build();

    Map<String, ParameterField<String>> result =
        ciStepGroupUtils.getAzureConnectorVariables(buildAzureConnectorDetails(credential));

    assertThat(result.get(HARNESS_AZURE_CLIENT_ID).getValue()).isEqualTo("clientId");
    assertThat(result.get(HARNESS_AZURE_TENANT_ID).getValue()).isEqualTo("tenantId");
    assertThat(result.get(HARNESS_AZURE_CLIENT_SECRET).getValue())
        .isEqualTo("<+secrets.getValue(\"account.secretKeyRef\")>");
    assertThat(result.get(HARNESS_CACHE_AZURE_CONNECTOR).getValue()).isEqualTo("true");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesManualUnsupportedSecretType() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder()
            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
            .config(
                AzureManualDetailsDTO.builder()
                    .clientId("clientId")
                    .tenantId("tenantId")
                    .authDTO(AzureAuthDTO.builder()
                                 .azureSecretType(AzureSecretType.KEY_CERT)
                                 .credentials(
                                     AzureClientKeyCertDTO.builder()
                                         .clientCertRef(
                                             SecretRefData.builder().identifier("certRef").scope(Scope.PROJECT).build())
                                         .build())
                                 .build())
                    .build())
            .build();

    Map<String, ParameterField<String>> result =
        ciStepGroupUtils.getAzureConnectorVariables(buildAzureConnectorDetails(credential));

    assertThat(result.get(HARNESS_AZURE_CLIENT_ID).getValue()).isEqualTo("clientId");
    assertThat(result.get(HARNESS_AZURE_TENANT_ID).getValue()).isEqualTo("tenantId");
    assertThat(result.get(HARNESS_CACHE_AZURE_CONNECTOR).getValue()).isEqualTo("true");
    assertThat(result.containsKey(HARNESS_AZURE_CLIENT_SECRET)).isFalse();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesOidcAuthentication() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder()
            .azureCredentialType(AzureCredentialType.OIDC_AUTHENTICATION)
            .config(AzureOidcSpecDTO.builder().clientId("clientId").tenantId("tenantId").build())
            .build();

    Map<EnvVariableEnum, String> envToSecretsMap = Map.of(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "azureOidcTokenId");

    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AZURE)
                                            .envToSecretsMap(envToSecretsMap)
                                            .connectorConfig(AzureConnectorDTO.builder().credential(credential).build())
                                            .build();

    Map<String, ParameterField<String>> result = ciStepGroupUtils.getAzureConnectorVariables(connectorDetails);

    assertThat(result.get(HARNESS_AZURE_CLIENT_ID).getValue()).isEqualTo("clientId");
    assertThat(result.get(HARNESS_AZURE_TENANT_ID).getValue()).isEqualTo("tenantId");
    assertThat(result.get(HARNESS_AZURE_OIDC_TOKEN_ID).getValue()).isEqualTo("azureOidcTokenId");
    assertThat(result.get(HARNESS_CACHE_AZURE_CONNECTOR).getValue()).isEqualTo("true");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesInheritFromDelegate() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder()
            .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
            .config(
                AzureInheritFromDelegateDetailsDTO.builder()
                    .authDTO(AzureMSIAuthSADTO.builder()
                                 .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                                 .build())
                    .build())
            .build();

    Map<String, ParameterField<String>> result =
        ciStepGroupUtils.getAzureConnectorVariables(buildAzureConnectorDetails(credential));

    assertThat(result.get(HARNESS_CACHE_AZURE_CONNECTOR).getValue()).isEqualTo("true");
    assertThat(result.containsKey(HARNESS_AZURE_CLIENT_ID)).isFalse();
    assertThat(result.containsKey(HARNESS_AZURE_CLIENT_SECRET)).isFalse();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesNullConfigReturnsEmptyMap() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder().azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS).config(null).build();

    Map<String, ParameterField<String>> result =
        ciStepGroupUtils.getAzureConnectorVariables(buildAzureConnectorDetails(credential));

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetAzureConnectorVariablesMapEnablesAzureBuildIntelBackend() {
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder()
            .azureCredentialType(AzureCredentialType.OIDC_AUTHENTICATION)
            .config(AzureOidcSpecDTO.builder().clientId("clientId").tenantId("tenantId").build())
            .build();

    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AZURE)
                                            .envToSecretsMap(Map.of(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "token"))
                                            .connectorConfig(AzureConnectorDTO.builder().credential(credential).build())
                                            .build();

    Map<String, ParameterField<String>> azureEnvVars = ciStepGroupUtils.getAzureConnectorVariables(connectorDetails);
    Map<String, String> settingsMap = new HashMap<>();

    boolean isAzureBackend = on(ciStepGroupUtils).call("isAzureBuildIntelBackend", azureEnvVars, settingsMap).get();

    assertThat(azureEnvVars.get(HARNESS_CACHE_AZURE_CONNECTOR).getValue()).isEqualTo("true");
    assertTrue(isAzureBackend);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureBuildIntelBackendTrueWhenAzureConnectorEnvPresent() {
    Map<String, ParameterField<String>> envVarMap = new HashMap<>();
    envVarMap.put(HARNESS_CACHE_AZURE_CONNECTOR, ParameterField.createValueField("true"));

    Map<String, String> settingsMap = new HashMap<>();

    boolean isAzureBackend = on(ciStepGroupUtils).call("isAzureBuildIntelBackend", envVarMap, settingsMap).get();

    assertTrue(isAzureBackend);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureBuildIntelBackendFalseWhenGcpConnectorEnvPresent() {
    Map<String, ParameterField<String>> envVarMap = new HashMap<>();
    envVarMap.put(HARNESS_CACHE_GCP_CONNECTOR, ParameterField.createValueField("true"));

    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "storageAccount");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "container");

    boolean isAzureBackend = on(ciStepGroupUtils).call("isAzureBuildIntelBackend", envVarMap, settingsMap).get();

    assertFalse(isAzureBackend);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureBuildIntelBackendTrueFromSettingsWhenNoConnectorFlags() {
    Map<String, ParameterField<String>> envVarMap = new HashMap<>();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "storageAccount");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "container");

    boolean isAzureBackend = on(ciStepGroupUtils).call("isAzureBuildIntelBackend", envVarMap, settingsMap).get();

    assertTrue(isAzureBackend);
  }

  private ConnectorDetails buildAzureConnectorDetails(AzureCredentialDTO credential) {
    return ConnectorDetails.builder()
        .connectorType(ConnectorType.AZURE)
        .connectorConfig(AzureConnectorDTO.builder().credential(credential).build())
        .build();
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSetupBuildIntelligenceStepK8() {
    String accountId = "testAccountId";
    Map<String, ParameterField<String>> envVarMap = new HashMap<>();
    IntegrationStageNode mockStageNode = mock(IntegrationStageNode.class);
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    // Populate envVarMap with expected environment variables
    envVarMap.put(HARNESS_ACCOUNT_ID, ParameterField.createValueField(accountId));
    envVarMap.put(HARNESS_CACHE_S3_BUCKET, ParameterField.createValueField("testBucket"));
    envVarMap.put(HARNESS_CACHE_S3_REGION, ParameterField.createValueField("testRegion"));
    envVarMap.put(HARNESS_CACHE_S3_ENDPOINT, ParameterField.createValueField("testEndpoint"));
    envVarMap.put(HARNESS_SELF_HOSTED, ParameterField.createValueField("true"));

    when(ciExecutionConfigService.getCacheProxyImage(accountId)).thenReturn("cache-proxy-image");
    StepImageConfig stepImageConfig = StepImageConfig.builder()
                                          .image("cache-proxy-image")
                                          .entrypoint(Collections.singletonList("/app/cache-proxy"))
                                          .build();
    CIStepConfig ciStepConfig = CIStepConfig.builder().cacheProxyConfig(stepImageConfig).build();

    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);

    Mockito.mockStatic(IntegrationStageUtility.class);
    when(IntegrationStageUtility.getFullyQualifiedImageName(any(), any())).thenReturn("cache-proxy-image");

    ExecutionWrapperConfig result =
        ciStepGroupUtils.getSetupBuildIntelligenceStepK8(accountId, envVarMap, mockStageNode, settingsMap, null, null);
    OnFailureConfig onFailureConfig = OnFailureConfig.builder()
                                          .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                          .action(IgnoreFailureActionConfig.builder().build())
                                          .build();
    FailureStrategyConfig failureStrategyConfig = FailureStrategyConfig.builder().onFailure(onFailureConfig).build();
    ParameterField.createValueField(Collections.singletonList(failureStrategyConfig));
    ciStepGroupUtils.getSetupBuildIntelligenceStepK8(accountId, envVarMap, mockStageNode, settingsMap, null, null);
    result.getStep().get("spec").get("entrypoint");
    assertNotNull(result);
    assertThat(result.getStep()).isNotNull();
    assertThat(result.getStep().get("identifier").asText()).isEqualTo(BUILD_CACHE_STEP_ID);
    assertEquals("cache-proxy-image", result.getStep().get("spec").get("image").asText());
    assertEquals("/app/cache-proxy", result.getStep().get("spec").get("entrypoint").get(0).asText());
    assertEquals("testAccountId", result.getStep().get("spec").get("envVariables").get(HARNESS_ACCOUNT_ID).asText());
    assertEquals("testBucket", result.getStep().get("spec").get("envVariables").get(HARNESS_CACHE_S3_BUCKET).asText());
    assertEquals("testRegion", result.getStep().get("spec").get("envVariables").get(HARNESS_CACHE_S3_REGION).asText());
    assertEquals(
        "testEndpoint", result.getStep().get("spec").get("envVariables").get(HARNESS_CACHE_S3_ENDPOINT).asText());
    assertEquals("true", result.getStep().get("spec").get("envVariables").get(HARNESS_SELF_HOSTED).asText());
    assertEquals(
        "Ignore", result.getStep().get("failureStrategies").get(0).get("onFailure").get("action").get("type").asText());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetSetupBuildIntelligenceStepK8PassesBuildIntelligenceResourcesAndRunAsUser() {
    String accountId = "testAccountId";
    Map<String, ParameterField<String>> envVarMap = new HashMap<>();
    IntegrationStageNode mockStageNode = mock(IntegrationStageNode.class);
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, "testBucket");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_REGION, "testRegion");
    settingsMap.put(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, "testEndpoint");

    envVarMap.put(HARNESS_ACCOUNT_ID, ParameterField.createValueField(accountId));
    envVarMap.put(HARNESS_CACHE_S3_BUCKET, ParameterField.createValueField("testBucket"));
    envVarMap.put(HARNESS_CACHE_S3_REGION, ParameterField.createValueField("testRegion"));
    envVarMap.put(HARNESS_CACHE_S3_ENDPOINT, ParameterField.createValueField("testEndpoint"));
    envVarMap.put(HARNESS_SELF_HOSTED, ParameterField.createValueField("true"));

    ContainerResource resources =
        ContainerResource.builder()
            .limits(ContainerResource.Limits.builder().memory(ParameterField.createValueField("512Mi")).build())
            .build();
    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().runAsUser(ParameterField.createValueField(3000)).resources(resources).build();

    when(ciExecutionConfigService.getCacheProxyImage(accountId)).thenReturn("cache-proxy-image");
    StepImageConfig stepImageConfig = StepImageConfig.builder()
                                          .image("cache-proxy-image")
                                          .entrypoint(Collections.singletonList("/app/cache-proxy"))
                                          .build();
    CIStepConfig ciStepConfig = CIStepConfig.builder().cacheProxyConfig(stepImageConfig).build();
    when(ciExecutionServiceConfig.getStepConfig()).thenReturn(ciStepConfig);

    Mockito.mockStatic(IntegrationStageUtility.class);
    when(IntegrationStageUtility.getFullyQualifiedImageName(any(), any())).thenReturn("cache-proxy-image");

    ExecutionWrapperConfig result = ciStepGroupUtils.getSetupBuildIntelligenceStepK8(
        accountId, envVarMap, mockStageNode, settingsMap, null, buildIntelligence);

    assertNotNull(result);
    JsonNode spec = result.getStep().get("spec");
    assertThat(spec.has("resources")).isTrue();
    assertThat(resolveRunAsUserFromSpec(spec)).isEqualTo(3000);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSetupBuildIntelligenceStepVM() {
    String accountId = "testAccountId";
    String projectId = "testProjectId";
    String orgId = "testOrgId";

    when(ciExecutionConfigService.getCacheProxyImage(accountId)).thenReturn("cache-proxy-image");
    when(harnessImageUtils.getDefaultInternalConnector(any())).thenReturn(ConnectorDetails.builder().build());

    Mockito.mockStatic(IntegrationStageUtility.class);
    when(IntegrationStageUtility.getFullyQualifiedImageName(any(), any())).thenReturn("cache-proxy-image");

    OnFailureConfig onFailureConfig = OnFailureConfig.builder()
                                          .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                          .action(IgnoreFailureActionConfig.builder().build())
                                          .build();
    FailureStrategyConfig failureStrategyConfig = FailureStrategyConfig.builder().onFailure(onFailureConfig).build();
    ParameterField.createValueField(Collections.singletonList(failureStrategyConfig));
    ExecutionWrapperConfig result = ciStepGroupUtils.getSetupBuildIntelligenceStepForHosted(
        null, IntegrationStageNode.builder().build(), new HashMap<>());
    assertNotNull(result);
    assertThat(result.getStep()).isNotNull();
    assertThat(result.getStep().get("identifier").asText()).isEqualTo(BUILD_CACHE_STEP_ID);
    assertThat(result.getStep().get("spec").get("image").asText()).isEqualTo("harness/harness-cache-server:1.7.8");
    assertThat(result.getStep().get("spec").get("entrypoint").get(0).asText()).isEqualTo("/app/cache-proxy");
    assertThat(result.getStep().get("spec").get("portBindings").get("8082").asText()).isEqualTo("8082");

    assertEquals(
        "Ignore", result.getStep().get("failureStrategies").get(0).get("onFailure").get("action").get("type").asText());
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testGetSetupBuildIntelligenceStepVMWithConditionalexecution() {
    when(harnessImageUtils.getDefaultInternalConnector(any())).thenReturn(ConnectorDetails.builder().build());

    Mockito.mockStatic(IntegrationStageUtility.class);
    when(IntegrationStageUtility.getFullyQualifiedImageName(any(), any())).thenReturn("cache-proxy-image");
    ParameterField<String> expressionValue =
        ParameterField.<String>builder().value("<+pipeline.variables.abcd>").build();

    String command = "docker run --network drone --network-alias harnesscache -p 8082:8082 --name harnesscache -e "
        + "HARNESS_ACCOUNT_ID=$HARNESS_ACCOUNT_ID -e "
        + "HARNESS_CACHE_SERVER_BEARER_TOKEN=$HARNESS_CACHE_SERVER_BEARER_TOKEN -e "
        + "HARNESS_CACHE_SERVER_URL=$HARNESS_CACHE_SERVER_URL cache-proxy-image";
    ExecutionWrapperConfig result = ciStepGroupUtils.getSetupBuildIntelligenceStepForHosted(
        expressionValue, IntegrationStageNode.builder().build(), new HashMap<>());
    assertNotNull(result);
    assertThat(result.getStep()).isNotNull();
    assertThat(result.getStep().get("identifier").asText()).isEqualTo(BUILD_CACHE_STEP_ID);
    assertThat(result.getStep().get("when").get("condition").asText()).isEqualTo("<+pipeline.variables.abcd>");
    assertThat(result.getStep().get("when").get("stageStatus").asText()).isEqualTo("Success");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheOverrideValueWithNullOverride() {
    Caching caching = Caching.builder().build();
    Map<String, String> settingsMap = new HashMap<>();

    boolean result = ciStepGroupUtils.getCacheOverrideValue(caching, accountID, settingsMap);

    assertThat(result).isTrue(); // Default value should be true
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheOverrideValueWithSettingsMapValue() {
    Caching caching = Caching.builder().build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_INTEL_ALWAYS_OVERRIDE, "false");

    boolean result = ciStepGroupUtils.getCacheOverrideValue(caching, accountID, settingsMap);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheOverrideValueWithExplicitOverrideTrue() {
    Caching caching = Caching.builder().override(ParameterField.createValueField(true)).build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_INTEL_ALWAYS_OVERRIDE, "false"); // Should be ignored

    boolean result = ciStepGroupUtils.getCacheOverrideValue(caching, accountID, settingsMap);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCacheOverrideValueWithExplicitOverrideFalse() {
    Caching caching = Caching.builder().override(ParameterField.createValueField(false)).build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_INTEL_ALWAYS_OVERRIDE, "false"); // Should be ignored

    boolean result = ciStepGroupUtils.getCacheOverrideValue(caching, accountID, settingsMap);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectCacheServicePort_WithExistingVariable() {
    // Given
    NGVariable httpsBindVar = StringNGVariable.builder()
                                  .name("CACHE_SERVICE_HTTPS_BIND")
                                  .value(ParameterField.createValueField("8081"))
                                  .type(NGVariableType.STRING)
                                  .build();
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().variables(new ArrayList<>(Collections.singletonList(httpsBindVar))).build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_CACHE_SERVER_PORT, "8085");

    // When
    String result = ciStepGroupUtils.resolveAndInjectCacheServicePort(stageNode, settingsMap);

    // Then
    assertThat(result).isEqualTo("8081");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectCacheServicePort_WithFallbackPortInt() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_CACHE_SERVER_PORT, "8082");

    // When
    String result = ciStepGroupUtils.resolveAndInjectCacheServicePort(stageNode, settingsMap);

    // Then
    assertThat(result).isEqualTo("8082");
    assertThat(stageNode.getVariables()).hasSize(1);
    NGVariable addedVar = stageNode.getVariables().get(0);
    assertThat(addedVar.getName()).isEqualTo("CACHE_SERVICE_HTTPS_BIND");
    assertThat(addedVar.fetchValue().toString()).contains("value=8082");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectCacheServicePort_WithFallbackPortDecimal() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_CACHE_SERVER_PORT, "8082.0");

    // When
    String result = ciStepGroupUtils.resolveAndInjectCacheServicePort(stageNode, settingsMap);

    // Then
    assertThat(result).isEqualTo("8082");
    assertThat(stageNode.getVariables()).hasSize(1);
    NGVariable addedVar = stageNode.getVariables().get(0);
    assertThat(addedVar.getName()).isEqualTo("CACHE_SERVICE_HTTPS_BIND");
    assertThat(addedVar.fetchValue().toString()).contains("value=8082");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectCacheServicePort_NoPortFound() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();

    // When
    String result = ciStepGroupUtils.resolveAndInjectCacheServicePort(stageNode, settingsMap);

    // Then
    assertThat(result).isNull();
    assertThat(stageNode.getVariables()).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectCacheServicePort_EmptyValue() {
    // Given
    NGVariable httpsBindVar = StringNGVariable.builder()
                                  .name("CACHE_SERVICE_HTTPS_BIND")
                                  .value(ParameterField.createValueField(""))
                                  .type(NGVariableType.STRING)
                                  .build();
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().variables(new ArrayList<>(Collections.singletonList(httpsBindVar))).build();
    Map<String, String> settingsMap = new HashMap<>();

    // When
    String result = ciStepGroupUtils.resolveAndInjectCacheServicePort(stageNode, settingsMap);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectMavenUrl_WithExistingVariable() {
    // Given
    NGVariable mavenUrlVar = StringNGVariable.builder()
                                 .name("MAVEN_URL")
                                 .value(ParameterField.createValueField("https://example.com/maven"))
                                 .type(NGVariableType.STRING)
                                 .build();
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().variables(new ArrayList<>(Collections.singletonList(mavenUrlVar))).build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_MAVEN_REPO_URL, "https://fallback.com/maven");

    // When
    ciStepGroupUtils.resolveAndInjectMavenUrl(stageNode, settingsMap);

    // Then
    assertThat(stageNode.getVariables()).hasSize(1);
    assertThat(stageNode.getVariables().get(0).fetchValue().fetchFinalValue().toString())
        .isEqualTo("https://example.com/maven");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectMavenUrl_WithFallbackUrl() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_MAVEN_REPO_URL, "https://fallback.com/maven");

    // When
    ciStepGroupUtils.resolveAndInjectMavenUrl(stageNode, settingsMap);

    // Then
    assertThat(stageNode.getVariables()).hasSize(1);
    NGVariable addedVar = stageNode.getVariables().get(0);
    assertThat(addedVar.getName()).isEqualTo("MAVEN_URL");
    assertThat(addedVar.fetchValue().fetchFinalValue().toString()).isEqualTo("https://fallback.com/maven");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectMavenUrl_NoValueInSettingsMap() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();

    // When
    ciStepGroupUtils.resolveAndInjectMavenUrl(stageNode, settingsMap);

    // Then
    assertThat(stageNode.getVariables()).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectMavenUrl_EmptyValueInSettingsMap() {
    // Given
    IntegrationStageNode stageNode = IntegrationStageNode.builder().build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_MAVEN_REPO_URL, "");

    // When
    ciStepGroupUtils.resolveAndInjectMavenUrl(stageNode, settingsMap);

    // Then
    assertThat(stageNode.getVariables()).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testResolveAndInjectMavenUrl_ExistingVariableWithEmptyValue() {
    // Given
    NGVariable mavenUrlVar = StringNGVariable.builder()
                                 .name("MAVEN_URL")
                                 .value(ParameterField.createValueField(""))
                                 .type(NGVariableType.STRING)
                                 .build();
    IntegrationStageNode stageNode =
        IntegrationStageNode.builder().variables(new ArrayList<>(Collections.singletonList(mavenUrlVar))).build();
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_BUILD_INTEL_MAVEN_REPO_URL, "https://fallback.com/maven");

    // When
    ciStepGroupUtils.resolveAndInjectMavenUrl(stageNode, settingsMap);

    // Then
    // It should not override the existing empty value
    assertThat(stageNode.getVariables()).hasSize(1);
    assertThat(stageNode.getVariables().get(0).fetchValue().fetchFinalValue().toString()).isEmpty();
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testNoWrapperSteps() {
    Level pipelineLevel = createLevel("pipeline", "PIPELINE", null);
    Level stageLevel = createLevel("stage", "STAGE", null);
    List<Level> levels = Arrays.asList(pipelineLevel, stageLevel);
    String stepIdentifier = "run_unit_tests";

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, stepIdentifier);

    assertEquals("run_unit_tests", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testStepGroupLevel() {
    Level stageLevel = createLevel("deploy_stage", "STAGE", null);
    Level stepGroupLevel = createLevel("integration_tests", "STEP_GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, stepGroupLevel);
    String stepIdentifier = "run_selenium";

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, stepIdentifier);

    assertEquals("integration_tests_run_selenium", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testInsertStepLevel() {
    Level templateLevel = createLevel("template", "TEMPLATE", null);
    Level insertLevel = createLevel("inject_vars", "INSERT", SubCategory.STEP_LEVEL);
    List<Level> levels = Arrays.asList(templateLevel, insertLevel);
    String stepIdentifier = "validate_inputs";

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, stepIdentifier);

    assertEquals("inject_vars_validate_inputs", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testInsertStageLevel() {
    Level templateLevel = createLevel("template", "TEMPLATE", null);
    Level insertLevel = createLevel("inject_pipeline", "INSERT", SubCategory.STAGE_LEVEL);
    List<Level> levels = Arrays.asList(templateLevel, insertLevel);
    String stepIdentifier = "deploy_app";

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, stepIdentifier);

    assertEquals("deploy_app", result);
  }
  private Level createLevel(String identifier, String stepType, SubCategory subCategory) {
    StepType.Builder stepTypeBuilder = StepType.newBuilder().setType(stepType);

    if (subCategory != null) {
      stepTypeBuilder.setSubCategory(subCategory);
    }

    return Level.newBuilder().setIdentifier(identifier).setStepType(stepTypeBuilder.build()).build();
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testDepthPopulationWithIntegerValue() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(25))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(null).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");
    assertEquals("25", settingsNode.get("depth").asText());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testDepthPopulationWithExpressionValue() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createExpressionField(true, "<+pipeline.sequenceId>", null, false))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(null).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();
    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");
    assertEquals("<+pipeline.sequenceId>", settingsNode.get("depth").asText());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testDepthPopulationWithManualExecutionBranch() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(null)) // No depth specified
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();
    CIExecutionArgs ciExecutionArgs =
        CIExecutionArgs.builder().executionSource(manualExecutionSource).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    when(executionSource.getType()).thenReturn(ExecutionSource.Type.MANUAL);
    when(manualExecutionSource.getType()).thenReturn(ExecutionSource.Type.MANUAL);
    when(manualExecutionSource.getBranch()).thenReturn("main");
    when(manualExecutionSource.getTag()).thenReturn(null);

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");

    assertEquals(String.valueOf(GIT_CLONE_MANUAL_DEPTH), settingsNode.get("depth").asText());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testPrCloneStrategyPopulationWithValue() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(25))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.SOURCE_BRANCH))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(null).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");
    System.out.println(settingsNode);
    assertEquals("SourceBranch", settingsNode.get("PR_CLONE_STRATEGY").asText());
  }

  @Test
  @Owner(developers = VIVEK_KUMAR)
  @Category(UnitTests.class)
  public void testMergeQueueForcesSourceBranchCloneStrategy() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(50))
                            .prCloneStrategy(ParameterField.createValueField(PRCloneStrategy.MERGE_COMMIT))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    WebhookExecutionSource mergeQueueSource =
        WebhookExecutionSource.builder()
            .webhookEvent(
                BranchWebhookEvent.builder()
                    .branchName("main")
                    .baseAttributes(
                        WebhookBaseAttributes.builder().after("speculativeMergeSha").action("checks_requested").build())
                    .build())
            .build();
    CIExecutionArgs ciExecutionArgs =
        CIExecutionArgs.builder().executionSource(mergeQueueSource).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    JsonNode settingsNode = result.getStep().get("spec").get("settings");
    // MergeCommit would fetch the target branch and merge the sha on top, which yields a different commit from
    // the one the queue asked us to validate as soon as the target branch has moved. The configured value is
    // overridden rather than honoured because the queue's commit is already a merge.
    assertEquals("SourceBranch", settingsNode.get("PR_CLONE_STRATEGY").asText());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testPrCloneStrategyPopulationWithExpression() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(25))
                            .prCloneStrategy(ParameterField.createExpressionField(
                                true, "<+<+pipeline.sequenceId> <= 162 ? 'SourceBranch':'MergeCommit'>", null, false))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(null).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");
    System.out.println(settingsNode);
    assertEquals("<+<+pipeline.sequenceId> <= 162 ? 'SourceBranch':'MergeCommit'>",
        settingsNode.get("PR_CLONE_STRATEGY").asText());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testPrCloneStrategyPopulationNotSetWhenNull() {
    CodeBase codebase = CodeBase.builder()
                            .depth(ParameterField.createValueField(25))
                            .prCloneStrategy(ParameterField.createValueField(null))
                            .sslVerify(ParameterField.createValueField(true))
                            .build();

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(null).runSequence("testOrgId").build();

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ExecutionWrapperConfig result = ciStepGroupUtils.getGitCloneStep(ciExecutionArgs, codebase, null, infrastructure);

    assertNotNull(result);
    JsonNode stepNode = result.getStep();

    JsonNode settingsNode = stepNode.get("spec").get("settings");
    System.out.println(settingsNode);
    assertFalse(settingsNode.has("PR_CLONE_STRATEGY_ATTRIBUTE"));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testAddEnvVariablesToBuildAndPushSteps() throws Exception {
    // Load BEFORE
    List<ExecutionWrapperConfig> wrappers;
    try (InputStream is = getClass().getResourceAsStream("/addDlcEnvVarsBefore.json")) {
      wrappers = mapper.readValue(is, new TypeReference<List<ExecutionWrapperConfig>>() {});
    }

    // Prepare selfHostedAuthDetails
    Map<String, ParameterField<String>> selfHostedAuthDetails = new HashMap<>();
    selfHostedAuthDetails.put(
        "HARNESS_CACHE_S3_SECRET_KEY", ParameterField.createValueField("<+secrets.getValue(\"AWS_SECRET_KEY\")>"));
    selfHostedAuthDetails.put(
        "HARNESS_CACHE_S3_ACCESS_KEY", ParameterField.createValueField("<+secrets.getValue(\"AWS_Access_KEY\")>"));

    // Call method under test
    ciStepGroupUtils.addEnvVariablesToBuildAndPushSteps(wrappers, selfHostedAuthDetails, true);

    // Load AFTER
    List<ExecutionWrapperConfig> expectedWrappers;
    try (InputStream is = getClass().getResourceAsStream("/addDlcEnvVarsAfter.json")) {
      expectedWrappers = mapper.readValue(is, new TypeReference<List<ExecutionWrapperConfig>>() {});
    }

    // Compare deep equality
    assertThat(wrappers).usingRecursiveComparison().isEqualTo(expectedWrappers);
  }

  private static int resolveRunAsUserFromSpec(JsonNode spec) {
    JsonNode n = spec.get("runAsUser");
    assertNotNull(n);
    if (n.isObject() && n.has("value") && !n.get("value").isNull()) {
      return n.get("value").asInt();
    }
    return n.asInt();
  }

  private Level createLevelWithStrategy(
      String identifier, String originalIdentifier, String stepType, SubCategory subCategory) {
    StepType.Builder stepTypeBuilder = StepType.newBuilder().setType(stepType);

    if (subCategory != null) {
      stepTypeBuilder.setSubCategory(subCategory);
    }

    return Level.newBuilder()
        .setIdentifier(identifier)
        .setOriginalIdentifier(originalIdentifier)
        .setStepType(stepTypeBuilder.build())
        .build();
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testV1StepGroupWithStrategyUsesOriginalIdentifier() {
    // V1 outer step group "step_g" (type GROUP) with strategy iterations. PmsLevelUtils#buildLevelFromNode
    // sets Level.identifier to the post-postfix value ("step_g_0") and originalIdentifier to
    // the planNode identifier. Init builds the port-map key from the YAML id ("step_g"), so
    // the V1 lookup must resolve to the clean originalIdentifier.
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level outerStepGroup = createLevelWithStrategy("step_g_0", "step_g", "GROUP", null);
    Level innerStepGroup = createLevel("sg1", "GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, outerStepGroup, innerStepGroup);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "test3");

    assertEquals("step_g_sg1_test3", result);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testV1StepGroupWithStrategyDirectChildStep() {
    // V1 direct child of strategy-stepGroup must resolve to the pre-strategy YAML key.
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level outerStepGroup = createLevelWithStrategy("step_g_1", "step_g", "GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, outerStepGroup);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "test2");

    assertEquals("step_g_test2", result);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testV1StepGroupWithUnresolvedTemplateLeakStripped() {
    // V1 plan creators bake the literal "<+strategy.identifierPostFix>" into the plan-node
    // identifier (StrategyUtilsV1#getIdentifierWithExpression), so originalIdentifier carries
    // the placeholder. Strip it to recover the clean YAML id used by init's port map.
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level outerStepGroup =
        createLevelWithStrategy("stepgrp_python1", "stepgrp<+strategy.identifierPostFix>", "GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, outerStepGroup);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "Run_1");

    assertEquals("stepgrp_Run_1", result);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testV1NestedStepGroupWithStrategyTemplateLeakStripped() {
    // Outer V1 strategy step group + inner non-strategy V1 step group. Outer originalIdentifier
    // is the placeholder-bearing YAML id; the inner step group identifier is plain.
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level outerStepGroup =
        createLevelWithStrategy("nestedStepGrp_python1", "nestedStepGrp<+strategy.identifierPostFix>", "GROUP", null);
    Level innerStepGroup = createLevel("insideStepGrp", "GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, outerStepGroup, innerStepGroup);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "Run_1");

    assertEquals("nestedStepGrp_insideStepGrp_Run_1", result);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testStepLevelStrategyDoesNotAffectLookupKey() {
    // Step-level strategy on a leaf step. The step Level itself is not a step-group, so the
    // lookup key is just the passed-in stepIdentifier (which the caller has already resolved
    // via AmbianceUtils.obtainStepIdentifier to match the init port-map key).
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level stepLevel = createLevelWithStrategy("Run_2_python1", "Run_2<+strategy.identifierPostFix>", "Run", null);
    List<Level> levels = Arrays.asList(stageLevel, stepLevel);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "Run_2_python1");

    assertEquals("Run_2_python1", result);
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testV0StepGroupKeepsResolvedIdentifier() {
    // V0 step-groups (type STEP_GROUP) must keep the resolved Level.identifier so existing
    // pipelines and downstream expression keys remain unchanged after the V1 lookup fix.
    Level stageLevel = createLevel("CI", "STAGE", null);
    Level outerStepGroup = createLevelWithStrategy("step_g_0", "step_g", "STEP_GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, outerStepGroup);

    String result = ciStepGroupUtils.getUniqueStepIdentifier(levels, "run_unit_tests");

    assertEquals("step_g_0_run_unit_tests", result);
  }
}
