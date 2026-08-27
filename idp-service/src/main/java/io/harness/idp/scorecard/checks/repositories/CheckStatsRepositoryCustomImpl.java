/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.repositories;

import static io.harness.idp.catalog.utils.CatalogUtils.getFullyQualifiedScopeRef;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity.CheckStatsKeys;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CheckStatsRepositoryCustomImpl implements CheckStatsRepositoryCustom {
  private MongoTemplate mongoTemplate;
  @Override
  public CheckStatsEntity findOneOrConstructStats(CheckStatus checkStatus, Object backstageCatalog,
      String accountIdentifier, String entityIdentifier, long lastComputedTimestamp) {
    Criteria criteria = Criteria.where(CheckStatsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CheckStatsKeys.entityIdentifier)
                            .is(entityIdentifier)
                            .and(CheckStatsKeys.checkIdentifier)
                            .is(checkStatus.getIdentifier())
                            .and(CheckStatsKeys.isCustom)
                            .is(checkStatus.isCustom());

    CheckStatsEntity entity = mongoTemplate.findOne(Query.query(criteria), CheckStatsEntity.class);
    if (entity == null) {
      return CheckStatsEntity.builder()
          .accountIdentifier(accountIdentifier)
          .entityIdentifier(entityIdentifier)
          .checkIdentifier(checkStatus.getIdentifier())
          .isCustom(checkStatus.isCustom())
          .status(String.valueOf(checkStatus.getStatus()))
          .metadata(buildMetadata(backstageCatalog))
          .createdAt(lastComputedTimestamp)
          .lastUpdatedAt(lastComputedTimestamp)
          .build();
    }
    entity.setStatus(String.valueOf(checkStatus.getStatus()));
    entity.setMetadata(buildMetadata(backstageCatalog));
    entity.setLastUpdatedAt(lastComputedTimestamp);
    return entity;
  }

  @Override
  public UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid) {
    Criteria criteria = Criteria.where(CheckStatsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CheckStatsKeys.entityIdentifier)
                            .is(entityIdentifier);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CheckStatsKeys.entityIdentifier, entityUid);
    return mongoTemplate.updateMulti(query, update, CheckStatsEntity.class);
  }

  @Override
  public List<String> findUniqueEntityIdentifiers(String accountIdentifier) {
    Criteria criteria = Criteria.where(CheckStatsKeys.accountIdentifier).is(accountIdentifier);
    Query query = new Query(criteria);
    return mongoTemplate.query(CheckStatsEntity.class)
        .distinct(CheckStatsKeys.entityIdentifier)
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
