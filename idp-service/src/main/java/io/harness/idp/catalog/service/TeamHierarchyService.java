/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.OWNER_OF;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.TeamHierarchyResult;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.graph.utils.EntityRefResolver;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.mapper.TeamHierarchyMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.strategy.TeamHierarchyAclStrategy;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecardsScores;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class TeamHierarchyService {
  private static final String TEAM_HIERARCHY_FLOW_LOG = "[teamHierarchy flow]";
  private static final String VIEW_PERMISSION = "view";

  private final CatalogScopeResolver catalogScopeResolver;
  private final CatalogOrgProjectService orgProjectService;
  private final CatalogServiceHelper catalogServiceHelper;
  private final CatalogEntityRepository catalogEntityRepository;
  private final KindServiceHelper kindServiceHelper;
  private final TeamHierarchyAclStrategy aclStrategy;
  private final ScorecardService scorecardService;
  private final ScorecardScoreHelper scorecardScoreHelper;

  @Inject
  public TeamHierarchyService(CatalogScopeResolver catalogScopeResolver, CatalogOrgProjectService orgProjectService,
      CatalogServiceHelper catalogServiceHelper, CatalogEntityRepository catalogEntityRepository,
      KindServiceHelper kindServiceHelper, TeamHierarchyAclStrategy aclStrategy, ScorecardService scorecardService,
      ScorecardScoreHelper scorecardScoreHelper) {
    this.catalogScopeResolver = catalogScopeResolver;
    this.orgProjectService = orgProjectService;
    this.catalogServiceHelper = catalogServiceHelper;
    this.catalogEntityRepository = catalogEntityRepository;
    this.kindServiceHelper = kindServiceHelper;
    this.aclStrategy = aclStrategy;
    this.scorecardService = scorecardService;
    this.scorecardScoreHelper = scorecardScoreHelper;
  }

  public TeamHierarchyResult getTeamHierarchy(String harnessAccount, String scopes, Boolean includeChildScopes,
      Integer page, Integer limit, String sort, String searchTerm, Boolean custom) {
    log.info("{} Received team hierarchy request account={} scopes={} includeChildScopes={} page={} limit={} "
            + "sort={} searchTerm={}",
        TEAM_HIERARCHY_FLOW_LOG, harnessAccount, scopes, includeChildScopes, page, limit, sort, searchTerm);
    if (isEmpty(scopes)) {
      scopes = catalogServiceHelper.getAllScopes();
      log.info("{} No scopes provided. Defaulting scopes={} for account={}", TEAM_HIERARCHY_FLOW_LOG, scopes,
          harnessAccount);
    }
    CatalogScopeResolver.ScopeResolveResult givenScopeResolveResult =
        catalogScopeResolver.resolve(harnessAccount, scopes);
    Set<String> givenScopeUniqueIds =
        givenScopeResolveResult.getScopeInfos().stream().map(ScopeInfo::getUniqueId).collect(Collectors.toSet());

    String finalScopes = catalogServiceHelper.includeChildScopesIfApplicable(scopes, includeChildScopes);
    CatalogScopeResolver.ScopeResolveResult finalScopeResolveResult =
        catalogScopeResolver.resolve(harnessAccount, finalScopes);
    ScopeTopology finalScopeTopology = finalScopeResolveResult.getTopology();
    List<String> finalScopeUniqueIds =
        finalScopeResolveResult.getScopeInfos().stream().map(ScopeInfo::getUniqueId).toList();
    List<CatalogEntity> allTeams = catalogEntityRepository.findAllTeamsInScopes(finalScopeUniqueIds, custom);
    Set<String> allRefs = allTeams.stream().map(CatalogUtils::entityRef).collect(Collectors.toSet());
    Set<String> permittedRefs =
        catalogServiceHelper.checkEntityRefsPermission(harnessAccount, allRefs, VIEW_PERMISSION);

    Map<String, List<CatalogEntity>> childrenByParentRef = buildChildrenByParentRefMap(allTeams, finalScopeTopology);
    List<CatalogEntity> roots =
        allTeams.stream()
            .filter(team -> givenScopeUniqueIds.contains(team.getParentUniqueId()) && isVisibleRoot(team, allRefs))
            .collect(Collectors.toList());

    Set<String> visibleRootRefs = aclStrategy.visibleRootRefs(roots, childrenByParentRef, permittedRefs);

    log.info("{} Resolved teams account={} allScopes={} teamsInScope={} roots={} permitted={} visibleRoots={}",
        TEAM_HIERARCHY_FLOW_LOG, harnessAccount, String.join(",", finalScopeUniqueIds), allTeams.size(), roots.size(),
        permittedRefs.size(), visibleRootRefs.size());

    Pageable pageable = buildPageable(page, limit, sort);
    Page<CatalogEntity> rootPage = catalogEntityRepository.findRootTeamsByPermittedRefs(finalScopeUniqueIds,
        visibleRootRefs, searchTerm, pageable, harnessAccount, finalScopeResolveResult.getScopeInfos());

    if (rootPage.isEmpty()) {
      log.info("{} No visible roots on requested page. Returning empty result.", TEAM_HIERARCHY_FLOW_LOG);
      return TeamHierarchyResult.builder()
          .nodes(Collections.emptyList())
          .pageNumber(rootPage.getNumber())
          .pageSize(pageable.getPageSize())
          .totalElements(rootPage.getTotalElements())
          .build();
    }

    EnrichmentContext enrichmentContext = buildEnrichmentContext(harnessAccount, allTeams);
    List<TeamHierarchyNode> nodes = aclStrategy.assembleTree(rootPage.getContent(), childrenByParentRef, permittedRefs,
        (entity, children) -> buildNode(entity, enrichmentContext, children));

    log.info("{} Completed team hierarchy request account={} rootNodes={} totalVisibleRoots={}",
        TEAM_HIERARCHY_FLOW_LOG, harnessAccount, nodes.size(), rootPage.getTotalElements());

    return TeamHierarchyResult.builder()
        .nodes(nodes)
        .pageNumber(rootPage.getNumber())
        .pageSize(pageable.getPageSize())
        .totalElements(rootPage.getTotalElements())
        .build();
  }

  public GetEntitiesDTO getTeamOwnedEntities(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, boolean includeChildTeams, Integer page, Integer limit, String sort, String searchTerm) {
    try {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = catalogServiceHelper.validateAndSanitizeKind(kindScopeIdentifier.getLeft());
      String identifier = catalogServiceHelper.validateAndSanitizeIdentifier(kindScopeIdentifier.getRight());
      String orgIdentifierFromScope, projectIdentifierFromScope;
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }

      catalogServiceHelper.checkCrudRbac(harnessAccount, orgIdentifier, projectIdentifier, kind, entityRef, "view");
      Pageable pageable = buildPageable(page, limit, sort);

      String finalScopes = catalogServiceHelper.includeChildScopesIfApplicable(scope, includeChildTeams);
      CatalogScopeResolver.ScopeResolveResult resolveResult = catalogScopeResolver.resolve(harnessAccount, finalScopes);
      List<ScopeInfo> scopeInfos = resolveResult.getScopeInfos();
      ScopeTopology topology = resolveResult.getTopology();

      Map<String, List<String>> entityRefsByTeamRef =
          resolveTeamOwnedEntities(harnessAccount, scope, identifier, includeChildTeams, scopeInfos);
      if (isEmpty(entityRefsByTeamRef)) {
        return GetEntitiesDTO.builder()
            .pageNumber(pageable.getPageNumber())
            .totalElements(0)
            .entityResponses(Collections.emptyList())
            .build();
      }

      Set<String> allRefs = new HashSet<>(entityRefsByTeamRef.keySet());
      entityRefsByTeamRef.values().forEach(allRefs::addAll);
      Set<String> permittedRefs =
          catalogServiceHelper.checkEntityRefsPermission(harnessAccount, allRefs, VIEW_PERMISSION);

      Set<String> accessibleEntityRefs = new HashSet<>();
      for (Map.Entry<String, List<String>> entry : entityRefsByTeamRef.entrySet()) {
        boolean teamViewable = permittedRefs.contains(entry.getKey());
        for (String ownedEntityRef : entry.getValue()) {
          boolean viewableViaTeam = teamViewable
              && catalogServiceHelper.isInheritableKind(
                  catalogServiceHelper.getKindScopeIdentifier(ownedEntityRef).getLeft());
          if (viewableViaTeam || permittedRefs.contains(ownedEntityRef)) {
            accessibleEntityRefs.add(ownedEntityRef);
          }
        }
      }

      if (isEmpty(accessibleEntityRefs)) {
        return GetEntitiesDTO.builder()
            .pageNumber(pageable.getPageNumber())
            .totalElements(0)
            .entityResponses(Collections.emptyList())
            .build();
      }

      Page<CatalogEntity> ownedPage =
          catalogEntityRepository.getEntities(harnessAccount, scopeInfos, page, pageable.getPageSize(), sort,
              searchTerm, null, String.join(",", accessibleEntityRefs), null, null, null, null, null, null, null);

      Set<String> orgIds = ownedPage.getContent()
                               .stream()
                               .map(CatalogEntity::getOrgIdentifier)
                               .filter(o -> !isEmpty(o))
                               .collect(Collectors.toSet());
      Map<String, Set<String>> projectsByOrg = new HashMap<>();
      for (CatalogEntity e : ownedPage.getContent()) {
        if (!isEmpty(e.getOrgIdentifier()) && !isEmpty(e.getProjectIdentifier())) {
          projectsByOrg.computeIfAbsent(e.getOrgIdentifier(), k -> new HashSet<>()).add(e.getProjectIdentifier());
        }
      }
      Map<String, String> orgNameMap = orgProjectService.getOrgNames(harnessAccount, orgIds);
      Map<String, String> projectNameMap = orgProjectService.getProjectNames(harnessAccount, orgIds, projectsByOrg);

      List<CatalogEntity> ownedEntities = ownedPage.getContent();
      Map<String, KindEntity> kindEntityMap = new HashMap<>();
      try {
        kindEntityMap.putAll(kindServiceHelper.findByAccountIdentifierIn(harnessAccount)
                                 .stream()
                                 .collect(Collectors.toMap(KindEntity::getIdentifier, kindEntity -> kindEntity)));
      } catch (Exception ex) {
        log.warn("Failed to fetch kind metadata account={}. Continuing without kind icons. Error={}", harnessAccount,
            ex.getMessage(), ex);
      }

      List<String> entitiesKinds = ownedEntities.stream().map(CatalogEntity::getKind).toList();
      boolean hasCoreKind = entitiesKinds.stream().anyMatch(CORE_KINDS::contains);
      Map<String, List<ScoreEntity>> entityScores = new HashMap<>();
      Map<String, String> scorecardIdToNameMap = new HashMap<>();
      if (!isEmpty(ownedEntities) && hasCoreKind) {
        try {
          List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
          entityScores =
              scorecardScoreHelper.fetchScoresForEntities(harnessAccount, ownedEntities, scorecardAndChecks, topology);
          scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
              -> scorecard.getScorecard().getIdentifier(),
              scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
        } catch (Exception ex) {
          log.warn("Failed to enrich team owned entities with scorecards account={}. Continuing without scorecard "
                  + "data. Error={}",
              harnessAccount, ex.getMessage(), ex);
          entityScores = new HashMap<>();
          scorecardIdToNameMap = new HashMap<>();
        }
      }

      List<EntityResponse> entityResponses = new ArrayList<>();
      for (CatalogEntity entity : ownedEntities) {
        String orgName = !isEmpty(entity.getOrgIdentifier()) ? orgNameMap.get(entity.getOrgIdentifier()) : null;
        String projectName = !isEmpty(entity.getOrgIdentifier()) && !isEmpty(entity.getProjectIdentifier())
            ? projectNameMap.get(entity.getOrgIdentifier() + ":" + entity.getProjectIdentifier())
            : null;
        List<ScoreEntity> scoreEntities = entityScores.get(CatalogUtils.entityRef(entity));
        String kindIcon =
            kindEntityMap.containsKey(entity.getKind()) ? kindEntityMap.get(entity.getKind()).getIcon() : null;
        EntityResponse resp = CatalogMapper.entityToResponse(entity, orgName, projectName, null, kindIcon,
            constructEntityScorecards(scoreEntities, scorecardIdToNameMap), false);
        entityResponses.add(resp);
      }

      return GetEntitiesDTO.builder()
          .pageNumber(ownedPage.getNumber())
          .totalElements(ownedPage.getTotalElements())
          .entityResponses(entityResponses)
          .build();
    } catch (NGAccessDeniedException e) {
      throw e;
    } catch (Exception ex) {
      log.error("Error in get team owned entities. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public Map<String, List<String>> resolveTeamOwnedEntities(
      String harnessAccount, String scope, String identifier, boolean includeChildTeams, List<ScopeInfo> scopeInfos) {
    String[] scopeSplit = scope.split("\\.");
    String orgIdentifier = scopeSplit.length >= 2 ? scopeSplit[1] : null;
    String projectIdentifier = scopeSplit.length == 3 ? scopeSplit[2] : null;
    List<String> scopeUniqueIds = scopeInfos.stream().map(ScopeInfo::getUniqueId).toList();
    List<CatalogEntity> allTeams = catalogEntityRepository.findAllTeamsInScopes(scopeUniqueIds, null);

    CatalogEntity team =
        allTeams.stream()
            .filter(t
                -> identifier.equals(t.getIdentifier()) && Objects.equals(orgIdentifier, t.getOrgIdentifier())
                    && Objects.equals(projectIdentifier, t.getProjectIdentifier()))
            .findFirst()
            .orElse(null);
    if (team == null) {
      return Collections.emptyMap();
    }

    Map<String, CatalogEntity> teamByRef =
        allTeams.stream().collect(Collectors.toMap(CatalogUtils::entityRef, t -> t, (a, b) -> a));

    Set<String> teamRefs = new HashSet<>();
    String teamRef = CatalogUtils.entityRef(team);
    teamRefs.add(teamRef);
    if (includeChildTeams) {
      ScopeTopology topology = catalogScopeResolver.resolve(harnessAccount, scope).getTopology();
      Map<String, List<CatalogEntity>> childrenByParentRef = buildChildrenByParentRefMap(allTeams, topology);
      collectDescendantRefs(teamRef, childrenByParentRef, teamRefs, 0);
    }

    Map<String, List<String>> entityRefsByTeamRef = new HashMap<>();
    for (String ref : teamRefs) {
      CatalogEntity t = teamByRef.get(ref);
      Set<String> ownedRefs = t == null ? Collections.emptySet() : t.getRelationsFor(OWNER_OF);
      entityRefsByTeamRef.put(ref, ownedRefs == null ? new ArrayList<>() : new ArrayList<>(ownedRefs));
    }
    return entityRefsByTeamRef;
  }

  private void collectDescendantRefs(
      String parentRef, Map<String, List<CatalogEntity>> childrenByParentRef, Set<String> collected, int depth) {
    if (depth >= TeamHierarchyAclStrategy.MAX_TREE_DEPTH) {
      log.warn("{} Exceeded max depth={} while collecting subteams under {}. Truncating.", TEAM_HIERARCHY_FLOW_LOG,
          TeamHierarchyAclStrategy.MAX_TREE_DEPTH, parentRef);
      return;
    }
    for (CatalogEntity child : childrenByParentRef.getOrDefault(parentRef, Collections.emptyList())) {
      String childRef = CatalogUtils.entityRef(child);
      if (collected.add(childRef)) {
        collectDescendantRefs(childRef, childrenByParentRef, collected, depth + 1);
      }
    }
  }

  private Map<String, List<CatalogEntity>> buildChildrenByParentRefMap(
      List<CatalogEntity> allTeams, ScopeTopology topology) {
    Map<String, CatalogEntity> lookupKeyToEntity = new HashMap<>();
    for (CatalogEntity team : allTeams) {
      String lookupKey = team.getParentUniqueId() + "|" + team.getKind().toLowerCase() + "|" + team.getIdentifier();
      lookupKeyToEntity.put(lookupKey, team);
    }

    Map<String, List<CatalogEntity>> childrenByParentRef = new HashMap<>();
    for (CatalogEntity child : allTeams) {
      Set<String> childOfRefs = child.getRelationsFor(CHILD_OF);
      if (isEmpty(childOfRefs)) {
        continue;
      }
      for (String childOfRef : childOfRefs) {
        Optional<EntityRefResolver.ScopedEntityLookup> lookupOpt =
            EntityRefResolver.parseRelationRefToLookup(childOfRef, topology::resolveNamespaceToUniqueId);
        if (lookupOpt.isEmpty()) {
          continue;
        }
        EntityRefResolver.ScopedEntityLookup lookup = lookupOpt.get();
        String lookupKey = lookup.parentUniqueId + "|" + lookup.kind.toLowerCase() + "|" + lookup.identifier;
        CatalogEntity parent = lookupKeyToEntity.get(lookupKey);
        if (parent != null) {
          String parentRef = CatalogUtils.entityRef(parent);
          childrenByParentRef.computeIfAbsent(parentRef, key -> new ArrayList<>()).add(child);
          break;
        }
      }
    }
    return childrenByParentRef;
  }

  private boolean isVisibleRoot(CatalogEntity team, Set<String> refsInScope) {
    Set<String> parents = team.getRelationsFor(CHILD_OF);
    if (isEmpty(parents)) {
      return true;
    }
    return parents.stream().noneMatch(refsInScope::contains);
  }

  private TeamHierarchyNode buildNode(
      CatalogEntity entity, EnrichmentContext context, List<TeamHierarchyNode> children) {
    String orgName = !isEmpty(entity.getOrgIdentifier()) ? context.orgNameMap.get(entity.getOrgIdentifier()) : null;
    String projectName = !isEmpty(entity.getOrgIdentifier()) && !isEmpty(entity.getProjectIdentifier())
        ? context.projectNameMap.get(entity.getOrgIdentifier() + ":" + entity.getProjectIdentifier())
        : null;
    KindEntity kindEntity = context.kindEntityMap.get(entity.getKind());
    String kindIcon = kindEntity != null ? kindEntity.getIcon() : null;

    return TeamHierarchyMapper.toNode(entity, orgName, projectName, kindIcon, children);
  }

  private EnrichmentContext buildEnrichmentContext(String harnessAccount, List<CatalogEntity> entities) {
    Set<String> orgIds = new HashSet<>();
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    for (CatalogEntity entity : entities) {
      if (!isEmpty(entity.getOrgIdentifier())) {
        orgIds.add(entity.getOrgIdentifier());
        if (!isEmpty(entity.getProjectIdentifier())) {
          projectsByOrg.computeIfAbsent(entity.getOrgIdentifier(), key -> new HashSet<>())
              .add(entity.getProjectIdentifier());
        }
      }
    }

    Map<String, String> orgNameMap = orgProjectService.getOrgNames(harnessAccount, orgIds);
    Map<String, String> projectNameMap = orgProjectService.getProjectNames(harnessAccount, orgIds, projectsByOrg);

    Map<String, KindEntity> kindEntityMap = new HashMap<>();
    try {
      kindEntityMap.putAll(kindServiceHelper.findByAccountIdentifierIn(harnessAccount)
                               .stream()
                               .collect(Collectors.toMap(KindEntity::getIdentifier, kindEntity -> kindEntity)));
    } catch (Exception ex) {
      log.warn("{} Failed to fetch kind metadata account={}. Continuing without kind icons. Error={}",
          TEAM_HIERARCHY_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
    }

    return new EnrichmentContext(orgNameMap, projectNameMap, kindEntityMap);
  }

  private EntityResponseScorecards constructEntityScorecards(
      List<ScoreEntity> scoreEntities, Map<String, String> scorecardIdToNameMap) {
    if (isEmpty(scoreEntities)) {
      return new EntityResponseScorecards();
    }

    List<EntityResponseScorecardsScores> scores =
        scoreEntities.stream()
            .filter(Objects::nonNull)
            .map(scoreEntity -> {
              EntityResponseScorecardsScores entityResponseScorecardsScores = new EntityResponseScorecardsScores();
              entityResponseScorecardsScores.setScorecard(scoreEntity.getScorecardIdentifier());
              entityResponseScorecardsScores.setScorecardName(scorecardIdToNameMap.getOrDefault(
                  scoreEntity.getScorecardIdentifier(), scoreEntity.getScorecardIdentifier()));
              entityResponseScorecardsScores.setScore(BigDecimal.valueOf(scoreEntity.getScore()));
              entityResponseScorecardsScores.setTotalChecks(BigDecimal.valueOf(
                  Optional.ofNullable(scoreEntity.getCheckStatus()).orElse(Collections.emptyList()).size()));
              entityResponseScorecardsScores.setPassedChecks(BigDecimal.valueOf(
                  Optional.ofNullable(scoreEntity.getCheckStatus())
                      .orElse(Collections.emptyList())
                      .stream()
                      .filter(checkStatus -> checkStatus.getStatus().equals(CheckStatus.StatusEnum.PASS))
                      .toList()
                      .size()));
              return entityResponseScorecardsScores;
            })
            .collect(Collectors.toList());
    EntityResponseScorecards scorecards = new EntityResponseScorecards();
    scorecards.setScores(scores);
    if (!scores.isEmpty()) {
      double averageScore =
          scoreEntities.stream().filter(Objects::nonNull).mapToInt(ScoreEntity::getScore).average().orElse(0);
      scorecards.setAverage(BigDecimal.valueOf(averageScore).setScale(0, RoundingMode.HALF_UP));
    }
    return scorecards;
  }

  private Pageable buildPageable(Integer page, Integer limit, String sort) {
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
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    return PageRequest.of(pageIndex, pageLimit, sortObj);
  }

  private static class EnrichmentContext {
    private final Map<String, String> orgNameMap;
    private final Map<String, String> projectNameMap;
    private final Map<String, KindEntity> kindEntityMap;

    EnrichmentContext(
        Map<String, String> orgNameMap, Map<String, String> projectNameMap, Map<String, KindEntity> kindEntityMap) {
      this.orgNameMap = orgNameMap;
      this.projectNameMap = projectNameMap;
      this.kindEntityMap = kindEntityMap;
    }
  }
}
