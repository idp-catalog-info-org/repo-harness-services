/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.licensing;

import static io.harness.beans.licensing.api.CILicense.CILicenseBuilder;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.contracts.plan.TriggerType.SCHEDULER_CRON;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK_CUSTOM;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.account.services.AccountClient;
import io.harness.beans.licensing.api.ActiveDevelopersDTO;
import io.harness.beans.licensing.api.CIDevelopersFilterParams;
import io.harness.beans.licensing.api.CILicense;
import io.harness.beans.licensing.api.CILicenseHistoryDTO;
import io.harness.beans.licensing.api.CILicenseType;
import io.harness.beans.licensing.api.CILicenseUsageDTO;
import io.harness.beans.licensing.api.CIUsageRequestParams;
import io.harness.beans.licensing.api.CreditConsumed;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.utils.CreditStatus;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.filter.CSVRequestParams;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Singleton
@Slf4j
public class CILicenseUsageImpl implements LicenseUsageInterface<CILicenseUsageDTO, CIUsageRequestParams> {
  private static final long HR_IN_MS = 60 * 60 * 1000L;
  private static final long DAY_IN_MS = 24 * HR_IN_MS;
  private static final String COLUMN_NAME_AUTHOR_ID = "moduleinfo_author_id";
  private static final String COLUMN_NAME_PROJECT_ID = "projectidentifier";
  private static final String COLUMN_NAME_ORG_ID = "orgidentifier";
  private static final String COLUMN_NAME_LAST_BUILD = "buildtime";
  private static final String COLUMN_NAME_TOTAL = "total";
  private static final String QUERY_END = ";";
  private static final String SORT_DEVELOPER_ID = "identifier";
  private static final String SORT_ORG_ID = "orgIdentifier";
  private static final String SORT_PROJECT_ID = "projectIdentifier";
  private static final String SORT_LAST_BUILD = "lastBuild";
  private static final String ASCENDING = "ASC";
  private static final String DECENDING = "DESC";

  private static final String TRIGGER_TYPES =
      String.join(",", Arrays.asList("'" + MANUAL + "'", "'" + WEBHOOK_CUSTOM + "'", "'" + SCHEDULER_CRON + "'"));
  private static final String QUERY_COLUMN_WITH_TOTAL =
      String.format("select %s, %s, %s, max(startts) as %s, count(*) over () as %s", COLUMN_NAME_AUTHOR_ID,
          COLUMN_NAME_PROJECT_ID, COLUMN_NAME_ORG_ID, COLUMN_NAME_LAST_BUILD, COLUMN_NAME_TOTAL);
  private static final String QUERY_COLUMN_WITH_NO_TOTAL = String.format("select %s, %s, %s, max(startts) as %s",
      COLUMN_NAME_AUTHOR_ID, COLUMN_NAME_PROJECT_ID, COLUMN_NAME_ORG_ID, COLUMN_NAME_LAST_BUILD);
  private static final String QUERY_COLUMN_COUNT_ONLY =
      String.format("select count(distinct %s) as %s", COLUMN_NAME_AUTHOR_ID, COLUMN_NAME_TOTAL);
  private static final String QUERY_BODY = String.format(" from pipeline_execution_summary_ci"
          + " where accountid=? and moduleinfo_type ='CI'"
          + " and %s is not null"
          + " and moduleinfo_is_private=true"
          + " and (trigger_type='%s' OR (trigger_type IN (%s) AND user_source='GIT'))"
          + " and startts<=? and startts>=?",
      COLUMN_NAME_AUTHOR_ID, WEBHOOK, TRIGGER_TYPES);
  private static final String DEVELOPER_QUERY_BODY = QUERY_COLUMN_WITH_TOTAL + QUERY_BODY;

  private static final String ORDER_BY_CLAUSE = " order by %s";
  private static final String PAGER_OFFSET = " offset ? rows fetch next ? rows only";
  private static final String GROUP_BY_QUERY =
      String.format(" group by %s, %s, %s", COLUMN_NAME_AUTHOR_ID, COLUMN_NAME_PROJECT_ID, COLUMN_NAME_ORG_ID);
  private static final String DEVELOPER_ID_CONDITION = String.format(" and %s=?", COLUMN_NAME_AUTHOR_ID);
  private static final String PROJECT_ID_CONDITION = String.format(" and %s=?", COLUMN_NAME_PROJECT_ID);
  private static final String ORG_ID_CONDITION = String.format(" and %s=?", COLUMN_NAME_ORG_ID);
  private static final String DEVELOPER_BY_BUILD_TIME_QUERY = DEVELOPER_QUERY_BODY + GROUP_BY_QUERY + QUERY_END;
  private static final String DEVELOPER_QUERY_NO_TOTAL =
      QUERY_COLUMN_WITH_NO_TOTAL + QUERY_BODY + GROUP_BY_QUERY + QUERY_END;
  private static final String LIST_DEVELOPER_QUERY = String.format("select distinct %s"
          + " from pipeline_execution_summary_ci"
          + " where accountid=?"
          + " and moduleinfo_type ='CI'"
          + " and %s is not null"
          + " and moduleinfo_is_private=true"
          + " and (trigger_type='%s' OR (trigger_type IN (%s) AND user_source='GIT'))"
          + " and startts<=? and startts>=?;",
      COLUMN_NAME_AUTHOR_ID, COLUMN_NAME_AUTHOR_ID, WEBHOOK, TRIGGER_TYPES);

  private static final Map<String, String> sortToColumnMap = new HashMap<>() {
    {
      put(SORT_DEVELOPER_ID, COLUMN_NAME_AUTHOR_ID);
      put(SORT_PROJECT_ID, COLUMN_NAME_PROJECT_ID);
      put(SORT_ORG_ID, COLUMN_NAME_ORG_ID);
      put(SORT_LAST_BUILD, COLUMN_NAME_LAST_BUILD);
    }
  };

  @Inject TimeScaleDBService timeScaleDBService;
  @Inject(optional = true) @Nullable private AccountClient accountClient;
  @Inject(optional = true) @Nullable private NgLicenseHttpClient ngLicenseHttpClient;
  @Inject(optional = true) @Nullable private CIOverviewDashboardService ciOverviewDashboardService;
  @Inject(optional = true) @Nullable private CILicenseUsageImpl ciLicenseUsageService;
  @Inject private CIFeatureFlagService featureFlagService;

  @Override
  public CILicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, CIUsageRequestParams usageRequest) {
    validateInput(accountIdentifier, module, timestamp);
    return getLicenseUsageByQuery(accountIdentifier, module, timestamp, usageRequest, DEVELOPER_BY_BUILD_TIME_QUERY);
  }

  public CILicenseUsageDTO getLicenseUsageByQuery(
      String accountIdentifier, ModuleType module, long timestamp, CIUsageRequestParams usageRequest, String query) {
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setString(1, accountIdentifier);
      statement.setLong(2, timestamp);
      statement.setLong(3, timestamp - 30 * DAY_IN_MS);
      resultSet = statement.executeQuery();
      List<ReferenceDTO> usageReferences = new ArrayList<>();
      Set<String> uniqueIds = new HashSet<>();
      while (resultSet != null && resultSet.next()) {
        String id = resultSet.getString(COLUMN_NAME_AUTHOR_ID);
        if (isEmpty(id)) {
          continue;
        }
        ReferenceDTO reference = ReferenceDTO.builder()
                                     .identifier(id)
                                     .projectIdentifier(resultSet.getString(COLUMN_NAME_PROJECT_ID))
                                     .orgIdentifier(resultSet.getString(COLUMN_NAME_ORG_ID))
                                     .accountIdentifier(accountIdentifier)
                                     .count(1)
                                     .build();
        uniqueIds.add(id);
        usageReferences.add(reference);
      }
      return CILicenseUsageDTO.builder()
          .activeCommitters(UsageDataDTO.builder()
                                .count(uniqueIds.size())
                                .displayName("Last 30 Days")
                                .references(usageReferences)
                                .build())
          .ciLicenseType(CILicenseType.DEVELOPERS)
          .build();
    } catch (SQLException ex) {
      log.error("Caught SQL Exception:" + ex.getMessage());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  @Override
  public Page<ActiveDevelopersDTO> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTsInMs, PageableUsageRequestParams usageRequestParams) {
    validateInput(accountIdentifier, module, currentTsInMs);
    return listLicenseUsageByQuery(
        accountIdentifier, module, currentTsInMs, usageRequestParams, DEVELOPER_QUERY_BODY, DEVELOPER_ID_CONDITION);
  }

  public Page<ActiveDevelopersDTO> listLicenseUsageByQuery(String accountIdentifier, ModuleType module,
      long currentTsInMs, PageableUsageRequestParams usageRequestParams, String query, String developerIdCondition) {
    int pageNumber = 0;
    int pageSize = 30;
    String sortQuery = null;
    if (usageRequestParams != null && usageRequestParams.getPageRequest() != null) {
      Pageable pageable = usageRequestParams.getPageRequest();
      pageNumber = pageable.getPageNumber();
      pageSize = pageable.getPageSize();
      List<String> orderByFields = new ArrayList<>();
      for (Map.Entry<String, String> entry : sortToColumnMap.entrySet()) {
        Sort.Order sort = pageable.getSort().getOrderFor(entry.getKey());
        if (sort != null) {
          orderByFields.add(entry.getValue() + " " + (sort.isAscending() ? ASCENDING : DECENDING));
        }
      }
      sortQuery = format(ORDER_BY_CLAUSE, String.join(", ", orderByFields));
    }

    CIDevelopersFilterParams filterParams =
        (CIDevelopersFilterParams) ((DefaultPageableUsageRequestParams) usageRequestParams).getFilterParams();
    List<String> extraParam = new ArrayList<>();
    if (filterParams != null) {
      if (!isEmpty(filterParams.getDeveloper())) {
        extraParam.add(filterParams.getDeveloper());
        query = query + developerIdCondition;
      }
      if (!isEmpty(filterParams.getOrgIdentifier())) {
        extraParam.add(filterParams.getOrgIdentifier());
        query = query + ORG_ID_CONDITION;
      }
      if (!isEmpty(filterParams.getProjectIdentifier())) {
        extraParam.add(filterParams.getProjectIdentifier());
        query = query + PROJECT_ID_CONDITION;
      }
    }
    String groupAndOrder = GROUP_BY_QUERY;
    if (sortQuery != null) {
      groupAndOrder = groupAndOrder + sortQuery;
    }
    if (usageRequestParams.getPageRequest() != null && usageRequestParams.getPageRequest().isPaged()) {
      query = query + groupAndOrder + PAGER_OFFSET + QUERY_END;
    } else {
      query = query + groupAndOrder + QUERY_END;
    }
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setString(1, accountIdentifier);
      statement.setLong(2, currentTsInMs);
      statement.setLong(3, currentTsInMs - 30 * DAY_IN_MS);
      for (int i = 0; i < extraParam.size(); i++) {
        statement.setString(i + 4, extraParam.get(i));
      }
      if (usageRequestParams.getPageRequest() != null && usageRequestParams.getPageRequest().isPaged()) {
        statement.setInt(extraParam.size() + 4, pageNumber * pageSize);
        statement.setInt(extraParam.size() + 5, pageSize);
      }
      resultSet = statement.executeQuery();
      List<ActiveDevelopersDTO> developerList = new ArrayList<>();
      long total = -1L;
      while (resultSet != null && resultSet.next()) {
        String id = resultSet.getString(COLUMN_NAME_AUTHOR_ID);
        if (isEmpty(id)) {
          continue;
        }
        ActiveDevelopersDTO developersDTO = ActiveDevelopersDTO.builder()
                                                .identifier(id)
                                                .projectIdentifier(resultSet.getString(COLUMN_NAME_PROJECT_ID))
                                                .orgIdentifier(resultSet.getString(COLUMN_NAME_ORG_ID))
                                                .lastBuild(resultSet.getLong(COLUMN_NAME_LAST_BUILD))
                                                .timestamp(currentTsInMs)
                                                .build();
        developerList.add(developersDTO);
        total = resultSet.getLong(COLUMN_NAME_TOTAL);
      }
      return new PageImpl<>(developerList, usageRequestParams.getPageRequest(), total);
    } catch (SQLException ex) {
      log.error("Caught SQL Exception:" + ex.getMessage());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  @Override
  public File getLicenseUsageCSVReport(String accountIdentifier, ModuleType moduleType, long currentTsInMs,
      Optional<CSVRequestParams> csvRequestParams) {
    validateInput(accountIdentifier, moduleType, currentTsInMs);
    return getLicenseUsageCSVReportByQuery(accountIdentifier, moduleType, currentTsInMs, DEVELOPER_QUERY_NO_TOTAL);
  }

  public File getLicenseUsageCSVReportByQuery(
      String accountIdentifier, ModuleType moduleType, long currentTsInMs, String query) {
    ResultSet resultSet = null;
    try {
      Path dirPath = createAccountCSVReportDirIfNotExist(accountIdentifier);
      Path csvReportFilePath = getAccountCSVReportFilePath(dirPath, accountIdentifier, currentTsInMs);
      File file = csvReportFilePath.toFile();
      CSVWriter writer = new CSVWriter(new FileWriter(file), '\t');
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setLong(2, currentTsInMs);
        statement.setLong(3, currentTsInMs - 30 * DAY_IN_MS);
        resultSet = statement.executeQuery();
        writer.writeAll(resultSet, true);
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
        writer.close();
      }
      return file;
    } catch (IOException e) {
      log.error("Caught IO Exception while trying to download CSV:" + e.getMessage());
    }

    return null;
  }

  public Set<String> listActiveDevelopers(String accountIdentifier, long currentTsInMs) {
    validateInput(accountIdentifier, ModuleType.CI, currentTsInMs);
    return listActiveDevelopersByQuery(accountIdentifier, currentTsInMs, LIST_DEVELOPER_QUERY);
  }

  public Set<String> listActiveDevelopersByQuery(String accountIdentifier, long currentTsInMs, String query) {
    ResultSet resultSet = null;
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setString(1, accountIdentifier);
      statement.setLong(2, currentTsInMs);
      statement.setLong(3, currentTsInMs - 30 * DAY_IN_MS);
      resultSet = statement.executeQuery();
      Set<String> uniqueIds = new HashSet<>();
      while (resultSet != null && resultSet.next()) {
        String id = resultSet.getString(COLUMN_NAME_AUTHOR_ID);
        if (isEmpty(id)) {
          continue;
        }
        uniqueIds.add(id);
      }
      return uniqueIds;
    } catch (SQLException ex) {
      log.error("Caught SQL Exception:" + ex.getMessage());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  public CILicenseHistoryDTO getLicenseHistoryUsage(
      String accountIdentifier, CILicenseType licenseType, CIDevelopersFilterParams filterParams) {
    Map<String, Integer> resultMap = new HashMap<>();
    long time = System.currentTimeMillis();
    for (int i = 0; i < 30; i++) {
      long cursorTime = time - i * DAY_IN_MS;
      SimpleDateFormat dmyFormat = new SimpleDateFormat("yyyy-MM-dd");
      String date = dmyFormat.format(new Date(cursorTime));
      resultMap.put(date, getCountOfDevelopersPerDay(cursorTime, accountIdentifier, filterParams));
    }
    return CILicenseHistoryDTO.builder().licenseUsage(resultMap).licenseType(licenseType).build();
  }

  public CILicense getCILicense(String accountIdentifier) {
    CILicenseBuilder builder = CILicense.builder().account_id(accountIdentifier).license_type(CILicenseType.DEVELOPERS);

    try {
      AccountDTO accountDto = CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
      builder.account_name(accountDto.getName());
    } catch (Exception e) {
      log.error(format("Exception during fetching account dto for account %s", accountIdentifier), e);
    }

    try {
      LicensesWithSummaryDTO licensesWithSummaryDTO =
          NGRestUtils.getResponse(ngLicenseHttpClient.getLicenseSummary(accountIdentifier, ModuleType.CI.toString()));
      if (licensesWithSummaryDTO instanceof CILicenseSummaryDTO) {
        CILicenseSummaryDTO ciLicensesWithSummaryDTO = (CILicenseSummaryDTO) licensesWithSummaryDTO;
        builder.users_provisioned(ciLicensesWithSummaryDTO.totalDevelopers);
        builder.developers_purchased(ciLicensesWithSummaryDTO.totalDevelopers);
      }
    } catch (Exception e) {
      log.error(format("Exception during fetching license summary for account %s", accountIdentifier), e);
      builder.users_provisioned(-1);
      builder.developers_purchased(-1);
    }

    long minimumPurchaseTime = Long.MAX_VALUE;
    try {
      List<CreditDTO> creditDTOs = NGRestUtils.getResponse(ngLicenseHttpClient.getCredits(accountIdentifier));
      long creditsPurchased = getCreditsPurchased(creditDTOs);
      minimumPurchaseTime = getMinimumPurchaseTime(creditDTOs);
      builder.credit_purchased(creditsPurchased);
    } catch (Exception e) {
      log.error(format("Exception during fetching credit DTOs for account %s", accountIdentifier), e);
      builder.credit_purchased(-1);
    }

    if (minimumPurchaseTime == Long.MAX_VALUE) {
      builder.credit_consumed(CreditConsumed.builder().contract_date(-1).count(-1).build());
    } else {
      try {
        long creditsConsumed = ciOverviewDashboardService.getHostedCreditUsage(
            accountIdentifier, minimumPurchaseTime, System.currentTimeMillis());
        builder.credit_consumed(
            CreditConsumed.builder().contract_date(minimumPurchaseTime).count(creditsConsumed).build());
      } catch (Exception e) {
        log.error(format("Exception during fetching credit usages for account %s", accountIdentifier), e);
        builder.credit_consumed(CreditConsumed.builder().contract_date(minimumPurchaseTime).count(-1).build());
      }
    }

    try {
      Set<String> developers =
          ciLicenseUsageService.listActiveDevelopers(accountIdentifier, System.currentTimeMillis());
      builder.active_developers(developers.size());
      builder.developers(developers.stream().toList());
    } catch (Exception e) {
      builder.active_developers(-1);
      log.error(format("Exception during fetching active developers for account %s", accountIdentifier), e);
    }

    return builder.build();
  }

  private long getCreditsPurchased(List<CreditDTO> creditDTOs) {
    long totalCredits = 0;
    long currentTime = System.currentTimeMillis();
    for (CreditDTO dto : creditDTOs) {
      if (dto.getCreditStatus() != CreditStatus.EXPIRED && dto.getPurchaseTime() < currentTime
          && dto.getExpiryTime() > currentTime) {
        totalCredits = totalCredits + Math.max(dto.getQuantity(), 0);
      }
    }
    return totalCredits;
  }

  private long getMinimumPurchaseTime(List<CreditDTO> creditDTOs) {
    long minimumPurchaseTime = Long.MAX_VALUE;
    for (CreditDTO dto : creditDTOs) {
      if (dto.getCreditStatus() == CreditStatus.ACTIVE) {
        minimumPurchaseTime = Math.min(dto.getPurchaseTime(), minimumPurchaseTime);
      }
    }
    return minimumPurchaseTime;
  }

  private int getCountOfDevelopersPerDay(
      long timestamp, String accountIdentifier, CIDevelopersFilterParams filterParams) {
    return getCountOfDevelopersPerDayByQuery(
        timestamp, accountIdentifier, filterParams, QUERY_COLUMN_COUNT_ONLY + QUERY_BODY, DEVELOPER_ID_CONDITION);
  }

  private int getCountOfDevelopersPerDayByQuery(long timestamp, String accountIdentifier,
      CIDevelopersFilterParams filterParams, String query, String developerIdCondition) {
    List<String> extraParam = new ArrayList<>();
    if (filterParams != null) {
      if (!isEmpty(filterParams.getDeveloper())) {
        extraParam.add(filterParams.getDeveloper());
        query = query + developerIdCondition;
      }
      if (!isEmpty(filterParams.getOrgIdentifier())) {
        extraParam.add(filterParams.getOrgIdentifier());
        query = query + ORG_ID_CONDITION;
      }
      if (!isEmpty(filterParams.getProjectIdentifier())) {
        extraParam.add(filterParams.getProjectIdentifier());
        query = query + PROJECT_ID_CONDITION;
      }
    }
    query = query + QUERY_END;
    ResultSet resultSet = null;

    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setString(1, accountIdentifier);
      statement.setLong(2, timestamp);
      statement.setLong(3, timestamp - 30 * DAY_IN_MS);
      for (int i = 0; i < extraParam.size(); i++) {
        statement.setString(i + 4, extraParam.get(i));
      }
      resultSet = statement.executeQuery();
      if (resultSet != null && resultSet.next()) {
        return resultSet.getInt(COLUMN_NAME_TOTAL);
      }
    } catch (SQLException ex) {
      log.error("Caught SQL Exception:" + ex.getMessage());
    } finally {
      DBUtils.close(resultSet);
    }
    return -1;
  }

  private void validateInput(String accountIdentifier, ModuleType module, long timestamp) {
    if (timestamp <= 0) {
      throw new InvalidArgumentsException(format("Invalid timestamp %d while fetching CI active developer", timestamp));
    }
    if (ModuleType.CI != module) {
      throw new InvalidArgumentsException(format("Invalid Module type %s provided, expected CI", module.toString()));
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account Identifier cannot be null or empty");
    }
  }

  private Path createAccountCSVReportDirIfNotExist(final String accountIdentifier) {
    Path activeServicesAccountCSVDir =
        Path.of(System.getProperty("java.io.tmpdir"), "developer-count-csv", accountIdentifier);
    try {
      FileIo.createDirectoryIfDoesNotExist(activeServicesAccountCSVDir);
      return activeServicesAccountCSVDir;
    } catch (IOException e) {
      throw new InvalidRequestException(
          format("Unable to create developer count CSV report directory, path: %s", activeServicesAccountCSVDir), e);
    }
  }

  private Path getAccountCSVReportFilePath(Path accountDir, final String accountIdentifier, long reportTSInMs) {
    String fileName = format("%s-%s-%s.csv", accountIdentifier, reportTSInMs, UUIDGenerator.generateUuid());
    return Paths.get(accountDir.toString(), fileName);
  }
}
