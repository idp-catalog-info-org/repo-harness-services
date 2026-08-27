/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static java.util.Objects.nonNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.triggers.TriggerPayload;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PlanExecutionMigrationHelper {
  public static String readTriggerJsonPayloadWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getTriggerJsonPayload())) {
        return planExecution.getTriggerJsonPayload();
      }
      if (nonNull(planExecutionMetadata.getTriggerJsonPayload())) {
        logDisparity(PlanExecutionKeys.triggerJsonPayload, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getTriggerJsonPayload();
  }

  public static TriggerPayload readTriggerPayloadWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getTriggerPayload())) {
        return planExecution.getTriggerPayload();
      }
      if (nonNull(planExecutionMetadata.getTriggerPayload())) {
        logDisparity(PlanExecutionKeys.triggerPayload, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getTriggerPayload();
  }

  public static StagesExecutionMetadata readStagesExecutionMetadataWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getStagesExecutionMetadata())) {
        return planExecution.getStagesExecutionMetadata();
      }
      if (nonNull(planExecutionMetadata.getStagesExecutionMetadata())) {
        logDisparity(PlanExecutionKeys.stagesExecutionMetadata, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getStagesExecutionMetadata();
  }

  public static Long readExpressionFunctorTokenWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getExpressionFunctorToken())) {
        return planExecution.getExpressionFunctorToken();
      }
      if (nonNull(planExecutionMetadata.getExpressionFunctorToken())) {
        logDisparity(PlanExecutionKeys.expressionFunctorToken, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getExpressionFunctorToken();
  }

  public static List<HeaderConfig> readTriggerHeaderWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getTriggerHeader())) {
        return planExecution.getTriggerHeader();
      }
      if (nonNull(planExecutionMetadata.getTriggerHeader())) {
        logDisparity(PlanExecutionKeys.triggerHeader, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getTriggerHeader();
  }

  public static Map<String, Object> readStageExpressionValuesMapWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getStageExpressionValuesMap())) {
        return planExecution.getStageExpressionValuesMap();
      }
      if (nonNull(planExecutionMetadata.getStageExpressionValuesMap())) {
        logDisparity(PlanExecutionKeys.stageExpressionValuesMap, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getStageExpressionValuesMap();
  }

  public static List<PostExecutionRollbackInfo> readPostExecutionRollbackInfoWithFallbackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getPostExecutionRollbackInfos())) {
        return planExecution.getPostExecutionRollbackInfos();
      }
      if (nonNull(planExecutionMetadata.getPostExecutionRollbackInfos())) {
        logDisparity(PlanExecutionKeys.postExecutionRollbackInfos, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getPostExecutionRollbackInfos();
  }

  public static String readProcessedYamlWithFallBackOnMetadata(
      PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution) {
    if (nonNull(planExecution)) {
      if (nonNull(planExecution.getProcessedYaml())) {
        return planExecution.getProcessedYaml();
      }
      if (nonNull(planExecutionMetadata.getProcessedYaml())) {
        logDisparity(PlanExecutionKeys.processedYaml, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getProcessedYaml();
  }

  private static void logDisparity(String key, String planExecutionId) {
    // This is an unexpected state, and we need to debug this if we reach it. Fallback on prevMetadata.
    log.warn("{} Disparity detected between previous planExecution and planExecutionMetadata for planExecutionId : {}",
        key, planExecutionId);
  }
}
