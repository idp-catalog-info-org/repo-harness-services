/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.runner.scheduledtask.response.artifactpolling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.ScheduledTaskLifecycleEvent;
import io.harness.delegate.ScheduledTaskLifecycleStatus;
import io.harness.delegate.ScheduledTaskResponse;
import io.harness.delegate.Status;
import io.harness.ng.webhook.polling.PolledItemPublisher;
import io.harness.polling.bean.ArtifactInfo;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.ScheduledPollingTaskInfo;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.contracts.Type;
import io.harness.polling.service.intfc.PollingService;
import io.harness.polling.service.intfc.ScheduledPollingTaskInfoService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public class ArtifactPollingScheduledTaskHandler {
  private static final String BUILDS_KEY = "BUILDS";
  // The s3-artifact plugin has no get-builds action; its get-file-paths equivalent publishes the same
  // BuildDetails list under FILE_PATHS instead of BUILDS.
  private static final String FILE_PATHS_KEY = "FILE_PATHS";

  private final ScheduledPollingTaskInfoService scheduledPollingTaskInfoService;
  private final PollingService pollingService;
  private final PolledItemPublisher polledItemPublisher;
  private final ObjectMapper objectMapper;

  @Inject
  public ArtifactPollingScheduledTaskHandler(ScheduledPollingTaskInfoService scheduledPollingTaskInfoService,
      PollingService pollingService, PolledItemPublisher polledItemPublisher, ObjectMapper objectMapper) {
    this.scheduledPollingTaskInfoService = scheduledPollingTaskInfoService;
    this.pollingService = pollingService;
    this.polledItemPublisher = polledItemPublisher;
    this.objectMapper = objectMapper;
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
        log.warn("Received empty ScheduledTaskResponse for artifact polling, scheduledTaskId: {}", scheduledTaskId);
        return true;
      }
    } catch (Exception e) {
      log.error(
          "Failed to process artifact polling scheduled task response for scheduledTaskId: {}", scheduledTaskId, e);
      return true;
    }
  }

  boolean handleExecutionResponse(String accountId, String scheduledTaskId, GetTaskStatusResponse response) {
    Status status = response.getStatus();

    log.info("Received artifact polling execution response - scheduledTaskId: {}, accountId: {}, status: {}",
        scheduledTaskId, accountId, status.name());

    if (status != Status.SUCCESS) {
      log.warn("Artifact polling task failed - scheduledTaskId: {}, status: {}, error: {}", scheduledTaskId,
          status.name(), response.getError());
      handleFailure(accountId, scheduledTaskId);
      return true;
    }

    Optional<ScheduledPollingTaskInfo> taskInfoOpt =
        scheduledPollingTaskInfoService.findByScheduledTaskId(scheduledTaskId);
    if (taskInfoOpt.isEmpty()) {
      log.warn("No ScheduledPollingTaskInfo found for scheduledTaskId: {}", scheduledTaskId);
      return true;
    }

    ScheduledPollingTaskInfo taskInfo = taskInfoOpt.get();
    PollingDocument pollingDocument = pollingService.get(accountId, taskInfo.getPollingDocumentId());
    if (pollingDocument == null) {
      log.warn("No PollingDocument found for pollingDocId: {}, scheduledTaskId: {}", taskInfo.getPollingDocumentId(),
          scheduledTaskId);
      return true;
    }

    if (isEmpty(pollingDocument.getSignatures())) {
      log.info("No signatures for pollingDocId: {}, skipping", pollingDocument.getUuid());
      return true;
    }

    Map<String, String> outputVariables = parseOutputVariables(response);
    String buildsKey = resolveBuildsKey(outputVariables);
    if (buildsKey == null) {
      log.warn("No BUILDS or FILE_PATHS found in response for scheduledTaskId: {}", scheduledTaskId);
      return true;
    }

    List<String> currentVersions = parseArtifactVersions(outputVariables.get(buildsKey));
    if (currentVersions == null) {
      log.warn("Failed to parse artifact versions from response for scheduledTaskId: {}", scheduledTaskId);
      return true;
    }

    processArtifactVersions(pollingDocument, currentVersions);

    if (pollingDocument.getFailedAttempts() > 0) {
      pollingService.updateFailedAttempts(accountId, pollingDocument.getUuid(), 0);
      pollingService.updateTriggerPollingStatus(
          accountId, pollingDocument.getSignatures(), true, null, Collections.emptyList(), null);
    }

    return true;
  }

  private void processArtifactVersions(PollingDocument pollingDocument, List<String> currentVersions) {
    String accountId = pollingDocument.getAccountId();
    String pollDocId = pollingDocument.getUuid();
    ArtifactPolledResponse savedResponse = (ArtifactPolledResponse) pollingDocument.getPolledResponse();

    if (savedResponse == null) {
      log.info(
          "First collection for artifact polling, pollingDocId: {}. Storing versions without triggering.", pollDocId);
      pollingService.updatePolledResponse(
          accountId, pollDocId, ArtifactPolledResponse.builder().allPolledKeys(new HashSet<>(currentVersions)).build());
      pollingService.updateTriggerPollingStatus(
          accountId, pollingDocument.getSignatures(), true, null, currentVersions, null);
      return;
    }

    Set<String> knownVersions = savedResponse.getAllPolledKeys();
    List<String> newVersions =
        currentVersions.stream().filter(v -> !knownVersions.contains(v)).collect(Collectors.toList());

    if (isNotEmpty(newVersions)) {
      log.info("Publishing {} new artifact versions for pollingDocId: {}", newVersions.size(), pollDocId);
      publishNewVersions(pollingDocument, newVersions);
    }

    Set<String> updatedKeys = new HashSet<>(knownVersions);
    updatedKeys.addAll(currentVersions);
    pollingService.updatePolledResponse(
        accountId, pollDocId, ArtifactPolledResponse.builder().allPolledKeys(updatedKeys).build());
  }

  private void publishNewVersions(PollingDocument pollingDocument, List<String> newVersions) {
    ArtifactInfo artifactInfo = (ArtifactInfo) pollingDocument.getPollingInfo();
    String name = getArtifactName(artifactInfo);
    Type type = getPollingType(artifactInfo);

    for (String version : newVersions) {
      PollingResponse.Builder responseBuilder =
          PollingResponse.newBuilder()
              .setAccountId(pollingDocument.getAccountId())
              .setPollingDocId(pollingDocument.getUuid())
              .setBuildInfo(
                  BuildInfo.newBuilder().setName(name).addAllVersions(Collections.singletonList(version)).build())
              .addAllSignatures(pollingDocument.getSignatures());

      if (type != null) {
        responseBuilder.setType(type);
      }

      polledItemPublisher.publishPolledItems(responseBuilder.build());
    }

    pollingService.updateTriggerPollingStatus(
        pollingDocument.getAccountId(), pollingDocument.getSignatures(), true, null, newVersions, null);
  }

  private String getArtifactName(ArtifactInfo artifactInfo) {
    switch (artifactInfo.getType()) {
      case DOCKER_REGISTRY:
        return ((io.harness.polling.bean.artifact.DockerHubArtifactInfo) artifactInfo).getImagePath();
      case ECR:
        return ((io.harness.polling.bean.artifact.EcrArtifactInfo) artifactInfo).getImagePath();
      case GCR:
        return ((io.harness.polling.bean.artifact.GcrArtifactInfo) artifactInfo).getImagePath();
      case ACR:
        return ((io.harness.polling.bean.artifact.AcrArtifactInfo) artifactInfo).getRepository();
      case GOOGLE_ARTIFACT_REGISTRY:
        return ((io.harness.polling.bean.artifact.GARArtifactInfo) artifactInfo).getPkg();
      case ARTIFACTORY_REGISTRY:
        return ((io.harness.polling.bean.artifact.ArtifactoryRegistryArtifactInfo) artifactInfo).getRepository();
      case NEXUS3_REGISTRY:
        return ((io.harness.polling.bean.artifact.NexusRegistryArtifactInfo) artifactInfo).getRepository();
      case GITHUB_PACKAGES:
        return ((io.harness.polling.bean.artifact.GithubPackagesArtifactInfo) artifactInfo).getPackageName();
      case AMAZONS3:
        return ((io.harness.polling.bean.artifact.S3ArtifactInfo) artifactInfo).getBucketName();
      default:
        return artifactInfo.getType().getDisplayName();
    }
  }

  private Type getPollingType(ArtifactInfo artifactInfo) {
    switch (artifactInfo.getType()) {
      case DOCKER_REGISTRY:
        return Type.DOCKER_HUB;
      case ECR:
        return Type.ECR;
      case GCR:
        return Type.GCR;
      case ACR:
        return Type.ACR;
      case GOOGLE_ARTIFACT_REGISTRY:
        return Type.GOOGLE_ARTIFACT_REGISTRY;
      case ARTIFACTORY_REGISTRY:
        return Type.ARTIFACTORY;
      case NEXUS3_REGISTRY:
        return Type.NEXUS3;
      case GITHUB_PACKAGES:
        return Type.GITHUB_PACKAGES;
      case AMAZONS3:
        return Type.AMAZON_S3;
      default:
        return null;
    }
  }

  boolean handleLifecycleEvent(String accountId, String scheduledTaskId, ScheduledTaskLifecycleEvent event) {
    ScheduledTaskLifecycleStatus status = event.getStatus();
    log.info("Received artifact polling lifecycle event - scheduledTaskId: {}, accountId: {}, status: {}, message: {}",
        scheduledTaskId, accountId, status.name(), event.getMessage());

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_SUSPENDED) {
      log.warn("Artifact polling task suspended - scheduledTaskId: {}", scheduledTaskId);
      handleFailure(accountId, scheduledTaskId);
    }

    if (status == ScheduledTaskLifecycleStatus.SCHEDULED_TASK_LIFECYCLE_STATUS_DISABLED) {
      log.error("Artifact polling task disabled (terminal) - scheduledTaskId: {}", scheduledTaskId);
      scheduledPollingTaskInfoService.findByScheduledTaskId(scheduledTaskId).ifPresent(taskInfo -> {
        scheduledPollingTaskInfoService.deleteByAccountIdentifierAndPollingDocumentId(
            accountId, taskInfo.getPollingDocumentId());
      });
    }

    return true;
  }

  private void handleFailure(String accountId, String scheduledTaskId) {
    scheduledPollingTaskInfoService.findByScheduledTaskId(scheduledTaskId).ifPresent(taskInfo -> {
      PollingDocument pollingDocument = pollingService.get(accountId, taskInfo.getPollingDocumentId());
      if (pollingDocument != null) {
        int failedAttempts = pollingDocument.getFailedAttempts() + 1;
        pollingService.updateFailedAttempts(accountId, pollingDocument.getUuid(), failedAttempts);
      }
    });
  }

  private Map<String, String> parseOutputVariables(GetTaskStatusResponse response) {
    if (response.getData().isEmpty()) {
      return Collections.emptyMap();
    }
    try {
      JsonNode rootNode = objectMapper.readTree(response.getData().toByteArray());
      JsonNode outputVarsNode = rootNode.get("output_vars");
      if (outputVarsNode != null && outputVarsNode.isObject()) {
        return objectMapper.convertValue(outputVarsNode, new TypeReference<>() {});
      }
      return Collections.emptyMap();
    } catch (Exception e) {
      log.error("Failed to parse output variables from artifact polling response", e);
      return Collections.emptyMap();
    }
  }

  private String resolveBuildsKey(Map<String, String> outputVariables) {
    if (isEmpty(outputVariables)) {
      return null;
    }
    if (outputVariables.containsKey(BUILDS_KEY)) {
      return BUILDS_KEY;
    }
    return outputVariables.containsKey(FILE_PATHS_KEY) ? FILE_PATHS_KEY : null;
  }

  private List<String> parseArtifactVersions(String buildsJson) {
    try {
      List<Map<String, Object>> builds = objectMapper.readValue(buildsJson, new TypeReference<>() {});
      List<String> versions = new ArrayList<>();
      for (Map<String, Object> build : builds) {
        String version = extractVersionFromBuild(build);
        if (version != null) {
          versions.add(version);
        }
      }
      return versions;
    } catch (Exception e) {
      log.error("Failed to parse artifact builds JSON", e);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private String extractVersionFromBuild(Map<String, Object> build) {
    Object tag = build.get("tag");
    if (tag != null && !tag.toString().isEmpty()) {
      return tag.toString();
    }
    Object buildDetails = build.get("buildDetails");
    if (buildDetails instanceof Map) {
      Object number = ((Map<String, Object>) buildDetails).get("number");
      if (number != null && !number.toString().isEmpty()) {
        return number.toString();
      }
    }
    Object number = build.get("number");
    if (number != null && !number.toString().isEmpty()) {
      return number.toString();
    }
    return null;
  }
}
