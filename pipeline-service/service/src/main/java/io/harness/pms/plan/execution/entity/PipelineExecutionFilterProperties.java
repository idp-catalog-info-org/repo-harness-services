/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.entity;

import static io.harness.filter.FilterConstants.PIPELINE_SETUP_FILTER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.FilterType;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.FilterWithOperator;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.plan.execution.beans.dto.ExecutionModeFilter;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TRIGGERS, HarnessModuleComponent.CDS_PIPELINE})
@Value
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineExecutionFilterProperties")
@JsonTypeName(PIPELINE_SETUP_FILTER)
public class PipelineExecutionFilterProperties extends FilterProperties {
  private List<NGTag> pipelineTags;
  private FilterWithOperator<NGTag> pipelineTagsV2;
  private List<ExecutionStatus> status;
  private TimeRange timeRange;
  private String pipelineName;
  private ModulePropertiesDTO moduleProperties;
  private List<TriggerType> triggerTypes;
  private List<String> triggerIdentifiers;
  private ExecutionModeFilter executionModeFilter;
  private Boolean myDeployments;
  private String branchName;
  private String repo;
  private List<String> pipelineIdentifiers;
  private List<String> inputSetIdentifiers;
  private List<String> planExecutionIds;
  private List<String> executionNotes;

  @Builder
  public PipelineExecutionFilterProperties(List<NGTag> tags, FilterType type, List<NGTag> pipelineTags,
      FilterWithOperator<NGTag> pipelineTagsV2, List<ExecutionStatus> status, TimeRange timeRange, String pipelineName,
      ModulePropertiesDTO moduleProperties, List<TriggerType> triggerTypes, List<String> triggerIdentifiers,
      ExecutionModeFilter executionModeFilter, Boolean myDeployments, String branchName, String repo,
      List<String> pipelineIdentifiers, List<String> inputSetIdentifiers, List<String> planExecutionIds,
      List<String> executionNotes) {
    super(tags, type);
    this.pipelineTags = pipelineTags;
    this.pipelineTagsV2 = pipelineTagsV2;
    this.status = status;
    this.timeRange = timeRange;
    this.pipelineName = pipelineName;
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
