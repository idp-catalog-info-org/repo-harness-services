/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.helper.EcsDeploymentReleaseData;
import io.harness.entities.deploymentinfo.EcsDeploymentInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.DeploymentInfoDetails;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.utils.AnyUtils;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.EcsDeploymentRelease;
import io.harness.perpetualtask.instancesync.EcsInstanceSyncPerpetualTaskParams;
import io.harness.serializer.KryoSerializer;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.ecs.EcsInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import software.wings.utils.EcsConvention;

import com.google.protobuf.Any;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class EcsPerpetualTaskDeDuplicationRunner
    extends AbstractPerpetualTaskDeDuplicationRunner<EcsDeploymentReleaseData> {
  private final MongoTemplate mongoTemplate;
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private final EcsInstanceSyncPerpetualTaskHandler ecsInstanceSyncPerpetualTaskHandler;
  private final KryoSerializer kryoSerializer;
  private final List<String> accountIds;

  public EcsPerpetualTaskDeDuplicationRunner(MongoTemplate mongoTemplate,
      DelegateServiceGrpcClient delegateServiceGrpcClient,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService,
      EcsInstanceSyncPerpetualTaskHandler ecsInstanceSyncPerpetualTaskHandler, KryoSerializer kryoSerializer,
      List<String> accountIds) {
    this.mongoTemplate = mongoTemplate;
    this.delegateServiceGrpcClient = delegateServiceGrpcClient;
    this.instanceSyncPerpetualTaskInfoService = instanceSyncPerpetualTaskInfoService;
    this.ecsInstanceSyncPerpetualTaskHandler = ecsInstanceSyncPerpetualTaskHandler;
    this.kryoSerializer = kryoSerializer;
    this.accountIds = accountIds;
  }

  public void run() {
    deDuplicatePerpetualTasks(
        mongoTemplate, delegateServiceGrpcClient, instanceSyncPerpetualTaskInfoService, accountIds);
  }

  @Override
  public boolean containsMatchingDeploymentInfo(InstanceSyncPerpetualTaskInfo perpetualTaskInfo) {
    try {
      List<DeploymentInfoDetails> deploymentInfoDetailsList = perpetualTaskInfo.getDeploymentInfoDetailsList();
      if (isEmpty(deploymentInfoDetailsList)) {
        return false;
      }
      return deploymentInfoDetailsList.stream()
          .map(DeploymentInfoDetails::getDeploymentInfo)
          .filter(Objects::nonNull)
          .anyMatch(EcsDeploymentInfo.class ::isInstance);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public List<DeploymentInfoDetails> deDuplicateInstanceSyncPTInfoDeploymentList(
      InstanceSyncPerpetualTaskInfo perpetualTaskInfo) {
    return deduplicateDeploymentInfos(perpetualTaskInfo.getDeploymentInfoDetailsList());
  }

  @Override
  public boolean isDeDuplicationNeeded(Any taskParams) {
    try {
      EcsInstanceSyncPerpetualTaskParams ecsInstanceSyncPerpetualTaskParams =
          AnyUtils.unpack(taskParams, EcsInstanceSyncPerpetualTaskParams.class);
      List<EcsDeploymentReleaseData> ecsDeploymentReleaseData =
          getEcsDeploymentReleaseData(ecsInstanceSyncPerpetualTaskParams);
      List<EcsDeploymentReleaseData> deDuplicatedDeploymentReleaseData =
          deduplicateReleaseData(ecsDeploymentReleaseData);
      return ecsDeploymentReleaseData.size() != deDuplicatedDeploymentReleaseData.size()
          && deDuplicatedDeploymentReleaseData.size() > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Any createUpdatedPerpetualTaskPack(
      String accountId, List<EcsDeploymentReleaseData> distinctDeploymentReleaseData) {
    return ecsInstanceSyncPerpetualTaskHandler.packEcsInstanceSyncPerpetualTaskParams(
        accountId, distinctDeploymentReleaseData);
  }

  @Override
  public List<EcsDeploymentReleaseData> getDistinctDeploymentReleases(Any taskParams) {
    EcsInstanceSyncPerpetualTaskParams ecsInstanceSyncPerpetualTaskParams =
        AnyUtils.unpack(taskParams, EcsInstanceSyncPerpetualTaskParams.class);
    List<EcsDeploymentReleaseData> ecsDeploymentReleaseData =
        getEcsDeploymentReleaseData(ecsInstanceSyncPerpetualTaskParams);
    return deduplicateReleaseData(ecsDeploymentReleaseData);
  }

  @Override
  public List<ExecutionCapability> getExecutionCapabilities(List<EcsDeploymentReleaseData> deploymentReleases) {
    return ecsInstanceSyncPerpetualTaskHandler.getExecutionCapabilities(deploymentReleases);
  }

  @Override
  public PerpetualTaskExecutionBundle getUpdatedPerpetualTaskBundle(
      String orgId, String projectId, List<ExecutionCapability> executionCapabilities, Any perpetualTaskPack) {
    return ecsInstanceSyncPerpetualTaskHandler.createPerpetualTaskExecutionBundle(
        perpetualTaskPack, executionCapabilities, orgId, projectId);
  }

  private List<EcsDeploymentReleaseData> getEcsDeploymentReleaseData(EcsInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getEcsDeploymentReleaseListList()
        .stream()
        .map(this::toEcsDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private EcsDeploymentReleaseData toEcsDeploymentReleaseData(EcsDeploymentRelease ecsDeploymentRelease) {
    return EcsDeploymentReleaseData.builder()
        .ecsInfraConfig(
            (EcsInfraConfig) kryoSerializer.asObject(ecsDeploymentRelease.getEcsInfraConfig().toByteArray()))
        .serviceName(ecsDeploymentRelease.getServiceName())
        .orgId(ecsDeploymentRelease.getOrgId())
        .projectId(ecsDeploymentRelease.getProjectId())
        .build();
  }

  List<EcsDeploymentReleaseData> deduplicateReleaseData(List<EcsDeploymentReleaseData> deploymentReleases) {
    if (isEmpty(deploymentReleases)) {
      return Collections.emptyList();
    }
    return deploymentReleases.stream()
        .map(this::toEcsServiceEntity)
        .collect(Collectors.groupingBy(EcsServiceEntity::getServiceNamePrefix,
            Collectors.collectingAndThen(Collectors.toList(),
                services -> {
                  // for each service, sort by suffix integer and keep only up to 2 entries
                  services.sort(Comparator.comparingInt(EcsServiceEntity::getSuffix).reversed());
                  return services.subList(0, Math.min(services.size(), 2));
                })))
        .values()
        .stream()
        .flatMap(Collection::stream)
        .map(EcsServiceEntity::getData)
        .collect(Collectors.toList());
  }

  List<DeploymentInfoDetails> deduplicateDeploymentInfos(List<DeploymentInfoDetails> deploymentInfoDetails) {
    if (isEmpty(deploymentInfoDetails)) {
      return Collections.emptyList();
    }
    List<DeploymentInfoDetails> deduplicatedList = new ArrayList<>();
    Map<String, List<EcsDeploymentEntity>> ecsServicePrefixToServicesMap = new HashMap<>();
    for (DeploymentInfoDetails deploymentInfoDetail : deploymentInfoDetails) {
      if (!(deploymentInfoDetail.getDeploymentInfo() instanceof EcsDeploymentInfo ecsDeploymentInfo)) {
        // preserve any unrelated deployment info
        deduplicatedList.add(deploymentInfoDetail);
      } else {
        String serviceNamePrefix =
            EcsConvention.getServiceNamePrefixFromServiceName(ecsDeploymentInfo.getServiceName());
        EcsDeploymentEntity deploymentEntity =
            EcsDeploymentEntity.builder()
                .serviceNamePrefix(serviceNamePrefix)
                .suffix(EcsConvention.getRevisionFromServiceName(ecsDeploymentInfo.getServiceName()))
                .data(deploymentInfoDetail)
                .build();
        List<EcsDeploymentEntity> ecsDeploymentEntities =
            ecsServicePrefixToServicesMap.getOrDefault(serviceNamePrefix, new ArrayList<>());
        ecsDeploymentEntities.add(deploymentEntity);
        ecsServicePrefixToServicesMap.put(serviceNamePrefix, ecsDeploymentEntities);
      }
    }

    ecsServicePrefixToServicesMap.forEach(
        (key, value) -> value.sort(Comparator.comparingInt(EcsDeploymentEntity::getSuffix).reversed()));

    for (Map.Entry<String, List<EcsDeploymentEntity>> entry : ecsServicePrefixToServicesMap.entrySet()) {
      List<EcsDeploymentEntity> ecsDeploymentEntities = entry.getValue();
      ecsDeploymentEntities.subList(0, Math.min(ecsDeploymentEntities.size(), 2))
          .forEach(item -> deduplicatedList.add(item.getData()));
    }
    return deduplicatedList;
  }

  private EcsServiceEntity toEcsServiceEntity(EcsDeploymentReleaseData releaseData) {
    return EcsServiceEntity.builder()
        .data(releaseData)
        .serviceNamePrefix(EcsConvention.getServiceNamePrefixFromServiceName(releaseData.getServiceName()))
        .suffix(EcsConvention.getRevisionFromServiceName(releaseData.getServiceName()))
        .build();
  }

  @Data
  @Builder
  private static class EcsServiceEntity {
    String serviceNamePrefix;
    Integer suffix;
    EcsDeploymentReleaseData data;
  }

  @Data
  @Builder
  private static class EcsDeploymentEntity {
    String serviceNamePrefix;
    Integer suffix;
    DeploymentInfoDetails data;
  }
}
