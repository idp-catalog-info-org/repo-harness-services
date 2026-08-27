/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;

import static io.harness.idp.common.DateUtils.getPreviousDay24HourTimeFrame;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ScoreRepositoryCustomImpl implements ScoreRepositoryCustom {
  private MongoTemplate mongoTemplate;
  @Override
  public AggregationResults<ScoreEntityByScorecardIdentifier> getAllLatestScoresByScorecardsForAnEntity(
      String accountIdentifier, String entityIdentifier, boolean idpV2Enabled) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);
    if (idpV2Enabled) {
      criteria.andOperator(Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier).is(entityIdentifier));
    } else {
      criteria.andOperator(Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier)
                               .regex("^" + entityIdentifier.replaceAll("[^a-zA-Z0-9]", "\\\\$0") + "$", "i"));
    }

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(Constants.ID_KEY)
                                                  .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                                  .andExpression(Constants.SCORE_ENTITY_KEY)
                                                  .as(Constants.SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .push(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);

    AggregationResults<ScoreEntityByScorecardIdentifier> result =
        mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScoreEntityByScorecardIdentifier.class);
    return result;
  }

  @Override
  public AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> getAllLatestScoresByScorecardsForEntities(
      String accountIdentifier, List<String> entityIdentifiers, boolean idpV2Enabled) {
    return getAllLatestScoresByScorecardsForEntities(accountIdentifier, entityIdentifiers, idpV2Enabled, 0L);
  }

  @Override
  public AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> getAllLatestScoresByScorecardsForEntities(
      String accountIdentifier, List<String> entityIdentifiers, boolean idpV2Enabled, long computedAfter) {
    Criteria criteria;

    if (idpV2Enabled) {
      criteria =
          new Criteria().andOperator(Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier),
              Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier).in(entityIdentifiers));
    } else {
      List<Criteria> entityIdentifierRegexCriteria =
          entityIdentifiers.stream()
              .map(identifier -> {
                String escapedIdentifier = identifier.replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
                return Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier).regex("^" + escapedIdentifier + "$", "i");
              })
              .toList();

      if (entityIdentifierRegexCriteria.isEmpty()) {
        criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);
      } else {
        criteria =
            new Criteria().andOperator(Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier),
                new Criteria().orOperator(entityIdentifierRegexCriteria.toArray(new Criteria[0])));
      }
    }

    // Bound the scanned history so the in-memory sort stays small; the latest doc for an actively-scored
    // entity is always within one compute interval, so this does not drop currently-scored entities.
    if (computedAfter > 0) {
      criteria = new Criteria().andOperator(
          criteria, Criteria.where(ScoreEntity.ScoreKeys.lastComputedTimestamp).gte(computedAfter));
    }

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .and("_id." + ScoreEntity.ScoreKeys.scorecardIdentifier)
                                                  .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                                  .and("_id." + ScoreEntity.ScoreKeys.entityIdentifier)
                                                  .as(ScoreEntity.ScoreKeys.entityIdentifier)
                                                  .andExpression(Constants.SCORE_ENTITY_KEY)
                                                  .as(Constants.SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.scorecardIdentifier, ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);

    return mongoTemplate.aggregate(
        aggregation, Constants.SCORE_COLLECTION_NAME, ScoreEntityByScorecardIdentifierEntityIdentifier.class);
  }

  @Override
  public List<ScoresByScorecardIdentifier> getAllScoresByAccountIdentifierAndScorecardIdentifierPerDay(
      String accountIdentifier, String scorecardIdentifier) {
    AggregationOperation match = Aggregation.match(Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                                                       .is(accountIdentifier)
                                                       .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                                       .is(scorecardIdentifier));

    AggregationOperation addFieldsDate =
        Aggregation.addFields()
            .addField("date")
            .withValue(DateOperators.DateToString.dateOf(ConvertOperators.ToDate.toDate("$lastComputedTimestamp"))
                           .toString("%Y-%m-%d"))
            .build();

    AggregationOperation sort =
        Aggregation.sort(Sort.by(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp));

    AggregationOperation groupByEntity =
        Aggregation
            .group(Fields.fields(ScoreEntity.ScoreKeys.accountIdentifier, ScoreEntity.ScoreKeys.scorecardIdentifier,
                ScoreEntity.ScoreKeys.entityIdentifier, Constants.DATE))
            .first(ScoreEntity.ScoreKeys.score)
            .as(ScoreEntity.ScoreKeys.score)
            .first(ScoreEntity.ScoreKeys.lastComputedTimestamp)
            .as(ScoreEntity.ScoreKeys.lastComputedTimestamp);

    AggregationOperation groupByDate = Aggregation
                                           .group(Fields.fields(ScoreEntity.ScoreKeys.accountIdentifier,
                                               ScoreEntity.ScoreKeys.scorecardIdentifier, Constants.DATE))
                                           .push(ScoreEntity.ScoreKeys.score)
                                           .as(Constants.SCORES)
                                           .first(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                                           .as(ScoreEntity.ScoreKeys.lastComputedTimestamp);

    ProjectionOperation project = Aggregation.project()
                                      .and(ScoreEntity.ScoreKeys.accountIdentifier)
                                      .as(ScoreEntity.ScoreKeys.accountIdentifier)
                                      .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                      .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                      .and(Constants.DATE)
                                      .as(Constants.DATE)
                                      .and(Constants.SCORES)
                                      .as(Constants.SCORES)
                                      .and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                                      .as(ScoreEntity.ScoreKeys.lastComputedTimestamp);

    Aggregation aggregation =
        Aggregation.newAggregation(match, addFieldsDate, sort, groupByEntity, groupByDate, project);

    AggregationResults<ScoresByScorecardIdentifier> results =
        mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScoresByScorecardIdentifier.class);
    return results.getMappedResults();
  }

  @Override
  public ScoreEntity getLatestComputedScoreForEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scoreCardIdentifier, boolean idpV2Enabled) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);
    if (idpV2Enabled) {
      criteria.andOperator(Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier).is(entityIdentifier));
    } else {
      criteria.andOperator(Criteria.where(ScoreEntity.ScoreKeys.entityIdentifier)
                               .regex("^" + entityIdentifier.replaceAll("[^a-zA-Z0-9]", "\\\\$0") + "$", "i"));
    }
    criteria.and(ScoreEntity.ScoreKeys.scorecardIdentifier).is(scoreCardIdentifier);
    Query query =
        new Query(criteria).with(Sort.by(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp)).limit(1);
    return mongoTemplate.findOne(query, ScoreEntity.class);
  }

  @Override
  public List<ScoreEntityByEntityIdentifier> getLatestScoresForScorecard(
      String accountIdentifier, String scorecardIdentifier) {
    Pair<Long, Long> previousDay24HourTimeFrame = getPreviousDay24HourTimeFrame();
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .is(scorecardIdentifier)
                            .and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                            .gt(previousDay24HourTimeFrame.getLeft())
                            .lt(previousDay24HourTimeFrame.getRight());

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(Constants.ID_KEY)
                                                  .as(ScoreEntity.ScoreKeys.entityIdentifier)
                                                  .andExpression(Constants.SCORE_ENTITY_KEY)
                                                  .as(Constants.SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.entityIdentifier)
            .push(ScoreEntity.ScoreKeys.entityIdentifier)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);
    return mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScoreEntityByEntityIdentifier.class)
        .getMappedResults();
  }

  @Override
  public List<ScoreEntityByEntityIdentifier> getLatestScorePerEntityForScorecard(
      String accountIdentifier, String scorecardIdentifier) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .is(scorecardIdentifier);

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(Constants.ID_KEY)
                                                  .as(ScoreEntity.ScoreKeys.entityIdentifier)
                                                  .andExpression(Constants.SCORE_ENTITY_KEY)
                                                  .as(Constants.SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.entityIdentifier)
            .push(ScoreEntity.ScoreKeys.entityIdentifier)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);
    return mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScoreEntityByEntityIdentifier.class)
        .getMappedResults();
  }

  @Override
  public UpdateResult updateCheckIdentifier(ScoreEntity score, List<CheckStatus> checkStatuses) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(score.getAccountIdentifier())
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .is(score.getScorecardIdentifier())
                            .and(ScoreEntity.ScoreKeys.entityIdentifier)
                            .is(score.getEntityIdentifier())
                            .and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                            .is(score.getLastComputedTimestamp());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScoreEntity.ScoreKeys.checkStatus, checkStatuses);
    return mongoTemplate.updateFirst(query, update, ScoreEntity.class);
  }

  @Override
  public UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.entityIdentifier)
                            .is(entityIdentifier);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScoreEntity.ScoreKeys.entityIdentifier, entityUid);
    return mongoTemplate.updateMulti(query, update, ScoreEntity.class);
  }

  @Override
  public List<String> findUniqueEntityIdentifiers(String accountIdentifier) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);
    Query query = new Query(criteria);
    return mongoTemplate.query(ScoreEntity.class)
        .distinct(ScoreEntity.ScoreKeys.entityIdentifier)
        .matching(query)
        .as(String.class)
        .all();
  }
}
