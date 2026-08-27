/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.personaview.entities.PersonaViewEntity.PersonaViewEntityKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class PersonaViewRepositoryCustomImpl implements PersonaViewRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<PersonaViewEntity> findViewsForAdmin(String accountIdentifier, Pageable pageable, String searchTerm) {
    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where(PersonaViewEntityKeys.accountIdentifier).is(accountIdentifier));
    if (!isEmpty(searchTerm)) {
      criteria.add(new Criteria().orOperator(Criteria.where(PersonaViewEntityKeys.name).regex(searchTerm, "i"),
          Criteria.where(PersonaViewEntityKeys.identifier).regex(searchTerm, "i")));
    }
    query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    // Clone the query for count so the subsequent `with(pageable)` skip/limit on the find query does not leak
    // into the count query (count would still ignore skip/limit, but cloning also keeps the two queries
    // independent for future modifications and makes hint/index targeting explicit per query).
    Query countQuery = Query.of(query);
    long totalRecords = mongoTemplate.count(countQuery, PersonaViewEntity.class);
    query.with(pageable);
    List<PersonaViewEntity> personaViewEntities = mongoTemplate.find(query, PersonaViewEntity.class);
    return new PageImpl<>(personaViewEntities, pageable, totalRecords);
  }

  @Override
  public List<PersonaViewEntity> findViewsForUser(String accountIdentifier, List<String> userGroupIdentifiers) {
    // Strict ACL: a view is visible to a user only if its userGroupIdentifiers list contains at least one of
    // the user's groups. Views with no user_group_identifiers assigned (null / missing / empty) are hidden
    // from every user — they require an admin to assign at least one group before they appear in the user
    // list. This also applies to freshly-seeded OOTB views (platform, leadership), which start with an
    // empty user_group_identifiers list. Admins continue to see every view via findViewsForAdmin.
    if (isEmpty(userGroupIdentifiers)) {
      return List.of();
    }
    Query query = new Query();
    query.addCriteria(
        new Criteria().andOperator(Criteria.where(PersonaViewEntityKeys.accountIdentifier).is(accountIdentifier),
            Criteria.where(PersonaViewEntityKeys.userGroupIdentifiers).in(userGroupIdentifiers)));
    return mongoTemplate.find(query, PersonaViewEntity.class);
  }
}
