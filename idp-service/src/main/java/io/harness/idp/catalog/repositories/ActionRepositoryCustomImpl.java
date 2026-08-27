/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.Action.ActionKeys;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class ActionRepositoryCustomImpl implements ActionRepositoryCustom {
  private static final Set<String> SORTABLE_FIELDS =
      Set.of(ActionKeys.name, ActionKeys.createdAt, ActionKeys.lastUpdatedAt, ActionKeys.status, ActionKeys.category);

  private MongoTemplate mongoTemplate;
  private TransactionHelper transactionHelper;

  @Override
  public Page<Action> findAll(String accountIdentifier, List<String> parentUniqueIds, ActionStatus status,
      String category, String searchTerm, Integer page, Integer limit, String sort) {
    Query query = new Query();

    if (!isEmpty(parentUniqueIds)) {
      query.addCriteria(Criteria.where(ActionKeys.parentUniqueId).in(parentUniqueIds));
    } else {
      query.addCriteria(Criteria.where(ActionKeys.accountIdentifier).is(accountIdentifier));
    }

    if (status != null) {
      query.addCriteria(Criteria.where(ActionKeys.status).is(status));
    }
    if (!isEmpty(category)) {
      query.addCriteria(Criteria.where(ActionKeys.category).is(category));
    }
    if (!isEmpty(searchTerm)) {
      String escaped = Pattern.quote(searchTerm);
      query.addCriteria(new Criteria().orOperator(Criteria.where(ActionKeys.name).regex(".*" + escaped + ".*", "i"),
          Criteria.where(ActionKeys.identifier).regex(".*" + escaped + ".*", "i")));
    }

    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;

    long totalRecords = mongoTemplate.count(query, Action.class);
    if (pageLimit == -1) {
      pageLimit = Math.min(
          (int) Math.min(totalRecords, NGCommonEntityConstants.MAX_PAGE_SIZE), NGCommonEntityConstants.MAX_PAGE_SIZE);
      if (pageLimit == 0) {
        pageLimit = 10;
      }
    }

    Sort sortOrder;
    if (!isEmpty(sort)) {
      String[] sortParams = sort.split(",");
      String sortField = sortParams[0];
      if (!SORTABLE_FIELDS.contains(sortField)) {
        throw new InvalidRequestException(
            String.format("Invalid sort field [%s]. Allowed fields: %s", sortField, SORTABLE_FIELDS));
      }
      Sort.Direction direction =
          sortParams.length > 1 && "DESC".equalsIgnoreCase(sortParams[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
      sortOrder = Sort.by(direction, sortField);
    } else {
      sortOrder = Sort.by(ActionKeys.createdAt).descending();
    }

    Pageable pageable = PageRequest.of(pageIndex, pageLimit).withSort(sortOrder);
    List<Action> actions = mongoTemplate.find(query.with(pageable), Action.class);
    return new PageImpl<>(actions, pageable, totalRecords);
  }

  @Override
  public Optional<Action> findPublishedVersion(String parentUniqueId, String identifier) {
    Query query = new Query(Criteria.where(ActionKeys.parentUniqueId)
                                .is(parentUniqueId)
                                .and(ActionKeys.identifier)
                                .is(identifier)
                                .and(ActionKeys.status)
                                .is(ActionStatus.PUBLISHED));
    return Optional.ofNullable(mongoTemplate.findOne(query, Action.class));
  }

  @Override
  public List<Action> bulkFindByParentUniqueIdIdentifierVersion(Collection<ActionLookupKey> keys) {
    if (isEmpty(keys)) {
      return Collections.emptyList();
    }
    Criteria[] tupleCriteria = keys.stream()
                                   .map(k
                                       -> Criteria.where(ActionKeys.parentUniqueId)
                                              .is(k.getParentUniqueId())
                                              .and(ActionKeys.identifier)
                                              .is(k.getIdentifier())
                                              .and(ActionKeys.version)
                                              .is(k.getVersion()))
                                   .toArray(Criteria[] ::new);
    Query query = new Query(new Criteria().orOperator(tupleCriteria));
    return mongoTemplate.find(query, Action.class);
  }

  @Override
  public void deprecateCurrentlyPublished(String parentUniqueId, String identifier) {
    Query query = new Query(Criteria.where(ActionKeys.parentUniqueId)
                                .is(parentUniqueId)
                                .and(ActionKeys.identifier)
                                .is(identifier)
                                .and(ActionKeys.status)
                                .is(ActionStatus.PUBLISHED));
    Update update = new Update()
                        .set(ActionKeys.status, ActionStatus.DEPRECATED)
                        .set(ActionKeys.deprecatedAt, System.currentTimeMillis());
    mongoTemplate.updateMulti(query, update, Action.class);
  }
}
