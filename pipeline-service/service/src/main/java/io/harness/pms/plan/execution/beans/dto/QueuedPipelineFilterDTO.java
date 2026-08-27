/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.filter.FilterConstants.QUEUED_PIPELINE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PriorityType;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(QUEUED_PIPELINE_FILTER)
@Schema(name = "QueuedPipelineFilter",
    description = "Filter criteria for listing queued pipeline executions. Pass inline as the request body,"
        + " or save via POST /filters with filterType `QueuedPipeline` and reference by filterIdentifier.")
public class QueuedPipelineFilterDTO extends FilterPropertiesDTO {
  @Schema(description = "Filter by organization identifiers") List<String> orgIdentifiers;
  @Schema(description = "Filter by project identifiers") List<String> projectIdentifiers;
  @Schema(description = "Filter by pipeline identifiers") List<String> pipelineIdentifiers;
  @Schema(description = "Sub-filter within queued statuses") List<ExecutionStatus> statuses;
  @Schema(description = "Filter by priority types (HIGH, LOW, NORMAL)") List<PriorityType> priorityTypes;
  @Schema(description = "Filter by trigger types") List<TriggerType> triggerTypes;
  @Schema(description = "Filter by pipeline tags") List<NGTag> pipelineTags;
  @Schema(description = "Filter by queued time window") TimeRange queuedTimeRange;

  @Override
  public FilterType getFilterType() {
    return FilterType.QUEUED_PIPELINE;
  }

  @Builder
  public QueuedPipelineFilterDTO(Map<String, String> tags, Map<String, String> labels, FilterType filterType,
      List<String> orgIdentifiers, List<String> projectIdentifiers, List<String> pipelineIdentifiers,
      List<ExecutionStatus> statuses, List<PriorityType> priorityTypes, List<TriggerType> triggerTypes,
      List<NGTag> pipelineTags, TimeRange queuedTimeRange) {
    super(tags, labels, filterType);
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
