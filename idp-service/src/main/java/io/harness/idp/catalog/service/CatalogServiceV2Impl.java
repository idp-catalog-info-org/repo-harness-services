/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.favorites.ResourceType.IDPENTITY;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.mappers.ScoreTierMapper;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecardsScores;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceV2Impl {
  private static final String GET_ENTITIES_FLOW_LOG = "[getEntities flow]";
  private final CatalogScopeResolver scopeResolver;
  private final CatalogRbacResolver rbacResolver;
  private final CatalogOrgProjectService orgProjectService;
  private final CatalogServiceHelper catalogServiceHelper;
  private final CatalogEntityRepository catalogEntityRepository;
  private final KindServiceHelper kindServiceHelper;
  private final ScorecardService scorecardService;
  private final ScorecardScoreHelper scorecardScoreHelper;
  private final IDPGitXHelper idpGitXHelper;
  private final HashMap<String, String> mongoReplacementConfig;

  @Inject
  public CatalogServiceV2Impl(CatalogScopeResolver scopeResolver, CatalogRbacResolver rbacResolver,
      CatalogOrgProjectService orgProjectService, CatalogServiceHelper catalogServiceHelper,
      CatalogEntityRepository catalogEntityRepository, KindServiceHelper kindServiceHelper,
      ScorecardService scorecardService, ScorecardScoreHelper scorecardScoreHelper, IDPGitXHelper idpGitXHelper,
      @Named("mongoReplacementConfig") HashMap<String, String> mongoReplacementConfig) {
    this.scopeResolver = scopeResolver;
    this.rbacResolver = rbacResolver;
    this.orgProjectService = orgProjectService;
    this.catalogServiceHelper = catalogServiceHelper;
    this.catalogEntityRepository = catalogEntityRepository;
    this.kindServiceHelper = kindServiceHelper;
    this.scorecardService = scorecardService;
    this.scorecardScoreHelper = scorecardScoreHelper;
    this.idpGitXHelper = idpGitXHelper;
    this.mongoReplacementConfig = mongoReplacementConfig;
  }

  /**
   * Catalog fetch API which returns back a paginated response of entities based on the filters provided
   * if kinds is not provided then all the kinds will be selected - this behaviour is different from v1
   * Primary optimizations are on the scope fetch which is now platform driven rather than the catalog level scopes
   *  {@link CatalogScopeResolver} now does this scope resolution and encapsulate the logic of which scopes exist in the
   * lifecycle of this request. If tomorrow we have to move towards different optimization then we can just change the
   * logic behind it rather than doing changes in this method. Favorites are now optional and can be skipped by setting
   * skipFavorites=true Scorecard computation is now having pre-resolved scopes instead of computing it in the method.
   * This saves re-fetching of the scopes again.
   * @param harnessAccount
   * @param page
   * @param limit
   * @param sort
   * @param searchTerm
   * @param resolvePlaceholders
   * @param scopes
   * @param entityRefs
   * @param ownedByMe
   * @param favorites
   * @param kind
   * @param type
   * @param owner
   * @param lifecycle
   * @param tags
   * @param filter
   * @param includeScorecardsData
   * @param entityRefAndCriteria
   * @param skipFavorites
   * @return
   */
  public GetEntitiesDTO getEntitiesV2(String harnessAccount, Integer page, Integer limit, String sort,
      String searchTerm, boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe,
      Boolean favorites, String kind, String type, String owner, String lifecycle, String tags, String filter,
      boolean includeScorecardsData, boolean entityRefAndCriteria, Boolean skipFavorites) {
    int resolvedPage = page == null ? 0 : page;
    try {
      log.info("{} Entering service account={} page={} limit={} scopes={} entityRefsPresent={} favorites={} "
              + "skipFavorites={} includeScorecardsData={} entityRefAndCriteria={} kind={} filterPresent={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, resolvedPage, limit, scopes, !isEmpty(entityRefs), favorites,
          skipFavorites, includeScorecardsData, entityRefAndCriteria, kind, !isEmpty(filter));

      if (isEmpty(scopes) && isEmpty(entityRefs)) {
        scopes = catalogServiceHelper.getAllScopes();
        log.info("{} No scopes/entityRefs provided. Defaulting scopes={} for account={}", GET_ENTITIES_FLOW_LOG, scopes,
            harnessAccount);
      }

      ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                       .accountIdentifier(harnessAccount)
                                       .scopeType(ScopeLevel.ACCOUNT)
                                       .uniqueId(harnessAccount)
                                       .build();

      CatalogScopeResolver.ScopeResolveResult scopeResult = scopeResolver.resolve(harnessAccount, scopes);
      List<ScopeInfo> scopeInfos = scopeResult.getScopeInfos();
      ScopeTopology topology = scopeResult.getTopology();
      log.info("{} Scope resolution completed account={} requestedScopes={} resolvedScopeInfos={} topologyUniqueIds={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, scopes, scopeInfos.size(),
          topology == null ? 0 : topology.getAllUniqueIds().size());

      List<String> resolvedKinds =
          isEmpty(kind) ? io.harness.idp.catalog.utils.Constants.SUPPORTED_KINDS : List.of(kind.split(","));

      CatalogRbacResolver.RbacResolveResult rbacResult =
          rbacResolver.resolve(harnessAccount, scopeInfos, topology, resolvedKinds);
      Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes = rbacResult.getResourceTypeToPermittedScopes();
      List<String> permittedEntityRefs = rbacResult.getPermittedEntityRefs();
      List<String> permittedEntityRefsForOwnedCount = new ArrayList<>(permittedEntityRefs);

      List<ScopeInfo> allPermittedScopeInfos =
          resourceTypeToPermittedScopes.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());

      log.info("{} RBAC resolution completed account={} requestedScopeInfos={} allPermittedScopeInfos={} "
              + "entityLevelPermittedRefs={} resourceTypes={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, scopeInfos.size(), allPermittedScopeInfos.size(),
          permittedEntityRefs.size(), resourceTypeToPermittedScopes.keySet());

      if (allPermittedScopeInfos.isEmpty()) {
        allPermittedScopeInfos.add(accountScopeInfo);
        log.warn("{} No permitted scopes after RBAC. Falling back to account scope account={}", GET_ENTITIES_FLOW_LOG,
            harnessAccount);
      }

      List<String> permittedGroupEntityRefs =
          rbacResolver.permittedGroupEntityRefs(harnessAccount, scopeInfos, topology, resolvedKinds);

      boolean ownedByMeFilter = ownedByMe != null && ownedByMe;
      if (ownedByMeFilter) {
        owner = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), owner);
        log.info("{} ownedByMe filter resolved owner criteria account={} ownerCriteria={}", GET_ENTITIES_FLOW_LOG,
            harnessAccount, owner);
      }

      boolean shouldSkipFavorites = skipFavorites != null && skipFavorites;
      boolean favoritesFilter = favorites != null && favorites;

      if (shouldSkipFavorites && favoritesFilter) {
        throw new InvalidRequestException("Cannot use skip_favorites=true and favorites=true together. "
            + "skip_favorites skips all favorites computation, which is required for the favorites filter.");
      }

      String userFavoriteEntityRefs = null;
      String permittedFavoriteEntityRefs = null;
      String deniedFavoriteEntityRefs = null;
      if (!shouldSkipFavorites) {
        try {
          userFavoriteEntityRefs = computeFavorites(harnessAccount, scopeInfos, kind);
        } catch (Exception ex) {
          log.warn("{} Failed to compute favorites for account={}. Continuing without favorites. Error={}",
              GET_ENTITIES_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
        }

        if (!isEmpty(userFavoriteEntityRefs)) {
          String[] userFavoriteEntityRefArray = userFavoriteEntityRefs.split(",");
          permittedFavoriteEntityRefs = String.join(",",
              catalogServiceHelper.checkEntityRefsPermission(
                  harnessAccount, Arrays.stream(userFavoriteEntityRefArray).collect(Collectors.toSet()), "view"));
          List<String> deniedFavoriteEntityRefList = new ArrayList<>(Arrays.asList(userFavoriteEntityRefArray));
          if (!isEmpty(permittedFavoriteEntityRefs)) {
            deniedFavoriteEntityRefList.removeAll(Arrays.asList(permittedFavoriteEntityRefs.split(",")));
          }
          deniedFavoriteEntityRefs = String.join(",", deniedFavoriteEntityRefList);
        }
        if (favoritesFilter && isEmpty(permittedFavoriteEntityRefs) && isEmpty(deniedFavoriteEntityRefs)) {
          log.info("{} Favorites filter requested but no favorites resolved for account={}. Returning empty page.",
              GET_ENTITIES_FLOW_LOG, harnessAccount);
          return GetEntitiesDTO.builder()
              .pageNumber(resolvedPage)
              .totalElements(0)
              .entityResponses(Collections.emptyList())
              .totalOwned(0)
              .totalStarred(0)
              .build();
        }
        log.info("{} Favorites resolution completed account={} favoritesRequested={} favoriteRefsPresent={} "
                + "permittedFavoriteEntityRefs={}",
            GET_ENTITIES_FLOW_LOG, harnessAccount, favoritesFilter, !isEmpty(userFavoriteEntityRefs),
            !isEmpty(permittedFavoriteEntityRefs));
      }

      String originalFilter = filter;
      if (!isEmpty(filter) && mongoReplacementConfig != null && !mongoReplacementConfig.isEmpty()) {
        for (Map.Entry<String, String> replacement : mongoReplacementConfig.entrySet()) {
          if (filter.contains(replacement.getKey())) {
            filter = filter.replace(replacement.getKey(), replacement.getValue());
          }
        }
        if (!Objects.equals(originalFilter, filter)) {
          log.info("{} Applied mongo filter replacements account={} originalFilter={} transformedFilter={}",
              GET_ENTITIES_FLOW_LOG, harnessAccount, originalFilter, filter);
        }
      }

      String ownedByMeCriteriaForTotalCount = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), null);

      long totalOwned = 0;
      try {
        totalOwned = catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(harnessAccount,
            resourceTypeToPermittedScopes, permittedEntityRefsForOwnedCount, scopeInfos, kind,
            ownedByMeCriteriaForTotalCount, entityRefs, entityRefAndCriteria, filter);
      } catch (Exception ex) {
        log.warn("{} Failed to compute owned entities count for account={}. Returning 0. Error={}",
            GET_ENTITIES_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
      }

      long totalFavorites = 0;
      if (!shouldSkipFavorites && !isEmpty(userFavoriteEntityRefs)) {
        try {
          totalFavorites = catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(harnessAccount,
              scopeInfos, permittedFavoriteEntityRefs, entityRefs, deniedFavoriteEntityRefs,
              String.join(",", permittedGroupEntityRefs), entityRefAndCriteria, filter);
        } catch (Exception ex) {
          log.warn("{} Failed to compute favorites count for account={}. Returning 0. Error={}", GET_ENTITIES_FLOW_LOG,
              harnessAccount, ex.getMessage(), ex);
        }
      }
      log.info("{} Aggregate counts computed account={} totalOwned={} totalFavorites={} allPermittedScopeInfos={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, totalOwned, totalFavorites, allPermittedScopeInfos.size());

      Page<CatalogEntity> catalogEntitiesPaged;
      if (favoritesFilter) {
        catalogEntitiesPaged = catalogEntityRepository.getFavoritesEntitiesPageWithGroupFallback(harnessAccount,
            scopeInfos, permittedFavoriteEntityRefs, entityRefs, deniedFavoriteEntityRefs,
            String.join(",", permittedGroupEntityRefs), entityRefAndCriteria, filter, page, limit, sort, searchTerm,
            kind, type, owner, lifecycle, tags);
      } else {
        catalogEntitiesPaged = catalogEntityRepository.getEntitiesWithKindScopes(harnessAccount,
            resourceTypeToPermittedScopes, permittedEntityRefs, scopeInfos, page, limit, sort, searchTerm, entityRefs,
            entityRefs, kind, type, owner, lifecycle, tags, filter, entityRefAndCriteria, permittedGroupEntityRefs);
      }

      List<CatalogEntity> catalogEntitiesPagedContent = catalogEntitiesPaged.getContent();
      log.info("{} Repository fetch completed account={} page={} pageSize={} returnedEntities={} totalElements={} "
              + "requestedEntityRefsPresent={} permittedEntityRefs={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, catalogEntitiesPaged.getNumber(), limit,
          catalogEntitiesPagedContent.size(), catalogEntitiesPaged.getTotalElements(), !isEmpty(entityRefs),
          permittedEntityRefs.size());

      Set<String> pageOrgIds = new HashSet<>();
      Map<String, Set<String>> pageProjectsByOrg = new HashMap<>();
      for (CatalogEntity ce : catalogEntitiesPagedContent) {
        if (!isEmpty(ce.getOrgIdentifier())) {
          pageOrgIds.add(ce.getOrgIdentifier());
          if (!isEmpty(ce.getProjectIdentifier())) {
            pageProjectsByOrg.computeIfAbsent(ce.getOrgIdentifier(), k -> new HashSet<>())
                .add(ce.getProjectIdentifier());
          }
        }
      }

      Map<String, String> orgNameMap = orgProjectService.getOrgNames(harnessAccount, pageOrgIds);
      Map<String, String> projectNameMap =
          orgProjectService.getProjectNames(harnessAccount, pageOrgIds, pageProjectsByOrg);
      log.info("{} Org/project enrichment inputs account={} pageOrgIds={} pageProjects={} resolvedOrgNames={} "
              + "resolvedProjectNames={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, pageOrgIds.size(),
          pageProjectsByOrg.values().stream().mapToInt(Set::size).sum(), orgNameMap.size(), projectNameMap.size());

      Map<String, KindEntity> kindEntityMap = new HashMap<>();
      try {
        kindEntityMap.putAll(kindServiceHelper.findByAccountIdentifierIn(harnessAccount)
                                 .stream()
                                 .collect(Collectors.toMap(KindEntity::getIdentifier, entity -> entity)));
      } catch (Exception ex) {
        log.warn("{} Failed to fetch kind metadata account={}. Continuing without kind icons. Error={}",
            GET_ENTITIES_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
      }

      try {
        catalogEntitiesPagedContent =
            catalogServiceHelper.resolveOwner(accountScopeInfo.getUniqueId(), catalogEntitiesPagedContent);
      } catch (Exception ex) {
        log.warn("{} Failed to resolve owner details account={}. Continuing with raw owner data. Error={}",
            GET_ENTITIES_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
      }

      List<String> entitiesKinds = catalogEntitiesPagedContent.stream().map(CatalogEntity::getKind).toList();
      boolean hasCoreKind = entitiesKinds.stream().anyMatch(CORE_KINDS::contains);
      Map<String, List<ScoreEntity>> entityScores = new HashMap<>();
      Map<String, String> scorecardIdToNameMap = new HashMap<>();

      if (!isEmpty(catalogEntitiesPagedContent) && (hasCoreKind || entitiesKinds.stream().anyMatch(GROUP_KIND::equals))
          && includeScorecardsData) {
        try {
          List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
          entityScores = scorecardScoreHelper.fetchScoresForEntities(
              harnessAccount, catalogEntitiesPagedContent, scorecardAndChecks, topology);
          scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
              -> scorecard.getScorecard().getIdentifier(),
              scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
          log.info("{} Scorecard enrichment completed account={} scorecards={} entitiesWithScores={}",
              GET_ENTITIES_FLOW_LOG, harnessAccount, scorecardAndChecks.size(), entityScores.size());
        } catch (Exception ex) {
          log.warn("{} Failed to enrich entities with scorecards account={}. Continuing without scorecard data. "
                  + "Error={}",
              GET_ENTITIES_FLOW_LOG, harnessAccount, ex.getMessage(), ex);
          entityScores = new HashMap<>();
          scorecardIdToNameMap = new HashMap<>();
        }
      }

      List<EntityResponse> entityResponses = new ArrayList<>();
      int skippedCount = 0;
      for (CatalogEntity catalogEntity : catalogEntitiesPagedContent) {
        try {
          List<ScoreEntity> scoreEntities = entityScores.get(CatalogUtils.entityRef(catalogEntity));
          String orgName =
              !isEmpty(catalogEntity.getOrgIdentifier()) ? orgNameMap.get(catalogEntity.getOrgIdentifier()) : null;
          String projName = !isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
              ? projectNameMap.get(catalogEntity.getOrgIdentifier() + ":" + catalogEntity.getProjectIdentifier())
              : null;

          if (!isEmpty(catalogEntity.getOrgIdentifier()) && isEmpty(orgName)) {
            skippedCount++;
            log.warn("{} Skipping entity due to unresolved org name account={} orgId={} entityRef={} queryableRef={}",
                GET_ENTITIES_FLOW_LOG, harnessAccount, catalogEntity.getOrgIdentifier(),
                CatalogUtils.entityRef(catalogEntity), catalogEntity.getQueryableEntityRef());
            continue;
          }
          if (!isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
              && (isEmpty(orgName) || isEmpty(projName))) {
            skippedCount++;
            log.warn("{} Skipping entity due to unresolved org/project name account={} orgId={} projectId={} "
                    + "entityRef={} queryableRef={} orgResolved={} projectResolved={}",
                GET_ENTITIES_FLOW_LOG, harnessAccount, catalogEntity.getOrgIdentifier(),
                catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity),
                catalogEntity.getQueryableEntityRef(), !isEmpty(orgName), !isEmpty(projName));
            continue;
          }

          EntityResponse entityResponse = CatalogMapper.entityToResponse(catalogEntity, orgName, projName,
              userFavoriteEntityRefs,
              kindEntityMap.containsKey(catalogEntity.getKind()) ? kindEntityMap.get(catalogEntity.getKind()).getIcon()
                                                                 : null,
              constructEntityScorecards(scoreEntities, scorecardIdToNameMap), resolvePlaceholders);
          try {
            entityResponse.setGitDetails(idpGitXHelper.getEntityDetails(catalogEntity));
          } catch (Exception ex) {
            log.warn("{} Failed to enrich git details for entity account={} entityRef={}. Continuing without git "
                    + "details. Error={}",
                GET_ENTITIES_FLOW_LOG, harnessAccount, CatalogUtils.entityRef(catalogEntity), ex.getMessage(), ex);
          }
          entityResponses.add(entityResponse);
        } catch (Exception ex) {
          skippedCount++;
          log.warn("{} Failed to map entity account={} entityRef={}. Skipping entity. Error={}", GET_ENTITIES_FLOW_LOG,
              harnessAccount, CatalogUtils.entityRef(catalogEntity), ex.getMessage(), ex);
        }
      }

      if (skippedCount > 0) {
        log.warn("{} Skipped {} out of {} entities during response enrichment for account={}. This affects "
                + "pagination accuracy.",
            GET_ENTITIES_FLOW_LOG, skippedCount, catalogEntitiesPagedContent.size(), harnessAccount);
      }

      long actualTotalElements = catalogEntitiesPaged.getTotalElements() - skippedCount;
      log.info("{} Completed service account={} page={} returnedEntities={} actualTotalElements={} skipped={} "
              + "totalOwned={} totalFavorites={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, catalogEntitiesPaged.getNumber(), entityResponses.size(),
          actualTotalElements, skippedCount, totalOwned, totalFavorites);

      return GetEntitiesDTO.builder()
          .pageNumber(catalogEntitiesPaged.getNumber())
          .totalElements(actualTotalElements)
          .entityResponses(entityResponses)
          .totalOwned(totalOwned)
          .totalStarred(totalFavorites)
          .build();
    } catch (InvalidRequestException ex) {
      log.error("{} Invalid request while fetching entities account={} page={} limit={}. Error={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, resolvedPage, limit, ex.getMessage(), ex);
      throw ex;
    } catch (Exception ex) {
      log.error("{} Unexpected error in get entities V2 account={} page={} limit={}. Error={}", GET_ENTITIES_FLOW_LOG,
          harnessAccount, resolvedPage, limit, ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private String computeFavorites(String harnessAccount, List<ScopeInfo> scopeInfos, String kind) {
    String userFavoriteEntityRefs = null;
    if (scopeInfos.stream().anyMatch(si -> si.getScopeType().equals(ScopeLevel.ACCOUNT))) {
      try {
        String acctFavs = catalogServiceHelper.getUserFavoriteEntityRefs(harnessAccount, null, null, IDPENTITY.name());
        if (!isEmpty(acctFavs)) {
          userFavoriteEntityRefs = acctFavs;
        }
      } catch (Exception ex) {
        log.warn("Failed to fetch account-level favorites for account={}. Error={}", harnessAccount, ex.getMessage());
      }
    }

    Set<String> orgIds = scopeInfos.stream()
                             .filter(si -> si.getScopeType().equals(ScopeLevel.ORGANIZATION))
                             .map(ScopeInfo::getOrgIdentifier)
                             .collect(Collectors.toSet());
    if (!orgIds.isEmpty()) {
      try {
        String orgFavs = catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(
            harnessAccount, new ArrayList<>(orgIds), IDPENTITY.name());
        if (!isEmpty(orgFavs)) {
          userFavoriteEntityRefs = isEmpty(userFavoriteEntityRefs) ? orgFavs : userFavoriteEntityRefs + "," + orgFavs;
        }
      } catch (Exception ex) {
        log.warn("Failed to fetch org-level favorites for account={}, orgs={}. Error={}", harnessAccount, orgIds,
            ex.getMessage());
      }
    }

    List<ScopeInfo> projectScopes =
        scopeInfos.stream().filter(si -> si.getScopeType().equals(ScopeLevel.PROJECT)).collect(Collectors.toList());
    if (!projectScopes.isEmpty()) {
      try {
        String orgProjects = projectScopes.stream()
                                 .map(si -> si.getOrgIdentifier() + "." + si.getProjectIdentifier())
                                 .collect(Collectors.joining(","));
        String projFavs =
            catalogServiceHelper.getUserFavoriteEntityRefsForProjects(harnessAccount, orgProjects, IDPENTITY.name());
        if (!isEmpty(projFavs)) {
          userFavoriteEntityRefs = isEmpty(userFavoriteEntityRefs) ? projFavs : userFavoriteEntityRefs + "," + projFavs;
        }
      } catch (Exception ex) {
        log.warn("Failed to fetch project-level favorites for account={}. Error={}", harnessAccount, ex.getMessage());
      }
    }

    Set<String> kinds = !isEmpty(kind) ? new HashSet<>(Arrays.asList(kind.split(","))) : new HashSet<>();
    if (!isEmpty(kinds) && !isEmpty(userFavoriteEntityRefs)) {
      List<String> filteredByKind = new ArrayList<>();
      for (String ref : userFavoriteEntityRefs.split(",")) {
        String[] parts = ref.split(":", 2);
        if (parts.length == 2 && kinds.contains(parts[0])) {
          filteredByKind.add(ref);
        }
      }
      userFavoriteEntityRefs = String.join(",", filteredByKind);
    }

    return userFavoriteEntityRefs;
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
              ScoreTierMapper.fromScoreEntity(scoreEntity).ifPresent(entityResponseScorecardsScores::setTier);
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
}
