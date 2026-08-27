/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.delegate.AccountId;
import io.harness.delegate.PerpetualTaskBundleResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.InfrastructureMapping.InfrastructureMappingNGKeys;
import io.harness.entities.instancesyncperpetualtaskinfo.DeploymentInfoDetails;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import com.google.protobuf.Any;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public abstract class AbstractPerpetualTaskDeDuplicationRunner<T> {
  private static final int BATCH_SIZE = 10000;
  public abstract boolean containsMatchingDeploymentInfo(InstanceSyncPerpetualTaskInfo perpetualTaskInfo);
  public abstract List<DeploymentInfoDetails> deDuplicateInstanceSyncPTInfoDeploymentList(
      InstanceSyncPerpetualTaskInfo perpetualTaskInfo);
  public abstract boolean isDeDuplicationNeeded(Any taskParams);

  public abstract Any createUpdatedPerpetualTaskPack(String accountId, List<T> distinctDeploymentReleases);
  public abstract List<T> getDistinctDeploymentReleases(Any taskParams);
  public abstract List<ExecutionCapability> getExecutionCapabilities(List<T> deploymentReleases);
  public abstract PerpetualTaskExecutionBundle getUpdatedPerpetualTaskBundle(
      String orgId, String projectId, List<ExecutionCapability> executionCapabilities, Any perpetualTaskPack);

  public void deDuplicatePerpetualTasks(MongoTemplate mongoTemplate,
      DelegateServiceGrpcClient delegateServiceGrpcClient,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService, List<String> accountIds) {
    for (String accountId : accountIds) {
      Query query = getQueryForAllPtInfo(accountId);
      try (Stream<InstanceSyncPerpetualTaskInfo> stream =
               mongoTemplate.stream(query, InstanceSyncPerpetualTaskInfo.class)) {
        Iterator<InstanceSyncPerpetualTaskInfo> iterator = stream.iterator();
        while (iterator.hasNext()) {
          InstanceSyncPerpetualTaskInfo perpetualTaskInfo = iterator.next();
          if (containsMatchingDeploymentInfo(perpetualTaskInfo)) {
            deDuplicateDeploymentInfoAndResetPt(mongoTemplate, delegateServiceGrpcClient,
                instanceSyncPerpetualTaskInfoService, accountId, perpetualTaskInfo);
          }
        }
      }
    }
  }

  private void deDuplicateDeploymentInfoAndResetPt(MongoTemplate mongoTemplate,
      DelegateServiceGrpcClient delegateServiceGrpcClient,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService, String accountId,
      InstanceSyncPerpetualTaskInfo perpetualTaskInfo) {
    try {
      if (isEmpty(perpetualTaskInfo.getInfrastructureMappingId())) {
        return;
      }
      InfrastructureMapping infrastructureMapping = getInfrastructureMapping(mongoTemplate, perpetualTaskInfo);
      if (infrastructureMapping == null || isEmpty(infrastructureMapping.getOrgIdentifier())
          || isEmpty(infrastructureMapping.getProjectIdentifier())) {
        return;
      }
      String orgId = infrastructureMapping.getOrgIdentifier();
      String projectId = infrastructureMapping.getProjectIdentifier();
      String perpetualTaskId = perpetualTaskInfo.getPerpetualTaskId();
      PerpetualTaskBundleResponse perpetualTaskBundle =
          delegateServiceGrpcClient.getPerpetualTaskBundle(perpetualTaskId);
      if (perpetualTaskBundle == null || isEmpty(perpetualTaskBundle.getRawBundle())) {
        return;
      }

      PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = getPerpetualTaskExecutionBundle(perpetualTaskBundle);
      if (perpetualTaskExecutionBundle == null
          || !isDeDuplicationNeeded(perpetualTaskExecutionBundle.getTaskParams())) {
        return;
      }

      // deduplicate pt info deployment info list and save updated pt info
      List<DeploymentInfoDetails> updatedDeploymentInfoDetails =
          deDuplicateInstanceSyncPTInfoDeploymentList(perpetualTaskInfo);
      if (updatedDeploymentInfoDetails.size() > 0
          && updatedDeploymentInfoDetails.size() < perpetualTaskInfo.getDeploymentInfoDetailsList().size()) {
        instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(
            perpetualTaskInfo.getId(), accountId, updatedDeploymentInfoDetails);
      }

      // deduplicate perpetual task bundle, repackage and reset perpetual task
      List<T> distinctDeploymentReleases = getDistinctDeploymentReleases(perpetualTaskExecutionBundle.getTaskParams());
      Any perpetualTaskPack = createUpdatedPerpetualTaskPack(accountId, distinctDeploymentReleases);
      List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(distinctDeploymentReleases);
      PerpetualTaskExecutionBundle updatedPtBundle =
          getUpdatedPerpetualTaskBundle(orgId, projectId, executionCapabilities, perpetualTaskPack);
      delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(accountId).build(),
          PerpetualTaskId.newBuilder().setId(perpetualTaskId).build(), updatedPtBundle);
    } catch (Exception e) {
      log.warn(
          "[AbstractPerpetualTaskDeDuplicationRunner] Failed to perform migration for account[{}] instance sync PT info[{}], containing PT[{}]",
          accountId, perpetualTaskInfo.getId(), perpetualTaskInfo.getPerpetualTaskId());
    }
  }

  private InfrastructureMapping getInfrastructureMapping(
      MongoTemplate mongoTemplate, InstanceSyncPerpetualTaskInfo perpetualTaskInfo) {
    return mongoTemplate.findOne(
        new Query(Criteria.where(InfrastructureMappingNGKeys.id).is(perpetualTaskInfo.getInfrastructureMappingId())),
        InfrastructureMapping.class);
  }

  private static PerpetualTaskExecutionBundle getPerpetualTaskExecutionBundle(
      PerpetualTaskBundleResponse perpetualTaskBundle) {
    try {
      return PerpetualTaskExecutionBundle.parseFrom(perpetualTaskBundle.getRawBundle());
    } catch (Exception e) {
      log.warn("[AbstractPerpetualTaskDeDuplicationRunner] Failed to parse PT execution from bundle from PT.");
      return null;
    }
  }

  private static Query getQueryForAllPtInfo(String accountId) {
    return new Query(Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier).is(accountId))
        .limit(NO_LIMIT)
        .cursorBatchSize(BATCH_SIZE);
  }
}
