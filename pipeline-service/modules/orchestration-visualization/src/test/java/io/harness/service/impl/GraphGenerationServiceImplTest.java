/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.converter.GraphVertexConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.cache.EntityWithAccountId;
import io.harness.cache.SpringCacheEntity;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.PipelineRetentionHelper;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.helper.NodeExecutionReadHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.GraphUpdateEventObserver;
import io.harness.engine.observers.GraphUpdatesInfo;
import io.harness.entity.eventlog.OrchestrationEventLog;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.event.PlanExecutionModuleInfoUpdateEventHandler;
import io.harness.event.StepDetailsUpdateEventHandler;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.generator.OrchestrationAdjacencyListGenerator;
import io.harness.graph.service.GraphCDCService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyInfo;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.repositories.executions.GraphUpdateInfoRepositoryCustomImpl;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.repositories.planexecution.PlanExecutionRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.GraphGenerationService;
import io.harness.service.PostgreSQLGraphStoreService;
import io.harness.utils.OrchestrationVisualisationTestHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.api.client.util.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Test class for {@link GraphGenerationServiceImpl}
 */
@Slf4j
public class GraphGenerationServiceImplTest extends OrchestrationVisualizationTestBase {
  @Inject @Spy private PlanExecutionRepository planExecutionRepository;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private NodeExecutionInfoService nodeExecutionInfoService;
  @Mock private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject @InjectMocks OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;
  @Mock private GraphUpdateInfoRepositoryCustomImpl graphUpdateInfoRepositoryCustom;
  @Inject @InjectMocks private NodeExecutionService nodeExecutionService;
  @Mock private StepDetailsUpdateEventHandler stepDetailsUpdateEventHandler;
  @Inject @InjectMocks OrchestrationLogPublisher publisher;
  @Inject @InjectMocks private SpringMongoStore mongoStore;
  @Mock private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Mock private GraphCDCService graphCDCService;
  @Inject private GraphVertexConverter graphVertexConverter;
  @InjectMocks @Inject private GraphGenerationService graphGenerationService;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PlanExecutionModuleInfoUpdateEventHandler planExecutionModuleInfoUpdateEventHandler;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject @InjectMocks GraphGenerationServiceImpl graphGenerationServiceImpl;
  @Inject @Named("referenceFalseKryoSerializer") KryoSerializer kryoSerializer;
  @Mock private PostgreSQLGraphStoreService postgreSQLGraphStoreService;
  private static String planExecutionUuid = "planExecutionUuid";
  private static String ACCOUNT_ID = "accountId";

  @Before
  public void setup() {
    Mockito.doNothing().when(eventEmitter).emitEvent(any());
    Mockito.when(planExecutionMetadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    Mockito.when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(false);
  }

  /*
  Test to deserialize the Binary OrchestrationGraph from the DB, can be used to deserialize any binary data from the DB.
  Whenever required remove the @Ignore annotation and run the test locally.
  Do not remove the @Ignore annotation from this test, as it is not a unit test.
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  @Ignore("This test is only for deserializing the binary data from the DB")
  public void testDeSerializer() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("binaryData.txt");
    assertThat(testFile).isNotNull();
    String fileContent = Resources.toString(testFile, Charsets.UTF_8);
    byte[] data = Base64.getDecoder().decode(fileContent);
    OrchestrationGraph obj = (OrchestrationGraph) kryoSerializer.asInflatedObject(data);
    for (GraphVertex graphVertex : obj.getAdjacencyList().getGraphVertexMap().values()) {
      graphVertex.setAmbiance(null);
    }
    assertThat(obj).isNotNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnOrchestrationGraphWithoutCache() {
    doReturn(
        PlanExecution.builder()
            .uuid("planExecutionUuid")
            .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, "accountIdentifier"))
            .ambiance(
                Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, "accountIdentifier").build())
            .build())
        .when(planExecutionService)
        .getWithFieldsIncluded(any(), any());
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .status(Status.SUCCEEDED)
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(planExecutionUuid)
                    .addAllLevels(Collections.singletonList(Level.newBuilder()
                                                                .setSetupId("node1_plan")
                                                                .setNodeType(NodeType.PLAN_NODE.name())
                                                                .setStrategyInfo(StrategyInfo.newBuilder().build())
                                                                .build()))
                    .setMetadata(ExecutionMetadata.newBuilder()
                                     .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                     .build())
                    .build())
            .mode(ExecutionMode.SYNC)
            .nodeId("node1_plan")
            .name("name")
            .stepType(StepType.newBuilder().setType("DUMMY").build())
            .identifier("identifier1")
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .identifier("identifier1")
            .build();
    nodeExecutionService.save(dummyStart);
    doReturn(NodeExecutionsInfo.builder()
                 .nodeExecutionId(dummyStart.getUuid())
                 .planExecutionId(planExecutionUuid)
                 .strategyMetadata(StrategyMetadata.newBuilder().build())
                 .build())
        .when(pmsGraphStepDetailsService)
        .getNodeExecutionsInfo(dummyStart.getUuid());

    OrchestrationGraphDTO graphResponse =
        graphGenerationService.generateOrchestrationGraphV2("accountIdentifier", planExecutionUuid);
    assertThat(graphResponse).isNotNull();
    assertThat(graphResponse.getAdjacencyList()).isNotNull();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap()).isNotEmpty();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().get(dummyStart.getUuid()).getStrategyMetadata())
        .isEqualTo(StrategyMetadata.newBuilder().build());
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)))
        .isNotNull();
    assertThat(
        graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getNextIds())
        .isEmpty();
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getEdges())
        .isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGenerateSimplifiedOrchestrationGraphV2() {
    doReturn(
        PlanExecution.builder()
            .uuid("planExecutionUuid")
            .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, "accountIdentifier"))
            .ambiance(
                Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, "accountIdentifier").build())
            .build())
        .when(planExecutionService)
        .getWithFieldsIncluded(any(), any());
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .status(Status.SUCCEEDED)
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(planExecutionUuid)
                    .addAllLevels(Collections.singletonList(
                        Level.newBuilder().setSetupId("node1_plan").setNodeType(NodeType.PLAN_NODE.name()).build()))
                    .setMetadata(ExecutionMetadata.newBuilder()
                                     .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                     .build())
                    .build())
            .mode(ExecutionMode.SYNC)
            .nodeId("node1_plan")
            .name("name")
            .stepType(StepType.newBuilder().setType("DUMMY").build())
            .identifier("identifier1")
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .identifier("identifier1")
            .build();
    nodeExecutionService.save(dummyStart);
    doReturn(NodeExecutionsInfo.builder()
                 .nodeExecutionId(dummyStart.getUuid())
                 .planExecutionId(planExecutionUuid)
                 .strategyMetadata(StrategyMetadata.newBuilder().build())
                 .build())
        .when(pmsGraphStepDetailsService)
        .getNodeExecutionsInfo(dummyStart.getUuid());

    SimplifiedOrchestrationGraphDTO graphResponse =
        graphGenerationService.generateSimplifiedOrchestrationGraphV2("accountIdentifier", planExecutionUuid);
    assertThat(graphResponse).isNotNull();
    assertThat(graphResponse.getAdjacencyList()).isNotNull();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap()).isNotEmpty();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)))
        .isNotNull();
    assertThat(
        graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getNextIds())
        .isEmpty();
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getEdges())
        .isEmpty();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnPartialOrchestrationGraph() {
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled("accountIdentifier", FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS);
    doReturn(PlanExecution.builder()
                 .uuid("planExecutionUuid")
                 .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, "accountIdentifier"))
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncluded(any(), any());
    GraphVertex dummyStart =
        GraphVertex.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId("")
                          .addAllLevels(new ArrayList<>())
                          .putAllSetupAbstractions(new HashMap<>())
                          .setMetadata(ExecutionMetadata.newBuilder()
                                           .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                           .build())
                          .build())
            .planNodeId("node1_plan")
            .name("dummyStart")
            .mode(ExecutionMode.SYNC)
            .skipType(SkipType.NOOP)
            .build();

    GraphVertex dummyFinish =
        GraphVertex.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId("")
                          .addAllLevels(new ArrayList<>())
                          .putAllSetupAbstractions(new HashMap<>())
                          .setMetadata(ExecutionMetadata.newBuilder()
                                           .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                           .build())
                          .build())
            .planNodeId("node2_plan")
            .name("dummyFinish")
            .skipType(SkipType.NOOP)
            .build();

    OrchestrationGraph orchestrationGraph =
        constructOrchestrationGraphForPartialTest(Lists.newArrayList(dummyStart, dummyFinish));
    graphGenerationService.cacheOrchestrationGraphInDB(orchestrationGraph, "accountIdentifier");

    OrchestrationGraphDTO graphResponse =
        graphGenerationService.generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
            "accountIdentifier", dummyFinish.getPlanNodeId(), orchestrationGraph.getPlanExecutionId(), null);
    assertThat(graphResponse).isNotNull();
    assertThat(graphResponse.getRootNodeIds().get(0)).isEqualTo(dummyFinish.getUuid());
    assertThat(graphResponse.getAdjacencyList()).isNotNull();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap()).isNotEmpty();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)))
        .isNotNull();
    assertThat(
        graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getNextIds())
        .isEmpty();
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getEdges())
        .isEmpty();
  }

  private OrchestrationGraph constructOrchestrationGraphForPartialTest(List<GraphVertex> graphVertices) {
    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeListInternal> adjacencyMap = new HashMap<>();
    adjacencyMap.put(graphVertices.get(0).getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(Collections.singletonList(graphVertices.get(1).getUuid()))
            .edges(new ArrayList<>())
            .build());
    adjacencyMap.put(graphVertices.get(1).getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(Collections.singletonList(graphVertices.get(0).getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    OrchestrationAdjacencyListInternal listInternal =
        OrchestrationAdjacencyListInternal.builder().graphVertexMap(graphVertexMap).adjacencyMap(adjacencyMap).build();

    return OrchestrationGraph.builder()
        .cacheKey(planExecutionUuid)
        .cacheContextOrder(System.currentTimeMillis())
        .cacheParams(null)
        .startTs(System.currentTimeMillis())
        .endTs(System.currentTimeMillis())
        .rootNodeIds(Collections.singletonList(graphVertices.get(0).getUuid()))
        .planExecutionId(planExecutionUuid)
        .status(Status.SUCCEEDED)
        .adjacencyList(listInternal)
        .build();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraph() {
    assertTrue(graphGenerationService.updateGraph(generateUuid()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphWithWaitLock() {
    assertTrue(graphGenerationService.updateGraphWithWaitLock(generateUuid()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock() {
    assertTrue(graphGenerationServiceImpl.updateGraphUnderLock(generateUuid()));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllGraphMetadataForGivenExecutionIds() {
    String planExecutionId1 = "EXECUTION_1";
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    OrchestrationGraph graph2 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    String planExecutionId2 = "EXECUTION_2";
    OrchestrationGraph graph3 = OrchestrationGraph.builder().cacheKey(planExecutionId2).cacheParams(null).build();
    String planExecutionId3 = "EXECUTION_3";
    OrchestrationGraph graph4 = OrchestrationGraph.builder().cacheKey(planExecutionId3).cacheParams(null).build();
    mongoStore.upsert(graph1, SpringCacheEntity.TTL);
    mongoStore.upsert(graph2, SpringCacheEntity.TTL);
    mongoStore.upsert(graph3, SpringCacheEntity.TTL);
    mongoStore.upsert(graph4, SpringCacheEntity.TTL);

    OrchestrationGraph graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    OrchestrationGraph graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    OrchestrationGraph graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);

    assertThat(graphForExecution1).isNotNull();
    assertThat(graphForExecution2).isNotNull();
    assertThat(graphForExecution3).isNotNull();

    graphGenerationServiceImpl.deleteAllGraphMetadataForGivenExecutionIds(
        Set.of(planExecutionId1, planExecutionId2), false, ACCOUNT_ID);

    graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);
    verify(orchestrationEventLogRepository, times(1)).deleteAllOrchestrationLogEvents(any());

    assertThat(graphForExecution1).isNull();
    assertThat(graphForExecution2).isNull();
    assertThat(graphForExecution3).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testShouldNotDeleteGraphMetadata() {
    String planExecutionId1 = "EXECUTION_1";
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    OrchestrationGraph graph2 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    String planExecutionId2 = "EXECUTION_2";
    OrchestrationGraph graph3 = OrchestrationGraph.builder().cacheKey(planExecutionId2).cacheParams(null).build();
    String planExecutionId3 = "EXECUTION_3";
    OrchestrationGraph graph4 = OrchestrationGraph.builder().cacheKey(planExecutionId3).cacheParams(null).build();
    mongoStore.upsert(graph1, SpringCacheEntity.TTL);
    mongoStore.upsert(graph2, SpringCacheEntity.TTL);
    mongoStore.upsert(graph3, SpringCacheEntity.TTL);
    mongoStore.upsert(graph4, SpringCacheEntity.TTL);

    OrchestrationGraph graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    OrchestrationGraph graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    OrchestrationGraph graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);

    assertThat(graphForExecution1).isNotNull();
    assertThat(graphForExecution2).isNotNull();
    assertThat(graphForExecution3).isNotNull();

    graphGenerationServiceImpl.deleteAllGraphMetadataForGivenExecutionIds(
        Set.of(planExecutionId1, planExecutionId2), true, ACCOUNT_ID);

    graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);
    verify(orchestrationEventLogRepository, times(1)).deleteAllOrchestrationLogEvents(any());

    assertThat(graphForExecution1).isNotNull();
    assertThat(graphForExecution2).isNotNull();
    assertThat(graphForExecution3).isNotNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedOrchestrationGraphWithAccountId() {
    String planExecutionId1 = "EXECUTION_1";
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    mongoStore.upsert(graph1, SpringCacheEntity.TTL, "ACCOUNT_ID");
    EntityWithAccountId graphWithAccountId =
        graphGenerationServiceImpl.getCachedOrchestrationGraphWithAccountIdFromDB(planExecutionId1);
    assertThat(graphWithAccountId.getAccountId()).isEqualTo("ACCOUNT_ID");
    assertThat(graphWithAccountId.getEntity()).isEqualTo(graph1);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedOrchestrationGraphFromDB_ForceRebuild_ReturnsCachedFromOldStores() {
    String planExecutionId = "FORCE_REBUILD_EXEC_1";
    String accountId = "test-account";

    // CDC-started execution
    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());

    // Force rebuild FF is ON
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    // Old stores have a cached graph (blob PG)
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);
    OrchestrationGraph expectedGraph = OrchestrationGraph.builder()
                                           .planExecutionId(planExecutionId)
                                           .status(Status.SUCCEEDED)
                                           .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                              .adjacencyMap(new HashMap<>())
                                                              .graphVertexMap(new HashMap<>())
                                                              .build())
                                           .build();
    doReturn(expectedGraph).when(postgreSQLGraphStoreService).get(planExecutionId);

    OrchestrationGraph result =
        graphGenerationServiceImpl.getCachedOrchestrationGraphFromDB(planExecutionId, accountId);

    // Should return cached graph without force rebuilding
    assertThat(result).isNotNull();
    assertThat(result.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
    // forceRebuildOrchestrationGraph should NOT have been called (getPlanExecutionMetadata not invoked)
    verify(planExecutionService, never()).getPlanExecutionMetadata(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedOrchestrationGraphFromDB_ForceRebuild_TriggersRebuildWhenNoCache() {
    String planExecutionId = "FORCE_REBUILD_EXEC_2";
    String accountId = "test-account";

    // CDC-started execution
    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());

    // Force rebuild FF is ON
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    // Old stores have nothing
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);

    // forceRebuildOrchestrationGraph will call getPlanExecutionMetadata
    doReturn(PlanExecution.builder().uuid(planExecutionId).startTs(1000L).endTs(2000L).status(Status.SUCCEEDED).build())
        .when(planExecutionService)
        .getPlanExecutionMetadata(planExecutionId);

    OrchestrationGraph result =
        graphGenerationServiceImpl.getCachedOrchestrationGraphFromDB(planExecutionId, accountId);

    // forceRebuildOrchestrationGraph was triggered (getPlanExecutionMetadata was called)
    verify(planExecutionService, times(1)).getPlanExecutionMetadata(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedOrchestrationGraphFromDB_NormalCDCPath() {
    String planExecutionId = "CDC_EXEC_1";
    String accountId = "test-account";

    // CDC-started execution, no force rebuild FF
    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    OrchestrationGraph cdcGraph = OrchestrationGraph.builder()
                                      .planExecutionId(planExecutionId)
                                      .status(Status.RUNNING)
                                      .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                         .adjacencyMap(new HashMap<>())
                                                         .graphVertexMap(new HashMap<>())
                                                         .build())
                                      .build();
    doReturn(Optional.of(cdcGraph)).when(graphCDCService).getOrchestrationGraph(planExecutionId);

    OrchestrationGraph result =
        graphGenerationServiceImpl.getCachedOrchestrationGraphFromDB(planExecutionId, accountId);

    assertThat(result).isNotNull();
    assertThat(result.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(result.getStatus()).isEqualTo(Status.RUNNING);
    verify(graphCDCService, times(1)).getOrchestrationGraph(planExecutionId);
    // No force rebuild
    verify(planExecutionService, never()).getPlanExecutionMetadata(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCachedOrchestrationGraphFromDB_NonCdcExecution_SkipsForceRebuild() {
    String planExecutionId = "NON_CDC_EXEC_1";
    String accountId = "test-account";

    // NOT a CDC-started execution
    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(false).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    // Old stores have nothing, MongoDB has nothing
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);

    OrchestrationGraph result =
        graphGenerationServiceImpl.getCachedOrchestrationGraphFromDB(planExecutionId, accountId);

    // Should return null — non-CDC execution, no graph anywhere
    assertThat(result).isNull();
    // Force rebuild should NOT be called for non-CDC executions
    verify(planExecutionService, never()).getPlanExecutionMetadata(any());
    verify(graphCDCService, never()).getOrchestrationGraph(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCdcSubGraph_CdcEnabled_ReturnsGraph() {
    String planExecutionId = "CDC_SUBGRAPH_1";
    String accountId = "test-account";
    String nodeExecutionId = "old-retry-node-1";

    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    OrchestrationGraph expectedGraph = OrchestrationGraph.builder()
                                           .planExecutionId(planExecutionId)
                                           .rootNodeIds(List.of(nodeExecutionId))
                                           .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                              .adjacencyMap(new HashMap<>())
                                                              .graphVertexMap(new HashMap<>())
                                                              .build())
                                           .build();
    doReturn(Optional.of(expectedGraph)).when(graphCDCService).getOldRetrySubGraph(planExecutionId, nodeExecutionId);

    OrchestrationGraph result = graphGenerationServiceImpl.getCdcSubGraph(accountId, planExecutionId, nodeExecutionId);

    assertThat(result).isNotNull();
    assertThat(result.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(result.getRootNodeIds()).contains(nodeExecutionId);
    verify(graphCDCService, times(1)).getOldRetrySubGraph(planExecutionId, nodeExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCdcSubGraph_CdcNotEnabled_ReturnsNull() {
    String planExecutionId = "NON_CDC_SUBGRAPH_1";
    String accountId = "test-account";
    String nodeExecutionId = "old-retry-node-1";

    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(false).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());

    OrchestrationGraph result = graphGenerationServiceImpl.getCdcSubGraph(accountId, planExecutionId, nodeExecutionId);

    assertThat(result).isNull();
    verify(graphCDCService, never()).getOldRetrySubGraph(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCdcSubGraph_ForceRebuildEnabled_ReturnsNull() {
    String planExecutionId = "CDC_SUBGRAPH_FR";
    String accountId = "test-account";
    String nodeExecutionId = "old-retry-node-1";

    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);

    OrchestrationGraph result = graphGenerationServiceImpl.getCdcSubGraph(accountId, planExecutionId, nodeExecutionId);

    assertThat(result).isNull();
    verify(graphCDCService, never()).getOldRetrySubGraph(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCdcSubGraph_CdcEnabled_EmptyResult_ReturnsNull() {
    String planExecutionId = "CDC_SUBGRAPH_EMPTY";
    String accountId = "test-account";
    String nodeExecutionId = "old-retry-node-1";

    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(eq(accountId), eq(planExecutionId), any());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD);
    doReturn(Optional.empty()).when(graphCDCService).getOldRetrySubGraph(planExecutionId, nodeExecutionId);

    OrchestrationGraph result = graphGenerationServiceImpl.getCdcSubGraph(accountId, planExecutionId, nodeExecutionId);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockWithOrchestrationGraph() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    List<OrchestrationEventLog> logs = new ArrayList<>();
    logs.add(OrchestrationEventLog.builder()
                 .nodeExecutionId(nodeExecutionId)
                 .orchestrationEventType(OrchestrationEventType.NODE_EXECUTION_START)
                 .createdAt(1550L)
                 .build());
    doReturn(logs).when(orchestrationEventLogRepository).findUnprocessedEvents(planExecutionId, 1222L, 1000);

    nodeExecutionService.save(
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .status(Status.SUCCEEDED)
            .ambiance(Ambiance.newBuilder()
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountIdentifier")
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.toString()).build())
                          .setMetadata(ExecutionMetadata.newBuilder()
                                           .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                           .build())
                          .build())
            .module("cd")
            .resolvedStepParameters(new HashMap<>())
            .build());
    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .planExecutionId(planExecutionId)
                                                .rootNodeIds(new ArrayList<>())
                                                .lastUpdatedAt(1222L)
                                                .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                   .adjacencyMap(new HashMap<>())
                                                                   .graphVertexMap(new HashMap<>())
                                                                   .build())
                                                .build();
    assertTrue(graphGenerationServiceImpl.updateGraphUnderLock(orchestrationGraph, ACCOUNT_ID));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockNewWithOrchestrationGraph() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    Mockito.when(pmsFeatureFlagHelper.isEnabled("accountIdentifier", FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(true);
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String nodeExecutionId2 = generateUuid();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .status(Status.SUCCEEDED)
            .ambiance(Ambiance.newBuilder()
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountIdentifier")
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.toString()).build())
                          .setMetadata(ExecutionMetadata.newBuilder()
                                           .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                           .build())
                          .build())
            .module("cd")
            .resolvedStepParameters(new HashMap<>())
            .lastUpdatedAt(1555L)
            .build();

    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionId2)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .status(Status.SUCCEEDED)
            .ambiance(Ambiance.newBuilder()
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountIdentifier")
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.toString()).build())
                          .setMetadata(ExecutionMetadata.newBuilder()
                                           .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                           .build())
                          .build())
            .module("cd")
            .lastUpdatedAt(1556L)
            .resolvedStepParameters(new HashMap<>())
            .build();
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    nodeExecutionList.add(nodeExecution1);
    nodeExecutionList.add(nodeExecution2);
    Stream<NodeExecution> iterator =
        OrchestrationVisualisationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator).when(nodeExecutionReadHelperMock).fetchNodeExecutionsWithoutValidation(any());

    List<NodeExecutionsInfo> nodeExecutionsInfoList = new LinkedList<>();
    nodeExecutionsInfoList.add(NodeExecutionsInfo.builder()
                                   .nodeExecutionId(nodeExecutionId)
                                   .planExecutionId(planExecutionId)
                                   .lastUpdatedAt(1558L)
                                   .build());
    Stream<NodeExecutionsInfo> iterator1 =
        OrchestrationVisualisationTestHelper.createCloseableIterator(nodeExecutionsInfoList.iterator()).stream();
    doReturn(iterator1).when(nodeExecutionInfoService).getStepDetailsNotUpdatedInGraph(any(), any());

    doReturn(PlanExecution.builder().uuid(planExecutionId).lastUpdatedAt(1333L).status(Status.RUNNING).build())
        .when(planExecutionService)
        .getByIdAndLastUpdatedAtGT(planExecutionId, 1222L);

    List<GraphUpdateInfo> graphUpdateInfoList = new ArrayList<>();
    graphUpdateInfoList.add(GraphUpdateInfo.builder()
                                .planExecutionId(planExecutionId)
                                .executionSummaryUpdateInfo(
                                    ExecutionSummaryUpdateInfo.builder().stepCategory(StepCategory.PIPELINE).build())
                                .lastUpdatedAt(1558L)
                                .build());
    graphUpdateInfoList.add(
        GraphUpdateInfo.builder()
            .planExecutionId(planExecutionId)
            .nodeExecutionId(nodeExecutionId)
            .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder().stepCategory(StepCategory.STAGE).build())
            .lastUpdatedAt(1559L)
            .build());
    Stream<GraphUpdateInfo> iterator2 =
        OrchestrationVisualisationTestHelper.createCloseableIterator(graphUpdateInfoList.iterator()).stream();
    doReturn(iterator2).when(graphUpdateInfoRepositoryCustom).findGraphUpdateInfoNotProcessedInGraph(any());
    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .planExecutionId(planExecutionId)
                                                .rootNodeIds(new ArrayList<>())
                                                .lastUpdatedAt(1222L)
                                                .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                   .adjacencyMap(new HashMap<>())
                                                                   .graphVertexMap(new HashMap<>())
                                                                   .build())
                                                .build();

    doReturn(orchestrationGraph)
        .when(stepDetailsUpdateEventHandler)
        .handleEventV2(any(), any(), any(), any(), eq(ACCOUNT_ID));
    doReturn(orchestrationGraph).when(stepDetailsUpdateEventHandler).handleStepInputEventV2(any(), any());
    doReturn(new Update()).when(pmsExecutionSummaryService).updateStatusOps(any(), any());
    assertTrue(graphGenerationServiceImpl.updateGraphUnderLockV2(
        OrchestrationGraph.builder()
            .planExecutionId(planExecutionId)
            .rootNodeIds(new ArrayList<>())
            .lastUpdatedAt(1222L)
            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                               .adjacencyMap(new HashMap<>())
                               .graphVertexMap(new HashMap<>())
                               .build())
            .build(),
        ACCOUNT_ID));

    verify(pmsExecutionSummaryService, times(2)).handleNodeExecutionUpdateFromGraphUpdate(any(), any(), any());
    verify(pmsExecutionSummaryService, times(1)).updateStatusOps(any(), any());
    verify(stepDetailsUpdateEventHandler, times(1)).handleEventV2(any(), any(), any(), any(), eq(ACCOUNT_ID));
    verify(stepDetailsUpdateEventHandler, times(1)).handleStepInputEventV2(any(), any());
    verify(planExecutionModuleInfoUpdateEventHandler, times(1)).handlePipelineInfoUpdate(eq(planExecutionId), any());
    verify(planExecutionModuleInfoUpdateEventHandler, times(1))
        .handleStageInfoUpdate(eq(planExecutionId), eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockWithOrchestrationGraphForPipelineInfoUpdate() {
    String planExecutionId = generateUuid();
    List<OrchestrationEventLog> logs = new ArrayList<>();
    logs.add(OrchestrationEventLog.builder()
                 .planExecutionId(planExecutionId)
                 .orchestrationEventType(OrchestrationEventType.PIPELINE_INFO_UPDATE)
                 .createdAt(1550L)
                 .build());
    doReturn(logs).when(orchestrationEventLogRepository).findUnprocessedEvents(planExecutionId, 1222L, 1000);
    assertTrue(
        graphGenerationServiceImpl.updateGraphUnderLock(OrchestrationGraph.builder()
                                                            .planExecutionId(planExecutionId)
                                                            .rootNodeIds(new ArrayList<>())
                                                            .lastUpdatedAt(1222L)
                                                            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                               .adjacencyMap(new HashMap<>())
                                                                               .graphVertexMap(new HashMap<>())
                                                                               .build())
                                                            .build(),
            ACCOUNT_ID));
    verify(planExecutionModuleInfoUpdateEventHandler, times(1)).handlePipelineInfoUpdate(any(), any());
    verify(pmsExecutionSummaryService, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockWithOrchestrationGraphForStageInfoUpdate() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    List<OrchestrationEventLog> logs = new ArrayList<>();
    logs.add(OrchestrationEventLog.builder()
                 .planExecutionId(planExecutionId)
                 .nodeExecutionId(nodeExecutionId)
                 .orchestrationEventType(OrchestrationEventType.STAGE_INFO_UPDATE)
                 .createdAt(1550L)
                 .build());
    doReturn(logs).when(orchestrationEventLogRepository).findUnprocessedEvents(planExecutionId, 1222L, 1000);
    assertTrue(
        graphGenerationServiceImpl.updateGraphUnderLock(OrchestrationGraph.builder()
                                                            .planExecutionId(planExecutionId)
                                                            .rootNodeIds(new ArrayList<>())
                                                            .lastUpdatedAt(1222L)
                                                            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                               .adjacencyMap(new HashMap<>())
                                                                               .graphVertexMap(new HashMap<>())
                                                                               .build())
                                                            .build(),
            ACCOUNT_ID));
    verify(planExecutionModuleInfoUpdateEventHandler, times(1)).handleStageInfoUpdate(any(), any(), any());
    verify(pmsExecutionSummaryService, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCacheOrchestrationGraphInDB_WhenFFEnabled_ShouldUpsertToBothStores() {
    String accountId = "test-account";
    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(planExecutionUuid)
                                   .status(Status.RUNNING)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationService).set("mongoStore", mongoStoreSpy);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);

    graphGenerationService.cacheOrchestrationGraphInDB(graph, accountId);

    verify(mongoStoreSpy).upsert(eq(graph), any(Duration.class), eq(accountId));
    verify(postgreSQLGraphStoreService).upsert(eq(graph), any(Duration.class), eq(accountId));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCacheOrchestrationGraphInDB_WhenFFEnabled_ShouldUpsertToPostgres() {
    String accountId = "test-account";
    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(planExecutionUuid)
                                   .status(Status.RUNNING)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationService).set("mongoStore", mongoStoreSpy);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.PIPE_STOP_USING_MONGO_FOR_EXECUTION_GRAPH);
    graphGenerationService.cacheOrchestrationGraphInDB(graph, accountId);

    verify(mongoStoreSpy, never()).upsert(eq(graph), any(Duration.class), eq(accountId));
    verify(postgreSQLGraphStoreService).upsert(eq(graph), any(Duration.class), eq(accountId));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCacheOrchestrationGraphInDB_WhenFFDisabled_ShouldUpsertOnlyToMongo() {
    String accountId = "test-account";
    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(planExecutionUuid)
                                   .status(Status.RUNNING)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationService).set("mongoStore", mongoStoreSpy);

    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);

    graphGenerationService.cacheOrchestrationGraphInDB(graph, accountId);

    verify(mongoStoreSpy, times(1)).upsert(any(), any(), any());
    verify(postgreSQLGraphStoreService, never()).upsert(any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCacheOrchestrationGraphInDB_WhenAccountIdNull_ShouldUpsertOnlyToMongo() {
    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(planExecutionUuid)
                                   .status(Status.RUNNING)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationService).set("mongoStore", mongoStoreSpy);

    graphGenerationService.cacheOrchestrationGraphInDB(graph, null);

    verify(mongoStoreSpy, never()).upsert(any(), any(), any());
    verify(pmsFeatureFlagHelper, never())
        .isEnabled(anyString(), eq(FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH.name()));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCacheOrchestrationGraphInDB_WhenFFEnabled_Exception() {
    String accountId = "test-account";
    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(planExecutionUuid)
                                   .status(Status.RUNNING)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationService).set("mongoStore", mongoStoreSpy);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);
    doThrow(new RuntimeException("Test Exception")).when(postgreSQLGraphStoreService).upsert(any(), any(), any());

    graphGenerationService.cacheOrchestrationGraphInDB(graph, accountId);

    verify(mongoStoreSpy).upsert(eq(graph), any(Duration.class), eq(accountId));
    verify(postgreSQLGraphStoreService).upsert(eq(graph), any(Duration.class), eq(accountId));
    verify(mongoStoreSpy, times(1)).upsert(any(), any(), any());
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testCachePartialOrchestrationGraph_WhenRetentionDisabledAndCustomizeTtlFFEnabled_UsesAccountOverride() {
    String accountId = "test-account";
    int retentionMonths = 18;

    PmsFeatureFlagService pmsFeatureFlagServiceMock = Mockito.mock(PmsFeatureFlagService.class);
    PipelineRetentionService pipelineRetentionServiceMock = Mockito.mock(PipelineRetentionService.class);
    ExecutionRetentionService executionRetentionServiceMock = Mockito.mock(ExecutionRetentionService.class);
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationServiceImpl).set("pmsFeatureFlagService", pmsFeatureFlagServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("pipelineRetentionService", pipelineRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("executionRetentionService", executionRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("mongoStore", mongoStoreSpy);

    doReturn(false).when(executionRetentionServiceMock).isEnabled();
    doReturn(true).when(pmsFeatureFlagServiceMock).isEnabled(accountId, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL);
    doReturn(retentionMonths).when(pipelineRetentionServiceMock).getRetentionPeriodInMonths(accountId);

    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(generateUuid())
                                   .rootNodeIds(new ArrayList<>())
                                   .lastUpdatedAt(1222L)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    graphGenerationServiceImpl.updateGraphUnderLockV2(graph, accountId);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mongoStoreSpy).upsert(any(OrchestrationGraph.class), ttlCaptor.capture(), eq(graph.getLastUpdatedAt()));
    Duration expected = PipelineRetentionHelper.getValidUntilAsDuration(retentionMonths);
    // Allow a small clock-skew tolerance: getValidUntilAsDuration calls LocalDateTime.now() twice,
    // so the captured and expected values can differ by a couple of millis at most.
    assertThat(Math.abs(ttlCaptor.getValue().minus(expected).toMillis())).isLessThan(1000L);
    assertThat(ttlCaptor.getValue()).isNotEqualTo(SpringCacheEntity.TTL);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testCachePartialOrchestrationGraph_WhenRetentionServiceEnabled_UsesRetentionServiceTtl() {
    String accountId = "test-account";
    int retentionDays = 70;

    PmsFeatureFlagService pmsFeatureFlagServiceMock = Mockito.mock(PmsFeatureFlagService.class);
    PipelineRetentionService pipelineRetentionServiceMock = Mockito.mock(PipelineRetentionService.class);
    ExecutionRetentionService executionRetentionServiceMock = Mockito.mock(ExecutionRetentionService.class);
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationServiceImpl).set("pmsFeatureFlagService", pmsFeatureFlagServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("pipelineRetentionService", pipelineRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("executionRetentionService", executionRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("mongoStore", mongoStoreSpy);

    doReturn(true).when(executionRetentionServiceMock).isEnabled();
    doReturn(retentionDays)
        .when(executionRetentionServiceMock)
        .getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH);

    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(generateUuid())
                                   .rootNodeIds(new ArrayList<>())
                                   .lastUpdatedAt(1222L)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    graphGenerationServiceImpl.updateGraphUnderLockV2(graph, accountId);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mongoStoreSpy).upsert(any(OrchestrationGraph.class), ttlCaptor.capture(), eq(graph.getLastUpdatedAt()));
    assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(retentionDays));
    // When the retention service wins, the per-account override must not be consulted.
    verify(pipelineRetentionServiceMock, never()).getRetentionPeriodInMonths(anyString());
    verify(pmsFeatureFlagServiceMock, never()).isEnabled(anyString(), eq(FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testCachePartialOrchestrationGraph_WhenRetentionDisabledAndCustomizeTtlFFDisabled_UsesDefaultTtl() {
    String accountId = "test-account";

    PmsFeatureFlagService pmsFeatureFlagServiceMock = Mockito.mock(PmsFeatureFlagService.class);
    PipelineRetentionService pipelineRetentionServiceMock = Mockito.mock(PipelineRetentionService.class);
    ExecutionRetentionService executionRetentionServiceMock = Mockito.mock(ExecutionRetentionService.class);
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationServiceImpl).set("pmsFeatureFlagService", pmsFeatureFlagServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("pipelineRetentionService", pipelineRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("executionRetentionService", executionRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("mongoStore", mongoStoreSpy);

    doReturn(false).when(executionRetentionServiceMock).isEnabled();
    doReturn(false).when(pmsFeatureFlagServiceMock).isEnabled(accountId, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL);

    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(generateUuid())
                                   .rootNodeIds(new ArrayList<>())
                                   .lastUpdatedAt(1222L)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    graphGenerationServiceImpl.updateGraphUnderLockV2(graph, accountId);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(mongoStoreSpy).upsert(any(OrchestrationGraph.class), ttlCaptor.capture(), eq(graph.getLastUpdatedAt()));
    assertThat(ttlCaptor.getValue()).isEqualTo(SpringCacheEntity.TTL);
    verify(pipelineRetentionServiceMock, never()).getRetentionPeriodInMonths(anyString());
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testCachePartialOrchestrationGraph_WhenAccountIdentifierNull_SkipsUpsert() {
    PmsFeatureFlagService pmsFeatureFlagServiceMock = Mockito.mock(PmsFeatureFlagService.class);
    PipelineRetentionService pipelineRetentionServiceMock = Mockito.mock(PipelineRetentionService.class);
    ExecutionRetentionService executionRetentionServiceMock = Mockito.mock(ExecutionRetentionService.class);
    SpringMongoStore mongoStoreSpy = Mockito.spy(mongoStore);
    Reflect.on(graphGenerationServiceImpl).set("pmsFeatureFlagService", pmsFeatureFlagServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("pipelineRetentionService", pipelineRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("executionRetentionService", executionRetentionServiceMock);
    Reflect.on(graphGenerationServiceImpl).set("mongoStore", mongoStoreSpy);

    doReturn(false).when(executionRetentionServiceMock).isEnabled();

    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .planExecutionId(generateUuid())
                                   .rootNodeIds(new ArrayList<>())
                                   .lastUpdatedAt(1222L)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();
    graphGenerationServiceImpl.updateGraphUnderLockV2(graph, null);

    verify(mongoStoreSpy, never())
        .upsert(any(OrchestrationGraph.class), any(Duration.class), eq(graph.getLastUpdatedAt()));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumeGraphUpdate_NotifyAfterGraphUpdateEnabled_RemoveOrchestrationLogEventsDisabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIE_NOTIFY_AFTER_GRAPH_UPDATE);

    String planExecutionId = generateUuid();

    List<OrchestrationEventLog> logs = new ArrayList<>();
    logs.add(OrchestrationEventLog.builder()
                 .planExecutionId(planExecutionId)
                 .orchestrationEventType(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE)
                 .createdAt(1550L)
                 .build());
    doReturn(logs).when(orchestrationEventLogRepository).findUnprocessedEvents(planExecutionId, 1222L, 1000);

    PlanExecution resumedPlanExecution =
        PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).lastUpdatedAt(1555L).build();
    doReturn(resumedPlanExecution).when(planExecutionService).get(planExecutionId);
    doReturn(new Update()).when(pmsExecutionSummaryService).updateStatusOps(any(), any());

    GraphUpdateEventObserver mockObserver = Mockito.mock(GraphUpdateEventObserver.class);
    graphGenerationServiceImpl.getGraphUpdateObserverSubject().register(mockObserver);
    try {
      OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                  .cacheKey(planExecutionId)
                                                  .cacheParams(null)
                                                  .planExecutionId(planExecutionId)
                                                  .status(Status.APPROVAL_WAITING)
                                                  .rootNodeIds(new ArrayList<>())
                                                  .lastUpdatedAt(1222L)
                                                  .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                     .adjacencyMap(new HashMap<>())
                                                                     .graphVertexMap(new HashMap<>())
                                                                     .build())
                                                  .build();

      assertTrue(graphGenerationServiceImpl.updateGraphUnderLock(orchestrationGraph, ACCOUNT_ID));

      OrchestrationGraph cachedGraph =
          mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
      assertThat(cachedGraph).isNotNull();
      assertThat(cachedGraph.getStatus()).isEqualTo(Status.RUNNING);

      ArgumentCaptor<GraphUpdatesInfo> captor = ArgumentCaptor.forClass(GraphUpdatesInfo.class);
      verify(mockObserver, times(1)).onGraphUpdate(captor.capture());
      GraphUpdatesInfo graphUpdatesInfo = captor.getValue();
      assertThat(graphUpdatesInfo.getPlanExecutionId()).isEqualTo(planExecutionId);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList()).isNotEmpty();
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getPreviousStatus())
          .isEqualTo(Status.APPROVAL_WAITING);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getStatus()).isEqualTo(Status.RUNNING);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getNodeType()).isEqualTo(NodeType.PLAN);
    } finally {
      graphGenerationServiceImpl.getGraphUpdateObserverSubject().unregister(mockObserver);
    }
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumeGraphUpdate_NotifyAfterGraphUpdateEnabled_RemoveOrchestrationLogEventsEnabled() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    Mockito.when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(true);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIE_NOTIFY_AFTER_GRAPH_UPDATE);

    String planExecutionId = generateUuid();

    Stream<NodeExecution> emptyNodeExecutionStream =
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecution>emptyIterator())
            .stream();
    doReturn(emptyNodeExecutionStream).when(nodeExecutionReadHelperMock).fetchNodeExecutionsWithoutValidation(any());

    PlanExecution resumedPlanExecution =
        PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).lastUpdatedAt(1555L).build();
    doReturn(resumedPlanExecution).when(planExecutionService).getByIdAndLastUpdatedAtGT(planExecutionId, 1222L);
    doReturn(new Update()).when(pmsExecutionSummaryService).updateStatusOps(any(), any());

    Stream<NodeExecutionsInfo> emptyNodeExecutionsInfoStream =
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecutionsInfo>emptyIterator())
            .stream();
    doReturn(emptyNodeExecutionsInfoStream)
        .when(nodeExecutionInfoService)
        .getStepDetailsNotUpdatedInGraph(any(), any());

    Stream<GraphUpdateInfo> emptyGraphUpdateInfoStream =
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<GraphUpdateInfo>emptyIterator())
            .stream();
    doReturn(emptyGraphUpdateInfoStream)
        .when(graphUpdateInfoRepositoryCustom)
        .findGraphUpdateInfoNotProcessedInGraph(any());

    GraphUpdateEventObserver mockObserver = Mockito.mock(GraphUpdateEventObserver.class);
    graphGenerationServiceImpl.getGraphUpdateObserverSubject().register(mockObserver);
    try {
      OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                  .cacheKey(planExecutionId)
                                                  .cacheParams(null)
                                                  .planExecutionId(planExecutionId)
                                                  .status(Status.APPROVAL_WAITING)
                                                  .rootNodeIds(new ArrayList<>())
                                                  .lastUpdatedAt(1222L)
                                                  .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                     .adjacencyMap(new HashMap<>())
                                                                     .graphVertexMap(new HashMap<>())
                                                                     .build())
                                                  .build();

      assertTrue(graphGenerationServiceImpl.updateGraphUnderLockV2(orchestrationGraph, ACCOUNT_ID));

      OrchestrationGraph cachedGraph =
          mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
      assertThat(cachedGraph).isNotNull();
      assertThat(cachedGraph.getStatus()).isEqualTo(Status.RUNNING);

      ArgumentCaptor<GraphUpdatesInfo> captor = ArgumentCaptor.forClass(GraphUpdatesInfo.class);
      verify(mockObserver, times(1)).onGraphUpdate(captor.capture());
      GraphUpdatesInfo graphUpdatesInfo = captor.getValue();
      assertThat(graphUpdatesInfo.getPlanExecutionId()).isEqualTo(planExecutionId);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList()).isNotEmpty();
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getPreviousStatus())
          .isEqualTo(Status.APPROVAL_WAITING);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getStatus()).isEqualTo(Status.RUNNING);
      assertThat(graphUpdatesInfo.getGraphUpdateEventInfoList().get(0).getNodeType()).isEqualTo(NodeType.PLAN);
    } finally {
      graphGenerationServiceImpl.getGraphUpdateObserverSubject().unregister(mockObserver);
    }
  }

  // ─── CDC fast-path tests ──────────────────────────────────────────────────────

  /**
   * When cdcGraphEnabled=true, updateGraphUnderLock(planExecutionId) must skip the blob entirely
   * and write updates via the lean CDC summary path (reads from 4 secondary collections).
   */
  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock_cdcStartedExecution_takesCdcFastPath() {
    String planExecutionId = generateUuid();

    // getAccountId uses secondary read
    doReturn(PlanExecution.builder()
                 .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, ACCOUNT_ID))
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncludedFromSecondary(eq(planExecutionId), any());

    // getCdcStartedSummaryEntity returns entity with cdcGraphEnabled=true
    PipelineExecutionSummaryEntity cdcEntity =
        PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).lastUpdatedAt(1000L).build();
    doReturn(cdcEntity)
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(eq(ACCOUNT_ID), eq(planExecutionId), any());

    // updateExecutionSummaryForCdcExecution: 4 secondary reads – all empty so updateRequired=false
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecution>emptyIterator())
                 .stream())
        .when(nodeExecutionReadHelperMock)
        .fetchNodeExecutionsWithoutValidationFromSecondary(any());

    doReturn(null).when(planExecutionService).getByIdAndLastUpdatedAtGTFromSecondary(planExecutionId, 1000L);

    doReturn(
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecutionsInfo>emptyIterator())
            .stream())
        .when(nodeExecutionInfoService)
        .getStepDetailsNotUpdatedInGraphFromSecondary(any(), any());

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<GraphUpdateInfo>emptyIterator())
                 .stream())
        .when(graphUpdateInfoRepositoryCustom)
        .findGraphUpdateInfoNotProcessedInGraphFromSecondary(any());

    boolean result = graphGenerationServiceImpl.updateGraphUnderLock(planExecutionId);

    assertThat(result).isTrue();
    // Blob-based path must NOT be taken — orchestration event log must not be consulted
    verify(orchestrationEventLogRepository, never()).findUnprocessedEvents(any(), any(Long.class), any(Integer.class));
    // pmsExecutionSummaryService.update is NOT called because there are no updates (updateRequired=false)
    verify(pmsExecutionSummaryService, never()).update(any(), any());
  }

  /**
   * updateGraphUnderLock(planExecutionId) when cdcGraphEnabled=false must fall through to the
   * blob-based path (fetches OrchestrationGraph from cache) and NOT call any CDC secondary reads.
   */
  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock_nonCdcExecution_fallsThroughToBlobPath() {
    String planExecutionId = generateUuid();

    doReturn(PlanExecution.builder()
                 .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, ACCOUNT_ID))
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncludedFromSecondary(eq(planExecutionId), any());

    // cdcGraphEnabled=false → getCdcStartedSummaryEntity returns null
    doReturn(PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(false).lastUpdatedAt(1000L).build())
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(eq(ACCOUNT_ID), eq(planExecutionId), any());

    boolean result = graphGenerationServiceImpl.updateGraphUnderLock(planExecutionId);

    // Graph is null (not cached) → returns true (warn + skip)
    assertThat(result).isTrue();
    // Secondary node-execution stream must not be touched
    verify(nodeExecutionInfoService, never()).getStepDetailsNotUpdatedInGraphFromSecondary(any(), any());
    verify(graphUpdateInfoRepositoryCustom, never()).findGraphUpdateInfoNotProcessedInGraphFromSecondary(any());
  }

  /**
   * updateGraphUnderLock(planExecutionId) when pmsExecutionSummaryService throws must NOT propagate
   * (getCdcStartedSummaryEntity catches and returns null) → falls through to blob path normally.
   */
  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock_cdcCheckThrows_gracefullyFallsThroughToBlobPath() {
    String planExecutionId = generateUuid();

    doReturn(PlanExecution.builder()
                 .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, ACCOUNT_ID))
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncludedFromSecondary(eq(planExecutionId), any());

    doThrow(new RuntimeException("mongo secondary unavailable"))
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(eq(ACCOUNT_ID), eq(planExecutionId), any());

    // Should not throw; falls through to blob path (graph null → returns true with warn)
    boolean result = graphGenerationServiceImpl.updateGraphUnderLock(planExecutionId);
    assertThat(result).isTrue();
  }

  /**
   * updateGraphUnderLockV2 must NOT short-circuit for CDC executions — it is called by
   * forceRebuildOrchestrationGraph which intentionally processes CDC executions with a full rebuild.
   * Verify that passing a CDC execution graph directly to updateGraphUnderLockV2 proceeds to the
   * 4-collection update path and does NOT bail out early.
   */
  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockV2_cdcExecution_doesNotShortCircuit() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    Mockito.when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(true);

    String planExecutionId = generateUuid();

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecution>emptyIterator())
                 .stream())
        .when(nodeExecutionReadHelperMock)
        .fetchNodeExecutionsWithoutValidation(any());

    doReturn(null).when(planExecutionService).getByIdAndLastUpdatedAtGT(planExecutionId, 0L);

    doReturn(
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecutionsInfo>emptyIterator())
            .stream())
        .when(nodeExecutionInfoService)
        .getStepDetailsNotUpdatedInGraph(any(), any());

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<GraphUpdateInfo>emptyIterator())
                 .stream())
        .when(graphUpdateInfoRepositoryCustom)
        .findGraphUpdateInfoNotProcessedInGraph(any());

    OrchestrationGraph graph = OrchestrationGraph.builder()
                                   .cacheKey(planExecutionId)
                                   .planExecutionId(planExecutionId)
                                   .rootNodeIds(new ArrayList<>())
                                   .lastUpdatedAt(0L)
                                   .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                      .adjacencyMap(new HashMap<>())
                                                      .graphVertexMap(new HashMap<>())
                                                      .build())
                                   .build();

    boolean result = graphGenerationServiceImpl.updateGraphUnderLockV2(graph, ACCOUNT_ID);

    assertThat(result).isTrue();
    // Verify the 4-collection update path was invoked (not skipped)
    verify(nodeExecutionReadHelperMock, atLeastOnce()).fetchNodeExecutionsWithoutValidation(any());
    verify(graphUpdateInfoRepositoryCustom, atLeastOnce()).findGraphUpdateInfoNotProcessedInGraph(any());
    // CDC fast-path secondary reads must NOT be called from updateGraphUnderLockV2
    verify(pmsExecutionSummaryService, never()).fetchFromSecondaryWithProjections(any(), any(), any());
  }

  /**
   * updateExecutionSummaryForCdcExecution reads from secondary for all 4 collections and calls
   * pmsExecutionSummaryService.update when there are updates.
   */
  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock_cdcPath_callsUpdateWhenNodeExecutionUpdatesExist() {
    String planExecutionId = generateUuid();

    doReturn(PlanExecution.builder()
                 .setupAbstractions(Collections.singletonMap(SetupAbstractionKeys.accountId, ACCOUNT_ID))
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncludedFromSecondary(eq(planExecutionId), any());

    PipelineExecutionSummaryEntity cdcEntity =
        PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).lastUpdatedAt(500L).build();
    doReturn(cdcEntity)
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(eq(ACCOUNT_ID), eq(planExecutionId), any());

    // One NodeExecution update exists on secondary
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .lastUpdatedAt(600L)
                                      .status(Status.SUCCEEDED)
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(planExecutionId)
                                                    .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                                                    .build())
                                      .build();
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    doReturn(OrchestrationVisualisationTestHelper
                 .createCloseableIterator(Collections.singletonList(nodeExecution).iterator())
                 .stream())
        .when(nodeExecutionReadHelperMock)
        .fetchNodeExecutionsWithoutValidationFromSecondary(any());

    doReturn(true)
        .when(pmsExecutionSummaryService)
        .handleNodeExecutionUpdateFromGraphUpdate(eq(planExecutionId), eq(nodeExecution), any());

    doReturn(null).when(planExecutionService).getByIdAndLastUpdatedAtGTFromSecondary(planExecutionId, 500L);

    doReturn(
        OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<NodeExecutionsInfo>emptyIterator())
            .stream())
        .when(nodeExecutionInfoService)
        .getStepDetailsNotUpdatedInGraphFromSecondary(any(), any());

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.<GraphUpdateInfo>emptyIterator())
                 .stream())
        .when(graphUpdateInfoRepositoryCustom)
        .findGraphUpdateInfoNotProcessedInGraphFromSecondary(any());

    graphGenerationServiceImpl.updateGraphUnderLock(planExecutionId);

    // update must be called because handleNodeExecutionUpdateFromGraphUpdate returned true
    verify(pmsExecutionSummaryService, times(1)).update(eq(planExecutionId), any());
  }
}
