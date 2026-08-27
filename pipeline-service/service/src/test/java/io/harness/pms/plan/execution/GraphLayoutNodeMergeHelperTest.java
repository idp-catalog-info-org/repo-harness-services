/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GraphLayoutNodeMergeHelperTest extends CategoryTest {
  @Mock GraphGenerationService graphGenerationService;
  GraphLayoutNodeMergeHelper mergeHelper;

  String accountId = "accountId";
  String planExecutionId = "planExecId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    mergeHelper = new GraphLayoutNodeMergeHelper(graphGenerationService);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeLayoutNodes_noOpWhenPgReturnsNull() {
    when(graphGenerationService.getStageLayoutNodesFromPostgres(accountId, planExecutionId)).thenReturn(null);
    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().build();
    Map<String, GraphLayoutNodeDTO> originalMap = new HashMap<>();
    originalMap.put("node1", GraphLayoutNodeDTO.builder().nodeType("Deployment").build());
    entity.setLayoutNodeMap(originalMap);

    mergeHelper.mergeLayoutNodes(accountId, planExecutionId, entity);

    assertThat(entity.getLayoutNodeMap()).isEqualTo(originalMap);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeLayoutNodes_copiesNodeTypeFromMongo() {
    Map<String, GraphLayoutNodeDTO> pgMap = new HashMap<>();
    pgMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").build());
    when(graphGenerationService.getStageLayoutNodesFromPostgres(accountId, planExecutionId)).thenReturn(pgMap);

    Map<String, GraphLayoutNodeDTO> mongoMap = new HashMap<>();
    mongoMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").nodeType("Deployment").build());

    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().layoutNodeMap(mongoMap).build();

    mergeHelper.mergeLayoutNodes(accountId, planExecutionId, entity);

    assertThat(entity.getLayoutNodeMap().get("node1").getNodeType()).isEqualTo("Deployment");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeLayoutNodes_preservesMongoOnlyNodes() {
    Map<String, GraphLayoutNodeDTO> pgMap = new HashMap<>();
    pgMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").build());
    when(graphGenerationService.getStageLayoutNodesFromPostgres(accountId, planExecutionId)).thenReturn(pgMap);

    Map<String, GraphLayoutNodeDTO> mongoMap = new HashMap<>();
    mongoMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").nodeType("Deployment").build());
    mongoMap.put("node2", GraphLayoutNodeDTO.builder().name("stage2").nodeType("Approval").build());

    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().layoutNodeMap(mongoMap).build();

    mergeHelper.mergeLayoutNodes(accountId, planExecutionId, entity);

    assertThat(entity.getLayoutNodeMap()).containsKey("node1");
    assertThat(entity.getLayoutNodeMap()).containsKey("node2");
    assertThat(entity.getLayoutNodeMap().get("node2").getNodeType()).isEqualTo("Approval");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeLayoutNodes_pgOnlyNodesKeptAsIs() {
    Map<String, GraphLayoutNodeDTO> pgMap = new HashMap<>();
    pgMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").build());
    pgMap.put("node3", GraphLayoutNodeDTO.builder().name("stage3").build());
    when(graphGenerationService.getStageLayoutNodesFromPostgres(accountId, planExecutionId)).thenReturn(pgMap);

    Map<String, GraphLayoutNodeDTO> mongoMap = new HashMap<>();
    mongoMap.put("node1", GraphLayoutNodeDTO.builder().name("stage1").nodeType("Deployment").build());

    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().layoutNodeMap(mongoMap).build();

    mergeHelper.mergeLayoutNodes(accountId, planExecutionId, entity);

    assertThat(entity.getLayoutNodeMap()).containsKey("node3");
    assertThat(entity.getLayoutNodeMap().get("node3").getName()).isEqualTo("stage3");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeModuleInfo_setsModuleInfoFromPg() {
    Map<String, Object> pgModuleInfo = new HashMap<>();
    Map<String, Object> cdInfo = new HashMap<>();
    cdInfo.put("serviceIdentifiers", "svc1");
    pgModuleInfo.put("cd", cdInfo);
    when(graphGenerationService.getPipelineModuleInfoFromPostgres(planExecutionId)).thenReturn(pgModuleInfo);

    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().build();

    mergeHelper.mergeModuleInfo(planExecutionId, entity);

    assertThat(entity.getModuleInfo()).isNotNull();
    assertThat(entity.getModuleInfo()).containsKey("cd");
    assertThat(entity.getModuleInfo().get("cd").get("serviceIdentifiers")).isEqualTo("svc1");
  }
}
