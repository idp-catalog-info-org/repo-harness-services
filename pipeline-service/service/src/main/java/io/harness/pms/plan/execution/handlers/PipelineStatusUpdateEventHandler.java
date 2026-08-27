/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.observer.OrchestrationObserverUtils;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryServiceImpl;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineStatusUpdateEventHandler implements PlanStatusUpdateObserver, OrchestrationEndObserver {
  private final PlanExecutionService planExecutionService;
  private final PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  private OrchestrationEventEmitter eventEmitter;
  private WaitNotifyEngine waitNotifyEngine;

  private PmsExecutionSummaryServiceImpl pmsExecutionSummaryServiceImpl;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private GraphUpdateInfoRepository graphUpdateInfoRepository;

  @Inject
  public PipelineStatusUpdateEventHandler(PlanExecutionService planExecutionService,
      PmsExecutionSummaryRepository pmsExecutionSummaryRepository, OrchestrationEventEmitter eventEmitter,
      WaitNotifyEngine waitNotifyEngine, PmsExecutionSummaryServiceImpl pmsExecutionSummaryServiceImpl,
      PmsFeatureFlagHelper pmsFeatureFlagHelper, GraphUpdateInfoRepository graphUpdateInfoRepository) {
    this.planExecutionService = planExecutionService;
    this.pmsExecutionSummaryRepository = pmsExecutionSummaryRepository;
    this.eventEmitter = eventEmitter;
    this.waitNotifyEngine = waitNotifyEngine;
    this.pmsExecutionSummaryServiceImpl = pmsExecutionSummaryServiceImpl;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.graphUpdateInfoRepository = graphUpdateInfoRepository;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    // When CDC graph is enabled, status updates on PipelineExecutionSummaryEntity are handled by the CDC consumer
    // via updateStatusFromCDC. Skip here to avoid duplicate updates and ES race conditions.
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_USE_CDC_BASED_GRAPH.name())) {
      return;
    }
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(planExecutionId);
    Status status = planExecution.getStatus();
    if (StatusUtils.waitingStatuses().contains(status)) {
      pmsExecutionSummaryServiceImpl.updatePlanExecutionSummaryStatus(planExecutionId, planExecution);
    }
  }

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(
        ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.endTs, PlanExecutionKeys.status));
    // todo: remove executedModules from summary.
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntity =
        pmsExecutionSummaryRepository.findByPlanExecutionIdAndPipelineDeletedNot(ambiance.getPlanExecutionId(), true);
    if (pipelineExecutionSummaryEntity.isPresent()) {
      Set<String> executedModules =
          OrchestrationObserverUtils.getExecutedModulesInPipeline(pipelineExecutionSummaryEntity.get());
      Update update = new Update();
      update.set(PlanExecutionSummaryKeys.executedModules, executedModules);
      Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(ambiance.getPlanExecutionId());
      Query query = new Query(criteria);
      PipelineExecutionSummaryEntity pipelineExecutionSummaryUpdatedEntity =
          pmsExecutionSummaryRepository.update(query, update);
      // This producer is not sending all pipeline_finished events to the modules used in the pipeline because it
      // filters by layoutNode with a final status. There are cases where the status is updated after this producer
      // runs. As a best practice, a producer should not filter events; the consumers should be responsible for
      // determining whether to process an event or not. This action resolves the issue of missing events sent to the CI
      // module.
      // TODO: send all pipeline_finished events to all modules
      emitEventToCIModule(ambiance, planExecution, executedModules);
      for (String module : executedModules) {
        if (!module.equalsIgnoreCase(ModuleType.PMS.name())) {
          eventEmitter.emitEvent(buildEndEvent(ambiance, module,
              ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus(),
              pipelineExecutionSummaryUpdatedEntity.getModuleInfo().get(module), planExecution.getEndTs()));
        }
      }
    }
  }

  private OrchestrationEvent buildEndEvent(
      Ambiance ambiance, String module, Status status, Document moduleInfo, long endTs) {
    return OrchestrationEvent.newBuilder()
        .setAmbiance(ambiance)
        .setServiceName(module)
        .setEventType(OrchestrationEventType.ORCHESTRATION_END)
        .setModuleInfo(ByteString.copyFromUtf8(emptyIfNull(RecastOrchestrationUtils.toJson(moduleInfo))))
        .setStatus(status)
        .setEndTs(endTs)
        .build();
  }

  private void emitEventToCIModule(Ambiance ambiance, PlanExecution planExecution, Set<String> executedModules) {
    try {
      Optional<GraphUpdateInfo> graphUpdateInfoOptional =
          graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
              ambiance.getPlanExecutionId(), StepCategory.PIPELINE);
      if (graphUpdateInfoOptional.isPresent()) {
        Map<String, LinkedHashMap<String, Object>> moduleInfo =
            graphUpdateInfoOptional.get().getExecutionSummaryUpdateInfo().getModuleInfo();
        if (moduleInfo.get("ci") != null) {
          eventEmitter.emitEvent(buildEndEvent(ambiance, "ci",
              ExecutionStatus.getExecutionStatus(planExecution.getStatus()).getEngineStatus(),
              new Document(moduleInfo.get("ci")), planExecution.getEndTs()));
          executedModules.remove("ci"); // ensure the pipeline_finish event is not emitter twice to ci.
        }
      } else {
        log.warn("Module info not found for planExecutionId {}", ambiance.getPlanExecutionId());
      }
    } catch (Exception e) {
      log.error("Retrieve Module info failed for plan ExecutionId {}", ambiance.getPlanExecutionId(), e);
    }
  }
}
