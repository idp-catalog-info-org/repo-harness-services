/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.filter.FilterConstants.PIPELINE_EXECUTION_FILTER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.FilterWithOperator;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.yaml.core.NGLabel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineExecutionFilterProperties")
@JsonTypeName(PIPELINE_EXECUTION_FILTER)
@Schema(name = "PipelineExecutionFilterProperties",
    description = "Filter properties for listing pipeline executions. The `filterType` field (inherited) is required"
        + " and must be set to `PipelineExecution`.")
public class PipelineExecutionFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "Filter executions by pipeline-level tags (key-value pairs).") private List<NGTag> pipelineTags;
  @Valid
  @Schema(description = "Filter executions by pipeline-level tags with AND/OR operator.")
  private FilterWithOperator<NGTag> pipelineTagsV2;
  @Schema(description = "Filter executions by pipeline-level labels (key-value pairs).")
  private List<NGLabel> pipelineLabels;
  @Schema(description = "Filter executions by execution status. Accepts a list of status values such as: Running,"
          + " Success, Failed, Aborted, Expired, etc. Uses PascalCase format (e.g. 'Success', not 'SUCCESS').")
  private List<ExecutionStatus> status;
  @Schema(description = "Filter executions by pipeline name (partial match supported).") private String pipelineName;
  @Schema(description = "Filter executions within a specific time range.") private TimeRange timeRange;
  @Schema(description = "Module-specific filter properties (e.g. CD service/environment filters,"
          + " CI build event filters).")
  private ModulePropertiesDTO moduleProperties;
  @Schema(description = "Filter executions by trigger type. Examples: MANUAL, WEBHOOK, SCHEDULER_CRON, etc.")
  private List<TriggerType> triggerTypes;
  @Schema(description = "Filter executions by trigger identifiers.") private List<String> triggerIdentifiers;
  @Schema(description = "Filter by execution mode (e.g. default executions only, rollback executions only, or all).")
  private ExecutionModeFilter executionModeFilter;
  @Schema(description = "Filter executions by a list of pipeline identifiers.")
  private List<String> pipelineIdentifiers;
  @Schema(description = "If true, returns only executions triggered by the current authenticated user.")
  private Boolean myDeployments;
  @Schema(description = "Filter executions by the codebase/repository branch used during execution."
          + " This is different from the `branch` query parameter, which refers to the Git branch where the"
          + " pipeline YAML definition is stored (for Git Experience / remote pipelines).")
  private String branchName;
  @Schema(description = "Filter executions by the repository name associated with the execution.") private String repo;
  @Schema(description = "Filter executions by input set identifiers used during execution.")
  private List<String> inputSetIdentifiers;
  @Schema(description = "Filter by specific plan execution IDs.") private List<String> planExecutionIds;
  @Schema(description = "Filter executions by execution notes content.") private List<String> executionNotes;

  @Override
  public FilterType getFilterType() {
    return FilterType.PIPELINEEXECUTION;
  }

  @Builder
  public PipelineExecutionFilterPropertiesDTO(Map<String, String> tags, Map<String, String> labels,
      FilterType filterType, List<NGTag> pipelineTags, FilterWithOperator<NGTag> pipelineTagsV2,
      List<NGLabel> pipelineLabels, List<ExecutionStatus> status, String pipelineName, TimeRange timeRange,
      ModulePropertiesDTO moduleProperties, List<TriggerType> triggerTypes, List<String> triggerIdentifiers,
      ExecutionModeFilter executionModeFilter, Boolean myDeployments, String branchName, String repo,
      List<String> pipelineIdentifiers, List<String> inputSetIdentifiers, List<String> planExecutionIds,
      List<String> executionNotes) {
    super(tags, labels, filterType);
    this.pipelineTags = pipelineTags;
    this.pipelineTagsV2 = pipelineTagsV2;
    this.pipelineLabels = pipelineLabels;
    this.status = status;
    this.pipelineName = pipelineName;
    this.timeRange = timeRange;
    this.moduleProperties = moduleProperties;
    this.triggerTypes = triggerTypes;
    this.triggerIdentifiers = triggerIdentifiers;
    this.executionModeFilter = executionModeFilter;
    this.myDeployments = myDeployments;
    this.branchName = branchName;
    this.repo = repo;
    this.pipelineIdentifiers = pipelineIdentifiers;
    this.inputSetIdentifiers = inputSetIdentifiers;
    this.planExecutionIds = planExecutionIds;
    this.executionNotes = executionNotes;
  }
}
