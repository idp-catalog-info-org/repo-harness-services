/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.repositories;

import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity.ScorecardKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ScorecardRepositoryCustomImpl implements ScorecardRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public ScorecardEntity update(ScorecardEntity scorecardEntity) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(scorecardEntity.getAccountIdentifier())
                            .and(ScorecardKeys.identifier)
                            .is(scorecardEntity.getIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScorecardKeys.filter, scorecardEntity.getFilter());
    update.set(ScorecardKeys.description, scorecardEntity.getDescription());
    update.set(ScorecardKeys.checks, scorecardEntity.getChecks());
    update.set(ScorecardKeys.name, scorecardEntity.getName());
    update.set(ScorecardKeys.published, scorecardEntity.isPublished());
    update.set(ScorecardKeys.onDemand, scorecardEntity.isOnDemand());
    update.set(ScorecardKeys.weightageStrategy, scorecardEntity.getWeightageStrategy());
    update.set(ScorecardKeys.tierGroupIdentifier, scorecardEntity.getTierGroupIdentifier());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, ScorecardEntity.class);
  }

  @Override
  public UpdateResult updateScoreCounts(String accountIdentifier, String identifier, int componentCount,
      List<ScorecardEntity.TierComponentCount> tierComponentCounts, long scoreCountsComputedAt) {
    Criteria scoreCountsAreOlder =
        new Criteria().orOperator(Criteria.where(ScorecardKeys.scoreCountsComputedAt).exists(false),
            Criteria.where(ScorecardKeys.scoreCountsComputedAt).lte(scoreCountsComputedAt));
    Criteria criteria =
        new Criteria().andOperator(Criteria.where(ScorecardKeys.accountIdentifier).is(accountIdentifier),
            Criteria.where(ScorecardKeys.identifier).is(identifier), scoreCountsAreOlder);
    Update update = new Update()
                        .set(ScorecardKeys.componentCount, componentCount)
                        .set(ScorecardKeys.tierComponentCounts, tierComponentCounts)
                        .set(ScorecardKeys.scoreCountsComputedAt, scoreCountsComputedAt);
    return mongoTemplate.updateFirst(new Query(criteria), update, ScorecardEntity.class);
  }

  @Override
  public DeleteResult delete(String accountIdentifier, String identifier) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScorecardKeys.identifier)
                            .is(identifier);
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, ScorecardEntity.class);
  }

  @Override
  public List<ScorecardEntity> findByCheckIdentifierAndIsCustom(
      String accountIdentifier, String checkIdentifier, Boolean custom) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and("checks.identifier")
                            .is(checkIdentifier)
                            .and("checks.isCustom")
                            .is(custom);
    Query query = new Query(criteria);
    return mongoTemplate.find(query, ScorecardEntity.class);
  }

  private ScorecardEntity findByAccountIdAndIdentifier(ScorecardEntity scorecardEntity) {
    Criteria criteria = Criteria.where(ScorecardKeys.accountIdentifier)
                            .is(scorecardEntity.getAccountIdentifier())
                            .and(ScorecardKeys.identifier)
                            .is(scorecardEntity.getIdentifier());
    return mongoTemplate.findOne(Query.query(criteria), ScorecardEntity.class);
  }
}
