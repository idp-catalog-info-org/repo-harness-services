/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.timescaledb.Tables.PIPELINE_EXECUTION_SUMMARY_CD;
import static io.harness.timescaledb.Tables.SERVICE_INFRA_INFO;
import static io.harness.utils.IdentifierRefHelper.IDENTIFIER_REF_DELIMITER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.encryption.Scope;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.ChartVersionInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedOnChartVersionList;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
import io.harness.ng.overview.dto.ServicePipelineWithRevertInfo;
import io.harness.utils.IdentifierRefHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.Query;
import org.jooq.Record11;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@UtilityClass
public class DashboardServiceHelper {
  private static final String tableNameServiceAndInfra = "service_infra_info";
  private static final String PIPELINE_EXECUTION_SUMMARY_CD_ID = "pipeline_execution_summary_cd_id";
  private static final String ARTIFACT_DISPLAY_NAME = "artifact_display_name";
  private static final String ARTIFACT_IMAGE = "artifact_image";
  private static final String TAG = "tag";
  private static final String ACCOUNT_ID = "accountid";
  private static final String ORG_ID = "orgidentifier";
  private static final String PROJECT_ID = "projectidentifier";
  private static final String SERVICE_ID = "service_id";
  private static final String SERVICE_NAME = "service_name";
  private static final String SERVICE_STARTTS = "service_startts";
  private static final String PARENT_UNIQUE_ID = "parent_unique_id";
  private static final String SERVICE_ENDTS = "service_endts";
  private static final String ID = "id";
  private static final String tableNameCD = "pipeline_execution_summary_cd";
  private static final String STATUS = "status";

  public String escapeSql(String input) {
    if (input == null) {
      return null;
    }
    return input.replace("'", "''");
  }

  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentListHelper(String envGrpId,
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage) {
    return getInstanceGroupedByEnvironmentListHelper(
        envGrpId, activeServiceInstanceInfoList, isGitOps, environmentGroupEntitiesPage, null);
  }

  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentListHelper(String envGrpId,
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, ScopeInfo scopeInfo) {
    // nested map - environmentId, environmentType, infrastructureId, displayName, (count, lastDeployedAt)

    boolean useScopeInfo = scopeInfo != null;
    Map<String,
        Map<EnvironmentType,
            Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
        instanceCountMap = new HashMap<>();
    Map<String, String> envIdToNameMap = new HashMap<>();
    // since we are already filtering instances on service type (gitOps or non-gitOps), infraIdToNameMap will contain
    // clusterId to agentId map in case of gitOps
    Map<String, String> infraIdToNameMap = new HashMap<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<String, List<String>> envToEnvGroupNameMap = new HashMap<>();
    Map<String, List<String>> envToEnvGroupIdMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<String> envIds =
              environmentGroupEntity.getEnvIdentifiers()
                  .stream()
                  .map(envId
                      -> convertIdToRef(
                          useScopeInfo ? scopeInfo.getAccountIdentifier() : environmentGroupEntity.getAccountId(),
                          useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentGroupEntity.getOrgIdentifier(),
                          useScopeInfo ? scopeInfo.getProjectIdentifier()
                                       : environmentGroupEntity.getProjectIdentifier(),
                          envId))
                  .collect(Collectors.toList());
          for (String envId : envIds) {
            envToEnvGroupNameMap.computeIfAbsent(envId, k -> new ArrayList<>()).add(environmentGroupEntity.getName());
            envToEnvGroupIdMap.computeIfAbsent(envId, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final EnvironmentType envType = activeServiceInstanceInfo.getEnvType();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();
      final String displayName = activeServiceInstanceInfo.getDisplayName();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envId);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envId == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      final String envName = activeServiceInstanceInfo.getEnvName();
      envIdToNameMap.putIfAbsent(envId, envName);
      instanceCountMap.putIfAbsent(envId, new HashMap<>());

      instanceCountMap.putIfAbsent(envId, new HashMap<>());
      instanceCountMap.get(envId).putIfAbsent(envType, new HashMap<>());

      String infraId = activeServiceInstanceInfo.getClusterIdentifier() != null
          ? activeServiceInstanceInfo.getClusterIdentifier()
          : activeServiceInstanceInfo.getInfraIdentifier();
      String infraName = activeServiceInstanceInfo.getAgentIdentifier() != null
          ? activeServiceInstanceInfo.getAgentIdentifier()
          : activeServiceInstanceInfo.getInfraName();

      infraIdToNameMap.putIfAbsent(infraId, infraName);
      instanceCountMap.get(envId).get(envType).putIfAbsent(infraId, new HashMap<>());
      instanceCountMap.get(envId).get(envType).get(infraId).putIfAbsent(
          activeServiceInstanceInfo.getDisplayName(), new HashMap<>());
      InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion byChartVersion =
          InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion.builder()
              .instanceKey(activeServiceInstanceInfo.getInstanceKey())
              .infrastructureMappingId(activeServiceInstanceInfo.getInfrastructureMappingId())
              .lastPlanExecutionId(activeServiceInstanceInfo.getLastPipelineExecutionId())
              .pipelineIdentifier(activeServiceInstanceInfo.getLastPipelineExecutionName())
              .stageNodeExecutionId(activeServiceInstanceInfo.getStageNodeExecutionId())
              .stageSetupId(activeServiceInstanceInfo.getStageSetupId())
              .rollbackStatus(activeServiceInstanceInfo.getRollbackStatus())
              .lastDeployedAt(activeServiceInstanceInfo.getLastDeployedAt())
              .count(activeServiceInstanceInfo.getCount())
              .chartVersion(activeServiceInstanceInfo.getVersion())
              .build();
      instanceCountMap.get(envId)
          .get(envType)
          .get(infraId)
          .get(activeServiceInstanceInfo.getDisplayName())
          .putIfAbsent(activeServiceInstanceInfo.getVersion(), byChartVersion);
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });

    return InstanceGroupedByEnvironmentList.builder()
        .instanceGroupedByEnvironmentList(groupByEnvironment(instanceCountMap, infraIdToNameMap, envIdToNameMap,
            envToEnvGroupNameMap, isGitOps, artifactToArtifactLinkMap, false))

        .build();
  }

  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentListHelperV2(String accountIdentifier,
      String envGrpId, List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, Map<IdentifierRef, Environment> identifierRefToEnvMap,
      ScopeInfo scopeInfo, boolean isGitOpsMergeEnabled) {
    // nested map - environmentId, environmentType, infrastructureId, displayName, (count, lastDeployedAt)
    boolean useScopeInfo = scopeInfo != null;
    Map<IdentifierRef,
        Map<EnvironmentType,
            Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
        instanceCountMap = new HashMap<>();
    // since we are already filtering instances on service type (gitOps or non-gitOps), infraIdToNameMap will contain
    // clusterId to agentId map in case of gitOps
    Map<String, String> infraIdToNameMap = new HashMap<>();
    Set<String> clusterIdSet = new HashSet<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<IdentifierRef, List<String>> envToEnvGroupNameMap = new HashMap<>();
    Map<IdentifierRef, List<String>> envToEnvGroupIdMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<IdentifierRef> envIdRefList =
              environmentGroupEntity.getEnvIdentifiers()
                  .stream()
                  .map(envId
                      -> buildIdentifierRef(envId,
                          useScopeInfo ? scopeInfo.getAccountIdentifier() : environmentGroupEntity.getAccountId(),
                          useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentGroupEntity.getOrgIdentifier(),
                          useScopeInfo ? scopeInfo.getProjectIdentifier()
                                       : environmentGroupEntity.getProjectIdentifier()))
                  .collect(Collectors.toList());
          for (IdentifierRef envIdRef : envIdRefList) {
            envToEnvGroupNameMap.computeIfAbsent(envIdRef, k -> new ArrayList<>())
                .add(environmentGroupEntity.getName());
            envToEnvGroupIdMap.computeIfAbsent(envIdRef, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final IdentifierRef envIdRef = envId == null
          ? null
          : DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier());
      final EnvironmentType envType = activeServiceInstanceInfo.getEnvType();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();
      final String displayName = activeServiceInstanceInfo.getDisplayName();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envIdRef);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envIdRef == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      instanceCountMap.putIfAbsent(envIdRef, new HashMap<>());

      instanceCountMap.putIfAbsent(envIdRef, new HashMap<>());
      instanceCountMap.get(envIdRef).putIfAbsent(envType, new HashMap<>());

      String infraId = activeServiceInstanceInfo.getClusterIdentifier() != null
          ? activeServiceInstanceInfo.getClusterIdentifier()
          : activeServiceInstanceInfo.getInfraIdentifier();
      String infraName = activeServiceInstanceInfo.getAgentIdentifier() != null
          ? activeServiceInstanceInfo.getAgentIdentifier()
          : activeServiceInstanceInfo.getInfraName();

      if (isGitOpsMergeEnabled && activeServiceInstanceInfo.getClusterIdentifier() != null) {
        clusterIdSet.add(infraId);
      }
      infraIdToNameMap.putIfAbsent(infraId, infraName);
      instanceCountMap.get(envIdRef).get(envType).putIfAbsent(infraId, new HashMap<>());
      instanceCountMap.get(envIdRef).get(envType).get(infraId).putIfAbsent(
          activeServiceInstanceInfo.getDisplayName(), new HashMap<>());
      InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion byChartVersion =
          InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion.builder()
              .instanceKey(activeServiceInstanceInfo.getInstanceKey())
              .infrastructureMappingId(activeServiceInstanceInfo.getInfrastructureMappingId())
              .lastPlanExecutionId(activeServiceInstanceInfo.getLastPipelineExecutionId())
              .pipelineIdentifier(activeServiceInstanceInfo.getLastPipelineExecutionName())
              .stageNodeExecutionId(activeServiceInstanceInfo.getStageNodeExecutionId())
              .stageSetupId(activeServiceInstanceInfo.getStageSetupId())
              .rollbackStatus(activeServiceInstanceInfo.getRollbackStatus())
              .lastDeployedAt(activeServiceInstanceInfo.getLastDeployedAt())
              .count(activeServiceInstanceInfo.getCount())
              .chartVersion(activeServiceInstanceInfo.getVersion())
              .build();
      instanceCountMap.get(envIdRef)
          .get(envType)
          .get(infraId)
          .get(activeServiceInstanceInfo.getDisplayName())
          .putIfAbsent(activeServiceInstanceInfo.getVersion(), byChartVersion);
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });

    return InstanceGroupedByEnvironmentList.builder()
        .instanceGroupedByEnvironmentList(
            groupByEnvironmentV2(instanceCountMap, infraIdToNameMap, identifierRefToEnvMap, envToEnvGroupNameMap,
                isGitOps, artifactToArtifactLinkMap, useScopeInfo, clusterIdSet, isGitOpsMergeEnabled))
        .build();
  }

  public InstanceGroupedOnArtifactList getInstanceGroupedByArtifactListHelper(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, String envGrpId) {
    // nested map - displayName, envId, environmentType, instanceGroupedByInfrastructure
    Map<String,
        Map<String,
            Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
        instanceCountMap = new HashMap<>();
    Map<String, String> envIdToNameMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<String, List<String>> envToEnvGroupIdMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<String> envIds = environmentGroupEntity.getEnvIdentifiers();
          for (String envId : envIds) {
            envToEnvGroupIdMap.computeIfAbsent(envId, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envId);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envId == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      final String envName = activeServiceInstanceInfo.getEnvName();
      envIdToNameMap.putIfAbsent(envId, envName);

      final String displayName = activeServiceInstanceInfo.getDisplayName();
      instanceCountMap.putIfAbsent(displayName, new HashMap<>());
      instanceCountMap.get(displayName).putIfAbsent(activeServiceInstanceInfo.getVersion(), new HashMap<>());
      instanceCountMap.get(displayName).get(activeServiceInstanceInfo.getVersion()).putIfAbsent(envId, new HashMap<>());

      final EnvironmentType environmentType = activeServiceInstanceInfo.getEnvType();
      instanceCountMap.get(displayName)
          .get(activeServiceInstanceInfo.getVersion())
          .get(envId)
          .putIfAbsent(environmentType, new ArrayList<>());
      instanceCountMap.get(displayName)
          .get(activeServiceInstanceInfo.getVersion())
          .get(envId)
          .get(environmentType)
          .add(getInstanceGroupedByInfrastructure(activeServiceInstanceInfo, isGitOps, false));
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    return InstanceGroupedOnArtifactList.builder()
        .instanceGroupedOnArtifactList(groupByArtifact(instanceCountMap, envIdToNameMap))
        .build();
  }

  public InstanceGroupedOnArtifactList getInstanceGroupedByArtifactListHelperV2(String accountIdentifier,
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, String envGrpId,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, ScopeInfo scopeInfo, boolean isGitOpsMergeEnabled) {
    // nested map - displayName, envIdRef, environmentType, instanceGroupedByInfrastructure

    boolean useScopeInfo = scopeInfo != null;
    Map<String,
        Map<String,
            Map<IdentifierRef,
                Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
        instanceCountMap = new HashMap<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<IdentifierRef, List<String>> envToEnvGroupIdMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<String> envIds = environmentGroupEntity.getEnvIdentifiers();
          for (String envId : envIds) {
            IdentifierRef envIdRef = buildIdentifierRef(envId,
                useScopeInfo ? scopeInfo.getAccountIdentifier() : environmentGroupEntity.getAccountIdentifier(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentGroupEntity.getOrgIdentifier(),
                useScopeInfo ? scopeInfo.getProjectIdentifier() : environmentGroupEntity.getProjectIdentifier());
            envToEnvGroupIdMap.computeIfAbsent(envIdRef, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final IdentifierRef envIdRef = envId == null
          ? null
          : DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier());
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envIdRef);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envId == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      final String displayName = activeServiceInstanceInfo.getDisplayName();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();
      instanceCountMap.putIfAbsent(displayName, new HashMap<>());
      instanceCountMap.get(displayName).putIfAbsent(activeServiceInstanceInfo.getVersion(), new HashMap<>());
      instanceCountMap.get(displayName)
          .get(activeServiceInstanceInfo.getVersion())
          .putIfAbsent(envIdRef, new HashMap<>());

      final EnvironmentType environmentType = activeServiceInstanceInfo.getEnvType();
      instanceCountMap.get(displayName)
          .get(activeServiceInstanceInfo.getVersion())
          .get(envIdRef)
          .putIfAbsent(environmentType, new ArrayList<>());
      instanceCountMap.get(displayName)
          .get(activeServiceInstanceInfo.getVersion())
          .get(envIdRef)
          .get(environmentType)
          .add(getInstanceGroupedByInfrastructure(activeServiceInstanceInfo, isGitOps, isGitOpsMergeEnabled));
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    return InstanceGroupedOnArtifactList.builder()
        .instanceGroupedOnArtifactList(
            groupByArtifactV2(instanceCountMap, identifierRefToEnvMap, artifactToArtifactLinkMap))
        .build();
  }

  public InstanceGroupedOnChartVersionList getInstanceGroupedByChartVersionListHelper(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, String envGrpId) {
    // nested map - chartVersion, displayName, envId, environmentType, instanceGroupedByInfrastructure
    Map<String,
        Map<String,
            Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
        instanceCountMap = new HashMap<>();
    Map<String, String> envIdToNameMap = new HashMap<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<String, List<String>> envToEnvGroupIdMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<String> envIds = environmentGroupEntity.getEnvIdentifiers();
          for (String envId : envIds) {
            envToEnvGroupIdMap.computeIfAbsent(envId, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();
      final String displayName = activeServiceInstanceInfo.getDisplayName();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envId);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envId == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      final String envName = activeServiceInstanceInfo.getEnvName();
      envIdToNameMap.putIfAbsent(envId, envName);

      final String chartVersion = activeServiceInstanceInfo.getVersion();
      instanceCountMap.putIfAbsent(chartVersion, new HashMap<>());
      instanceCountMap.get(chartVersion).putIfAbsent(activeServiceInstanceInfo.getDisplayName(), new HashMap<>());
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .putIfAbsent(envId, new HashMap<>());

      final EnvironmentType environmentType = activeServiceInstanceInfo.getEnvType();
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .get(envId)
          .putIfAbsent(environmentType, new ArrayList<>());
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .get(envId)
          .get(environmentType)
          .add(getInstanceGroupedByInfrastructureForChartVersion(activeServiceInstanceInfo, isGitOps, false));
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    return InstanceGroupedOnChartVersionList.builder()
        .instanceGroupByChartVersionList(
            groupByChartVersionList(instanceCountMap, envIdToNameMap, artifactToArtifactLinkMap))
        .build();
  }

  public InstanceGroupedOnChartVersionList getInstanceGroupedByChartVersionListHelperV2(String accountIdentifier,
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, boolean isGitOps,
      boolean isGitOpsMergeEnabled, Page<EnvironmentGroupEntity> environmentGroupEntitiesPage, String envGrpId,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, ScopeInfo scopeInfo) {
    // nested map - chartVersion, displayName, envIdRef, environmentType, instanceGroupedByInfrastructure

    boolean useScopeInfo = scopeInfo != null;
    Map<String,
        Map<String,
            Map<IdentifierRef,
                Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
        instanceCountMap = new HashMap<>();

    List<EnvironmentGroupEntity> environmentGroupEntities;
    Map<IdentifierRef, List<String>> envToEnvGroupIdMap = new HashMap<>();
    Map<String, String> artifactToArtifactLinkMap = new HashMap<>();

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          List<String> envIds = environmentGroupEntity.getEnvIdentifiers();
          for (String envId : envIds) {
            IdentifierRef envIdRef = buildIdentifierRef(envId,
                useScopeInfo ? scopeInfo.getAccountIdentifier() : environmentGroupEntity.getAccountIdentifier(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : environmentGroupEntity.getOrgIdentifier(),
                useScopeInfo ? scopeInfo.getProjectIdentifier() : environmentGroupEntity.getProjectIdentifier());
            envToEnvGroupIdMap.computeIfAbsent(envIdRef, k -> new ArrayList<>())
                .add(environmentGroupEntity.getIdentifier());
          }
        }
      }
    }

    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final IdentifierRef envIdRef = envId == null
          ? null
          : DashboardServiceHelper.getIdentifierRef(activeServiceInstanceInfo.getEnvIdentifier(), accountIdentifier,
                activeServiceInstanceInfo.getOrgIdentifier(), activeServiceInstanceInfo.getProjectIdentifier());
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String artifactLink = activeServiceInstanceInfo.getArtifactLink();
      final String displayName = activeServiceInstanceInfo.getDisplayName();

      List<String> envGrpIdList = envToEnvGroupIdMap.get(envIdRef);

      if (isEmpty(envGrpIdList) && !isEmpty(envGrpId)) {
        return;
      }

      if (envIdRef == null || lastDeployedAt == null || (!isEmpty(envGrpId) && !envGrpIdList.contains(envGrpId))) {
        return;
      }

      final String chartVersion = activeServiceInstanceInfo.getVersion();
      instanceCountMap.putIfAbsent(chartVersion, new HashMap<>());
      instanceCountMap.get(chartVersion).putIfAbsent(activeServiceInstanceInfo.getDisplayName(), new HashMap<>());
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .putIfAbsent(envIdRef, new HashMap<>());

      final EnvironmentType environmentType = activeServiceInstanceInfo.getEnvType();
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .get(envIdRef)
          .putIfAbsent(environmentType, new ArrayList<>());
      instanceCountMap.get(chartVersion)
          .get(activeServiceInstanceInfo.getDisplayName())
          .get(envIdRef)
          .get(environmentType)
          .add(getInstanceGroupedByInfrastructureForChartVersion(
              activeServiceInstanceInfo, isGitOps, isGitOpsMergeEnabled));
      if (StringUtils.isNotBlank(artifactLink)) {
        artifactToArtifactLinkMap.putIfAbsent(displayName, artifactLink);
      }
    });
    return InstanceGroupedOnChartVersionList.builder()
        .instanceGroupByChartVersionList(
            groupByChartVersionListV2(instanceCountMap, identifierRefToEnvMap, artifactToArtifactLinkMap))
        .build();
  }

  private InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure getInstanceGroupedByInfrastructure(
      ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType, boolean isGitOps,
      boolean isGitOpsMergeEnabled) {
    InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure
        .InstanceGroupedOnInfrastructureBuilder instanceGroupedByInfrastructureBuilder =
        InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure.builder();
    if ((isGitOpsMergeEnabled && activeServiceInstanceInfoWithEnvType.getClusterIdentifier() != null)
        || (isGitOps && !isGitOpsMergeEnabled)) {
      instanceGroupedByInfrastructureBuilder.clusterId(activeServiceInstanceInfoWithEnvType.getClusterIdentifier())
          .agentId(activeServiceInstanceInfoWithEnvType.getAgentIdentifier());
    } else {
      instanceGroupedByInfrastructureBuilder.infrastructureId(activeServiceInstanceInfoWithEnvType.getInfraIdentifier())
          .infrastructureName(activeServiceInstanceInfoWithEnvType.getInfraName());
    }

    return instanceGroupedByInfrastructureBuilder.count(activeServiceInstanceInfoWithEnvType.getCount())
        .lastDeployedAt(activeServiceInstanceInfoWithEnvType.getLastDeployedAt())
        .build();
  }

  private InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure
  getInstanceGroupedByInfrastructureForChartVersion(
      ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType, boolean isGitOps,
      boolean isGitOpsMergeEnabled) {
    InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure
        .InstanceGroupedOnInfrastructureBuilder instanceGroupedByInfrastructureBuilder =
        InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure.builder();
    if ((isGitOpsMergeEnabled && activeServiceInstanceInfoWithEnvType.getClusterIdentifier() != null)
        || (isGitOps && !isGitOpsMergeEnabled)) {
      instanceGroupedByInfrastructureBuilder.clusterId(activeServiceInstanceInfoWithEnvType.getClusterIdentifier())
          .agentId(activeServiceInstanceInfoWithEnvType.getAgentIdentifier());
    } else {
      instanceGroupedByInfrastructureBuilder.infrastructureId(activeServiceInstanceInfoWithEnvType.getInfraIdentifier())
          .infrastructureName(activeServiceInstanceInfoWithEnvType.getInfraName());
    }

    return instanceGroupedByInfrastructureBuilder.count(activeServiceInstanceInfoWithEnvType.getCount())
        .lastDeployedAt(activeServiceInstanceInfoWithEnvType.getLastDeployedAt())
        .build();
  }

  private String convertIdToRef(String accountId, String orgId, String projectId, String id) {
    return IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, id).buildScopedIdentifier();
  }

  private List<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion> groupByChartVersionList(
      Map<String,
          Map<String,
              Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion> instanceGroupedByChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Map<String,
                     Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
             entry : instanceCountMap.entrySet()) {
      final String chartVersion = entry.getKey();
      List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> instanceGroupedOnArtifactList =
          groupedOnArtifact(entry.getValue(), envIdToNameMap, artifactToArtifactLinkMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedOnArtifactList)) {
        lastDeployedAt = instanceGroupedOnArtifactList.get(0).getLastDeployedAt();
      }
      instanceGroupedByChartVersionList.add(InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion.builder()
                                                .chartVersion(chartVersion)
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupByArtifactList(instanceGroupedOnArtifactList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedByChartVersionList,
        new Comparator<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion>() {
          public int compare(InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion o1,
              InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByChartVersionList;
  }

  private List<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion> groupByChartVersionListV2(
      Map<String,
          Map<String,
              Map<IdentifierRef,
                  Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
          instanceCountMap,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion> instanceGroupedByChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Map<IdentifierRef,
                     Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
             entry : instanceCountMap.entrySet()) {
      final String chartVersion = entry.getKey();
      List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> instanceGroupedOnArtifactList =
          groupedOnArtifactV2(entry.getValue(), identifierRefToEnvMap, artifactToArtifactLinkMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedOnArtifactList)) {
        lastDeployedAt = instanceGroupedOnArtifactList.get(0).getLastDeployedAt();
      }
      instanceGroupedByChartVersionList.add(InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion.builder()
                                                .chartVersion(chartVersion)
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupByArtifactList(instanceGroupedOnArtifactList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedByChartVersionList,
        new Comparator<InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion>() {
          public int compare(InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion o1,
              InstanceGroupedOnChartVersionList.InstanceGroupByChartVersion o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByChartVersionList;
  }

  private List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> groupedOnArtifact(
      Map<String,
          Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> instanceGroupedOnChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
             entry : instanceCountMap.entrySet()) {
      final String displayName = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
          groupByEnvironment(entry.getValue(), envIdToNameMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentList)) {
        lastDeployedAt = instanceGroupedByEnvironmentList.get(0).getLastDeployedAt();
      }
      instanceGroupedOnChartVersionList.add(InstanceGroupedOnChartVersionList.InstanceGroupByArtifact.builder()
                                                .artifact(displayName)
                                                .artifactLink(artifactToArtifactLinkMap.get(displayName))
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupedOnEnvironmentList(instanceGroupedByEnvironmentList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedOnChartVersionList, new Comparator<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact>() {
          public int compare(InstanceGroupedOnChartVersionList.InstanceGroupByArtifact o1,
              InstanceGroupedOnChartVersionList.InstanceGroupByArtifact o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedOnChartVersionList;
  }

  private List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> groupedOnArtifactV2(
      Map<String,
          Map<IdentifierRef, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
          instanceCountMap,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact> instanceGroupedOnChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<IdentifierRef,
                 Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>> entry :
        instanceCountMap.entrySet()) {
      final String displayName = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
          groupByEnvironmentV2(entry.getValue(), identifierRefToEnvMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentList)) {
        lastDeployedAt = instanceGroupedByEnvironmentList.get(0).getLastDeployedAt();
      }
      instanceGroupedOnChartVersionList.add(InstanceGroupedOnChartVersionList.InstanceGroupByArtifact.builder()
                                                .artifact(displayName)
                                                .artifactLink(artifactToArtifactLinkMap.get(displayName))
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupedOnEnvironmentList(instanceGroupedByEnvironmentList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedOnChartVersionList, new Comparator<InstanceGroupedOnChartVersionList.InstanceGroupByArtifact>() {
          public int compare(InstanceGroupedOnChartVersionList.InstanceGroupByArtifact o1,
              InstanceGroupedOnChartVersionList.InstanceGroupByArtifact o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedOnChartVersionList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> groupByArtifact(
      Map<String,
          Map<String,
              Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> instanceGroupedByArtifactList = new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Map<String,
                     Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
             entry : instanceCountMap.entrySet()) {
      final String displayName = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> instanceGroupedOnChartVersionList =
          groupByChartVersion(entry.getValue(), envIdToNameMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedOnChartVersionList)) {
        lastDeployedAt = instanceGroupedOnChartVersionList.get(0).getLastDeployedAt();
      }
      instanceGroupedByArtifactList.add(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact.builder()
                                            .artifact(displayName)
                                            .lastDeployedAt(lastDeployedAt)
                                            .instanceGroupedOnChartVersionList(instanceGroupedOnChartVersionList)
                                            .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByArtifactList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> groupByArtifactV2(
      Map<String,
          Map<String,
              Map<IdentifierRef,
                  Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
          instanceCountMap,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact> instanceGroupedByArtifactList = new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Map<IdentifierRef,
                     Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>>
             entry : instanceCountMap.entrySet()) {
      final String displayName = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> instanceGroupedOnChartVersionList =
          groupByChartVersionV2(entry.getValue(), identifierRefToEnvMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedOnChartVersionList)) {
        lastDeployedAt = instanceGroupedOnChartVersionList.get(0).getLastDeployedAt();
      }
      instanceGroupedByArtifactList.add(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact.builder()
                                            .artifact(displayName)
                                            .artifactLink(artifactToArtifactLinkMap.get(displayName))
                                            .lastDeployedAt(lastDeployedAt)
                                            .instanceGroupedOnChartVersionList(instanceGroupedOnChartVersionList)
                                            .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnArtifact o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByArtifactList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> groupByChartVersion(
      Map<String,
          Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> instanceGroupedOnChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
             entry : instanceCountMap.entrySet()) {
      final String chartVersion = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
          groupByEnvironment(entry.getValue(), envIdToNameMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentList)) {
        lastDeployedAt = instanceGroupedByEnvironmentList.get(0).getLastDeployedAt();
      }
      instanceGroupedOnChartVersionList.add(InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion.builder()
                                                .chartVersion(chartVersion)
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupedOnEnvironmentList(instanceGroupedByEnvironmentList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedOnChartVersionList,
        new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedOnChartVersionList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> groupByChartVersionV2(
      Map<String,
          Map<IdentifierRef, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>>
          instanceCountMap,
      Map<IdentifierRef, Environment> identifierRefToEnvMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion> instanceGroupedOnChartVersionList =
        new ArrayList<>();
    for (Map.Entry<String,
             Map<IdentifierRef,
                 Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>> entry :
        instanceCountMap.entrySet()) {
      final String chartVersion = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
          groupByEnvironmentV2(entry.getValue(), identifierRefToEnvMap);
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentList)) {
        lastDeployedAt = instanceGroupedByEnvironmentList.get(0).getLastDeployedAt();
      }
      instanceGroupedOnChartVersionList.add(InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion.builder()
                                                .chartVersion(chartVersion)
                                                .lastDeployedAt(lastDeployedAt)
                                                .instanceGroupedOnEnvironmentList(instanceGroupedByEnvironmentList)
                                                .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedOnChartVersionList,
        new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnChartVersion o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedOnChartVersionList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> groupByEnvironment(
      Map<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>
          instanceCountMap,
      Map<String, String> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();
    for (Map.Entry<String, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>
             entry : instanceCountMap.entrySet()) {
      final String envId = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupByEnvironmentType(entry.getValue());
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }
      instanceGroupedByEnvironmentList.add(
          InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment.builder()
              .envId(envId)
              .envName(envIdToNameMap.get(envId))
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedOnEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
              .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByEnvironmentList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByEnvironmentList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> groupByEnvironmentV2(
      Map<IdentifierRef, Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>>
          instanceCountMap,
      Map<IdentifierRef, Environment> envIdToNameMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();
    for (Map.Entry<IdentifierRef,
             Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>>> entry :
        instanceCountMap.entrySet()) {
      final IdentifierRef envIdRef = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupByEnvironmentType(entry.getValue());
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }
      instanceGroupedByEnvironmentList.add(
          InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment.builder()
              .envId(envIdRef.buildScopedIdentifier())
              .envName(envIdToNameMap.get(envIdRef).getName())
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedOnEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
              .orgIdentifier(envIdRef.getOrgIdentifier())
              .projectIdentifier(envIdRef.getProjectIdentifier())
              .build());
    }
    // sort based on last deployed time
    Collections.sort(
        instanceGroupedByEnvironmentList, new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironment o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByEnvironmentList;
  }

  private List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> groupByEnvironmentType(
      Map<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>> instanceCountMap) {
    List<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType> instanceGroupedByEnvironmentTypeList =
        new ArrayList<>();
    for (Map.Entry<EnvironmentType, List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>> entry :
        instanceCountMap.entrySet()) {
      EnvironmentType environmentType = entry.getKey();
      List<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure> instanceGroupedByInfrastructureList =
          entry.getValue();
      // sort based on last deployed time
      Collections.sort(instanceGroupedByInfrastructureList,
          new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure>() {
            public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure o1,
                InstanceGroupedOnArtifactList.InstanceGroupedOnInfrastructure o2) {
              return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
            }
          });
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = instanceGroupedByInfrastructureList.get(0).getLastDeployedAt();
      }
      instanceGroupedByEnvironmentTypeList.add(
          InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType.builder()
              .environmentType(environmentType)
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedOnInfrastructureList(instanceGroupedByInfrastructureList)
              .build());
    }
    // sort based on last deployed time
    Collections.sort(instanceGroupedByEnvironmentTypeList,
        new Comparator<InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType>() {
          public int compare(InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType o1,
              InstanceGroupedOnArtifactList.InstanceGroupedOnEnvironmentType o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });
    return instanceGroupedByEnvironmentTypeList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> groupByEnvironment(
      Map<String,
          Map<EnvironmentType,
              Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
          instanceCountMap,
      Map<String, String> infraIdToNameMap, Map<String, String> envIdToNameMap,
      Map<String, List<String>> envToEnvGroupMap, boolean isGitOps, Map<String, String> artifactToArtifactLinkMap,
      boolean isGitOpsMergeEnabled) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();

    for (Map.Entry<String,
             Map<EnvironmentType,
                 Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
             entry : instanceCountMap.entrySet()) {
      final String envId = entry.getKey();
      List<String> envGroupList = envToEnvGroupMap.get(envId);

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupedByEnvironmentTypes(entry.getValue(), infraIdToNameMap, isGitOps, artifactToArtifactLinkMap,
              Collections.emptySet(), isGitOpsMergeEnabled);

      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }

      if (isEmpty(envGroupList)) {
        instanceGroupedByEnvironmentList.add(
            InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment.builder()
                .envId(envId)
                .envGroups(Collections.emptyList())
                .envName(envIdToNameMap.get(envId))
                .instanceGroupedByEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
                .lastDeployedAt(lastDeployedAt)
                .build());

      } else {
        instanceGroupedByEnvironmentList.add(
            InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment.builder()
                .envId(envId)
                .envGroups(envGroupList)
                .envName(envIdToNameMap.get(envId))
                .instanceGroupedByEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
                .lastDeployedAt(lastDeployedAt)
                .build());
      }
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByEnvironmentList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByEnvironmentList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> groupByEnvironmentV2(
      Map<IdentifierRef,
          Map<EnvironmentType,
              Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
          instanceCountMap,
      Map<String, String> infraIdToNameMap, Map<IdentifierRef, Environment> identifierRefToEnvMap,
      Map<IdentifierRef, List<String>> envToEnvGroupMap, boolean isGitOps,
      Map<String, String> artifactToArtifactLinkMap, boolean useScopeInfo, Set<String> clusterIdSet,
      boolean isGitOpsMergeEnabled) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList =
        new ArrayList<>();

    for (Map.Entry<IdentifierRef,
             Map<EnvironmentType,
                 Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>>
             entry : instanceCountMap.entrySet()) {
      final IdentifierRef envIdRef = entry.getKey();
      List<String> envGroupList = envToEnvGroupMap.get(envIdRef);

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList =
          groupedByEnvironmentTypes(entry.getValue(), infraIdToNameMap, isGitOps, artifactToArtifactLinkMap,
              clusterIdSet, isGitOpsMergeEnabled);

      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByEnvironmentTypeList)) {
        lastDeployedAt = instanceGroupedByEnvironmentTypeList.get(0).getLastDeployedAt();
      }

      if (isEmpty(envGroupList)) {
        instanceGroupedByEnvironmentList.add(
            InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment.builder()
                .envId(envIdRef.buildScopedIdentifier())
                .envGroups(Collections.emptyList())
                .envName(identifierRefToEnvMap.get(envIdRef).getName())
                .instanceGroupedByEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
                .lastDeployedAt(lastDeployedAt)
                // envIdRef has updated value as it was populated with scope info map
                .orgIdentifier(
                    useScopeInfo ? envIdRef.getOrgIdentifier() : identifierRefToEnvMap.get(envIdRef).getOrgIdentifier())
                .projectIdentifier(useScopeInfo ? envIdRef.getProjectIdentifier()
                                                : identifierRefToEnvMap.get(envIdRef).getProjectIdentifier())
                .build());

      } else {
        instanceGroupedByEnvironmentList.add(
            InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment.builder()
                .envId(envIdRef.buildScopedIdentifier())
                .envGroups(envGroupList)
                .envName(identifierRefToEnvMap.get(envIdRef).getName())
                .instanceGroupedByEnvironmentTypeList(instanceGroupedByEnvironmentTypeList)
                .lastDeployedAt(lastDeployedAt)
                .orgIdentifier(
                    useScopeInfo ? envIdRef.getOrgIdentifier() : identifierRefToEnvMap.get(envIdRef).getOrgIdentifier())
                .projectIdentifier(useScopeInfo ? envIdRef.getProjectIdentifier()
                                                : identifierRefToEnvMap.get(envIdRef).getProjectIdentifier())
                .build());
      }
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByEnvironmentList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironment o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByEnvironmentList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> groupedByEnvironmentTypes(
      Map<EnvironmentType,
          Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>
          instanceCountMap,
      Map<String, String> infraIdToNameMap, boolean isGitOps, Map<String, String> artifactToArtifactLinkMap,
      Set<String> clusterIdSet, boolean isGitOpsMergeEnabled) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList =
        new ArrayList<>();

    for (Map.Entry<EnvironmentType,
             Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>>
             entry : instanceCountMap.entrySet()) {
      EnvironmentType environmentType = entry.getKey();

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
          groupedByInfrastructures(entry.getValue(), infraIdToNameMap, isGitOps, artifactToArtifactLinkMap,
              clusterIdSet, isGitOpsMergeEnabled);

      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = instanceGroupedByInfrastructureList.get(0).getLastDeployedAt();
      }

      instanceGroupedByEnvironmentTypeList.add(
          InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType.builder()
              .environmentType(environmentType)
              .instanceGroupedByInfrastructureList(instanceGroupedByInfrastructureList)
              .lastDeployedAt(lastDeployedAt)
              .build());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByEnvironmentTypeList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByEnvironmentType o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByEnvironmentTypeList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> groupedByInfrastructures(
      Map<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>
          instanceCountMap,
      Map<String, String> infraIdToNameMap, boolean isGitOps, Map<String, String> artifactToArtifactLinkMap,
      Set<String> clusterIdSet, boolean isGitOpsMergeEnabled) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
        new ArrayList<>();

    for (Map.Entry<String, Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>>>
             entry : instanceCountMap.entrySet()) {
      String infraId = entry.getKey();

      List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> instanceGroupedByArtifactList =
          groupedByArtifacts(entry.getValue(), artifactToArtifactLinkMap);

      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByArtifactList)) {
        lastDeployedAt = instanceGroupedByArtifactList.get(0).getLastDeployedAt();
      }

      InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure
          .InstanceGroupedByInfrastructureBuilder infrastructureBuilder =
          InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure.builder();
      if ((isGitOpsMergeEnabled && clusterIdSet.contains(infraId)) || (!isGitOpsMergeEnabled && isGitOps)) {
        infrastructureBuilder.clusterId(infraId).agentId(infraIdToNameMap.get(infraId));
      } else {
        infrastructureBuilder.infrastructureId(infraId).infrastructureName(infraIdToNameMap.get(infraId));
      }
      infrastructureBuilder.instanceGroupedByArtifactList(instanceGroupedByArtifactList).lastDeployedAt(lastDeployedAt);
      instanceGroupedByInfrastructureList.add(infrastructureBuilder.build());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByInfrastructureList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByInfrastructure o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByInfrastructureList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> groupedByArtifacts(
      Map<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>> instanceCountMap,
      Map<String, String> artifactToArtifactLinkMap) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact> instanceGroupedByArtifactList = new ArrayList<>();

    for (Map.Entry<String, Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>> entry :
        instanceCountMap.entrySet()) {
      List<InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion> instanceGroupedByChartVersionList =
          groupedByChartVersion(entry.getValue());
      long lastDeployedAt = 0l;
      if (isNotEmpty(instanceGroupedByChartVersionList)) {
        lastDeployedAt = instanceGroupedByChartVersionList.get(0).getLastDeployedAt();
      }

      InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact instanceGroupedByArtifact =
          InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact.builder()
              .artifact(entry.getKey())
              .artifactLink(artifactToArtifactLinkMap.get(entry.getKey()))
              .lastDeployedAt(lastDeployedAt)
              .instanceGroupedByChartVersionList(instanceGroupedByChartVersionList)
              .build();
      instanceGroupedByArtifactList.add(instanceGroupedByArtifact);
    }

    // sort based on last deployed time

    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByArtifact o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByArtifactList;
  }

  public List<InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion> groupedByChartVersion(
      Map<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion> instanceCountMap) {
    List<InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion> instanceGroupedByChartVersionList =
        new ArrayList<>();

    for (Map.Entry<String, InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion> entry :
        instanceCountMap.entrySet()) {
      instanceGroupedByChartVersionList.add(entry.getValue());
    }

    // sort based on last deployed time

    Collections.sort(instanceGroupedByChartVersionList,
        new Comparator<InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion>() {
          public int compare(InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion o1,
              InstanceGroupedByEnvironmentList.InstanceGroupedByChartVersion o2) {
            return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
          }
        });

    return instanceGroupedByChartVersionList;
  }

  public ArtifactInstanceDetails getArtifactInstanceDetailsFromMap(
      Map<String, Map<String, ArtifactDeploymentDetail>> artifactDeploymentDetailsMap,
      Map<String, String> envIdToEnvNameMap, Map<String, EnvironmentType> envIdToEnvTypeMap,
      List<EnvironmentGroupEntity> environmentGroupEntities,
      Map<String, List<ArtifactDeploymentDetail>> envToArtifactMap, Map<String, String> artifactToArtifactLinkMap,
      ScopeInfo scopeInfo) {
    Map<String, EnvironmentGroupInstanceDetails> artifactToEnvGroupMap = new HashMap<>();

    boolean useScopeInfo = scopeInfo != null;
    Set<String> envIds = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        List<ArtifactDeploymentDetail> allArtifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Set<String> artifacts = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          envId = convertIdToRef(useScopeInfo ? scopeInfo.getAccountIdentifier() : envGroupEntity.getAccountId(),
              useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier(),
              useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier(), envId);
          List<ArtifactDeploymentDetail> artifactDeploymentDetails = envToArtifactMap.get(envId);
          ArtifactDeploymentDetail artifactDeploymentDetail = null;

          final EnvironmentType envType = envIdToEnvTypeMap.get(envId);

          envIds.add(envId);
          if (envType == null) {
            continue;
          }

          if (isNotEmpty(artifactDeploymentDetails)) {
            sortArtifactDeploymentDetailList(artifactDeploymentDetails);
            for (ArtifactDeploymentDetail currentArtifactDeploymentDetail : artifactDeploymentDetails) {
              if (currentArtifactDeploymentDetail == null || isEmpty(currentArtifactDeploymentDetail.getArtifact())) {
                if (currentArtifactDeploymentDetail == null) {
                  currentArtifactDeploymentDetail =
                      ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
                }
                currentArtifactDeploymentDetail.setArtifact("");
              }
              allArtifactDeploymentDetailList.add(currentArtifactDeploymentDetail);
            }

            artifactDeploymentDetail = artifactDeploymentDetails.get(0);
          }

          envTypes.add(envType);
          if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getArtifact())) {
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail =
                  ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
            }
            artifactDeploymentDetail.setArtifact("");
          }

          artifacts.add(artifactDeploymentDetail.getArtifact());
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }

        Set<String> uniqueArtifacts = new HashSet<>();
        if (isNotEmpty(artifactDeploymentDetailList)) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          for (ArtifactDeploymentDetail allArtifactDeploymentDetail : allArtifactDeploymentDetailList) {
            if (uniqueArtifacts.contains(allArtifactDeploymentDetail.getArtifact())) {
              continue;
            }
            uniqueArtifacts.add(allArtifactDeploymentDetail.getArtifact());
            EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .isDrift((artifacts.size() > 1)
                        || (artifacts.size() == 1 && !artifacts.contains(allArtifactDeploymentDetail.getArtifact())))
                    .build();
            if (artifactToEnvGroupMap.containsKey(allArtifactDeploymentDetail.getArtifact())) {
              artifactToEnvGroupMap.get(allArtifactDeploymentDetail.getArtifact())
                  .getEnvironmentGroupInstanceDetails()
                  .add(environmentGroupInstanceDetail);
            } else {
              artifactToEnvGroupMap.put(allArtifactDeploymentDetail.getArtifact(),
                  EnvironmentGroupInstanceDetails.builder()
                      .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                      .build());
            }
          }
        }
      }
    }

    for (Map.Entry<String, String> entry : envIdToEnvNameMap.entrySet()) {
      final String envId = entry.getKey();
      if (!envIds.contains(envId)) {
        final EnvironmentType envType = envIdToEnvTypeMap.get(envId);
        if (envType == null) {
          continue;
        }
        final String envName = entry.getValue();
        final List<ArtifactDeploymentDetail> artifactDeploymentDetails = envToArtifactMap.get(envId);
        sortArtifactDeploymentDetailList(artifactDeploymentDetails);
        ArtifactDeploymentDetail artifactDeploymentDetail = null;
        if (isNotEmpty(artifactDeploymentDetails)) {
          artifactDeploymentDetail = artifactDeploymentDetails.get(0);
        }

        if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getArtifact())) {
          if (artifactDeploymentDetail == null) {
            artifactDeploymentDetail =
                ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
          }
          artifactDeploymentDetail.setArtifact("");
        }

        for (ArtifactDeploymentDetail allArtifactDeploymentDetail : envToArtifactMap.get(envId)) {
          String artifactName =
              isEmpty(allArtifactDeploymentDetail.getArtifact()) ? "" : allArtifactDeploymentDetail.getArtifact();

          EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
              EnvironmentGroupInstanceDetail.builder()
                  .name(envName)
                  .id(envId)
                  .environmentTypes(envType == null ? null : Collections.singletonList(envType))
                  .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                  .isEnvGroup(false)
                  .isDrift(!artifactDeploymentDetail.getArtifact().equals(artifactName))
                  .build();

          if (artifactToEnvGroupMap.containsKey(artifactName)) {
            artifactToEnvGroupMap.get(artifactName)
                .getEnvironmentGroupInstanceDetails()
                .add(environmentGroupInstanceDetail);
          } else {
            artifactToEnvGroupMap.put(artifactName,
                EnvironmentGroupInstanceDetails.builder()
                    .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                    .build());
          }
        }
      }
    }

    List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetails = new ArrayList<>();
    for (Map.Entry<String, EnvironmentGroupInstanceDetails> entry : artifactToEnvGroupMap.entrySet()) {
      artifactInstanceDetails.add(ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
                                      .artifact(entry.getKey())
                                      .artifactLink(artifactToArtifactLinkMap.get(entry.getKey()))
                                      .environmentGroupInstanceDetails(entry.getValue())
                                      .build());
    }

    sortArtifactInstanceDetailList(artifactInstanceDetails);
    return ArtifactInstanceDetails.builder().artifactInstanceDetails(artifactInstanceDetails).build();
  }

  public ArtifactInstanceDetails getArtifactInstanceDetailsFromMapV2(
      Map<IdentifierRef, Environment> identifierRefToEnvMap, List<EnvironmentGroupEntity> environmentGroupEntities,
      Map<IdentifierRef, List<ArtifactDeploymentDetail>> identifierRefToArtifactMap,
      Map<String, String> artifactToArtifactLinkMap, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;

    Map<String, EnvironmentGroupInstanceDetails> artifactToEnvGroupMap = new HashMap<>();
    Set<IdentifierRef> envRefSet = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        List<ArtifactDeploymentDetail> allArtifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Set<String> artifacts = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          IdentifierRef environmentRef =
              IdentifierRef.builder()
                  .accountIdentifier(
                      useScopeInfo ? scopeInfo.getAccountIdentifier() : envGroupEntity.getAccountIdentifier())
                  .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier())
                  .projectIdentifier(
                      useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier())
                  .identifier(envId)
                  .scope(getEntityScope(useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier(),
                      useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier()))
                  .build();
          List<ArtifactDeploymentDetail> artifactDeploymentDetails = identifierRefToArtifactMap.get(environmentRef);
          ArtifactDeploymentDetail artifactDeploymentDetail = null;

          final EnvironmentType envType = identifierRefToEnvMap.get(environmentRef).getType();

          envRefSet.add(environmentRef);
          if (envType == null) {
            continue;
          }

          if (isNotEmpty(artifactDeploymentDetails)) {
            sortArtifactDeploymentDetailList(artifactDeploymentDetails);
            for (ArtifactDeploymentDetail currentArtifactDeploymentDetail : artifactDeploymentDetails) {
              if (currentArtifactDeploymentDetail == null || isEmpty(currentArtifactDeploymentDetail.getArtifact())) {
                if (currentArtifactDeploymentDetail == null) {
                  currentArtifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                                        .envName(identifierRefToEnvMap.get(environmentRef).getName())
                                                        .envId(environmentRef.buildScopedIdentifier())
                                                        .build();
                }
                currentArtifactDeploymentDetail.setArtifact("");
              }
              allArtifactDeploymentDetailList.add(currentArtifactDeploymentDetail);
            }

            artifactDeploymentDetail = artifactDeploymentDetails.get(0);
          }

          envTypes.add(envType);
          if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getArtifact())) {
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                             .envName(identifierRefToEnvMap.get(environmentRef).getName())
                                             .envId(environmentRef.buildScopedIdentifier())
                                             .build();
            }
            artifactDeploymentDetail.setArtifact("");
          }

          artifacts.add(artifactDeploymentDetail.getArtifact());
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }

        Set<String> uniqueArtifacts = new HashSet<>();
        if (isNotEmpty(artifactDeploymentDetailList)) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          for (ArtifactDeploymentDetail allArtifactDeploymentDetail : allArtifactDeploymentDetailList) {
            if (uniqueArtifacts.contains(allArtifactDeploymentDetail.getArtifact())) {
              continue;
            }
            uniqueArtifacts.add(allArtifactDeploymentDetail.getArtifact());
            EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier())
                    .projectIdentifier(
                        useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier())
                    .isDrift((artifacts.size() > 1)
                        || (artifacts.size() == 1 && !artifacts.contains(allArtifactDeploymentDetail.getArtifact())))
                    .build();
            if (artifactToEnvGroupMap.containsKey(allArtifactDeploymentDetail.getArtifact())) {
              artifactToEnvGroupMap.get(allArtifactDeploymentDetail.getArtifact())
                  .getEnvironmentGroupInstanceDetails()
                  .add(environmentGroupInstanceDetail);
            } else {
              artifactToEnvGroupMap.put(allArtifactDeploymentDetail.getArtifact(),
                  EnvironmentGroupInstanceDetails.builder()
                      .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                      .build());
            }
          }
        }
      }
    }

    for (Map.Entry<IdentifierRef, Environment> entry : identifierRefToEnvMap.entrySet()) {
      final IdentifierRef envRef = entry.getKey();
      if (!envRefSet.contains(envRef)) {
        final EnvironmentType envType = identifierRefToEnvMap.get(envRef).getType();
        if (envType == null) {
          continue;
        }
        final String envName = entry.getValue().getName();
        final List<ArtifactDeploymentDetail> artifactDeploymentDetails =
            identifierRefToArtifactMap.getOrDefault(envRef, new ArrayList<>());
        sortArtifactDeploymentDetailList(artifactDeploymentDetails);
        ArtifactDeploymentDetail artifactDeploymentDetail = null;
        if (isNotEmpty(artifactDeploymentDetails)) {
          artifactDeploymentDetail = artifactDeploymentDetails.get(0);
        }

        if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getArtifact())) {
          if (artifactDeploymentDetail == null) {
            artifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                           .envName(envName)
                                           .envId(envRef.buildScopedIdentifier())
                                           .orgIdentifier(entry.getKey().getOrgIdentifier())
                                           .projectIdentifier(entry.getKey().getProjectIdentifier())
                                           .build();
          }
          artifactDeploymentDetail.setArtifact("");
        }

        for (ArtifactDeploymentDetail allArtifactDeploymentDetail : artifactDeploymentDetails) {
          String artifactName =
              isEmpty(allArtifactDeploymentDetail.getArtifact()) ? "" : allArtifactDeploymentDetail.getArtifact();

          EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
              EnvironmentGroupInstanceDetail.builder()
                  .name(envName)
                  .id(entry.getKey().buildScopedIdentifier())
                  .environmentTypes(envType == null ? null : Collections.singletonList(envType))
                  .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                  .isEnvGroup(false)
                  .isDrift(!artifactDeploymentDetail.getArtifact().equals(artifactName))
                  .orgIdentifier(entry.getKey().getOrgIdentifier())
                  .projectIdentifier(entry.getKey().getProjectIdentifier())
                  .build();

          if (artifactToEnvGroupMap.containsKey(artifactName)) {
            artifactToEnvGroupMap.get(artifactName)
                .getEnvironmentGroupInstanceDetails()
                .add(environmentGroupInstanceDetail);
          } else {
            artifactToEnvGroupMap.put(artifactName,
                EnvironmentGroupInstanceDetails.builder()
                    .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                    .build());
          }
        }
      }
    }

    List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetails = new ArrayList<>();
    for (Map.Entry<String, EnvironmentGroupInstanceDetails> entry : artifactToEnvGroupMap.entrySet()) {
      artifactInstanceDetails.add(ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
                                      .artifact(entry.getKey())
                                      .artifactLink(artifactToArtifactLinkMap.get(entry.getKey()))
                                      .environmentGroupInstanceDetails(entry.getValue())
                                      .build());
    }

    sortArtifactInstanceDetailList(artifactInstanceDetails);
    return ArtifactInstanceDetails.builder().artifactInstanceDetails(artifactInstanceDetails).build();
  }

  private void sortArtifactInstanceDetailList(
      List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetailList) {
    Collections.sort(artifactInstanceDetailList, new Comparator<ArtifactInstanceDetails.ArtifactInstanceDetail>() {
      public int compare(
          ArtifactInstanceDetails.ArtifactInstanceDetail o1, ArtifactInstanceDetails.ArtifactInstanceDetail o2) {
        int c;
        if (o1.getArtifact() == null && o2.getArtifact() == null) {
          c = 0;
        } else if (o1.getArtifact() == null) {
          c = -1;
        } else if (o2.getArtifact() == null) {
          c = 1;
        } else {
          c = o1.getArtifact().compareTo(o2.getArtifact());
        }
        return c;
      }
    });
  }

  public ChartVersionInstanceDetails getChartVersionInstanceDetailsFromMap(Map<String, String> envIdToEnvNameMap,
      Map<String, EnvironmentType> envIdToEnvTypeMap, List<EnvironmentGroupEntity> environmentGroupEntities,
      Map<String, List<ArtifactDeploymentDetail>> envToArtifactMap, ScopeInfo scopeInfo) {
    Map<String, EnvironmentGroupInstanceDetails> chartVersionToEnvGroupMap = new HashMap<>();

    boolean useScopeInfo = scopeInfo != null;
    Set<String> envIds = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        List<ArtifactDeploymentDetail> allArtifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Set<String> chartVersions = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          envId = convertIdToRef(useScopeInfo ? scopeInfo.getAccountIdentifier() : envGroupEntity.getAccountId(),
              useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier(),
              useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier(), envId);
          List<ArtifactDeploymentDetail> artifactDeploymentDetails = envToArtifactMap.get(envId);
          ArtifactDeploymentDetail artifactDeploymentDetail = null;

          final EnvironmentType envType = envIdToEnvTypeMap.get(envId);

          envIds.add(envId);
          if (envType == null) {
            continue;
          }

          if (isNotEmpty(artifactDeploymentDetails)) {
            sortArtifactDeploymentDetailList(artifactDeploymentDetails);
            for (ArtifactDeploymentDetail currentArtifactDeploymentDetail : artifactDeploymentDetails) {
              if (currentArtifactDeploymentDetail == null
                  || isEmpty(currentArtifactDeploymentDetail.getChartVersion())) {
                if (currentArtifactDeploymentDetail == null) {
                  currentArtifactDeploymentDetail =
                      ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
                }
              }
              allArtifactDeploymentDetailList.add(currentArtifactDeploymentDetail);
            }

            artifactDeploymentDetail = artifactDeploymentDetails.get(0);
          }

          envTypes.add(envType);
          if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getChartVersion())) {
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail =
                  ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
            }
          }

          chartVersions.add(artifactDeploymentDetail.getChartVersion());
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }

        Set<String> uniqueChartVersions = new HashSet<>();
        if (isNotEmpty(artifactDeploymentDetailList)) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          for (ArtifactDeploymentDetail allArtifactDeploymentDetail : allArtifactDeploymentDetailList) {
            if (uniqueChartVersions.contains(allArtifactDeploymentDetail.getChartVersion())) {
              continue;
            }
            uniqueChartVersions.add(allArtifactDeploymentDetail.getChartVersion());
            EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .isDrift((chartVersions.size() > 1)
                        || (chartVersions.size() == 1
                            && !chartVersions.contains(allArtifactDeploymentDetail.getChartVersion())))
                    .build();
            if (chartVersionToEnvGroupMap.containsKey(allArtifactDeploymentDetail.getChartVersion())) {
              chartVersionToEnvGroupMap.get(allArtifactDeploymentDetail.getChartVersion())
                  .getEnvironmentGroupInstanceDetails()
                  .add(environmentGroupInstanceDetail);
            } else {
              chartVersionToEnvGroupMap.put(allArtifactDeploymentDetail.getChartVersion(),
                  EnvironmentGroupInstanceDetails.builder()
                      .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                      .build());
            }
          }
        }
      }
    }

    for (Map.Entry<String, String> entry : envIdToEnvNameMap.entrySet()) {
      final String envId = entry.getKey();
      if (!envIds.contains(envId)) {
        final EnvironmentType envType = envIdToEnvTypeMap.get(envId);
        if (envType == null) {
          continue;
        }
        final String envName = entry.getValue();
        final List<ArtifactDeploymentDetail> artifactDeploymentDetails = envToArtifactMap.get(envId);
        sortArtifactDeploymentDetailList(artifactDeploymentDetails);
        ArtifactDeploymentDetail artifactDeploymentDetail = null;
        if (isNotEmpty(artifactDeploymentDetails)) {
          artifactDeploymentDetail = artifactDeploymentDetails.get(0);
        }

        if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getChartVersion())) {
          if (artifactDeploymentDetail == null) {
            artifactDeploymentDetail =
                ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
          }
        }

        for (ArtifactDeploymentDetail allArtifactDeploymentDetail : envToArtifactMap.get(envId)) {
          String chartVersion = isEmpty(allArtifactDeploymentDetail.getChartVersion())
              ? ""
              : allArtifactDeploymentDetail.getChartVersion();

          String chartVersionToCompareTo =
              isEmpty(artifactDeploymentDetail.getChartVersion()) ? "" : artifactDeploymentDetail.getChartVersion();

          EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
              EnvironmentGroupInstanceDetail.builder()
                  .name(envName)
                  .id(envId)
                  .environmentTypes(envType == null ? null : Collections.singletonList(envType))
                  .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                  .isEnvGroup(false)
                  .isDrift(!chartVersionToCompareTo.equals(chartVersion))
                  .build();

          if (chartVersionToEnvGroupMap.containsKey(chartVersion)) {
            chartVersionToEnvGroupMap.get(chartVersion)
                .getEnvironmentGroupInstanceDetails()
                .add(environmentGroupInstanceDetail);
          } else {
            chartVersionToEnvGroupMap.put(chartVersion,
                EnvironmentGroupInstanceDetails.builder()
                    .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                    .build());
          }
        }
      }
    }

    List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> chartVersionInstanceDetails = new ArrayList<>();
    for (Map.Entry<String, EnvironmentGroupInstanceDetails> entry : chartVersionToEnvGroupMap.entrySet()) {
      chartVersionInstanceDetails.add(ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
                                          .chartVersion(entry.getKey())
                                          .environmentGroupInstanceDetails(entry.getValue())
                                          .build());
    }

    sortChartVersionInstanceDetailList(chartVersionInstanceDetails);
    return ChartVersionInstanceDetails.builder().chartVersionInstanceDetails(chartVersionInstanceDetails).build();
  }

  public ChartVersionInstanceDetails getChartVersionInstanceDetailsFromMap(
      List<EnvironmentGroupEntity> environmentGroupEntities,
      Map<IdentifierRef, List<ArtifactDeploymentDetail>> identifierRefToArtifactMap,
      Map<IdentifierRef, Environment> identifierRefToEnvMap) {
    // environmentGroupEntities has been passed as null
    Map<String, EnvironmentGroupInstanceDetails> chartVersionToEnvGroupMap = new HashMap<>();
    Set<IdentifierRef> envRefSet = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        List<ArtifactDeploymentDetail> allArtifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Set<String> chartVersions = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          IdentifierRef environmentRef =
              IdentifierRef.builder()
                  .accountIdentifier(envGroupEntity.getAccountIdentifier())
                  .orgIdentifier(envGroupEntity.getOrgIdentifier())
                  .projectIdentifier(envGroupEntity.getProjectIdentifier())
                  .identifier(envId)
                  .scope(getEntityScope(envGroupEntity.getOrgIdentifier(), envGroupEntity.getProjectIdentifier()))
                  .build();
          List<ArtifactDeploymentDetail> artifactDeploymentDetails = identifierRefToArtifactMap.get(environmentRef);
          ArtifactDeploymentDetail artifactDeploymentDetail = null;

          final EnvironmentType envType = identifierRefToEnvMap.get(environmentRef).getType();

          envRefSet.add(environmentRef);
          if (envType == null) {
            continue;
          }

          if (isNotEmpty(artifactDeploymentDetails)) {
            sortArtifactDeploymentDetailList(artifactDeploymentDetails);
            for (ArtifactDeploymentDetail currentArtifactDeploymentDetail : artifactDeploymentDetails) {
              if (currentArtifactDeploymentDetail == null
                  || isEmpty(currentArtifactDeploymentDetail.getChartVersion())) {
                if (currentArtifactDeploymentDetail == null) {
                  currentArtifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                                        .envName(identifierRefToEnvMap.get(environmentRef).getName())
                                                        .envId(environmentRef.buildScopedIdentifier())
                                                        .orgIdentifier(envGroupEntity.getOrgIdentifier())
                                                        .projectIdentifier(envGroupEntity.getProjectIdentifier())
                                                        .build();
                }
              }
              allArtifactDeploymentDetailList.add(currentArtifactDeploymentDetail);
            }

            artifactDeploymentDetail = artifactDeploymentDetails.get(0);
          }

          envTypes.add(envType);
          if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getChartVersion())) {
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                             .envName(identifierRefToEnvMap.get(environmentRef).getName())
                                             .envId(environmentRef.buildScopedIdentifier())
                                             .orgIdentifier(envGroupEntity.getOrgIdentifier())
                                             .projectIdentifier(envGroupEntity.getProjectIdentifier())
                                             .build();
            }
          }

          chartVersions.add(artifactDeploymentDetail.getChartVersion());
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }

        Set<String> uniqueChartVersions = new HashSet<>();
        if (isNotEmpty(artifactDeploymentDetailList)) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          for (ArtifactDeploymentDetail allArtifactDeploymentDetail : allArtifactDeploymentDetailList) {
            if (uniqueChartVersions.contains(allArtifactDeploymentDetail.getChartVersion())) {
              continue;
            }
            uniqueChartVersions.add(allArtifactDeploymentDetail.getChartVersion());
            EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .orgIdentifier(envGroupEntity.getOrgIdentifier())
                    .projectIdentifier(envGroupEntity.getProjectIdentifier())
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .isDrift((chartVersions.size() > 1)
                        || (chartVersions.size() == 1
                            && !chartVersions.contains(allArtifactDeploymentDetail.getChartVersion())))
                    .build();
            if (chartVersionToEnvGroupMap.containsKey(allArtifactDeploymentDetail.getChartVersion())) {
              chartVersionToEnvGroupMap.get(allArtifactDeploymentDetail.getChartVersion())
                  .getEnvironmentGroupInstanceDetails()
                  .add(environmentGroupInstanceDetail);
            } else {
              chartVersionToEnvGroupMap.put(allArtifactDeploymentDetail.getChartVersion(),
                  EnvironmentGroupInstanceDetails.builder()
                      .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                      .build());
            }
          }
        }
      }
    }

    for (Map.Entry<IdentifierRef, Environment> entry : identifierRefToEnvMap.entrySet()) {
      if (!envRefSet.contains(entry.getKey())) {
        final EnvironmentType envType = entry.getValue().getType();
        if (envType == null) {
          continue;
        }
        final String envName = entry.getValue().getName();
        final List<ArtifactDeploymentDetail> artifactDeploymentDetails =
            identifierRefToArtifactMap.getOrDefault(entry.getKey(), new ArrayList<>());
        sortArtifactDeploymentDetailList(artifactDeploymentDetails);
        ArtifactDeploymentDetail artifactDeploymentDetail = null;
        if (isNotEmpty(artifactDeploymentDetails)) {
          artifactDeploymentDetail = artifactDeploymentDetails.get(0);
        }

        if (artifactDeploymentDetail == null || isEmpty(artifactDeploymentDetail.getChartVersion())) {
          if (artifactDeploymentDetail == null) {
            artifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                           .envName(envName)
                                           .envId(entry.getKey().buildScopedIdentifier())
                                           .orgIdentifier(entry.getKey().getOrgIdentifier())
                                           .projectIdentifier(entry.getKey().getProjectIdentifier())
                                           .build();
          }
        }

        for (ArtifactDeploymentDetail allArtifactDeploymentDetail : artifactDeploymentDetails) {
          String chartVersion = isEmpty(allArtifactDeploymentDetail.getChartVersion())
              ? ""
              : allArtifactDeploymentDetail.getChartVersion();

          String chartVersionToCompareTo =
              isEmpty(artifactDeploymentDetail.getChartVersion()) ? "" : artifactDeploymentDetail.getChartVersion();

          EnvironmentGroupInstanceDetail environmentGroupInstanceDetail =
              EnvironmentGroupInstanceDetail.builder()
                  .name(envName)
                  .id(entry.getKey().buildScopedIdentifier())
                  .environmentTypes(Collections.singletonList(envType))
                  .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                  .isEnvGroup(false)
                  .isDrift(!chartVersionToCompareTo.equals(chartVersion))
                  .orgIdentifier(entry.getKey().getOrgIdentifier())
                  .projectIdentifier(entry.getKey().getProjectIdentifier())
                  .build();

          if (chartVersionToEnvGroupMap.containsKey(chartVersion)) {
            chartVersionToEnvGroupMap.get(chartVersion)
                .getEnvironmentGroupInstanceDetails()
                .add(environmentGroupInstanceDetail);
          } else {
            chartVersionToEnvGroupMap.put(chartVersion,
                EnvironmentGroupInstanceDetails.builder()
                    .environmentGroupInstanceDetails(new ArrayList<>(Arrays.asList(environmentGroupInstanceDetail)))
                    .build());
          }
        }
      }
    }

    List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> chartVersionInstanceDetails = new ArrayList<>();
    for (Map.Entry<String, EnvironmentGroupInstanceDetails> entry : chartVersionToEnvGroupMap.entrySet()) {
      chartVersionInstanceDetails.add(ChartVersionInstanceDetails.ChartVersionInstanceDetail.builder()
                                          .chartVersion(entry.getKey())
                                          .environmentGroupInstanceDetails(entry.getValue())
                                          .build());
    }

    sortChartVersionInstanceDetailList(chartVersionInstanceDetails);
    return ChartVersionInstanceDetails.builder().chartVersionInstanceDetails(chartVersionInstanceDetails).build();
  }

  public Scope getEntityScope(String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier != null) {
      if (projectIdentifier != null) {
        return Scope.PROJECT;
      } else {
        return Scope.ORG;
      }
    } else {
      return Scope.ACCOUNT;
    }
  }

  private void sortChartVersionInstanceDetailList(
      List<ChartVersionInstanceDetails.ChartVersionInstanceDetail> chartVersionInstanceDetails) {
    Collections.sort(
        chartVersionInstanceDetails, new Comparator<ChartVersionInstanceDetails.ChartVersionInstanceDetail>() {
          public int compare(ChartVersionInstanceDetails.ChartVersionInstanceDetail o1,
              ChartVersionInstanceDetails.ChartVersionInstanceDetail o2) {
            int c;
            if (o1.getChartVersion() == null && o2.getChartVersion() == null) {
              c = 0;
            } else if (o1.getChartVersion() == null) {
              c = -1;
            } else if (o2.getChartVersion() == null) {
              c = 1;
            } else {
              c = o1.getChartVersion().compareTo(o2.getChartVersion());
            }
            return c;
          }
        });
  }

  private void sortEnvironmentGroupInstanceDetailList(
      List<EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList) {
    Collections.sort(environmentGroupInstanceDetailList, new Comparator<EnvironmentGroupInstanceDetail>() {
      public int compare(EnvironmentGroupInstanceDetail o1, EnvironmentGroupInstanceDetail o2) {
        int c;
        if (isEmpty(o1.getEnvironmentTypes()) && isEmpty(o2.getEnvironmentTypes())) {
          c = 0;
        } else if (isEmpty(o1.getEnvironmentTypes())) {
          c = 1;
        } else if (isEmpty(o2.getEnvironmentTypes())) {
          c = -1;
        } else if (o1.getEnvironmentTypes().size() > 1 && o2.getEnvironmentTypes().size() > 1) {
          c = 0;
        } else if (o1.getEnvironmentTypes().size() == 1
            && o1.getEnvironmentTypes().contains(EnvironmentType.PreProduction)
            && o2.getEnvironmentTypes().size() > 1) {
          c = -1;
        } else if (o2.getEnvironmentTypes().size() == 1
            && o2.getEnvironmentTypes().contains(EnvironmentType.PreProduction)
            && o1.getEnvironmentTypes().size() > 1) {
          c = 1;
        } else if (o1.getEnvironmentTypes().size() == 1 && o1.getEnvironmentTypes().contains(EnvironmentType.Production)
            && o2.getEnvironmentTypes().size() > 1) {
          c = 1;
        } else if (o2.getEnvironmentTypes().size() == 1 && o2.getEnvironmentTypes().contains(EnvironmentType.Production)
            && o1.getEnvironmentTypes().size() > 1) {
          c = -1;
        } else if (o1.getEnvironmentTypes().size() == 1
            && o1.getEnvironmentTypes().contains(EnvironmentType.PreProduction) && o2.getEnvironmentTypes().size() == 1
            && o2.getEnvironmentTypes().contains(EnvironmentType.Production)) {
          c = -1;
        } else if (o1.getEnvironmentTypes().size() == 1 && o1.getEnvironmentTypes().contains(EnvironmentType.Production)
            && o2.getEnvironmentTypes().size() == 1
            && o2.getEnvironmentTypes().contains(EnvironmentType.PreProduction)) {
          c = 1;
        } else {
          c = 0;
        }
        if (c == 0) {
          if (o1.getName() != null && o2.getName() != null) {
            c = o1.getName().compareTo(o2.getName());
          } else if (o2.getName() != null) {
            c = -1;
          } else if (o1.getName() != null) {
            c = 1;
          }
        }
        return c;
      }
    });
  }

  private void sortArtifactDeploymentDetailList(List<ArtifactDeploymentDetail> artifactDeploymentDetailList) {
    Collections.sort(artifactDeploymentDetailList, new Comparator<ArtifactDeploymentDetail>() {
      public int compare(ArtifactDeploymentDetail o1, ArtifactDeploymentDetail o2) {
        int c;
        if (o1.getLastDeployedAt() > o2.getLastDeployedAt()) {
          c = -1;
        } else if (o1.getLastDeployedAt() < o2.getLastDeployedAt()) {
          c = 1;
        } else {
          c = 0;
        }
        if (c == 0) {
          if (o1.getEnvName() != null && o2.getEnvName() != null) {
            c = o1.getEnvName().compareTo(o2.getEnvName());
          } else if (o2.getEnvName() != null) {
            c = -1;
          } else if (o1.getEnvName() != null) {
            c = 1;
          }
        }
        return c;
      }
    });
  }

  public void sortActiveServiceInstanceInfoWithEnvTypeList(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList) {
    // sort based on last deployed time
    Collections.sort(activeServiceInstanceInfoList, new Comparator<ActiveServiceInstanceInfoWithEnvType>() {
      public int compare(ActiveServiceInstanceInfoWithEnvType o1, ActiveServiceInstanceInfoWithEnvType o2) {
        return Long.compare(o2.getLastDeployedAt(), o1.getLastDeployedAt());
      }
    });
  }

  public void sortServicePipelineInfoList(List<ServicePipelineWithRevertInfo> servicePipelineInfoList) {
    // sort based on last deployed time
    Collections.sort(servicePipelineInfoList, new Comparator<ServicePipelineWithRevertInfo>() {
      public int compare(ServicePipelineWithRevertInfo o1, ServicePipelineWithRevertInfo o2) {
        return Long.compare(o2.getLastExecutedAt(), o1.getLastExecutedAt());
      }
    });
  }

  public void constructEnvironmentNameAndTypeMap(List<Environment> environments, Map<String, String> envIdToNameMap,
      Map<String, EnvironmentType> envIdToEnvTypeMap, Map<String, Optional<ScopeInfo>> scopeInfoMap) {
    for (Environment environment : environments) {
      String envId = environment.getIdentifier();
      if (envId == null) {
        continue;
      }
      ScopeInfo scopeInfo = scopeInfoMap != null
          ? scopeInfoMap.getOrDefault(environment.getParentUniqueId(), Optional.empty()).orElse(null)
          : null;
      boolean useScopeInfo = scopeInfo != null;
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(envId,
          environment.getAccountId(), useScopeInfo ? scopeInfo.getOrgIdentifier() : environment.getOrgIdentifier(),
          useScopeInfo ? scopeInfo.getProjectIdentifier() : environment.getProjectIdentifier());
      envId = identifierRef.buildScopedIdentifier();
      final String envName = environment.getName();
      final EnvironmentType environmentType = environment.getType();
      envIdToNameMap.put(envId, envName);
      envIdToEnvTypeMap.put(envId, environmentType);
    }
  }

  public static IdentifierRef buildIdentifierRef(
      String envId, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return IdentifierRef.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(envId)
        .scope(getEntityScope(orgIdentifier, projectIdentifier))
        .build();
  }

  public static IdentifierRef getIdentifierRef(
      String envScopeId, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String envId = "";
    if (envScopeId == null) {
      return null;
    }
    String[] entityIdentifierRefStringSplit = envScopeId.split(IDENTIFIER_REF_DELIMITER);
    String childScope;
    Scope scope = null;
    int entityScope;
    if (entityIdentifierRefStringSplit.length == 1) {
      // project level child entity.
      entityScope = 2;
      scope = Scope.PROJECT;
      envId = entityIdentifierRefStringSplit[0];
    } else if (entityIdentifierRefStringSplit.length == 2) {
      childScope = entityIdentifierRefStringSplit[0];
      if ("account".equals(childScope)) {
        // account level child entity.
        entityScope = 0;
        envId = entityIdentifierRefStringSplit[1];
        scope = Scope.ACCOUNT;
      } else if ("org".equals(childScope)) {
        // org level child entity.
        entityScope = 1;
        envId = entityIdentifierRefStringSplit[1];
        scope = Scope.ORG;
      } else {
        // invalid scope
        entityScope = -1;
        childScope = "invalid";
      }
    } else {
      // invalid scope
      entityScope = -1;
      childScope = "invalid";
    }

    return IdentifierRef.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier((entityScope == 2 || entityScope == 1) ? orgIdentifier : null)
        .projectIdentifier(entityScope == 2 ? projectIdentifier : null)
        .identifier(envId)
        .scope(scope)
        .build();
  }

  public Map<String, Map<String, ArtifactDeploymentDetail>> constructArtifactToLastDeploymentMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails, Set<String> envIds) {
    Map<String, Map<String, ArtifactDeploymentDetail>> map = new HashMap<>();
    Set<String> envIdSet = new HashSet<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      final String displayName = artifactDeploymentDetail.getDisplayName();
      map.putIfAbsent(displayName, new HashMap<>());
      map.get(displayName)
          .putIfAbsent(envId,
              ArtifactDeploymentDetail.builder()
                  .artifact(displayName)
                  .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                  .artifactLink(artifactDeploymentDetail.getArtifactLink())
                  .build());
      envIdSet.add(envId);
    }
    envIds.addAll(envIdSet);
    return map;
  }

  public Map<String, Map<String, ArtifactDeploymentDetail>> constructChartVersionToLastDeploymentMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails, Set<String> envIds) {
    Map<String, Map<String, ArtifactDeploymentDetail>> map = new HashMap<>();
    Set<String> envIdSet = new HashSet<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      final String chartVersion = artifactDeploymentDetail.getChartVersion();
      map.putIfAbsent(chartVersion, new HashMap<>());
      map.get(chartVersion)
          .putIfAbsent(envId,
              ArtifactDeploymentDetail.builder()
                  .chartVersion(chartVersion)
                  .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                  .build());
      envIdSet.add(envId);
    }
    envIds.addAll(envIdSet);
    return map;
  }

  public Set<String> constructEnvIdsList(List<ArtifactDeploymentDetailModel> artifactDeploymentDetails) {
    Set<String> envIdSet = new HashSet<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      envIdSet.add(envId);
    }
    return envIdSet;
  }

  public void constructIdentifierRefToEnvMap(List<Environment> environments,
      Map<IdentifierRef, Environment> identifierRefToEnvMap, Map<String, Optional<ScopeInfo>> scopeInfoMap) {
    for (Environment environment : environments) {
      String envId = environment.getIdentifier();
      if (envId == null) {
        continue;
      }
      ScopeInfo scopeInfo = scopeInfoMap != null
          ? scopeInfoMap.getOrDefault(environment.getParentUniqueId(), Optional.empty()).orElse(null)
          : null;
      boolean useScopeInfo = scopeInfo != null;

      IdentifierRef environmentRef =
          IdentifierRef.builder()
              .accountIdentifier(environment.getAccountIdentifier())
              .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : environment.getOrgIdentifier())
              .projectIdentifier(useScopeInfo ? scopeInfo.getProjectIdentifier() : environment.getProjectIdentifier())
              .identifier(environment.getIdentifier())
              .scope(getEntityScope(useScopeInfo ? scopeInfo.getOrgIdentifier() : environment.getOrgIdentifier(),
                  useScopeInfo ? scopeInfo.getProjectIdentifier() : environment.getProjectIdentifier()))
              .build();
      identifierRefToEnvMap.put(environmentRef, environment);
    }
  }

  public Map<IdentifierRef, List<ArtifactDeploymentDetail>> constructEnvironmentRefToArtifactDeploymentListMap(
      String accountIdentifier, List<ArtifactDeploymentDetailModel> artifactDeploymentDetails,
      Map<IdentifierRef, Environment> identifierRefToEnvMap) {
    Map<IdentifierRef, List<ArtifactDeploymentDetail>> identifierRefToArtifactMap = new HashMap<>();

    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envScopedId = artifactDeploymentDetail.getEnvIdentifier();

      IdentifierRef environmentRef = getIdentifierRef(envScopedId, accountIdentifier,
          artifactDeploymentDetail.getOrgIdentifier(), artifactDeploymentDetail.getProjectIdentifier());
      // environment may be missing from the map if it was deleted or its scope could not be resolved
      Environment environment = identifierRefToEnvMap.get(environmentRef);
      String envName = environment != null && environment.getName() != null ? environment.getName() : "";

      if (identifierRefToArtifactMap.containsKey(environmentRef)) {
        identifierRefToArtifactMap.get(environmentRef)
            .add(ArtifactDeploymentDetail.builder()
                     .artifact(artifactDeploymentDetail.getDisplayName())
                     .chartVersion(artifactDeploymentDetail.getChartVersion())
                     .envName(envName)
                     .envId(environmentRef.buildScopedIdentifier())
                     .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                     .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
                     .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
                     .orgIdentifier(artifactDeploymentDetail.getOrgIdentifier())
                     .projectIdentifier(artifactDeploymentDetail.getProjectIdentifier())
                     .artifactLink(artifactDeploymentDetail.getArtifactLink())
                     .build());
      } else {
        identifierRefToArtifactMap.put(environmentRef,
            new ArrayList<>(
                Arrays.asList(ArtifactDeploymentDetail.builder()
                                  .artifact(artifactDeploymentDetail.getDisplayName())
                                  .chartVersion(artifactDeploymentDetail.getChartVersion())
                                  .artifact(artifactDeploymentDetail.getDisplayName())
                                  .envName(envName)
                                  .envId(environmentRef.buildScopedIdentifier())
                                  .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                                  .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
                                  .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
                                  .orgIdentifier(artifactDeploymentDetail.getOrgIdentifier())
                                  .projectIdentifier(artifactDeploymentDetail.getProjectIdentifier())
                                  .artifactLink(artifactDeploymentDetail.getArtifactLink())
                                  .build())));
      }
    }
    return identifierRefToArtifactMap;
  }

  public Set<IdentifierRef> constructEnvIdentifierRefList(
      String accountIdentifier, List<ArtifactDeploymentDetailModel> artifactDeploymentDetails) {
    Set<IdentifierRef> envsWithScopeSet = new HashSet<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envIdWithScope = artifactDeploymentDetail.getEnvIdentifier();
      IdentifierRef environments = getIdentifierRef(envIdWithScope, accountIdentifier,
          artifactDeploymentDetail.getOrgIdentifier(), artifactDeploymentDetail.getProjectIdentifier());

      envsWithScopeSet.add(environments);
    }
    return envsWithScopeSet;
  }

  public EnvironmentGroupInstanceDetails getEnvironmentInstanceDetailsFromMap(
      Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailsMap, Map<String, Integer> envToCountMap,
      Map<String, String> envIdToEnvNameMap, Map<String, EnvironmentType> envIdToEnvTypeMap,
      List<EnvironmentGroupEntity> environmentGroupEntities,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO,
      Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap,
      List<String> pipelineExecutionIdsWhereRollbackOccurred, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    List<EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList = new ArrayList<>();

    Set<String> envIds = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Integer totalCount = 0;
        int deploymentsWithoutArtifact = 0;
        Set<String> artifacts = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          envId = convertIdToRef(useScopeInfo ? scopeInfo.getAccountIdentifier() : envGroupEntity.getAccountId(),
              useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier(),
              useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier(), envId);

          ArtifactDeploymentDetail artifactDeploymentDetail = artifactDeploymentDetailsMap.get(envId);
          final EnvironmentType envType = envIdToEnvTypeMap.get(envId);
          if (envType == null) {
            continue;
          }

          envIds.add(envId);
          final Integer count = envToCountMap.get(envId);
          if (count != null) {
            totalCount += count;
          }
          if (envType != null) {
            envTypes.add(envType);
          }
          if (artifactDeploymentDetail != null && isNotEmpty(artifactDeploymentDetail.getArtifact())) {
            artifacts.add(artifactDeploymentDetail.getArtifact());
          } else {
            deploymentsWithoutArtifact++;
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail =
                  ArtifactDeploymentDetail.builder().envName(envIdToEnvNameMap.get(envId)).envId(envId).build();
            }
          }
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }
        if (totalCount > 0) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          boolean isValid = false;
          for (EnvironmentType environmentType : envTypes) {
            if (environmentFilterPropertiesDTO == null
                || environmentFilterPropertiesDTO.getEnvironmentTypes().contains(environmentType)) {
              isValid = true;
            }
          }
          if (isValid) {
            environmentGroupInstanceDetailList.add(
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .count(totalCount)
                    .isDrift((artifacts.size() > 1) || (artifacts.size() == 1 && deploymentsWithoutArtifact > 0))
                    .isRevert(isNotEmpty(artifactDeploymentDetailList)
                        && isNotEmpty(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap.containsKey(
                            artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap
                               .get(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                               .isRevertExecution())
                    .isRollback(isNotEmpty(artifactDeploymentDetailList)
                        && isNotEmpty(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap.containsKey(
                            artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionIdsWhereRollbackOccurred.contains(
                            pipelineExecutionDetailsMap
                                .get(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                                .getPipelineExecutionId()))
                    .build());
          }
        }
      }
    }

    for (Map.Entry<String, String> entry : envIdToEnvNameMap.entrySet()) {
      final String envId = entry.getKey();
      if (!envIds.contains(envId)) {
        final EnvironmentType envType = envIdToEnvTypeMap.get(envId);
        if (envType == null) {
          continue;
        }
        final String envName = envIdToEnvNameMap.get(envId);
        final Integer count = envToCountMap.get(envId);
        final ArtifactDeploymentDetail artifactDeploymentDetail = artifactDeploymentDetailsMap.get(envId);
        if (artifactDeploymentDetail == null) {
          continue;
        }
        if (environmentFilterPropertiesDTO != null
            && !environmentFilterPropertiesDTO.getEnvironmentTypes().contains(envType)) {
          continue;
        }
        environmentGroupInstanceDetailList.add(
            EnvironmentGroupInstanceDetail.builder()
                .name(envName)
                .id(envId)
                .environmentTypes(Collections.singletonList(envType))
                .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                .isEnvGroup(false)
                .count(count)
                .isDrift(false)
                .isRevert(isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.containsKey(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.get(artifactDeploymentDetail.getLastPipelineExecutionId())
                           .isRevertExecution())
                .isRollback(isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.containsKey(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionIdsWhereRollbackOccurred.contains(
                        pipelineExecutionDetailsMap.get(artifactDeploymentDetail.getLastPipelineExecutionId())
                            .getPipelineExecutionId()))
                .build());
      }
    }

    DashboardServiceHelper.sortEnvironmentGroupInstanceDetailList(environmentGroupInstanceDetailList);

    return EnvironmentGroupInstanceDetails.builder()
        .environmentGroupInstanceDetails(environmentGroupInstanceDetailList)
        .build();
  }

  public EnvironmentGroupInstanceDetails getEnvironmentInstanceDetailsFromMap(
      Map<IdentifierRef, ArtifactDeploymentDetail> artifactDeploymentDetailsMap,
      Map<IdentifierRef, Integer> envToCountMap, Map<IdentifierRef, Environment> identifierRefToEnvMap,
      List<EnvironmentGroupEntity> environmentGroupEntities,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO,
      Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap,
      List<String> pipelineExecutionIdsWhereRollbackOccurred, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    List<EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList = new ArrayList<>();

    Set<IdentifierRef> envRefs = new HashSet<>();
    if (environmentGroupEntities != null) {
      for (EnvironmentGroupEntity envGroupEntity : environmentGroupEntities) {
        List<ArtifactDeploymentDetail> artifactDeploymentDetailList = new ArrayList<>();
        Set<EnvironmentType> envTypes = new HashSet<>();
        Integer totalCount = 0;
        int deploymentsWithoutArtifact = 0;
        Set<String> artifacts = new HashSet<>();
        if (isEmpty(envGroupEntity.getEnvIdentifiers())) {
          continue;
        }
        for (String envId : envGroupEntity.getEnvIdentifiers()) {
          IdentifierRef environmentRef =
              IdentifierRef.builder()
                  .accountIdentifier(
                      useScopeInfo ? scopeInfo.getAccountIdentifier() : envGroupEntity.getAccountIdentifier())
                  .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier())
                  .projectIdentifier(
                      useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier())
                  .identifier(envId)
                  .scope(getEntityScope(useScopeInfo ? scopeInfo.getOrgIdentifier() : envGroupEntity.getOrgIdentifier(),
                      useScopeInfo ? scopeInfo.getProjectIdentifier() : envGroupEntity.getProjectIdentifier()))
                  .build();
          ArtifactDeploymentDetail artifactDeploymentDetail = artifactDeploymentDetailsMap.get(environmentRef);
          final EnvironmentType envType = identifierRefToEnvMap.get(environmentRef).getType();
          if (envType == null) {
            continue;
          }

          envRefs.add(environmentRef);
          final Integer count = envToCountMap.get(environmentRef);
          if (count != null) {
            totalCount += count;
          }
          if (envType != null) {
            envTypes.add(envType);
          }
          if (artifactDeploymentDetail != null && isNotEmpty(artifactDeploymentDetail.getArtifact())) {
            artifacts.add(artifactDeploymentDetail.getArtifact());
          } else {
            deploymentsWithoutArtifact++;
            if (artifactDeploymentDetail == null) {
              artifactDeploymentDetail = ArtifactDeploymentDetail.builder()
                                             .envName(identifierRefToEnvMap.get(environmentRef).getName())
                                             .envId(environmentRef.buildScopedIdentifier())
                                             .build();
            }
          }
          artifactDeploymentDetailList.add(artifactDeploymentDetail);
        }
        if (totalCount > 0) {
          DashboardServiceHelper.sortArtifactDeploymentDetailList(artifactDeploymentDetailList);
          boolean isValid = false;
          for (EnvironmentType environmentType : envTypes) {
            if (environmentFilterPropertiesDTO == null
                || environmentFilterPropertiesDTO.getEnvironmentTypes().contains(environmentType)) {
              isValid = true;
            }
          }
          if (isValid) {
            environmentGroupInstanceDetailList.add(
                EnvironmentGroupInstanceDetail.builder()
                    .name(envGroupEntity.getName())
                    .id(envGroupEntity.getIdentifier())
                    .environmentTypes(new ArrayList<>(envTypes))
                    .artifactDeploymentDetails(artifactDeploymentDetailList)
                    .isEnvGroup(true)
                    .count(totalCount)
                    .isDrift((artifacts.size() > 1) || (artifacts.size() == 1 && deploymentsWithoutArtifact > 0))
                    .isRevert(isNotEmpty(artifactDeploymentDetailList)
                        && isNotEmpty(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap.containsKey(
                            artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap
                               .get(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                               .isRevertExecution())
                    .isRollback(isNotEmpty(artifactDeploymentDetailList)
                        && isNotEmpty(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionDetailsMap.containsKey(
                            artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                        && pipelineExecutionIdsWhereRollbackOccurred.contains(
                            pipelineExecutionDetailsMap
                                .get(artifactDeploymentDetailList.get(0).getLastPipelineExecutionId())
                                .getPipelineExecutionId()))
                    .build());
          }
        }
      }
    }

    for (Map.Entry<IdentifierRef, Environment> entry : identifierRefToEnvMap.entrySet()) {
      if (!envRefs.contains(entry.getKey())) {
        final EnvironmentType envType = identifierRefToEnvMap.get(entry.getKey()).getType();
        if (envType == null) {
          continue;
        }
        final String envName = identifierRefToEnvMap.get(entry.getKey()).getName();
        final Integer count = envToCountMap.get(entry.getKey());
        final ArtifactDeploymentDetail artifactDeploymentDetail = artifactDeploymentDetailsMap.get(entry.getKey());
        if (artifactDeploymentDetail == null) {
          continue;
        }
        if (environmentFilterPropertiesDTO != null
            && !environmentFilterPropertiesDTO.getEnvironmentTypes().contains(envType)) {
          continue;
        }
        environmentGroupInstanceDetailList.add(
            EnvironmentGroupInstanceDetail.builder()
                .name(envName)
                .id(entry.getKey().buildScopedIdentifier())
                .environmentTypes(Collections.singletonList(envType))
                .artifactDeploymentDetails(Collections.singletonList(artifactDeploymentDetail))
                .isEnvGroup(false)
                .count(count)
                .isDrift(false)
                .isRevert(isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.containsKey(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.get(artifactDeploymentDetail.getLastPipelineExecutionId())
                           .isRevertExecution())
                .isRollback(isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionDetailsMap.containsKey(artifactDeploymentDetail.getLastPipelineExecutionId())
                    && pipelineExecutionIdsWhereRollbackOccurred.contains(
                        pipelineExecutionDetailsMap.get(artifactDeploymentDetail.getLastPipelineExecutionId())
                            .getPipelineExecutionId()))
                .orgIdentifier(entry.getKey().getOrgIdentifier())
                .projectIdentifier(entry.getKey().getProjectIdentifier())
                .build());
      }
    }

    DashboardServiceHelper.sortEnvironmentGroupInstanceDetailList(environmentGroupInstanceDetailList);

    return EnvironmentGroupInstanceDetails.builder()
        .environmentGroupInstanceDetails(environmentGroupInstanceDetailList)
        .build();
  }

  public void constructEnvironmentCountMap(List<EnvironmentInstanceCountModel> environmentInstanceCounts,
      Map<String, Integer> envToCountMap, Set<String> envIds) {
    for (EnvironmentInstanceCountModel environmentInstanceCountModel : environmentInstanceCounts) {
      final String envId = environmentInstanceCountModel.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      envToCountMap.put(envId, environmentInstanceCountModel.getCount());
      envIds.add(envId);
    }
  }

  public void constructEnvironmentCountMapV2(String accountId,
      List<EnvironmentInstanceCountModel> environmentInstanceCounts, Map<IdentifierRef, Integer> envToCountMap,
      Map<String, Optional<ScopeInfo>> scopeInfoMap) {
    for (EnvironmentInstanceCountModel environmentInstanceCountModel : environmentInstanceCounts) {
      final String envId = environmentInstanceCountModel.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      // orgIdentifier/projectIdentifier on the model are not populated by the scope-based count aggregation;
      // resolve the real scope via parentUniqueId instead.
      ScopeInfo scopeInfo = scopeInfoMap != null
          ? scopeInfoMap.getOrDefault(environmentInstanceCountModel.getParentUniqueId(), Optional.empty()).orElse(null)
          : null;
      IdentifierRef identifierRef = getIdentifierRef(envId, accountId,
          scopeInfo != null ? scopeInfo.getOrgIdentifier() : environmentInstanceCountModel.getOrgIdentifier(),
          scopeInfo != null ? scopeInfo.getProjectIdentifier() : environmentInstanceCountModel.getProjectIdentifier());
      envToCountMap.put(identifierRef, environmentInstanceCountModel.getCount());
    }
  }

  public Map<String, ArtifactDeploymentDetail> constructEnvironmentToArtifactDeploymentMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails, Map<String, String> envIdToEnvNameMap) {
    Map<String, ArtifactDeploymentDetail> map = new HashMap<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      map.putIfAbsent(envId,
          ArtifactDeploymentDetail.builder()
              .artifact(artifactDeploymentDetail.getDisplayName())
              .chartVersion(artifactDeploymentDetail.getChartVersion())
              .envName(envIdToEnvNameMap.getOrDefault(envId, ""))
              .envId(envId)
              .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
              .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
              .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
              .artifactLink(artifactDeploymentDetail.getArtifactLink())
              .build());
    }
    return map;
  }

  public Map<IdentifierRef, ArtifactDeploymentDetail> constructEnvironmentToArtifactDeploymentMapV2(
      String accountIdentifier, List<ArtifactDeploymentDetailModel> artifactDeploymentDetails,
      Map<IdentifierRef, Environment> identifierRefToEnvMap) {
    Map<IdentifierRef, ArtifactDeploymentDetail> map = new HashMap<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      IdentifierRef environmentRef = getIdentifierRef(envId, accountIdentifier,
          artifactDeploymentDetail.getOrgIdentifier(), artifactDeploymentDetail.getProjectIdentifier());
      if (envId == null) {
        continue;
      }
      map.putIfAbsent(environmentRef,
          ArtifactDeploymentDetail.builder()
              .artifact(artifactDeploymentDetail.getDisplayName())
              .chartVersion(artifactDeploymentDetail.getChartVersion())
              .envName(identifierRefToEnvMap.get(environmentRef) == null
                      ? ""
                      : identifierRefToEnvMap.get(environmentRef).getName())
              .envId(environmentRef.buildScopedIdentifier())
              .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
              .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
              .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
              .artifactLink(artifactDeploymentDetail.getArtifactLink())
              .build());
    }
    return map;
  }

  public Map<String, List<ArtifactDeploymentDetail>> constructEnvironmentToArtifactDeploymentListMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails, Map<String, String> envIdToEnvNameMap) {
    Map<String, List<ArtifactDeploymentDetail>> map = new HashMap<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      final String envId = artifactDeploymentDetail.getEnvIdentifier();
      if (envId == null) {
        continue;
      }
      if (map.containsKey(envId)) {
        map.get(envId).add(ArtifactDeploymentDetail.builder()
                               .artifact(artifactDeploymentDetail.getDisplayName())
                               .chartVersion(artifactDeploymentDetail.getChartVersion())
                               .envName(envIdToEnvNameMap.getOrDefault(envId, ""))
                               .envId(envId)
                               .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                               .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
                               .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
                               .orgIdentifier(artifactDeploymentDetail.getOrgIdentifier())
                               .projectIdentifier(artifactDeploymentDetail.getProjectIdentifier())
                               .artifactLink(artifactDeploymentDetail.getArtifactLink())
                               .build());
      } else {
        map.put(envId,
            new ArrayList<>(
                Arrays.asList(ArtifactDeploymentDetail.builder()
                                  .artifact(artifactDeploymentDetail.getDisplayName())
                                  .chartVersion(artifactDeploymentDetail.getChartVersion())
                                  .envName(envIdToEnvNameMap.getOrDefault(envId, ""))
                                  .envId(envId)
                                  .lastDeployedAt(artifactDeploymentDetail.getLastDeployedAt())
                                  .lastPipelineExecutionId(artifactDeploymentDetail.getLastPipelineExecutionId())
                                  .pipelineId(artifactDeploymentDetail.getLastPipelineExecutionName())
                                  .orgIdentifier(artifactDeploymentDetail.getOrgIdentifier())
                                  .projectIdentifier(artifactDeploymentDetail.getProjectIdentifier())
                                  .artifactLink(artifactDeploymentDetail.getArtifactLink())
                                  .build())));
      }
    }
    return map;
  }

  public Map<String, String> constructArtifactToArtifactLinkMap(
      List<ArtifactDeploymentDetailModel> artifactDeploymentDetails) {
    Map<String, String> map = new HashMap<>();
    for (ArtifactDeploymentDetailModel artifactDeploymentDetail : artifactDeploymentDetails) {
      String artifact = artifactDeploymentDetail.getDisplayName();
      if (artifact == null) {
        continue;
      }
      map.putIfAbsent(artifact, artifactDeploymentDetail.getArtifactLink());
    }
    return map;
  }

  public String buildOpenTaskQuery(String serviceId, long startInterval, ScopeInfo scopeInfo) {
    return String.format("select DISTINCT ON(pipeline_execution_summary_cd_id) pipeline_execution_summary_cd_id, "
            + "execution_failure_details from service_infra_info where parent_unique_id = '%s' "
            + "and service_id = '%s' and service_startts > %s order "
            + "by pipeline_execution_summary_cd_id, service_endts DESC",
        escapeSql(scopeInfo.getUniqueId()), escapeSql(serviceId), startInterval);
  }

  public Query buildOpenTaskQueryViaJooq(
      String serviceId, long startInterval, @NotNull Configuration configuration, ScopeInfo scopeInfo) {
    return DSL.using(configuration)
        .selectDistinct(
            SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.EXECUTION_FAILURE_DETAILS)
        .distinctOn(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()))
        .and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceId))
        .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterThan(startInterval))
        .orderBy(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.SERVICE_ENDTS.desc());
  }

  public String buildRollbackDurationQuery(List<String> pipelineExecutionSummaryCdId) {
    String statement = "select pipeline_execution_summary_cd_id from service_infra_info where rollback_duration > 0 "
        + "and pipeline_execution_summary_cd_id in (''";
    for (String id : pipelineExecutionSummaryCdId) {
      statement += String.format(",'%s'", escapeSql(id));
    }
    statement += ")";
    return statement;
  }

  public Query buildRollbackDurationQueryViaJooq(
      List<String> pipelineExecutionSummaryCdId, @NotNull Configuration configuration) {
    return DSL.using(configuration)
        .select(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID)
        .from(SERVICE_INFRA_INFO)
        .where(SERVICE_INFRA_INFO.ROLLBACK_DURATION.greaterThan(0L))
        .and(SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID.in(pipelineExecutionSummaryCdId));
  }

  public String queryToFetchExecutionIdAndArtifactDetails(String serviceRef, long startInterval, long endInterval,
      String artifactPath, String artifactVersion, String artifact, List<String> parentUniqueIds) {
    String query =
        String.format("select %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s from %s where %s and %s is not null and "
                + "%s >= %s and %s <= %s",
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, SERVICE_NAME, ARTIFACT_DISPLAY_NAME, ARTIFACT_IMAGE, TAG,
            PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_STARTTS, PARENT_UNIQUE_ID, tableNameServiceAndInfra,
            getScopeEqualityCriteria(parentUniqueIds), SERVICE_ID, SERVICE_STARTTS, startInterval, SERVICE_STARTTS,
            endInterval);

    if (serviceRef != null) {
      query = query + String.format(" and %s = '%s'", SERVICE_ID, escapeSql(serviceRef));
    }
    if (artifact != null) {
      query = query + String.format(" and %s = '%s'", ARTIFACT_DISPLAY_NAME, escapeSql(artifact));
    }
    if (artifactPath != null) {
      query = query + String.format(" and %s = '%s'", ARTIFACT_IMAGE, escapeSql(artifactPath));
    }
    if (artifactVersion != null) {
      query = query + String.format(" and %s = '%s'", TAG, escapeSql(artifactVersion));
    }
    return query;
  }

  public Query queryToFetchExecutionIdAndArtifactDetailsViaJooq(String serviceRef, long startInterval, long endInterval,
      String artifactPath, String artifactVersion, String artifact, @NotNull Configuration configuration,
      List<String> parentUniqueIds) {
    SelectConditionStep<Record11<String, String, String, String, String, String, String, String, String, Long, String>>
        query = DSL.using(configuration)
                    .select(SERVICE_INFRA_INFO.ACCOUNTID, SERVICE_INFRA_INFO.ORGIDENTIFIER,
                        SERVICE_INFRA_INFO.PROJECTIDENTIFIER, SERVICE_INFRA_INFO.SERVICE_ID,
                        SERVICE_INFRA_INFO.SERVICE_NAME, SERVICE_INFRA_INFO.ARTIFACT_DISPLAY_NAME,
                        SERVICE_INFRA_INFO.ARTIFACT_IMAGE, SERVICE_INFRA_INFO.TAG,
                        SERVICE_INFRA_INFO.PIPELINE_EXECUTION_SUMMARY_CD_ID, SERVICE_INFRA_INFO.SERVICE_STARTTS,
                        SERVICE_INFRA_INFO.PARENT_UNIQUE_ID)
                    .from(SERVICE_INFRA_INFO)
                    .where(SERVICE_INFRA_INFO.SERVICE_ID.isNotNull())
                    .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.greaterOrEqual(startInterval)
                             .and(SERVICE_INFRA_INFO.SERVICE_STARTTS.lessOrEqual(endInterval)));
    query.and(SERVICE_INFRA_INFO.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (serviceRef != null) {
      query.and(SERVICE_INFRA_INFO.SERVICE_ID.eq(serviceRef));
    }
    if (artifact != null) {
      query.and(SERVICE_INFRA_INFO.ARTIFACT_DISPLAY_NAME.eq(artifact));
    }
    if (artifactPath != null) {
      query.and(SERVICE_INFRA_INFO.ARTIFACT_IMAGE.eq(artifactPath));
    }
    if (artifactVersion != null) {
      query.and(SERVICE_INFRA_INFO.TAG.eq(artifactVersion));
    }
    return query;
  }

  public String queryToFetchStatusOfExecution(String status, List<String> parentUniqueIds) {
    String query = String.format("select %s, %s from %s where %s and %s = any (?)", ID, STATUS, tableNameCD,
        getScopeEqualityCriteria(parentUniqueIds), ID);
    if (status != null) {
      query = query + String.format(" and %s = '%s'", STATUS, escapeSql(status));
    }
    return query;
  }

  public Query queryToFetchStatusOfExecutionViaJooq(
      String status, List<String> ids, @NotNull Configuration configuration, List<String> parentUniqueIds) {
    SelectConditionStep<Record2<String, String>> query =
        DSL.using(configuration)
            .select(PIPELINE_EXECUTION_SUMMARY_CD.ID, PIPELINE_EXECUTION_SUMMARY_CD.STATUS)
            .from(PIPELINE_EXECUTION_SUMMARY_CD)
            .where(PIPELINE_EXECUTION_SUMMARY_CD.ID.in(ids));
    query.and(PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.in(parentUniqueIds));

    if (status != null) {
      query.and(PIPELINE_EXECUTION_SUMMARY_CD.STATUS.eq(status));
    }
    return query;
  }

  public String getArtifactPathFromDisplayName(String displayName) {
    if (isNotEmpty(displayName)) {
      String[] res = displayName.split(":");
      int count = res.length;
      if (count > 1) {
        return res[0];
      }
    }
    return null;
  }

  public String getTagFromDisplayName(String displayName) {
    if (isNotEmpty(displayName)) {
      String[] res = displayName.split(":");
      int count = res.length;
      if (count > 1) {
        return res[1];
      } else if (count == 1) {
        return res[0];
      }
    }
    return displayName;
  }

  public String getDisplayNameFromArtifact(String artifactPath, String buildId) {
    if (isEmpty(artifactPath)) {
      return buildId;
    }
    return String.format("%s:%s", artifactPath, buildId);
  }

  public String getScopeEqualityCriteria(List<String> parentUniqueIds) {
    String quotedIds = parentUniqueIds.stream().map(id -> "'" + escapeSql(id) + "'").collect(Collectors.joining(","));
    return String.format("%s in (%s)", PARENT_UNIQUE_ID, quotedIds);
  }

  public PipelineExecutionCountInfo getPipelineExecutionCountInfoHelper(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList, Map<String, String> statusMap) {
    sortServiceArtifactExecutionDetail(serviceArtifactExecutionDetailList);
    Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap = new HashMap<>();
    Map<String, Map<String, Set<String>>> artifactExecutionMap = new HashMap<>();
    Map<String, String> serviceRefToNameMap = new HashMap<>();
    Map<String, Set<String>> serviceExecutionMap = new HashMap<>();

    constructServiceToExecutionIdListMap(serviceArtifactExecutionDetailList, serviceArtifactExecutionDetailMap,
        artifactExecutionMap, serviceRefToNameMap, serviceExecutionMap);

    return getCountGroupedOnServiceList(
        artifactExecutionMap, statusMap, serviceArtifactExecutionDetailMap, serviceRefToNameMap, serviceExecutionMap);
  }

  private void sortServiceArtifactExecutionDetail(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList) {
    Collections.sort(serviceArtifactExecutionDetailList, new Comparator<ServiceArtifactExecutionDetail>() {
      public int compare(ServiceArtifactExecutionDetail o1, ServiceArtifactExecutionDetail o2) {
        Long o1Time = o1.getServiceStartTime();
        Long o2Time = o2.getServiceStartTime();
        if (o2Time == null && o1Time == null) {
          return 0;
        } else if (o2Time == null) {
          return -1;
        } else if (o1Time == null) {
          return 1;
        } else {
          return Long.compare(o2Time, o1Time);
        }
      }
    });
  }

  private PipelineExecutionCountInfo getCountGroupedOnServiceList(
      Map<String, Map<String, Set<String>>> artifactExecutionMap, Map<String, String> statusMap,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap,
      Map<String, String> serviceRefToNameMap, Map<String, Set<String>> serviceExecutionMap) {
    List<PipelineExecutionCountInfo.CountGroupedOnService> countGroupedOnServiceList = new ArrayList<>();
    for (Map.Entry<String, Map<String, Set<String>>> entry : artifactExecutionMap.entrySet()) {
      String serviceRef = entry.getKey();
      String serviceName = serviceRefToNameMap.get(serviceRef);
      List<PipelineExecutionCountInfo.CountGroupedOnArtifact> countGroupedOnArtifactList =
          getCountGroupedOnArtifactList(entry.getValue(), statusMap, serviceArtifactExecutionDetailMap, serviceRef);
      if (isEmpty(countGroupedOnArtifactList)) {
        continue;
      }
      Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> countInfo =
          getCountGroupedOnStatusList(serviceExecutionMap.get(serviceRef), statusMap);
      PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService =
          PipelineExecutionCountInfo.CountGroupedOnService.builder()
              .serviceReference(serviceRef)
              .serviceName(serviceName)
              .count(countInfo.getKey())
              .executionCountGroupedOnStatusList(countInfo.getValue())
              .executionCountGroupedOnArtifactList(countGroupedOnArtifactList)
              .build();
      countGroupedOnServiceList.add(countGroupedOnService);
    }
    return PipelineExecutionCountInfo.builder().executionCountGroupedOnServiceList(countGroupedOnServiceList).build();
  }

  private List<PipelineExecutionCountInfo.CountGroupedOnArtifact> getCountGroupedOnArtifactList(
      Map<String, Set<String>> artifactToExecutionIdMap, Map<String, String> statusMap,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap, String serviceRef) {
    List<PipelineExecutionCountInfo.CountGroupedOnArtifact> countGroupedOnArtifactList = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry1 : artifactToExecutionIdMap.entrySet()) {
      String artifact = entry1.getKey();
      ServiceArtifactExecutionDetail serviceArtifactExecutionDetail =
          serviceArtifactExecutionDetailMap.get(serviceRef).get(artifact);
      Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> countInfo =
          getCountGroupedOnStatusList(entry1.getValue(), statusMap);
      List<PipelineExecutionCountInfo.CountGroupedOnStatus> countGroupedOnStatusList = countInfo.getValue();
      if (isEmpty(countGroupedOnStatusList)) {
        continue;
      }
      PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact =
          PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
              .artifactPath(serviceArtifactExecutionDetail.getArtifactPath())
              .artifactVersion(serviceArtifactExecutionDetail.getArtifactTag())
              .artifact(serviceArtifactExecutionDetail.getArtifactDisplayName())
              .count(countInfo.getKey())
              .executionCountGroupedOnStatusList(countGroupedOnStatusList)
              .build();
      countGroupedOnArtifactList.add(countGroupedOnArtifact);
    }
    return countGroupedOnArtifactList;
  }

  private Pair<Long, List<PipelineExecutionCountInfo.CountGroupedOnStatus>> getCountGroupedOnStatusList(
      Set<String> executionIdList, Map<String, String> statusMap) {
    Map<String, Long> statusCountMap = new HashMap<>();
    Long totalExecution = 0L;
    for (String executionId : executionIdList) {
      if (!statusMap.containsKey(executionId)) {
        continue;
      }
      totalExecution++;
      String status = statusMap.get(executionId);
      Long count = statusCountMap.get(status);
      if (count == null) {
        statusCountMap.put(status, 1L);
      } else {
        statusCountMap.put(status, count + 1);
      }
    }
    List<PipelineExecutionCountInfo.CountGroupedOnStatus> countGroupedOnStatusList = new ArrayList<>();
    for (Map.Entry<String, Long> entry : statusCountMap.entrySet()) {
      countGroupedOnStatusList.add(PipelineExecutionCountInfo.CountGroupedOnStatus.builder()
                                       .status(entry.getKey())
                                       .count(entry.getValue())
                                       .build());
    }
    return MutablePair.of(totalExecution, countGroupedOnStatusList);
  }

  private void constructServiceToExecutionIdListMap(
      List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList,
      Map<String, Map<String, ServiceArtifactExecutionDetail>> serviceArtifactExecutionDetailMap,
      Map<String, Map<String, Set<String>>> artifactExecutionMap, Map<String, String> serviceRefToNameMap,
      Map<String, Set<String>> serviceExecutionMap) {
    serviceArtifactExecutionDetailList.forEach(serviceArtifactExecutionDetail -> {
      String accountId = serviceArtifactExecutionDetail.getAccountId();
      String orgId = serviceArtifactExecutionDetail.getOrgId();
      String projectId = serviceArtifactExecutionDetail.getProjectId();
      String serviceRef = serviceArtifactExecutionDetail.getServiceRef();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgId, projectId);
      serviceRef = identifierRef.getFullyQualifiedName();
      serviceRefToNameMap.putIfAbsent(serviceRef, serviceArtifactExecutionDetail.getServiceName());
      String artifactPath = serviceArtifactExecutionDetail.getArtifactPath();
      String artifactTag = serviceArtifactExecutionDetail.getArtifactTag();
      String artifactDisplayName = serviceArtifactExecutionDetail.getArtifactDisplayName();
      String pipelineExecutionId = serviceArtifactExecutionDetail.getPipelineExecutionSummaryCDId();
      if (artifactDisplayName == null) {
        artifactDisplayName = getDisplayNameFromArtifact(artifactPath, artifactTag);
      }
      artifactExecutionMap.putIfAbsent(serviceRef, new HashMap<>());
      artifactExecutionMap.get(serviceRef).putIfAbsent(artifactDisplayName, new HashSet<>());
      artifactExecutionMap.get(serviceRef).get(artifactDisplayName).add(pipelineExecutionId);
      serviceExecutionMap.putIfAbsent(serviceRef, new HashSet<>());
      serviceExecutionMap.get(serviceRef).add(pipelineExecutionId);
      serviceArtifactExecutionDetailMap.putIfAbsent(serviceRef, new HashMap<>());
      serviceArtifactExecutionDetailMap.get(serviceRef)
          .putIfAbsent(artifactDisplayName, serviceArtifactExecutionDetail);
    });
  }

  public Long checkForDefaultEndInterval(Long endInterval) {
    if (endInterval == null) {
      // taking current time as default endInterval
      return System.currentTimeMillis();
    }
    return endInterval;
  }

  public Long checkForDefaultStartInterval(Long startInterval, Long endInterval) {
    if (startInterval == null) {
      // taking 30 days interval as default
      return endInterval - 2592000000L;
    }
    return startInterval;
  }

  public boolean validateDuration(Long startInterval, Long endInterval) {
    long duration = endInterval - startInterval;
    long durationSixMonths = 15552000000l;
    if (duration > durationSixMonths) {
      return false;
    }
    return true;
  }
}
