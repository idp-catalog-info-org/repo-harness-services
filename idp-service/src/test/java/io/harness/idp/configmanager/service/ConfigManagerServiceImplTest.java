/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.idp.configmanager.events.appconfigs.AppConfigCreateEvent.APP_CONFIG_CREATED;
import static io.harness.idp.configmanager.events.appconfigs.AppConfigUpdateEvent.APP_CONFIG_UPDATED;
import static io.harness.idp.configmanager.events.plugin.PluginDisableEvent.PLUGIN_DISABLED;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.event.Event;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;
import io.harness.springdata.TransactionHelper;

import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class ConfigManagerServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock PluginInfoRepository pluginInfoRepository;

  @Mock private AppConfigRepository appConfigRepository;

  @Mock private ConfigEnvVariablesService configEnvVariablesService;

  @Mock private K8sClient k8sClient;

  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private NamespaceService namespaceService;
  @Mock private PluginsProxyInfoService pluginsProxyInfoService;
  @Mock private TransactionHelper transactionHelper;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;
  @Mock private BackstagePermissionsService backstagePermissionsService;
  @Mock private IdpCommonService idpCommonService;
  @Mock private GitIntegrationUtils gitIntegrationUtils;
  String env = "prod";
  Boolean dynamicConfigResolution = true;
  ConfigManagerServiceImpl configManagerServiceImpl;
  @Mock PluginInfoService pluginInfoService;
  @Mock CustomPluginService customPluginService;
  @Captor private ArgumentCaptor<Event> outboxEventCaptor;
  @Named("proxyEndPointEnv") String proxyEndPointEnv = "prod";
  @Named("base") String base = "app";
  @Mock private ServiceHttpClientConfig backstageClientConfig;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    configManagerServiceImpl = new ConfigManagerServiceImpl(env, dynamicConfigResolution, pluginInfoRepository,
        appConfigRepository, k8sClient, namespaceService, configEnvVariablesService, backstageEnvVariableService,
        pluginsProxyInfoService, transactionHelper, pluginInfoService, customPluginService, transactionTemplate,
        outboxService, backstagePermissionsService, idpCommonService, backstageClientConfig);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final ConfigType TEST_PLUGIN_CONFIG_TYPE = ConfigType.PLUGIN;
  static final String TEST_CONFIG_ID = "kafka";
  static final String TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER = "harness-ci-cd";
  static final String TEST_CONFIG_NAME = "test-config-name";
  static final String TEST_HARNESS_CI_CD_PLUGIN_NAME = "Harness CI/CD";
  static final String TEST_CONFIG_VALUE = "kafka:\n  clientId: backstage\n  clusters:\n    - name: cluster\n      "
      + "dashboardUrl: https://akhq.io/\n      brokers:\n        - localhost:9092";
  static final String TEST_CONFIG_VALUE_UPDATED =
      "kafka:\n  clientId: backstage\n  clusters:\n    - name: cluster\n      dashboardUrl: https://akhq1.io/\n      "
      + "brokers:\n        - localhost:9092";

  static final String CUSTOM_PLUGIN_ID1 = "custom-plugin1";
  static final String CUSTOM_PLUGIN_ID2 = "custom-plugin2";
  static final String CUSTOM_PLUGIN_CONFIG_VALUE =
      "proxy:\n  endpoints:\n    '/pagerduty':\n      target: 'https://app.harness.io/'";
  static final String PAGER_DUTY_CONFIG_VALUE =
      "proxy:\n  endpoints:\n    '/pagerduty':\n      target: 'https://pagerduty.harness.com/'";
  static final String K8s_CONFIG_VALUE = "kubernetes:\n"
      + "  serviceLocatorMethod:\n"
      + "    type: multiTenant\n"
      + "  clusterLocatorMethods:\n"
      + "    - type: config\n"
      + "      clusters:\n"
      + "        - url: https://35.238.78.97\n"
      + "          name: backstage-cluster\n"
      + "          authProvider: 'google'\n"
      + "          skipTLSVerify: true\n"
      + "          skipMetricsLookup: false\n";
  static final String TEST_INVALID_CONFIG_VALUE =
      "kafk2da:\n  clie23dntId: backstage\n  clusters:\n    - name: cluster\n      dashboardUrl: https://akhq.io/\n    "
      + "  brokers:\n        - localhost:9092";
  static final String TEST_HARNESS_CI_CD_PLUGIN_CONFIG =
      "proxy:\n  endpoints:\n    '/harness/prod':\n      target: 'https://app.harness.io/'\n      pathRewrite:\n       "
      + " '/api/proxy/harness/prod/?': '/'\n      allowedHeaders:\n        - authorization";
  static final Boolean TEST_ENABLED = true;
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT_TIME = 1681756036;
  static final String TEST_SECRET_ID = "test-secret-id";
  static final String TEST_SECRET_ENV_NAME = "test-env-name";

  static final String TEST_VALID_MERGED_APP_CONFIG = "proxy:\n"
      + "  endpoints:\n"
      + "    /harness/${HARNESS_PROXY_END_POINT}:\n"
      + "      target: ${HARNESS_BASE_URL}/\n"
      + "      pathRewrite:\n"
      + "        /api/proxy/harness/${HARNESS_PROXY_END_POINT}/?: /\n"
      + "      allowedHeaders:\n"
      + "      - authorization\n"
      + "      - Harness-Account\n"
      + "    /harness/scorecard:\n"
      + "      target: ${HARNESS_BASE_URL}/\n"
      + "      pathRewrite:\n"
      + "        /api/proxy/harness/scorecard/?: /\n"
      + "      allowedHeaders:\n"
      + "      - authorization\n"
      + "      - Harness-Account\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String TEST_INVALID_MERGED_APP_CONFIG_WITH_CUSTOM_PLUGIN = "proxy:\n"
      + "  endpoints:\n"
      + "    /harness/prod:\n"
      + "      target: https://app.harness.io/\n"
      + "      pathRewrite:\n"
      + "        /api/proxy/harness/prod/?: /\n"
      + "      allowedHeaders:\n"
      + "      - authorization\n"
      + "    /harness/scorecard:\n"
      + "      target: https://app.harness.io/\n"
      + "      pathRewrite:\n"
      + "        /api/proxy/harness/scorecard/?: /\n"
      + "      allowedHeaders:\n"
      + "      - authorization\n"
      + "      - Harness-Account\n"
      + "    /custom-plugin:\n"
      + "      target: https://app.harness.io/\n"
      + "    /abc:\n"
      + "      target: https://app.harness.io/\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String TEST_INVALID_MERGED_APP_CONFIG = "---\n"
      + "proxerhehy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n";
  static final String TEST_NAMESPACE_FOR_ACCOUNT = "test-namespace";

  static final String TEST_EXPECTED_CONFIG_VALUE_AFTER_MERGE = "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String TEST_PROXY_HOST_VALUE = "TEST_PROXY_HOST_VALUE";
  static final Boolean TEST_PROXY_BOOLEAN_VALUE = true;
  static final String TEST_PROXY_DELEGATE_SELECTOR_DELEGATE = "TEST_DELEGATE_SELECTOR";

  static final String TEST_INVALID_CONFIG_ID = "test-invalid-config-id";
  private final String TEST_ERROR_READING_SCHEMA =
      "Error in reading schema - Invalid config id provided - test-invalid-config-id";
  private final String TEST_ERROR_FOR_INVALID_CONFIG = "Invalid config provided for Plugin id - kafka";
  static final List<String> TEST_PROXY_DELEGATE_SELECTOR =
      Collections.singletonList(TEST_PROXY_DELEGATE_SELECTOR_DELEGATE);
  static final String TEST_HOST_VALUE = "test_host_value";

  static final String TEST_VALID_INTEGRATION_CONFIG = "integrations:\n"
      + "  github:\n"
      + "    - host: HOST_VALUE\n"
      + "      apiBaseUrl: API_BASE_URL\n"
      + "      token: ${HARNESS_GITHUB_TOKEN}";

  static final String TEST_INVALID_INTEGRATION_CONFIG = "inwetegrations:\n"
      + "  gawfwqeithub:\n"
      + "    - host: HOST_VALUE\n"
      + "      apiBaseUrl: API_BASE_URL\n"
      + "      token: ${HARNESS_GITHUB_TOKEN}";
  static final String OLD_MERGED_APP_CONFIG = "old-merged-app-config";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginConfig() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.empty());
    AppConfig appConfig =
        configManagerServiceImpl.getAppConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, ConfigType.PLUGIN);
    assertNull(appConfig);

    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.of(appConfigEntity));
    appConfig = configManagerServiceImpl.getAppConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, ConfigType.PLUGIN);
    assertNotNull(appConfig);
    assertEquals(appConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(appConfig.getConfigName(), TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllPluginIdsMap() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(appConfigRepository.findAllByAccountIdentifierAndConfigType(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Arrays.asList(appConfigEntity));
    Map<String, Boolean> pluginIdMap = configManagerServiceImpl.getAllPluginIdsMap(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(pluginIdMap.get(TEST_CONFIG_ID), TEST_ENABLED);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveConfigForAccount() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(pluginsProxyInfoService.insertProxyHostDetailsForPlugin(any(), any(), any()))
        .thenReturn(Collections.singletonList(new ProxyHostDetail()));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity(true));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    appConfig.setProxy(getTestProxyHostDetails());
    AppConfig savedAppConfig = configManagerServiceImpl.saveConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = savedAppConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(savedAppConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(savedAppConfig.getConfigId(), TEST_CONFIG_ID);

    // Handling for harness-ci-cd plugin
    AppConfigEntity harnessCiCdPluginEntity = getTestAppConfigEntity(true);
    harnessCiCdPluginEntity.setEnabled(true);
    harnessCiCdPluginEntity.setConfigId(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER);
    harnessCiCdPluginEntity.setConfigName(TEST_HARNESS_CI_CD_PLUGIN_NAME);
    harnessCiCdPluginEntity.setConfigs(TEST_HARNESS_CI_CD_PLUGIN_CONFIG);
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(harnessCiCdPluginEntity);
    appConfig = new AppConfig();
    appConfig.setConfigId(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER);
    appConfig.setConfigName(TEST_HARNESS_CI_CD_PLUGIN_NAME);
    appConfig.setConfigs(TEST_HARNESS_CI_CD_PLUGIN_CONFIG);
    savedAppConfig = configManagerServiceImpl.saveConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);
    assertEquals(true, savedAppConfig.isEnabled().booleanValue());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER, savedAppConfig.getConfigId());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_NAME, savedAppConfig.getConfigName());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_CONFIG, savedAppConfig.getConfigs());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateConfigForAccount() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.updateConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(pluginsProxyInfoService.updateProxyHostDetailsForPlugin(any(), any(), any()))
        .thenReturn(Collections.singletonList(new ProxyHostDetail()));
    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class))).thenReturn(null);
    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(getTestAppConfigEntity(true));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);

    Exception exception = null;
    try {
      configManagerServiceImpl.updateConfigForAccount(
          appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);

    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class)))
        .thenReturn(getTestAppConfigEntity(true));
    AppConfig updatedConfig = configManagerServiceImpl.updateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = updatedConfig.getEnvVariables();

    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(updatedConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(updatedConfig.getConfigId(), TEST_CONFIG_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveOrUpdateConfigForAccountForSave() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity(true));
    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(null);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    AppConfig savedAppConfig = configManagerServiceImpl.saveOrUpdateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);

    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = savedAppConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(savedAppConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(savedAppConfig.getConfigId(), TEST_CONFIG_ID);
    verify(outboxService).save(outboxEventCaptor.capture());
    assertEquals(APP_CONFIG_CREATED, outboxEventCaptor.getValue().getEventType());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveOrUpdateCustomPluginConfigThrowsException() {
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN, true))
        .thenReturn(List.of(getAppConfigEntityForPagerDuty(), getAppConfigEntityForCustomPlugin()));
    CustomPluginInfoEntity customPluginEntity1 = CustomPluginInfoEntity.builder().build();
    customPluginEntity1.setIdentifier(CUSTOM_PLUGIN_ID1);
    customPluginEntity1.setName(CUSTOM_PLUGIN_ID1);
    customPluginEntity1.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    customPluginEntity1.setType(PluginInfo.PluginTypeEnum.CUSTOM);
    CustomPluginInfoEntity customPluginEntity2 = CustomPluginInfoEntity.builder().build();
    customPluginEntity2.setIdentifier(CUSTOM_PLUGIN_ID2);
    customPluginEntity2.setName(CUSTOM_PLUGIN_ID2);
    customPluginEntity2.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    customPluginEntity2.setType(PluginInfo.PluginTypeEnum.CUSTOM);
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(any(), any()))
        .thenReturn(Optional.of(customPluginEntity1))
        .thenReturn(Optional.of(customPluginEntity2));
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(CUSTOM_PLUGIN_ID1);
    appConfig.setConfigs(CUSTOM_PLUGIN_CONFIG_VALUE);
    configManagerServiceImpl.validateProxyEndpointsForPlugin(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveOrUpdateConfigForAccountForUpdate() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity(true));
    AppConfigEntity appConfigToBeUpdated = getTestAppConfigEntity(true);
    appConfigToBeUpdated.setConfigs(TEST_CONFIG_VALUE_UPDATED);
    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(appConfigToBeUpdated);
    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class)))
        .thenReturn(appConfigToBeUpdated);
    when(configEnvVariablesService.updateConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    AppConfig updatedConfig = configManagerServiceImpl.saveOrUpdateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);

    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = updatedConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(updatedConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(updatedConfig.getConfigId(), TEST_CONFIG_ID);
    verify(outboxService).save(outboxEventCaptor.capture());
    assertEquals(APP_CONFIG_UPDATED, outboxEventCaptor.getValue().getEventType());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveOrUpdateConfigForAccountForUpdateWhenOldConfigIsNotPresent() {
    // oldAppConfig.getConfigs() is null
    // Custom plugin case we are trying to add config to a plugin which was never enabled and no config was added prior
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity(true));
    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(getTestAppConfigEntity(true))
        .thenReturn(getTestAppConfigEntity(false));
    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class)))
        .thenReturn(getTestAppConfigEntity(true));
    when(configEnvVariablesService.updateConfigEnvVariables(any(AppConfig.class), any(String.class), eq(false)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    AppConfig updatedConfig = configManagerServiceImpl.saveOrUpdateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, false);

    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = updatedConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(updatedConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(updatedConfig.getConfigId(), TEST_CONFIG_ID);
    verify(outboxService).save(outboxEventCaptor.capture());
    assertEquals(APP_CONFIG_UPDATED, outboxEventCaptor.getValue().getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeAllAppConfigsForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    when(idpCommonService.getConfigWithEnvSpecificValuesReplaced(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity));
    String mergedConfig = configManagerServiceImpl.mergeAllAppConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_VALID_MERGED_APP_CONFIG, mergedConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeAndSaveAppConfig() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    when(idpCommonService.getConfigWithEnvSpecificValuesReplaced(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, TEST_ENABLED))
        .thenReturn(Collections.singletonList(appConfigEntity));
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE_FOR_ACCOUNT);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
    when(k8sClient.updateConfigMapData(any(String.class), any(String.class), any(Map.class), any(Boolean.class)))
        .thenReturn(null);
    configManagerServiceImpl.mergeAndUpdateConfigInNamespace(TEST_ACCOUNT_IDENTIFIER, OLD_MERGED_APP_CONFIG);
    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE_FOR_ACCOUNT), eq(BACKSTAGE_SECRET), anyMap());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeEnabledPluginConfigsForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity, appConfigEntity));
    when(configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
             TEST_ACCOUNT_IDENTIFIER, Arrays.asList(TEST_CONFIG_ID)))
        .thenReturn(Arrays.asList(TEST_SECRET_ENV_NAME));
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(TEST_SECRET_ENV_NAME);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    when(backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
             any(String.class), anyList()))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    MergedPluginConfigs mergedPluginConfigs =
        configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(mergedPluginConfigs.getConfig(), TEST_EXPECTED_CONFIG_VALUE_AFTER_MERGE);
    assertEquals(mergedPluginConfigs.getEnvVariables().get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(mergedPluginConfigs.getEnvVariables().get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);

    // check if no plugin is enabled with configs for an account

    appConfigEntity = getTestAppConfigEntity(true);
    appConfigEntity.setConfigs(null);
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Collections.singletonList(appConfigEntity));
    when(configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
             TEST_ACCOUNT_IDENTIFIER, Arrays.asList(TEST_CONFIG_ID)))
        .thenReturn(Collections.emptyList());
    when(backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
             any(String.class), anyList()))
        .thenReturn(Collections.emptyList());
    mergedPluginConfigs = configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertNull(mergedPluginConfigs.getConfig());
    assertEquals(mergedPluginConfigs.getEnvVariables().size(), 0);

    // check if no plugin is enabled for an account

    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Collections.emptyList());
    mergedPluginConfigs = configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertNull(mergedPluginConfigs.getConfig());
    assertEquals(mergedPluginConfigs.getEnvVariables().size(), 0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveUpdateAndMergeConfigForAccount() {
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigName(TEST_CONFIG_NAME);
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    when(transactionHelper.performTransaction(any())).thenReturn(appConfig);
    AppConfig returnedConfig = configManagerServiceImpl.saveUpdateAndMergeConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN, false);
    assertNotNull(returnedConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo() {
    when(appConfigRepository.deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(any(Long.class)))
        .thenReturn(Collections.singletonList(getTestAppConfigEntity(true)));
    List<AppConfigEntity> appConfigEntities =
        configManagerServiceImpl.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
    assertEquals(appConfigEntities.get(0).getConfigId(), TEST_CONFIG_ID);
    assertEquals(appConfigEntities.get(0).getConfigName(), TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  @Ignore("We have disabled validation for custom plugin")
  public void testValidateSchemaForPlugin() {
    Exception exception = null;
    try {
      configManagerServiceImpl.validateSchemaForPlugin(TEST_CONFIG_VALUE, TEST_INVALID_CONFIG_ID);
    } catch (Exception e) {
      exception = e;
    }
    assertEquals(TEST_ERROR_READING_SCHEMA, exception.getMessage());

    try {
      configManagerServiceImpl.validateSchemaForPlugin(TEST_INVALID_CONFIG_VALUE, TEST_CONFIG_ID);
    } catch (Exception e) {
      exception = e;
    }
    assertEquals(TEST_ERROR_FOR_INVALID_CONFIG, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateOrUpdateTimeStampEnvVariable() {
    configManagerServiceImpl.createOrUpdateTimeStampEnvVariable(TEST_ACCOUNT_IDENTIFIER);
    verify(backstageEnvVariableService, times(1)).createOrUpdate(any(), any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveAndMergeAppConfigForGitIntegrations() throws Exception {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    connectorInfoDTO.setConnectorType(ConnectorType.GITHUB);
    MockedStatic<GitIntegrationUtils> mockRestStatic = Mockito.mockStatic(GitIntegrationUtils.class);
    mockRestStatic.when(() -> GitIntegrationUtils.getHostForConnector(any())).thenReturn(TEST_HOST_VALUE);
    when(transactionHelper.performTransaction(any())).thenReturn(new AppConfig());
    configManagerServiceImpl.saveAndMergeAppConfigForGitIntegrations(
        TEST_ACCOUNT_IDENTIFIER, connectorInfoDTO, TEST_VALID_INTEGRATION_CONFIG, ConnectorType.GITHUB.toString());

    // for invalid case
    configManagerServiceImpl.saveAndMergeAppConfigForGitIntegrations(
        TEST_ACCOUNT_IDENTIFIER, connectorInfoDTO, TEST_INVALID_INTEGRATION_CONFIG, ConnectorType.GITHUB.toString());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToggleConfigForAccount() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfigEntity appConfigEntity = getTestAppConfigEntity(true);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(null);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(any(), any(), any()))
        .thenReturn(Optional.of(appConfigEntity));
    doNothing().when(configEnvVariablesService).deleteConfigEnvVariables(any(), any());
    doNothing().when(pluginsProxyInfoService).deleteProxyHostDetailsForPlugin(any(), any());
    AppConfig appConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, false, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
    assertNull(appConfig);

    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.save(any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    AppConfig returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
    assertEquals(returnedAppConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(returnedAppConfig.getConfigs(), TEST_CONFIG_VALUE);
    assertEquals(returnedAppConfig.getConfigName(), TEST_CONFIG_NAME);

    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(null);
    when(appConfigRepository.save(any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
    assertEquals(returnedAppConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(returnedAppConfig.getConfigs(), TEST_CONFIG_VALUE);
    assertEquals(returnedAppConfig.getConfigName(), TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testToggleConfigForK8s() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfigEntity appConfigEntity = getAppConfigEntityForK8s();
    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(any(), any(), any()))
        .thenReturn(Optional.of(AppConfigEntity.builder().build()));
    AppConfig returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, Constants.KUBERNETES_PLUGIN, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
    assertEquals(returnedAppConfig.getConfigId(), Constants.KUBERNETES_PLUGIN);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testToggleConfigForK8sThrowsException() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfigEntity appConfigEntity = getAppConfigEntityForK8s();
    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, Constants.KUBERNETES_PLUGIN, ConfigType.PLUGIN))
        .thenReturn(Optional.of(appConfigEntity));
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, Constants.GOOGLE_AUTH, ConfigType.AUTH))
        .thenReturn(Optional.empty());
    configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, Constants.KUBERNETES_PLUGIN, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testToggleConfigForGithubInsights() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfigEntity appConfigEntity = getAppConfigEntityForGithubInsights();
    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(any(), any(), any()))
        .thenReturn(Optional.of(AppConfigEntity.builder().build()));
    AppConfig returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, Constants.GITHUB_INSIGHTS_PLUGIN, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
    assertEquals(returnedAppConfig.getConfigId(), Constants.GITHUB_INSIGHTS_PLUGIN);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testToggleConfigForGithubInsightsThrowsException() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    AppConfigEntity appConfigEntity = getAppConfigEntityForGithubInsights();
    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, Constants.GITHUB_INSIGHTS_PLUGIN, ConfigType.PLUGIN))
        .thenReturn(Optional.of(AppConfigEntity.builder().build()));
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, Constants.GITHUB_AUTH, ConfigType.AUTH))
        .thenReturn(Optional.empty());
    configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, Constants.GITHUB_INSIGHTS_PLUGIN, true, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllConfigs() {
    when(appConfigRepository.findAll()).thenReturn(Collections.singletonList(getTestAppConfigEntity(true)));
    List<AppConfigEntity> appConfigEntity = configManagerServiceImpl.getAllConfigs();
    assertEquals(TEST_CONFIG_ID, appConfigEntity.get(0).getConfigId());
    assertEquals(TEST_CONFIG_NAME, appConfigEntity.get(0).getConfigName());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateAppConfig() {
    when(appConfigRepository.updateConfig(any(), any())).thenReturn(getTestAppConfigEntity(true));
    AppConfigEntity appConfigEntity =
        configManagerServiceImpl.updateAppConfig(getTestAppConfigEntity(true), ConfigType.PLUGIN);
    assertEquals(TEST_CONFIG_ID, appConfigEntity.getConfigId());
    assertEquals(TEST_CONFIG_NAME, appConfigEntity.getConfigName());
  }

  private AppConfigEntity getTestAppConfigEntity(boolean withConfig) {
    AppConfigEntity.AppConfigEntityBuilder builder = AppConfigEntity.builder()
                                                         .id(TEST_ID)
                                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                         .configType(TEST_PLUGIN_CONFIG_TYPE)
                                                         .configId(TEST_CONFIG_ID)
                                                         .configName(TEST_CONFIG_NAME)
                                                         .enabled(TEST_ENABLED)
                                                         .createdAt(TEST_CREATED_AT_TIME)
                                                         .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
                                                         .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME);

    if (withConfig) {
      builder.configs(TEST_CONFIG_VALUE);
    }

    return builder.build();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteConfigForEnabledPlugin() {
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    when(idpCommonService.getConfigWithEnvSpecificValuesReplaced(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(getTestAppConfigEntity(true));
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, true))
        .thenReturn(Collections.singletonList(getTestAppConfigEntity(true)))
        .thenReturn(Collections.emptyList());
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE_FOR_ACCOUNT);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);

    configManagerServiceImpl.deleteConfig(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);

    verify(configEnvVariablesService).deleteConfigEnvVariables(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID);
    verify(pluginsProxyInfoService).deleteProxyHostDetailsForPlugin(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID);
    verify(outboxService).save(outboxEventCaptor.capture());
    assertEquals(PLUGIN_DISABLED, outboxEventCaptor.getValue().getEventType());
    verify(appConfigRepository)
        .updateConfigEnablement(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, false, TEST_PLUGIN_CONFIG_TYPE);
    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE_FOR_ACCOUNT), eq(BACKSTAGE_SECRET), anyMap());
    verify(pluginInfoService).updatePluginsMetadataOnGcs(TEST_ACCOUNT_IDENTIFIER);
    verify(pluginInfoService).updatePluginsMetadataOnGcs(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteConfigForNeverEnabledPlugin() {
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    when(idpCommonService.getConfigWithEnvSpecificValuesReplaced(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(null);

    configManagerServiceImpl.deleteConfig(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE, TEST_CONFIG_NAME);

    verify(backstageEnvVariableService).createOrUpdate(any(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  private AppConfigEntity getAppConfigEntityForCustomPlugin() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId(CUSTOM_PLUGIN_ID1)
        .configName(CUSTOM_PLUGIN_ID1)
        .configs(CUSTOM_PLUGIN_CONFIG_VALUE)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }

  private AppConfigEntity getAppConfigEntityForPagerDuty() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId("pager-duty")
        .configName("pager-duty")
        .configs(PAGER_DUTY_CONFIG_VALUE)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }

  private AppConfigEntity getAppConfigEntityForK8s() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId(Constants.KUBERNETES_PLUGIN)
        .configName(Constants.KUBERNETES_PLUGIN)
        .configs(K8s_CONFIG_VALUE)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }

  private AppConfigEntity getAppConfigEntityForGithubInsights() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId(Constants.GITHUB_INSIGHTS_PLUGIN)
        .configName(Constants.GITHUB_INSIGHTS_PLUGIN)
        .configs(null)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }

  private List<ProxyHostDetail> getTestProxyHostDetails() {
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setHost(TEST_PROXY_HOST_VALUE);
    proxyHostDetail.setProxy(TEST_PROXY_BOOLEAN_VALUE);
    proxyHostDetail.setSelectors(TEST_PROXY_DELEGATE_SELECTOR);
    return Collections.singletonList(proxyHostDetail);
  }
}
