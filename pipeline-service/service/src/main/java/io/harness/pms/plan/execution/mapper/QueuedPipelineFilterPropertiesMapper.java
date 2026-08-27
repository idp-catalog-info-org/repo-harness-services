/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.plan.execution.beans.dto.QueuedPipelineFilterDTO;
import io.harness.pms.plan.execution.entity.QueuedPipelineFilterProperties;

@OwnedBy(PIPELINE)
public class QueuedPipelineFilterPropertiesMapper
    implements FilterPropertiesMapper<QueuedPipelineFilterDTO, QueuedPipelineFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties filterProperties) {
    QueuedPipelineFilterProperties entity = (QueuedPipelineFilterProperties) filterProperties;
    return QueuedPipelineFilterDTO.builder()
        .tags(TagMapper.convertToMap(entity.getTags()))
        .orgIdentifiers(entity.getOrgIdentifiers())
        .projectIdentifiers(entity.getProjectIdentifiers())
        .pipelineIdentifiers(entity.getPipelineIdentifiers())
        .statuses(entity.getStatuses())
        .priorityTypes(entity.getPriorityTypes())
        .triggerTypes(entity.getTriggerTypes())
        .pipelineTags(entity.getPipelineTags())
        .queuedTimeRange(entity.getQueuedTimeRange())
        .build();
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO filterPropertiesDTO) {
    QueuedPipelineFilterDTO dto = (QueuedPipelineFilterDTO) filterPropertiesDTO;
    return QueuedPipelineFilterProperties.builder()
        .tags(TagMapper.convertToList(dto.getTags()))
        .type(dto.getFilterType())
        .orgIdentifiers(dto.getOrgIdentifiers())
        .projectIdentifiers(dto.getProjectIdentifiers())
        .pipelineIdentifiers(dto.getPipelineIdentifiers())
        .statuses(dto.getStatuses())
        .priorityTypes(dto.getPriorityTypes())
        .triggerTypes(dto.getTriggerTypes())
        .pipelineTags(dto.getPipelineTags())
        .queuedTimeRange(dto.getQueuedTimeRange())
        .build();
  }
}
