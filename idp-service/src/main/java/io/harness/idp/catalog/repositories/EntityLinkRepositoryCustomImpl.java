/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.EntityLinks.EntityLinkKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class EntityLinkRepositoryCustomImpl implements EntityLinkRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public List<EntityLinks> findByAccountIdentifierAndTargetEntityKindAndType(
      String accountIdentifier, String entityKind, String entityType) {
    Criteria criteria = Criteria.where(EntityLinkKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and("targets")
                            .elemMatch(Criteria.where("entityKind").is(entityKind).and("entityType").is(entityType));
    Query query = new Query(criteria);
    return mongoTemplate.find(query, EntityLinks.class);
  }

  @Override
  public List<EntityLinks> findByAccountIdentifierAndIntegrationIdentifier(
      String accountIdentifier, String integrationIdentifier, String spacePath) {
    Criteria criteria =
        Criteria.where(EntityLinkKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(EntityLinkKeys.integrations)
            .elemMatch(Criteria.where("identifier").is(integrationIdentifier).and("spacePath").is(spacePath));
    Query query = new Query(criteria);
    return mongoTemplate.find(query, EntityLinks.class);
  }

  @Override
  public long deleteByIntegration(String accountIdentifier, String integrationIdentifier, String spacePath) {
    Criteria criteria =
        Criteria.where(EntityLinkKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(EntityLinkKeys.integrations)
            .elemMatch(Criteria.where("identifier").is(integrationIdentifier).and("spacePath").is(spacePath));
    Query query = new Query(criteria);

    Update pull = new Update().pull(EntityLinkKeys.integrations,
        new BasicDBObject("identifier", integrationIdentifier).append("spacePath", spacePath));
    return mongoTemplate.updateMulti(query, pull, EntityLinks.class).getModifiedCount();
  }
}
