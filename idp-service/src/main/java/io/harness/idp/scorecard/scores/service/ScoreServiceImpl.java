/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.common.Constants.BACKSTAGE_KINDS;
import static io.harness.idp.common.JacksonUtils.readValue;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.EntityNotFoundException;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.cache.CatalogScopeTopologyCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ApplicabilityEngine;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.cache.FailureSummaryService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.mappers.ScorecardGraphSummaryInfoMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardScoreMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardSummaryInfoMapper;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifierEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityScores;
import io.harness.spec.server.idp.v1.model.EvaluationData;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DuplicateKeyException;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScoreServiceImpl implements ScoreService {
  @Inject TransactionHelper transactionHelper;
  @Inject CheckRepository checkRepository;
  @Inject DataPointsRepository datapointRepository;
  @Inject DataSourceRepository datasourceRepository;
  @Inject DataSourceLocationRepository datasourceLocationRepository;
  @Inject ScoreComputerService scoreComputerService;
  @Inject NamespaceService namespaceService;
  @Inject IdpCommonService idpCommonService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject ScopeInfoClient scopeInfoClient;
  ScorecardService scorecardService;
  ScoreRepository scoreRepository;
  AsyncScoreComputationService asyncScoreComputationService;
  @Inject private FailureSummaryService failureSummaryService;
  @Inject private ApplicabilityEngine applicabilityEngine;
  @Inject private CatalogScopeTopologyCache scopeTopologyCache;
  @Inject @com.google.inject.name.Named("ScorecardSummaryExecutor") private ExecutorService scorecardSummaryExecutor;

  @Override
  public void populateData(
      String checkEntities, String datapointEntities, String datasourceEntities, String datasourceLocationEntities) {
    List<CheckEntity> checks = readValue(checkEntities, CheckEntity.class);
    List<DataPointEntity> dataPoints = readValue(datapointEntities, DataPointEntity.class);
    List<DataSourceEntity> dataSources = readValue(datasourceEntities, DataSourceEntity.class);
    List<DataSourceLocationEntity> dataSourceLocations =
        readValue(datasourceLocationEntities, DataSourceLocationEntity.class);
    log.info("Converted entities json string to corresponding list<> pojo's");
    saveAll(checks, dataPoints, dataSources, dataSourceLocations);
    log.info("Populated data into checks, dataPoints, dataSources, dataSourceLocations");
  }

  @Override
  public List<ScorecardSummaryInfo> getScoresSummaryForAnEntity(String accountIdentifier, String entityIdentifier) {
    List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(accountIdentifier, null);

    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    Object entity;
    Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = new HashMap<>();
    if (idpV2Enabled) {
      Pair<CatalogEntity, Map<String, Set<ScopeInfo>>> catalogEntityAndScopeInfos =
          getCatalogEntityAndScopeInfos(accountIdentifier, scorecardAndChecks, entityIdentifier);
      entity = catalogEntityAndScopeInfos.getLeft();
      scopeInfosForScopesUniques = catalogEntityAndScopeInfos.getRight();
    } else {
      entity = getCatalogEntityForEntityAndScorecardFilters(accountIdentifier, scorecardAndChecks, entityIdentifier);
    }

    List<ScorecardEntity> scorecardEntities =
        scorecardAndChecks.stream().map(ScorecardAndChecks::getScorecard).toList();
    Map<String, ScorecardEntity> scorecardIdentifierEntityMapping =
        scorecardEntities.stream().collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));
    Map<String, ScoreEntity> lastComputedScoresForScorecards = getScoreEntityAndScoreCardIdentifierMapping(
        scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountIdentifier, entityIdentifier, idpV2Enabled)
            .getMappedResults());

    Set<String> scorecardIdentifiers =
        scorecardEntities.stream().map(ScorecardEntity::getIdentifier).collect(Collectors.toSet());
    Map<String, ScorecardRecalibrateInfo> recalibrateInfoMap =
        getRecalibrateInfoMap(accountIdentifier, scorecardIdentifiers, entityIdentifier);
    boolean scorecardTiersEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);

    // deleting scores for deleted scorecards
    deleteScoresForDeletedScoreCards(
        accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecards);

    List<ScorecardSummaryInfo> returnData = new ArrayList<>();

    for (Map.Entry<String, ScorecardEntity> entry : scorecardIdentifierEntityMapping.entrySet()) {
      String scorecardIdentifier = entry.getKey();
      ScorecardEntity scorecard = entry.getValue();
      boolean isFilterMatching = idpV2Enabled
          ? scoreComputerService
                .isFilterMatchingWithCatalogEntity(entry.getValue().getFilter(), (CatalogEntity) entity,
                    filterScopeInfosByScoreFilter(entry.getValue().getFilter(), scopeInfosForScopesUniques))
                .getLeft()
          : scoreComputerService.isFilterMatchingWithAnEntity(
                entry.getValue().getFilter(), (BackstageCatalogEntity) entity);
      if (isFilterMatching && lastComputedScoresForScorecards.get(scorecardIdentifier) != null) {
        returnData.add(ScorecardSummaryInfoMapper.toDTO(lastComputedScoresForScorecards.get(scorecardIdentifier),
            scorecard.getName(), scorecard.getDescription(), scorecard.getIdentifier(),
            recalibrateInfoMap.get(scorecardIdentifier), scorecardTiersEnabled));
      }
    }
    if (isEmpty(returnData)) {
      throw new UnsupportedOperationException("No scorecard is present for given entity");
    }
    return returnData;
  }

  @Override
  public List<ScorecardSummaryInfo> getScoresSummaryForAnEntityV2(String accountIdentifier, String entityIdentifier) {
    List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(accountIdentifier, null);

    Pair<CatalogEntity, Map<String, Set<ScopeInfo>>> catalogEntityAndScopeInfos =
        getCatalogEntityAndScopeInfosV2(accountIdentifier, scorecardAndChecks, entityIdentifier);
    CatalogEntity entity = catalogEntityAndScopeInfos.getLeft();
    Map<String, Set<ScopeInfo>> scopeInfos = catalogEntityAndScopeInfos.getRight();

    List<ScorecardEntity> scorecards = scorecardAndChecks.stream().map(ScorecardAndChecks::getScorecard).toList();
    Map<String, ScorecardEntity> scorecardMap =
        scorecards.stream().collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));

    List<CompletableFuture<Optional<ScorecardSummaryInfo>>> futures =
        scorecards.stream()
            .map(scorecard
                -> CompletableFuture.supplyAsync(()
                                                     -> processScorecardForEntity(accountIdentifier, entityIdentifier,
                                                         scorecard, entity, scopeInfos),
                    scorecardSummaryExecutor))
            .toList();

    List<ScorecardSummaryInfo> results = futures.stream()
                                             .map(CompletableFuture::join)
                                             .filter(Optional::isPresent)
                                             .map(Optional::get)
                                             .collect(Collectors.toList());

    Map<String, ScoreEntity> allScores = getScoreEntityAndScoreCardIdentifierMapping(
        scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountIdentifier, entityIdentifier, true)
            .getMappedResults());
    deleteScoresForDeletedScoreCards(accountIdentifier, scorecardMap, allScores);

    if (isEmpty(results)) {
      throw new UnsupportedOperationException("No scorecard is present for given entity");
    }
    return results;
  }

  private Optional<ScorecardSummaryInfo> processScorecardForEntity(String accountIdentifier, String entityIdentifier,
      ScorecardEntity scorecard, CatalogEntity entity, Map<String, Set<ScopeInfo>> scopeInfos) {
    try {
      boolean applicable = applicabilityEngine.isApplicable(
          scorecard.getFilter(), entity, filterScopeInfosByScoreFilter(scorecard.getFilter(), scopeInfos));
      if (!applicable) {
        return Optional.empty();
      }

      ScoreEntity score = scoreRepository.getLatestComputedScoreForEntityAndScorecard(
          accountIdentifier, entityIdentifier, scorecard.getIdentifier(), true);
      if (score == null) {
        return Optional.empty();
      }

      ScorecardRecalibrateInfo recalibrateInfo = asyncScoreComputationService.getRecalibrateInfo(
          accountIdentifier, scorecard.getIdentifier(), entityIdentifier);

      return Optional.of(ScorecardSummaryInfoMapper.toDTO(score, scorecard.getName(), scorecard.getDescription(),
          scorecard.getIdentifier(), recalibrateInfo, idpCommonService.idpScorecardTiersEnabled(accountIdentifier)));
    } catch (Exception e) {
      log.error("Error processing scorecard {} for entity {}: {}", scorecard.getIdentifier(), entityIdentifier,
          e.getMessage(), e);
      return Optional.empty();
    }
  }

  @Override
  public List<ScorecardGraphSummaryInfo> getScoresGraphSummaryForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    List<ScoreEntity> scoreEntities;
    if (idpV2Enabled) {
      scoreEntities = scoreRepository.findAllByAccountIdentifierAndEntityIdentifierAndScorecardIdentifier(
          accountIdentifier, entityIdentifier.replaceAll("[^a-zA-Z0-9]", "\\\\$0"), scorecardIdentifier);
    } else {
      scoreEntities = scoreRepository.findAllByAccountIdentifierAndEntityIdentifierIgnoreCaseAndScorecardIdentifier(
          accountIdentifier, entityIdentifier.replaceAll("[^a-zA-Z0-9]", "\\\\$0"), scorecardIdentifier);
    }
    return scoreEntities.stream().map(ScorecardGraphSummaryInfoMapper::toDTO).collect(Collectors.toList());
  }

  @Override
  public List<ScorecardScore> getScorecardScoreOverviewForAnEntity(String accountIdentifier, String entityIdentifier) {
    List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(accountIdentifier, null);

    boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    Object entity;
    Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = new HashMap<>();
    if (idpV2Enabled) {
      Pair<CatalogEntity, Map<String, Set<ScopeInfo>>> catalogEntityAndScopeInfos =
          getCatalogEntityAndScopeInfos(accountIdentifier, scorecardAndChecks, entityIdentifier);
      entity = catalogEntityAndScopeInfos.getLeft();
      scopeInfosForScopesUniques = catalogEntityAndScopeInfos.getRight();
    } else {
      entity = getCatalogEntityForEntityAndScorecardFilters(accountIdentifier, scorecardAndChecks, entityIdentifier);
    }

    List<ScorecardEntity> scorecardEntities =
        scorecardAndChecks.stream().map(ScorecardAndChecks::getScorecard).toList();
    Map<String, ScorecardEntity> scorecardIdentifierEntityMapping =
        scorecardEntities.stream().collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));
    Map<String, ScoreEntity> lastComputedScoresForScorecards = getScoreEntityAndScoreCardIdentifierMapping(
        scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountIdentifier, entityIdentifier, idpV2Enabled)
            .getMappedResults());

    // deleting scores for deleted scorecards
    deleteScoresForDeletedScoreCards(
        accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecards);

    boolean scorecardTiersEnabled = idpCommonService.idpScorecardTiersEnabled(accountIdentifier);
    List<ScorecardScore> returnData = new ArrayList<>();
    for (Map.Entry<String, ScorecardEntity> entry : scorecardIdentifierEntityMapping.entrySet()) {
      boolean isFilterMatching = idpV2Enabled
          ? scoreComputerService
                .isFilterMatchingWithCatalogEntity(entry.getValue().getFilter(), (CatalogEntity) entity,
                    filterScopeInfosByScoreFilter(entry.getValue().getFilter(), scopeInfosForScopesUniques))
                .getLeft()
          : scoreComputerService.isFilterMatchingWithAnEntity(
                entry.getValue().getFilter(), (BackstageCatalogEntity) entity);
      if (isFilterMatching && lastComputedScoresForScorecards.get(entry.getKey()) != null) {
        returnData.add(ScorecardScoreMapper.toDTO(lastComputedScoresForScorecards.get(entry.getKey()),
            entry.getValue().getName(), entry.getValue().getDescription(), scorecardTiersEnabled));
      }
    }
    if (isEmpty(returnData)) {
      throw new UnsupportedOperationException("No scorecard is present for given entity");
    }
    return returnData;
  }

  private Pair<CatalogEntity, Map<String, Set<ScopeInfo>>> getCatalogEntityAndScopeInfos(
      String accountIdentifier, List<ScorecardAndChecks> scorecardAndChecks, String entityIdentifier) {
    Set<String> scopes = new HashSet<>();
    scorecardAndChecks.stream()
        .map(ScorecardAndChecks::getScorecard)
        .forEach(scorecardEntity
            -> scopes.addAll(isEmpty(scorecardEntity.getFilter().getScopes())
                    ? Set.of(catalogServiceHelper.getAllScopes())
                    : scorecardEntity.getFilter().getScopes()));
    Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosForScopes =
        catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, String.join(",", scopes), null);
    Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = scopeInfosForScopes.getRight().entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, entry -> new HashSet<>(entry.getValue())));
    String entityRef = CatalogUtils.getEntityRefFromUid(entityIdentifier);
    Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
    String scope = kindScopeIdentifier.getMiddle();
    String[] scopeSplit = scope.split("\\.");
    String orgIdentifier = scopeSplit.length >= 2 ? scopeSplit[1] : null;
    String projectIdentifier = scopeSplit.length == 3 ? scopeSplit[2] : null;
    ScopeInfo scopeInfoOptional = scopeInfosForScopes.getLeft()
                                      .stream()
                                      .filter(scopeInfo
                                          -> Objects.equals(scopeInfo.getOrgIdentifier(), orgIdentifier)
                                              && Objects.equals(scopeInfo.getProjectIdentifier(), projectIdentifier))
                                      .findFirst()
                                      .orElse(null);
    ScopeInfo scopeInfo = scopeInfoOptional != null
        ? scopeInfoOptional
        : getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
    CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(
        scopeInfo.getUniqueId(), kindScopeIdentifier.getLeft(), kindScopeIdentifier.getRight());
    return Pair.of(catalogEntity, scopeInfosForScopesUniques);
  }

  private Pair<CatalogEntity, Map<String, Set<ScopeInfo>>> getCatalogEntityAndScopeInfosV2(
      String accountIdentifier, List<ScorecardAndChecks> scorecardAndChecks, String entityIdentifier) {
    Set<String> scopes = new HashSet<>();
    scorecardAndChecks.stream()
        .map(ScorecardAndChecks::getScorecard)
        .forEach(scorecardEntity
            -> scopes.addAll(isEmpty(scorecardEntity.getFilter().getScopes())
                    ? Set.of(catalogServiceHelper.getAllScopes())
                    : scorecardEntity.getFilter().getScopes()));

    ScopeTopology topology = scopeTopologyCache.get(accountIdentifier);
    if (topology == null) {
      log.warn("Scope topology cache miss for account={}. Falling back to legacy resolution.", accountIdentifier);
      return getCatalogEntityAndScopeInfos(accountIdentifier, scorecardAndChecks, entityIdentifier);
    }

    String joinedScopes = String.join(",", scopes);
    List<String> resolvedUniqueIds = topology.resolveParentUniqueIds(joinedScopes);
    if (resolvedUniqueIds.isEmpty()) {
      resolvedUniqueIds.add(accountIdentifier);
    }
    List<ScopeInfo> scopeInfos = topology.buildScopeInfos(resolvedUniqueIds);
    if (scopeInfos.isEmpty()) {
      scopeInfos.add(ScopeInfo.builder()
                         .accountIdentifier(accountIdentifier)
                         .scopeType(ScopeLevel.ACCOUNT)
                         .uniqueId(accountIdentifier)
                         .build());
    }

    Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = new HashMap<>();
    scopeInfosForScopesUniques.put(joinedScopes, new HashSet<>(scopeInfos));

    String entityRef = CatalogUtils.getEntityRefFromUid(entityIdentifier);
    Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
    String entityNamespace = kindScopeIdentifier.getMiddle();

    String parentUniqueId = topology.resolveNamespaceToUniqueId(entityNamespace);
    if (parentUniqueId == null) {
      // Fallback: use ScopeInfoClient if topology doesn't have this namespace
      String[] parts = entityNamespace.split("\\.");
      String orgIdentifier = parts.length >= 2 ? parts[1] : null;
      String projectIdentifier = parts.length == 3 ? parts[2] : null;
      ScopeInfo scopeInfo =
          getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
      parentUniqueId = scopeInfo != null ? scopeInfo.getUniqueId() : null;
    }
    if (parentUniqueId == null) {
      throw new EntityNotFoundException("Could not resolve scope for entity: " + entityRef);
    }

    CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(
        parentUniqueId, kindScopeIdentifier.getLeft(), kindScopeIdentifier.getRight());
    return Pair.of(catalogEntity, scopeInfosForScopesUniques);
  }

  private Map<String, Set<ScopeInfo>> filterScopeInfosByScoreFilter(
      ScorecardFilter filter, Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques) {
    if (isEmpty(scopeInfosForScopesUniques)) {
      return scopeInfosForScopesUniques;
    }

    List<String> filterScopes = filter.getScopes();
    if (isEmpty(filterScopes)) {
      return scopeInfosForScopesUniques;
    }

    scopeInfosForScopesUniques = constructFormattedScopeInfoMap(scopeInfosForScopesUniques);

    Map<String, Set<ScopeInfo>> filteredMap = new HashMap<>();

    for (String scopePattern : filterScopes) {
      if (scopePattern.equalsIgnoreCase("account")) {
        for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
          if (entry.getKey().equals("account")) {
            filteredMap.put(entry.getKey(), entry.getValue());
          }
        }
      } else if (scopePattern.equalsIgnoreCase("account.org")) {
        for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
          String[] parts = entry.getKey().split("\\.");
          if (parts.length == 2 && parts[0].equals("account")) {
            filteredMap.put(entry.getKey(), entry.getValue());
          }
        }
      } else if (scopePattern.equalsIgnoreCase("account.org.project")) {
        for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
          String[] parts = entry.getKey().split("\\.");
          if (parts.length == 3 && parts[0].equals("account")) {
            filteredMap.put(entry.getKey(), entry.getValue());
          }
        }
      } else if (scopePattern.equalsIgnoreCase("account.*")) {
        filteredMap.putAll(scopeInfosForScopesUniques);
      } else if (scopePattern.startsWith("account.") && scopePattern.endsWith(".*")) {
        String[] hierarchyScope = scopePattern.split("\\.");
        if (hierarchyScope.length == 3) { // account.orgName.*
          String orgIdentifier = hierarchyScope[1];
          for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if ((parts.length == 2 && parts[0].equals("account") && parts[1].equals(orgIdentifier))
                || (parts.length == 3 && parts[0].equals("account") && parts[1].equals(orgIdentifier))) {
              filteredMap.put(entry.getKey(), entry.getValue());
            }
          }
        }
      } else if (scopePattern.startsWith("account.") && !scopePattern.endsWith(".*")
          && scopePattern.split("\\.").length == 2) {
        String orgIdentifier = scopePattern.split("\\.")[1];
        for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
          String[] parts = entry.getKey().split("\\.");
          if (parts.length == 2 && parts[0].equals("account") && parts[1].equals(orgIdentifier)) {
            filteredMap.put(entry.getKey(), entry.getValue());
          }
        }
      } else {
        for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(scopePattern)) {
            filteredMap.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }

    return filteredMap.isEmpty() ? scopeInfosForScopesUniques : filteredMap;
  }

  private Map<String, Set<ScopeInfo>> constructFormattedScopeInfoMap(
      Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques) {
    if (isEmpty(scopeInfosForScopesUniques)) {
      return new HashMap<>();
    }

    Map<String, Set<ScopeInfo>> formattedMap = new HashMap<>();

    for (Map.Entry<String, Set<ScopeInfo>> entry : scopeInfosForScopesUniques.entrySet()) {
      Set<ScopeInfo> scopeInfoSet = entry.getValue();
      for (ScopeInfo scopeInfo : scopeInfoSet) {
        String key;
        if (scopeInfo.getProjectIdentifier() != null && scopeInfo.getOrgIdentifier() != null) {
          key = "account"
              + "." + scopeInfo.getOrgIdentifier() + "." + scopeInfo.getProjectIdentifier();
        } else if (scopeInfo.getOrgIdentifier() != null) {
          key = "account"
              + "." + scopeInfo.getOrgIdentifier();
        } else {
          key = "account";
        }

        formattedMap.computeIfAbsent(key, k -> new HashSet<>()).add(scopeInfo);
      }
    }

    return formattedMap;
  }

  @Override
  public ScorecardSummaryInfo getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    ScorecardDetails scorecardDetails = null;
    if (scorecardIdentifier != null) {
      scorecardDetails = scorecardService.getScorecardDetails(accountIdentifier, scorecardIdentifier).getScorecard();
      if (!scorecardDetails.isPublished()) {
        throw new UnsupportedOperationException(
            String.format("Recalibrated scores will not be calculated for unpublished scorecard - %s for entity - %s "
                    + "in account - %s ",
                scorecardIdentifier, entityIdentifier, accountIdentifier));
      }
    }

    scoreComputerService.computeScores(accountIdentifier,
        scorecardIdentifier == null ? Collections.emptyList() : Collections.singletonList(scorecardIdentifier),
        entityIdentifier == null ? Collections.emptyList() : Collections.singletonList(entityIdentifier));

    if (scorecardIdentifier != null) {
      ScoreEntity latestComputedScoreForScorecard = null;
      if (entityIdentifier != null) {
        boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
        latestComputedScoreForScorecard = scoreRepository.getLatestComputedScoreForEntityAndScorecard(
            accountIdentifier, entityIdentifier, scorecardIdentifier, idpV2Enabled);
      }
      return ScorecardSummaryInfoMapper.toDTO(latestComputedScoreForScorecard, scorecardDetails.getName(),
          scorecardDetails.getDescription(), scorecardIdentifier, null,
          idpCommonService.idpScorecardTiersEnabled(accountIdentifier));
    }
    return null;
  }

  @Override
  public List<EntityScores> getEntityScores(String harnessAccount, ScorecardFilter filter) {
    List<EntityScores> entityScores = new ArrayList<>();
    boolean idpV2Enabled = idpCommonService.idpV2Enabled(harnessAccount);
    if (idpV2Enabled) {
      Set<CatalogEntity> catalogEntities =
          scoreComputerService.getAllEntitiesForIDPCatalogs(harnessAccount, null, List.of(filter));
      for (CatalogEntity catalogEntity : catalogEntities) {
        List<ScorecardScore> scorecardScores =
            getScorecardScoreOverviewForAnEntity(harnessAccount, CatalogUtils.getEntityUUId(catalogEntity));
        if (isEmpty(scorecardScores)) {
          continue;
        }
        EntityScores entity = new EntityScores();

        entity.setName(catalogEntity.getIdentifier());
        entity.setTitle(catalogEntity.getName());
        entity.setKind(catalogEntity.getKind());
        entity.setNamespace("default");
        entity.setScores(scorecardScores);
        entityScores.add(entity);
      }

    } else {
      Set<BackstageCatalogEntity> backstageCatalogEntities =
          scoreComputerService.getAllEntities(harnessAccount, null, List.of(filter));
      for (BackstageCatalogEntity backstageCatalogEntity : backstageCatalogEntities) {
        List<ScorecardScore> scorecardScores =
            getScorecardScoreOverviewForAnEntity(harnessAccount, getEntityUniqueId(backstageCatalogEntity));
        if (isEmpty(scorecardScores)) {
          continue;
        }
        EntityScores entity = new EntityScores();
        String name = BackstageCatalogEntity.getValue(
            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAME, String.class);
        String title = BackstageCatalogEntity.getValue(
            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.TITLE, String.class);
        entity.setName(name);
        entity.setTitle(isEmpty(title) ? name : title);
        entity.setKind(backstageCatalogEntity.getKind());
        entity.setNamespace(BackstageCatalogEntity.getValue(
            backstageCatalogEntity.getMetadata(), MetadataFieldConstants.NAMESPACE, String.class));
        entity.setScores(scorecardScores);
        entityScores.add(entity);
      }
    }
    return entityScores;
  }

  @Override
  public List<ScoreEntity> fetchScoresForCatalogEntity(
      String accountIdentifier, CatalogEntity catalogEntity, List<ScorecardAndChecks> scorecardAndChecks) {
    try {
      List<ScorecardEntity> scorecardEntities =
          scorecardAndChecks.stream().map(ScorecardAndChecks::getScorecard).toList();
      Map<String, ScorecardEntity> scorecardIdentifierEntityMapping =
          scorecardEntities.stream().collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));
      Map<String, ScoreEntity> lastComputedScoresForScorecards =
          getScoreEntityAndScoreCardIdentifierMapping(scoreRepository
                                                          .getAllLatestScoresByScorecardsForAnEntity(accountIdentifier,
                                                              CatalogUtils.entityRefV1(catalogEntity), true)
                                                          .getMappedResults());
      deleteScoresForDeletedScoreCards(
          accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecards);
      Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = new HashMap<>();
      List<ScoreEntity> scores = new ArrayList<>();
      for (Map.Entry<String, ScorecardEntity> entry : scorecardIdentifierEntityMapping.entrySet()) {
        Pair<Boolean, Map<String, Set<ScopeInfo>>> filterMatchAndScopeInfosForScope =
            scoreComputerService.isFilterMatchingWithCatalogEntity(
                entry.getValue().getFilter(), catalogEntity, scopeInfosForScopesUniques);
        boolean isFilterMatching = filterMatchAndScopeInfosForScope.getLeft();
        scopeInfosForScopesUniques = filterMatchAndScopeInfosForScope.getRight();
        if (isFilterMatching) {
          scores.add(lastComputedScoresForScorecards.get(entry.getKey()));
        }
      }
      return scores;
    } catch (Exception e) {
      log.error("Error occurred while fetching scores for entity {}", CatalogUtils.entityRefV1(catalogEntity), e);
      return new ArrayList<>();
    }
  }

  @Override
  public Map<String, List<ScoreEntity>> fetchScoresForCatalogEntities(String accountIdentifier,
      List<CatalogEntity> catalogEntities, List<ScorecardAndChecks> scorecardAndChecks,
      Map<String, List<ScopeInfo>> scopeInfosForScopes) {
    try {
      List<ScorecardEntity> scorecardEntities =
          scorecardAndChecks.stream().map(ScorecardAndChecks::getScorecard).toList();
      Map<String, ScorecardEntity> scorecardIdentifierEntityMapping =
          scorecardEntities.stream().collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));
      Map<String, Map<String, ScoreEntity>> lastComputedScoresForScorecardsEntities =
          getScoreEntityAndScoreCardIdentifierEntityIdentifierMapping(
              scoreRepository
                  .getAllLatestScoresByScorecardsForEntities(accountIdentifier,
                      catalogEntities.stream().map(CatalogUtils::entityRefV1).collect(Collectors.toList()), true)
                  .getMappedResults());
      deleteScoresForDeletedScoreCardsEntities(
          accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecardsEntities);
      Map<String, List<ScoreEntity>> scores = new HashMap<>();
      Map<String, Set<ScopeInfo>> scopeInfosForScopesUniques = scopeInfosForScopes.entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, entry -> new HashSet<>(entry.getValue())));
      for (Map.Entry<String, ScorecardEntity> entry : scorecardIdentifierEntityMapping.entrySet()) {
        for (CatalogEntity catalogEntity : catalogEntities) {
          Pair<Boolean, Map<String, Set<ScopeInfo>>> filterMatchAndScopeInfosForScope =
              scoreComputerService.isFilterMatchingWithCatalogEntity(
                  entry.getValue().getFilter(), catalogEntity, scopeInfosForScopesUniques);
          boolean isFilterMatching = filterMatchAndScopeInfosForScope.getLeft();
          scopeInfosForScopesUniques = filterMatchAndScopeInfosForScope.getRight();
          if (isFilterMatching) {
            List<ScoreEntity> scoreEntities = !isEmpty(scores.get(CatalogUtils.entityRef(catalogEntity)))
                ? new ArrayList<>(scores.get(CatalogUtils.entityRef(catalogEntity)))
                : new ArrayList<>();
            Map<String, ScoreEntity> scorecardScores = lastComputedScoresForScorecardsEntities.getOrDefault(
                CatalogUtils.getEntityUUId(catalogEntity), new HashMap<>());
            ScoreEntity scoreEntity = scorecardScores.get(entry.getKey());
            if (isEmpty(scoreEntities)) {
              if (scoreEntity != null) {
                scores.put(CatalogUtils.entityRef(catalogEntity), List.of(scoreEntity));
              }
            } else {
              if (scoreEntity != null) {
                scoreEntities.add(scoreEntity);
                scores.put(CatalogUtils.entityRef(catalogEntity), scoreEntities);
              }
            }
          }
        }
      }
      return scores;
    } catch (Exception e) {
      log.error("Error occurred while fetching scores for entities {}",
          catalogEntities.stream().map(CatalogUtils::entityRefV1).collect(Collectors.joining(", ")), e);
      return new HashMap<>();
    }
  }

  @Override
  public void migrateScoresWithCheckIdentifier() {
    List<String> accountIds = namespaceService.getAccountIds();
    accountIds.forEach(account -> {
      List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(account, null);
      scorecardAndChecks.forEach(scorecardChecks -> {
        List<ScoreEntity> scoreEntities = scoreRepository.findAllByAccountIdentifierAndScorecardIdentifier(
            scorecardChecks.getScorecard().getAccountIdentifier(), scorecardChecks.getScorecard().getIdentifier());
        for (ScoreEntity score : scoreEntities) {
          updateCheckStatus(score, scorecardChecks.getChecks());
        }
      });
    });
  }

  @Override
  public void migrateEntityIdentifier(Map<String, String> entityIdentifiersMap, String accountIdentifier) {
    List<String> entityIdentifiers = scoreRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in Score collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      if (entityIdentifiersMap.containsKey(entityIdentifier)) {
        UpdateResult updateResult = scoreRepository.updateEntityIdentifier(
            accountIdentifier, entityIdentifier, entityIdentifiersMap.get(entityIdentifier));
        log.info("Totally {} records modified in Score collection for account {}, identifier {}",
            updateResult.getModifiedCount(), accountIdentifier, entityIdentifiersMap.get(entityIdentifier));
      }
    });
  }

  @Override
  public void modifyEntityIdentifier(String accountIdentifier) {
    List<String> entityIdentifiers = scoreRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in Score collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      String[] kindNamespaceAndName = entityIdentifier.split("/");
      if (kindNamespaceAndName.length == 3 && BACKSTAGE_KINDS.contains(kindNamespaceAndName[0])) {
        String modifiedEntityIdentifier =
            getEntityUniqueId(kindNamespaceAndName[1], kindNamespaceAndName[0], kindNamespaceAndName[2]);
        try {
          UpdateResult updateResult =
              scoreRepository.updateEntityIdentifier(accountIdentifier, entityIdentifier, modifiedEntityIdentifier);
          log.info("Totally {} records modified in Score collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityIdentifier);
        } catch (Exception e) {
          log.error("Error occurred while modifying Score collection for account {}, identifier {}", accountIdentifier,
              entityIdentifier, e);
        }
      }
    });
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids) {
    List<String> entityIdentifiers = scoreRepository.findUniqueEntityIdentifiers(accountIdentifier);
    log.info("Totally {} unique records present in Score collection for account {}", entityIdentifiers.size(),
        accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      String[] namespaceKindName = entityIdentifier.split("/");
      if (namespaceKindName.length == 3 && !namespaceKindName[0].equals("account")
          && !namespaceKindName[0].contains(".")) {
        String namespace = namespaceKindName[0].toLowerCase();
        String kind = namespaceKindName[1].toLowerCase();
        String name = namespaceKindName[2].toLowerCase();
        String modifiedEntityUid = "account/" + kind + "/" + name;
        String modifiedEntityUidForConflict = "account/" + kind + "/" + namespace + "_" + name;
        if (conflictedEntityUids.contains(modifiedEntityUidForConflict)) {
          modifiedEntityUid = modifiedEntityUidForConflict;
        }
        try {
          UpdateResult updateResult =
              scoreRepository.updateEntityIdentifier(accountIdentifier, entityIdentifier, modifiedEntityUid);
          log.info("Totally {} records modified in Score collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityIdentifier);
        } catch (DuplicateKeyException e) {
          log.error("Duplicate key exception occurred while modifying entityIdentifier {} for Score collection",
              entityIdentifier, e);
        }
      }
    });
  }

  @Override
  public void generateFailureSummaryForFailedChecksInScore(
      String accountIdentifier, String scorecardIdentifier, String entityIdentifier, long triggeredAt) {
    try {
      if (StringUtils.isBlank(accountIdentifier) || StringUtils.isBlank(scorecardIdentifier)
          || StringUtils.isBlank(entityIdentifier)) {
        log.info("Insufficient data to generate failure summary: acc={}, sc={}, ent={}, trig={} ", accountIdentifier,
            scorecardIdentifier, entityIdentifier, triggeredAt);
        return;
      }

      boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
      ScoreEntity score = scoreRepository.getLatestComputedScoreForEntityAndScorecard(
          accountIdentifier, entityIdentifier, scorecardIdentifier, idpV2Enabled);

      if (score == null || isEmpty(score.getCheckStatus())) {
        log.info("No score found for acc={}, sc={}, ent={}", accountIdentifier, scorecardIdentifier, entityIdentifier);
        return;
      }

      // Skip summary generation for stale computations
      if (score.getLastComputedTimestamp() > triggeredAt) {
        log.info("Skipping summary generation for stale score for acc={}, sc={}, ent={}", accountIdentifier,
            scorecardIdentifier, entityIdentifier);
        return;
      }

      List<CheckStatus> updatedStatuses = new ArrayList<>();
      for (CheckStatus status : score.getCheckStatus()) {
        if (status.getStatus() == CheckStatus.StatusEnum.FAIL) {
          String summary = generateFailureSummaryForCheck(accountIdentifier, status.getIdentifier(), status.getName(),
              status.getCheckDescription(), status.getExpression(), status.getEvaluationData());
          status.setFailureSummary(summary);
        }
        updatedStatuses.add(status);
      }
      score.setCheckStatus(updatedStatuses);
      scoreRepository.save(score);
      log.debug("Failure summary generated for acc={}, sc={}, ent={}, ts={}", accountIdentifier, scorecardIdentifier,
          entityIdentifier, score.getLastComputedTimestamp());
    } catch (Exception e) {
      log.warn("Failed to generate failure summary for acc={}, sc={}, ent={} ", accountIdentifier, scorecardIdentifier,
          entityIdentifier, e);
    }
  }

  private String generateFailureSummaryForCheck(String accountId, String checkId, String checkName,
      String checkDescription, String checkExpression, List<EvaluationData> checkEvaluationData) {
    return failureSummaryService.getOrCompute(
        accountId, checkId, checkName, checkDescription, checkExpression, checkEvaluationData);
  }

  @Override
  public void modifyScopeForEntityIdentifier(
      String accountIdentifier, String existingEntityIdentifier, String modifiedEntityIdentifier) {
    try {
      UpdateResult updateResult =
          scoreRepository.updateEntityIdentifier(accountIdentifier, existingEntityIdentifier, modifiedEntityIdentifier);
      log.info("Totally {} records modified in Score collection for IDP 2.0 MigrationAPI Operation for account {}, "
              + "identifier {}",
          updateResult.getModifiedCount(), accountIdentifier, existingEntityIdentifier);
    } catch (Exception e) {
      log.error("Error occurred while modifying Score collection for IDP 2.0 MigrationAPI Operation for account {}, "
              + "identifier {}",
          accountIdentifier, existingEntityIdentifier, e);
    }
  }

  private void updateCheckStatus(ScoreEntity score, List<CheckEntity> checks) {
    List<CheckStatus> checkStatuses = new ArrayList<>();
    for (CheckStatus checkStatus : score.getCheckStatus()) {
      CheckStatus updatedCheckStatus = new CheckStatus();
      for (CheckEntity check : checks) {
        if (check.getName().equals(checkStatus.getName())) {
          updatedCheckStatus.setIdentifier(check.getIdentifier());
          updatedCheckStatus.setCustom(check.isCustom());
          break;
        }
      }
      updatedCheckStatus.setName(checkStatus.getName());
      updatedCheckStatus.setStatus(checkStatus.getStatus());
      updatedCheckStatus.setReason(checkStatus.getReason());
      updatedCheckStatus.setWeight(checkStatus.getWeight());
      checkStatuses.add(updatedCheckStatus);
    }
    UpdateResult updateResult = scoreRepository.updateCheckIdentifier(score, checkStatuses);
    if (updateResult.getModifiedCount() == 1) {
      log.info(String.format(
          "Added check identifier field for scorecard: %s, account: %s, entity: %s, lastComputedTimestamp: %d",
          score.getScorecardIdentifier(), score.getAccountIdentifier(), score.getEntityIdentifier(),
          score.getLastComputedTimestamp()));
    } else {
      log.warn(String.format(
          "Could not add check identifier field for scorecard: %s, account: %s, entity: %s, lastComputedTimestamp: %d",
          score.getScorecardIdentifier(), score.getAccountIdentifier(), score.getEntityIdentifier(),
          score.getLastComputedTimestamp()));
    }
  }

  private void saveAll(List<CheckEntity> checks, List<DataPointEntity> dataPoints, List<DataSourceEntity> dataSources,
      List<DataSourceLocationEntity> dataSourceLocations) {
    transactionHelper.performTransaction(() -> {
      checkRepository.saveAll(checks);
      datapointRepository.saveAll(dataPoints);
      datasourceRepository.saveAll(dataSources);
      datasourceLocationRepository.saveAll(dataSourceLocations);
      return null;
    });
  }

  private Map<String, ScoreEntity> getScoreEntityAndScoreCardIdentifierMapping(
      List<ScoreEntityByScorecardIdentifier> scoreEntityByScorecardIdentifierList) {
    return scoreEntityByScorecardIdentifierList.stream().collect(Collectors.toMap(
        ScoreEntityByScorecardIdentifier::getScorecardIdentifier, ScoreEntityByScorecardIdentifier::getScoreEntity));
  }

  private Map<String, Map<String, ScoreEntity>> getScoreEntityAndScoreCardIdentifierEntityIdentifierMapping(
      List<ScoreEntityByScorecardIdentifierEntityIdentifier> scoreEntityByScorecardIdentifierEntityIdentifierList) {
    return scoreEntityByScorecardIdentifierEntityIdentifierList.stream().collect(
        Collectors.groupingBy(ScoreEntityByScorecardIdentifierEntityIdentifier::getEntityIdentifier,
            Collectors.toMap(ScoreEntityByScorecardIdentifierEntityIdentifier::getScorecardIdentifier,
                ScoreEntityByScorecardIdentifierEntityIdentifier::getScoreEntity)));
  }

  private void deleteScoresForDeletedScoreCards(String accountIdentifier,
      Map<String, ScorecardEntity> scorecardIdentifierMapping, Map<String, ScoreEntity> lastComputedScores) {
    List<String> scoreIdsToBeDeleted = new ArrayList<>();

    for (Map.Entry<String, ScoreEntity> lastComputedScore : lastComputedScores.entrySet()) {
      if (!scorecardIdentifierMapping.containsKey(lastComputedScore.getKey())) {
        scoreIdsToBeDeleted.add(lastComputedScore.getValue().getId());
      }
    }
    scoreRepository.deleteAllByAccountIdentifierAndIdIn(accountIdentifier, scoreIdsToBeDeleted);
  }

  private void deleteScoresForDeletedScoreCardsEntities(String accountIdentifier,
      Map<String, ScorecardEntity> scorecardIdentifierMapping,
      Map<String, Map<String, ScoreEntity>> lastComputedScores) {
    List<String> scoreIdsToBeDeleted = new ArrayList<>();

    for (Map.Entry<String, Map<String, ScoreEntity>> lastComputedScoreEntity : lastComputedScores.entrySet()) {
      for (Map.Entry<String, ScoreEntity> lastComputedScore : lastComputedScoreEntity.getValue().entrySet()) {
        if (!scorecardIdentifierMapping.containsKey(lastComputedScore.getKey())) {
          scoreIdsToBeDeleted.add(lastComputedScore.getValue().getId());
        }
      }
    }
    scoreRepository.deleteAllByAccountIdentifierAndIdIn(accountIdentifier, scoreIdsToBeDeleted);
  }

  Object getCatalogEntityForEntityAndScorecardFilters(
      String accountIdentifier, List<ScorecardAndChecks> scorecardAndChecks, String entityIdentifier) {
    Set ens = scoreComputerService.getBackstageEntitiesForScorecardsAndEntityIdentifiers(
        accountIdentifier, scorecardAndChecks, Collections.singletonList(entityIdentifier));
    if (!ens.iterator().hasNext()) {
      log.info(
          "No scorecards filters are matching with entity - {} in account - {}", entityIdentifier, accountIdentifier);
      throw new UnsupportedOperationException("No scorecard is present for given entity");
    }

    return ens.iterator().next();
  }

  private Map<String, ScorecardRecalibrateInfo> getRecalibrateInfoMap(
      String accountIdentifier, Set<String> scorecardIdentifiers, String entityIdentifier) {
    Map<String, ScorecardRecalibrateInfo> recalibrateInfoMap = new HashMap<>();
    for (String scorecardIdentifier : scorecardIdentifiers) {
      recalibrateInfoMap.put(scorecardIdentifier,
          asyncScoreComputationService.getRecalibrateInfo(accountIdentifier, scorecardIdentifier, entityIdentifier));
    }
    return recalibrateInfoMap;
  }
}
