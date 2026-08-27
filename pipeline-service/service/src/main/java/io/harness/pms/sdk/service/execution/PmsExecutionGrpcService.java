/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.service.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.plan.execution.beans.GraphUpdateInfo.TTL_WEEKS;
import static io.harness.springdata.PersistenceUtils.getRetryPolicyWithDuplicateKeyException;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.pms.contracts.service.ExecutionSummaryResponse;
import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.contracts.service.PlanExecutionMetadataYamlRequest;
import io.harness.pms.contracts.service.PlanExecutionMetadataYamlResponse;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc.PmsExecutionServiceImplBase;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo.ExecutionSummaryUpdateInfoKeys;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo.GraphUpdateInfoKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PmsExecutionGrpcService extends PmsExecutionServiceImplBase {
  private static final String MODULE_INFO_UPDATE_KEY = "executionSummaryUpdateInfo.moduleInfo.%s.%s";
  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject GraphUpdateInfoRepository graphUpdateInfoRepository;
  @Inject OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;

  @Override
  public void updateExecutionSummary(
      ExecutionSummaryUpdateRequest request, StreamObserver<ExecutionSummaryResponse> responseObserver) {
    updatePipelineInfoJson(request);
    updateStageModuleInfo(request);
    responseObserver.onNext(ExecutionSummaryResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void getPlanExecutionMetadataYaml(
      PlanExecutionMetadataYamlRequest request, StreamObserver<PlanExecutionMetadataYamlResponse> responseObserver) {
    String yaml =
        planExecutionMetadataService.getYaml(request.getAccountIdentifier(), request.getPlanExecutionId()).orElse("");
    responseObserver.onNext(PlanExecutionMetadataYamlResponse.newBuilder().setYaml(yaml).build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  void updatePipelineInfoJson(ExecutionSummaryUpdateRequest request) {
    String moduleName = request.getModuleName();
    String planExecutionId = request.getPlanExecutionId();
    String version = request.getVersion();
    Map<String, Object> pipelineInfoDoc = RecastOrchestrationUtils.fromJson(request.getPipelineModuleInfoJson());
    if (pipelineInfoDoc != null) {
      Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                              .is(planExecutionId)
                              .and(GraphUpdateInfoKeys.stepCategory)
                              .is(StepCategory.PIPELINE);
      Update update = getBaseUpdateWithFixedFields(request, true);
      if (HarnessYamlVersion.isV1(version)) {
        updateGraphUpdateInfoV1(update, moduleName, (LinkedHashMap<String, Object>) pipelineInfoDoc);
      } else {
        updateGraphUpdateInfo(update, moduleName, (LinkedHashMap<String, Object>) pipelineInfoDoc);
      }
      upsertGraphUpdateInfoWithRetry(criteria, update);
      orchestrationLogPublisher.onPipelineInfoUpdate(planExecutionId);
    }
  }

  @VisibleForTesting
  void updateStageModuleInfo(ExecutionSummaryUpdateRequest request) {
    String stageUuid = request.getNodeUuid();
    String moduleName = request.getModuleName();
    String nodeExecutionId = request.getNodeExecutionId();
    String stageInfo = request.getNodeModuleInfoJson();
    String planExecutionId = request.getPlanExecutionId();
    if (EmptyPredicate.isEmpty(stageUuid)) {
      return;
    }
    Map<String, Object> stageInfoDoc = RecastOrchestrationUtils.fromJson(stageInfo);
    if (stageInfoDoc != null) {
      Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                              .is(planExecutionId)
                              .and(GraphUpdateInfoKeys.stepCategory)
                              .is(StepCategory.STAGE)
                              .and(GraphUpdateInfoKeys.nodeExecutionId)
                              .is(nodeExecutionId);
      Update update = getBaseUpdateWithFixedFields(request, false);
      updateGraphUpdateInfo(update, moduleName, (LinkedHashMap<String, Object>) stageInfoDoc);
      upsertGraphUpdateInfoWithRetry(criteria, update);
      orchestrationLogPublisher.onStageInfoUpdate(planExecutionId, request.getNodeExecutionId());
    }
  }

  private void upsertGraphUpdateInfoWithRetry(Criteria criteria, Update update) {
    RetryPolicy<Object> retryPolicy = getRetryPolicyWithDuplicateKeyException(
        "[Retrying]: Failed upsert for GraphUpdateInfo; attempt: {}", "[Failed]: Failed GraphUpdateInfo; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> {
      graphUpdateInfoRepository.upsert(criteria, update);
      return null;
    });
  }

  Update getBaseUpdateWithFixedFields(ExecutionSummaryUpdateRequest request, boolean isStepCategoryPipeline) {
    Update update = new Update();
    String accountId = getAccountId(request.getPlanExecutionId());
    String planExecutionId = request.getPlanExecutionId();
    update.setOnInsert(GraphUpdateInfoKeys.accountIdentifier, accountId);
    update.setOnInsert(GraphUpdateInfoKeys.planExecutionId, planExecutionId);
    update.setOnInsert(GraphUpdateInfoKeys.createdAt, System.currentTimeMillis());
    update.setOnInsert(
        GraphUpdateInfoKeys.validUntil, Date.from(OffsetDateTime.now().plusWeeks(TTL_WEEKS).toInstant()));
    // By default, mongodb do not populate this on upsert
    update.setOnInsert("_class", GraphUpdateInfo.class.getAnnotation(TypeAlias.class).value());
    if (isStepCategoryPipeline) {
      update.setOnInsert(
          GraphUpdateInfoKeys.executionSummaryUpdateInfo + "." + ExecutionSummaryUpdateInfoKeys.stepCategory,
          StepCategory.PIPELINE);
    } else {
      String stageUuid = request.getNodeUuid();
      String nodeExecutionId = request.getNodeExecutionId();
      update.setOnInsert(GraphUpdateInfoKeys.nodeExecutionId, nodeExecutionId);
      update.setOnInsert(
          GraphUpdateInfoKeys.executionSummaryUpdateInfo + "." + ExecutionSummaryUpdateInfoKeys.stageUuid, stageUuid);
      update.setOnInsert(
          GraphUpdateInfoKeys.executionSummaryUpdateInfo + "." + ExecutionSummaryUpdateInfoKeys.stepCategory,
          StepCategory.STAGE);
    }
    return update;
  }

  private void updateGraphUpdateInfo(Update update, String moduleName, LinkedHashMap<String, Object> infoDoc) {
    for (Map.Entry<String, Object> entry : infoDoc.entrySet()) {
      String key = String.format(MODULE_INFO_UPDATE_KEY, moduleName, entry.getKey());
      if (entry.getValue() != null && Collection.class.isAssignableFrom(entry.getValue().getClass())) {
        Collection<Object> values = (Collection<Object>) entry.getValue();
        update.addToSet(key).each(values);
      } else if (entry.getValue() != null && Map.class.isAssignableFrom(entry.getValue().getClass())) {
        Map<Object, Object> values = (Map<Object, Object>) entry.getValue();
        for (Map.Entry<Object, Object> entry1 : values.entrySet()) {
          String mapKey = key + "." + entry1.getKey();
          update.set(mapKey, entry1.getValue());
        }
      } else {
        if (entry.getValue() != null) {
          update.set(key, entry.getValue());
        }
      }
    }
    update.set(GraphUpdateInfoKeys.lastUpdatedAt, System.currentTimeMillis());
  }

  private void updateGraphUpdateInfoV1(Update update, String moduleName, LinkedHashMap<String, Object> infoDoc) {
    for (Map.Entry<String, Object> entry : infoDoc.entrySet()) {
      String key = String.format(MODULE_INFO_UPDATE_KEY, moduleName, entry.getKey());
      processEntryRecursively(update, key, entry.getValue());
    }
    update.set(GraphUpdateInfoKeys.lastUpdatedAt, System.currentTimeMillis());
  }

  /**
   * Recursively processes module info entries for MongoDB update operations.
   * - Collections at any nesting level → $addToSet (accumulates values)
   * - Serialized objects (Maps with __recast) → recursively process fields
   * - Real maps (like stageInfoMap without __recast) → $set or recurse into values
   * - Other values → $set
   */
  @SuppressWarnings("unchecked")
  private void processEntryRecursively(Update update, String key, Object value) {
    if (value == null) {
      return;
    }

    if (Collection.class.isAssignableFrom(value.getClass())) {
      // Collections → $addToSet (works at ANY nesting level)
      Collection<Object> values = (Collection<Object>) value;
      update.addToSet(key).each(values);
    } else if (Map.class.isAssignableFrom(value.getClass())) {
      Map<String, Object> mapValue = (Map<String, Object>) value;

      // Check if this is a serialized object (has __recast key) or a real map (like stageInfoMap)
      if (mapValue.containsKey("__recast")) {
        // Preserve type metadata so nested objects can be deserialized back to concrete classes.
        update.set(key + ".__recast", mapValue.get("__recast"));
        // This is a serialized object - recursively process its fields
        // This enables nested objects like pipelineHeaderDataV1 to have their
        // inner collections properly use $addToSet
        for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
          if (!"__recast".equals(entry.getKey())) {
            String nestedKey = key + "." + entry.getKey();
            processEntryRecursively(update, nestedKey, entry.getValue());
          }
        }
      } else {
        // Real map (like stageInfoMap) - process individual entries
        // If entry value is a serialized object, recurse into it to allow field-level merging
        // Otherwise, $set the entire value
        for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
          String mapKey = key + "." + entry.getKey();
          Object entryValue = entry.getValue();

          // Check if the map entry's value is a serialized object (has __recast)
          // If so, recurse into it to enable field-level updates instead of full replacement
          if (isSerializedObject(entryValue)) {
            processEntryRecursively(update, mapKey, entryValue);
          } else {
            update.set(mapKey, entryValue);
          }
        }
      }
    } else {
      // Primitive or other types → $set
      update.set(key, value);
    }
  }

  /**
   * Checks if the given value is a serialized object (a Map containing __recast key).
   */
  @SuppressWarnings("unchecked")
  private boolean isSerializedObject(Object value) {
    if (value == null || !Map.class.isAssignableFrom(value.getClass())) {
      return false;
    }
    Map<String, Object> mapValue = (Map<String, Object>) value;
    return mapValue.containsKey("__recast");
  }

  private String getAccountId(String planExecutionId) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    PipelineExecutionSummaryEntity summaryEntity =
        pmsExecutionSummaryRepository.getPipelineExecutionSummaryWithProjections(
            criteria, Set.of(PlanExecutionSummaryKeys.accountId));
    return summaryEntity != null ? summaryEntity.getAccountId() : "";
  }
}
