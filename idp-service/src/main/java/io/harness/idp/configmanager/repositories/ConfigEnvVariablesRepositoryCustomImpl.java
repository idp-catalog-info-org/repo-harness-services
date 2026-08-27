/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.PluginConfigEnvVariablesEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@HarnessRepo
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ConfigEnvVariablesRepositoryCustomImpl implements ConfigEnvVariablesRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public List<PluginConfigEnvVariablesEntity> getAllEnvVariablesForMultiplePluginIds(
      String accountIdentifier, List<String> pluginIds) {
    Criteria criteria =
        Criteria.where(PluginConfigEnvVariablesEntity.PluginsConfigEnvVariablesEntityKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(PluginConfigEnvVariablesEntity.PluginsConfigEnvVariablesEntityKeys.pluginId)
            .in(pluginIds);
    Query query = new Query(criteria);
    return mongoTemplate.find(query, PluginConfigEnvVariablesEntity.class);
  }
}
