/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.DateUtils.getPreviousDay24HourTimeFrame;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.utils.BackstageUtils;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.repositories.CheckStatsRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepository;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardStatsRepository;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.idp.scorecard.scores.repositories.ScoresByScorecardIdentifier;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.springdata.TransactionHelper;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.CheckStatsRecord;
import io.harness.timescaledb.tables.records.ScorecardStatsRecord;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep4;
import org.jooq.InsertValuesStep6;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class StatsComputeServiceImpl implements StatsComputeService {
  private static final String STATS_COMPUTATION_HANDLER = "StatsComputationHandler";
  private static final String STATS_COMPUTATION_SYNC_HANDLER = "StatsComputationSyncHandler";
  @Inject ScorecardRepository scorecardRepository;
  @Inject ScoreRepository scoreRepository;
  @Inject NamespaceService namespaceService;
  @Inject ScoreComputerService scoreComputerService;
  @Inject ScorecardStatsRepository scorecardStatsRepository;
  @Inject CheckStatsRepository checkStatsRepository;
  @Inject CheckStatusRepository checkStatusRepository;
  @Inject TransactionHelper transactionHelper;
  @Inject DSLContext dsl;
  @Inject IdpCommonService idpCommonService;
  @Inject IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  private static final Integer BATCH_SIZE = 100;

  @Override
  public void populateStatsData() {
    List<String> accountIds = namespaceService.getAccountIds();
    for (String accountId : accountIds) {
      try {
        boolean idpV2Enabled = idpCommonService.idpV2Enabled(accountId);
        log.info("Stats Computation for account - {} started at - {}", accountId, System.currentTimeMillis());
        List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountId);
        List<ScorecardStatsEntity> scorecardStatsEntities = new ArrayList<>();
        List<CheckStatsEntity> checkStatsEntities = new ArrayList<>();
        Map<String, CheckStatusEntity> checkStatusEntityMap = new HashMap<>();
        Set<String> checkForEntityAlreadySeen = new HashSet<>();
        for (ScorecardEntity scorecardEntity : scorecardEntities) {
          String scorecardId;
          Map<String, Object> idpCatalogMap;

          if (idpV2Enabled) {
            Set<CatalogEntity> catalogEntities = scoreComputerService.getAllEntitiesForIDPCatalogs(
                accountId, null, List.of(scorecardEntity.getFilter()));
            idpCatalogMap =
                catalogEntities.stream().collect(Collectors.toMap(CatalogUtils::getEntityUUId, catalog -> catalog));
            scorecardId = scorecardEntity.getIdentifier();
            log.info("{} - IDP catalogs matching filter for scorecard - {} for account - {}", catalogEntities.size(),
                scorecardId, accountId);
          } else {
            Set<BackstageCatalogEntity> backstageCatalogs =
                scoreComputerService.getAllEntities(accountId, null, List.of(scorecardEntity.getFilter()));

            idpCatalogMap = backstageCatalogs.stream().collect(
                Collectors.toMap(BackstageUtils::getEntityUniqueId, catalog -> catalog));
            scorecardId = scorecardEntity.getIdentifier();
            log.info("{} - Backstage catalogs matching filter for scorecard - {} for account - {}",
                backstageCatalogs.size(), scorecardId, accountId);
          }
          List<ScoreEntityByEntityIdentifier> scoreByEntityIds =
              scoreRepository.getLatestScoresForScorecard(accountId, scorecardId);
          for (ScoreEntityByEntityIdentifier scoreByEntity : scoreByEntityIds) {
            String entityIdentifier = scoreByEntity.getEntityIdentifier();
            ScoreEntity scoreEntity = scoreByEntity.getScoreEntity();

            if (!idpCatalogMap.containsKey(entityIdentifier)) {
              log.info("Could not find entityId - {} for scorecard - {} in backstage catalogs map", entityIdentifier,
                  scorecardId);
              continue;
            }
            scorecardStatsEntities.add(
                scorecardStatsRepository.findOneOrConstructStats(scoreEntity, idpCatalogMap.get(entityIdentifier)));
            for (CheckStatus checkStatus : scoreEntity.getCheckStatus()) {
              String key = entityIdentifier + DOT_SEPARATOR + checkStatus.getIdentifier() + DOT_SEPARATOR
                  + checkStatus.isCustom();
              if (checkForEntityAlreadySeen.contains(key)) {
                continue;
              }
              checkForEntityAlreadySeen.add(key);
              checkStatsEntities.add(
                  checkStatsRepository.findOneOrConstructStats(checkStatus, idpCatalogMap.get(entityIdentifier),
                      accountId, entityIdentifier, scoreEntity.getLastComputedTimestamp()));
            }
          }
        }
        populateCheckStatus(checkStatusEntityMap, checkStatsEntities);
        log.info("Total scorecardStats entries - {} for account - {}", scorecardStatsEntities.size(), accountId);
        log.info("Total checkStats entries - {} for account - {}", checkStatsEntities.size(), accountId);
        log.info("Total checkStatus entries - {} for account - {}", checkStatusEntityMap.size(), accountId);
        transactionHelper.performTransaction(() -> {
          checkStatusRepository.saveAll(new ArrayList<>(checkStatusEntityMap.values()));
          scorecardStatsRepository.saveAll(scorecardStatsEntities);
          checkStatsRepository.saveAll(checkStatsEntities);
          return null;
        });
        idpIteratorMetricRecorder.recordSuccess(STATS_COMPUTATION_HANDLER, accountId);
        log.info("Stats Computation for account - {} completed at - {}", accountId, System.currentTimeMillis());
      } catch (Exception exception) {
        idpIteratorMetricRecorder.recordFailure(STATS_COMPUTATION_HANDLER, accountId);
      }
    }
  }

  @Override
  public void populateStatsDataForSync() {
    List<String> accountIds = namespaceService.getAccountIds();
    for (String accountId : accountIds) {
      try {
        log.info("Stats Computation sync for account - {} started at - {}", accountId, System.currentTimeMillis());
        List<ScorecardStatsEntity> scorecardStatsEntities =
            scorecardStatsRepository.findByAccountIdentifierAndLastUpdatedAtBetween(
                accountId, getPreviousDay24HourTimeFrame().getLeft(), getPreviousDay24HourTimeFrame().getRight());
        List<CheckStatusEntity> checkStatusEntities = checkStatusRepository.findByAccountIdentifierAndTimestampBetween(
            accountId, getPreviousDay24HourTimeFrame().getLeft(), getPreviousDay24HourTimeFrame().getRight());
        insertBulkScorecardStats(scorecardStatsEntities, accountId);
        insertBulkCheckStats(checkStatusEntities, accountId);
        idpIteratorMetricRecorder.recordSuccess(STATS_COMPUTATION_SYNC_HANDLER, accountId);
        log.info("Stats Computation sync for account - {} completed at - {}", accountId, System.currentTimeMillis());
      } catch (Exception exception) {
        idpIteratorMetricRecorder.recordFailure(STATS_COMPUTATION_SYNC_HANDLER, accountId);
      }
    }
  }

  @Override
  public void migrateStatsData(String accountIdentifier) {
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);
    List<ScoresByScorecardIdentifier> scoresByScorecardIdentifiers = new ArrayList<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      List<ScoresByScorecardIdentifier> results =
          scoreRepository.getAllScoresByAccountIdentifierAndScorecardIdentifierPerDay(
              accountIdentifier, scorecardEntity.getIdentifier());
      if (!isEmpty(results)) {
        scoresByScorecardIdentifiers.addAll(results);
      }
    }
    List<List<ScoresByScorecardIdentifier>> partitionedScoresByScorecardIdentifier =
        Lists.partition(scoresByScorecardIdentifiers, BATCH_SIZE);
    log.info("Start inserting batch into scorecard_stats, total score results: {}, "
            + "total partitions: {}, accountId: {}",
        scoresByScorecardIdentifiers.size(), partitionedScoresByScorecardIdentifier.size(), accountIdentifier);
    partitionedScoresByScorecardIdentifier.forEach(this::insertBulkScorecardStatsForMigration);

    List<CheckStatusEntity> checkStatusEntities = checkStatusRepository.findByAccountIdentifier(accountIdentifier);
    List<List<CheckStatusEntity>> partitionedCheckStatuses = Lists.partition(checkStatusEntities, BATCH_SIZE);
    log.info("Start inserting batch into check_stats, total check statuses: {}, "
            + "total partitions: {}, accountId: {}",
        checkStatusEntities.size(), partitionedCheckStatuses.size(), accountIdentifier);
    partitionedCheckStatuses.forEach(checkStatuses -> insertBulkCheckStats(checkStatuses, accountIdentifier));
  }

  private void populateCheckStatus(
      Map<String, CheckStatusEntity> checkStatusEntityMap, List<CheckStatsEntity> checkStatsEntities) {
    for (CheckStatsEntity checkStats : checkStatsEntities) {
      String key = checkStats.getCheckIdentifier() + DOT_SEPARATOR + checkStats.isCustom();
      if (checkStatusEntityMap.containsKey(key)) {
        CheckStatusEntity checkStatusEntity = checkStatusEntityMap.get(key);
        int totalPassed = checkStatusEntity.getPassCount()
            + (CheckStatus.StatusEnum.PASS.toString().equals(checkStats.getStatus()) ? 1 : 0);
        int total = checkStatusEntity.getTotal() + 1;
        checkStatusEntity.setPassCount(totalPassed);
        checkStatusEntity.setTotal(total);
        checkStatusEntityMap.put(key, checkStatusEntity);
      } else {
        checkStatusEntityMap.put(key,
            CheckStatusEntity.builder()
                .accountIdentifier(checkStats.getAccountIdentifier())
                .identifier(checkStats.getCheckIdentifier())
                .isCustom(checkStats.isCustom())
                .passCount(CheckStatus.StatusEnum.PASS.toString().equals(checkStats.getStatus()) ? 1 : 0)
                .total(1)
                .timestamp(checkStats.getLastUpdatedAt())
                .build());
      }
    }
  }

  private void insertBulkScorecardStats(List<ScorecardStatsEntity> scorecardStatsEntities, String accountId) {
    if (isEmpty(scorecardStatsEntities)) {
      log.warn("No scorecard stats found to migrate");
      return;
    }

    Map<String, String> scoresMap = new HashMap<>();
    Map<String, Long> lastUpdatedMap = new HashMap<>();
    for (ScorecardStatsEntity scorecardStatsEntity : scorecardStatsEntities) {
      String scorecardIdentifier = scorecardStatsEntity.getScorecardIdentifier();
      if (scoresMap.containsKey(scorecardIdentifier)) {
        scoresMap.put(scorecardIdentifier, scoresMap.get(scorecardIdentifier) + "," + scorecardStatsEntity.getScore());
        lastUpdatedMap.put(scorecardIdentifier,
            Long.max(lastUpdatedMap.get(scorecardIdentifier), scorecardStatsEntity.getLastUpdatedAt()));
      } else {
        scoresMap.put(scorecardIdentifier, String.valueOf(scorecardStatsEntity.getScore()));
        lastUpdatedMap.put(scorecardIdentifier, scorecardStatsEntity.getLastUpdatedAt());
      }
    }
    try {
      InsertValuesStep4<ScorecardStatsRecord, String, String, String, Long> bulkInsert =
          dsl.insertInto(Tables.SCORECARD_STATS, Tables.SCORECARD_STATS.ACCOUNT_IDENTIFIER,
              Tables.SCORECARD_STATS.SCORECARD_IDENTIFIER, Tables.SCORECARD_STATS.SCORES,
              Tables.SCORECARD_STATS.CALCULATED_AT);
      for (Map.Entry<String, String> entry : scoresMap.entrySet()) {
        scorecardStatsEntities.forEach(scorecardStatsEntity
            -> bulkInsert.values(accountId, entry.getKey(), entry.getValue(), lastUpdatedMap.get(entry.getKey())));
      }
      int size = bulkInsert.onConflictOnConstraint(Tables.SCORECARD_STATS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted scorecard_stats batch size {} for accountId: {}", size, accountId);
    } catch (Exception e) {
      log.error("Exception while bulk insert scorecard_stats for account {}", accountId, e);
    }
  }

  private void insertBulkScorecardStatsForMigration(List<ScoresByScorecardIdentifier> scoresByScorecardIdentifiers) {
    if (isEmpty(scoresByScorecardIdentifiers)) {
      log.warn("No score results found to migrate");
      return;
    }

    try {
      InsertValuesStep4<ScorecardStatsRecord, String, String, String, Long> bulkInsert =
          dsl.insertInto(Tables.SCORECARD_STATS, Tables.SCORECARD_STATS.ACCOUNT_IDENTIFIER,
              Tables.SCORECARD_STATS.SCORECARD_IDENTIFIER, Tables.SCORECARD_STATS.SCORES,
              Tables.SCORECARD_STATS.CALCULATED_AT);
      scoresByScorecardIdentifiers.forEach(scoresByScorecardIdentifier
          -> bulkInsert.values(scoresByScorecardIdentifier.getAccountIdentifier(),
              scoresByScorecardIdentifier.getScorecardIdentifier(),
              scoresByScorecardIdentifier.getScores().toString().replace("[", "").replace("]", "").replaceAll(" ", ""),
              scoresByScorecardIdentifier.getLastComputedTimestamp()));
      int size = bulkInsert.onConflictOnConstraint(Tables.SCORECARD_STATS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted scorecard_stats batch size {} for ids: {}", size,
          scoresByScorecardIdentifiers.stream()
              .map(scoresByScorecardIdentifier
                  -> scoresByScorecardIdentifier.getAccountIdentifier() + DOT_SEPARATOR
                      + scoresByScorecardIdentifier.getScorecardIdentifier() + DOT_SEPARATOR
                      + scoresByScorecardIdentifier.getLastComputedTimestamp())
              .toList());
    } catch (Exception e) {
      log.error("Exception while bulk insert scorecard_stats", e);
    }
  }

  private void insertBulkCheckStats(List<CheckStatusEntity> checkStatusEntities, String accountId) {
    if (isEmpty(checkStatusEntities)) {
      log.warn("No check stats found to migrate");
      return;
    }

    try {
      InsertValuesStep6<CheckStatsRecord, String, String, Short, Long, Boolean, Integer> bulkInsert =
          dsl.insertInto(Tables.CHECK_STATS, Tables.CHECK_STATS.ACCOUNT_IDENTIFIER, Tables.CHECK_STATS.CHECK_IDENTIFIER,
              Tables.CHECK_STATS.PASS_COUNT, Tables.CHECK_STATS.CALCULATED_AT, Tables.CHECK_STATS.CUSTOM,
              Tables.CHECK_STATS.TOTAL);
      checkStatusEntities.forEach(checkStatusEntity
          -> bulkInsert.values(checkStatusEntity.getAccountIdentifier(), checkStatusEntity.getIdentifier(),
              (short) checkStatusEntity.getPassCount(), checkStatusEntity.getTimestamp(), checkStatusEntity.isCustom(),
              checkStatusEntity.getTotal()));

      int size = bulkInsert.onConflictOnConstraint(Tables.CHECK_STATS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted check_stats batch size {} for accountId: {}", size, accountId);
    } catch (Exception e) {
      log.error("Exception while bulk insert check_stats for account {}", accountId, e);
    }
  }
}
