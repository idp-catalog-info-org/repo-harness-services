/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_AZURE_BACKEND;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.TENANT_ID;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.AZURE_CONTAINER_NAME;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_ACCOUNT_NAME;
import static io.harness.ci.execution.buildstate.constants.PluginSettingUtilsConstants.PLUGIN_BACKEND;
import static io.harness.iacm.execution.PluginSettingUtils.DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SATYAKOTA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.OSType;
import io.harness.beans.FeatureName;
import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.category.element.UnitTests;
import io.harness.ci.cache.GcsDlcCacheManager;
import io.harness.ci.cache.S3DlcCacheManager;
import io.harness.ci.config.CIDockerLayerCachingConfig;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIDockerLayerCachingConfigServiceTest {
  String accountId;
  @Mock private GcsDlcCacheManager gcsDlcCacheManager;
  @Mock private S3DlcCacheManager s3DlcCacheManager;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks private CIDockerLayerCachingConfigService ciDockerLayerCachingConfigService;
  @Mock private SerializerUtils serializerUtils;

  @Before
  public void setup() {
    accountId = "test-account-id";
    MockitoAnnotations.initMocks(this);
  }

  private CIDockerLayerCachingConfig getConfig() {
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
  public void testGetDockerLayerCachingConfig() {
    CIDockerLayerCachingConfig expectedConfig = getConfig();

    when(s3DlcCacheManager.getCacheConfig(accountId)).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config =
        ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId, OSType.LINUX.toString());
    assertThat(expectedConfig).isEqualTo(config);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCachingGCSConfig() {
    CIDockerLayerCachingConfig expectedConfig = getConfig();

    when(featureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_DLC, accountId)).thenReturn(true);
    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(expectedConfig);
    CIDockerLayerCachingConfig config =
        ciDockerLayerCachingConfigService.getDockerLayerCachingConfig(accountId, "MacOS");
    assertThat(expectedConfig).isEqualTo(config);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testPurgeDockerLayerCache() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(gcsDlcCacheManager.deleteCache(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDockerLayerCacheMetadata() {
    List<CacheMetadataDetail> expectedList = new ArrayList<>();
    expectedList.add(CacheMetadataDetail.builder().cachePath("cachePath").size(3).build());

    when(gcsDlcCacheManager.getCacheConfig(accountId)).thenReturn(getConfig());
    when(gcsDlcCacheManager.getCacheMetadata(accountId)).thenReturn(expectedList);
    List<CacheMetadataDetail> detailList = ciDockerLayerCachingConfigService.getDockerLayerCacheMetadata(accountId);
    assertThat(detailList).isEqualTo(expectedList);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheFrom() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheFrom = "type=s3,endpoint_url=endpoint,bucket=bucket,region=region,access_key_id=access_key,"
        + "secret_access_key=secret_key";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArg(config, "");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheFromWithPrefix() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheFrom = "type=s3,endpoint_url=endpoint,bucket=bucket,region=region,access_key_id=access_key,"
        + "secret_access_key=secret_key,prefix=test-account-id/test-prefix/";
    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArg(config, accountId + "/test-prefix/");
    assertThat(expectedCacheFrom).isEqualTo(cacheFrom);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheTo() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheTo = "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_"
        + "key,secret_access_key=secret_key,ignore-error=true";
    String cacheTo = ciDockerLayerCachingConfigService.getCacheToArg(config, "");
    assertThat(expectedCacheTo).isEqualTo(cacheTo);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetCacheToPrefix() {
    CIDockerLayerCachingConfig config = getConfig();
    String expectedCacheTo = "type=s3,endpoint_url=endpoint,bucket=bucket,mode=max,region=region,access_key_id=access_"
        + "key,secret_access_key=secret_key,ignore-error=true,prefix=test-account-id/test-prefix/";
    String cacheTo = ciDockerLayerCachingConfigService.getCacheToArg(config, accountId + "/test-prefix/");
    assertThat(expectedCacheTo).isEqualTo(cacheTo);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigValidValues() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking environment variables
    envVarMap.put("PLUGIN_ACCESS_KEY", "access-key");
    envVarMap.put("PLUGIN_SECRET_KEY", "secret-key");
    envVarMap.put("PLUGIN_ENDPOINT", "endpoint");
    envVarMap.put("PLUGIN_BACKEND", "s3");
    envVarMap.put("PLUGIN_BUCKET", "bucket");
    envVarMap.put("PLUGIN_REGION", "region");
    envVarMap.put("PLUGIN_ASSUME_ROLE", "assume-role");
    envVarMap.put("PLUGIN_OIDC_TOKEN_ID", "oidc-token-id");
    envVarMap.put("PLUGIN_EXTERNAL_ID", "external-id");

    doAnswer(invocation -> {
      // Simulating serializerUtils behavior to set environment variables
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    assertEquals("harness_placeholder_aws_creds", config.getAccessKey());
    assertEquals("harness_placeholder_aws_creds", config.getSecretKey());
    assertEquals("endpoint", config.getEndpoint());
    assertEquals("bucket", config.getBucket());
    assertEquals("region", config.getRegion());
    assertEquals("assume-role", config.getAssumeRole());
    assertEquals("oidc-token-id", config.getOidcTokenId());
    assertEquals("external-id", config.getExternalId());
    assertEquals("s3", config.getBackend());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigValidValuesGCPJSONKeyDecrpytCIManager() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking environment variables
    envVarMap.put("PLUGIN_ENDPOINT", "endpoint");
    envVarMap.put("PLUGIN_BACKEND", "gcs");
    envVarMap.put("PLUGIN_BUCKET", "bucket");

    envVarMap.put("PLUGIN_JSON_KEY", "gcp-json-key");

    doAnswer(invocation -> {
      // Simulating serializerUtils behavior to set environment variables
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    assertEquals("endpoint", config.getEndpoint());
    assertEquals("bucket", config.getBucket());
    assertEquals("Z2NwLWpzb24ta2V5", config.getGcpJsonKey());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigValidValuesGCPJSONKeyDecrpytDelegate() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking environment variables
    envVarMap.put("PLUGIN_ENDPOINT", "endpoint");
    envVarMap.put("PLUGIN_BACKEND", "gcs");
    envVarMap.put("PLUGIN_BUCKET", "bucket");

    envVarMap.put("PLUGIN_JSON_KEY", "gcp-json-key");

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_DLC_GCP_JSON_KEY_DECRYPTION_ON_DELEGATE, accountId))
        .thenReturn(true);

    doAnswer(invocation -> {
      // Simulating serializerUtils behavior to set environment variables
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    assertEquals("endpoint", config.getEndpoint());
    assertEquals("bucket", config.getBucket());
    assertEquals("harness_placeholder_gcp_creds", config.getGcpJsonKey());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigValidValuesGCPOIDC() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking environment variables
    envVarMap.put("PLUGIN_ENDPOINT", "endpoint");
    envVarMap.put("PLUGIN_BACKEND", "gcs");
    envVarMap.put("PLUGIN_BUCKET", "bucket");

    envVarMap.put("PLUGIN_OIDC_TOKEN_ID", "oidc-token-id");
    envVarMap.put("PLUGIN_PROJECT_NUMBER", "project-number");
    envVarMap.put("PLUGIN_POOL_ID", "pool-id");
    envVarMap.put("PLUGIN_PROVIDER_ID", "provider-id");
    envVarMap.put("PLUGIN_SERVICE_ACCOUNT_EMAIL", "service-account-email");

    doAnswer(invocation -> {
      // Simulating serializerUtils behavior to set environment variables
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    assertEquals("endpoint", config.getEndpoint());
    assertEquals("bucket", config.getBucket());
    assertEquals("oidc-token-id", config.getOidcTokenId());
    assertEquals("project-number", config.getOidcProjectId());
    assertEquals("pool-id", config.getOidcPoolId());
    assertEquals("provider-id", config.getOidcProviderId());
    assertEquals("service-account-email", config.getOidcServiceAccountEmail());
    assertEquals("gcs", config.getBackend());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigMissingAccessKeyAndSecretKey() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking environment variables without access and secret keys
    envVarMap.put("PLUGIN_BACKEND", "s3");
    envVarMap.put("PLUGIN_ENDPOINT", "endpoint");
    envVarMap.put("PLUGIN_BUCKET", "bucket");

    doAnswer(invocation -> {
      // Simulating serializerUtils behavior to set environment variables
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    // Verifying default placeholder credentials are used
    assertEquals("harness_placeholder_aws_creds", config.getAccessKey());
    assertEquals("harness_placeholder_aws_creds", config.getSecretKey());
    assertEquals("endpoint", config.getEndpoint());
    assertEquals("bucket", config.getBucket());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigExceptionHandling() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();

    // Simulating an exception being thrown by serializerUtils
    doThrow(new RuntimeException("Test Exception"))
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    // Verifying the method returns null in case of exception
    assertNull(config);
    // Optionally, you can verify if the log was called
    // verify(logger).error(contains("Something went wrong"), any(RuntimeException.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigEmptyEnvVarMap() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Empty environment variable map
    doAnswer(invocation -> {
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetSelfHostedCacheConfigAzureBackend() {
    String accountId = "testAccount";
    String orgId = "testOrg";
    String projectId = "testProject";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .build();
    Map<String, String> envVarMap = new HashMap<>();

    // Mocking Azure backend environment variables
    envVarMap.put(PLUGIN_BACKEND, CACHE_AZURE_BACKEND);
    envVarMap.put(PLUGIN_ACCOUNT_NAME, "azureStorageAccount");
    envVarMap.put(AZURE_CONTAINER_NAME, "myContainer");
    envVarMap.put(CLIENT_ID, "test-client-id");
    envVarMap.put(TENANT_ID, "test-tenant-id");

    doAnswer(invocation -> {
      Map<String, String> map = invocation.getArgument(1);
      map.putAll(envVarMap);
      return null;
    })
        .when(serializerUtils)
        .setIntelligencePluginEnvVariables(any(), any());

    CIDockerLayerCachingConfig config = ciDockerLayerCachingConfigService.getSelfHostedCacheConfig(ambiance);

    assertNotNull(config);
    assertEquals(CACHE_AZURE_BACKEND, config.getBackend());
    assertEquals("azureStorageAccount", config.getAccountName());
    assertEquals("myContainer", config.getContainerName());
    assertEquals("test-client-id", config.getClientId());
    assertEquals("test-tenant-id", config.getTenantId());
    assertEquals(DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS, config.getClientSecret());
    // Azure backend should not populate S3/GCS fields
    assertEquals("", config.getAccessKey());
    assertEquals("", config.getSecretKey());
    assertEquals("", config.getGcpJsonKey());
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheFromArgIgnoreEmptyAzure() {
    CIDockerLayerCachingConfig config = CIDockerLayerCachingConfig.builder()
                                            .backend(CACHE_AZURE_BACKEND)
                                            .accountName("azureStorageAccount")
                                            .containerName("myContainer")
                                            .clientId("test-client-id")
                                            .tenantId("test-tenant-id")
                                            .clientSecret(DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS)
                                            .build();

    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArgIgnoreEmpty(config, "");

    assertThat(cacheFrom).contains("type=azure");
    assertThat(cacheFrom).contains("account_name=azureStorageAccount");
    assertThat(cacheFrom).contains("container_name=myContainer");
    assertThat(cacheFrom).contains("client_id=test-client-id");
    assertThat(cacheFrom).contains("tenant_id=test-tenant-id");
    assertThat(cacheFrom).contains("client_secret=" + DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS);
    // Azure backend should not include bucket
    assertThat(cacheFrom).doesNotContain("bucket");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheToArgIgnoreEmptyAzure() {
    CIDockerLayerCachingConfig config = CIDockerLayerCachingConfig.builder()
                                            .backend(CACHE_AZURE_BACKEND)
                                            .accountName("azureStorageAccount")
                                            .containerName("myContainer")
                                            .clientId("test-client-id")
                                            .tenantId("test-tenant-id")
                                            .clientSecret(DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS)
                                            .build();

    String cacheTo = ciDockerLayerCachingConfigService.getCacheToArgIgnoreEmpty(config, "");

    assertThat(cacheTo).contains("type=azure");
    assertThat(cacheTo).contains("account_name=azureStorageAccount");
    assertThat(cacheTo).contains("container_name=myContainer");
    assertThat(cacheTo).contains("client_id=test-client-id");
    assertThat(cacheTo).contains("tenant_id=test-tenant-id");
    assertThat(cacheTo).contains("client_secret=" + DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS);
    assertThat(cacheTo).contains("mode=max");
    assertThat(cacheTo).contains("ignore-error=true");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetCacheFromArgIgnoreEmptyAzureWithPrefix() {
    CIDockerLayerCachingConfig config = CIDockerLayerCachingConfig.builder()
                                            .backend(CACHE_AZURE_BACKEND)
                                            .accountName("azureStorageAccount")
                                            .containerName("myContainer")
                                            .clientId("test-client-id")
                                            .tenantId("test-tenant-id")
                                            .clientSecret(DLC_SELF_HOSTED_PLACEHOLDER_AZURE_CREDS)
                                            .build();

    String cacheFrom = ciDockerLayerCachingConfigService.getCacheFromArgIgnoreEmpty(config, "testAccount/testPrefix/");

    assertThat(cacheFrom).contains("type=azure");
    assertThat(cacheFrom).contains("prefix=testAccount/testPrefix/");
    assertThat(cacheFrom).contains("account_name=azureStorageAccount");
    assertThat(cacheFrom).contains("container_name=myContainer");
  }
}
