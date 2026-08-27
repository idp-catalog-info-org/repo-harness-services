/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.timescaledb.Tables.ENVIRONMENTS;
import static io.harness.timescaledb.tables.Services.SERVICES;

import static java.util.Objects.isNull;
import static org.jooq.impl.DSL.row;

import io.harness.aggregates.AggregateProjectInfo;
import io.harness.aggregates.AggregateProjectInfoOnParentUniqueId;
import io.harness.aggregates.AggregateServiceInfo;
import io.harness.aggregates.AggregateServiceInfoOnParentUniqueId;
import io.harness.aggregates.TimeWiseExecutionSummary;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.dashboards.DashboardHelper;
import io.harness.dashboards.DeploymentStatsSummary;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.PipelineExecutionDashboardInfo;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectDashBoardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServiceDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.dashboards.TimeBasedDeploymentInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.OrgIdentifierAndUniqueId;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.pms.dashboards.GroupBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummary;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.Services;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.jooq.Condition;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Row2;
import org.jooq.Table;
import org.jooq.impl.DSL;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CDLandingDashboardServiceImpl implements CDLandingDashboardService {
  public static final long DAY_IN_MS = 86400000; // 24*60*60*1000

  @Inject private TimeScaleDAL timeScaleDAL;
  @Inject NGFeatureFlagHelperService featureFlagService;

  @Override
  public ServicesDashboardInfo getActiveServices(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval,
      @NotNull SortBy sortBy) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }

    if (sortBy == SortBy.INSTANCES) {
      return getActiveServicesByInstances(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
    }
    return getActiveServicesByDeployments(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
  }

  public ServicesDashboardInfo getActiveServicesWithParentUniqueId(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval,
      @NotNull SortBy sortBy) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getActiveServices(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, sortBy);
    }
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }

    if (sortBy == SortBy.INSTANCES) {
      return getActiveServicesByInstancesWithParentUniqueId(
          accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
    }
    return getActiveServicesByDeploymentsWithParentUniqueId(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
  }

  ServicesDashboardInfo getActiveServicesByDeployments(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateServiceInfo> serviceInfraInfoList = timeScaleDAL.getTopServicesByDeploymentCount(accountIdentifier,
        startInterval, endInterval, orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }

    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo = ServiceDashboardInfo.builder()
                                                      .identifier(serviceInfraInfo.getServiceId())
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(serviceInfraInfo.getOrgidentifier())
                                                      .projectIdentifier(serviceInfraInfo.getProjectidentifier())
                                                      .totalDeploymentsCount(serviceInfraInfo.getCount())
                                                      .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(getCombinedId(serviceDashboardInfo.getOrgIdentifier(),
                                    serviceDashboardInfo.getProjectIdentifier(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }

    Table<Record3<String, String, String>> orgProjectServiceTable =
        NgServiceInfraInfoUtils.getOrgProjectServiceTable(serviceInfraInfoList);
    prepareServicesChangeRate(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, servicesDashboardInfoList);
    prepareStatusWiseCount(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, servicesDashboardInfoList);
    addServiceNames(combinedIdToRecordMap, accountIdentifier, orgProjectServiceTable);

    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }

  ServicesDashboardInfo getActiveServicesByDeploymentsWithParentUniqueId(@NotNull String accountIdentifier,
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());
    Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap = new HashMap<>();
    for (int i = 0; i < parentUniqueIds.size(); i++) {
      parentUniqueIdToOrgProjectIdentifierMap.put(parentUniqueIds.get(i), orgProjectIdentifiers.get(i));
    }

    List<AggregateServiceInfoOnParentUniqueId> serviceInfraInfoList =
        timeScaleDAL.getTopServicesByDeploymentCountWithParentUniqueId(accountIdentifier, startInterval, endInterval,
            parentUniqueIds, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }

    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfoOnParentUniqueId serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo =
          ServiceDashboardInfo.builder()
              .identifier(serviceInfraInfo.getServiceId())
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(serviceInfraInfo.getParentUniqueId()).getOrgIdentifier())
              .projectIdentifier(parentUniqueIdToOrgProjectIdentifierMap.get(serviceInfraInfo.getParentUniqueId())
                                     .getProjectIdentifier())
              .totalDeploymentsCount(serviceInfraInfo.getCount())
              .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(
          getCombinedId(serviceInfraInfo.getParentUniqueId(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }

    Table<Record2<String, String>> parentUniqueIdServiceTable =
        NgServiceInfraInfoUtils.getParentUniqueIdServiceIdTable(serviceInfraInfoList);
    prepareServicesChangeRateWithParentUniqueId(
        parentUniqueIdServiceTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);
    prepareStatusWiseCountWithParentUniqueId(
        parentUniqueIdServiceTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);
    addServiceNamesWithParentUniqueId(combinedIdToRecordMap, accountIdentifier, parentUniqueIdServiceTable);

    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }

  ServicesDashboardInfo getActiveServicesByInstances(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateServiceInfo> serviceInfraInfoList =
        timeScaleDAL.getTopServicesByInstanceCount(accountIdentifier, startInterval, endInterval, orgProjectTable);

    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }

    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo = ServiceDashboardInfo.builder()
                                                      .identifier(serviceInfraInfo.getServiceId())
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(serviceInfraInfo.getOrgidentifier())
                                                      .projectIdentifier(serviceInfraInfo.getProjectidentifier())
                                                      .instancesCount(serviceInfraInfo.getCount())
                                                      .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(getCombinedId(serviceDashboardInfo.getOrgIdentifier(),
                                    serviceDashboardInfo.getProjectIdentifier(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }

    Table<Record3<String, String, String>> orgProjectServiceTable =
        NgServiceInfraInfoUtils.getOrgProjectServiceTable(serviceInfraInfoList);
    prepareServiceInstancesChangeRate(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);
    addServiceNames(combinedIdToRecordMap, accountIdentifier, orgProjectServiceTable);

    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }

  ServicesDashboardInfo getActiveServicesByInstancesWithParentUniqueId(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ServicesDashboardInfo.builder().build();
    }
    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());
    Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap = new HashMap<>();
    for (int i = 0; i < parentUniqueIds.size(); i++) {
      parentUniqueIdToOrgProjectIdentifierMap.put(parentUniqueIds.get(i), orgProjectIdentifiers.get(i));
    }
    List<AggregateServiceInfoOnParentUniqueId> serviceInfraInfoList =
        timeScaleDAL.getTopServicesByInstanceCountWithParentUniqueId(
            accountIdentifier, startInterval, endInterval, parentUniqueIds);
    if (EmptyPredicate.isEmpty(serviceInfraInfoList)) {
      return ServicesDashboardInfo.builder().build();
    }
    List<ServiceDashboardInfo> servicesDashboardInfoList = new ArrayList<>();
    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();
    for (AggregateServiceInfoOnParentUniqueId serviceInfraInfo : serviceInfraInfoList) {
      ServiceDashboardInfo serviceDashboardInfo =
          ServiceDashboardInfo.builder()
              .identifier(serviceInfraInfo.getServiceId())
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(serviceInfraInfo.getParentUniqueId()).getOrgIdentifier())
              .projectIdentifier(parentUniqueIdToOrgProjectIdentifierMap.get(serviceInfraInfo.getParentUniqueId())
                                     .getProjectIdentifier())
              .instancesCount(serviceInfraInfo.getCount())
              .build();
      servicesDashboardInfoList.add(serviceDashboardInfo);
      combinedIdToRecordMap.put(
          getCombinedId(serviceInfraInfo.getParentUniqueId(), serviceDashboardInfo.getIdentifier()),
          serviceDashboardInfo);
    }
    Table<Record2<String, String>> parentUniqueIdServiceIdTable =
        NgServiceInfraInfoUtils.getParentUniqueIdServiceIdTable(serviceInfraInfoList);
    prepareServiceInstancesChangeRateWithParentUniqueId(
        parentUniqueIdServiceIdTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);
    addServiceNamesWithParentUniqueId(combinedIdToRecordMap, accountIdentifier, parentUniqueIdServiceIdTable);
    return ServicesDashboardInfo.builder().serviceDashboardInfoList(servicesDashboardInfoList).build();
  }
  void prepareServiceInstancesChangeRate(Table<Record3<String, String, String>> orgProjectServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      Map<String, ServiceDashboardInfo> combinedIdToRecordMap) {
    if (EmptyPredicate.isEmpty(combinedIdToRecordMap)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateServiceInfo> serviceInstanceList = timeScaleDAL.getInstanceCountForGivenServices(
        orgProjectServiceTable, accountIdentifier, startInterval, endInterval);

    if (EmptyPredicate.isEmpty(serviceInstanceList)) {
      return;
    }

    for (AggregateServiceInfo aggregateServiceInfo : serviceInstanceList) {
      String combinedId = getCombinedId(aggregateServiceInfo.getOrgidentifier(),
          aggregateServiceInfo.getProjectidentifier(), aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(combinedId);
      double changeRate = getChangeRate(aggregateServiceInfo.getCount(), serviceDashboardInfo.getInstancesCount());
      serviceDashboardInfo.setInstancesCountChangeRate(changeRate);
    }
  }

  void prepareServiceInstancesChangeRateWithParentUniqueId(Table<Record2<String, String>> parentUniqueIdServiceIdTable,
      String accountIdentifier, long startInterval, long endInterval,
      Map<String, ServiceDashboardInfo> combinedIdToRecordMap) {
    if (EmptyPredicate.isEmpty(combinedIdToRecordMap)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;
    List<AggregateServiceInfoOnParentUniqueId> serviceInstanceList =
        timeScaleDAL.getInstanceCountForGivenServicesWithParentUniqueId(
            parentUniqueIdServiceIdTable, accountIdentifier, startInterval, endInterval);
    if (EmptyPredicate.isEmpty(serviceInstanceList)) {
      return;
    }
    for (AggregateServiceInfoOnParentUniqueId aggregateServiceInfo : serviceInstanceList) {
      String combinedId = getCombinedId(aggregateServiceInfo.getParentUniqueId(), aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(combinedId);
      double changeRate = getChangeRate(aggregateServiceInfo.getCount(), serviceDashboardInfo.getInstancesCount());
      serviceDashboardInfo.setInstancesCountChangeRate(changeRate);
    }
  }

  void addServiceNames(Map<String, ServiceDashboardInfo> combinedIdToRecordMap, String accountIdentifier,
      Table<Record3<String, String, String>> orgProjectServiceTable) {
    List<Services> servicesList = timeScaleDAL.getNamesForServiceIds(accountIdentifier, orgProjectServiceTable);

    if (EmptyPredicate.isEmpty(servicesList)) {
      return;
    }

    for (Services service : servicesList) {
      String key = getCombinedId(service.getOrgIdentifier(), service.getProjectIdentifier(), service.getIdentifier());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);
      serviceDashboardInfo.setName(service.getName());
    }
  }

  void addServiceNamesWithParentUniqueId(Map<String, ServiceDashboardInfo> combinedIdToRecordMap,
      String accountIdentifier, Table<Record2<String, String>> parentUniqueIdServiceIdTable) {
    List<Services> servicesList =
        timeScaleDAL.getNamesForServiceIdsWithParentUniqueId(accountIdentifier, parentUniqueIdServiceIdTable);
    if (EmptyPredicate.isEmpty(servicesList)) {
      return;
    }
    for (Services service : servicesList) {
      String key = getCombinedId(service.getParentUniqueId(), service.getIdentifier());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);
      serviceDashboardInfo.setName(service.getName());
    }
  }

  void prepareStatusWiseCount(Table<Record3<String, String, String>> orgProjectServiceTable, String accountIdentifier,
      long startInterval, long endInterval, List<ServiceDashboardInfo> serviceDashboardInfoList) {
    if (EmptyPredicate.isEmpty(serviceDashboardInfoList)) {
      return;
    }

    List<AggregateServiceInfo> previousServiceInfraInfoList =
        timeScaleDAL.getStatusWiseDeploymentCountForGivenServices(orgProjectServiceTable, accountIdentifier,
            startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();

    for (ServiceDashboardInfo serviceDashboardInfo : serviceDashboardInfoList) {
      String key = getCombinedId(serviceDashboardInfo.getOrgIdentifier(), serviceDashboardInfo.getProjectIdentifier(),
          serviceDashboardInfo.getIdentifier());
      combinedIdToRecordMap.put(key, serviceDashboardInfo);
    }

    for (AggregateServiceInfo aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getOrgidentifier(), aggregateServiceInfo.getProjectidentifier(),
          aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);

      String status = aggregateServiceInfo.getServiceStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        serviceDashboardInfo.setSuccessDeploymentsCount(aggregateServiceInfo.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        serviceDashboardInfo.setFailureDeploymentsCount(
            aggregateServiceInfo.getCount() + serviceDashboardInfo.getFailureDeploymentsCount());
      }
    }
  }

  void prepareStatusWiseCountWithParentUniqueId(Table<Record2<String, String>> parentUniqueIdServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      Map<String, ServiceDashboardInfo> combinedIdToRecordMap) {
    if (EmptyPredicate.isEmpty(combinedIdToRecordMap)) {
      return;
    }

    List<AggregateServiceInfoOnParentUniqueId> previousServiceInfraInfoList =
        timeScaleDAL.getStatusWiseDeploymentCountForGivenServicesWithParentUniqueId(parentUniqueIdServiceTable,
            accountIdentifier, startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    for (AggregateServiceInfoOnParentUniqueId aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getParentUniqueId(), aggregateServiceInfo.getServiceId());
      ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);

      String status = aggregateServiceInfo.getServiceStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        serviceDashboardInfo.setSuccessDeploymentsCount(aggregateServiceInfo.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        serviceDashboardInfo.setFailureDeploymentsCount(
            aggregateServiceInfo.getCount() + serviceDashboardInfo.getFailureDeploymentsCount());
      }
    }
  }

  void prepareServicesChangeRate(Table<Record3<String, String, String>> orgProjectServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      List<ServiceDashboardInfo> serviceDashboardInfoList) {
    if (EmptyPredicate.isEmpty(serviceDashboardInfoList)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateServiceInfo> previousServiceInfraInfoList =
        timeScaleDAL.getDeploymentCountForGivenServices(orgProjectServiceTable, accountIdentifier, startInterval,
            endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    Map<String, AggregateServiceInfo> combinedIdToRecordMap = new HashMap<>();

    for (AggregateServiceInfo aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getOrgidentifier(), aggregateServiceInfo.getProjectidentifier(),
          aggregateServiceInfo.getServiceId());
      combinedIdToRecordMap.put(key, aggregateServiceInfo);
    }

    for (ServiceDashboardInfo serviceDashboardInfo : serviceDashboardInfoList) {
      String key = getCombinedId(serviceDashboardInfo.getOrgIdentifier(), serviceDashboardInfo.getProjectIdentifier(),
          serviceDashboardInfo.getIdentifier());
      if (combinedIdToRecordMap.containsKey(key)) {
        AggregateServiceInfo previousServiceInfo = combinedIdToRecordMap.get(key);
        serviceDashboardInfo.setTotalDeploymentsChangeRate(
            getChangeRate(previousServiceInfo.getCount(), serviceDashboardInfo.getTotalDeploymentsCount()));
      }
    }
  }

  void prepareServicesChangeRateWithParentUniqueId(Table<Record2<String, String>> parentUniqueIdServiceTable,
      String accountIdentifier, long startInterval, long endInterval,
      Map<String, ServiceDashboardInfo> combinedIdToRecordMap) {
    if (EmptyPredicate.isEmpty(combinedIdToRecordMap)) {
      return;
    }
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateServiceInfoOnParentUniqueId> previousServiceInfraInfoList =
        timeScaleDAL.getDeploymentCountForGivenServicesWithParentUniqueId(parentUniqueIdServiceTable, accountIdentifier,
            startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousServiceInfraInfoList)) {
      return;
    }

    Map<String, AggregateServiceInfoOnParentUniqueId> combinedIdToRecordMapForAggregateServiceInfo = new HashMap<>();

    for (AggregateServiceInfoOnParentUniqueId aggregateServiceInfo : previousServiceInfraInfoList) {
      String key = getCombinedId(aggregateServiceInfo.getParentUniqueId(), aggregateServiceInfo.getServiceId());
      combinedIdToRecordMapForAggregateServiceInfo.put(key, aggregateServiceInfo);
    }

    for (String key : combinedIdToRecordMapForAggregateServiceInfo.keySet()) {
      if (combinedIdToRecordMapForAggregateServiceInfo.containsKey(key)) {
        AggregateServiceInfoOnParentUniqueId aggregateServiceInfo =
            combinedIdToRecordMapForAggregateServiceInfo.get(key);
        ServiceDashboardInfo serviceDashboardInfo = combinedIdToRecordMap.get(key);
        serviceDashboardInfo.setTotalDeploymentsChangeRate(
            getChangeRate(aggregateServiceInfo.getCount(), serviceDashboardInfo.getTotalDeploymentsCount()));
      }
    }
  }

  @org.jetbrains.annotations.NotNull
  private String getCombinedId(String... keys) {
    StringBuilder combinedId = new StringBuilder();

    for (String key : keys) {
      combinedId.append(key).append('-');
    }
    combinedId.deleteCharAt(combinedId.length() - 1);

    return combinedId.toString();
  }

  @org.jetbrains.annotations.NotNull
  private Table<Record2<String, String>> getOrgProjectTable(@NotNull List<OrgProjectIdentifier> orgProjectIdentifiers) {
    Row2<String, String>[] orgProjectRows = new Row2[orgProjectIdentifiers.size()];
    int index = 0;
    for (OrgProjectIdentifier orgProjectIdentifier : orgProjectIdentifiers) {
      orgProjectRows[index++] =
          row(orgProjectIdentifier.getOrgIdentifier(), orgProjectIdentifier.getProjectIdentifier());
    }

    return DSL.values(orgProjectRows).as("t", "orgId", "projectId");
  }

  @org.jetbrains.annotations.NotNull
  private Table<Record2<String, String>> getEmptyAwareOrgProjectTable(
      @NotNull List<OrgProjectIdentifier> orgProjectIdentifiers) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      // Return typed, schema-correct but empty table
      return DSL.select(DSL.val((String) null).as("orgId"), DSL.val((String) null).as("projectId"))
          .where(DSL.falseCondition())
          .asTable("t");
    }
    return getOrgProjectTable(orgProjectIdentifiers);
  }

  @Override
  public ProjectsDashboardInfo getTopProjects(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ProjectsDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<AggregateProjectInfo> projectInfoList = timeScaleDAL.getTopProjectsByDeploymentCount(accountIdentifier,
        startInterval, endInterval, orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    List<ProjectDashBoardInfo> projectDashBoardInfoList = new ArrayList<>();

    if (EmptyPredicate.isEmpty(projectInfoList)) {
      return ProjectsDashboardInfo.builder().build();
    }

    for (AggregateProjectInfo projectInfo : projectInfoList) {
      projectDashBoardInfoList.add(ProjectDashBoardInfo.builder()
                                       .accountId(accountIdentifier)
                                       .orgIdentifier(projectInfo.getOrgidentifier())
                                       .projectIdentifier(projectInfo.getProjectidentifier())
                                       .deploymentsCount(projectInfo.getCount())
                                       .build());
    }

    Table<Record2<String, String>> topOrgProjectTable = prepareOrgProjectTable(projectInfoList);
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = getCombinedIdToRecordMap(projectDashBoardInfoList);

    prepareProjectsChangeRate(topOrgProjectTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);

    prepareProjectsStatusWiseCount(
        topOrgProjectTable, accountIdentifier, startInterval, endInterval, combinedIdToRecordMap);

    return ProjectsDashboardInfo.builder().projectDashBoardInfoList(projectDashBoardInfoList).build();
  }

  @Override
  public ProjectsDashboardInfo getTopProjectsWithParentIdQuerying(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getTopProjects(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval);
    }
    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());
    List<AggregateProjectInfoOnParentUniqueId> projectInfoOnParentUniqueIdList;
    if (EmptyPredicate.isEmpty(parentUniqueIds)) {
      return ProjectsDashboardInfo.builder().build();
    }
    projectInfoOnParentUniqueIdList = timeScaleDAL.getTopProjectsByDeploymentCountUsingParentUniqueId(accountIdentifier,
        startInterval, endInterval, parentUniqueIds, CDDashboardServiceHelper.getSuccessFailedStatusList());

    Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap = new HashMap<>();

    if (parentUniqueIds.size() != orgProjectIdentifiers.size()) {
      throw new InvalidRequestException("parentUniqueIds and orgProjectIdentifiers size should be same");
    }

    for (int i = 0; i < parentUniqueIds.size(); i++) {
      parentUniqueIdToOrgProjectIdentifierMap.put(parentUniqueIds.get(i), orgProjectIdentifiers.get(i));
    }

    List<ProjectDashBoardInfo> projectDashBoardInfoList = new ArrayList<>();

    if (EmptyPredicate.isEmpty(projectInfoOnParentUniqueIdList)) {
      return ProjectsDashboardInfo.builder().build();
    }
    Map<String, ProjectDashBoardInfo> parentUniqueIdToProjectDashBoardInfoMap = new HashMap<>();
    for (AggregateProjectInfoOnParentUniqueId projectInfoWithParentUniqueId : projectInfoOnParentUniqueIdList) {
      ProjectDashBoardInfo projectDashBoardInfo =
          ProjectDashBoardInfo.builder()
              .accountId(accountIdentifier)
              .orgIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(projectInfoWithParentUniqueId.getParentUniqueId())
                      .getOrgIdentifier())
              .projectIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(projectInfoWithParentUniqueId.getParentUniqueId())
                      .getProjectIdentifier())
              .deploymentsCount(projectInfoWithParentUniqueId.getCount())
              .build();
      projectDashBoardInfoList.add(projectDashBoardInfo);
      parentUniqueIdToProjectDashBoardInfoMap.put(
          projectInfoWithParentUniqueId.getParentUniqueId(), projectDashBoardInfo);
    }
    prepareProjectsChangeRateWithParentIdQuerying(projectInfoOnParentUniqueIdList.stream()
                                                      .map(AggregateProjectInfoOnParentUniqueId::getParentUniqueId)
                                                      .collect(Collectors.toList()),
        accountIdentifier, startInterval, endInterval, parentUniqueIdToProjectDashBoardInfoMap);
    prepareProjectsStatusWiseCountWithParentIdQuerying(projectInfoOnParentUniqueIdList.stream()
                                                           .map(AggregateProjectInfoOnParentUniqueId::getParentUniqueId)
                                                           .collect(Collectors.toList()),
        accountIdentifier, startInterval, endInterval, parentUniqueIdToProjectDashBoardInfoMap);

    return ProjectsDashboardInfo.builder().projectDashBoardInfoList(projectDashBoardInfoList).build();
  }

  @Override
  public ServicesCount getServicesCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      List<String> orgIdentifierList, Scope requestScope, long startInterval, long endInterval) {
    Condition permittedScopeCondition =
        timeScaleDAL.buildPermittedScopeCondition(requestScope, getEmptyAwareOrgProjectTable(orgProjectIdentifiers),
            orgIdentifierList, SERVICES.ORG_IDENTIFIER, SERVICES.PROJECT_IDENTIFIER);

    Integer totalServicesCount = timeScaleDAL.getTotalServicesCount(accountIdentifier, permittedScopeCondition);
    int trendCount =
        timeScaleDAL.getNewServicesCount(accountIdentifier, startInterval, endInterval, permittedScopeCondition)
        - timeScaleDAL.getDeletedServiceCount(accountIdentifier, startInterval, endInterval, permittedScopeCondition);

    return ServicesCount.builder().totalCount(totalServicesCount).newCount(trendCount).build();
  }

  @Override
  public ServicesCount getServicesCountWithParentUniqueIdQuerying(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, List<String> orgIdentifierList,
      List<OrgIdentifierAndUniqueId> orgIdentifierAndUniqueIds, Scope requestScope, long startInterval,
      long endInterval) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getServicesCount(
          accountIdentifier, orgProjectIdentifiers, orgIdentifierList, requestScope, startInterval, endInterval);
    }

    List<String> parentUniqueIdsToQuery = getParentUniqueIdsToQuery(requestScope, accountIdentifier,
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList()),
        orgIdentifierAndUniqueIds.stream().map(OrgIdentifierAndUniqueId::getUniqueId).collect(Collectors.toList()));

    Integer totalServicesCount =
        timeScaleDAL.getTotalServicesCountWithParentUniqueId(accountIdentifier, parentUniqueIdsToQuery);
    int trendCount = timeScaleDAL.getNewServicesCountWithParentUniqueId(
                         accountIdentifier, startInterval, endInterval, parentUniqueIdsToQuery)
        - timeScaleDAL.getDeletedServiceCountWithParentUniqueId(
            accountIdentifier, startInterval, endInterval, parentUniqueIdsToQuery);

    return ServicesCount.builder().totalCount(totalServicesCount).newCount(trendCount).build();
  }

  @Override
  public EnvCount getEnvCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      List<String> orgIdentifierList, Scope requestScope, long startInterval, long endInterval) {
    Condition permittedScopeCondition =
        timeScaleDAL.buildPermittedScopeCondition(requestScope, getEmptyAwareOrgProjectTable(orgProjectIdentifiers),
            orgIdentifierList, ENVIRONMENTS.ORG_IDENTIFIER, ENVIRONMENTS.PROJECT_IDENTIFIER);

    Integer totalCount = timeScaleDAL.getTotalEnvCount(accountIdentifier, permittedScopeCondition);
    int trendCount = timeScaleDAL.getNewEnvCount(accountIdentifier, startInterval, endInterval, permittedScopeCondition)
        - timeScaleDAL.getDeletedEnvCount(accountIdentifier, startInterval, endInterval, permittedScopeCondition);

    return EnvCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  @Override
  public EnvCount getEnvCountWithParentUniqueIdQuerying(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, List<String> orgIdentifierList,
      List<OrgIdentifierAndUniqueId> orgIdentifierAndUniqueIds, Scope requestScope, long startInterval,
      long endInterval) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getEnvCount(
          accountIdentifier, orgProjectIdentifiers, orgIdentifierList, requestScope, startInterval, endInterval);
    }

    List<String> parentUniqueIdsToQuery = getParentUniqueIdsToQuery(requestScope, accountIdentifier,
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList()),
        orgIdentifierAndUniqueIds.stream().map(OrgIdentifierAndUniqueId::getUniqueId).collect(Collectors.toList()));

    Integer totalCount = timeScaleDAL.getTotalEnvCountWithParentUniqueId(accountIdentifier, parentUniqueIdsToQuery);
    int trendCount = timeScaleDAL.getNewEnvCountWithParentUniqueId(
                         accountIdentifier, startInterval, endInterval, parentUniqueIdsToQuery)
        - timeScaleDAL.getDeletedEnvCountWithParentUniqueId(
            accountIdentifier, startInterval, endInterval, parentUniqueIdsToQuery);

    return EnvCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  @Override
  public PipelinesExecutionDashboardInfo getActiveDeploymentStats(
      String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<String> requiredStatuses = new ArrayList<>(Arrays.asList(ExecutionStatus.APPROVAL_WAITING.name(),
        ExecutionStatus.APPROVALWAITING.name(), ExecutionStatus.WAITSTEPRUNNING.name(),
        ExecutionStatus.INTERVENTION_WAITING.name(), ExecutionStatus.INTERVENTIONWAITING.name()));
    requiredStatuses.addAll(CDOverviewDashboardServiceImpl.activeStatusList);

    PipelinesExecutionDashboardInfo pipelinesExecutionDashboardInfo;
    if (featureFlagService.isEnabled(
            accountIdentifier, FeatureName.PIPE_SHOW_ALL_EXECUTIONS_ON_ACCOUNT_OVERVIEW_PAGE)) {
      List<PipelineExecutionSummary> executionsList = timeScaleDAL.getPipelineExecutionsForGivenExecutionStatus(
          accountIdentifier, orgProjectTable, requiredStatuses);
      pipelinesExecutionDashboardInfo = filterByStatuses(accountIdentifier, executionsList);
    } else {
      List<PipelineExecutionSummaryCd> executionsList = timeScaleDAL.getPipelineExecutionsForGivenExecutionStatusCD(
          accountIdentifier, orgProjectTable, requiredStatuses);
      pipelinesExecutionDashboardInfo = filterByStatusesCD(accountIdentifier, executionsList);
    }

    pipelinesExecutionDashboardInfo.setFailed24HrsExecutions(
        getLast24HrsFailedExecutions(accountIdentifier, orgProjectTable));
    return pipelinesExecutionDashboardInfo;
  }

  @Override
  public PipelinesExecutionDashboardInfo getActiveDeploymentStatsWithParentUniqueIdQuerying(
      String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getActiveDeploymentStats(accountIdentifier, orgProjectIdentifiers);
    }
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }

    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());
    Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap = new HashMap<>();

    for (int i = 0; i < parentUniqueIds.size(); i++) {
      parentUniqueIdToOrgProjectIdentifierMap.put(parentUniqueIds.get(i), orgProjectIdentifiers.get(i));
    }

    List<String> requiredStatuses = new ArrayList<>(Arrays.asList(ExecutionStatus.APPROVAL_WAITING.name(),
        ExecutionStatus.APPROVALWAITING.name(), ExecutionStatus.WAITSTEPRUNNING.name(),
        ExecutionStatus.INTERVENTION_WAITING.name(), ExecutionStatus.INTERVENTIONWAITING.name()));
    requiredStatuses.addAll(CDOverviewDashboardServiceImpl.activeStatusList);

    PipelinesExecutionDashboardInfo pipelinesExecutionDashboardInfo;
    if (featureFlagService.isEnabled(
            accountIdentifier, FeatureName.PIPE_SHOW_ALL_EXECUTIONS_ON_ACCOUNT_OVERVIEW_PAGE)) {
      List<PipelineExecutionSummary> executionsList =
          timeScaleDAL.getPipelineExecutionsForGivenExecutionStatusUsingParentUniqueId(
              accountIdentifier, parentUniqueIds, requiredStatuses);
      pipelinesExecutionDashboardInfo =
          filterByStatuses(accountIdentifier, executionsList, parentUniqueIdToOrgProjectIdentifierMap);
    } else {
      List<PipelineExecutionSummaryCd> executionsList =
          timeScaleDAL.getPipelineExecutionsForGivenExecutionStatusCDWithParentUniqueId(
              accountIdentifier, parentUniqueIds, requiredStatuses);
      pipelinesExecutionDashboardInfo =
          filterByStatusesCD(accountIdentifier, executionsList, parentUniqueIdToOrgProjectIdentifierMap);
    }

    pipelinesExecutionDashboardInfo.setFailed24HrsExecutions(getLast24HrsFailedExecutionsWithParentUniqueId(
        accountIdentifier, parentUniqueIds, parentUniqueIdToOrgProjectIdentifierMap));
    return pipelinesExecutionDashboardInfo;
  }

  @Override
  public DeploymentStatsSummary getDeploymentStatsSummary(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return DeploymentStatsSummary.builder().build();
    }
    DeploymentStatsSummary currentDeploymentStatsSummary = getDeploymentStatsSummaryWithoutChangeRate(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    DeploymentStatsSummary previousDeploymentStatsSummary = getDeploymentStatsSummaryWithoutChangeRate(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    double totalCountChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getTotalCount(), currentDeploymentStatsSummary.getTotalCount());
    double failureRateChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getFailureRate(), currentDeploymentStatsSummary.getFailureRate());
    double deploymentRateChangeRate = getChangeRate(
        previousDeploymentStatsSummary.getDeploymentRate(), currentDeploymentStatsSummary.getDeploymentRate());

    currentDeploymentStatsSummary.setTotalCountChangeRate(totalCountChangeRate);
    currentDeploymentStatsSummary.setFailureRateChangeRate(failureRateChangeRate);
    currentDeploymentStatsSummary.setDeploymentRateChangeRate(deploymentRateChangeRate);

    return currentDeploymentStatsSummary;
  }

  @Override
  public DeploymentStatsSummary getDeploymentStatsSummaryWithParentUniqueIdQuerying(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    if (!featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_MOVE_PROJECTS_OVERVIEW_DASHBOARDS)) {
      return getDeploymentStatsSummary(accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);
    }

    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return DeploymentStatsSummary.builder().build();
    }
    DeploymentStatsSummary currentDeploymentStatsSummary = getDeploymentStatsSummaryWithoutChangeRateWithParentUniqueId(
        accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    DeploymentStatsSummary previousDeploymentStatsSummary =
        getDeploymentStatsSummaryWithoutChangeRateWithParentUniqueId(
            accountIdentifier, orgProjectIdentifiers, startInterval, endInterval, groupBy);

    double totalCountChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getTotalCount(), currentDeploymentStatsSummary.getTotalCount());
    double failureRateChangeRate =
        getChangeRate(previousDeploymentStatsSummary.getFailureRate(), currentDeploymentStatsSummary.getFailureRate());
    double deploymentRateChangeRate = getChangeRate(
        previousDeploymentStatsSummary.getDeploymentRate(), currentDeploymentStatsSummary.getDeploymentRate());

    currentDeploymentStatsSummary.setTotalCountChangeRate(totalCountChangeRate);
    currentDeploymentStatsSummary.setFailureRateChangeRate(failureRateChangeRate);
    currentDeploymentStatsSummary.setDeploymentRateChangeRate(deploymentRateChangeRate);

    return currentDeploymentStatsSummary;
  }

  private DeploymentStatsSummary getDeploymentStatsSummaryWithoutChangeRate(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    List<TimeWiseExecutionSummary> timeWiseDeploymentStatsList =
        timeScaleDAL.getTimeExecutionStatusWiseDeploymentCount(accountIdentifier, startInterval, endInterval, groupBy,
            orgProjectTable, CDDashboardServiceHelper.getSuccessFailedStatusList());

    List<TimeBasedDeploymentInfo> timeWiseDeploymentInfoList = new ArrayList<>();
    long totalDeploymentsCount = 0;
    long failedDeploymentsCount = 0;

    long prevEpoch = 0;
    TimeBasedDeploymentInfo prevTimeDeploymentInfo = null;
    for (TimeWiseExecutionSummary deploymentStats : timeWiseDeploymentStatsList) {
      long count = deploymentStats.getCount();
      String status = deploymentStats.getStatus();

      if (deploymentStats.getEpoch() != prevEpoch || prevTimeDeploymentInfo == null) {
        TimeBasedDeploymentInfo timeBasedDeploymentInfo =
            TimeBasedDeploymentInfo.builder().epochTime(deploymentStats.getEpoch()).build();

        prevEpoch = deploymentStats.getEpoch();
        prevTimeDeploymentInfo = timeBasedDeploymentInfo;
        timeWiseDeploymentInfoList.add(timeBasedDeploymentInfo);
      }
      prevTimeDeploymentInfo.setTotalCount(prevTimeDeploymentInfo.getTotalCount() + count);
      totalDeploymentsCount += count;
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        prevTimeDeploymentInfo.setSuccessCount(prevTimeDeploymentInfo.getSuccessCount() + count);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        prevTimeDeploymentInfo.setFailedCount(prevTimeDeploymentInfo.getFailedCount() + count);
        failedDeploymentsCount += count;
      }
    }

    timeWiseDeploymentInfoList.forEach(timeBasedDeploymentInfo
        -> timeBasedDeploymentInfo.setFailureRate(
            getRate(timeBasedDeploymentInfo.getFailedCount(), timeBasedDeploymentInfo.getTotalCount())));

    double failureRate = getRate(failedDeploymentsCount, totalDeploymentsCount);
    double noOfBuckets = Math.ceil(((endInterval - startInterval) * 1.0) / groupBy.getNoOfMilliseconds());
    double deploymentRate = DashboardHelper.truncate(totalDeploymentsCount / noOfBuckets);

    return DeploymentStatsSummary.builder()
        .totalCount(totalDeploymentsCount)
        .failureRate(failureRate)
        .deploymentRate(deploymentRate)
        .timeBasedDeploymentInfoList(timeWiseDeploymentInfoList)
        .build();
  }

  private DeploymentStatsSummary getDeploymentStatsSummaryWithoutChangeRateWithParentUniqueId(String accountIdentifier,
      List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval, GroupBy groupBy) {
    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());

    List<TimeWiseExecutionSummary> timeWiseDeploymentStatsList =
        timeScaleDAL.getTimeExecutionStatusWiseDeploymentCountWithParentUniqueId(accountIdentifier, startInterval,
            endInterval, groupBy, parentUniqueIds, CDDashboardServiceHelper.getSuccessFailedStatusList());

    List<TimeBasedDeploymentInfo> timeWiseDeploymentInfoList = new ArrayList<>();
    long totalDeploymentsCount = 0;
    long failedDeploymentsCount = 0;

    long prevEpoch = 0;
    TimeBasedDeploymentInfo prevTimeDeploymentInfo = null;
    for (TimeWiseExecutionSummary deploymentStats : timeWiseDeploymentStatsList) {
      long count = deploymentStats.getCount();
      String status = deploymentStats.getStatus();

      if (deploymentStats.getEpoch() != prevEpoch || prevTimeDeploymentInfo == null) {
        TimeBasedDeploymentInfo timeBasedDeploymentInfo =
            TimeBasedDeploymentInfo.builder().epochTime(deploymentStats.getEpoch()).build();

        prevEpoch = deploymentStats.getEpoch();
        prevTimeDeploymentInfo = timeBasedDeploymentInfo;
        timeWiseDeploymentInfoList.add(timeBasedDeploymentInfo);
      }
      prevTimeDeploymentInfo.setTotalCount(prevTimeDeploymentInfo.getTotalCount() + count);
      totalDeploymentsCount += count;
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        prevTimeDeploymentInfo.setSuccessCount(prevTimeDeploymentInfo.getSuccessCount() + count);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        prevTimeDeploymentInfo.setFailedCount(prevTimeDeploymentInfo.getFailedCount() + count);
        failedDeploymentsCount += count;
      }
    }

    timeWiseDeploymentInfoList.forEach(timeBasedDeploymentInfo
        -> timeBasedDeploymentInfo.setFailureRate(
            getRate(timeBasedDeploymentInfo.getFailedCount(), timeBasedDeploymentInfo.getTotalCount())));

    double failureRate = getRate(failedDeploymentsCount, totalDeploymentsCount);
    double noOfBuckets = Math.ceil(((endInterval - startInterval) * 1.0) / groupBy.getNoOfMilliseconds());
    double deploymentRate = DashboardHelper.truncate(totalDeploymentsCount / noOfBuckets);

    return DeploymentStatsSummary.builder()
        .totalCount(totalDeploymentsCount)
        .failureRate(failureRate)
        .deploymentRate(deploymentRate)
        .timeBasedDeploymentInfoList(timeWiseDeploymentInfoList)
        .build();
  }

  private double getRate(double count, double totalCount) {
    if (count == 0) {
      return 0;
    }
    return totalCount == 0 ? DashboardHelper.MAX_VALUE : DashboardHelper.truncate(count * 100 / totalCount);
  }

  PipelinesExecutionDashboardInfo filterByStatusesCD(
      String accountIdentifier, List<PipelineExecutionSummaryCd> executionsList) {
    if (EmptyPredicate.isEmpty(executionsList)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    List<PipelineExecutionDashboardInfo> runningExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions = new ArrayList<>();

    for (PipelineExecutionSummaryCd execution : executionsList) {
      String status = execution.getStatus();

      PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo =
          PipelineExecutionDashboardInfo.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(execution.getOrgidentifier())
              .projectIdentifier(execution.getProjectidentifier())
              .identifier(execution.getPipelineidentifier())
              .name(execution.getName())
              .planExecutionId(execution.getPlanexecutionid())
              .startTs(execution.getStartts())
              .build();

      if (ExecutionStatus.APPROVAL_WAITING.name().equals(status)
          || ExecutionStatus.APPROVALWAITING.name().equals(status)) {
        pendingApprovalExecutions.add(pipelineExecutionDashboardInfo);
      } else if (ExecutionStatus.INTERVENTION_WAITING.name().equals(status)
          || ExecutionStatus.INTERVENTIONWAITING.name().equals(status)) {
        pendingManualInterventionExecutions.add(pipelineExecutionDashboardInfo);
      } else if (CDOverviewDashboardServiceImpl.activeStatusList.contains(status)) {
        runningExecutions.add(pipelineExecutionDashboardInfo);
      }
    }

    return PipelinesExecutionDashboardInfo.builder()
        .pendingApprovalExecutions(pendingApprovalExecutions)
        .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
        .runningExecutions(runningExecutions)
        .build();
  }

  PipelinesExecutionDashboardInfo filterByStatusesCD(String accountIdentifier,
      List<PipelineExecutionSummaryCd> executionsList,
      Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap) {
    if (EmptyPredicate.isEmpty(executionsList)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    List<PipelineExecutionDashboardInfo> runningExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions = new ArrayList<>();

    for (PipelineExecutionSummaryCd execution : executionsList) {
      String status = execution.getStatus();

      PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo =
          PipelineExecutionDashboardInfo.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId()).getOrgIdentifier())
              .projectIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId()).getProjectIdentifier())
              .identifier(execution.getPipelineidentifier())
              .name(execution.getName())
              .planExecutionId(execution.getPlanexecutionid())
              .startTs(execution.getStartts())
              .build();

      if (ExecutionStatus.APPROVAL_WAITING.name().equals(status)
          || ExecutionStatus.APPROVALWAITING.name().equals(status)) {
        pendingApprovalExecutions.add(pipelineExecutionDashboardInfo);
      } else if (ExecutionStatus.INTERVENTION_WAITING.name().equals(status)
          || ExecutionStatus.INTERVENTIONWAITING.name().equals(status)) {
        pendingManualInterventionExecutions.add(pipelineExecutionDashboardInfo);
      } else if (CDOverviewDashboardServiceImpl.activeStatusList.contains(status)) {
        runningExecutions.add(pipelineExecutionDashboardInfo);
      }
    }

    return PipelinesExecutionDashboardInfo.builder()
        .pendingApprovalExecutions(pendingApprovalExecutions)
        .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
        .runningExecutions(runningExecutions)
        .build();
  }

  PipelinesExecutionDashboardInfo filterByStatuses(
      String accountIdentifier, List<PipelineExecutionSummary> executionsList) {
    if (EmptyPredicate.isEmpty(executionsList)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    List<PipelineExecutionDashboardInfo> runningExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions = new ArrayList<>();

    for (PipelineExecutionSummary execution : executionsList) {
      String status = execution.getStatus();

      PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo =
          PipelineExecutionDashboardInfo.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(execution.getOrgidentifier())
              .projectIdentifier(execution.getProjectidentifier())
              .identifier(execution.getPipelineidentifier())
              .name(execution.getName())
              .planExecutionId(execution.getPlanexecutionid())
              .startTs(execution.getStartts())
              .build();

      if (ExecutionStatus.APPROVAL_WAITING.name().equals(status)
          || ExecutionStatus.APPROVALWAITING.name().equals(status)) {
        pendingApprovalExecutions.add(pipelineExecutionDashboardInfo);
      } else if (ExecutionStatus.INTERVENTION_WAITING.name().equals(status)
          || ExecutionStatus.INTERVENTIONWAITING.name().equals(status)) {
        pendingManualInterventionExecutions.add(pipelineExecutionDashboardInfo);
      } else if (CDOverviewDashboardServiceImpl.activeStatusList.contains(status)) {
        runningExecutions.add(pipelineExecutionDashboardInfo);
      }
    }

    return PipelinesExecutionDashboardInfo.builder()
        .pendingApprovalExecutions(pendingApprovalExecutions)
        .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
        .runningExecutions(runningExecutions)
        .build();
  }

  PipelinesExecutionDashboardInfo filterByStatuses(String accountIdentifier,
      List<PipelineExecutionSummary> executionsList,
      Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap) {
    if (EmptyPredicate.isEmpty(executionsList)) {
      return PipelinesExecutionDashboardInfo.builder().build();
    }
    List<PipelineExecutionDashboardInfo> runningExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions = new ArrayList<>();
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions = new ArrayList<>();

    for (PipelineExecutionSummary execution : executionsList) {
      String status = execution.getStatus();

      PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo =
          PipelineExecutionDashboardInfo.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId()).getOrgIdentifier())
              .projectIdentifier(
                  parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId()).getProjectIdentifier())
              .identifier(execution.getPipelineidentifier())
              .name(execution.getName())
              .planExecutionId(execution.getPlanexecutionid())
              .startTs(execution.getStartts())
              .build();

      if (ExecutionStatus.APPROVAL_WAITING.name().equals(status)
          || ExecutionStatus.APPROVALWAITING.name().equals(status)) {
        pendingApprovalExecutions.add(pipelineExecutionDashboardInfo);
      } else if (ExecutionStatus.INTERVENTION_WAITING.name().equals(status)
          || ExecutionStatus.INTERVENTIONWAITING.name().equals(status)) {
        pendingManualInterventionExecutions.add(pipelineExecutionDashboardInfo);
      } else if (CDOverviewDashboardServiceImpl.activeStatusList.contains(status)) {
        runningExecutions.add(pipelineExecutionDashboardInfo);
      }
    }

    return PipelinesExecutionDashboardInfo.builder()
        .pendingApprovalExecutions(pendingApprovalExecutions)
        .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
        .runningExecutions(runningExecutions)
        .build();
  }

  List<PipelineExecutionDashboardInfo> getLast24HrsFailedExecutions(
      String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    long endTime = System.currentTimeMillis();
    long startTime = System.currentTimeMillis() - DAY_IN_MS;

    List<PipelineExecutionSummaryCd> executionsList =
        timeScaleDAL.getFailedExecutionsForGivenTimeRange(accountIdentifier, orgProjectTable, endTime, startTime);

    return executionsList.stream()
        .map(execution
            -> PipelineExecutionDashboardInfo.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(execution.getOrgidentifier())
                   .projectIdentifier(execution.getProjectidentifier())
                   .identifier(execution.getPipelineidentifier())
                   .name(execution.getName())
                   .planExecutionId(execution.getPlanexecutionid())
                   .startTs(execution.getStartts())
                   .build())
        .collect(Collectors.toList());
  }

  List<PipelineExecutionDashboardInfo> getLast24HrsFailedExecutionsWithParentUniqueId(String accountIdentifier,
      List<String> parentUniqueIds, Map<String, OrgProjectIdentifier> parentUniqueIdToOrgProjectIdentifierMap) {
    long endTime = System.currentTimeMillis();
    long startTime = System.currentTimeMillis() - DAY_IN_MS;

    List<PipelineExecutionSummaryCd> executionsList =
        timeScaleDAL.getFailedExecutionsForGivenTimeRangeWithParentUniqueId(
            accountIdentifier, parentUniqueIds, endTime, startTime);

    return executionsList.stream()
        .map(execution
            -> PipelineExecutionDashboardInfo.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(
                       parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId()).getOrgIdentifier())
                   .projectIdentifier(parentUniqueIdToOrgProjectIdentifierMap.get(execution.getParentUniqueId())
                                          .getProjectIdentifier())
                   .identifier(execution.getPipelineidentifier())
                   .name(execution.getName())
                   .planExecutionId(execution.getPlanexecutionid())
                   .startTs(execution.getStartts())
                   .build())
        .collect(Collectors.toList());
  }

  private Map<String, ProjectDashBoardInfo> getCombinedIdToRecordMap(List<ProjectDashBoardInfo> projectInfoList) {
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = new HashMap<>();

    for (ProjectDashBoardInfo projectDashBoardInfo : projectInfoList) {
      String key = getCombinedId(projectDashBoardInfo.getOrgIdentifier(), projectDashBoardInfo.getProjectIdentifier());
      combinedIdToRecordMap.put(key, projectDashBoardInfo);
    }

    return combinedIdToRecordMap;
  }

  void prepareProjectsStatusWiseCount(Table<Record2<String, String>> orgProjectTable, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    List<AggregateProjectInfo> projectInfoList = timeScaleDAL.getProjectWiseStatusWiseDeploymentCount(orgProjectTable,
        accountIdentifier, startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(projectInfoList)) {
      return;
    }

    for (AggregateProjectInfo aggregateProjectInfo : projectInfoList) {
      String key = getCombinedId(aggregateProjectInfo.getOrgidentifier(), aggregateProjectInfo.getProjectidentifier());
      ProjectDashBoardInfo projectDashBoardInfo = combinedIdToRecordMap.get(key);

      String status = aggregateProjectInfo.getStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        projectDashBoardInfo.setSuccessDeploymentsCount(aggregateProjectInfo.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        projectDashBoardInfo.setFailedDeploymentsCount(
            aggregateProjectInfo.getCount() + projectDashBoardInfo.getFailedDeploymentsCount());
      }
    }
  }

  void prepareProjectsStatusWiseCountWithParentIdQuerying(List<String> parentUniqueIds, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    List<AggregateProjectInfoOnParentUniqueId> projectInfoList =
        timeScaleDAL.getProjectWiseStatusWiseDeploymentCountFromParentUniqueId(parentUniqueIds, accountIdentifier,
            startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(projectInfoList)) {
      return;
    }

    for (AggregateProjectInfoOnParentUniqueId aggregateProjectInfoOnParentUniqueId : projectInfoList) {
      ProjectDashBoardInfo projectDashBoardInfo =
          combinedIdToRecordMap.get(aggregateProjectInfoOnParentUniqueId.getParentUniqueId());

      String status = aggregateProjectInfoOnParentUniqueId.getStatus();
      if (CDDashboardServiceHelper.successStatusList.contains(status)) {
        projectDashBoardInfo.setSuccessDeploymentsCount(aggregateProjectInfoOnParentUniqueId.getCount());
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
        projectDashBoardInfo.setFailedDeploymentsCount(
            aggregateProjectInfoOnParentUniqueId.getCount() + projectDashBoardInfo.getFailedDeploymentsCount());
      }
    }
  }

  void prepareProjectsChangeRate(Table<Record2<String, String>> orgProjectTable, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateProjectInfo> previousProjectInfoList = timeScaleDAL.getProjectWiseDeploymentCount(orgProjectTable,
        accountIdentifier, startInterval, endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousProjectInfoList)) {
      return;
    }

    for (AggregateProjectInfo previousProjectInfo : previousProjectInfoList) {
      String key = getCombinedId(previousProjectInfo.getOrgidentifier(), previousProjectInfo.getProjectidentifier());
      ProjectDashBoardInfo projectDashBoardInfo = combinedIdToRecordMap.get(key);
      projectDashBoardInfo.setDeploymentsCountChangeRate(
          getChangeRate(previousProjectInfo.getCount(), projectDashBoardInfo.getDeploymentsCount()));
    }
  }

  void prepareProjectsChangeRateWithParentIdQuerying(List<String> parentUniqueIds, String accountIdentifier,
      long startInterval, long endInterval, Map<String, ProjectDashBoardInfo> combinedIdToRecordMap) {
    long duration = endInterval - startInterval;
    startInterval -= duration;
    endInterval -= duration;

    List<AggregateProjectInfoOnParentUniqueId> previousProjectInfoList =
        timeScaleDAL.getProjectWiseDeploymentCountFromParentUniqueId(parentUniqueIds, accountIdentifier, startInterval,
            endInterval, CDDashboardServiceHelper.getSuccessFailedStatusList());

    if (EmptyPredicate.isEmpty(previousProjectInfoList)) {
      return;
    }

    for (AggregateProjectInfoOnParentUniqueId previousProjectInfoOnParentUniqueId : previousProjectInfoList) {
      ProjectDashBoardInfo projectDashBoardInfo =
          combinedIdToRecordMap.get(previousProjectInfoOnParentUniqueId.getParentUniqueId());
      projectDashBoardInfo.setDeploymentsCountChangeRate(
          getChangeRate(previousProjectInfoOnParentUniqueId.getCount(), projectDashBoardInfo.getDeploymentsCount()));
    }
  }

  private double getChangeRate(double previousValue, double newValue) {
    double change = newValue - previousValue;
    if (change == 0) {
      return 0;
    }
    double rate = previousValue != 0 ? (change * 100.0) / previousValue : Double.MAX_VALUE;
    return DashboardHelper.truncate(rate);
  }

  private Table<Record2<String, String>> prepareOrgProjectTable(List<AggregateProjectInfo> projectInfoList) {
    List<OrgProjectIdentifier> orgProjectIdentifiers =
        projectInfoList.stream()
            .map(aggregateProjectInfo
                -> OrgProjectIdentifier.builder()
                       .orgIdentifier(aggregateProjectInfo.getOrgidentifier())
                       .projectIdentifier(aggregateProjectInfo.getProjectidentifier())
                       .build())
            .collect(Collectors.toList());

    return getOrgProjectTable(orgProjectIdentifiers);
  }

  private List<String> getParentUniqueIdsToQuery(Scope requestScope, String accountIdentifier,
      List<String> parentUniqueIdsOfAccessibleProjects, List<String> parentUniqueIdsOfAccessibleOrganizations) {
    if (isNull(requestScope) || requestScope == Scope.PROJECT) {
      // if request scope isn't present, only have project entities by default for backward compatibility
      return parentUniqueIdsOfAccessibleProjects;
    } else if (requestScope == Scope.ACCOUNT) {
      List<String> parentUniqueIdsToQuery = new ArrayList<>();
      // Project Scope with permitted projects
      parentUniqueIdsToQuery.addAll(parentUniqueIdsOfAccessibleProjects);
      // Org Scope with permitted orgs
      parentUniqueIdsToQuery.addAll(parentUniqueIdsOfAccessibleOrganizations);
      // Account Scope with all org level entities
      parentUniqueIdsToQuery.add(accountIdentifier);

      return parentUniqueIdsToQuery;
    } else {
      throw new InvalidRequestException(String.format("Scope not supported: %s", requestScope));
    }
  }
}
