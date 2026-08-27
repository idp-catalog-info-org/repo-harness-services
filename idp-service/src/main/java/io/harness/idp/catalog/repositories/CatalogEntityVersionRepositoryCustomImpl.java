/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
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

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityVersionRepositoryCustomImpl implements CatalogEntityVersionRepositoryCustom {
  private MongoTemplate mongoTemplate;
  private TransactionHelper transactionHelper;

  @Override
  public Page<CatalogEntityVersion> findByEntityId(
      String entityId, Integer page, Integer limit, String versionSearchTerm, Boolean deprecated) {
    Query query = new Query();
    query.addCriteria(Criteria.where("entityId").is(entityId));

    if (!isEmpty(versionSearchTerm)) {
      query.addCriteria(Criteria.where("version").regex(".*" + Pattern.quote(versionSearchTerm) + ".*", "i"));
    }

    if (deprecated != null) {
      query.addCriteria(Criteria.where("deprecated").is(deprecated));
    }

    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;

    long totalRecords = mongoTemplate.count(query, CatalogEntityVersion.class);
    if (pageLimit == -1) {
      pageLimit = totalRecords == 0 ? 10 : (int) totalRecords;
    }

    // ordering by createdAt descending to have a consistent order
    Pageable pageable = PageRequest.of(pageIndex, pageLimit).withSort(Sort.by("createdAt").descending());
    List<CatalogEntityVersion> entities = mongoTemplate.find(query.with(pageable), CatalogEntityVersion.class);
    return new PageImpl<>(entities, pageable, totalRecords);
  }

  @Override
  public Optional<CatalogEntityVersion> getStableVersionForEntity(String entityId) {
    Query query = new Query();
    query.addCriteria(Criteria.where("entityId").is(entityId).and("stable").is(true));

    return Optional.ofNullable(mongoTemplate.findOne(query, CatalogEntityVersion.class));
  }

  @Override
  public CatalogEntityVersion createCatalogEntityVersionAndSyncStable(CatalogEntityVersion catalogEntityVersion) {
    Optional<CatalogEntityVersion> existing = this.getStableVersionForEntity(catalogEntityVersion.getEntityId());

    return transactionHelper.performTransaction(() -> {
      if (catalogEntityVersion.isStable() && existing.isPresent()) {
        CatalogEntityVersion existingVersion = existing.get();
        existingVersion.setStable(false);
        mongoTemplate.save(existingVersion);
      }

      if (existing.isEmpty() && !catalogEntityVersion.isStable()) {
        catalogEntityVersion.setStable(true);
      }

      mongoTemplate.insert(catalogEntityVersion);
      return catalogEntityVersion;
    });
  }

  @Override
  public void updateCatalogEntityVersionAndSyncStable(CatalogEntityVersion catalogEntityVersion) {
    transactionHelper.performTransaction(() -> {
      if (catalogEntityVersion.isStable()) {
        Query query = new Query();

        query.addCriteria(Criteria.where("entityId").is(catalogEntityVersion.getEntityId()).and("stable").is(true));
        Update update = new Update().set("stable", false);
        mongoTemplate.updateFirst(query, update, CatalogEntityVersion.class);
      }

      mongoTemplate.save(catalogEntityVersion);
      return null;
    });
  }

  public void deleteAllByEntityId(String entityId) {
    if (isEmpty(entityId)) {
      log.warn("EntityId is empty. Skipping deletion of CatalogEntityVersion.");
      return;
    }

    Query query = new Query();
    query.addCriteria(Criteria.where("entityId").is(entityId));
    mongoTemplate.remove(query, CatalogEntityVersion.class);
  }
}
