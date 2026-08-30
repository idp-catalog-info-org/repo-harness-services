/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.AUTHORIZATION_PROPERTY;
import static io.harness.idp.common.Constants.BACKSTAGE_BASE_URL_LOCAL_VALUE;
import static io.harness.idp.common.Constants.CUSTOM_PLUGIN;
import static io.harness.idp.common.Constants.ENDPOINTS_PROPERTY;
import static io.harness.idp.common.Constants.GITHUB_COPILOT_PLUGIN;
import static io.harness.idp.common.Constants.GITLAB_PLUGIN;
import static io.harness.idp.common.Constants.HARNESS_PROXY;
import static io.harness.idp.common.Constants.HEADERS_PROPERTY;
import static io.harness.idp.common.Constants.IDP_PLUGIN_ORIGIN_HEADER;
import static io.harness.idp.common.Constants.KUBERNETES_PLUGIN;
import static io.harness.idp.common.Constants.LAST_UPDATED_TIMESTAMP;
import static io.harness.idp.common.Constants.LOCAL_ENV;
import static io.harness.idp.common.YamlUtils.merge;
import static io.harness.idp.common.YamlUtils.yamlObject;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asJsonNode;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asYaml;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.idp.settings.Constants.KUBERNETES_PROXY_PERMISSION;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.events.appconfigs.AppConfigCreateEvent;
import io.harness.idp.configmanager.events.appconfigs.AppConfigUpdateEvent;
import io.harness.idp.configmanager.events.plugin.PluginDisableEvent;
import io.harness.idp.configmanager.events.plugin.PluginEnableEvent;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.configmanager.utils.ProxyTargetValidator;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.spec.server.idp.v1.model.*;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ConfigManagerServiceImpl implements ConfigManagerService {
  private final boolean dynamicConfigResolution;
  PluginInfoRepository pluginInfoRepository;
  AppConfigRepository appConfigRepository;
  K8sClient k8sClient;
  NamespaceService namespaceService;
  ConfigEnvVariablesService configEnvVariablesService;
  BackstageEnvVariableService backstageEnvVariableService;
  PluginsProxyInfoService pluginsProxyInfoService;
  TransactionHelper transactionHelper;
  PluginInfoService pluginInfoService;
  CustomPluginService customPluginService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  BackstagePermissionsService backstagePermissionsService;
  IdpCommonService idpCommonService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private static final String PROXY = "proxy";
  private static final String ENDPOINTS = "endpoints";
  private final String env;
  private final ServiceHttpClientConfig backstageClientConfig;

  @Inject
  public ConfigManagerServiceImpl(@Named("env") String env,
      @Named("dynamicConfigResolution") boolean dynamicConfigResolution, PluginInfoRepository pluginInfoRepository,
      AppConfigRepository appConfigRepository, K8sClient k8sClient, NamespaceService namespaceService,
      ConfigEnvVariablesService configEnvVariablesService, BackstageEnvVariableService backstageEnvVariableService,
      PluginsProxyInfoService pluginsProxyInfoService, TransactionHelper transactionHelper,
      PluginInfoService pluginInfoService, CustomPluginService customPluginService,
      TransactionTemplate transactionTemplate, OutboxService outboxService,
      BackstagePermissionsService backstagePermissionsService, IdpCommonService idpCommonService,
      @Named("backstageHttpClientConfig") ServiceHttpClientConfig backstageClientConfig) {
    this.dynamicConfigResolution = dynamicConfigResolution;
    this.pluginInfoRepository = pluginInfoRepository;
    this.appConfigRepository = appConfigRepository;
    this.k8sClient = k8sClient;
    this.namespaceService = namespaceService;
    this.configEnvVariablesService = configEnvVariablesService;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.pluginsProxyInfoService = pluginsProxyInfoService;
    this.transactionHelper = transactionHelper;
    this.pluginInfoService = pluginInfoService;
    this.customPluginService = customPluginService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.backstagePermissionsService = backstagePermissionsService;
    this.idpCommonService = idpCommonService;
    this.env = env;
    this.backstageClientConfig = backstageClientConfig;
  }

  private static final String PLUGIN_CONFIG_NOT_FOUND =
      "Plugin configs for plugin - %s is not present for account - %s";
  private static final String AUTH_NOT_CONFIGURED =
      "Go to Admin -> OAuth Configurations to setup a %s OAuth app and then come back to enable this plugin";
  private static final String NO_PLUGIN_ENABLED_FOR_ACCOUNT = "No plugin is enabled for account - %s";
  private static final String BASE_APP_CONFIG_PATH = "baseappconfig.yaml";
  private static final String AUTH_EXPERIMENTAL_EXTRA_ALLOWED_ORIGINS_YAML =
      "auth-experimentalExtraAllowedOrigins.yaml";

  private static final String CONFIG_DATA_NAME = "config";

  private static final String CONFIG_NAME = "backstage-override-config";

  private static final long baseTimeStamp = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;

  private static final String INVALID_SCHEMA_FOR_INTEGRATIONS =
      "Invalid json schema for integrations config for account - %s";
  private static final String INVALID_PROXY_ENDPOINT_FOR_PLUGIN =
      "Proxy endpoint %s already used in [%s] plugin. Please modify endpoint %s to avoid conflict";

  private static final String TARGET_TO_REPLACE_IN_GIT_INTEGRATION_CONFIG = "HOST_VALUE";

  private static final Yaml yaml = yamlObject();

  @Override
  public Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier) {
    List<AppConfigEntity> allPluginConfig =
        appConfigRepository.findAllByAccountIdentifierAndConfigType(accountIdentifier, ConfigType.PLUGIN);
    return allPluginConfig.stream().collect(
        Collectors.toMap(AppConfigEntity::getConfigId, AppConfigEntity::getEnabled));
  }

  @Override
  public AppConfig getAppConfig(String accountIdentifier, String configId, ConfigType configType) {
    Optional<AppConfigEntity> config =
        appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(accountIdentifier, configId, configType);
    if (config.isEmpty()) {
      return null;
    }
    return config.map(AppConfigMapper::toDTO).get();
  }

  @Override
  public Map<String, AppConfig> getEnabledPluginsAppConfigs(String accountIdentifier) {
    List<AppConfigEntity> allEnabledConfigEntities =
        appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
            accountIdentifier, ConfigType.PLUGIN, true);

    return allEnabledConfigEntities.stream().collect(
        Collectors.toMap(AppConfigEntity::getConfigId, AppConfigMapper::toDTO));
  }

  @Override
  public AppConfig saveConfigForAccount(
      AppConfig appConfig, String accountIdentifier, ConfigType configType, boolean skipReservedEnvVariableCheck) {
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);
    appConfigEntity.setEnabledDisabledAt(System.currentTimeMillis());
    appConfigEntity.setEnabled(getEnabledFlagBasedOnConfigType(configType));

    List<ProxyHostDetail> pluginProxyHostDetails =
        pluginsProxyInfoService.insertProxyHostDetailsForPlugin(appConfig, accountIdentifier, configType);

    List<BackstageEnvSecretVariable> backstageEnvSecretVariableList =
        configEnvVariablesService.insertConfigEnvVariables(appConfig, accountIdentifier, skipReservedEnvVariableCheck);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (appConfig.getConfigId().equals(Constants.HARNESS_CI_CD_PLUGIN)) {
        appConfigEntity.setEnabled(true);
      }

      AppConfigEntity insertedData = appConfigRepository.save(appConfigEntity);

      AppConfig returnedConfig = AppConfigMapper.toDTO(insertedData);
      returnedConfig.setEnvVariables(backstageEnvSecretVariableList);
      returnedConfig.setProxy(pluginProxyHostDetails);

      if (ConfigType.PLUGIN.equals(configType)) {
        outboxService.save(new AppConfigCreateEvent(accountIdentifier, appConfig));
      }
      return returnedConfig;
    }));
  }

  @Override
  public AppConfig updateConfigForAccount(
      AppConfig appConfig, String accountIdentifier, ConfigType configType, boolean skipReservedEnvVariableCheck) {
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);

    AppConfigEntity appConfigEntityOld =
        appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, appConfig.getConfigId());
    AppConfig oldAppConfig = AppConfigMapper.toDTO(appConfigEntityOld);

    List<BackstageEnvSecretVariable> backstageEnvSecretVariableList =
        configEnvVariablesService.updateConfigEnvVariables(appConfig, accountIdentifier, skipReservedEnvVariableCheck);

    List<ProxyHostDetail> proxyHostDetailList =
        pluginsProxyInfoService.updateProxyHostDetailsForPlugin(appConfig, accountIdentifier, configType);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      AppConfigEntity updatedData = appConfigRepository.updateConfig(appConfigEntity, configType);
      if (ConfigType.PLUGIN.equals(configType) && !Objects.equals(oldAppConfig.getConfigs(), appConfig.getConfigs())) {
        outboxService.save(new AppConfigUpdateEvent(accountIdentifier, appConfig, oldAppConfig));
      }
      if (updatedData == null) {
        throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, appConfig.getConfigId(), accountIdentifier));
      }
      AppConfig returnedConfig = AppConfigMapper.toDTO(updatedData);
      returnedConfig.setEnvVariables(backstageEnvSecretVariableList);

      returnedConfig.setProxy(proxyHostDetailList);

      return returnedConfig;
    }));
  }

  @Override
  public AppConfig saveOrUpdateConfigForAccount(
      AppConfig appConfig, String accountIdentifier, ConfigType configType, boolean skipReservedEnvVariableCheck) {
    if (appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, appConfig.getConfigId()) == null) {
      return saveConfigForAccount(appConfig, accountIdentifier, configType, skipReservedEnvVariableCheck);
    }
    return updateConfigForAccount(appConfig, accountIdentifier, configType, skipReservedEnvVariableCheck);
  }

  @Override
  public AppConfig saveUpdateAndMergeConfigForAccount(
      AppConfig appConfig, String accountIdentifier, ConfigType configType, boolean skipReservedEnvVariableCheck) {
    return transactionHelper.performTransaction(() -> {
      /* Merge and update is needed here for update of config in enabled plugins */
      String oldMergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);
      AppConfig returnConfig =
          saveOrUpdateConfigForAccount(appConfig, accountIdentifier, configType, skipReservedEnvVariableCheck);
      mergeAndUpdateConfigInNamespace(accountIdentifier, oldMergedAppConfig);
      return returnConfig;
    });
  }

  @Override
  public void validateForIntegrationsAndHost(String accountIdentifier, AppConfig appConfig) {
    if (!appConfig.getConfigId().equals(GITHUB_COPILOT_PLUGIN)) {
      return;
    }
    if (isEmpty(appConfig.getConfigs())) {
      return;
    }

    String pluginConfig = appConfig.getConfigs();
    Map<String, Object> entityYamlMapPlugin = YamlUtils.loadYamlStringAsMap(pluginConfig);
    Map<String, Object> copilotMap = (Map<String, Object>) entityYamlMapPlugin.get("copilot");
    String pluginHost = copilotMap != null ? (String) copilotMap.get("host") : null;

    // Check if copilot.enterprise is actually configured (key is present in YAML)
    boolean isEnterpriseUsed = copilotMap != null && copilotMap.containsKey("enterprise");

    List<AppConfigEntity> integrationsConfig = appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
        accountIdentifier, ConfigType.INTEGRATION, true);

    List<AppConfigEntity> githubIntegrations =
        integrationsConfig.stream()
            .filter(config -> config.getConfigId() != null && config.getConfigId().startsWith("GITHUB_"))
            .collect(Collectors.toList());

    if (isEmpty(githubIntegrations)) {
      throw new InvalidRequestException(
          "GitHub integration is not configured in IDP, which is a prerequisite for the plugin.");
    }

    boolean validIntegrationFound = false;

    for (AppConfigEntity appConfigEntity : githubIntegrations) {
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(appConfigEntity.getConfigs());

      Map<String, Object> integrationsMap = (Map<String, Object>) entityYamlMap.get("integrations");
      if (integrationsMap != null && integrationsMap.containsKey("github")) {
        List<Map<String, Object>> githubList = (List<Map<String, Object>>) integrationsMap.get("github");

        if (githubList != null && !githubList.isEmpty()) {
          for (Map<String, Object> githubConfig : githubList) {
            String integrationHost = (String) githubConfig.get("host");

            if (!isEmpty(integrationHost) && integrationHost.equals(pluginHost)) {
              if (isEnterpriseUsed) {
                if (githubConfig.containsKey("token")) {
                  validIntegrationFound = true;
                  break; // found a host+token match, done
                }
                // else: keep searching other integrations
              } else {
                validIntegrationFound = true;
                break; // found host match (no token needed), done
              }
            }
          }
        }
      }

      if (validIntegrationFound && !isEnterpriseUsed) {
        break; // non-enterprise: stop after first match
      }
    }

    if (!validIntegrationFound) {
      if (isEnterpriseUsed) {
        throw new InvalidRequestException("No GitHub integration matches the plugin host and provides PAT-based "
            + "access, which is required when 'copilot.enterprise' is configured. "
            + "Please configure a GitHub connector with 'username and token' as the authentication method, and ensure "
            + "the host matches the one in the plugin config.");
      } else {
        throw new InvalidRequestException(String.format("The GitHub integration host does not match the host - %s "
                + "specified in the GitHub Copilot plugin configuration.",
            pluginHost));
      }
    }
  }

  @Override
  public void validateForGitlabIntegrations(String accountIdentifier, AppConfig appConfig) {
    if (!appConfig.getConfigId().equals(GITLAB_PLUGIN)) {
      return;
    }
    if (isEmpty(appConfig.getConfigs())) {
      return;
    }
    String pluginConfig = appConfig.getConfigs();
    Map<String, Object> entityYamlMapPlugin = YamlUtils.loadYamlStringAsMap(pluginConfig);
    String pluginHost = "gitlab.com";

    List<AppConfigEntity> integrationsConfig = appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
        accountIdentifier, ConfigType.INTEGRATION, true);

    List<AppConfigEntity> gitlabIntegrations =
        integrationsConfig.stream()
            .filter(config -> config.getConfigId() != null && config.getConfigId().startsWith("GITLAB"))
            .collect(Collectors.toList());

    if (isEmpty(gitlabIntegrations)) {
      throw new InvalidRequestException(
          "GitLab integration is not configured in IDP, which is a prerequisite for the plugin.");
    }

    boolean validIntegrationFound = false;

    for (AppConfigEntity appConfigEntity : gitlabIntegrations) {
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(appConfigEntity.getConfigs());

      Map<String, Object> integrationsMap = (Map<String, Object>) entityYamlMap.get("integrations");
      if (integrationsMap != null && integrationsMap.containsKey("gitlab")) {
        List<Map<String, Object>> gitlabList = (List<Map<String, Object>>) integrationsMap.get("gitlab");

        if (gitlabList != null && !gitlabList.isEmpty()) {
          for (Map<String, Object> gitlabConfig : gitlabList) {
            String integrationHost = (String) gitlabConfig.get("host");

            if (!isEmpty(integrationHost) && integrationHost.equals(pluginHost)) {
              validIntegrationFound = true;
              break;
            }
          }
        }
      }

      if (validIntegrationFound) {
        break;
      }
    }

    if (!validIntegrationFound) {
      throw new InvalidRequestException("No GitLab integration matches the plugin host and provides PAT-based "
          + "access, which is required when 'gitlab' is configured. "
          + "Please configure a GitLab connector with 'username and token' as the authentication method, and ensure "
          + "the host matches the one in the plugin config.");
    }
  }

  @Override
  public AppConfig toggleConfigForAccount(
      String accountIdentifier, String configId, Boolean isEnabled, ConfigType configType, String configName) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      AppConfigEntity updatedData;
      AppConfigEntity appConfigEntity =
          appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, configId);
      List<ProxyHostDetail> proxyHostDetailList = new ArrayList<>();
      if (isEnabled) {
        if (appConfigEntity == null) {
          if (configId.equals(HARNESS_PROXY)) {
            throw new InvalidRequestException("AppConfig cannot be null / empty for harness-proxy plugin");
          }
          long currentTime = System.currentTimeMillis();
          AppConfigEntity pluginWithNoConfig = AppConfigEntity.builder()
                                                   .accountIdentifier(accountIdentifier)
                                                   .configType(configType)
                                                   .configId(configId)
                                                   .enabled(true)
                                                   .createdAt(currentTime)
                                                   .lastModifiedAt(currentTime)
                                                   .enabledDisabledAt(currentTime)
                                                   .build();
          updatedData = appConfigRepository.save(pluginWithNoConfig);
        } else {
          if (ConfigType.PLUGIN.equals(configType)) {
            validateForGitlabIntegrations(accountIdentifier, AppConfigMapper.toDTO(appConfigEntity));
            validateForIntegrationsAndHost(accountIdentifier, AppConfigMapper.toDTO(appConfigEntity));
          }
          validateProxyEndpointsForPlugin(AppConfigMapper.toDTO(appConfigEntity), accountIdentifier, configType);
          updatedData = appConfigRepository.updateConfigEnablement(accountIdentifier, configId, isEnabled, configType);
        }
        handleKubernetesProxyPermissionIfApplicable(accountIdentifier, configId, true);
        outboxService.save(new PluginEnableEvent(accountIdentifier, configId, configName));
        updateMetadataAndTriggerWebhook(accountIdentifier, appConfigEntity, configId, true);
        if (isAuthRequired(configId, updatedData.getConfigs())
            && !isAuthConfigured(accountIdentifier, getAuthId(configId))) {
          throw new InvalidRequestException(format(AUTH_NOT_CONFIGURED, getAuthName(configId)));
        }
        proxyHostDetailList = pluginsProxyInfoService.getProxyHostDetailsForPluginId(accountIdentifier, configId);
      } else {
        updatedData = appConfigRepository.updateConfigEnablement(accountIdentifier, configId, isEnabled, configType);
        configEnvVariablesService.deleteConfigEnvVariables(accountIdentifier, configId);
        pluginsProxyInfoService.deleteProxyHostDetailsForPlugin(accountIdentifier, configId);
        handleKubernetesProxyPermissionIfApplicable(accountIdentifier, configId, false);
        outboxService.save(new PluginDisableEvent(accountIdentifier, configId, configName));
        updateMetadataAndTriggerWebhook(accountIdentifier, appConfigEntity, configId, false);
      }
      if (updatedData == null) {
        return null;
      }
      if (isPluginWithNoConfig(accountIdentifier, configId)) {
        createOrUpdateTimeStampEnvVariable(accountIdentifier);
      }
      return AppConfigMapper.toDTO(updatedData, proxyHostDetailList);
    }));
  }

  private void updateMetadataAndTriggerWebhook(
      String accountIdentifier, AppConfigEntity appConfigEntity, String configId, boolean isEnabled) {
    PluginDetailedInfo pluginInfo = pluginInfoService.getPluginDetailedInfo(configId, accountIdentifier, false);
    if ((appConfigEntity == null || !appConfigEntity.getEnabled().equals(isEnabled))
        && pluginInfo instanceof CustomPluginDetailedInfo) {
      pluginInfoService.updatePluginsMetadataOnGcs(accountIdentifier);
    }
  }

  @Override
  public AppConfig toggleAndSave(
      String accountIdentifier, String pluginId, Boolean isEnabled, ConfigType configType, String pluginName) {
    String oldMergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);
    AppConfig disabledPluginAppConfig =
        toggleConfigForAccount(accountIdentifier, pluginId, isEnabled, ConfigType.PLUGIN, pluginName);
    mergeAndUpdateConfigInNamespace(accountIdentifier, oldMergedAppConfig);
    return disabledPluginAppConfig;
  }

  @Override
  public void validateProxyEndpointsForPlugin(AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    // Applies to every config type: any plugin that declares proxy.endpoints reaches the same Backstage egress.
    ProxyTargetValidator.validateProxyTargets(appConfig.getConfigs());
    if (ConfigType.PLUGIN.equals(configType) && appConfig.getConfigs() != null) {
      Set<String> appConfigProxyEndpoints = getProxyEndpoints(appConfig.getConfigs());
      if (!appConfigProxyEndpoints.isEmpty()) {
        validateIfAppConfigsHaveSameProxyEndpoints(appConfigProxyEndpoints, accountIdentifier, appConfig.getConfigId());
      }
    }
  }

  @Override
  public void deleteConfig(String harnessAccount, String configId, ConfigType configType, String configName) {
    AppConfigEntity appConfigEntity = appConfigRepository.findByAccountIdentifierAndConfigId(harnessAccount, configId);
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      String oldMergedAppConfig = mergeAllAppConfigsForAccount(harnessAccount);
      if (appConfigEntity != null) {
        if (appConfigEntity.getEnabled()) {
          configEnvVariablesService.deleteConfigEnvVariables(harnessAccount, configId);
          pluginsProxyInfoService.deleteProxyHostDetailsForPlugin(harnessAccount, configId);
          outboxService.save(new PluginDisableEvent(harnessAccount, configId, configName));
          appConfigRepository.updateConfigEnablement(harnessAccount, configId, false, configType);
          mergeAndUpdateConfigInNamespace(harnessAccount, oldMergedAppConfig);
        }
        pluginInfoService.updatePluginsMetadataOnGcs(harnessAccount);
        log.info("Removed plugin metadata from GCS and triggered build pipeline");
      }
      if (appConfigEntity == null || isEmpty(appConfigEntity.getConfigs())) {
        createOrUpdateTimeStampEnvVariable(harnessAccount);
      }
      log.info("Deleted custom plugin config");
      return true;
    }));
  }

  private void validateIfAppConfigsHaveSameProxyEndpoints(
      Set<String> appConfigProxyEndpoints, String accountIdentifier, String configId) {
    List<AppConfigEntity> appConfigEntities = appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
        accountIdentifier, ConfigType.PLUGIN, true);
    appConfigEntities.forEach(appConfigEntity -> {
      if (!appConfigEntity.getConfigId().equals(configId) && appConfigEntity.getConfigs() != null) {
        Set<String> appConfigProxyEndpointsInDB = getProxyEndpoints(appConfigEntity.getConfigs());
        Set<String> matchingProxyEndpoints =
            getMatchingProxyEndpoints(appConfigProxyEndpoints, appConfigProxyEndpointsInDB);
        if (!matchingProxyEndpoints.isEmpty()) {
          PluginInfoEntity currentPluginEntity = getPluginInfoEntity(configId, accountIdentifier);
          PluginInfoEntity conflictingPluginEntity =
              getPluginInfoEntity(appConfigEntity.getConfigId(), accountIdentifier);
          throw new InvalidRequestException(
              format(INVALID_PROXY_ENDPOINT_FOR_PLUGIN, matchingProxyEndpoints, conflictingPluginEntity.getName(),
                  appendErrorMessageBasedOnPluginType(currentPluginEntity, conflictingPluginEntity)));
        }
      }
    });
  }

  private Set<String> getProxyEndpoints(String config) {
    Set<String> endpoints = new HashSet<>();
    Map<String, Object> data = yaml.load(config);

    if (!isEmpty(data) && data.containsKey(PROXY)) {
      Map<String, Object> proxy = (Map<String, Object>) data.get(PROXY);
      if (!isEmpty(proxy) && proxy.containsKey(ENDPOINTS)) {
        Map<String, Object> endpointsObj = (Map<String, Object>) proxy.get(ENDPOINTS);
        endpoints.addAll(endpointsObj.keySet());
      }
    }
    return endpoints;
  }

  private Set<String> getMatchingProxyEndpoints(
      Set<String> appConfigProxyEndpoints, Set<String> appConfigProxyEndpointsInDB) {
    Set<String> intersection = new HashSet<>(appConfigProxyEndpoints);
    intersection.retainAll(appConfigProxyEndpointsInDB);
    return intersection;
  }

  private PluginInfoEntity getPluginInfoEntity(String configId, String accountIdentifier) {
    Optional<PluginInfoEntity> pluginInfoEntity = pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(
        configId, addGlobalAccountIdentifierAlong(accountIdentifier));
    if (pluginInfoEntity.isEmpty()) {
      throw new InvalidRequestException(
          format("Plugin Info not found for plugin identifier [%s] for account [%s]", configId, accountIdentifier));
    }
    return pluginInfoEntity.get();
  }

  private String appendErrorMessageBasedOnPluginType(
      PluginInfoEntity currentPluginEntity, PluginInfoEntity conflictingPluginEntity) {
    if (currentPluginEntity.getType().equals(conflictingPluginEntity.getType())) {
      return "in any one of the plugins";
    } else if (currentPluginEntity.getType().equals(PluginInfo.PluginTypeEnum.CUSTOM)) {
      return "in this plugin";
    } else {
      return "in the other plugin";
    }
  }

  private boolean isAuthRequired(String pluginId, String config) {
    switch (pluginId) {
      case Constants.GITHUB_ACTIONS_PLUGIN:
      case Constants.GITHUB_INSIGHTS_PLUGIN:
      case Constants.GITHUB_PULL_REQUESTS_PLUGIN:
      case Constants.GITHUB_CODESPACES:
      case Constants.ADR_PLUGIN:
        return true;
      case Constants.KUBERNETES_PLUGIN:
        return isAuthRequiredForK8s(config);
      default:
        return false;
    }
  }

  private String getAuthId(String pluginId) {
    switch (pluginId) {
      case Constants.GITHUB_ACTIONS_PLUGIN:
      case Constants.GITHUB_INSIGHTS_PLUGIN:
      case Constants.GITHUB_PULL_REQUESTS_PLUGIN:
      case Constants.GITHUB_CODESPACES:
      case Constants.ADR_PLUGIN:
        return Constants.GITHUB_AUTH;
      case Constants.KUBERNETES_PLUGIN:
        return Constants.GOOGLE_AUTH;
      default:
        return null;
    }
  }

  private String getAuthName(String pluginId) {
    switch (pluginId) {
      case Constants.GITHUB_ACTIONS_PLUGIN:
      case Constants.GITHUB_INSIGHTS_PLUGIN:
      case Constants.GITHUB_PULL_REQUESTS_PLUGIN:
      case Constants.GITHUB_CODESPACES:
      case Constants.ADR_PLUGIN:
        return "GitHub";
      case Constants.KUBERNETES_PLUGIN:
        return "Google Cloud";
    }
    return null;
  }

  private boolean isAuthRequiredForK8s(String config) {
    JsonNode clusters = ConfigManagerUtils.getNodeByName(asJsonNode(config), "clusters");
    boolean isRequired = false;
    for (JsonNode cluster : clusters) {
      if (cluster.get("authProvider").asText().equals("google")) {
        isRequired = true;
        break;
      }
    }
    return isRequired;
  }

  private boolean isAuthConfigured(String accountId, String authId) {
    Optional<AppConfigEntity> appConfig =
        appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(accountId, authId, ConfigType.AUTH);
    return appConfig.isPresent();
  }

  @Override
  public void mergeAndUpdateConfigInNamespace(String accountIdentifier, String oldMergedAppConfig) {
    /* oldMergedAppConfig can never be null if no config is in enabled state for account as we merge
     on top of baseappconfig.yaml(config) file */

    String mergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);

    if (!oldMergedAppConfig.equals(mergedAppConfig)) {
      if (dynamicConfigResolution) {
        Map<String, byte[]> secretData = new HashMap<>();
        secretData.put(LAST_UPDATED_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes());
        log.info("Triggering pod restart as app-config.yaml has changed for "
                + "account {}",
            accountIdentifier);
        k8sClient.updateSecretData(accountIdentifier,
            namespaceService.getNamespaceForAccountIdentifier(accountIdentifier).getNamespace(), BACKSTAGE_SECRET,
            secretData);
      } else {
        updateConfigMap(accountIdentifier, mergedAppConfig, CONFIG_NAME);
      }
    }
  }

  @Override
  public MergedPluginConfigs mergeEnabledPluginConfigsForAccount(String accountIdentifier) {
    MergedPluginConfigs mergedPluginConfigs = new MergedPluginConfigs();
    List<String> allEnabledPluginConfigs = getAllEnabledPluginConfigs(accountIdentifier);
    boolean isAllEnabledPluginsWithNoConfig = allEnabledPluginConfigs.stream().allMatch(config -> config == null);
    if (allEnabledPluginConfigs.isEmpty() || isAllEnabledPluginsWithNoConfig) {
      log.info(String.format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
      return mergedPluginConfigs;
    }
    allEnabledPluginConfigs = allEnabledPluginConfigs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    Iterator<String> itr = allEnabledPluginConfigs.iterator();
    String appConfig = itr.next();
    itr.remove();
    Map<String, Object> base = yaml.load(appConfig);
    while (itr.hasNext()) {
      String config = itr.next();
      if (config != null) {
        Map<String, Object> additional = yaml.load(config);
        Map<String, Object> merged = merge(base, additional);
        appConfig = yaml.dump(merged);
        base = yaml.load(appConfig);
        itr.remove();
      }
    }

    // fetching the env variables and corresponding secret identifier used while enabling the plugin
    List<String> enabledPluginIdsForAccount = getAllEnabledPluginIds(accountIdentifier);
    List<String> envVariablesForEnabledPlugins =
        getAllEnvVariablesForMultiplePluginIds(accountIdentifier, enabledPluginIdsForAccount);
    List<BackstageEnvSecretVariable> envVariableAndSecretList =
        backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
            accountIdentifier, envVariablesForEnabledPlugins);

    List<ProxyHostDetail> proxyHostDetailForEnabledPlugins =
        pluginsProxyInfoService.getProxyHostDetailsForMultiplePluginIds(accountIdentifier, enabledPluginIdsForAccount);

    return mergedPluginConfigs.config(appConfig)
        .envVariables(envVariableAndSecretList)
        .proxy(proxyHostDetailForEnabledPlugins);
  }

  @Override
  public List<AppConfigEntity> deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo() {
    return appConfigRepository.deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(baseTimeStamp);
  }

  private String mergeAppConfigs(String accountIdentifier, List<String> configs) {
    String appConfig = readFileFromClassPath(BASE_APP_CONFIG_PATH);
    appConfig = idpCommonService.getConfigWithEnvSpecificValuesReplaced(appConfig);
    Map<String, Object> base = yaml.load(appConfig);
    base = addSubdomainOriginToAuthExperimentalExtraAllowedOrigins(accountIdentifier, base);
    base = addAppAndBackendBaseUrlIfVanityAccount(accountIdentifier, base);
    Iterator<String> itr = configs.iterator();
    while (itr.hasNext()) {
      String config = itr.next();
      if (config != null) {
        Map<String, Object> additional = yaml.load(config);
        Map<String, Object> merged = merge(base, additional);
        appConfig = yaml.dump(merged);
        base = yaml.load(appConfig);
        itr.remove();
      }
    }
    return appConfig;
  }

  @Override
  public String mergeAllAppConfigsForAccount(String accountIdentifier) {
    List<String> enabledPluginConfigs = getAllEnabledConfigs(accountIdentifier);
    return mergeAppConfigs(accountIdentifier, enabledPluginConfigs);
  }

  private List<String> getAllEnabledConfigs(String accountIdentifier) {
    List<AppConfigEntity> allEnabledConfigEntity =
        appConfigRepository.findAllByAccountIdentifierAndEnabled(accountIdentifier, true);
    if (allEnabledConfigEntity.isEmpty()) {
      log.info(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    allEnabledConfigEntity = allEnabledConfigEntity.stream()
                                 .sorted(Comparator.comparingLong(AppConfigEntity::getEnabledDisabledAt))
                                 .collect(Collectors.toList());
    return allEnabledConfigEntity.stream()
        .map(appConfigEntity
            -> appConfigEntity.getConfigId().equals(HARNESS_PROXY) ? addProxyPluginHeader(appConfigEntity.getConfigs())
                                                                   : appConfigEntity.getConfigs())
        .collect(Collectors.toList());
  }

  @Override
  public void updateConfigMap(String accountIdentifier, String appConfigYamlData, String configName) {
    Map<String, String> data = new HashMap<>();
    data.put(CONFIG_DATA_NAME, appConfigYamlData);
    String namespace = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier).getNamespace();
    k8sClient.updateConfigMapData(namespace, configName, data, true);
    log.info(
        "Config map successfully created/updated for account - {} in namespace - {}", accountIdentifier, namespace);
  }

  @Override
  public Boolean isPluginWithNoConfig(String accountIdentifier, String configId) {
    return appConfigRepository
               .findByAccountIdentifierAndConfigIdAndConfigType(accountIdentifier, configId, ConfigType.PLUGIN)
               .get()
               .getConfigs()
        == null;
  }

  public void validateSchemaForPlugin(String config, String configId) {
    String pluginSchema = ConfigManagerUtils.getPluginConfigSchema(configId);
    boolean defaultPlugin = true;

    // Not default plugin, hence get custom plugin schema
    if (pluginSchema == null) {
      defaultPlugin = false;
      pluginSchema = ConfigManagerUtils.getPluginConfigSchema(CUSTOM_PLUGIN);
    }

    Set<String> schemaValidationResponse = ConfigManagerUtils.isValidSchema(config, pluginSchema);
    if (pluginSchema != null
        && (defaultPlugin
            || config != null) // Validate default plugin. Validate custom plugin only if config string is present
        && !schemaValidationResponse.isEmpty()) {
      throw new InvalidRequestException(CommonUtils.getParsedMessageFromSetOfStrings(schemaValidationResponse));
    }
  }

  @Override
  public void createOrUpdateAppConfigForGitIntegrations(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO, String integrationConfigs, String connectorType) {
    try {
      saveAndMergeAppConfigForGitIntegrations(accountIdentifier, connectorInfoDTO, integrationConfigs, connectorType);
    } catch (Exception e) {
      log.error("Error in saving and merging app config for git integration in account - {} for connector type - {} ",
          accountIdentifier, connectorInfoDTO.getConnectorType().toString(), e);
    }
  }

  @Override
  public void deleteAppConfigAndMergeConfigForAccount(
      String accountIdentifier, String configId, ConfigType configType) {
    transactionHelper.performTransaction(() -> {
      String oldMergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);
      appConfigRepository.deleteByAccountIdentifierAndConfigIdAndConfigType(accountIdentifier, configId, configType);
      mergeAndUpdateConfigInNamespace(accountIdentifier, oldMergedAppConfig);
      return null;
    });
  }

  @Override
  public List<AppConfigEntity> getAllConfigs() {
    return appConfigRepository.findAll();
  }

  @Override
  public AppConfigEntity updateAppConfig(AppConfigEntity appConfigEntity, ConfigType configType) {
    return appConfigRepository.updateConfig(appConfigEntity, configType);
  }

  @Override
  public void setupAndPropagateIntegrationConfig(String accountIdentifier, AppConfig appConfig) {
    saveUpdateAndMergeConfigForAccount(appConfig, accountIdentifier, ConfigType.INTEGRATION, false);
  }

  public void saveAndMergeAppConfigForGitIntegrations(String accountIdentifier, ConnectorInfoDTO connectorInfoDTO,
      String integrationConfigs, String connectorTypeAsString) throws Exception {
    ConnectorType connectorType = connectorInfoDTO.getConnectorType();
    String host = GitIntegrationUtils.getHostForConnector(connectorInfoDTO);
    log.info("Connector chosen in git integration is  - {} ", connectorTypeAsString);
    integrationConfigs = integrationConfigs.replace(TARGET_TO_REPLACE_IN_GIT_INTEGRATION_CONFIG, host);

    String schemaForIntegrations =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(connectorTypeAsString);
    if (!ConfigManagerUtils.isValidSchema(integrationConfigs, schemaForIntegrations).isEmpty()) {
      log.error(String.format(INVALID_SCHEMA_FOR_INTEGRATIONS, accountIdentifier));
    }

    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(connectorType.toString());
    appConfig.setConfigs(integrationConfigs);
    appConfig.setEnabled(true);

    saveUpdateAndMergeConfigForAccount(appConfig, accountIdentifier, ConfigType.INTEGRATION, false);

    log.info("Merging for git integration completed for connector - {}", connectorTypeAsString);
  }

  private List<AppConfigEntity> getAllEnabledPlugins(String accountIdentifier) {
    List<AppConfigEntity> allEnabledPluginConfigEntity =
        appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
            accountIdentifier, ConfigType.PLUGIN, true);
    if (allEnabledPluginConfigEntity.isEmpty()) {
      log.info(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    return allEnabledPluginConfigEntity;
  }

  private List<String> getAllEnabledPluginConfigs(String accountIdentifier) {
    return getAllEnabledPlugins(accountIdentifier)
        .stream()
        .map(entity -> entity.getConfigs())
        .collect(Collectors.toList());
  }

  private List<String> getAllEnabledPluginIds(String accountIdentifier) {
    return getAllEnabledPlugins(accountIdentifier)
        .stream()
        .map(entity -> entity.getConfigId())
        .collect(Collectors.toList());
  }

  private List<String> getAllEnvVariablesForMultiplePluginIds(String accountIdentifier, List<String> pluginIds) {
    return configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
        accountIdentifier, pluginIds);
  }

  private Boolean getEnabledFlagBasedOnConfigType(ConfigType configType) {
    if (configType.equals(ConfigType.PLUGIN)) {
      return false;
    }
    return true;
  }
  @VisibleForTesting
  void createOrUpdateTimeStampEnvVariable(String accountIdentifier) {
    BackstageEnvVariable timeStampEnvVariable = new BackstageEnvConfigVariable()
                                                    .value(String.valueOf(System.currentTimeMillis()))
                                                    .envName(Constants.LAST_UPDATED_TIMESTAMP)
                                                    .type(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvVariableService.createOrUpdate(Collections.singletonList(timeStampEnvVariable), accountIdentifier);
  }

  private String addProxyPluginHeader(String config) {
    JsonNode jsonNode = asJsonNode(config);
    Iterator<Map.Entry<String, JsonNode>> endpointsIterator =
        ConfigManagerUtils.getNodeByName(jsonNode, ENDPOINTS_PROPERTY).fields();
    while (endpointsIterator.hasNext()) {
      Map.Entry<String, JsonNode> endpoint = endpointsIterator.next();
      JsonNode headers = endpoint.getValue().get(HEADERS_PROPERTY);
      if (headers != null && headers.get(AUTHORIZATION_PROPERTY) != null) {
        log.info("Adding custom header internally for proxy plugin");
        ((ObjectNode) headers).put(IDP_PLUGIN_ORIGIN_HEADER, HARNESS_PROXY);
      }
    }
    return asYaml(jsonNode.toString());
  }

  public void handleKubernetesProxyPermissionIfApplicable(String accountIdentifier, String configId, boolean enabled) {
    if (!KUBERNETES_PLUGIN.equals(configId)) {
      return;
    }

    BackstagePermissions backstagePermissions =
        backstagePermissionsService.findByAccountIdentifier(accountIdentifier).orElse(new BackstagePermissions());

    List<String> permissions = backstagePermissions.getPermissions() != null
        ? new ArrayList<>(backstagePermissions.getPermissions())
        : new ArrayList<>();

    if (enabled) {
      if (!permissions.contains(KUBERNETES_PROXY_PERMISSION)) {
        permissions.add(KUBERNETES_PROXY_PERMISSION);
        backstagePermissions.setPermissions(permissions);
        backstagePermissionsService.updatePermissions(backstagePermissions, accountIdentifier);
      }
    } else {
      if (permissions.contains(KUBERNETES_PROXY_PERMISSION)) {
        permissions.remove(KUBERNETES_PROXY_PERMISSION);
        backstagePermissions.setPermissions(permissions);
        backstagePermissionsService.updatePermissions(backstagePermissions, accountIdentifier);
      }
    }
  }

  private Map<String, Object> addSubdomainOriginToAuthExperimentalExtraAllowedOrigins(
      String accountIdentifier, Map<String, Object> base) {
    try {
      AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
      if (!isEmpty(accountDTO.getSubdomainURL())) {
        String subdomainUrl = accountDTO.getSubdomainURL();
        String authExperimentalExtraAllowedOriginsYaml =
            readFileFromClassPath(AUTH_EXPERIMENTAL_EXTRA_ALLOWED_ORIGINS_YAML);
        Map<String, Object> authExperimentalExtraAllowedOrigins = yaml.load(authExperimentalExtraAllowedOriginsYaml);
        Map<String, Object> auth =
            (Map<String, Object>) authExperimentalExtraAllowedOrigins.computeIfAbsent("auth", k -> new HashMap<>());
        List<String> experimentalExtraAllowedOrigins =
            (List<String>) auth.computeIfAbsent("experimentalExtraAllowedOrigins", k -> new ArrayList<>());
        experimentalExtraAllowedOrigins =
            Stream.concat(experimentalExtraAllowedOrigins.stream(), Stream.of(subdomainUrl))
                .distinct()
                .collect(Collectors.toList());
        auth.put("experimentalExtraAllowedOrigins", experimentalExtraAllowedOrigins);
        base = merge(base, authExperimentalExtraAllowedOrigins);
      }
    } catch (Exception ex) {
      log.error(
          "Exception while trying to add subdomain origin to auth.experimentalExtraAllowedOrigins. Exception = {}",
          ex.getMessage(), ex);
    }
    return base;
  }

  private Map<String, Object> addAppAndBackendBaseUrlIfVanityAccount(
      String accountIdentifier, Map<String, Object> base) {
    try {
      AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
      String subdomainUrl = accountDTO.getSubdomainURL();

      if (isEmpty(subdomainUrl)) {
        return base;
      }

      if (!subdomainUrl.startsWith("http://") && !subdomainUrl.startsWith("https://")) {
        subdomainUrl = "https://" + subdomainUrl;
      }
      if (subdomainUrl.endsWith("/")) {
        subdomainUrl = subdomainUrl.substring(0, subdomainUrl.length() - 1);
      }

      Map<String, Object> baseUrlConfig = new HashMap<>();

      Map<String, Object> appNode = new HashMap<>();
      appNode.put("baseUrl", format("%s/ng/account/%s/module/idp", subdomainUrl, accountIdentifier));

      Map<String, Object> backendNode = new HashMap<>();
      if (!env.equals(LOCAL_ENV) && backstageClientConfig.getBaseUrl().equals(BACKSTAGE_BASE_URL_LOCAL_VALUE)) {
        backendNode.put("baseUrl", format("%s/%s/idp", subdomainUrl, accountIdentifier));
      } else {
        backendNode.put("baseUrl", format("%s/idp/%s/idp", subdomainUrl, accountIdentifier));
      }

      baseUrlConfig.put("app", appNode);
      baseUrlConfig.put("backend", backendNode);

      base = merge(base, baseUrlConfig);
    } catch (Exception ex) {
      log.error("Error while trying to add app.baseUrl and backend.baseUrl for vanity account {}. Exception = {}",
          accountIdentifier, ex.getMessage(), ex);
    }
    return base;
  }
}
