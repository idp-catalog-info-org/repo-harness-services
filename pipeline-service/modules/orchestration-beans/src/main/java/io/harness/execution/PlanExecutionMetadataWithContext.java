/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.IdentityExecutionContext;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.data.NGWorkflowType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Value;
import lombok.With;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

// PlanExecutionMetadataWithContext is created to pass down execution context without bloating up parameters
// Out of these, only planExecutionMetadata is persisted, rest all are ephemeral

@OwnedBy(PIPELINE)
@Value
@Builder(builderClassName = "Builder", toBuilder = true)
@AllArgsConstructor
@EqualsAndHashCode
@Slf4j
public class PlanExecutionMetadataWithContext implements PmsNodeExecutionMetadata {
  boolean isRetry;
  List<String> identifierOfSkipStages;
  @NonFinal @With String previousExecutionId;
  List<String> retryStagesIdentifier;
  boolean runAllStages;
  @NonFinal @With PlanExecutionMetadata planExecutionMetadata;
  @NonFinal String expandedPipelineJson;
  @NonFinal String pipelineYaml;
  @NonFinal private List<HeaderConfig> triggerHeader;
  @NonFinal private String triggerJsonPayload;
  @Setter @NonFinal @With private Long expressionFunctorToken;
  @NonFinal private TriggerPayload triggerPayload;
  @NonFinal private Map<String, Object> stageExpressionValuesMap;
  @NonFinal private StagesExecutionMetadata stagesExecutionMetadata;
  @NonFinal private String processedYaml;

  @Default private Boolean isDynamicExecution = false;
  @Default private Boolean isOriginalYamlUsedOnRerun = false;
  @Setter @NonFinal private String pipelineYamlWithTemplateRef;
  @Setter @NonFinal private List<String> inputSetIdentifiers;
  @Setter @NonFinal private String inputSetBranchName;
  @Setter @NonFinal private List<NGTag> tags;
  @Default private Boolean isAsyncPlanCreation = false;
  @NonFinal private NGWorkflowType workflowMode;

  @Default @NonFinal private List<PostExecutionRollbackInfo> postExecutionRollbackInfos = new ArrayList<>();

  // Ephemeral (not persisted): pipeline-level workload identities registered at plan-execution start
  // (behind PIPE_PIPELINE_IDENTITY). Seeded onto the root ambiance in OrchestrationServiceImpl.executePlan.
  @NonFinal @With private IdentityExecutionContext identityExecutionContext;
  @Override
  public NodeType forNodeType() {
    return NodeType.PLAN;
  }

  // Custom setters to ensure specific fields are only set once.
  // These setters log a warning if an attempt is made to set a value
  // more than once. This is to prevent potential inconsistencies and ensure the integrity of
  // the execution metadata.
  private boolean canSetField(Object currentValue, String fieldName) {
    if (currentValue != null) {
      log.warn(
          "{} can only be set once in PlanExecutionMetadataWithContext, current value: {}", fieldName, currentValue);
      return false;
    }
    return true;
  }

  public void setPlanExecutionMetadata(PlanExecutionMetadata planExecutionMetadata) {
    if (canSetField(this.planExecutionMetadata, "planExecutionMetadata")) {
      this.planExecutionMetadata = planExecutionMetadata;
    }
  }

  public void setExpandedPipelineJson(String expandedPipelineJson) {
    if (canSetField(this.expandedPipelineJson, "ExpandedPipelineJson")) {
      this.expandedPipelineJson = expandedPipelineJson;
    }
  }

  public void setPipelineYaml(String pipelineYaml) {
    if (canSetField(this.pipelineYaml, "PipelineYaml")) {
      this.pipelineYaml = pipelineYaml;
    }
  }

  public void setTriggerHeader(List<HeaderConfig> triggerHeader) {
    if (canSetField(this.triggerHeader, "TriggerHeader")) {
      this.triggerHeader = triggerHeader;
    }
  }

  public void setTriggerJsonPayload(String triggerJsonPayload) {
    if (canSetField(this.triggerJsonPayload, "TriggerJsonPayload")) {
      this.triggerJsonPayload = triggerJsonPayload;
    }
  }

  public void setTriggerPayload(TriggerPayload triggerPayload) {
    if (canSetField(this.triggerPayload, "TriggerPayload")) {
      this.triggerPayload = triggerPayload;
    }
  }

  public void setStageExpressionValuesMap(Map<String, Object> stageExpressionValuesMap) {
    if (canSetField(this.stageExpressionValuesMap, "StageExpressionValuesMap")) {
      this.stageExpressionValuesMap = stageExpressionValuesMap;
    }
  }

  public void setStagesExecutionMetadata(StagesExecutionMetadata stagesExecutionMetadata) {
    if (canSetField(this.stagesExecutionMetadata, "StagesExecutionMetadata")) {
      this.stagesExecutionMetadata = stagesExecutionMetadata;
    }
  }

  public void setProcessedYaml(String processedYaml) {
    if (canSetField(this.processedYaml, "ProcessedYaml")) {
      this.processedYaml = processedYaml;
    }
  }

  public void setIdentityExecutionContext(IdentityExecutionContext identityExecutionContext) {
    if (canSetField(this.identityExecutionContext, "identityExecutionContext")) {
      this.identityExecutionContext = identityExecutionContext;
    }
  }
}