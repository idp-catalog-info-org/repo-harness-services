/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity.BackstageCatalogKeys;
import io.harness.mongo.collation.CollationLocale;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntityRepositoryCustomImpl implements BackstageCatalogEntityRepositoryCustom {
  public static final String SPEC_TYPE = "spec.type";
  public static final String SPEC_OWNER = "spec.owner";
  public static final String SPEC_LIFECYCLE = "spec.lifecycle";
  public static final String METADATA_TAGS = BackstageCatalogKeys.metadata + "." + MetadataFieldConstants.TAGS;
  private MongoTemplate mongoTemplate;

  @Override
  public List<BackstageCatalogEntity> queryEntities(String kind, String type, List<String> owners, List<String> tags,
      List<String> lifecycle, String accountIdentifier, List<String> skipEntityUids) {
    Criteria criteria = Criteria.where(BackstageCatalogKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageCatalogKeys.kind)
                            .is(kind);
    if (!isEmpty(type) && !type.equals("all")) {
      criteria.and(SPEC_TYPE).is(type);
    }
    if (!isEmpty(owners)) {
      criteria.and(SPEC_OWNER).in(owners.stream().map(String::toLowerCase).toList());
    }
    if (!isEmpty(lifecycle)) {
      criteria.and(SPEC_LIFECYCLE).in(lifecycle);
    }
    if (!isEmpty(tags)) {
      criteria.and(METADATA_TAGS).all(tags);
    }
    if (!isEmpty(skipEntityUids)) {
      criteria.and(BackstageCatalogKeys.entityUid).nin(skipEntityUids);
    }

    Query query = Query.query(criteria).collation(
        Collation.of(String.valueOf(CollationLocale.ENGLISH)).strength(Collation.ComparisonLevel.secondary()));
    return mongoTemplate.find(query, BackstageCatalogEntity.class);
  }

  @Override
  public UpdateResult updateEntityIdentifier(String accountIdentifier, String entityIdentifier, String entityUid) {
    Criteria criteria = Criteria.where(BackstageCatalogKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageCatalogKeys.entityUid)
                            .is(entityIdentifier);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(BackstageCatalogKeys.entityUid, entityUid);
    return mongoTemplate.updateFirst(query, update, BackstageCatalogEntity.class);
  }

  @Override
  public Page<BackstageCatalogEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<BackstageCatalogEntity> backstageCatalogEntityList = mongoTemplate.find(query, BackstageCatalogEntity.class);
    return PageableExecutionUtils.getPage(backstageCatalogEntityList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BackstageCatalogEntity.class));
  }

  @Override
  public List<BackstageCatalogEntity> findAllByAccountIdentifierAndEntityUidIn(
      String accountIdentifier, List<String> entityUids) {
    Criteria criteria = Criteria.where(BackstageCatalogKeys.accountIdentifier).is(accountIdentifier);
    Criteria[] entityRefCriteria =
        entityUids.stream()
            .map(entityUid
                -> new Criteria().andOperator(
                    Criteria.where(BackstageCatalogKeys.entityUid)
                        .regex("^" + entityUid.replaceAll("[^a-zA-Z0-9]", "\\\\$0") + "$", "i")))
            .toArray(Criteria[] ::new);
    criteria.orOperator(entityRefCriteria);
    Query query = Query.query(criteria);
    return mongoTemplate.find(query, BackstageCatalogEntity.class);
  }
}
