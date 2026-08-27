/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.NGDateUtils.HOUR_IN_MS;
import static io.harness.NGDateUtils.getNumberOfDays;
import static io.harness.NGDateUtils.getStartTimeOfPreviousInterval;
import static io.harness.NGDateUtils.getStartTimeOfTheDayAsEpoch;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.event.timeseries.processor.utils.DateUtils.getCurrentTime;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.DAY;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.HOUR;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.timescaledb.Tables.NG_INSTANCE_STATS_DAY;
import static io.harness.timescaledb.Tables.PIPELINE_EXECUTION_SUMMARY_CD;
import static io.harness.timescaledb.Tables.SERVICE_INFRA_INFO;
import static io.harness.timescaledb.tables.StageExecution.STAGE_EXECUTION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.util.Objects.isNull;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.trueCondition;
import static org.jooq.impl.DSL.val;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGDateUtils;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.NGPipelineSummaryCDConstants;
import io.harness.cd.NGServiceConstants;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.cdng.service.beans.CustomSequenceDTO;
import io.harness.cdng.service.beans.ServiceDefinitionCategory;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.constants.TimescaleConstants;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndEnvId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.DashboardExecutionStatusInfo;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.dashboard.EnvironmentDeploymentsInfo;
import io.harness.ng.core.dashboard.ExecutionStatusInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.InfrastructureInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;
import io.harness.ng.core.dashboard.ServiceDeployments;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.beans.EnvironmentTypeCount;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.helpers.ServiceFilterHelper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.ServiceSequenceService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummary;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV2;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV3;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInfoDTO;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.BasicDeploymentMetric;
import io.harness.ng.overview.dto.BasicServiceDeploymentMetrics;
import io.harness.ng.overview.dto.BuildIdAndInstanceCount;
import io.harness.ng.overview.dto.ChangeRate;
import io.harness.ng.overview.dto.ChartInfoDTO;
import io.harness.ng.overview.dto.ChartVersionInstanceDetails;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.DashboardWorkloadDeploymentV2;
import io.harness.ng.overview.dto.Deployment;
import io.harness.ng.overview.dto.DeploymentChangeRates;
import io.harness.ng.overview.dto.DeploymentChangeRatesV2;
import io.harness.ng.overview.dto.DeploymentCount;
import io.harness.ng.overview.dto.DeploymentDateAndCount;
import io.harness.ng.overview.dto.DeploymentInfo;
import io.harness.ng.overview.dto.DeploymentInfoV2;
import io.harness.ng.overview.dto.DeploymentStatusInfoList;
import io.harness.ng.overview.dto.DeploymentsSummary;
import io.harness.ng.overview.dto.DeploymentsSummaryInfo;
import io.harness.ng.overview.dto.DeploymentsSummaryPercentage;
import io.harness.ng.overview.dto.DeploymentsSummaryPercentageInfo;
import io.harness.ng.overview.dto.EntityStatusDetails;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfo;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList;
import io.harness.ng.overview.dto.EnvIdCountPair;
import io.harness.ng.overview.dto.EnvironmentDeploymentInfo;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentInfoByServiceId;
import io.harness.ng.overview.dto.EnvironmentInfoDTO;
import io.harness.ng.overview.dto.ExecutionDeployment;
import io.harness.ng.overview.dto.ExecutionDeploymentInfo;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.dto.HealthDeploymentDashboard;
import io.harness.ng.overview.dto.HealthDeploymentDashboardV2;
import io.harness.ng.overview.dto.HealthDeploymentDetails;
import io.harness.ng.overview.dto.HealthDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentInfoV2;
import io.harness.ng.overview.dto.IconDTO;
import io.harness.ng.overview.dto.InfrastructureInfoDTO;
import io.harness.ng.overview.dto.InstanceGroupedByArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedOnChartVersionList;
import io.harness.ng.overview.dto.InstancesByBuildIdList;
import io.harness.ng.overview.dto.LastWorkloadInfo;
import io.harness.ng.overview.dto.LatestServiceDeploymentResponseDTO;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.PipelineExecutionInfoDTO;
import io.harness.ng.overview.dto.SequenceToggleDTO;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
import io.harness.ng.overview.dto.ServiceDeployment;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTO;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTOV2;
import io.harness.ng.overview.dto.ServiceDeploymentListInfo;
import io.harness.ng.overview.dto.ServiceDeploymentListInfoV2;
import io.harness.ng.overview.dto.ServiceDeploymentMetrics;
import io.harness.ng.overview.dto.ServiceDeploymentV2;
import io.harness.ng.overview.dto.ServiceDeploymentsList;
import io.harness.ng.overview.dto.ServiceDetailsDTO;
import io.harness.ng.overview.dto.ServiceDetailsDTO.ServiceDetailsDTOBuilder;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2.ServiceDetailsDTOV2Builder;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTO;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTOV2;
import io.harness.ng.overview.dto.ServiceGrowthTrendAndEnvBasedInfo;
import io.harness.ng.overview.dto.ServiceHeaderInfo;
import io.harness.ng.overview.dto.ServiceInfoDTO;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.ServicePipelineWithRevertInfo;
import io.harness.ng.overview.dto.TimeAndEnvTypeDeployment;
import io.harness.ng.overview.dto.TimeAndStatusDeployment;
import io.harness.ng.overview.dto.TimeValuePair;
import io.harness.ng.overview.dto.TimeValuePairListDTO;
import io.harness.ng.overview.dto.TotalDeploymentInfo;
import io.harness.ng.overview.dto.TotalDeploymentInfoV2;
import io.harness.ng.overview.dto.TriggeredByInfoDTO;
import io.harness.ng.overview.dto.WorkloadCountInfo;
import io.harness.ng.overview.dto.WorkloadDateCountInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentDetails;
import io.harness.ng.overview.dto.WorkloadDeploymentInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentInfoV2;
import io.harness.ng.overview.dto.WorkloadInfo;
import io.harness.ng.overview.util.GrowthTrendEvaluator;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.remote.client.NGRestUtils;
import io.harness.service.instancedashboardservice.InstanceDashboardService;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.ModifyPreparedStatement;
import io.harness.timescaledb.PaginatedQueryCallback;
import io.harness.timescaledb.PaginatedQueryCallbackViaJooq;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimescalePersistence;
import io.harness.timescaledb.tables.NgInstanceStatsDay;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record19;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record4;
import org.jooq.Record7;
import org.jooq.Record9;
import org.jooq.ResultQuery;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateStep;
import org.jooq.SelectHavingStep;
import org.jooq.Table;
import org.jooq.WithStep;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CDOverviewDashboardServiceImpl implements CDOverviewDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject @Named("SecondaryTimeScaleDBService") TimeScaleDBService secondaryTimeScaleDBService;
  @Inject DSLContext dslContext;
  @Inject ServiceEntityService serviceEntityService;
  @Inject InstanceDashboardService instanceDashboardService;
  @Inject ServiceEntityService serviceEntityServiceImpl;
  @Inject EnvironmentServiceImpl environmentService;
  @Inject EnvironmentGroupServiceImpl environmentGroupService;
  @Inject ServiceSequenceService serviceSequenceService;
  @Inject TemplateResourceClient templateResourceClient;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Inject NextGenConfiguration nextGenConfiguration;
  @Inject NGFeatureFlagHelperService featureFlagService;
  @Inject ScopeInfoService scopeResolverService;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject TimeScaleDAL timeScaleDAL;
  @Inject AccessControlClient accessControlClient;

  private String tableNameCD = "pipeline_execution_summary_cd";
  private String EMPTY_ARTIFACT = "";
  private String CUSTOM_DEPLOYMENT = "CustomDeployment";
  private String tableNameServiceAndInfra = "service_infra_info";
  private static final String PIPELINE_EXECUTION_SUMMARY_CD_ID = "pipeline_execution_summary_cd_id";
  private static final String EXECUTION_FAILURE_DETAILS = "execution_failure_details";
  public static List<String> activeStatusList = Arrays.asList(ExecutionStatus.RUNNING.name(),
      ExecutionStatus.ASYNCWAITING.name(), ExecutionStatus.TASKWAITING.name(), ExecutionStatus.TIMEDWAITING.name(),
      ExecutionStatus.PAUSED.name(), ExecutionStatus.PAUSING.name());
  private List<String> pendingStatusList = Arrays.asList(ExecutionStatus.INTERVENTIONWAITING.name(),
      ExecutionStatus.APPROVALWAITING.name(), ExecutionStatus.WAITING.name(), ExecutionStatus.RESOURCEWAITING.name());
  private static final int MAX_RETRY_COUNT = 5;
  private static final int RETRY_WAIT_DURATION = 10;
  private static int BATCH_SIZE = 1000;
  private static int IN_QUERY_ARRAY_MAX_SIZE = 25000;
  public static final double INVALID_CHANGE_RATE = -10000;
  private static final String SERVICE_NAME = "service_name";
  private static final String SERVICE_ID = "service_id";
  private static final String ARTIFACT_IMAGE = "artifact_image";
  private static final String TAG = "tag";
  private static final String ARTIFACT_DISPLAY_NAME = "artifact_display_name";
  private static final String ACCOUNT_ID = "accountid";
  private static final String ORG_ID = "orgidentifier";
  private static final String PROJECT_ID = "projectidentifier";
  private static final String SERVICE_STARTTS = "service_startts";
  private static final String PARENT_UNIQUE_ID = "parent_unique_id";
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";
  private static final Integer QUERY_PAGE_SIZE = 1000;
  private static final String DASHBOARD_DATA_BEING_QUERIED_FOR_MORE_THAN_A_YEAR =
      "Time interval being queried is for %s number of days which is more than a year";

  private static final String QUERY_EXECUTION_FAILED_ERROR_MESSAGE =
      "Query execution failed with error: %s after total tries = %s";
  private static final String KUBERNETES_GIT_OPS_DEPLOYMENT_TYPE = "KubernetesGitOps";

  public String executionStatusCdTimeScaleColumns() {
    return "id,"
        + "name,"
        + "pipelineidentifier,"
        + "startts,"
        + "endTs,"
        + "status,"
        + "planexecutionid,"
        + "moduleinfo_branch_name,"
        + "source_branch,"
        + "moduleinfo_branch_commit_message,"
        + "moduleinfo_branch_commit_id,"
        + "moduleinfo_event,"
        + "moduleinfo_repository,"
        + "trigger_type,"
        + "moduleinfo_author_id,"
        + "author_avatar,"
        + "orgidentifier,"
        + "projectidentifier,"
        + "parent_unique_id";
  }

  public String queryBuilderSelectStatusTime(long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select status,startts from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery)
        .append(String.format("parent_unique_id in ('%s') and ",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))));

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(
          String.format("startts is not null and startts>=%s and startts<%s;", startInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  public Query queryBuilderSelectStatusTimeViaJooq(long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record2<String, Long>> query =
        dslContext.select(PIPELINE_EXECUTION_SUMMARY_CD.STATUS, PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)
            .from(PIPELINE_EXECUTION_SUMMARY_CD)
            .where(trueCondition()); // JOOQ's way to initialize a condition chain
    query = query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (startInterval > 0 && endInterval > 0) {
      query = query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull()
                            .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
                            .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval)));
    }

    return query;
  }

  public String queryBuilderSelectEnvTypeTime(
      long startInterval, long endInterval, boolean returnStatus, List<String> parentUniqueIds) {
    String selectStatusQuery =
        "select service_status,env_type,service_startts from " + tableNameServiceAndInfra + " where ";
    StringBuilder querySqlBuilder = new StringBuilder(1024);
    querySqlBuilder.append(selectStatusQuery)
        .append(String.format("parent_unique_id in ('%s') and ",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))));

    if (returnStatus) {
      querySqlBuilder.append("service_status is not null and ");
    }

    if (startInterval > 0 && endInterval > 0) {
      querySqlBuilder.append(String.format(
          "env_type is not null and service_startts is not null and service_startts>=%s and service_startts<%s;",
          startInterval, endInterval));
    }
    return querySqlBuilder.toString();
  }

  public SelectConditionStep<Record3<String, String, Long>> queryBuilderSelectEnvTypeTimeViaJooq(
      long startInterval, long endInterval, boolean returnStatus, List<String> parentUniqueIds) {
    SelectConditionStep<Record3<String, String, Long>> query =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_STATUS, SERVICE_INFRA_INFO.ENV_TYPE, SERVICE_INFRA_INFO.SERVICE_STARTTS)
            .from(SERVICE_INFRA_INFO)
            .where(trueCondition()); // JOOQ's way to initialize a condition chain

    query = query.and(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (returnStatus) {
      query = query.and(SERVICE_INFRA_INFO.SERVICE_STATUS.isNotNull());
    }

    if (startInterval > 0 && endInterval > 0) {
      query = query.and(SERVICE_INFRA_INFO.ENV_TYPE.isNotNull());

      query = query.and(SERVICE_INFRA_INFO.SERVICE_STARTTS.isNotNull()
                            .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.ge(startInterval))
                            .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lt(endInterval)));
    }

    return query;
  }

  public String queryBuilderSelectIdCdTable(long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder(256);
    totalBuildSqlBuilder.append(selectStatusQuery)
        .append(String.format("parent_unique_id in ('%s') and ",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))));

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(
          String.format("startts is not null and startts>=%s and startts<%s;", startInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  public SelectConditionStep<Record1<String>> queryBuilderSelectIdCdTableJooq(
      long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record1<String>> query = dslContext.select(PIPELINE_EXECUTION_SUMMARY_CD.ID)
                                                     .from(PIPELINE_EXECUTION_SUMMARY_CD)
                                                     .where(trueCondition()); // Initialize the condition chain
    query = query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (startInterval > 0 && endInterval > 0) {
      query = query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull()
                            .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
                            .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval)));
    }

    return query;
  }

  public String queryBuilderSelectIdLimitTimeCdTableNew(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String queryPrepared =
        queryBuilderSelectIdLimitTimeCdTablePrepared(days, statusList, startInterval, endInterval, parentUniqueIds);
    return (queryPrepared != null)
        ? queryPrepared
        : queryBuilderSelectIdLimitTimeCdTable(days, statusList, startInterval, endInterval, parentUniqueIds);
  }

  public SelectConditionStep queryBuilderSelectIdLimitTimeCdTableNewViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep queryPrepared = queryBuilderSelectIdLimitTimeCdTablePreparedViaJooq(
        days, statusList, startInterval, endInterval, parentUniqueIds);
    return (queryPrepared != null)
        ? queryPrepared
        : queryBuilderSelectIdLimitTimeCdTableViaJooq(days, statusList, startInterval, endInterval, parentUniqueIds);
  }

  public String queryBuilderSelectIdLimitTimeCdTablePrepared(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder sqlBuilder = new StringBuilder(200);
    sqlBuilder.append(selectStatusQuery)
        .append("parent_unique_id in ('")
        .append(
            String.join("','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new)))
        .append("') and status in (");
    for (String status : statusList) {
      sqlBuilder.append("'" + DashboardServiceHelper.escapeSql(status) + "',");
    }

    sqlBuilder.deleteCharAt(sqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      sqlBuilder.append(String.format(") and startts >= %s and startts < %s", startInterval, endInterval));
    } else {
      sqlBuilder.append(')');
    }

    sqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s", days));

    return sqlBuilder.toString();
  }

  public SelectConditionStep queryBuilderSelectIdLimitTimeCdTablePreparedViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record1<String>> query =
        dslContext.select(PIPELINE_EXECUTION_SUMMARY_CD.ID).from(PIPELINE_EXECUTION_SUMMARY_CD).where(trueCondition());
    query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (isEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull());

    if (startInterval > 0 && endInterval > 0) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
          .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    }

    query.orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc()).limit(days);

    return query;
  }

  public String queryBuilderSelectIdLimitTimeCdTable(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder(256);
    totalBuildSqlBuilder.append(selectStatusQuery)
        .append("parent_unique_id in ('")
        .append(
            String.join("','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new)))
        .append("') and status in (");
    for (String status : statusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", DashboardServiceHelper.escapeSql(status)));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      totalBuildSqlBuilder.append(String.format(")"));
    }

    totalBuildSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s", days));

    return totalBuildSqlBuilder.toString();
  }

  public SelectConditionStep queryBuilderSelectIdLimitTimeCdTableViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record1<String>> query =
        dslContext.select(PIPELINE_EXECUTION_SUMMARY_CD.ID).from(PIPELINE_EXECUTION_SUMMARY_CD).where(trueCondition());

    query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (isEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull());
    if (startInterval > 0 && endInterval > 0) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
          .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    }
    query.orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc()).limit(days);

    return query;
  }

  public String queryBuilderEnvironmentType(long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select env_type from " + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (startInterval > 0 && endInterval > 0) {
      String idQuery = queryBuilderSelectIdCdTable(startInterval, endInterval, parentUniqueIds);
      idQuery = idQuery.replace(';', ' ');
      totalBuildSqlBuilder.append(
          String.format("pipeline_execution_summary_cd_id in (%s) and env_type is not null;", idQuery));
    }

    return totalBuildSqlBuilder.toString();
  }

  public SelectConditionStep<Record1<String>> queryBuilderEnvironmentTypeJooq(
      long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record1<String>> idSubquery =
        queryBuilderSelectIdCdTableJooq(startInterval, endInterval, parentUniqueIds);

    return dslContext.select(SERVICE_INFRA_INFO.ENV_TYPE)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idSubquery)
                   .and(SERVICE_INFRA_INFO.ENV_TYPE.isNotNull()));
  }

  public double getRate(long current, long previous) {
    double rate = 0.0;
    if (previous != 0) {
      rate = (current - previous) / (double) previous;
    }
    rate = rate * 100.0;
    return rate;
  }

  public String queryBuilderStatusNew(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String queryPrepared = queryBuilderStatusPrepared(days, statusList, startInterval, endInterval, parentUniqueIds);
    return (queryPrepared != null) ? queryPrepared
                                   : queryBuilderStatus(days, statusList, startInterval, endInterval, parentUniqueIds);
  }

  public SelectConditionStep queryBuilderStatusNewViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep queryPrepared =
        queryBuilderStatusPreparedViaJooq(days, statusList, startInterval, endInterval, parentUniqueIds);
    return (queryPrepared != null)
        ? queryPrepared
        : queryBuilderStatusViaJooq(days, statusList, startInterval, endInterval, parentUniqueIds);
  }

  public String queryBuilderStatusPrepared(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where ";
    StringBuilder sqlBuilder = new StringBuilder(200);
    sqlBuilder.append(selectStatusQuery)
        .append(String.format("parent_unique_id in ('%s') and ",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))))
        .append("status in (");
    for (String status : statusList) {
      sqlBuilder.append("'" + DashboardServiceHelper.escapeSql(status) + "',");
    }

    sqlBuilder.deleteCharAt(sqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      sqlBuilder.append(String.format(") and startts >= %s and startts < %s", startInterval, endInterval));
    } else {
      sqlBuilder.append(')');
    }

    sqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %d;", days));

    return sqlBuilder.toString();
  }

  public SelectConditionStep queryBuilderStatusPreparedViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record19<String, String, String, Long, Long, String, String, String, String, String, String,
        String, String, String, String, String, String, String, String>> query =
        dslContext
            .select(PIPELINE_EXECUTION_SUMMARY_CD.ID, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
                PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
                PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
                PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME,
                PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY,
                PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
                PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR, PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER,
                PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID)
            .from(PIPELINE_EXECUTION_SUMMARY_CD)
            .where(trueCondition());
    query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (isEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull());
    if (startInterval > 0 && endInterval > 0) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
          .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    }

    query.orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc()).limit(days);

    return query;
  }

  public String queryBuilderStatus(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String selectStatusQuery = "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery)
        .append(String.format("parent_unique_id in ('%s') and ",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))))
        .append("status in (");
    for (String status : statusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", DashboardServiceHelper.escapeSql(status)));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      totalBuildSqlBuilder.append(String.format(")"));
    }

    totalBuildSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s;", days));

    return totalBuildSqlBuilder.toString();
  }

  public SelectConditionStep queryBuilderStatusViaJooq(
      long days, List<String> statusList, long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record19<String, String, String, Long, Long, String, String, String, String, String, String,
        String, String, String, String, String, String, String, String>> query =
        dslContext
            .select(PIPELINE_EXECUTION_SUMMARY_CD.ID, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
                PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
                PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
                PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME,
                PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID,
                PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY,
                PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
                PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR, PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER,
                PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID)
            .from(PIPELINE_EXECUTION_SUMMARY_CD)
            .where(trueCondition());
    query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (isEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.isNotNull());
    if (startInterval > 0 && endInterval > 0) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
          .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    }

    query.orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc()).limit(days);

    return query;
  }

  public String queryBuilderServiceTag(String queryIdCdTable) {
    return queryBuilderServiceTag(queryIdCdTable, null);
  }

  public String queryBuilderServiceTag(String queryIdCdTable, String serviceId) {
    String selectStatusQuery =
        "select service_name,service_id,tag,env_id,env_name,env_type,artifact_image,pipeline_execution_summary_cd_id, "
        + "infrastructureidentifier, infrastructureName from " + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder(20480);

    totalBuildSqlBuilder.append(String.format(
        selectStatusQuery + "pipeline_execution_summary_cd_id in (%s) and service_name is not null", queryIdCdTable));

    if (serviceId != null) {
      totalBuildSqlBuilder.append(String.format(" and service_id='%s'", DashboardServiceHelper.escapeSql(serviceId)));
    }
    totalBuildSqlBuilder.append(';');
    return totalBuildSqlBuilder.toString();
  }

  public SelectConditionStep queryBuilderServiceTagViaJooq(SelectConditionStep queryIdCdTable) {
    return queryBuilderServiceTagViaJooq(queryIdCdTable, null);
  }

  public SelectConditionStep queryBuilderServiceTagViaJooq(SelectConditionStep queryIdCdTable, String serviceId) {
    SelectConditionStep query =
        DSL.select(SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.TAG,
               SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.ENV_NAME, SERVICE_INFRA_INFO.ENV_TYPE,
               SERVICE_INFRA_INFO.ARTIFACT_IMAGE, SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID,
               SERVICE_INFRA_INFO.INFRASTRUCTUREIDENTIFIER, SERVICE_INFRA_INFO.INFRASTRUCTURENAME)
            .from(SERVICE_INFRA_INFO)
            .where(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(queryIdCdTable))
            .and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull());
    if (serviceId != null) {
      query.and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId));
    }
    return query;
  }

  public String queryBuilderSelectWorkload(
      long previousStartInterval, long endInterval, EnvironmentType envType, List<String> parentUniqueIds) {
    String selectStatusQuery =
        "select service_name,service_id,service_status as status,service_startts as startts,service_endts as "
        + "endts,deployment_type, pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (previousStartInterval > 0 && endInterval > 0) {
      String idQuery = queryBuilderSelectIdCdTable(previousStartInterval, endInterval, parentUniqueIds);
      idQuery = idQuery.replace(';', ' ');

      if (envType != null) {
        totalBuildSqlBuilder.append(String.format("env_Type='%s' and ", envType.toString()));
      }

      totalBuildSqlBuilder.append(String.format(
          "pipeline_execution_summary_cd_id in (%s) and service_name is not null and service_id is not null;",
          idQuery));
    }

    return totalBuildSqlBuilder.toString();
  }

  public Query queryBuilderSelectWorkloadViaJooq(
      long previousStartInterval, long endInterval, EnvironmentType envType, List<String> parentUniqueIds) {
    SelectConditionStep<Record7<String, String, String, Long, Long, String, String>> query =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.SERVICE_ID,
                SERVICE_INFRA_INFO.SERVICE_STATUS.as("status"), SERVICE_INFRA_INFO.SERVICE_STARTTS.as("startts"),
                SERVICE_INFRA_INFO.SERVICE_ENDTS.as("endts"), SERVICE_INFRA_INFO.DEPLOYMENT_TYPE,
                SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
            .from(SERVICE_INFRA_INFO)
            .where(trueCondition());

    if (previousStartInterval > 0 && endInterval > 0) {
      SelectConditionStep<Record1<String>> idQuery =
          queryBuilderSelectIdCdTableJooq(previousStartInterval, endInterval, parentUniqueIds);
      if (envType != null) {
        query.and(SERVICE_INFRA_INFO.ENV_TYPE.eq(envType.toString()));
      }
      query.and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idQuery));
      query.and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull()).and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());
    }

    return query;
  }

  public TimeAndStatusDeployment queryCalculatorTimeAndStatus(String query) {
    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          status.add(resultSet.getString("status"));
          time.add(Long.valueOf(resultSet.getString("startts")));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return TimeAndStatusDeployment.builder().status(status).time(time).build();
  }

  public TimeAndStatusDeployment queryCalculatorTimeAndStatus(Query query) {
    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          status.add(record.get("status", String.class));
          time.add(record.get("startts", Long.class));
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    return TimeAndStatusDeployment.builder().status(status).time(time).build();
  }

  public TimeAndEnvTypeDeployment queryCalculatorTimeAndEnvType(String query, boolean returnStatus) {
    List<Long> time = new ArrayList<>();
    List<String> envType = new ArrayList<>();
    List<String> status = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          envType.add(resultSet.getString("env_type"));
          time.add(Long.valueOf(resultSet.getString("service_startts")));
          status.add(resultSet.getString("service_status"));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return TimeAndEnvTypeDeployment.builder().envType(envType).status(status).time(time).build();
  }

  public TimeAndEnvTypeDeployment queryCalculatorTimeAndEnvTypeViaJooq(Query query, boolean returnStatus) {
    List<Long> time = new ArrayList<>();
    List<String> envType = new ArrayList<>();
    List<String> status = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          envType.add(record.get("env_type", String.class));
          time.add(record.get("service_startts", Long.class));
          status.add(record.get("service_status", String.class));
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    return TimeAndEnvTypeDeployment.builder().envType(envType).status(status).time(time).build();
  }

  public List<String> queryCalculatorEnvType(String queryEnvironmentType) {
    List<String> envType = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryEnvironmentType)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          envType.add(resultSet.getString("env_type"));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return envType;
  }

  public List<String> queryCalculatorEnvType(Query queryEnvironmentType) {
    List<String> envType = new ArrayList<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(queryEnvironmentType.getSQL(), queryEnvironmentType.getBindValues().toArray())
            .forEach(record -> { envType.add(record.get("env_type", String.class)); });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return envType;
  }

  @Override
  public io.harness.ng.overview.dto.HealthDeploymentDashboard getHealthDeploymentDashboard(String accountId,
      String orgId, String projectId, long startInterval, long endInterval, long previousStartInterval) {
    HealthDeploymentDetails healthDeploymentDetails;
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      healthDeploymentDetails = healthDeploymentDashboardHelperViaJooq(
          accountId, orgId, projectId, startInterval, endInterval, previousStartInterval, parentUniqueIds);
    } else {
      healthDeploymentDetails = healthDeploymentDashboardHelper(
          accountId, orgId, projectId, startInterval, endInterval, previousStartInterval, parentUniqueIds);
    }

    return HealthDeploymentDashboard.builder()
        .healthDeploymentInfo(HealthDeploymentInfo.builder()
                                  .total(TotalDeploymentInfo.builder()
                                             .count(healthDeploymentDetails.getTotal())
                                             .production(healthDeploymentDetails.getProduction())
                                             .rate(getRate(healthDeploymentDetails.getTotal(),
                                                 healthDeploymentDetails.getPreviousDeployment()))
                                             .nonProduction(healthDeploymentDetails.getNonProduction())
                                             .countList(healthDeploymentDetails.getTotalDateAndCount())
                                             .build())
                                  .success(DeploymentInfo.builder()
                                               .count(healthDeploymentDetails.getCurrentSuccess())
                                               .rate(getRate(healthDeploymentDetails.getCurrentSuccess(),
                                                   healthDeploymentDetails.getPreviousSuccess()))
                                               .countList(healthDeploymentDetails.getSuccessDateAndCount())
                                               .build())
                                  .failure(DeploymentInfo.builder()
                                               .count(healthDeploymentDetails.getCurrentFailed())
                                               .rate(getRate(healthDeploymentDetails.getCurrentFailed(),
                                                   healthDeploymentDetails.getPreviousFailed()))
                                               .countList(healthDeploymentDetails.getFailedDateAndCount())
                                               .build())
                                  .active(DeploymentInfo.builder()
                                              .count(healthDeploymentDetails.getCurrentActive())
                                              .rate(getRate(healthDeploymentDetails.getCurrentActive(),
                                                  healthDeploymentDetails.getPreviousActive()))
                                              .countList(healthDeploymentDetails.getActiveDateAndCount())
                                              .build())
                                  .build())
        .build();
  }

  @Override
  public HealthDeploymentDashboardV2 getHealthDeploymentDashboardV2(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval) {
    HealthDeploymentDetails healthDeploymentDetails;
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      healthDeploymentDetails = healthDeploymentDashboardHelperViaJooq(
          accountId, orgId, projectId, startInterval, endInterval, previousStartInterval, parentUniqueIds);
    } else {
      healthDeploymentDetails = healthDeploymentDashboardHelper(
          accountId, orgId, projectId, startInterval, endInterval, previousStartInterval, parentUniqueIds);
    }

    return HealthDeploymentDashboardV2.builder()
        .healthDeploymentInfo(
            HealthDeploymentInfoV2.builder()
                .total(TotalDeploymentInfoV2.builder()
                           .count(healthDeploymentDetails.getTotal())
                           .production(healthDeploymentDetails.getProduction())
                           .rate(calculateChangeRateV2(
                               healthDeploymentDetails.getPreviousDeployment(), healthDeploymentDetails.getTotal()))
                           .nonProduction(healthDeploymentDetails.getNonProduction())
                           .countList(healthDeploymentDetails.getTotalDateAndCount())
                           .build())
                .success(DeploymentInfoV2.builder()
                             .count(healthDeploymentDetails.getCurrentSuccess())
                             .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousSuccess(),
                                 healthDeploymentDetails.getCurrentSuccess()))
                             .countList(healthDeploymentDetails.getSuccessDateAndCount())
                             .build())
                .failure(DeploymentInfoV2.builder()
                             .count(healthDeploymentDetails.getCurrentFailed())
                             .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousFailed(),
                                 healthDeploymentDetails.getCurrentFailed()))
                             .countList(healthDeploymentDetails.getFailedDateAndCount())
                             .build())
                .active(DeploymentInfoV2.builder()
                            .count(healthDeploymentDetails.getCurrentActive())
                            .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousActive(),
                                healthDeploymentDetails.getCurrentActive()))
                            .countList(healthDeploymentDetails.getActiveDateAndCount())
                            .build())
                .build())
        .build();
  }

  public HealthDeploymentDetails healthDeploymentDashboardHelper(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval, List<String> parentUniqueIds) {
    String query = queryBuilderSelectStatusTime(previousStartInterval, endInterval, parentUniqueIds);

    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<String> envType = new ArrayList<>();

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    time = timeAndStatusDeployment.getTime();
    status = timeAndStatusDeployment.getStatus();

    long total = 0;
    long currentSuccess = 0;
    long currentFailed = 0;
    long currentActive = 0;
    long previousSuccess = 0;
    long previousFailed = 0;
    long previousDeployment = 0;
    long previousActive = 0;

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();
    HashMap<Long, Integer> activeCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      activeCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      if (currentTimeEpoch >= startInterval && currentTimeEpoch < endInterval) {
        currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
        total++;
        totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          currentSuccess++;
          successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          currentActive++;
          activeCountMap.put(currentTimeEpoch, activeCountMap.get(currentTimeEpoch) + 1);
        } else {
          currentFailed++;
          failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
        }
      } else {
        previousDeployment++;
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          previousSuccess++;
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          previousActive++;
        } else {
          previousFailed++;
        }
      }
    }

    String queryEnvironmentType = queryBuilderEnvironmentType(startInterval, endInterval, parentUniqueIds);
    envType = queryCalculatorEnvType(queryEnvironmentType);

    long production = Collections.frequency(envType, EnvironmentType.Production.name());
    long nonProduction = Collections.frequency(envType, EnvironmentType.PreProduction.name());

    List<DeploymentDateAndCount> totalDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> successDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> failedDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> activeDateAndCount = new ArrayList<>();

    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      totalDateAndCount.add(DeploymentDateAndCount.builder()
                                .time(startDateCopy)
                                .deployments(Deployment.builder().count(totalCountMap.get(startDateCopy)).build())
                                .build());
      successDateAndCount.add(DeploymentDateAndCount.builder()
                                  .time(startDateCopy)
                                  .deployments(Deployment.builder().count(successCountMap.get(startDateCopy)).build())
                                  .build());
      failedDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(failedCountMap.get(startDateCopy)).build())
                                 .build());
      activeDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(activeCountMap.get(startDateCopy)).build())
                                 .build());
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    return HealthDeploymentDetails.builder()
        .total(total)
        .currentActive(currentActive)
        .currentFailed(currentFailed)
        .currentSuccess(currentSuccess)
        .previousDeployment(previousDeployment)
        .previousActive(previousActive)
        .previousFailed(previousFailed)
        .previousSuccess(previousSuccess)
        .production(production)
        .nonProduction(nonProduction)
        .totalDateAndCount(totalDateAndCount)
        .activeDateAndCount(activeDateAndCount)
        .failedDateAndCount(failedDateAndCount)
        .successDateAndCount(successDateAndCount)
        .build();
  }

  public HealthDeploymentDetails healthDeploymentDashboardHelperViaJooq(String accountId, String orgId,
      String projectId, long startInterval, long endInterval, long previousStartInterval,
      List<String> parentUniqueIds) {
    Query query = queryBuilderSelectStatusTimeViaJooq(previousStartInterval, endInterval, parentUniqueIds);

    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<String> envType = new ArrayList<>();

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    time = timeAndStatusDeployment.getTime();
    status = timeAndStatusDeployment.getStatus();

    long total = 0;
    long currentSuccess = 0;
    long currentFailed = 0;
    long currentActive = 0;
    long previousSuccess = 0;
    long previousFailed = 0;
    long previousDeployment = 0;
    long previousActive = 0;

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();
    HashMap<Long, Integer> activeCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      activeCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      if (currentTimeEpoch >= startInterval && currentTimeEpoch < endInterval) {
        currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
        total++;
        totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          currentSuccess++;
          successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          currentActive++;
          activeCountMap.put(currentTimeEpoch, activeCountMap.get(currentTimeEpoch) + 1);
        } else {
          currentFailed++;
          failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
        }
      } else {
        previousDeployment++;
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          previousSuccess++;
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          previousActive++;
        } else {
          previousFailed++;
        }
      }
    }

    Query queryEnvironmentType = queryBuilderEnvironmentTypeJooq(startInterval, endInterval, parentUniqueIds);
    envType = queryCalculatorEnvType(queryEnvironmentType);

    long production = Collections.frequency(envType, EnvironmentType.Production.name());
    long nonProduction = Collections.frequency(envType, EnvironmentType.PreProduction.name());

    List<DeploymentDateAndCount> totalDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> successDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> failedDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> activeDateAndCount = new ArrayList<>();

    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      totalDateAndCount.add(DeploymentDateAndCount.builder()
                                .time(startDateCopy)
                                .deployments(Deployment.builder().count(totalCountMap.get(startDateCopy)).build())
                                .build());
      successDateAndCount.add(DeploymentDateAndCount.builder()
                                  .time(startDateCopy)
                                  .deployments(Deployment.builder().count(successCountMap.get(startDateCopy)).build())
                                  .build());
      failedDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(failedCountMap.get(startDateCopy)).build())
                                 .build());
      activeDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(activeCountMap.get(startDateCopy)).build())
                                 .build());
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    return HealthDeploymentDetails.builder()
        .total(total)
        .currentActive(currentActive)
        .currentFailed(currentFailed)
        .currentSuccess(currentSuccess)
        .previousDeployment(previousDeployment)
        .previousActive(previousActive)
        .previousFailed(previousFailed)
        .previousSuccess(previousSuccess)
        .production(production)
        .nonProduction(nonProduction)
        .totalDateAndCount(totalDateAndCount)
        .activeDateAndCount(activeDateAndCount)
        .failedDateAndCount(failedDateAndCount)
        .successDateAndCount(successDateAndCount)
        .build();
  }

  private io.harness.ng.overview.dto.ExecutionDeployment getExecutionDeployment(
      Long time, long total, long success, long failed) {
    return io.harness.ng.overview.dto.ExecutionDeployment.builder()
        .time(time)
        .deployments(
            io.harness.ng.overview.dto.DeploymentCount.builder().total(total).success(success).failure(failed).build())
        .build();
  }

  private DeploymentsSummary getDeploymentsSummary(Long time, long total, long prod, long nonProd) {
    return DeploymentsSummary.builder().time(time).total(total).prod(prod).nonProd(nonProd).build();
  }

  private DeploymentsSummaryPercentage getDeploymentsSummaryPercentage(
      Long time, double total, double prod, double nonProd) {
    return DeploymentsSummaryPercentage.builder().time(time).total(total).prod(prod).nonProd(nonProd).build();
  }

  public Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
  queryCalculatorServiceTagMag(String queryServiceTag) {
    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, EnvironmentDeploymentsInfo> envIdToNameAndTypeMap = new HashMap<>();
    HashMap<String, HashMap<String, List<InfrastructureInfo>>> pipelineEnvInfraMap = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryServiceTag)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          String service_name = resultSet.getString(SERVICE_NAME);
          String service_id = resultSet.getString(SERVICE_ID);
          String tag = resultSet.getString("tag");
          String envId = resultSet.getString("env_id");
          String envName = resultSet.getString("env_name");
          String envType = resultSet.getString("env_type");
          String image = resultSet.getString("artifact_image");
          String infrastructureIdentifier = resultSet.getString("infrastructureidentifier");
          String infrastructureName = resultSet.getString("infrastructurename");
          if (serviceTagMap.containsKey(pipeline_execution_summary_cd_id)) {
            serviceTagMap.get(pipeline_execution_summary_cd_id)
                .add(getServiceDeployment(service_name, tag, image, service_id));
          } else {
            List<ServiceDeploymentInfo> serviceDeploymentInfos = new ArrayList<>();
            serviceDeploymentInfos.add(getServiceDeployment(service_name, tag, image, service_id));
            serviceTagMap.put(pipeline_execution_summary_cd_id, serviceDeploymentInfos);
          }
          envIdToNameAndTypeMap.putIfAbsent(
              envId, EnvironmentDeploymentsInfo.builder().envType(envType).envName(envName).build());

          pipelineEnvInfraMap.putIfAbsent(pipeline_execution_summary_cd_id, new HashMap<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id).putIfAbsent(envId, new ArrayList<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id)
              .get(envId)
              .add(InfrastructureInfo.builder()
                       .infrastructureName(infrastructureName)
                       .infrastructureIdentifier(infrastructureIdentifier)
                       .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return new MutablePair<>(serviceTagMap,
        getPipelineExecutionIdToEnvironmentDeploymentsInfoMap(envIdToNameAndTypeMap, pipelineEnvInfraMap));
  }

  public Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
  queryCalculatorServiceTagMag(Query queryServiceTag) {
    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, EnvironmentDeploymentsInfo> envIdToNameAndTypeMap = new HashMap<>();
    HashMap<String, HashMap<String, List<InfrastructureInfo>>> pipelineEnvInfraMap = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(queryServiceTag.getSQL(), queryServiceTag.getBindValues().toArray()).forEach(record -> {
          String pipeline_execution_summary_cd_id = record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class);
          String service_name = record.get(SERVICE_NAME, String.class);
          String service_id = record.get(SERVICE_ID, String.class);
          String tag = record.get("tag", String.class);
          String envId = record.get("env_id", String.class);
          String envName = record.get("env_name", String.class);
          String envType = record.get("env_type", String.class);
          String image = record.get("artifact_image", String.class);
          String infrastructureIdentifier = record.get("infrastructureidentifier", String.class);
          String infrastructureName = record.get("infrastructurename", String.class);
          if (serviceTagMap.containsKey(pipeline_execution_summary_cd_id)) {
            serviceTagMap.get(pipeline_execution_summary_cd_id)
                .add(getServiceDeployment(service_name, tag, image, service_id));
          } else {
            List<ServiceDeploymentInfo> serviceDeploymentInfos = new ArrayList<>();
            serviceDeploymentInfos.add(getServiceDeployment(service_name, tag, image, service_id));
            serviceTagMap.put(pipeline_execution_summary_cd_id, serviceDeploymentInfos);
          }
          envIdToNameAndTypeMap.putIfAbsent(
              envId, EnvironmentDeploymentsInfo.builder().envType(envType).envName(envName).build());
          pipelineEnvInfraMap.putIfAbsent(pipeline_execution_summary_cd_id, new HashMap<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id).putIfAbsent(envId, new ArrayList<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id)
              .get(envId)
              .add(InfrastructureInfo.builder()
                       .infrastructureName(infrastructureName)
                       .infrastructureIdentifier(infrastructureIdentifier)
                       .build());
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return new MutablePair<>(serviceTagMap,
        getPipelineExecutionIdToEnvironmentDeploymentsInfoMap(envIdToNameAndTypeMap, pipelineEnvInfraMap));
  }

  private HashMap<String, List<EnvironmentDeploymentsInfo>> getPipelineExecutionIdToEnvironmentDeploymentsInfoMap(
      HashMap<String, EnvironmentDeploymentsInfo> envIdToNameAndTypeMap,
      HashMap<String, HashMap<String, List<InfrastructureInfo>>> pipelineEnvInfraMap) {
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineExecutionIdToEnvironmentDeploymentsInfoMap =
        new HashMap<>();
    for (Map.Entry<String, HashMap<String, List<InfrastructureInfo>>> entry : pipelineEnvInfraMap.entrySet()) {
      String pipelineExecutionId = entry.getKey();
      HashMap<String, List<InfrastructureInfo>> envInfraMap = entry.getValue();
      List<EnvironmentDeploymentsInfo> environmentDeploymentsInfoList = new ArrayList<>();
      for (Map.Entry<String, List<InfrastructureInfo>> entry1 : envInfraMap.entrySet()) {
        String envId = entry1.getKey();
        List<InfrastructureInfo> infrastructureDetails = entry1.getValue();
        environmentDeploymentsInfoList.add(EnvironmentDeploymentsInfo.builder()
                                               .envId(envId)
                                               .envName(envIdToNameAndTypeMap.get(envId).getEnvName())
                                               .envType(envIdToNameAndTypeMap.get(envId).getEnvType())
                                               .infrastructureDetails(infrastructureDetails)
                                               .build());
      }
      pipelineExecutionIdToEnvironmentDeploymentsInfoMap.putIfAbsent(
          pipelineExecutionId, environmentDeploymentsInfoList);
    }
    return pipelineExecutionIdToEnvironmentDeploymentsInfoMap;
  }

  @Override
  public io.harness.ng.overview.dto.ExecutionDeploymentInfo getExecutionDeploymentDashboard(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);

    String query = queryBuilderSelectStatusTime(startInterval, endInterval, parentUniqueIds);

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    List<Long> time = timeAndStatusDeployment.getTime();
    List<String> status = timeAndStatusDeployment.getStatus();

    List<ExecutionDeployment> executionDeployments = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
        successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
        failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
      }
    }

    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      executionDeployments.add(getExecutionDeployment(startDateCopy, totalCountMap.get(startDateCopy),
          successCountMap.get(startDateCopy), failedCountMap.get(startDateCopy)));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return ExecutionDeploymentInfo.builder().executionDeploymentList(executionDeployments).build();
  }

  @Override
  public io.harness.ng.overview.dto.ExecutionDeploymentInfo getExecutionDeploymentDashboardViaJooq(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);

    Query query = queryBuilderSelectStatusTimeViaJooq(startInterval, endInterval, parentUniqueIds);

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    List<Long> time = timeAndStatusDeployment.getTime();
    List<String> status = timeAndStatusDeployment.getStatus();

    List<ExecutionDeployment> executionDeployments = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
        successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
        failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
      }
    }

    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      executionDeployments.add(getExecutionDeployment(startDateCopy, totalCountMap.get(startDateCopy),
          successCountMap.get(startDateCopy), failedCountMap.get(startDateCopy)));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return ExecutionDeploymentInfo.builder().executionDeploymentList(executionDeployments).build();
  }

  @Override
  public Map<String, String> getLastPipeline(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<String> serviceIds, ScopeInfo scopeInfo) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

    // TODO: not a good pattern to use any (?). But since serviceIds won't ahve too many values we are fine for now
    String query = "select distinct on(service_id) service_id, pipeline_execution_summary_cd_id, service_startts from "
        + "service_infra_info where parent_unique_id=? and service_id = any (?) "
        + "order by service_id, service_startts desc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, scopeInfo.getUniqueId());
        statement.setArray(2, connection.createArrayOf("VARCHAR", serviceRefs.toArray()));

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString(SERVICE_ID);
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          serviceIdToPipelineId.putIfAbsent(service_id, pipeline_execution_summary_cd_id);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return serviceIdToPipelineId;
  }

  public Map<String, String> getLastPipelineViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceIds, ScopeInfo scopeInfo) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

    Query query = dslContext
                      .selectDistinct(SERVICE_INFRA_INFO.SERVICE_ID,
                          SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.SERVICE_STARTTS)
                      .distinctOn(SERVICE_INFRA_INFO.SERVICE_ID)
                      .from(SERVICE_INFRA_INFO)
                      .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId())
                                 .and(SERVICE_INFRA_INFO.SERVICE_ID.in(serviceRefs)))
                      .orderBy(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_STARTTS.desc());

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String service_id = record.get(SERVICE_ID, String.class);
          String pipeline_execution_summary_cd_id = record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class);
          serviceIdToPipelineId.putIfAbsent(service_id, pipeline_execution_summary_cd_id);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    return serviceIdToPipelineId;
  }

  public Map<String, String> getLastPipeline(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Set<String> serviceIds, Set<String> envIds) {
    Map<String, String> serviceEnvToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

    List<String> envRefs = envIds.stream()
                               .map(envId
                                   -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                       accountIdentifier, orgIdentifier, projectIdentifier, envId))
                               .collect(Collectors.toList());

    // Unified query: Combines legacy single-env and GitOps multi-env records using UNION ALL
    // DISTINCT ON ensures we get the latest pipeline per service-env pair across both data sources
    String unifiedQuery =
        "SELECT DISTINCT ON (service_id, env_id) service_id, env_id, pipeline_execution_summary_cd_id, service_startts "
        + "FROM ( "
        // Legacy single-environment records (env_id column)
        + "SELECT service_id, env_id, pipeline_execution_summary_cd_id, service_startts "
        + "FROM service_infra_info "
        + "WHERE accountid = ? AND orgidentifier = ? AND projectidentifier = ? "
        + "AND service_id = ANY (?) AND env_id = ANY (?) "
        + "UNION ALL "
        // GitOps multi-environment records (gitops_env_ids array column with unnest)
        + "SELECT service_id, unnest(gitops_env_ids) AS env_id, pipeline_execution_summary_cd_id, service_startts "
        + "FROM service_infra_info "
        + "WHERE accountid = ? AND orgidentifier = ? AND projectidentifier = ? "
        + "AND service_id = ANY (?) AND gitops_env_ids && ? AND gitops_env_ids IS NOT NULL "
        + ") AS combined_results "
        + "WHERE env_id = ANY (?) "
        + "ORDER BY service_id, env_id, service_startts DESC";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(unifiedQuery)) {
        // Set parameters for legacy query part
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setArray(4, connection.createArrayOf("VARCHAR", serviceRefs.toArray()));
        statement.setArray(5, connection.createArrayOf("VARCHAR", envRefs.toArray()));

        // Set parameters for GitOps query part
        statement.setString(6, accountIdentifier);
        statement.setString(7, orgIdentifier);
        statement.setString(8, projectIdentifier);
        statement.setArray(9, connection.createArrayOf("VARCHAR", serviceRefs.toArray()));
        statement.setArray(10, connection.createArrayOf("TEXT", envRefs.toArray()));

        // Set parameter for final WHERE clause
        statement.setArray(11, connection.createArrayOf("TEXT", envRefs.toArray()));

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString(SERVICE_ID);
          String env_id = resultSet.getString("env_id");
          String service_env_id = service_id + '-' + env_id;
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);

          // Database already returned the latest record per service-env pair via DISTINCT ON + ORDER BY
          serviceEnvToPipelineId.put(service_env_id, pipeline_execution_summary_cd_id);
        }

        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return serviceEnvToPipelineId;
  }

  @Override
  public Map<String, String> getLastPipelineViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<String> serviceIds, Set<String> envIds) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

    List<String> envRefs = envIds.stream()
                               .map(envId
                                   -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                       accountIdentifier, orgIdentifier, projectIdentifier, envId))
                               .collect(Collectors.toList());

    Query query = dslContext
                      .selectDistinct(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.ENV_ID,
                          SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.SERVICE_STARTTS)
                      .distinctOn(SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.SERVICE_ID)
                      .from(SERVICE_INFRA_INFO)
                      .where(SERVICE_INFRA_INFO.ACCOUNTID.eq(accountIdentifier)
                                 .and(PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq(orgIdentifier))
                                 .and(PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(projectIdentifier))
                                 .and(SERVICE_INFRA_INFO.SERVICE_ID.in(serviceRefs))
                                 .and(SERVICE_INFRA_INFO.ENV_ID.in(envRefs)))
                      .groupBy(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.ENV_ID,
                          SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.SERVICE_STARTTS)
                      .orderBy(SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.SERVICE_ID,
                          SERVICE_INFRA_INFO.SERVICE_STARTTS.desc());

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String service_id = record.get(SERVICE_ID, String.class);
          String env_id = record.get("env_id", String.class);
          String service_env_id = service_id + '-' + env_id;
          String pipeline_execution_summary_cd_id = record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class);
          serviceIdToPipelineId.putIfAbsent(service_env_id, pipeline_execution_summary_cd_id);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    return serviceIdToPipelineId;
  }

  // TODO: Deployment type can be taken from service entity, evaluate if this can be removed
  private Map<String, Set<String>> getDeploymentType(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceIds, ScopeInfo scopeInfo) {
    Map<String, Set<String>> serviceIdToDeploymentType = new HashMap<>();

    // TODO: not a good pattern to use any (?). But since serviceIds won't ahve too many values we are fine for now
    String query =
        "select service_id, deployment_type, gitOpsEnabled from service_infra_info where parent_unique_id=? and "
        + " service_id = any (?) group by service_id, deployment_type, gitOpsEnabled";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, scopeInfo.getUniqueId());
        statement.setArray(2, connection.createArrayOf("VARCHAR", serviceIds.toArray()));
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString(SERVICE_ID);
          String deployment_type = resultSet.getString("deployment_type");
          boolean gitOpsEnabled = resultSet.getBoolean("gitOpsEnabled");
          serviceIdToDeploymentType.putIfAbsent(service_id, new HashSet<>());
          if (gitOpsEnabled) {
            serviceIdToDeploymentType.get(service_id).add("KubernetesGitOps");
          } else {
            serviceIdToDeploymentType.get(service_id).add(deployment_type);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return serviceIdToDeploymentType;
  }

  // TODO: Deployment type can be taken from service entity, evaluate if this can be removed
  private Map<String, Set<String>> getDeploymentTypeViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceIds, ScopeInfo scopeInfo) {
    Map<String, Set<String>> serviceIdToDeploymentType = new HashMap<>();

    Query query =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.DEPLOYMENT_TYPE, SERVICE_INFRA_INFO.GITOPSENABLED)
            .from(SERVICE_INFRA_INFO)
            .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId())
                       .and(SERVICE_INFRA_INFO.SERVICE_ID.in(serviceIds)))
            .groupBy(
                SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.DEPLOYMENT_TYPE, SERVICE_INFRA_INFO.GITOPSENABLED);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String service_id = record.get(SERVICE_ID, String.class);
          String deployment_type = record.get("deployment_type", String.class);
          boolean gitOpsEnabled = record.get("gitopsenabled", Boolean.class);
          serviceIdToDeploymentType.putIfAbsent(service_id, new HashSet<>());
          if (gitOpsEnabled) {
            serviceIdToDeploymentType.get(service_id).add("KubernetesGitOps");
          } else {
            serviceIdToDeploymentType.get(service_id).add(deployment_type);
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    return serviceIdToDeploymentType;
  }

  @Override
  public ServiceDetailsInfoDTO getServiceDetailsList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceEntity> services = serviceEntityServiceImpl.getAllNonDeletedServices(scopeInfo, sort);
    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = getDashboardWorkloadDeployment(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                  .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfo> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceRefs = getServiceRefs(accountIdentifier, orgIdentifier, projectIdentifier, services);
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipeline(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTO> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOBuilder serviceDetailsDTOBuilder = ServiceDetailsDTO.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceRef, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceId)) {
                final WorkloadDeploymentInfo workloadDeploymentInfo = serviceIdToWorkloadDeploymentInfo.get(serviceId);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              }

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTO.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  @Override
  public ServiceDetailsInfoDTO getServiceDetailsListViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceEntity> services = serviceEntityServiceImpl.getAllNonDeletedServices(scopeInfo, sort);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = getDashboardWorkloadDeploymentViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                  .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfo> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceRefs = getServiceRefs(accountIdentifier, orgIdentifier, projectIdentifier, services);
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipelineViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap =
        getPipelineExecutionDetailsViaJooq(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentTypeViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTO> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOBuilder serviceDetailsDTOBuilder = ServiceDetailsDTO.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceRef, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceId)) {
                final WorkloadDeploymentInfo workloadDeploymentInfo = serviceIdToWorkloadDeploymentInfo.get(serviceId);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              }

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTO.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  public Map<String, Set<IconDTO>> getDeploymentIconMap(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<ServiceEntity> services, Map<String, Set<String>> serviceIdToDeploymentTypeMap,
      ScopeInfo scopeInfo) {
    Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap = new HashMap<>();
    Map<String, String> serviceRefToTemplateRef = new HashMap<>();
    Map<Scope, List<String>> templateScopeToIds = new HashMap<>();
    Map<String, String> templateRefToIcon = new HashMap<>();
    // Validated from callers that services are on given scope only
    try {
      services.forEach(serviceEntity
          -> getServiceToTemplateRef(serviceIdToDeploymentTypeMap.get(serviceEntity.getIdentifier()),
              scopeInfo != null ? serviceEntity.getYaml(scopeInfo) : serviceEntity.getYaml(),
              serviceEntity.getIdentifier(), templateScopeToIds, serviceRefToTemplateRef));

      getTemplateRefToIcon(accountIdentifier, orgIdentifier, projectIdentifier, templateRefToIcon, templateScopeToIds);

      services.forEach(serviceEntity
          -> setServiceToIconList(templateRefToIcon, serviceRefToTemplateRef, serviceEntity.getIdentifier(),
              serviceIdToDeploymentTypeMap.get(serviceEntity.getIdentifier()), serviceIdToDeploymentIconMap));

    } catch (Exception e) {
      log.error("Not able to fetch icons for services ", e);
    }

    return serviceIdToDeploymentIconMap;
  }

  private List<String> getServiceRefs(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<ServiceEntity> services) {
    List<String> serviceIdentifiers = services.stream().map(ServiceEntity::getIdentifier).collect(Collectors.toList());
    return serviceIdentifiers.stream()
        .map(serviceId -> getServiceRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
        .collect(Collectors.toList());
  }

  private String getServiceRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    return IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
  }

  private void populateDeploymentTypeFromServiceEntity(Map<String, Set<String>> serviceIdToDeploymentTypeMap,
      List<ServiceEntity> services, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    for (ServiceEntity service : services) {
      String serviceRef = getServiceRef(accountIdentifier, orgIdentifier, projectIdentifier, service.getIdentifier());
      if (!serviceIdToDeploymentTypeMap.containsKey(serviceRef) && service.getType() != null) {
        serviceIdToDeploymentTypeMap.putIfAbsent(serviceRef, new HashSet<>());
        if (isGitOpsEnabledForService(service)) {
          serviceIdToDeploymentTypeMap.get(serviceRef).add(KUBERNETES_GIT_OPS_DEPLOYMENT_TYPE);
        } else {
          serviceIdToDeploymentTypeMap.get(serviceRef).add(service.getType().getYamlName());
        }
      }
    }
  }

  private boolean isGitOpsEnabledForService(ServiceEntity service) {
    if (service.getGitOpsEnabled() != null) {
      return service.getGitOpsEnabled();
    }
    return false;
  }

  private void setServiceToIconList(Map<String, String> templateRefToIcon, Map<String, String> serviceRefToTemplateRef,
      String serviceId, Set<String> deploymentType, Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap) {
    if (isNull(deploymentType)) {
      return;
    }
    String templateRef = serviceRefToTemplateRef.get(serviceId);
    String icon = "";
    if (!isEmpty(templateRef) && !isEmpty(templateRefToIcon.get(IdentifierRefHelper.getIdentifier(templateRef)))) {
      icon = templateRefToIcon.get(IdentifierRefHelper.getIdentifier(templateRef));
    }
    Set<IconDTO> iconDTOSet = new HashSet<>();
    String finalIcon = icon;
    deploymentType.forEach(deployment -> setIconToIconSet(iconDTOSet, deployment, finalIcon));
    serviceIdToDeploymentIconMap.put(serviceId, iconDTOSet);
  }

  private void setIconToIconSet(Set<IconDTO> iconDTOSet, String deployment, String icon) {
    if (CUSTOM_DEPLOYMENT.equals(deployment)) {
      iconDTOSet.add(IconDTO.builder().deploymentType(deployment).icon(icon).build());
    } else {
      iconDTOSet.add(IconDTO.builder().deploymentType(deployment).icon("").build());
    }
  }

  private void getServiceToTemplateRef(Set<String> deploymentType, String yaml, String serviceIdentifier,
      Map<Scope, List<String>> templateScopeToIds, Map<String, String> serviceRefToTemplateRef) {
    if (isEmpty(deploymentType)) {
      return;
    }
    if (deploymentType.contains(CUSTOM_DEPLOYMENT)) {
      String templateRef;
      YamlConfig yamlConfig = new YamlConfig(yaml);
      JsonNode serviceYaml = yamlConfig.getYamlMap().get("service");
      if (!isNull(serviceYaml)) {
        JsonNode serviceDefinition = serviceYaml.get("serviceDefinition");
        if (!isNull(serviceDefinition)) {
          JsonNode spec = serviceDefinition.get("spec");
          if (!isNull(spec)) {
            JsonNode customDeploymentRef = spec.get("customDeploymentRef");
            if (!isNull(customDeploymentRef)) {
              JsonNode template = customDeploymentRef.get("templateRef");
              if (!isNull(template)) {
                templateRef = template.asText();
                addTemplateByScope(templateRef, templateScopeToIds);
                serviceRefToTemplateRef.put(serviceIdentifier, templateRef);
              }
            }
          }
        }
      }
    }
  }

  private void addTemplateByScope(String templateRef, Map<Scope, List<String>> templateScopeToIds) {
    if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ACCOUNT)) {
        templateScopeToIds.put(Scope.ACCOUNT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ACCOUNT).add(templateRef.replace(ACCOUNT_IDENTIFIER, ""));
    } else if (templateRef.contains(ORG_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ORG)) {
        templateScopeToIds.put(Scope.ORG, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ORG).add(templateRef.replace(ORG_IDENTIFIER, ""));
    } else {
      if (!templateScopeToIds.containsKey(Scope.PROJECT)) {
        templateScopeToIds.put(Scope.PROJECT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.PROJECT).add(templateRef);
    }
  }

  private void getTemplateRefToIcon(String accountId, String orgId, String projectId,
      Map<String, String> templateRefToIcon, Map<Scope, List<String>> templateScopeToIds) {
    for (Map.Entry<Scope, List<String>> templateIds : templateScopeToIds.entrySet()) {
      if (!isEmpty(templateIds.getValue())) {
        TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
            TemplateFilterPropertiesDTO.builder()
                .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
                .templateIdentifiers(templateIds.getValue())
                .build();
        List<TemplateMetadataSummaryResponseDTO> templates;
        switch (templateIds.getKey()) {
          case ACCOUNT:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, null, null,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
            break;
          case ORG:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, null,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
            break;
          default:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, projectId,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
        }

        templates.forEach(template -> templateRefToIcon.put(template.getIdentifier(), template.getIcon()));
      }
    }
  }

  @Override
  public ServiceDetailsInfoDTOV2 getServiceDetailsListV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort, String repoName) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId).is(accountIdentifier);
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    applyScopeFiltersToCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoName, criteria, scopeInfo);

    int pageNum = 0;
    while (true) {
      Pageable pageRequest;
      if (isEmpty(sort)) {
        pageRequest =
            PageRequest.of(pageNum, QUERY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
      } else {
        pageRequest = PageUtils.getPageRequest(pageNum, QUERY_PAGE_SIZE, sort);
      }
      Page<ServiceEntity> pageResponse = serviceEntityService.list(criteria, pageRequest);
      services.addAll(pageResponse.getContent());
      if (pageResponse.isEmpty() || pageResponse.getNumberOfElements() < QUERY_PAGE_SIZE) {
        break;
      }
      pageNum += 1;
    }

    return getServiceDetailsInfoDTOV2(accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, services,
        previousStartTime, scopeInfo);
  }

  private void applyScopeFiltersToCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String repoName, Criteria criteria, ScopeInfo scopeInfo) {
    criteria.and(ServiceEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());

    if (isNotEmpty(repoName)) {
      criteria.and(ServiceEntityKeys.repo).is(repoName);
    }
  }

  @Override
  public ServiceDetailsInfoDTOV2 getServiceDetailsListV2ViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort, String repoName) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId).is(accountIdentifier);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    applyScopeFiltersToCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoName, criteria, scopeInfo);

    int pageNum = 0;
    while (true) {
      Pageable pageRequest;
      if (isEmpty(sort)) {
        pageRequest =
            PageRequest.of(pageNum, QUERY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
      } else {
        pageRequest = PageUtils.getPageRequest(pageNum, QUERY_PAGE_SIZE, sort);
      }
      Page<ServiceEntity> pageResponse = serviceEntityService.list(criteria, pageRequest);
      services.addAll(pageResponse.getContent());
      if (pageResponse.isEmpty() || pageResponse.getNumberOfElements() < QUERY_PAGE_SIZE) {
        break;
      }
      pageNum += 1;
    }

    return getServiceDetailsInfoDTOV2ViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime,
        services, previousStartTime, scopeInfo);
  }

  @Override
  public PageResponse<ServiceDetailsDTOV2> getServiceDetailsListV3(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort, String repoName, int size, int page,
      String searchTerm) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId).is(accountIdentifier);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    applyScopeFiltersToCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoName, criteria, scopeInfo);

    // by default AiAgent service is not included in the list of services
    // a different API is used to list AiAgent services
    excludeAiServiceTypes(criteria);

    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(ServiceEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }

    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<ServiceEntity> pageResponse = serviceEntityService.list(criteria, pageRequest);
    services.addAll(pageResponse.getContent());

    ServiceDetailsInfoDTOV2 serviceDetailsInfoDTOV2 = getServiceDetailsInfoDTOV2(accountIdentifier, orgIdentifier,
        projectIdentifier, startTime, endTime, services, previousStartTime, scopeInfo);
    if (serviceDetailsInfoDTOV2 == null) {
      return PageUtils.getNGPageResponse(pageResponse, Collections.EMPTY_LIST);
    } else {
      return PageUtils.getNGPageResponse(pageResponse, serviceDetailsInfoDTOV2.getServiceDeploymentDetailsList());
    }
  }

  @Override
  public PageResponse<ServiceDetailsDTOV2> getServiceDetailsListV3ViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTime, long endTime, List<String> sort, String repoName,
      int size, int page, String searchTerm) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId).is(accountIdentifier);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    applyScopeFiltersToCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoName, criteria, scopeInfo);

    // by default AiAgent service is not included in the list of services
    // a different API is used to list AiAgent services
    excludeAiServiceTypes(criteria);

    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(ServiceEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }

    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<ServiceEntity> pageResponse = serviceEntityService.list(criteria, pageRequest);
    services.addAll(pageResponse.getContent());

    ServiceDetailsInfoDTOV2 serviceDetailsInfoDTOV2 = getServiceDetailsInfoDTOV2ViaJooq(accountIdentifier,
        orgIdentifier, projectIdentifier, startTime, endTime, services, previousStartTime, scopeInfo);
    if (serviceDetailsInfoDTOV2 == null) {
      return PageUtils.getNGPageResponse(pageResponse, Collections.EMPTY_LIST);
    } else {
      return PageUtils.getNGPageResponse(pageResponse, serviceDetailsInfoDTOV2.getServiceDeploymentDetailsList());
    }
  }

  @Override
  public PageResponse<ServiceDashboardResponseDTO> getServicesList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> sort, String repoName, int size, int page, String searchTerm,
      ScopeInfo scopeInfo) throws Exception {
    return getServicesList(
        accountIdentifier, orgIdentifier, projectIdentifier, sort, repoName, size, page, searchTerm, scopeInfo, null);
  }

  @Override
  public PageResponse<ServiceDashboardResponseDTO> getServicesList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> sort, String repoName, int size, int page, String searchTerm,
      ScopeInfo scopeInfo, ServiceFilterPropertiesDTO filterProperties) throws Exception {
    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId).is(accountIdentifier);

    applyScopeFiltersToCriteria(accountIdentifier, orgIdentifier, projectIdentifier, repoName, criteria, scopeInfo);

    if (filterProperties != null) {
      ServiceFilterHelper.applyFilterProperties(criteria, filterProperties);
    }

    // By default AI agent services are not included in the list of services unless the request asked for them, either
    // by naming their types explicitly or by asking for their category. Applying the exclusion on top of either filter
    // would also put a second condition on the same criteria key, which a Criteria rejects.
    if (filterProperties == null
        || (isEmpty(filterProperties.getServiceTypes()) && filterProperties.getCategory() == null)) {
      excludeAiServiceTypes(criteria);
    }

    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(ServiceEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ServiceEntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }

    Pageable pageRequest;
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<ServiceEntity> pageResponse;
    if (featureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_ENTITY_CRUD_RBAC)) {
      if (hasRequiredPermissionForAllServices(
              accountIdentifier, orgIdentifier, projectIdentifier, SERVICE_VIEW_PERMISSION)) {
        pageResponse = serviceEntityService.list(criteria, pageRequest);
      } else {
        pageResponse =
            serviceEntityService.getRBACFilteredServices(criteria, pageRequest, null, SERVICE_VIEW_PERMISSION, false);
      }
    } else {
      pageResponse = serviceEntityService.list(criteria, pageRequest);
    }

    List<ServiceEntity> services = pageResponse.getContent();

    return PageUtils.getNGPageResponse(pageResponse,
        getServiceDashboardDetails(accountIdentifier, orgIdentifier, projectIdentifier, services, scopeInfo));
  }

  private boolean hasRequiredPermissionForAllServices(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceRBACPermission) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(SERVICE, null), serviceRBACPermission);
  }

  List<ServiceDashboardResponseDTO> getServiceDashboardDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<ServiceEntity> services, ScopeInfo scopeInfo) {
    Map<String, Set<String>> serviceIdToDeploymentTypeMap = new HashMap<>();

    populateDeploymentTypeFromServiceEntity(
        serviceIdToDeploymentTypeMap, services, accountIdentifier, orgIdentifier, projectIdentifier);
    return services.stream()
        .map(service -> {
          final String serviceId = service.getIdentifier();
          return ServiceDashboardResponseDTO.builder()
              .service(scopeInfo != null ? ServiceElementMapper.writeDTO(service, scopeInfo)
                                         : ServiceElementMapper.writeDTO(service))
              .createdAt(service.getCreatedAt())
              .lastModifiedAt(service.getLastModifiedAt())
              .deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceId, null))
              .build();
        })
        .collect(Collectors.toList());
  }

  ServiceDetailsInfoDTOV2 getServiceDetailsInfoDTOV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<ServiceEntity> services, long previousStartTime,
      ScopeInfo scopeInfo) {
    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = getDashboardWorkloadDeploymentV2(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                    .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfoV2> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceRefs = getServiceRefs(accountIdentifier, orgIdentifier, projectIdentifier, services);
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipeline(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    populateDeploymentTypeFromServiceEntity(
        serviceIdToDeploymentTypeMap, services, accountIdentifier, orgIdentifier, projectIdentifier);

    Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap = getDeploymentIconMap(
        accountIdentifier, orgIdentifier, projectIdentifier, services, serviceIdToDeploymentTypeMap, scopeInfo);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTOV2> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOV2Builder serviceDetailsDTOBuilder = ServiceDetailsDTOV2.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentIconList(serviceIdToDeploymentIconMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceRef)) {
                final WorkloadDeploymentInfoV2 workloadDeploymentInfo =
                    serviceIdToWorkloadDeploymentInfo.get(serviceRef);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              } else {
                ChangeRate changeRate = calculateChangeRateV2(0, 0);
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(changeRate);
                serviceDetailsDTOBuilder.successRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.failureRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.frequencyChangeRate(changeRate);
              }

              serviceDetailsDTOBuilder.connectorRef(service.getConnectorRef());
              serviceDetailsDTOBuilder.storeType(service.getStoreType());
              serviceDetailsDTOBuilder.entityGitDetails(ServiceElementMapper.getEntityGitDetails(service));
              serviceDetailsDTOBuilder.fallbackBranch(service.getFallBackBranch());

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTOV2.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  ServiceDetailsInfoDTOV2 getServiceDetailsInfoDTOV2ViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<ServiceEntity> services, long previousStartTime,
      ScopeInfo scopeInfo) {
    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = getDashboardWorkloadDeploymentV2ViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                    .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfoV2> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceRefs = getServiceRefs(accountIdentifier, orgIdentifier, projectIdentifier, services);
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipelineViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap =
        getPipelineExecutionDetailsViaJooq(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentTypeViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, scopeInfo);

    populateDeploymentTypeFromServiceEntity(
        serviceIdToDeploymentTypeMap, services, accountIdentifier, orgIdentifier, projectIdentifier);

    Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap = getDeploymentIconMap(
        accountIdentifier, orgIdentifier, projectIdentifier, services, serviceIdToDeploymentTypeMap, scopeInfo);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTOV2> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOV2Builder serviceDetailsDTOBuilder = ServiceDetailsDTOV2.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentIconList(serviceIdToDeploymentIconMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceRef)) {
                final WorkloadDeploymentInfoV2 workloadDeploymentInfo =
                    serviceIdToWorkloadDeploymentInfo.get(serviceRef);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              } else {
                ChangeRate changeRate = calculateChangeRateV2(0, 0);
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(changeRate);
                serviceDetailsDTOBuilder.successRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.failureRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.frequencyChangeRate(changeRate);
              }

              serviceDetailsDTOBuilder.connectorRef(service.getConnectorRef());
              serviceDetailsDTOBuilder.storeType(service.getStoreType());
              serviceDetailsDTOBuilder.entityGitDetails(ServiceElementMapper.getEntityGitDetails(service));
              serviceDetailsDTOBuilder.fallbackBranch(service.getFallBackBranch());

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTOV2.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  @Override
  public Map<String, ServicePipelineInfo> getPipelineExecutionDetails(List<String> pipelineExecutionIdList) {
    return getPipelineExecutionDetailsInBatches(pipelineExecutionIdList, null);
  }

  public Map<String, ServicePipelineInfo> getPipelineExecutionDetails(
      List<String> pipelineExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql;
    if (EmptyPredicate.isNotEmpty(statusList)) {
      sql = "select * from " + tableNameCD + " where id = any (?) and status = any (?);";
    } else {
      sql = "select * from " + tableNameCD + " where id = any (?);";
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        if (EmptyPredicate.isNotEmpty(statusList)) {
          final Array statusArray = connection.createArrayOf("VARCHAR", statusList.toArray());
          statement.setArray(2, statusArray);
        }
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String pipelineName = resultSet.getString(NGPipelineSummaryCDConstants.NAME);
          String pipelineId = resultSet.getString(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          String planExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID);
          String deployedByName = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_NAME);
          String deployedById = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);

          long executionTime = Long.parseLong(resultSet.getString(NGPipelineSummaryCDConstants.START_TS));
          if (!pipelineExecutionDetailsMap.containsKey(pipelineExecutionId)) {
            pipelineExecutionDetailsMap.put(pipelineExecutionId,
                ServicePipelineInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
                    .build());
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return pipelineExecutionDetailsMap;
  }

  public Map<String, ServicePipelineInfo> getPipelineExecutionDetailsInBatches(
      List<String> pipelineExecutionIdList, List<String> statusList) {
    return executeInBatches(
        pipelineExecutionIdList, IN_QUERY_ARRAY_MAX_SIZE, subList -> getPipelineExecutionDetails(subList, statusList));
  }

  @Override
  public Map<String, ServicePipelineInfo> getPipelineExecutionDetailsViaJooq(List<String> pipelineExecutionIdList) {
    return getPipelineExecutionDetailsViaJooq(pipelineExecutionIdList, null);
  }

  public Map<String, ServicePipelineInfo> getPipelineExecutionDetailsViaJooq(
      List<String> pipelineExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    SelectConditionStep<Record> query = dslContext.select()
                                            .from(PIPELINE_EXECUTION_SUMMARY_CD)
                                            .where(PIPELINE_EXECUTION_SUMMARY_CD.ID.in(pipelineExecutionIdList));
    if (EmptyPredicate.isNotEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        query.fetchLazy().forEach(record -> {
          String pipelineExecutionId = record.get(NGPipelineSummaryCDConstants.ID, String.class);
          String pipelineName = record.get(NGPipelineSummaryCDConstants.NAME, String.class);
          String pipelineId = record.get(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER, String.class);
          String status = record.get(NGPipelineSummaryCDConstants.STATUS, String.class);
          String planExecutionId = record.get(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID, String.class);
          String deployedByName = record.get(NGPipelineSummaryCDConstants.AUTHOR_NAME, String.class);
          String deployedById = record.get(NGPipelineSummaryCDConstants.AUTHOR_ID, String.class);
          long executionTime = Long.parseLong(record.get(NGPipelineSummaryCDConstants.START_TS, String.class));
          if (!pipelineExecutionDetailsMap.containsKey(pipelineExecutionId)) {
            pipelineExecutionDetailsMap.put(pipelineExecutionId,
                ServicePipelineInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
                    .build());
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return pipelineExecutionDetailsMap;
  }

  @Override
  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfo(
      List<String> planExecutionIdList) {
    // This should not have to many ids in the list
    return getPipelineExecutionDetailsWithRevertInfoInBatches(planExecutionIdList, null);
  }

  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfo(
      List<String> planExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql;
    if (EmptyPredicate.isNotEmpty(statusList)) {
      sql = "select * from " + tableNameCD + " where planexecutionid = any (?) and status = any (?);";
    } else {
      sql = "select * from " + tableNameCD + " where planexecutionid = any (?);";
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", planExecutionIdList.toArray());
        statement.setArray(1, array);
        if (EmptyPredicate.isNotEmpty(statusList)) {
          final Array statusArray = connection.createArrayOf("VARCHAR", statusList.toArray());
          statement.setArray(2, statusArray);
        }
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String pipelineName = resultSet.getString(NGPipelineSummaryCDConstants.NAME);
          String pipelineId = resultSet.getString(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          String planExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID);
          boolean isRevertExecution = resultSet.getBoolean(NGPipelineSummaryCDConstants.REVERT_EXECUTION);
          String deployedByName = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_NAME);
          String deployedById = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);

          long executionTime = Long.parseLong(resultSet.getString(NGPipelineSummaryCDConstants.START_TS));
          if (!pipelineExecutionDetailsMap.containsKey(planExecutionId)) {
            pipelineExecutionDetailsMap.put(planExecutionId,
                ServicePipelineWithRevertInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
                    .isRevertExecution(isRevertExecution)
                    .build());
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return pipelineExecutionDetailsMap;
  }

  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfoInBatches(
      List<String> pipelineExecutionIdList, List<String> statusList) {
    return executeInBatches(pipelineExecutionIdList, IN_QUERY_ARRAY_MAX_SIZE,
        subList -> getPipelineExecutionDetailsWithRevertInfo(subList, statusList));
  }

  @Override
  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfoViaJooq(
      List<String> planExecutionIdList) {
    return getPipelineExecutionDetailsWithRevertInfoViaJooq(planExecutionIdList, null);
  }

  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfoViaJooq(
      List<String> planExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    SelectConditionStep<Record> query =
        dslContext.select()
            .from(PIPELINE_EXECUTION_SUMMARY_CD)
            .where(PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID.in(planExecutionIdList));
    if (EmptyPredicate.isNotEmpty(statusList)) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.in(statusList));
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query).forEach(record -> {
          String pipelineExecutionId = record.get(NGPipelineSummaryCDConstants.ID, String.class);
          String pipelineName = record.get(NGPipelineSummaryCDConstants.NAME, String.class);
          String pipelineId = record.get(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER, String.class);
          String status = record.get(NGPipelineSummaryCDConstants.STATUS, String.class);
          String planExecutionId = record.get(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID, String.class);
          boolean isRevertExecution = record.get(NGPipelineSummaryCDConstants.REVERT_EXECUTION, Boolean.class);
          String deployedByName = record.get(NGPipelineSummaryCDConstants.AUTHOR_NAME, String.class);
          String deployedById = record.get(NGPipelineSummaryCDConstants.AUTHOR_ID, String.class);
          long executionTime = Long.parseLong(record.get(NGPipelineSummaryCDConstants.START_TS, String.class));
          if (!pipelineExecutionDetailsMap.containsKey(planExecutionId)) {
            pipelineExecutionDetailsMap.put(planExecutionId,
                ServicePipelineWithRevertInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
                    .isRevertExecution(isRevertExecution)
                    .build());
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return pipelineExecutionDetailsMap;
  }

  @Override
  public Map<String, GitOpsStageMetadata> getGitOpsStageMetadataForRollback(List<String> planExecutionIds) {
    Map<String, GitOpsStageMetadata> stageMetadataMap = new HashMap<>();
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return stageMetadataMap;
    }
    int totalTries = 0;
    boolean successfulOperation = false;
    // Filter to SUCCEEDED only: non-successful stages are ineligible for rollback (checkIfRollbackAllowed
    // requires stageStatus == SUCCEEDED). Instances from failed stages stay un-enriched and are rejected
    // by the existing stageStatus check, avoiding unnecessary Timescale writes.
    // stage_execution.status stores the raw protobuf Status.name() (CDC copies it verbatim), so a successful
    // stage is 'SUCCEEDED', NOT the display 'SUCCESS'. Filtering 'SUCCESS' matched zero rows in real
    // environments. The read side (TimescaleStatusMapper.mapStageStatus) still normalizes to Status.SUCCEEDED.
    // Use stage_execution_id (the runtime node execution UUID), NOT stage_node_id (the static plan setup ID).
    // triggerPostExecutionRollback looks up NodeExecution documents by UUID, so passing the setup ID
    // results in zero matches and an IndexOutOfBoundsException in ExecutionSummaryCreateEventHandler.
    // DISTINCT ON (plan_execution_id, service_id, env_id): post-CDS-114264 a multi-service/multi-env GitOps
    // pipeline emits one stage per (service, env) under a single plan_execution_id, so the correct stage must
    // be resolved per (planExecution, service, env) — keying by plan alone would stamp the latest stage on
    // every service+env group in that pipeline.
    String sql = "SELECT DISTINCT ON (se.plan_execution_id, cse.service_id, cse.env_id)"
        + " se.plan_execution_id, cse.service_id, cse.env_id, se.stage_execution_id, se.status"
        + " FROM stage_execution se"
        + " JOIN cd_stage_execution cse ON se.id = cse.id"
        + " WHERE se.plan_execution_id = ANY (?)"
        + " AND cse.gitOpsEnabled = true"
        + " AND se.status = 'SUCCEEDED'"
        + " ORDER BY se.plan_execution_id, cse.service_id, cse.env_id, se.start_time DESC";

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setArray(1, connection.createArrayOf("VARCHAR", planExecutionIds.toArray()));
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String planExecutionId = resultSet.getString("plan_execution_id");
          String serviceId = resultSet.getString("service_id");
          String envId = resultSet.getString("env_id");
          String stageExecutionId = resultSet.getString("stage_execution_id");
          String status = resultSet.getString("status");
          stageMetadataMap.put(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId),
              GitOpsStageMetadata.builder()
                  .planExecutionId(planExecutionId)
                  .serviceId(serviceId)
                  .envId(envId)
                  .stageExecutionId(stageExecutionId)
                  .stageStatus(status)
                  .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return stageMetadataMap;
  }

  @Override
  public Map<String, GitOpsStageMetadata> getGitOpsStageMetadataForRollbackViaJooq(List<String> planExecutionIds) {
    Map<String, GitOpsStageMetadata> stageMetadataMap = new HashMap<>();
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return stageMetadataMap;
    }
    int totalTries = 0;
    boolean successfulOperation = false;

    Table<?> cdStageExecution = DSL.table("cd_stage_execution");
    Field<String> cseId = DSL.field("cd_stage_execution.id", String.class);
    Field<Boolean> gitOpsEnabled = DSL.field("cd_stage_execution.gitopsenabled", Boolean.class);
    Field<String> serviceIdField = DSL.field("cd_stage_execution.service_id", String.class);
    Field<String> envIdField = DSL.field("cd_stage_execution.env_id", String.class);
    Field<String> stageExecId = DSL.field("stage_execution.stage_execution_id", String.class);

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        // Use STAGE_EXECUTION_ID (runtime node execution UUID), not STAGE_NODE_ID (static plan setup ID).
        // triggerPostExecutionRollback needs the execution UUID to look up NodeExecution documents.
        // distinctOn (plan_execution_id, service_id, env_id): post-CDS-114264 one GitOps stage exists per
        // (service, env) under a single plan_execution_id, so resolve the correct stage per service+env group.
        dslContext
            .selectDistinct(
                STAGE_EXECUTION.PLAN_EXECUTION_ID, serviceIdField, envIdField, stageExecId, STAGE_EXECUTION.STATUS)
            .distinctOn(STAGE_EXECUTION.PLAN_EXECUTION_ID, serviceIdField, envIdField)
            .from(STAGE_EXECUTION)
            .join(cdStageExecution)
            .on(STAGE_EXECUTION.ID.eq(cseId))
            .where(STAGE_EXECUTION.PLAN_EXECUTION_ID.in(planExecutionIds))
            .and(gitOpsEnabled.eq(true))
            .and(STAGE_EXECUTION.STATUS.eq("SUCCEEDED"))
            .orderBy(STAGE_EXECUTION.PLAN_EXECUTION_ID, serviceIdField, envIdField, STAGE_EXECUTION.START_TIME.desc())
            .fetchLazy()
            .forEach(record -> {
              String planExecutionId = record.get(STAGE_EXECUTION.PLAN_EXECUTION_ID);
              String serviceId = record.get(serviceIdField);
              String envId = record.get(envIdField);
              stageMetadataMap.put(GitOpsStageMetadata.buildKey(planExecutionId, serviceId, envId),
                  GitOpsStageMetadata.builder()
                      .planExecutionId(planExecutionId)
                      .serviceId(serviceId)
                      .envId(envId)
                      .stageExecutionId(record.get(stageExecId))
                      .stageStatus(record.get(STAGE_EXECUTION.STATUS))
                      .build());
            });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return stageMetadataMap;
  }

  public Map<String, String> getPipelineExecutionStatusMap(List<String> pipelineExecutionIdList, String query) {
    Map<String, String> executionStatusMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          executionStatusMap.put(pipelineExecutionId, status);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return executionStatusMap;
  }

  protected Map<String, String> getPipelineExecutionStatusMapInBatches(
      List<String> pipelineExecutionIdList, String query) {
    return executeInBatches(
        pipelineExecutionIdList, IN_QUERY_ARRAY_MAX_SIZE, subList -> getPipelineExecutionStatusMap(subList, query));
  }

  public Map<String, String> getPipelineExecutionStatusMap(Query query) {
    Map<String, String> executionStatusMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String pipelineExecutionId = record.get(NGPipelineSummaryCDConstants.ID, String.class);
          String status = record.get(NGPipelineSummaryCDConstants.STATUS, String.class);
          executionStatusMap.put(pipelineExecutionId, status);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return executionStatusMap;
  }

  public List<String> getPipelineExecutionIdFromServiceInfraInfo(String query) {
    Set<String> ids = new HashSet<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    ResultSet resultSet = null;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          if (EmptyPredicate.isNotEmpty(id)) {
            ids.add(id);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return ids.stream().collect(Collectors.toList());
  }

  public List<String> getPipelineExecutionIdFromServiceInfraInfo(Query query) {
    Set<String> ids = new HashSet<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String id = record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class);
          if (EmptyPredicate.isNotEmpty(id)) {
            ids.add(id);
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return ids.stream().collect(Collectors.toList());
  }

  public Map<String, String> getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(String query) {
    Map<String, String> idToFailureInfoMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    ResultSet resultSet = null;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          String executionFailureDetails = resultSet.getString(EXECUTION_FAILURE_DETAILS);
          idToFailureInfoMap.put(id, executionFailureDetails);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return idToFailureInfoMap;
  }

  public Map<String, String> getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(Query query) {
    Map<String, String> idToFailureInfoMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String id = record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class);
          String executionFailureDetails = record.get(EXECUTION_FAILURE_DETAILS, String.class);
          idToFailureInfoMap.put(id, executionFailureDetails);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return idToFailureInfoMap;
  }

  public Map<String, Pair<String, AuthorInfo>> getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(
      List<String> pipelineExecutionIdList) {
    return processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(
        pipelineExecutionIdList, this::getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping);
  }

  public void getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(
      Collection<String> pipelineExecutionIdList, Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap) {
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql =
        "select id, moduleinfo_author_id, author_avatar, trigger_type from " + tableNameCD + " where id = any (?);";
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String authorId = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);
          String authorAvatar = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_AVATAR);
          String triggerType = resultSet.getString(NGPipelineSummaryCDConstants.TRIGGER_TYPE);
          if (!triggerAndAuthorInfoMap.containsKey(pipelineExecutionId)) {
            triggerAndAuthorInfoMap.put(pipelineExecutionId,
                new MutablePair<>(triggerType, AuthorInfo.builder().name(authorId).url(authorAvatar).build()));
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  public Map<String, Pair<String, AuthorInfo>> getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(
      List<String> pipelineExecutionIdList) {
    return processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(
        pipelineExecutionIdList, this::getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq);
  }

  public void getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(
      Collection<String> pipelineExecutionIdList, Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap) {
    int totalTries = 0;
    boolean successfulOperation = false;
    Query sql = dslContext
                    .select(PIPELINE_EXECUTION_SUMMARY_CD.ID.as(NGPipelineSummaryCDConstants.ID),
                        PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID.as(NGPipelineSummaryCDConstants.AUTHOR_ID),
                        PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR.as(NGPipelineSummaryCDConstants.AUTHOR_AVATAR),
                        PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE.as(NGPipelineSummaryCDConstants.TRIGGER_TYPE))
                    .from(PIPELINE_EXECUTION_SUMMARY_CD)
                    .where(PIPELINE_EXECUTION_SUMMARY_CD.ID.in(pipelineExecutionIdList));
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(sql.getSQL(), sql.getBindValues().toArray()).forEach(record -> {
          String pipelineExecutionId = record.get(NGPipelineSummaryCDConstants.ID, String.class);
          String authorId = record.get(NGPipelineSummaryCDConstants.AUTHOR_ID, String.class);
          String authorAvatar = record.get(NGPipelineSummaryCDConstants.AUTHOR_AVATAR, String.class);
          String triggerType = record.get(NGPipelineSummaryCDConstants.TRIGGER_TYPE, String.class);
          if (!triggerAndAuthorInfoMap.containsKey(pipelineExecutionId)) {
            triggerAndAuthorInfoMap.put(pipelineExecutionId,
                new MutablePair<>(triggerType, AuthorInfo.builder().name(authorId).url(authorAvatar).build()));
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
  }

  public Map<String, Pair<String, AuthorInfo>> getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(
      List<String> pipelineExecutionIdList) {
    return processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(
        pipelineExecutionIdList, this::getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated);
  }

  public void getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(
      Collection<String> pipelineExecutionIdList, Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap) {
    String sql =
        "select id, moduleinfo_author_id, author_avatar, trigger_type from " + tableNameCD + " where id = any (?);";
    TimescalePersistence queryExecutor = new TimescalePersistence(timeScaleDBService, dslContext);
    // Create a ModifyPreparedStatement using a lambda expression
    ModifyPreparedStatement modifyPreparedStatement = (preparedStatement, connection) -> {
      final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
      preparedStatement.setArray(1, array);
    };

    // Define a PaginatedQueryCallback using a lambda expression
    PaginatedQueryCallback callback = resultSet -> {
      String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
      String authorId = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);
      String authorAvatar = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_AVATAR);
      String triggerType = resultSet.getString(NGPipelineSummaryCDConstants.TRIGGER_TYPE);
      if (!triggerAndAuthorInfoMap.containsKey(pipelineExecutionId)) {
        triggerAndAuthorInfoMap.put(pipelineExecutionId,
            new MutablePair<>(triggerType, AuthorInfo.builder().name(authorId).url(authorAvatar).build()));
      }
    };
    queryExecutor.executePaginatedQuery(sql, BATCH_SIZE, MAX_RETRY_COUNT, callback, modifyPreparedStatement);
  }

  public Map<String, Pair<String, AuthorInfo>> getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(
      List<String> pipelineExecutionIdList) {
    return processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(
        pipelineExecutionIdList, this::getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq);
  }

  public void getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(
      Collection<String> pipelineExecutionIdList, Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap) {
    Query query = dslContext
                      .select(PIPELINE_EXECUTION_SUMMARY_CD.ID.as(NGPipelineSummaryCDConstants.ID),
                          PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID.as(NGPipelineSummaryCDConstants.AUTHOR_ID),
                          PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR.as(NGPipelineSummaryCDConstants.AUTHOR_AVATAR),
                          PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE.as(NGPipelineSummaryCDConstants.TRIGGER_TYPE))
                      .from(PIPELINE_EXECUTION_SUMMARY_CD)
                      .where(PIPELINE_EXECUTION_SUMMARY_CD.ID.in(pipelineExecutionIdList));
    TimescalePersistence queryExecutor = new TimescalePersistence(timeScaleDBService, dslContext);

    PaginatedQueryCallbackViaJooq callback = record -> {
      String pipelineExecutionId = record.get(NGPipelineSummaryCDConstants.ID, String.class);
      String authorId = record.get(NGPipelineSummaryCDConstants.AUTHOR_ID, String.class);
      String authorAvatar = record.get(NGPipelineSummaryCDConstants.AUTHOR_AVATAR, String.class);
      String triggerType = record.get(NGPipelineSummaryCDConstants.TRIGGER_TYPE, String.class);
      if (!triggerAndAuthorInfoMap.containsKey(pipelineExecutionId)) {
        triggerAndAuthorInfoMap.put(pipelineExecutionId,
            new MutablePair<>(triggerType, AuthorInfo.builder().name(authorId).url(authorAvatar).build()));
      }
    };
    queryExecutor.executePaginatedQuery(query, BATCH_SIZE, MAX_RETRY_COUNT, callback);
  }

  @Override
  public PipelineExecutionCountInfo getPipelineExecutionCountInfo(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, Long startInterval, Long endInterval, String artifactPath,
      String artifactVersion, String artifact, String status) {
    endInterval = DashboardServiceHelper.checkForDefaultEndInterval(endInterval);
    startInterval = DashboardServiceHelper.checkForDefaultStartInterval(startInterval, endInterval);
    if (!DashboardServiceHelper.validateDuration(startInterval, endInterval)) {
      throw new InvalidRequestException("startTime and endTime interval should be less than 6 months");
    }
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountIdentifier, orgIdentifier, projectIdentifier, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));

    String queryArtifactDetails = DashboardServiceHelper.queryToFetchExecutionIdAndArtifactDetails(
        serviceId, startInterval, endInterval, artifactPath, artifactVersion, artifact, parentUniqueIds);
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList =
        getExecutionIdAndArtifactDetails(queryArtifactDetails, parentUniqueIdsToScopeInfoMap);
    List<String> ids = new ArrayList<>(serviceArtifactExecutionDetailList.stream()
                                           .map(ServiceArtifactExecutionDetail::getPipelineExecutionSummaryCDId)
                                           .collect(Collectors.toSet()));
    String queryExecutionStatus = DashboardServiceHelper.queryToFetchStatusOfExecution(status, parentUniqueIds);
    Map<String, String> executionStatusMap = getPipelineExecutionStatusMapInBatches(ids, queryExecutionStatus);
    return DashboardServiceHelper.getPipelineExecutionCountInfoHelper(
        serviceArtifactExecutionDetailList, executionStatusMap);
  }

  @Override
  public PipelineExecutionCountInfo getPipelineExecutionCountInfoViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, Long startInterval, Long endInterval, String artifactPath,
      String artifactVersion, String artifact, String status) {
    endInterval = DashboardServiceHelper.checkForDefaultEndInterval(endInterval);
    startInterval = DashboardServiceHelper.checkForDefaultStartInterval(startInterval, endInterval);
    if (!DashboardServiceHelper.validateDuration(startInterval, endInterval)) {
      throw new InvalidRequestException("startTime and endTime interval should be less than 6 months");
    }
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountIdentifier, orgIdentifier, projectIdentifier, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));

    Query query = DashboardServiceHelper.queryToFetchExecutionIdAndArtifactDetailsViaJooq(serviceId, startInterval,
        endInterval, artifactPath, artifactVersion, artifact, dslContext.configuration(), parentUniqueIds);
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList =
        getExecutionIdAndArtifactDetails(query, parentUniqueIdsToScopeInfoMap);
    List<String> ids = new ArrayList<>(serviceArtifactExecutionDetailList.stream()
                                           .map(ServiceArtifactExecutionDetail::getPipelineExecutionSummaryCDId)
                                           .collect(Collectors.toSet()));
    Query queryExecutionStatus = DashboardServiceHelper.queryToFetchStatusOfExecutionViaJooq(
        status, ids, dslContext.configuration(), parentUniqueIds);
    Map<String, String> executionStatusMap = getPipelineExecutionStatusMap(queryExecutionStatus);
    return DashboardServiceHelper.getPipelineExecutionCountInfoHelper(
        serviceArtifactExecutionDetailList, executionStatusMap);
  }

  @Override
  public CustomSequenceDTO getCustomSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceSequence> serviceSequenceOptional =
        getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    CustomSequenceDTO defaultSequence = getSequenceDTO(
        getEnvironmentInstanceDetails(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, null, true));
    if (!serviceSequenceOptional.isPresent()) {
      return defaultSequence;
    }
    ServiceSequence serviceSequence = serviceSequenceOptional.get();
    if (isNull(serviceSequence.getCustomSequence())) {
      return defaultSequence;
    } else {
      CustomSequenceDTO customSequence = serviceSequence.getCustomSequence();
      return filterExtraAndDeletedCards(customSequence, defaultSequence);
    }
  }

  private Optional<ServiceSequence> getServiceSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    return serviceSequenceService.get(scopeInfo, serviceId);
  }

  @Override
  public CustomSequenceDTO getCustomSequenceViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceSequence> serviceSequenceOptional =
        getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    CustomSequenceDTO defaultSequence = getSequenceDTO(getEnvironmentInstanceDetailsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, null, true));
    if (!serviceSequenceOptional.isPresent()) {
      return defaultSequence;
    }
    ServiceSequence serviceSequence = serviceSequenceOptional.get();
    if (isNull(serviceSequence.getCustomSequence())) {
      return defaultSequence;
    } else {
      CustomSequenceDTO customSequence = serviceSequence.getCustomSequence();
      return filterExtraAndDeletedCards(customSequence, defaultSequence);
    }
  }

  private CustomSequenceDTO filterExtraAndDeletedCards(
      CustomSequenceDTO customSequence, CustomSequenceDTO defaultSequence) {
    Set<String> defaultKeys = new HashSet<>();
    Set<String> customKeys = new HashSet<>();
    List<CustomSequenceDTO.EnvAndEnvGroupCard> newEnvAndEnvGroupCardList = new ArrayList<>();
    List<CustomSequenceDTO.EnvAndEnvGroupCard> appendEnvAndEnvGroupCardList = new ArrayList<>();

    defaultSequence.getEnvAndEnvGroupCardList().forEach(
        card -> defaultKeys.add(card.getIdentifier() + card.isEnvGroup()));
    customSequence.getEnvAndEnvGroupCardList().forEach(
        card -> customKeys.add(card.getIdentifier() + card.isEnvGroup()));

    customSequence.getEnvAndEnvGroupCardList().forEach(card -> {
      if (defaultKeys.contains(card.getIdentifier() + card.isEnvGroup())) {
        newEnvAndEnvGroupCardList.add(card);
      }
    });

    defaultSequence.getEnvAndEnvGroupCardList().forEach(card -> {
      if (!customKeys.contains(card.getIdentifier() + card.isEnvGroup())) {
        card.setNew(true);
        appendEnvAndEnvGroupCardList.add(card);
      }
    });

    appendEnvAndEnvGroupCardList.addAll(newEnvAndEnvGroupCardList);
    return CustomSequenceDTO.builder().envAndEnvGroupCardList(appendEnvAndEnvGroupCardList).build();
  }

  @Override
  public CustomSequenceDTO getDefaultSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    CustomSequenceDTO sequenceDTO = getSequenceDTO(
        getEnvironmentInstanceDetails(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, null, true));

    Optional<ServiceSequence> serviceSequenceOptional =
        getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    if (!serviceSequenceOptional.isPresent()) {
      return sequenceDTO;
    }
    CustomSequenceDTO customSequenceDTO = serviceSequenceOptional.get().getCustomSequence();

    if (isNull(customSequenceDTO)) {
      return sequenceDTO;
    }
    Map<String, Boolean> cardToIsNew = new HashMap<>();
    customSequenceDTO.getEnvAndEnvGroupCardList().forEach(
        card -> cardToIsNew.put(card.getIdentifier() + card.isEnvGroup(), card.isNew()));
    sequenceDTO.getEnvAndEnvGroupCardList().forEach(
        card -> card.setNew(cardToIsNew.getOrDefault(card.getIdentifier() + card.isEnvGroup(), false)));
    return sequenceDTO;
  }

  @Override
  public CustomSequenceDTO getDefaultSequenceViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    CustomSequenceDTO sequenceDTO = getSequenceDTO(getEnvironmentInstanceDetailsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, null, true));

    Optional<ServiceSequence> serviceSequenceOptional =
        getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    if (!serviceSequenceOptional.isPresent()) {
      return sequenceDTO;
    }
    CustomSequenceDTO customSequenceDTO = serviceSequenceOptional.get().getCustomSequence();

    if (isNull(customSequenceDTO)) {
      return sequenceDTO;
    }
    Map<String, Boolean> cardToIsNew = new HashMap<>();
    customSequenceDTO.getEnvAndEnvGroupCardList().forEach(
        card -> cardToIsNew.put(card.getIdentifier() + card.isEnvGroup(), card.isNew()));
    sequenceDTO.getEnvAndEnvGroupCardList().forEach(
        card -> card.setNew(cardToIsNew.getOrDefault(card.getIdentifier() + card.isEnvGroup(), false)));
    return sequenceDTO;
  }

  @Override
  public ServiceSequence useCustomSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, boolean useCustomSequence) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceSequence serviceSequence = ServiceSequence.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .parentUniqueId(scopeInfo.getUniqueId())
                                          .uniqueId(generateUuid())
                                          .serviceIdentifier(serviceId)
                                          .shouldUseCustomSequence(useCustomSequence)
                                          .build();
    return serviceSequenceService.upsertSequence(serviceSequence, scopeInfo);
  }

  @Override
  public SequenceToggleDTO useCustomSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceSequence> serviceSequenceOptional =
        getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    if (!serviceSequenceOptional.isPresent()) {
      return SequenceToggleDTO.builder().shouldUseCustomSequence(false).isNullCustomSequence(true).build();
    }
    return SequenceToggleDTO.builder()
        .shouldUseCustomSequence(serviceSequenceOptional.get().isShouldUseCustomSequence())
        .isNullCustomSequence(isNull(serviceSequenceOptional.get().getCustomSequence()))
        .build();
  }

  @Override
  public DeploymentsSummaryInfo getDeploymentsSummaryInfo(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    List<String> parentUniqueIds = null;
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return getDeploymentsSummaryInfoInternalViaJooq(
          accountId, orgId, projectId, startInterval, endInterval, parentUniqueIds);
    } else {
      return getDeploymentsSummaryInfoInternal(
          accountId, orgId, projectId, startInterval, endInterval, parentUniqueIds);
    }
  }

  @Override
  public DeploymentsSummaryPercentageInfo getFailuresDeploymentsSummaryInfo(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    List<String> parentUniqueIds = null;
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountId, orgId, projectId);
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return getFailedDeploymentsSummaryInfoInternalViaJooq(
          accountId, orgId, projectId, startInterval, endInterval, parentUniqueIds);
    } else {
      return getFailedDeploymentsSummaryInfoInternal(
          accountId, orgId, projectId, startInterval, endInterval, parentUniqueIds);
    }
  }

  private DeploymentsSummaryInfo getDeploymentsSummaryInfoInternal(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, List<String> parentUniqueIds) {
    String query = queryBuilderSelectEnvTypeTime(startInterval, endInterval, false, parentUniqueIds);

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> prod = new HashMap<>();
    HashMap<Long, Integer> nonProd = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      prod.put(startDateCopy, 0);
      nonProd.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndEnvTypeDeployment timeAndEnvTypeDeployment = queryCalculatorTimeAndEnvType(query, false);
    List<Long> time = timeAndEnvTypeDeployment.getTime();
    List<String> type = timeAndEnvTypeDeployment.getEnvType();

    List<DeploymentsSummary> deploymentsSummaries = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if ("Production".equals(type.get(i))) {
        prod.put(currentTimeEpoch, prod.get(currentTimeEpoch) + 1);
      } else if ("PreProduction".contains(type.get(i))) {
        nonProd.put(currentTimeEpoch, nonProd.get(currentTimeEpoch) + 1);
      }
    }

    startDateCopy = startInterval;

    while (startDateCopy < endDateCopy) {
      deploymentsSummaries.add(getDeploymentsSummary(
          startDateCopy, totalCountMap.get(startDateCopy), prod.get(startDateCopy), nonProd.get(startDateCopy)));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return DeploymentsSummaryInfo.builder().deploymentsSummaries(deploymentsSummaries).build();
  }

  private DeploymentsSummaryInfo getDeploymentsSummaryInfoInternalViaJooq(String accountId, String orgId,
      String projectId, long startInterval, long endInterval, List<String> parentUniqueIds) {
    Query query = queryBuilderSelectEnvTypeTimeViaJooq(startInterval, endInterval, false, parentUniqueIds);

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> prod = new HashMap<>();
    HashMap<Long, Integer> nonProd = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      prod.put(startDateCopy, 0);
      nonProd.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndEnvTypeDeployment timeAndEnvTypeDeployment = queryCalculatorTimeAndEnvTypeViaJooq(query, false);
    List<Long> time = timeAndEnvTypeDeployment.getTime();
    List<String> type = timeAndEnvTypeDeployment.getEnvType();

    List<DeploymentsSummary> deploymentsSummaries = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (EnvironmentType.Production.name().equals(type.get(i))) {
        prod.put(currentTimeEpoch, prod.get(currentTimeEpoch) + 1);
      } else if (EnvironmentType.PreProduction.name().contains(type.get(i))) {
        nonProd.put(currentTimeEpoch, nonProd.get(currentTimeEpoch) + 1);
      }
    }

    startDateCopy = startInterval;

    while (startDateCopy < endDateCopy) {
      deploymentsSummaries.add(getDeploymentsSummary(
          startDateCopy, totalCountMap.get(startDateCopy), prod.get(startDateCopy), nonProd.get(startDateCopy)));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return DeploymentsSummaryInfo.builder().deploymentsSummaries(deploymentsSummaries).build();
  }

  private DeploymentsSummaryPercentageInfo getFailedDeploymentsSummaryInfoInternal(String accountId, String orgId,
      String projectId, long startInterval, long endInterval, List<String> parentUniqueIds) {
    String query = queryBuilderSelectEnvTypeTime(startInterval, endInterval, true, parentUniqueIds);

    HashMap<Long, Integer> totalFailedCountMap = new HashMap<>();
    HashMap<Long, Integer> failedProd = new HashMap<>();
    HashMap<Long, Integer> failedNonProd = new HashMap<>();

    HashMap<Long, Integer> totalCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalFailedCountMap.put(startDateCopy, 0);
      failedProd.put(startDateCopy, 0);
      failedNonProd.put(startDateCopy, 0);
      totalCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndEnvTypeDeployment timeAndEnvTypeDeployment = queryCalculatorTimeAndEnvType(query, true);
    List<Long> time = timeAndEnvTypeDeployment.getTime();
    List<String> type = timeAndEnvTypeDeployment.getEnvType();
    List<String> status = timeAndEnvTypeDeployment.getStatus();

    List<DeploymentsSummaryPercentage> deploymentsSummaries = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (!"SUCCESS".equals(status.get(i))) {
        totalFailedCountMap.put(currentTimeEpoch, totalFailedCountMap.get(currentTimeEpoch) + 1);
        if ("Production".equals(type.get(i))) {
          failedProd.put(currentTimeEpoch, failedProd.get(currentTimeEpoch) + 1);
        } else if ("PreProduction".contains(type.get(i))) {
          failedNonProd.put(currentTimeEpoch, failedNonProd.get(currentTimeEpoch) + 1);
        }
      }
    }

    startDateCopy = startInterval;

    while (startDateCopy < endDateCopy) {
      double failedProdPercentage = getPercentage(totalCountMap.get(startDateCopy), failedProd.get(startDateCopy));
      double failedNonProdPercentage =
          getPercentage(totalCountMap.get(startDateCopy), failedNonProd.get(startDateCopy));
      double failedTotalPercentage = failedProdPercentage + failedNonProdPercentage;

      deploymentsSummaries.add(getDeploymentsSummaryPercentage(
          startDateCopy, failedTotalPercentage, failedProdPercentage, failedNonProdPercentage));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return DeploymentsSummaryPercentageInfo.builder().deploymentsSummaries(deploymentsSummaries).build();
  }

  private DeploymentsSummaryPercentageInfo getFailedDeploymentsSummaryInfoInternalViaJooq(String accountId,
      String orgId, String projectId, long startInterval, long endInterval, List<String> parentUniqueIds) {
    Query query = queryBuilderSelectEnvTypeTimeViaJooq(startInterval, endInterval, true, parentUniqueIds);

    HashMap<Long, Integer> totalFailedCountMap = new HashMap<>();
    HashMap<Long, Integer> failedProd = new HashMap<>();
    HashMap<Long, Integer> failedNonProd = new HashMap<>();

    HashMap<Long, Integer> totalCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalFailedCountMap.put(startDateCopy, 0);
      failedProd.put(startDateCopy, 0);
      failedNonProd.put(startDateCopy, 0);
      totalCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndEnvTypeDeployment timeAndEnvTypeDeployment = queryCalculatorTimeAndEnvTypeViaJooq(query, true);
    List<Long> time = timeAndEnvTypeDeployment.getTime();
    List<String> type = timeAndEnvTypeDeployment.getEnvType();
    List<String> status = timeAndEnvTypeDeployment.getStatus();

    List<DeploymentsSummaryPercentage> deploymentsSummaries = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (!"SUCCESS".equals(status.get(i))) {
        totalFailedCountMap.put(currentTimeEpoch, totalFailedCountMap.get(currentTimeEpoch) + 1);
        if (EnvironmentType.Production.name().equals(type.get(i))) {
          failedProd.put(currentTimeEpoch, failedProd.get(currentTimeEpoch) + 1);
        } else if (EnvironmentType.PreProduction.name().contains(type.get(i))) {
          failedNonProd.put(currentTimeEpoch, failedNonProd.get(currentTimeEpoch) + 1);
        }
      }
    }

    startDateCopy = startInterval;

    while (startDateCopy < endDateCopy) {
      double failedProdPercentage = getPercentage(totalCountMap.get(startDateCopy), failedProd.get(startDateCopy));
      double failedNonProdPercentage =
          getPercentage(totalCountMap.get(startDateCopy), failedNonProd.get(startDateCopy));
      double failedTotalPercentage = failedProdPercentage + failedNonProdPercentage;

      deploymentsSummaries.add(getDeploymentsSummaryPercentage(
          startDateCopy, failedTotalPercentage, failedProdPercentage, failedNonProdPercentage));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return DeploymentsSummaryPercentageInfo.builder().deploymentsSummaries(deploymentsSummaries).build();
  }

  private double getPercentage(Integer total, Integer subset) {
    if (total != 0) {
      double percentage = (double) subset / ((double) total / 100);
      return BigDecimal.valueOf(percentage).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    return 0;
  }

  @Override
  public ServiceSequence saveCustomSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, CustomSequenceDTO customSequenceDTO) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceSequence serviceSequence = ServiceSequence.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .parentUniqueId(scopeInfo.getUniqueId())
                                          .uniqueId(generateUuid())
                                          .customSequence(customSequenceDTO)
                                          .serviceIdentifier(serviceId)
                                          .shouldUseCustomSequence(true)
                                          .build();
    return serviceSequenceService.upsertCustomSequence(serviceSequence, scopeInfo);
  }

  public List<ServiceArtifactExecutionDetail> getExecutionIdAndArtifactDetails(
      String query, Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap) {
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = new ArrayList<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          serviceArtifactExecutionDetailList.add(
              ServiceArtifactExecutionDetail.builder()
                  .artifactPath(resultSet.getString(ARTIFACT_IMAGE))
                  .artifactTag(resultSet.getString(TAG))
                  .artifactDisplayName(resultSet.getString(ARTIFACT_DISPLAY_NAME))
                  .pipelineExecutionSummaryCDId(resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID))
                  .accountId(resultSet.getString(ACCOUNT_ID))
                  .orgId(parentUniqueIdsToScopeInfoMap.get(resultSet.getString(PARENT_UNIQUE_ID)).getOrgIdentifier())
                  .projectId(
                      parentUniqueIdsToScopeInfoMap.get(resultSet.getString(PARENT_UNIQUE_ID)).getProjectIdentifier())
                  .serviceRef(resultSet.getString(SERVICE_ID))
                  .serviceName(resultSet.getString(SERVICE_NAME))
                  .serviceStartTime(resultSet.getLong(SERVICE_STARTTS))
                  .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return serviceArtifactExecutionDetailList;
  }

  public List<ServiceArtifactExecutionDetail> getExecutionIdAndArtifactDetails(
      Query query, Map<String, ScopeInfo> parentUniqueIdToScopeInfoMap) {
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = new ArrayList<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          serviceArtifactExecutionDetailList.add(
              ServiceArtifactExecutionDetail.builder()
                  .artifactPath(record.get(ARTIFACT_IMAGE, String.class))
                  .artifactTag(record.get(TAG, String.class))
                  .artifactDisplayName(record.get(ARTIFACT_DISPLAY_NAME, String.class))
                  .pipelineExecutionSummaryCDId(record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class))
                  .accountId(record.get(ACCOUNT_ID, String.class))
                  .orgId(
                      parentUniqueIdToScopeInfoMap.get(record.get(PARENT_UNIQUE_ID, String.class)).getOrgIdentifier())
                  .projectId(parentUniqueIdToScopeInfoMap.get(record.get(PARENT_UNIQUE_ID, String.class))
                                 .getProjectIdentifier())
                  .serviceRef(record.get(SERVICE_ID, String.class))
                  .serviceName(record.get(SERVICE_NAME, String.class))
                  .serviceStartTime(record.get(SERVICE_STARTTS, Long.class))
                  .build());
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return serviceArtifactExecutionDetailList;
  }

  @Override
  public ServiceDeploymentInfoDTO getServiceDeployments(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    String query = queryBuilderServiceDeployments(accountIdentifier, orgIdentifier, projectIdentifier, startTime,
        endTime, bucketSizeInDays, serviceRef, parentUniqueIds);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, io.harness.ng.overview.dto.ServiceDeployment> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMap(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String status = resultSet.getString(NGServiceConstants.STATUS);
          long bucketTime = Long.parseLong(resultSet.getString(NGServiceConstants.TIME_ENTITY));
          long numberOfRecords = resultSet.getLong(NGServiceConstants.NUMBER_OF_RECORDS);
          io.harness.ng.overview.dto.ServiceDeployment serviceDeployment = resultMap.get(bucketTime);
          io.harness.ng.overview.dto.DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeploymentList =
        resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTO.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  @Override
  public ServiceDeploymentInfoDTO getServiceDeploymentsViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    SelectHavingStep<Record3<String, Long, Integer>> query = queryBuilderServiceDeploymentsViaJooq(accountIdentifier,
        orgIdentifier, projectIdentifier, startTime, endTime, bucketSizeInDays, serviceRef, parentUniqueIds);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, ServiceDeployment> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMap(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String status = record.get(NGServiceConstants.STATUS, String.class);
          long bucketTime = record.get(NGServiceConstants.TIME_ENTITY, Long.class);
          long numberOfRecords = record.get(NGServiceConstants.NUMBER_OF_RECORDS, Integer.class);
          ServiceDeployment serviceDeployment = resultMap.get(bucketTime);
          DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    List<ServiceDeployment> serviceDeploymentList = resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTO.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  @Override
  public ServiceDeploymentInfoDTOV2 getServiceDeploymentsV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays,
      List<String> parentUniqueIds) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    String query = queryBuilderServiceDeployments(accountIdentifier, orgIdentifier, projectIdentifier, startTime,
        endTime, bucketSizeInDays, serviceRef, parentUniqueIds);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, ServiceDeploymentV2> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMapV2(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String status = resultSet.getString(NGServiceConstants.STATUS);
          long bucketTime = Long.parseLong(resultSet.getString(NGServiceConstants.TIME_ENTITY));
          long numberOfRecords = resultSet.getLong(NGServiceConstants.NUMBER_OF_RECORDS);
          ServiceDeploymentV2 serviceDeployment = resultMap.get(bucketTime);
          DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    List<ServiceDeploymentV2> serviceDeploymentList = resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  @Override
  public ServiceDeploymentInfoDTOV2 getServiceDeploymentsV2ViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays,
      List<String> parentUniqueIds) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    SelectHavingStep<Record3<String, Long, Integer>> query = queryBuilderServiceDeploymentsViaJooq(accountIdentifier,
        orgIdentifier, projectIdentifier, startTime, endTime, bucketSizeInDays, serviceRef, parentUniqueIds);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, ServiceDeploymentV2> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMapV2(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String status = record.get(NGServiceConstants.STATUS, String.class);
          long bucketTime = record.get(NGServiceConstants.TIME_ENTITY, Long.class);
          long numberOfRecords = record.get(NGServiceConstants.NUMBER_OF_RECORDS, Integer.class);
          ServiceDeploymentV2 serviceDeployment = resultMap.get(bucketTime);
          DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    List<ServiceDeploymentV2> serviceDeploymentList = resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  // TODO: this method can be deprecated, adding this for the secondary timescaleDB
  //  Once all the queries have been moved to secondary this can be removed and the getServiceDeploymentsV2 can be used
  @Override
  public ServiceDeploymentInfoDTOV2 getServiceDeploymentsV3(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays,
      List<String> parentUniqueIds) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    String query = queryBuilderServiceDeployments(accountIdentifier, orgIdentifier, projectIdentifier, startTime,
        endTime, bucketSizeInDays, serviceRef, parentUniqueIds);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, ServiceDeploymentV2> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMapV2(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = secondaryTimeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String status = resultSet.getString(NGServiceConstants.STATUS);
          long bucketTime = Long.parseLong(resultSet.getString(NGServiceConstants.TIME_ENTITY));
          long numberOfRecords = resultSet.getLong(NGServiceConstants.NUMBER_OF_RECORDS);
          ServiceDeploymentV2 serviceDeployment = resultMap.get(bucketTime);
          DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    List<ServiceDeploymentV2> serviceDeploymentList = resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  private void initializeResultMap(Map<Long, io.harness.ng.overview.dto.ServiceDeployment> resultMap, long startTime,
      long endTime, long bucketSizeInDays) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    while (startTime < endTime) {
      resultMap.put(startTime,
          io.harness.ng.overview.dto.ServiceDeployment.builder()
              .time(startTime)
              .deployments(io.harness.ng.overview.dto.DeploymentCount.builder().total(0).failure(0).success(0).build())
              .rate(DeploymentChangeRates.builder()
                        .frequency(0)
                        .frequencyChangeRate(0)
                        .failureRate(0)
                        .failureRateChangeRate(0)
                        .build())
              .build());
      startTime = startTime + bucketSizeInMS;
    }
  }

  private void initializeResultMapV2(
      Map<Long, ServiceDeploymentV2> resultMap, long startTime, long endTime, long bucketSizeInDays) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    while (startTime < endTime) {
      resultMap.put(startTime,
          ServiceDeploymentV2.builder()
              .time(startTime)
              .deployments(io.harness.ng.overview.dto.DeploymentCount.builder().total(0).failure(0).success(0).build())
              .rate(DeploymentChangeRatesV2.builder()
                        .frequency(0)
                        .frequencyChangeRate(new ChangeRate(Double.valueOf(0)))
                        .failureRate(0)
                        .failureRateChangeRate(new ChangeRate(Double.valueOf(0)))
                        .build())
              .build());
      startTime = startTime + bucketSizeInMS;
    }
  }

  /**
   * select status, time_entity, count(*) as records from (
   * select service_status as status, service_startts as
   * execution_time, time_bucket_gapfill(86400000, service_startts, 1638403200000, 1654128000000) as time_entity,
   * pipeline_execution_summary_cd_id  from
   * service_infra_info as sii,
   * pipeline_execution_summary_cd as pesi
   * where pesi.accountid='ZVJHx0NyT9SciszZ0JQtFQ' and pesi.orgidentifier='PX' and
   * pesi.projectidentifier='horizonttdmetricscollector' and service_id='horzondeploymentmetrics'
   * and pesi.id=sii.pipeline_execution_summary_cd_id
   * and sii. service_startts >= 1652054400000 and sii.service_startts < 1654646400000
   * ) as service where status != ''
   * group by status, time_entity;
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param startTime
   * @param endTime
   * @param bucketSizeInDays
   * @param serviceIdentifier
   * @param parentUniqueIds
   * @return
   */
  public String queryBuilderServiceDeployments(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long startTime, long endTime, long bucketSizeInDays, String serviceIdentifier, List<String> parentUniqueIds) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    String selectQuery = "select status, time_entity, COUNT(*) as numberOfRecords from (select service_status as "
        + "status, service_startts as execution_time, ";
    totalBuildSqlBuilder.append(selectQuery)
        .append(String.format(
            "harness_date_bin_ng_mgr(%s, service_startts) as time_entity, pipeline_execution_summary_cd_id  from "
                + "service_infra_info as sii, pipeline_execution_summary_cd as pesi where pesi.accountid = "
                + "sii.accountid "
                + "AND sii.service_id is not null and ",
            bucketSizeInMS))
        .append(String.format("pesi.parent_unique_id in ('%s')",
            String.join(
                "','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new))));
    if (accountIdentifier != null) {
      totalBuildSqlBuilder.append(
          String.format(" and pesi.accountid='%s'", DashboardServiceHelper.escapeSql(accountIdentifier)));
    }
    if (serviceIdentifier != null) {
      totalBuildSqlBuilder.append(
          String.format(" and sii.service_id='%s'", DashboardServiceHelper.escapeSql(serviceIdentifier)));
    }

    totalBuildSqlBuilder.append(String.format(
        " and pesi.id=sii.pipeline_execution_summary_cd_id and sii.service_startts>=%s and sii.service_startts<%s) as "
            + "service where status != '' group by status, time_entity;",
        startTime, endTime));

    return totalBuildSqlBuilder.toString();
  }

  public SelectHavingStep<Record3<String, Long, Integer>> queryBuilderServiceDeploymentsViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long startTime, long endTime,
      long bucketSizeInDays, String serviceIdentifier, List<String> parentUniqueIds) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;

    Field<Long> timeEntity =
        function("harness_date_bin_ng_mgr", SQLDataType.BIGINT, val(bucketSizeInMS), SERVICE_INFRA_INFO.SERVICE_STARTTS)
            .as("time_entity");

    SelectConditionStep<Record4<String, Long, Long, String>> baseQuery =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_STATUS.as("status"),
                SERVICE_INFRA_INFO.SERVICE_STARTTS.as("execution_time"), timeEntity,
                SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
            .from(SERVICE_INFRA_INFO.as("sii"))
            .join(PIPELINE_EXECUTION_SUMMARY_CD.as("pesi"))
            .on(field("pesi.accountid").eq(field("sii.accountid")))
            .where(field("sii.service_id").isNotNull())
            .and(field("pesi.id").eq(field("sii.pipeline_execution_summary_cd_id")))
            .and(field("sii.service_startts").ge(val(startTime)))
            .and(field("sii.service_startts").lt(val(endTime)));
    baseQuery.and(field("pesi.parent_unique_id").in(parentUniqueIds));
    if (accountIdentifier != null) {
      baseQuery.and(field("pesi.accountid").eq(val(accountIdentifier)));
    }

    if (serviceIdentifier != null) {
      baseQuery.and(field("sii.service_id").eq(val(serviceIdentifier)));
    }

    var derivedTable = baseQuery.asTable("service");
    return DSL.select(field("status", String.class), field("time_entity", Long.class), count().as("numberOfRecords"))
        .from(derivedTable)
        .where(field("status").isNotNull().and(field("status").ne("")))
        .groupBy(field("status"), timeEntity);
  }

  private static void validateBucketSize(long numberOfDays, long bucketSizeInDays) throws Exception {
    if (numberOfDays < bucketSizeInDays) {
      throw new Exception("Bucket size should be less than the number of days in the selected time range");
    }
  }

  private void calculateRates(List<ServiceDeployment> serviceDeployments) {
    serviceDeployments.sort(Comparator.comparingLong(ServiceDeployment::getTime));

    double prevFrequency = 0, prevFailureRate = 0;
    for (int i = 0; i < serviceDeployments.size(); i++) {
      DeploymentCount deployments = serviceDeployments.get(i).getDeployments();
      DeploymentChangeRates rates = serviceDeployments.get(i).getRate();

      double currFrequency = deployments.getTotal();
      rates.setFrequency(currFrequency);
      rates.setFrequencyChangeRate(calculateChangeRate(prevFrequency, currFrequency));
      prevFrequency = currFrequency;

      double failureRate = deployments.getFailure() * 100;
      if (deployments.getTotal() != 0) {
        failureRate = failureRate / deployments.getTotal();
      }
      rates.setFailureRate(failureRate);
      rates.setFailureRateChangeRate(calculateChangeRate(prevFailureRate, failureRate));
      prevFailureRate = failureRate;
    }
  }

  private void calculateRatesV2(List<ServiceDeploymentV2> serviceDeployments) {
    serviceDeployments.sort(Comparator.comparingLong(ServiceDeploymentV2::getTime));

    double prevFrequency = 0, prevFailureRate = 0;
    for (int i = 0; i < serviceDeployments.size(); i++) {
      DeploymentCount deployments = serviceDeployments.get(i).getDeployments();
      DeploymentChangeRatesV2 rates = serviceDeployments.get(i).getRate();

      double currFrequency = deployments.getTotal();
      rates.setFrequency(currFrequency);
      rates.setFrequencyChangeRate(calculateChangeRateV2(prevFrequency, currFrequency));
      prevFrequency = currFrequency;

      double failureRate = deployments.getFailure() * 100;
      if (deployments.getTotal() != 0) {
        failureRate = failureRate / deployments.getTotal();
      }
      rates.setFailureRate(failureRate);
      rates.setFailureRateChangeRate(calculateChangeRateV2(prevFailureRate, failureRate));
      prevFailureRate = failureRate;
    }
  }

  @Override
  public io.harness.ng.overview.dto.ServiceDeploymentListInfo getServiceDeploymentsInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTime, long endTime, String serviceIdentifier,
      long bucketSizeInDays) throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ServiceDeploymentInfoDTO serviceDeployments = getServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays);
    List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeploymentList =
        serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTO prevServiceDeployment = getServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays);
    List<io.harness.ng.overview.dto.ServiceDeployment> prevServiceDeploymentList =
        prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeployments(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeployments(prevServiceDeploymentList);
    double failureRate = getFailureRate(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    double totalDeploymentChangeRate = calculateChangeRate(prevTotalDeployments, totalDeployments);
    double failureRateChangeRate = getFailureRateChangeRate(serviceDeploymentList, prevServiceDeploymentList);
    double frequencyChangeRate = calculateChangeRate(prevFrequency, frequency);

    calculateRates(serviceDeploymentList);

    return ServiceDeploymentListInfo.builder()
        .startTime(startTime)
        .endTime(endTime == -1 ? null : endTime)
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  @Override
  public ServiceDeploymentListInfo getServiceDeploymentsInfoViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ServiceDeploymentInfoDTO serviceDeployments = getServiceDeploymentsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays);
    List<ServiceDeployment> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTO prevServiceDeployment = getServiceDeploymentsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays);
    List<ServiceDeployment> prevServiceDeploymentList = prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeployments(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeployments(prevServiceDeploymentList);
    double failureRate = getFailureRate(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    double totalDeploymentChangeRate = calculateChangeRate(prevTotalDeployments, totalDeployments);
    double failureRateChangeRate = getFailureRateChangeRate(serviceDeploymentList, prevServiceDeploymentList);
    double frequencyChangeRate = calculateChangeRate(prevFrequency, frequency);

    calculateRates(serviceDeploymentList);

    return ServiceDeploymentListInfo.builder()
        .startTime(startTime)
        .endTime(endTime == -1 ? null : endTime)
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  @Override
  public ServiceDeploymentListInfoV2 getServiceDeploymentsInfoV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    ServiceDeploymentInfoDTOV2 serviceDeployments = getServiceDeploymentsV2(accountIdentifier, orgIdentifier,
        projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTOV2 prevServiceDeployment = getServiceDeploymentsV2(accountIdentifier, orgIdentifier,
        projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> prevServiceDeploymentList = prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeploymentsV2(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeploymentsV2(prevServiceDeploymentList);
    double failureRate = getFailureRateV2(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployments, totalDeployments);
    ChangeRate failureRateChangeRate = getFailureRateChangeRateV2(serviceDeploymentList, prevServiceDeploymentList);
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);

    calculateRatesV2(serviceDeploymentList);

    return ServiceDeploymentListInfoV2.builder()
        .startTime(startTime)
        .endTime(endTime == -1 ? null : endTime)
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  @Override
  public ServiceDeploymentListInfoV2 getServiceDeploymentsInfoV2ViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    ServiceDeploymentInfoDTOV2 serviceDeployments = getServiceDeploymentsV2ViaJooq(accountIdentifier, orgIdentifier,
        projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTOV2 prevServiceDeployment = getServiceDeploymentsV2ViaJooq(accountIdentifier, orgIdentifier,
        projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> prevServiceDeploymentList = prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeploymentsV2(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeploymentsV2(prevServiceDeploymentList);
    double failureRate = getFailureRateV2(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployments, totalDeployments);
    ChangeRate failureRateChangeRate = getFailureRateChangeRateV2(serviceDeploymentList, prevServiceDeploymentList);
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);

    calculateRatesV2(serviceDeploymentList);

    return ServiceDeploymentListInfoV2.builder()
        .startTime(startTime)
        .endTime(endTime == -1 ? null : endTime)
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  @Override
  public ServiceDeploymentsList getServiceDeploymentsList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    if (endTime < startTime) {
      throw new InvalidRequestException("End time cannot be less than start time");
    }
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);

    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    ServiceDeploymentInfoDTOV2 serviceDeployments = getServiceDeploymentsV3(accountIdentifier, orgIdentifier,
        projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();
    calculateRatesV2(serviceDeploymentList);

    return ServiceDeploymentsList.builder()
        .startTime(startTime)
        .endTime(endTime)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  @Override
  public ServiceDeploymentMetrics getServiceDeploymentMetrics(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    if (endTime < startTime) {
      throw new InvalidRequestException("End time cannot be less than start time");
    }
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceDeploymentInfoDTOV2 serviceDeployments = getServiceDeploymentsV3(accountIdentifier, orgIdentifier,
        projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTOV2 prevServiceDeployment = getServiceDeploymentsV3(accountIdentifier, orgIdentifier,
        projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays, parentUniqueIds);
    List<ServiceDeploymentV2> prevServiceDeploymentList = prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeploymentsV2(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeploymentsV2(prevServiceDeploymentList);
    double failureRate = getFailureRateV2(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployments, totalDeployments);
    ChangeRate failureRateChangeRate = getFailureRateChangeRateV2(serviceDeploymentList, prevServiceDeploymentList);
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);

    return ServiceDeploymentMetrics.builder()
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .build();
  }

  /**
   * This API processes all services for given combination of identifiers and produces list of data points
   * determining the active number of services at particular timestamps, distanced by equal quantity
   * determined by the groupBy param
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param startTimeInMs     start time of the search interval
   * @param endTimeInMs       end time of the search interval
   * @param timeGroupType     groupBy param to determine the discreteness of the growth trend
   * @return
   */
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getServicesGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTimeInMs, long endTimeInMs,
      TimeGroupType timeGroupType) {
    // Fetch all services for given accId + orgId + projectId including deleted ones in ASC order of creation time
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceEntity> serviceEntities = serviceEntityService.getAllServices(scopeInfo);

    // Create List<EntityStatusDetails> out of service entity list to create growth trend out of it
    List<io.harness.ng.overview.dto.EntityStatusDetails> entities = new ArrayList<>();
    serviceEntities.forEach(serviceEntity -> {
      if (Boolean.FALSE.equals(serviceEntity.getDeleted())) {
        entities.add(new io.harness.ng.overview.dto.EntityStatusDetails(serviceEntity.getCreatedAt()));
      } else {
        entities.add(new EntityStatusDetails(
            serviceEntity.getCreatedAt(), serviceEntity.getDeleted(), serviceEntity.getDeletedAt()));
      }
    });

    return new io.harness.ng.overview.dto.TimeValuePairListDTO<>(
        GrowthTrendEvaluator.getGrowthTrend(entities, startTimeInMs, endTimeInMs, timeGroupType));
  }

  public ServiceGrowthTrendAndEnvBasedInfo getServicesGrowthTrendV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTimeInMs, long endTimeInMs, TimeGroupType timeGroupType) {
    // Fetch all services for given accId + orgId + projectId including deleted ones in ASC order of creation time
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceEntity> serviceEntities = serviceEntityService.getAllServices(scopeInfo);
    long totalServiceEntities =
        serviceEntities.stream().filter(serviceEntity -> Boolean.FALSE.equals(serviceEntity.getDeleted())).count();

    // Create List<EntityStatusDetails> out of service entity list to create growth trend out of it
    List<io.harness.ng.overview.dto.EntityStatusDetails> entities = new ArrayList<>();
    serviceEntities.forEach(serviceEntity -> {
      if (Boolean.FALSE.equals(serviceEntity.getDeleted())) {
        entities.add(new io.harness.ng.overview.dto.EntityStatusDetails(serviceEntity.getCreatedAt()));
      } else {
        entities.add(new EntityStatusDetails(
            serviceEntity.getCreatedAt(), serviceEntity.getDeleted(), serviceEntity.getDeletedAt()));
      }
    });

    Map<EnvironmentType, Integer> activeServiceInstanceCountBreakdownByEnvType =
        instanceDashboardService.getActiveServiceInstanceCountBreakdownByEnvType(
            accountIdentifier, orgIdentifier, projectIdentifier);
    Map<EnvironmentTypeCount, Integer> activeServiceInstanceCountBreakdownByEnvTypeCount = new HashMap<>();
    activeServiceInstanceCountBreakdownByEnvTypeCount.put(
        EnvironmentTypeCount.prod, activeServiceInstanceCountBreakdownByEnvType.get(EnvironmentType.Production));
    activeServiceInstanceCountBreakdownByEnvTypeCount.put(
        EnvironmentTypeCount.nonProd, activeServiceInstanceCountBreakdownByEnvType.get(EnvironmentType.PreProduction));

    return ServiceGrowthTrendAndEnvBasedInfo.builder()
        .ActiveServiceInstancesCountBasedOnEnvironmentType(activeServiceInstanceCountBreakdownByEnvTypeCount)
        .timeValuePairListDTO(new io.harness.ng.overview.dto.TimeValuePairListDTO<>(
            GrowthTrendEvaluator.getGrowthTrend(entities, startTimeInMs, endTimeInMs, timeGroupType)))
        .totalServiceEntities(totalServiceEntities)
        .build();
  }

  private double getFailureRateChangeRate(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList,
      List<io.harness.ng.overview.dto.ServiceDeployment> prevExecutionDeploymentList) {
    double failureRate = getFailureRate(executionDeploymentList);
    double prevFailureRate = getFailureRate(prevExecutionDeploymentList);
    return calculateChangeRate(prevFailureRate, failureRate);
  }

  private ChangeRate getFailureRateChangeRateV2(
      List<ServiceDeploymentV2> executionDeploymentList, List<ServiceDeploymentV2> prevExecutionDeploymentList) {
    double failureRate = getFailureRateV2(executionDeploymentList);
    double prevFailureRate = getFailureRateV2(prevExecutionDeploymentList);
    return calculateChangeRateV2(prevFailureRate, failureRate);
  }

  private double getFailureRate(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList) {
    long totalDeployments = executionDeploymentList.stream()
                                .map(io.harness.ng.overview.dto.ServiceDeployment::getDeployments)
                                .mapToLong(io.harness.ng.overview.dto.DeploymentCount::getTotal)
                                .sum();
    long totalFailure = executionDeploymentList.stream()
                            .map(io.harness.ng.overview.dto.ServiceDeployment::getDeployments)
                            .mapToLong(DeploymentCount::getFailure)
                            .sum();
    double failureRate = totalFailure * 100;
    if (totalDeployments != 0) {
      failureRate = failureRate / totalDeployments;
    }
    return failureRate;
  }

  private double getFailureRateV2(List<ServiceDeploymentV2> executionDeploymentList) {
    long totalDeployments = executionDeploymentList.stream()
                                .map(ServiceDeploymentV2::getDeployments)
                                .mapToLong(DeploymentCount::getTotal)
                                .sum();
    long totalFailure = executionDeploymentList.stream()
                            .map(ServiceDeploymentV2::getDeployments)
                            .mapToLong(DeploymentCount::getFailure)
                            .sum();
    double failureRate = totalFailure * 100;
    if (totalDeployments != 0) {
      failureRate = failureRate / totalDeployments;
    }
    return failureRate;
  }

  private double calculateChangeRate(double prevValue, double curValue) {
    if (prevValue == curValue) {
      return 0;
    }
    if (prevValue == 0) {
      return INVALID_CHANGE_RATE;
    }
    return ((curValue - prevValue) * 100) / prevValue;
  }

  private ChangeRate calculateChangeRateV2(double prevValue, double curValue) {
    if (prevValue == curValue) {
      return new ChangeRate(Double.valueOf(0));
    }
    if (prevValue == 0) {
      return new ChangeRate(null);
    }
    return new ChangeRate(((curValue - prevValue) * 100) / prevValue);
  }

  private long getTotalDeployments(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList) {
    long total = 0;
    for (ServiceDeployment item : executionDeploymentList) {
      total += item.getDeployments().getTotal();
    }
    return total;
  }

  private long getTotalDeploymentsV2(List<ServiceDeploymentV2> executionDeploymentList) {
    long total = 0;
    for (ServiceDeploymentV2 item : executionDeploymentList) {
      total += item.getDeployments().getTotal();
    }
    return total;
  }

  public DeploymentStatusInfoList queryCalculatorDeploymentInfo(String queryStatus) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> identifierList = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();
    List<String> parentUniqueIdList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerTypeList = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          objectIdList.add(resultSet.getString("id"));
          planExecutionIdList.add(resultSet.getString("planexecutionid"));
          identifierList.add(resultSet.getString("pipelineidentifier"));
          namePipelineList.add(resultSet.getString("name"));
          startTs.add(Long.valueOf(resultSet.getString("startts")));
          deploymentStatus.add(resultSet.getString("status"));
          orgIdentifierList.add(resultSet.getString("orgidentifier"));
          projectIdentifierList.add(resultSet.getString("projectidentifier"));
          parentUniqueIdList.add(resultSet.getString("parent_unique_id"));
          if (resultSet.getString("endTs") != null) {
            endTs.add(Long.valueOf(resultSet.getString("endTs")));
          } else {
            endTs.add(-1L);
          }

          // GitInfo
          GitInfo gitInfo = GitInfo.builder()
                                .targetBranch(resultSet.getString("moduleinfo_branch_name"))
                                .sourceBranch(resultSet.getString("source_branch"))
                                .repoName(resultSet.getString("moduleinfo_repository"))
                                .commit(resultSet.getString("moduleinfo_branch_commit_message"))
                                .commitID(resultSet.getString("moduleinfo_branch_commit_id"))
                                .eventType(resultSet.getString("moduleinfo_event"))
                                .build();
          gitInfoList.add(gitInfo);

          // TriggerType
          triggerTypeList.add(resultSet.getString("trigger_type"));

          // AuthorInfo
          authorInfoList.add(AuthorInfo.builder()
                                 .name(resultSet.getString("moduleinfo_author_id"))
                                 .url(resultSet.getString("author_avatar"))
                                 .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return DeploymentStatusInfoList.builder()
        .objectIdList(objectIdList)
        .deploymentStatus(deploymentStatus)
        .endTs(endTs)
        .namePipelineList(namePipelineList)
        .startTs(startTs)
        .pipelineIdentifierList(identifierList)
        .planExecutionIdList(planExecutionIdList)
        .gitInfoList(gitInfoList)
        .triggerType(triggerTypeList)
        .author(authorInfoList)
        .orgIdentifierList(orgIdentifierList)
        .projectIdentifierList(projectIdentifierList)
        .parentUniqueIdList(parentUniqueIdList)
        .build();
  }

  public DeploymentStatusInfoList queryCalculatorDeploymentInfo(Query queryStatus) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> identifierList = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();
    List<String> parentUniqueIdList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerTypeList = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(queryStatus.getSQL(), queryStatus.getBindValues().toArray()).forEach(record -> {
          objectIdList.add(record.get("id", String.class));
          planExecutionIdList.add(record.get("planexecutionid", String.class));
          identifierList.add(record.get("pipelineidentifier", String.class));
          namePipelineList.add(record.get("name", String.class));
          startTs.add(record.get("startts", Long.class));
          deploymentStatus.add(record.get("status", String.class));
          orgIdentifierList.add(record.get("orgidentifier", String.class));
          projectIdentifierList.add(record.get("projectidentifier", String.class));
          parentUniqueIdList.add(record.get("parent_unique_id", String.class));
          if (record.get("endts") != null) {
            endTs.add(record.get("endts", Long.class));
          } else {
            endTs.add(-1L);
          }

          // GitInfo
          GitInfo gitInfo = GitInfo.builder()
                                .targetBranch(record.get("moduleinfo_branch_name", String.class))
                                .sourceBranch(record.get("source_branch", String.class))
                                .repoName(record.get("moduleinfo_repository", String.class))
                                .commit(record.get("moduleinfo_branch_commit_message", String.class))
                                .commitID(record.get("moduleinfo_branch_commit_id", String.class))
                                .eventType(record.get("moduleinfo_event", String.class))
                                .build();
          gitInfoList.add(gitInfo);

          // TriggerType
          triggerTypeList.add(record.get("trigger_type", String.class));

          // AuthorInfo
          authorInfoList.add(AuthorInfo.builder()
                                 .name(record.get("moduleinfo_author_id", String.class))
                                 .url(record.get("author_avatar", String.class))
                                 .build());
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return DeploymentStatusInfoList.builder()
        .objectIdList(objectIdList)
        .deploymentStatus(deploymentStatus)
        .endTs(endTs)
        .namePipelineList(namePipelineList)
        .startTs(startTs)
        .pipelineIdentifierList(identifierList)
        .planExecutionIdList(planExecutionIdList)
        .gitInfoList(gitInfoList)
        .triggerType(triggerTypeList)
        .author(authorInfoList)
        .orgIdentifierList(orgIdentifierList)
        .projectIdentifierList(projectIdentifierList)
        .parentUniqueIdList(parentUniqueIdList)
        .build();
  }

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(
      String queryStatus, String queryServiceTag, ScopeInfo scopeInfo) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> pipelineIdentifierList = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerType = new ArrayList<>();
    List<AuthorInfo> author = new ArrayList<>();

    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineToEnvMap = new HashMap<>();

    DeploymentStatusInfoList deploymentStatusInfoList = queryCalculatorDeploymentInfo(queryStatus);
    deploymentStatus = deploymentStatusInfoList.getDeploymentStatus();
    endTs = deploymentStatusInfoList.getEndTs();
    namePipelineList = deploymentStatusInfoList.getNamePipelineList();
    objectIdList = deploymentStatusInfoList.getObjectIdList();
    startTs = deploymentStatusInfoList.getStartTs();
    planExecutionIdList = deploymentStatusInfoList.getPlanExecutionIdList();
    pipelineIdentifierList = deploymentStatusInfoList.getPipelineIdentifierList();
    orgIdentifierList = deploymentStatusInfoList.getOrgIdentifierList();
    projectIdentifierList = deploymentStatusInfoList.getProjectIdentifierList();

    gitInfoList = deploymentStatusInfoList.getGitInfoList();
    triggerType = deploymentStatusInfoList.getTriggerType();
    author = deploymentStatusInfoList.getAuthor();

    Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
        deploymentInfo = queryCalculatorServiceTagMag(queryServiceTag);
    serviceTagMap = deploymentInfo.getKey();
    pipelineToEnvMap = deploymentInfo.getValue();

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.getOrDefault(objectId, Collections.emptyList()),
          pipelineToEnvMap.getOrDefault(objectId, Collections.emptyList()), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier()));
    }
    return statusInfo;
  }

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(
      String queryStatus, String queryServiceTag, Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> pipelineIdentifierList = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();
    List<String> parentUniqueIdList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerType = new ArrayList<>();
    List<AuthorInfo> author = new ArrayList<>();

    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineToEnvMap = new HashMap<>();

    DeploymentStatusInfoList deploymentStatusInfoList = queryCalculatorDeploymentInfo(queryStatus);
    deploymentStatus = deploymentStatusInfoList.getDeploymentStatus();
    endTs = deploymentStatusInfoList.getEndTs();
    namePipelineList = deploymentStatusInfoList.getNamePipelineList();
    objectIdList = deploymentStatusInfoList.getObjectIdList();
    startTs = deploymentStatusInfoList.getStartTs();
    planExecutionIdList = deploymentStatusInfoList.getPlanExecutionIdList();
    pipelineIdentifierList = deploymentStatusInfoList.getPipelineIdentifierList();
    orgIdentifierList = deploymentStatusInfoList.getOrgIdentifierList();
    projectIdentifierList = deploymentStatusInfoList.getProjectIdentifierList();
    parentUniqueIdList = deploymentStatusInfoList.getParentUniqueIdList();

    gitInfoList = deploymentStatusInfoList.getGitInfoList();
    triggerType = deploymentStatusInfoList.getTriggerType();
    author = deploymentStatusInfoList.getAuthor();

    Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
        deploymentInfo = queryCalculatorServiceTagMag(queryServiceTag);
    serviceTagMap = deploymentInfo.getKey();
    pipelineToEnvMap = deploymentInfo.getValue();

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.getOrDefault(objectId, Collections.emptyList()),
          pipelineToEnvMap.getOrDefault(objectId, Collections.emptyList()),
          parentUniqueIdsToScopeInfoMap.get(parentUniqueIdList.get(i)).getOrgIdentifier(),
          parentUniqueIdsToScopeInfoMap.get(parentUniqueIdList.get(i)).getProjectIdentifier()));
    }
    return statusInfo;
  }

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(
      Query queryStatus, Query queryServiceTag, ScopeInfo scopeInfo) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> pipelineIdentifierList = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerType = new ArrayList<>();
    List<AuthorInfo> author = new ArrayList<>();

    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineToEnvMap = new HashMap<>();

    DeploymentStatusInfoList deploymentStatusInfoList = queryCalculatorDeploymentInfo(queryStatus);
    deploymentStatus = deploymentStatusInfoList.getDeploymentStatus();
    endTs = deploymentStatusInfoList.getEndTs();
    namePipelineList = deploymentStatusInfoList.getNamePipelineList();
    objectIdList = deploymentStatusInfoList.getObjectIdList();
    startTs = deploymentStatusInfoList.getStartTs();
    planExecutionIdList = deploymentStatusInfoList.getPlanExecutionIdList();
    pipelineIdentifierList = deploymentStatusInfoList.getPipelineIdentifierList();
    // this will be outdated.
    orgIdentifierList = deploymentStatusInfoList.getOrgIdentifierList();
    projectIdentifierList = deploymentStatusInfoList.getProjectIdentifierList();

    gitInfoList = deploymentStatusInfoList.getGitInfoList();
    triggerType = deploymentStatusInfoList.getTriggerType();
    author = deploymentStatusInfoList.getAuthor();

    Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
        deploymentInfo = queryCalculatorServiceTagMag(queryServiceTag);
    serviceTagMap = deploymentInfo.getKey();
    pipelineToEnvMap = deploymentInfo.getValue();

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.getOrDefault(objectId, Collections.emptyList()),
          pipelineToEnvMap.getOrDefault(objectId, Collections.emptyList()), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier()));
    }
    return statusInfo;
  }

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(
      Query queryStatus, Query queryServiceTag, Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> pipelineIdentifierList = new ArrayList<>();
    List<String> orgIdentifierList = new ArrayList<>();
    List<String> projectIdentifierList = new ArrayList<>();
    List<String> parentUniqueIdList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerType = new ArrayList<>();
    List<AuthorInfo> author = new ArrayList<>();

    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineToEnvMap = new HashMap<>();

    DeploymentStatusInfoList deploymentStatusInfoList = queryCalculatorDeploymentInfo(queryStatus);
    deploymentStatus = deploymentStatusInfoList.getDeploymentStatus();
    endTs = deploymentStatusInfoList.getEndTs();
    namePipelineList = deploymentStatusInfoList.getNamePipelineList();
    objectIdList = deploymentStatusInfoList.getObjectIdList();
    startTs = deploymentStatusInfoList.getStartTs();
    planExecutionIdList = deploymentStatusInfoList.getPlanExecutionIdList();
    pipelineIdentifierList = deploymentStatusInfoList.getPipelineIdentifierList();
    // this will be outdated.
    orgIdentifierList = deploymentStatusInfoList.getOrgIdentifierList();
    projectIdentifierList = deploymentStatusInfoList.getProjectIdentifierList();
    parentUniqueIdList = deploymentStatusInfoList.getParentUniqueIdList();

    gitInfoList = deploymentStatusInfoList.getGitInfoList();
    triggerType = deploymentStatusInfoList.getTriggerType();
    author = deploymentStatusInfoList.getAuthor();

    Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
        deploymentInfo = queryCalculatorServiceTagMag(queryServiceTag);
    serviceTagMap = deploymentInfo.getKey();
    pipelineToEnvMap = deploymentInfo.getValue();

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.getOrDefault(objectId, Collections.emptyList()),
          pipelineToEnvMap.getOrDefault(objectId, Collections.emptyList()),
          parentUniqueIdsToScopeInfoMap.get(parentUniqueIdList.get(i)).getOrgIdentifier(),
          parentUniqueIdsToScopeInfoMap.get(parentUniqueIdList.get(i)).getProjectIdentifier()));
    }
    return statusInfo;
  }

  @Override
  public DashboardExecutionStatusInfo getDeploymentActiveFailedRunningInfo(
      String accountId, String orgId, String projectId, long days, long startInterval, long endInterval) {
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountId, orgId, projectId, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));
    // failed
    String queryFailed = queryBuilderStatusNew(
        days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval, parentUniqueIds);
    String queryServiceNameTagIdFailed = queryBuilderSelectIdLimitTimeCdTableNew(
        days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdFailed = queryBuilderServiceTag(queryServiceNameTagIdFailed);
    List<ExecutionStatusInfo> failure =
        getDeploymentStatusInfo(queryFailed, queryServiceNameTagIdFailed, parentUniqueIdsToScopeInfoMap);

    // active
    String queryActive = queryBuilderStatusNew(days, activeStatusList, startInterval, endInterval, parentUniqueIds);
    String queryServiceNameTagIdActive =
        queryBuilderSelectIdLimitTimeCdTableNew(days, activeStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdActive = queryBuilderServiceTag(queryServiceNameTagIdActive);
    List<ExecutionStatusInfo> active =
        getDeploymentStatusInfo(queryActive, queryServiceNameTagIdActive, parentUniqueIdsToScopeInfoMap);

    // pending
    String queryPending = queryBuilderStatusNew(days, pendingStatusList, startInterval, endInterval, parentUniqueIds);
    String queryServiceNameTagIdPending =
        queryBuilderSelectIdLimitTimeCdTableNew(days, pendingStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdPending = queryBuilderServiceTag(queryServiceNameTagIdPending);
    List<ExecutionStatusInfo> pending =
        getDeploymentStatusInfo(queryPending, queryServiceNameTagIdPending, parentUniqueIdsToScopeInfoMap);

    return DashboardExecutionStatusInfo.builder().failure(failure).active(active).pending(pending).build();
  }

  @Override
  public DashboardExecutionStatusInfo getDeploymentActiveFailedRunningInfoViaJooq(
      String accountId, String orgId, String projectId, long days, long startInterval, long endInterval) {
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountId, orgId, projectId, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));

    // failed
    SelectConditionStep queryFailed = queryBuilderStatusNewViaJooq(
        days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval, parentUniqueIds);
    SelectConditionStep queryServiceNameTagIdFailed = queryBuilderSelectIdLimitTimeCdTableNewViaJooq(
        days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdFailed = queryBuilderServiceTagViaJooq(queryServiceNameTagIdFailed);
    List<ExecutionStatusInfo> failure =
        getDeploymentStatusInfo(queryFailed, queryServiceNameTagIdFailed, parentUniqueIdsToScopeInfoMap);

    // active
    SelectConditionStep queryActive =
        queryBuilderStatusNewViaJooq(days, activeStatusList, startInterval, endInterval, parentUniqueIds);
    SelectConditionStep queryServiceNameTagIdActive = queryBuilderSelectIdLimitTimeCdTableNewViaJooq(
        days, activeStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdActive = queryBuilderServiceTagViaJooq(queryServiceNameTagIdActive);
    List<ExecutionStatusInfo> active =
        getDeploymentStatusInfo(queryActive, queryServiceNameTagIdActive, parentUniqueIdsToScopeInfoMap);

    // pending
    SelectConditionStep queryPending =
        queryBuilderStatusNewViaJooq(days, pendingStatusList, startInterval, endInterval, parentUniqueIds);
    SelectConditionStep queryServiceNameTagIdPending = queryBuilderSelectIdLimitTimeCdTableNewViaJooq(
        days, pendingStatusList, startInterval, endInterval, parentUniqueIds);
    queryServiceNameTagIdPending = queryBuilderServiceTagViaJooq(queryServiceNameTagIdPending);
    List<ExecutionStatusInfo> pending =
        getDeploymentStatusInfo(queryPending, queryServiceNameTagIdPending, parentUniqueIdsToScopeInfoMap);

    return DashboardExecutionStatusInfo.builder().failure(failure).active(active).pending(pending).build();
  }

  private ExecutionStatusInfo getDeploymentStatusInfoObject(String name, String identfier, String planExecutionId,
      Long startTime, Long endTime, String status, GitInfo gitInfo, String triggerType, AuthorInfo authorInfo,
      List<ServiceDeploymentInfo> serviceDeploymentInfos, List<EnvironmentDeploymentsInfo> environmentDeploymentsInfos,
      String orgIdentifier, String projectIdentifier) {
    return ExecutionStatusInfo.builder()
        .pipelineName(name)
        .pipelineIdentifier(identfier)
        .planExecutionId(planExecutionId)
        .startTs(startTime)
        .endTs(endTime)
        .status(status)
        .gitInfo(gitInfo)
        .triggerType(triggerType)
        .author(authorInfo)
        .serviceInfoList(serviceDeploymentInfos)
        .environmentInfoList(environmentDeploymentsInfos)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private ServiceDeploymentInfo getServiceDeployment(String service_name, String tag, String image, String serviceId) {
    if (service_name != null) {
      if (image != null) {
        return ServiceDeploymentInfo.builder()
            .serviceName(service_name)
            .serviceId(serviceId)
            .serviceTag(tag)
            .image(image)
            .build();
      }
      return ServiceDeploymentInfo.builder().serviceName(service_name).serviceId(serviceId).build();
    }
    return ServiceDeploymentInfo.builder().build();
  }

  private WorkloadDeploymentInfo getWorkloadDeploymentInfo(WorkloadDeploymentInfo workloadDeploymentInfo,
      long totalDeployment, long prevTotalDeployment, long success, long previousSuccess, long failure,
      long previousFailure, long numberOfDays) {
    double percentSuccess = 0.0;
    double failureRate = 0.0;
    double failureRateChangeRate = calculateChangeRate(previousFailure, failure);
    double totalDeploymentChangeRate = calculateChangeRate(prevTotalDeployment, totalDeployment);
    double frequency = totalDeployment / (double) numberOfDays;
    double prevFrequency = prevTotalDeployment / (double) numberOfDays;
    double frequencyChangeRate = calculateChangeRate(prevFrequency, frequency);
    if (totalDeployment != 0) {
      percentSuccess = success / (double) totalDeployment;
      percentSuccess = percentSuccess * 100.0;
      failureRate = failure / (double) totalDeployment;
      failureRate = failureRate * 100.0;
    }
    return WorkloadDeploymentInfo.builder()
        .serviceName(workloadDeploymentInfo.getServiceName())
        .serviceId(workloadDeploymentInfo.getServiceId())
        .lastExecuted(workloadDeploymentInfo.getLastExecuted())
        .deploymentTypeList(workloadDeploymentInfo.getDeploymentTypeList())
        .totalDeployments(totalDeployment)
        .totalDeploymentChangeRate(totalDeploymentChangeRate)
        .percentSuccess(percentSuccess)
        .rateSuccess(calculateChangeRate(previousSuccess, success))
        .failureRate(failureRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequency(frequency)
        .frequencyChangeRate(frequencyChangeRate)
        .lastPipelineExecutionId(workloadDeploymentInfo.getLastPipelineExecutionId())
        .workload(workloadDeploymentInfo.getWorkload())
        .build();
  }

  private WorkloadDeploymentInfoV2 getWorkloadDeploymentInfoV2(WorkloadDeploymentInfoV2 workloadDeploymentInfo,
      long totalDeployment, long prevTotalDeployment, long success, long previousSuccess, long failure,
      long previousFailure, long numberOfDays) {
    double percentSuccess = 0.0;
    double failureRate = 0.0;
    ChangeRate failureRateChangeRate = calculateChangeRateV2(previousFailure, failure);
    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployment, totalDeployment);
    double frequency = totalDeployment / (double) numberOfDays;
    double prevFrequency = prevTotalDeployment / (double) numberOfDays;
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);
    ChangeRate rateSuccess = calculateChangeRateV2(previousSuccess, success);
    if (totalDeployment != 0) {
      percentSuccess = success / (double) totalDeployment;
      percentSuccess = percentSuccess * 100.0;
      failureRate = failure / (double) totalDeployment;
      failureRate = failureRate * 100.0;
    }
    return WorkloadDeploymentInfoV2.builder()
        .serviceName(workloadDeploymentInfo.getServiceName())
        .serviceId(workloadDeploymentInfo.getServiceId())
        .lastExecuted(workloadDeploymentInfo.getLastExecuted())
        .deploymentTypeList(workloadDeploymentInfo.getDeploymentTypeList())
        .totalDeployments(totalDeployment)
        .totalDeploymentChangeRate(totalDeploymentChangeRate)
        .percentSuccess(percentSuccess)
        .rateSuccess(rateSuccess)
        .failureRate(failureRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequency(frequency)
        .frequencyChangeRate(frequencyChangeRate)
        .lastPipelineExecutionId(workloadDeploymentInfo.getLastPipelineExecutionId())
        .workload(workloadDeploymentInfo.getWorkload())
        .build();
  }

  public DashboardWorkloadDeployment getWorkloadDeploymentInfoCalculation(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfo workloadDeploymentInfo =
          WorkloadDeploymentInfo.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfo(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeployment.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public DashboardWorkloadDeployment getWorkloadDeploymentInfoCalculationViaJooq(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfo workloadDeploymentInfo =
          WorkloadDeploymentInfo.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfo(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeployment.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  // Aggregated success failure, total counts that match serviceIds (50)
  // Status of the workalods (aggregating them)

  // LastExecutionDetails for grouped on serviceId (50)
  //  Earlier we were fetching all workloads (sstages matching serviceIds)

  public DashboardWorkloadDeploymentV2 getWorkloadDeploymentInfoCalculationV2(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();

    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfoV2 workloadDeploymentInfo =
          WorkloadDeploymentInfoV2.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfoV2(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public DashboardWorkloadDeploymentV2 getWorkloadDeploymentInfoCalculationV2ViaJooq(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();

    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfoV2 workloadDeploymentInfo =
          WorkloadDeploymentInfoV2.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfoV2(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public DashboardWorkloadDeploymentV2 getWorkloadDeploymentInfoCalculationV2Paginated(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfoV2 workloadDeploymentInfo =
          WorkloadDeploymentInfoV2.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfoV2(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public DashboardWorkloadDeploymentV2 getWorkloadDeploymentInfoCalculationV2PaginatedViaJooq(String accountIdentifier,
      List<String> workloadsId, List<String> status, List<Pair<Long, Long>> timeInterval,
      List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap = new HashMap<>();
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = new ArrayList<>();
    List<String> lastWorkloadPipelineExecutionId = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList =
        workloadDeploymentInfoCalculationHelper(workloadsId, status, timeInterval, deploymentTypeList,
            uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList, lastWorkloadPipelineExecutionId);

    pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(lastWorkloadPipelineExecutionId);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfoV2 workloadDeploymentInfo =
          WorkloadDeploymentInfoV2.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfoV2(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public List<WorkloadDeploymentDetails> workloadDeploymentInfoCalculationHelper(List<String> workloadsId,
      List<String> status, List<Pair<Long, Long>> timeInterval, List<String> deploymentTypeList,
      Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate, List<String> pipelineExecutionIdList,
      Collection<String> lastWorkloadPipelineExecutionId) {
    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList = new ArrayList<>();
    for (String workloadId : uniqueWorkloadNameAndId.keySet()) {
      long totalDeployment = 0;
      long prevTotalDeployments = 0;
      long success = 0;
      long previousSuccess = 0;
      long failure = 0;
      long previousFailure = 0;
      long lastExecutedStartTs = 0L;
      long lastExecutedEndTs = 0L;
      String lastStatus = null;
      String deploymentType = null;
      String pipelineExecutionId = null;

      HashMap<Long, Integer> deploymentCountMap = new HashMap<>();

      long startDateCopy = startDate;
      long endDateCopy = endDate;

      while (startDateCopy < endDateCopy) {
        deploymentCountMap.put(startDateCopy, 0);
        startDateCopy = startDateCopy + DAY_IN_MS;
      }

      for (int i = 0; i < workloadsId.size(); i++) {
        if (workloadsId.get(i).contentEquals(workloadId)) {
          long startTime = timeInterval.get(i).getKey();
          long endTime = timeInterval.get(i).getValue();
          long currentTimeEpoch = startTime;
          if (currentTimeEpoch >= startDate && currentTimeEpoch < endDate) {
            currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startDate);
            totalDeployment++;
            deploymentCountMap.put(currentTimeEpoch, deploymentCountMap.get(currentTimeEpoch) + 1);
            if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
              success++;
            }
            if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
              failure++;
            }
            if (lastExecutedStartTs == 0 || lastExecutedStartTs < startTime) {
              lastExecutedStartTs = startTime;
              lastExecutedEndTs = endTime;
              lastStatus = status.get(i);
              deploymentType = deploymentTypeList.get(i);
              pipelineExecutionId = pipelineExecutionIdList.get(i);
            }
          } else {
            prevTotalDeployments++;
            if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
              previousSuccess++;
            }
            if (status.get(i).contentEquals(ExecutionStatus.FAILED.name())) {
              previousFailure++;
            }
          }
        }
      }

      if (totalDeployment > 0) {
        lastWorkloadPipelineExecutionId.add(pipelineExecutionId);
        List<io.harness.ng.overview.dto.WorkloadDateCountInfo> dateCount = new ArrayList<>();
        startDateCopy = startDate;
        endDateCopy = endDate;
        while (startDateCopy < endDateCopy) {
          dateCount.add(WorkloadDateCountInfo.builder()
                            .date(startDateCopy)
                            .execution(WorkloadCountInfo.builder().count(deploymentCountMap.get(startDateCopy)).build())
                            .build());
          startDateCopy = startDateCopy + DAY_IN_MS;
        }
        workloadDeploymentDetailsList.add(WorkloadDeploymentDetails.builder()
                                              .deploymentType(deploymentType)
                                              .workloadId(workloadId)
                                              .totalDeployment(totalDeployment)
                                              .prevTotalDeployments(prevTotalDeployments)
                                              .dateCount(dateCount)
                                              .failure(failure)
                                              .lastExecutedEndTs(lastExecutedEndTs)
                                              .lastExecutedStartTs(lastExecutedStartTs)
                                              .lastStatus(lastStatus)
                                              .pipelineExecutionId(pipelineExecutionId)
                                              .success(success)
                                              .previousFailure(previousFailure)
                                              .previousSuccess(previousSuccess)
                                              .build());
      }
    }
    return workloadDeploymentDetailsList;
  }

  @Override
  public DashboardWorkloadDeployment getDashboardWorkloadDeployment(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    List<String> parentUniqueIds = null;
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    WorkloadInfo workloadInfo = getWorkloadInfo(accountIdentifier, orgIdentifier, projectIdentifier, endInterval,
        previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculation(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public DashboardWorkloadDeployment getDashboardWorkloadDeploymentViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    List<String> parentUniqueIds = null;
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    WorkloadInfo workloadInfo = getWorkloadInfoViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, endInterval,
        previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculationViaJooq(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    List<String> parentUniqueIds = null;
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    if (nextGenConfiguration.getEnablePaginatedQueryOnTimescale()) {
      return getDashboardWorkloadDeploymentV2Paginated(accountIdentifier, orgIdentifier, projectIdentifier,
          startInterval, endInterval, previousStartInterval, envType, parentUniqueIds);
    }
    WorkloadInfo workloadInfo = getWorkloadInfo(accountIdentifier, orgIdentifier, projectIdentifier, endInterval,
        previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculationV2(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2ViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    if (nextGenConfiguration.getEnablePaginatedQueryOnTimescale()) {
      return getDashboardWorkloadDeploymentV2PaginatedViaJooq(accountIdentifier, orgIdentifier, projectIdentifier,
          startInterval, endInterval, previousStartInterval, envType, parentUniqueIds);
    }
    WorkloadInfo workloadInfo = getWorkloadInfoViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, endInterval,
        previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculationV2ViaJooq(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public PageResponse<BasicServiceDeploymentMetrics> getActiveServices(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, EnvironmentType envType, Integer page,
      Integer size) {
    List<String> parentUniqueIds = new ArrayList<>();
    parentUniqueIds = getParentUniqueIdsUnderChildScopes(accountIdentifier, orgIdentifier, projectIdentifier);

    if (page != null && size != null) {
      PageRequest pageRequest = PageRequest.of(page, size);
      SelectConditionStep<Record1<Integer>> totalServicesWithDeployments = buildTotalServicesWithDeployments(
          envType, accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, parentUniqueIds);
      return getNGPageResponse(
          PageableExecutionUtils.getPage(getActiveServicesList(accountIdentifier, orgIdentifier, projectIdentifier,
                                             startInterval, endInterval, envType, pageRequest, parentUniqueIds),
              pageRequest,
              ()
                  -> dslContext
                         .fetchOne(totalServicesWithDeployments.getSQL(),
                             totalServicesWithDeployments.getBindValues().toArray())
                         .getValue(0, Integer.class)));
    } else {
      List<BasicServiceDeploymentMetrics> basicServiceDeploymentMetrics = getActiveServicesList(
          accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, envType, parentUniqueIds);
      if (isEmpty(basicServiceDeploymentMetrics)) {
        return PageResponse.getEmptyPageResponse(null);
      }
      PageRequest pageRequest = PageRequest.of(0, basicServiceDeploymentMetrics.size());
      return getNGPageResponse(PageableExecutionUtils.getPage(
          basicServiceDeploymentMetrics, pageRequest, () -> basicServiceDeploymentMetrics.size()));
    }
  }

  private List<BasicServiceDeploymentMetrics> getActiveServicesList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, EnvironmentType envType,
      List<String> parentUniqueIds) {
    Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap = new HashMap<>();

    // Prepare main query to fetch counts per status
    Query topServicesByDeployments = buildTopServicesByDeploymentsQuery(
        envType, accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, parentUniqueIds);

    // Execute the query with retries and process records
    fetchAndProcessQuery(topServicesByDeployments, basicServiceDeploymentMetricsMap);

    return fetchBasicServiceDeploymentMetricsListOrderByTotalDeployments(basicServiceDeploymentMetricsMap);
  }

  private List<BasicServiceDeploymentMetrics> getActiveServicesList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, EnvironmentType envType, PageRequest pageRequest,
      List<String> parentUniqueIds) {
    Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap = new HashMap<>();

    // Prepare main query to fetch counts per status
    Query statusCountsQuery = buildTopServicesByDeploymentsQuery(envType, accountIdentifier, orgIdentifier,
        projectIdentifier, startInterval, endInterval, pageRequest, parentUniqueIds);

    // Execute the query with retries and process records
    fetchAndProcessQuery(statusCountsQuery, basicServiceDeploymentMetricsMap);

    // Return sorted list
    return fetchBasicServiceDeploymentMetricsListOrderByTotalDeployments(basicServiceDeploymentMetricsMap);
  }

  protected Query buildTopServicesByDeploymentsQuery(EnvironmentType envType, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval, PageRequest pageRequest,
      List<String> parentUniqueIds) {
    // Prepare query for top services
    SelectForUpdateStep<Record3<String, String, Integer>> topServicesQuery = buildTopServicesQuery(envType,
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, pageRequest, parentUniqueIds);

    WithStep topServicesCTE = dslContext.with("top_services").as(topServicesQuery);

    // Prepare main query to fetch counts per status
    return buildStatusCountsQuery(topServicesCTE, envType, accountIdentifier, orgIdentifier, projectIdentifier,
        startInterval, endInterval, parentUniqueIds);
  }

  private SelectForUpdateStep<Record3<String, String, Integer>> buildTopServicesQuery(EnvironmentType envType,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long startInterval, long endInterval,
      PageRequest pageRequest, List<String> parentUniqueIds) {
    SelectConditionStep<Record3<String, String, Integer>> query =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_NAME, count().as("total_deployments"))
            .from(SERVICE_INFRA_INFO)
            .where(trueCondition());

    if (startInterval > 0 && endInterval > 0) {
      SelectConditionStep<Record1<String>> idQuery =
          queryBuilderSelectIdCdTableJooq(startInterval, endInterval, parentUniqueIds);
      if (envType != null) {
        query.and(SERVICE_INFRA_INFO.ENV_TYPE.eq(envType.toString()));
      }
      query.and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idQuery));
      query.and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull()).and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());
    }

    return query.groupBy(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_NAME)
        .orderBy(DSL.field("total_deployments").desc())
        .limit(pageRequest.getPageSize())
        .offset(pageRequest.getPageNumber());
  }

  private ResultQuery<Record4<String, String, String, Integer>> buildStatusCountsQuery(WithStep topServicesCTE,
      EnvironmentType envType, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long startInterval, long endInterval, List<String> parentUniqueIds) {
    SelectConditionStep<Record4<String, String, String, Integer>> statusCountsQuery =
        topServicesCTE
            .select(DSL.field("top_services.service_id", String.class).as(SERVICE_ID),
                DSL.field("top_services.service_name", String.class).as(SERVICE_NAME),
                SERVICE_INFRA_INFO.SERVICE_STATUS, count().as("status_count"))
            .from(SERVICE_INFRA_INFO)
            .join("top_services")
            .on(SERVICE_INFRA_INFO.SERVICE_ID.eq(DSL.field("top_services.service_id", String.class)))
            .where(trueCondition());

    if (startInterval > 0 && endInterval > 0) {
      SelectConditionStep<Record1<String>> idQuery =
          queryBuilderSelectIdCdTableJooq(startInterval, endInterval, parentUniqueIds);
      if (envType != null) {
        statusCountsQuery.and(SERVICE_INFRA_INFO.ENV_TYPE.eq(envType.toString()));
      }
      statusCountsQuery.and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idQuery));
      statusCountsQuery.and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull()).and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());
    }

    return statusCountsQuery.groupBy(DSL.field("top_services.service_id"), DSL.field("top_services.service_name"),
        SERVICE_INFRA_INFO.SERVICE_STATUS);
  }

  protected SelectConditionStep<Record1<Integer>> buildTotalServicesWithDeployments(EnvironmentType envType,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long startInterval, long endInterval,
      List<String> parentUniqueIds) {
    SelectConditionStep<Record1<Integer>> query =
        dslContext.select(countDistinct(SERVICE_INFRA_INFO.SERVICE_ID)).from(SERVICE_INFRA_INFO).where(trueCondition());

    if (startInterval > 0 && endInterval > 0) {
      SelectConditionStep<Record1<String>> idQuery =
          queryBuilderSelectIdCdTableJooq(startInterval, endInterval, parentUniqueIds);
      if (envType != null) {
        query.and(SERVICE_INFRA_INFO.ENV_TYPE.eq(envType.toString()));
      }
      query.and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idQuery));
      query.and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull()).and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());
    }

    return query;
  }

  protected Query buildTopServicesByDeploymentsQuery(EnvironmentType envType, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval,
      List<String> parentUniqueIds) {
    SelectConditionStep<Record4<String, String, String, Integer>> query =
        dslContext
            .select(SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.SERVICE_STATUS,
                count().as("status_count"))
            .from(SERVICE_INFRA_INFO)
            .where(trueCondition());

    if (startInterval > 0 && endInterval > 0) {
      SelectConditionStep<Record1<String>> idQuery =
          queryBuilderSelectIdCdTableJooq(startInterval, endInterval, parentUniqueIds);
      if (envType != null) {
        query.and(SERVICE_INFRA_INFO.ENV_TYPE.eq(envType.toString()));
      }
      query.and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(idQuery));
      query.and(SERVICE_INFRA_INFO.SERVICE_NAME.isNotNull()).and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());
    }

    return query.groupBy(
        SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.SERVICE_STATUS);
  }

  private void fetchAndProcessQuery(
      Query query, Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap) {
    Retry retry = fetchDefaultRetryMechanism();
    retry.getEventPublisher()
        .onRetry((retryEvent)
                     -> log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE,
                                     retryEvent.getLastThrowable().getMessage(), retryEvent.getNumberOfRetryAttempts()),
                         retryEvent.getLastThrowable()))
        .onError(error
            -> log.error(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, error.getLastThrowable().getMessage(),
                             error.getNumberOfRetryAttempts()),
                error.getLastThrowable()));

    // Using decorateRunnable to apply retry mechanism to a void method
    Runnable queryExecution = () -> {
      try {
        executeQuery(query, basicServiceDeploymentMetricsMap);
      } catch (Exception ex) {
        throw new RuntimeException(ex); // Wrapping checked exceptions to allow retry
      }
    };

    Retry.decorateRunnable(retry, queryExecution).run();
  }

  private void executeQuery(Query query, Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap) {
    dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
      String serviceName = record.get(SERVICE_NAME, String.class);
      String serviceId = record.get(SERVICE_ID, String.class);
      String status = record.get(SERVICE_INFRA_INFO.SERVICE_STATUS);
      int totalDeployments = record.get("status_count", Integer.class);
      addToBasicServiceDeploymentMetrics(
          basicServiceDeploymentMetricsMap, serviceName, serviceId, status, totalDeployments);
    });
  }

  private List<BasicServiceDeploymentMetrics> fetchBasicServiceDeploymentMetricsListOrderByTotalDeployments(
      Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap) {
    return basicServiceDeploymentMetricsMap.values()
        .stream()
        .sorted((m1, m2)
                    -> Integer.compare(
                        m2.getDeploymentMetric().getTotalDeployments(), m1.getDeploymentMetric().getTotalDeployments()))
        .collect(Collectors.toList());
  }

  private void addToBasicServiceDeploymentMetrics(
      Map<String, BasicServiceDeploymentMetrics> basicServiceDeploymentMetricsMap, String serviceName, String serviceId,
      String status, int totalDeployments) {
    basicServiceDeploymentMetricsMap.compute(serviceId, (id, existingMetric) -> {
      if (existingMetric == null) {
        existingMetric =
            BasicServiceDeploymentMetrics.builder()
                .serviceName(serviceName)
                .serviceId(serviceId)
                .deploymentMetric(
                    BasicDeploymentMetric.builder()
                        .successfulDeployments(
                            CDDashboardServiceHelper.successStatusList.contains(status) ? totalDeployments : 0)
                        .failedDeployments(
                            CDDashboardServiceHelper.failedStatusList.contains(status) ? totalDeployments : 0)
                        .build())
                .build();
      } else {
        BasicDeploymentMetric metric = existingMetric.getDeploymentMetric();
        if (CDDashboardServiceHelper.successStatusList.contains(status)) {
          metric.setSuccessfulDeployments(metric.getSuccessfulDeployments() + totalDeployments);
        } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
          metric.setFailedDeployments(metric.getFailedDeployments() + totalDeployments);
        }
      }
      return existingMetric;
    });
  }

  @Override
  public DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2Paginated(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType, List<String> parentUniqueIds) {
    WorkloadInfo workloadInfo = getWorkloadInfoPaginated(accountIdentifier, orgIdentifier, projectIdentifier,
        endInterval, previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculationV2Paginated(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2PaginatedViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType, List<String> parentUniqueIds) {
    WorkloadInfo workloadInfo = getWorkloadInfoPaginatedViaJooq(accountIdentifier, orgIdentifier, projectIdentifier,
        endInterval, previousStartInterval, envType, parentUniqueIds);

    return getWorkloadDeploymentInfoCalculationV2PaginatedViaJooq(accountIdentifier, workloadInfo.getWorkloadsId(),
        workloadInfo.getStatus(), workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(),
        workloadInfo.getUniqueWorkloadNameAndId(), startInterval, endInterval,
        workloadInfo.getPipelineExecutionIdList());
  }

  private WorkloadInfo getWorkloadInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long endInterval, long previousStartInterval, EnvironmentType envType, List<String> parentUniqueIds) {
    String query = queryBuilderSelectWorkload(previousStartInterval, endInterval, envType, parentUniqueIds);

    List<String> workloadsId = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Pair<Long, Long>> timeInterval = new ArrayList<>();
    List<String> deploymentTypeList = new ArrayList<>();
    List<String> pipelineExecutionIdList = new ArrayList<>();

    HashMap<String, String> uniqueWorkloadNameAndId = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String serviceName = resultSet.getString(SERVICE_NAME);
          String service_id = resultSet.getString(SERVICE_ID);
          long startTime = Long.parseLong(resultSet.getString("startTs"));
          workloadsId.add(service_id);
          status.add(resultSet.getString("status"));
          String pipelineExecutionId = resultSet.getString(NGServiceConstants.PIPELINE_EXECUTION_ID);
          pipelineExecutionIdList.add(pipelineExecutionId);
          if (resultSet.getString("endTs") != null) {
            timeInterval.add(Pair.of(startTime, Long.valueOf(resultSet.getString("endTs"))));
          } else {
            timeInterval.add(Pair.of(startTime, -1L));
          }
          deploymentTypeList.add(resultSet.getString("deployment_type"));

          if (!uniqueWorkloadNameAndId.containsKey(service_id)) {
            uniqueWorkloadNameAndId.put(service_id, serviceName);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return WorkloadInfo.builder()
        .workloadsId(workloadsId)
        .uniqueWorkloadNameAndId(uniqueWorkloadNameAndId)
        .timeInterval(timeInterval)
        .deploymentTypeList(deploymentTypeList)
        .status(status)
        .pipelineExecutionIdList(pipelineExecutionIdList)
        .build();
  }

  private WorkloadInfo getWorkloadInfoViaJooq(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long endInterval, long previousStartInterval, EnvironmentType envType, List<String> parentUniqueIds) {
    Query query = queryBuilderSelectWorkloadViaJooq(previousStartInterval, endInterval, envType, parentUniqueIds);

    List<String> workloadsId = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Pair<Long, Long>> timeInterval = new ArrayList<>();
    List<String> deploymentTypeList = new ArrayList<>();
    List<String> pipelineExecutionIdList = new ArrayList<>();

    HashMap<String, String> uniqueWorkloadNameAndId = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          String serviceName = record.get(SERVICE_NAME, String.class);
          String service_id = record.get(SERVICE_ID, String.class);
          long startTime = record.get("startts", Long.class);
          workloadsId.add(service_id);
          status.add(record.get("status", String.class));
          String pipelineExecutionId = record.get(NGServiceConstants.PIPELINE_EXECUTION_ID, String.class);
          pipelineExecutionIdList.add(pipelineExecutionId);
          if (record.get("endts") != null) {
            timeInterval.add(Pair.of(startTime, record.get("endts", Long.class)));
          } else {
            timeInterval.add(Pair.of(startTime, -1L));
          }
          deploymentTypeList.add(record.get("deployment_type", String.class));

          if (!uniqueWorkloadNameAndId.containsKey(service_id)) {
            uniqueWorkloadNameAndId.put(service_id, serviceName);
          }
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return WorkloadInfo.builder()
        .workloadsId(workloadsId)
        .uniqueWorkloadNameAndId(uniqueWorkloadNameAndId)
        .timeInterval(timeInterval)
        .deploymentTypeList(deploymentTypeList)
        .status(status)
        .pipelineExecutionIdList(pipelineExecutionIdList)
        .build();
  }

  private WorkloadInfo getWorkloadInfoPaginated(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long endInterval, long previousStartInterval, EnvironmentType envType,
      List<String> parentUniqueIds) {
    String query = queryBuilderSelectWorkload(previousStartInterval, endInterval, envType, parentUniqueIds);

    List<String> workloadsId = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Pair<Long, Long>> timeInterval = new ArrayList<>();
    List<String> deploymentTypeList = new ArrayList<>();
    List<String> pipelineExecutionIdList = new ArrayList<>();

    HashMap<String, String> uniqueWorkloadNameAndId = new HashMap<>();

    TimescalePersistence queryExecutor = new TimescalePersistence(timeScaleDBService, dslContext);

    ModifyPreparedStatement modifyPreparedStatement = (preparedStatement, connection) -> {};

    // Define a PaginatedQueryCallback using a lambda expression
    PaginatedQueryCallback callback = resultSet -> {
      String serviceName = resultSet.getString(SERVICE_NAME);
      String service_id = resultSet.getString(SERVICE_ID);
      long startTime = Long.parseLong(resultSet.getString("startTs"));
      workloadsId.add(service_id);
      status.add(resultSet.getString("status"));
      String pipelineExecutionId = resultSet.getString(NGServiceConstants.PIPELINE_EXECUTION_ID);
      pipelineExecutionIdList.add(pipelineExecutionId);
      if (resultSet.getString("endTs") != null) {
        timeInterval.add(Pair.of(startTime, Long.valueOf(resultSet.getString("endTs"))));
      } else {
        timeInterval.add(Pair.of(startTime, -1L));
      }
      deploymentTypeList.add(resultSet.getString("deployment_type"));

      if (!uniqueWorkloadNameAndId.containsKey(service_id)) {
        uniqueWorkloadNameAndId.put(service_id, serviceName);
      }
    };

    queryExecutor.executePaginatedQuery(query, BATCH_SIZE, MAX_RETRY_COUNT, callback, modifyPreparedStatement);
    return WorkloadInfo.builder()
        .workloadsId(workloadsId)
        .uniqueWorkloadNameAndId(uniqueWorkloadNameAndId)
        .timeInterval(timeInterval)
        .deploymentTypeList(deploymentTypeList)
        .status(status)
        .pipelineExecutionIdList(pipelineExecutionIdList)
        .build();
  }

  private WorkloadInfo getWorkloadInfoPaginatedViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long endInterval, long previousStartInterval, EnvironmentType envType,
      List<String> parentUniqueIds) {
    Query query = queryBuilderSelectWorkloadViaJooq(previousStartInterval, endInterval, envType, parentUniqueIds);

    List<String> workloadsId = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Pair<Long, Long>> timeInterval = new ArrayList<>();
    List<String> deploymentTypeList = new ArrayList<>();
    List<String> pipelineExecutionIdList = new ArrayList<>();

    HashMap<String, String> uniqueWorkloadNameAndId = new HashMap<>();

    TimescalePersistence queryExecutor = new TimescalePersistence(timeScaleDBService, dslContext);

    // Define a PaginatedQueryCallback using a lambda expression
    PaginatedQueryCallbackViaJooq callback = record -> {
      String serviceName = record.get(SERVICE_NAME, String.class);
      String service_id = record.get(SERVICE_ID, String.class);
      long startTime = record.get("startts", Long.class);
      workloadsId.add(service_id);
      status.add(record.get("status", String.class));
      String pipelineExecutionId = record.get(NGServiceConstants.PIPELINE_EXECUTION_ID, String.class);
      pipelineExecutionIdList.add(pipelineExecutionId);
      if (record.get("endts") != null) {
        timeInterval.add(Pair.of(startTime, record.get("endts", Long.class)));
      } else {
        timeInterval.add(Pair.of(startTime, -1L));
      }
      deploymentTypeList.add(record.get("deployment_type", String.class));
      if (!uniqueWorkloadNameAndId.containsKey(service_id)) {
        uniqueWorkloadNameAndId.put(service_id, serviceName);
      }
    };

    queryExecutor.executePaginatedQuery(query, BATCH_SIZE, MAX_RETRY_COUNT, callback);
    return WorkloadInfo.builder()
        .workloadsId(workloadsId)
        .uniqueWorkloadNameAndId(uniqueWorkloadNameAndId)
        .timeInterval(timeInterval)
        .deploymentTypeList(deploymentTypeList)
        .status(status)
        .pipelineExecutionIdList(pipelineExecutionIdList)
        .build();
  }

  public long getTimeUnitToGroupBy(TimeGroupType timeGroupType) {
    if (timeGroupType == DAY) {
      return DAY_IN_MS;
    } else if (timeGroupType == HOUR) {
      return HOUR_IN_MS;
    } else {
      throw new UnknownEnumTypeException("Time Group Type", String.valueOf(timeGroupType));
    }
  }

  public long getStartingDateEpochValue(long epochValue, long startInterval) {
    return epochValue - (epochValue - startInterval) % DAY_IN_MS;
  }

  /*
  Returns break down of instance count for various environment type for given account+org+project+serviceIds
*/
  @Override
  public InstanceCountDetailsByEnvTypeAndServiceId getActiveServiceInstanceCountBreakdown(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceId) {
    return instanceDashboardService.getActiveServiceInstanceCountBreakdown(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, getCurrentTime());
  }

  /*
  Returns a list of buildId and instance counts for various environments for given account+org+project+service
*/
  @Override
  public EnvBuildIdAndInstanceCountInfoList getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Map<String, List<BuildIdAndInstanceCount>> envIdToBuildMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    List<EnvBuildInstanceCount> envBuildInstanceCounts = instanceDashboardService.getEnvBuildInstanceCountByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, getCurrentTime());

    envBuildInstanceCounts.forEach(envBuildInstanceCount -> {
      final String envId = envBuildInstanceCount.getEnvIdentifier();
      final String envName = envBuildInstanceCount.getEnvName();
      final String buildId = envBuildInstanceCount.getTag();
      final int count = envBuildInstanceCount.getCount();
      envIdToBuildMap.putIfAbsent(envId, new ArrayList<>());

      BuildIdAndInstanceCount buildIdAndInstanceCount =
          BuildIdAndInstanceCount.builder().buildId(buildId).count(count).build();
      envIdToBuildMap.get(envId).add(buildIdAndInstanceCount);

      envIdToEnvNameMap.putIfAbsent(envId, envName);
    });

    List<EnvBuildIdAndInstanceCountInfo> envBuildIdAndInstanceCountInfoList = new ArrayList<>();
    envIdToBuildMap.forEach((envId, buildIdAndInstanceCountList) -> {
      EnvBuildIdAndInstanceCountInfo envBuildIdAndInstanceCountInfo =
          EnvBuildIdAndInstanceCountInfo.builder()
              .envId(envId)
              .envName(envIdToEnvNameMap.getOrDefault(envId, ""))
              .buildIdAndInstanceCountList(buildIdAndInstanceCountList)
              .build();
      envBuildIdAndInstanceCountInfoList.add(envBuildIdAndInstanceCountInfo);
    });

    return EnvBuildIdAndInstanceCountInfoList.builder()
        .envBuildIdAndInstanceCountInfoList(envBuildIdAndInstanceCountInfoList)
        .build();
  }

  @Override
  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentList(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String environmentId, String envGrpId) {
    return getInstanceGroupedByEnvironmentListForOrgAndAccountLevel(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, environmentId, envGrpId);
  }

  private InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentListForOrgAndAccountLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, String environmentId,
      String envGrpId) {
    IdentifierRef serviceIdRef =
        DashboardServiceHelper.getIdentifierRef(serviceId, accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(
        accountIdentifier, serviceIdRef.getOrgIdentifier(), serviceIdRef.getProjectIdentifier(), serviceId);
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList =
        instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(accountIdentifier, orgIdentifier,
            projectIdentifier, environmentId, serviceId, null, gitOpsInfo.isGitOps, false, null, false,
            gitOpsInfo.isGitOpsMergeEnabled);

    updateNullArtifact(activeServiceInstanceInfoList);

    DashboardServiceHelper.sortActiveServiceInstanceInfoWithEnvTypeList(activeServiceInstanceInfoList);

    List<IdentifierRef> envIdRefList = new ArrayList<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo
        -> envIdRefList.add(
            DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier())));
    boolean useScopeInfoEnvGrp =
        featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage = useScopeInfoEnvGrp
        ? getEnvironmentGroupEntities(scopeInfo)
        : getEnvironmentGroupEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    List<Environment> environments;

    environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIdRefList), true);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();
    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    activeServiceInstanceInfoList = filterNonDeletedEnvs(activeServiceInstanceInfoList, environments, scopeInfoMap);

    return DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(accountIdentifier, envGrpId,
        activeServiceInstanceInfoList, gitOpsInfo.isGitOps, environmentGroupEntitiesPage, identifierRefToEnvMap,
        scopeInfo, gitOpsInfo.isGitOpsMergeEnabled);
  }

  private void updateNullArtifact(List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList) {
    for (ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType : activeServiceInstanceInfoList) {
      if (isNull(activeServiceInstanceInfoWithEnvType.getDisplayName())) {
        activeServiceInstanceInfoWithEnvType.setDisplayName(EMPTY_ARTIFACT);
      }
    }
  }

  private String convertIdToRef(String accountId, String orgId, String projectId, String id) {
    return IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, id).buildScopedIdentifier();
  }

  private IdentifierRef convertIdToIdentifierRef(String accountId, String orgId, String projectId, String id) {
    return IdentifierRef.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .identifier(id)
        .build();
  }

  private List<ActiveServiceInstanceInfoWithEnvType> filterNonDeletedEnvs(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, List<Environment> environments,
      Map<String, Optional<ScopeInfo>> scopeInfoMap) {
    List<String> envIds = new ArrayList<>();
    HashMap<String, Environment> envRefEnvMap = new HashMap<>();
    environments.forEach(environment -> {
      ScopeInfo scopeInfo = scopeInfoMap.get(environment.getParentUniqueId()).get();
      String envRef = convertIdToRef(environment.getAccountId(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), environment.getIdentifier());
      envRefEnvMap.put(envRef, environment);
      envIds.add(envRef);
    });
    List<ActiveServiceInstanceInfoWithEnvType> updatedActiveServiceInstanceInfoList = new ArrayList<>();

    for (ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType : activeServiceInstanceInfoList) {
      if (envIds.contains(activeServiceInstanceInfoWithEnvType.getEnvIdentifier())) {
        activeServiceInstanceInfoWithEnvType.setEnvName(
            envRefEnvMap.get(activeServiceInstanceInfoWithEnvType.getEnvIdentifier()).getName());
        activeServiceInstanceInfoWithEnvType.setEnvType(
            envRefEnvMap.get(activeServiceInstanceInfoWithEnvType.getEnvIdentifier()).getType());
        updatedActiveServiceInstanceInfoList.add(activeServiceInstanceInfoWithEnvType);
      }
    }
    return updatedActiveServiceInstanceInfoList;
  }

  @Override
  public InstanceGroupedOnArtifactList getInstanceGroupedOnArtifactList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, String environmentId, String envGrpId, String displayName,
      boolean filterOnArtifact) {
    return getInstanceGroupedOnArtifactListForOrgAndAccountLevel(accountIdentifier, orgIdentifier, projectIdentifier,
        serviceId, environmentId, envGrpId, displayName, filterOnArtifact);
  }

  private InstanceGroupedOnArtifactList getInstanceGroupedOnArtifactListForOrgAndAccountLevel(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String environmentId, String envGrpId,
      String displayName, boolean filterOnArtifact) {
    IdentifierRef serviceIdRef =
        DashboardServiceHelper.getIdentifierRef(serviceId, accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(
        accountIdentifier, serviceIdRef.getOrgIdentifier(), serviceIdRef.getProjectIdentifier(), serviceId);

    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList = new ArrayList<>();
    if (filterOnArtifact && isEmpty(displayName)) {
      activeServiceInstanceInfoList.addAll(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentId, serviceId, EMPTY_ARTIFACT,
          gitOpsInfo.isGitOps, filterOnArtifact, null, false, gitOpsInfo.isGitOpsMergeEnabled));

      activeServiceInstanceInfoList.addAll(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentId, serviceId, null, gitOpsInfo.isGitOps,
          filterOnArtifact, null, false, gitOpsInfo.isGitOpsMergeEnabled));

    } else {
      activeServiceInstanceInfoList = instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentId, serviceId, displayName,
          gitOpsInfo.isGitOps, filterOnArtifact, null, false, gitOpsInfo.isGitOpsMergeEnabled);
    }

    updateNullArtifact(activeServiceInstanceInfoList);

    DashboardServiceHelper.sortActiveServiceInstanceInfoWithEnvTypeList(activeServiceInstanceInfoList);

    List<IdentifierRef> envIdRefList = new ArrayList<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo
        -> envIdRefList.add(
            DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier())));
    boolean useScopeInfoEnvGrp =
        featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage = useScopeInfoEnvGrp
        ? getEnvironmentGroupEntities(scopeInfo)
        : getEnvironmentGroupEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    List<Environment> environments;

    environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIdRefList), true);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();
    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    activeServiceInstanceInfoList = filterNonDeletedEnvs(activeServiceInstanceInfoList, environments, scopeInfoMap);

    return DashboardServiceHelper.getInstanceGroupedByArtifactListHelperV2(accountIdentifier,
        activeServiceInstanceInfoList, gitOpsInfo.isGitOps, environmentGroupEntitiesPage, envGrpId,
        identifierRefToEnvMap, scopeInfo, gitOpsInfo.isGitOpsMergeEnabled);
  }

  @Override
  public InstanceGroupedOnChartVersionList getInstanceGroupedOnChartVersionList(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String environmentId, String envGrpId,
      String chartVersion, boolean filterOnChartVersion) {
    return getInstanceGroupedOnChartVersionListForOrgAndAccountLevel(accountIdentifier, orgIdentifier,
        projectIdentifier, serviceId, environmentId, envGrpId, chartVersion, filterOnChartVersion);
  }

  private InstanceGroupedOnChartVersionList getInstanceGroupedOnChartVersionListForOrgAndAccountLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, String environmentId,
      String envGrpId, String chartVersion, boolean filterOnChartVersion) {
    IdentifierRef serviceIdRef =
        DashboardServiceHelper.getIdentifierRef(serviceId, accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(
        accountIdentifier, serviceIdRef.getOrgIdentifier(), serviceIdRef.getProjectIdentifier(), serviceId);

    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList =
        instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(accountIdentifier, orgIdentifier,
            projectIdentifier, environmentId, serviceId, null, gitOpsInfo.isGitOps, false, chartVersion,
            filterOnChartVersion, gitOpsInfo.isGitOpsMergeEnabled);

    updateNullArtifact(activeServiceInstanceInfoList);

    DashboardServiceHelper.sortActiveServiceInstanceInfoWithEnvTypeList(activeServiceInstanceInfoList);

    List<IdentifierRef> envIdRefList = new ArrayList<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo
        -> envIdRefList.add(
            DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier())));
    boolean useScopeInfoEnvGrp =
        featureFlagService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage = useScopeInfoEnvGrp
        ? getEnvironmentGroupEntities(scopeInfo)
        : getEnvironmentGroupEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    List<Environment> environments;

    environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIdRefList), true);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();
    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    activeServiceInstanceInfoList = filterNonDeletedEnvs(activeServiceInstanceInfoList, environments, scopeInfoMap);

    return DashboardServiceHelper.getInstanceGroupedByChartVersionListHelperV2(accountIdentifier,
        activeServiceInstanceInfoList, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled,
        environmentGroupEntitiesPage, envGrpId, identifierRefToEnvMap, scopeInfo);
  }

  private Page<EnvironmentGroupEntity> getEnvironmentGroupEntities(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = environmentGroupService.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "", "", null, false);
    return environmentGroupService.list(criteria, Pageable.unpaged());
  }

  private Page<EnvironmentGroupEntity> getEnvironmentGroupEntities(ScopeInfo scopeInfo) {
    Criteria criteria = environmentGroupService.formCriteria(scopeInfo, false, "", "", null, false);
    return environmentGroupService.list(criteria, Pageable.unpaged());
  }

  @Override
  public InstanceGroupedByServiceList.InstanceGroupedByService getInstanceGroupedByArtifactList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList;
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    activeServiceInstanceInfoList = instanceDashboardService.getActiveServiceInstanceInfo(accountIdentifier,
        orgIdentifier, projectIdentifier, null, serviceRef, null, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    InstanceGroupedByServiceList instanceGroupedByServiceList =
        getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
    return getInstanceGroupedByService(instanceGroupedByServiceList);
  }

  private InstanceGroupedByServiceList.InstanceGroupedByService getInstanceGroupedByService(
      InstanceGroupedByServiceList instanceGroupedByServiceList) {
    if (EmptyPredicate.isNotEmpty(instanceGroupedByServiceList.getInstanceGroupedByServiceList())) {
      return instanceGroupedByServiceList.getInstanceGroupedByServiceList().get(0);
    } else {
      return InstanceGroupedByServiceList.InstanceGroupedByService.builder()
          .instanceGroupedByArtifactList(new ArrayList<>())
          .build();
    }
  }

  @Override
  public InstanceGroupedByServiceList getInstanceGroupedByServiceList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier, String buildIdentifier) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList =
        instanceDashboardService.getActiveServiceInstanceInfo(accountIdentifier, orgIdentifier, projectIdentifier,
            envIdentifier, serviceRef, buildIdentifier, false, false);
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceGitOpsInfoList =
        instanceDashboardService.getActiveServiceInstanceInfo(accountIdentifier, orgIdentifier, projectIdentifier,
            envIdentifier, serviceRef, buildIdentifier, true, false);
    activeServiceInstanceInfoList.addAll(activeServiceInstanceGitOpsInfoList);

    return getInstanceGroupedByServiceListHelperForOrgAndAccountLevel(
        accountIdentifier, orgIdentifier, projectIdentifier, activeServiceInstanceInfoList);
  }

  public InstanceGroupedByServiceList getInstanceGroupedByServiceListHelper(
      List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList) {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<String, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<String, String> clusterIdToAgentIdMap = new HashMap<>();
    Map<String, String> serviceIdToLatestBuildMap = new HashMap<>();
    Map<String, Long> serviceIdToLastDeployed = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String serviceId = activeServiceInstanceInfo.getServiceIdentifier();
      final String buildId = activeServiceInstanceInfo.getTag();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      if (serviceId == null || envId == null || lastDeployedAt == null) {
        return;
      }

      final String serviceName = activeServiceInstanceInfo.getServiceName();
      final String infraIdentifier = activeServiceInstanceInfo.getInfraIdentifier();
      final String infraName = activeServiceInstanceInfo.getInfraName();
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String artifactPath =
          DashboardServiceHelper.getArtifactPathFromDisplayName(activeServiceInstanceInfo.getDisplayName());
      final Integer count = activeServiceInstanceInfo.getCount();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, buildId);
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();

      if ((!serviceIdToLastDeployed.containsKey(serviceId))
          || (lastDeployedAt > serviceIdToLastDeployed.get(serviceId))) {
        serviceIdToLatestBuildMap.put(serviceId, displayName);
        serviceIdToLastDeployed.put(serviceId, lastDeployedAt);
      }

      serviceBuildEnvInfraMap.putIfAbsent(serviceId, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceId).putIfAbsent(displayName, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceId)
          .get(displayName)
          .putIfAbsent(envId, new MutablePair<>(new HashMap<>(), new HashMap<>()));

      if (clusterIdentifier != null) {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceId).get(displayName).get(envId).getValue();
        map.putIfAbsent(clusterIdentifier, new ArrayList<>());
        map.get(clusterIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        clusterIdToAgentIdMap.putIfAbsent(clusterIdentifier, agentIdentifier);
      } else {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceId).get(displayName).get(envId).getKey();
        map.putIfAbsent(infraIdentifier, new ArrayList<>());
        map.get(infraIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        infraIdToInfraNameMap.putIfAbsent(infraIdentifier, infraName);
      }

      serviceIdToServiceNameMap.putIfAbsent(serviceId, serviceName);
      envIdToEnvNameMap.putIfAbsent(envId, envName);
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList =
        groupedByServices(serviceBuildEnvInfraMap, envIdToEnvNameMap, infraIdToInfraNameMap, serviceIdToServiceNameMap,
            clusterIdToAgentIdMap, serviceIdToLatestBuildMap, artifactToArtifactLinkMap);

    return InstanceGroupedByServiceList.builder().instanceGroupedByServiceList(instanceGroupedByServiceList).build();
  }

  public InstanceGroupedByServiceList getInstanceGroupedByServiceListHelperForOrgAndAccountLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList) {
    Map<IdentifierRef,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<IdentifierRef, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<String, String> clusterIdToAgentIdMap = new HashMap<>();
    Map<IdentifierRef, String> serviceIdToLatestBuildMap = new HashMap<>();
    Map<IdentifierRef, ActiveServiceInstanceInfoV2> serviceIdInstanceInfoMap = new HashMap<>();
    Map<IdentifierRef, Long> serviceIdToLastDeployed = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String buildId = activeServiceInstanceInfo.getTag();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      if (activeServiceInstanceInfo.getServiceIdentifier() == null || envId == null || lastDeployedAt == null) {
        return;
      }
      final IdentifierRef serviceIdRef =
          DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getServiceIdentifier(), accountIdentifier,
              activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier());

      final String serviceName = activeServiceInstanceInfo.getServiceName();
      final String infraIdentifier = activeServiceInstanceInfo.getInfraIdentifier();
      final String infraName = activeServiceInstanceInfo.getInfraName();
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String artifactPath =
          DashboardServiceHelper.getArtifactPathFromDisplayName(activeServiceInstanceInfo.getDisplayName());
      final Integer count = activeServiceInstanceInfo.getCount();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, buildId);
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();

      if ((!serviceIdToLastDeployed.containsKey(serviceIdRef))
          || (lastDeployedAt > serviceIdToLastDeployed.get(serviceIdRef))) {
        serviceIdToLatestBuildMap.put(serviceIdRef, displayName);
        serviceIdToLastDeployed.put(serviceIdRef, lastDeployedAt);
      }

      serviceBuildEnvInfraMap.putIfAbsent(serviceIdRef, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceIdRef).putIfAbsent(displayName, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceIdRef)
          .get(displayName)
          .putIfAbsent(envId, new MutablePair<>(new HashMap<>(), new HashMap<>()));

      if (clusterIdentifier != null) {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceIdRef).get(displayName).get(envId).getValue();
        map.putIfAbsent(clusterIdentifier, new ArrayList<>());
        map.get(clusterIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        clusterIdToAgentIdMap.putIfAbsent(clusterIdentifier, agentIdentifier);
      } else {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceIdRef).get(displayName).get(envId).getKey();
        map.putIfAbsent(infraIdentifier, new ArrayList<>());
        map.get(infraIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        infraIdToInfraNameMap.putIfAbsent(infraIdentifier, infraName);
      }

      serviceIdToServiceNameMap.putIfAbsent(serviceIdRef, serviceName);
      serviceIdInstanceInfoMap.putIfAbsent(serviceIdRef, activeServiceInstanceInfo);
      envIdToEnvNameMap.putIfAbsent(envId, envName);
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList = groupedByServicesV2(
        serviceBuildEnvInfraMap, envIdToEnvNameMap, infraIdToInfraNameMap, serviceIdToServiceNameMap,
        clusterIdToAgentIdMap, serviceIdToLatestBuildMap, serviceIdInstanceInfoMap, artifactToArtifactLinkMap);

    return InstanceGroupedByServiceList.builder().instanceGroupedByServiceList(instanceGroupedByServiceList).build();
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByService> groupedByServices(
      Map<String,
          Map<String,
              Map<String,
                  Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                      Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
          serviceBuildEnvInfraMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> infraIdToInfraNameMap,
      Map<String, String> serviceIdToServiceNameMap, Map<String, String> clusterIdAgentIdMap,
      Map<String, String> serviceIdToLatestBuildMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList = new ArrayList<>();

    for (Map.Entry<String,
             Map<String,
                 Map<String,
                     Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                         Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>> entry3 :
        serviceBuildEnvInfraMap.entrySet()) {
      String serviceId = entry3.getKey();
      String serviceName = serviceIdToServiceNameMap.get(serviceId);

      List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> instanceGroupedByArtifactList =
          groupByArtifact(entry3.getValue(), serviceIdToLatestBuildMap.get(serviceId), infraIdToInfraNameMap,
              envIdToEnvNameMap, clusterIdAgentIdMap, artifactToArtifactLinkMap);

      instanceGroupedByServiceList.add(InstanceGroupedByServiceList.InstanceGroupedByService.builder()
                                           .serviceId(serviceId)
                                           .serviceName(serviceName)
                                           .lastDeployedAt(instanceGroupedByArtifactList.get(0).getLastDeployedAt())
                                           .instanceGroupedByArtifactList(instanceGroupedByArtifactList)
                                           .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are grouped

    Collections.sort(
        instanceGroupedByServiceList, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByService>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByService o1,
              InstanceGroupedByServiceList.InstanceGroupedByService o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByServiceList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByService> groupedByServicesV2(
      Map<IdentifierRef,
          Map<String,
              Map<String,
                  Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                      Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
          serviceBuildEnvInfraMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> infraIdToInfraNameMap,
      Map<IdentifierRef, String> serviceIdToServiceNameMap, Map<String, String> clusterIdAgentIdMap,
      Map<IdentifierRef, String> serviceIdToLatestBuildMap,
      Map<IdentifierRef, ActiveServiceInstanceInfoV2> serviceIdInstanceInfoMap,
      Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList = new ArrayList<>();

    for (Map.Entry<IdentifierRef,
             Map<String,
                 Map<String,
                     Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                         Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>> entry3 :
        serviceBuildEnvInfraMap.entrySet()) {
      IdentifierRef serviceIdRef = entry3.getKey();
      String serviceName = serviceIdToServiceNameMap.get(serviceIdRef);

      List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> instanceGroupedByArtifactList =
          groupByArtifact(entry3.getValue(), serviceIdToLatestBuildMap.get(serviceIdRef), infraIdToInfraNameMap,
              envIdToEnvNameMap, clusterIdAgentIdMap, artifactToArtifactLinkMap);

      instanceGroupedByServiceList.add(
          InstanceGroupedByServiceList.InstanceGroupedByService.builder()
              .serviceId(serviceIdRef.buildScopedIdentifier())
              .serviceName(serviceName)
              .lastDeployedAt(instanceGroupedByArtifactList.get(0).getLastDeployedAt())
              .instanceGroupedByArtifactList(instanceGroupedByArtifactList)
              .orgIdentifier(serviceIdInstanceInfoMap.get(serviceIdRef).getOrgIdentifier())
              .projectIdentifier(serviceIdInstanceInfoMap.get(serviceIdRef).getProjectIdentifier())
              .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are grouped

    Collections.sort(
        instanceGroupedByServiceList, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByService>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByService o1,
              InstanceGroupedByServiceList.InstanceGroupedByService o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByServiceList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> groupByArtifact(
      Map<String,
          Map<String,
              Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                  Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>
          artifactToEnvMap,
      String latestBuild, Map<String, String> infraIdToInfraNameMap, Map<String, String> envIdToEnvNameMap,
      Map<String, String> clusterIdAgentIdMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> instanceGroupedByArtifactList = new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                     Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>> entry :
        artifactToEnvMap.entrySet()) {
      String displayName = entry.getKey();
      String artifactPath = DashboardServiceHelper.getArtifactPathFromDisplayName(displayName);
      String buildId = DashboardServiceHelper.getTagFromDisplayName(displayName);

      List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList =
          groupByEnvironment(entry.getValue(), infraIdToInfraNameMap, envIdToEnvNameMap, clusterIdAgentIdMap);

      instanceGroupedByArtifactList.add(InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
                                            .artifactVersion(buildId)
                                            .artifactPath(artifactPath)
                                            .artifact(displayName)
                                            .artifactLink(artifactToArtifactLinkMap.get(displayName))
                                            .lastDeployedAt(instanceGroupedByEnvironmentList.get(0).getLastDeployedAt())
                                            .latest(checkEquality(latestBuild, displayName))
                                            .instanceGroupedByEnvironmentList(instanceGroupedByEnvironmentList)
                                            .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByArtifactList;
  }

  private boolean checkEquality(String a, String b) {
    if (a == null && b == null) {
      return true;
    } else if (a == null) {
      return false;
    } else if (b == null) {
      return false;
    }
    return a.equals(b);
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> groupByEnvironment(
      Map<String,
          Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
              Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envToInfraClusterMap,
      Map<String, String> infraIdToInfraNameMap, Map<String, String> envIdToEnvNameMap,
      Map<String, String> clusterIdAgentIdMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList =
        new ArrayList<>();

    for (Map.Entry<String,
             Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                 Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> entry1 :
        envToInfraClusterMap.entrySet()) {
      String envId = entry1.getKey();
      String envName = envIdToEnvNameMap.get(envId);

      List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByInfrastructureList =
          groupedByInfrastructure(entry1.getValue().getKey(), infraIdToInfraNameMap, false);
      List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByClusterList =
          groupedByInfrastructure(entry1.getValue().getValue(), clusterIdAgentIdMap, true);

      // fetch last deployed time by taking maximum or latest time from all executions that are grouped

      Long lastDeployedAt = 0l;

      if (EmptyPredicate.isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = Math.max(instanceGroupedByInfrastructureList.get(0).getLastDeployedAt(), lastDeployedAt);
      }

      if (EmptyPredicate.isNotEmpty(instanceGroupedByClusterList)) {
        lastDeployedAt = Math.max(instanceGroupedByClusterList.get(0).getLastDeployedAt(), lastDeployedAt);
      }

      instanceGroupedByEnvironmentList.add(InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
                                               .envId(envId)
                                               .envName(envName)
                                               .lastDeployedAt(lastDeployedAt)
                                               .instanceGroupedByClusterList(instanceGroupedByClusterList)
                                               .instanceGroupedByInfraList(instanceGroupedByInfrastructureList)
                                               .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(instanceGroupedByEnvironmentList,
        new Comparator<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByEnvironmentList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> groupedByInfrastructure(
      Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraToPipelineExecutionMap,
      Map<String, String> infraIdToInfraNameMap, boolean isGitOps) {
    List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByInfrastructureList =
        new ArrayList<>();

    for (Map.Entry<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> entry2 :
        infraToPipelineExecutionMap.entrySet()) {
      String infraId = entry2.getKey();
      String infraName = infraIdToInfraNameMap.get(infraId);

      List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> pipelineExecutions = entry2.getValue();

      pipelineExecutions = groupByPipelineExecution(pipelineExecutions);

      // sort based on last deployed time generated by taking maximum or latest time from all executions that are
      // grouped

      Collections.sort(
          pipelineExecutions, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>() {
            public int compare(InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution o1,
                InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution o2) {
              return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
            }
          });

      if (!isGitOps) {
        instanceGroupedByInfrastructureList.add(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
                                                    .infraName(infraName)
                                                    .infraIdentifier(infraId)
                                                    .lastDeployedAt(pipelineExecutions.get(0).getLastDeployedAt())
                                                    .instanceGroupedByPipelineExecutionList(pipelineExecutions)
                                                    .build());
      } else {
        instanceGroupedByInfrastructureList.add(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
                                                    .agentIdentifier(infraName)
                                                    .clusterIdentifier(infraId)
                                                    .lastDeployedAt(pipelineExecutions.get(0).getLastDeployedAt())
                                                    .instanceGroupedByPipelineExecutionList(pipelineExecutions)
                                                    .build());
      }
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(instanceGroupedByInfrastructureList,
        new Comparator<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByInfrastructureList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> groupByPipelineExecution(
      List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> pipelineExecutions) {
    Map<String, InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> instanceGroupedByPipelineExecutionMap =
        new HashMap<>();

    for (InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution pipelineExecution : pipelineExecutions) {
      if (instanceGroupedByPipelineExecutionMap.containsKey(pipelineExecution.getLastPipelineExecutionId())) {
        InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution instanceGroupedByPipelineExecution =
            instanceGroupedByPipelineExecutionMap.get(pipelineExecution.getLastPipelineExecutionId());
        instanceGroupedByPipelineExecution.setCount(
            instanceGroupedByPipelineExecution.getCount() + pipelineExecution.getCount());
        if (pipelineExecution.getLastDeployedAt() > instanceGroupedByPipelineExecution.getLastDeployedAt()) {
          instanceGroupedByPipelineExecution.setLastDeployedAt(pipelineExecution.getLastDeployedAt());
        }
      } else {
        instanceGroupedByPipelineExecutionMap.put(pipelineExecution.getLastPipelineExecutionId(), pipelineExecution);
      }
    }
    return new ArrayList<>(instanceGroupedByPipelineExecutionMap.values());
  }

  @Override
  public EnvironmentGroupInstanceDetails getEnvironmentInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO,
      boolean returnDefaultSequence) {
    if (projectIdentifier == null) {
      return getEnvironmentInstanceDetailsForOrgAndAccountLevel(accountIdentifier, orgIdentifier, projectIdentifier,
          serviceIdentifier, environmentFilterPropertiesDTO, returnDefaultSequence);
    }
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<EnvironmentInstanceCountModel> environmentInstanceCounts =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceIdentifier, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    Set<String> envIds = new HashSet<>();
    Map<String, Integer> envToCountMap = new HashMap<>();

    DashboardServiceHelper.constructEnvironmentCountMap(environmentInstanceCounts, envToCountMap, envIds);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds, scopeInfo);

    List<Environment> environments = fetchEnvList(scopeInfo, envIds);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMap, envIdToEnvTypeMap, scopeInfoMap);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, true, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMap(
            artifactDeploymentDetails, envIdToEnvNameMap);
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetailsWithRevertInfo(
        artifactDeploymentDetailsMap.values()
            .stream()
            .filter(artifactDeploymentDetail
                -> EmptyPredicate.isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId()))
            .map(artifactDeploymentDetail -> artifactDeploymentDetail.getLastPipelineExecutionId())
            .collect(Collectors.toList()));
    List<String> pipelineExecutionIdsWhereRollbackOccurred = getPipelineExecutionsWhereRollbackOccurred(
        pipelineExecutionDetailsMap.values()
            .stream()
            .filter(servicePipelineWithRevertInfo
                -> EmptyPredicate.isNotEmpty(servicePipelineWithRevertInfo.getPipelineExecutionId()))
            .map(servicePipelineWithRevertInfo -> servicePipelineWithRevertInfo.getPipelineExecutionId())
            .collect(Collectors.toList()));
    EnvironmentGroupInstanceDetails environmentGroupInstanceDetails =
        DashboardServiceHelper.getEnvironmentInstanceDetailsFromMap(artifactDeploymentDetailsMap, envToCountMap,
            envIdToEnvNameMap, envIdToEnvTypeMap, environmentGroupEntities, environmentFilterPropertiesDTO,
            pipelineExecutionDetailsMap, pipelineExecutionIdsWhereRollbackOccurred, scopeInfo);

    if (returnDefaultSequence) {
      return environmentGroupInstanceDetails;
    }
    environmentGroupInstanceDetails.setEnvironmentGroupInstanceDetails(
        getCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier,
            environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails()));

    return environmentGroupInstanceDetails;
  }

  @Override
  public EnvironmentGroupInstanceDetails getEnvironmentInstanceDetailsViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO, boolean returnDefaultSequence) {
    if (projectIdentifier == null) {
      return getEnvironmentInstanceDetailsViaJooqForOrgAndAccountLevel(accountIdentifier, orgIdentifier,
          projectIdentifier, serviceIdentifier, environmentFilterPropertiesDTO, returnDefaultSequence);
    }
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<EnvironmentInstanceCountModel> environmentInstanceCounts =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceIdentifier, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    Set<String> envIds = new HashSet<>();
    Map<String, Integer> envToCountMap = new HashMap<>();

    DashboardServiceHelper.constructEnvironmentCountMap(environmentInstanceCounts, envToCountMap, envIds);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds, scopeInfo);

    List<Environment> environments = fetchEnvList(scopeInfo, envIds);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMap, envIdToEnvTypeMap, scopeInfoMap);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, true, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMap(
            artifactDeploymentDetails, envIdToEnvNameMap);
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap =
        getPipelineExecutionDetailsWithRevertInfoViaJooq(
            artifactDeploymentDetailsMap.values()
                .stream()
                .filter(artifactDeploymentDetail
                    -> EmptyPredicate.isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId()))
                .map(artifactDeploymentDetail -> artifactDeploymentDetail.getLastPipelineExecutionId())
                .collect(Collectors.toList()));
    List<String> pipelineExecutionIdsWhereRollbackOccurred = getPipelineExecutionsWhereRollbackOccurredViaJooq(
        pipelineExecutionDetailsMap.values()
            .stream()
            .filter(servicePipelineWithRevertInfo
                -> EmptyPredicate.isNotEmpty(servicePipelineWithRevertInfo.getPipelineExecutionId()))
            .map(servicePipelineWithRevertInfo -> servicePipelineWithRevertInfo.getPipelineExecutionId())
            .collect(Collectors.toList()));
    EnvironmentGroupInstanceDetails environmentGroupInstanceDetails =
        DashboardServiceHelper.getEnvironmentInstanceDetailsFromMap(artifactDeploymentDetailsMap, envToCountMap,
            envIdToEnvNameMap, envIdToEnvTypeMap, environmentGroupEntities, environmentFilterPropertiesDTO,
            pipelineExecutionDetailsMap, pipelineExecutionIdsWhereRollbackOccurred, scopeInfo);

    if (returnDefaultSequence) {
      return environmentGroupInstanceDetails;
    }
    environmentGroupInstanceDetails.setEnvironmentGroupInstanceDetails(
        getCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier,
            environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails()));

    return environmentGroupInstanceDetails;
  }

  private EnvironmentGroupInstanceDetails getEnvironmentInstanceDetailsViaJooqForOrgAndAccountLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO, boolean returnDefaultSequence) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<EnvironmentInstanceCountModel> environmentInstanceCounts =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceIdentifier, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    Map<IdentifierRef, Integer> envToCountMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> countScopeInfoMap = scopeResolverService.getScopeInfo(accountIdentifier,
        environmentInstanceCounts.stream()
            .map(EnvironmentInstanceCountModel::getParentUniqueId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
    DashboardServiceHelper.constructEnvironmentCountMapV2(
        accountIdentifier, environmentInstanceCounts, envToCountMap, countScopeInfoMap);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, true, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);
    Set<IdentifierRef> environmentsWithScopes =
        DashboardServiceHelper.constructEnvIdentifierRefList(accountIdentifier, artifactDeploymentDetails);
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);
    List<Environment> environments =
        fetchEnvListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);
    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    Map<IdentifierRef, ArtifactDeploymentDetail> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMapV2(
            accountIdentifier, artifactDeploymentDetails, identifierRefToEnvMap);

    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap =
        getPipelineExecutionDetailsWithRevertInfoViaJooq(
            artifactDeploymentDetailsMap.values()
                .stream()
                .filter(artifactDeploymentDetail
                    -> EmptyPredicate.isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId()))
                .map(artifactDeploymentDetail -> artifactDeploymentDetail.getLastPipelineExecutionId())
                .collect(Collectors.toList()));
    List<String> pipelineExecutionIdsWhereRollbackOccurred = getPipelineExecutionsWhereRollbackOccurredViaJooq(
        pipelineExecutionDetailsMap.values()
            .stream()
            .filter(servicePipelineWithRevertInfo
                -> EmptyPredicate.isNotEmpty(servicePipelineWithRevertInfo.getPipelineExecutionId()))
            .map(servicePipelineWithRevertInfo -> servicePipelineWithRevertInfo.getPipelineExecutionId())
            .collect(Collectors.toList()));
    EnvironmentGroupInstanceDetails environmentGroupInstanceDetails =
        DashboardServiceHelper.getEnvironmentInstanceDetailsFromMap(artifactDeploymentDetailsMap, envToCountMap,
            identifierRefToEnvMap, environmentGroupEntities, environmentFilterPropertiesDTO,
            pipelineExecutionDetailsMap, pipelineExecutionIdsWhereRollbackOccurred, scopeInfo);

    if (returnDefaultSequence) {
      return environmentGroupInstanceDetails;
    }
    environmentGroupInstanceDetails.setEnvironmentGroupInstanceDetails(
        getCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier,
            environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails()));

    return environmentGroupInstanceDetails;
  }

  private List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> getCustomSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ServiceSequence> serviceSequenceOptional = serviceSequenceService.get(scopeInfo, serviceIdentifier);
    ServiceSequence serviceSequence;
    if (!serviceSequenceOptional.isPresent()) {
      return environmentGroupInstanceDetailList;
    }
    serviceSequence = serviceSequenceOptional.get();

    if (!serviceSequence.isShouldUseCustomSequence() || isNull(serviceSequence.getCustomSequence())) {
      return environmentGroupInstanceDetailList;

    } else {
      CustomSequenceDTO sequenceDTO = serviceSequence.getCustomSequence();
      List<CustomSequenceDTO.EnvAndEnvGroupCard> envAndEnvGroupCardsCustom = sequenceDTO.getEnvAndEnvGroupCardList();

      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> newEnvironmentGroupInstanceDetailList =
          new ArrayList<>();

      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> appendListForEnvGrpNotPresentInSequence =
          new ArrayList<>();

      HashMap<String, EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> envGrpMapForListFromDB =
          new HashMap<>();

      HashMap<String, CustomSequenceDTO.EnvAndEnvGroupCard> envGrpMapForCustomSequence = new HashMap<>();

      envAndEnvGroupCardsCustom.forEach(envGroupDetail
          -> envGrpMapForCustomSequence.put(
              envGroupDetail.getIdentifier() + envGroupDetail.isEnvGroup(), envGroupDetail));

      environmentGroupInstanceDetailList.forEach(envGroupDetail
          -> envGrpMapForListFromDB.put(
              envGroupDetail.getId() + envGroupDetail.getIsEnvGroup().toString(), envGroupDetail));

      for (Map.Entry<String, EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> entry :
          envGrpMapForListFromDB.entrySet()) {
        if (!envGrpMapForCustomSequence.containsKey(entry.getKey())) {
          appendListForEnvGrpNotPresentInSequence.add(entry.getValue());
        }
      }

      envAndEnvGroupCardsCustom.forEach(envGroup
          -> filterNonDeletedEnvListForSequence(
              newEnvironmentGroupInstanceDetailList, envGrpMapForListFromDB, envGroup));

      appendListForEnvGrpNotPresentInSequence.addAll(newEnvironmentGroupInstanceDetailList);

      return appendListForEnvGrpNotPresentInSequence;
    }
  }

  private void filterNonDeletedEnvListForSequence(
      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> newEnvironmentGroupInstanceDetailList,
      HashMap<String, EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> envGrpMapForListFromDB,
      CustomSequenceDTO.EnvAndEnvGroupCard envGroup) {
    if (!isNull(envGrpMapForListFromDB.get(envGroup.getIdentifier() + envGroup.isEnvGroup()))) {
      newEnvironmentGroupInstanceDetailList.add(
          envGrpMapForListFromDB.get(envGroup.getIdentifier() + envGroup.isEnvGroup()));
    }
  }

  private CustomSequenceDTO getSequenceDTO(EnvironmentGroupInstanceDetails environmentGroupInstanceDetails) {
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList =
        environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails();
    List<CustomSequenceDTO.EnvAndEnvGroupCard> envAndEnvGroupCards = new ArrayList<>();

    environmentGroupInstanceDetailList.forEach(
        envGrpDetail -> envAndEnvGroupCards.add(createEnvAndEnvGroupCard(envGrpDetail, false)));
    return CustomSequenceDTO.builder().envAndEnvGroupCardList(envAndEnvGroupCards).build();
  }

  private CustomSequenceDTO.EnvAndEnvGroupCard createEnvAndEnvGroupCard(
      EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail envGrpDetail, boolean isNew) {
    return CustomSequenceDTO.EnvAndEnvGroupCard.builder()
        .isEnvGroup(envGrpDetail.getIsEnvGroup())
        .identifier(envGrpDetail.getId())
        .environmentTypes(envGrpDetail.getEnvironmentTypes())
        .isNew(isNew)
        .name(envGrpDetail.getName())
        .build();
  }

  @Override
  public ArtifactInstanceDetails getArtifactInstanceDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    if (projectIdentifier == null) {
      return getArtifactInstanceDetailsForOrgAndAccountLevel(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, scopeInfo);
    }
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, false, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);

    Set<String> envIds = new HashSet<>();

    Map<String, Map<String, ArtifactDeploymentDetail>> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructArtifactToLastDeploymentMap(artifactDeploymentDetails, envIds);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds, scopeInfo);

    List<Environment> environments = fetchEnvList(scopeInfo, envIds);

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMap, envIdToEnvTypeMap, scopeInfoMap);
    Map<String, List<ArtifactDeploymentDetail>> envToArtifactMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentListMap(
            artifactDeploymentDetails, envIdToEnvNameMap);

    return DashboardServiceHelper.getArtifactInstanceDetailsFromMap(artifactDeploymentDetailsMap, envIdToEnvNameMap,
        envIdToEnvTypeMap, environmentGroupEntities, envToArtifactMap,
        DashboardServiceHelper.constructArtifactToArtifactLinkMap(artifactDeploymentDetails), scopeInfo);
  }

  @Override
  public ChartVersionInstanceDetails getChartVersionInstanceDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    if (projectIdentifier == null) {
      return getChartVersionInstanceDetailsForOrgAndAccountLevel(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    }

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, false, gitOpsInfo.isGitOps, true, gitOpsInfo.isGitOpsMergeEnabled);

    Set<String> envIds = DashboardServiceHelper.constructEnvIdsList(artifactDeploymentDetails);

    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds, scopeInfo);

    List<Environment> environments = fetchEnvList(scopeInfo, envIds);

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMap, envIdToEnvTypeMap, scopeInfoMap);
    Map<String, List<ArtifactDeploymentDetail>> envToArtifactMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentListMap(
            artifactDeploymentDetails, envIdToEnvNameMap);

    return DashboardServiceHelper.getChartVersionInstanceDetailsFromMap(
        envIdToEnvNameMap, envIdToEnvTypeMap, environmentGroupEntities, envToArtifactMap, scopeInfo);
  }

  private ArtifactInstanceDetails getArtifactInstanceDetailsForOrgAndAccountLevel(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, ScopeInfo scopeInfo) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, false, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);

    Set<IdentifierRef> environmentsWithScopes =
        DashboardServiceHelper.constructEnvIdentifierRefList(accountIdentifier, artifactDeploymentDetails);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);
    List<Environment> environments =
        fetchEnvListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();
    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    Map<IdentifierRef, List<ArtifactDeploymentDetail>> identifierRefToArtifactMap =
        DashboardServiceHelper.constructEnvironmentRefToArtifactDeploymentListMap(
            accountIdentifier, artifactDeploymentDetails, identifierRefToEnvMap);

    return DashboardServiceHelper.getArtifactInstanceDetailsFromMapV2(identifierRefToEnvMap, environmentGroupEntities,
        identifierRefToArtifactMap,
        DashboardServiceHelper.constructArtifactToArtifactLinkMap(artifactDeploymentDetails), scopeInfo);
  }

  private ChartVersionInstanceDetails getChartVersionInstanceDetailsForOrgAndAccountLevel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, false, gitOpsInfo.isGitOps, true, gitOpsInfo.isGitOpsMergeEnabled);

    Set<IdentifierRef> environmentsWithScopes =
        DashboardServiceHelper.constructEnvIdentifierRefList(accountIdentifier, artifactDeploymentDetails);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);

    List<Environment> environments =
        fetchEnvListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);

    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);
    Map<IdentifierRef, List<ArtifactDeploymentDetail>> identifierRefToArtifactMap =
        DashboardServiceHelper.constructEnvironmentRefToArtifactDeploymentListMap(
            accountIdentifier, artifactDeploymentDetails, identifierRefToEnvMap);
    return DashboardServiceHelper.getChartVersionInstanceDetailsFromMap(
        environmentGroupEntities, identifierRefToArtifactMap, identifierRefToEnvMap);
  }

  private EnvironmentGroupInstanceDetails getEnvironmentInstanceDetailsForOrgAndAccountLevel(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO, boolean returnDefaultSequence) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<EnvironmentInstanceCountModel> environmentInstanceCounts =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceIdentifier, gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    Map<IdentifierRef, Integer> envToCountMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> countScopeInfoMap = scopeResolverService.getScopeInfo(accountIdentifier,
        environmentInstanceCounts.stream()
            .map(EnvironmentInstanceCountModel::getParentUniqueId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
    DashboardServiceHelper.constructEnvironmentCountMapV2(
        accountIdentifier, environmentInstanceCounts, envToCountMap, countScopeInfoMap);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstance(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, true, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);
    Set<IdentifierRef> environmentsWithScopes =
        DashboardServiceHelper.constructEnvIdentifierRefList(accountIdentifier, artifactDeploymentDetails);
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);
    List<Environment> environments =
        fetchEnvListV2(accountIdentifier, orgIdentifier, projectIdentifier, environmentsWithScopes);
    Map<IdentifierRef, Environment> identifierRefToEnvMap = new HashMap<>();

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolverService.getScopeInfo(
        accountIdentifier, environments.stream().map(Environment::getParentUniqueId).collect(Collectors.toSet()));

    DashboardServiceHelper.constructIdentifierRefToEnvMap(environments, identifierRefToEnvMap, scopeInfoMap);

    Map<IdentifierRef, ArtifactDeploymentDetail> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMapV2(
            accountIdentifier, artifactDeploymentDetails, identifierRefToEnvMap);
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetailsWithRevertInfo(
        artifactDeploymentDetailsMap.values()
            .stream()
            .filter(artifactDeploymentDetail
                -> EmptyPredicate.isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId()))
            .map(artifactDeploymentDetail -> artifactDeploymentDetail.getLastPipelineExecutionId())
            .collect(Collectors.toList()));
    List<String> pipelineExecutionIdsWhereRollbackOccurred = getPipelineExecutionsWhereRollbackOccurred(
        pipelineExecutionDetailsMap.values()
            .stream()
            .filter(servicePipelineWithRevertInfo
                -> EmptyPredicate.isNotEmpty(servicePipelineWithRevertInfo.getPipelineExecutionId()))
            .map(servicePipelineWithRevertInfo -> servicePipelineWithRevertInfo.getPipelineExecutionId())
            .collect(Collectors.toList()));
    EnvironmentGroupInstanceDetails environmentGroupInstanceDetails =
        DashboardServiceHelper.getEnvironmentInstanceDetailsFromMap(artifactDeploymentDetailsMap, envToCountMap,
            identifierRefToEnvMap, environmentGroupEntities, environmentFilterPropertiesDTO,
            pipelineExecutionDetailsMap, pipelineExecutionIdsWhereRollbackOccurred, scopeInfo);

    if (returnDefaultSequence) {
      return environmentGroupInstanceDetails;
    }
    environmentGroupInstanceDetails.setEnvironmentGroupInstanceDetails(
        getCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier,
            environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails()));

    return environmentGroupInstanceDetails;
  }

  private List<EnvironmentGroupEntity> fetchEnvGrpList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<String> envIds, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage = useScopeInfo
        ? getEnvironmentGroupEntities(scopeInfo)
        : getEnvironmentGroupEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    List<EnvironmentGroupEntity> environmentGroupEntities = null;

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (EmptyPredicate.isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          envIds.addAll(
              environmentGroupEntity.getEnvIdentifiers()
                  .stream()
                  .map(envId
                      -> convertIdToRef(
                          useScopeInfo ? scopeInfo.getAccountIdentifier() : environmentGroupEntity.getAccountId(),
                          useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentGroupEntity.getOrgIdentifier(),
                          useScopeInfo ? scopeInfo.getProjectIdentifier()
                                       : environmentGroupEntity.getProjectIdentifier(),
                          envId))
                  .collect(Collectors.toList()));
        }
      }
    }
    return environmentGroupEntities;
  }

  private List<EnvironmentGroupEntity> fetchEnvGrpListV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<IdentifierRef> envIdentifierRefSet) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage = getEnvironmentGroupEntities(scopeInfo);

    List<EnvironmentGroupEntity> environmentGroupEntities = null;
    Set<IdentifierRef> mutableEnvIdentifierRefSet = new HashSet<>(envIdentifierRefSet);
    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (EmptyPredicate.isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          mutableEnvIdentifierRefSet.addAll(
              environmentGroupEntity.getEnvIdentifiers()
                  .stream()
                  .map(envId
                      -> convertIdToIdentifierRef(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
                          scopeInfo.getProjectIdentifier(), envId))
                  .collect(Collectors.toSet()));
        }
      }
    }
    envIdentifierRefSet.clear();
    envIdentifierRefSet.addAll(mutableEnvIdentifierRefSet);
    return environmentGroupEntities;
  }

  private List<Environment> fetchEnvList(ScopeInfo scopeInfo, Set<String> envIds) {
    return environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(scopeInfo, new ArrayList<>(envIds));
  }

  private List<Environment> fetchEnvListV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Set<IdentifierRef> envsWithScopeSet) {
    return environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envsWithScopeSet), true);
  }

  @Override
  public OpenTaskDetails getOpenTasks(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, long startInterval) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    final List<String> STATUS_LIST =
        Arrays
            .asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE, ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALWAITING)
            .stream()
            .map(ExecutionStatus::name)
            .collect(Collectors.toList());
    String query = DashboardServiceHelper.buildOpenTaskQuery(serviceIdentifier, startInterval, scopeInfo);
    Map<String, String> pipelineExecutionIdToFailureInfoMap =
        getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    Map<String, ServicePipelineInfo> servicePipelineInfoMap = getPipelineExecutionDetailsInBatches(
        new ArrayList<>(pipelineExecutionIdToFailureInfoMap.keySet()), STATUS_LIST);
    List<ServicePipelineWithRevertInfo> servicePipelineInfoList = new ArrayList<>();
    if (isNotEmpty(servicePipelineInfoMap.values())) {
      servicePipelineInfoList.addAll(servicePipelineInfoMap.values()
                                         .stream()
                                         .map(servicePipelineInfo
                                             -> ServicePipelineWithRevertInfo.builder()
                                                    .name(servicePipelineInfo.getName())
                                                    .deployedById(servicePipelineInfo.getDeployedById())
                                                    .deployedByName(servicePipelineInfo.getDeployedByName())
                                                    .identifier(servicePipelineInfo.getIdentifier())
                                                    .pipelineExecutionId(servicePipelineInfo.getPipelineExecutionId())
                                                    .planExecutionId(servicePipelineInfo.getPlanExecutionId())
                                                    .lastExecutedAt(servicePipelineInfo.getLastExecutedAt())
                                                    .status(servicePipelineInfo.getStatus())
                                                    .failureDetail(pipelineExecutionIdToFailureInfoMap.getOrDefault(
                                                        servicePipelineInfo.getPipelineExecutionId(), ""))
                                                    .build())
                                         .collect(Collectors.toList()));
    }
    DashboardServiceHelper.sortServicePipelineInfoList(servicePipelineInfoList);
    return OpenTaskDetails.builder().pipelineDeploymentDetails(servicePipelineInfoList).build();
  }

  @Override
  public OpenTaskDetails getOpenTasksViaJooq(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, long startInterval) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    final List<String> STATUS_LIST =
        Arrays
            .asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE, ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALWAITING)
            .stream()
            .map(ExecutionStatus::name)
            .collect(Collectors.toList());
    Query query = DashboardServiceHelper.buildOpenTaskQueryViaJooq(
        serviceIdentifier, startInterval, dslContext.configuration(), scopeInfo);
    Map<String, String> pipelineExecutionIdToFailureInfoMap =
        getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    Map<String, ServicePipelineInfo> servicePipelineInfoMap =
        getPipelineExecutionDetailsViaJooq(new ArrayList<>(pipelineExecutionIdToFailureInfoMap.keySet()), STATUS_LIST);
    List<ServicePipelineWithRevertInfo> servicePipelineInfoList = new ArrayList<>();
    if (isNotEmpty(servicePipelineInfoMap.values())) {
      servicePipelineInfoList.addAll(servicePipelineInfoMap.values()
                                         .stream()
                                         .map(servicePipelineInfo
                                             -> ServicePipelineWithRevertInfo.builder()
                                                    .name(servicePipelineInfo.getName())
                                                    .deployedById(servicePipelineInfo.getDeployedById())
                                                    .deployedByName(servicePipelineInfo.getDeployedByName())
                                                    .identifier(servicePipelineInfo.getIdentifier())
                                                    .pipelineExecutionId(servicePipelineInfo.getPipelineExecutionId())
                                                    .planExecutionId(servicePipelineInfo.getPlanExecutionId())
                                                    .lastExecutedAt(servicePipelineInfo.getLastExecutedAt())
                                                    .status(servicePipelineInfo.getStatus())
                                                    .failureDetail(pipelineExecutionIdToFailureInfoMap.getOrDefault(
                                                        servicePipelineInfo.getPipelineExecutionId(), ""))
                                                    .build())
                                         .collect(Collectors.toList()));
    }
    DashboardServiceHelper.sortServicePipelineInfoList(servicePipelineInfoList);
    return OpenTaskDetails.builder().pipelineDeploymentDetails(servicePipelineInfoList).build();
  }

  @Override
  public List<String> getPipelineExecutionsWhereRollbackOccurred(List<String> pipelineExecutionIdList) {
    String query = DashboardServiceHelper.buildRollbackDurationQuery(pipelineExecutionIdList);
    return getPipelineExecutionIdFromServiceInfraInfo(query);
  }

  @Override
  public List<String> getPipelineExecutionsWhereRollbackOccurredViaJooq(List<String> pipelineExecutionIdList) {
    Query query =
        DashboardServiceHelper.buildRollbackDurationQueryViaJooq(pipelineExecutionIdList, dslContext.configuration());
    return getPipelineExecutionIdFromServiceInfraInfo(query);
  }

  private List<InstanceGroupedByArtifactList.InstanceGroupedByArtifact> groupedByArtifacts(
      Map<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> buildEnvInfraMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> buildIdToArtifactPathMap) {
    List<InstanceGroupedByArtifactList.InstanceGroupedByArtifact> instanceGroupedByArtifactList = new ArrayList<>();

    for (Map.Entry<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> entry :
        buildEnvInfraMap.entrySet()) {
      String buildId = entry.getKey();
      String artifactPath = buildIdToArtifactPathMap.get(buildId);
      Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>> envInfraMap = entry.getValue();
      List<InstanceGroupedByArtifactList.InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList =
          new ArrayList<>();

      for (Map.Entry<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>> entry1 :
          envInfraMap.entrySet()) {
        String envId = entry1.getKey();
        String envName = envIdToEnvNameMap.get(envId);

        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceList = entry1.getValue();
        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
            instanceList.stream().filter(e -> e.getInfraIdentifier() != null).collect(Collectors.toList());
        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceGroupedByClusterList =
            instanceList.stream().filter(e -> e.getClusterIdentifier() != null).collect(Collectors.toList());

        instanceGroupedByEnvironmentList.add(InstanceGroupedByArtifactList.InstanceGroupedByEnvironment.builder()
                                                 .envId(envId)
                                                 .envName(envName)
                                                 .instanceGroupedByClusterList(instanceGroupedByClusterList)
                                                 .instanceGroupedByInfraList(instanceGroupedByInfrastructureList)
                                                 .build());
      }
      instanceGroupedByArtifactList.add(InstanceGroupedByArtifactList.InstanceGroupedByArtifact.builder()
                                            .artifactVersion(buildId)
                                            .artifactPath(artifactPath)
                                            .instanceGroupedByEnvironmentList(instanceGroupedByEnvironmentList)
                                            .build());
    }

    return instanceGroupedByArtifactList;
  }

  /*
  Returns list of instances for each build id for given account+org+project+service+env
*/
  @Override
  public InstancesByBuildIdList getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      String infraId, String clusterId, String pipelineExecutionId) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String envRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, envId);

    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<InstanceDetailsByBuildId> instancesByBuildIdList =
        instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceRef, envRef, buildIds, getCurrentTime(), infraId, clusterId, pipelineExecutionId,
            gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);

    return InstancesByBuildIdList.builder().instancesByBuildIdList(instancesByBuildIdList).build();
  }

  @Override
  public InstanceDetailsByBuildId getActiveInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String envIdentifier, String infraIdentifier,
      String clusterIdentifier, String pipelineExecutionId, String buildId) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    return instanceDashboardService.getActiveInstanceDetails(accountIdentifier, orgIdentifier, projectIdentifier,
        serviceIdentifier, envIdentifier, infraIdentifier, clusterIdentifier, pipelineExecutionId, buildId,
        gitOpsInfo.isGitOps, gitOpsInfo.isGitOpsMergeEnabled);
  }

  @Override
  public InstanceDetailGroupedByPipelineExecutionList getInstanceDetailGroupedByPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      String envIdentifier, EnvironmentType environmentType, String infraIdentifier, String clusterIdentifier,
      String displayName, String chartVersion, boolean isRollbackV2) {
    IdentifierRef serviceIdRef =
        DashboardServiceHelper.getIdentifierRef(serviceIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    ServiceGitOpsInfo gitOpsInfo = getServiceGitOpsInfo(
        accountIdentifier, serviceIdRef.getOrgIdentifier(), serviceIdRef.getProjectIdentifier(), serviceIdentifier);
    boolean isK8sOrHelmService = isK8sOrHelm(
        accountIdentifier, serviceIdRef.getOrgIdentifier(), serviceIdRef.getProjectIdentifier(), serviceIdentifier);

    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionList = new ArrayList<>();

    if (isEmpty(displayName)) {
      if (isRollbackV2) {
        instanceDetailGroupedByPipelineExecutionList.addAll(
            instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
                projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier,
                clusterIdentifier, null, chartVersion, gitOpsInfo.isGitOps, isK8sOrHelmService, isRollbackV2,
                gitOpsInfo.isGitOpsMergeEnabled));
      } else {
        instanceDetailGroupedByPipelineExecutionList.addAll(
            instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
                projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier,
                clusterIdentifier, EMPTY_ARTIFACT, chartVersion, gitOpsInfo.isGitOps, isK8sOrHelmService, false,
                gitOpsInfo.isGitOpsMergeEnabled));

        instanceDetailGroupedByPipelineExecutionList.addAll(
            instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
                projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier,
                clusterIdentifier, null, chartVersion, gitOpsInfo.isGitOps, isK8sOrHelmService, false,
                gitOpsInfo.isGitOpsMergeEnabled));
      }
    } else {
      instanceDetailGroupedByPipelineExecutionList.addAll(
          instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
              projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier, clusterIdentifier,
              displayName, chartVersion, gitOpsInfo.isGitOps, isK8sOrHelmService, false,
              gitOpsInfo.isGitOpsMergeEnabled));
    }
    // sort based on last deployed time

    Collections.sort(instanceDetailGroupedByPipelineExecutionList,
        new Comparator<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>() {
          public int compare(InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution o1,
              InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return InstanceDetailGroupedByPipelineExecutionList.builder()
        .instanceDetailGroupedByPipelineExecutionList(instanceDetailGroupedByPipelineExecutionList)
        .build();
  }

  /*
  Returns instance count summary for given account+org+project+serviceId, includes rate of change in count since
  provided timestamp
*/
  @Override
  public io.harness.ng.overview.dto.ActiveServiceInstanceSummary getActiveServiceInstanceSummary(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    // build service ref from id
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase> countDetailsByEnvTypeBasePair =
        getActiveServiceInstanceSummaryHelper(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, timestampInMs);

    InstanceCountDetailsByEnvTypeBase currentCountDetails = countDetailsByEnvTypeBasePair.getValue();
    InstanceCountDetailsByEnvTypeBase prevCountDetails = countDetailsByEnvTypeBasePair.getKey();

    double changeRate =
        calculateChangeRate(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummary.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  @Override
  public ActiveServiceInstanceSummaryV2 getActiveServiceInstanceSummaryV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase> countDetailsByEnvTypeBasePair =
        getActiveServiceInstanceSummaryHelper(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, timestampInMs);

    InstanceCountDetailsByEnvTypeBase currentCountDetails = countDetailsByEnvTypeBasePair.getValue();
    InstanceCountDetailsByEnvTypeBase prevCountDetails = countDetailsByEnvTypeBasePair.getKey();

    ChangeRate changeRate =
        calculateChangeRateV2(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummaryV2.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  @Override
  public ActiveServiceInstanceSummaryV3 getActiveServiceInstanceSummaryV3(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Pair<InstanceCountDetailsByEnvTypeAndEnvId, InstanceCountDetailsByEnvTypeAndEnvId> countDetailsByEnvIdPair =
        getActiveServiceInstanceSummaryV3Helper(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, timestampInMs);

    InstanceCountDetailsByEnvTypeAndEnvId currentCountDetails = countDetailsByEnvIdPair.getValue();
    InstanceCountDetailsByEnvTypeAndEnvId prevCountDetails = countDetailsByEnvIdPair.getKey();

    ChangeRate changeRate =
        calculateChangeRateV2(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummaryV3.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  public Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase>
  getActiveServiceInstanceSummaryHelper(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    final long currentTime = getCurrentTime();

    InstanceCountDetailsByEnvTypeBase defaultInstanceCountDetails =
        InstanceCountDetailsByEnvTypeBase.builder().envTypeVsInstanceCountMap(new HashMap<>()).build();

    InstanceCountDetailsByEnvTypeBase currentCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceId), currentTime)
            .getInstanceCountDetailsByEnvTypeBaseMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);
    InstanceCountDetailsByEnvTypeBase prevCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceId), timestampInMs)
            .getInstanceCountDetailsByEnvTypeBaseMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);

    return MutablePair.of(prevCountDetails, currentCountDetails);
  }

  public Pair<InstanceCountDetailsByEnvTypeAndEnvId, InstanceCountDetailsByEnvTypeAndEnvId>
  getActiveServiceInstanceSummaryV3Helper(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    final long currentTime = getCurrentTime();

    InstanceCountDetailsByEnvTypeAndEnvId defaultInstanceCountDetails =
        new InstanceCountDetailsByEnvTypeAndEnvId(new HashMap<>(), new HashMap<>());

    InstanceCountDetailsByEnvTypeAndEnvId currentCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdownByEnvId(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceId, currentTime)
            .getInstanceCountDetailsByEnvIdMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);
    InstanceCountDetailsByEnvTypeAndEnvId prevCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdownByEnvId(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs)
            .getInstanceCountDetailsByEnvIdMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);

    return MutablePair.of(prevCountDetails, currentCountDetails);
  }

  /*
  Returns a list of time value pairs where value represents count of instances for given account+org+project+service
  within provided time interval
*/
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getInstanceGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<TimeValuePair<Integer>> timeValuePairList = new ArrayList<>();
    Map<Long, Integer> timeValuePairMap = new HashMap<>();

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    final String query;

    query = "select reportedat, SUM(instancecount) as count from ng_instance_stats_day where parent_unique_id = "
        + "? and serviceid = ? and reportedat >= ? and reportedat <= "
        + "? group by reportedat order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, scopeInfo.getUniqueId());
        statement.setString(2, serviceRef);
        statement.setTimestamp(3, new Timestamp(tunedStartTimeInMs), DateUtils.getDefaultCalendar());
        statement.setTimestamp(4, new Timestamp(tunedEndTimeInMs), DateUtils.getDefaultCalendar());

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          final long timestamp =
              resultSet.getTimestamp(TimescaleConstants.REPORTEDAT.getKey(), DateUtils.getDefaultCalendar()).getTime();
          final int count = Integer.parseInt(resultSet.getString("count"));
          timeValuePairMap.put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    long currTime = tunedStartTimeInMs;
    while (currTime < tunedEndTimeInMs) {
      timeValuePairList.add(new TimeValuePair<>(currTime, timeValuePairMap.getOrDefault(currTime, 0)));
      currTime = currTime + DAY.getDurationInMs();
    }

    return new io.harness.ng.overview.dto.TimeValuePairListDTO<>(timeValuePairList);
  }

  /*
  Returns a list of time value pairs where value represents count of instances for given account+org+project+service
  within provided time interval
*/
  @Override
  public TimeValuePairListDTO<Integer> getInstanceGrowthTrendViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<TimeValuePair<Integer>> timeValuePairList = new ArrayList<>();
    Map<Long, Integer> timeValuePairMap = new HashMap<>();

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Query query;
    query = dslContext.select(NG_INSTANCE_STATS_DAY.REPORTEDAT, sum(NG_INSTANCE_STATS_DAY.INSTANCECOUNT).as("count"))
                .from(NG_INSTANCE_STATS_DAY)
                .where(NG_INSTANCE_STATS_DAY.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()))
                .and(NG_INSTANCE_STATS_DAY.SERVICEID.eq(serviceRef))
                .and(NG_INSTANCE_STATS_DAY.REPORTEDAT.greaterOrEqual(
                    new Timestamp(tunedStartTimeInMs).toInstant().atOffset(ZoneOffset.UTC)))
                .and(NG_INSTANCE_STATS_DAY.REPORTEDAT.lessOrEqual(
                    new Timestamp(tunedEndTimeInMs).toInstant().atOffset(ZoneOffset.UTC)))
                .groupBy(NG_INSTANCE_STATS_DAY.REPORTEDAT)
                .orderBy(NG_INSTANCE_STATS_DAY.REPORTEDAT.asc());

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          final long timestamp =
              record.get(NgInstanceStatsDay.NG_INSTANCE_STATS_DAY.REPORTEDAT, Timestamp.class).getTime();
          final int count = record.get("count", Integer.class);
          timeValuePairMap.put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    long currTime = tunedStartTimeInMs;
    while (currTime < tunedEndTimeInMs) {
      timeValuePairList.add(new TimeValuePair<>(currTime, timeValuePairMap.getOrDefault(currTime, 0)));
      currTime = currTime + DAY.getDurationInMs();
    }

    return new TimeValuePairListDTO<>(timeValuePairList);
  }

  /*
  Returns a list of time value pairs where value is a pair of envid and instance count
*/
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<io.harness.ng.overview.dto.EnvIdCountPair>
  getInstanceCountHistory(String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId,
      long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<TimeValuePair<io.harness.ng.overview.dto.EnvIdCountPair>> timeValuePairList = new ArrayList<>();
    Map<String, Map<Long, Integer>> envIdToTimestampAndCountMap = new HashMap<>();

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    final String query;
    query = "select reportedat, envid, SUM(instancecount) as count from ng_instance_stats_day where "
        + "parent_unique_id = ? and serviceid = ? and reportedat >= ? and "
        + "reportedat <= ? group by reportedat, envid order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, scopeInfo.getUniqueId());
        statement.setString(2, serviceRef);
        statement.setTimestamp(3, new Timestamp(tunedStartTimeInMs), DateUtils.getDefaultCalendar());
        statement.setTimestamp(4, new Timestamp(tunedEndTimeInMs), DateUtils.getDefaultCalendar());

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          final long timestamp =
              resultSet.getTimestamp(TimescaleConstants.REPORTEDAT.getKey(), DateUtils.getDefaultCalendar()).getTime();
          final String envId = resultSet.getString(TimescaleConstants.ENV_ID.getKey());
          final int count = Integer.parseInt(resultSet.getString("count"));

          envIdToTimestampAndCountMap.putIfAbsent(envId, new HashMap<>());
          envIdToTimestampAndCountMap.get(envId).put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    envIdToTimestampAndCountMap.forEach((envId, timeStampAndCountMap) -> {
      long currTime = tunedStartTimeInMs;
      while (currTime <= tunedEndTimeInMs) {
        int count = timeStampAndCountMap.getOrDefault(currTime, 0);
        io.harness.ng.overview.dto.EnvIdCountPair envIdCountPair =
            EnvIdCountPair.builder().envId(envId).count(count).build();
        timeValuePairList.add(new TimeValuePair<>(currTime, envIdCountPair));
        currTime += DAY.getDurationInMs();
      }
    });

    return new TimeValuePairListDTO<>(timeValuePairList);
  }

  @Override
  public TimeValuePairListDTO<EnvIdCountPair> getInstanceCountHistoryViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<TimeValuePair<EnvIdCountPair>> timeValuePairList = new ArrayList<>();
    Map<String, Map<Long, Integer>> envIdToTimestampAndCountMap = new HashMap<>();

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Query query;
    query = dslContext
                .select(NG_INSTANCE_STATS_DAY.REPORTEDAT, NG_INSTANCE_STATS_DAY.ENVID,
                    sum(NG_INSTANCE_STATS_DAY.INSTANCECOUNT).as("count"))
                .from(NG_INSTANCE_STATS_DAY)
                .where(NG_INSTANCE_STATS_DAY.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()))
                .and(NG_INSTANCE_STATS_DAY.SERVICEID.eq(serviceRef))
                .and(NG_INSTANCE_STATS_DAY.REPORTEDAT.greaterOrEqual(
                    new Timestamp(tunedStartTimeInMs).toInstant().atOffset(ZoneOffset.UTC)))
                .and(NG_INSTANCE_STATS_DAY.REPORTEDAT.lessOrEqual(
                    new Timestamp(tunedEndTimeInMs).toInstant().atOffset(ZoneOffset.UTC)))
                .groupBy(NG_INSTANCE_STATS_DAY.REPORTEDAT, NG_INSTANCE_STATS_DAY.ENVID)
                .orderBy(NG_INSTANCE_STATS_DAY.REPORTEDAT.asc());

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try {
        dslContext.fetchLazy(query.getSQL(), query.getBindValues().toArray()).forEach(record -> {
          final long timestamp =
              record.get(NgInstanceStatsDay.NG_INSTANCE_STATS_DAY.REPORTEDAT, Timestamp.class).getTime();
          final String envId = record.get(NgInstanceStatsDay.NG_INSTANCE_STATS_DAY.ENVID);
          final int count = record.get("count", Integer.class);

          envIdToTimestampAndCountMap.putIfAbsent(envId, new HashMap<>());
          envIdToTimestampAndCountMap.get(envId).put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }

    envIdToTimestampAndCountMap.forEach((envId, timeStampAndCountMap) -> {
      long currTime = tunedStartTimeInMs;
      while (currTime <= tunedEndTimeInMs) {
        int count = timeStampAndCountMap.getOrDefault(currTime, 0);
        EnvIdCountPair envIdCountPair = EnvIdCountPair.builder().envId(envId).count(count).build();
        timeValuePairList.add(new TimeValuePair<>(currTime, envIdCountPair));
        currTime += DAY.getDurationInMs();
      }
    });

    return new TimeValuePairListDTO<>(timeValuePairList);
  }

  @Override
  public DeploymentsInfo getDeploymentsByServiceId(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String query = queryBuilderDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, startTimeInMs, endTimeInMs, scopeInfo);
    String queryServiceNameTagId = queryBuilderServiceTag(
        queryToGetId(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, scopeInfo), serviceRef);
    List<ExecutionStatusInfo> deployments = getDeploymentStatusInfo(query, queryServiceNameTagId, scopeInfo);
    return DeploymentsInfo.builder().deployments(deployments).build();
  }

  @Override
  public DeploymentsInfo getDeploymentsByServiceIdViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Query query = queryBuilderDeploymentsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, startTimeInMs, endTimeInMs, scopeInfo);
    Query queryServiceNameTagId = queryBuilderServiceTagViaJooq(
        queryToGetIdViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, scopeInfo), serviceRef);
    List<ExecutionStatusInfo> deployments = getDeploymentStatusInfo(query, queryServiceNameTagId, scopeInfo);
    return DeploymentsInfo.builder().deployments(deployments).build();
  }

  @Override
  public ServiceDeployments getAllDeploymentsByServiceId(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountIdentifier, orgIdentifier, projectIdentifier, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String query = queryBuilderAllDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, startTimeInMs, endTimeInMs, parentUniqueIds);
    String queryServiceNameTagId = queryBuilderServiceTag(
        queryToGetAllIds(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, parentUniqueIds), serviceRef);
    List<ExecutionStatusInfo> deployments =
        getDeploymentStatusInfo(query, queryServiceNameTagId, parentUniqueIdsToScopeInfoMap);
    Map<String, Map<String, ServiceDeployments.ProjectDeployments>> orgDeploymentsMap = new HashMap<>();
    for (ExecutionStatusInfo executionStatusInfo : deployments) {
      orgDeploymentsMap.putIfAbsent(executionStatusInfo.getOrgIdentifier(), new HashMap<>());
      orgDeploymentsMap.get(executionStatusInfo.getOrgIdentifier())
          .putIfAbsent(executionStatusInfo.getProjectIdentifier(),
              ServiceDeployments.ProjectDeployments.builder()
                  .deployments(new ArrayList<>())
                  .projectIdentifier(executionStatusInfo.getProjectIdentifier())
                  .build());
      orgDeploymentsMap.get(executionStatusInfo.getOrgIdentifier())
          .get(executionStatusInfo.getProjectIdentifier())
          .getDeployments()
          .add(executionStatusInfo);
    }
    List<ServiceDeployments.OrgDeployments> orgDeploymentsList = new ArrayList<>();
    for (Map.Entry<String, Map<String, ServiceDeployments.ProjectDeployments>> entry : orgDeploymentsMap.entrySet()) {
      List<ServiceDeployments.ProjectDeployments> projectDeploymentsList = new ArrayList<>();
      for (Map.Entry<String, ServiceDeployments.ProjectDeployments> projectDeploymentsEntry :
          entry.getValue().entrySet()) {
        projectDeploymentsList.add(projectDeploymentsEntry.getValue());
      }
      orgDeploymentsList.add(ServiceDeployments.OrgDeployments.builder()
                                 .orgIdentifier(entry.getKey())
                                 .projectDeploymentsList(projectDeploymentsList)
                                 .build());
    }
    return ServiceDeployments.builder().orgDeploymentsList(orgDeploymentsList).build();
  }

  @Override
  public ServiceDeployments getAllDeploymentsByServiceIdViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    List<String> parentUniqueIds = new ArrayList<>();
    Map<String, ScopeInfo> parentUniqueIdsToScopeInfoMap = new HashMap<>();
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap;
    scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountIdentifier, orgIdentifier, projectIdentifier, false);
    parentUniqueIds = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                          .stream()
                          .flatMap(innerMap -> innerMap.values().stream())
                          .map(ScopeInfo::getUniqueId)
                          .toList();
    parentUniqueIdsToScopeInfoMap = scopeLevelToUniqueIdsAndScopeInfoMap.values()
                                        .stream()
                                        .flatMap(innerMap -> innerMap.values().stream())
                                        .collect(Collectors.toMap(ScopeInfo::getUniqueId, Function.identity()));

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Query query = queryBuilderAllDeploymentsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, startTimeInMs, endTimeInMs, parentUniqueIds);
    Query queryServiceNameTagId = queryBuilderServiceTagViaJooq(
        queryToGetAllIdsViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, parentUniqueIds),
        serviceRef);
    List<ExecutionStatusInfo> deployments =
        getDeploymentStatusInfo(query, queryServiceNameTagId, parentUniqueIdsToScopeInfoMap);
    Map<String, Map<String, ServiceDeployments.ProjectDeployments>> orgDeploymentsMap = new HashMap<>();
    for (ExecutionStatusInfo executionStatusInfo : deployments) {
      orgDeploymentsMap.putIfAbsent(executionStatusInfo.getOrgIdentifier(), new HashMap<>());
      orgDeploymentsMap.get(executionStatusInfo.getOrgIdentifier())
          .putIfAbsent(executionStatusInfo.getProjectIdentifier(),
              ServiceDeployments.ProjectDeployments.builder()
                  .deployments(new ArrayList<>())
                  .projectIdentifier(executionStatusInfo.getProjectIdentifier())
                  .build());
      orgDeploymentsMap.get(executionStatusInfo.getOrgIdentifier())
          .get(executionStatusInfo.getProjectIdentifier())
          .getDeployments()
          .add(executionStatusInfo);
    }
    List<ServiceDeployments.OrgDeployments> orgDeploymentsList = new ArrayList<>();
    for (Map.Entry<String, Map<String, ServiceDeployments.ProjectDeployments>> entry : orgDeploymentsMap.entrySet()) {
      List<ServiceDeployments.ProjectDeployments> projectDeploymentsList = new ArrayList<>();
      for (Map.Entry<String, ServiceDeployments.ProjectDeployments> projectDeploymentsEntry :
          entry.getValue().entrySet()) {
        projectDeploymentsList.add(projectDeploymentsEntry.getValue());
      }
      orgDeploymentsList.add(ServiceDeployments.OrgDeployments.builder()
                                 .orgIdentifier(entry.getKey())
                                 .projectDeploymentsList(projectDeploymentsList)
                                 .build());
    }
    return ServiceDeployments.builder().orgDeploymentsList(orgDeploymentsList).build();
  }

  private String queryBuilderDeployments(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, long startTimeInMs, long endTimeInMs, ScopeInfo scopeInfo) {
    return "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where id in ( "
        + queryToGetId(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, scopeInfo) + ") and "
        + String.format("startts>='%s' and startts<='%s' ", startTimeInMs, endTimeInMs) + "order by startts desc";
  }

  private Query queryBuilderDeploymentsViaJooq(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, long startTimeInMs, long endTimeInMs, ScopeInfo scopeInfo) {
    return dslContext
        .select(PIPELINE_EXECUTION_SUMMARY_CD.ID, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
            PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
            PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
            PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME,
            PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY, PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID, PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR,
            PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
            PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID)
        .from(PIPELINE_EXECUTION_SUMMARY_CD)
        .where(PIPELINE_EXECUTION_SUMMARY_CD.ID
                   .in(queryToGetIdViaJooq(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, scopeInfo))
                   .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startTimeInMs))
                   .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessOrEqual(endTimeInMs)))
        .orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc());
  }

  private String queryToGetId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, ScopeInfo scopeInfo) {
    return "select distinct pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra + " where "
        + String.format("parent_unique_id='%s' and ", DashboardServiceHelper.escapeSql(scopeInfo.getUniqueId()))
        + String.format("service_id='%s'", DashboardServiceHelper.escapeSql(serviceId));
  }

  private SelectConditionStep<Record1<String>> queryToGetIdViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, ScopeInfo scopeInfo) {
    return dslContext.selectDistinct(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.in(scopeInfo.getUniqueId()))
        .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId));
  }

  private String queryBuilderAllDeployments(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, long startTimeInMs, long endTimeInMs, List<String> parentUniqueIds) {
    return "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where id in ( "
        + queryToGetAllIds(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, parentUniqueIds) + ") and "
        + String.format("startts>='%s' and startts<='%s' ", startTimeInMs, endTimeInMs) + "order by startts desc";
  }

  private Query queryBuilderAllDeploymentsViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs, List<String> parentUniqueIds) {
    return dslContext
        .select(PIPELINE_EXECUTION_SUMMARY_CD.ID, PIPELINE_EXECUTION_SUMMARY_CD.NAME,
            PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
            PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
            PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME,
            PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID, PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY, PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE,
            PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID, PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR,
            PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER)
        .from(PIPELINE_EXECUTION_SUMMARY_CD)
        .where(PIPELINE_EXECUTION_SUMMARY_CD.ID
                   .in(queryToGetAllIdsViaJooq(
                       accountIdentifier, orgIdentifier, projectIdentifier, serviceId, parentUniqueIds))
                   .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.greaterOrEqual(startTimeInMs))
                   .and(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lessOrEqual(endTimeInMs)))
        .orderBy(PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.desc());
  }

  private String queryToGetAllIds(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, List<String> parentUniqueIds) {
    return "select distinct pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra + " where "
        + String.format("parent_unique_id in ('%s') and ",
            String.join("','", parentUniqueIds.stream().map(DashboardServiceHelper::escapeSql).toArray(String[] ::new)))
        + String.format("service_id='%s'", DashboardServiceHelper.escapeSql(serviceId));
  }

  private SelectConditionStep<Record1<String>> queryToGetAllIdsViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, List<String> parentUniqueIds) {
    return dslContext.selectDistinct(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId));
  }

  @Override
  public io.harness.ng.overview.dto.ServiceHeaderInfo getServiceHeaderInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, boolean loadFromCache,
      boolean loadFromFallbackBranch) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ServiceEntity> service =
        serviceEntityServiceImpl.get(scopeInfo, serviceId, false, loadFromCache, loadFromFallbackBranch);
    if (service.isEmpty()) {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceId));
    }
    ServiceEntity serviceEntity = service.get();

    String serviceRef = getServiceRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap = getDeploymentType(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(serviceRef), scopeInfo);

    populateDeploymentTypeFromServiceEntity(serviceIdToDeploymentTypeMap, Collections.singletonList(serviceEntity),
        accountIdentifier, orgIdentifier, projectIdentifier);
    Set<String> deploymentTypes = serviceIdToDeploymentTypeMap.getOrDefault(serviceRef, new HashSet<>());

    Set<IconDTO> iconDTOSet = new HashSet<>();
    Map<String, String> serviceRefToTemplateRef = new HashMap<>();

    ScopeInfo serviceScopeInfo =
        scopeResolverService.getScopeInfoFromParentUniqueId(accountIdentifier, serviceEntity.getParentUniqueId());
    getServiceToTemplateRef(
        deploymentTypes, serviceEntity.getYaml(serviceScopeInfo), serviceRef, new HashMap<>(), serviceRefToTemplateRef);
    if (!isEmpty(serviceRefToTemplateRef.get(serviceId))) {
      updateIconDTOList(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefToTemplateRef.get(serviceId),
          deploymentTypes, iconDTOSet);
    }

    return ServiceHeaderInfo.builder()
        .identifier(serviceId)
        .name(serviceEntity.getName())
        .deploymentIconList(iconDTOSet)
        .description(serviceEntity.getDescription())
        .deploymentTypes(deploymentTypes)
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  @Override
  public ServiceHeaderInfo getServiceHeaderInfoViaJooq(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, boolean loadFromCache, boolean loadFromFallbackBranch) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ServiceEntity> service =
        serviceEntityServiceImpl.get(scopeInfo, serviceId, false, loadFromCache, loadFromFallbackBranch);
    if (service.isEmpty()) {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceId));
    }
    ServiceEntity serviceEntity = service.get();

    String serviceRef = getServiceRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Map<String, Set<String>> serviceIdToDeploymentTypeMap = getDeploymentTypeViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(serviceRef), scopeInfo);

    populateDeploymentTypeFromServiceEntity(serviceIdToDeploymentTypeMap, Collections.singletonList(serviceEntity),
        accountIdentifier, orgIdentifier, projectIdentifier);
    Set<String> deploymentTypes = serviceIdToDeploymentTypeMap.getOrDefault(serviceRef, new HashSet<>());

    Set<IconDTO> iconDTOSet = new HashSet<>();
    Map<String, String> serviceRefToTemplateRef = new HashMap<>();
    // Service entity is at same scope as scope info as serviceId is not ref serviceRef is later constructed
    getServiceToTemplateRef(
        deploymentTypes, serviceEntity.getYaml(scopeInfo), serviceRef, new HashMap<>(), serviceRefToTemplateRef);
    if (!isEmpty(serviceRefToTemplateRef.get(serviceId))) {
      updateIconDTOList(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefToTemplateRef.get(serviceId),
          deploymentTypes, iconDTOSet);
    }

    return ServiceHeaderInfo.builder()
        .identifier(serviceId)
        .name(serviceEntity.getName())
        .deploymentIconList(iconDTOSet)
        .description(serviceEntity.getDescription())
        .deploymentTypes(deploymentTypes)
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  public void updateIconDTOList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateRef, Set<String> deploymentTypes, Set<IconDTO> iconDTOSet) {
    String icon = getIcon(accountIdentifier, projectIdentifier, orgIdentifier, templateRef);
    deploymentTypes.forEach(
        deploymentType -> iconDTOSet.add(setIcon(IconDTO.builder().deploymentType(deploymentType).build(), icon)));
  }

  private IconDTO setIcon(IconDTO iconDTO, String icon) {
    if (CUSTOM_DEPLOYMENT.equals(iconDTO.getDeploymentType())) {
      iconDTO.setIcon(icon);
    }
    return iconDTO;
  }

  private String getIcon(String accountIdentifier, String projectIdentifier, String orgIdentifier, String templateRef) {
    try {
      TemplateResponseDTO responseDTO = customDeploymentYamlHelper.getScopedTemplateResponseDTO(
          accountIdentifier, orgIdentifier, projectIdentifier, templateRef, null);
      if (!isNull(responseDTO)) {
        return responseDTO.getIcon();
      } else {
        return "";
      }
    } catch (Exception e) {
      log.error("could not fetch icon for template with template ref : {}", templateRef);
      return "";
    }
  }

  /*
Returns a list of last successfully buildId deployed to environments for given account+org+project+service
*/
  @Override
  public io.harness.ng.overview.dto.EnvironmentDeploymentInfo getEnvironmentDeploymentDetailsByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String query = queryBuilderDeploymentsWithArtifactsDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, scopeInfo);
    List<EnvironmentInfoByServiceId> environmentInfoByServiceIds = getEnvironmentWithArtifactDetails(query);
    return EnvironmentDeploymentInfo.builder().environmentInfoByServiceId(environmentInfoByServiceIds).build();
  }

  /*
Returns a list of last successfully buildId deployed to environments for given account+org+project+service
*/
  @Override
  public EnvironmentDeploymentInfo getEnvironmentDeploymentDetailsByServiceIdViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Query query = queryBuilderDeploymentsWithArtifactsDetailsViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, scopeInfo);
    List<EnvironmentInfoByServiceId> environmentInfoByServiceIds = getEnvironmentWithArtifactDetails(query);
    return EnvironmentDeploymentInfo.builder().environmentInfoByServiceId(environmentInfoByServiceIds).build();
  }

  @Override
  public InstanceGroupedByServiceList.InstanceGroupedByService getActiveServiceDeploymentsList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    InstanceGroupedByServiceList instanceGroupedByServiceList = getActiveServiceDeploymentsListHelper(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, null, null, scopeInfo);
    return getInstanceGroupedByService(instanceGroupedByServiceList);
  }

  @Override
  public InstanceGroupedByServiceList.InstanceGroupedByService getActiveServiceDeploymentsListViaJooq(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    InstanceGroupedByServiceList instanceGroupedByServiceList = getActiveServiceDeploymentsListHelperViaJooq(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, null, null);
    return getInstanceGroupedByService(instanceGroupedByServiceList);
  }

  @Override
  public LatestServiceDeploymentResponseDTO getLatestServiceDeployments(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    ServiceGitOpsInfo gitOpsInfo =
        getServiceGitOpsInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails =
        instanceDashboardService.getLastDeployedInstancePerService(accountIdentifier, orgIdentifier, projectIdentifier,
            serviceIdentifier, true, gitOpsInfo.isGitOps, false, gitOpsInfo.isGitOpsMergeEnabled);
    return mapLatestServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, artifactDeploymentDetails);
  }

  public LatestServiceDeploymentResponseDTO mapLatestServiceDeployments(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier,
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails) {
    List<EnvironmentInfoDTO> environmentInfoDTOList = new ArrayList<>();
    EnvironmentInfoDTO environmentInfoDTO;

    for (ArtifactDeploymentDetailModel artifactDeployment : artifactDeploymentDetails) {
      String orgIdOfPipeline = artifactDeployment.getOrgIdentifier();
      String projectIdOfPipeline = artifactDeployment.getProjectIdentifier();

      PipelineExecutionInfoDTO pipelineExecutionInfoDTO = getLastPipelineExecutionInfo(
          artifactDeployment.getLastPipelineExecutionId(), accountIdentifier, orgIdOfPipeline, projectIdOfPipeline);

      IdentifierRef envRef = IdentifierRefHelper.getIdentifierRef(
          artifactDeployment.getEnvIdentifier(), accountIdentifier, orgIdOfPipeline, projectIdOfPipeline);

      environmentInfoDTO =
          EnvironmentInfoDTO.builder()
              .id(envRef.getIdentifier())
              .name(artifactDeployment.getEnvName())
              .accountId(envRef.getAccountIdentifier())
              .orgId(envRef.getOrgIdentifier())
              .projectId(envRef.getProjectIdentifier())
              .type(artifactDeployment.getEnvType())
              .infrastructure(InfrastructureInfoDTO.builder()
                                  .id(artifactDeployment.getInfraIdentifier())
                                  .name(artifactDeployment.getInfraName())
                                  .build())
              .artifactInfo(ArtifactInfoDTO.builder().version(artifactDeployment.getDisplayName()).build())
              .chartInfo(ChartInfoDTO.builder().version(artifactDeployment.getChartVersion()).build())
              .latestPipelineExecution(pipelineExecutionInfoDTO)
              .build();

      environmentInfoDTOList.add(environmentInfoDTO);
    }
    return LatestServiceDeploymentResponseDTO.builder()
        .service(ServiceInfoDTO.builder()
                     .id(serviceIdentifier)
                     .accountId(accountIdentifier)
                     .orgId(orgIdentifier)
                     .projectId(projectIdentifier)
                     .build())
        .environments(environmentInfoDTOList)
        .build();
  }

  public PipelineExecutionInfoDTO getLastPipelineExecutionInfo(
      String planExecutionId, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Object executionDetails = null;
    try {
      executionDetails = NGRestUtils.getResponse(pipelineServiceClient.getExecutionDetailV2(
          planExecutionId, accountIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to fetch last pipeline execution details for planExecutionId [" + planExecutionId + "].");
    }

    String status = "", pipelineIdentifier = "", triggeredBy = "";
    Long startTs = null, endTs = null;

    if (executionDetails != null
        && ((LinkedHashMap<String, Object>) executionDetails).containsKey("pipelineExecutionSummary")) {
      LinkedHashMap<String, Object> pipelineExecutionSummary =
          (LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) executionDetails)
              .get("pipelineExecutionSummary");

      if (pipelineExecutionSummary != null) {
        if (pipelineExecutionSummary.containsKey("status")) {
          status = String.valueOf(pipelineExecutionSummary.get("status"));
        }
        if (pipelineExecutionSummary.containsKey("pipelineIdentifier")) {
          pipelineIdentifier = String.valueOf(pipelineExecutionSummary.get("pipelineIdentifier"));
        }
        if (pipelineExecutionSummary.containsKey("startTs")) {
          startTs = (Long) pipelineExecutionSummary.get("startTs");
        }
        if (pipelineExecutionSummary.containsKey("endTs")) {
          endTs = (Long) pipelineExecutionSummary.get("endTs");
        }

        LinkedHashMap<String, Object> executionTriggerInfo = new LinkedHashMap<>();
        if (pipelineExecutionSummary.containsKey("executionTriggerInfo")) {
          executionTriggerInfo = (LinkedHashMap<String, Object>) pipelineExecutionSummary.get("executionTriggerInfo");
        }

        if (executionTriggerInfo != null && executionTriggerInfo.containsKey("triggeredBy")) {
          LinkedHashMap<String, Object> triggeredByMap =
              (LinkedHashMap<String, Object>) executionTriggerInfo.get("triggeredBy");

          if (triggeredByMap != null && triggeredByMap.containsKey("identifier")) {
            triggeredBy = (String) triggeredByMap.get("identifier");
          }
        }
      }
    }

    ZonedDateTime startDateTime = Instant.ofEpochMilli(startTs).atZone(ZoneOffset.UTC);
    ZonedDateTime endDateTime = Instant.ofEpochMilli(endTs).atZone(ZoneOffset.UTC);

    return PipelineExecutionInfoDTO.builder()
        .id(planExecutionId)
        .pipelineId(pipelineIdentifier)
        .status(status)
        .triggeredBy(TriggeredByInfoDTO.builder().name(triggeredBy).build())
        .startedAt(startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")))
        .endedAt(endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")))
        .build();
  }

  public InstanceGroupedByServiceList getActiveServiceDeploymentsListHelper(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String buildIdentifier,
      String envIdentifier, ScopeInfo scopeInfo) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList = new ArrayList<>();
    Set<String> envIdsWithInfra = new HashSet<>();

    String query = queryActiveServiceDeploymentsInfo(accountIdentifier, orgIdentifier, projectIdentifier,
        serviceIdentifier, buildIdentifier, envIdentifier, scopeInfo);
    List<ActiveServiceDeploymentsInfo> deploymentsInfo = getActiveServiceDeploymentsInfo(query);

    List<String> pipelineExecutionIdList = new ArrayList<>();

    deploymentsInfo.forEach(deploymentInfo -> {
      if (deploymentInfo.getPipelineExecutionId() != null) {
        pipelineExecutionIdList.add(deploymentInfo.getPipelineExecutionId());
      }
      if (deploymentInfo.getInfrastructureIdentifier() != null) {
        envIdsWithInfra.add(deploymentInfo.getEnvId());
      }
    });

    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    deploymentsInfo.forEach(deploymentInfo -> {
      final String infrastructureIdentifier = deploymentInfo.getInfrastructureIdentifier();
      final String envId = deploymentInfo.getEnvId();

      if (envId == null || (infrastructureIdentifier == null && envIdsWithInfra.contains(envId))) {
        return;
      }

      final String artifact = deploymentInfo.getTag();
      final String envName = deploymentInfo.getEnvName();
      final String pipelineExecutionId = deploymentInfo.getPipelineExecutionId();
      final String infrastructureName = deploymentInfo.getInfrastructureName();
      final String artifactPath = deploymentInfo.getArtifactPath();
      final String serviceId = deploymentInfo.getServiceId();
      final String serviceName = deploymentInfo.getServiceName();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, artifact);

      String lastPipelineExecutionId = null;
      String lastPipelineExecutionName = null;
      Long lastDeployedAt = null;
      if (pipelineExecutionId != null) {
        ServicePipelineInfo servicePipelineInfo = pipelineExecutionDetailsMap.get(pipelineExecutionId);
        if (servicePipelineInfo != null) {
          lastPipelineExecutionId = servicePipelineInfo.getPlanExecutionId();
          lastPipelineExecutionName = servicePipelineInfo.getIdentifier();
          lastDeployedAt = servicePipelineInfo.getLastExecutedAt();
        }
      }
      if (lastPipelineExecutionId == null || lastDeployedAt == null) {
        return;
      }
      activeServiceInstanceInfoList.add(new ActiveServiceInstanceInfoV2(serviceId, serviceName, envId, envName,
          infrastructureIdentifier, infrastructureName, null, null, lastPipelineExecutionId, lastPipelineExecutionName,
          lastDeployedAt, artifact, displayName, null, orgIdentifier, projectIdentifier, null));
    });

    return getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
  }

  public InstanceGroupedByServiceList getActiveServiceDeploymentsListHelperViaJooq(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String buildIdentifier,
      String envIdentifier) {
    ScopeInfo scopeInfo = null;
    scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList = new ArrayList<>();
    Set<String> envIdsWithInfra = new HashSet<>();

    Query query = queryActiveServiceDeploymentsInfoViaJooq(accountIdentifier, orgIdentifier, projectIdentifier,
        serviceIdentifier, buildIdentifier, envIdentifier, scopeInfo);
    List<ActiveServiceDeploymentsInfo> deploymentsInfo = getActiveServiceDeploymentsInfo(query);

    List<String> pipelineExecutionIdList = new ArrayList<>();

    deploymentsInfo.forEach(deploymentInfo -> {
      if (deploymentInfo.getPipelineExecutionId() != null) {
        pipelineExecutionIdList.add(deploymentInfo.getPipelineExecutionId());
      }
      if (deploymentInfo.getInfrastructureIdentifier() != null) {
        envIdsWithInfra.add(deploymentInfo.getEnvId());
      }
    });

    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap =
        getPipelineExecutionDetailsViaJooq(pipelineExecutionIdList);

    deploymentsInfo.forEach(deploymentInfo -> {
      final String infrastructureIdentifier = deploymentInfo.getInfrastructureIdentifier();
      final String envId = deploymentInfo.getEnvId();

      if (envId == null || (infrastructureIdentifier == null && envIdsWithInfra.contains(envId))) {
        return;
      }

      final String artifact = deploymentInfo.getTag();
      final String envName = deploymentInfo.getEnvName();
      final String pipelineExecutionId = deploymentInfo.getPipelineExecutionId();
      final String infrastructureName = deploymentInfo.getInfrastructureName();
      final String artifactPath = deploymentInfo.getArtifactPath();
      final String serviceId = deploymentInfo.getServiceId();
      final String serviceName = deploymentInfo.getServiceName();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, artifact);

      String lastPipelineExecutionId = null;
      String lastPipelineExecutionName = null;
      Long lastDeployedAt = null;
      if (pipelineExecutionId != null) {
        ServicePipelineInfo servicePipelineInfo = pipelineExecutionDetailsMap.get(pipelineExecutionId);
        if (servicePipelineInfo != null) {
          lastPipelineExecutionId = servicePipelineInfo.getPlanExecutionId();
          lastPipelineExecutionName = servicePipelineInfo.getIdentifier();
          lastDeployedAt = servicePipelineInfo.getLastExecutedAt();
        }
      }
      if (lastPipelineExecutionId == null || lastDeployedAt == null) {
        return;
      }
      activeServiceInstanceInfoList.add(new ActiveServiceInstanceInfoV2(serviceId, serviceName, envId, envName,
          infrastructureIdentifier, infrastructureName, null, null, lastPipelineExecutionId, lastPipelineExecutionName,
          lastDeployedAt, artifact, displayName, null, orgIdentifier, projectIdentifier, null));
    });

    return getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
  }

  public String queryBuilderDeploymentsWithArtifactsDetails(
      String accountId, String orgId, String projectId, String serviceId, ScopeInfo scopeInfo) {
    return "SELECT DISTINCT ON (env_id) env_name, env_id, artifact_image, tag, service_startts, "
        + "service_endts, service_name, service_id from " + tableNameServiceAndInfra + " where "
        + String.format("parent_unique_id='%s' and ", DashboardServiceHelper.escapeSql(scopeInfo.getUniqueId()))
        + String.format("service_id='%s'", DashboardServiceHelper.escapeSql(serviceId))
        + " and service_status = 'SUCCESS' AND tag is not null order by env_id , service_endts DESC;";
  }

  public Query queryBuilderDeploymentsWithArtifactsDetailsViaJooq(
      String accountId, String orgId, String projectId, String serviceId, ScopeInfo scopeInfo) {
    return dslContext
        .selectDistinct(SERVICE_INFRA_INFO.ENV_NAME, SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.ARTIFACT_IMAGE,
            SERVICE_INFRA_INFO.TAG, SERVICE_INFRA_INFO.SERVICE_STARTTS, SERVICE_INFRA_INFO.SERVICE_ENDTS,
            SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.SERVICE_ID)
        .distinctOn(SERVICE_INFRA_INFO.ENV_ID)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()))
        .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId))
        .and(SERVICE_INFRA_INFO.SERVICE_STATUS.eq("SUCCESS"))
        .and(SERVICE_INFRA_INFO.TAG.isNotNull())
        .orderBy(SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.SERVICE_ENDTS.desc());
  }

  public String queryActiveServiceDeploymentsInfo(String accountId, String orgId, String projectId, String serviceId,
      String buildId, String envId, ScopeInfo scopeInfo) {
    String query = "";
    query = String.format(
        "select distinct on (env_id,infrastructureIdentifier) tag, env_id, env_name, service_id, service_name, "
            + "infrastructureIdentifier, infrastructureName, artifact_image, pipeline_execution_summary_cd_id from "
            + "%s "
            + "where parent_unique_id='%s' and service_status = 'SUCCESS' "
            + "AND "
            + "tag is not null AND service_id is not null",
        tableNameServiceAndInfra, DashboardServiceHelper.escapeSql(scopeInfo.getUniqueId()));

    if (serviceId != null) {
      query = query + String.format(" and service_id='%s'", DashboardServiceHelper.escapeSql(serviceId));
    }
    if (buildId != null) {
      query = query + String.format(" and tag='%s'", DashboardServiceHelper.escapeSql(buildId));
    }
    if (envId != null) {
      query = query + String.format(" and env_id='%s'", DashboardServiceHelper.escapeSql(envId));
    }

    return query + " order by env_id , infrastructureIdentifier, service_endts DESC;";
  }

  public Query queryActiveServiceDeploymentsInfoViaJooq(String accountId, String orgId, String projectId,
      String serviceId, String buildId, String envId, ScopeInfo scopeInfo) {
    SelectConditionStep<Record9<String, String, String, String, String, String, String, String, String>> query;

    query = dslContext
                .selectDistinct(SERVICE_INFRA_INFO.TAG, SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.ENV_NAME,
                    SERVICE_INFRA_INFO.SERVICE_ID, SERVICE_INFRA_INFO.SERVICE_NAME,
                    SERVICE_INFRA_INFO.INFRASTRUCTUREIDENTIFIER, SERVICE_INFRA_INFO.INFRASTRUCTURENAME,
                    SERVICE_INFRA_INFO.ARTIFACT_IMAGE, SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
                .distinctOn(SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.INFRASTRUCTUREIDENTIFIER)
                .from(SERVICE_INFRA_INFO)
                .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()))
                .and(SERVICE_INFRA_INFO.SERVICE_STATUS.eq("SUCCESS"))
                .and(SERVICE_INFRA_INFO.TAG.isNotNull())
                .and(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull());

    if (serviceId != null) {
      query.and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId));
    }
    if (buildId != null) {
      query.and(SERVICE_INFRA_INFO.TAG.eq(buildId));
    }
    if (envId != null) {
      query.and(SERVICE_INFRA_INFO.ENV_ID.eq(envId));
    }

    return query.orderBy(SERVICE_INFRA_INFO.ENV_ID, SERVICE_INFRA_INFO.INFRASTRUCTUREIDENTIFIER,
        SERVICE_INFRA_INFO.SERVICE_ENDTS.desc());
  }

  public List<EnvironmentInfoByServiceId> getEnvironmentWithArtifactDetails(String queryStatus) {
    int totalTries = 0;
    List<EnvironmentInfoByServiceId> environmentInfoList = new ArrayList<>();
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          environmentInfoList.add(EnvironmentInfoByServiceId.builder()
                                      .environmentId(resultSet.getString("env_id"))
                                      .environmentName(resultSet.getString("env_name"))
                                      .artifactImage(resultSet.getString("artifact_image"))
                                      .tag(resultSet.getString("tag"))
                                      .serviceId(resultSet.getString(SERVICE_ID))
                                      .serviceName(resultSet.getString(SERVICE_NAME))
                                      .service_startTs(resultSet.getLong("service_startts"))
                                      .service_endTs(resultSet.getLong("service_endts"))
                                      .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return environmentInfoList;
  }

  public List<EnvironmentInfoByServiceId> getEnvironmentWithArtifactDetails(Query queryStatus) {
    int totalTries = 0;
    List<EnvironmentInfoByServiceId> environmentInfoList = new ArrayList<>();
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(queryStatus.getSQL(), queryStatus.getBindValues().toArray()).forEach(record -> {
          environmentInfoList.add(EnvironmentInfoByServiceId.builder()
                                      .environmentId(record.get("env_id", String.class))
                                      .environmentName(record.get("env_name", String.class))
                                      .artifactImage(record.get("artifact_image", String.class))
                                      .tag(record.get("tag", String.class))
                                      .serviceId(record.get(SERVICE_ID, String.class))
                                      .serviceName(record.get(SERVICE_NAME, String.class))
                                      .service_startTs(record.get("service_startts", Long.class))
                                      .service_endTs(record.get("service_endts", Long.class))
                                      .build());
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return environmentInfoList;
  }

  public List<ActiveServiceDeploymentsInfo> getActiveServiceDeploymentsInfo(String queryStatus) {
    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          ActiveServiceDeploymentsInfo activeServiceDeploymentsInfo =
              ActiveServiceDeploymentsInfo.builder()
                  .envId(resultSet.getString("env_id"))
                  .envName(resultSet.getString("env_name"))
                  .tag(resultSet.getString("tag"))
                  .pipelineExecutionId(resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID))
                  .infrastructureIdentifier(resultSet.getString("infrastructureidentifier"))
                  .infrastructureName(resultSet.getString("infrastructurename"))
                  .artifactPath(resultSet.getString("artifact_image"))
                  .serviceId(resultSet.getString(SERVICE_ID))
                  .serviceName(resultSet.getString(SERVICE_NAME))
                  .build();
          activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfo);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return activeServiceDeploymentsInfoList;
  }

  public List<ActiveServiceDeploymentsInfo> getActiveServiceDeploymentsInfo(Query queryStatus) {
    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try {
        dslContext.fetchLazy(queryStatus.getSQL(), queryStatus.getBindValues().toArray()).forEach(record -> {
          ActiveServiceDeploymentsInfo activeServiceDeploymentsInfo =
              ActiveServiceDeploymentsInfo.builder()
                  .envId(record.get("env_id", String.class))
                  .envName(record.get("env_name", String.class))
                  .tag(record.get("tag", String.class))
                  .pipelineExecutionId(record.get(PIPELINE_EXECUTION_SUMMARY_CD_ID, String.class))
                  .infrastructureIdentifier(record.get("infrastructureidentifier", String.class))
                  .infrastructureName(record.get("infrastructurename", String.class))
                  .artifactPath(record.get("artifact_image", String.class))
                  .serviceId(record.get(SERVICE_ID, String.class))
                  .serviceName(record.get(SERVICE_NAME, String.class))
                  .build();
          activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfo);
        });
        successfulOperation = true;
      } catch (Exception ex) {
        log.warn(String.format(QUERY_EXECUTION_FAILED_ERROR_MESSAGE, ex.getMessage(), totalTries), ex);
        totalTries++;
      }
    }
    return activeServiceDeploymentsInfoList;
  }

  private static class ServiceGitOpsInfo {
    final boolean isGitOps;
    final boolean isGitOpsMergeEnabled;

    ServiceGitOpsInfo(boolean isGitOps, boolean isGitOpsMergeEnabled) {
      this.isGitOps = isGitOps;
      this.isGitOpsMergeEnabled = isGitOpsMergeEnabled;
    }
  }

  private ServiceGitOpsInfo getServiceGitOpsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ServiceEntity> serviceEntity = serviceEntityServiceImpl.getMetadata(scopeInfo, serviceId, false);
    if (serviceEntity.isPresent()) {
      ServiceEntity service = serviceEntity.get();
      boolean isGitOps = Boolean.TRUE.equals(service.getGitOpsEnabled());
      boolean isK8s = ServiceDefinitionType.KUBERNETES.equals(service.getType());
      boolean gitOpsMergeEnabled =
          featureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES) && isK8s;
      return new ServiceGitOpsInfo(isGitOps, gitOpsMergeEnabled);
    }
    return new ServiceGitOpsInfo(false, false);
  }

  private Boolean isK8sOrHelm(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ServiceEntity> serviceEntity = serviceEntityServiceImpl.getMetadata(scopeInfo, serviceId, false);
    if (serviceEntity.isPresent()) {
      ServiceEntity service = serviceEntity.get();
      return ServiceDefinitionType.KUBERNETES.equals(service.getType())
          || ServiceDefinitionType.NATIVE_HELM.equals(service.getType());
    }
    return Boolean.FALSE;
  }

  public void validateDashboardRequestDuration(long numOfRequestedDays) {
    if (numOfRequestedDays > 366) {
      log.warn(String.format(DASHBOARD_DATA_BEING_QUERIED_FOR_MORE_THAN_A_YEAR, numOfRequestedDays));
    }
  }

  public void validateDashboardRequestDuration(long startTime, long endTime) {
    long numberOfDays = (long) Math.ceil((endTime - startTime) / (double) DAY_IN_MS);
    validateDashboardRequestDuration(numberOfDays);
  }

  protected Map<String, Pair<String, AuthorInfo>>
  processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(List<String> pipelineExecutionIdList,
      BiConsumer<Collection<String>, Map<String, Pair<String, AuthorInfo>>> action) {
    List<String> uniquePipelineExecutionIds = new ArrayList<>(new HashSet<>(pipelineExecutionIdList));
    Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap = new HashMap<>();

    int start = 0;
    while (start < uniquePipelineExecutionIds.size()) {
      int end = Math.min(start + IN_QUERY_ARRAY_MAX_SIZE, uniquePipelineExecutionIds.size());
      List<String> subList = uniquePipelineExecutionIds.subList(start, end);
      action.accept(subList, triggerAndAuthorInfoMap);
      start = end; // Move to the next batch
    }
    return triggerAndAuthorInfoMap;
  }

  private Retry fetchDefaultRetryMechanism() {
    RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(MAX_RETRY_COUNT) // Include the first attempt
            .waitDuration(Duration.ofSeconds(RETRY_WAIT_DURATION)) // Set wait duration between retries
            .retryExceptions(Exception.class) // Specify exceptions to retry on
            .build();

    return Retry.of("timescaleQueryRetry", retryConfig);
  }

  // Derived from the enum, so a new agent platform is excluded here the moment it is tagged with the category.
  private void excludeAiServiceTypes(Criteria criteria) {
    criteria.and(ServiceEntityKeys.type)
        .not()
        .in(ServiceDefinitionType.getPersistedTypeNamesForCategory(ServiceDefinitionCategory.AI_SERVICE));
  }

  protected static <T, R> Map<T, R> executeInBatches(
      List<T> ids, int batchSize, Function<List<T>, Map<T, R>> queryFunction) {
    List<T> uniqueIds = new ArrayList<>(new LinkedHashSet<>(ids));
    Map<T, R> resultMap = new HashMap<>();

    int start = 0;
    while (start < uniqueIds.size()) {
      int end = Math.min(start + batchSize, uniqueIds.size());
      List<T> subList = uniqueIds.subList(start, end);
      resultMap.putAll(queryFunction.apply(subList));
      start = end;
    }
    return resultMap;
  }
  private List<String> getParentUniqueIdsUnderChildScopes(String accountId, String orgId, String projectId) {
    Map<ScopeLevel, Map<String, ScopeInfo>> scopeLevelToUniqueIdsAndScopeInfoMap =
        timeScaleDAL.getUniqueIdsIncludingChildScope(accountId, orgId, projectId, false);
    return scopeLevelToUniqueIdsAndScopeInfoMap.values()
        .stream()
        .flatMap(innerMap -> innerMap.values().stream())
        .map(ScopeInfo::getUniqueId)
        .toList();
  }
}
