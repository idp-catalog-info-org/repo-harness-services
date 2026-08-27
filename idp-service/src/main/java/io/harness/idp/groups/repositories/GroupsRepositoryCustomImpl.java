/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.groups.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.groups.entities.GroupEntity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class GroupsRepositoryCustomImpl implements GroupsRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public List<GroupEntity> findByAccountIdentifierAndSearchOnName(String accountIdentifier, String searchTermOnName) {
    Query query = new Query();

    query.addCriteria(Criteria.where(GroupEntity.GroupsEntityKeys.accountIdentifier).is(accountIdentifier));

    List<Criteria> criteria = new ArrayList<>();

    if (!isEmpty(searchTermOnName)) {
      criteria.add(Criteria.where(GroupEntity.GroupsEntityKeys.name).regex(".*" + searchTermOnName + ".*", "i"));
    }

    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    return mongoTemplate.find(query, GroupEntity.class);
  }
}
