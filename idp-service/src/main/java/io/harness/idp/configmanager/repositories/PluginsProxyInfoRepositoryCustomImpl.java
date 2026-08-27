/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.repositories;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class PluginsProxyInfoRepositoryCustomImpl implements PluginsProxyInfoRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public List<PluginsProxyInfoEntity> findAllByAccountIdentifierAndPluginIds(
      String accountIdentifier, List<String> pluginIds) {
    Criteria criteria = Criteria.where(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.pluginId)
                            .in(pluginIds);
    Query query = new Query(criteria);
    return mongoTemplate.find(query, PluginsProxyInfoEntity.class);
  }

  @Override
  public PluginsProxyInfoEntity updatePluginProxyInfo(ProxyHostDetail proxyHostDetail, String accountIdentifier) {
    Criteria criteria = Criteria.where(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.id)
                            .is(proxyHostDetail.getIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.host, proxyHostDetail.getHost());
    update.set(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.proxy, proxyHostDetail.isProxy());
    update.set(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.delegateSelectors, proxyHostDetail.getSelectors());
    update.set(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.healthCheckPath, proxyHostDetail.getHealthCheckPath());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, PluginsProxyInfoEntity.class);
  }
}
