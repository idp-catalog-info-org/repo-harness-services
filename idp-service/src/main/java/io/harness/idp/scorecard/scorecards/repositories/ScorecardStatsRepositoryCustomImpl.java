/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.repositories;

import static io.harness.idp.catalog.utils.CatalogUtils.getFullyQualifiedScopeRef;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity.ScorecardStatsKeys;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ScorecardStatsRepositoryCustomImpl implements ScorecardStatsRepositoryCustom {
  private MongoTemplate mongoTemplate;
  private static final String ID_KEY = "_id";
  private static final String SCORE_KEY = "score";
  private static final String COUNT_KEY = "count";
  private static final String PERCENTAGE_KEY = "percentage";
  private static final String SCORES_GREATER_THAN_74_KEY = "scoresGreaterThan74";
  private static final String SCORECARD_SCORE_ENTITY_KEY = "scorecardStatsEntity";
  private static final String SCORECARD_STATS_COLLECTION_NAME = "scorecardStats";

  @Override
  public ScorecardStatsEntity findOneOrConstructStats(ScoreEntity scoreEntity, Object backstageCatalog) {
    Criteria criteria = Criteria.where(ScorecardStatsKeys.accountIdentifier)
                            .is(scoreEntity.getAccountIdentifier())
                            .and(ScorecardStatsKeys.entityIdentifier)
                            .is(scoreEntity.getEntityIdentifier())
                            .and(ScorecardStatsKeys.scorecardIdentifier)
                            .is(scoreEntity.getScorecardIdentifier());
    ScorecardStatsEntity entity = mongoTemplate.findOne(Query.query(criteria), ScorecardStatsEntity.class);
    if (entity == null) {
      return ScorecardStatsEntity.builder()
          .accountIdentifier(scoreEntity.getAccountIdentifier())
          .entityIdentifier(scoreEntity.getEntityIdentifier())
          .scorecardIdentifier(scoreEntity.getScorecardIdentifier())
          .score(scoreEntity.getScore())
          .tierName(scoreEntity.getTierName())
          .tierGroupIdentifier(scoreEntity.getTierGroupIdentifier())
          .tierDescription(scoreEntity.getTierDescription())
          .tierIcon(scoreEntity.getTierIcon())
          .tierColour(scoreEntity.getTierColour())
          .metadata(buildMetadata(backstageCatalog))
          .createdAt(scoreEntity.getLastComputedTimestamp())
          .lastUpdatedAt(scoreEntity.getLastComputedTimestamp())
          .build();
    }
    entity.setScore(scoreEntity.getScore());
    entity.setTierName(scoreEntity.getTierName());
    entity.setTierGroupIdentifier(scoreEntity.getTierGroupIdentifier());
    entity.setTierDescription(scoreEntity.getTierDescription());
    entity.setTierIcon(scoreEntity.getTierIcon());
    entity.setTierColour(scoreEntity.getTierColour());
    entity.setMetadata(buildMetadata(backstageCatalog));
    entity.setLastUpdatedAt(scoreEntity.getLastComputedTimestamp());
    return entity;
  }

  @Override
  public CountAndPercentage computeScoresPercentageByScorecard(
      String accountIdentifier, String scorecardIdentifier, long milliseconds) {
    Criteria criteria = Criteria.where(ScorecardStatsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScorecardStatsKeys.scorecardIdentifier)
                            .is(scorecardIdentifier)
                            .and(ScorecardStatsKeys.lastUpdatedAt)
                            .gt(milliseconds);

    ProjectionOperation projectionOperation =
        Aggregation.project()
            .andExpression(ID_KEY)
            .as(ScorecardStatsKeys.scorecardIdentifier)
            .andExpression(COUNT_KEY)
            .as(COUNT_KEY)
            .and(ConditionalOperators.when(Criteria.where(COUNT_KEY).ne(0))
                     .then(ArithmeticOperators.valueOf(SCORES_GREATER_THAN_74_KEY).divideBy(COUNT_KEY))
                     .otherwise(0))
            .as(PERCENTAGE_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.group(ScorecardStatsKeys.scorecardIdentifier, ScorecardStatsKeys.entityIdentifier)
            .first(SCORE_KEY)
            .as(SCORE_KEY),
        Aggregation.group(ID_KEY + DOT_SEPARATOR + ScorecardStatsKeys.scorecardIdentifier)
            .count()
            .as(COUNT_KEY)
            .sum(ConditionalOperators.when(Criteria.where(SCORE_KEY).gt(74)).then(1).otherwise(0))
            .as(SCORES_GREATER_THAN_74_KEY),
        projectionOperation);

    AggregationResults<CountAndPercentage> results =
        mongoTemplate.aggregate(aggregation, SCORECARD_STATS_COLLECTION_NAME, CountAndPercentage.class);

    if (results.getMappedResults().isEmpty()) {
      return null;
    }
    return results.getMappedResults().get(0);
  }

  @Override
  public List<ScorecardIdentifierAndStats> findLastUpdatedByScorecardIdentifiers(
      String accountIdentifier, List<String> scorecardIdentifiers) {
    Criteria criteria = Criteria.where(ScorecardStatsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScorecardStatsKeys.scorecardIdentifier)
                            .in(scorecardIdentifiers);

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(ID_KEY)
                                                  .as(ScorecardStatsKeys.scorecardIdentifier)
                                                  .andExpression(SCORECARD_SCORE_ENTITY_KEY)
                                                  .as(SCORECARD_SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScorecardStatsKeys.lastUpdatedAt),
        Aggregation.group(ScorecardStatsKeys.scorecardIdentifier)
            .push(ScorecardStatsKeys.scorecardIdentifier)
            .as(ScorecardStatsKeys.scorecardIdentifier)
            .first(Aggregation.ROOT)
            .as(SCORECARD_SCORE_ENTITY_KEY),
        projectionOperation);
    return mongoTemplate.aggregate(aggregation, SCORECARD_STATS_COLLECTION_NAME, ScorecardIdentifierAndStats.class)
        .getMappedResults();
  }

  @Override
  public UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid) {
    Criteria criteria = Criteria.where(ScorecardStatsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScorecardStatsKeys.entityIdentifier)
                            .is(entityIdentifier);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScorecardStatsKeys.entityIdentifier, entityUid);
    return mongoTemplate.updateMulti(query, update, ScorecardStatsEntity.class);
  }

  @Override
  public List<String> findUniqueEntityIdentifiers(String accountIdentifier) {
    Criteria criteria = Criteria.where(ScorecardStatsKeys.accountIdentifier).is(accountIdentifier);
    Query query = new Query(criteria);
    return mongoTemplate.query(ScorecardStatsEntity.class)
        .distinct(ScorecardStatsKeys.entityIdentifier)
        .matching(query)
        .as(String.class)
        .all();
  }

  private StatsMetadata buildMetadata(Object catalog) {
    if (catalog instanceof CatalogEntity catalogEntity) {
      return StatsMetadata.builder()
          .kind(WORKFLOW_KIND.equals(catalogEntity.getKind()) ? TEMPLATE_KIND : catalogEntity.getKind())
          .namespace(getFullyQualifiedScopeRef(
              catalogEntity.getScope(), catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()))
          .name(catalogEntity.getIdentifier())
          .type(catalogEntity.getType())
          .owner(catalogEntity.getOwner())
          .build();
    }
    BackstageCatalogEntity backstageCatalog = (BackstageCatalogEntity) catalog;
    return StatsMetadata.builder()
        .kind(backstageCatalog.getKind())
        .namespace(BackstageCatalogEntity.getValue(
            backstageCatalog.getMetadata(), MetadataFieldConstants.NAMESPACE, String.class))
        .name(
            BackstageCatalogEntity.getValue(backstageCatalog.getMetadata(), MetadataFieldConstants.NAME, String.class))
        .type(BackstageCatalogEntityTypes.getEntityType(backstageCatalog))
        .owner(BackstageCatalogEntityTypes.getEntityOwner(backstageCatalog))
        .system(BackstageCatalogEntityTypes.getEntitySystem(backstageCatalog))
        .build();
  }
}
