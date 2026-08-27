/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.entity.PipelineExecutionFilterProperties;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PipelineExecutionFilterPropertiesMapper
    implements FilterPropertiesMapper<PipelineExecutionFilterPropertiesDTO, PipelineExecutionFilterProperties> {
  @Override
  public FilterPropertiesDTO writeDTO(FilterProperties pipelineExecutionFilterProperties) {
    PipelineExecutionFilterProperties executionFilterProperties =
        (PipelineExecutionFilterProperties) pipelineExecutionFilterProperties;
    return PipelineExecutionFilterPropertiesDTO.builder()
        .moduleProperties(executionFilterProperties.getModuleProperties())
        .pipelineName(executionFilterProperties.getPipelineName())
        .pipelineTags(executionFilterProperties.getPipelineTags())
        .status(executionFilterProperties.getStatus())
        .timeRange(executionFilterProperties.getTimeRange())
        .tags(TagMapper.convertToMap(pipelineExecutionFilterProperties.getTags()))
        .executionModeFilter(executionFilterProperties.getExecutionModeFilter())
        .triggerTypes(executionFilterProperties.getTriggerTypes())
        .triggerIdentifiers(executionFilterProperties.getTriggerIdentifiers())
        .myDeployments(executionFilterProperties.getMyDeployments())
        .pipelineIdentifiers(executionFilterProperties.getPipelineIdentifiers())
        .repo(executionFilterProperties.getRepo())
        .branchName(executionFilterProperties.getBranchName())
        .inputSetIdentifiers(executionFilterProperties.getInputSetIdentifiers())
        .planExecutionIds(executionFilterProperties.getPlanExecutionIds())
        .executionNotes(executionFilterProperties.getExecutionNotes())
        .pipelineTagsV2(executionFilterProperties.getPipelineTagsV2())
        .build();
  }

  @Override
  public FilterProperties toEntity(FilterPropertiesDTO pipelineExecutionFilterPropertiesDTO) {
    PipelineExecutionFilterPropertiesDTO executionFilterPropertiesDTO =
        (PipelineExecutionFilterPropertiesDTO) pipelineExecutionFilterPropertiesDTO;
    return PipelineExecutionFilterProperties.builder()
        .moduleProperties(executionFilterPropertiesDTO.getModuleProperties())
        .pipelineName(executionFilterPropertiesDTO.getPipelineName())
        .pipelineTags(executionFilterPropertiesDTO.getPipelineTags())
        .status(executionFilterPropertiesDTO.getStatus())
        .timeRange(executionFilterPropertiesDTO.getTimeRange())
        .tags(TagMapper.convertToList(pipelineExecutionFilterPropertiesDTO.getTags()))
        .type(pipelineExecutionFilterPropertiesDTO.getFilterType())
        .executionModeFilter(executionFilterPropertiesDTO.getExecutionModeFilter())
        .triggerTypes(executionFilterPropertiesDTO.getTriggerTypes())
        .triggerIdentifiers(executionFilterPropertiesDTO.getTriggerIdentifiers())
        .myDeployments(executionFilterPropertiesDTO.getMyDeployments())
        .repo(executionFilterPropertiesDTO.getRepo())
        .branchName(executionFilterPropertiesDTO.getBranchName())
        .pipelineIdentifiers(executionFilterPropertiesDTO.getPipelineIdentifiers())
        .inputSetIdentifiers(executionFilterPropertiesDTO.getInputSetIdentifiers())
        .planExecutionIds(executionFilterPropertiesDTO.getPlanExecutionIds())
        .executionNotes(executionFilterPropertiesDTO.getExecutionNotes())
        .pipelineTagsV2(executionFilterPropertiesDTO.getPipelineTagsV2())
        .build();
  }
}
