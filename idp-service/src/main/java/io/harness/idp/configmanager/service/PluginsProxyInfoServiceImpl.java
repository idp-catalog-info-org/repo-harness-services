/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.removeAccountFromIdentifier;
import static io.harness.idp.common.CommonUtils.urlObject;
import static io.harness.idp.common.Constants.ACCOUNT_SCOPED;
import static io.harness.idp.common.Constants.ATLASSIAN_AUTH;
import static io.harness.idp.common.Constants.AUTHORIZATION_PROPERTY;
import static io.harness.idp.common.Constants.ENDPOINTS_PROPERTY;
import static io.harness.idp.common.Constants.HARNESS_PROXY;
import static io.harness.idp.common.Constants.HEADERS_PROPERTY;
import static io.harness.idp.common.Constants.KUBERNETES_PLUGIN;
import static io.harness.idp.common.Constants.TARGET_PROPERTY;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asJsonNode;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.intfc.DelegateSelectable;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent;
import io.harness.idp.configmanager.mappers.PluginsProxyInfoMapper;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PluginsProxyInfoServiceImpl implements PluginsProxyInfoService {
  private PluginsProxyInfoRepository pluginsProxyInfoRepository;
  private DelegateSelectorsCache delegateSelectorsCache;
  private ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  SecretManagerClientService ngSecretService;
  ConnectorResourceClient connectorResourceClient;
  ConfigManagerService configManagerService;
  private TransactionTemplate transactionTemplate;
  private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private static final String ERROR_MESSAGE_FOR_PROXY =
      "Host - %s is already used in plugin - %s, please configure it from configurations page.";

  private static final String NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED =
      "No proxy hosts are associated with Plugin id - {} for account - {}";

  public static final String GOOGLE_APIS_HOST = "www.googleapis.com";
  public static final String ATLASSIAN_APIS_HOST = "auth.atlassian.com";

  @Inject
  public PluginsProxyInfoServiceImpl(PluginsProxyInfoRepository pluginsProxyInfoRepository,
      DelegateSelectorsCache delegateSelectorsCache, ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper,
      SecretManagerClientService ngSecretService, ConnectorResourceClient connectorResourceClient,
      ConfigManagerService configManagerService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.pluginsProxyInfoRepository = pluginsProxyInfoRepository;
    this.delegateSelectorsCache = delegateSelectorsCache;
    this.proxyEnvVariableServiceWrapper = proxyEnvVariableServiceWrapper;
    this.ngSecretService = ngSecretService;
    this.connectorResourceClient = connectorResourceClient;
    this.configManagerService = configManagerService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public List<ProxyHostDetail> insertProxyHostDetailsForPlugin(
      AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = getPluginProxyInfoEntities(appConfig, accountIdentifier);
    if (!ConfigType.PLUGIN.equals(configType) && !ConfigType.AUTH.equals(configType)) {
      return Collections.emptyList();
    }

    List<String> errorMessageForProxyDetails =
        getErrorMessageIfHostIsAlreadyInUseOrInvalid(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    // deleting old proxy host details
    List<PluginsProxyInfoEntity> existingPluginProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);

    if (!existingPluginProxies.isEmpty()) {
      removeHostsFromMapAndCache(accountIdentifier, existingPluginProxies, hostProxyMap);
      pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());
    }

    // add new proxy host details
    return insertNewlyCreatedHostProxy(accountIdentifier, pluginsProxyInfoEntities);
  }

  @Override
  public List<ProxyHostDetail> updateProxyHostDetailsForPlugin(
      AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    if (appConfig.getProxy().isEmpty()) {
      log.info(NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier);
    }

    List<String> errorMessageForProxyDetails =
        getErrorMessageIfHostIsAlreadyInUseOrInvalid(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    List<PluginsProxyInfoEntity> oldPluginProxyInfoEntities =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());

    Map<String, PluginsProxyInfoEntity> oldPluginProxyInfoEntityMap = oldPluginProxyInfoEntities.stream().collect(
        Collectors.toMap(PluginsProxyInfoEntity::getId, Function.identity()));

    List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate = getPluginProxyInfoEntities(appConfig, accountIdentifier);

    // newly added proxy host details
    List<PluginsProxyInfoEntity> newlyAddedProxyHostsDetails =
        proxyHostDetailsToUpdate.stream()
            .filter(proxyHostDetail -> proxyHostDetail.getId() == null)
            .collect(Collectors.toList());

    // removing the newly added proxy host from the list for the update case
    proxyHostDetailsToUpdate.removeAll(newlyAddedProxyHostsDetails);

    // delete all the older created proxy host not in use
    deleteOlderProxyHostNotInUse(accountIdentifier, oldPluginProxyInfoEntities, proxyHostDetailsToUpdate);

    List<ProxyHostDetail> returnList = new ArrayList<>();

    // insert newly created hosts
    returnList.addAll(insertNewlyCreatedHostProxy(accountIdentifier, newlyAddedProxyHostsDetails));

    // update case
    returnList.addAll(
        updateExistingProxyHosts(accountIdentifier, proxyHostDetailsToUpdate, oldPluginProxyInfoEntityMap));

    return returnList;
  }

  @Override
  public void deleteProxyHostDetailsForPlugin(String accountIdentifier, String pluginId) {
    List<PluginsProxyInfoEntity> existingPluginProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    if (!existingPluginProxies.isEmpty()) {
      Set<String> hostsToBeRemoved =
          existingPluginProxies.stream().map(PluginsProxyInfoEntity::getHost).collect(Collectors.toSet());
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
      proxyEnvVariableServiceWrapper.removeFromHostProxyEnvVariable(accountIdentifier, hostsToBeRemoved);
      pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    }
  }

  @Override
  public List<ProxyHostDetail> getProxyHostDetailsForMultiplePluginIds(
      String accountIdentifier, List<String> pluginIds) {
    List<PluginsProxyInfoEntity> pluginProxyHostDetailsForPlugins =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginIds(accountIdentifier, pluginIds);
    return getPluginProxyHostDetailsFromEntities(pluginProxyHostDetailsForPlugins);
  }

  @Override
  public List<ProxyHostDetail> updateProxyHostDetailsForHostValues(
      List<ProxyHostDetail> proxyHostDetails, String accountIdentifier) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = new ArrayList<>();

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      for (ProxyHostDetail proxyHostDetail : proxyHostDetails) {
        PluginsProxyInfoEntity oldPluginProxyInfoEntity =
            pluginsProxyInfoRepository.findByAccountIdentifierAndPluginIdAndHost(
                accountIdentifier, proxyHostDetail.getPluginId(), proxyHostDetail.getHost());
        PluginsProxyInfoEntity pluginsProxyInfoEntity =
            pluginsProxyInfoRepository.updatePluginProxyInfo(proxyHostDetail, accountIdentifier);
        if (pluginsProxyInfoEntity != null) {
          pluginsProxyInfoEntities.add(pluginsProxyInfoEntity);
          outboxService.save(
              new ProxyHostUpdateEvent(accountIdentifier, PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity),
                  PluginsProxyInfoMapper.toDto(oldPluginProxyInfoEntity)));
        }
      }
      return getPluginProxyHostDetailsFromEntities(pluginsProxyInfoEntities);
    }));
  }

  @Override
  public List<ProxyHostDetail> getProxyHostDetailsForPluginId(String accountIdentifier, String pluginId) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    return getPluginProxyHostDetailsFromEntities(pluginsProxyInfoEntities);
  }

  @VisibleForTesting
  List<String> getErrorMessageIfHostIsAlreadyInUseOrInvalid(String accountIdentifier, AppConfig appConfig) {
    List<ProxyHostDetail> proxyDetails = appConfig.getProxy();
    List<String> errorMessage = new ArrayList<>();
    for (ProxyHostDetail proxyHostDetail : proxyDetails) {
      PluginsProxyInfoEntity pluginsProxyInfoEntity =
          pluginsProxyInfoRepository.findByAccountIdentifierAndPluginIdAndHost(
              accountIdentifier, proxyHostDetail.getPluginId(), proxyHostDetail.getHost());
      if (pluginsProxyInfoEntity != null && !pluginsProxyInfoEntity.getPluginId().equals(appConfig.getConfigId())) {
        errorMessage.add(
            String.format(ERROR_MESSAGE_FOR_PROXY, proxyHostDetail.getHost(), pluginsProxyInfoEntity.getPluginId()));
      }
    }
    return errorMessage;
  }

  @VisibleForTesting
  List<PluginsProxyInfoEntity> getPluginProxyInfoEntities(AppConfig appConfig, String accountIdentifier) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = new ArrayList<>();
    if (isEmpty(appConfig.getProxy())) {
      setupProxyIfPluginUsesOtherSecretManagers(accountIdentifier, appConfig);
      if (isEmpty(appConfig.getProxy())) {
        return pluginsProxyInfoEntities;
      }
    }
    for (ProxyHostDetail proxyHostDetail : appConfig.getProxy()) {
      pluginsProxyInfoEntities.add(PluginsProxyInfoEntity.builder()
                                       .id(proxyHostDetail.getIdentifier())
                                       .accountIdentifier(accountIdentifier)
                                       .pluginId(appConfig.getConfigId())
                                       .createdAt(System.currentTimeMillis())
                                       .lastModifiedAt(System.currentTimeMillis())
                                       .host(proxyHostDetail.getHost())
                                       .proxy(proxyHostDetail.isProxy())
                                       .delegateSelectors(proxyHostDetail.getSelectors())
                                       .healthCheckPath(proxyHostDetail.getHealthCheckPath())
                                       .build());
    }
    return pluginsProxyInfoEntities;
  }

  private void setupProxyIfPluginUsesOtherSecretManagers(String accountIdentifier, AppConfig appConfig) {
    List<BackstageEnvSecretVariable> backstageEnvSecretVariables = appConfig.getEnvVariables();
    for (BackstageEnvSecretVariable backstageEnvSecretVariable : backstageEnvSecretVariables) {
      SecretResponseWrapper secretResponseWrapper = ngSecretService.getSecret(accountIdentifier, null, null,
          removeAccountFromIdentifier(backstageEnvSecretVariable.getHarnessSecretIdentifier()));
      String secretManagerIdentifier = getSecretManagerIdentifier(secretResponseWrapper);
      if (!secretManagerIdentifier.equals(ACCOUNT_SCOPED + HARNESS_SECRET_MANAGER_IDENTIFIER)) {
        List<String> delegateSelectors = getDelegateSelectors(accountIdentifier, secretManagerIdentifier);
        List<String> pluginHosts = pluginHosts(appConfig, backstageEnvSecretVariable.getEnvName());
        List<ProxyHostDetail> proxy = new ArrayList<>();
        for (String pluginHost : pluginHosts) {
          ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
          proxyHostDetail.setPluginId(appConfig.getConfigId());
          proxyHostDetail.setHost(pluginHost);
          proxyHostDetail.setProxy(true);
          proxyHostDetail.setSelectors(new ArrayList<>(delegateSelectors));
          proxy.add(proxyHostDetail);
        }
        appConfig.setProxy(proxy);
      }
    }
  }

  private String getSecretManagerIdentifier(SecretResponseWrapper secretResponseWrapper) {
    String secretManagerIdentifier = ACCOUNT_SCOPED + HARNESS_SECRET_MANAGER_IDENTIFIER;
    if (secretResponseWrapper.getSecret().getSpec() instanceof SecretTextSpecDTO) {
      secretManagerIdentifier =
          ((SecretTextSpecDTO) secretResponseWrapper.getSecret().getSpec()).getSecretManagerIdentifier();
    } else if (secretResponseWrapper.getSecret().getSpec() instanceof SecretFileSpecDTO) {
      secretManagerIdentifier =
          ((SecretFileSpecDTO) secretResponseWrapper.getSecret().getSpec()).getSecretManagerIdentifier();
    }
    return secretManagerIdentifier;
  }

  private List<String> getDelegateSelectors(String accountIdentifier, String secretManagerIdentifier) {
    ConnectorDTO connectorDTO =
        getResponse(connectorResourceClient.get(
                        removeAccountFromIdentifier(secretManagerIdentifier), accountIdentifier, null, null))
            .get();
    ConnectorConfigDTO connectorConfigDTO = connectorDTO.getConnectorInfo().getConnectorConfig();
    Set<String> delegateSelectors = new HashSet<>();
    if (connectorConfigDTO instanceof DelegateSelectable) {
      delegateSelectors = ((DelegateSelectable) connectorConfigDTO).getDelegateSelectors();
    }
    return new ArrayList<>(delegateSelectors);
  }

  private List<String> pluginHosts(AppConfig appConfig, String envName) {
    List<String> pluginHosts = new ArrayList<>();
    JsonNode jsonNode = asJsonNode(appConfig.getConfigs());
    switch (appConfig.getConfigId()) {
      case "google-auth":
        pluginHosts = List.of(GOOGLE_APIS_HOST);
        break;
      case ATLASSIAN_AUTH:
        pluginHosts = List.of(ATLASSIAN_APIS_HOST);
        break;
      case "adr", "datadog", "github-actions", "github-catalog-discovery", "github-codespaces", "github-insights",
          "github-pull-requests", "gitlab", "todo", "github-auth", "github-copilot":
        break;
      case "azure-devops":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "host").asText()).getHost());
        break;
      case "bugsnag", "circleci", "dynatrace", "firehydrant", "grafana", "jira", "opsgenie", "pager-duty", "rafay",
          "rootly", "snyk-security", "splunk-on-call", "new-relic", "jfrog-artifactory", "jfrog-artifactory-libs",
          "sysdig", "dx", "buildkite", "fme":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "target").asText()).getHost());
        break;
      case "confluence":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "wikiUrl").asText()).getHost());
        break;
      case "jenkins":
        JsonNode jenkinsInstances = ConfigManagerUtils.getNodeByName(jsonNode, "instances");
        for (JsonNode jenkinsInstance : jenkinsInstances) {
          pluginHosts.add(urlObject(jenkinsInstance.get("baseUrl").asText()).getHost());
        }
        break;
      case "argo-cd":
        JsonNode appLocatorMethods = ConfigManagerUtils.getNodeByName(jsonNode, "appLocatorMethods");
        for (JsonNode method : appLocatorMethods) {
          if (method.get("type").asText().equals("config")) {
            JsonNode instances = method.get("instances");
            for (JsonNode instance : instances) {
              String url = instance.get("url").asText();
              pluginHosts.add(urlObject(url).getHost());
            }
          }
        }
        break;
      case HARNESS_PROXY:
        Iterator<Map.Entry<String, JsonNode>> endpointsIterator =
            ConfigManagerUtils.getNodeByName(jsonNode, ENDPOINTS_PROPERTY).fields();
        while (endpointsIterator.hasNext()) {
          Map.Entry<String, JsonNode> endpoint = endpointsIterator.next();
          String target = endpoint.getValue().get(TARGET_PROPERTY).asText();
          JsonNode headers = endpoint.getValue().get(HEADERS_PROPERTY);
          if (headers != null && headers.get(AUTHORIZATION_PROPERTY) != null
              && headers.get(AUTHORIZATION_PROPERTY).asText().contains("${" + envName + "}")) {
            pluginHosts.add(urlObject(target).getHost());
          }
        }
        break;
      case "kubernetes":
        JsonNode clusters = ConfigManagerUtils.getNodeByName(jsonNode, "clusters");
        for (JsonNode cluster : clusters) {
          pluginHosts.add(urlObject(cluster.get("url").asText()).getHost());
        }
        break;
      case "lighthouse":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "baseUrl").asText()).getHost());
        break;
      case "sonarqube":
        JsonNode sonarqubeInstances = ConfigManagerUtils.getNodeByName(jsonNode, "instances");
        for (JsonNode sonarqubeInstance : sonarqubeInstances) {
          pluginHosts.add(urlObject(sonarqubeInstance.get("baseUrl").asText()).getHost());
        }
        break;
      case "wiz":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "wizAPIUrl").asText()).getHost());
        break;
      case "vee-code-kong":
        pluginHosts = List.of(urlObject(ConfigManagerUtils.getNodeByName(jsonNode, "apiBaseUrl").asText()).getHost());
      default:
        break;
    }
    return pluginHosts;
  }

  private List<ProxyHostDetail> getPluginProxyHostDetailsFromEntities(
      List<PluginsProxyInfoEntity> pluginsProxyInfoEntities) {
    List<ProxyHostDetail> returnList = new ArrayList<>();
    for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
      ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
      proxyHostDetail.setIdentifier(pluginsProxyInfoEntity.getId());
      proxyHostDetail.setPluginId(pluginsProxyInfoEntity.getPluginId());
      proxyHostDetail.setHost(pluginsProxyInfoEntity.getHost());
      proxyHostDetail.setProxy(pluginsProxyInfoEntity.getProxy());
      proxyHostDetail.setSelectors(pluginsProxyInfoEntity.getDelegateSelectors());
      proxyHostDetail.setHealthCheckPath(pluginsProxyInfoEntity.getHealthCheckPath());
      returnList.add(proxyHostDetail);
    }
    return returnList;
  }

  private void deleteOlderProxyHostNotInUse(String accountIdentifier,
      List<PluginsProxyInfoEntity> oldProxyHostDetailEntities, List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate) {
    // remove the proxy host details from oldProxyHostDetails that are present in newProxyHostDetails
    oldProxyHostDetailEntities.removeIf(proxyInfoEntity
        -> proxyHostDetailsToUpdate.stream().anyMatch(proxyHost -> proxyHost.getId().equals(proxyInfoEntity.getId())));

    // Deleting the older created proxy host details that are deleted from UI.
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (!oldProxyHostDetailEntities.isEmpty()) {
        log.info("Deleted proxy details - {}", oldProxyHostDetailEntities);
        JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
        JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

        removeHostsFromMapAndCache(accountIdentifier, oldProxyHostDetailEntities, hostProxyMap);

        List<String> proxyHostDetailsIdsToBeDeleted =
            oldProxyHostDetailEntities.stream().map(PluginsProxyInfoEntity::getId).collect(Collectors.toList());
        pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndIdIn(
            accountIdentifier, proxyHostDetailsIdsToBeDeleted);

        for (PluginsProxyInfoEntity oldProxyHostDetail : oldProxyHostDetailEntities) {
          outboxService.save(
              new ProxyHostDeleteEvent(accountIdentifier, PluginsProxyInfoMapper.toDto(oldProxyHostDetail)));
        }

        if (!originalHostProxyMap.similar(hostProxyMap)) {
          proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
        }
      }
      return true;
    }));
  }

  private List<ProxyHostDetail> insertNewlyCreatedHostProxy(
      String accountIdentifier, List<PluginsProxyInfoEntity> pluginsProxyInfoEntities) {
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      List<ProxyHostDetail> returnList = new ArrayList<>();

      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
        if (pluginsProxyInfoEntity.getProxy()) {
          putHostInMapAndCache(accountIdentifier, pluginsProxyInfoEntity, hostProxyMap);
        }
        ProxyHostDetail proxyHostDetail = PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity);
        outboxService.save(new ProxyHostCreateEvent(accountIdentifier, proxyHostDetail));
      }

      List<PluginsProxyInfoEntity> savedProxyHosts =
          (List<PluginsProxyInfoEntity>) pluginsProxyInfoRepository.saveAll(pluginsProxyInfoEntities);
      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : savedProxyHosts) {
        returnList.add(PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity));
      }

      if (!originalHostProxyMap.similar(hostProxyMap)) {
        proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
      }
      return returnList;
    }));
  }

  private List<ProxyHostDetail> updateExistingProxyHosts(String accountIdentifier,
      List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate,
      Map<String, PluginsProxyInfoEntity> oldPluginProxyInfoEntityMap) {
    List<ProxyHostDetail> returnList = new ArrayList<>();

    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      log.info("Updated proxy hosts  - {}", proxyHostDetailsToUpdate);

      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : proxyHostDetailsToUpdate) {
        String newEnvVariableIdentifier = pluginsProxyInfoEntity.getId();
        if (!pluginsProxyInfoEntity.getHost().equals(
                oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier).getHost())
            || !pluginsProxyInfoEntity.getDelegateSelectors().equals(
                oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier).getDelegateSelectors())) {
          removeHostsFromMapAndCache(accountIdentifier,
              Collections.singletonList(oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier)), hostProxyMap);
          putHostInMapAndCache(accountIdentifier, pluginsProxyInfoEntity, hostProxyMap);
          outboxService.save(
              new ProxyHostUpdateEvent(accountIdentifier, PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity),
                  PluginsProxyInfoMapper.toDto(oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier))));
        }
        returnList.add(PluginsProxyInfoMapper.toDto(pluginsProxyInfoRepository.updatePluginProxyInfo(
            PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity), accountIdentifier)));
      }

      if (!originalHostProxyMap.similar(hostProxyMap)) {
        proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
      }

      return true;
    }));

    return returnList;
  }

  private void removeHostsFromMapAndCache(
      String accountIdentifier, List<PluginsProxyInfoEntity> pluginsProxyInfoEntities, JSONObject hostProxyMap) {
    for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
      if (pluginsProxyInfoEntity.getPluginId().equals(KUBERNETES_PLUGIN)) {
        boolean isKubernetesClusterUsingGoogleAuth =
            isKubernetesPluginClusterHostUsingGoogleOAuth(accountIdentifier, pluginsProxyInfoEntity.getHost());
        if (isKubernetesClusterUsingGoogleAuth) {
          hostProxyMap.remove(GOOGLE_APIS_HOST);
          delegateSelectorsCache.remove(accountIdentifier, Collections.singleton(GOOGLE_APIS_HOST));
        }
      }
      hostProxyMap.remove(pluginsProxyInfoEntity.getHost());
      Set<String> hostsToBeRemoved =
          pluginsProxyInfoEntities.stream().map(PluginsProxyInfoEntity::getHost).collect(Collectors.toSet());
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
    }
  }

  private void putHostInMapAndCache(
      String accountIdentifier, PluginsProxyInfoEntity pluginsProxyInfoEntity, JSONObject hostProxyMap) {
    if (pluginsProxyInfoEntity.getPluginId().equals(KUBERNETES_PLUGIN)) {
      boolean isKubernetesClusterUsingGoogleAuth =
          isKubernetesPluginClusterHostUsingGoogleOAuth(accountIdentifier, pluginsProxyInfoEntity.getHost());
      if (isKubernetesClusterUsingGoogleAuth) {
        hostProxyMap.put(GOOGLE_APIS_HOST, true);
        delegateSelectorsCache.put(
            accountIdentifier, GOOGLE_APIS_HOST, new HashSet<>(pluginsProxyInfoEntity.getDelegateSelectors()));
      }
    }
    hostProxyMap.put(pluginsProxyInfoEntity.getHost(), true);
    delegateSelectorsCache.put(accountIdentifier, pluginsProxyInfoEntity.getHost(),
        new HashSet<>(pluginsProxyInfoEntity.getDelegateSelectors()));
  }

  public boolean isKubernetesPluginClusterHostUsingGoogleOAuth(String accountIdentifier, String host) {
    AppConfig appConfig = configManagerService.getAppConfig(accountIdentifier, KUBERNETES_PLUGIN, ConfigType.PLUGIN);
    if (appConfig != null) {
      JsonNode jsonNode = asJsonNode(appConfig.getConfigs());
      JsonNode clusters = ConfigManagerUtils.getNodeByName(jsonNode, "clusters");
      for (JsonNode cluster : clusters) {
        URL clusterUrlObject = urlObject(cluster.get("url").asText());
        if (clusterUrlObject.getHost().equals(host) && cluster.get("authProvider").asText().equals("google")) {
          return true;
        }
      }
    }
    return false;
  }
}
