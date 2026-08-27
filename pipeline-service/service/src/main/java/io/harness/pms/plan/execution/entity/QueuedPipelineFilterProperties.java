/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterConstants.QUEUED_PIPELINE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PriorityType;
import io.harness.filter.FilterType;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("QueuedPipelineFilterProperties")
@JsonTypeName(QUEUED_PIPELINE_FILTER)
public class QueuedPipelineFilterProperties extends FilterProperties {
  private List<String> orgIdentifiers;
  private List<String> projectIdentifiers;
  private List<String> pipelineIdentifiers;
  private List<ExecutionStatus> statuses;
  private List<PriorityType> priorityTypes;
  private List<TriggerType> triggerTypes;
  private List<NGTag> pipelineTags;
  private TimeRange queuedTimeRange;

  @Builder
  public QueuedPipelineFilterProperties(List<NGTag> tags, FilterType type, List<String> orgIdentifiers,
      List<String> projectIdentifiers, List<String> pipelineIdentifiers, List<ExecutionStatus> statuses,
      List<PriorityType> priorityTypes, List<TriggerType> triggerTypes, List<NGTag> pipelineTags,
      TimeRange queuedTimeRange) {
    super(tags, type);
    this.orgIdentifiers = orgIdentifiers;
    this.projectIdentifiers = projectIdentifiers;
    this.pipelineIdentifiers = pipelineIdentifiers;
    this.statuses = statuses;
    this.priorityTypes = priorityTypes;
    this.triggerTypes = triggerTypes;
    this.pipelineTags = pipelineTags;
    this.queuedTimeRange = queuedTimeRange;
  }
}
