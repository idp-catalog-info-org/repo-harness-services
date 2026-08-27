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
import io.harness.idp.configmanager.entities.PluginRequestEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@HarnessRepo
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class PluginRequestRepositoryCustomImpl implements PluginRequestRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<PluginRequestEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<PluginRequestEntity> pluginRequestEntityList = mongoTemplate.find(query, PluginRequestEntity.class);
    return PageableExecutionUtils.getPage(pluginRequestEntityList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), PluginRequestEntity.class));
  }

  @Override
  public PluginRequestEntity update(PluginRequestEntity pluginRequestEntity) {
    Criteria criteria = getCriteria(pluginRequestEntity.getIdentifier(), pluginRequestEntity.getAccountIdentifier());
    Query query = new Query(criteria);
    Update update = buildUpdateQuery(pluginRequestEntity);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, PluginRequestEntity.class);
  }

  private Criteria getCriteria(String pluginIdentifier, String accountIdentifier) {
    return Criteria.where(PluginRequestEntity.PluginRequestKeys.identifier)
        .is(pluginIdentifier)
        .and(PluginRequestEntity.PluginRequestKeys.accountIdentifier)
        .is(accountIdentifier);
  }

  private Update buildUpdateQuery(PluginRequestEntity pluginRequestEntity) {
    Update update = new Update();
    update.set(PluginRequestEntity.PluginRequestKeys.name, pluginRequestEntity.getName());
    update.set(PluginRequestEntity.PluginRequestKeys.creator, pluginRequestEntity.getCreator());
    update.set(PluginRequestEntity.PluginRequestKeys.creator, pluginRequestEntity.getCreator());
    update.set(PluginRequestEntity.PluginRequestKeys.createdAt, pluginRequestEntity.getCreatedAt());
    update.set(PluginRequestEntity.PluginRequestKeys.createdBy, pluginRequestEntity.getCreatedBy());
    update.set(PluginRequestEntity.PluginRequestKeys.packageLink, pluginRequestEntity.getPackageLink());
    update.set(PluginRequestEntity.PluginRequestKeys.docLink, pluginRequestEntity.getDocLink());
    update.set(PluginRequestEntity.PluginRequestKeys.status, pluginRequestEntity.getStatus());
    update.set(PluginRequestEntity.PluginRequestKeys.lastUpdatedAt, pluginRequestEntity.getLastUpdatedAt());
    update.set(PluginRequestEntity.PluginRequestKeys.lastUpdatedBy, pluginRequestEntity.getLastUpdatedBy());
    return update;
  }
}
