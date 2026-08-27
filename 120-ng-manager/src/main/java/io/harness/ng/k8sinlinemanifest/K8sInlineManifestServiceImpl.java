/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.k8sinlinemanifest;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.lang.String.format;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class K8sInlineManifestServiceImpl implements K8sInlineManifestService {
  private static final String KUBERNETES_APPLY_COMMAND_NAME = "K8s Apply";
  private static final String ORG_OWNER = "%s";
  private static final String PROJECT_OWNER = "%s/%s";
  private static final long EXECUTION_TIMEOUT = 15;
  private static final int TIMEOUT_INTERVAL_IN_MINUTES = 15;
  private static final int INITIAL_HASH_MAP_CAPACITY = 2;

  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureMapper infrastructureMapper;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;

  @Override
  public String applyK8sManifest(K8sManifestRequest k8sManifestRequest, String uid, NotifyCallback notifyCallback) {
    LinkedHashMap<String, String> logAbstractions = buildLogAbstractions(k8sManifestRequest, uid);
    DelegateTaskRequestBuilder requestBuilder =
        DelegateTaskRequest.builder()
            .accountId(k8sManifestRequest.getAccountId())
            .taskParameters(getTaskParams(k8sManifestRequest.getAccountId(), k8sManifestRequest.getOrgId(),
                k8sManifestRequest.getProjectId(), k8sManifestRequest.getK8sConnectorId(),
                k8sManifestRequest.getReleaseIdentifier(), uid, k8sManifestRequest.getK8sManifest(),
                k8sManifestRequest.getInfrastructureId(), k8sManifestRequest.getEnvironmentId(),
                k8sManifestRequest.isShowDetailedDiagnosticLogs()))
            .taskType(TaskType.K8S_COMMAND_TASK_NG.name())
            .executionTimeout(Duration.ofMinutes(EXECUTION_TIMEOUT))
            .taskSetupAbstractions(buildAbstractions(k8sManifestRequest.getOrgId(), k8sManifestRequest.getProjectId()))
            .logStreamingAbstractions(logAbstractions);

    if (isNotEmpty(k8sManifestRequest.getDelegateId())) {
      requestBuilder.eligibleToExecuteDelegateIds(Collections.singletonList(k8sManifestRequest.getDelegateId()));
    }

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(requestBuilder.build(), Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, notifyCallback, taskId);
    return taskId;
  }

  @NonNull
  private static LinkedHashMap<String, String> buildLogAbstractions(K8sManifestRequest k8sManifestRequest, String uid) {
    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put("accountId", k8sManifestRequest.getAccountId());
    logAbstractions.put("uid", uid);
    return logAbstractions;
  }

  private TaskParameters getTaskParams(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String releaseIdentifier, String uid, String manifestContent, String infrastructureId,
      String environmentId, boolean showDetailedDiagnosticLogs) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(accountIdentifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build();

    LocalFileStoreDelegateConfig storeDelegateConfig =
        LocalFileStoreDelegateConfig.builder()
            .filePaths(Collections.singletonList("/template/deployment.yaml"))
            .manifestType(ManifestType.K8Manifest)
            .manifestIdentifier("K8s Inline Manifest")
            .manifestFiles(Collections.singletonList(ManifestFiles.builder()
                                                         .fileContent(manifestContent)
                                                         .fileName("deployment.yaml")
                                                         .filePath("/template/deployment.yaml")
                                                         .build()))
            .build();

    K8sInfraDelegateConfig infraDelegateConfig =
        getInfraDelegateConfig(connectorIdentifier, infrastructureId, environmentId, ngAccess);
    if (infraDelegateConfig == null) {
      throw new UnsupportedOperationException(format("Unable to get Infra configuration"));
    }

    return K8sApplyRequest.builder()
        .skipDryRun(true)
        .releaseName(releaseIdentifier + "-" + uid)
        .commandName(KUBERNETES_APPLY_COMMAND_NAME)
        .taskType(K8sTaskType.APPLY)
        .timeoutIntervalInMin(TIMEOUT_INTERVAL_IN_MINUTES)
        .k8sInfraDelegateConfig(infraDelegateConfig)
        .manifestDelegateConfig(K8sManifestDelegateConfig.builder().storeDelegateConfig(storeDelegateConfig).build())
        .accountId(accountIdentifier)
        .deprecateFabric8Enabled(true)
        .filePaths(Collections.singletonList("template/deployment.yaml"))
        .skipSteadyStateCheck(false)
        .shouldOpenFetchFilesLogStream(false)
        .useNewKubectlVersion(false)
        .useLatestKustomizeVersion(false)
        .useK8sApiForSteadyStateCheck(true)
        .showDetailedDiagnosticLogs(showDetailedDiagnosticLogs)
        .build();
  }

  private Map<String, String> buildAbstractions(String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(INITIAL_HASH_MAP_CAPACITY);
    String owner = getOwner(orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  private static String getOwner(String orgIdentifier, String projectIdentifier) {
    String owner = null;
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      owner = String.format(PROJECT_OWNER, orgIdentifier, projectIdentifier);
    } else {
      if (isNotEmpty(orgIdentifier)) {
        owner = String.format(ORG_OWNER, orgIdentifier);
      }
    }
    return owner;
  }

  private void validateInfrastructureOutcome(InfrastructureOutcome infrastructureOutcome) {
    switch (infrastructureOutcome.getKind()) {
      case KUBERNETES_DIRECT:
      case KUBERNETES_GCP:
        return;
      default:
        throw new UnsupportedOperationException(
            format("Unsupported outcome for infrastructure kind: [%s]", infrastructureOutcome.getKind()));
    }
  }

  private K8sInfraDelegateConfig getInfraDelegateConfig(
      String connectorIdentifier, String infrastructureId, String environmentId, BaseNGAccess ngAccess) {
    if (!StringUtils.isBlank(connectorIdentifier)) {
      ConnectorInfoDTO responseDTO = k8sEntityHelper.getConnectorInfoDTO(connectorIdentifier, ngAccess);
      return DirectK8sInfraDelegateConfig.builder()
          .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) responseDTO.getConnectorConfig())
          .encryptionDataDetails(k8sEntityHelper.getEncryptionDataDetails(responseDTO, ngAccess))
          .build();
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    // Assumes the updated values account for any changes resulting from the project movement
    Optional<Environment> environmentEntity = environmentService.get(scopeInfo, environmentId, false);
    Optional<InfrastructureEntity> infraEntity =
        infrastructureEntityService.get(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
            ngAccess.getProjectIdentifier(), scopeInfo, environmentId, infrastructureId, false, false);

    if (environmentEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("Environment with identity [%s] does not exist", environmentId));
    }
    if (infraEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("Infra with identity [%s] does not exist", environmentId));
    }

    Environment environment = environmentEntity.get();
    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity.get());

    InfrastructureOutcome infrastructureOutcome =
        infrastructureMapper.toOutcome(infrastructureConfig.getInfrastructureDefinitionConfig().getSpec(),
            EnvironmentOutcome.builder().type(environment.getType()).identifier(environmentId).build(),
            ServiceStepOutcome.builder().build(), ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
            ngAccess.getProjectIdentifier(), infrastructureConfig.getInfrastructureDefinitionConfig().getTags(),
            infrastructureConfig.getInfrastructureDefinitionConfig().getDescription(),
            gitAwareEntityHelper.getEntityGitDetailsForOutcome(infraEntity.get()));

    validateInfrastructureOutcome(infrastructureOutcome);
    return k8sEntityHelper.getK8sInfraDelegateConfig(Ambiance.newBuilder().build(), infrastructureOutcome, ngAccess);
  }
}
