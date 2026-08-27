/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.JATIN;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.AbortInfoHelper;
import io.harness.OrchestrationVisualizationTestBase;
import io.harness.abort.AbortedBy;
import io.harness.advisers.pipelinerollback.output.OnFailPipelineRollbackOutput;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.beans.FeatureName;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.NodeType;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.merger.yaml.Utils;
import io.harness.pms.plan.execution.ExecutionSummaryUpdateUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO.GraphLayoutNodeDTOKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.search.service.PipelineSearchService;
import io.harness.utils.OrchestrationVisualisationTestHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryServiceImplTest extends OrchestrationVisualizationTestBase {
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRepositoryMock;
  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock NodeExecutionInfoService pmsGraphStepDetailsService;
  @Mock AbortInfoHelper abortInfoHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Spy @InjectMocks PmsExecutionSummaryServiceImpl pmsExecutionSummaryService;
  @Mock ExecutionSummaryUpdateUtils executionSummaryUpdateUtils;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock PipelineSearchService pipelineSearchService;
  @Mock ExecutionRetentionService executionRetentionService;
  @Mock ExecutionRetentionIteratorEntityService retentionIteratorEntityService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doCallRealMethod().when(executionSummaryUpdateUtils).updateNextIdOfStageBeforePipelineRollback(any(), any(), any());
    doCallRealMethod().when(executionSummaryUpdateUtils).updateDependencyGraphForPipelineRollback(any(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetNotesForExecution_ReturnsNotes() {
    String accountId = "acc";
    String planExecutionId = "planX";
    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().notes("hello world").build();
    doReturn(entity)
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(
            eq(accountId), eq(planExecutionId), eq(Set.of(PlanExecutionSummaryKeys.notes)));

    String result = pmsExecutionSummaryService.getNotesForExecution(accountId, planExecutionId);
    assertThat(result).isEqualTo("hello world");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetNotesForExecution_ReturnsEmptyNotes() {
    String accountId = "acc";
    String planExecutionId = "planX";
    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().notes("").build();
    doReturn(entity)
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(
            eq(accountId), eq(planExecutionId), eq(Set.of(PlanExecutionSummaryKeys.notes)));

    String result = pmsExecutionSummaryService.getNotesForExecution(accountId, planExecutionId);
    assertThat(result).isEqualTo("");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetNotesForExecution_ReturnsNotesFormMetadataIfNotesIsNull() {
    String accountId = "acc";
    String planExecutionId = "planX";

    PipelineExecutionSummaryEntity entityWithNullNotes = PipelineExecutionSummaryEntity.builder().notes(null).build();

    doReturn(entityWithNullNotes)
        .when(pmsExecutionSummaryService)
        .fetchFromSecondaryWithProjections(
            eq(accountId), eq(planExecutionId), eq(Set.of(PlanExecutionSummaryKeys.notes)));
    doReturn("Hello world").when(planExecutionMetadataService).getNotesForExecution(eq(accountId), eq(planExecutionId));

    String result = pmsExecutionSummaryService.getNotesForExecution(accountId, planExecutionId);
    assertThat(result).isEqualTo("Hello world");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_UpdatesNotesAndFlag() {
    String accountId = "acc";
    String planExecutionId = "planX";
    String notes = "some note";

    PipelineExecutionSummaryEntity updated = PipelineExecutionSummaryEntity.builder().notes(notes).build();
    doReturn(updated).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));

    String result = pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    assertThat(result).isEqualTo(notes);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_EmptyNotesSetsFlagFalse() {
    String accountId = "acc";
    String planExecutionId = "planX";
    String notes = "";

    final Update[] captured = new Update[1];
    doAnswer(invocation -> {
      Query q = invocation.getArgument(0);
      Update u = invocation.getArgument(1);
      captured[0] = u;
      return PipelineExecutionSummaryEntity.builder().notes(notes).build();
    })
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(Query.class), any(Update.class));

    String result = pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    assertThat(result).isEqualTo("");
    assertThat(captured[0].toString()).contains("\"notesExistForPlanExecutionId\" : false");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_ThrowsWhenEntityMissing() {
    String accountId = "acc";
    String planExecutionId = "planX";
    doReturn(null).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));
    assertThatThrownBy(() -> pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, "note"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_Expired_ReadsAndSyncsToObjectStoreAndElastic() {
    String accountId = "acc";
    String planExecutionId = "planX";
    String notes = "updated note";

    ExecutionRetentionMetadata expiredMetadata = ExecutionRetentionMetadata.builder().uuid("meta-1").build();
    doReturn(expiredMetadata)
        .when(executionRetentionService)
        .getRetentionMetadataIfExpired(
            accountId, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    RetentionFileData fileData = RetentionFileData.builder().uuid(planExecutionId).filePath("path").build();
    doReturn(fileData)
        .when(executionRetentionService)
        .getRetentionFileData(
            expiredMetadata, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    PipelineExecutionSummaryEntity stored =
        PipelineExecutionSummaryEntity.builder().planExecutionId(planExecutionId).notes("old").build();
    doReturn(stored)
        .when(executionRetentionService)
        .readObjectFromStore(
            fileData, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY, PipelineExecutionSummaryEntity.class);

    doReturn(true).when(pipelineSearchService).shouldSyncToElastic(any(Update.class));

    String result = pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    assertThat(result).isEqualTo(notes);
    verify(pmsExecutionSummaryRepositoryMock, times(0)).update(any(Query.class), any(Update.class));
    verify(retentionIteratorEntityService, times(1))
        .syncSummaryEntityToObjectStore(any(PipelineExecutionSummaryEntity.class), eq(expiredMetadata));
    verify(pipelineSearchService, times(1)).update(any(PipelineExecutionSummaryEntity.class));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_NotExpired_NoMetadata() {
    String accountId = "acc";
    String planExecutionId = "planX";
    String notes = "note";

    doReturn(null)
        .when(executionRetentionService)
        .getRetentionMetadataIfExpired(
            accountId, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    doReturn(null)
        .when(executionRetentionService)
        .getRetentionMetadata(accountId, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    PipelineExecutionSummaryEntity updated = PipelineExecutionSummaryEntity.builder().notes(notes).build();
    doReturn(updated).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));

    String result = pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    assertThat(result).isEqualTo(notes);
    verify(pmsExecutionSummaryRepositoryMock, times(1)).update(any(Query.class), any(Update.class));
    verify(retentionIteratorEntityService, times(0))
        .syncSummaryEntityToObjectStore(any(PipelineExecutionSummaryEntity.class), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecution_NotExpired_WithMetadata_SyncsToObjectStore() {
    String accountId = "acc";
    String planExecutionId = "planX";
    String notes = "note";

    doReturn(null)
        .when(executionRetentionService)
        .getRetentionMetadataIfExpired(
            accountId, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    ExecutionRetentionMetadata metadata = ExecutionRetentionMetadata.builder().uuid("meta").build();
    doReturn(metadata)
        .when(executionRetentionService)
        .getRetentionMetadata(accountId, planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY);

    PipelineExecutionSummaryEntity updated =
        PipelineExecutionSummaryEntity.builder().planExecutionId(planExecutionId).notes(notes).build();
    doReturn(updated).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));
    doReturn(Optional.of(updated)).when(pmsExecutionSummaryRepositoryMock).findByPlanExecutionId(planExecutionId);

    String result = pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    assertThat(result).isEqualTo(notes);
    verify(retentionIteratorEntityService, times(1)).syncSummaryEntityToObjectStore(eq(updated), eq(metadata));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionSummaryRepositoryMock)
        .getPipelineExecutionSummaryWithProjections(any(),
            eq(Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.planExecutionId,
                PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.projectIdentifier)));
    assertEquals(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
                     Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.planExecutionId,
                         PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.projectIdentifier)),
        pipelineExecutionSummaryEntity);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateStrategyNode() {
    Update update = new Update();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder().concurrentChildInstance(ConcurrentChildInstance.builder().build()).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .executableResponse(ExecutableResponse.newBuilder().build())
            .ambiance(Ambiance.newBuilder()
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                          .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .build();
    pmsExecutionSummaryService.updateStrategyPlanNode("planExecution", nodeExecution, update);
    verify(pmsGraphStepDetailsService, times(0)).fetchConcurrentChildInstance(nodeExecution.getUuid());
    nodeExecution =
        NodeExecution.builder()
            .ambiance(
                Ambiance.newBuilder()
                    .addLevels(Level.newBuilder().setGroup("STAGES").setNodeType(NodeType.PLAN_NODE.name()).build())
                    .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .build();
    pmsExecutionSummaryService.updateStrategyPlanNode("planExecution", nodeExecution, update);
    verify(pmsGraphStepDetailsService, times(1)).fetchConcurrentChildInstance(nodeExecution.getUuid());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchPlanExecutionIdsFromAnalytics() {
    String projectId = "projectId";
    String pipelineId = "pipelineId";
    String accountId = "accountId";
    String orgId = "orgId";

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.emptyListIterator()).stream())
        .when(pmsExecutionSummaryRepositoryMock)
        .fetchExecutionSummaryEntityFromAnalytics(any());
    List<PipelineExecutionSummaryEntity> executionSummaryEntities = new LinkedList<>();
    pmsExecutionSummaryService.fetchPlanExecutionIdsAndStatusFromAnalytics(
        accountId, orgId, projectId, pipelineId, null);
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionSummaryKeys.parentUniqueId)
                            .is((String) null)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is(pipelineId);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId).include(PlanExecutionSummaryKeys.status);
    verify(pmsExecutionSummaryRepositoryMock, times(1)).fetchExecutionSummaryEntityFromAnalytics(query);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateStageOfIdentityType() {
    String stageSetupIdForStrategy = "stageSetupId";
    String strategyNodeExecutionId = "strategyNodeExecutionId";
    String runtimeId = "runtimeId";
    String planExecutionId = "planExecutionId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder()
                                           .setNodeType(NodeType.PLAN_NODE.name())
                                           .setRuntimeId(runtimeId)
                                           .setSetupId("setupId")
                                           .build())
                            .build();

    List<NodeExecution> nodeExecutions = new ArrayList<>();

    nodeExecutions.add(NodeExecution.builder()
                           .uuid("nodeExecutionId1")
                           .endTs(1000L)
                           .status(Status.SUCCEEDED)
                           .nodeId("stageNodeIdWithoutStrategy")
                           .parentId("parentNodeExecutionId")
                           .ambiance(ambiance)
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build());

    nodeExecutions.add(NodeExecution.builder()
                           .uuid("nodeExecutionId2")
                           .endTs(1000L)
                           .status(Status.SUCCEEDED)
                           .nodeId(stageSetupIdForStrategy)
                           .parentId(strategyNodeExecutionId)
                           .ambiance(ambiance)
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build());

    nodeExecutions.add(
        NodeExecution.builder()
            .uuid("nodeExecutionId3")
            .parentId(strategyNodeExecutionId)
            .endTs(1000L)
            .nodeId(stageSetupIdForStrategy)
            .status(Status.SUCCEEDED)
            .ambiance(
                ambiance.toBuilder()
                    .addLevels(Level.newBuilder()
                                   .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                   .setRuntimeId(runtimeId)
                                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                   .build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .build());

    nodeExecutions.add(
        NodeExecution.builder()
            .uuid("stepStrategyNodeExecutionId")
            .nodeId("stepStrategyNodeId")
            .parentId("stepsNodeExecutionId")
            .endTs(1000L)
            .status(Status.SUCCEEDED)
            .nodeId("stepNodeId")
            .ambiance(
                ambiance.toBuilder()
                    .addLevels(Level.newBuilder()
                                   .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                   .setRuntimeId(runtimeId)
                                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                   .build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .build());

    Ambiance ambianceForStageStrategy =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId("setupId").setGroup("STAGES").build())
            .addLevels(Level.newBuilder()
                           .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                           .setRuntimeId(runtimeId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .build())
            .build();

    nodeExecutions.add(NodeExecution.builder()
                           .uuid(strategyNodeExecutionId)
                           .nodeId("strategyNodeId")
                           .parentId("stageNodeExecutionId")
                           .endTs(1000L)
                           .status(Status.SUCCEEDED)
                           .ambiance(ambianceForStageStrategy)
                           .executableResponses(Collections.singleton(
                               ExecutableResponse.newBuilder()
                                   .setChildren(ChildrenExecutableResponse.newBuilder().setMaxConcurrency(3).build())
                                   .build()))
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .build());

    doReturn(ConcurrentChildInstance.builder().childrenNodeExecutionIds(Collections.singletonList("randomId")).build())
        .when(pmsGraphStepDetailsService)
        .fetchConcurrentChildInstance(strategyNodeExecutionId);
    doReturn(nodeExecutions)
        .when(nodeExecutionService)
        .fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId);

    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .nodeType(StrategyType.PARALLELISM.name())
                             .edgeLayoutList(EdgeLayoutListDTO.builder()
                                                 .currentNodeChildren(new ArrayList<>(List.of(stageSetupIdForStrategy)))
                                                 .build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeType("STAGE")
                             .nodeGroup("stage")
                             .module("pms")
                             .edgeLayoutList(EdgeLayoutListDTO.builder().build())
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled("accountId", FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    doReturn(PipelineExecutionSummaryEntity.builder().build())
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(), any());
    Update update = new Update();
    pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, update);
    verify(pmsExecutionSummaryService, times(1))
        .pullStageStepIdFromStrategyChildren(eq(planExecutionId), any(), eq(stageSetupIdForStrategy));
    // Since returned pipelineExecutionSummary.layoutNode had the strategy node with type parallelism. So maxConcurrency
    // will not be present in update.
    assertEquals(update.toString(),
        "{ \"$set\" : { \"layoutNodeMap.setupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.setupId.endTs\" : "
            + "1000, \"layoutNodeMap.nodeExecutionId3.status\" : { \"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.nodeExecutionId3.endTs\" : 1000, "
            + "\"layoutNodeMap.strategyNodeId.moduleInfo.stepParameters\" "
            + ": null, \"layoutNodeMap.strategyNodeId.nodeRunInfo\" : null, "
            + "\"layoutNodeMap.strategyNodeId.childrenCount\" : 0, \"layoutNodeMap.strategyNodeId.status\" : "
            + "{ \"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.strategyNodeId.startTs\" : null, \"layoutNodeMap.strategyNodeId.endTs\" : 1000, "
            + "\"layoutNodeMap.nodeExecutionId2.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId2.nodeGroup\" : "
            + "\"stage\", \"layoutNodeMap.nodeExecutionId2.edgeLayoutList\" : { \"$java\" : "
            + "EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId2.skipInfo\" "
            + ": { "
            + "\"$java\" :  }, \"layoutNodeMap.nodeExecutionId2.nodeUuid\" : \"stageSetupId\", "
            + "\"layoutNodeMap.nodeExecutionId2.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId2.executionInputConfigured\" : null, "
            + "\"layoutNodeMap.nodeExecutionId3.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId3.nodeGroup\" : "
            + "\"stage\", \"layoutNodeMap.nodeExecutionId3.edgeLayoutList\" : { \"$java\" : "
            + "EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId3.skipInfo\" "
            + ": { "
            + "\"$java\" :  }, \"layoutNodeMap.nodeExecutionId3.nodeUuid\" : \"stageSetupId\", "
            + "\"layoutNodeMap.nodeExecutionId3.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId3.executionInputConfigured\" : null }, \"$addToSet\" : { "
            + "\"layoutNodeMap.strategyNodeId.edgeLayoutList.currentNodeChildren\" : { \"$java\" : { \"$each\" : [ "
            + "\"nodeExecutionId2\", \"nodeExecutionId3\" ] } } } }");
    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .nodeType(StrategyType.PARALLELISM.name())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeGroup("stage")
                             .module("pms")
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    update = new Update();
    pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, update);
    // Since returned pipelineExecutionSummary.layoutNode had the strategy node with type parallelism. So maxConcurrency
    // will not be present in update.
    assertEquals(update.toString(),
        "{ \"$set\" : { \"layoutNodeMap.setupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.setupId.endTs\" : "
            + "1000, \"layoutNodeMap.nodeExecutionId3.status\" : { \"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.nodeExecutionId3.endTs\" : 1000, "
            + "\"layoutNodeMap.strategyNodeId.moduleInfo.stepParameters\" "
            + ": null, \"layoutNodeMap.strategyNodeId.nodeRunInfo\" : null, "
            + "\"layoutNodeMap.strategyNodeId.childrenCount\" : 0, \"layoutNodeMap.strategyNodeId.status\" : "
            + "{ \"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.strategyNodeId.startTs\" : null, \"layoutNodeMap.strategyNodeId.endTs\" : 1000, "
            + "\"layoutNodeMap.nodeExecutionId2.nodeType\" : null, \"layoutNodeMap.nodeExecutionId2.nodeGroup\" : "
            + "\"stage\", \"layoutNodeMap.nodeExecutionId2.edgeLayoutList\" : null, "
            + "\"layoutNodeMap.nodeExecutionId2.skipInfo\" : { \"$java\" :  }, "
            + "\"layoutNodeMap.nodeExecutionId2.nodeUuid\" "
            + ": \"stageSetupId\", \"layoutNodeMap.nodeExecutionId2.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId2.executionInputConfigured\" : null, "
            + "\"layoutNodeMap.nodeExecutionId3.nodeType\" : null, \"layoutNodeMap.nodeExecutionId3.nodeGroup\" : "
            + "\"stage\", \"layoutNodeMap.nodeExecutionId3.edgeLayoutList\" : null, "
            + "\"layoutNodeMap.nodeExecutionId3.skipInfo\" : { \"$java\" :  }, "
            + "\"layoutNodeMap.nodeExecutionId3.nodeUuid\" "
            + ": \"stageSetupId\", \"layoutNodeMap.nodeExecutionId3.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId3.executionInputConfigured\" : null }, \"$addToSet\" : { "
            + "\"layoutNodeMap.strategyNodeId.edgeLayoutList.currentNodeChildren\" : { \"$java\" : { \"$each\" : [ "
            + "\"nodeExecutionId2\", \"nodeExecutionId3\" ] } } } }");
    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .nodeType(StrategyType.MATRIX.name())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeType("STAGE")
                             .nodeGroup("stage")
                             .module("pms")
                             .edgeLayoutList(EdgeLayoutListDTO.builder().build())
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    update = new Update();
    pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, update);
    // Since returned pipelineExecutionSummary.layoutNode had the strategy node with type Matrix. So maxConcurrency will
    // be present in update.
    assertEquals(update.toString(),
        "{ \"$set\" : { \"layoutNodeMap.setupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.setupId.endTs\" : "
            + "1000, \"layoutNodeMap.nodeExecutionId3.status\" : { \"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.nodeExecutionId3.endTs\" : 1000, "
            + "\"layoutNodeMap.strategyNodeId.moduleInfo.maxConcurrency.value\" : 3, "
            + "\"layoutNodeMap.strategyNodeId.moduleInfo.stepParameters\" : null, "
            + "\"layoutNodeMap.strategyNodeId.nodeRunInfo\" : null,"
            + " \"layoutNodeMap.strategyNodeId.childrenCount\" : 0, \"layoutNodeMap.strategyNodeId.status\" : { "
            + "\"$java\" : SUCCESS }, "
            + "\"layoutNodeMap.strategyNodeId.startTs\" : null, "
            + "\"layoutNodeMap.strategyNodeId.endTs\" : 1000, \"layoutNodeMap.nodeExecutionId2.nodeType\" : \"STAGE\", "
            + "\"layoutNodeMap.nodeExecutionId2.nodeGroup\" : \"stage\", "
            + "\"layoutNodeMap.nodeExecutionId2.edgeLayoutList\" "
            + ": { \"$java\" : EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, "
            + "\"layoutNodeMap.nodeExecutionId2.skipInfo\" : { \"$java\" :  }, "
            + "\"layoutNodeMap.nodeExecutionId2.nodeUuid\" "
            + ": \"stageSetupId\", \"layoutNodeMap.nodeExecutionId2.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId2.executionInputConfigured\" : null, "
            + "\"layoutNodeMap.nodeExecutionId3.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId3.nodeGroup\" : "
            + "\"stage\", \"layoutNodeMap.nodeExecutionId3.edgeLayoutList\" : { \"$java\" : "
            + "EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId3.skipInfo\" "
            + ": { "
            + "\"$java\" :  }, \"layoutNodeMap.nodeExecutionId3.nodeUuid\" : \"stageSetupId\", "
            + "\"layoutNodeMap.nodeExecutionId3.module\" : \"pms\", "
            + "\"layoutNodeMap.nodeExecutionId3.executionInputConfigured\" : null }, \"$addToSet\" : { "
            + "\"layoutNodeMap.strategyNodeId.edgeLayoutList.currentNodeChildren\" : { \"$java\" : { \"$each\" : [ "
            + "\"nodeExecutionId2\", \"nodeExecutionId3\" ] } } } }");
    // Graph Layout Node with null nodeType
    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeType("STAGE")
                             .nodeGroup("stage")
                             .module("pms")
                             .edgeLayoutList(EdgeLayoutListDTO.builder().build())
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    Update updateNew = new Update();
    assertThatCode(() -> pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, updateNew))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllSummaryForGivenPlanExecutionIds() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();

    pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    on(pmsExecutionSummaryService).set("pmsExecutionSummaryRepository", pmsExecutionSummaryRepository);

    pmsExecutionSummaryService.deleteAllSummaryForGivenPlanExecutionIds(
        Sets.newHashSet(planExecutionId), true, accountId);
    verify(pipelineSearchService, times(0)).deleteExecutions(Sets.newHashSet(planExecutionId), accountId);

    pmsExecutionSummaryService.deleteAllSummaryForGivenPlanExecutionIds(
        Sets.newHashSet(planExecutionId), false, accountId);

    verify(pipelineSearchService, times(1)).deleteExecutions(Sets.newHashSet(planExecutionId), accountId);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryWithProjections =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
            accountId, planExecutionId, Sets.newHashSet(PlanExecutionSummaryKeys.accountId));

    assertThat(pipelineExecutionSummaryWithProjections).isNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLAllSummaryForGivenPlanExecutionIds() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();

    pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    on(pmsExecutionSummaryService).set("pmsExecutionSummaryRepository", pmsExecutionSummaryRepository);

    Date ttlDate = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    pmsExecutionSummaryService.updateTTL(planExecutionId, ttlDate);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryWithProjections =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
            Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.validUntil));

    assertThat(pipelineExecutionSummaryWithProjections.getValidUntil()).isEqualTo(ttlDate);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForPRBStage() {
    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType prbStepType =
        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("PIPELINE_ROLLBACK_STAGE").build();
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String nodeId = "nodeId";
    String planExecutionId = "planExecutionId";
    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(prbStepType)
                                             .previousId(prevStageId)
                                             .nodeId(nodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));
    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, currentNodeExecution, update);
    Document updateObject = update.getUpdateObject();
    assertThat(updateObject).hasSize(1);
    Document setObjects = (Document) updateObject.get("$set");
    String expectedKey = "layoutNodeMap.prevStageNodeId.edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(expectedKey);
    assertThat(setObjects.get(expectedKey)).isEqualTo(Collections.singletonList("nodeId"));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForInject() {
    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.INSERT).build();
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String nodeId = "nodeId";
    String planExecutionId = "planExecutionId";
    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(stepType)
                                             .previousId(prevStageId)
                                             .nodeId(nodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));
    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, currentNodeExecution, update);
    Document updateObject = update.getUpdateObject();
    assertThat(updateObject).hasSize(1);
    Document setObjects = (Document) updateObject.get("$set");
    String expectedKey = "layoutNodeMap.nodeId.status";
    assertThat(setObjects).containsKey(expectedKey);
    assertThat(((ExecutionStatus) setObjects.get(expectedKey)).getEngineStatus().toString()).isEqualTo("RUNNING");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateNotesForExecutionSummaryForGivenPlanExecutionIds() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    String notes = "notes";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();

    pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    on(pmsExecutionSummaryService).set("pmsExecutionSummaryRepository", pmsExecutionSummaryRepository);

    Date ttlDate = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    pmsExecutionSummaryService.updateTTL(planExecutionId, ttlDate);
    pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryWithProjections =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
            Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.notesExistForPlanExecutionId));

    assertThat(pipelineExecutionSummaryWithProjections.getNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).status(RUNNING).endTs(System.currentTimeMillis()).build();
    AbortedBy abortedBy = AbortedBy.builder().email("admin@harness.io").userName("admin").build();
    doReturn(abortedBy).when(abortInfoHelper).fetchAbortedByInfoFromInterrupts(planExecution.getUuid());
    Update summaryUpdate = new Update();
    pmsExecutionSummaryService.updateStatusOps(planExecution, summaryUpdate);

    Document document = (Document) summaryUpdate.getUpdateObject().get("$set");
    assertThat(document.get("internalStatus").toString()).isEqualTo("RUNNING");
    assertThat(document.get("status").toString()).isEqualTo("RUNNING");
    assertThat(document.get("endTs")).isNull();
    assertThat(document.get("abortedBy")).isNull();

    planExecution =
        PlanExecution.builder().uuid(planExecution.getUuid()).status(ABORTED).endTs(System.currentTimeMillis()).build();

    summaryUpdate = new Update();
    pmsExecutionSummaryService.updateStatusOps(planExecution, summaryUpdate);
    document = (Document) summaryUpdate.getUpdateObject().get("$set");
    assertThat(document.get("internalStatus").toString()).isEqualTo("ABORTED");
    assertThat(document.get("status").toString()).isEqualTo("ABORTED");
    assertThat(document.get("endTs")).isEqualTo(planExecution.getEndTs());
    assertThat(document.get("abortedBy")).isEqualTo(abortedBy);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusErrored() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(ERRORED)
            .endTs(System.currentTimeMillis())
            .failureInfo(FailureInfo.newBuilder().setErrorMessage("Error while plan creation").build())
            .build();
    Update summaryUpdate = new Update();
    pmsExecutionSummaryService.updateStatusOps(planExecution, summaryUpdate);
    Document document = (Document) summaryUpdate.getUpdateObject().get("$set");
    assertThat(document.get("internalStatus").toString()).isEqualTo("ERRORED");
    assertThat(document.get("status").toString()).isEqualTo("ERRORED");
    assertThat(document.get("endTs")).isEqualTo(planExecution.getEndTs());
    assertThat(document.get("failureInfo"))
        .isEqualTo(FailureInfoDTOConverter.toFailureInfoDTO(planExecution.getFailureInfo()));
    assertThat(document.get("executionErrorInfo"))
        .isEqualTo(ExecutionErrorInfo.builder().message(planExecution.getFailureInfo().getErrorMessage()).build());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testUpdateResolvedUserInputSetYamlValidYaml() {
    String planExecutionId = "planExecutionId";
    String givenYaml = "pipeline:\n"
        + "  identifier: pipelineId\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stg1\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: default\n"
        + "          pipeline: testInputDefault\n"
        + "          project: projectId\n"
        + "          inputs:\n"
        + "            identifier: testInputDefault\n"
        + "            variables:\n"
        + "              - name: var1\n"
        + "                type: String\n"
        + "                value: <+input>.default(6).allowedValues(1,2,3,4,5,6,7,8,9)\n";
    String resolvedInputSetYaml = "pipeline:\n"
        + "  identifier: pipelineId\n"
        + "  projectIdentifier: projectId\n"
        + "  orgIdentifier: orgId\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stg1\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: default\n"
        + "          pipeline: testInputDefault\n"
        + "          project: projectId\n"
        + "          inputs:\n"
        + "            identifier: testInputDefault\n"
        + "            variables:\n"
        + "              - name: var1\n"
        + "                type: String\n"
        + "                value: \"6\"\n";
    doReturn(PipelineExecutionSummaryEntity.builder().build())
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(), any());
    pmsExecutionSummaryService.updateResolvedUserInputSetYaml(planExecutionId, givenYaml, HarnessYamlVersion.V0);
    Update expectedUpdate = new Update();
    expectedUpdate.set(PlanExecutionSummaryKeys.resolvedUserInputSetYaml, resolvedInputSetYaml);
    verify(pmsExecutionSummaryRepositoryMock, times(1))
        .update(
            new Query(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId)), expectedUpdate);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testUpdateResolvedUserInputSetInvalidYaml() {
    String planExecutionId = "planExecutionId";
    String resolvedInputSetYaml = "resolved-input-set-yaml";
    doReturn(PipelineExecutionSummaryEntity.builder().build())
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(), any());
    pmsExecutionSummaryService.updateResolvedUserInputSetYaml(
        planExecutionId, resolvedInputSetYaml, HarnessYamlVersion.V0);
    Update expectedUpdate = new Update();
    expectedUpdate.set(PlanExecutionSummaryKeys.resolvedUserInputSetYaml, null);
    verify(pmsExecutionSummaryRepositoryMock, times(1))
        .update(
            new Query(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId)), expectedUpdate);
  }

  @Test
  @Owner(developers = JATIN)
  @Category(UnitTests.class)
  public void testUpdateResolvedUserInputSetYamlNull() throws IOException {
    String planExecutionId = "planExecutionId";
    String resolvedInputSetYaml = "resolved-input-set-yaml";
    pmsExecutionSummaryService.updateResolvedUserInputSetYaml(planExecutionId, null, HarnessYamlVersion.V0);
    try (MockedStatic<Utils> mockRestStatic = mockStatic(Utils.class)) {
      mockRestStatic.when(() -> Utils.getYamlWithoutInputs(any())).thenThrow(IllegalArgumentException.class);
    }
    Update expectedUpdate = new Update();
    expectedUpdate.set(PlanExecutionSummaryKeys.resolvedUserInputSetYaml, null);
    verify(pmsExecutionSummaryRepositoryMock, times(0))
        .update(
            new Query(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId)), expectedUpdate);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpdateStatusOfPlanExecutionSummaryEntity() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(APPROVAL_WAITING).build();
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update update = new Update()
                        .set(PlanExecutionSummaryKeys.internalStatus, planExecution.getStatus())
                        .set(PlanExecutionSummaryKeys.status, ExecutionStatus.APPROVALWAITING);
    pmsExecutionSummaryService.updatePlanExecutionSummaryStatus(planExecutionId, planExecution);
    verify(pmsExecutionSummaryRepositoryMock, times(1)).update(query, update);

    planExecution = PlanExecution.builder().uuid(planExecutionId).status(INPUT_WAITING).build();
    update = new Update()
                 .set(PlanExecutionSummaryKeys.internalStatus, planExecution.getStatus())
                 .set(PlanExecutionSummaryKeys.status, ExecutionStatus.INPUTWAITING);
    pmsExecutionSummaryService.updatePlanExecutionSummaryStatus(planExecutionId, planExecution);
    verify(pmsExecutionSummaryRepositoryMock, times(1)).update(query, update);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusFromCDC_errored_withFailureInfo() {
    String planExecutionId = generateUuid();
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("something went wrong").build();

    final Update[] captured = new Update[1];
    doAnswer(invocation -> {
      captured[0] = invocation.getArgument(1);
      return PipelineExecutionSummaryEntity.builder().build();
    })
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(Query.class), any(Update.class));

    pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, ERRORED, 12345L, failureInfo);

    assertThat(captured[0]).isNotNull();
    Document document = (Document) captured[0].getUpdateObject().get("$set");
    assertThat(document.get("internalStatus").toString()).isEqualTo("ERRORED");
    assertThat(document.get("status").toString()).isEqualTo("ERRORED");
    assertThat(document.get("endTs")).isEqualTo(12345L);
    assertThat(document.get("executionErrorInfo"))
        .isEqualTo(ExecutionErrorInfo.builder().message("something went wrong").build());
    assertThat(document.get("failureInfo")).isEqualTo(FailureInfoDTOConverter.toFailureInfoDTO(failureInfo));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusFromCDC_aborted() {
    String planExecutionId = generateUuid();
    AbortedBy abortedBy = AbortedBy.builder().email("admin@harness.io").userName("admin").build();
    doReturn(abortedBy).when(abortInfoHelper).fetchAbortedByInfoFromInterrupts(planExecutionId);

    final Update[] captured = new Update[1];
    doAnswer(invocation -> {
      captured[0] = invocation.getArgument(1);
      return PipelineExecutionSummaryEntity.builder().build();
    })
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(Query.class), any(Update.class));

    pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, ABORTED, 99999L, null);

    assertThat(captured[0]).isNotNull();
    Document document = (Document) captured[0].getUpdateObject().get("$set");
    assertThat(document.get("internalStatus").toString()).isEqualTo("ABORTED");
    assertThat(document.get("status").toString()).isEqualTo("ABORTED");
    assertThat(document.get("endTs")).isEqualTo(99999L);
    assertThat(document.get("abortedBy")).isEqualTo(abortedBy);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusFromCDC_noEndTsForNonFinal() {
    String planExecutionId = generateUuid();

    final Update[] captured = new Update[1];
    doAnswer(invocation -> {
      captured[0] = invocation.getArgument(1);
      return PipelineExecutionSummaryEntity.builder().build();
    })
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(Query.class), any(Update.class));

    pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, RUNNING, 12345L, null);

    Document document = (Document) captured[0].getUpdateObject().get("$set");
    // RUNNING is not a final status, so endTs should not be set
    assertThat(document.get("endTs")).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusFromCDC_skipsWhenCdcGraphNotEnabled() {
    String planExecutionId = generateUuid();

    // repository.update returns null means no document matched (cdcGraphEnabled != true)
    doReturn(null).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));

    pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, RUNNING, null, null);

    verify(pmsExecutionSummaryRepositoryMock, times(1)).update(any(Query.class), any(Update.class));
    verify(pipelineSearchService, times(0)).update(any(PipelineExecutionSummaryEntity.class));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStatusFromCDC_syncsToElastic() {
    String planExecutionId = generateUuid();
    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder().cdcGraphEnabled(true).build();

    doReturn(entity).when(pmsExecutionSummaryRepositoryMock).update(any(Query.class), any(Update.class));
    doReturn(true).when(pipelineSearchService).shouldSyncToElastic(any(Update.class));
    doReturn(false).when(pipelineSearchService).shouldFetchDocumentFromPrimary(any(Update.class));

    pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, RUNNING, null, null);

    verify(pipelineSearchService, times(1)).update(entity);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPullStageStepIdFromStrategyChildren() {
    String planExecutionId = "somePlanExecutionId";
    String dummyStageNodeId = "someStageSetupId";
    String strategyNodeId = "someStrategyNodeId";
    doReturn(PipelineExecutionSummaryEntity.builder().build())
        .when(pmsExecutionSummaryRepositoryMock)
        .update(any(), any());
    pmsExecutionSummaryService.pullStageStepIdFromStrategyChildren(planExecutionId,
        NodeExecution.builder()
            .nodeId(strategyNodeId)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .build(),
        dummyStageNodeId);

    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query expectedQuery = new Query(criteria);

    Update expectedUpdate =
        new Update()
            .pull(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeId + ".edgeLayoutList.currentNodeChildren",
                dummyStageNodeId)
            .set(PlanExecutionSummaryKeys.layoutNodeMap + "." + dummyStageNodeId + "." + GraphLayoutNodeDTOKeys.hidden,
                true);

    verify(pmsExecutionSummaryRepositoryMock).update(eq(expectedQuery), eq(expectedUpdate));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForPRBStage_UpdatesDependencyGraph() {
    String stageSetupId = "triggeringStageSetupId";
    String rollbackNodeId = "rollbackNodeId";
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String planExecutionId = "planExecutionId";

    Level stageLevel =
        Level.newBuilder()
            .setSetupId(stageSetupId)
            .setRuntimeId("stageRuntimeId")
            .setNodeType(NodeType.PLAN_NODE.name())
            .setStepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();

    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType prbStepType =
        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("PIPELINE_ROLLBACK_STAGE").build();

    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(prbStepType)
                                             .previousId(prevStageId)
                                             .nodeId(rollbackNodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    doReturn(basicAmbiance).when(nodeExecutionService).getAmbiance(currentNodeExecution);
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    // Mock summary entity with isDagEnabled = true (using projection query)
    doReturn(PipelineExecutionSummaryEntity.builder().isDagEnabled(true).build())
        .when(pmsExecutionSummaryRepositoryMock)
        .getPipelineExecutionSummaryWithProjections(any(), any());

    OnFailPipelineRollbackOutput rollbackOutput = OnFailPipelineRollbackOutput.builder()
                                                      .shouldStartPipelineRollback(true)
                                                      .levelsAtFailurePoint(Collections.singletonList(stageLevel))
                                                      .build();
    doReturn(OptionalSweepingOutput.builder().found(true).output(rollbackOutput).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, currentNodeExecution, update);

    Document setObjects = (Document) update.getUpdateObject().get("$set");
    // Verify nextIds update
    String nextIdsKey = "layoutNodeMap.prevStageNodeId.edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(nextIdsKey);
    assertThat(setObjects.get(nextIdsKey)).isEqualTo(Collections.singletonList(rollbackNodeId));
    // Verify dependencyGraph update
    String depGraphKey = PlanExecutionSummaryKeys.dependencyGraph + "." + rollbackNodeId;
    assertThat(setObjects).containsKey(depGraphKey);
    assertThat(setObjects.get(depGraphKey)).isEqualTo(Collections.singletonList(stageSetupId));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForPRBStage_SweepingOutputNotFound() {
    String rollbackNodeId = "rollbackNodeId";
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String planExecutionId = "planExecutionId";

    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType prbStepType =
        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("PIPELINE_ROLLBACK_STAGE").build();

    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(prbStepType)
                                             .previousId(prevStageId)
                                             .nodeId(rollbackNodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    doReturn(basicAmbiance).when(nodeExecutionService).getAmbiance(currentNodeExecution);
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    // Mock summary entity with isDagEnabled = true (using projection query)
    doReturn(PipelineExecutionSummaryEntity.builder().isDagEnabled(true).build())
        .when(pmsExecutionSummaryRepositoryMock)
        .getPipelineExecutionSummaryWithProjections(any(), any());

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, currentNodeExecution, update);

    Document setObjects = (Document) update.getUpdateObject().get("$set");
    // Verify nextIds update still happens
    String nextIdsKey = "layoutNodeMap.prevStageNodeId.edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(nextIdsKey);
    // Verify dependencyGraph is NOT updated when sweeping output not found
    String depGraphKey = PlanExecutionSummaryKeys.dependencyGraph + "." + rollbackNodeId;
    assertThat(setObjects).doesNotContainKey(depGraphKey);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForPRBStage_SweepingOutputExceptionHandled() {
    String rollbackNodeId = "rollbackNodeId";
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String planExecutionId = "planExecutionId";

    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType prbStepType =
        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("PIPELINE_ROLLBACK_STAGE").build();

    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(prbStepType)
                                             .previousId(prevStageId)
                                             .nodeId(rollbackNodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    doReturn(basicAmbiance).when(nodeExecutionService).getAmbiance(currentNodeExecution);
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    // Mock summary entity with isDagEnabled = true so we enter the block where exception occurs (using projection
    // query)
    doReturn(PipelineExecutionSummaryEntity.builder().isDagEnabled(true).build())
        .when(pmsExecutionSummaryRepositoryMock)
        .getPipelineExecutionSummaryWithProjections(any(), any());

    doThrow(new RuntimeException("gRPC call failed"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    Update update = new Update();
    // Should not throw - exception is caught and logged
    assertThatCode(()
                       -> pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(
                           planExecutionId, currentNodeExecution, update))
        .doesNotThrowAnyException();

    Document setObjects = (Document) update.getUpdateObject().get("$set");
    // Verify nextIds update still happens (it runs before the try block)
    String nextIdsKey = "layoutNodeMap.prevStageNodeId.edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(nextIdsKey);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testV1RollbackFiltersIncorrectStrategyChildren() {
    // Test that V1 rollback filters out STRATEGY nodes that incorrectly have parentId pointing to another strategy
    String planExecutionId = generateUuid();
    String strategyNode1Id = generateUuid();
    String strategyNode2Id = generateUuid();
    String stageChildId = generateUuid();

    // Create V1 rollback ambiance
    Ambiance v1RollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setIdentifier("pipeline").build())
            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder()
                             .setHarnessVersion("1")
                             .setExecutionMode(io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK)
                             .build())
            .build();

    // Strategy node 1 (deploy_2) - correct parent (stages)
    NodeExecution strategyNode1 =
        NodeExecution.builder()
            .uuid(strategyNode1Id)
            .nodeId("deploy_2_node")
            .ambiance(v1RollbackAmbiance)
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .executionContext(ExecutionContext.newBuilder()
                                  .addLevels(Level.newBuilder().setIdentifier("_id1").setGroup("STAGES").build())
                                  .setPipelineExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                  .addLevels(Level.newBuilder().setIdentifier("_id2").build())
                                  .build())
            .stepType(
                StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("unifiedMultiDeployment").build())
            .status(Status.SUCCEEDED)
            .parentId("stagesNodeId")
            .build();

    // Stage child of strategy node 1 - correct parent (strategyNode1)
    NodeExecution stageChild = NodeExecution.builder()
                                   .uuid(stageChildId)
                                   .nodeId("deploy_2_child_node")
                                   .ambiance(v1RollbackAmbiance)
                                   .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                   .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                   .status(Status.SUCCEEDED)
                                   .parentId(strategyNode1Id) // Correct parent
                                   .build();

    // Strategy node 2 (deploy_1) - INCORRECT parent (should be stages, but points to strategyNode1)
    NodeExecution strategyNode2 =
        NodeExecution.builder()
            .uuid(strategyNode2Id)
            .nodeId("deploy_1_node")
            .ambiance(v1RollbackAmbiance)
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .stepType(
                StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("unifiedMultiDeployment").build())
            .status(Status.SUCCEEDED)
            .parentId(strategyNode1Id) // INCORRECT - should be stagesNodeId
            .build();

    List<NodeExecution> allNodes = Arrays.asList(strategyNode1, stageChild, strategyNode2);

    when(nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId)).thenReturn(allNodes);
    when(pmsExecutionSummaryRepositoryMock.findByPlanExecutionId(planExecutionId))
        .thenReturn(Optional.of(
            PipelineExecutionSummaryEntity.builder()
                .layoutNodeMap(Map.of("deploy_2_node",
                    GraphLayoutNodeDTO.builder()
                        .nodeType(StrategyType.PARALLELISM.name())
                        .nodeIdentifier("deploy_2")
                        .name("deploy 2")
                        .edgeLayoutList(
                            EdgeLayoutListDTO.builder().currentNodeChildren(Collections.singletonList(null)).build())
                        .build()))
                .build()));
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, strategyNode1, update);

    // Verify that only the stage child was processed, not the incorrectly-parented strategy node
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).isNotNull();

    // The status should be calculated from the stage child only, not from strategyNode2
    String statusKey = "layoutNodeMap.deploy_2_node.status";
    assertThat(setObjects).containsKey(statusKey);
    assertThat(setObjects.get(statusKey)).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testV1RollbackCalculatesStatusFromChildren() {
    // Test that V1 rollback identity strategy nodes calculate status from their children
    String planExecutionId = generateUuid();
    String strategyNodeId = generateUuid();
    String stageChild1Id = generateUuid();
    String stageChild2Id = generateUuid();

    // Create V1 rollback ambiance
    Ambiance v1RollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setIdentifier("pipeline").build())
            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder()
                             .setHarnessVersion("1")
                             .setExecutionMode(io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK)
                             .build())
            .build();

    // Strategy node with SKIPPED status (identity node doesn't execute itself)
    NodeExecution strategyNode =
        NodeExecution.builder()
            .uuid(strategyNodeId)
            .nodeId("deploy_strategy_node")
            .ambiance(v1RollbackAmbiance)
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .executionContext(ExecutionContext.newBuilder()
                                  .addLevels(Level.newBuilder().setIdentifier("_id1").setGroup("STAGES").build())
                                  .setPipelineExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                  .addLevels(Level.newBuilder().setIdentifier("_id2").build())
                                  .build())
            .stepType(
                StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("unifiedMultiDeployment").build())
            .status(Status.SKIPPED) // Identity node shows SKIPPED
            .parentId("stagesNodeId")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .build();

    // Child stage 1 - SUCCESS
    NodeExecution stageChild1 = NodeExecution.builder()
                                    .uuid(stageChild1Id)
                                    .nodeId("deploy_child1_node")
                                    .ambiance(v1RollbackAmbiance)
                                    .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                    .status(Status.SUCCEEDED)
                                    .parentId(strategyNodeId)
                                    .build();

    // Child stage 2 - SUCCESS
    NodeExecution stageChild2 = NodeExecution.builder()
                                    .uuid(stageChild2Id)
                                    .nodeId("deploy_child2_node")
                                    .ambiance(v1RollbackAmbiance)
                                    .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                    .status(Status.SUCCEEDED)
                                    .parentId(strategyNodeId)
                                    .build();

    List<NodeExecution> allNodes = Arrays.asList(strategyNode, stageChild1, stageChild2);

    when(nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId)).thenReturn(allNodes);
    when(pmsExecutionSummaryRepositoryMock.findByPlanExecutionId(planExecutionId))
        .thenReturn(Optional.of(
            PipelineExecutionSummaryEntity.builder()
                .layoutNodeMap(Map.of("deploy_strategy_node",
                    GraphLayoutNodeDTO.builder()
                        .nodeType(StrategyType.PARALLELISM.name())
                        .nodeIdentifier("deploy")
                        .name("deploy")
                        .edgeLayoutList(
                            EdgeLayoutListDTO.builder().currentNodeChildren(Collections.singletonList(null)).build())
                        .build()))
                .build()));
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, strategyNode, update);

    // Verify that the strategy node status is calculated from children (SUCCESS) not from itself (SKIPPED)
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).isNotNull();

    String statusKey = "layoutNodeMap.deploy_strategy_node.status";
    assertThat(setObjects).containsKey(statusKey);
    // Should be SUCCESS because both children are SUCCESS, not SKIPPED from the identity node
    assertThat(setObjects.get(statusKey)).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testV1RollbackCalculatesFailedStatusFromChildren() {
    // Test that V1 rollback correctly aggregates FAILED status from children
    String planExecutionId = generateUuid();
    String strategyNodeId = generateUuid();
    String stageChild1Id = generateUuid();
    String stageChild2Id = generateUuid();

    // Create V1 rollback ambiance
    Ambiance v1RollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setIdentifier("pipeline").build())
            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder()
                             .setHarnessVersion("1")
                             .setExecutionMode(io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK)
                             .build())
            .build();

    // Strategy node with SKIPPED status
    NodeExecution strategyNode =
        NodeExecution.builder()
            .uuid(strategyNodeId)
            .nodeId("deploy_strategy_node")
            .ambiance(v1RollbackAmbiance)
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .executionContext(ExecutionContext.newBuilder()
                                  .addLevels(Level.newBuilder().setIdentifier("_id1").setGroup("STAGES").build())
                                  .setPipelineExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                  .addLevels(Level.newBuilder().setIdentifier("_id2").build())
                                  .build())
            .stepType(
                StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("unifiedMultiDeployment").build())
            .status(Status.SKIPPED)
            .parentId("stagesNodeId")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .build();

    // Child stage 1 - SUCCESS
    NodeExecution stageChild1 = NodeExecution.builder()
                                    .uuid(stageChild1Id)
                                    .nodeId("deploy_child1_node")
                                    .ambiance(v1RollbackAmbiance)
                                    .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                    .status(Status.SUCCEEDED)
                                    .parentId(strategyNodeId)
                                    .build();

    // Child stage 2 - FAILED
    NodeExecution stageChild2 = NodeExecution.builder()
                                    .uuid(stageChild2Id)
                                    .nodeId("deploy_child2_node")
                                    .ambiance(v1RollbackAmbiance)
                                    .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                    .status(Status.FAILED)
                                    .parentId(strategyNodeId)
                                    .build();

    List<NodeExecution> allNodes = Arrays.asList(strategyNode, stageChild1, stageChild2);

    when(nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId)).thenReturn(allNodes);
    when(pmsExecutionSummaryRepositoryMock.findByPlanExecutionId(planExecutionId))
        .thenReturn(Optional.of(
            PipelineExecutionSummaryEntity.builder()
                .layoutNodeMap(Map.of("deploy_strategy_node",
                    GraphLayoutNodeDTO.builder()
                        .nodeType(StrategyType.PARALLELISM.name())
                        .nodeIdentifier("deploy")
                        .name("deploy")
                        .edgeLayoutList(
                            EdgeLayoutListDTO.builder().currentNodeChildren(Collections.singletonList(null)).build())
                        .build()))
                .build()));
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, strategyNode, update);

    // Verify that the strategy node status is FAILED because one child failed
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).isNotNull();

    String statusKey = "layoutNodeMap.deploy_strategy_node.status";
    assertThat(setObjects).containsKey(statusKey);
    // Should be FAILED because one child failed, even though the identity node itself is SKIPPED
    assertThat(setObjects.get(statusKey)).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testV0PipelineUsesNodeOwnStatus() {
    // Test that V0 pipelines use the node's own status, not calculated from children
    String planExecutionId = generateUuid();
    String strategyNodeId = generateUuid();

    // Create V0 (non-rollback) ambiance
    Ambiance v0Ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).setIdentifier("pipeline").build())
            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder()
                             .setHarnessVersion("0") // V0 pipeline
                             .setExecutionMode(io.harness.pms.contracts.plan.ExecutionMode.NORMAL)
                             .build())
            .build();

    // Strategy node with SUCCESS status
    NodeExecution strategyNode =
        NodeExecution.builder()
            .uuid(strategyNodeId)
            .nodeId("deploy_strategy_node")
            .ambiance(v0Ambiance)
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .executionContext(ExecutionContext.newBuilder()
                                  .addLevels(Level.newBuilder().setIdentifier("_id1").setGroup("STAGES").build())
                                  .setPipelineExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                  .addLevels(Level.newBuilder().setIdentifier("_id2").build())
                                  .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .status(Status.SUCCEEDED)
            .parentId("stagesNodeId")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .build();

    List<NodeExecution> allNodes = Arrays.asList(strategyNode);

    when(nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId)).thenReturn(allNodes);
    when(pmsExecutionSummaryRepositoryMock.findByPlanExecutionId(planExecutionId))
        .thenReturn(Optional.of(
            PipelineExecutionSummaryEntity.builder()
                .layoutNodeMap(Map.of("deploy_strategy_node",
                    GraphLayoutNodeDTO.builder()
                        .nodeType(StrategyType.PARALLELISM.name())
                        .nodeIdentifier("deploy")
                        .name("deploy")
                        .edgeLayoutList(
                            EdgeLayoutListDTO.builder().currentNodeChildren(Collections.singletonList(null)).build())
                        .build()))
                .build()));
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            any(), eq(planExecutionId), eq(PlanExecutionProjectionConstants.fieldsForPostProdRollbackOptimized));

    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(planExecutionId, strategyNode, update);

    // Verify that V0 uses the node's own status (SUCCESS)
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).isNotNull();

    String statusKey = "layoutNodeMap.deploy_strategy_node.status";
    assertThat(setObjects).containsKey(statusKey);
    // Should use node's own status (SUCCESS)
    assertThat(setObjects.get(statusKey)).isEqualTo(ExecutionStatus.SUCCESS);
  }
}
