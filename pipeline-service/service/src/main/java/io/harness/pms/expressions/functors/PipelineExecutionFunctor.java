/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.execution.ExecutionModeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class PipelineExecutionFunctor implements LateBindingValue {
  private final PMSExecutionService pmsExecutionService;
  PipelineExpressionHelper pipelineExpressionHelper;

  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final Ambiance ambiance;

  public PipelineExecutionFunctor(PMSExecutionService pmsExecutionService,
      PipelineExpressionHelper pipelineExpressionHelper, PlanExecutionMetadataService planExecutionMetadataService,
      Ambiance ambiance) {
    this.pmsExecutionService = pmsExecutionService;
    this.pipelineExpressionHelper = pipelineExpressionHelper;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());

    boolean isRollbackProcessed = false;
    return getAllExpressions(pipelineExecutionSummaryEntity, isRollbackProcessed);
  }

  private Map<String, Object> getAllExpressions(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, boolean isRollbackProcessed) {
    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("name", pipelineExecutionSummaryEntity.getName());
    jsonObject.put("identifier", pipelineExecutionSummaryEntity.getPipelineIdentifier());
    jsonObject.put("tags", pipelineExecutionSummaryEntity.getTags());
    jsonObject.put("triggerType", pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggerType().toString());

    // Treating UNDEFINED_MODE as NORMAL mode
    if (ExecutionMode.UNDEFINED_MODE.equals(pipelineExecutionSummaryEntity.getExecutionMode().toString())) {
      jsonObject.put("executionMode", ExecutionMode.NORMAL.toString());
    } else {
      jsonObject.put("executionMode", pipelineExecutionSummaryEntity.getExecutionMode().toString());
    }

    Map<String, String> triggeredByMap = new HashMap<>();
    triggeredByMap.put(
        "name", pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getIdentifier());
    triggeredByMap.put("email",
        pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email"));
    String triggerIdentifier =
        pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getTriggerIdentifier();
    String triggerName = pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getTriggerName();
    triggeredByMap.put("triggerIdentifier", isNotEmpty(triggerIdentifier) ? triggerIdentifier : null);
    triggeredByMap.put("triggerDisplayName", isNotEmpty(triggerName) ? triggerName : null);
    jsonObject.put("triggeredBy", triggeredByMap);

    // Removed run sequence From PipelineStepParameter as run sequence is set just before start of execution and not
    // during plan creation
    jsonObject.put("sequenceId", pipelineExecutionSummaryEntity.getRunSequence());

    // Branch-scoped build sequence ID (CI-19987)
    // Returns null if not available (e.g., tag builds, commit SHA builds, manual execution without branch context)
    long branchSeqId = ambiance.getMetadata().getBranchSeqId();
    jsonObject.put("branchSeqId", branchSeqId > 0 ? branchSeqId : null);

    jsonObject.put(
        "resumedExecutionId", pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    jsonObject.put("storeType",
        pipelineExecutionSummaryEntity.getStoreType() != null ? pipelineExecutionSummaryEntity.getStoreType()
                                                              : StoreType.INLINE);
    if (pipelineExecutionSummaryEntity.getEntityGitDetails() != null) {
      jsonObject.put("branch", pipelineExecutionSummaryEntity.getEntityGitDetails().getBranch());
      jsonObject.put("repo", pipelineExecutionSummaryEntity.getEntityGitDetails().getRepoName());
    }

    // block to add selected stages identifier
    try {
      // If Selective stage execution is allowed, add from StagesExecutionMetadata
      if (pipelineExecutionSummaryEntity.getAllowStagesExecution() != null
          && pipelineExecutionSummaryEntity.getAllowStagesExecution()) {
        jsonObject.put(
            "selectedStages", pipelineExecutionSummaryEntity.getStagesExecutionMetadata().getStageIdentifiers());
      } else {
        Optional<PlanExecutionMetadata> planExecutionMetadata = planExecutionMetadataService.findByPlanExecutionId(
            AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());

        if (planExecutionMetadata.isPresent()) {
          String pipelineYaml = planExecutionMetadata.get().getYaml();
          List<String> stageIdentifiers;
          if (HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))) {
            stageIdentifiers = StageExecutionSelectorHelper.getStageInfoListV1(pipelineYaml)
                                   .stream()
                                   .map(BasicStageInfo::getIdentifier)
                                   .collect(Collectors.toList());
          } else {
            stageIdentifiers = YamlUtils.extractStageFieldsFromPipeline(pipelineYaml)
                                   .stream()
                                   .map(stageField -> stageField.getNode().getField("identifier").getNode().asText())
                                   .collect(Collectors.toList());
          }
          jsonObject.put("selectedStages", stageIdentifiers);
        }
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Failed to fetch selected stages");
    }
    addExecutionUrlMap(jsonObject);
    addParentPipelineMap(jsonObject);

    // Process rollback execution only once using boolean flag
    if (!isRollbackProcessed && ExecutionModeUtils.isRollbackMode(pipelineExecutionSummaryEntity.getExecutionMode())) {
      processOriginalExecutionForRollbackMode(jsonObject);
    }

    return jsonObject;
  }

  // Added support for Fetching originalExecution details from Pipeline/Post Prod Rollback Execution
  private void processOriginalExecutionForRollbackMode(Map<String, Object> jsonObject) {
    if (null == ambiance) {
      return;
    }
    String originalExecutionForRollbackMode = ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();

    if (isNotEmpty(originalExecutionForRollbackMode)
        && !ambiance.getPlanExecutionId().equals(originalExecutionForRollbackMode)) {
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntityForOriginalExecution =
          pmsExecutionService.getPipelineExecutionSummaryEntity(
              AmbianceUtils.getAccountId(ambiance), originalExecutionForRollbackMode);

      Map<String, Object> originalExecution =
          getAllExpressions(pipelineExecutionSummaryEntityForOriginalExecution, true);

      originalExecution.put("executionId", originalExecutionForRollbackMode);
      originalExecution.put("startTs", pipelineExecutionSummaryEntityForOriginalExecution.getStartTs());
      originalExecution.put("endTs", pipelineExecutionSummaryEntityForOriginalExecution.getEndTs());
      originalExecution.put("status", pipelineExecutionSummaryEntityForOriginalExecution.getStatus());

      jsonObject.put("originalExecution", originalExecution);
    }
  }

  private void addParentPipelineMap(Map<String, Object> jsonObject) {
    if (ambiance == null || !ambiance.hasMetadata()) {
      return;
    }
    PipelineStageInfo pipelineStageInfo = ambiance.getMetadata().getPipelineStageInfo();
    if (pipelineStageInfo.getHasParentPipeline()) {
      Map<String, Object> parentPipelineMap = new HashMap<>();
      parentPipelineMap.put("identifier", pipelineStageInfo.getIdentifier());
      parentPipelineMap.put("name", pipelineStageInfo.getPipelineName());
      parentPipelineMap.put("executionId", pipelineStageInfo.getExecutionId());
      parentPipelineMap.put("stageNodeId", pipelineStageInfo.getStageNodeId());
      parentPipelineMap.put("projectId", pipelineStageInfo.getProjectId());
      parentPipelineMap.put("orgId", pipelineStageInfo.getOrgId());
      parentPipelineMap.put("runSequence", pipelineStageInfo.getRunSequence());
      jsonObject.put("parentPipeline", parentPipelineMap);
    }
  }

  private void addExecutionUrlMap(Map<String, Object> jsonObject) {
    Map<String, String> executionMap = new HashMap<>();
    String pipelineExecutionUrl = pipelineExpressionHelper.generateUrl(ambiance, null);
    executionMap.put("url", pipelineExecutionUrl);
    jsonObject.put("execution", executionMap);
    jsonObject.put(OrchestrationConstants.EXECUTION_URL, pipelineExecutionUrl);
  }
}
