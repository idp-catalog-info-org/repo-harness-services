/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.repositories;

import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity.TierGroupKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class TierGroupRepositoryCustomImpl implements TierGroupRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public TierGroupEntity update(TierGroupEntity tierGroupEntity) {
    Criteria criteria = Criteria.where(TierGroupKeys.accountIdentifier)
                            .is(tierGroupEntity.getAccountIdentifier())
                            .and(TierGroupKeys.identifier)
                            .is(tierGroupEntity.getIdentifier())
                            .and(TierGroupKeys.isDeleted)
                            .is(false);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(TierGroupKeys.name, tierGroupEntity.getName());
    update.set(TierGroupKeys.description, tierGroupEntity.getDescription());
    update.set(TierGroupKeys.tiers, tierGroupEntity.getTiers());
    update.set(TierGroupKeys.lastUpdatedAt, System.currentTimeMillis());
    update.set(TierGroupKeys.lastUpdatedAt, System.currentTimeMillis());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, TierGroupEntity.class);
  }

  @Override
  public UpdateResult softDelete(String accountIdentifier, String identifier) {
    Criteria criteria = Criteria.where(TierGroupKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(TierGroupKeys.identifier)
                            .is(identifier)
                            .and(TierGroupKeys.isDeleted)
                            .is(false);
    Update update = new Update();
    update.set(TierGroupKeys.isDeleted, true);
    update.set(TierGroupKeys.deletedAt, System.currentTimeMillis());
    return mongoTemplate.updateFirst(new Query(criteria), update, TierGroupEntity.class);
  }
}
