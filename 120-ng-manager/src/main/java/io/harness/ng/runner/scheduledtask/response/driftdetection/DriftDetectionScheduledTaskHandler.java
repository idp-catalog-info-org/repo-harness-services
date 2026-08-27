/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.driftdetection;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.driftdetection.K8sDriftDetectionPayload;
import io.harness.cdng.driftdetection.K8sDriftDetectionResultPayload;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.delegate.Status;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sDiffRequest;
import io.harness.delegate.task.k8s.K8sDiffRequest.K8sDiffRequestBuilder;
import io.harness.delegate.task.k8s.K8sDiffResponse;
import io.harness.delegate.task.k8s.K8sDriftFetchRequest;
import io.harness.delegate.task.k8s.K8sDriftFetchResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.driftdetection.entity.DriftDetectionEntity;
import io.harness.driftdetection.entity.DriftDetectionScheduledTaskInfo;
import io.harness.driftdetection.entity.DriftStatus;
import io.harness.driftdetection.expression.DriftDetectionExpressionContext;
import io.harness.driftdetection.expression.DriftDetectionExpressionService;
import io.harness.driftdetection.service.DriftDetectionDelegateConfigResolver;
import io.harness.driftdetection.service.DriftDetectionResultRecord;
import io.harness.driftdetection.service.DriftDetectionResultService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.repositories.driftdetection.DriftDetectionEntityRepository;
import io.harness.repositories.driftdetection.DriftDetectionScheduledTaskInfoRepository;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class DriftDetectionScheduledTaskHandler {
  private static final int DIFF_TASK_TIMEOUT_MINUTES = 5;

  private static final ObjectMapper objectMapper = new ObjectMapper()
                                                       .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                       .disable(MapperFeature.USE_GETTERS_AS_SETTERS);

  private final DriftDetectionScheduledTaskInfoRepository scheduledTaskInfoRepository;
  private final DriftDetectionEntityRepository entityRepository;
  private final DriftDetectionDelegateConfigResolver delegateConfigResolver;
  private final DriftDetectionResultService resultService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final ScopeInfoService scopeInfoService;
  private final DriftDetectionExpressionService expressionService;
  private final WaitNotifyEngine waitNotifyEngine;

  @Inject
  public DriftDetectionScheduledTaskHandler(DriftDetectionScheduledTaskInfoRepository scheduledTaskInfoRepository,
      DriftDetectionEntityRepository entityRepository, DriftDetectionDelegateConfigResolver delegateConfigResolver,
      DriftDetectionResultService resultService, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      ScopeInfoService scopeInfoService, DriftDetectionExpressionService expressionService,
      WaitNotifyEngine waitNotifyEngine) {
    this.scheduledTaskInfoRepository = scheduledTaskInfoRepository;
    this.entityRepository = entityRepository;
    this.delegateConfigResolver = delegateConfigResolver;
    this.resultService = resultService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.scopeInfoService = scopeInfoService;
    this.expressionService = expressionService;
    this.waitNotifyEngine = waitNotifyEngine;
  }

  public boolean processScheduledTaskResponse(ScheduledTaskResponse response) {
    String scheduledTaskId = response.getScheduledTaskId();
    String accountId = response.getAccountId();

    try {
      if (response.hasExecutionResponse()) {
        return handleExecutionResponse(accountId, scheduledTaskId, response.getExecutionResponse());
      } else if (response.hasLifecycleEvent()) {
        return handleLifecycleEvent(accountId, scheduledTaskId, response.getLifecycleEvent());
      } else {
        log.warn("Received empty ScheduledTaskResponse for drift detection");
        return false;
      }
    } catch (Exception e) {
      log.error("Failed to process drift detection scheduled task response, scheduledTaskId: {}", scheduledTaskId, e);
      return false;
    }
  }

  private boolean handleExecutionResponse(String accountId, String scheduledTaskId, GetTaskStatusResponse response) {
    Status status = response.getStatus();
    log.info("Received drift detection execution response - scheduledTaskId: {}, accountId: {}, status: {}",
        scheduledTaskId, accountId, status.name());

    if (status != Status.SUCCESS) {
      log.warn("Drift detection fetch task failed - scheduledTaskId: {}, status: {}, error: {}", scheduledTaskId,
          status.name(), response.getError());
      recordErrorResult(accountId, scheduledTaskId, "Fetch task failed: " + response.getError());
      return true;
    }

    Optional<DriftDetectionScheduledTaskInfo> taskInfoOpt =
        scheduledTaskInfoRepository.findByScheduledTaskId(accountId, scheduledTaskId);
    if (taskInfoOpt.isEmpty()) {
      log.warn("No DriftDetectionScheduledTaskInfo found for scheduledTaskId: {}", scheduledTaskId);
      return true;
    }

    DriftDetectionScheduledTaskInfo taskInfo = taskInfoOpt.get();

    K8sDriftFetchResponse fetchResponse = deserializeFetchResponse(response);
    if (fetchResponse == null) {
      recordErrorResult(accountId, scheduledTaskId, "Failed to deserialize fetch response");
      return true;
    }

    if (fetchResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      recordErrorResult(accountId, scheduledTaskId,
          "Fetch task returned non-success status: " + fetchResponse.getCommandExecutionStatus());
      return true;
    }

    List<String> overrideFiles = fetchResponse.getOverrideFiles();
    submitDiffTaskAndPersistResult(accountId, taskInfo, overrideFiles);
    return true;
  }

  private void submitDiffTaskAndPersistResult(
      String accountId, DriftDetectionScheduledTaskInfo taskInfo, List<String> overrideFiles) {
    try {
      Optional<DriftDetectionEntity> entityOpt =
          entityRepository.findByIdentity(accountId, taskInfo.getParentUniqueId(), taskInfo.getEntityId());
      if (entityOpt.isEmpty()) {
        recordErrorResult(accountId, taskInfo, "DriftDetectionEntity not found for diff task");
        return;
      }

      DriftDetectionEntity entity = entityOpt.get();
      K8sInfraDelegateConfig infraConfig = delegateConfigResolver.resolveInfraConfig(entity).orElse(null);
      ManifestDelegateConfig manifestConfig = delegateConfigResolver.resolveManifestConfig(entity).orElse(null);

      if (infraConfig == null) {
        recordErrorResult(accountId, taskInfo, "Cannot resolve K8s infra delegate config for diff task");
        return;
      }
      if (manifestConfig == null) {
        recordErrorResult(accountId, taskInfo, "Cannot resolve manifest delegate config for diff task");
        return;
      }

      String orgIdentifier = null;
      String projectIdentifier = null;
      if (entity.getParentUniqueId() != null) {
        Optional<ScopeInfo> scopeInfoOpt =
            scopeInfoService.getScopeInfoFromUniqueId(accountId, entity.getParentUniqueId());
        if (scopeInfoOpt.isPresent()) {
          orgIdentifier = scopeInfoOpt.get().getOrgIdentifier();
          projectIdentifier = scopeInfoOpt.get().getProjectIdentifier();
        }
      }

      Map<String, String> taskSetupAbstractions =
          getNGTaskSetupAbstractionsWithOwner(accountId, orgIdentifier, projectIdentifier);
      taskSetupAbstractions.put("ng", "true");
      if (orgIdentifier != null) {
        taskSetupAbstractions.put("orgIdentifier", orgIdentifier);
      }
      if (projectIdentifier != null) {
        taskSetupAbstractions.put("projectIdentifier", projectIdentifier);
      }

      List<ManifestDelegateConfig> valuesManifestConfigs = delegateConfigResolver.resolveValuesManifestConfigs(entity);
      List<String> inlineValuesContent = getInlineValuesFromPayload(entity);
      if (inlineValuesContent.isEmpty()) {
        inlineValuesContent = delegateConfigResolver.resolveInlineValuesContent(entity);
      }

      if (valuesManifestConfigs.isEmpty()) {
        List<String> allValuesContent = new ArrayList<>(overrideFiles != null ? overrideFiles : List.of());
        if (!inlineValuesContent.isEmpty()) {
          allValuesContent.addAll(inlineValuesContent);
        }
        log.info("Drift diff values: inlineCount={}, totalCount={} for entityId: {}", inlineValuesContent.size(),
            allValuesContent.size(), entity.getEntityId());
        submitDiffAsync(
            accountId, taskInfo, entity, infraConfig, manifestConfig, allValuesContent, taskSetupAbstractions);
      } else {
        K8sDriftFetchRequest fetchRequest = K8sDriftFetchRequest.builder()
                                                .accountId(accountId)
                                                .entityId(entity.getEntityId())
                                                .parentUniqueId(entity.getParentUniqueId())
                                                .valuesManifestDelegateConfigs(valuesManifestConfigs)
                                                .valuesYamlList(overrideFiles)
                                                .timeoutIntervalInMin(DIFF_TASK_TIMEOUT_MINUTES)
                                                .build();

        DelegateTaskRequest fetchTaskRequest = DelegateTaskRequest.builder()
                                                   .accountId(accountId)
                                                   .taskType("K8S_DRIFT_FETCH_TASK_NG")
                                                   .taskParameters(fetchRequest)
                                                   .executionTimeout(Duration.ofMinutes(DIFF_TASK_TIMEOUT_MINUTES))
                                                   .taskSetupAbstractions(taskSetupAbstractions)
                                                   .build();

        String fetchTaskId = delegateGrpcClientWrapper.submitAsyncTaskV2(fetchTaskRequest, Duration.ZERO);
        waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION,
            new DriftDetectionFetchValuesNotifyCallback(accountId, taskInfo.getScheduledTaskId(),
                taskInfo.getParentUniqueId(), taskInfo.getEntityId(), new ArrayList<>(inlineValuesContent),
                taskSetupAbstractions),
            fetchTaskId);
        log.info("Submitted async values fetch task {} for entityId: {}", fetchTaskId, entity.getEntityId());
      }
    } catch (Exception e) {
      log.error("Failed to submit drift task for entityId: {}", taskInfo.getEntityId(), e);
      recordErrorResult(accountId, taskInfo, "Drift task submission failed: " + e.getMessage());
    }
  }

  void submitDiffAsync(String accountId, DriftDetectionScheduledTaskInfo taskInfo, DriftDetectionEntity entity,
      K8sInfraDelegateConfig infraConfig, ManifestDelegateConfig manifestConfig, List<String> allValuesContent,
      Map<String, String> taskSetupAbstractions) {
    List<String> resolvedValues = resolveExpressions(entity, allValuesContent);
    String currentChecksum = computeSourceChecksum(resolvedValues);

    String releaseName = resolveReleaseName(entity);
    K8sDiffRequestBuilder diffRequestBuilder = K8sDiffRequest.builder()
                                                   .accountId(accountId)
                                                   .releaseName(releaseName)
                                                   .k8sInfraDelegateConfig(infraConfig)
                                                   .manifestDelegateConfig(manifestConfig)
                                                   .timeoutIntervalInMin(DIFF_TASK_TIMEOUT_MINUTES);

    if (!resolvedValues.isEmpty()) {
      diffRequestBuilder.valuesYamlList(new ArrayList<>(resolvedValues));
    }

    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(accountId)
                                                  .taskType("K8S_DIFF_TASK_NG")
                                                  .taskParameters(diffRequestBuilder.build())
                                                  .executionTimeout(Duration.ofMinutes(DIFF_TASK_TIMEOUT_MINUTES))
                                                  .taskSetupAbstractions(taskSetupAbstractions)
                                                  .build();

    String diffTaskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION,
        new DriftDetectionDiffNotifyCallback(accountId, taskInfo.getScheduledTaskId(), taskInfo.getParentUniqueId(),
            taskInfo.getEntityId(), currentChecksum),
        diffTaskId);
    log.info("Submitted async diff task {} for entityId: {}", diffTaskId, taskInfo.getEntityId());
  }

  void onFetchValuesComplete(String accountId, String scheduledTaskId, String parentUniqueId, String entityId,
      List<String> inlineValuesContent, Map<String, String> taskSetupAbstractions, ResponseData responseData) {
    Optional<DriftDetectionScheduledTaskInfo> taskInfoOpt =
        scheduledTaskInfoRepository.findByScheduledTaskId(accountId, scheduledTaskId);
    if (taskInfoOpt.isEmpty()) {
      log.warn(
          "No DriftDetectionScheduledTaskInfo found for scheduledTaskId: {} in fetch-values callback", scheduledTaskId);
      return;
    }
    DriftDetectionScheduledTaskInfo taskInfo = taskInfoOpt.get();

    Optional<DriftDetectionEntity> entityOpt = entityRepository.findByIdentity(accountId, parentUniqueId, entityId);
    if (entityOpt.isEmpty()) {
      recordErrorResult(accountId, taskInfo, "DriftDetectionEntity not found in fetch-values callback");
      return;
    }
    DriftDetectionEntity entity = entityOpt.get();

    List<String> fetchedValues = List.of();
    if (responseData instanceof K8sDeployResponse deployResponse
        && deployResponse.getK8sNGTaskResponse() instanceof K8sDriftFetchResponse fetchResponse
        && fetchResponse.getOverrideFiles() != null) {
      fetchedValues = fetchResponse.getOverrideFiles();
    } else {
      log.warn("Values fetch callback received unexpected response for entityId: {}; using inline only", entityId);
    }

    List<String> allValuesContent = new ArrayList<>(fetchedValues);
    if (!inlineValuesContent.isEmpty()) {
      allValuesContent.addAll(inlineValuesContent);
    }
    log.info("Drift diff values (after async fetch): fetchedCount={}, totalCount={} for entityId: {}",
        fetchedValues.size(), allValuesContent.size(), entityId);

    K8sInfraDelegateConfig infraConfig = delegateConfigResolver.resolveInfraConfig(entity).orElse(null);
    ManifestDelegateConfig manifestConfig = delegateConfigResolver.resolveManifestConfig(entity).orElse(null);
    if (infraConfig == null || manifestConfig == null) {
      recordErrorResult(accountId, taskInfo, "Cannot resolve delegate configs for diff task");
      return;
    }
    submitDiffAsync(accountId, taskInfo, entity, infraConfig, manifestConfig, allValuesContent, taskSetupAbstractions);
  }

  void onDiffComplete(String accountId, String scheduledTaskId, String sourceChecksum, ResponseData responseData) {
    Optional<DriftDetectionScheduledTaskInfo> taskInfoOpt =
        scheduledTaskInfoRepository.findByScheduledTaskId(accountId, scheduledTaskId);
    if (taskInfoOpt.isEmpty()) {
      log.warn("No DriftDetectionScheduledTaskInfo found for scheduledTaskId: {} in diff callback", scheduledTaskId);
      return;
    }
    DriftDetectionScheduledTaskInfo taskInfo = taskInfoOpt.get();

    if (!(responseData instanceof DelegateResponseData delegateResponseData)) {
      recordErrorResult(
          accountId, taskInfo, "Unexpected diff response type: " + responseData.getClass().getSimpleName());
      return;
    }
    handleDiffResponse(accountId, taskInfo, delegateResponseData, sourceChecksum);
  }

  private void handleDiffResponse(String accountId, DriftDetectionScheduledTaskInfo taskInfo,
      DelegateResponseData responseData, String sourceChecksum) {
    if (!(responseData instanceof K8sDeployResponse)) {
      log.warn("Unexpected response type for diff task: {}", responseData.getClass().getSimpleName());
      recordErrorResult(accountId, taskInfo, "Unexpected diff response type");
      return;
    }

    K8sDeployResponse deployResponse = (K8sDeployResponse) responseData;
    if (deployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      recordErrorResult(accountId, taskInfo, "Diff task failed: " + deployResponse.getCommandExecutionStatus().name());
      return;
    }

    if (!(deployResponse.getK8sNGTaskResponse() instanceof K8sDiffResponse)) {
      log.warn("Unexpected K8sNGTaskResponse type for diff: {}",
          deployResponse.getK8sNGTaskResponse() != null
              ? deployResponse.getK8sNGTaskResponse().getClass().getSimpleName()
              : "null");
      recordErrorResult(accountId, taskInfo, "Unexpected diff response payload type");
      return;
    }

    K8sDiffResponse diffResponse = (K8sDiffResponse) deployResponse.getK8sNGTaskResponse();
    persistDriftResult(accountId, taskInfo, diffResponse, sourceChecksum);
  }

  private void persistDriftResult(
      String accountId, DriftDetectionScheduledTaskInfo taskInfo, K8sDiffResponse diffResponse, String sourceChecksum) {
    String diffYaml = diffResponse.getManifestDiffYaml();
    DriftStatus driftStatus = isEmpty(diffYaml) ? DriftStatus.NO_DRIFT : DriftStatus.DRIFTED;

    K8sDriftDetectionResultPayload resultPayload =
        isEmpty(diffYaml) ? null : K8sDriftDetectionResultPayload.builder().manifestDiffYaml(diffYaml).build();

    DriftDetectionResultRecord record = DriftDetectionResultRecord.builder()
                                            .accountId(accountId)
                                            .parentUniqueId(taskInfo.getParentUniqueId())
                                            .entityId(taskInfo.getEntityId())
                                            .status(driftStatus)
                                            .payload(resultPayload)
                                            .sourceChecksum(sourceChecksum)
                                            .lastDriftCheckAt(Instant.now())
                                            .build();

    resultService.recordResult(record);
    log.info("Drift detection result persisted - entityId: {}, status: {}, checksum: {}, diffSize: {}",
        taskInfo.getEntityId(), driftStatus, sourceChecksum, diffYaml != null ? diffYaml.length() : 0);
  }

  private String resolveReleaseName(DriftDetectionEntity entity) {
    if (entity.getPayload() instanceof K8sDriftDetectionPayload) {
      K8sDriftDetectionPayload payload = (K8sDriftDetectionPayload) entity.getPayload();
      if (payload.getReleaseName() != null) {
        return payload.getReleaseName();
      }
    }
    return entity.getEntityId();
  }

  private List<String> getInlineValuesFromPayload(DriftDetectionEntity entity) {
    if (entity.getPayload() instanceof K8sDriftDetectionPayload) {
      K8sDriftDetectionPayload k8sPayload = (K8sDriftDetectionPayload) entity.getPayload();
      if (k8sPayload.getInlineValuesYamlContents() != null && !k8sPayload.getInlineValuesYamlContents().isEmpty()) {
        return k8sPayload.getInlineValuesYamlContents();
      }
    }
    return List.of();
  }

  private List<String> resolveExpressions(DriftDetectionEntity entity, List<String> overrideFiles) {
    if (overrideFiles == null || overrideFiles.isEmpty()) {
      return List.of();
    }
    if (entity.getNodeExecutionId() == null) {
      log.info("No nodeExecutionId on entity {}; skipping expression resolution", entity.getEntityId());
      return overrideFiles;
    }
    DriftDetectionExpressionContext context = DriftDetectionExpressionContext.builder()
                                                  .accountId(entity.getAccountId())
                                                  .parentUniqueId(entity.getParentUniqueId())
                                                  .entityId(entity.getEntityId())
                                                  .type(entity.getType())
                                                  .nodeExecutionId(entity.getNodeExecutionId())
                                                  .build();
    try {
      return overrideFiles.stream().map(content -> expressionService.resolve(context, content)).toList();
    } catch (Exception e) {
      log.warn("Expression resolution failed for entityId: {}; using raw values", entity.getEntityId(), e);
      return overrideFiles;
    }
  }

  private K8sDriftFetchResponse deserializeFetchResponse(GetTaskStatusResponse response) {
    if (response.getData().isEmpty()) {
      log.warn("No data in GetTaskStatusResponse");
      return null;
    }
    try {
      com.fasterxml.jackson.databind.JsonNode responseTree = objectMapper.readTree(response.getData().toByteArray());
      com.fasterxml.jackson.databind.JsonNode taskResponseNode = responseTree.get("k8sNGTaskResponse");
      if (taskResponseNode != null && !taskResponseNode.isNull()) {
        return objectMapper.treeToValue(taskResponseNode, K8sDriftFetchResponse.class);
      }
      return objectMapper.treeToValue(responseTree, K8sDriftFetchResponse.class);
    } catch (Exception e) {
      log.error("Failed to deserialize drift fetch response", e);
      return null;
    }
  }

  private void recordErrorResult(String accountId, String scheduledTaskId, String errorMessage) {
    Optional<DriftDetectionScheduledTaskInfo> taskInfoOpt =
        scheduledTaskInfoRepository.findByScheduledTaskId(accountId, scheduledTaskId);
    taskInfoOpt.ifPresent(taskInfo -> recordErrorResult(accountId, taskInfo, errorMessage));
  }

  private void recordErrorResult(String accountId, DriftDetectionScheduledTaskInfo taskInfo, String errorMessage) {
    try {
      DriftDetectionResultRecord record = DriftDetectionResultRecord.builder()
                                              .accountId(accountId)
                                              .parentUniqueId(taskInfo.getParentUniqueId())
                                              .entityId(taskInfo.getEntityId())
                                              .status(DriftStatus.ERROR)
                                              .errorMessage(errorMessage)
                                              .lastDriftCheckAt(Instant.now())
                                              .build();
      resultService.recordResult(record);
    } catch (Exception e) {
      log.error("Failed to record error result for entityId: {}", taskInfo.getEntityId(), e);
    }
  }

  private boolean handleLifecycleEvent(String accountId, String scheduledTaskId, ScheduledTaskLifecycleEvent event) {
    ScheduledTaskLifecycleStatus status = event.getStatus();
    log.info("Received drift detection lifecycle event - scheduledTaskId: {}, accountId: {}, status: {}, message: {}",
        scheduledTaskId, accountId, status.name(), event.getMessage());

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_DISABLED) {
      log.error("Drift detection scheduled task was disabled - scheduledTaskId: {}", scheduledTaskId);
    } else if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED) {
      log.warn("Drift detection scheduled task was suspended - scheduledTaskId: {}", scheduledTaskId);
    }

    return true;
  }

  private String computeSourceChecksum(List<String> resolvedValues) {
    if (resolvedValues == null || resolvedValues.isEmpty()) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String value : resolvedValues) {
        if (value != null) {
          digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception e) {
      log.warn("Failed to compute source checksum; will proceed with diff", e);
      return null;
    }
  }
}
