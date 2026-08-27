/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service.impl;

import static io.harness.NGDateUtils.YEAR_MONTH_DAY_DATE_PATTERN;
import static io.harness.NGDateUtils.getLocalDateOrThrow;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.DateUtils.dateToLocateDate;
import static io.harness.idp.common.DateUtils.getPreviousDay24HourTimeFrame;
import static io.harness.idp.common.DateUtils.localDateToDate;
import static io.harness.idp.common.DateUtils.yesterdayDateInStringAndDateFormat;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTOV2;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendResponse;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.mappers.ActiveDevelopersDailyCountEntityMapper;
import io.harness.idp.license.usage.mappers.ActiveDevelopersEntityMapper;
import io.harness.idp.license.usage.repositories.ActiveDevelopersDailyCountRepository;
import io.harness.idp.license.usage.repositories.ActiveDevelopersRepository;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.ngmanager.NgConnectorManagerClient;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.records.ActiveDevelopersRecord;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep5;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IDPModuleLicenseUsageImpl implements IDPModuleLicenseUsage {
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject NgConnectorManagerClient ngConnectorManagerClient;
  @Inject @Named("internalAccounts") private List<String> internalAccounts;
  @Inject ActiveDevelopersRepository activeDevelopersRepository;
  @Inject ActiveDevelopersDailyCountRepository activeDevelopersDailyCountRepository;
  @Inject DSLContext dsl;
  private static final Integer BATCH_SIZE = 100;

  private static final List<Pattern> URL_PATHS_PATTERN_FOR_LICENSE_USAGE_CAPTURE = List.of(
      Pattern.compile("v1/status-info.*"), Pattern.compile("v2/status-info.*"), Pattern.compile("v1/onboarding.*"),
      Pattern.compile("v1/plugins-info.*"), Pattern.compile("v1/plugin-toggle.*"), Pattern.compile("v1/app-config.*"),
      Pattern.compile("v1/plugin/request.*"), Pattern.compile("v1/merged-plugins-config.*"),
      Pattern.compile("v1/configuration-entities.*"), Pattern.compile("v1/auth-info.*"),
      Pattern.compile("v1/scorecards.*"), Pattern.compile("v1/scores.*"), Pattern.compile("v2/scores.*"),
      Pattern.compile("v1/checks.*"), Pattern.compile("v1/data-sources.*"), Pattern.compile("v1/layout.*"),
      Pattern.compile("v1/backstage-permissions.*"), Pattern.compile("v1/connector-info.*"),
      Pattern.compile("v1/allow-list.*"), Pattern.compile("v1/entity-facets.*"),
      Pattern.compile("v1/backstage-env-variables/batch.*"), Pattern.compile("/v1/custom-plugins.*"),
      Pattern.compile("v1/integrations.*"), Pattern.compile("v2/onboarding.*"), Pattern.compile("v1/entities.*"),
      Pattern.compile("/v1/aggregation-rules.*"), Pattern.compile("/v1/catalog/custom-properties.*"),
      Pattern.compile("v1/groups.*"), Pattern.compile("v1/home-page-layout.*"), Pattern.compile("v1/icons.*"),
      Pattern.compile("v1/entity-refs.*"), Pattern.compile("v1/external-proxy.*"),
      Pattern.compile("v2/custom-plugins.*"), Pattern.compile("v1/workflow-executions/history.*"),
      Pattern.compile("v1/kinds.*"), Pattern.compile("v1/graph/entity"), Pattern.compile("v1/teams.*"));

  @Override
  public boolean checkIfUrlPathCapturesLicenseUsage(String urlPath) {
    for (Pattern urlPattern : URL_PATHS_PATTERN_FOR_LICENSE_USAGE_CAPTURE) {
      if (urlPattern.matcher(urlPath).matches()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void captureLicenseUsageInRedis(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture) {
    idpServiceMiscRedisProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        idpLicenseUsageUserCapture.getAccountIdentifier(), idpLicenseUsageUserCapture.getUserIdentifier(),
        idpLicenseUsageUserCapture.getEmail(), idpLicenseUsageUserCapture.getUserName(),
        idpLicenseUsageUserCapture.getAccessedAt());
  }

  @Override
  public void saveLicenseUsageInDB(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture) {
    boolean isHarnessSupportUser =
        getResponse(ngConnectorManagerClient.isHarnessSupportUser(idpLicenseUsageUserCapture.getUserIdentifier()));
    if (!isHarnessSupportUser || internalAccounts.contains(idpLicenseUsageUserCapture.getAccountIdentifier())) {
      ActiveDevelopersEntity activeDevelopersEntity = ActiveDevelopersEntityMapper.fromDto(idpLicenseUsageUserCapture);
      Optional<ActiveDevelopersEntity> optionalActiveDevelopersEntity =
          activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
              idpLicenseUsageUserCapture.getAccountIdentifier(), idpLicenseUsageUserCapture.getUserIdentifier());
      optionalActiveDevelopersEntity.ifPresent(activeDevelopersEntityExisting -> {
        activeDevelopersEntity.setId(activeDevelopersEntityExisting.getId());
        activeDevelopersEntity.setCreatedAt(activeDevelopersEntityExisting.getCreatedAt());
      });
      activeDevelopersRepository.save(activeDevelopersEntity);
    }
  }

  @Override
  public void licenseUsageDailyCountAggregationPerAccount() {
    List<ActiveDevelopersEntity> activeDevelopersEntities = getActiveDevelopersPerDay();
    Map<String, Set<String>> accountToPreviousDayUniqueUsersMap =
        activeDevelopersEntities.stream().collect(Collectors.groupingBy(ActiveDevelopersEntity::getAccountIdentifier,
            Collectors.mapping(ActiveDevelopersEntity::getUserIdentifier, Collectors.toSet())));
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities =
        prepareActiveDevelopersDailyCountEntitiesForSave(accountToPreviousDayUniqueUsersMap);
    activeDevelopersDailyCountRepository.saveAll(activeDevelopersDailyCountEntities);
  }

  @Override
  public List<ActiveDevelopersEntity> getActiveDevelopersPerDay() {
    Pair<Long, Long> previousDay24HourTimeFrame = getPreviousDay24HourTimeFrame();
    log.info("Fetching data between {} {} for license usage daily count aggregation per account",
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    List<ActiveDevelopersEntity> activeDevelopersEntities = activeDevelopersRepository.findByLastAccessedAtBetween(
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    log.info("Found {} active developers for all accounts between {} {}", activeDevelopersEntities.size(),
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    return activeDevelopersEntities;
  }

  @Override
  public void populateActiveDevelopersDataForSync() {
    Pair<Long, Long> previousDay24HourTimeFrame = getPreviousDay24HourTimeFrame();
    List<ActiveDevelopersEntity> activeDevelopersEntities = activeDevelopersRepository.findByLastAccessedAtBetween(
        previousDay24HourTimeFrame.getLeft(), previousDay24HourTimeFrame.getRight());
    List<List<ActiveDevelopersEntity>> partitionedActiveDevelopers =
        Lists.partition(activeDevelopersEntities, BATCH_SIZE);
    log.info("Start inserting batch into active_developers, total active developers: {}, "
            + "total partitions: {}",
        activeDevelopersEntities.size(), partitionedActiveDevelopers.size());
    partitionedActiveDevelopers.forEach(this::insertBulkActiveDevelopers);
  }

  @Override
  public void migrateActiveDevelopersData(String accountIdentifier) {
    List<ActiveDevelopersEntity> activeDevelopersEntities =
        activeDevelopersRepository.findByAccountIdentifier(accountIdentifier);
    List<List<ActiveDevelopersEntity>> partitionedActiveDevelopers =
        Lists.partition(activeDevelopersEntities, BATCH_SIZE);
    log.info("Start inserting batch into active_developers, total active developers: {}, "
            + "total partitions: {}, accountId: {}",
        activeDevelopersEntities.size(), partitionedActiveDevelopers.size(), accountIdentifier);
    partitionedActiveDevelopers.forEach(this::insertBulkActiveDevelopers);
  }

  @Override
  public List<ActiveDevelopersTrendCountDTO> getHistoryTrend(
      String accountIdentifier, IDPLicenseDateUsageParams idpLicenseDateUsageParams) {
    LocalDate fromDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getFromDate());
    LocalDate toDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getToDate());
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities =
        activeDevelopersDailyCountRepository.findByAccountIdentifierAndDateInDateFormatBetween(
            accountIdentifier, localDateToDate(fromDate), localDateToDate(toDate));
    if (idpLicenseDateUsageParams.getReportType().equals(LicenseDateUsageReportType.MONTHLY)) {
      return monthlyData(activeDevelopersDailyCountEntities);
    } else {
      return dailyData(activeDevelopersDailyCountEntities);
    }
  }

  @Override
  public ActiveDevelopersTrendResponse getHistoryTrendV2(
      String accountIdentifier, IDPLicenseDateUsageParams idpLicenseDateUsageParams) {
    LocalDate fromDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getFromDate());
    LocalDate toDate = getLocalDateOrThrow(YEAR_MONTH_DAY_DATE_PATTERN, idpLicenseDateUsageParams.getToDate());
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities =
        activeDevelopersDailyCountRepository.findByAccountIdentifierAndDateInDateFormatBetween(
            accountIdentifier, localDateToDate(fromDate), localDateToDate(toDate));
    List<ActiveDevelopersTrendCountDTOV2> activeDevelopersTrendCountDTOV2List;
    if (idpLicenseDateUsageParams.getReportType().equals(LicenseDateUsageReportType.MONTHLY)) {
      activeDevelopersTrendCountDTOV2List = monthlyDataV2(activeDevelopersDailyCountEntities);
    } else {
      activeDevelopersTrendCountDTOV2List = dailyDataV2(activeDevelopersDailyCountEntities);
    }
    long peak = activeDevelopersTrendCountDTOV2List.stream()
                    .mapToLong(ActiveDevelopersTrendCountDTOV2::getCount)
                    .max()
                    .orElse(0);
    Set<String> totalUniqueDevelopers = activeDevelopersTrendCountDTOV2List.stream()
                                            .map(ActiveDevelopersTrendCountDTOV2::getUniqueUserIdentifiers)
                                            .filter(Objects::nonNull)
                                            .flatMap(Set::stream)
                                            .collect(Collectors.toSet());

    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOList =
        activeDevelopersTrendCountDTOV2List.stream()
            .map(v2 -> ActiveDevelopersTrendCountDTO.builder().date(v2.getDate()).count(v2.getCount()).build())
            .collect(Collectors.toList());

    return ActiveDevelopersTrendResponse.builder()
        .activeDevelopersTrendList(activeDevelopersTrendCountDTOList)
        .peak(peak)
        .totalUniqueDevelopers(totalUniqueDevelopers.size())
        .build();
  }

  @Override
  public long getActiveDevelopers(String accountIdentifier) {
    return activeDevelopersRepository.findByAccountIdentifier(accountIdentifier).size();
  }

  private List<ActiveDevelopersDailyCountEntity> prepareActiveDevelopersDailyCountEntitiesForSave(
      Map<String, Set<String>> accountToPreviousDayUniqueUsersMap) {
    Pair<String, Date> yesterdayDateInStringAndDateFormat = yesterdayDateInStringAndDateFormat();
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities = new ArrayList<>();
    accountToPreviousDayUniqueUsersMap.forEach((k, v) -> {
      ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
          ActiveDevelopersDailyCountEntity.builder()
              .accountIdentifier(k)
              .dateInStringFormat(yesterdayDateInStringAndDateFormat.getLeft())
              .dateInDateFormat(yesterdayDateInStringAndDateFormat.getRight())
              .uniqueUserIdentifiers(v)
              .count(v.size())
              .build();
      activeDevelopersDailyCountEntities.add(activeDevelopersDailyCountEntity);
    });
    return activeDevelopersDailyCountEntities;
  }

  private List<ActiveDevelopersTrendCountDTO> monthlyData(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOS = new ArrayList<>();

    Map<String, Integer> monthlyTrend = new HashMap<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity -> {
      LocalDate localDate = dateToLocateDate(activeDevelopersDailyCountEntity.getDateInDateFormat());
      int year = localDate.getYear();
      int month = localDate.getMonthValue();

      int max = monthlyTrend.getOrDefault(year + "-" + month, 0);
      if (activeDevelopersDailyCountEntity.getCount() > max) {
        max = (int) activeDevelopersDailyCountEntity.getCount();
      }

      monthlyTrend.put(year + "-" + month, max);
    });

    monthlyTrend.forEach(
        (k, v) -> activeDevelopersTrendCountDTOS.add(ActiveDevelopersTrendCountDTO.builder().date(k).count(v).build()));

    return activeDevelopersTrendCountDTOS;
  }

  private List<ActiveDevelopersTrendCountDTOV2> monthlyDataV2(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTOV2> activeDevelopersTrendCountDTOS = new ArrayList<>();

    Map<String, Pair<Set<String>, Integer>> monthlyUniqueUsers = new HashMap<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity -> {
      LocalDate localDate = dateToLocateDate(activeDevelopersDailyCountEntity.getDateInDateFormat());
      int year = localDate.getYear();
      int month = localDate.getMonthValue();
      String monthKey = year + "-" + month;
      Set<String> userIds = monthlyUniqueUsers.getOrDefault(monthKey, Pair.of(new HashSet<>(), 0)).getLeft();
      if (activeDevelopersDailyCountEntity.getUniqueUserIdentifiers() != null) {
        userIds.addAll(activeDevelopersDailyCountEntity.getUniqueUserIdentifiers());
      }
      int max = monthlyUniqueUsers.getOrDefault(monthKey, Pair.of(Collections.emptySet(), 0)).getRight();
      if (activeDevelopersDailyCountEntity.getCount() > max) {
        max = (int) activeDevelopersDailyCountEntity.getCount();
      }
      monthlyUniqueUsers.put(monthKey, Pair.of(userIds, max));
    });

    monthlyUniqueUsers.forEach((k, v)
                                   -> activeDevelopersTrendCountDTOS.add(ActiveDevelopersTrendCountDTOV2.builder()
                                                                             .date(k)
                                                                             .count(v.getRight())
                                                                             .uniqueUserIdentifiers(v.getLeft())
                                                                             .build()));

    return activeDevelopersTrendCountDTOS;
  }

  private List<ActiveDevelopersTrendCountDTO> dailyData(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOS = new ArrayList<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity
        -> activeDevelopersTrendCountDTOS.add(
            ActiveDevelopersDailyCountEntityMapper.toDto(activeDevelopersDailyCountEntity)));

    return activeDevelopersTrendCountDTOS;
  }

  private List<ActiveDevelopersTrendCountDTOV2> dailyDataV2(
      List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities) {
    List<ActiveDevelopersTrendCountDTOV2> activeDevelopersTrendCountDTOS = new ArrayList<>();

    activeDevelopersDailyCountEntities.forEach(activeDevelopersDailyCountEntity
        -> activeDevelopersTrendCountDTOS.add(
            ActiveDevelopersDailyCountEntityMapper.toDtoV2(activeDevelopersDailyCountEntity)));

    return activeDevelopersTrendCountDTOS;
  }

  private void insertBulkActiveDevelopers(List<ActiveDevelopersEntity> activeDevelopersEntities) {
    if (isEmpty(activeDevelopersEntities)) {
      log.warn("No active developers found to migrate");
      return;
    }
    try {
      InsertValuesStep5<ActiveDevelopersRecord, String, String, String, String, Long> bulkInsert = dsl.insertInto(
          Tables.ACTIVE_DEVELOPERS, Tables.ACTIVE_DEVELOPERS.ACCOUNT_IDENTIFIER, Tables.ACTIVE_DEVELOPERS.IDENTIFIER,
          Tables.ACTIVE_DEVELOPERS.EMAIL, Tables.ACTIVE_DEVELOPERS.NAME, Tables.ACTIVE_DEVELOPERS.LAST_ACCESSED_AT);
      activeDevelopersEntities.forEach(activeDevelopersEntity
          -> bulkInsert.values(activeDevelopersEntity.getAccountIdentifier(),
              activeDevelopersEntity.getUserIdentifier(), activeDevelopersEntity.getEmail(),
              activeDevelopersEntity.getUserName(), activeDevelopersEntity.getLastAccessedAt()));

      int size =
          bulkInsert.onConflictOnConstraint(Tables.ACTIVE_DEVELOPERS.getPrimaryKey())
              .doUpdate()
              .set(Tables.ACTIVE_DEVELOPERS.LAST_ACCESSED_AT, Tables.ACTIVE_DEVELOPERS.as("excluded").LAST_ACCESSED_AT)
              .execute();
      log.info("Successfully inserted active_developers batch size {}", size);
    } catch (Exception e) {
      log.error("Exception while bulk insert active_developers", e);
    }
  }
}
