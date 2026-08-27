/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.cdng.service.beans.ServiceDefinitionType.AI_AGENT;
import static io.harness.cdng.service.beans.ServiceDefinitionType.AWS_AGENT_CORE;
import static io.harness.cdng.service.beans.ServiceDefinitionType.GOOGLE_AGENT_RUNTIME;
import static io.harness.cdng.service.beans.ServiceDefinitionType.KUBERNETES;
import static io.harness.cdng.service.beans.ServiceDefinitionType.NATIVE_HELM;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;
import static io.harness.ng.overview.service.CDOverviewDashboardServiceImpl.INVALID_CHANGE_RATE;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.HARSHIT;
import static io.harness.rule.OwnerRule.KESHAV_GOEL;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PARTH_SHARMA;
import static io.harness.rule.OwnerRule.PRASHANTPAREEK;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SATENDRA;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.vivekveman;
import static io.harness.timescaledb.tables.StageExecution.STAGE_EXECUTION;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.cdng.service.beans.ServiceDefinitionCategory;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsDTO;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.ServiceSequenceService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo.ActiveServiceDeploymentsInfoBuilder;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.ChangeRate;
import io.harness.ng.overview.dto.ChartVersionInstanceDetails;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.DashboardWorkloadDeploymentV2;
import io.harness.ng.overview.dto.DeploymentChangeRatesV2;
import io.harness.ng.overview.dto.DeploymentCount;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentInfoDTO;
import io.harness.ng.overview.dto.GitOpsStageMetadata;
import io.harness.ng.overview.dto.IconDTO;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.LastWorkloadInfo;
import io.harness.ng.overview.dto.LatestServiceDeploymentResponseDTO;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.PipelineExecutionInfoDTO;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail.ServiceArtifactExecutionDetailBuilder;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTOV2;
import io.harness.ng.overview.dto.ServiceDeploymentMetrics;
import io.harness.ng.overview.dto.ServiceDeploymentV2;
import io.harness.ng.overview.dto.ServiceDeploymentsList;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.ServicePipelineWithRevertInfo;
import io.harness.ng.overview.dto.WorkloadCountInfo;
import io.harness.ng.overview.dto.WorkloadDateCountInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentInfoV2;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.rule.Owner;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImpl;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.ServiceKeys;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.jooq.tools.reflect.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class CDOverviewDashboardServiceImplTest extends NgManagerTestBase {
  @Mock private TimeScaleDBService timeScaleDBService;
  private DSLContext dslContext;
  @InjectMocks @Spy private CDOverviewDashboardServiceImpl cdOverviewDashboardService;
  @Mock private InstanceDashboardServiceImpl instanceDashboardService;
  @Mock private ServiceEntityService serviceEntityServiceImpl;
  @Mock private EnvironmentServiceImpl environmentService;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock private EnvironmentGroupServiceImpl environmentGroupService;
  @Mock private ServiceSequenceService serviceSequenceService;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private InstanceCountDetailsByEnvTypeAndServiceId instanceCountDetailsByEnvTypeAndServiceId;
  @Mock NGFeatureFlagHelperService featureFlagService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private TimeScaleDAL timeScaleDAL;
  @Mock ResultSet resultSet;
  @Mock private ScopeInfoService scopeInfoService;

  private final String ENVIRONMENT_1 = "env1";
  private final String ENVIRONMENT_2 = "env2";
  private final String ENVIRONMENT_3 = "env3";
  private final String ENVIRONMENT_GROUP_1 = "group1";
  private final String ENVIRONMENT_GROUP_2 = "group2";
  private final String ENVIRONMENT_NAME_1 = "envN1";
  private final String ENVIRONMENT_NAME_2 = "envN2";
  private final String ENVIRONMENT_GROUP_NAME_1 = "envgroupN1";
  private final String ENVIRONMENT_GROUP_NAME_2 = "envgroupN2";
  private final String INFRASTRUCTURE_1 = "infra1";
  private final String CHART_VERSION_1 = "chartVersion1";
  private final String CHART_VERSION_2 = "chartVersion2";
  private final String DISPLAY_NAME_1 = "display1:1";
  private final String DISPLAY_NAME_2 = "display2:2";
  private final String PLAN_EXECUTION_1 = "planexec:1";
  private final String PIPELINE_EXECUTION_SUMMARY_CD_ID_1 = "sumarryid1";
  private final String PIPELINE_EXECUTION_SUMMARY_CD_ID_2 = "sumarryid2";
  private final String PLAN_EXECUTION_2 = "planexec:2";
  private final String PLAN_EXECUTION_3 = "planexec:3";
  private final String ACCOUNT_ID = "accountID";
  private final String ORG_ID = "orgId";
  private final String PROJECT_ID = "projectId";
  private final String PARENT_UNIQUE_ID = "parentUniqueId";
  private final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                           .accountIdentifier(ACCOUNT_ID)
                                           .orgIdentifier(ORG_ID)
                                           .projectIdentifier(PROJECT_ID)
                                           .scopeType(ScopeLevel.PROJECT)
                                           .uniqueId(PARENT_UNIQUE_ID)
                                           .build();
  private final String SERVICE_ID = "serviceId";
  private final String SERVICE_NAME = "serviceName";
  private final String SERVICE_ID_2 = "org.serviceId2";
  private final String SERVICE_NAME_2 = "serviceName2";
  private final String TAG_1 = "1";
  private final String TAG_2 = "2";
  private final String ARTIFACT_PATH_1 = "display1";
  private final String ARTIFACT_PATH_2 = "display2";
  private final String SUCCESS = "SUCCESS";
  private final String FAILED = "FAILED";
  private static final String PIPELINE_1 = "pipeline1";
  private static final String PIPELINE_2 = "pipeline2";
  private static final String PIPELINE_EXECUTION_1 = "pipelineExecution1";
  private static final String PIPELINE_EXECUTION_2 = "pipelineExecution2";
  private static final String FAILURE_MESSAGE_1 = "fail1";
  private static final String FAILURE_MESSAGE_2 = "fail2";
  private static final long START_INTERVAL = 1619568000000L;
  private static final long END_INTERVAL = 1619999940000L;
  private static final long PREVIOUS_START_INTERVAL = 1619136000000L;

  InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution getSampleInstanceGroupedByPipelineExecution(
      String id, Long lastDeployedAt, int count, String name) {
    return new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(count, id, name, lastDeployedAt);
  }

  Map<String,
      Map<String,
          Map<String,
              Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                  Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
  getSampleServiceBuildEnvInfraMap() {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>> buildEnvInfraMap =
        new HashMap<>();

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap1 =
        new HashMap<>();
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap2 =
        new HashMap<>();

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap2 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap2 =
        new HashMap<>();

    infraPipelineExecutionMap1.put("infra1", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    infraPipelineExecutionMap1.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap2.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));

    clusterPipelineExecutionMap1.put("infra1", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    clusterPipelineExecutionMap1.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap2.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));

    envInfraMap1.put("env1", new MutablePair<>(infraPipelineExecutionMap1, clusterPipelineExecutionMap1));
    envInfraMap2.put("env2", new MutablePair<>(infraPipelineExecutionMap2, clusterPipelineExecutionMap2));

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap4 =
        new HashMap<>();
    infraPipelineExecutionMap4.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap4 =
        new HashMap<>();
    envInfraMap4.put("env1", new MutablePair<>(infraPipelineExecutionMap4, new HashMap<>()));

    buildEnvInfraMap.put("artifact1:1", envInfraMap1);
    buildEnvInfraMap.put("artifact2:2", envInfraMap2);
    buildEnvInfraMap.put(null, envInfraMap4);

    serviceBuildEnvInfraMap.put("svc1", buildEnvInfraMap);

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap3 =
        new HashMap<>();

    infraPipelineExecutionMap3.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap3 =
        new HashMap<>();
    envInfraMap3.put("env1", new MutablePair<>(infraPipelineExecutionMap3, new HashMap<>()));
    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>
        buildEnvInfraMap2 = new HashMap<>();
    buildEnvInfraMap2.put("artifact11:1", envInfraMap3);

    serviceBuildEnvInfraMap.put("svc2", buildEnvInfraMap2);

    return serviceBuildEnvInfraMap;
  }

  Map<IdentifierRef,
      Map<String,
          Map<String,
              Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                  Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
  getSampleServiceIdRefBuildEnvInfraMap() {
    Map<IdentifierRef,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>> buildEnvInfraMap =
        new HashMap<>();

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap1 =
        new HashMap<>();
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap2 =
        new HashMap<>();

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap2 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap2 =
        new HashMap<>();

    infraPipelineExecutionMap1.put("infra1", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    infraPipelineExecutionMap1.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap2.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));

    clusterPipelineExecutionMap1.put("infra1", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    clusterPipelineExecutionMap1.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap2.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));
    IdentifierRef serviceIdRef1 =
        IdentifierRef.builder().identifier("svc1").accountIdentifier("accountId").scope(Scope.ACCOUNT).build();
    IdentifierRef serviceIdRef2 = IdentifierRef.builder()
                                      .identifier("svc2")
                                      .accountIdentifier("accountId")
                                      .orgIdentifier("orgId")
                                      .scope(Scope.ORG)
                                      .build();
    envInfraMap1.put("env1", new MutablePair<>(infraPipelineExecutionMap1, clusterPipelineExecutionMap1));
    envInfraMap2.put("env2", new MutablePair<>(infraPipelineExecutionMap2, clusterPipelineExecutionMap2));

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap4 =
        new HashMap<>();
    infraPipelineExecutionMap4.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap4 =
        new HashMap<>();
    envInfraMap4.put("env1", new MutablePair<>(infraPipelineExecutionMap4, new HashMap<>()));

    buildEnvInfraMap.put("artifact1:1", envInfraMap1);
    buildEnvInfraMap.put("artifact2:2", envInfraMap2);
    buildEnvInfraMap.put(null, envInfraMap4);

    serviceBuildEnvInfraMap.put(serviceIdRef1, buildEnvInfraMap);

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap3 =
        new HashMap<>();

    infraPipelineExecutionMap3.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap3 =
        new HashMap<>();
    envInfraMap3.put("env1", new MutablePair<>(infraPipelineExecutionMap3, new HashMap<>()));
    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>
        buildEnvInfraMap2 = new HashMap<>();
    buildEnvInfraMap2.put("artifact11:1", envInfraMap3);

    serviceBuildEnvInfraMap.put(serviceIdRef2, buildEnvInfraMap2);

    return serviceBuildEnvInfraMap;
  }

  private AutoCloseable mocks;
  private PreparedStatement preparedStatement = mock(PreparedStatement.class);
  private Connection dbConnection = mock(Connection.class);

  @Before
  public void setUp() throws Exception {
    dslContext = DSL.using(SQLDialect.POSTGRES);
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(cdOverviewDashboardService).set("secondaryTimeScaleDBService", timeScaleDBService);
    Reflect.on(cdOverviewDashboardService).set("dslContext", dslContext);
    Reflect.on(cdOverviewDashboardService).set("BATCH_SIZE", 10);
    Reflect.on(cdOverviewDashboardService).set("IN_QUERY_ARRAY_MAX_SIZE", 50);
    when(timeScaleDBService.getDBConnection()).thenReturn(dbConnection);
    when(dbConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    lenient()
        .when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .uniqueId("uniqueId")
                        .build());

    Map<ScopeLevel, Map<String, ScopeInfo>> defaultScopeMap = new HashMap<>();
    Map<String, ScopeInfo> defaultScopeInfoMap = new HashMap<>();
    defaultScopeInfoMap.put("parentUniqueId1",
        ScopeInfo.builder()
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .scopeType(ScopeLevel.PROJECT)
            .uniqueId("parentUniqueId1")
            .build());
    defaultScopeMap.put(ScopeLevel.PROJECT, defaultScopeInfoMap);
    when(timeScaleDAL.getUniqueIdsIncludingChildScope(any(), any(), any(), anyBoolean())).thenReturn(defaultScopeMap);
  }

  private void mockScopeInfoServiceForEnvParentUniqueIds() {
    when(scopeInfoService.getScopeInfo(anyString(), any())).thenAnswer(invocation -> {
      Set<String> parentUniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (String parentUniqueId : parentUniqueIds) {
        scopeInfoMap.put(parentUniqueId,
            Optional.of(ScopeInfo.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .uniqueId(parentUniqueId)
                            .build()));
      }
      return scopeInfoMap;
    });
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  List<InstanceGroupedByServiceList.InstanceGroupedByService> getSampleListInstanceGroupedByService() {
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infra1")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 2l, 3, "a"),
                    getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infra2")
            .lastDeployedAt(0l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure4 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infra1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra1")
            .agentIdentifier("infra1")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 2l, 3, "a"),
                    getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra2")
            .agentIdentifier("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra2")
            .agentIdentifier("infra2")
            .lastDeployedAt(0l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b")))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment1 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(2l)
            .instanceGroupedByInfraList(
                Arrays.asList(instanceGroupedByInfrastructure1, instanceGroupedByInfrastructure2))
            .instanceGroupedByClusterList(Arrays.asList(instanceGroupedByCluster1, instanceGroupedByCluster2))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("env2")
            .lastDeployedAt(0l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure3))
            .instanceGroupedByClusterList(Arrays.asList(instanceGroupedByCluster3))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment3 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(1l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure4))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact1 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact1")
            .artifact("artifact1:1")
            .latest(true)
            .lastDeployedAt(2l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("2")
            .latest(false)
            .lastDeployedAt(0l)
            .artifactPath("artifact2")
            .artifact("artifact2:2")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment2))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact3 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion(null)
            .latest(false)
            .lastDeployedAt(1l)
            .artifactPath(null)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment3))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceName("svcN1")
            .serviceId("svc1")
            .lastDeployedAt(2l)
            .instanceGroupedByArtifactList(
                Arrays.asList(instanceGroupedByArtifact1, instanceGroupedByArtifact3, instanceGroupedByArtifact2))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructureV2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraName("infra1")
            .infraIdentifier("infra1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironmentV2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(1l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructureV2))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifactV2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactPath("artifact11")
            .artifactVersion("1")
            .lastDeployedAt(1l)
            .artifact("artifact11:1")
            .latest(true)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironmentV2))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc2")
            .serviceName("svcN2")
            .lastDeployedAt(1l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifactV2))
            .build();
    return Arrays.asList(instanceGroupedByService1, instanceGroupedByService);
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfo() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", "infra1",
        "infra1", null, null, "1", "a", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1",
        "a", 2l, "1", "artifact1:1", 2, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "2",
        "b", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", "infra2", "infra2", null, null, "1",
        "a", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env2", "env2", "infra2", "infra2", null, null, "2",
        "b", 0l, "2", "artifact2:2", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc2", "svcN2", "env1", "env1", "infra1", "infra1", null, null, "1",
        "a", 1l, "1", "artifact11:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, null, null, 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOps() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", null, null,
        "infra1", "infra1", "1", "a", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", null, null, "infra1", "infra1", "1",
        "a", 2l, "1", "artifact1:1", 2, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", null, null, "infra1", "infra1", "2",
        "b", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", null, null, "infra2", "infra2", "1",
        "a", 1l, "1", "artifact1:1", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env2", "env2", null, null, "infra2", "infra2", "2",
        "b", 0l, "2", "artifact2:2", 1, null, null, null);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceDeploymentsInfo> getSampleActiveServiceDeployments() {
    ActiveServiceDeploymentsInfoBuilder activeServiceDeploymentsInfoBuilder =
        ActiveServiceDeploymentsInfo.builder().serviceId("svc1").serviceName("svcN1");

    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = new ArrayList<>();

    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env1")
                                             .envName("envN1")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution1")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env1")
                                             .envName("envN1")
                                             .infrastructureIdentifier("infra2")
                                             .infrastructureName("infraN2")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution2")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env2")
                                             .envName("envN2")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution3")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env2")
                                             .envName("envN2")
                                             .infrastructureIdentifier("infra2")
                                             .infrastructureName("infraN2")
                                             .artifactPath("artifact1")
                                             .tag("2")
                                             .pipelineExecutionId("pipelineExecution4")
                                             .build());
    activeServiceDeploymentsInfoBuilder.serviceId("svc2").serviceName("svcN2");
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env3")
                                             .envName("envN3")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact2")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution5")
                                             .build());

    return activeServiceDeploymentsInfoList;
  }

  Map<String, ServicePipelineInfo> getSampleServicePipelineInfo() {
    Map<String, ServicePipelineInfo> servicePipelineInfoMap = new HashMap<>();

    servicePipelineInfoMap.put("pipelineExecution1",
        ServicePipelineInfo.builder()
            .planExecutionId("1")
            .identifier("pipeline1")
            .lastExecutedAt(1l)
            .pipelineExecutionId("pipelineExecution1")
            .build());
    servicePipelineInfoMap.put("pipelineExecution2",
        ServicePipelineInfo.builder()
            .planExecutionId("2")
            .identifier("pipeline2")
            .lastExecutedAt(2l)
            .pipelineExecutionId("pipelineExecution2")
            .build());
    servicePipelineInfoMap.put("pipelineExecution3",
        ServicePipelineInfo.builder()
            .planExecutionId("3")
            .identifier("pipeline3")
            .lastExecutedAt(3l)
            .pipelineExecutionId("pipelineExecution3")
            .build());
    servicePipelineInfoMap.put("pipelineExecution4",
        ServicePipelineInfo.builder()
            .planExecutionId("4")
            .identifier("pipeline4")
            .lastExecutedAt(4l)
            .pipelineExecutionId("pipelineExecution4")
            .build());
    servicePipelineInfoMap.put("pipelineExecution5",
        ServicePipelineInfo.builder()
            .planExecutionId("5")
            .identifier("pipeline5")
            .lastExecutedAt(5l)
            .pipelineExecutionId("pipelineExecution5")
            .build());

    return servicePipelineInfoMap;
  }

  List<InstanceGroupedByServiceList.InstanceGroupedByService>
  getSampleListInstanceGroupedByServiceForActiveDeployments() {
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "1", "pipeline1", 1l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infraN2")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "2", "pipeline2", 2l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(3l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "3", "pipeline3", 3l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure4 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infraN2")
            .lastDeployedAt(4l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "4", "pipeline4", 4l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure5 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(5l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "5", "pipeline5", 5l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment1 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("envN1")
            .lastDeployedAt(2l)
            .instanceGroupedByInfraList(
                Arrays.asList(instanceGroupedByInfrastructure2, instanceGroupedByInfrastructure1))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("envN2")
            .lastDeployedAt(3l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure3))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment3 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("envN2")
            .lastDeployedAt(4l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure4))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment4 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env3")
            .envName("envN3")
            .lastDeployedAt(5l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure5))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact1 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact1")
            .artifact("artifact1:1")
            .lastDeployedAt(3l)
            .instanceGroupedByEnvironmentList(
                Arrays.asList(instanceGroupedByEnvironment2, instanceGroupedByEnvironment1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("2")
            .artifactPath("artifact1")
            .artifact("artifact1:2")
            .latest(true)
            .lastDeployedAt(4l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment3))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact3 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact2")
            .artifact("artifact2:1")
            .latest(true)
            .lastDeployedAt(5l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment4))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc1")
            .serviceName("svcN1")
            .lastDeployedAt(4l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifact2, instanceGroupedByArtifact1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService2 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc2")
            .serviceName("svcN2")
            .lastDeployedAt(5l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifact3))
            .build();
    return Arrays.asList(instanceGroupedByService2, instanceGroupedByService1);
  }

  private List<EnvironmentInstanceCountModel> getEnvironmentInstanceCountModelList() {
    List<EnvironmentInstanceCountModel> environmentInstanceCountModelList = new ArrayList<>();
    environmentInstanceCountModelList.add(new EnvironmentInstanceCountModel(ENVIRONMENT_1, 2, null, null, null));
    environmentInstanceCountModelList.add(new EnvironmentInstanceCountModel(ENVIRONMENT_2, 1, null, null, null));
    return environmentInstanceCountModelList;
  }

  private List<Environment> getEnvironmentList() {
    List<Environment> environmentList = new ArrayList<>();
    environmentList.add(Environment.builder()
                            .name(ENVIRONMENT_NAME_1)
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId(PARENT_UNIQUE_ID)
                            .type(EnvironmentType.PreProduction)
                            .identifier(ENVIRONMENT_1)
                            .build());
    environmentList.add(Environment.builder()
                            .name(ENVIRONMENT_NAME_2)
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId(PARENT_UNIQUE_ID)
                            .type(EnvironmentType.Production)
                            .identifier(ENVIRONMENT_2)
                            .build());
    return environmentList;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(
        ENVIRONMENT_1, DISPLAY_NAME_1, "", 1l, PLAN_EXECUTION_1, null, null, null, null, null, null, null, null, null));
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(
        ENVIRONMENT_2, DISPLAY_NAME_2, "", 2l, PLAN_EXECUTION_2, null, null, null, null, null, null, null, null, null));
    return artifactDeploymentDetailModels;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList_ArtifactCard() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(
        ENVIRONMENT_1, DISPLAY_NAME_1, "", 1l, PLAN_EXECUTION_1, null, null, null, null, null, null, null, null, null));
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(
        ENVIRONMENT_2, DISPLAY_NAME_2, "", 2l, PLAN_EXECUTION_2, null, null, null, null, null, null, null, null, null));
    return artifactDeploymentDetailModels;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList_ChartVersionCard() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(ENVIRONMENT_1, DISPLAY_NAME_1, CHART_VERSION_1,
        1L, PLAN_EXECUTION_1, null, null, null, null, null, null, null, null, null));
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(ENVIRONMENT_2, DISPLAY_NAME_2, CHART_VERSION_2,
        2L, PLAN_EXECUTION_2, null, null, null, null, null, null, null, null, null));
    return artifactDeploymentDetailModels;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList_ChartVersionCard_WithoutGroup() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(ENVIRONMENT_1, DISPLAY_NAME_1, CHART_VERSION_1,
        2L, PLAN_EXECUTION_2, null, null, null, null, null, null, null, null, null));
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(ENVIRONMENT_1, DISPLAY_NAME_1, null, 1L,
        PLAN_EXECUTION_1, null, null, null, null, null, null, null, null, null));
    artifactDeploymentDetailModels.add(new ArtifactDeploymentDetailModel(ENVIRONMENT_2, DISPLAY_NAME_2, CHART_VERSION_2,
        3L, PLAN_EXECUTION_3, null, null, null, null, null, null, null, null, null));
    return artifactDeploymentDetailModels;
  }

  private List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> getEnvironmentGroupInstanceDetailList() {
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails = new ArrayList<>();
    environmentInstanceDetails.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_1)
            .name(ENVIRONMENT_GROUP_NAME_1)
            .isEnvGroup(true)
            .isDrift(false)
            .isRollback(true)
            .isRevert(false)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction))
            .count(2)
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_1)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .lastDeployedAt(1l)
                                                                     .build()))
            .build());
    environmentInstanceDetails.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .isRollback(false)
            .isRevert(true)
            .environmentTypes(Arrays.asList(EnvironmentType.Production, EnvironmentType.PreProduction))
            .count(3)
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());
    return environmentInstanceDetails;
  }

  private List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> getChartVersionInstanceDetailList() {
    List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> chartVersionInstanceDetails = new ArrayList<>();
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails1 =
        new ArrayList<>();
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_1)
            .name(ENVIRONMENT_GROUP_NAME_1)
            .isEnvGroup(true)
            .isDrift(false)
            .environmentTypes(List.of(EnvironmentType.PreProduction))
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_1)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .chartVersion(CHART_VERSION_1)
                                                                     .lastDeployedAt(1L)
                                                                     .build()))
            .build());
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .chartVersion(CHART_VERSION_2)
                                                         .lastDeployedAt(2L)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .chartVersion(CHART_VERSION_1)
                    .lastDeployedAt(1L)
                    .build()))
            .build());

    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails2 =
        new ArrayList<>();
    environmentInstanceDetails2.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .chartVersion(CHART_VERSION_2)
                                                         .lastDeployedAt(2L)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .chartVersion(CHART_VERSION_1)
                    .lastDeployedAt(1L)
                    .build()))
            .build());

    chartVersionInstanceDetails.add(
        ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
            .chartVersion(CHART_VERSION_1)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails1)
                                                 .build())
            .build());
    chartVersionInstanceDetails.add(
        ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
            .chartVersion(CHART_VERSION_2)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails2)
                                                 .build())
            .build());
    return chartVersionInstanceDetails;
  }

  private List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> getChartVersionInstanceDetailWithoutGroupList() {
    List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> chartVersionInstanceDetails = new ArrayList<>();
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails1 =
        new ArrayList<>();
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_1)
            .name(ENVIRONMENT_NAME_1)
            .isEnvGroup(false)
            .isDrift(true)
            .environmentTypes(List.of(EnvironmentType.PreProduction))
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .chartVersion(CHART_VERSION_1)
                                                                     .lastDeployedAt(2l)
                                                                     .build()))
            .build());

    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails2 =
        new ArrayList<>();
    environmentInstanceDetails2.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_1)
            .name(ENVIRONMENT_NAME_1)
            .isEnvGroup(false)
            .isDrift(false)
            .environmentTypes(List.of(EnvironmentType.PreProduction))
            .artifactDeploymentDetails(List.of(ArtifactDeploymentDetail.builder()
                                                   .envId(ENVIRONMENT_1)
                                                   .envName(ENVIRONMENT_NAME_1)
                                                   .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                   .artifact(DISPLAY_NAME_1)
                                                   .chartVersion(CHART_VERSION_1)
                                                   .lastDeployedAt(2L)
                                                   .build()))
            .build());

    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails3 =
        new ArrayList<>();
    environmentInstanceDetails3.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_2)
            .name(ENVIRONMENT_NAME_2)
            .isEnvGroup(false)
            .isDrift(false)
            .environmentTypes(List.of(EnvironmentType.Production))
            .artifactDeploymentDetails(List.of(ArtifactDeploymentDetail.builder()
                                                   .envId(ENVIRONMENT_2)
                                                   .envName(ENVIRONMENT_NAME_2)
                                                   .lastPipelineExecutionId(PLAN_EXECUTION_3)
                                                   .artifact(DISPLAY_NAME_2)
                                                   .chartVersion(CHART_VERSION_2)
                                                   .lastDeployedAt(3L)
                                                   .build()))
            .build());

    chartVersionInstanceDetails.add(
        ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
            .chartVersion("")
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails1)
                                                 .build())
            .build());
    chartVersionInstanceDetails.add(
        ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
            .chartVersion(CHART_VERSION_1)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails2)
                                                 .build())
            .build());
    chartVersionInstanceDetails.add(
        ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
            .chartVersion(CHART_VERSION_2)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails3)
                                                 .build())
            .build());
    return chartVersionInstanceDetails;
  }

  private List<ArtifactInstanceDetails.ArtifactInstanceDetail> getArtifactInstanceDetailList() {
    List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetails = new ArrayList<>();
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails1 =
        new ArrayList<>();
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_1)
            .name(ENVIRONMENT_GROUP_NAME_1)
            .isEnvGroup(true)
            .isDrift(false)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction))
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_1)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .lastDeployedAt(1l)
                                                                     .build()))
            .build());
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());

    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails2 =
        new ArrayList<>();
    environmentInstanceDetails2.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());

    artifactInstanceDetails.add(
        ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
            .artifact(DISPLAY_NAME_1)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails1)
                                                 .build())
            .build());
    artifactInstanceDetails.add(
        ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
            .artifact(DISPLAY_NAME_2)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails2)
                                                 .build())
            .build());
    return artifactInstanceDetails;
  }

  private void mockServiceEntityForNonGitOps() {
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
  }

  private void mockServiceEntityForGitOps() {
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(true).build()));
  }

  private void verifyServiceEntityCall(int times) {
    verify(serviceEntityServiceImpl, times(times)).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
  }

  private List<ServiceArtifactExecutionDetail> getServiceArtifactExecutionDetailList() {
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = new ArrayList<>();
    ServiceArtifactExecutionDetailBuilder serviceArtifactExecutionDetailBuilder =
        ServiceArtifactExecutionDetail.builder()
            .accountId(ACCOUNT_ID)
            .orgId(ORG_ID)
            .projectId(PROJECT_ID)
            .serviceRef(SERVICE_ID)
            .serviceName(SERVICE_NAME)
            .artifactTag(TAG_1)
            .artifactPath(ARTIFACT_PATH_1)
            .artifactDisplayName(DISPLAY_NAME_1)
            .serviceStartTime(7l)
            .pipelineExecutionSummaryCDId("7");
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.artifactDisplayName(null)
                                               .serviceStartTime(6l)
                                               .pipelineExecutionSummaryCDId("6")
                                               .build());
    serviceArtifactExecutionDetailList.add(
        serviceArtifactExecutionDetailBuilder.serviceStartTime(5l).pipelineExecutionSummaryCDId("6").build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.artifactDisplayName(DISPLAY_NAME_2)
                                               .artifactTag(TAG_2)
                                               .artifactPath(ARTIFACT_PATH_2)
                                               .serviceStartTime(4l)
                                               .pipelineExecutionSummaryCDId("6")
                                               .build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.serviceRef(SERVICE_ID_2)
                                               .serviceName(SERVICE_NAME_2)
                                               .artifactDisplayName(DISPLAY_NAME_2)
                                               .artifactTag(TAG_2)
                                               .artifactPath(ARTIFACT_PATH_2)
                                               .serviceStartTime(3l)
                                               .pipelineExecutionSummaryCDId("4")
                                               .build());
    return serviceArtifactExecutionDetailList;
  }

  private Map<String, String> getExecutionStatusMap() {
    Map<String, String> statusMap = new HashMap<>();
    statusMap.put("7", SUCCESS);
    statusMap.put("6", FAILED);
    statusMap.put("4", FAILED);
    return statusMap;
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_groupByServices() {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = getSampleServiceBuildEnvInfraMap();
    Map<String, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<String, String> serviceIdToLatestBuildMap = new HashMap<>();
    Map<String, String> artifactToArtifactSourceMap = new HashMap<>();

    serviceIdToLatestBuildMap.put("svc1", "artifact1:1");
    serviceIdToLatestBuildMap.put("svc2", "artifact11:1");

    artifactToArtifactSourceMap.put("test_artifact", "HAR");

    serviceIdToServiceNameMap.put("svc1", "svcN1");
    serviceIdToServiceNameMap.put("svc2", "svcN2");

    envIdToEnvNameMap.put("env1", "env1");
    envIdToEnvNameMap.put("env2", "env2");

    infraIdToInfraNameMap.put("infra1", "infra1");
    infraIdToInfraNameMap.put("infra2", "infra2");

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices =
        getSampleListInstanceGroupedByService();

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices1 =
        cdOverviewDashboardService.groupedByServices(serviceBuildEnvInfraMap, envIdToEnvNameMap, infraIdToInfraNameMap,
            serviceIdToServiceNameMap, infraIdToInfraNameMap, serviceIdToLatestBuildMap, artifactToArtifactSourceMap);

    assertThat(instanceGroupedByServices1).isEqualTo(instanceGroupedByServices);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void test_groupByServicesV2() {
    Map<IdentifierRef,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = getSampleServiceIdRefBuildEnvInfraMap();
    Map<IdentifierRef, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<IdentifierRef, String> serviceIdToLatestBuildMap = new HashMap<>();
    Map<String, String> artifactToArtifactSourceMap = new HashMap<>();
    Map<IdentifierRef, ActiveServiceInstanceInfoV2> serviceIdInstanceInfoMap = new HashMap<>();
    IdentifierRef serviceIdRef1 =
        IdentifierRef.builder().identifier("svc1").accountIdentifier("accountId").scope(Scope.ACCOUNT).build();
    IdentifierRef serviceIdRef2 = IdentifierRef.builder()
                                      .identifier("svc2")
                                      .accountIdentifier("accountId")
                                      .orgIdentifier("orgId")
                                      .scope(Scope.ORG)
                                      .build();
    serviceIdToLatestBuildMap.put(serviceIdRef1, "artifact1:1");
    serviceIdToLatestBuildMap.put(serviceIdRef2, "artifact11:1");

    artifactToArtifactSourceMap.put("test_artifact", "HAR");

    serviceIdToServiceNameMap.put(serviceIdRef1, "svcN1");
    serviceIdToServiceNameMap.put(serviceIdRef2, "svcN2");

    envIdToEnvNameMap.put("env1", "env1");
    envIdToEnvNameMap.put("env2", "env2");

    infraIdToInfraNameMap.put("infra1", "infra1");
    infraIdToInfraNameMap.put("infra2", "infra2");

    serviceIdInstanceInfoMap.put(serviceIdRef1,
        new ActiveServiceInstanceInfoV2("svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 2l,
            "1", "artifact1:1", 2, "orgId", "projectId1", "HAR"));
    serviceIdInstanceInfoMap.put(serviceIdRef2,
        new ActiveServiceInstanceInfoV2("svc2", "svcN2", "env2", "env2", "infra2", "infra2", null, null, "1", "a", 2l,
            "1", "artifact11:1", 2, "orgId", "projectId2", "HAR"));

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices =
        cdOverviewDashboardService.groupedByServicesV2(serviceBuildEnvInfraMap, envIdToEnvNameMap,
            infraIdToInfraNameMap, serviceIdToServiceNameMap, infraIdToInfraNameMap, serviceIdToLatestBuildMap,
            serviceIdInstanceInfoMap, artifactToArtifactSourceMap);

    assertThat(instanceGroupedByServices.size()).isEqualTo(2);
    assertThat(instanceGroupedByServices.get(0).getServiceId()).isEqualTo("account.svc1");
    assertThat(instanceGroupedByServices.get(1).getServiceId()).isEqualTo("org.svc2");
    assertThat(instanceGroupedByServices.get(0).getOrgIdentifier()).isEqualTo("orgId");
    assertThat(instanceGroupedByServices.get(0).getProjectIdentifier()).isEqualTo("projectId1");
    assertThat(instanceGroupedByServices.get(1).getProjectIdentifier()).isEqualTo("projectId2");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByServiceListHelper() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List = getSampleListActiveServiceInstanceInfo();
    activeServiceInstanceInfoV2List.addAll(getSampleListActiveServiceInstanceInfoGitOps());
    InstanceGroupedByServiceList instanceGroupedByServiceList =
        cdOverviewDashboardService.getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoV2List);
    assertThat(instanceGroupedByServiceList)
        .isEqualTo(InstanceGroupedByServiceList.builder()
                       .instanceGroupedByServiceList(getSampleListInstanceGroupedByService())
                       .build());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByServiceList() {
    Mockito
        .when(instanceDashboardService.getActiveServiceInstanceInfo(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, false, false))
        .thenReturn(getSampleListActiveServiceInstanceInfo());
    Mockito
        .when(instanceDashboardService.getActiveServiceInstanceInfo(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, true, false))
        .thenReturn(getSampleListActiveServiceInstanceInfoGitOps());
    InstanceGroupedByServiceList instanceGroupedByServiceList =
        InstanceGroupedByServiceList.builder()
            .instanceGroupedByServiceList(getSampleListInstanceGroupedByService())
            .build();
    assertThat(instanceGroupedByServiceList)
        .isEqualTo(cdOverviewDashboardService.getInstanceGroupedByServiceList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null));
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsListHelper() {
    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = getSampleActiveServiceDeployments();
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    doReturn(activeServiceDeploymentsInfoList)
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsInfo(anyString());
    doReturn(getSampleServicePipelineInfo()).when(cdOverviewDashboardService1).getPipelineExecutionDetails(anyList());
    InstanceGroupedByServiceList instanceGroupedByServiceList1 =
        InstanceGroupedByServiceList.builder()
            .instanceGroupedByServiceList(getSampleListInstanceGroupedByServiceForActiveDeployments())
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("parentUniqueId1")
                              .build();
    InstanceGroupedByServiceList instanceGroupedByServiceList2 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsListHelper(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, "build", "env", scopeInfo);
    assertThat(instanceGroupedByServiceList1).isEqualTo(instanceGroupedByServiceList2);
    verify(cdOverviewDashboardService1).getActiveServiceDeploymentsInfo(anyString());
    verify(cdOverviewDashboardService1).getPipelineExecutionDetails(anyList());
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsList() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId("uniqueId")
                              .build();

    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), isNull(), isNull(), any());

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), isNull(), isNull(), any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void test_getServiceDetailsListV3() throws Exception {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    for (long i = 0; i < 10; i++) {
      serviceEntities.add(ServiceEntity.builder()
                              .accountId(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .identifier("id")
                              .version(i)
                              .description("")
                              .build());
    }
    Page<ServiceEntity> serviceList = new PageImpl<>(serviceEntities, pageable, 10);
    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);
    ScopeInfo testScopeInfo = ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId("projectUniqueId").build();
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(testScopeInfo);
    doReturn(null)
        .when(cdOverviewDashboardService1)
        .getServiceDetailsInfoDTOV2(any(), any(), any(), anyLong(), anyLong(), any(), anyLong(), any());
    PageResponse<ServiceDetailsDTOV2> serviceDetailsInfoDTOV3 = cdOverviewDashboardService1.getServiceDetailsListV3(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, 6l, 7l, null, "repo", 10, 0, null);
    ArgumentCaptor<List<ServiceEntity>> argumentCaptor = forClass(List.class);
    verify(cdOverviewDashboardService1)
        .getServiceDetailsInfoDTOV2(
            any(), any(), any(), anyLong(), anyLong(), argumentCaptor.capture(), anyLong(), any());
    assertThat(argumentCaptor.getValue()).isEqualTo(serviceEntities);
    assertThat(typeExclusion(captureListCriteria()))
        .containsExactlyInAnyOrder(AI_AGENT.name(), GOOGLE_AGENT_RUNTIME.name(), AWS_AGENT_CORE.name());
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsList_EmptyCase() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .instanceGroupedByArtifactList(new ArrayList<>())
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId("uniqueId")
                              .build();

    doReturn(InstanceGroupedByServiceList.builder().instanceGroupedByServiceList(new ArrayList<>()).build())
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), isNull(), isNull(), any());

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_ID), isNull(), isNull(), any());
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_NonGitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    mockServiceEntityForNonGitOps();
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false);
    verifyServiceEntityCall(1);
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_GitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    mockServiceEntityForGitOps();
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, true, false);
    verifyServiceEntityCall(1);
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentCountMap() {
    List<EnvironmentInstanceCountModel> environmentInstanceCountModels = getEnvironmentInstanceCountModelList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2);
    Set<String> envIdResult = new HashSet<>();
    Map<String, Integer> envIdToCountMap = new HashMap<>();
    envIdToCountMap.put(ENVIRONMENT_1, 2);
    envIdToCountMap.put(ENVIRONMENT_2, 1);
    Map<String, Integer> envIdToCountMapResult = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentCountMap(
        environmentInstanceCountModels, envIdToCountMapResult, envIdResult);
    assertThat(envIds.size()).isEqualTo(envIdResult.size());
    assertThat(envIdToCountMap).isEqualTo(envIdToCountMapResult);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentNameAndTypeMap() {
    List<Environment> environments = getEnvironmentList();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    envIdToEnvNameMap.put(ENVIRONMENT_1, ENVIRONMENT_NAME_1);
    envIdToEnvNameMap.put(ENVIRONMENT_2, ENVIRONMENT_NAME_2);
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();
    envIdToEnvTypeMap.put(ENVIRONMENT_1, EnvironmentType.PreProduction);
    envIdToEnvTypeMap.put(ENVIRONMENT_2, EnvironmentType.Production);
    Map<String, String> envIdToEnvNameMapResult = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMapResult = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMapResult, envIdToEnvTypeMapResult, null);
    assertThat(envIdToEnvNameMap).isEqualTo(envIdToEnvNameMapResult);
    assertThat(envIdToEnvTypeMap).isEqualTo(envIdToEnvTypeMapResult);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentToArtifactDeploymentMap() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = getArtifactDeploymentDetailModelList();
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailMap = new HashMap<>();
    artifactDeploymentDetailMap.put(ENVIRONMENT_1,
        ArtifactDeploymentDetail.builder()
            .envId(ENVIRONMENT_1)
            .envName(ENVIRONMENT_NAME_1)
            .lastPipelineExecutionId(PLAN_EXECUTION_1)
            .artifact(DISPLAY_NAME_1)
            .lastDeployedAt(1l)
            .build());
    artifactDeploymentDetailMap.put(ENVIRONMENT_2,
        ArtifactDeploymentDetail.builder()
            .envId(ENVIRONMENT_2)
            .envName(ENVIRONMENT_NAME_2)
            .lastPipelineExecutionId(PLAN_EXECUTION_2)
            .artifact(DISPLAY_NAME_2)
            .lastDeployedAt(2l)
            .build());

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    envIdToEnvNameMap.put(ENVIRONMENT_1, ENVIRONMENT_NAME_1);
    envIdToEnvNameMap.put(ENVIRONMENT_2, ENVIRONMENT_NAME_2);
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailMapResult =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMap(
            artifactDeploymentDetailModels, envIdToEnvNameMap);
    assertArtifactDeploymentDetail(
        artifactDeploymentDetailMap.get(ENVIRONMENT_1), artifactDeploymentDetailMapResult.get(ENVIRONMENT_1));
  }

  private void assertArtifactDeploymentDetail(
      ArtifactDeploymentDetail artifactDeploymentDetail, ArtifactDeploymentDetail artifactDeploymentDetail1) {
    assertThat(artifactDeploymentDetail.getArtifact()).isEqualTo(artifactDeploymentDetail1.getArtifact());
    assertThat(artifactDeploymentDetail.getEnvId()).isEqualTo(artifactDeploymentDetail1.getEnvId());
    assertThat(artifactDeploymentDetail.getLastDeployedAt()).isEqualTo(artifactDeploymentDetail1.getLastDeployedAt());
    assertThat(artifactDeploymentDetail.getEnvName()).isEqualTo(artifactDeploymentDetail1.getEnvName());
    assertThat(artifactDeploymentDetail.getLastPipelineExecutionId())
        .isEqualTo(artifactDeploymentDetail1.getLastPipelineExecutionId());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getEnvironmentInstanceDetails() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = getArtifactDeploymentDetailModelList();
    List<EnvironmentInstanceCountModel> environmentInstanceCountModels = getEnvironmentInstanceCountModelList();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);
    Optional<ServiceSequence> serviceSequence = Optional.of(ServiceSequence.builder().build());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(serviceSequenceService.get(any(), any(), any(), any())).thenReturn(serviceSequence);
    EnvironmentGroupEntity environmentGroupEntity1 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_1)
                                                         .name(ENVIRONMENT_GROUP_NAME_1)
                                                         .envIdentifiers(Collections.singletonList(ENVIRONMENT_1))
                                                         .build();
    EnvironmentGroupEntity environmentGroupEntity2 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_2)
                                                         .name(ENVIRONMENT_GROUP_NAME_2)
                                                         .envIdentifiers(Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2))
                                                         .build();
    when(page.getContent()).thenReturn(Arrays.asList(environmentGroupEntity1, environmentGroupEntity2));
    Map<String, ServicePipelineWithRevertInfo> servicePipelineInfoMap = new HashMap<>();
    servicePipelineInfoMap.put(PLAN_EXECUTION_1,
        ServicePipelineWithRevertInfo.builder()
            .isRevertExecution(false)
            .identifier(PIPELINE_EXECUTION_SUMMARY_CD_ID_1)
            .planExecutionId(PLAN_EXECUTION_1)
            .pipelineExecutionId(PIPELINE_EXECUTION_1)
            .build());
    servicePipelineInfoMap.put(PLAN_EXECUTION_2,
        ServicePipelineWithRevertInfo.builder()
            .isRevertExecution(true)
            .identifier(PIPELINE_EXECUTION_SUMMARY_CD_ID_2)
            .planExecutionId(PLAN_EXECUTION_2)
            .pipelineExecutionId(PIPELINE_EXECUTION_2)
            .build());
    doReturn(servicePipelineInfoMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionDetailsWithRevertInfo(anyList());
    doReturn(Arrays.asList(PIPELINE_EXECUTION_SUMMARY_CD_ID_1))
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionsWhereRollbackOccurred(anyList());
    when(instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false))
        .thenReturn(environmentInstanceCountModels);
    when(instanceDashboardService.getLastDeployedInstance(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, true, false, false, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds))).thenReturn(environments);
    mockScopeInfoServiceForEnvParentUniqueIds();
    mockServiceEntityForNonGitOps();

    EnvironmentGroupInstanceDetails environmentInstanceDetails =
        EnvironmentGroupInstanceDetails.builder()
            .environmentGroupInstanceDetails(getEnvironmentGroupInstanceDetailList())
            .build();
    EnvironmentGroupInstanceDetails environmentInstanceDetailResult =
        cdOverviewDashboardService1.getEnvironmentInstanceDetails(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, false);
    assertThat(environmentInstanceDetails.getEnvironmentGroupInstanceDetails().size())
        .isEqualTo(environmentInstanceDetailResult.getEnvironmentGroupInstanceDetails().size());
    verify(instanceDashboardService)
        .getInstanceCountForEnvironmentFilteredByService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false);
    verify(instanceDashboardService)
        .getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, true, false, false, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds));
    verifyServiceEntityCall(1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceDetailGroupedByPipelineExecution() {
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution1 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_1)
            .planExecutionId(PIPELINE_EXECUTION_1)
            .lastDeployedAt(1l)
            .instances(Arrays.asList(
                InstanceDetailsDTO.builder().podName("1").build(), InstanceDetailsDTO.builder().podName("2").build()))
            .build();
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution2 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_2)
            .planExecutionId(PIPELINE_EXECUTION_2)
            .lastDeployedAt(2l)
            .instances(Arrays.asList(
                InstanceDetailsDTO.builder().podName("3").build(), InstanceDetailsDTO.builder().podName("4").build()))
            .build();
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionList =
            Arrays.asList(instanceDetailGroupedByPipelineExecution1, instanceDetailGroupedByPipelineExecution2);
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionListSorted =
            Arrays.asList(instanceDetailGroupedByPipelineExecution2, instanceDetailGroupedByPipelineExecution1);

    when(instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             SERVICE_ID, ENVIRONMENT_1, EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1, "", false,
             false, false, false))
        .thenReturn(instanceDetailGroupedByPipelineExecutionList);
    mockServiceEntityForNonGitOps();

    InstanceDetailGroupedByPipelineExecutionList instanceDetailGroupedByPipelineExecutionList1 =
        InstanceDetailGroupedByPipelineExecutionList.builder()
            .instanceDetailGroupedByPipelineExecutionList(instanceDetailGroupedByPipelineExecutionListSorted)
            .build();
    InstanceDetailGroupedByPipelineExecutionList instanceDetailGroupedByPipelineExecutionList2 =
        cdOverviewDashboardService.getInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            SERVICE_ID, ENVIRONMENT_1, EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1, "", false);

    assertThat(instanceDetailGroupedByPipelineExecutionList1).isEqualTo(instanceDetailGroupedByPipelineExecutionList2);
    verifyServiceEntityCall(2);
    verify(instanceDashboardService)
        .getActiveInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1,
            EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1, "", false, false, false, false);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList = new ArrayList<>();
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, false, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), anyList(), eq(false), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList1 =
          cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null);

      assertThat(instanceGroupedByEnvironmentList1).isEqualTo(instanceGroupedByEnvironmentList);
      verify(serviceEntityServiceImpl).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getArtifactInstanceDetails() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels =
        getArtifactDeploymentDetailModelList_ArtifactCard();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);

    when(instanceDashboardService.getLastDeployedInstance(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, false, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds))).thenReturn(environments);
    mockScopeInfoServiceForEnvParentUniqueIds();
    mockServiceEntityForNonGitOps();

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    EnvironmentGroupEntity environmentGroupEntity1 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_1)
                                                         .name(ENVIRONMENT_GROUP_NAME_1)
                                                         .envIdentifiers(Collections.singletonList(ENVIRONMENT_1))
                                                         .build();
    EnvironmentGroupEntity environmentGroupEntity2 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_2)
                                                         .name(ENVIRONMENT_GROUP_NAME_2)
                                                         .envIdentifiers(Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2))
                                                         .build();
    when(page.getContent()).thenReturn(Arrays.asList(environmentGroupEntity1, environmentGroupEntity2));

    ArtifactInstanceDetails artifactInstanceDetails =
        ArtifactInstanceDetails.builder().artifactInstanceDetails(getArtifactInstanceDetailList()).build();
    ArtifactInstanceDetails artifactInstanceDetailsResult =
        cdOverviewDashboardService.getArtifactInstanceDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    assertThat(artifactInstanceDetails.getArtifactInstanceDetails().size())
        .isEqualTo(artifactInstanceDetailsResult.getArtifactInstanceDetails().size());
    verify(instanceDashboardService)
        .getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, false, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds));
    verifyServiceEntityCall(1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void test_getChartVersionInstanceDetails() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels =
        getArtifactDeploymentDetailModelList_ChartVersionCard();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);

    when(instanceDashboardService.getLastDeployedInstance(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, true, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds))).thenReturn(environments);
    mockScopeInfoServiceForEnvParentUniqueIds();
    mockServiceEntityForNonGitOps();

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    EnvironmentGroupEntity environmentGroupEntity1 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_1)
                                                         .name(ENVIRONMENT_GROUP_NAME_1)
                                                         .envIdentifiers(Collections.singletonList(ENVIRONMENT_1))
                                                         .build();
    EnvironmentGroupEntity environmentGroupEntity2 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_2)
                                                         .name(ENVIRONMENT_GROUP_NAME_2)
                                                         .envIdentifiers(Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2))
                                                         .build();
    when(page.getContent()).thenReturn(Arrays.asList(environmentGroupEntity1, environmentGroupEntity2));

    ChartVersionInstanceDetails chartVersionInstanceDetailsReq =
        ChartVersionInstanceDetails.builder().chartVersionInstanceDetails(getChartVersionInstanceDetailList()).build();
    ChartVersionInstanceDetails chartVersionInstanceDetails =
        cdOverviewDashboardService.getChartVersionInstanceDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails().size())
        .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails().size())
        .isEqualTo(2);
    assertChart(chartVersionInstanceDetailsReq, chartVersionInstanceDetails);

    verify(instanceDashboardService)
        .getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, true, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds));
    verifyServiceEntityCall(1);
  }

  private void assertChart(ChartVersionInstanceDetails chartVersionInstanceDetailsReq,
      ChartVersionInstanceDetails chartVersionInstanceDetails) {
    assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails().size())
        .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails().size());
    for (int i = 0; i < chartVersionInstanceDetailsReq.getChartVersionInstanceDetails().size(); i++) {
      assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails().get(i).getChartVersion())
          .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails().get(i).getChartVersion());
      assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails()
                     .get(i)
                     .getEnvironmentGroupInstanceDetails()
                     .getEnvironmentGroupInstanceDetails()
                     .size())
          .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails()
                         .get(i)
                         .getEnvironmentGroupInstanceDetails()
                         .getEnvironmentGroupInstanceDetails()
                         .size());
      for (int j = 0; j < chartVersionInstanceDetailsReq.getChartVersionInstanceDetails()
                              .get(i)
                              .getEnvironmentGroupInstanceDetails()
                              .getEnvironmentGroupInstanceDetails()
                              .size();
           j++) {
        assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails()
                       .get(i)
                       .getEnvironmentGroupInstanceDetails()
                       .getEnvironmentGroupInstanceDetails()
                       .get(j))
            .usingRecursiveComparison()
            .ignoringCollectionOrder()
            .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails()
                           .get(i)
                           .getEnvironmentGroupInstanceDetails()
                           .getEnvironmentGroupInstanceDetails()
                           .get(j));
      }
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void test_getChartVersionInstanceDetails_without_group() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels =
        getArtifactDeploymentDetailModelList_ChartVersionCard_WithoutGroup();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);

    when(instanceDashboardService.getLastDeployedInstance(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, true, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds))).thenReturn(environments);
    mockScopeInfoServiceForEnvParentUniqueIds();
    mockServiceEntityForNonGitOps();

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(page.getContent()).thenReturn(Collections.emptyList());

    ChartVersionInstanceDetails chartVersionInstanceDetailsReq =
        ChartVersionInstanceDetails.builder()
            .chartVersionInstanceDetails(getChartVersionInstanceDetailWithoutGroupList())
            .build();
    ChartVersionInstanceDetails chartVersionInstanceDetails =
        cdOverviewDashboardService.getChartVersionInstanceDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    assertThat(chartVersionInstanceDetailsReq.getChartVersionInstanceDetails().size())
        .isEqualTo(chartVersionInstanceDetails.getChartVersionInstanceDetails().size())
        .isEqualTo(3);
    assertChart(chartVersionInstanceDetailsReq, chartVersionInstanceDetails);

    verify(instanceDashboardService)
        .getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false, true, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(any(), eq(envIds));
    verifyServiceEntityCall(1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedOnArtifactList() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList = new ArrayList<>();
    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1,
             SERVICE_ID, DISPLAY_NAME_1, false, true, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();

    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByArtifactListHelperV2(
                        eq(ACCOUNT_ID), anyList(), eq(false), any(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedOnArtifactList);

      InstanceGroupedOnArtifactList instanceGroupedOnArtifactList1 =
          cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, DISPLAY_NAME_1, true);

      assertThat(instanceGroupedOnArtifactList1).isEqualTo(instanceGroupedOnArtifactList);
      verify(serviceEntityServiceImpl).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID,
              DISPLAY_NAME_1, false, true, null, false, false);
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_OpenTasks() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("uniqueId1")
                              .build();
    String query = DashboardServiceHelper.buildOpenTaskQuery(SERVICE_ID, 1000l, scopeInfo);
    List<String> STATUS_LIST = Arrays
                                   .asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE,
                                       ExecutionStatus.FAILED, ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALWAITING)
                                   .stream()
                                   .map(ExecutionStatus::name)
                                   .collect(Collectors.toList());
    Map<String, String> pipelineExecutionToFailureMessageMap = new HashMap<>();
    pipelineExecutionToFailureMessageMap.put(PIPELINE_EXECUTION_1, FAILURE_MESSAGE_1);
    pipelineExecutionToFailureMessageMap.put(PIPELINE_EXECUTION_2, FAILURE_MESSAGE_2);

    List<ServicePipelineWithRevertInfo> servicePipelineRevertInfoList =
        Arrays.asList(ServicePipelineWithRevertInfo.builder()
                          .pipelineExecutionId(PIPELINE_EXECUTION_2)
                          .lastExecutedAt(1l)
                          .failureDetail(FAILURE_MESSAGE_2)
                          .build(),
            ServicePipelineWithRevertInfo.builder()
                .pipelineExecutionId(PIPELINE_EXECUTION_1)
                .lastExecutedAt(2l)
                .failureDetail(FAILURE_MESSAGE_1)
                .build());
    List<ServicePipelineInfo> servicePipelineInfoList = Arrays.asList(
        ServicePipelineInfo.builder().pipelineExecutionId(PIPELINE_EXECUTION_2).lastExecutedAt(1l).build(),
        ServicePipelineInfo.builder().pipelineExecutionId(PIPELINE_EXECUTION_1).lastExecutedAt(2l).build());
    List<ServicePipelineWithRevertInfo> servicePipelineInfoListSorted =
        Arrays.asList(servicePipelineRevertInfoList.get(1), servicePipelineRevertInfoList.get(0));
    Map<String, ServicePipelineInfo> servicePipelineInfoMap = new HashMap<>();
    servicePipelineInfoMap.put(PIPELINE_EXECUTION_1, servicePipelineInfoList.get(0));
    servicePipelineInfoMap.put(PIPELINE_EXECUTION_2, servicePipelineInfoList.get(1));
    doReturn(pipelineExecutionToFailureMessageMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    doReturn(servicePipelineInfoMap).when(cdOverviewDashboardService1).getPipelineExecutionDetails(any(), any());
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    OpenTaskDetails openTaskDetailsResult =
        cdOverviewDashboardService1.getOpenTasks(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, 1000l);
    OpenTaskDetails openTaskDetails =
        OpenTaskDetails.builder().pipelineDeploymentDetails(servicePipelineInfoListSorted).build();
    assertThat(openTaskDetails).isEqualTo(openTaskDetailsResult);
    verify(cdOverviewDashboardService1).getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    verify(cdOverviewDashboardService1).getPipelineExecutionDetails(any(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getPipelineExecutionCountInfo() throws InvalidRequestException {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = getServiceArtifactExecutionDetailList();
    Map<String, String> statusMap = getExecutionStatusMap();
    String queryExecutionIdAndArtifactDetails =
        "select accountid, orgidentifier, projectidentifier, service_id, service_name, artifact_display_name, "
        + "artifact_image, tag, pipeline_execution_summary_cd_id, service_startts, parent_unique_id from "
        + "service_infra_info where "
        + "parent_unique_id in ('parentUniqueId1') and service_id is "
        + "not null and service_startts >= 1 and service_startts <= 3 and service_id = 'serviceId' and "
        + "artifact_display_name = 'display1:1' and artifact_image = 'display1' and tag = '1'";
    String queryGetPipelineExecutionStatusMap =
        "select id, status from pipeline_execution_summary_cd where parent_unique_id in ('parentUniqueId1') and id = "
        + "any (?) and status = 'SUCCESS'";
    doReturn(serviceArtifactExecutionDetailList)
        .when(cdOverviewDashboardService1)
        .getExecutionIdAndArtifactDetails(eq(queryExecutionIdAndArtifactDetails), anyMap());
    doReturn(statusMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionStatusMap(
            statusMap.keySet().stream().collect(Collectors.toList()), queryGetPipelineExecutionStatusMap);
    PipelineExecutionCountInfo pipelineExecutionCountInfoResult =
        cdOverviewDashboardService1.getPipelineExecutionCountInfo(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, 1l, 3l, ARTIFACT_PATH_1, TAG_1, DISPLAY_NAME_1, SUCCESS);
    PipelineExecutionCountInfo.CountGroupedOnStatus countGroupedOnStatus1 =
        PipelineExecutionCountInfo.CountGroupedOnStatus.builder().count(1l).status(SUCCESS).build();
    PipelineExecutionCountInfo.CountGroupedOnStatus countGroupedOnStatus2 =
        PipelineExecutionCountInfo.CountGroupedOnStatus.builder().count(1l).status(FAILED).build();
    PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact1 =
        PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
            .count(2l)
            .artifact(DISPLAY_NAME_1)
            .artifactVersion(TAG_1)
            .artifactPath(ARTIFACT_PATH_1)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus1, countGroupedOnStatus2))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact2 =
        PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
            .count(1l)
            .artifact(DISPLAY_NAME_2)
            .artifactVersion(TAG_2)
            .artifactPath(ARTIFACT_PATH_2)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus2))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService1 =
        PipelineExecutionCountInfo.CountGroupedOnService.builder()
            .count(2l)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus1, countGroupedOnStatus2))
            .serviceReference("accountID/orgId/projectId/serviceId")
            .serviceName("serviceName")
            .executionCountGroupedOnArtifactList(Arrays.asList(countGroupedOnArtifact2, countGroupedOnArtifact1))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService2 =
        PipelineExecutionCountInfo.CountGroupedOnService.builder()
            .count(1l)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus2))
            .serviceReference("accountID/orgId/serviceId2")
            .serviceName("serviceName2")
            .executionCountGroupedOnArtifactList(Arrays.asList(countGroupedOnArtifact2))
            .build();
    PipelineExecutionCountInfo pipelineExecutionCountInfo =
        PipelineExecutionCountInfo.builder()
            .executionCountGroupedOnServiceList(Arrays.asList(countGroupedOnService1, countGroupedOnService2))
            .build();
    assertThat(pipelineExecutionCountInfo).isEqualTo(pipelineExecutionCountInfoResult);
    verify(cdOverviewDashboardService1)
        .getPipelineExecutionStatusMap(
            statusMap.keySet().stream().collect(Collectors.toList()), queryGetPipelineExecutionStatusMap);
    verify(cdOverviewDashboardService1)
        .getExecutionIdAndArtifactDetails(eq(queryExecutionIdAndArtifactDetails), anyMap());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getDeploymentIconMap() throws InvalidRequestException, IOException {
    String yamlForServiceWithTemplate =
        "service:\n  name: s-2\n  identifier: s1\n  serviceDefinition:\n    type: CustomDeployment\n    spec:\n      "
        + "customDeploymentRef:\n        templateRef: temp1\n        versionLabel: \"1\"\n  gitOpsEnabled: false\n";

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .yaml(yamlForServiceWithTemplate)
                                      .identifier("s1")
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .build();
    Map<String, Set<String>> serviceIdToDeploymentTypeMap = new HashMap<>();
    serviceIdToDeploymentTypeMap.put("s1", new HashSet<>(Arrays.asList("CustomDeployment")));

    Call<ResponseDTO<PageResponse<TemplateMetadataSummaryResponseDTO>>> callRequest = mock(Call.class);

    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
            .templateIdentifiers(Arrays.asList("temp1"))
            .build();

    Mockito
        .when(templateResourceClient.listTemplateMetadata(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, STABLE_TEMPLATE_TYPE, 0, 1, templateFilterPropertiesDTO))
        .thenReturn(callRequest);

    PageResponse<TemplateMetadataSummaryResponseDTO> pageResponse =
        PageResponse.<TemplateMetadataSummaryResponseDTO>builder()
            .content(Collections.singletonList(TemplateMetadataSummaryResponseDTO.builder()
                                                   .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                                   .icon("IconString")
                                                   .identifier("temp1")
                                                   .build()))
            .totalPages(1)
            .pageIndex(0)
            .pageSize(1)
            .build();

    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));

    Map<String, Set<IconDTO>> resultMap = cdOverviewDashboardService.getDeploymentIconMap(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList(serviceEntity), serviceIdToDeploymentTypeMap, null);

    List<IconDTO> iconDTO = new ArrayList<>(resultMap.get("s1"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("IconString");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getDeploymentIconMapWithMultipleServices() throws InvalidRequestException, IOException {
    String yamlForServiceWithTemplate =
        "service:\n  name: s-2\n  identifier: s2\n  serviceDefinition:\n    type: CustomDeployment\n    spec:\n      "
        + "customDeploymentRef:\n        templateRef: temp1\n        versionLabel: \"1\"\n  gitOpsEnabled: false\n";

    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .yaml(yamlForServiceWithTemplate)
                                       .identifier("s1")
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_ID)
                                       .projectIdentifier(PROJECT_ID)
                                       .build();
    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .yaml(yamlForServiceWithTemplate)
                                       .identifier("s2")
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_ID)
                                       .projectIdentifier(PROJECT_ID)
                                       .build();

    Map<String, Set<String>> serviceIdToDeploymentTypeMap = new HashMap<>();
    serviceIdToDeploymentTypeMap.put("s1", new HashSet<>(Arrays.asList("CustomDeployment")));
    serviceIdToDeploymentTypeMap.put("s2", new HashSet<>(Arrays.asList("K8s")));

    Call<ResponseDTO<PageResponse<TemplateMetadataSummaryResponseDTO>>> callRequest = mock(Call.class);

    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
            .templateIdentifiers(Arrays.asList("temp1"))
            .build();

    Mockito
        .when(templateResourceClient.listTemplateMetadata(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, STABLE_TEMPLATE_TYPE, 0, 1, templateFilterPropertiesDTO))
        .thenReturn(callRequest);

    PageResponse<TemplateMetadataSummaryResponseDTO> pageResponse =
        PageResponse.<TemplateMetadataSummaryResponseDTO>builder()
            .content(Collections.singletonList(TemplateMetadataSummaryResponseDTO.builder()
                                                   .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                                   .icon("IconString")
                                                   .identifier("temp1")
                                                   .build()))
            .totalPages(1)
            .pageIndex(0)
            .pageSize(1)
            .build();

    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));

    Map<String, Set<IconDTO>> resultMap = cdOverviewDashboardService.getDeploymentIconMap(ACCOUNT_ID, ORG_ID,
        PROJECT_ID, Arrays.asList(serviceEntity1, serviceEntity2), serviceIdToDeploymentTypeMap, null);

    List<IconDTO> iconDTO = new ArrayList<>(resultMap.get("s1"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("IconString");
    iconDTO = new ArrayList<>(resultMap.get("s2"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentListRevamp() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);

    when(page.getContent()).thenReturn(Collections.emptyList());
    ArgumentCaptor<List<ActiveServiceInstanceInfoWithEnvType>> argumentCaptor = forClass(List.class);

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), argumentCaptor.capture(), eq(false), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList1 =
          cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypes = argumentCaptor.getValue();
      assertThat(activeServiceInstanceInfoWithEnvTypes.size()).isEqualTo(2);
      assertThat(activeServiceInstanceInfoWithEnvTypes.get(0).getEnvIdentifier()).isEqualTo(ENVIRONMENT_1);
      assertThat(activeServiceInstanceInfoWithEnvTypes.get(1).getEnvIdentifier()).isEqualTo(ENVIRONMENT_2);

      assertThat(instanceGroupedByEnvironmentList1).isEqualTo(instanceGroupedByEnvironmentList);
      verify(serviceEntityServiceImpl).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactListRevamp() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, ARTIFACT_PATH_1));
    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1,
             SERVICE_ID, DISPLAY_NAME_1, false, true, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();

    when(environmentGroupService.list(any(), any())).thenReturn(mock(Page.class));

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByArtifactListHelperV2(
                        eq(ACCOUNT_ID), anyList(), eq(false), any(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedOnArtifactList);

      cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, DISPLAY_NAME_1, false);

      verify(instanceDashboardService, times(1))
          .getActiveServiceInstanceInfoWithEnvType(
              any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());
      verify(serviceEntityServiceImpl).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID,
              DISPLAY_NAME_1, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetServiceDeploymentMetrics() throws Exception {
    long prevStartInterval = 1619136000000L;
    long startInterval = 1619568000000L;
    long endInterval = 1619999940000L;

    Callable<DeploymentChangeRatesV2> getDeploymentChangeRates = ()
        -> DeploymentChangeRatesV2.builder()
               .frequency(0)
               .frequencyChangeRate(new ChangeRate((double) 0))
               .failureRate(0)
               .failureRateChangeRate(new ChangeRate((double) 0))
               .build();

    List<ServiceDeploymentV2> executionDeploymentList = new ArrayList<>();
    List<ServiceDeploymentV2> prevExecutionDeploymentList = new ArrayList<>();
    prevExecutionDeploymentList.add(ServiceDeploymentV2.builder()
                                        .time(1619136000000L)
                                        .deployments(DeploymentCount.builder().total(1).success(1).failure(0).build())
                                        .rate(getDeploymentChangeRates.call())
                                        .build());
    prevExecutionDeploymentList.add(ServiceDeploymentV2.builder()
                                        .time(1619222400000L)
                                        .deployments(DeploymentCount.builder().total(4).success(3).failure(0).build())
                                        .rate(getDeploymentChangeRates.call())
                                        .build());
    prevExecutionDeploymentList.add(ServiceDeploymentV2.builder()
                                        .time(1619308800000L)
                                        .deployments(DeploymentCount.builder().total(1).success(0).failure(1).build())
                                        .rate(getDeploymentChangeRates.call())
                                        .build());
    prevExecutionDeploymentList.add(ServiceDeploymentV2.builder()
                                        .time(1619395200000L)
                                        .deployments(DeploymentCount.builder().total(3).success(1).failure(2).build())
                                        .rate(getDeploymentChangeRates.call())
                                        .build());
    prevExecutionDeploymentList.add(ServiceDeploymentV2.builder()
                                        .time(1619481600000L)
                                        .deployments(DeploymentCount.builder().total(1).success(0).failure(1).build())
                                        .rate(getDeploymentChangeRates.call())
                                        .build());

    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619568000000L)
                                    .deployments(DeploymentCount.builder().total(2).success(1).failure(0).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619654400000L)
                                    .deployments(DeploymentCount.builder().total(0).success(0).failure(0).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619740800000L)
                                    .deployments(DeploymentCount.builder().total(3).success(1).failure(2).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619827200000L)
                                    .deployments(DeploymentCount.builder().total(4).success(2).failure(1).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619913600000L)
                                    .deployments(DeploymentCount.builder().total(1).success(0).failure(1).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());

    ServiceDeploymentInfoDTOV2 serviceDeploymentListWrap =
        ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(executionDeploymentList).build();
    ServiceDeploymentInfoDTOV2 prevExecutionDeploymentWrap =
        ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(prevExecutionDeploymentList).build();

    doReturn(serviceDeploymentListWrap)
        .when(cdOverviewDashboardService)
        .getServiceDeploymentsV3(
            "acc", "org", "pro", startInterval, endInterval, null, 1, Arrays.asList("parentUniqueId1"));
    doReturn(prevExecutionDeploymentWrap)
        .when(cdOverviewDashboardService)
        .getServiceDeploymentsV3(
            "acc", "org", "pro", prevStartInterval, startInterval, null, 1, Arrays.asList("parentUniqueId1"));

    ServiceDeploymentMetrics deploymentsExecutionInfo = cdOverviewDashboardService.getServiceDeploymentMetrics(
        "acc", "org", "pro", startInterval, endInterval, null, 1);

    assertThat(deploymentsExecutionInfo.getTotalDeployments()).isEqualTo(10);
    assertThat(deploymentsExecutionInfo.getFrequency()).isEqualTo(2.0);
    assertThat(deploymentsExecutionInfo.getFailureRate()).isEqualTo(40.0);
    assertThat(deploymentsExecutionInfo.getTotalDeploymentsChangeRate()).isEqualTo(new ChangeRate((double) 0));
    assertThat(deploymentsExecutionInfo.getFailureRateChangeRate()).isEqualTo(new ChangeRate((double) 0));
    assertThat(deploymentsExecutionInfo.getFrequencyChangeRate()).isEqualTo(new ChangeRate((double) 0));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetServiceDeploymentsList() throws Exception {
    long startInterval = 1619568000000L;
    long endInterval = 1619999940000L;

    Callable<DeploymentChangeRatesV2> getDeploymentChangeRates = ()
        -> DeploymentChangeRatesV2.builder()
               .frequency(0)
               .frequencyChangeRate(new ChangeRate((double) 0))
               .failureRate(0)
               .failureRateChangeRate(new ChangeRate((double) 0))
               .build();

    List<ServiceDeploymentV2> executionDeploymentList = new ArrayList<>();

    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619568000000L)
                                    .deployments(DeploymentCount.builder().total(2).success(1).failure(0).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619654400000L)
                                    .deployments(DeploymentCount.builder().total(0).success(0).failure(0).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619740800000L)
                                    .deployments(DeploymentCount.builder().total(3).success(1).failure(2).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619827200000L)
                                    .deployments(DeploymentCount.builder().total(4).success(2).failure(1).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());
    executionDeploymentList.add(ServiceDeploymentV2.builder()
                                    .time(1619913600000L)
                                    .deployments(DeploymentCount.builder().total(1).success(0).failure(1).build())
                                    .rate(getDeploymentChangeRates.call())
                                    .build());

    ServiceDeploymentInfoDTOV2 serviceDeploymentListWrap =
        ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(executionDeploymentList).build();

    doReturn(serviceDeploymentListWrap)
        .when(cdOverviewDashboardService)
        .getServiceDeploymentsV3(
            "acc", "org", "pro", startInterval, endInterval, null, 1, Arrays.asList("parentUniqueId1"));

    ServiceDeploymentsList deploymentsExecutionInfo =
        cdOverviewDashboardService.getServiceDeploymentsList("acc", "org", "pro", startInterval, endInterval, null, 1);

    assertThat(deploymentsExecutionInfo.getStartTime()).isEqualTo(startInterval);
    assertThat(deploymentsExecutionInfo.getEndTime()).isEqualTo(endInterval);
    assertThat(deploymentsExecutionInfo.getServiceDeploymentList()).isEqualTo(executionDeploymentList);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactListRevampForArtifactFilter() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, null),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, null),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, null));

    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1,
             SERVICE_ID, DISPLAY_NAME_1, false, true, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    when(environmentGroupService.list(any(), any())).thenReturn(mock(Page.class));

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), any(), any(), any()))
        .thenReturn(getEnvironmentList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByArtifactListHelperV2(
                        eq(ACCOUNT_ID), anyList(), eq(false), any(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedOnArtifactList);
      //      when(DashboardServiceHelper.getInstanceGroupedByArtifactListHelper(
      //              activeServiceInstanceInfoWithEnvTypeList, false, null, null))
      //              .thenReturn(instanceGroupedOnArtifactList);

      cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, "", true);

      verify(instanceDashboardService, times(2))
          .getActiveServiceInstanceInfoWithEnvType(
              any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());
      verify(serviceEntityServiceImpl).getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false));
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, true, null, false, false);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildFetchTopServicesWithMetricsWhenEnvTypeIsNull() {
    String expectedSql = "select \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\", "
        + "count(*) as \"status_count\" "
        + "from \"public\".\"service_infra_info\" "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\"";
    assertThat(cdOverviewDashboardService
                   .buildTopServicesByDeploymentsQuery(
                       null, ACCOUNT_ID, ORG_ID, PROJECT_ID, 10L, 100L, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSql);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildFetchTopServicesWithMetricsWhenEnvTypeIsNotNull() {
    String expectedSql = "select \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\", "
        + "count(*) as \"status_count\" "
        + "from \"public\".\"service_infra_info\" "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"env_type\" = ? "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\"";
    assertThat(cdOverviewDashboardService
                   .buildTopServicesByDeploymentsQuery(EnvironmentType.PreProduction, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                       10L, 100L, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSql);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildFetchTopServicesWithMetricsWhenEnvTypeIsNullWithPageSize() {
    PageRequest pageable = PageRequest.of(0, 5);
    String expectedSQL = "with \"top_services\" as ("
        + "select \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", count(*) as \"total_deployments\" "
        + "from \"public\".\"service_infra_info\" "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\" "
        + "order by total_deployments desc limit ? offset ?) "
        + "select top_services.service_id as \"service_id\", "
        + "top_services.service_name as \"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\", count(*) as \"status_count\" "
        + "from \"public\".\"service_infra_info\" "
        + "join top_services on \"public\".\"service_infra_info\".\"service_id\" = top_services.service_id "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by top_services.service_id, "
        + "top_services.service_name, "
        + "\"public\".\"service_infra_info\".\"service_status\"";

    assertThat(cdOverviewDashboardService
                   .buildTopServicesByDeploymentsQuery(
                       null, ACCOUNT_ID, ORG_ID, PROJECT_ID, 10L, 100L, pageable, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSQL);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildFetchTopServicesWithMetricsWhenEnvTypeIsNotNullWithPageSize() {
    PageRequest pageable = PageRequest.of(0, 5);
    String expectedSQL = "with \"top_services\" as ("
        + "select \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\", count(*) as \"total_deployments\" "
        + "from \"public\".\"service_infra_info\" "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"env_type\" = ? "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by \"public\".\"service_infra_info\".\"service_id\", "
        + "\"public\".\"service_infra_info\".\"service_name\" "
        + "order by total_deployments desc limit ? offset ?) "
        + "select top_services.service_id as \"service_id\", "
        + "top_services.service_name as \"service_name\", "
        + "\"public\".\"service_infra_info\".\"service_status\", count(*) as \"status_count\" "
        + "from \"public\".\"service_infra_info\" "
        + "join top_services on \"public\".\"service_infra_info\".\"service_id\" = top_services.service_id "
        + "where (true "
        + "and \"public\".\"service_infra_info\".\"env_type\" = ? "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" "
        + "where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null) "
        + "group by top_services.service_id, "
        + "top_services.service_name, "
        + "\"public\".\"service_infra_info\".\"service_status\"";
    assertThat(cdOverviewDashboardService
                   .buildTopServicesByDeploymentsQuery(EnvironmentType.PreProduction, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                       10L, 100L, pageable, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSQL);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildTotalServicesWithDeploymentsWhenEnvTypeIsNull() {
    String expectedSQL = "select count(distinct \"public\".\"service_infra_info\".\"service_id\") "
        + "from \"public\".\"service_infra_info\" where (true "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in ("
        + "select \"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" where (true "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null)";
    assertThat(cdOverviewDashboardService
                   .buildTotalServicesWithDeployments(
                       null, ACCOUNT_ID, ORG_ID, PROJECT_ID, 10L, 100L, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSQL);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testBuildTotalServicesWithDeploymentsWhenEnvTypeIsNotNull() {
    String expectedSQL = "select count(distinct \"public\".\"service_infra_info\".\"service_id\") "
        + "from \"public\".\"service_infra_info\" where (true and \"public\".\"service_infra_info\".\"env_type\" = ? "
        + "and \"public\".\"service_infra_info\".\"pipeline_execution_summary_cd_id\" in (select "
        + "\"public\".\"pipeline_execution_summary_cd\".\"id\" "
        + "from \"public\".\"pipeline_execution_summary_cd\" where (true and "
        + "\"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in (?) "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" is not null "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" >= ? "
        + "and \"public\".\"pipeline_execution_summary_cd\".\"startts\" < ?)) "
        + "and \"public\".\"service_infra_info\".\"service_name\" is not null "
        + "and \"public\".\"service_infra_info\".\"service_id\" is not null)";
    assertThat(cdOverviewDashboardService
                   .buildTotalServicesWithDeployments(EnvironmentType.PreProduction, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                       10L, 100L, Arrays.asList("parentUniqueId1"))
                   .getSQL())
        .isEqualTo(expectedSQL);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq() {
    // Setup
    List<String> ids = IntStream.range(0, 60).mapToObj(i -> "id" + i).collect(Collectors.toList());
    Map<String, Pair<String, AuthorInfo>> expectedMap = new HashMap<>();

    // Mock the recursive call to simulate behavior
    doAnswer(invocation -> {
      Collection<String> subList = invocation.getArgument(0);
      Map<String, Pair<String, AuthorInfo>> map = invocation.getArgument(1);
      subList.forEach(id -> map.put(id, Pair.of("triggerType", AuthorInfo.builder().build())));
      return null;
    })
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyCollection(), anyMap());

    // Execute
    Map<String, Pair<String, AuthorInfo>> result =
        cdOverviewDashboardService.getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(ids);

    // Verify
    verify(cdOverviewDashboardService, times(2))
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyCollection(), anyMap());
    assertEquals(60, result.size()); // Check that all IDs were processed
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping() {
    // Setup
    List<String> ids = IntStream.range(0, 60).mapToObj(i -> "id" + i).collect(Collectors.toList());
    Map<String, Pair<String, AuthorInfo>> expectedMap = new HashMap<>();

    // Mock the recursive call to simulate behavior
    doAnswer(invocation -> {
      Collection<String> subList = invocation.getArgument(0);
      Map<String, Pair<String, AuthorInfo>> map = invocation.getArgument(1);
      subList.forEach(id -> map.put(id, Pair.of("triggerType", AuthorInfo.builder().build())));
      return null;
    })
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyCollection(), anyMap());

    // Execute
    Map<String, Pair<String, AuthorInfo>> result =
        cdOverviewDashboardService.getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(ids);

    // Verify
    verify(cdOverviewDashboardService, times(2))
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyCollection(), anyMap());
    assertEquals(60, result.size()); // Check that all IDs were processed
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated() {
    // Setup
    List<String> ids = IntStream.range(0, 60).mapToObj(i -> "id" + i).collect(Collectors.toList());
    Map<String, Pair<String, AuthorInfo>> expectedMap = new HashMap<>();

    // Mock the recursive call to simulate behavior
    doAnswer(invocation -> {
      Collection<String> subList = invocation.getArgument(0);
      Map<String, Pair<String, AuthorInfo>> map = invocation.getArgument(1);
      subList.forEach(id -> map.put(id, Pair.of("triggerType", AuthorInfo.builder().build())));
      return null;
    })
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(anyCollection(), anyMap());

    // Execute
    Map<String, Pair<String, AuthorInfo>> result =
        cdOverviewDashboardService.getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(ids);

    // Verify
    verify(cdOverviewDashboardService, times(2))
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(anyCollection(), anyMap());
    assertEquals(60, result.size()); // Check that all IDs were processed
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq() {
    // Setup
    List<String> ids = IntStream.range(0, 60).mapToObj(i -> "id" + i).collect(Collectors.toList());
    Map<String, Pair<String, AuthorInfo>> expectedMap = new HashMap<>();

    // Mock the recursive call to simulate behavior
    doAnswer(invocation -> {
      Collection<String> subList = invocation.getArgument(0);
      Map<String, Pair<String, AuthorInfo>> map = invocation.getArgument(1);
      subList.forEach(id -> map.put(id, Pair.of("triggerType", AuthorInfo.builder().build())));
      return null;
    })
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(anyCollection(), anyMap());

    // Execute
    Map<String, Pair<String, AuthorInfo>> result =
        cdOverviewDashboardService.getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(ids);

    // Verify
    verify(cdOverviewDashboardService, times(2))
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(anyCollection(), anyMap());
    assertEquals(60, result.size()); // Check that all IDs were processed
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testProcessPipelineExecutions() {
    // Setup
    List<String> pipelineExecutionIdList = new ArrayList<>();
    for (int i = 0; i < 60; i++) { // Create a list with 25 elements
      pipelineExecutionIdList.add("execId-" + i);
    }

    BiConsumer<Collection<String>, Map<String, Pair<String, AuthorInfo>>> actionMock = mock(BiConsumer.class);

    // Execute
    Map<String, Pair<String, AuthorInfo>> result =
        cdOverviewDashboardService.processPipelineExecutionsToGetExecutionIdToTriggerTypeAndAuthorInfoMapping(
            pipelineExecutionIdList, actionMock);

    // Verify
    // The action should be called three times: twice with 10 IDs and once with 5 IDs
    verify(actionMock, times(2)).accept(any(Collection.class), anyMap());
    verify(actionMock)
        .accept(argThat(new ArgumentMatcher<Collection<String>>() {
          @Override
          public boolean matches(Collection<String> argument) {
            return argument.size() == 50; // Check the size of the last batch
          }
        }),
            anyMap());

    // You can also verify that the returned map is initially empty (if the action does not modify it)
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculation() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp).when(cdOverviewDashboardService).getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyList());

    DashboardWorkloadDeployment dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculation("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeployment expectedWorkloadDeployment = buildDashboardWorkloadDeployment(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationWithFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp).when(cdOverviewDashboardService).getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeployment dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculation("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeployment expectedWorkloadDeployment = buildDashboardWorkloadDeployment(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationViaJooq() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyList());

    DashboardWorkloadDeployment dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationViaJooq("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeployment expectedWorkloadDeployment = buildDashboardWorkloadDeployment(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationViaJooqWithFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeployment dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationViaJooq("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeployment expectedWorkloadDeployment = buildDashboardWorkloadDeployment(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp).when(cdOverviewDashboardService).getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyList());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2WithFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp).when(cdOverviewDashboardService).getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2("accountIdentifier", workloadsId, status,
            timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2ViaJooq() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyList());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2ViaJooq("accountIdentifier", workloadsId,
            status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2WithViaJooqFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingViaJooq(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2ViaJooq("accountIdentifier", workloadsId,
            status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  //
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2Paginated() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(anyList());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2Paginated("accountIdentifier", workloadsId,
            status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2PaginatedWithFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginated(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2Paginated("accountIdentifier", workloadsId,
            status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2PaginatedViaJooq() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(anyList());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2PaginatedViaJooq("accountIdentifier",
            workloadsId, status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetWorkloadDeploymentInfoCalculationV2PaginatedWithViaJooqFFOn() {
    long startInterval = 1619568000000L;
    long endInterval = 1620000000000L;
    long previousStartInterval = 1619136000000L;

    List<String> workloadsId = Arrays.asList("ServiceId1", "ServiceId1", "ServiceId2", "ServiceId3", "ServiceId3",
        "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2", "ServiceId1", "ServiceId1", "ServiceId2",
        "ServiceId3", "ServiceId3", "ServiceId3", "ServiceId1", "ServiceId1", "ServiceId3", "ServiceId2");
    List<String> pipelineExecutionIds =
        Arrays.asList("exec1", "exec2", "exec3", "exec4", "exec5", "exec6", "exec7", "exec8", "exec9", "exec10",
            "exec11", "exec12", "exec13", "exec14", "exec15", "exec16", "exec17", "exec18", "exec19", "exec20");
    List<String> deploymentTypeList = Arrays.asList(
        "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1");

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.EXPIRED.name(),
        ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.FAILED.name(), ExecutionStatus.FAILED.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.RESOURCEWAITING.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.EXPIRED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.ABORTED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name());
    List<Pair<Long, Long>> timeInterval = new ArrayList<Pair<Long, Long>>() {
      {
        add(Pair.of(1619626802000L, 1619626802000L));
        add(Pair.of(1619885951000L, 1619885951000L));
        add(Pair.of(1619885925000L, 1619885925000L));
        add(Pair.of(1619799469000L, 1619799469000L));
        add(Pair.of(1619885815000L, 1619885815000L));
        add(Pair.of(1619972127000L, 1619972127000L));

        add(Pair.of(1619799299000L, 1619799299000L));
        add(Pair.of(1619885632000L, 1619885632000L));
        add(Pair.of(1619799229000L, 1619799229000L));
        add(Pair.of(1619626420000L, 1619626420000L));

        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619540351000L, 1619540351000L));
        add(Pair.of(1619281125000L, 1619281125000L));
        add(Pair.of(1619367469000L, 1619367469000L));

        add(Pair.of(1619194615000L, 1619194615000L));
        add(Pair.of(1619453727000L, 1619453727000L));
        add(Pair.of(1619453699000L, 1619453699000L));
        add(Pair.of(1619280832000L, 1619280832000L));
        add(Pair.of(1619280829000L, 1619280829000L));
        add(Pair.of(1619453620000L, 1619453620000L));
      }
    };

    HashMap<String, String> hashMap = new HashMap<>();
    hashMap.put("ServiceId1", "Service1");
    hashMap.put("ServiceId2", "Service2");
    hashMap.put("ServiceId3", "Service3");

    Map<String, Pair<String, AuthorInfo>> temp = new HashMap<>();
    for (int i = 1; i <= pipelineExecutionIds.size(); i++) {
      temp.put(pipelineExecutionIds.get(i - 1),
          Pair.of("triggerType" + i, AuthorInfo.builder().name("authorName" + i).build()));
    }
    doReturn(temp)
        .when(cdOverviewDashboardService)
        .getPipelineExecutionIdToTriggerTypeAndAuthorInfoMappingPaginatedViaJooq(anyList());

    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeployment =
        cdOverviewDashboardService.getWorkloadDeploymentInfoCalculationV2PaginatedViaJooq("accountIdentifier",
            workloadsId, status, timeInterval,
            Arrays.asList(
                "kuber1", "kuber2", "kuber1", "kuber3", "kuber3", "kuber1", "kuber4", "kuber2", "kuber2", "kuber1"),
            hashMap, startInterval, endInterval, pipelineExecutionIds);

    DashboardWorkloadDeploymentV2 expectedWorkloadDeployment = buildDashboardWorkloadDeploymentV2(deploymentTypeList);

    assertThat(expectedWorkloadDeployment).isEqualTo(dashboardWorkloadDeployment);
  }

  private DashboardWorkloadDeploymentV2 buildDashboardWorkloadDeploymentV2(List<String> deploymentTypeList) {
    List<WorkloadDeploymentInfoV2> workloadDeploymentInfos = new ArrayList<>();

    List<WorkloadDateCountInfo> service1WorkloadDateCount = new ArrayList<>();
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(2).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());

    List<WorkloadDateCountInfo> service2WorkloadDateCount = new ArrayList<>();
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());

    List<WorkloadDateCountInfo> service3WorkloadDateCount = new ArrayList<>();
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(2).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfoV2.builder()
                                    .serviceName("Service3")
                                    .serviceId("ServiceId3")
                                    .totalDeploymentChangeRate(new ChangeRate(0.0))
                                    .failureRate(50.0)
                                    .failureRateChangeRate(new ChangeRate(null))
                                    .frequency(0.8)
                                    .frequencyChangeRate(new ChangeRate(0.0))
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619972127000L)
                                                      .endTime(1619972127000L)
                                                      .status(ExecutionStatus.FAILED.name())
                                                      .deploymentType("kuber1")
                                                      .triggerType("triggerType6")
                                                      .authorInfo(AuthorInfo.builder().name("authorName6").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .rateSuccess(new ChangeRate(-100 / (double) 3))
                                    .percentSuccess((2 / (double) 4) * 100)
                                    .totalDeployments(4)
                                    .lastPipelineExecutionId("exec6")
                                    .workload(service3WorkloadDateCount)
                                    .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfoV2.builder()
                                    .serviceName("Service2")
                                    .serviceId("ServiceId2")
                                    .totalDeploymentChangeRate(new ChangeRate(0.0))
                                    .failureRate(0.0)
                                    .failureRateChangeRate(new ChangeRate(-100.00))
                                    .frequency(0.4)
                                    .frequencyChangeRate(new ChangeRate(0.0))
                                    .rateSuccess(new ChangeRate(0.0))
                                    .percentSuccess(0.0)
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619885925000L)
                                                      .endTime(1619885925000L)
                                                      .status(ExecutionStatus.RUNNING.name())
                                                      .deploymentType("kuber1")
                                                      .triggerType("triggerType3")
                                                      .authorInfo(AuthorInfo.builder().name("authorName3").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .lastPipelineExecutionId("exec3")
                                    .totalDeployments(2)
                                    .workload(service2WorkloadDateCount)
                                    .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfoV2.builder()
                                    .serviceName("Service1")
                                    .serviceId("ServiceId1")
                                    .totalDeploymentChangeRate(new ChangeRate(0.0))
                                    .failureRate(50.0)
                                    .failureRateChangeRate(new ChangeRate(100.0))
                                    .frequency(0.8)
                                    .frequencyChangeRate(new ChangeRate(0.0))
                                    .rateSuccess(new ChangeRate(0.0))
                                    .percentSuccess((2 / (double) 4) * 100)
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619885951000L)
                                                      .endTime(1619885951000L)
                                                      .status(ExecutionStatus.EXPIRED.name())
                                                      .deploymentType("kuber2")
                                                      .triggerType("triggerType2")
                                                      .authorInfo(AuthorInfo.builder().name("authorName2").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .lastPipelineExecutionId("exec2")

                                    .totalDeployments(4)
                                    .workload(service1WorkloadDateCount)
                                    .build());

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfos).build();
  }

  private DashboardWorkloadDeployment buildDashboardWorkloadDeployment(List<String> deploymentTypeList) {
    List<WorkloadDeploymentInfo> workloadDeploymentInfos = new ArrayList<>();

    List<WorkloadDateCountInfo> service1WorkloadDateCount = new ArrayList<>();
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(2).build())
                                      .build());
    service1WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());

    List<WorkloadDateCountInfo> service2WorkloadDateCount = new ArrayList<>();
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service2WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());

    List<WorkloadDateCountInfo> service3WorkloadDateCount = new ArrayList<>();
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619568000000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619654400000L)
                                      .execution(WorkloadCountInfo.builder().count(0).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619740800000L)
                                      .execution(WorkloadCountInfo.builder().count(2).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619827200000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());
    service3WorkloadDateCount.add(WorkloadDateCountInfo.builder()
                                      .date(1619913600000L)
                                      .execution(WorkloadCountInfo.builder().count(1).build())
                                      .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfo.builder()
                                    .serviceName("Service3")
                                    .serviceId("ServiceId3")
                                    .totalDeploymentChangeRate(0.0)
                                    .failureRate(50.0)
                                    .failureRateChangeRate(INVALID_CHANGE_RATE)
                                    .frequency(0.8)
                                    .frequencyChangeRate(0.0)
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619972127000L)
                                                      .endTime(1619972127000L)
                                                      .status(ExecutionStatus.FAILED.name())
                                                      .deploymentType("kuber1")
                                                      .triggerType("triggerType6")
                                                      .authorInfo(AuthorInfo.builder().name("authorName6").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .rateSuccess(-100 / (double) 3)
                                    .percentSuccess((2 / (double) 4) * 100)
                                    .totalDeployments(4)
                                    .lastPipelineExecutionId("exec6")
                                    .workload(service3WorkloadDateCount)
                                    .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfo.builder()
                                    .serviceName("Service2")
                                    .serviceId("ServiceId2")
                                    .totalDeploymentChangeRate(0.0)
                                    .failureRate(0.0)
                                    .failureRateChangeRate(-100.00)
                                    .frequency(0.4)
                                    .frequencyChangeRate(0.0)
                                    .rateSuccess(0.0)
                                    .percentSuccess(0.0)
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619885925000L)
                                                      .endTime(1619885925000L)
                                                      .status(ExecutionStatus.RUNNING.name())
                                                      .deploymentType("kuber1")
                                                      .triggerType("triggerType3")
                                                      .authorInfo(AuthorInfo.builder().name("authorName3").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .lastPipelineExecutionId("exec3")
                                    .totalDeployments(2)
                                    .workload(service2WorkloadDateCount)
                                    .build());

    workloadDeploymentInfos.add(WorkloadDeploymentInfo.builder()
                                    .serviceName("Service1")
                                    .serviceId("ServiceId1")
                                    .totalDeploymentChangeRate(0.0)
                                    .failureRate(50.0)
                                    .failureRateChangeRate(100)
                                    .frequency(0.8)
                                    .frequencyChangeRate(0.00)
                                    .rateSuccess(0.0)
                                    .percentSuccess((2 / (double) 4) * 100)
                                    .lastExecuted(LastWorkloadInfo.builder()
                                                      .startTime(1619885951000L)
                                                      .endTime(1619885951000L)
                                                      .status(ExecutionStatus.EXPIRED.name())
                                                      .deploymentType("kuber2")
                                                      .triggerType("triggerType2")
                                                      .authorInfo(AuthorInfo.builder().name("authorName2").build())
                                                      .build())
                                    .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                                    .lastPipelineExecutionId("exec2")

                                    .totalDeployments(4)
                                    .workload(service1WorkloadDateCount)
                                    .build());

    return DashboardWorkloadDeployment.builder().workloadDeploymentInfoList(workloadDeploymentInfos).build();
  }

  private ActiveServiceInstanceInfoWithEnvType getActiveServiceInstanceInfoWithEnvType(
      String envRef, String displayName) {
    return ActiveServiceInstanceInfoWithEnvType.builder()
        .envType(EnvironmentType.PreProduction)
        .envIdentifier(envRef)
        .displayName(displayName)
        .build();
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void test_canPopulateDeploymentTypeFromServiceEntity() {
    List<ServiceEntity> services = Collections.singletonList(
        ServiceEntity.builder().name(SERVICE_NAME).type(KUBERNETES).gitOpsEnabled(false).build());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeploymentV2 =
        DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(new ArrayList<>()).build();
    doReturn(dashboardWorkloadDeploymentV2)
        .when(cdOverviewDashboardService)
        .getDashboardWorkloadDeploymentV2(
            anyString(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), any());
    doReturn(instanceCountDetailsByEnvTypeAndServiceId)
        .when(instanceDashboardService)
        .getActiveServiceInstanceCountBreakdown(anyString(), anyString(), anyString(), anyList(), anyLong());
    List<ServiceDetailsDTOV2> serviceDetailsInfoDTOV2 =
        cdOverviewDashboardService
            .getServiceDetailsInfoDTOV2(ACCOUNT_ID, ORG_ID, PROJECT_ID, START_INTERVAL, END_INTERVAL, services,
                PREVIOUS_START_INTERVAL, SCOPE_INFO)
            .getServiceDeploymentDetailsList();
    assertThat(serviceDetailsInfoDTOV2.get(0).getDeploymentTypeList().size()).isEqualTo(1);
    assertTrue(serviceDetailsInfoDTOV2.get(0).getDeploymentTypeList().contains(KUBERNETES.getYamlName()));
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void test_doesNotThrowErrorForNullDeploymentTypeFromServiceEntity() {
    List<ServiceEntity> services =
        Collections.singletonList(ServiceEntity.builder().name(SERVICE_NAME).gitOpsEnabled(false).build());

    DashboardWorkloadDeploymentV2 dashboardWorkloadDeploymentV2 =
        DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(new ArrayList<>()).build();
    doReturn(dashboardWorkloadDeploymentV2)
        .when(cdOverviewDashboardService)
        .getDashboardWorkloadDeploymentV2(
            anyString(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), any());
    doReturn(instanceCountDetailsByEnvTypeAndServiceId)
        .when(instanceDashboardService)
        .getActiveServiceInstanceCountBreakdown(anyString(), anyString(), anyString(), anyList(), anyLong());
    List<ServiceDetailsDTOV2> serviceDetailsInfoDTOV2 =
        cdOverviewDashboardService
            .getServiceDetailsInfoDTOV2(ACCOUNT_ID, ORG_ID, PROJECT_ID, START_INTERVAL, END_INTERVAL, services,
                PREVIOUS_START_INTERVAL, SCOPE_INFO)
            .getServiceDeploymentDetailsList();
    assertThat(serviceDetailsInfoDTOV2.get(0).getDeploymentTypeList()).isEqualTo(null);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteInBatches_EmptyList() {
    List<String> emptyList = Collections.emptyList();

    Map<String, String> result = cdOverviewDashboardService.executeInBatches(emptyList, 10, subList -> new HashMap<>());

    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteInBatches_SingleBatch() {
    List<String> inputList = Arrays.asList("id1", "id2");

    Function<List<String>, Map<String, String>> mockFunction = mock(Function.class);
    Map<String, String> expectedMap = new HashMap<>();
    expectedMap.put("id1", "value1");
    expectedMap.put("id2", "value2");

    when(mockFunction.apply(anyList())).thenAnswer(invocation -> {
      List<String> list = invocation.getArgument(0);
      Map<String, String> map = new HashMap<>();
      for (String id : list) {
        map.put(id, "value" + id.charAt(id.length() - 1));
      }
      return map;
    });

    // When
    Map<String, String> result = cdOverviewDashboardService.executeInBatches(inputList, 10, mockFunction);

    ArgumentCaptor<List<String>> listIdCaptor = forClass(List.class);

    // Then
    assertEquals(expectedMap, result);
    verify(mockFunction, times(1)).apply(listIdCaptor.capture());

    assertTrue(listIdCaptor.getValue().containsAll(inputList));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteInBatches_MultipleBatches() {
    // Given
    List<String> inputList = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      inputList.add("id" + i);
    }

    Function<List<String>, Map<String, String>> mockFunction = mock(Function.class);
    Map<String, String> batch1Result = new HashMap<>();
    for (int i = 1; i <= 10; i++) {
      batch1Result.put("id" + i, "value" + i);
    }

    Map<String, String> batch2Result = new HashMap<>();
    for (int i = 11; i <= 15; i++) {
      batch2Result.put("id" + i, "value" + i);
    }

    when(mockFunction.apply(anyList())).thenAnswer(invocation -> {
      List<String> list = invocation.getArgument(0);
      if (list.size() == 10) {
        return batch1Result;
      } else if (list.size() == 5) {
        return batch2Result;
      }
      return Collections.emptyMap();
    });

    // When
    Map<String, String> result = cdOverviewDashboardService.executeInBatches(inputList, 10, mockFunction);

    // Then
    Map<String, String> expectedMap = new HashMap<>(batch1Result);
    expectedMap.putAll(batch2Result);

    assertEquals(expectedMap, result);
    verify(mockFunction, times(2)).apply(anyList());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsInBatches_EmptyList() {
    List<String> pipelineExecutionIdList = Collections.emptyList();

    List<String> statusList = Lists.newArrayList("SUCCESS", "FAILED");

    // When
    Map<String, ServicePipelineInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsInBatches(pipelineExecutionIdList, statusList);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsInBatches_SingleBatch() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = Arrays.asList("id1", "id2");
    List<String> statusList = Collections.singletonList("Success");
    Map<String, ServicePipelineInfo> expectedResult = new HashMap<>();
    expectedResult.put("id1", ServicePipelineInfo.builder().pipelineExecutionId("id1").build());
    expectedResult.put("id2", ServicePipelineInfo.builder().pipelineExecutionId("id2").build());

    // Mock the behavior of the getPipelineExecutionDetails method
    when(cdOverviewDashboardService.getPipelineExecutionDetails(anyList(), anyList())).thenReturn(expectedResult);

    // When
    Map<String, ServicePipelineInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsInBatches(pipelineExecutionIds, statusList);

    // Then
    assertEquals(expectedResult, result);

    // Use ArgumentCaptor to capture the arguments passed to the mocked method
    ArgumentCaptor<List<String>> pipelineExecutionIdCaptor = forClass(List.class);
    ArgumentCaptor<List<String>> statusListCaptor = forClass(List.class);

    // Verify the method call with captor
    verify(cdOverviewDashboardService, times(1))
        .getPipelineExecutionDetails(pipelineExecutionIdCaptor.capture(), statusListCaptor.capture());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(pipelineExecutionIdCaptor.getValue().containsAll(pipelineExecutionIds));
    assertTrue(statusListCaptor.getValue().containsAll(statusList));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsInBatches_MultipleBatches() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      pipelineExecutionIds.add("id" + i);
    }

    List<String> statusList = Arrays.asList("Success", "Failed");

    Map<String, ServicePipelineInfo> batchResult1 = new HashMap<>();
    for (int i = 0; i < 50; i++) {
      batchResult1.put("id" + i, ServicePipelineInfo.builder().pipelineExecutionId("id" + i).build());
    }

    Map<String, ServicePipelineInfo> batchResult2 = new HashMap<>();
    for (int i = 50; i < 60; i++) {
      batchResult2.put("id" + i, ServicePipelineInfo.builder().pipelineExecutionId("id" + i).build());
    }

    when(cdOverviewDashboardService.getPipelineExecutionDetails(anyList(), anyList())).thenAnswer(invocation -> {
      List<String> executionIdList = invocation.getArgument(0);
      if (executionIdList.size() == 50) {
        return batchResult1;
      } else if (executionIdList.size() == 10) {
        return batchResult2;
      }
      return Collections.emptyMap();
    });

    // When
    Map<String, ServicePipelineInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsInBatches(pipelineExecutionIds, statusList);

    // Then
    Map<String, ServicePipelineInfo> expectedResult = new HashMap<>(batchResult1);
    expectedResult.putAll(batchResult2);

    assertEquals(expectedResult, result);

    // Verify the method is called twice for two batches
    verify(cdOverviewDashboardService, times(2)).getPipelineExecutionDetails(anyList(), anyList());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(result.keySet().containsAll(pipelineExecutionIds));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsWithRevertInfoInBatches_EmptyList() {
    List<String> pipelineExecutionIdList = Collections.emptyList();

    List<String> statusList = Lists.newArrayList("SUCCESS", "FAILED");

    // When
    Map<String, ServicePipelineWithRevertInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsWithRevertInfoInBatches(
            pipelineExecutionIdList, statusList);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsWithRevertInfoInBatchess_SingleBatch() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = Arrays.asList("id1", "id2");
    List<String> statusList = Collections.singletonList("Success");
    Map<String, ServicePipelineWithRevertInfo> expectedResult = new HashMap<>();
    expectedResult.put("id1", ServicePipelineWithRevertInfo.builder().pipelineExecutionId("id1").build());
    expectedResult.put("id2", ServicePipelineWithRevertInfo.builder().pipelineExecutionId("id2").build());

    // Mock the behavior of the getPipelineExecutionDetails method
    when(cdOverviewDashboardService.getPipelineExecutionDetailsWithRevertInfoInBatches(anyList(), anyList()))
        .thenReturn(expectedResult);

    // When
    Map<String, ServicePipelineWithRevertInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsWithRevertInfoInBatches(pipelineExecutionIds, statusList);

    // Then
    assertEquals(expectedResult, result);

    // Use ArgumentCaptor to capture the arguments passed to the mocked method
    ArgumentCaptor<List<String>> pipelineExecutionIdCaptor = forClass(List.class);
    ArgumentCaptor<List<String>> statusListCaptor = forClass(List.class);

    // Verify the method call with captor
    verify(cdOverviewDashboardService, times(1))
        .getPipelineExecutionDetailsWithRevertInfoInBatches(
            pipelineExecutionIdCaptor.capture(), statusListCaptor.capture());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(pipelineExecutionIdCaptor.getValue().containsAll(pipelineExecutionIds));
    assertTrue(statusListCaptor.getValue().containsAll(statusList));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionDetailsWithRevertInfoInBatches_MultipleBatches() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      pipelineExecutionIds.add("id" + i);
    }

    List<String> statusList = Arrays.asList("Success", "Failed");

    Map<String, ServicePipelineWithRevertInfo> batchResult1 = new HashMap<>();
    for (int i = 0; i < 50; i++) {
      batchResult1.put("id" + i, ServicePipelineWithRevertInfo.builder().pipelineExecutionId("id" + i).build());
    }

    Map<String, ServicePipelineWithRevertInfo> batchResult2 = new HashMap<>();
    for (int i = 50; i < 60; i++) {
      batchResult2.put("id" + i, ServicePipelineWithRevertInfo.builder().pipelineExecutionId("id" + i).build());
    }

    when(cdOverviewDashboardService.getPipelineExecutionDetailsWithRevertInfo(anyList(), anyList()))
        .thenAnswer(invocation -> {
          List<String> executionIdList = invocation.getArgument(0);
          if (executionIdList.size() == 50) {
            return batchResult1;
          } else if (executionIdList.size() == 10) {
            return batchResult2;
          }
          return Collections.emptyMap();
        });

    // When
    Map<String, ServicePipelineWithRevertInfo> result =
        cdOverviewDashboardService.getPipelineExecutionDetailsWithRevertInfoInBatches(pipelineExecutionIds, statusList);

    // Then
    Map<String, ServicePipelineWithRevertInfo> expectedResult = new HashMap<>(batchResult1);
    expectedResult.putAll(batchResult2);

    assertEquals(expectedResult, result);

    // Verify the method is called twice for two batches
    verify(cdOverviewDashboardService, times(2)).getPipelineExecutionDetailsWithRevertInfo(anyList(), anyList());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(result.keySet().containsAll(pipelineExecutionIds));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionStatusMapInBatches_EmptyList() {
    List<String> pipelineExecutionIdList = Collections.emptyList();

    // When
    Map<String, String> result =
        cdOverviewDashboardService.getPipelineExecutionStatusMapInBatches(pipelineExecutionIdList, "query");

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionStatusMapInBatches_SingleBatch() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = Arrays.asList("id1", "id2");
    Map<String, String> expectedResult = new HashMap<>();
    expectedResult.put("id1", "status");
    expectedResult.put("id2", "status2");

    // Mock the behavior of the getPipelineExecutionDetails method
    when(cdOverviewDashboardService.getPipelineExecutionStatusMapInBatches(anyList(), anyString()))
        .thenReturn(expectedResult);

    // When
    Map<String, String> result =
        cdOverviewDashboardService.getPipelineExecutionStatusMapInBatches(pipelineExecutionIds, "query");

    // Then
    assertEquals(expectedResult, result);

    // Use ArgumentCaptor to capture the arguments passed to the mocked method
    ArgumentCaptor<List<String>> pipelineExecutionIdCaptor = forClass(List.class);
    ArgumentCaptor<String> statusListCaptor = forClass(String.class);

    // Verify the method call with captor
    verify(cdOverviewDashboardService, times(1))
        .getPipelineExecutionStatusMapInBatches(pipelineExecutionIdCaptor.capture(), statusListCaptor.capture());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(pipelineExecutionIdCaptor.getValue().containsAll(pipelineExecutionIds));
    assertTrue(statusListCaptor.getValue().equals("query"));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionStatusMapInBatches_MultipleBatches() throws SQLException {
    // Given
    List<String> pipelineExecutionIds = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      pipelineExecutionIds.add("id" + i);
    }

    Map<String, String> batchResult1 = new HashMap<>();
    for (int i = 0; i < 50; i++) {
      batchResult1.put("id" + i, "status" + i);
    }

    Map<String, String> batchResult2 = new HashMap<>();
    for (int i = 50; i < 60; i++) {
      batchResult2.put("id" + i, "status" + i);
    }

    when(cdOverviewDashboardService.getPipelineExecutionStatusMap(anyList(), anyString())).thenAnswer(invocation -> {
      List<String> executionIdList = invocation.getArgument(0);
      if (executionIdList.size() == 50) {
        return batchResult1;
      } else if (executionIdList.size() == 10) {
        return batchResult2;
      }
      return Collections.emptyMap();
    });

    // When
    Map<String, String> result =
        cdOverviewDashboardService.getPipelineExecutionStatusMapInBatches(pipelineExecutionIds, "query");

    // Then
    Map<String, String> expectedResult = new HashMap<>(batchResult1);
    expectedResult.putAll(batchResult2);

    assertEquals(expectedResult, result);

    // Verify the method is called twice for two batches
    verify(cdOverviewDashboardService, times(2)).getPipelineExecutionStatusMap(anyList(), anyString());

    // Verify that the captured pipelineExecutionIdList contains the expected elements in any order
    assertTrue(result.keySet().containsAll(pipelineExecutionIds));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetServicesList() throws Exception {
    ServiceEntity entity = ServiceEntity.builder()
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projectId")
                               .identifier("id")
                               .type(KUBERNETES)
                               .version(1L)
                               .description("")
                               .build();
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);
    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();
    PageResponse<ServiceDashboardResponseDTO> serviceEntities = cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo);

    assertThat(serviceEntities).isNotNull();
    assertThat(serviceEntities.getContent().get(0).getDeploymentTypeList()).isNotNull();
  }

  @Test
  @Owner(developers = SATENDRA)
  @Category(UnitTests.class)
  public void testGetServicesListSkipTimescaleDeploymentType() throws Exception {
    // Service with type populated
    ServiceEntity serviceWithType = ServiceEntity.builder()
                                        .accountId("accountId")
                                        .orgIdentifier("orgId")
                                        .projectIdentifier("projectId")
                                        .identifier("service1")
                                        .type(KUBERNETES)
                                        .gitOpsEnabled(false)
                                        .version(1L)
                                        .description("")
                                        .build();

    // Service with null type (simulates Git sync bug where type is not extracted from YAML)
    ServiceEntity serviceWithNullType = ServiceEntity.builder()
                                            .accountId("accountId")
                                            .orgIdentifier("orgId")
                                            .projectIdentifier("projectId")
                                            .identifier("service2")
                                            .type(null)
                                            .version(0L)
                                            .description("")
                                            .build();

    // Service with GitOps enabled
    ServiceEntity gitOpsService = ServiceEntity.builder()
                                      .accountId("accountId")
                                      .orgIdentifier("orgId")
                                      .projectIdentifier("projectId")
                                      .identifier("service3")
                                      .type(KUBERNETES)
                                      .gitOpsEnabled(true)
                                      .version(1L)
                                      .description("")
                                      .build();

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList =
        new PageImpl<>(Arrays.asList(serviceWithType, serviceWithNullType, gitOpsService), pageable, 3);

    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();
    PageResponse<ServiceDashboardResponseDTO> response = cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(3);

    // Service 1: Has type in MongoDB -> should show deployment type "Kubernetes"
    ServiceDashboardResponseDTO service1Response = response.getContent().get(0);
    assertThat(service1Response.getDeploymentTypeList()).isNotNull();
    assertThat(service1Response.getDeploymentTypeList()).contains("Kubernetes");

    // Service 2: Null type in MongoDB -> should return null deploymentTypeList
    ServiceDashboardResponseDTO service2Response = response.getContent().get(1);
    assertThat(service2Response.getDeploymentTypeList()).isNull();

    // Service 3: GitOps enabled -> should show "KubernetesGitOps"
    ServiceDashboardResponseDTO service3Response = response.getContent().get(2);
    assertThat(service3Response.getDeploymentTypeList()).isNotNull();
    assertThat(service3Response.getDeploymentTypeList()).contains("KubernetesGitOps");

    // IMPORTANT: Verify TimescaleDB was NOT called when FF is enabled (performance optimization)
    verify(preparedStatement, times(0)).executeQuery();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetServicesList_nullFilter_excludesAiServices() throws Exception {
    stubServiceListWithEmptyPage();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();

    cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo, null);

    Criteria criteria = captureListCriteria();
    assertThat(typeExclusion(criteria))
        .containsExactlyInAnyOrder(AI_AGENT.name(), GOOGLE_AGENT_RUNTIME.name(), AWS_AGENT_CORE.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetServicesList_filterWithoutServiceTypes_excludesAiServices() throws Exception {
    stubServiceListWithEmptyPage();
    ServiceFilterPropertiesDTO filter = ServiceFilterPropertiesDTO.builder().build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();

    cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo, filter);

    Criteria criteria = captureListCriteria();
    assertThat(typeExclusion(criteria))
        .containsExactlyInAnyOrder(AI_AGENT.name(), GOOGLE_AGENT_RUNTIME.name(), AWS_AGENT_CORE.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetServicesList_filterWithAiAgentType_doesNotExcludeAiServices() throws Exception {
    stubServiceListWithEmptyPage();
    ServiceFilterPropertiesDTO filter =
        ServiceFilterPropertiesDTO.builder().serviceTypes(List.of(AI_AGENT.getYamlName())).build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();

    cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo, filter);

    Criteria criteria = captureListCriteria();
    assertThat(typeExclusion(criteria)).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetServicesList_filterWithAiServiceCategory_doesNotExcludeAiServices() throws Exception {
    stubServiceListWithEmptyPage();
    ServiceFilterPropertiesDTO filter =
        ServiceFilterPropertiesDTO.builder().category(ServiceDefinitionCategory.AI_SERVICE).build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("projectUniqueId").build();

    cdOverviewDashboardService.getServicesList(
        "accountId", "orgId", "projectId", null, null, 10, 0, null, scopeInfo, filter);

    Criteria criteria = captureListCriteria();
    assertThat(typeExclusion(criteria)).isNull();
  }

  private void stubServiceListWithEmptyPage() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    when(serviceEntityService.list(any(), any())).thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));
  }

  private Criteria captureListCriteria() {
    ArgumentCaptor<Criteria> captor = forClass(Criteria.class);
    verify(serviceEntityService).list(captor.capture(), any());
    return captor.getValue();
  }

  /**
   * Returns the values excluded on the {@code type} field via {@code $not/$in}, or null if no exclusion is present.
   */
  @SuppressWarnings("unchecked")
  private List<String> typeExclusion(Criteria criteria) {
    Document doc = criteria.getCriteriaObject();
    Object typeCondition = doc.get("type");
    if (typeCondition instanceof Document typeDoc) {
      Object notClause = typeDoc.get("$not");
      if (notClause instanceof Document notDoc && notDoc.containsKey("$in")) {
        return (List<String>) notDoc.get("$in");
      }
    }
    return null;
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testMapLatestServiceDeployments() {
    String accountId = "account1";
    String orgId = "org1";
    String projectId = "proj1";
    String serviceId = "svc1";

    ArtifactDeploymentDetailModel detailModel = ArtifactDeploymentDetailModel.builder()
                                                    .lastPipelineExecutionId("exec123")
                                                    .orgIdentifier(orgId)
                                                    .projectIdentifier(projectId)
                                                    .envIdentifier("env1")
                                                    .envName("QA")
                                                    .envType("PreProduction")
                                                    .infraIdentifier("infra1")
                                                    .infraName("Infra QA")
                                                    .displayName("artifact:v1")
                                                    .chartVersion("1.2.3")
                                                    .build();

    PipelineExecutionInfoDTO mockPipelineInfo =
        PipelineExecutionInfoDTO.builder().id("exec123").status("SUCCESS").build();

    doReturn(mockPipelineInfo)
        .when(cdOverviewDashboardService)
        .getLastPipelineExecutionInfo(eq("exec123"), eq(accountId), eq(orgId), eq(projectId));

    LatestServiceDeploymentResponseDTO result = cdOverviewDashboardService.mapLatestServiceDeployments(
        accountId, orgId, projectId, serviceId, List.of(detailModel));

    assertThat(result).isNotNull();
    assertEquals(serviceId, result.getService().getId());
    assertEquals(accountId, result.getService().getAccountId());

    List<EnvironmentInfoDTO> environments = result.getEnvironments();
    assertEquals(1, environments.size());

    EnvironmentInfoDTO env = environments.get(0);
    assertEquals("env1", env.getId());
    assertEquals("QA", env.getName());
    assertEquals("PreProduction", env.getType());
    assertEquals("infra1", env.getInfrastructure().getId());
    assertEquals("artifact:v1", env.getArtifactInfo().getVersion());
    assertEquals("1.2.3", env.getChartInfo().getVersion());
    assertEquals(mockPipelineInfo, env.getLatestPipelineExecution());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testQueryBuilderSelectIdCdTable_WithParentIdQuerying() {
    List<String> parentUniqueIds = Arrays.asList(PARENT_UNIQUE_ID);
    String query = cdOverviewDashboardService.queryBuilderSelectIdCdTable(1000L, 2000L, parentUniqueIds);

    assertThat(query).contains("parent_unique_id in");
    assertThat(query).contains(PARENT_UNIQUE_ID);
    assertThat(query).doesNotContain("orgidentifier=");
    assertThat(query).doesNotContain("projectidentifier=");
    assertThat(query).contains("startts is not null");
    assertThat(query).contains("startts>=1000");
    assertThat(query).contains("startts<2000");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList_GitOpsMergeEnabled() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();

    // Service is Kubernetes type
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).type(KUBERNETES).build()));

    // Enable CDS_GITOPS_MERGE_K8S_SERVICES FF
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES))).thenReturn(true);
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();

    // isGitOpsMergeEnabled=true should be passed as last param
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(page.getContent()).thenReturn(Collections.emptyList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

      // Verify instanceDashboardService was called with isGitOpsMergeEnabled=true (last param)
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, true);
    }
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList_GitOpsMergeDisabled_NonK8sService() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();

    // Service is NOT Kubernetes type (e.g., no type set) — isK8sOrHelm returns false
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    // Enable CDS_GITOPS_MERGE_K8S_SERVICES FF, but non-K8s service means isGitOpsMergeEnabled=false
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES))).thenReturn(true);
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();

    // isGitOpsMergeEnabled=false since service is not K8s/Helm
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(page.getContent()).thenReturn(Collections.emptyList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

      // Verify isGitOpsMergeEnabled=false even though FF is on (non-K8s service)
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList_GitOpsMergeDisabled_FFOff_K8sService() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();

    // Service IS Kubernetes type, but FF is OFF — isGitOpsMergeEnabled should be false
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).type(KUBERNETES).build()));

    // FF is OFF
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES))).thenReturn(false);
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();

    // isGitOpsMergeEnabled=false because FF is off, despite K8s service
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(page.getContent()).thenReturn(Collections.emptyList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

      // Verify isGitOpsMergeEnabled=false because FF is off (even though service is K8s)
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList_GitOpsMergeDisabled_HelmService() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();

    // Service is Helm type — merge should NOT be enabled (only K8s is eligible)
    when(serviceEntityServiceImpl.getMetadata(any(ScopeInfo.class), eq(SERVICE_ID), eq(false)))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).type(NATIVE_HELM).build()));

    // FF is ON, but Helm service means isGitOpsMergeEnabled=false
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES))).thenReturn(true);
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY)))
        .thenReturn(false);
    mockScopeInfoServiceForEnvParentUniqueIds();

    // isGitOpsMergeEnabled=false because Helm is not eligible for merge
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefsV2(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any())).thenReturn(page);
    when(page.getContent()).thenReturn(Collections.emptyList());

    IdentifierRef serviceIdRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .build();
    try (MockedStatic<DashboardServiceHelper> mockedStatic = mockStatic(DashboardServiceHelper.class)) {
      mockedStatic.when(() -> DashboardServiceHelper.getIdentifierRef(SERVICE_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .thenReturn(serviceIdRef);
      mockedStatic
          .when(()
                    -> DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelperV2(
                        eq(ACCOUNT_ID), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean()))
          .thenReturn(instanceGroupedByEnvironmentList);

      cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

      // Verify isGitOpsMergeEnabled=false — Helm services are NOT eligible for GitOps merge
      verify(instanceDashboardService)
          .getActiveServiceInstanceInfoWithEnvType(
              ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false, null, false, false);
    }
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testQueryBuilderServiceDeployments_WithParentIdQuerying() {
    List<String> parentUniqueIds = Arrays.asList(PARENT_UNIQUE_ID);
    String query = cdOverviewDashboardService.queryBuilderServiceDeployments(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, 1000L, 2000L, 1L, SERVICE_ID, parentUniqueIds);

    assertThat(query).contains("pesi.parent_unique_id in");
    assertThat(query).contains(PARENT_UNIQUE_ID);
    assertThat(query).contains("pesi.accountid='" + ACCOUNT_ID + "'");
    assertThat(query).doesNotContain("orgidentifier=");
    assertThat(query).doesNotContain("projectidentifier=");
    assertThat(query).contains("sii.service_id='" + SERVICE_ID + "'");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testQueryBuilderServiceDeployments_EscapesSqlInjection() {
    String maliciousInput = "test'; DROP TABLE users; --";
    String escaped = maliciousInput.replace("'", "''");
    List<String> parentUniqueIds = Arrays.asList("parentId");
    String query = cdOverviewDashboardService.queryBuilderServiceDeployments(
        maliciousInput, maliciousInput, maliciousInput, 1000L, 2000L, 1L, maliciousInput, parentUniqueIds);

    assertThat(query).contains("pesi.parent_unique_id in");
    assertThat(query).contains("sii.service_id='" + escaped + "'");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testQueryBuilderServiceDeployments_ParentIdQuerying_NullAccountId() {
    List<String> parentUniqueIds = Arrays.asList(PARENT_UNIQUE_ID);
    String query = cdOverviewDashboardService.queryBuilderServiceDeployments(
        null, ORG_ID, PROJECT_ID, 1000L, 2000L, 1L, SERVICE_ID, parentUniqueIds);

    assertThat(query).contains("pesi.parent_unique_id in");
  }

  @Test
  @Owner(developers = KESHAV_GOEL)
  @Category(UnitTests.class)
  public void testQueryBuilderSelectWorkloadViaJooqWithParentUniqueIds() {
    List<String> parentUniqueIds = Arrays.asList("parent1", "parent2");

    Query query = cdOverviewDashboardService.queryBuilderSelectWorkloadViaJooq(10L, 13L, null, parentUniqueIds);
    String sql = query.getSQL();

    assertThat(sql).contains("in (select");
    assertThat(sql).contains("\"public\".\"pipeline_execution_summary_cd\"");
    assertThat(sql).contains("\"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in");
  }

  @Test
  @Owner(developers = KESHAV_GOEL)
  @Category(UnitTests.class)
  public void testQueryBuilderSelectWorkloadViaJooqWithParentUniqueIdsAndEnvType() {
    List<String> parentUniqueIds = Arrays.asList("parent1");

    Query query = cdOverviewDashboardService.queryBuilderSelectWorkloadViaJooq(
        10L, 13L, EnvironmentType.Production, parentUniqueIds);
    String sql = query.getSQL();

    assertThat(sql).contains("in (select");
    assertThat(sql).contains("\"public\".\"service_infra_info\".\"env_type\"");
    assertThat(sql).contains("\"public\".\"pipeline_execution_summary_cd\".\"parent_unique_id\" in");
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetGitOpsStageMetadataForRollback() throws Exception {
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true).thenReturn(false);
    when(resultSet.getString("plan_execution_id")).thenReturn("plan1");
    when(resultSet.getString("service_id")).thenReturn("svc1");
    when(resultSet.getString("env_id")).thenReturn("env1");
    when(resultSet.getString("stage_execution_id")).thenReturn("stage1");
    when(resultSet.getString("status")).thenReturn("SUCCEEDED");

    Map<String, GitOpsStageMetadata> result =
        cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Collections.singletonList("plan1"));

    assertThat(result).hasSize(1);
    GitOpsStageMetadata metadata = result.get(GitOpsStageMetadata.buildKey("plan1", "svc1", "env1"));
    assertThat(metadata).isNotNull();
    assertThat(metadata.getPlanExecutionId()).isEqualTo("plan1");
    assertThat(metadata.getServiceId()).isEqualTo("svc1");
    assertThat(metadata.getEnvId()).isEqualTo("env1");
    assertThat(metadata.getStageExecutionId()).isEqualTo("stage1");
    assertThat(metadata.getStageStatus()).isEqualTo("SUCCEEDED");
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetGitOpsStageMetadataForRollbackEmptyInput() {
    assertThat(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(Collections.emptyList())).isEmpty();
    assertThat(cdOverviewDashboardService.getGitOpsStageMetadataForRollback(null)).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetGitOpsStageMetadataForRollbackViaJooq() {
    DSLContext renderer = DSL.using(SQLDialect.POSTGRES);
    Field<String> serviceIdField = DSL.field("cd_stage_execution.service_id", String.class);
    Field<String> envIdField = DSL.field("cd_stage_execution.env_id", String.class);
    Field<String> stageExecId = DSL.field("stage_execution.stage_execution_id", String.class);

    Result<Record5<String, String, String, String, String>> mockData = renderer.newResult(
        STAGE_EXECUTION.PLAN_EXECUTION_ID, serviceIdField, envIdField, stageExecId, STAGE_EXECUTION.STATUS);
    Record5<String, String, String, String, String> record = renderer.newRecord(
        STAGE_EXECUTION.PLAN_EXECUTION_ID, serviceIdField, envIdField, stageExecId, STAGE_EXECUTION.STATUS);
    record.set(STAGE_EXECUTION.PLAN_EXECUTION_ID, "plan1");
    record.set(serviceIdField, "svc1");
    record.set(envIdField, "env1");
    record.set(stageExecId, "stage1");
    record.set(STAGE_EXECUTION.STATUS, "SUCCEEDED");
    mockData.add(record);

    MockDataProvider provider = ctx -> new MockResult[] {new MockResult(1, mockData)};
    DSLContext mockDslContext = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
    Reflect.on(cdOverviewDashboardService).set("dslContext", mockDslContext);

    Map<String, GitOpsStageMetadata> result =
        cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(Collections.singletonList("plan1"));

    assertThat(result).hasSize(1);
    GitOpsStageMetadata metadata = result.get(GitOpsStageMetadata.buildKey("plan1", "svc1", "env1"));
    assertThat(metadata).isNotNull();
    assertThat(metadata.getStageExecutionId()).isEqualTo("stage1");
    assertThat(metadata.getStageStatus()).isEqualTo("SUCCEEDED");
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetGitOpsStageMetadataForRollbackViaJooqEmptyInput() {
    assertThat(cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(Collections.emptyList())).isEmpty();
    assertThat(cdOverviewDashboardService.getGitOpsStageMetadataForRollbackViaJooq(null)).isEmpty();
  }
}