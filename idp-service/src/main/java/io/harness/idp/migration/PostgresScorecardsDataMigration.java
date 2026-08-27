/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.ChecksRecord;
import io.harness.timescaledb.tables.records.ScorecardsChecksRecord;
import io.harness.timescaledb.tables.records.ScorecardsRecord;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep15;
import org.jooq.InsertValuesStep4;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PostgresScorecardsDataMigration implements NGMigration {
  @Inject private DSLContext dsl;
  @Inject NamespaceService namespaceService;
  @Inject ScorecardService scorecardService;
  @Inject CheckRepository checkRepository;
  private static final Integer BATCH_SIZE = 100;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding data to scorecards, checks, scorecards_checks tables.");
    List<CheckEntity> checkEntities = checkRepository.findByAccountIdentifierAndIsDeleted(GLOBAL_ACCOUNT_ID, false);
    List<ScorecardEntity> scorecardEntities = new ArrayList<>();
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    List<ScorecardsChecksRecord> scorecardsChecksRecords = new ArrayList<>();
    accountIdentifiers.forEach(accountIdentifier -> {
      List<ScorecardAndChecks> scorecardAndChecksList =
          scorecardService.getAllScorecardAndChecks(accountIdentifier, null);
      scorecardAndChecksList.forEach(scorecardAndChecks -> {
        scorecardEntities.add(scorecardAndChecks.getScorecard());
        String scorecardIdentifier = scorecardAndChecks.getScorecard().getIdentifier();
        scorecardAndChecks.getChecks().forEach(checkEntity
            -> scorecardsChecksRecords.add(new ScorecardsChecksRecord(
                accountIdentifier, scorecardIdentifier, checkEntity.getIdentifier(), checkEntity.isCustom())));
      });
      checkEntities.addAll(checkRepository.findByAccountIdentifierAndIsDeleted(accountIdentifier, false));
    });

    List<List<CheckEntity>> partitionedChecks = Lists.partition(checkEntities, BATCH_SIZE);
    log.info("Start inserting batch into checks, total checks: {}, "
            + "total partitions: {}",
        checkEntities.size(), partitionedChecks.size());
    partitionedChecks.forEach(this::insertBulkChecks);

    List<List<ScorecardEntity>> partitionedScorecards = Lists.partition(scorecardEntities, BATCH_SIZE);
    log.info("Start inserting batch into scorecards, total scorecards: {}, "
            + "total partitions: {}",
        scorecardEntities.size(), partitionedScorecards.size());
    partitionedScorecards.forEach(this::insertBulkScorecards);

    List<List<ScorecardsChecksRecord>> partitionedScorecardsChecks =
        Lists.partition(scorecardsChecksRecords, BATCH_SIZE);
    log.info("Start inserting batch into scorecards_checks, total scorecards_checks: {}, "
            + "total partitions: {}",
        scorecardsChecksRecords.size(), partitionedScorecardsChecks.size());
    partitionedScorecardsChecks.forEach(this::insertBulkScorecardsChecks);
    log.info("Migration complete for adding data to scorecards, checks, scorecards_checks tables.");
  }

  private void insertBulkChecks(List<CheckEntity> checkEntities) {
    if (isEmpty(checkEntities)) {
      log.warn("No active checks found to migrate");
      return;
    }

    try {
      InsertValuesStep15<ChecksRecord, String, String, String, String, Boolean, String, String, String, String, String,
          Boolean, Long, String, Long, String> bulkInsert = dsl.insertInto(Tables.CHECKS, Tables.CHECKS.ID,
          Tables.CHECKS.ACCOUNT_IDENTIFIER, Tables.CHECKS.IDENTIFIER, Tables.CHECKS.NAME, Tables.CHECKS.CUSTOM,
          Tables.CHECKS.DESCRIPTION, Tables.CHECKS.RULE_STRATEGY, Tables.CHECKS.EXPRESSION,
          Tables.CHECKS.DEFAULT_BEHAVIOUR, Tables.CHECKS.FAIL_MESSAGE, Tables.CHECKS.DELETED, Tables.CHECKS.CREATED_AT,
          Tables.CHECKS.CREATED_BY, Tables.CHECKS.LAST_UPDATED_AT, Tables.CHECKS.LAST_UPDATED_BY);
      checkEntities.forEach(check
          -> bulkInsert.values(check.getId(), check.getAccountIdentifier(), check.getIdentifier(), check.getName(),
              check.isCustom(), check.getDescription(), check.getRuleStrategy().toString(), check.getExpression(),
              check.getDefaultBehaviour().toString(), check.getFailMessage(), check.isDeleted(), check.getCreatedAt(),
              check.getCreatedBy() != null ? check.getCreatedBy().getName() : "System", check.getLastUpdatedAt(),
              check.getLastUpdatedBy() != null ? check.getLastUpdatedBy().getName() : null));
      int size = bulkInsert.onConflictOnConstraint(Tables.CHECKS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted checks batch size {} for ids: {}", size,
          checkEntities.stream()
              .map(check -> check.getAccountIdentifier() + DOT_SEPARATOR + check.getIdentifier())
              .toList());
    } catch (Exception e) {
      log.error("Exception while bulk insert checks", e);
    }
  }

  private void insertBulkScorecards(List<ScorecardEntity> scorecardEntities) {
    if (isEmpty(scorecardEntities)) {
      log.warn("No active scorecards found to migrate");
      return;
    }

    try {
      InsertValuesStep15<ScorecardsRecord, String, String, String, String, String, String, String, Short, Short,
          Boolean, Boolean, Long, String, Long, String> insert = dsl.insertInto(Tables.SCORECARDS, Tables.SCORECARDS.ID,
          Tables.SCORECARDS.ACCOUNT_IDENTIFIER, Tables.SCORECARDS.IDENTIFIER, Tables.SCORECARDS.NAME,
          Tables.SCORECARDS.DESCRIPTION, Tables.SCORECARDS.FILTER, Tables.SCORECARDS.WEIGHTAGE_STRATEGY,
          Tables.SCORECARDS.TOTAL_NUMBER_OF_CHECKS, Tables.SCORECARDS.NUMBER_OF_CUSTOM_CHECKS,
          Tables.SCORECARDS.PUBLISHED, Tables.SCORECARDS.DELETED, Tables.SCORECARDS.CREATED_AT,
          Tables.SCORECARDS.CREATED_BY, Tables.SCORECARDS.LAST_UPDATED_AT, Tables.SCORECARDS.LAST_UPDATED_BY);
      scorecardEntities.forEach(scorecard
          -> insert.values(scorecard.getId(), scorecard.getAccountIdentifier(), scorecard.getIdentifier(),
              scorecard.getName(), scorecard.getDescription(), constructFilters(scorecard.getFilter()),
              scorecard.getWeightageStrategy().toString(), (short) scorecard.getChecks().size(),
              (short) scorecard.getChecks()
                  .stream()
                  .filter(ScorecardEntity.Check::isCustom)
                  .collect(Collectors.toSet())
                  .size(),
              scorecard.isPublished(), scorecard.isDeleted(), scorecard.getCreatedAt(),
              scorecard.getCreatedBy() != null ? scorecard.getCreatedBy().getName() : "System",
              scorecard.getLastUpdatedAt(),
              scorecard.getLastUpdatedBy() != null ? scorecard.getLastUpdatedBy().getName() : null));
      int size = insert.onConflictOnConstraint(Tables.SCORECARDS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted data in scorecards batch size {} for ids: {}", size,
          scorecardEntities.stream()
              .map(scorecard -> scorecard.getAccountIdentifier() + DOT_SEPARATOR + scorecard.getIdentifier())
              .toList());
    } catch (Exception e) {
      log.error("Exception while bulk insert scorecards", e);
    }
  }

  private void insertBulkScorecardsChecks(List<ScorecardsChecksRecord> scorecardsChecks) {
    if (isEmpty(scorecardsChecks)) {
      log.warn("No active scorecards_checks found to migrate");
      return;
    }

    try {
      InsertValuesStep4<ScorecardsChecksRecord, String, String, String, Boolean> bulkInsert =
          dsl.insertInto(Tables.SCORECARDS_CHECKS, Tables.SCORECARDS_CHECKS.ACCOUNT_IDENTIFIER,
              Tables.SCORECARDS_CHECKS.SCORECARD_IDENTIFIER, Tables.SCORECARDS_CHECKS.CHECK_IDENTIFIER,
              Tables.SCORECARDS_CHECKS.CUSTOM);
      scorecardsChecks.forEach(scorecardChecks
          -> bulkInsert.values(scorecardChecks.getAccountIdentifier(), scorecardChecks.getScorecardIdentifier(),
              scorecardChecks.getCheckIdentifier(), scorecardChecks.getCustom()));
      int size = bulkInsert.onConflictOnConstraint(Tables.SCORECARDS_CHECKS.getPrimaryKey()).doNothing().execute();
      log.info("Successfully inserted data in scorecards_checks batch size {} for ids: {}", size,
          scorecardsChecks.stream()
              .map(scorecardsChecksRecord
                  -> scorecardsChecksRecord.getAccountIdentifier() + DOT_SEPARATOR
                      + scorecardsChecksRecord.getScorecardIdentifier() + DOT_SEPARATOR
                      + scorecardsChecksRecord.getCheckIdentifier())
              .toList());
    } catch (Exception e) {
      log.error("Exception while bulk insert scorecards_checks", e);
    }
  }

  private String constructFilters(ScorecardFilter scorecardFilter) {
    if (scorecardFilter != null) {
      String filter = scorecardFilter.getKind() + " | " + scorecardFilter.getType();
      if (!isEmpty(scorecardFilter.getOwners())) {
        filter = filter + " | " + String.join(", ", scorecardFilter.getOwners());
      }
      if (!isEmpty(scorecardFilter.getTags())) {
        filter = filter + " | " + String.join(", ", scorecardFilter.getTags());
      }
      if (!isEmpty(scorecardFilter.getLifecycle())) {
        filter = filter + " | " + String.join(", ", scorecardFilter.getLifecycle());
      }
      if (!isEmpty(scorecardFilter.getScopes())) {
        filter = filter + " | " + String.join(", ", scorecardFilter.getScopes());
      }

      return filter;
    }
    return null;
  }
}
