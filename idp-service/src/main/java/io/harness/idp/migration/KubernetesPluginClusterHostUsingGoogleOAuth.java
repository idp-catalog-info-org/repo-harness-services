/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.Constants.KUBERNETES_PLUGIN;
import static io.harness.idp.configmanager.service.PluginsProxyInfoServiceImpl.GOOGLE_APIS_HOST;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.configmanager.service.PluginsProxyInfoServiceImpl;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class KubernetesPluginClusterHostUsingGoogleOAuth implements NGMigration {
  @Inject PluginsProxyInfoRepository pluginsProxyInfoRepository;
  @Inject PluginsProxyInfoServiceImpl pluginsProxyInfoService;
  @Inject ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Inject DelegateSelectorsCache delegateSelectorsCache;

  @Override
  public void migrate() {
    log.info("Starting the migration for setting www.googleapis.com host in HOST_PROXY_MAP & DelegateSelectorsCache if "
        + "kubernetes plugin cluster host is using google oauth.");

    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities =
        pluginsProxyInfoRepository.findAllByPluginId(KUBERNETES_PLUGIN);
    log.info("Found {} accounts using kubernetes plugin", pluginsProxyInfoEntities.size());
    pluginsProxyInfoEntities.forEach(pluginsProxyInfoEntity -> {
      String accountIdentifier = pluginsProxyInfoEntity.getAccountIdentifier();
      String clusterHost = pluginsProxyInfoEntity.getHost();
      if (pluginsProxyInfoService.isKubernetesPluginClusterHostUsingGoogleOAuth(
              pluginsProxyInfoEntity.getAccountIdentifier(), clusterHost)) {
        log.info("Account {} is using kubernetes plugin with cluster host = {} which uses google oauth",
            accountIdentifier, clusterHost);
        JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
        hostProxyMap.put(GOOGLE_APIS_HOST, true);
        proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
        delegateSelectorsCache.put(
            accountIdentifier, GOOGLE_APIS_HOST, new HashSet<>(pluginsProxyInfoEntity.getDelegateSelectors()));
        log.info("Successfully set www.googleapis.com host in HOST_PROXY_MAP & DelegateSelectorsCache for account = {} "
                + "kubernetes plugin cluster host = {}",
            accountIdentifier, clusterHost);
      }
    });

    log.info("Completed the migration for setting www.googleapis.com host in HOST_PROXY_MAP & DelegateSelectorsCache "
        + "if kubernetes plugin cluster host is using google oauth.");
  }
}
