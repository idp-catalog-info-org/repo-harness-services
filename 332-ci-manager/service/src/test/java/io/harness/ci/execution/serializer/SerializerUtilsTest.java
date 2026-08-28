/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_ARCHIVE_TYPE_TAR;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_AZURE_BACKEND;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_GCS_BACKEND;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_S3_BACKEND;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ACCESS_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ASSUME_ROLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_SECRET_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.TENANT_ID;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.AZURE_CONTAINER_NAME;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_ACCOUNT_NAME;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_ARCHIVE_FORMAT;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_BACKEND;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_BUCKET;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_ENDPOINT;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_REGION;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DLITE_VM;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.VM;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_OIDC_TOKEN_ID;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_POOL_ID;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_PROJECT_NUMBER;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_PROVIDER_ID;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_SERVICE_ACCOUNT_EMAIL;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEVANSH;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DIKSHANT;
import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TAPAN;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureOidcSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureCredentialType;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.oidc.idtoken.OidcIdTokenCustomAttributesStructure;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.AzureOidcAuthenticator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CI)
@RunWith(MockitoJUnitRunner.class)
public class SerializerUtilsTest {
  @InjectMocks private SerializerUtils serializerUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private SecretSpecBuilder secretSpecBuilder;
  @Mock private SecretUtils secretUtils;
  @Mock CIFeatureFlagService featureFlagService;
  @Mock AzureOidcAuthenticator azureOidcAuthenticator;

  @Before
  public void setup() {}

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSetSelfHostedCacheEnvironments() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("AWS_OIDC")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("testAwsEndpoint")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, accountId, orgId, projectId);
    doReturn("testBucket")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("testRegion")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_REGION, accountId, orgId, projectId);
    doReturn("testAccessKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, accountId, orgId, projectId);
    doReturn("testSecretKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, accountId, orgId, projectId);

    // Act
    serializerUtilsSpy.setSelfHostedCacheEnvironments(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_S3_BACKEND)
        .containsEntry(PLUGIN_ARCHIVE_FORMAT, CACHE_ARCHIVE_TYPE_TAR)
        .containsEntry(PLUGIN_REGION, "testRegion")
        .containsEntry(PLUGIN_BUCKET, "testBucket")
        .containsEntry(PLUGIN_ENDPOINT, "testAwsEndpoint");
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesAWSOIDCConnector() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("AWS_OIDC")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("testAwsEndpoint")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, accountId, orgId, projectId);
    doReturn("testBucket")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("testRegion")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_REGION, accountId, orgId, projectId);
    doReturn("testAccessKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, accountId, orgId, projectId);
    doReturn("testSecretKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, accountId, orgId, projectId);

    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    Map<EnvVariableEnum, String> envToSecretsMap = new HashMap<>();
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "oidcTokenIdValue");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_ASSUME_ROLE, "assumeRoleValue");

    when(connectorDetails.getConnectorType()).thenReturn(ConnectorType.AWS);
    when(connectorDetails.getEnvToSecretsMap()).thenReturn(envToSecretsMap);
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);

    // Mock the decryptConnectorSecret to ensure it gets called but does nothing
    when(secretSpecBuilder.decryptConnectorSecret(any())).thenReturn(new HashMap<>());

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_S3_BACKEND)
        .containsEntry(PLUGIN_REGION, "testRegion")
        .containsEntry(PLUGIN_BUCKET, "testBucket")
        .containsEntry(PLUGIN_ENDPOINT, "testAwsEndpoint")
        .containsEntry(PLUGIN_OIDC_TOKEN_ID, "oidcTokenIdValue")
        .containsEntry(PLUGIN_ASSUME_ROLE, "assumeRoleValue");

    // Ensure connector details and secret decryption are called
    verify(connectorUtils, times(1)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(1)).decryptConnectorSecret(any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesGCPOIDCConnector() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("AWS_OIDC")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("testGcpEndpoint")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, accountId, orgId, projectId);
    doReturn("testBucket")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("testRegion")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_REGION, accountId, orgId, projectId);
    doReturn("testAccessKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, accountId, orgId, projectId);
    doReturn("testSecretKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, accountId, orgId, projectId);

    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    Map<EnvVariableEnum, String> envToSecretsMap = new HashMap<>();
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "oidcTokenIdValue");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_POOL_ID, "oidcPoolId");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_PROJECT_NUMBER, "gcpProjectNumber");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_PROVIDER_ID, "oidcProviderId");
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_SERVICE_ACCOUNT_EMAIL, "gcpServiceAccountEmail");

    when(connectorDetails.getConnectorType()).thenReturn(ConnectorType.GCP);
    when(connectorDetails.getEnvToSecretsMap()).thenReturn(envToSecretsMap);
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);

    // Mock the decryptConnectorSecret to ensure it gets called but does nothing
    when(secretSpecBuilder.decryptConnectorSecret(any())).thenReturn(new HashMap<>());

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_GCS_BACKEND)
        .containsEntry(PLUGIN_BUCKET, "testBucket")
        .containsEntry(PLUGIN_ENDPOINT, "testGcpEndpoint")
        .containsEntry(PLUGIN_OIDC_TOKEN_ID, "oidcTokenIdValue")
        .containsEntry(PLUGIN_POOL_ID, "oidcPoolId")
        .containsEntry(PLUGIN_PROJECT_NUMBER, "gcpProjectNumber")
        .containsEntry(PLUGIN_PROVIDER_ID, "oidcProviderId")
        .containsEntry(PLUGIN_SERVICE_ACCOUNT_EMAIL, "gcpServiceAccountEmail");

    // Ensure connector details and secret decryption are called
    verify(connectorUtils, times(1)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(1)).decryptConnectorSecret(any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesEmptyConnector() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("testAwsEndpoint")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, accountId, orgId, projectId);
    doReturn("testBucket")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("testRegion")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_REGION, accountId, orgId, projectId);
    doReturn("testAccessKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, accountId, orgId, projectId);
    doReturn("testSecretKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, accountId, orgId, projectId);

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_S3_BACKEND)
        .containsEntry(PLUGIN_REGION, "testRegion")
        .containsEntry(PLUGIN_BUCKET, "testBucket")
        .containsEntry(PLUGIN_ENDPOINT, "testAwsEndpoint");

    // Ensure connector details and secret decryption are called
    verify(connectorUtils, times(0)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(0)).decryptConnectorSecret(any());
    verify(secretUtils, times(0)).getSecretValue(any(), any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesEmptyConnectorNullAK() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    DecryptedSecretValue decryptedAccessKey = DecryptedSecretValue.builder()
                                                  .identifier("decryptedAccessKey")
                                                  .accountIdentifier("testAccount")
                                                  .orgIdentifier("testOrg")
                                                  .projectIdentifier("testProject")
                                                  .decryptedValue("decryptedAccessKey")
                                                  .lastModifiedAt(System.currentTimeMillis())
                                                  .createdAt(System.currentTimeMillis())
                                                  .build();
    DecryptedSecretValue decryptedSecretKey = DecryptedSecretValue.builder()
                                                  .identifier("decryptedSecretKey")
                                                  .accountIdentifier("testAccount")
                                                  .orgIdentifier("testOrg")
                                                  .projectIdentifier("testProject")
                                                  .decryptedValue("decryptedSecretKey")
                                                  .lastModifiedAt(System.currentTimeMillis())
                                                  .createdAt(System.currentTimeMillis())
                                                  .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("testAwsEndpoint")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ENDPOINT_URL, accountId, orgId, projectId);
    doReturn("testBucket")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("testRegion")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_REGION, accountId, orgId, projectId);
    doReturn(null)
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_ACCESS_KEY, accountId, orgId, projectId);
    doReturn("testSecretKey")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_SECRET_KEY, accountId, orgId, projectId);

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_S3_BACKEND)
        .containsEntry(PLUGIN_REGION, "testRegion")
        .containsEntry(PLUGIN_BUCKET, "testBucket")
        .containsEntry(PLUGIN_ENDPOINT, "testAwsEndpoint")
        .doesNotContainKey(PLUGIN_ACCESS_KEY)
        .doesNotContainKey(PLUGIN_SECRET_KEY);

    // Ensure connector details and secret decryption are called
    verify(connectorUtils, times(0)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(0)).decryptConnectorSecret(any());
    verify(secretUtils, times(0)).getSecretValue(any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testCheckAndGetFullyQualifiedName() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("https://dockerhub.io").build())
            .connectorType(ConnectorType.DOCKER)
            .executeOnDelegate(false)
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    when(connectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any())).thenReturn(connectorDetailsFoMock);
    String fqn = serializerUtils.checkAndGetFullyQualifiedName(null, "image", ambiance, "registry", true, false);
    assertThat(fqn).isEqualTo("dockerhub.io/testaccount/registry/image");
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testGetDebugCommandTimeoutAndPath() {
    // Setup
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    StageInfraDetails stageinfadetails = VmStageInfraDetails.builder().infraInfo(DLITE_VM).build();
    String accountId = "testAccount";
    ParameterField<CIShellType> shellType = ParameterField.createValueField(CIShellType.BASH);
    String tmatePath = "";
    String tmateEndpoint = "test.endpoint";

    // Test with positive timeout
    String resultWithTimeout = SerializerUtils.getVmDebugCommand(
        infrastructure, accountId, 60, shellType, stageinfadetails, tmatePath, tmateEndpoint);
    assertTrue("Debug command should contain timeout command", resultWithTimeout.contains("timeout"));
    assertTrue("Debug command should contain tmate path witout tmp", resultWithTimeout.contains("/addon/tmate"));

    infrastructure = HostedVmInfraYaml.builder()
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(
                                       Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                                   .build())
                         .build();

    // Test with zero timeout
    String resultWithoutTimeout = SerializerUtils.getVmDebugCommand(
        infrastructure, accountId, 0, shellType, stageinfadetails, tmatePath, tmateEndpoint);
    assertFalse("Debug command should not contain timeout command", resultWithoutTimeout.contains("timeout"));
    assertTrue("Debug command should contain tmate path with tmp", resultWithoutTimeout.contains("/tmp/addon/tmate"));
  }

  @Test
  @Owner(developers = DIKSHANT)
  @Category(UnitTests.class)
  public void testGetVmDebugCommandMacOsDockerWithTmatePathSkipsTimeout() {
    Infrastructure infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                      .build())
            .build();
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().infraInfo(VM).build();
    String tmatePath = "/Users/test/Documents/tmate-1.0-static-mac-arm64/tmate";

    String result = SerializerUtils.getVmDebugCommand(infrastructure, "testAccount", 3600,
        ParameterField.createValueField(CIShellType.SH), stageInfraDetails, tmatePath, "ssh.qa.harness.io");

    assertFalse("Debug command should not contain timeout command", result.contains("timeout"));
    assertTrue("Debug command should contain tmate path", result.contains(tmatePath));
  }

  @Test
  @Owner(developers = DIKSHANT)
  @Category(UnitTests.class)
  public void testGetVmDebugCommandLinuxDockerWithTmatePathKeepsTimeout() {
    Infrastructure infrastructure =
        DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().infraInfo(VM).build();
    String tmatePath = "/addon/tmate";

    String result = SerializerUtils.getVmDebugCommand(infrastructure, "testAccount", 3600,
        ParameterField.createValueField(CIShellType.SH), stageInfraDetails, tmatePath, "ssh.harness.io");

    assertTrue("Debug command should contain timeout command", result.contains("timeout 3600s"));
    assertTrue("Debug command should contain tmate path", result.contains(tmatePath));
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testCheckAndPopulateEnvironmentVariablesForOIDCPlugins_AZURE() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "acc").build();
    Map<String, String> envVars = new HashMap<>();

    when(azureOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("azure-oidc-token");

    String image = "plugins/azure-oidc:latest";
    OidcIdTokenCustomAttributesStructure customAttrs =
        OidcIdTokenCustomAttributesStructure.builder().accountId("acc").build();

    serializerUtils.checkAndPopulateEnvironmentVariablesForOIDCPlugins(image, ambiance, envVars, customAttrs);

    assertThat(envVars).containsEntry(PLUGIN_OIDC_TOKEN_ID, "azure-oidc-token");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testCheckAndPopulateEnvironmentVariablesForOIDCPlugins_AZURE_WithSubject() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "acc").build();
    Map<String, String> envVars = new HashMap<>();
    envVars.put("PLUGIN_SUBJECT", "account:acc:org:myorg:env:prod:stage:deploy");

    when(azureOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("azure-oidc-token");

    String image = "plugins/azure-oidc:latest";
    OidcIdTokenCustomAttributesStructure customAttrs =
        OidcIdTokenCustomAttributesStructure.builder().accountId("acc").build();

    serializerUtils.checkAndPopulateEnvironmentVariablesForOIDCPlugins(image, ambiance, envVars, customAttrs);

    assertThat(envVars).containsEntry(PLUGIN_OIDC_TOKEN_ID, "azure-oidc-token");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testCheckAndPopulateEnvironmentVariablesForOIDCPlugins_AZURE_NoSubject() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "acc").build();
    Map<String, String> envVars = new HashMap<>();

    when(azureOidcAuthenticator.fetchOidcIdToken(any())).thenReturn("azure-oidc-token");

    String image = "plugins/azure-oidc:latest";
    OidcIdTokenCustomAttributesStructure customAttrs =
        OidcIdTokenCustomAttributesStructure.builder().accountId("acc").build();

    serializerUtils.checkAndPopulateEnvironmentVariablesForOIDCPlugins(image, ambiance, envVars, customAttrs);

    assertThat(envVars).containsEntry(PLUGIN_OIDC_TOKEN_ID, "azure-oidc-token");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testResolveRunAsUser_StepLevelTakesPrecedence() {
    ParameterField<Integer> stepRunAsUser = ParameterField.createValueField(1000);
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().runAsUser(2000).build();

    String result = SerializerUtils.resolveRunAsUser(stepRunAsUser, stageInfraDetails);

    assertThat(result).isEqualTo("1000");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testResolveRunAsUser_FallbackToStageLevel() {
    ParameterField<Integer> stepRunAsUser = null;
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().runAsUser(2000).infraInfo(VM).build();

    String result = SerializerUtils.resolveRunAsUser(stepRunAsUser, stageInfraDetails);

    assertThat(result).isEqualTo("2000");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testResolveRunAsUser_BothNull() {
    ParameterField<Integer> stepRunAsUser = null;
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();

    String result = SerializerUtils.resolveRunAsUser(stepRunAsUser, stageInfraDetails);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetStageRunAsUser_VmInfra() {
    VmStageInfraDetails vmDetails = VmStageInfraDetails.builder().runAsUser(1500).infraInfo(VM).build();

    Integer result = SerializerUtils.getStageRunAsUser(vmDetails);

    assertThat(result).isEqualTo(1500);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetStageRunAsUser_DliteVmInfra() {
    DliteVmStageInfraDetails dliteDetails = DliteVmStageInfraDetails.builder().runAsUser(1800).build();

    Integer result = SerializerUtils.getStageRunAsUser(dliteDetails);

    assertThat(result).isEqualTo(1800);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetStageRunAsUser_NullInfra() {
    Integer result = SerializerUtils.getStageRunAsUser(null);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = DEVANSH)
  @Category(UnitTests.class)
  public void testStripInputSetValidatorSuffix() {
    // Expression with allowedValues suffix
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("<+pipeline.variables.pvar>.allowedValues('val')"))
        .isEqualTo("<+pipeline.variables.pvar>");

    // Already-resolved expression with allowedValues suffix
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("val.allowedValues('val')")).isEqualTo("val");

    // Expression with regex suffix
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("<+pipeline.variables.pvar>.regex(^[a-z]+$)"))
        .isEqualTo("<+pipeline.variables.pvar>");

    // No validator suffix - should return unchanged
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("<+pipeline.variables.pvar>"))
        .isEqualTo("<+pipeline.variables.pvar>");

    // Plain string without validator - should return unchanged
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("hello world")).isEqualTo("hello world");

    // Null input
    assertThat(SerializerUtils.stripInputSetValidatorSuffix(null)).isNull();

    // Empty input
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("")).isEqualTo("");

    // Multiple allowed values
    assertThat(SerializerUtils.stripInputSetValidatorSuffix("<+pipeline.variables.pvar>.allowedValues('val1', 'val2')"))
        .isEqualTo("<+pipeline.variables.pvar>");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesAzureManualConnector() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("AZURE_MANUAL")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("azureStorageAccount")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, accountId, orgId, projectId);
    doReturn("myContainer")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, accountId, orgId, projectId);

    AzureManualDetailsDTO azureManualDetails =
        AzureManualDetailsDTO.builder().clientId("test-client-id").tenantId("test-tenant-id").build();
    AzureCredentialDTO credential = AzureCredentialDTO.builder()
                                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                                        .config(azureManualDetails)
                                        .build();
    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().credential(credential).build();
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder().connectorType(ConnectorType.AZURE).connectorConfig(azureConnectorDTO).build();

    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(secretSpecBuilder.decryptConnectorSecret(any())).thenReturn(new HashMap<>());

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_AZURE_BACKEND)
        .containsEntry(PLUGIN_ACCOUNT_NAME, "azureStorageAccount")
        .containsEntry(AZURE_CONTAINER_NAME, "myContainer")
        .containsEntry(CLIENT_ID, "test-client-id")
        .containsEntry(TENANT_ID, "test-tenant-id");

    verify(connectorUtils, times(1)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(1)).decryptConnectorSecret(any());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesAzureOidcConnector() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("AZURE_OIDC")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("azureStorageAccount")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, accountId, orgId, projectId);
    doReturn("myContainer")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, accountId, orgId, projectId);

    AzureOidcSpecDTO azureOidcSpec =
        AzureOidcSpecDTO.builder().clientId("oidc-client-id").tenantId("oidc-tenant-id").build();
    AzureCredentialDTO credential = AzureCredentialDTO.builder()
                                        .azureCredentialType(AzureCredentialType.OIDC_AUTHENTICATION)
                                        .config(azureOidcSpec)
                                        .build();
    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().credential(credential).build();

    Map<EnvVariableEnum, String> envToSecretsMap = new HashMap<>();
    envToSecretsMap.put(EnvVariableEnum.PLUGIN_OIDC_TOKEN_ID, "oidc-token-id-value");

    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.AZURE)
                                            .connectorConfig(azureConnectorDTO)
                                            .envToSecretsMap(envToSecretsMap)
                                            .build();

    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(secretSpecBuilder.decryptConnectorSecret(any())).thenReturn(new HashMap<>());

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_AZURE_BACKEND)
        .containsEntry(PLUGIN_ACCOUNT_NAME, "azureStorageAccount")
        .containsEntry(AZURE_CONTAINER_NAME, "myContainer")
        .containsEntry(CLIENT_ID, "oidc-client-id")
        .containsEntry(TENANT_ID, "oidc-tenant-id")
        .containsEntry(PLUGIN_OIDC_TOKEN_ID, "oidc-token-id-value");

    verify(connectorUtils, times(1)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(1)).decryptConnectorSecret(any());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testSetIntelligencePluginEnvVariablesNoConnectorAzureSettings() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    SerializerUtils serializerUtilsSpy = spy(serializerUtils);

    doReturn("")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_CONNECTOR, accountId, orgId, projectId);
    doReturn("")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_S3_BUCKET_NAME, accountId, orgId, projectId);
    doReturn("azureStorageAccount")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, accountId, orgId, projectId);
    doReturn("myContainer")
        .when(serializerUtilsSpy)
        .getSettingValue(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, accountId, orgId, projectId);

    // Act
    serializerUtilsSpy.setIntelligencePluginEnvVariables(ambiance, envVarMap);

    // Assert
    assertThat(envVarMap)
        .containsEntry(PLUGIN_BACKEND, CACHE_AZURE_BACKEND)
        .containsEntry(PLUGIN_ACCOUNT_NAME, "azureStorageAccount")
        .containsEntry(AZURE_CONTAINER_NAME, "myContainer");

    verify(connectorUtils, times(0)).getConnectorDetails(any(), any());
    verify(secretSpecBuilder, times(0)).decryptConnectorSecret(any());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureSettingsConfiguredTrue() {
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azureStorageAccount");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "myContainer");

    boolean result = SerializerUtils.isAzureSettingsConfigured(settingsMap);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureSettingsConfiguredFalseMissingContainer() {
    Map<String, String> settingsMap = new HashMap<>();
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_STORAGE_ACCOUNT, "azureStorageAccount");
    settingsMap.put(SettingIdentifiers.CI_CACHE_AZURE_CONTAINER_NAME, "");

    boolean result = SerializerUtils.isAzureSettingsConfigured(settingsMap);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testIsAzureSettingsConfiguredFalseEmpty() {
    Map<String, String> settingsMap = new HashMap<>();

    boolean result = SerializerUtils.isAzureSettingsConfigured(settingsMap);

    assertThat(result).isFalse();
  }
}
