/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CustomPluginV2RepositoryCustomImpl implements CustomPluginV2RepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<CustomPluginV2Entity> getCustomPluginsV2(
      String harnessAccount, Integer page, Integer limit, String sort, String searchTerm) {
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(
              page, limit, Sort.by(Sort.Direction.DESC, CustomPluginV2Entity.CustomPluginV2EntityKeys.lastUpdatedAt))
        : PageUtils.getPageRequest(page, limit, List.of(sort));

    Criteria criteria = new Criteria();
    criteria.and(CustomPluginV2Entity.CustomPluginV2EntityKeys.accountIdentifier).is(harnessAccount);
    if (!isEmpty(searchTerm)) {
      criteria.orOperator(where(CustomPluginV2Entity.CustomPluginV2EntityKeys.name)
                              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(CustomPluginV2Entity.CustomPluginV2EntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    Query query = new Query(criteria).with(pageRequest);
    List<CustomPluginV2Entity> customPluginV2Entities = mongoTemplate.find(query, CustomPluginV2Entity.class);
    return PageableExecutionUtils.getPage(customPluginV2Entities, pageRequest,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), CustomPluginV2Entity.class));
  }
}
