/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ANINDITAA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filter.FilterType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionModeFilter;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.entity.PipelineExecutionFilterProperties;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class PipelineExecutionFilterPropertiesMapperTest extends CategoryTest {
  @InjectMocks PipelineExecutionFilterPropertiesMapper pipelineExecutionFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineExecutionFilterToEntity() {
    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .moduleProperties(ModulePropertiesDTO.builder()
                                  .cd(CDModulePropertiesDTO.builder().serviceDefinitionTypes("Kubernetes").build())
                                  .build())
            .pipelineName("pipeline1")
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1234L).build())
            .tags(Map.of("key1", "value1"))
            .filterType(FilterType.PIPELINEEXECUTION)
            .executionModeFilter(ExecutionModeFilter.DEFAULT)
            .triggerIdentifiers(List.of("triggerIdentifier"))
            .triggerTypes(List.of(TriggerType.WEBHOOK))
            .myDeployments(true)
            .repo("repo")
            .branchName("branch")
            .pipelineIdentifiers(Arrays.asList("pipeline1", "pipeline2"))
            .inputSetIdentifiers(Arrays.asList("inputSet1", "inputSet2"))
            .planExecutionIds(Arrays.asList("execution1", "execution2"))
            .inputSetIdentifiers(Arrays.asList("inputSet1", "inputSet2"))
            .build();

    PipelineExecutionFilterProperties pipelineExecutionFilterProperties =
        (PipelineExecutionFilterProperties) pipelineExecutionFilterPropertiesMapper.toEntity(
            pipelineExecutionFilterPropertiesDTO);
    assertNotNull(pipelineExecutionFilterProperties);
    assertEquals(pipelineExecutionFilterProperties.getTimeRange(), pipelineExecutionFilterProperties.getTimeRange());
    assertEquals(
        pipelineExecutionFilterProperties.getPipelineName(), pipelineExecutionFilterPropertiesDTO.getPipelineName());
    assertEquals(
        pipelineExecutionFilterProperties.getPipelineTags(), pipelineExecutionFilterPropertiesDTO.getPipelineTags());
    assertEquals(pipelineExecutionFilterProperties.getModuleProperties(),
        pipelineExecutionFilterPropertiesDTO.getModuleProperties());
    assertEquals(pipelineExecutionFilterProperties.getStatus(), pipelineExecutionFilterPropertiesDTO.getStatus());
    assertEquals(pipelineExecutionFilterProperties.getTags(),
        TagMapper.convertToList(pipelineExecutionFilterPropertiesDTO.getTags()));
    assertEquals(pipelineExecutionFilterProperties.getType(), pipelineExecutionFilterPropertiesDTO.getFilterType());
    assertEquals(pipelineExecutionFilterProperties.getExecutionModeFilter(),
        pipelineExecutionFilterPropertiesDTO.getExecutionModeFilter());
    assertEquals(pipelineExecutionFilterProperties.getTriggerIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getTriggerIdentifiers());
    assertEquals(
        pipelineExecutionFilterProperties.getTriggerTypes(), pipelineExecutionFilterPropertiesDTO.getTriggerTypes());
    assertEquals(
        pipelineExecutionFilterProperties.getMyDeployments(), pipelineExecutionFilterPropertiesDTO.getMyDeployments());
    assertEquals(pipelineExecutionFilterProperties.getRepo(), pipelineExecutionFilterPropertiesDTO.getRepo());
    assertEquals(
        pipelineExecutionFilterProperties.getBranchName(), pipelineExecutionFilterPropertiesDTO.getBranchName());
    assertEquals(pipelineExecutionFilterProperties.getPipelineIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getPipelineIdentifiers());
    assertEquals(pipelineExecutionFilterProperties.getInputSetIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getInputSetIdentifiers());
    assertEquals(pipelineExecutionFilterProperties.getPlanExecutionIds(),
        pipelineExecutionFilterPropertiesDTO.getPlanExecutionIds());
    assertEquals(pipelineExecutionFilterProperties.getInputSetIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getInputSetIdentifiers());
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineExecutionFilterWriteDTO() {
    PipelineExecutionFilterProperties pipelineExecutionFilterProperties =
        PipelineExecutionFilterProperties.builder()
            .moduleProperties(ModulePropertiesDTO.builder()
                                  .cd(CDModulePropertiesDTO.builder().serviceDefinitionTypes("Kubernetes").build())
                                  .build())
            .pipelineName("pipeline1")
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1234L).build())
            .tags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .type(FilterType.PIPELINEEXECUTION)
            .executionModeFilter(ExecutionModeFilter.DEFAULT)
            .triggerIdentifiers(List.of("triggerIdentifier"))
            .triggerTypes(List.of(TriggerType.WEBHOOK))
            .myDeployments(true)
            .repo("repo")
            .branchName("branch")
            .inputSetIdentifiers(Arrays.asList("inputSet1", "inputSet2"))
            .planExecutionIds(Arrays.asList("execution1", "execution2"))
            .inputSetIdentifiers(Arrays.asList("inputSet1", "inputSet2"))
            .build();

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        (PipelineExecutionFilterPropertiesDTO) pipelineExecutionFilterPropertiesMapper.writeDTO(
            pipelineExecutionFilterProperties);
    assertNotNull(pipelineExecutionFilterPropertiesDTO);
    assertEquals(
        pipelineExecutionFilterPropertiesDTO.getPipelineTags(), pipelineExecutionFilterProperties.getPipelineTags());
    assertEquals(
        pipelineExecutionFilterPropertiesDTO.getPipelineName(), pipelineExecutionFilterProperties.getPipelineName());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getModuleProperties(),
        pipelineExecutionFilterProperties.getModuleProperties());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getStatus(), pipelineExecutionFilterProperties.getStatus());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getTags(),
        TagMapper.convertToMap(pipelineExecutionFilterProperties.getTags()));
    assertEquals(pipelineExecutionFilterProperties.getTimeRange(), pipelineExecutionFilterProperties.getTimeRange());
    assertEquals(pipelineExecutionFilterProperties.getType(), pipelineExecutionFilterPropertiesDTO.getFilterType());
    assertEquals(pipelineExecutionFilterProperties.getExecutionModeFilter(),
        pipelineExecutionFilterPropertiesDTO.getExecutionModeFilter());
    assertEquals(pipelineExecutionFilterProperties.getTriggerIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getTriggerIdentifiers());
    assertEquals(
        pipelineExecutionFilterProperties.getTriggerTypes(), pipelineExecutionFilterPropertiesDTO.getTriggerTypes());
    assertEquals(
        pipelineExecutionFilterProperties.getMyDeployments(), pipelineExecutionFilterPropertiesDTO.getMyDeployments());
    assertEquals(pipelineExecutionFilterProperties.getRepo(), pipelineExecutionFilterPropertiesDTO.getRepo());
    assertEquals(
        pipelineExecutionFilterProperties.getBranchName(), pipelineExecutionFilterPropertiesDTO.getBranchName());
    assertEquals(pipelineExecutionFilterProperties.getPipelineIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getPipelineIdentifiers());
    assertEquals(pipelineExecutionFilterProperties.getInputSetIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getInputSetIdentifiers());
    assertEquals(pipelineExecutionFilterProperties.getPlanExecutionIds(),
        pipelineExecutionFilterPropertiesDTO.getPlanExecutionIds());
    assertEquals(pipelineExecutionFilterProperties.getInputSetIdentifiers(),
        pipelineExecutionFilterPropertiesDTO.getInputSetIdentifiers());
  }
}