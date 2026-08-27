/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.spec.server.idp.v1.model.PluginInfo.PluginTypeEnum.CUSTOM;
import static io.harness.spec.server.idp.v1.model.PluginInfo.PluginTypeEnum.DEFAULT;
import static io.harness.spec.server.idp.v1.model.PluginInfo.PluginTypeEnum.MARKETPLACE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity.CustomPluginInfoEntityKeys;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity.DefaultPluginInfoEntityKeys;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity.PluginInfoEntityKeys;

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
public class PluginInfoRepositoryCustomImpl implements PluginInfoRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public PluginInfoEntity saveOrUpdate(PluginInfoEntity pluginInfoEntity) {
    Criteria criteria = getCriteria(pluginInfoEntity.getIdentifier(), pluginInfoEntity.getAccountIdentifier());
    PluginInfoEntity entity = findByIdentifier(criteria);
    if (entity == null) {
      return mongoTemplate.save(pluginInfoEntity);
    }
    return update(criteria, pluginInfoEntity);
  }

  @Override
  public PluginInfoEntity update(String pluginIdentifier, String accountIdentifier, PluginInfoEntity pluginInfoEntity) {
    Criteria criteria = getCriteria(pluginIdentifier, accountIdentifier);
    return update(criteria, pluginInfoEntity);
  }

  @Override
  public List<PluginInfoEntity> findAllActivePlugins(String accountIdentifier, Boolean isDeleted) {
    Criteria criteria = getAllActivePluginsCriteria(accountIdentifier, isDeleted);
    return mongoTemplate.find(new Query(criteria), PluginInfoEntity.class);
  }

  private Criteria getAllActivePluginsCriteria(String accountIdentifier, Boolean isDeleted) {
    Criteria defaultPluginsCriteria = Criteria.where(PluginInfoEntityKeys.accountIdentifier)
                                          .is(GLOBAL_ACCOUNT_ID)
                                          .and(DefaultPluginInfoEntityKeys.isDeleted)
                                          .is(isDeleted)
                                          .and(PluginInfoEntityKeys.type)
                                          .is(DEFAULT);
    Criteria customPluginsCriteria = Criteria.where(PluginInfoEntityKeys.accountIdentifier)
                                         .is(accountIdentifier)
                                         .and(PluginInfoEntityKeys.type)
                                         .is(CUSTOM);

    Criteria marketPlacePluginsCriteria = Criteria.where(PluginInfoEntityKeys.accountIdentifier)
                                              .is(GLOBAL_ACCOUNT_ID)
                                              .and(PluginInfoEntityKeys.type)
                                              .is(MARKETPLACE);
    return new Criteria().orOperator(defaultPluginsCriteria, customPluginsCriteria, marketPlacePluginsCriteria);
  }

  private Criteria getCriteria(String pluginIdentifier, String accountIdentifier) {
    return Criteria.where(PluginInfoEntityKeys.identifier)
        .is(pluginIdentifier)
        .and(PluginInfoEntityKeys.accountIdentifier)
        .is(accountIdentifier);
  }

  private PluginInfoEntity update(Criteria criteria, PluginInfoEntity pluginInfoEntity) {
    Query query = new Query(criteria);
    Update update = buildUpdateQuery(pluginInfoEntity);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, PluginInfoEntity.class);
  }

  private PluginInfoEntity findByIdentifier(Criteria criteria) {
    return mongoTemplate.findOne(Query.query(criteria), PluginInfoEntity.class);
  }

  private Update buildUpdateQuery(PluginInfoEntity pluginInfoEntity) {
    Update update = new Update();
    update.set(PluginInfoEntityKeys.name, pluginInfoEntity.getName());
    update.set(PluginInfoEntityKeys.type, pluginInfoEntity.getType());
    update.set(PluginInfoEntityKeys.description, pluginInfoEntity.getDescription());
    update.set(PluginInfoEntityKeys.creator, pluginInfoEntity.getCreator());
    update.set(PluginInfoEntityKeys.category, pluginInfoEntity.getCategory());
    update.set(PluginInfoEntityKeys.source, pluginInfoEntity.getSource());
    update.set(PluginInfoEntityKeys.iconUrl, pluginInfoEntity.getIconUrl());
    update.set(PluginInfoEntityKeys.documentation, pluginInfoEntity.getDocumentation());
    update.set(PluginInfoEntityKeys.exports, pluginInfoEntity.getExports());
    update.set(PluginInfoEntityKeys.config, pluginInfoEntity.getConfig());
    update.set(PluginInfoEntityKeys.envVariables, pluginInfoEntity.getEnvVariables());

    if (pluginInfoEntity.getImages() != null) {
      update.set(PluginInfoEntityKeys.images, pluginInfoEntity.getImages());
    }

    if (CUSTOM.equals(pluginInfoEntity.getType())) {
      CustomPluginInfoEntity customPluginInfoEntity = ((CustomPluginInfoEntity) pluginInfoEntity);
      update.set(CustomPluginInfoEntityKeys.artifact, customPluginInfoEntity.getArtifact());
      update.set(CustomPluginInfoEntityKeys.packageName, customPluginInfoEntity.getPackageName());
      update.set(CustomPluginInfoEntityKeys.statusApiUrl, customPluginInfoEntity.getStatusApiUrl());
    } else if (DEFAULT.equals(pluginInfoEntity.getType())) {
      DefaultPluginInfoEntity defaultPluginInfoEntity = (DefaultPluginInfoEntity) pluginInfoEntity;
      update.set(DefaultPluginInfoEntityKeys.core, defaultPluginInfoEntity.isCore());
      update.set(DefaultPluginInfoEntityKeys.isDeleted, defaultPluginInfoEntity.isDeleted());
    }

    return update;
  }
}
