/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.common.CommonUtils.escapeRegexMetacharacters;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.repositories.AggregationRuleRepository;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.events.CatalogDecoratorUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.RbacUtils;
import io.harness.idp.iterators.config.IteratorsConfig;
import io.harness.outbox.api.OutboxService;
import io.harness.security.SecurityContextBuilder;
import io.harness.spec.server.idp.v1.model.AggregationAccountSelection;
import io.harness.spec.server.idp.v1.model.AggregationOrgSelection;
import io.harness.spec.server.idp.v1.model.AggregationPlatformSelection;
import io.harness.spec.server.idp.v1.model.AggregationScopeLevel;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewResponse;
import io.harness.springdata.TransactionHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesHelper {
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject OutboxService outboxService;
  @Inject TransactionHelper transactionHelper;
  @Inject AggregationRuleRepository aggregationRuleRepository;
  @Inject @Named("iteratorsConfig") IteratorsConfig iteratorsConfig;
  @Inject AccessControlClient accessControlClient;
  @Inject KindServiceHelper kindServiceHelper;
  @Inject CatalogScopeResolver catalogScopeResolver;

  public Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndCatalogEntitiesPair(
      AggregationRuleEntity aggregationRuleEntity) {
    String accountIdentifier = aggregationRuleEntity.getAccountIdentifier();
    Set<CatalogEntity> catalogEntities = new HashSet<>();
    Page<CatalogEntity> catalogEntitiesPaged;
    String kind = aggregationRuleEntity.getEntitySelectionCriteria().getKind();
    String type = null;
    if (StringUtils.isNotBlank(aggregationRuleEntity.getEntitySelectionCriteria().getType())
        && !aggregationRuleEntity.getEntitySelectionCriteria().getType().equalsIgnoreCase("all")) {
      type = aggregationRuleEntity.getEntitySelectionCriteria().getType().toLowerCase();
    }
    String owner = String.join(",", aggregationRuleEntity.getEntitySelectionCriteria().getOwners());
    String tag = String.join(",", aggregationRuleEntity.getEntitySelectionCriteria().getTags());
    String lifecycle = String.join(",", aggregationRuleEntity.getEntitySelectionCriteria().getLifecycles());
    String scopes = catalogServiceHelper.getAllScopes();
    if (!isEmpty(aggregationRuleEntity.getEntitySelectionCriteria().getScopes())) {
      scopes = String.join(",", aggregationRuleEntity.getEntitySelectionCriteria().getScopes());
    }
    Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfoPairs =
        catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, null);
    int page = 0;
    do {
      catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier, scopeInfoPairs.getLeft(), page,
          1000, null, null, null, null, kind, type, owner, lifecycle, tag, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        catalogEntities.addAll(catalogEntitiesPaged.getContent());
      }
      page++;
    } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

    return Pair.of(scopeInfoPairs.getLeft(), catalogEntities);
  }

  public List<ScopeInfo> findAllOrgScopeInfos(List<ScopeInfo> scopeInfos, AggregationRuleEntity aggregationRuleEntity) {
    boolean containsOrgScopeToAggregateAt =
        aggregationRuleEntity.getScopesToAggregateAt().contains(AggregationRuleEntity.Scope.ORGANIZATION);
    boolean containsProjectScopeToAggregateAt =
        aggregationRuleEntity.getScopesToAggregateAt().contains(AggregationRuleEntity.Scope.PROJECT);
    boolean doesNotContainAllScopes = !isEmpty(aggregationRuleEntity.getEntitySelectionCriteria().getScopes())
        && !aggregationRuleEntity.getEntitySelectionCriteria().getScopes().contains(
            catalogServiceHelper.getAllScopes());
    if ((containsOrgScopeToAggregateAt || containsProjectScopeToAggregateAt) && doesNotContainAllScopes) {
      List<String> projectScopes = aggregationRuleEntity.getEntitySelectionCriteria()
                                       .getScopes()
                                       .stream()
                                       .filter(scope -> scope.chars().filter(ch -> ch == '.').count() == 2)
                                       .toList();
      if (projectScopes.size() == aggregationRuleEntity.getEntitySelectionCriteria().getScopes().size()) {
        String orgScopes = projectScopes.stream()
                               .map(scope -> scope.substring(0, scope.lastIndexOf(".")))
                               .collect(Collectors.joining(","));
        Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> orgScopeInfoPairs =
            catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(
                aggregationRuleEntity.getAccountIdentifier(), orgScopes, null);
        scopeInfos.addAll(orgScopeInfoPairs.getLeft());
      }
    }
    return scopeInfos;
  }

  public List<String> getSystemEntityRefs(String accountIdentifier, List<String> scopesList) {
    List<String> entityRefs = new ArrayList<>();
    StringBuilder scopes = new StringBuilder();
    for (String scope : scopesList) {
      scopes.append(scope).append(',');
    }
    Page<CatalogEntity> catalogEntitiesPaged;
    int page = 0;
    do {
      catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes.toString(), null)
              .getLeft(),
          page, 1000, null, null, null, null, "system", null, null, null, null, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        entityRefs.addAll(
            catalogEntitiesPaged.getContent().stream().map(CatalogUtils::entityRef).collect(Collectors.toSet()));
      }
      page++;
    } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);
    return entityRefs;
  }

  public List<String> getTeamEntityRefs(String accountIdentifier, List<String> scopesList) {
    List<String> entityRefs = new ArrayList<>();
    StringBuilder scopes = new StringBuilder();
    for (String scope : scopesList) {
      scopes.append(scope).append(',');
    }
    Page<CatalogEntity> catalogEntitiesPaged;
    int page = 0;
    do {
      catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier,
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes.toString(), null)
              .getLeft(),
          page, 1000, null, null, null, null, "group", null, null, null, null, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        entityRefs.addAll(
            catalogEntitiesPaged.getContent().stream().map(CatalogUtils::entityRef).collect(Collectors.toSet()));
      }
      page++;
    } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);
    return entityRefs;
  }

  public List<CatalogEntity> getAllSystemEntities(String accountIdentifier, List<ScopeInfo> scopeInfos) {
    List<CatalogEntity> systemEntities = new ArrayList<>();
    Page<CatalogEntity> catalogEntitiesPaged;
    int page = 0;

    do {
      catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier, scopeInfos, page, 1000, null, null,
          null, null, "system", null, null, null, null, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        systemEntities.addAll(catalogEntitiesPaged.getContent());
      }
      page++;
    } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

    log.info("Fetched {} system entities for account {}", systemEntities.size(), accountIdentifier);
    return systemEntities;
  }

  public List<CatalogEntity> getAllTeamEntities(String accountIdentifier, List<ScopeInfo> scopeInfos) {
    List<String> scopeUniqueIds =
        scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().collect(Collectors.toList());
    List<CatalogEntity> teamEntities = catalogEntityRepository.findAllTeamsInScopes(scopeUniqueIds, null);
    log.info("Fetched {} team entities for account {}", teamEntities.size(), accountIdentifier);
    return teamEntities;
  }

  public List<ScopeInfo> getAllAccountScopeInfos(String accountIdentifier) {
    return catalogScopeResolver.resolve(accountIdentifier, catalogServiceHelper.getAllScopes()).getScopeInfos();
  }

  public Function<String, String> getAccountNamespaceResolver(String accountIdentifier) {
    return namespace -> catalogScopeResolver.resolveNamespaceToUniqueId(accountIdentifier, namespace);
  }

  public List<String> getDefaultScopeSelector() {
    return Collections.singletonList(catalogServiceHelper.getAllScopes());
  }

  public Pair<String, String> getOrgProjectFromScope(String scope) {
    return catalogServiceHelper.getOrgProjectFromScope(scope);
  }

  public Set<CatalogEntity> getCatalogEntitiesByRef(
      String accountIdentifier, List<ScopeInfo> scopeInfos, Set<String> entityRefs) {
    String entityRef = String.join(",", entityRefs);
    Set<CatalogEntity> catalogEntities = new HashSet<>();
    Page<CatalogEntity> catalogEntitiesPaged;
    int page = 0;
    do {
      catalogEntitiesPaged = catalogEntityRepository.getEntities(accountIdentifier, scopeInfos, page, 1000, null, null,
          null, entityRef, null, null, null, null, null, null, null);
      if (!isEmpty(catalogEntitiesPaged) && !isEmpty(catalogEntitiesPaged.getContent())) {
        catalogEntities.addAll(catalogEntitiesPaged.getContent());
      }
      page++;
    } while (!isEmpty(catalogEntitiesPaged) && catalogEntitiesPaged.getTotalPages() > page);

    return catalogEntities;
  }

  public List<CatalogEntity> getCatalogEntitiesByParentUniqueIds(List<String> uniqueIds) {
    return catalogEntityRepository.findByParentUniqueIdInAndKind(uniqueIds, HIERARCHY_KIND);
  }

  public void saveAndAuditChanges(Set<CatalogEntity> newCatalogEntities, Set<CatalogEntity> oldCatalogEntities) {
    newCatalogEntities.forEach(
        catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
    transactionHelper.performTransaction(() -> {
      catalogEntityRepository.saveAll(newCatalogEntities);
      auditDecoratorChanges(newCatalogEntities, oldCatalogEntities);
      return null;
    });
  }

  private Map<String, Set<String>> processScopesToOrgProjects(List<String> scopes) {
    if (scopes == null) {
      return Collections.emptyMap();
    }
    Map<String, Set<String>> orgToProjects = new HashMap<>();
    for (String scope : scopes) {
      if (scope == null || scope.isBlank()) {
        continue;
      }
      Pair<String, String> orgProjectPair = getOrgProjectFromScope(scope);
      if (orgProjectPair != null && orgProjectPair.getLeft() != null) {
        String orgId = orgProjectPair.getLeft();
        String projectId = orgProjectPair.getRight();
        Set<String> projectsForOrg = orgToProjects.computeIfAbsent(orgId, k -> new HashSet<>());
        if (projectId != null) {
          projectsForOrg.add(projectId);
        }
      }
    }
    return orgToProjects;
  }

  private List<AggregationOrgSelection> buildOrgSelectionReviewResponse(
      Map<String, Set<String>> orgToProjectsFromFilter, boolean includeOrg, boolean includeProjects) {
    List<AggregationOrgSelection> orgSelections = new ArrayList<>();
    for (Map.Entry<String, Set<String>> orgAndProjects : orgToProjectsFromFilter.entrySet()) {
      AggregationOrgSelection orgSelection =
          new AggregationOrgSelection().identifier(orgAndProjects.getKey()).include(includeOrg);
      if (includeProjects) {
        if (orgAndProjects.getValue().isEmpty()) {
          orgSelection.includeAllProjects(true);
        } else {
          orgSelection.setProjects(new ArrayList<>(orgAndProjects.getValue()));
        }
      }
      orgSelections.add(orgSelection);
    }
    return orgSelections;
  }

  public AggregationSelectionReviewResponse buildAggregationResponse(
      String accountIdentifier, List<String> scopes, List<AggregationScopeLevel> scopesToAggregateAt) {
    Map<String, Set<String>> orgToProjectsFromFilter = processScopesToOrgProjects(scopes);
    boolean includeOrgs =
        scopesToAggregateAt != null && scopesToAggregateAt.contains(AggregationScopeLevel.ORGANIZATION);
    boolean includeProjects =
        scopesToAggregateAt != null && scopesToAggregateAt.contains(AggregationScopeLevel.PROJECT);
    boolean includeAccount = scopesToAggregateAt != null && scopesToAggregateAt.contains(AggregationScopeLevel.ACCOUNT);
    AggregationAccountSelection account = new AggregationAccountSelection().include(includeAccount);
    if (scopes.contains(catalogServiceHelper.getAllScopes())) {
      if (includeOrgs && includeProjects) {
        account.setIncludeAllChildren(AggregationAccountSelection.IncludeAllChildrenEnum.ORGANIZATIONS_AND_PROJECTS);
      } else if (includeOrgs) {
        account.setIncludeAllChildren(AggregationAccountSelection.IncludeAllChildrenEnum.ORGANIZATIONS);
      } else if (includeProjects) {
        account.setIncludeAllChildren(AggregationAccountSelection.IncludeAllChildrenEnum.PROJECTS);
      }
    } else {
      account.setOrgs(buildOrgSelectionReviewResponse(orgToProjectsFromFilter, includeOrgs, includeProjects));
    }
    AggregationPlatformSelection platform = new AggregationPlatformSelection().account(account);
    List<String> systems = scopesToAggregateAt != null && scopesToAggregateAt.contains(AggregationScopeLevel.SYSTEM)
        ? getSystemEntityRefs(accountIdentifier, scopes)
        : Collections.emptyList();
    List<String> teams = scopesToAggregateAt != null && scopesToAggregateAt.contains(AggregationScopeLevel.TEAM)
        ? getTeamEntityRefs(accountIdentifier, scopes)
        : Collections.emptyList();
    return new AggregationSelectionReviewResponse().platform(platform).systems(systems).teams(teams);
  }

  private void auditDecoratorChanges(Set<CatalogEntity> newCatalogEntities, Set<CatalogEntity> oldCatalogEntities) {
    Map<String, CatalogEntity> newCatalogEntitiesMap = newCatalogEntities.stream().collect(Collectors.toMap(
        CatalogEntity::getUniqueId, catalogEntity -> catalogEntity, (existing, duplicate) -> existing));
    Map<String, CatalogEntity> oldCatalogEntitiesMap = oldCatalogEntities.stream().collect(Collectors.toMap(
        CatalogEntity::getUniqueId, catalogEntity -> catalogEntity, (existing, duplicate) -> existing));
    Set<String> newEntitiesUniqueIds =
        newCatalogEntities.stream().map(CatalogEntity::getUniqueId).collect(Collectors.toSet());
    for (String uniqueId : newEntitiesUniqueIds) {
      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(newCatalogEntitiesMap.get(uniqueId).getAccountIdentifier())
                                .orgIdentifier(newCatalogEntitiesMap.get(uniqueId).getOrgIdentifier())
                                .projectIdentifier(newCatalogEntitiesMap.get(uniqueId).getProjectIdentifier())
                                .uniqueId(newCatalogEntitiesMap.get(uniqueId).getParentUniqueId())
                                .scopeType(ScopeLevel.valueOf(newCatalogEntitiesMap.get(uniqueId).getScope()))
                                .build();

      outboxService.save(
          new CatalogDecoratorUpdateEvent(scopeInfo, newCatalogEntitiesMap.get(uniqueId).getFailSafeProcessedData(),
              oldCatalogEntitiesMap.get(uniqueId).getFailSafeProcessedData(),
              newCatalogEntitiesMap.get(uniqueId).getKind(), newCatalogEntitiesMap.get(uniqueId).getIdentifier()));
    }
  }

  public void updateEntity(
      AggregationRuleEntity entity, AggregationRuleEntity.ComputedStatus status, String errorMessage) {
    entity.setStatus(status);
    long currentTime = System.currentTimeMillis();
    entity.setLastComputedAt(currentTime);
    entity.setLastErrorMessage(errorMessage);
    entity.setNextIteration(
        currentTime + (iteratorsConfig.getAggregationRulesComputation().getTargetIntervalInSeconds() * 1000));
    aggregationRuleRepository.save(entity);
  }

  public boolean hasAggregationRulePermission(String accountIdentifier, String ruleIdentifier, String permission) {
    if (RbacUtils.isPureServiceToServiceCall()) {
      return true;
    }

    Set<String> permittedIdentifiers =
        checkAggregationRulePermissions(accountIdentifier, List.of(ruleIdentifier), permission);
    return permittedIdentifiers.contains(ruleIdentifier);
  }

  public Set<String> checkAggregationRulesRbac(String accountIdentifier, String permission, String searchTerm) {
    List<AggregationRuleEntity> aggregationRules;
    if (isNotEmpty(searchTerm)) {
      String escapedSearchTerm = escapeRegexMetacharacters(searchTerm);
      aggregationRules = aggregationRuleRepository.findIdentifiersByAccountIdentifierAndSearchTerm(
          accountIdentifier, escapedSearchTerm);
    } else {
      aggregationRules = aggregationRuleRepository.findIdentifiersByAccountIdentifier(accountIdentifier);
    }

    List<String> allRuleIdentifiers =
        aggregationRules.stream().map(AggregationRuleEntity::getIdentifier).collect(Collectors.toList());
    if (RbacUtils.isPureServiceToServiceCall()) {
      return new HashSet<>(allRuleIdentifiers);
    }
    return checkAggregationRulePermissions(accountIdentifier, allRuleIdentifiers, permission);
  }

  private Set<String> checkAggregationRulePermissions(
      String accountIdentifier, List<String> ruleIdentifiers, String permission) {
    if (isEmpty(ruleIdentifiers)) {
      return new HashSet<>();
    }
    List<PermissionCheckDTO> permissionCheckDTOList =
        ruleIdentifiers.stream()
            .map(identifier
                -> PermissionCheckDTO.builder()
                       .resourceScope(ResourceScope.of(accountIdentifier, null, null))
                       .resourceType(IDP_AGGREGATION_RULE)
                       .resourceIdentifier(identifier)
                       .permission(permission)
                       .build())
            .collect(Collectors.toList());

    List<List<PermissionCheckDTO>> permissionCheckDTOSList = Lists.partition(permissionCheckDTOList, 9999);

    Set<String> permittedRuleIdentifiers = new HashSet<>();
    permissionCheckDTOSList.forEach(permissionCheckDTOSBatch -> {
      AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(
          RbacUtils.fromSecurityPrincipalTypeWithoutModifying(SecurityContextBuilder.getPrincipal().getType()),
          permissionCheckDTOSBatch);

      permittedRuleIdentifiers.addAll(accessCheckResponseDTO.getAccessControlList()
                                          .stream()
                                          .filter(AccessControlDTO::isPermitted)
                                          .map(AccessControlDTO::getResourceIdentifier)
                                          .collect(Collectors.toSet()));
    });

    return permittedRuleIdentifiers;
  }

  public void validateKind(String accountIdentifier, String kind) {
    kindServiceHelper.validateKindIfExist(accountIdentifier, kind);
  }
}
