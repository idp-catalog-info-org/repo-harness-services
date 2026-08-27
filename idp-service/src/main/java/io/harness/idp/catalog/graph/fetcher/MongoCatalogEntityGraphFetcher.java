/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.fetcher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity.CatalogKeys;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class MongoCatalogEntityGraphFetcher implements CatalogEntityGraphFetcher {
  private static final String GRAPH_TRAVERSE_FLOW_LOG = "[graphTraverse flow]";
  private final MongoTemplate mongoTemplate;

  @Inject
  public MongoCatalogEntityGraphFetcher(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<CatalogEntity> findRootEntity(String parentUniqueId, String kind, String identifier) {
    log.info("{} Looking up graph root entity parentUniqueId={} kind={} identifier={}", GRAPH_TRAVERSE_FLOW_LOG,
        parentUniqueId, kind, identifier);
    Criteria criteria = Criteria.where(CatalogKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(CatalogKeys.kind)
                            .is(kind)
                            .and(CatalogKeys.identifier)
                            .is(identifier);
    CatalogEntity rootEntity = mongoTemplate.findOne(new Query(criteria), CatalogEntity.class);
    log.info("{} Graph root lookup completed parentUniqueId={} kind={} identifier={} found={}", GRAPH_TRAVERSE_FLOW_LOG,
        parentUniqueId, kind, identifier, rootEntity != null);
    return Optional.ofNullable(rootEntity);
  }

  @Override
  public Map<String, CatalogEntity> fetchByScopedLookups(List<ScopedEntityLookup> lookups) {
    if (lookups == null || lookups.isEmpty()) {
      return Map.of();
    }
    log.info("{} Batch fetching graph entities lookupCount={}", GRAPH_TRAVERSE_FLOW_LOG, lookups.size());

    List<Criteria> orCriteria = new ArrayList<>();
    for (ScopedEntityLookup lookup : lookups) {
      Criteria c = Criteria.where(CatalogKeys.parentUniqueId)
                       .is(lookup.parentUniqueId)
                       .and(CatalogKeys.kind)
                       .is(lookup.kind)
                       .and(CatalogKeys.identifier)
                       .is(lookup.identifier);
      orCriteria.add(c);
    }

    Query query = new Query(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
    List<CatalogEntity> entities = mongoTemplate.find(query, CatalogEntity.class);

    Map<String, CatalogEntity> result = new HashMap<>();
    for (CatalogEntity entity : entities) {
      String key = entity.getKind().toLowerCase() + ":" + entity.getIdentifier();
      result.put(key, entity);
    }

    log.info("{} Batch graph fetch completed lookupCount={} fetchedEntities={} dedupedKeys={}", GRAPH_TRAVERSE_FLOW_LOG,
        lookups.size(), entities.size(), result.size());

    return result;
  }
}
