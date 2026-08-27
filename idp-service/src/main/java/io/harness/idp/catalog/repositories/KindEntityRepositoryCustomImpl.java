/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.common.CommonUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
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

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class KindEntityRepositoryCustomImpl implements KindEntityRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<KindEntity> getKinds(
      String parentUniqueId, Integer page, Integer limit, String sort, String searchTerm, Boolean custom) {
    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();

    if (custom == null) {
      query.addCriteria(
          Criteria.where(KindEntity.KindKeys.accountIdentifier).in(addGlobalAccountIdentifierAlong(parentUniqueId)));
    } else {
      String accountId = custom ? parentUniqueId : GLOBAL_ACCOUNT_ID;
      KindType kindType = custom ? KindType.CUSTOM : KindType.BUILT_IN;
      query.addCriteria(Criteria.where(KindEntity.KindKeys.accountIdentifier)
                            .is(accountId)
                            .and(KindEntity.KindKeys.kindType)
                            .is(kindType));
    }

    if (!isEmpty(searchTerm)) {
      criteria.add(Criteria.where(KindEntity.KindKeys.name)
                       .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    Sort sortObj = Sort.unsorted();
    if (!isEmpty(sort)) {
      if (sort.equalsIgnoreCase("identifier,asc")) {
        sortObj = Sort.by(Sort.Direction.ASC, "identifier");
      } else if (sort.equalsIgnoreCase("identifier,desc")) {
        sortObj = Sort.by(Sort.Direction.DESC, "identifier");
      } else if (sort.equalsIgnoreCase("name,asc")) {
        sortObj = Sort.by(Sort.Direction.ASC, "name");
      } else if (sort.equalsIgnoreCase("name,desc")) {
        sortObj = Sort.by(Sort.Direction.DESC, "name");
      }
    }

    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;

    query.with(sortObj);

    long totalRecords = mongoTemplate.count(query, KindEntity.class);

    Pageable pageable = PageRequest.of(pageIndex, pageLimit, sortObj);

    List<KindEntity> kinds = mongoTemplate.find(query.with(pageable), KindEntity.class);

    return new PageImpl<>(kinds, pageable, totalRecords);
  }
}
