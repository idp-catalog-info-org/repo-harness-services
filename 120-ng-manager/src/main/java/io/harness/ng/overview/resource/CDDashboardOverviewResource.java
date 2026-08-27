/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static io.harness.NGDateUtils.getNumberOfDays;
import static io.harness.NGDateUtils.getStartTimeOfPreviousInterval;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ACCOUNT;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ORGANIZATION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.cd.NGServiceConstants;
import io.harness.cdng.service.beans.CustomSequenceDTO;
import io.harness.cdng.service.beans.ServiceDefinitionCategory;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.ApplicationSyncStatusList;
import io.harness.gitops.models.ApplicationSyncStatusQuery;
import io.harness.gitops.models.RecentDeploymentQuery;
import io.harness.gitops.models.RecentDeploymentsDetailsList;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitx.GitXUtils;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.dashboard.DashboardExecutionStatusInfo;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.dashboard.ServiceDeployments;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummary;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV2;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV3;
import io.harness.ng.overview.dto.ApplicationSyncStatusDTO;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.BasicServiceDeploymentMetrics;
import io.harness.ng.overview.dto.ChartVersionInstanceDetails;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.DashboardWorkloadDeploymentV2;
import io.harness.ng.overview.dto.DeploymentsSummaryInfo;
import io.harness.ng.overview.dto.DeploymentsSummaryPercentageInfo;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList;
import io.harness.ng.overview.dto.EnvIdCountPair;
import io.harness.ng.overview.dto.EnvironmentDeploymentInfo;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.ExecutionDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentDashboard;
import io.harness.ng.overview.dto.HealthDeploymentDashboardV2;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedOnChartVersionList;
import io.harness.ng.overview.dto.InstancesByBuildIdList;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.RecentDeploymentsDetailsListDTO;
import io.harness.ng.overview.dto.SequenceToggleDTO;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTO;
import io.harness.ng.overview.dto.ServiceDeploymentListInfo;
import io.harness.ng.overview.dto.ServiceDeploymentListInfoV2;
import io.harness.ng.overview.dto.ServiceDeploymentMetrics;
import io.harness.ng.overview.dto.ServiceDeploymentsList;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTO;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTOV2;
import io.harness.ng.overview.dto.ServiceGrowthTrendAndEnvBasedInfo;
import io.harness.ng.overview.dto.ServiceHeaderInfo;
import io.harness.ng.overview.dto.TimeValuePairListDTO;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.NGFeatureFlagHelperService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Api("dashboard")
@Path("/dashboard")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Tag(name = "Service Dashboard", description = "This contains APIs related to Service Dashboard")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CDDashboardOverviewResource {
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final AccessControlClient accessControlClient;
  @Inject private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject ScopeInfoService scopeResolverService;
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  private long epochShouldBeOfStartOfDay(long epoch) {
    return epoch - epoch % DAY_IN_MS;
  }
  @GET
  @Path("/deploymentHealth")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployment health", nickname = "getDeploymentHealth")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<HealthDeploymentDashboard> getDeploymentHealth(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    long previousStartInterval = startInterval - getStartTimeOfPreviousInterval(startInterval, numDays);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getHealthDeploymentDashboard(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousStartInterval));
  }

  @GET
  @Path("/deploymentFrequency")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployment frequency", nickname = "getDeploymentFrequency")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<DeploymentsSummaryInfo> getDeploymentFrequency(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getDeploymentsSummaryInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval));
  }

  @GET
  @Path("/failureFrequency")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get failure frequency", nickname = "getFailureFrequency")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<DeploymentsSummaryPercentageInfo> getFailureFrequency(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getFailuresDeploymentsSummaryInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval));
  }

  @GET
  @Path("/deploymentHealthV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployment health V2", nickname = "getDeploymentHealthV2")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<HealthDeploymentDashboardV2> getDeploymentHealthV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    long previousStartInterval = startInterval - getStartTimeOfPreviousInterval(startInterval, numDays);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getHealthDeploymentDashboardV2(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousStartInterval));
  }

  @GET
  @Path("/serviceDeployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service deployment", nickname = "getServiceDeployments")
  @Hidden
  @Deprecated
  public ResponseDTO<ServiceDeploymentInfoDTO> getServiceDeployment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGServiceConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGServiceConstants.END_TIME) long endTime,
      @QueryParam(NGServiceConstants.SERVICE_IDENTIFIER) String serviceIdentifier,
      @QueryParam(NGServiceConstants.BUCKET_SIZE_IN_DAYS) @DefaultValue("1") long bucketSizeInDays) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeployments(accountIdentifier, orgIdentifier,
          projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    }
  }

  @GET
  @Path("/serviceDeploymentsInfo")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service deployments info", nickname = "getServiceDeploymentsInfo")
  @Hidden
  public ResponseDTO<ServiceDeploymentListInfo> getDeploymentExecutionInfo(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGServiceConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGServiceConstants.END_TIME) long endTime,
      @QueryParam(NGServiceConstants.SERVICE_IDENTIFIER) String serviceIdentifier,
      @QueryParam(NGServiceConstants.BUCKET_SIZE_IN_DAYS) @DefaultValue("1") long bucketSizeInDays) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsInfoViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsInfo(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    }
  }

  @GET
  @Path("/serviceDeploymentsInfoV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service deployments info v2", nickname = "getServiceDeploymentsInfoV2")
  @Hidden
  public ResponseDTO<ServiceDeploymentListInfoV2> getDeploymentExecutionInfoV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGServiceConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGServiceConstants.END_TIME) long endTime,
      @QueryParam(NGServiceConstants.SERVICE_IDENTIFIER) String serviceIdentifier,
      @QueryParam(NGServiceConstants.BUCKET_SIZE_IN_DAYS) @DefaultValue("1") long bucketSizeInDays) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsInfoV2ViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsInfoV2(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
    }
  }

  @GET
  @Path("/serviceDeploymentMetrics")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service deployment metrics", nickname = "getServiceDeploymentMetrics")
  @Hidden
  public ResponseDTO<ServiceDeploymentMetrics> getServiceDeploymentMetrics(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGServiceConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGServiceConstants.END_TIME) long endTime,
      @QueryParam(NGServiceConstants.SERVICE_IDENTIFIER) String serviceIdentifier,
      @QueryParam(NGServiceConstants.BUCKET_SIZE_IN_DAYS) @DefaultValue("1") long bucketSizeInDays) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentMetrics(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
  }

  @GET
  @Path("/serviceDeploymentsList")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service deployments list", nickname = "getServiceDeploymentsList")
  @Hidden
  public ResponseDTO<ServiceDeploymentsList> getServiceDeploymentsList(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGServiceConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGServiceConstants.END_TIME) long endTime,
      @QueryParam(NGServiceConstants.SERVICE_IDENTIFIER) String serviceIdentifier,
      @QueryParam(NGServiceConstants.BUCKET_SIZE_IN_DAYS) @DefaultValue("1") long bucketSizeInDays) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDeploymentsList(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays));
  }

  @GET
  @Path("/deploymentExecution")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployment execution", nickname = "getDeploymentExecution")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<ExecutionDeploymentInfo> getDeploymentExecution(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getExecutionDeploymentDashboardViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getExecutionDeploymentDashboard(
          accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getDeployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployments", nickname = "getDeployments")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<DashboardExecutionStatusInfo> getDeployments(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @QueryParam("top") @DefaultValue("20") long days) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDeploymentActiveFailedRunningInfoViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, days, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDeploymentActiveFailedRunningInfo(
          accountIdentifier, orgIdentifier, projectIdentifier, days, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getWorkloads")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get workloads", nickname = "getWorkloads")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<DashboardWorkloadDeployment> getWorkloads(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @QueryParam(NGServiceConstants.ENVIRONMENT_TYPE) EnvironmentType envType) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    long previousStartInterval = getStartTimeOfPreviousInterval(startInterval, numDays);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDashboardWorkloadDeploymentViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, startInterval, endInterval, previousStartInterval, envType));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDashboardWorkloadDeployment(accountIdentifier,
          orgIdentifier, projectIdentifier, startInterval, endInterval, previousStartInterval, envType));
    }
  }

  @GET
  @Path("/getWorkloadsV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get workloads", nickname = "getWorkloadsV2")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<DashboardWorkloadDeploymentV2> getWorkloadsV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @QueryParam(NGServiceConstants.ENVIRONMENT_TYPE) EnvironmentType envType) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    long previousStartInterval = getStartTimeOfPreviousInterval(startInterval, numDays);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(
          cdOverviewDashboardService.getDashboardWorkloadDeploymentV2ViaJooq(accountIdentifier, orgIdentifier,
              projectIdentifier, startInterval, endInterval, previousStartInterval, envType));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDashboardWorkloadDeploymentV2(accountIdentifier,
          orgIdentifier, projectIdentifier, startInterval, endInterval, previousStartInterval, envType));
    }
  }

  @GET
  @Path("/servicesByDeployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get Most Active Services By deployment count", nickname = "servicesByDeployments")
  @NGAccessControlCheck(resourceType = PROJECT, permission = VIEW_PROJECT_PERMISSION)
  @Hidden
  public ResponseDTO<PageResponse<BasicServiceDeploymentMetrics>> getActiveServices(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @QueryParam(NGServiceConstants.ENVIRONMENT_TYPE) EnvironmentType envType,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) Integer page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) Integer size) {
    long numDays = getNumberOfDays(startInterval, endInterval);
    cdOverviewDashboardService.validateDashboardRequestDuration(numDays);

    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServices(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, envType, page, size));
  }

  @GET
  @Path("/serviceDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service details list", nickname = "getServiceDetails")
  @Hidden
  @Deprecated
  public ResponseDTO<ServiceDetailsInfoDTO> getServiceDeployments(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endTime,
      @Parameter(description = "Specifies the sorting criteria of the list") @QueryParam("sort") List<String> sort)
      throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsListViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, sort));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsList(
          accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, sort));
    }
  }

  @GET
  @Path("/serviceDetailsV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service details list v2", nickname = "getServiceDetailsV2")
  @Hidden
  public ResponseDTO<ServiceDetailsInfoDTOV2> getServiceDeploymentsV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endTime,
      @Parameter(description = "Specifies the sorting criteria of the list") @QueryParam("sort") List<String> sort,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsListV2ViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, sort, repoName));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsListV2(
          accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, sort, repoName));
    }
  }

  @GET
  @Path("/serviceDetailsV3")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service details list v3 with pagination", nickname = "getServiceDetailsV3")
  @Hidden
  public ResponseDTO<PageResponse<ServiceDetailsDTOV2>> getServiceDeploymentsV3(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startTime,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endTime,
      @Parameter(description = "Specifies the sorting criteria of the list") @QueryParam("sort") List<String> sort,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          "repoName") String repoName,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) throws Exception {
    cdOverviewDashboardService.validateDashboardRequestDuration(startTime, endTime);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsListV3ViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, sort, repoName, size, page, searchTerm));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getServiceDetailsListV3(accountIdentifier,
          orgIdentifier, projectIdentifier, startTime, endTime, sort, repoName, size, page, searchTerm));
    }
  }

  @GET
  @Path("/services")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get services list with pagination", nickname = "getServicesList")
  @Hidden
  public ResponseDTO<PageResponse<ServiceDashboardResponseDTO>> getServicesList(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Specifies the sorting criteria of the list") @QueryParam(
          NGCommonEntityConstants.SORT) List<String> sort,
      @Parameter(description = "Specifies the repo name of the entity", hidden = true) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Specifies the service type of the entity") @QueryParam(
          "serviceDefinitionType") String serviceDefinitionType,
      @Parameter(description = "Specifies the family of service types to list, for example AiService for AI agent "
              + "services. Ignored when serviceDefinitionType is set.") @QueryParam("category")
      ServiceDefinitionCategory category) throws Exception {
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    ServiceFilterPropertiesDTO filterPropertiesDTO = null;
    if (isNotEmpty(serviceDefinitionType) || category != null) {
      filterPropertiesDTO = ServiceFilterPropertiesDTO.builder()
                                .serviceTypes(isNotEmpty(serviceDefinitionType) ? List.of(serviceDefinitionType) : null)
                                .category(category)
                                .build();
    }

    PageResponse<ServiceDashboardResponseDTO> serviceEntities =
        cdOverviewDashboardService.getServicesList(accountIdentifier, orgIdentifier, projectIdentifier, sort, repoName,
            size, page, searchTerm, scopeInfo, filterPropertiesDTO);
    return ResponseDTO.newResponse(serviceEntities);
  }

  @GET
  @Path("/getServicesGrowthTrend")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service growth trend", nickname = "getServicesGrowthTrend")
  @Hidden
  public ResponseDTO<io.harness.ng.overview.dto.TimeValuePairListDTO<Integer>> getServicesGrowthTrend(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.TIME_GROUP_BY_TYPE) TimeGroupType timeGroupType) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getServicesGrowthTrend(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, timeGroupType));
  }
  @GET
  @Path("/getServicesGrowthTrendV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service growth trend", nickname = "getServicesGrowthTrendV2")
  @Hidden
  public ResponseDTO<ServiceGrowthTrendAndEnvBasedInfo> getServicesGrowthTrendV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.TIME_GROUP_BY_TYPE) TimeGroupType timeGroupType) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    return ResponseDTO.newResponse(cdOverviewDashboardService.getServicesGrowthTrendV2(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, timeGroupType));
  }

  @GET
  @Path("/getInstanceCountDetailsByService")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active service instance count breakdown by env type",
      nickname = "getActiveServiceInstanceCountBreakdown")
  @Hidden
  public ResponseDTO<InstanceCountDetailsByEnvTypeAndServiceId>
  getActiveServiceInstanceCountBreakdown(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) List<String> serviceId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceInstanceCountBreakdown(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getActiveServiceInstanceSummary")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active service instance summary", nickname = "getActiveServiceInstanceSummary")
  @Hidden
  public ResponseDTO<ActiveServiceInstanceSummary> getActiveServiceInstanceSummary(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.TIMESTAMP) long timestampInMs) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceInstanceSummary(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs));
  }

  @GET
  @Path("/getActiveServiceInstanceSummaryV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active service instance summary v2", nickname = "getActiveServiceInstanceSummaryV2")
  @Hidden
  public ResponseDTO<ActiveServiceInstanceSummaryV2> getActiveServiceInstanceSummaryV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.TIMESTAMP) long timestampInMs) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceInstanceSummaryV2(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs));
  }

  @GET
  @Path("/v3/getActiveServiceInstanceSummary")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active service instance summary v3", nickname = "getActiveServiceInstanceSummaryV3")
  @Hidden
  public ResponseDTO<ActiveServiceInstanceSummaryV3> getActiveServiceInstanceSummaryV3(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.TIMESTAMP) long timestampInMs) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceInstanceSummaryV3(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs));
  }

  @GET
  @Path("/getEnvBuildInstanceCountByService")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get list of unique environment and build ids with instance count", nickname = "getEnvBuildInstanceCount")
  @Hidden
  public ResponseDTO<EnvBuildIdAndInstanceCountInfoList>
  getEnvBuildInstanceCount(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getEnvBuildInstanceCountByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getActiveServiceInstances")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get list of artifact version, last pipeline execution, environment, infrastructure with instance count",
      nickname = "getActiveServiceInstances")
  @Hidden
  public ResponseDTO<InstanceGroupedByServiceList.InstanceGroupedByService>
  getEnvBuildInstanceCountV2(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedByArtifactList(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getActiveInstanceGroupedByEnvironment")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active instance count for a service grouped on environment, infrastructure, artifact",
      nickname = "getActiveInstanceGroupedByEnvironment")
  @Hidden
  public ResponseDTO<InstanceGroupedByEnvironmentList>
  getActiveInstanceGroupedByEnvironment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_GROUP_KEY) String envGrpId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, environmentId, envGrpId));
  }

  @GET
  @Path("/getActiveInstanceGroupedByArtifact")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get active instance count for a service grouped on artifact, environment, infrastructure",
      nickname = "getActiveInstanceGroupedByArtifact")
  @Hidden
  public ResponseDTO<InstanceGroupedOnArtifactList>
  getActiveInstanceGroupedByEnvironment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_GROUP_KEY) String envGrpId,
      @QueryParam(NGCommonEntityConstants.ARTIFACT) String displayName,
      @NotNull @QueryParam("filterOnArtifact") boolean filterOnArtifact) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedOnArtifactList(accountIdentifier,
        orgIdentifier, projectIdentifier, serviceId, environmentId, envGrpId, displayName, filterOnArtifact));
  }

  @GET
  @Path("/getActiveInstanceGroupedByChartVersion")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get active instance count for a service grouped on chart version, artifact, environment, infrastructure",
      nickname = "getActiveInstanceGroupedByChartVersion")
  @Hidden
  public ResponseDTO<InstanceGroupedOnChartVersionList>
  getActiveInstanceGroupedByChartVersion(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_GROUP_KEY) String envGrpId,
      @QueryParam(NGCommonEntityConstants.CHART_VERSION) String chartVersion,
      @NotNull @QueryParam(NGCommonEntityConstants.FILTER_ON_CHART_VERSION) boolean filterOnChartVersion) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedOnChartVersionList(accountIdentifier,
        orgIdentifier, projectIdentifier, serviceId, environmentId, envGrpId, chartVersion, filterOnChartVersion));
  }

  @GET
  @Path("/getInstancesByServiceEnvAndBuilds")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of buildId and instances", nickname = "getActiveInstancesByServiceIdEnvIdAndBuildIds")
  @Hidden
  public ResponseDTO<InstancesByBuildIdList> getActiveInstancesByServiceIdEnvIdAndBuildIds(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @NotNull @QueryParam(NGCommonEntityConstants.BUILDS_KEY) List<String> buildIds,
      @QueryParam(NGCommonEntityConstants.INFRA_IDENTIFIER) String infraIdentifier,
      @QueryParam(NGCommonEntityConstants.CLUSTER_IDENTIFIER) String clusterIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_EXECUTION_ID) String pipelineExecutionId) {
    return ResponseDTO.newResponse(
        cdOverviewDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceId, envId, buildIds, infraIdentifier, clusterIdentifier, pipelineExecutionId));
  }

  @GET
  @Path("/getInstancesDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get list of instances grouped by serviceId, buildId, environment, infrastructure and pipeline execution",
      nickname = "getInstancesDetails")
  @Hidden
  public ResponseDTO<InstanceDetailsByBuildId>
  getActiveInstancesDetails(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @QueryParam(NGCommonEntityConstants.INFRA_IDENTIFIER) String infraId,
      @QueryParam(NGCommonEntityConstants.CLUSTER_IDENTIFIER) String clusterId,
      @QueryParam(NGCommonEntityConstants.PIPELINE_EXECUTION_ID) String pipelineExecutionId,
      @QueryParam(NGCommonEntityConstants.BUILD_KEY) String buildId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveInstanceDetails(accountIdentifier, orgIdentifier,
        projectIdentifier, serviceId, envId, infraId, clusterId, pipelineExecutionId, buildId));
  }

  @GET
  @Path("/getActiveServiceInstanceDetailsGroupedByPipelineExecution")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of active instance metadata grouped by pipeline execution for a service",
      nickname = "getActiveServiceInstanceDetailsGroupedByPipelineExecution")
  @Hidden
  public ResponseDTO<InstanceDetailGroupedByPipelineExecutionList>
  getInstanceDetailGroupedByPipelineExecutionList(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_TYPE_KEY) EnvironmentType environmentType,
      @QueryParam(NGCommonEntityConstants.INFRA_IDENTIFIER) String infraId,
      @QueryParam(NGCommonEntityConstants.CLUSTER_IDENTIFIER) String clusterId,
      @QueryParam(NGCommonEntityConstants.ARTIFACT) String displayName,
      @QueryParam(NGCommonEntityConstants.CHART_VERSION) String chartVersion) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceDetailGroupedByPipelineExecution(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId, environmentType, infraId, clusterId,
        displayName, chartVersion, false));
  }

  @GET
  @Path("/getInstanceGrowthTrend")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance growth trend", nickname = "getInstanceGrowthTrend")
  @Hidden
  @Deprecated
  public ResponseDTO<io.harness.ng.overview.dto.TimeValuePairListDTO<Integer>> getInstanceGrowthTrend(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGrowthTrendViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGrowthTrend(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getInstanceCountHistory")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get instance count history", nickname = "getInstanceCountHistory")
  @Hidden
  public ResponseDTO<TimeValuePairListDTO<EnvIdCountPair>> getInstanceCountHistory(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceCountHistoryViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceCountHistory(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getDeploymentsByServiceId")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get deployments by serviceId", nickname = "getDeploymentsByServiceId")
  @Hidden
  public ResponseDTO<DeploymentsInfo> getDeploymentsByServiceId(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDeploymentsByServiceIdViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDeploymentsByServiceId(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getAllDeploymentsByServiceId")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all deployments by serviceId", nickname = "getAllDeploymentsByServiceId")
  @Hidden
  @Deprecated
  public ResponseDTO<ServiceDeployments> getAllDeploymentsByServiceId(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    cdOverviewDashboardService.validateDashboardRequestDuration(startInterval, endInterval);
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getAllDeploymentsByServiceIdViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getAllDeploymentsByServiceId(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval));
    }
  }

  @GET
  @Path("/getServiceHeaderInfo")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get service header info", nickname = "getServiceHeaderInfo")
  @Hidden
  public ResponseDTO<ServiceHeaderInfo> getServiceHeaderInfo(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @Parameter(description = "This contains details of Git Entity like Git Branch info",
          hidden = true) @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Specifies whether to load the entity from cache", hidden = true) @HeaderParam(
          "Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "Specifies whether to load the entity from fallback branch", hidden = true) @QueryParam(
          "loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(
          cdOverviewDashboardService.getServiceHeaderInfoViaJooq(accountIdentifier, orgIdentifier, projectIdentifier,
              serviceId, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch));
    } else {
      return ResponseDTO.newResponse(
          cdOverviewDashboardService.getServiceHeaderInfo(accountIdentifier, orgIdentifier, projectIdentifier,
              serviceId, GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch));
    }
  }

  @GET
  @Path("/getEnvArtifactDetailsByServiceId")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of unique environment and Artifact version filter by service_id",
      nickname = "getEnvArtifactDetailsByServiceId")
  @Hidden
  @Deprecated
  public ResponseDTO<EnvironmentDeploymentInfo>
  getEnvArtifactDetailsByServiceId(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getEnvironmentDeploymentDetailsByServiceIdViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getEnvironmentDeploymentDetailsByServiceId(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    }
  }

  @GET
  @Path("/getActiveServiceDeployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get Information about artifacts for a particular service, deployed to different environments",
      nickname = "getActiveServiceDeployments")
  @Hidden
  public ResponseDTO<InstanceGroupedByServiceList.InstanceGroupedByService>
  getActiveServiceDeployments(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceDeploymentsListViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getActiveServiceDeploymentsList(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    }
  }

  @POST
  @Path("/getEnvironmentInstanceDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get instance count and last artifact deployment detail in each environment for a particular service",
      nickname = "getEnvironmentInstanceDetails")
  @Hidden
  public ResponseDTO<EnvironmentGroupInstanceDetails>
  getEnvironmentInstanceDetails(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getEnvironmentInstanceDetailsViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, filterProperties, false));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getEnvironmentInstanceDetails(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, filterProperties, false));
    }
  }

  @GET
  @Path("/getArtifactInstanceDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get last deployment detail in each environment for artifacts having active instances of a service",
      nickname = "getArtifactInstanceDetails")
  @Hidden
  public ResponseDTO<ArtifactInstanceDetails>
  getArtifactInstanceDetails(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getArtifactInstanceDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getChartVersionInstanceDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Get last deployment detail in each environment for chart versions having active instances of a service",
      nickname = "getChartVersionInstanceDetails")
  @Hidden
  public ResponseDTO<ChartVersionInstanceDetails>
  getChartVersionInstanceDetails(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.getChartVersionInstanceDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getOpenTasks")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of pipelines failed and waiting for approval in 5 days", nickname = "getOpenTasks")
  @Hidden
  public ResponseDTO<OpenTaskDetails> getOpenTasks(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getOpenTasksViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getOpenTasks(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startInterval));
    }
  }

  @GET
  @Path("/getPipelineExecutionCount")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get pipeline execution count info", nickname = "getPipelineExecutionCount")
  @Operation(operationId = "pipelineExecutionCount",
      summary = "Get pipeline execution count for a service with grouping support on artifact and deployment status",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns pipeline execution count for a service with grouping support on artifact and "
                + "deployment status")
      })
  public ResponseDTO<PipelineExecutionCountInfo>
  getPipelineExecutionCount(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                            @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @Parameter(description = NGCommonEntityConstants.START_TIME_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.START_TIME) Long startInterval,
      @Parameter(description = NGCommonEntityConstants.END_TIME_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.END_TIME) Long endInterval,
      @Parameter(description = NGCommonEntityConstants.ARTIFACT_PATH_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ARTIFACT_PATH) String artifactPath,
      @Parameter(description = NGCommonEntityConstants.ARTIFACT_VERSION_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ARTIFACT_VERSION) String artifactVersion,
      @Parameter(description = NGCommonEntityConstants.ARTIFACT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ARTIFACT) String artifact,
      @Parameter(description = NGCommonEntityConstants.STATUS_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.STATUS) String status) {
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_ENTITY_CRUD_RBAC)) {
      if (isNotEmpty(projectIdentifier)) {
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, null),
            Resource.of(PROJECT, projectIdentifier), VIEW_PROJECT_PERMISSION);
      } else if (isNotEmpty(orgIdentifier)) {
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, null, null),
            Resource.of(ORGANIZATION, orgIdentifier), VIEW_ORGANIZATION_PERMISSION);
      } else {
        accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(null, null, null), Resource.of(ACCOUNT, accountIdentifier), VIEW_ACCOUNT_PERMISSION);
      }
    }
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getPipelineExecutionCountInfoViaJooq(accountIdentifier,
          orgIdentifier, projectIdentifier, serviceId, startInterval, endInterval, artifactPath, artifactVersion,
          artifact, status));
    } else {
      return ResponseDTO.newResponse(
          cdOverviewDashboardService.getPipelineExecutionCountInfo(accountIdentifier, orgIdentifier, projectIdentifier,
              serviceId, startInterval, endInterval, artifactPath, artifactVersion, artifact, status));
    }
  }

  @GET
  @Path("/customSequence")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Get custom sequence for env and env groups", nickname = "getCustomSequence")
  public ResponseDTO<CustomSequenceDTO> getCustomSequence(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getCustomSequenceViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    } else {
      return ResponseDTO.newResponse(
          cdOverviewDashboardService.getCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    }
  }

  @POST
  @Path("/customSequence")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Save custom sequence for env and env groups", nickname = "saveCustomSequence")
  public ResponseDTO<ServiceSequence> saveCustomSequence(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @RequestBody(
          required = true, description = "custom sequence for env and env grps") CustomSequenceDTO customSequenceDTO) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.saveCustomSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, customSequenceDTO));
  }

  @GET
  @Path("/defaultSequence")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Get default sequence for env and env groups", nickname = "DefaultSequence")
  public ResponseDTO<CustomSequenceDTO> getDefaultSequence(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    if (ngFeatureFlagHelperService.isEnabled(
            accountIdentifier, FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)) {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDefaultSequenceViaJooq(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    } else {
      return ResponseDTO.newResponse(cdOverviewDashboardService.getDefaultSequence(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
    }
  }

  @POST
  @Path("/useCustomSequence")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Save the status of current sequence of env cards ", nickname = "setCustomSequenceStatus")
  public ResponseDTO<ServiceSequence> useCustomSequence(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId,
      @NotNull @QueryParam("useCustomSequence") boolean useCustomSequence) {
    return ResponseDTO.newResponse(cdOverviewDashboardService.useCustomSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, useCustomSequence));
  }

  @GET
  @Path("/useCustomSequence")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "get the status of current sequence of env cards ", nickname = "getCustomSequenceStatus")
  public ResponseDTO<SequenceToggleDTO> useCustomSequence(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceId) {
    return ResponseDTO.newResponse(
        cdOverviewDashboardService.useCustomSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @GET
  @Path("/getGitOpsRecentDeployments")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get recent deployments for GitOps", nickname = "getGitOpsRecentDeployments")
  @Operation(operationId = "getGitOpsRecentDeployments", summary = "Get recent deployments for GitOps",
      description = "Gets recent deployments from GitOps service")
  @Hidden
  public ResponseDTO<RecentDeploymentsDetailsListDTO>
  getGitOpsRecentDeployments(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Start time in epoch milliseconds") @QueryParam(
          NGResourceFilterConstants.START_TIME) long startTime,
      @Parameter(description = "End time in epoch milliseconds") @QueryParam(
          NGResourceFilterConstants.END_TIME) long endTime,
      @Parameter(description = "Service reference") @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @Parameter(description = "Environment reference") @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envRef) {
    RecentDeploymentQuery query = RecentDeploymentQuery.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .startTime((int) startTime)
                                      .endTime((int) endTime)
                                      .serviceRef(serviceRef)
                                      .envRef(envRef)
                                      .build();

    try {
      Response<RecentDeploymentsDetailsList> response = gitopsResourceClient.recentDeployments(query).execute();
      if (!response.isSuccessful() || response.body() == null) {
        throw new InvalidRequestException("Failed to fetch recent deployments from GitOps service");
      }
      return ResponseDTO.newResponse(mapToRecentDeploymentsDetailsListDTO(response.body()));
    } catch (IOException e) {
      throw new InvalidRequestException("Error fetching recent deployments", e);
    }
  }

  @GET
  @Path("/getGitOpsAppSyncs")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get application sync status list for GitOps", nickname = "getGitOpsAppSyncs")
  @Operation(operationId = "getGitOpsAppSyncs", summary = "Get application sync status list for GitOps",
      description = "Gets application sync statuses from GitOps service")
  @Hidden
  public ResponseDTO<Page<ApplicationSyncStatusDTO>>
  getGitOpsAppSyncs(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Start time in epoch milliseconds") @QueryParam(
          NGResourceFilterConstants.START_TIME) long startTime,
      @Parameter(description = "End time in epoch milliseconds") @QueryParam(
          NGResourceFilterConstants.END_TIME) long endTime,
      @Parameter(description = "Service reference") @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @Parameter(description = "Environment reference") @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envRef,
      @Parameter(description = "Application name") @QueryParam(
          NGCommonEntityConstants.APPLICATION_NAME) String applicationName,
      @Parameter(description = "Operation phase") @QueryParam(
          "operationPhase") List<ApplicationSyncStatusQuery.SyncOperationPhase> operationPhase,
      @Parameter(description = "Page size") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Max(
          1000) Integer pageSize,
      @Parameter(description = "Page index") @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue(
          "0") Integer pageIndex) {
    Pageable pageRequest = PageRequest.of(pageIndex, pageSize);
    ApplicationSyncStatusQuery query = ApplicationSyncStatusQuery.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .applicationName(applicationName)
                                           .operationPhase(operationPhase)
                                           .startTime((int) startTime)
                                           .endTime((int) endTime)
                                           .serviceRef(serviceRef)
                                           .envRef(envRef)
                                           .pageSize(pageSize)
                                           .pageIndex(pageIndex)
                                           .build();
    try {
      Response<ApplicationSyncStatusList> response = gitopsResourceClient.listAppSyncs(query).execute();
      if (!response.isSuccessful() || response.body() == null) {
        throw new InvalidRequestException("Failed to fetch application sync statuses from GitOps service");
      }
      return ResponseDTO.newResponse(mapToApplicationSyncStatusDTOPage(response.body(), pageRequest));
    } catch (IOException e) {
      throw new InvalidRequestException("Error fetching application sync statuses", e);
    }
  }

  private RecentDeploymentsDetailsListDTO mapToRecentDeploymentsDetailsListDTO(RecentDeploymentsDetailsList source) {
    if (source == null) {
      return null;
    }

    List<RecentDeploymentsDetailsListDTO.DeploymentsDetails> contentDTO = null;
    if (source.getContent() != null) {
      contentDTO = source.getContent()
                       .stream()
                       .map(details
                           -> RecentDeploymentsDetailsListDTO.DeploymentsDetails.builder()
                                  .startedAt(details.getStartedAt())
                                  .rollback(details.getRollback())
                                  .deploy(details.getDeploy())
                                  .redeploy(details.getRedeploy())
                                  .succeeded(details.getSucceeded())
                                  .error(details.getError())
                                  .terminating(details.getTerminating())
                                  .failed(details.getFailed())
                                  .running(details.getRunning())
                                  .totalDeployments(details.getTotalDeployments())
                                  .failureRate(details.getFailureRate())
                                  .build())
                       .collect(Collectors.toList());
    }

    return RecentDeploymentsDetailsListDTO.builder()
        .content(contentDTO)
        .pageItemCount(source.getPageItemCount())
        .empty(source.getEmpty())
        .build();
  }

  Page<ApplicationSyncStatusDTO> mapToApplicationSyncStatusDTOPage(
      ApplicationSyncStatusList source, Pageable pageRequest) {
    if (source == null) {
      return null;
    }

    if (source.getTotalItems() == null) {
      source.setTotalItems(0);
      source.setTotalPages(0);
    }

    List<ApplicationSyncStatusDTO> content;
    if (source.getContent() == null) {
      content = new ArrayList<>();
    } else {
      content = source.getContent()
                    .stream()
                    .map(status
                        -> ApplicationSyncStatusDTO.builder()
                               .accountIdentifier(status.getAccountIdentifier())
                               .projectIdentifier(status.getProjectIdentifier())
                               .orgIdentifier(status.getOrgIdentifier())
                               .agentIdentifier(status.getAgentIdentifier())
                               .applicationName(status.getApplicationName())
                               .syncStatus(status.getSyncStatus())
                               .createdAt(status.getCreatedAt())
                               .lastModifiedAt(status.getLastModifiedAt())
                               .operationState(status.getOperationState())
                               .reqIdentifier(status.getReqIdentifier())
                               .lastKnownRevisionId(status.getLastKnownRevisionId())
                               .syncedBy(status.getSyncedBy())
                               .autoSyncCount(status.getAutoSyncCount())
                               .serviceRef(status.getServiceRef())
                               .envRef(status.getEnvRef())
                               .build())
                    .collect(Collectors.toCollection(() -> new ArrayList<>(source.getContent().size())));
    }

    return new PageImpl<>(content, pageRequest, source.getTotalItems());
  }
}
