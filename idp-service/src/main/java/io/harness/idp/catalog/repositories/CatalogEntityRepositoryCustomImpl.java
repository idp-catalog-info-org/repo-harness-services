/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.IS_CUSTOM_USER_GROUP;
import static io.harness.idp.catalog.utils.Constants.NON_INHERITABLE_KINDS;
import static io.harness.idp.catalog.utils.Constants.SPEC;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.security.SecurityContextBuilder.EMAIL;
import static io.harness.security.SecurityContextBuilder.UNIQUE_ID;
import static io.harness.security.SecurityContextBuilder.USERNAME;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.GitXUtils;
import io.harness.idp.catalog.beans.HierarchyEntityCount;
import io.harness.idp.catalog.beans.ScopeData;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.common.CommonUtils;
import io.harness.mongo.MongoPersistence;
import io.harness.security.SourcePrincipalContextBuilder;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityRepositoryCustomImpl implements CatalogEntityRepositoryCustom {
  private MongoTemplate mongoTemplate;
  private GitAwareEntityHelper gitAwareEntityHelper;
  private MongoPersistence mongoPersistence;

  @Override
  public Page<CatalogEntity> getEntities(String harnessAccount, List<ScopeInfo> scopeInfos, Integer page, Integer limit,
      String sort, String searchTerm, String requestedEntityRefs, String entityRefs, String kind, String type,
      String owner, String lifecycle, String tags, String arbitraryFields, String filter) {
    return getEntities(harnessAccount, scopeInfos, page, limit, sort, searchTerm, requestedEntityRefs, entityRefs, null,
        kind, type, owner, lifecycle, tags, arbitraryFields, filter, false);
  }

  @Override
  public Page<CatalogEntity> getEntities(String harnessAccount, List<ScopeInfo> scopeInfos, Integer page, Integer limit,
      String sort, String searchTerm, String requestedEntityRefs, String entityRefs, String permittedEntityRefs,
      String kind, String type, String owner, String lifecycle, String tags, String arbitraryFields, String filter,
      boolean entityRefAndCriteria) {
    Query query = new Query();

    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                          .in(scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList()));

    List<Criteria> criteria = new ArrayList<>();

    if (isEmpty(entityRefs) && !isEmpty(requestedEntityRefs)) {
      return new PageImpl<>(Collections.emptyList(),
          PageRequest.of(page == null ? 0 : page, limit == null ? 10 : limit, Sort.unsorted()), 0);
    }

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(entityRefs)) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(entityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(permittedEntityRefs)) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(permittedEntityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(kind)) {
      List<String> kindList = Arrays.asList(kind.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindList));
    }

    if (!isEmpty(type)) {
      List<String> typeList = Arrays.asList(type.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.type).in(typeList));
    }

    if (!isEmpty(owner)) {
      Criteria ownerCriteria = getOwnerCriteria(owner);
      if (ownerCriteria != null) {
        criteria.add(ownerCriteria);
      }
    }

    if (!isEmpty(lifecycle)) {
      List<String> lifecycleList = Arrays.asList(lifecycle.split(","));
      criteria.add(Criteria.where("spec.lifecycle").in(lifecycleList));
    }

    if (!isEmpty(tags)) {
      List<String> tagsList = Arrays.asList(tags.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.tags).in(tagsList));
    }

    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }

    if (!isEmpty(arbitraryFields)) {
      String[] fields = arbitraryFields.split(",");
      for (String field : fields) {
        String[] keyValue = field.split("=");
        criteria.add(Criteria.where(keyValue[0]).is(keyValue[1]));
      }
    }

    if (!isEmpty(searchTerm)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.name)
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
    int pageLimit = limit == null ? 10 : limit;

    Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    query.collation(collation).with(sortObj);

    long totalRecords = mongoTemplate.count(query, CatalogEntity.class);
    if (pageLimit == -1) {
      pageLimit = totalRecords == 0 ? 10 : (int) totalRecords;
    }

    if (!isEmpty(requestedEntityRefs)) {
      String[] requestedEntityRefsList = requestedEntityRefs.split(",");
      int requestedEntityRefsCount = requestedEntityRefsList.length;
      if (pageLimit < requestedEntityRefsCount) {
        pageLimit = requestedEntityRefsCount;
      }
    }

    Pageable pageable = PageRequest.of(pageIndex, pageLimit, sortObj);
    List<CatalogEntity> entities = mongoTemplate.find(query.with(pageable), CatalogEntity.class);
    return new PageImpl<>(entities, pageable, totalRecords);
  }

  @Override
  public Page<CatalogEntity> getEntitiesWithKindScopes(String harnessAccount,
      Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes, List<String> permittedEntityRefs,
      List<ScopeInfo> allRequestedScopeInfos, Integer page, Integer limit, String sort, String searchTerm,
      String requestedEntityRefs, String entityRefs, String kind, String type, String owner, String lifecycle,
      String tags, String filter, boolean entityRefAndCriteria, List<String> permittedGroupEntityRefs) {
    Query query = new Query();

    List<Criteria> scopeKindCriteria = new ArrayList<>();

    for (Map.Entry<String, List<ScopeInfo>> entry : resourceTypeToPermittedScopes.entrySet()) {
      String resourceType = entry.getKey();
      List<ScopeInfo> scopes = entry.getValue();
      if (scopes.isEmpty()) {
        continue;
      }

      List<String> scopeUniqueIds = scopes.stream().map(ScopeInfo::getUniqueId).distinct().toList();
      List<String> kindsForType = io.harness.idp.catalog.rbac.KindResourceTypeMapper.kindsForResourceType(resourceType);

      Criteria scopeCriteria = Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(scopeUniqueIds);

      if (kindsForType != null) {
        scopeKindCriteria.add(
            new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindsForType), scopeCriteria));
      } else {
        scopeKindCriteria.add(
            new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.kind)
                                           .nin(io.harness.idp.catalog.rbac.KindResourceTypeMapper.SPECIAL_KINDS),
                scopeCriteria));
      }
    }

    if (!isEmpty(permittedEntityRefs)) {
      String permittedRefsStr = String.join(",", permittedEntityRefs);
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(permittedRefsStr, harnessAccount, allRequestedScopeInfos);
      if (entityRefCriteria.length > 0) {
        scopeKindCriteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(permittedGroupEntityRefs)) {
      Criteria permittedOwnerRefsCriteria =
          getOwnedByGroupEntityRefsWithRequestedScopes(permittedGroupEntityRefs, allRequestedScopeInfos);
      scopeKindCriteria.add(new Criteria().orOperator(permittedOwnerRefsCriteria));
    }

    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;

    if (scopeKindCriteria.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageIndex, pageLimit, Sort.unsorted()), 0);
    }

    List<Criteria> topLevelAnd = new ArrayList<>();
    topLevelAnd.add(new Criteria().orOperator(scopeKindCriteria.toArray(new Criteria[0])));

    if (isEmpty(entityRefs) && !isEmpty(requestedEntityRefs)) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageIndex, pageLimit, Sort.unsorted()), 0);
    }

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      List<ScopeInfo> allScopes =
          resourceTypeToPermittedScopes.values().stream().flatMap(List::stream).distinct().toList();
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, allScopes);
      if (entityRefCriteria.length > 0) {
        topLevelAnd.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(entityRefs)) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(entityRefs, harnessAccount, allRequestedScopeInfos);
      if (entityRefCriteria.length > 0) {
        topLevelAnd.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(kind)) {
      List<String> kindList = Arrays.asList(kind.split(","));
      topLevelAnd.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindList));
    }

    if (!isEmpty(type)) {
      List<String> typeList = Arrays.asList(type.split(","));
      topLevelAnd.add(Criteria.where(CatalogEntity.CatalogKeys.type).in(typeList));
    }

    if (!isEmpty(owner)) {
      Criteria ownerCriteria = getOwnerCriteria(owner);
      if (ownerCriteria != null) {
        topLevelAnd.add(ownerCriteria);
      }
    }

    if (!isEmpty(lifecycle)) {
      List<String> lifecycleList = Arrays.asList(lifecycle.split(","));
      topLevelAnd.add(Criteria.where("spec.lifecycle").in(lifecycleList));
    }

    if (!isEmpty(tags)) {
      List<String> tagsList = Arrays.asList(tags.split(","));
      topLevelAnd.add(Criteria.where(CatalogEntity.CatalogKeys.tags).in(tagsList));
    }

    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        topLevelAnd.add(filterCriteria);
      }
    }

    if (!isEmpty(searchTerm)) {
      topLevelAnd.add(Criteria.where(CatalogEntity.CatalogKeys.name)
                          .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    query.addCriteria(new Criteria().andOperator(topLevelAnd.toArray(new Criteria[0])));

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

    Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    query.collation(collation).with(sortObj);

    long totalRecords = mongoTemplate.count(query, CatalogEntity.class);
    if (pageLimit == -1) {
      pageLimit = totalRecords == 0 ? 10 : (int) totalRecords;
    }

    if (!isEmpty(requestedEntityRefs)) {
      String[] requestedEntityRefsList = requestedEntityRefs.split(",");
      int requestedEntityRefsCount = requestedEntityRefsList.length;
      if (pageLimit < requestedEntityRefsCount) {
        pageLimit = requestedEntityRefsCount;
      }
    }

    Pageable pageable = PageRequest.of(pageIndex, pageLimit, sortObj);
    List<CatalogEntity> entities = mongoTemplate.find(query.with(pageable), CatalogEntity.class);
    return new PageImpl<>(entities, pageable, totalRecords);
  }

  public List<CatalogEntity> getEntitiesForEntityRefsAndKinds(
      String accountIdentifier, String entityRefs, List<ScopeInfo> scopeInfos, List<String> kinds) {
    List<Criteria> criteria = new ArrayList<>();
    Query query = new Query();

    if (!isEmpty(entityRefs)) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(entityRefs, accountIdentifier, scopeInfos);
      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(kinds)) {
      Criteria[] kindCriteria =
          kinds.stream().map(k -> Criteria.where(CatalogEntity.CatalogKeys.kind).is(k)).toArray(Criteria[] ::new);
      criteria.add(new Criteria().orOperator(kindCriteria));
    }

    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    return mongoTemplate.find(query, CatalogEntity.class);
  }

  private Criteria getOwnedByGroupEntityRefsWithRequestedScopes(
      List<String> groupEntityRefs, List<ScopeInfo> scopeInfos) {
    Criteria scopeCriteria = Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                                 .in(scopeInfos.stream().map(ScopeInfo::getUniqueId).toList());
    Criteria inheritableKindCriteria = Criteria.where(CatalogEntity.CatalogKeys.kind).nin(NON_INHERITABLE_KINDS);
    Criteria ownedByGroupsCriteria = getOwnerCriteriaForGroups(groupEntityRefs);
    if (ownedByGroupsCriteria == null) {
      return new Criteria().andOperator(scopeCriteria, inheritableKindCriteria);
    }
    return new Criteria().andOperator(scopeCriteria, ownedByGroupsCriteria, inheritableKindCriteria);
  }

  private Criteria[] getEntityRefCriteria(String entityRefs, String harnessAccount, List<ScopeInfo> scopeInfos) {
    List<String> entityRefList = Arrays.asList(entityRefs.split(","));
    Criteria[] entityRefCriteria =
        entityRefList.stream()
            .map(entityRef -> {
              String[] parts = entityRef.split(":");
              if (parts.length == 2) {
                int slashIndex = parts[1].indexOf("/");

                String scope = slashIndex != -1 ? parts[1].substring(0, slashIndex) : harnessAccount;

                String[] hierarchyScope = scope.split("\\.");
                ScopeInfo scopeInfo = ScopeInfo.builder()
                                          .accountIdentifier(harnessAccount)
                                          .uniqueId(harnessAccount)
                                          .scopeType(ScopeLevel.ACCOUNT)
                                          .build();
                if (hierarchyScope.length == 3) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(
                              scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), hierarchyScope[1]))
                          .filter(scopeInfoScope
                              -> Objects.equals(scopeInfoScope.getProjectIdentifier(), hierarchyScope[2]))
                          .findFirst()
                          .orElse(null);
                } else if (hierarchyScope.length == 2) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(
                              scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), hierarchyScope[1]))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getProjectIdentifier(), null))
                          .findFirst()
                          .orElse(null);
                } else if (hierarchyScope.length == 1 && hierarchyScope[0].equals("account")) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), null))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getProjectIdentifier(), null))
                          .findFirst()
                          .orElse(null);
                }

                if (scopeInfo != null) {
                  String entityIdentifier = slashIndex != -1 ? parts[1].substring(slashIndex + 1) : parts[1];

                  return new Criteria().andOperator(
                      Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).is(scopeInfo.getUniqueId()),
                      Criteria.where(CatalogEntity.CatalogKeys.kind).is(parts[0]),
                      Criteria.where(CatalogEntity.CatalogKeys.identifier).is(entityIdentifier));
                }
              } else if (parts.length == 1) {
                return new Criteria().andOperator(
                    Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).is(harnessAccount),
                    Criteria.where(CatalogEntity.CatalogKeys.kind).is("component"),
                    Criteria.where(CatalogEntity.CatalogKeys.identifier).is(parts[0]));
              }
              return null;
            })
            .filter(Objects::nonNull)
            .toArray(Criteria[] ::new);
    return entityRefCriteria;
  }

  @Override
  public Optional<CatalogEntity> findUserBasedOnAccountIdAndUUID(String accountIdentifier, String uuid) {
    Criteria criteria = Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and("metadata.uuid")
                            .is(uuid)
                            .and(CatalogEntity.CatalogKeys.kind)
                            .is(USER_KIND);
    Query query = new Query(criteria);
    return Optional.of(mongoTemplate.findOne(query, CatalogEntity.class));
  }

  @Override
  public long getOwnedEntitiesCount(List<String> parentUniqueIds, String kind, String owner, String harnessAccount,
      List<ScopeInfo> scopeInfos, String requestedEntityRefs, boolean entityRefAndCriteria, String filter) {
    if (isEmpty(owner)) {
      return 0;
    }
    Query query = new Query();
    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(parentUniqueIds));
    List<Criteria> criteria = new ArrayList<>();

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }
    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }
    if (!isEmpty(kind)) {
      List<String> kindList = Arrays.asList(kind.split(","));
      Criteria[] kindCriteria =
          kindList.stream().map(k -> Criteria.where(CatalogEntity.CatalogKeys.kind).is(k)).toArray(Criteria[] ::new);
      criteria.add(new Criteria().orOperator(kindCriteria));
    }

    List<String> ownerList = Arrays.asList(owner.split(","));
    Criteria[] ownerCriteria =
        ownerList.stream()
            .flatMap(k -> {
              Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifierForUserAndGroup(k);
              if (kindScopeIdentifier != null) {
                if (kindScopeIdentifier.getLeft() != null && kindScopeIdentifier.getMiddle() != null
                    && kindScopeIdentifier.getRight() != null) {
                  return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                       .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle() + "/"
                                           + kindScopeIdentifier.getRight())));
                } else {
                  return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                       .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle() + "/"
                                           + kindScopeIdentifier.getRight())),
                      Criteria.where(CatalogEntity.CatalogKeys.owner)
                          .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getRight())),
                      Criteria.where(CatalogEntity.CatalogKeys.owner).is(kindScopeIdentifier.getRight()));
                }
              } else {
                return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner).is(k));
              }
            })
            .toArray(Criteria[] ::new);
    criteria.add(new Criteria().orOperator(ownerCriteria));
    query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    return mongoTemplate.count(query, CatalogEntity.class);
  }

  @Override
  public long getOwnedEntitiesCountWithKindScopes(String harnessAccount,
      Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes, List<String> permittedEntityRefs,
      List<ScopeInfo> allRequestedScopeInfos, String kind, String owner, String requestedEntityRefs,
      boolean entityRefAndCriteria, String filter) {
    if (isEmpty(owner)) {
      return 0;
    }

    List<Criteria> scopeKindCriteria = new ArrayList<>();
    for (Map.Entry<String, List<ScopeInfo>> entry : resourceTypeToPermittedScopes.entrySet()) {
      List<ScopeInfo> scopes = entry.getValue();
      if (scopes.isEmpty()) {
        continue;
      }

      Criteria scopeCriteria = Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                                   .in(scopes.stream().map(ScopeInfo::getUniqueId).distinct().toList());
      List<String> kindsForType =
          io.harness.idp.catalog.rbac.KindResourceTypeMapper.kindsForResourceType(entry.getKey());
      if (kindsForType != null) {
        scopeKindCriteria.add(
            new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindsForType), scopeCriteria));
      } else {
        scopeKindCriteria.add(
            new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.kind)
                                           .nin(io.harness.idp.catalog.rbac.KindResourceTypeMapper.SPECIAL_KINDS),
                scopeCriteria));
      }
    }

    if (!isEmpty(permittedEntityRefs)) {
      Criteria[] entityRefCriteria =
          getEntityRefCriteriaV2(String.join(",", permittedEntityRefs), harnessAccount, allRequestedScopeInfos);
      if (entityRefCriteria.length > 0) {
        scopeKindCriteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (scopeKindCriteria.isEmpty()) {
      return 0;
    }

    List<Criteria> criteria = new ArrayList<>();
    criteria.add(new Criteria().orOperator(scopeKindCriteria.toArray(new Criteria[0])));

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      Criteria[] entityRefCriteria =
          getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, allRequestedScopeInfos);
      if (entityRefCriteria.length == 0) {
        return 0;
      }
      criteria.add(new Criteria().orOperator(entityRefCriteria));
    }

    if (!isEmpty(kind)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(Arrays.asList(kind.split(","))));
    }

    Criteria ownerCriteria = getOwnerCriteria(owner);
    if (ownerCriteria == null) {
      return 0;
    }
    criteria.add(ownerCriteria);

    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }

    Query query = new Query();
    query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    return mongoTemplate.count(query, CatalogEntity.class);
  }

  @Override
  public long getFavoritesEntitiesCount(String harnessAccount, List<ScopeInfo> scopeInfos, String favoriteEntityRefs,
      String requestedEntityRefs, boolean entityRefAndCriteria, String filter) {
    if (isEmpty(favoriteEntityRefs)) {
      return 0;
    }

    Query query = new Query();
    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                          .in(scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList()));
    List<Criteria> criteria = new ArrayList<>();

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }
    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }
    Criteria[] entityRefCriteria = getEntityRefCriteriaV2(favoriteEntityRefs, harnessAccount, scopeInfos);
    criteria.add(new Criteria().orOperator(entityRefCriteria));
    query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    return mongoTemplate.count(query, CatalogEntity.class);
  }

  @Override
  public long getFavoritesEntitiesCountWithGroupFallback(String harnessAccount, List<ScopeInfo> scopeInfos,
      String favoriteEntityRefs, String requestedEntityRefs, String deniedFavoriteEntityRefs,
      String permittedGroupEntityRefs, boolean entityRefAndCriteria, String filter) {
    List<Criteria> criteria = buildFavoritesCriteriaWithGroupFallback(harnessAccount, scopeInfos, favoriteEntityRefs,
        requestedEntityRefs, deniedFavoriteEntityRefs, permittedGroupEntityRefs, entityRefAndCriteria, filter);
    if (criteria == null) {
      return 0;
    }
    Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    return mongoTemplate.count(query, CatalogEntity.class);
  }

  @Override
  public Page<CatalogEntity> getFavoritesEntitiesPageWithGroupFallback(String harnessAccount,
      List<ScopeInfo> scopeInfos, String favoriteEntityRefs, String requestedEntityRefs,
      String deniedFavoriteEntityRefs, String permittedGroupEntityRefs, boolean entityRefAndCriteria, String filter,
      Integer page, Integer limit, String sort, String searchTerm, String kind, String type, String owner,
      String lifecycle, String tags) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;

    List<Criteria> criteria = buildFavoritesCriteriaWithGroupFallback(harnessAccount, scopeInfos, favoriteEntityRefs,
        requestedEntityRefs, deniedFavoriteEntityRefs, permittedGroupEntityRefs, entityRefAndCriteria, filter);
    if (criteria == null) {
      return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageIndex, pageLimit, Sort.unsorted()), 0);
    }

    if (!isEmpty(kind)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(Arrays.asList(kind.split(","))));
    }
    if (!isEmpty(type)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.type).in(Arrays.asList(type.split(","))));
    }
    if (!isEmpty(owner)) {
      Criteria ownerCriteria = getOwnerCriteria(owner);
      if (ownerCriteria != null) {
        criteria.add(ownerCriteria);
      }
    }
    if (!isEmpty(lifecycle)) {
      criteria.add(Criteria.where("spec.lifecycle").in(Arrays.asList(lifecycle.split(","))));
    }
    if (!isEmpty(tags)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.tags).in(Arrays.asList(tags.split(","))));
    }
    if (!isEmpty(searchTerm)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.name)
                       .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

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

    Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    query.collation(collation).with(sortObj);

    long totalRecords = mongoTemplate.count(query, CatalogEntity.class);
    if (pageLimit == -1) {
      pageLimit = totalRecords == 0 ? 10 : (int) totalRecords;
    }

    if (!isEmpty(requestedEntityRefs)) {
      String[] requestedEntityRefsList = requestedEntityRefs.split(",");
      int requestedEntityRefsCount = requestedEntityRefsList.length;
      if (pageLimit < requestedEntityRefsCount) {
        pageLimit = requestedEntityRefsCount;
      }
    }

    Pageable pageable = PageRequest.of(pageIndex, pageLimit, sortObj);
    List<CatalogEntity> entities = mongoTemplate.find(query.with(pageable), CatalogEntity.class);
    return new PageImpl<>(entities, pageable, totalRecords);
  }

  private List<Criteria> buildFavoritesCriteriaWithGroupFallback(String harnessAccount, List<ScopeInfo> scopeInfos,
      String favoriteEntityRefs, String requestedEntityRefs, String deniedFavoriteEntityRefs,
      String permittedGroupEntityRefs, boolean entityRefAndCriteria, String filter) {
    boolean hasGroupFallback = !isEmpty(deniedFavoriteEntityRefs) && !isEmpty(permittedGroupEntityRefs);
    if (isEmpty(favoriteEntityRefs) && !hasGroupFallback) {
      return null;
    }

    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                     .in(scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList()));

    if (!isEmpty(requestedEntityRefs) && entityRefAndCriteria) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(requestedEntityRefs, harnessAccount, scopeInfos);

      if (entityRefCriteria.length > 0) {
        criteria.add(new Criteria().orOperator(entityRefCriteria));
      }
    }
    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }
    List<Criteria> favoriteBranches = new ArrayList<>();
    if (!isEmpty(favoriteEntityRefs)) {
      Criteria[] entityRefCriteria = getEntityRefCriteriaV2(favoriteEntityRefs, harnessAccount, scopeInfos);
      if (entityRefCriteria.length > 0) {
        favoriteBranches.add(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (hasGroupFallback) {
      Criteria[] deniedCriteria = getEntityRefCriteriaV2(deniedFavoriteEntityRefs, harnessAccount, scopeInfos);
      if (deniedCriteria.length > 0) {
        List<String> permittedGroups = Arrays.stream(permittedGroupEntityRefs.split(",")).toList();
        favoriteBranches.add(new Criteria().andOperator(new Criteria().orOperator(deniedCriteria),
            Criteria.where(CatalogEntity.CatalogKeys.owner).in(permittedGroups),
            Criteria.where(CatalogEntity.CatalogKeys.kind).nin(NON_INHERITABLE_KINDS)));
      }
    }

    if (favoriteBranches.isEmpty()) {
      return null;
    }
    criteria.add(new Criteria().orOperator(favoriteBranches.toArray(new Criteria[0])));
    return criteria;
  }

  @Override
  public List<CatalogEntity> getEntitiesForArbitraryFields(
      String accountIdentifier, Map<String, Object> arbitraryFields, String kind) {
    Query query = new Query();
    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier).is(accountIdentifier));
    Criteria kindCriteria = null;
    if (!isEmpty(kind)) {
      List<String> kindList = Arrays.asList(kind.split(","));
      kindCriteria = new Criteria().orOperator(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindList));
    }

    Criteria arbitraryFieldCriteria = null;
    if (!isEmpty(arbitraryFields)) {
      List<Criteria> fieldCriteriaList = new ArrayList<>();
      for (Map.Entry<String, Object> entry : arbitraryFields.entrySet()) {
        if (entry.getValue() instanceof Pattern) {
          fieldCriteriaList.add(Criteria.where(entry.getKey()).regex((Pattern) entry.getValue()));
        } else {
          fieldCriteriaList.add(Criteria.where(entry.getKey()).is(entry.getValue()));
        }
      }
      if (!fieldCriteriaList.isEmpty()) {
        arbitraryFieldCriteria = new Criteria().orOperator(fieldCriteriaList.toArray(new Criteria[0]));
      }
    }

    List<Criteria> combined = new ArrayList<>();
    if (kindCriteria != null) {
      combined.add(kindCriteria);
    }
    if (arbitraryFieldCriteria != null) {
      combined.add(arbitraryFieldCriteria);
    }
    if (!combined.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(combined.toArray(new Criteria[0])));
    }
    return mongoTemplate.find(query, CatalogEntity.class);
  }

  @Override
  public Page<CatalogEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);
    return PageableExecutionUtils.getPage(
        catalogEntities, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), CatalogEntity.class));
  }

  private Triple<String, String, String> getKindScopeIdentifierForUserAndGroup(String entityRef) {
    String[] entityRefSplit = entityRef.split(":");
    String kind;
    String scope = "account";
    String identifier;
    if (entityRefSplit.length == 2) {
      kind = entityRefSplit[0];
      String scopeAndIdentifier = entityRefSplit[1];
      int slashIndex = scopeAndIdentifier.indexOf("/");
      scope = slashIndex != -1 ? scopeAndIdentifier.substring(0, slashIndex) : scope;
      identifier = slashIndex != -1 ? scopeAndIdentifier.substring(slashIndex + 1) : scopeAndIdentifier;
      return Triple.of(kind.toLowerCase(), scope, identifier);
    }
    return null;
  }

  @Override
  public GitReferencedCatalogEntity getRemoteServiceWithYaml(
      GitReferencedCatalogEntity gitReferencedCatalogEntity, boolean loadFromCache, boolean loadFromFallbackBranch) {
    try {
      String branchName = gitAwareEntityHelper.getWorkingBranch(gitReferencedCatalogEntity.getRepo());
      if (loadFromFallbackBranch) {
        gitReferencedCatalogEntity =
            fetchRemoteEntityWithFallBackBranch(gitReferencedCatalogEntity, branchName, loadFromCache);
      } else {
        gitReferencedCatalogEntity = fetchRemoteEntity(gitReferencedCatalogEntity, branchName, loadFromCache);
      }
      return gitReferencedCatalogEntity;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(
          String.format("Error while retrieving entity YAML: [%s]", gitReferencedCatalogEntity.getIdentifier()), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Unexpected error occurred while retrieving entity YAML: [%s]",
                    gitReferencedCatalogEntity.getIdentifier()),
          e);
      throw new InternalServerErrorException(String.format(
          "Unexpected error occurred while retrieving entity YAML: [%s]", gitReferencedCatalogEntity.getIdentifier()));
    }
  }

  @Override
  public Long countFileInstances(
      String accountIdentifier, String repoURL, String filePath, String connectorRef, String repoName) {
    Criteria criteria;
    if (isEmpty(connectorRef)) {
      criteria = Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                     .is(accountIdentifier)
                     .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.repo)
                     .is(repoName)
                     .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.filePath)
                     .is(filePath);
    } else {
      criteria = Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                     .is(accountIdentifier)
                     .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.repoURL)
                     .is(repoURL)
                     .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.filePath)
                     .is(filePath);
    }
    Query query = new Query(criteria);
    return mongoTemplate.count(Query.of(query).limit(-1).skip(-1), CatalogEntity.class);
  }

  @Override
  public CatalogEntity findByFilePathAndRepo(String accountIdentifier, String filePath, String repo) {
    Criteria criteria = Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.repo)
                            .is(repo)
                            .and(GitReferencedCatalogEntity.GitReferencedCatalogEntityKeys.filePath)
                            .is(filePath);
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, CatalogEntity.class);
  }

  @Override
  public List<String> findDistinctOrgIdentifiersByAccountIdentifierAndProjectIdentifierIsNull(
      String accountIdentifier) {
    Criteria criteria = Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CatalogEntity.CatalogKeys.orgIdentifier)
                            .exists(true)
                            .ne(null)
                            .and(CatalogEntity.CatalogKeys.projectIdentifier)
                            .is(null);
    Query query = new Query(criteria);
    return mongoTemplate.query(CatalogEntity.class)
        .distinct(CatalogEntity.CatalogKeys.orgIdentifier)
        .matching(query)
        .as(String.class)
        .all();
  }

  @Override
  public Map<String, Set<String>> findDistinctProjectIdentifiersByOrgIdentifierForAccount(String accountIdentifier) {
    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                                         .is(accountIdentifier)
                                                         .and(CatalogEntity.CatalogKeys.orgIdentifier)
                                                         .exists(true)
                                                         .ne(null)
                                                         .and(CatalogEntity.CatalogKeys.projectIdentifier)
                                                         .exists(true)
                                                         .ne(null)),
            Aggregation.group(CatalogEntity.CatalogKeys.orgIdentifier)
                .addToSet(CatalogEntity.CatalogKeys.projectIdentifier)
                .as("projectIdentifiers"),
            Aggregation.project("projectIdentifiers").and("_id").as("orgIdentifier"));

    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, mongoTemplate.getCollectionName(CatalogEntity.class), Document.class);

    return results.getMappedResults().stream().collect(Collectors.toMap(document
        -> document.getString("orgIdentifier"),
        document
        -> Set.copyOf((List<String>) document.get("projectIdentifiers", List.class)),
        (existing, replacement) -> existing, LinkedHashMap::new));
  }

  @Override
  public List<ScopeData> findDistinctScopeData(String accountIdentifier) {
    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                                         .is(accountIdentifier)
                                                         .and(CatalogEntity.CatalogKeys.orgIdentifier)
                                                         .exists(true)
                                                         .ne(null)),
            Aggregation.group(CatalogEntity.CatalogKeys.orgIdentifier, CatalogEntity.CatalogKeys.projectIdentifier)
                .first(CatalogEntity.CatalogKeys.parentUniqueId)
                .as("parentUniqueId"),
            Aggregation.project("parentUniqueId")
                .and("_id." + CatalogEntity.CatalogKeys.orgIdentifier)
                .as("orgIdentifier")
                .and("_id." + CatalogEntity.CatalogKeys.projectIdentifier)
                .as("projectIdentifier"));

    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, mongoTemplate.getCollectionName(CatalogEntity.class), Document.class);

    return results.getMappedResults()
        .stream()
        .map(document
            -> ScopeData.builder()
                   .orgIdentifier(document.getString("orgIdentifier"))
                   .projectIdentifier(document.getString("projectIdentifier"))
                   .parentUniqueId(document.getString("parentUniqueId"))
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<String> findDistinctAccountIdentifiers() {
    return mongoTemplate.findDistinct(CatalogEntity.CatalogKeys.accountIdentifier, CatalogEntity.class, String.class);
  }

  @Override
  public List<HierarchyEntityCount> topEntityCountsByOrg(String accountIdentifier, String excludedKind, int limit) {
    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                                         .is(accountIdentifier)
                                                         .and(CatalogEntity.CatalogKeys.orgIdentifier)
                                                         .exists(true)
                                                         .ne(null)
                                                         .and(CatalogEntity.CatalogKeys.kind)
                                                         .ne(excludedKind)),
            Aggregation.group(CatalogEntity.CatalogKeys.orgIdentifier).count().as("count"),
            Aggregation.sort(Sort.by(Sort.Order.desc("count"), Sort.Order.asc("_id"))), Aggregation.limit(limit));

    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, mongoTemplate.getCollectionName(CatalogEntity.class), Document.class);

    List<HierarchyEntityCount> counts = new ArrayList<>();
    for (Document document : results.getMappedResults()) {
      String orgIdentifier = document.getString("_id");
      if (orgIdentifier != null) {
        counts.add(
            HierarchyEntityCount.builder().orgIdentifier(orgIdentifier).count(toLong(document.get("count"))).build());
      }
    }
    return counts;
  }

  @Override
  public List<HierarchyEntityCount> topEntityCountsByOrgAndProject(
      String accountIdentifier, String excludedKind, int limit) {
    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                                         .is(accountIdentifier)
                                                         .and(CatalogEntity.CatalogKeys.projectIdentifier)
                                                         .exists(true)
                                                         .ne(null)
                                                         .and(CatalogEntity.CatalogKeys.kind)
                                                         .ne(excludedKind)),
            Aggregation.group(CatalogEntity.CatalogKeys.orgIdentifier, CatalogEntity.CatalogKeys.projectIdentifier)
                .count()
                .as("count"),
            Aggregation.sort(Sort.by(Sort.Order.desc("count"), Sort.Order.asc("_id"))), Aggregation.limit(limit));

    AggregationResults<Document> results =
        mongoTemplate.aggregate(aggregation, mongoTemplate.getCollectionName(CatalogEntity.class), Document.class);

    List<HierarchyEntityCount> counts = new ArrayList<>();
    for (Document document : results.getMappedResults()) {
      Document key = document.get("_id", Document.class);
      if (key == null) {
        continue;
      }
      String orgIdentifier = key.getString(CatalogEntity.CatalogKeys.orgIdentifier);
      String projectIdentifier = key.getString(CatalogEntity.CatalogKeys.projectIdentifier);
      if (orgIdentifier != null && projectIdentifier != null) {
        counts.add(HierarchyEntityCount.builder()
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .count(toLong(document.get("count")))
                       .build());
      }
    }
    return counts;
  }

  @Override
  public List<CatalogEntity> findHierarchyProjectNodesForKeys(
      String accountIdentifier, String kind, List<HierarchyEntityCount> keys) {
    if (isEmpty(keys)) {
      return Collections.emptyList();
    }
    Criteria[] pairCriteria = keys.stream()
                                  .map(key
                                      -> Criteria.where(CatalogEntity.CatalogKeys.orgIdentifier)
                                             .is(key.getOrgIdentifier())
                                             .and(CatalogEntity.CatalogKeys.projectIdentifier)
                                             .is(key.getProjectIdentifier()))
                                  .toArray(Criteria[] ::new);
    Query query = new Query(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier)
                                .is(accountIdentifier)
                                .and(CatalogEntity.CatalogKeys.kind)
                                .is(kind)
                                .orOperator(pairCriteria));
    return mongoTemplate.find(query, CatalogEntity.class);
  }

  private static long toLong(Object raw) {
    return raw instanceof Number ? ((Number) raw).longValue() : 0L;
  }

  @Override
  public void convertInlineToGit(ScopeInfo scopeInfo, String kind, String identifier) {
    BasicDBObject queryOps = new BasicDBObject("parentUniqueId", scopeInfo.getUniqueId())
                                 .append("kind", kind)
                                 .append("identifier", identifier);

    BasicDBObject updateDBObject = new BasicDBObject();
    updateDBObject.put("_class", "io.harness.idp.catalog.entities.GitReferencedCatalogEntity");
    updateDBObject.put("referenceType", "GIT");

    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    updateDBObject.put("repoURL",
        gitAwareEntityHelper.getRepoUrl(
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()));
    updateDBObject.put("storeType", gitEntityInfo.getStoreType());
    updateDBObject.put("repo", gitEntityInfo.getRepoName());
    updateDBObject.put("filePath", gitEntityInfo.getFilePath());
    updateDBObject.put("connectorRef", gitEntityInfo.getConnectorRef());
    updateDBObject.put("fallBackBranch", gitEntityInfo.getBranch());
    updateDBObject.put("lastUpdatedAt", System.currentTimeMillis());
    updateDBObject.put(
        "lastUpdatedBy.name", SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(USERNAME));
    updateDBObject.put(
        "lastUpdatedBy.email", SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(EMAIL));
    updateDBObject.put(
        "lastUpdatedBy.uuid", SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID));

    BulkWriteOperation writeOperation =
        mongoPersistence.getCollection(CatalogEntity.class).initializeUnorderedBulkOperation();
    writeOperation.find(queryOps).update(new BasicDBObject("$set", updateDBObject));
    BulkWriteResult updateOperationResult = writeOperation.execute();
    if (updateOperationResult.getModifiedCount() != 1) {
      throw new InvalidRequestException("Could not convert Inline to Remote entity for identifier " + identifier);
    }
  }

  private GitReferencedCatalogEntity fetchRemoteEntityWithFallBackBranch(
      GitReferencedCatalogEntity gitReferencedCatalogEntity, String branch, boolean loadFromCache) {
    try {
      gitReferencedCatalogEntity = fetchRemoteEntity(gitReferencedCatalogEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      String fallBackBranch = gitReferencedCatalogEntity.getFallBackBranch();
      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(
          gitReferencedCatalogEntity.getFallBackBranch());
      if (shouldRetryWithFallBackBranch(GitXUtils.getScmExceptionIfExists(ex), branch, fallBackBranch)) {
        log.info(String.format("Retrieving entity [%s] from fall back branch [%s] ",
            gitReferencedCatalogEntity.getIdentifier(), fallBackBranch));
        gitReferencedCatalogEntity = fetchRemoteEntity(gitReferencedCatalogEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return gitReferencedCatalogEntity;
  }

  private GitReferencedCatalogEntity fetchRemoteEntity(
      GitReferencedCatalogEntity gitReferencedCatalogEntity, String branchName, boolean loadFromCache) {
    return (GitReferencedCatalogEntity) gitAwareEntityHelper.fetchEntityFromRemote(gitReferencedCatalogEntity,
        Scope.of(gitReferencedCatalogEntity.getAccountIdentifier(), gitReferencedCatalogEntity.getOrgIdentifier(),
            gitReferencedCatalogEntity.getProjectIdentifier()),
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(gitReferencedCatalogEntity.getConnectorRef())
            .filePath(gitReferencedCatalogEntity.getFilePath())
            .repoName(gitReferencedCatalogEntity.getRepo())
            .entityType(EntityType.IDP_CATALOG)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }
  public List<CatalogEntity> getEntitiesFilters(List<String> parentUniqueId, List<String> kinds, String filter) {
    Query query = new Query();

    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(parentUniqueId));

    List<Criteria> criteria = new ArrayList<>();
    if (!isEmpty(kinds)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kinds));
    }
    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }
    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    List<CatalogEntity> entities = new ArrayList<>();
    try (Stream<CatalogEntity> stream = mongoTemplate.stream(query, CatalogEntity.class)) {
      Iterator<CatalogEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        entities.add(iterator.next());
      }
    }
    return entities;
  }
  public long countByParentUniqueIdAndKindInAndFilter(String parentUniqueId, List<String> kinds, String filter) {
    Query query = new Query();

    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(parentUniqueId));

    List<Criteria> criteria = new ArrayList<>();
    if (!isEmpty(kinds)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kinds));
    }
    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }
    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }
    return mongoTemplate.count(query, CatalogEntity.class);
  }
  boolean shouldRetryWithFallBackBranch(ScmException scmException, String branchTried, String serviceFallbackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(serviceFallbackBranch) && !branchTried.equals(serviceFallbackBranch));
  }

  @Override
  public Page<CatalogEntity> findEnvironmentsByBlueprintIdentifier(String accountIdentifier, String blueprintIdentifier,
      List<String> environmentParentUniqueIds, String searchTerm, List<String> permittedEnvironmentRefs,
      List<ScopeInfo> scopeInfos, Pageable pageable) {
    Query query = new Query();

    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.accountIdentifier).is(accountIdentifier));
    query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.kind).is(ENVIRONMENT_KIND));
    query.addCriteria(Criteria.where("spec.environmentBlueprint.identifier").is(blueprintIdentifier));

    // Scope the environment query to only environments that can reference this blueprint:
    // Account-level blueprint → no filter (all project environments can reference it)
    // Org-level blueprint → parentUniqueIds of all projects under that org
    // Project-level blueprint → parentUniqueId of that specific project
    if (!isEmpty(environmentParentUniqueIds)) {
      query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(environmentParentUniqueIds));
    }

    if (!isEmpty(permittedEnvironmentRefs)) {
      String permittedEntityRefsString = String.join(",", permittedEnvironmentRefs);
      Criteria[] entityRefCriteria = getEntityRefCriteria(permittedEntityRefsString, accountIdentifier, scopeInfos);
      if (entityRefCriteria.length > 0) {
        query.addCriteria(new Criteria().orOperator(entityRefCriteria));
      }
    }

    if (!isEmpty(searchTerm)) {
      query.addCriteria(Criteria.where(CatalogEntity.CatalogKeys.name)
                            .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    query.with(pageable);

    List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);
    long count = mongoTemplate.count(query.skip(-1).limit(-1), CatalogEntity.class);

    return new PageImpl<>(catalogEntities, pageable, count);
  }

  @Override
  public Page<CatalogEntity> findEntitiesByRelationRefs(String accountIdentifier, List<String> entityRefs,
      String searchTerm, List<ScopeInfo> scopeInfos, Pageable pageable, String kind, String type, String owner,
      String lifecycle, String tags, String filter) {
    if (isEmpty(entityRefs)) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();

    String entityRefsString = String.join(",", entityRefs);
    Criteria[] entityRefCriteria = getEntityRefCriteriaV2(entityRefsString, accountIdentifier, scopeInfos);
    if (entityRefCriteria.length > 0) {
      criteria.add(new Criteria().orOperator(entityRefCriteria));
    } else {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    if (!isEmpty(kind)) {
      List<String> kindList = Arrays.asList(kind.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.kind).in(kindList));
    }

    if (!isEmpty(type)) {
      List<String> typeList = Arrays.asList(type.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.type).in(typeList));
    }

    if (!isEmpty(owner)) {
      Criteria ownerCriteria = getOwnerCriteria(owner);
      if (ownerCriteria != null) {
        criteria.add(ownerCriteria);
      }
    }

    if (!isEmpty(lifecycle)) {
      List<String> lifecycleList = Arrays.asList(lifecycle.split(","));
      criteria.add(Criteria.where("spec.lifecycle").in(lifecycleList));
    }

    if (!isEmpty(tags)) {
      List<String> tagsList = Arrays.asList(tags.split(","));
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.tags).in(tagsList));
    }

    if (!isEmpty(filter)) {
      Criteria filterCriteria = getFilterCriteria(filter);
      if (filterCriteria != null) {
        criteria.add(filterCriteria);
      }
    }

    if (!isEmpty(searchTerm)) {
      criteria.add(Criteria.where(CatalogEntity.CatalogKeys.name)
                       .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    if (!criteria.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    query.collation(collation).with(pageable);

    List<CatalogEntity> catalogEntities = mongoTemplate.find(query, CatalogEntity.class);
    long count = mongoTemplate.count(query.skip(-1).limit(-1), CatalogEntity.class);

    return new PageImpl<>(catalogEntities, pageable, count);
  }

  private Criteria getOwnerCriteria(String owner) {
    List<String> ownerList = Arrays.asList(owner.split(","));
    Criteria[] ownerCriteria =
        ownerList.stream()
            .flatMap(ownerField -> {
              if (ownerField.startsWith("group:")) {
                Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifierForUserAndGroup(ownerField);
                if (kindScopeIdentifier != null) {
                  if (kindScopeIdentifier.getLeft() != null && kindScopeIdentifier.getMiddle() != null
                      && kindScopeIdentifier.getRight() != null) {
                    return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                         .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle()
                                             + "/" + kindScopeIdentifier.getRight())));
                  } else {
                    return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                         .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle()
                                             + "/" + kindScopeIdentifier.getRight())),
                        Criteria.where(CatalogEntity.CatalogKeys.owner)
                            .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getRight())),
                        Criteria.where(CatalogEntity.CatalogKeys.owner).is(kindScopeIdentifier.getRight()));
                  }
                } else {
                  return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner).is(ownerField));
                }
              } else if (ownerField.startsWith("user:")) {
                Triple<String, String, String> kindScopeIdentifier = getKindScopeIdentifierForUserAndGroup(ownerField);
                if (kindScopeIdentifier != null) {
                  if (kindScopeIdentifier.getLeft() != null && kindScopeIdentifier.getMiddle() != null
                      && kindScopeIdentifier.getRight() != null) {
                    return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                         .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle()
                                             + "/" + kindScopeIdentifier.getRight())));
                  } else {
                    return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner)
                                         .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getMiddle()
                                             + "/" + kindScopeIdentifier.getRight())),
                        Criteria.where(CatalogEntity.CatalogKeys.owner)
                            .is((kindScopeIdentifier.getLeft() + ":" + kindScopeIdentifier.getRight())),
                        Criteria.where(CatalogEntity.CatalogKeys.owner).is(kindScopeIdentifier.getRight()));
                  }
                } else {
                  return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner).is(ownerField));
                }
              } else {
                return Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner).is(ownerField),
                    Criteria.where(CatalogEntity.CatalogKeys.owner).is("group:" + ownerField));
              }
            })
            .toArray(Criteria[] ::new);
    if (ownerCriteria.length == 0) {
      return null;
    }
    return new Criteria().orOperator(ownerCriteria);
  }

  private Criteria getOwnerCriteriaForGroups(List<String> groupEntityRefs) {
    Criteria[] ownerCriteria =
        groupEntityRefs.stream()
            .flatMap(owner -> Stream.of(Criteria.where(CatalogEntity.CatalogKeys.owner).is(owner)))
            .toArray(Criteria[] ::new);
    if (ownerCriteria.length == 0) {
      return null;
    }
    return new Criteria().orOperator(ownerCriteria);
  }

  private Criteria getFilterCriteria(String filter) {
    List<String> orGroups = Arrays.asList(filter.split("&"));
    List<Criteria> orCriteriaList = new ArrayList<>();

    for (String orGroup : orGroups) {
      List<String> andGroups = Arrays.asList(orGroup.split(","));
      List<Criteria> andCriteriaList = new ArrayList<>();

      for (String andGroup : andGroups) {
        if (andGroup.contains("!=")) {
          String[] keyValue = andGroup.split("!=");
          if (keyValue.length == 2) {
            if (keyValue[1].equals("null")) {
              andCriteriaList.add(Criteria.where(keyValue[0]).ne(null));
            } else {
              andCriteriaList.add(Criteria.where(keyValue[0]).ne(keyValue[1]));
            }
          }
        } else if (andGroup.contains("=")) {
          String[] keyValue = andGroup.split("=");
          if (keyValue.length == 2) {
            if (keyValue[1].equals("null")) {
              andCriteriaList.add(Criteria.where(keyValue[0]).is(null));
            } else {
              andCriteriaList.add(Criteria.where(keyValue[0]).is(keyValue[1]));
            }
          }
        } else {
          andCriteriaList.add(Criteria.where(andGroup).exists(true));
        }
      }

      if (!andCriteriaList.isEmpty()) {
        orCriteriaList.add(new Criteria().andOperator(andCriteriaList.toArray(new Criteria[0])));
      }
    }

    if (!orCriteriaList.isEmpty()) {
      return new Criteria().orOperator(orCriteriaList.toArray(new Criteria[0]));
    }
    return null;
  }

  private Criteria[] getEntityRefCriteriaV2(String entityRefs, String harnessAccount, List<ScopeInfo> scopeInfos) {
    List<String> entityRefList = Arrays.asList(entityRefs.split(","));
    Criteria[] entityRefCriteria =
        entityRefList.stream()
            .map(entityRef -> {
              String[] parts = entityRef.split(":");
              if (parts.length == 2) {
                int slashIndex = parts[1].indexOf("/");

                String scope = slashIndex != -1 ? parts[1].substring(0, slashIndex) : harnessAccount;

                String[] hierarchyScope = scope.split("\\.");
                ScopeInfo scopeInfo = ScopeInfo.builder()
                                          .accountIdentifier(harnessAccount)
                                          .uniqueId(harnessAccount)
                                          .scopeType(ScopeLevel.ACCOUNT)
                                          .build();
                if (hierarchyScope.length == 3) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(
                              scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), hierarchyScope[1]))
                          .filter(scopeInfoScope
                              -> Objects.equals(scopeInfoScope.getProjectIdentifier(), hierarchyScope[2]))
                          .findFirst()
                          .orElse(null);
                } else if (hierarchyScope.length == 2) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(
                              scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), hierarchyScope[1]))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getProjectIdentifier(), null))
                          .findFirst()
                          .orElse(null);
                } else if (hierarchyScope.length == 1 && hierarchyScope[0].equals("account")) {
                  scopeInfo =
                      scopeInfos.stream()
                          .filter(scopeInfoScope -> scopeInfoScope.getAccountIdentifier().equals(harnessAccount))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getOrgIdentifier(), null))
                          .filter(scopeInfoScope -> Objects.equals(scopeInfoScope.getProjectIdentifier(), null))
                          .findFirst()
                          .orElse(null);
                }

                if (scopeInfo != null) {
                  String entityIdentifier = slashIndex != -1 ? parts[1].substring(slashIndex + 1) : parts[1];

                  return new Criteria().andOperator(
                      Criteria.where(CatalogEntity.CatalogKeys.queryableEntityRef)
                          .is(scopeInfo.getUniqueId() + "/" + parts[0] + "/" + entityIdentifier));
                }
              } else if (parts.length == 1) {
                return new Criteria().andOperator(Criteria.where(CatalogEntity.CatalogKeys.queryableEntityRef)
                                                      .is(harnessAccount + "/"
                                                          + "component"
                                                          + "/" + parts[0]));
              }
              return null;
            })
            .filter(Objects::nonNull)
            .toArray(Criteria[] ::new);
    return entityRefCriteria;
  }

  @Override
  public void addContentFile(String accountIdentifier, String uniqueId, String filePath, String label) {
    String fieldPath = "decorator._processed_data.metadata.contentFiles";
    Query query = new Query(Criteria.where("accountIdentifier").is(accountIdentifier).and("uniqueId").is(uniqueId));
    // Single roundtrip: ordered bulk ensures pull executes before push
    BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, CatalogEntity.class);
    bulk.updateOne(query, new Update().pull(fieldPath, new org.bson.Document("path", filePath)));
    bulk.updateOne(query, new Update().push(fieldPath, Map.of("path", filePath, "label", label)));
    bulk.execute();
  }

  @Override
  public long countEntitiesByKindTypeAndScopes(
      String accountIdentifier, List<Pair<String, String>> kindTypePairs, Set<String> parentUniqueIds) {
    List<Criteria> targetCriteria = kindTypePairs.stream()
                                        .map(pair
                                            -> new Criteria().andOperator(Criteria.where("kind").is(pair.getLeft()),
                                                Criteria.where("type").is(pair.getRight())))
                                        .collect(Collectors.toList());

    Criteria criteria = Criteria.where("accountIdentifier")
                            .is(accountIdentifier)
                            .andOperator(new Criteria().orOperator(targetCriteria.toArray(new Criteria[0])));

    if (parentUniqueIds != null && !parentUniqueIds.isEmpty()) {
      criteria = criteria.and("parentUniqueId").in(parentUniqueIds);
    }

    return mongoTemplate.count(new Query(criteria), CatalogEntity.class);
  }

  @Override
  public Page<CatalogEntity> findRootTeamsByPermittedRefs(List<String> parentUniqueIds, Set<String> permittedEntityRefs,
      String searchTerm, Pageable pageable, String harnessAccount, List<ScopeInfo> scopeInfos) {
    if (isEmpty(parentUniqueIds) || isEmpty(permittedEntityRefs)) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    List<Criteria> criteriaList = new ArrayList<>();
    criteriaList.add(Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId).in(parentUniqueIds));
    criteriaList.add(Criteria.where(CatalogEntity.CatalogKeys.kind).is(GROUP_KIND));

    String permittedRefsCommaSeparated = String.join(",", permittedEntityRefs);
    Criteria[] permittedRefCriteria = getEntityRefCriteriaV2(permittedRefsCommaSeparated, harnessAccount, scopeInfos);
    if (permittedRefCriteria.length > 0) {
      criteriaList.add(new Criteria().orOperator(permittedRefCriteria));
    } else {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    if (!isEmpty(searchTerm)) {
      criteriaList.add(Criteria.where(CatalogEntity.CatalogKeys.name)
                           .regex(".*" + CommonUtils.escapeRegexMetacharacters(searchTerm) + ".*", "i"));
    }

    Criteria finalCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

    Query query = new Query(finalCriteria);
    Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    query.collation(collation).with(pageable);

    List<CatalogEntity> entities = mongoTemplate.find(query, CatalogEntity.class);
    long count = mongoTemplate.count(query.skip(-1).limit(-1), CatalogEntity.class);

    return new PageImpl<>(entities, pageable, count);
  }

  @Override
  public List<CatalogEntity> findAllTeamsInScopes(List<String> parentUniqueIds, Boolean custom) {
    if (isEmpty(parentUniqueIds)) {
      return Collections.emptyList();
    }

    Criteria criteria = Criteria.where(CatalogEntity.CatalogKeys.parentUniqueId)
                            .in(parentUniqueIds)
                            .and(CatalogEntity.CatalogKeys.kind)
                            .is(GROUP_KIND);
    if (Boolean.TRUE.equals(custom)) {
      criteria.and(SPEC + "." + IS_CUSTOM_USER_GROUP).exists(true);
    }

    Query query = new Query(criteria);
    return mongoTemplate.find(query, CatalogEntity.class);
  }
}
